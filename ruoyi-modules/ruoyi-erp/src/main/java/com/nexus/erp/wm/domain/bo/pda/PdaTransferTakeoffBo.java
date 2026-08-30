package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 移库行级下架核销入参
 * <p>
 * 下架为整行语义: qty 可空(默认整行数量), 提供时必须等于行数量(部分下架不支持, 避免与过账数量不一致)。
 */
@Data
public class PdaTransferTakeoffBo {

    /** 移库单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 源库位编码(须与行源库位一致, 防错扫) */
    @NotBlank(message = "库位编码不能为空")
    private String locationCode;

    /** 下架数量(可空, 默认整行) */
    private BigDecimal qty;
}
