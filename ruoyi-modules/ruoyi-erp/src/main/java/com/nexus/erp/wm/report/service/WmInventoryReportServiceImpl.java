package com.nexus.erp.wm.report.service;

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
 * 库存业务报表服务
 *
 * <p><b>修复记录（2026-09-21）</b>：JdbcTemplate 原生 SQL 绕过 MyBatis-Plus 租户插件，
 * 原实现三个报表均无 {@code tenant_id} 条件 → 会把**全部租户**的库存金额/库龄/周转率聚合给
 * 任意租户；且 {@code categoryCode} 由字符串拼接进 SQL（控制器 {@code @RequestParam} 直传）
 * → SQL 注入。现统一改为参数绑定 + 显式租户过滤，与
 * {@code SoSalesReportServiceImpl#appendTenantFilter} 保持一致。</p>
 *
 * @author nexus
 * @date 2026-06-15
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmInventoryReportServiceImpl {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 库存汇总报表（按物料分类维度）。
     * <p>按物料分类聚合库存种类数、库存总量与库存金额，支持按仓库与分类筛选。</p>
     *
     * @param warehouseId 仓库ID，为空时统计全部仓库
     * @param categoryCode 物料分类编码，为空时统计全部分类
     * @return 各分类维度的库存汇总数据列表
     */
    public List<Map<String, Object>> getInventorySummary(Long warehouseId, String categoryCode) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT mc.category_name AS categoryName, ");
        sql.append("COUNT(DISTINCT ms.material_id) AS materialCount, ");
        sql.append("SUM(ms.on_hand_qty) AS totalQty, ");
        sql.append("SUM(ms.on_hand_qty * ms.unit_cost) AS totalAmount ");
        sql.append("FROM wm_material_stock ms ");
        sql.append("LEFT JOIN bd_material m ON ms.material_id = m.material_id ");
        sql.append("LEFT JOIN bd_material_category mc ON m.category_id = mc.category_id ");
        sql.append("WHERE ms.del_flag = '0' AND ms.on_hand_qty > 0 ");
        if (warehouseId != null) {
            sql.append("AND ms.warehouse_id = ? ");
            params.add(warehouseId);
        }
        if (StringUtils.isNotBlank(categoryCode)) {
            sql.append("AND mc.category_code = ? ");
            params.add(categoryCode);
        }
        appendTenantFilter(sql, params, "ms");
        sql.append("GROUP BY mc.category_name ");
        sql.append("ORDER BY totalAmount DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 库龄分析报表。
     * <p>按库存创建时间划分 0-30/31-60/61-90/91-180/180天以上 区间，统计各区间SKU数与库存量。</p>
     *
     * @param warehouseId 仓库ID，为空时统计全部仓库
     * @param daysThreshold 库龄阈值（天），为空时默认 90 天
     * @return 各库龄区间的库存分析数据列表
     */
    public List<Map<String, Object>> getInventoryAging(Long warehouseId, Integer daysThreshold) {
        int threshold = daysThreshold != null ? daysThreshold : 90;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT CASE ");
        sql.append("WHEN DATEDIFF(NOW(), ms.create_time) <= 30 THEN '0-30天' ");
        sql.append("WHEN DATEDIFF(NOW(), ms.create_time) <= 60 THEN '31-60天' ");
        sql.append("WHEN DATEDIFF(NOW(), ms.create_time) <= 90 THEN '61-90天' ");
        sql.append("WHEN DATEDIFF(NOW(), ms.create_time) <= 180 THEN '91-180天' ");
        sql.append("ELSE '180天以上' END AS agingRange, ");
        sql.append("COUNT(*) AS skuCount, ");
        sql.append("SUM(ms.on_hand_qty) AS totalQty, ");
        sql.append("SUM(ms.on_hand_qty * ms.unit_cost) AS totalAmount ");
        sql.append("FROM wm_material_stock ms ");
        sql.append("WHERE ms.del_flag = '0' AND ms.on_hand_qty > 0 ");
        if (warehouseId != null) {
            sql.append("AND ms.warehouse_id = ? ");
            params.add(warehouseId);
        }
        appendTenantFilter(sql, params, "ms");
        sql.append("GROUP BY agingRange ");
        sql.append("ORDER BY FIELD(agingRange, '0-30天', '31-60天', '61-90天', '91-180天', '180天以上')");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 库存周转率报表。
     * <p>按月份与物料分类统计出库数量与平均库存，计算库存周转率。</p>
     *
     * @param categoryCode 物料分类编码，为空时统计全部分类
     * @param months 统计月份数，为空时默认 6 个月
     * @return 各月份与分类维度的周转率数据列表
     */
    public List<Map<String, Object>> getInventoryTurnover(String categoryCode, Integer months) {
        int m = months != null ? months : 6;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATE_FORMAT(stx.create_time, '%Y-%m') AS month, ");
        sql.append("mc.category_name AS categoryName, ");
        sql.append("SUM(CASE WHEN stx.tx_type = 'OUT' THEN stx.tx_qty ELSE 0 END) AS outQty, ");
        sql.append("AVG(ms.on_hand_qty) AS avgStock, ");
        sql.append("CASE WHEN AVG(ms.on_hand_qty) > 0 ");
        sql.append("THEN SUM(CASE WHEN stx.tx_type = 'OUT' THEN stx.tx_qty ELSE 0 END) / AVG(ms.on_hand_qty) ");
        sql.append("ELSE 0 END AS turnoverRate ");
        sql.append("FROM wm_material_stock_tx stx ");
        sql.append("LEFT JOIN bd_material m ON stx.material_id = m.material_id ");
        sql.append("LEFT JOIN bd_material_category mc ON m.category_id = mc.category_id ");
        sql.append("LEFT JOIN wm_material_stock ms ON stx.material_id = ms.material_id ");
        sql.append("WHERE stx.del_flag = '0' ");
        sql.append("AND stx.create_time >= DATE_SUB(NOW(), INTERVAL ? MONTH) ");
        params.add(m);
        if (StringUtils.isNotBlank(categoryCode)) {
            sql.append("AND mc.category_code = ? ");
            params.add(categoryCode);
        }
        appendTenantFilter(sql, params, "stx");
        sql.append("GROUP BY month, mc.category_name ");
        sql.append("ORDER BY month, categoryName");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 追加租户隔离条件。
     * <p>JdbcTemplate 原生 SQL 绕过 MyBatis-Plus 租户插件，必须显式加 {@code tenant_id}，
     * 否则报表会跨租户聚合。取不到租户时不加条件 —— 与租户插件自身行为保持一致
     * （租户功能关闭或超管跨租户场景）。</p>
     *
     * @param sql    正在构造的 SQL
     * @param params 参数列表
     * @param alias  需要限定 tenant_id 的表别名（内部常量，非用户输入）
     */
    private void appendTenantFilter(StringBuilder sql, List<Object> params, String alias) {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            sql.append("AND ").append(alias).append(".tenant_id = ? ");
            params.add(tenantId);
        } else {
            log.debug("库存报表未取到租户上下文，跳过 tenant_id 过滤");
        }
    }
}
