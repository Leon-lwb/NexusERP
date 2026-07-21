package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OEE 时长分布视图对象(运行/停机/保养占比)
 *
 * <p>属于MES 设备管理模块，OEE分布视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
public class EqOeeDistributionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行时长(分钟) */
    private BigDecimal runMinutes;

    /** 停机时长(分钟) */
    private BigDecimal downtimeMinutes;

    /** 保养占用时长(分钟) */
    private BigDecimal maintenanceMinutes;

    /** 运行占比(0~1) */
    private BigDecimal runRatio;

    /** 停机占比(0~1) */
    private BigDecimal downtimeRatio;

    /** 保养占比(0~1) */
    private BigDecimal maintenanceRatio;
}
