package com.nexus.report.version;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.export.ReportExportRenderer;
import com.nexus.report.runtime.*;
import com.nexus.report.service.ReportDefinitionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 报表引擎Compatibility测试，负责当前模块对应的契约或处理职责。 */
class ReportEngineCompatibilityTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code defaultChangesDoNotReplaceTheExecutorForAnExistingPublication} 场景下的结果与边界条件。 */
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
                        /**
                         * 读取不可变执行器版本标识。
                         *
                         * @return 当前处理返回的固定结果
                         */
                        @Override
                        public String engineVersion() {
                            return "1.1.0";
                        }

                        /**
                         * 编译定义并检查字段、类型和业务权限。
                         *
                         * @param dsl 报表定义
                         * @param descriptor 可信数据集契约
                         * @param actor 当前服务端身份
                         * @return 当前处理返回的固定结果
                         */
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
                        /**
                         * 重新解析当前账户和业务权限。
                         *
                         * @param tenant 当前租户标识
                         * @param id 资源标识
                         * @return 当前处理返回的固定结果
                         */
                        public ReportActor resolve(String tenant, long id) {
                            return f.actors.get(id);
                        }

                        /**
                         * 校验授权主体属于当前租户。
                         *
                         * @param tenant 当前租户标识
                         * @param type 当前操作的 {@code type} 输入
                         * @param id 资源标识
                         * @return 当前检查或条件操作是否成功
                         */
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

    /** 验证 {@code duplicateUnknownAndDisabledExecutorsAreExplicitlyRejected} 场景下的结果与边界条件。 */
    @Test
    void duplicateUnknownAndDisabledExecutorsAreExplicitlyRejected() {
        var compiler = new ReportCompiler(new ObjectMapper());
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

    /** 验证 {@code snapshotKeepsTheSelectedExecutorsCalculatedSummaries} 场景下的结果与边界条件。 */
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
                        /**
                         * 基于精确值计算列与汇总。
                         *
                         * @param report 已编译报表
                         * @param rows 待处理业务行
                         * @param input 待校验输入
                         * @return 当前处理返回的固定结果
                         */
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

    /**
     * 验证 {@code actualLiveSnapshotLayoutAndExportUseThePublishedExecutorAfterDefaultChanges}
     * 场景下的结果与边界条件。
     */
    @Test
    void actualLiveSnapshotLayoutAndExportUseThePublishedExecutorAfterDefaultChanges()
            throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            var oldCalls = new AtomicInteger();
            var nextCalls = new AtomicInteger();
            var old =
                    new V1ReportEngineExecutor(f.compiler) {
                        /**
                         * 基于精确值计算列与汇总。
                         *
                         * @param c 当前操作的 {@code c} 输入
                         * @param r 当前操作的 {@code r} 输入
                         * @param p 当前操作的 {@code p} 输入
                         * @return 当前处理返回的固定结果
                         */
                        @Override
                        public ReportCalculator.CalculatedData calculate(
                                CompiledReport c,
                                List<Map<String, Object>> r,
                                Map<String, Object> p) {
                            oldCalls.incrementAndGet();
                            return super.calculate(c, r, p);
                        }

                        /**
                         * 计算单行精确展示值。
                         *
                         * @param c 当前操作的 {@code c} 输入
                         * @param r 当前操作的 {@code r} 输入
                         * @param p 当前操作的 {@code p} 输入
                         * @return 当前处理返回的固定结果
                         */
                        @Override
                        public Map<String, TypedValue> calculateRow(
                                CompiledReport c, Map<String, Object> r, Map<String, Object> p) {
                            oldCalls.incrementAndGet();
                            return super.calculateRow(c, r, p);
                        }

                        /**
                         * 使用固定字体计算页面坐标和断行。
                         *
                         * @param c 当前操作的 {@code c} 输入
                         * @param d 当前操作的 {@code d} 输入
                         * @param p 当前操作的 {@code p} 输入
                         * @param font 可信私有字体路径
                         * @return 当前处理返回的固定结果
                         */
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
                        /**
                         * 读取不可变执行器版本标识。
                         *
                         * @return 当前处理返回的固定结果
                         */
                        @Override
                        public String engineVersion() {
                            return "1.1.0";
                        }

                        /**
                         * 编译定义并检查字段、类型和业务权限。
                         *
                         * @param d 当前操作的 {@code d} 输入
                         * @param s 当前操作的 {@code s} 输入
                         * @param a 当前操作的 {@code a} 输入
                         * @return 当前处理返回的固定结果
                         */
                        @Override
                        public CompiledReport compile(
                                ReportDsl d, DatasetDescriptor s, ReportActor a) {
                            nextCalls.incrementAndGet();
                            return super.compile(d, s, a);
                        }
                    };
            var auth =
                    new ReportAuthorizationPort() {
                        /**
                         * 重新解析当前账户和业务权限。
                         *
                         * @param tenant 当前租户标识
                         * @param id 资源标识
                         * @return 当前处理返回的固定结果
                         */
                        public ReportActor resolve(String tenant, long id) {
                            return f.actors.get(id);
                        }

                        /**
                         * 校验授权主体属于当前租户。
                         *
                         * @param t 当前操作的 {@code t} 输入
                         * @param k 当前操作的 {@code k} 输入
                         * @param id 资源标识
                         * @return 当前检查或条件操作是否成功
                         */
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
            var properties = new ReportProperties();
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
                    f.store.claimJob(job, "test", Instant.now().plusSeconds(60)).orElseThrow();
            var output = new ByteArrayOutputStream();
            new ReportExportRenderer(runtime, properties)
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
