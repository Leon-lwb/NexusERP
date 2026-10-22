package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ERP 工具公共基类
 * <p>
 * 提取所有业务 Tool 的公共逻辑，消除重复代码：
 * 1. injectTenantCondition() - 注入租户隔离条件
 * 2. formatResults() - 格式化查询结果为 Markdown 表格
 * 3. executeAndFormat() - 统一执行查询并格式化（含异常处理）
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ErpToolHelper {

    /** ERP 查询服务（@DS("erp") 数据源） */
    protected final ErpQueryService erpQueryService;

    /** 租户ID按系统 varchar(20) 标识符处理，禁止引号、反斜杠和控制符进入SQL字面量。 */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,20}");

    /** 仅识别工具预置的租户参数比较；表间租户相等不代表已限定当前请求租户。 */
    private static final Pattern PARAMETERIZED_TENANT_PATTERN = Pattern.compile(
        "\\btenant_id\\s*=\\s*\\?", Pattern.CASE_INSENSITIVE);

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /** 子句关键字（FROM 别名提取时排除，防止无别名表把 WHERE/ORDER 等当别名吞掉 → 注入出 WHERE.tenant_id 语法错误） */
    private static final Set<String> CLAUSE_KEYWORDS =
            Set.of(
                    "WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "JOIN", "UNION", "ON", "AND",
                    "OR", "SET", "VALUES");

    /**
     * 注入租户隔离条件
     * <p>
     * 从 SQL 中提取主查询的表别名（如 FROM wm_material_stock s），
     * 使用 "s.tenant_id = X" 避免多表 JOIN 时的列歧义错误。
     * 无别名时使用 "表名.tenant_id"。
     * </p>
     * <p>
     * 对于 CTE（WITH ... AS）开头的 SQL，会跳过 CTE 定义部分，
     * 从主查询的 FROM 处提取表别名并注入条件。
     * </p>
     * <p>
     * 注意：本类服务于硬编码 SQL 的业务工具（SQL 文本不含用户输入的 tenant_id 条件），
     * 因此仅对工具自身预置的 tenant_id 参数化条件（如tx.tenant_id = ?）跳过重复注入。
     * JOIN中的两表tenant_id相等不算请求级隔离，仍须注入精确请求租户。
     * 用户可控 SQL 必须走 SqlSafetyValidator（剥离用户条件后强制重写）。
     * </p>
     *
     * @param sql      原始 SQL
     * @param tenantId 精确租户字符串；缺失或非法时拒绝查询
     * @return 注入租户条件后的 SQL
     */
    protected String injectTenantCondition(String sql, String tenantId) {
        requireTenantId(tenantId);
        if (PARAMETERIZED_TENANT_PATTERN.matcher(sql).find()) {
            return sql;
        }
        // 跳过 CTE 定义部分，定位主查询的 FROM
        // CTE 格式：WITH name AS (...), name2 AS (...) SELECT ...
        // 主查询的 FROM 在最后一个 CTE 定义之后
        String mainQuery = sql;
        String ctePrefix = "";
        String upperSql = sql.toUpperCase().trim();
        if (upperSql.startsWith("WITH ")) {
            // 找到最后一个 ") SELECT" 或 ") \nSELECT" 的位置
            int lastCteEnd = findMainQueryStart(sql);
            if (lastCteEnd > 0) {
                ctePrefix = sql.substring(0, lastCteEnd);
                mainQuery = sql.substring(lastCteEnd);
            }
        }

        Pattern fromPattern = Pattern.compile(
            "FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)?",
            Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(mainQuery);
        String tenantCol;
        if (fromMatcher.find()) {
            String alias = fromMatcher.group(2);
            // 别名不得是 SQL 子句关键字（无别名表 + WHERE/ORDER 时，正则会把子句关键字当别名吞掉）
            if (alias != null && CLAUSE_KEYWORDS.contains(alias.toUpperCase())) {
                alias = null;
            }
            if (alias != null) {
                tenantCol = alias + ".tenant_id";
            } else {
                tenantCol = fromMatcher.group(1) + ".tenant_id";
            }
        } else {
            tenantCol = "tenant_id";
        }

        // 严格校验标识符后以字符串字面量比较，保留000000、前导零和字母租户；
        // 不使用数值比较，不补零，也不把旧0数据自动归属到000000。
        String tenantCondition = tenantCol + " = '" + tenantId + "'";
        String upperMain = mainQuery.toUpperCase();

        String modifiedMain;
        if (upperMain.contains("WHERE")) {
            int whereIndex = upperMain.indexOf("WHERE");
            modifiedMain = mainQuery.substring(0, whereIndex + 5) +
                  " " + tenantCondition + " AND" +
                  mainQuery.substring(whereIndex + 5);
        } else {
            Matcher clauseMatcher = CLAUSE_PATTERN.matcher(mainQuery);
            if (clauseMatcher.find()) {
                int insertPos = clauseMatcher.start();
                modifiedMain = mainQuery.substring(0, insertPos) +
                      " WHERE " + tenantCondition + " " +
                      mainQuery.substring(insertPos);
            } else {
                modifiedMain = mainQuery + " WHERE " + tenantCondition;
            }
        }

        return ctePrefix + modifiedMain;
    }

    /**
     * 找到 CTE 定义结束后主查询的起始位置
     * <p>处理 WITH name AS (...), name2 AS (...) SELECT ... 格式，
     * 需要正确匹配括号以找到最后一个 CTE 定义的结束位置</p>
     */
    private int findMainQueryStart(String sql) {
        int depth = 0;
        boolean inCte = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
                inCte = true;
            } else if (c == ')') {
                depth--;
                if (inCte && depth == 0) {
                    // 找到 CTE 定义的结束括号，检查后面是否跟着 SELECT
                    String rest = sql.substring(i + 1).trim().toUpperCase();
                    if (rest.startsWith("SELECT") || rest.startsWith(",") && rest.indexOf("SELECT") > 0) {
                        // 如果后面是逗号，说明还有更多 CTE 定义
                        if (rest.startsWith(",")) {
                            continue;
                        }
                        // 后面是 SELECT，说明主查询从这里开始
                        return i + 1;
                    }
                }
            }
        }
        // 兜底：如果找不到，返回 0（从头开始解析）
        return 0;
    }

    /**
     * 从 AiRequestContext 获取租户ID并注入条件（便捷方法）
     */
    protected String injectTenantCondition(String sql) {
        return injectTenantCondition(sql, currentTenantId());
    }

    /**
     * 获取必需的请求租户，兼容旧Long基线与主线程迁移后的String上下文。
     * 只做原值转字符串，不擅自补回已经丢失的前导零，不把null转为字面量null。
     * 参数化SQL也必须通过此入口，缺失上下文不能查询全库或绑定空租户。
     */
    protected String currentTenantId() {
        Object tenantId = AiRequestContext.getTenantId();
        return requireTenantId(tenantId == null ? null : tenantId.toString());
    }

    private String requireTenantId(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("租户上下文缺失或租户标识无效，拒绝查询");
        }
        return tenantId;
    }

    /**
     * 格式化查询结果为 Markdown 表格
     * <p>最多显示 8 列 × 10 行，避免超出模型上下文限制</p>
     *
     * @param results 查询结果列表
     * @return Markdown 表格字符串
     */
    protected String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "查询成功，但未返回数据。";
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

        result.append("\n共 ").append(results.size()).append(" 行");
        if (displayRows < results.size()) {
            result.append("（已显示前 ").append(displayRows).append(" 行）");
        }
        return result.toString();
    }

    /**
     * 执行查询并格式化结果（统一异常处理）
     *
     * @param sql      SQL 语句（已注入租户条件）
     * @param toolName 工具名称（用于日志）
     * @return 格式化后的结果或错误信息
     */
    protected String executeAndFormat(String sql, String toolName) {
        try {
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-{}] 查询完成，返回 {} 行", toolName, results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-{}] 查询失败", toolName, e);
            return "查询失败：" + e.getMessage();
        }
    }
}
