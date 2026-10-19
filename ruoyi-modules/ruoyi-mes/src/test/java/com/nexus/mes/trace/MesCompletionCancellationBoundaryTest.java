package com.nexus.mes.trace;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 保留四个独立的完工确认边界场景，作为常规业务回归。
 */
class MesCompletionCancellationBoundaryTest {
    MesCompletionCancellationTest f;

    @BeforeEach
    void setup() {
        f = new MesCompletionCancellationTest();
        f.setup();
    }

    @AfterEach
    void cleanup() {
        f.cleanup();
    }

    @Test
    void allThreeLegitimateDeliveryOrdersAndLateDuplicatesPreserveTheNewFreeze() {
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : orders) {
            f.cleanup();
            f = new MesCompletionCancellationTest();
            f.setup();
            f.pending();
            var result = f.result("CANCELLED");
            var original = new LinkedHashMap<String, Object>(result);
            original.put("sourceEventId", f.originalEventId);
            var events =
                    List.of(
                            f.ack(f.event(f.originalEventId), original),
                            f.ack(f.request, result),
                            f.typed("SYNTH_TYPED", result));
            for (int i : order) {
                f.f.inTx(() -> f.f.deliver(events.get(i)));
            }
            assertEquals("CANCELLED", f.status());
            assertEquals(
                    "NONE",
                    f.f.jdbc.queryForObject(
                            "SELECT handoff_status FROM pro_sn WHERE sn_id=1", String.class));
            var next =
                    f.f.inTx(
                            () ->
                                    f.f.manifests.refreeze(
                                            "SYNTH_NEXT", 10L, 1, "corrected evidence", 1L));
            for (int i : order) {
                assertFalse(f.f.inTx(() -> f.f.deliver(events.get(i))));
            }
            assertEquals(
                    "FROZEN",
                    f.f.jdbc.queryForObject(
                            "SELECT status FROM pro_completion_manifest WHERE manifest_id=?",
                            String.class,
                            next.manifestId()));
            assertEquals(
                    "FROZEN",
                    f.f.jdbc.queryForObject(
                            "SELECT handoff_status FROM pro_sn WHERE sn_id=1", String.class));
        }
    }

    @Test
    void cancellationSourcePayloadRetainsOperatorAndReasonAudit() {
        f.pending();
        var payload =
                f.f.events.payload(
                        f.f.jdbc.queryForMap(
                                "SELECT * FROM mes_trace_outbox WHERE event_id=?",
                                f.request.eventId()));
        assertEquals("correct source evidence", payload.path("reason").asText());
        assertEquals("1", payload.path("actorId").asText());
        assertEquals("cancel-1", payload.path("commandId").asText());
    }

    @Test
    void lateOldCompensationAckCanFinishAfterTheLaterCancellationIsTerminal() {
        f.pending();
        var first = f.request;
        var old = f.result("COMPENSATION_REQUIRED");
        f.f.inTx(() -> f.f.deliver(f.typed("SYNTH_OLD_COMP", old)));
        f.request =
                f.event(
                        f.f.inTx(
                                () ->
                                        f.cancellations()
                                                .request(
                                                        "cancel-2",
                                                        f.manifest.manifestId(),
                                                        1,
                                                        "source investigated",
                                                        1L)));
        f.f.inTx(() -> f.f.deliver(f.ack(f.request, f.result("CANCELLED"))));
        assertEquals("CANCELLED", f.status());
        assertDoesNotThrow(() -> f.f.inTx(() -> f.f.deliver(f.ack(first, old))));
        assertEquals("CANCELLED", f.status());
    }

    @Test
    void consumedNoGoodOutputMustNotBeReleasedByAContradictoryCancellationResult() {
        f.cleanup();
        f = new MesCompletionCancellationTest();
        f.f = new MesTraceBusinessTest();
        f.f.setup();
        f.f.productMapping();
        f.f.sn(1, "SCRAPPED");
        f.manifest = f.f.inTx(() -> f.f.manifests.freeze(f.f.wo(0, 1)));
        f.originalEventId =
                f.f.jdbc.queryForObject(
                        "SELECT event_id FROM mes_trace_outbox WHERE"
                                + " event_type='COMPLETION_MANIFEST_FROZEN'",
                        String.class);
        f.pending();
        var noGood = new LinkedHashMap<String, Object>();
        noGood.put("sourceEventId", f.originalEventId);
        noGood.put("manifestId", f.manifest.manifestId());
        noGood.put("revision", 1);
        noGood.put("manifestHash", f.manifest.contentHash());
        noGood.put("outcome", "NO_GOOD_OUTPUT");
        f.f.inTx(() -> f.f.deliver(f.ack(f.event(f.originalEventId), noGood)));
        assertThrows(
                RuntimeException.class,
                () -> f.f.inTx(() -> f.f.deliver(f.ack(f.request, f.result("CANCELLED")))));
        assertEquals("CANCEL_REQUESTED", f.status());
        assertEquals(
                "FROZEN",
                f.f.jdbc.queryForObject(
                        "SELECT handoff_status FROM pro_sn WHERE sn_id=1", String.class));
    }
}
