package com.example.mgdemoplus.social.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes social SSE events on {@link SocialRedisKeys#EVENTS_CHANNEL}.
 */
@Component
public class SocialEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public SocialEventPublisher(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(int userId, String kind) {
        if (userId <= 0 || kind == null || kind.isEmpty()) {
            return;
        }
        send(new SocialEventMessage(userId, kind, null, null, null, null, null, null));
    }

    public void publishPresence(int watcherUserId, int friendUserId, String displayPresence, String reason) {
        if (watcherUserId <= 0 || friendUserId <= 0) {
            return;
        }
        send(new SocialEventMessage(
                watcherUserId, "presence", friendUserId, displayPresence, reason, null, null, null));
    }

    public void publishAchievement(int userId, String code, String title, String description) {
        if (userId <= 0 || code == null || code.isEmpty()) {
            return;
        }
        send(new SocialEventMessage(userId, "achievement", null, null, null, code, title, description));
    }

    private void send(SocialEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            //发送redis消息，剩下的交给框架就完事了，redis会自动把消息广播到所有订阅者
            stringRedisTemplate.convertAndSend(SocialRedisKeys.EVENTS_CHANNEL, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("social event publish failed userId=" + message.userId(), e);
        }
    }

    public record SocialEventMessage(
            int userId,
            String kind,
            Integer friendUserId,
            String presence,
            String reason,
            String code,
            String title,
            String description) {
    }
}
