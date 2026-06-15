package com.nexus.erp.so.report.service;

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
public class SoSalesReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getSalesExecution(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(so.order_date, '%Y-%m') AS month, " +
            "COUNT(*) AS orderCount, " +
            "SUM(so.total_amount) AS totalAmount, " +
            "SUM(CASE WHEN so.status IN ('FULFILLED', 'CLOSED') THEN 1 ELSE 0 END) AS completedCount, " +
            "ROUND(SUM(CASE WHEN so.status IN ('FULFILLED', 'CLOSED') THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate " +
            "FROM so_sales_order_header so " +
            "WHERE so.del_flag = '0' " +
            "AND so.order_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getCustomerRanking(Integer months, Integer topN) {
        int m = months != null ? months : 6;
        int n = topN != null ? topN : 10;
        String sql = "SELECT c.client_name AS clientName, " +
            "COUNT(so.order_id) AS orderCount, " +
            "SUM(so.total_amount) AS totalAmount " +
            "FROM so_sales_order_header so " +
            "LEFT JOIN bd_client c ON so.client_id = c.client_id " +
            "WHERE so.del_flag = '0' " +
            "AND so.order_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY c.client_name " +
            "ORDER BY totalAmount DESC LIMIT " + n;
        return jdbcTemplate.queryForList(sql);
    }
}
