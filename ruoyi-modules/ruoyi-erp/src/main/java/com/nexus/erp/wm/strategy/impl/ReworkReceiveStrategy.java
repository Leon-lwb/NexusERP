package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdReworkOrder;
import com.nexus.erp.prd.mapper.PrdReworkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 返工入库策略实现
 * <p>
 * 处理返工订单完工入库业务，增加库存并记录事务流水。
 * 支持查询或创建库存记录，并处理库存并发冲突。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ReworkReceiveStrategy extends BaseStockOperationStrategy {

    /** 事务类型：返工入库 */
    private static final String TX_TYPE_REWORK_RECEIVE = "REWORK_RECEIVE";
    /** 来源单据类型：返工订单 */
    private static final String SOURCE_DOC_TYPE_REWORK = "PRD_REWORK_ORDER";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;
    private final PrdReworkOrderMapper reworkOrderMapper;

    /**
     * 构造返工入库策略实例
     *
     * @param stockTxMapper      库存事务Mapper
     * @param stockMapper        库存Mapper
     * @param reworkOrderMapper  返工订单Mapper
     */
    public ReworkReceiveStrategy(WmMaterialStockTxMapper stockTxMapper,
                                 WmMaterialStockMapper stockMapper,
                                 PrdReworkOrderMapper reworkOrderMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.reworkOrderMapper = reworkOrderMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 返工入库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.REWORK_RECEIVE;
    }

    /**
     * 执行返工入库操作
     *
     * @param args 操作参数，args[0]为返工订单ID（Long）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long reworkId = (Long) args[0];
        PrdReworkOrder rework = reworkOrderMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_REWORK + "_RECEIVE", reworkId)) {
            return;
        }

        BigDecimal receiveQty = rework.getActualQty() != null ? rework.getActualQty() : rework.getReworkQty();
        if (receiveQty == null || receiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("返工入库数量必须大于0");
        }

        Date now = new Date();

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, rework.getMaterialId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);

        WmMaterialStock stock = null;
        if (stockList != null && !stockList.isEmpty()) {
            stock = stockList.get(0);
        }

        BigDecimal beforeQty = BigDecimal.ZERO;
        if (stock != null) {
            beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            BigDecimal afterQty = beforeQty.add(receiveQty);
            stock.setStockQuantity(afterQty);
            stock.setRecptDate(now);
            int affected = stockMapper.updateById(stock);
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + rework.getMaterialName() + "】");
            }
        } else {
            stock = new WmMaterialStock();
            stock.setMaterialId(rework.getMaterialId());
            stock.setMaterialCode(rework.getMaterialCode());
            stock.setMaterialName(rework.getMaterialName());
            stock.setMaterialSpec(rework.getMaterialSpec());
            stock.setBaseUomName(rework.getBaseUomName());
            stock.setStockQuantity(receiveQty);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_REWORK_RECEIVE, TX_FLAG_IN,
            receiveQty, beforeQty, beforeQty.add(receiveQty), now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_REWORK);
        tx.setSourceDocId(rework.getReworkId());
        tx.setSourceDocCode(rework.getReworkNo());
        tx.setRemark("返工入库");
        stockTxMapper.insert(tx);
    }
}
