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

    /**
     * 异步触发打卡人脸验证
     * <p>打卡成功后调用(不阻断打卡):照片先存 MinIO 存证,再提交 cvExecutor 异步调 vision 1:1 校验,
     * 结果回写 sch_attendance.face_verify_*;vision 不可用时置 NONE,由降级补跑任务兜底。</p>
     *
     * @param attendanceId 考勤记录ID
     * @param workerId     工人ID
     * @param tenantId     租户ID(异步线程需手动恢复)
     * @param imageBytes   打卡人脸照片(可能为 null,照片缺失则不校验置 NONE)
     * @param storeImage   是否存 MinIO 存证(打卡传 true;降级补跑传 false 复用已有 ossId,避免重复存证)
     */
    void asyncFaceVerify(Long attendanceId, Long workerId, String tenantId, byte[] imageBytes, boolean storeImage);
}
