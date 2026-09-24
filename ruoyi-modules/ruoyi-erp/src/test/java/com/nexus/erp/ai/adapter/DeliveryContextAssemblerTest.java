package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.support.DataSourceClassResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.PuVendorEvaluation;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuVendorEvaluationMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
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
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ERP 工业决策上下文装配测试：作用域必须精确，展示快照与可重算指纹必须稳定且 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("dev")
class DeliveryContextAssemblerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Mock
    private SoSalesOrderHeaderMapper salesOrderHeaderMapper;
    @Mock
    private SoSalesOrderLineMapper salesOrderLineMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private PrdSchedulePlanMapper schedulePlanMapper;
    @Mock
    private PrdScheduleTaskMapper scheduleTaskMapper;
    @Mock
    private PrdMaterialRequirementMapper requirementMapper;
    @Mock
    private PrdProductionPlanMapper productionPlanMapper;
    @Mock
    private PrdMaterialRequirementDetailMapper requirementDetailMapper;
    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private BdRoutingMapper routingMapper;
    @Mock
    private BdRoutingStepMapper routingStepMapper;
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
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private PuVendorEvaluationMapper vendorEvaluationMapper;
    @Mock
    private PrdScheduleInputAssembler scheduleInputAssembler;

    @Spy
    @InjectMocks
    private DeliveryContextAssembler assembler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(
            SoSalesOrderHeader.class, SoSalesOrderLine.class, PrdWorkOrder.class, PrdProductionPlan.class,
            PrdSchedulePlan.class, PrdScheduleTask.class, PrdMaterialRequirement.class,
            PrdMaterialRequirementDetail.class, WmMaterialStock.class, BdMaterial.class,
            PuPurchaseOrder.class, PuPurchaseOrderLine.class, PuVendorEvaluation.class,
            BdRouting.class, BdRoutingStep.class, PrdCapacityCalendar.class, SchHoliday.class,
            SchEmployeeSchedule.class, SchShift.class, SchTeam.class, SchLeaveApply.class,
            BdWorkstation.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void snapshotAtUsesAsiaShanghaiWhenJvmDefaultIsUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            SoSalesOrderHeader order = salesOrder(100L, "SO-100");
            when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
            when(salesOrderLineMapper.selectList(any())).thenReturn(List.of());
            when(workOrderMapper.selectList(any())).thenReturn(List.of());
            stubEmptyRelatedFacts();
            LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

            DeliveryContextSnapshot snapshot = assembler.assemble(
                new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
                actor("ai:decision:run", "so:orderheader:query"));

            LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            assertFalse(snapshot.snapshotAt().isBefore(before), snapshot.snapshotAt().toString());
            assertFalse(snapshot.snapshotAt().isAfter(after), snapshot.snapshotAt().toString());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void freshnessHashIsStableAcrossJvmDefaultTimeZones() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            RichFixture fixture = stubRichSalesOrderFixture(
                new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
                new AtomicReference<>(new BigDecimal("3")));
            String utcHash = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            String losAngelesHash = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

            assertEquals(utcHash, losAngelesHash);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void freshnessDecimalCanonicalIgnoresScaleAndNormalizesZero() {
        AtomicReference<BigDecimal> stockQty = new AtomicReference<>(new BigDecimal("20.00"));
        AtomicReference<BigDecimal> reservedQty = new AtomicReference<>(new BigDecimal("0.00"));
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false), stockQty, reservedQty);

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        stockQty.set(new BigDecimal("20.0"));
        reservedQty.set(BigDecimal.ZERO);
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
    }

    @Test
    void freshnessIgnoresRequirementsOutsideFixedNinetyDayWindowAndQueriesDates() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        PrdMaterialRequirement requirement = requirement(801L, 701L);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        AtomicReference<BigDecimal> outsideRequired = new AtomicReference<>(new BigDecimal("10"));
        when(requirementDetailMapper.selectList(any())).thenAnswer(invocation -> {
            PrdMaterialRequirementDetail inside =
                requirementDetail(901L, 801L, 501L, "10", "2", TODAY.plusDays(10));
            PrdMaterialRequirementDetail outside =
                requirementDetail(902L, 801L, 999L, outsideRequired.get().toPlainString(), "0",
                    TODAY.plusDays(100));
            return List.of(outside, inside);
        });

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        outsideRequired.set(new BigDecimal("99"));
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdMaterialRequirementDetail>> queries =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(requirementDetailMapper, atLeastOnce()).selectList(queries.capture());
        for (Wrapper<PrdMaterialRequirementDetail> query : queries.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("required_date"), query.getSqlSegment());
            Collection<?> values = queryValues(query);
            assertTrue(values.contains(java.sql.Date.valueOf(TODAY)), values.toString());
            assertTrue(values.contains(java.sql.Date.valueOf(TODAY.plusDays(89))), values.toString());
        }
    }

    @Test
    void freshnessKeepsOverdueAndNullArrivalsButIgnoresExplicitlyLateArrivals() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        AtomicReference<Date> lateUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 10, 0));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(
            purchaseLine(2101L, 2001L, 501L, "10", "1"),
            purchaseLine(2102L, 2002L, 501L, "10", "1"),
            purchaseLine(2103L, 2003L, 501L, "10", "1"),
            purchaseLine(2104L, 2004L, 501L, "10", "1")));
        when(purchaseOrderMapper.selectList(any())).thenAnswer(invocation -> {
            PuPurchaseOrder overdue = purchaseOrder(2001L, 301L, "APPROVED", TODAY.minusDays(2));
            PuPurchaseOrder unknown = purchaseOrder(2002L, 302L, "APPROVED", null);
            PuPurchaseOrder within = purchaseOrder(2003L, 303L, "APPROVED", TODAY.plusDays(20));
            PuPurchaseOrder late = purchaseOrder(2004L, 304L, "APPROVED", TODAY.plusDays(100));
            late.setUpdateTime(lateUpdate.get());
            return List.of(late, within, unknown, overdue);
        });

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        lateUpdate.set(toDateTime(2026, 9, 23, 10, 1));
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PuPurchaseOrder>> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchaseOrderMapper, atLeastOnce()).selectList(queries.capture());
        for (Wrapper<PuPurchaseOrder> query : queries.getAllValues()) {
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("expected_arrival_date"), sql);
            assertTrue(sql.contains("IS NULL"), sql);
            assertTrue(sql.contains("<="), sql);
            assertTrue(queryValues(query).contains(java.sql.Date.valueOf(TODAY.plusDays(89))),
                queryValues(query).toString());
        }
    }

    @Test
    void salesOrderLegacyDirectExcludesMaterialRequirementSourceWithoutProductionChain() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdWorkOrder unrelated = workOrder(11L, 811L, 999L, "RELEASED");
        unrelated.setSourceType("MATERIAL_REQUIREMENT");
        unrelated.setSourceId(100L);
        unrelated.setSourceLineId(101L);
        unrelated.setSourceNo("SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(line));
        when(productionPlanMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(unrelated));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(unrelated), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
            actor("ai:decision:run", "so:orderheader:query"));

        assertTrue(snapshot.workOrders().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(text -> text.contains("NO_WORK_ORDER")),
            snapshot.warnings().toString());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), anyCollection());
    }

    @Test
    void mixedStatusScopeKeepsApprovingAndSchedulesOnlyEligibleWorkOrders() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of());
        PrdWorkOrder approving = workOrder(11L, 811L, 201L, "APPROVING");
        approving.setSourceId(100L);
        PrdWorkOrder released = workOrder(12L, 812L, 202L, "RELEASED");
        released.setSourceId(100L);
        PrdWorkOrder completed = workOrder(13L, 813L, 203L, "COMPLETED");
        completed.setSourceId(100L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(completed, released, approving));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class),
            argThat(ids -> new LinkedHashSet<>(ids).equals(new LinkedHashSet<>(List.of(12L))))))
            .thenReturn(scheduleInput(List.of(released), false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
            actor("ai:decision:run", "so:orderheader:query"));

        assertEquals(List.of(11L, 12L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());
        assertFalse(snapshot.warnings().stream()
            .anyMatch(text -> text.contains("NO_SCHEDULABLE_WORK_ORDER")));
        verify(scheduleInputAssembler).assemble(any(PrdSchedulePlanBo.class),
            argThat(ids -> new LinkedHashSet<>(ids).equals(new LinkedHashSet<>(List.of(12L)))));
    }

    @Test
    void approvingRootWorkOrderReturnsFactsWithEmptyApsWarning() {
        PrdWorkOrder approving = workOrder(11L, 811L, 201L, "APPROVING");
        when(workOrderMapper.selectById(11L)).thenReturn(approving);
        stubEmptyRelatedFacts();

        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null);
        DecisionActor actor = actor("ai:decision:run", "prd:work:query");
        assertTrue(assembler.exists(ref, actor));
        DeliveryContextSnapshot snapshot = assembler.assemble(ref, 14, actor);

        assertEquals(List.of(11L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());
        assertTrue(snapshot.capacityByDate().isEmpty());
        assertTrue(snapshot.warnings().stream()
            .anyMatch(text -> text.contains("NO_SCHEDULABLE_WORK_ORDER")),
            snapshot.warnings().toString());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), anyCollection());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
    }

    @Test
    void schedulePlanWithOnlyTerminalWorkOrdersPreservesPlanAndFingerprintVersions() {
        AtomicReference<String> strategy = new AtomicReference<>("EDD");
        AtomicReference<Date> planUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0));
        AtomicReference<Long> resourceId = new AtomicReference<>(6011L);
        AtomicReference<Date> taskUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 9, 0));
        when(schedulePlanMapper.selectById(9001L)).thenAnswer(invocation -> {
            PrdSchedulePlan plan =
                schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7));
            plan.setStrategy(strategy.get());
            plan.setUpdateTime(planUpdate.get());
            return plan;
        });
        when(scheduleTaskMapper.selectList(any())).thenAnswer(invocation -> {
            PrdScheduleTask task = scheduleTask(501L, 9001L, 11L, 1, "1.250", TODAY);
            task.setResourceId(resourceId.get());
            task.setUpdateTime(taskUpdate.get());
            return List.of(task);
        });
        when(workOrderMapper.selectList(any())).thenReturn(
            List.of(workOrder(11L, 811L, 201L, "COMPLETED")));
        stubEmptyRelatedFacts();
        DecisionBusinessRef ref =
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null);
        DecisionActor actor = actor("ai:decision:run", "prd:schedule:query");

        DeliveryContextSnapshot snapshot = assembler.assemble(ref, 14, actor);

        assertTrue(snapshot.workOrders().isEmpty());
        assertEquals("9001", snapshot.currentSchedule().get("planId"));
        assertEquals("PUBLISHED", snapshot.currentSchedule().get("status"));
        assertEquals("EDD", snapshot.currentSchedule().get("strategy"));
        assertEquals(List.of(), snapshot.currentSchedule().get("tasks"));
        String baseline = snapshot.snapshotHash();

        strategy.set("CR");
        planUpdate.set(toDateTime(2026, 9, 23, 8, 1));
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor));

        strategy.set("EDD");
        planUpdate.set(toDateTime(2026, 9, 23, 8, 0));
        resourceId.set(6012L);
        taskUpdate.set(toDateTime(2026, 9, 23, 9, 1));
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor));
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), anyCollection());
    }

    @Test
    void schedulePlanCompletedTaskWorkOrderDoesNotFailOrEnterScope() {
        PrdSchedulePlan plan = schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7));
        PrdScheduleTask completedTask = scheduleTask(501L, 9001L, 11L, 1, "1.000", TODAY);
        PrdScheduleTask releasedTask = scheduleTask(502L, 9001L, 12L, 1, "1.000", TODAY);
        PrdWorkOrder completed = workOrder(11L, 811L, 201L, "COMPLETED");
        PrdWorkOrder released = workOrder(12L, 812L, 202L, "RELEASED");
        when(schedulePlanMapper.selectById(9001L)).thenReturn(plan);
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(completedTask, releasedTask));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(completed, released));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class),
            argThat(ids -> new LinkedHashSet<>(ids).equals(new LinkedHashSet<>(List.of(12L))))))
            .thenReturn(scheduleInput(List.of(released), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null), 14,
            actor("ai:decision:run", "prd:schedule:query"));

        assertEquals(List.of(12L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());
    }

    @Test
    void salesOrderUsesLineProductionPlanChainAndBindsRequirementToSourceWorkOrder() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdProductionPlan plan = productionPlan(201L, 101L, 301L);
        PrdMaterialRequirement requirement = requirement(301L, 201L);
        PrdMaterialRequirementDetail detail = requirementDetail(901L, 301L, 501L, "10", "2", TODAY);
        PrdWorkOrder workOrder = workOrder(11L, 811L, 201L, "RELEASED");
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("so_id") && queryValues(wrapper).contains(100L))))
            .thenReturn(List.of(line));
        when(productionPlanMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("order_line_id") && queryValues(wrapper).contains(101L))))
            .thenReturn(List.of(plan));
        when(workOrderMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("plan_id") && queryValues(wrapper).contains(201L))))
            .thenReturn(List.of(workOrder));
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(detail));
        when(stockMapper.selectList(any())).thenReturn(List.of());
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(workOrder), false));

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
            actor("ai:decision:run", "so:orderheader:query"));

        assertEquals(List.of(11L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());
        assertEquals(11L, snapshot.requirements().get(0).workOrderId());
        assertEquals("WO-11", snapshot.requirements().get(0).workOrderNo());
    }

    @Test
    void workOrderRestoresSalesOrderThroughProductionPlanOrderLine() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdProductionPlan plan = productionPlan(201L, 101L, 301L);
        PrdWorkOrder workOrder = workOrder(11L, 811L, 201L, "RELEASED");
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(productionPlanMapper.selectBatchIds(argThat(ids -> ids.contains(201L))))
            .thenReturn(List.of(plan));
        when(salesOrderLineMapper.selectBatchIds(argThat(ids -> ids.contains(101L))))
            .thenReturn(List.of(line));
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(workOrder), false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), 14,
            actor("ai:decision:run", "prd:work:query"));

        assertEquals(List.of(100L), snapshot.salesOrders().stream()
            .map(DeliveryContextSnapshot.OrderFact::id).toList());
    }

    @Test
    void schedulePlanRestoresSalesOrderAndWorkOrderMappingThroughProductionPlan() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdProductionPlan productionPlan = productionPlan(201L, 101L, 301L);
        PrdWorkOrder workOrder = workOrder(11L, 811L, 201L, "RELEASED");
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        PrdSchedulePlan schedulePlan = schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7));
        PrdScheduleTask task = scheduleTask(501L, 9001L, 11L, 1, "1.250", TODAY);
        when(schedulePlanMapper.selectById(9001L)).thenReturn(schedulePlan);
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(workOrderMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("wo_id") && queryValues(wrapper).contains(11L))))
            .thenReturn(List.of(workOrder));
        when(productionPlanMapper.selectBatchIds(argThat(ids -> ids.contains(201L))))
            .thenReturn(List.of(productionPlan));
        when(salesOrderLineMapper.selectBatchIds(argThat(ids -> ids.contains(101L))))
            .thenReturn(List.of(line));
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(workOrder), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null), 14,
            actor("ai:decision:run", "prd:schedule:query"));

        assertEquals(List.of(100L), snapshot.salesOrders().stream()
            .map(DeliveryContextSnapshot.OrderFact::id).toList());
        assertEquals(List.of("100"), snapshot.currentSchedule().get("workOrderSalesOrderIds"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings =
            (List<Map<String, Object>>) snapshot.currentSchedule().get("workOrderSalesOrders");
        assertEquals("11", mappings.get(0).get("workOrderId"));
        assertEquals("100", mappings.get(0).get("salesOrderId"));
    }

    @Test
    void computeFreshnessHashResolvesErpDatasourceForPublicInvocation() throws Exception {
        DataSourceClassResolver resolver = new DataSourceClassResolver(true);
        Method method = DeliveryContextAssembler.class.getMethod(
            "computeFreshnessHash", DecisionBusinessRef.class, DecisionActor.class);

        assertEquals("erp", resolver.findKey(method, assembler, DS.class));
    }

    @Test
    void unrelatedCapacitySourceUpdateDoesNotChangeHash() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        stubRoutingSourceFacts(new AtomicReference<>(7001L),
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0)));
        AtomicReference<Date> unrelatedUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 9, 0));
        when(capacityCalendarMapper.selectList(any())).thenAnswer(invocation -> {
            PrdCapacityCalendar relevant = capacityCalendar(6001L, "WORKSTATION", 1100L, "8");
            relevant.setUpdateTime(toDateTime(2026, 9, 23, 8, 30));
            PrdCapacityCalendar unrelated = capacityCalendar(6002L, "WORKSTATION", 9999L, "8");
            unrelated.setUpdateTime(unrelatedUpdate.get());
            return List.of(unrelated, relevant);
        });

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        unrelatedUpdate.set(toDateTime(2026, 9, 23, 9, 1));
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
    }

    @Test
    void capacitySourceUpdateChangesHashEvenWhenDerivedCapacityIsUnchanged() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        stubRoutingSourceFacts(new AtomicReference<>(7001L),
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0)));
        AtomicReference<Date> calendarUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 9, 0));
        when(capacityCalendarMapper.selectList(any())).thenAnswer(invocation -> {
            PrdCapacityCalendar calendar = capacityCalendar(6001L, "WORKSTATION", 1100L, "8");
            calendar.setUpdateTime(calendarUpdate.get());
            return List.of(calendar);
        });

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        calendarUpdate.set(toDateTime(2026, 9, 23, 9, 1));
        String changed = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertNotEquals(first, changed);
    }

    @Test
    void routingSourceVersionChangesHashAndSnapshotUsesRealRoutingId() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        AtomicReference<Long> routingId = new AtomicReference<>(7001L);
        AtomicReference<Date> routingUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0));
        stubRoutingSourceFacts(routingId, routingUpdate);

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertEquals(7001L, snapshot.routingByMaterial().get(811L).get(0).routingId());
        String baseline = snapshot.snapshotHash();

        routingId.set(7002L);
        String idChanged = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        assertNotEquals(baseline, idChanged);

        routingId.set(7001L);
        routingUpdate.set(toDateTime(2026, 9, 23, 8, 1));
        String versionChanged = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        assertNotEquals(baseline, versionChanged);
    }

    @Test
    void currentScheduleResourceStartTimeAndPlanStrategyChangeHash() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        AtomicReference<Long> resourceId = new AtomicReference<>(6012L);
        AtomicReference<LocalDate> startDay = new AtomicReference<>(TODAY.plusDays(1));
        AtomicReference<String> strategy = new AtomicReference<>("EDD");
        when(schedulePlanMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            PrdSchedulePlan plan = schedulePlan(5002L, "APS-5002", "PUBLISHED", TODAY, TODAY.plusDays(5));
            plan.setStrategy(strategy.get());
            plan.setTotalHours(new BigDecimal("16.500"));
            plan.setUpdateTime(toDateTime(2026, 9, 23, 8, 0));
            return List.of(plan);
        });
        when(scheduleTaskMapper.selectList(any())).thenAnswer(invocation -> {
            PrdScheduleTask task = scheduleTask(4002L, 5002L, 12L, 1, "2.000", startDay.get());
            task.setResourceId(resourceId.get());
            task.setUpdateTime(toDateTime(2026, 9, 23, 9, 0));
            return List.of(task);
        });

        String baseline = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        resourceId.set(6013L);
        assertNotEquals(baseline, assembler.computeFreshnessHash(fixture.ref(), fixture.actor()));

        resourceId.set(6012L);
        startDay.set(TODAY.plusDays(2));
        assertNotEquals(baseline, assembler.computeFreshnessHash(fixture.ref(), fixture.actor()));

        startDay.set(TODAY.plusDays(1));
        strategy.set("CR");
        assertNotEquals(baseline, assembler.computeFreshnessHash(fixture.ref(), fixture.actor()));
    }

    @Test
    void salesOrderWithoutWorkOrdersReturnsEmptySnapshotAndStableHash() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        DecisionActor actor = actor("ai:decision:run", "so:orderheader:query");

        assertTrue(assembler.exists(ref, actor));
        DeliveryContextSnapshot snapshot = assembler.assemble(ref, 14, actor);
        String independentlyRecomputed = assembler.computeFreshnessHash(ref, actor);

        assertEquals(1, snapshot.salesOrders().size());
        assertTrue(snapshot.workOrders().isEmpty());
        assertTrue(snapshot.routingByMaterial().isEmpty());
        assertTrue(snapshot.capacityByDate().isEmpty());
        assertTrue(snapshot.holidays().isEmpty());
        assertTrue(snapshot.currentSchedule().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(text -> text.contains("NO_WORK_ORDER")),
            snapshot.warnings().toString());
        assertEquals(snapshot.snapshotHash(), independentlyRecomputed);
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), anyCollection());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
    }

    @Test
    void shortageHorizonWithoutWorkOrdersReturnsEmptySnapshotAndStableHash() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);
        DecisionActor actor = actor("ai:decision:run", "prd:shortage:query");

        assertTrue(assembler.exists(ref, actor));
        DeliveryContextSnapshot snapshot = assembler.assemble(ref, 30, actor);
        String independentlyRecomputed = assembler.computeFreshnessHash(ref, actor);

        assertTrue(snapshot.salesOrders().isEmpty());
        assertTrue(snapshot.workOrders().isEmpty());
        assertTrue(snapshot.routingByMaterial().isEmpty());
        assertTrue(snapshot.requirements().isEmpty());
        assertTrue(snapshot.stocks().isEmpty());
        assertTrue(snapshot.arrivals().isEmpty());
        assertTrue(snapshot.capacityByDate().isEmpty());
        assertTrue(snapshot.holidays().isEmpty());
        assertTrue(snapshot.currentSchedule().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(text -> text.contains("NO_WORK_ORDER")),
            snapshot.warnings().toString());
        assertEquals(snapshot.snapshotHash(), independentlyRecomputed);
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class), anyCollection());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
    }

    @Test
    void rejectsMismatchedSalesOrderRootIdBeforeLoadingDownstreamFacts() {
        SoSalesOrderHeader wrong = salesOrder(999L, "SO-999");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(wrong);
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        DecisionActor actor = actor("ai:decision:run", "so:orderheader:query");

        assertFalse(assembler.exists(ref, actor));
        ServiceException assembleError = assertThrows(ServiceException.class,
            () -> assembler.assemble(ref, 14, actor));
        ServiceException hashError = assertThrows(ServiceException.class,
            () -> assembler.computeFreshnessHash(ref, actor));

        assertTrue(assembleError.getMessage().contains("不存在或不可访问"), assembleError.getMessage());
        assertTrue(hashError.getMessage().contains("不存在或不可访问"), hashError.getMessage());
        verifyNoInteractions(salesOrderLineMapper, workOrderMapper, scheduleInputAssembler);
    }

    @Test
    void rejectsMismatchedWorkOrderRootIdBeforeLoadingDownstreamFacts() {
        PrdWorkOrder wrong = workOrder(999L, 811L, 701L, "RELEASED");
        when(workOrderMapper.selectById(11L)).thenReturn(wrong);
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null);
        DecisionActor actor = actor("ai:decision:run", "prd:work:query");

        assertFalse(assembler.exists(ref, actor));
        ServiceException assembleError = assertThrows(ServiceException.class,
            () -> assembler.assemble(ref, 14, actor));
        ServiceException hashError = assertThrows(ServiceException.class,
            () -> assembler.computeFreshnessHash(ref, actor));

        assertTrue(assembleError.getMessage().contains("不存在或不可访问"), assembleError.getMessage());
        assertTrue(hashError.getMessage().contains("不存在或不可访问"), hashError.getMessage());
        verifyNoInteractions(salesOrderHeaderMapper, scheduleInputAssembler);
    }

    @Test
    void rejectsMismatchedSchedulePlanRootIdBeforeLoadingDownstreamFacts() {
        PrdSchedulePlan wrong = schedulePlan(9999L, "APS-9999", "PUBLISHED", TODAY, TODAY.plusDays(7));
        when(schedulePlanMapper.selectById(9001L)).thenReturn(wrong);
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null);
        DecisionActor actor = actor("ai:decision:run", "prd:schedule:query");

        assertFalse(assembler.exists(ref, actor));
        ServiceException assembleError = assertThrows(ServiceException.class,
            () -> assembler.assemble(ref, 14, actor));
        ServiceException hashError = assertThrows(ServiceException.class,
            () -> assembler.computeFreshnessHash(ref, actor));

        assertTrue(assembleError.getMessage().contains("不存在或不可访问"), assembleError.getMessage());
        assertTrue(hashError.getMessage().contains("不存在或不可访问"), hashError.getMessage());
        verifyNoInteractions(scheduleTaskMapper, workOrderMapper, scheduleInputAssembler);
    }

    @BeforeEach
    void fixedBusinessDate() {
        doReturn(TODAY).when(assembler).businessDate();
    }

    @Test
    void assembleUsesOneBusinessDateAndOneFactReadForSnapshotAndHash() {
        AtomicBoolean reverse = new AtomicBoolean(false);
        AtomicReference<BigDecimal> stockQty = new AtomicReference<>(new BigDecimal("20"));
        AtomicReference<BigDecimal> reservedQty = new AtomicReference<>(new BigDecimal("3"));
        RichFixture fixture = stubRichSalesOrderFixture(reverse, stockQty, reservedQty);
        AtomicInteger stockReads = new AtomicInteger();
        when(stockMapper.selectList(any())).thenAnswer(invocation -> {
            if (stockReads.incrementAndGet() > 1) {
                throw new AssertionError("assemble 不得为 hash 二次读取库存事实");
            }
            return List.of(
                stock(10001L, 501L, "20", "3"),
                stock(10002L, 501L, "5", "1"),
                stock(10003L, 502L, "8", "0"));
        });
        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());

        assertEquals(1, stockReads.get());
        assertEquals(new BigDecimal("25"), snapshot.stocks().stream()
            .filter(stock -> Long.valueOf(501L).equals(stock.materialId()))
            .findFirst().orElseThrow().stockQty());
        assertTrue(snapshot.snapshotHash().matches("[0-9a-f]{64}"), snapshot.snapshotHash());
        verify(salesOrderHeaderMapper, times(1)).selectById(100L);
        verify(assembler, times(1)).businessDate();
    }

    @Test
    void schedulePlanScopeLoadsOnlyTaskWorkOrdersAndProducesJsonSafeCurrentSchedule() {
        PrdSchedulePlan plan = schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7));
        plan.setTotalHours(new BigDecimal("16.500"));
        when(schedulePlanMapper.selectById(9001L)).thenReturn(plan);

        PrdScheduleTask task12 = scheduleTask(502L, 9001L, 12L, 2, "2.500", TODAY.plusDays(1));
        PrdScheduleTask task11 = scheduleTask(501L, 9001L, 11L, 1, "1.250", TODAY);
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(task12, task11));

        PrdWorkOrder wo11 = workOrder(11L, 811L, 701L, "RELEASED");
        PrdWorkOrder wo12 = workOrder(12L, 812L, 702L, "IN_PROGRESS");
        PrdWorkOrder unrelated = workOrder(99L, 899L, 799L, "RELEASED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(unrelated, wo12, wo11));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenAnswer(invocation -> scheduleInput(List.of(wo12, wo11), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, "untrusted"),
            14,
            actor("ai:decision:run", "prd:schedule:query"));

        assertEquals("APS-9001", snapshot.rootBusinessRef().code());
        assertEquals(List.of(11L, 12L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());
        assertFalse(snapshot.workOrders().stream().anyMatch(fact -> fact.id().equals(99L)));

        Map<String, Object> schedule = snapshot.currentSchedule();
        assertEquals("9001", schedule.get("planId"));
        assertEquals("2026-09-24", schedule.get("startDate"));
        assertEquals("2026-10-01", schedule.get("makespanDate"));
        assertEquals(List.of("11", "12"), schedule.get("workOrderIds"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) schedule.get("tasks");
        assertEquals(List.of("11", "12"), tasks.stream().map(task -> task.get("woId")).toList());
        assertEquals("1.250", tasks.get(0).get("processHours"));
        assertEquals("2026-09-24", tasks.get(0).get("startTime"));
        assertJsonSafe(schedule);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> workOrderQueries = ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper, atLeastOnce()).selectList(workOrderQueries.capture());
        for (Wrapper<PrdWorkOrder> query : workOrderQueries.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("wo_id"), query.getSqlSegment());
            Collection<?> values = queryValues(query);
            assertTrue(values.contains(11L), values.toString());
            assertTrue(values.contains(12L), values.toString());
            assertFalse(values.contains(99L), values.toString());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> scopedIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<PrdSchedulePlanBo> bos = ArgumentCaptor.forClass(PrdSchedulePlanBo.class);
        verify(scheduleInputAssembler, atLeastOnce()).assemble(bos.capture(), scopedIds.capture());
        assertTrue(scopedIds.getAllValues().stream()
            .allMatch(ids -> new LinkedHashSet<>(ids).equals(new LinkedHashSet<>(List.of(11L, 12L)))));
        assertTrue(bos.getAllValues().stream()
            .allMatch(bo -> Integer.valueOf(90).equals(bo.getHorizonDays())));
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
    }

    @Test
    void salesOrderScopeUsesAllThreeAssociationKeysWithoutFallingBackToAllWorkOrders() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        SoSalesOrderLine line1 = salesOrderLine(1001L, 100L, 501L);
        SoSalesOrderLine line2 = salesOrderLine(1002L, 100L, 502L);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(line2, line1));

        PrdWorkOrder bySourceId = workOrder(11L, 811L, 701L, "RELEASED");
        bySourceId.setSourceId(100L);
        PrdWorkOrder bySourceLine = workOrder(12L, 812L, 702L, "IN_PROGRESS");
        bySourceLine.setSourceLineId(1002L);
        PrdWorkOrder bySourceNo = workOrder(13L, 813L, 703L, "APPROVED");
        bySourceNo.setSourceNo("SO-100");
        PrdWorkOrder unrelated = workOrder(99L, 899L, 799L, "RELEASED");
        unrelated.setSourceId(999L);
        unrelated.setSourceLineId(9999L);
        unrelated.setSourceNo("SO-OTHER");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(unrelated, bySourceNo, bySourceLine, bySourceId));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenAnswer(invocation -> scheduleInput(List.of(bySourceNo, bySourceLine, bySourceId), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, "forged"),
            30,
            actor("ai:decision:run", "so:orderheader:query"));

        assertEquals(new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, "SO-100"),
            snapshot.rootBusinessRef());
        assertEquals(List.of(11L, 12L, 13L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).toList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper, atLeastOnce()).selectList(queries.capture());
        List<String> sqlSegments = queries.getAllValues().stream()
            .map(Wrapper::getSqlSegment)
            .toList();
        List<Object> values = queries.getAllValues().stream()
            .flatMap(query -> queryValues(query).stream())
            .map(value -> (Object) value)
            .toList();
        assertTrue(sqlSegments.stream().anyMatch(sql -> sql.contains("source_id")),
            sqlSegments.toString());
        assertTrue(sqlSegments.stream().anyMatch(sql -> sql.contains("source_line_id")),
            sqlSegments.toString());
        assertTrue(sqlSegments.stream().anyMatch(sql -> sql.contains("source_no")),
            sqlSegments.toString());
        assertTrue(values.contains(100L), values.toString());
        assertTrue(values.contains(1002L), values.toString());
        assertTrue(values.contains("SO-100"), values.toString());
        verify(scheduleInputAssembler, never()).assemble(any(PrdSchedulePlanBo.class));
    }

    @Test
    void mapperReturnOrderDoesNotChangeCriticalFreshnessHash() {
        AtomicBoolean reverse = new AtomicBoolean(false);
        RichFixture fixture = stubRichSalesOrderFixture(reverse, new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        reverse.set(true);
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{64}"), first);
    }

    @Test
    void snapshotAtDoesNotAffectHashButCriticalStockOrReservationChangesDo() throws InterruptedException {
        AtomicBoolean reverse = new AtomicBoolean(false);
        AtomicReference<BigDecimal> stockQty = new AtomicReference<>(new BigDecimal("20"));
        AtomicReference<BigDecimal> reservedQty = new AtomicReference<>(new BigDecimal("3"));
        RichFixture fixture = stubRichSalesOrderFixture(reverse, stockQty, reservedQty);

        DeliveryContextSnapshot first = assembler.assemble(fixture.ref(), 14, fixture.actor());
        Thread.sleep(2L);
        DeliveryContextSnapshot second = assembler.assemble(fixture.ref(), 14, fixture.actor());

        assertNotEquals(first.snapshotAt(), second.snapshotAt());
        assertEquals(first.snapshotHash(), second.snapshotHash());

        stockQty.set(new BigDecimal("21"));
        String stockChanged = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        assertNotEquals(first.snapshotHash(), stockChanged);

        stockQty.set(new BigDecimal("20"));
        reservedQty.set(new BigDecimal("4"));
        String reservationChanged = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        assertNotEquals(first.snapshotHash(), reservationChanged);
    }

    @Test
    void requirementAssociationIsNullWhenMultipleWorkOrdersSharePlanAndMissingArrivalDateOnlyWarns() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(salesOrderLine(1001L, 100L, 501L)));

        PrdWorkOrder wo11 = workOrder(11L, 811L, 701L, "RELEASED");
        wo11.setSourceId(100L);
        PrdWorkOrder wo12 = workOrder(12L, 812L, 701L, "RELEASED");
        wo12.setSourceId(100L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo12, wo11));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenAnswer(invocation -> scheduleInput(List.of(wo12, wo11), false));

        PrdMaterialRequirement requirement = requirement(801L, 701L);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        PrdMaterialRequirementDetail detail = requirementDetail(901L, 801L, 501L, "10", "2", TODAY);
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(detail));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10001L, 501L, "20", "3")));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(material(501L, "2")));

        PuPurchaseOrder purchaseOrder = purchaseOrder(2001L, 301L, "APPROVED", null);
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(purchaseOrder));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(
            purchaseLine(2101L, 2001L, 501L, "12", "2")));
        when(vendorEvaluationMapper.selectList(any())).thenReturn(List.of());
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null),
            14,
            actor("ai:decision:run", "so:orderheader:query"));

        assertEquals(1, snapshot.requirements().size());
        assertNull(snapshot.requirements().get(0).workOrderId());
        assertNull(snapshot.requirements().get(0).workOrderNo());
        assertTrue(snapshot.arrivals().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(text -> text.contains("唯一") || text.contains("多个工单")),
            snapshot.warnings().toString());
        assertTrue(snapshot.warnings().stream().anyMatch(text -> text.contains("预计到货日期")),
            snapshot.warnings().toString());
    }

    @Test
    void shortageHorizonRequiresNullBusinessIdAndBothPermissions() {
        DecisionBusinessRef invalidId = new DecisionBusinessRef(
            DecisionBusinessType.SHORTAGE_HORIZON, 1L, "SHORTAGE");
        DecisionActor allowed = actor("ai:decision:run", "prd:shortage:query");
        DecisionBusinessRef validRef = new DecisionBusinessRef(
            DecisionBusinessType.SHORTAGE_HORIZON, null, "SHORTAGE");
        DecisionActor missingReadPermission = actor("ai:decision:run");

        assertFalse(assembler.exists(invalidId, allowed));
        assertThrows(ServiceException.class, () -> assembler.assemble(invalidId, 14, allowed));
        assertFalse(assembler.exists(validRef, missingReadPermission));
        assertThrows(ServiceException.class, () -> assembler.assemble(validRef, 14, missingReadPermission));
        verifyNoInteractions(workOrderMapper, scheduleInputAssembler);
    }

    @Test
    void invalidActorAndHorizonFailClosedBeforeMapperAccess() {
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        DecisionActor blankTenant = new DecisionActor(" ", 7L, "tester",
            Set.of("ai:decision:run", "so:orderheader:query"));
        DecisionActor invalidUser = new DecisionActor("000000", 0L, "tester",
            Set.of("ai:decision:run", "so:orderheader:query"));

        assertFalse(assembler.exists(ref, blankTenant));
        assertFalse(assembler.exists(ref, invalidUser));
        assertThrows(ServiceException.class, () -> assembler.assemble(ref, 0, actor("*:*:*")));
        assertThrows(ServiceException.class, () -> assembler.assemble(ref, 91, actor("*:*:*")));
        verifyNoInteractions(salesOrderHeaderMapper);
    }

    private RichFixture stubRichSalesOrderFixture(AtomicBoolean reverse,
                                                  AtomicReference<BigDecimal> stockQty,
                                                  AtomicReference<BigDecimal> reservedQty) {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        order.setPromiseDate(java.sql.Date.valueOf("2026-10-10"));
        order.setUpdateTime(toDateTime(2026, 9, 23, 9, 0));
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);

        List<SoSalesOrderLine> lines = List.of(
            salesOrderLine(1001L, 100L, 501L),
            salesOrderLine(1002L, 100L, 502L));
        when(salesOrderLineMapper.selectList(any())).thenAnswer(invocation -> ordered(lines, reverse.get()));

        PrdWorkOrder wo11 = workOrder(11L, 811L, 701L, "RELEASED");
        wo11.setSourceId(100L);
        wo11.setUpdateTime(toDateTime(2026, 9, 23, 10, 0));
        PrdWorkOrder wo12 = workOrder(12L, 812L, 702L, "IN_PROGRESS");
        wo12.setSourceLineId(1002L);
        wo12.setUpdateTime(toDateTime(2026, 9, 23, 10, 1));
        List<PrdWorkOrder> workOrders = List.of(wo11, wo12);
        when(workOrderMapper.selectList(any())).thenAnswer(invocation -> ordered(workOrders, reverse.get()));

        List<PrdMaterialRequirement> requirements = List.of(requirement(801L, 701L), requirement(802L, 702L));
        when(requirementMapper.selectList(any())).thenAnswer(invocation -> ordered(requirements, reverse.get()));
        PrdMaterialRequirementDetail detail1 = requirementDetail(901L, 801L, 501L, "10", "2", TODAY.plusDays(2));
        detail1.setUpdateTime(toDateTime(2026, 9, 23, 11, 0));
        PrdMaterialRequirementDetail detail2 = requirementDetail(902L, 802L, 502L, "6", "1", TODAY.plusDays(3));
        detail2.setUpdateTime(toDateTime(2026, 9, 23, 11, 1));
        List<PrdMaterialRequirementDetail> details = List.of(detail1, detail2);
        when(requirementDetailMapper.selectList(any())).thenAnswer(invocation -> ordered(details, reverse.get()));

        Date stockUpdate1 = toDateTime(2026, 9, 23, 12, 0);
        Date stockUpdate2 = toDateTime(2026, 9, 23, 12, 1);
        Date stockUpdate3 = toDateTime(2026, 9, 23, 12, 2);
        when(stockMapper.selectList(any())).thenAnswer(invocation -> {
            WmMaterialStock stock1 = stock(10001L, 501L, stockQty.get().toPlainString(), reservedQty.get().toPlainString());
            stock1.setUpdateTime(stockUpdate1);
            WmMaterialStock stock2 = stock(10002L, 501L, "5", "1");
            stock2.setUpdateTime(stockUpdate2);
            WmMaterialStock stock3 = stock(10003L, 502L, "8", "0");
            stock3.setUpdateTime(stockUpdate3);
            return ordered(List.of(stock1, stock2, stock3), reverse.get());
        });
        List<BdMaterial> materials = List.of(material(501L, "2"), material(502L, "1"));
        when(materialMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> ordered(materials, reverse.get()));

        PuPurchaseOrder po1 = purchaseOrder(2001L, 301L, "APPROVED", TODAY.plusDays(4));
        po1.setUpdateTime(toDateTime(2026, 9, 23, 13, 0));
        PuPurchaseOrder po2 = purchaseOrder(2002L, 302L, "PARTIALLY_RECEIVED", TODAY.plusDays(5));
        po2.setUpdateTime(toDateTime(2026, 9, 23, 13, 1));
        List<PuPurchaseOrder> purchaseOrders = List.of(po1, po2);
        when(purchaseOrderMapper.selectList(any())).thenAnswer(invocation -> ordered(purchaseOrders, reverse.get()));
        PuPurchaseOrderLine poLine1 = purchaseLine(2101L, 2001L, 501L, "12", "2");
        poLine1.setUpdateTime(toDateTime(2026, 9, 23, 14, 0));
        PuPurchaseOrderLine poLine2 = purchaseLine(2102L, 2002L, 502L, "6", "1");
        poLine2.setUpdateTime(toDateTime(2026, 9, 23, 14, 1));
        List<PuPurchaseOrderLine> purchaseLines = List.of(poLine1, poLine2);
        when(purchaseOrderLineMapper.selectList(any())).thenAnswer(invocation -> ordered(purchaseLines, reverse.get()));

        PuVendorEvaluation evaluation1 = evaluation(3001L, 301L, "2026-08", "95", 10);
        PuVendorEvaluation evaluation2 = evaluation(3002L, 302L, "2026-09", "90", 8);
        List<PuVendorEvaluation> evaluations = List.of(evaluation1, evaluation2);
        when(vendorEvaluationMapper.selectList(any())).thenAnswer(invocation -> ordered(evaluations, reverse.get()));

        PrdSchedulePlan oldPlan = schedulePlan(5001L, "APS-5001", "COMPLETED", TODAY.minusDays(1), TODAY.plusDays(4));
        oldPlan.setUpdateTime(toDateTime(2026, 9, 22, 8, 0));
        PrdSchedulePlan latestPlan = schedulePlan(5002L, "APS-5002", "PUBLISHED", TODAY, TODAY.plusDays(5));
        latestPlan.setUpdateTime(toDateTime(2026, 9, 23, 8, 0));
        when(schedulePlanMapper.selectBatchIds(anyCollection()))
            .thenAnswer(invocation -> ordered(List.of(oldPlan, latestPlan), reverse.get()));
        PrdScheduleTask oldTask = scheduleTask(4001L, 5001L, 11L, 1, "1.000", TODAY);
        oldTask.setUpdateTime(toDateTime(2026, 9, 22, 9, 0));
        PrdScheduleTask latestTask = scheduleTask(4002L, 5002L, 12L, 1, "2.000", TODAY.plusDays(1));
        latestTask.setUpdateTime(toDateTime(2026, 9, 23, 9, 0));
        when(scheduleTaskMapper.selectList(any()))
            .thenAnswer(invocation -> ordered(List.of(oldTask, latestTask), reverse.get()));

        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenAnswer(invocation -> scheduleInput(workOrders, reverse.get()));

        return new RichFixture(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, "ignored"),
            actor("ai:decision:run", "so:orderheader:query"));
    }

    private void stubRoutingSourceFacts(AtomicReference<Long> routingId,
                                        AtomicReference<Date> routingUpdate) {
        when(routingMapper.selectList(any())).thenAnswer(invocation -> {
            BdRouting routing = new BdRouting();
            routing.setRoutingId(routingId.get());
            routing.setMaterialId(811L);
            routing.setRoutingVersion("1.0");
            routing.setIsCurrent(1L);
            routing.setStatus(1L);
            routing.setTotalHours(new BigDecimal("2.000"));
            routing.setUpdateTime(routingUpdate.get());
            return List.of(routing);
        });
        when(routingStepMapper.selectList(any())).thenAnswer(invocation -> {
            BdRoutingStep step = new BdRoutingStep();
            step.setStepId(7101L);
            step.setRoutingId(routingId.get());
            step.setSequenceNum(1);
            step.setWorkstationId(1100L);
            step.setStandardHours(new BigDecimal("1.250"));
            step.setSetupHours(new BigDecimal("0.500"));
            step.setIsOutsource(0);
            step.setWaitTime(BigDecimal.ZERO);
            step.setTransferTime(BigDecimal.ZERO);
            step.setUpdateTime(routingUpdate.get());
            return List.of(step);
        });
    }

    private PrdCapacityCalendar capacityCalendar(long id, String objectType, long objectId, String remaining) {
        PrdCapacityCalendar calendar = new PrdCapacityCalendar();
        calendar.setCalendarId(id);
        calendar.setCalendarDate(java.sql.Date.valueOf(TODAY));
        calendar.setObjectType(objectType);
        calendar.setObjectId(objectId);
        calendar.setRemainingHours(new BigDecimal(remaining));
        calendar.setStatus("ACTIVE");
        return calendar;
    }

    private void stubEmptyRelatedFacts() {
        when(requirementMapper.selectList(any())).thenReturn(List.of());
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of());
        when(stockMapper.selectList(any())).thenReturn(List.of());
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(vendorEvaluationMapper.selectList(any())).thenReturn(List.of());
    }

    private ScheduleInput scheduleInput(List<PrdWorkOrder> workOrders, boolean reverse) {
        List<PrdWorkOrder> orderedWorkOrders = ordered(workOrders, reverse);
        List<WoInfo> infos = new ArrayList<>();
        Map<Long, List<StepInfo>> routing = new LinkedHashMap<>();
        for (PrdWorkOrder workOrder : orderedWorkOrders) {
            WoInfo info = new WoInfo();
            info.setWoId(workOrder.getWoId());
            info.setWoNo(workOrder.getWoNo());
            info.setMaterialId(workOrder.getMaterialId());
            info.setRemainingQty(workOrder.getPlannedQty().subtract(workOrder.getCompletedQty()));
            info.setPriority(workOrder.getPriority());
            info.setDueDate(toLocalDate(workOrder.getPlanEndDate()));
            info.setWorkshopId(workOrder.getWorkshopId());
            infos.add(info);

            StepInfo first = step(workOrder.getWoId() * 10 + 1, 1, workOrder.getWoId() * 100, "1.250");
            StepInfo second = step(workOrder.getWoId() * 10 + 2, 2, workOrder.getWoId() * 100 + 1, "0.750");
            routing.put(workOrder.getMaterialId(), reverse ? List.of(second, first) : List.of(first, second));
        }

        LinkedHashMap<LocalDate, Map<String, BigDecimal>> capacity = new LinkedHashMap<>();
        Map<String, BigDecimal> day1 = new LinkedHashMap<>();
        if (reverse) {
            day1.put("WORKSHOP:20", new BigDecimal("16"));
            day1.put("WORKSTATION:1100", new BigDecimal("8"));
            capacity.put(TODAY.plusDays(1), Map.of("WORKSTATION:1200", new BigDecimal("6")));
            capacity.put(TODAY, day1);
        } else {
            day1.put("WORKSTATION:1100", new BigDecimal("8"));
            day1.put("WORKSHOP:20", new BigDecimal("16"));
            capacity.put(TODAY, day1);
            capacity.put(TODAY.plusDays(1), Map.of("WORKSTATION:1200", new BigDecimal("6")));
        }

        ScheduleInput input = new ScheduleInput();
        input.setWorkOrders(infos);
        input.setRoutingProvider(materialId -> routing.getOrDefault(materialId, List.of()));
        input.setCapacityByDate(capacity);
        input.setHolidays(new LinkedHashSet<>(reverse
            ? List.of(TODAY.plusDays(7), TODAY.plusDays(6))
            : List.of(TODAY.plusDays(6), TODAY.plusDays(7))));
        input.setStartDate(TODAY);
        input.setHorizonDays(90);
        input.setStrategy(Strategy.EDD);
        return input;
    }

    private SoSalesOrderHeader salesOrder(long id, String code) {
        SoSalesOrderHeader order = new SoSalesOrderHeader();
        order.setSoId(id);
        order.setSoNo(code);
        order.setPromiseDate(java.sql.Date.valueOf("2026-10-10"));
        order.setOrderStatus("CONFIRMED");
        return order;
    }

    private SoSalesOrderLine salesOrderLine(long lineId, long soId, long materialId) {
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(lineId);
        line.setSoId(soId);
        line.setMaterialId(materialId);
        line.setLineStatus("OPEN");
        return line;
    }

    private PrdWorkOrder workOrder(long id, long materialId, long planId, String status) {
        PrdWorkOrder workOrder = new PrdWorkOrder();
        workOrder.setWoId(id);
        workOrder.setWoNo("WO-" + id);
        workOrder.setPlanId(planId);
        workOrder.setMaterialId(materialId);
        workOrder.setMaterialCode("MAT-" + materialId);
        workOrder.setMaterialName("物料-" + materialId);
        workOrder.setPlannedQty(new BigDecimal("10"));
        workOrder.setCompletedQty(new BigDecimal("2"));
        workOrder.setPlanEndDate(java.sql.Date.valueOf("2026-10-05"));
        workOrder.setWoStatus(status);
        workOrder.setPriority(id);
        workOrder.setWorkshopId(20L);
        return workOrder;
    }

    private PrdProductionPlan productionPlan(long id, long orderLineId, long requirementId) {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(id);
        plan.setPlanNo("PP-" + id);
        plan.setOrderLineId(orderLineId);
        plan.setMaterialRequirementId(requirementId);
        plan.setStatus("RELEASED");
        return plan;
    }

    private PrdMaterialRequirement requirement(long id, long planId) {
        PrdMaterialRequirement requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(id);
        requirement.setPlanId(planId);
        requirement.setRequirementNo("MR-" + id);
        return requirement;
    }

    private PrdMaterialRequirementDetail requirementDetail(long id, long requirementId, long materialId,
                                                           String required, String issued, LocalDate date) {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(id);
        detail.setRequirementId(requirementId);
        detail.setMaterialId(materialId);
        detail.setMaterialCode("RM-" + materialId);
        detail.setMaterialName("原料-" + materialId);
        detail.setRequiredQuantity(new BigDecimal(required));
        detail.setIssuedQuantity(new BigDecimal(issued));
        detail.setRequiredDate(java.sql.Date.valueOf(date));
        return detail;
    }

    private WmMaterialStock stock(long id, long materialId, String stock, String reserved) {
        WmMaterialStock row = new WmMaterialStock();
        row.setStockId(id);
        row.setMaterialId(materialId);
        row.setStockQuantity(new BigDecimal(stock));
        row.setReservedQuantity(new BigDecimal(reserved));
        return row;
    }

    private BdMaterial material(long id, String safetyStock) {
        BdMaterial material = new BdMaterial();
        material.setMaterialId(id);
        material.setSafetyStock(new BigDecimal(safetyStock));
        return material;
    }

    private PuPurchaseOrder purchaseOrder(long id, long vendorId, String status, LocalDate expectedDate) {
        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderId(id);
        order.setVendorId(vendorId);
        order.setStatus(status);
        order.setExpectedArrivalDate(expectedDate == null ? null : java.sql.Date.valueOf(expectedDate));
        return order;
    }

    private PuPurchaseOrderLine purchaseLine(long id, long orderId, long materialId,
                                             String ordered, String received) {
        PuPurchaseOrderLine line = new PuPurchaseOrderLine();
        line.setLineId(id);
        line.setOrderId(orderId);
        line.setMaterialId(materialId);
        line.setOrderQuantity(new BigDecimal(ordered));
        line.setReceivedQuantity(new BigDecimal(received));
        return line;
    }

    private PuVendorEvaluation evaluation(long id, long vendorId, String period,
                                          String deliveryRate, int sampleCount) {
        PuVendorEvaluation evaluation = new PuVendorEvaluation();
        evaluation.setEvaluationId(id);
        evaluation.setVendorId(vendorId);
        evaluation.setEvalPeriod(period);
        evaluation.setDeliveryRate(new BigDecimal(deliveryRate));
        evaluation.setTotalOrders(sampleCount);
        evaluation.setUpdateTime(toDateTime(2026, 9, 23, 15, 0));
        return evaluation;
    }

    private PrdSchedulePlan schedulePlan(long id, String no, String status,
                                         LocalDate startDate, LocalDate makespanDate) {
        PrdSchedulePlan plan = new PrdSchedulePlan();
        plan.setPlanId(id);
        plan.setPlanNo(no);
        plan.setStatus(status);
        plan.setStrategy("EDD");
        plan.setStartDate(java.sql.Date.valueOf(startDate));
        plan.setHorizonDays(14);
        plan.setMakespanDate(java.sql.Date.valueOf(makespanDate));
        return plan;
    }

    private PrdScheduleTask scheduleTask(long id, long planId, long woId, int seq,
                                         String processHours, LocalDate day) {
        PrdScheduleTask task = new PrdScheduleTask();
        task.setTaskId(id);
        task.setPlanId(planId);
        task.setWoId(woId);
        task.setWoNo("WO-" + woId);
        task.setSeqNum(seq);
        task.setStepId(id + 1000);
        task.setResourceType("WORKSTATION");
        task.setResourceId(6000L + woId);
        task.setResourceName("WS-" + woId);
        task.setSetupHours(new BigDecimal("0.500"));
        task.setProcessHours(new BigDecimal(processHours));
        task.setStartTime(java.sql.Date.valueOf(day));
        task.setEndTime(java.sql.Date.valueOf(day.plusDays(1)));
        task.setDurationDays(2);
        task.setDelayFlag(0);
        return task;
    }

    private StepInfo step(long id, int seq, long workstationId, String standardHours) {
        StepInfo step = new StepInfo();
        step.setStepId(id);
        step.setSeqNum(seq);
        step.setWorkstationId(workstationId);
        step.setStandardHours(new BigDecimal(standardHours));
        return step;
    }

    private DecisionActor actor(String... permissions) {
        return new DecisionActor("000000", 7L, "tester", Set.of(permissions));
    }

    private Collection<?> queryValues(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    private LocalDate toLocalDate(Date date) {
        return java.time.Instant.ofEpochMilli(date.getTime())
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
    }

    private Date toDateTime(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private <T> List<T> ordered(List<T> source, boolean reverse) {
        List<T> copy = new ArrayList<>(source);
        if (reverse) {
            Collections.reverse(copy);
        }
        return copy;
    }

    private void assertJsonSafe(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Double || value instanceof Float) {
            return;
        }
        assertFalse(value instanceof Long, "ID/Long 必须先转换成 JSON-safe 字符串: " + value);
        assertFalse(value instanceof BigDecimal, "BigDecimal 必须先转换成普通十进制字符串: " + value);
        assertFalse(value instanceof Date, "Date 必须先转换成 ISO 字符串: " + value);
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                assertTrue(key instanceof String, "动态 Map 键必须为 String");
                assertJsonSafe(nested);
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(this::assertJsonSafe);
            return;
        }
        throw new AssertionError("currentSchedule 包含不安全类型: " + value.getClass().getName());
    }

    private record RichFixture(DecisionBusinessRef ref, DecisionActor actor) {
    }
}
