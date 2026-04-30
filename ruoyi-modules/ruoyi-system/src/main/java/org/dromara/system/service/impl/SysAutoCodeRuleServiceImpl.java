package org.dromara.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.system.domain.SysAutoCodeRule;
import org.dromara.system.domain.vo.SysAutoCodeRuleVo;
import org.dromara.system.mapper.SysAutoCodeRuleMapper;
import org.dromara.system.service.ISysAutoCodeRuleService;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SysAutoCodeRuleServiceImpl implements ISysAutoCodeRuleService {

    private final SysAutoCodeRuleMapper baseMapper;

    @Override
    public SysAutoCodeRule getOne(String ruleCode) {
        List<SysAutoCodeRule> rules = baseMapper.selectList(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleCode, ruleCode));
        return CollUtil.isNotEmpty(rules) ? rules.get(0) : null;
    }

    @Override
    public List<SysAutoCodeRuleVo> selectAutoCodeList(SysAutoCodeRule rule) {
        LambdaQueryWrapper<SysAutoCodeRule> wrapper = new LambdaQueryWrapper<>();
        if (rule.getRuleCode() != null && !rule.getRuleCode().isEmpty()) {
            wrapper.eq(SysAutoCodeRule::getRuleCode, rule.getRuleCode());
        }
        if (rule.getRuleName() != null && !rule.getRuleName().isEmpty()) {
            wrapper.like(SysAutoCodeRule::getRuleName, rule.getRuleName());
        }
        wrapper.orderByDesc(SysAutoCodeRule::getCreateTime);
        return baseMapper.selectVoList(wrapper);
    }

    @Override
    public SysAutoCodeRuleVo findById(Long ruleId) {
        return baseMapper.selectVoById(ruleId);
    }

    @Override
    public boolean checkRuleCodeUnique(SysAutoCodeRule rule) {
        SysAutoCodeRule exist = baseMapper.selectOne(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleCode, rule.getRuleCode()));
        return exist == null || exist.getRuleId().equals(rule.getRuleId());
    }

    @Override
    public boolean checkRuleNameUnique(SysAutoCodeRule rule) {
        SysAutoCodeRule exist = baseMapper.selectOne(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleName, rule.getRuleName()));
        return exist == null || exist.getRuleId().equals(rule.getRuleId());
    }

    @Override
    public int insertInfo(SysAutoCodeRule rule) {
        return baseMapper.insert(rule);
    }

    @Override
    public int updateInfo(SysAutoCodeRule rule) {
        return baseMapper.updateById(rule);
    }

    @Override
    public int deleteById(Long ruleId) {
        return baseMapper.deleteById(ruleId);
    }

    @Override
    public int deleteByIds(Long[] ruleIds) {
        for (Long id : ruleIds) {
            baseMapper.deleteById(id);
        }
        return 1;
    }
}
