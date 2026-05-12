package com.example.mgdemoplus.websocket;

import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 大厅快匹：{@code /ws/dp-quick-match?nickname=...&token=...}，握手校验 JWT 与昵称一致。
 */
@Component
public class DpQuickMatchWebSocketHandler extends TextWebSocketHandler {

    private final DpQuickMatchPushService pushService;
    private final JwtTokenService jwtTokenService;
    private final DpRoomServiceImpl dpRoomService;

    public DpQuickMatchWebSocketHandler(
            DpQuickMatchPushService pushService,
            JwtTokenService jwtTokenService,
            DpRoomServiceImpl dpRoomService) {
        this.pushService = pushService;
        this.jwtTokenService = jwtTokenService;
        this.dpRoomService = dpRoomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String nickname = resolveNickname(session);
        String token = resolveToken(session);
        if (nickname == null || nickname.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("missing nickname"));
            return;
        }
        if (token == null || token.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("missing token"));
            return;
        }
        try {
            Claims claims = jwtTokenService.verifyToken(token);
            String sub = claims.getSubject();
            if (sub == null || !sub.equals(nickname)) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("token nickname mismatch"));
                return;
            }
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
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

    private static String resolveNickname(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        List<String> n = params.get("nickname");
        if (n == null || n.isEmpty()) {
            return null;
        }
        return n.get(0).trim();
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
