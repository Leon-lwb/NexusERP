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

class ReportLookupSecurityTest {
    @TempDir Path directory;

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
                        public List<ReportLookupBinding> bindings() {
                            return List.of(binding);
                        }

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
                        public List<ReportLookupBinding> bindings() {
                            return List.of(binding);
                        }

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
