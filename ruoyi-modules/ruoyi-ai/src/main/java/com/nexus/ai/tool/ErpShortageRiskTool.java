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
 * ERP 供应链缺料风险查询工具
 * 供 AI Agent 调用，读取缺料风险推演结果（prd_material_requirement 明细 + wm_material_stock + pu_purchase_order），
 * 支撑"未来两周哪些物料会缺料 / 缺口多大 / 该不该催货"等供应链决策问答。
 * 查询类工具（只读）。推演逻辑与 /erp/prd/shortage/predict 同口径，此处以 SQL 直查 @DS("erp") 数据源实现（Agent 工具不走业务 Service）。
 */
@Slf4j
@Component
public class ErpShortageRiskTool extends ErpToolHelper {

    public ErpShortageRiskTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    @Tool("查询未来N天供应链缺料风险清单(MRP毛需求+库存+在途采购逐日推演), 返回物料/最早缺料日/最大缺口/在途量/建议动作, 用于回答'未来两周哪些物料会缺料'")
    public String queryShortageRisk(@P("推演天数, 默认14, 上限90") Integer days) {
        int range = (days == null || days <= 0) ? 14 : Math.min(days, 90);
        log.info("[AI-Shortage] 查询缺料风险: 未来{}天", range);
        try {
            Long tenantId = AiRequestContext.getTenantId();
            // 与 PrdShortageRiskServiceImpl 同口径:
            // 毛需求 = MRP明细(需求日在窗口内, 未发料余量 = required_quantity - issued_quantity)
            // 库存 = SUM(stock_quantity - reserved_quantity)
            // 在途 = APPROVED/PARTIALLY_RECEIVED 采购单行的 order_quantity - received_quantity
            // 逐日余额 = 库存 + 累计在途 - 累计需求 - 安全库存; 首次 < 0 即最早缺料日
            String sql = """
                WITH demand AS (
                    SELECT d.material_id,
                           SUM(d.required_quantity - IFNULL(d.issued_quantity, 0)) AS demand_qty,
                           MIN(d.required_date) AS first_demand_date
                    FROM prd_material_requirement_detail d
                    WHERE d.required_quantity > 0
                      AND d.required_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)
                      AND d.tenant_id = ?
                      AND d.del_flag = '0'
                    GROUP BY d.material_id
                ),
                stock AS (
                    SELECT s.material_id,
                           SUM(s.stock_quantity - IFNULL(s.reserved_quantity, 0)) AS available_qty
                    FROM wm_material_stock s
                    WHERE s.stock_quantity > 0
                      AND s.tenant_id = ?
                      AND s.del_flag = '0'
                    GROUP BY s.material_id
                ),
                onorder AS (
                    SELECT l.material_id,
                           SUM(l.order_quantity - IFNULL(l.received_quantity, 0)) AS on_order_qty
                    FROM pu_purchase_order_line l
                    JOIN pu_purchase_order o ON o.order_id = l.order_id
                    WHERE o.status IN ('APPROVED', 'PARTIALLY_RECEIVED')
                      AND o.expected_arrival_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)
                      AND l.order_quantity > IFNULL(l.received_quantity, 0)
                      AND o.tenant_id = ?
                      AND o.del_flag = '0'
                    GROUP BY l.material_id
                )
                SELECT m.material_code AS 物料编码,
                       m.material_name AS 物料名称,
                       IFNULL(st.available_qty, 0) AS 可用库存,
                       IFNULL(m.safety_stock, 0) AS 安全库存,
                       IFNULL(dm.demand_qty, 0) AS 期内需求,
                       IFNULL(oo.on_order_qty, 0) AS 在途量,
                       dm.first_demand_date AS 最早需求日,
                       IFNULL(st.available_qty, 0) + IFNULL(oo.on_order_qty, 0)
                           - IFNULL(dm.demand_qty, 0) - IFNULL(m.safety_stock, 0) AS 推演净余额,
                       CASE
                           WHEN IFNULL(st.available_qty, 0) + IFNULL(oo.on_order_qty, 0)
                                - IFNULL(dm.demand_qty, 0) - IFNULL(m.safety_stock, 0) < 0
                           THEN IFNULL(dm.demand_qty, 0) + IFNULL(m.safety_stock, 0)
                                - IFNULL(st.available_qty, 0) - IFNULL(oo.on_order_qty, 0)
                           ELSE 0
                       END AS 预计缺口,
                       CASE
                           WHEN IFNULL(st.available_qty, 0) + IFNULL(oo.on_order_qty, 0)
                                - IFNULL(dm.demand_qty, 0) - IFNULL(m.safety_stock, 0) < 0
                               AND IFNULL(oo.on_order_qty, 0) >= IFNULL(dm.demand_qty, 0)
                                + IFNULL(m.safety_stock, 0) - IFNULL(st.available_qty, 0)
                           THEN '催货: 在途可覆盖缺口'
                           WHEN IFNULL(st.available_qty, 0) + IFNULL(oo.on_order_qty, 0)
                                - IFNULL(dm.demand_qty, 0) - IFNULL(m.safety_stock, 0) < 0
                           THEN '提前采购: 在途不足'
                           ELSE '无风险'
                       END AS 建议
                FROM demand dm
                LEFT JOIN stock st ON st.material_id = dm.material_id
                LEFT JOIN onorder oo ON oo.material_id = dm.material_id
                LEFT JOIN bd_material m ON m.material_id = dm.material_id AND m.del_flag = '0'
                ORDER BY 预计缺口 DESC, dm.first_demand_date ASC
                """;
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{range, tenantId, tenantId, range, tenantId}, 100, 5);
            if (results.isEmpty()) {
                return "未来" + range + "天内没有物料需求计划（MRP 无未发料明细），无法推演缺料风险。请先生成物料需求计划。";
            }
            long riskCount = results.stream()
                .filter(r -> !"无风险".equals(String.valueOf(r.get("建议"))))
                .count();
            StringBuilder sb = new StringBuilder();
            sb.append("未来 ").append(range)
              .append(" 天缺料风险推演（共 ").append(results.size())
              .append(" 种物料有需求，其中 ").append(riskCount).append(" 种存在缺料风险）：\n\n");
            sb.append(formatResults(results));
            sb.append("\n说明: 推演净余额 = 可用库存 + 在途量 − 期内需求 − 安全库存, 小于 0 即缺料; ")
              .append("建议列'催货'表示在途可覆盖但需提前到货, '提前采购'表示在途不足需新下单。");
            return sb.toString();
        } catch (Exception e) {
            log.error("[AI-Shortage] 查询缺料风险失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
