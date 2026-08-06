package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.ai.service.chat.ChatModelFactory;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 节点执行器
 * <p>复用生产验证的 AgenticServices.agentBuilder(ErpQueryAgent) 单 agent 直调路径，
 * 绑定内置工具（按 categories）与可选 MCP 工具。不自研 tool loop（MVP 决策）</p>
 *
 * @author NexusERP
 */
@Component
@RequiredArgsConstructor
public class AgentNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModelFactory chatModelFactory;
    private final ToolProviderFactory toolProviderFactory;

    @Override
    public String getNodeType() {
        return "agent";
    }

    @Override
    public String getDisplayName() {
        return "Agent 智能体";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("modelId", "模型", "number", "0", true),
            new NodeParamSchema("queryTemplate", "问题模板", "textarea", "", true),
            new NodeParamSchema("toolCategories", "工具类别", "options", "QUERY,ALL", false),
            new NodeParamSchema("mcpToolIds", "MCP 工具ID(逗号分隔)", "string", "", false),
            new NodeParamSchema("maxAgentInvocations", "最大调用次数", "number", "20", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("modelId") == null || !config.get("modelId").canConvertToLong()) {
            throw new IllegalArgumentException("Agent 节点缺少 modelId 配置");
        }
        if (config.get("queryTemplate") == null || config.get("queryTemplate").asText().isBlank()) {
            throw new IllegalArgumentException("Agent 节点缺少 queryTemplate 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        Long modelId = config.get("modelId").asLong();

        // 渲染问题模板
        String queryTemplate = config.get("queryTemplate").asText();
        String query = FlowTemplateRenderer.render(queryTemplate, ctx.getState(),
            "节点 " + ctx.getNodeName() + " 的 queryTemplate");

        // Agent 专用模型（低 temperature，提高 SQL 生成准确性）
        ChatModel chatModel = chatModelFactory.getAgentChatModel(modelId);

        // 动态加载已启用的工具（默认 QUERY+ALL）
        String categories = config.has("toolCategories") && !config.get("toolCategories").isNull()
            ? config.get("toolCategories").asText() : "QUERY,ALL";
        List<Object> tools = toolProviderFactory.getEnabledBuiltinToolObjectsByCategories(categories);

        // 构建 Agent（复用 AiServiceFacade.invokeWithAgent L451-459 的已验证路径）
        var agentBuilder = AgenticServices.agentBuilder(ErpQueryAgent.class)
            .chatModel(chatModel);
        if (!tools.isEmpty()) {
            agentBuilder.tools(tools.toArray());
        }
        ErpQueryAgent agent = agentBuilder.build();

        // 调用 Agent（同步，AiRequestContext 由引擎在流程级设置，工具内 tenant 隔离依赖它）
        String result = agent.query(query);

        // 组装输出（Token 用量从 AiRequestContext 暂存读取，由 AiChatModelListener 写入）
        ObjectNode output = MAPPER.createObjectNode();
        output.put("content", result);
        output.put("modelId", modelId);
        Integer inputTokens = AiRequestContext.getLastInputTokens();
        Integer outputTokens = AiRequestContext.getLastOutputTokens();
        if (inputTokens != null || outputTokens != null) {
            ObjectNode tokenUsage = MAPPER.createObjectNode();
            tokenUsage.put("inputTokens", inputTokens == null ? 0 : inputTokens);
            tokenUsage.put("outputTokens", outputTokens == null ? 0 : outputTokens);
            output.set("tokenUsage", tokenUsage);
        }
        return output;
    }
}
