package com.nexus.erp.prd.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdProductionReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getWorkOrderCompletionRate(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(wo.planned_start_date, '%Y-%m') AS month, " +
            "COUNT(*) AS totalOrders, " +
            "SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedOrders, " +
            "ROUND(SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate " +
            "FROM prd_work_order wo " +
            "WHERE wo.del_flag = '0' " +
            "AND wo.planned_start_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getProductionEfficiency(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(wo.planned_start_date, '%Y-%m') AS month, " +
            "SUM(wo.planned_qty) AS plannedQty, " +
            "SUM(wo.completed_qty) AS completedQty, " +
            "CASE WHEN SUM(wo.planned_qty) > 0 " +
            "THEN ROUND(SUM(wo.completed_qty) / SUM(wo.planned_qty) * 100, 1) " +
            "ELSE 0 END AS efficiencyRate " +
            "FROM prd_work_order wo " +
            "WHERE wo.del_flag = '0' " +
            "AND wo.planned_start_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }
}
