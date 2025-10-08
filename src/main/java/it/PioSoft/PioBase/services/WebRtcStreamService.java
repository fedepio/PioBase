package it.PioSoft.PioBase.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio WebRTC per streaming IP cam con latenza ultra-bassa (~100ms)
 * Usa MediaMTX (ex rtsp-simple-server) come bridge RTSP -> WebRTC
 */
@Service
public class WebRtcStreamService {

    private static final Logger logger = LoggerFactory.getLogger(WebRtcStreamService.class);
    private static final int WEBRTC_HTTP_PORT = 8889;
    private static final int WEBRTC_RTSP_PORT = 8554;
    private static final int WEBRTC_RTP_PORT = 8000;
    private static final String MEDIAMTX_CONFIG_FILE = "mediamtx.yml";

    private final IpCamScannerService ipCamScannerService;
    private Process mediaMtxProcess;
    private boolean isRunning = false;
    private String currentCamIp;
    private final Map<String, Object> sessionInfo = new ConcurrentHashMap<>();
    private boolean usingExternalMediaMtx = false;

    public WebRtcStreamService(IpCamScannerService ipCamScannerService) {
        this.ipCamScannerService = ipCamScannerService;
    }

    /**
     * Verifica se MediaMTX è già in esecuzione sulla porta RTSP
     */
    private boolean isMediaMtxAlreadyRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", WEBRTC_RTSP_PORT), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Avvia server MediaMTX per WebRTC streaming
     */
    public synchronized Map<String, Object> startWebRtcServer(String rtspUrl) {
        Map<String, Object> result = new HashMap<>();

        if (isRunning) {
            logger.info("Server WebRTC già attivo, restituisco configurazione corrente");
            result.put("success", true);
            result.put("message", "Server WebRTC già attivo");
            result.put("rtspUrl", rtspUrl);
            result.put("alreadyRunning", true);
            return result;
        }

        // Usa l'URL RTSP passato come parametro
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            result.put("success", false);
            result.put("message", "URL RTSP non valido");
            return result;
        }

        // Verifica se MediaMTX è già in esecuzione (servizio esterno)
        if (isMediaMtxAlreadyRunning()) {
            logger.info("MediaMTX esterno già in esecuzione, configuro il path dinamicamente");
            usingExternalMediaMtx = true;

            // Configura MediaMTX via API con l'URL RTSP dinamico
            configureMediaMtxPath(rtspUrl);

            isRunning = true;
            result.put("success", true);
            result.put("message", "Server WebRTC configurato con successo (MediaMTX esterno)");
            result.put("rtspUrl", rtspUrl);
            return result;
        }

        // Se MediaMTX non è in esecuzione, restituisci errore
        // (assumendo che il servizio systemd lo gestisca)
        result.put("success", false);
        result.put("message", "MediaMTX non in esecuzione. Verificare che il servizio systemd sia attivo.");
        result.put("suggestion", "sudo systemctl status mediamtx");
        return result;
    }

    /**
     * Configura MediaMTX dinamicamente via API per aggiornare il path RTSP
     * con ottimizzazioni per bassa latenza
     */
    private void configureMediaMtxPath(String rtspUrl) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "http://localhost:9997/v3/config/paths/patch/cam";

            Map<String, Object> config = new HashMap<>();
            config.put("source", rtspUrl);
            config.put("sourceProtocol", "tcp");
            config.put("sourceOnDemand", false);  // Connessione sempre attiva per ridurre latenza
            config.put("readBufferCount", 2048);  // Buffer ottimizzato
            config.put("disablePublisherOverride", false);

            restTemplate.patchForObject(apiUrl, config, String.class);
            logger.info("MediaMTX configurato con RTSP ottimizzato per bassa latenza: {}", rtspUrl);
        } catch (Exception e) {
            logger.warn("Impossibile configurare path via API (potrebbe essere già configurato nel file YAML): {}", e.getMessage());
        }
    }

    /**
     * Ferma server WebRTC
     */
    public synchronized Map<String, Object> stopWebRtcServer() {
        Map<String, Object> result = new HashMap<>();

        if (!isRunning) {
            result.put("success", true);
            result.put("message", "Server WebRTC non attivo");
            return result;
        }

        // Se stiamo usando MediaMTX esterno, non lo fermiamo
        if (usingExternalMediaMtx) {
            logger.info("MediaMTX esterno in uso, non lo fermo");
            isRunning = false;
            currentCamIp = null;
            usingExternalMediaMtx = false;

            result.put("success", true);
            result.put("message", "Disconnesso da MediaMTX esterno (processo non fermato)");
            return result;
        }

        if (mediaMtxProcess != null && mediaMtxProcess.isAlive()) {
            logger.info("Fermo processo MediaMTX");
            mediaMtxProcess.destroy();

            try {
                mediaMtxProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (mediaMtxProcess.isAlive()) {
                    logger.warn("MediaMTX non si è fermato, forzo terminazione");
                    mediaMtxProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.error("Errore durante attesa termine MediaMTX", e);
                Thread.currentThread().interrupt();
            }

            mediaMtxProcess = null;
        }

        isRunning = false;
        currentCamIp = null;

        result.put("success", true);
        result.put("message", "Server WebRTC fermato");

        logger.info("Server WebRTC fermato");
        return result;
    }

    /**
     * Ottieni stato server WebRTC
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("isRunning", isRunning);
        status.put("camIp", currentCamIp);
        status.put("usingExternalMediaMtx", usingExternalMediaMtx);

        if (isRunning && mediaMtxProcess != null) {
            status.put("processAlive", mediaMtxProcess.isAlive());
            status.put("webrtcUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/");
            status.put("whepUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/whep");
            status.put("latency", "~100-200ms");
        } else if (isRunning && usingExternalMediaMtx) {
            status.put("processAlive", true);
            status.put("webrtcUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/");
            status.put("whepUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/whep");
            status.put("latency", "~100-200ms");
        }

        return status;
    }

    /**
     * Crea file configurazione MediaMTX ottimizzato per bassa latenza
     */
    private void createMediaMtxConfig(String camIp) throws IOException {
        String rtspSource = String.format("rtsp://tony:747@%s:554/", camIp);

        String config = String.format("""
# MediaMTX Configuration for IP Cam WebRTC Streaming
# Ottimizzato per latenza ultra-bassa (<200ms)
# Auto-generated

# API HTTP
api: yes
apiAddress: :9997

# Metriche
metrics: yes
metricsAddress: :9998

# Server HTTP per WebRTC - OTTIMIZZATO PER BASSA LATENZA
webrtc: yes
webrtcAddress: :%d
webrtcServerKey: server.key
webrtcServerCert: server.crt
webrtcAllowOrigin: "*"
webrtcTrustedProxies: []
webrtcICEServers2: []
# Ottimizzazioni WebRTC per ridurre latenza
webrtcICEHostNAT1To1IPs: []
webrtcICEUDPMuxAddress: ""
webrtcICETCPMuxAddress: ""

# Proxy RTSP locale con ottimizzazioni
rtspAddress: :%d
protocols: [tcp]
rtspEncryption: "no"
rtspTransport: tcp

# HLS integrato con latenza ridotta
hls: yes
hlsAddress: :%d
hlsAllowOrigin: "*"
hlsVariant: lowLatency
hlsSegmentCount: 3
hlsSegmentDuration: 1s
hlsPartDuration: 200ms
hlsSegmentMaxSize: 50M

# RTP
rtpAddress: :%d
rtcpAddress: :%d

# Ottimizzazioni globali per streaming
readTimeout: 10s
writeTimeout: 10s
readBufferCount: 512
udpMaxPayloadSize: 1472

# Percorsi stream - CONFIGURAZIONE OTTIMIZZATA
paths:
  cam:
    source: %s
    sourceProtocol: tcp
    sourceOnDemand: false
    sourceFingerprint: ""

    # Ottimizzazioni video per bassa latenza
    runOnInit: ""
    runOnDemand: ""
    runOnReady: ""
    runOnRead: ""
    runOnUnread: ""
    runOnNotReady: ""

    # Parametri buffer ottimizzati
    readBufferCount: 2048

    # Riduce il buffering per WebRTC
    disablePublisherOverride: false
    fallback: ""

    # Ottimizzazioni RTSP->WebRTC
    overridePublisher: false
    srtPublishPassphrase: ""

# Log
logLevel: info
logDestinations: [stdout]

# Ottimizzazioni performance
externalAuthenticationURL: ""
authInternalUsers: []
""",
            WEBRTC_HTTP_PORT,
            WEBRTC_RTSP_PORT,
            WEBRTC_HTTP_PORT + 1,
            WEBRTC_RTP_PORT,
            WEBRTC_RTP_PORT + 1,
            rtspSource
        );

        Files.writeString(Path.of(MEDIAMTX_CONFIG_FILE), config);
        logger.info("Creato file configurazione MediaMTX ottimizzato per bassa latenza: {}", MEDIAMTX_CONFIG_FILE);
    }

    /**
     * Verifica se MediaMTX è disponibile
     */
    private boolean isMediaMtxAvailable() {
        try {
            Process process = new ProcessBuilder("mediamtx", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Genera pagina HTML per test WebRTC
     */
    public String generateWebRtcTestPage() {
        return String.format("""
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <title>WebRTC Stream - Ultra Low Latency</title>
    <style>
        body { font-family: Arial; max-width: 1200px; margin: 20px auto; padding: 20px; }
        video { width: 100%%; max-width: 800px; background: #000; }
        .controls { margin: 20px 0; }
        button { padding: 10px 20px; margin: 5px; font-size: 16px; cursor: pointer; }
        .info { background: #e7f3ff; padding: 15px; border-radius: 5px; margin: 20px 0; }
    </style>
</head>
<body>
    <h1>🚀 WebRTC Stream - Latenza ~100ms</h1>
    
    <div class="info">
        <p><strong>Server WebRTC:</strong> http://localhost:%d/cam/</p>
        <p><strong>Latenza attesa:</strong> ~100-200ms (vs ~4-10 sec HLS)</p>
    </div>
    
    <video id="video" controls autoplay muted></video>
    
    <div class="controls">
        <button onclick="startStream()">▶️ Avvia Stream</button>
        <button onclick="stopStream()">⏹️ Ferma Stream</button>
    </div>
    
    <div id="stats"></div>
    
    <script>
    const pc = new RTCPeerConnection();
    const video = document.getElementById('video');
    
    pc.ontrack = (event) => {
        video.srcObject = event.streams[0];
    };
    
    async function startStream() {
        try {
            pc.addTransceiver('video', { direction: 'recvonly' });
            pc.addTransceiver('audio', { direction: 'recvonly' });
            
            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);
            
            const response = await fetch('http://localhost:%d/cam/whep', {
                method: 'POST',
                headers: { 'Content-Type': 'application/sdp' },
                body: offer.sdp
            });
            
            const answer = await response.text();
            await pc.setRemoteDescription(new RTCSessionDescription({
                type: 'answer',
                sdp: answer
            }));
            
            console.log('Stream WebRTC avviato!');
        } catch (error) {
            console.error('Errore:', error);
            alert('Errore: ' + error.message);
        }
    }
    
    function stopStream() {
        pc.close();
        video.srcObject = null;
    }
    </script>
</body>
</html>
""", WEBRTC_HTTP_PORT, WEBRTC_HTTP_PORT);
    }
}
