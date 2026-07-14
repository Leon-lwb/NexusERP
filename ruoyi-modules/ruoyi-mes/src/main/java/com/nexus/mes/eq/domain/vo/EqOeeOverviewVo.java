package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OEE 看板概览 KPI 视图对象
 * <p>
 * 聚合时间范围内的: 平均稼动率、平均 OEE、总停机时长、总故障次数、覆盖设备数。
 *
 * @author Nexus
 */
@Data
public class EqOeeOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平均稼动率(0~1) */
    private BigDecimal avgAvailability;

    /** 平均 OEE(0~1) */
    private BigDecimal avgOee;

    /** 总停机时长(分钟) */
    private BigDecimal totalDowntimeMinutes;

    /** 总故障次数 */
    private Integer totalFaultCount;

    /** 覆盖设备数 */
    private Integer equipmentCount;

    /** 总计划生产时长(分钟) */
    private BigDecimal totalPlannedMinutes;

    /** 总运行时长(分钟) */
    private BigDecimal totalRunMinutes;
}
