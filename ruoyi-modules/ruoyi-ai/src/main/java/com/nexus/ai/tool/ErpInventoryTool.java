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
 * 库存查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 库存相关数据，包括低库存预警、出库趋势、库存金额分布、按仓库统计。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ErpInventoryTool extends ErpToolHelper {

    public ErpInventoryTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    /**
     * 查询低于安全库存的物料列表及其当前库存、安全库存、缺口数量
     */
    @Tool("查询低于安全库存的物料列表及其当前库存、安全库存、缺口数量")
    public String queryLowStockMaterials() {
        log.info("[AI-Inventory] 查询低库存物料");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT m.material_code AS 物料编码,
                       m.material_name AS 名称,
                       s.stock_quantity AS 当前库存,
                       m.safety_stock AS 安全库存,
                       (m.safety_stock - s.stock_quantity) AS 缺口
                FROM wm_material_stock s
                JOIN bd_material m ON s.material_id = m.material_id
                WHERE s.stock_quantity < m.safety_stock
                ORDER BY (m.safety_stock - s.stock_quantity) DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            return executeAndFormat(sql, "Inventory-LowStock");
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询低库存物料失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 根据物料编码查询最近N个月的出库趋势
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
                SELECT DATE_FORMAT(tx.tx_date, '%Y-%m') AS 月份,
                       SUM(tx.tx_quantity) AS 出库数量,
                       SUM(tx.tx_quantity * tx.unit_cost) AS 出库金额
                FROM wm_material_stock_tx tx
                JOIN bd_material m ON tx.material_id = m.material_id
                WHERE m.material_code = ?
                  AND tx.tx_flag = -1
                  AND tx.tx_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
                GROUP BY DATE_FORMAT(tx.tx_date, '%Y-%m')
                ORDER BY 月份
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{materialCode, months}, 100, 5);
            log.info("[AI-Inventory] 物料出库趋势查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询物料出库趋势失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询库存总金额及按仓库分布
     */
    @Tool("查询库存总金额及按仓库分布")
    public String queryInventoryValue() {
        log.info("[AI-Inventory] 查询库存金额分布");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT w.warehouse_name AS 仓库名称,
                       SUM(s.stock_quantity) AS 库存数量,
                       SUM(s.stock_quantity * s.unit_cost) AS 库存金额
                FROM wm_material_stock s
                JOIN wm_warehouse w ON s.warehouse_id = w.warehouse_id
                GROUP BY w.warehouse_name
                ORDER BY 库存金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            return executeAndFormat(sql, "Inventory-Value");
        } catch (Exception e) {
            log.error("[AI-Inventory] 查询库存金额分布失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 按仓库维度统计库存数量和金额
     */
    @Tool("按仓库维度统计库存数量和金额")
    public String queryStockByWarehouse() {
        log.info("[AI-Inventory] 按仓库统计库存");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT w.warehouse_name AS 仓库名称,
                       COUNT(DISTINCT s.material_id) AS 物料种类数,
                       SUM(s.stock_quantity) AS 总库存数量,
                       SUM(s.stock_quantity * s.unit_cost) AS 总库存金额
                FROM wm_material_stock s
                JOIN wm_warehouse w ON s.warehouse_id = w.warehouse_id
                GROUP BY w.warehouse_name
                ORDER BY 总库存金额 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            return executeAndFormat(sql, "Inventory-ByWarehouse");
        } catch (Exception e) {
            log.error("[AI-Inventory] 按仓库统计库存失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
