package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.ai.observability.AiAgentListener;
import com.nexus.ai.observability.AiToolExecutedListener;
import com.nexus.ai.observability.AgentStreamHelper;
import com.nexus.ai.observability.OutputChannel;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.prompt.PromptTemplateService;
import com.nexus.ai.service.rag.RetrieverService;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.IAiTokenUsageService;
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
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 服务门面
 * <p>统一入口：智能路由普通流式对话 / RAG 增强对话 / Agent 编排</p>
 * <p>路由规则：数据查询关键词 → Agent 模式；knowledgeId 或知识检索关键词 → RAG 模式；其他 → 普通流式</p>
 * <p>Agent 模式支持流式推送：通过 OutputChannel 跨线程事件总线 + SSE 实时推送工具调用过程</p>
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
    private final ToolProviderFactory toolProviderFactory;
    private final RetrieverService retrieverService;
    private final AiAgentListener aiAgentListener;
    private final PromptTemplateService promptTemplateService;
    private final IAiTokenUsageService aiTokenUsageService;

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
        String userName = null;
        try {
            userName = LoginHelper.getUsername();
        } catch (Exception e) {
            log.debug("获取用户名称失败: {}", e.getMessage());
        }
        final Long finalSessionId = sessionId;
        final String finalUserName = userName;

        // 5. 智能路由：前端显式指定模式 或 后端根据关键词自动判断
        boolean needAgent;
        boolean needRag;

        String mode = request.getMode();
        if ("agent".equalsIgnoreCase(mode)) {
            needAgent = true;
            needRag = false;
        } else if ("rag".equalsIgnoreCase(mode)) {
            needAgent = false;
            needRag = true;
        } else if ("chat".equalsIgnoreCase(mode)) {
            needAgent = false;
            needRag = false;
        } else {
            // auto 模式（默认）：根据关键词自动路由
            needAgent = containsDataQueryKeywords(request.getContent());
            needRag = request.getKnowledgeId() != null || (!needAgent && containsRagKeywords(request.getContent()));
        }

        if (needAgent) {
            invokeWithAgent(modelConfig, request, userId, finalSessionId, tenantId, finalUserName);
        } else if (needRag) {
            invokeWithRag(modelConfig, request, userId, finalSessionId, tenantId, finalUserName);
        } else {
            invokeWithStreaming(modelConfig, request, userId, finalSessionId, tenantId, finalUserName);
        }

        return sessionId;
    }

    /**
     * 普通流式对话：StreamingChatModel → 逐 Token 推送
     */
    private void invokeWithStreaming(AiModel modelConfig, AiChatRequest request,
                                     Long userId, Long sessionId, Long tenantId, String userName) {
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
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());

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
                            // 保存完整 AI 回复到数据库（含 token 明细）
                            // 注意：回调运行在 OkHttp I/O 线程，AiRequestContext 不可用，
                            // 因此直接使用闭包捕获的局部变量传递上下文
                            saveAiMessageWithTokens(sessionId, fullContent.toString(),
                                modelConfig.getId(), response, tenantId, userId, userName, modelConfig.getModelName());
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
                               Long userId, Long sessionId, Long tenantId, String userName) {
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

        // 从后往前查找最后一条 UserMessage 并替换为增强后的提示词
        // （避免当末尾是 AiMessage 时误删，或连续多条 UserMessage 时删错）
        boolean replaced = false;
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            if (allMessages.get(i) instanceof dev.langchain4j.data.message.UserMessage) {
                allMessages.set(i, dev.langchain4j.data.message.UserMessage.from(augmentedQuery));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            allMessages.add(dev.langchain4j.data.message.UserMessage.from(augmentedQuery));
        }

        AiSseMessageHelper.sendThinking(userId);

        StringBuffer fullContent = new StringBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());

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
                            saveAiMessageWithTokens(sessionId, fullContent.toString(),
                                modelConfig.getId(), response, tenantId, userId, userName, modelConfig.getModelName());
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
     * Agent 编排模式：SupervisorAgent + 同步 ChatModel → 流式推送结果
     * <p>
     * 使用 OutputChannel 跨线程事件总线，将 Agent 执行过程中的工具调用事件推送到前端。
     * SubAgent 不使用持久化记忆，避免工具调用中间结果膨胀上下文导致 Agent 混乱。
     * </p>
     */
    private void invokeWithAgent(AiModel modelConfig, AiChatRequest request,
                                 Long userId, Long sessionId, Long tenantId, String userName) {
        // Agent 模式使用低 temperature（0.1），提高 SQL 生成准确性
        ChatModel chatModel = chatModelFactory.getAgentChatModel(modelConfig.getId());

        // 加载历史消息，构建带上下文的查询（避免多轮对话丢失上下文）
        List<ChatMessage> history = persistentChatMemoryStore.getMessages(sessionId);
        String contextualQuery = buildContextualQuery(history, request.getContent());

        // 动态加载已启用的工具（差异化注入：QueryAgent 需要 QUERY+ALL，ReportAgent 只需要 ALL）
        List<Object> queryTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("QUERY,ALL");
        List<Object> reportTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("ALL");

        // 会话级 MCP 工具选择：优先使用请求中的 enabledToolIds，其次使用会话保存的配置
        ToolProvider mcpToolProvider = resolveMcpToolProvider(request, sessionId);

        // 注入 PromptTemplateService 渲染的动态上下文
        // 由于 LangChain4j Supervisor 模式下 SubAgent 的 @UserMessage 由框架填充，
        // 无法直接为 SubAgent 注入独立上下文，因此将所有上下文合并到 Supervisor 层，
        // 由 Supervisor 在分发时将关键信息传递给 SubAgent
        String queryAgentContext = promptTemplateService.renderQueryAgentContext();
        String reportAgentContext = promptTemplateService.renderReportAgentContext();
        String supervisorContext = promptTemplateService.renderSupervisorContext();

        // 构建增强查询：将 Supervisor 上下文 + SubAgent 上下文 + 原始查询合并
        // Supervisor 会将完整上下文连同用户问题一起分发给对应的 SubAgent
        String enhancedQuery = supervisorContext + "\n\n"
            + "【数据查询助手上下文】\n" + queryAgentContext + "\n\n"
            + "【报表生成助手上下文】\n" + reportAgentContext + "\n\n"
            + "【用户问题】\n" + contextualQuery;

        // SubAgent 不使用持久化记忆，避免工具调用中间结果膨胀上下文
        var queryAgentBuilder = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel);
        if (!queryTools.isEmpty()) {
            queryAgentBuilder.tools(queryTools.toArray());
        }
        if (mcpToolProvider != null) {
            queryAgentBuilder.toolProvider(mcpToolProvider);
        }
        ErpQueryAgent queryAgent = queryAgentBuilder.build();

        // 构建 ERP 报表 Agent（同样不使用持久化记忆）
        var reportAgentBuilder = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel);
        if (!reportTools.isEmpty()) {
            reportAgentBuilder.tools(reportTools.toArray());
        }
        if (mcpToolProvider != null) {
            reportAgentBuilder.toolProvider(mcpToolProvider);
        }
        ErpReportAgent reportAgent = reportAgentBuilder.build();

        // 构建 Supervisor（增大 maxAgentsInvocations，避免复杂查询耗尽迭代次数）
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(queryAgent, reportAgent)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .maxAgentsInvocations(20)
            .build();

        AiSseMessageHelper.sendThinking(userId);

        // 创建 OutputChannel 用于流式推送
        String requestId = UUID.randomUUID().toString();
        OutputChannel channel = OutputChannel.create(requestId);

        CompletableFuture.runAsync(() -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());
                // 设置 ThreadLocal OutputChannel，使 AiToolExecutedListener 能推送事件
                AiToolExecutedListener.setChannel(channel);

                long startTime = System.currentTimeMillis();
                aiAgentListener.onAgentStart("SupervisorAgent", enhancedQuery);

                // 推送 Agent 开始思考
                AgentStreamHelper.sendThinking(channel, "SupervisorAgent", "开始分析您的问题...");

                String result = supervisor.invoke(enhancedQuery);

                long duration = System.currentTimeMillis() - startTime;
                aiAgentListener.onAgentComplete("SupervisorAgent", duration, 0);

                // 保存 AI 回复（含 Token 明细，从 AiRequestContext 暂存中读取）
                saveAiMessageFromContext(sessionId, result, tenantId, userId, userName, modelConfig.getModelName());

                // 推送最终结果
                AiSseMessageHelper.sendContent(userId, result);
                AiSseMessageHelper.sendDone(userId);

                channel.complete();
            } catch (Exception e) {
                log.error("AI Agent 对话失败", e);
                aiAgentListener.onAgentError("SupervisorAgent", e.getMessage());
                AgentStreamHelper.sendError(channel, "SupervisorAgent", e.getMessage());
                channel.completeWithError(e);
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            } finally {
                AiToolExecutedListener.clearChannel();
                AiRequestContext.clear();
                OutputChannel.remove(requestId);
            }
        }, aiExecutor);

        // 异步消费 OutputChannel 事件，根据事件类型路由到对应的 SSE 推送方法
        final com.fasterxml.jackson.databind.ObjectMapper drainMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        CompletableFuture.runAsync(() -> {
            try {
                channel.drain(eventJson -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = drainMapper.readTree(eventJson);
                        String eventType = node.has("type") ? node.get("type").asText() : "";

                        switch (eventType) {
                            case "TOOL_START" -> {
                                String toolName = node.has("toolName") ? node.get("toolName").asText() : "unknown";
                                String args = node.has("arguments") ? node.get("arguments").asText() : "";
                                AiSseMessageHelper.sendToolStart(userId, toolName, args);
                            }
                            case "TOOL_END" -> {
                                String toolName = node.has("toolName") ? node.get("toolName").asText() : "unknown";
                                long dur = node.has("duration") ? node.get("duration").asLong() : 0;
                                String summary = node.has("resultSummary") ? node.get("resultSummary").asText() : "";
                                AiSseMessageHelper.sendToolEnd(userId, toolName, dur, summary);
                            }
                            case "THINKING" -> {
                                String agentName = node.has("agentName") ? node.get("agentName").asText() : "Agent";
                                String message = node.has("message") ? node.get("message").asText() : "";
                                AiSseMessageHelper.sendAgentThinking(userId, agentName, message);
                            }
                            case "ERROR" -> {
                                String errorMsg = node.has("error") ? node.get("error").asText() : "未知错误";
                                AiSseMessageHelper.sendError(userId, errorMsg);
                            }
                            default -> log.warn("[Agent] 未知 OutputChannel 事件类型: {}", eventType);
                        }
                    } catch (Exception e) {
                        log.warn("[Agent] 解析 OutputChannel 事件失败: {}", e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Agent] OutputChannel drain 被中断");
            } catch (Exception e) {
                log.error("[Agent] OutputChannel drain 异常", e);
            }
        }, aiExecutor);
    }

    /**
     * 构建带历史上下文的查询提示
     * <p>按字符数预算控制上下文窗口，超出预算时保留最近消息完整、更早消息只保留摘要</p>
     * <p>预算分配：最近 3 轮（6 条消息）完整保留，更早消息截取前 100 字符</p>
     *
     * @param history      历史消息列表
     * @param currentQuery 当前用户问题
     * @return 带上下文的查询字符串
     */
    private String buildContextualQuery(List<ChatMessage> history, String currentQuery) {
        if (history == null || history.size() <= 1) {
            return currentQuery;
        }

        final int maxChars = 6000;
        StringBuilder sb = new StringBuilder();
        sb.append("以下是本次会话的历史对话，请参考上下文回答当前问题：\n\n");

        // 从最新消息往前遍历，最近 3 轮（6 条）完整保留，更早消息截断
        int recentFullMessages = 6; // 最近 3 轮 = 6 条消息
        int startIdx = 0;
        int totalChars = 0;

        // 先计算最近 N 条完整消息的字符数
        int recentStart = Math.max(0, history.size() - 1 - recentFullMessages);
        for (int i = recentStart; i < history.size() - 1; i++) {
            ChatMessage msg = history.get(i);
            totalChars += getMessageTextLength(msg);
        }

        // 如果最近消息已超出预算，只保留最近消息（截断到 200 字符）
        if (totalChars > maxChars) {
            int recentIdx = Math.max(0, history.size() - 1 - recentFullMessages);
            for (int i = recentIdx; i < history.size() - 1; i++) {
                ChatMessage msg = history.get(i);
                appendMessage(sb, msg, 200);
            }
        } else {
            // 最近消息完整保留，更早消息只保留摘要（100 字符）
            int remainingBudget = maxChars - totalChars;

            // 先输出更早的消息（摘要）
            for (int i = 0; i < recentStart; i++) {
                ChatMessage msg = history.get(i);
                int summaryLen = Math.min(100, remainingBudget);
                if (summaryLen <= 0) break;
                appendMessage(sb, msg, summaryLen);
                remainingBudget -= summaryLen;
            }

            // 输出最近消息（完整，但单条不超过 500 字符）
            for (int i = recentStart; i < history.size() - 1; i++) {
                ChatMessage msg = history.get(i);
                appendMessage(sb, msg, 500);
            }
        }

        sb.append("\n当前问题：").append(currentQuery);
        return sb.toString();
    }

    /**
     * 获取消息文本长度
     *
     * @param msg 聊天消息
     * @return 文本字符数
     */
    private int getMessageTextLength(ChatMessage msg) {
        if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
            return um.singleText().length();
        } else if (msg instanceof dev.langchain4j.data.message.AiMessage am) {
            return am.text() != null ? am.text().length() : 0;
        }
        return 0;
    }

    /**
     * 将消息追加到 StringBuilder，超过 maxLength 时截断
     *
     * @param sb        目标 StringBuilder
     * @param msg       聊天消息
     * @param maxLength 单条消息最大字符数
     */
    private void appendMessage(StringBuilder sb, ChatMessage msg, int maxLength) {
        if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
            String text = um.singleText();
            sb.append("用户：").append(text.length() > maxLength ? text.substring(0, maxLength) + "..." : text).append("\n");
        } else if (msg instanceof dev.langchain4j.data.message.AiMessage am) {
            String text = am.text();
            if (text != null) {
                sb.append("AI：").append(text.length() > maxLength ? text.substring(0, maxLength) + "..." : text).append("\n");
            }
        }
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
     * 保存 AI 回复消息到数据库（Agent 模式，从 AiRequestContext 暂存读取 Token）
     * <p>Agent 模式下，AiChatModelListener 在 supervisor.invoke() 内部同步调用，
     * 将 token 用量暂存到 AiRequestContext，此方法在 invoke() 之后读取</p>
     *
     * @param sessionId 会话ID
     * @param content   回复内容
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param userName  用户名称
     * @param modelName 模型名称
     */
    private void saveAiMessageFromContext(Long sessionId, String content,
                                           Long tenantId, Long userId,
                                           String userName, String modelName) {
        AiMessage aiMsg = new AiMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(content);

        // 从 AiRequestContext 暂存读取 token 用量
        Integer lastInputTokens = AiRequestContext.getLastInputTokens();
        Integer lastOutputTokens = AiRequestContext.getLastOutputTokens();
        Long lastModelId = AiRequestContext.getLastModelId();

        if (lastInputTokens != null || lastOutputTokens != null) {
            int inputTokens = lastInputTokens != null ? lastInputTokens : 0;
            int outputTokens = lastOutputTokens != null ? lastOutputTokens : 0;
            aiMsg.setModelId(lastModelId);
            aiMsg.setInputTokens(inputTokens);
            aiMsg.setOutputTokens(outputTokens);
            aiMsg.setTokenUsage((long) (inputTokens + outputTokens));
        }

        aiMessageMapper.insert(aiMsg);
        // Agent 模式的日统计已由 AiChatModelListener 处理，无需重复
    }

    /**
     * 保存 AI 回复消息到数据库（含 Token 明细）
     * <p>从 ChatResponse 中提取 inputTokens/outputTokens，同时更新日统计</p>
     * <p>上下文参数（tenantId/userId/userName/modelName）由调用方通过闭包捕获传入，
     * 而非从 AiRequestContext 读取，因为流式回调运行在 OkHttp I/O 线程，
     * AiRequestContext 的 ThreadLocal 值在该线程上不可用</p>
     *
     * @param sessionId  会话ID
     * @param content    回复内容
     * @param modelId    模型ID
     * @param response   ChatResponse（含 tokenUsage）
     * @param tenantId   租户ID
     * @param userId     用户ID
     * @param userName   用户名称
     * @param modelName  模型名称
     */
    private void saveAiMessageWithTokens(Long sessionId, String content, Long modelId,
                                          ChatResponse response, Long tenantId, Long userId,
                                          String userName, String modelName) {
        AiMessage aiMsg = new AiMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(content);
        aiMsg.setModelId(modelId);

        // 提取 Token 用量
        if (response != null && response.tokenUsage() != null) {
            int inputTokens = response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0;
            int outputTokens = response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0;
            aiMsg.setInputTokens(inputTokens);
            aiMsg.setOutputTokens(outputTokens);
            aiMsg.setTokenUsage((long) (inputTokens + outputTokens));

            // 更新日统计（使用闭包捕获的参数，而非 AiRequestContext）
            if (userId != null && modelId != null) {
                try {
                    java.math.BigDecimal cost = aiTokenUsageService.calculateCost(modelId, inputTokens, outputTokens);
                    aiTokenUsageService.incrementDailyUsage(tenantId, userId,
                        userName != null ? userName : String.valueOf(userId),
                        modelId, modelName != null ? modelName : "unknown",
                        inputTokens, outputTokens, cost);
                } catch (Exception e) {
                    log.warn("[AiServiceFacade] 更新日统计失败: {}", e.getMessage());
                }
            }
        }

        aiMessageMapper.insert(aiMsg);
    }

    /**
     * 解析会话级 MCP 工具选择
     * <p>优先级：请求中的 enabledToolIds > 会话保存的 enabledToolIds > 全部已启用工具</p>
     * <p>首次设置后保存到会话，后续对话自动复用</p>
     *
     * @param request   聊天请求
     * @param sessionId 会话ID
     * @return MCP ToolProvider（可能为 null，表示不使用 MCP 工具）
     */
    private ToolProvider resolveMcpToolProvider(AiChatRequest request, Long sessionId) {
        List<Long> toolIds = null;

        // 1. 优先使用请求中指定的工具 ID
        if (request.getEnabledToolIds() != null && !request.getEnabledToolIds().isEmpty()) {
            toolIds = request.getEnabledToolIds();
            // 保存到会话，后续对话自动复用
            saveEnabledToolIdsToSession(sessionId, toolIds);
        } else {
            // 2. 使用会话保存的工具配置
            AiSession session = aiSessionMapper.selectById(sessionId);
            if (session != null && session.getEnabledToolIds() != null && !session.getEnabledToolIds().isEmpty()) {
                toolIds = parseToolIds(session.getEnabledToolIds());
            }
            // 3. toolIds 为 null 时，使用全部已启用工具
        }

        if (toolIds != null) {
            if (toolIds.isEmpty()) {
                // 空列表表示不使用任何 MCP 工具
                return null;
            }
            return toolProviderFactory.getMcpToolProvidersByIds(toolIds);
        }

        // 默认：加载全部已启用的 MCP 工具
        return toolProviderFactory.getAllEnabledMcpToolProviders();
    }

    /**
     * 保存工具 ID 列表到会话
     *
     * @param sessionId 会话ID
     * @param toolIds   工具 ID 列表
     */
    private void saveEnabledToolIdsToSession(Long sessionId, List<Long> toolIds) {
        AiSession update = new AiSession();
        update.setId(sessionId);
        update.setEnabledToolIds(toolIds.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(",")));
        aiSessionMapper.updateById(update);
    }

    /**
     * 解析逗号分隔的工具 ID 字符串
     *
     * @param toolIdsStr 逗号分隔的 ID 字符串
     * @return 工具 ID 列表
     */
    private List<Long> parseToolIds(String toolIdsStr) {
        if (toolIdsStr == null || toolIdsStr.isBlank()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String part : toolIdsStr.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                log.warn("[AI] 解析工具 ID 失败: {}", part);
            }
        }
        return ids.isEmpty() ? null : ids;
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
