package com.nexus.iot.forward;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * IoT 数据转发 URL 安全校验（SSRF 防护）。
 * <p>
 * 与 {@code ruoyi-flow} 的 {@code HttpNodeProvider.sanitizeUrl} 同一安全口径
 * （仅 http/https；解析主机全部 A/AAAA 记录，任一命中内网/保留地址即拒绝），
 * 并额外加固：
 * <ul>
 *   <li>拒绝带 userinfo 的 URL（{@code http://user@host/}，可干扰主机解析）；</li>
 *   <li>显式封堵 CGNAT（100.64/10）、0.0.0.0/8、169.254/16、IPv6 ULA（fc00::/7）等
 *       JDK 分类可能漏判的网段；</li>
 *   <li>投递前再次调用（防 DNS rebinding：登记时合法、投递时解析到内网）。</li>
 * </ul>
 * 注意：本校验**不做**重定向后的二次校验，因此建议目标服务不返回 3xx；
 * 若需要更强保证，应改用固定 IP + Host 头方式投递（见报告"未完成项"）。
 *
 * @author nexus
 */
@Slf4j
public final class IotForwardUrlValidator {

    private IotForwardUrlValidator() {
    }

    /**
     * 校验并净化 URL。
     *
     * @param url 原始 URL
     * @return 净化后的 URL；不合法或指向禁止地址返回 null
     */
    public static String sanitize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.warn("[SSRF] 拒绝非 http/https 协议: {}", url);
                return null;
            }
            if (uri.getUserInfo() != null) {
                log.warn("[SSRF] 拒绝携带 userinfo 的 URL: {}", url);
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("[SSRF] URL 缺少 host: {}", url);
                return null;
            }
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs.length == 0) {
                log.warn("[SSRF] 主机无法解析: {}", host);
                return null;
            }
            for (InetAddress addr : addrs) {
                if (isBlocked(addr)) {
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

    /**
     * 判断地址是否属于禁止投递的网段。
     *
     * @param addr 已解析地址
     * @return 属内网/保留/回环等禁止网段返回 true
     */
    public static boolean isBlocked(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
            || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;
            // 0.0.0.0/8（本网络）、127/8（回环）、10/8、172.16/12、192.168/16、169.254/16（链路本地）、100.64/10（CGNAT）
            return b0 == 0 || b0 == 127 || b0 == 10
                || (b0 == 172 && b1 >= 16 && b1 <= 31)
                || (b0 == 192 && b1 == 168)
                || (b0 == 169 && b1 == 254)
                || (b0 == 100 && b1 >= 64 && b1 <= 127);
        }
        // IPv6：fc00::/7（唯一本地地址 ULA）
        return (raw[0] & 0xFE) == 0xFC;
    }
}
