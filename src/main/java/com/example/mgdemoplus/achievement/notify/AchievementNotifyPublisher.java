package com.example.mgdemoplus.achievement.notify;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.social.notify.AchievementUnlockNotifyPayload;
import com.example.mgdemoplus.social.notify.SocialSseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 成就首次解锁后，经现有社交 SSE 通道推送给本人。
 */
@Service
public class AchievementNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(AchievementNotifyPublisher.class);

    private final SocialSseHub sseHub;

    public AchievementNotifyPublisher(SocialSseHub sseHub) {
        this.sseHub = sseHub;
    }

    public void notifyUnlocked(int userId, DpAchievement achievement) {
        if (userId <= 0 || achievement == null) {
            return;
        }
        AchievementUnlockNotifyPayload payload =
                new AchievementUnlockNotifyPayload(
                        achievement.getCode(), achievement.getTitle(), achievement.getDescription());
        log.info(
                "[achievement-sse] notifyUnlocked userId={} code={} title={}",
                userId,
                payload.getCode(),
                payload.getTitle());
        sseHub.broadcastAchievementUnlocked(userId, payload);
    }
}
