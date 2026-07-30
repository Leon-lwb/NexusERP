package com.nexus.erp.fin.gl.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 科目余额视图对象 gl_account_balance
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GlAccountBalance.class)
public class GlAccountBalanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 余额ID */
    private Long balanceId;

    /** 期间ID */
    private Long periodId;

    /** 科目ID */
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

    /** 乐观锁版本号 */
    private Integer version;
}
