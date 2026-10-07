package com.nexus.ai.mcp;

import com.nexus.common.core.exception.ServiceException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MCP专用HTTP SPI：以已批准地址快照约束OkHttp的最终DNS路由及重新连接。
 * 原域名URL不变，沿用OkHttp默认TLS证书/主机名验证与SNI；不替换成IP、不关闭TLS。
 * 仅用于市场GET；本版MCP Transport没有此SPI注入口，不能冒称协议链已支持域名。
 * 禁用代理/重定向，未接入的流式与multipart结构明确拒绝。
 */
public final class McpPinnedHttpClientBuilder implements HttpClientBuilder {
    private final McpEndpointPolicy.ApprovedEndpoint endpoint;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);

    public McpPinnedHttpClientBuilder(String url, String allowedLocalEndpoints) {
        endpoint = McpEndpointPolicy.approve(url, allowedLocalEndpoints);
    }

    @Override public Duration connectTimeout() { return connectTimeout; }
    @Override public Duration readTimeout() { return readTimeout; }
    @Override public McpPinnedHttpClientBuilder connectTimeout(Duration timeout) {
        if (timeout != null) { requireTimeout(timeout); connectTimeout = timeout; }
        return this;
    }
    @Override public McpPinnedHttpClientBuilder readTimeout(Duration timeout) {
        if (timeout != null) { requireTimeout(timeout); readTimeout = timeout; }
        return this;
    }

    private void requireTimeout(Duration timeout) {
        if (timeout.isNegative()) throw new ServiceException("MCP超时配置无效");
    }

    @Override public PinnedClient build() {
        OkHttpClient nativeClient = new OkHttpClient.Builder()
            .dns(host -> {
                if (!endpoint.host().equalsIgnoreCase(host)) {
                    throw new UnknownHostException("MCP连接主机不在本次批准范围");
                }
                // 返回已校验快照，绝不回退系统DNS；后续连接也只能使用同一批准集合。
                return endpoint.addresses();
            })
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();
        return new PinnedClient(endpoint.url(), nativeClient);
    }

    public static final class PinnedClient implements HttpClient, AutoCloseable {
        private final String approvedUrl;
        private final OkHttpClient delegate;

        private PinnedClient(String approvedUrl, OkHttpClient delegate) {
            this.approvedUrl = approvedUrl;
            this.delegate = delegate;
        }

        private Request request(HttpRequest source) {
            if (!approvedUrl.equals(source.url()) || !"GET".equals(source.method().name())
                || !source.formDataFields().isEmpty() || !source.formDataFiles().isEmpty()) {
                throw new ServiceException("MCP请求地址或结构不在本次批准范围");
            }
            Request.Builder request = new Request.Builder().url(approvedUrl);
            source.headers().forEach((name, values) -> {
                if ("host".equalsIgnoreCase(name) || "proxy-authorization".equalsIgnoreCase(name)) {
                    throw new ServiceException("MCP不允许覆盖连接身份或代理认证头");
                }
                if (values != null) values.forEach(value -> request.addHeader(name, value));
            });
            RequestBody body = source.body() == null ? null
                : RequestBody.create(source.body(), MediaType.parse("application/json; charset=utf-8"));
            if ("POST".equals(source.method().name()) && body == null) {
                body = RequestBody.create("", MediaType.parse("application/json"));
            }
            return request.method(source.method().name(), body).build();
        }

        @Override public SuccessfulHttpResponse execute(HttpRequest request) {
            Request approved = request(request);
            try (Response response = delegate.newCall(approved).execute()) {
                requireSuccess(response);
                byte[] body = response.body() == null ? new byte[0] : response.body().bytes();
                return response(response, body);
            } catch (IOException error) {
                throw new ServiceException("MCP连接请求失败");
            }
        }

        @Override public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new ServiceException("当前受控客户端仅用于市场GET，未接入的MCP流式结构不支持");
        }

        @Override public void close() {
            delegate.connectionPool().evictAll();
            delegate.dispatcher().executorService().shutdown();
        }

        private static void requireSuccess(Response response) {
            if (response.code() < 200 || response.code() >= 300) {
                throw new HttpException(response.code(), "MCP请求返回非成功状态");
            }
        }
        private static SuccessfulHttpResponse response(Response response, byte[] body) {
            return SuccessfulHttpResponse.builder().statusCode(response.code())
                .headers(response.headers().toMultimap()).body(body).build();
        }
    }
}
