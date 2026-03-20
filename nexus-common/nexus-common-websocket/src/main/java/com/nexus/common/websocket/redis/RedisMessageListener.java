package com.nexus.common.websocket.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.base.BaseMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class RedisMessageListener implements MessageListener {

    private final RedisReceiver receiver;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            receiver.onMessage(new BaseMap(map));
        } catch (Exception e) {
            log.error("Redis消息解析失败: {}", e.getMessage(), e);
        }
    }
}
