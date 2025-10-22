package it.PioSoft.PioBase.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.PioSoft.PioBase.model.ShoppingItem;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handler WebSocket nativo per notifiche push della lista della spesa
 * Compatibile con iOS URLSessionWebSocketTask
 */
@Component
public class ShoppingListWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("✅ WebSocket connesso: " + session.getId() + " (totale: " + sessions.size() + ")");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("❌ WebSocket disconnesso: " + session.getId() +
            " - Status: " + status.getCode() + " - Reason: " + status.getReason() +
            " (totale: " + sessions.size() + ")");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("⚠️ WebSocket error per " + session.getId() + ": " + exception.getMessage());
        if (session.isOpen()) {
            sessions.remove(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Per ora non gestiamo messaggi dal client
        System.out.println("📩 Messaggio ricevuto da " + session.getId() + ": " + message.getPayload());
    }

    /**
     * Invia un aggiornamento a tutti i client connessi quando un item cambia
     */
    public void broadcastItemUpdate(ShoppingItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            TextMessage message = new TextMessage(json);

            Set<WebSocketSession> closedSessions = new HashSet<>();

            synchronized (sessions) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            System.err.println("⚠️ Errore invio a " + session.getId() + ": " + e.getMessage());
                            closedSessions.add(session);
                        }
                    } else {
                        closedSessions.add(session);
                    }
                }

                // Rimuovi sessioni chiuse
                sessions.removeAll(closedSessions);
            }

            int activeSessions = sessions.size();
            if (activeSessions > 0) {
                System.out.println("📤 Broadcast item update a " + activeSessions + " client(s)");
            }
        } catch (Exception e) {
            System.err.println("❌ Errore broadcast: " + e.getMessage());
        }
    }

    /**
     * Invia notifica di cancellazione item
     */
    public void broadcastItemDelete(String itemId) {
        try {
            String json = "{\"type\":\"delete\",\"id\":\"" + itemId + "\"}";
            TextMessage message = new TextMessage(json);

            Set<WebSocketSession> closedSessions = new HashSet<>();

            synchronized (sessions) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            System.err.println("⚠️ Errore invio delete a " + session.getId() + ": " + e.getMessage());
                            closedSessions.add(session);
                        }
                    } else {
                        closedSessions.add(session);
                    }
                }

                // Rimuovi sessioni chiuse
                sessions.removeAll(closedSessions);
            }

            int activeSessions = sessions.size();
            if (activeSessions > 0) {
                System.out.println("📤 Broadcast item delete a " + activeSessions + " client(s)");
            }
        } catch (Exception e) {
            System.err.println("❌ Errore broadcast delete: " + e.getMessage());
        }
    }
}
