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
 * 库存查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 库存相关数据，包括低库存预警、出库趋势、库存金额分布。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpInventoryTool {

    private final ErpQueryService erpQueryService;

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * 查询低于安全库存的物料列表及其当前库存、安全库存、缺口数量
     *
     * @return Markdown 表格格式的低库存物料列表
     */
    @Tool("查询低于安全库存的物料列表及其当前库存、安全库存、缺口数量")
    public String queryLowStockMaterials() {
        log.info("[AI-Inventory] 查询低库存物料");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT m.code AS 物料编码,
                       m.name AS 名称,
                       s.quantity AS 当前库存,
                       m.safety_stock AS 安全库存,
                       (m.safety_stock - s.quantity) AS 缺口
                FROM wm_material_stock s
                JOIN bd_material m ON s.material_id = m.id
                WHERE s.quantity < m.safety_stock
                ORDER BY (m.safety_stock - s.quantity) DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Inventory] 低库存物料查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询低库存物料失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 根据物料编码查询最近N个月的出库趋势
     *
     * @param materialCode 物料编码
     * @param months       查询月数
     * @return Markdown 表格格式的月度出库趋势
     */
    @Tool("根据物料编码查询最近N个月的出库趋势")
    public String queryMaterialOutboundTrend(
        @P("物料编码") String materialCode,
        @P("查询月数") int months) {
        log.info("[AI-Inventory] 查询物料出库趋势: materialCode={}, months={}", materialCode, months);
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return "参数错误：物料编码不能为空";
        }
        if (months <= 0) {
            return "参数错误：查询月数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT DATE_FORMAT(tx.tx_date, '%%Y-%%m') AS 月份,
                       SUM(tx.quantity) AS 出库数量,
                       SUM(tx.quantity * tx.unit_price) AS 出库金额
                FROM wm_material_stock_tx tx
                JOIN bd_material m ON tx.material_id = m.id
                WHERE m.code = '%s'
                  AND tx.tx_type = 'OUT'
                  AND tx.tx_date >= DATE_SUB(CURDATE(), INTERVAL %d MONTH)
                GROUP BY DATE_FORMAT(tx.tx_date, '%%Y-%%m')
                ORDER BY 月份
                """.formatted(materialCode.replace("'", "''"), months);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Inventory] 物料出库趋势查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询物料出库趋势失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询库存总金额及按仓库分布
     *
     * @return Markdown 表格格式的库存金额分布
     */
    @Tool("查询库存总金额及按仓库分布")
    public String queryInventoryValue() {
        log.info("[AI-Inventory] 查询库存金额分布");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT w.name AS 仓库名称,
                       SUM(s.quantity) AS 库存数量,
                       SUM(s.quantity * s.avg_cost) AS 库存金额
                FROM wm_material_stock s
                JOIN wm_warehouse w ON s.warehouse_id = w.id
                GROUP BY w.name
                ORDER BY 库存金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Inventory] 库存金额分布查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询库存金额分布失败", e);
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
