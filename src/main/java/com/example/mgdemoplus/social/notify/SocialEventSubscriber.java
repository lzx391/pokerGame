package com.example.mgdemoplus.social.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Handles {@link SocialRedisKeys#EVENTS_CHANNEL} messages on each app instance.
 */
public class SocialEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SocialEventSubscriber.class);

    private final SocialSseHub socialSseHub;
    private final SocialNotifySummaryService summaryService;
    private final ObjectMapper objectMapper;

    public SocialEventSubscriber(
            SocialSseHub socialSseHub,
            SocialNotifySummaryService summaryService,
            ObjectMapper objectMapper) {
        this.socialSseHub = socialSseHub;
        this.summaryService = summaryService;
        this.objectMapper = objectMapper;
    }
/**
 * 收到redis消息后的处理逻辑
 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            SocialEventPublisher.SocialEventMessage evt =
                    objectMapper.readValue(body, SocialEventPublisher.SocialEventMessage.class);
            if (evt == null || evt.userId() <= 0 || evt.kind() == null || evt.kind().isEmpty()) {
                return;
            }
            //如果本机没有订阅者，则跳过
            if (!socialSseHub.hasLocalSubscribers(evt.userId())) {
                log.debug("[social-sse] pub/sub skip no local SSE userId={} kind={}", evt.userId(), evt.kind());
                return;
            }
            dispatch(evt);
        } catch (Exception e) {
            log.warn("[social-sse] pub/sub handler failed", e);
        }
    }

    private void dispatch(SocialEventPublisher.SocialEventMessage evt) {
        switch (evt.kind()) {
            case "notify" -> {
                SocialNotifyPayload payload = summaryService.buildForUser(evt.userId());
                log.info(
                        "[social-sse] pub/sub notify userId={} mailboxUnread={} friendChatUnreadTotal={}",
                        evt.userId(),
                        payload.getMailboxUnread(),
                        payload.getFriendChatUnreadTotal());
                socialSseHub.broadcastNotify(evt.userId(), payload);
            }
            case "presence" -> {
                if (evt.friendUserId() == null || evt.friendUserId() <= 0) {
                    return;
                }
                FriendPresenceNotifyPayload payload =
                        new FriendPresenceNotifyPayload(evt.friendUserId(), evt.presence(), evt.reason());
                log.info(
                        "[social-sse] pub/sub presence userId={} friendUserId={} presence={}",
                        evt.userId(),
                        evt.friendUserId(),
                        evt.presence());
                socialSseHub.broadcastFriendPresence(evt.userId(), payload);
            }
            case "achievement" -> {
                AchievementUnlockNotifyPayload payload =
                        new AchievementUnlockNotifyPayload(evt.code(), evt.title(), evt.description());
                log.info(
                        "[social-sse] pub/sub achievement userId={} code={}",
                        evt.userId(),
                        evt.code());
                socialSseHub.broadcastAchievementUnlocked(evt.userId(), payload);
            }
            default -> log.debug("[social-sse] pub/sub unknown kind={} userId={}", evt.kind(), evt.userId());
        }
    }
}
