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
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 调拨策略
 * <p>
 * 处理库存调拨业务，包括来源库存扣减和目标库存增加两个步骤。
 * 支持按先进先出（FIFO）规则匹配来源库存，并在目标库位创建或更新库存记录。
 *
 * @author nexus
 */
@Slf4j
@Component
public class TransferStrategy extends BaseStockOperationStrategy {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;


    /** 事务类型：调拨出库 */
    private static final String TX_TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    /** 事务类型：调拨入库 */
    private static final String TX_TYPE_TRANSFER_IN = "TRANSFER_IN";
    /** 来源单据类型：库存调拨单 */
    private static final String SOURCE_DOC_TYPE_TRANSFER = "WM_TRANSFER";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 构造调拨策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public TransferStrategy(WmMaterialStockTxMapper stockTxMapper,
                            WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 调拨操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.TRANSFER;
    }

    /**
     * 执行调拨操作
     *
     * @param args 操作参数，args[0]为调拨单行对象（WmTransferLine）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmTransferLine line = (WmTransferLine) args[0];
        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("调拨数量必须大于0");
        }

        Date now = new Date();
        if(serialInventory!=null && serialInventory.post(com.nexus.erp.wm.domain.WmSerialPostingRequest.out(
            "WM_TRANSFER",line.getTransferId(),line.getLineId(),"TRANSFER",line.getMaterialId(),line.getQuantity(),line.getFromWarehouseId(),line.getFromLocationId(),line.getBatchId())
            .transfer(line.getToWarehouseId(),line.getToAreaId(),line.getToLocationId())))return;

        // 1. 来源库存扣减（按来源仓库过滤，防止跨仓调拨从错误仓库扣减；排除已预留量）
        LambdaQueryWrapper<WmMaterialStock> fromLqw = Wrappers.lambdaQuery();
        fromLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        fromLqw.eq(line.getFromWarehouseId() != null, WmMaterialStock::getWarehouseId, line.getFromWarehouseId());
        fromLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        fromLqw.eq(line.getFromLocationId() != null, WmMaterialStock::getLocationId, line.getFromLocationId());
        fromLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        fromLqw.apply("(stock_quantity - reserved_quantity) > 0");
        // ERP-WM-06: 效期拦截 —— 已过期批次不得参与调拨
        fromLqw.apply(NOT_EXPIRED_SQL);
        fromLqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> fromStocks = stockMapper.selectList(fromLqw);
        if (fromStocks == null || fromStocks.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不存在");
        }

        BigDecimal remaining = line.getQuantity();
        for (WmMaterialStock fromStock : fromStocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            // ERP-WM-02: 扣减上限按可用量(在库-已预留)而非账面量, 否则调拨可扣穿销售预留
            BigDecimal available = availableOf(fromStock);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = available.min(remaining);
            BigDecimal beforeQty = fromStock.getStockQuantity();
            BigDecimal afterQty = beforeQty.subtract(deductQty);
            int affected = stockMapper.deductStockAvailable(fromStock.getStockId(), deductQty, fromStock.getVersion());
            if (affected == 0) {
                throw new ServiceException("可用库存不足或已被并发占用，请重试: 物料【" + line.getMaterialName() + "】");
            }
            stockTxMapper.insert(buildTransferOutTx(line, fromStock, deductQty, beforeQty, afterQty, now));
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不足");
        }

        // 2. 目标库存增加（ERP-WM-14: 目标仓库/库位改为强制条件，缺失直接拒绝）
        //    修复前用条件 eq：维度为空时该条件被跳过 → 会命中"任意仓库"里同物料同批次的第一条库存行
        //    并直接加数量（跨仓错账）；新建行也会落 warehouse_id/location_id=NULL（库存维度丢失）。
        if (line.getToWarehouseId() == null || line.getToLocationId() == null) {
            throw new ServiceException("调拨目标仓库/库位不能为空: 物料【" + line.getMaterialName() + "】");
        }
        LambdaQueryWrapper<WmMaterialStock> toLqw = Wrappers.lambdaQuery();
        toLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        toLqw.eq(WmMaterialStock::getWarehouseId, line.getToWarehouseId());
        toLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        toLqw.eq(WmMaterialStock::getLocationId, line.getToLocationId());
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
            // 目标库存维度取自目标仓库/调拨单头，而非复制来源行维度（跨仓调拨时来源仓库与目标库位矛盾）
            toStock.setWarehouseId(line.getToWarehouseId());
            toStock.setWarehouseCode(line.getToWarehouseCode());
            toStock.setWarehouseName(line.getToWarehouseName());
            toStock.setAreaId(line.getToAreaId());
            toStock.setAreaCode(line.getToAreaCode());
            toStock.setAreaName(line.getToAreaName());
            toStock.setLocationId(line.getToLocationId());
            toStock.setLocationCode(line.getToLocationCode());
            toStock.setLocationName(line.getToLocationName());
            toStock.setStockQuantity(line.getQuantity());
            toStock.setRecptDate(now);
            toStock.setFrozenFlag(0L);
            toStock.setVersion(0);
            stockMapper.insert(toStock);
        } else {
            toStock = toStocks.get(0);
            int affected = stockMapper.addStock(toStock.getStockId(), line.getQuantity(), toStock.getVersion());
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
            }
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
