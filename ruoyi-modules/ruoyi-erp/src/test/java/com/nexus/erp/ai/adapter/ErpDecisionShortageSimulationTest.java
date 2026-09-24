package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.StockFact;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.schedule.PrdShortageScenarioService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("dev")
class ErpDecisionShortageSimulationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 9, 24, 10, 0);
    private final ErpDecisionShortageSimulation simulation =
        new ErpDecisionShortageSimulation(new PrdShortageScenarioService());

    @Test
    void twoMaterialsUseEveryPurchaseLineAndLeaveSnapshotUnchanged() {
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY.plusDays(1), "10", 11L),
                requirement(101L, TODAY.plusDays(3), "5", 11L),
                requirement(102L, TODAY.plusDays(2), "6", 22L)),
            List.of(stock(101L, "6", "1", "1"), stock(102L, "1", "0", "0")),
            List.of(arrival(101L, 5001L, TODAY.plusDays(5), "8"),
                arrival(101L, 5002L, TODAY.plusDays(1), "4")),
            Map.of("workOrderSalesOrders", List.of(Map.of("workOrderId", "11", "salesOrderId", "42"),
                Map.of("workOrderId", "22", "salesOrderId", "99")),
                "workOrderSalesOrderIds", List.of("42", "99")));

        ShortageSimulationResult base = simulation.simulate(snapshot, 7, null);
        ShortageSimulationResult changed = simulation.simulate(snapshot, 7,
            Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-09-24")));

        assertTrue(base.hasRisk());
        assertEquals(2, base.riskMaterialCount());
        assertEquals(TODAY.plusDays(1), base.earliestShortageDate());
        assertEquals(new BigDecimal("12"), base.totalGap());
        assertEquals(List.of(101L, 102L), base.items().stream().map(ShortageSimulationResult.Item::materialId).toList());
        assertEquals(new BigDecimal("7"), base.items().get(0).maxGap());
        assertEquals(new BigDecimal("12"), base.items().get(0).onOrderQty());
        assertEquals("URGE", base.items().get(0).recommendationCode());
        assertEquals(List.of(11L), base.items().get(0).affectedWorkOrderIds());
        assertEquals(List.of(42L), base.items().get(0).affectedSalesOrderIds());
        assertEquals(List.of("erp:requirement:101", "erp:purchaseLine:5001", "erp:purchaseLine:5002"),
            base.items().get(0).evidenceKeys());
        assertEquals(List.of(99L), base.items().get(1).affectedSalesOrderIds());

        assertEquals(1, changed.riskMaterialCount());
        assertEquals(TODAY.plusDays(2), changed.earliestShortageDate());
        assertEquals(new BigDecimal("5"), changed.totalGap());
        assertEquals(List.of(102L, 101L), changed.items().stream().map(ShortageSimulationResult.Item::materialId).toList());
        assertNull(changed.items().get(1).firstShortageDate());
        assertEquals(new BigDecimal("12"), changed.items().get(1).onOrderQty());
        assertEquals("NO_RISK", changed.items().get(1).recommendationCode());
        assertEquals(new BigDecimal("5"), changed.kpis().totalShortageGap());
        assertEquals(1, changed.kpis().shortageMaterialCount());
        assertEquals(1, changed.kpis().impactedOrderCount());
        assertNull(changed.kpis().makespanDate());
        assertTrue(changed.warnings().stream().anyMatch(w -> w.contains("未计算")));
        assertEquals(TODAY.plusDays(5), snapshot.arrivals().get(0).expectedDate());
        assertEquals(TODAY.plusDays(1), snapshot.arrivals().get(1).expectedDate());
        assertEquals(new BigDecimal("12"), simulation.simulate(snapshot, 7, Map.of()).totalGap());
    }

    @Test
    void rejectsUnknownMalformedAndDuplicateOverridesBeforeSimulating() {
        DeliveryContextSnapshot snapshot = fixture();
        List<Map<String, Object>> invalid = new ArrayList<>();
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("9999", "2026-09-24")));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-02-30")));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-9-24")));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of(5001L, "2026-09-24")));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("0", "2026-09-24")));
        invalid.add(Map.of("otherField", 1));
        invalid.add(Map.of("purchaseArrivalOverrides", "not a map"));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("5001", TODAY)));
        invalid.add(Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-09-24", "05001", "2026-09-25")));
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("5001", null);
        invalid.add(Map.of("purchaseArrivalOverrides", nullValue));
        Map<String, Object> nullKey = new LinkedHashMap<>();
        nullKey.put(null, "2026-09-24");
        invalid.add(Map.of("purchaseArrivalOverrides", nullKey));
        for (Map<String, Object> overrides : invalid) {
            assertThrows(IllegalArgumentException.class, () -> simulation.simulate(snapshot, 7, overrides),
                "必须拒绝不安全覆盖: " + overrides);
        }
    }

    @Test
    void rejectsInvalidSnapshotAndHorizonAndConflictingSafetyStock() {
        DeliveryContextSnapshot valid = fixture();
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(null, 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(valid, 0, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(valid, 91, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(null, SNAPSHOT_AT, "hash", valid.requirements(), valid.stocks(), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(valid.rootBusinessRef(), null, "hash", valid.requirements(), valid.stocks(), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(valid.rootBusinessRef(), SNAPSHOT_AT, " ", valid.requirements(), valid.stocks(), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(valid.rootBusinessRef(), SNAPSHOT_AT, "hash", null, valid.stocks(), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(valid.rootBusinessRef(), SNAPSHOT_AT, "hash", valid.requirements(), null, valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(
            snapshot(valid.rootBusinessRef(), SNAPSHOT_AT, "hash", valid.requirements(), valid.stocks(), null, null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(snapshot(valid.requirements(),
            List.of(stock(101L, "4", "1", "1"), stock(101L, "4", "1", "2")), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(snapshot(
            List.of(requirement(101L, TODAY.minusDays(1), "2", -1L),
                requirement(101L, TODAY, "3", 11L)), valid.stocks(), valid.arrivals(), null), 7, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(snapshot(valid.requirements(),
            valid.stocks(), List.of(arrival(101L, 5001L, TODAY, "2"),
                arrival(101L, 5001L, TODAY.plusDays(2), "3")), null), 7, null));
    }

    @Test
    void groupsSameDayLinesSubtractsReservationsAndSafetyOnceAndKeepsDecimalPrecision() {
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "11.25", 11L), requirement(101L, TODAY, "5", 11L)),
            List.of(stock(101L, "7.50", "2", "3"), stock(101L, "4", "1", "3.00")),
            List.of(arrival(101L, 5002L, TODAY, "4"), arrival(101L, 5001L, TODAY, "3.25")), null);

        ShortageSimulationResult result = simulation.simulate(snapshot, 1, null);

        assertEquals(TODAY, result.earliestShortageDate());
        assertEquals(new BigDecimal("3.50"), result.totalGap());
        assertEquals(new BigDecimal("7.25"), result.items().get(0).onOrderQty());
        assertEquals("PURCHASE", result.items().get(0).recommendationCode());
        assertEquals(List.of("erp:requirement:101", "erp:purchaseLine:5001", "erp:purchaseLine:5002"),
            result.items().get(0).evidenceKeys());
        assertEquals(List.of(11L), result.items().get(0).affectedWorkOrderIds());
    }

    @Test
    void mapsSalesOrdersOnlyViaAffectedWorkOrdersAndFallsBackToSalesRoot() {
        DeliveryContextSnapshot mapped = snapshot(List.of(requirement(101L, TODAY, "10", 11L)),
            List.of(stock(101L, "0", "0", "0")), List.of(),
            Map.of("workOrderSalesOrders", List.of(Map.of("workOrderId", "11", "salesOrderId", "42"),
                Map.of("workOrderId", "22", "salesOrderId", "99")),
                "workOrderSalesOrderIds", List.of("42", "99")));
        assertEquals(List.of(42L), simulation.simulate(mapped, 3, null).items().get(0).affectedSalesOrderIds());

        DeliveryContextSnapshot noMapping = snapshot(mapped.requirements(), mapped.stocks(), List.of(),
            Map.of("workOrderSalesOrderIds", List.of("42", "99")));
        assertEquals(List.of(900L), simulation.simulate(noMapping, 3, null).items().get(0).affectedSalesOrderIds());
    }

    @Test
    void absentOrOutsideHorizonRequirementsAreUnknownNotNoRisk() {
        for (List<RequirementFact> requirements : List.of(List.<RequirementFact>of(),
            List.of(requirement(101L, TODAY.minusDays(1), "100", 11L),
                requirement(101L, TODAY.plusDays(7), "100", 11L)))) {
            ShortageSimulationResult result = simulation.simulate(snapshot(requirements,
                List.of(stock(101L, "0", "0", "0")), List.of(), null), 7, null);
            assertFalse(result.hasRisk());
            assertEquals(0, result.riskMaterialCount());
            assertTrue(result.items().isEmpty());
            assertNull(result.totalGap(), "无需求证据不能生成总缺口 0");
            assertNull(result.kpis());
            assertTrue(result.warnings().contains("NO_REQUIREMENT_DATA"));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("缺少需求证据，不能判定无风险")));
        }
    }

    @Test
    void isolatesScenarioFailureButRejectsInvalidOverrideGlobally() {
        PrdShortageScenarioService delegate = new PrdShortageScenarioService();
        PrdShortageScenarioService broken = mock(PrdShortageScenarioService.class);
        doAnswer(call -> {
            if (call.<com.nexus.erp.prd.schedule.PrdShortageInput>getArgument(0).getMaterialId() == 101L) {
                throw new IllegalStateException("service unavailable");
            }
            return delegate.simulate(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
        }).when(broken).simulate(any(), any(), any(), eq(TODAY));
        ErpDecisionShortageSimulation isolated = new ErpDecisionShortageSimulation(broken);
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "10", 11L), requirement(102L, TODAY, "4", 22L)),
            List.of(stock(101L, "0", "0", "0"), stock(102L, "1", "0", "0")),
            List.of(arrival(101L, 5001L, TODAY.plusDays(1), "10")), null);
        ShortageSimulationResult result = isolated.simulate(snapshot, 2, null);
        assertEquals(List.of(102L), result.items().stream().map(ShortageSimulationResult.Item::materialId).toList());
        assertTrue(result.hasRisk());
        assertEquals(new BigDecimal("3"), result.items().get(0).maxGap());
        assertNull(result.totalGap());
        assertNull(result.kpis());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("101") && w.contains("失败")));
        assertThrows(IllegalArgumentException.class, () -> isolated.simulate(snapshot, 2,
            Map.of("purchaseArrivalOverrides", Map.of("9999", "2026-09-24"))));
    }

    @Test
    void invalidArrivalFactsWarnButOverridesCannotTargetThemAndOutOfWindowIsNotActualArrival() {
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "3", 11L)),
            List.of(),
            List.of(arrival(101L, null, TODAY, "2"),
                arrival(101L, 5001L, null, "2"),
                arrival(101L, 5002L, TODAY.plusDays(10), "4")), null);
        ShortageSimulationResult result = simulation.simulate(snapshot, 2, null);
        assertEquals(new BigDecimal("3"), result.totalGap());
        assertEquals(new BigDecimal("4"), result.items().get(0).onOrderQty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("无库存")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("无效") && w.contains("采购")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("窗口外") && w.contains("非已到货")));
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(snapshot, 2,
            Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-09-24"))));
        ShortageSimulationResult expedited = simulation.simulate(snapshot, 2,
            Map.of("purchaseArrivalOverrides", Map.of("5002", "2026-09-24")));
        assertFalse(expedited.hasRisk());
        assertEquals(new BigDecimal("4"), expedited.items().get(0).onOrderQty());
        assertEquals(TODAY.plusDays(10), snapshot.arrivals().get(2).expectedDate());

    }

    @Test
    void evidenceItemsAndWarningsStableUnderReorderedInputs() {
        List<RequirementFact> demands = List.of(requirement(102L, TODAY, "4", 22L),
            requirement(101L, TODAY, "2", 11L));
        List<StockFact> stocks = List.of(stock(102L, "0", "0", "0"), stock(101L, "0", "0", "0"));
        List<ArrivalFact> arrivals = List.of(arrival(101L, 5002L, TODAY.plusDays(2), "1"),
            arrival(101L, 5001L, TODAY.plusDays(2), "1"));
        DeliveryContextSnapshot first = snapshot(demands, stocks, arrivals, null);
        List<RequirementFact> reversedDemands = new ArrayList<>(demands);
        List<StockFact> reversedStocks = new ArrayList<>(stocks);
        List<ArrivalFact> reversedArrivals = new ArrayList<>(arrivals);
        Collections.reverse(reversedDemands);
        Collections.reverse(reversedStocks);
        Collections.reverse(reversedArrivals);
        DeliveryContextSnapshot reversed = snapshot(reversedDemands, reversedStocks, reversedArrivals, null);
        ShortageSimulationResult one = simulation.simulate(first, 3, null);
        ShortageSimulationResult two = simulation.simulate(reversed, 3, null);
        assertEquals(one, two);
        assertEquals(List.of(101L, 102L), one.items().stream().map(ShortageSimulationResult.Item::materialId).toList());
        assertEquals(List.of("erp:requirement:101", "erp:purchaseLine:5001", "erp:purchaseLine:5002",
            "erp:requirement:102"), one.evidenceRefs());
        assertEquals(one.warnings().stream().distinct().sorted().toList(), one.warnings());
    }

    @Test
    void shortageHorizonRootMustHaveNullIdAndCanSimulateScopedRequirements() {
        DeliveryContextSnapshot base = fixture();
        DeliveryContextSnapshot horizon = snapshot(
            new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, "SHORTAGE_HORIZON"),
            SNAPSHOT_AT, "hash", base.requirements(), base.stocks(), base.arrivals(), null);
        assertEquals(new BigDecimal("10"), simulation.simulate(horizon, 1, null).totalGap());
        DeliveryContextSnapshot invalid = snapshot(
            new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, 1L, "SHORTAGE_HORIZON"),
            SNAPSHOT_AT, "hash", base.requirements(), base.stocks(), base.arrivals(), null);
        assertThrows(IllegalArgumentException.class, () -> simulation.simulate(invalid, 1, null));
    }

    @Test
    void salesOrderRootIsFallbackWhenRequirementHasNoWorkOrderId() {
        DeliveryContextSnapshot snapshot = snapshot(List.of(new RequirementFact(101L, "M-101", "物料-101",
            TODAY, new BigDecimal("2"), null, null)), List.of(), List.of(), null);
        ShortageSimulationResult result = simulation.simulate(snapshot, 1, null);
        assertEquals(List.of(900L), result.items().get(0).affectedSalesOrderIds());
        assertEquals(1, result.kpis().impactedOrderCount());
    }

    @Test
    void rejectsOverrideForMaterialWithoutHorizonDemandWhenNoRequirementsExist() {
        DeliveryContextSnapshot snapshot = snapshot(List.of(), List.of(),
            List.of(arrival(101L, 5001L, TODAY.plusDays(1), "4")), null);
        PrdShortageScenarioService service = mock(PrdShortageScenarioService.class);
        ErpDecisionShortageSimulation guarded = new ErpDecisionShortageSimulation(service);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> guarded.simulate(snapshot, 3,
                Map.of("purchaseArrivalOverrides", Map.of("5001", "2026-09-24"))));

        assertTrue(error.getMessage().contains("5001"));
        assertTrue(error.getMessage().contains("101"));
        verifyNoInteractions(service);
        ShortageSimulationResult baseline = simulation.simulate(snapshot, 3, null);
        assertTrue(baseline.warnings().contains("IGNORED_ARRIVAL_WITHOUT_DEMAND:101"));
    }

    @Test
    void rejectsOverrideForExistingLineOfMaterialOutsideDemandScopeBeforeAnySimulation() {
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "3", 11L),
                requirement(102L, TODAY.plusDays(4), "8", 22L)),
            List.of(stock(101L, "0", "0", "0")),
            List.of(arrival(101L, 5001L, TODAY.plusDays(1), "2"),
                arrival(102L, 5002L, TODAY.plusDays(1), "8")), null);
        PrdShortageScenarioService service = mock(PrdShortageScenarioService.class);
        ErpDecisionShortageSimulation guarded = new ErpDecisionShortageSimulation(service);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> guarded.simulate(snapshot, 3,
                Map.of("purchaseArrivalOverrides", Map.of("5002", "2026-09-24"))));

        assertTrue(error.getMessage().contains("5002"));
        assertTrue(error.getMessage().contains("102"));
        verifyNoInteractions(service);
        ShortageSimulationResult baseline = simulation.simulate(snapshot, 3, null);
        assertEquals(List.of(101L), baseline.items().stream().map(ShortageSimulationResult.Item::materialId).toList());
        assertEquals(new BigDecimal("2"), baseline.items().get(0).onOrderQty());
        assertTrue(baseline.warnings().contains("IGNORED_ARRIVAL_WITHOUT_DEMAND:102"));
    }

    @Test
    void allMaterialsFailingThrowsIncompleteAfterAttemptingEveryMaterial() {
        PrdShortageScenarioService service = mock(PrdShortageScenarioService.class);
        doAnswer(call -> { throw new IllegalStateException("simulator offline"); })
            .when(service).simulate(any(), any(), any(), eq(TODAY));
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "1", 11L), requirement(102L, TODAY, "2", 22L)),
            List.of(), List.of(), null);

        ServiceException failure = assertThrows(ServiceException.class,
            () -> new ErpDecisionShortageSimulation(service).simulate(snapshot, 1, null));

        assertTrue(failure.getMessage().startsWith("SHORTAGE_SIMULATION_INCOMPLETE"));
        verify(service, times(2)).simulate(any(), any(), any(), eq(TODAY));
    }

    @Test
    void failedMaterialWithOnlyNoRiskSuccessfulMaterialsThrowsIncomplete() {
        PrdShortageScenarioService delegate = new PrdShortageScenarioService();
        PrdShortageScenarioService service = mock(PrdShortageScenarioService.class);
        doAnswer(call -> {
            if (call.<com.nexus.erp.prd.schedule.PrdShortageInput>getArgument(0).getMaterialId() == 101L) {
                throw new IllegalStateException("simulator offline");
            }
            return delegate.simulate(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
        }).when(service).simulate(any(), any(), any(), eq(TODAY));
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "5", 11L), requirement(102L, TODAY, "1", 22L)),
            List.of(stock(102L, "2", "0", "0")), List.of(), null);

        ServiceException failure = assertThrows(ServiceException.class,
            () -> new ErpDecisionShortageSimulation(service).simulate(snapshot, 1, null));

        assertTrue(failure.getMessage().startsWith("SHORTAGE_SIMULATION_INCOMPLETE"));
        verify(service, times(2)).simulate(any(), any(), any(), eq(TODAY));
    }

    @Test
    void preservesRealSimulatorAdjustSafetyWhenBalanceExactlyMeetsSafetyLine() {
        DeliveryContextSnapshot snapshot = snapshot(
            List.of(requirement(101L, TODAY, "5", 11L)),
            List.of(stock(101L, "8", "0", "3")), List.of(), null);

        ShortageSimulationResult result = simulation.simulate(snapshot, 1, null);

        assertFalse(result.hasRisk());
        assertNull(result.items().get(0).firstShortageDate());
        assertEquals(BigDecimal.ZERO, result.items().get(0).maxGap());
        assertEquals("ADJUST_SAFETY", result.items().get(0).recommendationCode());
    }

    private static DeliveryContextSnapshot fixture() {
        return snapshot(List.of(requirement(101L, TODAY, "10", 11L)),
            List.of(stock(101L, "0", "0", "0")),
            List.of(arrival(101L, 5001L, TODAY.plusDays(2), "5")), null);
    }

    private static RequirementFact requirement(Long materialId, LocalDate date, String qty, Long workOrderId) {
        return new RequirementFact(materialId, "M-" + materialId, "物料-" + materialId,
            date, new BigDecimal(qty), workOrderId, "WO-" + workOrderId);
    }

    private static StockFact stock(Long materialId, String qty, String reserved, String safety) {
        return new StockFact(materialId, new BigDecimal(qty), new BigDecimal(reserved), new BigDecimal(safety));
    }

    private static ArrivalFact arrival(Long materialId, Long lineId, LocalDate date, String qty) {
        return new ArrivalFact(materialId, lineId, date, new BigDecimal(qty), 50L);
    }

    private static DeliveryContextSnapshot snapshot(List<RequirementFact> requirements, List<StockFact> stocks,
                                                    List<ArrivalFact> arrivals, Map<String, Object> schedule) {
        return snapshot(new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 900L, "SO-900"),
            SNAPSHOT_AT, "hash", requirements, stocks, arrivals, schedule);
    }

    private static DeliveryContextSnapshot snapshot(DecisionBusinessRef ref, LocalDateTime at, String hash,
                                                    List<RequirementFact> requirements, List<StockFact> stocks,
                                                    List<ArrivalFact> arrivals, Map<String, Object> schedule) {
        return new DeliveryContextSnapshot(ref, List.of(), List.of(), Map.of(), requirements, stocks,
            arrivals, Map.of(), List.of(), schedule, List.of(), hash, at, List.of("snapshot notice", "snapshot notice"));
    }
}
