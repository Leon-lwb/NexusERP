package com.nexus.ai.mcp.compatibility;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.nexus.ai.mcp.compatibility.McpTransportTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 SDK 和临时数字回环端点验证旧版 MCP HTTP 协议兼容，不修改生产端点策略。 */
@Timeout(20)
class McpHttpOfflineCompatibilityTest {

    @Test
    void defaultProtocolDetectionFallsBackToInitializeAndRoundTripsChinese() throws Exception {
        try (var server = new LoopbackMcpServer();
             var client = httpClient(server, Duration.ofSeconds(3))) {
            assertEquals("echo", client.listTools().get(0).name());
            assertEquals("中文物料查询", client.listTools().get(0).description());
            assertTrue(client.listTools().get(0).parameters().required().contains("message"));
            assertEquals(CHINESE_RESULT, client.executeTool(request("echo", CHINESE_ARGUMENT)).resultText());
            assertEquals(CHINESE_ARGUMENT, server.lastCall.get().path("params").path("arguments").path("message").asText());
            assertEquals(1, server.initializations.get());
            assertTrue(server.methods.contains("server/discover"), "覆盖 1.22 默认探测及旧服务端回退");
            assertTrue(server.methods.contains("notifications/initialized"));
            assertEquals("offline-session-1", server.lastCallSession.get());
        }
    }

    @Test
    void toolErrorRaisesApplicationExceptionAndNotifiesIsErrorWithChineseResult() throws Exception {
        try (var server = new LoopbackMcpServer();
             var client = httpClient(server, Duration.ofSeconds(3))) {
            var failure = assertThrows(McpApplicationErrorException.class,
                () -> client.executeTool(request("fail", CHINESE_ARGUMENT)));
            assertEquals(CHINESE_ERROR, failure.getMessage());
            ToolExecutionResult result = server.results.get(0);
            assertTrue(result.isError());
            assertEquals(CHINESE_ERROR, result.resultText());
            assertFalse(client.executeTool(request("echo", CHINESE_ARGUMENT)).isError());
        }
    }

    @Test
    void timeoutCancelsMatchingSseRequestAndLeavesClientUsable() throws Exception {
        try (var server = new LoopbackMcpServer();
             var client = httpClient(server, Duration.ofMillis(600))) {
            long started = System.nanoTime();
            ToolExecutionResult result = client.executeTool(request("slow", CHINESE_ARGUMENT));
            assertTrue(result.isError());
            assertEquals(TIMEOUT_RESULT, result.resultText());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(4)) < 0);
            assertTrue(server.cancellationReceived.await(3, TimeUnit.SECONDS), "服务端必须收到取消通知");
            assertEquals(server.slowRequest.get().path("id"), server.cancellation.get().path("params").path("requestId"));
            assertEquals("Timeout", server.cancellation.get().path("params").path("reason").asText());
            assertTrue(server.streamDisconnected.await(3, TimeUnit.SECONDS), "超时应取消 SSE 订阅并断开响应流");
            assertEquals(CHINESE_RESULT, client.executeTool(request("echo", CHINESE_ARGUMENT)).resultText());
        }
    }

    @Test
    void expiredSessionReinitializesAndRetriesOriginalCallOnce() throws Exception {
        try (var server = new LoopbackMcpServer();
             var client = httpClient(server, Duration.ofSeconds(3))) {
            server.expiredCallsRemaining.set(1);
            assertEquals(CHINESE_RESULT, client.executeTool(request("echo", CHINESE_ARGUMENT)).resultText());
            assertEquals(2, server.initializations.get());
            assertEquals(2, server.callAttempts.get());
            assertEquals("offline-session-2", server.lastCallSession.get());
            assertEquals(server.callIds.get(0), server.callIds.get(server.callIds.size() - 1), "重试必须关联原调用 ID");
        }
    }

    @Test
    void secondExpiredSessionFailsWithoutAnUnboundedReconnectLoop() throws Exception {
        try (var server = new LoopbackMcpServer();
             var client = httpClient(server, Duration.ofSeconds(3))) {
            server.expiredCallsRemaining.set(10);
            assertThrows(ToolExecutionException.class, () -> client.executeTool(request("echo", CHINESE_ARGUMENT)));
            assertEquals(2, server.initializations.get(), "本次调用最多重新握手一次");
            assertEquals(2, server.callAttempts.get());
        }
    }

    @Test
    void closeCancelsActiveSseAndPreservesCallerOwnedExecutor() throws Exception {
        try (var server = new LoopbackMcpServer()) {
            DefaultMcpClient client = httpClient(server, Duration.ofSeconds(5));
            CompletableFuture<ToolExecutionResult> pending = null;
            try {
                pending = client.executeToolAsync(request("slow", CHINESE_ARGUMENT), null);
                assertTrue(server.progressReceived.await(3, TimeUnit.SECONDS), "先由 SDK 处理进度事件，确认 SSE 已订阅");
                client.close();
                assertTrue(server.streamDisconnected.await(3, TimeUnit.SECONDS), "close 必须取消仍在接收的 SSE 流");
                assertThrows(IllegalStateException.class, client::listTools);
                assertFalse(server.clientExecutor.isShutdown(), "传入的执行器由测试负责回收");
            } finally {
                if (pending != null) {
                    pending.cancel(true);
                }
                client.close();
            }
        }
    }
}
