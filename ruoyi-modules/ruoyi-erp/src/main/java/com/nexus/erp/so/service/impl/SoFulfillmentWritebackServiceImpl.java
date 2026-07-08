package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.event.SoLineProducedEvent;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售订单履行数量回写Service实现
 * <p>
 * 采用 setSql 原子累加，规避读后写并发冲突；soLineId 为空或 qty<=0 时跳过。
 * <p>
 * 状态机驱动：累加后检查是否达到阈值，自动推进 SO 行状态：
 * <ul>
 *   <li>production_qty >= ordered_qty -> PRODUCED（已生产）</li>
 *   <li>delivered_qty >= ordered_qty -> DELIVERED（已发货）</li>
 * </ul>
 * PRODUCED 时发布 {@link SoLineProducedEvent}，供下游订阅（如自动创建销售出库单）。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoFulfillmentWritebackServiceImpl implements ISoFulfillmentWritebackService {

    /** SO 行状态：已生产 */
    private static final String LINE_STATUS_PRODUCED = "PRODUCED";
    /** SO 行状态：已发货 */
    private static final String LINE_STATUS_DELIVERED = "DELIVERED";
    /** SO 行状态：已履行（终态，不可回退） */
    private static final String LINE_STATUS_FULFILLED = "FULFILLED";

    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addShippedQty(Long soLineId, BigDecimal qty) {
        if (soLineId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String qtyStr = qty.toPlainString();
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .setSql("delivered_qty = delivered_qty + " + qtyStr)
            .setSql("fulfilled_qty = fulfilled_qty + " + qtyStr);
        soSalesOrderLineMapper.update(null, wrapper);
        log.info("销售出库回写SO行, soLineId={}, qty={}", soLineId, qtyStr);

        // 发货数量达到订单数量时，推进行状态为 DELIVERED
        promoteToDelivered(soLineId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProducedQty(Long soLineId, BigDecimal qty) {
        if (soLineId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String qtyStr = qty.toPlainString();
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .setSql("production_qty = production_qty + " + qtyStr);
        soSalesOrderLineMapper.update(null, wrapper);
        log.info("生产完工回写SO行, soLineId={}, qty={}", soLineId, qtyStr);

        // 投产数量达到订单数量时，推进行状态为 PRODUCED 并发布事件
        promoteToProduced(soLineId);
    }

    /**
     * 投产数量达到订单数量时，推进 SO 行状态为 PRODUCED（已生产）
     * <p>
     * 仅当当前状态早于 PRODUCED（null 或其他非终态）时更新，避免覆盖 DELIVERED/FULFILLED。
     * <p>
     * 注意：不基于内存中的 productionQty 做提前返回——并发累加下 SELECT 可能为快照旧值，
     * 误判为未达阈值而跳过。改为始终执行条件 UPDATE，由 DB 级 ge(production_qty, ordered_qty)
     * 权威判定，确保并发场景下状态推进不丢失。UPDATE 返回 >0 才发布事件，保证事件恰好一次。
     */
    private void promoteToProduced(Long soLineId) {
        SoSalesOrderLine line = soSalesOrderLineMapper.selectOne(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getLineId, soLineId));
        if (line == null || line.getOrderedQty() == null) {
            return;
        }
        // 已达 PRODUCED/DELIVERED/FULFILLED 则不重复推进（状态只前进不回退）
        if (LINE_STATUS_PRODUCED.equals(line.getLineStatus())
            || LINE_STATUS_DELIVERED.equals(line.getLineStatus())
            || LINE_STATUS_FULFILLED.equals(line.getLineStatus())) {
            return;
        }
        // 条件更新：DB 级 production_qty >= ordered_qty 且当前状态非终态。
        // 并发下另一事务的累加已提交时，UPDATE 的 WHERE 能看到最新值并正确推进。
        int rows = soSalesOrderLineMapper.update(null,
            Wrappers.<SoSalesOrderLine>lambdaUpdate()
                .eq(SoSalesOrderLine::getLineId, soLineId)
                .ge(SoSalesOrderLine::getProductionQty, line.getOrderedQty())
                .and(w -> w.isNull(SoSalesOrderLine::getLineStatus)
                    .or().notIn(SoSalesOrderLine::getLineStatus,
                        LINE_STATUS_PRODUCED, LINE_STATUS_DELIVERED, LINE_STATUS_FULFILLED))
                .set(SoSalesOrderLine::getLineStatus, LINE_STATUS_PRODUCED));
        if (rows > 0) {
            applicationEventPublisher.publishEvent(
                new SoLineProducedEvent(this, line.getSoId(), soLineId,
                    line.getMaterialCode(), line.getProductionQty()));
            log.info("SO行已生产完成, soLineId={}, soId={}, productionQty={}",
                soLineId, line.getSoId(), line.getProductionQty());
        }
    }

    /**
     * 发货数量达到订单数量时，推进 SO 行状态为 DELIVERED（已发货）
     * <p>
     * 仅当当前状态非 DELIVERED/FULFILLED 时更新，避免覆盖终态。
     * 与 promoteToProduced 同理：不基于内存 deliveredQty 提前返回，由 DB 级 ge() 权威判定。
     */
    private void promoteToDelivered(Long soLineId) {
        SoSalesOrderLine line = soSalesOrderLineMapper.selectOne(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getLineId, soLineId));
        if (line == null || line.getOrderedQty() == null) {
            return;
        }
        if (LINE_STATUS_DELIVERED.equals(line.getLineStatus())
            || LINE_STATUS_FULFILLED.equals(line.getLineStatus())) {
            return;
        }
        int rows = soSalesOrderLineMapper.update(null,
            Wrappers.<SoSalesOrderLine>lambdaUpdate()
                .eq(SoSalesOrderLine::getLineId, soLineId)
                .ge(SoSalesOrderLine::getDeliveredQty, line.getOrderedQty())
                .and(w -> w.isNull(SoSalesOrderLine::getLineStatus)
                    .or().notIn(SoSalesOrderLine::getLineStatus,
                        LINE_STATUS_DELIVERED, LINE_STATUS_FULFILLED))
                .set(SoSalesOrderLine::getLineStatus, LINE_STATUS_DELIVERED));
        if (rows > 0) {
            log.info("SO行已发货完成, soLineId={}, soId={}, deliveredQty={}",
                soLineId, line.getSoId(), line.getDeliveredQty());
        }
    }
}
