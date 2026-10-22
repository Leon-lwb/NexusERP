package com.nexus.ai.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.ai.service.sse.AiChatSseLifecycle;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.IAiSessionService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.handler.SaTokenExceptionHandler;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.config.JacksonConfig;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// 同名类型说明：本文件同时使用 com.nexus.common.ai.domain.entity.AiMessage 和
// dev.langchain4j.data.message.AiMessage，后者保留全限定名以区分。

/** 使用合成端口贯通真实控制器、异步门面和序列化 SSE，供真实 Vue 处理函数离线重放。 */
class AiChatRoundTripFixtureTest {
    private static final String TENANT = "000007";
    private static final long USER = 7L;
    private static final ObjectMapper JSON =
            new ObjectMapper().registerModule(new JacksonConfig().bigNumberModule());

    /** 沿用现有 SSE 夹具的离线初始化方式，避免静态初始化读取真实 Spring 或 Redis。 */
    @BeforeAll
    static void initializeOfflinePublisher() throws Exception {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtils.getProperty("sse.enabled", Boolean.class, true))
                    .thenReturn(false);
            Class.forName(SseMessageUtils.class.getName());
        }
    }

    @Test
    @Timeout(45)
    void captureAuthorizedDeniedInterleavedAndFailedRoundTrips() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.denied("denied-controller", "10000000-0000-4000-8000-000000000001", false);
            fixture.denied("denied-facade", "10000000-0000-4000-8000-000000000002", true);

            // 强制工作线程先完成，随后控制器才能返回 HTTP ACK，稳定覆盖新会话回执竞态。
            fixture.completeBeforeAck.set(true);
            var success = fixture.send("ordinary-success", "10000000-0000-4000-8000-000000000003", "普通用户离线问候");
            assertEquals("AI_DONE", fixture.eventsFor(success).get(fixture.eventsFor(success).size() - 1).path("type").asText());
            fixture.completeBeforeAck.set(false);

            var sessionA = fixture.send("session-a", "10000000-0000-4000-8000-000000000004", "甲会话问候");
            var sessionB = fixture.send("session-b", "10000000-0000-4000-8000-000000000005", "乙会话问候");
            fixture.awaitModel(sessionA);
            fixture.awaitModel(sessionB);
            assertNotEquals(sessionA.sessionId, sessionB.sessionId);
            fixture.callback(sessionA, handler -> handler.onPartialResponse("甲的"));
            fixture.callback(sessionB, handler -> handler.onPartialResponse("乙的"));
            fixture.callback(sessionA, handler -> handler.onPartialResponse("回答"));
            fixture.callback(sessionB, handler -> handler.onPartialResponse("回答"));
            fixture.callback(sessionA, handler -> complete(handler, "甲的回答"));
            fixture.callback(sessionB, handler -> complete(handler, "乙的回答"));

            var failed = fixture.send("model-error", "10000000-0000-4000-8000-000000000006", "失败场景问候");
            fixture.awaitModel(failed);
            fixture.callback(failed, handler -> handler.onPartialResponse("未完成"));
            fixture.callback(failed, handler -> handler.onError(new IllegalStateException("synthetic-provider-failure")));

            // 重复和迟到的 SDK 回调不能改变已捕获的成功或失败终态。
            int beforeLate = fixture.wire.size();
            fixture.callback(sessionA, handler -> handler.onError(new IllegalStateException("late-error")));
            fixture.callback(sessionB, handler -> handler.onPartialResponse("迟到正文"));
            fixture.callback(failed, handler -> complete(handler, "不应保存"));
            assertEquals(beforeLate, fixture.wire.size());
            fixture.verifyCleanWorkers();
            fixture.verifyAndWrite();
        }
    }

    /** 完成合成模型响应；不访问真实模型、网络或凭证。 */
    private static void complete(StreamingChatResponseHandler handler, String answer) {
        handler.onCompleteResponse(ChatResponse.builder()
            .aiMessage(dev.langchain4j.data.message.AiMessage.from(answer)).build());
    }

    /** 每条场景保存真实 HTTP 正文及请求，SSE 原始信封由统一时间线保留。 */
    private static final class Scenario {
        final String name;
        final AiChatRequest request;
        final int status;
        final String responseJson;
        final Long sessionId;

        Scenario(String name, AiChatRequest request, int status, String responseJson) throws Exception {
            this.name = name;
            this.request = request;
            this.status = status;
            this.responseJson = responseJson;
            JsonNode response = JSON.readTree(responseJson);
            this.sessionId = response.path("code").asInt() == 200 ? response.path("data").asLong() : null;
        }
    }

    /** 有界线程池替换外部端口，业务控制器、门面、生命周期与 SSE 编码保持真实执行。 */
    private static final class Fixture implements AutoCloseable {
        final Map<Class<?>, Object> dependencies = new LinkedHashMap<>();
        final List<Scenario> scenarios = new ArrayList<>();
        final List<String> wire = new CopyOnWriteArrayList<>();
        final List<AiMessage> stored = new CopyOnWriteArrayList<>();
        final Map<Long, StreamingChatResponseHandler> handlers = new ConcurrentHashMap<>();
        final Map<Long, CountDownLatch> modelReady = new ConcurrentHashMap<>();
        final List<Throwable> workerFailures = new CopyOnWriteArrayList<>();
        final AtomicBoolean completeBeforeAck = new AtomicBoolean();
        final AtomicBoolean annotationAllowed = new AtomicBoolean(true);
        final AtomicBoolean facadeAllowed = new AtomicBoolean(true);
        final AtomicLong sessionIds = new AtomicLong(2100000000000000100L);
        final AtomicLong messageIds = new AtomicLong(2100000000000000200L);
        final ThreadLocal<String> storageTenant = new ThreadLocal<>();
        final ThreadLocal<Boolean> contextProbe = new ThreadLocal<>();
        final AtomicLong workerChecks = new AtomicLong();
        final AtomicLong unscopedSseProbes = new AtomicLong();
        final Set<Long> workerThreads = ConcurrentHashMap.newKeySet();
        final Thread requestThread = Thread.currentThread();
        final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "ai-roundtrip-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        final MockedStatic<SaManager> manager;
        final MockedStatic<StpUtil> authorization;
        final MockedStatic<LoginHelper> login;
        final MockedStatic<SseMessageUtils> requestPublisher;
        final StpLogic logic = mock(StpLogic.class);
        final MockMvc mvc;

        Fixture() throws Exception {
            workers.prestartAllCoreThreads();
            var constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
            List<Object> arguments = new ArrayList<>();
            for (Class<?> type : constructor.getParameterTypes()) {
                Object dependency = type == Executor.class ? (Executor) this::execute : mock(type);
                dependencies.put(type, dependency);
                arguments.add(dependency);
            }
            var facade = (AiServiceFacade) constructor.newInstance(arguments.toArray());
            var model = new AiModel();
            model.setId(9L);
            model.setModelName("synthetic-roundtrip");
            when(dep(IAiModelService.class).selectDefaultModel()).thenReturn(model);
            doAnswer(call -> {
                AiSession session = call.getArgument(0);
                assertEquals(USER, session.getUserId());
                session.setId(sessionIds.incrementAndGet());
                session.setTenantId(TENANT);
                return 1;
            }).when(dep(AiSessionMapper.class)).insert(any(AiSession.class));
            doAnswer(call -> {
                AiMessage message = call.getArgument(0);
                if ("ASSISTANT".equals(message.getRole())) {
                    assertNotSame(requestThread, Thread.currentThread());
                    assertEquals(TENANT, storageTenant.get(), "异步写入必须恢复含前导零的租户身份");
                }
                message.setId(messageIds.incrementAndGet());
                message.setTenantId(TENANT);
                stored.add(message);
                return 1;
            }).when(dep(AiMessageMapper.class)).insert(any(AiMessage.class));
            when(dep(PersistentChatMemoryStore.class).getMessages(any())).thenAnswer(call -> {
                Long sessionId = call.getArgument(0);
                String question = stored.stream().filter(message -> sessionId.equals(message.getSessionId()))
                    .filter(message -> "USER".equals(message.getRole())).findFirst().orElseThrow().getContent();
                return List.of(UserMessage.from(question));
            });
            StreamingChatModel streaming = mock(StreamingChatModel.class);
            when(dep(ChatModelFactory.class).getStreamingChatModel(anyLong(), any(AiChatRequest.class))).thenReturn(streaming);
            doAnswer(call -> {
                assertNotSame(requestThread, Thread.currentThread());
                assertEquals(TENANT, AiRequestContext.getTenantId());
                assertEquals(USER, AiRequestContext.getUserId());
                StreamingChatResponseHandler handler = call.getArgument(1);
                handlers.put(AiRequestContext.getSessionId(), handler);
                modelReady.computeIfAbsent(AiRequestContext.getSessionId(), ignored -> new CountDownLatch(1)).countDown();
                if (completeBeforeAck.get()) {
                    handler.onPartialResponse("离线");
                    handler.onPartialResponse("回答");
                    complete(handler, "离线回答");
                }
                return null;
            }).when(streaming).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

            manager = mockStatic(SaManager.class);
            manager.when(() -> SaManager.getStpLogic(anyString(), eq(false))).thenReturn(logic);
            doAnswer(call -> {
                assertArrayEquals(new String[]{"ai:assistant:send"}, (String[]) call.getRawArguments()[0]);
                if (!annotationAllowed.get()) throw new NotPermissionException("ai:assistant:send");
                return null;
            }).when(logic).checkPermissionAnd(any(String[].class));
            authorization = mockStatic(StpUtil.class);
            authorization.when(() -> StpUtil.checkPermission("ai:assistant:send")).thenAnswer(call -> {
                if (!facadeAllowed.get()) throw new NotPermissionException("ai:assistant:send");
                return null;
            });
            login = mockStatic(LoginHelper.class);
            login.when(LoginHelper::getUserId).thenReturn(USER);
            login.when(LoginHelper::getTenantId).thenReturn(TENANT);
            login.when(LoginHelper::getUsername).thenReturn("synthetic-ordinary-user");
            requestPublisher = publisher();
            mvc = MockMvcBuilders.standaloneSetup(new AiChatController(facade, dep(AiMessageMapper.class), mock(IAiSessionService.class)))
                .setControllerAdvice(new SaTokenExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .addInterceptors(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        if (handler instanceof HandlerMethod method) {
                            SaAnnotationStrategy.instance.checkMethodAnnotation.accept(method.getMethod());
                        }
                        return true;
                    }
                }).build();
        }

        private <T> T dep(Class<T> type) { return type.cast(dependencies.get(type)); }

        /** 使用真实 Sa-Token 注解分发器及门面权限检查，拒绝发生在模型和持久化之前。 */
        void denied(String name, String nonce, boolean allowAnnotation) throws Exception {
            annotationAllowed.set(allowAnnotation);
            facadeAllowed.set(false);
            Scenario scenario = send(name, nonce, "未授权问候");
            assertEquals(403, JSON.readTree(scenario.responseJson).path("code").asInt());
            for (Object dependency : dependencies.values()) {
                if (!(dependency instanceof Executor)) verifyNoInteractions(dependency);
            }
            assertTrue(wire.isEmpty());
            assertEquals(0, workers.getTaskCount());
            annotationAllowed.set(true);
            facadeAllowed.set(true);
        }

        Scenario send(String name, String nonce, String question) throws Exception {
            AiChatRequest request = new AiChatRequest();
            request.setContent(question);
            request.setClientRequestId(nonce);
            request.setMode("chat");
            var response = mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request))).andReturn().getResponse();
            var scenario = new Scenario(name, request, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
            scenarios.add(scenario);
            if (!name.startsWith("denied-")) {
                assertEquals(200, JSON.readTree(scenario.responseJson).path("code").asInt());
                assertTrue(JSON.readTree(scenario.responseJson).path("data").isTextual(), "HTTP 雪花ID必须保留字符串精度");
            }
            return scenario;
        }

        /** 主请求与工作线程各自安装发布端口，仅截获真实编码后的 DTO，不调用 Redis。 */
        MockedStatic<SseMessageUtils> publisher() {
            var publisher = mockStatic(SseMessageUtils.class);
            publisher.when(() -> SseMessageUtils.publishMessage(any(SseMessageDto.class))).thenAnswer(call -> {
                SseMessageDto dto = call.getArgument(0);
                assertEquals(List.of(USER), dto.getUserIds());
                if (Boolean.TRUE.equals(contextProbe.get())) {
                    JsonNode event = JSON.readTree(dto.getMessage());
                    for (String field : List.of("requestId", "clientRequestId", "sessionId", "sequence", "messageId")) {
                        assertFalse(event.has(field), "回调结束后不得残留 SSE 关联：" + field);
                    }
                    unscopedSseProbes.incrementAndGet();
                } else {
                    wire.add(JSON.writeValueAsString(dto));
                }
                return null;
            });
            return publisher;
        }

        /** 异步池容量和所有等待均有上限；捕获工作线程异常，避免 CompletableFuture 吞掉断言。 */
        void execute(Runnable task) {
            CountDownLatch finished = dispatch(task);
            if (completeBeforeAck.get()) await(finished);
        }

        CountDownLatch dispatch(Runnable task) {
            var finished = new CountDownLatch(1);
            workers.execute(() -> {
                try (var sse = publisher(); var tenants = mockStatic(TenantHelper.class)) {
                    assertCleanContext();
                    tenants.when(() -> TenantHelper.dynamic(eq(TENANT), any(Runnable.class))).thenAnswer(call -> {
                        storageTenant.set(call.getArgument(0));
                        try { ((Runnable) call.getArgument(1)).run(); }
                        finally { storageTenant.remove(); }
                        return null;
                    });
                    task.run();
                    assertCleanContext();
                    probeUnscopedSse();
                    workerThreads.add(Thread.currentThread().getId());
                    workerChecks.incrementAndGet();
                } catch (Throwable failure) {
                    workerFailures.add(failure);
                } finally {
                    AiRequestContext.clear();
                    finished.countDown();
                }
            });
            return finished;
        }

        /** 在合成端口清理之前核对全部请求身份及用量字段，避免测试自身清理掩盖业务泄漏。 */
        void assertCleanContext() {
            assertAll("任务与回调结束后必须清空请求上下文",
                () -> assertNull(AiRequestContext.getTenantId()),
                () -> assertNull(AiRequestContext.getUserId()),
                () -> assertNull(AiRequestContext.getUserName()),
                () -> assertNull(AiRequestContext.getSessionId()),
                () -> assertNull(AiRequestContext.getModelId()),
                () -> assertNull(AiRequestContext.getModelName()),
                () -> assertNull(AiRequestContext.getKnowledgeId()),
                () -> assertNull(AiRequestContext.getLastInputTokens()),
                () -> assertNull(AiRequestContext.getLastOutputTokens()),
                () -> assertNull(AiRequestContext.getLastModelId()),
                () -> assertNull(storageTenant.get()));
        }

        /** 通过真实编码器发出单独探针，验证线程已不再绑定上一轮事件；探针不进入业务时间线。 */
        void probeUnscopedSse() {
            contextProbe.set(true);
            try { AiSseMessageHelper.sendThinking(USER); }
            finally { contextProbe.remove(); }
        }

        /** 同时占住两个工作线程，确保池中每个复用线程都实际完成清理检查。 */
        void verifyCleanWorkers() {
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            Runnable probe = () -> { entered.countDown(); await(release); };
            CountDownLatch first = dispatch(probe);
            CountDownLatch second = dispatch(probe);
            try { await(entered); }
            finally { release.countDown(); }
            await(first);
            await(second);
            assertEquals(2, workerThreads.size());
        }

        void awaitModel(Scenario scenario) {
            await(modelReady.computeIfAbsent(scenario.sessionId, ignored -> new CountDownLatch(1)));
            assertNotNull(handlers.get(scenario.sessionId), "真实门面必须调用合成流式模型");
            assertTrue(workerFailures.isEmpty(), () -> workerFailures.toString());
        }

        void callback(Scenario scenario, Consumer<StreamingChatResponseHandler> callback) {
            await(dispatch(() -> callback.accept(handlers.get(scenario.sessionId))));
            assertTrue(workerFailures.isEmpty(), () -> workerFailures.toString());
        }

        void await(CountDownLatch latch) {
            try { assertTrue(latch.await(5, TimeUnit.SECONDS), "异步任务必须在五秒内完成"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            assertTrue(workerFailures.isEmpty(), () -> workerFailures.toString());
        }

        List<JsonNode> eventsFor(Scenario scenario) throws Exception {
            List<JsonNode> result = new ArrayList<>();
            for (String dtoJson : wire) {
                JsonNode event = JSON.readTree(JSON.readTree(dtoJson).path("message").asText());
                if (scenario.request.getClientRequestId().equals(event.path("clientRequestId").asText())) result.add(event);
            }
            return result;
        }

        /** 只有全部断言通过后生成本轮产物；输出路径缺失也不跳过测试，使用临时文件并即时删除。 */
        void verifyAndWrite() throws Exception {
            assertTrue(workerFailures.isEmpty(), () -> workerFailures.toString());
            assertCleanContext();
            probeUnscopedSse();
            assertEquals(workerChecks.get() + 1, unscopedSseProbes.get());
            List<Map<String, Object>> captures = new ArrayList<>();
            for (Scenario scenario : scenarios) {
                List<JsonNode> events = eventsFor(scenario);
                var capture = new LinkedHashMap<String, Object>();
                capture.put("name", scenario.name);
                capture.put("request", scenario.request);
                capture.put("http", Map.of("status", scenario.status, "bodyJson", scenario.responseJson));
                List<Map<String, Object>> persisted = stored.stream()
                    .filter(message -> message.getSessionId().equals(scenario.sessionId))
                    .map(message -> Map.<String, Object>of("id", message.getId().toString(), "sessionId", message.getSessionId().toString(),
                        "tenantId", message.getTenantId(), "role", message.getRole(), "content", message.getContent())).toList();
                capture.put("persisted", persisted);
                if (scenario.sessionId == null) {
                    assertTrue(events.isEmpty());
                    assertTrue(persisted.isEmpty());
                } else {
                    assertFalse(events.isEmpty());
                    String requestId = events.get(0).path("requestId").asText();
                    UUID.fromString(requestId);
                    for (int i = 0; i < events.size(); i++) {
                        JsonNode event = events.get(i);
                        assertEquals(i + 1, event.path("sequence").asInt());
                        assertEquals(requestId, event.path("requestId").asText());
                        assertEquals(scenario.sessionId.toString(), event.path("sessionId").asText());
                    }
                    JsonNode terminal = events.get(events.size() - 1);
                    assertEquals(1, events.stream().filter(event -> List.of("AI_DONE", "AI_ERROR").contains(event.path("type").asText())).count());
                    if ("model-error".equals(scenario.name)) {
                        assertEquals("AI_ERROR", terminal.path("type").asText());
                        assertEquals(AiChatSseLifecycle.ERROR_MESSAGE, terminal.path("content").asText());
                        assertEquals(1, persisted.size());
                    } else {
                        assertEquals("AI_DONE", terminal.path("type").asText());
                        assertEquals(2, persisted.size());
                        assertEquals(persisted.get(persisted.size() - 1).get("id"), terminal.path("messageId").asText());
                        assertTrue(terminal.path("messageId").isTextual());
                    }
                }
                captures.add(capture);
            }
            verify(logic, times(6)).checkPermissionAnd("ai:assistant:send");
            authorization.verify(() -> StpUtil.checkPermission("ai:assistant:send"), times(5));
            verify(dep(IAiModelService.class), times(4)).selectDefaultModel();
            verify(dep(AiSessionMapper.class), times(4)).insert(any(AiSession.class));
            verify(dep(AiMessageMapper.class), times(7)).insert(any(AiMessage.class));
            Map<String, Object> artifact = Map.of("formatVersion", 1, "synthetic", true,
                "principal", Map.of("userId", Long.toString(USER), "tenantId", TENANT,
                    "permissions", List.of("ai:assistant:send"), "superAdmin", false),
                "executor", Map.of("threads", 2, "queueCapacity", 4, "ordinaryCompletedBeforeAck", true),
                "contextCleanup", Map.of("workerTasks", workerChecks.get(), "workerThreads", workerThreads.size(),
                    "requestThread", true, "unscopedSseProbes", unscopedSseProbes.get()),
                "scenarios", captures, "timeline", List.copyOf(wire));
            String output = System.getProperty("nexus.ai.roundtrip.output");
            Path path = output == null || output.isBlank() ? Files.createTempFile("nexus-ai-roundtrip-", ".json") : Path.of(output);
            try {
                if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(artifact), StandardCharsets.UTF_8);
                assertEquals(6, JSON.readTree(Files.readString(path)).path("scenarios").size());
            } finally {
                if (output == null || output.isBlank()) Files.deleteIfExists(path);
            }
        }

        @Override
        public void close() throws Exception {
            try (requestPublisher; login; authorization; manager) {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                AiRequestContext.clear();
            }
        }
    }
}
