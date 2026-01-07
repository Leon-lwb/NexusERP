package com.nexus.core.sch.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.sch.domain.SchPunchRule;

/**
 * 打卡规则Mapper接口
 *
 * @author nexus
 * @date 2026-01-07
 */
public interface SchPunchRuleMapper extends BaseMapper<SchPunchRule> {
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
     * 删除打卡规则
     *
     * @param ruleId 打卡规则主键
     * @return 结果
     */
    public int deleteSchPunchRuleByRuleId(Long ruleId);

    /**
     * 批量删除打卡规则
     *
     * @param ruleIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchPunchRuleByRuleIds(Long[] ruleIds);
}
