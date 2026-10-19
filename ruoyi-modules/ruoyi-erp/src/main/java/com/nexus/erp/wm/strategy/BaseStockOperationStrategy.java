package com.nexus.erp.wm.strategy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存操作策略抽象基类，提供公共辅助方法
 */
public abstract class BaseStockOperationStrategy implements StockOperationStrategy {

    /**
     * 效期守卫 SQL（ERP-WM-06）：排除已过期批次（无有效期视为不失效）。
     * <p>修复前全仓对 {@code expire_date} 只有排序、没有与当前时间比较，
     * FEFO 反而把「最过期」批次排在最前，过期物料被优先领用/发给客户。
     * 该片段同时用于拣货策略与各出库策略的选行条件。MySQL 方言（CURDATE()）。</p>
     */
    protected static final String NOT_EXPIRED_SQL =
            "(expire_date IS NULL OR expire_date >= CURDATE())";

    /**
     * 库存流水数据访问映射器（Mapper）
     */
    protected final WmMaterialStockTxMapper stockTxMapper;

    protected BaseStockOperationStrategy(WmMaterialStockTxMapper stockTxMapper) {
        this.stockTxMapper = stockTxMapper;
    }

    /**
     * 计算库存行可用量 = 在库数量 - 已预留数量（ERP-WM-02）。
     * <p>出库/领料/转储的扣减上限必须按可用量计算；若按 {@code stock_quantity} 计算，
     * 会把其他单据已预留的库存扣走，使销售预留承诺落空、可用量出现负数。</p>
     *
     * @param stock 库存行
     * @return 可用量（不小于 0 时不裁剪，由调用方按需判断）
     */
    protected BigDecimal availableOf(WmMaterialStock stock) {
        BigDecimal qty =
                stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
        BigDecimal reserved =
                stock.getReservedQuantity() == null ? BigDecimal.ZERO : stock.getReservedQuantity();
        return qty.subtract(reserved);
    }

    /**
     * 检查指定来源单据是否已过账
     */
    protected boolean hasPosted(String sourceDocType, Long sourceDocId) {
        Long count =
                stockTxMapper.selectCount(
                        Wrappers.lambdaQuery(WmMaterialStockTx.class)
                                .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
                                .eq(WmMaterialStockTx::getSourceDocId, sourceDocId));
        return count != null && count > 0;
    }

    /**
     * 构建库存流水基础信息
     */
    protected WmMaterialStockTx buildBaseTxFromStock(
            WmMaterialStock stock,
            String txType,
            Integer txFlag,
            BigDecimal txQuantity,
            BigDecimal beforeQuantity,
            BigDecimal afterQuantity,
            Date now) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType(txType);
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setSnId(stock.getSnId());
        tx.setSnCode(stock.getSnCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setAreaId(stock.getAreaId());
        tx.setAreaCode(stock.getAreaCode());
        tx.setAreaName(stock.getAreaName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxFlag(txFlag);
        tx.setTxQuantity(txQuantity);
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        return tx;
    }
}
