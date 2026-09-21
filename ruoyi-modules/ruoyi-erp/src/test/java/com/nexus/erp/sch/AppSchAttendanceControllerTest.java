package com.nexus.erp.sch;

import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.sch.controller.app.AppSchAttendanceController;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchAppApplyService;
import com.nexus.erp.sch.service.ISchAttendanceService;
import com.nexus.erp.sch.service.ISchEmployeeScheduleService;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import com.nexus.erp.sch.service.ISchShiftService;
import com.nexus.erp.sch.service.ISchWorkerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppSchAttendanceController 打卡越权防护单元测试
 * <p>核心验证 ERP-SCH-01: 当前登录用户未绑定 sch_worker 档案时,打卡必须被拒绝,
 * <b>绝不能回退到"全表第一条"</b>(原实现会把考勤写到他人名下)。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class AppSchAttendanceControllerTest {

    @Mock
    private ISchAttendanceService schAttendanceService;
    @Mock
    private ISchEmployeeScheduleService schEmployeeScheduleService;
    @Mock
    private ISchPunchRuleService schPunchRuleService;
    @Mock
    private ISchWorkerService schWorkerService;
    @Mock
    private ISchShiftService schShiftService;
    @Mock
    private SchWorkerMapper workerMapper;
    @Mock
    private SchAttendanceMapper attendanceMapper;
    @Mock
    private SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock
    private SchHolidayMapper holidayMapper;
    @Mock
    private ISchAppApplyService schAppApplyService;

    @InjectMocks
    private AppSchAttendanceController controller;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(999L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void clockIn_unboundWorker_isRejectedWithoutFallbackToOtherWorkers() {
        // 按 user_id 查不到工人档案
        when(workerMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> params = new HashMap<>();
        params.put("latitude", new BigDecimal("31.230416"));
        params.put("longitude", new BigDecimal("121.473701"));

        R<?> result = controller.clockIn(params);

        assertEquals(R.FAIL, result.getCode());
        assertTrue(result.getMsg() != null && result.getMsg().contains("找不到对应的工人信息"), result.getMsg());
        // 绝不能以他人身份打卡
        verify(schAttendanceService, never()).performClockIn(any(), any(), any(), any(), any(),
            any(), any(), any());
        // 关键回归: 只按 user_id 查一次;修复前的实现会再查一次全表(回退"全表第一条")→ 共 2 次
        verify(workerMapper, times(1)).selectList(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clockOut_unboundWorker_isRejectedWithoutFallbackToOtherWorkers() {
        when(workerMapper.selectList(any())).thenReturn(List.of());

        R<?> result = controller.clockOut(new HashMap<>());

        assertEquals(R.FAIL, result.getCode());
        assertTrue(result.getMsg() != null && result.getMsg().contains("找不到对应的工人信息"), result.getMsg());
        verify(schAttendanceService, never()).performClockOut(any(), any(), any(), any(), any(),
            any(), any(), any(), any());
        verify(workerMapper, times(1)).selectList(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void todayStatus_unboundWorker_returnsEmptyInsteadOfOtherWorkerData() {
        when(workerMapper.selectList(any())).thenReturn(List.of());

        R<?> result = controller.getTodayStatus();

        // 不查询任何工人的考勤(不回退他人), 返回空数据
        verify(schAttendanceService, never()).getTodayAttendanceStatus(any());
        assertEquals(R.SUCCESS, result.getCode());
        assertNull(result.getData());
        verify(workerMapper, times(1)).selectList(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void myAttendance_unboundWorker_returnsEmptyRows() {
        when(workerMapper.selectList(any())).thenReturn(List.of());

        R<?> result = controller.getMyAttendance(null, null, null, 1, 20);

        verify(attendanceMapper, never()).selectList(any());
        assertTrue(String.valueOf(result.getData()).contains("total=0"), String.valueOf(result.getData()));
        verify(workerMapper, times(1)).selectList(any());
    }

    /** 未绑定工人时不得写入任何考勤记录 */
    @Test
    @SuppressWarnings("unchecked")
    void clockIn_unboundWorker_neverInsertsAttendance() {
        when(workerMapper.selectList(any())).thenReturn(List.of());

        controller.clockIn(new HashMap<>());

        verify(attendanceMapper, never()).insert(any(SchAttendance.class));
    }
}
