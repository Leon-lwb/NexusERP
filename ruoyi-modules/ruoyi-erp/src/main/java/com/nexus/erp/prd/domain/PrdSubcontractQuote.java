package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单报价记录对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("prd_subcontract_quote")
public class PrdSubcontractQuote extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 报价ID */
    Long quoteId;
    /** 委外订单ID */
    Long subcontractId;
    /** 供应商ID */
    Long vendorId;
    /** 供应商编码 */
    String vendorCode;
    /** 供应商名称 */
    String vendorName;
    /** 加工单价 */
    BigDecimal unitPrice;
    /** 币种 */
    String currency;
    /** 交期(天) */
    Integer leadTimeDays;
    /** 治具/钢网费 */
    BigDecimal toolingFee;
    /** 状态: PENDING/SELECTED/NOT_SELECTED/EXPIRED */
    String quoteStatus;
    /** 报价有效期 */
    Date validUntil;
    /** 报价时间 */
    Date quoteTime;
    /** 创建部门 */
    Long createDept;
    /** 创建者 */
    Long createBy;
    /** 创建时间 */
    Date createTime;
    /** 更新者 */
    Long updateBy;
    /** 更新时间 */
    Date updateTime;
    /** 备注 */
    String remark;
}
