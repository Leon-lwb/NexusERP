package com.nexus.mes.flash.provider;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * ExternalRestFlashProvider 单元测试 — MES-FLASH-08 回归
 * <p>覆盖: SSRF 校验(回环/内网/链路本地/非 http 协议)、302 重定向不被跟随而是被拒、
 * 响应结构/长度校验、失败重试后统一抛 ServiceException(不再让 IORuntimeException 冒泡)。</p>
 * <p>重定向用例用裸 ServerSocket 回一段固定 HTTP 报文, 不依赖 jdk.httpserver 模块。</p>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
class ExternalRestFlashProviderTest {

    private ExternalRestFlashProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ExternalRestFlashProvider(mock(MesFlashTypeMapper.class));
        // 退避置 0: 重试用例不等待
        provider.setRetryBackoffMs(0);
    }

    // ==================== SSRF ====================

    @Test
    void checkSsrf_rejectsLoopback() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> ExternalRestFlashProvider.checkSsrf("http://127.0.0.1/flash"));
        assertTrue(ex.getMessage().contains("SSRF"), ex.getMessage());
    }

    @Test
    void checkSsrf_rejectsPrivateAndLinkLocalAddresses() {
        assertThrows(ServiceException.class, () -> ExternalRestFlashProvider.checkSsrf("http://10.0.0.1/x"));
        assertThrows(ServiceException.class, () -> ExternalRestFlashProvider.checkSsrf("http://192.168.1.10/x"));
        assertThrows(ServiceException.class, () -> ExternalRestFlashProvider.checkSsrf("http://172.16.0.1/x"));
        // 云元数据地址(169.254.0.0/16 链路本地): 302 打内网的经典目标
        assertThrows(ServiceException.class,
            () -> ExternalRestFlashProvider.checkSsrf("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void checkSsrf_rejectsNonHttpProtocol() {
        assertThrows(ServiceException.class, () -> ExternalRestFlashProvider.checkSsrf("file:///etc/passwd"));
    }

    @Test
    void checkSsrf_allowsPublicAddress() {
        assertDoesNotThrow(() -> ExternalRestFlashProvider.checkSsrf("http://8.8.8.8/flash"));
    }

    // ==================== 重定向 ====================

    @Test
    void fetchOnce_rejectsRedirectInsteadOfFollowing() throws Exception {
        String raw = "HTTP/1.1 302 Found\r\n"
            + "Location: http://169.254.169.254/latest/meta-data/\r\n"
            + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        try (RawHttpServer server = new RawHttpServer(raw)) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            ServiceException ex = assertThrows(ServiceException.class, () -> provider.fetchOnce(url, null));
            assertTrue(ex.getMessage().contains("重定向"), ex.getMessage());
            assertTrue(ex.getMessage().contains("拒绝跟随"), ex.getMessage());
        }
    }

    @Test
    void fetchOnce_rejectsRelativeRedirectToo() throws Exception {
        String raw = "HTTP/1.1 301 Moved Permanently\r\n"
            + "Location: /internal/secret\r\n"
            + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        try (RawHttpServer server = new RawHttpServer(raw)) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            assertThrows(ServiceException.class, () -> provider.fetchOnce(url, null));
        }
    }

    // ==================== 响应校验 ====================

    @Test
    void fetchOnce_returnsJsonBodyAfterValidation() throws Exception {
        String body = "{\"mac\":\"00:1A:2B:3C:4D:5E\"}";
        try (RawHttpServer server = new RawHttpServer(okResponse(body))) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            assertEquals(body, provider.fetchOnce(url, "Bearer t"));
        }
    }

    @Test
    void fetchOnce_rejectsMalformedJsonBody() throws Exception {
        String body = "{not-json";
        try (RawHttpServer server = new RawHttpServer(okResponse(body))) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            ServiceException ex = assertThrows(ServiceException.class, () -> provider.fetchOnce(url, null));
            assertTrue(ex.getMessage().contains("不是合法 JSON"), ex.getMessage());
        }
    }

    @Test
    void fetchOnce_rejectsEmptyBody() throws Exception {
        try (RawHttpServer server = new RawHttpServer(okResponse(""))) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            ServiceException ex = assertThrows(ServiceException.class, () -> provider.fetchOnce(url, null));
            assertTrue(ex.getMessage().contains("为空"), ex.getMessage());
        }
    }

    @Test
    void validatePayload_rejectsOverlongBody() {
        String body = "x".repeat(ExternalRestFlashProvider.MAX_PAYLOAD_LEN + 1);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> ExternalRestFlashProvider.validatePayload("http://8.8.8.8/x", body));
        assertTrue(ex.getMessage().contains("超长"), ex.getMessage());
    }

    @Test
    void validatePayload_allowsNonJsonRawContent() {
        assertDoesNotThrow(() -> ExternalRestFlashProvider.validatePayload("http://8.8.8.8/x", "RAW-FLASH-PAYLOAD"));
    }

    // ==================== 重试/降级 ====================

    @Test
    void fetchWithRetry_networkFailureThrowsServiceException() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String url = "http://127.0.0.1:" + closedPort + "/flash";

        ServiceException ex = assertThrows(ServiceException.class, () -> provider.fetchWithRetry(url, null));
        assertTrue(ex.getMessage().contains("已重试"), ex.getMessage());
        assertTrue(ex.getMessage().contains("失败"), ex.getMessage());
    }

    @Test
    void fetchWithRetry_retriesServerErrorThenThrowsServiceException() throws Exception {
        String raw = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        try (RawHttpServer server = new RawHttpServer(raw)) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            ServiceException ex = assertThrows(ServiceException.class, () -> provider.fetchWithRetry(url, null));
            assertTrue(ex.getMessage().contains("已重试"), ex.getMessage());
            assertEquals(ExternalRestFlashProvider.MAX_ATTEMPTS, server.requestCount(),
                "5xx 属可重试故障, 应按 MAX_ATTEMPTS 重试");
        }
    }

    @Test
    void fetchWithRetry_doesNotRetryDeterministicFailures() throws Exception {
        String raw = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        try (RawHttpServer server = new RawHttpServer(raw)) {
            String url = "http://127.0.0.1:" + server.port() + "/flash";
            assertThrows(ServiceException.class, () -> provider.fetchWithRetry(url, null));
            assertEquals(1, server.requestCount(), "4xx 是确定性失败, 不得重试");
        }
    }

    @Test
    void resolveLocation_resolvesRelativeAgainstBase() {
        assertEquals("http://8.8.8.8/internal/secret",
            ExternalRestFlashProvider.resolveLocation("http://8.8.8.8/flash", "/internal/secret"));
        assertEquals("http://169.254.169.254/latest/meta-data/",
            ExternalRestFlashProvider.resolveLocation("http://8.8.8.8/flash", "http://169.254.169.254/latest/meta-data/"));
    }

    private String okResponse(String body) {
        int length = body.getBytes(StandardCharsets.ISO_8859_1).length;
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + length
            + "\r\nConnection: close\r\n\r\n" + body;
    }

    /** 极简 HTTP 服务端: 对每个连接回同一段固定报文, 用于验证重定向拒绝与响应校验 */
    private static final class RawHttpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final AtomicInteger requests = new AtomicInteger();

        RawHttpServer(String rawResponse) throws IOException {
            this.serverSocket = new ServerSocket(0);
            Thread thread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try (Socket socket = serverSocket.accept()) {
                        socket.getInputStream().read(new byte[2048]);
                        // 先计数再回包: 客户端读到响应时计数已确定, 避免用例计数竞态
                        requests.incrementAndGet();
                        socket.getOutputStream().write(rawResponse.getBytes(StandardCharsets.ISO_8859_1));
                        socket.getOutputStream().flush();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "raw-http-test");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int requestCount() {
            return requests.get();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
