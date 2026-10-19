package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 读取并重放原JSON，兼容旧BO/entity和原先没有RequestBody参数的执行URL。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class WmSerialPayloadFilter extends OncePerRequestFilter {
    public static final int MAX_BODY = 2 * 1024 * 1024;
    public static final String BODY = WmSerialPayloadFilter.class.getName() + ".body";
    private final ObjectMapper json;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SerialBusinessRoute.ofPath(
                                request.getRequestURI()
                                        .substring(request.getContextPath().length()))
                        == null
                || !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))
                || request.getContentType() != null
                        && !request.getContentType()
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains("json");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            response.sendError(413, "SN业务请求体过大");
            return;
        }
        JsonNode body = bytes.length == 0 ? json.createObjectNode() : json.readTree(bytes);
        request.setAttribute(BODY, body);
        String explicit = request.getHeader("X-Idempotent-Key");
        String command = body.path("commandId").asText("");
        // 新命令与SN实物参与原PDA幂等键，防止旧typed BO忽略字段导致不同扫描被吞成重放。
        String key =
                command.isBlank()
                        ? explicit
                        : "SERIAL/"
                                + WmSerialBusinessStore.hash(
                                        (explicit == null ? "" : explicit)
                                                + "/"
                                                + SerialRequestFingerprint.canonical(body, "")
                                                        .toString());
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public ServletInputStream getInputStream() {
                        var stream = new ByteArrayInputStream(bytes);
                        return new ServletInputStream() {
                            @Override
                            public int read() {
                                return stream.read();
                            }

                            @Override
                            public int read(byte[] b, int off, int len) {
                                return stream.read(b, off, len);
                            }

                            @Override
                            public boolean isFinished() {
                                return stream.available() == 0;
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setReadListener(ReadListener listener) {
                                throw new UnsupportedOperationException("此业务JSON使用同步读取");
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(
                                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                    }

                    @Override
                    public String getHeader(String name) {
                        return "X-Idempotent-Key".equalsIgnoreCase(name)
                                ? key
                                : super.getHeader(name);
                    }

                    @Override
                    public java.util.Enumeration<String> getHeaders(String name) {
                        return "X-Idempotent-Key".equalsIgnoreCase(name) && key != null
                                ? java.util.Collections.enumeration(java.util.List.of(key))
                                : super.getHeaders(name);
                    }
                },
                response);
    }
}
