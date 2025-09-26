package it.PioSoft.PioBase.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pc")
public class PcMappingConfig {

    private Map<String, String> mapping;
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
        private String username;
        private String password;
        private int port = 22;

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
    }
}
