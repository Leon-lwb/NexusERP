package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 杂项入库策略
 */
@Slf4j
@Component
public class MiscRecptStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_MISC_IN = "MISC_IN";
    private static final String SOURCE_DOC_TYPE_MISC_RECPT = "WM_MISC_RECPT";
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;

    public MiscRecptStrategy(WmMaterialStockTxMapper stockTxMapper,
                             WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.MISC_RECPT;
    }

    @Override
    public void execute(Object... args) {
        WmMiscRecptLine line = (WmMiscRecptLine) args[0];
        WmMiscRecpt recpt = (WmMiscRecpt) args[1];
        Date now = (Date) args[2];

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项入库数量必须大于0");
        }

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(recpt.getWarehouseId() != null, WmMaterialStock::getWarehouseId, recpt.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
            ? BigDecimal.ZERO : stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.add(line.getQuantity());

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(line.getMaterialId());
            stock.setMaterialCode(line.getMaterialCode());
            stock.setMaterialName(line.getMaterialName());
            stock.setMaterialSpec(line.getMaterialSpec());
            stock.setBaseUomId(line.getBaseUomId());
            stock.setBaseUomName(line.getBaseUomName());
            stock.setBatchId(line.getBatchId());
            stock.setBatchCode(line.getBatchCode());
            stock.setWarehouseId(recpt.getWarehouseId());
            stock.setWarehouseCode(recpt.getWarehouseCode());
            stock.setWarehouseName(recpt.getWarehouseName());
            stock.setAreaId(recpt.getAreaId());
            stock.setAreaCode(recpt.getAreaCode());
            stock.setAreaName(recpt.getAreaName());
            stock.setLocationId(line.getLocationId());
            stock.setLocationCode(line.getLocationCode());
            stock.setLocationName(line.getLocationName());
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        } else {
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stockMapper.updateById(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_MISC_IN, TX_FLAG_IN, line.getQuantity(),
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_MISC_RECPT);
        tx.setSourceDocId(line.getRecptId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("杂项入库");
        stockTxMapper.insert(tx);
    }
}
