-- =============================================
-- NexusERP - MES Week 28 IoT Bridge 扩展字段
-- 目标库: ry-mes
-- 创建时间: 2026-06-26
-- 说明: 为 Wave1 Task 3.2「IoT 自动采集归档」预留字段
--       幂等设计：使用 information_schema 检查列是否存在
-- =============================================

USE `ry-mes`;

-- ============================================================
-- 1. dc_collection_point 新增 iot_device_id / iot_property_code
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_collection_point' AND COLUMN_NAME = 'iot_device_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_collection_point` ADD COLUMN `iot_device_id` BIGINT NULL COMMENT ''关联 IoT 设备 ID（AUTO 类型必填）'' AFTER `equipment_id`',
    'SELECT ''dc_collection_point.iot_device_id 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_collection_point' AND COLUMN_NAME = 'iot_property_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_collection_point` ADD COLUMN `iot_property_code` VARCHAR(64) NULL COMMENT ''IoT 属性编码（对应 iot_device_data.property_code）'' AFTER `iot_device_id`',
    'SELECT ''dc_collection_point.iot_property_code 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. dc_data_record 新增 property_code
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_data_record' AND COLUMN_NAME = 'property_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_data_record` ADD COLUMN `property_code` VARCHAR(64) NULL COMMENT ''IoT 属性编码（source=IoT 时填充）'' AFTER `point_code`',
    'SELECT ''dc_data_record.property_code 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. dc_data_record 去重唯一索引（point_id + collect_time）
--    用于 IoT 归档任务幂等：同一采集点同一时间只保留一条
-- ============================================================
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_data_record' AND INDEX_NAME = 'uk_point_collect_time');
SET @sql = IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_point_collect_time` ON `dc_data_record` (`point_id`, `collect_time`)',
    'SELECT ''dc_data_record.uk_point_collect_time 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 验证
-- ============================================================
-- DESC dc_collection_point;  -- 应包含 iot_device_id / iot_property_code
-- DESC dc_data_record;       -- 应包含 property_code
-- SHOW INDEX FROM dc_data_record WHERE Key_name = 'uk_point_collect_time';
