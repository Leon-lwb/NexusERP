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

    /** ERP 查询服务（执行只读 SQL，@DS("erp") 数据源） */
    private final ErpQueryService erpQueryService;
    /** AI 配置属性，提供 SQL 行数/超时限制与表前缀白名单 */
    private final AiProperties aiProperties;

    /** 禁止出现的 SQL 关键词正则（使用单词边界避免子串误匹配，如 UPDATE_TIME 不会命中 UPDATE） */
    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
        "\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD_FILE|LOAD\\s+DATA|" +
        "INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|" +
        "GRANT|REVOKE|EXECUTE|INFORMATION_SCHEMA|" +
        "SLEEP|BENCHMARK|WAITFOR)\\b",
        Pattern.CASE_INSENSITIVE);

    /** 禁止 UNION SELECT（允许 UNION ALL 用于 CTE 递归查询） */
    private static final Pattern FORBIDDEN_UNION_SELECT = Pattern.compile(
        "\\bUNION\\s+SELECT\\b", Pattern.CASE_INSENSITIVE);

    /** 表名提取正则（匹配 FROM/JOIN 后的表名） */
    private static final Pattern TABLE_NAME_PATTERN =
        Pattern.compile("(?:FROM|JOIN)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?", Pattern.CASE_INSENSITIVE);

    /**
     * 执行 ERP 数据库查询（只读）
     * <p>包含完整安全校验：SELECT 白名单 + 危险关键词黑名单 + 表名白名单 + 租户隔离 + SQL 语法预校验</p>
     *
     * @param sql SQL 查询语句
     * @return 查询结果（Markdown 表格格式）
     */
    @Tool("执行 ERP 数据库查询（只读），返回查询结果。SQL 语法错误时会返回错误信息，请根据错误修正后重试")
    public String executeSqlQuery(@P("SQL SELECT 查询语句") String sql) {
        log.info("[AI-SQL] 收到查询请求: {}", sql);

        if (sql == null || sql.trim().isEmpty()) {
            log.warn("[AI-SQL] SQL 参数为空");
            return "Error: SQL query cannot be empty";
        }

        String upperSql = sql.trim().toUpperCase();

        // 0. 移除 SQL 注释（防止通过注释绕过关键词检测）
        String cleanedSql = sql.replaceAll("/\\*.*?\\*/", " ");           // 块注释 /*...*/
        cleanedSql = cleanedSql.replaceAll("--[^\\n]*", " ");              // 单行注释 --
        cleanedSql = cleanedSql.replaceAll("#[^\\n]*", " ");              // 单行注释 #
        String cleanedUpperSql = cleanedSql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!cleanedUpperSql.startsWith("SELECT")) {
            log.warn("[AI-SQL] 非 SELECT 语句被拒绝: {}", sql);
            return "Error: Only SELECT queries are allowed";
        }

        // 2. 禁止危险关键词（单词边界匹配，避免列名 UPDATE_TIME 误触发 UPDATE）
        Matcher forbiddenMatcher = FORBIDDEN_KEYWORD_PATTERN.matcher(cleanedUpperSql);
        if (forbiddenMatcher.find()) {
            String matched = cleanedUpperSql.substring(
                Math.max(0, forbiddenMatcher.start() - 10),
                Math.min(cleanedUpperSql.length(), forbiddenMatcher.end() + 10)).trim();
            log.warn("[AI-SQL] 包含禁止关键词: {}", matched);
            return "Error: SQL contains forbidden keyword: " + forbiddenMatcher.group();
        }
        // 禁止 UNION SELECT（允许 UNION ALL 用于 CTE 递归）
        if (FORBIDDEN_UNION_SELECT.matcher(cleanedUpperSql).find()) {
            log.warn("[AI-SQL] 包含禁止的 UNION SELECT: {}", sql);
            return "Error: SQL contains forbidden keyword: UNION SELECT";
        }

        // 3. 表名白名单校验（动态查库，确保与实际 ERP 表一致）
        Matcher matcher = TABLE_NAME_PATTERN.matcher(cleanedSql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!isTableAllowed(tableName)) {
                log.warn("[AI-SQL] 表名 '{}' 不在白名单中", tableName);
                return "Error: Table '" + tableName + "' does not exist or is not allowed. "
                    + getAvailableTablesHint()
                    + ". Please use an exact table name from this list.";
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
        // 移除用户指定的 LIMIT（防止绕过行数限制），兼容末尾有分号的情况
        sql = sql.replaceAll("(?i)\\s+LIMIT\\s+\\d+(?:\\s*,\\s*\\d+)?\\s*;?\\s*$", "");
        sql = sql + " LIMIT " + aiProperties.getSqlMaxRows();

        // 6. SQL 语法预校验（通过 EXPLAIN 验证，避免执行语法错误的 SQL）
        String explainError = validateSqlSyntax(sql);
        if (explainError != null) {
            log.warn("[AI-SQL] SQL 语法校验失败: {}", explainError);
            return "SQL 语法错误：" + explainError + "。请修正 SQL 后重试。原始 SQL：" + sql;
        }

        // 7. 通过 ErpQueryService 执行（走 @DS("erp") 数据源）
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
     * 检查表名是否在白名单中
     * <p>优先使用前缀匹配（配置项），如果前缀匹配失败则动态查库验证</p>
     * <p>动态查库结果按表名缓存，避免每次查询都访问数据库</p>
     *
     * @param tableName 表名
     * @return true 如果表名在白名单中
     */
    private boolean isTableAllowed(String tableName) {
        // 1. 前缀匹配（快速路径）
        boolean prefixMatch = aiProperties.getAllowedTablePrefixes().stream()
            .anyMatch(tableName::startsWith);
        if (prefixMatch) {
            return true;
        }

        // 2. 动态查库验证（覆盖不在前缀白名单中但实际存在的 ERP 表）
        try {
            return erpQueryService.tableExists(tableName);
        } catch (Exception e) {
            log.warn("[AI-SQL] 动态查库验证失败，回退到前缀匹配: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用表名提示（包含前几条表名，帮助 Agent 直接选择正确的表）
     *
     * @return 包含可用表名列表的提示字符串
     */
    private String getAvailableTablesHint() {
        try {
            List<String> tables = erpQueryService.queryErpTables();
            if (tables.isEmpty()) {
                return "No ERP tables found in the database.";
            }
            StringBuilder hint = new StringBuilder("Available tables: ");
            int limit = Math.min(tables.size(), 30);
            hint.append(String.join(", ", tables.subList(0, limit)));
            if (tables.size() > limit) {
                hint.append(", ... (and ").append(tables.size() - limit).append(" more)");
            }
            return hint.toString();
        } catch (Exception e) {
            return "Please call queryAllTables to see available tables.";
        }
    }

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

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
    private String injectTenantCondition(String sql, Long tenantId) {
        if (sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
        // 提取主表名/别名：查找最外层（不在括号内）的 FROM 子句
        String tenantCol = extractOuterTenantColumn(sql);

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
     * 提取最外层 FROM 子句的表名/别名，用于构建租户隔离列名
     * <p>跳过括号内的 FROM（子查询/CTE），只匹配最外层的主表</p>
     */
    private String extractOuterTenantColumn(String sql) {
        Pattern fromPattern = Pattern.compile(
            "FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)?",
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = fromPattern.matcher(sql);
        while (matcher.find()) {
            if (!isInsideParentheses(sql, matcher.start())) {
                if (matcher.group(2) != null) {
                    return matcher.group(2) + ".tenant_id";
                }
                return matcher.group(1) + ".tenant_id";
            }
        }
        return "tenant_id";
    }

    /**
     * 判断给定位置是否在括号内（子查询/CTE 内）
     */
    private boolean isInsideParentheses(String sql, int position) {
        int depth = 0;
        for (int i = 0; i < position; i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        return depth > 0;
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

    /**
     * SQL 语法预校验（通过 EXPLAIN 验证）
     * <p>在执行前先 EXPLAIN 验证语法，语法错误时返回错误信息，帮助 LLM 自动修正</p>
     *
     * @param sql 待校验的 SQL
     * @return 错误信息，null 表示校验通过
     */
    private String validateSqlSyntax(String sql) {
        try {
            erpQueryService.executeQuery("EXPLAIN " + sql, 1, 2);
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            // 提取关键错误信息（去除堆栈噪音）
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return msg;
        }
    }
}
