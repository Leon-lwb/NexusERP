package com.nexus.mes.trace;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MesCompletionCorrectionTest {
    MesTraceBusinessTest f;
    CompletionManifest old;
    long firstUse, secondUse, originalEntry;

    @BeforeEach
    void setup() {
        f = new MesTraceBusinessTest();
        f.setup();
        f.componentPolicy = new TrackingPolicy(true, false, false, 1L, TrackingStage.STRICT);
        f.productMapping();
        f.sn(1, "IN_PRODUCTION");
        long a = f.allocate("quota-a", f.source(1, 10, 0), 8, 5),
                b = f.allocate("quota-b", "source1", 9, 5);
        firstUse =
                f.inTx(() -> f.materials.pick(f.pick("actual-a", 8, a, 3, null), f.componentPolicy))
                        .get(0);
        secondUse =
                f.inTx(() -> f.materials.pick(f.pick("actual-b", 9, b, 3, null), f.componentPolicy))
                        .get(0);
        originalEntry =
                f.inTx(
                        () ->
                                f.materials.allocateToSn(
                                        "old-attribution",
                                        firstUse,
                                        1L,
                                        new BigDecimal("2"),
                                        30L,
                                        "assembly",
                                        1L,
                                        f.componentPolicy));
        f.jdbc.update("UPDATE pro_sn SET sn_status='COMPLETED' WHERE sn_id=1");
        old = f.inTx(() -> f.manifests.freeze(f.wo(1, 0)));
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD mes_wo_id bigint");
        f.jdbc.execute("ALTER TABLE pro_flow_card_step ADD process_id bigint");
        f.jdbc.update(
                "INSERT INTO"
                    + " pro_flow_card_step(step_record_id,tenant_id,card_id,mes_wo_id,sequence_num,process_id,step_status,del_flag)"
                    + " VALUES(31,'000001',11,10,1,30,'COMPLETED','0')");
    }

    @AfterEach
    void cleanup() {
        f.cleanup();
    }

    void cancel() {
        String id =
                f.inTx(
                        () ->
                                new MesCompletionCancellationService(f.jdbc, f.context, f.events)
                                        .request(
                                                "cancel",
                                                old.manifestId(),
                                                1,
                                                "correct component attribution",
                                                1L));
        var request = f.jdbc.queryForMap("SELECT * FROM mes_trace_outbox WHERE event_id=?", id);
        var body = f.events.payload(request).path("originalEvent");
        var result =
                new CompletionCancellationResult(
                        id,
                        id,
                        body.path("eventId").asText(),
                        body.path("contentHash").asText(),
                        old.manifestId(),
                        1,
                        old.contentHash(),
                        "CANCELLED",
                        "CANCELLED_BEFORE_APPLICATION");
        String payload =
                f.events.encode(
                        Map.of(
                                "eventId",
                                id,
                                "contentHash",
                                request.get("content_hash"),
                                "resultPayload",
                                f.events.encode(result)));
        var ack =
                new MaterialTraceEvent(
                        TraceHashes.fingerprint("correction-ack", id),
                        "000001",
                        "ERP",
                        "TRACE_APPLIED",
                        "ACK_COMPLETION_MANIFEST_CANCELLATION",
                        old.manifestId(),
                        1,
                        1,
                        payload,
                        TraceHashes.sha256(payload));
        f.inTx(() -> f.deliver(ack));
    }

    MesMaterialTraceWriter.Correction correction(
            String command, long version, BigDecimal reverseQty, BigDecimal assignQty) {
        return new MesMaterialTraceWriter.Correction(
                command,
                old.manifestId(),
                1,
                1L,
                200L,
                version,
                List.of(new MesMaterialTraceWriter.ReverseLine(originalEntry, reverseQty)),
                List.of(
                        new MesMaterialTraceWriter.CorrectedAllocation(
                                secondUse, assignQty, 31L, "verified-step-31")),
                "correct witnessed component attribution",
                1L);
    }

    MesMaterialTraceWriter.CorrectionResult apply(MesMaterialTraceWriter.Correction command) {
        return f.inTx(() -> f.materials.correct(command, f.componentPolicy));
    }

    @Test
    void
            cancellationAllowsAuditedReverseAndReallocationWithoutReopeningProductionOrDeductingLotAgain() {
        cancel();
        var before = f.manifests.lines("000001", old.manifestId(), 1, null, 500).rows();
        var command = correction("correct-1", 1, new BigDecimal("2"), new BigDecimal("2"));
        var corrected = apply(command);
        assertEquals(2, corrected.version());
        assertEquals(1, corrected.reversalEntryIds().size());
        assertEquals(1, corrected.allocationEntryIds().size());
        assertEquals(
                3, f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
        assertEquals(
                "COMPLETED",
                f.jdbc.queryForObject("SELECT sn_status FROM pro_sn WHERE sn_id=1", String.class));
        assertEquals(
                "COMPLETED",
                f.jdbc.queryForObject(
                        "SELECT wo_status FROM pro_work_order WHERE mes_wo_id=10", String.class));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT aggregated_flag FROM pro_flow_card WHERE card_id=11",
                        Integer.class));
        assertEquals(
                0,
                new BigDecimal("6")
                        .compareTo(
                                f.jdbc.queryForObject(
                                        "SELECT SUM(consumed_qty) FROM lt_lot WHERE lot_id IN(8,9)",
                                        BigDecimal.class)));
        assertEquals(before, f.manifests.lines("000001", old.manifestId(), 1, null, 500).rows());
        var revised =
                f.inTx(
                        () ->
                                f.manifests.refreeze(
                                        "revision-after-correction",
                                        10L,
                                        1,
                                        "corrected attribution",
                                        1L));
        assertEquals(2, revised.revision());
        assertNotEquals(old.contentHash(), revised.contentHash());
        assertEquals(
                2,
                f.manifests
                        .lines("000001", revised.manifestId(), 2, null, 500)
                        .rows()
                        .get(0)
                        .sourceVersion());
        assertEquals(corrected, apply(command));
    }

    @Test
    void ordinaryWriteRoutesStillRejectTerminalSnEvenAfterCancellation() {
        cancel();
        assertThrows(
                RuntimeException.class,
                () ->
                        f.inTx(
                                () ->
                                        f.materials.reverse(
                                                "ordinary-undo",
                                                originalEntry,
                                                BigDecimal.ONE,
                                                "not a correction",
                                                1L)));
        assertThrows(
                RuntimeException.class,
                () ->
                        f.inTx(
                                () ->
                                        f.materials.allocateToSn(
                                                "ordinary-assign",
                                                secondUse,
                                                1L,
                                                BigDecimal.ONE,
                                                30L,
                                                "not a correction",
                                                1L,
                                                f.componentPolicy)));
    }

    @Test
    void noCancellationOrStaleExpectedVersionCannotCreateCorrectionEntries() {
        assertThrows(
                RuntimeException.class,
                () ->
                        apply(
                                correction(
                                        "uncancelled",
                                        1,
                                        new BigDecimal("2"),
                                        new BigDecimal("2"))));
        cancel();
        assertThrows(
                RuntimeException.class,
                () -> apply(correction("stale", 2, new BigDecimal("2"), new BigDecimal("2"))));
        assertEquals(
                1, f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
    }

    @Test
    void noMixAndAllocationCeilingsRollBackTheWholeCorrection() {
        cancel();
        assertThrows(
                RuntimeException.class,
                () -> apply(correction("mixed", 1, BigDecimal.ONE, BigDecimal.ONE)));
        assertThrows(
                RuntimeException.class,
                () -> apply(correction("overuse", 1, new BigDecimal("2"), new BigDecimal("4"))));
        assertEquals(
                1L,
                f.jdbc.queryForObject(
                        "SELECT identity_version FROM pro_sn WHERE sn_id=1", Long.class));
        assertEquals(
                1, f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
    }

    @Test
    void simultaneousCorrectionsWithTheSameExpectedVersionOnlyApplyOnce() throws Exception {
        cancel();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                String command = "race-" + i;
                futures.add(
                        pool.submit(
                                () -> {
                                    com.baomidou.dynamic.datasource.toolkit
                                            .DynamicDataSourceContextHolder.push("mes");
                                    try {
                                        start.await();
                                        apply(
                                                correction(
                                                        command,
                                                        1,
                                                        new BigDecimal("2"),
                                                        new BigDecimal("2")));
                                        return true;
                                    } catch (RuntimeException expected) {
                                        return false;
                                    } finally {
                                        com.baomidou.dynamic.datasource.toolkit
                                                .DynamicDataSourceContextHolder.clear();
                                    }
                                }));
            }
            start.countDown();
            int applied = 0;
            for (var future : futures) {
                if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    applied++;
                }
            }
            assertEquals(1, applied);
            assertEquals(
                    2L,
                    f.jdbc.queryForObject(
                            "SELECT identity_version FROM pro_sn WHERE sn_id=1", Long.class));
            assertEquals(
                    3,
                    f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void foreignOrUnfinishedOperationRollsBackReversalAndOutbox() {
        cancel();
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='IN_PROGRESS' WHERE step_record_id=31");
        assertThrows(
                RuntimeException.class,
                () ->
                        apply(
                                correction(
                                        "unfinished-operation",
                                        1,
                                        new BigDecimal("2"),
                                        new BigDecimal("2"))));
        f.jdbc.update(
                "UPDATE pro_flow_card_step SET step_status='COMPLETED',mes_wo_id=99 WHERE"
                        + " step_record_id=31");
        assertThrows(
                RuntimeException.class,
                () ->
                        apply(
                                correction(
                                        "foreign-operation",
                                        1,
                                        new BigDecimal("2"),
                                        new BigDecimal("2"))));
        assertEquals(
                1, f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
        assertEquals(
                1L,
                f.jdbc.queryForObject(
                        "SELECT identity_version FROM pro_sn WHERE sn_id=1", Long.class));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_outbox WHERE"
                                + " event_type='SN_CONSUMPTION_REVERSED'",
                        Integer.class));
    }

    @Test
    void sameCorrectionCommandCannotChangeItsReasonOrActor() {
        cancel();
        var command = correction("fixed-audit", 1, new BigDecimal("2"), new BigDecimal("2"));
        apply(command);
        var changed =
                new MesMaterialTraceWriter.Correction(
                        command.commandId(),
                        command.manifestId(),
                        command.cancelledRevision(),
                        command.snId(),
                        command.componentMaterialId(),
                        command.expectedVersion(),
                        command.reversals(),
                        command.allocations(),
                        "different audit reason",
                        2L);
        assertThrows(RuntimeException.class, () -> apply(changed));
        assertEquals(
                3, f.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption", Integer.class));
    }

    @Test
    void correctedRefreezeUsesOriginalReleasePolicyAndCommittedReplaySurvivesUnknown() {
        cancel();
        var correction =
                correction("frozen-policy-fix", 1, new BigDecimal("2"), new BigDecimal("2"));
        var corrected = apply(correction);
        var unknown = new java.util.concurrent.atomic.AtomicBoolean();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var prepared = MesFrozenPolicyTestSupport.prepared(f, f.mixed, unknown, reads);
        org.springframework.test.util.ReflectionTestUtils.setField(
                f.manifests, "preparedPolicies", prepared);
        org.mockito.Mockito.when(f.policies.policy("000001", 100L))
                .thenReturn(new TrackingPolicy(false, false, true, 99L, TrackingStage.STRICT));
        org.mockito.Mockito.clearInvocations(f.policies);
        var revised =
                f.inTx(
                        () ->
                                f.manifests.refreeze(
                                        "policy-revision",
                                        10L,
                                        1,
                                        "audited source correction",
                                        1L));
        assertEquals(2, revised.revision());
        assertNotEquals(old.contentHash(), revised.contentHash());
        assertEquals(
                600L,
                f.manifests
                        .lines("000001", revised.manifestId(), 2, null, 500)
                        .rows()
                        .get(0)
                        .batchId());
        unknown.set(true);
        var replay =
                f.inTx(
                        () ->
                                f.manifests.refreeze(
                                        "policy-revision",
                                        10L,
                                        1,
                                        "audited source correction",
                                        1L));
        assertEquals(revised.manifestId(), replay.manifestId());
        assertEquals(revised.contentHash(), replay.contentHash());
        assertEquals(revised.revision(), replay.revision());
        assertEquals(revised.status(), replay.status());
        assertEquals(0, revised.goodQty().compareTo(replay.goodQty()));
        assertEquals(0, revised.scrapQty().compareTo(replay.scrapQty()));
        assertEquals(corrected, f.inTx(() -> f.materials.correct(correction, null)));
        assertEquals(1, reads.get());
        org.mockito.Mockito.verifyNoInteractions(f.policies);
        assertEquals("CANCELLED", f.manifests.manifest("000001", old.manifestId(), 1).status());
    }

    @Test
    void unknownReleaseBlocksNewRefreezeBeforeCreatingItsCommand() {
        cancel();
        var unknown = new java.util.concurrent.atomic.AtomicBoolean(true);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var prepared = MesFrozenPolicyTestSupport.prepared(f, f.mixed, unknown, reads);
        org.springframework.test.util.ReflectionTestUtils.setField(
                f.manifests, "preparedPolicies", prepared);
        assertThrows(
                RuntimeException.class,
                () ->
                        f.inTx(
                                () ->
                                        f.manifests.refreeze(
                                                "unknown-release",
                                                10L,
                                                1,
                                                "no source adoption",
                                                1L)));
        assertEquals(
                1,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_completion_manifest", Integer.class));
        assertEquals(
                0,
                f.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mes_trace_inbox WHERE"
                                + " event_type='COMPLETION_MANIFEST_REFREEZE'",
                        Integer.class));
    }
}
