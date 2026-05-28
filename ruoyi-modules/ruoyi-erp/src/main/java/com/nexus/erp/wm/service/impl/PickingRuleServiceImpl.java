package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmPickingRule;
import com.nexus.erp.wm.domain.bo.WmPickingRuleBo;
import com.nexus.erp.wm.domain.vo.WmPickingRuleVo;
import com.nexus.erp.wm.mapper.WmPickingRuleMapper;
import com.nexus.erp.wm.service.IPickingRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class PickingRuleServiceImpl implements IPickingRuleService {

    private final WmPickingRuleMapper baseMapper;

    @Override
    public TableDataInfo<WmPickingRuleVo> selectPageList(WmPickingRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmPickingRule> lqw = buildQueryWrapper(bo);
        Page<WmPickingRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmPickingRuleVo> selectList(WmPickingRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmPickingRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmPickingRuleBo bo) {
        WmPickingRule entity = MapstructUtils.convert(bo, WmPickingRule.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmPickingRuleBo bo) {
        WmPickingRule entity = MapstructUtils.convert(bo, WmPickingRule.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmPickingRule> buildQueryWrapper(WmPickingRuleBo bo) {
        LambdaQueryWrapper<WmPickingRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRuleCode()), WmPickingRule::getRuleCode, bo.getRuleCode());
        lqw.like(StringUtils.isNotBlank(bo.getRuleName()), WmPickingRule::getRuleName, bo.getRuleName());
        lqw.eq(bo.getWarehouseId() != null, WmPickingRule::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getMaterialCategoryId() != null, WmPickingRule::getMaterialCategoryId, bo.getMaterialCategoryId());
        lqw.eq(StringUtils.isNotBlank(bo.getStrategyType()), WmPickingRule::getStrategyType, bo.getStrategyType());
        lqw.eq(bo.getPriority() != null, WmPickingRule::getPriority, bo.getPriority());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmPickingRule::getStatus, bo.getStatus());
        return lqw;
    }
}
