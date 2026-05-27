package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.service.IPickingStrategyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service("fifoPickingStrategy")
public class FifoPickingStrategy implements IPickingStrategyService {

    private final WmMaterialStockMapper materialStockMapper;

    @Override
    public List<WmMaterialStock> selectStocks(Long materialId, Long warehouseId, BigDecimal quantity) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        lqw.orderByAsc(WmMaterialStock::getRecptDate);
        List<WmMaterialStock> stocks = materialStockMapper.selectList(lqw);
        return allocateStocks(stocks, quantity);
    }

    private List<WmMaterialStock> allocateStocks(List<WmMaterialStock> stocks, BigDecimal quantity) {
        List<WmMaterialStock> result = new ArrayList<>();
        BigDecimal remaining = quantity;
        for (WmMaterialStock stock : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = stock.getStockQuantity();
            BigDecimal allocated = available.min(remaining);
            stock.setStockQuantity(allocated);
            result.add(stock);
            remaining = remaining.subtract(allocated);
        }
        return result;
    }
}
