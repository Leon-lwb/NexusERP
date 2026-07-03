-- ----------------------------
-- Phase 5 增量脚本：仓储 + 客商组织归属
-- 日期：2026-07-02
-- 说明：
--   wm_warehouse：新增 dept_id/dept_name（仓库归属部门）
--   bd_client：新增 sales_rep_id/sales_rep_name（业务员）+ dept_id/dept_name（归属部门）
--   bd_vendor：新增 buyer_id/buyer_name（采购员）+ dept_id/dept_name（归属部门）
--   新列均追加在表末尾，避免扰动既有列顺序与种子数据
-- ----------------------------

-- ============ wm_warehouse ============
ALTER TABLE `wm_warehouse`
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称';

-- ============ bd_client ============
ALTER TABLE `bd_client`
  ADD COLUMN `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '业务员ID（关联sys_user）',
  ADD COLUMN `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务员名称',
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称';

-- ============ bd_vendor ============
ALTER TABLE `bd_vendor`
  ADD COLUMN `buyer_id` bigint(20) NULL DEFAULT NULL COMMENT '采购员ID（关联sys_user）',
  ADD COLUMN `buyer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购员名称',
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称';
