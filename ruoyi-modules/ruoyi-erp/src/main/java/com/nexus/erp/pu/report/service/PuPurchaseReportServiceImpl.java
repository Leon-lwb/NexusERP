package com.nexus.erp.pu.report.service;

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
public class PuPurchaseReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getPurchaseExecution(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT DATE_FORMAT(po.order_date, '%Y-%m') AS month, " +
            "COUNT(*) AS orderCount, " +
            "SUM(po.total_amount) AS totalAmount, " +
            "SUM(CASE WHEN po.status IN ('RECEIVED', 'CLOSED') THEN 1 ELSE 0 END) AS completedCount, " +
            "ROUND(SUM(CASE WHEN po.status IN ('RECEIVED', 'CLOSED') THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate " +
            "FROM pu_purchase_order po " +
            "WHERE po.del_flag = '0' " +
            "AND po.order_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY month ORDER BY month";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getVendorDeliveryRate(Integer months) {
        int m = months != null ? months : 6;
        String sql = "SELECT v.vendor_name AS vendorName, " +
            "COUNT(po.order_id) AS orderCount, " +
            "SUM(CASE WHEN po.expected_arrival_date >= po.order_date THEN 1 ELSE 0 END) AS onTimeCount, " +
            "ROUND(SUM(CASE WHEN po.expected_arrival_date >= po.order_date THEN 1 ELSE 0 END) / COUNT(po.order_id) * 100, 1) AS onTimeRate " +
            "FROM pu_purchase_order po " +
            "LEFT JOIN bd_vendor v ON po.vendor_id = v.vendor_id " +
            "WHERE po.del_flag = '0' " +
            "AND po.order_date >= DATE_SUB(NOW(), INTERVAL " + m + " MONTH) " +
            "GROUP BY v.vendor_name " +
            "ORDER BY onTimeRate ASC LIMIT 20";
        return jdbcTemplate.queryForList(sql);
    }
}
