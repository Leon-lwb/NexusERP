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
 * 生产查询工具
 * <p>
 * 供 AI Agent 调用，查询 ERP 生产相关数据，包括在产工单、产能利用率、BOM展开、生产完成率。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ErpProductionTool extends ErpToolHelper {

    public ErpProductionTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    /**
     * 查询当前在产工单及其进度
     */
    @Tool("查询当前在产工单及其进度")
    public String queryActiveWorkOrders() {
        log.info("[AI-Production] 查询在产工单");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT wo.wo_no AS 工单编号,
                       m.material_name AS 产品名称,
                       wo.planned_qty AS 计划数量,
                       wo.completed_qty AS 完成数量,
                       ROUND(wo.completed_qty * 100.0 / NULLIF(wo.planned_qty, 0), 2) AS 完成率,
                       wo.plan_start_date AS 计划开始日期,
                       wo.plan_end_date AS 计划结束日期
                FROM prd_work_order wo
                JOIN bd_material m ON wo.material_id = m.material_id
                WHERE wo.wo_status = 'IN_PROGRESS'
                ORDER BY wo.plan_end_date
                """;
            sql = injectTenantCondition(sql, tenantId);
            return executeAndFormat(sql, "Production-ActiveOrders");
        } catch (Exception e) {
            log.error("[AI-Production] 查询在产工单失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询指定车间/工位的产能利用率
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
                SELECT ws.workstation_name AS 工位名称,
                       COUNT(DISTINCT wo.wo_id) AS 工单数,
                       SUM(wo.planned_qty) AS 计划数量,
                       SUM(wo.completed_qty) AS 完成数量,
                       ROUND(SUM(wo.completed_qty) * 100.0 / NULLIF(SUM(wo.planned_qty), 0), 2) AS 完成率
                FROM prd_work_order wo
                JOIN bd_workstation ws ON wo.workstation_id = ws.workstation_id
                WHERE ws.workshop_id = ?
                  AND wo.wo_status IN ('IN_PROGRESS', 'COMPLETED')
                GROUP BY ws.workstation_name
                ORDER BY 完成率 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{workshopId}, 100, 5);
            log.info("[AI-Production] 产能利用率查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询产能利用率失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询BOM展开（指定产品的所有子件及用量）
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
                    SELECT b.bom_id AS bom_id,
                           m_parent.material_code AS 父件编码,
                           m_parent.material_name AS 父件名称,
                           m_child.material_code AS 子件编码,
                           m_child.material_name AS 子件名称,
                           bd.quantity_per_parent AS 用量,
                           1 AS 层级
                    FROM bd_bom b
                    JOIN bd_material m_parent ON b.parent_material_id = m_parent.material_id
                    JOIN bd_bom_detail bd ON b.bom_id = bd.bom_id
                    JOIN bd_material m_child ON bd.child_material_id = m_child.material_id
                    WHERE m_parent.material_code = ?
                    UNION ALL
                    SELECT child_bom.bom_id,
                           bt.子件编码,
                           bt.子件名称,
                           m_child.material_code,
                           m_child.material_name,
                           bd.quantity_per_parent,
                           bt.层级 + 1
                    FROM bom_tree bt
                    JOIN bd_bom child_bom ON child_bom.parent_material_id = (
                        SELECT material_id FROM bd_material WHERE material_code = bt.子件编码
                    )
                    JOIN bd_bom_detail bd ON child_bom.bom_id = bd.bom_id
                    JOIN bd_material m_child ON bd.child_material_id = m_child.material_id
                )
                SELECT 层级,
                       父件编码,
                       父件名称,
                       子件编码,
                       子件名称,
                       用量
                FROM bom_tree
                ORDER BY 层级, 子件编码
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{materialCode}, 100, 5);
            log.info("[AI-Production] BOM展开查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询BOM展开失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最近N天的生产完成率
     */
    @Tool("查询最近N天的生产完成率")
    public String queryProductionCompletionRate(
        @P(value = "查询天数", defaultValue = "30") int days) {
        log.info("[AI-Production] 查询生产完成率: days={}", days);
        if (days <= 0) {
            return "参数错误：查询天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT DATE(wo.plan_end_date) AS 日期,
                       COUNT(wo.wo_id) AS 工单总数,
                       SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) AS 完成工单数,
                       ROUND(SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(wo.wo_id), 0), 2) AS 完成率,
                       SUM(wo.planned_qty) AS 计划总数量,
                       SUM(wo.completed_qty) AS 完成总数量,
                       ROUND(SUM(wo.completed_qty) * 100.0 / NULLIF(SUM(wo.planned_qty), 0), 2) AS 数量完成率
                FROM prd_work_order wo
                WHERE wo.plan_end_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY DATE(wo.plan_end_date)
                ORDER BY 日期 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Production] 生产完成率查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询生产完成率失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询生产延期分析
     * <p>筛选计划结束日期已过但未完成的工单，按延期天数排序</p>
     *
     * @param days 查询最近N天的工单
     * @return 延期工单列表
     */
    @Tool("查询生产延期分析（计划结束日期已过但未完成的工单）")
    public String queryProductionDelayAnalysis(@P(value = "查询天数", defaultValue = "30") int days) {
        log.info("[AI-Production] 查询生产延期分析: days={}", days);
        if (days <= 0) {
            return "参数错误：查询天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT wo.wo_no AS 工单编号,
                       m.material_name AS 产品名称,
                       wo.planned_qty AS 计划数量,
                       wo.completed_qty AS 完成数量,
                       ROUND(wo.completed_qty * 100.0 / NULLIF(wo.planned_qty, 0), 2) AS 完成率,
                       wo.plan_end_date AS 计划结束日期,
                       DATEDIFF(CURDATE(), wo.plan_end_date) AS 延期天数,
                       wo.workshop_name AS 车间
                FROM prd_work_order wo
                JOIN bd_material m ON wo.material_id = m.material_id
                WHERE wo.wo_status NOT IN ('COMPLETED', 'CANCELLED')
                  AND wo.plan_end_date < CURDATE()
                  AND wo.plan_end_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                ORDER BY 延期天数 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Production] 生产延期分析查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询生产延期分析失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询各车间的产量统计
     * <p>按车间维度统计计划数量、完成数量和完成率</p>
     *
     * @param days 统计天数
     * @return 车间产量统计
     */
    @Tool("查询各车间的产量统计")
    public String queryWorkshopOutput(@P(value = "统计天数", defaultValue = "30") int days) {
        log.info("[AI-Production] 查询车间产量统计: days={}", days);
        if (days <= 0) {
            return "参数错误：统计天数必须大于0";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT wo.workshop_name AS 车间名称,
                       COUNT(wo.wo_id) AS 工单数,
                       SUM(wo.planned_qty) AS 计划数量,
                       SUM(wo.completed_qty) AS 完成数量,
                       ROUND(SUM(wo.completed_qty) * 100.0 / NULLIF(SUM(wo.planned_qty), 0), 2) AS 完成率
                FROM prd_work_order wo
                WHERE wo.plan_start_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY wo.workshop_name
                ORDER BY 完成率 DESC
                """;
            sql = injectTenantCondition(sql, tenantId);
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{days}, 100, 5);
            log.info("[AI-Production] 车间产量统计查询完成，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-Production] 查询车间产量统计失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
