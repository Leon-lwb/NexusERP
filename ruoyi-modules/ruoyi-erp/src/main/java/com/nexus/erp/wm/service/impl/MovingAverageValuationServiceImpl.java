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

@RequiredArgsConstructor
@DS("erp")
@Service
public class MovingAverageValuationServiceImpl implements IInventoryValuationService {

    private final WmMaterialStockMapper baseMapper;

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
