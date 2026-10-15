package com.nexus.report.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** 硬配额在两个服务实例和不同租户竞争时不能超发，删除失败保留占用。 */
class ReportQuotaTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行globalReservationAcrossTenantsIsAtomicAndDeletionMustBeConfirmed对应的受控处理。 */
    @Test
    void globalReservationAcrossTenantsIsAtomicAndDeletionMustBeConfirmed() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var a = new ReportQuotaService(f.jdbc, 100, 100, 20, 20);
            var b = new ReportQuotaService(f.jdbc, 100, 100, 20, 20);
            var pool = Executors.newFixedThreadPool(8);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 16; i++) {
                int index = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        (index % 2 == 0 ? a : b)
                                                .reserve(
                                                        "T" + index,
                                                        "shared",
                                                        "ref" + index,
                                                        60,
                                                        "STAGING",
                                                        "node",
                                                        null);
                                        return true;
                                    } catch (ReportFault full) {
                                        return false;
                                    }
                                }));
            }
            start.countDown();
            int accepted = 0;
            for (var future : futures) if (future.get()) accepted++;
            pool.shutdownNow();
            assertEquals(1, accepted);
            assertEquals(60, a.usage(null).bytes());
            var manifest = a.candidates("shared", 100).get(0);
            a.markDelete(manifest.tenant(), "shared", manifest.reference());
            assertEquals(60, a.usage(null).bytes());
            a.confirmDelete(manifest.tenant(), "shared", manifest.reference());
            assertEquals(0, a.usage(null).bytes());
            a.confirmDelete(manifest.tenant(), "shared", manifest.reference());
            assertEquals(0, a.usage(null).objects());
        }
    }

    /** 执行growingStagingBytesAndObjectCountAreHardBounded对应的受控处理。 */
    @Test
    void growingStagingBytesAndObjectCountAreHardBounded() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var quota = new ReportQuotaService(f.jdbc, 100, 80, 2, 2);
            quota.reserve("A", "shared", "r1", 40, "STAGING", "node", null);
            quota.grow("A", "shared", "r1", "node", 20);
            assertEquals(60, quota.usage("A").bytes());
            assertThrows(ReportFault.class, () -> quota.grow("A", "shared", "r1", "node", 21));
            assertEquals(60, quota.usage("A").bytes());
            quota.reserve("B", "shared", "r2", 20, "STAGING", "node", null);
            assertThrows(
                    ReportFault.class,
                    () -> quota.reserve("C", "shared", "r3", 0, "STAGING", "node", null));
            assertEquals(2, quota.usage(null).objects());
        }
    }
}
