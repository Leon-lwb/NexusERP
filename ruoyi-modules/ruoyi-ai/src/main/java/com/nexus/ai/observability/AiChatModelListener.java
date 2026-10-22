package com.nexus.ai.observability;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.governance.AiGovernanceProperties;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiTokenUsageService;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

/**
 * ChatModel 调用监听器（持久化版）
 * <p>实现 langchain4j 的 {@link ChatModelListener} 接口，记录 LLM 调用的请求、响应和错误</p>
 * <p>在响应回调中：</p>
 * <ol>
 *   <li>更新当前 ai_message 的 modelId/inputTokens/outputTokens/tokenUsage</li>
 *   <li>异步更新 ai_token_usage 日统计表</li>
 * </ol>
 * <p>通过 {@link AiRequestContext}（ThreadLocal）获取当前请求的 sessionId/userId/modelId 等上下文</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class AiChatModelListener implements ChatModelListener {
    @Autowired(required = false)
    private AiGovernanceProperties governance;

    /** 聊天消息表 Mapper，用于更新消息的 Token 明细。 */
    private final AiMessageMapper aiMessageMapper;
    /** Token 用量业务服务，用于计算成本与更新日统计。 */
    private final IAiTokenUsageService aiTokenUsageService;

    /**
     * LLM 请求回调：记录请求消息数量与模型提供者（debug 级别）。
     *
     * @param requestContext 请求上下文
     */
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var chatRequest = requestContext.chatRequest();
        var provider = requestContext.modelProvider();
        log.debug("[ChatModel] 请求: messages={}, provider={}",
            chatRequest.messages().size(), provider);
    }

    /**
     * LLM 响应回调：记录 input/output tokens 到消息和日统计
     * <p>从 {@link AiRequestContext} 读取当前请求的 sessionId/userId/modelId</p>
     * <p>此方法在 Agent 模式下由 ChatModel 同步调用的线程执行，
     * AiRequestContext 已在 AiServiceFacade 的异步线程中通过 InheritableThreadLocal 传播</p>
     */
    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        if (governance != null && governance.isEnabled()) return;
        var chatResponse = responseContext.chatResponse();
        var provider = responseContext.modelProvider();

        // 从 AiRequestContext 获取上下文
        Long sessionId = AiRequestContext.getSessionId();
        Long userId = AiRequestContext.getUserId();
        String userName = AiRequestContext.getUserName();
        Long modelId = AiRequestContext.getModelId();
        String modelName = AiRequestContext.getModelName();
        String tenantId = AiRequestContext.getTenantId();

        if (chatResponse == null) {
            log.debug("[ChatModel] 响应为空: provider={}", provider);
            return;
        }

        var usage = chatResponse.tokenUsage();
        if (usage != null) {
            int inputTokens = usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
            int outputTokens = usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
            int totalTokens = inputTokens + outputTokens;

            log.info(
                    "[ChatModel] 响应: inputTokens={}, outputTokens={}, totalTokens={}, provider={},"
                            + " sessionId={}",
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    provider,
                    sessionId);

            // 1. 暂存 token 用量到 AiRequestContext（供 Agent 模式的 saveAiMessage 读取）
            AiRequestContext.setLastTokenUsage(inputTokens, outputTokens, modelId);

            if (governance != null && governance.isEnabled()) return;

            // 2. 更新日统计（Agent 模式在同一线程，AiRequestContext 可用）
            if (userId != null && modelId != null) {
                try {
                    BigDecimal cost = aiTokenUsageService.calculateCost(modelId, inputTokens, outputTokens);
                    aiTokenUsageService.incrementDailyUsage(tenantId, userId,
                        userName != null ? userName : String.valueOf(userId),
                        modelId, modelName != null ? modelName : provider.toString(),
                        inputTokens, outputTokens, cost);
                } catch (Exception e) {
                    log.warn("[ChatModel] 更新日统计失败: {}", e.getMessage());
                }
            }

        } else {
            log.info("[ChatModel] 响应: provider={}, tokenUsage=null", provider);
        }
    }

    /**
     * LLM 错误回调：记录调用异常信息与模型提供者。
     *
     * @param errorContext 错误上下文
     */
    @Override
    public void onError(ChatModelErrorContext errorContext) {
        var error = errorContext.error();
        var provider = errorContext.modelProvider();
        log.error("[ChatModel] 错误: error={}, provider={}",
            error.getMessage(), provider);
    }

}
