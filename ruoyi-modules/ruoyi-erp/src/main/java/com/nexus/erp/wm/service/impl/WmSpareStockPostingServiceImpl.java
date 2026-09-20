package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import com.nexus.erp.wm.service.mq.MesSpareErpMessage;
import com.nexus.erp.wm.service.mq.WmSpareStockDedupGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MES 备件 → ERP 仓储过账服务实现（MES-EQ-04）
 * <p>
 * <b>原子性</b>：数量增减全部走 {@code WmMaterialStockMapper} 的原子 SQL
 * （{@link WmMaterialStockMapper#deductStockAvailable} 可用量守卫 / {@link WmMaterialStockMapper#addStock}），
 * 绝不 Java 先算后写主表；版本冲突时重读一次重试。
 * <p>
 * <b>口径</b>：可用量 = {@code stock_quantity − reserved_quantity}，且仅统计 {@code frozen_flag = 0} 的行
 * （与 ERP-WM-02 / ERP-WM-09 统一口径）。
 * <p>
 * <b>幂等</b>：Redis 原子占位（{@link WmSpareStockDedupGuard}）+ 流水查重；事务失败时释放占位，
 * 避免"标记成功但未过账"。
 * <p>
 * <b>失败可见</b>：可用量不足、数量非法、仓库缺失均抛 {@code ServiceException}，由消费端记录 ERROR
 * 并在 MES 侧过账同步台账保持 PENDING/FAILED，可人工重投，绝不静默部分过账。
 *
 * @author Nexus
 */
@Slf4j
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmSpareStockPostingServiceImpl implements IWmSpareStockPostingService {

    /** 入库流水类型 */
    private static final String TX_TYPE_IN = "MES_SPARE_IN";
    /** 出库流水类型 */
    private static final String TX_TYPE_OUT = "MES_SPARE_OUT";
    /** 原子更新重试次数 */
    private static final int MAX_RETRY = 3;

    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final WmSpareStockDedupGuard dedupGuard;

    /**
     * 查询 ERP 可用量（在库 − 已预留，且 frozen_flag = 0）
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID(可空)
     * @return 可用量合计
     */
    @Override
    public BigDecimal availableOf(Long materialId, Long warehouseId) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        List<WmMaterialStock> stocks = stockMapper.selectList(buildAvailableQuery(materialId, warehouseId));
        BigDecimal total = BigDecimal.ZERO;
        for (WmMaterialStock stock : stocks) {
            total = total.add(stock.getAvailableQuantity());
        }
        return total;
    }

    /**
     * 过账一笔 MES 备件出入库
     *
     * @param message 过账消息
     * @return 过账结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmSpareStockPostingResult postSpareStock(MesSpareErpMessage message) {
        if (message == null || message.getBizKey() == null || message.getMaterialId() == null) {
            throw new ServiceException("备件过账消息缺少 bizKey/materialId, 拒绝过账");
        }
        BigDecimal qty = message.getQuantity();
        if (qty == null || qty.signum() <= 0) {
            throw new ServiceException("备件过账数量必须大于0: bizKey=" + message.getBizKey());
        }
        String bizKey = message.getBizKey();
        // 幂等①: Redis 原子占位
        if (!dedupGuard.tryMark(bizKey)) {
            log.info("备件过账消息重复, 已跳过: bizKey={}", bizKey);
            return new WmSpareStockPostingResult(true, availableOf(message.getMaterialId(), message.getWarehouseId()));
        }
        try {
            // 幂等②: 持久层流水查重(Redis 过期后的迟到重投)
            Long posted = stockTxMapper.selectCount(Wrappers.<WmMaterialStockTx>lambdaQuery()
                .eq(WmMaterialStockTx::getSourceDocType, SOURCE_DOC_TYPE)
                .eq(WmMaterialStockTx::getSourceDocCode, bizKey));
            if (posted != null && posted > 0) {
                log.info("备件过账流水已存在, 已跳过: bizKey={}", bizKey);
                return new WmSpareStockPostingResult(true, availableOf(message.getMaterialId(), message.getWarehouseId()));
            }
            boolean inbound = isInbound(message.getTransactionType());
            if (inbound) {
                applyInbound(message, qty);
            } else {
                applyOutbound(message, qty);
            }
            BigDecimal available = availableOf(message.getMaterialId(), message.getWarehouseId());
            log.info("备件过账成功: bizKey={}, type={}, qty={}, 过账后ERP可用量={}",
                bizKey, message.getTransactionType(), qty, available);
            return new WmSpareStockPostingResult(false, available);
        } catch (RuntimeException e) {
            // 事务将回滚: 释放幂等占位, 否则重投会被误判为"已过账"而永久丢失
            dedupGuard.release(bizKey);
            throw e;
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 判断是否入库方向（IN / RETURN）
     *
     * @param transactionType 变动类型
     * @return 是否入库
     */
    private boolean isInbound(String transactionType) {
        return "IN".equals(transactionType) || "RETURN".equals(transactionType);
    }

    /**
     * 构造可用量选行条件
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID(可空)
     * @return 查询条件
     */
    private LambdaQueryWrapper<WmMaterialStock> buildAvailableQuery(Long materialId, Long warehouseId) {
        return Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
            .eq(WmMaterialStock::getFrozenFlag, 0L);
    }

    /**
     * 入库过账：优先累加到同维度既有行（含冻结行，避免制造重复维度行），无行则新建（version=0）
     *
     * @param message 过账消息
     * @param qty     数量
     */
    private void applyInbound(MesSpareErpMessage message, BigDecimal qty) {
        List<WmMaterialStock> stocks = stockMapper.selectList(Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, message.getMaterialId())
            .eq(message.getWarehouseId() != null, WmMaterialStock::getWarehouseId, message.getWarehouseId())
            .orderByAsc(WmMaterialStock::getStockId));
        if (stocks == null || stocks.isEmpty()) {
            WmMaterialStock created = new WmMaterialStock();
            created.setMaterialId(message.getMaterialId());
            created.setMaterialCode(message.getSpareCode());
            created.setMaterialName(message.getSpareName());
            created.setWarehouseId(message.getWarehouseId());
            created.setStockQuantity(qty);
            created.setReservedQuantity(BigDecimal.ZERO);
            created.setFrozenFlag(0L);
            created.setUnitCost(BigDecimal.ZERO);
            created.setTotalCost(BigDecimal.ZERO);
            created.setRecptDate(new Date());
            created.setDelFlag("0");
            created.setRemark("MES备件首次入库创建 (bizKey=" + message.getBizKey() + ")");
            // 新建库存行必须显式 setVersion(0), 否则乐观锁失效
            created.setVersion(0);
            stockMapper.insert(created);
            writeTx(message, created, qty, true, BigDecimal.ZERO, qty);
            return;
        }
        WmMaterialStock target = stocks.get(0);
        for (int i = 0; i < MAX_RETRY; i++) {
            WmMaterialStock fresh = stockMapper.selectById(target.getStockId());
            if (fresh == null) {
                break;
            }
            BigDecimal before = fresh.getStockQuantity() == null ? BigDecimal.ZERO : fresh.getStockQuantity();
            int affected = stockMapper.addStock(fresh.getStockId(), qty, fresh.getVersion());
            if (affected > 0) {
                writeTx(message, fresh, qty, true, before, before.add(qty));
                return;
            }
        }
        throw new ServiceException("ERP 备件入库失败(库存行并发冲突): stockId=" + target.getStockId()
            + ", bizKey=" + message.getBizKey());
    }

    /**
     * 出库过账：按可用量跨库存行扣减（{@code deductStockAvailable} 守卫，不扣穿他人预留）
     *
     * @param message 过账消息
     * @param qty     数量
     */
    private void applyOutbound(MesSpareErpMessage message, BigDecimal qty) {
        List<WmMaterialStock> stocks = stockMapper.selectList(Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, message.getMaterialId())
            .eq(message.getWarehouseId() != null, WmMaterialStock::getWarehouseId, message.getWarehouseId())
            .eq(WmMaterialStock::getFrozenFlag, 0L)
            .apply("(stock_quantity - IFNULL(reserved_quantity, 0)) > 0")
            .orderByAsc(WmMaterialStock::getRecptDate)
            .orderByAsc(WmMaterialStock::getStockId));
        if (stocks == null || stocks.isEmpty()) {
            throw new ServiceException("ERP 库存无可用量, 备件领用被拒绝: materialId=" + message.getMaterialId()
                + ", warehouseId=" + message.getWarehouseId() + ", 需扣减=" + qty + ", bizKey=" + message.getBizKey());
        }
        BigDecimal remaining = qty;
        // 先收集拟写流水, 待全量覆盖后再落库: 任一缺口即整单回滚, 不留"半成品"流水
        record PendingTx(WmMaterialStock stock, BigDecimal qty, BigDecimal before, BigDecimal after) {
        }
        List<PendingTx> pending = new ArrayList<>();
        for (WmMaterialStock row : stocks) {
            if (remaining.signum() <= 0) {
                break;
            }
            WmMaterialStock fresh = stockMapper.selectById(row.getStockId());
            BigDecimal deduct = BigDecimal.ZERO;
            BigDecimal before = BigDecimal.ZERO;
            int affected = 0;
            for (int i = 0; i < MAX_RETRY && fresh != null; i++) {
                BigDecimal available = fresh.getAvailableQuantity();
                if (available.signum() <= 0) {
                    break;
                }
                deduct = available.min(remaining);
                before = fresh.getStockQuantity() == null ? BigDecimal.ZERO : fresh.getStockQuantity();
                affected = stockMapper.deductStockAvailable(fresh.getStockId(), deduct, fresh.getVersion());
                if (affected > 0) {
                    break;
                }
                fresh = stockMapper.selectById(row.getStockId());
            }
            if (affected == 0 || deduct.signum() <= 0) {
                continue;
            }
            pending.add(new PendingTx(fresh, deduct, before, before.subtract(deduct)));
            remaining = remaining.subtract(deduct);
        }
        if (remaining.signum() > 0) {
            throw new ServiceException("ERP 备件可用库存不足, 已整单回滚: materialId=" + message.getMaterialId()
                + ", warehouseId=" + message.getWarehouseId() + ", 缺口=" + remaining
                + ", bizKey=" + message.getBizKey());
        }
        for (PendingTx tx : pending) {
            writeTx(message, tx.stock(), tx.qty(), false, tx.before(), tx.after());
        }
    }

    /**
     * 写库存事务流水（MES_SPARE_IN / MES_SPARE_OUT）
     *
     * @param message 过账消息
     * @param stock   库存行（已原子更新后的读值）
     * @param qty     数量
     * @param inbound 是否入库
     * @param before  变动前数量
     * @param after   变动后数量
     */
    private void writeTx(MesSpareErpMessage message, WmMaterialStock stock, BigDecimal qty,
                         boolean inbound, BigDecimal before, BigDecimal after) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType(inbound ? TX_TYPE_IN : TX_TYPE_OUT);
        tx.setTxFlag(inbound ? 1 : 0);
        tx.setMaterialId(message.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode() != null ? stock.getMaterialCode() : message.getSpareCode());
        tx.setMaterialName(stock.getMaterialName() != null ? stock.getMaterialName() : message.getSpareName());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setSourceDocType(SOURCE_DOC_TYPE);
        tx.setSourceDocId(message.getSpareId());
        tx.setSourceDocCode(message.getBizKey());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxQuantity(qty);
        tx.setUnitCost(stock.getUnitCost());
        tx.setBeforeQuantity(before);
        tx.setAfterQuantity(after);
        tx.setTxDate(new Date());
        tx.setPosted(0);
        tx.setDelFlag("0");
        tx.setRemark("MES备件" + (inbound ? "入库" : "出库")
            + " refType=" + message.getRefType() + " refId=" + message.getRefId());
        stockTxMapper.insert(tx);
    }
}
