package com.example.mgdemoplus.config;

import com.example.mgdemoplus.websocket.DpGameRoomWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketGameRoomConfig implements WebSocketConfigurer {

    private final DpGameRoomWebSocketHandler dpGameRoomWebSocketHandler;

    public WebSocketGameRoomConfig(DpGameRoomWebSocketHandler dpGameRoomWebSocketHandler) {
        this.dpGameRoomWebSocketHandler = dpGameRoomWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dpGameRoomWebSocketHandler, "/ws/dp-game")
                .setAllowedOriginPatterns("*");
    }
}
