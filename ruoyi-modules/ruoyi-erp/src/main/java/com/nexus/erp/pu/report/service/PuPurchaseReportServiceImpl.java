package com.nexus.erp.pu.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采购报表Service实现类
 * <p>基于 JDBC 直接聚合采购订单数据，提供采购执行率、供应商准时交货率等统计报表。属于采购管理（PU）模块。</p>
 *
 * <p><b>修复记录（2026-09-21）</b>：JdbcTemplate 原生 SQL 绕过 MyBatis-Plus 租户插件，
 * 原实现两个报表均无 {@code tenant_id} 条件 → 会把**全部租户**的采购金额与供应商准时率
 * 聚合给任意租户。现统一改为参数绑定 + 显式租户过滤，与
 * {@code SoSalesReportServiceImpl#appendTenantFilter} 保持一致。</p>
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
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(po.order_date, '%Y-%m') AS month, ")
            .append("COUNT(*) AS orderCount, ")
            .append("SUM(po.total_amount) AS totalAmount, ")
            .append("SUM(CASE WHEN po.status IN ('RECEIVED', 'CLOSED') THEN 1 ELSE 0 END) AS completedCount, ")
            .append("ROUND(SUM(CASE WHEN po.status IN ('RECEIVED', 'CLOSED') THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate ")
            .append("FROM pu_purchase_order po ")
            .append("WHERE po.del_flag = '0' ")
            .append("AND po.order_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY month ORDER BY month");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询供应商准时交货率报表：按供应商汇总订单数与准时交货数及准时率，取准时率最低的 20 名。
     *
     * @param months 统计周期（月），为空时默认 6 个月
     * @return 供应商准时交货率列表（按准时率升序）
     */
    public List<Map<String, Object>> getVendorDeliveryRate(Integer months) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT v.vendor_name AS vendorName, ")
            .append("COUNT(po.order_id) AS orderCount, ")
            .append("SUM(CASE WHEN po.expected_arrival_date >= po.order_date THEN 1 ELSE 0 END) AS onTimeCount, ")
            .append("ROUND(SUM(CASE WHEN po.expected_arrival_date >= po.order_date THEN 1 ELSE 0 END) / COUNT(po.order_id) * 100, 1) AS onTimeRate ")
            .append("FROM pu_purchase_order po ")
            .append("LEFT JOIN bd_vendor v ON po.vendor_id = v.vendor_id ")
            .append("WHERE po.del_flag = '0' ")
            .append("AND po.order_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY v.vendor_name ")
            .append("ORDER BY onTimeRate ASC LIMIT 20");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 追加租户隔离条件。
     * <p>JdbcTemplate 原生 SQL 绕过 MyBatis-Plus 租户插件，必须显式加 {@code tenant_id}，
     * 否则报表会跨租户聚合。取不到租户时不加条件 —— 与租户插件自身行为保持一致
     * （租户功能关闭或超管跨租户场景）。</p>
     */
    private void appendTenantFilter(StringBuilder sql, List<Object> params) {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            sql.append("AND po.tenant_id = ? ");
            params.add(tenantId);
        } else {
            log.debug("采购报表未取到租户上下文，跳过 tenant_id 过滤");
        }
    }

    /** 归一化统计周期：null 取默认 6 个月，并收敛到 [1, 60]，避免超大范围全表扫描 */
    private int normalizeMonths(Integer months) {
        int m = months == null ? 6 : months;
        if (m < 1) {
            m = 1;
        }
        return Math.min(m, 60);
    }
}
