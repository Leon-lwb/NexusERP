package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchTeam;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 班组设置Service接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface ISchTeamService extends IService<SchTeam> {
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
     * 批量删除班组设置
     *
     * @param teamIds 需要删除的班组设置主键集合
     * @return 结果
     */
    public int deleteSchTeamByTeamIds(Long[] teamIds);

    /**
     * 删除班组设置信息
     *
     * @param teamId 班组设置主键
     * @return 结果
     */
    public int deleteSchTeamByTeamId(Long teamId);
}
