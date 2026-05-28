package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 盘点调整策略（盘盈/盘亏调整库存）
 */
@Slf4j
@Component
public class CountAdjustStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_COUNT_ADJUST = "COUNT_ADJUST";
    private static final String SOURCE_DOC_TYPE_STOCK_TAKING = "WM_STOCK_TAKING";
    private static final int TX_FLAG_IN = 1;
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;

    public CountAdjustStrategy(WmMaterialStockTxMapper stockTxMapper,
                               WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.COUNT_ADJUST;
    }

    @Override
    public void execute(Object... args) {
        WmStockTakingLine line = (WmStockTakingLine) args[0];
        BigDecimal actualQuantity = (BigDecimal) args[1];
        BigDecimal bookQuantity = (BigDecimal) args[2];
        BigDecimal diffQuantity = (BigDecimal) args[3];
        Date now = (Date) args[4];

        int txFlag = diffQuantity.compareTo(BigDecimal.ZERO) > 0 ? TX_FLAG_IN : TX_FLAG_OUT;
        BigDecimal absDiff = diffQuantity.abs();

        WmMaterialStock stock;
        if (line.getStockId() != null) {
            stock = stockMapper.selectById(line.getStockId());
        } else {
            LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
            lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
            stock = stockMapper.selectOne(lqw);
        }

        if (stock == null) {
            throw new RuntimeException("盘点物料【" + line.getMaterialName() + "】找不到对应的库存记录");
        }

        BigDecimal beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
        BigDecimal afterQty = bookQuantity.add(diffQuantity);

        stock.setStockQuantity(afterQty);
        stockMapper.updateById(stock);

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_COUNT_ADJUST, txFlag, absDiff,
            beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_STOCK_TAKING);
        tx.setSourceDocId(line.getPlanId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("盘点调整，账面:" + bookQuantity + " 实际:" + actualQuantity + " 差异:" + diffQuantity);
        stockTxMapper.insert(tx);
    }
}
