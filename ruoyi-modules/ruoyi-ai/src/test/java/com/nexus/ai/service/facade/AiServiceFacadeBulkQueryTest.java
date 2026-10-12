package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.ai.observability.OutputChannel;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.prompt.PromptTemplateService;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 执行真实 Facade 的异步上下文、结果落库与 SSE 控制流，仅隔离模型/Agent 与持久化边界。 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AiServiceFacadeBulkQueryTest {
    private static final String BULK_QUESTION = "查询全部生产工单，逐条列出编号、计划数量、已完工数量、单位和状态";

    @ParameterizedTest
    @ValueSource(ints = {26, 250})
    void bulkAnswerIsDeliveredWithoutReencodingInPlannerJson(int rowCount) throws Exception {
        String answer = table(rowCount);
        exercise(BULK_QUESTION, answer, true, false, true, true);
    }

    @Test
    void truncatedAnswerMustNotBeSavedOrAnnouncedAsComplete() throws Exception {
        exercise(BULK_QUESTION, table(26), false, true, true, false);
    }

    @Test
    void emptyBulkAnswerMustNotBeAnnouncedAsComplete() throws Exception {
        exercise(BULK_QUESTION, " ", false, false, true, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"查询工单 TEST-WO-0001", "查询全部工单并生成柱状图", "查询所有MCP工具的数据"})
    void nonBulkOrCoordinatedQuestionsKeepSupervisor(String question) throws Exception {
        exercise(question, "边界测试的完整结果", false, false, false, true);
    }

    private static String table(int rows) {
        StringBuilder result = new StringBuilder("共 " + rows + " 条工单\n|工单|计划数量|完成数量|单位|状态|\n|---|---:|---:|---|---|\n");
        for (int i = 1; i <= rows; i++) {
            result.append("|TEST-WO-").append(String.format("%04d", i))
                .append('|').append(i * 3).append('|').append(i).append("|件|CREATED|\n");
        }
        return result.toString();
    }

    private void exercise(String content, String answer, boolean malformedPlanner,
                          boolean truncated, boolean direct, boolean success) throws Exception {
        Constructor<?> constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
        Map<Class<?>, Object> dependencies = new HashMap<>();
        for (Class<?> type : constructor.getParameterTypes()) {
            dependencies.computeIfAbsent(type, key -> key == Executor.class ? (Executor) Runnable::run : mock(key));
        }
        AiServiceFacade facade = (AiServiceFacade) constructor.newInstance(java.util.Arrays.stream(constructor.getParameterTypes())
            .map(dependencies::get).toArray());
        ChatModel rawModel = mock(ChatModel.class);
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        when(rawModel.defaultRequestParameters()).thenReturn(parameters);
        when(rawModel.supportedCapabilities()).thenReturn(Set.of());
        when(rawModel.provider()).thenReturn(ModelProvider.OTHER);
        when(rawModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
            .aiMessage(AiMessage.from(answer))
            .metadata(ChatResponseMetadata.builder().finishReason(truncated ? FinishReason.LENGTH : FinishReason.STOP).build())
            .build());
        when(((ChatModelFactory) dependencies.get(ChatModelFactory.class)).getAgentChatModel(9L)).thenReturn(rawModel);
        StreamingChatModel streaming = mock(StreamingChatModel.class);
        when(streaming.defaultRequestParameters()).thenReturn(parameters);
        when(((ChatModelFactory) dependencies.get(ChatModelFactory.class)).getStreamingChatModel(eq(9L), any(AiChatRequest.class))).thenReturn(streaming);
        doAnswer(call -> {
            StreamingChatResponseHandler handler = call.getArgument(1);
            assertEquals("000000", AiRequestContext.getTenantId());
            int split = Math.max(1, answer.length() / 2);
            handler.onPartialResponse(answer.substring(0, split));
            verify((AiMessageMapper) dependencies.get(AiMessageMapper.class), never()).insert(any(com.nexus.common.ai.domain.entity.AiMessage.class));
            handler.onPartialResponse(answer.substring(split));
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(answer))
                .metadata(ChatResponseMetadata.builder().finishReason(truncated ? FinishReason.LENGTH : FinishReason.STOP).build()).build());
            return null;
        }).when(streaming).doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(((PersistentChatMemoryStore) dependencies.get(PersistentChatMemoryStore.class)).getMessages(1L)).thenReturn(List.of());
        ToolProviderFactory tools = (ToolProviderFactory) dependencies.get(ToolProviderFactory.class);
        when(tools.getEnabledBuiltinToolObjectsByCategories(anyString())).thenReturn(List.of());
        PromptTemplateService prompts = (PromptTemplateService) dependencies.get(PromptTemplateService.class);
        when(prompts.renderQueryAgentContext()).thenReturn("保留 SQL 安全和租户隔离");
        when(prompts.renderReportAgentContext()).thenReturn("");
        when(prompts.renderSupervisorContext()).thenReturn("");

        AtomicReference<ChatModel> queryModel = new AtomicReference<>();
        AgentBuilder queryBuilder = mock(AgentBuilder.class, RETURNS_SELF);
        when(queryBuilder.chatModel(any(ChatModel.class))).thenAnswer(call -> {
            queryModel.set(call.getArgument(0));
            return queryBuilder;
        });
        ErpQueryAgent query = mock(ErpQueryAgent.class);
        when(queryBuilder.build()).thenReturn(query);
        when(query.query(anyString())).thenAnswer(call -> {
            assertEquals("000000", AiRequestContext.getTenantId(), "查询在真实请求租户上下文中执行");
            assertEquals(7L, AiRequestContext.getUserId());
            ChatResponse response = queryModel.get().chat(ChatRequest.builder()
                .messages(UserMessage.from(call.getArgument(0, String.class))).build());
            return response.aiMessage().text();
        });
        AgentBuilder reportBuilder = mock(AgentBuilder.class, RETURNS_SELF);
        when(reportBuilder.build()).thenReturn(mock(ErpReportAgent.class));
        SupervisorAgent supervisor = mock(SupervisorAgent.class);
        if (malformedPlanner) {
            when(supervisor.invoke(anyString())).thenThrow(new IllegalStateException("Failed to parse done AgentInvocation JSON"));
        } else {
            when(supervisor.invoke(anyString())).thenAnswer(call -> query.query(call.getArgument(0)));
        }
        SupervisorAgentService supervisorBuilder = mock(SupervisorAgentService.class, RETURNS_SELF);
        when(supervisorBuilder.build()).thenReturn(supervisor);
        OutputChannel channel = mock(OutputChannel.class);
        AiModel model = new AiModel();
        model.setId(9L);
        model.setModelName("offline-regression-model");
        AiChatRequest request = new AiChatRequest();
        request.setContent(content);
        request.setMode("agent");

        try (var agents = mockStatic(AgenticServices.class);
             var sse = mockStatic(AiSseMessageHelper.class);
             var channels = mockStatic(OutputChannel.class);
             var tenants = mockStatic(TenantHelper.class)) {
            agents.when(() -> AgenticServices.agentBuilder(ErpQueryAgent.class)).thenReturn(queryBuilder);
            agents.when(() -> AgenticServices.agentBuilder(ErpReportAgent.class)).thenReturn(reportBuilder);
            agents.when(AgenticServices::supervisorBuilder).thenReturn(supervisorBuilder);
            channels.when(() -> OutputChannel.create(anyString())).thenReturn(channel);
            tenants.when(() -> TenantHelper.dynamic(eq("000000"), any(Runnable.class))).thenAnswer(call -> {
                ((Runnable) call.getArgument(1)).run();
                return null;
            });
            ReflectionTestUtils.invokeMethod(facade, "invokeWithAgent", model, request, 7L, 1L, "000000", "offline-test", "000000");
            AiMessageMapper mapper = (AiMessageMapper) dependencies.get(AiMessageMapper.class);
            if (success) {
                if (AiServiceFacade.isPureDataQuery(content)) {
                    int split = Math.max(1, answer.length() / 2);
                    sse.verify(() -> AiSseMessageHelper.sendContentIncrement(7L, answer.substring(0, split)));
                    sse.verify(() -> AiSseMessageHelper.sendContentIncrement(7L, answer.substring(split)));
                }
                sse.verify(() -> AiSseMessageHelper.sendContent(7L, answer));
                sse.verify(() -> AiSseMessageHelper.sendDone(7L));
                ArgumentCaptor<com.nexus.common.ai.domain.entity.AiMessage> persisted = ArgumentCaptor.forClass(com.nexus.common.ai.domain.entity.AiMessage.class);
                verify(mapper).insert(persisted.capture());
                assertEquals(answer, persisted.getValue().getContent(), "完整结果原样持久化，不能截断成摘要");
                verify(channel).complete();
                verify(channel, never()).completeWithError(any());
            } else {
                sse.verify(() -> AiSseMessageHelper.sendContent(anyLong(), anyString()), never());
                sse.verify(() -> AiSseMessageHelper.sendDone(anyLong()), never());
                verify(mapper, never()).insert(any(com.nexus.common.ai.domain.entity.AiMessage.class));
                verify(channel).completeWithError(any());
                sse.verify(() -> AiSseMessageHelper.sendError(eq(7L), anyString()));
            }
            if (direct) {
                agents.verify(AgenticServices::supervisorBuilder, never());
                verify(supervisor, never()).invoke(anyString());
                verify(query).query(contains(content));
                assertSame(parameters, queryModel.get().defaultRequestParameters());
            } else {
                verify(supervisor).invoke(anyString());
            }
            verify(queryBuilder, never()).toolProvider(any());
        }
        assertNull(AiRequestContext.getTenantId(), "异步任务结束必须清理租户上下文");
    }
}