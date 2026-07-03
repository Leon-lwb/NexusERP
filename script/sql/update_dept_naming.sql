-- ----------------------------
-- Phase 2 增量脚本：部门字段命名统一为 dept_id / dept_name
-- 日期：2026-07-02
-- 说明：
--   将 4 张表的部门字段统一为 dept_id / dept_name，与 sys_dept 主键、bd_workshop.dept_id 命名一致。
--   纯重命名/新增列，不改业务逻辑。配合 Java 实体/BO/VO/Service 同步改名。
--
--   prd_production_plan：department_id→dept_id, department_name→dept_name
--   prd_material_requirement：同上（与生产计划有数据拷贝耦合，必须同步改）
--   so_sales_order_header：sales_org_id→dept_id（语义化为部门），新增 dept_name
--   po_requisition：department_name→dept_name，新增 dept_id
-- ----------------------------

-- ============ prd_production_plan ============
ALTER TABLE `prd_production_plan`
  CHANGE COLUMN `department_id` `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '生产部门ID',
  CHANGE COLUMN `department_name` `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产部门';

-- ============ prd_material_requirement ============
ALTER TABLE `prd_material_requirement`
  CHANGE COLUMN `department_id` `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '部门ID',
  CHANGE COLUMN `department_name` `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门';

-- ============ so_sales_order_header ============
ALTER TABLE `so_sales_order_header`
  CHANGE COLUMN `sales_org_id` `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '销售部门ID（关联sys_dept）';
-- dept_name 追加在表末尾，避免扰动既有列顺序与种子数据
ALTER TABLE `so_sales_order_header`
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售部门名称';

-- ============ po_requisition ============
ALTER TABLE `po_requisition`
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '部门ID（关联sys_dept）' AFTER `status`,
  CHANGE COLUMN `department_name` `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门名称';
-- 重建部门索引（原 idx_po_requisition_department 建在 department_name 上）
ALTER TABLE `po_requisition` DROP INDEX `idx_po_requisition_department`;
ALTER TABLE `po_requisition` ADD INDEX `idx_po_requisition_dept`(`dept_id`) USING BTREE;
