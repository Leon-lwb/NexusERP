package com.nexus.ai.service.sse;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/** 每次生成独立管理事件终态，内部异常仅记录在带请求标识的服务端日志中。 */
@Slf4j
public final class AiChatSseLifecycle {
    public static final String ERROR_MESSAGE = "AI 对话失败，请稍后重试。";
    private final String requestId = UUID.randomUUID().toString();
    private final Long userId;
    private final Long sessionId;
    private boolean terminal;
    private Throwable failure;

    public AiChatSseLifecycle(Long userId, Long sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
        log.info("AI 对话开始 requestId={}, sessionId={}, userId={}", requestId, sessionId, userId);
    }

    public String requestId() { return requestId; }

    /** 正文及过程事件与终态串行，完成后忽略迟到回调。 */
    public synchronized void progress(Runnable event) {
        if (!terminal) event.run();
    }

    /** 先持久化并推送完整内容，再发送唯一的完成事件；保存失败转为失败终态。 */
    public synchronized boolean complete(Runnable beforeDone) {
        if (terminal) return false;
        terminal = true;
        try {
            beforeDone.run();
        } catch (Exception error) {
            reportFailure(error);
            return false;
        }
        log.info("AI 对话完成 requestId={}, sessionId={}", requestId, sessionId);
        AiSseMessageHelper.sendDone(userId);
        return true;
    }

    /** 成功与失败只允许先到者生效，SDK 重复回调不能反转已确认终态。 */
    public synchronized void fail(Throwable error) {
        if (terminal) return;
        terminal = true;
        reportFailure(error);
    }

    public synchronized Throwable failure() { return failure; }

    private void reportFailure(Throwable error) {
        failure = error;
        log.error("AI 对话失败 requestId={}, sessionId={}", requestId, sessionId, error);
        AiSseMessageHelper.sendError(userId, ERROR_MESSAGE);
    }
}
