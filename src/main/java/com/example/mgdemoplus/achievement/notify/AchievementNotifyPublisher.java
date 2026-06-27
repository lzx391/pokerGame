package com.example.mgdemoplus.achievement.notify;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.social.notify.SocialEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 成就首次解锁后，经现有社交 SSE 通道推送给本人。
 */
@Service
public class AchievementNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(AchievementNotifyPublisher.class);

    private final SocialEventPublisher socialEventPublisher;

    public AchievementNotifyPublisher(SocialEventPublisher socialEventPublisher) {
        this.socialEventPublisher = socialEventPublisher;
    }

    public void notifyUnlocked(int userId, DpAchievement achievement) {
        if (userId <= 0 || achievement == null) {
            return;
        }
        log.info(
                "[achievement-sse] notifyUnlocked publish userId={} code={} title={}",
                userId,
                achievement.getCode(),
                achievement.getTitle());
        socialEventPublisher.publishAchievement(
                userId, achievement.getCode(), achievement.getTitle(), achievement.getDescription());
    }
}
