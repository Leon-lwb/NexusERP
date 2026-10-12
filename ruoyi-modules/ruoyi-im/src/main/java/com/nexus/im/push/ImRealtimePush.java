package com.nexus.im.push;

import cn.hutool.core.collection.CollUtil;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.utils.WebSocketUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

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
 * <p>
 * P5-1: 引入 {@code imPushExecutor} 异步推送线程池。
 * <ul>
 *   <li>{@code im.push.async.enabled=true}（默认）→ 推送动作封装为 Runnable 提交到 imPushExecutor</li>
 *   <li>线程池满时由 {@code CallerRunsPolicy} 拒绝策略处理（调用线程同步执行，不丢消息）</li>
 *   <li>通过 {@link ImMetrics#registerPushExecutor} 注册 executor.active / queue.size 等监控指标</li>
 *   <li>关闭开关或单元测试场景 → 走同步路径（保持向后兼容）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImRealtimePush {

    private final ImMetrics imMetrics;

    /** P5-1: 异步推送线程池（@Lazy 防止初始化时序问题；单元测试中为 null） */
    @Autowired
    @Qualifier("imPushExecutor")
    @org.springframework.context.annotation.Lazy
    private Executor imPushExecutor;

    /** P5-1: 异步推送开关，默认 true；故障时关闭回退到同步推送 */
    @Value("${im.push.async.enabled:true}")
    private boolean asyncEnabled;

    /** 默认 chatType 标签（用于撤回事件/已读回执等非主消息发送场景） */
    private static final String CHAT_TYPE_OTHER = "other";

    /**
     * P5-1: 注册推送线程池监控指标（active / queue.size / completed / pool.size）。
     * <p>仅在 imPushExecutor 为 ExecutorService 实例时注册（单元测试中为 null 或同步执行器，跳过）。
     */
    @PostConstruct
    public void registerExecutorMetrics() {
        if (imPushExecutor instanceof ExecutorService es) {
            imMetrics.registerPushExecutor(es);
            log.info("[IM] P5-1 推送线程池监控已注册, asyncEnabled={}", asyncEnabled);
        } else if (imPushExecutor != null) {
            log.info("[IM] P5-1 推送路径: asyncEnabled={}, executor={} (非 ExecutorService, 跳过监控注册)",
                asyncEnabled, imPushExecutor.getClass().getSimpleName());
        }
    }

    /**
     * 向多个用户推送消息（默认 chatType 为 other）。
     *
     * @param userIds     目标用户 ID 列表
     * @param jsonMessage 推送载荷（信封 JSON 字符串）
     */
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
     * <p>P5-1: 若 {@code asyncEnabled=true} 且 {@code imPushExecutor != null}，
     * 推送动作封装为 Runnable 提交到 imPushExecutor 异步执行（CallerRunsPolicy 兜底）。
     */
    public void publishToUsers(List<Long> userIds, String jsonMessage, String chatType) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        String type = chatType == null ? CHAT_TYPE_OTHER : chatType;

        // P5-1: 异步路径
        if (asyncEnabled && imPushExecutor != null) {
            try {
                imPushExecutor.execute(() -> doPublish(userIds, jsonMessage, type));
                return;
            } catch (Exception e) {
                // CallerRunsPolicy 不应抛异常，但为防御性记录
                log.warn("[IM] P5-1 异步提交失败, 降级同步推送: {}", e.getMessage());
                imMetrics.recordPushExecutorRejected(type);
            }
        }
        // 同步路径（异步关闭 / 异步提交失败 / 单元测试）
        doPublish(userIds, jsonMessage, type);
    }

    /**
     * 实际推送动作（WS + SSE）+ 指标埋点。
     * <p>P5-1: 提取为独立方法，供同步/异步路径共用。
     */
    private void doPublish(List<Long> userIds, String jsonMessage, String type) {
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
                try {
                    // 交给集群订阅者在持有连接的节点下发，避免只发到当前节点。
                    SseMessageDto sse = new SseMessageDto();
                    sse.setUserIds(userIds);
                    sse.setMessage(jsonMessage);
                    SseMessageUtils.publishMessage(sse);
                } catch (Exception e) {
                    log.warn("SSE 广播失败: {}", e.getMessage());
                    sseOk = false;
                    imMetrics.recordPushFailure(type, "sse");
                }
            }
            if (wsOk && sseOk) {
                imMetrics.recordPushSuccess(type);
            }
        } finally {
            imMetrics.stopPushTimer(sample, type);
        }
    }

    /**
     * 向单个用户推送消息（默认 chatType 为 other）。
     *
     * @param userId      目标用户 ID
     * @param jsonMessage 推送载荷（信封 JSON 字符串）
     */
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

    /**
     * 在事务提交后向单个用户推送消息（默认 chatType 为 other）；无活动事务则立即推送。
     *
     * @param userId      目标用户 ID
     * @param jsonMessage 推送载荷（信封 JSON 字符串）
     */
    public void publishToUserAfterCommit(Long userId, String jsonMessage) {
        publishToUsersAfterCommit(List.of(userId), jsonMessage, CHAT_TYPE_OTHER);
    }

    /** P4-9: 单用户带 chatType 的 afterCommit 重载（消息发送路径使用） */
    public void publishToUserAfterCommit(Long userId, String jsonMessage, String chatType) {
        publishToUsersAfterCommit(List.of(userId), jsonMessage, chatType);
    }
}
