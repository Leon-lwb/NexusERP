package com.nexus.erp.prd.report.service;

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
 * 生产报表 Service 实现
 * <p>通过 JDBC 直接查询生产工单数据，提供工单完工率、生产效率等统计报表的计算。
 * 属于 PRD（生产计划）模块，数据源为 erp 库。</p>
 *
 * <p><b>修复记录（2026-09-21）</b>：JdbcTemplate 原生 SQL 绕过 MyBatis-Plus 租户插件，
 * 原实现两个报表均无 {@code tenant_id} 条件 → 会把**全部租户**的产量与完工率聚合给任意租户。
 * 现统一改为参数绑定 + 显式租户过滤，与 {@code SoSalesReportServiceImpl#appendTenantFilter} 一致。</p>
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
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(wo.plan_start_date, '%Y-%m') AS month, ")
            .append("COUNT(*) AS totalOrders, ")
            .append("SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedOrders, ")
            .append("ROUND(SUM(CASE WHEN wo.wo_status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) AS completionRate ")
            .append("FROM prd_work_order wo ")
            .append("WHERE wo.del_flag = '0' ")
            .append("AND wo.plan_start_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY month ORDER BY month");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 统计近 N 个月生产效率
     * <p>按月分组统计计划产量与完工产量，并计算生产效率（完工/计划）百分比。</p>
     *
     * @param months 统计月份数，为空时默认 6 个月
     * @return 每月计划产量、完工产量与效率百分比的明细列表
     */
    public List<Map<String, Object>> getProductionEfficiency(Integer months) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(wo.plan_start_date, '%Y-%m') AS month, ")
            .append("SUM(wo.planned_qty) AS plannedQty, ")
            .append("SUM(wo.completed_qty) AS completedQty, ")
            .append("CASE WHEN SUM(wo.planned_qty) > 0 ")
            .append("THEN ROUND(SUM(wo.completed_qty) / SUM(wo.planned_qty) * 100, 1) ")
            .append("ELSE 0 END AS efficiencyRate ")
            .append("FROM prd_work_order wo ")
            .append("WHERE wo.del_flag = '0' ")
            .append("AND wo.plan_start_date >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(normalizeMonths(months));
        appendTenantFilter(sql, params);
        sql.append("GROUP BY month ORDER BY month");
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
            sql.append("AND wo.tenant_id = ? ");
            params.add(tenantId);
        } else {
            log.debug("生产报表未取到租户上下文，跳过 tenant_id 过滤");
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
