package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.erp.wm.service.WmSerialInventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 库存预留服务实现
 * FIFO 多批次预留: 按 expireDate/recptDate/stockId 排序, 逐条预留直到满足需求量
 *
 * @author nexus
 */
@Slf4j
@DS("erp")
@Service
@RequiredArgsConstructor
public class WmStockReservationServiceImpl implements IWmStockReservationService {
    @Autowired
    private WmSerialInventoryService serialInventory;

    /**
     * 流水类型：预留
     */
    public static final String TX_TYPE_RESERVE = "RESERVE";

    /**
     * 流水类型：预留消耗（出库时核销预留，ERP-WM-03）
     */
    public static final String TX_TYPE_CONSUME_RESERVE = "CONSUME_RESERVE";

    /**
     * 流水类型：预留释放
     */
    public static final String TX_TYPE_UNRESERVE = "UNRESERVE";

    /**
     * 库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 流水数据访问映射器（Mapper）
     */
    private final WmMaterialStockTxMapper txMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reserve(
            Long materialId,
            Long warehouseId,
            BigDecimal reserveQty,
            String sourceDocType,
            Long sourceDocId,
            Long sourceDocLineId) {
        if (reserveQty == null || reserveQty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (serialInventory != null) {
            serialInventory.validateReservation(materialId, reserveQty);
        }
        // 查询可用库存批次 (FIFO: 先到期先出)
        LambdaQueryWrapper<WmMaterialStock> wrapper =
                Wrappers.<WmMaterialStock>lambdaQuery()
                        .eq(WmMaterialStock::getMaterialId, materialId)
                        .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
                        .eq(WmMaterialStock::getFrozenFlag, 0L)
                        .apply("(expire_date IS NULL OR expire_date >= CURDATE())")
                        .apply("(stock_quantity - reserved_quantity) > 0")
                        .orderByAsc(WmMaterialStock::getExpireDate)
                        .orderByAsc(WmMaterialStock::getRecptDate)
                        .orderByAsc(WmMaterialStock::getStockId);
        List<WmMaterialStock> stocks = stockMapper.selectList(wrapper);

        BigDecimal remaining = reserveQty;
        BigDecimal reserved = BigDecimal.ZERO;
        for (WmMaterialStock stock : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = stock.getAvailableQuantity();
            BigDecimal toReserve = remaining.min(available);
            if (toReserve.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int rows = stockMapper.reserveStock(stock.getStockId(), toReserve, stock.getVersion());
            if (rows > 0) {
                reserved = reserved.add(toReserve);
                remaining = remaining.subtract(toReserve);
                recordStockTx(
                        stock,
                        toReserve,
                        TX_TYPE_RESERVE,
                        sourceDocType,
                        sourceDocId,
                        sourceDocLineId);
                log.info(
                        "预留库存: stockId={}, qty={}, source={}/{}",
                        stock.getStockId(),
                        toReserve,
                        sourceDocType,
                        sourceDocId);
            }
        }
        if (reserved.compareTo(reserveQty) < 0) {
            // 预留不足显式抛异常，禁止静默部分预留（调用方无法感知会导致销售承诺落空）
            throw new ServiceException(
                    "库存预留不足: 需求=" + reserveQty + ", 实际可预留=" + reserved + ", 物料ID=" + materialId);
        }
        return reserved;
    }

    /**
     * 库存预留处理（unreserve）
     *
     * @param sourceDocType 参数 sourceDocType
     * @param sourceDocId sourceDoc ID
     * @param sourceDocLineId sourceDoc行 ID
     * @return 结果
     */
    /**
     * 释放预留（按来源单据，ERP-WM-03 修复）
     * <p>原实现按历史 {@code RESERVE} 流水<b>全额</b>释放，而出库消耗预留时只减
     * {@code reserved_quantity} 不写流水，于是「已出库的订单再取消/关闭」会把同一库存行上
     * <b>其他单据</b>的预留释放掉（可用量虚增/超卖），或在预留不足时抛异常阻断订单取消/关闭。</p>
     * <p>现改为按<b>净额</b>释放：对每个库存行按
     * {@code SUM(RESERVE) - SUM(CONSUME_RESERVE) - SUM(UNRESERVE)} 计算仍未消耗的预留量，
     * 净额 &le; 0 直接跳过（幂等，可重复调用），净额 &gt; 0 才释放。
     * 出库消耗预留的写入点见 {@code SalesOutStrategy} / {@code WmSalesIssueServiceImpl}
     * 写入的 {@code CONSUME_RESERVE} 流水。</p>
     *
     * @param sourceDocType 来源单据类型
     * @param sourceDocId 来源单据ID
     * @param sourceDocLineId 来源单据行ID（可为 null，表示该单据全部行）
     * @return 本次实际释放数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal unreserve(String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        // 查询该来源单据的预留 / 预留消耗 / 已释放流水，按库存行计算净额
        LambdaQueryWrapper<WmMaterialStockTx> wrapper =
                Wrappers.<WmMaterialStockTx>lambdaQuery()
                        .in(
                                WmMaterialStockTx::getTxType,
                                TX_TYPE_RESERVE,
                                TX_TYPE_CONSUME_RESERVE,
                                TX_TYPE_UNRESERVE)
                        .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
                        .eq(WmMaterialStockTx::getSourceDocId, sourceDocId)
                        .eq(
                                sourceDocLineId != null,
                                WmMaterialStockTx::getSourceDocLineId,
                                sourceDocLineId);
        List<WmMaterialStockTx> txs = txMapper.selectList(wrapper);

        // 按库存行聚合净预留额：RESERVE 为正，CONSUME_RESERVE / UNRESERVE 为负
        Map<Long, BigDecimal> netByStock = new TreeMap<>();
        for (WmMaterialStockTx tx : txs) {
            if (tx.getMaterialStockId() == null || tx.getTxQuantity() == null) {
                continue;
            }
            boolean isReserve = TX_TYPE_RESERVE.equals(tx.getTxType());
            BigDecimal signed = isReserve ? tx.getTxQuantity() : tx.getTxQuantity().negate();
            netByStock.merge(tx.getMaterialStockId(), signed, BigDecimal::add);
        }

        BigDecimal released = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : netByStock.entrySet()) {
            Long stockId = entry.getKey();
            BigDecimal netQty = entry.getValue();
            if (netQty == null || netQty.compareTo(BigDecimal.ZERO) <= 0) {
                // 已全部被出库消耗或已释放过 —— 无剩余预留，跳过（不再全额释放他人预留）
                continue;
            }
            // 先按稳定顺序锁真实库存，再做当前读；旧快照的净额不能用于版本重试。
            WmMaterialStock stock = stockMapper.selectOne(Wrappers.<WmMaterialStock>lambdaQuery()
                .eq(WmMaterialStock::getStockId, stockId).last("FOR UPDATE"));
            if (stock == null) {
                throw new ServiceException("预留库存记录不存在");
            }
            List<WmMaterialStockTx> current = txMapper.selectList(Wrappers.<WmMaterialStockTx>lambdaQuery()
                .in(WmMaterialStockTx::getTxType, TX_TYPE_RESERVE, TX_TYPE_CONSUME_RESERVE, TX_TYPE_UNRESERVE)
                .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
                .eq(WmMaterialStockTx::getSourceDocId, sourceDocId)
                .eq(WmMaterialStockTx::getMaterialStockId, stockId)
                .orderByAsc(WmMaterialStockTx::getTxId).last("FOR UPDATE"));
            Map<Long, BigDecimal> currentByLine = new LinkedHashMap<>();
            for (WmMaterialStockTx tx : current) {
                if (!stockId.equals(tx.getMaterialStockId()) || tx.getTxQuantity() == null) continue;
                currentByLine.merge(tx.getSourceDocLineId(), TX_TYPE_RESERVE.equals(tx.getTxType())
                    ? tx.getTxQuantity() : tx.getTxQuantity().negate(), BigDecimal::add);
            }
            BigDecimal totalRemaining = currentByLine.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalRemaining.signum() <= 0) continue;
            if (currentByLine.values().stream().anyMatch(value -> value.signum() < 0)) {
                throw new ServiceException("历史预留释放缺少可核对的来源行，请先核对来源台账");
            }
            // 整单释放也按真实来源行逐笔落账，后续按行释放才能得到相同净额。
            boolean firstLine = true;
            for (Map.Entry<Long, BigDecimal> balance : currentByLine.entrySet()) {
                if (sourceDocLineId != null && !sourceDocLineId.equals(balance.getKey())) continue;
                BigDecimal amount = balance.getValue();
                if (amount.signum() <= 0) continue;
                if (!firstLine) stock = stockMapper.selectOne(Wrappers.<WmMaterialStock>lambdaQuery()
                    .eq(WmMaterialStock::getStockId, stockId).last("FOR UPDATE"));
                firstLine = false;
                if (stock == null || stockMapper.releaseReserved(stock.getStockId(), amount, stock.getVersion()) != 1) {
                    throw new ServiceException("释放预留失败，请重新核对来源库存");
                }
                released = released.add(amount);
                recordStockTx(stock, amount, TX_TYPE_UNRESERVE, sourceDocType, sourceDocId, balance.getKey());
            }
        }
        log.info("释放预留: source={}/{}, 释放量={}", sourceDocType, sourceDocId, released);
        return released;
    }

    /**
     * 校验库存预留
     *
     * @param materialId 物料 ID
     * @param warehouseId 仓库 ID
     * @return 结果
     */
    @Override
    public BigDecimal checkAvailable(Long materialId, Long warehouseId) {
        LambdaQueryWrapper<WmMaterialStock> wrapper =
                Wrappers.<WmMaterialStock>lambdaQuery()
                        .eq(WmMaterialStock::getMaterialId, materialId)
                        .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
                        .eq(WmMaterialStock::getFrozenFlag, 0L)
                        .apply("(expire_date IS NULL OR expire_date >= CURDATE())");
        List<WmMaterialStock> stocks = stockMapper.selectList(wrapper);
        return stocks.stream()
                .map(WmMaterialStock::getAvailableQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 记录库存事务流水
     */
    private void recordStockTx(
            WmMaterialStock stock,
            BigDecimal qty,
            String txType,
            String sourceDocType,
            Long sourceDocId,
            Long sourceDocLineId) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        // 流水身份完整继承真实库存，不从请求拼接仓库或臆造批次、单位。
        tx.setTenantId(stock.getTenantId());
        tx.setMaterialStockId(stock.getStockId());
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setSnId(stock.getSnId());
        tx.setSnCode(stock.getSnCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setAreaId(stock.getAreaId());
        tx.setAreaCode(stock.getAreaCode());
        tx.setAreaName(stock.getAreaName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setRecptDate(stock.getRecptDate());
        tx.setTrackingVersion(
                stock.getTrackingVersion() != null && stock.getTrackingVersion() >= 2 ? 2L : 1L);
        tx.setTxType(txType);
        tx.setTxFlag(0); // 预留/释放不改变在库数量, flag=0
        tx.setTxQuantity(qty);
        tx.setBeforeQuantity(stock.getStockQuantity());
        tx.setAfterQuantity(stock.getStockQuantity()); // 在库数量不变
        tx.setSourceDocType(sourceDocType);
        tx.setSourceDocId(sourceDocId);
        tx.setSourceDocLineId(sourceDocLineId);
        tx.setTxDate(new Date());
        if (txMapper.insert(tx) != 1) {
            throw new ServiceException("库存预留流水写入失败，预留或释放操作已回滚");
        }
    }
}
