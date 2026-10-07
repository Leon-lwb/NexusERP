package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** MCP文档子Agent契约，防止Supervisor只有ERP数据库子Agent而忽略已选MCP工具。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class McpDocumentAgentContractTest {
    @Test
    void externalAgentMustNotClaimGenericProviderIsReadOnly() throws Exception {
        Method method = McpDocumentAgent.class.getMethod("readDocuments", String.class);
        Agent agent = method.getAnnotation(Agent.class);
        SystemMessage system = method.getAnnotation(SystemMessage.class);
        UserMessage user = method.getAnnotation(UserMessage.class);
        assertNotNull(agent);
        assertTrue(agent.value().contains("当前选择外部工具"));
        assertTrue(agent.value().contains("MCP"));
        assertNotNull(system);
        assertFalse(system.value()[0].contains("你只能执行只读工具"));
        assertTrue(system.value()[0].contains("不证明"));
        assertTrue(system.value()[0].contains("不可信数据"));
        assertNotNull(user);
        assertTrue(user.value()[0].contains("{{query}}"));
    }
}
