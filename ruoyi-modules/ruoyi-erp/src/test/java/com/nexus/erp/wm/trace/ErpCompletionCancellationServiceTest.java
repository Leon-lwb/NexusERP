package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.trace.*;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ERP 取消终态、原 ACK、业务消耗及同工单竞争的 H2 行为测试。
 */
class ErpCompletionCancellationServiceTest extends ErpCompletionCancellationTestSupport {
    @Test
    void cancelBeforeOriginalFreezesTombstoneAndBothAcknowledgements() throws Exception {
        var result = result(deliver());
        assertEquals("CANCELLED", result.outcome());
        assertEquals(
                "APPLIED",
                scalar("SELECT status FROM wm_trace_inbox WHERE event_id=?", original.eventId()));
        var tombstone = json.readTree(originalResult());
        assertEquals(original.eventId(), tombstone.path("sourceEventId").asText());
        assertEquals("CANCELLED", tombstone.path("outcome").asText());
        assertFalse(tombstone.has("receiptId"));
        assertFalse(tombstone.has("postingGroupId"));
        assertEquals("3", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_production_recpt"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_material_stock_tx"));
        verify(context).requireWrite(TENANT);
    }

    @Test
    void delayedOriginalNeverInvokesPostingAndReplaysExactOriginalAck() throws Exception {
        deliver();
        String before = originalResult();
        var calls = new AtomicInteger();
        var delivered =
                et.execute(
                        s ->
                                ej.accept(
                                        original,
                                        () -> {
                                            calls.incrementAndGet();
                                            return "FAKE_POSTED";
                                        }));
        assertTrue(delivered.applied());
        assertEquals(0, calls.get());
        assertEquals(before, delivered.resultPayload());
        String ackId = "ACK_" + TraceHashes.sha256(original.eventId()).substring(0, 48);
        var ack =
                json.readTree(
                        text(
                                erp.queryForObject(
                                        "SELECT payload FROM wm_trace_outbox WHERE event_id=?",
                                        Object.class,
                                        ackId)));
        assertEquals(original.eventId(), ack.path("eventId").asText());
        assertEquals(original.contentHash(), ack.path("contentHash").asText());
        assertEquals(before, ack.path("resultPayload").asText());
    }

    @Test
    void repeatCancellationReturnsStoredResultWithoutChangingOriginal() throws Exception {
        var first = deliver();
        String before = originalResult();
        assertEquals(first, deliver());
        assertEquals(before, originalResult());
        assertEquals("3", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }

    @Test
    void laterCancellationKeepsFirstOriginalTombstoneByteForByte() throws Exception {
        deliver();
        String before = originalResult();
        cancel = cancellation("CANCEL_AGAIN", request, 2);
        mt.executeWithoutResult(s -> mj.append(cancel));
        var response = result(deliver());
        assertEquals("CANCELLED", response.outcome());
        assertEquals("CANCEL_AGAIN", response.cancellationEventId());
        assertEquals(before, originalResult());
        assertEquals("CANCEL", json.readTree(before).path("cancellationEventId").asText());
    }

    @Test
    void appliedPostedResultRequiresCompensationAndIsNeverRewritten() throws Exception {
        String posted = receiptResult("POSTED");
        et.executeWithoutResult(s -> ej.accept(original, () -> posted));
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertEquals(posted, originalResult());
        String ack = "ACK_" + TraceHashes.sha256(original.eventId()).substring(0, 48);
        assertEquals(
                posted,
                json.readTree(
                                text(
                                        erp.queryForObject(
                                                "SELECT payload FROM wm_trace_outbox WHERE"
                                                        + " event_id=?",
                                                Object.class,
                                                ack)))
                        .path("resultPayload")
                        .asText());
    }

    @Test
    void appliedNoGoodResultIsConsumedWithoutInventingReceipt() throws Exception {
        String consumed = receiptResult("NO_GOOD_OUTPUT");
        et.executeWithoutResult(s -> ej.accept(original, () -> consumed));
        var value = result(deliver());
        assertEquals("COMPENSATION_REQUIRED", value.outcome());
        assertEquals(consumed, originalResult());
        assertFalse(json.readTree(json.writeValueAsString(value)).has("receiptId"));
    }

    @Test
    void existingExecutedReceiptLeavesOriginalPendingAndDoesNotAcknowledgeIt() throws Exception {
        erp.update(
                "INSERT INTO wm_production_recpt VALUES(51,?,?, 'EXECUTED','0',NULL)",
                TENANT,
                ERP_WO);
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
    }

    @Test
    void deletedExecutedReceiptIsNotHiddenByCancellation() throws Exception {
        erp.update(
                "INSERT INTO wm_production_recpt VALUES(51,?,?, 'EXECUTED','2',NULL)",
                TENANT,
                ERP_WO);
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
    }

    @Test
    void zeroEffectDraftDoesNotPretendReceiptWasExecuted() throws Exception {
        erp.update(
                "INSERT INTO wm_production_recpt VALUES(51,?,?, 'DRAFT','0',NULL)", TENANT, ERP_WO);
        assertEquals("CANCELLED", result(deliver()).outcome());
        assertEquals("DRAFT", scalar("SELECT status FROM wm_production_recpt"));
    }

    @Test
    void existingInventoryEffectRequiresCompensation() throws Exception {
        erp.update(
                "INSERT INTO wm_material_stock_tx VALUES(71,?,'PRD_WORK_ORDER',?)", TENANT, ERP_WO);
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
    }

    @Test
    void postingGroupPreventsCancellingAHalfPostedSource() throws Exception {
        erp.update(
                "INSERT INTO wm_trace_posting_group VALUES(61,?,'PRD_WORK_ORDER',?,'PREPARED')",
                TENANT,
                ERP_WO);
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
    }

    @Test
    void frozenMaterialConsumptionIsAnExistingBusinessEffect() throws Exception {
        erp.update("INSERT INTO wm_trace_material_consumption VALUES(81,?,?,1)", TENANT, MANIFEST);
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
    }

    @Test
    void noGoodOutboxProofIsConsumedEvenWithoutAnyReceipt() throws Exception {
        noGoodProof();
        assertEquals("COMPENSATION_REQUIRED", result(deliver()).outcome());
        assertPendingWithoutOriginalAck();
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_production_recpt"));
    }

    @Test
    void otherTenantReceiptDoesNotBlockTheOriginalTenant() throws Exception {
        erp.update(
                "INSERT INTO wm_production_recpt VALUES(51,'000002',?,'EXECUTED','0',NULL)",
                ERP_WO);
        assertEquals("CANCELLED", result(deliver()).outcome());
    }

    @Test
    void wrongMaterialAndDeletedWorkOrderRollBackTheWholeHandshake() {
        erp.update("UPDATE prd_work_order SET material_id=9999");
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }

    @Test
    void missingOrDeletedWorkOrderCannotBePreempted() {
        erp.update("UPDATE prd_work_order SET del_flag='2'");
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
    }

    @Test
    void noTransactionAndReadOnlyTransactionCannotCreateATombstone() {
        var proof = proof();
        assertThrows(IllegalStateException.class, () -> service.cancel(proof));
        var readOnly = new TransactionTemplate(et.getTransactionManager());
        readOnly.setReadOnly(true);
        assertThrows(
                IllegalStateException.class, () -> readOnly.execute(s -> service.cancel(proof)));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
    }

    @Test
    void contextRejectionCreatesNoTargetState() {
        doThrow(new IllegalStateException("wrong tenant")).when(context).requireWrite(TENANT);
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
    }

    @Test
    void outerFailureRollsBackBothInboxesBothAcksAndResultOutbox() {
        Object prepared = handler.prepare(cancel);
        assertThrows(
                IllegalStateException.class,
                () ->
                        et.execute(
                                s ->
                                        ej.accept(
                                                cancel,
                                                () -> {
                                                    handler.apply(cancel, prepared);
                                                    throw new IllegalStateException("after effect");
                                                })));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
        assertTrue(deliver().applied());
    }

    @Test
    void resultOutboxConflictRollsBackTheOriginalTombstone() {
        et.executeWithoutResult(
                s ->
                        ej.append(
                                event(
                                        "OTHER_RESULT",
                                        "ERP",
                                        "COMPLETION_MANIFEST_CANCELLATION_RESULT",
                                        "COMPLETION_CANCELLATION_RESULT",
                                        MANIFEST,
                                        1,
                                        "{}")));
        assertThrows(IllegalStateException.class, this::deliver);
        assertEquals("0", scalar("SELECT COUNT(*) FROM wm_trace_inbox"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }

    @Test
    void cancellationAckReallyAcknowledgesOriginalSourceAsCancelled() throws Exception {
        deliver();
        String ackId = "ACK_" + TraceHashes.sha256(original.eventId()).substring(0, 48);
        var row = erp.queryForMap("SELECT * FROM wm_trace_outbox WHERE event_id=?", ackId);
        var ack =
                event(
                        ackId,
                        "ERP",
                        "TRACE_APPLIED",
                        row.get("aggregate_type").toString(),
                        ((Number) row.get("aggregate_id")).longValue(),
                        ((Number) row.get("aggregate_version")).longValue(),
                        text(row.get("payload")));
        mt.executeWithoutResult(
                s ->
                        assertTrue(
                                mj.accept(
                                                ack,
                                                () -> {
                                                    throw new AssertionError("ACK is not posting");
                                                })
                                        .applied()));
        assertEquals(
                "APPLIED",
                mes.queryForObject(
                        "SELECT status FROM mes_trace_outbox WHERE event_id=?",
                        String.class,
                        original.eventId()));
        var stored =
                json.readTree(
                        text(
                                mes.queryForObject(
                                        "SELECT result_payload FROM mes_trace_outbox WHERE"
                                                + " event_id=?",
                                        Object.class,
                                        original.eventId())));
        assertEquals("CANCELLED", stored.path("outcome").asText());
        assertFalse(stored.has("receiptId"));
    }

    @Test
    void cancellationFirstMakesConcurrentDelayedPostingANoOp() throws Exception {
        Object prepared = handler.prepare(cancel);
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var cancelling =
                    pool.submit(
                            () ->
                                    et.execute(
                                            s ->
                                                    ej.accept(
                                                            cancel,
                                                            () -> {
                                                                String value =
                                                                        handler.apply(
                                                                                cancel, prepared);
                                                                ready.countDown();
                                                                await(release);
                                                                return value;
                                                            })));
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            var posting =
                    pool.submit(
                            () ->
                                    et.execute(
                                            s ->
                                                    ej.accept(
                                                            original,
                                                            () -> {
                                                                calls.incrementAndGet();
                                                                return "wrong";
                                                            })));
            assertThrows(TimeoutException.class, () -> posting.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(cancelling.get(4, TimeUnit.SECONDS).applied());
            assertTrue(posting.get(4, TimeUnit.SECONDS).applied());
            assertEquals(0, calls.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(4, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellationWaitsOnTheProductionWorkOrderLockAndSeesCommittedReceipt() throws Exception {
        Object prepared = handler.prepare(cancel);
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var posting =
                    pool.submit(
                            () ->
                                    et.execute(
                                            s -> {
                                                erp.queryForList(
                                                        "SELECT * FROM prd_work_order WHERE"
                                                                + " tenant_id=? AND wo_id=? AND"
                                                                + " del_flag='0' FOR UPDATE",
                                                        TENANT,
                                                        ERP_WO);
                                                erp.update(
                                                        "INSERT INTO wm_production_recpt"
                                                            + " VALUES(51,?,?,'EXECUTED','0',NULL)",
                                                        TENANT,
                                                        ERP_WO);
                                                ready.countDown();
                                                await(release);
                                                return true;
                                            }));
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            var cancelling =
                    pool.submit(
                            () ->
                                    et.execute(
                                            s ->
                                                    ej.accept(
                                                            cancel,
                                                            () ->
                                                                    handler.apply(
                                                                            cancel, prepared))));
            assertThrows(TimeoutException.class, () -> cancelling.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            posting.get(4, TimeUnit.SECONDS);
            assertEquals(
                    "COMPENSATION_REQUIRED", result(cancelling.get(4, TimeUnit.SECONDS)).outcome());
            assertPendingWithoutOriginalAck();
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(4, TimeUnit.SECONDS));
        }
    }

    void assertPendingWithoutOriginalAck() {
        assertEquals(
                "WAITING",
                scalar("SELECT status FROM wm_trace_inbox WHERE event_id=?", original.eventId()));
        assertEquals(
                "0",
                scalar(
                        "SELECT COUNT(*) FROM wm_trace_outbox WHERE event_id=?",
                        "ACK_" + TraceHashes.sha256(original.eventId()).substring(0, 48)));
        assertNull(originalResult());
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(4, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test barrier timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
