package com.nexus.erp.wm.trace;

import com.nexus.common.trace.*;
import com.nexus.common.trace.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 原始 MES outbox 是取消准备的真实权限凭据，payload 自带 hash 不足以授权。
 */
class ErpCompletionCancellationHandlerTest extends ErpCompletionCancellationTestSupport {
    @Test
    void dedicatedHandlerSupportsOnlyCancellation() {
        assertEquals("ERP", handler.targetModule());
        assertTrue(handler.supports("COMPLETION_MANIFEST_CANCEL_REQUESTED"));
        assertFalse(handler.supports("COMPLETION_MANIFEST_FROZEN"));
        assertFalse(handler.supports("TRACE_APPLIED"));
    }

    @Test
    void missingOriginalOwnerOutboxCannotPreempt() {
        mes.update("DELETE FROM mes_trace_outbox WHERE event_id=?", original.eventId());
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }

    @Test
    void missingCancellationOwnerOutboxCannotPreempt() {
        mes.update("DELETE FROM mes_trace_outbox WHERE event_id=?", cancel.eventId());
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
    }

    @Test
    void changedOwnerPayloadAndHeaderAreRejected() {
        mes.update(
                "UPDATE mes_trace_outbox SET aggregate_type='OTHER' WHERE event_id=?",
                original.eventId());
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }

    @Test
    void inconsistentManifestHashInCommittedCancelIsRejected() throws Exception {
        request = new CompletionCancellationRequest(MANIFEST, 1, "c".repeat(64), original);
        cancel = cancellation("CANCEL_BAD_HASH", request, 2);
        mt.executeWithoutResult(s -> mj.append(cancel));
        assertThrows(IllegalArgumentException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
    }

    @Test
    void unknownOrDuplicateSourceVerifierFailsClosed() {
        var absent = new ErpCompletionCancellationHandler(json, service, List.of());
        assertThrows(IllegalStateException.class, () -> absent.prepare(cancel));
        var duplicate =
                new ErpCompletionCancellationHandler(json, service, List.of(verifier, verifier));
        assertThrows(IllegalStateException.class, () -> duplicate.prepare(cancel));
    }

    @Test
    void preparedProofCannotBeReusedForAnotherCancellationEnvelope() throws Exception {
        Object prepared = handler.prepare(cancel);
        var other = cancellation("OTHER_CANCEL", request, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> et.execute(s -> handler.apply(other, prepared)));
    }

    @Test
    void cancellationMustHaveAnIndependentAggregateStream() throws Exception {
        String payload = json.writeValueAsString(request);
        cancel =
                event(
                        "BAD_STREAM",
                        "MES",
                        "COMPLETION_MANIFEST_CANCEL_REQUESTED",
                        "COMPLETION_MANIFEST",
                        MANIFEST,
                        2,
                        payload);
        mt.executeWithoutResult(s -> mj.append(cancel));
        assertThrows(IllegalArgumentException.class, this::deliver);
    }
}
