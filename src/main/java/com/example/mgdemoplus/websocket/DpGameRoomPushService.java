package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏对局房间推送：单机内存维护 roomId → WebSocket 会话，不依赖 Redis。
 */
@Service
public class DpGameRoomPushService {

    private static final Logger log = LoggerFactory.getLogger(DpGameRoomPushService.class);
    private static final String ROOM_CLOSED = "{\"_ws\":\"roomClosed\"}";

    /** 房间聊天：广播后由前端在 ttl 到时移除，不落库 */
    private static final long CHAT_TTL_MS = 15_000L;
    private static final int CHAT_MAX_LEN = 200;
    private static final long CHAT_MIN_INTERVAL_MS = 1_200L;

    private final ObjectMapper objectMapper;
    private final DpRoomServiceImpl roomService;

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    /** 上次已成功广播的房间 JSON；与本次序列化结果相同则不再下发，节省流量。 */
    private final Map<String, String> lastBroadcastPayload = new ConcurrentHashMap<>();

    public DpGameRoomPushService(ObjectMapper objectMapper, @Lazy DpRoomServiceImpl roomService) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
    }

    public void register(String roomId, WebSocketSession session) {
        //已学习，注册房间订阅者
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
            lastBroadcastPayload.remove(roomId);
        }
    }

    /**
     * 处理客户端经同一 WS 连接发来的文本：目前仅支持 {@code {"_ws":"chatSend","nickname":"…","text":"…"}}。
     * 校验发送者昵称须为当前房间玩家或观众后，向该房间所有订阅者广播 {@code _ws=chat}（不参与房间快照去重）。
     */
    public void handleClientTextMessage(WebSocketSession session, String payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.debug("WS client text not JSON: {}", payload);
            return;
        }
        if (!root.has("_ws") || !"chatSend".equals(root.get("_ws").asText())) {
            return;
        }
        Object ridObj = session.getAttributes().get("roomId");
        if (!(ridObj instanceof String)) {
            return;
        }
        String roomId = (String) ridObj;
        String nickname = root.path("nickname").asText("").trim();
        String text = root.path("text").asText("");
        text = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (nickname.isEmpty() || text.isEmpty()) {
            return;
        }
        if (text.length() > CHAT_MAX_LEN) {
            text = text.substring(0, CHAT_MAX_LEN);
        }
        long now = System.currentTimeMillis();
        Long last = (Long) session.getAttributes().get("_chatLastMs");
        if (last != null && now - last < CHAT_MIN_INTERVAL_MS) {
            return;
        }
        DpRoom room = roomService.getAllRooms(roomId);
        if (room == null || !isNicknameInRoom(room, nickname)) {
            return;
        }
        session.getAttributes().put("_chatLastMs", now);
        try {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("_ws", "chat");
            out.put("id", UUID.randomUUID().toString());
            out.put("nickname", nickname);
            out.put("text", text);
            out.put("serverTime", now);
            out.put("ttlMs", CHAT_TTL_MS);
            broadcastRawJsonToRoom(roomId, objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            log.warn("chat broadcast failed roomId={}", roomId, e);
        }
    }

    private static boolean isNicknameInRoom(DpRoom room, String nickname) {
        List<DpPlayer> players = room.getPlayers();
        if (players != null) {
            for (DpPlayer p : players) {
                if (p != null && nickname.equals(p.getNickname())) {
                    return true;
                }
            }
        }
        List<String> spectators = room.getSpectators();
        return spectators != null && spectators.contains(nickname);
    }

    private void broadcastRawJsonToRoom(String roomId, String json) {
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
                log.debug("WebSocket chat send failed, closing session", e);
                set.remove(s);
                try {
                    s.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }
//已学习，判断房间是否有订阅者，如果房间有订阅者，则返回true，如果房间没有订阅者，则返回false
    public boolean hasSubscribers(String roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        return set != null && !set.isEmpty();
    }
//已学习，发送初始房间数据给订阅者
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
            lastBroadcastPayload.put(roomId, json);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("WebSocket initial snapshot failed roomId={}", roomId, e);
        }
    }
//已学习，调用服务层的roomService.getAllRooms(roomId)获取房间数据，然后序列化成JSON字符串，然后广播给所有订阅者
    /**
     * 在 {@link DpRoomServiceImpl} 的定时任务中调用：仅当有订阅者时序列化；若与上次推送内容相同则不下发。
     */
    public void broadcastIfSubscribed(String roomId) {
        //如果房间没有订阅者，则返回，如果房间有订阅者，则获取房间数据，然后序列化成JSON字符串，然后广播给所有订阅者
        if (!hasSubscribers(roomId)) {
            return;
        }
        try {
            //正式返回房间数据给所有订阅者
            DpRoom r = roomService.getAllRooms(roomId);
            String json = r == null ? ROOM_CLOSED : objectMapper.writeValueAsString(r);
            //如果房间数据不为空，则获取上次已成功广播的房间 JSON，如果与本次序列化结果相同则不下发
            if (r != null) {
                String prev = lastBroadcastPayload.get(roomId);
                if (json.equals(prev)) {
                    log.debug("WS broadcast skip (payload unchanged) roomId={}", roomId);
                    return;
                }
            } else {
                lastBroadcastPayload.remove(roomId);
            }
            Set<WebSocketSession> set = roomSessions.get(roomId);
            if (set == null || set.isEmpty()) {
                
                return;
            }
            //遍历所有订阅者，如果订阅者不打开，则移除订阅者
            for (WebSocketSession s : new ArrayList<>(set)) {
                //如果session不打开，则移除session
                if (!s.isOpen()) {
                    set.remove(s);
                    continue;
                }
                try {
                    synchronized (s) {
                        // System.out.println("成功推送");
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
            if (r != null) {
                lastBroadcastPayload.put(roomId, json);
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
