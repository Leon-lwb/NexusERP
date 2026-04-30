package org.dromara.erp.sch.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.sch.domain.SchTeam;
import org.dromara.erp.sch.domain.SchTeamMember;
import org.dromara.erp.sch.domain.vo.SchTeamVo;

import java.util.List;

/**
 * 班组设置Mapper接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface SchTeamMapper extends BaseMapperPlus<SchTeam, SchTeamVo> {

    /**
     * 批量删除班组成员
     */
    int deleteSchTeamMemberByTeamIds(Long[] teamIds);

    /**
     * 批量新增班组成员
     */
    int batchSchTeamMember(List<SchTeamMember> schTeamMemberList);

    /**
     * 通过班组设置主键删除班组成员信息
     */
    int deleteSchTeamMemberByTeamId(Long teamId);

    /**
     * 通过班组ID和工人ID检查班组成员是否存在
     */
    SchTeamMember checkTeamMemberExists(@Param("teamId") Long teamId, @Param("workerId") Long workerId);
}
