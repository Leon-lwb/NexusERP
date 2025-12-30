package com.nexus.core.sch.service;

import java.util.List;

import com.nexus.core.sch.domain.SchTeamSchedule;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 班组排班（模板）Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchTeamScheduleService extends IService<SchTeamSchedule> {
    /**
     * 查询班组排班（模板）
     *
     * @param teamScheduleId 班组排班（模板）主键
     * @return 班组排班（模板）
     */
    public SchTeamSchedule selectSchTeamScheduleByTeamScheduleId(Long teamScheduleId);

    /**
     * 查询班组排班（模板）列表
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 班组排班（模板）集合
     */
    public List<SchTeamSchedule> selectSchTeamScheduleList(SchTeamSchedule schTeamSchedule);

    /**
     * 新增班组排班（模板）
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 结果
     */
    public int insertSchTeamSchedule(SchTeamSchedule schTeamSchedule);

    /**
     * 修改班组排班（模板）
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 结果
     */
    public int updateSchTeamSchedule(SchTeamSchedule schTeamSchedule);

    /**
     * 批量删除班组排班（模板）
     *
     * @param teamScheduleIds 需要删除的班组排班（模板）主键集合
     * @return 结果
     */
    public int deleteSchTeamScheduleByTeamScheduleIds(Long[] teamScheduleIds);

    /**
     * 删除班组排班（模板）信息
     *
     * @param teamScheduleId 班组排班（模板）主键
     * @return 结果
     */
    public int deleteSchTeamScheduleByTeamScheduleId(Long teamScheduleId);
}
