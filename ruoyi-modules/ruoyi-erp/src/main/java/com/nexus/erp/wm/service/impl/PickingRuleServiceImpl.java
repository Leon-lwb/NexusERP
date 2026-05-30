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

/**
 * 拣货规则Service业务层处理
 * <p>
 * 提供拣货规则的分页查询、列表查询、新增、修改、删除等业务操作。
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PickingRuleServiceImpl implements IPickingRuleService {

    private final WmPickingRuleMapper baseMapper;

    /**
     * 分页查询拣货规则列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 拣货规则分页列表
     */
    @Override
    public TableDataInfo<WmPickingRuleVo> selectPageList(WmPickingRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmPickingRule> lqw = buildQueryWrapper(bo);
        Page<WmPickingRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询拣货规则列表
     *
     * @param bo 查询参数
     * @return 拣货规则列表
     */
    @Override
    public List<WmPickingRuleVo> selectList(WmPickingRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取拣货规则详情
     *
     * @param id 拣货规则ID
     * @return 拣货规则详情
     */
    @Override
    public WmPickingRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增拣货规则
     *
     * @param bo 拣货规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmPickingRuleBo bo) {
        WmPickingRule entity = MapstructUtils.convert(bo, WmPickingRule.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改拣货规则
     *
     * @param bo 拣货规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmPickingRuleBo bo) {
        WmPickingRule entity = MapstructUtils.convert(bo, WmPickingRule.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除拣货规则
     *
     * @param ids 拣货规则ID数组
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
