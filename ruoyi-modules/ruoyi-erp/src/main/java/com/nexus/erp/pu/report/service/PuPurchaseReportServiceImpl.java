package com.nexus.erp.pu.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 采购报表Service实现类
 * <p>基于 JDBC 直接聚合采购订单数据，提供采购执行率、供应商准时交货率等统计报表。属于采购管理（PU）模块。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuPurchaseReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询采购执行率报表：按月统计订单数、总金额、已收货数及完成率。
     *
     * @param months 统计周期（月），为空时默认 6 个月
     * @return 按月聚合的统计结果列表
     */
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

    /**
     * 查询供应商准时交货率报表：按供应商汇总订单数与准时交货数及准时率，取准时率最低的 20 名。
     *
     * @param months 统计周期（月），为空时默认 6 个月
     * @return 供应商准时交货率列表（按准时率升序）
     */
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
