package it.PioSoft.PioBase.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Service
public class IpCamScannerService {

    private static final Logger logger = LoggerFactory.getLogger(IpCamScannerService.class);
    private static final int RTSP_PORT = 554;
    private static final int TIMEOUT_MS = 500;
    private static final int PING_TIMEOUT_MS = 1000;
    private static final int MAX_THREADS = 50;
    private static final String CAM_CONFIG_DIR = "config";
    private static final String CAM_CONFIG_FILE = "ipcam.json";

    private final DeviceMonitoringService monitoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String currentCamIp = null;
    private boolean isScanning = false;

    public IpCamScannerService(DeviceMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
        // Registra questo service nel monitoring service per evitare dipendenze circolari
        monitoringService.setIpCamScannerService(this);
        initializeConfigDirectory();
        loadCamIpFromConfig();
    }

    /**
     * Inizializza la directory di configurazione
     */
    private void initializeConfigDirectory() {
        try {
            Path configPath = Paths.get(CAM_CONFIG_DIR);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                logger.info("Creata directory configurazione: {}", configPath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Errore creazione directory config", e);
        }
    }

    /**
     * Carica l'IP della cam dal file JSON
     */
    private void loadCamIpFromConfig() {
        try {
            File configFile = new File(CAM_CONFIG_DIR, CAM_CONFIG_FILE);
            if (configFile.exists()) {
                Map<String, Object> config = objectMapper.readValue(configFile, Map.class);
                currentCamIp = (String) config.get("ip");
                logger.info("IP cam caricato da config: {}", currentCamIp);

                // Avvia il monitoraggio della cam
                if (currentCamIp != null) {
                    startCamMonitoring();
                }
            } else {
                logger.info("Nessuna configurazione cam trovata, avvio scansione iniziale");
                scanAndSaveCamIp();
            }
        } catch (IOException e) {
            logger.error("Errore lettura configurazione cam", e);
            scanAndSaveCamIp();
        }
    }

    /**
     * Salva l'IP della cam nel file JSON
     */
    private void saveCamIpToConfig(String ip) {
        try {
            File configFile = new File(CAM_CONFIG_DIR, CAM_CONFIG_FILE);
            Map<String, Object> config = new HashMap<>();
            config.put("ip", ip);
            config.put("lastUpdate", System.currentTimeMillis());
            config.put("lastScan", new Date().toString());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            currentCamIp = ip;
            logger.info("IP cam salvato: {}", ip);
        } catch (IOException e) {
            logger.error("Errore salvataggio configurazione cam", e);
        }
    }

    /**
     * Scansiona la rete e salva il primo IP trovato con porta 554 aperta
     */
    private void scanAndSaveCamIp() {
        if (isScanning) {
            logger.info("Scansione già in corso, skip");
            return;
        }

        isScanning = true;
        logger.info("Avvio scansione rete per IP cam...");

        try {
            String network = getLocalNetwork();
            if (network == null) {
                logger.error("Impossibile rilevare la rete locale");
                return;
            }

            logger.info("Scansione rete: {}", network);
            List<String> foundDevices = scanNetworkForRtspDevices(network);

            if (!foundDevices.isEmpty()) {
                String foundIp = foundDevices.get(0);
                logger.info("IP cam trovata: {}", foundIp);

                // Se l'IP è diverso da quello precedente, aggiorna
                if (!foundIp.equals(currentCamIp)) {
                    saveCamIpToConfig(foundIp);
                    startCamMonitoring();
                }
            } else {
                logger.warn("Nessuna IP cam trovata sulla rete");
            }

        } catch (Exception e) {
            logger.error("Errore durante la scansione della rete", e);
        } finally {
            isScanning = false;
        }
    }

    /**
     * Avvia il monitoraggio della cam tramite DeviceMonitoringService
     */
    private void startCamMonitoring() {
        if (currentCamIp != null) {
            logger.info("Avvio monitoraggio cam: {}", currentCamIp);
            // Il monitoraggio avverrà automaticamente tramite il metodo schedulato checkCamStatus
        }
    }

    /**
     * Verifica periodicamente lo stato della cam
     */
    @Scheduled(fixedDelay = 3000)
    public void checkCamStatus() {
        if (currentCamIp == null) {
            // Se non abbiamo un IP, prova a scansionare
            if (!isScanning) {
                scanAndSaveCamIp();
            }
            return;
        }

        // Verifica se la cam è online
        boolean isOnline = isCamOnline(currentCamIp);

        // Prepara lo stato da broadcast
        Map<String, Object> status = new HashMap<>();
        status.put("ip", currentCamIp);
        status.put("timestamp", System.currentTimeMillis());
        status.put("online", isOnline);
        status.put("type", "ipcam");
        status.put("port", RTSP_PORT);

        if (isOnline) {
            status.put("rtspUrl", "rtsp://" + currentCamIp + ":" + RTSP_PORT + "/");
        }

        // Usa il broadcast del DeviceMonitoringService
        broadcastCamStatus(currentCamIp, status);

        // Se offline, ri-scansiona la rete
        if (!isOnline) {
            logger.warn("Cam {} è offline, avvio ri-scansione rete", currentCamIp);
            scanAndSaveCamIp();
        }
    }

    /**
     * Verifica se la cam è online tramite ping sulla porta RTSP
     */
    private boolean isCamOnline(String ip) {
        try {
            // Prima prova un ping ICMP veloce
            InetAddress inet = InetAddress.getByName(ip);
            if (!inet.isReachable(PING_TIMEOUT_MS)) {
                return false;
            }

            // Poi verifica la porta RTSP
            return checkPort(ip, RTSP_PORT);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Broadcast dello stato della cam usando il sistema esistente
     */
    private void broadcastCamStatus(String ipAddress, Map<String, Object> status) {
        // Utilizza il metodo broadcast del DeviceMonitoringService
        // tramite riflessione o esponendo un metodo pubblico
        try {
            // Per ora usiamo un approccio diretto
            logger.debug("Stato cam {}: {}", ipAddress, status.get("online"));

            // Il DeviceMonitoringService gestirà il broadcast tramite SSE
            monitoringService.updateDeviceStatus(ipAddress, status);

        } catch (Exception e) {
            logger.error("Errore broadcast stato cam", e);
        }
    }

    /**
     * Rileva la rete locale (es: 192.168.1.0/24)
     * Metodo migliorato che usa le interfacce di rete locali
     */
    private String getLocalNetwork() {
        try {
            // Metodo 1: Usa NetworkInterface per trovare IP locale
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Salta interfacce down o loopback
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Cerca solo indirizzi IPv4 privati
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        String ip = addr.getHostAddress();
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) {
                            logger.info("Rilevata rete locale da interfaccia {}: {}",
                                networkInterface.getName(), ip);
                            return parts[0] + "." + parts[1] + "." + parts[2] + ".";
                        }
                    }
                }
            }

            // Metodo 2: Fallback - prova connessione con timeout più lungo
            logger.warn("Nessuna interfaccia di rete trovata, provo metodo fallback");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("8.8.8.8", 80), 5000);
                String localIp = socket.getLocalAddress().getHostAddress();
                String[] parts = localIp.split("\\.");
                if (parts.length == 4) {
                    logger.info("Rilevata rete locale via fallback: {}", localIp);
                    return parts[0] + "." + parts[1] + "." + parts[2] + ".";
                }
            }

        } catch (Exception e) {
            logger.error("Errore nel rilevare la rete locale", e);
        }
        return null;
    }

    /**
     * Scansiona la rete per dispositivi con porta RTSP aperta
     */
    private List<String> scanNetworkForRtspDevices(String networkPrefix) {
        List<String> foundDevices = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            final String ip = networkPrefix + i;

            Future<?> future = executor.submit(() -> {
                if (checkPort(ip, RTSP_PORT)) {
                    logger.info("Dispositivo RTSP trovato: {}", ip);
                    foundDevices.add(ip);
                }
            });

            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                // Ignora errori
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Scansione completata. Trovati {} dispositivi", foundDevices.size());
        return new ArrayList<>(foundDevices);
    }

    /**
     * Verifica se una porta specifica è aperta su un IP
     */
    private boolean checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ottiene l'IP corrente della cam
     */
    public String getCurrentCamIp() {
        return currentCamIp;
    }

    /**
     * Forza una nuova scansione della rete
     */
    public void forceScan() {
        logger.info("Scansione forzata richiesta");
        scanAndSaveCamIp();
    }
}
