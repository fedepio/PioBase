package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.services.WebRtcStreamService;
import it.PioSoft.PioBase.services.IpCamScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webrtc")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class WebRtcController {

    private static final Logger logger = LoggerFactory.getLogger(WebRtcController.class);

    @Autowired
    private WebRtcStreamService webRtcStreamService;

    @Autowired
    private IpCamScannerService ipCamScannerService;

    /**
     * Avvia server WebRTC
     * POST /api/webrtc/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startWebRtc() {
        logger.info("Richiesta avvio server WebRTC");

        // Recupera IP cam dal servizio scanner
        String camIp = ipCamScannerService.getCurrentCamIp();
        if (camIp == null || camIp.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "IP cam non trovata nella configurazione"
            ));
        }

        // Costruisci URL RTSP dalla configurazione
        String rtspUrl = String.format("rtsp://%s:554/live/ch0", camIp);
        logger.info("Usando RTSP URL dalla configurazione: {}", rtspUrl);

        Map<String, Object> result = webRtcStreamService.startWebRtcServer(rtspUrl);

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }

    /**
     * Ferma server WebRTC
     * GET /api/webrtc/stop
     */
    @GetMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopWebRtc() {
        logger.info("Richiesta stop server WebRTC");
        Map<String, Object> result = webRtcStreamService.stopWebRtcServer();
        return ResponseEntity.ok(result);
    }

    /**
     * Ottieni stato server WebRTC
     * GET /api/webrtc/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = webRtcStreamService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Genera pagina test WebRTC
     * GET /api/webrtc/test-page
     */
    @GetMapping(value = "/test-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getTestPage() {
        String html = webRtcStreamService.generateWebRtcTestPage();
        return ResponseEntity.ok(html);
    }
}
