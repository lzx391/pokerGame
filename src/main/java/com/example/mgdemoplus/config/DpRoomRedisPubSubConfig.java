package com.example.mgdemoplus.config;

import com.example.mgdemoplus.room.support.DpRoomEventSubscriber;
import com.example.mgdemoplus.room.support.DpRoomRedisKeys;
import com.example.mgdemoplus.social.notify.SocialEventSubscriber;
import com.example.mgdemoplus.social.notify.SocialRedisKeys;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Shared Redis pub/sub listener container for room WS and social SSE fan-out on each app instance.
 */
@Configuration
public class DpRoomRedisPubSubConfig {

    @Bean
    DpRoomEventSubscriber dpRoomEventSubscriber(
            DpGameRoomPushService pushService,
            ObjectMapper objectMapper) {
        return new DpRoomEventSubscriber(pushService, objectMapper);
    }

    @Bean
    RedisMessageListenerContainer dpRoomRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DpRoomEventSubscriber dpRoomEventSubscriber,
            SocialEventSubscriber socialEventSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter roomAdapter = new MessageListenerAdapter(dpRoomEventSubscriber, "onMessage");
        container.addMessageListener(roomAdapter, new ChannelTopic(DpRoomRedisKeys.EVENTS_CHANNEL));
        MessageListenerAdapter socialAdapter = new MessageListenerAdapter(socialEventSubscriber, "onMessage");
        container.addMessageListener(socialAdapter, new ChannelTopic(SocialRedisKeys.EVENTS_CHANNEL));
        return container;
    }
}
