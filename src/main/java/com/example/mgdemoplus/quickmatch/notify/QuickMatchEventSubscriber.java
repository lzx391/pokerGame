package com.example.mgdemoplus.quickmatch.notify;

import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Handles {@link com.example.mgdemoplus.quickmatch.QuickMatchRedisKeys#EVENTS_CHANNEL} on each app instance.
 */
public class QuickMatchEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(QuickMatchEventSubscriber.class);

    private final DpQuickMatchPushService quickMatchPushService;
    private final ObjectMapper objectMapper;

    public QuickMatchEventSubscriber(DpQuickMatchPushService quickMatchPushService, ObjectMapper objectMapper) {
        this.quickMatchPushService = quickMatchPushService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            QuickMatchEventPublisher.QuickMatchEventMessage evt =
                    objectMapper.readValue(body, QuickMatchEventPublisher.QuickMatchEventMessage.class);
            if (evt == null || evt.nickname() == null || evt.nickname().isBlank()
                    || evt.state() == null || evt.state().isEmpty()) {
                return;
            }
            if (!quickMatchPushService.hasLocalSession(evt.nickname())) {
                log.debug("[qm-ws] pub/sub skip no local WS nick={} state={}", evt.nickname(), evt.state());
                return;
            }
            dispatch(evt);
        } catch (Exception e) {
            log.warn("[qm-ws] pub/sub handler failed", e);
        }
    }

    private void dispatch(QuickMatchEventPublisher.QuickMatchEventMessage evt) {
        switch (evt.state()) {
            case "WAITING" -> quickMatchPushService.notifyWaiting(evt.nickname(), evt.queuePosition());
            case "MATCHED" -> quickMatchPushService.notifyMatched(evt.nickname(), evt.roomId());
            case "IDLE" -> quickMatchPushService.notifyIdle(evt.nickname(), evt.message());
            default -> log.debug("[qm-ws] pub/sub unknown state={} nick={}", evt.state(), evt.nickname());
        }
    }
}
