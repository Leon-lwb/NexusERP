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
import com.nexus.erp.cv.service.CvSubjectHelper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.domain.vo.SysOssVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
     * @param latitude   打卡纬度（可为 null）
     * @param longitude  打卡经度（可为 null）
     * @param address   打卡地址/定位描述（可为 null）
     * @return 打卡结果数据，包含考勤ID、打卡时间、是否迟到、迟到分钟数等
     */
    @Override
    public Map<String, Object> performClockIn(Long workerId, String workerName, Date clockInTime, String deviceId, String deviceName,
                                              BigDecimal latitude, BigDecimal longitude, String address, BigDecimal accuracy) {
        LocalDate todayLd = LocalDate.now();
        // 打卡围栏校验(ERP-SCH-03): 必须在落库主流程内计算,不能只依赖前端主动调 /validate-location,
        // 否则绕过该端点即可异地打卡(原实现硬编码 locationValid=true)。
        Long distance = requireLocationWithinFence(latitude, longitude);
        // 排班匹配(含前一日跨天班次),record_date 取命中排班的排班日期,避免跨天班次被拆成两天(ERP-SCH-05)
        SchEmployeeSchedule matched = findScheduleForPunch(workerId, clockInTime, todayLd);
        Date recordDate = matched != null ? truncateToDay(matched.getScheduledDate()) : startOfDay(todayLd);

        SchAttendance attendance = new SchAttendance();
        attendance.setWorkerId(workerId);
        attendance.setWorkerName(workerName);
        attendance.setRecordDate(recordDate);
        attendance.setScheduleId(matched != null ? matched.getScheduleId() : null);
        attendance.setClockInTime(clockInTime);
        attendance.setSourceType("manual");
        attendance.setDeviceId(deviceId);
        attendance.setDeviceName(deviceName);
        attendance.setClockInLatitude(latitude);
        attendance.setClockInLongitude(longitude);
        attendance.setClockInAddress(address);
        // ERP-CV-05: 前端已上报定位精度, 原后端无列/无字段直接丢弃, 现落 clock_in_accuracy 供证据强度评估
        attendance.setClockInAccuracy(accuracy);

        String attendanceStatus = determineAttendanceStatus(clockInTime, workerId, recordDate, true);
        attendance.setAttendanceStatus(attendanceStatus);

        long lateMinutes = 0;
        if (matched != null && matched.getStartTime() != null) {
            long diff = clockInTime.getTime() - matched.getStartTime().getTime();
            lateMinutes = Math.max(0, diff / (1000 * 60));
        }

        attendance.setLateMinutes(lateMinutes);

        LambdaQueryWrapper<SchAttendance> existLqw = Wrappers.lambdaQuery();
        existLqw.eq(SchAttendance::getWorkerId, workerId);
        existLqw.eq(SchAttendance::getRecordDate, recordDate);
        List<SchAttendance> attendanceList = baseMapper.selectList(existLqw);

        if (attendanceList.isEmpty()) {
            try {
                baseMapper.insert(attendance);
            } catch (DuplicateKeyException e) {
                // ERP-SCH-09: "先查后写"下并发连点会撞 uk_worker_date(tenant_id, worker_id, record_date),
                // 原实现直接冒泡成 500。捕获后按"记录已存在"合并覆盖(与下面更新分支同语义),
                // 让用户看到正常打卡结果而不是服务器错误。
                log.warn("打卡并发写入冲突, 按已存在记录合并: workerId={}, recordDate={}", workerId, recordDate);
                SchAttendance concurrent = baseMapper.selectList(existLqw).stream().findFirst().orElse(null);
                if (concurrent == null) {
                    throw new ServiceException("打卡记录写入冲突，请重试");
                }
                concurrent.setClockInAccuracy(accuracy);
                applyClockInFields(concurrent, workerName, clockInTime, deviceId, deviceName,
                    latitude, longitude, address, attendanceStatus, lateMinutes, matched);
                baseMapper.updateById(concurrent);
                attendance = concurrent;
            }
        } else {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockInAccuracy(accuracy);
            applyClockInFields(existing, workerName, clockInTime, deviceId, deviceName,
                latitude, longitude, address, attendanceStatus, lateMinutes, matched);
            baseMapper.updateById(existing);
            attendance = existing;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("attendanceId", attendance.getAttendanceId());
        data.put("clockInTime", clockInTime);
        data.put("recordDate", recordDate);
        // 走到此处即已通过围栏校验(或该规则未配置围栏),distance 为真实 Haversine 距离
        data.put("locationValid", true);
        data.put("locationChecked", distance != null);
        data.put("distance", distance != null ? distance : 0);
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
     * @param latitude   打卡纬度（可为 null）
     * @param longitude  打卡经度（可为 null）
     * @param address   打卡地址/定位描述（可为 null）
     * @return 打卡结果数据，包含考勤ID、打卡时间、是否早退、早退分钟数、考勤状态等
     */
    @Override
    public Map<String, Object> performClockOut(Long workerId, String workerName, Date clockOutTime, String deviceId, String deviceName,
                                               Double overtimeHours, BigDecimal latitude, BigDecimal longitude, String address,
                                               BigDecimal clockOutAccuracy) {
        LocalDate todayLd = LocalDate.now();
        // 打卡围栏校验(ERP-SCH-03)
        Long distance = requireLocationWithinFence(latitude, longitude);
        // 跨天班次匹配(ERP-SCH-05): 次日凌晨的下班打卡要命中"前一日"的跨天排班,
        // record_date 取该排班的排班日期,才能更新 22:00 上班打卡建立的那条考勤行。
        SchEmployeeSchedule matched = findScheduleForPunch(workerId, clockOutTime, todayLd);
        Date recordDate = matched != null ? truncateToDay(matched.getScheduledDate()) : startOfDay(todayLd);

        LambdaQueryWrapper<SchAttendance> existLqw = Wrappers.lambdaQuery();
        existLqw.eq(SchAttendance::getWorkerId, workerId);
        existLqw.eq(SchAttendance::getRecordDate, recordDate);
        List<SchAttendance> attendanceList = baseMapper.selectList(existLqw);

        Map<String, Object> result = new HashMap<>();
        long leaveEarlyMinutes = calcLeaveEarlyMinutes(matched, clockOutTime);

        if (!attendanceList.isEmpty()) {
            SchAttendance existing = attendanceList.get(0);
            existing.setClockOutTime(clockOutTime);
            existing.setWorkerName(workerName);
            existing.setDeviceId(deviceId);
            existing.setDeviceName(deviceName);
            existing.setClockOutLatitude(latitude);
            existing.setClockOutLongitude(longitude);
            existing.setClockOutAddress(address);
            existing.setClockOutAccuracy(clockOutAccuracy);
            if (matched != null) {
                existing.setScheduleId(matched.getScheduleId());
            }

            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, recordDate, false);

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
            result.put("isEarlyLeave", "early_leave".equals(existing.getAttendanceStatus()) || "late_early_leave".equals(existing.getAttendanceStatus()));
            result.put("leaveEarlyMinutes", existing.getLeaveEarlyMinutes() != null ? existing.getLeaveEarlyMinutes() : 0);
            result.put("overtimeHours", overtimeHours != null ? overtimeHours : 0.0);
            result.put("attendanceStatus", existing.getAttendanceStatus() != null ? existing.getAttendanceStatus() : "normal");
        } else {
            SchAttendance newAttendance = new SchAttendance();
            newAttendance.setWorkerId(workerId);
            newAttendance.setWorkerName(workerName);
            newAttendance.setRecordDate(recordDate);
            newAttendance.setScheduleId(matched != null ? matched.getScheduleId() : null);
            newAttendance.setClockOutTime(clockOutTime);
            newAttendance.setSourceType("manual");
            newAttendance.setDeviceId(deviceId);
            newAttendance.setDeviceName(deviceName);
            newAttendance.setClockOutLatitude(latitude);
            newAttendance.setClockOutLongitude(longitude);
            newAttendance.setClockOutAddress(address);
            newAttendance.setClockOutAccuracy(clockOutAccuracy);

            String clockOutAttendanceStatus = determineAttendanceStatus(clockOutTime, workerId, recordDate, false);

            newAttendance.setLeaveEarlyMinutes(leaveEarlyMinutes);
            newAttendance.setAttendanceStatus(clockOutAttendanceStatus);
            if (overtimeHours != null) {
                newAttendance.setOvertimeHours(java.math.BigDecimal.valueOf(overtimeHours));
            }

            baseMapper.insert(newAttendance);

            result.put("attendanceId", newAttendance.getAttendanceId());
            result.put("clockOutTime", clockOutTime);
            result.put("isEarlyLeave", "early_leave".equals(newAttendance.getAttendanceStatus()));
            result.put("leaveEarlyMinutes", newAttendance.getLeaveEarlyMinutes() != null ? newAttendance.getLeaveEarlyMinutes() : 0);
            result.put("overtimeHours", overtimeHours != null ? overtimeHours : 0.0);
            result.put("attendanceStatus", newAttendance.getAttendanceStatus() != null ? newAttendance.getAttendanceStatus() : "normal");
        }

        result.put("recordDate", recordDate);
        result.put("locationValid", true);
        result.put("locationChecked", distance != null);
        result.put("distance", distance != null ? distance : 0);

        return result;
    }

    /**
     * 上班打卡字段合并（"新建"与"已存在/并发冲突合并"三条路径共用，保证语义一致）
     */
    private void applyClockInFields(SchAttendance target, String workerName, Date clockInTime, String deviceId,
                                    String deviceName, BigDecimal latitude, BigDecimal longitude, String address,
                                    String attendanceStatus, long lateMinutes, SchEmployeeSchedule matched) {
        target.setClockInTime(clockInTime);
        target.setWorkerName(workerName);
        target.setDeviceId(deviceId);
        target.setDeviceName(deviceName);
        target.setClockInLatitude(latitude);
        target.setClockInLongitude(longitude);
        target.setClockInAddress(address);
        target.setAttendanceStatus(attendanceStatus);
        target.setLateMinutes(lateMinutes);
        if (matched != null) {
            target.setScheduleId(matched.getScheduleId());
        }
    }

    /**
     * 打卡围栏校验(ERP-SCH-03): 命中"已启用打卡规则"的围栏时,必须在半径内才允许打卡。
     * <p>语义:</p>
     * <ul>
     *   <li>无已启用规则 / 规则未配置中心点与半径 → 视为未启用围栏,不拦截(返回 null);</li>
     *   <li>配置了围栏但客户端未上报经纬度 → 拒绝(否则"不带定位"即可绕过);</li>
     *   <li>超出半径 → 拒绝并抛出实际距离。</li>
     * </ul>
     *
     * @return 实际距离(米,四舍五入);未启用围栏时返回 null
     */
    private Long requireLocationWithinFence(BigDecimal latitude, BigDecimal longitude) {
        SchPunchRuleVo punchRule = pickEnabledPunchRule();
        if (punchRule == null) {
            return null;
        }
        BigDecimal ruleLat = punchRule.getLocationLat();
        BigDecimal ruleLng = punchRule.getLocationLng();
        Long ruleRadius = punchRule.getLocationRadius();
        if (ruleLat == null || ruleLng == null || ruleRadius == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new ServiceException("打卡失败：未获取到定位信息，无法通过打卡范围校验");
        }
        double distance = calculateDistance(latitude.doubleValue(), longitude.doubleValue(),
            ruleLat.doubleValue(), ruleLng.doubleValue());
        if (distance > ruleRadius) {
            throw new ServiceException(String.format("打卡失败：超出打卡范围（距离%.0f米，允许范围%d米）",
                distance, ruleRadius));
        }
        return Math.round(distance);
    }

    /**
     * 取当前生效的打卡规则(enabled 且 ruleId 最小的一条);无规则返回 null
     */
    private SchPunchRuleVo pickEnabledPunchRule() {
        com.nexus.erp.sch.domain.bo.SchPunchRuleBo ruleQuery = new com.nexus.erp.sch.domain.bo.SchPunchRuleBo();
        ruleQuery.setIsEnabled(1);
        List<SchPunchRuleVo> enabledRules = schPunchRuleService.selectList(ruleQuery);
        if (enabledRules == null || enabledRules.isEmpty()) {
            return null;
        }
        return enabledRules.stream()
            .filter(r -> r.getIsEnabled() == null || r.getIsEnabled() == 1)
            .min(Comparator.comparing(SchPunchRuleVo::getRuleId, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
    }

    /**
     * 按打卡时间匹配排班(含前一日跨天班次)。
     * <p>1) 优先取"打卡时间落在 [startTime, endTime] 区间内"的排班(跨天班次 endTime 已推到次日,
     * 次日凌晨的下班打卡靠这条命中);2) 未命中区间时退化为当天排班,再退化为最近一条(保持原行为)。</p>
     *
     * @param workerId  工人ID
     * @param punchTime 打卡时间
     * @param todayLd   今天
     * @return 命中的排班;无任何排班返回 null
     */
    private SchEmployeeSchedule findScheduleForPunch(Long workerId, Date punchTime, LocalDate todayLd) {
        Date from = startOfDay(todayLd.minusDays(1));
        Date toExclusive = startOfDay(todayLd.plusDays(1));
        List<SchEmployeeSchedule> list = employeeScheduleMapper.selectList(
            Wrappers.<SchEmployeeSchedule>lambdaQuery()
                .eq(SchEmployeeSchedule::getWorkerId, workerId)
                .ge(SchEmployeeSchedule::getScheduledDate, from)
                .lt(SchEmployeeSchedule::getScheduledDate, toExclusive)
                .orderByAsc(SchEmployeeSchedule::getScheduledDate));
        if (list.isEmpty()) {
            return null;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            SchEmployeeSchedule schedule = list.get(i);
            if (schedule.getStartTime() != null && schedule.getEndTime() != null
                && !punchTime.before(schedule.getStartTime()) && !punchTime.after(schedule.getEndTime())) {
                return schedule;
            }
        }
        Date today = startOfDay(todayLd);
        for (int i = list.size() - 1; i >= 0; i--) {
            SchEmployeeSchedule schedule = list.get(i);
            if (schedule.getScheduledDate() != null && today.equals(truncateToDay(schedule.getScheduledDate()))) {
                return schedule;
            }
        }
        return list.get(list.size() - 1);
    }

    /**
     * 早退分钟数: 打卡时间早于排班结束时间即为早退
     */
    private long calcLeaveEarlyMinutes(SchEmployeeSchedule schedule, Date clockOutTime) {
        if (schedule == null || schedule.getEndTime() == null) {
            return 0;
        }
        long diff = schedule.getEndTime().getTime() - clockOutTime.getTime();
        return Math.max(0, diff / (1000 * 60));
    }

    private Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date truncateToDay(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
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
            // 今日无考勤记录：不得返回 normal，否则调用方（App 首页“我的数据”/打卡页）会把“未打卡”显示成“已打卡”。
            data.put("attendanceStatus", null);
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
        doFaceVerify(attendanceId, workerId, tenantId, imageBytes, storeImage, false);
    }

    /**
     * 降级补跑触发人脸验证(ERP-SCH-07)
     * <p>复用已有存证照片,不重复存证;若本轮仍为 NONE 则不刷新 {@code face_verify_time},
     * 让该记录按原始入队时间自然滚出 24h 补跑窗口,不再永久占用每轮 200 条配额。</p>
     */
    @Async("cvExecutor")
    @Override
    public void backfillFaceVerify(Long attendanceId, Long workerId, String tenantId, byte[] imageBytes) {
        doFaceVerify(attendanceId, workerId, tenantId, imageBytes, false, true);
    }

    /**
     * 人脸校验异步主体: 存证(可选) + 校验 + 回写
     *
     * @param storeImage 是否上传存证照片(打卡 true;补跑 false 复用已有 ossId)
     * @param backfill   是否为降级补跑(补跑且结果仍 NONE 时不刷新 face_verify_time)
     */
    private void doFaceVerify(Long attendanceId, Long workerId, String tenantId, byte[] imageBytes,
                              boolean storeImage, boolean backfill) {
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
                    verifyAndWriteBack(attendanceId, workerId, tenantId, imageBytes, ossId, backfill);
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
     *
     * @param tenantId 租户ID(用于拼装带租户维度的底库 subject)
     * @param backfill 降级补跑场景:结果仍为 NONE 时不刷新 face_verify_time(避免永久占用补跑配额)
     */
    private void verifyAndWriteBack(Long attendanceId, Long workerId, String tenantId, byte[] imageBytes, Long ossId,
                                    boolean backfill) {
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
                // 底库 subject 必须带租户维度(ERP-CV-02): 不同租户同工号不得串库
                String subject = CvSubjectHelper.build(tenantId,
                    worker != null ? worker.getEmployeeId() : String.valueOf(workerId));
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
                    // 补跑仍为 NONE 时不刷新 face_verify_time: 该字段是补跑任务的 24h 选取窗口,
                    // 无条件刷新会让无照片记录永远落在窗口内、每轮被重复选中(ERP-SCH-07 饿死)
                    if (!backfill || !"NONE".equals(status)) {
                        attendance.setFaceVerifyTime(verifyTime);
                    }
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
