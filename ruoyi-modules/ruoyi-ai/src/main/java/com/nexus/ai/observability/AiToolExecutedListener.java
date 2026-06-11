package com.nexus.ai.observability;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool 执行监听器
 * <p>实现 langchain4j-agentic 的 {@link ToolExecutedEventListener} 接口，记录 Agent 工具调用的请求和结果</p>
 * <p>用于调用链追踪和调试，通过 {@link LangChain4jObservabilityConfig} 中的
 * {@code AiServiceListenerRegistrar.register()} 全局注册</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
public class AiToolExecutedListener implements ToolExecutedEventListener {

    @Override
    public void onEvent(ToolExecutedEvent event) {
        var invocationCtx = event.invocationContext();
        var request = event.request();
        log.info("[ToolExecuted] invocationId={}, toolName={}, requestId={}, resultLength={}",
            invocationCtx.invocationId(),
            request.name(),
            request.id(),
            event.resultText() != null ? event.resultText().length() : 0);

        if (log.isDebugEnabled()) {
            log.debug("[ToolExecuted] toolName={}, arguments={}, result={}",
                request.name(),
                request.arguments(),
                event.resultText());
        }
    }
}
