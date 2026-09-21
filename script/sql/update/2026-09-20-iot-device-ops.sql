-- =============================================================================
-- 增量脚本：IoT 设备管理 / 消息能力补齐（批次 B10-b）
-- -----------------------------------------------------------------------------
-- 用途     ：为存量库补齐 设备分组/标签、设备事件上报、下行指令、数据转发 所需的 8 张表
--            + sys_menu 权限码种子（IoT 设备运维能力）。
-- 执行前提 ：MySQL 5.7+/8.0；已有 ry-iot 库与 ry-vue 库（IoT 基础表 iot_device 等已存在）。
-- 影响库   ：ry-iot（建表）、ry-vue（sys_menu 权限码种子）。
-- 幂等性   ：可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
--            本脚本不含 ALTER，**不需要**停机窗口；不动存量表结构与存量数据。
-- 需清缓存 ：否（新表无历史缓存键；Redis 中新增键 iot:lastreport:{deviceId} 可自然过期）。
-- 关联主脚本：script/sql/ry-iot.sql（已同步这 8 张表 DDL，新环境直接导入主脚本即可）。
-- 前置检查 ：无唯一键冲突风险（全部为新建表）；若曾手工建过同名表，
--            请先核对 uk_code/uk_name/uk_device_group/uk_device_tag/uk_source_msg_id 定义是否一致。
-- 说明     ：本批次未修改 iot_device 表结构（分组用关系表 iot_device_group_rel 表达，
--            避免改动主脚本中 iot_device 的**无列名位序 INSERT** 存量行）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ry-iot：设备分组（树形）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_group`  (
  `group_id` bigint(20) NOT NULL COMMENT '分组ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父分组ID(0=根分组)',
  `ancestors` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔,不含自身;根为0)',
  `group_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分组编码',
  `group_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分组名称',
  `order_num` int(11) NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`group_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`tenant_id`, `group_code`) USING BTREE,
  INDEX `idx_parent`(`tenant_id`, `parent_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备分组(树形)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 2. ry-iot：设备-分组关系（N:N）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_group_rel`  (
  `rel_id` bigint(20) NOT NULL COMMENT '关系ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `group_id` bigint(20) NOT NULL COMMENT '分组ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`rel_id`) USING BTREE,
  UNIQUE INDEX `uk_device_group`(`tenant_id`, `device_id`, `group_id`) USING BTREE,
  INDEX `idx_group`(`tenant_id`, `group_id`) USING BTREE,
  INDEX `idx_device`(`device_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备与分组关系(多对多)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 3. ry-iot：设备标签
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_tag`  (
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `tag_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '标签名称',
  `tag_color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#409EFF' COMMENT '标签颜色(前端展示)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`tag_id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`tenant_id`, `tag_name`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备标签' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 4. ry-iot：设备-标签关系（N:N）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_tag_rel`  (
  `rel_id` bigint(20) NOT NULL COMMENT '关系ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`rel_id`) USING BTREE,
  UNIQUE INDEX `uk_device_tag`(`tenant_id`, `device_id`, `tag_id`) USING BTREE,
  INDEX `idx_tag`(`tenant_id`, `tag_id`) USING BTREE,
  INDEX `idx_device`(`device_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备与标签关系(多对多)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 5. ry-iot：设备事件上报（event/post）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_event`  (
  `event_id` bigint(20) NOT NULL COMMENT '事件ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '产品ID(冗余)',
  `event_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '事件编码',
  `event_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '事件名称(设备上报)',
  `event_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '事件级别: INFO/WARN/ERROR/CRITICAL(设备上报)',
  `event_time` datetime(0) NOT NULL COMMENT '事件发生时间(设备侧)',
  `receive_time` datetime(0) NULL DEFAULT NULL COMMENT '服务端接收时间',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '事件原始载荷(JSON)',
  `quality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GOOD' COMMENT '数据质量: GOOD/BAD/UNCERTAIN',
  `process_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING/PROCESSED/IGNORED/FAILED',
  `process_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理说明',
  `alert_id` bigint(20) NULL DEFAULT NULL COMMENT '联动生成的告警ID(空=未联动)',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MQTT' COMMENT '数据来源: MQTT/HTTP',
  `source_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源消息ID(Redis Stream entry id,幂等去重键)',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`event_id`) USING BTREE,
  UNIQUE INDEX `uk_source_msg_id`(`source_msg_id`) USING BTREE,
  INDEX `idx_query`(`tenant_id`, `device_id`, `event_time`) USING BTREE,
  INDEX `idx_status`(`tenant_id`, `process_status`, `event_time`) USING BTREE,
  INDEX `idx_code`(`tenant_id`, `event_code`, `event_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备事件上报(含处理状态与告警联动)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 6. ry-iot：下行指令留痕
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_device_command`  (
  `command_id` bigint(20) NOT NULL COMMENT '指令ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '设备编码(冗余)',
  `command_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROPERTY_SET' COMMENT '指令类型: PROPERTY_SET/SERVICE_INVOKE',
  `topic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '下发Topic',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '下发载荷(JSON)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/SUCCESS/FAILED/TIMEOUT',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重发次数',
  `sent_time` datetime(0) NULL DEFAULT NULL COMMENT '下发时间',
  `ack_time` datetime(0) NULL DEFAULT NULL COMMENT '设备回执时间',
  `ack_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '设备回执载荷',
  `ack_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '回执消息ID(幂等去重键)',
  `result_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下发/回执结果说明',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '下发人',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下发人名称',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`command_id`) USING BTREE,
  UNIQUE INDEX `uk_ack_msg_id`(`ack_msg_id`) USING BTREE,
  INDEX `idx_query`(`tenant_id`, `device_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`tenant_id`, `status`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT下行指令(下发留痕+回执状态)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 7. ry-iot：数据转发规则（HTTP Webhook）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_forward_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '规则名称',
  `product_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '产品范围(0=不限)',
  `device_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '设备范围(0=不限)',
  `property_codes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '属性编码白名单(逗号分隔,空=全部)',
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HTTP' COMMENT '目标类型: HTTP',
  `target_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Webhook URL(仅http/https, 内网/保留地址被拒)',
  `http_method` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POST' COMMENT 'HTTP方法: POST/PUT',
  `headers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '自定义请求头(JSON)',
  `timeout_ms` int(11) NOT NULL DEFAULT 5000 COMMENT '单次投递超时(毫秒)',
  `max_retry` int(11) NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '启用状态(1启用 0停用)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE INDEX `uk_rule_scope`(`tenant_id`, `product_id`, `device_id`, `rule_name`) USING BTREE,
  INDEX `idx_enabled`(`tenant_id`, `enabled`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT数据转发规则(HTTP Webhook)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 8. ry-iot：转发投递留痕（失败可重试）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_forward_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_id` bigint(20) NOT NULL COMMENT '转发规则ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '属性编码',
  `target_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实际投递URL',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '投递载荷(JSON)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `http_status` int(11) NULL DEFAULT NULL COMMENT 'HTTP响应码',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `next_retry_time` datetime(0) NULL DEFAULT NULL COMMENT '下次重试时间(空=不再重试)',
  `last_attempt_time` datetime(0) NULL DEFAULT NULL COMMENT '最近一次投递时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_retry`(`tenant_id`, `status`, `next_retry_time`) USING BTREE,
  INDEX `idx_rule`(`rule_id`) USING BTREE,
  INDEX `idx_device`(`tenant_id`, `device_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT数据转发投递留痕(失败可重试)' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 9. ry-vue：sys_menu 权限码种子（IoT 设备运维能力）
-- -----------------------------------------------------------------------------
-- menu_id 占用校验（已核对 script/sql/ry-vue.sql 的 6000-6999 段）：
--   已占用：6000,6010-6015,6020-6026,6030-6034,6040-6045,6100-6136,6140-6146,6161-6178,6181-6187,6190,6201
--   本脚本使用：6050-6059,6060-6063,6070-6073,6075,6080-6085,6090-6092（全部空闲）
-- 超管（role_id=1）走 *:*:*，无需 sys_role_menu；其他角色在「角色管理」勾选即可。
-- =============================================================================
USE `ry-vue`;

-- 9.1 设备状态变更（挂在 6020 设备管理下）
INSERT IGNORE INTO `sys_menu` VALUES (6075, '设备状态变更', 6020, 7, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:changeStatus', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '激活/禁用/注销(CAS, 禁用后 MQTT 认证被拒)');

-- 9.2 设备分组
INSERT IGNORE INTO `sys_menu` VALUES (6050, '设备分组', 6000, 5, 'deviceGroup', 'iot/deviceGroup/index', '', 1, 0, 'C', '0', '0', 'iot:deviceGroup:list', 'tree', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT设备分组(树形)');
INSERT IGNORE INTO `sys_menu` VALUES (6051, '分组查询', 6050, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceGroup:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6052, '分组新增', 6050, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceGroup:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6053, '分组修改', 6050, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceGroup:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6054, '分组删除', 6050, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceGroup:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6090, '设备挂分组', 6050, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceGroup:assign', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '批量挂载/移出分组');

-- 9.3 设备标签
INSERT IGNORE INTO `sys_menu` VALUES (6055, '设备标签', 6000, 6, 'deviceTag', 'iot/deviceTag/index', '', 1, 0, 'C', '0', '0', 'iot:deviceTag:list', 'tag', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT设备标签');
INSERT IGNORE INTO `sys_menu` VALUES (6056, '标签查询', 6055, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceTag:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6057, '标签新增', 6055, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceTag:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6058, '标签修改', 6055, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceTag:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6059, '标签删除', 6055, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceTag:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6091, '设备打标签', 6055, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:deviceTag:bind', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '批量打标签/解绑标签');

-- 9.4 设备事件
INSERT IGNORE INTO `sys_menu` VALUES (6060, '设备事件', 6000, 7, 'event', 'iot/event/index', '', 1, 0, 'C', '0', '0', 'iot:event:list', 'documentation', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT设备事件上报(event/post)');
INSERT IGNORE INTO `sys_menu` VALUES (6061, '事件查询', 6060, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:event:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6062, '事件处理', 6060, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:event:process', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '标记已处理/忽略');
INSERT IGNORE INTO `sys_menu` VALUES (6063, '事件导出', 6060, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:event:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');

-- 9.5 下行指令
INSERT IGNORE INTO `sys_menu` VALUES (6070, '设备指令', 6000, 8, 'command', 'iot/command/index', '', 1, 0, 'C', '0', '0', 'iot:command:list', 'guide', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT下行指令(属性设置/服务调用)与回执');
INSERT IGNORE INTO `sys_menu` VALUES (6071, '指令查询', 6070, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:command:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6072, '指令下发', 6070, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:command:send', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '下发属性设置/服务调用');
INSERT IGNORE INTO `sys_menu` VALUES (6073, '指令重发', 6070, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:command:resend', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');

-- 9.6 数据转发
INSERT IGNORE INTO `sys_menu` VALUES (6080, '数据转发', 6000, 9, 'forwardRule', 'iot/forwardRule/index', '', 1, 0, 'C', '0', '0', 'iot:forwardRule:list', 'shuffle', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT数据转发规则(HTTP Webhook)');
INSERT IGNORE INTO `sys_menu` VALUES (6081, '转发查询', 6080, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:forwardRule:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6082, '转发新增', 6080, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:forwardRule:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6083, '转发修改', 6080, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:forwardRule:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6084, '转发删除', 6080, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:forwardRule:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6085, '转发日志', 6080, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:forwardRule:log', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '投递留痕与重试记录');

-- 校验：应返回 25 行（6075 + 6050-6054 + 6090 + 6055-6059 + 6091 + 6060-6063 + 6070-6073 + 6080-6085）
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE menu_id BETWEEN 6050 AND 6099 ORDER BY menu_id;
