package com.nexus.common.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;

import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 只读查询目录由代码维护，接口和模板不能提供物理标识符或数据库连接串。 */
public abstract class JdbcDatasetProvider implements ReportDatasetProvider {
    /** 保存当前 {@code jdbc} 值。 */
    private final JdbcTemplate jdbc;

    /** 可信数据集契约。 */
    private final DatasetDescriptor descriptor;

    /** 保存当前 {@code from} 值。 */
    private final String from;

    /** 保存当前 {@code alias} 值。 */
    private final String alias;

    /** 保存当前 {@code key} 值。 */
    private final String key;

    /** 保存当前 {@code expressions} 值。 */
    private final Map<String, String> expressions;

    /**
     * 初始化只读取数数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     * @param descriptor 可信数据集契约
     * @param from 当前操作的 {@code from} 输入
     * @param alias 当前操作的 {@code alias} 输入
     * @param key 当前操作的 {@code key} 输入
     * @param expressions 当前操作的 {@code expressions} 输入
     */
    protected JdbcDatasetProvider(
            JdbcTemplate jdbc,
            DatasetDescriptor descriptor,
            String from,
            String alias,
            String key,
            Map<String, String> expressions) {
        this.jdbc = jdbc;
        this.descriptor = descriptor;
        this.from = from;
        this.alias = alias;
        this.key = key;
        this.expressions = Collections.unmodifiableMap(new LinkedHashMap<>(expressions));
        if (!expressions
                .keySet()
                .containsAll(descriptor.fields().stream().map(DatasetField::name).toList()))
            throw new IllegalArgumentException("Projection catalog incomplete");
    }

    /**
     * 读取固定代码契约描述。
     *
     * @return 当前处理返回的固定结果
     */
    public DatasetDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Filter，负责当前模块对应的契约或处理职责。
     *
     * @param sql 当前契约的sql配置或状态
     * @param values 固定类型的精确值列表
     */
    protected record Filter(String sql, List<Object> values) {

        /**
         * 初始化Filter并保存所需依赖或不可变值。
         *
         * @param sql 代码生成的查询语句
         * @param values 绑定值
         */
        public Filter {
            values = List.copyOf(values);
        }
    }

    /**
     * 生成契约允许的绑定筛选条件。
     *
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    protected List<Filter> filters(DatasetQuery query) {
        return List.of();
    }

    /**
     * 生成包含结束日期的有界日期条件。
     *
     * @param query 已声明字段和筛选参数
     * @param expression 当前操作的 {@code expression} 输入
     * @return 当前处理返回的固定结果
     */
    protected static List<Filter> dates(DatasetQuery query, String expression) {
        List<Filter> filters = new ArrayList<>();
        Object from = query.parameters().get("fromDate"), to = query.parameters().get("toDate");
        if (from != null)
            filters.add(new Filter(expression + " >= ?", List.of(Date.valueOf(from.toString()))));
        if (to != null)
            filters.add(
                    new Filter(
                            expression + " < ?",
                            List.of(Date.valueOf(LocalDate.parse(to.toString()).plusDays(1)))));
        return filters;
    }

    /**
     * 完成 {@code equal} 对应的只读取数数据集提供者处理。
     *
     * @param filters 当前操作的 {@code filters} 输入
     * @param query 已声明字段和筛选参数
     * @param parameter 当前操作的 {@code parameter} 输入
     * @param expression 当前操作的 {@code expression} 输入
     */
    protected static void equal(
            List<Filter> filters, DatasetQuery query, String parameter, String expression) {
        Object value = query.parameters().get(parameter);
        if (value != null) filters.add(new Filter(expression + " = ?", List.of(value)));
    }

    /**
     * 查询，负责当前模块对应的契约或处理职责。
     *
     * @param where 当前契约的where配置或状态
     * @param values 固定类型的精确值列表
     * @param query 当前契约的query配置或状态
     * @param context 当前契约的context配置或状态
     */
    private record Query(
            String where,
            List<Object> values,
            DatasetQuery query,
            ReportExecutionContext context) {}

    /**
     * 完成 {@code build} 对应的只读取数数据集提供者处理。
     *
     * @param context 服务端执行上下文
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    private Query build(ReportExecutionContext context, DatasetQuery input) {
        context.cancellation().check();
        DatasetQuery query = DatasetQueryValidator.validate(descriptor, input);
        if (!context.actor().has(descriptor.permission()))
            throw new DatasetAccessException("SCOPE_DENIED", "缺少业务数据集权限");
        List<ReportScope> scopes = new ArrayList<>(context.scopes());
        scopes.add(context.actor().scopeFor(descriptor.permission()));
        for (String name : query.fields()) {
            String permission = descriptor.field(name).permission();
            if (permission == null || permission.isBlank()) continue;
            if (!context.actor().has(permission))
                throw new DatasetAccessException("SCOPE_DENIED", "缺少字段权限");
            scopes.add(context.actor().scopeFor(permission));
        }
        var effective = new ReportExecutionContext(context.actor(), scopes, context.cancellation());
        var policy = ReportSqlScope.where(alias, effective);
        StringBuilder where = new StringBuilder(policy.sql());
        List<Object> values = new ArrayList<>(policy.parameters());
        for (Filter filter : filters(query)) {
            where.append(" AND (").append(filter.sql()).append(")");
            values.addAll(filter.values());
        }
        return new Query(where.toString(), values, query, effective);
    }

    /**
     * 完成 {@code select} 对应的只读取数数据集提供者处理。
     *
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    private String select(Query query) {
        String columns =
                query.query.fields().isEmpty()
                        ? "1 AS _record"
                        : query.query.fields().stream()
                                .map(field -> expressions.get(field) + " AS " + field)
                                .collect(Collectors.joining(","));
        String order =
                query.query.sortField() == null ? key : expressions.get(query.query.sortField());
        return "SELECT "
                + columns
                + " FROM "
                + from
                + " WHERE "
                + query.where
                + " ORDER BY "
                + order
                + (query.query.descending() ? " DESC" : " ASC")
                + (query.query.sortField() == null ? "" : ", " + key + " ASC")
                + " LIMIT ? OFFSET ?";
    }

    /**
     * 生成参数绑定、超时和取消可用的查询语句。
     *
     * @param sql 代码生成的查询语句
     * @param values 绑定值
     * @param context 服务端执行上下文
     * @param streaming 当前操作的 {@code streaming} 输入
     * @return 当前处理返回的固定结果
     */
    private PreparedStatementCreator statement(
            String sql, List<Object> values, ReportExecutionContext context, boolean streaming) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(streaming ? 120 : 10);
            if (streaming)
                statement.setFetchSize(
                        connection
                                        .getMetaData()
                                        .getDatabaseProductName()
                                        .toLowerCase(Locale.ROOT)
                                        .contains("mysql")
                                ? Integer.MIN_VALUE
                                : 500);
            for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
            context.cancellation()
                    .attach(
                            () -> {
                                try {
                                    statement.cancel();
                                } catch (SQLException ignored) {
                                }
                            });
            return statement;
        };
    }

    /**
     * 完成 {@code row} 对应的只读取数数据集提供者处理。
     *
     * @param result 当前操作的 {@code result} 输入
     * @param fields 允许访问的字段
     * @return 当前处理返回的固定结果
     */
    private Map<String, Object> row(ResultSet result, Set<String> fields) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) values.put(field, result.getObject(field));
        return values;
    }

    /**
     * 执行受控只读查询。
     *
     * @param context 服务端执行上下文
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    public DatasetResult query(ReportExecutionContext context, DatasetQuery input) {
        Query query = build(context, input);
        Instant time = Instant.now();
        try {
            Long total =
                    jdbc.query(
                            statement(
                                    "SELECT COUNT(*) FROM " + from + " WHERE " + query.where,
                                    query.values,
                                    query.context,
                                    false),
                            result -> result.next() ? result.getLong(1) : 0L);
            context.cancellation().check();
            List<Object> values = new ArrayList<>(query.values);
            values.add(query.query.pageSize());
            values.add(
                    query.query.snapshot()
                            ? 0L
                            : (long) (query.query.pageNumber() - 1) * query.query.pageSize());
            List<Map<String, Object>> rows =
                    jdbc.query(
                            statement(select(query), values, query.context, false),
                            (result, index) -> row(result, query.query.fields()));
            return new DatasetResult(rows, total == null ? 0 : total, time);
        } finally {
            context.cancellation().detach();
        }
    }

    /**
     * 在取消与容量控制下逐行读取。
     *
     * @param context 服务端执行上下文
     * @param input 待校验输入
     * @param consumer 当前操作的 {@code consumer} 输入
     */
    public void stream(
            ReportExecutionContext context,
            DatasetQuery input,
            Consumer<Map<String, Object>> consumer) {
        Query query = build(context, input);
        List<Object> values = new ArrayList<>(query.values);
        values.add(100001);
        values.add(0L);
        try {
            jdbc.query(
                    statement(select(query), values, query.context, true),
                    result -> {
                        int count = 0;
                        while (result.next()) {
                            context.cancellation().check();
                            if (++count > 100000)
                                throw new DatasetAccessException("CAPACITY_EXCEEDED", "明细超过十万行");
                            consumer.accept(row(result, query.query.fields()));
                        }
                        return null;
                    });
        } finally {
            context.cancellation().detach();
        }
    }
}
