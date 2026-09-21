package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ReworkStockMessage;
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
 * 返工领料策略实现
 * <p>
 * 处理返工订单领料出库业务，扣减库存并记录事务流水。
 * 支持按先进先出（FIFO）规则匹配库存，并处理库存并发冲突。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ReworkIssueStrategy extends BaseStockOperationStrategy {

    /** 事务类型：返工领料 */
    private static final String TX_TYPE_REWORK_ISSUE = "REWORK_ISSUE";
    /** 来源单据类型：返工订单 */
    private static final String SOURCE_DOC_TYPE_REWORK = "PRD_REWORK_ORDER";
    /** 事务标志：出库（负数） */
    private static final int TX_FLAG_OUT = -1;

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 消息提供器 */
    private final ReliableMessageProducer messageProducer;

    /**
     * 构造返工领料策略实例
     *
     * @param stockTxMapper      库存事务Mapper
     * @param stockMapper        库存Mapper
     * @param messageProducer    消息生产者
     */
    public ReworkIssueStrategy(WmMaterialStockTxMapper stockTxMapper,
                               WmMaterialStockMapper stockMapper,
                               ReliableMessageProducer messageProducer) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.messageProducer = messageProducer;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 返工领料操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.REWORK_ISSUE;
    }

    /**
     * 执行返工领料出库操作
     * <p>参数契约与 {@code IWmStockOperationService#postReworkIssue} 一致(9参):
     * args[0]=reworkId, args[1]=reworkNo, args[2]=materialId, args[3]=materialCode,
     * args[4]=materialName, args[5]=quantity, args[6]=warehouseId(可空), args[7]=locationId(可空), args[8]=now。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long reworkId = (Long) args[0];
        String reworkNo = (String) args[1];
        Long materialId = (Long) args[2];
        String materialCode = (String) args[3];
        String materialName = (String) args[4];
        BigDecimal quantity = (BigDecimal) args[5];
        Long warehouseId = (Long) args[6];
        Date now = args[8] instanceof Date ? (Date) args[8] : new Date();

        if (hasPosted(SOURCE_DOC_TYPE_REWORK, reworkId)) {
            return;
        }

        List<WmMaterialStock> stockList = stockMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .eq(WmMaterialStock::getMaterialId, materialId)
                .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
                .eq(WmMaterialStock::getFrozenFlag, 0L)
                .orderByAsc(WmMaterialStock::getRecptDate));

        if (stockList == null || stockList.isEmpty()) {
            throw new ServiceException("物料【" + materialCode + "/" + materialName + "】库存不足");
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
                throw new ServiceException("库存并发冲突，请重试: 物料【" + materialCode + "/" + materialName + "】");
            }

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_REWORK_ISSUE, TX_FLAG_OUT,
                deductQty, beforeQty, afterQty, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_REWORK);
            tx.setSourceDocId(reworkId);
            tx.setSourceDocCode(reworkNo);
            tx.setRemark("返工领料出库");
            stockTxMapper.insert(tx);

            remaining = remaining.subtract(deductQty);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException("物料【" + materialCode + "/" + materialName + "】库存不足，缺少" + remaining);
        }

        ReworkStockMessage msg = new ReworkStockMessage();
        msg.setReworkId(reworkId);
        msg.setOperationType("ISSUE");
        msg.setMaterialId(materialId);
        msg.setQuantity(quantity);
        msg.setSourceModule("wm");
        // 显式携带租户(消费端 fail-closed: 缺 tenantId 会重试/死信, 绝不静默落到 000000):
        // 拿不到可靠租户即显式失败并回滚本次出库, 不投递必然死信的消息
        String tenantIdStr = TenantHelper.getTenantId();
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new ServiceException("返工领料出库 MQ 消息缺少租户上下文, 拒绝投递: reworkId=" + reworkId);
        }
        try {
            msg.setTenantId(Long.parseLong(tenantIdStr));
        } catch (NumberFormatException e) {
            throw new ServiceException("返工领料出库 MQ 消息租户ID非法, 拒绝投递: tenantId=" + tenantIdStr);
        }
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
            RoutingKeyConstants.REWORK_STOCK, msg);
    }
}
