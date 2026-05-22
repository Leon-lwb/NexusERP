package org.dromara.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.domain.WmProductionIssueLine;
import org.dromara.erp.wm.enums.StockOperationTypeEnum;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 生产领料策略
 */
@Slf4j
@Component
public class ProductionIssueStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    private static final String SOURCE_DOC_TYPE_PRODUCTION_ISSUE = "WM_PRODUCTION_ISSUE";
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;

    public ProductionIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                                   WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.PRODUCTION_ISSUE;
    }

    @Override
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
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
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
            stock.setStockQuantity(afterQuantity);
            stockMapper.updateById(stock);

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
