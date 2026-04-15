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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏对局房间推送：本机内存维护 roomId → WebSocket 会话；对局状态只在持有该房的 JVM 上，
 * 客户端应连 {@code dp.game.public-ws-url}（lookup / wsRoute）所指实例。
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

    public void register(String roomId, WebSocketSession session) {
        // 已学习，注册房间订阅者
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String roomId, WebSocketSession session) {
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
     * 处理客户端经同一 WS 连接发来的文本：
     * <ul>
     * <li>{@code {"_ws":"chatSend",...}} → 房间聊天</li>
     * <li>{@code {"_ws":"roomMusicSync",...}} → 曲库 BGM 同步（不参与房间快照去重）</li>
     * </ul>
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

    /**
     * 客户端 {@code roomMusicSync}：校验昵称在房间内后广播 {@code _ws=roomMusic}，并记下最后状态供新连接补发。
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
        DpRoom room = roomService.getAllRooms(roomId);
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

    // 已学习，判断房间是否有订阅者，如果房间有订阅者，则返回true，如果房间没有订阅者，则返回false
    public boolean hasSubscribers(String roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        return set != null && !set.isEmpty();
    }

    // 已学习，发送初始房间数据给订阅者
    public void sendInitialSnapshot(WebSocketSession session, String roomId) {
        try {
            String nick = (String) session.getAttributes().get("viewerNickname");
            DpRoom r = roomService.getRoomSnapshotForViewer(roomId, nick);//通过房间id和昵称实现处理
            if (r == null) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(ROOM_CLOSED));
                    session.close(CloseStatus.GOING_AWAY);
                }
                return;
            }
            //将房间数据序列化成JSON字符串
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

    // 已学习，调用服务层的roomService.getAllRooms(roomId)获取房间数据，然后序列化成JSON字符串，然后广播给所有订阅者
    /**
     * 在 {@link DpRoomServiceImpl} 的定时任务中调用：仅当有订阅者时序列化；若与上次推送内容相同则不下发。
     */
    public void broadcastIfSubscribed(String roomId) {
        // 如果房间没有订阅者，则返回，如果房间有订阅者，则获取房间数据，然后序列化成JSON字符串，然后广播给所有订阅者
        if (!hasSubscribers(roomId)) {
            return;
        }
        try {
            DpRoom live = roomService.getAllRooms(roomId);
            Set<WebSocketSession> set = roomSessions.get(roomId);//这里是通过房间id获取所有订阅者的会话
            if (set == null || set.isEmpty()) {
                return;
            }
            for (WebSocketSession s : new ArrayList<>(set)) {
                if (!s.isOpen()) {
                    set.remove(s);
                    lastBroadcastPayloadBySession.remove(s);
                    continue;
                }
                String nick = (String) s.getAttributes().get("viewerNickname");
                String json;
                if (live == null) {
                    json = ROOM_CLOSED;
                } else {
                    DpRoom view = roomService.snapshotForViewerFromLive(live, nick);
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
                    set.remove(s);
                    lastBroadcastPayloadBySession.remove(s);
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
            if (live == null) {
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
