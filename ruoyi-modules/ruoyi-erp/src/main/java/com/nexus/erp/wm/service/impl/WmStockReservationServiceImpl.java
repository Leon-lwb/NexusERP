package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 流水数据访问映射器（Mapper） */
    private final WmMaterialStockTxMapper txMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reserve(Long materialId, Long warehouseId, BigDecimal reserveQty,
                              String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        if (reserveQty == null || reserveQty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // 查询可用库存批次 (FIFO: 先到期先出)
        LambdaQueryWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
            .eq(WmMaterialStock::getFrozenFlag, 0L)
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
                recordStockTx(stock, toReserve, "RESERVE", sourceDocType, sourceDocId, sourceDocLineId);
                log.info("预留库存: stockId={}, qty={}, source={}/{}", stock.getStockId(), toReserve, sourceDocType, sourceDocId);
            }
        }
        if (reserved.compareTo(reserveQty) < 0) {
            // 预留不足显式抛异常，禁止静默部分预留（调用方无法感知会导致销售承诺落空）
            throw new com.nexus.common.core.exception.ServiceException(
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal unreserve(String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        // 查询该来源单据的所有预留流水
        LambdaQueryWrapper<WmMaterialStockTx> wrapper = Wrappers.<WmMaterialStockTx>lambdaQuery()
            .eq(WmMaterialStockTx::getTxType, "RESERVE")
            .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
            .eq(WmMaterialStockTx::getSourceDocId, sourceDocId)
            .eq(sourceDocLineId != null, WmMaterialStockTx::getSourceDocLineId, sourceDocLineId);
        List<WmMaterialStockTx> reserveTxs = txMapper.selectList(wrapper);
        BigDecimal released = BigDecimal.ZERO;
        for (WmMaterialStockTx tx : reserveTxs) {
            WmMaterialStock stock = stockMapper.selectById(tx.getMaterialStockId());
            if (stock == null) {
                continue;
            }
            // 版本冲突/预留不足时重读最新版本重试（最多3次），避免并发下释放被静默跳过导致预留永久卡死
            int rows = 0;
            for (int attempt = 0; attempt < 3 && rows == 0; attempt++) {
                if (attempt > 0) {
                    stock = stockMapper.selectById(tx.getMaterialStockId());
                    if (stock == null) {
                        break;
                    }
                }
                rows = stockMapper.releaseReserved(stock.getStockId(), tx.getTxQuantity(), stock.getVersion());
            }
            if (rows == 0) {
                // 重试仍失败：库存行被并发操作修改或预留不足，抛异常回滚，禁止静默跳过
                throw new com.nexus.common.core.exception.ServiceException(
                    "释放预留失败(并发冲突或预留不足): stockId=" + tx.getMaterialStockId() + ", qty=" + tx.getTxQuantity());
            }
            released = released.add(tx.getTxQuantity());
            recordStockTx(stock, tx.getTxQuantity(), "UNRESERVE", sourceDocType, sourceDocId, sourceDocLineId);
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
        LambdaQueryWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
            .eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stocks = stockMapper.selectList(wrapper);
        return stocks.stream()
            .map(WmMaterialStock::getAvailableQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 记录库存事务流水
     */
    private void recordStockTx(WmMaterialStock stock, BigDecimal qty, String txType,
                               String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setMaterialStockId(stock.getStockId());
        tx.setMaterialId(stock.getMaterialId());
        tx.setTxType(txType);
        tx.setTxFlag(0); // 预留/释放不改变在库数量, flag=0
        tx.setTxQuantity(qty);
        tx.setBeforeQuantity(stock.getStockQuantity());
        tx.setAfterQuantity(stock.getStockQuantity()); // 在库数量不变
        tx.setSourceDocType(sourceDocType);
        tx.setSourceDocId(sourceDocId);
        tx.setSourceDocLineId(sourceDocLineId);
        tx.setTxDate(new Date());
        txMapper.insert(tx);
    }
}
