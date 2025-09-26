/**
 * Configurazione del server web embedded Tomcat
 *
 * Gestisce la configurazione personalizzata del server per:
 * - Binding su tutte le interfacce di rete (0.0.0.0)
 * - Accessibilità del servizio da reti remote
 * - Configurazione factory Tomcat per deployment su Raspberry Pi
 *
 * Questa configurazione consente al server di essere raggiungibile
 * dall'esterno della rete locale, necessario per il controllo remoto
 * dei PC da qualsiasi posizione.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.configs;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.UnknownHostException;

@Configuration
public class ServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            try {
                factory.setAddress(java.net.InetAddress.getByName("0.0.0.0"));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
