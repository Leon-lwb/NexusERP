package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmMaterialStock;

import java.math.BigDecimal;
import java.util.List;
/**
 * 拣货策略服务接口
 * <p>
 * 定义拣货策略的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IPickingStrategyService {

    /**
     * 查询拣货策略
     *
     * @param materialId 物料 ID
     * @param warehouseId 仓库 ID
     * @param quantity 数量
     * @return 结果列表
     */
    List<WmMaterialStock> selectStocks(Long materialId, Long warehouseId, BigDecimal quantity);
}
