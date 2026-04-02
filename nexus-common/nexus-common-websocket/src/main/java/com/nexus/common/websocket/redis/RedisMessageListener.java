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

            Object messageField = map.get("message");
            if (messageField instanceof String) {
                String messageStr = messageField.toString();
                if (messageStr.startsWith("{") && messageStr.endsWith("}")) {
                    try {
                        Map<String, Object> nestedMessage = objectMapper.readValue(
                                messageStr,
                                new TypeReference<Map<String, Object>>() {}
                        );
                        map.put("message", nestedMessage);
                        log.debug("检测到嵌套 JSON 消息，已解析处理");
                    } catch (Exception e) {
                        log.debug("消息字段为普通字符串或非标准 JSON");
                    }
                }
            }

            receiver.onMessage(new BaseMap(map));
        } catch (Exception e) {
            log.error("Redis 消息解析失败：{}", e.getMessage(), e);
        }
    }
}
