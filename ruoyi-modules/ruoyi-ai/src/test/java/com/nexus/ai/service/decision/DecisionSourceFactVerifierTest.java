package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static DeliveryContextSnapshot snapshot(List<RequirementFact> requirements) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,"SCGD202609280001"),
            List.of(),List.of(),Map.of(),requirements,List.of(),List.of(),Map.of(),List.of(),Map.of(),List.of(),
            "unit-provenance-hash",LocalDateTime.of(2026,9,30,9,0),List.of());
    }
}
