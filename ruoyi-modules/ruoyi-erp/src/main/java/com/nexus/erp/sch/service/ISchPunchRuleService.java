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

    TableDataInfo<SchPunchRuleVo> selectPageList(SchPunchRuleBo bo, PageQuery pageQuery);

    List<SchPunchRuleVo> selectList(SchPunchRuleBo bo);

    SchPunchRuleVo selectById(Long id);

    int insertByBo(SchPunchRuleBo bo);

    int updateByBo(SchPunchRuleBo bo);

    int deleteByIds(Long[] ids);
}
