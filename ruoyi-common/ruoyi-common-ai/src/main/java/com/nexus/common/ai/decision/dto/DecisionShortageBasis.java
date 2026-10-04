package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单次冻结运行的逐物料排名依据。调用方须先核验 ERP 来源及基础单位身份；
 * 本合同不执行单位换算，不是采购授权，也不以缺失清单冒充完整的零缺料结果。
 */
public record DecisionShortageBasis(
    String runId, String snapshotEvidenceKey, LocalDate windowStart, LocalDate windowEnd,
    List<Requirement> requirements, List<Gap> gaps, String simulationEvidenceKey
) implements Serializable {
    public DecisionShortageBasis {
        requirements = requirements == null ? null : List.copyOf(requirements);
        gaps = gaps == null ? null : List.copyOf(gaps);
    }

    /** 数量已经净算为 required-issued；来源明细与基础 UOM 均须经 ERP 核验。 */
    public record Requirement(Long detailId, Long materialId, LocalDate requiredDate,
                              BigDecimal requiredQty, Long baseUomId) implements Serializable { }

    /** 每个物料均须提供显式缺口，含零值；单位必须与需求分母的基础 UOM 相同。 */
    public record Gap(Long materialId, Long baseUomId, BigDecimal maxGap,
                      LocalDate peakDate, String sourceEvidenceKey) implements Serializable { }
}
