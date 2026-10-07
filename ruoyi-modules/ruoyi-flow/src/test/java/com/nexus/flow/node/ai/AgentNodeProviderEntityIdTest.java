package com.nexus.flow.node.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** 真实 Agent 节点兼容回归；工厂、工具与模型均离线模拟，不启动业务服务。 */
@DisplayName("Agent 节点实体 ID 无损兼容")
class AgentNodeProviderEntityIdTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MODEL_ID = 2103871286244564993L;
    private static final String MODEL_ID_ERROR = "Agent 节点缺少 modelId 配置";

    private final ChatModelFactory factory = mock(ChatModelFactory.class);
    private final ToolProviderFactory tools = mock(ToolProviderFactory.class);
    private final AgentNodeProvider provider = new AgentNodeProvider(factory, tools);

    @AfterEach
    void clearTestContext() {
        AiRequestContext.clear();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validModelIds")
    @DisplayName("保存校验接受规范文本与历史整型 ID")
    void validIdIsAcceptedWithoutLoadingModels(String description, JsonNode id, long expected) {
        assertDoesNotThrow(() -> provider.validateConfig(config(id)), "合法实体 ID 不应因文本类型被拒绝：" + description);
        verifyNoInteractions(factory, tools);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validModelIds")
    @DisplayName("执行将精确 Long 传到 Agent 专用工厂后才触及工具")
    void executionReachesTheExactAgentModelId(String description, JsonNode id, long expected) {
        RuntimeException sentinel = new IllegalStateException("离线模型工厂哨兵");
        doThrow(sentinel).when(factory).getAgentChatModel(anyLong());

        RuntimeException failure = assertThrows(RuntimeException.class,
            () -> provider.execute(context(config(id))), "合法实体 ID 应抵达离线工厂：" + description);

        assertSame(sentinel, failure, "必须停在模型工厂哨兵，不能调用真实模型或工具");
        verify(factory).getAgentChatModel(expected);
        verifyNoMoreInteractions(factory);
        verifyNoInteractions(tools);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigs")
    @DisplayName("保存校验拒绝缺失、非法、浮点与越界 ID")
    void validationRejectsInvalidModelId(String description, JsonNode config) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> provider.validateConfig(config), "非法实体 ID 必须拒绝：" + description);
        assertEquals(MODEL_ID_ERROR, failure.getMessage(), "保持已有模型配置错误契约");
        verifyNoInteractions(factory, tools);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigs")
    @DisplayName("直接执行非法 ID 不能转成零或截断后调用模型")
    void executionRejectsInvalidModelIdBeforeModelAndTools(String description, JsonNode config) {
        doThrow(new IllegalStateException("非法 ID 越过模型边界")).when(factory).getAgentChatModel(anyLong());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> provider.execute(context(config)), "不能绕过校验并将非法 ID 转成模型 ID：" + description);

        assertEquals(MODEL_ID_ERROR, failure.getMessage(), "执行与保存应使用相同模型配置错误");
        verifyNoInteractions(factory, tools);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"规范文本", "历史整型"})
    @DisplayName("真实 Agent 离线编排返回文本 ID 并保留模板、令牌和上下文")
    void outputModelIdIsTextAndExistingAgentContractsRemain(String representation) throws Exception {
        JsonNode id = "规范文本".equals(representation)
            ? MAPPER.getNodeFactory().textNode(Long.toString(MODEL_ID))
            : MAPPER.getNodeFactory().numberNode(MODEL_ID);
        ChatModel model = mock(ChatModel.class, CALLS_REAL_METHODS);
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("离线 Agent 回答")).build();
        doAnswer(invocation -> {
            AiRequestContext.setLastTokenUsage(13, 5, MODEL_ID);
            return response;
        }).when(model).doChat(any(ChatRequest.class));
        when(factory.getAgentChatModel(MODEL_ID)).thenReturn(model);
        when(tools.getEnabledBuiltinToolObjectsByCategories("QUERY,ALL")).thenReturn(List.of());
        AiRequestContext.set("100", 200L);
        ObjectNode config = config(id);

        assertDoesNotThrow(() -> provider.validateConfig(config), "离线编排前应允许有效模型 ID");
        JsonNode output = provider.execute(context(config));

        assertTrue(output.get("modelId").isTextual(), "局部 ObjectNode 输出必须直接使用文本 ID，避免前端精度丢失");
        assertEquals(Long.toString(MODEL_ID), output.get("modelId").textValue(), "输出模型 ID 必须逐位保留");
        assertEquals(Long.toString(MODEL_ID), MAPPER.readTree(MAPPER.writeValueAsString(output)).get("modelId").textValue(),
            "JSON 往返后仍应是无损文本 ID");
        assertEquals("离线 Agent 回答", output.get("content").asText(), "Agent 回答内容保持不变");
        assertTrue(output.at("/tokenUsage/inputTokens").isIntegralNumber(), "令牌用量仍为普通数值");
        assertEquals(13, output.at("/tokenUsage/inputTokens").intValue(), "保留输入令牌用量");
        assertEquals(5, output.at("/tokenUsage/outputTokens").intValue(), "保留输出令牌用量");
        assertEquals("100", AiRequestContext.getTenantId(), "节点不能覆盖流程级租户上下文");
        assertEquals(200L, AiRequestContext.getUserId(), "节点不能覆盖流程级用户上下文");
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).doChat(request.capture());
        assertTrue(request.getValue().messages().stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .anyMatch(message -> "请回答以下问题：测试 零外联".equals(message.singleText())), "问题模板应按既有 Agent 路径渲染");
        verify(factory).getAgentChatModel(MODEL_ID);
        verifyNoMoreInteractions(factory);
        verify(tools).getEnabledBuiltinToolObjectsByCategories("QUERY,ALL");
        verifyNoMoreInteractions(tools);
    }

    @Test
    @DisplayName("无令牌用量时不新增输出且保留显式工具类别")
    void absentTokenUsageAndExplicitToolCategoriesKeepTheirContract() {
        ChatModel model = mock(ChatModel.class, CALLS_REAL_METHODS);
        doReturn(ChatResponse.builder().aiMessage(AiMessage.from("无令牌回答")).build())
            .when(model).doChat(any(ChatRequest.class));
        when(factory.getAgentChatModel(42L)).thenReturn(model);
        when(tools.getEnabledBuiltinToolObjectsByCategories("QUERY")).thenReturn(List.of());
        ObjectNode config = config(MAPPER.getNodeFactory().numberNode(42)).put("toolCategories", "QUERY");

        JsonNode output = provider.execute(context(config));

        assertEquals("42", output.get("modelId").textValue(), "历史整型 ID 输出同样应无损文本化");
        assertFalse(output.has("tokenUsage"), "无上下文用量时不能捏造令牌统计");
        assertEquals("无令牌回答", output.get("content").asText(), "离线回答保持原样");
        verify(factory).getAgentChatModel(42L);
        verify(tools).getEnabledBuiltinToolObjectsByCategories("QUERY");
        verifyNoMoreInteractions(factory, tools);
    }

    @ParameterizedTest(name = "问题模板：{0}")
    @ValueSource(strings = {"", " "})
    @DisplayName("文本模型 ID 兼容不能绕过问题模板必填校验")
    void queryTemplateStillRequired(String template) {
        ObjectNode config = config(MAPPER.getNodeFactory().textNode(Long.toString(MODEL_ID)));
        config.put("queryTemplate", template);
        assertEquals("Agent 节点缺少 queryTemplate 配置",
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config), "问题模板仍必须非空").getMessage(),
            "有效文本 ID 后仍执行原问题模板校验");
        config.remove("queryTemplate");
        assertEquals("Agent 节点缺少 queryTemplate 配置",
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(config), "缺失问题模板仍必须拒绝").getMessage(),
            "缺失问题模板保持原错误契约");
        verifyNoInteractions(factory, tools);
    }

    @Test
    @DisplayName("实体来源、零默认值和普通数值参数不变")
    void parameterSchemaAndAgentIdentityRemainUnchanged() {
        var schemas = provider.getParamSchemas();
        assertEquals("agent", provider.getNodeType(), "保留 Agent 节点类型");
        assertEquals("Agent 智能体", provider.getDisplayName(), "保留 Agent 展示名称");
        assertEquals("modelId", schemas.get(0).getName(), "实体模型参数仍为 modelId");
        assertEquals("number", schemas.get(0).getType(), "不改变共享表单的既有实体类型声明");
        assertEquals("aiModel", schemas.get(0).getOptionsSource(), "模型选项来源保持不变");
        assertEquals("0", schemas.get(0).getDefaultValue(), "保留零默认值兼容契约");
        assertTrue(schemas.get(0).isRequired(), "模型 ID 保持必填");
        assertEquals("number", schemas.get(4).getType(), "普通调用次数参数仍为数值");
        assertEquals("20", schemas.get(4).getDefaultValue(), "普通调用次数默认值不变");
        verifyNoInteractions(factory, tools);
    }

    private static Stream<Arguments> validModelIds() {
        return Stream.of(0L, 42L, 9007199254740993L, MODEL_ID, Long.MAX_VALUE).flatMap(id -> Stream.of(
            Arguments.of("规范文本 " + id, MAPPER.getNodeFactory().textNode(Long.toString(id)), id),
            Arguments.of("历史整型 " + id, MAPPER.getNodeFactory().numberNode(id), id)));
    }

    private static Stream<Arguments> invalidConfigs() {
        Stream<Arguments> missing = Stream.of(
            Arguments.of("空配置", (JsonNode) null),
            Arguments.of("字段缺失", MAPPER.createObjectNode().put("queryTemplate", "测试")),
            Arguments.of("空值字段", config(MAPPER.nullNode())));
        Stream<Arguments> text = Stream.of("", " ", "abc", "1.5", "1e3", "+1", "-1", "01", "00",
            " 1", "1 ", "1\n", "１２", "9223372036854775808").map(id ->
                Arguments.of("非法文本 " + MAPPER.getNodeFactory().textNode(id), config(MAPPER.getNodeFactory().textNode(id))));
        Stream<Arguments> others = Stream.of(
            Arguments.of("负数整型", config(MAPPER.getNodeFactory().numberNode(-1))),
            Arguments.of("小数数值", config(MAPPER.getNodeFactory().numberNode(1.5))),
            Arguments.of("浮点整值", config(MAPPER.getNodeFactory().numberNode(1.0))),
            Arguments.of("浮点科学计数值", config(MAPPER.getNodeFactory().numberNode(1e3))),
            Arguments.of("越界整型", config(MAPPER.getNodeFactory().numberNode(new BigInteger("9223372036854775808")))),
            Arguments.of("布尔值", config(MAPPER.getNodeFactory().booleanNode(true))),
            Arguments.of("对象值", config(MAPPER.createObjectNode())),
            Arguments.of("数组值", config(MAPPER.createArrayNode())));
        return Stream.concat(Stream.concat(missing, text), others);
    }

    private static ObjectNode config(JsonNode id) {
        ObjectNode config = MAPPER.createObjectNode().put("queryTemplate", "测试 {{input.subject}}");
        config.set("modelId", id);
        return config;
    }

    private static FlowExecutionContext context(JsonNode config) {
        FlowExecutionContext context = new FlowExecutionContext();
        context.setNodeId("node-f01-agent");
        context.setNodeName("离线 Agent 节点");
        context.setNodeType("agent");
        context.setConfig(config);
        context.setState(new FlowState(MAPPER.createObjectNode().put("subject", "零外联")));
        return context;
    }
}
