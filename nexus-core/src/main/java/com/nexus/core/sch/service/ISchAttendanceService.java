package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchAttendance;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 考勤记录Service接口
 *
 * @author nexus
 * @date 2026-01-06
 */
public interface ISchAttendanceService extends IService<SchAttendance> {
    /**
     * 查询考勤记录
     *
     * @param attendanceId 考勤记录主键
     * @return 考勤记录
     */
    public SchAttendance selectSchAttendanceByAttendanceId(Long attendanceId);

    /**
     * 查询考勤记录列表
     *
     * @param schAttendance 考勤记录
     * @return 考勤记录集合
     */
    public List<SchAttendance> selectSchAttendanceList(SchAttendance schAttendance);

    /**
     * 新增考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    public int insertSchAttendance(SchAttendance schAttendance);

    /**
     * 修改考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    public int updateSchAttendance(SchAttendance schAttendance);

    /**
     * 批量删除考勤记录
     *
     * @param attendanceIds 需要删除的考勤记录主键集合
     * @return 结果
     */
    public int deleteSchAttendanceByAttendanceIds(Long[] attendanceIds);

    /**
     * 删除考勤记录信息
     *
     * @param attendanceId 考勤记录主键
     * @return 结果
     */
    public int deleteSchAttendanceByAttendanceId(Long attendanceId);
}
