package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.ExportJob;
import com.nexus.report.engine.FontSupport;
import com.nexus.report.export.ReportExportRenderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

/** 报表渲染计划运行测试，负责当前模块对应的契约或处理职责。 */
class ReportRenderPlanRuntimeTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /**
     * 完成 {@code runtime} 对应的报表渲染计划运行测试处理。
     *
     * @param f 当前操作的 {@code f} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportRuntimeService runtime(RuntimeReviewFixture f, ReportProperties properties) {
        return new ReportRuntimeService(
                f.store, f.definitions, f.compiler, f.snapshots, f.artifacts, properties, f.json);
    }

    /** 验证 {@code firstPreviewPersistsOnePlanAndSubsequentPreviewsReuseIt} 场景下的结果与边界条件。 */
    @Test
    void firstPreviewPersistsOnePlanAndSubsequentPreviewsReuseIt() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", java.util.Map.of());
            var plan = f.runtime.layout(f.owner, run.id(), "report:runtime:preview", "VIEW");
            var stored = f.store.run("A", run.id()).orElseThrow();
            assertNotNull(
                    stored.plan(), "a successful preview must register its immutable RenderPlan");
            assertEquals(
                    plan, f.runtime.layout(f.owner, run.id(), "report:runtime:preview", "VIEW"));
            assertEquals(2, f.committedFiles(), "rows and the self-contained plan/font object");
        }
    }

    /** 验证 {@code restartAndFontConfigurationChangesPreserveTheLayoutAndPdf} 场景下的结果与边界条件。 */
    @Test
    void restartAndFontConfigurationChangesPreserveTheLayoutAndPdf() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var properties = new ReportProperties();
            properties.setFontPath(FontSupport.find(null).toString());
            var first = runtime(f, properties);
            var run = first.create(f.owner, f.definition.id(), "SNAPSHOT", java.util.Map.of());
            var plan = first.layout(f.owner, run.id(), "report:runtime:preview", "VIEW");
            properties.setFontPath(directory.resolve("removed-font.ttf").toString());
            var restarted = runtime(f, properties);
            assertEquals(
                    plan, restarted.layout(f.owner, run.id(), "report:runtime:preview", "VIEW"));
            var output = new ByteArrayOutputStream();
            new ReportExportRenderer(restarted, properties)
                    .write(
                            new ExportJob(
                                    "1",
                                    "A",
                                    f.owner.userId(),
                                    run.id(),
                                    "PDF",
                                    "k",
                                    "h",
                                    "RUNNING",
                                    1,
                                    "w",
                                    run.expires(),
                                    false,
                                    null,
                                    null,
                                    run.expires(),
                                    null),
                            restarted.access(f.owner, run.id(), "report:runtime:export", "EXPORT"),
                            output,
                            new ReportCancellation());
            assertTrue(output.size() > 1000);
            assertEquals(
                    "%PDF-",
                    output.toString(java.nio.charset.StandardCharsets.ISO_8859_1).substring(0, 5));
            try (var files = Files.walk(directory)) {
                assertEquals(
                        0,
                        files.filter(p -> p.getFileName().toString().startsWith("pending-"))
                                .count());
            }
        }
    }

    /** 验证 {@code tamperingWithThePersistedPlanIsRejectedBeforeDelivery} 场景下的结果与边界条件。 */
    @Test
    void tamperingWithThePersistedPlanIsRejectedBeforeDelivery() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", java.util.Map.of());
            f.runtime.layout(f.owner, run.id(), "report:runtime:preview", "VIEW");
            var stored = f.store.run("A", run.id()).orElseThrow();
            assertNotNull(stored.plan());
            Files.writeString(f.artifacts.read("A", stored.plan()), "corrupted");
            assertEquals(
                    "PLAN_INVALID",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.layout(
                                                    f.owner,
                                                    run.id(),
                                                    "report:runtime:preview",
                                                    "VIEW"))
                            .code());
        }
    }

    /** 验证 {@code concurrentFirstPreviewsPublishOnlyOneCanonicalPlan} 场景下的结果与边界条件。 */
    @Test
    void concurrentFirstPreviewsPublishOnlyOneCanonicalPlan() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var gate = new CyclicBarrier(2);
            f.artifacts.afterCommit =
                    () -> {
                        try {
                            gate.await(8, TimeUnit.SECONDS);
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    };
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first =
                        executor.submit(
                                () ->
                                        f.runtime.layout(
                                                f.owner,
                                                run.id(),
                                                "report:runtime:preview",
                                                "VIEW"));
                var second =
                        executor.submit(
                                () ->
                                        f.runtime.layout(
                                                f.owner,
                                                run.id(),
                                                "report:runtime:preview",
                                                "VIEW"));
                assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertNotNull(f.store.run("A", run.id()).orElseThrow().plan());
                assertEquals(2, f.committedFiles());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    /** 验证 {@code revokedScopeBeforeRegistrationDeletesTheUnpublishedCandidate} 场景下的结果与边界条件。 */
    @Test
    void revokedScopeBeforeRegistrationDeletesTheUnpublishedCandidate() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            f.artifacts.afterCommit = () -> f.narrow(f.owner.userId());
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.runtime.layout(
                                                    f.owner,
                                                    run.id(),
                                                    "report:runtime:preview",
                                                    "VIEW"))
                            .code());
            assertNull(f.store.run("A", run.id()).orElseThrow().plan());
            assertEquals(1, f.committedFiles());
        }
    }

    /** 验证 {@code cancellationAfterPrivateCommitLeavesOnlyTheRowsSnapshot} 场景下的结果与边界条件。 */
    @Test
    void cancellationAfterPrivateCommitLeavesOnlyTheRowsSnapshot() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var cancellation = new ReportCancellation();
            f.artifacts.afterCommit = () -> cancellation.cancel("CANCELLED");
            assertThrows(
                    com.nexus.common.report.DatasetAccessException.class,
                    () ->
                            f.runtime.layout(
                                    f.runtime.access(
                                            f.owner, run.id(), "report:runtime:preview", "VIEW"),
                                    cancellation));
            assertNull(f.store.run("A", run.id()).orElseThrow().plan());
            assertEquals(1, f.committedFiles());
        }
    }

    /** 验证 {@code cachedPlanStillRequiresCurrentAuthorization} 场景下的结果与边界条件。 */
    @Test
    void cachedPlanStillRequiresCurrentAuthorization() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.viewer, f.definition.id(), "SNAPSHOT", Map.of());
            f.runtime.layout(f.viewer, run.id(), "report:runtime:preview", "VIEW");
            var access = f.runtime.access(f.viewer, run.id(), "report:runtime:preview", "VIEW");
            f.revokeReportView();
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(ReportFault.class, () -> f.runtime.layout(access)).code());
            assertEquals(
                    2, f.committedFiles(), "a registered canonical plan remains until expiration");
        }
    }

    /** 验证 {@code expiredRunsDeleteThePlanAndItsEmbeddedFontTogether} 场景下的结果与边界条件。 */
    @Test
    void expiredRunsDeleteThePlanAndItsEmbeddedFontTogether() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            f.runtime.layout(f.owner, run.id(), "report:runtime:preview", "VIEW");
            f.jdbc.update(
                    "UPDATE rpt_run SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE"
                            + " run_id=?",
                    Long.valueOf(run.id()));
            try (var worker =
                    new com.nexus.report.export.ReportExportWorker(
                            f.store,
                            f.runtime,
                            f.artifacts,
                            new ReportProperties(),
                            new ReportExportRenderer(f.runtime, new ReportProperties()))) {
                worker.expire();
            }
            var expired = f.store.run("A", run.id()).orElseThrow();
            assertEquals("EXPIRED", expired.state());
            assertNull(expired.plan());
            assertNull(expired.snapshot());
            assertEquals(0, f.committedFiles());
        }
    }

    /** 验证 {@code dataExportDoesNotRequireAFontOrCreateAnUnneededPlan} 场景下的结果与边界条件。 */
    @Test
    void dataExportDoesNotRequireAFontOrCreateAnUnneededPlan() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var properties = new ReportProperties();
            properties.setFontPath(directory.resolve("absent.ttf").toString());
            var runtime = runtime(f, properties);
            var run = runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var output = new ByteArrayOutputStream();
            new ReportExportRenderer(runtime, properties)
                    .write(
                            new ExportJob(
                                    "1",
                                    "A",
                                    f.owner.userId(),
                                    run.id(),
                                    "XLSX_DATA",
                                    "k",
                                    "h",
                                    "RUNNING",
                                    1,
                                    "w",
                                    run.expires(),
                                    false,
                                    null,
                                    null,
                                    run.expires(),
                                    null),
                            runtime.access(f.owner, run.id(), "report:runtime:export", "EXPORT"),
                            output,
                            new ReportCancellation());
            assertTrue(output.size() > 1000);
            assertNull(f.store.run("A", run.id()).orElseThrow().plan());
        }
    }

    /** 验证 {@code oldPayloadWithoutPlanChecksumCanAcquireItsFirstLayout} 场景下的结果与边界条件。 */
    @Test
    void oldPayloadWithoutPlanChecksumCanAcquireItsFirstLayout() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var payload = f.json.readTree(run.parameters());
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload).remove("planChecksum");
            f.jdbc.update(
                    "UPDATE rpt_run SET parameters_json=? WHERE run_id=?",
                    payload.toString(),
                    Long.valueOf(run.id()));
            assertEquals(
                    1,
                    f.runtime
                            .layout(f.owner, run.id(), "report:runtime:preview", "VIEW")
                            .rowCount());
            assertNotNull(f.store.run("A", run.id()).orElseThrow().plan());
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    /** 验证 {@code aCancelledWorkerCannotRegisterAPersistedPlan} 场景下的结果与边界条件。 */
    @Test
    void aCancelledWorkerCannotRegisterAPersistedPlan() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            var job = f.runtime.enqueue(f.owner, run.id(), "PDF", "cancel-before-plan");
            var claimed =
                    f.store
                            .claimJob(job, "worker", java.time.Instant.now().plusSeconds(60))
                            .orElseThrow();
            var access = f.runtime.execution(claimed);
            f.artifacts.afterCommit = () -> f.store.cancel(f.owner, claimed.id());
            assertEquals(
                    "LEASE_LOST",
                    assertThrows(ReportFault.class, () -> f.runtime.layout(access)).code());
            assertNull(f.store.run("A", run.id()).orElseThrow().plan());
            assertEquals(1, f.committedFiles());
        }
    }
}
