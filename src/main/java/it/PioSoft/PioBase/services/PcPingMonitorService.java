package it.PioSoft.PioBase.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio per monitoraggio attivo dello stato dei PC tramite ping periodici
 * Il PC invia ping ogni 3 secondi, se non riceve ping per 10 secondi marca come offline
 */
@Service
public class PcPingMonitorService {

    private final Map<String, LocalDateTime> lastPingMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pcStatusMap = new ConcurrentHashMap<>();
    private static final int PING_TIMEOUT_SECONDS = 10;

    /**
     * Riceve un ping da un PC e aggiorna il timestamp
     * @param pcIp IP del PC che invia il ping
     * @return true se il PC era offline ed è tornato online, false altrimenti
     */
    public boolean receivePing(String pcIp) {
        LocalDateTime now = LocalDateTime.now();
        lastPingMap.put(pcIp, now);

        Boolean wasOnline = pcStatusMap.get(pcIp);

        if (wasOnline == null || !wasOnline) {
            // PC appena tornato online o primo ping
            System.out.println("PC " + pcIp + " è ONLINE (ping ricevuto)");
            pcStatusMap.put(pcIp, true);
            return true; // Cambio di stato
        }

        return false; // Nessun cambio di stato
    }

    /**
     * Verifica periodicamente se i PC sono ancora online controllando l'ultimo ping
     * Esegue ogni 2 secondi per rilevamento rapido
     */
    @Scheduled(fixedDelay = 2000)
    public void checkPingTimeouts() {
        LocalDateTime now = LocalDateTime.now();

        lastPingMap.forEach((pcIp, lastPing) -> {
            long secondsSinceLastPing = ChronoUnit.SECONDS.between(lastPing, now);

            if (secondsSinceLastPing > PING_TIMEOUT_SECONDS) {
                Boolean wasOnline = pcStatusMap.get(pcIp);

                if (wasOnline == null || wasOnline) {
                    // PC è andato offline
                    System.out.println("PC " + pcIp + " è OFFLINE (nessun ping da " + secondsSinceLastPing + " secondi)");
                    pcStatusMap.put(pcIp, false);
                }
            }
        });
    }

    /**
     * Ottiene lo stato corrente di un PC
     * @param pcIp IP del PC
     * @return true se online, false se offline
     */
    public boolean isPcOnline(String pcIp) {
        return pcStatusMap.getOrDefault(pcIp, false);
    }

    /**
     * Ottiene il timestamp dell'ultimo ping ricevuto
     * @param pcIp IP del PC
     * @return LocalDateTime dell'ultimo ping o null se mai ricevuto
     */
    public LocalDateTime getLastPingTime(String pcIp) {
        return lastPingMap.get(pcIp);
    }

    /**
     * Forza un PC come offline (utile per shutdown remoto)
     * @param pcIp IP del PC
     */
    public void markAsOffline(String pcIp) {
        System.out.println("PC " + pcIp + " forzato OFFLINE");
        pcStatusMap.put(pcIp, false);
        lastPingMap.remove(pcIp);
    }
}
