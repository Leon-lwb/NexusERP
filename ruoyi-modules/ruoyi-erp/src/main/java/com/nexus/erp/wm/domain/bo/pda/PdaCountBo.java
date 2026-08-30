package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 盘点实盘录入入参 (覆盖语义, 可重复扫码改数)
 */
@Data
public class PdaCountBo {

    /** 盘点单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 实盘数量 */
    @NotNull(message = "实盘数量不能为空")
    @DecimalMin(value = "0", message = "实盘数量不能为负")
    private BigDecimal countedQty;
}
