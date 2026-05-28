package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchAttendanceBo;
import com.nexus.erp.sch.domain.vo.SchAttendanceVo;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 考勤记录Service接口
 *
 * @author nexus
 * @date 2026-01-06
 */
public interface ISchAttendanceService {

    TableDataInfo<SchAttendanceVo> selectPageList(SchAttendanceBo bo, PageQuery pageQuery);

    List<SchAttendanceVo> selectList(SchAttendanceBo bo);

    SchAttendanceVo selectById(Long id);

    int insertByBo(SchAttendanceBo bo);

    int updateByBo(SchAttendanceBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行上班打卡
     */
    Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName);

    /**
     * 执行下班打卡
     */
    Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours);

    /**
     * 获取今日打卡状态
     */
    Map<String, Object> getTodayAttendanceStatus(Long workerId);

    /**
     * 验证打卡位置
     */
    Map<String, Object> validatePunchLocation(double latitude, double longitude);

    /**
     * 根据打卡时间和排班时间确定考勤状态
     */
    String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn);
}
