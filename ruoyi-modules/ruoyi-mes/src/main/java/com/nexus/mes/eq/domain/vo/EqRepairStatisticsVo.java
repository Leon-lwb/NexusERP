package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES故障统计分析视图对象
 * <p>
 * 汇总: KPI(总故障/已完成/维修中/总停机/总成本/MTTR/MTBF近似) + 趋势 + Pareto + 设备TOP。
 * 数据源: eq_repair(维修记录)。
 *
 * @author Nexus
 */
@Data
public class EqRepairStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总故障次数 */
    private Integer totalFault;

    /** 已完成数 */
    private Integer completedCount;

    /** 维修中数(REPORTED+REPAIRING) */
    private Integer repairingCount;

    /** 总停机时长(小时) */
    private BigDecimal totalDowntimeHours;

    /** 总维修成本 */
    private BigDecimal totalRepairCost;

    /** MTTR 平均维修时长(小时, = avg(repair_end - repair_start)) */
    private BigDecimal mttrHours;

    /** MTBF 近似平均故障间隔(小时, = 区间时长 / 故障次数) */
    private BigDecimal mtbfHours;

    /** 故障趋势(按日) */
    private List<TrendItem> trend;

    /** 故障类型 Pareto */
    private List<ParetoItem> pareto;

    /** 设备故障 TOP10 */
    private List<TopItem> topEquipment;

    /** 趋势项 */
    @Data
    public static class TrendItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Date statDate;
        private Integer faultCount;
        private BigDecimal downtimeHours;
    }

    /** Pareto 项 */
    @Data
    public static class ParetoItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String faultType;
        private Integer count;
        private BigDecimal ratio;
    }

    /** 设备 TOP 项 */
    @Data
    public static class TopItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long equipmentId;
        private String equipmentCode;
        private String equipmentName;
        private Integer faultCount;
        private BigDecimal downtimeHours;
    }
}
