package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ShortageSimulationResult(
    boolean hasRisk,
    int riskMaterialCount,
    LocalDate earliestShortageDate,
    BigDecimal totalGap,
    List<Item> items,
    DecisionKpiSet kpis,
    List<String> evidenceRefs,
    List<String> warnings
) implements Serializable {

    public record Item(
        Long materialId,
        String materialCode,
        String materialName,
        LocalDate firstShortageDate,
        BigDecimal maxGap,
        BigDecimal onOrderQty,
        List<Long> affectedWorkOrderIds,
        List<Long> affectedSalesOrderIds,
        String recommendationCode,
        List<String> evidenceKeys
    ) implements Serializable {
    }
}
