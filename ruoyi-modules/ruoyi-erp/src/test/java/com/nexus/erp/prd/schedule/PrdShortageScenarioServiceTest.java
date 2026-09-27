package com.nexus.erp.prd.schedule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缺料场景服务单元测试：采购行覆盖、输入隔离与 校验失败即拒绝 校验。
 */
@Tag("dev")
class PrdShortageScenarioServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final LocalDate DEMAND_DATE = LocalDate.of(2026, 9, 26);
    private static final LocalDate FIRST_ARRIVAL_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate SECOND_ARRIVAL_DATE = LocalDate.of(2026, 10, 1);
    private static final LocalDate EXPEDITED_DATE = LocalDate.of(2026, 9, 24);

    private final PrdShortageScenarioService scenarioService = new PrdShortageScenarioService();

    @Test
    void selectedLineOverrideEliminatesShortageWithoutMutatingBaseInput() {
        PrdShortageInput input = createBaseInput(BigDecimal.TEN);
        List<PurchaseArrivalLine> lines = purchaseLines();
        TreeMap<LocalDate, BigDecimal> originalDemands = new TreeMap<>(input.getDemands());
        TreeMap<LocalDate, BigDecimal> originalArrivals = new TreeMap<>(input.getArrivals());

        PrdShortageResult baseline = scenarioService.simulate(input, lines, Map.of(), TODAY);
        PrdShortageResult expedited = scenarioService.simulate(
            input, lines, Map.of(5001L, EXPEDITED_DATE), TODAY);

        assertAll(
            () -> assertEquals(DEMAND_DATE, baseline.getEarliestShortageDate()),
            () -> assertNull(expedited.getEarliestShortageDate()),
            () -> assertEquals(BigDecimal.valueOf(14), expedited.getOnOrderQty()),
            () -> assertEquals(100L, input.getMaterialId()),
            () -> assertEquals("M-100", input.getMaterialCode()),
            () -> assertEquals("测试物料", input.getMaterialName()),
            () -> assertEquals(BigDecimal.ZERO, input.getStockQty()),
            () -> assertEquals(BigDecimal.ZERO, input.getSafetyStock()),
            () -> assertEquals(originalDemands, input.getDemands()),
            () -> assertEquals(originalArrivals, input.getArrivals())
        );
    }

    @Test
    void onlySelectedPurchaseLineMovesWhileOtherLineKeepsOriginalDate() {
        PrdShortageInput input = createBaseInput(BigDecimal.valueOf(12));

        PrdShortageResult result = scenarioService.simulate(
            input, purchaseLines(), Map.of(5001L, EXPEDITED_DATE), TODAY);

        assertAll(
            () -> assertEquals(DEMAND_DATE, result.getEarliestShortageDate()),
            () -> assertEquals(BigDecimal.valueOf(2), result.getMaxGap()),
            () -> assertEquals(1L, result.getGapDays()),
            () -> assertEquals(BigDecimal.valueOf(14), result.getOnOrderQty())
        );
    }

    @Test
    void rejectsOverridesWithoutPurchaseLineFacts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(
                createBaseInput(BigDecimal.TEN), List.of(), Map.of(5001L, EXPEDITED_DATE), TODAY));

        assertTrue(exception.getMessage().contains("采购行到货覆盖缺少原始采购行数据"));
    }

    @Test
    void rejectsUnknownOverridePurchaseLineId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(
                createBaseInput(BigDecimal.TEN), purchaseLines(), Map.of(9999L, EXPEDITED_DATE), TODAY));

        assertAll(
            () -> assertTrue(exception.getMessage().contains("未知采购行")),
            () -> assertTrue(exception.getMessage().contains("9999"))
        );
    }

    @Test
    void rejectsNullAndDuplicatePurchaseLineIds() {
        PurchaseArrivalLine nullId = new PurchaseArrivalLine(null, FIRST_ARRIVAL_DATE, BigDecimal.ONE);
        IllegalArgumentException nullIdException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), List.of(nullId), Map.of(), TODAY));

        List<PurchaseArrivalLine> duplicateIds = List.of(
            new PurchaseArrivalLine(5001L, FIRST_ARRIVAL_DATE, BigDecimal.ONE),
            new PurchaseArrivalLine(5001L, SECOND_ARRIVAL_DATE, BigDecimal.ONE)
        );
        IllegalArgumentException duplicateIdException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), duplicateIds, Map.of(), TODAY));

        assertAll(
            () -> assertTrue(nullIdException.getMessage().contains("采购行 ID")),
            () -> assertTrue(nullIdException.getMessage().contains("为空")),
            () -> assertTrue(duplicateIdException.getMessage().contains("重复")),
            () -> assertTrue(duplicateIdException.getMessage().contains("5001"))
        );
    }

    @Test
    void rejectsInvalidOriginalDateAndRemainingQuantity() {
        IllegalArgumentException nullDateException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), List.of(
                new PurchaseArrivalLine(5001L, null, BigDecimal.ONE)), Map.of(), TODAY));
        IllegalArgumentException nullQtyException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), List.of(
                new PurchaseArrivalLine(5001L, FIRST_ARRIVAL_DATE, null)), Map.of(), TODAY));
        IllegalArgumentException zeroQtyException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), List.of(
                new PurchaseArrivalLine(5001L, FIRST_ARRIVAL_DATE, BigDecimal.ZERO)), Map.of(), TODAY));
        IllegalArgumentException negativeQtyException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(createBaseInput(BigDecimal.TEN), List.of(
                new PurchaseArrivalLine(5001L, FIRST_ARRIVAL_DATE, BigDecimal.valueOf(-1))), Map.of(), TODAY));

        assertAll(
            () -> assertTrue(nullDateException.getMessage().contains("原始到货日")),
            () -> assertTrue(nullQtyException.getMessage().contains("剩余数量")),
            () -> assertTrue(zeroQtyException.getMessage().contains("必须为正数")),
            () -> assertTrue(negativeQtyException.getMessage().contains("必须为正数"))
        );
    }

    @Test
    void rejectsNullArgumentsAtServiceBoundary() {
        PrdShortageInput input = createBaseInput(BigDecimal.TEN);
        List<PurchaseArrivalLine> lines = purchaseLines();

        IllegalArgumentException baseException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(null, lines, Map.of(), TODAY));
        IllegalArgumentException linesException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(input, null, Map.of(), TODAY));
        IllegalArgumentException overridesException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(input, lines, null, TODAY));
        IllegalArgumentException todayException = assertThrows(IllegalArgumentException.class,
            () -> scenarioService.simulate(input, lines, Map.of(), null));

        assertAll(
            () -> assertTrue(baseException.getMessage().contains("base")),
            () -> assertTrue(linesException.getMessage().contains("purchaseLines")),
            () -> assertTrue(overridesException.getMessage().contains("arrivalOverrides")),
            () -> assertTrue(todayException.getMessage().contains("today"))
        );
    }

    @Test
    void emptyPurchaseLinesCopyAggregatedArrivalsAndMatchDirectSimulator() {
        PrdShortageInput input = createBaseInput(BigDecimal.TEN);

        PrdShortageResult direct = PrdShortageSimulator.simulate(input, TODAY);
        PrdShortageResult scenario = scenarioService.simulate(input, List.of(), Map.of(), TODAY);

        assertEquals(direct, scenario);
    }

    private static PrdShortageInput createBaseInput(BigDecimal demandQty) {
        PrdShortageInput input = new PrdShortageInput();
        input.setMaterialId(100L);
        input.setMaterialCode("M-100");
        input.setMaterialName("测试物料");
        input.setStockQty(BigDecimal.ZERO);
        input.setSafetyStock(BigDecimal.ZERO);
        input.addDemand(DEMAND_DATE, demandQty);
        input.addArrival(FIRST_ARRIVAL_DATE, BigDecimal.TEN);
        input.addArrival(SECOND_ARRIVAL_DATE, BigDecimal.valueOf(4));
        return input;
    }

    private static List<PurchaseArrivalLine> purchaseLines() {
        return List.of(
            new PurchaseArrivalLine(5001L, FIRST_ARRIVAL_DATE, BigDecimal.TEN),
            new PurchaseArrivalLine(5002L, SECOND_ARRIVAL_DATE, BigDecimal.valueOf(4))
        );
    }
}
