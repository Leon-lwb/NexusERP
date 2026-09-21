package com.nexus.iot.forward;

import java.util.Map;

/**
 * IoT 转发 HTTP 客户端抽象。
 * <p>
 * 抽出接口而非直接静态调用 Hutool：既便于单测注入失败/成功响应（无需 mock 静态工具类），
 * 也为后续替换为带连接池/签名/重定向控制的实现留出扩展点。
 *
 * @author nexus
 */
public interface IotForwardHttpClient {

    /**
     * 执行一次投递。
     *
     * @param url      目标地址（调用方已通过 SSRF 校验）
     * @param method   HTTP 方法（POST/PUT）
     * @param headers  自定义请求头
     * @param body     请求体（JSON）
     * @param timeoutMs 超时（毫秒）
     * @return 投递结果
     */
    DeliveryResult post(String url, String method, Map<String, String> headers, String body, int timeoutMs);

    /**
     * 投递结果。
     *
     * @param success    是否成功（2xx）
     * @param httpStatus HTTP 状态码（异常时为 -1）
     * @param message    结果说明（失败原因/响应片段）
     */
    record DeliveryResult(boolean success, int httpStatus, String message) {
    }
}
