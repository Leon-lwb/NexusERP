package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.SubcontractStockMessage;
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
 * 委外发料策略实现
 * <p>
 * 处理委外订单发料出库业务，扣减库存并记录事务流水。
 * 支持按先进先出（FIFO）规则匹配库存，并处理库存并发冲突。
 *
 * @author nexus
 */
@Slf4j
@Component
public class SubcontractIssueStrategy extends BaseStockOperationStrategy {

    /** 事务类型：委外发料 */
    private static final String TX_TYPE_SUBCONTRACT_ISSUE = "SUBCONTRACT_ISSUE";
    /** 来源单据类型：委外订单 */
    private static final String SOURCE_DOC_TYPE_SUBCONTRACT = "PRD_SUBCONTRACT";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialStockMapper stockMapper;
    private final ReliableMessageProducer messageProducer;

    /**
     * 构造委外发料策略实例
     *
     * @param stockTxMapper      库存事务Mapper
     * @param stockMapper        库存Mapper
     * @param messageProducer    消息生产者
     */
    public SubcontractIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                                    WmMaterialStockMapper stockMapper,
                                    ReliableMessageProducer messageProducer) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.messageProducer = messageProducer;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 委外发料操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.SUBCONTRACT_ISSUE;
    }

    /**
     * 执行委外发料出库操作
     *
     * @param args 操作参数，args[0]为委外订单ID（Long），args[1]为物料ID（Long），
     *             args[2]为发料数量（BigDecimal），args[3]为物料名称（String），
     *             args[4]为委外订单编号（String）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long subcontractId = (Long) args[0];
        Long materialId = (Long) args[1];
        BigDecimal quantity = (BigDecimal) args[2];
        String materialName = (String) args[3];
        String subcontractNo = (String) args[4];

        if (hasPosted(SOURCE_DOC_TYPE_SUBCONTRACT, subcontractId)) {
            return;
        }

        Date now = new Date();

        List<WmMaterialStock> stockList = stockMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getMaterialId, materialId)
                .eq(WmMaterialStock::getFrozenFlag, 0L)
                .orderByAsc(WmMaterialStock::getRecptDate));

        if (stockList == null || stockList.isEmpty()) {
            throw new ServiceException("物料【" + materialName + "】库存不足");
        }

        BigDecimal remaining = quantity;
        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = remaining.min(stockQty);
            BigDecimal beforeQty = stockQty;
            BigDecimal afterQty = stockQty.subtract(deductQty);

            int affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + materialName + "】");
            }

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SUBCONTRACT_ISSUE, TX_FLAG_OUT,
                deductQty, beforeQty, afterQty, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_SUBCONTRACT);
            tx.setSourceDocId(subcontractId);
            tx.setSourceDocCode(subcontractNo);
            tx.setRemark("委外发料出库");
            stockTxMapper.insert(tx);

            remaining = remaining.subtract(deductQty);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException("物料【" + materialName + "】库存不足，缺少" + remaining);
        }

        SubcontractStockMessage msg = new SubcontractStockMessage();
        msg.setSubcontractId(subcontractId);
        msg.setOperationType("ISSUE");
        msg.setMaterialId(materialId);
        msg.setQuantity(quantity);
        msg.setSourceModule("wm");
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
            RoutingKeyConstants.SUBCONTRACT_STOCK, msg);
    }
}
