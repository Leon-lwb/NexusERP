-- ============================================================
-- F3-a 存货成本(收敛版) - 增量 DDL + 菜单
-- 目标库: ry-erp (业务表 DDL) / ry-vue (菜单)
-- 所属: NexusERP-SPEC-2026-007-F3A
-- 日期: 2026-08-01
-- 说明: wm_material_stock_tx 追加成本过账标记(posted/posted_voucher_no)
--       wm_material_stock 追加物料类型冗余(material_type,成本科目映射)
--       菜单 6176-6178(fin:cost 权限)
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. wm_material_stock_tx 成本过账标记(INPLACE;重复执行报 Duplicate column 属正常,已应用)
-- ------------------------------------------------------------
ALTER TABLE `wm_material_stock_tx`
  ADD COLUMN `posted` tinyint(1) NULL DEFAULT 0 COMMENT '成本过账标记 0-未过账 1-已过账' AFTER `tx_amount`,
  ADD COLUMN `posted_voucher_no` varchar(50) NULL DEFAULT NULL COMMENT '成本过账凭证号' AFTER `posted`;

-- ------------------------------------------------------------
-- 2. wm_material_stock 物料类型冗余(INPLACE;成本科目映射)
-- ------------------------------------------------------------
ALTER TABLE `wm_material_stock`
  ADD COLUMN `material_type` bigint(20) NULL DEFAULT NULL COMMENT '物料类型(冗余,成本科目映射)1-原材料2-半成品3-成品4-辅料' AFTER `material_id`;

-- ------------------------------------------------------------
-- 3. 菜单(ry-vue):成本结转 6176 + 按钮
-- ------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(6176, '成本结转', 6100, 6, 'cost', 'erp/fin/cost/index', NULL, 1, 0, 'C', '0', '0', 'fin:cost:list', 'money', 103, 1, NOW(), NULL, NULL, '存货成本过账/暂估'),
(6177, '成本过账', 6176, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:cost:post', '#', 103, 1, NOW(), NULL, NULL, '按库存流水过账'),
(6178, '采购暂估', 6176, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:cost:provision', '#', 103, 1, NOW(), NULL, NULL, '期间暂估入账');

-- 验证
-- SELECT column_name FROM information_schema.columns WHERE table_schema='ry-erp' AND table_name='wm_material_stock_tx' AND column_name IN ('posted','posted_voucher_no');
-- SELECT menu_id, menu_name, perms FROM `ry-vue`.sys_menu WHERE `perms` LIKE 'fin:cost:%' ORDER BY menu_id;
