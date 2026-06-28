package com.example.mgdemoplus.config;

import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.quickmatch.QuickMatchRedisKeys;
import com.example.mgdemoplus.quickmatch.notify.QuickMatchEventSubscriber;
import com.example.mgdemoplus.room.support.DpRoomEventSubscriber;
import com.example.mgdemoplus.room.support.DpRoomRedisKeys;
import com.example.mgdemoplus.social.notify.SocialEventSubscriber;
import com.example.mgdemoplus.social.notify.SocialRedisKeys;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Shared Redis pub/sub listener container for room WS, quick-match WS, and social SSE fan-out on each app instance.
 */
@Configuration
public class DpRoomRedisPubSubConfig {

    @Bean
    DpRoomEventSubscriber dpRoomEventSubscriber(
            DpGameRoomPushService pushService,
            DpRoomHallService dpRoomHallService,
            ObjectMapper objectMapper) {
        return new DpRoomEventSubscriber(pushService, dpRoomHallService, objectMapper);
    }

    @Bean
    QuickMatchEventSubscriber quickMatchEventSubscriber(
            DpQuickMatchPushService quickMatchPushService,
            ObjectMapper objectMapper) {
        return new QuickMatchEventSubscriber(quickMatchPushService, objectMapper);
    }

    @Bean
    RedisMessageListenerContainer dpRoomRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DpRoomEventSubscriber dpRoomEventSubscriber,
            QuickMatchEventSubscriber quickMatchEventSubscriber,
            SocialEventSubscriber socialEventSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter roomAdapter = new MessageListenerAdapter(dpRoomEventSubscriber, "onMessage");
        container.addMessageListener(roomAdapter, new ChannelTopic(DpRoomRedisKeys.EVENTS_CHANNEL));
        MessageListenerAdapter qmAdapter = new MessageListenerAdapter(quickMatchEventSubscriber, "onMessage");
        container.addMessageListener(qmAdapter, new ChannelTopic(QuickMatchRedisKeys.EVENTS_CHANNEL));
        MessageListenerAdapter socialAdapter = new MessageListenerAdapter(socialEventSubscriber, "onMessage");
        container.addMessageListener(socialAdapter, new ChannelTopic(SocialRedisKeys.EVENTS_CHANNEL));
        return container;
    }
}
