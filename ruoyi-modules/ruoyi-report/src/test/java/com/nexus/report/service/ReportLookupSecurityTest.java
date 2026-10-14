package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

/** 报表选择器权限边界测试，负责当前模块对应的契约或处理职责。 */
class ReportLookupSecurityTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code undeclaredUnsupportedRevokedAndCrossTenantLookupsFail} 场景下的结果与边界条件。 */
    @Test
    void undeclaredUnsupportedRevokedAndCrossTenantLookupsFail() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var versions =
                    new ReportDatasetVersionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            f.definitions,
                            f.compiler);
            var binding =
                    new ReportLookupBinding("SECURE_TEST", 1, "customerId", "客户", "wm:stock:list");
            var provider =
                    new ReportLookupProvider() {
                        /**
                         * 列出固定契约已安装且有权限的选择器。
                         *
                         * @return 当前处理返回的固定结果
                         */
                        public List<ReportLookupBinding> bindings() {
                            return List.of(binding);
                        }

                        /**
                         * 执行受控只读查询。
                         *
                         * @param b 当前操作的 {@code b} 输入
                         * @param context 服务端执行上下文
                         * @param query 已声明字段和筛选参数
                         * @return 当前处理返回的固定结果
                         */
                        public Result query(
                                ReportLookupBinding b,
                                ReportExecutionContext context,
                                Query query) {
                            assertEquals("A", context.tenantId());
                            assertFalse(context.scopes().isEmpty());
                            return new Result(
                                    List.of(new Option("9223372036854775807", "合成客户")), 1);
                        }
                    };
            var service = new ReportLookupService(versions, f.definitions, List.of(provider));
            assertEquals(1, service.bindings(f.viewer, f.dataset.publishedVersion()).size());
            assertEquals(
                    "9223372036854775807",
                    service.query(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    new ReportLookupProvider.Query("", 1, 10))
                            .options()
                            .get(0)
                            .value());
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.query(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "unknown",
                                    new ReportLookupProvider.Query("", 1, 10)));
            f.actors.put(
                    8L,
                    RuntimeReviewFixture.actor(
                            8,
                            Set.of("report:runtime:preview", "wm:stock:list"),
                            ReportScope.all(8)));
            assertEquals(1, service.bindings(f.viewer, f.dataset.publishedVersion()).size());
            assertThrows(
                    ReportFault.class, () -> versions.read(f.viewer, f.dataset.publishedVersion()));
            f.store.replaceAcl(f.root, "DATASET", f.dataset.id(), List.of());
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.query(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    new ReportLookupProvider.Query("", 1, 10)));
        }
    }

    /**
     * 验证 {@code lookupEntityUsesItsOwnBusinessScopeInsteadOfTheReportEntitiesScope} 场景下的结果与边界条件。
     */
    @Test
    void lookupEntityUsesItsOwnBusinessScopeInsteadOfTheReportEntitiesScope() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var actor =
                    new ReportActor(
                            "A",
                            8,
                            10,
                            false,
                            List.of(
                                    new ReportRole(
                                            1,
                                            Set.of("report:runtime:preview", "wm:stock:list"),
                                            new ReportScope(false, Set.of(10L), false, 8)),
                                    new ReportRole(
                                            2,
                                            Set.of("bd:client:list"),
                                            new ReportScope(false, Set.of(20L), false, 8))));
            f.actors.put(8L, actor);
            var binding =
                    new ReportLookupBinding("SECURE_TEST", 1, "customerId", "客户", "bd:client:list");
            var provider =
                    new ReportLookupProvider() {
                        /**
                         * 列出固定契约已安装且有权限的选择器。
                         *
                         * @return 当前处理返回的固定结果
                         */
                        public List<ReportLookupBinding> bindings() {
                            return List.of(binding);
                        }

                        /**
                         * 执行受控只读查询。
                         *
                         * @param b 当前操作的 {@code b} 输入
                         * @param context 服务端执行上下文
                         * @param query 已声明字段和筛选参数
                         * @return 当前处理返回的固定结果
                         */
                        public Result query(
                                ReportLookupBinding b,
                                ReportExecutionContext context,
                                Query query) {
                            boolean allowed =
                                    context.scopes().stream()
                                            .allMatch(scope -> scope.departments().contains(20L));
                            return new Result(
                                    allowed ? List.of(new Option("1", "合成客户")) : List.of(),
                                    allowed ? 1 : 0);
                        }
                    };
            var versions =
                    new ReportDatasetVersionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            f.definitions,
                            f.compiler);
            var service = new ReportLookupService(versions, f.definitions, List.of(provider));
            assertEquals(
                    1,
                    service.query(
                                    actor,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    new ReportLookupProvider.Query("", 1, 20))
                            .total());
        }
    }
}
