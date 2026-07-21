package com.nexus.erp.wm.report.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 库存业务报表服务
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
            sql.append("AND ms.warehouse_id = ").append(warehouseId).append(" ");
        }
        if (categoryCode != null && !categoryCode.isEmpty()) {
            sql.append("AND mc.category_code = '").append(categoryCode).append("' ");
        }
        sql.append("GROUP BY mc.category_name ");
        sql.append("ORDER BY totalAmount DESC");
        return jdbcTemplate.queryForList(sql.toString());
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
            sql.append("AND ms.warehouse_id = ").append(warehouseId).append(" ");
        }
        sql.append("GROUP BY agingRange ");
        sql.append("ORDER BY FIELD(agingRange, '0-30天', '31-60天', '61-90天', '91-180天', '180天以上')");
        return jdbcTemplate.queryForList(sql.toString());
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
        sql.append("AND stx.create_time >= DATE_SUB(NOW(), INTERVAL ").append(m).append(" MONTH) ");
        if (categoryCode != null && !categoryCode.isEmpty()) {
            sql.append("AND mc.category_code = '").append(categoryCode).append("' ");
        }
        sql.append("GROUP BY month, mc.category_name ");
        sql.append("ORDER BY month, categoryName");
        return jdbcTemplate.queryForList(sql.toString());
    }
}
