package com.nexus.report.version;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.runtime.*;
import com.nexus.report.service.ReportDefinitionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ReportEngineCompatibilityTest {
    @TempDir Path directory;

    @Test
    void defaultChangesDoNotReplaceTheExecutorForAnExistingPublication() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var original =
                    f.store
                            .version(
                                    "A",
                                    f.definitions
                                            .detail(f.owner, f.definition.id())
                                            .publishedVersion())
                            .orElseThrow();
            var old = new V1ReportEngineExecutor(f.compiler);
            var calls = new AtomicInteger();
            var next =
                    new V1ReportEngineExecutor(f.compiler) {
                        @Override
                        public String engineVersion() {
                            return "1.1.0";
                        }

                        @Override
                        public CompiledReport compile(
                                ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor) {
                            calls.incrementAndGet();
                            return super.compile(dsl, descriptor, actor);
                        }
                    };
            var registry =
                    new ReportEngineRegistry(List.of(old, next), Map.of(1, "1.1.0"), Set.of());
            var auth =
                    new ReportAuthorizationPort() {
                        public ReportActor resolve(String tenant, long id) {
                            return f.actors.get(id);
                        }

                        public boolean subjectExists(String tenant, String type, long id) {
                            return true;
                        }
                    };
            var definitions =
                    new ReportDefinitionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            auth,
                            f.compiler,
                            registry);
            var fixed = definitions.bindVersion(f.owner, original);
            assertEquals("1.0.0", fixed.engine().engineVersion());
            assertEquals(0, calls.get());
            var current = definitions.detail(f.owner, f.definition.id());
            var published = definitions.publish(f.owner, current.id(), current.revision());
            assertEquals("1.1.0", published.engine());
            assertTrue(calls.get() > 0);
            assertEquals(
                    "1.0.0", definitions.bindVersion(f.owner, original).engine().engineVersion());
            definitions.rollback(
                    f.owner,
                    current.id(),
                    original.id(),
                    definitions.detail(f.owner, current.id()).revision());
            assertEquals(
                    original.id(), definitions.detail(f.owner, current.id()).publishedVersion());
        }
    }

    @Test
    void duplicateUnknownAndDisabledExecutorsAreExplicitlyRejected() {
        var compiler = new ReportCompiler(new com.fasterxml.jackson.databind.ObjectMapper());
        var executor = new V1ReportEngineExecutor(compiler);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new ReportEngineRegistry(
                                List.of(executor, executor), Map.of(1, "1.0.0"), Set.of()));
        var registry = new ReportEngineRegistry(List.of(executor), Map.of(1, "1.0.0"), Set.of());
        assertEquals(
                "VERSION_UNSUPPORTED",
                assertThrows(ReportFault.class, () -> registry.require(2, "2.0.0")).code());
        var disabled =
                new ReportEngineRegistry(List.of(executor), Map.of(1, "1.0.0"), Set.of("1.0.0"));
        assertEquals(
                "ENGINE_DISABLED",
                assertThrows(ReportFault.class, () -> disabled.require(1, "1.0.0")).code());
        assertFalse(disabled.capabilities().get(0).enabled());
    }

    @Test
    void snapshotKeepsTheSelectedExecutorsCalculatedSummaries() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var original = f.dsl("AGG_REPORT");
            var dsl =
                    new ReportDsl(
                            1,
                            original.code(),
                            original.title(),
                            original.kind(),
                            original.datasetCode(),
                            original.datasetVersionId(),
                            List.of(
                                    new ReportDsl.Column(
                                            "count", "数量", 100, 0, "COUNT(code)", null)),
                            List.of(),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            true);
            var marker =
                    new ReportCalculator.SummaryValue(
                            "executor-summary", Map.of(), TypedValue.of(ValueType.INTEGER, 9));
            var executor =
                    new V1ReportEngineExecutor(f.compiler) {
                        @Override
                        public ReportCalculator.CalculatedData calculate(
                                CompiledReport report,
                                List<Map<String, Object>> rows,
                                Map<String, Object> input) {
                            return new ReportCalculator.CalculatedData(
                                    super.calculate(report, rows, input).rows(), List.of(marker));
                        }
                    };
            var compiled = executor.compile(dsl, f.provider.descriptor(), f.owner);
            assertTrue(compiled.aggregateExpressions());
            var snapshot =
                    f.snapshots.create(
                            compiled,
                            f.provider,
                            f.owner,
                            Map.of(),
                            new ReportCancellation(),
                            5000,
                            executor);
            assertEquals(List.of(marker), snapshot.summaries());
        }
    }

    @Test
    void actualLiveSnapshotLayoutAndExportUseThePublishedExecutorAfterDefaultChanges()
            throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var oldCalls = new AtomicInteger();
            var nextCalls = new AtomicInteger();
            var old =
                    new V1ReportEngineExecutor(f.compiler) {
                        @Override
                        public ReportCalculator.CalculatedData calculate(
                                CompiledReport c,
                                List<Map<String, Object>> r,
                                Map<String, Object> p) {
                            oldCalls.incrementAndGet();
                            return super.calculate(c, r, p);
                        }

                        @Override
                        public Map<String, TypedValue> calculateRow(
                                CompiledReport c, Map<String, Object> r, Map<String, Object> p) {
                            oldCalls.incrementAndGet();
                            return super.calculateRow(c, r, p);
                        }

                        @Override
                        public RenderPlan layout(
                                CompiledReport c,
                                ReportCalculator.CalculatedData d,
                                Map<String, Object> p,
                                Path font) {
                            oldCalls.incrementAndGet();
                            return super.layout(c, d, p, font);
                        }
                    };
            var next =
                    new V1ReportEngineExecutor(f.compiler) {
                        @Override
                        public String engineVersion() {
                            return "1.1.0";
                        }

                        @Override
                        public CompiledReport compile(
                                ReportDsl d, DatasetDescriptor s, ReportActor a) {
                            nextCalls.incrementAndGet();
                            return super.compile(d, s, a);
                        }
                    };
            var auth =
                    new ReportAuthorizationPort() {
                        public ReportActor resolve(String tenant, long id) {
                            return f.actors.get(id);
                        }

                        public boolean subjectExists(String t, String k, long id) {
                            return true;
                        }
                    };
            var registry =
                    new ReportEngineRegistry(List.of(old, next), Map.of(1, "1.1.0"), Set.of());
            var definitions =
                    new ReportDefinitionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            auth,
                            f.compiler,
                            registry);
            var properties = new com.nexus.report.config.ReportProperties();
            var runtime =
                    new ReportRuntimeService(
                            f.store,
                            definitions,
                            f.compiler,
                            f.snapshots,
                            f.artifacts,
                            properties,
                            f.json);
            var live = runtime.create(f.owner, f.definition.id(), "LIVE", Map.of());
            runtime.page(f.owner, live.id(), 1, 20, null, false);
            var snapshot = runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            runtime.layout(f.owner, snapshot.id(), "report:runtime:preview", "VIEW");
            var job = runtime.enqueue(f.owner, snapshot.id(), "PDF", "fixed-executor");
            var acquired =
                    f.store
                            .claimJob(job, "test", java.time.Instant.now().plusSeconds(60))
                            .orElseThrow();
            var output = new java.io.ByteArrayOutputStream();
            new com.nexus.report.export.ReportExportRenderer(runtime, properties)
                    .write(acquired, runtime.execution(acquired), output, new ReportCancellation());
            assertTrue(output.size() > 1000);
            assertTrue(oldCalls.get() >= 3);
            assertEquals(0, nextCalls.get());
            var disabled =
                    new ReportDefinitionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            auth,
                            f.compiler,
                            new ReportEngineRegistry(
                                    List.of(old, next), Map.of(1, "1.1.0"), Set.of("1.0.0")));
            var unavailable =
                    new ReportRuntimeService(
                            f.store,
                            disabled,
                            f.compiler,
                            f.snapshots,
                            f.artifacts,
                            properties,
                            f.json);
            assertEquals(
                    "ENGINE_DISABLED",
                    assertThrows(
                                    ReportFault.class,
                                    () -> unavailable.page(f.owner, live.id(), 1, 20, null, false))
                            .code());
            assertEquals(
                    "ENGINE_DISABLED",
                    assertThrows(ReportFault.class, () -> unavailable.execution(acquired)).code());
        }
    }
}
