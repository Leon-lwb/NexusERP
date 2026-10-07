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

    /** 兼容long字段的未知耗时标识；不是测得耗时，不能以调用链时间戳推算工具耗时。 */
    private static final long UNKNOWN_DURATION_MS = -1L;
    private static final String COMPLETED_SNAPSHOT = "【完成后补发，非实时开始/进度；执行耗时未知（unknown）】";

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

    /**
     * 工具执行事件回调：记录调用信息，并在存在 OutputChannel 时推送工具调用事件到前端。
     *
     * @param event 工具执行事件
     */
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
            // 1.18.0的ToolExecutedEvent不提供工具开始时间或耗时；invocationContext.timestamp()
            // 属于整个AI Service调用，不能冒充工具计时。旧前端依赖配对建卡，因此仅补发
            // 明确标注的完成快照，保留原参数/结果摘要，不宣称实时开始、实时进度或0毫秒执行。
            String arguments = request.arguments();
            AgentStreamHelper.sendToolStart(channel, toolName,
                COMPLETED_SNAPSHOT + (arguments == null ? "" : arguments));
            AgentStreamHelper.sendToolEnd(channel, toolName, UNKNOWN_DURATION_MS,
                COMPLETED_SNAPSHOT + (resultText == null ? "" : resultText));
        }
    }
}
