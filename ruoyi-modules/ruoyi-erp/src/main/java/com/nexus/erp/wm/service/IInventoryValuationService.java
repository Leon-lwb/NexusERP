package com.nexus.erp.wm.service;

import java.math.BigDecimal;
/**
 * 库存计价服务接口
 * <p>
 * 定义库存计价的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IInventoryValuationService {

    /**
     * 计算库存计价
     *
     * @param stockId 库存 ID
     * @param inQty 入库数量
     * @param inUnitCost 入库单位成本
     * @return 结果
     */
    BigDecimal calculateMovingAverage(Long stockId, BigDecimal inQty, BigDecimal inUnitCost);

    /**
     * 修改库存计价
     *
     * @param stockId 库存 ID
     * @param inQty 入库数量
     * @param inUnitCost 入库单位成本
     */
    void updateStockCost(Long stockId, BigDecimal inQty, BigDecimal inUnitCost);

    /**
     * 出库成本结转:按当前移动平均单位成本扣减 totalCost,返回出库单位成本
     *
     * @param stockId 库存 ID
     * @param outQty  出库数量
     * @return 出库单位成本(移动平均成本,出库不改变单位成本)
     */
    BigDecimal issueStockCost(Long stockId, BigDecimal outQty);
}
