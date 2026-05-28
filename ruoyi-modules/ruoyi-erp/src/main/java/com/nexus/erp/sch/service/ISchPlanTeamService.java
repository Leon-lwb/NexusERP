package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchPlanTeamBo;
import com.nexus.erp.sch.domain.vo.SchPlanTeamVo;

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
