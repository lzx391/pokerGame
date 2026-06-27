package com.example.mgdemoplus.social.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SocialEventPublisherTest {

    private StringRedisTemplate stringRedisTemplate;
    private SocialEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        publisher = new SocialEventPublisher(stringRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("publish sends notify event to dp:social:events")
    void publishNotify() throws Exception {
        publisher.publish(42, "notify");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(eq(SocialRedisKeys.EVENTS_CHANNEL), captor.capture());
        SocialEventPublisher.SocialEventMessage msg =
                objectMapper.readValue(captor.getValue(), SocialEventPublisher.SocialEventMessage.class);
        org.junit.jupiter.api.Assertions.assertEquals(42, msg.userId());
        org.junit.jupiter.api.Assertions.assertEquals("notify", msg.kind());
    }

    @Test
    @DisplayName("publishPresence includes friend fields")
    void publishPresence() throws Exception {
        publisher.publishPresence(1, 2, "IDLE", "test-reason");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(eq(SocialRedisKeys.EVENTS_CHANNEL), captor.capture());
        SocialEventPublisher.SocialEventMessage msg =
                objectMapper.readValue(captor.getValue(), SocialEventPublisher.SocialEventMessage.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, msg.userId());
        org.junit.jupiter.api.Assertions.assertEquals("presence", msg.kind());
        org.junit.jupiter.api.Assertions.assertEquals(2, msg.friendUserId());
        org.junit.jupiter.api.Assertions.assertEquals("IDLE", msg.presence());
        org.junit.jupiter.api.Assertions.assertEquals("test-reason", msg.reason());
    }

    @Test
    @DisplayName("invalid userId is no-op")
    void publishSkipsInvalidUserId() {
        publisher.publish(0, "notify");
        org.mockito.Mockito.verifyNoInteractions(stringRedisTemplate);
    }
}
