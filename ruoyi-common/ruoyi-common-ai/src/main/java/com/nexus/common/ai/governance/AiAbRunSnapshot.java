package com.nexus.common.ai.governance;

import java.util.List;

/** 批次冻结的问题与非敏感模型引用，永不包含厂商密钥。 */
public record AiAbRunSnapshot(
        List<String> questions, Model modelA, Model modelB, String datasetVersion) {
    public record Model(AiPriceSnapshot pricing, String configurationFingerprint) {}
}
