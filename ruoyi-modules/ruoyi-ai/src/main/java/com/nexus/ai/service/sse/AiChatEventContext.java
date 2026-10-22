package com.nexus.ai.service.sse;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 单次对话事件的显式上下文；线程池和SDK回调只通过生命周期绑定，不继承其他请求。 */
final class AiChatEventContext {
    private static final ThreadLocal<AiChatEventContext> CURRENT = new ThreadLocal<>();
    private final Long userId;
    private final Long sessionId;
    private final String requestId;
    private final String clientRequestId;
    private final AtomicLong sequence = new AtomicLong();
    private volatile Long messageId;

    AiChatEventContext(Long userId, Long sessionId, String requestId, String clientRequestId) {
        this.userId = Objects.requireNonNull(userId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.requestId = Objects.requireNonNull(requestId);
        this.clientRequestId = clientRequestId;
    }

    /** 绑定期间可发布本次事件；嵌套或回调结束后恢复原线程状态。 */
    void run(Runnable action) {
        AiChatEventContext previous = CURRENT.get();
        CURRENT.set(this);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** 仅在消息实际写库后关联持久化ID，禁止串入其他会话。 */
    static void persisted(Long sessionId, Long messageId) {
        AiChatEventContext context = CURRENT.get();
        if (context != null && context.sessionId.equals(sessionId) && messageId != null) {
            if (messageId <= 0) throw new IllegalArgumentException("消息标识必须为正数");
            context.messageId = messageId;
        }
    }

    /** 普通聊天信封使用文本ID；Flow和工程事件继续采用各自协议。 */
    static void enrich(Long targetUserId, String type, Map<String, Object> payload) {
        AiChatEventContext context = CURRENT.get();
        if (context == null || !context.userId.equals(targetUserId)
            || type.startsWith("AI_FLOW_") || "AI_ENGINEERING_TASK".equals(type)) return;
        payload.put("requestId", context.requestId);
        if (context.clientRequestId != null) payload.put("clientRequestId", context.clientRequestId);
        payload.put("sessionId", context.sessionId.toString());
        payload.put("sequence", context.sequence.incrementAndGet());
        if (context.messageId != null) payload.put("messageId", context.messageId.toString());
    }
}
