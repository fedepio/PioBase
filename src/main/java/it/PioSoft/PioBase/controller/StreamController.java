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
     * Avvia lo streaming HLS automaticamente dalla IP cam rilevata
     * POST /api/stream/start-auto
     */
    @GetMapping("/start-auto")
    public ResponseEntity<Map<String, Object>> startStreamAuto() {
        logger.info("Richiesta avvio stream automatico dalla IP cam");

        // Verifica FFmpeg disponibile
        if (!hlsStreamService.isFfmpegAvailable()) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "FFmpeg non disponibile",
                "suggestion", "Installare FFmpeg e aggiungerlo al PATH di sistema"
            ));
        }

        // Usa il metodo startStreamAuto che gestisce tutto automaticamente
        Map<String, Object> result = hlsStreamService.startStreamAuto();

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(404).body(result);
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
}
