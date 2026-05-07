package org.dromara.im.push;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.sse.utils.SseMessageUtils;
import org.dromara.common.websocket.dto.WebSocketMessageDto;
import org.dromara.common.websocket.utils.WebSocketUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一 WebSocket + SSE 下行（载荷为 JSON 字符串）
 */
@Slf4j
@Component
public class ImRealtimePush {

    public void publishToUsers(List<Long> userIds, String jsonMessage) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        WebSocketMessageDto dto = new WebSocketMessageDto();
        dto.setSessionKeys(userIds);
        dto.setMessage(jsonMessage);
        try {
            WebSocketUtils.publishMessage(dto);
        } catch (Exception e) {
            log.warn("WebSocket publish failed: {}", e.getMessage());
        }
        if (SseMessageUtils.isEnable()) {
            for (Long userId : userIds) {
                try {
                    SseMessageUtils.sendMessage(userId, jsonMessage);
                } catch (Exception e) {
                    log.warn("SSE send failed userId={}: {}", userId, e.getMessage());
                }
            }
        }
    }

    public void publishToUser(Long userId, String jsonMessage) {
        publishToUsers(List.of(userId), jsonMessage);
    }
}
