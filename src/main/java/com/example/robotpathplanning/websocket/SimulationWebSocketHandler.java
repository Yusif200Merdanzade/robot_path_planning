package com.example.robotpathplanning.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Brauzerlə WebSocket əlaqəsi (/ws/robot).
 *
 * Qoşulan bütün brauzerləri yadda saxlayır və simulyasiyanın hər vəziyyətini
 * JSON-a çevirib hamısına göndərir. Simulyasiya məntiqi haqqında heç nə bilmir.
 */
@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final JsonMapper json;

    public SimulationWebSocketHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(Object state) {
        TextMessage message = new TextMessage(json.writeValueAsString(state));
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(message);
            } catch (IOException | IllegalStateException e) {
                // Qırılmış sessiya simulyasiya döngüsünü dayandırmasın, sadəcə siyahıdan çıxarılır
                sessions.remove(session);
            }
        }
    }
}
