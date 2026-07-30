package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 记账凭证明细行 gl_voucher_line
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_voucher_line")
public class GlVoucherLine extends TenantEntity {

    /** 明细行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 凭证ID */
    private Long voucherId;

    /** 行号 */
    private Integer lineNo;

    /** 科目ID(须末级) */
    private Long accountId;

    /** 科目编码(冗余) */
    private String accountCode;

    /** 科目名称(冗余) */
    private String accountName;

    /** 摘要 */
    private String summary;

    /** 借方金额(本币) */
    private BigDecimal debit;

    /** 贷方金额(本币) */
    private BigDecimal credit;

    /** 原币 */
    private String currency;

    /** 原币金额 */
    private BigDecimal amountCurrency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 往来对象ID(客户/供应商,统驭科目用) */
    private Long partnerId;

    /** CLIENT-客户 VENDOR-供应商 */
    private String partnerType;

    /** 是否未清项 0-否 1-是 */
    private String openItem;

    /** 是否已清账 0-否 1-是 */
    private String cleared;

    /** 清账核销记录ID(F1 fin_settlement.settlement_id) */
    private Long clearedBy;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
