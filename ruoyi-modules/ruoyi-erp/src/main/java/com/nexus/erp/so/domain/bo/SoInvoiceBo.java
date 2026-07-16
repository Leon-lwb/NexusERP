package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售发票业务对象 so_invoice
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoInvoice.class, reverseConvertGenerate = false)
public class SoInvoiceBo extends BaseEntity {

    /**
     * 发票ID
     */
    private Long invoiceId;

    /**
     * 发票号
     */
    @NotBlank(message = "发票号不能为空")
    private String invoiceNo;

    /**
     * 发票代码
     */
    private String invoiceCode;

    /**
     * 发票类型
     */
    private String invoiceType;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户名称
     */
    private String clientName;

    /** 购买方纳税人识别号 */
    private String buyerTaxId;
    /** 购买方发票抬头 */
    private String buyerTitle;
    /** 购买方开户行 */
    private String buyerBankName;
    /** 购买方银行账号 */
    private String buyerBankAccount;
    /** 购买方地址、电话 */
    private String buyerAddressPhone;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 开票日期
     */
    private Date invoiceDate;

    /**
     * 币种
     */
    private String currency;

    /**
     * 汇率
     */
    private BigDecimal exchangeRate;

    /**
     * 金额（不含税）
     */
    private BigDecimal amount;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 价税合计
     */
    private BigDecimal totalAmount;

    /**
     * 已核销金额
     */
    private BigDecimal verifiedAmount;

    /**
     * 发票状态
     */
    private String status;

    /** 开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台 */
    private String issueChannel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 扩展字段1
     */
    private String attr1;

    /**
     * 扩展字段2
     */
    private String attr2;

    /**
     * 扩展字段3
     */
    private Long attr3;

    /**
     * 扩展字段4
     */
    private Long attr4;

    /**
     * 发票明细行列表
     */
    private List<SoInvoiceLine> soInvoiceLineList;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
