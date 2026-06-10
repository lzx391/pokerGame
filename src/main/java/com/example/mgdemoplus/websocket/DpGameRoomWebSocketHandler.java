package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.security.DpWebSocketAuthSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游戏页专用：ws 路径 {@code /ws/dp-game?roomId=...&token=...}，payload 与 {@code GET /dpRoom/getNowRoom} 的 JSON 一致。
 */
@Component
public class DpGameRoomWebSocketHandler extends TextWebSocketHandler {

    private final DpGameRoomPushService pushService;
    private final DpWebSocketAuthSupport webSocketAuthSupport;

    public DpGameRoomWebSocketHandler(
            DpGameRoomPushService pushService,
            DpWebSocketAuthSupport webSocketAuthSupport) {
        this.pushService = pushService;
        this.webSocketAuthSupport = webSocketAuthSupport;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = resolveRoomId(session);
        if (roomId == null || roomId.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("missing roomId"));
            return;
        }
        session.getAttributes().put("roomId", roomId);

        Optional<DpUser> viewer = webSocketAuthSupport.verifyTokenFromQuery(resolveToken(session));
        if (viewer.isPresent()) {
            DpUser u = viewer.get();
            session.getAttributes().put("viewerNickname", u.getNickname());
            session.getAttributes().put("viewerUserId", u.getId());
        }

        pushService.register(roomId, session);
        pushService.sendInitialSnapshot(session, roomId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object rid = session.getAttributes().get("roomId");
        if (rid instanceof String) {
            pushService.removeSessionFromRoom((String) rid, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object rid = session.getAttributes().get("roomId");
        if (rid instanceof String) {
            pushService.removeSessionFromRoom((String) rid, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        pushService.handleClientTextMessage(session, message.getPayload());
    }

    private static String resolveRoomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        List<String> ids = params.get("roomId");
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    private static String resolveToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        List<String> t = params.get("token");
        if (t == null || t.isEmpty()) {
            return null;
        }
        return t.get(0).trim();
    }
}
