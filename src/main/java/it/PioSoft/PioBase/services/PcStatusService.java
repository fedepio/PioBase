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
import java.net.InetAddress;

@Service
public class PcStatusService {

    public boolean isPcOnline(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            // Timeout di 3 secondi
            return inet.isReachable(3000);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPcStatus(String ipAddress) {
        return isPcOnline(ipAddress) ? "online" : "offline";
    }
}