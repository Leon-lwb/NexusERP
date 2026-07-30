package com.nexus.erp.fin.arap.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 收付款单 fin_receipt(AR收款/AP付款合一,direction 区分)
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receipt")
public class FinReceipt extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 收付款单ID */
    @TableId(value = "receipt_id")
    private Long receiptId;

    /** 收付款单号 */
    private String receiptNo;

    /** 收付款类型 CASH/BANK/ALIPAY/WECHAT/OTHER */
    private String receiptType;

    /** 方向 AR=应收收款 AP=应付付款 */
    private String direction;

    /** 客户ID(direction=AR) */
    private Long clientId;

    /** 供应商ID(direction=AP) */
    private Long vendorId;

    /** 付款方名称 */
    private String payerName;

    /** 收付款金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 第三方流水号 */
    private String thirdPartyNo;

    /** 支付订单号(跨库关联 pay_order,不JOIN) */
    private String payOrderNumber;

    /** 来源 MANUAL/PAY_CALLBACK */
    private String source;

    /** 状态 DRAFT/CONFIRMED/SETTLED/CANCELLED */
    private String status;

    /** 收付款日期(凭证日期) */
    private Date receiptDate;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
