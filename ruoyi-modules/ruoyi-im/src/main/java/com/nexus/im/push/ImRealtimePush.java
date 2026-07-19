package com.nexus.im.push;

import cn.hutool.core.collection.CollUtil;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
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
 * <p>
 * P4-9: 新增带 {@code chatType} 参数的重载，在 {@code publishToUsers} 实际推送处
 * 集成 Micrometer 指标（success/failure 计数 + latency 分布）。
 * 原 2 参数重载保留并向后兼容（chatType 默认 "other"，用于撤回/已读回执等非消息发送场景）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImRealtimePush {

    private final ImMetrics imMetrics;

    /** 默认 chatType 标签（用于撤回事件/已读回执等非主消息发送场景） */
    private static final String CHAT_TYPE_OTHER = "other";

    public void publishToUsers(List<Long> userIds, String jsonMessage) {
        publishToUsers(userIds, jsonMessage, CHAT_TYPE_OTHER);
    }

    /**
     * P4-9: 带指标埋点的推送入口。
     * <p>埋点契约：
     * <ul>
     *   <li>成功（WS 与 SSE 均无异常）→ {@code im.push.success(type=chatType)} +1</li>
     *   <li>WS 抛异常 → {@code im.push.failure(type=chatType, reason=ws)} +1（仍继续 SSE）</li>
     *   <li>SSE 抛异常 → {@code im.push.failure(type=chatType, reason=sse)} +1（每异常一次记一次）</li>
     *   <li>整个调用耗时记入 {@code im.push.latency(type=chatType)}</li>
     * </ul>
     */
    public void publishToUsers(List<Long> userIds, String jsonMessage, String chatType) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        String type = chatType == null ? CHAT_TYPE_OTHER : chatType;
        Timer.Sample sample = imMetrics.startPushTimer();
        boolean wsOk = false;
        boolean sseOk = true;
        try {
            WebSocketMessageDto dto = new WebSocketMessageDto();
            dto.setSessionKeys(userIds);
            dto.setMessage(jsonMessage);
            try {
                WebSocketUtils.publishMessage(dto);
                wsOk = true;
            } catch (Exception e) {
                log.warn("WebSocket publish failed: {}", e.getMessage());
                imMetrics.recordPushFailure(type, "ws");
            }
            if (SseMessageUtils.isEnable()) {
                for (Long userId : userIds) {
                    try {
                        SseMessageUtils.sendMessage(userId, jsonMessage);
                    } catch (Exception e) {
                        log.warn("SSE send failed userId={}: {}", userId, e.getMessage());
                        sseOk = false;
                        imMetrics.recordPushFailure(type, "sse");
                    }
                }
            }
            if (wsOk && sseOk) {
                imMetrics.recordPushSuccess(type);
            }
        } finally {
            imMetrics.stopPushTimer(sample, type);
        }
    }

    public void publishToUser(Long userId, String jsonMessage) {
        publishToUsers(List.of(userId), jsonMessage, CHAT_TYPE_OTHER);
    }

    /** P4-9: 单用户带 chatType 重载（消息发送路径使用） */
    public void publishToUser(Long userId, String jsonMessage, String chatType) {
        publishToUsers(List.of(userId), jsonMessage, chatType);
    }

    /**
     * 在当前事务提交后再执行推送；若当前无活动事务则立即推送。
     * 用于 @Transactional 方法内部，避免推送先于事务提交导致接收方收到"幽灵消息"。
     */
    public void publishToUsersAfterCommit(List<Long> userIds, String jsonMessage) {
        publishToUsersAfterCommit(userIds, jsonMessage, CHAT_TYPE_OTHER);
    }

    /** P4-9: 带 chatType 的 afterCommit 重载（消息发送路径使用） */
    public void publishToUsersAfterCommit(List<Long> userIds, String jsonMessage, String chatType) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无活动事务，降级为立即推送（保持向后兼容）
            publishToUsers(userIds, jsonMessage, chatType);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishToUsers(userIds, jsonMessage, chatType);
            }
        });
    }

    public void publishToUserAfterCommit(Long userId, String jsonMessage) {
        publishToUsersAfterCommit(List.of(userId), jsonMessage, CHAT_TYPE_OTHER);
    }

    /** P4-9: 单用户带 chatType 的 afterCommit 重载（消息发送路径使用） */
    public void publishToUserAfterCommit(Long userId, String jsonMessage, String chatType) {
        publishToUsersAfterCommit(List.of(userId), jsonMessage, chatType);
    }
}
