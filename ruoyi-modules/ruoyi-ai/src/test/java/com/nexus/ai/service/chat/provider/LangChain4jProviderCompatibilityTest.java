package com.nexus.ai.service.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 SDK 与回环合成服务验证协议、流式回调和监听器；不访问厂商网络。 */
@Timeout(30)
class LangChain4jProviderCompatibilityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ANSWER = "库存正常";
    private static final String USAGE = "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}";

    /** 七类生产适配器均通过真实协议工厂发现，并构造同步、流式模型。 */
    @ParameterizedTest(name = "构造与路由：{0}")
    @MethodSource("protocols")
    void sevenAdaptersBuildAndRouteWithoutNetwork(String protocol) {
        var listener = new Probe();
        ChatServiceProvider provider = provider(protocol, listener);
        try (var context = new GenericApplicationContext()) {
            context.registerBean("offlineProvider", ChatServiceProvider.class, () -> provider);
            context.refresh();
            var factory = new ChatServiceProviderFactory(new AiProviderPolicyRegistry());
            factory.setApplicationContext(context);
            var config = config(protocol, "http://127.0.0.1:1");
            assertSame(provider, factory.get(config));
            ChatModel sync = provider.buildChatModel(config, 0.2);
            StreamingChatModel streaming = provider.buildStreamingChatModel(config, request());
            assertNotNull(sync);
            assertNotNull(streaming);
            assertTrue(sync.listeners().contains(listener), protocol);
            assertTrue(streaming.listeners().contains(listener), protocol);
            assertEquals(0, listener.responses.get(), "构造不能产生模型调用");
        }
    }

    /** 验证真实 HTTP 编解码、中文正文、用量及应用参数，避免只测试构造器。 */
    @ParameterizedTest(name = "同步 HTTP：{0}")
    @MethodSource("wireProtocols")
    void synchronousProtocolsDecodeRealSyntheticResponse(Wire wire) throws Exception {
        var listener = new Probe();
        try (var endpoint = new Endpoint(200, "application/json", wire.response())) {
            var model = provider(wire.protocol(), listener).buildChatModel(config(wire.protocol(), endpoint.url()), 0.2);
            var response = model.chat(ChatRequest.builder().messages(dev.langchain4j.data.message.UserMessage.from("查询库存")).build());
            assertEquals(ANSWER, response.aiMessage().text());
            assertNotNull(response.tokenUsage());
            assertEquals(5, response.tokenUsage().totalTokenCount());
            assertEquals(1, listener.responses.get());
            assertEquals(0, listener.errors.get());
            assertRequest(endpoint, wire, 23, false);
        }
    }

    /** 回调由真实流解析器触发，正文与终态完整，单次调用只记一次用量。 */
    @ParameterizedTest(name = "流式 HTTP：{0}")
    @MethodSource("wireProtocols")
    void streamingProtocolsDeliverTextTerminalAndUsage(Wire wire) throws Exception {
        var listener = new Probe();
        var chunks = new CopyOnWriteArrayList<String>();
        var thinking = new CopyOnWriteArrayList<String>();
        var done = new CompletableFuture<ChatResponse>();
        var terminals = new AtomicInteger();
        try (var endpoint = new Endpoint(200, wire.protocol().equals("OLLAMA_NATIVE") ? "application/x-ndjson" : "text/event-stream", wire.stream())) {
            var model = provider(wire.protocol(), listener).buildStreamingChatModel(config(wire.protocol(), endpoint.url()), request());
            model.chat(ChatRequest.builder().messages(dev.langchain4j.data.message.UserMessage.from("查询库存")).build(), new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String text) { chunks.add(text); }
                @Override public void onPartialThinking(PartialThinking value) { thinking.add(value.text()); }
                @Override public void onCompleteResponse(ChatResponse response) { terminals.incrementAndGet(); done.complete(response); }
                @Override public void onError(Throwable error) { terminals.incrementAndGet(); done.completeExceptionally(error); }
            });
            ChatResponse response = done.get(10, TimeUnit.SECONDS);
            assertEquals(ANSWER, String.join("", chunks));
            assertEquals(ANSWER, response.aiMessage().text());
            assertEquals(5, response.tokenUsage().totalTokenCount());
            assertEquals(1, terminals.get());
            assertEquals(1, listener.responses.get());
            assertEquals(0, listener.errors.get());
            if (wire.protocol().equals("OPENAI_CHAT_COMPLETIONS")) assertEquals(List.of("先核对"), thinking);
            assertRequest(endpoint, wire, wire.protocol().equals("OLLAMA_NATIVE") ? 23 : 17, true);
        }
    }

    /** 客户端错误必须走失败回调，不能伪造成功回答或成功用量。 */
    @ParameterizedTest(name = "HTTP 错误：{0}")
    @MethodSource("wireProtocols")
    void streamingHttpFailureReachesErrorListener(Wire wire) throws Exception {
        var listener = new Probe();
        var failed = new CompletableFuture<Throwable>();
        var successes = new AtomicInteger();
        try (var endpoint = new Endpoint(400, "application/json", "{\"error\":{\"message\":\"offline rejection\",\"type\":\"invalid_request_error\",\"code\":400},\"message\":\"offline rejection\"}")) {
            provider(wire.protocol(), listener).buildStreamingChatModel(config(wire.protocol(), endpoint.url()), request())
                .chat(ChatRequest.builder().messages(dev.langchain4j.data.message.UserMessage.from("查询库存")).build(), new StreamingChatResponseHandler() {
                    @Override public void onPartialResponse(String text) { fail("错误响应不能产生正文"); }
                    @Override public void onCompleteResponse(ChatResponse response) { successes.incrementAndGet(); failed.completeExceptionally(new AssertionError("unexpected success")); }
                    @Override public void onError(Throwable error) { failed.complete(error); }
                });
            assertNotNull(failed.get(10, TimeUnit.SECONDS));
            assertEquals(0, successes.get());
            assertEquals(0, listener.responses.get());
            assertEquals(1, listener.errors.get());
        }
    }

    /** 请求级限制与无效配置裁剪保留已有语义。 */
    @Test
    void openAiRequestCapsAndThinkingSwitchRemainExplicit() {
        var provider = (OpenAiServiceProvider) provider("OPENAI_CHAT_COMPLETIONS", new Probe());
        var config = config("OPENAI_CHAT_COMPLETIONS", "http://127.0.0.1:1");
        config.setMaxTokens(900000);
        assertEquals(393216, provider.buildChatModel(config, 0.2).defaultRequestParameters().maxOutputTokens());
        assertEquals(17, provider.buildStreamingChatModel(config, request()).defaultRequestParameters().maxOutputTokens());
        config.setMaxTokens(-1);
        assertNull(provider.buildChatModel(config, 0.2).defaultRequestParameters().maxOutputTokens());
    }

    private static Stream<String> protocols() {
        return Stream.of("OPENAI_CHAT_COMPLETIONS", "OPENAI_RESPONSES", "ANTHROPIC_MESSAGES", "GEMINI_GENERATE_CONTENT", "OLLAMA_NATIVE", "DASHSCOPE_NATIVE", "ZHIPU_NATIVE");
    }

    private static ChatServiceProvider provider(String protocol, Probe listener) {
        List<ChatModelListener> listeners = List.of(listener);
        return switch (protocol) {
            case "OPENAI_CHAT_COMPLETIONS" -> { var value = new OpenAiServiceProvider(); value.chatModelListeners = listeners; yield value; }
            case "OPENAI_RESPONSES" -> { var value = new OpenAiResponsesServiceProvider(); value.chatModelListeners = listeners; yield value; }
            case "ANTHROPIC_MESSAGES" -> new AnthropicMessagesServiceProvider(listeners);
            case "GEMINI_GENERATE_CONTENT" -> new GeminiServiceProvider(listeners);
            case "OLLAMA_NATIVE" -> new OllamaServiceProvider(listeners);
            case "DASHSCOPE_NATIVE" -> new DashScopeServiceProvider(listeners);
            case "ZHIPU_NATIVE" -> new ZhiPuServiceProvider(listeners);
            default -> throw new AssertionError(protocol);
        };
    }

    private static AiModel config(String protocol, String url) {
        var model = new AiModel();
        model.setId(9L);
        model.setApiProtocol(protocol);
        model.setModelName("offline-model");
        model.setApiHost(url);
        model.setApiKey("offline-test-key");
        model.setTemperature(new BigDecimal("0.2"));
        model.setMaxTokens(23);
        return model;
    }

    private static AiChatRequest request() {
        var request = new AiChatRequest();
        request.setContent("查询库存");
        request.setEnableThinking(true);
        request.setMaxOutputTokens(17);
        return request;
    }

    private static void assertRequest(Endpoint endpoint, Wire wire, int cap, boolean streaming) {
        assertEquals(1, endpoint.requests.size());
        var captured = endpoint.requests.get(0);
        String expectedPath = streaming && wire.protocol().equals("GEMINI_GENERATE_CONTENT") ? ":streamGenerateContent" : wire.path();
        assertTrue(captured.path().endsWith(expectedPath), captured.path());
        assertTrue(captured.body().toString().contains("查询库存"));
        assertEquals(cap, captured.body().at(wire.capPointer()).asInt());
    }

    private record Wire(String protocol, String path, String capPointer, String response, String stream) {
        @Override public String toString() { return protocol; }
    }

    private static Stream<Wire> wireProtocols() {
        String openAi = "{\"id\":\"chat-offline\",\"model\":\"offline-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"库存正常\"},\"finish_reason\":\"stop\"}]," + USAGE + "}";
        String openAiStream = sse("", "{\"id\":\"chat-offline\",\"model\":\"offline-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"先核对\",\"content\":\"库存正常\"}}]}")
            + sse("", "{\"id\":\"chat-offline\",\"model\":\"offline-model\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]," + USAGE + "}") + sse("", "[DONE]");
        String responses = "{\"id\":\"resp-offline\",\"model\":\"offline-model\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"id\":\"msg-offline\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"库存正常\",\"annotations\":[]}]}],\"usage\":{\"input_tokens\":3,\"output_tokens\":2,\"total_tokens\":5}}";
        String responsesStream = sse("response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"content_index\":0,\"delta\":\"库存正常\"}")
            + sse("response.completed", "{\"type\":\"response.completed\",\"response\":" + responses + "}");
        String anthropic = "{\"id\":\"msg-offline\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"offline-model\",\"content\":[{\"type\":\"text\",\"text\":\"库存正常\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}";
        String anthropicStream = sse("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg-offline\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"offline-model\",\"content\":[],\"usage\":{\"input_tokens\":3,\"output_tokens\":0}}}")
            + sse("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}")
            + sse("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"库存正常\"}}")
            + sse("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}")
            + sse("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}")
            + sse("message_stop", "{\"type\":\"message_stop\"}");
        String gemini = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"库存正常\"}]},\"finishReason\":\"STOP\",\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":3,\"candidatesTokenCount\":2,\"totalTokenCount\":5},\"modelVersion\":\"offline-model\"}";
        String ollama = "{\"model\":\"offline-model\",\"message\":{\"role\":\"assistant\",\"content\":\"库存正常\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":3,\"eval_count\":2}";
        return Stream.of(
            new Wire("OPENAI_CHAT_COMPLETIONS", "/chat/completions", "/max_tokens", openAi, openAiStream),
            new Wire("OPENAI_RESPONSES", "/responses", "/max_output_tokens", responses, responsesStream),
            new Wire("ANTHROPIC_MESSAGES", "/messages", "/max_tokens", anthropic, anthropicStream),
            new Wire("GEMINI_GENERATE_CONTENT", ":generateContent", "/generationConfig/maxOutputTokens", gemini, sse("", gemini)),
            new Wire("OLLAMA_NATIVE", "/api/chat", "/options/num_predict", ollama, ollama + "\n")
        );
    }

    private static String sse(String type, String body) { return (type.isEmpty() ? "" : "event: " + type + "\n") + "data: " + body + "\n\n"; }

    private static final class Probe implements ChatModelListener {
        private final AtomicInteger responses = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        @Override public void onResponse(ChatModelResponseContext context) { responses.incrementAndGet(); }
        @Override public void onError(ChatModelErrorContext context) { errors.incrementAndGet(); }
    }

    private record Captured(String path, JsonNode body) { }

    /** 每例使用独立端口和有界生命周期，确保不触碰既有用户服务。 */
    private static final class Endpoint implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final List<Captured> requests = new CopyOnWriteArrayList<>();
        private Endpoint(int status, String contentType, String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    requests.add(new Captured(exchange.getRequestURI().getPath(), JSON.readTree(exchange.getRequestBody())));
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }
        private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
