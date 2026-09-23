package com.nexus.common.ai.decision.enums;

public enum DecisionRiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static DecisionRiskLevel fromScore(double score) {
        if (!Double.isFinite(score) || score < 0 || score > 100) {
            throw new IllegalArgumentException("风险分数必须在0到100之间");
        }
        if (score >= 70) return CRITICAL;
        if (score >= 40) return HIGH;
        if (score >= 20) return MEDIUM;
        return LOW;
    }
}
