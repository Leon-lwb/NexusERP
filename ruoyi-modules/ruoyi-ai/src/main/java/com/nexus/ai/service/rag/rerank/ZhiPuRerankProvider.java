package com.nexus.ai.service.rag.rerank;

import com.nexus.common.ai.domain.entity.AiModel;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 智谱 AI（GLM）Rerank 厂商
 * <p>端点：{apiHost}/api/paas/v4/rerank</p>
 * <p>鉴权：JWT（apiKey 格式为 id.secret，用 HmacSHA256 签名，无需 jjwt 依赖）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class ZhiPuRerankProvider extends AbstractHttpRerankProvider {

    @Override
    public String getProviderType() {
        return "ZHIPU";
    }

    @Override
    protected String buildUrl(AiModel config) {
        return config.getApiHost() + "/api/paas/v4/rerank";
    }

    @Override
    protected String authHeader(AiModel config) {
        return "Bearer " + generateZhipuToken(config.getApiKey());
    }

    /**
     * 手写智谱 JWT（HmacSHA256），避免引入 jjwt 依赖
     * <p>apiKey 格式：{id}.{secret}</p>
     * <p>header: {"alg":"HS256","sign_type":"SIGN"}</p>
     * <p>payload: {"api_key":id,"exp":now+30min,"timestamp":now}（毫秒）</p>
     * <p>signature: HmacSHA256(secret, base64url(header)+"."+base64url(payload))</p>
     */
    private String generateZhipuToken(String apiKey) {
        try {
            String[] parts = apiKey.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("智谱 apiKey 格式错误（应为 id.secret）");
            }
            String keyId = parts[0];
            String secret = parts[1];

            long now = System.currentTimeMillis();
            long exp = now + 1000L * 60 * 30; // 30 分钟有效期

            String headerJson = "{\"alg\":\"HS256\",\"sign_type\":\"SIGN\"}";
            String payloadJson = "{\"api_key\":\"" + keyId + "\",\"exp\":" + exp + ",\"timestamp\":" + now + "}";

            String encHeader = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String encPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encHeader + "." + encPayload;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));

            return encHeader + "." + encPayload + "." + base64Url(sig);
        } catch (Exception e) {
            throw new RuntimeException("生成智谱 Token 失败: " + e.getMessage(), e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
