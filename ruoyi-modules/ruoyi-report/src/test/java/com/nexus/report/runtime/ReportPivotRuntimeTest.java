package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.*;
import com.nexus.report.pivot.*;
import com.nexus.report.service.*;
import com.nexus.report.version.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

/** 第二版发布、固定运行、钻取、撤权与旧执行器隔离回归。 */
class ReportPivotRuntimeTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行pivotRunsUseSecondEngineAndKeepRawSnapshotForDrill对应的受控处理。 */
    @Test
    void pivotRunsUseSecondEngineAndKeepRawSnapshotForDrill() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var registry =
                    new ReportEngineRegistry(
                            List.of(
                                    new V1ReportEngineExecutor(fixture.compiler),
                                    new V2ReportEngineExecutor(fixture.compiler)),
                            Map.of(1, "1.0.0", 2, "2.0.0"),
                            Set.of());
            var auth =
                    new ReportAuthorizationPort() {
                        /**
                         * 执行resolve对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param user 当前受控操作的user输入
                         * @return 当前固定契约或处理结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            return fixture.actors.get(user);
                        }

                        /**
                         * 执行subjectExists对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param type 当前受控操作的type输入
                         * @param id 当前受控操作的id输入
                         * @return 当前固定契约或处理结果
                         */
                        public boolean subjectExists(String tenant, String type, long id) {
                            return true;
                        }
                    };
            var definitions =
                    new ReportDefinitionService(
                            fixture.store,
                            new ReportProviderRegistry(List.of(fixture.provider)),
                            auth,
                            fixture.compiler,
                            registry);
            var dsl =
                    new ReportDsl(
                            2,
                            "PIVOT_TEST",
                            "交叉测试",
                            ReportDsl.Kind.PIVOT,
                            fixture.dataset.code(),
                            fixture.dataset.publishedVersion(),
                            List.of(new ReportDsl.Column("code", "编码", 80, 0, null, null)),
                            List.of(),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            true,
                            new PivotDefinition(
                                    List.of("code"),
                                    List.of(),
                                    List.of(
                                            new PivotDefinition.Measure(
                                                    "code", "COUNT_ROWS", "行数", 0)),
                                    true,
                                    true,
                                    0));
            assertThrows(
                    ReportFault.class,
                    () ->
                            fixture.compiler.compile(
                                    dsl, fixture.provider.descriptor(), fixture.owner));
            var draft = definitions.create(fixture.owner, dsl, null);
            var version = definitions.publish(fixture.owner, draft.id(), draft.revision());
            assertEquals("2.0.0", version.engine());
            var runtime =
                    new ReportRuntimeService(
                            fixture.store,
                            definitions,
                            fixture.compiler,
                            fixture.snapshots,
                            fixture.artifacts,
                            new ReportProperties(),
                            fixture.json);
            var run = runtime.create(fixture.owner, draft.id(), "LIVE", Map.of());
            assertEquals("SNAPSHOT", run.mode());
            var result = runtime.pivot(fixture.owner, run.id());
            assertEquals("1", result.totals().get(0).values().get(0).value());
            fixture.provider.onQuery =
                    () -> {
                        throw new AssertionError("钻取不可重查业务库");
                    };
            assertEquals(
                    "001",
                    runtime.drill(
                                    fixture.owner,
                                    run.id(),
                                    result.rows().get(0).cells().get(0).coordinate(),
                                    0,
                                    20)
                            .rows()
                            .get(0)
                            .get("code")
                            .value());
            assertEquals(
                    "1",
                    runtime.page(fixture.owner, run.id(), 1, 20, null, false)
                            .rows()
                            .get(0)
                            .get("cell_0_0")
                            .value());
            fixture.actors.remove(fixture.owner.userId());
            assertThrows(ReportFault.class, () -> runtime.pivot(fixture.owner, run.id()));
        }
    }
}
