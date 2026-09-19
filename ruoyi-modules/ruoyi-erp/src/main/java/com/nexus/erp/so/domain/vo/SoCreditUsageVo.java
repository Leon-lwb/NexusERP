package com.nexus.erp.so.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoCreditUsage;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 信用占用记录视图对象
 *
 * @author nexus
 */
@Data
@AutoMapper(target = SoCreditUsage.class)
public class SoCreditUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 信用占用ID */
    private Long usageId;

    /** 客户ID */
    private Long clientId;

    /** 销售订单ID */
    private Long soId;

    /** 占用金额 */
    private BigDecimal amount;

    /** 占用状态: FROZEN/RELEASED */
    private String status;

    /** 冻结时间 */
    private Date freezeTime;

    /** 释放时间 */
    private Date releaseTime;
}
