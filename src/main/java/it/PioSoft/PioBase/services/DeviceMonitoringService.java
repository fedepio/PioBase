package it.PioSoft.PioBase.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;

@Service
public class DeviceMonitoringService {
    private final Map<String, List<SseEmitter>> deviceEmitters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> deviceStatusCache = new ConcurrentHashMap<>();

    // Nuovi emitters per monitoraggio combinato PC + IP Cam
    private final List<SseEmitter> combinedEmitters = new CopyOnWriteArrayList<>();
    private Map<String, Object> lastCombinedStatus = new HashMap<>();

    private final PcStatusService pcStatusService;
    private final SystemInfoService systemInfoService;
    private final PcPingMonitorService pcPingMonitorService;

    // Riferimento all'IpCamScannerService (sarà iniettato)
    private IpCamScannerService ipCamScannerService;

    public DeviceMonitoringService(PcStatusService pcStatusService, SystemInfoService systemInfoService, PcPingMonitorService pcPingMonitorService) {
        this.pcStatusService = pcStatusService;
        this.systemInfoService = systemInfoService;
        this.pcPingMonitorService = pcPingMonitorService;
    }

    // Setter per dependency injection circolare
    public void setIpCamScannerService(IpCamScannerService ipCamScannerService) {
        this.ipCamScannerService = ipCamScannerService;
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

        // Usa il ping monitor per marcare il PC come offline
        pcPingMonitorService.markAsOffline(ipAddress);

        Map<String, Object> status = new HashMap<>();
        status.put("ip", ipAddress);
        status.put("timestamp", System.currentTimeMillis());
        status.put("online", false);
        status.put("reason", reason);
        status.put("forcedOffline", true);

        deviceStatusCache.put(ipAddress, status);
        broadcastToDevice(ipAddress, status);

        // Forza anche l'aggiornamento dello stato combinato
        forceCombinedStatusCheck(ipAddress);
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

    /**
     * Sottoscrizione per monitoraggio combinato PC + IP Cam
     * Invia aggiornamenti unificati con entrambi gli stati
     */
    public SseEmitter subscribeToSystemStatus(String pcIpAddress) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        combinedEmitters.add(emitter);

        // Registra il PC per il monitoraggio attivo tramite ping
        pcPingMonitorService.registerPcForMonitoring(pcIpAddress);

        emitter.onCompletion(() -> combinedEmitters.remove(emitter));
        emitter.onTimeout(() -> combinedEmitters.remove(emitter));
        emitter.onError(e -> combinedEmitters.remove(emitter));

        // Invia stato iniziale immediato
        try {
            Map<String, Object> initialStatus = buildCombinedStatus(pcIpAddress);
            emitter.send(SseEmitter.event().name("systemStatus").data(initialStatus));
        } catch (IOException e) {
            combinedEmitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Polling schedulato per monitoraggio combinato (ogni 2 secondi)
     */
    @Scheduled(fixedDelay = 2000)
    public void checkCombinedStatus() {
        if (combinedEmitters.isEmpty()) {
            return; // Nessun client connesso, skip
        }

        // Ottieni l'IP del PC dalla cache o usa default
        String pcIp = getPcIpFromCache();
        if (pcIp == null) {
            return; // Nessun PC da monitorare
        }

        Map<String, Object> combinedStatus = buildCombinedStatus(pcIp);

        // Broadcast solo se ci sono cambiamenti
        if (!combinedStatus.equals(lastCombinedStatus)) {
            lastCombinedStatus = new HashMap<>(combinedStatus);
            broadcastCombinedStatus(combinedStatus);
            System.out.println("Stato combinato aggiornato - PC: " + combinedStatus.get("pcOnline") +
                             ", Cam: " + combinedStatus.get("camOnline"));
        }
    }

    /**
     * Forza un controllo immediato dello stato combinato
     */
    public void forceCombinedStatusCheck(String pcIpAddress) {
        System.out.println("Forzando controllo stato combinato per PC: " + pcIpAddress);
        Map<String, Object> combinedStatus = buildCombinedStatus(pcIpAddress);
        lastCombinedStatus = new HashMap<>(combinedStatus);
        broadcastCombinedStatus(combinedStatus);
    }

    /**
     * Costruisce lo stato combinato di PC e IP Cam
     */
    private Map<String, Object> buildCombinedStatus(String pcIpAddress) {
        Map<String, Object> combined = new HashMap<>();
        combined.put("timestamp", System.currentTimeMillis());

        // Usa il nuovo sistema di ping per verificare lo stato del PC
        boolean pcOnline = pcPingMonitorService.isPcOnline(pcIpAddress);

        combined.put("pcIp", pcIpAddress);
        combined.put("pcOnline", pcOnline);

        // Se il PC è online, recupera le info di sistema
        if (pcOnline) {
            try {
                Map<String, String> systemInfo = systemInfoService.getSystemInfoQuick(pcIpAddress);

                if (systemInfo.containsKey("error")) {
                    combined.put("pcHostname", "N/A");
                    combined.put("pcOs", "N/A");
                    combined.put("pcUptime", "N/A");
                    combined.put("pcError", systemInfo.get("error"));
                } else {
                    combined.put("pcHostname", systemInfo.getOrDefault("hostname", "N/A"));
                    combined.put("pcOs", systemInfo.getOrDefault("os", "N/A"));
                    combined.put("pcUptime", systemInfo.getOrDefault("uptime", "N/A"));
                }
            } catch (Exception e) {
                combined.put("pcHostname", "N/A");
                combined.put("pcOs", "N/A");
                combined.put("pcUptime", "N/A");
                combined.put("pcError", "Errore recupero info: " + e.getMessage());
            }
        } else {
            combined.put("pcHostname", "N/A");
            combined.put("pcOs", "N/A");
            combined.put("pcUptime", "N/A");
            combined.put("pcError", "PC offline - nessun ping ricevuto");
        }

        // Stato della IP Cam (solo ping, NO SSH)
        String camIp = ipCamScannerService != null ? ipCamScannerService.getCurrentCamIp() : null;
        if (camIp != null && !camIp.isEmpty()) {
            Map<String, Object> camStatus = getCameraStatus(camIp);
            combined.put("camIp", camIp);
            combined.put("camOnline", camStatus.getOrDefault("online", false));
            combined.put("camRtspUrl", "rtsp://" + camIp + ":554/");

            if (camStatus.containsKey("error")) {
                combined.put("camError", camStatus.get("error"));
            }
        } else {
            combined.put("camIp", null);
            combined.put("camOnline", false);
            combined.put("camError", "IP cam non ancora trovata");
        }

        return combined;
    }

    /**
     * Ottiene lo stato della camera IP (solo ping, senza SSH)
     */
    private Map<String, Object> getCameraStatus(String ipAddress) {
        Map<String, Object> status = new HashMap<>();
        status.put("ip", ipAddress);
        status.put("timestamp", System.currentTimeMillis());

        try {
            // Ping diretto alla camera - verifica porta RTSP 554
            boolean isOnline = checkCameraOnline(ipAddress);
            status.put("online", isOnline);

            if (!isOnline) {
                status.put("error", "Camera offline - ping fallito");
            }
        } catch (Exception e) {
            status.put("online", false);
            status.put("error", "Errore controllo camera: " + e.getMessage());
        }

        return status;
    }

    /**
     * Verifica se una camera è online controllando la porta RTSP
     */
    private boolean checkCameraOnline(String ipAddress) {
        try {
            // Metodo 1: Prova ping ICMP
            InetAddress inet = InetAddress.getByName(ipAddress);
            if (inet.isReachable(1000)) {
                return true;
            }

            // Metodo 2: Verifica porta RTSP 554
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ipAddress, 554), 1000);
                return true;
            } catch (IOException e) {
                // Porta non disponibile
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Broadcast dello stato combinato a tutti i client sottoscritti
     */
    private void broadcastCombinedStatus(Map<String, Object> status) {
        combinedEmitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("systemStatus").data(status));
                return false;
            } catch (IOException e) {
                System.out.println("Errore invio SSE, rimuovo emitter");
                return true;
            }
        });
    }

    /**
     * Ottiene l'IP del PC dalla cache dei dispositivi monitorati
     */
    private String getPcIpFromCache() {
        // Prende il primo IP nella cache che non è la cam
        String camIp = ipCamScannerService != null ? ipCamScannerService.getCurrentCamIp() : null;

        for (String ip : deviceStatusCache.keySet()) {
            if (camIp == null || !ip.equals(camIp)) {
                return ip;
            }
        }

        return null;
    }
}
