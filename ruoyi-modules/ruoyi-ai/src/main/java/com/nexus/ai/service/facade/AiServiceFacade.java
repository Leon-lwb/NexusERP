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
            int lastIdx = allMessages.size() - 1;
            ChatMessage lastMsg = allMessages.get(lastIdx);
            if (lastMsg instanceof dev.langchain4j.data.message.UserMessage) {
                allMessages.remove(lastIdx);
            }
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
     * Agent 编排模式：SupervisorAgent + 同步 ChatModel → 流式推送结果
     * <p>
     * 使用 OutputChannel 跨线程事件总线，将 Agent 执行过程中的工具调用事件推送到前端。
     * SubAgent 不使用持久化记忆，避免工具调用中间结果膨胀上下文导致 Agent 混乱。
     * </p>
     */
    private void invokeWithAgent(AiModel modelConfig, AiChatRequest request,
                                 Long userId, Long sessionId, Long tenantId) {
        // Agent 模式使用低 temperature（0.1），提高 SQL 生成准确性
        ChatModel chatModel = chatModelFactory.getAgentChatModel(modelConfig.getId());

        // 加载历史消息，构建带上下文的查询（避免多轮对话丢失上下文）
        List<ChatMessage> history = persistentChatMemoryStore.getMessages(sessionId);
        String contextualQuery = buildContextualQuery(history, request.getContent());

        // 动态加载已启用的工具
        List<Object> builtinTools = toolProviderFactory.getAllEnabledBuiltinToolObjects();
        ToolProvider mcpToolProvider = toolProviderFactory.getAllEnabledMcpToolProviders();

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
        ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel)
            .tools(builtinTools.toArray())
            .toolProvider(mcpToolProvider)
            .build();

        // 构建 ERP 报表 Agent（同样不使用持久化记忆）
        ErpReportAgent reportAgent = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel)
            .tools(builtinTools.toArray())
            .toolProvider(mcpToolProvider)
            .build();

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
                AiRequestContext.set(tenantId, userId);
                // 设置 ThreadLocal OutputChannel，使 AiToolExecutedListener 能推送事件
                AiToolExecutedListener.setChannel(channel);

                long startTime = System.currentTimeMillis();
                aiAgentListener.onAgentStart("SupervisorAgent", enhancedQuery);

                // 推送 Agent 开始思考
                AgentStreamHelper.sendThinking(channel, "SupervisorAgent", "开始分析您的问题...");

                String result = supervisor.invoke(enhancedQuery);

                long duration = System.currentTimeMillis() - startTime;
                aiAgentListener.onAgentComplete("SupervisorAgent", duration, 0);

                // 保存 AI 回复
                saveAiMessage(sessionId, result);

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
     * <p>将最近 10 条历史消息拼接到当前问题前，每条消息截断到 200 字符以控制 Token 消耗</p>
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
        // 只取最近 10 条历史消息（约 5 轮对话），每条截断到 200 字符
        int startIdx = Math.max(0, history.size() - 11);
        for (int i = startIdx; i < history.size() - 1; i++) {
            ChatMessage msg = history.get(i);
            if (msg instanceof dev.langchain4j.data.message.UserMessage um) {
                String text = um.singleText();
                sb.append("用户：").append(text.length() > 200 ? text.substring(0, 200) + "..." : text).append("\n");
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage am) {
                String text = am.text();
                if (text != null) {
                    sb.append("AI：").append(text.length() > 200 ? text.substring(0, 200) + "..." : text).append("\n");
                }
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
