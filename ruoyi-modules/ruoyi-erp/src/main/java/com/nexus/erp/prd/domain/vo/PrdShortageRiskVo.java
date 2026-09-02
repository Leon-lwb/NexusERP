package com.nexus.erp.prd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 缺料风险预测 视图对象(推演型, 不落库)
 */
@Data
public class PrdShortageRiskVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long materialId;
    private String materialCode;
    private String materialName;
    /** 当前可用库存(已扣预留) */
    private BigDecimal stockQty;
    /** 安全库存 */
    private BigDecimal safetyStock;
    /** 推演期内总毛需求 */
    private BigDecimal totalDemand;
    /** 在途采购总量 */
    private BigDecimal onOrderQty;
    /** 最早缺料日(无风险为 null) */
    private LocalDate earliestShortageDate;
    /** 距最早缺料日天数(负数=已缺料; 无风险为 null) */
    private Long daysUntilShortage;
    /** 最大缺口 */
    private BigDecimal maxGap;
    /** 缺口天数 */
    private Long gapDays;
    /** 建议动作: URGE催货/PURCHASE提前采购/ADJUST_SAFETY/null */
    private String suggestion;
    /** 建议动作说明 */
    private String suggestionText;
    /** 是否有风险 */
    private Boolean hasRisk;
}
