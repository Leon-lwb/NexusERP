package com.nexus.erp.so.report.service;

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
 * 销售报表Service实现类
 * <p>基于 JDBC 直接聚合销售订单数据，提供销售执行率、客户销售排行等统计报表。属于销售管理（SO）模块。</p>
 *
 * <p><b>修复记录（T1.6）</b>：原实现照抄采购报表，但改名时漏改列名
 * （{@code so.order_id}/{@code so.status} 在 {@code so_sales_order_header} 中并不存在，
 * 实际列为 {@code so_id}/{@code order_status}），导致两个接口必然抛 SQLSyntaxErrorException；
 * 且原生 SQL 绕过 MyBatis-Plus 租户插件、无 tenant_id 条件，会跨租户聚合。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoSalesReportServiceImpl {

    /** 统计周期上限（月），防止传入超大范围导致全表扫描 */
    private static final int MAX_MONTHS = 60;
    /** 排行默认条数 */
    private static final int DEFAULT_TOP_N = 10;
    /** 排行上限条数 */
    private static final int MAX_TOP_N = 100;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询销售执行率报表：按月统计订单数、总金额、已完成数及完成率。
     * <p>已取消（CANCELLED）订单既不计入金额，也不计入完成率分母。</p>
     *
     * @param months 统计周期（月），为空时默认 6 个月，取值范围 [1, 60]
     * @return 按月聚合的统计结果列表
     */
    public List<Map<String, Object>> getSalesExecution(Integer months) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(so.order_date, '%Y-%m') AS month, ")
            .append("COUNT(*) AS orderCount, ")
            .append("SUM(so.total_amount) AS totalAmount, ")
            .append("SUM(CASE WHEN so.order_status IN ('FULFILLED', 'CLOSED') THEN 1 ELSE 0 END) AS completedCount, ")
            .append("ROUND(SUM(CASE WHEN so.order_status IN ('FULFILLED', 'CLOSED') THEN 1 ELSE 0 END) ")
            .append("/ COUNT(*) * 100, 1) AS completionRate ")
            .append("FROM so_sales_order_header so ")
            .append("WHERE so.del_flag = '0' ")
            .append("AND (so.order_status IS NULL OR so.order_status <> 'CANCELLED') ")
            .append("AND so.order_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY month ORDER BY month");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询客户销售排行报表：按客户汇总订单数与总金额，按金额倒序取前 N 名。
     * <p>已取消（CANCELLED）订单不参与统计。</p>
     *
     * @param months 统计周期（月），为空时默认 6 个月，取值范围 [1, 60]
     * @param topN   取前 N 名，为空时默认 10 名，取值范围 [1, 100]
     * @return 客户销售排行列表
     */
    public List<Map<String, Object>> getCustomerRanking(Integer months, Integer topN) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT c.client_name AS clientName, ")
            .append("COUNT(so.so_id) AS orderCount, ")
            .append("SUM(so.total_amount) AS totalAmount ")
            .append("FROM so_sales_order_header so ")
            .append("LEFT JOIN bd_client c ON so.client_id = c.client_id ")
            .append("WHERE so.del_flag = '0' ")
            .append("AND (so.order_status IS NULL OR so.order_status <> 'CANCELLED') ")
            .append("AND so.order_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY c.client_name ORDER BY totalAmount DESC LIMIT ?");
        params.add(normalizeTopN(topN));
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
            sql.append("AND so.tenant_id = ? ");
            params.add(tenantId);
        } else {
            log.debug("销售报表未取到租户上下文，跳过 tenant_id 过滤");
        }
    }

    /** 归一化统计周期：null 取默认 6 个月，并收敛到 [1, MAX_MONTHS] */
    private int normalizeMonths(Integer months) {
        int m = months == null ? 6 : months;
        if (m < 1) {
            m = 1;
        }
        return Math.min(m, MAX_MONTHS);
    }

    /** 归一化排行条数：null/非法取默认 10，并收敛到 [1, MAX_TOP_N] */
    private int normalizeTopN(Integer topN) {
        int n = topN == null ? DEFAULT_TOP_N : topN;
        if (n < 1) {
            n = DEFAULT_TOP_N;
        }
        return Math.min(n, MAX_TOP_N);
    }
}
