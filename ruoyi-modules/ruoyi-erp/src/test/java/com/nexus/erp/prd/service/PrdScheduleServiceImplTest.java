package com.nexus.erp.prd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.prd.service.impl.PrdScheduleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private PrdScheduleInputAssembler scheduleInputAssembler;

    @InjectMocks
    private PrdScheduleServiceImpl scheduleService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleTask.class);
    }

    @Test
    void scopedRunPersistsStructuredScopeVersionAndTasks() {
        PrdSchedulePlanBo bo = decisionScheduleBo();
        List<Long> ids = List.of(11L, 12L);
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        stubPlanInsert(8001L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            PrdSchedulePlanVo vo = scheduleService.runSchedule(bo, ids);

            assertEquals(8001L, vo.getPlanId());
            assertEquals("COMPLETED", vo.getStatus());
            assertEquals(RUN_ID, vo.getDecisionRunId());
            assertEquals(ACTION_ID, vo.getDecisionActionId());
            assertEquals(IDEM_KEY, vo.getDecisionIdempotencyKey());
            assertEquals(SCENARIO, vo.getDecisionScenarioCode());
            assertEquals(WORK_ORDER_IDS_JSON, vo.getDecisionWorkOrderIdsJson());
            assertEquals(0, vo.getVersion());
            ArgumentCaptor<PrdScheduleTask> task = ArgumentCaptor.forClass(PrdScheduleTask.class);
            verify(taskMapper).insert(task.capture());
            assertEquals(Date.from(START.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),
                task.getValue().getStartTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void originalRunUsesFullAssemblerForOrdinaryUiRows() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        stubPlanInsert(8002L);
        when(taskMapper.insert(any(PrdScheduleTask.class))).thenReturn(1);

        PrdSchedulePlanVo vo = scheduleService.runSchedule(bo);

        assertEquals(8002L, vo.getPlanId());
        verify(scheduleInputAssembler).assemble(bo);
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), any());
    }

    @Test
    void planInsertZeroDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(planMapper.insert(any(PrdSchedulePlan.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo));

        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void missingGeneratedPlanIdDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
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
        verifyNoInteractions(scheduleInputAssembler, taskMapper);
        verify(planMapper, never()).insert(any(PrdSchedulePlan.class));
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
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
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
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
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
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
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
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
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
    void publishCasMissDoesNotReadTasksOrUpdateWorkOrders() {
        PrdSchedulePlan current = matchingExistingPlan();
        when(planMapper.selectById(8001L)).thenReturn(current);
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.publish(8001L));

        verifyNoInteractions(taskMapper, workOrderMapper);
    }

    @Test
    void publishClaimsCompletedVersionBeforeTasksAndWorkOrders() {
        PrdSchedulePlan current = matchingExistingPlan();
        when(planMapper.selectById(8001L)).thenReturn(current);
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        PrdScheduleTask task = persistedTask();
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        PrdWorkOrder workOrder = new PrdWorkOrder();
        workOrder.setWoId(11L);
        workOrder.setWoStatus("RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);

        assertTrue(scheduleService.publish(8001L));

        ArgumentCaptor<Wrapper<PrdSchedulePlan>> cas = wrapperCaptor();
        InOrder order = inOrder(planMapper, taskMapper, workOrderMapper);
        order.verify(planMapper).update(isNull(), cas.capture());
        order.verify(taskMapper).selectList(any(Wrapper.class));
        order.verify(workOrderMapper).selectById(11L);
        order.verify(workOrderMapper).updateById(any(PrdWorkOrder.class));
        assertSqlContains(cas.getValue(), "plan_id", "status", "version");
        Map<String, Object> params = parameters(cas.getValue());
        assertTrue(params.containsValue("COMPLETED"));
        assertTrue(params.containsValue(4));
        assertTrue(params.containsValue("PUBLISHED"));
        assertTrue(params.containsValue(5));
        assertSqlSetContains(cas.getValue(), "status", "version", "update_time");
    }

    @Test
    void publishThrowsWhenWorkOrderUpdateMissesAfterPlanClaim() {
        when(planMapper.selectById(8001L)).thenReturn(matchingExistingPlan());
        when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persistedTask()));
        PrdWorkOrder workOrder = new PrdWorkOrder();
        workOrder.setWoId(11L);
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
        task.setPlanId(8001L);
        task.setWoId(11L);
        task.setSeqNum(10);
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
