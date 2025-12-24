package com.nexus.core.sch.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.sch.domain.SchTeam;
import com.nexus.core.sch.domain.SchTeamMember;

/**
 * 班组设置Mapper接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface SchTeamMapper extends BaseMapper<SchTeam> {
    /**
     * 查询班组设置
     *
     * @param teamId 班组设置主键
     * @return 班组设置
     */
    public SchTeam selectSchTeamByTeamId(Long teamId);

    /**
     * 查询班组设置列表
     *
     * @param schTeam 班组设置
     * @return 班组设置集合
     */
    public List<SchTeam> selectSchTeamList(SchTeam schTeam);

    /**
     * 新增班组设置
     *
     * @param schTeam 班组设置
     * @return 结果
     */
    public int insertSchTeam(SchTeam schTeam);

    /**
     * 修改班组设置
     *
     * @param schTeam 班组设置
     * @return 结果
     */
    public int updateSchTeam(SchTeam schTeam);

    /**
     * 删除班组设置
     *
     * @param teamId 班组设置主键
     * @return 结果
     */
    public int deleteSchTeamByTeamId(Long teamId);

    /**
     * 批量删除班组设置
     *
     * @param teamIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchTeamByTeamIds(Long[] teamIds);

    /**
     * 批量删除班组成员
     *
     * @param teamIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchTeamMemberByTeamIds(Long[] teamIds);

    /**
     * 批量新增班组成员
     *
     * @param schTeamMemberList 班组成员列表
     * @return 结果
     */
    public int batchSchTeamMember(List<SchTeamMember> schTeamMemberList);


    /**
     * 通过班组设置主键删除班组成员信息
     *
     * @param teamId 班组设置ID
     * @return 结果
     */
    public int deleteSchTeamMemberByTeamId(Long teamId);

    /**
     * 通过班组ID和工人ID检查班组成员是否存在
     *
     * @param teamId 班组ID
     * @param workerId 工人ID
     * @return 班组成员信息，如果不存在则返回null
     */
    public SchTeamMember checkTeamMemberExists(Long teamId, Long workerId);
}
