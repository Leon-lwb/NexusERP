package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.trace.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * 取消 DTO 和不可伪造准备凭据的公共契约。
 */
class ErpCompletionCancellationContractTest extends ErpCompletionCancellationTestSupport {
    @Test
    void requestPreservesLongManifestAndOriginalProof() throws Exception {
        String encoded = json.writeValueAsString(request);
        assertTrue(encoded.contains("\"manifestId\":\"" + MANIFEST + "\""));
        assertEquals(request, json.readValue(encoded, CompletionCancellationRequest.class));
    }

    @Test
    void auditExtrasRemainInTheVerifiedOwnerPayload() throws Exception {
        var payload = json.valueToTree(request);
        ((ObjectNode) payload)
                .put("commandId", "SYNTH-CANCEL-COMMAND")
                .put("reason", "Operator requested correction")
                .put("actorId", "9007199254741997")
                .put("mesWoId", Long.toString(MES_WO))
                .put("materialHash", MATERIAL_HASH);
        String encoded = json.writeValueAsString(payload);
        var restored = json.readValue(encoded, CompletionCancellationRequest.class);
        var audit = json.valueToTree(restored);
        assertEquals("SYNTH-CANCEL-COMMAND", audit.path("commandId").asText());
        assertEquals("Operator requested correction", audit.path("reason").asText());
        assertTrue(audit.path("actorId").isTextual());
        assertEquals("9007199254741997", audit.path("actorId").asText());
        assertEquals(request.originalEvent(), restored.originalEvent());
        assertEquals(request.manifestId(), restored.manifestId());
        cancel =
                event(
                        "CANCEL_AUDIT",
                        "MES",
                        CompletionCancellationRequest.EVENT_TYPE,
                        CompletionCancellationRequest.AGGREGATE_TYPE,
                        MANIFEST,
                        1,
                        encoded);
        mes.update("DELETE FROM mes_trace_outbox WHERE event_id='CANCEL'");
        mt.executeWithoutResult(s -> mj.append(cancel));
        var prepared = proof();
        assertEquals(encoded, prepared.cancellationEvent().payload());
        assertEquals(
                "9007199254741997",
                json.readTree(prepared.cancellationEvent().payload()).path("actorId").asText());
    }

    @Test
    void legacyFourArgumentConstructorKeepsItsFourFieldWireContract() throws Exception {
        var legacy = new CompletionCancellationRequest(MANIFEST, 1, HASH, original);
        var node = json.valueToTree(legacy);
        assertEquals(4, node.size());
        assertFalse(node.has("commandId"));
        assertFalse(node.has("reason"));
        assertFalse(node.has("actorId"));
        assertEquals(legacy, json.treeToValue(node, CompletionCancellationRequest.class));
    }

    @Test
    void partialAuditMetadataCannotBeSilentlyDiscarded() throws Exception {
        var payload = (ObjectNode) json.valueToTree(request);
        payload.put("commandId", "SYNTH-PARTIAL-AUDIT");
        assertThrows(
                Exception.class,
                () -> json.treeToValue(payload, CompletionCancellationRequest.class));
    }

    @Test
    void auditMetadataRequiresANonblankReasonAndPositiveExactActor() throws Exception {
        var payload = (ObjectNode) json.valueToTree(request);
        payload.put("commandId", "SYNTH-AUDIT")
                .put("reason", " ")
                .put("actorId", "9007199254741997");
        assertThrows(
                Exception.class,
                () -> json.treeToValue(payload, CompletionCancellationRequest.class));
        payload.put("reason", "Correction requires audit").put("actorId", "0");
        assertThrows(
                Exception.class,
                () -> json.treeToValue(payload, CompletionCancellationRequest.class));
    }

    @Test
    void nullOutcomeIsAContractError() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CompletionCancellationResult(
                                "CANCEL",
                                "CANCEL",
                                "ORIGINAL",
                                original.contentHash(),
                                MANIFEST,
                                1,
                                HASH,
                                null,
                                "INVALID"));
    }

    @Test
    void malformedManifestAndOriginalScopeAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionCancellationRequest(0L, 1, HASH, original));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionCancellationRequest(MANIFEST, 0, HASH, original));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionCancellationRequest(MANIFEST, 1, "wrong", original));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompletionCancellationRequest(MANIFEST + 1, 1, HASH, original));
    }

    @Test
    void proofHasNoPublicConstructorAndCannotBePreparedInsideTargetTransaction() {
        assertTrue(
                Arrays.stream(CompletionCancellationProof.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(IllegalStateException.class, () -> et.execute(s -> proof()));
    }

    @Test
    void resultHasNoInventoryReceiptAndCannotInventAnOriginalCompensationAck() throws Exception {
        var value =
                new CompletionCancellationResult(
                        "CANCEL",
                        "CANCEL",
                        "ORIGINAL",
                        original.contentHash(),
                        MANIFEST,
                        1,
                        HASH,
                        "CANCELLED",
                        "CANCELLED_BEFORE_APPLICATION");
        var node = json.readTree(json.writeValueAsString(value));
        assertEquals(Long.toString(MANIFEST), node.path("manifestId").asText());
        assertFalse(node.has("receiptId"));
        assertFalse(node.has("postingGroupId"));
        assertEquals("ORIGINAL", value.originalAcknowledgement().sourceEventId());
        var consumed =
                new CompletionCancellationResult(
                        "CANCEL",
                        "CANCEL",
                        "ORIGINAL",
                        original.contentHash(),
                        MANIFEST,
                        1,
                        HASH,
                        "COMPENSATION_REQUIRED",
                        "ORIGINAL_ALREADY_APPLIED");
        assertThrows(IllegalStateException.class, consumed::originalAcknowledgement);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CompletionCancellationResult(
                                "CANCEL",
                                "CANCEL",
                                "ORIGINAL",
                                original.contentHash(),
                                MANIFEST,
                                1,
                                HASH,
                                "POSTED",
                                "INVALID"));
    }
}
