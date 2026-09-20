package com.nexus.erp.sch;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchLeaveApply;
import com.nexus.erp.sch.domain.SchSupplementApply;
import com.nexus.erp.sch.domain.bo.SchApplyApproveBo;
import com.nexus.erp.sch.domain.bo.SchLeaveApplyBo;
import com.nexus.erp.sch.domain.bo.SchOutdoorPunchBo;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.bo.SchSupplementApplyBo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchLeaveApplyMapper;
import com.nexus.erp.sch.mapper.SchOutdoorPunchMapper;
import com.nexus.erp.sch.mapper.SchSupplementApplyMapper;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import com.nexus.erp.sch.service.impl.SchAppApplyServiceImpl;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SchAppApplyServiceImpl 单元测试
 * <p>核心验证: 打卡规则校验（允许补卡/截止天数/允许外勤/拍照必填）、
 * 同日重复补卡拦截、请假日期与时长计算、审批状态机、请假通过后考勤记录回写。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SchAppApplyServiceImplTest {

    @Mock
    private SchSupplementApplyMapper supplementApplyMapper;
    @Mock
    private SchOutdoorPunchMapper outdoorPunchMapper;
    @Mock
    private SchLeaveApplyMapper leaveApplyMapper;
    @Mock
    private SchAttendanceMapper attendanceMapper;
    @Mock
    private SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock
    private ISchPunchRuleService schPunchRuleService;
    @Mock
    private ISysOssService sysOssService;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private SchAppApplyServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long WORKER_ID = 100L;
    private static final String WORKER_NAME = "张三";

    @BeforeEach
    void setUp() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private SchPunchRuleVo rule(Integer allowSupplement, Long deadline, Integer allowOutdoor, Integer requirePhoto) {
        SchPunchRuleVo r = new SchPunchRuleVo();
        r.setRuleId(1L);
        r.setRuleName("默认规则");
        r.setIsEnabled(1);
        r.setAllowSupplement(allowSupplement);
        r.setSupplementDeadline(deadline);
        r.setAllowOutdoorPunch(allowOutdoor);
        r.setOutdoorRequirePhoto(requirePhoto);
        return r;
    }

    private SchSupplementApplyBo supplementBo(String date, String type) {
        SchSupplementApplyBo bo = new SchSupplementApplyBo();
        bo.setApplyDate(date);
        bo.setApplyType(type);
        bo.setReason("忘记打卡");
        return bo;
    }

    // ==================== 补卡申请 ====================

    @Test
    void submitSupplement_ruleDisabled_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(0, 7L, 1, 0)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitSupplementApply(WORKER_ID, WORKER_NAME, supplementBo("2026-08-20", "clock_in")));
        assertTrue(ex.getMessage().contains("不允许补卡"));
    }

    @Test
    void submitSupplement_beyondDeadline_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 0)));

        // 截止 7 天：申请 10 天前 → 拒绝
        String tenDaysAgo = LocalDate.now().minusDays(10).toString();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitSupplementApply(WORKER_ID, WORKER_NAME, supplementBo(tenDaysAgo, "clock_in")));
        assertTrue(ex.getMessage().contains("截止天数"));
    }

    @Test
    void submitSupplement_futureDate_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 0)));

        String tomorrow = LocalDate.now().plusDays(1).toString();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitSupplementApply(WORKER_ID, WORKER_NAME, supplementBo(tomorrow, "clock_in")));
        assertTrue(ex.getMessage().contains("不能晚于今天"));
    }

    @Test
    void submitSupplement_duplicate_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 0)));
        when(supplementApplyMapper.selectCount(any())).thenReturn(1L);

        String yesterday = LocalDate.now().minusDays(1).toString();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitSupplementApply(WORKER_ID, WORKER_NAME, supplementBo(yesterday, "clock_in")));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void submitSupplement_success_insertsPendingApply() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 0)));
        when(supplementApplyMapper.selectCount(any())).thenReturn(0L);
        when(supplementApplyMapper.insert(any(SchSupplementApply.class))).thenAnswer(inv -> {
            inv.getArgument(0, SchSupplementApply.class).setApplyId(501L);
            return 1;
        });

        String yesterday = LocalDate.now().minusDays(1).toString();
        Long applyId = service.submitSupplementApply(WORKER_ID, WORKER_NAME, supplementBo(yesterday, "clock_in"));

        assertEquals(501L, applyId);
        ArgumentCaptor<SchSupplementApply> captor = ArgumentCaptor.forClass(SchSupplementApply.class);
        verify(supplementApplyMapper).insert(captor.capture());
        SchSupplementApply saved = captor.getValue();
        assertEquals("pending", saved.getApplyStatus());
        assertEquals(WORKER_ID, saved.getWorkerId());
        assertNotNull(saved.getApplyNo());
        assertTrue(saved.getApplyNo().startsWith("BC"));
    }

    // ==================== 外勤打卡 ====================

    @Test
    void submitOutdoor_outdoorDisabled_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 0, 0)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitOutdoorPunch(WORKER_ID, WORKER_NAME, outdoorBo(null)));
        assertTrue(ex.getMessage().contains("不允许外勤"));
    }

    @Test
    void submitOutdoor_photoRequiredButMissing_throws() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 1)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitOutdoorPunch(WORKER_ID, WORKER_NAME, outdoorBo(null)));
        assertTrue(ex.getMessage().contains("拍照"));
    }

    @Test
    void submitOutdoor_success() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(1, 7L, 1, 0)));
        when(outdoorPunchMapper.insert(any(com.nexus.erp.sch.domain.SchOutdoorPunch.class))).thenAnswer(inv -> {
            inv.getArgument(0, com.nexus.erp.sch.domain.SchOutdoorPunch.class).setPunchId(601L);
            return 1;
        });

        Long punchId = service.submitOutdoorPunch(WORKER_ID, WORKER_NAME, outdoorBo(null));
        assertEquals(601L, punchId);
        verify(outdoorPunchMapper).insert(any(com.nexus.erp.sch.domain.SchOutdoorPunch.class));
    }

    private SchOutdoorPunchBo outdoorBo(String photo) {
        SchOutdoorPunchBo bo = new SchOutdoorPunchBo();
        bo.setLatitude("31.230416");
        bo.setLongitude("121.473701");
        bo.setAddress("上海市黄浦区");
        bo.setCustomerName("客户A");
        bo.setReason("拜访客户");
        bo.setPhotoImage(photo);
        return bo;
    }

    // ==================== 请假申请 ====================

    @Test
    void submitLeave_endBeforeStart_throws() {
        SchLeaveApplyBo bo = leaveBo("2026-09-10", "2026-09-08");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitLeaveApply(WORKER_ID, WORKER_NAME, bo));
        assertTrue(ex.getMessage().contains("结束日期不能早于开始日期"));
    }

    @Test
    void submitLeave_success_computesDurationDays() {
        when(leaveApplyMapper.insert(any(SchLeaveApply.class))).thenAnswer(inv -> {
            inv.getArgument(0, SchLeaveApply.class).setLeaveId(701L);
            return 1;
        });

        // 9-01 ~ 9-03 → 3 天
        Long leaveId = service.submitLeaveApply(WORKER_ID, WORKER_NAME, leaveBo("2026-09-01", "2026-09-03"));

        assertEquals(701L, leaveId);
        ArgumentCaptor<SchLeaveApply> captor = ArgumentCaptor.forClass(SchLeaveApply.class);
        verify(leaveApplyMapper).insert(captor.capture());
        SchLeaveApply saved = captor.getValue();
        assertEquals("pending", saved.getApplyStatus());
        assertEquals(0, saved.getDurationDays().compareTo(new java.math.BigDecimal("3")));
        assertTrue(saved.getLeaveNo().startsWith("QJ"));
    }

    private SchLeaveApplyBo leaveBo(String start, String end) {
        SchLeaveApplyBo bo = new SchLeaveApplyBo();
        bo.setLeaveType("personal");
        bo.setStartDate(start);
        bo.setEndDate(end);
        bo.setReason("家中有事");
        return bo;
    }

    // ==================== 审批状态机 ====================

    @Test
    void approveSupplement_alreadyProcessed_throws() {
        SchSupplementApply apply = new SchSupplementApply();
        apply.setApplyId(1L);
        apply.setApplyStatus("approved");
        when(supplementApplyMapper.selectById(1L)).thenReturn(apply);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.approveSupplementApply(approveBo(1L, true)));
        assertTrue(ex.getMessage().contains("已审批"));
    }

    @Test
    void approveLeave_notFound_throws() {
        when(leaveApplyMapper.selectById(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.approveLeaveApply(approveBo(99L, true)));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void approveLeave_rejected_noAttendanceWriteBack() {
        SchLeaveApply apply = pendingLeave(1L, "2026-09-01", "2026-09-03");
        when(leaveApplyMapper.selectById(1L)).thenReturn(apply);

        service.approveLeaveApply(approveBo(1L, false));

        ArgumentCaptor<SchLeaveApply> captor = ArgumentCaptor.forClass(SchLeaveApply.class);
        verify(leaveApplyMapper).updateById(captor.capture());
        assertEquals("rejected", captor.getValue().getApplyStatus());
        // 驳回不落考勤记录
        verify(attendanceMapper, never()).insert(any(SchAttendance.class));
    }

    @Test
    void approveLeave_approved_writesBackLeaveAttendancePerDay() {
        SchLeaveApply apply = pendingLeave(1L, "2026-09-01", "2026-09-03");
        when(leaveApplyMapper.selectById(1L)).thenReturn(apply);
        // 区间内无已有考勤记录 → 每天新建
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        service.approveLeaveApply(approveBo(1L, true));

        ArgumentCaptor<SchLeaveApply> leaveCaptor = ArgumentCaptor.forClass(SchLeaveApply.class);
        verify(leaveApplyMapper).updateById(leaveCaptor.capture());
        assertEquals("approved", leaveCaptor.getValue().getApplyStatus());

        // 3 天请假 → 3 条 leave 考勤记录
        ArgumentCaptor<SchAttendance> attendanceCaptor = ArgumentCaptor.forClass(SchAttendance.class);
        verify(attendanceMapper, Mockito.times(3)).insert(attendanceCaptor.capture());
        attendanceCaptor.getAllValues().forEach(a -> assertEquals("leave", a.getAttendanceStatus()));
    }

    // ==================== 补卡回写不覆盖已判定状态(ERP-SCH-02) ====================

    @Test
    void approveSupplement_clockOut_keepsExistingLateStatus() {
        SchSupplementApply apply = new SchSupplementApply();
        apply.setApplyId(1L);
        apply.setApplyNo("BC20260920000001");
        apply.setWorkerId(WORKER_ID);
        apply.setWorkerName(WORKER_NAME);
        apply.setApplyDate(Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        apply.setApplyType("clock_out");
        apply.setApplyStatus("pending");
        when(supplementApplyMapper.selectById(1L)).thenReturn(apply);

        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(WORKER_ID);
        schedule.setStartTime(Date.from(LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setEndTime(Date.from(LocalDate.now().atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of(schedule));

        // 当天已判定迟到: 9:30 上班打卡 → late / late_minutes=30, 下班卡缺失
        SchAttendance existing = new SchAttendance();
        existing.setAttendanceId(900L);
        existing.setWorkerId(WORKER_ID);
        existing.setClockInTime(Date.from(LocalDate.now().atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant()));
        existing.setAttendanceStatus("late");
        existing.setLateMinutes(30L);
        when(attendanceMapper.selectOne(any())).thenReturn(existing);

        service.approveSupplementApply(approveBo(1L, true));

        ArgumentCaptor<SchAttendance> captor = ArgumentCaptor.forClass(SchAttendance.class);
        verify(attendanceMapper).updateById(captor.capture());
        SchAttendance updated = captor.getValue();
        // 补齐缺的下班卡
        assertNotNull(updated.getClockOutTime());
        // 关键回归: 不得把已判定的迟到抹成 normal(修复前恒为 normal)
        assertEquals("late", updated.getAttendanceStatus());
        // 未触碰迟到分钟数(MP updateById 忽略 null 字段 → 库中仍为 30)
        assertNull(updated.getLateMinutes());
        verify(attendanceMapper, never()).insert(any(SchAttendance.class));
    }

    @Test
    void approveSupplement_clockIn_onEmptyRecord_setsNormal() {
        SchSupplementApply apply = new SchSupplementApply();
        apply.setApplyId(2L);
        apply.setApplyNo("BC20260920000002");
        apply.setWorkerId(WORKER_ID);
        apply.setWorkerName(WORKER_NAME);
        apply.setApplyDate(Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        apply.setApplyType("clock_in");
        apply.setApplyStatus("pending");
        when(supplementApplyMapper.selectById(2L)).thenReturn(apply);
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of());
        // 当天完全没有考勤记录 → 新建, 状态 normal
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        service.approveSupplementApply(approveBo(2L, true));

        ArgumentCaptor<SchAttendance> captor = ArgumentCaptor.forClass(SchAttendance.class);
        verify(attendanceMapper).insert(captor.capture());
        assertEquals("normal", captor.getValue().getAttendanceStatus());
        assertEquals(WORKER_ID, captor.getValue().getWorkerId());
    }

    private SchLeaveApply pendingLeave(Long id, String start, String end) {
        SchLeaveApply apply = new SchLeaveApply();
        apply.setLeaveId(id);
        apply.setLeaveNo("QJ20260824000001");
        apply.setWorkerId(WORKER_ID);
        apply.setWorkerName(WORKER_NAME);
        apply.setLeaveType("personal");
        apply.setStartDate(Date.from(LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        apply.setEndDate(Date.from(LocalDate.parse(end).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        apply.setApplyStatus("pending");
        return apply;
    }

    private SchApplyApproveBo approveBo(Long applyId, boolean approved) {
        SchApplyApproveBo bo = new SchApplyApproveBo();
        bo.setApplyId(applyId);
        bo.setApproved(approved);
        bo.setApproveRemark("同意");
        return bo;
    }
}
