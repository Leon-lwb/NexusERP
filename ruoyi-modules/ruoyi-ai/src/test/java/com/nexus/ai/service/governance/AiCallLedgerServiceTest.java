package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.ai.governance.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

class AiCallLedgerServiceTest {
    JdbcTemplate jdbc;
    AiCallLedgerService ledger;
    final AiCallContext context =
            new AiCallContext("000001", 7L, "fixture", "req", 9L, null, null, null, "CHAT");
    final AiPriceSnapshot price =
            new AiPriceSnapshot(
                    11L,
                    "fixture",
                    "OPENAI_CHAT_COMPLETIONS",
                    "CNY",
                    new BigDecimal("2"),
                    new BigDecimal("4"),
                    1000,
                    100);

    @BeforeEach
    void setup() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        Path script = Path.of("../../script/sql/update/2026-10-22-ai-stage-two.sql");
        for (String statement : Files.readString(script).replaceAll("(?m)^--.*$", "").split(";"))
            if (!statement.isBlank()) jdbc.execute(statement);
        ledger =
                new AiCallLedgerService(
                        ds,
                        Clock.fixed(
                                Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void recentFlowCallRecoveryDoesNotDependOnConnectionTextCollation() {
        var fixed = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        var repo = new AiExecutionRepository(jdbc.getDataSource(), fixed);
        String execution = repo.create("000001", 7L, "FLOW", "99", null, "{}", 600);
        var lease = repo.claim("000001", execution, "one");
        repo.create("000001", 7L, "CHAT", "not-a-number", 9L, "{}", 600);
        String id =
                ledger.begin(
                        new AiCallContext(
                                "000001", 7L, "fixture", "logical", null, 99L, null, null, "FLOW"),
                        price,
                        true);
        jdbc.update(
                "UPDATE ai_model_call SET started_at=? WHERE call_id=?",
                Timestamp.from(fixed.instant().minusSeconds(120)),
                id);
        assertThat(repo.finish(lease, "SUCCEEDED", null)).isTrue();
        assertThat(ledger.recoverUnconfirmed()).isEqualTo(1);
        assertThat(ledger.call("000001", id).get("status")).isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    void abandonedCallRetainsHoldAndLateUsageAppendsOnlyOneAudit() {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("1"), true, 7L);
        String id = ledger.begin(context, price, true);
        jdbc.update(
                "UPDATE ai_model_call SET started_at=? WHERE call_id=?",
                Timestamp.from(Instant.parse("2026-10-20T00:00:00Z")),
                id);
        assertThat(ledger.recoverUnconfirmed()).isEqualTo(1);
        assertThat(ledger.call("000001", id).get("status")).isEqualTo("RECOVERY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT held_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("0.0024");
        var usage = new AiCallUsage(100L, 50L, 20L, 10L, "REPORTED");
        ledger.finish("000001", id, "SUCCEEDED", usage, null);
        ledger.finish("000001", id, "SUCCEEDED", usage, null);
        assertThat(ledger.call("000001", id).get("cost")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ai_model_call_adjustment", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(known_cost) FROM ai_model_call_daily",
                                BigDecimal.class))
                .isEqualByComparingTo("0.0004");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(input_tokens) FROM ai_model_call_daily", Long.class))
                .isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT held_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("0");
    }

    @Test
    void terminalIsIdempotentAndPriceIsFrozen() {
        String id = ledger.begin(context, price, true);
        ledger.finish(
                context.tenantId(),
                id,
                "SUCCEEDED",
                new AiCallUsage(100L, 50L, 20L, 10L, "REPORTED"),
                null);
        ledger.finish(context.tenantId(), id, "FAILED", AiCallUsage.unknown(), "LATE");
        var row = ledger.call(context.tenantId(), id);
        assertThat(row.get("STATUS")).isEqualTo("SUCCEEDED");
        assertThat(new BigDecimal(row.get("COST").toString())).isEqualByComparingTo("0.0004");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(request_count) FROM ai_model_call_daily", Long.class))
                .isEqualTo(1);
        assertThat(ledger.summary("other")).isEmpty();
    }

    @Test
    void missingUsageIsUnknownAndDoesNotReleaseReservation() {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("1"), true, 7L);
        String id = ledger.begin(context, price, true);
        ledger.finish("000001", id, "FAILED", AiCallUsage.unknown(), "TIMEOUT");
        assertThat(ledger.call("000001", id).get("COST")).isNull();
        assertThat(jdbc.queryForObject("SELECT held_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("0.0024");
        ledger.reconcile(
                "000001",
                id,
                new BigDecimal("0.001"),
                "CNY",
                "vendor confirmation",
                7L,
                "correction-1");
        ledger.reconcile(
                "000001",
                id,
                new BigDecimal("0.001"),
                "CNY",
                "vendor confirmation",
                7L,
                "correction-1");
        assertThat(jdbc.queryForObject("SELECT held_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT spent_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("0.001");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ai_model_call_adjustment", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void currenciesRemainSeparate() {
        String a = ledger.begin(context, price, true);
        var usd =
                new AiPriceSnapshot(
                        12L,
                        "usd",
                        "OPENAI_CHAT_COMPLETIONS",
                        "USD",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        1000,
                        100);
        String b = ledger.begin(context, usd, true);
        ledger.finish(
                "000001",
                a,
                "SUCCEEDED",
                new AiCallUsage(100L, 100L, null, null, "REPORTED"),
                null);
        ledger.finish(
                "000001",
                b,
                "SUCCEEDED",
                new AiCallUsage(100L, 100L, null, null, "REPORTED"),
                null);
        assertThat(ledger.summary("000001")).hasSize(2);
    }

    @Test
    void concurrentReservationsCannotOverspendAndUnboundedCallsAreDenied() throws Exception {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("0.01"), true, 7L);
        assertThatThrownBy(() -> ledger.begin(context, price, false))
                .isInstanceOf(IllegalStateException.class);
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < 20; i++)
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        ledger.begin(context, price, true);
                                        return true;
                                    } catch (IllegalStateException e) {
                                        return false;
                                    }
                                }));
            start.countDown();
            int accepted = 0;
            for (var f : results) if (f.get(20, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT held_amount FROM ai_budget", BigDecimal.class))
                    .isEqualByComparingTo("0.0096");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void wrongTenantCannotFinishOrRead() {
        String id = ledger.begin(context, price, true);
        assertThatThrownBy(
                        () ->
                                ledger.finish(
                                        "other",
                                        id,
                                        "SUCCEEDED",
                                        new AiCallUsage(1L, 1L, null, null, "REPORTED"),
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.call("other", id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownCurrencyIsReconciledWithoutErasingBudgetSpend() {
        var unknown =
                new AiPriceSnapshot(
                        11L,
                        "fixture",
                        "OPENAI_CHAT_COMPLETIONS",
                        null,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        1000,
                        100);
        String id = ledger.begin(context, unknown, false);
        ledger.finish("000001", id, "FAILED", AiCallUsage.unknown(), "UNKNOWN");
        assertThatThrownBy(
                        () ->
                                ledger.configureBudget(
                                        "000001",
                                        "month",
                                        "CNY",
                                        "TENANT_MONTH",
                                        "",
                                        new BigDecimal("20"),
                                        true,
                                        7L))
                .isInstanceOf(IllegalStateException.class);
        ledger.reconcile(
                "000001", id, new BigDecimal("10"), "CNY", "confirmed", 7L, "unknown-currency");
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("20"), true, 7L);
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("20"), true, 7L);
        assertThat(jdbc.queryForObject("SELECT spent_amount FROM ai_budget", BigDecimal.class))
                .isEqualByComparingTo("10");
        assertThatThrownBy(
                        () ->
                                ledger.reconcile(
                                        "000001",
                                        id,
                                        new BigDecimal("10"),
                                        "USD",
                                        "confirmed",
                                        7L,
                                        "unknown-currency"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ledger.reconcile(
                                        "000001",
                                        id,
                                        new BigDecimal("11"),
                                        "USD",
                                        "confirmed",
                                        7L,
                                        "different-currency"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeBudgetDeniesCurrenciesWithoutAnAllowance() {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", BigDecimal.ZERO, true, 7L);
        var usd =
                new AiPriceSnapshot(
                        12L,
                        "fixture",
                        "OPENAI_CHAT_COMPLETIONS",
                        "USD",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        1000,
                        100);
        assertThatThrownBy(() -> ledger.begin(context, usd, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void budgetRollsIntoNewMonthWithoutAFreeUnbudgetedWindow() {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", new BigDecimal("1"), true, 7L);
        var november =
                new AiCallLedgerService(
                        jdbc.getDataSource(),
                        Clock.fixed(
                                Instant.parse("2026-11-01T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
        assertThatThrownBy(() -> november.begin(context, price, false))
                .isInstanceOf(IllegalStateException.class);
        november.begin(context, price, true);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ai_budget WHERE period_start='2026-11-01'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void callReservationSurvivesOuterTransactionRollback() {
        var outer = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(
                        () ->
                                outer.execute(
                                        s -> {
                                            ledger.begin(context, price, true);
                                            throw new IllegalStateException("outer rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.calls("000001", 7L, "req", 100)).hasSize(1);
    }

    @Test
    void invalidPricesAndOverPreciseCorrectionsAreRejected() {
        assertThatThrownBy(
                        () ->
                                new AiPriceSnapshot(
                                        11L,
                                        "fixture",
                                        "OPENAI_CHAT_COMPLETIONS",
                                        "CNY",
                                        new BigDecimal("-1"),
                                        BigDecimal.ZERO,
                                        1000,
                                        100))
                .isInstanceOf(IllegalArgumentException.class);
        String id = ledger.begin(context, price, false);
        ledger.finish("000001", id, "FAILED", AiCallUsage.unknown(), "UNKNOWN");
        assertThatThrownBy(
                        () ->
                                ledger.reconcile(
                                        "000001",
                                        id,
                                        new BigDecimal("0.0000000000001"),
                                        "CNY",
                                        "confirmed",
                                        7L,
                                        "precision"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditedReconfigurationClearsResolvedBoundViolation() {
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", BigDecimal.ONE, true, 7L);
        String id = ledger.begin(context, price, true);
        ledger.finish(
                "000001",
                id,
                "SUCCEEDED",
                new AiCallUsage(10000L, 0L, null, null, "REPORTED"),
                null);
        ledger.reconcile(
                "000001", id, new BigDecimal("0.001"), "CNY", "corrected", 7L, "bound-correction");
        ledger.configureBudget(
                "000001", "month", "CNY", "TENANT_MONTH", "", BigDecimal.ONE, true, 7L);
        assertThat(jdbc.queryForObject("SELECT blocked FROM ai_budget", Integer.class)).isZero();
    }

    @Test
    void governanceAlwaysUsesPhysicalMasterEvenUnderErpScope() {
        var routing = new DynamicRoutingDataSource(List.of());
        routing.setPrimary("master");
        routing.addDataSource("master", jdbc.getDataSource());
        routing.addDataSource(
                "erp",
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", ""));
        DynamicDataSourceContextHolder.push("erp");
        try {
            var explicit =
                    new AiCallLedgerService(
                            routing,
                            Clock.fixed(
                                    Instant.parse("2026-10-22T12:00:00Z"),
                                    ZoneId.of("Asia/Shanghai")));
            assertThatCode(() -> explicit.begin(context, price, false)).doesNotThrowAnyException();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_call", Integer.class))
                    .isEqualTo(1);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
