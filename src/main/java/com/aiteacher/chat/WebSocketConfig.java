package com.aiteacher.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket wiring: exposes the ask-the-teacher chat at {@code /ws/ask}.
 * Allowed origins are open so the packaged app can be reached from any
 * preview/proxy host during development.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AskTeacherWebSocketHandler askTeacherHandler;

    public WebSocketConfig(AskTeacherWebSocketHandler askTeacherHandler) {
        this.askTeacherHandler = askTeacherHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(askTeacherHandler, "/ws/ask")
                .setAllowedOriginPatterns("*");
    }
}
