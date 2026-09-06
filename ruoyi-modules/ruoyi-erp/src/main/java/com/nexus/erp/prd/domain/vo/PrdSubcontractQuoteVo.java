package com.nexus.erp.prd.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontractQuote;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单报价记录视图对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdSubcontractQuote.class)
public class PrdSubcontractQuoteVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报价ID */
    private Long quoteId;
    /** 委外订单ID */
    private Long subcontractId;
    /** 供应商ID */
    private Long vendorId;
    /** 供应商编码 */
    private String vendorCode;
    /** 供应商名称 */
    private String vendorName;
    /** 加工单价 */
    private BigDecimal unitPrice;
    /** 币种 */
    private String currency;
    /** 交期(天) */
    private Integer leadTimeDays;
    /** 治具/钢网费 */
    private BigDecimal toolingFee;
    /** 状态: PENDING/SELECTED/NOT_SELECTED/EXPIRED */
    private String quoteStatus;
    /** 报价有效期 */
    private Date validUntil;
    /** 报价时间 */
    private Date quoteTime;
    /** 创建部门 */
    private Long createDept;
    /** 创建者 */
    private Long createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新者 */
    private Long updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 备注 */
    private String remark;
}
