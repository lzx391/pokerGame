package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.security.DpWebSocketAuthSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大厅快匹：{@code /ws/dp-quick-match?token=...}，握手校验 JWT。
 */
@Component
public class DpQuickMatchWebSocketHandler extends TextWebSocketHandler {

    private final DpQuickMatchPushService pushService;
    private final DpWebSocketAuthSupport webSocketAuthSupport;
    private final DpRoomService dpRoomService;

    public DpQuickMatchWebSocketHandler(
            DpQuickMatchPushService pushService,
            DpWebSocketAuthSupport webSocketAuthSupport,
            DpRoomService dpRoomService) {
        this.pushService = pushService;
        this.webSocketAuthSupport = webSocketAuthSupport;
        this.dpRoomService = dpRoomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveToken(session);
        if (token == null || token.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("missing token"));
            return;
        }
        Optional<DpUser> userOpt = webSocketAuthSupport.verifyTokenFromQuery(token);
        if (userOpt.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        String nickname = userOpt.get().getNickname();
        session.getAttributes().put("qmNickname", nickname);
        pushService.register(nickname, session);
        dpRoomService.pushQuickMatchLobbySnapshot(nickname);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        pushService.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        pushService.removeSession(session);
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
