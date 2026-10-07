package com.nexus.ai.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.common.ai.domain.entity.AiSqlExample;
import com.nexus.common.ai.mapper.AiSqlExampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SQL Few-Shot 示例初始化器
 * <p>应用启动时自动插入默认 SQL 示例（如果不存在）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSqlExampleInitializer implements ApplicationRunner {

    /** SQL 示例持久层 Mapper，用于统计现有示例数量并执行插入。 */
    private final AiSqlExampleMapper sqlExampleMapper;

    /**
     * 应用启动时执行，当表为空时插入库存、销售、采购、生产、通用等默认 SQL 示例。
     *
     * @param args Spring 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        initDefaultExamples();
    }

    /**
     * 初始化默认 SQL 示例
     * <p>仅在表中无数据时插入，避免重复</p>
     */
    private void initDefaultExamples() {
        Long count = sqlExampleMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            log.info("[AI-SqlExample] 已有 {} 条示例数据，跳过初始化", count);
            return;
        }

        // 库存相关示例
        insertExample("INVENTORY", "查询库存低于安全库存的物料",
            "SELECT m.material_code, m.material_name, s.stock_quantity, m.safety_stock " +
            "FROM wm_material_stock s JOIN bd_material m ON s.material_id = m.material_id " +
            "WHERE s.stock_quantity < m.safety_stock", 1);

        insertExample("INVENTORY", "查询各仓库的库存总金额",
            "SELECT w.warehouse_name, SUM(s.stock_quantity * s.unit_cost) AS total_value " +
            "FROM wm_material_stock s JOIN wm_warehouse w ON s.warehouse_id = w.warehouse_id " +
            "GROUP BY w.warehouse_name", 2);

        // 销售相关示例
        insertExample("SALES", "查询本月销售总额",
            "SELECT SUM(total_amount) AS total_sales FROM so_sales_order_header " +
            "WHERE DATE_FORMAT(order_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') " +
            "AND order_status IN ('CONFIRMED','FULFILLED')", 3);

        insertExample("SALES", "查询客户销售排行TOP10",
            "SELECT c.client_name, SUM(h.total_amount) AS total_amount " +
            "FROM so_sales_order_header h JOIN bd_client c ON h.client_id = c.client_id " +
            "WHERE h.order_status IN ('CONFIRMED','FULFILLED') " +
            "GROUP BY c.client_name ORDER BY total_amount DESC LIMIT 10", 4);

        // 采购相关示例
        insertExample("PURCHASE", "查询本月采购总额",
            "SELECT SUM(total_amount) AS total_purchase FROM pu_purchase_order " +
            "WHERE DATE_FORMAT(order_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') " +
            "AND status IN ('APPROVED','COMPLETED')", 5);

        insertExample("PURCHASE", "查询供应商交货准时率",
            "SELECT v.vendor_name, ROUND(e.on_time_orders * 100.0 / NULLIF(e.total_orders, 0), 1) AS on_time_rate " +
            "FROM pu_vendor_evaluation e JOIN bd_vendor v ON e.vendor_id = v.vendor_id", 6);

        // 生产相关示例
        insertExample("PRODUCTION", "查询当前在产工单",
            "SELECT wo.wo_no, m.material_name, wo.planned_qty, wo.completed_qty, wo.wo_status " +
            "FROM prd_work_order wo JOIN bd_material m ON wo.material_id = m.material_id " +
            "WHERE wo.wo_status IN ('IN_PROGRESS','STARTED')", 7);

        // 通用示例
        insertExample("GENERAL", "查询指定物料的详细信息",
            "SELECT m.material_code, m.material_name, mc.category_name, m.material_spec, " +
            "m.safety_stock, m.standard_cost " +
            "FROM bd_material m LEFT JOIN bd_material_category mc ON m.category_id = mc.category_id " +
            "WHERE m.material_code = ?", 8);

        log.info("[AI-SqlExample] 默认示例初始化完成，共插入 8 条数据");
    }

    /**
     * 插入单条默认示例
     *
     * @param category    业务分类
     * @param question    用户问题
     * @param sqlTemplate SQL 模板
     * @param sortOrder   排序号
     */
    private void insertExample(String category, String question, String sqlTemplate, int sortOrder) {
        AiSqlExample example = new AiSqlExample();
        example.setTenantId("0");
        example.setCategory(category);
        example.setQuestion(question);
        example.setSqlTemplate(sqlTemplate);
        example.setSortOrder(sortOrder);
        example.setStatus(AiToolStatusContract.ACTIVE);
        sqlExampleMapper.insert(example);
    }
}
