package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmPutawayRule;
import com.nexus.erp.wm.domain.bo.WmPutawayRuleBo;
import com.nexus.erp.wm.domain.vo.WmPutawayRuleVo;
import com.nexus.erp.wm.mapper.WmPutawayRuleMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.wm.service.IPutawayRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上架规则Service业务层处理
 * <p>
 * 提供上架规则的分页查询、列表查询、新增、修改、删除等业务操作。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PutawayRuleServiceImpl implements IPutawayRuleService {

    /** 基础数据访问映射器（Mapper） */
    private final WmPutawayRuleMapper baseMapper;
    /** 仓库数据访问映射器（Mapper） */
    private final WmWarehouseMapper wmWarehouseMapper;
    /** 上架规则数据访问映射器（Mapper） */
    private final BdMaterialCategoryMapper bdMaterialCategoryMapper;

    /**
     * 分页查询上架规则列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 上架规则分页列表
     */
    @Override
    public TableDataInfo<WmPutawayRuleVo> selectPageList(WmPutawayRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmPutawayRule> lqw = buildQueryWrapper(bo);
        Page<WmPutawayRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 批量填充仓库名称、物料分类名称
        fillWarehouseAndCategoryInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询上架规则列表
     *
     * @param bo 查询参数
     * @return 上架规则列表
     */
    @Override
    public List<WmPutawayRuleVo> selectList(WmPutawayRuleBo bo) {
        List<WmPutawayRuleVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        // 批量填充仓库名称、物料分类名称
        fillWarehouseAndCategoryInfo(list);
        return list;
    }

    /**
     * 根据ID获取上架规则详情
     *
     * @param id 上架规则ID
     * @return 上架规则详情
     */
    @Override
    public WmPutawayRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增上架规则
     *
     * @param bo 上架规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmPutawayRuleBo bo) {
        WmPutawayRule entity = MapstructUtils.convert(bo, WmPutawayRule.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改上架规则
     *
     * @param bo 上架规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmPutawayRuleBo bo) {
        WmPutawayRule entity = MapstructUtils.convert(bo, WmPutawayRule.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除上架规则
     *
     * @param ids 上架规则ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return MyBatis查询包装器
     */
    private LambdaQueryWrapper<WmPutawayRule> buildQueryWrapper(WmPutawayRuleBo bo) {
        LambdaQueryWrapper<WmPutawayRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRuleCode()), WmPutawayRule::getRuleCode, bo.getRuleCode());
        lqw.like(StringUtils.isNotBlank(bo.getRuleName()), WmPutawayRule::getRuleName, bo.getRuleName());
        lqw.eq(bo.getWarehouseId() != null, WmPutawayRule::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getMaterialCategoryId() != null, WmPutawayRule::getMaterialCategoryId, bo.getMaterialCategoryId());
        lqw.eq(StringUtils.isNotBlank(bo.getStrategyType()), WmPutawayRule::getStrategyType, bo.getStrategyType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmPutawayRule::getStatus, bo.getStatus());
        return lqw;
    }

    /**
     * 批量填充仓库名称、仓库编码、物料分类名称
     * <p>
     * 收集所有规则的 warehouseId / materialCategoryId，分别批量查询仓库与物料分类信息，
     * 通过 Map 映射填充到每条记录，避免 N+1 查询。
     * </p>
     *
     * @param list 规则Vo列表
     */
    private void fillWarehouseAndCategoryInfo(List<WmPutawayRuleVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 批量查询仓库信息
        Set<Long> warehouseIds = list.stream()
            .map(WmPutawayRuleVo::getWarehouseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, WmWarehouseVo> warehouseMap = Map.of();
        if (!warehouseIds.isEmpty()) {
            List<WmWarehouseVo> warehouses = wmWarehouseMapper.selectVoByIds(warehouseIds);
            warehouseMap = warehouses.stream()
                .collect(Collectors.toMap(WmWarehouseVo::getWarehouseId, w -> w));
        }

        // 批量查询物料分类信息
        Set<Long> categoryIds = list.stream()
            .map(WmPutawayRuleVo::getMaterialCategoryId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, BdMaterialCategoryVo> categoryMap = Map.of();
        if (!categoryIds.isEmpty()) {
            List<BdMaterialCategoryVo> categories = bdMaterialCategoryMapper.selectVoByIds(categoryIds);
            categoryMap = categories.stream()
                .collect(Collectors.toMap(BdMaterialCategoryVo::getCategoryId, c -> c));
        }

        // 填充关联信息
        for (WmPutawayRuleVo vo : list) {
            WmWarehouseVo warehouse = warehouseMap.get(vo.getWarehouseId());
            if (warehouse != null) {
                vo.setWarehouseName(warehouse.getWarehouseName());
                vo.setWarehouseCode(warehouse.getWarehouseCode());
            }
            BdMaterialCategoryVo category = categoryMap.get(vo.getMaterialCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
            }
        }
    }
}
