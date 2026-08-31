package com.nexus.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.system.domain.SysAutoCodeRule;
import com.nexus.system.domain.vo.SysAutoCodeRuleVo;
import com.nexus.system.mapper.SysAutoCodeRuleMapper;
import com.nexus.system.service.ISysAutoCodeRuleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 编码规则 服务实现类，实现 {@link ISysAutoCodeRuleService} 接口。属于 System 系统管理模块。
 */
@RequiredArgsConstructor
@Service
public class SysAutoCodeRuleServiceImpl implements ISysAutoCodeRuleService {

    /** SysAutoCodeRuleMapper 数据访问层组件，提供 编码规则 的持久化能力。 */
    private final SysAutoCodeRuleMapper baseMapper;

    /**
     * 根据条件查询单个编码规则。
     * @param ruleCode 规则编码
     * @return 编码规则对象
     */
    @Override
    public SysAutoCodeRule getOne(String ruleCode) {
        List<SysAutoCodeRule> rules = baseMapper.selectList(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleCode, ruleCode));
        return CollUtil.isNotEmpty(rules) ? rules.get(0) : null;
    }

    /**
     * 根据条件查询编码规则列表。
     * @param rule 查询条件（编码规则）
     * @return 匹配条件的编码规则列表
     */
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

    /**
     * 根据条件查询编码规则列表。
     * @param ruleId ID
     * @return 编码规则对象
     */
    @Override
    public SysAutoCodeRuleVo findById(Long ruleId) {
        return baseMapper.selectVoById(ruleId);
    }

    /**
     * 校验编码规则是否唯一。
     * @param rule 待校验的编码规则
     * @return 存在/成功返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean checkRuleCodeUnique(SysAutoCodeRule rule) {
        SysAutoCodeRule exist = baseMapper.selectOne(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleCode, rule.getRuleCode()));
        return exist == null || exist.getRuleId().equals(rule.getRuleId());
    }

    /**
     * 校验编码规则是否唯一。
     * @param rule 待校验的编码规则
     * @return 存在/成功返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean checkRuleNameUnique(SysAutoCodeRule rule) {
        SysAutoCodeRule exist = baseMapper.selectOne(
            new LambdaQueryWrapper<SysAutoCodeRule>().eq(SysAutoCodeRule::getRuleName, rule.getRuleName()));
        return exist == null || exist.getRuleId().equals(rule.getRuleId());
    }

    /**
     * 新增编码规则。
     * @param rule 待新增的编码规则
     * @return 受影响行数
     */
    @Override
    public int insertInfo(SysAutoCodeRule rule) {
        return baseMapper.insert(rule);
    }

    /**
     * 更新编码规则。
     * @param rule 待更新的编码规则
     * @return 受影响行数
     */
    @Override
    public int updateInfo(SysAutoCodeRule rule) {
        return baseMapper.updateById(rule);
    }

    /**
     * 删除记录。
     * @param ruleId 待删除的 ID
     * @return 受影响行数
     */
    @Override
    public int deleteById(Long ruleId) {
        return baseMapper.deleteById(ruleId);
    }

    /**
     * 删除记录。
     * @param ruleIds 待删除的 ID 数组
     * @return 受影响行数
     */
    @Override
    public int deleteByIds(Long[] ruleIds) {
        for (Long id : ruleIds) {
            baseMapper.deleteById(id);
        }
        return 1;
    }
}
