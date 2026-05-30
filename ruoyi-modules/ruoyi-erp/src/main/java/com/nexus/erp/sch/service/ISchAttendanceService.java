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

    /**
     * 分页查询考勤记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 考勤记录分页列表
     */
    TableDataInfo<SchAttendanceVo> selectPageList(SchAttendanceBo bo, PageQuery pageQuery);

    /**
     * 查询考勤记录列表
     *
     * @param bo 查询参数
     * @return 考勤记录列表
     */
    List<SchAttendanceVo> selectList(SchAttendanceBo bo);

    /**
     * 根据ID获取考勤记录详情
     *
     * @param id 考勤记录ID
     * @return 考勤记录详情
     */
    SchAttendanceVo selectById(Long id);

    /**
     * 新增考勤记录
     *
     * @param bo 考勤记录业务对象
     * @return 影响行数
     */
    int insertByBo(SchAttendanceBo bo);

    /**
     * 修改考勤记录
     *
     * @param bo 考勤记录业务对象
     * @return 影响行数
     */
    int updateByBo(SchAttendanceBo bo);

    /**
     * 批量删除考勤记录
     *
     * @param ids 考勤记录ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行上班打卡
     *
     * @param workerId   工人ID
     * @param workerName 工人姓名
     * @param clockInTime 上班打卡时间
     * @param deviceId   打卡设备ID
     * @param deviceName 打卡设备名称
     * @return 打卡结果数据
     */
    Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName);

    /**
     * 执行下班打卡
     *
     * @param workerId      工人ID
     * @param workerName    工人姓名
     * @param clockOutTime  下班打卡时间
     * @param deviceId      打卡设备ID
     * @param deviceName    打卡设备名称
     * @param overtimeHours 加班时长（小时）
     * @return 打卡结果数据
     */
    Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours);

    /**
     * 获取今日打卡状态
     *
     * @param workerId 工人ID
     * @return 今日打卡状态数据
     */
    Map<String, Object> getTodayAttendanceStatus(Long workerId);

    /**
     * 验证打卡位置
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 位置验证结果
     */
    Map<String, Object> validatePunchLocation(double latitude, double longitude);

    /**
     * 根据打卡时间和排班时间确定考勤状态
     *
     * @param clockTime  打卡时间
     * @param workerId   工人ID
     * @param recordDate 考勤日期
     * @param isClockIn  是否为上班打卡（true上班，false下班）
     * @return 考勤状态（normal正常, late迟到, early_leave早退等）
     */
    String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn);
}
