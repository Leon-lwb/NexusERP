package com.nexus.ai.observability;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool 执行监听器
 * <p>实现 langchain4j-agentic 的 {@link ToolExecutedEventListener} 接口，记录 Agent 工具调用的请求和结果</p>
 * <p>用于调用链追踪和调试，通过 {@link LangChain4jObservabilityConfig} 中的
 * {@code AiServiceListenerRegistrar.register()} 全局注册</p>
 * <p>当 ThreadLocal 中存在 OutputChannel 时，自动推送工具调用事件到前端</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
public class AiToolExecutedListener implements ToolExecutedEventListener {

    /** 请求级 OutputChannel，Agent 执行前设置，执行后清除 */
    private static final ThreadLocal<OutputChannel> CURRENT_CHANNEL = new ThreadLocal<>();

    /**
     * 设置当前请求的 OutputChannel
     */
    public static void setChannel(OutputChannel channel) {
        CURRENT_CHANNEL.set(channel);
    }

    /**
     * 清除当前请求的 OutputChannel
     */
    public static void clearChannel() {
        CURRENT_CHANNEL.remove();
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        var invocationCtx = event.invocationContext();
        var request = event.request();
        String toolName = request.name();
        String resultText = event.resultText();

        log.info("[ToolExecuted] invocationId={}, toolName={}, requestId={}, resultLength={}",
            invocationCtx.invocationId(),
            toolName,
            request.id(),
            resultText != null ? resultText.length() : 0);

        if (log.isDebugEnabled()) {
            log.debug("[ToolExecuted] toolName={}, arguments={}, result={}",
                toolName,
                request.arguments(),
                resultText);
        }

        // 推送工具调用事件到 OutputChannel（如果存在）
        OutputChannel channel = CURRENT_CHANNEL.get();
        if (channel != null) {
            // 先发送 TOOL_START（因为 observability API 没有工具执行前的事件，
            // 在工具执行完成时补发 TOOL_START + TOOL_END，确保前端能完整展示工具调用过程）
            AgentStreamHelper.sendToolStart(channel, toolName, request.arguments());
            // 再发送 TOOL_END
            AgentStreamHelper.sendToolEnd(channel, toolName, 0, resultText);
        }
    }
}
