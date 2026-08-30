package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 发料行级拣货核销入参
 */
@Data
public class PdaPickBo {

    /** 发料单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 拣货库位编码(须与行库位一致) */
    @NotBlank(message = "库位编码不能为空")
    private String locationCode;

    /** 批次号(可选) */
    private String batchCode;

    /** 本次拣货数量 */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.000001", message = "数量必须大于0")
    private BigDecimal qty;
}
