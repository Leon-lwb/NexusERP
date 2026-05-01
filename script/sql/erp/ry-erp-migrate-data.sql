-- =============================================
-- 数据迁移脚本: ry-vue → ry-erp
-- 将 ERP 业务表数据从 ry-vue 库迁移到 ry-erp 库
-- 按外键依赖顺序排列
-- =============================================

-- 确认 ry-erp 库中已有表结构
-- 如果表不存在，先执行 ry-erp-init.sql

-- ========== Level 0: 无外键依赖 ==========

INSERT INTO `ry-erp`.`bd_material_category`
SELECT * FROM `ry-vue`.`bd_material_category`;

INSERT INTO `ry-erp`.`bd_unit_of_measure`
SELECT * FROM `ry-vue`.`bd_unit_of_measure`;

INSERT INTO `ry-erp`.`bd_client`
SELECT * FROM `ry-vue`.`bd_client`;

INSERT INTO `ry-erp`.`bd_vendor`
SELECT * FROM `ry-vue`.`bd_vendor`;

INSERT INTO `ry-erp`.`bd_workshop`
SELECT * FROM `ry-vue`.`bd_workshop`;

INSERT INTO `ry-erp`.`sch_shift`
SELECT * FROM `ry-vue`.`sch_shift`;

INSERT INTO `ry-erp`.`sch_worker`
SELECT * FROM `ry-vue`.`sch_worker`;

INSERT INTO `ry-erp`.`wm_warehouse`
SELECT * FROM `ry-vue`.`wm_warehouse`;

INSERT INTO `ry-erp`.`sch_holiday`
SELECT * FROM `ry-vue`.`sch_holiday`;

-- ========== Level 1: 依赖 Level 0 ==========

INSERT INTO `ry-erp`.`bd_client_credit`
SELECT * FROM `ry-vue`.`bd_client_credit`;

INSERT INTO `ry-erp`.`bd_material`
SELECT * FROM `ry-vue`.`bd_material`;

INSERT INTO `ry-erp`.`bd_material_vendor`
SELECT * FROM `ry-vue`.`bd_material_vendor`;

INSERT INTO `ry-erp`.`bd_workstation`
SELECT * FROM `ry-vue`.`bd_workstation`;

INSERT INTO `ry-erp`.`sch_team`
SELECT * FROM `ry-vue`.`sch_team`;

INSERT INTO `ry-erp`.`sch_punch_rule`
SELECT * FROM `ry-vue`.`sch_punch_rule`;

INSERT INTO `ry-erp`.`wm_storage_area`
SELECT * FROM `ry-vue`.`wm_storage_area`;

-- ========== Level 2 ==========

INSERT INTO `ry-erp`.`bd_bom`
SELECT * FROM `ry-vue`.`bd_bom`;

INSERT INTO `ry-erp`.`sch_team_member`
SELECT * FROM `ry-vue`.`sch_team_member`;

INSERT INTO `ry-erp`.`sch_team_schedule`
SELECT * FROM `ry-vue`.`sch_team_schedule`;

INSERT INTO `ry-erp`.`sch_attendance`
SELECT * FROM `ry-vue`.`sch_attendance`;

INSERT INTO `ry-erp`.`wm_storage_location`
SELECT * FROM `ry-vue`.`wm_storage_location`;

INSERT INTO `ry-erp`.`so_customer_order`
SELECT * FROM `ry-vue`.`so_customer_order`;

INSERT INTO `ry-erp`.`wm_arrival_notice`
SELECT * FROM `ry-vue`.`wm_arrival_notice`;

-- ========== Level 3 ==========

INSERT INTO `ry-erp`.`bd_bom_detail`
SELECT * FROM `ry-vue`.`bd_bom_detail`;

INSERT INTO `ry-erp`.`sch_plan`
SELECT * FROM `ry-vue`.`sch_plan`;

INSERT INTO `ry-erp`.`sch_employee_schedule`
SELECT * FROM `ry-vue`.`sch_employee_schedule`;

INSERT INTO `ry-erp`.`so_sales_order_header`
SELECT * FROM `ry-vue`.`so_sales_order_header`;

INSERT INTO `ry-erp`.`wm_arrival_notice_line`
SELECT * FROM `ry-vue`.`wm_arrival_notice_line`;

INSERT INTO `ry-erp`.`prd_production_plan`
SELECT * FROM `ry-vue`.`prd_production_plan`;

-- ========== Level 4 ==========

INSERT INTO `ry-erp`.`sch_plan_team`
SELECT * FROM `ry-vue`.`sch_plan_team`;

INSERT INTO `ry-erp`.`so_sales_order_line`
SELECT * FROM `ry-vue`.`so_sales_order_line`;

INSERT INTO `ry-erp`.`wm_material_receive`
SELECT * FROM `ry-vue`.`wm_material_receive`;

INSERT INTO `ry-erp`.`wm_sales_issue`
SELECT * FROM `ry-vue`.`wm_sales_issue`;

INSERT INTO `ry-erp`.`prd_work_order`
SELECT * FROM `ry-vue`.`prd_work_order`;

INSERT INTO `ry-erp`.`prd_material_requirement`
SELECT * FROM `ry-vue`.`prd_material_requirement`;

-- ========== Level 5 ==========

INSERT INTO `ry-erp`.`wm_material_receive_detail`
SELECT * FROM `ry-vue`.`wm_material_receive_detail`;

INSERT INTO `ry-erp`.`wm_material_receive_line`
SELECT * FROM `ry-vue`.`wm_material_receive_line`;

INSERT INTO `ry-erp`.`wm_material_stock`
SELECT * FROM `ry-vue`.`wm_material_stock`;

INSERT INTO `ry-erp`.`wm_sales_issue_line`
SELECT * FROM `ry-vue`.`wm_sales_issue_line`;

INSERT INTO `ry-erp`.`prd_work_order_resource`
SELECT * FROM `ry-vue`.`prd_work_order_resource`;

INSERT INTO `ry-erp`.`prd_work_report`
SELECT * FROM `ry-vue`.`prd_work_report`;

INSERT INTO `ry-erp`.`prd_material_requirement_detail`
SELECT * FROM `ry-vue`.`prd_material_requirement_detail`;

-- ========== Level 6 ==========

INSERT INTO `ry-erp`.`wm_material_stock_tx`
SELECT * FROM `ry-vue`.`wm_material_stock_tx`;

-- =============================================
-- 验证数据行数
-- =============================================
SELECT 'ry-erp 迁移完成，各表行数:' AS '';
SELECT TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ry-erp'
  AND TABLE_NAME LIKE 'bd_%' OR TABLE_NAME LIKE 'prd_%'
  OR TABLE_NAME LIKE 'sch_%' OR TABLE_NAME LIKE 'so_%'
  OR TABLE_NAME LIKE 'wm_%'
ORDER BY TABLE_NAME;
