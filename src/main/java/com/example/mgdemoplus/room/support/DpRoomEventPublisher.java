package com.example.mgdemoplus.room.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes room revision events on {@link DpRoomRedisKeys#EVENTS_CHANNEL}.
 */
@Component
public class DpRoomEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DpRoomEventPublisher(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String roomId, long rev, String reason) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(new DpRoomEventMessage(roomId, rev, reason));
            stringRedisTemplate.convertAndSend(DpRoomRedisKeys.EVENTS_CHANNEL, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("room event publish failed roomId=" + roomId, e);
        }
    }

    public record DpRoomEventMessage(String roomId, long rev, String reason) {
    }
}
