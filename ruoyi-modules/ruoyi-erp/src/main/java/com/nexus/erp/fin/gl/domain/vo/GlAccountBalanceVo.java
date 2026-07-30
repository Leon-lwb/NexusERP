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

    private Long balanceId;

    private Long periodId;

    private Long accountId;

    private BigDecimal openingDebit;

    private BigDecimal openingCredit;

    private BigDecimal periodDebit;

    private BigDecimal periodCredit;

    private BigDecimal closingDebit;

    private BigDecimal closingCredit;

    private BigDecimal yearOpeningDebit;

    private BigDecimal yearOpeningCredit;

    private Integer version;
}
