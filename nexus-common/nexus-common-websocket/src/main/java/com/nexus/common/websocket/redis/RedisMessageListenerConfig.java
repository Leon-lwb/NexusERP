package com.nexus.common.websocket.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.constant.GlobalConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisMessageListenerConfig {

    private final RedisReceiver receiver;
    private final ObjectMapper objectMapper;

    @Bean
    public RedisMessageListener redisMessageListener() {
        return new RedisMessageListener(receiver, objectMapper);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory, RedisMessageListener redisMessageListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisMessageListener, new PatternTopic(GlobalConstants.REDIS_TOPIC_NAME));
        return container;
    }
}
