package com.nexus.erp.prd.schedule;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 缺料推演输入(按物料聚合)
 */
@Data
public class PrdShortageInput {
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 当前库存(可用量, 已扣预留) */
    private BigDecimal stockQty = BigDecimal.ZERO;
    /** 安全库存 */
    private BigDecimal safetyStock = BigDecimal.ZERO;
    /** 毛需求: 需求日期 → 数量(MRP 明细按 requiredDate 聚合) */
    private final TreeMap<LocalDate, BigDecimal> demands = new TreeMap<>();
    /** 在途采购: 预计到货日 → 数量(在途 = 订购量 − 已收货量) */
    private final TreeMap<LocalDate, BigDecimal> arrivals = new TreeMap<>();

    /**
     * 按日期累加毛需求
     */
    public void addDemand(LocalDate date, BigDecimal qty) {
        if (date == null || qty == null || qty.signum() == 0) {
            return;
        }
        demands.merge(date, qty, BigDecimal::add);
    }

    /**
     * 按日期累加在途到货
     */
    public void addArrival(LocalDate date, BigDecimal qty) {
        if (date == null || qty == null || qty.signum() <= 0) {
            return;
        }
        arrivals.merge(date, qty, BigDecimal::add);
    }

    /**
     * 总在途量
     */
    public BigDecimal totalOnOrder() {
        return arrivals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 总毛需求
     */
    public BigDecimal totalDemand() {
        return demands.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 推演涉及的最早日期(需求或在途, 取更早者)
     */
    public LocalDate horizonStart() {
        List<LocalDate> dates = new ArrayList<>();
        if (!demands.isEmpty()) dates.add(demands.firstKey());
        if (!arrivals.isEmpty()) dates.add(arrivals.firstKey());
        return dates.stream().min(LocalDate::compareTo).orElse(null);
    }
}
