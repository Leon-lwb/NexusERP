package com.nexus.report.batch;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.ReportScope;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** 创建幂等、并发竞争及批次前端兼容字段的真实服务回归。 */
class ReportBatchCompatibilityTest {
    /** 当前测试拥有的私有目录。 */
    @TempDir Path directory;

    /** 相同主体与规范化单据、标题重复创建返回同一批次。 */
    @Test
    void repeatedNormalizedCreationReturnsExistingBatch() {
        try (var fixture = new ReportBatchFixture(directory)) {
            var first = fixture.service.create(fixture.runtime.owner, "IDEMPOTENT", " 固定批次 ",
                    fixture.documents());
            var second = fixture.service.create(fixture.runtime.owner, "IDEMPOTENT", "固定批次",
                    List.of(new ReportBatchService.Document(fixture.version, Map.of("customerId", 7L))));
            assertEquals(first.id(), second.id());
            assertEquals("固定批次", second.title());
            assertEquals(1, fixture.runtime.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_batch", Integer.class));
            assertEquals(1, fixture.runtime.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_batch_item", Integer.class));
            assertEquals(1, fixture.runtime.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_batch_version", Integer.class));
        }
    }

    /** 编码不能被不同内容或其他创建者复用。 */
    @Test
    void conflictingContentOrCreatorHasControlledConflict() {
        try (var fixture = new ReportBatchFixture(directory)) {
            fixture.service.create(fixture.runtime.owner, "CONFLICT", "原批次", fixture.documents());
            assertEquals("BATCH_CODE_CONFLICT", assertThrows(ReportFault.class,
                    () -> fixture.service.create(fixture.runtime.owner, "CONFLICT", "其他标题",
                            fixture.documents())).code());
            var other = RuntimeReviewFixture.actor(8, Set.of("*:*:*"), ReportScope.all(8));
            fixture.runtime.actors.put(8L, other);
            assertEquals("BATCH_CODE_CONFLICT", assertThrows(ReportFault.class,
                    () -> fixture.service.create(other, "CONFLICT", "原批次", fixture.documents())).code());
        }
    }

    /** 两个独立请求竞争唯一编码时只产生一组批次记录。 */
    @Test
    void concurrentCodeCreationConvergesAcrossServiceInstances() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var fixture = new ReportBatchFixture(directory)) {
            var other = new ReportBatchService(fixture.resources, fixture.runtime.jdbc, fixture.runtime.store,
                    fixture.runtime.definitions, fixture.runtime.runtime, fixture.runtime.compiler);
            var start = new CountDownLatch(1);
            Callable<ReportResourceStore.Draft> first = () -> {
                start.await();
                return fixture.service.create(fixture.runtime.owner, "RACING", "并发批次", fixture.documents());
            };
            Callable<ReportResourceStore.Draft> second = () -> {
                start.await();
                return other.create(fixture.runtime.owner, "RACING", "并发批次", fixture.documents());
            };
            var left = executor.submit(first);
            var right = executor.submit(second);
            start.countDown();
            assertEquals(left.get(20, TimeUnit.SECONDS).id(), right.get(20, TimeUnit.SECONDS).id());
            assertEquals(1, fixture.runtime.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_batch", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 新字段以追加方式提供，失败任务沿用运行服务的可重试判定。 */
    @Test
    void itemAddsRetryAndExpiryWithoutExposingBlockedJob() {
        try (var fixture = new ReportBatchFixture(directory)) {
            var batch = fixture.service.create(fixture.runtime.owner, "ITEM_FIELDS", "状态", fixture.documents());
            fixture.service.start(fixture.runtime.owner, batch.id());
            var item = fixture.service.items(fixture.runtime.owner, batch.id()).get(0);
            fixture.runtime.jdbc.update("UPDATE rpt_export_job SET state='FAILED',error_code='PDF_EXPORT_FAILED' WHERE job_id=?",
                    Long.parseLong(item.jobId()));
            var fields = fixture.runtime.json.findAndRegisterModules().valueToTree(
                    fixture.service.items(fixture.runtime.owner, batch.id()).get(0));
            assertTrue(fields.path("canRetry").asBoolean());
            assertEquals(0, fields.path("retryRound").asInt(-1));
            assertFalse(fields.path("expires").isMissingNode());
            fixture.runtime.narrow(7);
            var blocked = fixture.service.items(fixture.runtime.owner, batch.id()).get(0);
            assertEquals("BLOCKED", blocked.state());
            assertNull(blocked.jobId());
            assertFalse(fixture.runtime.json.valueToTree(blocked).path("canRetry").asBoolean());
        }
    }

    /** 业务模板保留原字段并公开固定发布标识和报表类型。 */
    @Test
    void businessTemplatesAppendKindAndPublishedVersion() {
        try (var fixture = new ReportBatchFixture(directory)) {
            var templates = fixture.runtime.definitions.businessTemplates(fixture.runtime.owner, "SECURE_TEST");
            var template = fixture.runtime.json.valueToTree(templates.stream()
                    .filter(value -> value.id().equals(fixture.runtime.store.version("A", fixture.version).orElseThrow().reportId()))
                    .findFirst().orElseThrow());
            assertEquals("DOCUMENT", template.path("kind").asText());
            assertEquals(fixture.version, template.path("versionId").asText());
            assertTrue(template.hasNonNull("id"));
            assertTrue(template.hasNonNull("name"));
        }
    }
}
