package com.nexus.report.mapper;

import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.Grant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.List;

import static com.nexus.report.mapper.ReportStoreFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportStoreTest {
    private final ReportStoreFixture f = new ReportStoreFixture();
    private final JdbcReportStore store = f.store;
    private final ReportActor creator = actor("A", 7);

    @AfterEach void close() { f.close(); }

    @Test void optimisticDraftChangesCannotOverwriteAnotherWriterAndTenantIdsCannotCross() {
        var definition = f.definition(creator);
        store.saveDraft(creator, definition.id(), 1, "新版", "{}");
        assertFault(409, () -> store.saveDraft(creator, definition.id(), 1, "覆盖", "{}"));
        assertTrue(store.definition("B", definition.id()).isEmpty());
        assertEquals(2, store.definition("A", definition.id()).orElseThrow().revision());
    }

    @Test void publicationAndRollbackUseImmutableVersions() {
        var dataset = f.dataset(creator);
        var definition = store.createDefinition(creator, "TEST_REPORT", "测试", "TABLE", null, "{\"a\":1}");
        var v1 = store.publish(creator, definition.id(), 1, "{\"a\":1}", "hash1", dataset.publishedVersion());
        long revision = store.definition("A", definition.id()).orElseThrow().revision();
        var draft = store.saveDraft(creator, definition.id(), revision, "更新", "{\"a\":2}");
        var v2 = store.publish(creator, definition.id(), draft.revision(), draft.json(), "hash2", dataset.publishedVersion());
        assertEquals("{\"a\":1}", store.version("A", v1.id()).orElseThrow().json());
        assertNotEquals(v1.id(), v2.id());
        store.rollback(creator, definition.id(), v1.id());
        assertEquals(v1.id(), store.definition("A", definition.id()).orElseThrow().publishedVersion());
    }

    @Test void resourceAclIsTenantBoundAndDoesNotGrantDataFromAnotherResource() {
        var viewer = actor("A", 8);
        var definition = f.definition(creator);
        assertFalse(store.allowed(viewer, "REPORT", definition.id(), viewer.userId(), "VIEW"), "Caller-supplied owner is untrusted");
        store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("USER", "8", "VIEW")));
        assertTrue(store.allowed(viewer, "REPORT", definition.id(), 7, "VIEW"));
        assertFalse(store.allowed(actor("B", 8), "REPORT", definition.id(), 7, "VIEW"));
        assertFalse(store.allowed(viewer, "REPORT", definition.id(), 7, "EXPORT"));
        assertFalse(store.allowed(viewer, "REPORT", f.definition(creator).id(), 7, "VIEW"));
    }

    @Test void publicationRejectsMissingOrForeignDatasetVersionsWithoutInsertingChildren() {
        var definition = f.definition(creator);
        assertFault(404, () -> store.publish(creator, definition.id(), definition.revision(), "{}", "hash", "11"));
        var foreign = f.dataset(actor("B", 7));
        assertFault(404, () -> store.publish(creator, definition.id(), definition.revision(), "{}", "hash", foreign.publishedVersion()));
        assertEquals(0, f.count("rpt_version"));
        assertNull(store.definition("A", definition.id()).orElseThrow().publishedVersion());
    }

    @Test void publicationConsumesTheRevisionAndDoesNotPublishTheSameRevisionTwice() {
        var definition = f.definition(creator);
        var first = f.publish(creator, definition);
        assertFault(409, () -> store.publish(creator, definition.id(), definition.revision(), "{}", "hash", first.datasetVersion()));
        assertEquals(1, store.versions("A", definition.id()).size());
        assertFault(409, () -> store.saveDraft(creator, definition.id(), definition.revision(), "过期草稿", "{}"));
    }

    @Test void offlineAndRollbackFencePreviouslyReadDrafts() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        var beforeOffline = store.definition("A", definition.id()).orElseThrow();
        store.definitionState(creator, definition.id(), "OFFLINE");
        assertFault(409, () -> store.publish(creator, definition.id(), beforeOffline.revision(), "{}", "hash", version.datasetVersion()));
        var beforeRollback = store.definition("A", definition.id()).orElseThrow();
        store.rollback(creator, definition.id(), version.id());
        assertFault(409, () -> store.saveDraft(creator, definition.id(), beforeRollback.revision(), "过期草稿", "{}"));
    }

    @Test void archiveCannotBeReopenedByOfflinePublishRollbackOrDraftWrites() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        store.definitionState(creator, definition.id(), "ARCHIVED");
        var archived = store.definition("A", definition.id()).orElseThrow();
        assertFault(409, () -> store.definitionState(creator, definition.id(), "OFFLINE"));
        assertFault(409, () -> store.rollback(creator, definition.id(), version.id()));
        assertFault(409, () -> store.publish(creator, definition.id(), archived.revision(), "{}", "hash", version.datasetVersion()));
        assertFault(409, () -> store.saveDraft(creator, definition.id(), archived.revision(), "更改", "{}"));
        assertEquals("ARCHIVED", store.definition("A", definition.id()).orElseThrow().status());
        store.definitionState(creator, definition.id(), "ARCHIVED");
    }

    @Test void versionReadsRequireATenantOwnedParent() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        var foreignDefinition = f.definition(actor("B", 8));
        f.jdbc.update("UPDATE rpt_version SET report_id=? WHERE version_id=?", Long.valueOf(foreignDefinition.id()), Long.valueOf(version.id()));
        assertTrue(store.version("A", version.id()).isEmpty());
        assertTrue(store.versions("A", foreignDefinition.id()).isEmpty());
        var dataset = f.dataset(creator);
        var foreignDataset = f.dataset(actor("B", 8));
        f.jdbc.update("UPDATE rpt_dataset_version SET dataset_id=? WHERE version_id=?", Long.valueOf(foreignDataset.id()), Long.valueOf(dataset.publishedVersion()));
        assertTrue(store.datasetVersion("A", dataset.publishedVersion()).isEmpty());
    }

    @Test void catalogsRejectForeignParentsAndRootCreationReturnsTheCanonicalRoot() {
        var foreign = store.createCatalog(actor("B", 8), null, "外部目录");
        assertFault(404, () -> store.createCatalog(creator, foreign.id(), "子目录"));
        var root = store.createCatalog(creator, "0", "报表中心");
        var again = store.createCatalog(actor("A", 8), null, "报表中心");
        assertEquals(root.id(), again.id());
        assertEquals("0", again.parentId());
        assertEquals(1, store.catalogs("A").size());
    }

    @Test void datasetRegistrationIsIdempotentAndKeepsOfflineStateAndOldVersions() {
        var dataset = f.dataset(creator);
        assertEquals(dataset.publishedVersion(), f.dataset(creator).publishedVersion());
        store.datasetState(creator, dataset.id(), "OFFLINE");
        var updated = store.registerDataset(creator, descriptor("TEST_DATA", 2), "{\"v\":2}", "data-hash-2");
        assertEquals("OFFLINE", updated.status());
        assertNotEquals(dataset.publishedVersion(), updated.publishedVersion());
        assertEquals("{\"v\":1}", store.datasetVersion("A", dataset.publishedVersion()).orElseThrow().json());
        assertEquals(2, f.count("rpt_dataset_version"));
    }

    @Test void failedDatasetVersionInsertRollsBackTheNewParent() {
        assertThrows(DataAccessException.class, () -> store.registerDataset(creator, descriptor("BROKEN_DATA", 1), "{}", "x".repeat(65)));
        assertTrue(store.datasetByCode("A", "BROKEN_DATA").isEmpty());
        assertEquals(0, f.count("rpt_dataset_version"));
    }

    @Test void nonOwnerCannotGrantThemselvesAclManagementOrExportRights() {
        var definition = f.definition(creator);
        var viewer = actor("A", 8);
        assertFault(403, () -> store.replaceAcl(viewer, "REPORT", definition.id(), List.of(new Grant("USER", "8", "EXPORT"))));
        store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("USER", "8", "EDIT")));
        assertFault(403, () -> store.replaceAcl(viewer, "REPORT", definition.id(), List.of(new Grant("USER", "8", "ACL"))));
        assertEquals(List.of(new Grant("USER", "8", "EDIT")), store.acl("A", "REPORT", definition.id()));
    }

    @Test void aclReplacementValidatesBeforeDeletingAndCanonicalizesRepeatedSubjects() {
        var definition = f.definition(creator);
        store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("USER", "8", "VIEW")));
        assertFault(400, () -> store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("TENANT", "8", "VIEW"))));
        assertFault(400, () -> store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("USER", "8", "*"))));
        assertEquals(List.of(new Grant("USER", "8", "VIEW")), store.acl("A", "REPORT", definition.id()));
        store.replaceAcl(creator, "REPORT", definition.id(), List.of(new Grant("USER", "0008", "VIEW"), new Grant("USER", "8", "VIEW")));
        assertEquals(List.of(new Grant("USER", "8", "VIEW")), store.acl("A", "REPORT", definition.id()));
    }

    @Test void invalidAclActionsDoNotBecomeValidThroughOwnership() {
        var definition = f.definition(creator);
        assertFalse(store.allowed(creator, "REPORT", definition.id(), 7, "ROOT"));
        assertFault(400, () -> store.acl("A", "rpt_definition", definition.id()));
    }

    @Test void runCreationRejectsForeignOrUnrelatedVersionsAndOfflineDefinitions() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        var other = f.definition(creator);
        var unrelated = f.publish(creator, other);
        assertFault(404, () -> store.createRun(creator, definition.id(), unrelated.id(), "SNAPSHOT", "{}", "scope", Instant.now().plusSeconds(100)));
        assertFault(404, () -> store.createRun(actor("B", 7), definition.id(), version.id(), "SNAPSHOT", "{}", "scope", Instant.now().plusSeconds(100)));
        store.definitionState(creator, definition.id(), "OFFLINE");
        assertFault(409, () -> store.createRun(creator, definition.id(), version.id(), "SNAPSHOT", "{}", "scope", Instant.now().plusSeconds(100)));
        assertEquals(0, f.count("rpt_run"));
    }

    @Test void exportCreationRequiresAnOwnedSuccessfulUnexpiredSnapshotRun() {
        var run = f.completedRun(creator);
        assertFault(404, () -> f.job(actor("A", 8), run, "other-user"));
        assertFault(404, () -> f.job(actor("B", 7), run, "other-tenant"));
        f.jdbc.update("UPDATE rpt_run SET state='RUNNING' WHERE run_id=?", Long.valueOf(run.id()));
        assertFault(409, () -> f.job(creator, run, "not-ready"));
        f.jdbc.update("UPDATE rpt_run SET state='SUCCEEDED',expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?", Long.valueOf(run.id()));
        assertFault(410, () -> f.job(creator, run, "expired"));
        assertEquals(0, f.count("rpt_export_job"));
    }

    @Test void idempotencySurvivesQuotaAndRejectsReusedKeysWithAnotherRunFormatOrHash() {
        var run = f.completedRun(creator);
        var another = f.completedRun(creator);
        var job = f.job(creator, run, "repeat-key");
        assertEquals(job.id(), f.job(creator, run, "repeat-key").id());
        assertFault(409, () -> store.createJob(creator, another.id(), "PDF", "repeat-key", "request-hash", Instant.now().plusSeconds(100)));
        assertFault(409, () -> store.createJob(creator, run.id(), "XLSX_DETAIL", "repeat-key", "request-hash", Instant.now().plusSeconds(100)));
        assertFault(409, () -> store.createJob(creator, run.id(), "PDF", "repeat-key", "other-hash", Instant.now().plusSeconds(100)));
        assertEquals(1, f.count("rpt_export_job"));
        assertEquals(1, store.catalogs("A").stream().filter(c -> c.name().equals("报表中心") && c.parentId().equals("0")).count());
    }

    @Test void terminalRunStateCannotBeOverwrittenByLateCompletion() {
        var run = f.completedRun(creator);
        store.runState("A", run.id(), "FAILED", null, null, 0, "{}", "LATE_ERROR");
        assertEquals("SUCCEEDED", store.run("A", run.id()).orElseThrow().state());
        f.jdbc.update("UPDATE rpt_run SET state='EXPIRED' WHERE run_id=?", Long.valueOf(run.id()));
        store.runState("A", run.id(), "SUCCEEDED", "resurrected", "resurrected", 10, "{}", null);
        assertEquals("EXPIRED", store.run("A", run.id()).orElseThrow().state());
        assertFault(400, () -> store.runState("A", run.id(), "PUBLISHED", null, null, 0, "{}", null));
    }

    @Test void anExpiredWorkerCannotHeartbeatOrFinishAndAnOldAttemptCannotUseAReusedOwner() {
        var job = f.job(creator, f.completedRun(creator), "lease");
        assertTrue(store.claim(job, "worker", Instant.now().plusSeconds(60)));
        var first = f.reload(job);
        f.expireLease(job);
        assertFalse(store.heartbeat(first, "worker", Instant.now().plusSeconds(120)));
        assertFalse(store.finishJob(first, "worker", "SUCCEEDED", "old-file", 10L, null));
        assertTrue(store.claim(f.reload(job), "worker", Instant.now().plusSeconds(60)));
        var second = f.reload(job);
        assertFalse(store.heartbeat(first, "worker", Instant.now().plusSeconds(120)));
        assertFalse(store.finishJob(first, "worker", "FAILED", null, null, "STALE_WORKER"));
        assertTrue(store.finishJob(second, "worker", "SUCCEEDED", "new-file", 20L, null));
        assertFalse(store.finishJob(second, "worker", "FAILED", null, null, "LATE_ERROR"));
        assertEquals("new-file", f.reload(job).file());
    }

    @Test void cancellationSurvivesLeaseReclaimAndCannotBeOverwrittenBySuccess() {
        var job = f.job(creator, f.completedRun(creator), "cancel");
        assertTrue(store.claim(job, "first", Instant.now().plusSeconds(60)));
        store.cancel(creator, job.id());
        var cancelled = f.reload(job);
        assertFalse(store.finishJob(cancelled, "first", "SUCCEEDED", "file", 1L, null));
        f.expireLease(job);
        assertTrue(store.claim(f.reload(job), "second", Instant.now().plusSeconds(60)));
        var reclaimed = f.reload(job);
        assertEquals("CANCEL_REQUESTED", reclaimed.state());
        assertTrue(reclaimed.cancelled());
        assertTrue(store.finishJob(reclaimed, "second", "CANCELLED", null, null, null));
        assertEquals("CANCELLED", f.reload(job).state());
        assertNull(f.reload(job).leaseOwner());
    }

    @Test void cancellationOfQueuedAndTerminalJobsIsIdempotentAndActorBound() {
        var job = f.job(creator, f.completedRun(creator), "queued-cancel");
        store.cancel(actor("A", 8), job.id());
        assertEquals("QUEUED", f.reload(job).state());
        store.cancel(creator, job.id());
        store.cancel(creator, job.id());
        assertEquals("CANCELLED", f.reload(job).state());
        assertFalse(store.claim(job, "worker", Instant.now().plusSeconds(60)));
        var success = f.job(creator, f.completedRun(creator), "terminal-cancel");
        assertTrue(store.claim(success, "worker", Instant.now().plusSeconds(60)));
        assertTrue(store.finishJob(f.reload(success), "worker", "SUCCEEDED", "file", 10L, null));
        store.cancel(creator, success.id());
        assertEquals("SUCCEEDED", f.reload(success).state());
    }

    @Test void finishRejectsNonTerminalStatesAndHeartbeatCannotShortenTheLease() {
        var job = f.job(creator, f.completedRun(creator), "state");
        assertTrue(store.claim(job, "worker", Instant.now().plusSeconds(120)));
        var running = f.reload(job);
        assertFault(400, () -> store.finishJob(running, "worker", "QUEUED", null, null, null));
        assertFalse(store.heartbeat(running, "worker", Instant.now().plusSeconds(30)));
        assertEquals(running.leaseUntil(), f.reload(job).leaseUntil());
        assertFalse(store.finishJob(running, "other-worker", "FAILED", null, null, "FAIL"));
    }

    @Test void retryExhaustionTerminatesAbandonedJobsAndReleasesAdmissionQuota() {
        var job = f.job(creator, f.completedRun(creator), "exhaustion");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(store.claim(f.reload(job), "worker-" + attempt, Instant.now().plusSeconds(60)));
            f.expireLease(job);
        }
        assertTrue(store.claimableJobs().stream().noneMatch(j -> j.id().equals(job.id())));
        assertEquals("FAILED", f.reload(job).state());
        assertEquals("RETRY_EXHAUSTED", f.reload(job).error());
        assertEquals(3, f.reload(job).attempts());
        assertNotNull(f.job(creator, f.completedRun(creator), "replacement"));
    }

    @Test void expiredJobsCannotBeClaimedAndExpiryRechecksTimeAndActiveLease() {
        var job = f.job(creator, f.completedRun(creator), "expiry");
        store.expireJob(job);
        assertEquals("QUEUED", f.reload(job).state(), "A stale expiry request must not expire a future job");
        f.jdbc.update("UPDATE rpt_export_job SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE job_id=?", Long.valueOf(job.id()));
        assertFalse(store.claim(f.reload(job), "worker", Instant.now().plusSeconds(60)));
        assertTrue(store.claimableJobs().stream().noneMatch(j -> j.id().equals(job.id())));
        assertEquals("EXPIRED", f.reload(job).state());
    }

    @Test void runExpiryWaitsForAnActiveExportLease() {
        var run = f.completedRun(creator);
        var job = f.job(creator, run, "active-export");
        assertTrue(store.claim(job, "worker", Instant.now().plusSeconds(60)));
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?", Long.valueOf(run.id()));
        assertTrue(store.expiredRuns().stream().noneMatch(r -> r.id().equals(run.id())));
        store.runState("A", run.id(), "EXPIRED", null, null, 0, "{}", null);
        assertEquals("SUCCEEDED", store.run("A", run.id()).orElseThrow().state());
    }


    @Test void lifecycleOverloadsRejectStaleRevisionAndStatusPreconditions() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        assertFault(409, () -> store.rollback(creator, definition.id(), version.id(), definition.revision()));
        assertFault(409, () -> store.definitionState(creator, definition.id(), "OFFLINE", definition.revision()));
        var current = store.definition("A", definition.id()).orElseThrow();
        store.definitionState(creator, definition.id(), "OFFLINE", current.revision());
        var dataset = f.dataset(creator);
        store.datasetState(creator, dataset.id(), "OFFLINE", "ACTIVE");
        assertFault(409, () -> store.datasetState(creator, dataset.id(), "ACTIVE", "ACTIVE"));
        assertEquals("OFFLINE", store.dataset("A", dataset.id()).orElseThrow().status());
    }

    @Test void archivedDatasetCannotBeReactivatedByRegistrationOrExplicitStateChanges() {
        var dataset = f.dataset(creator);
        store.datasetState(creator, dataset.id(), "ARCHIVED");
        assertFault(409, () -> store.datasetState(creator, dataset.id(), "ACTIVE"));
        assertFault(409, () -> store.registerDataset(creator, descriptor("TEST_DATA", 2), "{}", "changed-checksum"));
        assertEquals("ARCHIVED", f.dataset(creator).status());
        assertEquals(1, f.count("rpt_dataset_version"));
    }

    @Test void claimReturnsTheAcquiredAttemptAndStaleCandidatesCannotAcquireLaterAttempts() {
        var queued = f.job(creator, f.completedRun(creator), "claim-fence");
        var first = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertEquals(1, first.attempts());
        assertEquals("worker", first.leaseOwner());
        f.expireLease(first);
        assertTrue(store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).isEmpty());
        var second = store.claimJob(f.reload(first), "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertEquals(2, second.attempts());
        assertFalse(store.finishJob(first, "worker", "FAILED", null, null, "OLD_ATTEMPT"));
        assertTrue(store.finishJob(second, "worker", "SUCCEEDED", "current-file", 10L, null));
    }

    @Test void thirdAttemptCancellationRemainsClaimableForCleanupAndDoesNotRestartExport() {
        var queued = f.job(creator, f.completedRun(creator), "third-cancel");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(store.claim(f.reload(queued), "attempt-" + attempt, Instant.now().plusSeconds(60)));
            f.expireLease(queued);
        }
        store.cancel(creator, queued.id());
        assertTrue(store.claimableJobs().stream().anyMatch(j -> j.id().equals(queued.id()) && j.state().equals("CANCEL_REQUESTED")));
        var cleanup = store.claimJob(f.reload(queued), "cleanup", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(cleanup.cancelled());
        assertEquals("CANCEL_REQUESTED", cleanup.state());
        assertFalse(store.finishJob(cleanup, "cleanup", "SUCCEEDED", "forbidden-file", 10L, null));
        assertTrue(store.finishJob(cleanup, "cleanup", "CANCELLED", null, null, null));
        assertEquals("CANCELLED", f.reload(queued).state());
    }

    @Test void conditionalExpiryDoesNotClearFutureArtifactsAndReturnsWhetherCleanupWon() {
        var run = f.completedRun(creator);
        assertFalse(store.expireRun(run));
        assertNotNull(store.run("A", run.id()).orElseThrow().snapshot());
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?", Long.valueOf(run.id()));
        assertTrue(store.expireRun(run));
        assertFalse(store.expireRun(run));
        assertEquals(run.snapshot(), store.run("A", run.id()).orElseThrow().snapshot());
        assertTrue(store.ackRunCleanup(store.run("A", run.id()).orElseThrow()));
        assertNull(store.run("A", run.id()).orElseThrow().snapshot());
        assertFalse(store.transitionRun("A", run.id(), "RUNNING", null, null, 0, "{}", null));
        var queued = f.job(creator, f.completedRun(creator), "expiry-fence");
        var claimed = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(store.finishJob(claimed, "worker", "SUCCEEDED", "retained-file", 10L, null));
        var succeeded = f.reload(queued);
        assertFalse(store.tryExpireJob(succeeded));
        assertEquals("retained-file", f.reload(queued).file());
        f.jdbc.update("UPDATE rpt_export_job SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE job_id=?", Long.valueOf(queued.id()));
        assertTrue(store.tryExpireJob(succeeded));
        assertFalse(store.tryExpireJob(succeeded));
        assertEquals("retained-file", f.reload(queued).file());
        assertTrue(store.ackJobCleanup(f.reload(queued)));
        assertNull(f.reload(queued).file());
        assertNull(f.reload(queued).bytes());
    }

    @Test void expiryWaitsForAValidLeaseEvenWhenTheJobExpiryTimeHasPassed() {
        var queued = f.job(creator, f.completedRun(creator), "active-expiry");
        assertTrue(store.claim(queued, "worker", Instant.now().plusSeconds(60)));
        f.jdbc.update("UPDATE rpt_export_job SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE job_id=?", Long.valueOf(queued.id()));
        assertFalse(store.tryExpireJob(f.reload(queued)));
        assertTrue(store.expiredJobs().stream().noneMatch(j -> j.id().equals(queued.id())));
        store.claimableJobs();
        assertEquals("RUNNING", f.reload(queued).state());
        f.expireLease(queued);
        assertTrue(store.tryExpireJob(f.reload(queued)));
        assertEquals("EXPIRED", f.reload(queued).state());
    }


    @Test void heartbeatExtendsAnOwnedLeaseAndExportExpiryIsBoundedByItsSnapshot() {
        var run = f.completedRun(creator);
        var queued = store.createJob(creator, run.id(), "PDF", "bounded-expiry", "hash", run.expires().plusSeconds(3600));
        assertEquals(run.expires(), queued.expires());
        var claimed = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(store.heartbeat(claimed, "worker", Instant.now().plusSeconds(120)));
        assertTrue(f.reload(queued).leaseUntil().isAfter(claimed.leaseUntil()));
        assertTrue(store.finishJob(claimed, "worker", "SUCCEEDED", "file", 10L, null));
    }

    @Test void requestBoundsAreRejectedAsClientFaultsWithoutInsertingJobs() {
        var run = f.completedRun(creator);
        assertFault(400, () -> store.createJob(creator, run.id(), "PDF", "x".repeat(81), "hash", Instant.now().plusSeconds(100)));
        assertFault(400, () -> store.createJob(creator, run.id(), "x".repeat(33), "key", "hash", Instant.now().plusSeconds(100)));
        assertFault(400, () -> store.createJob(creator, run.id(), "PDF", "key", "x".repeat(65), Instant.now().plusSeconds(100)));
        assertEquals(0, f.count("rpt_export_job"));
    }

    @Test void datasetActivationRejectsAPublishedPointerOwnedByAnotherDataset() {
        var dataset = f.dataset(creator);
        var other = store.registerDataset(creator, descriptor("OTHER_DATA", 1), "{}", "other-data-checksum");
        store.datasetState(creator, dataset.id(), "OFFLINE");
        f.jdbc.update("UPDATE rpt_dataset SET published_version_id=? WHERE dataset_id=?", Long.valueOf(other.publishedVersion()), Long.valueOf(dataset.id()));
        assertFault(404, () -> store.datasetState(creator, dataset.id(), "ACTIVE"));
        assertEquals("OFFLINE", store.dataset("A", dataset.id()).orElseThrow().status());
    }


    @Test void retryIsFencedAndStopsAfterTheThirdAttemptWithoutSavingExceptionMessages() {
        var queued = f.job(creator, f.completedRun(creator), "retry");
        for (int attempt = 1; attempt <= 3; attempt++) {
            var claimed = store.claimJob(f.reload(queued), "worker", Instant.now().plusSeconds(60)).orElseThrow();
            assertTrue(store.retryJob(claimed, "worker", attempt == 1 ? "raw exception details" : "PDF_EXPORT_FAILED"));
            var retried = f.reload(queued);
            assertEquals(attempt < 3 ? "QUEUED" : "FAILED", retried.state());
            assertEquals(attempt == 1 ? "REPORT_EXECUTION_FAILED" : "PDF_EXPORT_FAILED", retried.error());
            assertNull(retried.leaseOwner());
            assertNull(retried.leaseUntil());
            assertEquals(attempt, retried.attempts());
        }
        assertFalse(store.claim(f.reload(queued), "worker", Instant.now().plusSeconds(60)));
    }

    @Test void retryPreservesCancellationAndDoesNotRequeueARequestedCancellation() {
        var queued = f.job(creator, f.completedRun(creator), "cancel-retry");
        var claimed = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        store.cancel(creator, queued.id());
        assertTrue(store.retryJob(claimed, "worker", "STORAGE_UNAVAILABLE"));
        assertEquals("CANCELLED", f.reload(queued).state());
        assertTrue(f.reload(queued).cancelled());
        assertNull(f.reload(queued).leaseOwner());
    }

    @Test void oldExpiredAndTerminalAttemptsCannotRetryOrOverwriteTheCurrentJob() {
        var queued = f.job(creator, f.completedRun(creator), "stale-retry");
        var first = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertFalse(store.retryJob(first, "other-owner", "STORAGE_UNAVAILABLE"));
        f.expireLease(first);
        assertFalse(store.retryJob(first, "worker", "STORAGE_UNAVAILABLE"));
        var second = store.claimJob(f.reload(queued), "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertFalse(store.retryJob(first, "worker", "XLSX_EXPORT_FAILED"));
        assertTrue(store.finishJob(second, "worker", "SUCCEEDED", "file", 10L, null));
        assertFalse(store.retryJob(second, "worker", "PDF_EXPORT_FAILED"));
        assertEquals("SUCCEEDED", f.reload(queued).state());
        assertEquals("file", f.reload(queued).file());
        assertNull(f.reload(queued).error());
    }

    @Test void retryRejectsExpiredJobAndSnapshotEvenIfTheLeaseHasNotExpired() {
        var run = f.completedRun(creator);
        var queued = f.job(creator, run, "expired-retry");
        var claimed = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?", Long.valueOf(run.id()));
        assertFalse(store.retryJob(claimed, "worker", "STORAGE_UNAVAILABLE"));
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP+INTERVAL '1' HOUR WHERE run_id=?", Long.valueOf(run.id()));
        f.jdbc.update("UPDATE rpt_export_job SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE job_id=?", Long.valueOf(queued.id()));
        assertFalse(store.retryJob(claimed, "worker", "STORAGE_UNAVAILABLE"));
        assertEquals("RUNNING", f.reload(queued).state());
    }


    @Test void snapshotCheckpointIsExclusiveAndOnlyItsLiveWorkerCanPublishTheArtifact() {
        var definition = f.definition(creator);
        var version = f.publish(creator, definition);
        var run = store.createRun(creator, definition.id(), version.id(), "SNAPSHOT", "{}", "scope", Instant.now().plusSeconds(3600));
        assertTrue(store.beginSnapshot("A", run.id(), "build/preview/one", null));
        assertFalse(store.beginSnapshot("A", run.id(), "build/preview/two", null));
        assertFalse(store.finishSnapshot("A", run.id(), "build/preview/two", null, "SUCCEEDED", "wrong-file", 1, "{}", null));
        assertTrue(store.finishSnapshot("A", run.id(), "build/preview/one", null, "SUCCEEDED", "canonical-file", 1, "{}", null));
        assertFalse(store.finishSnapshot("A", run.id(), "build/preview/one", null, "FAILED", null, 0, "{}", "REPORT_EXECUTION_FAILED"));
        assertEquals("canonical-file", store.run("A", run.id()).orElseThrow().snapshot());
    }

    @Test void reclaimedSnapshotBuildRejectsTheOldAttemptAndKeepsOnlyTheNewArtifact() {
        var origin = f.completedLiveRun(creator);
        var originalVersion = store.version("A", origin.version()).orElseThrow();
        var newer = store.saveDraft(creator, origin.reportId(), store.definition("A", origin.reportId()).orElseThrow().revision(), "新版", "{}");
        store.publish(creator, origin.reportId(), newer.revision(), newer.json(), "new-hash", originalVersion.datasetVersion());
        var job = store.createExportJob(creator, origin.id(), "PDF", "atomic-clone", "origin-hash", origin.expires());
        var clone = store.run("A", job.runId()).orElseThrow();
        assertEquals(origin.version(), clone.version());
        assertEquals("QUEUED", clone.state());
        var first = store.claimJob(job, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(store.beginSnapshot("A", clone.id(), "build/job/" + first.id() + "/" + first.attempts() + "/old", first));
        f.expireLease(first);
        assertFalse(store.finishSnapshot("A", clone.id(), "build/job/" + first.id() + "/" + first.attempts() + "/old", first, "SUCCEEDED", "old-file", 1, "{}", null));
        var second = store.claimJob(f.reload(job), "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(store.beginSnapshot("A", clone.id(), "build/job/" + second.id() + "/" + second.attempts() + "/new", second));
        assertFalse(store.finishSnapshot("A", clone.id(), "build/job/" + first.id() + "/" + first.attempts() + "/old", first, "FAILED", null, 0, "{}", "REPORT_EXECUTION_FAILED"));
        assertTrue(store.finishSnapshot("A", clone.id(), "build/job/" + second.id() + "/" + second.attempts() + "/new", second, "SUCCEEDED", "new-file", 1, "{}", null));
        assertEquals("new-file", store.run("A", clone.id()).orElseThrow().snapshot());
    }

    @Test void atomicExportAdmissionCreatesOnlyOneCloneForRepeatedKeysAndNoCloneOnQuotaFailure() {
        var origin = f.completedLiveRun(creator);
        var job = store.createExportJob(creator, origin.id(), "PDF", "one-clone", "origin-hash", origin.expires());
        assertEquals(job.id(), store.createExportJob(creator, origin.id(), "PDF", "one-clone", "origin-hash", origin.expires()).id());
        assertEquals(2, f.count("rpt_run"));
        assertFault(429, () -> store.createExportJob(creator, origin.id(), "PDF", "quota", "other-hash", origin.expires()));
        assertEquals(2, f.count("rpt_run"));
        assertEquals(job.id(), store.jobByRequest(creator, "one-clone").orElseThrow().id());
        assertTrue(store.jobByRequest(actor("B", 7), "one-clone").isEmpty());
    }


    @Test void expiryRetainsReferencesUntilPhysicalCleanupIsAcknowledgedAndCanBeRetried() {
        var run = f.completedRun(creator);
        var queued = f.job(creator, run, "cleanup-retry");
        var claimed = store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertTrue(store.finishJob(claimed, "worker", "SUCCEEDED", "private-file", 10L, null));
        f.jdbc.update("UPDATE rpt_export_job SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE job_id=?", Long.valueOf(queued.id()));
        var succeeded = f.reload(queued);
        assertTrue(store.tryExpireJob(succeeded));
        var expiredJob = f.reload(queued);
        assertEquals("private-file", expiredJob.file());
        assertTrue(store.expiredJobs().stream().anyMatch(j -> j.id().equals(queued.id()) && j.file().equals("private-file")));
        assertTrue(store.ackJobCleanup(expiredJob));
        assertNull(f.reload(queued).file());
        assertTrue(store.expiredJobs().stream().noneMatch(j -> j.id().equals(queued.id())));
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?", Long.valueOf(run.id()));
        assertTrue(store.expireRun(run));
        var expiredRun = store.run("A", run.id()).orElseThrow();
        assertEquals(run.snapshot(), expiredRun.snapshot());
        assertTrue(store.expiredRuns().stream().anyMatch(r -> r.id().equals(run.id())));
        assertTrue(store.ackRunCleanup(expiredRun));
        assertNull(store.run("A", run.id()).orElseThrow().snapshot());
        assertNull(store.run("A", run.id()).orElseThrow().plan());
        assertTrue(store.expiredRuns().stream().noneMatch(r -> r.id().equals(run.id())));
    }

    @Test void cleanupAcknowledgementCannotClearLiveOrReplacedReferences() {
        var run = f.completedRun(creator);
        assertFalse(store.ackRunCleanup(run));
        f.jdbc.update("UPDATE rpt_run SET state='EXPIRED',snapshot_ref='replacement-file' WHERE run_id=?", Long.valueOf(run.id()));
        assertFalse(store.ackRunCleanup(run));
        assertEquals("replacement-file", store.run("A", run.id()).orElseThrow().snapshot());
        assertTrue(store.ackRunCleanup(store.run("A", run.id()).orElseThrow()));
    }

    private static void assertFault(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ReportFault.class, action).status());
    }
    @Test void artifactRecoveryReferenceChecksRespectTenantAndUnacknowledgedExpiry() {
        var run=f.completedRun(creator);
        assertTrue(store.artifactReferenced("A",run.snapshot()));assertTrue(store.artifactReferenced("A",run.plan()));
        assertFalse(store.artifactReferenced("B",run.snapshot()));assertFalse(store.artifactReferenced("A","unregistered-object"));
        f.jdbc.update("UPDATE rpt_run SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1' MINUTE WHERE run_id=?",Long.valueOf(run.id()));
        assertTrue(store.expireRun(run));assertTrue(store.artifactReferenced("A",run.snapshot()));
        assertTrue(store.ackRunCleanup(store.run("A",run.id()).orElseThrow()));assertFalse(store.artifactReferenced("A",run.snapshot()));
    }
    @Test void snapshotDataTimeIsTheAcquiredExecutionStartRatherThanQueueAdmissionTime() {
        var definition=f.definition(creator);var version=f.publish(creator,definition);
        var run=store.createRun(creator,definition.id(),version.id(),"SNAPSHOT","{}","scope",Instant.now().plusSeconds(3600));
        f.jdbc.update("UPDATE rpt_run SET data_time=? WHERE run_id=?",java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),Long.valueOf(run.id()));
        assertTrue(store.beginSnapshot("A",run.id(),"build/preview/time",null));
        var started=store.run("A",run.id()).orElseThrow();assertTrue(started.dataTime().isAfter(Instant.parse("2000-01-02T00:00:00Z")));
        assertFalse(store.beginSnapshot("A",run.id(),"build/preview/stale",null));
        assertEquals(started.dataTime(),store.run("A",run.id()).orElseThrow().dataTime());
    }
}
