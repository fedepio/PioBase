package it.PioSoft.PioBase.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;

@Service
public class DeviceMonitoringService {
    private final Map<String, List<SseEmitter>> deviceEmitters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> deviceStatusCache = new ConcurrentHashMap<>();

    private final PcStatusService pcStatusService;
    private final SystemInfoService systemInfoService;

    public DeviceMonitoringService(PcStatusService pcStatusService, SystemInfoService systemInfoService) {
        this.pcStatusService = pcStatusService;
        this.systemInfoService = systemInfoService;
    }

    public SseEmitter subscribeToDevice(String ipAddress) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        deviceEmitters.computeIfAbsent(ipAddress, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(ipAddress, emitter));
        emitter.onTimeout(() -> removeEmitter(ipAddress, emitter));
        emitter.onError(e -> removeEmitter(ipAddress, emitter));
        // Invia stato corrente se disponibile
        if (deviceStatusCache.containsKey(ipAddress)) {
            try {
                emitter.send(SseEmitter.event().name("status").data(deviceStatusCache.get(ipAddress)));
            } catch (IOException e) {
            }
        }
        return emitter;
    }

    private void removeEmitter(String ipAddress, SseEmitter emitter) {
        List<SseEmitter> emitters = deviceEmitters.get(ipAddress);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                deviceEmitters.remove(ipAddress);
            }
        }
    }

    // Ridotto il polling da 5000ms a 2000ms per rilevamento più veloce
    @Scheduled(fixedDelay = 2000)
    public void checkDevices() {
        deviceEmitters.keySet().forEach(ipAddress -> {
            Map<String, Object> status = getDeviceStatus(ipAddress);
            Map<String, Object> previousStatus = deviceStatusCache.get(ipAddress);
            if (!status.equals(previousStatus)) {
                deviceStatusCache.put(ipAddress, status);
                broadcastToDevice(ipAddress, status);
            }
        });
    }

    /**
     * Forza un controllo immediato dello stato di un dispositivo specifico
     * Utile dopo operazioni come spegnimento remoto
     */
    public void forceStatusCheck(String ipAddress) {
        System.out.println("Forzando controllo stato immediato per: " + ipAddress);
        Map<String, Object> status = getDeviceStatus(ipAddress);
        Map<String, Object> previousStatus = deviceStatusCache.get(ipAddress);

        // Aggiorna sempre lo stato dopo spegnimento forzato
        deviceStatusCache.put(ipAddress, status);
        broadcastToDevice(ipAddress, status);

        System.out.println("Stato aggiornato per " + ipAddress + ": " + status.get("online"));
    }

    /**
     * Marca immediatamente un dispositivo come offline (per uso dopo spegnimento)
     */
    public void markDeviceOffline(String ipAddress, String reason) {
        System.out.println("Marcando dispositivo come offline: " + ipAddress + " - Motivo: " + reason);

        Map<String, Object> status = new HashMap<>();
        status.put("ip", ipAddress);
        status.put("timestamp", System.currentTimeMillis());
        status.put("online", false);
        status.put("reason", reason);
        status.put("forcedOffline", true);

        deviceStatusCache.put(ipAddress, status);
        broadcastToDevice(ipAddress, status);

        // Programma controlli multipli per confermare lo stato offline
        new Thread(() -> {
            try {
                // Primo controllo dopo 1 secondo
                Thread.sleep(1000);
                forceStatusCheckUltraFast(ipAddress);

                // Secondo controllo dopo 3 secondi per essere sicuri
                Thread.sleep(2000);
                forceStatusCheckUltraFast(ipAddress);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Controllo ultra-veloce dello stato con timeout ridottissimi
     */
    private void forceStatusCheckUltraFast(String ipAddress) {
        System.out.println("Controllo ultra-veloce per: " + ipAddress);

        Map<String, Object> status = new HashMap<>();
        status.put("ip", ipAddress);
        status.put("timestamp", System.currentTimeMillis());

        try {
            // Usa il metodo ultra-veloce per rilevare rapidamente se offline
            boolean isOnline = pcStatusService.isPcOnlineFast(ipAddress);
            status.put("online", isOnline);

            if (isOnline) {
                // Se è online, prova SSH ultra-veloce
                Map<String, String> systemInfo = systemInfoService.getSystemInfoUltraFast(ipAddress);

                if (systemInfo.containsKey("error") &&
                    (systemInfo.get("error").contains("timeout") ||
                     systemInfo.get("error").contains("offline"))) {
                    // SSH fallisce = PC probabilmente spento
                    status.put("online", false);
                    status.put("ultraFastCheck", true);
                    status.put("systemInfoError", "SSH ultra-veloce fallito - PC spento");
                } else {
                    status.putAll(systemInfo);
                    status.put("ultraFastCheck", true);
                }
            } else {
                status.put("systemInfoError", "PC offline - ping ultra-veloce fallito");
                status.put("ultraFastCheck", true);
            }

        } catch (Exception e) {
            status.put("online", false);
            status.put("error", "Errore controllo ultra-veloce: " + e.getMessage());
            status.put("ultraFastCheck", true);
        }

        deviceStatusCache.put(ipAddress, status);
        broadcastToDevice(ipAddress, status);

        System.out.println("Controllo ultra-veloce completato per " + ipAddress + ": online=" + status.get("online"));
    }

    private Map<String, Object> getDeviceStatus(String ipAddress) {
        Map<String, Object> status = new HashMap<>();
        status.put("ip", ipAddress);
        status.put("timestamp", System.currentTimeMillis());

        try {
            // Prima controlla se il PC è online con ping veloce
            boolean isOnline = pcStatusService.isPcOnline(ipAddress);
            status.put("online", isOnline);

            if (isOnline) {
                // Solo se online, tenta di recuperare info sistema con timeout ridotto
                try {
                    Map<String, String> systemInfo = systemInfoService.getSystemInfoQuick(ipAddress);

                    // Controlla se ci sono errori nelle info di sistema
                    if (systemInfo.containsKey("error")) {
                        String errorMsg = systemInfo.get("error");
                        if (errorMsg.contains("timeout") || errorMsg.contains("ConnectException")) {
                            // Se c'è timeout SSH, marca come offline
                            status.put("online", false);
                            status.put("sshTimeout", true);
                            status.put("systemInfoError", "SSH timeout - PC potrebbe essere spento o SSH non disponibile");
                        } else {
                            // Altri errori SSH ma PC è online via ping
                            status.putAll(systemInfo);
                        }
                    } else {
                        // Info sistema recuperate con successo
                        status.putAll(systemInfo);
                    }

                } catch (Exception e) {
                    // Gestisci eccezioni durante recupero info sistema
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("timeout") || errorMsg.contains("ConnectException"))) {
                        status.put("online", false);
                        status.put("sshTimeout", true);
                        status.put("systemInfoError", "SSH non raggiungibile");
                    } else {
                        status.put("systemInfoError", errorMsg);
                    }
                }
            } else {
                // PC offline, non tentare SSH
                status.put("systemInfoError", "PC offline - SSH non tentato");
            }

        } catch (Exception e) {
            // Errore durante controllo ping
            status.put("online", false);
            status.put("error", "Errore controllo stato: " + e.getMessage());
        }

        return status;
    }

    /**
     * Aggiorna e broadcast lo stato di un dispositivo (usato anche per IP cam)
     */
    public void updateDeviceStatus(String ipAddress, Map<String, Object> status) {
        deviceStatusCache.put(ipAddress, status);
        broadcastToDevice(ipAddress, status);
    }

    private void broadcastToDevice(String ipAddress, Map<String, Object> status) {
        List<SseEmitter> emitters = deviceEmitters.get(ipAddress);
        if (emitters != null) {
            emitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event().name("status").data(status));
                    return false;
                } catch (IOException e) {
                    return true;
                }
            });
        }
    }
}
