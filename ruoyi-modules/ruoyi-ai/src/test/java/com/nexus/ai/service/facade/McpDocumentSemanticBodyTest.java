package com.nexus.ai.service.facade;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** 元数据对象非空不等于资料正文非空，必须检查结构化返回中的实际文本。 */
class McpDocumentSemanticBodyTest {
    private final ToolProviderRequest discovery = new ToolProviderRequest("资料会话", UserMessage.from("读取资料"));
    private final ToolExecutionRequest read = ToolExecutionRequest.builder().name("read_document").arguments("{}").build();

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\" \"", "{\"content\":[]}",
        "{\"content\":[{\"type\":\"text\",\"text\":\"\"}]}",
        "{\"structuredContent\":{\"content\":\" \"}}",
        "{\"name\":\"采购与库存作业规范.md\",\"content\":\"\"}"})
    @DisplayName("有工具元数据但无资料正文的返回不能签发读取成功")
    void emptySemanticBodyDoesNotBecomeEvidence(String returned) {
        McpDocumentCapability capability = capability(returned);
        capability.provider().provideTools(discovery).toolExecutorByName("read_document").execute(read, "资料会话");
        assertThrows(RuntimeException.class, () -> capability.requireSuccessfulResult("模型声称资料读取成功"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"# 采购与库存作业规范\n库存来源缺失时保留未知。",
        "{\"content\":[{\"type\":\"text\",\"text\":\"库存来源缺失时保留未知。\"}]}",
        "{\"structuredContent\":{\"content\":\"库存来源缺失时保留未知。\"}}",
        "{\"content\":[],\"structuredContent\":{\"content\":\"库存来源缺失时保留未知。\"}}",
        "{\"content\":\"\",\"structuredContent\":{\"content\":\"库存来源缺失时保留未知。\"}}"})
    @DisplayName("原文及包含非空正文的协议结果仍可证明真实读取")
    void realSemanticBodyStillWorks(String returned) {
        McpDocumentCapability capability = capability(returned);
        capability.provider().provideTools(discovery).toolExecutorByName("read_document").execute(read, "资料会话");
        assertDoesNotThrow(() -> capability.requireSuccessfulResult("根据实际原文说明库存来源"));
    }

    private McpDocumentCapability capability(String returned) {
        ToolExecutor executor = (request, memory) -> returned;
        return McpDocumentCapability.prepare(ignored -> ToolProviderResult.builder()
            .add(ToolSpecification.builder().name("read_document").build(), executor).build(), discovery, true);
    }
}
