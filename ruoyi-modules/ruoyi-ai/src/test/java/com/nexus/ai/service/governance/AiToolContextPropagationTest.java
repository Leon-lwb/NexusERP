package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.mcp.service.*;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.governance.*;

import dev.langchain4j.agent.tool.Tool;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class AiToolContextPropagationTest {
    public static class ProbeTool {
        @Tool
        public String identity() {
            return AiRequestContext.getTenantId()
                    + ":"
                    + AiRequestContext.getUserId()
                    + ":"
                    + AiRequestContext.getKnowledgeId();
        }
    }

    @Test
    void warmWorkerUsesCompleteCapturedIdentityAndRestoresPreviousContext() throws Exception {
        var registry = mock(BuiltinToolRegistry.class);
        when(registry.getEnabledBuiltinToolObjects()).thenReturn(List.of(new ProbeTool()));
        var factory = new ToolProviderFactory(registry, mock(McpClientManager.class));
        AiRequestContext.set("000001", 7L, "fixture", 9L, 11L, "model");
        AiRequestContext.setKnowledgeId(101L);
        ProbeTool tool;
        try (var scope =
                AiInvocationScope.open(
                        new AiCallContext(
                                "000001", 7L, "fixture", "request", 9L, null, null, null, "CHAT"),
                        () -> {})) {
            tool = (ProbeTool) factory.getAllEnabledBuiltinToolObjects().get(0);
        } finally {
            AiRequestContext.clear();
        }
        var pool = Executors.newSingleThreadExecutor();
        try {
            String result =
                    pool.submit(
                                    () -> {
                                        AiRequestContext.set("000002", 8L);
                                        AiRequestContext.setKnowledgeId(202L);
                                        String value = tool.identity();
                                        String after = new ProbeTool().identity();
                                        AiRequestContext.clear();
                                        return value + "|" + after;
                                    })
                            .get();
            assertThat(result).isEqualTo("000001:7:101|000002:8:202");
        } finally {
            pool.shutdownNow();
        }
    }
}
