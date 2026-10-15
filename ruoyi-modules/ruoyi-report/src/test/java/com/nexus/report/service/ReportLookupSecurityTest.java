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

    /** 按值回显复用真实数据集版本和权限校验，保持完整ID。 */
    @Test
    void selectedValueRetainsPrecisionAndRejectsUndeclaredValues() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var service = selectedService(f, () -> {}, "9223372036854775807");
            assertEquals(
                    "合成客户",
                    service.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775807")
                            .orElseThrow()
                            .label());
            assertThrows(
                    ReportFault.class,
                    () -> service.selected(f.viewer, f.dataset.publishedVersion(), "unknown", "1"));
            assertThrows(
                    DatasetAccessException.class,
                    () ->
                            service.selected(
                                    f.viewer, f.dataset.publishedVersion(), "customerId", "1e18"));
            assertThrows(
                    DatasetAccessException.class,
                    () ->
                            service.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775808"));
            f.store.replaceAcl(f.root, "DATASET", f.dataset.id(), List.of());
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775807"));
        }
    }

    /** 数据集样例权限可使用候选，仍要通过业务权限和资源ACL。 */
    @Test
    void datasetSampleUsersCanResolveWithTheirExistingDatasetPermission() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.actors.put(
                    8L,
                    RuntimeReviewFixture.actor(
                            8, Set.of("report:dataset:list", "wm:stock:list"), ReportScope.all(8)));
            var service = selectedService(f, () -> {}, "9223372036854775807");
            assertEquals(1, service.bindings(f.viewer, f.dataset.publishedVersion()).size());
            assertTrue(
                    service.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775807")
                            .isPresent());
        }
    }

    /** 读取名称期间发生权限变化，返回前必须拒绝旧结果。 */
    @Test
    void selectedResultsAreDiscardedAfterScopeChangesOrMismatchedKeys() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var mismatch = selectedService(f, () -> {}, "2");
            assertThrows(
                    ReportFault.class,
                    () ->
                            mismatch.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775807"));
            var changed =
                    selectedService(
                            f,
                            () ->
                                    f.actors.put(
                                            8L,
                                            RuntimeReviewFixture.actor(
                                                    8,
                                                    Set.of(
                                                            "report:runtime:preview",
                                                            "wm:stock:list"),
                                                    new ReportScope(false, Set.of(20L), false, 8))),
                            "9223372036854775807");
            assertThrows(
                    ReportFault.class,
                    () ->
                            changed.selected(
                                    f.viewer,
                                    f.dataset.publishedVersion(),
                                    "customerId",
                                    "9223372036854775807"));
        }
    }

    /**
     * 创建只返回合成名称的候选服务，保留真实版本和ACL链路。
     *
     * @param f 隔离元数据夹具
     * @param during 回显过程中执行的并发状态变化
     * @param returnedKey 合成提供者返回的完整键
     * @return 已安装合成绑定的真实服务
     */
    private ReportLookupService selectedService(
            RuntimeReviewFixture f, Runnable during, String returnedKey) {
        var binding =
                new ReportLookupBinding("SECURE_TEST", 1, "customerId", "客户", "wm:stock:list");
        var provider =
                new ReportLookupProvider() {
                    /** 返回固定的合成参数绑定。 */
                    public List<ReportLookupBinding> bindings() {
                        return List.of(binding);
                    }

                    /** 返回有界合成候选，不访问外部数据库。 */
                    public Result query(ReportLookupBinding b, ReportExecutionContext c, Query q) {
                        return new Result(List.of(), 0);
                    }

                    /** 执行合成并发回调后返回待复核的候选。 */
                    public Optional<Option> selected(
                            ReportLookupBinding b, ReportExecutionContext c, String value) {
                        assertEquals("A", c.tenantId());
                        assertEquals("9223372036854775807", value);
                        during.run();
                        return Optional.of(new Option(returnedKey, "合成客户"));
                    }
                };
        var versions =
                new ReportDatasetVersionService(
                        f.store,
                        new ReportProviderRegistry(List.of(f.provider)),
                        f.definitions,
                        f.compiler);
        return new ReportLookupService(versions, f.definitions, List.of(provider));
    }

    /** 回显单个参数不要求先填写同一契约中的其他必填业务参数。 */
    @Test
    void selectedValidationIsIndependentFromOtherRequiredParameters() {
        var actor =
                RuntimeReviewFixture.actor(
                        8, Set.of("report:runtime:preview", "wm:stock:list"), ReportScope.all(8));
        var definitions = org.mockito.Mockito.mock(ReportDefinitionService.class);
        var versions = org.mockito.Mockito.mock(ReportDatasetVersionService.class);
        var descriptor =
                new DatasetDescriptor(
                        "SECURE_TEST",
                        "合成契约",
                        1,
                        "wm:stock:list",
                        false,
                        List.of(),
                        List.of(
                                new DatasetParameter("customerId", "客户", ValueType.ID, false, null),
                                new DatasetParameter(
                                        "other", "其他必填", ValueType.STRING, true, null)),
                        List.of());
        org.mockito.Mockito.when(definitions.resolve("A", 8)).thenReturn(actor);
        org.mockito.Mockito.when(versions.forLookup(actor, "v"))
                .thenReturn(
                        new ReportDatasetVersionService.View(
                                "v", "d", "SECURE_TEST", 1, true, descriptor));
        var binding =
                new ReportLookupBinding("SECURE_TEST", 1, "customerId", "客户", "wm:stock:list");
        var provider =
                new ReportLookupProvider() {
                    /** 只安装当前测试的客户参数。 */
                    public List<ReportLookupBinding> bindings() {
                        return List.of(binding);
                    }

                    /** 按值测试不使用模糊检索。 */
                    public Result query(ReportLookupBinding b, ReportExecutionContext c, Query q) {
                        throw new AssertionError("不应模糊查询");
                    }

                    /** 返回经过服务层单参数校验的完整值。 */
                    public Optional<Option> selected(
                            ReportLookupBinding b, ReportExecutionContext c, String value) {
                        return Optional.of(new Option(value, "合成客户"));
                    }
                };
        var service = new ReportLookupService(versions, definitions, List.of(provider));
        assertEquals(
                "9223372036854775807",
                service.selected(actor, "v", "customerId", "9223372036854775807")
                        .orElseThrow()
                        .value());
    }
}
