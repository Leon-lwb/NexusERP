package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSubcontractIssueLine;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 委外发料策略实现 (PCBA 闭环重写)
 * <p>
 * 按发料单行 + 批次/储位分配明细扣减库存并写流水(含移动平均成本结转),
 * 来源单据为 wm_subcontract_issue(_line); 分配明细为空时按
 * FEFO(先过期) -> FIFO(先入库) 自动选批次, 与生产领料同款可用量过滤
 * (排除冻结与已预留量)。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
public class SubcontractIssueStrategy extends BaseStockOperationStrategy {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    /**
     * 事务类型：委外发料
     */
    private static final String TX_TYPE_SUBCONTRACT_ISSUE = "SUBCONTRACT_ISSUE";

    /**
     * 来源单据类型：委外发料单
     */
    private static final String SOURCE_DOC_TYPE = "WM_SUBCONTRACT_ISSUE";

    /**
     * 事务标志：出库（负数）
     */
    private static final int TX_FLAG_OUT = -1;

    /**
     * 库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 库存计价服务(出库按移动平均结转成本)
     */
    private final IInventoryValuationService valuationService;

    /**
     * 构造委外发料策略实例
     *
     * @param stockTxMapper     库存事务Mapper
     * @param stockMapper       库存Mapper
     * @param valuationService  库存计价服务
     */
    public SubcontractIssueStrategy(
            WmMaterialStockTxMapper stockTxMapper,
            WmMaterialStockMapper stockMapper,
            IInventoryValuationService valuationService) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
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
     * 执行委外发料出库
     *
     * @param args args[0]为发料单行（WmSubcontractIssueLine），args[1]为批次/储位分配明细
     *             （List&lt;AllocatedStock&gt;，可为空=自动FEFO/FIFO），args[2]为操作时间（Date）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmSubcontractIssueLine line = (WmSubcontractIssueLine) args[0];
        @SuppressWarnings("unchecked")
        List<AllocatedStock> allocations = (List<AllocatedStock>) args[1];
        Date now = (Date) args[2];
        if (serialInventory != null
                && serialInventory.post(
                        com.nexus.erp.wm.domain.WmSerialPostingRequest.out(
                                "WM_SUBCONTRACT_ISSUE",
                                line.getIssueId(),
                                line.getLineId(),
                                "SUBCONTRACT_ISSUE",
                                line.getMaterialId(),
                                line.getIssueQty(),
                                null,
                                null,
                                null))) {
            return;
        }

        if (line.getIssueQty() == null || line.getIssueQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("委外发料数量必须大于0: " + line.getMaterialName());
        }
        List<AllocatedStock> allocs =
                (allocations == null || allocations.isEmpty()) ? autoAllocate(line) : allocations;
        BigDecimal total =
                allocs.stream()
                        .map(AllocatedStock::getDeductQty)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(line.getIssueQty()) != 0) {
            throw new ServiceException(
                    "物料【"
                            + line.getMaterialName()
                            + "】分配明细合计["
                            + total.stripTrailingZeros().toPlainString()
                            + "]与发料量["
                            + line.getIssueQty().stripTrailingZeros().toPlainString()
                            + "]不一致");
        }
        for (AllocatedStock a : allocs) {
            deductAndLog(a, line, now);
        }
    }

    /**
     * 自动选批次分配: FEFO -> FIFO -> 库存ID, 仅扣未冻结且未预留库存
     */
    private List<AllocatedStock> autoAllocate(WmSubcontractIssueLine line) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.apply("(stock_quantity - IFNULL(reserved_quantity, 0)) > 0");
        // ERP-WM-06: 效期拦截 —— 已过期批次不得参与委外发料
        lqw.apply(NOT_EXPIRED_SQL);
        lqw.orderByAsc(
                WmMaterialStock::getExpireDate,
                WmMaterialStock::getRecptDate,
                WmMaterialStock::getStockId);
        List<WmMaterialStock> stocks = stockMapper.selectList(lqw);

        List<AllocatedStock> allocs = new java.util.ArrayList<>();
        BigDecimal remaining = line.getIssueQty();
        for (WmMaterialStock s : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available =
                    s.getStockQuantity() == null
                            ? BigDecimal.ZERO
                            : s.getStockQuantity()
                                    .subtract(
                                            s.getReservedQuantity() == null
                                                    ? BigDecimal.ZERO
                                                    : s.getReservedQuantity());
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal deduct = available.min(remaining);
            AllocatedStock a = new AllocatedStock();
            a.setStockId(s.getStockId());
            a.setMaterialId(s.getMaterialId());
            a.setWarehouseId(s.getWarehouseId());
            a.setLocationId(s.getLocationId());
            a.setBatchId(s.getBatchId());
            a.setBatchCode(s.getBatchCode());
            a.setDeductQty(deduct);
            allocs.add(a);
            remaining = remaining.subtract(deduct);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(
                    "物料【"
                            + line.getMaterialName()
                            + "】可用库存不足, 缺少"
                            + remaining.stripTrailingZeros().toPlainString());
        }
        return allocs;
    }

    /**
     * 扣减单条库存并写入出库流水(乐观锁 + 移动平均成本结转)
     */
    private void deductAndLog(AllocatedStock a, WmSubcontractIssueLine line, Date now) {
        WmMaterialStock stock = stockMapper.selectById(a.getStockId());
        if (stock == null) {
            throw new ServiceException("库存记录不存在: " + a.getStockId());
        }
        BigDecimal beforeQty =
                stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
        // ERP-WM-02: 显式分配明细同样按可用量校验(不得越过他人预留)
        BigDecimal available = availableOf(stock);
        if (available.compareTo(a.getDeductQty()) < 0) {
            throw new ServiceException(
                    "物料【"
                            + stock.getMaterialName()
                            + "】可用库存不足, 现有"
                            + beforeQty.stripTrailingZeros().toPlainString()
                            + ", 可用"
                            + available.stripTrailingZeros().toPlainString()
                            + ", 需要"
                            + a.getDeductQty().stripTrailingZeros().toPlainString());
        }
        // ERP-WM-02/WM-09: 原子扣减按可用量守卫(且 frozen_flag=0)
        int affected =
                stockMapper.deductStockAvailable(
                        stock.getStockId(), a.getDeductQty(), stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("可用库存不足或已被并发占用, 请重试: 物料【" + stock.getMaterialName() + "】");
        }
        BigDecimal outUnitCost =
                valuationService.issueStockCost(stock.getStockId(), a.getDeductQty());
        WmMaterialStockTx tx =
                buildBaseTxFromStock(
                        stock,
                        TX_TYPE_SUBCONTRACT_ISSUE,
                        TX_FLAG_OUT,
                        a.getDeductQty(),
                        beforeQty,
                        beforeQty.subtract(a.getDeductQty()),
                        now);
        tx.setUnitCost(outUnitCost);
        tx.setSourceDocType(SOURCE_DOC_TYPE);
        tx.setSourceDocId(line.getIssueId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark(
                "委外发料出库" + (line.getSubcontractNo() != null ? "-" + line.getSubcontractNo() : ""));
        stockTxMapper.insert(tx);
    }
}
