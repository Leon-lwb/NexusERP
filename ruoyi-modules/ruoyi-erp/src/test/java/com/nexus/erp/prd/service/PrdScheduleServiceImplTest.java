package com.nexus.erp.prd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.PrdScheduleNumberAllocator;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.prd.service.impl.PrdScheduleServiceImpl;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/** 排产方案幂等持久化、发布 CAS 与条件补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdScheduleServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 24);
    private static final long CREATE_MILLIS = 1_795_622_400_000L;
    private static final long UPDATE_MILLIS = 1_795_622_460_000L;
    private static final long RUN_ID = 99L;
    private static final long ACTION_ID = 502L;
    private static final String IDEM_KEY = "decision-idem-502";
    private static final String SCENARIO = "APS_EDD";
    private static final String WORK_ORDER_IDS_JSON = "[\"11\",\"12\"]";

    @Mock
    private PrdSchedulePlanMapper planMapper;
    @Mock
    private PrdScheduleTaskMapper taskMapper;
    @Mock
    private PrdScheduleTaskDayLoadMapper taskDayLoadMapper;
    @Mock
    private PrdScheduleCapacityBookingMapper bookingMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private PrdScheduleInputAssembler scheduleInputAssembler;
    @Mock
    private PrdScheduleNumberAllocator numberAllocator;
    @Mock
    private PrdCapacityCalendarMapper capacityCalendarMapper;
    @Mock
    private IPrdCapacityCalendarService capacityCalendarService;
    @Mock
    private SchShiftMapper shiftMapper;
    @Mock
    private SchHolidayMapper holidayMapper;

    @InjectMocks
    private PrdScheduleServiceImpl scheduleService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleTask.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleTaskDayLoad.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, SchShift.class);
    }

    @BeforeEach
    void acceptUnrelatedLegacyGeneratedDayRows() {
        Mockito.lenient().when(numberAllocator.nextNumber(any(String.class), any(LocalDate.class)))
            .thenReturn("APS202609240001");
        Mockito.lenient()
                .when(taskDayLoadMapper.insert(any(PrdScheduleTaskDayLoad.class)))
                .thenReturn(1);
        Mockito.lenient().when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);
        Mockito.lenient().when(workOrderMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation ->
            workOrderMapper.selectById(11L));
        Mockito.lenient()
                .when(shiftMapper.selectList(any(Wrapper.class)))
                .thenAnswer(
                        invocation -> {
                            Map<String, Object> params = parameters(invocation.getArgument(0));
                            List<SchShift> candidates = new ArrayList<>();
                            for (long id = 1; id <= 2; id++) {
                                if (!params.containsValue(id)) continue;
                                SchShift shift = new SchShift();
                                shift.setShiftId(id);
                                shift.setTenantId("000000");
                                shift.setStartTime(Time.valueOf(id == 1 ? "08:00:00" : "16:00:00"));
                                shift.setEndTime(Time.valueOf(id == 1 ? "16:00:00" : "23:00:00"));
                                shift.setIsCrossDay(0);
                                candidates.add(shift);
                            }
                            return candidates;
                        });
    }

    @Test
    void scopedRunPersistsStructuredScopeVersionAndTasks() throws ReflectiveOperationException {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        List<Long> ids = List.of(11L, 12L);
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        stubPlanInsert(8001L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            PrdSchedulePlanVo vo = scheduleService.runSchedule(bo, ids);

            assertEquals(8001L, vo.getPlanId());
            assertEquals("COMPLETED", vo.getStatus());
            verify(numberAllocator).nextNumber("000000", START);
            assertEquals(RUN_ID, vo.getDecisionRunId());
            assertEquals(ACTION_ID, vo.getDecisionActionId());
            assertEquals(IDEM_KEY, vo.getDecisionIdempotencyKey());
            assertEquals(SCENARIO, vo.getDecisionScenarioCode());
            assertEquals(WORK_ORDER_IDS_JSON, vo.getDecisionWorkOrderIdsJson());
            assertEquals(0, vo.getVersion());
            ArgumentCaptor<PrdSchedulePlan> savedPlan = ArgumentCaptor.forClass(PrdSchedulePlan.class);
            verify(planMapper).insert(savedPlan.capture());
            var arithmeticVersion =
                    Arrays.stream(PrdSchedulePlan.class.getMethods())
                            .filter(method -> method.getName().equals("getCapacityCalcVersion"))
                            .findFirst()
                            .orElse(null);
            Assertions.assertNotNull(
                    arithmeticVersion,
                    "new schedules need an explicit arithmetic version independent from optimistic-lock version");
            assertEquals(2, arithmeticVersion.invoke(savedPlan.getValue()));
            ArgumentCaptor<PrdScheduleTask> task = ArgumentCaptor.forClass(PrdScheduleTask.class);
            verify(taskMapper).insert(task.capture());
            assertEquals(Date.from(START.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),
                task.getValue().getStartTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void generatedScheduleWritesDailyLoadBelongingToItsOwnTaskWithoutBookingCalendar() {
        PrdSchedulePlanBo bo = scheduleBo();
        ScheduleInput input = scheduleInput();
        StepInfo step = input.getRoutingProvider().apply(101L).get(0);
        step.setStandardHours(new BigDecimal("0.07"));
        step.setSetupHours(new BigDecimal("0.10"));
        when(scheduleInputAssembler.assemble(bo)).thenReturn(input);
        stubPlanInsert(8301L);
        doAnswer(invocation -> {
            PrdScheduleTask task = invocation.getArgument(0);
            task.setTaskId(9301L);
            return 1;
        }).when(taskMapper).insert(any(PrdScheduleTask.class));

        PrdSchedulePlanVo result = TenantHelper.dynamic("000000", () -> scheduleService.runSchedule(bo));

        assertEquals("COMPLETED", result.getStatus());
        ArgumentCaptor<PrdScheduleTaskDayLoad> saved = ArgumentCaptor.forClass(PrdScheduleTaskDayLoad.class);
        verify(taskDayLoadMapper).insert(saved.capture());
        assertEquals(8301L, saved.getValue().getPlanId());
        assertEquals(9301L, saved.getValue().getTaskId());
        assertEquals("000000", saved.getValue().getTenantId());
        assertEquals("WORKSTATION", saved.getValue().getResourceType());
        assertEquals(401L, saved.getValue().getResourceId());
        assertEquals(START, saved.getValue().getLoadDate().toInstant()
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
        assertEquals(0, new BigDecimal("0.17").compareTo(saved.getValue().getPlannedHours()));
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }
    @Test
    void ordinaryRunIgnoresForgedRequestAuditAndUsesSessionIdentity() {
        PrdSchedulePlanBo bo = scheduleBo();
        /** 这些继承自 BaseEntity 的字段可由 {@code /erp/prd/schedule/run} 请求的 JSON 绑定。 */
        bo.setCreateBy(90001L);
        bo.setUpdateBy(90002L);
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        stubPlanInsert(8401L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);

        try (MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            scheduleService.runSchedule(bo);
            login.verify(LoginHelper::getUserId);
        }

        ArgumentCaptor<PrdSchedulePlan> saved = ArgumentCaptor.forClass(PrdSchedulePlan.class);
        verify(planMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getCreateBy());
        assertEquals(42L, saved.getValue().getUpdateBy());
    }

    @Test
    void scopedRunKeepsDecisionActorAuditRatherThanThreadSessionUser() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        bo.setCreateBy(503L);
        bo.setUpdateBy(504L);
        List<Long> ids = List.of(11L, 12L);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        stubPlanInsert(8402L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);

        try (MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            scheduleService.runSchedule(bo, ids);
            login.verify(LoginHelper::getUserId, never());
        }

        ArgumentCaptor<PrdSchedulePlan> saved = ArgumentCaptor.forClass(PrdSchedulePlan.class);
        verify(planMapper).insert(saved.capture());
        assertEquals(503L, saved.getValue().getCreateBy());
        assertEquals(504L, saved.getValue().getUpdateBy());
    }
    @Test
    void ordinaryRunRejectsForgedDecisionOriginFieldsBeforeAssemblyOrNumberReservation() {
        PrdSchedulePlanBo bo = scheduleBo();
        bo.setDecisionRunId(99L);
        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        bo.setDecisionRunId(null);
        bo.setDecisionActionId(502L);
        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        bo.setDecisionActionId(null);
        bo.setDecisionScenarioCode("APS_EDD");
        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        bo.setDecisionScenarioCode(null);
        bo.setDecisionWorkOrderIdsJson("[\"11\"]");
        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        verifyNoInteractions(scheduleInputAssembler, numberAllocator, taskMapper);
    }

    @Test
    void ordinaryRunExplainsZeroHourInternalStepAsBusinessErrorBeforePersisting() {
        PrdSchedulePlanBo bo = scheduleBo();
        ScheduleInput input = scheduleInput();
        StepInfo step = input.getRoutingProvider().apply(101L).get(0);
        step.setStandardHours(BigDecimal.ZERO);
        step.setSetupHours(BigDecimal.ZERO);
        when(scheduleInputAssembler.assemble(bo)).thenReturn(input);

        ServiceException error = assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        assertTrue(error.getMessage().contains("工时"), error.getMessage());
        verifyNoInteractions(numberAllocator, taskMapper);
        verify(planMapper, never()).insert(any(PrdSchedulePlan.class));
    }

    @Test
    void allWorkOrdersSkippedDoesNotPersistPublishableEmptyV2Plan() {
        PrdSchedulePlanBo bo = scheduleBo();
        ScheduleInput input = scheduleInput();
        input.setRoutingProvider(materialId -> List.of());
        when(scheduleInputAssembler.assemble(bo)).thenReturn(input);

        ServiceException error = assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
        assertTrue(error.getMessage().contains("可排") || error.getMessage().contains("任务"), error.getMessage());
        verifyNoInteractions(numberAllocator, taskMapper);
        verify(planMapper, never()).insert(any(PrdSchedulePlan.class));
    }

    @Test
    void originalRunUsesFullAssemblerForOrdinaryUiRows() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        stubPlanInsert(8002L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);

        PrdSchedulePlanVo vo = scheduleService.runSchedule(bo);

        assertEquals(8002L, vo.getPlanId());
        verify(scheduleInputAssembler).assemble(bo);
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), any());
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }

    @Test
    void newCompletedSchedulePersistsSeparateProcessAndSetupWithoutReservingCapacity() {
        PrdSchedulePlanBo bo = scheduleBo();
        ScheduleInput input = scheduleInput();
        StepInfo step = input.getRoutingProvider().apply(101L).get(0);
        step.setStandardHours(new BigDecimal("0.07"));
        step.setSetupHours(new BigDecimal("0.10"));
        when(scheduleInputAssembler.assemble(bo)).thenReturn(input);
        stubPlanInsert(8004L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);

        PrdSchedulePlanVo result = scheduleService.runSchedule(bo);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(0, new BigDecimal("0.17").compareTo(result.getTotalHours()));
        ArgumentCaptor<PrdScheduleTask> saved = ArgumentCaptor.forClass(PrdScheduleTask.class);
        verify(taskMapper).insert(saved.capture());
        assertEquals(0, new BigDecimal("0.07").compareTo(saved.getValue().getProcessHours()));
        assertEquals(0, new BigDecimal("0.10").compareTo(saved.getValue().getSetupHours()));
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }

    @Test
    void planInsertZeroDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        when(planMapper.insert(any(PrdSchedulePlan.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));

        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void missingGeneratedPlanIdDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        doAnswer(invocation -> {
            PrdSchedulePlan plan = invocation.getArgument(0);
            plan.setCreateTime(new Date(CREATE_MILLIS));
            plan.setUpdateTime(new Date(UPDATE_MILLIS));
            return 1;
        }).when(planMapper).insert(any(PrdSchedulePlan.class));

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));

        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void sameDecisionKeyReturnsExistingPlanBeforeAssemblyOrTaskWrites() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingPlan());

        PrdSchedulePlanVo vo = scheduleService.runSchedule(bo, List.of(11L, 12L));

        assertEquals(8001L, vo.getPlanId());
        assertEquals(4, vo.getVersion());
        verifyNoInteractions(scheduleInputAssembler, taskMapper, capacityCalendarMapper, capacityCalendarService, numberAllocator);
        verify(planMapper, never()).insert(any(PrdSchedulePlan.class));
    }

    @Test
    void sameDecisionKeyAcceptsGeneratedDefaultNameAndParsedStrategy() {
        PrdSchedulePlanBo request = decisionScheduleBo();
        request.setPlanName(null);
        request.setStrategy("edd");
        PrdSchedulePlan existing = matchingExistingPlan();
        existing.setPlanName("智能排产 " + existing.getPlanNo());
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        PrdSchedulePlanVo result = scheduleService.runSchedule(request, List.of(11L, 12L));

        assertEquals(existing.getPlanId(), result.getPlanId());
        verifyNoInteractions(scheduleInputAssembler, taskMapper, taskDayLoadMapper);
    }

    @Test
    void currentAliasWithWhitespaceCannotBypassIdempotentScheduleValidation() {
        PrdSchedulePlanBo request = decisionScheduleBo();
        request.setStrategy(" CURRENT ");
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingPlan());

        assertThrows(ServiceException.class,
            () -> scheduleService.runSchedule(request, List.of(11L, 12L)));
        verifyNoInteractions(scheduleInputAssembler, taskMapper, taskDayLoadMapper);
    }

    @Test
    void sameDecisionKeyWithDifferentScenarioOrWorkOrderScopeConflicts() {
        PrdSchedulePlanBo scenarioConflict = decisionScheduleBo();
        scenarioConflict.setDecisionScenarioCode("APS_CR");
        PrdSchedulePlanBo scopeConflict = decisionScheduleBo();
        scopeConflict.setDecisionWorkOrderIdsJson("[\"11\"]");
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingPlan());

        assertThrows(ServiceException.class,
            () -> scheduleService.runSchedule(scenarioConflict, List.of(11L, 12L)));
        assertThrows(ServiceException.class,
            () -> scheduleService.runSchedule(scopeConflict, List.of(11L)));

        verifyNoInteractions(scheduleInputAssembler, taskMapper);
    }

    @Test
    void duplicateDecisionInsertRereadsMatchingPlanWithoutTasks() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        when(planMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase(Locale.ROOT).contains("FOR UPDATE")
                ? matchingExistingPlan() : null;
        });
        when(scheduleInputAssembler.assemble(bo, List.of(11L, 12L))).thenReturn(scheduleInput());
        when(planMapper.insert(any(PrdSchedulePlan.class)))
            .thenThrow(new DuplicateKeyException("duplicate decision key"));

        PrdSchedulePlanVo vo = scheduleService.runSchedule(bo, List.of(11L, 12L));

        assertEquals(8001L, vo.getPlanId());
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> reads = wrapperCaptor();
        verify(planMapper, times(2)).selectOne(reads.capture());
        assertSqlContains(reads.getAllValues().get(1), "for update");
        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void compensatedScheduleKeyCannotBeRecreatedAfterLogicalDelete() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        when(planMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(scheduleInputAssembler.assemble(bo, List.of(11L, 12L))).thenReturn(scheduleInput());
        when(planMapper.insert(any(PrdSchedulePlan.class)))
            .thenThrow(new DuplicateKeyException("tenant and decision key still occupied"));
        when(planMapper.findCompensatedDecisionId("000000", IDEM_KEY)).thenReturn(8001L);

        assertTrue(scheduleService.deleteCompletedIfUnchanged(8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));
        try (MockedStatic<TenantHelper> tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            ServiceException error = assertThrows(ServiceException.class,
                () -> scheduleService.runSchedule(bo, List.of(11L, 12L)));
            assertTrue(error.getMessage().contains("已补偿"), error.getMessage());
        }
        verify(planMapper).findCompensatedDecisionId("000000", IDEM_KEY);
        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void unrelatedScheduleUniqueKeyCollisionIsNotMisclassifiedAsCompensated() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(scheduleInputAssembler.assemble(bo, List.of(11L, 12L))).thenReturn(scheduleInput());
        DuplicateKeyException duplicate = new DuplicateKeyException("duplicate plan number");
        when(planMapper.insert(any(PrdSchedulePlan.class))).thenThrow(duplicate);
        when(planMapper.findCompensatedDecisionId("000000", IDEM_KEY)).thenReturn(null);

        try (MockedStatic<TenantHelper> tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            assertSame(duplicate, assertThrows(DuplicateKeyException.class,
                () -> scheduleService.runSchedule(bo, List.of(11L, 12L))));
        }
        verify(planMapper).findCompensatedDecisionId("000000", IDEM_KEY);
        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void taskInsertMustAffectExactlyOneRow() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        stubPlanInsert(8003L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));
    }

    @Test
    void deleteCompletedIfUnchangedUsesVersionAndStructuredProvenance() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(taskMapper.delete(any(Wrapper.class))).thenReturn(1);

        boolean deleted = scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO);

        assertTrue(deleted);
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> plan = wrapperCaptor();
        InOrder order = inOrder(planMapper, taskMapper);
        order.verify(planMapper).delete(plan.capture());
        order.verify(taskMapper).delete(any(Wrapper.class));
        assertSqlContains(plan.getValue(), "plan_id", "status", "version", "decision_run_id",
            "decision_action_id", "decision_idempotency_key", "decision_scenario_code");
        Map<String, Object> params = parameters(plan.getValue());
        assertTrue(params.containsValue(4));
        assertTrue(params.containsValue(RUN_ID));
        assertTrue(params.containsValue(ACTION_ID));
        assertTrue(params.containsValue(IDEM_KEY));
        assertTrue(params.containsValue(SCENARIO));
        assertFalse(plan.getValue().getSqlSegment().toLowerCase(Locale.ROOT).contains("update_time"));
    }

    @Test
    void compensationLocksWorkOrderAndTasksBeforePlanDelete() {
        PrdScheduleTask task = persistedTask();
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(11L);
        wo.setTenantId("000000");
        lenient().when(workOrderMapper.selectOne(any(Wrapper.class))).thenReturn(wo);
        when(planMapper.delete(any(Wrapper.class))).thenReturn(1);

        assertTrue(scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));

        InOrder order = inOrder(taskMapper, workOrderMapper, planMapper);
        order.verify(taskMapper).selectList(any(Wrapper.class)); /** 首先普通读找工单 */
        order.verify(workOrderMapper).selectOne(any(Wrapper.class));
        ArgumentCaptor<Wrapper<PrdScheduleTask>> lock = wrapperCaptor();
        order.verify(taskMapper).selectList(lock.capture());
        assertSqlContains(lock.getValue(), "for update", "plan_id");
        order.verify(planMapper).delete(any(Wrapper.class));
    }

    @Test
    void compensationCleansOnlyItsDayLoadsInSameTransaction() throws NoSuchMethodException {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(1);
        assertTrue(scheduleService.deleteCompletedIfUnchanged(8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));
        ArgumentCaptor<Wrapper<PrdScheduleTaskDayLoad>> deleted = wrapperCaptor();
        verify(taskDayLoadMapper).delete(deleted.capture());
        assertTrue(parameters(deleted.getValue()).containsValue(8001L));
        Transactional tx = PrdScheduleServiceImpl.class.getMethod("deleteCompletedIfUnchanged",
            Long.class, String.class, Long.class, Long.class, String.class, String.class)
            .getAnnotation(Transactional.class);
        assertTrue(tx != null && List.of(tx.rollbackFor()).contains(Exception.class));
    }

    @Test
    void compensationRefusesCandidateWithExistingBookings() {
        when(bookingMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(ServiceException.class, () -> scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));
        verify(planMapper, never()).delete(any(Wrapper.class));
        verify(taskDayLoadMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void compensationCasMissDoesNotDeleteTasks() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertFalse(scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));

        verify(taskMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void invalidVersionMarkerFailsClosedBeforeScheduleDelete() {
        assertFalse(scheduleService.deleteCompletedIfUnchanged(
            8001L, "U:" + UPDATE_MILLIS, RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));
        assertFalse(scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:-1", RUN_ID, ACTION_ID, IDEM_KEY, SCENARIO));

        verifyNoInteractions(planMapper, taskMapper);
    }

    @Test
    void deletingPublishedPlanWithoutReservationLedgerIsRejected() {
        PrdSchedulePlan published = matchingExistingPlan();
        published.setStatus("PUBLISHED");
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(published);

        assertThrows(ServiceException.class, () -> scheduleService.deleteByIds(new Long[]{8001L}));

        verify(taskMapper, never()).delete(any(Wrapper.class));
        verify(planMapper, never()).deleteByIds(any());
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> lock = wrapperCaptor();
        verify(planMapper).selectOne(lock.capture());
        assertSqlContains(lock.getValue(), "for update");
    }

    @Test
    void legacyCompletedVersionZeroCannotBePublished() {
        PrdSchedulePlan legacy = matchingExistingPlan();
        legacy.setCapacityCalcVersion(0);
        when(planMapper.selectById(8001L)).thenReturn(legacy);
        Mockito.lenient().when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        Mockito.lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ServiceException failure = assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        assertTrue(failure.getMessage().contains("版本"), failure.getMessage());
        verify(planMapper, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }

    @Test
    void versionTwoPlanWithoutTaskDayLoadsCannotBePublished() {
        PrdSchedulePlan current = matchingExistingPlan();
        current.setCapacityCalcVersion(2);
        when(planMapper.selectById(8001L)).thenReturn(current);
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("0.07", "0.10");
        task.setTaskId(9001L);
        task.setEndTime(java.sql.Date.valueOf(START));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubWorkOrderForPublish();

        ServiceException failure = assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        assertTrue(failure.getMessage().contains("逐日"), failure.getMessage());
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }

    @Test
    void completedWorkOrderIsRejectedBeforeClaimOrCapacityBooking() {
        assertTerminalWorkOrderCannotPublish("COMPLETED");
    }

    @Test
    void closedWorkOrderIsRejectedBeforeClaimOrCapacityBooking() {
        assertTerminalWorkOrderCannotPublish("CLOSED");
    }

    private void assertTerminalWorkOrderCannotPublish(String status) {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(internalTask("0.10", "0.00")));
        PrdWorkOrder order = new PrdWorkOrder();
        order.setWoId(11L);
        order.setTenantId("000000");
        order.setWoStatus(status);
        when(workOrderMapper.selectById(11L)).thenReturn(order);

        ServiceException failure = assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));
        assertTrue(failure.getMessage().contains("工单"), failure.getMessage());
        verify(planMapper, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService, bookingMapper);
        verify(workOrderMapper, never()).updateById(any(PrdWorkOrder.class));
    }

    @Test
    void publishCasMissDoesNotBookCapacityOrUpdateWorkOrders() {
        PrdSchedulePlan current = matchingExistingPlan();
        when(planMapper.selectById(8001L)).thenReturn(current);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persistedTask()));
        stubWorkOrderForPublish();
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verify(workOrderMapper, never()).updateById(any(PrdWorkOrder.class));
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService);
    }

    @Test
    void publishLocksWorkOrderBeforeClaimingCompletedVersion() {
        PrdSchedulePlan current = matchingExistingPlan();
        when(planMapper.selectById(8001L)).thenReturn(current);
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = persistedTask();
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        PrdWorkOrder workOrder = new PrdWorkOrder();
        workOrder.setWoId(11L);
        workOrder.setTenantId("000000");
        workOrder.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);

        assertTrue(scheduleService.publish(8001L));

        ArgumentCaptor<Wrapper<PrdSchedulePlan>> cas = wrapperCaptor();
        InOrder order = inOrder(planMapper, taskMapper, workOrderMapper);
        order.verify(taskMapper).selectList(any(Wrapper.class));
        order.verify(workOrderMapper).selectOne(any(Wrapper.class));
        order.verify(planMapper).update(isNull(), cas.capture());
        order.verify(workOrderMapper).updateById(any(PrdWorkOrder.class));
        assertSqlContains(cas.getValue(), "plan_id", "status", "version");
        Map<String, Object> params = parameters(cas.getValue());
        assertTrue(params.containsValue("COMPLETED"));
        assertTrue(params.containsValue(4));
        assertTrue(params.containsValue("PUBLISHED"));
        assertTrue(params.containsValue(5));
        assertSqlSetContains(cas.getValue(), "status", "version", "update_time");
        verifyNoInteractions(capacityCalendarMapper, capacityCalendarService); /** OUTSOURCE 不占用 */
    }

    @Test
    void publishBooksProcessingAndSetupAcrossDaysAndShifts() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = persistedTask();
        task.setResourceType("WORKSTATION");
        task.setResourceId(401L);
        task.setProcessHours(new BigDecimal("2.00"));
        task.setSetupHours(new BigDecimal("1.00"));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "1.00", START.plusDays(1).toString(), "2.00");
        stubSuccessfulOccupancy(List.of(
            calendar(3L, START.plusDays(1), 1L, "2.00"),
            calendar(2L, START, 2L, "0.50"),
            calendar(1L, START, 1L, "0.50")));
        PrdWorkOrder order = new PrdWorkOrder();
        order.setWoId(11L);
        order.setTenantId("000000");
        order.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(order);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);

        assertTrue(scheduleService.publish(8001L));

        verify(capacityCalendarService).occupyCapacity(1L, new BigDecimal("0.50"));
        verify(capacityCalendarService).occupyCapacity(2L, new BigDecimal("0.50"));
        verify(capacityCalendarService).occupyCapacity(3L, new BigDecimal("2.00"));
        InOrder orderOfWrites = inOrder(planMapper, capacityCalendarMapper, capacityCalendarService, workOrderMapper);
        orderOfWrites.verify(planMapper).update(isNull(), any(Wrapper.class));
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> lock = wrapperCaptor();
        orderOfWrites.verify(capacityCalendarMapper, times(2)).selectList(any(Wrapper.class));
        orderOfWrites.verify(capacityCalendarMapper, times(3)).selectOne(lock.capture());
        assertSqlContains(lock.getValue(), "for update", "calendar_id");
        assertEquals(List.of(1L, 2L, 3L), lock.getAllValues().stream()
            .map(query -> (Long) parameters(query).values().iterator().next()).toList());
        orderOfWrites.verify(capacityCalendarService).occupyCapacity(1L, new BigDecimal("0.50"));
        orderOfWrites.verify(capacityCalendarService).occupyCapacity(2L, new BigDecimal("0.50"));
        orderOfWrites.verify(capacityCalendarService).occupyCapacity(3L, new BigDecimal("2.00"));
        orderOfWrites.verify(workOrderMapper).updateById(any(PrdWorkOrder.class));
    }

    @Test
    void publishAggregatesMultipleTasksOnSameShiftWithoutDoubleCounting() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask first = internalTask("0.25", "0.10");
        PrdScheduleTask second = internalTask("0.20", "0.05");
        first.setEndTime(java.sql.Date.valueOf(START));
        second.setEndTime(java.sql.Date.valueOf(START));
        second.setWoId(12L);
        second.setTaskId(9002L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(second, first));
        List<PrdScheduleTaskDayLoad> loads = List.of(dayLoad(first, START, "0.35"),
            dayLoad(second, START, "0.25"));
        when(taskDayLoadMapper.selectList(any(Wrapper.class))).thenReturn(loads);
        stubSuccessfulOccupancy(List.of(calendar(1L, START, 1L, "0.60")));
        PrdWorkOrder firstOrder = new PrdWorkOrder();
        firstOrder.setWoId(11L);
        firstOrder.setTenantId("000000");
        firstOrder.setWoStatus("RELEASED");
        PrdWorkOrder secondOrder = new PrdWorkOrder();
        secondOrder.setWoId(12L);
        secondOrder.setTenantId("000000");
        secondOrder.setWoStatus("RELEASED");
        when(workOrderMapper.selectOne(any(Wrapper.class))).thenReturn(firstOrder, secondOrder);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);

        assertTrue(scheduleService.publish(8001L));

        verify(capacityCalendarService, times(1)).occupyCapacity(1L, new BigDecimal("0.60"));
        verify(capacityCalendarService, never()).occupyCapacity(eq(2L), any());
    }

    @Test
    void publishRejectsMissingCalendarWithoutBookingOrWritingOrders() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("0.07", "0.10");
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "0.17");
        when(capacityCalendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        stubWorkOrderForPublish();
        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verifyNoInteractions(capacityCalendarService);
        verify(workOrderMapper, never()).updateById(any(PrdWorkOrder.class));
    }

    @Test
    void publishRejectsInsufficientRemainingAcrossDaysWithoutPartialBooking() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("2.00", "1.00");
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "1.00", START.plusDays(1).toString(), "2.00");
        when(capacityCalendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            calendar(1L, START, 1L, "0.50"), calendar(2L, START.plusDays(1), 1L, "1.00")));

        stubWorkOrderForPublish();
        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verifyNoInteractions(capacityCalendarService);
        verify(workOrderMapper, never()).updateById(any(PrdWorkOrder.class));
    }

    @Test
    void publishRejectsWhenChangedCapacityMovesWorkOffScheduledStartDay() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("2.00", "1.00");
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "1.00", START.plusDays(1).toString(), "2.00");
        when(capacityCalendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            calendar(1L, START, 1L, "0.00"),
            calendar(2L, START.plusDays(1), 1L, "3.00")));

        stubWorkOrderForPublish();
        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verifyNoInteractions(capacityCalendarService);
    }

    @Test
    void publishRejectsIfCalendarServiceDoesNotPersistPlannedHours() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("0.10", "0.07");
        task.setEndTime(java.sql.Date.valueOf(START));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "0.17");
        PrdCapacityCalendar first = calendar(1L, START, 1L, "0.17");
        when(capacityCalendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first));
        when(capacityCalendarMapper.selectOne(any(Wrapper.class))).thenReturn(first);
        when(capacityCalendarMapper.selectById(1L)).thenReturn(first);

        stubWorkOrderForPublish();
        ServiceException failure = assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));
        assertTrue(failure.getMessage().contains("占用未成功持久化"));
    }

    @Test
    void publishDoesNotOccupyTwiceWhenCalledAgain() {
        PrdSchedulePlan current = matchingExistingPlan();
        PrdSchedulePlan published = matchingExistingPlan();
        published.setStatus("PUBLISHED");
        when(planMapper.selectById(8001L)).thenReturn(current, published);
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("0.07", "0.10");
        task.setEndTime(java.sql.Date.valueOf(START));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "0.17");
        stubSuccessfulOccupancy(List.of(calendar(1L, START, 1L, "0.17")));
        PrdWorkOrder order = new PrdWorkOrder();
        order.setWoId(11L);
        order.setTenantId("000000");
        order.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(order);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);

        assertTrue(scheduleService.publish(8001L));
        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verify(capacityCalendarService, times(1)).occupyCapacity(1L, new BigDecimal("0.17"));
        verify(taskMapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void failedWorkOrderWriteAfterReservationThrowsInsidePublishTransaction() throws NoSuchMethodException {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = internalTask("0.07", "0.10");
        task.setEndTime(java.sql.Date.valueOf(START));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        stubDayLoads(task, START.toString(), "0.17");
        stubSuccessfulOccupancy(List.of(calendar(1L, START, 1L, "0.17")));
        PrdWorkOrder order = new PrdWorkOrder();
        order.setWoId(11L);
        order.setTenantId("000000");
        order.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(order);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verify(capacityCalendarService).occupyCapacity(1L, new BigDecimal("0.17"));
        Transactional boundary = PrdScheduleServiceImpl.class.getMethod("publish", Long.class)
            .getAnnotation(Transactional.class);
        assertTrue(boundary != null && List.of(boundary.rollbackFor()).contains(Exception.class));
    }

    private void stubSuccessfulOccupancy(List<PrdCapacityCalendar> rows) {
        Map<Long, PrdCapacityCalendar> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.getCalendarId(), row));
        when(capacityCalendarMapper.selectList(any(Wrapper.class)))
                .thenAnswer(
                        invocation -> {
                            Map<String, Object> params = parameters(invocation.getArgument(0));
                            LocalDate date =
                                    params.values().stream()
                                            .filter(Date.class::isInstance)
                                            .map(Date.class::cast)
                                            .map(
                                                    value ->
                                                            value instanceof java.sql.Date sqlDate
                                                                    ? sqlDate.toLocalDate()
                                                                    : value.toInstant()
                                                                            .atZone(
                                                                                    ZoneId.of(
                                                                                            "Asia/Shanghai"))
                                                                            .toLocalDate())
                                            .findFirst()
                                            .orElseThrow();
                            return rows.stream()
                                    .filter(
                                            row ->
                                                    params.containsValue(row.getObjectId())
                                                            && date.equals(
                                                                    ((java.sql.Date)
                                                                                    row
                                                                                            .getCalendarDate())
                                                                            .toLocalDate()))
                                    .toList();
                        });
        lenient()
                .when(capacityCalendarMapper.selectOne(any(Wrapper.class)))
                .thenAnswer(
                        invocation -> {
                            Wrapper<?> query = invocation.getArgument(0);
                            return parameters(query).values().stream()
                                    .filter(Long.class::isInstance)
                                    .map(Long.class::cast)
                                    .map(byId::get)
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(null);
                        });
        when(capacityCalendarMapper.selectById(any())).thenAnswer(invocation -> byId.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            PrdCapacityCalendar row = byId.get(invocation.getArgument(0));
            BigDecimal hours = invocation.getArgument(1);
            row.setPlannedHours(row.getPlannedHours().add(hours));
            row.setRemainingHours(row.getRemainingHours().subtract(hours));
            return null;
        }).when(capacityCalendarService).occupyCapacity(any(), any());
    }

    private void stubWorkOrderForPublish() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(11L);
        wo.setTenantId("000000");
        wo.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(wo);
    }

    private PrdScheduleTaskDayLoad dayLoad(PrdScheduleTask task, LocalDate day, String hours) {
        PrdScheduleTaskDayLoad load = new PrdScheduleTaskDayLoad();
        load.setLoadId(40_000L + task.getTaskId() * 10 + day.toEpochDay());
        load.setTenantId("000000");
        load.setPlanId(task.getPlanId());
        load.setTaskId(task.getTaskId());
        load.setResourceType(task.getResourceType());
        load.setResourceId(task.getResourceId());
        load.setLoadDate(java.sql.Date.valueOf(day));
        load.setPlannedHours(new BigDecimal(hours));
        return load;
    }

    private void stubDayLoads(PrdScheduleTask task, String... dayAndHours) {
        List<PrdScheduleTaskDayLoad> loads = new ArrayList<>();
        for (int index = 0; index < dayAndHours.length; index += 2) {
            PrdScheduleTaskDayLoad load = new PrdScheduleTaskDayLoad();
            load.setLoadId(40_000L + task.getTaskId() * 10 + index);
            load.setTenantId("000000");
            load.setPlanId(task.getPlanId());
            load.setTaskId(task.getTaskId());
            load.setResourceType(task.getResourceType());
            load.setResourceId(task.getResourceId());
            load.setLoadDate(java.sql.Date.valueOf(LocalDate.parse(dayAndHours[index])));
            load.setPlannedHours(new BigDecimal(dayAndHours[index + 1]));
            loads.add(load);
        }
        when(taskDayLoadMapper.selectList(any(Wrapper.class))).thenReturn(loads);
    }

    private PrdScheduleTask internalTask(String process, String setup) {
        PrdScheduleTask task = persistedTask();
        task.setResourceType("WORKSTATION");
        task.setResourceId(401L);
        task.setProcessHours(new BigDecimal(process));
        task.setSetupHours(new BigDecimal(setup));
        return task;
    }

    private PrdCapacityCalendar calendar(Long id, LocalDate day, Long shift, String remaining) {
        PrdCapacityCalendar row = new PrdCapacityCalendar();
        row.setCalendarId(id);
        row.setTenantId("000000");
        row.setCalendarDate(java.sql.Date.valueOf(day));
        row.setObjectType("WORKSTATION");
        row.setObjectId(401L);
        row.setShiftId(shift);
        row.setStatus("ACTIVE");
        row.setRemainingHours(new BigDecimal(remaining));
        row.setEffectiveHours(new BigDecimal(remaining));
        row.setPlannedHours(BigDecimal.ZERO);
        return row;
    }

    @Test
    void publishThrowsWhenWorkOrderUpdateMissesAfterPlanClaim() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persistedTask()));
        PrdWorkOrder workOrder = new PrdWorkOrder();
        workOrder.setWoId(11L);
        workOrder.setTenantId("000000");
        workOrder.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));
    }

    private PrdSchedulePlanBo scheduleBo() {
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setPlanName("AI交付风险方案-APS_EDD");
        bo.setWorkshopId(21L);
        bo.setWorkshopName("装配车间");
        bo.setStartDate(java.sql.Date.valueOf(START));
        bo.setHorizonDays(14);
        bo.setStrategy("EDD");
        bo.setRemark("display only");
        return bo;
    }

    private PrdSchedulePlanBo decisionScheduleBo() {
        PrdSchedulePlanBo bo = scheduleBo();
        bo.setDecisionRunId(RUN_ID);
        bo.setDecisionActionId(ACTION_ID);
        bo.setDecisionIdempotencyKey(IDEM_KEY);
        bo.setDecisionScenarioCode(SCENARIO);
        bo.setDecisionWorkOrderIdsJson(WORK_ORDER_IDS_JSON);
        bo.setVersion(0);
        return bo;
    }

    private PrdSchedulePlan matchingExistingPlan() {
        PrdSchedulePlan plan = new PrdSchedulePlan();
        plan.setPlanId(8001L);
        plan.setPlanNo("APS202609240001");
        plan.setPlanName("AI交付风险方案-APS_EDD");
        plan.setWorkshopId(21L);
        plan.setWorkshopName("装配车间");
        plan.setStartDate(java.sql.Date.valueOf(START));
        plan.setHorizonDays(14);
        plan.setStrategy("EDD");
        plan.setStatus("COMPLETED");
        plan.setTenantId("000000");
        plan.setCapacityCalcVersion(2);
        plan.setDecisionRunId(RUN_ID);
        plan.setDecisionActionId(ACTION_ID);
        plan.setDecisionIdempotencyKey(IDEM_KEY);
        plan.setDecisionScenarioCode(SCENARIO);
        plan.setDecisionWorkOrderIdsJson(WORK_ORDER_IDS_JSON);
        plan.setVersion(4);
        plan.setCreateTime(new Date(CREATE_MILLIS));
        plan.setUpdateTime(new Date(UPDATE_MILLIS));
        return plan;
    }

    private ScheduleInput scheduleInput() {
        WoInfo wo = new WoInfo();
        wo.setWoId(11L);
        wo.setWoNo("WO-11");
        wo.setMaterialId(101L);
        wo.setMaterialCode("M-101");
        wo.setMaterialName("物料101");
        wo.setRemainingQty(BigDecimal.ONE);
        wo.setDueDate(START.plusDays(5));
        wo.setPriority(1L);
        wo.setWorkshopId(21L);
        wo.setWorkshopName("装配车间");

        StepInfo step = new StepInfo();
        step.setStepId(301L);
        step.setSeqNum(10);
        step.setProcessCode("P-10");
        step.setProcessName("装配");
        step.setWorkstationId(401L);
        step.setWorkstationCode("WS-401");
        step.setWorkstationName("装配工位");
        step.setStandardHours(BigDecimal.ONE);
        step.setSetupHours(BigDecimal.ZERO);
        step.setIsOutsource(0);

        ScheduleInput input = new ScheduleInput();
        input.setWorkOrders(List.of(wo));
        input.setRoutingProvider(materialId -> List.of(step));
        input.setCapacityByDate(Map.of());
        input.setHolidays(Set.of());
        input.setStartDate(START);
        input.setHorizonDays(14);
        input.setStrategy(Strategy.EDD);
        return input;
    }

    private PrdScheduleTask persistedTask() {
        PrdScheduleTask task = new PrdScheduleTask();
        task.setTaskId(9001L);
        task.setPlanId(8001L);
        task.setTenantId("000000");
        task.setWoId(11L);
        task.setSeqNum(10);
        task.setResourceType("OUTSOURCE");
        task.setStartTime(java.sql.Date.valueOf(START));
        task.setEndTime(java.sql.Date.valueOf(START.plusDays(1)));
        return task;
    }

    private void stubPlanInsert(Long planId) {
        doAnswer(invocation -> {
            PrdSchedulePlan plan = invocation.getArgument(0);
            plan.setPlanId(planId);
            plan.setCreateTime(new Date(CREATE_MILLIS));
            plan.setUpdateTime(new Date(UPDATE_MILLIS));
            return 1;
        }).when(planMapper).insert(any(PrdSchedulePlan.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private Map<String, Object> parameters(Wrapper<?> wrapper) {
        wrapper.getSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private void assertSqlSetContains(Wrapper<?> wrapper, String... fragments) {
        String sql = ((LambdaUpdateWrapper<?>) wrapper).getSqlSet()
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SET缺少片段 " + fragment + ": " + sql);
        }
    }

    private void assertSqlContains(Wrapper<?> wrapper, String... fragments) {
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SQL缺少片段 " + fragment + ": " + sql);
        }
    }
}
