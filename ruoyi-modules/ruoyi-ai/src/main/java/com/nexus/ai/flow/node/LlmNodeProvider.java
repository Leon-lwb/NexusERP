package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import com.nexus.ai.service.chat.ChatModelFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 节点执行器
 * <p>调用配置的模型（同步 ChatModel），prompt 模板支持 {{path}} 变量渲染</p>
 *
 * @author NexusERP
 */
@Component
@RequiredArgsConstructor
public class LlmNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModelFactory chatModelFactory;

    @Override
    public String getNodeType() {
        return "llm";
    }

    @Override
    public String getDisplayName() {
        return "LLM 调用";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("modelId", "模型", "number", "0", true),
            new NodeParamSchema("promptTemplate", "提示词模板", "textarea", "", true),
            new NodeParamSchema("systemPrompt", "系统提示词", "textarea", "", false),
            new NodeParamSchema("temperature", "温度", "number", "0.7", false),
            new NodeParamSchema("timeoutSeconds", "超时(秒)", "number", "300", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("modelId") == null || !config.get("modelId").canConvertToLong()) {
            throw new IllegalArgumentException("LLM 节点缺少 modelId 配置");
        }
        if (config.get("promptTemplate") == null || config.get("promptTemplate").asText().isBlank()) {
            throw new IllegalArgumentException("LLM 节点缺少 promptTemplate 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        Long modelId = config.get("modelId").asLong();

        // 渲染提示词模板
        String promptTemplate = config.get("promptTemplate").asText();
        String prompt = FlowTemplateRenderer.render(promptTemplate, ctx.getState(),
            "节点 " + ctx.getNodeName() + " 的 promptTemplate");

        // 系统提示词（可选）
        String systemPrompt = null;
        if (config.has("systemPrompt") && !config.get("systemPrompt").isNull()) {
            systemPrompt = FlowTemplateRenderer.render(config.get("systemPrompt").asText(), ctx.getState(),
                "节点 " + ctx.getNodeName() + " 的 systemPrompt");
        }

        // 构建消息
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(prompt));

        // 调用模型（同步，带缓存）
        ChatModel chatModel = chatModelFactory.getChatModel(modelId);
        ChatResponse response = chatModel.chat(ChatRequest.builder()
            .messages(messages)
            .build());
        AiMessage aiMessage = response.aiMessage();
        String content = aiMessage != null && aiMessage.text() != null ? aiMessage.text() : "";

        // 组装输出
        ObjectNode output = MAPPER.createObjectNode();
        output.put("content", content);
        output.put("modelId", modelId);

        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            ObjectNode tokenUsage = MAPPER.createObjectNode();
            Integer inputTokens = usage.inputTokenCount();
            Integer outputTokens = usage.outputTokenCount();
            tokenUsage.put("inputTokens", inputTokens == null ? 0 : inputTokens);
            tokenUsage.put("outputTokens", outputTokens == null ? 0 : outputTokens);
            output.set("tokenUsage", tokenUsage);
        }
        return output;
    }
}
