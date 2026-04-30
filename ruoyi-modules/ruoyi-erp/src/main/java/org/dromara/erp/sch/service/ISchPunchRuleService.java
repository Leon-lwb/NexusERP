package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchPunchRuleBo;
import org.dromara.erp.sch.domain.vo.SchPunchRuleVo;

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
