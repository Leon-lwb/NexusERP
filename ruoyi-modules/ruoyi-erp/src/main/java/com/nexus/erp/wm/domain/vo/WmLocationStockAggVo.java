package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库位库存聚合视图对象
 * <p>
 * 按 {@code location_id} 聚合 {@code wm_material_stock} 的中间结果，供 Service 计算库位状态。
 *
 * @author nexus
 * @date 2026-06-30
 */
@Data
public class WmLocationStockAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库存记录数
     */
    private Integer stockCount;

    /**
     * 在库数量合计
     */
    private BigDecimal stockQuantity;

    /**
     * 已预留数量合计
     */
    private BigDecimal reservedQuantity;

    /**
     * 是否含冻结库存(MAX(frozen_flag))
     */
    private Long frozenFlag;
}
