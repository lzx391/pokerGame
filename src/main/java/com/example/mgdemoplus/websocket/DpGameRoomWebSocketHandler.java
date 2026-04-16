package com.example.mgdemoplus.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 游戏页专用：ws 路径 {@code /ws/dp-game?roomId=...}，payload 与 {@code GET /dpRoom/getNowRoom} 的 JSON 一致。
 */
@Component
public class DpGameRoomWebSocketHandler extends TextWebSocketHandler {

    private final DpGameRoomPushService pushService;

    public DpGameRoomWebSocketHandler(DpGameRoomPushService pushService) {
        this.pushService = pushService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = resolveRoomId(session);
        if (roomId == null || roomId.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("missing roomId"));
            return;
        }
        //已学习，将roomId存入session的attributes中
        session.getAttributes().put("roomId", roomId);
        String nickname = resolveNickname(session);
        if (nickname != null && !nickname.isEmpty()) {
            session.getAttributes().put("viewerNickname", nickname);
        }
        //已学习，调用websocket的pushService.register(roomId, session)注册房间订阅者
        pushService.register(roomId, session);
        //已学习，调用websocket的pushService.sendInitialSnapshot(session, roomId)发送初始房间数据给订阅者
        pushService.sendInitialSnapshot(session, roomId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object rid = session.getAttributes().get("roomId");
        if (rid instanceof String) {
            pushService.unregister((String) rid, session);
        }
    }
/**
 * 处理客户端发送的文本消息
 * @param session
 * @param message
 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        pushService.handleClientTextMessage(session, message.getPayload());
    }
/**
 * 解析房间ID
 * @param session
 * @return
 */
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
//解析昵称
/**
 * 解析昵称
 * @param session
 * @return
 */
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
}
