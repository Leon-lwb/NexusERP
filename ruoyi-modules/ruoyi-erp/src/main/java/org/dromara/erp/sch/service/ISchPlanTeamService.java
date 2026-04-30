package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchPlanTeamBo;
import org.dromara.erp.sch.domain.vo.SchPlanTeamVo;

import java.util.List;

/**
 * 计划班组关联Service接口
 *
 * @author nexus
 * @date 2025-12-26
 */
public interface ISchPlanTeamService {

    TableDataInfo<SchPlanTeamVo> selectPageList(SchPlanTeamBo bo, PageQuery pageQuery);

    List<SchPlanTeamVo> selectList(SchPlanTeamBo bo);

    SchPlanTeamVo selectById(Long id);

    int insertByBo(SchPlanTeamBo bo);

    int updateByBo(SchPlanTeamBo bo);

    int deleteByIds(Long[] ids);
}
