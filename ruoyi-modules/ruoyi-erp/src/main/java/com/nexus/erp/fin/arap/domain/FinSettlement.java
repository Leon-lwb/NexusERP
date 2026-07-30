package com.nexus.erp.fin.arap.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 应收应付核销记录 fin_settlement(发票 <-> 收付款单)
 * <p>gl_voucher_line.cleared_by 反向指向本表 settlement_id(全额核销时清账)。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("fin_settlement")
public class FinSettlement extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 核销记录ID */
    @TableId(value = "settlement_id")
    private Long settlementId;

    /** 核销单号 */
    private String settlementNo;

    /** 方向 AR/AP */
    private String direction;

    /** 发票ID */
    private Long invoiceId;

    /** 发票号(冗余) */
    private String invoiceNo;

    /** 发票类型(冗余) */
    private String invoiceType;

    /** 收付款单ID */
    private Long receiptId;

    /** 核销金额 */
    private BigDecimal settleAmount;

    /** 核销日期 */
    private Date settleDate;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
