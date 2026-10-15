package com.nexus.common.report;

import java.util.*;
import java.util.stream.Collectors;

/** JDBC 不经过 MyBatis 租户拦截器，每个提供者必须显式绑定这些范围条件。 */
public final class ReportSqlScope {
    /** 初始化报表查询条件范围并保存所需依赖或不可变值。 */
    private ReportSqlScope() {}

    /**
     * Clause，负责当前模块对应的契约或处理职责。
     *
     * @param sql 当前契约的sql配置或状态
     * @param parameters 固定参数契约
     */
    public record Clause(String sql, List<Object> parameters) {

        /**
         * 初始化Clause并保存所需依赖或不可变值。
         *
         * @param sql 代码生成的查询语句
         * @param parameters 按契约解析的输入参数
         */
        public Clause {
            parameters = List.copyOf(parameters);
        }
    }

    /**
     * 生成当前租户及业务范围条件。
     *
     * @param alias 当前操作的 {@code alias} 输入
     * @param context 服务端执行上下文
     * @return 当前处理返回的固定结果
     */
    public static Clause where(String alias, ReportExecutionContext context) {
        if (!alias.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Internal SQL alias required");
        StringBuilder sql =
                new StringBuilder(alias + ".tenant_id = ? AND " + alias + ".del_flag = '0'");
        List<Object> params = new ArrayList<>();
        params.add(context.tenantId());
        for (ReportScope scope : context.scopes()) {
            if (scope.userId() != context.actor().userId())
                throw new IllegalArgumentException("Scope actor mismatch");
            if (scope.all()) continue;
            List<String> predicates = new ArrayList<>();
            if (!scope.departments().isEmpty()) {
                predicates.add(
                        alias
                                + ".create_dept IN ("
                                + scope.departments().stream()
                                        .map(id -> "?")
                                        .collect(Collectors.joining(","))
                                + ")");
                params.addAll(scope.departments());
            }
            if (scope.self()) {
                predicates.add(alias + ".create_by = ?");
                params.add(scope.userId());
            }
            sql.append(" AND (")
                    .append(predicates.isEmpty() ? "1 = 0" : String.join(" OR ", predicates))
                    .append(")");
        }
        return new Clause(sql.toString(), params);
    }
}
