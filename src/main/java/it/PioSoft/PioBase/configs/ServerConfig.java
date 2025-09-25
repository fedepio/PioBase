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
