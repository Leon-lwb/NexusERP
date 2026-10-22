package com.nexus.ai.service.chat.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 通过生产策略构造真实原生适配器，只替换底层传输，验证 beta32 映射且不访问厂商网络。 */
@Timeout(10)
class LangChain4jNativeProviderCompatibilityTest {

    private static final String QUESTION = "查询物料甲库存";
    private static final String ANSWER = "库存正常";
    private static final String TOOL_NAME = "query_inventory";
    private static final String TOOL_ID = "native-tool-01";
    private static final String ARGUMENTS = "{\"material\":\"物料甲\",\"warehouse\":\"主仓\"}";
    private static final List<String> ARGUMENT_PARTS = List.of(
        "{\"material\":\"物料", "甲\",\"warehouse\":\"主仓\"}");
    private static final int CONFIGURED_CAP = 23;
    private static final int REQUEST_CAP = 11;
    private static final ToolSpecification TOOL = ToolSpecification.builder()
        .name(TOOL_NAME)
        .description("查询指定仓库的物料库存")
        .parameters(JsonObjectSchema.builder()
            .addStringProperty("material")
            .addStringProperty("warehouse")
            .required("material", "warehouse")
            .build())
        .build();

    /** 同步结果必须经过真实 SDK 的正文、工具调用和用量转换，并保留生产配置。 */
    @ParameterizedTest(name = "原生同步映射：{0}")
    @EnumSource(NativeProtocol.class)
    void synchronousAdaptersMapChineseTextToolsAndTokenUsage(NativeProtocol protocol) throws Exception {
        var listener = new Probe();
        ChatModel model = protocol.provider(listener).buildChatModel(config(protocol), 0.2);
        assertInstanceOf(protocol.syncType, model);
        var client = new FakeNativeClient(protocol, model);
        client.replySynchronously();

        ChatResponse response = model.chat(chatRequest(null, true));

        assertEquals(ANSWER, response.aiMessage().text());
        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());
        assertEquals(1, response.aiMessage().toolExecutionRequests().size());
        assertTool(response.aiMessage().toolExecutionRequests().get(0));
        assertUsage(response);
        listener.assertSuccess(response);
        client.assertRequest(false, protocol.configuredCap(), 0.2, true);
    }

    /** 原生流式解析器聚合中文增量，只触发一次成功终态和一次用量监听。 */
    @ParameterizedTest(name = "原生文本流：{0}")
    @EnumSource(NativeProtocol.class)
    void streamingAdaptersDeliverChineseTextUsageAndSingleTerminal(NativeProtocol protocol) throws Exception {
        var listener = new Probe();
        StreamingChatModel model = protocol.provider(listener).buildStreamingChatModel(config(protocol), new AiChatRequest());
        assertInstanceOf(protocol.streamingType, model);
        assertEquals(protocol.configuredCap(), model.defaultRequestParameters().maxOutputTokens());
        var client = new FakeNativeClient(protocol, model);
        client.replyWithStream(false);
        var handler = new StreamProbe();

        model.chat(chatRequest(null, false), handler);
        ChatResponse response = handler.completed.get(3, TimeUnit.SECONDS);

        assertEquals(List.of("库存", "正常"), handler.text);
        assertEquals(ANSWER, response.aiMessage().text());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertFalse(response.aiMessage().hasToolExecutionRequests());
        assertEquals(List.of("complete"), handler.terminals);
        assertFalse(handler.failed.isDone());
        assertUsage(response);
        listener.assertSuccess(response);
        client.assertRequest(true, protocol.configuredCap(), 0.4, false);
    }

    /** 工具参数跨帧拆分中文，真实 SDK 负责拼接；请求级上限覆盖可用的模型默认值。 */
    @ParameterizedTest(name = "原生工具流及上限：{0}")
    @EnumSource(NativeProtocol.class)
    void streamingAdaptersAssembleToolArgumentsAndHonorRequestTokenLimit(NativeProtocol protocol) throws Exception {
        var listener = new Probe();
        StreamingChatModel model = protocol.provider(listener).buildStreamingChatModel(config(protocol), new AiChatRequest());
        assertEquals(protocol.configuredCap(), model.defaultRequestParameters().maxOutputTokens());
        var client = new FakeNativeClient(protocol, model);
        client.replyWithStream(true);
        var handler = new StreamProbe();

        model.chat(chatRequest(REQUEST_CAP, true), handler);
        ChatResponse response = handler.completed.get(3, TimeUnit.SECONDS);

        assertTrue(handler.text.isEmpty());
        assertEquals(2, handler.partialTools.size());
        assertEquals(ARGUMENT_PARTS, handler.partialTools.stream().map(PartialToolCall::partialArguments).toList());
        assertTrue(handler.partialTools.stream().allMatch(tool -> tool.index() == 0));
        assertEquals(1, handler.completeTools.size());
        assertEquals(0, handler.completeTools.get(0).index());
        assertTool(handler.completeTools.get(0).toolExecutionRequest());
        assertEquals(1, response.aiMessage().toolExecutionRequests().size());
        assertTool(response.aiMessage().toolExecutionRequests().get(0));
        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());
        assertEquals(List.of("complete"), handler.terminals);
        assertFalse(handler.failed.isDone());
        assertUsage(response);
        listener.assertSuccess(response);
        assertEquals(REQUEST_CAP, listener.requests.get(0).chatRequest().parameters().maxOutputTokens());
        client.assertRequest(true, REQUEST_CAP, 0.4, true);
    }

    /** 部分正文之后发生上游故障，失败回调与监听器共享异常，不能记成功响应或成功用量。 */
    @ParameterizedTest(name = "原生上游中断：{0}")
    @EnumSource(NativeProtocol.class)
    void streamingUpstreamFailureReachesCallbackAndListenerWithoutSuccess(NativeProtocol protocol) throws Exception {
        var listener = new Probe();
        StreamingChatModel model = protocol.provider(listener).buildStreamingChatModel(config(protocol), new AiChatRequest());
        var client = new FakeNativeClient(protocol, model);
        client.failStreamAfterPartialText();
        var handler = new StreamProbe();

        model.chat(chatRequest(null, false), handler);
        Throwable failure = handler.failed.get(3, TimeUnit.SECONDS);

        assertTrue(failure.getMessage().contains("模拟上游中断"), failure.toString());
        assertEquals(List.of("库存"), handler.text);
        assertEquals(List.of("error"), handler.terminals);
        assertFalse(handler.completed.isDone());
        assertTrue(handler.completeTools.isEmpty());
        assertEquals(1, listener.requests.size());
        assertTrue(listener.responses.isEmpty(), "失败流不得产生成功用量事件");
        assertEquals(1, listener.errors.size());
        assertSame(failure, listener.errors.get(0).error());
        assertEquals(List.of("request", "error"), listener.events);
        client.assertRequest(true, protocol.configuredCap(), 0.4, false);
    }

    private static AiModel config(NativeProtocol protocol) {
        var config = new AiModel();
        config.setModelName(protocol.modelName);
        config.setApiKey("offline-native.0123456789abcdef0123456789abcdef");
        config.setMaxTokens(CONFIGURED_CAP);
        config.setTemperature(new BigDecimal("0.4"));
        return config;
    }

    private static ChatRequest chatRequest(Integer maxTokens, boolean tools) {
        var builder = ChatRequest.builder().messages(UserMessage.from(QUESTION));
        if (maxTokens != null) {
            builder.parameters(DefaultChatRequestParameters.builder().maxOutputTokens(maxTokens).build());
        }
        if (tools) {
            builder.toolSpecifications(List.of(TOOL));
        }
        return builder.build();
    }

    private static void assertTool(ToolExecutionRequest tool) {
        assertEquals(TOOL_ID, tool.id());
        assertEquals(TOOL_NAME, tool.name());
        assertEquals(JsonParser.parseString(ARGUMENTS), JsonParser.parseString(tool.arguments()));
    }

    private static void assertUsage(ChatResponse response) {
        assertNotNull(response.tokenUsage());
        assertEquals(13, response.tokenUsage().inputTokenCount());
        assertEquals(7, response.tokenUsage().outputTokenCount());
        assertEquals(20, response.tokenUsage().totalTokenCount());
    }

    private enum NativeProtocol {
        DASHSCOPE_NATIVE("qwen-plus", QwenChatModel.class, QwenStreamingChatModel.class),
        ZHIPU_NATIVE("glm-4", ZhipuAiChatModel.class, ZhipuAiStreamingChatModel.class);

        private final String modelName;
        private final Class<?> syncType;
        private final Class<?> streamingType;

        NativeProtocol(String modelName, Class<?> syncType, Class<?> streamingType) {
            this.modelName = modelName;
            this.syncType = syncType;
            this.streamingType = streamingType;
        }

        private ChatServiceProvider provider(Probe listener) {
            return this == DASHSCOPE_NATIVE
                ? new DashScopeServiceProvider(List.of(listener))
                : new ZhiPuServiceProvider(List.of(listener));
        }

        private Integer configuredCap() {
            return CONFIGURED_CAP;
        }
    }

    /** 记录真实 ChatModel/StreamingChatModel 包装层发出的监听事件。 */
    private static final class Probe implements ChatModelListener {
        private final List<ChatModelRequestContext> requests = new ArrayList<>();
        private final List<ChatModelResponseContext> responses = new ArrayList<>();
        private final List<ChatModelErrorContext> errors = new ArrayList<>();
        private final List<String> events = new ArrayList<>();

        @Override
        public void onRequest(ChatModelRequestContext context) {
            requests.add(context);
            events.add("request");
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            responses.add(context);
            events.add("response");
        }

        @Override
        public void onError(ChatModelErrorContext context) {
            errors.add(context);
            events.add("error");
        }

        private void assertSuccess(ChatResponse response) {
            assertEquals(1, requests.size());
            assertEquals(1, responses.size());
            assertTrue(errors.isEmpty());
            assertSame(response, responses.get(0).chatResponse());
            assertUsage(responses.get(0).chatResponse());
            assertEquals(List.of("request", "response"), events);
        }
    }

    private static final class StreamProbe implements StreamingChatResponseHandler {
        private final List<String> text = new ArrayList<>();
        private final List<PartialToolCall> partialTools = new ArrayList<>();
        private final List<CompleteToolCall> completeTools = new ArrayList<>();
        private final List<String> terminals = new ArrayList<>();
        private final CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        private final CompletableFuture<Throwable> failed = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String value) { text.add(value); }
        @Override
        public void onPartialToolCall(PartialToolCall value) { partialTools.add(value); }
        @Override
        public void onCompleteToolCall(CompleteToolCall value) { completeTools.add(value); }
        @Override
        public void onCompleteResponse(ChatResponse value) {
            terminals.add("complete");
            completed.complete(value);
        }
        @Override
        public void onError(Throwable error) {
            terminals.add("error");
            failed.complete(error);
        }
    }

    /** Qwen 保留模型映射，智谱同时保留原生客户端的 JSON/SSE 映射，只替换不出网的传输。 */
    private static final class FakeNativeClient {
        private final NativeProtocol protocol;
        private final Generation generation;
        private final HttpClient http;
        private final List<GenerationParam> generationRequests = new ArrayList<>();
        private final List<HttpRequest> httpRequests = new ArrayList<>();

        private FakeNativeClient(NativeProtocol protocol, Object model) {
            this.protocol = protocol;
            if (protocol == NativeProtocol.DASHSCOPE_NATIVE) {
                generation = mock(Generation.class);
                http = null;
                ReflectionTestUtils.setField(model, "generation", generation);
            } else {
                generation = null;
                http = mock(HttpClient.class);
                Object nativeClient = ReflectionTestUtils.getField(model, "client");
                assertNotNull(nativeClient);
                ReflectionTestUtils.setField(nativeClient, "httpClient", http);
            }
        }

        private void replySynchronously() throws Exception {
            JsonObject tool = toolCall(TOOL_ID, TOOL_NAME, ARGUMENTS);
            if (generation != null) {
                when(generation.call(any(GenerationParam.class))).thenAnswer(invocation -> {
                    generationRequests.add(invocation.getArgument(0));
                    return qwenResult(ANSWER, tool, "tool_calls", 7);
                });
            } else {
                when(http.execute(any(HttpRequest.class))).thenAnswer(invocation -> {
                    httpRequests.add(invocation.getArgument(0));
                    return SuccessfulHttpResponse.builder().statusCode(200)
                        .body(zhipuResponse(false, ANSWER, tool, "tool_calls", 7)).build();
                });
            }
        }

        private void replyWithStream(boolean tools) throws Exception {
            if (generation != null) {
                doAnswer(invocation -> {
                    generationRequests.add(invocation.getArgument(0));
                    ResultCallback<GenerationResult> callback = invocation.getArgument(1);
                    if (tools) {
                        callback.onEvent(qwenResult(null, toolCall(TOOL_ID, TOOL_NAME, ARGUMENT_PARTS.get(0)), null, 2));
                        callback.onEvent(qwenResult(null, toolCall(null, null, ARGUMENT_PARTS.get(1)), null, 4));
                    } else {
                        callback.onEvent(qwenResult("库存", null, null, 2));
                        callback.onEvent(qwenResult("正常", null, null, 4));
                    }
                    callback.onEvent(qwenResult("", null, tools ? "tool_calls" : "stop", 7));
                    callback.onComplete();
                    return null;
                }).when(generation).streamCall(any(GenerationParam.class), any());
            } else {
                doAnswer(invocation -> {
                    httpRequests.add(invocation.getArgument(0));
                    ServerSentEventListener listener = invocation.getArgument(1);
                    if (tools) {
                        emit(listener, null, toolCall(TOOL_ID, TOOL_NAME, ARGUMENT_PARTS.get(0)), null, 2);
                        emit(listener, null, toolCall(null, null, ARGUMENT_PARTS.get(1)), null, 4);
                    } else {
                        emit(listener, "库存", null, null, 2);
                        emit(listener, "正常", null, null, 4);
                    }
                    emit(listener, null, null, tools ? "tool_calls" : "stop", 7);
                    listener.onEvent(new ServerSentEvent(null, "[DONE]"));
                    listener.onClose();
                    return null;
                }).when(http).execute(any(HttpRequest.class), any(ServerSentEventListener.class));
            }
        }

        private void failStreamAfterPartialText() throws Exception {
            var failure = new IllegalStateException("模拟上游中断");
            if (generation != null) {
                doAnswer(invocation -> {
                    generationRequests.add(invocation.getArgument(0));
                    ResultCallback<GenerationResult> callback = invocation.getArgument(1);
                    callback.onEvent(qwenResult("库存", null, null, 2));
                    callback.onError(failure);
                    return null;
                }).when(generation).streamCall(any(GenerationParam.class), any());
            } else {
                doAnswer(invocation -> {
                    httpRequests.add(invocation.getArgument(0));
                    ServerSentEventListener listener = invocation.getArgument(1);
                    emit(listener, "库存", null, null, 2);
                    listener.onError(failure);
                    listener.onClose();
                    return null;
                }).when(http).execute(any(HttpRequest.class), any(ServerSentEventListener.class));
            }
        }

        private void emit(ServerSentEventListener listener, String text, JsonObject tool, String finish, int outputTokens) {
            listener.onEvent(new ServerSentEvent(null, zhipuResponse(true, text, tool, finish, outputTokens)));
        }

        private void assertRequest(boolean streaming, Integer cap, double temperature, boolean tools) throws Exception {
            if (generation != null) {
                assertEquals(1, generationRequests.size());
                GenerationParam request = generationRequests.get(0);
                assertEquals(protocol.modelName, request.getModel());
                assertEquals(cap, request.getMaxTokens());
                assertEquals(temperature, request.getTemperature().doubleValue(), 0.000001);
                assertEquals(QUESTION, request.getMessages().get(0).getContent());
                if (tools) {
                    assertNotNull(request.getTools());
                    assertEquals(1, request.getTools().size());
                    assertTrue(JsonUtils.toJson(request.getTools()).contains(TOOL_NAME));
                    assertTrue(JsonUtils.toJson(request.getTools()).contains("warehouse"));
                } else {
                    assertTrue(request.getTools() == null || request.getTools().isEmpty());
                }
                if (streaming) {
                    verify(generation).streamCall(any(GenerationParam.class), any());
                } else {
                    verify(generation).call(any(GenerationParam.class));
                }
                verifyNoMoreInteractions(generation);
            } else {
                assertEquals(1, httpRequests.size());
                JsonObject body = JsonParser.parseString(httpRequests.get(0).body()).getAsJsonObject();
                assertEquals(protocol.modelName, body.get("model").getAsString());
                if (cap == null) {
                    assertTrue(!body.has("max_tokens") || body.get("max_tokens").isJsonNull(),
                        "当前智谱生产策略没有配置 SDK 的 maxToken");
                } else {
                    assertEquals(cap.intValue(), body.get("max_tokens").getAsInt());
                }
                assertEquals(streaming, body.get("stream").getAsBoolean());
                assertEquals(temperature, body.get("temperature").getAsDouble(), 0.000001);
                assertEquals(
                        QUESTION,
                        body.getAsJsonArray("messages")
                                .get(0)
                                .getAsJsonObject()
                                .get("content")
                                .getAsString());
                if (tools) {
                    assertEquals(1, body.getAsJsonArray("tools").size());
                    var function =
                            body.getAsJsonArray("tools")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonObject("function");
                    assertEquals(TOOL_NAME, function.get("name").getAsString());
                    assertTrue(
                            function.getAsJsonObject("parameters")
                                    .getAsJsonObject("properties")
                                    .has("warehouse"));
                }
                if (streaming) {
                    verify(http).execute(any(HttpRequest.class), any(ServerSentEventListener.class));
                } else {
                    verify(http).execute(any(HttpRequest.class));
                }
                verifyNoMoreInteractions(http);
            }
        }

        private GenerationResult qwenResult(String text, JsonObject tool, String finish, int outputTokens) {
            var result = new JsonObject();
            result.addProperty("request_id", "native-qwen-01");
            result.add("usage", usage("input_tokens", "output_tokens", outputTokens));
            var choice = new JsonObject();
            choice.addProperty("index", 0);
            choice.addProperty("finish_reason", finish);
            choice.add("message", message(text, tool));
            var choices = new JsonArray();
            choices.add(choice);
            var output = new JsonObject();
            output.add("choices", choices);
            result.add("output", output);
            return JsonUtils.fromJson(result.toString(), GenerationResult.class);
        }

        private String zhipuResponse(boolean streaming, String text, JsonObject tool, String finish, int outputTokens) {
            var result = new JsonObject();
            result.addProperty("id", "native-zhipu-01");
            result.addProperty("model", protocol.modelName);
            result.add("usage", usage("prompt_tokens", "completion_tokens", outputTokens));
            var choice = new JsonObject();
            choice.addProperty("index", 0);
            choice.addProperty("finish_reason", finish);
            choice.add(streaming ? "delta" : "message", message(text, tool));
            var choices = new JsonArray();
            choices.add(choice);
            result.add("choices", choices);
            return result.toString();
        }

        private static JsonObject message(String text, JsonObject tool) {
            var message = new JsonObject();
            message.addProperty("role", "assistant");
            if (text != null) {
                message.addProperty("content", text);
            }
            if (tool != null) {
                var tools = new JsonArray();
                tools.add(tool);
                message.add("tool_calls", tools);
            }
            return message;
        }

        private static JsonObject toolCall(String id, String name, String arguments) {
            var call = new JsonObject();
            call.addProperty("index", 0);
            call.addProperty("type", "function");
            if (id != null) {
                call.addProperty("id", id);
            }
            var function = new JsonObject();
            if (name != null) {
                function.addProperty("name", name);
            }
            function.addProperty("arguments", arguments);
            call.add("function", function);
            return call;
        }

        private static JsonObject usage(String inputName, String outputName, int outputTokens) {
            var usage = new JsonObject();
            usage.addProperty(inputName, 13);
            usage.addProperty(outputName, outputTokens);
            usage.addProperty("total_tokens", 13 + outputTokens);
            return usage;
        }
    }
}
