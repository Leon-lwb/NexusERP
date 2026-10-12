package com.nexus.common.websocket;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.holder.WebSocketSessionHolder;
import com.nexus.common.websocket.utils.WebSocketUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 定向投递不得扩大为广播，同账号其他节点的设备也必须接收。 */
class WebSocketClusterPublishTest {
    private static final RedissonClient REDIS = mock(RedissonClient.class);
    private static final RTopic TOPIC = mock(RTopic.class);

    @BeforeEach
    void resetCalls() {
        clearInvocations(REDIS, TOPIC);
        when(REDIS.getTopic("global:websocket")).thenReturn(TOPIC);
    }

    @Test
    void publishIncludesUserEvenWhenConnectedLocally() {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(REDIS);
            WebSocketSession local = mock(WebSocketSession.class);
            when(local.getId()).thenReturn("local");
            WebSocketSessionHolder.addSession(88L, local);
            try {
                WebSocketMessageDto payload = payload(List.of(88L));
                WebSocketUtils.publishMessage(payload);
                verify(TOPIC).publish(payload);
            } finally {
                WebSocketSessionHolder.removeSession(88L);
            }
        }
    }

    @Test
    void emptyRecipientsDoNotBroadcast() {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(REDIS);
            WebSocketUtils.publishMessage(payload(List.of()));
            verify(TOPIC, never()).publish(any());
        }
    }

    @Test
    void missingRecipientsDoNotBroadcast() {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(REDIS);
            WebSocketUtils.publishMessage(payload(null));
            verify(TOPIC, never()).publish(any());
        }
    }

    @Test
    void explicitBroadcastIsStillPublished() {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(REDIS);
            WebSocketUtils.publishAll("明确广播");
            verify(TOPIC).publish(any(WebSocketMessageDto.class));
        }
    }

    private WebSocketMessageDto payload(List<Long> users) {
        WebSocketMessageDto payload = new WebSocketMessageDto();
        payload.setSessionKeys(users);
        payload.setMessage("跨节点消息");
        return payload;
    }
}