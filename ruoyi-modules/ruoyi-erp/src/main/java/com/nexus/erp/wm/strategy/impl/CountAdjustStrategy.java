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
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 盘点调整策略
 * <p>
 * 处理库存盘点调整业务，根据盘点差异进行盘盈入库或盘亏出库。
 * 支持通过库存ID直接定位库存记录，或按物料、批次、库位等维度匹配库存。
 *
 * @author nexus
 */
@Slf4j
@Component
public class CountAdjustStrategy extends BaseStockOperationStrategy {

    /** 事务类型：盘点调整 */
    private static final String TX_TYPE_COUNT_ADJUST = "COUNT_ADJUST";
    /** 来源单据类型：库存盘点单 */
    private static final String SOURCE_DOC_TYPE_STOCK_TAKING = "WM_STOCK_TAKING";
    /** 事务标志：入库（正数，盘盈） */
    private static final int TX_FLAG_IN = 1;
    /** 事务标志：出库（负数，盘亏） */
    private static final int TX_FLAG_OUT = -1;

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 构造盘点调整策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public CountAdjustStrategy(WmMaterialStockTxMapper stockTxMapper,
                               WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 盘点调整操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.COUNT_ADJUST;
    }

    /**
     * 执行盘点调整操作
     *
     * @param args 操作参数，依次为：盘点单行对象（WmStockTakingLine）、实际数量、账面数量、差异数量、操作时间（Date）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        int affected;
        if (diffQuantity.compareTo(BigDecimal.ZERO) > 0) {
            affected = stockMapper.addStock(stock.getStockId(), absDiff, stock.getVersion());
        } else {
            // 盘点盘亏必须能把冻结行调整到实盘值(冻结是质量状态、不是库存维度),
            // 故此处用不带 frozen_flag 守卫的入口; deductStock 已按 ERP-WM-09 加了冻结守卫。
            affected = stockMapper.deductStockIgnoreFrozen(stock.getStockId(), absDiff, stock.getVersion());
        }
        if (affected == 0) {
            throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
        }

        // afterQty 用原子更新后的实际库存（重读），不能用快照推算——
        // 盘点行生成后、过账前若发生出入库，bookQuantity+diff 与真实库存对不上，流水前后断裂
        WmMaterialStock afterStock = stockMapper.selectById(stock.getStockId());
        BigDecimal afterQty = afterStock != null && afterStock.getStockQuantity() != null
            ? afterStock.getStockQuantity() : beforeQty.add(diffQuantity);

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_COUNT_ADJUST, txFlag, absDiff,
            beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_STOCK_TAKING);
        tx.setSourceDocId(line.getPlanId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("盘点调整，账面:" + bookQuantity + " 实际:" + actualQuantity + " 差异:" + diffQuantity);
        stockTxMapper.insert(tx);
    }
}
