package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
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
import java.util.Locale;
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

    /** 房间 BGM：最后一次广播的 JSON，新连接触发第二条消息（与房间快照去重无关） */
    private static final long MUSIC_SYNC_MIN_INTERVAL_MS = 400L;
    private static final int MUSIC_DISPLAY_NAME_MAX = 120;

    private final ObjectMapper objectMapper;
    private final DpRoomServiceImpl roomService;

//Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>(); 的作用是：存储房间ID和订阅者的映射关系，每个房间ID对应一个订阅者集合，集合中存储的是WebSocketSession对象，用于标识每个订阅者的连接会话。
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    /** 每个连接上次已成功下发的房间快照 JSON（按观看者过滤后）；相同则不再下发。 */
    private final Map<WebSocketSession, String> lastBroadcastPayloadBySession = new ConcurrentHashMap<>();
    /** 房间曲库播放状态：供新订阅者连接后补发一帧（与 {@link #broadcastIfSubscribed} 去重无关）。 */
    private final Map<String, String> lastRoomMusicJson = new ConcurrentHashMap<>();

    // lazy注解的作用是？在Spring容器初始化时，不立即创建对象，而是在需要时创建对象，这样可以避免循环依赖
    public DpGameRoomPushService(ObjectMapper objectMapper, @Lazy DpRoomServiceImpl roomService) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
    }

    // ====== 房间会话注册与注销相关 ======

    /**
     * 将WebSocketSession会话注册到指定房间。
     */
    public void register(String roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * 连接关闭、传输错误或广播时发现僵死会话时调用: 仅从推送订阅集合移除会话。
     */
    public void removeSessionFromRoom(String roomId, WebSocketSession session) {
        if (session == null) {
            return;
        }
        lastBroadcastPayloadBySession.remove(session);
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            roomSessions.remove(roomId);
            lastRoomMusicJson.remove(roomId);
        }
    }

    /**
     * 房间对象已从 {@link DpRoomServiceImpl} 移除时调用: 关断该房全部长连并清理本地状态。
     */
    public void shutdownSubscriptionsForRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        lastRoomMusicJson.remove(roomId);
        Set<WebSocketSession> set = roomSessions.remove(roomId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (WebSocketSession s : new ArrayList<>(set)) {
            lastBroadcastPayloadBySession.remove(s);
            try {
                if (s.isOpen()) {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(ROOM_CLOSED));
                    }
                }
            } catch (Exception e) {
                log.debug("shutdownSubscriptionsForRoom send roomClosed failed", e);
            }
            try {
                s.close(CloseStatus.GOING_AWAY);
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    // ====== 客户端消息处理模块 ======

    /**
     * 处理客户端经同一 WS 连接发来的文本消息。
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
        if (!root.has("_ws")) {
            return;
        }
        String ws = root.get("_ws").asText("");
        if ("chatSend".equals(ws)) {
            handleChatSend(session, root);
            return;
        }
        if ("roomMusicSync".equals(ws)) {
            handleRoomMusicSync(session, root);
        }
    }

    /**
     * 聊天模块: 处理房间内聊天的发送与广播
     */
    private void handleChatSend(WebSocketSession session, JsonNode root) {
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
        DpRoomBO room = roomService.getAllRooms(roomId);
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

    /**
     * 曲库BGM模块: 客户端 roomMusicSync 处理与广播
     */
    private void handleRoomMusicSync(WebSocketSession session, JsonNode root) {
        Object ridObj = session.getAttributes().get("roomId");
        if (!(ridObj instanceof String)) {
            return;
        }
        String roomId = (String) ridObj;
        String nickname = root.path("nickname").asText("").trim();
        String action = root.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        if (nickname.isEmpty()) {
            return;
        }
        if (!"play".equals(action) && !"pause".equals(action) && !"stop".equals(action)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastMs = (Long) session.getAttributes().get("_musicSyncLastMs");
        if (lastMs != null && now - lastMs < MUSIC_SYNC_MIN_INTERVAL_MS) {
            return;
        }
        DpRoomBO room = roomService.getAllRooms(roomId);
        if (room == null || !isNicknameInRoom(room, nickname)) {
            return;
        }

        long trackId = root.path("trackId").asLong(0L);
        String webPath = root.path("webPath").asText("").trim();
        String displayName = root.path("displayName").asText("").trim();
        if (displayName.length() > MUSIC_DISPLAY_NAME_MAX) {
            displayName = displayName.substring(0, MUSIC_DISPLAY_NAME_MAX);
        }

        if ("play".equals(action)) {
            if (trackId <= 0L || webPath.isEmpty() || !isSafeMusicWebPath(webPath)) {
                return;
            }
        } else {
            if (!webPath.isEmpty() && !isSafeMusicWebPath(webPath)) {
                return;
            }
        }

        session.getAttributes().put("_musicSyncLastMs", now);
        try {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("_ws", "roomMusic");
            out.put("action", action);
            out.put("byNickname", nickname);
            out.put("serverTime", now);
            if (trackId > 0L) {
                out.put("trackId", trackId);
            }
            if (!webPath.isEmpty()) {
                out.put("webPath", webPath);
            }
            if (!displayName.isEmpty()) {
                out.put("displayName", displayName);
            }
            String json = objectMapper.writeValueAsString(out);
            lastRoomMusicJson.put(roomId, json);
            broadcastRawJsonToRoom(roomId, json);
        } catch (Exception e) {
            log.warn("room music broadcast failed roomId={}", roomId, e);
        }
    }

    // ========== 推送与快照/广播相关 ==========

    /**
     * 判断房间是否有订阅者。
     */
    public boolean hasSubscribers(String roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        return set != null && !set.isEmpty();
    }

    /**
     * 发送初始房间数据给订阅者。
     */
    public void sendInitialSnapshot(WebSocketSession session, String roomId) {
        try {
            String nick = (String) session.getAttributes().get("viewerNickname");
            DpRoomBO r = roomService.getRoomSnapshotForViewer(roomId, nick);
            if (r == null) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(ROOM_CLOSED));
                    session.close(CloseStatus.GOING_AWAY);
                }
                return;
            }
            String json = objectMapper.writeValueAsString(r);
            lastBroadcastPayloadBySession.put(session, json);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            String music = lastRoomMusicJson.get(roomId);
            if (music != null && !music.isEmpty()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(music));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket initial snapshot failed roomId={}", roomId, e);
        }
    }

    /**
     * 在定时任务中调用：仅当有订阅者时序列化；若与上次推送内容相同则不下发。
     */
    public void broadcastIfSubscribed(String roomId) {
        if (!hasSubscribers(roomId)) {
            return;
        }
        try {
            DpRoomBO live = roomService.getAllRooms(roomId);
            Set<WebSocketSession> set = roomSessions.get(roomId);
            if (set == null || set.isEmpty()) {
                return;
            }
            for (WebSocketSession s : new ArrayList<>(set)) {
                if (!s.isOpen()) {
                    removeSessionFromRoom(roomId, s);
                    continue;
                }
                //定制化的json数据
                String nick = (String) s.getAttributes().get("viewerNickname");
                String json;
                if (live == null) {
                    json = ROOM_CLOSED;
                } else {
                    DpRoomBO view = roomService.snapshotForViewerFromLive(live, nick);
                    json = objectMapper.writeValueAsString(view);
                }
                String prev = lastBroadcastPayloadBySession.get(s);
                if (json.equals(prev)) {
                    log.debug("WS broadcast skip (payload unchanged) roomId={}", roomId);
                    continue;
                }
                try {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(json));
                    }
                    lastBroadcastPayloadBySession.put(s, json);
                } catch (IOException e) {
                    log.debug("WebSocket send failed, closing session", e);
                    removeSessionFromRoom(roomId, s);
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
            if (live == null) {
                for (WebSocketSession s : new ArrayList<>(set)) {
                    lastBroadcastPayloadBySession.remove(s);
                    try {
                        s.close(CloseStatus.GOING_AWAY);
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
                roomSessions.remove(roomId);
                lastRoomMusicJson.remove(roomId);
            }
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed roomId={}", roomId, e);
        }
    }

    /**
     * 向房间内所有会话推送json字符串消息（内部模块，供多处调用）。
     */
    private void broadcastRawJsonToRoom(String roomId, String json) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (WebSocketSession s : new ArrayList<>(set)) {
            if (!s.isOpen()) {
                removeSessionFromRoom(roomId, s);
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.debug("WebSocket chat send failed, closing session", e);
                removeSessionFromRoom(roomId, s);
                try {
                    s.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    // =========== 校验与工具方法（按首次引用内联到对应模块） ===========

    /**
     * 校验音乐WebPath安全，首次用于handleRoomMusicSync。
     */
    private static boolean isSafeMusicWebPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.contains("..") || path.indexOf('\\') >= 0) {
            return false;
        }
        if (!path.startsWith("/music/")) {
            return false;
        }
        String rest = path.substring("/music/".length());
        return rest.length() > 0 && rest.matches("[a-zA-Z0-9._-]+");
    }

    /**
     * 判断nickname是否在房间内，首次用于handleChatSend/handleRoomMusicSync。
     */
    private static boolean isNicknameInRoom(DpRoomBO room, String nickname) {
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
}