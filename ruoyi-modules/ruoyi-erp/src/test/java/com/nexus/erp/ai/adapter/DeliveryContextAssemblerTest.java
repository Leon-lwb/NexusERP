package com.nexus.erp.ai.adapter;

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
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
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
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private PrdMaterialRequirementDetailMapper requirementDetailMapper;
    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private PuVendorEvaluationMapper vendorEvaluationMapper;
    @Mock
    private PrdScheduleInputAssembler scheduleInputAssembler;

    @InjectMocks
    private DeliveryContextAssembler assembler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(
            SoSalesOrderHeader.class, SoSalesOrderLine.class, PrdWorkOrder.class,
            PrdSchedulePlan.class, PrdScheduleTask.class, PrdMaterialRequirement.class,
            PrdMaterialRequirementDetail.class, WmMaterialStock.class, BdMaterial.class,
            PuPurchaseOrder.class, PuPurchaseOrderLine.class, PuVendorEvaluation.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
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
        assertTrue(bos.getAllValues().stream().anyMatch(bo -> Integer.valueOf(14).equals(bo.getHorizonDays())));
        assertTrue(bos.getAllValues().stream().anyMatch(bo -> Integer.valueOf(90).equals(bo.getHorizonDays())));
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
        for (Wrapper<PrdWorkOrder> query : queries.getAllValues()) {
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("source_id"), sql);
            assertTrue(sql.contains("source_line_id"), sql);
            assertTrue(sql.contains("source_no"), sql);
            Collection<?> values = queryValues(query);
            assertTrue(values.contains(100L), values.toString());
            assertTrue(values.contains(1002L), values.toString());
            assertTrue(values.contains("SO-100"), values.toString());
        }
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

        when(stockMapper.selectList(any())).thenAnswer(invocation -> {
            WmMaterialStock stock1 = stock(10001L, 501L, stockQty.get().toPlainString(), reservedQty.get().toPlainString());
            stock1.setUpdateTime(toDateTime(2026, 9, 23, 12, 0));
            WmMaterialStock stock2 = stock(10002L, 501L, "5", "1");
            stock2.setUpdateTime(toDateTime(2026, 9, 23, 12, 1));
            WmMaterialStock stock3 = stock(10003L, 502L, "8", "0");
            stock3.setUpdateTime(toDateTime(2026, 9, 23, 12, 2));
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
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDateTime(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant());
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
