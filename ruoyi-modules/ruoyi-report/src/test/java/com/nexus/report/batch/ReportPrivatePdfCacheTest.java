package com.nexus.report.batch;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.runtime.RuntimeReviewFixture;
import com.nexus.report.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

/** PDFBox随机缓存必须正确覆盖、清空，并实际扣减和释放原硬配额。 */
class ReportPrivatePdfCacheTest {
    /** 当前测试拥有的私有目录。 */
    @TempDir Path directory;

    /** 随机覆盖、范围视图与清空不能破坏PDFBox缓存契约。 */
    @Test
    void supportsRandomOverwriteViewsAndLogicalClear() throws Exception {
        var artifacts = new ReportBatchArtifacts(new LocalReportArtifactStore(directory, 1024 * 1024), false);
        var cache = new ReportPrivatePdfCache(artifacts, "A", new ReportPrivatePdfCache.Budget(1024), () -> {});
        try (cache; var buffer = cache.createBuffer()) {
            buffer.write(new byte[] {1, 2, 3});
            buffer.seek(1);
            buffer.write(9);
            try (var view = buffer.createView(0, 3)) {
                var bytes = new byte[3];
                view.readFully(bytes);
                assertArrayEquals(new byte[] {1, 9, 3}, bytes);
            }
            buffer.clear();
            assertEquals(0, buffer.length());
            assertEquals(-1, buffer.read());
            buffer.write(new byte[] {4, 5});
            buffer.seek(0);
            assertEquals(4, buffer.read());
            assertEquals(5, buffer.read());
            assertTrue(buffer.isEOF());
        }
        assertTrue(artifacts.pending.isEmpty());
        assertThrows(IOException.class, cache::createBuffer);
    }

    /** 多份文档共享一个请求缓存预算，不能各自占满字节上限。 */
    @Test
    void combinesBudgetAcrossDocumentCaches() throws Exception {
        var artifacts = new ReportBatchArtifacts(new LocalReportArtifactStore(directory, 1024 * 1024), false);
        var budget = new ReportPrivatePdfCache.Budget(100);
        try (var first = new ReportPrivatePdfCache(artifacts, "A", budget, () -> {});
                var second = new ReportPrivatePdfCache(artifacts, "A", budget, () -> {});
                var left = first.createBuffer(); var right = second.createBuffer()) {
            left.write(new byte[60]);
            assertEquals("BATCH_PDF_BYTES_LIMIT", assertThrows(ReportFault.class, () -> right.write(new byte[41])).code());
            left.close();
            right.write(new byte[100]);
        }
        assertTrue(artifacts.pending.isEmpty());
    }

    /** 逻辑清空之后随机写入的间隙必须是零，不能读到原缓存残留。 */
    @Test
    void clearRemovesOldBytesBeforeRandomWrite() throws Exception {
        var artifacts = new ReportBatchArtifacts(new LocalReportArtifactStore(directory, 1024 * 1024), false);
        try (var cache = new ReportPrivatePdfCache(artifacts, "A", new ReportPrivatePdfCache.Budget(100), () -> {});
                var buffer = cache.createBuffer()) {
            buffer.write(new byte[] {1, 2, 3});
            buffer.clear();
            buffer.seek(2);
            buffer.write(9);
            buffer.seek(0);
            var actual = new byte[3];
            buffer.readFully(actual);
            assertArrayEquals(new byte[] {0, 0, 9}, actual);
        }
        assertTrue(artifacts.pending.isEmpty());
    }

    /** 暂存缓存真实进入主库清单及硬配额，关闭后占用恢复为零。 */
    @Test
    void cacheGrowthUsesRealQuotaAndReleasesReservation() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory.resolve("fixture"))) {
            ReportExtensionSchema.apply(fixture.jdbc);
            var quota = new ReportQuotaService(fixture.jdbc, 4L * 1024 * 1024, 4L * 1024 * 1024, 100, 100);
            var local = new LocalReportArtifactStore(directory.resolve("cache"), 2L * 1024 * 1024);
            var artifacts = new QuotaReportArtifactStore(local, quota, 2L * 1024 * 1024, "batch-cache");
            try (var cache = new ReportPrivatePdfCache(artifacts, "A", new ReportPrivatePdfCache.Budget(2L * 1024 * 1024), () -> {});
                    var buffer = cache.createBuffer()) {
                buffer.write(new byte[1000]);
                assertTrue(quota.usage("A").bytes() >= 1000);
                assertEquals(1, quota.usage("A").objects());
            }
            assertEquals(0, quota.usage("A").bytes());
            assertEquals(0, quota.usage("A").objects());
        }
    }
}
