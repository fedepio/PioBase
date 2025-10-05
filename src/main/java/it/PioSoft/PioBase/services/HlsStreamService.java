package it.PioSoft.PioBase.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class HlsStreamService {

    private static final Logger logger = LoggerFactory.getLogger(HlsStreamService.class);
    private static final String HLS_OUTPUT_DIR = "hls-stream";
    private static final String STREAM_FILENAME = "stream.m3u8";

    // Credenziali RTSP
    private static final String RTSP_USERNAME = "tony";
    private static final String RTSP_PASSWORD = "747";
    private static final int RTSP_PORT = 554;

    private final IpCamScannerService ipCamScannerService;

    private Process ffmpegProcess;
    private String currentRtspUrl;
    private boolean isStreaming = false;

    public HlsStreamService(IpCamScannerService ipCamScannerService) {
        this.ipCamScannerService = ipCamScannerService;
    }

    /**
     * Avvia lo streaming RTSP -> HLS usando FFmpeg
     * Ottimizzato per client mobile iOS/Android
     * Usa automaticamente l'IP della cam rilevata dal sistema
     */
    public synchronized Map<String, Object> startStream(String rtspUrl) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Se c'è già uno stream attivo, fermalo
            if (isStreaming) {
                logger.info("Stream già attivo, fermo lo stream precedente");
                stopStream();
            }

            // Crea directory per file HLS
            Path hlsDir = Paths.get(HLS_OUTPUT_DIR);
            if (!Files.exists(hlsDir)) {
                Files.createDirectories(hlsDir);
                logger.info("Creata directory HLS: {}", hlsDir.toAbsolutePath());
            } else {
                // Pulisci vecchi file
                cleanHlsDirectory();
            }

            // Comando FFmpeg ottimizzato per mobile (iOS/Android)
            String[] command = {
                "ffmpeg",
                "-rtsp_transport", "tcp",           // TCP più stabile su mobile
                "-i", rtspUrl,                       // Input RTSP
                "-c:v", "copy",                      // Copia video (no re-encoding = risparmio batteria)
                "-c:a", "aac",                       // AAC compatibile iOS/Android
                "-ar", "44100",                      // Sample rate audio standard
                "-ac", "2",                          // Stereo
                "-f", "hls",                         // Formato HLS
                "-hls_time", "2",                    // Segmenti 2 sec (bilanciamento latenza/stabilità)
                "-hls_list_size", "5",               // 5 segmenti in playlist (10 sec buffer)
                "-hls_flags", "delete_segments+append_list", // Gestione segmenti ottimizzata
                "-hls_segment_type", "mpegts",       // Formato segmenti standard
                "-hls_allow_cache", "0",             // No cache per live streaming
                "-start_number", "0",                // Numerazione segmenti da 0
                "-hls_segment_filename", HLS_OUTPUT_DIR + "/segment%03d.ts",
                HLS_OUTPUT_DIR + "/" + STREAM_FILENAME
            };

            logger.info("Avvio FFmpeg per RTSP -> HLS (mobile optimized): {}", rtspUrl);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            ffmpegProcess = pb.start();

            // Thread per loggare output FFmpeg
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("FFmpeg: {}", line);
                    }
                } catch (IOException e) {
                    logger.error("Errore lettura output FFmpeg", e);
                }
            }).start();

            // Attendi che FFmpeg generi il primo file
            int attempts = 0;
            Path playlistPath = Paths.get(HLS_OUTPUT_DIR, STREAM_FILENAME);
            while (attempts < 30 && !Files.exists(playlistPath)) {
                Thread.sleep(200);
                attempts++;
            }

            if (Files.exists(playlistPath)) {
                currentRtspUrl = rtspUrl;
                isStreaming = true;

                result.put("success", true);
                result.put("message", "Stream HLS avviato con successo");
                result.put("playlistUrl", "/hls/" + STREAM_FILENAME);
                result.put("fullUrl", "/api/stream/hls/" + STREAM_FILENAME);
                result.put("rtspUrl", rtspUrl);
                result.put("mobileOptimized", true);

                logger.info("Stream HLS avviato (mobile optimized): {}", playlistPath.toAbsolutePath());
            } else {
                stopStream();
                result.put("success", false);
                result.put("message", "Timeout: FFmpeg non ha generato il file playlist");
            }

        } catch (IOException e) {
            logger.error("Errore I/O durante avvio stream", e);
            result.put("success", false);
            result.put("message", "Errore I/O: " + e.getMessage());
            result.put("suggestion", "Verificare che FFmpeg sia installato e nel PATH");
        } catch (InterruptedException e) {
            logger.error("Thread interrotto durante attesa FFmpeg", e);
            Thread.currentThread().interrupt();
            result.put("success", false);
            result.put("message", "Operazione interrotta");
        } catch (Exception e) {
            logger.error("Errore generico durante avvio stream", e);
            result.put("success", false);
            result.put("message", "Errore: " + e.getMessage());
        }

        return result;
    }

    /**
     * Avvia lo streaming automaticamente dalla cam rilevata
     * Usa le credenziali hardcoded e l'IP dal IpCamScannerService
     */
    public synchronized Map<String, Object> startStreamAuto() {
        Map<String, Object> result = new HashMap<>();

        // Ottieni IP della cam dal service
        String camIp = ipCamScannerService.getCurrentCamIp();

        if (camIp == null || camIp.isEmpty()) {
            logger.warn("IP cam non disponibile per avvio stream automatico");
            result.put("success", false);
            result.put("message", "IP cam non ancora trovata");
            result.put("suggestion", "Attendere che la scansione rete trovi la cam");
            return result;
        }

        // Costruisci URL RTSP con credenziali
        String rtspUrl = String.format("rtsp://%s:%s@%s:%d/",
            RTSP_USERNAME, RTSP_PASSWORD, camIp, RTSP_PORT);

        logger.info("Avvio stream automatico da IP: {} (credenziali incluse)", camIp);

        // Avvia stream con URL completo
        return startStream(rtspUrl);
    }

    /**
     * Ferma lo streaming FFmpeg
     */
    public synchronized Map<String, Object> stopStream() {
        Map<String, Object> result = new HashMap<>();

        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            logger.info("Fermo processo FFmpeg");
            ffmpegProcess.destroy();

            // Attendi termine processo
            try {
                ffmpegProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (ffmpegProcess.isAlive()) {
                    logger.warn("FFmpeg non si è fermato, forzo terminazione");
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.error("Errore durante attesa termine FFmpeg", e);
                Thread.currentThread().interrupt();
            }

            ffmpegProcess = null;
            isStreaming = false;
            currentRtspUrl = null;

            result.put("success", true);
            result.put("message", "Stream fermato con successo");

        } else {
            result.put("success", true);
            result.put("message", "Nessuno stream attivo");
        }

        return result;
    }

    /**
     * Ottieni stato dello stream
     */
    public Map<String, Object> getStreamStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("isStreaming", isStreaming);
        status.put("rtspUrl", currentRtspUrl);

        if (isStreaming && ffmpegProcess != null) {
            status.put("processAlive", ffmpegProcess.isAlive());
            status.put("playlistUrl", "/hls/" + STREAM_FILENAME);

            // Verifica esistenza file
            Path playlistPath = Paths.get(HLS_OUTPUT_DIR, STREAM_FILENAME);
            status.put("playlistExists", Files.exists(playlistPath));
        }

        return status;
    }

    /**
     * Pulisce la directory HLS da vecchi file
     */
    private void cleanHlsDirectory() {
        try {
            Path hlsDir = Paths.get(HLS_OUTPUT_DIR);
            if (Files.exists(hlsDir)) {
                Files.walk(hlsDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            logger.debug("Eliminato vecchio file HLS: {}", file);
                        } catch (IOException e) {
                            logger.warn("Impossibile eliminare file: {}", file, e);
                        }
                    });
            }
        } catch (IOException e) {
            logger.error("Errore pulizia directory HLS", e);
        }
    }

    /**
     * Verifica se FFmpeg è disponibile nel sistema
     */
    public boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ottieni path directory HLS
     */
    public String getHlsDirectory() {
        return HLS_OUTPUT_DIR;
    }

    /**
     * Ottieni nome file playlist
     */
    public String getStreamFilename() {
        return STREAM_FILENAME;
    }
}
