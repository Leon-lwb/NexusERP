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
 * 生产查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 生产相关数据，包括在产工单、产能利用率、BOM展开。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpProductionTool {

    private final ErpQueryService erpQueryService;

    /** SQL 子句关键词正则（用于在正确位置插入 WHERE 条件） */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT)\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * 查询当前在产工单及其进度
     *
     * @return Markdown 表格格式的在产工单列表
     */
    @Tool("查询当前在产工单及其进度")
    public String queryActiveWorkOrders() {
        log.info("[AI-Production] 查询在产工单");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT wo.order_no AS 工单编号,
                       m.name AS 产品名称,
                       wo.quantity AS 计划数量,
                       wo.completed_quantity AS 完成数量,
                       ROUND(wo.completed_quantity * 100.0 / NULLIF(wo.quantity, 0), 2) AS 完成率,
                       wo.planned_start_date AS 计划开始日期,
                       wo.planned_end_date AS 计划结束日期
                FROM prd_work_order wo
                JOIN bd_material m ON wo.material_id = m.id
                WHERE wo.status = 'IN_PROGRESS'
                ORDER BY wo.planned_end_date
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Production] 在产工单查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询在产工单失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询指定车间/工位的产能利用率
     *
     * @param workshopId 车间ID
     * @return Markdown 表格格式的产能利用率数据
     */
    @Tool("查询指定车间/工位的产能利用率")
    public String queryWorkstationUtilization(
        @P("车间ID") Long workshopId) {
        log.info("[AI-Production] 查询产能利用率: workshopId={}", workshopId);
        if (workshopId == null || workshopId <= 0) {
            return "参数错误：车间ID必须为正整数";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT ws.name AS 工位名称,
                       COUNT(DISTINCT wo.id) AS 工单数,
                       SUM(wo.planned_duration) AS 计划工时,
                       SUM(wo.actual_duration) AS 实际工时,
                       ROUND(SUM(wo.actual_duration) * 100.0 / NULLIF(SUM(wo.planned_duration), 0), 2) AS 利用率
                FROM sch_production_schedule ps
                JOIN prd_work_order wo ON ps.work_order_id = wo.id
                JOIN bd_workstation ws ON ps.workstation_id = ws.id
                WHERE ws.workshop_id = %d
                  AND wo.status IN ('IN_PROGRESS', 'COMPLETED')
                GROUP BY ws.name
                ORDER BY 利用率 DESC
                """.formatted(workshopId);
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Production] 产能利用率查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询产能利用率失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询BOM展开（指定产品的所有子件及用量）
     *
     * @param materialCode 产品物料编码
     * @return Markdown 表格格式的BOM展开数据
     */
    @Tool("查询BOM展开（指定产品的所有子件及用量）")
    public String queryBomExplosion(
        @P("产品物料编码") String materialCode) {
        log.info("[AI-Production] 查询BOM展开: materialCode={}", materialCode);
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return "参数错误：产品物料编码不能为空";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                WITH RECURSIVE bom_tree AS (
                    SELECT b.id AS bom_id,
                           m_parent.code AS 父件编码,
                           m_parent.name AS 父件名称,
                           m_child.code AS 子件编码,
                           m_child.name AS 子件名称,
                           bd.quantity AS 用量,
                           1 AS 层级
                    FROM bd_bom b
                    JOIN bd_material m_parent ON b.material_id = m_parent.id
                    JOIN bd_bom_detail bd ON b.id = bd.bom_id
                    JOIN bd_material m_child ON bd.material_id = m_child.id
                    WHERE m_parent.code = '%s'
                    UNION ALL
                    SELECT child_bom.id,
                           bt.子件编码,
                           bt.子件名称,
                           m_child.code,
                           m_child.name,
                           bd.quantity,
                           bt.层级 + 1
                    FROM bom_tree bt
                    JOIN bd_bom child_bom ON child_bom.material_id = (
                        SELECT id FROM bd_material WHERE code = bt.子件编码
                    )
                    JOIN bd_bom_detail bd ON child_bom.id = bd.bom_id
                    JOIN bd_material m_child ON bd.material_id = m_child.id
                )
                SELECT 层级,
                       父件编码,
                       父件名称,
                       子件编码,
                       子件名称,
                       用量
                FROM bom_tree
                ORDER BY 层级, 子件编码
                """.formatted(materialCode.replace("'", "''"));
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeQuery(sql, 100, 5);
            log.info("[AI-Production] BOM展开查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询BOM展开失败", e);
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
