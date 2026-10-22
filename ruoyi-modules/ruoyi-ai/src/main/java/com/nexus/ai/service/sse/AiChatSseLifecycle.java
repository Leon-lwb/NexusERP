package com.nexus.ai.service.sse;

import com.nexus.ai.service.governance.AiChatExecutionService;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.dto.AiChatRequest;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/** 普通聊天的关联信封和唯一终态。取消受理不等于工作已退出。 */
@Slf4j
public final class AiChatSseLifecycle {
    public static final String ERROR_MESSAGE = "AI 对话失败，请稍后重试。";
    private final String requestId;
    private final Long userId;
    private final Long sessionId;
    private final AiChatEventContext events;
    private final AiChatExecutionService.Managed managed;
    private boolean terminal;
    private Throwable failure;

    public AiChatSseLifecycle(Long user, Long session) {
        this(user, session, null);
    }

    public AiChatSseLifecycle(Long user, Long session, String client) {
        this(user, session, client, null, null);
    }

    public AiChatSseLifecycle(
            Long user,
            Long session,
            String client,
            String serverRequest,
            AiChatExecutionService.Managed managed) {
        if (!AiChatRequest.validClientRequestId(client))
            throw new IllegalArgumentException("请求关联标识格式不正确");
        userId = user;
        sessionId = session;
        this.managed = managed;
        requestId = serverRequest == null ? UUID.randomUUID().toString() : serverRequest;
        events = new AiChatEventContext(user, session, requestId, client);
        if (managed != null) {
            events.messageWriter(managed::persist);
            managed.onCancel(this::cancelRequested);
        }
    }

    public String requestId() {
        return requestId;
    }

    public static String currentRequestId() {
        return AiChatEventContext.currentRequestId();
    }

    public void run(Runnable task) {
        try {
            if (managed == null) events.run(task);
            else managed.run(() -> events.run(task));
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    public void observe(Runnable task) {
        events.run(task);
    }

    public boolean persistent() {
        return managed != null;
    }

    public void eventFailure(Throwable error) {
        if (managed == null) fail(error);
        else
            log.warn(
                    "聊天事件交付失败 requestId={}, cause={}", requestId, error.getClass().getSimpleName());
    }

    public static boolean persistAssistantMessage(AiMessage message) {
        return AiChatEventContext.persistAssistant(message);
    }

    public static void persistedMessage(Long session, Long message) {
        AiChatEventContext.persisted(session, message);
    }

    public synchronized void partial(String content) {
        if (managed != null) managed.partial(content);
    }

    public synchronized void progress(Runnable event) {
        if (!terminal && (managed == null || !managed.cancelRequested())) events.run(event);
    }

    private synchronized void cancelRequested() {
        if (!terminal) events.run(() -> AiSseMessageHelper.sendCancellation(userId, false));
    }

    public synchronized boolean complete(Runnable beforeDone) {
        if (terminal) return false;
        terminal = true;
        try {
            if (managed == null) events.run(beforeDone);
            else {
                events.stage();
                String[] state = new String[1];
                events.run(
                        () ->
                                state[0] =
                                        managed.complete(
                                                () -> {
                                                    beforeDone.run();
                                                    managed.linkedMessage(
                                                            AiChatEventContext.currentMessageId());
                                                }));
                if ("CANCELLED".equals(state[0])) {
                    events.discard();
                    publishCancelled();
                    return false;
                }
                if (!"SUCCEEDED".equals(state[0])) {
                    events.discard();
                    failure = new TimeoutException("请求超过截止时间");
                    events.run(() -> AiSseMessageHelper.sendError(userId, ERROR_MESSAGE));
                    return false;
                }
                events.run(events::flush);
            }
        } catch (Exception error) {
            events.discard();
            reportFailure(error);
            return false;
        }
        events.run(() -> AiSseMessageHelper.sendDone(userId));
        return true;
    }

    public synchronized void fail(Throwable error) {
        if (terminal) return;
        terminal = true;
        if (managed != null && managed.cancelRequested()) {
            confirmCancelled();
            return;
        }
        reportFailure(error);
    }

    public synchronized Throwable failure() {
        return failure;
    }

    private void publishCancelled() {
        failure = new CancellationException("请求已取消");
        events.run(
                () -> {
                    AiChatEventContext.persisted(sessionId, managed.messageId());
                    AiSseMessageHelper.sendCancellation(userId, true);
                });
    }

    private void confirmCancelled() {
        try {
            if (managed.cancelComplete()) {
                publishCancelled();
                return;
            }
        } catch (RuntimeException error) {
            failure = error;
        }
        events.run(() -> AiSseMessageHelper.sendError(userId, ERROR_MESSAGE));
    }

    private void reportFailure(Throwable error) {
        failure = error;
        log.error("AI 对话失败 requestId={}, sessionId={}", requestId, sessionId, error);
        if (managed != null)
            try {
                String state = managed.fail(error);
                if ("CANCELLED".equals(state)) {
                    publishCancelled();
                    return;
                }
            } catch (RuntimeException ignored) {
                /* 无法确认的执行由租约恢复进入核对状态。 */
            }
        events.run(() -> AiSseMessageHelper.sendError(userId, ERROR_MESSAGE));
    }
}
