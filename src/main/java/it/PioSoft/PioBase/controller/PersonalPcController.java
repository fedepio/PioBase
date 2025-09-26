/**
 * Controller REST per la gestione remota dei PC personali
 *
 * Fornisce endpoint per il controllo remoto dei PC tramite:
 * - Wake-on-LAN: accensione remota tramite pacchetto magico
 * - Spegnimento remoto: via SSH con credenziali configurate
 * - Controllo stato: verifica se il PC è online/offline
 *
 * Tutti i PC sono identificati dal loro indirizzo MAC e mappati
 * agli IP corrispondenti tramite configurazione.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.dto.WolRequest;
import it.PioSoft.PioBase.services.PcManagementService;
import it.PioSoft.PioBase.services.PcStatusService;
import it.PioSoft.PioBase.services.RemoteShutdownService;
import it.PioSoft.PioBase.services.WakeOnLanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
public class PersonalPcController {

    @Autowired
    private WakeOnLanService wakeOnLanService;

    @Autowired
    private RemoteShutdownService remoteShutdownService;

    @Autowired
    private PcManagementService pcManagementService;

    @Autowired
    private PcStatusService pcStatusService;

    @PostMapping("/wol")
    public ResponseEntity<String> wakeOnLan(@RequestBody WolRequest request) {
        try {
            wakeOnLanService.sendWakeOnLan(request.getMacAddress(), request.getBroadcastAddress());
            return ResponseEntity.ok("Avvio computer da remoto effettuato con successo");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore: " + e.getMessage());
        }
    }

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdownPC(@RequestBody WolRequest request) {
        try {
            String ip = pcManagementService.getIpByMac(request.getMacAddress());
            if (ip == null) {
                return ResponseEntity.badRequest().body("MAC address non configurato");
            }
            remoteShutdownService.shutdownPC(ip);
            return ResponseEntity.ok("Comando spegnimento inviato con successo");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante spegnimento: " + e.getMessage());
        }
    }

    @PostMapping("/status")
    public ResponseEntity<String> checkPcStatus(@RequestBody WolRequest request) {
        try {
            String ip = pcManagementService.getIpByMac(request.getMacAddress());
            if (ip == null) {
                return ResponseEntity.badRequest().body("MAC address non configurato");
            }
            String status = pcStatusService.getPcStatus(ip);
            return ResponseEntity.ok("PC (" + ip + ") status: " + status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante controllo stato: " + e.getMessage());
        }
    }
}
