package com.assaffin.games.wsserver;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;

import java.util.concurrent.CopyOnWriteArraySet;

public class GameWSController extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session); // Save the connection
        System.out.println("New player connected: " + session.getId());
        handleTextMessage(session,
                new TextMessage("{ \"action\": \"join_game\", \"username\": \"" + session.getId() + "\" }"));
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload(); // Get message from this client
        System.out.println("Received: " + payload);

        // Broadcast the message to all connected players
        for (WebSocketSession webSocketSession : sessions) {
            if (webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        sessions.remove(session); // Remove the closed session
        System.out.println("Player disconnected: " + session.getId());
        handleTextMessage(session,
                new TextMessage("{ \"action\": \"leave_game\", \"username\": \"" + session.getId() + "\" }"));
    }
}