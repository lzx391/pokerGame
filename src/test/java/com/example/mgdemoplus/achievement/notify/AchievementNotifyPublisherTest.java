package com.example.mgdemoplus.achievement.notify;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.social.notify.SocialEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AchievementNotifyPublisherTest {

    private SocialEventPublisher socialEventPublisher;
    private AchievementNotifyPublisher publisher;

    @BeforeEach
    void setUp() {
        socialEventPublisher = mock(SocialEventPublisher.class);
        publisher = new AchievementNotifyPublisher(socialEventPublisher);
    }

    @Test
    @DisplayName("首次解锁经 SocialEventPublisher 发布 achievement 事件")
    void notifyUnlockedBroadcastsPayload() {
        DpAchievement def = new DpAchievement();
        def.setCode("draw_insulator");
        def.setTitle("听牌绝缘体");
        def.setDescription("花顺双抽河牌未成");

        publisher.notifyUnlocked(42, def);

        verify(socialEventPublisher).publishAchievement(
                eq(42), eq("draw_insulator"), eq("听牌绝缘体"), eq("花顺双抽河牌未成"));
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

        verify(socialEventPublisher, never()).publishAchievement(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
