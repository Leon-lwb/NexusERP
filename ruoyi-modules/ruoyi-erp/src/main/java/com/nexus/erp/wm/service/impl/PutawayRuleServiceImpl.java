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

    private final WmPutawayRuleMapper baseMapper;

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
        return baseMapper.selectVoList(buildQueryWrapper(bo));
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
}
