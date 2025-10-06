package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.services.HlsStreamService;
import it.PioSoft.PioBase.services.WebRtcStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller per gestire lo streaming HLS dalla IP cam
 * Gestisce conversione RTSP -> HLS tramite FFmpeg e serving dei file
 * Ottimizzato per client iOS e Android
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class StreamController {

    private static final Logger logger = LoggerFactory.getLogger(StreamController.class);

    @Autowired
    private HlsStreamService hlsStreamService;

    @Autowired
    private WebRtcStreamService webRtcStreamService;

    /**
     * Avvia lo streaming HLS da URL RTSP fornito
     * POST /api/stream/start?rtspUrl=rtsp://192.168.1.150:554/
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startStream(@RequestParam String rtspUrl) {
        logger.info("Richiesta avvio stream HLS da: {}", rtspUrl);

        // Verifica FFmpeg disponibile
        if (!hlsStreamService.isFfmpegAvailable()) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "FFmpeg non disponibile",
                "suggestion", "Installare FFmpeg e aggiungerlo al PATH di sistema"
            ));
        }

        Map<String, Object> result = hlsStreamService.startStream(rtspUrl);

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Avvia lo streaming automaticamente dalla IP cam rilevata usando MediaMTX
     * GET /api/stream/start-auto
     */
    @GetMapping("/start-auto")
    public ResponseEntity<Map<String, Object>> startStreamAuto() {
        logger.info("Richiesta avvio stream automatico dalla IP cam");

        // Usa MediaMTX invece di FFmpeg per streaming più efficiente
        Map<String, Object> result = webRtcStreamService.startWebRtcServer();

        if ((Boolean) result.get("success")) {
            // Aggiungi informazioni aggiuntive per il client
            result.put("streamType", "MediaMTX");
            result.put("webrtcEnabled", true);
            result.put("hlsEnabled", true);
            result.put("hlsUrl", "http://localhost:8890/cam/index.m3u8");
            result.put("testPage", "http://localhost:8080/api/stream/player");
            result.put("latencyWebRTC", "~100-200ms");
            result.put("latencyHLS", "~2-6s");

            logger.info("Stream MediaMTX avviato con successo");
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Ferma lo streaming HLS
     * GET /api/stream/stop
     */
    @GetMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopStream() {
        logger.info("Richiesta stop stream HLS");
        Map<String, Object> result = hlsStreamService.stopStream();
        return ResponseEntity.ok(result);
    }

    /**
     * Ottieni stato dello streaming
     * GET /api/stream/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        Map<String, Object> status = hlsStreamService.getStreamStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Endpoint intelligente per ottenere l'URL HLS migliore disponibile
     * Restituisce MediaMTX HLS se attivo, altrimenti FFmpeg HLS
     * GET /api/stream/best-hls-url
     */
    @GetMapping("/best-hls-url")
    public ResponseEntity<Map<String, Object>> getBestHlsUrl() {
        Map<String, Object> result = new HashMap<>();

        // Verifica MediaMTX (priorità)
        Map<String, Object> webrtcStatus = webRtcStreamService.getStatus();
        boolean isMediaMtxRunning = (Boolean) webrtcStatus.getOrDefault("isRunning", false);

        if (isMediaMtxRunning) {
            result.put("available", true);
            result.put("provider", "MediaMTX");
            result.put("hlsUrl", "http://localhost:8890/cam/index.m3u8");
            result.put("latency", "2-6 seconds");
            result.put("recommended", true);
            result.put("note", "HLS nativo MediaMTX - ottimale per latenza e performance");
            logger.debug("Best HLS URL: MediaMTX nativo");
            return ResponseEntity.ok(result);
        }

        // Fallback su FFmpeg
        Map<String, Object> streamStatus = hlsStreamService.getStreamStatus();
        boolean isFfmpegStreaming = (Boolean) streamStatus.getOrDefault("isStreaming", false);

        if (isFfmpegStreaming) {
            result.put("available", true);
            result.put("provider", "FFmpeg");
            result.put("hlsUrl", "http://localhost:8080/api/stream/hls/stream.m3u8");
            result.put("latency", "4-10 seconds");
            result.put("recommended", false);
            result.put("note", "HLS FFmpeg attivo. Per migliori performance, considera MediaMTX");
            logger.debug("Best HLS URL: FFmpeg");
            return ResponseEntity.ok(result);
        }

        // Nessuno stream disponibile
        result.put("available", false);
        result.put("message", "Nessuno stream HLS attivo");
        result.put("suggestions", Map.of(
            "mediamtx", "POST /api/webrtc/start (raccomandato)",
            "ffmpeg", "GET /api/stream/start-auto"
        ));
        logger.debug("Nessuno stream HLS disponibile");
        return ResponseEntity.status(503).body(result);
    }

    /**
     * Serve i file HLS (playlist .m3u8 e segmenti .ts)
     * GET /api/stream/hls/stream.m3u8
     * GET /api/stream/hls/segment001.ts
     * Ottimizzato per iOS e Android con headers corretti
     *
     * NOTA: Questo endpoint serve l'HLS generato da FFmpeg.
     * Se stai usando MediaMTX, l'HLS nativo è disponibile su http://localhost:8890/cam/
     */
    @GetMapping("/hls/{filename:.+}")
    public ResponseEntity<Resource> serveHlsFile(@PathVariable String filename) {
        try {
            // Verifica prima se lo stream è attivo
            Map<String, Object> streamStatus = hlsStreamService.getStreamStatus();
            boolean isStreaming = (Boolean) streamStatus.getOrDefault("isStreaming", false);

            // Verifica anche se MediaMTX è attivo (HLS alternativo)
            Map<String, Object> webrtcStatus = webRtcStreamService.getStatus();
            boolean isMediaMtxRunning = (Boolean) webrtcStatus.getOrDefault("isRunning", false);

            Path filePath = Paths.get(hlsStreamService.getHlsDirectory(), filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Determina content type in base all'estensione
                String contentType;
                if (filename.endsWith(".m3u8")) {
                    contentType = "application/vnd.apple.mpegurl";
                } else if (filename.endsWith(".ts")) {
                    contentType = "video/mp2t";
                } else {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // Headers per evitare caching (importante per live streaming)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    // CORS per app mobile
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*")
                    .header(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
                    // Headers per streaming ottimale
                    .header("Accept-Ranges", "bytes")
                    .header("Connection", "keep-alive")
                    .body(resource);
            }

            // File non trovato - distingui tra varie situazioni
            if (!isStreaming && !isMediaMtxRunning) {
                // Nessuno stream attivo
                logger.debug("Richiesta file HLS '{}' ma nessuno stream attivo (né FFmpeg né MediaMTX)", filename);
                return ResponseEntity.status(503) // Service Unavailable
                    .header("X-Stream-Status", "inactive")
                    .header("X-Stream-Message", "Nessuno stream attivo. Usa POST /api/stream/start-auto (FFmpeg) o POST /api/webrtc/start (MediaMTX)")
                    .build();
            } else if (!isStreaming && isMediaMtxRunning) {
                // MediaMTX attivo ma FFmpeg no - suggerisci HLS nativo di MediaMTX
                logger.debug("Richiesta file HLS FFmpeg '{}' ma solo MediaMTX è attivo. Suggerisco HLS nativo MediaMTX", filename);
                return ResponseEntity.status(503)
                    .header("X-Stream-Status", "ffmpeg-inactive-mediamtx-active")
                    .header("X-Stream-Message", "FFmpeg HLS non attivo. Usa HLS nativo MediaMTX su http://localhost:8890/cam/index.m3u8")
                    .header("X-MediaMTX-HLS-URL", "http://localhost:8890/cam/index.m3u8")
                    .build();
            } else {
                // Stream FFmpeg attivo ma file mancante
                logger.warn("File HLS '{}' non trovato nonostante stream FFmpeg sia attivo", filename);
                return ResponseEntity.status(404)
                    .header("X-Stream-Status", "active-but-file-missing")
                    .header("X-Stream-Message", "Stream attivo ma file non disponibile. Potrebbe essere in generazione...")
                    .build();
            }

        } catch (Exception e) {
            logger.error("Errore serving file HLS: {}", filename, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Serve pagina player universale per streaming (HLS + WebRTC)
     * GET /api/stream/player
     */
    @GetMapping(value = "/player", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPlayerPage() {
        String html = """
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Stream IP Camera</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a1a;
            color: #fff;
            padding: 20px;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        h1 { margin-bottom: 20px; color: #4CAF50; }
        .player-container {
            background: #000;
            border-radius: 12px;
            overflow: hidden;
            margin-bottom: 20px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.5);
        }
        video {
            width: 100%;
            height: auto;
            display: block;
            max-height: 70vh;
        }
        .controls {
            background: #2a2a2a;
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 20px;
        }
        .btn-group {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            margin-bottom: 15px;
        }
        button {
            padding: 12px 24px;
            border: none;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            flex: 1;
            min-width: 120px;
        }
        .btn-start { background: #4CAF50; color: white; }
        .btn-start:hover { background: #45a049; }
        .btn-stop { background: #f44336; color: white; }
        .btn-stop:hover { background: #da190b; }
        .btn-webrtc { background: #2196F3; color: white; }
        .btn-webrtc:hover { background: #0b7dda; }
        .btn-hls { background: #FF9800; color: white; }
        .btn-hls:hover { background: #e68900; }
        .status {
            background: #333;
            padding: 15px;
            border-radius: 8px;
            margin-top: 15px;
        }
        .status-item {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #444;
        }
        .status-item:last-child { border-bottom: none; }
        .status-label { color: #999; }
        .status-value { color: #4CAF50; font-weight: 600; }
        .info-box {
            background: #2a2a2a;
            padding: 15px;
            border-radius: 8px;
            margin-top: 15px;
        }
        .info-box h3 { color: #4CAF50; margin-bottom: 10px; }
        .info-box ul { list-style: none; padding-left: 0; }
        .info-box li { padding: 5px 0; color: #ccc; }
        .loading {
            display: none;
            text-align: center;
            padding: 20px;
            color: #4CAF50;
        }
        .loading.active { display: block; }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        .spinner {
            border: 3px solid #333;
            border-top: 3px solid #4CAF50;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 0 auto;
        }
    </style>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
</head>
<body>
    <div class="container">
        <h1>📹 IP Camera Stream</h1>
        
        <div class="player-container">
            <video id="video" controls muted playsinline></video>
            <div class="loading" id="loading">
                <div class="spinner"></div>
                <p>Caricamento stream...</p>
            </div>
        </div>
        
        <div class="controls">
            <div class="btn-group">
                <button class="btn-start" onclick="startStream()">🚀 Avvia Stream</button>
                <button class="btn-stop" onclick="stopStream()">⏹️ Ferma Stream</button>
            </div>
            <div class="btn-group">
                <button class="btn-webrtc" onclick="playWebRTC()">⚡ WebRTC (Low Latency)</button>
                <button class="btn-hls" onclick="playHLS()">📺 HLS (Compatibile)</button>
            </div>
            
            <div class="status" id="status">
                <div class="status-item">
                    <span class="status-label">Stato:</span>
                    <span class="status-value" id="statusText">Non connesso</span>
                </div>
                <div class="status-item">
                    <span class="status-label">Tipo Stream:</span>
                    <span class="status-value" id="streamType">-</span>
                </div>
                <div class="status-item">
                    <span class="status-label">Latenza:</span>
                    <span class="status-value" id="latency">-</span>
                </div>
            </div>
        </div>
        
        <div class="info-box">
            <h3>ℹ️ Informazioni</h3>
            <ul>
                <li>🚀 <strong>Avvia Stream:</strong> Inizializza MediaMTX e connette alla telecamera</li>
                <li>⚡ <strong>WebRTC:</strong> Latenza ultra-bassa (~100-200ms) - Migliore qualità</li>
                <li>📺 <strong>HLS:</strong> Massima compatibilità (~2-6s latenza) - iOS/Safari</li>
                <li>🔄 Il sistema usa MediaMTX come server di streaming</li>
            </ul>
        </div>
    </div>
    
    <script>
        const video = document.getElementById('video');
        const loading = document.getElementById('loading');
        let pc = null;
        let hls = null;
        
        async function startStream() {
            updateStatus('Avvio stream...', 'Inizializzazione', '-');
            loading.classList.add('active');
            
            try {
                const response = await fetch('/api/stream/start-auto');
                const result = await response.json();
                
                if (result.success) {
                    updateStatus('Stream pronto', 'MediaMTX attivo', result.latencyWebRTC || '~100-200ms');
                    console.log('Stream avviato:', result);
                    
                    // Prova prima WebRTC, poi HLS come fallback
                    setTimeout(() => playWebRTC(), 1000);
                } else {
                    updateStatus('Errore: ' + result.message, 'Errore', '-');
                    alert('Errore: ' + result.message);
                }
            } catch (error) {
                console.error('Errore avvio stream:', error);
                updateStatus('Errore connessione', 'Errore', '-');
                alert('Errore: ' + error.message);
            } finally {
                loading.classList.remove('active');
            }
        }
        
        async function stopStream() {
            if (pc) {
                pc.close();
                pc = null;
            }
            if (hls) {
                hls.destroy();
                hls = null;
            }
            video.srcObject = null;
            video.src = '';
            updateStatus('Stream fermato', '-', '-');
        }
        
        async function playWebRTC() {
            loading.classList.add('active');
            updateStatus('Connessione WebRTC...', 'WebRTC', '~100-200ms');
            
            try {
                if (pc) pc.close();
                
                pc = new RTCPeerConnection({
                    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
                });
                
                pc.ontrack = (event) => {
                    video.srcObject = event.streams[0];
                    video.play();
                    loading.classList.remove('active');
                    updateStatus('✅ Streaming WebRTC', 'WebRTC', '~100-200ms');
                };
                
                pc.addTransceiver('video', { direction: 'recvonly' });
                pc.addTransceiver('audio', { direction: 'recvonly' });
                
                const offer = await pc.createOffer();
                await pc.setLocalDescription(offer);
                
                const response = await fetch('http://localhost:8889/cam/whep', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/sdp' },
                    body: offer.sdp
                });
                
                if (!response.ok) throw new Error('WHEP request failed');
                
                const answer = await response.text();
                await pc.setRemoteDescription(new RTCSessionDescription({
                    type: 'answer',
                    sdp: answer
                }));
                
            } catch (error) {
                console.error('Errore WebRTC:', error);
                loading.classList.remove('active');
                updateStatus('❌ WebRTC fallito, provo HLS...', 'Fallback', '-');
                // Fallback automatico a HLS
                setTimeout(() => playHLS(), 500);
            }
        }
        
        async function playHLS() {
            loading.classList.add('active');
            updateStatus('Connessione HLS...', 'HLS', '~2-6s');
            
            try {
                if (pc) {
                    pc.close();
                    pc = null;
                }
                
                const hlsUrl = 'http://localhost:8890/cam/index.m3u8';
                
                if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    // Safari nativo
                    video.src = hlsUrl;
                    video.play();
                    loading.classList.remove('active');
                    updateStatus('✅ Streaming HLS (nativo)', 'HLS', '~2-6s');
                } else if (Hls.isSupported()) {
                    // Altri browser con hls.js
                    if (hls) hls.destroy();
                    hls = new Hls({
                        enableWorker: true,
                        lowLatencyMode: true,
                        backBufferLength: 90
                    });
                    hls.loadSource(hlsUrl);
                    hls.attachMedia(video);
                    hls.on(Hls.Events.MANIFEST_PARSED, () => {
                        video.play();
                        loading.classList.remove('active');
                        updateStatus('✅ Streaming HLS', 'HLS', '~2-6s');
                    });
                    hls.on(Hls.Events.ERROR, (event, data) => {
                        console.error('HLS error:', data);
                        if (data.fatal) {
                            loading.classList.remove('active');
                            updateStatus('❌ Errore HLS', 'Errore', '-');
                        }
                    });
                } else {
                    throw new Error('HLS non supportato in questo browser');
                }
            } catch (error) {
                console.error('Errore HLS:', error);
                loading.classList.remove('active');
                updateStatus('❌ Errore HLS: ' + error.message, 'Errore', '-');
                alert('Errore HLS: ' + error.message);
            }
        }
        
        function updateStatus(status, type, latency) {
            document.getElementById('statusText').textContent = status;
            document.getElementById('streamType').textContent = type;
            document.getElementById('latency').textContent = latency;
        }
        
        // Auto-start on load (opzionale)
        // window.addEventListener('load', () => setTimeout(startStream, 500));
    </script>
</body>
</html>
""";
        return ResponseEntity.ok(html);
    }
}
