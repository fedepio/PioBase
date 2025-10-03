package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.dto.CamConfigRequest;
import it.PioSoft.PioBase.dto.CamScanRequest;
import it.PioSoft.PioBase.dto.CamStatusResponse;
import it.PioSoft.PioBase.services.IpCamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller REST per gestione IP Camera
 *
 * Fornisce endpoint per:
 * - Ricerca automatica della cam (eseguita all'avvio)
 * - Scansione manuale della rete per trovare IP cam
 * - Configurazione manuale della cam
 * - URL streaming video dalla cam (RTSP e HTTP)
 * - Verifica stato e disponibilità cam
 *
 * @author Federico
 * @version 2.0
 */
@RestController
@RequestMapping("/api/cam")
public class CamController {

    private static final Logger logger = LoggerFactory.getLogger(CamController.class);

    @Autowired
    private IpCamService ipCamService;

    /**
     * Scansiona la rete per trovare IP cam
     * POST /api/cam/scan
     * Body: {"subnet": "192.168.1"}
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanNetwork(@RequestBody CamScanRequest request) {
        logger.info("Richiesta scansione rete: {}", request.getSubnet());

        if (request.getSubnet() == null || request.getSubnet().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Subnet non specificata"));
        }

        List<String> foundDevices = ipCamService.scanNetwork(request.getSubnet());

        Map<String, Object> response = new HashMap<>();
        response.put("found", foundDevices.size());
        response.put("devices", foundDevices);

        if (!foundDevices.isEmpty()) {
            response.put("message", "Scansione completata. IP cam configurata automaticamente.");
            response.put("selectedCam", ipCamService.getDetectedCamIp() + ":" + ipCamService.getDetectedCamPort());
        } else {
            response.put("message", "Nessun dispositivo trovato nella subnet " + request.getSubnet());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Configura manualmente la IP cam
     * POST /api/cam/config
     * Body: {"ip": "192.168.1.100", "port": 80, "username": "admin", "password": "pass", "streamPath": "video.mjpg"}
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> configureCamera(@RequestBody CamConfigRequest request) {
        logger.info("Configurazione manuale IP cam: {}:{}", request.getIp(), request.getPort());

        if (request.getIp() == null || request.getPort() <= 0) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "IP o porta non validi"));
        }

        ipCamService.setDetectedCamIp(request.getIp(), request.getPort());

        Map<String, String> response = new HashMap<>();
        response.put("message", "IP cam configurata correttamente");
        response.put("ip", request.getIp());
        response.put("port", String.valueOf(request.getPort()));

        return ResponseEntity.ok(response);
    }

    /**
     * Verifica lo stato della IP cam
     * GET /api/cam/status
     *
     * La cam viene cercata automaticamente all'avvio del server.
     * Questo endpoint restituisce lo stato della cam rilevata.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCameraStatus() {
        String camIp = ipCamService.getDetectedCamIp();
        int camPort = ipCamService.getDetectedCamPort();
        boolean configured = camIp != null;
        boolean available = configured && ipCamService.isCamAvailable();
        boolean scanCompleted = ipCamService.isAutoScanCompleted();

        Map<String, Object> response = new HashMap<>();
        response.put("configured", configured);
        response.put("available", available);
        response.put("scanCompleted", scanCompleted);

        String message;
        if (!scanCompleted) {
            message = "Scansione automatica in corso...";
        } else if (!configured) {
            message = "IP cam non trovata. Eseguire scansione manuale o configurazione.";
        } else if (!available) {
            message = "IP cam configurata ma non raggiungibile: " + camIp + ":" + camPort;
        } else {
            message = "IP cam operativa: " + camIp + ":" + camPort;
        }

        response.put("message", message);

        if (configured) {
            response.put("ip", camIp);
            response.put("port", camPort);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Ottiene l'URL dello stream video (HTTP/MJPEG)
     * GET /api/cam/stream?path=video.mjpg
     *
     * Se la cam non è stata ancora trovata, avvia automaticamente la scansione della rete.
     * Restituisce direttamente l'URL dello stream HTTP da utilizzare nel client.
     * L'URL include già le credenziali di autenticazione.
     */
    @GetMapping("/stream")
    public ResponseEntity<Map<String, String>> getStreamUrl(
            @RequestParam(defaultValue = "video.mjpg") String path) {
        logger.info("Richiesta URL stream HTTP dalla IP cam");

        String camIp = ipCamService.getDetectedCamIp();

        // Se la cam non è configurata, avvia la ricerca automatica
        if (camIp == null) {
            logger.info("IP cam non ancora trovata, avvio scansione automatica...");

            if (!ipCamService.isAutoScanCompleted()) {
                logger.info("Scansione automatica già in corso, attesa completamento...");
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                        "message", "Scansione della rete in corso, riprova tra pochi secondi",
                        "status", "scanning"
                    ));
            }

            // Avvia una nuova scansione
            ipCamService.autoDiscoverCamera();
            camIp = ipCamService.getDetectedCamIp();

            if (camIp == null) {
                logger.error("Nessuna IP cam trovata dopo la scansione");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Nessuna IP camera trovata sulla rete",
                        "message", "Verifica che la cam sia accesa e connessa alla stessa rete"
                    ));
            }

            logger.info("IP cam trovata automaticamente: {}", camIp);
        }

        if (!ipCamService.isCamAvailable()) {
            logger.error("IP cam non disponibile: {}", camIp);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "IP cam non raggiungibile",
                    "ip", camIp
                ));
        }

        String streamUrl = ipCamService.getHttpStreamUrl(path);
        logger.info("URL stream HTTP generato: {}", streamUrl.replaceAll("://.*@", "://***:***@"));

        Map<String, String> response = new HashMap<>();
        response.put("streamUrl", streamUrl);
        response.put("type", "HTTP/MJPEG");
        response.put("ip", camIp);
        response.put("message", "Utilizzare questo URL per visualizzare lo stream");

        return ResponseEntity.ok(response);
    }

    /**
     * Ottiene l'URL dello stream RTSP
     * GET /api/cam/rtsp-url?path=stream1
     *
     * Restituisce l'URL RTSP completo di credenziali.
     */
    @GetMapping("/rtsp-url")
    public ResponseEntity<Map<String, String>> getRtspUrl(
            @RequestParam(defaultValue = "stream1") String path) {

        if (ipCamService.getDetectedCamIp() == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "IP cam non configurata"));
        }

        String rtspUrl = ipCamService.getRtspStreamUrl(path);

        Map<String, String> response = new HashMap<>();
        response.put("rtspUrl", rtspUrl);
        response.put("type", "RTSP");
        response.put("message", "Utilizzare questo URL con un player RTSP (es. VLC)");

        return ResponseEntity.ok(response);
    }

    /**
     * Riavvia la ricerca automatica della cam
     * POST /api/cam/rediscover
     */
    @PostMapping("/rediscover")
    public ResponseEntity<Map<String, String>> rediscoverCamera() {
        logger.info("Richiesta ricerca automatica IP cam");

        ipCamService.autoDiscoverCamera();

        String camIp = ipCamService.getDetectedCamIp();
        if (camIp != null) {
            return ResponseEntity.ok(Map.of(
                "message", "IP cam trovata e configurata",
                "ip", camIp,
                "port", String.valueOf(ipCamService.getDetectedCamPort())
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "message", "Nessuna IP cam trovata sulla rete"
            ));
        }
    }

    /**
     * Lista tutti i dispositivi trovati nella scansione
     * GET /api/cam/devices
     */
    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> listDevices() {
        logger.info("Richiesta lista dispositivi trovati");

        String camIp = ipCamService.getDetectedCamIp();

        if (camIp == null) {
            logger.info("Nessuna cam configurata, avvio scansione...");
            ipCamService.autoDiscoverCamera();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("selectedCam", camIp);
        response.put("message", camIp != null ?
            "Camera selezionata: " + camIp :
            "Nessuna camera trovata. Controlla i log del server per vedere tutti i dispositivi rilevati.");

        return ResponseEntity.ok(response);
    }
}
