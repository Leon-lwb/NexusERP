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

class ReportDatasetVersionTest {
    @TempDir Path directory;

    @Test
    void oldContractRemainsVisibleAfterAnotherVersionIsPublished() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var next =
                    new ReportDatasetProvider() {
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

                        public DatasetResult query(ReportExecutionContext c, DatasetQuery q) {
                            throw new UnsupportedOperationException();
                        }
                    };
            var registry = new ReportProviderRegistry(List.of(f.provider, next));
            var authorization =
                    new ReportAuthorizationPort() {
                        public ReportActor resolve(String tenant, long user) {
                            return f.definitions.resolve(tenant, user);
                        }

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
