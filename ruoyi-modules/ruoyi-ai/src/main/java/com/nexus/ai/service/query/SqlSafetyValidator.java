package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 只读安全校验器（公共，ExecuteSqlQueryTool 与 flow 的 sql 节点复用）
 * <p>安全链路：SELECT 白名单 + 注释清洗 + 危险关键词黑名单 + 表名白名单（前缀/动态查库）+ 租户隔离注入 + LIMIT 强加 + EXPLAIN 语法预校验</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlSafetyValidator {

    /** ERP 查询服务（执行只读 SQL，@DS("erp") 数据源；表存在性动态校验） */
    private final ErpQueryService erpQueryService;
    /** AI 配置属性（表前缀白名单 + 行数/超时限制） */
    private final AiProperties aiProperties;

    /** 禁止出现的 SQL 关键词正则（单词边界避免子串误匹配，如 UPDATE_TIME 不会命中 UPDATE） */
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

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * 校验并净化 SQL（只读 SELECT）
     * <p>通过则返回清洗注释 + 注入租户条件 + 追加行数上限后的可执行 SQL；
     * 不通过抛 {@link IllegalArgumentException}（错误信息中文可读）</p>
     *
     * @param sql     原始 SQL
     * @param maxRows 行数上限（null 时取配置 nexus.ai.sql-max-rows）
     * @return 可安全执行的 SQL
     */
    public String validateAndSanitize(String sql, Integer maxRows) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 0. 移除 SQL 注释（防止通过注释绕过关键词检测）
        String cleanedSql = sql.replaceAll("/\\*.*?\\*/", " ");   // 块注释 /*...*/
        cleanedSql = cleanedSql.replaceAll("--[^\\n]*", " ");      // 单行注释 --
        cleanedSql = cleanedSql.replaceAll("#[^\\n]*", " ");      // 单行注释 #
        String cleanedUpperSql = cleanedSql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!cleanedUpperSql.startsWith("SELECT")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询语句");
        }

        // 2. 禁止危险关键词（单词边界匹配，避免列名 UPDATE_TIME 误触发 UPDATE）
        Matcher forbiddenMatcher = FORBIDDEN_KEYWORD_PATTERN.matcher(cleanedUpperSql);
        if (forbiddenMatcher.find()) {
            String matched = cleanedUpperSql.substring(
                Math.max(0, forbiddenMatcher.start() - 10),
                Math.min(cleanedUpperSql.length(), forbiddenMatcher.end() + 10)).trim();
            log.warn("[AI-SQL] 包含禁止关键词: {}", matched);
            throw new IllegalArgumentException("SQL 包含禁止关键词: " + forbiddenMatcher.group());
        }
        // 禁止 UNION SELECT（允许 UNION ALL 用于 CTE 递归）
        if (FORBIDDEN_UNION_SELECT.matcher(cleanedUpperSql).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的 UNION SELECT");
        }

        // 3. 表名白名单校验（动态查库，确保与实际 ERP 表一致）
        Matcher matcher = TABLE_NAME_PATTERN.matcher(cleanedSql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!isTableAllowed(tableName)) {
                log.warn("[AI-SQL] 表名 '{}' 不在白名单中", tableName);
                throw new IllegalArgumentException("表 '" + tableName + "' 不存在或不在允许列表中，可用表: "
                    + getAvailableTablesHint());
            }
        }

        // 4. 注入租户隔离条件（从 AiRequestContext 获取，兼容异步线程）
        Long tenantId = AiRequestContext.getTenantId();
        if (tenantId != null) {
            cleanedSql = injectTenantCondition(cleanedSql, tenantId);
        } else {
            log.warn("[AI-SQL] 无法获取租户ID，跳过租户隔离");
        }

        // 5. 追加行数限制（移除已有的 LIMIT，强制使用安全限制）
        cleanedSql = cleanedSql.replaceAll("(?i)\\s+LIMIT\\s+\\d+(?:\\s*,\\s*\\d+)?\\s*;?\\s*$", "");
        int limit = maxRows == null || maxRows <= 0 ? aiProperties.getSqlMaxRows() : maxRows;
        cleanedSql = cleanedSql + " LIMIT " + limit;

        // 6. SQL 语法预校验（通过 EXPLAIN 验证，避免执行语法错误的 SQL）
        String explainError = validateSqlSyntax(cleanedSql);
        if (explainError != null) {
            throw new IllegalArgumentException("SQL 语法错误：" + explainError);
        }
        return cleanedSql;
    }

    /**
     * 检查表名是否在白名单中
     * <p>优先前缀匹配（配置项），失败则动态查库验证（结果缓存）</p>
     *
     * @param tableName 表名
     * @return 是否允许
     */
    private boolean isTableAllowed(String tableName) {
        boolean prefixMatch = aiProperties.getAllowedTablePrefixes().stream()
            .anyMatch(tableName::startsWith);
        if (prefixMatch) {
            return true;
        }
        try {
            return erpQueryService.tableExists(tableName);
        } catch (Exception e) {
            log.warn("[AI-SQL] 动态查库验证失败，回退到前缀匹配: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用表名提示（前 30 张表，帮助选择正确的表）
     *
     * @return 可用表名提示
     */
    private String getAvailableTablesHint() {
        try {
            List<String> tables = erpQueryService.queryErpTables();
            if (tables.isEmpty()) {
                return "ERP 库中无可用表";
            }
            int limit = Math.min(tables.size(), 30);
            return String.join(", ", tables.subList(0, limit));
        } catch (Exception e) {
            return "请调用查询全部表接口获取可用表";
        }
    }

    /**
     * 注入租户隔离条件
     * <p>从 SQL 提取主表别名（如 FROM wm_material_stock s），使用 "s.tenant_id = X" 避免多表 JOIN 列歧义</p>
     *
     * @param sql      原始 SQL（已清洗注释）
     * @param tenantId 租户ID
     * @return 注入租户条件后的 SQL
     */
    private String injectTenantCondition(String sql, Long tenantId) {
        if (sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
        String tenantCol = extractOuterTenantColumn(sql);
        String tenantCondition = tenantCol + " = " + tenantId;
        String upperSql = sql.toUpperCase();

        if (upperSql.contains("WHERE")) {
            int whereIndex = upperSql.indexOf("WHERE");
            sql = sql.substring(0, whereIndex + 5) +
                  " " + tenantCondition + " AND" +
                  sql.substring(whereIndex + 5);
        } else {
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
     * 提取最外层 FROM 子句的表名/别名（跳过括号内子查询/CTE）
     *
     * @param sql SQL
     * @return 租户列名（如 s.tenant_id）
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
     * SQL 语法预校验（EXPLAIN）
     *
     * @param sql 待校验 SQL
     * @return 错误信息，null 表示通过
     */
    private String validateSqlSyntax(String sql) {
        try {
            erpQueryService.executeQuery("EXPLAIN " + sql, 1, 2);
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return msg;
        }
    }
}
