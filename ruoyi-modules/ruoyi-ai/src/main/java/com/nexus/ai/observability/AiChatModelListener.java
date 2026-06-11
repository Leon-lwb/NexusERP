package com.nexus.ai.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;

/**
 * ChatModel 调用监听器
 * <p>实现 langchain4j 的 {@link ChatModelListener} 接口，记录 LLM 调用的请求、响应和错误</p>
 * <p>用于可观测性和 Token 用量统计，通过 {@link LangChain4jObservabilityConfig} 注册为 Spring Bean</p>
 * <p>注入到 {@code OpenAiChatModel.builder().listeners()} 和 {@code OpenAiStreamingChatModel.builder().listeners()}</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
public class AiChatModelListener implements ChatModelListener {

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var chatRequest = requestContext.chatRequest();
        var provider = requestContext.modelProvider();
        log.debug("[ChatModel] 请求: messages={}, provider={}",
            chatRequest.messages().size(), provider);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var chatResponse = responseContext.chatResponse();
        var chatRequest = responseContext.chatRequest();
        var provider = responseContext.modelProvider();
        var usage = chatResponse.tokenUsage();
        if (usage != null) {
            log.info("[ChatModel] 响应: inputTokens={}, outputTokens={}, provider={}",
                usage.inputTokenCount(), usage.outputTokenCount(), provider);
        } else {
            log.info("[ChatModel] 响应: provider={}", provider);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        var error = errorContext.error();
        var chatRequest = errorContext.chatRequest();
        var provider = errorContext.modelProvider();
        log.error("[ChatModel] 错误: error={}, provider={}",
            error.getMessage(), provider);
    }
}
