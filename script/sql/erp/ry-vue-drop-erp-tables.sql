-- =============================================
-- 清理脚本: 删除 ry-vue 库中的 ERP 业务表
-- 逆向外键依赖顺序删除（有子表依赖的先删子表）
-- 请确认数据已迁移至 ry-erp 后再执行！
-- =============================================

USE `ry-vue`;

-- ========== Level 6（叶子节点，先删） ==========
DROP TABLE IF EXISTS `wm_material_stock_tx`;

-- ========== Level 5 ==========
DROP TABLE IF EXISTS `prd_material_requirement_detail`;
DROP TABLE IF EXISTS `prd_work_order_resource`;
DROP TABLE IF EXISTS `prd_work_report`;
DROP TABLE IF EXISTS `wm_sales_issue_line`;
DROP TABLE IF EXISTS `wm_material_stock`;
DROP TABLE IF EXISTS `wm_material_receive_line`;
DROP TABLE IF EXISTS `wm_material_receive_detail`;

-- ========== Level 4 ==========
DROP TABLE IF EXISTS `prd_material_requirement`;
DROP TABLE IF EXISTS `prd_work_order`;
DROP TABLE IF EXISTS `wm_sales_issue`;
DROP TABLE IF EXISTS `wm_material_receive`;
DROP TABLE IF EXISTS `so_sales_order_line`;
DROP TABLE IF EXISTS `sch_plan_team`;

-- ========== Level 3 ==========
DROP TABLE IF EXISTS `prd_production_plan`;
DROP TABLE IF EXISTS `wm_arrival_notice_line`;
DROP TABLE IF EXISTS `so_sales_order_header`;
DROP TABLE IF EXISTS `sch_employee_schedule`;
DROP TABLE IF EXISTS `sch_plan`;
DROP TABLE IF EXISTS `bd_bom_detail`;

-- ========== Level 2 ==========
DROP TABLE IF EXISTS `wm_arrival_notice`;
DROP TABLE IF EXISTS `so_customer_order`;
DROP TABLE IF EXISTS `wm_storage_location`;
DROP TABLE IF EXISTS `sch_attendance`;
DROP TABLE IF EXISTS `sch_team_schedule`;
DROP TABLE IF EXISTS `sch_team_member`;
DROP TABLE IF EXISTS `bd_bom`;

-- ========== Level 1 ==========
DROP TABLE IF EXISTS `wm_storage_area`;
DROP TABLE IF EXISTS `sch_punch_rule`;
DROP TABLE IF EXISTS `sch_team`;
DROP TABLE IF EXISTS `bd_workstation`;
DROP TABLE IF EXISTS `bd_material_vendor`;
DROP TABLE IF EXISTS `bd_material`;
DROP TABLE IF EXISTS `bd_client_credit`;

-- ========== Level 0（基础表，最后删） ==========
DROP TABLE IF EXISTS `wm_warehouse`;
DROP TABLE IF EXISTS `sch_worker`;
DROP TABLE IF EXISTS `sch_shift`;
DROP TABLE IF EXISTS `sch_holiday`;
DROP TABLE IF EXISTS `bd_workshop`;
DROP TABLE IF EXISTS `bd_vendor`;
DROP TABLE IF EXISTS `bd_client`;
DROP TABLE IF EXISTS `bd_unit_of_measure`;
DROP TABLE IF EXISTS `bd_material_category`;

-- =============================================
-- 验证: 检查 ry-vue 库中是否还有 ERP 表残留
-- =============================================
SELECT 'ry-vue 清理完成，检查残留:' AS '';
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ry-vue'
  AND (
    TABLE_NAME LIKE 'bd_%' OR TABLE_NAME LIKE 'prd_%'
    OR TABLE_NAME LIKE 'sch_%' OR TABLE_NAME LIKE 'so_%'
    OR TABLE_NAME LIKE 'wm_%'
  )
ORDER BY TABLE_NAME;
