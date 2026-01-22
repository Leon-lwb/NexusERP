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

        try {
            Map<String, Object> data = schAttendanceService.getTodayAttendanceStatus(workerId);
            return R.ok(data);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
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

        try {
            Date now = new Date();
            Map<String, Object> data = schAttendanceService.performClockIn(
                    workerId,
                    currentWorker.getUserName(),
                    now,
                    (String) params.get("deviceId"),
                    (String) params.get("deviceName")
            );
            return R.ok(data, "打卡成功");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
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

        try {
            Date now = new Date();
            Double overtimeHours = params.get("overtimeHours") != null ? Double.parseDouble(params.get("overtimeHours").toString()) : 0.0;
            Map<String, Object> data = schAttendanceService.performClockOut(
                    workerId,
                    currentWorker.getUserName(),
                    now,
                    (String) params.get("deviceId"),
                    (String) params.get("deviceName"),
                    overtimeHours
            );
            return R.ok(data, "打卡成功");
        } catch (Exception e) {
            return R.fail(e.getMessage());
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
            
            Map<String, Object> data = schAttendanceService.validatePunchLocation(userLat, userLng);
            return R.ok(data);
        } catch (NumberFormatException e) {
            return R.fail("经纬度参数格式错误");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
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
}