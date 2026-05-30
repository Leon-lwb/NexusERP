package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchPunchRule;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.mapper.SchPunchRuleMapper;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 打卡规则Service业务层处理
 *
 * @author nexus
 * @date 2026-01-07
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchPunchRuleServiceImpl implements ISchPunchRuleService {

    private final SchPunchRuleMapper baseMapper;

    /**
     * 分页查询打卡规则列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 打卡规则分页列表
     */
    @Override
    public TableDataInfo<SchPunchRuleVo> selectPageList(SchPunchRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPunchRule> lqw = buildQueryWrapper(bo);
        Page<SchPunchRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询打卡规则列表
     *
     * @param bo 查询参数
     * @return 打卡规则列表
     */
    @Override
    public List<SchPunchRuleVo> selectList(SchPunchRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取打卡规则详情
     *
     * @param id 打卡规则ID
     * @return 打卡规则详情
     */
    @Override
    public SchPunchRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增打卡规则
     *
     * @param bo 打卡规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPunchRuleBo bo) {
        SchPunchRule entity = MapstructUtils.convert(bo, SchPunchRule.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改打卡规则
     *
     * @param bo 打卡规则业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPunchRuleBo bo) {
        SchPunchRule entity = MapstructUtils.convert(bo, SchPunchRule.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除打卡规则
     *
     * @param ids 打卡规则ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchPunchRule> buildQueryWrapper(SchPunchRuleBo bo) {
        LambdaQueryWrapper<SchPunchRule> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getRuleName()), SchPunchRule::getRuleName, bo.getRuleName());
            lqw.eq(bo.getIsEnabled() != null, SchPunchRule::getIsEnabled, bo.getIsEnabled());
        }
        return lqw;
    }
}
