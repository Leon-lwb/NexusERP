package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采购查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 采购相关数据，包括采购金额分布、供应商评估、在途订单。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPurchaseTool {

    private final ErpQueryService erpQueryService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * 查询指定时间段的采购总金额及按供应商分布
     *
     * @param startDate 开始日期(YYYY-MM-DD)
     * @param endDate   结束日期(YYYY-MM-DD)
     * @return Markdown 表格格式的采购金额分布
     */
    @Tool("查询指定时间段的采购总金额及按供应商分布")
    public String queryPurchaseAmountByVendor(
        @P("开始日期(YYYY-MM-DD)") String startDate,
        @P("结束日期(YYYY-MM-DD)") String endDate) {
        log.info("[AI-Purchase] 查询采购金额分布: startDate={}, endDate={}", startDate, endDate);
        if (startDate == null || startDate.trim().isEmpty()) {
            return "参数错误：开始日期不能为空";
        }
        if (endDate == null || endDate.trim().isEmpty()) {
            return "参数错误：结束日期不能为空";
        }
        if (!isValidDate(startDate) || !isValidDate(endDate)) {
            return "参数错误：日期格式不正确，请使用 YYYY-MM-DD 格式";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT v.name AS 供应商名称,
                       COUNT(o.id) AS 采购订单数,
                       SUM(o.total_amount) AS 采购总金额
                FROM pu_purchase_order o
                JOIN bd_vendor v ON o.vendor_id = v.id
                WHERE o.order_date >= '%s'
                  AND o.order_date <= '%s'
                GROUP BY v.name
                ORDER BY 采购总金额 DESC
                """.formatted(startDate.replace("'", "''"), endDate.replace("'", "''"));
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Purchase] 采购金额分布查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询采购金额分布失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询供应商综合评估数据，包括交货准时率、退货率
     *
     * @param vendorId 供应商ID
     * @return Markdown 表格格式的供应商评估数据
     */
    @Tool("查询供应商综合评估数据，包括交货准时率、退货率")
    public String queryVendorRiskAssessment(
        @P("供应商ID") Long vendorId) {
        log.info("[AI-Purchase] 查询供应商评估: vendorId={}", vendorId);
        if (vendorId == null || vendorId <= 0) {
            return "参数错误：供应商ID必须为正整数";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT v.name AS 供应商名称,
                       e.total_orders AS 订单总数,
                       e.on_time_orders AS 准时交货数,
                       ROUND(e.on_time_orders * 100.0 / NULLIF(e.total_orders, 0), 2) AS 准时交货率,
                       e.return_orders AS 退货订单数,
                       ROUND(e.return_orders * 100.0 / NULLIF(e.total_orders, 0), 2) AS 退货率,
                       e.composite_score AS 综合评分
                FROM pu_vendor_evaluation e
                JOIN bd_vendor v ON e.vendor_id = v.id
                WHERE e.vendor_id = %d
                """.formatted(vendorId);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Purchase] 供应商评估查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询供应商评估失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最近N天未到货的在途采购订单
     *
     * @param days 查询天数
     * @return Markdown 表格格式的在途采购订单列表
     */
    @Tool("查询最近N天未到货的在途采购订单")
    public String queryPendingPurchaseOrders(
        @P("查询天数") int days) {
        log.info("[AI-Purchase] 查询在途采购订单: days={}", days);
        if (days <= 0) {
            return "参数错误：查询天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT o.order_no AS 订单编号,
                       v.name AS 供应商名称,
                       o.order_date AS 订单日期,
                       o.expected_date AS 预计到货日期,
                       DATEDIFF(CURDATE(), o.expected_date) AS 逾期天数,
                       o.total_amount AS 订单金额
                FROM pu_purchase_order o
                JOIN bd_vendor v ON o.vendor_id = v.id
                WHERE o.status = 'CONFIRMED'
                  AND o.expected_date < CURDATE()
                  AND o.order_date >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                ORDER BY 逾期天数 DESC
                """.formatted(days);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Purchase] 在途采购订单查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询在途采购订单失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 校验日期格式
     */
    private boolean isValidDate(String dateStr) {
        try {
            LocalDate.parse(dateStr.trim(), DATE_FMT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
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
