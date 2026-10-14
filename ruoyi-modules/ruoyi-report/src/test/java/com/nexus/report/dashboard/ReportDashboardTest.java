package com.nexus.report.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.Grant;
import com.nexus.report.extension.*;
import com.nexus.report.runtime.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.util.*;

/** 仪表盘独立发布、逐组件权限、收藏及跨节点刷新配额集成回归。 */
class ReportDashboardTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行revokedWidgetFailsLocallyAndPersonalFavoritesNeverGrantAuthority对应的受控处理。 */
    @Test
    void revokedWidgetFailsLocallyAndPersonalFavoritesNeverGrantAuthority() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var service =
                    new ReportDashboardService(
                            resources, f.jdbc, f.store, f.definitions, f.compiler);
            var old = f.dsl("SAFE_METRIC");
            var safe =
                    new ReportDsl(
                            1,
                            old.code(),
                            old.title(),
                            old.kind(),
                            old.datasetCode(),
                            old.datasetVersionId(),
                            List.of(old.columns().get(0)),
                            old.parameters(),
                            old.page(),
                            List.of(),
                            List.of(),
                            List.of(),
                            true);
            var report = f.definitions.create(f.owner, safe, null);
            var version = f.definitions.publish(f.owner, report.id(), report.revision());
            f.store.replaceAcl(
                    f.owner, "REPORT", report.id(), List.of(new Grant("USER", "8", "VIEW")));
            var secretDsl =
                    new ReportDsl(
                            1,
                            "SECRET_WIDGET",
                            "受限组件",
                            ReportDsl.Kind.TABLE,
                            old.datasetCode(),
                            old.datasetVersionId(),
                            List.of(new ReportDsl.Column("secret", "受控字段", 100, 0, null, null)),
                            List.of(),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            true);
            var secretReport = f.definitions.create(f.owner, secretDsl, null);
            var secretVersion =
                    f.definitions.publish(f.owner, secretReport.id(), secretReport.revision()).id();
            f.store.replaceAcl(
                    f.owner, "REPORT", secretReport.id(), List.of(new Grant("USER", "8", "VIEW")));
            var good =
                    new DashboardDefinition.Widget(
                            "good",
                            "METRIC",
                            1,
                            "可见指标",
                            version.id(),
                            Map.of(),
                            null,
                            "code",
                            "COUNT_ROWS",
                            "NONE",
                            null,
                            null,
                            new DashboardDefinition.Layout(0, 0, 6, 3, 0),
                            List.of());
            var bad =
                    new DashboardDefinition.Widget(
                            "restricted",
                            "TABLE",
                            1,
                            "受限组件",
                            secretVersion,
                            Map.of(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            new DashboardDefinition.Layout(6, 0, 6, 4, 1),
                            List.of());
            var model = new DashboardDefinition(1, "权限仪表盘", List.of(), List.of(good, bad), 30);
            var dashboard = service.create(f.owner, "DASHBOARD_TEST", "权限仪表盘", model);
            service.publish(f.owner, dashboard.id(), dashboard.revision());
            f.store.replaceAcl(
                    f.owner, "DASHBOARD", dashboard.id(), List.of(new Grant("USER", "8", "VIEW")));
            var viewer =
                    RuntimeReviewFixture.actor(
                            8,
                            Set.of(
                                    "report:dashboard:list",
                                    "report:runtime:preview",
                                    "wm:stock:list"),
                            ReportScope.all(8));
            f.actors.put(8L, viewer);
            var rendered = service.render(viewer, dashboard.id(), Map.of(), List.of());
            assertNull(rendered.widgets().get(0).error());
            assertNotNull(rendered.widgets().get(0).data());
            assertEquals("SCOPE_DENIED", rendered.widgets().get(1).error());
            assertNull(rendered.widgets().get(1).data());
            int[] reads = {0};
            f.provider.onQuery =
                    () -> {
                        if (++reads[0] == 2)
                            f.store.replaceAcl(f.owner, "REPORT", report.id(), List.of());
                    };
            var ownerRender = service.render(f.owner, dashboard.id(), Map.of(), List.of());
            f.provider.onQuery = () -> {};
            f.actors.put(
                    8L,
                    RuntimeReviewFixture.actor(
                            8,
                            Set.of(
                                    "report:dashboard:list",
                                    "report:runtime:preview",
                                    "wm:stock:list",
                                    "wm:stock:secret"),
                            ReportScope.all(8)));
            f.store.replaceAcl(
                    f.owner, "REPORT", report.id(), List.of(new Grant("USER", "8", "VIEW")));
            reads[0] = 0;
            f.provider.onQuery =
                    () -> {
                        if (++reads[0] == 2)
                            f.store.replaceAcl(f.owner, "REPORT", report.id(), List.of());
                    };
            var rechecked = service.render(f.actors.get(8L), dashboard.id(), Map.of(), List.of());
            assertNull(rechecked.widgets().get(0).data());
            assertEquals("SCOPE_DENIED", rechecked.widgets().get(0).error());
            f.provider.onQuery = () -> {};
            service.favorite(viewer, dashboard.id(), true);
            assertEquals(List.of(dashboard.id()), service.favorites(viewer));
            f.store.replaceAcl(f.owner, "DASHBOARD", dashboard.id(), List.of());
            assertTrue(service.favorites(viewer).isEmpty());
            assertThrows(
                    ReportFault.class,
                    () -> service.render(viewer, dashboard.id(), Map.of(), List.of()));
        }
    }

    /** 执行refreshQuotaIsSharedAcrossServiceInstancesAndStopsAtTwelve对应的受控处理。 */
    @Test
    void refreshQuotaIsSharedAcrossServiceInstancesAndStopsAtTwelve() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var a = new DashboardRefreshQuota(f.jdbc, resources);
            var b = new DashboardRefreshQuota(f.jdbc, resources);
            for (int i = 0; i < 12; i++) (i % 2 == 0 ? a : b).reserve(f.owner);
            assertThrows(ReportFault.class, () -> a.reserve(f.owner));
            assertEquals(
                    12,
                    f.jdbc.queryForObject(
                            "SELECT counter FROM rpt_refresh_quota WHERE scope_key='GLOBAL'",
                            Integer.class));
        }
    }
}
