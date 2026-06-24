package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
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
 * 生产领料策略
 * <p>
 * 处理生产领料出库业务，根据生产领料单行数据扣减库存并生成库存流水记录。
 * 支持按先进先出（FIFO）规则匹配库存，并处理多批次库存拆分扣减。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ProductionIssueStrategy extends BaseStockOperationStrategy {

    /** 事务类型：生产领料 */
    private static final String TX_TYPE_PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    /** 来源单据类型：生产领料单 */
    private static final String SOURCE_DOC_TYPE_PRODUCTION_ISSUE = "WM_PRODUCTION_ISSUE";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;

    /**
     * 构造生产领料策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public ProductionIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                                   WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 生产领料操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.PRODUCTION_ISSUE;
    }

    /**
     * 执行生产领料出库操作
     *
     * @param args 操作参数，args[0]为领料单行对象（WmProductionIssueLine），args[1]为操作时间（Date）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmProductionIssueLine line = (WmProductionIssueLine) args[0];
        Date now = (Date) args[1];

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("生产领料数量必须大于0");
        }

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        // 仅扣减未冻结库存：frozen_flag 0=未冻结/可用，1=已冻结
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }

        BigDecimal remaining = line.getQuantity();
        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = stockQty.min(remaining);
            BigDecimal beforeQuantity = stockQty;
            BigDecimal afterQuantity = stockQty.subtract(deductQty);
            int affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
            }

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_PRODUCTION_ISSUE, TX_FLAG_OUT, deductQty,
                beforeQuantity, afterQuantity, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_PRODUCTION_ISSUE);
            tx.setSourceDocId(line.getIssueId());
            tx.setSourceDocLineId(line.getLineId());
            tx.setRemark("生产领料");
            stockTxMapper.insert(tx);
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
        }
    }
}
