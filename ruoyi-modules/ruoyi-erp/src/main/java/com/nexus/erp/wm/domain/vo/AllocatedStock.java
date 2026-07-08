package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 拣货分配明细
 * <p>
 * 拣货策略选中的单条库存及其本次扣减量。由拣货编排层产出，供倒冲领料逐条扣减。
 *
 * @author nexus
 */
@Data
public class AllocatedStock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存记录ID */
    private Long stockId;

    /** 物料ID */
    private Long materialId;

    /** 仓库ID */
    private Long warehouseId;

    /** 库位ID */
    private Long locationId;

    /** 批次ID */
    private Long batchId;

    /** 批次编码 */
    private String batchCode;

    /** 本次扣减量 */
    private BigDecimal deductQty;
}
