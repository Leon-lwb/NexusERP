-- =============================================================================
-- IoT OTA 固件升级 —— 前端页面字典增量脚本
-- 对应: doc/audit/2026-09-20-feat-iot-ota.md §6.3 | 报告: doc/audit/2026-09-20-feat-iot-ota-fe.md
-- 分支: feature/erp-mes-iot-ota-audit-fix 前序批次 B10-c（后端 15 端点 + 13 权限码 + 2 菜单）
-- 库: `ry-vue`（系统库）；本脚本全部使用全限定名，可在任意默认库下执行
-- 幂等: 全部 INSERT IGNORE，可重复执行；无 ALTER、无数据清洗，不需要停机窗口
-- ID 占用核对（本地库已确认空闲）:
--   sys_dict_type 600209/600210/600211 —— 现有 iot 字典类型占用 600200-600208
--   sys_dict_data 600229-600239/600253 —— 现有占用到 600228，600252 已被 SPC 使用，故 CANCELLED 用 600253
-- 注意: sys_dict_type / sys_dict_data **没有 status 列**，列顺序与 script/sql/ry-vue.sql 中现有同类 INSERT 整行一致
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 字典类型
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.sys_dict_type VALUES (600209, '000000', 'IoT固件发布状态', 'iot_firmware_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT固件发布状态: DRAFT(草稿)/RELEASED(已发布)/DISABLED(已停用)');
INSERT IGNORE INTO `ry-vue`.sys_dict_type VALUES (600210, '000000', 'IoT固件校验算法', 'iot_firmware_checksum_type', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT固件包校验算法: MD5/SHA256');
INSERT IGNORE INTO `ry-vue`.sys_dict_type VALUES (600211, '000000', 'IoT升级任务状态', 'iot_ota_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT OTA升级任务状态: PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED');

-- -----------------------------------------------------------------------------
-- 2. 字典数据
--    列顺序: dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
--            css_class, list_class, is_default, create_dept, create_by,
--            create_time, update_by, update_time, remark
--    list_class 取 Element Plus 标签色: primary/success/info/warning/danger
-- -----------------------------------------------------------------------------

-- 2.1 iot_firmware_status（草稿/已发布/已停用）
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600229, '000000', 1, '草稿', 'DRAFT', 'iot_firmware_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '新登记固件为草稿，可编辑/删除，不可下发');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600230, '000000', 2, '已发布', 'RELEASED', 'iot_firmware_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已发布固件可下发升级任务，版本号与固件包冻结');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600231, '000000', 3, '已停用', 'DISABLED', 'iot_firmware_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已停用固件不再允许新建升级任务，可重新发布');

-- 2.2 iot_firmware_checksum_type（MD5/SHA256）
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600232, '000000', 1, 'MD5', 'MD5', 'iot_firmware_checksum_type', '', 'primary', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MD5 校验(小写十六进制)');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600233, '000000', 2, 'SHA256', 'SHA256', 'iot_firmware_checksum_type', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SHA256 校验(小写十六进制)');

-- 2.3 iot_ota_status（7 态：PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED）
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600234, '000000', 1, '已登记', 'PENDING', 'iot_ota_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '任务已登记，指令尚未确认下发');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600235, '000000', 2, '已下发', 'SENT', 'iot_ota_status', '', 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '指令已提交 MQTT Broker，等待设备回执');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600236, '000000', 3, '升级中', 'UPGRADING', 'iot_ota_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备已开始下载/刷写');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600237, '000000', 4, '成功', 'SUCCESS', 'iot_ota_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备回执升级成功，平台回写设备固件版本');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600238, '000000', 5, '失败', 'FAILED', 'iot_ota_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备回执升级失败(可人工重试)');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600239, '000000', 6, '超时', 'TIMEOUT', 'iot_ota_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '超时窗口内未收到任何回执，由超时兜底任务置位(可重试)');
INSERT IGNORE INTO `ry-vue`.sys_dict_data VALUES (600253, '000000', 7, '已取消', 'CANCELLED', 'iot_ota_status', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '人工取消(终态，与设备升级失败区分)');

-- -----------------------------------------------------------------------------
-- 3. 校验（执行后人工核对，期望 3 + 12 = 15 行）
-- -----------------------------------------------------------------------------
-- SELECT dict_id, dict_name, dict_type FROM `ry-vue`.sys_dict_type
--  WHERE dict_type IN ('iot_firmware_status','iot_firmware_checksum_type','iot_ota_status');
-- SELECT dict_code, dict_sort, dict_label, dict_value, list_class, is_default FROM `ry-vue`.sys_dict_data
--  WHERE dict_type IN ('iot_firmware_status','iot_firmware_checksum_type','iot_ota_status') ORDER BY dict_type, dict_sort;

-- -----------------------------------------------------------------------------
-- 4. 回滚（仅在需要撤销本批次前端字典时执行）
-- -----------------------------------------------------------------------------
-- DELETE FROM `ry-vue`.sys_dict_data
--  WHERE dict_code IN (600229,600230,600231,600232,600233,600234,600235,600236,600237,600238,600239,600253);
-- DELETE FROM `ry-vue`.sys_dict_type
--  WHERE dict_id IN (600209,600210,600211);
