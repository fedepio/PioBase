package it.PioSoft.PioBase.configs;

import it.PioSoft.PioBase.websocket.ShoppingListWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configurazione WebSocket nativo (senza STOMP/SockJS) per iOS
 */
@Configuration
@EnableWebSocket
public class NativeWebSocketConfig implements WebSocketConfigurer {

    private final ShoppingListWebSocketHandler shoppingListWebSocketHandler;

    public NativeWebSocketConfig(ShoppingListWebSocketHandler shoppingListWebSocketHandler) {
        this.shoppingListWebSocketHandler = shoppingListWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(shoppingListWebSocketHandler, "/ws-shopping-native")
                .setAllowedOrigins("*");
    }
}
