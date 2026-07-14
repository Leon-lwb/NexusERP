package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * OEE 趋势视图对象(按日/周/月)
 *
 * @author Nexus
 */
@Data
public class EqOeeTrendVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期(粒度起点) */
    private Date statDate;

    /** 稼动率(0~1) */
    private BigDecimal availability;

    /** OEE(0~1) */
    private BigDecimal oee;

    /** 停机时长(分钟) */
    private BigDecimal downtimeMinutes;

    /** 故障次数 */
    private Integer faultCount;
}
