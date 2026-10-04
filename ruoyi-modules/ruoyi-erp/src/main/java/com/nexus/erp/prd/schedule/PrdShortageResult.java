package com.nexus.erp.prd.schedule;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 缺料推演结果(物料级)
 */
@Data
public class PrdShortageResult {
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 当前可用库存(已扣预留) */
    private BigDecimal stockQty = BigDecimal.ZERO;
    /** 安全库存 */
    private BigDecimal safetyStock = BigDecimal.ZERO;
    /** 推演期内总毛需求 */
    private BigDecimal totalDemand = BigDecimal.ZERO;
    /** 在途总量 */
    private BigDecimal onOrderQty = BigDecimal.ZERO;
    /** 最早缺料日(余额首次 < 0 的日期; 无缺口为 null) */
    private LocalDate earliestShortageDate;
    /** 最大缺口(最深负余额的绝对值) */
    private BigDecimal maxGap = BigDecimal.ZERO;
    /** 首次达到最大缺口的日期；无缺口保持未知。 */
    private LocalDate maxGapDate;
    /** 存在缺口的需求日期（不包含仅到货的日期，按时间升序） */
    private List<LocalDate> shortageDemandDates = List.of();
    /** 缺口天数(余额 < 0 的天数) */
    private long gapDays;
    /** 建议动作: 催货/提前采购/调安全库存/无 */
    private String suggestion;
    /** 建议动作说明 */
    private String suggestionText;

    public void setShortageDemandDates(List<LocalDate> shortageDemandDates) {
        this.shortageDemandDates = List.copyOf(shortageDemandDates);
    }

    /**
     * 是否存在缺料风险
     */
    public boolean hasRisk() {
        return earliestShortageDate != null;
    }

    /**
     * 距最早缺料日的天数(负数表示已缺料)
     */
    public long daysUntilShortage(LocalDate today) {
        return earliestShortageDate == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, earliestShortageDate);
    }
}
