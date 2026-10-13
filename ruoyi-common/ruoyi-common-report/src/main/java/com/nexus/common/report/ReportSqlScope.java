package com.nexus.common.report;
import java.util.*;
import java.util.stream.Collectors;
/** JDBC does not use MyBatis tenant interceptors. Every provider binds these predicates. */
public final class ReportSqlScope {
    private ReportSqlScope() { }
    public record Clause(String sql, List<Object> parameters) { public Clause { parameters = List.copyOf(parameters); } }
    public static Clause where(String alias, ReportExecutionContext context) {
        if (!alias.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Internal SQL alias required");
        StringBuilder sql = new StringBuilder(alias + ".tenant_id = ? AND " + alias + ".del_flag = '0'");
        List<Object> params = new ArrayList<>(); params.add(context.tenantId());
        for (ReportScope scope : context.scopes()) {
            if (scope.userId() != context.actor().userId()) throw new IllegalArgumentException("Scope actor mismatch");
            if (scope.all()) continue;
            List<String> predicates = new ArrayList<>();
            if (!scope.departments().isEmpty()) {
                predicates.add(alias + ".create_dept IN (" + scope.departments().stream().map(id -> "?").collect(Collectors.joining(",")) + ")");
                params.addAll(scope.departments());
            }
            if (scope.self()) { predicates.add(alias + ".create_by = ?"); params.add(scope.userId()); }
            sql.append(" AND (").append(predicates.isEmpty() ? "1 = 0" : String.join(" OR ", predicates)).append(")");
        }
        return new Clause(sql.toString(), params);
    }
}
