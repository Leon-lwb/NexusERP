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

 Date: 14/09/2026 18:08:48
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
INSERT INTO `iot_device` VALUES (1, '000000', 'DEV0001', '数控车床A-采集点', 4, '转速传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140001', '192.168.10.101', 'V1.0.0', '机加车间', 5, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (2, '000000', 'DEV0002', '数控车床B-采集点', 4, '转速传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140002', '192.168.10.102', 'V1.0.0', '机加车间', 6, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (3, '000000', 'DEV0003', '加工中心A-采集点', 4, '转速传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140003', '192.168.10.103', 'V1.0.0', '机加车间', 7, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (4, '000000', 'DEV0004', '铣床-采集点', 4, '转速传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140004', '192.168.10.104', 'V1.0.0', '机加车间', 8, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (5, '000000', 'DEV0005', '磨床-采集点', 2, '振动传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140005', '192.168.10.105', 'V1.0.0', '机加车间', 9, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (6, '000000', 'DEV0006', '氩弧焊工位-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140006', '192.168.10.106', 'V1.0.0', '焊接车间', 20, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (7, '000000', 'DEV0007', '二保焊工位-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140007', '192.168.10.107', 'V1.0.0', '焊接车间', 21, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (8, '000000', 'DEV0008', '点焊工位-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140008', '192.168.10.108', 'V1.0.0', '焊接车间', 22, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (9, '000000', 'DEV0009', '打磨工位-采集点', 2, '振动传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140009', '192.168.10.109', 'V1.0.0', '焊接车间', 23, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (10, '000000', 'DEV0010', '注塑机A-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140010', '192.168.10.110', 'V1.0.0', '注塑车间', 24, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (11, '000000', 'DEV0011', '注塑机B-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140011', '192.168.10.111', 'V1.0.0', '注塑车间', 25, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (12, '000000', 'DEV0012', '注塑机C-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140012', '192.168.10.112', 'V1.0.0', '注塑车间', 26, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (13, '000000', 'DEV0013', '熔炼炉-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140013', '192.168.10.113', 'V1.0.0', '铸造车间', 28, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (14, '000000', 'DEV0014', '淬火炉-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140014', '192.168.10.114', 'V1.0.0', '热处理车间', 31, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (15, '000000', 'DEV0015', '回火炉-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140015', '192.168.10.115', 'V1.0.0', '热处理车间', 32, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (16, '000000', 'DEV0016', '渗碳炉-采集点', 1, '温度传感器模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140016', '192.168.10.116', 'V1.0.0', '热处理车间', 33, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (17, '000000', 'DEV0017', 'SMT贴片线-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140017', '192.168.10.117', 'V1.0.0', '电子车间', 39, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (18, '000000', 'DEV0018', '装配线A-采集点', 5, '工业视觉相机', 'MQTT', 'OFFLINE', NULL, 'SEC202609140018', '192.168.10.118', 'V1.0.0', '装配车间', 46, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (19, '000000', 'DEV0019', '装配线B-采集点', 5, '工业视觉相机', 'MQTT', 'OFFLINE', NULL, 'SEC202609140019', '192.168.10.119', 'V1.0.0', '装配车间', 47, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (20, '000000', 'DEV0020', '功能测试工位-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140020', '192.168.10.120', 'V1.0.0', '测试车间', 49, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (21, '000000', 'DEV0021', '气密测试台-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140021', '192.168.10.121', 'V1.0.0', '测试车间', 51, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (22, '000000', 'DEV0022', '包装线A-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140022', '192.168.10.122', 'V1.0.0', '包装车间', 53, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (23, '000000', 'DEV0023', '包装线B-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140023', '192.168.10.123', 'V1.0.0', '包装车间', 54, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_device` VALUES (24, '000000', 'DEV0024', '打包工位-采集点', 3, '电能表模组', 'MQTT', 'OFFLINE', NULL, 'SEC202609140024', '192.168.10.124', 'V1.0.0', '包装车间', 55, 'ACTIVE', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');

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
  `source_msg_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源消息ID(Redis Stream entry id,幂等去重键)',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`data_id`) USING BTREE,
  UNIQUE INDEX `uk_source_msg_id`(`source_msg_id`) USING BTREE,
  INDEX `idx_device_time`(`device_id`, `collect_time`) USING BTREE,
  INDEX `idx_property`(`device_id`, `property_code`, `collect_time`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'IoT设备数据(原始)' ROW_FORMAT = Dynamic;

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
INSERT INTO `iot_product` VALUES (1, '000000', 'TEMP-SENSOR', '温度传感器模组', 'SENSOR', 'MQTT', '温度传感器模组 数据采集模板', '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_product` VALUES (2, '000000', 'VIB-SENSOR', '振动传感器模组', 'SENSOR', 'MQTT', '振动传感器模组 数据采集模板', '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_product` VALUES (3, '000000', 'POWER-METER', '电能表模组', 'METER', 'MQTT', '电能表模组 数据采集模板', '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_product` VALUES (4, '000000', 'SPEED-SENSOR', '转速传感器模组', 'SENSOR', 'MQTT', '转速传感器模组 数据采集模板', '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');
INSERT INTO `iot_product` VALUES (5, '000000', 'VISION-CAM', '工业视觉相机', 'CAMERA', 'HTTP', '工业视觉相机 数据采集模板', '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0');

-- ----------------------------
-- Table structure for iot_alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_rule`;
CREATE TABLE `iot_alert_rule`  (
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

-- ----------------------------
-- Records of iot_alert_rule (与 application.yml 的 alert-thresholds 兜底默认保持一致)
-- ----------------------------
INSERT INTO `iot_alert_rule` VALUES (7001, '000000', '温度超上限(全局默认)', 0, 0, 'temperature', 'GT', 80.0000, 0, 60, 'TEMPERATURE', 'HIGH', 'SSE,MES_ANDON', '1', '与 nexus.iot.pipeline.alert-thresholds.temperature 等价的库内规则(库内有规则时优先于YAML)', 103, 1, '2026-09-20 00:00:00', NULL, '2026-09-20 00:00:00');
INSERT INTO `iot_alert_rule` VALUES (7002, '000000', '振动烈度超上限(全局默认)', 0, 0, 'vibration', 'GT', 4.5000, 30, 60, 'VIBRATION', 'MEDIUM', 'SSE,MES_ANDON', '1', '连续30秒超限才告警, 去抖窗口60秒', 103, 1, '2026-09-20 00:00:00', NULL, '2026-09-20 00:00:00');

-- ----------------------------
-- Table structure for iot_alert_record
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_record`;
CREATE TABLE `iot_alert_record`  (
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
  `active_flag` tinyint(1) NULL DEFAULT 1 COMMENT '未恢复标记(1=未恢复, NULL=已恢复); 唯一键组成, 保证同一设备+属性仅一条未恢复告警',
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

-- ----------------------------
-- Table structure for iot_alert_notify_log
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_notify_log`;
CREATE TABLE `iot_alert_notify_log`  (
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

SET FOREIGN_KEY_CHECKS = 1;
