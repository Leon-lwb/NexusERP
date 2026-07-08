package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.vo.PickingAllocation;

import java.math.BigDecimal;

/**
 * 拣货规则解析编排层
 * <p>
 * 按物料类别全局匹配拣货规则，规则绑定的 warehouseId 作为目标仓库，
 * 再按规则策略类型(FIFO/FEFO)在指定仓库内选库存。供倒冲领料等"未知仓库"场景使用。
 *
 * @author nexus
 */
public interface IPickingResolutionService {

    /**
     * 规则驱动解析拣货目标仓库与选中库存
     *
     * @param materialId 物料ID
     * @param quantity   需求数量
     * @return 目标仓库 + 库存分配明细
     */
    PickingAllocation resolvePicking(Long materialId, BigDecimal quantity);
}
