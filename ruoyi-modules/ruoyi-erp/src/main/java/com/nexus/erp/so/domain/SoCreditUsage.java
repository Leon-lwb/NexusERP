package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;
import com.nexus.erp.so.domain.vo.SoCreditUsageVo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 信用占用记录对象 so_credit_usage
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_credit_usage")
@AutoMapper(target = SoCreditUsageVo.class)
public class SoCreditUsage extends TenantEntity {

    /** 信用占用ID */
    @TableId(value = "usage_id", type = IdType.ASSIGN_ID)
    private Long usageId;

    /** 客户ID */
    private Long clientId;

    /** 销售订单ID */
    private Long soId;

    /** 占用金额 */
    private BigDecimal amount;

    /** 占用状态: FROZEN(已冻结)/USED(已交付占用)/RELEASED(已释放) */
    private String status;

    /** 冻结时间 */
    private Date freezeTime;

    /** 转为已用(已交付)时间 */
    private Date usedTime;

    /** 释放时间 */
    private Date releaseTime;
}
