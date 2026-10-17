package com.nexus.report.batch;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

/** 合并的LOCAL及S3物化语义统一进入既有硬配额，失败及断连均恢复占用。 */
class ReportBatchPdfQuotaTest {
    /** 当前测试拥有的私有目录。 */
    @TempDir Path directory;

    /** 成功和断连交付之后，只有原任务对象仍占用配额。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedMaterializationAndDisconnectReturnToOriginalQuota(boolean disconnect) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = ReportBatchPdfTest.successful(fixture, 2, 0);
            var quota = new ReportQuotaService(fixture.runtime.jdbc, 30L * 1024 * 1024, 30L * 1024 * 1024, 1000, 1000);
            var shared = new ReportBatchArtifacts(new LocalReportArtifactStore(directory, 20L * 1024 * 1024), true);
            var artifacts = new QuotaReportArtifactStore(shared, quota, 20L * 1024 * 1024, "batch-shared");
            var before = quota.usage("A");
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            try (var prepared = pdfs.merge(fixture.runtime.owner, batch)) {
                assertTrue(quota.usage("A").bytes() > before.bytes());
                assertTrue(quota.usage("A").objects() > before.objects());
                if (disconnect) {
                    assertThrows(IOException.class, () -> prepared.write(new OutputStream() {
                        /** 模拟客户端在交付过程中断开。 */
                        public void write(int value) throws IOException { throw new IOException("断连"); }
                    }));
                } else prepared.write(OutputStream.nullOutputStream());
            }
            assertEquals(before, quota.usage("A"));
            assertTrue(shared.pending.isEmpty());
            assertEquals(0, shared.commits);
        }
    }

    /** S3物化需要的节点暂存没有容量时必须明确失败，不能绕过配额复制。 */
    @Test
    void refusesSharedStagingWhenHardQuotaCannotReserveOneChunk() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = ReportBatchPdfTest.successful(fixture, 1, 0);
            long maximum = 512 * 1024;
            var quota = new ReportQuotaService(fixture.runtime.jdbc, maximum, maximum, 1000, 1000);
            var shared = new ReportBatchArtifacts(new LocalReportArtifactStore(directory, 20L * 1024 * 1024), true);
            var artifacts = new QuotaReportArtifactStore(shared, quota, 20L * 1024 * 1024, "batch-limited");
            var before = quota.usage("A");
            var fault = assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch));
            assertEquals("ARTIFACT_QUOTA_EXCEEDED", fault.code());
            assertEquals(before, quota.usage("A"));
            assertTrue(shared.pending.isEmpty());
        }
    }

    /** 第二份单据物化期间，第一份已到期也必须阻断整个批次。 */
    @Test
    void rechecksAlreadyMergedSourceWhenLaterItemMaterializes() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = ReportBatchPdfTest.successful(fixture, 2, 0);
            String first = fixture.service.items(fixture.runtime.owner, batch).get(0).jobId();
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            artifacts.onMaterialize = () -> {
                if (artifacts.materialized == 2)
                    fixture.runtime.jdbc.update("UPDATE rpt_export_job SET expires_at=? WHERE job_id=?",
                            Timestamp.from(Instant.now().minusSeconds(1)), Long.parseLong(first));
            };
            var fault = assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch));
            assertEquals("BATCH_ITEM_EXPIRED", fault.code());
            assertTrue(fault.getMessage().contains("item_no=0"));
            assertEquals(artifacts.materialized, artifacts.released);
            assertTrue(artifacts.pending.isEmpty());
        }
    }
}
