package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性工业风险数值计算，不使用大模型输出作为评分输入。
 */
@Service
@RequiredArgsConstructor
public class DeliveryRiskScoringService {
    private final AiProperties properties;

    int tardinessPoints(int days) {
        if (days <= 0) return 0;
        if (days <= 2) return 15;
        if (days <= 5) return 25;
        return properties.getDecision().getRisk().getTardinessPoints();
    }

    int shortagePoints(int daysUntilFirstShortage) {
        if (daysUntilFirstShortage == Integer.MAX_VALUE) return 0;
        if (daysUntilFirstShortage > 30) return 10;
        if (daysUntilFirstShortage > 7) return 20;
        return properties.getDecision().getRisk().getShortagePoints();
    }

    int capacityPoints(double utilization) {
        if (!Double.isFinite(utilization) || utilization < 0) {
            throw new IllegalArgumentException("资源利用率不是有效非负数值");
        }
        if (utilization < 80) return 0;
        if (utilization < 90) return 5;
        if (utilization <= 100) return 12;
        return properties.getDecision().getRisk().getCapacityPoints();
    }

    int supplierPoints(double onTimeRate, int sampleCount) {
        if (sampleCount < 3) return 0;
        if (!Double.isFinite(onTimeRate) || onTimeRate < 0 || onTimeRate > 100) {
            throw new IllegalArgumentException("供应商准时率不是有效百分比");
        }
        if (onTimeRate >= 95) return 0;
        if (onTimeRate >= 85) return 3;
        if (onTimeRate >= 70) return 6;
        return properties.getDecision().getRisk().getSupplierPoints();
    }

    public RiskResult score(RiskInput input) {
        if (input == null) throw new IllegalArgumentException("风险评分输入不能为空");
        if (input.tardinessDays() < 0 || input.supplierSampleCount() < 0) {
            throw new IllegalArgumentException("风险评分天数和样本数不能为负数");
        }
        int delay = tardinessPoints(input.tardinessDays());
        int shortage = input.daysUntilFirstShortage() == null ? 0
            : shortagePoints(input.daysUntilFirstShortage());
        int capacity = input.maxUtilization() == null ? 0 : capacityPoints(input.maxUtilization());
        boolean supplierReliable = input.supplierSampleCount() >= 3 && input.supplierOnTimeRate() != null;
        int supplier = supplierReliable ? supplierPoints(input.supplierOnTimeRate(), input.supplierSampleCount()) : 0;
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("tardiness", delay);
        dimensions.put("shortage", shortage);
        dimensions.put("capacity", capacity);
        dimensions.put("supplier", supplier);
        List<String> rootCauses = new ArrayList<>();
        if (delay > 0) rootCauses.add("DELIVERY_DELAY");
        if (shortage > 0) rootCauses.add("MATERIAL_SHORTAGE");
        if (capacity > 0) rootCauses.add("CAPACITY_BOTTLENECK");
        if (supplier > 0) rootCauses.add("SUPPLIER_DELAY");
        if (!supplierReliable) rootCauses.add("SUPPLIER_EVIDENCE_INSUFFICIENT");
        if (input.daysUntilFirstShortage() == null || !input.stockComplete() || !input.mrpComplete()) {
            rootCauses.add("SHORTAGE_EVIDENCE_INSUFFICIENT");
        }
        if (input.maxUtilization() == null || !input.capacityComplete()) {
            rootCauses.add("CAPACITY_EVIDENCE_INSUFFICIENT");
        }
        if (!input.currentSchedulePresent() || !input.routesComplete()) {
            rootCauses.add("SCHEDULE_EVIDENCE_INSUFFICIENT");
        }
        // 核心事实未知不等于零风险，此时不得发布分数或等级。
        boolean missingCritical = !input.currentSchedulePresent() || !input.routesComplete()
            || !input.stockComplete() || !input.mrpComplete() || !input.capacityComplete()
            || input.daysUntilFirstShortage() == null || input.maxUtilization() == null
            || !input.purchaseEvidenceComplete() && input.daysUntilFirstShortage() == Integer.MAX_VALUE;
        if (missingCritical) return new RiskResult(null, null, dimensions, rootCauses, "LOW");
        double score = Math.min(100, delay + shortage + capacity + supplier);
        String confidence = !supplierReliable || !input.purchaseEvidenceComplete() ? "MEDIUM" : "HIGH";
        return new RiskResult(score, DecisionRiskLevel.fromScore(score), dimensions, rootCauses, confidence);
    }

    /**
     * 显式证据护栏，空数值表示未知而非零。
     */
    public record RiskInput(int tardinessDays, Integer daysUntilFirstShortage,
                            Double maxUtilization, Double supplierOnTimeRate, int supplierSampleCount,
                            boolean currentSchedulePresent, boolean routesComplete, boolean stockComplete,
                            boolean mrpComplete, boolean purchaseEvidenceComplete, boolean capacityComplete) { }

    public record RiskResult(Double score, DecisionRiskLevel level, Map<String, Integer> dimensionScores,
                             List<String> rootCauseCodes, String confidence) {
        public RiskResult {
            dimensionScores = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionScores));
            rootCauseCodes = List.copyOf(rootCauseCodes);
        }
    }
}
