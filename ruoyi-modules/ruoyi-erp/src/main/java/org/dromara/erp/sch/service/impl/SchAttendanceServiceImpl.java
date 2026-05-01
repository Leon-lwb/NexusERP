package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchAttendance;
import org.dromara.erp.sch.domain.SchEmployeeSchedule;
import org.dromara.erp.sch.domain.SchPunchRule;
import org.dromara.erp.sch.domain.vo.SchPunchRuleVo;
import org.dromara.erp.sch.domain.SchShift;
import org.dromara.erp.sch.domain.bo.SchAttendanceBo;
import org.dromara.erp.sch.domain.vo.SchAttendanceVo;
import org.dromara.erp.sch.mapper.SchAttendanceMapper;
import org.dromara.erp.sch.mapper.SchEmployeeScheduleMapper;
import org.dromara.erp.sch.mapper.SchShiftMapper;
import org.dromara.erp.sch.service.ISchAttendanceService;
import org.dromara.erp.sch.service.ISchPunchRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 考勤记录Service业务层处理
 *
 * @author nexus
 * @date 2026-01-06
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchAttendanceServiceImpl implements ISchAttendanceService {

    private final SchAttendanceMapper baseMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;
    private final SchShiftMapper shiftMapper;
    private final ISchPunchRuleService schPunchRuleService;

    @Override
    public TableDataInfo<SchAttendanceVo> selectPageList(SchAttendanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchAttendance> lqw = buildQueryWrapper(bo);
        Page<SchAttendanceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchAttendanceVo> selectList(SchAttendanceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchAttendanceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchAttendanceBo bo) {
        SchAttendance entity = MapstructUtils.convert(bo, SchAttendance.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchAttendanceBo bo) {
        SchAttendance entity = MapstructUtils.convert(bo, SchAttendance.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行上班打卡
     */
    @Override
    public Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

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
     */
    @Override
    public Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

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
     */
    @Override
    public Map<String, Object> getTodayAttendanceStatus(Long workerId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

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
     */
    @Override
    public Map<String, Object> validatePunchLocation(double latitude, double longitude) {
        try {
            SchPunchRuleVo punchRule = schPunchRuleService.selectById(1L);
            if (punchRule == null) {
                throw new RuntimeException("未找到打卡规则");
            }

            if (punchRule.getIsEnabled() != null && punchRule.getIsEnabled() == 0) {
                throw new RuntimeException("打卡规则已禁用");
            }

            java.math.BigDecimal ruleLat = punchRule.getLocationLat();
            java.math.BigDecimal ruleLng = punchRule.getLocationLng();
            Long ruleRadius = punchRule.getLocationRadius();

            if (ruleLat == null || ruleLng == null || ruleRadius == null) {
                throw new RuntimeException("打卡规则配置不完整");
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
            throw new RuntimeException("位置验证失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据打卡时间和排班时间确定考勤状态
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
}
