package com.nexus.ai.observability;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * 流式 Tool 执行监听器
 * <p>
 * 继承自 {@link AiToolExecutedListener}，额外通过 OutputChannel 推送工具调用事件到前端。
 * 每次请求创建新实例，绑定到当前请求的 OutputChannel。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
public class StreamingToolExecutedListener implements ToolExecutedEventListener {

    /** 当前请求的 OutputChannel，用于向前端推送工具执行事件。 */
    private final OutputChannel channel;

    public StreamingToolExecutedListener(OutputChannel channel) {
        this.channel = channel;
    }

    /**
     * 工具执行事件回调：推送工具完成事件到当前请求的 OutputChannel 并记录日志。
     *
     * @param event 工具执行事件
     */
    @Override
    public void onEvent(ToolExecutedEvent event) {
        var request = event.request();
        String toolName = request.name();

        // 推送工具调用完成事件到 OutputChannel
        String resultText = event.resultText();
        AgentStreamHelper.sendToolEnd(channel, toolName, 0, resultText);

        // 记录日志
        log.info("[StreamingToolExecuted] toolName={}, resultLength={}",
            toolName, resultText != null ? resultText.length() : 0);

        if (log.isDebugEnabled()) {
            log.debug("[StreamingToolExecuted] toolName={}, arguments={}, result={}",
                toolName, request.arguments(), resultText);
        }
    }
}
