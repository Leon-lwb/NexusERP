package com.nexus.ai.observability;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiTokenUsageService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        var chatResponse = responseContext.chatResponse();
        var provider = responseContext.modelProvider();

        // 从 AiRequestContext 获取上下文
        Long sessionId = AiRequestContext.getSessionId();
        Long userId = AiRequestContext.getUserId();
        String userName = AiRequestContext.getUserName();
        Long modelId = AiRequestContext.getModelId();
        String modelName = AiRequestContext.getModelName();
        Long tenantId = AiRequestContext.getTenantId();

        if (chatResponse == null) {
            log.debug("[ChatModel] 响应为空: provider={}", provider);
            return;
        }

        var usage = chatResponse.tokenUsage();
        if (usage != null) {
            int inputTokens = usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
            int outputTokens = usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
            int totalTokens = inputTokens + outputTokens;

            log.info("[ChatModel] 响应: inputTokens={}, outputTokens={}, totalTokens={}, provider={}, sessionId={}",
                inputTokens, outputTokens, totalTokens, provider, sessionId);

            // 1. 暂存 token 用量到 AiRequestContext（供 Agent 模式的 saveAiMessage 读取）
            AiRequestContext.setLastTokenUsage(inputTokens, outputTokens, modelId);

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

            // 3. 更新 ai_message 的 token 明细（仅流式/RAG 模式需要，
            //    Agent 模式由 saveAiMessageWithTokens 直接写入）
            if (sessionId != null) {
                updateLatestAssistantMessage(sessionId, modelId, inputTokens, outputTokens, totalTokens);
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

    /**
     * 更新会话中最近一条 ASSISTANT 消息的 Token 明细
     *
     * @param sessionId    会话ID
     * @param modelId      模型ID
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param totalTokens  总 Token 数
     */
    private void updateLatestAssistantMessage(Long sessionId, Long modelId,
                                               int inputTokens, int outputTokens, int totalTokens) {
        try {
            LambdaUpdateWrapper<AiMessage> update = new LambdaUpdateWrapper<>();
            update.eq(AiMessage::getSessionId, sessionId)
                .eq(AiMessage::getRole, "ASSISTANT")
                .isNull(AiMessage::getTokenUsage)
                .orderByDesc(AiMessage::getCreateTime)
                .last("LIMIT 1")
                .set(AiMessage::getModelId, modelId)
                .set(AiMessage::getInputTokens, inputTokens)
                .set(AiMessage::getOutputTokens, outputTokens)
                .set(AiMessage::getTokenUsage, (long) totalTokens);
            aiMessageMapper.update(null, update);
        } catch (Exception e) {
            log.warn("[ChatModel] 更新消息 Token 明细失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
