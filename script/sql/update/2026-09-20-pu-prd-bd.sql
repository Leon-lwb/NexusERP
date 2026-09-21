-- =====================================================================
-- 增量脚本: 2026-09-20-pu-prd-bd.sql
-- 用途: 采购(pu)/生产计划(prd)/基础数据(bd) 批次修复所需的 DDL
--       1) pu_purchase_order_line 新增 returned_quantity（ERP-PU-05：订单行退货数量回写，
--          净采购量 = order_quantity - returned_quantity）
--       2) bd_bom_detail.supply_type 存在性兜底（ERP-BD-01；主脚本已存在该列，此处仅幂等补建，
--          用于从更早期版本升级的存量库）
-- 执行前提: 已在 ry-erp 库执行；MySQL 8.0+（使用 information_schema + PREPARE 实现幂等）
-- 影响库: ry-erp
-- 是否需清 Redis 缓存: 否
-- 可重复执行: 是（幂等）
-- =====================================================================

USE `ry-erp`;

-- ---------------------------------------------------------------------
-- 1) pu_purchase_order_line.returned_quantity
-- 前置检查（存量数据不会导致失败，此列为新增可空列，无唯一约束）:
--   SELECT COUNT(*) FROM pu_purchase_order_line;   -- 仅用于评估锁表时长
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pu_purchase_order_line'
    AND COLUMN_NAME = 'returned_quantity'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `pu_purchase_order_line`
     ADD COLUMN `returned_quantity` decimal(18,4) NULL DEFAULT 0
     COMMENT ''已退货数量(净采购量=order_quantity-returned_quantity; 可开票口径=received_quantity-returned_quantity)''
     AFTER `invoiced_qty`',
  'SELECT ''pu_purchase_order_line.returned_quantity already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量数据初始化：已有退货单的订单行按退货明细回填（幂等：只回填仍为 0/NULL 的行）
UPDATE `pu_purchase_order_line` l
JOIN (
  SELECT rl.`order_line_id` AS order_line_id, SUM(rl.`return_quantity`) AS returned_qty
  FROM `pu_purchase_return_line` rl
  JOIN `pu_purchase_return` r ON r.`return_id` = rl.`return_id`
  WHERE rl.`order_line_id` IS NOT NULL
    AND rl.`del_flag` = '0'
    AND r.`status` = 'EXECUTED'
    AND r.`del_flag` = '0'
  GROUP BY rl.`order_line_id`
) x ON x.order_line_id = l.`line_id`
SET l.`returned_quantity` = x.returned_qty
WHERE IFNULL(l.`returned_quantity`, 0) = 0;

-- ---------------------------------------------------------------------
-- 2) bd_bom_detail.supply_type 兜底（主脚本 ry-erp.sql:419 已有该列）
-- 前置检查: SELECT COUNT(*) FROM bd_bom_detail;   -- 评估锁表时长
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'bd_bom_detail'
    AND COLUMN_NAME = 'supply_type'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `bd_bom_detail`
     ADD COLUMN `supply_type` varchar(32) NULL DEFAULT ''SELF_PROVIDED''
     COMMENT ''供应方: SELF_PROVIDED自备/VENDOR_PROVIDED外协厂供/CUSTOMER_PROVIDED客供(委外快照用)''',
  'SELECT ''bd_bom_detail.supply_type already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 说明: 以下内容不在本脚本内执行，需由主代理串行合并（见修复报告 §3/§4）
--   * sys_dict_data 新增字典类型 bd_supply_type（SELF_PROVIDED/VENDOR_PROVIDED/CUSTOMER_PROVIDED）
--     → 目标文件 script/sql/ry-vue.sql（菜单/字典主脚本），属前端批次职责
-- =====================================================================

-- =============================================================================
-- 4) ry-vue：字典 bd_supply_type（BOM 明细「供应方」：自备/外协厂供/客供）—— ERP-BD-01
--    说明：后端 BdBomDetailBo/Vo.supplyType 已打通，前端下拉依赖本字典；
--          早期版本本脚本仅在注释中提及、未实际插入，2026-09-20 补齐。
--    幂等：INSERT IGNORE（按主键去重）
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_dict_type`
  (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600260, '000000', 'BOM供应方', 'bd_supply_type', 103, 1, NOW(), 'BOM明细供应方（自备/外协厂供/客供）');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data`
  (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600261, '000000', 1, '自备', 'SELF_PROVIDED', 'bd_supply_type', '', 'primary', 'Y', 103, 1, NOW(), '我方提供'),
       (600262, '000000', 2, '外协厂供', 'VENDOR_PROVIDED', 'bd_supply_type', '', 'warning', 'N', 103, 1, NOW(), '外协厂提供'),
       (600263, '000000', 3, '客供', 'CUSTOMER_PROVIDED', 'bd_supply_type', '', 'info', 'N', 103, 1, NOW(), '客户提供');