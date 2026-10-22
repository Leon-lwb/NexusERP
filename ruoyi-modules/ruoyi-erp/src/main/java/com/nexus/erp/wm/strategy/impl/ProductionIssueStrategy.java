package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.service.WmSerialInventoryService;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 生产领料策略
 * <p>
 * 处理生产领料出库业务，根据生产领料单行数据扣减库存并生成库存流水记录。
 * 支持按先进先出（FIFO）规则匹配库存，并处理多批次库存拆分扣减。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ProductionIssueStrategy extends BaseStockOperationStrategy {
    @Autowired
    private WmSerialInventoryService serialInventory;

    /**
     * 事务类型：生产领料
     */
    private static final String TX_TYPE_PRODUCTION_ISSUE = "PRODUCTION_ISSUE";

    /**
     * 来源单据类型：生产领料单
     */
    private static final String SOURCE_DOC_TYPE_PRODUCTION_ISSUE = "WM_PRODUCTION_ISSUE";

    /**
     * 事务标志：出库（负数）
     */
    private static final int TX_FLAG_OUT = -1;

    /**
     * 金额精度：与库存 total_cost、流水 tx_amount 的 decimal(14,4) 保持一致。
     */
    private static final int TX_AMOUNT_SCALE = 4;

    /**
     * 库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 库存计价服务(F3-a:出库按移动平均结转成本)
     */
    private final IInventoryValuationService valuationService;

    /**
     * 批次累计发料数量的原子更新映射器。
     */
    private final WmBatchMapper batchMapper;

    /**
     * 构造生产领料策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     * @param valuationService 库存计价服务
     * @param batchMapper 批次Mapper
     */
    public ProductionIssueStrategy(
            WmMaterialStockTxMapper stockTxMapper,
            WmMaterialStockMapper stockMapper,
            IInventoryValuationService valuationService,
            WmBatchMapper batchMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
        this.batchMapper = batchMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 生产领料操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.PRODUCTION_ISSUE;
    }

    /**
     * 执行生产领料出库操作
     *
     * @param args 操作参数，args[0]为领料单行对象（WmProductionIssueLine），args[1]为操作时间（Date）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmProductionIssueLine line = (WmProductionIssueLine) args[0];
        Date now = (Date) args[1];
        if (serialInventory != null
                && serialInventory.post(
                        WmSerialPostingRequest.out(
                                "WM_PRODUCTION_ISSUE",
                                line.getIssueId(),
                                line.getLineId(),
                                "PRODUCTION_ISSUE",
                                line.getMaterialId(),
                                line.getQuantity(),
                                line.getWarehouseId(),
                                line.getLocationId(),
                                line.getBatchId()))) {
            return;
        }

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("生产领料数量必须大于0");
        }

        if (line.getWarehouseId() == null) {
            throw new ServiceException("生产领料必须选择仓库");
        }

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getWarehouseId, line.getWarehouseId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        /**
         * 仅扣减未冻结且未预留库存：frozen_flag 0=未冻结/可用，1=已冻结；排除已预留量防止绕穿销售预留承诺
         */
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        /**
         * 库存未启用逻辑删除，实际扣料与候选必须显式排除已删及未知数量。
         */
        lqw.eq(WmMaterialStock::getDelFlag, "0");
        lqw.isNotNull(WmMaterialStock::getStockQuantity);
        lqw.isNotNull(WmMaterialStock::getReservedQuantity);
        lqw.apply("(stock_quantity - IFNULL(reserved_quantity, 0)) > 0");
        /**
         * ERP-WM-06: 效期拦截 —— 已过期批次不得参与生产领料
         */
        lqw.apply(NOT_EXPIRED_SQL);
        /**
         * MySQL 升序会把 NULL 排在前面；先领近期到期批次，未设置效期的库存最后。
         */
        lqw.last("ORDER BY expire_date IS NULL ASC, expire_date ASC, recpt_date ASC, stock_id ASC");

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }

        BigDecimal remaining = line.getQuantity();
        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            /**
             * ERP-WM-02: 扣减上限按可用量(在库-已预留)而非账面量, 否则可扣穿其他单据的预留
             */
            BigDecimal available = availableOf(stock);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal deductQty = available.min(remaining);
            deductAndLog(stock, deductQty, line.getIssueId(), line.getLineId(), now);
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
        }
        if (serialInventory != null) {
            serialInventory.bindTrackedLegacyIssue(
                    WmSerialPostingRequest.out(
                            "WM_PRODUCTION_ISSUE",
                            line.getIssueId(),
                            line.getLineId(),
                            "PRODUCTION_ISSUE",
                            line.getMaterialId(),
                            line.getQuantity(),
                            line.getWarehouseId(),
                            line.getLocationId(),
                            line.getBatchId()));
        }
    }

    /**
     * 按已分配库存明细扣减（供倒冲领料使用）
     * <p>
     * 拣货策略已选定具体库存与扣减量，此处逐条重新读取最新库存（取最新乐观锁版本）后扣减并写流水。
     *
     * @param issueId 领料单头ID
     * @param lineId  领料单行ID
     * @param stocks  拣货分配明细
     * @param now     操作时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeAllocated(Long issueId, Long lineId, List<AllocatedStock> stocks, Date now) {
        if (serialInventory != null && stocks != null && !stocks.isEmpty()) {
            WmMaterialStock selected = stockMapper.selectById(stocks.get(0).getStockId());
            if (selected == null) {
                throw new ServiceException("倒冲来源库存不存在");
            }
            BigDecimal total =
                    stocks.stream()
                            .map(AllocatedStock::getDeductQty)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (serialInventory.post(
                    WmSerialPostingRequest.out(
                            "WM_PRODUCTION_ISSUE",
                            issueId,
                            lineId,
                            "PRODUCTION_ISSUE",
                            selected.getMaterialId(),
                            total,
                            selected.getWarehouseId(),
                            null,
                            null))) {
                return;
            }
        }
        if (stocks == null || stocks.isEmpty()) {
            throw new RuntimeException("生产领料分配明细为空");
        }
        for (AllocatedStock a : stocks) {
            WmMaterialStock stock = stockMapper.selectById(a.getStockId());
            if (stock == null) {
                throw new ServiceException("库存记录不存在: " + a.getStockId());
            }
            BigDecimal stockQty =
                    stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            /**
             * ERP-WM-02: 倒冲路径同样按可用量校验, 不得越过他人预留
             */
            BigDecimal available = availableOf(stock);
            if (available.compareTo(a.getDeductQty()) < 0) {
                throw new ServiceException(
                        "物料【"
                                + stock.getMaterialName()
                                + "】可用库存不足(现有"
                                + stockQty.stripTrailingZeros().toPlainString()
                                + ", 可用"
                                + available.stripTrailingZeros().toPlainString()
                                + "), 无法扣减");
            }
            deductAndLog(stock, a.getDeductQty(), issueId, lineId, now);
        }
    }

    /**
     * 扣减单条库存并写入出库流水
     *
     * @param stock     库存记录
     * @param deductQty 本次扣减量
     * @param issueId   领料单头ID（来源单据ID）
     * @param lineId    领料单行ID（来源单据行ID）
     * @param now       操作时间
     */
    private void deductAndLog(
            WmMaterialStock stock, BigDecimal deductQty, Long issueId, Long lineId, Date now) {
        BigDecimal beforeQuantity = stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.subtract(deductQty);
        /**
         * ERP-WM-02/WM-09: 原子扣减按可用量守卫(且 frozen_flag=0), 不再按账面量扣减
         */
        int affected =
                stockMapper.deductStockAvailable(stock.getStockId(), deductQty, stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("可用库存不足或已被并发占用，请重试: 物料【" + stock.getMaterialName() + "】");
        }
        /**
         * F3-a:出库按移动平均成本结转(扣减 totalCost,返回单位成本)
         */
        BigDecimal outUnitCost = valuationService.issueStockCost(stock.getStockId(), deductQty);
        // 使用实际被扣库存的批次身份；历史无批次库存保留原有兼容行为。
        if (stock.getBatchId() != null
                && batchMapper.increaseIssuedQty(
                                stock.getBatchId(),
                                stock.getMaterialId(),
                                stock.getTenantId(),
                                deductQty)
                        != 1) {
            throw new ServiceException("批次发料累计失败或超过已收数量: 批次【" + stock.getBatchId() + "】");
        }
        WmMaterialStockTx tx =
                buildBaseTxFromStock(
                        stock,
                        TX_TYPE_PRODUCTION_ISSUE,
                        TX_FLAG_OUT,
                        deductQty,
                        beforeQuantity,
                        afterQuantity,
                        now);
        tx.setUnitCost(outUnitCost);
        // 出库不改变移动平均价；按实际扣量乘该价格结转，不能从扣后总成本反推。
        tx.setTxAmount(
                deductQty.multiply(outUnitCost).setScale(TX_AMOUNT_SCALE, RoundingMode.HALF_UP));
        tx.setSourceDocType(SOURCE_DOC_TYPE_PRODUCTION_ISSUE);
        tx.setSourceDocId(issueId);
        tx.setSourceDocLineId(lineId);
        tx.setRemark("生产领料");
        if (stockTxMapper.insert(tx) != 1) {
            throw new ServiceException("生产领料流水写入失败: 物料【" + stock.getMaterialName() + "】");
        }
    }
}
