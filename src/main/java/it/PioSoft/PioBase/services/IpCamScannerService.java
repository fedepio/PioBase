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
    private static final long RESCAN_INTERVAL_MS = 20 * 60 * 1000; // 20 minuti in millisecondi

    private final DeviceMonitoringService monitoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String currentCamIp = null;
    private boolean isScanning = false;
    private long lastScanTime = 0; // Timestamp dell'ultima scansione

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
     * Verifica periodicamente lo stato della cam (ogni 3 secondi)
     * Esegue scansione completa della rete solo se offline E sono passati almeno 20 minuti
     */
    @Scheduled(fixedDelay = 3000)
    public void checkCamStatus() {
        if (currentCamIp == null) {
            // Se non abbiamo un IP, prova a scansionare (solo se non stiamo già scansionando)
            if (!isScanning) {
                long timeSinceLastScan = System.currentTimeMillis() - lastScanTime;
                if (timeSinceLastScan >= RESCAN_INTERVAL_MS || lastScanTime == 0) {
                    logger.info("Nessuna cam configurata, avvio scansione iniziale");
                    lastScanTime = System.currentTimeMillis();
                    scanAndSaveCamIp();
                } else {
                    long minutesRemaining = (RESCAN_INTERVAL_MS - timeSinceLastScan) / 60000;
                    logger.debug("Cam non trovata, prossima scansione tra {} minuti", minutesRemaining);
                }
            }
            return;
        }

        // Verifica se la cam è online (ping veloce - operazione leggera)
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
            // Reset del timestamp se torna online
            if (lastScanTime != 0) {
                logger.debug("Cam {} è online", currentCamIp);
            }
        }

        // Usa il broadcast del DeviceMonitoringService
        broadcastCamStatus(currentCamIp, status);

        // Se offline, ri-scansiona la rete SOLO se sono passati almeno 20 minuti
        if (!isOnline && !isScanning) {
            long timeSinceLastScan = System.currentTimeMillis() - lastScanTime;

            if (timeSinceLastScan >= RESCAN_INTERVAL_MS || lastScanTime == 0) {
                logger.warn("Cam {} è offline da tempo, avvio ri-scansione rete (ultima scansione {} minuti fa)",
                    currentCamIp, timeSinceLastScan / 60000);
                lastScanTime = System.currentTimeMillis();
                scanAndSaveCamIp();
            }
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
            // Il DeviceMonitoringService gestirà il broadcast tramite SSE
            monitoringService.updateDeviceStatus(ipAddress, status);

        } catch (Exception e) {
            logger.error("Errore broadcast stato cam", e);
        }
    }

    /**
     * Ottiene tutti gli IP locali di questo host
     */
    private Set<String> getLocalIps() {
        Set<String> localIps = new HashSet<>();
        try {
            // Aggiungi localhost
            localIps.add("127.0.0.1");
            localIps.add("192.168.1.37");
            localIps.add(InetAddress.getLocalHost().getHostAddress());

            // Aggiungi tutti gli IP delle interfacce
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        localIps.add(addr.getHostAddress());
                    }
                }
            }
            logger.debug("IP locali rilevati: {}", localIps);
        } catch (Exception e) {
            logger.error("Errore nel recuperare IP locali", e);
        }
        return localIps;
    }

    /**
     * Verifica se un dispositivo è realmente una IP cam RTSP
     */
    private boolean isRealIpCam(String ip) {
        try {
            // 1. Verifica che NON sia il nostro IP locale
            String localIp = InetAddress.getLocalHost().getHostAddress();
            if (ip.equals(localIp)) {
                logger.debug("Skip IP locale principale: {}", ip);
                return false;
            }

            // 2. Verifica che NON sia un IP di questo host
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (ip.equals(addr.getHostAddress())) {
                        logger.debug("Skip IP host (interfaccia {}): {}", iface.getName(), ip);
                        return false;
                    }
                }
            }

            // 3. Prova a connettersi con RTSP OPTIONS per verificare che sia un server RTSP
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, RTSP_PORT), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS * 2);

                // Invia comando RTSP OPTIONS
                String rtspRequest = "OPTIONS rtsp://" + ip + ":554/ RTSP/1.0\r\n" +
                                   "CSeq: 1\r\n" +
                                   "User-Agent: Java RTSP Scanner\r\n" +
                                   "\r\n";

                socket.getOutputStream().write(rtspRequest.getBytes());
                socket.getOutputStream().flush();

                // Leggi risposta
                byte[] buffer = new byte[1024];
                int bytesRead = socket.getInputStream().read(buffer);

                if (bytesRead > 0) {
                    String response = new String(buffer, 0, bytesRead);
                    // Verifica che sia una risposta RTSP valida
                    if (response.contains("RTSP/1.0") &&
                        (response.contains("200 OK") ||
                         response.contains("Public:") ||
                         response.contains("OPTIONS"))) {
                        logger.info("Server RTSP valido confermato: {}", ip);
                        return true;
                    } else {
                        logger.debug("Porta 554 aperta ma risposta non RTSP valida: {}", ip);
                    }
                }
            }

            return false;

        } catch (Exception e) {
            logger.debug("Errore verifica RTSP per {}: {}", ip, e.getMessage());
            return false;
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

        // Ottieni tutti gli IP locali da escludere
        Set<String> localIps = getLocalIps();
        logger.info("IP locali da escludere dalla scansione: {}", localIps);

        for (int i = 1; i <= 254; i++) {
            final String ip = networkPrefix + i;

            // Skip IP locali
            if (localIps.contains(ip)) {
                logger.debug("Skip IP locale nella scansione: {}", ip);
                continue;
            }

            Future<?> future = executor.submit(() -> {
                // Prima verifica porta aperta (veloce)
                if (checkPort(ip, RTSP_PORT)) {
                    logger.debug("Porta 554 aperta su: {}", ip);

                    // Poi verifica che sia realmente una IP cam
                    if (isRealIpCam(ip)) {
                        logger.info("IP Cam RTSP confermata: {}", ip);
                        foundDevices.add(ip);
                    }
                }
            });

            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_MS * 4, TimeUnit.MILLISECONDS);
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

        logger.info("Scansione completata. Trovate {} IP cam RTSP", foundDevices.size());
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
