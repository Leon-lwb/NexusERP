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
 * 采购查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 采购相关数据，包括采购金额分布、供应商评估、在途订单、采购价格历史。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ErpPurchaseTool extends ErpToolHelper {

    public ErpPurchaseTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    /**
     * 查询指定时间段的采购总金额及按供应商分布
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
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT v.vendor_name AS 供应商名称,
                       COUNT(o.order_id) AS 采购订单数,
                       SUM(o.total_amount) AS 采购总金额
                FROM pu_purchase_order o
                JOIN bd_vendor v ON o.vendor_id = v.vendor_id
                WHERE o.order_date >= ?
                  AND o.order_date <= ?
                GROUP BY v.vendor_name
                ORDER BY 采购总金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{startDate, endDate}, 100, 5);
            log.info("[AI-Purchase] 采购金额分布查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询采购金额分布失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询供应商综合评估数据，包括交货准时率、退货率
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
                SELECT v.vendor_name AS 供应商名称,
                       e.total_orders AS 订单总数,
                       e.on_time_orders AS 准时交货数,
                       ROUND(e.on_time_orders * 100.0 / NULLIF(e.total_orders, 0), 2) AS 准时交货率,
                       e.return_qty AS 退货订单数,
                       ROUND(e.return_qty * 100.0 / NULLIF(e.total_orders, 0), 2) AS 退货率,
                       e.total_score AS 综合评分
                FROM pu_vendor_evaluation e
                JOIN bd_vendor v ON e.vendor_id = v.vendor_id
                WHERE e.vendor_id = ?
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{vendorId}, 100, 5);
            log.info("[AI-Purchase] 供应商评估查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询供应商评估失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最近N天未到货的在途采购订单
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
                       v.vendor_name AS 供应商名称,
                       o.order_date AS 订单日期,
                       o.expected_arrival_date AS 预计到货日期,
                       DATEDIFF(CURDATE(), o.expected_arrival_date) AS 逾期天数,
                       o.total_amount AS 订单金额
                FROM pu_purchase_order o
                JOIN bd_vendor v ON o.vendor_id = v.vendor_id
                WHERE o.status = 'APPROVED'
                  AND o.expected_arrival_date < CURDATE()
                  AND o.order_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                ORDER BY 逾期天数 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Purchase] 在途采购订单查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询在途采购订单失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询指定物料近N个月的采购价格历史趋势
     */
    @Tool("查询指定物料近N个月的采购价格历史趋势")
    public String queryPurchasePriceHistory(
        @P("物料编码") String materialCode,
        @P("查询月数") int months) {
        log.info("[AI-Purchase] 查询采购价格历史: materialCode={}, months={}", materialCode, months);
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return "参数错误：物料编码不能为空";
        }
        if (months <= 0) {
            return "参数错误：查询月数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS 月份,
                       AVG(l.unit_price) AS 平均采购单价,
                       MIN(l.unit_price) AS 最低单价,
                       MAX(l.unit_price) AS 最高单价,
                       SUM(l.order_quantity) AS 采购总量
                FROM pu_purchase_order o
                JOIN pu_purchase_order_line l ON o.order_id = l.order_id
                JOIN bd_material m ON l.material_id = m.material_id
                WHERE m.material_code = ?
                  AND o.order_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
                GROUP BY DATE_FORMAT(o.order_date, '%Y-%m')
                ORDER BY 月份
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{materialCode, months}, 100, 5);
            log.info("[AI-Purchase] 采购价格历史查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Purchase] 查询采购价格历史失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
