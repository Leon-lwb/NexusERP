package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamMemberBo;
import com.nexus.erp.sch.domain.vo.SchTeamMemberVo;

import java.util.List;

/**
 * 班组成员Service接口
 *
 * @author nexus
 * @date 2025-12-24
 */
public interface ISchTeamMemberService {

    TableDataInfo<SchTeamMemberVo> selectPageList(SchTeamMemberBo bo, PageQuery pageQuery);

    List<SchTeamMemberVo> selectList(SchTeamMemberBo bo);

    SchTeamMemberVo selectById(Long id);

    int insertByBo(SchTeamMemberBo bo);

    int updateByBo(SchTeamMemberBo bo);

    int deleteByIds(Long[] ids);
}
