package it.PioSoft.PioBase.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.out.println("Health check endpoint called");
        return ResponseEntity.ok("Servizio attivo");
    }
}
