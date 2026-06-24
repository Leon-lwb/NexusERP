package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
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
 * 销售出库策略
 * <p>
 * 处理销售出库业务，根据销售出库单明细扣减库存并生成库存流水记录。
 * 支持按仓库、批次、库位等维度匹配库存，并按先进先出（FIFO）规则出库。
 *
 * @author nexus
 */
@Slf4j
@Component
public class SalesOutStrategy extends BaseStockOperationStrategy {

    /** 来源单据类型：销售出库单 */
    private static final String SOURCE_DOC_TYPE_SALES_ISSUE = "WM_SALES_ISSUE";
    /** 事务类型：销售出库 */
    private static final String TX_TYPE_SALES_OUT = "SALES_OUT";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;

    private final WmSalesIssueMapper salesIssueMapper;
    private final WmSalesIssueLineMapper salesIssueLineMapper;
    private final WmMaterialStockMapper stockMapper;

    /**
     * 构造销售出库策略实例
     *
     * @param stockTxMapper        库存事务Mapper
     * @param salesIssueMapper     销售出库单Mapper
     * @param salesIssueLineMapper 销售出库单行Mapper
     * @param stockMapper          库存Mapper
     */
    public SalesOutStrategy(WmMaterialStockTxMapper stockTxMapper,
                            WmSalesIssueMapper salesIssueMapper,
                            WmSalesIssueLineMapper salesIssueLineMapper,
                            WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.salesIssueMapper = salesIssueMapper;
        this.salesIssueLineMapper = salesIssueLineMapper;
        this.stockMapper = stockMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 销售出库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.SALES_OUT;
    }

    /**
     * 执行销售出库操作
     *
     * @param args 操作参数，args[0]为销售出库单ID（Long）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
                int affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
                if (affected == 0) {
                    throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
                }
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
