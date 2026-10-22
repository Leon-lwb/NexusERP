package com.nexus.ai.mcp.compatibility;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 离线测试拥有全部临时端口、执行器与子进程；不依赖 Spring、数据库或外部 MCP 服务。 */
final class McpTransportTestSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String CHINESE_ARGUMENT = "物料：螺栓 M8；批次：沪A-2026\n第二行 \"检验\"";
    static final String CHINESE_RESULT = "收到：" + CHINESE_ARGUMENT;
    static final String CHINESE_ERROR = "物料不存在：请核对编码";
    static final String TIMEOUT_RESULT = "离线调用超时";

    private McpTransportTestSupport() {
    }

    static ToolExecutionRequest request(String name, String message) throws IOException {
        return ToolExecutionRequest.builder().name(name)
            .arguments(JSON.writeValueAsString(Map.of("message", message))).build();
    }

    static DefaultMcpClient httpClient(LoopbackMcpServer server, Duration timeout) {
        var transport = StreamableHttpMcpTransport.builder().url(server.endpoint())
            .timeout(Duration.ofSeconds(3)).setHttpVersion1_1().followRedirects(false)
            .executor(server.clientExecutor).build();
        try {
            return clientBuilder(timeout).transport(transport).addListener(new ResultRecorder(server.results))
                .progressHandler(notification -> server.progressReceived.countDown()).build();
        } catch (RuntimeException failure) {
            try {
                transport.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static DefaultMcpClient.Builder clientBuilder(Duration timeout) {
        return new DefaultMcpClient.Builder().autoHealthCheck(false)
            .initializationTimeout(Duration.ofSeconds(5))
            .toolExecutionTimeout(timeout).toolExecutionTimeoutErrorMessage(TIMEOUT_RESULT);
    }

    static void terminateExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS), "测试执行器应在有界等待内退出");
    }

    private static final class ResultRecorder implements McpClientListener {
        private final List<ToolExecutionResult> results;

        ResultRecorder(List<ToolExecutionResult> results) {
            this.results = results;
        }

        @Override
        public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
            results.add(result);
        }
    }

    /** 一个精确数字回环地址、一个临时端口与一个 /mcp 端点，不经过生产策略或更改其白名单。 */
    static final class LoopbackMcpServer implements AutoCloseable {
        final ExecutorService clientExecutor = Executors.newCachedThreadPool();
        private final ExecutorService serverExecutor = Executors.newCachedThreadPool();
        private final HttpServer server;
        final List<String> methods = new CopyOnWriteArrayList<>();
        final List<ToolExecutionResult> results = new CopyOnWriteArrayList<>();
        final List<Long> callIds = new CopyOnWriteArrayList<>();
        final AtomicInteger initializations = new AtomicInteger();
        final AtomicInteger callAttempts = new AtomicInteger();
        final AtomicInteger expiredCallsRemaining = new AtomicInteger();
        final AtomicReference<JsonNode> lastCall = new AtomicReference<>();
        final AtomicReference<JsonNode> slowRequest = new AtomicReference<>();
        final AtomicReference<JsonNode> cancellation = new AtomicReference<>();
        final AtomicReference<String> lastCallSession = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch cancellationReceived = new CountDownLatch(1);
        final CountDownLatch progressReceived = new CountDownLatch(1);
        final CountDownLatch streamDisconnected = new CountDownLatch(1);
        private final CountDownLatch stopping = new CountDownLatch(1);

        LoopbackMcpServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(serverExecutor);
            server.createContext("/mcp", this::handle);
            server.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod()) || !"/mcp".equals(exchange.getRequestURI().getPath())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                JsonNode request = JSON.readTree(exchange.getRequestBody());
                String method = request.path("method").asText();
                methods.add(method);
                if ("initialize".equals(method)) {
                    exchange.getResponseHeaders().set("Mcp-Session-Id", "offline-session-" + initializations.incrementAndGet());
                } else if ("tools/call".equals(method)) {
                    lastCall.set(request);
                    callAttempts.incrementAndGet();
                    callIds.add(request.path("id").asLong());
                    lastCallSession.set(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
                    if (expiredCallsRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    if ("slow".equals(request.path("params").path("name").asText())) {
                        slowRequest.set(request);
                        streamUntilDisconnected(exchange, request);
                        return;
                    }
                } else if ("notifications/cancelled".equals(method)) {
                    cancellation.set(request);
                    cancellationReceived.countDown();
                }
                Map<String, Object> response = JavaStdioMcpFixture.responseFor(request);
                if (response == null) {
                    exchange.sendResponseHeaders(202, -1);
                } else {
                    byte[] body = JSON.writeValueAsBytes(response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }

        private void streamUntilDisconnected(HttpExchange exchange, JsonNode request) throws IOException, InterruptedException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            // 注释帧不完成 JSON-RPC 请求，周期写入让服务器可观察客户端主动断流。
            byte[] heartbeat = (":" + "x".repeat(1024) + "\n\n").getBytes(StandardCharsets.UTF_8);
            try {
                Map<String, Object> progress = Map.of("jsonrpc", "2.0", "method", "notifications/progress", "params",
                    Map.of("progressToken", request.path("params").path("_meta").path("progressToken").asText(),
                        "progress", 1, "total", 100, "message", "等待离线测试取消"));
                exchange.getResponseBody().write(("data: " + JSON.writeValueAsString(progress) + "\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                while (System.nanoTime() < deadline && !stopping.await(25, TimeUnit.MILLISECONDS)) {
                    exchange.getResponseBody().write(heartbeat);
                    exchange.getResponseBody().flush();
                }
            } catch (IOException expectedDisconnect) {
                streamDisconnected.countDown();
            }
        }

        @Override
        public void close() throws InterruptedException {
            stopping.countDown();
            server.stop(0);
            try {
                terminateExecutor(serverExecutor);
            } finally {
                terminateExecutor(clientExecutor);
            }
            assertNull(failure.get(), () -> "临时 HTTP 服务端异常：" + failure.get());
        }
    }

    /** 子进程只接收夹具目录和 Jackson 三个依赖，避免 Windows 命令行长度与 Surefire manifest 差异。 */
    static final class StdioSession implements AutoCloseable {
        final Path journal;
        final List<ToolExecutionResult> results = new CopyOnWriteArrayList<>();
        final ExecutorService executor = Executors.newCachedThreadPool();
        final StdioMcpTransport transport;
        final DefaultMcpClient client;
        final Process process;

        StdioSession(Path directory, Duration timeout) throws Exception {
            journal = directory.resolve("stdio-requests.jsonl");
            String classpath = Stream.of(JavaStdioMcpFixture.class, ObjectMapper.class, JsonFactory.class, JsonInclude.class)
                .map(type -> {
                    try {
                        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }).distinct().collect(Collectors.joining(File.pathSeparator));
            String executable =
                    System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                            ? "java.exe"
                            : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
            transport = StdioMcpTransport.builder().executorService(executor).command(List.of(java, "-cp", classpath,
                JavaStdioMcpFixture.class.getName(), journal.toString())).build();
            try {
                client = clientBuilder(timeout).transport(transport).addListener(new ResultRecorder(results)).build();
                process = transport.getProcess();
                assertNotNull(process);
            } catch (Throwable failure) {
                Process started = transport.getProcess();
                transport.close();
                if (started != null && !started.waitFor(3, TimeUnit.SECONDS)) {
                    started.destroyForcibly();
                    assertTrue(started.waitFor(3, TimeUnit.SECONDS));
                }
                terminateExecutor(executor);
                throw failure;
            }
        }

        List<JsonNode> messages() throws IOException {
            List<JsonNode> messages = new ArrayList<>();
            for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
                messages.add(JSON.readTree(line));
            }
            return messages;
        }

        @Override
        public void close() throws Exception {
            client.close();
            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            try {
                if (!exited) {
                    process.destroyForcibly();
                    assertTrue(process.waitFor(3, TimeUnit.SECONDS), "夹具必须强制回收遗留子进程");
                }
                assertTrue(exited, "SDK 应主动回收子进程，强制清理不算通过");
            } finally {
                terminateExecutor(executor);
            }
        }
    }
}
