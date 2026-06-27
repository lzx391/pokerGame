package com.example.mgdemoplus.config;

import com.example.mgdemoplus.social.notify.SocialEventSubscriber;
import com.example.mgdemoplus.social.notify.SocialNotifySummaryService;
import com.example.mgdemoplus.social.notify.SocialSseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for {@link com.example.mgdemoplus.social.notify.SocialRedisKeys#EVENTS_CHANNEL} subscription.
 * Listener registration is on the shared {@code RedisMessageListenerContainer} in
 * {@link DpRoomRedisPubSubConfig}.
 */
@Configuration
public class SocialRedisPubSubConfig {

    @Bean
    SocialEventSubscriber socialEventSubscriber(
            SocialSseHub socialSseHub,
            SocialNotifySummaryService summaryService,
            ObjectMapper objectMapper) {
        return new SocialEventSubscriber(socialSseHub, summaryService, objectMapper);
    }
}
