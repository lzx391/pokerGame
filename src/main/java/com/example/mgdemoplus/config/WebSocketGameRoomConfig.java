package com.example.mgdemoplus.config;

import com.example.mgdemoplus.websocket.DpGameRoomWebSocketHandler;
import com.example.mgdemoplus.websocket.DpQuickMatchWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketGameRoomConfig implements WebSocketConfigurer {

    private final DpGameRoomWebSocketHandler dpGameRoomWebSocketHandler;
    private final DpQuickMatchWebSocketHandler dpQuickMatchWebSocketHandler;

    public WebSocketGameRoomConfig(
            DpGameRoomWebSocketHandler dpGameRoomWebSocketHandler,
            DpQuickMatchWebSocketHandler dpQuickMatchWebSocketHandler) {
        this.dpGameRoomWebSocketHandler = dpGameRoomWebSocketHandler;
        this.dpQuickMatchWebSocketHandler = dpQuickMatchWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dpGameRoomWebSocketHandler, "/ws/dp-game")
                .setAllowedOriginPatterns("*");
        registry.addHandler(dpQuickMatchWebSocketHandler, "/ws/dp-quick-match")
                .setAllowedOriginPatterns("*");
    }
}
