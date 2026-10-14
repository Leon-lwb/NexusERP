package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.extension.ReportExtensionSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** 大批次不能淹没其他用户，两个节点领取仍遵守主库并发限制。 */
class ReportDispatchFairnessTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行oneUserBacklogDoesNotStarvePeerAndRunningUserCannotClaimSecondTask对应的受控处理。 */
    @Test
    void oneUserBacklogDoesNotStarvePeerAndRunningUserCannotClaimSecondTask() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            f.store.admissionLimits(50, 100, 1000);
            f.store.fairDispatch(true, 8, 2, 1);
            var peer = RuntimeReviewFixture.actor(9, Set.of("*:*:*"), ReportScope.all(9));
            f.actors.put(9L, peer);
            f.store.replaceAcl(
                    f.owner,
                    "REPORT",
                    f.definition.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            f.store.replaceAcl(
                    f.root,
                    "DATASET",
                    f.dataset.id(),
                    List.of(
                            new Grant("USER", "7", "VIEW"),
                            new Grant("USER", "7", "EXPORT"),
                            new Grant("USER", "9", "VIEW"),
                            new Grant("USER", "9", "EXPORT")));
            var version =
                    f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion();
            for (int i = 0; i < 12; i++)
                f.runtime.enqueueFixed(f.owner, version, "XLSX_DATA", Map.of(), "batch_owner_" + i);
            var other = f.runtime.enqueueFixed(peer, version, "XLSX_DATA", Map.of(), "manual_peer");
            var candidates = f.store.claimableJobs();
            assertTrue(candidates.stream().anyMatch(job -> job.id().equals(other.id())));
            assertEquals(2, candidates.size());
            var ownerJob =
                    candidates.stream().filter(job -> job.actor() == 7).findFirst().orElseThrow();
            assertTrue(
                    f.store
                            .claimJob(ownerJob, "node-a", Instant.now().plusSeconds(120))
                            .isPresent());
            var next =
                    f.store.jobs(f.owner).stream()
                            .filter(job -> !job.id().equals(ownerJob.id()))
                            .findFirst()
                            .orElseThrow();
            assertTrue(f.store.claimJob(next, "node-b", Instant.now().plusSeconds(120)).isEmpty());
            assertTrue(
                    f.store.claimJob(other, "node-b", Instant.now().plusSeconds(120)).isPresent());
        }
    }
}
