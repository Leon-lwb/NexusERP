package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求节点执行器
 * <p>调用外部 HTTP API（GET/POST/PUT/DELETE/PATCH），URL/请求头/请求体支持 {{path}} 模板渲染。
 * 内置 SSRF 防护（复用 {@link com.nexus.ai.tool.WebSearchTool} 的 sanitizeUrl 模式，拒绝内网/保留地址）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class HttpNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 响应体最大保留长度（超出截断） */
    private static final int MAX_BODY_LENGTH = 100_000;

    @Override
    public String getNodeType() {
        return "http";
    }

    @Override
    public String getDisplayName() {
        return "HTTP 请求";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema method = new NodeParamSchema("method", "请求方法", "options", "GET", true);
        method.setOptions(List.of(
            Map.of("name", "GET", "value", "GET"),
            Map.of("name", "POST", "value", "POST"),
            Map.of("name", "PUT", "value", "PUT"),
            Map.of("name", "DELETE", "value", "DELETE"),
            Map.of("name", "PATCH", "value", "PATCH")
        ));
        NodeParamSchema headers = new NodeParamSchema("headers", "请求头", "json", "", false);
        headers.setEditor("keyvalue");
        return List.of(
            method,
            new NodeParamSchema("url", "请求 URL", "string", "", true),
            headers,
            new NodeParamSchema("body", "请求体", "textarea", "", false),
            new NodeParamSchema("timeoutSeconds", "超时(秒)", "number", "15", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("url") == null || config.get("url").asText().isBlank()) {
            throw new IllegalArgumentException("HTTP 节点缺少 url 配置");
        }
        String method = config.has("method") && !config.get("method").isNull()
            ? config.get("method").asText() : "GET";
        if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method.toUpperCase())) {
            throw new IllegalArgumentException("HTTP 节点 method 不支持: " + method);
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws Exception {
        JsonNode config = ctx.getConfig();
        String method = config.has("method") && !config.get("method").isNull()
            ? config.get("method").asText().toUpperCase() : "GET";
        int timeoutSeconds = config.has("timeoutSeconds") && config.get("timeoutSeconds").canConvertToInt()
            ? Math.max(1, Math.min(config.get("timeoutSeconds").asInt(), 120)) : 15;

        // 渲染模板变量
        String url = FlowTemplateRenderer.render(config.get("url").asText(), ctx.getState(),
            "节点 " + ctx.getNodeName() + " 的 url");
        String body = null;
        if (config.has("body") && !config.get("body").isNull()) {
            body = FlowTemplateRenderer.render(config.get("body").asText(), ctx.getState(),
                "节点 " + ctx.getNodeName() + " 的 body");
        }

        // SSRF 防护（对齐 WebSearchTool.sanitizeUrl，仅 http/https + 拒绝内网/保留地址）
        String safeUrl = sanitizeUrl(url);
        if (safeUrl == null) {
            throw new IllegalArgumentException("URL 不合法或指向禁止访问的地址（仅允许 http/https，且禁止内网/环回/链路本地地址）: " + url);
        }

        // 构建请求
        cn.hutool.http.HttpRequest request = new cn.hutool.http.HttpRequest(safeUrl)
            .method(cn.hutool.http.Method.valueOf(method))
            .timeout(timeoutSeconds * 1000);

        // 请求头（JSON 对象，值支持 {{}} 渲染）
        JsonNode headers = config.get("headers");
        if (headers != null && headers.isObject()) {
            var it = headers.fields();
            while (it.hasNext()) {
                var entry = it.next();
                request.header(entry.getKey(), FlowTemplateRenderer.render(
                    entry.getValue() == null || entry.getValue().isNull() ? "" : entry.getValue().asText(),
                    ctx.getState(), "节点 " + ctx.getNodeName() + " 的 headers." + entry.getKey()));
            }
        }
        if (body != null && !body.isBlank()) {
            request.body(body);
        }

        log.info("[Flow] HTTP 节点 {} 请求: {} {}", ctx.getNodeName(), method, safeUrl);
        cn.hutool.http.HttpResponse response = request.execute();
        String responseBody = response.body();
        if (responseBody == null) {
            responseBody = "";
        }
        boolean truncated = responseBody.length() > MAX_BODY_LENGTH;
        if (truncated) {
            responseBody = responseBody.substring(0, MAX_BODY_LENGTH);
        }
        response.close();

        // 组装输出
        ObjectNode output = MAPPER.createObjectNode();
        output.put("status", response.getStatus());
        ObjectNode headerNode = output.putObject("headers");
        response.headers().forEach((k, v) -> {
            // 响应头 key/value 可能为 null/空（ObjectNode.put 对 null 抛 NPE），跳过
            if (k != null && v != null && !v.isEmpty() && v.get(0) != null) {
                headerNode.put(k, v.get(0));
            }
        });
        output.put("body", responseBody);
        output.put("bodyTruncated", truncated);
        // 尽力解析 JSON 响应体（供后续节点按字段引用）
        if (!responseBody.isBlank()) {
            try {
                JsonNode parsed = MAPPER.readTree(responseBody);
                if (parsed.isContainerNode()) {
                    output.set("bodyJson", parsed);
                }
            } catch (Exception ignore) {
                // 非 JSON 响应，仅保留 body 文本
            }
        }
        return output;
    }

    /**
     * 校验并净化 URL，防止 SSRF
     * <p>仅允许 http/https 协议；解析主机地址后拒绝内网/环回/链路本地/任意本地/组播地址
     * （对齐 {@code WebSearchTool.sanitizeUrl} 的既有实现，保证安全口径一致）</p>
     *
     * @param url 原始 URL
     * @return 净化后的 URL；不合法或指向禁止地址返回 null
     */
    static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.warn("[SSRF] 拒绝非 http/https 协议: {}", url);
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("[SSRF] URL 缺少 host: {}", url);
                return null;
            }
            // 解析主机 IP，任一地址命中内网/保留段即拒绝
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress()) {
                    log.warn("[SSRF] 拒绝访问内网/保留地址: host={}, addr={}", host, addr.getHostAddress());
                    return null;
                }
            }
            return uri.toString();
        } catch (URISyntaxException | UnknownHostException e) {
            log.warn("[SSRF] URL 解析失败: {}", url, e);
            return null;
        }
    }
}
