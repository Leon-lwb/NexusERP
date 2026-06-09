package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.ai.tool.ExecuteSqlQueryTool;
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
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 服务门面
 * <p>统一入口：路由普通对话 / RAG / Agent 编排</p>
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
    private final QueryAllTablesTool queryAllTablesTool;
    private final QueryTableSchemaTool queryTableSchemaTool;
    private final ExecuteSqlQueryTool executeSqlQueryTool;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /**
     * 聊天入口
     * <p>
     * 1. 获取模型配置 → 2. 创建会话 → 3. 保存消息 → 4. 构建 Agent → 5. 异步执行 → 6. SSE 推送结果
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

        // 2. 创建或复用会话（tenantId 由多租户插件自动填充，不要手动设置）
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

        // 4. 构建 ChatModel
        ChatModel chatModel = chatModelFactory.getChatModel(modelConfig.getId());

        // 5. 构建 ERP 查询 Agent（带 ChatMemory 保持多轮工具调用上下文）
        ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(40))
            .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool)
            .build();

        // 6. 构建 Supervisor（管理多个子 Agent）
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(queryAgent)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .maxAgentsInvocations(10)
            .build();

        // 7. 捕获请求上下文（Sa-Token 的 ThreadLocal 不会传播到异步线程）
        Long tenantId = LoginHelper.getTenantId() != null ? Long.parseLong(LoginHelper.getTenantId()) : null;
        final Long finalSessionId = sessionId;

        // 8. 发送"思考中"状态
        AiSseMessageHelper.sendThinking(userId);

        // 9. 异步执行 + SSE 推送结果（使用专用线程池）
        CompletableFuture.runAsync(() -> {
            try {
                // 在异步线程中设置请求上下文
                AiRequestContext.set(tenantId, userId);
                String result = supervisor.invoke(request.getContent());

                // 保存 AI 回复
                AiMessage aiMsg = new AiMessage();
                aiMsg.setSessionId(finalSessionId);
                aiMsg.setRole("ASSISTANT");
                aiMsg.setContent(result);
                aiMessageMapper.insert(aiMsg);

                AiSseMessageHelper.sendContent(userId, result);
                AiSseMessageHelper.sendDone(userId);
            } catch (Exception e) {
                log.error("AI 对话失败", e);
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            } finally {
                AiRequestContext.clear();
            }
        }, aiExecutor);

        return sessionId;
    }
}
