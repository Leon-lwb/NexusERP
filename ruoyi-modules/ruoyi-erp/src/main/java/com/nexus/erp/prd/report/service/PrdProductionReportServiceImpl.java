package com.nexus.erp.prd.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 生产报表 Service 实现
 * <p>通过 JDBC 直接查询生产工单数据，提供工单完工率、生产效率等统计报表的计算。
 * 属于 PRD（生产计划）模块，数据源为 erp 库。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdProductionReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 统计近 N 个月工单完工率
     * <p>按月分组统计生产工单总数与完工（COMPLETED）数量，并计算完工率百分比。</p>
     *
     * @param months 统计月份数，为空时默认 6 个月
     * @return 每月工单总数、完工数与完工率的明细列表
     */
    public List<Map<String, Object>> getWorkOrderCompletionRate(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(wo.plan_start_date, '%Y-%m') AS month, " +
            "COUNT(*) AS totalOrders, " +
            "SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedOrders, " +
            "ROUND(SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate " +
            "FROM prd_work_order wo " +
            "WHERE wo.del_flag = '0' " +
            "AND wo.plan_start_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 统计近 N 个月生产效率
     * <p>按月分组统计计划产量与完工产量，并计算生产效率（完工/计划）百分比。</p>
     *
     * @param months 统计月份数，为空时默认 6 个月
     * @return 每月计划产量、完工产量与效率百分比的明细列表
     */
    public List<Map<String, Object>> getProductionEfficiency(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(wo.plan_start_date, '%Y-%m') AS month, " +
            "SUM(wo.planned_qty) AS plannedQty, " +
            "SUM(wo.completed_qty) AS completedQty, " +
            "CASE WHEN SUM(wo.planned_qty) > 0 " +
            "THEN ROUND(SUM(wo.completed_qty) / SUM(wo.planned_qty) * 100, 1) " +
            "ELSE 0 END AS efficiencyRate " +
            "FROM prd_work_order wo " +
            "WHERE wo.del_flag = '0' " +
            "AND wo.plan_start_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }
}
