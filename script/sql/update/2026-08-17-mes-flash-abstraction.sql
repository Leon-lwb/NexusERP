-- ============================================================
-- MES 刷写能力抽象 P0: 表重命名 + 字段泛化 + 数据迁移 + mes_flash_type + 字典/权限/配置重命名
-- 库: MES 业务表(fw_*) -> ry-erp 业务库(ry-mes 所在库); 字典/菜单/配置 -> ry-vue 系统库
-- 日期: 2026-08-17
-- 配套方案: doc/mes/plans/2026-08-17-mes-flash-abstraction-plan.md
-- 前置: 库已执行 2026-08-13-mes-firmware.sql(fw_* 4 表 + pro_work_order_step 扩展)
-- 幂等说明: RENAME 不可重入(二次执行报 Unknown table), 需在升级窗口执行一次;
--          CREATE TABLE IF NOT EXISTS / INSERT IGNORE 可重入; ALTER ADD COLUMN 重复执行会报 Duplicate
-- 段位避让: 字典 600906/600907(6009xx 段, 避开既有 600901-600905); 菜单沿用 4280-4295(仅改 perms/path/component)
-- ============================================================

-- ============================================================
-- ★★★ 以下语句分库执行: 第一节~第五节落 ry-erp(MES 业务库); 第六节~第八节落 ry-vue(系统库) ★★★
-- ============================================================

-- ==================== 一、MES 业务库: 4 表重命名 fw_→mes_ ====================
RENAME TABLE `fw_firmware`     TO `mes_firmware`;
RENAME TABLE `fw_flash_record` TO `mes_flash_record`;
RENAME TABLE `fw_test_item`    TO `mes_test_item`;
RENAME TABLE `fw_test_record`  TO `mes_test_record`;

-- ==================== 二、mes_flash_record 字段泛化(固件专用 → 通用内容引用) ====================
-- 新增通用内容字段(_nullable, 向后兼容), 回填历史数据, 再删除旧固件专用字段
ALTER TABLE `mes_flash_record`
  ADD COLUMN `flash_type_id`    bigint(20)  NULL DEFAULT NULL COMMENT '刷写类型ID(mes_flash_type)' AFTER `flash_no`,
  ADD COLUMN `flash_type_code`  varchar(32) NULL DEFAULT NULL COMMENT '刷写类型标识(快照: firmware_file/config_file/mac/sn)' AFTER `flash_type_id`,
  ADD COLUMN `content_ref_type` varchar(16) NULL DEFAULT NULL COMMENT '内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL' AFTER `process_name`,
  ADD COLUMN `content_ref_id`   bigint(20)  NULL DEFAULT NULL COMMENT '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)' AFTER `content_ref_type`,
  ADD COLUMN `content_version`  varchar(32) NULL DEFAULT NULL COMMENT '内容版本(固件类=固件版本)' AFTER `content_ref_id`,
  ADD COLUMN `content_payload`  text        NULL COMMENT '实际写入内容快照JSON(如 {"mac":"00:1A:2B:3C:4D:5E"})' AFTER `content_version`,
  ADD COLUMN `content_checksum` varchar(64) NULL DEFAULT NULL COMMENT '内容校验值(泛化MD5/校验码)' AFTER `content_payload`,
  ADD INDEX `idx_flash_type`(`tenant_id`, `flash_type_id`) USING BTREE,
  ADD INDEX `idx_content_ref`(`content_ref_type`, `content_ref_id`) USING BTREE;

-- 历史固件刷写记录回填到通用字段(fw_flash_record 当前为空, 若有数据则迁移)
UPDATE `mes_flash_record`
   SET `flash_type_code`  = 'firmware_file',
       `content_ref_type` = 'FIRMWARE',
       `content_ref_id`   = `firmware_id`,
       `content_version`  = `firmware_version`,
       `content_checksum` = `file_md5`
 WHERE `firmware_id` IS NOT NULL;

-- 删除旧固件专用字段(含其索引 idx_firmware)
ALTER TABLE `mes_flash_record`
  DROP INDEX `idx_firmware`,
  DROP COLUMN `firmware_id`,
  DROP COLUMN `firmware_version`,
  DROP COLUMN `file_md5`;

-- ==================== 三、mes_test_record / mes_test_item 字段重命名(firmware_id→content_ref_id) ====================
-- 测试项/记录仍按"内容源版本"范围(固件类即固件版本), 仅统一字段命名, 语义不变
ALTER TABLE `mes_test_record`
  CHANGE COLUMN `firmware_id`     `content_ref_id` bigint(20)  NULL DEFAULT NULL COMMENT '测试时内容源ID(固件类=mes_firmware_id)',
  CHANGE COLUMN `firmware_version` `content_version` varchar(32) NULL DEFAULT NULL COMMENT '测试时内容版本',
  DROP INDEX `idx_firmware`,
  ADD INDEX `idx_content_ref`(`content_ref_id`) USING BTREE;

ALTER TABLE `mes_test_item`
  CHANGE COLUMN `firmware_id` `content_ref_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '内容源ID(0=该物料所有版本通用, 固件类=mes_firmware_id)',
  DROP INDEX `uk_tenant_material_item`,
  ADD UNIQUE INDEX `uk_tenant_material_item`(`tenant_id`, `material_id`, `content_ref_id`, `item_code`) USING BTREE;

-- ==================== 四、新建 mes_flash_type 刷写类型定义(配置化目录) ====================
CREATE TABLE IF NOT EXISTS `mes_flash_type` (
  `flash_type_id`    bigint(20)  NOT NULL COMMENT '刷写类型ID',
  `tenant_id`        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `type_code`        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型标识: firmware_file/config_file/mac/sn/external_*',
  `type_name`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名',
  `source_type`      varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容来源: FILE/POOL/EXTERNAL/INLINE',
  `source_provider`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPI Provider bean名(如 firmwareFileFlashProvider)',
  `source_config`    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源配置JSON(审批流标识/池ID/REST模板)',
  `verify_strategy`  varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MD5_MATCH' COMMENT '校验策略: MD5_MATCH/RESOURCE_BOUND/CUSTOM',
  `gate_enabled`     char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否参与完工门禁(0否/1是)',
  `status`           varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `sort_order`       int(11)     NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept`      bigint(20)  NULL DEFAULT NULL COMMENT '创建部门',
  `create_by`        bigint(20)  NULL DEFAULT NULL COMMENT '创建人',
  `create_time`      datetime    NULL DEFAULT NULL COMMENT '创建时间',
  `update_by`        bigint(20)  NULL DEFAULT NULL COMMENT '更新人',
  `update_time`      datetime    NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag`         char(1)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark`           varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`flash_type_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_type_code`(`tenant_id`, `type_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写类型定义(配置化目录)' ROW_FORMAT = DYNAMIC;

-- P0 仅启用 firmware_file(已实现 FirmwareFileFlashProvider); config_file/mac/sn/external_rest 待 P2/P3 实现 Provider 后再 INSERT 并 ENABLED
INSERT IGNORE INTO `mes_flash_type`(`flash_type_id`, `tenant_id`, `type_code`, `type_name`, `source_type`, `source_provider`, `verify_strategy`, `gate_enabled`, `status`, `sort_order`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(2091000000000006001, '000000', 'firmware_file', '刷写固件', 'FILE', 'firmwareFileFlashProvider', 'MD5_MATCH', '1', 'ENABLED', 1, 103, 1, NOW(), '固件文件(审批发布, 绑工单/物料), MD5校验');

-- 回填 mes_flash_record.flash_type_id(需 mes_flash_type 已建)
UPDATE `mes_flash_record` r
   JOIN `mes_flash_type` t ON t.`type_code` = 'firmware_file'
   SET r.`flash_type_id` = t.`flash_type_id`
 WHERE r.`flash_type_code` = 'firmware_file';

-- ==================== 五、pro_work_order_step 字段迁移(单一 firmware_id → 通用 content_ref + 多类型 step_flash_config) ====================
ALTER TABLE `pro_work_order_step`
  ADD COLUMN `content_ref_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容引用类型(单类型快捷绑定: FIRMWARE/CONFIG/MAC_POOL...)' AFTER `step_type`,
  ADD COLUMN `content_ref_id`   bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件类=mes_firmware_id)' AFTER `content_ref_type`,
  ADD COLUMN `step_flash_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '多类型绑定JSON: [{"flashTypeCode":"firmware_file","contentRefId":123},{"flashTypeCode":"mac"}]' AFTER `content_ref_id`,
  ADD INDEX `idx_step_type`(`tenant_id`, `step_type`) USING BTREE;

-- 历史工序固件绑定迁移
UPDATE `pro_work_order_step`
   SET `content_ref_type` = 'FIRMWARE',
       `content_ref_id`   = `firmware_id`
 WHERE `firmware_id` IS NOT NULL;

ALTER TABLE `pro_work_order_step` DROP COLUMN `firmware_id`;


-- ============================================================
-- ★★★ 以下落 ry-vue 系统库 ★★★
-- ============================================================

-- ==================== 六、字典: 重命名 + 新增 ====================
-- 6.1 重命名通用刷写字典(mes_fw_mode→mes_flash_mode, mes_fw_result→mes_flash_result)
--     mes_fw_status 保留(固件档案 mes_firmware 生命周期用); mes_step_type 保留(激活)
UPDATE `sys_dict_type` SET `dict_type` = 'mes_flash_mode'   WHERE `dict_type` = 'mes_fw_mode';
UPDATE `sys_dict_type` SET `dict_type` = 'mes_flash_result' WHERE `dict_type` = 'mes_fw_result';
UPDATE `sys_dict_data` SET `dict_type` = 'mes_flash_mode'   WHERE `dict_type` = 'mes_fw_mode';
UPDATE `sys_dict_data` SET `dict_type` = 'mes_flash_result' WHERE `dict_type` = 'mes_fw_result';

-- 6.2 新增刷写内容来源字典(mes_flash_type 由 mes_flash_type 表动态驱动, 不另建字典)
INSERT IGNORE INTO `sys_dict_type`(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600906, '000000', '刷写内容来源', 'mes_flash_source', 103, 1, NOW(), NULL, NULL, 'MES刷写内容来源(FILE/POOL/EXTERNAL/INLINE)');

INSERT IGNORE INTO `sys_dict_data`(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600961, '000000', 1, '文件',     'FILE',     'mes_flash_source', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '审批发布文件(绑工单/物料)'),
(600962, '000000', 2, '资源池',   'POOL',     'mes_flash_source', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '内部资源池原子分配(一SN绑一资源)'),
(600963, '000000', 3, '外部REST', 'EXTERNAL', 'mes_flash_source', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '外部系统REST获取'),
(600964, '000000', 4, '现场上报', 'INLINE',   'mes_flash_source', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, '产线工具上报时传入');

-- ==================== 七、菜单/权限: mes:fw:→mes:flash:, 组件路径 mes/fw/→mes/flash/ ====================
-- 7.1 权限串前缀替换(所有 mes:fw:* → mes:flash:*)
UPDATE `sys_menu` SET `perms` = REPLACE(`perms`, 'mes:fw:', 'mes:flash:') WHERE `perms` LIKE 'mes:fw:%';
-- 7.2 组件路径 mes/fw/ → mes/flash/
UPDATE `sys_menu` SET `component` = REPLACE(`component`, 'mes/fw/', 'mes/flash/') WHERE `component` LIKE 'mes/fw/%';
-- 7.3 父菜单(4280 固件管理→刷写管理, path fw→flash)
UPDATE `sys_menu` SET `menu_name` = '刷写管理', `path` = 'flash' WHERE `menu_id` = 4280;
-- 7.4 子菜单名(固件档案保留; 其余后续 P1/P2 调整)
--   4281 固件档案 保留; 4286-4295 按钮 perms 已由 7.1 替换

-- ==================== 八、审批业务名 + 配置重命名 ====================
-- 8.1 审批业务服务名 fwFirmwareService → mesFirmwareService
UPDATE `wf_custom_form` SET `business_service` = 'mesFirmwareService' WHERE `business_service` = 'fwFirmwareService';

-- 8.2 门禁配置: mes.fw.gate.mode(死配置) → mes.flash.gate.mode(激活 FORCE/WARN); mes.flash-step-gate(旧 bool) 废弃由代码兼容读取
UPDATE `sys_config` SET `config_key` = 'mes.flash.gate.mode', `config_value` = 'FORCE', `config_name` = '刷写门禁模式', `remark` = 'FORCE=FAIL拦截 / WARN=告警放行(无SN工单始终放行)' WHERE `config_key` = 'mes.fw.gate.mode';
INSERT IGNORE INTO `sys_config`(`config_id`, `tenant_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2091000000000006006, '000000', '刷写门禁模式', 'mes.flash.gate.mode', 'FORCE', 'Y', 103, 1, NOW(), NULL, NULL, 'FORCE=FAIL拦截 / WARN=告警放行(无SN工单始终放行)'
  WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'mes.flash.gate.mode');

-- 编码规则 MES_FW_CODE / MES_FLASH_NO / MES_TEST_NO 保持不变(固件编码/刷写号/测试号语义仍成立)

-- ==================== 校验 ====================
-- SELECT type_code, type_name, source_type, source_provider, status FROM mes_flash_type;
-- SELECT flash_record_id, flash_no, flash_type_code, content_ref_type, content_ref_id, content_version, content_checksum FROM mes_flash_record;
-- SHOW COLUMNS FROM mes_flash_record LIKE 'content_%';
-- SHOW COLUMNS FROM pro_work_order_step LIKE 'content_%';
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type IN ('mes_flash_mode','mes_flash_result','mes_flash_source') ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, path, component, perms FROM sys_menu WHERE perms LIKE 'mes:flash:%' OR menu_id=4280 ORDER BY menu_id;
-- SELECT config_key, config_value FROM sys_config WHERE config_key='mes.flash.gate.mode';
-- SELECT business_name, business_service FROM wf_custom_form WHERE business_service='mesFirmwareService';
