package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

/** 报表数据集版本测试，负责当前模块对应的契约或处理职责。 */
class ReportDatasetVersionTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code oldContractRemainsVisibleAfterAnotherVersionIsPublished} 场景下的结果与边界条件。 */
    @Test
    void oldContractRemainsVisibleAfterAnotherVersionIsPublished() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var next =
                    new ReportDatasetProvider() {
                        /**
                         * 读取固定代码契约描述。
                         *
                         * @return 当前处理返回的固定结果
                         */
                        public DatasetDescriptor descriptor() {
                            return new DatasetDescriptor(
                                    "SECURE_TEST",
                                    "新版",
                                    2,
                                    "wm:stock:list",
                                    false,
                                    List.of(
                                            new DatasetField("code", "编码", ValueType.STRING),
                                            new DatasetField("quantity", "数量", ValueType.DECIMAL)),
                                    List.of(),
                                    List.of("code"));
                        }

                        /**
                         * 执行受控只读查询。
                         *
                         * @param c 当前操作的 {@code c} 输入
                         * @param q 当前操作的 {@code q} 输入
                         * @return 当前处理返回的固定结果
                         */
                        public DatasetResult query(ReportExecutionContext c, DatasetQuery q) {
                            throw new UnsupportedOperationException();
                        }
                    };
            var registry = new ReportProviderRegistry(List.of(f.provider, next));
            var authorization =
                    new ReportAuthorizationPort() {
                        /**
                         * 重新解析当前账户和业务权限。
                         *
                         * @param tenant 当前租户标识
                         * @param user 当前操作的 {@code user} 输入
                         * @return 当前处理返回的固定结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            return f.definitions.resolve(tenant, user);
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
            var definitions =
                    new ReportDefinitionService(f.store, registry, authorization, f.compiler);
            var published = definitions.registerDataset(f.root, "SECURE_TEST", 2);
            var service =
                    new ReportDatasetVersionService(f.store, registry, definitions, f.compiler);
            var old = service.read(f.owner, f.dataset.publishedVersion());
            assertEquals(1, old.descriptor().version());
            assertFalse(old.current());
            assertTrue(old.descriptor().fields().stream().anyMatch(x -> x.name().equals("secret")));
            var diff = service.diff(f.owner, old.id(), published.publishedVersion());
            assertTrue(diff.breaking());
            assertTrue(
                    diff.changes().stream()
                            .anyMatch(
                                    x -> x.name().equals("quantity") && x.kind().equals("ADDED")));
            assertTrue(
                    diff.changes().stream()
                            .anyMatch(
                                    x -> x.name().equals("secret") && x.kind().equals("REMOVED")));
            assertFalse(
                    service.read(f.viewer, old.id()).descriptor().fields().stream()
                            .anyMatch(x -> x.name().equals("secret")));
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            f.resolveOverride =
                    (tenant, user) -> {
                        if (calls.incrementAndGet() == 5)
                            f.actors.put(
                                    7L,
                                    RuntimeReviewFixture.actor(
                                            7,
                                            Set.of("report:dataset:list", "wm:stock:list"),
                                            ReportScope.all(7)));
                        return f.actors.get(user);
                    };
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            service.diff(
                                                    f.owner,
                                                    old.id(),
                                                    published.publishedVersion()))
                            .code());
            f.resolveOverride = null;
            assertThrows(
                    ReportFault.class,
                    () -> service.read(new ReportActor("B", 7, 10, true, List.of()), old.id()));
            f.store.replaceAcl(f.root, "DATASET", f.dataset.id(), List.of());
            assertThrows(ReportFault.class, () -> service.read(f.viewer, old.id()));
        }
    }

    /** 验证 {@code tamperedOrDisabledContractsAreNotReturned} 场景下的结果与边界条件。 */
    @Test
    void tamperedOrDisabledContractsAreNotReturned() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var service =
                    new ReportDatasetVersionService(
                            f.store,
                            new ReportProviderRegistry(List.of(f.provider)),
                            f.definitions,
                            f.compiler);
            f.jdbc.update(
                    "UPDATE rpt_dataset_version SET checksum='tampered' WHERE version_id=?",
                    Long.valueOf(f.dataset.publishedVersion()));
            assertEquals(
                    "VERSION_INVALID",
                    assertThrows(
                                    ReportFault.class,
                                    () -> service.read(f.owner, f.dataset.publishedVersion()))
                            .code());
        }
    }
}
