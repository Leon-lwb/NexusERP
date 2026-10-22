package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class AiExecutionRepositoryTest {
    AiExecutionRepository repo;
    MutableClock clock;
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        for (String s :
                Files.readString(Path.of("../../script/sql/update/2026-10-22-ai-stage-two.sql"))
                        .replaceAll("(?m)^--.*$", "")
                        .split(";")) if (!s.isBlank()) jdbc.execute(s);
        clock = new MutableClock();
        repo = new AiExecutionRepository(ds, clock);
    }

    @Test
    void deadlineCannotCommitSuccessEvenWhileLeaseIsValid() {
        String id = repo.create("000001", 7L, "CHAT", "", 9L, "{}", 1);
        var lease = repo.claim("000001", id, "one");
        clock.advance(2);
        var writes = new AtomicInteger();
        assertThat(repo.finish(lease, "SUCCEEDED", null)).isFalse();
        assertThat(repo.completeAtomically(lease, "SUCCEEDED", writes::incrementAndGet, () -> {}))
                .isEqualTo("FAILED");
        assertThat(writes).hasValue(0);
    }

    @Test
    void cancellationCannotBeReversedAndIsTenantAndOwnerScoped() {
        String id = repo.create("000001", 7L, "CHAT", "", 9L, "{}", 600);
        assertThatThrownBy(() -> repo.owned("000002", 7L, id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repo.owned("000001", 8L, id))
                .isInstanceOf(IllegalArgumentException.class);
        var lease = repo.claim("000001", id, "node-a");
        assertThat(lease).isNotNull();
        repo.cancel("000001", 7L, id);
        repo.cancel("000001", 7L, id);
        assertThatThrownBy(() -> repo.check(lease)).isInstanceOf(CancellationException.class);
        assertThat(repo.finish(lease, "SUCCEEDED", null)).isFalse();
        assertThat(repo.finish(lease, "CANCELLED", null)).isTrue();
        assertThat(repo.finish(lease, "FAILED", null)).isFalse();
        assertThat(repo.owned("000001", 7L, id).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void expiredOwnerCannotCommitAfterTakeoverAndWaitReleasesLease() {
        String id = repo.create("000001", 7L, "FLOW", "17", null, "{}", 600);
        var first = repo.claim("000001", id, "node-a");
        clock.advance(31);
        var second = repo.claim("000001", id, "node-b");
        assertThat(second.generation()).isGreaterThan(first.generation());
        assertThat(repo.finish(first, "SUCCEEDED", null)).isFalse();
        assertThat(repo.park(second, "{\"node\":\"next\"}", clock.instant().plusSeconds(100)))
                .isTrue();
        assertThat(repo.claim("000001", id, "node-c")).isNull();
        clock.advance(101);
        var third = repo.claim("000001", id, "node-c");
        assertThat(third).isNotNull();
        assertThat(repo.checkpoint(third)).contains("next");
    }

    @Test
    void onlyOneConcurrentClaimWins() throws Exception {
        String id = repo.create("000001", 7L, "AB", "run", null, "{}", 600);
        var pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 12; i++) {
                final int k = i;
                futures.add(pool.submit(() -> repo.claim("000001", id, "node-" + k) != null));
            }
            int accepted = 0;
            for (var f : futures) if (f.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    static class MutableClock extends Clock {
        Instant value = Instant.parse("2026-10-22T12:00:00Z");

        void advance(long seconds) {
            value = value.plusSeconds(seconds);
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return value;
        }
    }
}
