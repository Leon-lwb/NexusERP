package com.nexus.report.mapper;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.Dataset;
import com.nexus.report.domain.ReportRecords.ExportJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.nexus.report.mapper.ReportStoreFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportStoreConcurrencyTest {
    private final ReportStoreFixture f = new ReportStoreFixture();

    @AfterEach void close() { f.close(); }

    @Test void sameUserCannotExceedQuotaByExportingDifferentRunsOnDifferentStoreInstances() throws Exception {
        var actor = actor("A", 7);
        var one = f.completedRun(actor);
        var two = f.completedRun(actor);
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_export_job"),
            sql -> mutex(sql) || sql.startsWith("insert into rpt_export_job"),
            () -> f.job(actor, one, "one"),
            () -> otherStore.createJob(actor, two.id(), "PDF", "two", "request-hash", Instant.now().plusSeconds(1800)));
        assertEquals(1, outcomes.stream().filter(ExportJob.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(o -> o instanceof ReportFault fault && fault.status() == 429).count(), outcomes::toString);
        assertEquals(1, f.count("rpt_export_job"));
    }

    @Test void differentUsersCannotExceedTenantQuotaAcrossDifferentRuns() throws Exception {
        var existingActor = actor("A", 7);
        f.job(existingActor, f.completedRun(existingActor), "existing");
        var firstActor = actor("A", 8);
        var secondActor = actor("A", 9);
        var firstRun = f.completedRun(firstActor);
        var secondRun = f.completedRun(secondActor);
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_export_job"),
            sql -> mutex(sql) || sql.startsWith("insert into rpt_export_job"),
            () -> f.job(firstActor, firstRun, "one"),
            () -> otherStore.createJob(secondActor, secondRun.id(), "PDF", "two", "request-hash", Instant.now().plusSeconds(1800)));
        assertEquals(1, outcomes.stream().filter(ExportJob.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(o -> o instanceof ReportFault fault && fault.status() == 429).count(), outcomes::toString);
        assertEquals(2, f.count("rpt_export_job"));
    }

    @Test void repeatedKeyRacingOnTheSameRunReturnsOneCanonicalJobInsteadOfQuotaOrSqlErrors() throws Exception {
        var actor = actor("A", 7);
        var run = f.completedRun(actor);
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_export_job"),
            sql -> mutex(sql) || sql.contains("from rpt_run") && sql.contains("for update"),
            () -> f.job(actor, run, "repeat"),
            () -> otherStore.createJob(actor, run.id(), "PDF", "repeat", "request-hash", Instant.now().plusSeconds(1800)));
        assertTrue(outcomes.stream().allMatch(ExportJob.class::isInstance), outcomes::toString);
        assertEquals(((ExportJob) outcomes.get(0)).id(), ((ExportJob) outcomes.get(1)).id());
        assertEquals(1, f.count("rpt_export_job"));
    }

    @Test void concurrentInitialDatasetRegistrationReturnsOneParentAndOneVersion() throws Exception {
        var actor = actor("A", 7);
        var otherStore = f.newStore();
        var descriptor = descriptor("RACING_DATA", 1);
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_dataset_version"),
            sql -> mutex(sql) || sql.startsWith("insert into rpt_dataset("),
            () -> f.store.registerDataset(actor, descriptor, "{}", "same-checksum"),
            () -> otherStore.registerDataset(actor, descriptor, "{}", "same-checksum"));
        assertTrue(outcomes.stream().allMatch(Dataset.class::isInstance), outcomes::toString);
        assertEquals(((Dataset) outcomes.get(0)).publishedVersion(), ((Dataset) outcomes.get(1)).publishedVersion());
        assertEquals(1, f.count("rpt_dataset"));
        assertEquals(1, f.count("rpt_dataset_version"));
    }

    @Test void waitingDatasetRegistrationRereadsThePublishedPointerAfterTheLock() throws Exception {
        var actor = actor("A", 7);
        f.dataset(actor);
        var otherStore = f.newStore();
        var descriptor = descriptor("TEST_DATA", 2);
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_dataset_version"),
            sql -> mutex(sql) || sql.contains("from rpt_dataset") && sql.contains("for update"),
            () -> f.store.registerDataset(actor, descriptor, "{\"v\":2}", "data-hash-2"),
            () -> otherStore.registerDataset(actor, descriptor, "{\"v\":2}", "data-hash-2"));
        assertTrue(outcomes.stream().allMatch(Dataset.class::isInstance), outcomes::toString);
        assertEquals(((Dataset) outcomes.get(0)).publishedVersion(), ((Dataset) outcomes.get(1)).publishedVersion());
        assertEquals(2, f.count("rpt_dataset_version"));
    }

    @Test void competingWorkersClaimAJobOnlyOnce() throws Exception {
        var actor = actor("A", 7);
        var job = f.job(actor, f.completedRun(actor), "claim-race");
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("update rpt_export_job"),
            sql -> sql.startsWith("update rpt_export_job"),
            () -> f.store.claim(job, "first", Instant.now().plusSeconds(60)),
            () -> otherStore.claim(job, "second", Instant.now().plusSeconds(60)));
        assertEquals(1, outcomes.stream().filter(Boolean.TRUE::equals).count());
        assertEquals(1, f.reload(job).attempts());
        assertEquals("RUNNING", f.reload(job).state());
    }


    @Test void simultaneousPublishersCannotCreateTwoVersionsFromOneRevision() throws Exception {
        var actor = actor("A", 7);
        var definition = f.definition(actor);
        var dataset = f.dataset(actor);
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("insert into rpt_version"),
            sql -> sql.contains("from rpt_definition") && sql.contains("for update"),
            () -> f.store.publish(actor, definition.id(), definition.revision(), "{}", "same-hash", dataset.publishedVersion()),
            () -> otherStore.publish(actor, definition.id(), definition.revision(), "{}", "same-hash", dataset.publishedVersion()));
        assertEquals(1, outcomes.stream().filter(com.nexus.report.domain.ReportRecords.Version.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(o -> o instanceof ReportFault fault && fault.status() == 409).count(), outcomes::toString);
        assertEquals(1, f.count("rpt_version"));
    }

    @Test void racingSuccessAndCancellationProduceOneConsistentTerminalOutcome() throws Exception {
        var actor = actor("A", 7);
        var queued = f.job(actor, f.completedRun(actor), "finish-cancel-race");
        var claimed = f.store.claimJob(queued, "worker", Instant.now().plusSeconds(60)).orElseThrow();
        var otherStore = f.newStore();
        var outcomes = interleave(sql -> sql.startsWith("update rpt_export_job") && sql.contains("file_ref=?"),
            sql -> sql.startsWith("update rpt_export_job") && sql.contains("cancel_requested=1"),
            () -> f.store.finishJob(claimed, "worker", "SUCCEEDED", "file", 10L, null),
            () -> { otherStore.cancel(actor, queued.id()); return Boolean.TRUE; });
        var current = f.reload(queued);
        if (Boolean.TRUE.equals(outcomes.get(0))) {
            assertEquals("SUCCEEDED", current.state());
            assertFalse(current.cancelled());
            assertEquals("file", current.file());
        } else {
            assertEquals(Boolean.FALSE, outcomes.get(0), outcomes::toString);
            assertEquals("CANCEL_REQUESTED", current.state());
            assertTrue(current.cancelled());
            assertNull(current.file());
            assertTrue(f.store.finishJob(current, "worker", "CANCELLED", null, null, null));
            assertEquals("CANCELLED", f.reload(queued).state());
        }
    }

    /** Pause the first real SQL write; let the second either reach its mutex or pass the old unsafe checks. */
    private List<Object> interleave(Predicate<String> pauseFirst, Predicate<String> observeSecond,
                                    Supplier<?> first, Supplier<?> second) throws Exception {
        var firstPaused = new CountDownLatch(1);
        var secondReached = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var gateUsed = new AtomicBoolean();
        var threadIndex = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2, task -> new Thread(task, "dao-" + threadIndex.incrementAndGet()));
        f.beforeSql = statement -> {
            String sql = statement.toLowerCase(Locale.ROOT);
            if (Thread.currentThread().getName().equals("dao-1") && pauseFirst.test(sql) && gateUsed.compareAndSet(false, true)) {
                firstPaused.countDown();
                await(releaseFirst);
            }
            if (Thread.currentThread().getName().equals("dao-2") && observeSecond.test(sql)) secondReached.countDown();
        };
        try {
            Future<Object> firstResult = pool.submit(() -> outcome(first));
            assertTrue(firstPaused.await(5, TimeUnit.SECONDS), "First transaction did not reach its SQL gate");
            Future<Object> secondResult = pool.submit(() -> outcome(second));
            assertTrue(secondReached.await(5, TimeUnit.SECONDS), "Second transaction did not reach the contested operation");
            releaseFirst.countDown();
            return List.of(firstResult.get(8, TimeUnit.SECONDS), secondResult.get(8, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(8, TimeUnit.SECONDS));
            f.beforeSql = sql -> { };
        }
    }

    private static boolean mutex(String sql) {
        return sql.contains("rpt_catalog") && (sql.contains("for update") || sql.startsWith("insert into"));
    }

    private static Object outcome(Supplier<?> action) {
        try { return action.get(); }
        catch (RuntimeException failure) { return failure; }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new AssertionError("SQL gate timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
