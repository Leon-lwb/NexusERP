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
import com.nexus.erp.wm.service.IPutawayRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@DS("erp")
@Service
public class PutawayRuleServiceImpl implements IPutawayRuleService {

    private final WmPutawayRuleMapper baseMapper;

    @Override
    public TableDataInfo<WmPutawayRuleVo> selectPageList(WmPutawayRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmPutawayRule> lqw = buildQueryWrapper(bo);
        Page<WmPutawayRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmPutawayRuleVo> selectList(WmPutawayRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmPutawayRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmPutawayRuleBo bo) {
        WmPutawayRule entity = MapstructUtils.convert(bo, WmPutawayRule.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmPutawayRuleBo bo) {
        WmPutawayRule entity = MapstructUtils.convert(bo, WmPutawayRule.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

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
}
