package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.observability.AiAgentListener;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.rag.RetrieverService;
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
 * <p>统一入口：智能路由普通流式对话 / RAG 增强对话 / Agent 编排</p>
 * <p>路由规则：数据查询关键词 → Agent 模式；knowledgeId 或知识检索关键词 → RAG 模式；其他 → 普通流式</p>
 *
 * @author NexusERP
 * @since 1.0.0
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
    private final RetrieverService retrieverService;
    private final AiAgentListener aiAgentListener;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 数据查询关键词（包含这些词时走 Agent 模式） */
    private static final Set<String> DATA_QUERY_KEYWORDS = Set.of(
        "查询", "统计", "分析", "报表", "图表", "数据", "金额", "数量",
        "订单", "库存", "采购", "销售", "生产", "供应商", "客户", "物料",
        "趋势", "排行", "占比", "分布", "对比", "总", "多少", "几个",
        "BOM", "工单", "补货", "退货", "入库", "出库", "仓库"
    );

    /** 知识库检索关键词（包含这些词时走 RAG 增强模式） */
    private static final Set<String> RAG_KEYWORDS = Set.of(
        "如何", "怎么", "操作", "流程", "步骤", "手册", "指南", "规则",
        "制度", "规范", "说明", "文档", "教程", "方法", "什么是", "定义",
        "区别", "比较", "配置", "设置", "使用", "创建", "审批"
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

        // 5. 智能路由：判断走 Agent 编排、RAG 增强还是普通流式对话
        boolean needAgent = containsDataQueryKeywords(request.getContent());
        boolean needRag = request.getKnowledgeId() != null || (!needAgent && containsRagKeywords(request.getContent()));

        if (needAgent) {
            invokeWithAgent(modelConfig, request, userId, finalSessionId, tenantId);
        } else if (needRag) {
            invokeWithRag(modelConfig, request, userId, finalSessionId, tenantId);
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

        // 使用 MessageWindowChatMemory 加载历史消息，限制最近 20 条（约 10 轮对话），避免超出 token 限制
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxMessages(20)
            .chatMemoryStore(persistentChatMemoryStore)
            .build();
        List<ChatMessage> allMessages = new ArrayList<>(memory.messages());

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
     * RAG 增强对话：先检索知识库，再流式生成回答
     * <p>将检索到的知识片段注入提示词，让 LLM 基于知识库内容回答</p>
     */
    private void invokeWithRag(AiModel modelConfig, AiChatRequest request,
                               Long userId, Long sessionId, Long tenantId) {
        StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId());

        // 加载历史消息
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxMessages(20)
            .chatMemoryStore(persistentChatMemoryStore)
            .build();
        List<ChatMessage> allMessages = new ArrayList<>(memory.messages());

        // RAG 检索增强：将用户问题与知识库匹配，生成增强提示词
        String augmentedQuery;
        if (request.getKnowledgeId() != null) {
            augmentedQuery = retrieverService.augmentPrompt(request.getKnowledgeId(), request.getContent());
        } else {
            augmentedQuery = retrieverService.augmentPrompt(request.getContent());
        }

        // 替换最后一条用户消息为增强后的提示词
        if (!allMessages.isEmpty()) {
            allMessages.remove(allMessages.size() - 1);
        }
        allMessages.add(dev.langchain4j.data.message.UserMessage.from(augmentedQuery));

        AiSseMessageHelper.sendThinking(userId);

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
                            saveAiMessage(sessionId, fullContent.toString());
                            AiSseMessageHelper.sendDone(userId);
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("AI RAG 对话失败", error);
                            AiSseMessageHelper.sendError(userId, "AI 对话失败: " + error.getMessage());
                        }
                    });
            } catch (Exception e) {
                log.error("AI RAG 对话异常", e);
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

        // 加载历史消息，构建带上下文的查询（避免多轮对话丢失上下文）
        List<ChatMessage> history = persistentChatMemoryStore.getMessages(sessionId);
        String contextualQuery = buildContextualQuery(history, request.getContent());

        // 为 SubAgent 配置基于 sessionId 的持久化记忆，确保工具调用链内部有上下文
        ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(20)
                .chatMemoryStore(persistentChatMemoryStore)
                .build())
            .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool,
                erpInventoryTool, erpPurchaseTool, erpSalesTool, erpProductionTool)
            .build();

        // 构建 ERP 报表 Agent
        ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(20)
                .chatMemoryStore(persistentChatMemoryStore)
                .build())
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
                long startTime = System.currentTimeMillis();
                aiAgentListener.onAgentStart("SupervisorAgent", contextualQuery);

                String result = supervisor.invoke(contextualQuery);

                long duration = System.currentTimeMillis() - startTime;
                aiAgentListener.onAgentComplete("SupervisorAgent", duration, 0);

                // 保存 AI 回复
                saveAiMessage(sessionId, result);

                AiSseMessageHelper.sendContent(userId, result);
                AiSseMessageHelper.sendDone(userId);
            } catch (Exception e) {
                log.error("AI Agent 对话失败", e);
                aiAgentListener.onAgentError("SupervisorAgent", e.getMessage());
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            } finally {
                AiRequestContext.clear();
            }
        }, aiExecutor);
    }

    /**
     * 构建带历史上下文的查询提示
     * <p>将最近 20 条历史消息拼接到当前问题前，确保 Agent 模式具备多轮对话能力</p>
     *
     * @param history      历史消息列表
     * @param currentQuery 当前用户问题
     * @return 带上下文的查询字符串
     */
    private String buildContextualQuery(List<ChatMessage> history, String currentQuery) {
        if (history == null || history.size() <= 1) {
            return currentQuery;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是本次会话的历史对话，请参考上下文回答当前问题：\n\n");
        // 只取最近 20 条历史消息（约 10 轮对话），避免超出 token 限制
        int startIdx = Math.max(0, history.size() - 21);
        for (int i = startIdx; i < history.size() - 1; i++) {
            ChatMessage msg = history.get(i);
            if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
                sb.append("用户：").append(um.singleText()).append("\n");
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage am) {
                sb.append("AI：").append(am.text()).append("\n");
            }
        }
        sb.append("\n当前问题：").append(currentQuery);
        return sb.toString();
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
        return containsKeywords(input, DATA_QUERY_KEYWORDS);
    }

    /**
     * 判断用户输入是否包含知识库检索关键词
     *
     * @param input 用户输入
     * @return 是否需要走 RAG 增强模式
     */
    private boolean containsRagKeywords(String input) {
        return containsKeywords(input, RAG_KEYWORDS);
    }

    /**
     * 通用关键词匹配
     *
     * @param input    用户输入
     * @param keywords 关键词集合
     * @return 是否包含任一关键词
     */
    private boolean containsKeywords(String input, Set<String> keywords) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        for (String keyword : keywords) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
