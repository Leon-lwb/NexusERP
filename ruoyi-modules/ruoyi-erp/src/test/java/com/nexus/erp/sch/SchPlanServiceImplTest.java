package com.nexus.erp.sch;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchPlanTeamMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.impl.SchPlanServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SchPlanServiceImpl 单元测试（ERP-SCH-12 计划状态校验 / ERP-SCH-13 删除级联保护）
 * <p>修复前：{@code updateByBo} 只做小写归一化，任意状态字符串都能落库；
 * {@code deleteByIds} 无条件级联删除排班明细，已产生考勤的排班被删后 schedule_id 悬空。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
@ExtendWith(MockitoExtension.class)
class SchPlanServiceImplTest {

    @Mock
    private SchPlanMapper baseMapper;
    @Mock
    private SchShiftMapper schShiftMapper;
    @Mock
    private SchTeamScheduleMapper schTeamScheduleMapper;
    @Mock
    private SchEmployeeScheduleMapper schEmployeeScheduleMapper;
    @Mock
    private SchPlanTeamMapper schPlanTeamMapper;
    @Mock
    private SchAttendanceMapper schAttendanceMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private SchPlanServiceImpl service;

    private void invokeNormalize(SchPlan plan) throws Exception {
        Method m = SchPlanServiceImpl.class.getDeclaredMethod("normalizeDictValues", SchPlan.class);
        m.setAccessible(true);
        try {
            m.invoke(service, plan);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    @Test
    void normalize_rejectsUnknownStatus() {
        SchPlan plan = new SchPlan();
        plan.setStatus("WEIRD_STATUS");

        ServiceException ex = assertThrows(ServiceException.class, () -> invokeNormalize(plan));
        assertTrue(ex.getMessage().contains("状态非法"));
    }

    @Test
    void normalize_rejectsUnknownPeriodType() {
        SchPlan plan = new SchPlan();
        plan.setPeriodType("fortnight");

        ServiceException ex = assertThrows(ServiceException.class, () -> invokeNormalize(plan));
        assertTrue(ex.getMessage().contains("周期类型非法"));
    }

    @Test
    void normalize_lowercasesLegalValues() throws Exception {
        SchPlan plan = new SchPlan();
        plan.setStatus("PUBLISHED");
        plan.setPeriodType("Month");

        invokeNormalize(plan);

        assertEquals("published", plan.getStatus());
        assertEquals("month", plan.getPeriodType());
    }

    @Test
    void delete_rejectedWhenScheduleReferencedByAttendance() {
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setScheduleId(900L);
        schedule.setPlanId(7L);
        when(schEmployeeScheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(schAttendanceMapper.selectCount(any())).thenReturn(3L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{7L}));
        assertTrue(ex.getMessage().contains("考勤记录"));
        // 关键回归：被拦截时不得删任何子表/主表
        verify(schShiftMapper, never()).delete(any());
        verify(baseMapper, never()).deleteBatchIds(any());
    }

    @Test
    void delete_proceedsWhenNoAttendanceReference() {
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setScheduleId(900L);
        schedule.setPlanId(7L);
        when(schEmployeeScheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(schAttendanceMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.deleteBatchIds(any())).thenReturn(1);

        int rows = service.deleteByIds(new Long[]{7L});

        assertEquals(1, rows);
        verify(schEmployeeScheduleMapper).delete(any());
        verify(schAttendanceMapper).selectCount(any());
    }
}
