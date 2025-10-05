package it.PioSoft.PioBase.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    private static final String THUMBNAIL_DIR = "thumbnails";
    private static final int THUMBNAIL_INTERVAL_SECONDS = 5; // Genera thumbnail ogni 5 secondi
    private static final int MAX_THUMBNAILS = 12; // Mantieni ultimi 12 thumbnails (1 minuto)

    private final IpCamScannerService ipCamScannerService;
    private ScheduledExecutorService scheduler;
    private Process ffmpegProcess;
    private boolean isGenerating = false;
    private String currentRtspUrl;

    public ThumbnailService(IpCamScannerService ipCamScannerService) {
        this.ipCamScannerService = ipCamScannerService;
        initializeThumbnailDirectory();
    }

    /**
     * Inizializza la directory per i thumbnails
     */
    private void initializeThumbnailDirectory() {
        try {
            Path thumbDir = Paths.get(THUMBNAIL_DIR);
            if (!Files.exists(thumbDir)) {
                Files.createDirectories(thumbDir);
                logger.info("Creata directory thumbnails: {}", thumbDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Errore creazione directory thumbnails", e);
        }
    }

    /**
     * Avvia generazione automatica thumbnails
     */
    public synchronized Map<String, Object> startThumbnailGeneration() {
        Map<String, Object> result = new HashMap<>();

        if (isGenerating) {
            result.put("success", false);
            result.put("message", "Generazione thumbnails già attiva");
            return result;
        }

        // Ottieni IP cam
        String camIp = ipCamScannerService.getCurrentCamIp();
        if (camIp == null || camIp.isEmpty()) {
            result.put("success", false);
            result.put("message", "IP cam non disponibile");
            return result;
        }

        // Costruisci URL RTSP con credenziali
        currentRtspUrl = String.format("rtsp://tony:747@%s:554/", camIp);

        // Pulisci vecchi thumbnails
        cleanThumbnailDirectory();

        // Avvia scheduler
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
            this::generateThumbnail,
            0,
            THUMBNAIL_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        isGenerating = true;

        result.put("success", true);
        result.put("message", "Generazione thumbnails avviata");
        result.put("interval", THUMBNAIL_INTERVAL_SECONDS);
        result.put("maxThumbnails", MAX_THUMBNAILS);

        logger.info("Generazione thumbnails avviata da: {}", camIp);
        return result;
    }

    /**
     * Genera un singolo thumbnail
     */
    private void generateThumbnail() {
        try {
            long timestamp = System.currentTimeMillis();
            String filename = "thumb_" + timestamp + ".jpg";
            String outputPath = THUMBNAIL_DIR + "/" + filename;

            // Comando FFmpeg per catturare frame
            String[] command = {
                "ffmpeg",
                "-rtsp_transport", "tcp",
                "-i", currentRtspUrl,
                "-vframes", "1",           // Cattura solo 1 frame
                "-q:v", "2",               // Qualità JPEG (1-31, minore = migliore)
                "-s", "320x240",           // Risoluzione thumbnail
                "-y",                      // Sovrascrivi se esiste
                outputPath
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Attendi completamento con timeout
            boolean completed = process.waitFor(3, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                logger.warn("Timeout generazione thumbnail");
                return;
            }

            if (process.exitValue() == 0) {
                logger.debug("Thumbnail generato: {}", filename);
                // Pulisci vecchi thumbnails
                cleanOldThumbnails();
            } else {
                logger.warn("Errore generazione thumbnail, exit code: {}", process.exitValue());
            }

        } catch (Exception e) {
            logger.error("Errore durante generazione thumbnail", e);
        }
    }

    /**
     * Ferma generazione thumbnails
     */
    public synchronized Map<String, Object> stopThumbnailGeneration() {
        Map<String, Object> result = new HashMap<>();

        if (!isGenerating) {
            result.put("success", true);
            result.put("message", "Generazione thumbnails non attiva");
            return result;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        isGenerating = false;
        currentRtspUrl = null;

        result.put("success", true);
        result.put("message", "Generazione thumbnails fermata");

        logger.info("Generazione thumbnails fermata");
        return result;
    }

    /**
     * Ottieni lista thumbnails disponibili
     */
    public List<Map<String, Object>> getThumbnailList() {
        List<Map<String, Object>> thumbnails = new ArrayList<>();

        try {
            File thumbDir = new File(THUMBNAIL_DIR);
            if (!thumbDir.exists()) {
                return thumbnails;
            }

            File[] files = thumbDir.listFiles((dir, name) -> name.startsWith("thumb_") && name.endsWith(".jpg"));
            if (files != null) {
                // Ordina per timestamp (più recenti prima)
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

                for (File file : files) {
                    Map<String, Object> thumb = new HashMap<>();
                    thumb.put("filename", file.getName());
                    thumb.put("url", "/thumbnails/" + file.getName());
                    thumb.put("timestamp", file.lastModified());
                    thumb.put("size", file.length());
                    thumbnails.add(thumb);
                }
            }

        } catch (Exception e) {
            logger.error("Errore lettura lista thumbnails", e);
        }

        return thumbnails;
    }

    /**
     * Ottieni ultimo thumbnail generato
     */
    public Map<String, Object> getLatestThumbnail() {
        List<Map<String, Object>> thumbnails = getThumbnailList();
        if (!thumbnails.isEmpty()) {
            return thumbnails.get(0);
        }
        return Map.of("message", "Nessun thumbnail disponibile");
    }

    /**
     * Pulisci vecchi thumbnails mantenendo solo gli ultimi N
     */
    private void cleanOldThumbnails() {
        try {
            File thumbDir = new File(THUMBNAIL_DIR);
            File[] files = thumbDir.listFiles((dir, name) -> name.startsWith("thumb_") && name.endsWith(".jpg"));

            if (files != null && files.length > MAX_THUMBNAILS) {
                // Ordina per data modifica (più vecchi prima)
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));

                // Elimina i più vecchi
                for (int i = 0; i < files.length - MAX_THUMBNAILS; i++) {
                    if (files[i].delete()) {
                        logger.debug("Eliminato vecchio thumbnail: {}", files[i].getName());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Errore pulizia vecchi thumbnails", e);
        }
    }

    /**
     * Pulisci tutti i thumbnails
     */
    private void cleanThumbnailDirectory() {
        try {
            File thumbDir = new File(THUMBNAIL_DIR);
            if (thumbDir.exists()) {
                File[] files = thumbDir.listFiles((dir, name) -> name.startsWith("thumb_") && name.endsWith(".jpg"));
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Errore pulizia directory thumbnails", e);
        }
    }

    /**
     * Ottieni stato generazione thumbnails
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isGenerating", isGenerating);
        status.put("interval", THUMBNAIL_INTERVAL_SECONDS);
        status.put("maxThumbnails", MAX_THUMBNAILS);
        status.put("currentCount", getThumbnailList().size());
        return status;
    }

    public String getThumbnailDirectory() {
        return THUMBNAIL_DIR;
    }
}

