-- =============================================================================
-- 增量脚本：IoT 安全与消息管道 P0 修复（2026-09-20）
-- 对应审计：doc/audit/2026-09-20-audit-iot.md
--   编号：IOT-MQTT-01 / IOT-MQTT-02 / IOT-MQTT-03 / IOT-MQTT-05
--         IOT-PIPE-01 / IOT-PIPE-03 / IOT-PIPE-04 / IOT-DATA-01
-- 用途：为 iot_device_data 增加幂等去重键（source_msg_id）；登记设备密钥重置按钮权限码
-- 执行前提：MySQL 8.0；请先 USE `ry-iot`（DDL 部分）；建议在低峰/停机窗口执行 ALTER
-- 影响库：`ry-iot`（DDL：加列 + 唯一索引）、`ry-vue`（DML：sys_menu 按钮权限种子）
-- 是否需清 Redis 缓存：否（无需清理；旧数据 source_msg_id 为 NULL，不影响消费端）
-- 幂等性：DDL 部分用 information_schema 守卫，可重复执行；DML 用 INSERT IGNORE，可重复执行
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 前置重复数据检查（执行前请先运行，属只读检查，不修改数据）
-- -----------------------------------------------------------------------------
-- 0.1 本次新增的唯一键是 source_msg_id（Redis Stream entry id，全局技术键）。
--     存量行该列为 NULL，MySQL 唯一索引允许多个 NULL，因此**存量数据不会冲突，无需清洗**。
--     若怀疑该列已存在且存在重复（历史遗留），先执行：
--       SELECT source_msg_id, COUNT(*) AS c
--         FROM `ry-iot`.`iot_device_data`
--        WHERE source_msg_id IS NOT NULL
--        GROUP BY source_msg_id HAVING c > 1;
--     若有结果，先按 MIN(data_id) 保留一行再建唯一键：
--       DELETE t FROM `ry-iot`.`iot_device_data` t
--         JOIN (SELECT source_msg_id, MIN(data_id) AS keep_id
--                 FROM `ry-iot`.`iot_device_data`
--                WHERE source_msg_id IS NOT NULL
--                GROUP BY source_msg_id HAVING COUNT(*) > 1) d
--           ON t.source_msg_id = d.source_msg_id AND t.data_id <> d.keep_id;
--
-- 0.2 备选方案（若改用业务键去重，则必须先清理存量重复行，否则建唯一键会失败）：
--       SELECT device_id, property_code, collect_time, COUNT(*) AS c
--         FROM `ry-iot`.`iot_device_data`
--        GROUP BY device_id, property_code, collect_time HAVING c > 1;
--     清理（按 device_id 保留最小 data_id，注意 tenant_id 已由 device_id 唯一确定）：
--       DELETE t FROM `ry-iot`.`iot_device_data` t
--         JOIN (SELECT device_id, property_code, collect_time, MIN(data_id) AS keep_id
--                 FROM `ry-iot`.`iot_device_data`
--                GROUP BY device_id, property_code, collect_time HAVING COUNT(*) > 1) d
--           ON t.device_id = d.device_id AND t.property_code = d.property_code
--          AND t.collect_time = d.collect_time AND t.data_id <> d.keep_id;
--       ALTER TABLE `ry-iot`.`iot_device_data`
--         ADD UNIQUE INDEX `uk_dev_prop_collect`(`device_id`, `property_code`, `collect_time`);
--     注：本批次采用 source_msg_id 方案（见主脚本 ry-iot.sql），故 0.2 仅为可选备选路径。

-- -----------------------------------------------------------------------------
-- 1. `ry-iot`.`iot_device_data` 增加来源消息 ID 列（IOT-PIPE-01 幂等去重键）
-- -----------------------------------------------------------------------------
USE `ry-iot`;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'ry-iot'
     AND TABLE_NAME = 'iot_device_data'
     AND COLUMN_NAME = 'source_msg_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `iot_device_data` ADD COLUMN `source_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''来源消息ID(Redis Stream entry id,幂等去重键)'' AFTER `source`',
  'SELECT ''column source_msg_id already exists'' AS msg'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. `ry-iot`.`iot_device_data` 增加唯一索引 uk_source_msg_id
-- -----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = 'ry-iot'
     AND TABLE_NAME = 'iot_device_data'
     AND INDEX_NAME = 'uk_source_msg_id'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `iot_device_data` ADD UNIQUE INDEX `uk_source_msg_id`(`source_msg_id`) USING BTREE',
  'SELECT ''index uk_source_msg_id already exists'' AS msg'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. `ry-vue`.`sys_menu` 登记「设备密钥重置」按钮权限码（IOT-MQTT-05）
--    权限码：iot:device:resetSecret，父菜单 6020（IoT 设备管理），排序 6（接在导出 6025 之后）
--    超管（role_id=1）经 SysPermissionServiceImpl 返回 *:*:*，无需 sys_role_menu 授权；
--    其他角色请在「系统管理 → 角色管理」中勾选该按钮。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`,
   `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`,
   `create_time`, `update_by`, `update_time`, `remark`)
VALUES
  (6026, '设备密钥重置', 6020, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:resetSecret', '#',
   103, 1, NOW(), NULL, NULL, '重置设备密钥(轮换 device_secret, 仅返回一次)');

-- 若存量库的 sys_menu 主键 6026 已被占用，请改用其他空闲 ID 并同步修改上面的语句。
-- 校验：
--   SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu` WHERE perms = 'iot:device:resetSecret';
