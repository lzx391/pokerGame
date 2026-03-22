package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏对局房间推送：单机内存维护 roomId → WebSocket 会话，不依赖 Redis。
 */
@Service
public class DpGameRoomPushService {

    private static final Logger log = LoggerFactory.getLogger(DpGameRoomPushService.class);
    private static final String ROOM_CLOSED = "{\"_ws\":\"roomClosed\"}";

    private final ObjectMapper objectMapper;
    private final DpRoomServiceImpl roomService;

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public DpGameRoomPushService(ObjectMapper objectMapper, @Lazy DpRoomServiceImpl roomService) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
    }

    public void register(String roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String roomId, WebSocketSession session) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            roomSessions.remove(roomId);
        }
    }

    public boolean hasSubscribers(String roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        return set != null && !set.isEmpty();
    }

    public void sendInitialSnapshot(WebSocketSession session, String roomId) {
        try {
            DpRoom r = roomService.getAllRooms(roomId);
            if (r == null) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(ROOM_CLOSED));
                    session.close(CloseStatus.GOING_AWAY);
                }
                return;
            }
            String json = objectMapper.writeValueAsString(r);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("WebSocket initial snapshot failed roomId={}", roomId, e);
        }
    }

    /**
     * 在 {@link DpRoomServiceImpl} 的定时任务中调用：仅当有订阅者时推送，避免空房间也序列化。
     */
    public void broadcastIfSubscribed(String roomId) {
        if (!hasSubscribers(roomId)) {
            return;
        }
        try {
            DpRoom r = roomService.getAllRooms(roomId);
            String json = r == null ? ROOM_CLOSED : objectMapper.writeValueAsString(r);
            Set<WebSocketSession> set = roomSessions.get(roomId);
            if (set == null || set.isEmpty()) {
                return;
            }
            for (WebSocketSession s : new ArrayList<>(set)) {
                if (!s.isOpen()) {
                    set.remove(s);
                    continue;
                }
                try {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(json));
                    }
                } catch (IOException e) {
                    log.debug("WebSocket send failed, closing session", e);
                    set.remove(s);
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
            if (r == null) {
                for (WebSocketSession s : new ArrayList<>(set)) {
                    try {
                        s.close(CloseStatus.GOING_AWAY);
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
                roomSessions.remove(roomId);
            }
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed roomId={}", roomId, e);
        }
    }
}
