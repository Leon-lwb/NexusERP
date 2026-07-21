package com.nexus.erp.wm.service;

import java.math.BigDecimal;

/**
 * 库存预留服务
 * 支持销售订单确认/工单下达时锁定库存, 出库时消费预留
 *
 * @author nexus
 */
public interface IWmStockReservationService {

    /**
     * 预留库存 (按物料+仓库, FIFO 多批次)
     * @param materialId 物料ID
     * @param warehouseId 仓库ID (可为null, 表示不限仓库)
     * @param reserveQty 预留数量
     * @param sourceDocType 来源单据类型 (SALES_ORDER / WORK_ORDER)
     * @param sourceDocId 来源单据ID
     * @param sourceDocLineId 来源单据行ID
     * @return 预留成功数量 (不足时返回实际预留量)
     */
    BigDecimal reserve(Long materialId, Long warehouseId, BigDecimal reserveQty,
                       /** 库存预留（库存预留相关字段） */
                       String sourceDocType, Long sourceDocId, Long sourceDocLineId);

    /**
     * 释放预留 (按来源单据行)
     * @param sourceDocType 来源单据类型
     * @param sourceDocId 来源单据ID
     * @param sourceDocLineId 来源单据行ID
     * @return 释放数量
     */
    BigDecimal unreserve(String sourceDocType, Long sourceDocId, Long sourceDocLineId);

    /**
     * 检查可用库存 (stock_quantity - reserved_quantity)
     * @param materialId 物料ID
     * @param warehouseId 仓库ID (可为null)
     * @return 可用数量
     */
    BigDecimal checkAvailable(Long materialId, Long warehouseId);
}
