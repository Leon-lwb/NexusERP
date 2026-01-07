package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchPunchRuleMapper;
import com.nexus.core.sch.domain.SchPunchRule;
import com.nexus.core.sch.service.ISchPunchRuleService;

/**
 * 打卡规则Service业务层处理
 *
 * @author nexus
 * @date 2026-01-07
 */
@Service
public class SchPunchRuleServiceImpl extends ServiceImpl<SchPunchRuleMapper, SchPunchRule> implements ISchPunchRuleService {

    @Autowired
    private SchPunchRuleMapper schPunchRuleMapper;

    /**
     * 查询打卡规则
     *
     * @param ruleId 打卡规则主键
     * @return 打卡规则
     */
    @Override
    public SchPunchRule selectSchPunchRuleByRuleId(Long ruleId) {
        return schPunchRuleMapper.selectSchPunchRuleByRuleId(ruleId);
    }

    /**
     * 查询打卡规则列表
     *
     * @param schPunchRule 打卡规则
     * @return 打卡规则
     */
    @Override
    public List<SchPunchRule> selectSchPunchRuleList(SchPunchRule schPunchRule) {
        return schPunchRuleMapper.selectSchPunchRuleList(schPunchRule);
    }

    /**
     * 新增打卡规则
     *
     * @param schPunchRule 打卡规则
     * @return 结果
     */
    @Override
    public int insertSchPunchRule(SchPunchRule schPunchRule) {
                schPunchRule.setCreateTime(DateUtils.getNowDate());
            return schPunchRuleMapper.insertSchPunchRule(schPunchRule);
    }

    /**
     * 修改打卡规则
     *
     * @param schPunchRule 打卡规则
     * @return 结果
     */
    @Override
    public int updateSchPunchRule(SchPunchRule schPunchRule) {
                schPunchRule.setUpdateTime(DateUtils.getNowDate());
        return schPunchRuleMapper.updateSchPunchRule(schPunchRule);
    }

    /**
     * 批量删除打卡规则
     *
     * @param ruleIds 需要删除的打卡规则主键
     * @return 结果
     */
    @Override
    public int deleteSchPunchRuleByRuleIds(Long[] ruleIds) {
        return schPunchRuleMapper.deleteSchPunchRuleByRuleIds(ruleIds);
    }

    /**
     * 删除打卡规则信息
     *
     * @param ruleId 打卡规则主键
     * @return 结果
     */
    @Override
    public int deleteSchPunchRuleByRuleId(Long ruleId) {
        return schPunchRuleMapper.deleteSchPunchRuleByRuleId(ruleId);
    }
}
