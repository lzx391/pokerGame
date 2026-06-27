package com.example.mgdemoplus.social.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialEventSubscriberTest {

    private SocialSseHub socialSseHub;
    private SocialNotifySummaryService summaryService;
    private SocialEventSubscriber subscriber;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        socialSseHub = mock(SocialSseHub.class);
        summaryService = mock(SocialNotifySummaryService.class);
        objectMapper = new ObjectMapper();
        subscriber = new SocialEventSubscriber(socialSseHub, summaryService, objectMapper);
    }

    @Test
    @DisplayName("notify event rebuilds summary and broadcasts when local SSE exists")
    void notifyWithLocalSubscriber() throws Exception {
        when(socialSseHub.hasLocalSubscribers(42)).thenReturn(true);
        SocialNotifyPayload payload = new SocialNotifyPayload(3, 5L, Map.of("7", 5));
        when(summaryService.buildForUser(42)).thenReturn(payload);

        subscriber.onMessage(messageFor(new SocialEventPublisher.SocialEventMessage(
                42, "notify", null, null, null, null, null, null)), null);

        verify(summaryService).buildForUser(42);
        verify(socialSseHub).broadcastNotify(eq(42), eq(payload));
    }

    @Test
    @DisplayName("no local SSE connection is silent no-op")
    void notifyWithoutLocalSubscriber() throws Exception {
        when(socialSseHub.hasLocalSubscribers(42)).thenReturn(false);

        subscriber.onMessage(messageFor(new SocialEventPublisher.SocialEventMessage(
                42, "notify", null, null, null, null, null, null)), null);

        verify(summaryService, never()).buildForUser(org.mockito.ArgumentMatchers.anyInt());
        verify(socialSseHub, never()).broadcastNotify(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("presence event broadcasts friendPresence when local SSE exists")
    void presenceWithLocalSubscriber() throws Exception {
        when(socialSseHub.hasLocalSubscribers(1)).thenReturn(true);

        subscriber.onMessage(messageFor(new SocialEventPublisher.SocialEventMessage(
                1, "presence", 2, "OFFLINE", "room_gone", null, null, null)), null);

        ArgumentCaptor<FriendPresenceNotifyPayload> captor =
                ArgumentCaptor.forClass(FriendPresenceNotifyPayload.class);
        verify(socialSseHub).broadcastFriendPresence(eq(1), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getValue().getFriendUserId());
        org.junit.jupiter.api.Assertions.assertEquals("OFFLINE", captor.getValue().getPresence());
        org.junit.jupiter.api.Assertions.assertEquals("room_gone", captor.getValue().getReason());
    }

    @Test
    @DisplayName("achievement event broadcasts achievement_unlocked when local SSE exists")
    void achievementWithLocalSubscriber() throws Exception {
        when(socialSseHub.hasLocalSubscribers(99)).thenReturn(true);

        subscriber.onMessage(messageFor(new SocialEventPublisher.SocialEventMessage(
                99, "achievement", null, null, null, "draw_insulator", "听牌绝缘体", "desc")), null);

        ArgumentCaptor<AchievementUnlockNotifyPayload> captor =
                ArgumentCaptor.forClass(AchievementUnlockNotifyPayload.class);
        verify(socialSseHub).broadcastAchievementUnlocked(eq(99), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("draw_insulator", captor.getValue().getCode());
        org.junit.jupiter.api.Assertions.assertEquals("听牌绝缘体", captor.getValue().getTitle());
    }

    private Message messageFor(SocialEventPublisher.SocialEventMessage evt) throws Exception {
        byte[] body = objectMapper.writeValueAsString(evt).getBytes(StandardCharsets.UTF_8);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        return message;
    }
}
