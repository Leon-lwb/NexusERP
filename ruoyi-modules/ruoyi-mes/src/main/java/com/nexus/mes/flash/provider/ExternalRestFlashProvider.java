package com.nexus.mes.flash.provider;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.flash.domain.MesFlashType;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URL;

/**
 * 外部REST刷写 Provider (source=EXTERNAL)
 * <p>从外部系统 REST 获取刷写内容(source_config 配 url + token + method)。</p>
 * <p><b>SSRF 防护(MES-FLASH-08)</b>: ① 仅 http/https; ② 解析主机名后禁止回环/内网/任播地址;
 * ③ HTTP 客户端<b>禁用重定向跟随</b>({@code setFollowRedirects(false)}), 3xx 一律拒绝 ——
 * 否则公网地址可用 302 把服务端引向内网(逐跳重新校验只在跟随的前提下才有意义, 这里直接不跟随);
 * ④ 若响应带 Location, 仍对新目标做一次 SSRF 校验并留痕, 以便将来放开跟随时不漏检。</p>
 * <p><b>可用性(MES-FLASH-08)</b>: 网络类失败按指数退避重试 {@value #MAX_ATTEMPTS} 次(含首次),
 * 最终仍失败抛 {@link ServiceException}(不再让 {@code IORuntimeException} 冒泡成 500);
 * 协议/安全/结构类错误是确定性失败, 不重试。</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalRestFlashProvider implements FlashTypeProvider {

    /** 单次 HTTP 超时(毫秒) */
    private static final int HTTP_TIMEOUT_MS = 5_000;
    /** 最大尝试次数(含首次) */
    static final int MAX_ATTEMPTS = 3;
    /** 响应内容最大长度(字符): 刷写内容不应是大文件, 超长视为异常响应 */
    static final int MAX_PAYLOAD_LEN = 64 * 1024;
    /** 默认退避基数(毫秒), 第 n 次失败后等待 n * 基数 */
    static final long DEFAULT_BACKOFF_MS = 200L;

    private final MesFlashTypeMapper flashTypeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 退避基数(毫秒); 包级可改, 供单测置 0 免等待 */
    private long retryBackoffMs = DEFAULT_BACKOFF_MS;

    /** 单测用: 覆盖退避基数(生产不调用) */
    void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
    }

    @Override
    public String getTypeCode() {
        return "external_rest";
    }

    @Override
    public String getSourceType() {
        return "EXTERNAL";
    }

    @Override
    public String getDisplayName() {
        return "外部REST刷写";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        MesFlashType ft = flashTypeMapper.selectOne(Wrappers.<MesFlashType>lambdaQuery()
            .eq(MesFlashType::getTypeCode, "external_rest").last("LIMIT 1"));
        if (ft == null || StringUtils.isBlank(ft.getSourceConfig())) {
            throw new ServiceException("external_rest 未配置 source_config(需含 url)");
        }
        String url;
        String token;
        try {
            JsonNode cfg = objectMapper.readTree(ft.getSourceConfig());
            url = cfg.has("url") ? cfg.get("url").asText() : null;
            token = cfg.has("token") ? cfg.get("token").asText() : null;
        } catch (Exception e) {
            throw new ServiceException("external_rest source_config 解析失败: " + e.getMessage());
        }
        if (StringUtils.isBlank(url)) {
            throw new ServiceException("source_config 缺 url");
        }
        // ① 出网前 SSRF 校验(协议 + 解析后 IP 归属)
        checkSsrf(url);
        String resp = fetchWithRetry(url, token);
        log.info("外部REST刷写取内容: snCode={}, url={}, respLen={}", ctx.getSnCode(), url, resp.length());
        return FlashContent.builder()
            .contentRefType("EXTERNAL")
            .contentPayload(resp)
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        // EXTERNAL类: 内容已获取即视为可刷写; 实际结果以上报 result 为准
        return FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType())
            .contentPayload(expected.getContentPayload())
            .build();
    }

    // ==================== HTTP ====================

    /**
     * 带退避重试的取内容: 网络类失败重试 {@value #MAX_ATTEMPTS} 次, 安全/协议类错误立即失败。
     * <p>失败统一抛 {@link ServiceException}, 避免 {@code IORuntimeException} 冒泡成 500。</p>
     */
    String fetchWithRetry(String url, String token) {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return fetchOnce(url, token);
            } catch (ServiceException e) {
                // 确定性失败(SSRF/重定向/4xx/结构非法): 重试无意义, 直接拒绝
                throw e;
            } catch (Exception e) {
                last = e;
                log.warn("外部REST刷写第{}/{}次尝试失败: url={}, err={}", attempt, MAX_ATTEMPTS, url, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(attempt * retryBackoffMs);
                }
            }
        }
        throw new ServiceException("外部REST刷写取内容失败(已重试" + MAX_ATTEMPTS + "次): " + url
            + " -> " + (last == null ? "未知错误" : last.getClass().getSimpleName() + ": " + last.getMessage()));
    }

    /**
     * 单次 GET: 禁用重定向跟随 + 3xx 拒绝 + 非 2xx 分类 + 响应结构/长度校验。
     * <p>包级可见以便单测直接覆盖重定向拒绝逻辑(SSRF 校验由调用方 {@link #resolveContent} 先行完成)。</p>
     */
    String fetchOnce(String url, String token) {
        HttpRequest req = HttpUtil.createGet(url).timeout(HTTP_TIMEOUT_MS).setFollowRedirects(false);
        if (StringUtils.isNotBlank(token)) {
            req.header("Authorization", token);
        }
        HttpResponse resp = req.execute();
        int status = resp.getStatus();
        if (status >= 300 && status < 400) {
            rejectRedirect(url, resp.header("Location"));
        }
        if (status >= 500) {
            // 5xx 视为可重试的上游故障
            throw new IORuntimeException("外部REST刷写返回 " + status);
        }
        if (status < 200 || status >= 300) {
            throw new ServiceException("外部REST刷写返回非 2xx: status=" + status + ", url=" + url);
        }
        String body = resp.body();
        validatePayload(url, body);
        return body;
    }

    /**
     * 响应校验: 非空 + 长度上限 + 形如 JSON 时必须是合法 JSON(结构校验后才落 contentPayload)。
     */
    static void validatePayload(String url, String body) {
        if (StringUtils.isBlank(body)) {
            throw new ServiceException("外部REST刷写返回内容为空: url=" + url);
        }
        if (body.length() > MAX_PAYLOAD_LEN) {
            throw new ServiceException("外部REST刷写返回内容超长(" + body.length() + " > " + MAX_PAYLOAD_LEN + "): url=" + url);
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                new ObjectMapper().readTree(trimmed);
            } catch (Exception e) {
                throw new ServiceException("外部REST刷写返回内容不是合法 JSON: url=" + url + ", err=" + e.getMessage());
            }
        }
    }

    /**
     * 拒绝重定向(MES-FLASH-08): 即便不跟随, 也对 Location 目标重做 SSRF 校验并留痕,
     * 防止将来放开跟随(或客户端行为变化)时静默绕过校验。
     */
    static void rejectRedirect(String url, String location) {
        if (StringUtils.isNotBlank(location)) {
            try {
                checkSsrf(resolveLocation(url, location));
            } catch (ServiceException e) {
                log.warn("外部REST刷写重定向目标校验不通过: {} -> {}, err={}", url, location, e.getMessage());
            }
        }
        throw new ServiceException("SSRF 防护: 外部REST刷写返回重定向, 已拒绝跟随: " + url + " -> " + location);
    }

    /** 相对 Location 按基址解析为绝对地址(解析失败时原样返回, 由后续校验兜底) */
    static String resolveLocation(String baseUrl, String location) {
        try {
            return new URL(new URL(baseUrl), location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * SSRF 基础校验: 仅 http/https, 且禁止回环/内网/任播地址(防止服务端请求伪造)。
     * <p>包级静态以便单测直接覆盖(不依赖真实出网)。</p>
     */
    static void checkSsrf(String url) {
        try {
            URL u = new URL(url);
            if (!"http".equals(u.getProtocol()) && !"https".equals(u.getProtocol())) {
                throw new ServiceException("SSRF: 仅允许 http/https 协议");
            }
            InetAddress addr = InetAddress.getByName(u.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
                throw new ServiceException("SSRF 防护: 禁止访问回环/内网地址 " + url);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("URL 校验失败: " + e.getMessage());
        }
    }
}
