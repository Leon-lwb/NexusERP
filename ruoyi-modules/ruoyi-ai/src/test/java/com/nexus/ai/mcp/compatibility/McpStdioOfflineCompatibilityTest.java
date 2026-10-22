package com.nexus.ai.mcp.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.nexus.ai.mcp.compatibility.McpTransportTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 使用独立 Java 子进程验证真实 STDIO 编解码、取消协议及关闭后的子进程回收。 */
@Timeout(20)
class McpStdioOfflineCompatibilityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializeListAndCallRoundTripChineseAndPreserveToolError() throws Exception {
        try (var fixture = new StdioSession(temporaryDirectory, Duration.ofSeconds(3))) {
            var client = fixture.client;
            assertEquals("echo", client.listTools().get(0).name());
            assertEquals("中文物料查询", client.listTools().get(0).description());
            ToolExecutionResult success = client.executeTool(request("echo", CHINESE_ARGUMENT));
            assertFalse(success.isError());
            assertEquals(CHINESE_RESULT, success.resultText());
            var failure = assertThrows(McpApplicationErrorException.class,
                () -> client.executeTool(request("fail", CHINESE_ARGUMENT)));
            assertEquals(CHINESE_ERROR, failure.getMessage());
            ToolExecutionResult observed = fixture.results.get(fixture.results.size() - 1);
            assertTrue(observed.isError());
            assertEquals(CHINESE_ERROR, observed.resultText());
            List<JsonNode> messages = fixture.messages();
            assertTrue(messages.stream().anyMatch(m -> "initialize".equals(m.path("method").asText())));
            assertTrue(messages.stream().anyMatch(m -> "notifications/initialized".equals(m.path("method").asText())));
            assertTrue(messages.stream().anyMatch(m -> "tools/list".equals(m.path("method").asText())));
            assertTrue(messages.stream().anyMatch(m -> "server/discover".equals(m.path("method").asText())));
            assertTrue(messages.stream().filter(m -> "tools/call".equals(m.path("method").asText()))
                .allMatch(m -> CHINESE_ARGUMENT.equals(m.path("params").path("arguments").path("message").asText())));
        }
    }

    @Test
    void boundedTimeoutSendsMatchingCancellationAndNextCallSucceeds() throws Exception {
        try (var fixture = new StdioSession(temporaryDirectory, Duration.ofMillis(500))) {
            long started = System.nanoTime();
            ToolExecutionResult result = fixture.client.executeTool(request("slow", CHINESE_ARGUMENT));
            assertTrue(result.isError());
            assertEquals(TIMEOUT_RESULT, result.resultText());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(4)) < 0);
            // STDIO 消息有序；后续成功响应也证明子进程已处理前面的取消通知。
            assertEquals(CHINESE_RESULT, fixture.client.executeTool(request("echo", CHINESE_ARGUMENT)).resultText());
            List<JsonNode> messages = fixture.messages();
            JsonNode slow = messages.stream().filter(m -> "slow".equals(m.path("params").path("name").asText())).findFirst().orElseThrow();
            JsonNode cancel = messages.stream().filter(m -> "notifications/cancelled".equals(m.path("method").asText())).findFirst().orElseThrow();
            assertEquals(slow.path("id"), cancel.path("params").path("requestId"));
            assertEquals("Timeout", cancel.path("params").path("reason").asText());
            assertTrue(fixture.process.isAlive());
        }
    }

    @Test
    void closeTerminatesChildRejectsReuseAndLeavesNoProcessBehind() throws Exception {
        StdioSession fixture = new StdioSession(temporaryDirectory, Duration.ofSeconds(3));
        Process child = fixture.process;
        try {
            assertTrue(child.isAlive());
            assertTrue(Files.exists(fixture.journal));
            fixture.client.close();
            assertTrue(child.waitFor(3, TimeUnit.SECONDS), "SDK close 必须终止自己启动的 Java 进程");
            assertFalse(child.isAlive());
            assertThrows(IllegalStateException.class, fixture.client::listTools);
            assertFalse(fixture.executor.isShutdown(), "SDK 不应关闭传入的共享执行器");
            fixture.client.close();
        } finally {
            fixture.close();
        }
        assertFalse(child.isAlive());
        assertTrue(fixture.executor.isTerminated());
    }
}
