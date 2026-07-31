package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
/**
 * 移动平均计价服务实现
 * <p>
 * 实现移动平均计价服务接口，处理移动平均计价的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@RequiredArgsConstructor
@DS("erp")
@Service
public class MovingAverageValuationServiceImpl implements IInventoryValuationService {

    /** 基础数据访问映射器（Mapper） */
    private final WmMaterialStockMapper baseMapper;

    /**
     * 计算移动平均计价
     *
     * @param stockId 库存 ID
     * @param inQty 入库数量
     * @param inUnitCost 入库单位成本
     * @return 结果
     */
    @Override
    public BigDecimal calculateMovingAverage(Long stockId, BigDecimal inQty, BigDecimal inUnitCost) {
        if (stockId == null) {
            throw new ServiceException("库存ID不能为空");
        }
        if (inQty == null || inQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }
        if (inUnitCost == null || inUnitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("入库单位成本不能为负数");
        }
        WmMaterialStock stock = baseMapper.selectById(stockId);
        if (stock == null) {
            throw new ServiceException("库存记录不存在(ID={})", stockId);
        }
        BigDecimal oldQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal oldCost = stock.getTotalCost() != null ? stock.getTotalCost() : BigDecimal.ZERO;
        BigDecimal inAmount = inQty.multiply(inUnitCost);
        BigDecimal newTotalAmount = oldCost.add(inAmount);
        BigDecimal newTotalQty = oldQty.add(inQty);
        if (newTotalQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return newTotalAmount.divide(newTotalQty, 6, RoundingMode.HALF_UP);
    }

    /**
     * 修改移动平均计价
     *
     * @param stockId 库存 ID
     * @param inQty 入库数量
     * @param inUnitCost 入库单位成本
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStockCost(Long stockId, BigDecimal inQty, BigDecimal inUnitCost) {
        if (stockId == null) {
            throw new ServiceException("库存ID不能为空");
        }
        if (inQty == null || inQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }
        if (inUnitCost == null || inUnitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("入库单位成本不能为负数");
        }
        WmMaterialStock stock = baseMapper.selectById(stockId);
        if (stock == null) {
            throw new ServiceException("库存记录不存在(ID={})", stockId);
        }
        // 调用方已先完成数量累加(executor/策略均为"先加数量后计价"),
        // 故分母用当前数量(已含本次入库),分子 = 既有总成本 + 本次入库金额
        BigDecimal oldQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal oldCost = stock.getTotalCost() != null ? stock.getTotalCost() : BigDecimal.ZERO;
        BigDecimal inAmount = inQty.multiply(inUnitCost);
        BigDecimal newTotalAmount = oldCost.add(inAmount);
        BigDecimal newTotalQty = oldQty;
        BigDecimal newUnitCost = BigDecimal.ZERO;
        if (newTotalQty.compareTo(BigDecimal.ZERO) > 0) {
            newUnitCost = newTotalAmount.divide(newTotalQty, 6, RoundingMode.HALF_UP);
        }
        stock.setUnitCost(newUnitCost);
        stock.setTotalCost(newTotalAmount);
        baseMapper.updateById(stock);
    }

    /**
     * 出库成本结转:按当前移动平均单位成本扣减 totalCost。
     * <p>移动平均下出库不改变 unitCost,仅按出库数量×unitCost 扣减 totalCost;
     * 出库数量不得超过可用库存。</p>
     *
     * @param stockId 库存 ID
     * @param outQty  出库数量
     * @return 出库单位成本(当前 unitCost)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal issueStockCost(Long stockId, BigDecimal outQty) {
        if (stockId == null) {
            throw new ServiceException("库存ID不能为空");
        }
        if (outQty == null || outQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("出库数量必须大于0");
        }
        WmMaterialStock stock = baseMapper.selectById(stockId);
        if (stock == null) {
            throw new ServiceException("库存记录不存在(ID={})", stockId);
        }
        BigDecimal unitCost = stock.getUnitCost() != null ? stock.getUnitCost() : BigDecimal.ZERO;
        BigDecimal qty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        if (outQty.compareTo(qty) > 0) {
            throw new ServiceException("出库数量超过库存数量");
        }
        BigDecimal costAmount = unitCost.multiply(outQty);
        BigDecimal newTotalCost = (stock.getTotalCost() != null ? stock.getTotalCost() : BigDecimal.ZERO).subtract(costAmount);
        if (newTotalCost.signum() < 0) {
            newTotalCost = BigDecimal.ZERO;
        }
        stock.setTotalCost(newTotalCost);
        baseMapper.updateById(stock);
        return unitCost;
    }
}
