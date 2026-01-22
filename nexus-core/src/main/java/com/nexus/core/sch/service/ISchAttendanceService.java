package com.nexus.core.sch.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
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

    /**
     * 执行上班打卡
     * @param workerId 工人ID
     * @param workerName 工人姓名
     * @param clockInTime 打卡时间
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @return 打卡结果
     */
    public Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName);

    /**
     * 执行下班打卡
     * @param workerId 工人ID
     * @param workerName 工人姓名
     * @param clockOutTime 打卡时间
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param overtimeHours 加班小时数
     * @return 打卡结果
     */
    public Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours);

    /**
     * 获取今日打卡状态
     * @param workerId 工人ID
     * @return 考勤状态
     */
    public Map<String, Object> getTodayAttendanceStatus(Long workerId);

    /**
     * 验证打卡位置
     * @param latitude 纬度
     * @param longitude 经度
     * @return 位置验证结果
     */
    public Map<String, Object> validatePunchLocation(double latitude, double longitude);

    /**
     * 根据打卡时间和排班时间确定考勤状态
     * @param clockTime 打卡时间
     * @param workerId 工人ID
     * @param recordDate 记录日期
     * @param isClockIn 是否上班打卡
     * @return 考勤状态
     */
    public String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn);
}
