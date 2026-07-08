package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 拣货解析结果
 * <p>
 * 规则驱动下确定的领料目标仓库 + 按策略选中的库存分配明细。由拣货编排层返回，供倒冲领料扣减。
 *
 * @author nexus
 */
@Data
public class PickingAllocation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标仓库ID */
    private Long warehouseId;

    /** 目标仓库编码 */
    private String warehouseCode;

    /** 目标仓库名称 */
    private String warehouseName;

    /** 选中的库存分配明细 */
    private List<AllocatedStock> stocks;

    /** 本次分配总量 */
    private BigDecimal totalAllocatedQty;
}
