package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售订单履行数量回写Service实现
 * <p>
 * 采用 setSql 原子累加，规避读后写并发冲突；soLineId 为空或 qty<=0 时跳过。
 * <p>
 * 状态机驱动（有效枚举 so_order_line_status：PENDING/SCHEDULED/PARTIALLY_FULFILLED/FULFILLED/CANCELLED）：
 * 累加后按阈值推进 SO 行状态（状态只前进不回退）：
 * <ul>
 *   <li>production_qty >= ordered_qty -> SCHEDULED（已排产：已投产完工，待发货）</li>
 *   <li>0 &lt; delivered_qty &lt; ordered_qty -> PARTIALLY_FULFILLED（部分履行）</li>
 *   <li>delivered_qty >= ordered_qty -> FULFILLED（已履行，行已发齐；与 fulfill() 按钮幂等）</li>
 * </ul>
 * 达 SCHEDULED 时仅记录日志推进状态。
 *
 * <p><b>T3.5 清理（2026-09-19）</b>：原先此处发布 {@code SoLineProducedEvent}，注释称
 * 「供下游订阅（如自动创建销售出库单）」，但**全仓没有任何监听者**（仓库内 19 个
 * {@code @EventListener}/{@code @TransactionalEventListener} 均不订阅该事件）——
 * 是一个承诺了功能却从未实现的空发布。已连同事件类一并删除。</p>
 *
 * <p>之所以选择删除而非补实现：销售出库单应由仓储按实际发货决策创建
 * （现由 {@code WmSalesIssueServiceImpl} 驱动并回写 {@code delivered_qty}），
 * 若在「投产完工」时自动建出库单，等于**绕过仓储的发货决策**，属错误行为而非缺失功能。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoFulfillmentWritebackServiceImpl implements ISoFulfillmentWritebackService {

    /** SO 行状态：已排产/已采购（投产完工，待发货） */
    private static final String LINE_STATUS_SCHEDULED = "SCHEDULED";
    /** SO 行状态：部分履行（部分发货） */
    private static final String LINE_STATUS_PARTIALLY_FULFILLED = "PARTIALLY_FULFILLED";
    /** SO 行状态：已履行（终态，全额发货或履行按钮） */
    private static final String LINE_STATUS_FULFILLED = "FULFILLED";

    private final SoSalesOrderLineMapper soSalesOrderLineMapper;

    /**
     * 回写销售出库的已发货数量（原子累加 delivered_qty 与 fulfilled_qty）。
     * <p>soLineId 为空或 qty&lt;=0 时跳过；累加后按发货数量推进行状态。</p>
     *
     * @param soLineId 销售订单行ID
     * @param qty      本次发货数量（正数）
     */
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

        // 发货数量达到订单数量时，推进行状态为 FULFILLED（部分发则 PARTIALLY_FULFILLED）
        promoteOnShipped(soLineId);
    }

    /**
     * 回写生产完工的已投产数量（原子累加 production_qty）。
     * <p>soLineId 为空或 qty&lt;=0 时跳过；投产达量后推进行状态为 SCHEDULED 并发布生产完成事件。</p>
     *
     * @param soLineId 销售订单行ID
     * @param qty      本次投产完工数量（正数）
     */
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

        // 投产数量达到订单数量时，推进行状态为 SCHEDULED 并发布事件
        promoteToScheduled(soLineId);
    }

    /**
     * 投产数量达到订单数量时，推进 SO 行状态为 SCHEDULED（已排产：已投产完工，待发货）
     * <p>
     * 仅当当前状态早于 SCHEDULED（null 或其他非终态）时更新，避免覆盖 PARTIALLY_FULFILLED/FULFILLED。
     * <p>
     * 注意：不基于内存中的 productionQty 做提前返回——并发累加下 SELECT 可能为快照旧值，
     * 误判为未达阈值而跳过。改为始终执行条件 UPDATE，由 DB 级 ge(production_qty, ordered_qty)
     * 权威判定，确保并发场景下状态推进不丢失。UPDATE 返回 >0 才发布事件，保证事件恰好一次。
     */
    private void promoteToScheduled(Long soLineId) {
        SoSalesOrderLine line = soSalesOrderLineMapper.selectOne(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getLineId, soLineId));
        if (line == null || line.getOrderedQty() == null) {
            return;
        }
        // 已达 SCHEDULED/PARTIALLY_FULFILLED/FULFILLED 则不重复推进（状态只前进不回退）
        if (LINE_STATUS_SCHEDULED.equals(line.getLineStatus())
            || LINE_STATUS_PARTIALLY_FULFILLED.equals(line.getLineStatus())
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
                        LINE_STATUS_SCHEDULED, LINE_STATUS_PARTIALLY_FULFILLED, LINE_STATUS_FULFILLED))
                .set(SoSalesOrderLine::getLineStatus, LINE_STATUS_SCHEDULED));
        if (rows > 0) {
            log.info("SO行已投产完工, soLineId={}, soId={}, productionQty={}",
                soLineId, line.getSoId(), line.getProductionQty());
        }
    }

    /**
     * 发货数量推进 SO 行状态：全额发货 -> FULFILLED，部分发货 -> PARTIALLY_FULFILLED
     * <p>
     * 仅当当前状态非 FULFILLED 时更新，避免覆盖终态。与 promoteToScheduled 同理：
     * 不基于内存 deliveredQty 提前返回，由 DB 级 ge/gt/lt 权威判定。
     * 先判全额（FULFILLED），未命中再判部分（PARTIALLY_FULFILLED），保证全额发货直达 FULFILLED。
     */
    private void promoteOnShipped(Long soLineId) {
        SoSalesOrderLine line = soSalesOrderLineMapper.selectOne(
            new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getLineId, soLineId));
        if (line == null || line.getOrderedQty() == null) {
            return;
        }
        if (LINE_STATUS_FULFILLED.equals(line.getLineStatus())) {
            return;
        }
        BigDecimal orderedQty = line.getOrderedQty();
        // 全额发货 -> FULFILLED（允许 SCHEDULED/PARTIALLY_FULFILLED 前进到 FULFILLED，已 FULFILLED 不重复）
        int rows = soSalesOrderLineMapper.update(null,
            Wrappers.<SoSalesOrderLine>lambdaUpdate()
                .eq(SoSalesOrderLine::getLineId, soLineId)
                .ge(SoSalesOrderLine::getDeliveredQty, orderedQty)
                .and(w -> w.isNull(SoSalesOrderLine::getLineStatus)
                    .or().ne(SoSalesOrderLine::getLineStatus, LINE_STATUS_FULFILLED))
                .set(SoSalesOrderLine::getLineStatus, LINE_STATUS_FULFILLED));
        if (rows > 0) {
            log.info("SO行已全额发货完成, soLineId={}, soId={}, deliveredQty={}",
                soLineId, line.getSoId(), line.getDeliveredQty());
            return;
        }
        // 部分发货 -> PARTIALLY_FULFILLED（已有发货但未达全额；已 PARTIALLY_FULFILLED/FULFILLED 不重复）
        soSalesOrderLineMapper.update(null,
            Wrappers.<SoSalesOrderLine>lambdaUpdate()
                .eq(SoSalesOrderLine::getLineId, soLineId)
                .gt(SoSalesOrderLine::getDeliveredQty, BigDecimal.ZERO)
                .lt(SoSalesOrderLine::getDeliveredQty, orderedQty)
                .and(w -> w.isNull(SoSalesOrderLine::getLineStatus)
                    .or().notIn(SoSalesOrderLine::getLineStatus,
                        LINE_STATUS_PARTIALLY_FULFILLED, LINE_STATUS_FULFILLED))
                .set(SoSalesOrderLine::getLineStatus, LINE_STATUS_PARTIALLY_FULFILLED));
    }
}
