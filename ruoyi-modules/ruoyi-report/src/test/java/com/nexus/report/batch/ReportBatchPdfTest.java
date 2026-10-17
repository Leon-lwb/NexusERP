package com.nexus.report.batch;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.runtime.RuntimeReviewFixture;
import com.nexus.report.storage.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** 固定PDF合并的隔离权限、页序、容量、期限和释放生命周期回归。 */
class ReportBatchPdfTest {
    /** 当前测试独占的私有目录。 */
    @TempDir Path directory;

    /** LOCAL及共享语义均按原单据页序、纸型和页码合并，不重新查询业务。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesItemOrderPaperAndPrintedPageNumbers(boolean shared) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 2, 0);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, shared);
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            fixture.runtime.provider.onQuery = () -> fail("合并不能重新读取业务数据");
            var bytes = new ByteArrayOutputStream();
            try (var merged = pdfs.merge(fixture.runtime.owner, batch)) {
                merged.write(bytes);
                assertEquals(merged.bytes(), bytes.size());
            }
            try (var document = Loader.loadPDF(bytes.toByteArray())) {
                assertEquals(3, document.getNumberOfPages());
                assertEquals(PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth(), 0.01);
                assertEquals(PDRectangle.A4.getHeight(), document.getPage(1).getMediaBox().getHeight(), 0.01);
                assertEquals(PDRectangle.A5.getWidth(), document.getPage(2).getMediaBox().getWidth(), 0.01);
                assertEquals(90, document.getPage(2).getRotation());
                String text = new PDFTextStripper().getText(document);
                assertEquals("DOC11/2DOC12/2DOC21/1", text.replaceAll("\\s+", ""));
            }
            assertEquals(artifacts.materialized, artifacts.released);
            assertTrue(artifacts.pending.isEmpty());
            assertEquals(0, artifacts.commits);
            assertEquals(2, fixture.runtime.runtime.jobs(fixture.runtime.owner).size());
        }
    }

    /** 三个平台权限均不可缺少，且拒绝之前不得开始物化。 */
    @ParameterizedTest
    @ValueSource(strings = {"report:batch:list", "report:runtime:preview", "report:runtime:export"})
    void rejectsEachMissingPlatformPermission(String missing) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var permissions = new HashSet<>(Set.of("report:batch:list", "report:runtime:preview", "report:runtime:export", "wm:stock:list"));
            permissions.remove(missing);
            fixture.runtime.actors.put(7L, RuntimeReviewFixture.actor(7, permissions, ReportScope.all(7)));
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            assertEquals(403, assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties())
                            .merge(fixture.runtime.owner, batch)).status());
            assertEquals(0, artifacts.materialized);
        }
    }

    /** 即使管理员获得资源VIEW也不能领取其他创建者或其他租户的批次。 */
    @Test
    void rejectsOtherOwnerAndTenant() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var other = new ReportActor("A", 8, 10, true, List.of());
            fixture.runtime.actors.put(8L, other);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            var service = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            assertEquals(403, assertThrows(ReportFault.class, () -> service.merge(other, batch)).status());
            var tenant = new ReportActor("B", 7, 10, true, List.of());
            fixture.runtime.resolveOverride = (tenantId, user) -> tenantId.equals("B") ? tenant : fixture.runtime.owner;
            assertEquals(404, assertThrows(ReportFault.class, () -> service.merge(tenant, batch)).status());
            assertEquals(0, artifacts.materialized);
        }
    }

    /** 任意未成功、失败或已取消的单据都必须报出对应项，不允许跳过。 */
    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "FAILED", "CANCELLED", "EXPIRED"})
    void rejectsAnyUnusableChildWithItemLocation(String state) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 2, 0);
            var job = fixture.service.items(fixture.runtime.owner, batch).get(1).jobId();
            fixture.runtime.jdbc.update("UPDATE rpt_export_job SET state=? WHERE job_id=?", state, Long.parseLong(job));
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            var fault = assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch));
            assertTrue(fault.getMessage().contains("item_no=1"));
            assertEquals(0, artifacts.materialized);
        }
    }

    /** 已到期文件、非PDF文件和被其他主体替换的任务不能合并。 */
    @ParameterizedTest
    @ValueSource(strings = {"expiry", "format", "actor", "cancelled", "runExpiry", "offline", "scope"})
    void rejectsExpiredWrongFormatOwnerOrDependencies(String reason) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            String job = fixture.service.items(fixture.runtime.owner, batch).get(0).jobId();
            switch (reason) {
                case "expiry" -> fixture.runtime.jdbc.update("UPDATE rpt_export_job SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
                case "format" -> fixture.runtime.jdbc.update("UPDATE rpt_export_job SET format='XLSX_DATA'");
                case "actor" -> fixture.runtime.jdbc.update("UPDATE rpt_export_job SET actor_id=8");
                case "cancelled" -> fixture.runtime.jdbc.update("UPDATE rpt_export_job SET cancel_requested=1");
                case "runExpiry" -> fixture.runtime.jdbc.update("UPDATE rpt_run SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
                case "offline" -> fixture.runtime.jdbc.update("UPDATE rpt_definition SET status='OFFLINE'");
                case "scope" -> fixture.runtime.narrow(7);
                default -> fail(reason);
            }
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            var failure = assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch));
            assertTrue(failure.getMessage().contains("item_no=0"), job);
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** 缺少子任务、批次下线或资源VIEW被拒绝时不能开始领取。 */
    @Test
    void rejectsUnstartedOfflineAndViewRevokedBatch() {
        try (var fixture = new ReportBatchFixture(directory)) {
            var batch = fixture.service.create(fixture.runtime.owner, "UNSTARTED", "未开始", fixture.documents());
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            assertEquals("BATCH_ITEM_NOT_READY", assertThrows(ReportFault.class, () -> pdfs.merge(fixture.runtime.owner, batch.id())).code());
            fixture.runtime.jdbc.update("UPDATE rpt_batch SET status='OFFLINE'");
            assertEquals("BATCH_OFFLINE", assertThrows(ReportFault.class, () -> pdfs.merge(fixture.runtime.owner, batch.id())).code());
            assertEquals(0, artifacts.materialized);
        }
    }

    /** 元数据校验和不等于实际物化文件时阻断并释放所有物化。 */
    @Test
    void rejectsChecksumMismatchAndReleasesSharedCopy() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            artifacts.wrongChecksum = true;
            var fault = assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch));
            assertEquals("ARTIFACT_INVALID", fault.code());
            assertTrue(fault.getMessage().contains("item_no=0"));
            assertTrue(artifacts.pending.isEmpty());
            assertEquals(artifacts.materialized, artifacts.released);
        }
    }

    /** 已有文件即使元数据一致，内容不是PDF也必须明确失败。 */
    @Test
    void rejectsMalformedPdfAndReleasesScratch() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var job = fixture.runtime.runtime.job(fixture.runtime.owner,
                    fixture.service.items(fixture.runtime.owner, batch).get(0).jobId());
            byte[] broken = "%PDF-1.7\ncorrupt".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            Files.write(fixture.runtime.artifacts.read("A", job.file()), broken);
            fixture.runtime.jdbc.update("UPDATE rpt_export_job SET file_size=?", broken.length);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            assertEquals("BATCH_PDF_INVALID", assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties()).merge(fixture.runtime.owner, batch)).code());
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** 输入总量和实际输出量均不能超过既有产物上限。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boundsInputAndActualOutputBytes(boolean output) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, output ? 1 : 2, output ? 0 : 600_000);
            var properties = new ReportProperties();
            properties.setMaxArtifactBytes(1024 * 1024);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            if (output) artifacts.padOutput = 1_100_000;
            assertEquals("BATCH_PDF_BYTES_LIMIT", assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, properties).merge(fixture.runtime.owner, batch)).code());
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** 页数上限不能通过多个子单据累积绕过。 */
    @Test
    void boundsCombinedPages() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 2, 0);
            var properties = new ReportProperties();
            properties.getBatch().setMaxPages(2);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            assertEquals("BATCH_PDF_PAGE_LIMIT", assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, properties).merge(fixture.runtime.owner, batch)).code());
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** PDF内部页数元数据不可信，必须以实际页面集合计算总页数。 */
    @Test
    void countsActualPagesRatherThanTrustingPdfCountMetadata() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var job = fixture.runtime.runtime.job(fixture.runtime.owner,
                    fixture.service.items(fixture.runtime.owner, batch).get(0).jobId());
            var path = fixture.runtime.artifacts.read("A", job.file());
            var modified = new ByteArrayOutputStream();
            try (var pdf = Loader.loadPDF(Files.readAllBytes(path))) {
                pdf.getPages().getCOSObject().setInt(org.apache.pdfbox.cos.COSName.COUNT, 1);
                pdf.save(modified);
            }
            Files.write(path, modified.toByteArray());
            fixture.runtime.jdbc.update("UPDATE rpt_export_job SET file_size=?", modified.size());
            var properties = new ReportProperties();
            properties.getBatch().setMaxPages(1);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, false);
            assertEquals("BATCH_PDF_PAGE_LIMIT", assertThrows(ReportFault.class,
                    () -> new ReportBatchPdfService(fixture.service, artifacts, properties).merge(fixture.runtime.owner, batch)).code());
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** 配置默认值和边界不可放大为无限页数或无限执行。 */
    @Test
    void validatesBatchConfiguration() {
        var batch = new ReportProperties().getBatch();
        assertEquals(1000, batch.getMaxPages());
        assertEquals(60, batch.getMergeSeconds());
        assertThrows(IllegalArgumentException.class, () -> batch.setMaxPages(0));
        assertThrows(IllegalArgumentException.class, () -> batch.setMaxPages(1001));
        assertThrows(IllegalArgumentException.class, () -> batch.setMergeSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> batch.setMergeSeconds(61));
    }

    /** 超时使用可控单调时钟，无需阻塞等待一分钟。 */
    @Test
    void expiresMergeDeadlineAndReleasesEveryTemporary() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 0);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            var clock = new AtomicLong();
            artifacts.onMaterialize = () -> clock.set(61_000_000_000L);
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties(), clock::get);
            assertEquals("BATCH_PDF_TIMEOUT", assertThrows(ReportFault.class, () -> pdfs.merge(fixture.runtime.owner, batch)).code());
            assertTrue(artifacts.pending.isEmpty());
            assertEquals(artifacts.materialized, artifacts.released);
        }
    }

    /** 输出前及每MiB复核所有来源，批次下线或任一来源撤权立即终止。 */
    @ParameterizedTest
    @ValueSource(strings = {"before", "scope", "expiry", "offline"})
    void rechecksBeforeAndDuringDelivery(String change) throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 1, 1_300_000);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            try (var merged = pdfs.merge(fixture.runtime.owner, batch)) {
                if (change.equals("before")) fixture.runtime.jdbc.update("UPDATE rpt_batch SET status='OFFLINE'");
                var count = new AtomicLong();
                var target = new OutputStream() {
                    /** 记录已发字节并在首次MiB后注入状态变化。 */
                    public void write(int value) { throw new AssertionError("应使用有界块流"); }
                    /** 触发撤权或到期，下一块前必须完成全来源复核。 */
                    public void write(byte[] bytes, int offset, int length) {
                        if (count.addAndGet(length) >= 1_048_576) {
                            if (change.equals("scope")) fixture.runtime.narrow(7);
                            if (change.equals("expiry")) fixture.runtime.jdbc.update("UPDATE rpt_export_job SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
                            if (change.equals("offline")) fixture.runtime.jdbc.update("UPDATE rpt_batch SET status='OFFLINE'");
                        }
                    }
                };
                assertThrows(ReportFault.class, () -> merged.write(target));
                assertTrue(count.get() <= 1_048_576);
                if (change.equals("before")) assertEquals(0, count.get());
            }
            assertTrue(artifacts.pending.isEmpty());
        }
    }

    /** 客户端断连时释放合并文件和所有共享物化，不删除原任务文件。 */
    @Test
    void disconnectReleasesTemporaryAndKeepsOriginal() throws Exception {
        try (var fixture = new ReportBatchFixture(directory)) {
            String batch = successful(fixture, 2, 0);
            var artifacts = new ReportBatchArtifacts(fixture.runtime.artifacts, true);
            var pdfs = new ReportBatchPdfService(fixture.service, artifacts, new ReportProperties());
            try (var merged = pdfs.merge(fixture.runtime.owner, batch)) {
                assertThrows(IOException.class, () -> merged.write(new OutputStream() {
                    /** 模拟网络已断开。 */
                    public void write(int value) throws IOException { throw new IOException("断连"); }
                }));
            }
            assertTrue(artifacts.pending.isEmpty());
            assertEquals(artifacts.materialized, artifacts.released);
            for (var item : fixture.service.items(fixture.runtime.owner, batch)) {
                assertTrue(fixture.runtime.artifacts.exists("A", fixture.runtime.runtime.job(fixture.runtime.owner, item.jobId()).file()));
            }
        }
    }

    /**
     * 建立真实固定运行及合成PDF，只直接写测试内存库。
     *
     * @param fixture 隔离批次夹具
     * @param count 单据数
     * @param padding 每份PDF的未压缩注释长度
     * @return 固定批次标识
     */
    static String successful(ReportBatchFixture fixture, int count, int padding) throws Exception {
        var documents = new ArrayList<ReportBatchService.Document>();
        for (int index = 0; index < count; index++) documents.add(new ReportBatchService.Document(fixture.version, Map.of("customerId", Integer.toString(index + 1))));
        var batch = fixture.service.create(fixture.runtime.owner, "PDF_BATCH", "合并测试", documents);
        fixture.service.start(fixture.runtime.owner, batch.id());
        var items = fixture.service.items(fixture.runtime.owner, batch.id());
        for (var item : items) {
            Path path = fixture.runtime.artifacts.temporary("A", "pdf");
            try (var document = new PDDocument(); var output = fixture.runtime.artifacts.output("A", path)) {
                int pages = item.number() == 0 ? 2 : 1;
                for (int index = 0; index < pages; index++) {
                    var page = new PDPage(item.number() == 0 ? PDRectangle.A4 : PDRectangle.A5);
                    if (item.number() != 0) page.setRotation(90);
                    document.addPage(page);
                    try (var content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(10, 100);
                        content.showText("DOC" + (item.number() + 1) + " " + (index + 1) + "/" + pages);
                        content.endText();
                    }
                    if (padding > 0 && index == 0) {
                        var extra = new PDStream(document);
                        try (var stream = extra.createOutputStream()) {
                            stream.write('%');
                            byte[] bytes = new byte[padding];
                            Arrays.fill(bytes, (byte) 'A');
                            stream.write(bytes);
                            stream.write('\n');
                        }
                        page.setContents(List.of(page.getContentStreams().next(), extra));
                    }
                }
                document.save(output);
            }
            long length = Files.size(path);
            String reference = fixture.runtime.artifacts.commit("A", path, "pdf");
            fixture.runtime.jdbc.update("UPDATE rpt_export_job SET state='SUCCEEDED',file_ref=?,file_size=? WHERE job_id=?",
                    reference, length, Long.parseLong(item.jobId()));
        }
        return batch.id();
    }
}
