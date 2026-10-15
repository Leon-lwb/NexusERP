package com.nexus.common.report;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 代码固定的业务候选查询底座，检索和按值回显共用租户及组织范围。 */
public abstract class JdbcReportLookupProvider implements ReportLookupProvider {
    /**
     * 候选规则只接受代码注册的简单物理标识符。
     *
     * @param binding 固定数据集版本与参数绑定
     * @param table 业务表名
     * @param key 候选ID或编码列
     * @param code 向用户展示的业务编码列
     * @param label 向用户展示的业务名称列
     * @param keyType 主键值的精确类型，只支持ID和STRING
     */
    public record Rule(
            ReportLookupBinding binding,
            String table,
            String key,
            String code,
            String label,
            ValueType keyType) {
        /**
         * 注册时检查标识符，禁止运行时传入SQL片段。
         *
         * @param binding 固定选择器绑定
         * @param table 固定业务表
         * @param key 固定值列
         * @param code 固定编码列
         * @param label 固定名称列
         * @param keyType 值类型
         */
        public Rule {
            Objects.requireNonNull(binding);
            for (String identifier : List.of(table, key, code, label)) {
                if (!identifier.matches("[a-z][a-z0-9_]*"))
                    throw new IllegalArgumentException("选择器物理标识符无效");
            }
            if (keyType != ValueType.ID && keyType != ValueType.STRING)
                throw new IllegalArgumentException("选择器值类型无效");
        }
    }

    /** 每次调用由业务模块的动态数据源代理决定连接位置。 */
    private final JdbcTemplate jdbc;

    /** 不可变的已安装候选规则，键包含完整契约版本。 */
    private final Map<ReportLookupBinding, Rule> rules;

    /**
     * 构建有查询超时且规则不可重复的业务候选提供者。
     *
     * @param source 使用业务模块动态数据源的JDBC入口
     * @param installed 代码声明的候选规则
     */
    protected JdbcReportLookupProvider(JdbcTemplate source, List<Rule> installed) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(source.getDataSource()));
        jdbc.setQueryTimeout(10);
        var values = new LinkedHashMap<ReportLookupBinding, Rule>();
        for (Rule rule : installed) {
            if (values.putIfAbsent(rule.binding(), rule) != null)
                throw new IllegalArgumentException("选择器绑定重复");
        }
        rules = Collections.unmodifiableMap(values);
    }

    /**
     * 返回已安装规则，授权过滤仍由报表服务完成。
     *
     * @return 固定顺序的候选绑定
     */
    @Override
    public List<ReportLookupBinding> bindings() {
        return List.copyOf(rules.keySet());
    }

    /**
     * 按名称、编码和完整ID搜索，所有条件使用绑定参数。
     *
     * @param binding 已安装绑定
     * @param context 当前身份和业务范围
     * @param query 有界检索及分页参数
     * @return 当前可见候选与总数
     */
    @Override
    public Result query(ReportLookupBinding binding, ReportExecutionContext context, Query query) {
        Rule rule = rule(binding);
        var effective = effective(binding, context);
        var scope = ReportSqlScope.where("p", effective);
        var values = new ArrayList<Object>(scope.parameters());
        String pattern =
                "%"
                        + query.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_")
                        + "%";
        values.add(pattern);
        values.add(pattern);
        String match =
                "p."
                        + rule.code()
                        + " LIKE ? ESCAPE '!' OR p."
                        + rule.label()
                        + " LIKE ? ESCAPE '!'";
        if (rule.keyType() == ValueType.STRING || validId(query.keyword())) {
            match += " OR p." + rule.key() + " = ?";
            values.add(key(rule, query.keyword()));
        }
        String where = scope.sql() + " AND (" + match + ")";
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + rule.table() + " p WHERE " + where,
                        Long.class,
                        values.toArray());
        effective.cancellation().check();
        values.add(query.size());
        values.add((long) (query.page() - 1) * query.size());
        var options =
                jdbc.query(
                        select(rule)
                                + " WHERE "
                                + where
                                + " ORDER BY p."
                                + rule.key()
                                + " LIMIT ? OFFSET ?",
                        (row, index) ->
                                option(
                                        row.getString("lookup_value"),
                                        row.getString("lookup_code"),
                                        row.getString("lookup_label")),
                        values.toArray());
        effective.cancellation().check();
        return new Result(options, count == null ? 0 : count);
    }

    /**
     * 按完整值精确回显，不能依靠模糊检索绕过不可见对象边界。
     *
     * @param binding 已安装绑定
     * @param context 当前身份和业务范围
     * @param value 完整ID或业务编码
     * @return 仅当前可见对象
     */
    @Override
    public Optional<Option> selected(
            ReportLookupBinding binding, ReportExecutionContext context, String value) {
        Rule rule = rule(binding);
        var effective = effective(binding, context);
        var scope = ReportSqlScope.where("p", effective);
        var values = new ArrayList<Object>(scope.parameters());
        values.add(key(rule, value));
        var options =
                jdbc.query(
                        select(rule)
                                + " WHERE "
                                + scope.sql()
                                + " AND p."
                                + rule.key()
                                + " = ? LIMIT 1",
                        (row, index) ->
                                option(
                                        row.getString("lookup_value"),
                                        row.getString("lookup_code"),
                                        row.getString("lookup_label")),
                        values.toArray());
        effective.cancellation().check();
        return options.stream().findFirst();
    }

    /**
     * 只允许使用当前提供者注册的绑定。
     *
     * @param binding 待匹配的绑定
     * @return 固定规则
     */
    private Rule rule(ReportLookupBinding binding) {
        Rule rule = rules.get(binding);
        if (rule == null) throw new DatasetAccessException("LOOKUP_UNSUPPORTED", "选择器未安装");
        return rule;
    }

    /**
     * 对候选实体单独校验操作权限并交集其组织范围。
     *
     * @param binding 候选实体权限绑定
     * @param context 当前服务端上下文
     * @return 不扩大原业务范围的执行上下文
     */
    private ReportExecutionContext effective(
            ReportLookupBinding binding, ReportExecutionContext context) {
        if (!context.actor().has(binding.permission()))
            throw new DatasetAccessException("SCOPE_DENIED", "缺少选择器业务权限");
        context.cancellation().check();
        var scopes = new ArrayList<>(context.scopes());
        scopes.add(context.actor().scopeFor(binding.permission()));
        return new ReportExecutionContext(context.actor(), scopes, context.cancellation());
    }

    /**
     * 返回代码固定的投影，物理字段不从请求中读取。
     *
     * @param rule 已校验的规则
     * @return 只读投影SQL
     */
    private String select(Rule rule) {
        return "SELECT p."
                + rule.key()
                + " lookup_value,p."
                + rule.code()
                + " lookup_code,p."
                + rule.label()
                + " lookup_label FROM "
                + rule.table()
                + " p";
    }

    /**
     * 解析ID时只使用Java整数，不接受科学计数法或越界值。
     *
     * @param rule 当前键类型
     * @param value 未经浮点转换的输入
     * @return JDBC绑定值
     */
    private Object key(Rule rule, String value) {
        if (value == null || value.length() > 500)
            throw new DatasetAccessException("INVALID_PARAMETER", "候选值无效");
        if (rule.keyType() == ValueType.STRING) return value;
        if (!validId(value)) throw new DatasetAccessException("INVALID_PARAMETER", "候选ID无效");
        return Long.valueOf(value);
    }

    /**
     * 检查合法正数ID，包括有符号64位整数上界。
     *
     * @param value 原始文本
     * @return 是否可无损绑定为ID
     */
    private boolean validId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return false;
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * 组装人可识别的编码和名称，值始终使用字符串。
     *
     * @param value 完整候选值
     * @param code 业务编码
     * @param label 业务名称
     * @return 安全展示候选
     */
    private Option option(String value, String code, String label) {
        String name = Objects.toString(label, "");
        String displayCode = Objects.toString(code, "");
        return new Option(value, name.isBlank() ? displayCode : displayCode + " · " + name);
    }
}
