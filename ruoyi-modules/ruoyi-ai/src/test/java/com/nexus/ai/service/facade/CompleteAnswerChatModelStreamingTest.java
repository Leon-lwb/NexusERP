package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.context.AiRequestContext;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CompleteAnswerChatModelStreamingTest {
    @AfterEach
    void clear() { AiRequestContext.clear(); }

    private ChatModel parameters(List<ChatModelListener> listeners) {
        return new ChatModel() {
            @Override public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder().temperature(0.1).maxOutputTokens(2048).build();
            }
            @Override public List<ChatModelListener> listeners() { return listeners; }
            @Override public ModelProvider provider() { return ModelProvider.OTHER; }
            @Override public ChatResponse doChat(ChatRequest request) { throw new AssertionError("must use real streaming delegate"); }
        };
    }
    private ChatResponse answer(String text, FinishReason finish) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text))
            .metadata(ChatResponseMetadata.builder().finishReason(finish).build()).build();
    }
    private ChatRequest request() { return ChatRequest.builder().messages(UserMessage.from("查询工单")).build(); }

    @Test
    void realPartialCallbacksArriveBeforeCompletionAndListenersRunOnTheTenantThread() {
        AiRequestContext.set("000007", 7L);
        Thread caller = Thread.currentThread();
        AtomicInteger responses = new AtomicInteger();
        List<String> chunks = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        ChatModelListener listener = new ChatModelListener() {
            @Override public void onResponse(ChatModelResponseContext response) {
                assertSame(caller, Thread.currentThread());
                assertEquals("000007", AiRequestContext.getTenantId());
                assertEquals(7L, AiRequestContext.getUserId());
                responses.incrementAndGet();
            }
        };
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                assertEquals(0.1, request.parameters().temperature());
                Thread callback = new Thread(() -> {
                    try {
                        handler.onPartialResponse("第一部分");
                        assertEquals(List.of("第一部分"), chunks);
                        assertEquals(0, responses.get());
                        handler.onPartialResponse("第二部分");
                        handler.onCompleteResponse(answer("第一部分第二部分", FinishReason.STOP));
                        handler.onPartialResponse("迟到的内容");
                    } catch (Throwable error) { callbackFailure.set(error); handler.onError(error); }
                }, "offline-model-callback");
                callback.start();
            }
        };
        CompleteAnswerChatModel model = new CompleteAnswerChatModel(parameters(List.of(listener)), streaming, chunks::add, ignored -> {});
        assertEquals("第一部分第二部分", model.chat(request()).aiMessage().text());
        assertNull(callbackFailure.get());
        assertEquals(List.of("第一部分", "第二部分"), chunks);
        assertEquals(1, responses.get());
    }

    @Test
    void realQueryAgentStillExecutesItsToolInTheCallerTenantContext() {
        AiRequestContext.set("000007", 7L);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger tools = new AtomicInteger();
        List<String> chunks = new ArrayList<>();
        class ReadTool {
            @Tool("读取测试工单，只读") public String readWorkOrder() {
                assertEquals("000007", AiRequestContext.getTenantId());
                assertEquals(7L, AiRequestContext.getUserId());
                tools.incrementAndGet();
                return "TEST-WO：计划 3 件";
            }
        }
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                if (calls.getAndIncrement() == 0) {
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("offline-call").name("readWorkOrder").arguments("{}").build()))
                        .metadata(ChatResponseMetadata.builder().finishReason(FinishReason.TOOL_EXECUTION).build()).build());
                } else {
                    handler.onPartialResponse("TEST-WO："); handler.onPartialResponse("计划 3 件");
                    handler.onCompleteResponse(answer("TEST-WO：计划 3 件", FinishReason.STOP));
                }
            }
        };
        CompleteAnswerChatModel model = new CompleteAnswerChatModel(parameters(List.of()), streaming, chunks::add, ignored -> {});
        ErpQueryAgent agent = AgenticServices.agentBuilder(ErpQueryAgent.class).chatModel(model).tools(new ReadTool()).build();
        assertEquals("TEST-WO：计划 3 件", agent.query("查询测试工单"));
        assertEquals(1, tools.get()); assertEquals(2, calls.get());
        assertEquals(List.of("TEST-WO：", "计划 3 件"), chunks);
    }

    @Test
    void lengthLimitRejectsCompletionEvenAfterSomeTextWasStreamed() {
        List<String> chunks = new ArrayList<>();
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("不完整");
                handler.onCompleteResponse(answer("不完整", FinishReason.LENGTH));
            }
        };
        AtomicInteger measured = new AtomicInteger();
        ChatModelListener listener = new ChatModelListener() {
            @Override public void onResponse(ChatModelResponseContext response) { measured.incrementAndGet(); }
        };
        CompleteAnswerChatModel model = new CompleteAnswerChatModel(parameters(List.of(listener)), streaming, chunks::add, ignored -> {});
        assertTrue(assertThrows(IllegalStateException.class, () -> model.chat(request())).getMessage().contains("不完整"));
        assertEquals(1, measured.get(), "实际已消耗的模型用量仍须进入原监听器");
        assertEquals(List.of("不完整"), chunks);
    }
    @Test
    void errorClosesPartialDeliveryAndKeepsTheModelError() {
        List<String> chunks = new ArrayList<>();
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("部分文字");
                handler.onError(new IllegalStateException("模型不可用"));
                handler.onPartialResponse("迟到的内容");
            }
        };
        CompleteAnswerChatModel model = new CompleteAnswerChatModel(parameters(List.of()), streaming, chunks::add, ignored -> {});
        assertEquals("模型不可用", assertThrows(IllegalStateException.class, () -> model.chat(request())).getMessage());
        assertEquals(List.of("部分文字"), chunks);
    }

    @Test
    void reasoningTokensRemainSeparateFromAnswerText() {
        List<String> content = new ArrayList<>(); List<String> reasoning = new ArrayList<>();
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialThinking(new PartialThinking("推理片段"));
                handler.onPartialResponse("回答");
                handler.onCompleteResponse(answer("回答", FinishReason.STOP));
            }
        };
        CompleteAnswerChatModel model = new CompleteAnswerChatModel(parameters(List.of()), streaming, content::add, reasoning::add);
        assertEquals("回答", model.chat(request()).aiMessage().text());
        assertEquals(List.of("回答"), content); assertEquals(List.of("推理片段"), reasoning);
    }

    @Test
    void reportAndDocumentRequestsKeepTheirExistingModelBoundary() {
        assertTrue(AiServiceFacade.isPureDataQuery("查询工单 TEST-WO"));
        assertTrue(AiServiceFacade.isPureDataQuery("查询全部生产工单"));
        for (String question : List.of("查询所有MCP工具的数据", "查询全部工单并生成柱状图", "根据知识库查询物料资料")) {
            assertFalse(AiServiceFacade.isPureDataQuery(question), question);
        }
    }
}
