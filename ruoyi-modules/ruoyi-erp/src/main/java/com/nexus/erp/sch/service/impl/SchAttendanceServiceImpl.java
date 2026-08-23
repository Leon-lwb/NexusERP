package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.DateUtils;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.bo.SchAttendanceBo;
import com.nexus.erp.sch.domain.vo.SchAttendanceVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.service.ISchAttendanceService;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.common.cv.domain.FaceDtos.VerifyResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.domain.vo.SysOssVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 考勤记录Service业务层处理
 *
 * @author nexus
 * @date 2026-01-06
 */
@RequiredArgsConstructor
@Slf4j
@DS("erp")
@Service
public class SchAttendanceServiceImpl implements ISchAttendanceService {

    private final SchAttendanceMapper baseMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;
    private final SchShiftMapper shiftMapper;
    private final ISchPunchRuleService schPunchRuleService;
    private final VisionClient visionClient;
    private final CvProperties cvProperties;
    private final SchWorkerMapper workerMapper;
    private final ISysOssService sysOssService;

    /**
     * 分页查询考勤记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 考勤记录分页列表
     */
    @Override
    public TableDataInfo<SchAttendanceVo> selectPageList(SchAttendanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchAttendance> lqw = buildQueryWrapper(bo);
        Page<SchAttendanceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询考勤记录列表
     *
     * @param bo 查询参数
     * @return 考勤记录列表
     */
    @Override
    public List<SchAttendanceVo> selectList(SchAttendanceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取考勤记录详情
     *
     * @param id 考勤记录ID
     * @return 考勤记录详情
     */
    @Override
    public SchAttendanceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增考勤记录
     *
     * @param bo 考勤记录业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchAttendanceBo bo) {
        SchAttendance entity = MapstructUtils.convert(bo, SchAttendance.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改考勤记录
     *
     * @param bo 考勤记录业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchAttendanceBo bo) {
        SchAttendance entity = MapstructUtils.convert(bo, SchAttendance.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除考勤记录
     *
     * @param ids 考勤记录ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行上班打卡
     * <p>根据工人ID和打卡时间记录上班考勤，计算迟到分钟数并判定考勤状态</p>
     *
     * @param workerId   工人ID
     * @param workerName 工人姓名
     * @param clockInTime 上班打卡时间
     * @param deviceId   打卡设备ID
     * @param deviceName 打卡设备名称
     * @return 打卡结果数据，包含考勤ID、打卡时间、是否迟到、迟到分钟数等
     */
    @Override
    public Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName) {
        LocalDate todayLd = LocalDate.now();
        Date today = Date.from(todayLd.atStartOfDay(ZoneId.systemDefault()).toInstant());

        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setWorkerName(workerName);
        attendance.setRecordDate(today);
        attendance.setClockInTime(clockInTime);
        attendance.setSourceType("manual");
        attendance.setDeviceId(deviceId);
        attendance.setDeviceName(deviceName);

        String attendanceStatus = determineAttendanceStatus(clockInTime, workerId, today, true);
        attendance.setAttendanceStatus(attendanceStatus);

        long lateMinutes = 0;
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);
        schedule.setScheduledDate(today);
        LambdaQueryWrapper<SchEmployeeSchedule> scheduleLqw = Wrappers.lambdaQuery();
        scheduleLqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
        scheduleLqw.eq(SchEmployeeSchedule::getScheduledDate, today);
        List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(scheduleLqw);

        if (!scheduleList.isEmpty()) {
            SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
            Date startTime = employeeSchedule.getStartTime();
            if (startTime != null) {
                long diff = clockInTime.getTime() - startTime.getTime();
                lateMinutes = Math.max(0, diff / (1000 * 60));
            }
        }

        attendance.setLateMinutes(lateMinutes);

        LambdaQueryWrapper<SchAttendance> existLqw = Wrappers.lambdaQuery();
        existLqw.eq(SchAttendance::getWorkerId, workerId);
        existLqw.eq(SchAttendance::getRecordDate, today);
        List<SchAttendance> attendanceList = baseMapper.selectList(existLqw);

        if (attendanceList.isEmpty()) {
            baseMapper.insert(attendance);
        } else {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockInTime(clockInTime);
            existing.setWorkerName(workerName);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);
            existing.setAttendanceStatus(attendanceStatus);
            existing.setLateMinutes(lateMinutes);
            baseMapper.updateById(existing);
            attendance = existing;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("attendanceId", attendance.getAttendanceId());
        data.put("clockInTime", clockInTime);
        data.put("locationValid", true);
        data.put("distance", 0);
        data.put("isLate", "late".equals(attendance.getAttendanceStatus()));
        data.put("lateMinutes", lateMinutes);
        data.put("nextClockOutTime", null);

        return data;
    }

    /**
     * 执行下班打卡
     * <p>根据工人ID和打卡时间记录下班考勤，计算早退分钟数、加班时长并综合判定考勤状态</p>
     *
     * @param workerId      工人ID
     * @param workerName    工人姓名
     * @param clockOutTime  下班打卡时间
     * @param deviceId      打卡设备ID
     * @param deviceName    打卡设备名称
     * @param overtimeHours 加班时长（小时）
     * @return 打卡结果数据，包含考勤ID、打卡时间、是否早退、早退分钟数、考勤状态等
     */
    @Override
    public Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours) {
        LocalDate todayLd = LocalDate.now();
        Date today = Date.from(todayLd.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LambdaQueryWrapper<SchAttendance> existLqw = Wrappers.lambdaQuery();
        existLqw.eq(SchAttendance::getWorkerId, workerId);
        existLqw.eq(SchAttendance::getRecordDate, today);
        List<SchAttendance> attendanceList = baseMapper.selectList(existLqw);

        Map<String, Object> result = new HashMap<>();

        if (!attendanceList.isEmpty()) {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockOutTime(clockOutTime);
            existing.setWorkerName(workerName);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);

            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, today, false);

            LambdaQueryWrapper<SchEmployeeSchedule> scheduleLqw = Wrappers.lambdaQuery();
            scheduleLqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
            scheduleLqw.eq(SchEmployeeSchedule::getScheduledDate, today);
            List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(scheduleLqw);

            long leaveEarlyMinutes = 0;
            if (!scheduleList.isEmpty()) {
                SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
                Date endTime = employeeSchedule.getEndTime();
                if (endTime != null) {
                    long diff = endTime.getTime() - clockOutTime.getTime();
                    leaveEarlyMinutes = Math.max(0, diff / (1000 * 60));
                }
            }

            existing.setLeaveEarlyMinutes(leaveEarlyMinutes);

            String attendanceStatus;
            boolean isEarlyLeave = "early_leave".equals(clockOutAttendanceStatus);
            boolean wasLate = "late".equals(existing.getAttendanceStatus()) || "late_early_leave".equals(existing.getAttendanceStatus());

            if (wasLate && isEarlyLeave) {
                attendanceStatus = "late_early_leave";
            } else if (wasLate && !isEarlyLeave) {
                attendanceStatus = "late";
            } else if (isEarlyLeave) {
                attendanceStatus = "early_leave";
            } else {
                attendanceStatus = clockOutAttendanceStatus;
            }

            existing.setAttendanceStatus(attendanceStatus);
            if (overtimeHours != null) {
                existing.setOvertimeHours(java.math.BigDecimal.valueOf(overtimeHours));
            }
            baseMapper.updateById(existing);

            result.put("attendanceId", existing.getAttendanceId());
            result.put("clockOutTime", clockOutTime);
            result.put("locationValid", true);
            result.put("distance", 0);
            result.put("isEarlyLeave", "early_leave".equals(existing.getAttendanceStatus()) || "late_early_leave".equals(existing.getAttendanceStatus()));
            result.put("leaveEarlyMinutes", existing.getLeaveEarlyMinutes() != null ? existing.getLeaveEarlyMinutes() : 0);
            result.put("overtimeHours", overtimeHours != null ? overtimeHours : 0.0);
            result.put("attendanceStatus", existing.getAttendanceStatus() != null ? existing.getAttendanceStatus() : "normal");
        } else {
            SchAttendance newAttendance = new SchAttendance();
            newAttendance.setWorkerId(workerId);
            newAttendance.setWorkerName(workerName);
            newAttendance.setRecordDate(today);
            newAttendance.setClockOutTime(clockOutTime);
            newAttendance.setSourceType("manual");
            newAttendance.setDeviceId(deviceId);
            newAttendance.setDeviceName(deviceName);

            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, today, false);

            LambdaQueryWrapper<SchEmployeeSchedule> scheduleLqw = Wrappers.lambdaQuery();
            scheduleLqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
            scheduleLqw.eq(SchEmployeeSchedule::getScheduledDate, today);
            List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(scheduleLqw);

            long leaveEarlyMinutes = 0;
            if (!scheduleList.isEmpty()) {
                SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
                Date endTime = employeeSchedule.getEndTime();
                if (endTime != null) {
                    long diff = endTime.getTime() - clockOutTime.getTime();
                    leaveEarlyMinutes = Math.max(0, diff / (1000 * 60));
                }
            }

            newAttendance.setLeaveEarlyMinutes(leaveEarlyMinutes);
            newAttendance.setAttendanceStatus(clockOutAttendanceStatus);
            if (overtimeHours != null) {
                newAttendance.setOvertimeHours(java.math.BigDecimal.valueOf(overtimeHours));
            }

            baseMapper.insert(newAttendance);

            result.put("attendanceId", newAttendance.getAttendanceId());
            result.put("clockOutTime", clockOutTime);
            result.put("locationValid", true);
            result.put("distance", 0);
            result.put("isEarlyLeave", "early_leave".equals(newAttendance.getAttendanceStatus()));
            result.put("leaveEarlyMinutes", newAttendance.getLeaveEarlyMinutes() != null ? newAttendance.getLeaveEarlyMinutes() : 0);
            result.put("overtimeHours", overtimeHours != null ? overtimeHours : 0.0);
            result.put("attendanceStatus", newAttendance.getAttendanceStatus() != null ? newAttendance.getAttendanceStatus() : "normal");
        }

        return result;
    }

    /**
     * 获取今日打卡状态
     * <p>查询指定工人当天的考勤记录，返回上班/下班打卡状态、迟到/早退分钟数、加班时长等</p>
     *
     * @param workerId 工人ID
     * @return 今日打卡状态数据
     */
    @Override
    public Map<String, Object> getTodayAttendanceStatus(Long workerId) {
        LocalDate todayLd = LocalDate.now();
        Date today = Date.from(todayLd.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LambdaQueryWrapper<SchAttendance> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchAttendance::getWorkerId, workerId);
        lqw.eq(SchAttendance::getRecordDate, today);
        List<SchAttendance> attendanceList = baseMapper.selectList(lqw);

        Map<String, Object> data = new HashMap<>();
        data.put("date", today);

        if (!attendanceList.isEmpty()) {
            SchAttendance todayAttendance = attendanceList.get(0);
            data.put("clockInTime", todayAttendance.getClockInTime());
            data.put("clockOutTime", todayAttendance.getClockOutTime());

            Long punchCount = 0L;

            if (todayAttendance.getClockInTime() != null) {
                data.put("clockInStatus", "success");
                punchCount += 1L;
            } else {
                data.put("clockInStatus", "pending");
            }

            if (todayAttendance.getClockOutTime() != null) {
                data.put("clockOutStatus", "success");
                punchCount += 1L;
            } else {
                data.put("clockOutStatus", "pending");
            }

            data.put("attendanceStatus", todayAttendance.getAttendanceStatus());
            data.put("lateMinutes", todayAttendance.getLateMinutes() != null ? todayAttendance.getLateMinutes() : 0);
            data.put("leaveEarlyMinutes", todayAttendance.getLeaveEarlyMinutes() != null ? todayAttendance.getLeaveEarlyMinutes() : 0);
            data.put("overtimeHours", todayAttendance.getOvertimeHours() != null ? todayAttendance.getOvertimeHours() : BigDecimal.ZERO);
            data.put("punchCount", punchCount);
        } else {
            data.put("clockInTime", null);
            data.put("clockOutTime", null);
            data.put("clockInStatus", "pending");
            data.put("clockOutStatus", "pending");
            data.put("attendanceStatus", "normal");
            data.put("lateMinutes", 0);
            data.put("leaveEarlyMinutes", 0);
            data.put("overtimeHours", BigDecimal.ZERO);
            data.put("punchCount", 0);
        }

        return data;
    }

    /**
     * 验证打卡位置
     * <p>根据用户当前经纬度与打卡规则中配置的中心点位置进行距离计算，判断是否在规定打卡半径内</p>
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 位置验证结果，包含是否在范围内、实际距离、允许半径及打卡规则信息
     */
    @Override
    public Map<String, Object> validatePunchLocation(double latitude, double longitude) {
        try {
            // 取已启用的打卡规则(原硬编码 ruleId=1: 规则被删/停用后位置校验全体报错)
            com.nexus.erp.sch.domain.bo.SchPunchRuleBo ruleQuery = new com.nexus.erp.sch.domain.bo.SchPunchRuleBo();
            ruleQuery.setIsEnabled(1);
            List<SchPunchRuleVo> enabledRules = schPunchRuleService.selectList(ruleQuery);
            if (enabledRules == null || enabledRules.isEmpty()) {
                throw new ServiceException("未找到已启用的打卡规则");
            }
            SchPunchRuleVo punchRule = enabledRules.get(0);

            if (punchRule.getIsEnabled() != null && punchRule.getIsEnabled() == 0) {
                throw new ServiceException("打卡规则已禁用");
            }

            java.math.BigDecimal ruleLat = punchRule.getLocationLat();
            java.math.BigDecimal ruleLng = punchRule.getLocationLng();
            Long ruleRadius = punchRule.getLocationRadius();

            if (ruleLat == null || ruleLng == null || ruleRadius == null) {
                throw new ServiceException("打卡规则配置不完整");
            }

            double centerLat = ruleLat.doubleValue();
            double centerLng = ruleLng.doubleValue();
            long radius = ruleRadius;

            double distance = calculateDistance(latitude, longitude, centerLat, centerLng);

            boolean isValid = distance <= radius;

            Map<String, Object> data = new HashMap<>();
            data.put("isValid", isValid);
            data.put("distance", Math.round(distance));
            data.put("radius", radius);

            String message;
            if (isValid) {
                message = String.format("在打卡范围内（距离%.0f米，允许范围%d米）", distance, radius);
            } else {
                message = String.format("超出打卡范围（距离%.0f米，超过允许范围%.0f米）", distance, distance - radius);
            }
            data.put("message", message);

            Map<String, Object> ruleInfo = new HashMap<>();
            ruleInfo.put("locationLat", centerLat);
            ruleInfo.put("locationLng", centerLng);
            ruleInfo.put("locationRadius", radius);
            ruleInfo.put("locationAddress", punchRule.getLocationAddress());
            data.put("punchRule", ruleInfo);

            return data;
        } catch (Exception e) {
            throw new ServiceException("位置验证失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据打卡时间和排班时间确定考勤状态
     * <p>比对实际打卡时间与排班计划的上下班时间，结合迟到/早退阈值判定考勤结果</p>
     *
     * @param clockTime  打卡时间
     * @param workerId   工人ID
     * @param recordDate 考勤日期
     * @param isClockIn  是否为上班打卡（true上班，false下班）
     * @return 考勤状态（normal正常, late迟到, early_leave早退等）
     */
    @Override
    public String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn) {
        LambdaQueryWrapper<SchEmployeeSchedule> scheduleLqw = Wrappers.lambdaQuery();
        scheduleLqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
        scheduleLqw.eq(SchEmployeeSchedule::getScheduledDate, recordDate);
        List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(scheduleLqw);

        if (scheduleList.isEmpty()) {
            return "normal";
        }

        SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
        SchShift schShift = shiftMapper.selectById(employeeSchedule.getActualShiftId() != null ?
                employeeSchedule.getActualShiftId() : employeeSchedule.getShiftId());

        if (isClockIn) {
            Date startTime = employeeSchedule.getStartTime();
            if (startTime == null) {
                return "normal";
            }

            Long lateThreshold = schShift != null ? schShift.getLateThreshold() : 0L;
            Date adjustedStartTime = DateUtils.addMinutes(startTime, lateThreshold.intValue());

            Calendar clockCal = Calendar.getInstance();
            clockCal.setTime(clockTime);
            clockCal.set(Calendar.SECOND, 0);
            clockCal.set(Calendar.MILLISECOND, 0);

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(adjustedStartTime);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            if (clockCal.compareTo(startCal) <= 0) {
                return "normal";
            } else {
                return "late";
            }
        } else {
            Date endTime = employeeSchedule.getEndTime();
            if (endTime == null) {
                return "normal";
            }

            Long earlyLeaveThreshold = schShift != null ? schShift.getEarlyLeaveThreshold() : 0L;
            Date earliestAllowedClockOutTime = DateUtils.addMinutes(endTime, -earlyLeaveThreshold.intValue());

            Calendar clockCal = Calendar.getInstance();
            clockCal.setTime(clockTime);
            clockCal.set(Calendar.SECOND, 0);
            clockCal.set(Calendar.MILLISECOND, 0);

            Calendar earliestCal = Calendar.getInstance();
            earliestCal.setTime(earliestAllowedClockOutTime);
            earliestCal.set(Calendar.SECOND, 0);
            earliestCal.set(Calendar.MILLISECOND, 0);

            if (clockCal.before(earliestCal)) {
                return "early_leave";
            } else {
                return "normal";
            }
        }
    }

    private LambdaQueryWrapper<SchAttendance> buildQueryWrapper(SchAttendanceBo bo) {
        LambdaQueryWrapper<SchAttendance> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getWorkerId() != null, SchAttendance::getWorkerId, bo.getWorkerId());
            lqw.eq(bo.getRecordDate() != null, SchAttendance::getRecordDate, bo.getRecordDate());
            lqw.eq(StringUtils.isNotBlank(bo.getAttendanceStatus()), SchAttendance::getAttendanceStatus, bo.getAttendanceStatus());
            // 人脸验证状态过滤(FAIL 复查页用)
            lqw.eq(StringUtils.isNotBlank(bo.getFaceVerifyStatus()), SchAttendance::getFaceVerifyStatus, bo.getFaceVerifyStatus());
        }
        return lqw;
    }

    /**
     * 计算两个经纬度坐标之间的距离（使用Haversine公式）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * FAIL 复查人工标记(改 PASS 或确认 FAIL)
     * <p>考勤管理员复查 FAIL 记录:误判→PASS;确认代打卡→保持 FAIL。
     * 操作在 remark 追加 {@code [FACE-REVIEW:status] 备注} 前缀供审计。</p>
     *
     * @param attendanceId 考勤记录ID
     * @param status       目标状态(PASS=误判 / FAIL=确认代打卡)
     * @param remark       复查备注(可选)
     * @return 影响行数
     */
    @Override
    public int markFaceVerified(Long attendanceId, String status, String remark) {
        SchAttendance attendance = baseMapper.selectById(attendanceId);
        if (attendance == null) {
            return 0;
        }
        if (!"PASS".equals(status) && !"FAIL".equals(status)) {
            throw new ServiceException("复查状态仅支持 PASS(误判) 或 FAIL(代打卡)");
        }
        attendance.setFaceVerifyStatus(status);
        attendance.setFaceVerifyTime(new Date());
        // 审计备注:追加复查记录,不覆盖原有备注
        String audit = "[FACE-REVIEW:" + status + "] " + (remark != null ? remark : "人工复查");
        attendance.setRemark(attendance.getRemark() != null
            ? attendance.getRemark() + " " + audit : audit);
        return baseMapper.updateById(attendance);
    }

    /**
     * 异步触发打卡人脸验证
     * <p>打卡成功后由调用方触发(不阻断打卡)。照片先存 MinIO 存证(ossId),再提交 {@code cvExecutor}
     * 异步调 vision 1:1 校验,结果回写 sch_attendance.face_verify_*。</p>
     * <p><b>异步租户上下文</b>:{@code cvExecutor} 线程不传播 Sa-Token ThreadLocal,
     * 必须 {@link TenantHelper#dynamic} 恢复租户,否则 tenant_id 落 0、查不到考勤记录。
     * <b>异步数据源</b>:类级 {@code @DS("erp")} 不作用于异步线程,但 sys_oss 表在 master 库
     * (ry-vue),上传存证前必须 {@link DynamicDataSourceContextHolder#push}("master"),
     * 否则误落 erp 数据源报 "Table 'ry-erp.sys_oss' doesn't exist"。
     * 视觉服务不可用时不抛异常,置 NONE 由降级补跑任务兜底(见 FaceVerifyBackfillTask)。</p>
     *
     * @param attendanceId 考勤记录ID
     * @param workerId     工人ID
     * @param tenantId     租户ID(异步线程需手动恢复)
     * @param imageBytes   打卡人脸照片(可能为 null,照片缺失则不校验置 NONE)
     */
    @Async("cvExecutor")
    @Override
    public void asyncFaceVerify(Long attendanceId, Long workerId, String tenantId, byte[] imageBytes, boolean storeImage) {
        try {
            TenantHelper.dynamic(tenantId, () -> {
                Long ossId = null;
                if (storeImage && imageBytes != null && imageBytes.length > 0) {
                    // 1. 照片存 MinIO 存证(仅打卡时;补跑复用已有 ossId,避免重复存证)
                    //    sys_oss 表在 master 库(ry-vue),异步线程无 @DS 上下文,显式切主数据源
                    DynamicDataSourceContextHolder.push("master");
                    try {
                        java.io.File temp = java.io.File.createTempFile("face-", ".jpg");
                        try {
                            java.nio.file.Files.write(temp.toPath(), imageBytes);
                            SysOssVo ossVo = sysOssService.upload(temp);
                            ossId = ossVo.getOssId();
                        } finally {
                            temp.delete();
                        }
                    } catch (Exception e) {
                        log.error("[cv] 打卡人脸照片存 MinIO 失败, attendanceId={}, 跳过存证继续校验", attendanceId, e);
                    } finally {
                        DynamicDataSourceContextHolder.poll();
                    }
                }
                // 2. 校验 + 回写 sch_attendance(erp 库),异步线程需显式切回 erp 数据源
                DynamicDataSourceContextHolder.push("erp");
                try {
                    verifyAndWriteBack(attendanceId, workerId, imageBytes, ossId);
                } finally {
                    DynamicDataSourceContextHolder.poll();
                }
            });
        } catch (Exception e) {
            // 异步校验失败不影响打卡主流程,告警即可
            log.error("[cv] 打卡人脸验证异常, attendanceId={}, workerId={}", attendanceId, workerId, e);
        }
    }

    /**
     * 执行人脸验证并回写考勤记录
     * <p>vision 可用 → PASS/FAIL + similarity;vision 不可用/无照片 → NONE(等待降级补跑)</p>
     */
    private void verifyAndWriteBack(Long attendanceId, Long workerId, byte[] imageBytes, Long ossId) {
        String status = "NONE";
        BigDecimal similarity = null;
        Date verifyTime = new Date();
        try {
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("[cv] 打卡未上传人脸照片, attendanceId={}, 置 NONE", attendanceId);
            } else if (!cvProperties.isEnabled() || !visionClient.isAvailable()) {
                log.warn("[cv] 视觉服务不可用, attendanceId={}, 置 NONE 待补跑", attendanceId);
            } else {
                SchWorker worker = workerMapper.selectById(workerId);
                String subject = worker != null ? "EMP-" + worker.getEmployeeId() : "EMP-" + workerId;
                VerifyResult result = visionClient.verifyFace(imageBytes, subject);
                similarity = BigDecimal.valueOf(result.getSimilarity());
                status = result.isMatched() ? "PASS" : "FAIL";
                log.info("[cv] 打卡人脸验证完成, attendanceId={}, subject={}, status={}, similarity={}",
                    attendanceId, subject, status, similarity);
            }
        } catch (VisionException e) {
            log.warn("[cv] 打卡人脸验证调用失败, attendanceId={}, 置 NONE 待补跑: {}", attendanceId, e.getMessage());
        } catch (Exception e) {
            log.error("[cv] 打卡人脸验证异常, attendanceId={}, 置 NONE 待补跑", attendanceId, e);
        } finally {
            try {
                SchAttendance attendance = baseMapper.selectById(attendanceId);
                if (attendance != null) {
                    attendance.setFaceVerifyStatus(status);
                    attendance.setFaceVerifyTime(verifyTime);
                    attendance.setFaceSimilarity(similarity);
                    if (ossId != null) {
                        attendance.setFaceImageOssId(ossId);
                    }
                    baseMapper.updateById(attendance);
                }
            } catch (Exception e) {
                log.error("[cv] 回写考勤人脸验证状态失败, attendanceId={}", attendanceId, e);
            }
        }
    }
}
