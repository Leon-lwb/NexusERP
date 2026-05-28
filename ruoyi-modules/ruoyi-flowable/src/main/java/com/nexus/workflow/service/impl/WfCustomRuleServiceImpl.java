package com.nexus.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.workflow.domain.WfCustomRule;
import com.nexus.workflow.domain.bo.WfCustomRuleBo;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;
import com.nexus.workflow.mapper.WfCustomRuleMapper;
import com.nexus.workflow.service.IWfCustomRuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 流程自定义业务规则Service业务层处理
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@RequiredArgsConstructor
@Service
public class WfCustomRuleServiceImpl implements IWfCustomRuleService {

    private final WfCustomRuleMapper baseMapper;

    /**
     * 查询流程自定义业务规则
     */
    @Override
    public WfCustomRuleVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询流程自定义业务规则列表
     */
    @Override
    public TableDataInfo<WfCustomRuleVo> queryPageList(WfCustomRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfCustomRule> lqw = buildQueryWrapper(bo);
        Page<WfCustomRuleVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询流程自定义业务规则列表
     */
    @Override
    public List<WfCustomRuleVo> queryList(WfCustomRuleBo bo) {
        LambdaQueryWrapper<WfCustomRule> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfCustomRule> buildQueryWrapper(WfCustomRuleBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfCustomRule> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getConfigId() != null, WfCustomRule::getConfigId, bo.getConfigId());
        lqw.eq(StringUtils.isNotBlank(bo.getColCode()), WfCustomRule::getColCode, bo.getColCode());
        lqw.like(StringUtils.isNotBlank(bo.getColName()), WfCustomRule::getColName, bo.getColName());
        lqw.eq(StringUtils.isNotBlank(bo.getJavaType()), WfCustomRule::getJavaType, bo.getJavaType());
        lqw.eq(StringUtils.isNotBlank(bo.getJavaField()), WfCustomRule::getJavaField, bo.getJavaField());
        lqw.eq(StringUtils.isNotBlank(bo.getAttribute()), WfCustomRule::getAttribute, bo.getAttribute());
        lqw.eq(bo.getSort() != null, WfCustomRule::getSort, bo.getSort());
        return lqw;
    }

    /**
     * 新增流程自定义业务规则
     */
    @Override
    public Boolean insertByBo(WfCustomRuleBo bo) {
        WfCustomRule add = BeanUtil.toBean(bo, WfCustomRule.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改流程自定义业务规则
     */
    @Override
    public Boolean updateByBo(WfCustomRuleBo bo) {
        WfCustomRule update = BeanUtil.toBean(bo, WfCustomRule.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(WfCustomRule entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除流程自定义业务规则
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }
}
