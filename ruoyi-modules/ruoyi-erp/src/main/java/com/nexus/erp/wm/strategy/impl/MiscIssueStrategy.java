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
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    /**
     * 事务类型：杂项出库
     */
    private static final String TX_TYPE_MISC_OUT = "MISC_OUT";

    /**
     * 来源单据类型：杂项出库单
     */
    private static final String SOURCE_DOC_TYPE_MISC_ISSUE = "WM_MISC_ISSUE";

    /**
     * 事务标志：出库（负数）
     */
    private static final int TX_FLAG_OUT = -1;

    /**
     * 库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 库存计价服务(F3-a:出库按移动平均结转成本)
     */
    private final com.nexus.erp.wm.service.IInventoryValuationService valuationService;

    /**
     * 构造杂项出库策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public MiscIssueStrategy(
            WmMaterialStockTxMapper stockTxMapper,
            WmMaterialStockMapper stockMapper,
            com.nexus.erp.wm.service.IInventoryValuationService valuationService) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
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
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmMiscIssueLine line = (WmMiscIssueLine) args[0];
        Date now = (Date) args[1];
        if (serialInventory != null
                && serialInventory.post(
                        com.nexus.erp.wm.domain.WmSerialPostingRequest.out(
                                "WM_MISC_ISSUE",
                                line.getIssueId(),
                                line.getLineId(),
                                "MISC_ISSUE",
                                line.getMaterialId(),
                                line.getQuantity(),
                                null,
                                line.getLocationId(),
                                line.getBatchId()))) {
            return;
        }

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项出库数量必须大于0");
        }

        List<WmMaterialStock> stockList = resolveMiscIssueStocks(line);
        BigDecimal remaining = line.getQuantity();

        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            // ERP-WM-02: 扣减上限按可用量(在库-已预留)而非账面量, 否则可扣穿其他单据的预留
            BigDecimal available = availableOf(stock);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal deductQty = available.min(remaining);
            BigDecimal beforeQuantity = stock.getStockQuantity();
            BigDecimal afterQuantity = beforeQuantity.subtract(deductQty);
            // ERP-WM-02/WM-09: 原子扣减按可用量守卫(且 frozen_flag=0)
            int affected =
                    stockMapper.deductStockAvailable(
                            stock.getStockId(), deductQty, stock.getVersion());
            if (affected == 0) {
                throw new ServiceException("可用库存不足或已被并发占用，请重试: 物料【" + line.getMaterialName() + "】");
            }

            // F3-a:出库按移动平均成本结转(扣减 totalCost,返回单位成本)
            BigDecimal outUnitCost = valuationService.issueStockCost(stock.getStockId(), deductQty);
            WmMaterialStockTx tx =
                    buildBaseTxFromStock(
                            stock,
                            TX_TYPE_MISC_OUT,
                            TX_FLAG_OUT,
                            deductQty,
                            beforeQuantity,
                            afterQuantity,
                            now);
            tx.setUnitCost(outUnitCost);
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
        // 排除已预留量，防止杂项出库扣走销售预留库存
        lqw.apply("(stock_quantity - reserved_quantity) > 0");
        // ERP-WM-06: 效期拦截 —— 已过期批次不得参与杂项出库
        lqw.apply(NOT_EXPIRED_SQL);
        lqw.orderByAsc(
                WmMaterialStock::getExpireDate,
                WmMaterialStock::getRecptDate,
                WmMaterialStock::getStockId);

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }
        return stockList;
    }
}
