package com.nexus.erp.fin.gl.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 试算平衡表行视图对象
 * <p>期初 ± 本期发生 = 期末;汇总行借贷合计须相等。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
public class TrialBalanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 科目ID */
    private Long accountId;

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 科目类型 */
    private String accountType;

    /** 余额方向 */
    private String balanceDir;

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
}
