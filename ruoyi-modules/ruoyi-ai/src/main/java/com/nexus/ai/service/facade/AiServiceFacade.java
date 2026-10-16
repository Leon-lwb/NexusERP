package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.agent.McpDocumentAgent;
import com.nexus.ai.agent.WebSearchAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.context.SearchSourceHolder;
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
import com.nexus.ai.service.sse.AiChatSseLifecycle;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.ai.tool.WebSearchTool;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.ai.domain.entity.AiTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.IAiTokenUsageService;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.service.tool.ToolProviderRequest;
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
 * <p>Agent 模式支持流式推送：通过 OutputChannel 跨线程事件总线 + SSE 实时推送工具调用过程</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceFacade {

    /** ChatModel 工厂，构建并缓存流式与 Agent 用 ChatModel */
    private final ChatModelFactory chatModelFactory;
    /** 模型配置服务，解析用户指定或默认模型 */
    private final IAiModelService aiModelService;
    /** 会话 Mapper，创建/读取会话及工具配置 */
    private final AiSessionMapper aiSessionMapper;
    /** 消息 Mapper，持久化用户与 AI 消息 */
    private final AiMessageMapper aiMessageMapper;
    /** 持久化对话记忆存储，加载历史消息 */
    private final PersistentChatMemoryStore persistentChatMemoryStore;
    /** 工具提供者工厂，按分类/ID 加载内置与 MCP 工具 */
    private final ToolProviderFactory toolProviderFactory;
    /** RAG 检索服务，增强提示词 */
    private final RetrieverService retrieverService;
    /** Agent 监听器，记录 Agent 生命周期事件 */
    private final AiAgentListener aiAgentListener;
    /** Prompt 模板渲染服务，注入动态上下文 */
    private final PromptTemplateService promptTemplateService;
    /** Token 用量服务，计算成本与日统计 */
    private final IAiTokenUsageService aiTokenUsageService;

    @Qualifier("aiExecutor")
    /** AI 异步执行线程池（限流保护大模型并发调用） */
    private final Executor aiExecutor;

    /** 联网搜索工具，供 WebSearchAgent 调用 */
    private final WebSearchTool webSearchTool;

    /** 只读查询当前租户所选工具身份；资料能力只识别本次已审部署，不改写工具权限。 */
    private final AiToolMapper aiToolMapper;

    /** 工程功能显式启用时才装配；普通路由无需工程表或私有桶。 */
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.nexus.ai.engineering.service.EngineeringAgentFactory engineeringAgentFactory;

    /** OutputChannel 事件 JSON 解析复用单例（ObjectMapper 线程安全，避免每请求 new 产生 GC 压力） */
    private static final ObjectMapper DRAIN_MAPPER = new ObjectMapper();

    /**
     * 数据查询强意图词（包含任一即走 Agent 模式）。
     * <p>仅收录查询/统计强意图词与 ERP 专属业务名词（BOM/工单等词日常对话几乎不出现），
     * 避免普通知识问答误入 Agent 模式；
     * 泛化业务词（订单/库存/采购等）移入 {@link #DATA_QUERY_COMBO_KEYWORDS} 组合规则。</p>
     */
    private static final Set<String> DATA_QUERY_KEYWORDS = Set.of(
        // 查询/统计强意图词
        "查询", "统计", "报表", "图表", "排行", "占比",
        // ERP 专属名词（日常知识问答不出现，单独命中即强意图）
        "BOM", "工单", "补货", "入库", "出库",
        // APS 排产/缺料风险专属名词（智能排产 Agent 场景）
        "排产", "拖期", "交期", "瓶颈", "缺料", "APS"
    );

    /**
     * 数据查询泛化业务词（需与 {@link #DATA_QUERY_INTENT_KEYWORDS} 至少一个意图词组合命中才走 Agent 模式）。
     * <p>"订单""库存""客户"等词单独出现时无法区分知识问答与数据查询
     * （如"什么是安全库存"），组合命中可将误路由率显著降低。</p>
     */
    private static final Set<String> DATA_QUERY_COMBO_KEYWORDS = Set.of(
        "订单", "库存", "采购", "销售", "生产", "供应商", "客户", "物料",
        "退货", "仓库", "金额"
    );

    /** 数据查询意图词（与组合业务词搭配判定，如"订单金额多少""库存数量统计"） */
    private static final Set<String> DATA_QUERY_INTENT_KEYWORDS = Set.of(
        "数量", "多少", "几个", "趋势", "分布", "对比", "分析", "总", "数据"
    );

    /** 知识库检索关键词（包含这些词时走 RAG 增强模式） */
    private static final Set<String> RAG_KEYWORDS = Set.of(
        "如何", "怎么", "操作", "流程", "步骤", "手册", "指南", "规则",
        "制度", "规范", "说明", "文档", "教程", "方法", "什么是", "定义",
        "区别", "比较", "配置", "设置", "使用", "创建", "审批"
    );

    /** 联网搜索关键词（包含这些词时走联网搜索模式，仅 auto 模式生效） */
    private static final Set<String> WEB_SEARCH_KEYWORDS = Set.of(
        "最新", "今天", "新闻", "股价", "天气", "汇率", "近期", "最近", "实时"
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
        String tenantIdStr = LoginHelper.getTenantId();

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
        // 打印实际解析到的模型配置，便于排查"切换模型是否生效"：apiHost 即实际调用 endpoint
        log.info("[AI对话] 模型解析 modelId={}, modelName={}, providerType={}, apiHost={}, mode={}, thinking={}, webSearch={}",
            modelConfig.getId(), modelConfig.getModelName(), modelConfig.getProviderType(), modelConfig.getApiHost(),
            request.getMode(), request.getEnableThinking(), request.getEnableWebSearch());

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
        } else {
            // 越权防护：复用会话时必须校验会话归属当前用户，防止向他人会话注入消息并窃取历史上下文
            AiSession existing = aiSessionMapper.selectById(sessionId);
            if (existing == null || !userId.equals(existing.getUserId())
                || !java.util.Objects.equals(tenantIdStr, existing.getTenantId())) {
                throw new ServiceException("会话不存在或无权访问该会话", 404);
            }
        }

        // 3. 保存用户消息
        AiMessage userMsg = new AiMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(request.getContent());
        aiMessageMapper.insert(userMsg);

        // 4. 捕获请求上下文（tenantIdStr 透传给异步路径，TenantHelper.dynamic 恢复，避免 ai_message insert 时 tenant_id 落 0）
        // 租户是文本身份，异步上下文与数据库条件均保留原始前导零。
        String tenantId = tenantIdStr;
        String userName = null;
        try {
            userName = LoginHelper.getUsername();
        } catch (Exception e) {
            log.debug("获取用户名称失败: {}", e.getMessage());
        }
        final Long finalSessionId = sessionId;
        final String finalUserName = userName;

        // 5. 智能路由：显式行为开关优先，其次前端显式指定模式，最后关键词自动判断

        // 工程只在显式设备与工程动作意图共同成立时进入独立角色；普通库存/MCP/RAG路由沿用原语义。
        if (engineeringAgentFactory != null && engineeringAgentFactory.requested(request)) {
            invokeWithAgent(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
            return sessionId;
        }

        // 5a. 显式开关优先（独立于 mode，用户主动开启时覆盖路由）
        if (Boolean.TRUE.equals(request.getEnableWebSearch())) {
            // 深度思考开启时改走"先检索后流式"：Agent 路径同步一次性返回，无法流式输出与展示思考过程
            if (Boolean.TRUE.equals(request.getEnableThinking())) {
                invokeWithWebSearchStreaming(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
            } else {
                invokeWithWebSearch(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
            }
            return sessionId;
        }

        // 5b. 深度思考开关优先：Agent 路径是同步一次性返回，无法流式输出思考过程；
        // 用户显式开启 enableThinking 期待流式思考展示，因此强制走流式路径（显式 agent/rag 模式仍按模式路由）
        boolean forceStreaming = Boolean.TRUE.equals(request.getEnableThinking());
        if (forceStreaming && !("agent".equalsIgnoreCase(request.getMode()) || "rag".equalsIgnoreCase(request.getMode()))) {
            invokeWithStreaming(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
            return sessionId;
        }

        // 5c. 模式路由
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
            // auto 模式（默认）：数据查询意图优先于联网搜索判定。
            // 联网搜索词（"最新""近期"等）常与 ERP 查询共存（如"分析最新排产方案"），
            // 若先判联网搜索会把本应走 Agent 工具链的问题错误送入纯 webSearch 路径。
            needAgent = containsDataQueryKeywords(request.getContent());
            if (!needAgent && containsWebSearchKeywords(request.getContent())) {
                invokeWithWebSearch(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
                return sessionId;
            }
            needRag = request.getKnowledgeId() != null || (!needAgent && containsRagKeywords(request.getContent()));
        }

        if (needAgent) {
            invokeWithAgent(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
        } else if (needRag) {
            invokeWithRag(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
        } else {
            invokeWithStreaming(modelConfig, request, userId, finalSessionId, tenantId, finalUserName, tenantIdStr);
        }

        return sessionId;
    }

    /**
     * 普通流式对话：StreamingChatModel → 逐 Token 推送
     */
    private void invokeWithStreaming(AiModel modelConfig, AiChatRequest request,
                                     Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr) {
        invokeWithStreaming(modelConfig, request, userId, sessionId, tenantId, userName, tenantIdStr,
            new AiChatSseLifecycle(userId, sessionId));
    }

    private void invokeWithStreaming(AiModel modelConfig, AiChatRequest request,
                                     Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr,
                                     AiChatSseLifecycle lifecycle) {
        try {
            StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId(), request);
            MessageWindowChatMemory memory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(20)
                .chatMemoryStore(persistentChatMemoryStore).build();
            List<ChatMessage> allMessages = new ArrayList<>(memory.messages());
            injectContextIntoLastUserMessage(request, allMessages);
            streamResponse(streamingModel, allMessages, modelConfig, userId, sessionId, tenantId, userName, tenantIdStr, lifecycle);
        } catch (Exception error) {
            lifecycle.fail(error);
        }
    }

    /** 知识库增强与普通对话共用流式回调及唯一终态，避免收尾行为分叉。 */
    private void invokeWithRag(AiModel modelConfig, AiChatRequest request,
                               Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr) {
        AiChatSseLifecycle lifecycle = new AiChatSseLifecycle(userId, sessionId);
        try {
            StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelConfig.getId(), request);
            MessageWindowChatMemory memory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(20)
                .chatMemoryStore(persistentChatMemoryStore).build();
            List<ChatMessage> allMessages = new ArrayList<>(memory.messages());
            String augmentedQuery = request.getKnowledgeId() != null
                ? retrieverService.augmentPrompt(request.getKnowledgeId(), request.getContent())
                : retrieverService.augmentPrompt(request.getContent());
            boolean replaced = false;
            String injectedQuery = buildInjectedPrompt(request, augmentedQuery);
            for (int i = allMessages.size() - 1; i >= 0; i--) {
                if (allMessages.get(i) instanceof dev.langchain4j.data.message.UserMessage) {
                    allMessages.set(i, dev.langchain4j.data.message.UserMessage.from(injectedQuery));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) allMessages.add(dev.langchain4j.data.message.UserMessage.from(injectedQuery));
            streamResponse(streamingModel, allMessages, modelConfig, userId, sessionId, tenantId, userName, tenantIdStr, lifecycle);
        } catch (Exception error) {
            lifecycle.fail(error);
        }
    }

    /** 模型回调的租户身份从请求闭包恢复，不读取回调线程的登录上下文。 */
    private void streamResponse(StreamingChatModel streamingModel, List<ChatMessage> allMessages, AiModel modelConfig,
                                Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr,
                                AiChatSseLifecycle lifecycle) {
        lifecycle.progress(() -> AiSseMessageHelper.sendThinking(userId));
        StringBuffer fullContent = new StringBuffer();
        StringBuffer fullThinking = new StringBuffer();
        submit(lifecycle, () -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId, modelConfig.getId(), modelConfig.getModelName());
                streamingModel.chat(ChatRequest.builder().messages(allMessages).build(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        lifecycle.progress(() -> {
                            fullContent.append(token);
                            AiSseMessageHelper.sendContentIncrement(userId, token);
                        });
                    }

                    @Override
                    public void onPartialThinking(dev.langchain4j.model.chat.response.PartialThinking thinking) {
                        if (thinking == null || thinking.text() == null) return;
                        lifecycle.progress(() -> {
                            fullThinking.append(thinking.text());
                            AiSseMessageHelper.sendThinkingToken(userId, thinking.text());
                        });
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        lifecycle.complete(() -> saveAiMessageWithTokens(sessionId, fullContent.toString(), modelConfig.getId(),
                            response, tenantId, userId, userName, modelConfig.getModelName(),
                            fullThinking.isEmpty() ? null : fullThinking.toString(), tenantIdStr));
                    }

                    @Override
                    public void onError(Throwable error) { lifecycle.fail(error); }
                });
            } catch (Exception error) {
                lifecycle.fail(error);
            } finally {
                AiRequestContext.clear();
            }
        });
    }

    /** 提交失败也通过同一生命周期收尾，避免线程池拒绝后客户端永久等待。 */
    private boolean submit(AiChatSseLifecycle lifecycle, Runnable task) {
        try {
            CompletableFuture.runAsync(task, aiExecutor);
            return true;
        } catch (Exception error) {
            lifecycle.fail(error);
            return false;
        }
    }

    /**
     * Agent 编排模式：SupervisorAgent + 同步 ChatModel → 流式推送结果
     * <p>
     * 使用 OutputChannel 跨线程事件总线，将 Agent 执行过程中的工具调用事件推送到前端。
     * SubAgent 不使用持久化记忆，避免工具调用中间结果膨胀上下文导致 Agent 混乱。
     * </p>
     */
    private void invokeWithAgent(AiModel modelConfig, AiChatRequest request,
                                 Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr) {
        AiChatSseLifecycle lifecycle = new AiChatSseLifecycle(userId, sessionId);
        try {
            runAgent(modelConfig, request, userId, sessionId, tenantId, userName, tenantIdStr, lifecycle);
        } catch (Exception error) {
            lifecycle.fail(error);
        }
    }

    private void runAgent(AiModel modelConfig, AiChatRequest request,
                          Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr,
                          AiChatSseLifecycle lifecycle) {
        final com.nexus.ai.engineering.service.EngineeringToolContext.Context engineeringContext =
            engineeringAgentFactory == null ? null : engineeringAgentFactory.capture(request, userId, sessionId, tenantId);

        // Agent 模式使用低 temperature（0.1），提高 SQL 生成准确性
        ChatModel chatModel = chatModelFactory.getAgentChatModel(modelConfig.getId());

        // 加载历史消息，构建带上下文的查询（避免多轮对话丢失上下文）
        List<ChatMessage> history = persistentChatMemoryStore.getMessages(sessionId);
        String contextualQuery = buildContextualQuery(history, request.getContent());

        // 动态加载已启用的工具（差异化注入：QueryAgent 需要 QUERY+ALL，ReportAgent 只需要 ALL）
        List<Object> queryTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("QUERY,ALL");
        List<Object> reportTools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories("ALL");

        // 会话级 MCP 工具选择：优先使用请求中的 enabledToolIds，其次使用会话保存的配置
        McpDocumentCapability mcpCapability = resolveMcpDocumentCapability(request, sessionId);

        // 注入 PromptTemplateService 渲染的动态上下文
        // 由于 LangChain4j Supervisor 模式下 SubAgent 的 @UserMessage 由框架填充，
        // 无法直接为 SubAgent 注入独立上下文，因此将所有上下文合并到 Supervisor 层，
        // 由 Supervisor 在分发时将关键信息传递给 SubAgent
        String queryAgentContext = promptTemplateService.renderQueryAgentContext();
        String reportAgentContext = promptTemplateService.renderReportAgentContext();
        String supervisorContext = promptTemplateService.renderSupervisorContext();

        // 构建增强查询：将 Supervisor 上下文 + SubAgent 上下文 + 业务上下文 + 原始查询合并
        // Supervisor 会将完整上下文连同用户问题一起分发给对应的 SubAgent
        StringBuilder enhancedSb = new StringBuilder();
        enhancedSb.append(supervisorContext).append("\n\n")
            .append("【数据查询助手上下文】\n").append(queryAgentContext).append("\n\n")
            .append("【报表生成助手上下文】\n").append(reportAgentContext).append("\n\n");
        // 业务上下文（移动端从详情页带入）作为独立段注入，Supervisor 会将其分发给 SubAgent，
        // 使 Agent 可直接引用单据号/状态调用 ERP 工具
        String bizCtx = request.getContext();
        if (bizCtx != null && !bizCtx.isBlank()) {
            enhancedSb.append("【业务上下文】\n").append(truncateContext(bizCtx)).append("\n\n");
        }
        enhancedSb.append("【用户问题】\n").append(contextualQuery);
        // SubAgent 不使用持久化记忆，避免工具调用中间结果膨胀上下文
        boolean directBulkQuery = engineeringContext == null && isDirectBulkDataQuery(request.getContent());
        ChatModel queryModel = isPureDataQuery(request.getContent()) ? new CompleteAnswerChatModel(chatModel,
            chatModelFactory.getStreamingChatModel(modelConfig.getId(), request),
            token -> lifecycle.progress(() -> AiSseMessageHelper.sendContentIncrement(userId, token)),
            token -> lifecycle.progress(() -> AiSseMessageHelper.sendThinkingToken(userId, token))) : chatModel;
        var queryAgentBuilder = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(queryModel);
        if (!queryTools.isEmpty()) {
            queryAgentBuilder.tools(queryTools.toArray());
        }
        ErpQueryAgent queryAgent = queryAgentBuilder.build();

        // 构建 ERP 报表 Agent（同样不使用持久化记忆）
        var reportAgentBuilder = AgenticServices.agentBuilder(ErpReportAgent.class)
            .chatModel(chatModel);
        if (!reportTools.isEmpty()) {
            reportAgentBuilder.tools(reportTools.toArray());
        }
        ErpReportAgent reportAgent = reportAgentBuilder.build();

        // 构建 Supervisor。外部 MCP 工具只挂独立角色，不注入 ERP 查询/报表 Agent。
        // 泛化 Provider 不自称只读资料；只有选中已审资料服务且有资料意图时才启用资料过滤与证据闸门。
        List<Object> subAgents = new ArrayList<>();
        subAgents.add(queryAgent);
        subAgents.add(reportAgent);
        if (!directBulkQuery && mcpCapability.hasTools()) {
            McpDocumentAgent documentAgent = AgenticServices.agentBuilder(McpDocumentAgent.class)
                .chatModel(chatModel)
                .toolProvider(mcpCapability.provider())
                .description(mcpCapability.description())
                .systemMessage(mcpCapability.instructions())
                .maxToolCallingRoundTrips(8)
                .build();
            subAgents.add(documentAgent);
            enhancedSb.insert(0, "【当前请求外部工具能力】\n" + mcpCapability.description() + "\n"
                + mcpCapability.instructions() + "\n\n");
        }
        if (engineeringContext != null) {
            subAgents.add(engineeringAgentFactory.build(chatModel, engineeringContext));
            enhancedSb.insert(0, "【受控工程能力】本次请求已绑定设备，只能由工程角色调用easyeda_具名任务工具。工具返回任务派发回执，不能宣称编辑器已经完成设计。\n\n");
        }
        final String enhancedQuery = enhancedSb.toString();
        // 增大 maxAgentsInvocations，避免复杂查询耗尽迭代次数。
        SupervisorAgent supervisor = directBulkQuery ? null : AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(subAgents)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .maxAgentsInvocations(20)
            .build();

        String executionAgentName = directBulkQuery ? "ErpQueryAgent" : "SupervisorAgent";
        AiSseMessageHelper.sendThinking(userId);

        // 创建 OutputChannel 用于流式推送
        String requestId = lifecycle.requestId();
        OutputChannel channel = OutputChannel.create(requestId);

        if (!submit(lifecycle, () -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());
                // 设置当前知识库ID，供 KnowledgeGraphTool 限定图谱范围
                AiRequestContext.setKnowledgeId(request.getKnowledgeId());
                com.nexus.ai.engineering.service.EngineeringToolContext.set(engineeringContext);
                // 设置 ThreadLocal OutputChannel，使 AiToolExecutedListener 能推送事件
                AiToolExecutedListener.setChannel(channel);

                long startTime = System.currentTimeMillis();
                aiAgentListener.onAgentStart(executionAgentName, enhancedQuery);

                // 推送 Agent 开始思考
                AgentStreamHelper.sendThinking(channel, executionAgentName, "开始分析您的问题...");

                String rawResult = directBulkQuery ? queryAgent.query(enhancedQuery) : supervisor.invoke(enhancedQuery);
                // 资料证据闸门继续校验真实模型结果，工程角色不能绕过既有MCP读取证据。
                mcpCapability.requireSuccessfulResult(rawResult);
                final String result = engineeringContext == null ? rawResult
                    : engineeringAgentFactory.verifiedResult(engineeringContext, rawResult);

                long duration = System.currentTimeMillis() - startTime;
                aiAgentListener.onAgentComplete(executionAgentName, duration, 0);

                if (lifecycle.complete(() -> {
                    saveAiMessageFromContext(sessionId, result, tenantId, userId, userName, modelConfig.getModelName(), tenantIdStr);
                    AiSseMessageHelper.sendContent(userId, result);
                })) {
                    channel.complete();
                } else {
                    channel.completeWithError(lifecycle.failure());
                }
            } catch (Exception e) {
                lifecycle.fail(e);
                aiAgentListener.onAgentError(executionAgentName, AiChatSseLifecycle.ERROR_MESSAGE);
                channel.completeWithError(e);
            } finally {
                AiToolExecutedListener.clearChannel();
                com.nexus.ai.engineering.service.EngineeringToolContext.clear();
                AiRequestContext.clear();
                OutputChannel.remove(requestId);
            }
        })) {
            channel.completeWithError(lifecycle.failure());
            OutputChannel.remove(requestId);
            return;
        }

        // 异步消费 OutputChannel 事件，根据事件类型路由到对应的 SSE 推送方法
        if (!submit(lifecycle, () -> {
            try {
                channel.drain(eventJson -> {
                    try {
                        JsonNode node = DRAIN_MAPPER.readTree(eventJson);
                        String eventType = node.has("type") ? node.get("type").asText() : "";

                        switch (eventType) {
                            case "TOOL_START" -> {
                                String toolName = node.has("toolName") ? node.get("toolName").asText() : "unknown";
                                String args = node.has("arguments") ? node.get("arguments").asText() : "";
                                lifecycle.progress(() -> AiSseMessageHelper.sendToolStart(userId, toolName, args));
                            }
                            case "TOOL_END" -> {
                                String toolName = node.has("toolName") ? node.get("toolName").asText() : "unknown";
                                long dur = node.has("duration") ? node.get("duration").asLong() : 0;
                                String summary = node.has("resultSummary") ? node.get("resultSummary").asText() : "";
                                lifecycle.progress(() -> AiSseMessageHelper.sendToolEnd(userId, toolName, dur, summary));
                            }
                            case "THINKING" -> {
                                String agentName = node.has("agentName") ? node.get("agentName").asText() : "Agent";
                                String message = node.has("message") ? node.get("message").asText() : "";
                                lifecycle.progress(() -> AiSseMessageHelper.sendAgentThinking(userId, agentName, message));
                            }
                            case "ERROR" -> {
                                String errorMsg = node.has("error") ? node.get("error").asText() : "未知错误";
                                lifecycle.fail(new IllegalStateException(errorMsg));
                            }
                            default -> log.warn("[Agent] 未知 OutputChannel 事件类型: {}", eventType);
                        }
                    } catch (Exception e) {
                        log.warn("[Agent] 解析事件失败 requestId={}", requestId, e);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Agent] OutputChannel drain 被中断");
            } catch (Exception e) {
                log.debug("[Agent] 事件通道收尾 requestId={}", requestId, e);
            }
        })) {
            channel.completeWithError(lifecycle.failure());
            OutputChannel.remove(requestId);
        }
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
                                           String tenantId, Long userId,
                                           String userName, String modelName, String tenantIdStr) {
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

        // 异步线程（Agent 闭包）恢复租户上下文，避免 ai_message insert 时 tenant_id 落 0
        TenantHelper.dynamic(tenantIdStr, () -> {
            aiMessageMapper.insert(aiMsg);
        });
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
     * @param thinkingContent 推理过程内容（可为 null）
     */
    private void saveAiMessageWithTokens(Long sessionId, String content, Long modelId,
                                          ChatResponse response, String tenantId, Long userId,
                                          String userName, String modelName, String thinkingContent, String tenantIdStr) {
        AiMessage aiMsg = new AiMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(content);
        aiMsg.setThinkingContent(thinkingContent);
        aiMsg.setModelId(modelId);

        // 提取 Token 用量
        int inputTokens = 0;
        int outputTokens = 0;
        if (response != null && response.tokenUsage() != null) {
            inputTokens = response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0;
            outputTokens = response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0;
            aiMsg.setInputTokens(inputTokens);
            aiMsg.setOutputTokens(outputTokens);
            aiMsg.setTokenUsage((long) (inputTokens + outputTokens));
        }

        // 异步线程（流式回调 I/O 线程）恢复租户上下文，避免 ai_message insert 时 tenant_id 落 0
        TenantHelper.dynamic(tenantIdStr, () -> {
            aiMessageMapper.insert(aiMsg);
        });

        // 流式/RAG 路径日统计：listener.onResponse 在 OkHttp I/O 线程读不到 AiRequestContext（InheritableThreadLocal
        // 不跨线程池），日统计不会被写入；此处已持有闭包上下文 + token 用量，补写日统计。
        // Agent 路径走 saveAiMessageFromContext，listener 在 aiExecutor 线程同步执行已写日统计，不双计。
        if (userId != null && modelId != null && (inputTokens > 0 || outputTokens > 0)) {
            try {
                java.math.BigDecimal cost = aiTokenUsageService.calculateCost(modelId, inputTokens, outputTokens);
                aiTokenUsageService.incrementDailyUsage(tenantId, userId,
                    userName != null ? userName : String.valueOf(userId),
                    modelId, modelName != null ? modelName : "unknown",
                    inputTokens, outputTokens, cost);
            } catch (Exception e) {
                log.warn("[流式对话] 更新日统计失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 解析会话级 MCP 工具选择
     * <p>优先级：请求中的 enabledToolIds > 会话保存的 enabledToolIds > 全部已启用工具</p>
     * <p>首次设置后保存到会话，后续对话自动复用</p>
     *
     * @param request   聊天请求
     * @param sessionId 会话ID
     * @return 本请求的工具包装与结果闸门，不以 Provider 存在宣称只读或连接成功
     */
    private McpDocumentCapability resolveMcpDocumentCapability(AiChatRequest request, Long sessionId) {
        List<Long> toolIds = resolveMcpToolIds(request, sessionId);
        List<Long> auditedDocumentIds = new ArrayList<>();
        if (McpDocumentCapability.isDocumentQuestion(request.getContent())) {
            if (toolIds == null) {
                // 缺省“全部启用”也是本请求实际工具集，不能绕过资料身份识别与真实读取闸门。
                List<AiTool> enabled = aiToolMapper.selectList(new LambdaQueryWrapper<AiTool>()
                    .eq(AiTool::getStatus, "0"));
                if (enabled != null) {
                    for (AiTool stored : enabled) {
                        if (McpDocumentCapability.isAuditedDocumentService(stored)) auditedDocumentIds.add(stored.getId());
                    }
                }
            } else {
                for (Long id : toolIds) {
                    // Mapper 保留当前租户可见性；精确已审名称与端点共同识别，不能只凭工具名/prompt 自称只读。
                    if (McpDocumentCapability.isAuditedDocumentService(aiToolMapper.selectById(id))) {
                        auditedDocumentIds.add(id);
                    }
                }
            }
        }
        boolean documentRequest = !auditedDocumentIds.isEmpty();
        ToolProvider source;
        if (documentRequest) {
            source = toolProviderFactory.getMcpToolProvidersByIds(auditedDocumentIds);
        } else if (toolIds != null) {
            source = toolIds.isEmpty() ? null : toolProviderFactory.getMcpToolProvidersByIds(toolIds);
        } else {
            source = toolProviderFactory.getAllEnabledMcpToolProviders();
        }
        ToolProviderRequest discoveryRequest = new ToolProviderRequest(sessionId,
            dev.langchain4j.data.message.UserMessage.from(request.getContent()));
        return McpDocumentCapability.prepare(source, discoveryRequest, documentRequest);
    }

    private List<Long> resolveMcpToolIds(AiChatRequest request, Long sessionId) {
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

        return toolIds;
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
     * <p>强意图词（查询/统计/BOM...）单独命中即路由；泛化业务词（订单/库存...）
     * 需与意图词（数量/多少/分析...）组合命中才路由，避免普通知识问答误入 Agent 模式。
     * 包级静态便于单测直接验证路由规则。</p>
     *
     * @param input 用户输入
     * @return 是否需要走 Agent 模式
     */
    static boolean containsDataQueryKeywords(String input) {
        return containsKeywords(input, DATA_QUERY_KEYWORDS)
            || (containsKeywords(input, DATA_QUERY_COMBO_KEYWORDS)
                && containsKeywords(input, DATA_QUERY_INTENT_KEYWORDS));
    }

    /** 纯 ERP 查询可推送查询 Agent 的真实正文；资料证据和组合工具任务仍保留原协调边界。 */
    static boolean isPureDataQuery(String input) {
        if (!containsDataQueryKeywords(input) || McpDocumentCapability.isDocumentQuestion(input)) return false;
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        return Set.of("报表", "图表", "柱状", "饼图", "折线", "可视化", "echarts", "mcp", "工具", "联网", "知识库")
            .stream().noneMatch(lower::contains);
    }

    /** 纯批量业务明细直接由查询 Agent 交付，避免长正文再次进入调度 JSON。 */
    static boolean isDirectBulkDataQuery(String input) {
        if (!isPureDataQuery(input)) return false;
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        return Set.of("全部", "所有", "全量", "逐条", "每条", "批量", "完整列出")
            .stream().anyMatch(lower::contains);
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
    private static boolean containsKeywords(String input, Set<String> keywords) {
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

    /**
     * 判断是否包含联网搜索关键词
     */
    private boolean containsWebSearchKeywords(String input) {
        return containsKeywords(input, WEB_SEARCH_KEYWORDS);
    }

    /**
     * 联网搜索 + 流式回答（enableWebSearch 与 enableThinking 同时开启时）。
     * <p>Agent 路径（{@link #invokeWithWebSearch}）是同步一次性返回，无法流式输出与展示思考过程；
     * 本路径改为先同步调用 {@link WebSearchTool#webSearch} 纯 HTTP 检索（无 LLM 参与），
     * 将搜索结果注入提示词后走流式生成，检索增强与思考流式展示两者兼得。
     * 检索来源经 SearchSourceHolder 收集后仍走来源卡片推送；检索失败不阻断对话，降级为普通流式回答。</p>
     */
    private void invokeWithWebSearchStreaming(AiModel modelConfig, AiChatRequest request,
                                              Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr) {
        AiChatSseLifecycle lifecycle = new AiChatSseLifecycle(userId, sessionId);
        lifecycle.progress(() -> AiSseMessageHelper.sendThinking(userId));

        submit(lifecycle, () -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());

                // 1. 同步检索（纯 HTTP 调 Tavily/Bing，无 LLM；期间收集来源到 SearchSourceHolder）
                String searchResult;
                try {
                    searchResult = webSearchTool.webSearch(request.getContent());
                } catch (Exception e) {
                    log.warn("[AI] 联网搜索检索失败，降级为普通流式回答 requestId={}", lifecycle.requestId(), e);
                    searchResult = null;
                }

                // 2. 构造增强提示词：业务上下文 + 检索结果 + 原始问题（与 buildInjectedPrompt 的结构保持一致）
                String enhancedContent;
                if (searchResult != null && !searchResult.contains("联网搜索未配置或未启用") && !searchResult.contains("搜索失败")) {
                    log.info("[AI] 联网搜索完成，检索结果长度={}字符，注入增强提示词后流式生成", searchResult.length());
                    pushWebSearchSources(userId);
                    enhancedContent = "以下是与用户问题相关的联网搜索结果（供参考，回答时请注明信息来源链接）：\n\n"
                        + searchResult + "\n\n【用户问题】\n" + request.getContent();
                } else {
                    // 未配置 Key 或检索失败时降级，提示词保持原样（模型会自行说明无法联网）
                    log.info("[AI] 联网搜索不可用（{}），降级为普通流式回答", searchResult);
                    enhancedContent = request.getContent();
                }

                // 3. 替换请求 content 为增强提示词（业务上下文经 invokeWithStreaming 内的
                // injectContextIntoLastUserMessage 继续注入），走流式生成
                AiChatRequest enhancedRequest = new AiChatRequest();
                enhancedRequest.setContent(enhancedContent);
                enhancedRequest.setContext(request.getContext());
                enhancedRequest.setEnableThinking(request.getEnableThinking());
                invokeWithStreaming(modelConfig, enhancedRequest, userId, sessionId, tenantId, userName, tenantIdStr, lifecycle);
            } catch (Exception e) {
                lifecycle.fail(e);
            } finally {
                SearchSourceHolder.clear();
                // invokeWithStreaming 异步派发后即可清理（上下文已在其闭包内重新设置）
                AiRequestContext.clear();
            }
        });
    }

    /**
     * 联网搜索模式：WebSearchAgent + WebSearchTool → SSE 推送结果
     */
    private void invokeWithWebSearch(AiModel modelConfig, AiChatRequest request,
                                      Long userId, Long sessionId, String tenantId, String userName, String tenantIdStr) {
        AiChatSseLifecycle lifecycle = new AiChatSseLifecycle(userId, sessionId);
        submit(lifecycle, () -> {
            try {
                AiRequestContext.set(tenantId, userId, userName, sessionId,
                    modelConfig.getId(), modelConfig.getModelName());
                AiSseMessageHelper.sendThinking(userId);

                ChatModel agentModel = chatModelFactory.getAgentChatModel(modelConfig.getId());
                WebSearchAgent agent = AgenticServices.agentBuilder(WebSearchAgent.class)
                    .chatModel(agentModel)
                    .tools(webSearchTool)
                    .build();

                String result = agent.search(buildInjectedPrompt(request, request.getContent()));

                // 推送搜索来源卡片（从 SearchSourceHolder 取出本次搜索的来源）
                pushWebSearchSources(userId);

                lifecycle.complete(() -> {
                    saveAiMessageFromContext(sessionId, result, tenantId, userId, userName, modelConfig.getModelName(), tenantIdStr);
                    AiSseMessageHelper.sendContent(userId, result);
                });
            } catch (Exception e) {
                lifecycle.fail(e);
            } finally {
                SearchSourceHolder.clear();
                AiRequestContext.clear();
            }
        });
    }

    /**
     * 推送联网搜索来源卡片（AI_WEB_SEARCH 事件）
     * <p>从 {@link SearchSourceHolder} 取出本次请求收集的来源（按 url 去重），
     * 非空时通过 SSE 推送到前端渲染来源卡片。</p>
     *
     * @param userId 目标用户ID
     */
    private void pushWebSearchSources(Long userId) {
        try {
            var sources = SearchSourceHolder.drain();
            if (!sources.isEmpty()) {
                AiSseMessageHelper.sendWebSearchSources(userId, sources);
            }
        } catch (Exception e) {
            log.error("推送联网搜索来源卡片失败, userId={}", userId, e);
        }
    }

    /**
     * 业务上下文最大长度（字符）。防恶意大包与超长摘要，超长前缀截断保留尾部摘要语义。
     */
    private static final int CONTEXT_MAX_LEN = 2000;

    /**
     * 截断业务上下文至 {@link #CONTEXT_MAX_LEN} 字符
     *
     * @param ctx 原始业务上下文
     * @return 截断后的上下文
     */
    private String truncateContext(String ctx) {
        if (ctx.length() <= CONTEXT_MAX_LEN) {
            return ctx;
        }
        return ctx.substring(0, CONTEXT_MAX_LEN) + "…";
    }

    /**
     * 构建注入业务上下文的用户问题
     * <p>context 非空时拼为「【业务上下文】…【用户问题】…」结构注入 prompt；
     * 为空时原样返回 query。context 仅参与 prompt 拼接，不参与关键词路由判断。</p>
     *
     * @param request 聊天请求（含 context）
     * @param query   原始/增强后的用户问题
     * @return 注入后的 prompt
     */
    private String buildInjectedPrompt(AiChatRequest request, String query) {
        String ctx = request.getContext();
        if (ctx == null || ctx.isBlank()) {
            return query;
        }
        return "【业务上下文】\n" + truncateContext(ctx) + "\n\n【用户问题】\n" + query;
    }

    /**
     * 将业务上下文注入消息列表中最后一条 UserMessage
     * <p>从后往前查找（避免末尾是 AiMessage 时误删，或连续多条 UserMessage 时删错），
     * 未找到则追加一条。用于 streaming/RAG 模式。</p>
     *
     * @param request     聊天请求（含 context）
     * @param allMessages 消息列表（原地修改）
     */
    private void injectContextIntoLastUserMessage(AiChatRequest request, List<ChatMessage> allMessages) {
        String ctx = request.getContext();
        if (ctx == null || ctx.isBlank()) {
            return;
        }
        boolean replaced = false;
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            if (allMessages.get(i) instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                String injected = "【业务上下文】\n" + truncateContext(ctx)
                    + "\n\n【用户问题】\n" + userMsg.singleText();
                allMessages.set(i, dev.langchain4j.data.message.UserMessage.from(injected));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            allMessages.add(dev.langchain4j.data.message.UserMessage.from(
                "【业务上下文】\n" + truncateContext(ctx) + "\n\n【用户问题】\n" + request.getContent()));
        }
    }
}
