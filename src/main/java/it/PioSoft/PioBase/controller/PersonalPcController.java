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
import it.PioSoft.PioBase.services.PinEntryService;
import it.PioSoft.PioBase.services.SystemInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


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

    @Autowired
    private PinEntryService pinEntryService;

    @Autowired
    private SystemInfoService systemInfoService;

    @PostMapping("/wol")
    public ResponseEntity<String> wakeOnLan(@RequestBody WolRequest request) {
        try {
            System.out.println("Avvio PC da remoto: MAC=" + request.getMacAddress() + ", Broadcast=" + request.getBroadcastAddress());
            wakeOnLanService.sendWakeOnLan(request.getMacAddress(), request.getBroadcastAddress());

            // Avvia l'inserimento automatico del PIN in background
            String ipAddress = request.getIpAddress();
            String pin = request.getPin();

            if (ipAddress != null && !ipAddress.isEmpty()) {
                if (pin != null && !pin.isEmpty()) {
                    pinEntryService.enterPinAfterWakeUp(ipAddress, pin)
                        .thenAccept(result -> System.out.println("Risultato PIN auto-entry: " + result));

                    return ResponseEntity.ok("Avvio computer da remoto effettuato con successo. PIN sarà inserito automaticamente.");
                } else {
                    return ResponseEntity.ok("Avvio computer da remoto effettuato con successo. PIN non fornito.");
                }
            } else {
                return ResponseEntity.ok("Avvio computer da remoto effettuato con successo");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore: " + e.getMessage());
        }
    }

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdownPC(@RequestBody WolRequest request) {
        try {
            String ip = request.getIpAddress();
            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
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
            String ip = request.getIpAddress();
            System.out.println("Controllo stato PC da remoto: MAC=" + request.getMacAddress() + ", IP=" + ip);
            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
            }
            String status = pcStatusService.getPcStatus(ip);
            return ResponseEntity.ok("PC (" + ip + ") status: " + status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante controllo stato: " + e.getMessage());
        }
    }

    @PostMapping("/enterPin")
    public ResponseEntity<String> enterPin(@RequestBody WolRequest request) {
        try {
            String ip = request.getIpAddress();
            String pin = request.getPin();

            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
            }
            if (pin == null || pin.isEmpty()) {
                return ResponseEntity.badRequest().body("PIN non fornito");
            }

            System.out.println("Richiesta manuale inserimento PIN per IP: " + ip + " con PIN: " + pin);
            String result = pinEntryService.enterPinNow(ip, pin);
            System.out.println("Risultato inserimento PIN manuale: " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante inserimento PIN: " + e.getMessage());
        }
    }

    @PostMapping("/enterPinAdvanced")
    public ResponseEntity<String> enterPinAdvanced(@RequestBody WolRequest request) {
        try {
            String ip = request.getIpAddress();
            String pin = request.getPin();

            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
            }
            if (pin == null || pin.isEmpty()) {
                return ResponseEntity.badRequest().body("PIN non fornito");
            }

            System.out.println("Richiesta inserimento PIN avanzato per IP: " + ip + " con PIN: " + pin);
            String result = pinEntryService.enterPinAdvanced(ip, pin);
            System.out.println("Risultato inserimento PIN avanzato: " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante inserimento PIN avanzato: " + e.getMessage());
        }
    }

    @PostMapping("/testPin")
    public ResponseEntity<String> testPinConnection(@RequestBody WolRequest request) {
        try {
            String ip = request.getIpAddress();
            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
            }

            // Test di connessione SSH
            System.out.println("Test connessione SSH verso: " + ip);
            boolean isOnline = pcStatusService.isPcOnline(ip);

            if (!isOnline) {
                return ResponseEntity.ok("PC " + ip + " non è raggiungibile via ping");
            }

            return ResponseEntity.ok("PC " + ip + " è online e raggiungibile. Pronto per inserimento PIN.");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Errore durante test: " + e.getMessage());
        }
    }

    @PostMapping("/systemInfo")
    public ResponseEntity<Object> getSystemInfo(@RequestBody WolRequest request) {
        try {
            String ip = request.getIpAddress();
            if (ip == null || ip.isEmpty()) {
                return ResponseEntity.badRequest().body("IP address non fornito");
            }

            System.out.println("Richiesta informazioni di sistema per IP: " + ip);

            // Recupera le informazioni di sistema complete
            var systemInfo = systemInfoService.getSystemInfo(ip);

            if (systemInfo.containsKey("error")) {
                String error = (String) systemInfo.get("error");

                // Gestisci errori SSH specifici
                if (error.contains("Permission denied") || error.contains("Auth fail")) {
                    return ResponseEntity.status(401).body(Map.of(
                        "error", "Errore autenticazione SSH",
                        "details", "Le credenziali SSH configurate non sono corrette per il PC " + ip + ". Verificare username/password nelle configurazioni.",
                        "suggestion", "Controllare che l'utente 'shutdownuser' esista sul PC target e che la password sia corretta.",
                        "ip", ip
                    ));
                } else if (error.contains("Connection refused")) {
                    return ResponseEntity.status(503).body(Map.of(
                        "error", "Servizio SSH non disponibile",
                        "details", "Il servizio SSH non è attivo o non è raggiungibile sul PC " + ip,
                        "suggestion", "Verificare che SSH sia installato e abilitato sul PC target.",
                        "ip", ip
                    ));
                } else if (error.contains("timeout")) {
                    return ResponseEntity.status(504).body(Map.of(
                        "error", "Timeout connessione SSH",
                        "details", "Timeout durante la connessione SSH al PC " + ip,
                        "suggestion", "Il PC potrebbe essere spento o la rete potrebbe essere lenta.",
                        "ip", ip
                    ));
                } else {
                    return ResponseEntity.status(500).body(Map.of(
                        "error", "Errore SSH generico",
                        "details", error,
                        "ip", ip
                    ));
                }
            }

            System.out.println("Informazioni di sistema recuperate per " + ip + ": " + systemInfo);
            return ResponseEntity.ok(systemInfo);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Errore interno del server",
                "details", e.getMessage()
            ));
        }
    }
}
