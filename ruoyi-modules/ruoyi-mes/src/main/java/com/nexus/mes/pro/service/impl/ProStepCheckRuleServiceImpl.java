package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProStepCheckRule;
import com.nexus.mes.pro.domain.bo.ProStepCheckRuleBo;
import com.nexus.mes.pro.domain.vo.ProStepCheckRuleVo;
import com.nexus.mes.pro.mapper.ProStepCheckRuleMapper;
import com.nexus.mes.pro.service.IProStepCheckRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * MES工序校验规则配置 Service 实现(P0-3)
 * <p>规则由 IProEntryCheckService 规则引擎按 process_id + check_stage + enabled 加载执行。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proStepCheckRuleService")
public class ProStepCheckRuleServiceImpl implements IProStepCheckRuleService {

    private final ProStepCheckRuleMapper baseMapper;

    @Override
    public TableDataInfo<ProStepCheckRuleVo> selectPageList(ProStepCheckRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProStepCheckRule> lqw = buildQueryWrapper(bo);
        Page<ProStepCheckRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ProStepCheckRuleVo selectById(Long ruleId) {
        return baseMapper.selectVoById(ruleId);
    }

    @Override
    public List<ProStepCheckRuleVo> selectList(ProStepCheckRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ProStepCheckRuleBo bo) {
        ProStepCheckRule rule = MapstructUtils.convert(bo, ProStepCheckRule.class);
        if (StringUtils.isBlank(rule.getEnabled())) {
            rule.setEnabled("1");
        }
        if (rule.getSortOrder() == null) {
            rule.setSortOrder(0);
        }
        int rows = baseMapper.insert(rule);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ProStepCheckRuleBo bo) {
        ProStepCheckRule rule = MapstructUtils.convert(bo, ProStepCheckRule.class);
        int rows = baseMapper.updateById(rule);
        return rows > 0;
    }

    @Override
    public Boolean deleteByIds(Collection<Long> ids) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    private LambdaQueryWrapper<ProStepCheckRule> buildQueryWrapper(ProStepCheckRuleBo bo) {
        return Wrappers.<ProStepCheckRule>lambdaQuery()
            .eq(bo.getProcessId() != null, ProStepCheckRule::getProcessId, bo.getProcessId())
            .eq(StringUtils.isNotBlank(bo.getCheckType()), ProStepCheckRule::getCheckType, bo.getCheckType())
            .eq(StringUtils.isNotBlank(bo.getCheckStage()), ProStepCheckRule::getCheckStage, bo.getCheckStage())
            .eq(StringUtils.isNotBlank(bo.getEnabled()), ProStepCheckRule::getEnabled, bo.getEnabled())
            .orderByAsc(ProStepCheckRule::getSortOrder);
    }
}
