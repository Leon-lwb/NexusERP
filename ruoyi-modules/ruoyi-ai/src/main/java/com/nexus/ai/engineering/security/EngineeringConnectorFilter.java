package com.nexus.ai.engineering.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.core.domain.R;
import com.nexus.common.tenant.helper.TenantHelper;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/** 仅保护连接器命名空间的独立设备认证边界，禁止扩大全局匿名路径。 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringConnectorFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL =
            EngineeringConnectorFilter.class.getName() + ".principal";
    private final EngineeringDeviceAuthenticator authenticator;
    private final ObjectMapper json;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith("/ai/engineering/connector/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String trace = UUID.randomUUID().toString();
        request.setAttribute("engineeringTraceId", trace);
        response.setHeader("X-Nexus-Trace-Id", trace);
        try {
            String path = path(request);
            if (path.equals("/ai/engineering/connector/pair")
                    || path.equals("/ai/engineering/connector/refresh")) {
                chain.doFilter(request, response);
                return;
            }
            var device = authenticator.authenticate(request.getHeader("X-Nexus-Device-Token"));
            request.setAttribute(PRINCIPAL, device);
            try {
                TenantHelper.dynamic(
                        device.actor().tenantId(),
                        () -> {
                            try {
                                chain.doFilter(request, response);
                            } catch (IOException | ServletException e) {
                                throw new ChainFailure(e);
                            }
                        });
            } catch (ChainFailure e) {
                if (e.getCause() instanceof IOException io) throw io;
                throw (ServletException) e.getCause();
            } finally {
                request.removeAttribute(PRINCIPAL);
            }
        } catch (EngineeringException e) {
            response.setStatus(e.status());
            response.setContentType("application/json;charset=UTF-8");
            var error = R.fail(e.getMessage(), Map.of("errorCode", e.code(), "traceId", trace));
            error.setCode(e.status());
            json.writeValue(response.getOutputStream(), error);
        }
    }

    /** Controller 强制要求过滤器设置的属性；缺少 Filter 时同样拒绝请求。 */
    public static EngineeringDevicePrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(PRINCIPAL);
        EngineeringException.require(
                value instanceof EngineeringDevicePrincipal, 401, "DEVICE_REVOKED", "缺少设备认证上下文");
        return (EngineeringDevicePrincipal) value;
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static class ChainFailure extends RuntimeException {
        ChainFailure(Exception e) {
            super(e);
        }
    }
}
