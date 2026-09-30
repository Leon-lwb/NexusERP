package com.nexus.ai.service.decision;

import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.CalculationResult;
import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.FrozenRun;
import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.GapFact;
import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.RequirementRow;
import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.ScenarioFacts;
import com.nexus.ai.service.decision.DecisionShortageRatioCalculator.ScenarioRatio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionShortageRatioCalculatorTest {
    private static final String RUN = "run-101";
    private static final LocalDate START = LocalDate.of(2026, 9, 30);
    private static final LocalDate END = START.plusDays(6);
    private final DecisionShortageRatioCalculator calculator = new DecisionShortageRatioCalculator();

    @Test
    void threeScenariosUsePerMaterialRatiosAndAllCandidatesForNormalization() {
        CalculationResult result = calculator.calculate(run(row("A", "1", "10", "kg"), row("B", "2", "20", "piece")), List.of(
            scenario("S1", gap("A", "5", "kg"), gap("B", "0", "piece")),
            scenario("S2", gap("A", "0", "kg"), gap("B", "10", "piece")),
            scenario("S3", gap("A", "0", "kg"), gap("B", "0", "piece"))));
        assertEquals(new BigDecimal("0.25000000"), result.scenarios().get(0).rawRatio());
        assertEquals(new BigDecimal("0.25000000"), result.scenarios().get(1).rawRatio());
        assertEquals(new BigDecimal("0E-8"), result.scenarios().get(2).rawRatio());
        assertEquals(new BigDecimal("1.00000000"), result.scenarios().get(0).normalizedRatio());
        assertEquals(new BigDecimal("1.00000000"), result.scenarios().get(1).normalizedRatio());
        assertEquals(new BigDecimal("0E-8"), result.scenarios().get(2).normalizedRatio());
        assertEquals(new BigDecimal("0.50000000"), result.scenarios().get(0).materials().get("A").ratio());
        assertEquals(new BigDecimal("0E-8"), result.scenarios().get(0).materials().get("B").ratio());
        assertEquals("ERP:SIM:S1:A", result.scenarios().get(0).materials().get("A").sourceEvidenceKey());
    }

    @Test
    void soleCandidateHasZeroNormalizedDifferenceButPreservesRawRisk() {
        ScenarioRatio ratio = calculator.calculate(run(row("A", "1", "10", "kg"), row("B", "2", "20", "piece")),
            List.of(scenario("S1", gap("A", "5", "kg"), gap("B", "0", "piece")))).scenarios().get(0);
        assertEquals(new BigDecimal("0.25000000"), ratio.rawRatio());
        assertEquals(new BigDecimal("0E-8"), ratio.normalizedRatio());
    }

    @Test
    void equalRiskCandidatesDoNotEraseEitherRawRatio() {
        List<ScenarioRatio> ratios = calculator.calculate(run(row("A", "1", "10", "kg"), row("B", "2", "20", "piece")),
            List.of(scenario("S1", gap("A", "5", "kg"), gap("B", "0", "piece")),
                scenario("S2", gap("A", "0", "kg"), gap("B", "10", "piece")))).scenarios();
        for (ScenarioRatio ratio : ratios) {
            assertEquals(new BigDecimal("0.25000000"), ratio.rawRatio());
            assertEquals(new BigDecimal("0E-8"), ratio.normalizedRatio());
        }
    }

    @Test
    void multipleFrozenDetailsSumAsNetDemandWithoutSubtractingSupplyAgain() {
        CalculationResult result = calculator.calculate(run(row("A", "1", "4", "kg"), row("A", "2", "6", "kg")),
            List.of(scenario("S1", gap("A", "5", "kg"))));
        assertEquals(new BigDecimal("10"), result.demands().get("A").requiredQty());
        assertEquals(List.of("1", "2"), result.demands().get("A").detailIds());
        assertEquals(new BigDecimal("0.50000000"), result.scenarios().get(0).rawRatio());
    }

    @Test
    void decimalDivisionUsesScaleEightHalfUpWithoutDouble() {
        assertEquals(new BigDecimal("0.00195313"), calculator.calculate(run(row("A", "1", "512", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")))).scenarios().get(0).rawRatio());
    }

    @Test
    void materialRatiosAreRoundedBeforeTakingTheirEqualWeightMean() {
        CalculationResult result = calculator.calculate(run(row("A", "1", "200000000", "kg"),
            row("B", "2", "1", "piece")), List.of(scenario("S1", gap("A", "1", "kg"), gap("B", "0", "piece"))));
        assertEquals(new BigDecimal("0.00000001"), result.scenarios().get(0).materials().get("A").ratio());
        assertEquals(new BigDecimal("0.00000001"), result.scenarios().get(0).rawRatio());
    }

    @Test
    void veryLargeDecimalDemandDoesNotOverflowOrLoseRatioPrecision() {
        assertEquals(new BigDecimal("0.33333333"), calculator.calculate(
            run(row("A", "1", "9007199254740993.00000000", "kg")),
            List.of(scenario("S1", gap("A", "3002399751580331.00000000", "kg"))))
            .scenarios().get(0).rawRatio());
    }

    @Test
    void equallyWeightedMaterialsAreNotWeightedByQuantityOrMixedPhysicalUnits() {
        CalculationResult result = calculator.calculate(run(row("A", "1", "2", "kg"), row("B", "2", "100", "piece")),
            List.of(scenario("S1", gap("A", "1", "kg"), gap("B", "1", "piece"))));
        assertEquals(new BigDecimal("0.25500000"), result.scenarios().get(0).rawRatio());
        assertEquals("kg", result.demands().get("A").baseUomId());
        assertEquals("piece", result.demands().get("B").baseUomId());
        assertEquals(2, result.demands().size()); /** 不提供跨物料、跨单位的总量。 */
    }

    @Test
    void allExplicitZeroGapFactsPreserveEvidenceAndProduceZeroRisk() {
        ScenarioRatio ratio = calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "0", "kg")))).scenarios().get(0);
        assertEquals(new BigDecimal("0E-8"), ratio.rawRatio());
        assertEquals("ERP:SIM:S1:A", ratio.materials().get("A").sourceEvidenceKey());
    }

    @Test
    void emptyDemandRequiresCompleteSnapshotAndSimulationEvidence() {
        CalculationResult result = calculator.calculate(run(), List.of(scenario("S1")));
        assertEquals("ERP:Q:run-101:complete", result.snapshotEvidenceKey());
        assertTrue(result.demands().isEmpty());
        assertEquals(new BigDecimal("0E-8"), result.scenarios().get(0).rawRatio());
        assertEquals("ERP:SIM:S1:complete", result.scenarios().get(0).simulationEvidenceKey());
    }

    @Test
    void emptyDemandWithoutSnapshotEvidenceFailsClosed() {
        denied(() -> calculator.calculate(new FrozenRun(RUN, START, END, List.of(), null), List.of(scenario("S1"))));
    }

    @Test
    void emptyDemandWithoutScenarioProofFailsClosed() {
        denied(() -> calculator.calculate(run(), List.of(new ScenarioFacts(RUN, "S1", START, END, List.of(), ""))));
    }

    @Test
    void nonEmptyDemandWithMissingMaterialItemCannotBeImplicitZero() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), List.of(scenario("S1"))));
    }

    @Test
    void scenarioWithExtraMaterialDoesNotChangeTheFrozenCandidateMaterialSet() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg"), gap("B", "0", "piece")))));
    }

    @Test
    void oneIncompleteScenarioFailsTheWholeCandidateSetRatherThanDroppingIt() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")), scenario("S2"))));
    }

    @Test
    void zeroDemandWithNonzeroShortageIsUnknownNotZero() {
        denied(() -> calculator.calculate(run(), List.of(scenario("S1", gap("A", "1", "kg")))));
    }

    @Test
    void missingGapEvidenceIsRejectedEvenWhenGapIsZero() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), List.of(scenario("S1",
            new GapFact("A", BigDecimal.ZERO, "kg", null, "")))));
    }

    @Test
    void missingUomAndMismatchedUomAreBothRejected() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "g")))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg"), row("A", "2", "10", "g")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
    }

    @Test
    void nonzeroGapNeedsAnObservedPeakDateInTheFrozenWindow() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), List.of(scenario("S1",
            new GapFact("A", BigDecimal.ONE, "kg", null, "ERP:SIM:S1:A")))));
    }

    @Test
    void requirementIdentityDateAndQuantityCannotBeInferred() {
        denied(() -> calculator.calculate(run(new RequirementRow("A", "", START, BigDecimal.ONE, "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
        denied(() -> calculator.calculate(run(new RequirementRow("A", "1", null, BigDecimal.ONE, "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
        denied(() -> calculator.calculate(run(new RequirementRow("A", "1", START, null, "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
    }

    @Test
    void gapLargerThanSameMaterialDemandIsUnknownEvenWithValidUnits() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "10.00001", "kg")))));
    }

    @Test
    void negativeGapAndNonPositiveDemandAreRejected() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "-1", "kg")))));
        denied(() -> calculator.calculate(run(row("A", "1", "0", "kg")), List.of(scenario("S1"))));
        denied(() -> calculator.calculate(run(row("A", "1", "-1", "kg")), List.of(scenario("S1"))));
    }

    @Test
    void duplicateDetailAndScenarioAndMaterialItemsAreRejected() {
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg"), row("B", "1", "10", "piece")),
            List.of(scenario("S1", gap("A", "1", "kg"), gap("B", "0", "piece")))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")), scenario("S1", gap("A", "1", "kg")))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg"), gap("A", "1", "kg")))));
    }

    @Test
    void datesAndRunIdentityAreFrozenAcrossAllScenarios() {
        denied(() -> calculator.calculate(run(rowAt("A", "1", END.plusDays(1), "10", "kg")),
            List.of(scenario("S1", gap("A", "1", "kg")))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(new ScenarioFacts("other-run", "S1", START, END, List.of(gap("A", "1", "kg")), "ERP:SIM:S1"))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")),
            List.of(new ScenarioFacts(RUN, "S1", START.plusDays(1), END, List.of(gap("A", "1", "kg")), "ERP:SIM:S1"))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), List.of(scenario("S1",
            new GapFact("A", BigDecimal.ONE, "kg", END.plusDays(1), "ERP:SIM:S1:A")))));
    }

    @Test
    void missingOrInvalidFrozenInputsFailClosed() {
        denied(() -> calculator.calculate(new FrozenRun(RUN, START, END, null, "ERP:Q:complete"), List.of(scenario("S1"))));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), List.of()));
        denied(() -> calculator.calculate(run(row("A", "1", "10", "kg")), null));
        denied(() -> calculator.calculate(new FrozenRun(RUN, END, START, List.of(), "ERP:Q:complete"),
            List.of(scenario("S1"))));
    }

    @Test
    void inputsAreDefensivelyFrozenAndOutputCollectionsCannotBeMutated() {
        List<RequirementRow> rows = new ArrayList<>(List.of(row("A", "1", "10", "kg")));
        List<GapFact> gaps = new ArrayList<>(List.of(gap("A", "5", "kg")));
        FrozenRun run = new FrozenRun(RUN, START, END, rows, "ERP:Q:complete");
        ScenarioFacts scenario = new ScenarioFacts(RUN, "S1", START, END, gaps, "ERP:SIM:S1:complete");
        rows.clear();
        gaps.clear();
        CalculationResult result = calculator.calculate(run, List.of(scenario));
        assertEquals(new BigDecimal("0.50000000"), result.scenarios().get(0).rawRatio());
        assertThrows(UnsupportedOperationException.class, () -> run.requirements().clear());
        assertThrows(UnsupportedOperationException.class, () -> scenario.gaps().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.demands().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.scenarios().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.scenarios().get(0).materials().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.demands().get("A").detailIds().clear());
    }

    private static FrozenRun run(RequirementRow... rows) {
        return new FrozenRun(RUN, START, END, List.of(rows), "ERP:Q:run-101:complete");
    }

    private static RequirementRow row(String material, String detail, String qty, String unit) {
        return rowAt(material, detail, START, qty, unit);
    }

    private static RequirementRow rowAt(String material, String detail, LocalDate date, String qty, String unit) {
        return new RequirementRow(material, detail, date, new BigDecimal(qty), unit);
    }

    private static ScenarioFacts scenario(String code, GapFact... gaps) {
        return new ScenarioFacts(RUN, code, START, END, List.of(gaps), "ERP:SIM:" + code + ":complete");
    }

    private static GapFact gap(String material, String qty, String unit) {
        return new GapFact(material, new BigDecimal(qty), unit,
            new BigDecimal(qty).signum() == 0 ? null : START, "ERP:SIM:S1:" + material);
    }

    private static void denied(Runnable calculation) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, calculation::run);
        assertTrue(failure.getMessage().contains("MISSING_SOURCE_OR_UNIT"), failure::getMessage);
    }
}