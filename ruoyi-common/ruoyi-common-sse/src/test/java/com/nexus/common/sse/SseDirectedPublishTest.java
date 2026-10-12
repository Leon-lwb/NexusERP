package com.nexus.common.sse;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.sse.core.SseEmitterManager;
import com.nexus.common.sse.dto.SseMessageDto;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 空定向接收者不能被订阅端解释为跨用户广播。 */
class SseDirectedPublishTest {
    private static final RedissonClient REDIS = mock(RedissonClient.class);
    private static final RTopic TOPIC = mock(RTopic.class);

    @Test
    void emptyOrMissingRecipientsNeverPublish() {
        when(REDIS.getTopic("global:sse")).thenReturn(TOPIC);
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(REDIS);
            spring.when(() -> SpringUtil.getBean(ScheduledExecutorService.class)).thenReturn(mock(ScheduledExecutorService.class));
            SseEmitterManager manager = new SseEmitterManager();
            SseMessageDto payload = new SseMessageDto();
            payload.setMessage("定向通知");
            payload.setUserIds(List.of());
            manager.publishMessage(payload);
            payload.setUserIds(null);
            manager.publishMessage(payload);
            verify(TOPIC, never()).publish(any());
        }
    }
}