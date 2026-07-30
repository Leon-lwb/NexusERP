package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 科目余额表 gl_account_balance(试算/报表基座)
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_account_balance")
public class GlAccountBalance extends TenantEntity {

    @TableId(value = "balance_id")
    private Long balanceId;

    /** 期间ID */
    private Long periodId;

    /** 科目ID(末级) */
    private Long accountId;

    /** 期初借方 */
    private BigDecimal openingDebit;

    /** 期初贷方 */
    private BigDecimal openingCredit;

    /** 本期借方发生 */
    private BigDecimal periodDebit;

    /** 本期贷方发生 */
    private BigDecimal periodCredit;

    /** 期末借方 */
    private BigDecimal closingDebit;

    /** 期末贷方 */
    private BigDecimal closingCredit;

    /** 年初借方 */
    private BigDecimal yearOpeningDebit;

    /** 年初贷方 */
    private BigDecimal yearOpeningCredit;

    @Version
    private Integer version;

    @TableLogic
    private String delFlag;
}
