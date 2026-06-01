package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.service.IPickingStrategyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 先进先出（FIFO）拣货策略实现
 * <p>
 * 按入库日期从早到晚排序，优先拣选最早入库的库存。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@Service("fifoPickingStrategy")
public class FifoPickingStrategy implements IPickingStrategyService {

    private final WmMaterialStockMapper materialStockMapper;

    /**
     * 根据FIFO策略查询并分配库存
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID
     * @param quantity    需求数量
     * @return 分配到的库存列表
     */
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
