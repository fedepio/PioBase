package it.PioSoft.PioBase.configs;

/**
 * Configurazione per la mappatura e gestione dei PC remoti
 *
 * Gestisce la configurazione centralizzata per:
 * - Mappatura MAC address -> IP address dei PC target
 * - Credenziali SSH per l'accesso remoto ai PC
 * - Parametri di connessione SSH (porta, timeout)
 *
 * Carica le configurazioni dal file application.properties
 * utilizzando il prefisso "pc" per tutte le proprietà correlate
 * alla gestione dei PC remoti.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pc")
public class PcMappingConfig {

    private Map<String, String> mapping = new HashMap<>();
    private Ssh ssh = new Ssh();

    public Map<String, String> getMapping() {
        return mapping;
    }

    public void setMapping(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public Ssh getSsh() {
        return ssh;
    }

    public void setSsh(Ssh ssh) {
        this.ssh = ssh;
    }

    public String getIpByMac(String macAddress) {
        return mapping.get(macAddress.toLowerCase().replace(":", "").replace("-", ""));
    }

    public static class Ssh {
        private String username = "shutdownuser";
        private String password = "password";
        private int port = 22;
        private int timeout = 5000;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }
}
