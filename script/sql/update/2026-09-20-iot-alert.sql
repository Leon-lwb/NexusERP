-- =============================================================================
-- 增量脚本：IoT 告警落库 + 确认/恢复状态机 + 规则 CRUD（2026-09-20）
-- 对应审计：doc/audit/2026-09-20-audit-iot.md
--   编号：IOT-ALERT-01（告警不落库、无确认/恢复，离线用户完全丢失）
--         IOT-ALERT-02（规则硬编码 YAML，无 CRUD/持续时长/去抖/不分租户）
-- 用途：1) ry-iot 新增 iot_alert_rule / iot_alert_record / iot_alert_notify_log 三表
--       2) ry-vue.sys_menu 登记告警与规则的页面/按钮权限码（6030~6034、6040~6045）
-- 执行前提：MySQL 8.0；请先 USE `ry-iot`（DDL 部分）；三张表为新增表，存量数据无冲突
-- 影响库：`ry-iot`（DDL：3 张新表 + 2 条兜底规则种子）、`ry-vue`（DML：sys_menu 种子）
-- 是否需清 Redis 缓存：是（若此前已跑过旧版告警逻辑，建议 DEL iot:alert:* 清理限频/去抖键）
-- 幂等性：DDL 用 CREATE TABLE IF NOT EXISTS，DML 用 INSERT IGNORE，可重复执行
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 前置检查（只读，不修改数据）
-- -----------------------------------------------------------------------------
-- 0.1 确认三张表尚不存在：
--   SELECT TABLE_NAME FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = 'ry-iot'
--      AND TABLE_NAME IN ('iot_alert_rule', 'iot_alert_record', 'iot_alert_notify_log');
-- 0.2 确认 sys_menu 主键 6030~6034 / 6040~6045 未被占用：
--   SELECT menu_id, menu_name FROM `ry-vue`.`sys_menu`
--    WHERE menu_id BETWEEN 6030 AND 6045;
--   若已被占用，请改用其他空闲 ID 并同步修改第 3 节语句与后端 @SaCheckPermission 权限码无关联（权限码不依赖 ID）。
-- 0.3 iot_alert_record 的唯一键 uk_active_alert(tenant_id, device_id, property_code, active_flag)
--     依赖 active_flag：未恢复=1、已恢复=NULL。MySQL 唯一索引允许多个 NULL，
--     因此"同一设备+属性仅一条未恢复告警、多条历史告警"可同时成立，无需清洗存量数据。

-- -----------------------------------------------------------------------------
-- 1. `ry-iot`.`iot_alert_rule` 告警规则表（IOT-ALERT-02）
-- -----------------------------------------------------------------------------
USE `ry-iot`;

CREATE TABLE IF NOT EXISTS `iot_alert_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '规则名称',
  `product_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '产品范围(0=不限)',
  `device_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '设备范围(0=不限)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '属性编码',
  `compare_operator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GT' COMMENT '比较符: GT/GE/LT/LE/EQ/NE',
  `threshold` decimal(14, 4) NOT NULL DEFAULT 0.0000 COMMENT '阈值',
  `duration_sec` int(11) NOT NULL DEFAULT 0 COMMENT '持续时长(秒): 超限持续该时长才告警, 0=立即告警',
  `debounce_sec` int(11) NOT NULL DEFAULT 60 COMMENT '去抖窗口(秒): 窗口内仅合并落库不重复推送',
  `alert_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT '告警类型: TEMPERATURE/VIBRATION/PRESSURE/OTHER',
  `alert_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '告警级别: HIGH/MEDIUM/LOW',
  `notify_channels` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'SSE,MES_ANDON' COMMENT '通知渠道(逗号分隔): SSE/MES_ANDON',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '启用状态(1启用 0停用)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE INDEX `uk_rule_scope`(`tenant_id`, `product_id`, `device_id`, `property_code`) USING BTREE,
  INDEX `idx_tenant_property`(`tenant_id`, `property_code`, `enabled`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT告警规则' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 2. `ry-iot`.`iot_alert_record` 告警记录表（IOT-ALERT-01）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_alert_record`  (
  `alert_id` bigint(20) NOT NULL COMMENT '告警ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_id` bigint(20) NULL DEFAULT NULL COMMENT '命中规则ID(空=YAML兜底默认或EWMA异常)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RULE' COMMENT '来源: RULE/DEFAULT/ANOMALY',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `product_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '产品ID(冗余)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '属性编码',
  `alert_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT '告警类型: TEMPERATURE/VIBRATION/PRESSURE/OTHER/ANOMALY',
  `alert_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '告警级别: HIGH/MEDIUM/LOW',
  `compare_operator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GT' COMMENT '比较符快照',
  `threshold` decimal(14, 4) NULL DEFAULT NULL COMMENT '阈值快照(用于恢复判定)',
  `trigger_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '触发值(最近一次超限值)',
  `alert_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '告警描述',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态机: ACTIVE/ACKNOWLEDGED/RESOLVED',
  `active_flag` tinyint(1) NULL DEFAULT 1 COMMENT '未恢复标记(1=未恢复, NULL=已恢复); 唯一键组成',
  `trigger_time` datetime(0) NOT NULL COMMENT '首次触发时间',
  `last_trigger_time` datetime(0) NULL DEFAULT NULL COMMENT '最近触发时间',
  `ack_time` datetime(0) NULL DEFAULT NULL COMMENT '确认时间',
  `ack_by` bigint(20) NULL DEFAULT NULL COMMENT '确认人',
  `ack_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '确认备注',
  `resolve_time` datetime(0) NULL DEFAULT NULL COMMENT '恢复时间',
  `resolve_by` bigint(20) NULL DEFAULT NULL COMMENT '恢复操作人(空=系统自动恢复)',
  `resolve_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '恢复值',
  `resolve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '恢复备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`alert_id`) USING BTREE,
  UNIQUE INDEX `uk_active_alert`(`tenant_id`, `device_id`, `property_code`, `active_flag`) USING BTREE,
  INDEX `idx_query`(`tenant_id`, `device_id`, `status`, `trigger_time`) USING BTREE,
  INDEX `idx_status_time`(`tenant_id`, `status`, `trigger_time`) USING BTREE,
  INDEX `idx_rule`(`rule_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT告警记录(含确认/恢复状态机)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 3. `ry-iot`.`iot_alert_notify_log` 通知投递留痕表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_alert_notify_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `alert_id` bigint(20) NOT NULL COMMENT '告警ID',
  `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '通知渠道: SSE/MES_ANDON',
  `target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '投递目标(在线用户ID列表/安灯ID)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '投递结果: SUCCESS/FAILED/SKIPPED',
  `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '结果说明',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_alert`(`alert_id`) USING BTREE,
  INDEX `idx_tenant_time`(`tenant_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT告警通知投递留痕(无在线用户也不丢)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 4. 兜底规则种子（与 nexus.iot.pipeline.alert-thresholds 的 YAML 默认等价）
--    说明：库内存在规则时**优先于 YAML**（YAML 仅在库内无匹配规则时兜底），
--    因此这两条种子只是把原 YAML 默认值搬进库内，行为等价、可按租户自行增删改。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `iot_alert_rule`
  (`rule_id`, `tenant_id`, `rule_name`, `product_id`, `device_id`, `property_code`, `compare_operator`,
   `threshold`, `duration_sec`, `debounce_sec`, `alert_type`, `alert_level`, `notify_channels`,
   `enabled`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (7001, '000000', '温度超上限(全局默认)', 0, 0, 'temperature', 'GT', 80.0000, 0, 60,
   'TEMPERATURE', 'HIGH', 'SSE,MES_ANDON', '1',
   '与 nexus.iot.pipeline.alert-thresholds.temperature 等价的库内规则(库内有规则时优先于YAML)',
   103, 1, NOW(), NULL, NOW()),
  (7002, '000000', '振动烈度超上限(全局默认)', 0, 0, 'vibration', 'GT', 4.5000, 30, 60,
   'VIBRATION', 'MEDIUM', 'SSE,MES_ANDON', '1',
   '连续30秒超限才告警, 去抖窗口60秒', 103, 1, NOW(), NULL, NOW());

-- -----------------------------------------------------------------------------
-- 5. `ry-vue`.`sys_menu` 登记告警/规则权限码
--    父菜单 6000（IoT 物联网）；6030~6034 = 告警记录，6040~6045 = 告警规则
--    超管（role_id=1）经 SysPermissionServiceImpl 返回 *:*:*，无需 sys_role_menu 授权；
--    其他角色请在「系统管理 → 角色管理」中勾选。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`,
   `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`,
   `create_time`, `update_by`, `update_time`, `remark`)
VALUES
  (6030, '告警记录', 6000, 3, 'alert', 'iot/alert/index', '', 1, 0, 'C', '0', '0', 'iot:alert:list', 'warning', 103, 1, NOW(), NULL, NULL, 'IoT告警记录(查询/确认/恢复)'),
  (6031, '告警查询', 6030, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:query', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6032, '告警确认', 6030, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:ack', '#', 103, 1, NOW(), NULL, NULL, 'ACTIVE → ACKNOWLEDGED'),
  (6033, '告警恢复', 6030, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:recover', '#', 103, 1, NOW(), NULL, NULL, 'ACTIVE/ACKNOWLEDGED → RESOLVED'),
  (6034, '告警导出', 6030, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:export', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6040, '告警规则', 6000, 4, 'alertRule', 'iot/alertRule/index', '', 1, 0, 'C', '0', '0', 'iot:alertRule:list', 'set', 103, 1, NOW(), NULL, NULL, 'IoT告警规则(阈值/持续时长/去抖/级别)'),
  (6041, '规则查询', 6040, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:query', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6042, '规则新增', 6040, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:add', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6043, '规则修改', 6040, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6044, '规则删除', 6040, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
  (6045, '规则启停', 6040, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:changeStatus', '#', 103, 1, NOW(), NULL, NULL, '启用/停用规则(1/0)');

-- -----------------------------------------------------------------------------
-- 6. 校验
-- -----------------------------------------------------------------------------
-- SELECT COUNT(*) AS rule_cnt FROM `ry-iot`.`iot_alert_rule`;
-- SELECT COUNT(*) AS record_cnt FROM `ry-iot`.`iot_alert_record`;
-- SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu` WHERE perms LIKE 'iot:alert%' ORDER BY menu_id;
