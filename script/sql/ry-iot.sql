/*
 Navicat Premium Data Transfer

 Source Server         : 本地5.7
 Source Server Type    : MySQL
 Source Server Version : 50714 (5.7.14)
 Source Host           : localhost:3306
 Source Schema         : ry-iot

 Target Server Type    : MySQL
 Target Server Version : 50714 (5.7.14)
 File Encoding         : 65001

 Date: 21/06/2026 21:45:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for iot_device
-- ----------------------------
DROP TABLE IF EXISTS `iot_device`;
CREATE TABLE `iot_device`  (
  `device_id` bigint(20) NOT NULL COMMENT '设备ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '设备编码(MQTT ClientID)',
  `device_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '设备名称',
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品名称(冗余)',
  `protocol_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MQTT' COMMENT '协议类型: MQTT/OPC_UA/MODBUS/HTTP',
  `connection_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OFFLINE' COMMENT '连接状态: ONLINE/OFFLINE',
  `last_online_time` datetime NULL DEFAULT NULL COMMENT '最后在线时间',
  `device_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备密钥(MQTT认证)',
  `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `firmware_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件版本',
  `location` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '位置',
  `mes_equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES设备ID(接口查询,无外键)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`device_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`device_code`, `tenant_id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE,
  INDEX `idx_status`(`connection_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备实例' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of iot_device
-- ----------------------------
INSERT INTO `iot_device` VALUES (1, '000000', 'CNC001-TEMP', 'CNC001温度探头', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'test_secret_cnc001_temp_001', '192.168.1.101', 'v2.1.0', '注塑车间#1-A区 数控车床#1', 1, 'ACTIVE', '绑定MES设备CNC-001，监测主轴温度', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_device` VALUES (2, '000000', 'CNC001-VIB', 'CNC001振动探头', 2, '振动传感器模组', 'MQTT', 'OFFLINE', NULL, 'test_secret_cnc001_vib_002', '192.168.1.102', 'v2.0.1', '注塑车间#1-A区 数控车床#1', 1, 'ACTIVE', '绑定MES设备CNC-001，监测振动', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_device` VALUES (3, '000000', 'WELD001-TEMP', 'WELD001温度探头', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'test_secret_weld001_temp_003', '192.168.1.103', 'v2.1.0', '冲压车间#1-C区 自动焊接机#1', 3, 'ACTIVE', '绑定MES设备WELD-001，监测焊接温度', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_device` VALUES (4, '000000', 'ASM001-DIM', 'ASM001尺寸检测仪', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'test_secret_asm001_dim_004', '192.168.1.104', 'v1.5.0', '注塑车间#1-D区 装配流水线#1', 4, 'ACTIVE', '绑定MES设备ASM-001，装配尺寸检测(复用温度传感器产品类型)', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_device` VALUES (5, '000000', 'PKG001-CNT', 'PKG001包装计数器', 3, '环境数据采集网关', 'MQTT', 'OFFLINE', NULL, 'test_secret_pkg001_cnt_005', '192.168.1.105', 'v1.0.0', '注塑车间#1-E区 包装机#1', 5, 'ACTIVE', '绑定MES设备PKG-001，包装产出计数', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');

-- ----------------------------
-- Table structure for iot_device_data
-- ----------------------------
DROP TABLE IF EXISTS `iot_device_data`;
CREATE TABLE `iot_device_data`  (
  `data_id` bigint(20) NOT NULL COMMENT '数据ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '属性编码',
  `property_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '属性名称',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据类型: NUMERIC/TEXT/BOOL',
  `numeric_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '数值型值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文本型值',
  `bool_value` tinyint(1) NULL DEFAULT NULL COMMENT '布尔型值(0/1)',
  `collect_time` datetime NOT NULL COMMENT '采集时间(设备侧)',
  `receive_time` datetime NOT NULL COMMENT '接收时间(服务端)',
  `quality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GOOD' COMMENT '数据质量: GOOD/BAD/UNCERTAIN',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MQTT' COMMENT '数据来源: MQTT/OPC_UA/MODBUS/HTTP',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_device_time`(`device_id`, `collect_time`) USING BTREE,
  INDEX `idx_property`(`device_id`, `property_code`, `collect_time`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备数据(原始)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of iot_device_data
-- ----------------------------
INSERT INTO `iot_device_data` VALUES (1, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 45.2000, NULL, NULL, '2026-06-20 00:00:00', '2026-06-20 00:00:01', 'GOOD', 'MQTT', '2026-06-20 00:00:01');
INSERT INTO `iot_device_data` VALUES (2, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 43.8000, NULL, NULL, '2026-06-20 02:00:00', '2026-06-20 02:00:01', 'GOOD', 'MQTT', '2026-06-20 02:00:01');
INSERT INTO `iot_device_data` VALUES (3, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 44.1000, NULL, NULL, '2026-06-20 04:00:00', '2026-06-20 04:00:01', 'GOOD', 'MQTT', '2026-06-20 04:00:01');
INSERT INTO `iot_device_data` VALUES (4, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 46.5000, NULL, NULL, '2026-06-20 06:00:00', '2026-06-20 06:00:01', 'GOOD', 'MQTT', '2026-06-20 06:00:01');
INSERT INTO `iot_device_data` VALUES (5, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 72.5000, NULL, NULL, '2026-06-20 08:10:00', '2026-06-20 08:10:01', 'GOOD', 'MQTT', '2026-06-20 08:10:01');
INSERT INTO `iot_device_data` VALUES (6, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 74.1000, NULL, NULL, '2026-06-20 08:30:00', '2026-06-20 08:30:01', 'GOOD', 'MQTT', '2026-06-20 08:30:01');
INSERT INTO `iot_device_data` VALUES (7, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 76.3000, NULL, NULL, '2026-06-20 09:00:00', '2026-06-20 09:00:01', 'GOOD', 'MQTT', '2026-06-20 09:00:01');
INSERT INTO `iot_device_data` VALUES (8, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 87.5000, NULL, NULL, '2026-06-20 10:00:00', '2026-06-20 10:00:01', 'BAD', 'MQTT', '2026-06-20 10:00:01');
INSERT INTO `iot_device_data` VALUES (9, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 73.2000, NULL, NULL, '2026-06-20 10:15:00', '2026-06-20 10:15:01', 'GOOD', 'MQTT', '2026-06-20 10:15:01');
INSERT INTO `iot_device_data` VALUES (10, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 75.8000, NULL, NULL, '2026-06-20 11:00:00', '2026-06-20 11:00:01', 'GOOD', 'MQTT', '2026-06-20 11:00:01');
INSERT INTO `iot_device_data` VALUES (11, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 78.1000, NULL, NULL, '2026-06-20 12:00:00', '2026-06-20 12:00:01', 'GOOD', 'MQTT', '2026-06-20 12:00:01');
INSERT INTO `iot_device_data` VALUES (12, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 80.2000, NULL, NULL, '2026-06-20 14:00:00', '2026-06-20 14:00:01', 'GOOD', 'MQTT', '2026-06-20 14:00:01');
INSERT INTO `iot_device_data` VALUES (13, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 82.8000, NULL, NULL, '2026-06-20 15:00:00', '2026-06-20 15:00:01', 'GOOD', 'MQTT', '2026-06-20 15:00:01');
INSERT INTO `iot_device_data` VALUES (14, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 84.5000, NULL, NULL, '2026-06-20 16:00:00', '2026-06-20 16:00:01', 'GOOD', 'MQTT', '2026-06-20 16:00:01');
INSERT INTO `iot_device_data` VALUES (15, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 88.2000, NULL, NULL, '2026-06-20 16:30:00', '2026-06-20 16:30:01', 'BAD', 'MQTT', '2026-06-20 16:30:01');
INSERT INTO `iot_device_data` VALUES (16, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 70.1000, NULL, NULL, '2026-06-20 17:00:00', '2026-06-20 17:00:01', 'GOOD', 'MQTT', '2026-06-20 17:00:01');
INSERT INTO `iot_device_data` VALUES (17, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 68.3000, NULL, NULL, '2026-06-20 18:00:00', '2026-06-20 18:00:01', 'GOOD', 'MQTT', '2026-06-20 18:00:01');
INSERT INTO `iot_device_data` VALUES (18, '000000', 1, 'CNC001-TEMP', 'temperature', '主轴温度', 'NUMERIC', 50.5000, NULL, NULL, '2026-06-20 20:00:00', '2026-06-20 20:00:01', 'GOOD', 'MQTT', '2026-06-20 20:00:01');
INSERT INTO `iot_device_data` VALUES (19, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 1.2000, NULL, NULL, '2026-06-20 00:00:00', '2026-06-20 00:00:01', 'GOOD', 'MQTT', '2026-06-20 00:00:01');
INSERT INTO `iot_device_data` VALUES (20, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 1.0000, NULL, NULL, '2026-06-20 04:00:00', '2026-06-20 04:00:01', 'GOOD', 'MQTT', '2026-06-20 04:00:01');
INSERT INTO `iot_device_data` VALUES (21, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 2.8000, NULL, NULL, '2026-06-20 08:10:00', '2026-06-20 08:10:01', 'GOOD', 'MQTT', '2026-06-20 08:10:01');
INSERT INTO `iot_device_data` VALUES (22, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 3.2000, NULL, NULL, '2026-06-20 08:25:00', '2026-06-20 08:25:01', 'GOOD', 'MQTT', '2026-06-20 08:25:01');
INSERT INTO `iot_device_data` VALUES (23, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 3.5000, NULL, NULL, '2026-06-20 09:00:00', '2026-06-20 09:00:01', 'GOOD', 'MQTT', '2026-06-20 09:00:01');
INSERT INTO `iot_device_data` VALUES (24, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 6.5000, NULL, NULL, '2026-06-20 10:00:00', '2026-06-20 10:00:01', 'BAD', 'MQTT', '2026-06-20 10:00:01');
INSERT INTO `iot_device_data` VALUES (25, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 2.5000, NULL, NULL, '2026-06-20 10:30:00', '2026-06-20 10:30:01', 'GOOD', 'MQTT', '2026-06-20 10:30:01');
INSERT INTO `iot_device_data` VALUES (26, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 3.1000, NULL, NULL, '2026-06-20 12:00:00', '2026-06-20 12:00:01', 'GOOD', 'MQTT', '2026-06-20 12:00:01');
INSERT INTO `iot_device_data` VALUES (27, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 4.2000, NULL, NULL, '2026-06-20 14:00:00', '2026-06-20 14:00:01', 'GOOD', 'MQTT', '2026-06-20 14:00:01');
INSERT INTO `iot_device_data` VALUES (28, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 4.8000, NULL, NULL, '2026-06-20 16:00:00', '2026-06-20 16:00:01', 'GOOD', 'MQTT', '2026-06-20 16:00:01');
INSERT INTO `iot_device_data` VALUES (29, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 5.5000, NULL, NULL, '2026-06-20 16:30:00', '2026-06-20 16:30:01', 'BAD', 'MQTT', '2026-06-20 16:30:01');
INSERT INTO `iot_device_data` VALUES (30, '000000', 2, 'CNC001-VIB', 'vibration', '主轴振动', 'NUMERIC', 1.5000, NULL, NULL, '2026-06-20 20:00:00', '2026-06-20 20:00:01', 'GOOD', 'MQTT', '2026-06-20 20:00:01');
INSERT INTO `iot_device_data` VALUES (31, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 245.0000, NULL, NULL, '2026-06-20 08:10:00', '2026-06-20 08:10:01', 'GOOD', 'MQTT', '2026-06-20 08:10:01');
INSERT INTO `iot_device_data` VALUES (32, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 252.0000, NULL, NULL, '2026-06-20 09:00:00', '2026-06-20 09:00:01', 'GOOD', 'MQTT', '2026-06-20 09:00:01');
INSERT INTO `iot_device_data` VALUES (33, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 275.0000, NULL, NULL, '2026-06-20 10:00:00', '2026-06-20 10:00:01', 'GOOD', 'MQTT', '2026-06-20 10:00:01');
INSERT INTO `iot_device_data` VALUES (34, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 258.0000, NULL, NULL, '2026-06-20 11:00:00', '2026-06-20 11:00:01', 'GOOD', 'MQTT', '2026-06-20 11:00:01');
INSERT INTO `iot_device_data` VALUES (35, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 263.0000, NULL, NULL, '2026-06-20 14:00:00', '2026-06-20 14:00:01', 'GOOD', 'MQTT', '2026-06-20 14:00:01');
INSERT INTO `iot_device_data` VALUES (36, '000000', 3, 'WELD001-TEMP', 'temperature', '焊接温度', 'NUMERIC', 270.0000, NULL, NULL, '2026-06-20 16:00:00', '2026-06-20 16:00:01', 'GOOD', 'MQTT', '2026-06-20 16:00:01');
INSERT INTO `iot_device_data` VALUES (37, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 10.0200, NULL, NULL, '2026-06-20 08:30:00', '2026-06-20 08:30:01', 'GOOD', 'MQTT', '2026-06-20 08:30:01');
INSERT INTO `iot_device_data` VALUES (38, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 9.9800, NULL, NULL, '2026-06-20 09:00:00', '2026-06-20 09:00:01', 'GOOD', 'MQTT', '2026-06-20 09:00:01');
INSERT INTO `iot_device_data` VALUES (39, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 10.0300, NULL, NULL, '2026-06-20 10:00:00', '2026-06-20 10:00:01', 'GOOD', 'MQTT', '2026-06-20 10:00:01');
INSERT INTO `iot_device_data` VALUES (40, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 10.0100, NULL, NULL, '2026-06-20 11:00:00', '2026-06-20 11:00:01', 'GOOD', 'MQTT', '2026-06-20 11:00:01');
INSERT INTO `iot_device_data` VALUES (41, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 10.0600, NULL, NULL, '2026-06-20 14:00:00', '2026-06-20 14:00:01', 'BAD', 'MQTT', '2026-06-20 14:00:01');
INSERT INTO `iot_device_data` VALUES (42, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 10.0200, NULL, NULL, '2026-06-20 14:30:00', '2026-06-20 14:30:01', 'GOOD', 'MQTT', '2026-06-20 14:30:01');
INSERT INTO `iot_device_data` VALUES (43, '000000', 4, 'ASM001-DIM', 'dimension', '装配尺寸', 'NUMERIC', 9.9900, NULL, NULL, '2026-06-20 16:00:00', '2026-06-20 16:00:01', 'GOOD', 'MQTT', '2026-06-20 16:00:01');
INSERT INTO `iot_device_data` VALUES (44, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 25.0000, NULL, NULL, '2026-06-20 08:30:00', '2026-06-20 08:30:01', 'GOOD', 'MQTT', '2026-06-20 08:30:01');
INSERT INTO `iot_device_data` VALUES (45, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 28.0000, NULL, NULL, '2026-06-20 09:30:00', '2026-06-20 09:30:01', 'GOOD', 'MQTT', '2026-06-20 09:30:01');
INSERT INTO `iot_device_data` VALUES (46, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 30.0000, NULL, NULL, '2026-06-20 10:30:00', '2026-06-20 10:30:01', 'GOOD', 'MQTT', '2026-06-20 10:30:01');
INSERT INTO `iot_device_data` VALUES (47, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 35.0000, NULL, NULL, '2026-06-20 11:30:00', '2026-06-20 11:30:01', 'GOOD', 'MQTT', '2026-06-20 11:30:01');
INSERT INTO `iot_device_data` VALUES (48, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 22.0000, NULL, NULL, '2026-06-20 14:30:00', '2026-06-20 14:30:01', 'GOOD', 'MQTT', '2026-06-20 14:30:01');
INSERT INTO `iot_device_data` VALUES (49, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 27.0000, NULL, NULL, '2026-06-20 15:30:00', '2026-06-20 15:30:01', 'GOOD', 'MQTT', '2026-06-20 15:30:01');
INSERT INTO `iot_device_data` VALUES (50, '000000', 5, 'PKG001-CNT', 'count', '包装计数', 'NUMERIC', 32.0000, NULL, NULL, '2026-06-20 16:30:00', '2026-06-20 16:30:01', 'GOOD', 'MQTT', '2026-06-20 16:30:01');

-- ----------------------------
-- Table structure for iot_product
-- ----------------------------
DROP TABLE IF EXISTS `iot_product`;
CREATE TABLE `iot_product`  (
  `product_id` bigint(20) NOT NULL COMMENT '产品ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '产品编码',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '产品名称',
  `product_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DEVICE' COMMENT '产品类型: DEVICE/GATEWAY',
  `protocol_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MQTT' COMMENT '协议类型: MQTT/OPC_UA/MODBUS/HTTP',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`product_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`product_code`, `tenant_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT产品(设备模板)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of iot_product
-- ----------------------------
INSERT INTO `iot_product` VALUES (1, '000000', 'TEMP-SENSOR', '温度传感器模组', 'DEVICE', 'MQTT', '工业级温度传感器，支持-40~150℃，精度±0.5℃，MQTT协议上报', 'ACTIVE', '通用温度采集模组', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_product` VALUES (2, '000000', 'VIB-SENSOR', '振动传感器模组', 'DEVICE', 'MQTT', '三轴振动传感器，量程0-50mm/s，精度±0.1mm/s，MQTT协议上报', 'ACTIVE', '设备振动监测专用', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_product` VALUES (3, '000000', 'ENV-GATEWAY', '环境数据采集网关', 'GATEWAY', 'MQTT', '多功能环境网关，集成温度/湿度/气压，支持MQTT上云', 'ACTIVE', '环境监测网关', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');

SET FOREIGN_KEY_CHECKS = 1;
