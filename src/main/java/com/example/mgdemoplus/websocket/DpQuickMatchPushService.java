package com.example.mgdemoplus.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅默认快匹：按昵称维护订阅会话，服务端主动推送 {@code WAITING} / {@code MATCHED} / {@code IDLE}。
 */
@Service
public class DpQuickMatchPushService {

    private static final Logger log = LoggerFactory.getLogger(DpQuickMatchPushService.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> nicknameSessions = new ConcurrentHashMap<>();

    public DpQuickMatchPushService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String nickname, WebSocketSession session) {
        if (nickname == null || nickname.isBlank() || session == null) {
            return;
        }
        nicknameSessions
                .computeIfAbsent(nickname, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }
        Object nickObj = session.getAttributes().get("qmNickname");
        if (!(nickObj instanceof String nickname) || nickname.isBlank()) {
            return;
        }
        Set<WebSocketSession> set = nicknameSessions.get(nickname);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                nicknameSessions.remove(nickname, set);
            }
        }
    }

    /** 队列位置 1 为队首。 */
    public void notifyWaiting(String nickname, int queuePosition) {
        send(
                nickname,
                Map.of(
                        "_ws", "quickMatch",
                        "state", "WAITING",
                        "queuePosition", queuePosition));
    }

    public void notifyMatched(String nickname, String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        send(
                nickname,
                Map.of(
                        "_ws", "quickMatch",
                        "state", "MATCHED",
                        "roomId", roomId));
    }

    public void notifyIdle(String nickname, String message) {
        send(
                nickname,
                Map.of(
                        "_ws", "quickMatch",
                        "state", "IDLE",
                        "message", message != null ? message : ""));
    }

    private void send(String nickname, Map<String, ?> payload) {
        Set<WebSocketSession> set = nicknameSessions.get(nickname);
        if (set == null || set.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("quickMatch push serialize failed: {}", e.toString());
            return;
        }
        TextMessage tm = new TextMessage(json);
        for (WebSocketSession s : Set.copyOf(set)) {
            if (s == null || !s.isOpen()) {
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(tm);
                }
            } catch (Exception e) {
                log.debug("quickMatch push send failed: {}", e.toString());
            }
        }
    }
}
