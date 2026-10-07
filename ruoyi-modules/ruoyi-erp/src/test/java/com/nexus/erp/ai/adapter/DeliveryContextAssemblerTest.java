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
import com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.PrdShortageScenarioService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * ERP 工业决策上下文装配测试：作用域必须精确，展示快照与可重算指纹必须稳定；
 * 校验失败时必须拒绝。
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
    private PrdScheduleTaskDayLoadMapper taskDayLoadMapper;
    @Mock
    private PrdScheduleCapacityBookingMapper capacityBookingMapper;
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
    private com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper evidenceUnitMapper;
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
            PrdSchedulePlan.class, PrdScheduleTask.class, PrdScheduleTaskDayLoad.class,
            PrdScheduleCapacityBooking.class, PrdMaterialRequirement.class,
            PrdMaterialRequirementDetail.class, WmMaterialStock.class, BdMaterial.class,
            PuPurchaseOrder.class, PuPurchaseOrderLine.class, PuVendorEvaluation.class,
            BdRouting.class, BdRoutingStep.class, PrdCapacityCalendar.class, SchHoliday.class,
            SchEmployeeSchedule.class, SchShift.class, SchTeam.class, SchLeaveApply.class,
            BdWorkstation.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void existenceSupportsQueryOnlyButDoesNotGrantAnalysisOrFreshnessCalculation() {
        var order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        var ref = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        var viewer = actor("ai:decision:query", "so:orderheader:query");
        assertTrue(assembler.exists(ref, viewer));
        assertFalse(assembler.exists(ref, actor("so:orderheader:query")));
        assertFalse(assembler.exists(ref, actor("ai:decision:query")));
        assertThrows(ServiceException.class, () -> assembler.assemble(ref, 14, viewer));
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(ref, viewer));
    }

    @Test
    void executorWithBusinessReadMayRecheckFreshnessWithoutAnalysisPermission() {
        var order = salesOrder(100L, "SO-100");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        stubEmptyRelatedFacts();
        var ref = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        var executor = actor("ai:decision:execute", "so:orderheader:query");
        assertTrue(assembler.exists(ref, executor));
        assertEquals(assembler.assemble(ref, 14, executor).snapshotHash(),
            assembler.computeFreshnessHash(ref, executor));
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
    void sqlDateKeepsLogicalDateWhenJvmDefaultUsesDifferentZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            SoSalesOrderHeader order = salesOrder(100L, "SO-100");
            order.setPromiseDate(java.sql.Date.valueOf(TODAY));
            when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
            when(salesOrderLineMapper.selectList(any())).thenReturn(List.of());
            when(workOrderMapper.selectList(any())).thenReturn(List.of());
            stubEmptyRelatedFacts();

            DeliveryContextSnapshot snapshot = assembler.assemble(
                new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
                actor("ai:decision:run", "so:orderheader:query"));

            assertEquals(TODAY, snapshot.salesOrders().get(0).promiseDate());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void freshnessHashIsStableAcrossJvmDefaultTimeZones() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            RichFixture utcFixture = stubRichSalesOrderFixture(
                new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
                new AtomicReference<>(new BigDecimal("3")));
            String utcHash = assembler.computeFreshnessHash(utcFixture.ref(), utcFixture.actor());

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            RichFixture losAngelesFixture = stubRichSalesOrderFixture(
                new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
                new AtomicReference<>(new BigDecimal("3")));
            String losAngelesHash = assembler.computeFreshnessHash(
                losAngelesFixture.ref(), losAngelesFixture.actor());

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
            assertHalfOpenWindow(query, "required_date", TODAY, TODAY.plusDays(90));
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
            assertTrue(sql.contains("expected_arrival_date <"), sql);
            assertFalse(sql.contains("expected_arrival_date <="), sql);
            assertDateParameter(query, TODAY.plusDays(90));
        }
    }

    @Test
    void distinctMrpDetailIdsWithSameWorkOrderMaterialAndDayStayAsDistinctSnapshotFacts() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        PrdMaterialRequirement requirement = requirement(801L, 701L);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(
            requirementDetail(9901L, 801L, 501L, "10", "2", TODAY),
            requirementDetail(9902L, 801L, 501L, "5", "2", TODAY)));

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertEquals(List.of(9901L, 9902L), snapshot.requirements().stream()
            .map(DeliveryContextSnapshot.RequirementFact::detailId).toList());
        assertEquals(List.of(new BigDecimal("8"), new BigDecimal("3")), snapshot.requirements().stream()
            .map(DeliveryContextSnapshot.RequirementFact::requiredQty).toList());
    }

    @Test
    void repeatedMrpDetailIdMustFailInsteadOfKeepingOnlyOneRow() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        PrdMaterialRequirement requirement = requirement(801L, 701L);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(
            requirementDetail(9901L, 801L, 501L, "10", "2", TODAY),
            requirementDetail(9901L, 801L, 501L, "6", "1", TODAY)));

        assertThrows(ServiceException.class, () -> assembler.assemble(fixture.ref(), 14, fixture.actor()));
    }

    @Test
    void fixedNinetyDayDatetimeWindowIncludesFinalDayFactsAndUsesExclusiveNextDay() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        LocalDate lastDay = TODAY.plusDays(89);

        PrdMaterialRequirement requirement = requirement(801L, 701L);
        PrdMaterialRequirementDetail detail =
            requirementDetail(9901L, 801L, 501L, "10", "2", lastDay);
        detail.setRequiredDate(atTime(lastDay, 23, 59, 59));
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(detail));

        PuPurchaseOrderLine line = purchaseLine(9902L, 9903L, 501L, "10", "1");
        PuPurchaseOrder order = purchaseOrder(9903L, 301L, "APPROVED", lastDay);
        order.setExpectedArrivalDate(atTime(lastDay, 12, 0, 0));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(line));
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(order));

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 90, fixture.actor());

        assertTrue(snapshot.requirements().stream()
            .anyMatch(fact -> lastDay.equals(fact.requiredDate()) && fact.materialId() == 501L),
            snapshot.requirements().toString());
        assertTrue(snapshot.arrivals().stream()
            .anyMatch(fact -> lastDay.equals(fact.expectedDate()) && fact.purchaseLineId() == 9902L),
            snapshot.arrivals().toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdMaterialRequirementDetail>> requirementQueries =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(requirementDetailMapper, atLeastOnce()).selectList(requirementQueries.capture());
        requirementQueries.getAllValues().forEach(query ->
            assertHalfOpenWindow(query, "required_date", TODAY, TODAY.plusDays(90)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PuPurchaseOrder>> purchaseQueries = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchaseOrderMapper, atLeastOnce()).selectList(purchaseQueries.capture());
        for (Wrapper<PuPurchaseOrder> query : purchaseQueries.getAllValues()) {
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("expected_arrival_date <"), sql);
            assertFalse(sql.contains("expected_arrival_date <="), sql);
            assertDateParameter(query, TODAY.plusDays(90));
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
        assertEquals(901L, snapshot.requirements().get(0).detailId(),
            "The exact MRP detail row must survive in the snapshot for numeric evidence ownership");
    }

    @Test
    void materialRequirementWorkOrderDoesNotRestoreSalesOrderByAccidentalSourceNumber() {
        PrdWorkOrder workOrder = workOrder(11L, 811L, 201L, "RELEASED");
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        workOrder.setSourceNo("SO-100");
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(productionPlanMapper.selectBatchIds(argThat(ids -> ids.contains(201L))))
            .thenReturn(List.of());
        when(salesOrderHeaderMapper.selectList(any())).thenReturn(List.of(salesOrder(100L, "SO-100")));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(workOrder), false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), 14,
            actor("ai:decision:run", "prd:work:query"));

        assertTrue(snapshot.salesOrders().isEmpty(), snapshot.salesOrders().toString());
        verify(salesOrderHeaderMapper, never()).selectList(any());
    }

    @Test
    void materialRequirementSchedulePlanDoesNotMapSalesOrderByAccidentalSourceNumber() {
        PrdWorkOrder workOrder = workOrder(11L, 811L, 201L, "RELEASED");
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(301L);
        workOrder.setSourceNo("SO-100");
        PrdSchedulePlan schedulePlan =
            schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7));
        PrdScheduleTask task = scheduleTask(501L, 9001L, 11L, 1, "1.250", TODAY);
        when(schedulePlanMapper.selectById(9001L)).thenReturn(schedulePlan);
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(workOrderMapper.selectList(argThat(wrapper ->
            wrapper.getSqlSegment().contains("wo_id") && queryValues(wrapper).contains(11L))))
            .thenReturn(List.of(workOrder));
        when(productionPlanMapper.selectBatchIds(argThat(ids -> ids.contains(201L))))
            .thenReturn(List.of());
        when(salesOrderHeaderMapper.selectList(any())).thenReturn(List.of(salesOrder(100L, "SO-100")));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(workOrder), false));
        stubEmptyRelatedFacts();

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null), 14,
            actor("ai:decision:run", "prd:schedule:query"));

        assertTrue(snapshot.salesOrders().isEmpty(), snapshot.salesOrders().toString());
        assertEquals(List.of(), snapshot.currentSchedule().get("workOrderSalesOrderIds"));
        assertEquals(List.of(), snapshot.currentSchedule().get("workOrderSalesOrders"));
        verify(salesOrderHeaderMapper, never()).selectList(any());
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
    void sameWorkshopUnreferencedWorkstationVersionDoesNotChangeHash() {
        RichFixture fixture = stubRichSalesOrderFixture(
            new AtomicBoolean(false), new AtomicReference<>(new BigDecimal("20")),
            new AtomicReference<>(new BigDecimal("3")));
        stubRoutingSourceFacts(new AtomicReference<>(7001L),
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0)));
        AtomicReference<Long> unrelatedStatus = new AtomicReference<>(1L);
        AtomicReference<Date> unrelatedUpdate =
            new AtomicReference<>(toDateTime(2026, 9, 23, 9, 0));
        when(workstationMapper.selectList(any())).thenAnswer(invocation -> {
            BdWorkstation relevant = workstation(1100L, 20L);
            relevant.setStatus(1L);
            relevant.setUpdateTime(toDateTime(2026, 9, 23, 8, 30));
            BdWorkstation unrelated = workstation(9999L, 20L);
            unrelated.setStatus(unrelatedStatus.get());
            unrelated.setUpdateTime(unrelatedUpdate.get());
            return List.of(unrelated, relevant);
        });

        String first = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        unrelatedStatus.set(0L);
        unrelatedUpdate.set(toDateTime(2026, 9, 23, 9, 1));
        String second = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());

        assertEquals(first, second);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BdWorkstation>> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(workstationMapper, atLeastOnce()).selectList(queries.capture());
        for (Wrapper<BdWorkstation> query : queries.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("workstation_id"), query.getSqlSegment());
            assertFalse(query.getSqlSegment().contains("workshop_id"), query.getSqlSegment());
            assertFalse(queryValues(query).contains(9999L), queryValues(query).toString());
        }
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
    void incompletePublishedPlanCannotUseCalendarAsVerifiedBaseline() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        stubRoutingSourceFacts(new AtomicReference<>(7001L),
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0)));
        when(capacityCalendarMapper.selectList(any())).thenAnswer(invocation -> {
            PrdCapacityCalendar relevant = capacityCalendar(6001L, "WORKSTATION", 1100L, "1");
            relevant.setEffectiveHours(new BigDecimal("2"));
            relevant.setPlannedHours(new BigDecimal("3"));
            return List.of(relevant);
        });
        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observed =
            (List<Map<String, Object>>) snapshot.currentSchedule().get("capacityCalendars");
        assertNull(observed, "a calendar cannot make an incomplete published plan a verified baseline");
        assertTrue(snapshot.currentSchedule().isEmpty());
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
    void routingSourceFactsCarryEngineTimingAndOutsourceFields() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        stubRoutingSourceFacts(new AtomicReference<>(7001L),
            new AtomicReference<>(toDateTime(2026, 9, 23, 8, 0)));
        when(routingStepMapper.selectList(any())).thenAnswer(invocation -> {
            BdRoutingStep step = new BdRoutingStep();
            step.setStepId(7101L);
            step.setRoutingId(7001L);
            step.setSequenceNum(1);
            step.setWorkstationId(1100L);
            step.setStandardHours(new BigDecimal("1.250"));
            step.setSetupHours(new BigDecimal("0.625"));
            step.setIsOutsource(1);
            step.setWaitTime(new BigDecimal("9.5"));
            step.setTransferTime(new BigDecimal("2.25"));
            return List.of(step);
        });
        var route = assembler.assemble(fixture.ref(), 14, fixture.actor())
            .routingByMaterial().get(811L).get(0);
        assertEquals(7001L, route.routingId());
        assertEquals(new BigDecimal("0.625"), route.setupHours());
        assertEquals(1, route.isOutsource());
        assertEquals(new BigDecimal("9.5"), route.waitTime());
        assertEquals(new BigDecimal("2.25"), route.transferTime());
    }

    @Test
    void routingFallbackFactsCarryEngineTimingAndOutsourceFields() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenAnswer(invocation -> {
                var input = scheduleInput(List.of(workOrder(11L, 811L, 701L, "RELEASED"),
                    workOrder(12L, 812L, 702L, "IN_PROGRESS")), false);
                var original = input.getRoutingProvider();
                input.setRoutingProvider(materialId -> {
                    List<StepInfo> steps = original.apply(materialId);
                    if (materialId == 811L) {
                        var step = steps.get(0);
                        step.setSetupHours(new BigDecimal("0.625"));
                        step.setIsOutsource(1);
                        step.setWaitTime(new BigDecimal("9.5"));
                        step.setTransferTime(new BigDecimal("2.25"));
                    }
                    return steps;
                });
                return input;
            });
        var route = assembler.assemble(fixture.ref(), 14, fixture.actor())
            .routingByMaterial().get(811L).get(0);
        assertNull(route.routingId());
        assertEquals(new BigDecimal("0.625"), route.setupHours());
        assertEquals(1, route.isOutsource());
        assertEquals(new BigDecimal("9.5"), route.waitTime());
        assertEquals(new BigDecimal("2.25"), route.transferTime());
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
            PrdScheduleTask remainingScope = scheduleTask(4001L, 5002L, 11L, 1, "1.000", TODAY);
            return List.of(remainingScope, task);
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
    void newerDraftOrLegacyPublishedPlanCannotReplaceAttributablePublishedBaseline() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        PrdSchedulePlan trusted = schedulePlan(5002L, "APS-5002", "PUBLISHED", TODAY, TODAY.plusDays(3));
        trusted.setCapacityCalcVersion(2);
        trusted.setUpdateTime(toDateTime(2026, 9, 23, 8, 0));
        PrdSchedulePlan draft = schedulePlan(5003L, "APS-5003", "COMPLETED", TODAY, TODAY.plusDays(4));
        draft.setCapacityCalcVersion(2);
        draft.setUpdateTime(toDateTime(2026, 9, 23, 9, 0));
        PrdSchedulePlan legacy = schedulePlan(5004L, "APS-5004", "PUBLISHED", TODAY, TODAY.plusDays(5));
        legacy.setCapacityCalcVersion(0);
        legacy.setUpdateTime(toDateTime(2026, 9, 23, 10, 0));
        when(schedulePlanMapper.selectBatchIds(anyCollection())).thenReturn(List.of(trusted, draft, legacy));
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(
            scheduleTask(4002L, 5002L, 11L, 1, "2.000", TODAY),
            scheduleTask(4005L, 5002L, 12L, 1, "1.000", TODAY),
            scheduleTask(4003L, 5003L, 11L, 1, "2.000", TODAY),
            scheduleTask(4004L, 5004L, 11L, 1, "2.000", TODAY)));

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());

        assertEquals("5002", snapshot.currentSchedule().get("planId"));
        assertEquals(2, snapshot.currentSchedule().get("capacityCalcVersion"));
        assertEquals("PUBLISHED", snapshot.currentSchedule().get("status"));
    }
    @Test
    void scheduleProvenanceReadsUseOneIndependentErpRepeatableReadSnapshot() throws Exception {
        DS source = DeliveryContextAssembler.class.getAnnotation(DS.class);
        assertNotNull(source);
        assertEquals("erp", source.value());
        for (Method method : List.of(
            DeliveryContextAssembler.class.getMethod("assemble", DecisionBusinessRef.class, int.class, DecisionActor.class),
            DeliveryContextAssembler.class.getMethod("computeFreshnessHash", DecisionBusinessRef.class, DecisionActor.class))) {
            Transactional boundary = method.getAnnotation(Transactional.class);
            assertNotNull(boundary, method.getName() + " must pin all ERP SELECTs to one transaction");
            assertTrue(boundary.readOnly());
            assertEquals(Isolation.REPEATABLE_READ, boundary.isolation());
            assertEquals(Propagation.REQUIRES_NEW, boundary.propagation());
        }
    }

    @Test
    void automaticBaselineRejectsPublishedPlanMissingOneSalesOrderWorkOrder() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        PrdSchedulePlan partial = schedulePlan(5002L, "APS-5002", "PUBLISHED", TODAY, TODAY.plusDays(2));
        when(schedulePlanMapper.selectBatchIds(anyCollection())).thenReturn(List.of(partial));
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of(
            scheduleTask(4002L, 5002L, 11L, 1, "2.00", TODAY)));

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertEquals(List.of(11L, 12L), snapshot.workOrders().stream()
            .map(DeliveryContextSnapshot.WorkOrderFact::id).sorted().toList());
        assertTrue(snapshot.currentSchedule().isEmpty(),
            "a one-work-order plan cannot be the saved baseline for a two-work-order sales scope");
    }

    @Test
    void ownedBookingAndAllPlanCalendarLedgerEnterSnapshotAndFreshnessHash() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        PrdWorkOrder linked = workOrder(11L, 811L, 701L, "RELEASED");
        linked.setSourceId(100L);
        linked.setWorkstationId(1100L);
        PrdWorkOrder peer = workOrder(12L, 812L, 702L, "IN_PROGRESS");
        peer.setSourceLineId(1002L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(linked, peer));        PrdSchedulePlan plan = schedulePlan(5002L, "APS-5002", "PUBLISHED", TODAY, TODAY.plusDays(5));
        AtomicReference<String> otherPlanStatus = new AtomicReference<>("PUBLISHED");
        when(schedulePlanMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            if (ids.contains(5000L)) return List.of(schedulePlan(5000L, "APS-5000", otherPlanStatus.get(), TODAY, TODAY.plusDays(5)));
            return List.of(plan);
        });
        PrdScheduleTask task = scheduleTask(4002L, 5002L, 11L, 1, "2.00", TODAY);
        task.setResourceId(1100L);
        task.setEndTime(java.sql.Date.valueOf(TODAY));
        task.setDurationDays(1);
        PrdScheduleTask outsourced = scheduleTask(4007L, 5002L, 12L, 1, "1.00", TODAY);
        outsourced.setResourceType("OUTSOURCE");
        outsourced.setResourceId(null);
        outsourced.setEndTime(java.sql.Date.valueOf(TODAY));
        AtomicBoolean missingUnrelatedPlanTask = new AtomicBoolean(false);
        PrdScheduleTask missingTask = scheduleTask(4010L, 5002L, 13L, 1, "1.00", TODAY);
        missingTask.setResourceId(1100L);
        missingTask.setEndTime(java.sql.Date.valueOf(TODAY));
        PrdScheduleTask unshownTask = scheduleTask(4003L, 5002L, 13L, 1, "1.50", TODAY);
        unshownTask.setResourceId(1100L);
        unshownTask.setEndTime(java.sql.Date.valueOf(TODAY));
        AtomicBoolean includeUnshownTask = new AtomicBoolean(false);
        when(scheduleTaskMapper.selectList(any())).thenAnswer(invocation -> {
            String sql = ((Wrapper<?>) invocation.getArgument(0)).getSqlSegment().toLowerCase();
            if (missingUnrelatedPlanTask.get() && sql.contains("plan_id")) return List.of(task, outsourced, missingTask);
            if (includeUnshownTask.get() && sql.contains("plan_id")) return List.of(task, outsourced, unshownTask);
            return List.of(task, outsourced);
        });
        PrdScheduleTask otherTask = scheduleTask(4099L, 5000L, 99L, 1, "1.00", TODAY);
        otherTask.setResourceId(1100L);
        otherTask.setEndTime(java.sql.Date.valueOf(TODAY));
        when(scheduleTaskMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            return ids.contains(4099L) ? List.of(otherTask) : List.of();
        });
        when(scheduleTaskMapper.selectById(4003L)).thenReturn(unshownTask);
        PrdCapacityCalendar calendar = capacityCalendar(6001L, "WORKSTATION", 1100L, "4");
        calendar.setEffectiveHours(new BigDecimal("8"));
        calendar.setPlannedHours(new BigDecimal("4"));
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of(calendar));
        AtomicBoolean includeEarlierVerifiedDay = new AtomicBoolean(false);
        PrdCapacityCalendar earlierCalendar = capacityCalendar(6002L, "WORKSTATION", 1100L, "1");
        earlierCalendar.setCalendarDate(java.sql.Date.valueOf(TODAY.minusDays(1)));
        earlierCalendar.setEffectiveHours(new BigDecimal("8"));
        earlierCalendar.setPlannedHours(new BigDecimal("1"));
        when(capacityCalendarMapper.selectBatchIds(anyCollection())).thenAnswer(invocation ->
            includeEarlierVerifiedDay.get() ? List.of(earlierCalendar) : List.of());
        PrdScheduleTaskDayLoad load = new PrdScheduleTaskDayLoad();
        load.setLoadId(7001L); load.setTenantId("000000"); load.setPlanId(5002L);
        load.setTaskId(4002L); load.setResourceType("WORKSTATION"); load.setResourceId(1100L);
        load.setLoadDate(java.sql.Date.valueOf(TODAY)); load.setPlannedHours(new BigDecimal("2.50"));
        PrdScheduleTaskDayLoad earlierLoad = new PrdScheduleTaskDayLoad();
        earlierLoad.setLoadId(7002L); earlierLoad.setTenantId("000000"); earlierLoad.setPlanId(5002L);
        earlierLoad.setTaskId(4002L); earlierLoad.setResourceType("WORKSTATION"); earlierLoad.setResourceId(1100L);
        earlierLoad.setLoadDate(java.sql.Date.valueOf(TODAY.minusDays(1)));
        earlierLoad.setPlannedHours(new BigDecimal("1.00"));
        AtomicReference<BigDecimal> otherSourceHours = new AtomicReference<>(new BigDecimal("1.50"));
        PrdScheduleTaskDayLoad otherLoad = new PrdScheduleTaskDayLoad();
        otherLoad.setLoadId(7003L); otherLoad.setTenantId("000000"); otherLoad.setPlanId(5000L);
        otherLoad.setTaskId(4099L); otherLoad.setResourceType("WORKSTATION"); otherLoad.setResourceId(1100L);
        otherLoad.setLoadDate(java.sql.Date.valueOf(TODAY));
        PrdScheduleTaskDayLoad unshownLoad = new PrdScheduleTaskDayLoad();
        unshownLoad.setLoadId(7004L); unshownLoad.setTenantId("000000"); unshownLoad.setPlanId(5002L);
        unshownLoad.setTaskId(4003L); unshownLoad.setResourceType("WORKSTATION");
        unshownLoad.setResourceId(1100L); unshownLoad.setLoadDate(java.sql.Date.valueOf(TODAY));
        unshownLoad.setPlannedHours(new BigDecimal("1.00"));
        when(taskDayLoadMapper.selectList(any())).thenAnswer(invocation -> {
            if (((Wrapper<?>) invocation.getArgument(0)).getSqlSegment().contains("task_id")) {
                otherLoad.setPlannedHours(otherSourceHours.get());
                return List.of(otherLoad);
            }
            if (includeUnshownTask.get()) return List.of(load, unshownLoad);
            return includeEarlierVerifiedDay.get() ? List.of(load, earlierLoad) : List.of(load);
        });
        AtomicReference<BigDecimal> otherPlanHours = new AtomicReference<>(new BigDecimal("1.50"));
        AtomicReference<Long> otherBookingId = new AtomicReference<>(8002L);
        AtomicBoolean wrongOtherBookingDate = new AtomicBoolean(false);
        AtomicBoolean invalidOtherTask = new AtomicBoolean(false);
        AtomicBoolean unseenOwnerTask = new AtomicBoolean(false);
        AtomicBoolean unseenOwnerCalendar = new AtomicBoolean(false);
        when(capacityBookingMapper.selectList(any())).thenAnswer(invocation -> {
            PrdScheduleCapacityBooking owned = new PrdScheduleCapacityBooking();
            owned.setBookingId(8001L); owned.setTenantId("000000"); owned.setPlanId(5002L);
            owned.setTaskId(4002L); owned.setCalendarId(6001L);
            owned.setLoadDate(java.sql.Date.valueOf(TODAY)); owned.setBookedHours(new BigDecimal("2.50"));
            PrdScheduleCapacityBooking other = new PrdScheduleCapacityBooking();
            other.setBookingId(otherBookingId.get()); other.setTenantId("000000"); other.setPlanId(5000L);
            other.setTaskId(invalidOtherTask.get() ? 4999L : 4099L); other.setCalendarId(6001L);
            other.setLoadDate(java.sql.Date.valueOf(wrongOtherBookingDate.get() ? TODAY.plusDays(1) : TODAY));
            other.setBookedHours(otherPlanHours.get());
            PrdScheduleCapacityBooking unknownTask = new PrdScheduleCapacityBooking();
            unknownTask.setBookingId(8004L); unknownTask.setTenantId("000000"); unknownTask.setPlanId(5002L);
            unknownTask.setTaskId(9999L); unknownTask.setCalendarId(6001L);
            unknownTask.setLoadDate(java.sql.Date.valueOf(TODAY));
            unknownTask.setBookedHours(new BigDecimal("0.25"));
            String sql = ((Wrapper<?>) invocation.getArgument(0)).getSqlSegment().toLowerCase();
            PrdScheduleCapacityBooking earlier = new PrdScheduleCapacityBooking();
            earlier.setBookingId(8005L); earlier.setTenantId("000000"); earlier.setPlanId(5002L);
            earlier.setTaskId(4002L); earlier.setCalendarId(6002L);
            earlier.setLoadDate(java.sql.Date.valueOf(TODAY.minusDays(1)));
            earlier.setBookedHours(new BigDecimal("1.00"));
            PrdScheduleCapacityBooking unshown = new PrdScheduleCapacityBooking();
            unshown.setBookingId(8006L); unshown.setTenantId("000000"); unshown.setPlanId(5002L);
            unshown.setTaskId(4003L); unshown.setCalendarId(6001L);
            unshown.setLoadDate(java.sql.Date.valueOf(TODAY));
            unshown.setBookedHours(new BigDecimal("1.00"));
            if (sql.contains("task_id")) return List.of(other);
            if (sql.contains("plan_id")) {
                if (includeUnshownTask.get()) return List.of(owned, unshown);
                if (includeEarlierVerifiedDay.get()) return List.of(owned, earlier);
                if (unseenOwnerCalendar.get()) {
                    PrdScheduleCapacityBooking hidden = new PrdScheduleCapacityBooking();
                    hidden.setBookingId(8003L); hidden.setTenantId("000000"); hidden.setPlanId(5002L);
                    hidden.setTaskId(4002L); hidden.setCalendarId(9999L);
                    hidden.setLoadDate(java.sql.Date.valueOf(TODAY));
                    hidden.setBookedHours(new BigDecimal("0.25"));
                    return List.of(owned, hidden);
                }
                return unseenOwnerTask.get() ? List.of(owned, unknownTask) : List.of(owned);
            }
            if (includeUnshownTask.get()) return List.of(owned, other, unshown);
            if (includeEarlierVerifiedDay.get()) return List.of(owned, earlier, other);
            return unseenOwnerTask.get() ? List.of(owned, other, unknownTask) : List.of(owned, other);
        });

        DeliveryContextSnapshot snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        Map<String, Object> schedule = snapshot.currentSchedule();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> owned = (List<Map<String, Object>>) schedule.get("capacityBookings");
        assertEquals(List.of("8001"), owned.stream().map(row -> row.get("bookingId")).toList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dates = (List<Map<String, Object>>) schedule.get("taskDayLoads");
        assertEquals(List.of("7001"), dates.stream().map(row -> row.get("loadId")).toList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> observedCalendars = (List<Map<String, Object>>) schedule.get("capacityCalendars");
        assertEquals("4.00", new BigDecimal((String) observedCalendars.get(0).get("ledgerHours"))
            .setScale(2).toPlainString());
        var observed = com.nexus.common.ai.decision.util.SavedScheduleCapacity.measure(schedule);
        assertNotNull(observed, "only the owned booking plus a reconciled shared calendar proves utilization");
        assertEquals(0, new BigDecimal("50.0").compareTo(observed.maxUtilization()));
        verify(taskDayLoadMapper, atLeastOnce()).selectList(argThat(query ->
            query.getSqlSegment().toUpperCase().contains("LIMIT 100001")));
        verify(capacityBookingMapper, atLeastOnce()).selectList(argThat(query ->
            query.getSqlSegment().toUpperCase().contains("LIMIT 100001")));
        otherPlanHours.set(new BigDecimal("1.75"));
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a changed ledger without a matching other-plan day-load must fail closed before approval");
        otherPlanHours.set(new BigDecimal("1.50"));
        otherTask.setProcessHours(new BigDecimal("2.00"));
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "the other plan task hours must agree with its actual day-load and all bookings");
        otherTask.setProcessHours(new BigDecimal("1.00"));
        otherSourceHours.set(new BigDecimal("2.00"));
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "the other plan day-load cannot be larger than its complete booking sum");
        otherSourceHours.set(new BigDecimal("1.50"));
        otherTask.setProcessHours(new BigDecimal("1.10"));
        otherTask.setSetupHours(new BigDecimal("0.40"));
        assertNotEquals(snapshot.snapshotHash(), assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "the other plan task composition must enter approval freshness even when total hours do not change");
        otherTask.setProcessHours(new BigDecimal("1.00"));
        otherTask.setSetupHours(new BigDecimal("0.500"));
        otherBookingId.set(8012L);
        assertNotEquals(snapshot.snapshotHash(), assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a replaced other-plan booking must change freshness even if booked hours remain unchanged");
        otherBookingId.set(8002L);
        otherPlanStatus.set("COMPLETED");
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a draft plan cannot contribute to the all-plan ledger");
        otherPlanStatus.set("PUBLISHED");
        wrongOtherBookingDate.set(true);
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "another plan booking must agree with its sourced calendar date");
        wrongOtherBookingDate.set(false);
        invalidOtherTask.set(true);
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a booking must belong to an actual task of the other published plan");
        invalidOtherTask.set(false);
        unseenOwnerTask.set(true);
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a booking for an unverified task in this plan cannot be hidden in the shared ledger");
        unseenOwnerTask.set(false);
        missingUnrelatedPlanTask.set(true);
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a plan task lacking both day-load and booking must not disappear outside the decision work-order scope");
        missingUnrelatedPlanTask.set(false);
        unseenOwnerCalendar.set(true);
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "a booking for this task outside sourced calendars must never disappear from the hash");
        unseenOwnerCalendar.set(false);
        includeUnshownTask.set(true);
        calendar.setPlannedHours(new BigDecimal("5"));
        assertThrows(ServiceException.class, () -> assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "an undisplayed task must still match its own process plus setup total");
        unshownTask.setProcessHours(new BigDecimal("0.50"));
        assertNotNull(com.nexus.common.ai.decision.util.SavedScheduleCapacity.measure(
            assembler.assemble(fixture.ref(), 14, fixture.actor()).currentSchedule()),
            "a valid task from the same plan may be outside the decision's displayed work orders");
        includeUnshownTask.set(false);
        calendar.setPlannedHours(new BigDecimal("4"));
        includeEarlierVerifiedDay.set(true);
        plan.setStartDate(java.sql.Date.valueOf(TODAY.minusDays(1)));
        task.setStartTime(java.sql.Date.valueOf(TODAY.minusDays(1)));
        task.setProcessHours(new BigDecimal("3.00"));
        var savedAcrossBoundary = assembler.assemble(fixture.ref(), 14, fixture.actor()).currentSchedule();
        assertEquals(2, ((List<?>) savedAcrossBoundary.get("taskDayLoads")).size());
        assertEquals(2, ((List<?>) savedAcrossBoundary.get("capacityBookings")).size());
        assertNotNull(com.nexus.common.ai.decision.util.SavedScheduleCapacity.measure(savedAcrossBoundary),
            "an older verified calendar may be outside today's APS input window without invalidating a published plan");
        String earlierHash = assembler.computeFreshnessHash(fixture.ref(), fixture.actor());
        earlierCalendar.setShiftId(700L);
        assertNotEquals(earlierHash, assembler.computeFreshnessHash(fixture.ref(), fixture.actor()),
            "an occupied historical calendar changing shift must stale the approval");
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
        assertEquals("000000", schedule.get("tenantId"));
        assertEquals(2, schedule.get("capacityCalcVersion"));
        assertTrue(schedule.get("taskDayLoads") instanceof List<?>, "saved tasks must carry their own daily load evidence");
        assertTrue(schedule.get("capacityBookings") instanceof List<?>, "saved tasks must carry their own capacity bookings");
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

    private BdWorkstation workstation(long workstationId, long workshopId) {
        BdWorkstation workstation = new BdWorkstation();
        workstation.setWorkstationId(workstationId);
        workstation.setWorkshopId(workshopId);
        return workstation;
    }

    private PrdCapacityCalendar capacityCalendar(long id, String objectType, long objectId, String remaining) {
        PrdCapacityCalendar calendar = new PrdCapacityCalendar();
        calendar.setCalendarId(id);
        calendar.setTenantId("000000");
        calendar.setCalendarDate(java.sql.Date.valueOf(TODAY));
        calendar.setObjectType(objectType);
        calendar.setObjectId(objectId);
        calendar.setRemainingHours(new BigDecimal(remaining));
        calendar.setStatus("ACTIVE");
        return calendar;
    }

    @Test
    void salesOrderWithoutScheduleStillPublishesTypedWorkOrderSalesLinks() {
        SoSalesOrderHeader order = salesOrder(100L, "SO-100");
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdProductionPlan plan = productionPlan(201L, 101L, 301L);
        PrdWorkOrder wo = workOrder(11L, 811L, 201L, "RELEASED");
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(line));
        when(productionPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(wo), false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null), 14,
            actor("ai:decision:run", "so:orderheader:query"));

        assertTrue(snapshot.currentSchedule().isEmpty());
        assertEquals(List.of(new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 100L)),
            snapshot.workOrderSalesLinks());
    }

    @Test
    void workOrderWithoutScheduleStillPublishesTypedWorkOrderSalesLinks() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        when(workOrderMapper.selectById(11L)).thenReturn(wo);

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), 14,
            actor("ai:decision:run", "prd:work:query"));

        assertTrue(snapshot.currentSchedule().isEmpty());
        assertEquals(List.of(new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 100L)),
            snapshot.workOrderSalesLinks());
    }

    @Test
    void shortageHorizonWithoutScheduleStillPublishesTypedWorkOrderSalesLinks() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo));

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null), 14,
            actor("ai:decision:run", "prd:shortage:query"));

        assertTrue(snapshot.currentSchedule().isEmpty());
        assertEquals(List.of(new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 100L)),
            snapshot.workOrderSalesLinks());
    }

    @Test
    void workOrderWithoutConfirmedSalesHeaderDoesNotExposeLinkedSalesOrderOrImpactCount() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        when(workOrderMapper.selectById(11L)).thenReturn(wo);
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(null);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(301L, 201L)));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(
            requirementDetail(901L, 301L, 501L, "1", "0",
                LocalDate.now(ZoneId.of("Asia/Shanghai")))));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(material(501L, "0")));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10001L,501L,"0","0")));
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null);
        DecisionActor actor = actor("ai:decision:run", "prd:work:query");

        DeliveryContextSnapshot snapshot = assembler.assemble(ref, 14, actor);
        assertTrue(snapshot.salesOrders().isEmpty());
        assertTrue(snapshot.workOrderSalesLinks().isEmpty(), "未验证的销售订单头不可进入 typed 关联");
        var result = new ErpDecisionShortageSimulation(new PrdShortageScenarioService())
            .simulate(snapshot, 14, null);
        assertTrue(result.hasRisk());
        assertEquals(List.of(), result.items().get(0).affectedSalesOrderIds());
        assertEquals(0, result.kpis().impactedOrderCount());

        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(salesOrder(100L, "SO-100"));
        assertEquals(List.of(new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 100L)),
            assembler.assemble(ref, 14, actor).workOrderSalesLinks());
    }

    @Test
    void schedulePlanWithMismatchedSalesHeaderDoesNotPublishUnconfirmedTypedLink() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(salesOrder(999L, "SO-999"));
        when(schedulePlanMapper.selectById(9001L))
            .thenReturn(schedulePlan(9001L, "APS-9001", "PUBLISHED", TODAY, TODAY.plusDays(7)));
        when(scheduleTaskMapper.selectList(any()))
            .thenReturn(List.of(scheduleTask(501L, 9001L, 11L, 1, "1.250", TODAY)));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(wo));

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null), 14,
            actor("ai:decision:run", "prd:schedule:query"));

        assertTrue(snapshot.salesOrders().isEmpty());
        assertTrue(snapshot.workOrderSalesLinks().isEmpty());
        assertEquals(List.of(), snapshot.currentSchedule().get("workOrderSalesOrderIds"));
    }
    @Test
    void missingMaterialMasterLeavesSafetyStockUnknownDespitePurchaseArrival() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        stubSingleMaterialDemandAndArrival(wo);

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), 14,
            actor("ai:decision:run", "prd:work:query"));

        DeliveryContextSnapshot.StockFact stock = snapshot.stocks().stream()
            .filter(fact -> fact.materialId().equals(501L)).findFirst().orElseThrow();
        assertNull(stock.stockQty(), "无库存行不能认证库存0");
        assertNull(stock.reservedQty(), "无库存来源不能认证预留0");
        assertNull(stock.safetyStock(), "无 BdMaterial 时不得伪装成安全库存 0");
        assertTrue(snapshot.warnings().contains("MISSING_MATERIAL_MASTER:501"));
        assertEquals(new BigDecimal("2"), snapshot.arrivals().stream()
            .filter(fact -> fact.materialId().equals(501L)).findFirst().orElseThrow().remainingQty());
        // 缺失事实由专属证据拒绝类型表达，不再用已废弃的通用消息字符串判断。
        var failure = assertThrows(com.nexus.common.ai.decision.exception.DecisionEvidenceException.class,
            () -> new ErpDecisionShortageSimulation(new PrdShortageScenarioService())
                .simulate(snapshot, 14, null));
        assertEquals(com.nexus.common.ai.decision.exception.DecisionEvidenceException.Reason.SHORTAGE_SIMULATION_INCOMPLETE,
            failure.reason());
    }

    @Test
    void knownMaterialWithNullSafetySettingAndMissingInventoryRemainsUnknown() {
        PrdWorkOrder wo = stubLinkedWorkOrderWithoutSchedule();
        stubSingleMaterialDemandAndArrival(wo);
        BdMaterial known = new BdMaterial();
        known.setMaterialId(501L);
        known.setSafetyStock(null);
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(known));

        DeliveryContextSnapshot snapshot = assembler.assemble(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null), 14,
            actor("ai:decision:run", "prd:work:query"));

        DeliveryContextSnapshot.StockFact stock = snapshot.stocks().stream()
            .filter(fact -> fact.materialId().equals(501L)).findFirst().orElseThrow();
        assertNull(stock.stockQty());
        assertNull(stock.reservedQty());
        assertNull(stock.safetyStock());
        assertFalse(snapshot.warnings().contains("MISSING_MATERIAL_MASTER:501"));
        assertTrue(snapshot.warnings().contains("UNKNOWN_STOCK_SOURCE:501"));
        var failure = assertThrows(com.nexus.common.ai.decision.exception.DecisionEvidenceException.class,
            () -> new ErpDecisionShortageSimulation(new PrdShortageScenarioService()).simulate(snapshot, 14, null));
        assertEquals(com.nexus.common.ai.decision.exception.DecisionEvidenceException.Reason.SHORTAGE_SIMULATION_INCOMPLETE,
            failure.reason());
    }


    @Test
    void missingStockRecordsRemainUnknownEvenWithKnownMaterialAndArrival() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(
            requirementDetail(901L,801L,501L,"10","2",LocalDate.now(ZoneId.of("Asia/Shanghai")))));
        when(stockMapper.selectList(any())).thenReturn(List.of());
        var snapshot=assembler.assemble(f.ref(),14,f.actor());
        assertTrue(snapshot.stocks().stream().allMatch(x -> x.stockQty()==null && x.reservedQty()==null));
        assertTrue(snapshot.warnings().contains("UNKNOWN_STOCK_SOURCE:501"));
        assertThrows(ServiceException.class,()->new ErpDecisionShortageSimulation(new PrdShortageScenarioService()).simulate(snapshot,14,null));
    }

    @Test
    void explicitZeroStockSourceRemainsKnownZero() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10001L,501L,"0","0"),stock(10003L,502L,"0","0")));
        var snapshot=assembler.assemble(f.ref(),14,f.actor());
        var zeroFacts = snapshot.stocks().stream()
            .filter(x -> x.materialId() == 501L || x.materialId() == 502L).toList();
        assertEquals(2, zeroFacts.size(), "两个显式零来源物料都必须保留事实");
        assertEquals(Set.of(501L, 502L), new LinkedHashSet<>(zeroFacts.stream().map(DeliveryContextSnapshot.StockFact::materialId).toList()));
        assertTrue(zeroFacts.stream().allMatch(x -> x.stockQty() != null && x.reservedQty() != null
            && BigDecimal.ZERO.compareTo(x.stockQty()) == 0 && BigDecimal.ZERO.compareTo(x.reservedQty()) == 0));
    }

    @Test
    void nullQuantityCannotBecomeZeroOrPartialMaterialSum() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        var bad=stock(10001L,501L,"0","0"); bad.setStockQuantity(null);
        when(stockMapper.selectList(any())).thenReturn(List.of(bad,stock(10002L,501L,"5","1"),stock(10003L,502L,"8","0")));
        var snapshot=assembler.assemble(f.ref(),14,f.actor());
        assertNull(snapshot.stocks().stream().filter(x->x.materialId()==501L).findFirst().orElseThrow().stockQty());
    }

    @Test
    void unknownReservationOrFrozenStateCannotBecomeAvailableSupply() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        var bad=stock(10001L,501L,"20","3"); bad.setReservedQuantity(null);
        when(stockMapper.selectList(any())).thenReturn(List.of(bad,stock(10003L,502L,"8","0")));
        assertNull(assembler.assemble(f.ref(),14,f.actor()).stocks().stream().filter(x->x.materialId()==501L).findFirst().orElseThrow().reservedQty());
        bad.setReservedQuantity(new BigDecimal("3")); bad.setFrozenFlag(null);
        assertNull(assembler.assemble(f.ref(),14,f.actor()).stocks().stream().filter(x->x.materialId()==501L).findFirst().orElseThrow().stockQty());
    }

    @Test
    void frozenStockBlocksEntireQuantityWithoutDoubleCountingReservation() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        var frozen=stock(10001L,501L,"20","3"); frozen.setFrozenFlag(1L);
        when(stockMapper.selectList(any())).thenReturn(List.of(frozen,stock(10002L,501L,"5","1"),stock(10003L,502L,"8","0")));
        var fact=assembler.assemble(f.ref(),14,f.actor()).stocks().stream().filter(x->x.materialId()==501L).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("5").compareTo(fact.stockQty()), "推演供给只含非冻结来源");
        assertEquals(0,BigDecimal.ONE.compareTo(fact.reservedQty()), "不能把冻结数量冒充预留或重复扣减");
        assertEquals(0,new BigDecimal("4").compareTo(fact.stockQty().subtract(fact.reservedQty())));
    }

    @Test
    void mismatchedBaseUnitCannotBeAggregatedAsSameMaterialStock() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        var bad=stock(10001L,501L,"20","3"); bad.setBaseUomId("999");
        when(stockMapper.selectList(any())).thenReturn(List.of(bad,stock(10002L,501L,"5","1"),stock(10003L,502L,"8","0")));
        assertNull(assembler.assemble(f.ref(),14,f.actor()).stocks().stream().filter(x->x.materialId()==501L).findFirst().orElseThrow().stockQty());
    }

    @Test
    void rawUnknownVersusZeroAndFrozenUnitLocationChangesInvalidateFreshnessHash() {
        RichFixture f=stubRichSalesOrderFixture(new AtomicBoolean(false),new AtomicReference<>(new BigDecimal("20")),new AtomicReference<>(new BigDecimal("3")));
        var changed = stock(10001L, 501L, "0", "0");
        var changedMaterial = material(501L, "0");
        when(stockMapper.selectList(any())).thenReturn(List.of(changed, stock(10003L, 502L, "8", "0")));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(changedMaterial, material(502L, "1")));
        String first = assembler.computeFreshnessHash(f.ref(), f.actor());
        changed.setStockQuantity(null);
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setStockQuantity(BigDecimal.ZERO);
        assertEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setReservedQuantity(null);
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()), "原始预留 NULL 与零必须区分");
        changed.setReservedQuantity(BigDecimal.ZERO);
        assertEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changedMaterial.setSafetyStock(null);
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()), "原始安全库存 NULL 与零必须区分");
        changedMaterial.setSafetyStock(BigDecimal.ZERO);
        assertEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setFrozenFlag(1L);
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setFrozenFlag(0L);
        assertEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setBaseUomId("999");
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setBaseUomId("56");
        assertEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
        changed.setLocationId(999L);
        assertNotEquals(first, assembler.computeFreshnessHash(f.ref(), f.actor()));
    }

    private void stubSingleMaterialDemandAndArrival(PrdWorkOrder workOrder) {
        when(workOrderMapper.selectById(11L)).thenReturn(workOrder);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(301L, 201L)));
        // 装配器已在 fixedBusinessDate 固定到 TODAY；夹具日期必须使用同一业务日，
        // 否则自然日推进后到货会落在半开分析窗口之外，导致历史回归随日历失效。
        LocalDate businessDay = TODAY;
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(
            requirementDetail(901L, 301L, 501L, "1", "0", businessDay)));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(
            purchaseLine(2101L, 2001L, 501L, "2", "0")));
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(
            purchaseOrder(2001L, 301L, "APPROVED", businessDay)));
    }
    @Test
    void confirmedLegacySalesLinkChangeWithinSameUpdateSecondChangesApprovalFingerprint() {
        PrdWorkOrder changing = stubStableLegacySalesSources();
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);
        DecisionActor actor = actor("ai:decision:run", "prd:shortage:query");
        Date unchangedUpdate = changing.getUpdateTime();

        DeliveryContextSnapshot first = assembler.assemble(ref, 14, actor);
        changing.setSourceId(200L);
        DeliveryContextSnapshot second = assembler.assemble(ref, 14, actor);

        assertEquals(List.of(100L, 200L), first.salesOrders().stream()
            .map(DeliveryContextSnapshot.OrderFact::id).toList());
        assertEquals(first.salesOrders(), second.salesOrders(), "两个 SO 的可见头事实保持不变");
        assertEquals(unchangedUpdate, changing.getUpdateTime());
        assertTrue(first.workOrderSalesLinks().contains(
            new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 100L)));
        assertTrue(second.workOrderSalesLinks().contains(
            new DeliveryContextSnapshot.WorkOrderSalesLinkFact(11L, 200L)));
        assertNotEquals(first.snapshotHash(), second.snapshotHash(), "审批指纹必须覆盖已验证 WO→SO 关系");
    }

    @Test
    void eachWorkOrderSourceFieldIsIncludedEvenWhenTypedLinkDoesNotChange() {
        PrdWorkOrder changing = stubStableLegacySalesSources();
        DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);
        DecisionActor actor = actor("ai:decision:run", "prd:shortage:query");
        DeliveryContextSnapshot first = assembler.assemble(ref, 14, actor);
        String baseline = first.snapshotHash();

        changing.setSourceType("CUSTOMER_ORDER");
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor), "sourceType");
        changing.setSourceType("SALES_ORDER");
        changing.setSourceLineId(1001L);
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor), "sourceLineId");
        changing.setSourceLineId(null);
        changing.setSourceNo("SO-100");
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor), "sourceNo");
        changing.setSourceNo(null);
        changing.setPlanId(799L);
        assertNotEquals(baseline, assembler.computeFreshnessHash(ref, actor), "planId");
        assertEquals(first.workOrderSalesLinks(), assembler.assemble(ref, 14, actor).workOrderSalesLinks());
    }

    private PrdWorkOrder stubStableLegacySalesSources() {
        PrdWorkOrder changing = workOrder(11L, 811L, 701L, "RELEASED");
        PrdWorkOrder fixedA = workOrder(12L, 812L, 702L, "RELEASED");
        PrdWorkOrder fixedB = workOrder(13L, 813L, 703L, "RELEASED");
        changing.setSourceType("SALES_ORDER");
        fixedA.setSourceType("SALES_ORDER");
        fixedB.setSourceType("SALES_ORDER");
        changing.setSourceId(100L);
        fixedA.setSourceId(100L);
        fixedB.setSourceId(200L);
        Date sameSecond = toDateTime(2026, 9, 24, 10, 0);
        changing.setUpdateTime(sameSecond);
        fixedA.setUpdateTime(sameSecond);
        fixedB.setUpdateTime(sameSecond);
        List<PrdWorkOrder> workOrders = List.of(changing, fixedA, fixedB);
        when(workOrderMapper.selectList(any())).thenReturn(workOrders);
        when(salesOrderHeaderMapper.selectBatchIds(anyCollection()))
            .thenReturn(List.of(salesOrder(100L, "SO-100"), salesOrder(200L, "SO-200")));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(workOrders, false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());
        return changing;
    }
    private PrdWorkOrder stubLinkedWorkOrderWithoutSchedule() {
        SoSalesOrderLine line = salesOrderLine(101L, 100L, 501L);
        PrdProductionPlan plan = productionPlan(201L, 101L, 301L);
        PrdWorkOrder wo = workOrder(11L, 811L, 201L, "RELEASED");
        when(productionPlanMapper.selectBatchIds(anyCollection())).thenReturn(List.of(plan));
        when(salesOrderLineMapper.selectBatchIds(anyCollection())).thenReturn(List.of(line));
        when(salesOrderHeaderMapper.selectById(100L)).thenReturn(salesOrder(100L, "SO-100"));
        when(scheduleInputAssembler.assemble(any(PrdSchedulePlanBo.class), anyCollection()))
            .thenReturn(scheduleInput(List.of(wo), false));
        stubEmptyRelatedFacts();
        when(scheduleTaskMapper.selectList(any())).thenReturn(List.of());
        return wo;
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
        row.setBaseUomId("56"); row.setFrozenFlag(0L); row.setWarehouseId(10L); row.setAreaId(11L); row.setLocationId(12L);
        row.setStockQuantity(new BigDecimal(stock));
        row.setReservedQuantity(new BigDecimal(reserved));
        return row;
    }

    private BdMaterial material(long id, String safetyStock) {
        BdMaterial material = new BdMaterial();
        material.setMaterialId(id);
        material.setBaseUomId(56L);
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
        plan.setTenantId("000000");
        plan.setCapacityCalcVersion(2);
        plan.setStrategy("EDD");
        plan.setStartDate(java.sql.Date.valueOf(startDate));
        plan.setHorizonDays(14);
        plan.setMakespanDate(java.sql.Date.valueOf(makespanDate));
        return plan;
    }

    private PrdScheduleTask scheduleTask(long id, long planId, long woId, int seq,
                                         String processHours, LocalDate day) {
        PrdScheduleTask task = new PrdScheduleTask();
        task.setTenantId("000000");
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


    @Test
    void verifiedMrpUnitIdentitySurvivesActualRequirementProjection() {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        var detail = requirementDetail(9901L, 801L, 501L, "10", "2", TODAY);
        detail.setTenantId("000000"); detail.setUnit("56");
        var master = material(501L, "0");
        master.setTenantId("000000"); master.setStatus(1L); master.setDelFlag("0");
        var unit = new com.nexus.erp.bd.domain.BdUnitOfMeasure();
        unit.setUomId(56L); unit.setTenantId("000000"); unit.setStatus(1L); unit.setDelFlag("0");
        unit.setPrimaryId(56L); unit.setIsBase(1L); unit.setConversionRate(BigDecimal.ONE); unit.setQuantityScale(0);
        unit.setConversionVersion("CONFIRMED_TEST_ROOT"); unit.setConversionVerifiedAt(new java.util.Date());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(801L, 701L)));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(detail));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(master));
        when(evidenceUnitMapper.selectBatchIds(anyCollection())).thenReturn(List.of(unit));
        var snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertEquals(56L, snapshot.requirements().get(0).baseUomId());
        assertEquals(new BigDecimal("8"), snapshot.requirements().get(0).requiredQty());
        assertEquals(9901L, snapshot.requirements().get(0).detailId());
        String hash = snapshot.snapshotHash();
        unit.setStatus(0L);
        var disabled = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(disabled.requirements().get(0).baseUomId());
        assertTrue(disabled.warnings().stream().anyMatch(w -> w.startsWith("UNKNOWN_REQUIREMENT_UNIT:")));
        assertNotEquals(hash, disabled.snapshotHash(), "单位主档失效须改变真实来源版本");
    }

    @Test
    void purchaseSupplyRequiresActualRootUnitAndItsRawUnitChangesFingerprint() throws Exception {
        RichFixture fixture = stubRichSalesOrderFixture(new AtomicBoolean(false),
            new AtomicReference<>(new BigDecimal("20")), new AtomicReference<>(new BigDecimal("3")));
        var detail = requirementDetail(9901L, 801L, 501L, "10", "2", TODAY);
        detail.setTenantId("000000"); detail.setUnit("56");
        var master = material(501L, "0");
        master.setTenantId("000000"); master.setStatus(1L); master.setDelFlag("0");
        var unit = new com.nexus.erp.bd.domain.BdUnitOfMeasure();
        unit.setUomId(56L); unit.setTenantId("000000"); unit.setStatus(1L); unit.setDelFlag("0");
        unit.setPrimaryId(56L); unit.setIsBase(1L); unit.setConversionRate(BigDecimal.ONE); unit.setQuantityScale(0);
        unit.setConversionVersion("CONFIRMED_TEST_ROOT"); unit.setConversionVerifiedAt(new java.util.Date());
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement(801L, 701L)));
        when(requirementDetailMapper.selectList(any())).thenReturn(List.of(detail));
        when(materialMapper.selectBatchIds(anyCollection())).thenReturn(List.of(master));
        when(evidenceUnitMapper.selectBatchIds(anyCollection())).thenReturn(List.of(unit));
        var order = purchaseOrder(4001L, 301L, "APPROVED", TODAY.plusDays(1));
        var arrivalLine = purchaseLine(4002L, 4001L, 501L, "1000", "0");
        arrivalLine.setUnit("56");
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));
        var snapshot = assembler.assemble(fixture.ref(), 14, fixture.actor());
        var arrival = snapshot.arrivals().get(0);
        assertEquals(56L, arrival.getClass().getMethod("baseUomId").invoke(arrival));
        String hash = snapshot.snapshotHash();
        arrivalLine.setUnit("克");
        var changed = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(changed.arrivals().get(0).getClass().getMethod("baseUomId").invoke(changed.arrivals().get(0)));
        assertTrue(changed.warnings().stream().anyMatch(w -> w.startsWith("UNKNOWN_ARRIVAL_UNIT:")));
        assertNotEquals(hash, changed.snapshotHash(), "不能把1000克默认成基础单位1000千克且保留旧指纹");
        arrivalLine.setUnit("56");
        for (Long base : java.util.Arrays.asList(0L, null)) {
            unit.setIsBase(base);
            var invalid = assembler.assemble(fixture.ref(), 14, fixture.actor());
            assertNull(invalid.arrivals().get(0).getClass().getMethod("baseUomId").invoke(invalid.arrivals().get(0)));
        }
        unit.setIsBase(1L);
        var verified = unit.getConversionVerifiedAt();
        unit.setConversionVerifiedAt(java.sql.Date.valueOf("2099-01-01"));
        var future = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(future.arrivals().get(0).getClass().getMethod("baseUomId").invoke(future.arrivals().get(0)));
        unit.setConversionVerifiedAt(verified);
        arrivalLine.setReceivedQuantity(null);
        var unknown = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(unknown.arrivals().get(0).getClass().getMethod("baseUomId").invoke(unknown.arrivals().get(0)));
        assertNotEquals(hash, unknown.snapshotHash(), "收货null不能规范为0后仍保留原资格指纹");
        arrivalLine.setReceivedQuantity(BigDecimal.valueOf(-1));
        var negative = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(negative.arrivals().get(0).getClass().getMethod("baseUomId").invoke(negative.arrivals().get(0)));
        arrivalLine.setOrderQuantity(BigDecimal.ONE); arrivalLine.setReceivedQuantity(BigDecimal.ZERO);
        var integer = assembler.assemble(fixture.ref(), 14, fixture.actor());
        arrivalLine.setOrderQuantity(new BigDecimal("1.5")); arrivalLine.setReceivedQuantity(new BigDecimal("0.5"));
        var fraction = assembler.assemble(fixture.ref(), 14, fixture.actor());
        assertNull(fraction.arrivals().get(0).getClass().getMethod("baseUomId").invoke(fraction.arrivals().get(0)));
        assertNotEquals(integer.snapshotHash(), fraction.snapshotHash(), "净量相同仍须核原订购/收货量精度与指纹");
    }

    private record RichFixture(DecisionBusinessRef ref, DecisionActor actor) {
    }
}
