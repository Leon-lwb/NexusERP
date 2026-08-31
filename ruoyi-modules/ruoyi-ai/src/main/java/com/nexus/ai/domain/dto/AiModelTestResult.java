package com.nexus.ai.domain.dto;

/**
 * AI 模型连接测试结果（不可变值对象）
 * <p>message 已脱敏（不含 API Key / Bearer 令牌，超长截断至 300 字符）；
 * 测试成功不落库、不自动保存模型。</p>
 *
 * @param success                   测试是否通过
 * @param message                   结果说明（失败原因已脱敏截断）
 * @param latencyMs                 上游调用耗时（毫秒）
 * @param providerType              厂商类型（ai_provider.provider_type）
 * @param apiProtocol               实际使用的调用协议（未配置时为厂商默认协议，回显供前端保存）
 * @param detectedEmbeddingDimension 实测向量维度（仅 EMBEDDING 测试返回，其余为 null）
 * @author nexus-school
 * @since 2.0.0
 */
public record AiModelTestResult(boolean success, String message, long latencyMs,
                                String providerType, String apiProtocol,
                                Integer detectedEmbeddingDimension) {

    /** 成功结果工厂（统一成功文案） */
    public static AiModelTestResult success(String providerType, String apiProtocol, long latencyMs,
                                            Integer detectedEmbeddingDimension) {
        return new AiModelTestResult(true, "连接测试通过", latencyMs, providerType, apiProtocol,
            detectedEmbeddingDimension);
    }

    /** 失败结果工厂（message 由调用方保证已脱敏） */
    public static AiModelTestResult failure(String providerType, String apiProtocol, long latencyMs,
                                            String message, Integer detectedEmbeddingDimension) {
        return new AiModelTestResult(false, message, latencyMs, providerType, apiProtocol,
            detectedEmbeddingDimension);
    }
}
