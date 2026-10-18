package com.nexus.report.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.report.ReportActor;
import com.nexus.common.report.ReportAuthorizationPort;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.dashboard.DashboardRefreshQuota;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.runtime.RuntimeReviewFixture;
import com.nexus.report.subscription.ReportDeliveryService;
import com.nexus.report.subscription.ReportSubscriptionProperties;
import com.nexus.report.subscription.ReportSubscriptionAccessGuard;
import com.nexus.report.subscription.ReportSubscriptionService;
import com.nexus.report.subscription.SubscriptionDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 使用隔离 H2 和 JDBC 返回类型模拟验证时间列读取边界。 */
class TimestampColumnMapRowMapperTest {
    @TempDir Path directory;

    @Test
    void dashboardQuotaReadsLocalDateTimeFromDefaultColumnMaps() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var jdbc = mysqlJdbc(fixture.jdbc, Instant.parse("2026-10-18T00:00:00Z"));

            new DashboardRefreshQuota(jdbc, resources).reserve(fixture.owner);

            assertEquals(
                    1,
                    fixture.jdbc.queryForObject(
                            "SELECT counter FROM rpt_refresh_quota WHERE scope_key='GLOBAL'",
                            Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void dashboardWindowExpiresAtExactlySixtySeconds(long boundaryMillis) {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.parse("2026-10-18T00:30:00Z");
            var start = now.minusSeconds(60).plusMillis(boundaryMillis);
            var jdbc = mysqlJdbc(fixture.jdbc, now);
            String scope = "USER:A:7";
            fixture.jdbc.update(
                    "INSERT INTO rpt_refresh_quota(scope_key,window_start,counter) VALUES(?,?,12)",
                    scope,
                    Timestamp.from(start));
            assertInstanceOf(
                    LocalDateTime.class,
                    jdbc.queryForMap("SELECT window_start FROM rpt_refresh_quota")
                            .get("window_start"));
            var quota = new DashboardRefreshQuota(jdbc, resources);

            if (boundaryMillis > 0) {
                var fault = assertThrows(ReportFault.class, () -> quota.reserve(fixture.owner));
                assertEquals("DASHBOARD_RATE_LIMIT", fault.code());
                assertEquals(
                        0,
                        fixture.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM rpt_refresh_quota WHERE scope_key='GLOBAL'",
                                Integer.class));
            } else {
                quota.reserve(fixture.owner);
            }
            assertEquals(
                    boundaryMillis > 0 ? 12 : 1,
                    fixture.jdbc.queryForObject(
                            "SELECT counter FROM rpt_refresh_quota WHERE scope_key=?",
                            Integer.class,
                            scope));
            assertEquals(
                    boundaryMillis > 0 ? start : now,
                    fixture.jdbc.queryForObject(
                                    "SELECT window_start FROM rpt_refresh_quota WHERE scope_key=?",
                                    Timestamp.class,
                                    scope)
                            .toInstant());
        }
    }

    @Test
    void manualSubscriptionDrainsOnceWithTheOriginalScheduledInstant() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.parse("2026-10-18T00:30:00Z");
            var service = subscriptions(fixture, resources, now, false);
            var draft = publishedSubscription(fixture, service);

            service.manual(fixture.owner, draft, "jdbc-manual");
            service.drain();

            assertEquals(1, fixture.runtime.jobs(fixture.owner).size());
            assertEquals(
                    "QUEUED",
                    fixture.jdbc.queryForObject(
                            "SELECT state FROM rpt_subscription_run", String.class));
            assertEquals(
                    now,
                    fixture.jdbc.queryForObject(
                                    "SELECT scheduled_instant FROM rpt_subscription_run",
                                    Timestamp.class)
                            .toInstant());
            assertEquals(
                    1,
                    fixture.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_delivery", Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void subscriptionDispatchIncludesTheDueInstantAndExcludesTheFuture(long boundaryMillis) {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.parse("2026-10-18T00:30:00Z");
            var due = now.plusMillis(boundaryMillis);
            var service = subscriptions(fixture, resources, now, true);
            publishedSubscription(fixture, service);
            fixture.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", Timestamp.from(due));

            assertEquals(boundaryMillis > 0 ? 0 : 1, service.dispatchDue(now));
            assertEquals(
                    boundaryMillis > 0 ? 0 : 1,
                    fixture.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_subscription_run", Integer.class));
            assertEquals(
                    boundaryMillis > 0 ? due : Instant.parse("2026-10-18T01:00:00Z"),
                    fixture.jdbc.queryForObject(
                                    "SELECT next_at FROM rpt_subscription_schedule",
                                    Timestamp.class)
                            .toInstant());
            if (boundaryMillis <= 0) {
                assertEquals(1, fixture.runtime.jobs(fixture.owner).size());
                assertEquals(
                        due,
                        fixture.jdbc.queryForObject(
                                        "SELECT scheduled_instant FROM rpt_subscription_run",
                                        Timestamp.class)
                                .toInstant());
                assertEquals(0, service.dispatchDue(now));
            }
        }
    }

    @Test
    void deliveryListPreservesTheDriverExpiryInstant() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.now();
            var service = subscriptions(fixture, resources, now, false, fixture.jdbc);
            service.manual(fixture.owner, publishedSubscription(fixture, service), "jdbc-list");
            var expires = Instant.parse("2026-10-18T00:30:45.123456Z");
            fixture.jdbc.update("UPDATE rpt_delivery SET expires_at=?", Timestamp.from(expires));

            var delivery = deliveries(fixture, resources, now).list(fixture.owner).get(0);

            assertEquals(expires.toString(), delivery.expires());
            assertEquals(fixture.runtime.jobs(fixture.owner).get(0).id(), delivery.jobId());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void deliveryReconciliationExpiresAtTheDeadline(long boundaryMillis) {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.parse("2026-10-18T00:30:00Z");
            var service = subscriptions(fixture, resources, now, false, fixture.jdbc);
            service.manual(
                    fixture.owner, publishedSubscription(fixture, service), "jdbc-reconcile");
            fixture.jdbc.update(
                    "UPDATE rpt_delivery SET expires_at=?",
                    Timestamp.from(now.plusMillis(boundaryMillis)));
            var deliveries = deliveries(fixture, resources, now);

            try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
                clock.when(Instant::now).thenReturn(now);
                deliveries.reconcile();
            }

            assertEquals(
                    boundaryMillis > 0 ? "PREPARING" : "EXPIRED",
                    fixture.jdbc.queryForObject("SELECT state FROM rpt_delivery", String.class));
            assertEquals(
                    boundaryMillis > 0 ? null : "SNAPSHOT_EXPIRED",
                    fixture.jdbc.queryForObject(
                            "SELECT error_code FROM rpt_delivery", String.class));
            assertEquals(
                    boundaryMillis > 0 ? "PREPARING" : "QUEUED",
                    fixture.jdbc.queryForObject(
                            "SELECT state FROM rpt_subscription_run", String.class));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void deliveryClaimRejectsExpiryAtTheDeadlineBeforeCheckingJobReadiness(long boundaryMillis) {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var now = Instant.parse("2026-10-18T00:30:00Z");
            var service = subscriptions(fixture, resources, now, false, fixture.jdbc);
            service.manual(fixture.owner, publishedSubscription(fixture, service), "jdbc-claim");
            fixture.jdbc.update(
                    "UPDATE rpt_delivery SET expires_at=?",
                    Timestamp.from(now.plusMillis(boundaryMillis)));
            String id =
                    fixture.jdbc.queryForObject("SELECT delivery_id FROM rpt_delivery", String.class);
            var deliveries = deliveries(fixture, resources, now);

            try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
                clock.when(Instant::now).thenReturn(now);
                var fault =
                        assertThrows(ReportFault.class, () -> deliveries.claim(fixture.owner, id));
                assertEquals(
                        boundaryMillis > 0 ? "RUN_NOT_READY" : "SNAPSHOT_EXPIRED", fault.code());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Z", "+08:00", "-04:00", "+05:45"})
    void timestampMappingUsesTheDriverInstantInsteadOfInferringAZone(String sessionOffset)
            throws SQLException {
        var local = LocalDateTime.of(2026, 10, 18, 0, 30, 45, 123456789);
        var expected = Timestamp.from(local.toInstant(ZoneOffset.of(sessionOffset)));
        var result = timestampResult(local, expected);

        var row = TimestampColumnMapRowMapper.INSTANCE.mapRow(result, 0);

        assertEquals(expected.toInstant(), ((Timestamp) row.get("window_start")).toInstant());
        verify(result).getTimestamp(1);
        verify(result, never()).getObject(1);
    }

    @Test
    void timestampMappingPreservesNull() throws SQLException {
        var result = timestampResult(null, null);

        assertNull(TimestampColumnMapRowMapper.INSTANCE.mapRow(result, 0).get("window_start"));

        verify(result).getTimestamp(1);
    }

    @Test
    void nonTimestampColumnsKeepTheirOriginalJdbcValues() throws SQLException {
        var result = mock(ResultSet.class);
        var metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(4);
        String[] names = {"identifier", "amount", "optional", "business_date"};
        int[] types = {Types.BIGINT, Types.DECIMAL, Types.VARCHAR, Types.DATE};
        var amount = new BigDecimal("9007199254740993.123400");
        var date = java.sql.Date.valueOf("2026-10-18");
        Object[] values = {Long.MAX_VALUE, amount, null, date};
        for (int index = 1; index <= names.length; index++) {
            when(metadata.getColumnLabel(index)).thenReturn(names[index - 1]);
            when(metadata.getColumnType(index)).thenReturn(types[index - 1]);
            when(result.getObject(index)).thenReturn(values[index - 1]);
        }

        var row = TimestampColumnMapRowMapper.INSTANCE.mapRow(result, 0);

        assertEquals(Long.MAX_VALUE, row.get("identifier"));
        assertEquals(amount, row.get("amount"));
        assertNull(row.get("optional"));
        assertEquals(date, row.get("business_date"));
        verify(result, never()).getTimestamp(anyInt());
    }

    private ResultSet timestampResult(LocalDateTime raw, Timestamp timestamp) throws SQLException {
        var result = mock(ResultSet.class);
        var metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("window_start");
        when(metadata.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(result.getObject(1)).thenReturn(raw);
        when(result.getTimestamp(1)).thenReturn(timestamp);
        return result;
    }

    private ReportSubscriptionService subscriptions(
            RuntimeReviewFixture fixture,
            ReportResourceStore resources,
            Instant now,
            boolean scheduled) {
        return subscriptions(fixture, resources, now, scheduled, mysqlJdbc(fixture.jdbc, now));
    }

    private ReportSubscriptionService subscriptions(
            RuntimeReviewFixture fixture,
            ReportResourceStore resources,
            Instant now,
            boolean scheduled,
            JdbcTemplate jdbc) {
        var properties = new ReportSubscriptionProperties();
        properties.setSchedulingEnabled(scheduled);
        return new ReportSubscriptionService(
                resources,
                jdbc,
                fixture.store,
                fixture.definitions,
                fixture.runtime,
                fixture.compiler,
                properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private String publishedSubscription(
            RuntimeReviewFixture fixture, ReportSubscriptionService service) {
        var model =
                new SubscriptionDefinition(
                        1,
                        fixture.store
                                .definition("A", fixture.definition.id())
                                .orElseThrow()
                                .publishedVersion(),
                        "XLSX_DATA",
                        Map.of(),
                        List.of("7"),
                        "0 0 * * * *",
                        "UTC",
                        "EARLIER_OFFSET",
                        3600,
                        false);
        var draft = service.create(fixture.owner, "JDBC_TIMESTAMP", "时间映射回归", model);
        service.publish(fixture.owner, draft.id(), draft.revision());
        return draft.id();
    }

    private ReportDeliveryService deliveries(
            RuntimeReviewFixture fixture, ReportResourceStore resources, Instant now) {
        return new ReportDeliveryService(
                resources,
                mysqlJdbc(fixture.jdbc, now),
                fixture.store,
                fixture.definitions,
                fixture.runtime,
                fixture.compiler,
                new ReportSubscriptionProperties(),
                null);
    }

    private ReportResourceStore resources(RuntimeReviewFixture fixture) {
        ReportExtensionSchema.apply(fixture.jdbc);
        var resources = new ReportResourceStore(
                fixture.jdbc,
                new DataSourceTransactionManager(fixture.jdbc.getDataSource()),
                fixture.store);
        var authorization = new ReportAuthorizationPort() {
            public ReportActor resolve(String tenant, long user) {
                return fixture.actors.get(user);
            }

            public boolean subjectExists(String tenant, String type, long user) {
                return fixture.actors.containsKey(user);
            }
        };
        fixture.runtime.constraints(
                new ReportSubscriptionAccessGuard(
                                resources,
                                fixture.jdbc,
                                authorization,
                                fixture.compiler,
                                fixture.definitions)
                        ::check);
        return resources;
    }

    private JdbcTemplate mysqlJdbc(JdbcTemplate original, Instant databaseNow) {
        return new JdbcTemplate(original.getDataSource()) {
            @Override
            protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
                return new ColumnMapRowMapper() {
                    @Override
                    protected Object getColumnValue(ResultSet result, int index)
                            throws SQLException {
                        if (result.getMetaData().getColumnType(index) == Types.TIMESTAMP)
                            return result.getObject(index, LocalDateTime.class);
                        return super.getColumnValue(result, index);
                    }
                };
            }

            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                if (sql.equals("SELECT CURRENT_TIMESTAMP") && requiredType == Timestamp.class)
                    return requiredType.cast(Timestamp.from(databaseNow));
                return super.queryForObject(sql, requiredType);
            }
        };
    }
}
