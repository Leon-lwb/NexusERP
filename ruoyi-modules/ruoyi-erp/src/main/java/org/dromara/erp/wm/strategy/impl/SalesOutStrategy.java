package org.dromara.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.domain.WmSalesIssue;
import org.dromara.erp.wm.domain.WmSalesIssueLine;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.enums.StockOperationTypeEnum;
import org.dromara.erp.wm.mapper.WmSalesIssueLineMapper;
import org.dromara.erp.wm.mapper.WmSalesIssueMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售出库策略
 */
@Slf4j
@Component
public class SalesOutStrategy extends BaseStockOperationStrategy {

    private static final String SOURCE_DOC_TYPE_SALES_ISSUE = "WM_SALES_ISSUE";
    private static final String TX_TYPE_SALES_OUT = "SALES_OUT";
    private static final int TX_FLAG_OUT = -1;

    private final WmSalesIssueMapper salesIssueMapper;
    private final WmSalesIssueLineMapper salesIssueLineMapper;
    private final WmMaterialStockMapper stockMapper;

    public SalesOutStrategy(WmMaterialStockTxMapper stockTxMapper,
                            WmSalesIssueMapper salesIssueMapper,
                            WmSalesIssueLineMapper salesIssueLineMapper,
                            WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.salesIssueMapper = salesIssueMapper;
        this.salesIssueLineMapper = salesIssueLineMapper;
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.SALES_OUT;
    }

    @Override
    public void execute(Object... args) {
        Long issueId = (Long) args[0];
        WmSalesIssue salesIssue = salesIssueMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_SALES_ISSUE, issueId)) {
            return;
        }

        List<WmSalesIssueLine> lineList = salesIssueLineMapper.selectList(
            Wrappers.lambdaQuery(WmSalesIssueLine.class)
                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new RuntimeException("出库单明细为空");
        }

        Date now = new Date();
        for (WmSalesIssueLine line : lineList) {
            if (line.getQuantityIssue() == null || line.getQuantityIssue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】出库数量必须大于0");
            }

            List<WmMaterialStock> stockList = resolveSalesIssueStocks(salesIssue, line);
            BigDecimal remaining = line.getQuantityIssue();

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
                if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal deductQty = stockQty.min(remaining);
                BigDecimal beforeQuantity = stockQty;
                BigDecimal afterQuantity = stockQty.subtract(deductQty);
                stock.setStockQuantity(afterQuantity);
                stockMapper.updateById(stock);
                stockTxMapper.insert(buildSalesOutStockTx(salesIssue, line, stock, beforeQuantity, afterQuantity, now));
                remaining = remaining.subtract(deductQty);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
            }
        }
    }

    private List<WmMaterialStock> resolveSalesIssueStocks(WmSalesIssue salesIssue, WmSalesIssueLine line) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
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

    private WmMaterialStockTx buildSalesOutStockTx(WmSalesIssue salesIssue, WmSalesIssueLine line,
                                                   WmMaterialStock stock, BigDecimal beforeQuantity,
                                                   BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SALES_OUT, TX_FLAG_OUT, line.getQuantityIssue(),
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_SALES_ISSUE);
        tx.setSourceDocId(salesIssue.getIssueId());
        tx.setSourceDocCode(salesIssue.getIssueCode());
        tx.setSourceDocLineId(line.getLineId());
        tx.setErpDate(salesIssue.getIssueDate());
        tx.setAttr1(salesIssue.getSoNo());
        tx.setAttr3(salesIssue.getSoId());
        tx.setRemark("销售出库扣减库存，业务单号：" + salesIssue.getIssueCode() + "，业务类型：" + SOURCE_DOC_TYPE_SALES_ISSUE);
        return tx;
    }
}
