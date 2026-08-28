package com.nexus.common.ai.service;

import org.jasypt.encryption.StringEncryptor;

/**
 * 厂商 API Key 解密器（Jasypt 密文 → 明文，兼容存量明文）
 * <p>自 {@code AiModelServiceImpl.decryptApiKey} 抽取为共享工具：连接测试等调用方必须与
 * 模型配置加载走<b>完全相同</b>的解密路径（Jasypt 解密失败视为明文兜底，兼容未加密旧数据），
 * 避免两处实现漂移。纯静态工具，无 Spring 依赖。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
public final class AiApiKeyDecryptor {

    private AiApiKeyDecryptor() {
    }

    /**
     * 解密 API Key（Jasypt）
     * <p>数据库中存储的是 Jasypt 加密后的密文，构建上游客户端时需要明文；
     * 解密失败时原样返回（兼容历史明文数据）。</p>
     *
     * @param stringEncryptor  Jasypt 加密器（jasyptStringEncryptor 单例 Bean）
     * @param encryptedApiKey 加密的 API Key（null/空串原样返回）
     * @return 解密后的 API Key（解密失败返回原文）
     */
    public static String decrypt(StringEncryptor stringEncryptor, String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isEmpty()) {
            return encryptedApiKey;
        }
        try {
            return stringEncryptor.decrypt(encryptedApiKey);
        } catch (Exception e) {
            // 如果解密失败，说明可能是明文（兼容旧数据）
            return encryptedApiKey;
        }
    }
}
