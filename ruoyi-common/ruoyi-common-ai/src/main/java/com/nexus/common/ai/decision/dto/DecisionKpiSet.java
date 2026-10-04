package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record DecisionKpiSet(
    int impactedOrderCount,
    int delayedWorkOrderCount,
    int totalTardinessDays,
    int maxTardinessDays,
    String makespanDate,
    int shortageMaterialCount,
    BigDecimal totalShortageGap,
    String earliestShortageDate,
    BigDecimal maxResourceUtilization,
    int overloadedResourceCount,
    int changedWorkOrderCount,
    BigDecimal actionCost,
    /**
     * 经逐物料基础单位、需求分母和来源证据核验后的原始缺口比例 R。
     * null 表示当前 KPI 不具备可排名的量纲安全比例，不能回退到 totalShortageGap。
     */
    BigDecimal shortageGapRatio,
    DecisionShortageBasis shortageBasis
) implements Serializable {
    /** 兼容历史 Java/JSON 装配；缺少新比例时必须由排名层明确标记不可排名。 */
    public DecisionKpiSet(int impactedOrderCount, int delayedWorkOrderCount, int totalTardinessDays,
                          int maxTardinessDays, String makespanDate, int shortageMaterialCount,
                          BigDecimal totalShortageGap, String earliestShortageDate,
                          BigDecimal maxResourceUtilization, int overloadedResourceCount,
                          int changedWorkOrderCount, BigDecimal actionCost) {
        this(impactedOrderCount, delayedWorkOrderCount, totalTardinessDays, maxTardinessDays,
            makespanDate, shortageMaterialCount, totalShortageGap, earliestShortageDate,
            maxResourceUtilization, overloadedResourceCount, changedWorkOrderCount, actionCost, null, null);
    }
    /** 兼容只含比例的过渡调用；裸比例不是可排名证明，仍需逐物料依据。 */
    public DecisionKpiSet(int impactedOrderCount, int delayedWorkOrderCount, int totalTardinessDays,
                          int maxTardinessDays, String makespanDate, int shortageMaterialCount,
                          BigDecimal totalShortageGap, String earliestShortageDate,
                          BigDecimal maxResourceUtilization, int overloadedResourceCount,
                          int changedWorkOrderCount, BigDecimal actionCost, BigDecimal shortageGapRatio) {
        this(impactedOrderCount, delayedWorkOrderCount, totalTardinessDays, maxTardinessDays,
            makespanDate, shortageMaterialCount, totalShortageGap, earliestShortageDate,
            maxResourceUtilization, overloadedResourceCount, changedWorkOrderCount, actionCost,
            shortageGapRatio, null);
    }

}
