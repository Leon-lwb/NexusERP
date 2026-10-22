package com.nexus.ai.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 使用真实HTTP适配器和受控DNS/Call替身验证最终连接约束，不访问网络、不监听端口。 */
class McpPinnedHttpClientTest {
    private static final String HOST = "review-dns.example.test";
    private static final String URL = "https://" + HOST + "/mcp";

    @Test
    void 解析只取已批准快照重连也不能转向后续私网结果() throws Exception {
        InetAddress approved =
                InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34});
        InetAddress rebound = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        try (var dns = mockStatic(InetAddress.class)) {
            var lookups = new AtomicInteger();
            dns.when(() -> InetAddress.getAllByName(HOST))
                    .thenAnswer(
                            call ->
                                    lookups.getAndIncrement() == 0
                                            ? new InetAddress[] {approved}
                                            : new InetAddress[] {rebound});
            HttpClient client = builder(URL, "").build();
            OkHttpClient nativeClient = nativeClient(client);
            assertEquals(List.of(approved), nativeClient.dns().lookup(HOST));
            assertEquals(List.of(approved), nativeClient.dns().lookup(HOST));
            dns.verify(() -> InetAddress.getAllByName(HOST), times(1));
            assertThrows(
                    UnknownHostException.class,
                    () -> nativeClient.dns().lookup("other.example.test"));
        }
    }

    @Test
    void 未授权私网候选在HTTP构造前拒绝() throws Exception {
        InetAddress privateIp = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
        try (var dns = mockStatic(InetAddress.class)) {
            dns.when(() -> InetAddress.getAllByName(HOST))
                    .thenReturn(new InetAddress[] {privateIp});
            assertThrows(RuntimeException.class, () -> builder(URL, "").build());
        }
    }

    @Test
    void 原域名URL和默认TLS验证保持不变且禁用代理与跳转() throws Exception {
        try (var dns = publicDns()) {
            HttpClient client =
                    builder(URL, "")
                            .connectTimeout(Duration.ofSeconds(3))
                            .readTimeout(Duration.ofSeconds(7))
                            .build();
            OkHttpClient original = nativeClient(client);
            OkHttpClient transport = spy(original);
            assertSame(new OkHttpClient().hostnameVerifier(), original.hostnameVerifier());
            assertEquals(Proxy.NO_PROXY, original.proxy());
            assertFalse(original.followRedirects());
            assertFalse(original.followSslRedirects());
            assertEquals(3000, original.connectTimeoutMillis());
            assertEquals(7000, original.readTimeoutMillis());
            Call call = mock(Call.class);
            doReturn(call).when(transport).newCall(any());
            when(call.execute()).thenReturn(response(URL, 200, "{\"ok\":true}"));
            ReflectionTestUtils.setField(client, "delegate", transport);
            assertEquals(
                    "{\"ok\":true}",
                    client.execute(HttpRequest.builder().method(HttpMethod.GET).url(URL).build())
                            .body());
            var request = ArgumentCaptor.forClass(Request.class);
            verify(transport).newCall(request.capture());
            assertEquals(HOST, request.getValue().url().host());
            assertEquals(URL, request.getValue().url().toString());
        }
    }

    @Test
    void 当前本机数字IP精确端点仍能生成受控客户端() throws Exception {
        String local = "http://127.0.0.1:19540/mcp";
        HttpClient client = builder(local, local).build();
        assertEquals(
                "127.0.0.1",
                nativeClient(client).dns().lookup("127.0.0.1").get(0).getHostAddress());
        assertThrows(RuntimeException.class, () -> builder(local + "/child", local).build());
    }

    @Test
    void 请求不能越过已批准URI或用Host头改变身份() throws Exception {
        try (var dns = publicDns()) {
            HttpClient client = builder(URL, "").build();
            OkHttpClient nativeClient = spy(nativeClient(client));
            ReflectionTestUtils.setField(client, "delegate", nativeClient);
            assertThrows(
                    RuntimeException.class,
                    () ->
                            client.execute(
                                    HttpRequest.builder()
                                            .method(HttpMethod.GET)
                                            .url(URL + "/child")
                                            .build()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            client.execute(
                                    HttpRequest.builder()
                                            .method(HttpMethod.GET)
                                            .url(URL)
                                            .addHeader("Host", "other.example.test")
                                            .build()));
            verify(nativeClient, never()).newCall(any());
        }
    }

    @Test
    void HTTP错误不反射响应中的虚构敏感哨兵() throws Exception {
        try (var dns = publicDns()) {
            HttpClient client = builder(URL, "").build();
            OkHttpClient nativeClient = spy(nativeClient(client));
            Call call = mock(Call.class);
            doReturn(call).when(nativeClient).newCall(any());
            when(call.execute()).thenReturn(response(URL, 302, "REVIEW_ONLY_HTTP_SECRET_SENTINEL"));
            ReflectionTestUtils.setField(client, "delegate", nativeClient);
            RuntimeException error =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    client.execute(
                                            HttpRequest.builder()
                                                    .method(HttpMethod.GET)
                                                    .url(URL)
                                                    .build()));
            assertFalse(error.toString().contains("REVIEW_ONLY_HTTP_SECRET_SENTINEL"));
            assertNull(error.getCause());
        }
    }

    @Test
    void 未接入的流式结构必须明确拒绝而不能默认回退() throws Exception {
        try (var dns = publicDns()) {
            HttpClient client = builder(URL, "").build();
            OkHttpClient nativeClient = spy(nativeClient(client));
            Call call = mock(Call.class);
            doReturn(call).when(nativeClient).newCall(any());
            ReflectionTestUtils.setField(client, "delegate", nativeClient);
            ServerSentEventParser parser = mock(ServerSentEventParser.class);
            ServerSentEventListener listener = mock(ServerSentEventListener.class);
            assertThrows(
                    RuntimeException.class,
                    () ->
                            client.execute(
                                    HttpRequest.builder().method(HttpMethod.GET).url(URL).build(),
                                    parser,
                                    listener));
            verify(nativeClient, never()).newCall(any());
            verifyNoInteractions(parser, listener);
        }
    }

    private HttpClientBuilder builder(String url, String allow) {
        Class<?> type =
                assertDoesNotThrow(
                        () -> Class.forName("com.nexus.ai.mcp.McpPinnedHttpClientBuilder"));
        try {
            return (HttpClientBuilder)
                    type.getConstructor(String.class, String.class).newInstance(url, allow);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private OkHttpClient nativeClient(HttpClient client) {
        return (OkHttpClient) ReflectionTestUtils.getField(client, "delegate");
    }

    private MockedStatic<InetAddress> publicDns() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34});
        var dns = mockStatic(InetAddress.class);
        dns.when(() -> InetAddress.getAllByName(HOST)).thenReturn(new InetAddress[] {address});
        return dns;
    }

    private Response response(String url, int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("review-only")
                .body(ResponseBody.create(body, MediaType.parse("text/event-stream")))
                .build();
    }
}
