-- =============================================================================
-- 增量脚本：IoT OTA 固件升级 MVP（批次 B10-c）
-- -----------------------------------------------------------------------------
-- 用途     ：为存量库补齐 OTA 固件升级所需的 2 张表
--            （iot_firmware 固件登记、iot_ota_task 升级任务）
--            + sys_menu 权限码种子（固件管理/升级任务，13 条）
--            + sj_job / sj_job_executor 种子（OTA 升级超时兜底任务）。
-- 执行前提 ：MySQL 5.7+/8.0；已有 ry-iot 库与 ry-vue 库（IoT 基础表 iot_device 等已存在）。
-- 影响库   ：ry-iot（建表）、ry-vue（sys_menu + sj_job + sj_job_executor 种子）。
-- 幂等性   ：可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
--            本脚本不含 ALTER，**不需要**停机窗口；不动存量表结构与存量数据。
-- 需清缓存 ：否（新表无历史缓存键）。
-- 关联主脚本：script/sql/ry-iot.sql（已同步这 2 张表 DDL，新环境直接导入主脚本即可）。
-- 前置检查 ：无唯一键冲突风险（全部为新建表）。若曾手工建过同名表，请先核对
--            uk_version(tenant_id, product_key, version) 与
--            idx_device / idx_status / idx_firmware 定义是否一致。
-- 说明     ：本批次**未修改 iot_device 表结构** —— 固件版本复用既有列
--            iot_device.firmware_version（ry-iot.sql 中该列已存在），
--            避免改动主脚本中 iot_device 的**无列名位序 INSERT** 存量行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ry-iot：固件登记
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_firmware`  (
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '固件名称',
  `product_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '适用产品型号(iot_product.product_code,空=不限)',
  `hardware_model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '适用硬件型号(空=不限,仅登记不参与匹配)',
  `version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '版本号',
  `file_oss_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件包OSS文件ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件包文件名',
  `file_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '固件包大小(字节)',
  `checksum_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MD5' COMMENT '校验算法: MD5/SHA256',
  `checksum` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '校验值(小写十六进制)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态: DRAFT/RELEASED/DISABLED',
  `release_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `change_log` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更说明',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`firmware_id`) USING BTREE,
  UNIQUE INDEX `uk_version`(`tenant_id`, `product_key`, `version`) USING BTREE,
  INDEX `idx_status`(`tenant_id`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT固件登记(版本/包/校验值/发布状态)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 2. ry-iot：OTA 升级任务
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `iot_ota_task`  (
  `task_id` bigint(20) NOT NULL COMMENT '升级任务ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID(iot_firmware)',
  `target_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '目标版本(冗余,固件删除后仍可追溯)',
  `current_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下发时设备当前版本',
  `device_id` bigint(20) NOT NULL COMMENT '目标设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '设备编码(冗余)',
  `group_id` bigint(20) NULL DEFAULT NULL COMMENT '来源分组ID(按分组批量下发时记录)',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批量下发批次号',
  `command_id` bigint(20) NULL DEFAULT NULL COMMENT '关联下行指令ID(iot_device_command,下发留痕)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
  `progress` int(11) NOT NULL DEFAULT 0 COMMENT '升级进度(0-100)',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '人工重试次数',
  `sent_time` datetime(0) NULL DEFAULT NULL COMMENT '指令下发时间',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '设备开始升级时间',
  `finish_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `fail_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败/取消原因',
  `report_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近一次回执的消息ID(Stream entry id,便于取证)',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '下发人',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下发人名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`task_id`) USING BTREE,
  INDEX `idx_device`(`tenant_id`, `device_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`tenant_id`, `status`, `sent_time`) USING BTREE,
  INDEX `idx_firmware`(`tenant_id`, `firmware_id`) USING BTREE,
  INDEX `idx_batch`(`batch_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT OTA固件升级任务(单设备/按分组批量)' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------------------------
-- 3. ry-vue：sys_menu 权限码种子（13 条，menu_id 已逐个校验空闲）
--    menu_id 占用核查（6000-6199 段，来自 script/sql/ry-vue.sql）：
--      已占用 6000,6010-6015,6020-6026,6030-6034,6040-6045,6050-6059,6060-6063,
--             6070-6073,6075,6080-6085,6090,6091,
--             6100-6136,6140-6146,6161-6178,6181-6187,6190-6194
--      本批次使用 6137,6138,6139,6147,6148,6150-6155,6157,6158 → 全部空闲
--    父菜单：6157 固件管理 / 6158 升级任务 挂在 6000（IoT平台，排序 10/11，
--            接在既有 6050(分组) 6060(事件) 6070(指令) 6080(转发) 之后）。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES (6157, '固件管理', 6000, 10, 'firmware', 'iot/firmware/index', '', 1, 0, 'C', '0', '0', 'iot:firmware:list', 'upload', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT固件登记(版本/固件包/校验值/发布状态)');
INSERT IGNORE INTO `sys_menu` VALUES (6158, '升级任务', 6000, 11, 'ota', 'iot/ota/index', '', 1, 0, 'C', '0', '0', 'iot:ota:list', 'guide', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT OTA固件升级任务(单设备/按分组批量)');
INSERT IGNORE INTO `sys_menu` VALUES (6137, '固件查询', 6157, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:firmware:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6138, '固件新增', 6157, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:firmware:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6139, '固件修改', 6157, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:firmware:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6147, '固件删除', 6157, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:firmware:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6148, '固件发布停用', 6157, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:firmware:release', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '发布/停用固件(DRAFT→RELEASED→DISABLED)');
INSERT IGNORE INTO `sys_menu` VALUES (6150, '升级任务查询', 6158, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6151, '升级任务创建', 6158, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:create', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '单设备下发/按分组批量下发升级任务');
INSERT IGNORE INTO `sys_menu` VALUES (6152, '升级任务重试', 6158, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:retry', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '仅 FAILED/TIMEOUT 可重试');
INSERT IGNORE INTO `sys_menu` VALUES (6153, '升级任务取消', 6158, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:cancel', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '仅 PENDING/SENT/UPGRADING 可取消');
INSERT IGNORE INTO `sys_menu` VALUES (6154, '升级回执上报', 6158, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:report', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备侧/网关回传升级进度与结果');
INSERT IGNORE INTO `sys_menu` VALUES (6155, '版本一致性核对', 6158, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:ota:versionCheck', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '查询设备当前版本 vs 目标版本不一致/落后清单');

-- -----------------------------------------------------------------------------
-- 4. ry-vue：sj_job / sj_job_executor 种子（OTA 升级超时兜底任务）
--    与 B1 批次一致：SnailJob 客户端在生产被禁用，任务类另有 @Scheduled 本地兜底；
--    此处仅补齐 SnailJob 侧定义，使 snail-job.enabled=true 的环境也能调度。
--    sj_job 现有 id：1-28,100-103,200 → 本批次用 29；sj_job_executor 现有 1-44,200 → 用 45。
--    若 id 已被占用，INSERT IGNORE 会静默跳过，请在 SnailJob 后台核对后手工补。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sj_job` VALUES (29, 'dev', 'biz_29', 'ruoyi_group', 'IoT OTA升级超时检测任务', '', 1, 1789405200000, 1, 1, 4, 1, 'iotOtaTimeoutTask', 3, '0 */5 * * * ?', 1, 60, 3, 1, 1, 51, 0, '', 1, '{}', '', '', 1, '2026-09-20 00:00:00', '2026-09-20 00:00:00');
INSERT IGNORE INTO `sj_job_executor` VALUES (45, 'dev', 'ruoyi_group', 'iotOtaTimeoutTask', '1', '2026-09-20 00:00:00', '2026-09-20 00:00:00');
