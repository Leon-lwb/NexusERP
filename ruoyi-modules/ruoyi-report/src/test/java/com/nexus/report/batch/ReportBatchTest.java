package com.nexus.report.batch;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.extension.*;
import com.nexus.report.runtime.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.util.*;

/** 固定批次逐项入队、幂等和权限撤销回归。 */
class ReportBatchTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行batchQueuesEachFixedVersionOnceAndRevocationBlocksOutstandingItem对应的受控处理。 */
    @Test
    void batchQueuesEachFixedVersionOnceAndRevocationBlocksOutstandingItem() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(fixture.jdbc);
            fixture.store.admissionLimits(50, 100, 1000);
            var resources =
                    new ReportResourceStore(
                            fixture.jdbc,
                            new DataSourceTransactionManager(fixture.jdbc.getDataSource()),
                            fixture.store);
            var old =
                    fixture.definitions.versionDsl(
                            fixture.store
                                    .version(
                                            "A",
                                            fixture.store
                                                    .definition("A", fixture.definition.id())
                                                    .orElseThrow()
                                                    .publishedVersion())
                                    .orElseThrow());
            var document =
                    new ReportDsl(
                            1,
                            "BATCH_DOCUMENT",
                            "批量单据",
                            ReportDsl.Kind.DOCUMENT,
                            old.datasetCode(),
                            old.datasetVersionId(),
                            old.columns(),
                            List.of(new ReportDsl.Parameter("customerId", "客户", "ID", false, null)),
                            old.page(),
                            old.headerRows(),
                            old.footerRows(),
                            old.summaries(),
                            true);
            var draft = fixture.definitions.create(fixture.owner, document, null);
            var version = fixture.definitions.publish(fixture.owner, draft.id(), draft.revision());
            var service =
                    new ReportBatchService(
                            resources,
                            fixture.jdbc,
                            fixture.store,
                            fixture.definitions,
                            fixture.runtime,
                            fixture.compiler);
            var batch =
                    service.create(
                            fixture.owner,
                            "BATCH_TEST",
                            "固定批次",
                            List.of(
                                    new ReportBatchService.Document(version.id(), Map.of()),
                                    new ReportBatchService.Document(
                                            version.id(),
                                            Map.of("customerId", "9223372036854775807"))));
            service.start(fixture.owner, batch.id());
            service.start(fixture.owner, batch.id());
            var items = service.items(fixture.owner, batch.id());
            assertEquals(2, items.size());
            assertNotNull(items.get(0).jobId());
            assertNotEquals(items.get(0).jobId(), items.get(1).jobId());
            assertEquals(2, fixture.runtime.jobs(fixture.owner).size());
            fixture.actors.put(
                    8L,
                    RuntimeReviewFixture.actor(8, Set.of("report:batch:list"), ReportScope.all(8)));
            fixture.store.replaceAcl(
                    fixture.owner,
                    "BATCH",
                    batch.id(),
                    List.of(new com.nexus.report.domain.ReportRecords.Grant("USER", "8", "VIEW")));
            assertNull(service.list(fixture.actors.get(8L)).get(0).json());
            fixture.actors.put(
                    7L,
                    RuntimeReviewFixture.actor(
                            7,
                            Set.of("report:batch:manage", "report:batch:list"),
                            ReportScope.all(7)));
            assertThrows(ReportFault.class, () -> service.items(fixture.owner, batch.id()));
        }
    }
}
