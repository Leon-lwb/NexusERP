package com.nexus.core.sch.service;

import java.util.List;

import com.nexus.core.sch.domain.SchEmployeeSchedule;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 员工排班明细Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchEmployeeScheduleService extends IService<SchEmployeeSchedule> {
    /**
     * 查询员工排班明细
     *
     * @param scheduleId 员工排班明细主键
     * @return 员工排班明细
     */
    public SchEmployeeSchedule selectSchEmployeeScheduleByScheduleId(Long scheduleId);

    /**
     * 查询员工排班明细列表
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 员工排班明细集合
     */
    public List<SchEmployeeSchedule> selectSchEmployeeScheduleList(SchEmployeeSchedule schEmployeeSchedule);

    /**
     * 新增员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    public int insertSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule);

    /**
     * 修改员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    public int updateSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule);

    /**
     * 批量删除员工排班明细
     *
     * @param scheduleIds 需要删除的员工排班明细主键集合
     * @return 结果
     */
    public int deleteSchEmployeeScheduleByScheduleIds(Long[] scheduleIds);

    /**
     * 删除员工排班明细信息
     *
     * @param scheduleId 员工排班明细主键
     * @return 结果
     */
    public int deleteSchEmployeeScheduleByScheduleId(Long scheduleId);
}
