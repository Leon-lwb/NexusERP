-- =============================================
-- NexusERP - IoT模块 Week 35 字典数据初始化SQL
-- 目标库: ry-vue (sys_dict_type / sys_dict_data)
-- 字典类型ID段: 600200~600203 (避开 Week 33 菜单 6000~6025 段 / ERP字典 6001xx 段)
-- 字典数据ID段: 600200~600209
-- 创建时间: 2026-06-18
-- 说明: IoT字段使用 ACTIVE/INACTIVE、ONLINE/OFFLINE 等字符串，与系统字典 sys_normal_disable(0/1) 不兼容，
--       故新建 IoT 专用字典，供前端 dict-tag 渲染。INSERT IGNORE 幂等。
-- 菜单: Week 33 iot-week33-02-menu.sql 已建 (6000~6025)，本周不产出菜单 SQL。
-- =============================================

USE `ry-vue`;

-- ============================================================
-- 1. 字典类型 - 产品类型
-- ============================================================
INSERT IGNORE INTO `sys_dict_type` VALUES (600200, '000000', 'IoT产品类型', 'product_type', 103, 1, NOW(), NULL, NULL, 'IoT产品类型: 直连设备/网关');

INSERT IGNORE INTO `sys_dict_data` VALUES (600200, '000000', 1, '直连设备', 'DEVICE', 'product_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '直连设备');
INSERT IGNORE INTO `sys_dict_data` VALUES (600201, '000000', 2, '网关', 'GATEWAY', 'product_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '网关设备');

-- ============================================================
-- 2. 字典类型 - IoT协议类型
-- ============================================================
INSERT IGNORE INTO `sys_dict_type` VALUES (600201, '000000', 'IoT协议类型', 'iot_protocol_type', 103, 1, NOW(), NULL, NULL, 'IoT协议类型: MQTT/OPC_UA/MODBUS/HTTP');

INSERT IGNORE INTO `sys_dict_data` VALUES (600202, '000000', 1, 'MQTT', 'MQTT', 'iot_protocol_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, 'MQTT协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (600203, '000000', 2, 'OPC UA', 'OPC_UA', 'iot_protocol_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, 'OPC UA协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (600204, '000000', 3, 'Modbus', 'MODBUS', 'iot_protocol_type', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'Modbus协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (600205, '000000', 4, 'HTTP', 'HTTP', 'iot_protocol_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, 'HTTP协议');

-- ============================================================
-- 3. 字典类型 - IoT连接状态
-- ============================================================
INSERT IGNORE INTO `sys_dict_type` VALUES (600202, '000000', 'IoT连接状态', 'iot_connection_status', 103, 1, NOW(), NULL, NULL, 'IoT设备连接状态: 在线/离线');

INSERT IGNORE INTO `sys_dict_data` VALUES (600206, '000000', 1, '在线', 'ONLINE', 'iot_connection_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '设备在线');
INSERT IGNORE INTO `sys_dict_data` VALUES (600207, '000000', 2, '离线', 'OFFLINE', 'iot_connection_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '设备离线');

-- ============================================================
-- 4. 字典类型 - IoT设备状态
-- ============================================================
INSERT IGNORE INTO `sys_dict_type` VALUES (600203, '000000', 'IoT设备状态', 'iot_device_status', 103, 1, NOW(), NULL, NULL, 'IoT设备启用状态: 启用/停用');

INSERT IGNORE INTO `sys_dict_data` VALUES (600208, '000000', 1, '启用', 'ACTIVE', 'iot_device_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '启用');
INSERT IGNORE INTO `sys_dict_data` VALUES (600209, '000000', 2, '停用', 'INACTIVE', 'iot_device_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '停用');

-- ============================================================
-- 验证(执行后人工核对):
-- SELECT dict_id, dict_name, dict_type FROM sys_dict_type WHERE dict_id BETWEEN 600200 AND 600203 ORDER BY dict_id;
-- SELECT dict_code, dict_label, dict_value, dict_type FROM sys_dict_data WHERE dict_code BETWEEN 600200 AND 600209 ORDER BY dict_type, dict_sort;
-- 预期: 4 个字典类型 + 10 条字典数据
-- ============================================================
