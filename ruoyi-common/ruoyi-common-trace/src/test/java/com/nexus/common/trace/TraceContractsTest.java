package com.nexus.common.trace;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TraceContractsTest {
    @Test
    void trackingDimensionsRemainIndependent() {
        assertEquals(TrackingMode.QUANTITY, TrackingPolicy.of(0L, 0L, true, 1L, "STRICT").mode());
        assertEquals(TrackingMode.BATCH, TrackingPolicy.of(1L, 0L, true, 1L, "STRICT").mode());
        assertEquals(TrackingMode.SERIAL, TrackingPolicy.of(0L, 1L, true, 1L, "STRICT").mode());
        assertEquals(
                TrackingMode.BATCH_SERIAL, TrackingPolicy.of(1L, 1L, false, 2L, "STRICT").mode());
    }

    @Test
    void serialQuantityMustMatchUniquePhysicalIdentities() {
        var policy = TrackingPolicy.of(0L, 1L, true, 1L, "STRICT");
        var a = new SnItem(10L, null, null, null, null);
        var b = new SnItem(11L, null, null, null, null);
        assertDoesNotThrow(
                () -> TraceRules.validateSerialItems(policy, new BigDecimal("2"), List.of(a, b)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TraceRules.validateSerialItems(policy, new BigDecimal("1.5"), List.of(a)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TraceRules.validateSerialItems(policy, new BigDecimal("2"), List.of(a, a)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TraceRules.validateSerialItems(policy, new BigDecimal("2"), List.of(a)));
    }

    @Test
    void batchSerialPolicyRequiresBatchOnEveryItem() {
        var policy = TrackingPolicy.of(1L, 1L, true, 1L, "STRICT");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TraceRules.validateSerialItems(
                                policy,
                                BigDecimal.ONE,
                                List.of(new SnItem(10L, null, null, null, null))));
    }

    @Test
    void explicitAmbiguityNeverExposesAChosenIdentity() {
        var items = new ArrayList<CodeCandidate>();
        items.add(
                new CodeCandidate(CodeObjectType.SN, 10L, null, "SN1", 1L, "M1", null, null, null));
        items.add(
                new CodeCandidate(CodeObjectType.SN, 11L, null, "SN2", 1L, "M1", null, null, null));
        var result = CodeResolution.ambiguous(items);
        items.clear();
        assertEquals(ResolveStatus.AMBIGUOUS, result.status());
        assertNull(result.identityId());
        assertEquals(2, result.candidates().size());
        assertThrows(UnsupportedOperationException.class, () -> result.candidates().clear());
    }

    @Test
    void amountAllocationConservesOriginalAmountAndStableOrder() {
        var result = TraceRules.allocateAmount(new BigDecimal("1.0000"), List.of(30L, 10L, 20L));
        assertEquals(
                new BigDecimal("1.0000"),
                result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("0.3334"), result.get(10L));
        assertEquals(List.of(10L, 20L, 30L), new ArrayList<>(result.keySet()));
    }

    @Test
    void nonSerialQuantityIsNotForcedIntoPieceCounting() {
        assertDoesNotThrow(
                () ->
                        TraceRules.validateSerialItems(
                                TrackingPolicy.of(1L, 0L, true, 1L, "STRICT"),
                                new BigDecimal("1.25"),
                                List.of()));
    }

    @Test
    void identifiersAndDecimalQuantitiesKeepTheirStringWireContractEvenForSmallValues()
            throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var policy = TrackingPolicy.of(1L, 1L, true, 1L, "STRICT");
        var candidate =
                new CodeCandidate(CodeObjectType.SN, 12L, 13L, "SN", 14L, "M", 15L, "B", policy);
        var tree = json.readTree(json.writeValueAsString(CodeResolution.resolved(candidate)));
        assertTrue(tree.path("identityId").isTextual());
        assertEquals("12", tree.path("identityId").asText());
        assertTrue(tree.path("policy").path("version").isTextual());
        var line =
                new CompletionManifestLine(
                        1, 12L, 13L, "SN", 15L, null, 14L, new BigDecimal("1.0000"), "GOOD", 1);
        var value = json.readTree(json.writeValueAsString(line));
        assertTrue(value.path("quantity").isTextual());
        assertTrue(value.path("identityId").isTextual());
    }

    @Test
    void allScrapAcknowledgementCannotInventAnInboundReceipt() {
        var result = new CompletionReceipt(null, null, 77L, 1, "a".repeat(64));
        assertEquals("NO_GOOD_OUTPUT", result.outcome());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionReceipt(1L, null, 77L, 1, "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionReceipt(1L, 2L, 77L, 1, "a".repeat(64), "NO_GOOD_OUTPUT"));
    }
}
