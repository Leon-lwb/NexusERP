package com.nexus.ai.service.facade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import cn.dev33.satoken.stp.StpUtil;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.ai.observability.OutputChannel;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.rag.RetrieverService;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

// 同名类型说明：本文件同时使用 com.nexus.common.ai.domain.entity.AiMessage 和
// dev.langchain4j.data.message.AiMessage，后者保留全限定名以区分。

@SuppressWarnings({"rawtypes", "unchecked"})
class AiServiceFacadeTerminalTest {
    private AiServiceFacade facade;
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final AtomicReference<StreamingChatResponseHandler> callback = new AtomicReference<>();
    private MockedStatic<AiSseMessageHelper> sse;
    private final List<String> errors = new ArrayList<>();
    private MockedStatic<TenantHelper> tenant;
    private final AiModel model = new AiModel();
    private final AiChatRequest request = new AiChatRequest();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.nexus.ai");

    @BeforeEach
    void prepare() throws Exception {
        var constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
        List<Object> args = new ArrayList<>();
        for (Class<?> type : constructor.getParameterTypes()) {
            Object value = type == Executor.class ? (Executor) Runnable::run : mock(type);
            args.add(value);
            dependencies.put(type, value);
        }
        facade = (AiServiceFacade) constructor.newInstance(args.toArray());
        StreamingChatModel streaming = mock(StreamingChatModel.class);
        doAnswer(call -> { callback.set(call.getArgument(1)); return null; })
            .when(streaming).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(dep(ChatModelFactory.class).getStreamingChatModel(anyLong(), any(AiChatRequest.class))).thenReturn(streaming);
        when(dep(PersistentChatMemoryStore.class).getMessages(any()))
                .thenReturn(List.of(UserMessage.from("问题")));
        when(dep(RetrieverService.class).augmentPrompt(anyString())).thenReturn("增强问题");
        model.setId(9L);
        model.setModelName("离线模型");
        request.setContent("问题");
        sse = mockStatic(AiSseMessageHelper.class);
        sse.when(() -> AiSseMessageHelper.sendError(anyLong(), anyString())).thenAnswer(call -> {
            errors.add(call.getArgument(1)); return null;
        });
        tenant = mockStatic(TenantHelper.class);
        tenant.when(() -> TenantHelper.dynamic(eq("000007"), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run(); return null;
        });
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(logs);
        logs.stop();
        tenant.close();
        sse.close();
        AiRequestContext.clear();
    }

    private <T> T dep(Class<T> type) { return type.cast(dependencies.get(type)); }
    private void start(String path) {
        ReflectionTestUtils.invokeMethod(facade, path, model, request, 7L, 1L, "000007", "测试用户", "000007");
    }
    private ChatResponse answer() {
        return ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("完整答案")).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invokeWithStreaming", "invokeWithRag"})
    void successfulResponseIgnoresDuplicateCompletionAndLateError(String path) {
        start(path);
        callback.get().onPartialResponse("完整答案");
        callback.get().onCompleteResponse(answer());
        callback.get().onCompleteResponse(answer());
        callback.get().onError(new IllegalStateException("迟到的内部错误"));
        callback.get().onPartialResponse("迟到正文");
        verify(dep(AiMessageMapper.class), times(1)).insert(any(AiMessage.class));
        sse.verify(() -> AiSseMessageHelper.sendDone(7L), times(1));
        assertTrue(errors.isEmpty(), "不得发送失败事件");
        sse.verify(() -> AiSseMessageHelper.sendContentIncrement(7L, "迟到正文"), never());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invokeWithStreaming", "invokeWithRag"})
    void failureHasOneFixedChineseErrorAndLogsRequestId(String path) {
        start(path);
        callback.get().onError(new IllegalStateException("密钥与供应商内部错误"));
        callback.get().onError(new IllegalStateException("重复错误"));
        callback.get().onCompleteResponse(answer());
        verify(dep(AiMessageMapper.class), never()).insert(any(AiMessage.class));
        assertEquals(List.of("AI 对话失败，请稍后重试。"), errors);
        sse.verify(() -> AiSseMessageHelper.sendDone(anyLong()), never());
        assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().matches("(?s).*requestId=[a-f0-9-]{36}.*")));
    }

    @Test
    void persistenceFailureIsContainedInCompletionCallback() {
        start("invokeWithStreaming");
        doThrow(new IllegalStateException("数据库内部错误")).when(dep(AiMessageMapper.class)).insert(any(AiMessage.class));
        assertDoesNotThrow(() -> callback.get().onCompleteResponse(answer()));
        callback.get().onError(new IllegalStateException("SDK 再次报告"));
        assertEquals(List.of("AI 对话失败，请稍后重试。"), errors);
        sse.verify(() -> AiSseMessageHelper.sendDone(anyLong()), never());
    }

    @Test
    void invalidSessionSendHasExplicitNotFoundMeaning() {
        try (var login = mockStatic(LoginHelper.class);
                var permissions = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            when(dep(IAiModelService.class).selectDefaultModel()).thenReturn(model);
            request.setSessionId(9007199254740993L);
            ServiceException error = assertThrows(ServiceException.class, () -> facade.chat(request));
            assertEquals(404, error.getCode());
            verify(dep(AiMessageMapper.class), never()).insert(any(AiMessage.class));
            assertTrue(errors.isEmpty(), "不得发送失败事件");
        }
    }

    @Test
    void sameUserIdFromAnotherTenantCannotAppendToForeignSession() {
        try (var login = mockStatic(LoginHelper.class);
                var permissions = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");
            when(dep(IAiModelService.class).selectDefaultModel()).thenReturn(model);
            request.setSessionId(9007199254740993L);
            var foreign = new AiSession();
            foreign.setUserId(7L);
            foreign.setTenantId("000008");
            when(dep(AiSessionMapper.class).selectById(9007199254740993L)).thenReturn(foreign);
            ServiceException error = assertThrows(ServiceException.class, () -> facade.chat(request));
            assertEquals(404, error.getCode());
            verifyNoInteractions(dep(AiMessageMapper.class));
            verify(dep(AiSessionMapper.class), never()).insert(any(AiSession.class));
            verifyNoInteractions(dep(ChatModelFactory.class));
            assertTrue(errors.isEmpty());
        }
    }

    @Test
    void rejectedAgentSubmissionReleasesTheChannelAndSendsOneError() {
        Map<?, ?> registry = (Map<?, ?>) ReflectionTestUtils.getField(OutputChannel.class, "REGISTRY");
        Set<?> before = new HashSet<>(registry.keySet());
        request.setContent("统计库存报表");
        ReflectionTestUtils.setField(
                facade,
                "aiExecutor",
                (Executor)
                        task -> {
                            throw new RejectedExecutionException("线程池已满");
                        });
        AgentBuilder query = mock(AgentBuilder.class, RETURNS_SELF);
        AgentBuilder report = mock(AgentBuilder.class, RETURNS_SELF);
        when(query.build()).thenReturn(mock(ErpQueryAgent.class));
        when(report.build()).thenReturn(mock(ErpReportAgent.class));
        SupervisorAgentService builder = mock(SupervisorAgentService.class, RETURNS_SELF);
        when(builder.build()).thenReturn(mock(SupervisorAgent.class));
        try (var agents = mockStatic(AgenticServices.class)) {
            agents.when(() -> AgenticServices.agentBuilder(ErpQueryAgent.class)).thenReturn(query);
            agents.when(() -> AgenticServices.agentBuilder(ErpReportAgent.class)).thenReturn(report);
            agents.when(AgenticServices::supervisorBuilder).thenReturn(builder);
            start("invokeWithAgent");
        }
        assertEquals(List.of("AI 对话失败，请稍后重试。"), errors);
        assertEquals(before, registry.keySet(), "拒绝任务后不能泄漏已注册的事件通道");
    }

    @Test
    void agentFailureUsesOneSafeErrorEvenWhenChannelIsDrained() {
        request.setContent("统计库存报表");
        when(dep(ToolProviderFactory.class).getEnabledBuiltinToolObjectsByCategories(anyString())).thenReturn(List.of());
        AgentBuilder query = mock(AgentBuilder.class, RETURNS_SELF);
        AgentBuilder report = mock(AgentBuilder.class, RETURNS_SELF);
        when(query.build()).thenReturn(mock(ErpQueryAgent.class));
        when(report.build()).thenReturn(mock(ErpReportAgent.class));
        SupervisorAgent supervisor = mock(SupervisorAgent.class);
        when(supervisor.invoke(anyString())).thenThrow(new IllegalStateException("AgentInvocation 解析内部错误"));
        SupervisorAgentService builder = mock(SupervisorAgentService.class, RETURNS_SELF);
        when(builder.build()).thenReturn(supervisor);
        try (var agents = mockStatic(AgenticServices.class)) {
            agents.when(() -> AgenticServices.agentBuilder(ErpQueryAgent.class)).thenReturn(query);
            agents.when(() -> AgenticServices.agentBuilder(ErpReportAgent.class)).thenReturn(report);
            agents.when(AgenticServices::supervisorBuilder).thenReturn(builder);
            start("invokeWithAgent");
        }
        assertEquals(List.of("AI 对话失败，请稍后重试。"), errors);
        sse.verify(() -> AiSseMessageHelper.sendDone(anyLong()), never());
        assertFalse(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains("解析 OutputChannel 事件失败")));
    }
}
