package com.nexus.flow.node.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 真实 LLM 节点回归；仅 mock 工厂和模型，不启动 Spring 或连接外部模型。 */
class LlmNodeProviderEntityIdTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MODEL_ID = 2103871286244564993L;
    private final ChatModelFactory factory = mock(ChatModelFactory.class);
    private final ChatModel model = mock(ChatModel.class);
    private final LlmNodeProvider provider = new LlmNodeProvider(factory);

    @Test
    void numericLongModelOutputIsTextAndSurvivesJsonRoundTrip() throws Exception {
        ObjectNode config = config();
        config.put("modelId", MODEL_ID);
        stubResponse(MODEL_ID, ChatResponse.builder().aiMessage(AiMessage.from("离线回答")).tokenUsage(new TokenUsage(13, 5)).build());

        provider.validateConfig(config);
        JsonNode output = provider.execute(context(config));
        assertTrue(output.get("modelId").isTextual(), "ObjectNode 的模型 ID 必须为文本，不能依赖局部序列化器");
        assertEquals(Long.toString(MODEL_ID), output.get("modelId").textValue());
        assertEquals(Long.toString(MODEL_ID), MAPPER.readTree(MAPPER.writeValueAsString(output)).get("modelId").textValue());
        assertEquals("离线回答", output.get("content").asText());
        assertTrue(output.at("/tokenUsage/inputTokens").isIntegralNumber());
        assertEquals(13, output.at("/tokenUsage/inputTokens").intValue());
        assertEquals(5, output.at("/tokenUsage/outputTokens").intValue());
        verify(factory).getChatModel(MODEL_ID);
        verify(model).chat(any(ChatRequest.class));
        verifyNoMoreInteractions(factory, model);
    }

    @Test
    void savedTextModelIdValidatesAndReachesTheExactFactoryId() throws Exception {
        ObjectNode config = (ObjectNode) MAPPER.readTree("{\"modelId\":\"2103871286244564993\",\"promptTemplate\":\"测试 {{input.subject}}\",\"systemPrompt\":\"系统 {{input.subject}}\"}");
        stubResponse(MODEL_ID, ChatResponse.builder().aiMessage(AiMessage.from("离线回答")).build());

        assertDoesNotThrow(() -> provider.validateConfig(config));
        JsonNode output = provider.execute(context(config));
        assertEquals("2103871286244564993", output.get("modelId").textValue());
        assertFalse(output.has("tokenUsage"));
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(request.capture());
        assertEquals(2, request.getValue().messages().size());
        assertEquals("系统 零外联", ((SystemMessage) request.getValue().messages().get(0)).text());
        assertEquals("测试 零外联", ((UserMessage) request.getValue().messages().get(1)).singleText());
        verify(factory).getChatModel(MODEL_ID);
    }

    @Test
    void secondSnowflakeAndLongUpperBoundRemainExact() {
        for (String id : new String[]{"9007199254740993", "9223372036854775807"}) {
            ObjectNode config = config();
            config.put("modelId", id);
            long expected = Long.parseLong(id);
            stubResponse(expected, ChatResponse.builder().aiMessage(AiMessage.from("离线回答")).build());
            assertDoesNotThrow(() -> provider.validateConfig(config));
            assertEquals(id, provider.execute(context(config)).get("modelId").textValue());
            verify(factory).getChatModel(expected);
        }
    }

    @Test
    void zeroDefaultDoesNotSelectAnUnrelatedModel() {
        stubResponse(0L, ChatResponse.builder().aiMessage(AiMessage.from("默认契约")).build());
        for (JsonNode id : new JsonNode[]{MAPPER.getNodeFactory().numberNode(0), MAPPER.getNodeFactory().textNode("0")}) {
            ObjectNode config = config();
            config.set("modelId", id);
            assertDoesNotThrow(() -> provider.validateConfig(config));
            assertEquals("0", provider.execute(context(config)).get("modelId").textValue());
        }
        verify(factory, times(2)).getChatModel(0L);
        verifyNoMoreInteractions(factory);
    }

    @Test
    void missingOrEmptyModelIdKeepsTheExistingValidationError() {
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(null));
        ObjectNode missing = config();
        assertEquals("LLM 节点缺少 modelId 配置", assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(missing)).getMessage());
        for (JsonNode id : new JsonNode[]{MAPPER.nullNode(), MAPPER.getNodeFactory().textNode(""), MAPPER.getNodeFactory().textNode(" ")}) {
            ObjectNode config = config();
            config.set("modelId", id);
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
        }
        verifyNoInteractions(factory, model);
    }

    @Test
    void invalidTextIdsNeverSilentlyBecomeZero() {
        for (String id : new String[]{"abc", "1.5", "1e3", "+1", "-1", "01", " 1", "9223372036854775808"}) {
            ObjectNode config = config();
            config.put("modelId", id);
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
            assertThrows(IllegalArgumentException.class, () -> provider.execute(context(config)));
        }
        verifyNoInteractions(factory, model);
    }

    @Test
    void fractionalModelIdIsNotTruncatedIntoAnEntity() {
        ObjectNode config = config();
        config.put("modelId", 42.5);
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
        assertThrows(IllegalArgumentException.class, () -> provider.execute(context(config)));
        verifyNoInteractions(factory, model);
    }

    @Test
    void promptRemainsRequired() {
        ObjectNode config = MAPPER.createObjectNode().put("modelId", 42);
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
        config.put("promptTemplate", " ");
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config));
        verifyNoInteractions(factory, model);
    }

    @Test
    void emptyAiMessageAndAbsentTokenUsageKeepTheirContract() {
        ObjectNode config = config().put("modelId", 42);
        stubResponse(42L, mock(ChatResponse.class));
        JsonNode output = provider.execute(context(config));
        assertEquals("", output.get("content").asText());
        assertFalse(output.has("tokenUsage"));
        assertEquals("42", output.get("modelId").textValue());
    }

    @Test
    void schemaKeepsEntitySourceDefaultAndOrdinaryNumberParameters() {
        var schemas = provider.getParamSchemas();
        assertEquals("number", schemas.get(0).getType());
        assertEquals("aiModel", schemas.get(0).getOptionsSource());
        assertEquals("0", schemas.get(0).getDefaultValue());
        assertTrue(schemas.get(0).isRequired());
        assertEquals("number", schemas.get(3).getType());
        assertEquals("0.7", schemas.get(3).getDefaultValue());
        assertEquals("number", schemas.get(4).getType());
        assertEquals("300", schemas.get(4).getDefaultValue());
        verifyNoInteractions(factory, model);
    }

    private ObjectNode config() {
        return MAPPER.createObjectNode().put("promptTemplate", "测试 {{input.subject}}");
    }

    private FlowExecutionContext context(ObjectNode config) {
        FlowExecutionContext context = new FlowExecutionContext();
        context.setNodeId("node-f01");
        context.setNodeName("离线模型节点");
        context.setNodeType("llm");
        context.setConfig(config);
        context.setState(new FlowState(MAPPER.createObjectNode().put("subject", "零外联")));
        return context;
    }

    private void stubResponse(long id, ChatResponse response) {
        when(factory.getChatModel(id)).thenReturn(model);
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
    }
}
