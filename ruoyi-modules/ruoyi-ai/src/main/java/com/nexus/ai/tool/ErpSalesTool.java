package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 销售查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 销售相关数据，包括月度销售趋势、客户销售排行、退货统计、订单履行率。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ErpSalesTool extends ErpToolHelper {

    public ErpSalesTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    /**
     * 查询指定物料近N个月的月度销售数据，用于趋势分析
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
                SELECT DATE_FORMAT(h.order_date, '%Y-%m') AS 月份,
                       SUM(l.ordered_qty) AS 销售数量,
                       SUM(l.line_amount) AS 销售金额
                FROM so_sales_order_line l
                JOIN so_sales_order_header h ON l.so_id = h.so_id
                JOIN bd_material m ON l.material_id = m.material_id
                WHERE m.material_code = ?
                  AND h.order_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
                GROUP BY DATE_FORMAT(h.order_date, '%Y-%m')
                ORDER BY 月份
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{materialCode, months}, 100, 5);
            log.info("[AI-Sales] 月度销售趋势查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询月度销售趋势失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询按客户维度的销售金额排行（Top N）
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
                SELECT c.client_name AS 客户名称,
                       COUNT(h.so_id) AS 订单数,
                       SUM(h.total_amount) AS 销售总金额
                FROM so_sales_order_header h
                JOIN bd_client c ON h.client_id = c.client_id
                WHERE h.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY c.client_name
                ORDER BY 销售总金额 DESC
                LIMIT ?
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days, limitN}, 100, 5);
            log.info("[AI-Sales] 客户销售排行查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询客户销售排行失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最近N天的退货统计
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
                       COUNT(r.return_id) AS 退货单数,
                       SUM(r.total_amount) AS 退货金额
                FROM so_return r
                WHERE r.return_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY r.return_reason
                ORDER BY 退货金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Sales] 退货统计查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询退货统计失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询销售订单履行率（已发货数量 / 订单数量）
     */
    @Tool("查询销售订单履行率")
    public String querySalesOrderFulfillmentRate() {
        log.info("[AI-Sales] 查询销售订单履行率");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT h.so_no AS 订单编号,
                       c.client_name AS 客户名称,
                       h.order_date AS 订单日期,
                       SUM(l.ordered_qty) AS 订单总数量,
                       SUM(l.delivered_qty) AS 已发货数量,
                       ROUND(SUM(l.delivered_qty) * 100.0 / NULLIF(SUM(l.ordered_qty), 0), 2) AS 履行率
                FROM so_sales_order_header h
                JOIN so_sales_order_line l ON h.so_id = l.so_id
                JOIN bd_client c ON h.client_id = c.client_id
                WHERE h.order_status IN ('CONFIRMED', 'FULFILLED')
                GROUP BY h.so_id, h.so_no, c.client_name, h.order_date
                ORDER BY 履行率 ASC
                """;
            sql = injectTenantCondition(sql, tenantId);
            return executeAndFormat(sql, "Sales-FulfillmentRate");
        } catch (Exception e) {
            log.error("[AI-Sales] 查询销售订单履行率失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
