package com.example.mgdemoplus.achievement.notify;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.social.notify.AchievementUnlockNotifyPayload;
import com.example.mgdemoplus.social.notify.SocialSseHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AchievementNotifyPublisherTest {

    private SocialSseHub sseHub;
    private AchievementNotifyPublisher publisher;

    @BeforeEach
    void setUp() {
        sseHub = mock(SocialSseHub.class);
        publisher = new AchievementNotifyPublisher(sseHub);
    }

    @Test
    @DisplayName("首次解锁经 SocialSseHub 广播 achievement_unlocked")
    void notifyUnlockedBroadcastsPayload() {
        DpAchievement def = new DpAchievement();
        def.setCode("draw_insulator");
        def.setTitle("听牌绝缘体");
        def.setDescription("花顺双抽河牌未成");

        publisher.notifyUnlocked(42, def);

        ArgumentCaptor<AchievementUnlockNotifyPayload> captor =
                ArgumentCaptor.forClass(AchievementUnlockNotifyPayload.class);
        verify(sseHub).broadcastAchievementUnlocked(eq(42), captor.capture());
        AchievementUnlockNotifyPayload payload = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("draw_insulator", payload.getCode());
        org.junit.jupiter.api.Assertions.assertEquals("听牌绝缘体", payload.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals("花顺双抽河牌未成", payload.getDescription());
    }

    @Test
    @DisplayName("非法 userId 或成就定义时跳过广播")
    void notifyUnlockedSkipsInvalidArgs() {
        DpAchievement def = new DpAchievement();
        def.setCode("table_clear");
        def.setTitle("牌桌消消乐");
        def.setDescription("desc");

        publisher.notifyUnlocked(0, def);
        publisher.notifyUnlocked(1, null);

        verify(sseHub, never()).broadcastAchievementUnlocked(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }
}
