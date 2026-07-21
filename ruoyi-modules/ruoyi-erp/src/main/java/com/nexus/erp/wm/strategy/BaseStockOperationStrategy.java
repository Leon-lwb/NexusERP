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

    /** 库存流水数据访问映射器（Mapper） */
    protected final WmMaterialStockTxMapper stockTxMapper;

    protected BaseStockOperationStrategy(WmMaterialStockTxMapper stockTxMapper) {
        this.stockTxMapper = stockTxMapper;
    }

    /**
     * 检查指定来源单据是否已过账
     */
    protected boolean hasPosted(String sourceDocType, Long sourceDocId) {
        Long count = stockTxMapper.selectCount(Wrappers.lambdaQuery(WmMaterialStockTx.class)
            .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
            .eq(WmMaterialStockTx::getSourceDocId, sourceDocId));
        return count != null && count > 0;
    }

    /**
     * 构建库存流水基础信息
     */
    protected WmMaterialStockTx buildBaseTxFromStock(WmMaterialStock stock, String txType, Integer txFlag,
                                                      BigDecimal txQuantity, BigDecimal beforeQuantity,
                                                      BigDecimal afterQuantity, Date now) {
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
