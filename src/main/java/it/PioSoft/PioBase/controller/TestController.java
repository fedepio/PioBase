/**
 * Controller REST per test e monitoraggio del servizio
 *
 * Fornisce endpoint di utilità per:
 * - Health check: verifica che il servizio sia attivo e funzionante
 * - Monitoraggio base dello stato dell'applicazione
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.controller;


import it.PioSoft.PioBase.dto.VersionResponse;
import it.PioSoft.PioBase.services.VersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired
    private VersionService versionService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.out.println("Chiamata ricevuta");
        return ResponseEntity.ok("Servizio attivo");
    }

    @GetMapping("/version")
    public ResponseEntity<VersionResponse> version() {
        VersionResponse versionInfo = versionService.getVersionInfo();
        return ResponseEntity.ok(versionInfo);
    }
}
