package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamScheduleBo;
import com.nexus.erp.sch.domain.vo.SchTeamScheduleVo;

import java.util.List;

/**
 * 班组排班（模板）Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchTeamScheduleService {

    TableDataInfo<SchTeamScheduleVo> selectPageList(SchTeamScheduleBo bo, PageQuery pageQuery);

    List<SchTeamScheduleVo> selectList(SchTeamScheduleBo bo);

    SchTeamScheduleVo selectById(Long id);

    int insertByBo(SchTeamScheduleBo bo);

    int updateByBo(SchTeamScheduleBo bo);

    int deleteByIds(Long[] ids);
}
