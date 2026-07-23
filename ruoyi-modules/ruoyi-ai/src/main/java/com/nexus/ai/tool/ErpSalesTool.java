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
        @P(value = "查询月数", defaultValue = "6") int months) {
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
        @P(value = "时间范围（天数）", defaultValue = "30") int days,
        @P(value = "Top N", defaultValue = "10") int topN) {
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
        @P(value = "查询天数", defaultValue = "30") int days) {
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

    /**
     * 查询各物料分类的销售额分布
     * <p>按物料分类维度统计销售金额和数量，用于分析品类贡献</p>
     *
     * @param days 统计天数
     * @return 各品类销售额分布
     */
    @Tool("查询各物料分类的销售额分布")
    public String querySalesByCategory(@P(value = "统计天数", defaultValue = "30") int days) {
        log.info("[AI-Sales] 查询品类销售分布: days={}", days);
        if (days <= 0) {
            return "参数错误：统计天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT mc.category_name AS 品类名称,
                       SUM(l.line_amount) AS 销售金额,
                       SUM(l.ordered_qty) AS 销售数量,
                       COUNT(DISTINCT l.so_id) AS 订单数
                FROM so_sales_order_line l
                JOIN so_sales_order_header h ON l.so_id = h.so_id
                JOIN bd_material m ON l.material_id = m.material_id
                LEFT JOIN bd_material_category mc ON m.category_id = mc.category_id
                WHERE h.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY mc.category_name
                ORDER BY 销售金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Sales] 品类销售分布查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询品类销售分布失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询物料销售金额排行 TOP N
     * <p>按物料维度统计销售金额，返回排行前N的物料</p>
     *
     * @param days 统计天数
     * @param topN 排行数量
     * @return 物料销售排行
     */
    @Tool("查询物料销售金额排行TOP N")
    public String queryTopMaterialsBySales(
        @P(value = "统计天数", defaultValue = "30") int days,
        @P(value = "Top N", defaultValue = "10") int topN) {
        log.info("[AI-Sales] 查询物料销售排行: days={}, topN={}", days, topN);
        if (days <= 0) {
            return "参数错误：统计天数必须大于0";
        }
        if (topN <= 0) {
            return "参数错误：Top N必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            int limitN = Math.min(topN, 100);
            String sql = """
                SELECT m.material_code AS 物料编码,
                       m.material_name AS 物料名称,
                       SUM(l.ordered_qty) AS 销售数量,
                       SUM(l.line_amount) AS 销售金额
                FROM so_sales_order_line l
                JOIN so_sales_order_header h ON l.so_id = h.so_id
                JOIN bd_material m ON l.material_id = m.material_id
                WHERE h.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY m.material_code, m.material_name
                ORDER BY 销售金额 DESC
                LIMIT ?
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days, limitN}, 100, 5);
            log.info("[AI-Sales] 物料销售排行查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Sales] 查询物料销售排行失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
