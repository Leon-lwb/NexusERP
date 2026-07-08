package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmPickingRule;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.vo.AllocatedStock;
import com.nexus.erp.wm.domain.vo.PickingAllocation;
import com.nexus.erp.wm.mapper.WmPickingRuleMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IPickingResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 拣货规则解析编排层实现
 * <p>
 * 全局匹配拣货规则得目标仓库与策略类型，调用对应拣货策略选库存，校验分配总量。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PickingResolutionServiceImpl implements IPickingResolutionService {

    private final WmPickingRuleMapper pickingRuleMapper;
    private final WmWarehouseMapper wmWarehouseMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final PickingStrategyFactory pickingStrategyFactory;

    /**
     * 规则驱动解析拣货目标仓库与选中库存
     *
     * @param materialId 物料ID
     * @param quantity   需求数量
     * @return 目标仓库 + 库存分配明细
     */
    @Override
    public PickingAllocation resolvePicking(Long materialId, BigDecimal quantity) {
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new ServiceException("物料不存在");
        }

        WmPickingRule rule = findMatchingRuleGlobal(material.getCategoryId());
        if (rule == null) {
            throw new ServiceException("物料[" + material.getMaterialCode() + "]未配置拣货规则");
        }
        Long warehouseId = rule.getWarehouseId();
        if (warehouseId == null) {
            throw new ServiceException("拣货规则[" + rule.getRuleCode() + "]未绑定目标仓库");
        }
        WmWarehouse warehouse = wmWarehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new ServiceException("拣货规则绑定的仓库不存在: " + warehouseId);
        }

        List<WmMaterialStock> stocks = pickingStrategyFactory
            .getStrategy(rule.getStrategyType())
            .selectStocks(materialId, warehouseId, quantity);

        List<AllocatedStock> allocated = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (WmMaterialStock s : stocks) {
            // allocateStocks 已将 stockQuantity 改写为本次分配量
            BigDecimal deductQty = s.getStockQuantity();
            if (deductQty == null || deductQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            AllocatedStock a = new AllocatedStock();
            a.setStockId(s.getStockId());
            a.setMaterialId(s.getMaterialId());
            a.setWarehouseId(s.getWarehouseId());
            a.setLocationId(s.getLocationId());
            a.setBatchId(s.getBatchId());
            a.setBatchCode(s.getBatchCode());
            a.setDeductQty(deductQty);
            allocated.add(a);
            total = total.add(deductQty);
        }

        if (total.compareTo(quantity) < 0) {
            throw new ServiceException("物料[" + material.getMaterialCode()
                + "]在仓库[" + warehouse.getWarehouseName()
                + "]库存不足，需要" + quantity.stripTrailingZeros().toPlainString()
                + "，可用" + total.stripTrailingZeros().toPlainString());
        }

        PickingAllocation result = new PickingAllocation();
        result.setWarehouseId(warehouseId);
        result.setWarehouseCode(warehouse.getWarehouseCode());
        result.setWarehouseName(warehouse.getWarehouseName());
        result.setStocks(allocated);
        result.setTotalAllocatedQty(total);
        return result;
    }

    /**
     * 全局匹配拣货规则（不限定仓库）
     * <p>
     * 查询所有启用规则按优先级排序，优先匹配物料类别，否则取通用规则（materialCategoryId 为空）。
     * 规则绑定的 warehouseId 即作为目标仓库。
     *
     * @param categoryId 物料类别ID
     * @return 匹配的规则，无则返回 null
     */
    private WmPickingRule findMatchingRuleGlobal(Long categoryId) {
        LambdaQueryWrapper<WmPickingRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmPickingRule::getStatus, "0");
        lqw.orderByAsc(WmPickingRule::getPriority);
        List<WmPickingRule> rules = pickingRuleMapper.selectList(lqw);
        if (rules.isEmpty()) {
            return null;
        }
        return rules.stream()
            .filter(r -> r.getMaterialCategoryId() == null || r.getMaterialCategoryId().equals(categoryId))
            .findFirst()
            .orElse(rules.get(0));
    }
}
