package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionShortageBasis;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 即使日期和物料相同，各 MRP 明细行仍须保留独立的数值来源。 */
class DecisionSourceFactVerifierTest {
    @Test
    void twoDemandDetailRowsOnSameWorkOrderMaterialAndDateHaveDistinctEvidenceKeys() {
        LocalDate date=LocalDate.of(2026,10,9);
        var first=new RequirementFact(101L,140L,"MAT140","钢材",date,new BigDecimal("6.51"),11L,"SCGD202609280001");
        var second=new RequirementFact(102L,140L,"MAT140","钢材",date,new BigDecimal("8.14"),11L,"SCGD202609280001");
        var evidence=new DecisionSourceFactVerifier().numericFacts(900L,snapshot(List.of(first,second)));
        assertEquals(2,evidence.size());
        assertEquals(List.of("ERP:REQUIREMENT:101:required_qty","ERP:REQUIREMENT:102:required_qty"),
            evidence.stream().map(item -> item.evidenceKey()).toList());
        assertEquals(List.of(101L,102L),evidence.stream().map(item -> item.sourceId()).toList());
        assertEquals(new BigDecimal("14.65"),evidence.stream().map(item -> new BigDecimal(item.value()))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void sameDetailIdWithContradictoryAmountsStillFailsClosed() {
        LocalDate date=LocalDate.of(2026,10,9);
        var first=new RequirementFact(101L,140L,"MAT140","钢材",date,new BigDecimal("6.51"),11L,"SCGD202609280001");
        var inconsistent=new RequirementFact(101L,140L,"MAT140","钢材",date,new BigDecimal("8.14"),11L,"SCGD202609280001");
        var denied=assertThrows(ServiceException.class,() -> new DecisionSourceFactVerifier()
            .numericFacts(900L,snapshot(List.of(first,inconsistent))));
        assertTrue(denied.getMessage().contains("重复"), "重复来源必须在数值去重前被拒绝");
    }

    @Test
    void duplicateSameDetailIdAndQuantityIsRejectedBeforeDownstreamDemandDoubleCounting() {
        LocalDate day=LocalDate.of(2026,10,9);
        var original=new RequirementFact(101L,140L,"MAT140","钢材",day,new BigDecimal("6.51"),11L,"SCGD202609280001");
        var repeated=new RequirementFact(101L,140L,"MAT140","钢材",day,new BigDecimal("6.51"),11L,"SCGD202609280001");
        var rejected=assertThrows(ServiceException.class,() -> new DecisionSourceFactVerifier()
            .numericFacts(900L,snapshot(List.of(original,repeated))));
        assertTrue(rejected.getMessage().contains("重复"));
    }

    @Test
    void mixedLegacyAndIdentifiedDemandRowsCannotDoubleCountOnePhysicalRequirement() {
        LocalDate day=LocalDate.of(2026,10,9);
        var unidentified=new RequirementFact(140L,"MAT140","钢材",day,new BigDecimal("6.51"),
            11L,"SCGD202609280001");
        var identified=new RequirementFact(101L,140L,"MAT140","钢材",day,new BigDecimal("6.51"),
            11L,"SCGD202609280001");
        var rejected=assertThrows(ServiceException.class,() -> new DecisionSourceFactVerifier()
            .numericFacts(900L,snapshot(List.of(unidentified,identified))));
        assertTrue(rejected.getMessage().contains("混") || rejected.getMessage().contains("来源"));
    }


    @Test
    void claimedShortageUnitCannotPromoteLegacySnapshotWithoutUnitProof() {
        var day = LocalDate.of(2026, 10, 9);
        var stored = snapshot(List.of(new RequirementFact(101L, 140L, "MAT140", "钢材", day,
            BigDecimal.TEN, 11L, "SCGD202609280001")));
        var basis =
                new DecisionShortageBasis(
                        "900",
                        "snapshot:" + stored.snapshotHash(),
                        stored.snapshotAt().toLocalDate(),
                        day,
                        List.of(
                                new DecisionShortageBasis.Requirement(
                                        101L, 140L, day, BigDecimal.TEN, 1L)),
                        List.of(
                                new DecisionShortageBasis.Gap(
                                        140L,
                                        1L,
                                        BigDecimal.ZERO,
                                        null,
                                        "DERIVED:SHORTAGE:APS_CR_140:max_gap")),
                        "DERIVED:SHORTAGE:APS_CR:complete");
        var kpi =
                new DecisionKpiSet(
                        0,
                        0,
                        0,
                        0,
                        null,
                        0,
                        BigDecimal.ZERO,
                        null,
                        BigDecimal.ZERO,
                        0,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        basis);
        var verifier = new DecisionSourceFactVerifier();
        var result =
                new DecisionResult(
                        900L,
                        stored.rootBusinessRef(),
                        0,
                        null,
                        "HIGH",
                        List.of(),
                        kpi,
                        List.of(),
                        null,
                        Map.of(),
                        verifier.numericFacts(900L, stored),
                        List.of(),
                        List.of());
        var denied = assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored, 10));
        assertTrue(denied.getMessage().contains("单位"));
    }


    @Test
    void basisCannotCropFrozenWindowToHideAConfirmedLaterDemand() {
        var start = LocalDate.of(2026, 9, 30);
        var stored = snapshot(List.of(new RequirementFact(101L, 140L, "MAT140", "钢材", start.plusDays(10),
            BigDecimal.TEN, 11L, "SCGD202609280001", 1L)));
        var basis =
                new DecisionShortageBasis(
                        "900",
                        "snapshot:" + stored.snapshotHash(),
                        start,
                        start,
                        List.of(),
                        List.of(),
                        "DERIVED:SHORTAGE:APS_CR:complete");
        var k =
                new DecisionKpiSet(
                        0,
                        0,
                        0,
                        0,
                        null,
                        0,
                        BigDecimal.ZERO,
                        null,
                        BigDecimal.ZERO,
                        0,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        basis);
        var verifier = new DecisionSourceFactVerifier();
        var result =
                new DecisionResult(
                        900L,
                        stored.rootBusinessRef(),
                        0,
                        null,
                        "HIGH",
                        List.of(),
                        k,
                        List.of(),
                        null,
                        Map.of(),
                        verifier.numericFacts(900L, stored),
                        List.of(),
                        List.of());
        assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored),
            "缺少服务端冻结窗口时，不能信任被验证对象自行裁剪出的空来源集合");
    }

    @Test
    void fullFrozenWindowAcceptsTrustedRatioAndLaterRereadClock() {
        var stored = confirmedDemandSnapshot();
        var start = stored.snapshotAt().toLocalDate();
        var k = ratioKpi(stored, start, start.plusDays(29), true);
        var verifier = new DecisionSourceFactVerifier();
        var result = ratioResult(stored, k);
        var reread = new DeliveryContextSnapshot(stored.rootBusinessRef(), stored.salesOrders(), stored.workOrders(),
            stored.routingByMaterial(), stored.requirements(), stored.stocks(), stored.arrivals(),
            stored.capacityByDate(), stored.holidays(), stored.currentSchedule(), stored.supplierMetrics(),
            stored.snapshotHash(), stored.snapshotAt().plusDays(1), stored.warnings());
        assertDoesNotThrow(() -> verifier.assertShortageBasis(900L, k, stored, 30));
        assertDoesNotThrow(() -> verifier.assertProvenance(result, stored, 30));
        assertDoesNotThrow(() -> verifier.assertProvenance(result, stored, reread, 30));
        assertEquals(new BigDecimal("0.5"), result.baselineKpis().shortageGapRatio());
        assertSame(k, result.baselineKpis(), "来源核验不得改写已冻结 KPI");
    }

    @Test
    void frozenWindowRejectsCroppingExpansionAndShiftedStart() {
        var stored = confirmedDemandSnapshot();
        var start = stored.snapshotAt().toLocalDate();
        var verifier = new DecisionSourceFactVerifier();
        for (var k : List.of(ratioKpi(stored, start, start, false),
            ratioKpi(stored, start, start.plusDays(30), true),
            ratioKpi(stored, start.minusDays(1), start.plusDays(28), true))) {
            var result = ratioResult(stored, k);
            var failure = assertThrows(ServiceException.class, () -> verifier.assertShortageBasis(900L, k, stored, 30));
            assertTrue(failure.getMessage().contains("窗口"));
            assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored, 30));
            assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored, stored, 30));
        }
    }

    @Test
    void allLegacyOverloadsRejectNewRatioWithoutTrustedHorizon() {
        var stored = confirmedDemandSnapshot();
        var start = stored.snapshotAt().toLocalDate();
        var k = ratioKpi(stored, start, start.plusDays(29), true);
        var result = ratioResult(stored, k);
        var verifier = new DecisionSourceFactVerifier();
        assertThrows(ServiceException.class, () -> verifier.assertShortageBasis(900L, k, stored));
        assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored));
        assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, stored, stored));
        for (int horizon : new int[] {0, -1, 91}) {
            assertThrows(ServiceException.class, () -> verifier.assertShortageBasis(900L, k, stored, horizon));
        }
    }

    @Test
    void legacyKpiWithoutRatioKeepsItsOriginalConclusionWithoutHorizon() {
        var stored = confirmedDemandSnapshot();
        var k = new DecisionKpiSet(0, 0, 0, 0, null, 1, BigDecimal.TEN, null,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        var result = ratioResult(stored, k);
        var verifier = new DecisionSourceFactVerifier();
        assertDoesNotThrow(() -> verifier.assertShortageBasis(900L, k, stored));
        assertDoesNotThrow(() -> verifier.assertProvenance(result, stored));
        assertDoesNotThrow(() -> verifier.assertProvenance(result, stored, stored));
        assertSame(k, result.baselineKpis());
        assertNull(k.shortageGapRatio());
        assertNull(k.shortageBasis());
        assertEquals(BigDecimal.TEN, k.totalShortageGap());
    }

    @Test
    void inclusiveOneDayAndNinetyDayFrozenWindowsAreAccepted() {
        var start = LocalDate.of(2026, 9, 30);
        var stored = snapshot(List.of(new RequirementFact(101L, 140L, "MAT140", "钢材", start,
            BigDecimal.TEN, 11L, "SCGD202609280001", 1L)));
        var verifier = new DecisionSourceFactVerifier();
        for (int horizon : new int[] {1, 90}) {
            var k = ratioKpi(stored, start, start.plusDays(horizon - 1L), true);
            assertDoesNotThrow(() -> verifier.assertShortageBasis(900L, k, stored, horizon));
        }
    }

    private static DeliveryContextSnapshot confirmedDemandSnapshot() {
        var start = LocalDate.of(2026, 9, 30);
        return snapshot(List.of(new RequirementFact(101L, 140L, "MAT140", "钢材", start.plusDays(10),
            BigDecimal.TEN, 11L, "SCGD202609280001", 1L)));
    }

    private static DecisionKpiSet ratioKpi(DeliveryContextSnapshot stored, LocalDate start, LocalDate end,
                                         boolean includeDemand) {
        var row = stored.requirements().get(0);
        var basis = new DecisionShortageBasis("900", "snapshot:" + stored.snapshotHash(), start, end,
            includeDemand ? List.of(new DecisionShortageBasis.Requirement(row.detailId(), row.materialId(),
                row.requiredDate(), row.requiredQty(), row.baseUomId())) : List.of(),
            includeDemand ? List.of(new DecisionShortageBasis.Gap(row.materialId(), row.baseUomId(),
                new BigDecimal("5"), row.requiredDate(), "DERIVED:SHORTAGE:APS_CR_140:max_gap")) : List.of(),
            "DERIVED:SHORTAGE:APS_CR:complete");
        return new DecisionKpiSet(0, 0, 0, 0, null, includeDemand ? 1 : 0, null, null,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, includeDemand ? new BigDecimal("0.5") : BigDecimal.ZERO, basis);
    }

    private static DecisionResult ratioResult(DeliveryContextSnapshot stored, DecisionKpiSet k) {
        return new DecisionResult(900L, stored.rootBusinessRef(), 0, null, "HIGH", List.of(), k, List.of(),
            null, Map.of(), new DecisionSourceFactVerifier().numericFacts(900L, stored), List.of(), List.of());
    }

    private static DeliveryContextSnapshot snapshot(List<RequirementFact> requirements) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,"SCGD202609280001"),
            List.of(),List.of(),Map.of(),requirements,List.of(),List.of(),Map.of(),List.of(),Map.of(),List.of(),
            "unit-provenance-hash",LocalDateTime.of(2026,9,30,9,0),List.of());
    }
}
