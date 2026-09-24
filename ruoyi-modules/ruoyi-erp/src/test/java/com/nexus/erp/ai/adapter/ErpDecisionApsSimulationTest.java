package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ErpDecisionApsSimulationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private final PrdScheduleInputAssembler assembler = mock(PrdScheduleInputAssembler.class);
    private final PrdWorkOrderMapper workOrderMapper = mock(PrdWorkOrderMapper.class);
    private final ErpDecisionShortageSimulation shortage = mock(ErpDecisionShortageSimulation.class);
    private final ErpDecisionApsSimulation simulation =
        new ErpDecisionApsSimulation(assembler, workOrderMapper, shortage);

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisWorkOrderMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            com.nexus.erp.prd.domain.PrdWorkOrder.class);
    }
    private DeliveryContextSnapshot snapshot(Map<String, Object> schedule) {
        return new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"), List.of(),
            List.of(new WorkOrderFact(11L, "WO-11", 101L, BigDecimal.ONE, TODAY.plusDays(1), 1L, 3L)),
            Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), schedule,
            List.of(), "hash", LocalDateTime.of(2026, 9, 24, 10, 0), List.of());
    }

    @Test
    void missingSavedPlanIsInfeasibleWithoutCallingEngineOrMapper() {
        ApsScenarioCompareResult result = simulation.compare(snapshot(Map.of()), List.of("BASELINE_CURRENT"), 7);
        DecisionScenario baseline = result.scenarios().get(0);
        assertFalse(baseline.feasible());
        assertEquals(List.of("NO_SAVED_SCHEDULE"), baseline.infeasibleReasons());
        verifyNoInteractions(workOrderMapper, assembler, shortage);
    }

    @Test
    void savedBaselineUsesLastTaskEndAndDueDateAndDoesNotInventUtilization() {
        Map<String, Object> saved = Map.of("planId", "9007199254740993", "strategy", "CR",
            "makespanDate", "2026-09-29", "tasks", List.of(
                Map.of("woId", "11", "seqNum", 1, "startTime", "2026-09-24T00:00:00",
                    "endTime", "2026-09-26T23:59:59", "processHours", "8"),
                Map.of("woId", "11", "seqNum", 2, "startTime", "2026-09-27T00:00:00",
                    "endTime", "2026-09-29T23:59:59", "processHours", "4")));
        DecisionScenario baseline = simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0);
        assertEquals("CR", baseline.strategy());
        assertEquals("2026-09-29", baseline.kpis().makespanDate());
        assertEquals(1, baseline.kpis().delayedWorkOrderCount());
        assertEquals(4, baseline.kpis().totalTardinessDays());
        assertEquals(4, baseline.kpis().maxTardinessDays());
        assertEquals(0, baseline.kpis().changedWorkOrderCount());
        assertNull(baseline.kpis().maxResourceUtilization());
        assertTrue(simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7).warnings()
            .contains("NO_BASELINE_UTILIZATION"));
        verifyNoInteractions(workOrderMapper, assembler);
    }

    private DeliveryContextSnapshot withMaterialEvidence(Map<String, Object> schedule, boolean demand,
                                                         boolean stock, boolean arrival) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"),
            List.of(), List.of(new WorkOrderFact(11L, "WO-11", 101L, new BigDecimal("16"),
                TODAY, 1L, 3L)), Map.of(),
            demand ? List.of(new DeliveryContextSnapshot.RequirementFact(200L, "RAW", "原料",
                TODAY.plusDays(1), new BigDecimal("10"), 11L, "WO-11")) : List.of(),
            stock ? List.of(new DeliveryContextSnapshot.StockFact(200L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO)) : List.of(),
            arrival ? List.of(new DeliveryContextSnapshot.ArrivalFact(200L, 5002L, TODAY.plusDays(4),
                    new BigDecimal("10"), 6L),
                new DeliveryContextSnapshot.ArrivalFact(200L, 5001L, TODAY.plusDays(3),
                    new BigDecimal("10"), 6L)) : List.of(),
            Map.of(), List.of(), schedule, List.of(), "hash", LocalDateTime.of(2026, 9, 24, 10, 0), List.of());
    }

    private com.nexus.erp.prd.domain.PrdWorkOrder liveWo(long id, String status) {
        com.nexus.erp.prd.domain.PrdWorkOrder wo = new com.nexus.erp.prd.domain.PrdWorkOrder();
        wo.setWoId(id);
        wo.setWoNo("WO-" + id);
        wo.setMaterialId(101L);
        wo.setPlannedQty(new BigDecimal("16"));
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setPlanEndDate(java.util.Date.from(TODAY.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
        wo.setPriority(1L);
        wo.setWorkshopId(3L);
        wo.setWoStatus(status);
        return wo;
    }

    private com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput engineInput(String strategy,
                                                                                BigDecimal dailyCapacity) {
        var wo = new com.nexus.erp.prd.schedule.ScheduleModel.WoInfo();
        wo.setWoId(11L); wo.setWoNo("WO-11"); wo.setMaterialId(101L);
        wo.setRemainingQty(new BigDecimal("16")); wo.setPriority(1L);
        wo.setDueDate(TODAY); wo.setWorkshopId(3L);
        var step = new com.nexus.erp.prd.schedule.ScheduleModel.StepInfo();
        step.setStepId(901L); step.setSeqNum(1); step.setStandardHours(BigDecimal.ONE);
        var input = new com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput();
        input.setWorkOrders(List.of(wo));
        input.setRoutingProvider(material -> List.of(step));
        input.setStrategy(com.nexus.erp.prd.schedule.ScheduleModel.Strategy.valueOf(strategy));
        input.setStartDate(TODAY); input.setHorizonDays(7);
        input.setCapacityByDate(Map.of(TODAY, Map.of("WORKSHOP:3", dailyCapacity),
            TODAY.plusDays(1), Map.of("WORKSHOP:3", dailyCapacity)));
        input.setHolidays(java.util.Set.of());
        return input;
    }

    private ErpDecisionApsSimulation withRealShortage() {
        return new ErpDecisionApsSimulation(assembler, workOrderMapper,
            new ErpDecisionShortageSimulation(new com.nexus.erp.prd.schedule.PrdShortageScenarioService()));
    }

    @Test
    void scopedEddAndCrUseActualEngineAndExpediteChangesOnlyMaterialAssumption() {
        var saved = Map.<String, Object>of("planId", "9007199254740993", "strategy", "EDD",
            "makespanDate", TODAY.toString(), "tasks", List.of(Map.of("woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.toString())));
        DeliveryContextSnapshot snapshot = withMaterialEvidence(saved, true, true, true);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var bo = (com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo) call.getArgument(0);
            assertEquals(TODAY, bo.getStartDate().toInstant()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate());
            assertEquals(7, bo.getHorizonDays());
            assertEquals(List.of(11L), List.copyOf((java.util.Collection<Long>) call.getArgument(1)));
            return engineInput(bo.getStrategy(), new BigDecimal("8"));
        });
        var sim = withRealShortage();
        List<DecisionScenario> scenarios = sim.compare(snapshot, null, 7).scenarios();
        assertEquals(List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE"),
            scenarios.stream().map(DecisionScenario::scenarioCode).toList());
        DecisionScenario edd = scenarios.get(1), cr = scenarios.get(2), expedite = scenarios.get(3);
        assertEquals(TODAY.plusDays(1).toString(), edd.kpis().makespanDate());
        assertEquals(1, edd.kpis().delayedWorkOrderCount());
        assertEquals(1, edd.kpis().totalTardinessDays());
        assertEquals(new BigDecimal("28.6"), edd.kpis().maxResourceUtilization());
        assertEquals(1, edd.kpis().changedWorkOrderCount());
        assertFalse(cr.feasible());
        assertTrue(expedite.feasible(), () -> "reason=" + expedite.infeasibleReasons() + ", overrides=" + expedite.overrides());
        assertEquals(cr.kpis().makespanDate(), expedite.kpis().makespanDate());
        assertEquals(cr.kpis().totalTardinessDays(), expedite.kpis().totalTardinessDays());
        assertEquals(cr.kpis().maxResourceUtilization(), expedite.kpis().maxResourceUtilization());
        assertEquals(cr.kpis().changedWorkOrderCount(), expedite.kpis().changedWorkOrderCount());
        assertEquals(Map.of("purchaseArrivalOverrides", Map.of("5001", TODAY.toString())), expedite.overrides());
        assertNotEquals(cr.inputHash(), expedite.inputHash());
        verify(assembler, times(2)).assemble(any(), any(java.util.Collection.class));
        assertEquals(sim.compare(snapshot, null, 7).scenarios().get(2).inputHash(), cr.inputHash());
        assertEquals(sim.compare(snapshot, null, 7).scenarios().get(2).schedulePreview(), cr.schedulePreview());
        assertEquals(saved, snapshot.currentSchedule());
        assertEquals(0, cr.rank()); assertNull(cr.objectiveScore());
        assertInstanceOf(String.class, cr.schedulePreview().get(0).get("woId"));
        assertInstanceOf(String.class, cr.schedulePreview().get(0).get("processHours"));
        assertDoesNotThrow(() -> com.nexus.common.json.utils.JsonUtils.toJsonString(cr.schedulePreview()));
    }

    @Test
    void mapperExtraDuplicateOrMissingFailsClosedAndDoesNotAssemble() {
        for (List<com.nexus.erp.prd.domain.PrdWorkOrder> rows : List.of(
            List.of(liveWo(99L, "CREATED")),
            List.of(liveWo(11L, "CREATED"), liveWo(11L, "CREATED")),
            List.<com.nexus.erp.prd.domain.PrdWorkOrder>of())) {
            reset(workOrderMapper, assembler);
            when(workOrderMapper.selectList(any())).thenReturn(rows);
            DecisionScenario result = simulation.compare(snapshot(Map.of()), List.of("APS_EDD"), 7)
                .scenarios().get(0);
            assertFalse(result.feasible());
            assertTrue(result.infeasibleReasons().contains("SCOPED_WORK_ORDER_INCOMPLETE"));
            verifyNoInteractions(assembler);
        }
    }

    @Test
    void approvingIsExcludedWithoutPoisoningSchedulableWorkOrder() {
        var base = withMaterialEvidence(Map.of(), true, true, false);
        var two = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(),
            List.of(base.workOrders().get(0), new WorkOrderFact(22L, "WO-22", 101L,
                new BigDecimal("16"), TODAY, 1L, 3L)), base.routingByMaterial(), base.requirements(),
            base.stocks(), base.arrivals(), base.capacityByDate(), base.holidays(), base.currentSchedule(),
            base.supplierMetrics(), base.snapshotHash(), base.snapshotAt(), base.warnings());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED"), liveWo(22L, "APPROVING")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenAnswer(call -> {
                assertEquals(List.of(11L), List.copyOf((java.util.Collection<Long>) call.getArgument(1)));
                return engineInput("EDD", new BigDecimal("8"));
            });
        simulation.compare(two, List.of("APS_EDD"), 7);
        verify(assembler).assemble(any(), any(java.util.Collection.class));
        reset(assembler, workOrderMapper);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "APPROVING"), liveWo(22L, "CLOSED")));
        DecisionScenario result = simulation.compare(two, List.of("APS_EDD"), 7).scenarios().get(0);
        assertFalse(result.feasible());
        assertTrue(result.infeasibleReasons().contains("NO_SCHEDULABLE_WORK_ORDERS"));
        verifyNoInteractions(assembler);
    }

    @Test
    void changedLiveBusinessFactsFailClosedBeforeAssembling() {
        var changed = liveWo(11L, "CREATED");
        changed.setCompletedQty(BigDecimal.ONE);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(changed));
        DecisionScenario result = simulation.compare(snapshot(Map.of()), List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(List.of("STALE_SNAPSHOT"), result.infeasibleReasons());
        verifyNoInteractions(assembler);
    }

    @Test
    void scenarioCodesMustBeUniqueAndBoundedAndUnsupportedIsNotEdd() {
        var snap = snapshot(Map.of());
        assertThrows(IllegalArgumentException.class, () -> simulation.compare(snap, List.of("APS_EDD", "APS_EDD"), 7));
        assertThrows(IllegalArgumentException.class, () -> simulation.compare(snap, List.of("A", "B", "C", "D", "E"), 7));
        assertThrows(IllegalArgumentException.class, () -> simulation.compare(snap, java.util.Arrays.asList("APS_EDD", null), 7));
        assertThrows(IllegalArgumentException.class, () -> simulation.compare(snap, List.of("APS_EDD"), 0));
        assertEquals(List.of("UNSUPPORTED_SCENARIO"), simulation.compare(snap, List.of("CURRENT"), 7)
            .scenarios().get(0).infeasibleReasons());
        verifyNoInteractions(workOrderMapper, assembler);
    }

    @Test
    void serviceErrorIsIsolatedButProgrammingBugIsNotSwallowed() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var bo = (com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo) call.getArgument(0);
            if ("EDD".equals(bo.getStrategy())) throw new com.nexus.common.core.exception.ServiceException("NO_ROUTE");
            return engineInput("CR", new BigDecimal("8"));
        });
        var result = simulation.compare(withMaterialEvidence(Map.of(), true, true, true),
            List.of("APS_EDD", "APS_CR"), 7);
        assertFalse(result.scenarios().get(0).feasible());
        assertEquals("NO_ROUTE", result.scenarios().get(0).infeasibleReasons().get(0));
        assertNotNull(result.scenarios().get(1).kpis());
        reset(assembler);
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenThrow(new NullPointerException("bug"));
        assertThrows(NullPointerException.class, () -> simulation.compare(withMaterialEvidence(Map.of(), true, true, true), List.of("APS_EDD"), 7));
    }

    @Test
    void missingRequirementOrStockMustNotBecomeFeasibleEvenWhenApsFinishes() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        for (var snapshot : List.of(withMaterialEvidence(Map.of(), false, true, true),
            withMaterialEvidence(Map.of(), true, false, true))) {
            DecisionScenario scenario = withRealShortage().compare(snapshot, List.of("APS_EDD"), 7).scenarios().get(0);
            assertFalse(scenario.feasible());
            assertTrue(scenario.infeasibleReasons().contains("SHORTAGE_UNKNOWN"));
        }
    }

    @Test
    void missingArrivalOrLateArrivalNeverMovesApsMakespan() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("CR", new BigDecimal("8")));
        var results = withRealShortage().compare(withMaterialEvidence(Map.of(), true, true, false),
            List.of("APS_CR", "APS_CR_EXPEDITE"), 7).scenarios();
        assertTrue(results.get(1).infeasibleReasons().contains("NO_EXPEDITABLE_PURCHASE_LINE"));
        assertTrue(results.get(1).infeasibleReasons().contains("MATERIAL_SHORTAGE"));
        assertEquals(results.get(0).kpis().makespanDate(), results.get(1).kpis().makespanDate());
    }

    @Test
    void changingActualCapacityChangesHashWhileSameInputsRemainStable() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        var sim = withRealShortage();
        var snap = withMaterialEvidence(Map.of(), true, true, true);
        var first = sim.compare(snap, List.of("APS_EDD"), 7).scenarios().get(0);
        var repeated = sim.compare(snap, List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(first.inputHash(), repeated.inputHash());
        assertEquals(first.schedulePreview(), repeated.schedulePreview());
        assertEquals(64, first.inputHash().length());
        reset(assembler);
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("16")));
        var changed = sim.compare(snap, List.of("APS_EDD"), 7).scenarios().get(0);
        assertNotEquals(first.inputHash(), changed.inputHash());
        assertNotEquals(first.kpis().makespanDate(), changed.kpis().makespanDate());
    }

    @Test
    void incompleteHorizonOrSkippedRoutingIsNeverFeasible() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenAnswer(call -> {
                var input = engineInput("EDD", new BigDecimal("8"));
                input.setHorizonDays(((com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo) call.getArgument(0)).getHorizonDays());
                return input;
            });
        var sim = withRealShortage();
        var snap = withMaterialEvidence(Map.of(), true, true, true);
        assertTrue(sim.compare(snap, List.of("APS_EDD"), 1).scenarios().get(0)
            .infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
        var empty = engineInput("EDD", new BigDecimal("8"));
        empty.setRoutingProvider(material -> List.of());
        reset(assembler);
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(empty);
        assertTrue(sim.compare(snap, List.of("APS_EDD"), 7).scenarios().get(0)
            .infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
    }

    @Test
    void baselineKeepsScheduleKpisWhenMaterialEvidenceIsIncomplete() {
        var saved = Map.<String, Object>of("planId", "42", "strategy", "EDD", "makespanDate",
            TODAY.plusDays(4).toString(), "tasks", List.of(Map.of("woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.plusDays(4).toString())));
        var scenario = withRealShortage().compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0);
        assertNotNull(scenario.kpis());
        assertEquals(3, scenario.kpis().totalTardinessDays());
        assertEquals(List.of("SHORTAGE_UNKNOWN"), scenario.infeasibleReasons());
    }

    @Test
    void previewAndBaselineHashAreIndependentOfSavedTaskInsertionOrder() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 59; i >= 0; i--) rows.add(Map.of("woId", "11", "seqNum", i,
            "startTime", TODAY.toString(), "endTime", TODAY.plusDays(1).toString()));
        Map<String, Object> first = Map.of("planId", "42", "strategy", "CR", "tasks", rows);
        List<Map<String, Object>> reversed = new java.util.ArrayList<>(rows);
        java.util.Collections.reverse(reversed);
        var left = simulation.compare(snapshot(first), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        var right = simulation.compare(snapshot(Map.of("planId", "42", "strategy", "CR", "tasks", reversed)),
            List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(50, left.schedulePreview().size());
        assertEquals(0, left.schedulePreview().get(0).get("seqNum"));
        assertEquals(49, left.schedulePreview().get(49).get("seqNum"));
        assertEquals(left.schedulePreview(), right.schedulePreview());
        assertEquals(left.inputHash(), right.inputHash());
    }

    @Test
    void skippedWorkOrderWithoutBaselineStillCountsAsChange() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        var input = engineInput("EDD", new BigDecimal("8"));
        input.setRoutingProvider(material -> List.of());
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(input);
        var result = withRealShortage().compare(withMaterialEvidence(Map.of(), true, true, true),
            List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(1, result.kpis().changedWorkOrderCount());
        assertTrue(result.infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
    }

    @Test
    void scopedLookupBatchesAtFiveHundredAndRejectsRowsFromOtherBatch() {
        var base = withMaterialEvidence(Map.of(), true, true, false);
        List<WorkOrderFact> all = new java.util.ArrayList<>();
        for (long id = 1; id <= 501; id++) all.add(new WorkOrderFact(id, "WO-" + id, 101L,
            new BigDecimal("16"), TODAY, 1L, 3L));
        var wide = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(), all,
            base.routingByMaterial(), base.requirements(), base.stocks(), base.arrivals(),
            base.capacityByDate(), base.holidays(), base.currentSchedule(), base.supplierMetrics(),
            base.snapshotHash(), base.snapshotAt(), base.warnings());
        when(workOrderMapper.selectList(any())).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) call.getArgument(0);
            query.getSqlSegment();
            var ids = query.getParamNameValuePairs().values().stream()
                .map(value -> ((Number) value).longValue()).toList();
            assertTrue(ids.size() <= 500);
            assertTrue(ids.stream().allMatch(id -> id >= 1 && id <= 501));
            return ids.stream().map(id -> liveWo(id, "CLOSED")).toList();
        });
        var scenario = simulation.compare(wide, List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(List.of("NO_SCHEDULABLE_WORK_ORDERS"), scenario.infeasibleReasons());
        verify(workOrderMapper, times(2)).selectList(any());
        verifyNoInteractions(assembler);
        reset(workOrderMapper);
        when(workOrderMapper.selectList(any())).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) call.getArgument(0);
            query.getSqlSegment();
            var ids = query.getParamNameValuePairs().values().stream()
                .map(value -> ((Number) value).longValue()).toList();
            if (ids.size() == 500) return java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(id -> liveWo(id, "CLOSED")).toList();
            return List.of();
        });
        var malformed = simulation.compare(wide, List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(List.of("SCOPED_WORK_ORDER_INCOMPLETE"), malformed.infeasibleReasons());
        verifyNoInteractions(assembler);
    }

    @Test
    void orphanSavedTasksWithoutPlanAreNotAChangeBaseline() {
        Map<String, Object> orphan = Map.of("tasks", List.of(Map.of("woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.plusDays(1).toString())));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        var result = withRealShortage().compare(withMaterialEvidence(orphan, true, true, true),
            List.of("BASELINE_CURRENT", "APS_EDD"), 7);
        assertEquals(List.of("NO_SAVED_SCHEDULE"), result.scenarios().get(0).infeasibleReasons());
        assertEquals(1, result.scenarios().get(1).kpis().changedWorkOrderCount());
        assertTrue(result.warnings().contains("NO_BASELINE_FOR_CHANGE_COMPARISON"));
    }

    @Test
    void allInfeasibleScenariosReturnResultsWithoutPrematureTaskEightVerdict() {
        var result = simulation.compare(snapshot(Map.of()), List.of("BASELINE_CURRENT", "UNKNOWN"), 7);
        assertEquals(2, result.scenarios().size());
        assertTrue(result.scenarios().stream().noneMatch(DecisionScenario::feasible));
        assertFalse(result.warnings().contains("NO_FEASIBLE_SCENARIO"));
        assertTrue(result.warnings().contains("ALL_SCENARIOS_INFEASIBLE"));
    }

    @Test
    void baselineHashIncludesCapacityAndHolidaysNotOnlySnapshotHash() {
        Map<String, Object> saved = Map.of("planId", "42", "strategy", "EDD", "tasks",
            List.of(Map.of("woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.toString())));
        var old = snapshot(saved);
        var changed = new DeliveryContextSnapshot(old.rootBusinessRef(), old.salesOrders(), old.workOrders(),
            old.routingByMaterial(), old.requirements(), old.stocks(), old.arrivals(),
            Map.of(TODAY, Map.of("WORKSHOP:3", new BigDecimal("5"))), List.of(TODAY.plusDays(2)),
            old.currentSchedule(), old.supplierMetrics(), old.snapshotHash(), old.snapshotAt(), old.warnings());
        var left = simulation.compare(old, List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        var right = simulation.compare(changed, List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertNotEquals(left.inputHash(), right.inputHash());
    }

    @Test
    void expediteNeverHidesIncompleteApsWhenPurchaseLineIsMissing() {
        var base = withMaterialEvidence(Map.of(), true, true, false);
        var nowDemand = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(),
            base.workOrders(), base.routingByMaterial(),
            List.of(new DeliveryContextSnapshot.RequirementFact(200L, "RAW", "原料", TODAY,
                new BigDecimal("10"), 11L, "WO-11")), base.stocks(), base.arrivals(),
            base.capacityByDate(), base.holidays(), base.currentSchedule(), base.supplierMetrics(),
            base.snapshotHash(), base.snapshotAt(), base.warnings());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var input = engineInput("CR", new BigDecimal("8"));
            input.setHorizonDays(1);
            return input;
        });
        var scenarios = withRealShortage().compare(nowDemand, List.of("APS_CR", "APS_CR_EXPEDITE"), 1).scenarios();
        assertTrue(scenarios.get(0).infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
        assertTrue(scenarios.get(1).infeasibleReasons().contains("NO_EXPEDITABLE_PURCHASE_LINE"));
        assertTrue(scenarios.get(1).infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
    }
}
