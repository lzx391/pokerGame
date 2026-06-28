package com.example.mgdemoplus.quickmatch.notify;

import com.example.mgdemoplus.quickmatch.QuickMatchRedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes quick-match WebSocket events on {@link QuickMatchRedisKeys#EVENTS_CHANNEL}.
 */
@Component
public class QuickMatchEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public QuickMatchEventPublisher(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishWaiting(String nickname, int queuePosition) {
        if (nickname == null || nickname.isBlank() || queuePosition <= 0) {
            return;
        }
        send(new QuickMatchEventMessage(nickname, "WAITING", queuePosition, null, null));
    }

    public void publishMatched(String nickname, String roomId) {
        if (nickname == null || nickname.isBlank() || roomId == null || roomId.isEmpty()) {
            return;
        }
        send(new QuickMatchEventMessage(nickname, "MATCHED", 0, roomId, null));
    }

    public void publishIdle(String nickname, String message) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        send(new QuickMatchEventMessage(nickname, "IDLE", 0, null, message != null ? message : ""));
    }

    private void send(QuickMatchEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(QuickMatchRedisKeys.EVENTS_CHANNEL, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("quick match event publish failed nick=" + message.nickname(), e);
        }
    }

    public record QuickMatchEventMessage(
            String nickname,
            String state,
            int queuePosition,
            String roomId,
            String message) {
    }
}
