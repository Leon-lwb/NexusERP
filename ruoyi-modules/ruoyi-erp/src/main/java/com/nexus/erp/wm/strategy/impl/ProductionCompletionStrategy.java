package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 完工入库策略
 * 工单完工时，将成品/半成品入库到指定仓库/库位
 *
 * @author nexus
 */
@Slf4j
@Component
public class ProductionCompletionStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_PRODUCTION_IN = "PRODUCTION_IN";
    private static final String SOURCE_DOC_TYPE_WORK_ORDER = "PRD_WORK_ORDER";
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;

    public ProductionCompletionStrategy(WmMaterialStockTxMapper stockTxMapper,
                                        WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.stockTxMapper = stockTxMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.PRODUCTION_IN;
    }

    @Override
    public void execute(Object... args) {
        Long woId = (Long) args[0];
        Long materialId = (Long) args[1];
        String materialCode = (String) args[2];
        String materialName = (String) args[3];
        BigDecimal quantity = (BigDecimal) args[4];
        Long warehouseId = (Long) args[5];
        Long locationId = args[6] != null ? (Long) args[6] : null;
        Date now = (Date) args[7];

        // 1. 查询或创建库存记录
        WmMaterialStock stock = findOrCreateStock(materialId, warehouseId, locationId, materialCode, materialName);

        // 2. 增加库存数量
        BigDecimal beforeQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal afterQty = beforeQty.add(quantity);
        stock.setStockQuantity(afterQty);
        stockMapper.updateById(stock);

        // 3. 生成库存流水
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_PRODUCTION_IN, TX_FLAG_IN,
            quantity, beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_WORK_ORDER);
        tx.setSourceDocId(woId);
        tx.setRemark("工单完工入库");
        stockTxMapper.insert(tx);

        log.info("工单[{}]完工入库，物料[{}]，数量[{}]", woId, materialCode, quantity.stripTrailingZeros().toPlainString());
    }

    private WmMaterialStock findOrCreateStock(Long materialId, Long warehouseId, Long locationId,
                                               String materialCode, String materialName) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(materialId);
            stock.setMaterialCode(materialCode);
            stock.setMaterialName(materialName);
            stock.setWarehouseId(warehouseId);
            stock.setLocationId(locationId);
            stock.setStockQuantity(BigDecimal.ZERO);
            //stock.setIsQualityInspected("N");
            stockMapper.insert(stock);
        }
        return stock;
    }
}
