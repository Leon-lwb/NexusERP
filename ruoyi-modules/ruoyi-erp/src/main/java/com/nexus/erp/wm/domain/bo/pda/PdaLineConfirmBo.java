package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 收货行级核销入参
 */
@Data
public class PdaLineConfirmBo {

    /** 收货单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 批次号(可选, 扫供应商批次标签时携带) */
    private String batchCode;

    /** 本次收货数量 */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.000001", message = "数量必须大于0")
    private BigDecimal qty;

    /** 上架库位编码(可选, 未提供时在上架阶段统一指定) */
    private String locationCode;
}
