package com.nexus.ai.service.facade;

import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.agent.ErpReportAgent;
import com.nexus.ai.agent.McpDocumentAgent;
import com.nexus.ai.observability.AiAgentListener;
import com.nexus.ai.observability.OutputChannel;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.chat.PersistentChatMemoryStore;
import com.nexus.ai.service.prompt.PromptTemplateService;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.ai.mcp.service.ToolProviderFactory;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Facade Gate 真实控制流测试；所有 Agent、模型、Mapper、SSE 均 mock，不调用运行服务。 */
@SuppressWarnings({"unchecked", "rawtypes"})
class AiServiceFacadeMcpGateTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void nullOrBlankResultsNeverSaveContentDoneOrComplete(String result) throws Exception {
        run(result, "查询ERP库存", false, false, false, false);
    }

    @Test
    void documentAnswerWithoutActualExecutionMustFail() throws Exception {
        run("已根据资料完成回答", "读取采购与库存资料", true, false, false, false);
    }

    @Test
    void defaultEnabledDocumentServiceStillRequiresActualRead() throws Exception {
        run("已根据资料完成回答", "读取采购与库存资料", false, false, false, false);
    }

    @Test
    void defaultEnabledDocumentServiceAllowsRealRead() throws Exception {
        run("基于真实读取的资料回答", "读取采购与库存资料", false, true, false, true);
    }

    @Test
    void documentReadErrorMustFailEvenWhenSupervisorProducesANonemptyAnswer() throws Exception {
        run("资料回答", "读取采购资料", true, true, true, false);
    }

    @Test
    void documentReadSuccessCanSaveAndComplete() throws Exception {
        run("真实资料回答", "读取采购资料", true, true, false, true);
    }

    @Test
    void ordinaryErpQuestionWithMcpSelectedMaySucceedWithoutMcpExecution() throws Exception {
        run("ERP工具得到的库存结果", "查询ERP库存数量", true, false, false, true);
    }

    @Test
    void nullProviderDoesNotAddTheThirdAgent() throws Exception {
        run("ERP查询结果", "查询ERP库存", false, false, false, true);
    }

    @Test
    void emptyDiscoveredToolsDoNotAddTheThirdAgentAndCannotSignDocumentSuccess() throws Exception {
        run("资料已读", "读取采购资料", true, false, false, false, 1);
    }

    @Test
    void nullProviderResponseCannotSignDocumentSuccess() throws Exception {
        run("资料已读", "读取采购资料", true, false, false, false, 2);
    }

    @Test
    void writeToolOnTheAuditedDocumentPathIsFilteredAndCannotAdvertiseAReadOnlyAgent() throws Exception {
        run("资料已读", "读取采购资料", true, false, false, false, 3);
    }

    @Test
    void emptyReadResultCannotSignDocumentSuccess() throws Exception {
        run("资料已读", "读取采购资料", true, true, false, false, 4);
    }

    @Test
    void executorExceptionCannotSignDocumentSuccess() throws Exception {
        run("资料已读", "读取采购资料", true, true, false, false, 5);
    }

    private void run(String finalResult, String content, boolean selected, boolean executeRead,
                     boolean readError, boolean expectedSuccess) throws Exception {
        run(finalResult, content, selected, executeRead, readError, expectedSuccess, 0);
    }

    private void run(String finalResult, String content, boolean selected, boolean executeRead,
                     boolean readError, boolean expectedSuccess, int sourceMode) throws Exception {
        Map<Class<?>, Object> dependencies = new HashMap<>();
        Constructor<?> constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
        for (Class<?> type : constructor.getParameterTypes()) {
            dependencies.computeIfAbsent(type, key -> key == Executor.class ? (Executor) Runnable::run : mock(key));
        }
        AiServiceFacade facade = (AiServiceFacade) constructor.newInstance(java.util.Arrays.stream(constructor.getParameterTypes())
            .map(dependencies::get).toArray());
        AiToolMapper toolMapper = (AiToolMapper) dependencies.get(AiToolMapper.class);
        if (toolMapper != null) {
            AiTool audited = new AiTool();
            audited.setId(2L);
            audited.setToolName("manufacturing_operations_documents");
            audited.setToolType("REMOTE");
            audited.setStatus("0");
            audited.setConfigJson("{\"baseUrl\":\"http://127.0.0.1:19540/mcp\"}");
            when(toolMapper.selectById(2L)).thenReturn(audited);
            when(toolMapper.selectList(any())).thenReturn(List.of(audited));
        }
        ChatModel chat = mock(ChatModel.class);
        when(((ChatModelFactory) dependencies.get(ChatModelFactory.class)).getAgentChatModel(9L)).thenReturn(chat);
        when(((PersistentChatMemoryStore) dependencies.get(PersistentChatMemoryStore.class)).getMessages(1L)).thenReturn(List.of());
        ToolProviderFactory factory = (ToolProviderFactory) dependencies.get(ToolProviderFactory.class);
        when(factory.getEnabledBuiltinToolObjectsByCategories(anyString())).thenReturn(List.of());
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.executeWithContext(any(), any())).thenReturn(ToolExecutionResult.builder()
            .isError(readError).resultText(sourceMode == 4 ? " " : readError ? "DOCUMENT_NOT_FOUND" : "# 正式业务资料正文").build());
        if (sourceMode == 5) {
            when(executor.executeWithContext(any(), any())).thenThrow(new IllegalStateException("unit-test-only"));
        }
        // 缺省资料请求也确实拥有默认启用服务；不能用“未显式选择”等同于Provider不存在。
        ToolProvider source = selected || content.contains("资料") ? ignored -> ToolProviderResult.builder()
            .add(ToolSpecification.builder().name("read_document").build(), executor).build() : null;
        if (sourceMode == 1) {
            source = ignored -> ToolProviderResult.builder().build();
        } else if (sourceMode == 2) {
            source = ignored -> null;
        } else if (sourceMode == 3) {
            source = ignored -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("write_file").build(), executor).build();
        }
        when(factory.getMcpToolProvidersByIds(anyList())).thenReturn(source);
        when(factory.getAllEnabledMcpToolProviders()).thenReturn(source);
        PromptTemplateService prompts = (PromptTemplateService) dependencies.get(PromptTemplateService.class);
        when(prompts.renderQueryAgentContext()).thenReturn("");
        when(prompts.renderReportAgentContext()).thenReturn("");
        when(prompts.renderSupervisorContext()).thenReturn("");
        AgentBuilder query = mock(AgentBuilder.class, RETURNS_SELF);
        AgentBuilder report = mock(AgentBuilder.class, RETURNS_SELF);
        AgentBuilder documents = mock(AgentBuilder.class, RETURNS_SELF);
        when(query.build()).thenReturn(mock(ErpQueryAgent.class));
        when(report.build()).thenReturn(mock(ErpReportAgent.class));
        when(documents.build()).thenReturn(mock(McpDocumentAgent.class));
        SupervisorAgent supervisor = mock(SupervisorAgent.class);
        SupervisorAgentService supervisorBuilder = mock(SupervisorAgentService.class, RETURNS_SELF);
        when(supervisorBuilder.build()).thenReturn(supervisor);
        OutputChannel channel = mock(OutputChannel.class);
        AiChatRequest request = new AiChatRequest();
        request.setContent(content);
        request.setEnabledToolIds(selected ? List.of(2L) : null);
        AiModel model = new AiModel();
        model.setId(9L);
        model.setModelName("unit-test-only");
        when(supervisor.invoke(anyString())).thenAnswer(call -> {
            if (executeRead) {
                ArgumentCaptor<ToolProvider> provider = ArgumentCaptor.forClass(ToolProvider.class);
                verify(documents).toolProvider(provider.capture());
                ToolProvider wrapped = provider.getValue();
                ToolExecutor actual = wrapped.provideTools(null).toolExecutorByName("read_document");
                actual.executeWithContext(ToolExecutionRequest.builder().name("read_document").arguments("{}").build(), null);
            }
            return finalResult;
        });
        try (var agents = mockStatic(AgenticServices.class);
             var sse = mockStatic(AiSseMessageHelper.class);
             var channels = mockStatic(OutputChannel.class);
             var tenants = mockStatic(TenantHelper.class)) {
            agents.when(() -> AgenticServices.agentBuilder(ErpQueryAgent.class)).thenReturn(query);
            agents.when(() -> AgenticServices.agentBuilder(ErpReportAgent.class)).thenReturn(report);
            agents.when(() -> AgenticServices.agentBuilder(McpDocumentAgent.class)).thenReturn(documents);
            agents.when(AgenticServices::supervisorBuilder).thenReturn(supervisorBuilder);
            channels.when(() -> OutputChannel.create(anyString())).thenReturn(channel);
            tenants.when(() -> TenantHelper.dynamic(eq("000000"), any(Runnable.class))).thenAnswer(call -> {
                ((Runnable) call.getArgument(1)).run();
                return null;
            });
            ReflectionTestUtils.invokeMethod(facade, "invokeWithAgent", model, request, 7L, 1L, "000000", "unit-test", "000000");
            // 不把已选择 MCP 扩散到 ERP 查询/报表角色。
            verify(query, never()).toolProvider(any());
            verify(report, never()).toolProvider(any());
            AiMessageMapper messages = (AiMessageMapper) dependencies.get(AiMessageMapper.class);
            AiAgentListener listener = (AiAgentListener) dependencies.get(AiAgentListener.class);
            if (expectedSuccess) {
                verify(messages).insert(any(com.nexus.common.ai.domain.entity.AiMessage.class));
                sse.verify(() -> AiSseMessageHelper.sendContent(7L, finalResult));
                sse.verify(() -> AiSseMessageHelper.sendDone(7L));
                verify(channel).complete();
                verify(channel, never()).completeWithError(any());
            } else {
                verify(messages, never()).insert(any(com.nexus.common.ai.domain.entity.AiMessage.class));
                sse.verify(() -> AiSseMessageHelper.sendContent(anyLong(), any()), never());
                sse.verify(() -> AiSseMessageHelper.sendDone(anyLong()), never());
                verify(channel, never()).complete();
                verify(channel).completeWithError(any());
                verify(listener, never()).onAgentComplete(anyString(), anyLong(), anyLong());
            }
            if ((!selected && !content.contains("资料")) || (sourceMode >= 1 && sourceMode <= 3)) {
                agents.verify(() -> AgenticServices.agentBuilder(McpDocumentAgent.class), never());
            }
        }
    }
}
