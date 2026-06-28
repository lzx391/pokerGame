package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Handles {@link DpRoomRedisKeys#EVENTS_CHANNEL} messages on each app instance.
 * Joinable quick-match index is updated in {@link DpRedisRoomRegistry#saveAfterMutation} (P2); this subscriber
 * drives local WebSocket fan-out and cross-instance {@code dp_room_lobby} / publicRooms cache invalidation.
 */
public class DpRoomEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DpRoomEventSubscriber.class);

    private final DpGameRoomPushService pushService;
    private final DpRoomHallService dpRoomHallService;
    private final ObjectMapper objectMapper;

    public DpRoomEventSubscriber(
            DpGameRoomPushService pushService,
            DpRoomHallService dpRoomHallService,
            ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.dpRoomHallService = dpRoomHallService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            DpRoomEventPublisher.DpRoomEventMessage evt =
                    objectMapper.readValue(body, DpRoomEventPublisher.DpRoomEventMessage.class);
            if (evt == null || evt.roomId() == null || evt.roomId().isEmpty()) {
                return;
            }
            if ("roomRemoved".equals(evt.reason())) {
                pushService.shutdownSubscriptionsForRoom(evt.roomId());
                // 摘房实例若未跑到 finalizeHall，其它节点仍须软删大厅行并清 Redis 分页缓存。
                dpRoomHallService.deleteRoomSummary(evt.roomId());
                return;
            }
            if (pushService.hasSubscribers(evt.roomId())) {
                pushService.broadcastIfSubscribed(evt.roomId());
            }
        } catch (Exception e) {
            log.warn("room pub/sub handler failed", e);
        }
    }
}
