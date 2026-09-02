package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.common.json.utils.JsonUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * APS 智能排产查询工具
 * <p>
 * 供 AI Agent 调用，读取排程引擎（prd_schedule_plan / prd_schedule_task）的结果数据，
 * 支撑"下周哪些订单会拖期 / 瓶颈资源是什么 / 某工单如何排产"等决策类问答。
 * 查询类工具（只读），不触发重排；重排请在 生产执行 → 智能排产 页面执行。
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ApsScheduleTool extends ErpToolHelper {

    public ApsScheduleTool(ErpQueryService erpQueryService) {
        super(erpQueryService);
    }

    /**
     * 无排产数据时的统一提示
     */
    private static final String NO_PLAN_HINT =
        "尚未找到排产数据。请先在「生产执行 → 智能排产」页面执行排程（需要未完工工单和对应物料的工艺路线），然后再查询。";

    /**
     * 查询最近的排产方案列表（含拖期数与总完工日）
     *
     * @param days 最近天数(默认30)
     */
    @Tool("查询最近的APS排产方案列表，包含每个方案的策略、参与工单数、拖期工单数、总负荷工时与总完工日期")
    public String querySchedulePlans(@P("最近天数，默认30") Integer days) {
        int range = (days == null || days <= 0) ? 30 : days;
        log.info("[AI-APS] 查询排产方案列表: 最近{}天", range);
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT plan_no AS 方案编号,
                       plan_name AS 方案名称,
                       strategy AS 排序策略,
                       DATE(start_date) AS 排产起始日,
                       horizon_days AS 跨度天,
                       wo_count AS 工单数,
                       task_count AS 任务数,
                       delayed_count AS 拖期工单数,
                       total_hours AS 总负荷工时,
                       DATE(makespan_date) AS 总完工日,
                       status AS 状态
                FROM prd_schedule_plan
                WHERE create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND tenant_id = ?
                ORDER BY create_time DESC
                """;
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{range, tenantId}, 100, 5);
            if (results.isEmpty()) {
                return NO_PLAN_HINT;
            }
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-APS] 查询排产方案失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最新方案中的拖期工单（排程完工日晚于交期）
     */
    @Tool("查询最新排产方案中预计拖期的工单清单，包含交期、排程完工日、拖期天数与占用资源，用于回答'哪些订单会拖期'")
    public String queryDelayedWorkOrders() {
        log.info("[AI-APS] 查询拖期工单");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT t.wo_no AS 工单编号,
                       t.material_name AS 产品,
                       DATE(wo.plan_end_date) AS 交期,
                       DATE(MAX(t.end_time)) AS 排程完工日,
                       DATEDIFF(DATE(MAX(t.end_time)), DATE(wo.plan_end_date)) AS 拖期天数,
                       GROUP_CONCAT(DISTINCT CONCAT(t.seq_num, '.', t.process_name)) AS 工序,
                       GROUP_CONCAT(DISTINCT t.resource_name) AS 占用资源
                FROM prd_schedule_task t
                JOIN prd_work_order wo ON t.wo_id = wo.wo_id
                WHERE t.delay_flag = 1
                  AND t.tenant_id = ?
                  AND t.plan_id = (
                      SELECT p.plan_id FROM prd_schedule_plan p
                      WHERE p.tenant_id = ? ORDER BY p.create_time DESC LIMIT 1
                  )
                GROUP BY t.wo_no, t.material_name, wo.plan_end_date
                ORDER BY 拖期天数 DESC
                """;
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{tenantId, tenantId}, 100, 5);
            if (results.isEmpty()) {
                return "最新排产方案中没有拖期工单（或尚无排产数据）。";
            }
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-APS] 查询拖期工单失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询最新方案的资源利用率（瓶颈分析）
     */
    @Tool("查询最新排产方案的资源利用率排行（已排工时/可用工时/利用率），识别瓶颈工位或车间")
    public String queryResourceUtilization() {
        log.info("[AI-APS] 查询资源利用率");
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT plan_no AS 方案编号, utilization_json AS 利用率明细
                FROM prd_schedule_plan
                WHERE tenant_id = ? AND utilization_json IS NOT NULL
                ORDER BY create_time DESC
                LIMIT 1
                """;
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{tenantId}, 1, 5);
            if (results.isEmpty() || results.get(0).get("利用率明细") == null) {
                return NO_PLAN_HINT;
            }
            String planNo = String.valueOf(results.get(0).get("方案编号"));
            List<Map> loads = JsonUtils.parseArray(String.valueOf(results.get(0).get("利用率明细")), Map.class);
            if (loads == null || loads.isEmpty()) {
                return "方案 " + planNo + " 没有资源利用率数据（可能全部为委外工序）。";
            }
            loads.sort((a, b) -> Double.compare(
                toDouble(String.valueOf(b.get("rate"))), toDouble(String.valueOf(a.get("rate")))));
            StringBuilder sb = new StringBuilder();
            sb.append("排产方案 ").append(planNo).append(" 资源利用率（按利用率降序，首位即瓶颈）：\n\n");
            sb.append("| 资源 | 已排工时 | 可用工时 | 利用率 |\n| --- | --- | --- | --- |\n");
            for (Map load : loads) {
                sb.append("| ").append(load.get("resourceName"))
                    .append(" | ").append(load.get("bookedHours"))
                    .append(" | ").append(load.get("availableHours"))
                    .append(" | ").append(load.get("rate")).append("% |\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[AI-APS] 查询资源利用率失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询某工单在最新方案中的工序排程明细
     *
     * @param woNo 工单编号
     */
    @Tool("查询指定工单在最新排产方案中的工序级排程明细（每道工序的资源、起止日期与工时），用于回答'某工单是怎么排的'")
    public String queryWorkOrderSchedule(@P("工单编号，如 SCGD202608230001") String woNo) {
        log.info("[AI-APS] 查询工单排程明细: woNo={}", woNo);
        if (woNo == null || woNo.isBlank()) {
            return "参数错误：工单编号不能为空";
        }
        try {
            Long tenantId = AiRequestContext.getTenantId();
            String sql = """
                SELECT t.wo_no AS 工单编号,
                       t.seq_num AS 工序序号,
                       t.process_name AS 工序,
                       t.resource_type AS 资源类型,
                       IFNULL(NULLIF(t.resource_name, ''), CONCAT('工作站', t.resource_id)) AS 资源,
                       t.process_hours AS 工时,
                       DATE(t.start_time) AS 开始日,
                       DATE(t.end_time) AS 结束日,
                       t.delay_flag AS 拖期
                FROM prd_schedule_task t
                WHERE t.wo_no = ?
                  AND t.tenant_id = ?
                  AND t.plan_id = (
                      SELECT p.plan_id FROM prd_schedule_plan p
                      WHERE p.tenant_id = ? ORDER BY p.create_time DESC LIMIT 1
                  )
                ORDER BY t.wo_no, t.seq_num
                """;
            List<Map<String, Object>> results = erpQueryService.executeParameterizedQuery(
                sql, new Object[]{woNo.trim(), tenantId, tenantId}, 100, 5);
            if (results.isEmpty()) {
                return "工单 " + woNo + " 在最新排产方案中没有排程任务（可能无工艺路线被跳过，或工单编号不存在）。";
            }
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-APS] 查询工单排程明细失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    private double toDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
