package com.example.mgdemoplus.config;

import com.example.mgdemoplus.room.support.DpRoomEventSubscriber;
import com.example.mgdemoplus.room.support.DpRoomRedisKeys;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Subscribes to {@link DpRoomRedisKeys#EVENTS_CHANNEL} and fans out WS pushes on the local instance.
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
            DpRoomEventSubscriber dpRoomEventSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter adapter = new MessageListenerAdapter(dpRoomEventSubscriber, "onMessage");
        container.addMessageListener(adapter, new ChannelTopic(DpRoomRedisKeys.EVENTS_CHANNEL));
        return container;
    }
}
