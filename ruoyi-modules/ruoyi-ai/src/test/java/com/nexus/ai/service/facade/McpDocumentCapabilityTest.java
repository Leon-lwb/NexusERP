package com.nexus.ai.service.facade;

import com.nexus.common.ai.domain.entity.AiTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 请求级真实 executor 证据测试；不依赖模型、监听事件、网络或数据库。 */
class McpDocumentCapabilityTest {
    private final ToolProviderRequest request = new ToolProviderRequest("session-A", UserMessage.from("读取资料"));
    private final ToolExecutionRequest read = ToolExecutionRequest.builder().id("call-A")
        .name("read_document").arguments("{\"document_name\":\"采购与库存作业规范.md\"}").build();
    private final InvocationContext context = mock(InvocationContext.class);

    @Test
    void nullProviderHasNoAdvertisedCapabilityOrDocumentEvidence() {
        McpDocumentCapability capability = create(null, true);
        assertFalse(hasTools(capability));
        assertNull(provider(capability));
        assertThrows(RuntimeException.class, () -> accept(capability, "模型声称读到了资料"));
    }

    @Test
    void nullResponseAndEmptyToolsCannotAdvertiseCapability() {
        assertFalse(hasTools(create(ignored -> null, false)));
        assertFalse(hasTools(create(ignored -> ToolProviderResult.builder().build(), false)));
    }

    @Test
    void genericWriteToolsRemainGenericAndMustNotBeLabelledReadOnlyOrConnected() {
        ToolExecutor write = (execution, memory) -> "ok";
        McpDocumentCapability capability = create(source(Map.of("write_file", write)), false);
        assertTrue(hasTools(capability));
        String description = capability.description();
        assertTrue(description.contains("外部工具"));
        assertFalse(description.contains("只读"));
        assertFalse(description.contains("已连接"));
        assertNotNull(provider(capability).provideTools(request).toolExecutorByName("write_file"));
    }

    @Test
    void auditedDocumentPathFiltersToTheTwoExactDocumentTools() {
        ToolExecutor executor = (execution, memory) -> "正式资料正文";
        McpDocumentCapability capability = create(source(Map.of("list_documents", executor, "read_document", executor,
            "write_file", executor, "read_document_extra", executor)), true);
        var tools = provider(capability).provideTools(request);
        assertEquals(2, tools.tools().size());
        assertNotNull(tools.toolExecutorByName("list_documents"));
        assertNotNull(tools.toolExecutorByName("read_document"));
        assertNull(tools.toolExecutorByName("write_file"));
        assertNull(tools.toolExecutorByName("read_document_extra"));
    }

    @Test
    void discoveryWithoutActualReadIsNotDocumentEvidence() {
        McpDocumentCapability capability = create(source(Map.of("read_document", (execution, memory) -> "正式资料正文")), true);
        provider(capability).provideTools(request);
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void contextualExecutorPreservesArgumentsContextAndResultAndRecordsRealRead() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        ToolExecutionResult original = ToolExecutionResult.builder().resultText("# 正式业务资料原文")
            .attributes(Map.of("source", "audited-service")).build();
        when(delegate.executeWithContext(read, context)).thenReturn(original);
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        ToolExecutor wrapped = provider(capability).provideTools(request).toolExecutorByName("read_document");
        assertSame(original, wrapped.executeWithContext(read, context));
        verify(delegate).executeWithContext(same(read), same(context));
        assertDoesNotThrow(() -> accept(capability, "基于真实读取结果的回答"));
    }

    @Test
    void legacyExecutorPreservesMemoryAndRecordsOnlyRealNonemptyRead() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        when(delegate.execute(read, "session-A")).thenReturn("正式资料正文");
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        assertEquals("正式资料正文", provider(capability).provideTools(request).toolExecutorByName("read_document").execute(read, "session-A"));
        verify(delegate).execute(same(read), eq("session-A"));
        assertDoesNotThrow(() -> accept(capability, "真实资料回答"));
    }

    @Test
    void contextualErrorResultIsPassedThroughButDoesNotCountAsSuccess() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        ToolExecutionResult error = ToolExecutionResult.builder().isError(true).resultText("DOCUMENT_NOT_FOUND").build();
        when(delegate.executeWithContext(any(), any())).thenReturn(error);
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        assertSame(error, provider(capability).provideTools(request).toolExecutorByName("read_document").executeWithContext(read, context));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void executorExceptionDoesNotBecomeEvidenceAndIsNotSwallowed() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        IllegalStateException failure = new IllegalStateException("test-only-error");
        when(delegate.executeWithContext(any(), any())).thenThrow(failure);
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> provider(capability).provideTools(request).toolExecutorByName("read_document").executeWithContext(read, context)));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "unknown", "Error: read failed", "{\"isError\":true,\"content\":\"error\"}",
        "{\"error\":{\"code\":\"DOCUMENT_NOT_FOUND\"}}", "{\"success\":false}", "{\"structuredContent\":{\"error\":{\"code\":\"X\"}}}"})
    void emptyOrErrorLegacyResultCannotSatisfyTheDocumentGate(String result) {
        McpDocumentCapability capability = create(source(Map.of("read_document", (execution, memory) -> result)), true);
        assertEquals(result, provider(capability).provideTools(request).toolExecutorByName("read_document").execute(read, "session-A"));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void listOnlyAndGenericToolSuccessAreNotEvidenceOfReadingARequestedDocument() {
        ToolExecutor delegate = (execution, memory) -> "{\"documents\":[{\"available\":true}]}";
        McpDocumentCapability capability = create(source(Map.of("list_documents", delegate)), true);
        var list = ToolExecutionRequest.builder().name("list_documents").arguments("{}").build();
        provider(capability).provideTools(request).toolExecutorByName("list_documents").execute(list, "session-A");
        assertThrows(RuntimeException.class, () -> accept(capability, "资料已读取"));
    }

    @Test
    void successfulReadInOneRequestCannotAuthorizeAnotherRequest() {
        ToolProvider shared = source(Map.of("read_document", (execution, memory) -> "正式资料正文"));
        McpDocumentCapability first = create(shared, true);
        McpDocumentCapability second = create(shared, true);
        provider(first).provideTools(request).toolExecutorByName("read_document").execute(read, "session-A");
        assertDoesNotThrow(() -> accept(first, "真实资料回答"));
        assertThrows(RuntimeException.class, () -> accept(second, "复制另一请求的成功"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void everyNullOrBlankSupervisorResultIsRejectedEvenAfterARead(String result) {
        McpDocumentCapability capability = create(source(Map.of("read_document", (execution, memory) -> "正文")), true);
        provider(capability).provideTools(request).toolExecutorByName("read_document").execute(read, "session-A");
        assertThrows(RuntimeException.class, () -> accept(capability, result));
        assertThrows(RuntimeException.class, () -> accept(create(null, false), result));
    }

    @Test
    void ordinaryErpAnswerDoesNotRequireMcpExecutionEvidence() {
        McpDocumentCapability capability = create(source(Map.of("read_document", (execution, memory) -> "正文")), false);
        assertDoesNotThrow(() -> accept(capability, "ERP工具查询得到的库存回答"));
        assertDoesNotThrow(() -> accept(create(null, false), "ERP回答"));
    }

    @Test
    void auditedServiceIdentityRequiresTheReviewedNameEnabledStateAndExactEndpoint() {
        AiTool stored = new AiTool();
        stored.setToolName("manufacturing_operations_documents");
        stored.setToolType("REMOTE");
        stored.setStatus("0");
        stored.setConfigJson("{\"baseUrl\":\"http://127.0.0.1:19540/mcp\"}");
        assertTrue(isAudited(stored));
        stored.setConfigJson("{\"baseUrl\":\"https://unreviewed.example.test/mcp\"}");
        assertFalse(isAudited(stored));
        stored.setConfigJson("{\"baseUrl\":\"http://127.0.0.1:19540/mcp\"}");
        stored.setStatus("1");
        assertFalse(isAudited(stored));
    }

    @Test
    void contextualNullResultDoesNotRecordEvidence() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        when(delegate.executeWithContext(any(), any())).thenReturn(null);
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        assertNull(provider(capability).provideTools(request).toolExecutorByName("read_document").executeWithContext(read, context));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void contextualEmptyResultDoesNotRecordEvidence() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        ToolExecutionResult empty = ToolExecutionResult.builder().resultText(" ").build();
        when(delegate.executeWithContext(any(), any())).thenReturn(empty);
        McpDocumentCapability capability = create(source(Map.of("read_document", delegate)), true);
        assertSame(empty, provider(capability).provideTools(request).toolExecutorByName("read_document").executeWithContext(read, context));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void dynamicProviderRetainsOriginalRequestAndRechecksItsTools() {
        ToolProvider source = mock(ToolProvider.class);
        when(source.isDynamic()).thenReturn(true);
        when(source.provideTools(request)).thenReturn(ToolProviderResult.builder()
            .add(ToolSpecification.builder().name("read_document").build(), (execution, memory) -> "正文").build());
        McpDocumentCapability capability = create(source, true);
        assertTrue(provider(capability).isDynamic());
        ToolProviderRequest next = new ToolProviderRequest("session-A", UserMessage.from("下一步读取"));
        when(source.provideTools(next)).thenReturn(ToolProviderResult.builder().build());
        assertTrue(provider(capability).provideTools(next).tools().isEmpty());
        verify(source).provideTools(same(next));
        assertThrows(RuntimeException.class, () -> accept(capability, "声称成功"));
    }

    @Test
    void documentIntentMustBeExplicitRatherThanThePresenceOfAMcpProvider() {
        assertTrue(McpDocumentCapability.isDocumentQuestion("读取资料"));
        assertTrue(McpDocumentCapability.isDocumentQuestion("查看文档"));
        assertTrue(McpDocumentCapability.isDocumentQuestion("读取文件"));
        assertTrue(McpDocumentCapability.isDocumentQuestion("采购作业规范"));
        assertFalse(McpDocumentCapability.isDocumentQuestion("查询ERP库存数量"));
        assertFalse(McpDocumentCapability.isDocumentQuestion(null));
    }

    private ToolProvider source(Map<String, ToolExecutor> tools) {
        Map<ToolSpecification, ToolExecutor> specs = new LinkedHashMap<>();
        tools.forEach((name, executor) -> specs.put(ToolSpecification.builder().name(name).build(), executor));
        return ignored -> ToolProviderResult.builder().addAll(specs).build();
    }

    private McpDocumentCapability create(ToolProvider source, boolean documentsRequired) {
        return McpDocumentCapability.prepare(source, request, documentsRequired);
    }
    private boolean hasTools(McpDocumentCapability capability) { return capability.hasTools(); }
    private ToolProvider provider(McpDocumentCapability capability) { return capability.provider(); }
    private void accept(McpDocumentCapability capability, String result) { capability.requireSuccessfulResult(result); }
    private boolean isAudited(AiTool stored) { return McpDocumentCapability.isAuditedDocumentService(stored); }
}
