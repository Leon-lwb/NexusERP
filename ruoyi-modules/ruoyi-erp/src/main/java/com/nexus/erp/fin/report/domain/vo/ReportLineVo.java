package com.nexus.erp.fin.report.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报表行视图对象(三大报表统一行结构)
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
public class ReportLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报表类型 BALANCE/INCOME/CASH */
    private String reportType;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 父项目ID(0=顶级) */
    private Long parentItemId;

    /** 是否合计行 0-否 1-是 */
    private Boolean isSummary;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 本期/期末金额(符号已按 sign 处理) */
    private BigDecimal amount;

    /** 本年累计(利润表/现金流量表;资产负债表为 null) */
    private BigDecimal yearAmount;
}
