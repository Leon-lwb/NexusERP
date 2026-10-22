package com.nexus.ai.service.sse;

import com.nexus.common.ai.dto.AiChatRequest;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/** 每次生成独立管理事件关联、递增序号和唯一终态；异常详情仅保留服务端。 */
@Slf4j
public final class AiChatSseLifecycle {
    public static final String ERROR_MESSAGE = "AI 对话失败，请稍后重试。";
    private final String requestId = UUID.randomUUID().toString();
    private final Long userId;
    private final Long sessionId;
    private final AiChatEventContext events;
    private boolean terminal;
    private Throwable failure;

    public AiChatSseLifecycle(Long userId, Long sessionId) {
        this(userId, sessionId, null);
    }

    /** 客户端标识只用于配对事件，服务端执行标识始终独立生成。 */
    public AiChatSseLifecycle(Long userId, Long sessionId, String clientRequestId) {
        if (!AiChatRequest.validClientRequestId(clientRequestId)) {
            throw new IllegalArgumentException("请求关联标识格式不正确");
        }
        this.userId = userId;
        this.sessionId = sessionId;
        this.events = new AiChatEventContext(userId, sessionId, requestId, clientRequestId);
        log.info("AI 对话开始 requestId={}, sessionId={}, userId={}", requestId, sessionId, userId);
    }

    public String requestId() { return requestId; }

    /** 异步任务显式绑定本轮事件，不把线程创建时的其他身份带入回调。 */
    public void run(Runnable task) { events.run(task); }

    /** 持久化成功后回填完成消息标识；保持雪花ID精度。 */
    public static void persistedMessage(Long sessionId, Long messageId) {
        AiChatEventContext.persisted(sessionId, messageId);
    }

    /** 正文及过程事件与终态串行，完成后忽略迟到回调。 */
    public synchronized void progress(Runnable event) {
        if (!terminal) events.run(event);
    }

    /** 先持久化并推送完整内容，再发送唯一的完成事件；保存失败转为失败终态。 */
    public synchronized boolean complete(Runnable beforeDone) {
        if (terminal) return false;
        terminal = true;
        try {
            events.run(beforeDone);
        } catch (Exception error) {
            reportFailure(error);
            return false;
        }
        log.info("AI 对话完成 requestId={}, sessionId={}", requestId, sessionId);
        events.run(() -> AiSseMessageHelper.sendDone(userId));
        return true;
    }

    /** 成功与失败只允许先到者生效，SDK重复回调不能反转已确认终态。 */
    public synchronized void fail(Throwable error) {
        if (terminal) return;
        terminal = true;
        reportFailure(error);
    }

    public synchronized Throwable failure() { return failure; }

    private void reportFailure(Throwable error) {
        failure = error;
        log.error("AI 对话失败 requestId={}, sessionId={}", requestId, sessionId, error);
        events.run(() -> AiSseMessageHelper.sendError(userId, ERROR_MESSAGE));
    }
}
