package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.service.IInventoryValuationService;
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

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 库存计价服务(F3-a:出库按移动平均结转成本) */
    private final IInventoryValuationService valuationService;

    /**
     * 构造生产领料策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     * @param valuationService 库存计价服务
     */
    public ProductionIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                                   WmMaterialStockMapper stockMapper,
                                   IInventoryValuationService valuationService) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
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
            deductAndLog(stock, deductQty, line.getIssueId(), line.getLineId(), now);
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
        }
    }

    /**
     * 按已分配库存明细扣减（供倒冲领料使用）
     * <p>
     * 拣货策略已选定具体库存与扣减量，此处逐条重新读取最新库存（取最新乐观锁版本）后扣减并写流水。
     *
     * @param issueId 领料单头ID
     * @param lineId  领料单行ID
     * @param stocks  拣货分配明细
     * @param now     操作时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeAllocated(Long issueId, Long lineId, List<AllocatedStock> stocks, Date now) {
        if (stocks == null || stocks.isEmpty()) {
            throw new RuntimeException("生产领料分配明细为空");
        }
        for (AllocatedStock a : stocks) {
            WmMaterialStock stock = stockMapper.selectById(a.getStockId());
            if (stock == null) {
                throw new ServiceException("库存记录不存在: " + a.getStockId());
            }
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(a.getDeductQty()) < 0) {
                throw new ServiceException("物料【" + stock.getMaterialName() + "】库存不足，无法扣减");
            }
            deductAndLog(stock, a.getDeductQty(), issueId, lineId, now);
        }
    }

    /**
     * 扣减单条库存并写入出库流水
     *
     * @param stock     库存记录
     * @param deductQty 本次扣减量
     * @param issueId   领料单头ID（来源单据ID）
     * @param lineId    领料单行ID（来源单据行ID）
     * @param now       操作时间
     */
    private void deductAndLog(WmMaterialStock stock, BigDecimal deductQty, Long issueId, Long lineId, Date now) {
        BigDecimal beforeQuantity = stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.subtract(deductQty);
        int affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("库存并发冲突，请重试: 物料【" + stock.getMaterialName() + "】");
        }
        // F3-a:出库按移动平均成本结转(扣减 totalCost,返回单位成本)
        BigDecimal outUnitCost = valuationService.issueStockCost(stock.getStockId(), deductQty);
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_PRODUCTION_ISSUE, TX_FLAG_OUT, deductQty,
            beforeQuantity, afterQuantity, now);
        tx.setUnitCost(outUnitCost);
        tx.setSourceDocType(SOURCE_DOC_TYPE_PRODUCTION_ISSUE);
        tx.setSourceDocId(issueId);
        tx.setSourceDocLineId(lineId);
        tx.setRemark("生产领料");
        stockTxMapper.insert(tx);
    }
}
