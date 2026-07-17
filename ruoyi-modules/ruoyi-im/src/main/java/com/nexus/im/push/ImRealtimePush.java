package com.nexus.im.push;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.utils.WebSocketUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 统一 WebSocket + SSE 下行（载荷为 JSON 字符串）
 * <p>
 * P0-2: 新增 {@code publishToUser(s)AfterCommit} 系列方法，将推送注册到
 * 当前事务的 afterCommit 钩子，避免事务回滚后接收方仍收到"幽灵消息"。
 * 若调用方未处于活动事务中，则降级为立即推送（保持向后兼容）。
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

    /**
     * 在当前事务提交后再执行推送；若当前无活动事务则立即推送。
     * 用于 @Transactional 方法内部，避免推送先于事务提交导致接收方收到"幽灵消息"。
     */
    public void publishToUsersAfterCommit(List<Long> userIds, String jsonMessage) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无活动事务，降级为立即推送（保持向后兼容）
            publishToUsers(userIds, jsonMessage);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishToUsers(userIds, jsonMessage);
            }
        });
    }

    public void publishToUserAfterCommit(Long userId, String jsonMessage) {
        publishToUsersAfterCommit(List.of(userId), jsonMessage);
    }
}
