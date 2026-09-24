package com.nexus.erp.prd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 排产方案 scoped 持久化与条件补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdScheduleServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 24);
    private static final long CREATE_MILLIS = 1_795_622_400_000L;
    private static final long UPDATE_MILLIS = 1_795_622_460_000L;

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
    void scopedRunUsesOnlyScopedAssemblerAndPersistsCompletedPlanWithRemarkAndTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        List<Long> ids = List.of(11L, 12L);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        stubPlanInsert(8001L, 1);
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            PrdSchedulePlanVo vo = scheduleService.runSchedule(bo, ids);

            assertEquals(8001L, vo.getPlanId());
            assertEquals("APS202609240001", vo.getPlanNo());
            assertEquals("COMPLETED", vo.getStatus());
            assertEquals("runId=99;scenario=APS_EDD;idempotency=idem-99", vo.getRemark());
            assertEquals(new Date(CREATE_MILLIS), vo.getCreateTime());
            assertEquals(new Date(UPDATE_MILLIS), vo.getUpdateTime());
            verify(scheduleInputAssembler).assemble(bo, ids);
            verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
            verifyNoInteractions(workOrderMapper);

            ArgumentCaptor<PrdSchedulePlan> plan = ArgumentCaptor.forClass(PrdSchedulePlan.class);
            verify(planMapper).insert(plan.capture());
            assertEquals("COMPLETED", plan.getValue().getStatus());
            assertEquals(bo.getRemark(), plan.getValue().getRemark());

            ArgumentCaptor<PrdScheduleTask> task = ArgumentCaptor.forClass(PrdScheduleTask.class);
            verify(taskMapper).insert(task.capture());
            assertEquals(8001L, task.getValue().getPlanId());
            assertEquals(11L, task.getValue().getWoId());
            assertEquals(Date.from(START.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),
                task.getValue().getStartTime());
            assertEquals(Date.from(START.atTime(23, 59, 59).atZone(ZoneId.of("Asia/Shanghai")).toInstant()),
                task.getValue().getEndTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void originalRunUsesOnlyFullAssemblerOverload() {
        PrdSchedulePlanBo bo = scheduleBo();
        when(scheduleInputAssembler.assemble(bo)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        stubPlanInsert(8002L, 1);

        PrdSchedulePlanVo vo = scheduleService.runSchedule(bo);

        assertEquals(8002L, vo.getPlanId());
        verify(scheduleInputAssembler).assemble(bo);
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), any());
        verify(taskMapper).insert(any(PrdScheduleTask.class));
    }

    @Test
    void failedPlanInsertDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        List<Long> ids = List.of(11L);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(planMapper.insert(any(PrdSchedulePlan.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo, ids));

        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void missingGeneratedPlanIdDoesNotWriteTasks() {
        PrdSchedulePlanBo bo = scheduleBo();
        List<Long> ids = List.of(11L);
        when(scheduleInputAssembler.assemble(bo, ids)).thenReturn(scheduleInput());
        when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(planMapper.insert(any(PrdSchedulePlan.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> scheduleService.runSchedule(bo, ids));

        verify(taskMapper, never()).insert(any(PrdScheduleTask.class));
    }

    @Test
    void deleteCompletedIfUnchangedDeletesPlanBeforeTasks() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(taskMapper.delete(any(Wrapper.class))).thenReturn(1);

        boolean deleted = scheduleService.deleteCompletedIfUnchanged(8001L, "U:" + UPDATE_MILLIS);

        assertTrue(deleted);
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> plan = wrapperCaptor();
        ArgumentCaptor<Wrapper<PrdScheduleTask>> tasks = wrapperCaptor();
        InOrder order = inOrder(planMapper, taskMapper);
        order.verify(planMapper).delete(plan.capture());
        order.verify(taskMapper).delete(tasks.capture());
        assertSqlContains(plan.getValue(), "plan_id", "status", "update_time");
        assertTrue(parameters(plan.getValue()).containsValue(8001L));
        assertTrue(parameters(plan.getValue()).containsValue("COMPLETED"));
        assertTrue(parameters(plan.getValue()).containsValue(new Date(UPDATE_MILLIS)));
        assertSqlContains(tasks.getValue(), "plan_id");
        assertTrue(parameters(tasks.getValue()).containsValue(8001L));
    }

    @Test
    void publishedPlanCannotBeConditionallyDeletedAndTasksRemain() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertFalse(scheduleService.deleteCompletedIfUnchanged(8001L, "U:" + UPDATE_MILLIS));

        verify(planMapper).delete(any(Wrapper.class));
        verify(taskMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void changedMarkerCannotDeleteTasks() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertFalse(scheduleService.deleteCompletedIfUnchanged(8001L, "U:" + (UPDATE_MILLIS + 1)));

        verify(taskMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void createMarkerRequiresUpdateTimeToRemainNull() {
        when(planMapper.delete(any(Wrapper.class))).thenReturn(0);
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> plan = wrapperCaptor();

        assertFalse(scheduleService.deleteCompletedIfUnchanged(8001L, "C:" + CREATE_MILLIS));

        verify(planMapper).delete(plan.capture());
        verify(taskMapper, never()).delete(any(Wrapper.class));
        assertSqlContains(plan.getValue(), "create_time", "update_time is null");
        assertTrue(parameters(plan.getValue()).containsValue(new Date(CREATE_MILLIS)));
    }

    @Test
    void invalidCompensationInputFailsClosedBeforeAnyDelete() {
        assertFalse(scheduleService.deleteCompletedIfUnchanged(null, "U:" + UPDATE_MILLIS));
        assertFalse(scheduleService.deleteCompletedIfUnchanged(0L, "U:" + UPDATE_MILLIS));
        assertFalse(scheduleService.deleteCompletedIfUnchanged(8001L, null));
        assertFalse(scheduleService.deleteCompletedIfUnchanged(8001L, "C:not-a-number"));

        verifyNoInteractions(planMapper, taskMapper);
    }

    private PrdSchedulePlanBo scheduleBo() {
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setPlanName("AI交付风险方案-APS_EDD");
        bo.setWorkshopId(21L);
        bo.setWorkshopName("装配车间");
        bo.setStartDate(java.sql.Date.valueOf(START));
        bo.setHorizonDays(14);
        bo.setStrategy("EDD");
        bo.setRemark("runId=99;scenario=APS_EDD;idempotency=idem-99");
        return bo;
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

    private void stubPlanInsert(Long planId, int rows) {
        doAnswer(invocation -> {
            PrdSchedulePlan plan = invocation.getArgument(0);
            plan.setPlanId(planId);
            plan.setCreateTime(new Date(CREATE_MILLIS));
            plan.setUpdateTime(new Date(UPDATE_MILLIS));
            return rows;
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

    private void assertSqlContains(Wrapper<?> wrapper, String... fragments) {
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SQL缺少片段 " + fragment + ": " + sql);
        }
    }
}
