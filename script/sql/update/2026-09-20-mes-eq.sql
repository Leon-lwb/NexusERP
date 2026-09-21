-- ============================================================================
-- NexusERP MES 设备管理域（eq）修复增量脚本（2026-09-20，B-eq 批次）
-- 对应审计: doc/audit/2026-09-20-audit-mes-eq.md
--           MES-EQ-06（维修单作废 + 字典补 CANCELLED）
--           MES-EQ-05（MAINTENANCE 自动恢复，无 DDL）
--           MES-EQ-03（OEE planned_minutes / maintenance_minutes 真实口径）
-- 修复报告: doc/audit/2026-09-20-fix-mes-eq.md
-- ----------------------------------------------------------------------------
-- 用途
--   1) ry-mes：为 eq_equipment 增加设备级“计划生产时长(分钟/天)”，消除 OEE 计划时长恒为
--      480 的硬编码（两班/三班制车间可用率被系统性高估/低估）。
--   2) ry-mes：为 eq_maintenance_record 增加“保养占用时长(分钟)”，使 OEE 的
--      maintenance_minutes 由保养记录真实汇总（此前恒为 0）。
--   3) ry-vue：mes_repair_status 字典补 CANCELLED 值（EqHealthServiceImpl 早就引用了该状态，
--      但字典里从来没有，前端也无法展示）。
--   4) ry-vue：补 mes:eq:repair:cancel 按钮权限种子（新增端点
--      PUT /mes/eq/repair/cancel/{repairId} 需要该权限码，否则非超管 403）。
--
-- 执行前提
--   * 目标库：ry-mes（第 1、2 节）；ry-vue（第 3、4 节）。
--   * 仅用于存量库升级；script/sql/ry-mes.sql 与 ry-vue.sql 主脚本的等价合并由主代理串行执行
--     （见修复报告 §3「主脚本合并清单」）。
--   * 第 1、2 节必须**先于应用启动**执行：eq_equipment / eq_maintenance_record 实体已新增对应
--     字段，未加列时 MES 设备域查询会报 Unknown column。
--   * 第 4 节若目标库 sys_menu 已占用 menu_id=4127，请改用空闲 id（本脚本用 INSERT IGNORE，
--     冲突时静默跳过 → 需人工补一条按钮权限，或改由主脚本合并时分配 id）。
--   * 无需清 Redis 缓存。
--
-- 幂等性
--   * ALTER 前先查 information_schema，重复执行不会报 Duplicate column。
--   * 第 3、4 节用 INSERT IGNORE，重复执行不产生重复行。
--   * 不新增唯一索引/约束，无需存量重复数据前置检查。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) ry-mes: eq_equipment 增加 planned_minutes_per_day（MES-EQ-03 ①）
-- ----------------------------------------------------------------------------
SET @ddl_eq := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `eq_equipment` ADD COLUMN `planned_minutes_per_day` decimal(10,2) NULL DEFAULT NULL COMMENT ''计划生产时长(分钟/天), 供OEE聚合; 空则取 nexus.mes.eq.oee.planned-minutes-per-day(默认480)'' AFTER `iot_device_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_equipment'
    AND COLUMN_NAME = 'planned_minutes_per_day'
);
PREPARE stmt_eq FROM @ddl_eq;
EXECUTE stmt_eq;
DEALLOCATE PREPARE stmt_eq;

-- ----------------------------------------------------------------------------
-- 2) ry-mes: eq_maintenance_record 增加 duration_minutes（MES-EQ-03 ②）
-- ----------------------------------------------------------------------------
SET @ddl_mr := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `eq_maintenance_record` ADD COLUMN `duration_minutes` decimal(10,2) NULL DEFAULT NULL COMMENT ''保养占用时长(分钟), 供OEE的maintenance_minutes真实汇总'' AFTER `result`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_maintenance_record'
    AND COLUMN_NAME = 'duration_minutes'
);
PREPARE stmt_mr FROM @ddl_mr;
EXECUTE stmt_mr;
DEALLOCATE PREPARE stmt_mr;

-- ----------------------------------------------------------------------------
-- 3) ry-vue: mes_repair_status 字典补“已作废 / CANCELLED”（MES-EQ-06）
--    列顺序与 ry-vue.sql 既有 sys_dict_data 种子完全一致（位置化 VALUES）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES
(2075000000000000001, '000000', 4, '已作废', 'CANCELLED', 'mes_repair_status', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, 'MES-EQ-06 误报修/重复报修作废, 不再计为活跃报修');

-- ----------------------------------------------------------------------------
-- 4) ry-vue: 补 mes:eq:repair:cancel 按钮权限（挂到“维修记录”菜单 4120 下）
--    列顺序与 ry-vue.sql 既有 sys_menu 种子完全一致（位置化 VALUES）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES
(4127, '维修作废', 4120, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:repair:cancel', '#', 103, 1, NOW(), NULL, NULL, 'MES-EQ-06 维修单作废/撤销');

-- ----------------------------------------------------------------------------
-- 5) 回滚（如需）
-- ----------------------------------------------------------------------------
-- ALTER TABLE `eq_equipment` DROP COLUMN `planned_minutes_per_day`;
-- ALTER TABLE `eq_maintenance_record` DROP COLUMN `duration_minutes`;
-- DELETE FROM `ry-vue`.`sys_dict_data` WHERE `dict_code` = 2075000000000000001;
-- DELETE FROM `ry-vue`.`sys_menu` WHERE `menu_id` = 4127 AND `perms` = 'mes:eq:repair:cancel';
