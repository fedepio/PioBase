/**
 * Servizio per il controllo dello stato dei PC remoti
 *
 * Implementa la funzionalità di verifica dello stato dei PC tramite:
 * - Ping ICMP per verificare la raggiungibilità di rete
 * - Timeout configurabile per evitare attese prolungate
 * - Restituzione stato formattato (online/offline)
 *
 * Utilizza la classe InetAddress di Java per inviare ping
 * di rete e determinare se un PC è acceso e raggiungibile.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.services;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

@Service
public class PcStatusService {

    public boolean isPcOnline(String ipAddress) {
        try {
            // Prova prima con il metodo nativo Java
            InetAddress inet = InetAddress.getByName(ipAddress);
            if (inet.isReachable(3000)) {
                return true;
            }

            // Se il metodo nativo fallisce, usa il comando ping esterno
            return pingWithCommand(ipAddress);

        } catch (Exception e) {
            System.err.println("Errore durante controllo connettività per " + ipAddress + ": " + e.getMessage());
            return false;
        }
    }

    private boolean pingWithCommand(String ipAddress) {
        try {
            // Comando ping per macOS/Linux: ping -c 3 -W 5000 IP
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "3", "-W", "5000", ipAddress);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean isReachable = false;

            while ((line = reader.readLine()) != null) {
                // Controlla se la risposta contiene "bytes from" (indica successo del ping)
                if (line.contains("bytes from")) {
                    isReachable = true;
                    break;
                }
            }

            process.waitFor();
            reader.close();

            return isReachable;

        } catch (Exception e) {
            System.err.println("Errore durante ping command per " + ipAddress + ": " + e.getMessage());
            return false;
        }
    }

    public String getPcStatus(String ipAddress) {
        boolean online = isPcOnline(ipAddress);
        System.out.println("Status check per " + ipAddress + ": " + (online ? "online" : "offline"));
        return online ? "online" : "offline";
    }
}