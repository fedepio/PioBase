package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.services.HlsStreamService;
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
     * Serve i file HLS (playlist .m3u8 e segmenti .ts)
     * GET /api/stream/hls/stream.m3u8
     * GET /api/stream/hls/segment001.ts
     * Ottimizzato per iOS e Android con headers corretti
     */
    @GetMapping("/hls/{filename:.+}")
    public ResponseEntity<Resource> serveHlsFile(@PathVariable String filename) {
        try {
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

            logger.warn("File HLS non trovato o non leggibile: {}", filename);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Errore serving file HLS: {}", filename, e);
            return ResponseEntity.status(500).build();
        }
    }
}
