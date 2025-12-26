package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchPlanTeam;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 计划班组关联Service接口
 *
 * @author nexus
 * @date 2025-12-26
 */
public interface ISchPlanTeamService extends IService<SchPlanTeam> {
    /**
     * 查询计划班组关联
     *
     * @param recordId 计划班组关联主键
     * @return 计划班组关联
     */
    public SchPlanTeam selectSchPlanTeamByRecordId(Long recordId);

    /**
     * 查询计划班组关联列表
     *
     * @param schPlanTeam 计划班组关联
     * @return 计划班组关联集合
     */
    public List<SchPlanTeam> selectSchPlanTeamList(SchPlanTeam schPlanTeam);

    /**
     * 新增计划班组关联
     *
     * @param schPlanTeam 计划班组关联
     * @return 结果
     */
    public int insertSchPlanTeam(SchPlanTeam schPlanTeam);

    /**
     * 修改计划班组关联
     *
     * @param schPlanTeam 计划班组关联
     * @return 结果
     */
    public int updateSchPlanTeam(SchPlanTeam schPlanTeam);

    /**
     * 批量删除计划班组关联
     *
     * @param recordIds 需要删除的计划班组关联主键集合
     * @return 结果
     */
    public int deleteSchPlanTeamByRecordIds(Long[] recordIds);

    /**
     * 删除计划班组关联信息
     *
     * @param recordId 计划班组关联主键
     * @return 结果
     */
    public int deleteSchPlanTeamByRecordId(Long recordId);
}
