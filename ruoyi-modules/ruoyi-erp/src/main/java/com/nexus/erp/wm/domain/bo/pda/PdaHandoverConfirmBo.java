package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 上料交接行级核销入参 (累计语义, 不超过计划数量)
 */
@Data
public class PdaHandoverConfirmBo {

    /** 交接单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 本次核销数量 */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.000001", message = "数量必须大于0")
    private BigDecimal qty;

    /** 批次号(可选, 覆盖行批次用于追溯) */
    private String batchCode;
}
