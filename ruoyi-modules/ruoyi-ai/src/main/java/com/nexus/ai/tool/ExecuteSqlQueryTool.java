package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行 SQL 查询工具
 * <p>
 * 供 AI Agent 调用，在 ERP 数据源上执行只读 SELECT 查询。
 * 包含完整安全校验：SELECT 白名单 + 危险关键词黑名单 + 表名白名单 + 租户隔离。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteSqlQueryTool {

    private final ErpQueryService erpQueryService;
    private final AiProperties aiProperties;

    /** 禁止出现的 SQL 关键词（大小写不敏感） */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "LOAD DATA",
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "GRANT", "REVOKE", "EXECUTE", "INFORMATION_SCHEMA",
        "SLEEP", "BENCHMARK", "WAITFOR", "UNION"
    );

    /** 表名提取正则（匹配 FROM/JOIN 后的表名） */
    private static final Pattern TABLE_NAME_PATTERN =
        Pattern.compile("(?:FROM|JOIN)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?", Pattern.CASE_INSENSITIVE);

    /**
     * 执行 SELECT SQL 查询
     *
     * @param sql SQL 查询语句
     * @return 查询结果（Markdown 表格格式）
     */
    @Tool("Execute a SELECT SQL query on ERP database and return the results")
    public String executeSqlQuery(@P("SQL SELECT query statement") String sql) {
        log.info("[AI-SQL] 收到查询请求: {}", sql);

        if (sql == null || sql.trim().isEmpty()) {
            log.warn("[AI-SQL] SQL 参数为空");
            return "Error: SQL query cannot be empty";
        }

        String upperSql = sql.trim().toUpperCase();

        // 0. 移除 SQL 注释（防止通过注释绕过关键词检测）
        String cleanedSql = sql.replaceAll("/\\*.*?\\*/", " ");  // 块注释 /*...*/
        String cleanedUpperSql = cleanedSql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!cleanedUpperSql.startsWith("SELECT")) {
            log.warn("[AI-SQL] 非 SELECT 语句被拒绝: {}", sql);
            return "Error: Only SELECT queries are allowed";
        }

        // 2. 禁止危险关键词（在清理注释后的 SQL 上检测）
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (cleanedUpperSql.contains(keyword)) {
                log.warn("[AI-SQL] 包含禁止关键词 '{}': {}", keyword, sql);
                return "Error: SQL contains forbidden keyword: " + keyword;
            }
        }

        // 3. 表名白名单校验
        Matcher matcher = TABLE_NAME_PATTERN.matcher(cleanedSql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            boolean allowed = aiProperties.getAllowedTablePrefixes().stream()
                .anyMatch(tableName::startsWith);
            if (!allowed) {
                log.warn("[AI-SQL] 表名 '{}' 不在白名单中", tableName);
                return "Error: Table '" + tableName + "' is not allowed. Only ERP business tables are permitted.";
            }
        }

        // 4. 注入租户隔离条件（从 AiRequestContext 获取，兼容异步线程）
        Long tenantId = AiRequestContext.getTenantId();
        if (tenantId != null) {
            sql = injectTenantCondition(sql, tenantId);
            log.debug("[AI-SQL] 已注入租户条件 tenant_id={}", tenantId);
        } else {
            log.warn("[AI-SQL] 无法获取租户ID，跳过租户隔离");
        }

        // 5. 追加行数限制（移除已有的 LIMIT，强制使用安全限制）
        // 移除用户指定的 LIMIT（防止绕过行数限制）
        sql = sql.replaceAll("(?i)\\s+LIMIT\\s+\\d+(?:\\s*,\\s*\\d+)?\\s*$", "");
        sql = sql + " LIMIT " + aiProperties.getSqlMaxRows();

        // 6. 通过 ErpQueryService 执行（走 @DS("erp") 数据源）
        try {
            log.info("[AI-SQL] 执行最终SQL: {}", sql);
            List<Map<String, Object>> results = erpQueryService.executeQuery(
                sql, aiProperties.getSqlMaxRows(), aiProperties.getSqlTimeout() / 1000);
            log.info("[AI-SQL] 查询成功，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-SQL] 执行 SQL 查询失败: {}", sql, e);
            return "Error: Query execution failed - " + e.getMessage();
        }
    }

    /**
     * 注入租户隔离条件
     * <p>
     * 从 SQL 中提取主表别名（如 FROM wm_material_stock s），
     * 使用 "s.tenant_id = X" 避免多表 JOIN 时的列歧义错误。
     * 无别名时使用 "表名.tenant_id"。
     * </p>
     *
     * @param sql      原始 SQL
     * @param tenantId 租户ID
     * @return 注入租户条件后的 SQL
     */
    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    private String injectTenantCondition(String sql, Long tenantId) {
        if (sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
        // 提取主表名/别名：FROM table_name [AS] alias
        Pattern fromPattern = Pattern.compile(
            "FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)?",
            Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(sql);
        String tenantCol;
        if (fromMatcher.find()) {
            if (fromMatcher.group(2) != null) {
                tenantCol = fromMatcher.group(2) + ".tenant_id";
            } else {
                tenantCol = fromMatcher.group(1) + ".tenant_id";
            }
        } else {
            tenantCol = "tenant_id";
        }

        String tenantCondition = tenantCol + " = " + tenantId;
        String upperSql = sql.toUpperCase();

        if (upperSql.contains("WHERE")) {
            // 已有 WHERE，在其后追加 AND 条件
            int whereIndex = upperSql.indexOf("WHERE");
            sql = sql.substring(0, whereIndex + 5) +
                  " " + tenantCondition + " AND" +
                  sql.substring(whereIndex + 5);
        } else {
            // 没有 WHERE，需要在 ORDER BY / GROUP BY / HAVING / LIMIT 之前插入
            Matcher clauseMatcher = CLAUSE_PATTERN.matcher(sql);
            if (clauseMatcher.find()) {
                int insertPos = clauseMatcher.start();
                sql = sql.substring(0, insertPos) +
                      " WHERE " + tenantCondition + " " +
                      sql.substring(insertPos);
            } else {
                sql = sql + " WHERE " + tenantCondition;
            }
        }
        return sql;
    }

    /**
     * 格式化查询结果为 Markdown 表格
     *
     * @param results 查询结果列表
     * @return Markdown 表格字符串
     */
    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "Query executed successfully, but no results returned.";
        }

        StringBuilder result = new StringBuilder();
        int displayRows = Math.min(results.size(), 10);

        List<String> columnNames = List.copyOf(results.get(0).keySet());
        int displayCols = Math.min(columnNames.size(), 8);
        List<String> displayColumns = columnNames.subList(0, displayCols);

        // 表头
        result.append("| ");
        for (String col : displayColumns) {
            result.append(col).append(" | ");
        }
        result.append("\n|");
        for (int i = 0; i < displayCols; i++) {
            result.append(" --- |");
        }
        result.append("\n");

        // 数据行
        for (int i = 0; i < displayRows; i++) {
            result.append("| ");
            Map<String, Object> row = results.get(i);
            for (String column : displayColumns) {
                Object value = row.get(column);
                String displayValue = (value == null) ? "-" : value.toString();
                if (displayValue.length() > 20) {
                    displayValue = displayValue.substring(0, 17) + "...";
                }
                result.append(displayValue).append(" | ");
            }
            result.append("\n");
        }

        result.append("\nTotal: ").append(results.size()).append(" rows");
        if (displayRows < results.size()) {
            result.append(" (displayed ").append(displayRows).append(" rows)");
        }
        return result.toString();
    }
}
