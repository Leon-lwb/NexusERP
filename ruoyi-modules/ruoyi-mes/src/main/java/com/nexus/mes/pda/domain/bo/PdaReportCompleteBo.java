package com.nexus.mes.pda.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 报工完成入参 (合格数必填, 不良/报废可空)
 */
@Data
public class PdaReportCompleteBo {

    /** 合格数量 */
    @NotNull(message = "合格数量不能为空")
    @DecimalMin(value = "0", message = "合格数量不能为负")
    private BigDecimal goodQty;

    /** 不良数量 */
    @DecimalMin(value = "0", message = "不良数量不能为负")
    private BigDecimal defectQty;

    /** 报废数量 */
    @DecimalMin(value = "0", message = "报废数量不能为负")
    private BigDecimal scrapQty;
}
