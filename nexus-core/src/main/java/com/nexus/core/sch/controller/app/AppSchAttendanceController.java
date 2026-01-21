package com.nexus.core.sch.controller.app;

import com.nexus.common.core.domain.R;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.core.sch.domain.*;
import com.nexus.core.sch.service.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * app打卡考勤控制器
 *
 * @author wanghuan
 * @date 2026/1/7
 */
@Api(tags = "app打卡Controller")
@RestController
@RequestMapping("/app/sch")
public class AppSchAttendanceController {

    @Autowired
    private ISchAttendanceService schAttendanceService;

    @Autowired
    private ISchEmployeeScheduleService schEmployeeScheduleService;

    @Autowired
    private ISchPunchRuleService schPunchRuleService;

    @Autowired
    private ISchWorkerService schWorkerService;

    @Autowired
    private ISchShiftService schShiftService;

    /**
     * 获取今日排班
     */
    @ApiOperation("获取今日排班")
    @GetMapping("/employee-schedule/today")
    public R<?> getTodaySchedule() {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            return R.ok();
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            return R.fail("日期格式错误");
        }

        // 查询今日排班信息
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);
        schedule.setScheduledDate(today);
        List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

        if (!scheduleList.isEmpty()) {
            SchEmployeeSchedule todaySchedule = scheduleList.get(0); // 取第一条记录

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("scheduleId", todaySchedule.getScheduleId());
            data.put("planId", todaySchedule.getPlanId());
            data.put("workerId", todaySchedule.getWorkerId());
            data.put("workerName", todaySchedule.getWorkerName());
            data.put("teamId", todaySchedule.getTeamId());
            data.put("teamName", todaySchedule.getTeamName());
            data.put("shiftId", todaySchedule.getShiftId());
            data.put("shiftName", todaySchedule.getShiftName());
            data.put("scheduledDate", todaySchedule.getScheduledDate());
            data.put("startTime", todaySchedule.getStartTime());
            data.put("endTime", todaySchedule.getEndTime());
            data.put("scheduleStatus", todaySchedule.getScheduleStatus());
            data.put("isWorkingDay", todaySchedule.getIsWorkingDay() != null && todaySchedule.getIsWorkingDay() == 1);
            data.put("remark", null);

            return R.ok(data);
        } else {
            // 如果没有排班，返回空数据
            return R.ok();
        }
    }

    /**
     * 获取今日打卡状态
     */
    @ApiOperation("获取今日打卡状态")
    @GetMapping("/attendance/today-status")
    public R<?> getTodayStatus() {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            return R.ok();
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            return R.fail("日期格式错误");
        }

        // 查询今日考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceService.selectSchAttendanceList(attendance);

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

        return R.ok(data);
    }

    /**
     * 上班打卡
     */
    @ApiOperation("上班打卡")
    @PostMapping("/attendance/clock-in")
    public R<?> clockIn(@RequestBody Map<String, Object> params) {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回错误
            return R.fail("找不到对应的工人信息");
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        Date now = new Date();

        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            return R.fail("日期格式错误");
        }

        // 创建考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setRecordDate(today);
        attendance.setClockInTime(now);
        attendance.setSourceType("manual"); // 手动打卡
        attendance.setDeviceId((String) params.get("deviceId"));
        attendance.setDeviceName((String) params.get("deviceName"));

        // 获取员工排班信息来确定打卡状态
        String attendanceStatus = determineAttendanceStatus(now, workerId, today, true);
        attendance.setAttendanceStatus(attendanceStatus);

        // 检查是否已有今日记录，如果没有则新增，否则更新
        SchAttendance existingAttendance = new SchAttendance();
        existingAttendance.setWorkerId(workerId);
        existingAttendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceService.selectSchAttendanceList(existingAttendance);

        if (attendanceList.isEmpty()) {
            // 新增考勤记录
            schAttendanceService.insertSchAttendance(attendance);
        } else {
            // 更新考勤记录
            SchAttendance existing = attendanceList.get(0);
            existing.setClockInTime(now);
            existing.setDeviceId((String) params.get("deviceId"));
            existing.setDeviceName((String) params.get("deviceName"));
            existing.setAttendanceStatus(attendanceStatus); // 同样更新现有记录的状态
            schAttendanceService.updateSchAttendance(existing);
            attendance = existing;
        }

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("attendanceId", attendance.getAttendanceId());
        data.put("clockInTime", now);
        data.put("locationValid", true); // 暂时默认位置有效
        data.put("distance", 0); // 暂时默认距离
        data.put("isLate", "late".equals(attendance.getAttendanceStatus())); // 根据考勤状态判断是否迟到
        data.put("lateMinutes", 0); // 暂时默认迟到分钟为0
        data.put("nextClockOutTime", null); // 暂时返回空

        return R.ok(data, "打卡成功");
    }

    /**
     * 下班打卡
     */
    @ApiOperation("下班打卡")
    @PostMapping("/attendance/clock-out")
    public R<?> clockOut(@RequestBody Map<String, Object> params) {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回错误
            return R.fail("找不到对应的工人信息");
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        Date now = new Date();

        // 获取当前日期，格式为 yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        Date today = null;
        try {
            today = sdf.parse(todayStr);
        } catch (Exception e) {
            return R.fail("日期格式错误");
        }

        // 查找今日考勤记录
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setRecordDate(today);
        List<SchAttendance> attendanceList = schAttendanceService.selectSchAttendanceList(attendance);

        if (!attendanceList.isEmpty()) {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockOutTime(now);
            existing.setDeviceId((String) params.get("deviceId"));
            existing.setDeviceName((String) params.get("deviceName"));

            // 使用通用方法确定考勤状态
            String clockOutAttendanceStatus = determineAttendanceStatus(now, workerId, today, false);

            // 计算早退分钟数
            // 查询员工当天的排班信息
            SchEmployeeSchedule schedule = new SchEmployeeSchedule();
            schedule.setWorkerId(workerId);
            schedule.setScheduledDate(today);
            List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

            long leaveEarlyMinutes = 0;
            if (!scheduleList.isEmpty()) {
                SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
                Date endTime = employeeSchedule.getEndTime();

                if (endTime != null) {
                    // 计算早退分钟数
                    long diff = endTime.getTime() - now.getTime();
                    leaveEarlyMinutes = Math.max(0, diff / (1000 * 60));
                }
            }

            existing.setLeaveEarlyMinutes(leaveEarlyMinutes);

            // 如果之前是迟到状态，且现在也是早退，则设置为迟到早退复合状态
            String attendanceStatus;
            if ("late".equals(existing.getAttendanceStatus()) && "early_leave".equals(clockOutAttendanceStatus)) {
                attendanceStatus = "late_early_leave";
            } else if ("early_leave".equals(clockOutAttendanceStatus)) {
                attendanceStatus = "early_leave";
            } else {
                attendanceStatus = clockOutAttendanceStatus;
            }

            existing.setAttendanceStatus(attendanceStatus);

            schAttendanceService.updateSchAttendance(existing);

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("attendanceId", existing.getAttendanceId());
            data.put("clockOutTime", now);
            data.put("locationValid", true); // 暂时默认位置有效
            data.put("distance", 0); // 暂时默认距离
            data.put("isEarlyLeave", "early_leave".equals(existing.getAttendanceStatus()) || "late_early_leave".equals(existing.getAttendanceStatus()));
            data.put("leaveEarlyMinutes", existing.getLeaveEarlyMinutes() != null ? existing.getLeaveEarlyMinutes() : 0);
            data.put("overtimeHours", params.get("overtimeHours") != null ? params.get("overtimeHours") : 0.0);
            data.put("attendanceStatus", existing.getAttendanceStatus() != null ? existing.getAttendanceStatus() : "normal");

            return R.ok(data, "打卡成功");
        } else {
            // 如果没有找到今日的考勤记录，创建一条新的记录并设置下班时间
            SchAttendance newAttendance = new SchAttendance();
            newAttendance.setWorkerId(workerId);
            newAttendance.setRecordDate(today);
            newAttendance.setClockOutTime(now);
            newAttendance.setSourceType("manual");
            newAttendance.setDeviceId((String) params.get("deviceId"));
            newAttendance.setDeviceName((String) params.get("deviceName"));

            // 使用通用方法确定考勤状态
            String clockOutAttendanceStatus = determineAttendanceStatus(now, workerId, today, false);

            // 计算早退分钟数
            // 查询员工当天的排班信息
            SchEmployeeSchedule schedule = new SchEmployeeSchedule();
            schedule.setWorkerId(workerId);
            schedule.setScheduledDate(today);
            List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

            long leaveEarlyMinutes = 0;
            if (!scheduleList.isEmpty()) {
                SchEmployeeSchedule employeeSchedule = scheduleList.get(0);
                Date endTime = employeeSchedule.getEndTime();

                if (endTime != null) {
                    // 计算早退分钟数
                    long diff = endTime.getTime() - now.getTime();
                    leaveEarlyMinutes = Math.max(0, diff / (1000 * 60));
                }
            }

            newAttendance.setLeaveEarlyMinutes(leaveEarlyMinutes);
            newAttendance.setAttendanceStatus(clockOutAttendanceStatus);

            schAttendanceService.insertSchAttendance(newAttendance);

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("attendanceId", newAttendance.getAttendanceId());
            data.put("clockOutTime", now);
            data.put("locationValid", true); // 暂时默认位置有效
            data.put("distance", 0); // 暂时默认距离
            data.put("isEarlyLeave", "early_leave".equals(newAttendance.getAttendanceStatus()));
            data.put("leaveEarlyMinutes", newAttendance.getLeaveEarlyMinutes() != null ? newAttendance.getLeaveEarlyMinutes() : 0);
            data.put("overtimeHours", params.get("overtimeHours") != null ? params.get("overtimeHours") : 0.0);
            data.put("attendanceStatus", newAttendance.getAttendanceStatus() != null ? newAttendance.getAttendanceStatus() : "normal");

            return R.ok(data, "打卡成功");
        }
    }

    /**
     * 获取月度考勤统计
     */
    @ApiOperation("获取月度考勤统计")
    @GetMapping("/attendance/monthly/{month}")
    public R<?> getMonthlyAttendance(@PathVariable String month) {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            Map<String, Object> data = new HashMap<>();
            data.put("month", month);
            data.put("totalDays", 0);
            data.put("workDays", 0);
            data.put("attendanceDays", 0);
            data.put("leaveDays", 0);
            data.put("lateCount", 0);
            data.put("earlyLeaveCount", 0);
            data.put("absentCount", 0);
            data.put("overtimeHours", 0.0);
            data.put("attendanceRate", "0%");
            data.put("details", java.util.Collections.emptyList());
            return R.ok(data);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("month", month);
        data.put("totalDays", 30); // 暂时返回默认值
        data.put("workDays", 22); // 暂时返回默认值
        data.put("attendanceDays", 20); // 暂时返回默认值
        data.put("leaveDays", 2); // 暂时返回默认值
        data.put("lateCount", 1); // 暂时返回默认值
        data.put("earlyLeaveCount", 0); // 暂时返回默认值
        data.put("absentCount", 0); // 暂时返回默认值
        data.put("overtimeHours", 12.5); // 暂时返回默认值
        data.put("attendanceRate", "90.0%"); // 暂时返回默认值
        data.put("details", java.util.Collections.emptyList()); // 暂时返回空列表

        return R.ok(data);
    }

    /**
     * 获取我的排班列表
     */
    @ApiOperation("获取我的排班列表")
    @GetMapping("/employee-schedule/my-schedule")
    public R<?> getMyScheduleList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            Map<String, Object> data = new HashMap<>();
            data.put("total", 0);
            data.put("rows", java.util.Collections.emptyList());
            return R.ok(data);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 构建查询条件
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);

        // 模拟查询排班列表
        List<SchEmployeeSchedule> scheduleList = schEmployeeScheduleService.selectSchEmployeeScheduleList(schedule);

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("total", scheduleList.size());
        data.put("rows", scheduleList); // 直接返回查询结果

        return R.ok(data);
    }

    /**
     * 验证位置
     */
    @ApiOperation("验证位置")
    @PostMapping("/attendance/validate-location")
    public R<?> validateLocation(@RequestBody Map<String, Object> params) {
        try {
            // 获取用户提交的经纬度
            Object latitudeObj = params.get("latitude");
            Object longitudeObj = params.get("longitude");
            
            if (latitudeObj == null || longitudeObj == null) {
                return R.fail("缺少经纬度参数");
            }
            
            double userLat = Double.parseDouble(latitudeObj.toString());
            double userLng = Double.parseDouble(longitudeObj.toString());
            
            // 查询打卡规则（暂时使用rule_id=1的数据）
            SchPunchRule punchRule = schPunchRuleService.selectSchPunchRuleByRuleId(1L);
            if (punchRule == null) {
                return R.fail("未找到打卡规则");
            }
            
            // 检查打卡规则是否启用
            if (punchRule.getIsEnabled() != null && punchRule.getIsEnabled() == 0) {
                return R.fail("打卡规则已禁用");
            }
            
            // 获取预设的打卡地点坐标
            java.math.BigDecimal ruleLat = punchRule.getLocationLat();
            java.math.BigDecimal ruleLng = punchRule.getLocationLng();
            Long ruleRadius = punchRule.getLocationRadius();
            
            if (ruleLat == null || ruleLng == null || ruleRadius == null) {
                return R.fail("打卡规则配置不完整");
            }
            
            double centerLat = ruleLat.doubleValue();
            double centerLng = ruleLng.doubleValue();
            long radius = ruleRadius; // 半径（米）
            
            // 计算用户位置与预设打卡地点的距离
            double distance = calculateDistance(userLat, userLng, centerLat, centerLng);
            
            // 验证位置是否在允许范围内
            boolean isValid = distance <= radius;
            
            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("isValid", isValid);
            data.put("distance", Math.round(distance)); // 距离（米），四舍五入
            data.put("radius", radius); // 允许的半径范围（米）
            
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
            
            return R.ok(data);
        } catch (NumberFormatException e) {
            return R.fail("经纬度参数格式错误");
        } catch (Exception e) {
            return R.fail("位置验证失败：" + e.getMessage());
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
        final double EARTH_RADIUS = 6371000; // 地球半径（米）
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }

    /**
     * 获取打卡规则
     */
    @ApiOperation("获取打卡规则")
    @GetMapping("/punch-rule/list")
    public R<?> getPunchRuleList() {
        // 查询打卡规则列表
        List<SchPunchRule> punchRuleList = schPunchRuleService.selectSchPunchRuleList(new SchPunchRule());

        return R.ok(punchRuleList);
    }

    /**
     * 获取最近打卡记录
     */
    @ApiOperation("获取最近打卡记录")
    @GetMapping("/attendance/recent")
    public R<?> getRecentAttendance(@RequestParam(defaultValue = "10") Integer limit) {
        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            return R.ok(java.util.Collections.emptyList());
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 构建查询条件
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);

        // 查询考勤记录列表
        List<SchAttendance> attendanceList = schAttendanceService.selectSchAttendanceList(attendance);

        // 限制返回数量
        if (attendanceList.size() > limit) {
            attendanceList = attendanceList.subList(0, limit);
        }

        return R.ok(attendanceList);
    }

    /**
     * 提交补卡申请
     */
    @ApiOperation("提交补卡申请")
    @PostMapping("/supplement-apply")
    public R<?> submitSupplementApply(@RequestBody Map<String, Object> params) {
        // 根据要求，暂时返回成功信息，不实现具体逻辑
        Map<String, Object> data = new HashMap<>();
        data.put("applyId", 3001);
        data.put("applyNo", "BC20260107001");
        data.put("applyDate", params.get("applyDate"));
        data.put("applyType", params.get("applyType"));
        data.put("applyStatus", "pending");
        data.put("createTime", new Date());

        return R.ok(data, "补卡申请提交成功");
    }

    /**
     * 获取补卡申请列表
     */
    @ApiOperation("获取补卡申请列表")
    @GetMapping("/supplement-apply/list")
    public R<?> getSupplementApplyList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String applyStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 根据要求，暂时返回成功信息，不实现具体逻辑
        Map<String, Object> data = new HashMap<>();
        data.put("total", 0);
        data.put("rows", java.util.Collections.emptyList());

        return R.ok(data);
    }

    /**
     * 获取外勤打卡记录
     */
    @ApiOperation("获取外勤打卡记录")
    @GetMapping("/outdoor-punch/list")
    public R<?> getOutdoorPunchList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 根据要求，暂时返回成功信息，不实现具体逻辑
        Map<String, Object> data = new HashMap<>();
        data.put("total", 0);
        data.put("rows", java.util.Collections.emptyList());

        return R.ok(data);
    }

    /**
     * 获取我的考勤记录
     */
    @ApiOperation("获取我的考勤记录")
    @GetMapping("/attendance/my")
    public R<?> getMyAttendance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String attendanceStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        Long userId = SecurityUtils.getUserId();

        // 通过系统用户ID查询工人信息
        SchWorker schWorker = new SchWorker();
        schWorker.setUserId(userId);
        List<SchWorker> workerList = schWorkerService.selectSchWorkerList(schWorker);
        if (workerList.isEmpty()) {
            // 如果找不到对应的工人信息，返回空数据
            Map<String, Object> data = new HashMap<>();
            data.put("total", 0);
            data.put("rows", java.util.Collections.emptyList());
            return R.ok(data);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        // 构建查询条件
        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);

        // 查询考勤记录列表
        List<SchAttendance> attendanceList = schAttendanceService.selectSchAttendanceList(attendance);

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("total", attendanceList.size());
        data.put("rows", attendanceList);

        return R.ok(data);
    }

    /**
     * 根据打卡时间和排班时间确定考勤状态
     */
    private String determineAttendanceStatus(Date clockTime, Long workerId, Date recordDate, boolean isClockIn) {
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
            clockCal.set(Calendar.SECOND, 0); // 将秒数设为0
            clockCal.set(Calendar.MILLISECOND, 0); // 将毫秒设为0

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(adjustedStartTime);
            startCal.set(Calendar.SECOND, 0); // 将秒数设为0
            startCal.set(Calendar.MILLISECOND, 0); // 将毫秒设为0

            // 如果打卡时间小于等于排班开始时间，则为正常，否则为迟到
            if (clockCal.compareTo(startCal) <= 0) {
                return "normal"; // 在规定时间前或相同时打卡为正常
            } else {
                return "late"; // 迟到
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
            clockCal.set(Calendar.SECOND, 0); // 将秒数设为0
            clockCal.set(Calendar.MILLISECOND, 0); // 将毫秒设为0

            Calendar earliestCal = Calendar.getInstance();
            earliestCal.setTime(earliestAllowedClockOutTime);
            earliestCal.set(Calendar.SECOND, 0); // 将秒数设为0
            earliestCal.set(Calendar.MILLISECOND, 0); // 将毫秒设为0

            // 如果打卡时间小于允许的最早下班时间，则为早退，否则为正常
            if (clockCal.before(earliestCal)) {
                return "early_leave"; // 早退
            } else {
                return "normal"; // 正常下班
            }
        }
    }
}