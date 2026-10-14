package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** 报表导出工作器测试，负责当前模块对应的契约或处理职责。 */
class ReportExportWorkerTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 租户化元数据或私有存储。 */
    final JdbcReportStore store = mock(JdbcReportStore.class);

    /** 保存当前 {@code runtime} 值。 */
    final ReportRuntimeService runtime = mock(ReportRuntimeService.class);

    /** 保存当前 {@code artifacts} 值。 */
    final ReportArtifactStore artifacts = mock(ReportArtifactStore.class);

    /** 保存当前 {@code access} 值。 */
    final ReportRuntimeService.Access access = mock(ReportRuntimeService.Access.class);

    /** 当前导出任务。 */
    ExportJob job;

    /** 本次操作拥有的暂存文件。 */
    Path temporary;

    /** 当前工作节点标识。 */
    ReportExportWorker worker;

    /** 验证 {@code setup} 场景下的结果与边界条件。 */
    @BeforeEach
    void setup() throws Exception {
        var now = Instant.now();
        job =
                new ExportJob(
                        "1",
                        "T",
                        2,
                        "3",
                        "XLSX_DATA",
                        "key",
                        "hash",
                        "RUNNING",
                        1,
                        "owner",
                        now.plusSeconds(30),
                        false,
                        null,
                        null,
                        now.plusSeconds(3600),
                        null);
        when(store.job("T", "1")).thenReturn(Optional.of(job));
        when(runtime.execution(job)).thenReturn(access);
        when(runtime.buildSnapshot(eq(access), any(), eq(100000))).thenReturn(access);
        when(access.actor()).thenReturn(new ReportActor("T", 2, 10, true, List.of()));
        temporary = directory.resolve("temp");
        when(artifacts.temporary(anyString(), anyString())).thenReturn(temporary);
        when(artifacts.output(anyString(), eq(temporary)))
                .thenAnswer(x -> Files.newOutputStream(temporary));
        when(artifacts.commit(anyString(), eq(temporary), anyString())).thenReturn("file");
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    @AfterEach
    void close() {
        if (worker != null) worker.close();
    }

    /**
     * 完成 {@code worker} 对应的报表导出工作器测试处理。
     *
     * @param renderer 当前操作的 {@code renderer} 输入
     */
    void worker(ReportExportWorker.Renderer renderer) {
        worker =
                new ReportExportWorker(store, runtime, artifacts, new ReportProperties(), renderer);
    }

    /** 验证 {@code writesOnlyWhenFenceAndFinalAuthorizationStillPass} 场景下的结果与边界条件。 */
    @Test
    void writesOnlyWhenFenceAndFinalAuthorizationStillPass() {
        when(store.finishJob(job, "owner", "SUCCEEDED", "file", 3L, null)).thenReturn(true);
        worker((j, a, o, c) -> o.write(new byte[] {1, 2, 3}));
        worker.execute(job);
        verify(runtime, times(2)).execution(job);
        verify(store).finishJob(job, "owner", "SUCCEEDED", "file", 3L, null);
        verify(artifacts).temporary("T", "xlsx");
        verify(artifacts).commit("T", temporary, "xlsx");
        verify(artifacts, never()).delete(anyString(), anyString());
    }

    /**
     * 完成 {@code realPrivateStorageAcceptsAllExportFormats} 对应的报表导出工作器测试处理。
     *
     * @param format 当前操作的 {@code format} 输入
     */
    @ParameterizedTest
    @ValueSource(strings = {"PDF", "XLSX_DATA", "XLSX_LAYOUT"})
    void realPrivateStorageAcceptsAllExportFormats(String format) throws Exception {
        var acquired =
                new ExportJob(
                        job.id(),
                        job.tenant(),
                        job.actor(),
                        job.runId(),
                        format,
                        job.requestKey(),
                        job.requestHash(),
                        job.state(),
                        job.attempts(),
                        job.leaseOwner(),
                        job.leaseUntil(),
                        false,
                        null,
                        null,
                        job.expires(),
                        null);
        when(store.job("T", "1")).thenReturn(Optional.of(acquired));
        when(runtime.execution(acquired)).thenReturn(access);
        when(store.finishJob(
                        eq(acquired), eq("owner"), eq("SUCCEEDED"), anyString(), eq(3L), isNull()))
                .thenReturn(true);
        var real = new LocalReportArtifactStore(directory.resolve("private"), 1024 * 1024);
        worker =
                new ReportExportWorker(
                        store,
                        runtime,
                        real,
                        new ReportProperties(),
                        (j, a, o, c) -> o.write(new byte[] {1, 2, 3}));
        worker.execute(acquired);
        var file = ArgumentCaptor.forClass(String.class);
        verify(store)
                .finishJob(
                        eq(acquired),
                        eq("owner"),
                        eq("SUCCEEDED"),
                        file.capture(),
                        eq(3L),
                        isNull());
        assertTrue(file.getValue().endsWith(format.equals("PDF") ? ".pdf" : ".xlsx"));
        assertArrayEquals(
                new byte[] {1, 2, 3}, Files.readAllBytes(real.read("T", file.getValue())));
    }

    /** 验证 {@code lostFenceDeletesItsUnpublishedFile} 场景下的结果与边界条件。 */
    @Test
    void lostFenceDeletesItsUnpublishedFile() {
        worker((j, a, o, c) -> o.write(1));
        worker.execute(job);
        verify(artifacts).delete("T", "file");
    }

    /** 验证 {@code cancellationSkipsProvidersAndExporter} 场景下的结果与边界条件。 */
    @Test
    void cancellationSkipsProvidersAndExporter() {
        worker((j, a, o, c) -> fail("cancelled job must not execute"));
        var cancelled = current("CANCEL_REQUESTED", 1, "owner", job.leaseUntil(), true);
        worker.execute(cancelled);
        verifyNoInteractions(runtime);
        verify(store).finishJob(cancelled, "owner", "CANCELLED", null, null, "CANCELLED");
    }

    /** 验证 {@code staleAttemptCannotInvokeProvidersOrChangeTheNewAttempt} 场景下的结果与边界条件。 */
    @Test
    void staleAttemptCannotInvokeProvidersOrChangeTheNewAttempt() {
        when(store.job("T", "1"))
                .thenReturn(Optional.of(current("RUNNING", 2, "owner", job.leaseUntil(), false)));
        worker((j, a, o, c) -> fail("stale worker must not render"));
        worker.execute(job);
        verifyNoInteractions(runtime);
        verifyNoInteractions(artifacts);
        assertNoTerminalWrite();
    }

    /** 验证 {@code expiredLeaseCannotInvokeProviders} 场景下的结果与边界条件。 */
    @Test
    void expiredLeaseCannotInvokeProviders() {
        when(store.job("T", "1"))
                .thenReturn(
                        Optional.of(
                                current(
                                        "RUNNING",
                                        1,
                                        "owner",
                                        Instant.now().minusSeconds(1),
                                        false)));
        worker((j, a, o, c) -> fail());
        worker.execute(job);
        verifyNoInteractions(runtime);
        assertNoTerminalWrite();
    }

    /** 验证 {@code attemptChangedDuringRenderingDiscardsTemporaryWithoutPublishing} 场景下的结果与边界条件。 */
    @Test
    void attemptChangedDuringRenderingDiscardsTemporaryWithoutPublishing() {
        worker(
                (j, a, o, c) -> {
                    o.write(1);
                    when(store.job("T", "1"))
                            .thenReturn(
                                    Optional.of(
                                            current(
                                                    "RUNNING",
                                                    2,
                                                    "owner",
                                                    job.leaseUntil(),
                                                    false)));
                });
        worker.execute(job);
        verify(artifacts).discard("T", temporary);
        verify(artifacts, never()).commit(anyString(), any(), anyString());
        assertNoTerminalWrite();
    }

    /** 验证 {@code cancellationDuringRenderingWinsBeforeFilePublication} 场景下的结果与边界条件。 */
    @Test
    void cancellationDuringRenderingWinsBeforeFilePublication() {
        worker(
                (j, a, o, c) -> {
                    o.write(1);
                    when(store.job("T", "1"))
                            .thenReturn(
                                    Optional.of(
                                            current(
                                                    "CANCEL_REQUESTED",
                                                    1,
                                                    "owner",
                                                    job.leaseUntil(),
                                                    true)));
                });
        worker.execute(job);
        verify(artifacts).discard("T", temporary);
        verify(artifacts, never()).commit(anyString(), any(), anyString());
        verify(store).finishJob(job, "owner", "CANCELLED", null, null, "CANCELLED");
    }

    /** 验证 {@code accessFailureNeverPersistsSensitiveExceptionMessages} 场景下的结果与边界条件。 */
    @Test
    void accessFailureNeverPersistsSensitiveExceptionMessages() {
        when(runtime.execution(job))
                .thenThrow(new ReportFault("SCOPE_DENIED", "private sample", 403));
        worker((j, a, o, c) -> fail());
        worker.execute(job);
        verify(store).finishJob(job, "owner", "FAILED", null, null, "SCOPE_DENIED");
    }

    /** 验证 {@code retryableFaultDiscardsTemporaryAndUsesTheAcquiredAttemptFence} 场景下的结果与边界条件。 */
    @Test
    void retryableFaultDiscardsTemporaryAndUsesTheAcquiredAttemptFence() {
        worker(
                (j, a, o, c) -> {
                    throw new IOException("secret connection string");
                });
        worker.execute(job);
        verify(artifacts).discard("T", temporary);
        verify(store).retryJob(job, "owner", "REPORT_EXECUTION_FAILED");
    }

    /** 验证 {@code realStorageFailureIsRetryable} 场景下的结果与边界条件。 */
    @Test
    void realStorageFailureIsRetryable() {
        when(artifacts.temporary(anyString(), anyString()))
                .thenThrow(new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "private path", 500));
        worker((j, a, o, c) -> fail());
        worker.execute(job);
        verify(store).retryJob(job, "owner", "ARTIFACT_STORE_UNAVAILABLE");
    }

    /** 验证 {@code temporaryCleanupFailureDoesNotPreventFencedRetry} 场景下的结果与边界条件。 */
    @Test
    void temporaryCleanupFailureDoesNotPreventFencedRetry() {
        doThrow(new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "private path", 500))
                .when(artifacts)
                .discard("T", temporary);
        worker(
                (j, a, o, c) -> {
                    throw new IOException("secret connection string");
                });
        assertDoesNotThrow(() -> worker.execute(job));
        verify(store).retryJob(job, "owner", "REPORT_EXECUTION_FAILED");
    }

    /** 验证 {@code shutdownDoesNotStartProviderOrLeaveLocalAttemptActive} 场景下的结果与边界条件。 */
    @Test
    void shutdownDoesNotStartProviderOrLeaveLocalAttemptActive() {
        worker((j, a, o, c) -> fail());
        worker.close();
        worker.execute(job);
        verifyNoInteractions(runtime);
        verify(store).finishJob(job, "owner", "CANCELLED", null, null, "SHUTDOWN");
    }

    /** 验证 {@code heartbeatRejectsTerminalStateEvenWhenOwnerAndAttemptMatch} 场景下的结果与边界条件。 */
    @Test
    void heartbeatRejectsTerminalStateEvenWhenOwnerAndAttemptMatch() {
        when(store.job("T", "1"))
                .thenReturn(Optional.of(current("SUCCEEDED", 1, "owner", job.leaseUntil(), false)));
        worker((j, a, o, c) -> {});
        var cancellation = new ReportCancellation();
        worker.renewLease(job, cancellation, Instant.now().plusSeconds(60));
        assertEquals("LEASE_LOST", cancellation.reason());
        verifyNoInteractions(runtime);
        verify(store, never()).heartbeat(any(), anyString(), any());
    }

    /** 验证 {@code heartbeatRejectsCancellationEvenBeforeTheFlagIsSet} 场景下的结果与边界条件。 */
    @Test
    void heartbeatRejectsCancellationEvenBeforeTheFlagIsSet() {
        when(store.job("T", "1"))
                .thenReturn(
                        Optional.of(
                                current("CANCEL_REQUESTED", 1, "owner", job.leaseUntil(), false)));
        worker((j, a, o, c) -> {});
        var cancellation = new ReportCancellation();
        worker.renewLease(job, cancellation, Instant.now().plusSeconds(60));
        assertEquals("CANCELLED", cancellation.reason());
        verify(store, never()).heartbeat(any(), anyString(), any());
    }

    /** 验证 {@code heartbeatCannotRenewAJobPastItsDeadline} 场景下的结果与边界条件。 */
    @Test
    void heartbeatCannotRenewAJobPastItsDeadline() {
        worker((j, a, o, c) -> {});
        var cancellation = new ReportCancellation();
        worker.renewLease(job, cancellation, Instant.now().minusSeconds(1));
        assertEquals("TIMEOUT", cancellation.reason());
        verifyNoInteractions(runtime);
        verify(store, never()).heartbeat(any(), anyString(), any());
    }

    /** 验证 {@code heartbeatRechecksAuthorizationAndHonorsFailedConditionalRenewal} 场景下的结果与边界条件。 */
    @Test
    void heartbeatRechecksAuthorizationAndHonorsFailedConditionalRenewal() {
        worker((j, a, o, c) -> {});
        var cancellation = new ReportCancellation();
        worker.renewLease(job, cancellation, Instant.now().plusSeconds(60));
        verify(runtime).execution(job);
        verify(store).heartbeat(eq(job), eq("owner"), any());
        assertEquals("LEASE_LOST", cancellation.reason());
    }

    /** 验证 {@code maintenanceDoesNotDeleteArtifactsBeforeConditionalExpiry} 场景下的结果与边界条件。 */
    @Test
    void maintenanceDoesNotDeleteArtifactsBeforeConditionalExpiry() {
        var run =
                new Run(
                        "3",
                        "T",
                        2,
                        "4",
                        "5",
                        "SNAPSHOT",
                        "SUCCEEDED",
                        "{}",
                        "hash",
                        "rows",
                        null,
                        1,
                        Instant.now(),
                        Instant.now().minusSeconds(1),
                        null);
        var expired =
                new ExportJob(
                        job.id(),
                        job.tenant(),
                        job.actor(),
                        job.runId(),
                        job.format(),
                        job.requestKey(),
                        job.requestHash(),
                        "SUCCEEDED",
                        1,
                        null,
                        null,
                        false,
                        "export-file",
                        3L,
                        Instant.now().minusSeconds(1),
                        null);
        when(store.expiredRuns()).thenReturn(List.of(run));
        when(store.expiredJobs()).thenReturn(List.of(expired));
        worker((j, a, o, c) -> {});
        worker.expire();
        verify(artifacts, never()).delete(anyString(), anyString());
        when(store.expireRun(run)).thenReturn(true);
        when(store.tryExpireJob(expired)).thenReturn(true);
        worker.expire();
        verify(artifacts).delete("T", "rows");
        verify(artifacts).delete("T", "export-file");
    }

    /** 验证 {@code oneArtifactCleanupFailureDoesNotPreventOtherExpiredArtifacts} 场景下的结果与边界条件。 */
    @Test
    void oneArtifactCleanupFailureDoesNotPreventOtherExpiredArtifacts() {
        var one =
                new Run(
                        "3",
                        "T",
                        2,
                        "4",
                        "5",
                        "SNAPSHOT",
                        "SUCCEEDED",
                        "{}",
                        "hash",
                        "first",
                        null,
                        1,
                        Instant.now(),
                        Instant.now().minusSeconds(1),
                        null);
        var two =
                new Run(
                        "6",
                        "T",
                        2,
                        "4",
                        "5",
                        "SNAPSHOT",
                        "SUCCEEDED",
                        "{}",
                        "hash",
                        "second",
                        null,
                        1,
                        Instant.now(),
                        Instant.now().minusSeconds(1),
                        null);
        when(store.expiredJobs()).thenReturn(List.of());
        when(store.expiredRuns()).thenReturn(List.of(one, two));
        when(store.expireRun(any())).thenReturn(true);
        doThrow(new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "private path", 500))
                .when(artifacts)
                .delete("T", "first");
        worker((j, a, o, c) -> {});
        worker.expire();
        verify(artifacts).delete("T", "second");
    }

    /** 验证 {@code queueScanExecutesTheAcquiredAttemptRatherThanTheQueuedCandidate} 场景下的结果与边界条件。 */
    @Test
    void queueScanExecutesTheAcquiredAttemptRatherThanTheQueuedCandidate() {
        var queued = current("QUEUED", 0, null, null, false);
        when(store.claimableJobs()).thenReturn(List.of(queued));
        when(store.claimJob(eq(queued), anyString(), any())).thenReturn(Optional.of(job));
        when(store.finishJob(job, "owner", "SUCCEEDED", "file", 3L, null)).thenReturn(true);
        worker((j, a, o, c) -> o.write(new byte[] {1, 2, 3}));
        worker.scan();
        verify(store, timeout(2000)).finishJob(job, "owner", "SUCCEEDED", "file", 3L, null);
        verify(runtime, never()).execution(queued);
    }

    /** 验证 {@code finalAuthorizationRevocationDiscardsUnpublishedArtifact} 场景下的结果与边界条件。 */
    @Test
    void finalAuthorizationRevocationDiscardsUnpublishedArtifact() {
        when(runtime.execution(job)).thenReturn(access).thenThrow(ReportFault.denied());
        worker((j, a, o, c) -> o.write(1));
        worker.execute(job);
        verify(artifacts).discard("T", temporary);
        verify(artifacts, never()).commit(anyString(), any(), anyString());
        verify(store).finishJob(job, "owner", "FAILED", null, null, "SCOPE_DENIED");
    }

    /** 验证 {@code expiredJobDoesNotStartProvidersOrRenderer} 场景下的结果与边界条件。 */
    @Test
    void expiredJobDoesNotStartProvidersOrRenderer() {
        var expired =
                new ExportJob(
                        job.id(),
                        job.tenant(),
                        job.actor(),
                        job.runId(),
                        job.format(),
                        job.requestKey(),
                        job.requestHash(),
                        job.state(),
                        job.attempts(),
                        job.leaseOwner(),
                        job.leaseUntil(),
                        false,
                        null,
                        null,
                        Instant.now().minusSeconds(1),
                        null);
        worker((j, a, o, c) -> fail());
        worker.execute(expired);
        verifyNoInteractions(runtime);
        verifyNoInteractions(artifacts);
        verify(store).finishJob(expired, "owner", "FAILED", null, null, "SNAPSHOT_EXPIRED");
    }

    /** 验证 {@code cancellationDuringCommitDeletesFileInsteadOfPublishingSuccess} 场景下的结果与边界条件。 */
    @Test
    void cancellationDuringCommitDeletesFileInsteadOfPublishingSuccess() {
        when(artifacts.commit("T", temporary, "xlsx"))
                .thenAnswer(
                        invocation -> {
                            when(store.job("T", "1"))
                                    .thenReturn(
                                            Optional.of(
                                                    current(
                                                            "CANCEL_REQUESTED",
                                                            1,
                                                            "owner",
                                                            job.leaseUntil(),
                                                            true)));
                            return "file";
                        });
        worker((j, a, o, c) -> o.write(1));
        worker.execute(job);
        verify(artifacts).delete("T", "file");
        verify(store).finishJob(job, "owner", "CANCELLED", null, null, "CANCELLED");
        verify(store, never()).finishJob(any(), anyString(), eq("SUCCEEDED"), any(), any(), any());
    }

    /** 验证 {@code shutdownDuringCommitDeletesFileInsteadOfPublishingSuccess} 场景下的结果与边界条件。 */
    @Test
    void shutdownDuringCommitDeletesFileInsteadOfPublishingSuccess() {
        when(artifacts.commit("T", temporary, "xlsx"))
                .thenAnswer(
                        invocation -> {
                            worker.close();
                            return "file";
                        });
        worker((j, a, o, c) -> o.write(1));
        worker.execute(job);
        verify(artifacts).delete("T", "file");
        verify(store).finishJob(job, "owner", "CANCELLED", null, null, "SHUTDOWN");
        verify(store, never()).finishJob(any(), anyString(), eq("SUCCEEDED"), any(), any(), any());
    }

    /** 验证 {@code assertNoTerminalWrite} 场景下的结果与边界条件。 */
    private void assertNoTerminalWrite() {
        verify(store, never()).finishJob(any(), anyString(), anyString(), any(), any(), any());
        verify(store, never()).retryJob(any(), anyString(), anyString());
    }

    /**
     * 完成 {@code current} 对应的报表导出工作器测试处理。
     *
     * @param state 目标状态
     * @param attempts 当前操作的 {@code attempts} 输入
     * @param owner 租约或资源持有者
     * @param lease 已领取的任务围栏
     * @param cancelled 当前操作的 {@code cancelled} 输入
     * @return 当前处理返回的固定结果
     */
    private ExportJob current(
            String state, int attempts, String owner, Instant lease, boolean cancelled) {
        return new ExportJob(
                job.id(),
                job.tenant(),
                job.actor(),
                job.runId(),
                job.format(),
                job.requestKey(),
                job.requestHash(),
                state,
                attempts,
                owner,
                lease,
                cancelled,
                null,
                null,
                job.expires(),
                null);
    }

    /**
     * 验证 {@code failedPhysicalJobDeletionRetainsTheExpiredReferenceUntilARepeatedCleanupSucceeds}
     * 场景下的结果与边界条件。
     */
    @Test
    void failedPhysicalJobDeletionRetainsTheExpiredReferenceUntilARepeatedCleanupSucceeds() {
        worker((j, a, o, c) -> fail("expiry must not render"));
        var expired =
                new ExportJob(
                        job.id(),
                        job.tenant(),
                        job.actor(),
                        job.runId(),
                        job.format(),
                        job.requestKey(),
                        job.requestHash(),
                        "EXPIRED",
                        job.attempts(),
                        null,
                        null,
                        false,
                        "retained-file",
                        10L,
                        Instant.now().minusSeconds(60),
                        null);
        when(store.expiredJobs()).thenReturn(List.of(expired));
        when(store.expiredRuns()).thenReturn(List.of());
        doThrow(new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "storage failed", 500))
                .doNothing()
                .when(artifacts)
                .delete("T", "retained-file");
        worker.expire();
        verify(store, never()).ackJobCleanup(any());
        worker.expire();
        verify(store).ackJobCleanup(expired);
        verify(artifacts, times(2)).delete("T", "retained-file");
    }

    /** 验证 {@code failedRunPlanDeletionDoesNotAcknowledgeEitherReferenceBeforeRetry} 场景下的结果与边界条件。 */
    @Test
    void failedRunPlanDeletionDoesNotAcknowledgeEitherReferenceBeforeRetry() {
        worker((j, a, o, c) -> fail("expiry must not render"));
        var expired =
                new Run(
                        "3",
                        "T",
                        2,
                        "4",
                        "5",
                        "SNAPSHOT",
                        "EXPIRED",
                        "{}",
                        "scope",
                        "rows-file",
                        "plan-file",
                        1,
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(60),
                        null);
        when(store.expiredJobs()).thenReturn(List.of());
        when(store.expiredRuns()).thenReturn(List.of(expired));
        doThrow(new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "storage failed", 500))
                .doNothing()
                .when(artifacts)
                .delete("T", "plan-file");
        worker.expire();
        verify(store, never()).ackRunCleanup(any());
        worker.expire();
        verify(store).ackRunCleanup(expired);
        verify(artifacts, times(2)).delete("T", "rows-file");
        verify(artifacts, times(2)).delete("T", "plan-file");
    }
}
