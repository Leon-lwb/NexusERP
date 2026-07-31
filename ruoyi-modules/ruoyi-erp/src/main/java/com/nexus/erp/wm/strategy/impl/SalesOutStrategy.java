package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
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

    /** 销售发料数据访问映射器（Mapper） */
    private final WmSalesIssueMapper salesIssueMapper;
    /** 销售发料行数据访问映射器（Mapper） */
    private final WmSalesIssueLineMapper salesIssueLineMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 销售出库数据访问映射器（Mapper） */
    private final SoSalesOrderLineMapper salesOrderLineMapper;
    /** 销售出库业务服务，提供销售出库相关能力 */
    private final ISoFulfillmentWritebackService fulfillmentWritebackService;
    /** 库存计价服务(F3-a:出库按移动平均结转成本) */
    private final com.nexus.erp.wm.service.IInventoryValuationService valuationService;

    /**
     * 构造销售出库策略实例
     *
     * @param stockTxMapper              库存事务Mapper
     * @param salesIssueMapper           销售出库单Mapper
     * @param salesIssueLineMapper       销售出库单行Mapper
     * @param stockMapper                库存Mapper
     * @param salesOrderLineMapper       销售订单行Mapper
     * @param fulfillmentWritebackService 销售订单履行回写Service
     * @param valuationService           库存计价服务
     */
    public SalesOutStrategy(WmMaterialStockTxMapper stockTxMapper,
                            WmSalesIssueMapper salesIssueMapper,
                            WmSalesIssueLineMapper salesIssueLineMapper,
                            WmMaterialStockMapper stockMapper,
                            SoSalesOrderLineMapper salesOrderLineMapper,
                            ISoFulfillmentWritebackService fulfillmentWritebackService,
                            com.nexus.erp.wm.service.IInventoryValuationService valuationService) {
        super(stockTxMapper);
        this.salesIssueMapper = salesIssueMapper;
        this.salesIssueLineMapper = salesIssueLineMapper;
        this.stockMapper = stockMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.fulfillmentWritebackService = fulfillmentWritebackService;
        this.valuationService = valuationService;
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

            // 查询 SO 行的预留数量
            SoSalesOrderLine soLine = line.getSourceDocLineId() != null
                ? salesOrderLineMapper.selectById(line.getSourceDocLineId()) : null;
            BigDecimal reservedQty = soLine != null && soLine.getReservedQty() != null
                ? soLine.getReservedQty() : BigDecimal.ZERO;
            BigDecimal totalConsumeReserved = line.getQuantityIssue().min(reservedQty);
            BigDecimal remainingConsumeReserved = totalConsumeReserved;

            List<WmMaterialStock> stockList = resolveSalesIssueStocks(salesIssue, line);
            BigDecimal remaining = line.getQuantityIssue();

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
                if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal deductQty = stockQty.min(remaining);
                BigDecimal beforeQuantity = stockQty;
                BigDecimal afterQuantity = stockQty.subtract(deductQty);

                // 本次扣减消费的预留量: 取 min(本次扣减, 该批次已预留, 剩余待消费预留)
                BigDecimal stockReserved = stock.getReservedQuantity() == null
                    ? BigDecimal.ZERO : stock.getReservedQuantity();
                BigDecimal batchConsumeReserved = deductQty
                    .min(stockReserved).min(remainingConsumeReserved);

                int affected;
                if (batchConsumeReserved.compareTo(BigDecimal.ZERO) > 0) {
                    affected = stockMapper.deductStockWithReserved(
                        stock.getStockId(), deductQty, batchConsumeReserved, stock.getVersion());
                } else {
                    affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
                }
                if (affected == 0) {
                    throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
                }
                remainingConsumeReserved = remainingConsumeReserved.subtract(batchConsumeReserved);
                // F3-a:出库按移动平均成本结转(扣减 totalCost,返回单位成本)
                BigDecimal outUnitCost = valuationService.issueStockCost(stock.getStockId(), deductQty);
                stockTxMapper.insert(buildSalesOutStockTx(salesIssue, line, stock, beforeQuantity, afterQuantity, now, outUnitCost));
                remaining = remaining.subtract(deductQty);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
            }

            // 原子更新 SO 行剩余预留数量(避免并发出库覆盖)
            if (totalConsumeReserved.compareTo(BigDecimal.ZERO) > 0 && soLine != null) {
                LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
                    .eq(SoSalesOrderLine::getLineId, soLine.getLineId())
                    .ge(SoSalesOrderLine::getReservedQty, totalConsumeReserved)
                    .setSql("reserved_qty = reserved_qty - " + totalConsumeReserved.toPlainString());
                int updateRows = salesOrderLineMapper.update(null, wrapper);
                if (updateRows == 0) {
                    throw new ServiceException("SO行预留数量更新失败(并发冲突或预留不足), soLineId=" + soLine.getLineId());
                }
            }

            // 回写 SO 行已发货/已履行数量（销售出库即对该行的履行交付）
            if (soLine != null) {
                fulfillmentWritebackService.addShippedQty(soLine.getLineId(), line.getQuantityIssue());
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
                                                   BigDecimal afterQuantity, Date now, BigDecimal outUnitCost) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SALES_OUT, TX_FLAG_OUT, line.getQuantityIssue(),
            beforeQuantity, afterQuantity, now);
        tx.setUnitCost(outUnitCost);
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
