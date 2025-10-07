package it.PioSoft.PioBase.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.net.Socket;

/**
 * Servizio per monitoraggio attivo dello stato dei PC tramite ping periodici
 * Il server fa ping attivi ai PC registrati ogni 2 secondi
 */
@Service
public class PcPingMonitorService {

    private final Map<String, LocalDateTime> lastPingMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pcStatusMap = new ConcurrentHashMap<>();
    private final Set<String> monitoredPcs = ConcurrentHashMap.newKeySet();
    private static final int PING_TIMEOUT_MS = 2000; // 2 secondi timeout per ping
    private static final int OFFLINE_THRESHOLD_SECONDS = 10;

    /**
     * Registra un PC per il monitoraggio attivo
     * @param pcIp IP del PC da monitorare
     */
    public void registerPcForMonitoring(String pcIp) {
        if (!monitoredPcs.contains(pcIp)) {
            System.out.println("Registrato PC per monitoraggio attivo: " + pcIp);
            monitoredPcs.add(pcIp);
            // Esegui ping immediato per avere subito lo stato
            checkPcStatusNow(pcIp);
        }
    }

    /**
     * Esegue un controllo immediato dello stato di un PC
     * @param pcIp IP del PC
     */
    public void checkPcStatusNow(String pcIp) {
        LocalDateTime now = LocalDateTime.now();
        boolean isReachable = doPing(pcIp);

        if (isReachable) {
            lastPingMap.put(pcIp, now);
            Boolean wasOnline = pcStatusMap.get(pcIp);
            if (wasOnline == null || !wasOnline) {
                System.out.println("PC " + pcIp + " è ONLINE (ping immediato riuscito)");
                pcStatusMap.put(pcIp, true);
            }
        } else {
            // Se ping fallisce, marca come offline
            pcStatusMap.put(pcIp, false);
            lastPingMap.put(pcIp, now.minusSeconds(OFFLINE_THRESHOLD_SECONDS + 1));
            System.out.println("PC " + pcIp + " è OFFLINE (ping immediato fallito)");
        }
    }

    /**
     * Riceve un ping da un PC e aggiorna il timestamp (per compatibilità con client che inviano ping)
     * @param pcIp IP del PC che invia il ping
     * @return true se il PC era offline ed è tornato online, false altrimenti
     */
    public boolean receivePing(String pcIp) {
        LocalDateTime now = LocalDateTime.now();
        lastPingMap.put(pcIp, now);

        // Registra automaticamente per monitoraggio
        registerPcForMonitoring(pcIp);

        Boolean wasOnline = pcStatusMap.get(pcIp);

        if (wasOnline == null || !wasOnline) {
            // PC appena tornato online o primo ping
            System.out.println("PC " + pcIp + " è ONLINE (ping ricevuto dal client)");
            pcStatusMap.put(pcIp, true);
            return true; // Cambio di stato
        }

        return false; // Nessun cambio di stato
    }

    /**
     * Esegue ping attivo verso un PC specifico
     * @param pcIp IP del PC
     * @return true se il PC è raggiungibile, false altrimenti
     */
    private boolean doPing(String pcIp) {
        try {
            // Metodo 1: Prova InetAddress.isReachable
            InetAddress address = InetAddress.getByName(pcIp);
            if (address.isReachable(PING_TIMEOUT_MS)) {
                return true;
            }

            // Metodo 2: Prova connessione socket su porta SSH (22) come fallback
            // Se il PC ha SSH attivo, è sicuramente online
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(pcIp, 22), PING_TIMEOUT_MS);
                return true;
            } catch (IOException e) {
                // Porta SSH non disponibile, ma potrebbe essere comunque online
                // Proviamo altre porte comuni
            }

            // Metodo 3: Prova porta 135 (Windows RPC) o 445 (SMB)
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(pcIp, 135), PING_TIMEOUT_MS);
                return true;
            } catch (IOException e) {
                // Ignora
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Verifica periodicamente lo stato dei PC registrati tramite ping attivo
     * Esegue ogni 2 secondi per rilevamento rapido
     */
    @Scheduled(fixedDelay = 2000)
    public void checkPingTimeouts() {
        LocalDateTime now = LocalDateTime.now();

        // Ping attivo a tutti i PC monitorati
        for (String pcIp : monitoredPcs) {
            boolean isReachable = doPing(pcIp);

            if (isReachable) {
                // Aggiorna timestamp ultimo ping riuscito
                lastPingMap.put(pcIp, now);

                Boolean wasOnline = pcStatusMap.get(pcIp);
                if (wasOnline == null || !wasOnline) {
                    System.out.println("PC " + pcIp + " è ONLINE (ping attivo riuscito)");
                    pcStatusMap.put(pcIp, true);
                }
            } else {
                // Ping fallito, controlla se è timeout
                LocalDateTime lastPing = lastPingMap.get(pcIp);

                if (lastPing != null) {
                    long secondsSinceLastPing = ChronoUnit.SECONDS.between(lastPing, now);

                    if (secondsSinceLastPing > OFFLINE_THRESHOLD_SECONDS) {
                        Boolean wasOnline = pcStatusMap.get(pcIp);
                        if (wasOnline == null || wasOnline) {
                            System.out.println("PC " + pcIp + " è OFFLINE (nessun ping riuscito da " + secondsSinceLastPing + " secondi)");
                            pcStatusMap.put(pcIp, false);
                        }
                    }
                } else {
                    // Primo ping fallito, marca come offline
                    pcStatusMap.put(pcIp, false);
                    lastPingMap.put(pcIp, now.minusSeconds(OFFLINE_THRESHOLD_SECONDS + 1));
                }
            }
        }
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
