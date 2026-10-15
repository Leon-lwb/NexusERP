package com.nexus.report.query;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.zaxxer.hikari.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import javax.sql.DataSource;

/** 通过独立SELECT账户执行目录规划，每次连接校验账户权限并有界流式读取。 */
public final class CatalogQueryExecutor implements AutoCloseable {
    /** 保存当前dataSource状态或配置。 */
    private final DataSource dataSource;

    /** 保存当前approvedTables状态或配置。 */
    private Set<String> approvedTables = Set.of();

    /**
     * 固定整个代码目录白名单，允许账户读取多个批准表。
     *
     * @param tables 当前契约的tables配置或状态
     */
    public void approvedTables(Set<String> tables) {
        this.approvedTables = Set.copyOf(tables);
    }

    /** 保存当前timeout状态或配置。 */
    private final int timeout;

    /** 保存当前ownsSource状态或配置。 */
    private final boolean ownsSource;

    /**
     * 从可信配置创建小型只读池，默认关闭时不创建连接。
     *
     * @param properties 当前契约的properties配置或状态
     */
    public CatalogQueryExecutor(ReportQueryProperties properties) {
        timeout = properties.getTimeoutSeconds();
        ownsSource = true;
        if (!properties.isEnabled()) {
            dataSource = null;
            return;
        }
        if (properties.getJdbcUrl() == null
                || !properties.getJdbcUrl().startsWith("jdbc:mysql:")
                || properties.getUsername() == null
                || properties.getUsername().isBlank())
            throw new IllegalArgumentException("查询数据源必须配置独立MySQL只读账户");
        var config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setReadOnly(true);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("report-catalog-read");
        dataSource = new HikariDataSource(config);
    }

    /**
     * 注入隔离验证用的实际数据源，仍检查数据库权限。
     *
     * @param source 当前契约的source配置或状态
     * @param timeout 当前契约的timeout配置或状态
     */
    public CatalogQueryExecutor(DataSource source, int timeout) {
        dataSource = source;
        this.timeout = timeout;
        ownsSource = false;
    }

    /**
     * 核对账户具备且仅具备目录表读取权限。
     *
     * @param tables 当前契约的tables配置或状态
     */
    public void verify(Set<String> tables) {
        if (dataSource == null)
            throw new ReportFault("QUERY_SOURCE_DISABLED", "请配置并启用独立SELECT报表数据源", 409);
        try (var connection = dataSource.getConnection()) {
            ReadOnlyQueryAccount.verify(connection, tables);
        } catch (SQLException failure) {
            throw new ReportFault("QUERY_SOURCE_UNAVAILABLE", "只读查询数据源不可用", 503);
        }
    }

    /**
     * 对已规划SQL执行固定页码和稳定排序，不返回SQL或原始数据库异常。
     *
     * @param plan 当前契约的plan配置或状态
     * @param context 当前契约的context配置或状态
     * @param query 当前契约的query配置或状态
     * @return 当前受控处理的固定结果
     */
    public DatasetResult query(
            QueryPlanner.Plan plan, ReportExecutionContext context, DatasetQuery query) {
        var rows = new ArrayList<Map<String, Object>>();
        Instant time = Instant.now();
        long total = execute(plan, context, query, rows::add, false);
        return new DatasetResult(rows, total, time);
    }

    /**
     * 全量读取最多十万行，并在下一行前检查取消信号。
     *
     * @param plan 当前契约的plan配置或状态
     * @param context 当前契约的context配置或状态
     * @param query 当前契约的query配置或状态
     * @param consumer 当前契约的consumer配置或状态
     */
    public void stream(
            QueryPlanner.Plan plan,
            ReportExecutionContext context,
            DatasetQuery query,
            Consumer<Map<String, Object>> consumer) {
        execute(plan, context, query, consumer, true);
    }

    /**
     * 在同一个只读事务内获取总数与结果，保证单库一致性。
     *
     * @param plan 当前契约的plan配置或状态
     * @param context 当前契约的context配置或状态
     * @param query 当前契约的query配置或状态
     * @param consumer 当前契约的consumer配置或状态
     * @param streaming 当前契约的streaming配置或状态
     * @return 当前受控处理的固定结果
     */
    private long execute(
            QueryPlanner.Plan plan,
            ReportExecutionContext context,
            DatasetQuery query,
            Consumer<Map<String, Object>> consumer,
            boolean streaming) {
        if (dataSource == null)
            throw new ReportFault("QUERY_SOURCE_DISABLED", "请配置并启用独立SELECT报表数据源", 409);
        context.cancellation().check();
        if (query.pageNumber() < 1
                || query.pageSize() < 1
                || query.pageSize() > (query.snapshot() ? 100001 : 200))
            throw ReportFault.invalid("查询分页范围无效");
        try (var connection = dataSource.getConnection()) {
            ReadOnlyQueryAccount.verify(
                    connection, approvedTables.isEmpty() ? plan.tables() : approvedTables);
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            long total;
            try (var count =
                    connection.prepareStatement(
                            "SELECT COUNT(*) FROM (" + plan.sql() + ") count_guard")) {
                count.setQueryTimeout(timeout);
                bind(count, plan.values());
                context.cancellation().attach(() -> cancel(count));
                try (var result = count.executeQuery()) {
                    result.next();
                    total = result.getLong(1);
                } finally {
                    context.cancellation().detach();
                }
            }
            context.cancellation().check();
            try (var statement = connection.prepareStatement(plan.sql() + " LIMIT ? OFFSET ?")) {
                statement.setQueryTimeout(streaming ? Math.min(120, timeout * 6) : timeout);
                statement.setFetchSize(
                        connection
                                        .getMetaData()
                                        .getDatabaseProductName()
                                        .toLowerCase(Locale.ROOT)
                                        .contains("mysql")
                                ? Integer.MIN_VALUE
                                : 500);
                var values = new ArrayList<>(plan.values());
                values.add(streaming ? 100001 : query.pageSize());
                values.add(
                        streaming || query.snapshot()
                                ? 0L
                                : (long) (query.pageNumber() - 1) * query.pageSize());
                bind(statement, values);
                context.cancellation().attach(() -> cancel(statement));
                try (var result = statement.executeQuery()) {
                    int count = 0;
                    while (result.next()) {
                        context.cancellation().check();
                        if (++count > 100000) throw ReportFault.capacity();
                        var row = new LinkedHashMap<String, Object>();
                        for (var field : query.fields()) row.put(field, result.getObject(field));
                        consumer.accept(row);
                    }
                } finally {
                    context.cancellation().detach();
                }
            }
            connection.commit();
            return total;
        } catch (ReportFault fault) {
            throw fault;
        } catch (SQLException failure) {
            var fault =
                    new ReportFault(
                            context.cancellation().cancelled()
                                    ? "CANCELLED"
                                    : "QUERY_EXECUTION_FAILED",
                            "受控查询未能完成",
                            503);
            fault.initCause(failure);
            throw fault;
        }
    }

    /**
     * 所有客户端值绑定为参数，时间值按声明类型交给驱动。
     *
     * @param statement 当前契约的statement配置或状态
     * @param values 固定类型的精确值列表
     */
    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
    }

    /**
     * 取消当前语句，关闭竞态不影响原取消语义。
     *
     * @param statement 当前契约的statement配置或状态
     */
    private static void cancel(Statement statement) {
        try {
            statement.cancel();
        } catch (SQLException ignored) {
        }
    }

    /** 仅关闭本对象创建的连接池。 */
    @Override
    public void close() {
        if (ownsSource && dataSource instanceof HikariDataSource pool) pool.close();
    }
}
