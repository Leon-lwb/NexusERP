package com.nexus.report.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/** 清单产物容量、尝试围栏及删除失败的实际文件回归。 */
class ReportManifestStoreTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行stagingGrowthIsReservedBeforeWriteAndCommittedBytesAreReleasedOnlyAfterDelete对应的受控处理。 */
    @Test
    void stagingGrowthIsReservedBeforeWriteAndCommittedBytesAreReleasedOnlyAfterDelete()
            throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("fixture"));
                var local =
                        new LocalReportArtifactStore(directory.resolve("objects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 100, 50);
            var faulting = new FaultStore(local);
            var store = new QuotaReportArtifactStore(faulting, quota, 1024 * 1024, "node-a");
            Path tooBig = store.temporary("A", "rows");
            try (var output = store.output("A", tooBig)) {
                assertThrows(ReportFault.class, () -> output.write(new byte[1024 * 1024 + 1]));
            }
            store.discard("A", tooBig);
            assertEquals(0, quota.usage(null).bytes());
            Path file = store.temporary("A", "rows");
            try (var output = store.output("A", file)) {
                output.write("private".getBytes());
            }
            String reference = store.commit("A", file, "rows");
            assertEquals(7, quota.usage(null).bytes());
            assertEquals(1, quota.usage(null).objects());
            faulting.failDelete = true;
            assertThrows(ReportFault.class, () -> store.delete("A", reference));
            assertEquals(7, quota.usage(null).bytes());
            assertEquals(
                    "DELETE_PENDING",
                    quota.manifest("A", store.storeId(), reference).orElseThrow().state());
            store.delete("A", reference);
            assertEquals(0, quota.usage(null).bytes());
            assertFalse(local.exists("A", reference));
        }
    }

    /** 旧命名空间启用清单后，既有有效文件必须计入并继续读取。 */
    @Test
    void adoptingExistingLocalObjectsKeepsReadAndCleanupWorking() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("fixture"));
                var local =
                        new LocalReportArtifactStore(directory.resolve("legacy"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            Path temporary = local.temporary("A", "rows");
            try (var output = local.output("A", temporary)) {
                output.write("legacy".getBytes());
            }
            String reference = local.commit("A", temporary, "rows");
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 100, 50);
            var wrapped = new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-a");
            assertEquals(6, quota.usage(null).bytes());
            assertEquals("legacy", Files.readString(wrapped.read("A", reference)));
            wrapped.delete("A", reference);
            assertEquals(0, quota.usage(null).bytes());
        }
    }

    /** 对象数量满时不能先创建物理文件再尝试补偿删除。 */
    @Test
    void fullObjectQuotaDoesNotCreateAnUntrackedFileWhenDiscardFails() {
        try (var f = new RuntimeReviewFixture(directory.resolve("fixture"));
                var local =
                        new LocalReportArtifactStore(directory.resolve("objects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 1, 1);
            quota.reserve("A", local.storeId(), "occupied", 0, "STAGING", "node", null);
            var faulting = new FaultStore(local);
            var wrapped = new QuotaReportArtifactStore(faulting, quota, 1024 * 1024, "node-a");
            faulting.failDiscard = true;
            assertThrows(ReportFault.class, () -> wrapped.temporary("A", "rows"));
            try (var files = Files.walk(directory.resolve("objects"))) {
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 另一节点即使能解释相同绝对路径，也不能释放源节点暂存配额。 */
    @Test
    void otherNodeCannotConfirmSourceStagingDeletion() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("fixture"));
                var local =
                        new LocalReportArtifactStore(directory.resolve("objects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 100, 50);
            var a = new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-a");
            Path path = a.temporary("A", "rows");
            try (var output = a.output("A", path)) {
                output.write("private".getBytes());
            }
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET update_time=?,lease_until=?",
                    Timestamp.from(Instant.now().minusSeconds(7200)),
                    Timestamp.from(Instant.now().minusSeconds(3600)));
            var b = new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-b");
            Files.setLastModifiedTime(path, FileTime.from(Instant.now().minusSeconds(7200)));
            b.reap(Instant.now().minusSeconds(3600), (tenant, reference) -> false);
            assertTrue(Files.exists(path));
            assertEquals(7, quota.usage(null).bytes());
            a.discard("A", path);
        }
    }

    /** 注入一次删除失败，其他操作均使用实际私有文件实现。 */

    /** 底层暂存创建失败且未创建文件时，数量预留应立即补偿。 */
    @Test
    void failedTemporaryAllocationDoesNotConsumeObjectQuota() {
        try (var f = new RuntimeReviewFixture(directory.resolve("creation"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("creationObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1, 1);
            var fault = new FaultStore(local);
            var wrapped = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            fault.failTemporary = true;
            assertThrows(ReportFault.class, () -> wrapped.temporary("A", "rows"));
            assertEquals(0, quota.usage(null).objects());
            Path path = wrapped.temporary("A", "rows");
            wrapped.discard("A", path);
        }
    }

    /** 创建后权限设置失败且删除也失败时，物理文件仍须占据清单数量。 */
    @Test
    void partialTemporaryCreationFailureKeepsQuotaUntilFileIsRemoved() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("partial"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("partialObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1, 1);
            var fault = new FaultStore(local);
            var wrapped = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            fault.failAfterTemporary = true;
            fault.failDiscard = true;
            assertThrows(ReportFault.class, () -> wrapped.temporary("A", "rows"));
            assertEquals(1, quota.usage(null).objects());
            assertThrows(ReportFault.class, () -> wrapped.temporary("A", "rows"));
            fault.failDiscard = false;
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET lease_until=?,update_time=?",
                    Timestamp.from(Instant.now().minusSeconds(3600)),
                    Timestamp.from(Instant.now().minusSeconds(7200)));
            wrapped.reap(Instant.now().minusSeconds(300), (tenant, ref) -> false);
            assertEquals(0, quota.usage(null).objects());
        }
    }

    /** 预留后、物理创建前中断，清单已经知道确定路径，原节点重启可以确认空文件删除。 */
    @Test
    void crashBeforePhysicalAllocationHasRecoverableReservedPath() {
        try (var f = new RuntimeReviewFixture(directory.resolve("preCreate"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("preCreateObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1, 1);
            var fault = new FaultStore(local);
            var wrapped = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            fault.crashBeforeTemporary = true;
            assertThrows(SimulatedCrash.class, () -> wrapped.temporary("A", "rows"));
            assertNotNull(quota.candidates(local.storeId(), 10).get(0).path());
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET lease_until=?,update_time=?",
                    Timestamp.from(Instant.now().minusSeconds(3600)),
                    Timestamp.from(Instant.now().minusSeconds(7200)));
            var restarted = new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-a");
            restarted.reap(Instant.now().minusSeconds(300), (tenant, ref) -> false);
            assertEquals(0, quota.usage(null).objects());
            var path = restarted.temporary("A", "rows");
            restarted.discard("A", path);
        }
    }

    /** inventory取得元数据后对象已封存时，新节点不能按旧内容重建清单和容量。 */
    @Test
    void staleInventoryCannotReadoptRetiredObject() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("staleInventory"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("staleInventoryObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var path = local.temporary("A", "rows");
            try (var output = local.output("A", path)) {
                output.write("old".getBytes());
            }
            String reference = local.commit("A", path, "rows");
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 100, 50);
            var fault = new FaultStore(local);
            fault.beforeInventoryReturn = () -> local.delete("A", reference);
            var wrapped = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-b");
            assertEquals(0, quota.usage(null).objects());
            assertFalse(wrapped.exists("A", reference));
        }
    }

    /** 纳管过程中短暂网络失败后，重启可完成既有对象清单，不能将其误当孤立上传删除。 */
    @Test
    void adoptionMetadataFailureCanBeCompletedOnRestart() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("adoptRetry"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("adoptRetryObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var path = local.temporary("A", "rows");
            try (var output = local.output("A", path)) {
                output.write("retry".getBytes());
            }
            String reference = local.commit("A", path, "rows");
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 100, 50);
            var fault = new FaultStore(local);
            fault.beforeInfoRead =
                    () -> {
                        if (++fault.infoReads == 2)
                            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成HEAD短暂失败", 503);
                    };
            assertThrows(
                    ReportFault.class,
                    () -> new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a"));
            var restarted = new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-a");
            assertEquals("retry", Files.readString(restarted.read("A", reference)));
            assertEquals(1, quota.usage(null).objects());
            assertEquals(5, quota.usage(null).bytes());
        }
    }

    /** 纳管预留容量之后再发生封存，也应撤销新增预留而不复活陈旧对象。 */
    @Test
    void concurrentSealAfterAdoptionReservationReleasesNewReservation() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("adoptSeal"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("adoptSealObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var path = local.temporary("A", "rows");
            try (var output = local.output("A", path)) {
                output.write("old".getBytes());
            }
            String reference = local.commit("A", path, "rows");
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 100, 50);
            var fault = new FaultStore(local);
            fault.beforeInfoRead =
                    () -> {
                        if (++fault.infoReads == 2) local.delete("A", reference);
                    };
            var wrapped = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            assertEquals(0, quota.usage(null).objects());
            assertFalse(wrapped.exists("A", reference));
        }
    }

    /** 模拟进程被终止，绕过正常RuntimeException补偿路径。 */
    private static final class SimulatedCrash extends Error {}

    /** 对象删除必须永久封住旧引用，迟到提交不能越过已释放的容量。 */
    @Test
    void deletedReferenceRejectsLatePhysicalCommit() throws Exception {
        try (var local =
                new LocalReportArtifactStore(directory.resolve("lateObject"), 1024 * 1024)) {
            String reference = UUID.randomUUID() + ".rows";
            var temporary = local.temporary("A", "rows");
            try (var output = local.output("A", temporary)) {
                output.write("late".getBytes());
            }
            local.delete("A", reference);
            assertThrows(
                    ReportFault.class, () -> local.commitAt("A", temporary, "rows", reference));
            assertFalse(local.exists("A", reference));
            local.discard("A", temporary);
        }
    }

    /** 过期回收完成后，暂停的旧提交不能重新创建对象；慢I/O也不占全局配额锁。 */
    @Test
    void physicalFenceRejectsPausedCommitAfterQuotaRecovery() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("guard"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("guardObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 100, 50);
            String reference = UUID.randomUUID() + ".rows";
            Path temporary = local.temporary("A", "rows");
            try (var output = local.output("A", temporary)) {
                output.write("guarded".getBytes());
            }
            quota.reserve("A", local.storeId(), reference, 7, "COMMITTING", "node-a", null);
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET update_time=?",
                    Timestamp.from(Instant.now().minusSeconds(7200)));
            var entered = new CountDownLatch(1);
            var resume = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var commit =
                        executor.submit(
                                () ->
                                        quota.commitGuard(
                                                "A",
                                                local.storeId(),
                                                reference,
                                                "node-a",
                                                () -> {
                                                    entered.countDown();
                                                    try {
                                                        if (!resume.await(5, TimeUnit.SECONDS))
                                                            throw new IllegalStateException(
                                                                    "测试暂停超时");
                                                    } catch (InterruptedException failure) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(failure);
                                                    }
                                                    local.commitAt(
                                                            "A", temporary, "rows", reference);
                                                    quota.stored(
                                                            "A",
                                                            local.storeId(),
                                                            reference,
                                                            "node-a",
                                                            7,
                                                            local.checksum("A", reference));
                                                    return reference;
                                                }));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var peer =
                        executor.submit(
                                () ->
                                        quota.reserve(
                                                "B",
                                                local.storeId(),
                                                "peer",
                                                1,
                                                "STAGING",
                                                "node-b",
                                                null));
                peer.get(1, TimeUnit.SECONDS);
                f.jdbc.update(
                        "UPDATE rpt_artifact_manifest SET lease_until=? WHERE tenant_id='A'",
                        Timestamp.from(Instant.now().minusSeconds(3600)));
                quota.interruptedGuard(
                        "A",
                        local.storeId(),
                        reference,
                        Instant.now().minusSeconds(300),
                        () -> local.delete("A", reference));
                assertTrue(quota.manifest("A", local.storeId(), reference).isEmpty());
                assertEquals(1, quota.usage(null).bytes());
                resume.countDown();
                var failure =
                        assertThrows(
                                ExecutionException.class, () -> commit.get(5, TimeUnit.SECONDS));
                assertInstanceOf(ReportFault.class, failure.getCause());
                assertFalse(local.exists("A", reference));
                local.discard("A", temporary);
            } finally {
                resume.countDown();
                executor.shutdownNow();
            }
        }
    }

    /** 物理删除开始前删除意图必须已提交，新读者不能在数据库回滚窗口重新pin。 */
    @Test
    void deletingFontHasDurableIntentBeforePhysicalIo() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("durableDelete"))) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 100, 50);
            String reference = UUID.randomUUID() + ".font";
            quota.reserve("A", "store", reference, 1, "COMMITTING", "node-a", null);
            quota.stored("A", "store", reference, "node-a", 1, "a".repeat(64));
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET update_time=?,lease_until=?",
                    Timestamp.from(Instant.now().minusSeconds(7200)),
                    Timestamp.from(Instant.now().minusSeconds(3600)));
            var executor = Executors.newSingleThreadExecutor();
            try {
                assertTrue(
                        quota.reapGuard(
                                "A",
                                "store",
                                reference,
                                Instant.now().minusSeconds(300),
                                () -> false,
                                () -> {
                                    try {
                                        executor.submit(
                                                        () -> {
                                                            assertEquals(
                                                                    "DELETE_PENDING",
                                                                    quota.manifest(
                                                                                    "A", "store",
                                                                                    reference)
                                                                            .orElseThrow()
                                                                            .state());
                                                            assertThrows(
                                                                    ReportFault.class,
                                                                    () ->
                                                                            quota.pin(
                                                                                    "A", "store",
                                                                                    reference,
                                                                                    3600));
                                                        })
                                                .get(1, TimeUnit.SECONDS);
                                    } catch (Exception failure) {
                                        throw new IllegalStateException(failure);
                                    }
                                }));
            } finally {
                executor.shutdownNow();
            }
            assertEquals(0, quota.usage(null).bytes());
        }
    }

    /** 旧候选不能越过新字体读取租约删除共享字体。 */
    @Test
    void fontPinIsRecheckedUnderCleanupLock() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("fontPin"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("fontObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8 * 1024 * 1024, 4 * 1024 * 1024, 100, 50);
            String reference = UUID.randomUUID() + ".font";
            quota.reserve("A", local.storeId(), reference, 1, "COMMITTING", "node-a", null);
            quota.stored("A", local.storeId(), reference, "node-a", 1, "a".repeat(64));
            f.jdbc.update(
                    "UPDATE rpt_artifact_manifest SET update_time=?,lease_until=?",
                    Timestamp.from(Instant.now().minusSeconds(7200)),
                    Timestamp.from(Instant.now().minusSeconds(3600)));
            assertEquals(1, quota.candidates(local.storeId(), 200).size());
            quota.pin("A", local.storeId(), reference, 3600);
            assertFalse(
                    quota.reapGuard(
                            "A",
                            local.storeId(),
                            reference,
                            Instant.now().minusSeconds(300),
                            () -> false,
                            () -> fail("读取租约内不得删除字体")));
            assertEquals(1, quota.usage(null).bytes());
        }
    }

    /** 恢复游标使用不可变标识，失败记录修改时间不会挤占下一批。 */
    @Test
    void recoveryCursorMovesPastFullFailureBatchBeforeWrapping() {
        try (var f = new RuntimeReviewFixture(directory.resolve("cursor"))) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1000, 500);
            for (int i = 0; i < 205; i++)
                quota.reserve(
                        "A",
                        "store",
                        String.format("item_%04d", i),
                        1,
                        "COMMITTING",
                        "node-a",
                        null);
            var first = quota.candidates("store", 200);
            assertEquals(200, first.size());
            first.forEach(item -> quota.markDelete("A", "store", item.reference()));
            var next = quota.candidates("store", 200);
            assertEquals(5, next.size());
            assertEquals("item_0200", next.get(0).reference());
            assertEquals(200, quota.candidates("store", 200).size());
        }
    }

    /** 可注入暂存创建及删除故障的测试存储装饰器。 */
    /** 回收已经计划的暂存路径后，暂停的旧物理分配必须被封存标志拒绝。 */
    @Test
    void retiredStagingPathRejectsLateAllocation() {
        try (var local =
                new LocalReportArtifactStore(directory.resolve("lateStage"), 1024 * 1024)) {
            Path path = local.temporaryPath("A", "rows", "stage_" + UUID.randomUUID());
            local.discard("A", path);
            assertThrows(ReportFault.class, () -> local.allocateTemporary("A", path));
            assertFalse(Files.exists(path));
        }
    }

    /** 可注入部分分配、删除、元数据和重启故障的隔离测试存储。 */
    /** 未登记回收的物理删除前发生纳管时，两者必须争用同一持久删除状态。 */
    @Test
    void untrackedCleanupCannotSealAnObjectBehindCompletedAdoption() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("untrackedRace"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("untrackedRaceObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1, 1);
            var fault = new FaultStore(local);
            var a = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            var path = local.temporary("A", "rows");
            try (var output = local.output("A", path)) {
                output.write("old".getBytes());
            }
            String reference = local.commit("A", path, "rows");
            Files.setLastModifiedTime(
                    local.read("A", reference), FileTime.from(Instant.now().minusSeconds(7200)));
            fault.beforeUntrackedDelete =
                    () -> new QuotaReportArtifactStore(local, quota, 1024 * 1024, "node-b");
            a.reap(Instant.now().minusSeconds(3600), (tenant, ref) -> false);
            assertEquals(0, quota.usage(null).objects());
            assertFalse(local.exists("A", reference));
        }
    }

    /** 未登记对象删除失败仍保留实际容量，下一轮不等原保留年龄即可安全重试。 */
    @Test
    void untrackedDeleteFailureKeepsCapacityUntilNextConfirmedRetry() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("untrackedRetry"));
                var local =
                        new LocalReportArtifactStore(
                                directory.resolve("untrackedRetryObjects"), 1024 * 1024)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 8192, 4096, 1, 1);
            var fault = new FaultStore(local);
            var a = new QuotaReportArtifactStore(fault, quota, 1024 * 1024, "node-a");
            var path = local.temporary("A", "rows");
            try (var output = local.output("A", path)) {
                output.write("old".getBytes());
            }
            String reference = local.commit("A", path, "rows");
            Files.setLastModifiedTime(
                    local.read("A", reference), FileTime.from(Instant.now().minusSeconds(7200)));
            fault.beforeUntrackedDelete =
                    () -> {
                        throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成封存失败", 503);
                    };
            assertThrows(
                    ReportFault.class,
                    () -> a.reap(Instant.now().minusSeconds(3600), (tenant, ref) -> false));
            assertEquals(3, quota.usage(null).bytes());
            fault.beforeUntrackedDelete = () -> {};
            a.reap(Instant.now().minusSeconds(3600), (tenant, ref) -> false);
            assertEquals(0, quota.usage(null).objects());
            assertFalse(local.exists("A", reference));
        }
    }

    /** 可注入暂存分配、清单竞态、元数据及物理删除故障的隔离测试存储。 */
    private static final class FaultStore implements ReportArtifactStore {
        /** 保存当前私有存储委托。 */
        private final ReportArtifactStore delegate;

        /** 保存当前failDelete状态或配置。 */
        boolean failDelete, failDiscard, failTemporary, failAfterTemporary, crashBeforeTemporary;

        /** inventory返回前可触发另节点封存。 */
        Runnable beforeInventoryReturn = () -> {};

        /** 元数据读取前的隔离故障钩子。 */
        Runnable beforeInfoRead = () -> {};

        /** 未登记回收准备删除后的并发钩子。 */
        Runnable beforeUntrackedDelete = () -> {};

        /** 统计合成元数据调用次数。 */
        int infoReads;

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param delegate 当前受控操作的delegate输入
         */
        FaultStore(ReportArtifactStore delegate) {
            this.delegate = delegate;
        }

        /**
         * 执行storeId对应的受控处理。
         *
         * @return 当前固定契约或处理结果
         */
        public String storeId() {
            return delegate.storeId();
        }

        /**
         * 为当前尝试分配受控私有暂存。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param kind 当前受控操作的kind输入
         * @return 当前固定契约或处理结果
         */
        /**
         * 返回确定路径，不在配额保留前创建物理文件。
         *
         * @param tenant 租户隔离标识
         * @param kind 当前契约的kind配置或状态
         * @param token 当前契约的token配置或状态
         * @return 当前受控处理的固定结果
         */
        public Path temporaryPath(String tenant, String kind, String token) {
            return delegate.temporaryPath(tenant, kind, token);
        }

        /**
         * 在返回路径前注入崩溃或部分权限失败。
         *
         * @param tenant 租户隔离标识
         * @param path 本节点私有暂存路径
         */
        public void allocateTemporary(String tenant, Path path) {
            if (crashBeforeTemporary) {
                crashBeforeTemporary = false;
                throw new SimulatedCrash();
            }
            if (failTemporary) {
                failTemporary = false;
                throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成暂存创建失败", 503);
            }
            delegate.allocateTemporary(tenant, path);
            if (failAfterTemporary) {
                failAfterTemporary = false;
                throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成创建后权限失败", 503);
            }
        }

        /**
         * 为传统接口创建私有暂存路径，故障注入与确定路径协议保持一致。
         *
         * @param tenant 租户隔离标识
         * @param kind 当前契约的kind配置或状态
         * @return 当前受控处理的固定结果
         */
        public Path temporary(String tenant, String kind) {
            if (crashBeforeTemporary) {
                crashBeforeTemporary = false;
                throw new SimulatedCrash();
            }
            if (failTemporary) {
                failTemporary = false;
                throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成暂存创建失败", 503);
            }
            Path path = delegate.temporary(tenant, kind);
            if (failAfterTemporary) {
                failAfterTemporary = false;
                throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成创建后权限失败", 503);
            }
            return path;
        }

        /**
         * 取得物理清单后模拟并发删除。
         *
         * @return 当前受控处理的固定结果
         */
        public List<Inventory> inventory() {
            var result = delegate.inventory();
            beforeInventoryReturn.run();
            beforeInventoryReturn = () -> {};
            return result;
        }

        /**
         * 实际读取元数据，不使用测试缓存值。
         *
         * @param tenant 租户隔离标识
         * @param reference 私有对象的一次性引用
         * @return 当前受控处理的固定结果
         */
        public Info info(String tenant, String reference) {
            beforeInfoRead.run();
            return delegate.info(tenant, reference);
        }

        /**
         * 包装器的孤立对象回收不扫描暂存。
         *
         * @param olderThan 当前契约的olderThan配置或状态
         * @param referenced 当前契约的referenced配置或状态
         */
        public void reapUntracked(Instant olderThan, BiPredicate<String, String> referenced) {
            delegate.reapUntracked(
                    olderThan,
                    (tenant, ref) -> {
                        boolean keep = referenced.test(tenant, ref);
                        if (!keep) beforeUntrackedDelete.run();
                        return keep;
                    });
        }

        /**
         * 打开当前尝试的受控暂存输出。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param temporary 当前受控操作的temporary输入
         * @return 当前固定契约或处理结果
         */
        public OutputStream output(String tenant, Path temporary) {
            return delegate.output(tenant, temporary);
        }

        /**
         * 提交当前围栏下的固定私有产物。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param temporary 当前受控操作的temporary输入
         * @param kind 当前受控操作的kind输入
         * @return 当前固定契约或处理结果
         */
        public String commit(String tenant, Path temporary, String kind) {
            return delegate.commit(tenant, temporary, kind);
        }

        /**
         * 向预分配引用提交已校验内容。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param temporary 当前受控操作的temporary输入
         * @param kind 当前受控操作的kind输入
         * @param reference 当前受控操作的reference输入
         * @return 当前固定契约或处理结果
         */
        public String commitAt(String tenant, Path temporary, String kind, String reference) {
            return delegate.commitAt(tenant, temporary, kind, reference);
        }

        /**
         * 读取当前租户已授权的固定内容。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param reference 当前受控操作的reference输入
         * @return 当前固定契约或处理结果
         */
        public Path read(String tenant, String reference) {
            return delegate.read(tenant, reference);
        }

        /**
         * 确认私有对象是否存在且仍符合存储契约。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param reference 当前受控操作的reference输入
         * @return 当前固定契约或处理结果
         */
        public boolean exists(String tenant, String reference) {
            return delegate.exists(tenant, reference);
        }

        /**
         * 清理当前尝试拥有的私有暂存。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param temporary 当前受控操作的temporary输入
         */
        public void discard(String tenant, Path temporary) {
            if (failDiscard) throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成暂存删除失败", 503);
            delegate.discard(tenant, temporary);
        }

        /**
         * 计算并校验当前固定产物的实际摘要。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param reference 当前受控操作的reference输入
         * @return 当前固定契约或处理结果
         */
        public String checksum(String tenant, String reference) {
            return delegate.checksum(tenant, reference);
        }

        /**
         * 确认物理删除并保持失败可恢复。
         *
         * @param tenant 当前受控操作的tenant输入
         * @param reference 当前受控操作的reference输入
         */
        public void delete(String tenant, String reference) {
            if (failDelete) {
                failDelete = false;
                throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "合成删除失败", 503);
            }
            delegate.delete(tenant, reference);
        }
    }
}
