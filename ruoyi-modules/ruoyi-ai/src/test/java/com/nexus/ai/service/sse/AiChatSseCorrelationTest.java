package com.nexus.ai.service.sse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 同名类型说明：本文件同时使用 com.nexus.common.ai.domain.entity.AiMessage 和
// dev.langchain4j.data.message.AiMessage，后者保留全限定名以区分。

/** 捕获真实SSE信封，验证跨请求、回调线程和终态关联。 */
class AiChatSseCorrelationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void initializeOfflinePublisher() throws Exception {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtils.getProperty("sse.enabled", Boolean.class, true)).thenReturn(false);
            Class.forName(SseMessageUtils.class.getName());
        }
    }

    @Test
    void realEnvelopeCarriesServerRequestAndLosslessSessionWithOrderedTerminal() {
        List<Map<String, Object>> events = new ArrayList<>();
        try (var publisher = capture(events)) {
            var lifecycle = new AiChatSseLifecycle(7L, 9007199254740993L);
            lifecycle.progress(() -> AiSseMessageHelper.sendThinking(7L));
            lifecycle.progress(() -> AiSseMessageHelper.sendContentIncrement(7L, "answer"));
            assertTrue(lifecycle.complete(() -> AiSseMessageHelper.sendContent(7L, "answer")));
            lifecycle.fail(new IllegalStateException("late"));
            lifecycle.progress(() -> AiSseMessageHelper.sendContentIncrement(7L, "late"));
            assertEquals(4, events.size());
            for (int i = 0; i < events.size(); i++) {
                assertEquals(lifecycle.requestId(), events.get(i).get("requestId"));
                assertEquals("9007199254740993", events.get(i).get("sessionId"));
                assertEquals(i + 1, ((Number) events.get(i).get("sequence")).intValue());
            }
            assertEquals("AI_DONE", events.get(3).get("type"));
        }
    }

    @Test
    void callbackThreadUsesExplicitLifecycleRatherThanPreviousRequest() throws Exception {
        var lifecycle = new AiChatSseLifecycle(7L, 93L);
        var event = CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> events = new ArrayList<>();
            try (var publisher = capture(events)) {
                lifecycle.progress(() -> AiSseMessageHelper.sendContentIncrement(7L, "callback"));
                lifecycle.fail(new IllegalStateException("private detail"));
                return events;
            }
        }).get(10, TimeUnit.SECONDS);
        assertEquals(lifecycle.requestId(), event.get(0).get("requestId"));
        assertEquals("93", event.get(0).get("sessionId"));
        assertEquals(lifecycle.requestId(), event.get(1).get("requestId"));
        assertEquals(AiChatSseLifecycle.ERROR_MESSAGE, event.get(1).get("content"));
    }

    @Test
    void nestedLifecyclesRestoreOriginalScopeAndNeverRelabelAnotherUsersEvent() {
        List<Map<String, Object>> events = new ArrayList<>();
        try (var publisher = capture(events)) {
            var first = new AiChatSseLifecycle(7L, 91L);
            var second = new AiChatSseLifecycle(8L, 92L);
            first.progress(() -> {
                second.progress(() -> AiSseMessageHelper.sendContentIncrement(8L, "second"));
                AiSseMessageHelper.sendContentIncrement(7L, "first");
                AiSseMessageHelper.sendContentIncrement(8L, "other target");
            });
            assertEquals(second.requestId(), events.get(0).get("requestId"));
            assertEquals(first.requestId(), events.get(1).get("requestId"));
            assertFalse(events.get(2).containsKey("requestId"));
            AiSseMessageHelper.sendContentIncrement(7L, "outside");
            assertFalse(events.get(3).containsKey("requestId"));
        }
    }

    @Test
    void echoedClientNonceIsSeparateFromServerRequestAndLegacyCallersRemainCompatible() {
        List<Map<String, Object>> events = new ArrayList<>();
        String nonce = "ba07d9bb-66f5-40e6-9537-d5ab0c00cc6f";
        try (var publisher = capture(events)) {
            var first = new AiChatSseLifecycle(7L, 91L, nonce);
            var second = new AiChatSseLifecycle(7L, 91L, nonce);
            first.progress(() -> AiSseMessageHelper.sendThinking(7L));
            second.progress(() -> AiSseMessageHelper.sendThinking(7L));
            assertEquals(nonce, events.get(0).get("clientRequestId"));
            assertNotEquals(nonce, first.requestId());
            assertNotEquals(first.requestId(), second.requestId());
            assertEquals(1, ((Number) events.get(0).get("sequence")).intValue());
            assertEquals(1, ((Number) events.get(1).get("sequence")).intValue());
            assertThrows(IllegalArgumentException.class, () -> new AiChatSseLifecycle(7L, 91L, "bad"));
        }
    }

    @Test
    void doneCarriesOnlyPersistedMessageFromItsOwnSessionAsText() {
        List<Map<String, Object>> events = new ArrayList<>();
        try (var publisher = capture(events)) {
            var lifecycle = new AiChatSseLifecycle(7L, 91L);
            lifecycle.complete(() -> {
                AiChatSseLifecycle.persistedMessage(92L, 9007199254740993L);
                AiSseMessageHelper.sendContent(7L, "saved answer");
                AiChatSseLifecycle.persistedMessage(91L, 9007199254740995L);
            });
            assertFalse(events.get(0).containsKey("messageId"));
            assertEquals("9007199254740995", events.get(1).get("messageId"));
            assertEquals("AI_DONE", events.get(1).get("type"));
        }
    }

    @Test
    void actualStreamingCompletionPublishesDatabaseMessageId() throws Exception {
        var constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
        var dependencies = new HashMap<Class<?>, Object>();
        var arguments = new ArrayList<Object>();
        for (Class<?> type : constructor.getParameterTypes()) {
            Object dependency = type == Executor.class ? (Executor) Runnable::run : mock(type);
            dependencies.put(type, dependency);
            arguments.add(dependency);
        }
        var facade = (AiServiceFacade) constructor.newInstance(arguments.toArray());
        var streaming = mock(StreamingChatModel.class);
        var callback = new AtomicReference<StreamingChatResponseHandler>();
        doAnswer(
                        call -> {
                            callback.set(call.getArgument(1));
                            return null;
                        })
                .when(streaming)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(((ChatModelFactory) dependencies.get(ChatModelFactory.class))
                        .getStreamingChatModel(anyLong(), any(AiChatRequest.class)))
                .thenReturn(streaming);
        when(((PersistentChatMemoryStore) dependencies.get(PersistentChatMemoryStore.class))
                        .getMessages(any()))
                .thenReturn(List.of(UserMessage.from("question")));
        var messages = (AiMessageMapper) dependencies.get(AiMessageMapper.class);
        doAnswer(
                        call -> {
                            AiMessage message = call.getArgument(0);
                            message.setId(9007199254740993L);
                            return 1;
                        })
                .when(messages)
                .insert(any(AiMessage.class));
        var model = new AiModel();
        model.setId(9L);
        model.setModelName("offline");
        var request = new AiChatRequest();
        request.setContent("question");
        request.setClientRequestId("ba07d9bb-66f5-40e6-9537-d5ab0c00cc6f");
        List<Map<String, Object>> events = new ArrayList<>();
        try (var publisher = capture(events);
                var tenants = mockStatic(TenantHelper.class)) {
            tenants.when(() -> TenantHelper.dynamic(eq("000007"), any(Runnable.class)))
                    .thenAnswer(
                            call -> {
                                ((Runnable) call.getArgument(1)).run();
                                return null;
                            });
            ReflectionTestUtils.invokeMethod(
                    facade,
                    "invokeWithStreaming",
                    model,
                    request,
                    7L,
                    91L,
                    "000007",
                    "offline",
                    "000007");
            callback.get().onPartialResponse("answer");
            callback.get()
                    .onCompleteResponse(
                            ChatResponse.builder()
                                    .aiMessage(
                                            dev.langchain4j.data.message.AiMessage.from("answer"))
                                    .build());
            var done = events.get(events.size() - 1);
            assertEquals("AI_DONE", done.get("type"));
            assertEquals("9007199254740993", done.get("messageId"));
            assertEquals(request.getClientRequestId(), done.get("clientRequestId"));
            assertEquals("91", done.get("sessionId"));
        }
    }

    private MockedStatic<SseMessageUtils> capture(List<Map<String, Object>> events) {
        var publisher = mockStatic(SseMessageUtils.class);
        publisher.when(() -> SseMessageUtils.publishMessage(any(SseMessageDto.class))).thenAnswer(call -> {
            SseMessageDto event = call.getArgument(0);
            events.add(JSON.readValue(event.getMessage(), Map.class));
            return null;
        });
        return publisher;
    }
}
