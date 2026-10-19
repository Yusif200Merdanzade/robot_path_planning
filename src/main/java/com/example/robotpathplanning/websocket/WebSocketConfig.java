package com.example.robotpathplanning.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Spring-ə WebSocket-i yandırmağı və {@link SimulationWebSocketHandler}-i /ws/robot ünvanında qeydiyyata almağı deyir. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SimulationWebSocketHandler handler;

    public WebSocketConfig(SimulationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // setAllowedOrigins çağırılmır: Spring standart olaraq yalnız eyni mənbədən (bu saytın öz səhifəsindən)
        // qoşulmağa icazə verir, başqa saytdan açılan qoşulma 403 alır. Serverdə (Render) mənbə proxy-nin
        // X-Forwarded-* başlıqlarından düzgün bərpa olunur (bax: application-prod.properties).
        registry.addHandler(handler, "/ws/robot");
    }
}
