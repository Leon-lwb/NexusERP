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
        BigDecimal oldQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal oldCost = stock.getTotalCost() != null ? stock.getTotalCost() : BigDecimal.ZERO;
        BigDecimal inAmount = inQty.multiply(inUnitCost);
        BigDecimal newTotalAmount = oldCost.add(inAmount);
        BigDecimal newTotalQty = oldQty.add(inQty);
        BigDecimal newUnitCost = BigDecimal.ZERO;
        if (newTotalQty.compareTo(BigDecimal.ZERO) > 0) {
            newUnitCost = newTotalAmount.divide(newTotalQty, 6, RoundingMode.HALF_UP);
        }
        stock.setUnitCost(newUnitCost);
        stock.setTotalCost(newTotalAmount);
        baseMapper.updateById(stock);
    }
}
