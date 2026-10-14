package com.nexus.report.dashboard;

import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportResourceStore;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

/** 数据库刷新限流按全局、租户、用户固定次序锁定，跨节点不重复使用本机计数。 */
public final class DashboardRefreshQuota {
    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /**
     * 使用元数据事务，时间由主库提供。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param resources 当前契约的resources配置或状态
     */
    public DashboardRefreshQuota(JdbcTemplate jdbc, ReportResourceStore resources) {
        this.jdbc = jdbc;
        this.resources = resources;
    }

    /**
     * 每分钟全局1200次、租户120次、用户12次，超限事务不消耗其他层计数。
     *
     * @param actor 执行用户
     */
    public void reserve(ReportActor actor) {
        resources.atomic(
                () -> {
                    Instant now =
                            jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class)
                                    .toInstant();
                    count("GLOBAL", 1200, now);
                    count("TENANT:" + actor.tenantId(), 120, now);
                    count("USER:" + actor.tenantId() + ":" + actor.userId(), 12, now);
                    return null;
                });
    }

    /**
     * 创建并锁定计数窗口，超过限制使用明确的429错误。
     *
     * @param scope 当前契约的scope配置或状态
     * @param limit 当前操作的容量上限
     * @param now 当前契约的now配置或状态
     */
    private void count(String scope, int limit, Instant now) {
        jdbc.update(
                "INSERT IGNORE INTO rpt_refresh_quota(scope_key,window_start,counter)"
                        + " VALUES(?,?,0)",
                scope,
                Timestamp.from(now));
        var row =
                jdbc.queryForMap(
                        "SELECT window_start,counter FROM rpt_refresh_quota WHERE scope_key=? FOR"
                                + " UPDATE",
                        scope);
        Instant start = ((Timestamp) row.get("window_start")).toInstant();
        int value =
                now.isBefore(start.plusSeconds(60)) ? ((Number) row.get("counter")).intValue() : 0;
        if (value >= limit) throw new ReportFault("DASHBOARD_RATE_LIMIT", "仪表盘刷新过于频繁，请稍后重试", 429);
        jdbc.update(
                "UPDATE rpt_refresh_quota SET window_start=?,counter=? WHERE scope_key=?",
                Timestamp.from(value == 0 ? now : start),
                value + 1,
                scope);
    }
}
