package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 调拨策略（来源库存扣减 + 目标库存增加）
 */
@Slf4j
@Component
public class TransferStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    private static final String TX_TYPE_TRANSFER_IN = "TRANSFER_IN";
    private static final String SOURCE_DOC_TYPE_TRANSFER = "WM_TRANSFER";
    private static final int TX_FLAG_OUT = -1;
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;

    public TransferStrategy(WmMaterialStockTxMapper stockTxMapper,
                            WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.TRANSFER;
    }

    @Override
    public void execute(Object... args) {
        WmTransferLine line = (WmTransferLine) args[0];
        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("调拨数量必须大于0");
        }

        Date now = new Date();

        // 1. 来源库存扣减
        LambdaQueryWrapper<WmMaterialStock> fromLqw = Wrappers.lambdaQuery();
        fromLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        fromLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        fromLqw.eq(line.getFromLocationId() != null, WmMaterialStock::getLocationId, line.getFromLocationId());
        fromLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        fromLqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> fromStocks = stockMapper.selectList(fromLqw);
        if (fromStocks == null || fromStocks.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不存在");
        }

        BigDecimal remaining = line.getQuantity();
        for (WmMaterialStock fromStock : fromStocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = fromStock.getStockQuantity() == null ? BigDecimal.ZERO : fromStock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = stockQty.min(remaining);
            BigDecimal beforeQty = stockQty;
            BigDecimal afterQty = stockQty.subtract(deductQty);
            fromStock.setStockQuantity(afterQty);
            stockMapper.updateById(fromStock);
            stockTxMapper.insert(buildTransferOutTx(line, fromStock, deductQty, beforeQty, afterQty, now));
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不足");
        }

        // 2. 目标库存增加
        LambdaQueryWrapper<WmMaterialStock> toLqw = Wrappers.lambdaQuery();
        toLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        toLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        toLqw.eq(line.getToLocationId() != null, WmMaterialStock::getLocationId, line.getToLocationId());
        toLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> toStocks = stockMapper.selectList(toLqw);

        WmMaterialStock toStock;
        if (toStocks == null || toStocks.isEmpty()) {
            WmMaterialStock fromRef = fromStocks.get(0);
            toStock = new WmMaterialStock();
            toStock.setMaterialId(fromRef.getMaterialId());
            toStock.setMaterialCode(fromRef.getMaterialCode());
            toStock.setMaterialName(fromRef.getMaterialName());
            toStock.setMaterialSpec(fromRef.getMaterialSpec());
            toStock.setBaseUomId(fromRef.getBaseUomId());
            toStock.setBaseUomName(fromRef.getBaseUomName());
            toStock.setBatchId(line.getBatchId());
            toStock.setBatchCode(line.getBatchCode());
            toStock.setWarehouseId(fromRef.getWarehouseId());
            toStock.setWarehouseCode(fromRef.getWarehouseCode());
            toStock.setWarehouseName(fromRef.getWarehouseName());
            toStock.setAreaId(fromRef.getAreaId());
            toStock.setAreaCode(fromRef.getAreaCode());
            toStock.setAreaName(fromRef.getAreaName());
            toStock.setLocationId(line.getToLocationId());
            toStock.setLocationCode(line.getToLocationCode());
            toStock.setLocationName(line.getToLocationName());
            toStock.setStockQuantity(line.getQuantity());
            toStock.setRecptDate(now);
            toStock.setFrozenFlag(0L);
            stockMapper.insert(toStock);
        } else {
            toStock = toStocks.get(0);
            toStock.setStockQuantity(toStock.getStockQuantity().add(line.getQuantity()));
            stockMapper.updateById(toStock);
        }

        stockTxMapper.insert(buildTransferInTx(line, toStock, line.getQuantity(), now));
    }

    private WmMaterialStockTx buildTransferOutTx(WmTransferLine line, WmMaterialStock stock,
                                                 BigDecimal txQuantity, BigDecimal beforeQuantity,
                                                 BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_TRANSFER_OUT, TX_FLAG_OUT, txQuantity,
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_TRANSFER);
        tx.setSourceDocId(line.getTransferId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("调拨出库，目标库位：" + line.getToLocationName());
        return tx;
    }

    private WmMaterialStockTx buildTransferInTx(WmTransferLine line, WmMaterialStock stock,
                                                 BigDecimal txQuantity, Date now) {
        BigDecimal beforeQty = stock.getStockQuantity().subtract(txQuantity);
        BigDecimal afterQty = stock.getStockQuantity();
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_TRANSFER_IN, TX_FLAG_IN, txQuantity,
            beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_TRANSFER);
        tx.setSourceDocId(line.getTransferId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("调拨入库，来源库位：" + line.getFromLocationName());
        return tx;
    }
}
