package com.nexus.erp.sch.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.DateUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchLeaveApply;
import com.nexus.erp.sch.domain.SchOutdoorPunch;
import com.nexus.erp.sch.domain.SchSupplementApply;
import com.nexus.erp.sch.domain.bo.SchApplyApproveBo;
import com.nexus.erp.sch.domain.bo.SchLeaveApplyBo;
import com.nexus.erp.sch.domain.bo.SchOutdoorPunchBo;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.bo.SchSupplementApplyBo;
import com.nexus.erp.sch.domain.vo.SchLeaveApplyVo;
import com.nexus.erp.sch.domain.vo.SchOutdoorPunchVo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.domain.vo.SchSupplementApplyVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchLeaveApplyMapper;
import com.nexus.erp.sch.mapper.SchOutdoorPunchMapper;
import com.nexus.erp.sch.mapper.SchSupplementApplyMapper;
import com.nexus.erp.sch.service.ISchAppApplyService;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * App 考勤申请Service业务层处理（补卡/外勤/请假）
 * <p>
 * 替换 AppSchAttendanceController 原补卡/外勤 Mock 返回，业务规则依赖 sch_punch_rule：
 * allow_supplement / supplement_deadline / allow_outdoor_punch / outdoor_require_photo。
 *
 * @author nexus
 * @date 2026-08-24
 */
@RequiredArgsConstructor
@Slf4j
@DS("erp")
@Service
public class SchAppApplyServiceImpl implements ISchAppApplyService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final SchSupplementApplyMapper supplementApplyMapper;
    private final SchOutdoorPunchMapper outdoorPunchMapper;
    private final SchLeaveApplyMapper leaveApplyMapper;
    private final SchAttendanceMapper attendanceMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;
    private final ISchPunchRuleService schPunchRuleService;
    private final ISysOssService sysOssService;
    private final SysUserMapper sysUserMapper;

    // ==================== 补卡申请 ====================

    @Override
    public Long submitSupplementApply(Long workerId, String workerName, SchSupplementApplyBo bo) {
        SchPunchRuleVo rule = requirePunchRule();
        if (rule.getAllowSupplement() == null || rule.getAllowSupplement() != 1) {
            throw new ServiceException("当前打卡规则不允许补卡申请");
        }

        Date applyDate = DateUtils.parseDate(bo.getApplyDate());
        if (applyDate == null) {
            throw new ServiceException("补卡日期格式错误，应为yyyy-MM-dd");
        }
        LocalDate date = applyDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new ServiceException("补卡日期不能晚于今天");
        }
        long deadline = rule.getSupplementDeadline() == null ? 7L : rule.getSupplementDeadline();
        if (date.isBefore(today.minusDays(deadline))) {
            throw new ServiceException("超过补卡申请截止天数（" + deadline + "天），无法申请");
        }

        // 同日重复申请拦截：待审批或已通过的补卡不可重复提交
        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long dup = supplementApplyMapper.selectCount(Wrappers.<SchSupplementApply>lambdaQuery()
            .eq(SchSupplementApply::getWorkerId, workerId)
            .ge(SchSupplementApply::getApplyDate, dayStart)
            .lt(SchSupplementApply::getApplyDate, dayEnd)
            .in(SchSupplementApply::getApplyStatus, STATUS_PENDING, STATUS_APPROVED));
        if (dup != null && dup > 0) {
            throw new ServiceException("该日期已存在待审批或已通过的补卡申请");
        }

        SchSupplementApply apply = new SchSupplementApply();
        apply.setApplyNo("BC" + DateUtils.dateTimeNow());
        apply.setWorkerId(workerId);
        apply.setWorkerName(workerName);
        apply.setApplyDate(applyDate);
        apply.setApplyType(bo.getApplyType());
        apply.setReason(bo.getReason());
        apply.setApplyStatus(STATUS_PENDING);
        try {
            supplementApplyMapper.insert(apply);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // ERP-SCH-11: 单号为"前缀+秒级时间", 同秒并发会撞 uk_tenant_apply_no(增量脚本 §2)。
            // 捕获后给出可读提示而非 500(重试即可拿到新单号)。
            log.warn("[sch] 补卡单号冲突: applyNo={}", apply.getApplyNo());
            throw new ServiceException("补卡单号生成冲突，请重试");
        }
        return apply.getApplyId();
    }

    @Override
    public TableDataInfo<SchSupplementApplyVo> selectMySupplementApplies(Long workerId, String startDate,
                                                                         String endDate, String applyStatus,
                                                                         PageQuery pageQuery) {
        LambdaQueryWrapper<SchSupplementApply> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchSupplementApply::getWorkerId, workerId);
        if (StrUtil.isNotBlank(startDate)) {
            lqw.ge(SchSupplementApply::getApplyDate, DateUtils.parseDate(startDate));
        }
        if (StrUtil.isNotBlank(endDate)) {
            lqw.le(SchSupplementApply::getApplyDate, DateUtils.parseDate(endDate + " 23:59:59"));
        }
        if (StrUtil.isNotBlank(applyStatus)) {
            lqw.eq(SchSupplementApply::getApplyStatus, applyStatus);
        }
        lqw.orderByDesc(SchSupplementApply::getApplyDate);
        Page<SchSupplementApplyVo> page = supplementApplyMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<SchSupplementApplyVo> selectPendingSupplementApplies(PageQuery pageQuery) {
        LambdaQueryWrapper<SchSupplementApply> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchSupplementApply::getApplyStatus, STATUS_PENDING);
        lqw.orderByAsc(SchSupplementApply::getApplyDate);
        Page<SchSupplementApplyVo> page = supplementApplyMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveSupplementApply(SchApplyApproveBo bo) {
        SchSupplementApply apply = supplementApplyMapper.selectById(bo.getApplyId());
        if (apply == null) {
            throw new ServiceException("补卡申请不存在");
        }
        if (!STATUS_PENDING.equals(apply.getApplyStatus())) {
            throw new ServiceException("该申请已审批，不能重复操作");
        }

        String targetStatus = Boolean.TRUE.equals(bo.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED;
        // ERP-SCH-09: "读-改-写"改为条件更新(CAS)。双审批人并发时只有一方能把 pending 推进,
        // 影响 0 行即抛异常回滚, 避免两人都通过并各自回写考勤(原实现可双通过)。
        int rows = supplementApplyMapper.update(null, Wrappers.<SchSupplementApply>lambdaUpdate()
            .eq(SchSupplementApply::getApplyId, bo.getApplyId())
            .eq(SchSupplementApply::getApplyStatus, STATUS_PENDING)
            .set(SchSupplementApply::getApplyStatus, targetStatus)
            .set(SchSupplementApply::getApproveUserId, currentUserId())
            .set(SchSupplementApply::getApproveUserName, currentUserName())
            .set(SchSupplementApply::getApproveTime, new Date())
            .set(SchSupplementApply::getApproveRemark, bo.getApproveRemark()));
        if (rows == 0) {
            throw new ServiceException("该申请已审批，不能重复操作");
        }

        if (Boolean.TRUE.equals(bo.getApproved())) {
            apply.setApplyStatus(targetStatus);
            writeBackSupplement(apply);
        }
    }

    /**
     * 补卡通过后回写考勤记录：按当日排班的上下班时间补齐缺卡侧打卡，来源 manual。
     * <p><b>不覆盖已判定的迟到/早退</b>（ERP-SCH-02）：原实现无条件把 {@code attendance_status}
     * 置 normal，会把当天已经判定的 late/early_leave 抹平，与仍然保留的 {@code late_minutes}
     * 自相矛盾、月度考勤口径失真。现在只在"新建记录"或"原状态为空"时才置 normal，
     * 已有判定结果原样保留，仅补缺卡侧时间。</p>
     */
    private void writeBackSupplement(SchSupplementApply apply) {
        LocalDate date = apply.getApplyDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // 当日排班的上下班时间作为补卡时间
        Date scheduledStart = null;
        Date scheduledEnd = null;
        List<SchEmployeeSchedule> schedules = employeeScheduleMapper.selectList(
            Wrappers.<SchEmployeeSchedule>lambdaQuery()
                .eq(SchEmployeeSchedule::getWorkerId, apply.getWorkerId())
                .ge(SchEmployeeSchedule::getScheduledDate, dayStart)
                .lt(SchEmployeeSchedule::getScheduledDate, dayEnd));
        if (!schedules.isEmpty()) {
            scheduledStart = schedules.get(0).getStartTime();
            scheduledEnd = schedules.get(0).getEndTime();
        }

        SchAttendance attendance = attendanceMapper.selectOne(Wrappers.<SchAttendance>lambdaQuery()
            .eq(SchAttendance::getWorkerId, apply.getWorkerId())
            .ge(SchAttendance::getRecordDate, dayStart)
            .lt(SchAttendance::getRecordDate, dayEnd)
            .last("LIMIT 1"));

        String type = apply.getApplyType();
        boolean fixClockIn = "clock_in".equals(type) || "full_day".equals(type);
        boolean fixClockOut = "clock_out".equals(type) || "full_day".equals(type);

        if (attendance == null) {
            attendance = new SchAttendance();
            attendance.setWorkerId(apply.getWorkerId());
            attendance.setWorkerName(apply.getWorkerName());
            attendance.setRecordDate(dayStart);
            attendance.setClockInTime(fixClockIn ? (scheduledStart != null ? scheduledStart : dayStart) : null);
            attendance.setClockOutTime(fixClockOut ? (scheduledEnd != null ? scheduledEnd : dayStart) : null);
            attendance.setAttendanceStatus("normal");
            attendance.setSourceType("manual");
            attendance.setRemark("补卡通过：" + apply.getApplyNo());
            attendanceMapper.insert(attendance);
        } else {
            SchAttendance update = new SchAttendance();
            update.setAttendanceId(attendance.getAttendanceId());
            if (fixClockIn && attendance.getClockInTime() == null) {
                update.setClockInTime(scheduledStart != null ? scheduledStart : dayStart);
            }
            if (fixClockOut && attendance.getClockOutTime() == null) {
                update.setClockOutTime(scheduledEnd != null ? scheduledEnd : dayStart);
            }
            // 仅当原记录尚无考勤判定结果时才置 normal；迟到/早退等已判定状态必须保留(ERP-SCH-02)
            update.setAttendanceStatus(StrUtil.isBlank(attendance.getAttendanceStatus())
                ? "normal" : attendance.getAttendanceStatus());
            update.setSourceType("manual");
            update.setRemark("补卡通过：" + apply.getApplyNo());
            attendanceMapper.updateById(update);
        }
    }

    // ==================== 外勤打卡 ====================

    @Override
    public Long submitOutdoorPunch(Long workerId, String workerName, SchOutdoorPunchBo bo) {
        SchPunchRuleVo rule = requirePunchRule();
        if (rule.getAllowOutdoorPunch() == null || rule.getAllowOutdoorPunch() != 1) {
            throw new ServiceException("当前打卡规则不允许外勤打卡");
        }

        Long photoOssId = null;
        if (rule.getOutdoorRequirePhoto() != null && rule.getOutdoorRequirePhoto() == 1) {
            if (StrUtil.isBlank(bo.getPhotoImage())) {
                throw new ServiceException("当前打卡规则要求外勤打卡必须拍照");
            }
            photoOssId = uploadPhoto(bo.getPhotoImage());
        }

        SchOutdoorPunch punch = new SchOutdoorPunch();
        punch.setWorkerId(workerId);
        punch.setWorkerName(workerName);
        punch.setPunchTime(new Date());
        punch.setLatitude(parseCoordinate(bo.getLatitude(), "纬度"));
        punch.setLongitude(parseCoordinate(bo.getLongitude(), "经度"));
        punch.setAddress(bo.getAddress());
        punch.setCustomerName(StrUtil.trimToNull(bo.getCustomerName()));
        punch.setReason(bo.getReason());
        punch.setPhotoOssId(photoOssId);
        outdoorPunchMapper.insert(punch);
        return punch.getPunchId();
    }

    @Override
    public TableDataInfo<SchOutdoorPunchVo> selectMyOutdoorPunches(Long workerId, String startDate,
                                                                   String endDate, PageQuery pageQuery) {
        LambdaQueryWrapper<SchOutdoorPunch> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchOutdoorPunch::getWorkerId, workerId);
        if (StrUtil.isNotBlank(startDate)) {
            lqw.ge(SchOutdoorPunch::getPunchTime, DateUtils.parseDate(startDate));
        }
        if (StrUtil.isNotBlank(endDate)) {
            lqw.le(SchOutdoorPunch::getPunchTime, DateUtils.parseDate(endDate + " 23:59:59"));
        }
        lqw.orderByDesc(SchOutdoorPunch::getPunchTime);
        Page<SchOutdoorPunchVo> page = outdoorPunchMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    // ==================== 请假申请 ====================

    @Override
    public Long submitLeaveApply(Long workerId, String workerName, SchLeaveApplyBo bo) {
        Date start = DateUtils.parseDate(bo.getStartDate());
        Date end = DateUtils.parseDate(bo.getEndDate());
        if (start == null || end == null) {
            throw new ServiceException("请假日期格式错误，应为yyyy-MM-dd");
        }
        LocalDate startDate = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (endDate.isBefore(startDate)) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > 365) {
            throw new ServiceException("单次请假时长不能超过365天");
        }

        SchLeaveApply apply = new SchLeaveApply();
        apply.setLeaveNo("QJ" + DateUtils.dateTimeNow());
        apply.setWorkerId(workerId);
        apply.setWorkerName(workerName);
        apply.setLeaveType(bo.getLeaveType());
        apply.setStartDate(start);
        apply.setEndDate(end);
        apply.setDurationDays(BigDecimal.valueOf(days));
        apply.setReason(bo.getReason());
        apply.setApplyStatus(STATUS_PENDING);
        try {
            leaveApplyMapper.insert(apply);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // ERP-SCH-11: 单号为"前缀+秒级时间", 同秒并发会撞 uk_tenant_leave_no(增量脚本 §2)
            log.warn("[sch] 请假单号冲突: leaveNo={}", apply.getLeaveNo());
            throw new ServiceException("请假单号生成冲突，请重试");
        }
        return apply.getLeaveId();
    }

    @Override
    public TableDataInfo<SchLeaveApplyVo> selectMyLeaveApplies(Long workerId, String startDate, String endDate,
                                                               String applyStatus, PageQuery pageQuery) {
        LambdaQueryWrapper<SchLeaveApply> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchLeaveApply::getWorkerId, workerId);
        if (StrUtil.isNotBlank(startDate)) {
            lqw.ge(SchLeaveApply::getStartDate, DateUtils.parseDate(startDate));
        }
        if (StrUtil.isNotBlank(endDate)) {
            lqw.le(SchLeaveApply::getStartDate, DateUtils.parseDate(endDate + " 23:59:59"));
        }
        if (StrUtil.isNotBlank(applyStatus)) {
            lqw.eq(SchLeaveApply::getApplyStatus, applyStatus);
        }
        lqw.orderByDesc(SchLeaveApply::getStartDate);
        Page<SchLeaveApplyVo> page = leaveApplyMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<SchLeaveApplyVo> selectPendingLeaveApplies(PageQuery pageQuery) {
        LambdaQueryWrapper<SchLeaveApply> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchLeaveApply::getApplyStatus, STATUS_PENDING);
        lqw.orderByAsc(SchLeaveApply::getStartDate);
        Page<SchLeaveApplyVo> page = leaveApplyMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveLeaveApply(SchApplyApproveBo bo) {
        SchLeaveApply apply = leaveApplyMapper.selectById(bo.getApplyId());
        if (apply == null) {
            throw new ServiceException("请假申请不存在");
        }
        if (!STATUS_PENDING.equals(apply.getApplyStatus())) {
            throw new ServiceException("该申请已审批，不能重复操作");
        }

        String targetStatus = Boolean.TRUE.equals(bo.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED;
        // ERP-SCH-09: 同补卡审批, 条件更新(CAS)防双审批人并发通过
        int rows = leaveApplyMapper.update(null, Wrappers.<SchLeaveApply>lambdaUpdate()
            .eq(SchLeaveApply::getLeaveId, bo.getApplyId())
            .eq(SchLeaveApply::getApplyStatus, STATUS_PENDING)
            .set(SchLeaveApply::getApplyStatus, targetStatus)
            .set(SchLeaveApply::getApproveUserId, currentUserId())
            .set(SchLeaveApply::getApproveUserName, currentUserName())
            .set(SchLeaveApply::getApproveTime, new Date())
            .set(SchLeaveApply::getApproveRemark, bo.getApproveRemark()));
        if (rows == 0) {
            throw new ServiceException("该申请已审批，不能重复操作");
        }

        // 审批通过：请假区间逐日落考勤记录(状态 leave)，月度统计 leaveDays 生效
        if (Boolean.TRUE.equals(bo.getApproved())) {
            apply.setApplyStatus(targetStatus);
            LocalDate start = apply.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate end = apply.getEndDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                markLeaveAttendance(apply, d);
            }
        }
    }

    private void markLeaveAttendance(SchLeaveApply apply, LocalDate date) {
        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        SchAttendance existing = attendanceMapper.selectOne(Wrappers.<SchAttendance>lambdaQuery()
            .eq(SchAttendance::getWorkerId, apply.getWorkerId())
            .ge(SchAttendance::getRecordDate, dayStart)
            .lt(SchAttendance::getRecordDate, dayEnd)
            .last("LIMIT 1"));
        if (existing == null) {
            SchAttendance attendance = new SchAttendance();
            attendance.setWorkerId(apply.getWorkerId());
            attendance.setWorkerName(apply.getWorkerName());
            attendance.setRecordDate(dayStart);
            attendance.setAttendanceStatus("leave");
            attendance.setSourceType("manual");
            attendance.setRemark("请假：" + apply.getLeaveNo());
            attendanceMapper.insert(attendance);
        } else if (!"leave".equals(existing.getAttendanceStatus())) {
            SchAttendance update = new SchAttendance();
            update.setAttendanceId(existing.getAttendanceId());
            update.setAttendanceStatus("leave");
            update.setRemark("请假：" + apply.getLeaveNo());
            attendanceMapper.updateById(update);
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 取当前生效的打卡规则（enabled 且 id 最小的一条；无规则时抛异常）
     */
    private SchPunchRuleVo requirePunchRule() {
        List<SchPunchRuleVo> rules = schPunchRuleService.selectList(new SchPunchRuleBo());
        return rules.stream()
            .filter(r -> r.getIsEnabled() == null || r.getIsEnabled() == 1)
            .min(Comparator.comparing(SchPunchRuleVo::getRuleId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .orElseThrow(() -> new ServiceException("未配置打卡规则，请联系管理员"));
    }

    private BigDecimal parseCoordinate(String value, String label) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ServiceException(label + "格式错误");
        }
    }

    /**
     * 外勤照片 base64 → MinIO 存证，返回 ossId。
     * sys_oss 表在 master 库，方法运行于 erp 数据源上下文，需显式切换。
     */
    private Long uploadPhoto(String base64Image) {
        String base64 = base64Image.contains(",") ? base64Image.split(",", 2)[1] : base64Image;
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("照片数据格式错误");
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            Path temp = Files.createTempFile("outdoor-", ".jpg");
            try {
                Files.write(temp, bytes);
                SysOssVo ossVo = sysOssService.upload(temp.toFile());
                return ossVo.getOssId();
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[sch] 外勤打卡照片上传 MinIO 失败", e);
            throw new ServiceException("照片上传失败，请重试");
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("无法获取当前登录用户");
        }
        return userId;
    }

    private String currentUserName() {
        try {
            Long userId = currentUserId();
            DynamicDataSourceContextHolder.push("master");
            try {
                SysUserVo user = sysUserMapper.selectVoById(userId);
                if (user != null) {
                    return user.getNickName() != null ? user.getNickName() : user.getUserName();
                }
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        } catch (Exception e) {
            log.warn("[sch] 获取审批人姓名失败", e);
        }
        return null;
    }
}
