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
    private com.nexus.common.ai.decision.dto.ShortageSimulationResult knownMaterialResult() {
        return new com.nexus.common.ai.decision.dto.ShortageSimulationResult(false, 0, null,
            BigDecimal.ZERO, List.of(), new com.nexus.common.ai.decision.dto.DecisionKpiSet(
                0, 0, 0, 0, null, 0, BigDecimal.ZERO, null, null, 0, 0, null), List.of(), List.of());
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
                Map.of("taskId", "1", "woId", "11", "seqNum", 1, "startTime", "2026-09-24T00:00:00",
                    "endTime", "2026-09-26T23:59:59", "processHours", "8"),
                Map.of("taskId", "2", "woId", "11", "seqNum", 2, "startTime", "2026-09-27T00:00:00",
                    "endTime", "2026-09-29T23:59:59", "processHours", "4")));
        when(shortage.simulate(any(), anyInt(), any())).thenReturn(knownMaterialResult());
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

    @Test void savedBaselineUsesOnlyCompletePersistedCalendarLoadNotHypotheticalApsRate() {
        Map<String, Object> saved = Map.of("planId", "1", "strategy", "EDD",
            "tasks", List.of(Map.of("taskId", "5", "woId", "11", "seqNum", 1,
                "resourceType", "WORKSTATION", "resourceId", "12", "processHours", "2",
                "startTime", TODAY.toString(), "endTime", TODAY.plusDays(1).toString())),
            "capacityCalendars", List.of(
                Map.of("calendarId", "7", "calendarDate", TODAY.toString(),
                    "resourceType", "WORKSTATION", "resourceId", "12",
                    "plannedHours", "4", "effectiveHours", "8"),
                Map.of("calendarId", "8", "calendarDate", TODAY.plusDays(1).toString(),
                    "resourceType", "WORKSTATION", "resourceId", "12",
                    "plannedHours", "3", "effectiveHours", "2")));
        when(shortage.simulate(any(), anyInt(), any())).thenReturn(knownMaterialResult());
        var baseline = simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0);
        assertEquals(0, new BigDecimal("150").compareTo(baseline.kpis().maxResourceUtilization()));
        assertEquals(1, baseline.kpis().overloadedResourceCount());
        assertFalse(simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7).warnings()
            .contains("NO_BASELINE_UTILIZATION"));
        verifyNoInteractions(workOrderMapper, assembler);
    }
    private DeliveryContextSnapshot withMaterialEvidence(Map<String, Object> schedule, boolean demand,
                                                         boolean stock, boolean arrival) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"),
            List.of(), List.of(new WorkOrderFact(11L, "WO-11", 101L, new BigDecimal("16"),
                TODAY, 1L, 3L)), routeFacts(),
            demand ? List.of(new DeliveryContextSnapshot.RequirementFact(200L, "RAW", "原料",
                TODAY.plusDays(1), new BigDecimal("10"), 11L, "WO-11")) : List.of(),
            stock ? List.of(new DeliveryContextSnapshot.StockFact(200L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO)) : List.of(),
            arrival ? List.of(new DeliveryContextSnapshot.ArrivalFact(200L, 5002L, TODAY.plusDays(4),
                    new BigDecimal("10"), 6L),
                new DeliveryContextSnapshot.ArrivalFact(200L, 5001L, TODAY.plusDays(3),
                    new BigDecimal("10"), 6L)) : List.of(),
            calendar(new BigDecimal("8")), List.of(), schedule, List.of(), "hash",
            LocalDateTime.of(2026, 9, 24, 10, 0), List.of());
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
            "makespanDate", TODAY.toString(), "tasks", List.of(Map.of("taskId", "1", "woId", "11", "seqNum", 1,
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
        var compared = sim.compare(snapshot, null, 7);
        List<DecisionScenario> scenarios = compared.scenarios();
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
        assertEquals(0, BigDecimal.ONE.compareTo(expedite.kpis().actionCost()),
            "one overridden purchase line costs one proxy unit, not actual money");
        assertTrue(compared.warnings().contains("EXPEDITE_COST_IS_LINE_COUNT_PROXY"));
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
            assertStale(() -> simulation.compare(snapshot(Map.of()), List.of("APS_EDD"), 7));
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
        assertStale(() -> simulation.compare(snapshot(Map.of()), List.of("APS_EDD"), 7));
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
    void assemblerErrorsAndProgrammingBugsAbortWithoutContinuingScenarios() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var bo = (com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo) call.getArgument(0);
            if ("EDD".equals(bo.getStrategy())) throw new com.nexus.common.core.exception.ServiceException("NO_ROUTE");
            return engineInput("CR", new BigDecimal("8"));
        });
        var base = withMaterialEvidence(savedOneTask(), true, true, false);
        var safe = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(),
            base.workOrders(), base.routingByMaterial(), base.requirements(),
            List.of(new DeliveryContextSnapshot.StockFact(200L, new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO)), base.arrivals(), base.capacityByDate(),
            base.holidays(), base.currentSchedule(), base.supplierMetrics(), base.snapshotHash(),
            base.snapshotAt(), base.warnings());
        var sim = withRealShortage();
        assertTrue(sim.compare(safe, List.of("BASELINE_CURRENT"), 7).scenarios().get(0).feasible());
        var failure = assertThrows(com.nexus.common.core.exception.ServiceException.class, () ->
            sim.compare(safe, List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR"), 7));
        assertEquals("NO_ROUTE", failure.getMessage());
        verify(assembler, times(1)).assemble(any(), any(java.util.Collection.class));
        // 未分类装配异常出现后，不得继续运行 CR 候选。
        reset(assembler);
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenThrow(new NullPointerException("bug"));
        assertThrows(NullPointerException.class, () -> simulation.compare(withMaterialEvidence(Map.of(), true, true, true), List.of("APS_EDD"), 7));
    }

    @Test
    void assemblerNoRouteWithForbiddenCodeMustAbortEvenAfterFeasibleBaseline() {
        var snapshot = withMaterialEvidence(savedOneTask(), true, true, true);
        when(shortage.simulate(any(), anyInt(), any())).thenReturn(knownMaterialResult());
        assertTrue(simulation.compare(snapshot, List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0).feasible());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        var forbidden = new com.nexus.common.core.exception.ServiceException("NO_ROUTE", 403);
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var bo = (com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo) call.getArgument(0);
            if ("EDD".equals(bo.getStrategy())) throw forbidden;
            return engineInput("CR", new BigDecimal("8"));
        });
        var caught = assertThrows(com.nexus.common.core.exception.ServiceException.class, () ->
            simulation.compare(snapshot, List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR"), 7));
        assertSame(forbidden, caught, "must preserve original error code and instance");
        assertEquals(403, caught.getCode());
        verify(assembler, times(1)).assemble(any(), any(java.util.Collection.class));
    }

    @Test
    void unsupportedScenarioDoesNotPreventLaterScopedCr() {
        var snapshot = withMaterialEvidence(Map.of(), true, true, true);
        when(shortage.simulate(any(), anyInt(), any())).thenReturn(knownMaterialResult());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("CR", new BigDecimal("8")));
        var compared = simulation.compare(snapshot, List.of("NOT_SUPPORTED", "APS_CR"), 7);
        assertEquals(2, compared.scenarios().size());
        assertEquals(List.of("UNSUPPORTED_SCENARIO"), compared.scenarios().get(0).infeasibleReasons());
        assertNotNull(compared.scenarios().get(0).inputHash());
        assertTrue(compared.scenarios().get(1).feasible());
        assertFalse(compared.scenarios().get(1).schedulePreview().isEmpty());
        verify(assembler, times(1)).assemble(any(), any(java.util.Collection.class));
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
        var newerSnapshot = withApsFacts(snap, routeFacts(), calendar(new BigDecimal("16")),
            List.of(), "hash-capacity-16");
        var changed = sim.compare(newerSnapshot, List.of("APS_EDD"), 7).scenarios().get(0);
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
        var withoutRouting = withApsFacts(snap, Map.of(101L, List.of()), snap.capacityByDate(),
            snap.holidays(), "hash-no-route");
        assertTrue(sim.compare(withoutRouting, List.of("APS_EDD"), 7).scenarios().get(0)
            .infeasibleReasons().contains("SCHEDULE_INCOMPLETE"));
    }

    @Test
    void typedMissingRequirementsRemainAnInfeasibleApsResultNotAnEngineCrash() {
        var saved = Map.<String, Object>of("planId", "42", "strategy", "EDD", "makespanDate",
            TODAY.plusDays(4).toString(), "tasks", List.of(Map.of("taskId", "1", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.plusDays(4).toString())));
        when(shortage.simulate(any(), anyInt(), any())).thenThrow(new com.nexus.common.ai.decision.exception.DecisionEvidenceException(
            com.nexus.common.ai.decision.exception.DecisionEvidenceException.Reason.SHORTAGE_SIMULATION_INCOMPLETE));
        var result = assertDoesNotThrow(() -> simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7));
        assertFalse(result.scenarios().get(0).feasible());
        assertTrue(result.warnings().contains("SHORTAGE_SIMULATION_INCOMPLETE"));
    }

    @Test
    void baselineKeepsScheduleKpisWhenMaterialEvidenceIsIncomplete() {
        var saved = Map.<String, Object>of("planId", "42", "strategy", "EDD", "makespanDate",
            TODAY.plusDays(4).toString(), "tasks", List.of(Map.of("taskId", "1", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.plusDays(4).toString())));
        var scenario = withRealShortage().compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0);
        assertNull(scenario.kpis());
        assertFalse(scenario.schedulePreview().isEmpty());
        assertEquals(List.of("SHORTAGE_UNKNOWN"), scenario.infeasibleReasons());
    }

    @Test
    void previewAndBaselineHashAreIndependentOfSavedTaskInsertionOrder() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 59; i >= 0; i--) rows.add(Map.of("taskId", String.valueOf(i + 100), "woId", "11", "seqNum", i,
            "startTime", TODAY.toString(), "endTime", TODAY.toString()));
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
        var base = withMaterialEvidence(Map.of(), true, true, true);
        var withoutRouting = withApsFacts(base, Map.of(101L, List.of()), base.capacityByDate(),
            base.holidays(), "hash-no-route");
        var result = withRealShortage().compare(withoutRouting,
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
        assertStale(() -> simulation.compare(wide, List.of("APS_EDD"), 7));
        verifyNoInteractions(assembler);
    }

    @Test
    void orphanSavedTasksWithoutPlanAreNotAChangeBaseline() {
        Map<String, Object> orphan = Map.of("tasks", List.of(Map.of("woId", "11", "seqNum", 1,
            "taskId", "1", "startTime", TODAY.toString(), "endTime", TODAY.plusDays(1).toString())));
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

    private DeliveryContextSnapshot withApsFacts(DeliveryContextSnapshot base,
        Map<Long, List<DeliveryContextSnapshot.RoutingStepFact>> routes,
        Map<LocalDate, Map<String, BigDecimal>> capacity, List<LocalDate> holidays, String hash) {
        return new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(), base.workOrders(),
            routes, base.requirements(), base.stocks(), base.arrivals(), capacity, holidays,
            base.currentSchedule(), base.supplierMetrics(), hash, base.snapshotAt(), base.warnings());
    }

    private Map<Long, List<DeliveryContextSnapshot.RoutingStepFact>> routeFacts() {
        return Map.of(101L, List.of(new DeliveryContextSnapshot.RoutingStepFact(
            9010L, 901L, 1, null, 3L, BigDecimal.ONE)));
    }

    private Map<LocalDate, Map<String, BigDecimal>> calendar(BigDecimal hours) {
        return Map.of(TODAY, Map.of("WORKSHOP:3", hours),
            TODAY.plusDays(1), Map.of("WORKSHOP:3", hours));
    }

    private Map<String, Object> savedOneTask() {
        return Map.of("planId", "42", "strategy", "EDD", "makespanDate", TODAY.toString(),
            "tasks", List.of(Map.of("taskId", "101", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.toString())));
    }

    private void assertStale(Runnable compare) {
        var ex = assertThrows(com.nexus.common.core.exception.ServiceException.class, compare::run);
        assertTrue(ex.getMessage().startsWith("STALE_SNAPSHOT"), ex.getMessage());
    }

    @Test
    void review1RouteChangesBetweenSnapshotAndAssemblerMustAbortEntireCompare() {
        var snap = withApsFacts(withMaterialEvidence(savedOneTask(), true, true, true),
            routeFacts(), calendar(new BigDecimal("8")), List.of(), "hash-route-old");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        var changed = engineInput("EDD", new BigDecimal("8"));
        var newStep = new com.nexus.erp.prd.schedule.ScheduleModel.StepInfo();
        newStep.setStepId(901L); newStep.setSeqNum(1); newStep.setStandardHours(new BigDecimal("16"));
        changed.setRoutingProvider(material -> List.of(newStep));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(changed);
        assertStale(() -> withRealShortage().compare(snap, List.of("BASELINE_CURRENT", "APS_EDD"), 7));
        // 缺失的旧工艺路线不得由新建的实时路线悄然补齐。
        var missing = withApsFacts(snap, Map.of(), snap.capacityByDate(), List.of(), "hash-no-route");
        assertStale(() -> withRealShortage().compare(missing, List.of("APS_EDD"), 7));
    }

    @Test
    void review1CapacityOrHolidayChangesBetweenSnapshotAndAssemblerMustAbort() {
        var snap = withApsFacts(withMaterialEvidence(Map.of(), true, true, true),
            routeFacts(), calendar(new BigDecimal("8")), List.of(), "hash-calendar-old");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("16")));
        assertStale(() -> withRealShortage().compare(snap, List.of("APS_EDD"), 7));
        var holiday = engineInput("EDD", new BigDecimal("8"));
        holiday.setHolidays(java.util.Set.of(TODAY));
        reset(assembler);
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(holiday);
        assertStale(() -> withRealShortage().compare(snap, List.of("APS_EDD"), 7));
    }

    @Test
    void review2AssemblerOrMapperExceptionsCannotBeHiddenByFeasibleSavedBaseline() {
        var base = withApsFacts(withMaterialEvidence(savedOneTask(), true, true, false),
            routeFacts(), calendar(new BigDecimal("8")), List.of(), "hash");
        var safe = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(), base.workOrders(),
            base.routingByMaterial(), base.requirements(),
            List.of(new DeliveryContextSnapshot.StockFact(200L, new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO)), base.arrivals(), base.capacityByDate(),
            base.holidays(), base.currentSchedule(), base.supplierMetrics(), base.snapshotHash(),
            base.snapshotAt(), base.warnings());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenThrow(new com.nexus.common.core.exception.ServiceException("ERP_ACCESS_DENIED"));
        var ex = assertThrows(com.nexus.common.core.exception.ServiceException.class, () ->
            withRealShortage().compare(safe, List.of("BASELINE_CURRENT", "APS_EDD"), 7));
        assertEquals("ERP_ACCESS_DENIED", ex.getMessage());
        reset(workOrderMapper, assembler);
        when(workOrderMapper.selectList(any())).thenThrow(new IllegalArgumentException("DATA_SOURCE_DENIED"));
        assertEquals("DATA_SOURCE_DENIED", assertThrows(IllegalArgumentException.class, () ->
            withRealShortage().compare(safe, List.of("BASELINE_CURRENT", "APS_EDD"), 7)).getMessage());
        reset(workOrderMapper);
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        assertStale(() -> withRealShortage().compare(safe, List.of("BASELINE_CURRENT", "APS_EDD"), 7));
    }

    @Test
    void review3WoNumberChangeOnEitherDbReadMustAbort() {
        var snap = withApsFacts(withMaterialEvidence(Map.of(), true, true, true),
            routeFacts(), calendar(new BigDecimal("8")), List.of(), "hash");
        var renamed = liveWo(11L, "CREATED");
        renamed.setWoNo("DIFFERENT");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(renamed));
        assertStale(() -> withRealShortage().compare(snap, List.of("APS_EDD"), 7));
        verifyNoInteractions(assembler);
        reset(workOrderMapper);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        var changedInput = engineInput("EDD", new BigDecimal("8"));
        changedInput.getWorkOrders().get(0).setWoNo("RENAMED-BETWEEN-READS");
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(changedInput);
        assertStale(() -> withRealShortage().compare(snap, List.of("APS_EDD"), 7));
    }

    @Test
    void review4BaselineRejectsEarlierTerminalOperationAndMissingTerminalEnd() {
        Map<String, Object> backwards = Map.of("planId", "42", "strategy", "CR", "tasks", List.of(
            Map.of("taskId", "1", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.plusDays(4).toString()),
            Map.of("taskId", "2", "woId", "11", "seqNum", 2,
                "startTime", TODAY.plusDays(1).toString(), "endTime", TODAY.plusDays(1).toString())));
        var first = simulation.compare(snapshot(backwards), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(List.of("INVALID_SAVED_SCHEDULE"), first.infeasibleReasons());
        var terminal = new java.util.LinkedHashMap<String, Object>();
        terminal.put("taskId", "2"); terminal.put("woId", "11"); terminal.put("seqNum", 2);
        terminal.put("startTime", TODAY.plusDays(1).toString()); terminal.put("endTime", null);
        var saved = Map.<String, Object>of("planId", "42", "tasks", List.of(
            Map.of("taskId", "1", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.toString()), terminal));
        var second = simulation.compare(snapshot(saved), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(List.of("INVALID_SAVED_SCHEDULE"), second.infeasibleReasons());
        assertNull(second.kpis());
    }

    @Test
    void review4BaselineRejectsIncompleteSequenceInsteadOfTreatingEarlierTaskAsTerminal() {
        var bad = Map.<String, Object>of("planId", "42", "tasks", List.of(
            Map.of("taskId", "1", "woId", "11", "seqNum", 1,
                "startTime", TODAY.toString(), "endTime", TODAY.toString()),
            Map.of("taskId", "2", "woId", "11", "seqNum", "",
                "startTime", TODAY.plusDays(1).toString(), "endTime", TODAY.plusDays(1).toString())));
        var result = simulation.compare(snapshot(bad), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(List.of("INVALID_SAVED_SCHEDULE"), result.infeasibleReasons());
    }

    @Test
    void review5UnknownMaterialRiskHasNoCompositeKpisButKeepsSchedulePreview() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        var result = withRealShortage().compare(withMaterialEvidence(Map.of(), false, true, true),
            List.of("APS_EDD"), 7).scenarios().get(0);
        assertEquals(List.of("SHORTAGE_UNKNOWN"), result.infeasibleReasons());
        assertNull(result.kpis());
        assertFalse(result.schedulePreview().isEmpty());
    }

    @Test
    void review6HashCoversHorizonSnapshotAndFailedScenarios() {
        var noPlan = simulation.compare(snapshot(Map.of()), List.of("BASELINE_CURRENT", "UNSUPPORTED"), 7);
        for (var result : noPlan.scenarios()) {
            assertNotNull(result.inputHash());
            assertEquals(64, result.inputHash().length());
        }
        var saved = snapshot(savedOneTask());
        var seven = simulation.compare(saved, List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        var eight = simulation.compare(saved, List.of("BASELINE_CURRENT"), 8).scenarios().get(0);
        assertNotEquals(seven.inputHash(), eight.inputHash());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        var base = withApsFacts(withMaterialEvidence(Map.of(), true, true, true),
            routeFacts(), calendar(new BigDecimal("8")), List.of(), "stock-low");
        var enough = new DeliveryContextSnapshot(base.rootBusinessRef(), base.salesOrders(), base.workOrders(),
            base.routingByMaterial(), base.requirements(),
            List.of(new DeliveryContextSnapshot.StockFact(200L, new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO)), base.arrivals(), base.capacityByDate(), base.holidays(),
            base.currentSchedule(), base.supplierMetrics(), "stock-high", base.snapshotAt(), base.warnings());
        var sim = withRealShortage();
        var low = sim.compare(base, List.of("APS_EDD"), 7).scenarios().get(0);
        var high = sim.compare(enough, List.of("APS_EDD"), 7).scenarios().get(0);
        assertNotEquals(low.kpis().shortageMaterialCount(), high.kpis().shortageMaterialCount());
        assertNotEquals(low.inputHash(), high.inputHash());
    }

    @Test
    void review6BaselineHashAndPreviewSortSameSequenceByTaskIdAndLinkId() {
        Map<String, Object> firstTask = Map.of("taskId", "101", "woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.toString());
        Map<String, Object> secondTask = Map.of("taskId", "102", "woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.toString());
        Map<String, Object> firstLink = Map.of("workOrderId", "11", "salesOrderId", "42");
        Map<String, Object> secondLink = Map.of("workOrderId", "22", "salesOrderId", "43");
        var a = new java.util.LinkedHashMap<String, Object>(savedOneTask());
        a.put("tasks", List.of(secondTask, firstTask));
        a.put("workOrderSalesOrders", List.of(secondLink, firstLink));
        a.put("workOrderIds", List.of("22", "11"));
        a.put("workOrderSalesOrderIds", List.of("43", "42"));
        var b = new java.util.LinkedHashMap<String, Object>(savedOneTask());
        b.put("tasks", List.of(firstTask, secondTask));
        b.put("workOrderSalesOrders", List.of(firstLink, secondLink));
        b.put("workOrderIds", List.of("11", "22"));
        b.put("workOrderSalesOrderIds", List.of("42", "43"));
        var left = simulation.compare(snapshot(a), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        var right = simulation.compare(snapshot(b), List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals("101", left.schedulePreview().get(0).get("taskId"));
        assertEquals(left.schedulePreview(), right.schedulePreview());
        assertEquals(left.inputHash(), right.inputHash());
    }

    @Test
    void review6ExpediteUnavailableWithoutCrInputStillHashesItsOwnScenarioCode() {
        var snapshot = withMaterialEvidence(Map.of(), true, true, true);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CLOSED")));
        var cr = simulation.compare(snapshot, List.of("APS_CR"), 7).scenarios().get(0);
        var expedite = simulation.compare(snapshot, List.of("APS_CR_EXPEDITE"), 7).scenarios().get(0);
        assertNotNull(expedite.inputHash());
        assertEquals(64, expedite.inputHash().length());
        assertNotEquals(cr.inputHash(), expedite.inputHash());
    }

    @Test
    void review5NoRequirementWarningRejectsCompositeKpisEvenIfHelperSuppliesZeros() {
        var misleading = new com.nexus.common.ai.decision.dto.ShortageSimulationResult(false, 0, null,
            BigDecimal.ZERO, List.of(), knownMaterialResult().kpis(), List.of(),
            List.of("NO_REQUIREMENT_DATA"));
        when(shortage.simulate(any(), anyInt(), any())).thenReturn(misleading);
        var saved = simulation.compare(snapshot(savedOneTask()), List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0);
        assertFalse(saved.feasible());
        assertNull(saved.kpis());
        assertTrue(saved.infeasibleReasons().contains("SHORTAGE_UNKNOWN"));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("CR", new BigDecimal("8")));
        var scenarios = simulation.compare(withMaterialEvidence(Map.of(), true, true, true),
            List.of("APS_CR", "APS_CR_EXPEDITE"), 7).scenarios();
        for (var scenario : scenarios) {
            assertFalse(scenario.feasible());
            assertNull(scenario.kpis());
            assertTrue(scenario.infeasibleReasons().contains("SHORTAGE_UNKNOWN"));
            assertNotNull(scenario.inputHash());
        }
    }

    @Test
    void review3InputHashContainsWorkOrderCodeEvenWhenSnapshotHashIsUnchanged() {
        var original = withMaterialEvidence(Map.of(), true, true, true);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        when(assembler.assemble(any(), any(java.util.Collection.class)))
            .thenReturn(engineInput("EDD", new BigDecimal("8")));
        String before = withRealShortage().compare(original, List.of("APS_EDD"), 7)
            .scenarios().get(0).inputHash();
        var renamed = new WorkOrderFact(11L, "WO-RENAMED", 101L, new BigDecimal("16"),
            TODAY, 1L, 3L);
        var changed = new DeliveryContextSnapshot(original.rootBusinessRef(), original.salesOrders(),
            List.of(renamed), original.routingByMaterial(), original.requirements(),
            original.stocks(), original.arrivals(), original.capacityByDate(), original.holidays(),
            original.currentSchedule(), original.supplierMetrics(), original.snapshotHash(),
            original.snapshotAt(), original.warnings());
        var row = liveWo(11L, "CREATED");
        row.setWoNo("WO-RENAMED");
        reset(workOrderMapper, assembler);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(row));
        when(assembler.assemble(any(), any(java.util.Collection.class))).thenAnswer(call -> {
            var input = engineInput("EDD", new BigDecimal("8"));
            input.getWorkOrders().get(0).setWoNo("WO-RENAMED");
            return input;
        });
        String after = withRealShortage().compare(changed, List.of("APS_EDD"), 7)
            .scenarios().get(0).inputHash();
        assertNotEquals(before, after);
    }

    @Test
    void reviewRouteDriftInSetupOutsourceWaitOrTransferAbortsTheWholeCompare() {
        var snapshot = withMaterialEvidence(savedOneTask(), true, true, true);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(liveWo(11L, "CREATED")));
        for (String field : List.of("setup", "outsource", "wait", "transfer")) {
            var input = engineInput("EDD", new BigDecimal("8"));
            var step = input.getRoutingProvider().apply(101L).get(0);
            switch (field) {
                case "setup" -> step.setSetupHours(new BigDecimal("2"));
                case "outsource" -> step.setIsOutsource(1);
                case "wait" -> step.setWaitTime(new BigDecimal("12"));
                case "transfer" -> step.setTransferTime(new BigDecimal("3"));
                default -> fail("unknown test case");
            }
            when(assembler.assemble(any(), any(java.util.Collection.class))).thenReturn(input);
            assertStale(() -> withRealShortage().compare(snapshot,
                List.of("BASELINE_CURRENT", "APS_EDD"), 7));
        }
    }

    @Test
    void baselineHashIncludesEveryRouteTimingFieldEvenWithSameSnapshotHash() {
        var base = withMaterialEvidence(savedOneTask(), true, true, true);
        String original = simulation.compare(base, List.of("BASELINE_CURRENT"), 7)
            .scenarios().get(0).inputHash();
        for (String field : List.of("setup", "outsource", "wait", "transfer")) {
            var changed = switch (field) {
                case "setup" -> new DeliveryContextSnapshot.RoutingStepFact(
                    9010L, 901L, 1, null, 3L, BigDecimal.ONE,
                    new BigDecimal("2"), 0, BigDecimal.ZERO, BigDecimal.ZERO);
                case "outsource" -> new DeliveryContextSnapshot.RoutingStepFact(
                    9010L, 901L, 1, null, 3L, BigDecimal.ONE,
                    BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO);
                case "wait" -> new DeliveryContextSnapshot.RoutingStepFact(
                    9010L, 901L, 1, null, 3L, BigDecimal.ONE,
                    BigDecimal.ZERO, 0, new BigDecimal("2"), BigDecimal.ZERO);
                case "transfer" -> new DeliveryContextSnapshot.RoutingStepFact(
                    9010L, 901L, 1, null, 3L, BigDecimal.ONE,
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, new BigDecimal("2"));
                default -> throw new IllegalStateException("unknown test case");
            };
            var snapshot = withApsFacts(base, Map.of(101L, List.of(changed)),
                base.capacityByDate(), base.holidays(), base.snapshotHash());
            var hash = simulation.compare(snapshot, List.of("BASELINE_CURRENT"), 7)
                .scenarios().get(0).inputHash();
            assertNotEquals(original, hash, field);
        }
    }

    @Test
    void scopeBaselineUsesTerminalMaxNotGlobalPlanMakespan() {
        var saved = new java.util.LinkedHashMap<String, Object>(savedOneTask());
        saved.put("makespanDate", TODAY.plusDays(5).toString());
        saved.put("tasks", List.of(Map.of("taskId", "101", "woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.plusDays(1).toString())));
        var baseline = withRealShortage().compare(withMaterialEvidence(saved, true, true, true),
            List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(TODAY.plusDays(1).toString(), baseline.kpis().makespanDate());
        assertEquals(1, baseline.kpis().totalTardinessDays());
    }

    @Test
    void review6InvalidDuplicateTasksStillYieldOrderIndependentHash() {
        var taskA = Map.<String, Object>of("taskId", "501", "woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.toString(), "processHours", "4");
        var taskB = Map.<String, Object>of("taskId", "501", "woId", "11", "seqNum", 1,
            "startTime", TODAY.toString(), "endTime", TODAY.toString(), "processHours", "8");
        var left = simulation.compare(snapshot(Map.of("planId", "42", "tasks", List.of(taskA, taskB))),
            List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        var right = simulation.compare(snapshot(Map.of("planId", "42", "tasks", List.of(taskB, taskA))),
            List.of("BASELINE_CURRENT"), 7).scenarios().get(0);
        assertEquals(List.of("INVALID_SAVED_SCHEDULE"), left.infeasibleReasons());
        assertEquals(left.inputHash(), right.inputHash());
    }
}
