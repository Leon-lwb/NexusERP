package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.wm.domain.WmPutawayRule;
import org.dromara.erp.wm.domain.WmStorageLocation;
import org.dromara.erp.wm.mapper.WmPutawayRuleMapper;
import org.dromara.erp.wm.mapper.WmStorageLocationMapper;
import org.dromara.erp.wm.service.IPutawayStrategyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class PutawayStrategyServiceImpl implements IPutawayStrategyService {

    private final WmPutawayRuleMapper putawayRuleMapper;
    private final WmStorageLocationMapper storageLocationMapper;
    private final BdMaterialMapper bdMaterialMapper;

    @Override
    public WmStorageLocation recommendLocation(Long materialId, Long warehouseId, BigDecimal quantity) {
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new ServiceException("物料不存在");
        }

        WmPutawayRule rule = findMatchingRule(material.getCategoryId(), warehouseId);
        if (rule == null) {
            throw new ServiceException("未找到匹配的上架策略规则");
        }

        List<WmStorageLocation> candidates = getCandidateLocations(warehouseId);

        return switch (rule.getStrategyType()) {
            case "NEAREST_EMPTY" -> nearestEmpty(candidates);
            case "CLASS_BASED" -> classBased(candidates, material.getCategoryId());
            case "FIFO_LOCATION" -> fifoLocation(candidates);
            default -> throw new ServiceException("不支持的策略类型: " + rule.getStrategyType());
        };
    }

    private WmPutawayRule findMatchingRule(Long categoryId, Long warehouseId) {
        LambdaQueryWrapper<WmPutawayRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmPutawayRule::getWarehouseId, warehouseId);
        lqw.eq(WmPutawayRule::getStatus, "0");
        lqw.orderByAsc(WmPutawayRule::getPriority);
        List<WmPutawayRule> rules = putawayRuleMapper.selectList(lqw);
        if (rules.isEmpty()) {
            return null;
        }
        return rules.stream()
            .filter(r -> r.getMaterialCategoryId() == null || r.getMaterialCategoryId().equals(categoryId))
            .findFirst()
            .orElse(rules.get(0));
    }

    private List<WmStorageLocation> getCandidateLocations(Long warehouseId) {
        LambdaQueryWrapper<WmStorageLocation> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmStorageLocation::getEnableFlag, 1L);
        lqw.eq(WmStorageLocation::getFrozenFlag, 0L);
        return storageLocationMapper.selectList(lqw);
    }

    private WmStorageLocation nearestEmpty(List<WmStorageLocation> candidates) {
        return candidates.stream()
            .filter(l -> l.getOccupancyRate() == null || l.getOccupancyRate().compareTo(new BigDecimal("100")) < 0)
            .min(Comparator
                .comparing(WmStorageLocation::getAreaId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WmStorageLocation::getRackId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WmStorageLocation::getLayer, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WmStorageLocation::getCol, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElseThrow(() -> new ServiceException("未找到可用的空库位"));
    }

    private WmStorageLocation classBased(List<WmStorageLocation> candidates, Long categoryId) {
        return candidates.stream()
            .filter(l -> l.getOccupancyRate() == null || l.getOccupancyRate().compareTo(new BigDecimal("100")) < 0)
            .min(Comparator.comparing(WmStorageLocation::getOccupancyRate, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElseThrow(() -> new ServiceException("未找到匹配物料类别的可用库位"));
    }

    private WmStorageLocation fifoLocation(List<WmStorageLocation> candidates) {
        return candidates.stream()
            .filter(l -> l.getOccupancyRate() == null || l.getOccupancyRate().compareTo(new BigDecimal("100")) < 0)
            .min(Comparator.comparing(WmStorageLocation::getLastInboundTime, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElseThrow(() -> new ServiceException("未找到可用的FIFO库位"));
    }
}
