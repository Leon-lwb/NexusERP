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

    /** 禁止 UNION（含 UNION ALL：多分支 SELECT 时租户条件只注入第一分支，第二分支零隔离 → 跨租户读取） */
    private static final Pattern FORBIDDEN_UNION = Pattern.compile(
        "\\bUNION\\b", Pattern.CASE_INSENSITIVE);

    /** 表名提取正则（匹配 FROM/JOIN 后的表名） */
    private static final Pattern TABLE_NAME_PATTERN =
        Pattern.compile("(?:FROM|JOIN)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?", Pattern.CASE_INSENSITIVE);

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /** 子句关键字（FROM 别名提取时排除，防止无别名表把 WHERE 当别名吞掉） */
    private static final java.util.Set<String> CLAUSE_KEYWORDS = java.util.Set.of(
        "WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "JOIN", "UNION", "ON", "AND", "OR", "SET", "VALUES");

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
        // 无显式租户（AI 对话链路）：从 AiRequestContext 获取（对话线程 ThreadLocal 可达）
        return validateAndSanitize(sql, maxRows, AiRequestContext.getTenantId());
    }

    /**
     * SQL 安全校验（带显式租户）
     * <p>流程 sql 节点在 ForkJoinPool 线程执行（AiRequestContext ThreadLocal 不可达），
     * 必须由调用方经执行上下文传入租户，否则租户隔离注入失效（跨租户数据泄露风险）</p>
     *
     * @param sql     原始 SQL
     * @param maxRows 行数上限（null 时取配置 nexus.ai.sql-max-rows）
     * @param tenantId 显式租户（null 时回退 AiRequestContext）
     * @return 可安全执行的 SQL
     */
    public String validateAndSanitize(String sql, Integer maxRows, Long tenantId) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 0. 移除 SQL 注释（防止通过注释绕过关键词检测）
        // 块注释开启 DOTALL：跨行 /* \n TENANT_ID \n */ 也必须被清洗，否则藏匿的关键词
        // 会让后续租户注入判断失效
        String cleanedSql = sql.replaceAll("(?s)/\\*.*?\\*/", " ");  // 块注释 /*...*/（含跨行）
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
        // 禁止 UNION（含 UNION ALL，多分支零租户隔离风险）
        if (FORBIDDEN_UNION.matcher(cleanedUpperSql).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的 UNION，请拆分为多条简单查询");
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

        // 4. 注入租户隔离条件（显式租户优先，回退 AiRequestContext）
        // 安全：先重写用户书写的 tenant_id 等值条件为服务端租户（防伪造租户值），
        // 再无条件追加服务端条件兜底
        Long effectiveTenantId = tenantId != null ? tenantId : AiRequestContext.getTenantId();
        if (effectiveTenantId != null) {
            cleanedSql = rewriteTenantValues(cleanedSql, effectiveTenantId);
            cleanedSql = injectTenantCondition(cleanedSql, effectiveTenantId);
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
     * 用户书写的 tenant_id 等值条件（含别名前缀）：{@code [别名.]tenant_id = '值'|数字}。
     * <p>用于把用户/LLM 提供的租户值强制重写为服务端租户值（结构保持不变）。
     * 别名前缀可选：裸 {@code tenant_id = 'x'} 与 {@code t.tenant_id = 'x'} 均命中；
     * 列引用（SELECT tenant_id）无 = 条件不命中。</p>
     */
    private static final Pattern USER_TENANT_CONDITION = Pattern.compile(
        "(?i)((?:[a-zA-Z_][a-zA-Z0-9_]*\\.)?tenant_id\\s*=\\s*)('[^']*'|[0-9]+)");

    /**
     * 注入租户隔离条件
     * <p>从 SQL 提取主表别名（如 FROM wm_material_stock s），使用 "s.tenant_id = X" 避免多表 JOIN 列歧义</p>
     * <p>安全：调用方须先经 {@link #rewriteTenantValues} 把用户条件值重写为服务端租户，
     * 本方法无条件追加服务端条件兜底（原"包含 TENANT_ID 字样即跳过"可被 SELECT tenant_id FROM ... 绕过）</p>
     *
     * @param sql      原始 SQL（已清洗注释）
     * @param tenantId 租户ID
     * @return 注入租户条件后的 SQL
     */
    private String injectTenantCondition(String sql, Long tenantId) {
        String tenantCol = extractOuterTenantColumn(sql);
        // 租户值按 RuoYi 6 位字符串格式注入（Long.valueOf("000000")=0 会丢失前导零，
        // 且数字字面量依赖 MySQL 隐式转换才能匹配 varchar 列——统一字符串+引号）
        String tenantValue = String.format("%06d", tenantId);
        String tenantCondition = tenantCol + " = '" + tenantValue + "'";
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
     * <p>别名组排除子句关键字（无别名表 + WHERE 时，原正则把 WHERE 当别名吞掉 → WHERE.tenant_id）</p>
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
                String alias = matcher.group(2);
                // 别名不得是 SQL 子句关键字（无别名表时 group(2) 会吞掉 WHERE/GROUP/ORDER 等）
                if (alias != null && CLAUSE_KEYWORDS.contains(alias.toUpperCase())) {
                    alias = null;
                }
                if (alias != null) {
                    return alias + ".tenant_id";
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
     * 把 SQL 中用户书写的 tenant_id 等值条件的值强制重写为服务端租户值。
     * <p>不剥离条件结构（避免破坏 WHERE ... AND ... 语法）：{@code WHERE tenant_id='999999'}
     * → {@code WHERE tenant_id=<服务端租户>}。等值条件被重写后，再由
     * {@link #injectTenantCondition} 无条件追加服务端条件兜底（拦截 !=/IN 等绕过变体）。</p>
     */
    private String rewriteTenantValues(String sql, Long tenantId) {
        return USER_TENANT_CONDITION.matcher(sql).replaceAll("$1" + tenantId);
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
