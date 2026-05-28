package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamBo;
import com.nexus.erp.sch.domain.vo.SchTeamVo;

import java.util.List;

/**
 * 班组设置Service接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface ISchTeamService {

    TableDataInfo<SchTeamVo> selectPageList(SchTeamBo bo, PageQuery pageQuery);

    List<SchTeamVo> selectList(SchTeamBo bo);

    SchTeamVo selectById(Long id);

    int insertByBo(SchTeamBo bo);

    int updateByBo(SchTeamBo bo);

    int deleteByIds(Long[] ids);
}
