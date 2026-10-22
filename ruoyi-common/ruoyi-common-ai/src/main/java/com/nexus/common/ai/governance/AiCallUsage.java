package com.nexus.common.ai.governance;

/** 厂商用量原值；缓存和推理为输入/输出的子集，不再次累加。null 表示未知。 */
public record AiCallUsage(
        Long inputTokens,
        Long outputTokens,
        Long cachedTokens,
        Long reasoningTokens,
        String source) {
    public AiCallUsage {
        for (Long value : new Long[] {inputTokens, outputTokens, cachedTokens, reasoningTokens})
            if (value != null && value < 0) throw new IllegalArgumentException("Token数量不能为负数");
    }

    public static AiCallUsage unknown() {
        return new AiCallUsage(null, null, null, null, "UNKNOWN");
    }

    public boolean known() {
        return inputTokens != null && outputTokens != null;
    }
}
