package com.nexus.ai.mcp;

import com.nexus.common.core.exception.ServiceException;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

/** MCP 外连统一边界：默认只允许公网，运维可显式授权精确的本地数字 IP 端点。 */
public final class McpEndpointPolicy {
    private McpEndpointPolicy() {
    }

    /** 未显式传入运维配置时保持原来的公网策略，不能默认放行本机。 */
    public static String validate(String url) {
        return validate(url, "");
    }

    /** 配置只来自服务端启动参数；任何非法条目均 fail-closed，错误信息不包含地址原文。 */
    public static String validate(String url, String allowedLocalEndpoints) {
        return approve(url, allowedLocalEndpoints).url();
    }

    /** 连接必须使用这个不可变解析快照，不能验证后再交给另一套默认DNS重新解析。 */
    public record ApprovedEndpoint(String url, String host, List<InetAddress> addresses) {
        public ApprovedEndpoint { addresses = List.copyOf(addresses); }
    }

    public static ApprovedEndpoint approve(String url, String allowedLocalEndpoints) {
        try {
            Map<String, InetAddress> allowed = parseAllowlist(allowedLocalEndpoints);
            URI uri = URI.create(url);
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new ServiceException("MCP 地址仅支持无用户信息、无片段的 HTTP(S) URL");
            }
            InetAddress approvedLiteral = allowed.get(url);
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw new ServiceException("MCP 地址无法解析");
            }
            for (InetAddress address : addresses) {
                if (approvedLiteral != null) {
                    // 只放行完整 URI 对应的数字 IP，不把例外扩展到其他 DNS 候选。
                    if (!approvedLiteral.equals(address)) {
                        throw new ServiceException("MCP 本地白名单地址解析结果不匹配");
                    }
                } else if (isPrivateOrReserved(address)) {
                    throw new ServiceException("MCP 地址不允许访问未授权的内网、环回或保留地址");
                }
            }
            String host = uri.getHost();
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            return new ApprovedEndpoint(url, host, Arrays.asList(addresses));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("MCP 地址格式无效或无法解析");
        }
    }

    /**
     * 当前实际StreamableHttpMcpTransport没有HTTP-client/DNS注入口。
     * 在升级到可绑定解析器的库前，REMOTE协议仅支持规范数字IP；不能验证域名后默认重新解析。
     * 原URL/TLS保持不变，私网仍必须命中运维的完整精确URI白名单。
     */
    public static String validateRemoteTransport(String url, String allowedLocalEndpoints) {
        ApprovedEndpoint endpoint = approve(url, allowedLocalEndpoints);
        try {
            parseLiteralAddress(endpoint.host());
        } catch (Exception error) {
            throw new ServiceException("当前MCP传输库不支持绑定DNS解析，REMOTE仅支持经批准的规范数字IP地址");
        }
        return endpoint.url();
    }

    private static Map<String, InetAddress> parseAllowlist(String configured) {
        Map<String, InetAddress> endpoints = new LinkedHashMap<>();
        if (configured == null || configured.isBlank()) {
            return endpoints;
        }
        try {
            for (String part : configured.split(",", -1)) {
                String endpoint = part.trim();
                URI uri = URI.create(endpoint);
                String path = uri.getRawPath();
                if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path == null || path.isEmpty() || !path.startsWith("/")
                    || !path.equals(uri.normalize().getRawPath()) || path.contains("%") || endpoint.contains("*")) {
                    throw new IllegalArgumentException();
                }
                // 保留完整 URI 原文作为键，不做前缀、默认端口、路径或编码归一化匹配。
                endpoints.put(endpoint, parseLiteralAddress(uri.getHost()));
            }
        } catch (Exception e) {
            throw new ServiceException("nexus.ai.mcp.allowed-local-endpoints 配置无效，请使用完整的精确数字 IP URI");
        }
        return endpoints;
    }

    /** 不允许 DNS 名称、网段、缩写 IPv4 或 scope-id，避免白名单扩大地址解析范围。 */
    private static InetAddress parseLiteralAddress(String host) throws Exception {
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (literal.contains(":") && literal.matches("[0-9a-fA-F:]+")) {
            return InetAddress.getByName(literal);
        }
        if (!literal.matches("[0-9]+(\\.[0-9]+){3}")) {
            throw new IllegalArgumentException();
        }
        byte[] bytes = new byte[4];
        String[] octets = literal.split("\\.");
        for (int i = 0; i < octets.length; i++) {
            int number = Integer.parseInt(octets[i]);
            if (number > 255 || !Integer.toString(number).equals(octets[i])) {
                throw new IllegalArgumentException();
            }
            bytes[i] = (byte) number;
        }
        return InetAddress.getByAddress(bytes);
    }

    private static boolean isPrivateOrReserved(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
            || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        if (bytes.length == 16) {
            // Java 的 isSiteLocalAddress 不覆盖 IPv6 ULA（fc00::/7）。
            return (first & 0xfe) == 0xfc;
        }
        int second = bytes[1] & 0xff;
        return first == 0 || first >= 224
            || (first == 100 && second >= 64 && second <= 127)
            || (first == 192 && second == 0)
            || (first == 198 && (second == 18 || second == 19));
    }
}
