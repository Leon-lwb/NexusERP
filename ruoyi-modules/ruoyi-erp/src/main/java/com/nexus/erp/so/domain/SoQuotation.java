package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售报价对象 so_quotation
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_quotation")
public class SoQuotation extends TenantEntity {

    /** 报价ID */
    @TableId(value = "quotation_id")
    private Long quotationId;

    /** 报价单号 */
    private String quotationNo;
    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 报价日期 */
    private Date quotationDate;
    /** 有效期至 */
    private Date validUntil;
    /** 币种 */
    private String currency;
    /** 汇率 */
    private BigDecimal exchangeRate;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 折扣额 */
    private BigDecimal discountAmount;
    /** 折扣率 */
    private BigDecimal discountRate;
    /** 付款条款 */
    private String paymentTerms;
    /** 交货条款 */
    private String deliveryTerms;
    /** 状态 */
    private String status;
    /** 审批流程ID */
    private Long selectFlowId;
    /** 备注 */
    private String remark;
    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;

    /** 报价明细行列表（非数据库字段） */
    @TableField(exist = false)
    private List<SoQuotationLine> soQuotationLineList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
