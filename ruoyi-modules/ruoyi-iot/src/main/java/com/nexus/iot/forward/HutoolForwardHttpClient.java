package com.nexus.iot.forward;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于 Hutool 的转发 HTTP 客户端实现。
 * <p>
 * 不自动跟随重定向（Hutool 默认不跟随，除非显式调用 {@code setFollowRedirects(true)}），
 * 避免"SSRF 校验通过后被 302 到内网地址"。
 *
 * @author nexus
 */
@Slf4j
@Component
public class HutoolForwardHttpClient implements IotForwardHttpClient {

    /**
     * 执行一次投递。
     *
     * @param url       目标地址
     * @param method    HTTP 方法（POST/PUT）
     * @param headers   自定义请求头
     * @param body      请求体
     * @param timeoutMs 超时（毫秒）
     * @return 投递结果
     */
    @Override
    public DeliveryResult post(String url, String method, Map<String, String> headers, String body, int timeoutMs) {
        try {
            HttpRequest request = "PUT".equalsIgnoreCase(method) ? HttpRequest.put(url) : HttpRequest.post(url);
            request.timeout(timeoutMs <= 0 ? 5000 : timeoutMs)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(body == null ? "" : body);
            if (headers != null) {
                headers.forEach((k, v) -> {
                    if (StringUtils.isNotBlank(k) && v != null) {
                        request.header(k, v);
                    }
                });
            }
            try (HttpResponse response = request.execute()) {
                int status = response.getStatus();
                String respBody = StringUtils.substring(response.body(), 0, 200);
                boolean ok = status >= 200 && status < 300;
                if (!ok) {
                    log.warn("转发投递失败：url={}, status={}, resp={}", url, status, respBody);
                }
                return new DeliveryResult(ok, status, respBody);
            }
        } catch (Exception e) {
            log.warn("转发投递异常：url={}, err={}", url, e.getMessage());
            return new DeliveryResult(false, -1, e.getMessage());
        }
    }
}
