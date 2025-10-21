package it.PioSoft.PioBase.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configurazione WebSocket per le notifiche push della lista della spesa
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Abilita un message broker semplice in memoria
        config.enableSimpleBroker("/topic");
        // Prefisso per i messaggi inviati dai client al server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket per la connessione
        registry.addEndpoint("/ws-shopping")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
