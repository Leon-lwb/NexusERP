package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmMiscIssueLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 杂项出库策略
 * <p>
 * 处理杂项出库业务，根据杂项出库单行数据扣减库存并生成库存流水记录。
 * 支持按先进先出（FIFO）规则匹配库存，并处理多批次库存拆分扣减。
 *
 * @author nexus
 */
@Slf4j
@Component
public class MiscIssueStrategy extends BaseStockOperationStrategy {

    /** 事务类型：杂项出库 */
    private static final String TX_TYPE_MISC_OUT = "MISC_OUT";
    /** 来源单据类型：杂项出库单 */
    private static final String SOURCE_DOC_TYPE_MISC_ISSUE = "WM_MISC_ISSUE";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;

    /**
     * 构造杂项出库策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public MiscIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                             WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 杂项出库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.MISC_ISSUE;
    }

    /**
     * 执行杂项出库操作
     *
     * @param args 操作参数，args[0]为出库单行对象（WmMiscIssueLine），args[1]为操作时间（Date）
     */
    @Override
    public void execute(Object... args) {
        WmMiscIssueLine line = (WmMiscIssueLine) args[0];
        Date now = (Date) args[1];

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项出库数量必须大于0");
        }

        List<WmMaterialStock> stockList = resolveMiscIssueStocks(line);
        BigDecimal remaining = line.getQuantity();

        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = stockQty.min(remaining);
            BigDecimal beforeQuantity = stockQty;
            BigDecimal afterQuantity = stockQty.subtract(deductQty);
            stock.setStockQuantity(afterQuantity);
            stockMapper.updateById(stock);

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_MISC_OUT, TX_FLAG_OUT, deductQty,
                beforeQuantity, afterQuantity, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_MISC_ISSUE);
            tx.setSourceDocId(line.getIssueId());
            tx.setSourceDocLineId(line.getLineId());
            tx.setRemark("杂项出库");
            stockTxMapper.insert(tx);
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
        }
    }

    private List<WmMaterialStock> resolveMiscIssueStocks(WmMiscIssueLine line) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }
        return stockList;
    }
}
