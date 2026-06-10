package com.nexus.ai.tool;

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
 * 销售查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 销售相关数据，包括月度销售趋势、客户销售排行、退货统计。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpSalesTool {

    private final ErpQueryService erpQueryService;

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * 查询指定物料近N个月的月度销售数据，用于趋势分析
     *
     * @param materialCode 物料编码
     * @param months       查询月数
     * @return Markdown 表格格式的月度销售趋势
     */
    @Tool("查询指定物料近N个月的月度销售数据，用于趋势分析")
    public String queryMonthlySalesTrend(
        @P("物料编码") String materialCode,
        @P("查询月数") int months) {
        log.info("[AI-Sales] 查询月度销售趋势: materialCode={}, months={}", materialCode, months);
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return "参数错误：物料编码不能为空";
        }
        if (months <= 0) {
            return "参数错误：查询月数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT DATE_FORMAT(h.order_date, '%%Y-%%m') AS 月份,
                       SUM(l.quantity) AS 销售数量,
                       SUM(l.amount) AS 销售金额
                FROM so_sales_order_line l
                JOIN so_sales_order_header h ON l.header_id = h.id
                JOIN bd_material m ON l.material_id = m.id
                WHERE m.code = '%s'
                  AND h.order_date >= DATE_SUB(CURDATE(), INTERVAL %d MONTH)
                GROUP BY DATE_FORMAT(h.order_date, '%%Y-%%m')
                ORDER BY 月份
                """.formatted(materialCode.replace("'", "''"), months);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Sales] 月度销售趋势查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询月度销售趋势失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询按客户维度的销售金额排行（Top N）
     *
     * @param days 时间范围（天数）
     * @param topN Top N
     * @return Markdown 表格格式的客户销售排行
     */
    @Tool("查询按客户维度的销售金额排行（Top N）")
    public String queryTopClientsBySales(
        @P("时间范围（天数）") int days,
        @P("Top N") int topN) {
        log.info("[AI-Sales] 查询客户销售排行: days={}, topN={}", days, topN);
        if (days <= 0) {
            return "参数错误：时间范围天数必须大于0";
        }
        if (topN <= 0) {
            return "参数错误：Top N必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            int limitN = Math.min(topN, 100);
            String sql = """
                SELECT c.name AS 客户名称,
                       COUNT(h.id) AS 订单数,
                       SUM(h.total_amount) AS 销售总金额
                FROM so_sales_order_header h
                JOIN bd_client c ON h.client_id = c.id
                WHERE h.order_date >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY c.name
                ORDER BY 销售总金额 DESC
                LIMIT %d
                """.formatted(days, limitN);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Sales] 客户销售排行查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询客户销售排行失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最近N天的退货统计
     *
     * @param days 查询天数
     * @return Markdown 表格格式的退货统计
     */
    @Tool("查询最近N天的退货统计")
    public String queryRecentReturns(
        @P("查询天数") int days) {
        log.info("[AI-Sales] 查询退货统计: days={}", days);
        if (days <= 0) {
            return "参数错误：查询天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT r.return_reason AS 退货原因,
                       COUNT(r.id) AS 退货单数,
                       SUM(r.total_amount) AS 退货金额
                FROM so_sales_return r
                WHERE r.return_date >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY r.return_reason
                ORDER BY 退货金额 DESC
                """.formatted(days);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Sales] 退货统计查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询退货统计失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 注入租户隔离条件
     */
    private String injectTenantCondition(String sql, Long tenantId) {
        if (tenantId == null || sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
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
     * 格式化查询结果为 Markdown 表格
     */
    private String formatResults(List<Map<String, Object>> results) {
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
}
