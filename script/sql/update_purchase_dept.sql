-- ----------------------------
-- Phase 4 增量脚本：采购域部门+采购员/申请人归属
-- 日期：2026-07-02
-- 说明：
--   pu_purchase_order：新增 dept_id/dept_name（采购部门）+ buyer_id/buyer_name（采购员）
--   po_requisition：新增 applicant_id（申请人，原仅有纯文本 applicant_name）
--   pu_purchase_requisition：新增 dept_id/dept_name（采购部门）
--   新列均追加在表末尾，避免扰动既有列顺序与种子数据
-- ----------------------------

-- ============ pu_purchase_order ============
ALTER TABLE `pu_purchase_order`
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '采购部门ID（关联sys_dept）',
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购部门名称',
  ADD COLUMN `buyer_id` bigint(20) NULL DEFAULT NULL COMMENT '采购员ID（关联sys_user）',
  ADD COLUMN `buyer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购员名称';

-- ============ po_requisition ============
-- applicant_id 追加在表末尾，避免依赖 update_dept_naming.sql 先把 department_name 改名为 dept_name
ALTER TABLE `po_requisition`
  ADD COLUMN `applicant_id` bigint(20) NULL DEFAULT NULL COMMENT '申请人ID（关联sys_user）';
ALTER TABLE `po_requisition` ADD INDEX `idx_po_requisition_applicant_id`(`applicant_id`) USING BTREE;

-- ============ pu_purchase_requisition ============
ALTER TABLE `pu_purchase_requisition`
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '采购部门ID（关联sys_dept）',
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购部门名称';
