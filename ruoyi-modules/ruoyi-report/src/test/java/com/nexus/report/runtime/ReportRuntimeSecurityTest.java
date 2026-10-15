package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 报表运行权限边界测试，负责当前模块对应的契约或处理职责。 */
class ReportRuntimeSecurityTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code previewOnlyActorCanCreateAndReadASnapshotWithoutExportPermission} 场景下的结果与边界条件。 */
    @Test
    void previewOnlyActorCanCreateAndReadASnapshotWithoutExportPermission() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.viewer, f.definition.id(), "SNAPSHOT", Map.of());
            assertEquals("SUCCEEDED", run.state());
            assertEquals(
                    "001",
                    f.runtime
                            .page(f.viewer, run.id(), 1, 50, null, false)
                            .rows()
                            .get(0)
                            .get("code")
                            .value());
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.enqueue(
                                                    f.viewer, run.id(), "PDF", "denied-export"))
                            .code());
        }
    }

    /** 验证 {@code liveCreationUsesAValidRunTransitionAndItsMetadataRemainsFixed} 场景下的结果与边界条件。 */
    @Test
    void liveCreationUsesAValidRunTransitionAndItsMetadataRemainsFixed() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            assertEquals("SUCCEEDED", run.state());
            assertEquals(
                    "001",
                    f.runtime
                            .page(f.owner, run.id(), 1, 50, null, false)
                            .rows()
                            .get(0)
                            .get("code")
                            .value());
            assertNull(run.snapshot());
        }
    }

    /**
     * 验证 {@code permissionRevokedDuringSnapshotCreationPreventsPublicationAndDeletesTheArtifact}
     * 场景下的结果与边界条件。
     */
    @Test
    void permissionRevokedDuringSnapshotCreationPreventsPublicationAndDeletesTheArtifact() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.provider.streamOverride =
                    (context, consumer) -> {
                        consumer.accept(f.provider.row());
                        f.revokeReportView();
                    };
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.create(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    "SNAPSHOT",
                                                    Map.of()))
                            .code());
            assertEquals(0, f.committedFiles());
            assertEquals(
                    "FAILED", f.jdbc.queryForObject("SELECT state FROM rpt_run", String.class));
            assertNull(f.jdbc.queryForObject("SELECT snapshot_ref FROM rpt_run", String.class));
        }
    }

    /** 验证 {@code snapshotScopeChangedDuringCreationPreventsPublication} 场景下的结果与边界条件。 */
    @Test
    void snapshotScopeChangedDuringCreationPreventsPublication() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.provider.streamOverride =
                    (context, consumer) -> {
                        consumer.accept(f.provider.row());
                        f.narrow(f.owner.userId());
                    };
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.create(
                                                    f.owner,
                                                    f.definition.id(),
                                                    "SNAPSHOT",
                                                    Map.of()))
                            .code());
            assertEquals(0, f.committedFiles());
            assertEquals(
                    "FAILED", f.jdbc.queryForObject("SELECT state FROM rpt_run", String.class));
        }
    }

    /** 验证 {@code cachedActorAndChecksumTimeAclChangesCannotLeakASnapshotPage} 场景下的结果与边界条件。 */
    @Test
    void cachedActorAndChecksumTimeAclChangesCannotLeakASnapshotPage() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.viewer, f.definition.id(), "SNAPSHOT", Map.of());
            f.artifacts.afterChecksum = f::revokeReportView;
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () -> f.runtime.page(f.viewer, run.id(), 1, 50, null, false))
                            .code());
        }
    }

    /** 验证 {@code liveQueryRechecksScopeBeforeReturningBusinessRows} 场景下的结果与边界条件。 */
    @Test
    void liveQueryRechecksScopeBeforeReturningBusinessRows() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            f.provider.onQuery = () -> f.narrow(f.owner.userId());
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(
                                    ReportFault.class,
                                    () -> f.runtime.page(f.owner, run.id(), 1, 50, null, false))
                            .code());
        }
    }

    /**
     * 验证 {@code cachedExportAccessCannotBypassAnExportRevocationBeforeConsumingRows} 场景下的结果与边界条件。
     */
    @Test
    void cachedExportAccessCannotBypassAnExportRevocationBeforeConsumingRows() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var access = f.runtime.access(f.owner, run.id(), "report:runtime:export", "EXPORT");
            f.artifacts.afterChecksum =
                    () ->
                            f.actors.put(
                                    7L,
                                    RuntimeReviewFixture.actor(
                                            7,
                                            Set.of("report:runtime:preview", "wm:stock:list"),
                                            ReportScope.all(7)));
            var consumed = new AtomicInteger();
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.each(
                                                    access,
                                                    row -> consumed.incrementAndGet(),
                                                    new ReportCancellation()))
                            .code());
            assertEquals(0, consumed.get());
        }
    }

    /** 验证 {@code layoutRevalidatesCachedScopeBeforeUsingTheSnapshot} 场景下的结果与边界条件。 */
    @Test
    void layoutRevalidatesCachedScopeBeforeUsingTheSnapshot() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var access = f.runtime.access(f.owner, run.id(), "report:runtime:export", "EXPORT");
            f.artifacts.afterChecksum = () -> f.narrow(f.owner.userId());
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(ReportFault.class, () -> f.runtime.layout(access)).code());
        }
    }

    /** 验证 {@code exportRequestKeysAreCanonicalAndNotLimitedToTheLastTwoHundredJobs} 场景下的结果与边界条件。 */
    @Test
    void exportRequestKeysAreCanonicalAndNotLimitedToTheLastTwoHundredJobs() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var live = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            var first = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "old-key");
            f.store.cancel(f.owner, first.id());
            for (int i = 0; i < 205; i++) {
                var job = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "later-" + i);
                f.store.cancel(f.owner, job.id());
            }
            assertEquals(
                    first.id(),
                    f.runtime.enqueue(f.owner, "0" + live.id(), "XLSX_DATA", "old-key").id());
            assertEquals(
                    206,
                    f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_export_job", Integer.class));
            assertEquals(207, f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_run", Integer.class));
        }
    }

    /**
     * 验证 {@code liveExportKeepsItsVersionAfterANewerPublicationAndQueuesItsUnmaterializedSnapshot}
     * 场景下的结果与边界条件。
     */
    @Test
    void liveExportKeepsItsVersionAfterANewerPublicationAndQueuesItsUnmaterializedSnapshot() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var live = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            var draft = f.definitions.detail(f.owner, f.definition.id());
            var changed =
                    f.definitions.save(f.owner, draft.id(), draft.revision(), f.dsl("TEST_REPORT"));
            f.definitions.publish(f.owner, draft.id(), changed.revision());
            var job = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "pinned");
            var clone = f.store.run("A", job.runId()).orElseThrow();
            assertEquals(live.version(), clone.version());
            assertEquals("QUEUED", clone.state());
            var claimed =
                    f.store.claimJob(job, "worker", Instant.now().plusSeconds(60)).orElseThrow();
            var built =
                    f.runtime.buildSnapshot(
                            f.runtime.execution(claimed), new ReportCancellation(), 100000);
            assertEquals("SUCCEEDED", built.run().state());
            assertEquals(live.version(), built.version().id());
        }
    }

    /** 验证 {@code executionRejectsAStaleAttemptEvenWhenTheWorkerOwnerIsReused} 场景下的结果与边界条件。 */
    @Test
    void executionRejectsAStaleAttemptEvenWhenTheWorkerOwnerIsReused() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var live = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            var job = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "fence");
            var first = f.store.claimJob(job, "node", Instant.now().plusSeconds(60)).orElseThrow();
            f.jdbc.update(
                    "UPDATE rpt_export_job SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE"
                            + " WHERE job_id=?",
                    Long.valueOf(job.id()));
            var second =
                    f.store
                            .claimJob(
                                    f.store.job("A", job.id()).orElseThrow(),
                                    "node",
                                    Instant.now().plusSeconds(60))
                            .orElseThrow();
            assertEquals(
                    "LEASE_LOST",
                    assertThrows(ReportFault.class, () -> f.runtime.execution(first)).code());
            assertEquals(second.attempts(), f.runtime.execution(second).lease().attempts());
        }
    }

    /** 验证 {@code cancellationAtCommitDoesNotPublishOrLeakAPrivateSnapshot} 场景下的结果与边界条件。 */
    @Test
    void cancellationAtCommitDoesNotPublishOrLeakAPrivateSnapshot() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var binding = f.definitions.bind(f.owner, f.dsl("TEST_REPORT"));
            var cancellation = new ReportCancellation();
            f.artifacts.afterCommit = () -> cancellation.cancel("CANCELLED");
            assertEquals(
                    "CANCELLED",
                    assertThrows(
                                    DatasetAccessException.class,
                                    () ->
                                            f.snapshots.create(
                                                    binding.compiled(),
                                                    binding.provider(),
                                                    f.owner,
                                                    Map.of(),
                                                    cancellation))
                            .code());
            assertEquals(0, f.committedFiles());
        }
    }

    /** 验证 {@code cancelledUsersCanStillCancelTheirOwnJobAfterReportAclRevocation} 场景下的结果与边界条件。 */
    @Test
    void cancelledUsersCanStillCancelTheirOwnJobAfterReportAclRevocation() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var actor =
                    RuntimeReviewFixture.actor(
                            8,
                            Set.of(
                                    "report:runtime:preview",
                                    "report:runtime:export",
                                    "wm:stock:list"),
                            ReportScope.all(8));
            f.actors.put(8L, actor);
            var run = f.runtime.create(actor, f.definition.id(), "SNAPSHOT", Map.of());
            var job = f.runtime.enqueue(actor, run.id(), "PDF", "cancel-after-revoke");
            f.revokeReportView();
            f.runtime.cancel(actor, job.id());
            assertEquals("CANCELLED", f.store.job("A", job.id()).orElseThrow().state());
        }
    }

    /**
     * 验证 {@code theSameAcquiredJobCannotMaterializeItsRunTwiceOrLeakTheLosingArtifact} 场景下的结果与边界条件。
     */
    @Test
    void theSameAcquiredJobCannotMaterializeItsRunTwiceOrLeakTheLosingArtifact() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var live = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            var job = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "concurrent-build");
            var claimed =
                    f.store.claimJob(job, "node", Instant.now().plusSeconds(60)).orElseThrow();
            var access = f.runtime.execution(claimed);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            f.provider.streamOverride =
                    (context, consumer) -> {
                        entered.countDown();
                        await(release);
                        consumer.accept(f.provider.row());
                    };
            var pool = Executors.newSingleThreadExecutor();
            try {
                var first =
                        pool.submit(
                                () ->
                                        f.runtime.buildSnapshot(
                                                access, new ReportCancellation(), 100000));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(
                        "SNAPSHOT_BUILD_IN_PROGRESS",
                        assertThrows(
                                        ReportFault.class,
                                        () ->
                                                f.runtime.buildSnapshot(
                                                        access, new ReportCancellation(), 100000))
                                .code());
                release.countDown();
                var built = first.get(8, TimeUnit.SECONDS);
                assertEquals("SUCCEEDED", built.run().state());
                assertEquals(1, f.committedFiles());
                assertEquals(
                        built.run().snapshot(),
                        f.store.run("A", job.runId()).orElseThrow().snapshot());
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(8, TimeUnit.SECONDS));
            }
        }
    }

    /**
     * 验证 {@code anOldAttemptFinishingAfterLeaseReclaimCannotReplaceTheNewSnapshotOrLeaveItsFile}
     * 场景下的结果与边界条件。
     */
    @Test
    void anOldAttemptFinishingAfterLeaseReclaimCannotReplaceTheNewSnapshotOrLeaveItsFile()
            throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var live = f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            var job = f.runtime.enqueue(f.owner, live.id(), "XLSX_DATA", "reclaimed-build");
            var firstClaim =
                    f.store.claimJob(job, "node", Instant.now().plusSeconds(60)).orElseThrow();
            var firstAccess = f.runtime.execution(firstClaim);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var streams = new AtomicInteger();
            f.provider.streamOverride =
                    (context, consumer) -> {
                        if (streams.incrementAndGet() == 1) {
                            entered.countDown();
                            await(release);
                        }
                        consumer.accept(f.provider.row());
                    };
            var pool = Executors.newSingleThreadExecutor();
            try {
                var first =
                        pool.submit(
                                () -> {
                                    try {
                                        f.runtime.buildSnapshot(
                                                firstAccess, new ReportCancellation(), 100000);
                                        return "unexpected-success";
                                    } catch (ReportFault fault) {
                                        return fault.code();
                                    }
                                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                f.jdbc.update(
                        "UPDATE rpt_export_job SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1'"
                                + " MINUTE WHERE job_id=?",
                        Long.valueOf(job.id()));
                var secondClaim =
                        f.store
                                .claimJob(
                                        f.store.job("A", job.id()).orElseThrow(),
                                        "node",
                                        Instant.now().plusSeconds(60))
                                .orElseThrow();
                var second =
                        f.runtime.buildSnapshot(
                                f.runtime.execution(secondClaim), new ReportCancellation(), 100000);
                release.countDown();
                assertEquals("LEASE_LOST", first.get(8, TimeUnit.SECONDS));
                assertEquals(
                        second.run().snapshot(),
                        f.store.run("A", job.runId()).orElseThrow().snapshot());
                assertEquals("SUCCEEDED", f.store.run("A", job.runId()).orElseThrow().state());
                assertEquals(1, f.committedFiles());
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(8, TimeUnit.SECONDS));
            }
        }
    }

    /** 验证 {@code unknownStoredEngineVersionIsRejectedBeforeBusinessReads} 场景下的结果与边界条件。 */
    @Test
    void unknownStoredEngineVersionIsRejectedBeforeBusinessReads() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.jdbc.update(
                    "UPDATE rpt_version SET engine_version='99.0.0' WHERE version_id=?",
                    Long.valueOf(
                            f.store
                                    .definition("A", f.definition.id())
                                    .orElseThrow()
                                    .publishedVersion()));
            var fault =
                    assertThrows(
                            ReportFault.class,
                            () -> f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of()));
            assertEquals("VERSION_UNSUPPORTED", fault.code());
            assertTrue(f.provider.queries.isEmpty());
        }
    }

    /** 验证 {@code immutableVersionChecksumTamperingIsRejectedBeforeBusinessReads} 场景下的结果与边界条件。 */
    @Test
    void immutableVersionChecksumTamperingIsRejectedBeforeBusinessReads() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.jdbc.update(
                    "UPDATE rpt_version SET checksum='changed' WHERE version_id=?",
                    Long.valueOf(
                            f.store
                                    .definition("A", f.definition.id())
                                    .orElseThrow()
                                    .publishedVersion()));
            var fault =
                    assertThrows(
                            ReportFault.class,
                            () -> f.runtime.create(f.owner, f.definition.id(), "LIVE", Map.of()));
            assertEquals("VERSION_INVALID", fault.code());
            assertTrue(f.provider.queries.isEmpty());
        }
    }

    /** 验证 {@code storedSchemaAndDatasetReferencesMustMatchThePublishedDefinition} 场景下的结果与边界条件。 */
    @Test
    void storedSchemaAndDatasetReferencesMustMatchThePublishedDefinition() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var version =
                    Long.valueOf(
                            f.store
                                    .definition("A", f.definition.id())
                                    .orElseThrow()
                                    .publishedVersion());
            f.jdbc.update("UPDATE rpt_version SET schema_version=2 WHERE version_id=?", version);
            assertEquals(
                    "VERSION_UNSUPPORTED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.create(
                                                    f.owner, f.definition.id(), "LIVE", Map.of()))
                            .code());
            f.jdbc.update(
                    "UPDATE rpt_version SET schema_version=1,dataset_version_id=999 WHERE"
                            + " version_id=?",
                    version);
            assertEquals(
                    "VERSION_INVALID",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.create(
                                                    f.owner, f.definition.id(), "LIVE", Map.of()))
                            .code());
            assertTrue(f.provider.queries.isEmpty());
        }
    }

    /**
     * 验证 {@code exportNeedsBothReportAndDatasetExportGrantsAndRevocationBlocksDelivery}
     * 场景下的结果与边界条件。
     */
    @Test
    void exportNeedsBothReportAndDatasetExportGrantsAndRevocationBlocksDelivery() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var exporter =
                    new ReportActor(
                            "A",
                            9,
                            10,
                            false,
                            List.of(new ReportRole(2, Set.of("*:*:*"), ReportScope.all(9))));
            f.actors.put(9L, exporter);
            f.store.replaceAcl(
                    f.owner,
                    "REPORT",
                    f.definition.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            f.store.replaceAcl(
                    f.root, "DATASET", f.dataset.id(), List.of(new Grant("USER", "9", "VIEW")));
            var run = f.runtime.create(exporter, f.definition.id(), "SNAPSHOT", Map.of());
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.enqueue(
                                                    exporter, run.id(), "PDF", "dataset-export"))
                            .code());
            assertEquals(
                    0, f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_export_job", Integer.class));
            f.store.replaceAcl(
                    f.root,
                    "DATASET",
                    f.dataset.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            var job = f.runtime.enqueue(exporter, run.id(), "PDF", "dataset-export");
            assertNotNull(job);
            f.store.replaceAcl(
                    f.root, "DATASET", f.dataset.id(), List.of(new Grant("USER", "9", "VIEW")));
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(ReportFault.class, () -> f.runtime.job(exporter, job.id()))
                            .code());
        }
    }

    /**
     * 完成 {@code await} 对应的报表运行权限边界测试处理。
     *
     * @param latch 当前操作的 {@code latch} 输入
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS))
                throw new AssertionError("Provider gate timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
