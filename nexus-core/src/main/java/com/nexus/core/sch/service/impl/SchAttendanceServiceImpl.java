package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchAttendanceMapper;
import com.nexus.core.sch.domain.SchAttendance;
import com.nexus.core.sch.domain.SchEmployeeSchedule;
import com.nexus.core.sch.domain.SchPunchRule;
import com.nexus.core.sch.domain.SchShift;
import com.nexus.core.sch.service.ISchAttendanceService;
import com.nexus.core.sch.service.ISchEmployeeScheduleService;
import com.nexus.core.sch.service.ISchPunchRuleService;
import com.nexus.core.sch.service.ISchShiftService;

/**
 * 考勤记录Service业务层处理
 *
 * @author nexus
 * @date 2026-01-06
 */
@Service
public class SchAttendanceServiceImpl extends ServiceImpl<SchAttendanceMapper, SchAttendance> implements ISchAttendanceService {

    @Autowired
    private SchAttendanceMapper schAttendanceMapper;

    @Autowired
    private ISchEmployeeScheduleService schEmployeeScheduleService;

    @Autowired
    private ISchPunchRuleService schPunchRuleService;

    @Autowired
    private ISchShiftService schShiftService;

    /**
     * 查询考勤记录
     *
     * @param attendanceId 考勤记录主键
     * @return 考勤记录
     */
    @Override
    public SchAttendance selectSchAttendanceByAttendanceId(Long attendanceId) {
        return schAttendanceMapper.selectSchAttendanceByAttendanceId(attendanceId);
    }

    /**
     * 查询考勤记录列表
     *
     * @param schAttendance 考勤记录
     * @return 考勤记录
     */
    @Override
    public List<SchAttendance> selectSchAttendanceList(SchAttendance schAttendance) {
        return schAttendanceMapper.selectSchAttendanceList(schAttendance);
    }

    /**
     * 新增考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    @Override
    public int insertSchAttendance(SchAttendance schAttendance) {
        schAttendance.setCreateTime(DateUtils.getNowDate());
        return schAttendanceMapper.insertSchAttendance(schAttendance);
    }

    /**
     * 修改考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    @Override
    public int updateSchAttendance(SchAttendance schAttendance) {
        schAttendance.setUpdateTime(DateUtils.getNowDate());
        return schAttendanceMapper.updateSchAttendance(schAttendance);
    }

    /**
     * 批量删除考勤记录
     *
     * @param attendanceIds 需要删除的考勤记录主键
     * @return 结果
     */
    @Override
    public int deleteSchAttendanceByAttendanceIds(Long[] attendanceIds) {
        return schAttendanceMapper.deleteSchAttendanceByAttendanceIds(attendanceIds);
    }

    /**
     * 删除考勤记录信息
     *
     * @param attendanceId 考勤记录主键
     * @return 结果
     */
    @Override
    public int deleteSchAttendanceByAttendanceId(Long attendanceId) {
        return schAttendanceMapper.deleteSchAttendanceByAttendanceId(attendanceId);
    }

    /**
     * 执行上班打卡
     * @param workerId 工人ID
     * @param workerName 工人姓名
     * @param clockInTime 打卡时间
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @return 打卡结果
     */
    @Override
    public Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName) {
        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

        // 创建考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setWorkerName(workerName);
        attendance.setRecordDate(today);
        attendance.setClockInTime(clockInTime);
        attendance.setSourceType("manual");
        attendance.setDeviceId(deviceId);
        attendance.setDeviceName(deviceName);

        // 获取员工排班信息来确定打卡状态
        String attendanceStatus = determineAttendanceStatus(clockInTime, workerId, today, true);
        attendance.setAttendanceStatus(attendanceStatus);
        
        // 计算迟到分钟数
        long lateMinutes = 0;
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);
        schedule.setScheduledDate(today);
        List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);
        
        if (!scheduleList.isEmpty()) {
            SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
            Date startTime = employeeSchedule.getStartTime();
            
            if (startTime != null) {
                // 计算迟到分钟数
                long diff = clockInTime.getTime() - startTime.getTime();
                lateMinutes = Math.max(0, diff / (1000 * 60));
            }
        }
        
        attendance.setLateMinutes(lateMinutes);

        // 检查是否已有今日记录，如果没有则新增，否则更新
        SchAttendance existingAttendance = new SchAttendance();
        existingAttendance.setWorkerId(workerId);
        existingAttendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceMapper.selectSchAttendanceList(existingAttendance);

        if (attendanceList.isEmpty()) {
            // 新增考勤记录
            schAttendanceMapper.insertSchAttendance(attendance);
        } else {
            // 更新考勤记录
            SchAttendance existing = attendanceList.get(0);
            existing.setClockInTime(clockInTime);
            existing.setWorkerName(workerName);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);
            existing.setAttendanceStatus(attendanceStatus);
            existing.setLateMinutes(lateMinutes);
            schAttendanceMapper.updateSchAttendance(existing);
            attendance = existing;
        }

        // 构建返回数据
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
     * @param workerId 工人ID
     * @param workerName 工人姓名
     * @param clockOutTime 打卡时间
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param overtimeHours 加班小时数
     * @return 打卡结果
     */
    @Override
    public Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName, Double overtimeHours) {
        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

        // 查找今日考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceMapper.selectSchAttendanceList(attendance);

        Map<String, Object> result = new HashMap<>();

        if (!attendanceList.isEmpty()) {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockOutTime(clockOutTime);
            existing.setWorkerName(workerName);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);

            // 使用通用方法确定考勤状态
            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, today, false);

            // 计算早退分钟数
            SchEmployeeSchedule schedule = new SchEmployeeSchedule();
            schedule.setWorkerId(workerId);
            schedule.setScheduledDate(today);
            List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

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

            // 优化刷新打卡时的状态计算逻辑
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
            schAttendanceMapper.updateSchAttendance(existing);

            // 构建返回数据
            result.put("attendanceId", existing.getAttendanceId());
            result.put("clockOutTime", clockOutTime);
            result.put("locationValid", true);
            result.put("distance", 0);
            result.put("isEarlyLeave", "early_leave".equals(existing.getAttendanceStatus()) || "late_early_leave".equals(existing.getAttendanceStatus()));
            result.put("leaveEarlyMinutes", existing.getLeaveEarlyMinutes() != null ? existing.getLeaveEarlyMinutes() : 0);
            result.put("overtimeHours", overtimeHours != null ? overtimeHours : 0.0);
            result.put("attendanceStatus", existing.getAttendanceStatus() != null ? existing.getAttendanceStatus() : "normal");
        } else {
            // 如果没有找到今日的考勤记录，创建一条新的记录并设置下班时间
            SchAttendance newAttendance = new SchAttendance();
            newAttendance.setWorkerId(workerId);
            newAttendance.setWorkerName(workerName);
            newAttendance.setRecordDate(today);
            newAttendance.setClockOutTime(clockOutTime);
            newAttendance.setSourceType("manual");
            newAttendance.setDeviceId(deviceId);
            newAttendance.setDeviceName(deviceName);

            // 使用通用方法确定考勤状态
            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, today, false);

            // 计算早退分钟数
            SchEmployeeSchedule schedule = new SchEmployeeSchedule();
            schedule.setWorkerId(workerId);
            schedule.setScheduledDate(today);
            List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

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

            schAttendanceMapper.insertSchAttendance(newAttendance);

            // 构建返回数据
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
     * @param workerId 工人ID
     * @return 考勤状态
     */
    @Override
    public Map<String, Object> getTodayAttendanceStatus(Long workerId) {
        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            throw new RuntimeException("日期格式错误", e);
        }

        // 查询今日考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceMapper.selectSchAttendanceList(attendance);

        Map<String, Object> data = new HashMap<>();
        data.put("date", today);

        if (!attendanceList.isEmpty()) {
            SchAttendance todayAttendance = attendanceList.get(0);
            data.put("clockInTime", todayAttendance.getClockInTime());
            data.put("clockOutTime", todayAttendance.getClockOutTime());

            // 设置打卡次数
            Long punchCount = 0L;

            // 设置打卡状态
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
            data.put("overtimeHours", todayAttendance.getOvertimeHours() != null ? todayAttendance.getOvertimeHours() : 0.0);
            data.put("punchCount", punchCount);
        } else {
            // 如果没有打卡记录，设置默认值
            data.put("clockInTime", null);
            data.put("clockOutTime", null);
            data.put("clockInStatus", "pending");
            data.put("clockOutStatus", "pending");
            data.put("attendanceStatus", "normal");
            data.put("lateMinutes", 0);
            data.put("leaveEarlyMinutes", 0);
            data.put("overtimeHours", 0.0);
            data.put("punchCount", 0);
        }

        return data;
    }

    /**
     * 验证打卡位置
     * @param latitude 纬度
     * @param longitude 经度
     * @return 位置验证结果
     */
    @Override
    public Map<String, Object> validatePunchLocation(double latitude, double longitude) {
        try {
            // 查询打卡规则（暂时使用rule_id=1的数据）
            SchPunchRule punchRule = schPunchRuleService.selectSchPunchRuleByRuleId(1L);
            if (punchRule == null) {
                throw new RuntimeException("未找到打卡规则");
            }
            
            // 检查打卡规则是否启用
            if (punchRule.getIsEnabled() != null && punchRule.getIsEnabled() == 0) {
                throw new RuntimeException("打卡规则已禁用");
            }
            
            // 获取预设的打卡地点坐标
            java.math.BigDecimal ruleLat = punchRule.getLocationLat();
            java.math.BigDecimal ruleLng = punchRule.getLocationLng();
            Long ruleRadius = punchRule.getLocationRadius();
            
            if (ruleLat == null || ruleLng == null || ruleRadius == null) {
                throw new RuntimeException("打卡规则配置不完整");
            }
            
            double centerLat = ruleLat.doubleValue();
            double centerLng = ruleLng.doubleValue();
            long radius = ruleRadius;
            
            // 计算用户位置与预设打卡地点的距离
            double distance = calculateDistance(latitude, longitude, centerLat, centerLng);
            
            // 验证位置是否在允许范围内
            boolean isValid = distance <= radius;
            
            // 构建返回数据
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
            
            // 返回打卡规则信息
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
     * @param clockTime 打卡时间
     * @param workerId 工人ID
     * @param recordDate 记录日期
     * @param isClockIn 是否上班打卡
     * @return 考勤状态
     */
    @Override
    public String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn) {
        // 查询员工当天的排班信息
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);
        schedule.setScheduledDate(recordDate);
        List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

        if (scheduleList.isEmpty()) {
            // 如果没有排班信息，默认为正常
            return "normal";
        }

        SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
        SchShift schShift = schShiftService.selectSchShiftByShiftId(employeeSchedule.getActualShiftId() != null ?
                employeeSchedule.getActualShiftId() : employeeSchedule.getShiftId());

        if (isClockIn) {
            // 上班打卡逻辑
            Date startTime = employeeSchedule.getStartTime();
            if (startTime == null) {
                // 如果排班开始时间为空，默认为正常
                return "normal";
            }

            Long lateThreshold = schShift != null ? schShift.getLateThreshold() : 0L;
            Date adjustedStartTime = DateUtils.addMinutes(startTime, lateThreshold.intValue());

            // 比较打卡时间与排班开始时间，精确到分钟
            Calendar clockCal = Calendar.getInstance();
            clockCal.setTime(clockTime);
            clockCal.set(Calendar.SECOND, 0);
            clockCal.set(Calendar.MILLISECOND, 0);

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(adjustedStartTime);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            // 如果打卡时间小于等于排班开始时间，则为正常，否则为迟到
            if (clockCal.compareTo(startCal) <= 0) {
                return "normal";
            } else {
                return "late";
            }
        } else {
            // 下班打卡逻辑
            Date endTime = employeeSchedule.getEndTime();
            if (endTime == null) {
                // 如果排班结束时间为空，默认为正常
                return "normal";
            }

            Long earlyLeaveThreshold = schShift != null ? schShift.getEarlyLeaveThreshold() : 0L;
            // 计算实际允许的最早下班时间 = 排班结束时间 - 早退阈值
            Date earliestAllowedClockOutTime = DateUtils.addMinutes(endTime, -earlyLeaveThreshold.intValue());

            // 比较打卡时间与允许的最早下班时间，精确到分钟
            Calendar clockCal = Calendar.getInstance();
            clockCal.setTime(clockTime);
            clockCal.set(Calendar.SECOND, 0);
            clockCal.set(Calendar.MILLISECOND, 0);

            Calendar earliestCal = Calendar.getInstance();
            earliestCal.setTime(earliestAllowedClockOutTime);
            earliestCal.set(Calendar.SECOND, 0);
            earliestCal.set(Calendar.MILLISECOND, 0);

            // 如果打卡时间小于允许的最早下班时间，则为早退，否则为正常
            if (clockCal.before(earliestCal)) {
                return "early_leave";
            } else {
                return "normal";
            }
        }
    }

    /**
     * 计算两个经纬度坐标之间的距离（使用Haversine公式）
     * @param lat1 纬度1
     * @param lng1 经度1
     * @param lat2 纬度2
     * @param lng2 经度2
     * @return 距离（米）
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
