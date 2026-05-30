package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;

import java.util.List;

/**
 * 打卡规则Service接口
 *
 * @author nexus
 * @date 2026-01-07
 */
public interface ISchPunchRuleService {

    /**
     * 分页查询打卡规则列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 打卡规则分页列表
     */
    TableDataInfo<SchPunchRuleVo> selectPageList(SchPunchRuleBo bo, PageQuery pageQuery);

    /**
     * 查询打卡规则列表
     *
     * @param bo 查询参数
     * @return 打卡规则列表
     */
    List<SchPunchRuleVo> selectList(SchPunchRuleBo bo);

    /**
     * 根据ID获取打卡规则详情
     *
     * @param id 打卡规则ID
     * @return 打卡规则详情
     */
    SchPunchRuleVo selectById(Long id);

    /**
     * 新增打卡规则
     *
     * @param bo 打卡规则业务对象
     * @return 影响行数
     */
    int insertByBo(SchPunchRuleBo bo);

    /**
     * 修改打卡规则
     *
     * @param bo 打卡规则业务对象
     * @return 影响行数
     */
    int updateByBo(SchPunchRuleBo bo);

    /**
     * 批量删除打卡规则
     *
     * @param ids 打卡规则ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
