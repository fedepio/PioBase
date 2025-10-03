package it.PioSoft.PioBase.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Service per la gestione delle IP Camera
 *
 * Fornisce funzionalità per:
 * - Ricerca automatica di IP cam nella rete locale all'avvio
 * - Verifica disponibilità cam su porte comuni (80, 8080, 554, 8554, 37777, 34567)
 * - Gestione configurazione cam
 * - Test RTSP per verificare se il dispositivo è una IP cam
 *
 * @author Federico
 * @version 2.0
 */
@Service
public class IpCamService {

    private static final Logger logger = LoggerFactory.getLogger(IpCamService.class);

    // Credenziali IP Cam
    private static final String CAM_USERNAME = "tony";
    private static final String CAM_PASSWORD = "747";

    // Porte comuni per IP cam (HTTP, RTSP, Dahua, Hikvision, etc.)
    private static final int[] COMMON_PORTS = {80, 8080, 554, 8554, 443, 8443, 37777, 34567, 9000};
    private static final int TIMEOUT = 1000; // Timeout connessione in ms
    private static final int SCAN_TIMEOUT = 500; // Timeout per il ping
    private static final int RTSP_TIMEOUT = 2000; // Timeout per test RTSP

    private String detectedCamIp = null;
    private int detectedCamPort = 80;
    private int detectedRtspPort = 554;
    private boolean autoScanCompleted = false;

    /**
     * Ricerca automatica della cam all'avvio del servizio
     */
    @PostConstruct
    public void init() {
        logger.info("=== Avvio ricerca automatica IP Camera ===");
        autoDiscoverCamera();
    }

    /**
     * Ricerca automatica della cam sulla rete locale
     */
    public void autoDiscoverCamera() {
        String localSubnet = detectLocalSubnet();

        if (localSubnet == null) {
            logger.error("Impossibile rilevare la subnet locale");
            autoScanCompleted = true;
            return;
        }

        logger.info("Subnet rilevata: {}.0/24", localSubnet);
        logger.info("Scansione della rete in corso...");

        List<String> foundDevices = scanNetwork(localSubnet);

        if (foundDevices.isEmpty()) {
            logger.warn("Nessuna IP camera trovata sulla rete");
        } else {
            logger.info("=== IP Camera trovata e configurata ===");
            logger.info("IP: {}:{}", detectedCamIp, detectedCamPort);
            if (detectedRtspPort > 0) {
                logger.info("RTSP disponibile su porta: {}", detectedRtspPort);
            }
        }

        autoScanCompleted = true;
    }

    /**
     * Rileva la subnet locale con metodi fallback
     */
    private String detectLocalSubnet() {
        // Metodo 1: Prova a connettersi a DNS Google
        String subnet = tryDetectViaExternalConnection();
        if (subnet != null) {
            return subnet;
        }

        // Metodo 2: Usa NetworkInterface per trovare l'IP locale
        subnet = tryDetectViaNetworkInterface();
        if (subnet != null) {
            return subnet;
        }

        logger.error("Impossibile rilevare la subnet con nessun metodo");
        return null;
    }

    /**
     * Tenta di rilevare la subnet tramite connessione esterna
     */
    private String tryDetectViaExternalConnection() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("8.8.8.8", 80), 3000);
            String localIp = socket.getLocalAddress().getHostAddress();
            socket.close();

            return extractSubnet(localIp);
        } catch (IOException e) {
            logger.warn("Metodo connessione esterna fallito: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tenta di rilevare la subnet tramite NetworkInterface
     */
    private String tryDetectViaNetworkInterface() {
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Salta interfacce loopback e non attive
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                java.util.Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Prendi solo IPv4 non loopback
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        logger.info("IP trovato via NetworkInterface: {}", ip);
                        return extractSubnet(ip);
                    }
                }
            }
        } catch (SocketException e) {
            logger.warn("Metodo NetworkInterface fallito: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Estrae la subnet da un IP (assumendo /24)
     */
    private String extractSubnet(String localIp) {
        String[] parts = localIp.split("\\.");
        if (parts.length == 4) {
            String subnet = parts[0] + "." + parts[1] + "." + parts[2];
            logger.info("IP locale rilevato: {} -> Subnet: {}.0/24", localIp, subnet);
            return subnet;
        }
        return null;
    }

    /**
     * Scansiona la rete locale per trovare IP cam
     *
     * @param subnet La subnet da scansionare (es. "192.168.1")
     * @return Lista di IP trovati con porte aperte
     */
    public List<String> scanNetwork(String subnet) {
        List<String> foundDevices = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future<CamDevice>> futures = new ArrayList<>();

        logger.info("Inizio scansione rete: {}.0/24", subnet);

        for (int i = 1; i < 255; i++) {
            final String host = subnet + "." + i;
            Future<CamDevice> future = executor.submit(() -> checkHost(host));
            futures.add(future);
        }

        CamDevice bestCandidate = null;

        for (Future<CamDevice> future : futures) {
            try {
                CamDevice device = future.get();
                if (device != null) {
                    foundDevices.add(device.ip + ":" + device.port);
                    logger.info("Dispositivo trovato: {}:{} (Porte: {})",
                        device.ip, device.port, device.openPorts);

                    // Selezione il miglior candidato (priorità a chi risponde a RTSP)
                    if (bestCandidate == null || device.supportsRtsp) {
                        bestCandidate = device;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Errore durante la scansione: {}", e.getMessage());
            }
        }

        executor.shutdown();

        if (bestCandidate != null) {
            detectedCamIp = bestCandidate.ip;
            detectedCamPort = bestCandidate.port;
            detectedRtspPort = bestCandidate.rtspPort;
            logger.info("IP Cam selezionata: {}:{} (RTSP: {})",
                detectedCamIp, detectedCamPort, detectedRtspPort);
        }

        return foundDevices;
    }

    /**
     * Verifica se un host è raggiungibile e ha porte aperte
     */
    private CamDevice checkHost(String host) {
        // Salta indirizzi comuni di router/gateway
        if (host.endsWith(".1") || host.endsWith(".254") || host.endsWith(".255")) {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(SCAN_TIMEOUT)) {
                // Host raggiungibile, verifica porte comuni delle cam
                List<Integer> openPorts = new ArrayList<>();
                int mainPort = 0;
                int rtspPort = 0;
                boolean supportsRtsp = false;

                for (int port : COMMON_PORTS) {
                    if (isPortOpen(host, port)) {
                        openPorts.add(port);

                        // Porta HTTP principale
                        if (mainPort == 0 && (port == 80 || port == 8080)) {
                            mainPort = port;
                        }

                        // Porta RTSP
                        if (port == 554 || port == 8554) {
                            rtspPort = port;
                            // Test se risponde a comandi RTSP
                            if (testRtspConnection(host, port)) {
                                supportsRtsp = true;
                                logger.info("✓ {}:{} risponde a comandi RTSP!", host, port);
                            }
                        }
                    }
                }

                if (!openPorts.isEmpty()) {
                    if (mainPort == 0) mainPort = openPorts.get(0);
                    return new CamDevice(host, mainPort, rtspPort, openPorts, supportsRtsp);
                }
            }
        } catch (IOException e) {
            // Host non raggiungibile, continua
        }
        return null;
    }

    /**
     * Testa se il dispositivo risponde a comandi RTSP
     */
    private boolean testRtspConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), RTSP_TIMEOUT);
            socket.setSoTimeout(RTSP_TIMEOUT);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Invia richiesta RTSP OPTIONS
            String request = String.format(
                "OPTIONS rtsp://%s:%d/ RTSP/1.0\r\n" +
                "CSeq: 1\r\n\r\n",
                host, port
            );
            out.write(request.getBytes());
            out.flush();

            // Leggi risposta
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);
            if (bytesRead > 0) {
                String response = new String(buffer, 0, bytesRead);
                return response.contains("RTSP");
            }
        } catch (IOException e) {
            // Non risponde a RTSP
        }
        return false;
    }

    /**
     * Verifica se una porta è aperta su un host
     */
    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ritorna l'IP della cam rilevata
     */
    public String getDetectedCamIp() {
        return detectedCamIp;
    }

    /**
     * Ritorna la porta della cam rilevata
     */
    public int getDetectedCamPort() {
        return detectedCamPort;
    }

    /**
     * Imposta manualmente l'IP della cam
     */
    public void setDetectedCamIp(String ip, int port) {
        this.detectedCamIp = ip;
        this.detectedCamPort = port;
        logger.info("IP Cam configurata manualmente: {}:{}", ip, port);
    }

    /**
     * Verifica se la cam è configurata e raggiungibile
     */
    public boolean isCamAvailable() {
        if (detectedCamIp == null) {
            return false;
        }
        return isPortOpen(detectedCamIp, detectedCamPort);
    }

    /**
     * Ritorna se la scansione automatica è stata completata
     */
    public boolean isAutoScanCompleted() {
        return autoScanCompleted;
    }

    /**
     * Costruisce l'URL dello stream RTSP con credenziali
     */
    public String getRtspStreamUrl(String streamPath) {
        if (detectedCamIp == null) {
            return null;
        }

        int port = detectedRtspPort > 0 ? detectedRtspPort : 554;

        // Formato: rtsp://username:password@ip:port/stream_path
        return String.format("rtsp://%s:%s@%s:%d/%s",
            CAM_USERNAME, CAM_PASSWORD, detectedCamIp, port, streamPath);
    }

    /**
     * Costruisce l'URL dello stream RTSP (metodo legacy)
     */
    public String getRtspUrl(String username, String password, String streamPath) {
        return getRtspStreamUrl(streamPath);
    }

    /**
     * Costruisce l'URL dello stream HTTP/MJPEG con credenziali
     */
    public String getHttpStreamUrl(String streamPath) {
        if (detectedCamIp == null) {
            return null;
        }

        // Formato con autenticazione basic
        return String.format("http://%s:%s@%s:%d/%s",
            CAM_USERNAME, CAM_PASSWORD, detectedCamIp, detectedCamPort, streamPath);
    }

    /**
     * Classe interna per rappresentare un dispositivo cam trovato
     */
    private static class CamDevice {
        String ip;
        int port;
        int rtspPort;
        List<Integer> openPorts;
        boolean supportsRtsp;

        CamDevice(String ip, int port, int rtspPort, List<Integer> openPorts, boolean supportsRtsp) {
            this.ip = ip;
            this.port = port;
            this.rtspPort = rtspPort;
            this.openPorts = openPorts;
            this.supportsRtsp = supportsRtsp;
        }
    }
}
