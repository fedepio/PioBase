package it.PioSoft.PioBase.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    public synchronized Map<String, Object> startWebRtcServer() {
        Map<String, Object> result = new HashMap<>();

        if (isRunning) {
            result.put("success", false);
            result.put("message", "Server WebRTC già attivo");
            return result;
        }

        // Ottieni IP cam
        String camIp = ipCamScannerService.getCurrentCamIp();
        if (camIp == null || camIp.isEmpty()) {
            result.put("success", false);
            result.put("message", "IP cam non disponibile");
            result.put("suggestion", "Attendere che la scansione rete trovi la cam");
            return result;
        }

        currentCamIp = camIp;

        try {
            // Crea configurazione MediaMTX
            createMediaMtxConfig(camIp);

            // Verifica se MediaMTX è già in esecuzione
            if (isMediaMtxAlreadyRunning()) {
                logger.info("MediaMTX già in esecuzione su porta {}, utilizzo istanza esistente", WEBRTC_RTSP_PORT);
                usingExternalMediaMtx = true;
                isRunning = true;

                result.put("success", true);
                result.put("message", "Utilizzo istanza MediaMTX esistente");
                result.put("webrtcUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/");
                result.put("whepUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/whep");
                result.put("rtspProxy", "rtsp://localhost:" + WEBRTC_RTSP_PORT + "/cam");
                result.put("camIp", camIp);
                result.put("latency", "~100-200ms");
                result.put("externalMediaMtx", true);

                return result;
            }

            // Verifica se MediaMTX è disponibile
            if (!isMediaMtxAvailable()) {
                result.put("success", false);
                result.put("message", "MediaMTX non installato");
                result.put("suggestion", "Scaricare MediaMTX da: https://github.com/bluenviron/mediamtx/releases");
                result.put("installGuide", "Estrarre mediamtx.exe nella directory del progetto o nel PATH");
                return result;
            }

            // Avvia MediaMTX
            ProcessBuilder pb = new ProcessBuilder("mediamtx", MEDIAMTX_CONFIG_FILE);
            pb.redirectErrorStream(true);
            mediaMtxProcess = pb.start();
            usingExternalMediaMtx = false;

            // Thread per loggare output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(mediaMtxProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("MediaMTX: {}", line);
                    }
                } catch (IOException e) {
                    logger.error("Errore lettura output MediaMTX", e);
                }
            }).start();

            // Attendi avvio server
            Thread.sleep(2000);

            if (mediaMtxProcess.isAlive()) {
                isRunning = true;

                result.put("success", true);
                result.put("message", "Server WebRTC avviato con successo");
                result.put("webrtcUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/");
                result.put("whepUrl", "http://localhost:" + WEBRTC_HTTP_PORT + "/cam/whep");
                result.put("rtspProxy", "rtsp://localhost:" + WEBRTC_RTSP_PORT + "/cam");
                result.put("camIp", camIp);
                result.put("latency", "~100-200ms");
                result.put("externalMediaMtx", false);

                logger.info("Server WebRTC avviato su porta {}", WEBRTC_HTTP_PORT);
                logger.info("Streaming da cam: {}", camIp);
            } else {
                result.put("success", false);
                result.put("message", "MediaMTX terminato inaspettatamente");
            }

        } catch (IOException e) {
            logger.error("Errore I/O durante avvio MediaMTX", e);
            result.put("success", false);
            result.put("message", "Errore I/O: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Thread interrotto durante avvio MediaMTX", e);
            Thread.currentThread().interrupt();
            result.put("success", false);
            result.put("message", "Operazione interrotta");
        } catch (Exception e) {
            logger.error("Errore generico durante avvio MediaMTX", e);
            result.put("success", false);
            result.put("message", "Errore: " + e.getMessage());
        }

        return result;
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
     * Crea file configurazione MediaMTX
     */
    private void createMediaMtxConfig(String camIp) throws IOException {
        String rtspSource = String.format("rtsp://tony:747@%s:554/", camIp);

        String config = String.format("""
# MediaMTX Configuration for IP Cam WebRTC Streaming
# Auto-generated

# API HTTP
api: yes
apiAddress: :9997

# Metriche
metrics: yes
metricsAddress: :9998

# Server HTTP per WebRTC
webrtc: yes
webrtcAddress: :%d
webrtcServerKey: server.key
webrtcServerCert: server.crt
webrtcAllowOrigin: "*"
webrtcTrustedProxies: []
webrtcICEServers2: []

# Proxy RTSP locale
rtspAddress: :%d
protocols: [tcp]

# HLS integrato (opzionale)
hls: yes
hlsAddress: :%d
hlsAllowOrigin: "*"

# RTP
rtpAddress: :%d
rtcpAddress: :%d

# Percorsi stream
paths:
  cam:
    source: %s
    sourceProtocol: tcp
    sourceOnDemand: no
    runOnInit: ""
    runOnDemand: ""
    runOnReady: ""
    
# Log
logLevel: info
logDestinations: [stdout]
""",
            WEBRTC_HTTP_PORT,
            WEBRTC_RTSP_PORT,
            WEBRTC_HTTP_PORT + 1,
            WEBRTC_RTP_PORT,
            WEBRTC_RTP_PORT + 1,
            rtspSource
        );

        Files.writeString(Path.of(MEDIAMTX_CONFIG_FILE), config);
        logger.info("Creato file configurazione MediaMTX: {}", MEDIAMTX_CONFIG_FILE);
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
