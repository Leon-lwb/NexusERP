package com.nexus.erp.sch.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchAttendanceService;
import com.nexus.erp.sch.service.ISchEmployeeScheduleService;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import com.nexus.erp.sch.service.ISchShiftService;
import com.nexus.erp.sch.service.ISchWorkerService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * app打卡考勤控制器
 *
 * @author wanghuan
 * @date 2026/1/7
 */
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/app/sch")
public class AppSchAttendanceController {

    private final ISchAttendanceService schAttendanceService;
    private final ISchEmployeeScheduleService schEmployeeScheduleService;
    private final ISchPunchRuleService schPunchRuleService;
    private final ISchWorkerService schWorkerService;
    private final ISchShiftService schShiftService;
    private final SchWorkerMapper workerMapper;
    private final SchAttendanceMapper attendanceMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;

    /**
     * 获取今日排班
     * <p>查询当前登录工人当天的排班信息，包括班次、时间、工作状态等</p>
     *
     * @return 今日排班详情数据
     */
    @GetMapping("/employee-schedule/today")
    public R<?> getTodaySchedule() {
        // 获取当前登录用户 (简化处理, 使用第一条工人记录)
        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
            return R.ok();
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        LocalDate today = LocalDate.now();
        Date todayDate = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LambdaQueryWrapper<SchEmployeeSchedule> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
        lqw.eq(SchEmployeeSchedule::getScheduledDate, todayDate);
        List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(lqw);

        if (!scheduleList.isEmpty()) {
            SchEmployeeSchedule todaySchedule = scheduleList.get(0);
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
            return R.ok();
        }
    }

    /**
     * 获取今日打卡状态
     * <p>查询当前登录工人当天的考勤打卡状态，包括上下班打卡时间、迟到早退情况等</p>
     *
     * @return 今日打卡状态数据
     */
    @GetMapping("/attendance/today-status")
    public R<?> getTodayStatus() {
        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
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
     * <p>记录工人的上班打卡时间、设备信息，并计算考勤状态</p>
     *
     * @param params 打卡参数，包含 deviceId（设备ID）、deviceName（设备名称）等
     * @return 打卡结果数据
     */
    @PostMapping("/attendance/clock-in")
    public R<?> clockIn(@RequestBody Map<String, Object> params) {
        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
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
            return R.ok(data);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 下班打卡
     * <p>记录工人的下班打卡时间、设备信息及加班时长，并计算考勤状态</p>
     *
     * @param params 打卡参数，包含 deviceId（设备ID）、deviceName（设备名称）、overtimeHours（加班时长）等
     * @return 打卡结果数据
     */
    @PostMapping("/attendance/clock-out")
    public R<?> clockOut(@RequestBody Map<String, Object> params) {
        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
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
            return R.ok(data);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取月度考勤统计
     * <p>统计指定月份工人的出勤天数、请假天数、迟到次数、早退次数、加班时长及出勤率</p>
     *
     * @param month 月份，格式为 YYYY-MM
     * @return 月度考勤统计数据
     */
    @GetMapping("/attendance/monthly/{month}")
    public R<?> getMonthlyAttendance(@PathVariable String month) {
        if (!month.matches("\\d{4}-\\d{2}")) {
            return R.fail("月份格式错误，应为YYYY-MM");
        }

        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
            Map<String, Object> emptyData = buildEmptyMonthlyData(month);
            return R.ok(emptyData);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        String[] parts = month.split("-");
        int year = Integer.parseInt(parts[0]);
        int monthInt = Integer.parseInt(parts[1]) - 1;

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, monthInt, 1);
        Date startDate = calendar.getTime();

        int lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(year, monthInt, lastDay);
        Date endDate = calendar.getTime();

        int totalDays = lastDay;
        int workDays = calculateWorkDays(startDate, endDate);

        LambdaQueryWrapper<SchAttendance> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchAttendance::getWorkerId, workerId);
        List<SchAttendance> attendanceList = attendanceMapper.selectList(lqw);

        List<SchAttendance> monthlyAttendanceList = new ArrayList<>();
        for (SchAttendance attendance : attendanceList) {
            if (attendance.getRecordDate() != null) {
                Calendar attendanceCal = Calendar.getInstance();
                attendanceCal.setTime(attendance.getRecordDate());
                if (attendanceCal.get(Calendar.YEAR) == year && attendanceCal.get(Calendar.MONTH) == monthInt) {
                    monthlyAttendanceList.add(attendance);
                }
            }
        }

        int attendanceDays = 0;
        int leaveDays = 0;
        int lateCount = 0;
        int earlyLeaveCount = 0;
        int absentCount = 0;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;

        Map<String, Map<String, Object>> attendanceData = new HashMap<>();

        for (SchAttendance attendance : monthlyAttendanceList) {
            String status = attendance.getAttendanceStatus();

            if (attendance.getClockInTime() != null || attendance.getClockOutTime() != null) {
                attendanceDays++;
            }

            if ("leave".equals(status)) {
                leaveDays++;
            }

            if ("late".equals(status) || "late_early_leave".equals(status)) {
                lateCount++;
            }

            if ("early_leave".equals(status) || "late_early_leave".equals(status)) {
                earlyLeaveCount++;
            }

            if ("absent".equals(status)) {
                absentCount++;
            }

            if (attendance.getOvertimeHours() != null) {
                totalOvertimeHours = totalOvertimeHours.add(attendance.getOvertimeHours());
            }

            if (attendance.getRecordDate() != null) {
                String dateStr = attendance.getRecordDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                Map<String, Object> dayDetail = new HashMap<>();
                dayDetail.put("attendanceStatus", status);
                dayDetail.put("clockInTime", attendance.getClockInTime());
                dayDetail.put("clockOutTime", attendance.getClockOutTime());
                dayDetail.put("lateMinutes", attendance.getLateMinutes());
                dayDetail.put("leaveEarlyMinutes", attendance.getLeaveEarlyMinutes());
                dayDetail.put("overtimeHours", attendance.getOvertimeHours());
                attendanceData.put(dateStr, dayDetail);
            }
        }

        String attendanceRate;
        if (workDays > 0) {
            double rate = (double) attendanceDays / workDays * 100;
            attendanceRate = String.format("%.1f%%", rate);
        } else {
            attendanceRate = "0%";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("month", month);
        data.put("totalDays", totalDays);
        data.put("workDays", workDays);
        data.put("attendanceDays", attendanceDays);
        data.put("leaveDays", leaveDays);
        data.put("lateCount", lateCount);
        data.put("earlyLeaveCount", earlyLeaveCount);
        data.put("absentCount", absentCount);
        data.put("overtimeHours", totalOvertimeHours.doubleValue());
        data.put("attendanceRate", attendanceRate);
        data.put("attendanceData", attendanceData);

        return R.ok(data);
    }

    /**
     * 获取我的排班列表
     * <p>查询当前登录工人的排班记录列表</p>
     *
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @param pageNum   页码，默认为1
     * @param pageSize  每页条数，默认为10
     * @return 排班列表数据
     */
    @GetMapping("/employee-schedule/my-schedule")
    public R<?> getMyScheduleList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
            Map<String, Object> emptyData = new HashMap<>();
            emptyData.put("total", 0);
            emptyData.put("rows", java.util.Collections.emptyList());
            return R.ok(emptyData);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        LambdaQueryWrapper<SchEmployeeSchedule> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchEmployeeSchedule::getWorkerId, workerId);
        List<SchEmployeeSchedule> scheduleList = employeeScheduleMapper.selectList(lqw);

        Map<String, Object> data = new HashMap<>();
        data.put("total", scheduleList.size());
        data.put("rows", scheduleList);
        return R.ok(data);
    }

    /**
     * 验证打卡位置
     * <p>验证用户当前位置是否在允许的打卡范围内</p>
     *
     * @param params 位置参数，包含 latitude（纬度）、longitude（经度）
     * @return 位置验证结果
     */
    @PostMapping("/attendance/validate-location")
    public R<?> validateLocation(@RequestBody Map<String, Object> params) {
        try {
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
     * 获取打卡规则列表
     * <p>查询系统配置的所有打卡规则</p>
     *
     * @return 打卡规则列表
     */
    @GetMapping("/punch-rule/list")
    public R<?> getPunchRuleList() {
        List<SchPunchRuleVo> punchRuleList = schPunchRuleService.selectList(new SchPunchRuleBo());
        return R.ok(punchRuleList);
    }

    /**
     * 获取最近打卡记录
     * <p>查询当前登录工人最近的考勤打卡记录</p>
     *
     * @param limit 返回记录数量限制，默认为10条
     * @return 最近打卡记录列表
     */
    @GetMapping("/attendance/recent")
    public R<?> getRecentAttendance(@RequestParam(defaultValue = "10") Integer limit) {
        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
            return R.ok(java.util.Collections.emptyList());
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        LambdaQueryWrapper<SchAttendance> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchAttendance::getWorkerId, workerId);
        lqw.orderByDesc(SchAttendance::getRecordDate);
        List<SchAttendance> attendanceList = attendanceMapper.selectList(lqw);

        if (attendanceList.size() > limit) {
            attendanceList = attendanceList.subList(0, limit);
        }
        return R.ok(attendanceList);
    }

    /**
     * 提交补卡申请
     * <p>提交考勤补卡申请，包含补卡日期、补卡类型等信息</p>
     *
     * @param params 补卡申请参数，包含 applyDate（申请日期）、applyType（申请类型）等
     * @return 补卡申请结果
     */
    @PostMapping("/supplement-apply")
    public R<?> submitSupplementApply(@RequestBody Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        data.put("applyId", 3001);
        data.put("applyNo", "BC20260107001");
        data.put("applyDate", params.get("applyDate"));
        data.put("applyType", params.get("applyType"));
        data.put("applyStatus", "pending");
        data.put("createTime", new Date());
        return R.ok(data);
    }

    /**
     * 获取补卡申请列表
     * <p>查询当前登录工人的补卡申请记录</p>
     *
     * @param startDate  开始日期（可选）
     * @param endDate    结束日期（可选）
     * @param applyStatus 申请状态（可选）
     * @param pageNum    页码，默认为1
     * @param pageSize   每页条数，默认为10
     * @return 补卡申请列表数据
     */
    @GetMapping("/supplement-apply/list")
    public R<?> getSupplementApplyList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String applyStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", 0);
        data.put("rows", java.util.Collections.emptyList());
        return R.ok(data);
    }

    /**
     * 获取外勤打卡记录
     * <p>查询当前登录工人的外勤打卡记录</p>
     *
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @param pageNum   页码，默认为1
     * @param pageSize  每页条数，默认为10
     * @return 外勤打卡记录列表数据
     */
    @GetMapping("/outdoor-punch/list")
    public R<?> getOutdoorPunchList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", 0);
        data.put("rows", java.util.Collections.emptyList());
        return R.ok(data);
    }

    /**
     * 获取我的考勤记录
     * <p>查询当前登录工人的考勤记录列表，支持按日期范围和考勤状态筛选</p>
     *
     * @param startDate       开始日期（可选）
     * @param endDate         结束日期（可选）
     * @param attendanceStatus 考勤状态（可选）
     * @param pageNum         页码，默认为1
     * @param pageSize        每页条数，默认为20
     * @return 考勤记录列表数据
     */
    @GetMapping("/attendance/my")
    public R<?> getMyAttendance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String attendanceStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        List<SchWorker> workerList = getWorkerList();
        if (workerList.isEmpty()) {
            Map<String, Object> emptyData = new HashMap<>();
            emptyData.put("total", 0);
            emptyData.put("rows", java.util.Collections.emptyList());
            return R.ok(emptyData);
        }
        SchWorker currentWorker = workerList.get(0);
        Long workerId = currentWorker.getWorkerId();

        LambdaQueryWrapper<SchAttendance> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchAttendance::getWorkerId, workerId);
        List<SchAttendance> attendanceList = attendanceMapper.selectList(lqw);

        Map<String, Object> data = new HashMap<>();
        data.put("total", attendanceList.size());
        data.put("rows", attendanceList);
        return R.ok(data);
    }

    private List<SchWorker> getWorkerList() {
        // 简化处理：获取所有工人（实际应通过SecurityUtils获取当前用户）
        return workerMapper.selectList(Wrappers.lambdaQuery());
    }

    private Map<String, Object> buildEmptyMonthlyData(String month) {
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
        data.put("attendanceData", new HashMap<>());
        return data;
    }

    private int calculateWorkDays(Date startDate, Date endDate) {
        int count = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.add(Calendar.DAY_OF_MONTH, 1);

        while (calendar.before(endCal)) {
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                count++;
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return count;
    }
}
