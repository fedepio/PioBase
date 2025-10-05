package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.services.ThumbnailService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/thumbnails")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ThumbnailController {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailController.class);

    @Autowired
    private ThumbnailService thumbnailService;

    /**
     * Avvia generazione automatica thumbnails
     * POST /api/thumbnails/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startThumbnails() {
        logger.info("Richiesta avvio generazione thumbnails");
        Map<String, Object> result = thumbnailService.startThumbnailGeneration();

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }

    /**
     * Ferma generazione thumbnails
     * GET /api/thumbnails/stop
     */
    @GetMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopThumbnails() {
        logger.info("Richiesta stop generazione thumbnails");
        Map<String, Object> result = thumbnailService.stopThumbnailGeneration();
        return ResponseEntity.ok(result);
    }

    /**
     * Ottieni lista tutti i thumbnails
     * GET /api/thumbnails/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getThumbnailList() {
        List<Map<String, Object>> thumbnails = thumbnailService.getThumbnailList();
        return ResponseEntity.ok(thumbnails);
    }

    /**
     * Ottieni ultimo thumbnail
     * GET /api/thumbnails/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestThumbnail() {
        Map<String, Object> thumbnail = thumbnailService.getLatestThumbnail();
        return ResponseEntity.ok(thumbnail);
    }

    /**
     * Ottieni stato generazione
     * GET /api/thumbnails/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = thumbnailService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Serve i file thumbnail
     * GET /api/thumbnails/thumb_123456789.jpg
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(thumbnailService.getThumbnailDirectory(), filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .body(resource);
            }

            logger.warn("Thumbnail non trovato: {}", filename);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Errore serving thumbnail: {}", filename, e);
            return ResponseEntity.status(500).build();
        }
    }
}

