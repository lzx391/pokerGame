package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Handles {@link DpRoomRedisKeys#EVENTS_CHANNEL} messages on each app instance.
 */
public class DpRoomEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DpRoomEventSubscriber.class);

    private final DpGameRoomPushService pushService;
    private final ObjectMapper objectMapper;

    public DpRoomEventSubscriber(DpGameRoomPushService pushService, ObjectMapper objectMapper) {
        this.pushService = pushService;
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
