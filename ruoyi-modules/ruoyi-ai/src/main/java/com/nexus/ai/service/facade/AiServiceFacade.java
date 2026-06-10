package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.ai.tool.ExecuteSqlQueryTool;
import com.nexus.ai.tool.ErpInventoryTool;
import com.nexus.ai.tool.ErpPurchaseTool;
import com.nexus.ai.tool.ErpProductionTool;
import com.nexus.ai.tool.ErpSalesTool;
import com.nexus.ai.tool.QueryAllTablesTool;
import com.nexus.ai.tool.QueryTableSchemaTool;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.satoken.utils.LoginHelper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 服务门面
 * <p>统一入口：智能路由普通流式对话 / Agent 编排</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceFacade {

    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;
    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final PersistentChatMemoryStore persistentChatMemoryStore;
    private final QueryAllTablesTool queryAllTablesTool;
    private final QueryTableSchemaTool queryTableSchemaTool;
    private final ExecuteSqlQueryTool executeSqlQueryTool;
    private final ErpInventoryTool erpInventoryTool;
    private final ErpPurchaseTool erpPurchaseTool;
    private final ErpSalesTool erpSalesTool;
    private final ErpProductionTool erpProductionTool;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 数据查询关键词（包含这些词时走 Agent 模式） */
    private static final Set<String> DATA_QUERY_KEYWORDS = Set.of(
        "查询", "统计", "分析", "报表", "图表", "数据", "金额", "数量",
        "订单", "库存", "采购", "销售", "生产", "供应商", "客户", "物料",
        "趋势", "排行", "占比", "分布", "对比", "总", "多少", "几个",
        "BOM", "工单", "补货", "退货", "入库", "出库", "仓库"
    );

    /**
     * 聊天入口
     * <p>
     * 1. 获取模型配置 → 2. 创建/复用会话 → 3. 保存消息 → 4. 智能路由 → 5. 异步执行 → 6. SSE 推送结果
     * </p>
     *
     * @param request 聊天请求
     * @return 会话ID
     */
    public Long chat(AiChatRequest request) {
        Long userId = LoginHelper.getUserId();

        // 1. 获取模型配置
        AiModel modelConfig;
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            modelConfig = aiModelService.selectModelByName(request.getModel());
        } else {
            modelConfig = aiModelService.selectDefaultModel();
        }
        if (modelConfig == null) {
            AiSseMessageHelper.sendError(userId, "未找到可用的 AI 模型，请先配置模型");
            return null;
        }

        // 2. 创建或复用会话
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            AiSession session = new AiSession();
            session.setUserId(userId);
            String title = request.getContent();
            session.setTitle(title.length() > 50 ? title.substring(0, 50) + "..." : title);
            session.setModelName(modelConfig.getModelName());
            aiSessionMapper.insert(session);
            sessionId = session.getId();
        }

        // 3. 保存用户消息
        AiMessage userMsg = new AiMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(request.getContent());
        aiMessageMapper.insert(userMsg);

        // 4. 捕获请求上下文
        Long tenantId = null;
        try {
            String tenantIdStr = LoginHelper.getTenantId();
            if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
                tenantId = Long.parseLong(tenantIdStr);
            }
        } catch (NumberFormatException e) {
            log.warn("租户ID格式异常: {}", LoginHelper.getTenantId());
        }
        final Long finalSessionId = sessionId;

        // 5. 智能路由：判断走普通流式对话还是 Agent 编排
        boolean needAgent = containsDataQueryKeywords(request.getContent());

        if (needAgent) {
            invokeWithAgent(modelConfig, request, userId, finalSessionId, tenantId);
        } else {
            invokeWithStreaming(modelConfig, request, userId, finalSessionId, tenantId);
        }

        return sessionId;
    }

    /**
     * 普通流式对话：StreamingChatModel → 逐 Token 推送
     */
    private void invokeWithStreaming(AiModel modelConfig, AiChatRequest request,
                                     Long userId, Long sessionId, Long tenantId) {
        StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId());

        // 加载历史消息
        List<ChatMessage> historyMessages = persistentChatMemoryStore.getMessages(sessionId);
        List<ChatMessage> allMessages = new ArrayList<>(historyMessages);

        AiSseMessageHelper.sendThinking(userId);

        // 用于拼接完整 AI 回复（StringBuffer 线程安全，流式回调可能由不同线程执行）
        StringBuffer fullContent = new StringBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequestContext.set(tenantId, userId);

                streamingModel.chat(ChatRequest.builder()
                        .messages(allMessages)
                        .build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialToken) {
                            fullContent.append(partialToken);
                            AiSseMessageHelper.sendContentIncrement(userId, partialToken);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            // 保存完整 AI 回复到数据库
                            saveAiMessage(sessionId, fullContent.toString());
                            AiSseMessageHelper.sendDone(userId);
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("AI 流式对话失败", error);
                            AiSseMessageHelper.sendError(userId, "AI 对话失败: " + error.getMessage());
                        }
                    });
            } catch (Exception e) {
                log.error("AI 流式对话异常", e);
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            } finally {
                AiRequestContext.clear();
            }
        }, aiExecutor);
    }

    /**
     * Agent 编排模式：SupervisorAgent + 同步 ChatModel → 一次性推送结果
     * <p>SupervisorAgent 暂不支持流式回调，结果完成后一次性推送</p>
     */
    private void invokeWithAgent(AiModel modelConfig, AiChatRequest request,
                                 Long userId, Long sessionId, Long tenantId) {
        ChatModel chatModel = chatModelFactory.getChatModel(modelConfig.getId());

        // 构建 ERP 查询 Agent（不配置 chatMemoryProvider，避免默认 memoryId="default" 导致 PersistentChatMemoryStore 转换失败。
        // 历史上下文由 Supervisor / AiServiceFacade 层统一维护。）
        ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel)
            .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
                erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)
            .build();

        // 构建 ERP 报表 Agent
        ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel)
            .tools(queryAllTablesTool, executeSqlQueryTool)
            .build();

        // 构建 Supervisor
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(queryAgent, reportAgent)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .maxAgentsInvocations(10)
            .build();

        AiSseMessageHelper.sendThinking(userId);

        CompletableFuture.runAsync(() -> {
            try {
                AiRequestContext.set(tenantId, userId);
                String result = supervisor.invoke(request.getContent());

                // 保存 AI 回复
                saveAiMessage(sessionId, result);

                AiSseMessageHelper.sendContent(userId, result);
                AiSseMessageHelper.sendDone(userId);
            } catch (Exception e) {
                log.error("AI Agent 对话失败", e);
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            } finally {
                AiRequestContext.clear();
            }
        }, aiExecutor);
    }

    /**
     * 保存 AI 回复消息到数据库
     */
    private void saveAiMessage(Long sessionId, String content) {
        AiMessage aiMsg = new AiMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(content);
        aiMessageMapper.insert(aiMsg);
    }

    /**
     * 判断用户输入是否包含数据查询关键词
     *
     * @param input 用户输入
     * @return 是否需要走 Agent 模式
     */
    private boolean containsDataQueryKeywords(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        for (String keyword : DATA_QUERY_KEYWORDS) {
            if (lowerInput.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
