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
        registry.addHandler(handler, "/ws/robot").setAllowedOrigins("*");
    }
}
