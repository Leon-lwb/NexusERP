/*
 Navicat Premium Data Transfer

 Source Server         : whbuild
 Source Server Type    : MySQL
 Source Server Version : 50720
 Source Host           : localhost:3306
 Source Schema         : ry-iot

 Target Server Type    : MySQL
 Target Server Version : 50720
 File Encoding         : 65001

 Date: 29/06/2026 17:47:20
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
  `last_online_time` datetime(0) NULL DEFAULT NULL COMMENT '最后在线时间',
  `device_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备密钥(MQTT认证)',
  `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `firmware_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件版本',
  `location` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '位置',
  `mes_equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES设备ID(接口查询,无外键)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`device_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`device_code`, `tenant_id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE,
  INDEX `idx_status`(`connection_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备实例' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of iot_device
-- ----------------------------
INSERT INTO `iot_device` VALUES (1, '000000', 'CNC001-TEMP', 'CNC001温度探头', 1, '温度传感器模组', 'MQTT', 'OFFLINE', '2026-06-29 15:39:09', 'test_secret_cnc001_temp_001', '192.168.1.101', 'v2.1.0', '注塑车间#1-A区 数控车床#1', 1, 'ACTIVE', '绑定MES设备CNC-001，监测主轴温度', 100, 1, '2026-06-21 21:00:35', -1, '2026-06-29 15:51:18', '0');
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
  `collect_time` datetime(0) NOT NULL COMMENT '采集时间(设备侧)',
  `receive_time` datetime(0) NOT NULL COMMENT '接收时间(服务端)',
  `quality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GOOD' COMMENT '数据质量: GOOD/BAD/UNCERTAIN',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MQTT' COMMENT '数据来源: MQTT/OPC_UA/MODBUS/HTTP',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_device_time`(`device_id`, `collect_time`) USING BTREE,
  INDEX `idx_property`(`device_id`, `property_code`, `collect_time`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备数据(原始)' ROW_FORMAT = Dynamic;

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
INSERT INTO `iot_device_data` VALUES (2071498737508757506, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.5000, NULL, NULL, '2026-06-29 15:39:20', '2026-06-29 15:39:20', 'GOOD', 'MQTT', '2026-06-29 15:39:20');
INSERT INTO `iot_device_data` VALUES (2071499830334664706, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.6000, NULL, NULL, '2026-06-29 15:43:41', '2026-06-29 15:43:41', 'GOOD', 'MQTT', '2026-06-29 15:43:41');
INSERT INTO `iot_device_data` VALUES (2071500362893832193, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.0900, NULL, NULL, '2026-06-29 15:45:48', '2026-06-29 15:45:48', 'GOOD', 'MQTT', '2026-06-29 15:45:48');
INSERT INTO `iot_device_data` VALUES (2071500372020637698, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.6300, NULL, NULL, '2026-06-29 15:45:50', '2026-06-29 15:45:50', 'GOOD', 'MQTT', '2026-06-29 15:45:50');
INSERT INTO `iot_device_data` VALUES (2071500380388274178, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 27.3900, NULL, NULL, '2026-06-29 15:45:52', '2026-06-29 15:45:52', 'GOOD', 'MQTT', '2026-06-29 15:45:52');
INSERT INTO `iot_device_data` VALUES (2071500392941826049, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.5800, NULL, NULL, '2026-06-29 15:45:54', '2026-06-29 15:45:55', 'GOOD', 'MQTT', '2026-06-29 15:45:55');
INSERT INTO `iot_device_data` VALUES (2071500401343016962, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.6300, NULL, NULL, '2026-06-29 15:45:56', '2026-06-29 15:45:57', 'GOOD', 'MQTT', '2026-06-29 15:45:57');
INSERT INTO `iot_device_data` VALUES (2071500409702264834, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 26.9000, NULL, NULL, '2026-06-29 15:45:58', '2026-06-29 15:45:59', 'GOOD', 'MQTT', '2026-06-29 15:45:59');
INSERT INTO `iot_device_data` VALUES (2071500418082484225, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.4900, NULL, NULL, '2026-06-29 15:46:00', '2026-06-29 15:46:01', 'GOOD', 'MQTT', '2026-06-29 15:46:01');
INSERT INTO `iot_device_data` VALUES (2071500426525618177, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 28.3000, NULL, NULL, '2026-06-29 15:46:02', '2026-06-29 15:46:03', 'GOOD', 'MQTT', '2026-06-29 15:46:03');
INSERT INTO `iot_device_data` VALUES (2071500434960363522, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.8300, NULL, NULL, '2026-06-29 15:46:04', '2026-06-29 15:46:05', 'GOOD', 'MQTT', '2026-06-29 15:46:05');
INSERT INTO `iot_device_data` VALUES (2071500443239919618, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.1900, NULL, NULL, '2026-06-29 15:46:06', '2026-06-29 15:46:07', 'GOOD', 'MQTT', '2026-06-29 15:46:07');
INSERT INTO `iot_device_data` VALUES (2071500451683053569, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.6400, NULL, NULL, '2026-06-29 15:46:08', '2026-06-29 15:46:09', 'GOOD', 'MQTT', '2026-06-29 15:46:09');
INSERT INTO `iot_device_data` VALUES (2071500460054884354, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.5400, NULL, NULL, '2026-06-29 15:46:10', '2026-06-29 15:46:11', 'GOOD', 'MQTT', '2026-06-29 15:46:11');
INSERT INTO `iot_device_data` VALUES (2071500468481241089, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 28.3200, NULL, NULL, '2026-06-29 15:46:12', '2026-06-29 15:46:13', 'GOOD', 'MQTT', '2026-06-29 15:46:13');
INSERT INTO `iot_device_data` VALUES (2071500476836294658, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.7000, NULL, NULL, '2026-06-29 15:46:14', '2026-06-29 15:46:15', 'GOOD', 'MQTT', '2026-06-29 15:46:15');
INSERT INTO `iot_device_data` VALUES (2071500485258457090, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 27.5800, NULL, NULL, '2026-06-29 15:46:16', '2026-06-29 15:46:17', 'GOOD', 'MQTT', '2026-06-29 15:46:17');
INSERT INTO `iot_device_data` VALUES (2071500493554790402, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.2100, NULL, NULL, '2026-06-29 15:46:18', '2026-06-29 15:46:19', 'GOOD', 'MQTT', '2026-06-29 15:46:19');
INSERT INTO `iot_device_data` VALUES (2071500501981147137, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.0200, NULL, NULL, '2026-06-29 15:46:20', '2026-06-29 15:46:21', 'GOOD', 'MQTT', '2026-06-29 15:46:21');
INSERT INTO `iot_device_data` VALUES (2071500510394920962, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.3300, NULL, NULL, '2026-06-29 15:46:22', '2026-06-29 15:46:23', 'GOOD', 'MQTT', '2026-06-29 15:46:23');
INSERT INTO `iot_device_data` VALUES (2071500518766751745, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.0400, NULL, NULL, '2026-06-29 15:46:24', '2026-06-29 15:46:25', 'GOOD', 'MQTT', '2026-06-29 15:46:25');
INSERT INTO `iot_device_data` VALUES (2071500527109222401, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.1300, NULL, NULL, '2026-06-29 15:46:26', '2026-06-29 15:46:27', 'GOOD', 'MQTT', '2026-06-29 15:46:27');
INSERT INTO `iot_device_data` VALUES (2071500535502024706, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.2600, NULL, NULL, '2026-06-29 15:46:28', '2026-06-29 15:46:29', 'GOOD', 'MQTT', '2026-06-29 15:46:29');
INSERT INTO `iot_device_data` VALUES (2071500543903215618, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.7200, NULL, NULL, '2026-06-29 15:46:30', '2026-06-29 15:46:31', 'GOOD', 'MQTT', '2026-06-29 15:46:31');
INSERT INTO `iot_device_data` VALUES (2071500552325378049, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.5800, NULL, NULL, '2026-06-29 15:46:32', '2026-06-29 15:46:33', 'GOOD', 'MQTT', '2026-06-29 15:46:33');
INSERT INTO `iot_device_data` VALUES (2071500560667848706, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.2800, NULL, NULL, '2026-06-29 15:46:34', '2026-06-29 15:46:35', 'GOOD', 'MQTT', '2026-06-29 15:46:35');
INSERT INTO `iot_device_data` VALUES (2071500569102594049, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.2100, NULL, NULL, '2026-06-29 15:46:36', '2026-06-29 15:46:37', 'GOOD', 'MQTT', '2026-06-29 15:46:37');
INSERT INTO `iot_device_data` VALUES (2071500577445064705, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.8900, NULL, NULL, '2026-06-29 15:46:38', '2026-06-29 15:46:39', 'GOOD', 'MQTT', '2026-06-29 15:46:39');
INSERT INTO `iot_device_data` VALUES (2071500585900781570, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.7900, NULL, NULL, '2026-06-29 15:46:40', '2026-06-29 15:46:41', 'GOOD', 'MQTT', '2026-06-29 15:46:41');
INSERT INTO `iot_device_data` VALUES (2071500594322944002, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.6400, NULL, NULL, '2026-06-29 15:46:42', '2026-06-29 15:46:43', 'GOOD', 'MQTT', '2026-06-29 15:46:43');
INSERT INTO `iot_device_data` VALUES (2071500602631860225, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 26.4100, NULL, NULL, '2026-06-29 15:46:44', '2026-06-29 15:46:45', 'GOOD', 'MQTT', '2026-06-29 15:46:45');
INSERT INTO `iot_device_data` VALUES (2071500610986913793, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.5700, NULL, NULL, '2026-06-29 15:46:46', '2026-06-29 15:46:47', 'GOOD', 'MQTT', '2026-06-29 15:46:47');
INSERT INTO `iot_device_data` VALUES (2071500619417464834, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.7800, NULL, NULL, '2026-06-29 15:46:48', '2026-06-29 15:46:49', 'GOOD', 'MQTT', '2026-06-29 15:46:49');
INSERT INTO `iot_device_data` VALUES (2071500803228643330, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.2800, NULL, NULL, '2026-06-29 15:47:14', '2026-06-29 15:47:33', 'GOOD', 'MQTT', '2026-06-29 15:47:33');
INSERT INTO `iot_device_data` VALUES (2071500803274780674, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.2400, NULL, NULL, '2026-06-29 15:47:33', '2026-06-29 15:47:33', 'GOOD', 'MQTT', '2026-06-29 15:47:33');
INSERT INTO `iot_device_data` VALUES (2071500812342865921, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.4300, NULL, NULL, '2026-06-29 15:47:34', '2026-06-29 15:47:35', 'GOOD', 'MQTT', '2026-06-29 15:47:35');
INSERT INTO `iot_device_data` VALUES (2071500816256151553, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.8200, NULL, NULL, '2026-06-29 15:47:36', '2026-06-29 15:47:36', 'GOOD', 'MQTT', '2026-06-29 15:47:36');
INSERT INTO `iot_device_data` VALUES (2071500824338575362, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.5300, NULL, NULL, '2026-06-29 15:47:38', '2026-06-29 15:47:38', 'GOOD', 'MQTT', '2026-06-29 15:47:38');
INSERT INTO `iot_device_data` VALUES (2071500837491912705, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.0100, NULL, NULL, '2026-06-29 15:47:40', '2026-06-29 15:47:41', 'GOOD', 'MQTT', '2026-06-29 15:47:41');
INSERT INTO `iot_device_data` VALUES (2071500845876326401, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 20.3100, NULL, NULL, '2026-06-29 15:47:42', '2026-06-29 15:47:43', 'GOOD', 'MQTT', '2026-06-29 15:47:43');
INSERT INTO `iot_device_data` VALUES (2071500854260740098, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.2200, NULL, NULL, '2026-06-29 15:47:44', '2026-06-29 15:47:45', 'GOOD', 'MQTT', '2026-06-29 15:47:45');
INSERT INTO `iot_device_data` VALUES (2071500862724845570, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.7100, NULL, NULL, '2026-06-29 15:47:46', '2026-06-29 15:47:47', 'GOOD', 'MQTT', '2026-06-29 15:47:47');
INSERT INTO `iot_device_data` VALUES (2071500871100870658, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.1900, NULL, NULL, '2026-06-29 15:47:48', '2026-06-29 15:47:49', 'GOOD', 'MQTT', '2026-06-29 15:47:49');
INSERT INTO `iot_device_data` VALUES (2071500879439147010, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.5000, NULL, NULL, '2026-06-29 15:47:50', '2026-06-29 15:47:51', 'GOOD', 'MQTT', '2026-06-29 15:47:51');
INSERT INTO `iot_device_data` VALUES (2071500887861309441, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.7200, NULL, NULL, '2026-06-29 15:47:52', '2026-06-29 15:47:53', 'GOOD', 'MQTT', '2026-06-29 15:47:53');
INSERT INTO `iot_device_data` VALUES (2071500896199585794, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.5900, NULL, NULL, '2026-06-29 15:47:54', '2026-06-29 15:47:55', 'GOOD', 'MQTT', '2026-06-29 15:47:55');
INSERT INTO `iot_device_data` VALUES (2071500904604971010, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.3200, NULL, NULL, '2026-06-29 15:47:56', '2026-06-29 15:47:57', 'GOOD', 'MQTT', '2026-06-29 15:47:57');
INSERT INTO `iot_device_data` VALUES (2071500913043910658, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.0800, NULL, NULL, '2026-06-29 15:47:58', '2026-06-29 15:47:59', 'GOOD', 'MQTT', '2026-06-29 15:47:59');
INSERT INTO `iot_device_data` VALUES (2071500921407352834, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 28.9300, NULL, NULL, '2026-06-29 15:48:00', '2026-06-29 15:48:01', 'GOOD', 'MQTT', '2026-06-29 15:48:01');
INSERT INTO `iot_device_data` VALUES (2071500929783377921, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 22.7800, NULL, NULL, '2026-06-29 15:48:02', '2026-06-29 15:48:03', 'GOOD', 'MQTT', '2026-06-29 15:48:03');
INSERT INTO `iot_device_data` VALUES (2071500938239094786, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.5700, NULL, NULL, '2026-06-29 15:48:04', '2026-06-29 15:48:05', 'GOOD', 'MQTT', '2026-06-29 15:48:05');
INSERT INTO `iot_device_data` VALUES (2071500946581565441, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 28.4100, NULL, NULL, '2026-06-29 15:48:06', '2026-06-29 15:48:07', 'GOOD', 'MQTT', '2026-06-29 15:48:07');
INSERT INTO `iot_device_data` VALUES (2071500954957590529, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.6200, NULL, NULL, '2026-06-29 15:48:08', '2026-06-29 15:48:09', 'GOOD', 'MQTT', '2026-06-29 15:48:09');
INSERT INTO `iot_device_data` VALUES (2071500960284356610, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 29.8000, NULL, NULL, '2026-06-29 15:48:10', '2026-06-29 15:48:10', 'GOOD', 'MQTT', '2026-06-29 15:48:10');
INSERT INTO `iot_device_data` VALUES (2071500966873608193, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.0100, NULL, NULL, '2026-06-29 15:48:12', '2026-06-29 15:48:12', 'GOOD', 'MQTT', '2026-06-29 15:48:12');
INSERT INTO `iot_device_data` VALUES (2071500975291576322, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 23.1700, NULL, NULL, '2026-06-29 15:48:14', '2026-06-29 15:48:14', 'GOOD', 'MQTT', '2026-06-29 15:48:14');
INSERT INTO `iot_device_data` VALUES (2071500983696961537, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 25.2100, NULL, NULL, '2026-06-29 15:48:16', '2026-06-29 15:48:16', 'GOOD', 'MQTT', '2026-06-29 15:48:16');
INSERT INTO `iot_device_data` VALUES (2071500992081375234, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 27.6100, NULL, NULL, '2026-06-29 15:48:18', '2026-06-29 15:48:18', 'GOOD', 'MQTT', '2026-06-29 15:48:18');
INSERT INTO `iot_device_data` VALUES (2071501000482566146, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 28.2400, NULL, NULL, '2026-06-29 15:48:20', '2026-06-29 15:48:20', 'GOOD', 'MQTT', '2026-06-29 15:48:20');
INSERT INTO `iot_device_data` VALUES (2071501008866979842, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 21.2100, NULL, NULL, '2026-06-29 15:48:22', '2026-06-29 15:48:22', 'GOOD', 'MQTT', '2026-06-29 15:48:22');
INSERT INTO `iot_device_data` VALUES (2071501017284947969, '000000', 1, 'CNC001-TEMP', 'temperature', NULL, 'NUMERIC', 27.7800, NULL, NULL, '2026-06-29 15:48:24', '2026-06-29 15:48:24', 'GOOD', 'MQTT', '2026-06-29 15:48:24');

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
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`product_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`product_code`, `tenant_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT产品(设备模板)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of iot_product
-- ----------------------------
INSERT INTO `iot_product` VALUES (1, '000000', 'TEMP-SENSOR', '温度传感器模组', 'DEVICE', 'MQTT', '工业级温度传感器，支持-40~150℃，精度±0.5℃，MQTT协议上报', 'ACTIVE', '通用温度采集模组', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_product` VALUES (2, '000000', 'VIB-SENSOR', '振动传感器模组', 'DEVICE', 'MQTT', '三轴振动传感器，量程0-50mm/s，精度±0.1mm/s，MQTT协议上报', 'ACTIVE', '设备振动监测专用', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');
INSERT INTO `iot_product` VALUES (3, '000000', 'ENV-GATEWAY', '环境数据采集网关', 'GATEWAY', 'MQTT', '多功能环境网关，集成温度/湿度/气压，支持MQTT上云', 'ACTIVE', '环境监测网关', 100, 1, '2026-06-21 21:00:35', NULL, '2026-06-21 21:00:35', '0');

SET FOREIGN_KEY_CHECKS = 1;
