package com.nexus.erp.prd.schedule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrdShortageSimulator 推演引擎单元测试: 无缺口、恰好踩线、在途迟到导致缺口、多批次需求、批量排序。
 */
@Tag("dev")
class PrdShortageSimulatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 2);
    private static final LocalDate D1 = LocalDate.of(2026, 9, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 20);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 28);

    private PrdShortageInput input(String code, BigDecimal stock, BigDecimal safety) {
        PrdShortageInput input = new PrdShortageInput();
        input.setMaterialId(1L);
        input.setMaterialCode(code);
        input.setMaterialName("物料" + code);
        input.setStockQty(stock);
        input.setSafetyStock(safety);
        return input;
    }

    @Test
    void noShortageWhenStockCoversAll() {
        PrdShortageInput in = input("M1", bd("100"), bd("10"));
        in.addDemand(D1, bd("50"));
        in.addDemand(D2, bd("30"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertFalse(r.hasRisk());
        assertNull(r.getEarliestShortageDate());
        assertEquals(bd("80"), r.getTotalDemand());
        assertTrue(r.getSuggestionText().contains("无缺料风险"));
    }

    @Test
    void exactlyOnSafetyLine() {
        // 可用(库存-安全) 90, 需求 90 → 余额恰为 0(不判缺口, 建议复核安全库存)
        PrdShortageInput in = input("M2", bd("100"), bd("10"));
        in.addDemand(D1, bd("90"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertFalse(r.hasRisk());
        assertEquals(PrdShortageSimulator.SUGGEST_ADJUST_SAFETY, r.getSuggestion());
    }

    @Test
    void lateArrivalCausesShortageAndUrges() {
        // 库存可用 50, 9/10 需求 80 → 缺 30; 9/20 到货 100(可覆盖) → 建议催货
        PrdShortageInput in = input("M3", bd("60"), bd("10"));
        in.addDemand(D1, bd("80"));
        in.addArrival(D2, bd("100"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertTrue(r.hasRisk());
        assertEquals(D1, r.getEarliestShortageDate());
        assertEquals(bd("30"), r.getMaxGap());
        assertEquals(PrdShortageSimulator.SUGGEST_URGE, r.getSuggestion());
        assertEquals(bd("100"), r.getOnOrderQty());
    }

    @Test
    void noArrivalSuggestsPurchase() {
        // 无在途, 净缺口 = 需求 − 可用 = 70
        PrdShortageInput in = input("M4", bd("30"), bd("0"));
        in.addDemand(D1, bd("100"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertTrue(r.hasRisk());
        assertEquals(D1, r.getEarliestShortageDate());
        assertEquals(bd("70"), r.getMaxGap());
        assertEquals(PrdShortageSimulator.SUGGEST_PURCHASE, r.getSuggestion());
        assertTrue(r.getSuggestionText().contains("70"));
    }

    @Test
    void multiBatchDemandDeepensGap() {
        // 可用 20: 9/10 需求 50(缺30), 9/20 到货 20(仍缺10), 9/28 需求 30(缺40=最大缺口)
        PrdShortageInput in = input("M5", bd("20"), bd("0"));
        in.addDemand(D1, bd("50"));
        in.addArrival(D2, bd("20"));
        in.addDemand(D3, bd("30"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertTrue(r.hasRisk());
        assertEquals(D1, r.getEarliestShortageDate());
        assertEquals(bd("40"), r.getMaxGap());
        assertEquals(3, r.getGapDays());
        assertEquals(PrdShortageSimulator.SUGGEST_PURCHASE, r.getSuggestion());
    }

    @Test
    void arrivalBeforeDemandPreventsShortage() {
        // 到货 9/5 先于需求 9/10: 不应缺料
        PrdShortageInput in = input("M6", bd("10"), bd("0"));
        in.addArrival(LocalDate.of(2026, 9, 5), bd("100"));
        in.addDemand(D1, bd("80"));
        PrdShortageResult r = PrdShortageSimulator.simulate(in, TODAY);
        assertFalse(r.hasRisk());
    }

    @Test
    void simulateAllSortsByEarliestShortageDate() {
        PrdShortageInput noRisk = input("A", bd("100"), bd("0"));
        noRisk.addDemand(D1, bd("10"));

        PrdShortageInput late = input("B", bd("0"), bd("0"));
        late.addDemand(D3, bd("10"));

        PrdShortageInput early = input("C", bd("0"), bd("0"));
        early.addDemand(D1, bd("10"));

        List<PrdShortageResult> results = PrdShortageSimulator.simulateAll(
            List.of(noRisk, late, early), TODAY);
        assertEquals("C", results.get(0).getMaterialCode());
        assertEquals("B", results.get(1).getMaterialCode());
        assertEquals("A", results.get(2).getMaterialCode());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
