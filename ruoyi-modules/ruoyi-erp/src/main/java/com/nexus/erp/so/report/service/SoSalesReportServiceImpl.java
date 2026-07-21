package com.nexus.erp.so.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 销售报表Service实现类
 * <p>基于 JDBC 直接聚合销售订单数据，提供销售执行率、客户销售排行等统计报表。属于销售管理（SO）模块。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoSalesReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询销售执行率报表：按月统计订单数、总金额、已完成数及完成率。
     *
     * @param months 统计周期（月），为空时默认 6 个月
     * @return 按月聚合的统计结果列表
     */
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

    /**
     * 查询客户销售排行报表：按客户汇总订单数与总金额，按金额倒序取前 N 名。
     *
     * @param months 统计周期（月），为空时默认 6 个月
     * @param topN   取前 N 名，为空时默认 10 名
     * @return 客户销售排行列表
     */
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
