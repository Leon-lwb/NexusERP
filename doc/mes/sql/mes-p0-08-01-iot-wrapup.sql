-- =============================================
-- NexusERP - MES P0-8 IoT 自动采集打通 收尾 SQL
-- 目标库: ry-mes (dc_collection_point) + ry-vue (sys_dict_data)
-- 创建时间: 2026-07-29
-- 说明:
--   F8-6 数据补偿: dc_collection_point 加 last_archive_time 游标列
--   F8-5 状态联动: mes_equipment_status 字典加 OFFLINE（IoT离线）
-- 幂等设计: information_schema 列存在检查 + dict NOT EXISTS + 标量子查询 MAX 分配 dict_code
-- =============================================

-- ============================================================
-- 1. ry-mes: dc_collection_point 新增 last_archive_time（数据补偿游标）
-- ============================================================
USE `ry-mes`;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_collection_point' AND COLUMN_NAME = 'last_archive_time');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_collection_point` ADD COLUMN `last_archive_time` DATETIME NULL COMMENT ''最后成功归档时间（数据补偿游标）'' AFTER `remark`',
    'SELECT ''dc_collection_point.last_archive_time 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. ry-vue: mes_equipment_status 字典加 OFFLINE（IoT离线）
--    dict_code 走标量子查询 MAX+1 动态分配（对齐 ruoyi-snowflake-dict-menu-id，避免硬编码冲突）
--    NOT EXISTS 保证可重复执行
-- ============================================================
USE `ry-vue`;

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT (SELECT IFNULL(MAX(dict_code), 0) + 1 FROM `sys_dict_data`),
       '000000', 5, 'IoT离线', 'OFFLINE', 'mes_equipment_status', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, 'IoT设备离线(状态联动任务置,P0-8)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_equipment_status' AND `dict_value` = 'OFFLINE');

-- ============================================================
-- 验证
-- ============================================================
-- DESC dc_collection_point;  -- 应包含 last_archive_time
-- SELECT dict_code, dict_sort, dict_label, dict_value FROM sys_dict_data WHERE dict_type='mes_equipment_status' ORDER BY dict_sort;
