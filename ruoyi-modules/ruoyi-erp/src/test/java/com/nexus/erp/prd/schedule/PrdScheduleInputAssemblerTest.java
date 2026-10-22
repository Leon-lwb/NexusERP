package com.nexus.erp.prd.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.bd.service.IBdRoutingService;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.SchLeaveApply;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchLeaveApplyMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/** APS 输入装配单元测试：正式排产与 What-if 必须共享同一口径，scoped 查询必须 校验失败即拒绝。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdScheduleInputAssemblerTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 24);

    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private PrdCapacityCalendarMapper capacityCalendarMapper;
    @Mock
    private SchHolidayMapper holidayMapper;
    @Mock
    private SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock
    private SchShiftMapper shiftMapper;
    @Mock
    private SchTeamMapper teamMapper;
    @Mock
    private SchLeaveApplyMapper leaveApplyMapper;
    @Mock
    private BdWorkstationMapper workstationMapper;
    @Mock
    private IBdRoutingService routingService;
    @Mock
    private BdRoutingMapper routingMapper;
    @Mock
    private BdRoutingStepMapper routingStepMapper;

    @InjectMocks
    private PrdScheduleInputAssembler assembler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, BdRouting.class);
        TableInfoHelper.initTableInfo(assistant, BdRoutingStep.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, SchHoliday.class);
        TableInfoHelper.initTableInfo(assistant, SchEmployeeSchedule.class);
        TableInfoHelper.initTableInfo(assistant, SchShift.class);
        TableInfoHelper.initTableInfo(assistant, SchTeam.class);
        TableInfoHelper.initTableInfo(assistant, SchLeaveApply.class);
        TableInfoHelper.initTableInfo(assistant, BdWorkstation.class);
    }

    @Test
    void duplicateCurrentActiveRoutesFailClosedBeforeLoadingSteps() {
        PrdWorkOrder workOrder = workOrder(1L, 100L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(routingMapper.selectList(any())).thenReturn(
            List.of(routing(200L, 100L), routing(201L, 100L)));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L)));

        assertTrue(error.getMessage().contains("多条当前有效工艺路线"), error.getMessage());
        verify(routingStepMapper, never()).selectList(any());
    }

    @Test
    void multipleMaterialsLoadRoutesAndStepsInTwoBatchQueriesWithoutServiceNPlusOne() {
        PrdWorkOrder wo1 = workOrder(1L, 100L, "RELEASED");
        PrdWorkOrder wo2 = workOrder(2L, 101L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo2, wo1));
        when(routingMapper.selectList(argThat(wrapper -> {
            Collection<?> values = queryValues(wrapper);
            return wrapper.getSqlSegment().contains("material_id")
                && values.contains(100L) && values.contains(101L);
        }))).thenReturn(List.of(routing(200L, 100L), routing(201L, 101L)));
        when(routingStepMapper.selectList(argThat(wrapper -> {
            Collection<?> values = queryValues(wrapper);
            return wrapper.getSqlSegment().contains("routing_id")
                && values.contains(200L) && values.contains(201L);
        }))).thenReturn(List.of(
            routingStep(210L, 200L, 1, 400L),
            routingStep(211L, 201L, 1, 401L)));
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(14, "EDD"), List.of(1L, 2L));

        assertEquals(List.of(210L), input.getRoutingProvider().apply(100L).stream()
            .map(StepInfo::getStepId).toList());
        assertEquals(List.of(211L), input.getRoutingProvider().apply(101L).stream()
            .map(StepInfo::getStepId).toList());
        verify(routingMapper, times(1)).selectList(any());
        verify(routingStepMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService);
    }

    @Test
    void sameWorkshopUnreferencedWorkstationIsExcludedFromLaborCapacity() {
        PrdWorkOrder workOrder = workOrder(1L, 100L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(routingMapper.selectList(any())).thenReturn(List.of(routing(200L, 100L)));
        when(routingStepMapper.selectList(any())).thenReturn(
            List.of(routingStep(210L, 200L, 1, 400L)));
        when(workstationMapper.selectList(any())).thenReturn(List.of(
            workstation(400L, 10L), workstation(999L, 10L)));
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of(
            capacity(START_DATE, "WORKSTATION", 400L, "12"),
            capacity(START_DATE, "WORKSTATION", 999L, "12")));
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        when(teamMapper.selectList(any())).thenReturn(List.of(team(500L, 10L)));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of(
            employeeSchedule(101L, 500L, 600L, null, START_DATE, 1, "scheduled")));
        when(shiftMapper.selectBatchIds(any())).thenReturn(List.of(shift(600L, "6")));
        when(leaveApplyMapper.selectList(any())).thenReturn(List.of());

        ScheduleInput input = assembler.assemble(scheduleBo(3, "EDD"), List.of(1L));

        Map<String, BigDecimal> day = input.getCapacityByDate().get(START_DATE);
        assertEquals(0, new BigDecimal("6").compareTo(
            day.get(ScheduleModel.resourceKey("WORKSTATION", 400L))));
        assertFalse(day.containsKey(ScheduleModel.resourceKey("WORKSTATION", 999L)), day.toString());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BdWorkstation>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(workstationMapper, times(1)).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("workstation_id"),
            query.getValue().getSqlSegment());
        assertFalse(query.getValue().getSqlSegment().contains("workshop_id"),
            query.getValue().getSqlSegment());
    }

    @Test
    void datetimeScopesUseExclusiveNextDayAndKeepFinalDayFacts() {
        LocalDate lastDay = START_DATE.plusDays(2);
        PrdWorkOrder workOrder = workOrder(1L, 100L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(routingMapper.selectList(any())).thenReturn(List.of(routing(200L, 100L)));
        when(routingStepMapper.selectList(any())).thenReturn(
            List.of(routingStep(210L, 200L, 1, 400L)));
        when(workstationMapper.selectList(any())).thenReturn(List.of(workstation(400L, 10L)));

        PrdCapacityCalendar calendar = capacity(lastDay, "WORKSTATION", 400L, "12");
        calendar.setCalendarDate(atTime(lastDay, 23, 59, 59));
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of(calendar));
        SchHoliday holiday = holiday(lastDay);
        holiday.setHolidayDate(atTime(lastDay, 12, 0, 0));
        SchHoliday outsideHoliday = holiday(lastDay.plusDays(1));
        outsideHoliday.setHolidayDate(atTime(lastDay.plusDays(1), 12, 0, 0));
        when(holidayMapper.selectList(any())).thenReturn(List.of(outsideHoliday, holiday));
        when(teamMapper.selectList(any())).thenReturn(List.of(team(500L, 10L)));
        SchEmployeeSchedule available =
            employeeSchedule(101L, 500L, 600L, null, lastDay, 1, "scheduled");
        available.setScheduledDate(atTime(lastDay, 23, 59, 59));
        SchEmployeeSchedule absent =
            employeeSchedule(102L, 500L, 600L, null, lastDay, 1, "scheduled");
        absent.setScheduledDate(atTime(lastDay, 12, 0, 0));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of(available, absent));
        when(shiftMapper.selectBatchIds(any())).thenReturn(List.of(shift(600L, "6")));
        SchLeaveApply leave = approvedLeave(102L, lastDay);
        leave.setStartDate(atTime(lastDay, 12, 0, 0));
        leave.setEndDate(atTime(lastDay, 23, 59, 59));
        when(leaveApplyMapper.selectList(any())).thenReturn(List.of(leave));

        ScheduleInput input = assembler.assemble(scheduleBo(3, "EDD"), List.of(1L));

        assertTrue(input.getHolidays().contains(lastDay), input.getHolidays().toString());
        assertFalse(input.getHolidays().contains(lastDay.plusDays(1)), input.getHolidays().toString());
        assertEquals(0, new BigDecimal("6").compareTo(input.getCapacityByDate().get(lastDay)
            .get(ScheduleModel.resourceKey("WORKSTATION", 400L))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> capacityQuery =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(capacityCalendarMapper).selectList(capacityQuery.capture());
        assertHalfOpenWindow(capacityQuery.getValue(), "calendar_date", START_DATE, lastDay.plusDays(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SchHoliday>> holidayQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(holidayMapper).selectList(holidayQuery.capture());
        assertHalfOpenWindow(holidayQuery.getValue(), "holiday_date", START_DATE, lastDay.plusDays(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SchEmployeeSchedule>> scheduleQuery =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(employeeScheduleMapper).selectList(scheduleQuery.capture());
        assertHalfOpenWindow(scheduleQuery.getValue(), "scheduled_date", START_DATE, lastDay.plusDays(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SchLeaveApply>> leaveQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(leaveApplyMapper).selectList(leaveQuery.capture());
        String leaveSql = leaveQuery.getValue().getSqlSegment();
        assertTrue(leaveSql.contains("start_date <"), leaveSql);
        assertFalse(leaveSql.contains("start_date <="), leaveSql);
        assertTrue(leaveSql.contains("end_date >="), leaveSql);
        assertDateParameter(leaveQuery.getValue(), START_DATE);
        assertDateParameter(leaveQuery.getValue(), lastDay.plusDays(1));
    }

    @Test
    void routeWorkstationDerivesWorkshopAndAppliesLaborWhenWorkOrderWorkshopIsNull() {
        PrdWorkOrder workOrder = workOrder(1L, 100L, "RELEASED");
        workOrder.setWorkshopId(null);
        workOrder.setWorkshopName(null);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(routingMapper.selectList(any())).thenReturn(List.of(routing(200L, 100L)));
        when(routingStepMapper.selectList(any())).thenReturn(
            List.of(routingStep(210L, 200L, 1, 400L)));
        when(workstationMapper.selectList(any())).thenReturn(List.of(workstation(400L, 10L)));
        when(capacityCalendarMapper.selectList(any())).thenReturn(
            List.of(capacity(START_DATE, "WORKSTATION", 400L, "12")));
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        when(teamMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("workshop_id") && queryValues(wrapper).contains(10L))))
            .thenReturn(List.of(team(500L, 10L)));
        when(employeeScheduleMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("team_id") && queryValues(wrapper).contains(500L))))
            .thenReturn(List.of(employeeSchedule(101L, 500L, 600L, null,
                START_DATE, 1, "scheduled")));
        when(shiftMapper.selectBatchIds(argThat(ids -> ids.contains(600L))))
            .thenReturn(List.of(shift(600L, "6")));
        when(leaveApplyMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("worker_id") && queryValues(wrapper).contains(101L))))
            .thenReturn(List.of());

        ScheduleInput input = assembler.assemble(scheduleBo(3, "EDD"), List.of(1L));

        assertEquals(0, new BigDecimal("6").compareTo(input.getCapacityByDate()
            .get(START_DATE).get(ScheduleModel.resourceKey("WORKSTATION", 400L))));
        verify(workstationMapper, times(1)).selectList(any());
        verify(teamMapper).selectList(any());
        verify(employeeScheduleMapper).selectList(any());
        verify(leaveApplyMapper).selectList(any());
    }

    @Test
    void scopedAssemblyQueriesOnlyRelatedCapacityAndLaborResources() {
        PrdWorkOrder workOrder = workOrder(1L, 100L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(routingMapper.selectList(any())).thenReturn(List.of(routing(200L, 100L)));
        when(routingStepMapper.selectList(any())).thenReturn(
            List.of(routingStep(210L, 200L, 1, 400L)));
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of(
            capacity(START_DATE, "WORKSTATION", 400L, "8"),
            capacity(START_DATE, "WORKSTATION", 999L, "99")));
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        when(teamMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("workshop_id") && queryValues(wrapper).contains(10L))))
            .thenReturn(List.of(team(500L, 10L)));
        when(employeeScheduleMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("team_id") && queryValues(wrapper).contains(500L))))
            .thenReturn(List.of(employeeSchedule(101L, 500L, 600L, null,
                START_DATE, 1, "scheduled")));
        when(shiftMapper.selectBatchIds(argThat(ids -> ids.contains(600L))))
            .thenReturn(List.of(shift(600L, "8")));
        when(leaveApplyMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("worker_id") && queryValues(wrapper).contains(101L))))
            .thenReturn(List.of());
        when(workstationMapper.selectList(any()))
            .thenReturn(List.of(workstation(400L, 10L), workstation(999L, 99L)));

        ScheduleInput input = assembler.assemble(scheduleBo(3, "EDD"), List.of(1L));

        Map<String, BigDecimal> day = input.getCapacityByDate().get(START_DATE);
        assertTrue(day.containsKey(ScheduleModel.resourceKey("WORKSTATION", 400L)), day.toString());
        assertFalse(day.containsKey(ScheduleModel.resourceKey("WORKSTATION", 999L)), day.toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> capacityQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(capacityCalendarMapper).selectList(capacityQuery.capture());
        assertTrue(capacityQuery.getValue().getSqlSegment().contains("object_id"),
            capacityQuery.getValue().getSqlSegment());
        assertFalse(queryValues(capacityQuery.getValue()).contains(999L),
            queryValues(capacityQuery.getValue()).toString());
        verify(teamMapper).selectList(any());
        verify(employeeScheduleMapper).selectList(any());
        verify(leaveApplyMapper).selectList(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BdWorkstation>> workstationQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(workstationMapper).selectList(workstationQuery.capture());
        Collection<?> workstationValues = queryValues(workstationQuery.getValue());
        String workstationSql = workstationQuery.getValue().getSqlSegment();
        assertTrue(workstationSql.contains("workstation_id"), workstationSql);
        assertFalse(workstationSql.contains("workshop_id"), workstationSql);
        assertTrue(workstationValues.contains(400L), workstationValues.toString());
        assertFalse(workstationValues.contains(999L), workstationValues.toString());
    }

    @Test
    void fullAndScopedOverloadsUseSameAssemblyRulesForSameWorkOrders() {
        PrdWorkOrder wo1 = workOrder(1L, 100L, "RELEASED");
        PrdWorkOrder wo2 = workOrder(2L, 101L, "IN_PROGRESS");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo2, wo1));
        when(routingMapper.selectList(any())).thenReturn(
            List.of(routing(200L, 100L), routing(201L, 101L)));
        when(routingStepMapper.selectList(any())).thenReturn(List.of(
            routingStep(210L, 200L, 1, 400L),
            routingStep(211L, 201L, 1, 401L)));
        stubEmptyCapacityInputs();

        ScheduleInput full = assembler.assemble(scheduleBo(14, "EDD"));
        ScheduleInput scoped = assembler.assemble(scheduleBo(14, "EDD"), List.of(2L, 1L));

        assertEquals(full.getWorkOrders().stream().map(ScheduleModel.WoInfo::getWoId).toList(),
            scoped.getWorkOrders().stream().map(ScheduleModel.WoInfo::getWoId).toList());
        assertEquals(full.getRoutingProvider().apply(100L).stream().map(StepInfo::getStepId).toList(),
            scoped.getRoutingProvider().apply(100L).stream().map(StepInfo::getStepId).toList());
        assertEquals(full.getCapacityByDate(), scoped.getCapacityByDate());
        assertEquals(full.getHolidays(), scoped.getHolidays());
    }

    @Test
    void assembleBuildsCrInputWithOneWorkOrderAndOneRoutingStep() {
        PrdSchedulePlanBo bo = scheduleBo(14, "CR");
        bo.setWorkshopId(10L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(bo);

        assertEquals(START_DATE, input.getStartDate());
        assertEquals(14, input.getHorizonDays());
        assertEquals(Strategy.CR, input.getStrategy());
        assertEquals(1, input.getWorkOrders().size());
        assertEquals(1L, input.getWorkOrders().get(0).getWoId());
        assertEquals(100L, input.getWorkOrders().get(0).getMaterialId());
        assertEquals(0, BigDecimal.TEN.compareTo(input.getWorkOrders().get(0).getRemainingQty()));
        assertTrue(input.getCapacityByDate().isEmpty());
        assertTrue(input.getHolidays().isEmpty());

        List<StepInfo> steps = input.getRoutingProvider().apply(100L);
        assertEquals(1, steps.size());
        StepInfo step = steps.get(0);
        assertEquals(10, step.getSeqNum());
        assertEquals("P-1", step.getProcessCode());
        assertEquals("加工", step.getProcessName());
        assertEquals(400L, step.getWorkstationId());
        assertEquals("CNC-01", step.getWorkstationName());
        assertEquals(0, BigDecimal.ONE.compareTo(step.getStandardHours()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queryCaptor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("wo_status"), sql);
        assertTrue(sql.contains("workshop_id"), sql);
        assertTrue(queryValues(queryCaptor.getValue()).contains(10L));
        verify(routingMapper).selectList(any());
    }

    @Test
    void assemblePreservesSqlDateLogicalValuesAcrossJvmDefaultTimeZones() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            PrdSchedulePlanBo bo = scheduleBo(3, "EDD");
            bo.setStartDate(java.sql.Date.valueOf("2026-09-24"));
            PrdWorkOrder workOrder = workOrder(1L, 100L, "CREATED");
            workOrder.setPlanEndDate(java.sql.Date.valueOf("2026-09-30"));
            when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
            stubRouting(100L);
            stubEmptyCapacityInputs();

            ScheduleInput input = assembler.assemble(bo);

            assertEquals(LocalDate.of(2026, 9, 24), input.getStartDate());
            assertEquals(LocalDate.of(2026, 9, 30), input.getWorkOrders().get(0).getDueDate());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void assembleConvertsSqlDateStartAndDueDates() {
        PrdSchedulePlanBo bo = scheduleBo(3, "EDD");
        bo.setStartDate(java.sql.Date.valueOf("2026-09-24"));
        PrdWorkOrder workOrder = workOrder(1L, 100L, "CREATED");
        workOrder.setPlanEndDate(java.sql.Date.valueOf("2026-09-30"));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(bo);

        assertEquals(LocalDate.of(2026, 9, 24), input.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 30), input.getWorkOrders().get(0).getDueDate());
    }

    @Test
    void assembleAppliesCalendarHolidayShiftTeamLeaveAndRestDayCapacity() {
        LocalDate day1 = START_DATE;
        LocalDate day2 = START_DATE.plusDays(1);
        LocalDate day3 = START_DATE.plusDays(2);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of(
            capacity(day1, "WORKSTATION", 400L, "12"),
            capacity(day1, "WORKSHOP", 10L, "20"),
            capacity(day2, "WORKSTATION", 400L, "8"),
            capacity(day3, "WORKSTATION", 999L, "5")
        ));
        when(holidayMapper.selectList(any())).thenReturn(List.of(holiday(day3)));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of(
            employeeSchedule(101L, 500L, 600L, 601L, day1, 1, "scheduled"),
            employeeSchedule(102L, 500L, 600L, null, day1, 1, "scheduled"),
            employeeSchedule(103L, 500L, 600L, null, day2, 0, "rest")
        ));
        when(shiftMapper.selectBatchIds(any())).thenReturn(List.of(
            shift(600L, "8"),
            shift(601L, "6")
        ));
        when(teamMapper.selectList(any())).thenReturn(List.of(team(500L, 10L)));
        when(leaveApplyMapper.selectList(any())).thenReturn(List.of(approvedLeave(102L, day1)));
        when(workstationMapper.selectList(any())).thenReturn(List.of(workstation(400L, 10L)));

        ScheduleInput input = assembler.assemble(scheduleBo(3, "CR"));

        Map<LocalDate, Map<String, BigDecimal>> capacity = input.getCapacityByDate();
        assertEquals(START_DATE, input.getStartDate());
        assertEquals(3, input.getHorizonDays());
        assertTrue(input.getHolidays().contains(day3));
        assertEquals(0, new BigDecimal("6").compareTo(
            capacity.get(day1).get(ScheduleModel.resourceKey("WORKSTATION", 400L))));
        assertEquals(0, new BigDecimal("6").compareTo(
            capacity.get(day1).get(ScheduleModel.resourceKey("WORKSHOP", 10L))));
        assertEquals(0, capacity.get(day2).get(ScheduleModel.resourceKey("WORKSTATION", 400L)).signum());
        assertEquals(0, capacity.get(day2).get(ScheduleModel.resourceKey("WORKSHOP", 10L)).signum());
        assertFalse(capacity.getOrDefault(day3, Map.of())
            .containsKey(ScheduleModel.resourceKey("WORKSTATION", 999L)));
    }

    @Test
    void assembleDefaultsHorizonToFourteen() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(null, "EDD"));

        assertEquals(14, input.getHorizonDays());
    }

    @Test
    void assembleClampsHorizonToAtLeastOneDay() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(0, "PRIORITY"));

        assertEquals(1, input.getHorizonDays());
        assertEquals(Strategy.PRIORITY, input.getStrategy());
    }

    @Test
    void scopedAssemblyUsesOnlyDeduplicatedRequestedIdsInRequestedOrder() {
        PrdWorkOrder firstFromMapper = workOrder(1L, 100L, "CREATED");
        PrdWorkOrder secondFromMapper = workOrder(2L, 100L, "APPROVED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(firstFromMapper, secondFromMapper));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(14, "CR"), List.of(2L, 1L, 2L));

        assertEquals(List.of(2L, 1L), input.getWorkOrders().stream()
            .map(ScheduleModel.WoInfo::getWoId)
            .toList());
        verify(workOrderMapper, times(1)).selectList(any());
        verify(routingMapper, times(1)).selectList(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queryCaptor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("wo_id"), sql);
        Collection<?> queryValues = queryValues(queryCaptor.getValue());
        assertTrue(queryValues.contains(1L), queryValues.toString());
        assertTrue(queryValues.contains(2L), queryValues.toString());
    }

    @Test
    void scopedAssemblyIgnoresWorkshopFilterWhenIdsAreExplicit() {
        PrdSchedulePlanBo bo = scheduleBo(14, "EDD");
        bo.setWorkshopId(10L);
        PrdWorkOrder selected = workOrder(1L, 100L, "CREATED");
        selected.setWorkshopId(20L);
        selected.setWorkshopName("二车间");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(selected));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(bo, List.of(1L));

        assertEquals(List.of(1L), input.getWorkOrders().stream()
            .map(ScheduleModel.WoInfo::getWoId)
            .toList());
        assertEquals(20L, input.getWorkOrders().get(0).getWorkshopId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queryCaptor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("wo_id"), sql);
        assertFalse(sql.contains("workshop_id"), sql);
        assertFalse(sql.contains("wo_status"), sql);
        Collection<?> queryValues = queryValues(queryCaptor.getValue());
        assertEquals(1, queryValues.size());
        assertTrue(queryValues.contains(1L), queryValues.toString());
        assertFalse(queryValues.contains(10L), queryValues.toString());
    }

    @Test
    void scopedAssemblyRejectsNullOrEmptyIdsWithoutFallingBackToAllWorkOrders() {
        ServiceException nullIds = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), null));
        ServiceException emptyIds = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of()));

        assertTrue(nullIds.getMessage().contains("工单ID"), nullIds.getMessage());
        assertTrue(emptyIds.getMessage().contains("工单ID"), emptyIds.getMessage());
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    void scopedAssemblyRejectsMissingIdsWithoutFallingBackToAllWorkOrders() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L, 2L)));

        assertTrue(error.getMessage().contains("2"), error.getMessage());
        verify(workOrderMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService);
    }

    @Test
    void scopedAssemblyRejectsDuplicateMapperRowsBeforeLoadingDependencies() {
        PrdWorkOrder first = workOrder(1L, 100L, "CREATED");
        PrdWorkOrder duplicate = workOrder(1L, 100L, "CREATED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(first, duplicate));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L)));

        assertTrue(error.getMessage().contains("重复"), error.getMessage());
        verify(workOrderMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService, routingMapper, routingStepMapper, capacityCalendarMapper, holidayMapper,
            employeeScheduleMapper, shiftMapper, teamMapper, leaveApplyMapper, workstationMapper);
    }

    @Test
    void scopedAssemblyRejectsUnschedulableIdsWithoutFallingBackToAllWorkOrders() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "COMPLETED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L)));

        assertTrue(error.getMessage().contains("不可排"), error.getMessage());
        assertTrue(error.getMessage().contains("1"), error.getMessage());
        verify(workOrderMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASELINE_CURRENT", "CURRENT", " baseline_current "})
    void assembleRejectsCurrentBaselineStrategies(String strategy) {
        PrdSchedulePlanBo bo = scheduleBo(14, strategy);

        ServiceException error = assertThrows(ServiceException.class, () -> assembler.assemble(bo));

        assertTrue(error.getMessage().contains("基线"), error.getMessage());
        verify(workOrderMapper, never()).selectList(any());
    }

    private PrdCapacityCalendar capacity(LocalDate day, String objectType, Long objectId, String hours) {
        PrdCapacityCalendar calendar = new PrdCapacityCalendar();
        calendar.setCalendarDate(toDate(day));
        calendar.setObjectType(objectType);
        calendar.setObjectId(objectId);
        calendar.setRemainingHours(new BigDecimal(hours));
        calendar.setStatus("ACTIVE");
        return calendar;
    }

    private SchHoliday holiday(LocalDate day) {
        SchHoliday holiday = new SchHoliday();
        holiday.setHolidayDate(toDate(day));
        holiday.setHolidayName("停工日");
        holiday.setIsWorkingDay(0);
        return holiday;
    }

    private SchEmployeeSchedule employeeSchedule(Long workerId, Long teamId, Long shiftId,
                                                   Long actualShiftId, LocalDate day,
                                                   Integer isWorkingDay, String status) {
        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
        schedule.setWorkerId(workerId);
        schedule.setTeamId(teamId);
        schedule.setShiftId(shiftId);
        schedule.setActualShiftId(actualShiftId);
        schedule.setScheduledDate(toDate(day));
        schedule.setIsWorkingDay(isWorkingDay);
        schedule.setScheduleStatus(status);
        return schedule;
    }

    private SchShift shift(Long shiftId, String hours) {
        SchShift shift = new SchShift();
        shift.setShiftId(shiftId);
        shift.setWorkHours(new BigDecimal(hours));
        return shift;
    }

    private SchTeam team(Long teamId, Long workshopId) {
        SchTeam team = new SchTeam();
        team.setTeamId(teamId);
        team.setWorkshopId(workshopId);
        return team;
    }

    private SchLeaveApply approvedLeave(Long workerId, LocalDate day) {
        SchLeaveApply leave = new SchLeaveApply();
        leave.setWorkerId(workerId);
        leave.setStartDate(toDate(day));
        leave.setEndDate(toDate(day));
        leave.setApplyStatus("approved");
        return leave;
    }

    private BdWorkstation workstation(Long workstationId, Long workshopId) {
        BdWorkstation workstation = new BdWorkstation();
        workstation.setWorkstationId(workstationId);
        workstation.setWorkshopId(workshopId);
        return workstation;
    }

    private BdRouting routing(Long routingId, Long materialId) {
        BdRouting routing = new BdRouting();
        routing.setRoutingId(routingId);
        routing.setMaterialId(materialId);
        routing.setIsCurrent(1L);
        routing.setStatus(1L);
        return routing;
    }

    private BdRoutingStep routingStep(Long stepId, Long routingId, Integer sequence,
                                             Long workstationId) {
        BdRoutingStep step = new BdRoutingStep();
        step.setStepId(stepId);
        step.setRoutingId(routingId);
        step.setSequenceNum(sequence);
        step.setProcessCode("P-" + stepId);
        step.setProcessName("工序-" + stepId);
        step.setWorkstationId(workstationId);
        step.setWorkstationName("WS-" + workstationId);
        step.setStandardHours(BigDecimal.ONE);
        step.setSetupHours(BigDecimal.ZERO);
        step.setIsOutsource(0);
        return step;
    }

    private void stubRouting(Long materialId) {
        when(routingMapper.selectList(any())).thenReturn(List.of(routing(200L, materialId)));

        BdRoutingStep step = new BdRoutingStep();
        step.setStepId(201L);
        step.setRoutingId(200L);
        step.setSequenceNum(10);
        step.setProcessId(300L);
        step.setProcessCode("P-1");
        step.setProcessName("加工");
        step.setWorkstationId(400L);
        step.setWorkstationName("CNC-01");
        step.setStandardHours(BigDecimal.ONE);
        step.setSetupHours(BigDecimal.ZERO);
        step.setIsOutsource(0);
        when(routingStepMapper.selectList(any())).thenReturn(List.of(step));
    }

    private void stubEmptyCapacityInputs() {
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of());
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        when(teamMapper.selectList(any())).thenReturn(List.of());
    }

    private PrdSchedulePlanBo scheduleBo(Integer horizonDays, String strategy) {
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setStartDate(toDate(START_DATE));
        bo.setHorizonDays(horizonDays);
        bo.setStrategy(strategy);
        return bo;
    }

    private PrdWorkOrder workOrder(Long woId, Long materialId, String status) {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(woId);
        wo.setWoNo("WO-" + woId);
        wo.setMaterialId(materialId);
        wo.setMaterialCode("MAT-" + materialId);
        wo.setMaterialName("物料-" + materialId);
        wo.setPlannedQty(BigDecimal.TEN);
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setPriority(1L);
        wo.setWoStatus(status);
        wo.setWorkshopId(10L);
        wo.setWorkshopName("一车间");
        return wo;
    }

    private void assertHalfOpenWindow(Wrapper<?> wrapper, String column,
                                      LocalDate startInclusive, LocalDate endExclusive) {
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains(column + " >="), sql);
        assertTrue(sql.contains(column + " <"), sql);
        assertFalse(sql.contains(column + " <="), sql);
        assertDateParameter(wrapper, startInclusive);
        assertDateParameter(wrapper, endExclusive);
    }

    private void assertDateParameter(Wrapper<?> wrapper, LocalDate day) {
        long expected = day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertTrue(queryValues(wrapper).stream()
            .filter(Date.class::isInstance)
            .map(Date.class::cast)
            .anyMatch(value -> value.getTime() == expected), queryValues(wrapper).toString());
    }

    private Date atTime(LocalDate day, int hour, int minute, int second) {
        return Date.from(day.atTime(hour, minute, second)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private Collection<?> queryValues(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
    }
}
