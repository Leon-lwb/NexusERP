package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 委外发料策略实现
 * 处理委外订单发料出库，扣减库存并记录事务流水
 *
 * @author nexus
 */
@Slf4j
@Component
public class SubcontractIssueStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_SUBCONTRACT_ISSUE = "SUBCONTRACT_ISSUE";
    private static final String SOURCE_DOC_TYPE_SUBCONTRACT = "PRD_SUBCONTRACT";
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;
    private final PrdSubcontractMapper subcontractMapper;

    public SubcontractIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                                    WmMaterialStockMapper stockMapper,
                                    PrdSubcontractMapper subcontractMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.subcontractMapper = subcontractMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.SUBCONTRACT_ISSUE;
    }

    @Override
    public void execute(Object... args) {
        Long subcontractId = (Long) args[0];
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new RuntimeException("委外订单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_SUBCONTRACT, subcontractId)) {
            return;
        }

        if (subcontract.getMaterialId() == null || subcontract.getPlannedQty() == null) {
            throw new RuntimeException("委外订单物料信息不完整");
        }

        Date now = new Date();
        BigDecimal issueQty = subcontract.getPlannedQty();

        List<WmMaterialStock> stockList = stockMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getMaterialId, subcontract.getMaterialId())
                .eq(WmMaterialStock::getFrozenFlag, 0L)
                .orderByAsc(WmMaterialStock::getRecptDate));

        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + subcontract.getMaterialName() + "】库存不足");
        }

        BigDecimal remaining = issueQty;
        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = remaining.min(stockQty);
            BigDecimal beforeQty = stockQty;
            BigDecimal afterQty = stockQty.subtract(deductQty);

            stock.setStockQuantity(afterQty);
            stockMapper.updateById(stock);

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SUBCONTRACT_ISSUE, TX_FLAG_OUT,
                deductQty, beforeQty, afterQty, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_SUBCONTRACT);
            tx.setSourceDocId(subcontract.getSubcontractId());
            tx.setSourceDocCode(subcontract.getSubcontractNo());
            tx.setRemark("委外发料出库");
            stockTxMapper.insert(tx);

            remaining = remaining.subtract(deductQty);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + subcontract.getMaterialName() + "】库存不足，缺少" + remaining);
        }
    }
}
