package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchPunchRule;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 打卡规则Service接口
 *
 * @author nexus
 * @date 2026-01-07
 */
public interface ISchPunchRuleService extends IService<SchPunchRule> {
    /**
     * 查询打卡规则
     *
     * @param ruleId 打卡规则主键
     * @return 打卡规则
     */
    public SchPunchRule selectSchPunchRuleByRuleId(Long ruleId);

    /**
     * 查询打卡规则列表
     *
     * @param schPunchRule 打卡规则
     * @return 打卡规则集合
     */
    public List<SchPunchRule> selectSchPunchRuleList(SchPunchRule schPunchRule);

    /**
     * 新增打卡规则
     *
     * @param schPunchRule 打卡规则
     * @return 结果
     */
    public int insertSchPunchRule(SchPunchRule schPunchRule);

    /**
     * 修改打卡规则
     *
     * @param schPunchRule 打卡规则
     * @return 结果
     */
    public int updateSchPunchRule(SchPunchRule schPunchRule);

    /**
     * 批量删除打卡规则
     *
     * @param ruleIds 需要删除的打卡规则主键集合
     * @return 结果
     */
    public int deleteSchPunchRuleByRuleIds(Long[] ruleIds);

    /**
     * 删除打卡规则信息
     *
     * @param ruleId 打卡规则主键
     * @return 结果
     */
    public int deleteSchPunchRuleByRuleId(Long ruleId);
}
