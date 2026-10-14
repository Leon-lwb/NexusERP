package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** 报表任务进度测试，负责当前模块对应的契约或处理职责。 */
class ReportJobProgressTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code progressAndManualRetryKeepTheOriginalRunAndMonotonicAttemptFence} 场景下的结果与边界条件。 */
    @Test
    void progressAndManualRetryKeepTheOriginalRunAndMonotonicAttemptFence() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var job = f.runtime.enqueue(f.owner, run.id(), "XLSX_DATA", "progress-case");
            var first =
                    f.store.claimJob(job, "worker", Instant.now().plusSeconds(60)).orElseThrow();
            assertTrue(f.store.progressJob(first, "WRITING", 1, 1L));
            assertEquals(1, f.store.job("A", job.id()).orElseThrow().processedRows());
            assertFalse(f.store.progressJob(first, "SNAPSHOT", 0, null));
            assertTrue(
                    f.store.finishJob(first, "worker", "FAILED", null, null, "XLSX_EXPORT_FAILED"));
            f.runtime.retry(f.owner, job.id());
            var queued = f.store.job("A", job.id()).orElseThrow();
            assertEquals(job.id(), queued.id());
            assertEquals(run.id(), queued.runId());
            assertEquals(1, queued.retryRound());
            assertEquals("QUEUED", queued.state());
            var second =
                    f.store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
            assertEquals(first.attempts() + 1, second.attempts());
            assertFalse(f.store.progressJob(first, "WRITING", 1, 1L));
            assertTrue(f.store.progressJob(second, "WRITING", 1, 1L));
            assertTrue(f.store.finishJob(second, "worker", "SUCCEEDED", "file", 10L, null));
            assertThrows(ReportFault.class, () -> f.runtime.retry(f.owner, job.id()));
        }
    }

    /** 验证 {@code permanentFailuresAndRevokedResourcesCannotRetry} 场景下的结果与边界条件。 */
    @Test
    void permanentFailuresAndRevokedResourcesCannotRetry() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var job = f.runtime.enqueue(f.owner, run.id(), "PDF", "permanent-case");
            var lease =
                    f.store.claimJob(job, "worker", Instant.now().plusSeconds(60)).orElseThrow();
            assertTrue(
                    f.store.finishJob(lease, "worker", "FAILED", null, null, "CAPACITY_EXCEEDED"));
            assertThrows(ReportFault.class, () -> f.runtime.retry(f.owner, job.id()));
        }
    }

    /**
     * 验证 {@code automaticRetryExhaustionAndQueuedCancellationKeepTheirExecutionPhase} 场景下的结果与边界条件。
     */
    @Test
    void automaticRetryExhaustionAndQueuedCancellationKeepTheirExecutionPhase() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var job = f.runtime.enqueue(f.owner, run.id(), "XLSX_DATA", "phase-transitions");
            var first =
                    f.store.claimJob(job, "worker", Instant.now().plusSeconds(60)).orElseThrow();
            assertTrue(f.store.progressJob(first, "WRITING", 1, 1L));
            assertTrue(f.store.retryJob(first, "worker", "XLSX_EXPORT_FAILED"));
            var queued = f.store.job("A", job.id()).orElseThrow();
            assertEquals("QUEUED", queued.phase());
            assertEquals(0, queued.processedRows());
            f.store.cancel(f.owner, job.id());
            assertEquals("CANCELLED", f.store.job("A", job.id()).orElseThrow().phase());
            var another = f.runtime.enqueue(f.owner, run.id(), "PDF", "phase-exhausted");
            var last =
                    f.store
                            .claimJob(another, "worker", Instant.now().plusSeconds(60))
                            .orElseThrow();
            f.jdbc.update(
                    "UPDATE rpt_export_job SET attempts=3 WHERE job_id=?", Long.valueOf(last.id()));
            last = f.store.job("A", last.id()).orElseThrow();
            assertTrue(f.store.retryJob(last, "worker", "PDF_EXPORT_FAILED"));
            assertEquals("FAILED", f.store.job("A", last.id()).orElseThrow().phase());
        }
    }

    /** 验证 {@code expiringFinishedJobsSynchronizesStateAndPhase} 场景下的结果与边界条件。 */
    @Test
    void expiringFinishedJobsSynchronizesStateAndPhase() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            for (String state : List.of("SUCCEEDED", "FAILED")) {
                var job = f.runtime.enqueue(f.owner, run.id(), "XLSX_DATA", "expire-" + state);
                var lease =
                        f.store
                                .claimJob(job, "worker", Instant.now().plusSeconds(60))
                                .orElseThrow();
                assertTrue(
                        f.store.finishJob(
                                lease,
                                "worker",
                                state,
                                state.equals("SUCCEEDED") ? "file" : null,
                                state.equals("SUCCEEDED") ? 10L : null,
                                state.equals("FAILED") ? "XLSX_EXPORT_FAILED" : null));
                f.jdbc.update(
                        "UPDATE rpt_export_job SET expires_at=? WHERE job_id=?",
                        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                        Long.valueOf(job.id()));
                assertTrue(f.store.tryExpireJob(f.store.job("A", job.id()).orElseThrow()));
                var expired = f.store.job("A", job.id()).orElseThrow();
                assertEquals("EXPIRED", expired.state());
                assertEquals("EXPIRED", expired.phase());
            }
        }
    }
}
