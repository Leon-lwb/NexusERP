/*
 Navicat Premium Data Transfer

 Source Server         : whbuild
 Source Server Type    : MySQL
 Source Server Version : 50720
 Source Host           : localhost:3306
 Source Schema         : ry-mes

 Target Server Type    : MySQL
 Target Server Version : 50720
 File Encoding         : 65001

 Date: 14/09/2026 18:07:57
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bak_20260914_dc_collection_point
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_dc_collection_point`;
CREATE TABLE `bak_20260914_dc_collection_point`  (
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采集点编码',
  `point_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采集点名称',
  `point_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MANUAL/AUTO',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联设备ID',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT '关联 IoT 设备 ID（AUTO 类型必填）',
  `iot_property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IoT 属性编码（对应 iot_device_data.property_code）',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `frequency` int(11) NULL DEFAULT NULL COMMENT '采集频率(秒)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `last_archive_time` datetime(0) NULL DEFAULT NULL COMMENT '最后成功归档时间（数据补偿游标）',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_dc_collection_point
-- ----------------------------
INSERT INTO `bak_20260914_dc_collection_point` VALUES (1, '000000', 'CP-TEMP-001', 'CNC001主轴温度', 'AUTO', 1, NULL, NULL, 1, 'NUMERIC', '℃', 60, 'ACTIVE', '数控车床#1主轴温度自动采集，每60秒', NULL, 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (2, '000000', 'CP-VIB-001', 'CNC001振动', 'AUTO', 1, NULL, NULL, 1, 'NUMERIC', 'mm/s', 30, 'ACTIVE', '数控车床#1振动值自动采集，每30秒', NULL, 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (3, '000000', 'CP-DIM-001', '装配尺寸检验', 'MANUAL', 4, NULL, NULL, 3, 'NUMERIC', 'mm', NULL, 'ACTIVE', '装配工序关键尺寸人工采集', NULL, 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (4, '000000', 'CP-WELD-TEMP', '焊接温度监控', 'AUTO', 3, NULL, NULL, 2, 'NUMERIC', '℃', 10, 'ACTIVE', '焊接温度自动采集，每10秒', NULL, 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (5, '000000', 'CP-PKG-CNT', '包装计数', 'AUTO', 5, NULL, NULL, 5, 'NUMERIC', '件', 60, 'ACTIVE', '包装机产出计数，每60秒', NULL, 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (2082453690429710337, '000000', 'E2E-AUTO-IOT', 'E2E自动采集点', 'AUTO', NULL, 1, 'temperature', NULL, 'NUMERIC', '℃', 10, 'ACTIVE', 'p0-8 e2e', NULL, 103, 1, '2026-07-29 21:10:25', 1, '2026-07-29 21:10:25', '1');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (2082453690815586305, '000000', 'E2E-MANUAL', 'E2E手动采集点', 'MANUAL', NULL, NULL, NULL, NULL, 'TEXT', NULL, NULL, 'ACTIVE', 'p0-8 e2e', NULL, 103, 1, '2026-07-29 21:10:25', 1, '2026-07-29 21:10:25', '1');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (2082454791119937537, '000000', 'E2E-AUTO-IOT-330886', 'E2E自动采集点', 'AUTO', NULL, 1, 'temperature', NULL, 'NUMERIC', '℃', 10, 'ACTIVE', 'p0-8 e2e', NULL, 103, 1, '2026-07-29 21:14:47', 1, '2026-07-29 21:14:47', '1');
INSERT INTO `bak_20260914_dc_collection_point` VALUES (2082454791241572353, '000000', 'E2E-MANUAL-330886', 'E2E手动采集点', 'MANUAL', NULL, NULL, NULL, NULL, 'TEXT', NULL, NULL, 'ACTIVE', 'p0-8 e2e', NULL, 103, 1, '2026-07-29 21:14:47', 1, '2026-07-29 21:14:47', '1');

-- ----------------------------
-- Table structure for bak_20260914_dc_data_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_dc_data_record`;
CREATE TABLE `bak_20260914_dc_data_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采集点编码(冗余)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IoT 属性编码（source=IoT 时填充）',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序记录ID',
  `numeric_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文本值',
  `collect_time` datetime(0) NOT NULL COMMENT '采集时间',
  `collect_by` bigint(20) NULL DEFAULT NULL COMMENT '采集人(手动时)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_dc_data_record
-- ----------------------------
INSERT INTO `bak_20260914_dc_data_record` VALUES (1, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 72.5000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (2, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 74.1000, NULL, '2026-06-10 08:20:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (3, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 87.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC主轴温度-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (4, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 73.2000, NULL, '2026-06-10 08:40:00', NULL, 'IoT', 'CNC主轴温度-恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (5, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 75.8000, NULL, '2026-06-10 08:50:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (6, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 2.8000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (7, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 3.2000, NULL, '2026-06-10 08:25:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (8, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 5.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC振动值-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (9, '000000', 3, 'CP-DIM-001', NULL, 1, 1, 3, 10.0200, NULL, '2026-06-10 15:00:00', 30005, 'MANUAL', '装配关键尺寸检验', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (10, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 245.0000, NULL, '2026-06-11 09:10:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (11, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 252.0000, NULL, '2026-06-11 09:20:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_dc_data_record` VALUES (12, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 258.0000, NULL, '2026-06-11 09:30:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_eq_equipment
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_equipment`;
CREATE TABLE `bak_20260914_eq_equipment`  (
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编码',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备名称',
  `equipment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备类型',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '型号',
  `manufacturer` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '制造商',
  `serial_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出厂编号',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `location` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '安装位置',
  `purchase_date` date NULL DEFAULT NULL COMMENT '购置日期',
  `warranty_date` date NULL DEFAULT NULL COMMENT '保修到期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
  `inspection_items` json NULL COMMENT '点检项目及标准(JSON)',
  `last_inspection` json NULL COMMENT '最近一次点检结果(JSON)',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT '关联IoT设备ID(预留,无外键)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_eq_equipment
-- ----------------------------
INSERT INTO `bak_20260914_eq_equipment` VALUES (1, '000000', 'CNC-001', '数控车床#1', 'CNC', 'CK6140', '沈阳机床厂', 'SY20250001', 1, 'GZZ0002', '注塑车间#1-A区', '2025-03-15', '2027-03-15', 'NORMAL', '[{\"name\": \"主轴温度\", \"unit\": \"℃\", \"threshold\": 80}, {\"name\": \"液压油位\", \"unit\": \"\", \"threshold\": \"正常\"}, {\"name\": \"刀具磨损\", \"unit\": \"mm\", \"threshold\": 0.5}]', '{\"主轴温度\": \"72℃\", \"刀具磨损\": \"0.2mm\", \"检查时间\": \"2026-06-20\", \"液压油位\": \"正常\"}', 1, '主力数控车床', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_equipment` VALUES (2, '000000', 'CNC-002', '数控车床#2', 'CNC', 'CK6150', '大连机床厂', 'DL20250002', 2, 'GZZ0003', '冲压车间#1-B区', '2025-06-01', '2027-06-01', 'NORMAL', '[{\"name\": \"主轴温度\", \"unit\": \"℃\", \"threshold\": 80}, {\"name\": \"冷却液浓度\", \"unit\": \"\", \"threshold\": \"5%\"}]', '{\"主轴温度\": \"68℃\", \"检查时间\": \"2026-06-20\", \"冷却液浓度\": \"6%\"}', NULL, '数控车床备用机', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_equipment` VALUES (3, '000000', 'WELD-001', '自动焊接机#1', 'WELDING', 'YD-350FR', '松下焊接', 'XH20250003', 2, 'GZZ0003', '冲压车间#1-C区', '2025-01-10', '2026-07-10', 'NORMAL', '[{\"name\": \"焊接电流\", \"unit\": \"A\", \"threshold\": 300}, {\"name\": \"气体流量\", \"unit\": \"L/min\", \"threshold\": 20}]', '{\"检查时间\": \"2026-06-20\", \"气体流量\": \"18L/min\", \"焊接电流\": \"250A\"}', NULL, '自动焊接设备', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_equipment` VALUES (4, '000000', 'ASM-001', '装配流水线#1', 'ASSEMBLY', 'APL-2000', '自制设备', 'AS20250004', 1, 'GZZ0002', '注塑车间#1-D区', '2024-09-01', '2026-09-01', 'NORMAL', '[{\"name\": \"传送带速度\", \"unit\": \"m/min\", \"threshold\": 10}, {\"name\": \"气压\", \"unit\": \"MPa\", \"threshold\": 0.6}]', '{\"气压\": \"0.65MPa\", \"检查时间\": \"2026-06-20\", \"传送带速度\": \"8m/min\"}', NULL, '装配主产线', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_equipment` VALUES (5, '000000', 'PKG-001', '包装机#1', 'PACKAGING', 'ZB-100', '浙江包装机械', 'PK20250005', 3, 'GZZ0004', '注塑车间#1-E区', '2025-05-01', '2027-05-01', 'NORMAL', '[{\"name\": \"封口温度\", \"unit\": \"℃\", \"threshold\": 200}, {\"name\": \"包装速度\", \"unit\": \"件/min\", \"threshold\": 30}]', '{\"包装速度\": \"25件/min\", \"封口温度\": \"180℃\", \"检查时间\": \"2026-06-20\"}', NULL, '成品包装设备', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_equipment` VALUES (2099000000000000401, '000000', 'QC-CHECK-001', '质检工作台#1', 'INSPECTION', 'QC-BENCH-01', '测试设备', 'QC20260001', 16, 'GZZ0017', '测试车间-QC工位', '2026-01-01', '2028-01-01', 'NORMAL', '[{\"item\": \"卡尺校准标签\", \"standard\": \"在有效期内\"}, {\"item\": \"外观检查灯亮度\", \"standard\": \"正常\"}, {\"item\": \"静电手环导通\", \"standard\": \"小于1兆欧\"}]', NULL, NULL, 'QC测试工位点检设备', -1, -1, '2026-08-22 19:43:49', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_eq_health_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_health_record`;
CREATE TABLE `bak_20260914_eq_health_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT 'IoT设备ID',
  `score` int(11) NOT NULL COMMENT '健康评分(0-100)',
  `grade` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '等级: HIGH(>=80)/MEDIUM(>=60)/LOW(<60)',
  `anomaly_points` int(11) NULL DEFAULT 0 COMMENT '24h异常数据点数(z>3)',
  `repair_count_90d` int(11) NULL DEFAULT 0 COMMENT '90天内维修单数',
  `open_repair_count` int(11) NULL DEFAULT 0 COMMENT '未闭环维修单数',
  `detail_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '明细JSON[{propertyCode,propertyName,mean,std,anomalyPoints}]',
  `prev_score` int(11) NULL DEFAULT NULL COMMENT '前次评分(对比趋势)',
  `score_change` int(11) NULL DEFAULT NULL COMMENT '评分变化(本次-前次)',
  `record_date` date NOT NULL COMMENT '评分日期',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_inspection_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_inspection_record`;
CREATE TABLE `bak_20260914_eq_inspection_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位编码(工位终端提交时留痕)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '点检人ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '点检人姓名',
  `operator_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '点检人工号(设备通道免登录留痕)',
  `inspect_time` datetime(0) NOT NULL COMMENT '点检时间',
  `results` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '逐项点检明细 JSON: [{item,standard,actual,pass}]',
  `overall_pass` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '总判定: Y合格/N不合格',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_maintenance_plan`;
CREATE TABLE `bak_20260914_eq_maintenance_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `maintenance_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
  `cycle_days` int(11) NULL DEFAULT NULL COMMENT '周期(天)',
  `next_date` date NULL DEFAULT NULL COMMENT '下次保养日期(自动计算)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '保养内容',
  `responsible_id` bigint(20) NULL DEFAULT NULL COMMENT '负责人ID',
  `responsible_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_eq_maintenance_plan
-- ----------------------------
INSERT INTO `bak_20260914_eq_maintenance_plan` VALUES (1, '000000', 'CNC001日常保养', 1, 'DAILY', 1, '2026-06-22', '1. 检查主轴润滑系统\n2. 清理铁屑\n3. 检查冷却液液位\n4. 检查导轨防护', 1, '张三', 'ACTIVE', '每日开工前执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_maintenance_plan` VALUES (2, '000000', 'CNC001月度保养', 1, 'MONTHLY', 30, '2026-07-20', '1. 主轴精度校准\n2. 冷却液更换\n3. 液压油过滤\n4. 电气系统检查\n5. 丝杠间隙补偿', 1, '张三', 'ACTIVE', '每月20日执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_maintenance_plan` VALUES (3, '000000', 'WELD001周度保养', 3, 'WEEKLY', 7, '2026-06-28', '1. 焊枪喷嘴清理\n2. 送丝机构检查\n3. 气体管路检查\n4. 接地线检查', 2, '李四', 'ACTIVE', '每周五执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_maintenance_plan` VALUES (4, '000000', 'ASM001季度保养', 4, 'QUARTERLY', 90, '2026-09-20', '1. 传动链条张紧度调整\n2. 轴承润滑\n3. 传感器校准\n4. 安全门开关检测', 1, '张三', 'ACTIVE', '每季度执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_maintenance_plan` VALUES (5, '000000', 'PKG001年度保养', 5, 'YEARLY', 365, '2027-06-20', '1. 全机大修\n2. 易损件更换\n3. 精度全面校准\n4. 电气系统全面检测', 1, '张三', 'ACTIVE', '年度大保养', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_eq_maintenance_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_maintenance_record`;
CREATE TABLE `bak_20260914_eq_maintenance_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '关联保养计划ID(可空)',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `maintenance_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '保养类型(冗余)',
  `executor_id` bigint(20) NULL DEFAULT NULL COMMENT '执行人ID',
  `executor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '执行人姓名',
  `execute_time` datetime(0) NULL DEFAULT NULL COMMENT '执行时间',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '保养内容/执行情况',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ABNORMAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_oee_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_oee_record`;
CREATE TABLE `bak_20260914_eq_oee_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称(冗余)',
  `equipment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备类型(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(冗余, 车间级聚合用)',
  `stat_date` date NOT NULL COMMENT '统计日期(日粒度)',
  `planned_minutes` decimal(10, 2) NULL DEFAULT 480.00 COMMENT '计划生产时长(分钟, 首版默认480=8h单班)',
  `downtime_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '停机时长(分钟, 来自 eq_repair.downtime_hours 求和)',
  `maintenance_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '保养占用时长(分钟, 预留, 首版0)',
  `run_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '运行时长(分钟, = planned - downtime - maintenance)',
  `fault_count` int(11) NULL DEFAULT 0 COMMENT '故障次数(eq_repair 当日报修数)',
  `availability` decimal(6, 4) NULL DEFAULT 0.0000 COMMENT '可用率/稼动率(0~1), = run/planned',
  `performance_rate` decimal(6, 4) NULL DEFAULT 1.0000 COMMENT '表现率(首版占位1.0, 待工单报工数据补全)',
  `quality_rate` decimal(6, 4) NULL DEFAULT 1.0000 COMMENT '质量率(首版占位1.0, 待质量数据补全)',
  `oee` decimal(6, 4) NULL DEFAULT 0.0000 COMMENT 'OEE = availability * performance * quality',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_patrol_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_patrol_record`;
CREATE TABLE `bak_20260914_eq_patrol_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `task_id` bigint(20) NULL DEFAULT NULL COMMENT '关联任务ID(可空, 手动巡检无任务)',
  `route_id` bigint(20) NULL DEFAULT NULL COMMENT '关联路线ID(冗余)',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称(冗余)',
  `check_item` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检查项目',
  `actual_value` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实际值/读数',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ABNORMAL',
  `photo_oss_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '照片ossId',
  `executor_id` bigint(20) NULL DEFAULT NULL COMMENT '执行人ID',
  `executor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '执行人姓名',
  `execute_time` datetime(0) NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_patrol_route
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_patrol_route`;
CREATE TABLE `bak_20260914_eq_patrol_route`  (
  `route_id` bigint(20) NOT NULL COMMENT '路线ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `route_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路线描述',
  `equipment_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '设备ID列表(JSON数组)',
  `check_items` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '通用检查项(JSON数组)',
  `frequency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DAILY' COMMENT '频率 DAILY/WEEKLY/MONTHLY',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_patrol_task
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_patrol_task`;
CREATE TABLE `bak_20260914_eq_patrol_task`  (
  `task_id` bigint(20) NOT NULL COMMENT '任务ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `route_id` bigint(20) NOT NULL COMMENT '关联路线ID',
  `route_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路线名称(冗余)',
  `plan_date` date NOT NULL COMMENT '计划巡检日期',
  `actual_date` datetime(0) NULL DEFAULT NULL COMMENT '实际完成时间',
  `assigned_to_id` bigint(20) NULL DEFAULT NULL COMMENT '指派人ID',
  `assigned_to_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '指派人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_repair
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_repair`;
CREATE TABLE `bak_20260914_eq_repair`  (
  `repair_id` bigint(20) NOT NULL COMMENT '维修ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `repair_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修单号',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `fault_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '故障类型',
  `fault_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '故障描述',
  `reporter_id` bigint(20) NULL DEFAULT NULL COMMENT '报修人ID',
  `reporter_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报修人姓名',
  `repairer_id` bigint(20) NULL DEFAULT NULL COMMENT '维修人ID',
  `repairer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '维修人姓名',
  `repair_start` datetime(0) NULL DEFAULT NULL COMMENT '维修开始时间',
  `repair_end` datetime(0) NULL DEFAULT NULL COMMENT '维修结束时间',
  `downtime_hours` decimal(10, 2) NULL DEFAULT NULL COMMENT '停机时长(小时)',
  `repair_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '维修成本',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_eq_repair
-- ----------------------------
INSERT INTO `bak_20260914_eq_repair` VALUES (1, '000000', 'RP-001', 1, 'TEMP_OVERHEAT', '数控车床#1主轴温度持续超过85℃，导致自动停机保护，需检查冷却系统', 30003, '王五', 30001, '张三', '2026-06-15 10:30:00', '2026-06-15 14:00:00', 3.50, 800.00, 'COMPLETED', '主轴冷却系统管路堵塞，清理后恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_eq_repair` VALUES (2, '000000', 'RP-002', 3, 'CURRENT_ABNORMAL', '自动焊接机#1焊接电流不稳定，波动范围±50A，焊接质量受影响', 30002, '李四', NULL, NULL, NULL, NULL, 0.00, 0.00, 'REPORTED', '焊接电流波动异常，待维修', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_eq_repair_spare
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_repair_spare`;
CREATE TABLE `bak_20260914_eq_repair_spare`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `repair_id` bigint(20) NOT NULL COMMENT '维修单ID',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件名称(冗余)',
  `spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格(冗余)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '领用数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_spare_part
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_spare_part`;
CREATE TABLE `bak_20260914_eq_spare_part`  (
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备件编码(唯一)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备件名称',
  `spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '型号',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类(字典 mes_spare_category)',
  `safety_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '安全库存',
  `current_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '当前库存(冗余, 出入库流水维护)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_eq_spare_stock_log
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_eq_spare_stock_log`;
CREATE TABLE `bak_20260914_eq_spare_stock_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '流水ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件名称(冗余)',
  `transaction_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN/OUT/REPAIR_USE',
  `quantity` decimal(14, 4) NOT NULL COMMENT '变动数量(正数)',
  `before_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '变动前库存',
  `after_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '变动后库存',
  `ref_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联单据类型 MANUAL/REPAIR',
  `ref_id` bigint(20) NULL DEFAULT NULL COMMENT '关联单据ID',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `operate_time` datetime(0) NULL DEFAULT NULL COMMENT '操作时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_lt_lot
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_lt_lot`;
CREATE TABLE `bak_20260914_lt_lot`  (
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次编码',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `supplier_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称(冗余)',
  `supplier_lot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商批次号',
  `quantity` decimal(14, 4) NOT NULL COMMENT '批次数量',
  `consumed_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '已消耗数量',
  `produced_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '有效期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IN_STOCK' COMMENT '状态: IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED',
  `qc_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT 'QC状态: PENDING/PASS/FAIL',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT 'IQC检验单ID(关联qc_iqc.iqc_id,可空;关联后IQC完成自动回写qc_status)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_lt_lot
-- ----------------------------
INSERT INTO `bak_20260914_lt_lot` VALUES (2081043029287907330, '000000', 'LOT-20260725-001', 1, '', '', 1, '', '', 0.0000, 0.0000, NULL, NULL, 'IN_STOCK', 'PENDING', NULL, '', 103, 1, '2026-07-25 23:44:57', 1, '2026-07-25 23:44:57', '0');
INSERT INTO `bak_20260914_lt_lot` VALUES (2081053196305989635, '000000', 'LOT20260726001', 99002, 'QC-RM-A', '质检测试原料A', 2062098763051089924, '天津化工材料有限公司', '', 2.0000, 2.0000, '2026-07-26', '2027-07-25', 'CONSUMED', 'PASS', NULL, '', 103, 1, '2026-07-26 00:25:21', 1, '2026-07-26 00:25:21', '0');

-- ----------------------------
-- Table structure for bak_20260914_lt_sn_binding
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_lt_sn_binding`;
CREATE TABLE `bak_20260914_lt_sn_binding`  (
  `binding_id` bigint(20) NOT NULL COMMENT '绑定ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SN编码(冗余)',
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `bound_time` datetime(0) NOT NULL COMMENT '绑定时间',
  `bound_by` bigint(20) NULL DEFAULT NULL COMMENT '绑定人ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_lt_wo_consumption
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_lt_wo_consumption`;
CREATE TABLE `bak_20260914_lt_wo_consumption`  (
  `consumption_id` bigint(20) NOT NULL COMMENT '消耗记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `consumed_qty` decimal(14, 4) NOT NULL COMMENT '消耗数量',
  `consumed_time` datetime(0) NOT NULL COMMENT '消耗时间',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_lt_wo_consumption
-- ----------------------------
INSERT INTO `bak_20260914_lt_wo_consumption` VALUES (2081053452590546946, '000000', 2080270525816516609, NULL, 2081053196305989635, 'LOT20260726001', 99002, 'QC-RM-A', 2.0000, '2026-07-26 00:26:22', 1, NULL, NULL, 103, 1, '2026-07-26 00:26:22', 1, '2026-07-26 00:26:22', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_content_file
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_content_file`;
CREATE TABLE `bak_20260914_mes_content_file`  (
  `content_file_id` bigint(20) NOT NULL COMMENT '配置文件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `content_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置编码(自动编码 MES_CONFIG_CODE)',
  `content_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `content_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CONFIG_FILE' COMMENT '配置类型: CONFIG_FILE(可扩展校准/证书)',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `oss_id` bigint(20) NULL DEFAULT NULL COMMENT '配置文件OSS文件ID',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MD5',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `release_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '物料默认版本(0否/1是)',
  `published_by` bigint(20) NULL DEFAULT NULL,
  `publish_time` datetime(0) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_content_file
-- ----------------------------
INSERT INTO `bak_20260914_mes_content_file` VALUES (2091000000000006102, '000000', 'CF20260817001', 'GW-1000出厂配置', 'CONFIG_FILE', 1001, 'IF20251018001', '电机Y132M-4 7.5KW', '1.0', 2088871530275577857, '21c307a98a4cf86276b003b47340638d', 6752, '出厂默认配置(波特率/服务器地址等)', 'RELEASED', '1', 1, '2026-08-17 22:16:17', 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', '正式测试数据');

-- ----------------------------
-- Table structure for bak_20260914_mes_firmware
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_firmware`;
CREATE TABLE `bak_20260914_mes_firmware`  (
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件编码(自动编码 MES_FW_CODE)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID(产品)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 1.2.3)',
  `build_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '构建号',
  `oss_id` bigint(20) NULL DEFAULT NULL COMMENT '固件文件OSS文件ID(sys_oss)',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件文件MD5',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '固件文件大小(字节)',
  `release_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否物料默认版本(0否/1是, 物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_firmware
-- ----------------------------
INSERT INTO `bak_20260914_mes_firmware` VALUES (2088871611594743809, '000000', 'FW20260816001', '测试', 5, 'GEAR_SET', '齿轮组', '1.0.0', 'CI-20260816-01', 2088871530275577857, '21c307a98a4cf86276b003b47340638d', 6752, '测试', 'DRAFT', '0', 2091000000000005001, NULL, NULL, 103, 1, '2026-08-16 14:12:56', 1, '2026-08-16 14:12:56', '0', '');
INSERT INTO `bak_20260914_mes_firmware` VALUES (2088889870117240834, '000000', 'FW20260816002', 'cs2', 2, 'IF20251018001', '电机Y132M-4 7.5KW', '1.0.0', 'aaa', 2088889865583198210, '21c307a98a4cf86276b003b47340638d', 6752, '', 'RELEASED', '1', 2091000000000005001, 1, '2026-08-16 15:26:18', 103, 1, '2026-08-16 15:25:29', 1, '2026-08-16 15:25:29', '0', '');
INSERT INTO `bak_20260914_mes_firmware` VALUES (2089376872356917250, '000000', 'FW20260817010', 'GW-1000主控固件', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', '1.0.0', 'CI-20260817', 2088871530275577900, '21c307a98a4cf86276b003b47340638d', 6752, 'GW-1000 首版主控固件(产线正式版)', 'RELEASED', '1', NULL, 1, '2026-08-17 23:40:40', 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', '全链路正式数据-固件');
INSERT INTO `bak_20260914_mes_firmware` VALUES (2091000000000006101, '000000', 'FW20260817001', 'GW-1000主控固件', 1001, 'IF20251018001', '电机Y132M-4 7.5KW', '1.0.0', 'CI-20260817', 2088871530275577857, '21c307a98a4cf86276b003b47340638d', 6752, 'GW-1000 首版主控固件(产线正式版)', 'RELEASED', '1', NULL, 1, '2026-08-17 22:16:17', 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', '正式测试数据');
INSERT INTO `bak_20260914_mes_firmware` VALUES (2091200576758001667, '000000', 'FW20260823001', 'TB-100主控固件', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', '1.0.0', 'CI-TB-1000', 2088871530275577900, '21c307a98a4cf86276b003b47340638d', 6752, 'TB-100 首版主控固件(标准版, 工艺路线绑定)', 'RELEASED', '1', NULL, 1, '2026-08-23 00:27:25', 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0', '电动牙刷全链路');
INSERT INTO `bak_20260914_mes_firmware` VALUES (2091200577269706754, '000000', 'FW20260823002', 'TB-100主控固件', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', '1.1.0', 'CI-TB-1100', 2088871530275577900, '21c307a98a4cf86276b003b47340638d', 6752, 'TB-100 白金定制版固件(发布包映射用)', 'RELEASED', '0', NULL, 1, '2026-08-23 00:27:25', 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0', '电动牙刷全链路');

-- ----------------------------
-- Table structure for bak_20260914_mes_flash_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_flash_record`;
CREATE TABLE `bak_20260914_mes_flash_record`  (
  `flash_record_id` bigint(20) NOT NULL COMMENT '刷写记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `flash_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷写记录号(自动编码 MES_FLASH_NO)',
  `flash_type_id` bigint(20) NULL DEFAULT NULL COMMENT '刷写类型ID(mes_flash_type)',
  `flash_type_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '刷写类型标识(快照: firmware_file/config_file/mac/sn)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(pro_flow_card_step)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `content_ref_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)',
  `content_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容版本(固件类=固件版本)',
  `content_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '实际写入内容快照JSON(如 {\"mac\":\"00:1A:2B:3C:4D:5E\"})',
  `content_checksum` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容校验值(泛化MD5/校验码)',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '刷写设备ID(eq_equipment)',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '刷写设备编码',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `flash_time` datetime(0) NULL DEFAULT NULL COMMENT '刷写时间',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果: PASS/FAIL',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `flash_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '刷写模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_flash_record
-- ----------------------------
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089350828161765377, '000000', 'FL202608170002', 2091000000000006003, 'mac', 3, 'SN202606200003', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, NULL, 'MAC_POOL', 2089350827679420417, NULL, '{\"mac\":\"00:1A:2B:3C:4D:5F\"}', NULL, NULL, NULL, NULL, NULL, '2026-08-17 21:57:10', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-17 21:57:10', -1, '2026-08-17 21:57:10', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089353941568471042, '000000', 'FL202608170004', 2091000000000006003, 'mac', 3, 'SN202606200003', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, NULL, 'MAC_POOL', 2089350827679420417, NULL, '{\"mac\":\"00:1A:2B:3C:4D:5F\"}', NULL, NULL, NULL, NULL, NULL, '2026-08-17 22:09:33', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-17 22:09:33', -1, '2026-08-17 22:09:33', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089359892421816321, '000000', 'FL202608170006', 2091000000000006003, 'mac', 3, 'SN202606200003', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, NULL, 'MAC_POOL', 2089350827679420417, NULL, '{\"mac\":\"00:1A:2B:3C:4D:5F\"}', NULL, NULL, NULL, NULL, NULL, '2026-08-17 22:33:11', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-17 22:33:11', -1, '2026-08-17 22:33:11', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089376873367744514, '000000', 'FL202608170009', 2091000000000006003, 'mac', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, 'GW-1000 MAC刷写', 'MAC_POOL', 2089376872742793217, NULL, '{\"mac\":\"00:1A:2B:3C:4D:0B\"}', NULL, NULL, NULL, 1, 'admin', '2026-08-17 23:40:40', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', '全链路正式数据-MAC刷写');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089381695185760257, '000000', 'FL202608170010', 2091000000000006003, 'mac', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, 'test', 'MAC_POOL', 2089376872742793217, NULL, '{\"mac\":\"00:1A:2B:3C:4D:0B\"}', NULL, NULL, NULL, 1, 'admin', '2026-08-17 23:59:50', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-17 23:59:50', 1, '2026-08-17 23:59:50', '0', 'CEH-test');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089383748683440130, '000000', 'FL202608180001', 2091000000000006003, 'mac', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, NULL, NULL, NULL, 'C����-MAC', 'MAC_POOL', 2089376872742793217, NULL, '{\"mac\":\"00:1A:2B:3C:4D:0B\"}', NULL, NULL, NULL, 1, 'admin', '2026-08-18 00:07:59', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 00:07:59', 1, '2026-08-18 00:07:59', '0', 'CEH-pos');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089724283822661634, '000000', 'FL202608180002', 2091000000000006001, 'firmware_file', 2089716723199512577, 'SN-FC-SCGD202608180001-7372-0001', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', 1, 'CNC-001', 1, 'admin', '2026-08-18 22:40:53', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 22:41:09', 1, '2026-08-18 22:41:09', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089724337581056002, '000000', 'FL202608180003', 2091000000000006001, 'firmware_file', 2089716723199512578, 'SN-FC-SCGD202608180001-7372-0002', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', 1, 'CNC-001', 1, 'admin', '2026-08-18 22:41:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 22:41:22', 1, '2026-08-18 22:41:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089724363686404097, '000000', 'FL202608180004', 2091000000000006001, 'firmware_file', 2089716723199512579, 'SN-FC-SCGD202608180001-7372-0003', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', 1, 'CNC-001', 1, 'admin', '2026-08-18 22:41:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 22:41:28', 1, '2026-08-18 22:41:28', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731575431307266, '000000', 'FL202608180005', 2091000000000006001, 'firmware_file', 2089731476877746177, 'SN-FC-SCGD202608180001-3431-0001', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:04', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:08', 1, '2026-08-18 23:10:08', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731595194867713, '000000', 'FL202608180006', 2091000000000006001, 'firmware_file', 2089731476877746178, 'SN-FC-SCGD202608180001-3431-0002', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:09', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:12', 1, '2026-08-18 23:10:12', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731603205988353, '000000', 'FL202608180007', 2091000000000006001, 'firmware_file', 2089731476877746179, 'SN-FC-SCGD202608180001-3431-0003', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:13', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:14', 1, '2026-08-18 23:10:14', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731610382442497, '000000', 'FL202608180008', 2091000000000006001, 'firmware_file', 2089731476877746181, 'SN-FC-SCGD202608180001-3431-0005', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:15', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:16', 1, '2026-08-18 23:10:16', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731618318065665, '000000', 'FL202608180009', 2091000000000006001, 'firmware_file', 2089731476877746182, 'SN-FC-SCGD202608180001-3431-0006', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:16', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:18', 1, '2026-08-18 23:10:18', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089731645094502402, '000000', 'FL202608180010', 2091000000000006001, 'firmware_file', 2089731476877746180, 'SN-FC-SCGD202608180001-3431-0004', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:10:22', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:10:24', 1, '2026-08-18 23:10:24', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089736293247459330, '000000', 'FL202608180011', 2091000000000006001, 'firmware_file', 2089736183729987585, 'SN-FC-SCGD202608180001-8898-0001', 2089713975506821122, 'SCGD202608180001', 2089736110841372674, NULL, 90002001, 'FLASH', '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:28:50', 'PASS', NULL, 0, 'AUTO', 103, 1, '2026-08-18 23:28:52', 1, '2026-08-18 23:28:52', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742961444175874, '000000', 'FL202608180012', 2091000000000006001, 'firmware_file', 2089736183729987585, 'SN-FC-SCGD202608180001-8898-0001', 2089713975506821122, 'SCGD202608180001', 2089736110841372674, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742961586782209, '000000', 'FL202608180013', 2091000000000006001, 'firmware_file', 2089731476877746177, 'SN-FC-SCGD202608180001-3431-0001', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742961708417025, '000000', 'FL202608180014', 2091000000000006001, 'firmware_file', 2089731476877746178, 'SN-FC-SCGD202608180001-3431-0002', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742961834246145, '000000', 'FL202608180015', 2091000000000006001, 'firmware_file', 2089731476877746179, 'SN-FC-SCGD202608180001-3431-0003', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742961960075265, '000000', 'FL202608180016', 2091000000000006001, 'firmware_file', 2089731476877746180, 'SN-FC-SCGD202608180001-3431-0004', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742962090098690, '000000', 'FL202608180017', 2091000000000006001, 'firmware_file', 2089731476877746181, 'SN-FC-SCGD202608180001-3431-0005', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742962220122113, '000000', 'FL202608180018', 2091000000000006001, 'firmware_file', 2089731476877746182, 'SN-FC-SCGD202608180001-3431-0006', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742962354339842, '000000', 'FL202608180019', 2091000000000006001, 'firmware_file', 2089716723199512577, 'SN-FC-SCGD202608180001-7372-0001', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742962484363266, '000000', 'FL202608180020', 2091000000000006001, 'firmware_file', 2089716723199512578, 'SN-FC-SCGD202608180001-7372-0002', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742962622775298, '000000', 'FL202608180021', 2091000000000006001, 'firmware_file', 2089716723199512579, 'SN-FC-SCGD202608180001-7372-0003', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:22', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:22', 1, '2026-08-18 23:55:22', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963293863938, '000000', 'FL202608180022', 2091000000000006001, 'firmware_file', 2089736183729987585, 'SN-FC-SCGD202608180001-8898-0001', 2089713975506821122, 'SCGD202608180001', 2089736110841372674, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963407110146, '000000', 'FL202608180023', 2091000000000006001, 'firmware_file', 2089731476877746177, 'SN-FC-SCGD202608180001-3431-0001', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963595853826, '000000', 'FL202608180024', 2091000000000006001, 'firmware_file', 2089731476877746178, 'SN-FC-SCGD202608180001-3431-0002', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963725877250, '000000', 'FL202608180025', 2091000000000006001, 'firmware_file', 2089731476877746179, 'SN-FC-SCGD202608180001-3431-0003', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963839123457, '000000', 'FL202608180026', 2091000000000006001, 'firmware_file', 2089731476877746180, 'SN-FC-SCGD202608180001-3431-0004', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742963952369665, '000000', 'FL202608180027', 2091000000000006001, 'firmware_file', 2089731476877746181, 'SN-FC-SCGD202608180001-3431-0005', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742964061421570, '000000', 'FL202608180028', 2091000000000006001, 'firmware_file', 2089731476877746182, 'SN-FC-SCGD202608180001-3431-0006', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742964166279169, '000000', 'FL202608180029', 2091000000000006001, 'firmware_file', 2089716723199512577, 'SN-FC-SCGD202608180001-7372-0001', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742964262748162, '000000', 'FL202608180030', 2091000000000006001, 'firmware_file', 2089716723199512578, 'SN-FC-SCGD202608180001-7372-0002', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089742964367605762, '000000', 'FL202608180031', 2091000000000006001, 'firmware_file', 2089716723199512579, 'SN-FC-SCGD202608180001-7372-0003', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:23', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:23', 1, '2026-08-18 23:55:23', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107447898114, '000000', 'FL202608180032', 2091000000000006001, 'firmware_file', 2089743107275931649, 'SN-FC-SCGD202608180002V6776-6871-0003', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107544367106, '000000', 'FL202608180033', 2091000000000006001, 'firmware_file', 2089743107263348737, 'SN-FC-SCGD202608180002V6776-6871-0001', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107645030401, '000000', 'FL202608180034', 2091000000000006001, 'firmware_file', 2089743107284320257, 'SN-FC-SCGD202608180002V6776-6871-0004', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107745693697, '000000', 'FL202608180035', 2091000000000006001, 'firmware_file', 2089743107288514561, 'SN-FC-SCGD202608180002V6776-6871-0005', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107850551298, '000000', 'FL202608180036', 2091000000000006001, 'firmware_file', 2089743107292708866, 'SN-FC-SCGD202608180002V6776-6871-0006', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743107942825985, '000000', 'FL202608180037', 2091000000000006001, 'firmware_file', 2089743107267543042, 'SN-FC-SCGD202608180002V6776-6871-0002', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743108781686786, '000000', 'FL202608180038', 2091000000000006001, 'firmware_file', 2089743108622303233, 'SN-FC-SCGD202608180002V6776-7235-0002', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743108873961473, '000000', 'FL202608180039', 2091000000000006001, 'firmware_file', 2089743108626497537, 'SN-FC-SCGD202608180002V6776-7235-0003', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743108978819073, '000000', 'FL202608180040', 2091000000000006001, 'firmware_file', 2089743108613914626, 'SN-FC-SCGD202608180002V6776-7235-0001', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743109075288065, '000000', 'FL202608180041', 2091000000000006001, 'firmware_file', 2089743108630691842, 'SN-FC-SCGD202608180002V6776-7235-0004', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:55:57', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743634931957762, '000000', 'FL202608180042', 2091000000000006001, 'firmware_file', 2089743634722242561, 'SN-FC-SCGD202608180002V2560-2653-0002', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743635124895746, '000000', 'FL202608180043', 2091000000000006001, 'firmware_file', 2089743634739019777, 'SN-FC-SCGD202608180002V2560-2653-0006', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743635238141954, '000000', 'FL202608180044', 2091000000000006001, 'firmware_file', 2089743634730631169, 'SN-FC-SCGD202608180002V2560-2653-0004', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743635342999553, '000000', 'FL202608180045', 2091000000000006001, 'firmware_file', 2089743634718048258, 'SN-FC-SCGD202608180002V2560-2653-0001', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743635456245762, '000000', 'FL202608180046', 2091000000000006001, 'firmware_file', 2089743634734825474, 'SN-FC-SCGD202608180002V2560-2653-0005', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743635561103362, '000000', 'FL202608180047', 2091000000000006001, 'firmware_file', 2089743634726436866, 'SN-FC-SCGD202608180002V2560-2653-0003', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743636341243906, '000000', 'FL202608180048', 2091000000000006001, 'firmware_file', 2089743636177666050, 'SN-FC-SCGD202608180002V2560-3019-0001', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743636450295810, '000000', 'FL202608180049', 2091000000000006001, 'firmware_file', 2089743636181860354, 'SN-FC-SCGD202608180002V2560-3019-0002', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743636538376193, '000000', 'FL202608180050', 2091000000000006001, 'firmware_file', 2089743636186054658, 'SN-FC-SCGD202608180002V2560-3019-0003', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089743636634845185, '000000', 'FL202608180051', 2091000000000006001, 'firmware_file', 2089743636190248962, 'SN-FC-SCGD202608180002V2560-3019-0004', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-18 23:58:03', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256047230978, '000000', 'FL202608190001', 2091000000000006001, 'firmware_file', 2089745255866875905, 'SN-FC-SCGD202608190001V9085-9166-0001', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256147894273, '000000', 'FL202608190002', 2091000000000006001, 'firmware_file', 2089745255887847425, 'SN-FC-SCGD202608190001V9085-9166-0005', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256248557569, '000000', 'FL202608190003', 2091000000000006001, 'firmware_file', 2089745255896236033, 'SN-FC-SCGD202608190001V9085-9166-0006', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256345026561, '000000', 'FL202608190004', 2091000000000006001, 'firmware_file', 2089745255871070210, 'SN-FC-SCGD202608190001V9085-9166-0002', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256462467074, '000000', 'FL202608190005', 2091000000000006001, 'firmware_file', 2089745255879458818, 'SN-FC-SCGD202608190001V9085-9166-0003', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745256571518977, '000000', 'FL202608190006', 2091000000000006001, 'firmware_file', 2089745255879458819, 'SN-FC-SCGD202608190001V9085-9166-0004', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:29', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745257464905730, '000000', 'FL202608190007', 2091000000000006001, 'firmware_file', 2089745257276162049, 'SN-FC-SCGD202608190001V9085-9513-0001', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:30', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745257561374721, '000000', 'FL202608190008', 2091000000000006001, 'firmware_file', 2089745257280356353, 'SN-FC-SCGD202608190001V9085-9513-0002', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:30', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745257653649409, '000000', 'FL202608190009', 2091000000000006001, 'firmware_file', 2089745257284550657, 'SN-FC-SCGD202608190001V9085-9513-0003', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:30', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745257745924098, '000000', 'FL202608190010', 2091000000000006001, 'firmware_file', 2089745257288744962, 'SN-FC-SCGD202608190001V9085-9513-0004', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:04:30', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745700538597377, '000000', 'FL202608190011', 2091000000000006001, 'firmware_file', 2089745700261773313, 'SN-FC-SCGD202608190001V5037-5122-0002', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745700643454978, '000000', 'FL202608190012', 2091000000000006001, 'firmware_file', 2089745700257579009, 'SN-FC-SCGD202608190001V5037-5122-0001', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745700744118273, '000000', 'FL202608190013', 2091000000000006001, 'firmware_file', 2089745700278550530, 'SN-FC-SCGD202608190001V5037-5122-0006', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745700848975873, '000000', 'FL202608190014', 2091000000000006001, 'firmware_file', 2089745700265967618, 'SN-FC-SCGD202608190001V5037-5122-0003', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745700953833474, '000000', 'FL202608190015', 2091000000000006001, 'firmware_file', 2089745700270161922, 'SN-FC-SCGD202608190001V5037-5122-0004', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745701054496770, '000000', 'FL202608190016', 2091000000000006001, 'firmware_file', 2089745700274356226, 'SN-FC-SCGD202608190001V5037-5122-0005', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:15', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745702002409473, '000000', 'FL202608190017', 2091000000000006001, 'firmware_file', 2089745701700419585, 'SN-FC-SCGD202608190001V5037-5475-0001', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:16', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745702107267074, '000000', 'FL202608190018', 2091000000000006001, 'firmware_file', 2089745701717196801, 'SN-FC-SCGD202608190001V5037-5475-0003', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:16', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745702207930369, '000000', 'FL202608190019', 2091000000000006001, 'firmware_file', 2089745701713002498, 'SN-FC-SCGD202608190001V5037-5475-0002', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:16', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2089745702321176578, '000000', 'FL202608190020', 2091000000000006001, 'firmware_file', 2089745701717196802, 'SN-FC-SCGD202608190001V5037-5475-0004', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, NULL, NULL, NULL, '固件刷写', 'FIRMWARE', 2089376872356917250, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-19 00:06:16', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL);
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091000000000006140, '000000', 'FL20260817001', 2091000000000006001, 'firmware_file', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, NULL, 1, 'CUT', '切割', 'FIRMWARE', 2091000000000006101, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, 1, 'admin', '2026-08-17 22:16:17', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', '正式测试数据-固件刷写');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091000000000006141, '000000', 'FL20260817002', 2091000000000006003, 'mac', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, NULL, 1, 'CUT', '切割', 'MAC_POOL', 2091000000000006119, NULL, '{\"mac\":\"00:1A:2B:3C:4D:0A\"}', NULL, NULL, NULL, 1, 'admin', '2026-08-17 22:16:17', 'PASS', NULL, 0, 'MANUAL', 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', '正式测试数据-MAC刷写');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091129246834585601, '000000', 'FL202608220001', 2091000000000006001, 'firmware_file', 2099000000000000106, 'SNQCFLASH0001', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000022, NULL, NULL, NULL, NULL, 'FIRMWARE', 2091000000000006101, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-22 19:43:58', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-22 19:43:58', -1, '2026-08-22 19:43:58', '0', 'station/mock');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091129891230674945, '000000', 'FL202608220002', 2091000000000006001, 'firmware_file', 2099000000000000107, 'SNQCFLASH0002', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000022, NULL, NULL, NULL, NULL, 'FIRMWARE', 2091000000000006101, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-22 19:46:32', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-22 19:46:32', -1, '2026-08-22 19:46:32', '0', 'station/mock');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201165948661761, '000000', 'FL202608230001', 2091000000000006001, 'firmware_file', 2091201164983971841, 'SN-FC-SCGD202608230001-4957-0001', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166015770626, '000000', 'FL202608230002', 2091000000000006001, 'firmware_file', 2091201164983971842, 'SN-FC-SCGD202608230001-4957-0002', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166078685185, '000000', 'FL202608230003', 2091000000000006001, 'firmware_file', 2091201164983971843, 'SN-FC-SCGD202608230001-4957-0003', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166145794050, '000000', 'FL202608230004', 2091000000000006001, 'firmware_file', 2091201165046886401, 'SN-FC-SCGD202608230001-4957-0004', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166208708609, '000000', 'FL202608230005', 2091000000000006001, 'firmware_file', 2091201165046886402, 'SN-FC-SCGD202608230001-4957-0005', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166338732034, '000000', 'FL202608230006', 2091000000000006001, 'firmware_file', 2091201165046886403, 'SN-FC-SCGD202608230001-4957-0006', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166405840898, '000000', 'FL202608230007', 2091000000000006001, 'firmware_file', 2091201165046886404, 'SN-FC-SCGD202608230001-4957-0007', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166472949761, '000000', 'FL202608230008', 2091000000000006001, 'firmware_file', 2091201165046886405, 'SN-FC-SCGD202608230001-4957-0008', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166535864322, '000000', 'FL202608230009', 2091000000000006001, 'firmware_file', 2091201165046886406, 'SN-FC-SCGD202608230001-4957-0009', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');
INSERT INTO `bak_20260914_mes_flash_record` VALUES (2091201166598778881, '000000', 'FL202608230010', 2091000000000006001, 'firmware_file', 2091201165046886407, 'SN-FC-SCGD202608230001-4957-0010', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, NULL, NULL, 'FLASH', '固件刷写', 'FIRMWARE', 2091200577269706754, '1.1.0', NULL, '21c307a98a4cf86276b003b47340638d', NULL, NULL, NULL, NULL, '2026-08-23 00:29:45', 'PASS', NULL, 0, 'AUTO', -1, -1, '2026-08-23 00:29:45', -1, '2026-08-23 00:29:45', '0', '电动牙刷全链路-刷写PASS');

-- ----------------------------
-- Table structure for bak_20260914_mes_flash_type
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_flash_type`;
CREATE TABLE `bak_20260914_mes_flash_type`  (
  `flash_type_id` bigint(20) NOT NULL COMMENT '刷写类型ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `type_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型标识: firmware_file/config_file/mac/sn/external_*',
  `type_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容来源: FILE/POOL/EXTERNAL/INLINE',
  `source_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPI Provider bean名(如 firmwareFileFlashProvider)',
  `source_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源配置JSON(审批流标识/池ID/REST模板)',
  `verify_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MD5_MATCH' COMMENT '校验策略: MD5_MATCH/RESOURCE_BOUND/CUSTOM',
  `gate_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否参与完工门禁(0否/1是)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_flash_type
-- ----------------------------
INSERT INTO `bak_20260914_mes_flash_type` VALUES (2091000000000006001, '000000', 'firmware_file', '刷写固件', 'FILE', 'firmwareFileFlashProvider', NULL, 'MD5_MATCH', '1', 'ENABLED', 1, 103, 1, '2026-08-17 21:46:24', NULL, NULL, '0', '固件文件(审批发布, 绑工单/物料), MD5校验');
INSERT INTO `bak_20260914_mes_flash_type` VALUES (2091000000000006002, '000000', 'config_file', '刷写配置文件', 'FILE', 'configFileFlashProvider', NULL, 'MD5_MATCH', '1', 'ENABLED', 2, 103, 1, '2026-08-17 21:47:40', NULL, NULL, '0', '配置文件(审批发布), MD5校验');
INSERT INTO `bak_20260914_mes_flash_type` VALUES (2091000000000006003, '000000', 'mac', '刷写MAC', 'POOL', 'macPoolFlashProvider', NULL, 'RESOURCE_BOUND', '1', 'ENABLED', 3, 103, 1, '2026-08-17 21:47:40', NULL, NULL, '0', 'MAC资源池原子分配, 一SN绑一MAC');
INSERT INTO `bak_20260914_mes_flash_type` VALUES (2091000000000006004, '000000', 'sn', '刷写SN', 'POOL', 'snPoolFlashProvider', NULL, 'RESOURCE_BOUND', '1', 'ENABLED', 4, 103, 1, '2026-08-17 21:47:40', NULL, NULL, '0', 'SN资源池原子分配, 一SN绑一SN');
INSERT INTO `bak_20260914_mes_flash_type` VALUES (2091000000000006005, '000000', 'external_rest', '外部REST刷写', 'EXTERNAL', 'externalRestFlashProvider', NULL, 'CUSTOM', '0', 'ENABLED', 5, 103, 1, '2026-08-17 21:46:24', NULL, NULL, '0', '外部系统REST获取内容, SSRF防护禁内网; source_config配 {\"url\":\"http://...\",\"token\":\"...\"}');

-- ----------------------------
-- Table structure for bak_20260914_mes_label_template
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_label_template`;
CREATE TABLE `bak_20260914_mes_label_template`  (
  `template_id` bigint(20) NOT NULL COMMENT '模板ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `label_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'SN' COMMENT '标签类型: SN 标签(流转卡标签后续扩展)',
  `label_lang` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'TSPL' COMMENT '打印机语言: TSPL/ZPL',
  `width_mm` int(11) NULL DEFAULT 60 COMMENT '标签宽度(mm)',
  `height_mm` int(11) NULL DEFAULT 40 COMMENT '标签高度(mm)',
  `printer_host` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '网络打印机地址(TCP RAW 9100)',
  `printer_port` int(11) NULL DEFAULT 9100 COMMENT '网络打印机端口',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段映射布局 JSON: {items:[{type,field,x,y,size,height}]}',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'Y' COMMENT '是否启用(Y/N): SN 造码打印仅消费启用模板',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_label_template
-- ----------------------------
INSERT INTO `bak_20260914_mes_label_template` VALUES (2090820814134960129, '000000', 'TEST-SN', 'SN123', 'SN', 'TSPL', 60, 40, '127.0.0.1', 9100, '{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8,\"size\":5,\"height\":2}]}', 'Y', NULL, 103, 1, '2026-08-21 23:18:22', 1, '2026-08-21 23:18:22', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_material_load
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_material_load`;
CREATE TABLE `bak_20260914_mes_material_load`  (
  `load_id` bigint(20) NOT NULL COMMENT '台账ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID(pro_work_order.mes_wo_id)',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工位编码',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作中心编码(MBOM快照冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID(wm_batch.batch_id)',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(wm_batch.batch_code)',
  `feeder_slot` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '料站号(可空, 贴片机 feeder 位号等)',
  `is_alternative` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'N' COMMENT '是否替代料匹配: Y=bd_bom_alternative替代料, N=BOM主料',
  `operator_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上料人工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上料人姓名',
  `load_time` datetime(0) NOT NULL COMMENT '上料时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE=在机, REPLACED=已换料',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_material_load
-- ----------------------------
INSERT INTO `bak_20260914_mes_material_load` VALUES (2091136952706363394, '000000', 2099000000000000001, 'WO-QCTEST-001', 'STN|TEST|L1|QC|FLASH', 'QC', 'GZZ0017', 7, 'BOLT_M12', '螺栓M12', 2099000000000000601, 'B20260822001', NULL, 'N', 'SCZX20251222017', '张明', '2026-08-22 20:14:36', 'REPLACED', '换料 → B20260822001', -1, -1, '2026-08-22 20:14:36', -1, '2026-08-30 22:41:20', '0');
INSERT INTO `bak_20260914_mes_material_load` VALUES (2094072987597885442, '000000', 2099000000000000001, 'WO-QCTEST-001', 'STN|TEST|L1|QC|FLASH', 'QC', 'GZZ0017', 7, 'BOLT_M12', '螺栓M12', 2099000000000000601, 'B20260822001', NULL, 'N', 'SCZX20251222017', '张明', '2026-08-30 22:41:21', 'REPLACED', '换料 → B20260822001', -1, -1, '2026-08-30 22:41:21', -1, '2026-08-30 22:43:26', '0');
INSERT INTO `bak_20260914_mes_material_load` VALUES (2094073512607305730, '000000', 2099000000000000001, 'WO-QCTEST-001', 'STN|TEST|L1|QC|FLASH', 'QC', 'GZZ0017', 7, 'BOLT_M12', '螺栓M12', 2099000000000000601, 'B20260822001', NULL, 'N', 'SCZX20251222017', '张明', '2026-08-30 22:43:26', 'REPLACED', '换料 → B20260822001', -1, -1, '2026-08-30 22:43:26', -1, '2026-08-30 22:45:39', '0');
INSERT INTO `bak_20260914_mes_material_load` VALUES (2094074072161013762, '000000', 2099000000000000001, 'WO-QCTEST-001', 'STN|TEST|L1|QC|FLASH', 'QC', 'GZZ0017', 7, 'BOLT_M12', '螺栓M12', 2099000000000000601, 'B20260822001', NULL, 'N', 'SCZX20251222017', '张明', '2026-08-30 22:45:39', 'REPLACED', '换料 → B20260822001', -1, -1, '2026-08-30 22:45:39', -1, '2026-08-30 23:20:03', '0');
INSERT INTO `bak_20260914_mes_material_load` VALUES (2094082727874818050, '000000', 2099000000000000001, 'WO-QCTEST-001', 'STN|TEST|L1|QC|FLASH', 'QC', 'GZZ0017', 7, 'BOLT_M12', '螺栓M12', 2099000000000000601, 'B20260822001', NULL, 'N', 'SCZX20251222017', '张明', '2026-08-30 23:20:03', 'ACTIVE', NULL, -1, -1, '2026-08-30 23:20:03', -1, '2026-08-30 23:20:03', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_mbom
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_mbom`;
CREATE TABLE `bak_20260914_mes_mbom`  (
  `mbom_id` bigint(20) NOT NULL COMMENT 'MBOM ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `bom_id` bigint(20) NOT NULL COMMENT '关联ERP bd_bom.bom_id',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '制造工艺路线ID(可继承自bd_bom.routing_id或MES侧指定)',
  `mbom_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MBOM版本号',
  `mbom_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/FROZEN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_mbom
-- ----------------------------
INSERT INTO `bak_20260914_mes_mbom` VALUES (1, '000000', 5, 1, '1.0', 'ACTIVE', '自行车制造BOM，关联ERP BOM#5', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom` VALUES (2, '000000', 1, 2, '1.0', 'ACTIVE', '减速机JZQ-350制造BOM，关联ERP BOM#1', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom` VALUES (30, '000000', 20, 3, '1.0', 'ACTIVE', '工具架MBOM，关联工艺路线和BOM', 103, 1, '2026-06-30 10:13:11', 1, '2026-06-30 10:13:11', '0');
INSERT INTO `bak_20260914_mes_mbom` VALUES (2089701818874478593, '000000', 2089376872235282434, 2089698804193546241, '1.0', 'ACTIVE', NULL, 103, 1, '2026-08-18 21:11:53', 1, '2026-08-18 21:12:04', '0');
INSERT INTO `bak_20260914_mes_mbom` VALUES (2091200578813210626, '000000', 2091200578037264385, 2091200577588473858, '1.0', 'ACTIVE', '电动牙刷全链路', 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_mbom_line
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_mbom_line`;
CREATE TABLE `bak_20260914_mes_mbom_line`  (
  `mbom_line_id` bigint(20) NOT NULL COMMENT 'MBOM明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mbom_id` bigint(20) NOT NULL COMMENT '关联mes_mbom.mbom_id',
  `bom_id` bigint(20) NOT NULL COMMENT '冗余ERP bom_id',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '关联ERP bd_bom_detail.detail_id(可空,MES附加消耗料时为空)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_routing_step.step_id(工序步骤)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_process.process_id(工序)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_workstation.workstation_id(工位)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_mbom_line
-- ----------------------------
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (1, '000000', 1, 5, NULL, 2001, 1.000000, 0.0200, 1, 1, 1, '车架切割前准备', 0, 1, '车架在切割工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2, '000000', 1, 5, NULL, 2002, 2.000000, 0.0100, 3, 3, 1, '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (3, '000000', 1, 5, NULL, 2003, 1.000000, 0.0100, 3, 3, 1, '车把装配', 1, 3, '车把在装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (4, '000000', 1, 5, NULL, 10, 1.000000, 0.0000, 4, 5, 3, '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (5, '000000', 2, 1, NULL, 6, 1.000000, 0.0200, 5, 1, 1, '箱体毛坯切割', 0, 1, '箱体在切割工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (6, '000000', 2, 1, NULL, 7, 4.000000, 0.0000, 7, 3, 1, '螺栓装配', 0, 2, '螺栓在总装配工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (7, '000000', 2, 1, NULL, 5, 1.000000, 0.0100, 7, 3, 1, '齿轮组装配', 1, 3, '齿轮组在总装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (8, '000000', 2, 1, NULL, 8, 2.000000, 0.0050, 7, 3, 1, '轴承装配', 0, 4, '轴承在总装配工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (9, '000000', 2, 1, NULL, 10, 0.500000, 0.0000, 8, 4, 3, '润滑处理', 0, 5, '润滑油在质检前投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (100, '000000', 30, 20, 100, 8001, 2.000000, 0.0000, 11, 3, 1, '取侧板组件2件，装配至工具架', 0, 10, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (101, '000000', 30, 20, 101, 8002, 1.000000, 0.0000, 11, 3, 1, '取连接件包1套，装配至工具架', 0, 20, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (102, '000000', 30, 21, 102, 1, 1.500000, 2.0000, 9, 1, 1, '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2089701818929004545, '000000', 2089701818874478593, 2089376872235282434, NULL, 2089376872126230529, 1.000000, 0.0000, 2089698804256460801, NULL, 3, '刷写', 0, 1, NULL, 103, 1, '2026-08-18 21:11:53', 1, '2026-08-18 21:11:53', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578813210627, '000000', 2091200578813210626, 2091200578037264385, 2091200578360225794, 2091200054030282754, 1.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 1, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578813210628, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334658, 2091200054223220737, 1.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 2, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319489, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334659, 2091200054416158722, 1.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 3, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319490, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334660, 2091200054672011266, 1.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 4, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319491, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334661, 2091200055313739778, 2.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 5, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319492, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334662, 2091200055502483457, 4.000000, 0.0000, 2091200577651388417, 3, 2091200056404258818, '', 0, 6, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319493, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334663, 2091200054864949250, 2.000000, 0.0000, 2091200577651388422, 5, 2091200575986249730, '', 0, 7, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578880319494, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334664, 2091200055120801794, 1.000000, 0.0000, 2091200577651388422, 5, 2091200575986249730, '', 0, 8, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578943234049, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334665, 2091200055695421442, 1.000000, 0.0000, 2091200577651388422, 5, 2091200575986249730, '', 0, 9, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');
INSERT INTO `bak_20260914_mes_mbom_line` VALUES (2091200578943234050, '000000', 2091200578813210626, 2091200578037264385, 2091200578427334666, 2091200055825444865, 1.000000, 0.0000, 2091200577651388422, 5, 2091200575986249730, '', 0, 10, NULL, 103, 1, '2026-08-23 00:27:25', 1, '2026-08-23 00:27:25', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_release_package
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_release_package`;
CREATE TABLE `bak_20260914_mes_release_package`  (
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布包编码(自动编码 MES_RP_CODE)',
  `client_id` bigint(20) NOT NULL COMMENT '客户ID(bd_client.client_id)',
  `client_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '成品物料ID(bd_material.material_id)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `customer_product_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品代号(客户对该产品的内部编号, 匹配键)',
  `customer_product_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 2.1)',
  `release_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `effective_date` datetime(0) NULL DEFAULT NULL COMMENT '生效日期(空=发布即生效)',
  `expiry_date` datetime(0) NULL DEFAULT NULL COMMENT '过期日期(空=永久有效)',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否客户+物料默认发布包(0否/1是, 客户+物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_release_package
-- ----------------------------
INSERT INTO `bak_20260914_mes_release_package` VALUES (2091201152438808578, '000000', 'RP20260823001', 2091201152120041473, '洁白口腔护理科技有限公司', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', 'TB100-WHITE', '白金定制版', '1.0', '洁白口腔定制: 主控固件 v1.1.0(白金版开机画面+刷牙模式增强)', 'RELEASED', NULL, NULL, '1', NULL, 1, '2026-08-23 00:29:42', 103, 1, '2026-08-23 00:29:42', 1, '2026-08-23 00:29:42', '0', '电动牙刷全链路');

-- ----------------------------
-- Table structure for bak_20260914_mes_release_pkg_bom_item
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_release_pkg_bom_item`;
CREATE TABLE `bak_20260914_mes_release_pkg_bom_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `action_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型: REPLACE/ADD/REMOVE',
  `child_material_id` bigint(20) NULL DEFAULT NULL COMMENT '原物料ID(REPLACE/REMOVE 定位键, 按子件物料定位抗BOM版本变更)',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料名称(冗余)',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '标准BOM明细行ID(可选精确定位, 空则按原物料定位)',
  `target_material_id` bigint(20) NULL DEFAULT NULL COMMENT '目标物料ID(REPLACE/ADD 必填)',
  `target_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料编码(冗余)',
  `target_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NULL DEFAULT NULL COMMENT '单位用量(空=REPLACE沿用原行/ADD必填)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_mes_release_pkg_config
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_release_pkg_config`;
CREATE TABLE `bak_20260914_mes_release_pkg_config`  (
  `config_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `config_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS',
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置值',
  `config_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STRING' COMMENT '值类型: STRING/NUMBER/BOOLEAN/JSON',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID(空=全局生效)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_mes_release_pkg_firmware
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_release_pkg_firmware`;
CREATE TABLE `bak_20260914_mes_release_pkg_firmware`  (
  `mapping_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺路线步骤ID(bd_routing_step.step_id, 精确定位)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(bd_process.process_id, 宽泛定位)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `pcba_material_id` bigint(20) NULL DEFAULT NULL COMMENT 'PCBA物料ID(固件物理载体, 追溯用, 可空)',
  `pcba_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料编码(冗余)',
  `pcba_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料名称(冗余)',
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID(mes_firmware.firmware_id, 仅RELEASED)',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件编码(冗余)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件名称(冗余)',
  `firmware_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件版本(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_release_pkg_firmware
-- ----------------------------
INSERT INTO `bak_20260914_mes_release_pkg_firmware` VALUES (2091201152568832001, '000000', 2091201152438808578, NULL, 90002001, 'FLASH', '固件刷写', NULL, NULL, NULL, 2091200577269706754, 'FW20260823002', 'TB-100主控固件', '1.1.0', 1, 103, 1, '2026-08-23 00:29:42', 1, '2026-08-23 00:29:42', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_mes_resource_item
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_resource_item`;
CREATE TABLE `bak_20260914_mes_resource_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '资源项ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_id` bigint(20) NOT NULL COMMENT '资源池ID',
  `resource_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源值(如MAC 00:1A:2B:3C:4D:5E)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/ALLOCATED/USED/SCRAPPED',
  `bound_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '已绑定SN ID(一SN绑一资源)',
  `bound_time` datetime(0) NULL DEFAULT NULL COMMENT '绑定时间',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_resource_item
-- ----------------------------
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872742793217, '000000', 2089376872637935618, '00:1A:2B:3C:4D:0B', 'ALLOCATED', 1, '2026-08-17 23:40:40', 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872751181825, '000000', 2089376872637935618, '00:1A:2B:3C:4D:0C', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872759570434, '000000', 2089376872637935618, '00:1A:2B:3C:4D:0D', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872763764737, '000000', 2089376872637935618, '00:1A:2B:3C:4D:0E', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872776347650, '000000', 2089376872637935618, '00:1A:2B:3C:4D:0F', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872780541953, '000000', 2089376872637935618, '00:1A:2B:3C:4D:10', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872784736257, '000000', 2089376872637935618, '00:1A:2B:3C:4D:11', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872797319169, '000000', 2089376872637935618, '00:1A:2B:3C:4D:12', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872805707778, '000000', 2089376872637935618, '00:1A:2B:3C:4D:13', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2089376872809902081, '000000', 2089376872637935618, '00:1A:2B:3C:4D:14', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006110, '000000', 2091000000000006103, '00:1A:2B:3C:4D:01', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006111, '000000', 2091000000000006103, '00:1A:2B:3C:4D:02', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006112, '000000', 2091000000000006103, '00:1A:2B:3C:4D:03', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006113, '000000', 2091000000000006103, '00:1A:2B:3C:4D:04', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006114, '000000', 2091000000000006103, '00:1A:2B:3C:4D:05', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006115, '000000', 2091000000000006103, '00:1A:2B:3C:4D:06', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006116, '000000', 2091000000000006103, '00:1A:2B:3C:4D:07', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006117, '000000', 2091000000000006103, '00:1A:2B:3C:4D:08', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006118, '000000', 2091000000000006103, '00:1A:2B:3C:4D:09', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006119, '000000', 2091000000000006103, '00:1A:2B:3C:4D:0A', 'ALLOCATED', 1, '2026-08-17 22:16:17', 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006130, '000000', 2091000000000006120, 'SN-GW1000-0001', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006131, '000000', 2091000000000006120, 'SN-GW1000-0002', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006132, '000000', 2091000000000006120, 'SN-GW1000-0003', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006133, '000000', 2091000000000006120, 'SN-GW1000-0004', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006134, '000000', 2091000000000006120, 'SN-GW1000-0005', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006135, '000000', 2091000000000006120, 'SN-GW1000-0006', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006136, '000000', 2091000000000006120, 'SN-GW1000-0007', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006137, '000000', 2091000000000006120, 'SN-GW1000-0008', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006138, '000000', 2091000000000006120, 'SN-GW1000-0009', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_mes_resource_item` VALUES (2091000000000006139, '000000', 2091000000000006120, 'SN-GW1000-0010', 'AVAILABLE', NULL, NULL, 103, 1, '2026-08-17 22:16:17', NULL, NULL, '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_mes_resource_pool
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_resource_pool`;
CREATE TABLE `bak_20260914_mes_resource_pool`  (
  `pool_id` bigint(20) NOT NULL COMMENT '资源池ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池编码(自动编码 MES_POOL_CODE)',
  `pool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池名称',
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型: MAC/SN/CERT',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INTERNAL' COMMENT '来源: INTERNAL(内部导入)/EXTERNAL(外部REST)',
  `external_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外部REST配置JSON(baseUrl/路径/鉴权, 经SSRF校验)',
  `alloc_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIFO' COMMENT '分配策略: FIFO/随机',
  `total_count` int(11) NULL DEFAULT NULL COMMENT '总数量',
  `used_count` int(11) NOT NULL DEFAULT 0 COMMENT '已分配数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_resource_pool
-- ----------------------------
INSERT INTO `bak_20260914_mes_resource_pool` VALUES (2089376872637935618, '000000', 'PL20260817012', 'GW-1000产线MAC池', 'MAC', 'INTERNAL', NULL, 'FIFO', 10, 1, 'ENABLED', 103, 1, '2026-08-17 23:40:40', 1, '2026-08-17 23:40:40', '0', '全链路正式数据-MAC池');

-- ----------------------------
-- Table structure for bak_20260914_mes_sn_bind_log
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_sn_bind_log`;
CREATE TABLE `bak_20260914_mes_sn_bind_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作: BIND/UNBIND/REBIND',
  `parent_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '父SN ID',
  `parent_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父SN编码',
  `component_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '组件SN ID',
  `component_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '组件SN编码',
  `new_component_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '换板新组件SN编码(REBIND)',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作原因(解绑/换板必填)',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员工号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_sn_bind_log
-- ----------------------------
INSERT INTO `bak_20260914_mes_sn_bind_log` VALUES (2090696597551636481, '000000', 'BIND', 1, 'SN202606200001', 2, 'SN202606200002', NULL, NULL, 'SCZX20251222017', -1, -1, '2026-08-21 15:04:47', -1, '2026-08-21 15:04:47', '0');
INSERT INTO `bak_20260914_mes_sn_bind_log` VALUES (2090696660399087617, '000000', 'UNBIND', 1, 'SN202606200001', 2, 'SN202606200002', NULL, '�����������', 'SCZX20251222017', -1, -1, '2026-08-21 15:05:02', -1, '2026-08-21 15:05:02', '0');
INSERT INTO `bak_20260914_mes_sn_bind_log` VALUES (2090696660449419265, '000000', 'REBIND', 1, 'SN202606200001', 3, 'SN202606200003', 'SN202606200002', '�����������', 'SCZX20251222017', -1, -1, '2026-08-21 15:05:02', -1, '2026-08-21 15:05:02', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_station_device
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_station_device`;
CREATE TABLE `bak_20260914_mes_station_device`  (
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编码(工控机)',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绑定工位编码',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绑定工序编码',
  `app_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '终端版本',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前操作员工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前操作员姓名',
  `shutdown_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '主动下线标记: 1=应用正常退出(即时离线判定)',
  `last_heartbeat` datetime(0) NULL DEFAULT NULL COMMENT '最后心跳时间',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_station_device
-- ----------------------------
INSERT INTO `bak_20260914_mes_station_device` VALUES (2090677150346575873, '000000', 'UNREGISTERED', NULL, NULL, '33.4.11', NULL, NULL, '1', '2026-08-22 20:03:43', -1, -1, '2026-08-21 13:47:30', -1, '2026-08-22 20:03:43', '0');
INSERT INTO `bak_20260914_mes_station_device` VALUES (2090691380416282625, '000000', 'TEST-DEV-01', 'STN|总装车间|装配线1|ASM|GENERAL', 'ASM', '0.1.0', NULL, NULL, '0', '2026-08-21 14:44:03', -1, -1, '2026-08-21 14:44:03', -1, '2026-08-22 18:18:30', '0');
INSERT INTO `bak_20260914_mes_station_device` VALUES (2090729868033466369, '000000', '一号工位机', NULL, NULL, '0.1.0', NULL, NULL, '1', '2026-08-21 17:50:53', -1, -1, '2026-08-21 17:16:59', -1, '2026-08-21 17:50:52', '0');
INSERT INTO `bak_20260914_mes_station_device` VALUES (2090804010377306113, '000000', 'GZZ0002', 'STN|TEST|L1|QC|FLASH', 'QC', '1.0.0', 'SCZX20251222019', '王思远', '0', '2026-08-31 00:24:26', -1, -1, '2026-08-21 22:11:36', -1, '2026-08-31 00:24:25', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_station_report
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_station_report`;
CREATE TABLE `bak_20260914_mes_station_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报工记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `report_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报工类型: SINGLE(单件过站)/BATCH(批量计数)',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SN编码(SINGLE)',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工位编码',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员用户ID',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '结果: PASS/FAIL(SINGLE)',
  `defect_codes` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '不良代码(逗号分隔)',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良数量',
  `client_ts` bigint(20) NOT NULL COMMENT '客户端时间戳(毫秒)',
  `client_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键: SINGLE|sn|station|ts 或 BATCH|station|ts',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_station_report
-- ----------------------------
INSERT INTO `bak_20260914_mes_station_report` VALUES (2090696331347550210, '000000', 'SINGLE', 'SN202606200001', 'STN|ע�ܳ���|����1|ASM|GENERAL', 1, 3, 1, 'WO-TEST-001', 105, 'SCZX20251222017', '张明', 'PASS', NULL, 1.0000, 0.0000, 1787295823000, 'SINGLE|SN202606200001|STN|ע�ܳ���|����1|ASM|GENERAL|1787295823000', -1, -1, '2026-08-21 15:03:43', -1, '2026-08-21 15:03:43', '0');
INSERT INTO `bak_20260914_mes_station_report` VALUES (2090696529842987010, '000000', 'BATCH', NULL, 'STN|JZ|L1|PKG|GENERAL', 1, 4, 1, 'WO-TEST-001', 106, 'SCZX20251222018', '李芳华', NULL, 'D01', 2.0000, 1.0000, 1787295870000, 'BATCH|STN|JZ|L1|PKG|GENERAL|1787295870000', -1, -1, '2026-08-21 15:04:31', -1, '2026-08-21 15:04:31', '0');

-- ----------------------------
-- Table structure for bak_20260914_mes_test_item
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_test_item`;
CREATE TABLE `bak_20260914_mes_test_item`  (
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(如 RF-TXPWR)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID',
  `content_ref_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '内容源ID(0=该物料所有版本通用, 固件类=mes_firmware_id)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NUMERIC' COMMENT '数据类型: NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(JSON数组)',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否必测(0否/1是)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_mes_test_item
-- ----------------------------
INSERT INTO `bak_20260914_mes_test_item` VALUES (2088902653508710401, '000000', 'cs1', '测试1', 1001, 2088889870117240834, 'NUMERIC', 'dBm/V', 221.0000, 219.0000, 220.0000, NULL, '1', 1, 103, 1, '2026-08-16 16:16:17', 1, '2026-08-16 16:18:21', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_mes_test_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_mes_test_record`;
CREATE TABLE `bak_20260914_mes_test_record`  (
  `test_record_id` bigint(20) NOT NULL COMMENT '测试记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `test_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试单号(一次SN测试一条单号, 共享多行)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(测试工序)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '测试时内容源ID(固件类=mes_firmware_id)',
  `content_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试时内容版本',
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称(快照)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型(快照)',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `numeric_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '实测数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测文本',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限(快照)',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限(快照)',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值(快照)',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(快照)',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '结果: PASS/FAIL(空=未测)',
  `tester_id` bigint(20) NULL DEFAULT NULL COMMENT '测试人ID',
  `tester_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试人姓名',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '测试设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试设备编码',
  `test_time` datetime(0) NULL DEFAULT NULL COMMENT '测试时间',
  `test_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '测试模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_ncr_8d_report
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_ncr_8d_report`;
CREATE TABLE `bak_20260914_ncr_8d_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报告ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_id` bigint(20) NOT NULL COMMENT '关联NCR ID',
  `d1_team` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D1团队',
  `d2_problem` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D2问题描述',
  `d3_interim` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D3临时措施',
  `d4_root_cause` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D4根因分析',
  `d5_permanent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D5永久措施',
  `d6_implement` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D6措施实施与验证',
  `d7_prevent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D7预防措施',
  `d8_recognize` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D8团队认可',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_PROGRESS/COMPLETED',
  `completed_by` bigint(20) NULL DEFAULT NULL COMMENT '完成人',
  `completed_time` datetime(0) NULL DEFAULT NULL COMMENT '完成时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_ncr_8d_report
-- ----------------------------
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082122141029011457, '000000', 2082122139888160769, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:12:57', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082122243898511362, '000000', 2082122242732494850, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:13:22', 103, 1, '2026-07-28 23:13:22', 1, '2026-07-28 23:13:22', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082122379726852098, '000000', 2082122378531475458, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:13:54', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082123172148318210, '000000', 2082122379370336257, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:17:03', 1, '2026-07-28 23:17:03', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082132871811866625, '000000', 2082122140596998146, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:35', 1, '2026-07-28 23:55:35', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082132918037291009, '000000', 2082128180327653377, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:46', 1, '2026-07-28 23:55:46', '0');
INSERT INTO `bak_20260914_ncr_8d_report` VALUES (2082132972085092354, '000000', 2082128179820142593, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:59', 1, '2026-07-28 23:55:59', '0');

-- ----------------------------
-- Table structure for bak_20260914_ncr_defect
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_ncr_defect`;
CREATE TABLE `bak_20260914_ncr_defect`  (
  `defect_id` bigint(20) NOT NULL COMMENT '缺陷明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_id` bigint(20) NOT NULL COMMENT 'NCR ID',
  `defect_code_id` bigint(20) NULL DEFAULT NULL COMMENT '缺陷代码ID(可空-自动建单时仅记defectCode字符串,REVIEW时关联正式代码库)',
  `defect_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷代码(冗余)',
  `defect_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷名称(冗余)',
  `defect_qty` decimal(14, 4) NOT NULL COMMENT '缺陷数量',
  `defect_cause` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷原因(MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT)',
  `root_cause_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '根因描述',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_ncr_defect
-- ----------------------------
INSERT INTO `bak_20260914_ncr_defect` VALUES (2082128179824336898, '000000', 2082128179820142593, NULL, 'SCRATCH', NULL, 3.0000, NULL, '表面划痕', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_ncr_defect` VALUES (2082128179828531202, '000000', 2082128179820142593, NULL, 'DENT', NULL, 1.0000, NULL, '凹痕', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

-- ----------------------------
-- Table structure for bak_20260914_ncr_defect_code
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_ncr_defect_code`;
CREATE TABLE `bak_20260914_ncr_defect_code`  (
  `code_id` bigint(20) NOT NULL COMMENT '代码ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_id` bigint(20) NULL DEFAULT 0 COMMENT '父代码ID(层级,0=根)',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷代码',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷名称',
  `level` int(4) NULL DEFAULT 1 COMMENT '层级(1类别/2子类/3代码)',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '是否启用(1/0)',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_ncr_defect_code
-- ----------------------------
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946001, '000000', 0, 'D-APP', '外观不良', 1, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946002, '000000', 0, 'D-FUN', '功能不良', 1, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946003, '000000', 0, 'D-ASM', '组装不良', 1, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946004, '000000', 0, 'D-SW', '软件烧写', 1, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946005, '000000', 0, 'D-DIM', '尺寸超差', 1, 'Y', 5, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946006, '000000', 0, 'D-OTH', '其他', 1, 'Y', 6, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946011, '000000', 946001, 'D-APP-01', '划伤碰撞', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946012, '000000', 946001, 'D-APP-02', '脏污氧化', 2, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946013, '000000', 946001, 'D-APP-03', '丝印标识', 2, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946014, '000000', 946002, 'D-FUN-01', '电性能', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946015, '000000', 946002, 'D-FUN-02', '整机功能', 2, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946016, '000000', 946002, 'D-FUN-03', '射频声学', 2, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946017, '000000', 946003, 'D-ASM-01', '装配错漏', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946018, '000000', 946003, 'D-ASM-02', '焊接连接', 2, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946019, '000000', 946003, 'D-ASM-03', '紧固密封', 2, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946020, '000000', 946004, 'D-SW-01', '烧写失败', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946021, '000000', 946004, 'D-SW-02', '版本参数', 2, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946022, '000000', 946004, 'D-SW-03', '测试软件', 2, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946023, '000000', 946005, 'D-DIM-01', '结构尺寸', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946024, '000000', 946005, 'D-DIM-02', '形位公差', 2, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946025, '000000', 946006, 'D-OTH-01', '其他', 2, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946101, '000000', 946011, 'D-APP-SCR', '表面划伤', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946102, '000000', 946011, 'D-APP-DENT', '凹痕压伤', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946103, '000000', 946011, 'D-APP-CHIP', '破损缺角', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946104, '000000', 946012, 'D-APP-STAIN', '表面脏污', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946105, '000000', 946012, 'D-APP-OXID', '氧化变色', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946106, '000000', 946012, 'D-APP-GLUE', '胶残留', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946107, '000000', 946013, 'D-APP-INK', '丝印不良', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946108, '000000', 946013, 'D-APP-LABEL', '标签错贴', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946109, '000000', 946013, 'D-APP-MARK', '标识缺失', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946110, '000000', 946014, 'D-FUN-SHORT', '短路', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946111, '000000', 946014, 'D-FUN-OPEN', '开路断路', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946112, '000000', 946014, 'D-FUN-LEAK', '漏电', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946113, '000000', 946014, 'D-FUN-VOLT', '电压超差', 3, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946114, '000000', 946015, 'D-FUN-NOPWR', '不开机', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946115, '000000', 946015, 'D-FUN-BOOT', '启动异常', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946116, '000000', 946015, 'D-FUN-RESET', '死机重启', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946117, '000000', 946015, 'D-FUN-NOSIG', '无信号', 3, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946118, '000000', 946016, 'D-FUN-RF', '射频指标超差', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946119, '000000', 946016, 'D-FUN-AUDIO', '音频异常', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946120, '000000', 946016, 'D-FUN-MIC', '麦克风失效', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946121, '000000', 946016, 'D-FUN-SPK', '喇叭失效', 3, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946122, '000000', 946017, 'D-ASM-MISS', '漏装', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946123, '000000', 946017, 'D-ASM-WRONG', '错装', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946124, '000000', 946017, 'D-ASM-REV', '反向安装', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946125, '000000', 946017, 'D-ASM-FIT', '配合不良', 3, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946126, '000000', 946018, 'D-ASM-SOLDER', '虚焊假焊', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946127, '000000', 946018, 'D-ASM-COLD', '冷焊', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946128, '000000', 946018, 'D-ASM-BRIDGE', '连锡桥连', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946129, '000000', 946019, 'D-ASM-SCREW', '螺丝浮高', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946130, '000000', 946019, 'D-ASM-GAP', '缝隙超标', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946131, '000000', 946019, 'D-ASM-GLUE', '点胶不良', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946132, '000000', 946020, 'D-SW-FLASH', '烧写失败', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946133, '000000', 946020, 'D-SW-VERIFY', '校验失败', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946134, '000000', 946020, 'D-SW-CHKSUM', '校验和不符', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946135, '000000', 946021, 'D-SW-VER', '版本错误', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946136, '000000', 946021, 'D-SW-MAC', 'MAC地址异常', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946137, '000000', 946021, 'D-SW-SN', 'SN写入异常', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946138, '000000', 946022, 'D-SW-TEST', '综测失败', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946139, '000000', 946022, 'D-SW-ITEM', '单项超差', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946140, '000000', 946023, 'D-DIM-LEN', '长度超差', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946141, '000000', 946023, 'D-DIM-WID', '宽度超差', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946142, '000000', 946023, 'D-DIM-THK', '厚度超差', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946143, '000000', 946023, 'D-DIM-HOLE', '孔位偏移', 3, 'Y', 4, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946144, '000000', 946024, 'D-DIM-FLAT', '平面度超差', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946145, '000000', 946024, 'D-DIM-POS', '位置度超差', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946146, '000000', 946025, 'D-OTH-UNK', '原因不明', 3, 'Y', 1, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946147, '000000', 946025, 'D-OTH-CUST', '客诉反馈', 3, 'Y', 2, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (946148, '000000', 946025, 'D-OTH-REWORK', '返工遗留', 3, 'Y', 3, NULL, 103, 1, '2026-08-21 22:00:00', NULL, NULL, '0');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (2082122139464536065, '000000', 0, 'SURF', '表面缺陷', 1, '1', 0, NULL, 103, 1, '2026-07-28 23:12:57', 1, '2026-08-21 23:12:35', '1');
INSERT INTO `bak_20260914_ncr_defect_code` VALUES (2082122378246262786, '000000', 0, 'SURF9293', '表面缺陷', 1, '1', 0, NULL, 103, 1, '2026-07-28 23:13:54', 1, '2026-08-21 23:12:35', '1');

-- ----------------------------
-- Table structure for bak_20260914_ncr_order
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_ncr_order`;
CREATE TABLE `bak_20260914_ncr_order`  (
  `ncr_id` bigint(20) NOT NULL COMMENT 'NCR ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NCR编号',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型(IQC/IPQC/OQC/PROCESS/CUSTOMER)',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID(iqc_id/ipqc_id/oqc_id/step_record_id)',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID(PROCESS来源)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '工序记录ID(PROCESS来源)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(IPQC/PROCESS来源)',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `lot_id` bigint(20) NULL DEFAULT NULL COMMENT '关联批次ID(隔离用)',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `defect_qty` decimal(14, 4) NOT NULL COMMENT '不良数量',
  `disposition` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处置(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OPEN' COMMENT 'OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED',
  `review_by` bigint(20) NULL DEFAULT NULL COMMENT '评审人',
  `review_time` datetime(0) NULL DEFAULT NULL COMMENT '评审时间',
  `disposition_by` bigint(20) NULL DEFAULT NULL COMMENT '处置人',
  `disposition_time` datetime(0) NULL DEFAULT NULL COMMENT '处置时间',
  `disposition_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '处置签名ID(P0-6预留)',
  `created_time` datetime(0) NOT NULL COMMENT '创建时间',
  `closed_time` datetime(0) NULL DEFAULT NULL COMMENT '关闭时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_ncr_order
-- ----------------------------
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122139888160769, '000000', 'NCR20260728001', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, NULL, 'REVIEW', 1, '2026-07-28 23:12:57', NULL, NULL, NULL, '2026-07-28 23:12:57', NULL, 'E2E自动化测试建单', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122140596998146, '000000', 'NCR20260728002', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:12:57', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122242732494850, '000000', 'NCR20260728003', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, 'ACCEPT_AS_IS', 'CLOSED', 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', NULL, '2026-07-28 23:13:21', '2026-07-28 23:13:21', '让步接收', 103, 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122243621687298, '000000', 'NCR20260728004', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:13:21', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122378531475458, '000000', 'NCR20260728005', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, 'ACCEPT_AS_IS', 'CLOSED', 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', NULL, '2026-07-28 23:13:54', '2026-07-28 23:13:54', '让步接收', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082122379370336257, '000000', 'NCR20260728006', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:13:54', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082124524291264514, '000000', 'NCR20260728008', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:22:25', NULL, '返工闭环测试', 103, 1, '2026-07-28 23:22:25', 1, '2026-07-28 23:22:25', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082125049405542401, '000000', 'NCR20260728010', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, NULL, 'REVIEW', 1, '2026-07-28 23:24:31', NULL, NULL, NULL, '2026-07-28 23:24:30', NULL, '返工闭环测试', 103, 1, '2026-07-28 23:24:30', 1, '2026-07-28 23:24:30', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082127796360093697, '000000', 'NCR20260728012', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, 'REWORK', 'IN_REWORK', 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', NULL, '2026-07-28 23:35:25', NULL, 'E2E返工', 103, 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082128179820142593, '000000', 'NCR20260728013', 'IQC', 2082128179698507778, NULL, NULL, NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 0.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:36:57', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_ncr_order` VALUES (2082128180327653377, '000000', 'NCR20260728014', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, 'REWORK', 'CLOSED', 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', NULL, '2026-07-28 23:36:57', '2026-07-28 23:36:58', 'E2E返工', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

-- ----------------------------
-- Table structure for bak_20260914_ncr_rework_order
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_ncr_rework_order`;
CREATE TABLE `bak_20260914_ncr_rework_order`  (
  `rework_id` bigint(20) NOT NULL COMMENT '返工记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rework_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '返工工单编号',
  `ncr_id` bigint(20) NOT NULL COMMENT '关联NCR ID',
  `original_wo_id` bigint(20) NOT NULL COMMENT '原工单ID',
  `original_wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原工单编号(冗余)',
  `rework_wo_id` bigint(20) NOT NULL COMMENT '生成的返工工单ID(pro_work_order.mes_wo_id)',
  `rework_wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '返工工单编号(冗余)',
  `rework_qty` decimal(14, 4) NOT NULL COMMENT '返工数量',
  `rework_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '返工原因',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/COMPLETED',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_ncr_rework_order
-- ----------------------------
INSERT INTO `bak_20260914_ncr_rework_order` VALUES (2082127797010210818, '000000', 'RW20260728002', 2082127796360093697, 1, 'WO-TEST-001', 2082127796938907650, 'RW20260728001', 5.0000, 'E2E返工', 'OPEN', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_ncr_rework_order` VALUES (2082128180793221121, '000000', 'RW20260728004', 2082128180327653377, 1, 'WO-TEST-001', 2082128180742889473, 'RW20260728003', 5.0000, 'E2E返工', 'COMPLETED', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_andon
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_andon`;
CREATE TABLE `bak_20260914_pro_andon`  (
  `andon_id` bigint(20) NOT NULL COMMENT '安灯ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `andon_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '安灯编号',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位编码(设备通道呼叫)',
  `call_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
  `call_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫原因',
  `andon_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
  `caller_id` bigint(20) NULL DEFAULT NULL COMMENT '呼叫人ID',
  `caller_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫人姓名',
  `responder_id` bigint(20) NULL DEFAULT NULL COMMENT '响应人ID',
  `responder_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '响应人姓名',
  `call_time` datetime(0) NULL DEFAULT NULL COMMENT '呼叫时间',
  `respond_time` datetime(0) NULL DEFAULT NULL COMMENT '响应时间',
  `resolve_time` datetime(0) NULL DEFAULT NULL COMMENT '解决时间',
  `duration_minutes` int(11) NULL DEFAULT NULL COMMENT '持续时长(分钟, 解决时计算)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_andon
-- ----------------------------
INSERT INTO `bak_20260914_pro_andon` VALUES (1, '000000', 'ANDON-001', 1, 'WO-TEST-001', 1, 'FC-001', 2, NULL, 'MATERIAL', '焊接工序焊条库存不足，需要紧急补料', 'RESOLVED', 30002, '李四', 30001, '张三', '2026-06-11 09:00:00', '2026-06-11 09:05:00', '2026-06-11 09:20:00', 20, 'MANUAL', '焊条已从仓库调配到位', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2, '000000', 'ANDON-002', 1, 'WO-TEST-001', 1, 'FC-001', 3, NULL, 'QUALITY', '装配发现车架焊接质量不达标，需质检介入', 'CALLED', 30003, '王五', NULL, NULL, '2026-06-12 14:00:00', NULL, NULL, NULL, 'MANUAL', '待质检人员响应', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (3, '000000', 'ANDON-003', 1, 'WO-TEST-001', 1, 'FC-001', 2, NULL, 'EQUIPMENT', '[IoT告警] CNC001温度探头报告主轴温度87.5℃ 超过阈值85℃', 'RESPONDED', NULL, 'IoT系统', 30001, '张三', '2026-06-15 10:15:00', '2026-06-15 10:16:00', NULL, NULL, 'IoT', 'IoT设备告警自动创建安灯', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2067643792365850626, '000000', 'AND-20260619-9718', NULL, NULL, NULL, NULL, NULL, NULL, 'QUALITY', '', 'RESOLVED', 1, 'admin', 1, 'admin', '2026-06-19 00:21:10', '2026-06-19 00:21:16', '2026-06-19 00:21:23', 0, 'MANUAL', '测试', 103, 1, '2026-06-19 00:21:10', 1, '2026-06-19 00:21:10', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090684862186848257, '000000', 'AND-20260821-8846', NULL, NULL, NULL, NULL, NULL, '', 'MATERIAL', NULL, 'CALLED', NULL, 'SCZX20251222018', NULL, NULL, '2026-08-21 14:18:09', NULL, NULL, NULL, 'MANUAL', NULL, -1, -1, '2026-08-21 14:18:09', -1, '2026-08-21 14:18:09', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090685604989698050, '000000', 'AND-20260821-5945', NULL, NULL, NULL, NULL, NULL, '', 'EQUIPMENT', NULL, 'CALLED', NULL, 'SCZX20251222018', NULL, NULL, '2026-08-21 14:21:06', NULL, NULL, NULL, 'MANUAL', NULL, -1, -1, '2026-08-21 14:21:06', -1, '2026-08-21 14:21:06', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090685740063064065, '000000', 'AND-20260821-8150', NULL, NULL, NULL, NULL, NULL, '', 'OTHER', NULL, 'RESOLVED', NULL, 'SCZX20251222018', 1, 'admin', '2026-08-21 14:21:38', '2026-08-21 14:21:56', '2026-08-21 14:22:20', 0, 'MANUAL', '测试', -1, -1, '2026-08-21 14:21:38', -1, '2026-08-21 14:21:38', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090685853300883458, '000000', 'AND-20260821-5148', NULL, NULL, NULL, NULL, NULL, '', 'QUALITY', NULL, 'CALLED', NULL, 'SCZX20251222018', NULL, NULL, '2026-08-21 14:22:05', NULL, NULL, NULL, 'MANUAL', NULL, -1, -1, '2026-08-21 14:22:05', -1, '2026-08-21 14:22:05', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090696389941977090, '000000', 'AND-20260821-7276', NULL, NULL, NULL, NULL, NULL, 'STN|ע�ܳ���|����1|PKG|GENERAL', 'QUALITY', '�������Ժ���', 'CALLED', NULL, 'SCZX20251222017', NULL, NULL, '2026-08-21 15:03:57', NULL, NULL, NULL, 'MANUAL', NULL, -1, -1, '2026-08-21 15:03:57', -1, '2026-08-21 15:03:57', '0');
INSERT INTO `bak_20260914_pro_andon` VALUES (2090696469331763202, '000000', 'AND-20260821-6206', NULL, NULL, NULL, NULL, NULL, 'STN|JZ|L1|PKG|GENERAL', 'MATERIAL', 'call-material-test', 'CALLED', NULL, 'SCZX20251222017', NULL, NULL, '2026-08-21 15:04:16', NULL, NULL, NULL, 'MANUAL', NULL, -1, -1, '2026-08-21 15:04:16', -1, '2026-08-21 15:04:16', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_dispatch
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_dispatch`;
CREATE TABLE `bak_20260914_pro_dispatch`  (
  `dispatch_id` bigint(20) NOT NULL COMMENT '派工单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `dispatch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '派工单编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `workcenter_id` bigint(20) NULL DEFAULT NULL,
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID(SchWorker.workerId)',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '派工数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `dispatch_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `plan_start_time` datetime(0) NULL DEFAULT NULL,
  `plan_end_time` datetime(0) NULL DEFAULT NULL,
  `actual_start_time` datetime(0) NULL DEFAULT NULL,
  `actual_end_time` datetime(0) NULL DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_dispatch
-- ----------------------------
INSERT INTO `bak_20260914_pro_dispatch` VALUES (1, '000000', 'DP-001', 1, 'WO-TEST-001', 1, 'FC-001', 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 30003, '王五', 50.0000, 50.0000, 49.0000, 1.0000, 0.0000, 'COMPLETED', '2026-06-10 08:00:00', NULL, '2026-06-10 08:05:00', '2026-06-10 16:30:00', '王五切割工序完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2, '000000', 'DP-002', 1, 'WO-TEST-001', 1, 'FC-001', 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 30002, '李四', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', '2026-06-11 08:00:00', NULL, '2026-06-11 08:10:00', '2026-06-24 11:36:52', '李四焊接工序进行中', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2069672596578680834, '000000', 'DIS-FC-SCGD202606220001-5210-4337', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288629600258, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 50.0000, 49.0000, 49.0000, 1.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:00:04', '2026-06-24 15:00:47', NULL, 103, 1, '2026-06-24 14:42:54', 1, '2026-06-24 14:42:54', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2069672880461758465, '000000', 'DIS-FC-SCGD202606220001-5210-2021', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288688320513, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:29', '2026-06-24 15:24:57', NULL, 103, 1, '2026-06-24 14:44:02', 1, '2026-06-24 14:44:02', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2069672909561839618, '000000', 'DIS-FC-SCGD202606220001-5210-8959', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288700903425, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:32', '2026-06-24 15:24:51', NULL, 103, 1, '2026-06-24 14:44:09', 1, '2026-06-24 14:44:09', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2069672943393095681, '000000', 'DIS-FC-SCGD202606220001-5210-7026', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288709292033, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:34', '2026-06-24 15:24:46', NULL, 103, 1, '2026-06-24 14:44:17', 1, '2026-06-24 14:44:17', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074152759460098050, '000000', 'DIS-FC-SCGD202607060001-3908-8417', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031869677570, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:26:31', '2026-07-06 23:26:43', NULL, 103, 1, '2026-07-06 23:25:28', 1, '2026-07-06 23:25:28', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074152779248824322, '000000', 'DIS-FC-SCGD202607060001-3908-3134', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031869677571, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:27:49', '2026-07-06 23:27:56', NULL, 103, 1, '2026-07-06 23:25:33', 1, '2026-07-06 23:25:33', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074152801914843138, '000000', 'DIS-FC-SCGD202607060001-3908-8534', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031928397825, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:27:58', '2026-07-06 23:28:07', NULL, 103, 1, '2026-07-06 23:25:39', 1, '2026-07-06 23:25:39', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074152826820620289, '000000', 'DIS-FC-SCGD202607060001-3908-4471', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031928397826, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:28:01', '2026-07-06 23:28:12', NULL, 103, 1, '2026-07-06 23:25:44', 1, '2026-07-06 23:25:44', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395341140967425, '000000', 'DIS-FC-SCGD202607070001-8776-4386', 2074394214269894658, 'SCGD202607070001', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394478821425153, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 0.0000, 0.0000, 0.0000, 0.0000, 'IN_PROGRESS', NULL, NULL, '2026-07-07 15:30:19', NULL, NULL, 103, 1, '2026-07-07 15:29:24', 1, '2026-07-07 15:29:24', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395357666525185, '000000', 'DIS-FC-SCGD202607070001-8776-8327', 2074394214269894658, 'SCGD202607070001', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394478884339714, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 0.0000, 0.0000, 0.0000, 0.0000, 'IN_PROGRESS', NULL, NULL, '2026-07-07 15:30:17', NULL, NULL, 103, 1, '2026-07-07 15:29:28', 1, '2026-07-07 15:29:28', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395407633268738, '000000', 'DIS-FC-SCGD202607070001-5213-0239', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757453234178, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:14', '2026-07-07 15:30:38', NULL, 103, 1, '2026-07-07 15:29:40', 1, '2026-07-07 15:29:40', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395419746418690, '000000', 'DIS-FC-SCGD202607070001-5213-3128', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757470011394, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:12', '2026-07-07 15:30:35', NULL, 103, 1, '2026-07-07 15:29:43', 1, '2026-07-07 15:29:43', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395431591133185, '000000', 'DIS-FC-SCGD202607070001-5213-5951', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757474205698, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:10', '2026-07-07 15:30:31', NULL, 103, 1, '2026-07-07 15:29:46', 1, '2026-07-07 15:29:46', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074395445751103489, '000000', 'DIS-FC-SCGD202607070001-5213-9326', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757490982913, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:09', '2026-07-07 15:30:27', NULL, 103, 1, '2026-07-07 15:29:49', 1, '2026-07-07 15:29:49', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074767446839824385, '000000', 'DIS-FC-SCGD202607080001-5802-1298', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130249564162, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:36', '2026-07-08 16:08:52', NULL, 103, 1, '2026-07-08 16:08:01', 1, '2026-07-08 16:08:01', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074767462119673857, '000000', 'DIS-FC-SCGD202607080001-5802-4941', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130295701506, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:34', '2026-07-08 16:08:49', NULL, 103, 1, '2026-07-08 16:08:05', 1, '2026-07-08 16:08:05', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074767483657424897, '000000', 'DIS-FC-SCGD202607080001-5802-0076', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130304090113, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 18, '周涛', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:33', '2026-07-08 16:08:46', NULL, 103, 1, '2026-07-08 16:08:10', 1, '2026-07-08 16:08:10', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074767502611484674, '000000', 'DIS-FC-SCGD202607080001-5802-4595', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130320867330, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 19, '吴静', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:31', '2026-07-08 16:08:42', NULL, 103, 1, '2026-07-08 16:08:15', 1, '2026-07-08 16:08:15', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074787569524969473, '000000', 'DIS-FC-SCGD202607080002-6809-8920', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476835045377, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 20, '黄俊杰', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:49', '2026-07-08 17:35:13', NULL, 103, 1, '2026-07-08 17:27:59', 1, '2026-07-08 17:27:59', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074787589187866626, '000000', 'DIS-FC-SCGD202607080002-6809-3607', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476851822593, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 21, '林小雨', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:47', '2026-07-08 17:35:01', NULL, 103, 1, '2026-07-08 17:28:04', 1, '2026-07-08 17:28:04', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074787608284532737, '000000', 'DIS-FC-SCGD202607080002-6809-8161', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476860211202, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:45', '2026-07-08 17:34:57', NULL, 103, 1, '2026-07-08 17:28:08', 1, '2026-07-08 17:28:08', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2074787626882076674, '000000', 'DIS-FC-SCGD202607080002-6809-2596', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476868599810, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 14, '王思远', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:43', '2026-07-08 17:34:54', NULL, 103, 1, '2026-07-08 17:28:13', 1, '2026-07-08 17:28:13', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2075402304033329154, '000000', 'DIS-FC-SCGD202607100001-0754-3047', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210584236033, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 18, '周涛', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:15', '2026-07-10 10:11:41', NULL, 103, 1, '2026-07-10 10:10:43', 1, '2026-07-10 10:10:43', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2075402316997922817, '000000', 'DIS-FC-SCGD202607100001-0754-6139', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210655539202, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:14', '2026-07-10 10:11:32', NULL, 103, 1, '2026-07-10 10:10:46', 1, '2026-07-10 10:10:46', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2075402335050207233, '000000', 'DIS-FC-SCGD202607100001-0754-0444', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210659733506, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 15, '刘薇薇', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:13', '2026-07-10 10:11:27', NULL, 103, 1, '2026-07-10 10:10:50', 1, '2026-07-10 10:10:50', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2075402351756120065, '000000', 'DIS-FC-SCGD202607100001-0754-4426', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210672316417, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:11', '2026-07-10 10:11:22', NULL, 103, 1, '2026-07-10 10:10:54', 1, '2026-07-10 10:10:54', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2080299991368347649, '000000', 'DIS-FC-SCGD202607230001-7515-2707', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927648481283, 10, 1, 'CUT', '切割', 2, 'GZZ0003', 12, '张明', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:46', '2026-07-23 22:33:00', NULL, 103, 1, '2026-07-23 22:32:23', 1, '2026-07-23 22:32:23', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2080300008791486466, '000000', 'DIS-FC-SCGD202607230001-7515-6861', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927703007233, 20, 2, 'WELD', '焊接', 6, 'GZZ0007', 13, '李芳华', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:45', '2026-07-23 22:32:57', NULL, 103, 1, '2026-07-23 22:32:27', 1, '2026-07-23 22:32:27', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2080300025757446146, '000000', 'DIS-FC-SCGD202607230001-7515-0908', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927703007234, 30, 5, 'PKG', '包装', 9, 'GZZ0010', 14, '王思远', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:42', '2026-07-23 22:32:53', NULL, 103, 1, '2026-07-23 22:32:31', 1, '2026-07-23 22:32:31', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2089705468250308610, '000000', 'DIS-FC-SCGD202608180001-3403-3141', 2089704730308657153, 'SCGD202608180001', 2089705007984164865, 'FC-SCGD202608180001-3403', 2089705007984164866, 1, 90002001, 'FLASH', '固件刷写', 3, '', 14, '王思远', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-08-18 21:26:46', '2026-08-18 21:26:52', NULL, 103, 1, '2026-08-18 21:26:23', 1, '2026-08-18 21:26:23', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2089710801203085314, '000000', 'DIS-FC-SCGD202608180001-3534-4622', 2089710626887811073, 'SCGD202608180001', 2089710754692448257, 'FC-SCGD202608180001-3534', 2089710754755362818, 1, 90002001, 'FLASH', '固件刷写', 3, '', 13, '李芳华', 5.0000, 5.0000, 5.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-08-18 21:48:08', '2026-08-18 21:48:16', NULL, 103, 1, '2026-08-18 21:47:35', 1, '2026-08-18 21:47:35', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2089714118352232449, '000000', 'DIS-FC-SCGD202608180001-7372-5492', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, 'FC-SCGD202608180001-7372', 2089714042397581313, 1, 90002001, 'FLASH', '固件刷写', 3, '', 13, '李芳华', 3.0000, 3.0000, 3.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-08-18 22:01:00', '2026-08-18 22:41:50', NULL, 103, 1, '2026-08-18 22:00:45', 1, '2026-08-18 22:00:45', '0');
INSERT INTO `bak_20260914_pro_dispatch` VALUES (2089731257016524801, '000000', 'DIS-FC-SCGD202608180001-3431-1673', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089731180625666050, 1, 90002001, 'FLASH', '固件刷写', 3, '', 14, '王思远', 6.0000, 6.0000, 6.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-08-18 23:09:28', '2026-08-18 23:10:44', NULL, 103, 1, '2026-08-18 23:08:52', 1, '2026-08-18 23:08:52', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_flow_card
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_flow_card`;
CREATE TABLE `bak_20260914_pro_flow_card`  (
  `card_id` bigint(20) NOT NULL COMMENT '流转卡ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '流转卡编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `current_step_id` bigint(20) NULL DEFAULT NULL COMMENT '当前工序步骤记录ID',
  `current_step_seq` int(4) NULL DEFAULT NULL COMMENT '当前工序序号',
  `card_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CREATED' COMMENT '流转卡状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
  `barcode` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡条码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_flow_card
-- ----------------------------
INSERT INTO `bak_20260914_pro_flow_card` VALUES (1, '000000', 'FC-001', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, 3, 1, 'COMPLETED', 'BC-FC-001', '流转卡#1:首批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-16 17:53:11', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2, '000000', 'FC-002', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, NULL, 1, 'CREATED', 'BC-FC-002', '流转卡#2:第二批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2069625288491188226, '000000', 'FC-SCGD202606220001-5210', 2068925615991463937, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', 50.0000, 50.0000, 0.0000, 2069625288629600258, NULL, 'CLOSED', 'FC2069625288491188226', NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 16:07:12', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2074140031802568706, '000000', 'FC-SCGD202607060001-3908', 2074134800092008450, 'SCGD202607060001', 8003, 'RACK-001', '简易金属工具架', 10.0000, 10.0000, 0.0000, 2074140031869677570, NULL, 'CLOSED', 'FC2074140031802568706', NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:47:30', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2074394757411291138, '000000', 'FC-SCGD202607070001-5213', 2074394214269894658, 'SCGD202607070001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074394757470011394, NULL, 'COMPLETED', 'FC2074394757411291138', NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 16:15:23', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2074767130190843905, '000000', 'FC-SCGD202607080001-5802', 2074766761268252674, 'SCGD202607080001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074767130249564162, NULL, 'COMPLETED', 'FC2074767130190843905', NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:51', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2074787476788908033, '000000', 'FC-SCGD202607080002-6809', 2074787137209667586, 'SCGD202607080002', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074787476835045377, NULL, 'COMPLETED', 'FC2074787476788908033', NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:13', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2075402210525515778, '000000', 'FC-SCGD202607100001-0754', 2075397661844770817, 'SCGD202607100001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2075402210584236033, NULL, 'COMPLETED', 'FC2075402210525515778', NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:40', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2080299927648481282, '000000', 'FC-SCGD202607230001-7515', 2080270525816516609, 'SCGD202607230001', 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 20.0000, 0.0000, 2080299927648481283, NULL, 'COMPLETED', 'FC2080299927648481282', NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:33:00', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2082127798264307713, '000000', 'FC-RW20260728001-5826', 2082127796938907650, 'RW20260728001', 1001, 'IF20251110001', '自行车', 5.0000, 0.0000, 0.0000, 2082127798297862146, NULL, 'IN_PROGRESS', 'FC2082127798264307713', NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2082128181665636354, '000000', 'FC-RW20260728003-7237', 2082128180742889473, 'RW20260728003', 1001, 'IF20251110001', '自行车', 5.0000, 5.0000, 0.0000, 2082128181686607873, NULL, 'COMPLETED', 'FC2082128181665636354', NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089714042334666753, '000000', 'FC-SCGD202608180001-7372', 2089713975506821122, 'SCGD202608180001', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 3.0000, 3.0000, 0.0000, 2089714042397581313, NULL, 'COMPLETED', 'FC2089714042334666753', NULL, 103, 1, '2026-08-18 22:00:27', 1, '2026-08-18 22:41:50', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089731180571140098, '000000', 'FC-SCGD202608180001-3431', 2089713975506821122, 'SCGD202608180001', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 6.0000, 6.0000, 0.0000, 2089731180625666050, NULL, 'COMPLETED', 'FC2089731180571140098', NULL, 103, 1, '2026-08-18 23:08:33', 1, '2026-08-18 23:39:38', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089736110841372674, '000000', 'FC-SCGD202608180001-8898', 2089713975506821122, 'SCGD202608180001', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.0000, 1.0000, 0.0000, 2089736110895898625, NULL, 'COMPLETED', 'FC2089736110841372674', NULL, 103, 1, '2026-08-18 23:28:09', 1, '2026-08-18 23:29:10', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089743106818752514, '000000', 'FC-SCGD202608180002V6776-6871', 2089743106437070849, 'SCGD202608180002V6776', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 6.0000, 6.0000, 0.0000, 2089743106839724034, NULL, 'COMPLETED', 'FC2089743106818752514', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089743108337090561, '000000', 'FC-SCGD202608180002V6776-7235', 2089743106437070849, 'SCGD202608180002V6776', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 4.0000, 4.0000, 0.0000, 2089743108358062082, NULL, 'COMPLETED', 'FC2089743108337090561', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089743634378309634, '000000', 'FC-SCGD202608180002V2560-2653', 2089743634013405186, 'SCGD202608180002V2560', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 6.0000, 6.0000, 0.0000, 2089743634403475458, NULL, 'COMPLETED', 'FC2089743634378309634', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:02', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089743635917619202, '000000', 'FC-SCGD202608180002V2560-3019', 2089743634013405186, 'SCGD202608180002V2560', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 4.0000, 4.0000, 0.0000, 2089743635934396418, NULL, 'COMPLETED', 'FC2089743635917619202', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089745255535525890, '000000', 'FC-SCGD202608190001V9085-9166', 2089745255220953090, 'SCGD202608190001V9085', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 6.0000, 6.0000, 0.0000, 2089745255552303106, NULL, 'COMPLETED', 'FC2089745255535525890', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089745256990949377, '000000', 'FC-SCGD202608190001V9085-9513', 2089745255220953090, 'SCGD202608190001V9085', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 4.0000, 4.0000, 0.0000, 2089745257007726593, NULL, 'COMPLETED', 'FC2089745256990949377', NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:29', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089745699947200514, '000000', 'FC-SCGD202608190001V5037-5122', 2089745699615850497, 'SCGD202608190001V5037', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 6.0000, 6.0000, 0.0000, 2089745699963977730, NULL, 'COMPLETED', 'FC2089745699947200514', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2089745701427789826, '000000', 'FC-SCGD202608190001V5037-5475', 2089745699615850497, 'SCGD202608190001V5037', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 4.0000, 4.0000, 0.0000, 2089745701444567042, NULL, 'COMPLETED', 'FC2089745701427789826', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2091201164656816129, '000000', 'FC-SCGD202608230001-4957', 2091201155861360641, 'SCGD202608230001', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', 10.0000, 10.0000, 0.0000, 2091201164723924994, NULL, 'IN_PROGRESS', 'FC2091201164656816129', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2099000000000000021, '000000', 'FC-QCTEST-0001', 2099000000000000001, 'WO-QCTEST-001', 1001, 'IF20251110001', '自行车', 10.0000, 0.0000, 0.0000, 2099000000000000033, 30, 'IN_PROGRESS', 'BC-QCTEST-0001', '工位机QC测试工位专用流转卡', -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');
INSERT INTO `bak_20260914_pro_flow_card` VALUES (2099000000000000022, '000000', 'FC-QCTEST-F001', 2099000000000000001, 'WO-QCTEST-001', 1001, 'IF20251110001', '自行车', 3.0000, 0.0000, 0.0000, 2099000000000000034, 10, 'IN_PROGRESS', 'BC-QCTEST-F001', '工位机QC测试工位刷写专用卡', -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_flow_card_step
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_flow_card_step`;
CREATE TABLE `bak_20260914_pro_flow_card_step`  (
  `step_record_id` bigint(20) NOT NULL COMMENT '工序记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `card_id` bigint(20) NOT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID(冗余便于查询)',
  `wo_step_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单工序步骤ID(pro_work_order_step.step_id)',
  `sequence_num` int(4) NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(来自ERP bd_routing_step)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
  `step_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `actual_start_time` datetime(0) NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime(0) NULL DEFAULT NULL COMMENT '实际结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `entry_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验结果(PASS/FAIL)',
  `entry_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验备注(失败原因)',
  `exit_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验结果(PASS/FAIL)',
  `exit_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验备注(失败原因)',
  `skip_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳站原因',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_flow_card_step
-- ----------------------------
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (1, '000000', 1, 'FC-001', 1, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 30003, '王五', 50.0000, 49.0000, 1.0000, 0.0000, NULL, NULL, '切割完成:49良+1不良', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-18 00:07:59', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2, '000000', 1, 'FC-001', 1, 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1, 'COMPLETED', 30002, '李四', 50.0000, 50.0000, 0.0000, 0.0000, NULL, '2026-06-24 11:36:52', '焊接进行中(关键工序)', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-18 00:07:59', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (3, '000000', 1, 'FC-001', 1, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1, 'COMPLETED', 1, NULL, 50.0000, 51.0000, 0.0000, 0.0000, '2026-07-27 23:18:08', '2026-08-21 15:03:43', '待装配(关键工序)', 'PASS', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-21 15:03:43', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (4, '000000', 1, 'FC-001', 1, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'IN_PROGRESS', NULL, NULL, 50.0000, 2.0000, 1.0000, 0.0000, '2026-08-21 15:04:06', NULL, '待包装', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-21 15:04:30', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2069625288629600258, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616075350017, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 50.0000, 49.0000, 1.0000, 0.0000, '2026-06-24 14:44:22', '2026-06-24 15:00:47', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:00:46', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2069625288688320513, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616113098753, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:29', '2026-06-24 15:24:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:56', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2069625288700903425, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616125681666, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:32', '2026-06-24 15:24:51', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:50', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2069625288709292033, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616142458881, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:34', '2026-06-24 15:24:46', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074140031869677570, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800092008451, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:25:49', '2026-07-06 23:26:43', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:26:42', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074140031869677571, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728706, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:27:49', '2026-07-06 23:27:56', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:27:55', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074140031928397825, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728707, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:27:58', '2026-07-06 23:28:07', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:28:06', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074140031928397826, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728708, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:28:01', '2026-07-06 23:28:12', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:28:11', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394478821425153, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214299254786, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'IN_PROGRESS', 12, '张明', 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-07 15:30:19', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:30:19', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394478884339714, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214341197826, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'IN_PROGRESS', 13, '李芳华', 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-07 15:30:17', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:30:17', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394478896922625, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214353780737, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'IN_PROGRESS', 1, NULL, 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-27 23:14:00', NULL, NULL, 'PASS', NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-27 23:14:00', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394478905311234, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214366363650, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 1.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:25:59', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394757453234178, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214299254786, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 12, '张明', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:14', '2026-07-07 15:30:38', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:37', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394757470011394, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214341197826, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', NULL, '李芳华', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:29:55', '2026-07-07 15:30:35', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:34', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394757474205698, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214353780737, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:10', '2026-07-07 15:30:31', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:31', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074394757490982913, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214366363650, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:09', '2026-07-07 15:30:27', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:26', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074767130249564162, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761289224194, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:19', '2026-07-08 16:08:52', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:51', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074767130295701506, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761360527361, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:34', '2026-07-08 16:08:49', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:48', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074767130304090113, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761373110274, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 18, '周涛', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:33', '2026-07-08 16:08:46', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074767130320867330, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761389887489, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 19, '吴静', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:31', '2026-07-08 16:08:42', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:42', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074787476835045377, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137239027713, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '黄俊杰', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:30:37', '2026-07-08 17:35:13', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:13', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074787476851822593, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137280970754, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 21, '林小雨', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:47', '2026-07-08 17:35:01', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:01', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074787476860211202, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137297747969, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:45', '2026-07-08 17:34:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:34:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2074787476868599810, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137310330881, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 14, '王思远', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:43', '2026-07-08 17:34:54', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:34:54', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2075402210584236033, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661861548033, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '周涛', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:10:59', '2026-07-10 10:11:41', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:40', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2075402210655539202, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661953822721, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:14', '2026-07-10 10:11:32', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:31', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2075402210659733506, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661970599938, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 15, '刘薇薇', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:13', '2026-07-10 10:11:27', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:27', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2075402210672316417, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661983182849, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:11', '2026-07-10 10:11:22', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:22', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2080299927648481283, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516611, 10, 1, 'CUT', '切割', 2, 'GZZ0003', 0, 'COMPLETED', NULL, '张明', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:34', '2026-07-23 22:33:00', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:33:00', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2080299927703007233, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516612, 20, 2, 'WELD', '焊接', 6, 'GZZ0007', 0, 'COMPLETED', 13, '李芳华', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:45', '2026-07-23 22:32:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:32:56', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2080299927703007234, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516613, 30, 5, 'PKG', '包装', 9, 'GZZ0010', 0, 'COMPLETED', 14, '王思远', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:42', '2026-07-23 22:32:53', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:32:53', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082127798297862146, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797928763393, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'IN_PROGRESS', 1, NULL, 5.0000, 0.0000, 0.0000, 0.0000, '2026-07-28 23:35:26', NULL, NULL, 'PASS', NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082127798306250753, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797941346305, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082127798310445057, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797953929218, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082127798318833665, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797962317825, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082128181686607873, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181367840770, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:57', '2026-07-28 23:36:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082128181694996481, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181376229377, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:57', '2026-07-28 23:36:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082128181699190785, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181380423681, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:58', '2026-07-28 23:36:58', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2082128181707579394, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181397200897, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:58', '2026-07-28 23:36:58', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089714042397581313, '000000', 2089714042334666753, 'FC-SCGD202608180001-7372', 2089713975506821122, 2089713975506821123, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 13, '李芳华', 3.0000, 3.0000, 0.0000, 0.0000, '2026-08-18 22:01:00', '2026-08-18 22:41:50', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-18 22:00:27', 1, '2026-08-18 22:41:50', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089731180625666050, '000000', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089713975506821122, 2089713975506821123, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 14, '王思远', 6.0000, 6.0000, 0.0000, 0.0000, '2026-08-18 23:09:17', '2026-08-18 23:10:44', NULL, 'PASS', NULL, NULL, NULL, NULL, 103, 1, '2026-08-18 23:08:33', 1, '2026-08-18 23:39:38', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089736110895898625, '000000', 2089736110841372674, 'FC-SCGD202608180001-8898', 2089713975506821122, 2089713975506821123, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 13, NULL, 1.0000, 1.0000, 0.0000, 0.0000, '2026-08-18 23:28:22', '2026-08-18 23:29:10', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-18 23:28:09', 1, '2026-08-18 23:29:10', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089743106839724034, '000000', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089743106437070849, 2089743106671951873, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 6.0000, 6.0000, 0.0000, 0.0000, '2026-08-18 23:55:57', '2026-08-18 23:55:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089743108358062082, '000000', 2089743108337090561, 'FC-SCGD202608180002V6776-7235', 2089743106437070849, 2089743106671951873, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 4.0000, 4.0000, 0.0000, 0.0000, '2026-08-18 23:55:57', '2026-08-18 23:55:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089743634403475458, '000000', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089743634013405186, 2089743634231508993, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 6.0000, 6.0000, 0.0000, 0.0000, '2026-08-18 23:58:03', '2026-08-18 23:58:03', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:02', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089743635934396418, '000000', 2089743635917619202, 'FC-SCGD202608180002V2560-3019', 2089743634013405186, 2089743634231508993, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 4.0000, 4.0000, 0.0000, 0.0000, '2026-08-18 23:58:03', '2026-08-18 23:58:03', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089745255552303106, '000000', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089745255220953090, 2089745255401308162, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 6.0000, 6.0000, 0.0000, 0.0000, '2026-08-19 00:04:29', '2026-08-19 00:04:29', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089745257007726593, '000000', 2089745256990949377, 'FC-SCGD202608190001V9085-9513', 2089745255220953090, 2089745255401308162, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 4.0000, 4.0000, 0.0000, 0.0000, '2026-08-19 00:04:30', '2026-08-19 00:04:30', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:29', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089745699963977730, '000000', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089745699615850497, 2089745699808788482, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 6.0000, 6.0000, 0.0000, 0.0000, '2026-08-19 00:06:15', '2026-08-19 00:06:15', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2089745701444567042, '000000', 2089745701427789826, 'FC-SCGD202608190001V5037-5475', 2089745699615850497, 2089745699808788482, 1, 90002001, 'FLASH', '固件刷写', 3, '', 0, 'COMPLETED', 1, NULL, 4.0000, 4.0000, 0.0000, 0.0000, '2026-08-19 00:06:16', '2026-08-19 00:06:16', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924994, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155861360642, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 0, 'COMPLETED', 2091200580948111362, NULL, 10.0000, 10.0000, 0.0000, 0.0000, '2026-08-23 00:29:45', '2026-08-23 00:29:45', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924995, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155924275202, 2, 90002001, 'FLASH', '固件刷写', 2091200575474544642, 'GZZ0018', 0, 'COMPLETED', 2091200580948111362, NULL, 10.0000, 10.0000, 0.0000, 0.0000, '2026-08-23 00:29:45', '2026-08-23 00:29:45', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924996, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155924275203, 3, 2091200576372125698, 'FUNC_TEST', '整机功能测试', 2091200575604568065, 'GZZ0019', 0, 'PENDING', NULL, NULL, 10.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924997, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155924275204, 4, 2091200576565063681, 'WP_TEST', '防水气密测试', 2091200575604568065, 'GZZ0019', 0, 'PENDING', NULL, NULL, 10.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924998, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155924275205, 5, 4, 'QC', '质检', 2091200575793311745, 'GZZ0020', 0, 'PENDING', NULL, NULL, 10.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2091201164723924999, '000000', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091201155861360641, 2091201155924275206, 6, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 0, 'PENDING', NULL, NULL, 10.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2099000000000000031, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 10, 1, 'CUT', '切割', 13, 'GZZ0014', 0, 'COMPLETED', NULL, NULL, 10.0000, 10.0000, 0.0000, 0.0000, '2026-08-20 19:43:49', '2026-08-20 19:43:49', 'QC测试数据: 前道已完成', NULL, NULL, NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2099000000000000032, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 20, 2, 'WELD', '焊接', 11, 'GZZ0012', 1, 'COMPLETED', NULL, NULL, 10.0000, 10.0000, 0.0000, 0.0000, '2026-08-21 19:43:49', '2026-08-21 19:43:49', 'QC测试数据: 前道已完成', NULL, NULL, NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2099000000000000033, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 30, 4, 'QC', '质检', 16, 'GZZ0017', 1, 'IN_PROGRESS', NULL, NULL, 10.0000, 0.0000, 0.0000, 0.0000, '2026-08-22 19:43:49', NULL, 'QC测试工位当前在制工序', NULL, NULL, NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');
INSERT INTO `bak_20260914_pro_flow_card_step` VALUES (2099000000000000034, '000000', 2099000000000000022, 'FC-QCTEST-F001', 2099000000000000001, NULL, 10, 90002001, 'FLASH', '固件烧录', NULL, NULL, 1, 'IN_PROGRESS', NULL, NULL, 3.0000, 0.0000, 0.0000, 0.0000, '2026-08-22 19:43:49', NULL, 'QC测试工位刷写在制工序(seq 与工单刷写工序一致)', NULL, NULL, NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_sn
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_sn`;
CREATE TABLE `bak_20260914_pro_sn`  (
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN序列号编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `current_step_seq` int(11) NULL DEFAULT NULL COMMENT '当前工序序号',
  `sn_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
  `parent_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
  `parent_sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父SN编码(冗余)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
  `production_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID(委外=subcontract_id)',
  `subcontract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '委外单号(冗余,SN直达追溯委外订单)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_sn
-- ----------------------------
INSERT INTO `bak_20260914_pro_sn` VALUES (1, '000000', 'SN202606200001', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 40, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#001-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2, '000000', 'SN202606200002', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 3, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#002-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (3, '000000', 'SN202606200003', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 2, 'COMPLETED', 1, 'SN202606200001', 'MANUAL', '2026-06-20', '自行车SN#003-全部工序已完成', 100, 1, NULL, NULL, NULL, '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089716723199512577, '000000', 'SN-FC-SCGD202608180001-7372-0001', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, 'FC-SCGD202608180001-7372', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 22:11:07', 1, '2026-08-18 22:11:07', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089716723199512578, '000000', 'SN-FC-SCGD202608180001-7372-0002', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, 'FC-SCGD202608180001-7372', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 22:11:07', 1, '2026-08-18 22:11:07', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089716723199512579, '000000', 'SN-FC-SCGD202608180001-7372-0003', 2089713975506821122, 'SCGD202608180001', 2089714042334666753, 'FC-SCGD202608180001-7372', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 22:11:07', 1, '2026-08-18 22:11:07', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746177, '000000', 'SN-FC-SCGD202608180001-3431-0001', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746178, '000000', 'SN-FC-SCGD202608180001-3431-0002', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746179, '000000', 'SN-FC-SCGD202608180001-3431-0003', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746180, '000000', 'SN-FC-SCGD202608180001-3431-0004', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746181, '000000', 'SN-FC-SCGD202608180001-3431-0005', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089731476877746182, '000000', 'SN-FC-SCGD202608180001-3431-0006', 2089713975506821122, 'SCGD202608180001', 2089731180571140098, 'FC-SCGD202608180001-3431', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:09:44', 1, '2026-08-18 23:09:44', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089736183729987585, '000000', 'SN-FC-SCGD202608180001-8898-0001', 2089713975506821122, 'SCGD202608180001', 2089736110841372674, 'FC-SCGD202608180001-8898', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:28:26', 1, '2026-08-18 23:28:26', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107263348737, '000000', 'SN-FC-SCGD202608180002V6776-6871-0001', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107267543042, '000000', 'SN-FC-SCGD202608180002V6776-6871-0002', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107275931649, '000000', 'SN-FC-SCGD202608180002V6776-6871-0003', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107284320257, '000000', 'SN-FC-SCGD202608180002V6776-6871-0004', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107288514561, '000000', 'SN-FC-SCGD202608180002V6776-6871-0005', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743107292708866, '000000', 'SN-FC-SCGD202608180002V6776-6871-0006', 2089743106437070849, 'SCGD202608180002V6776', 2089743106818752514, 'FC-SCGD202608180002V6776-6871', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743108613914626, '000000', 'SN-FC-SCGD202608180002V6776-7235-0001', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, 'FC-SCGD202608180002V6776-7235', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743108622303233, '000000', 'SN-FC-SCGD202608180002V6776-7235-0002', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, 'FC-SCGD202608180002V6776-7235', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743108626497537, '000000', 'SN-FC-SCGD202608180002V6776-7235-0003', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, 'FC-SCGD202608180002V6776-7235', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743108630691842, '000000', 'SN-FC-SCGD202608180002V6776-7235-0004', 2089743106437070849, 'SCGD202608180002V6776', 2089743108337090561, 'FC-SCGD202608180002V6776-7235', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634718048258, '000000', 'SN-FC-SCGD202608180002V2560-2653-0001', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634722242561, '000000', 'SN-FC-SCGD202608180002V2560-2653-0002', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634726436866, '000000', 'SN-FC-SCGD202608180002V2560-2653-0003', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634730631169, '000000', 'SN-FC-SCGD202608180002V2560-2653-0004', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634734825474, '000000', 'SN-FC-SCGD202608180002V2560-2653-0005', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743634739019777, '000000', 'SN-FC-SCGD202608180002V2560-2653-0006', 2089743634013405186, 'SCGD202608180002V2560', 2089743634378309634, 'FC-SCGD202608180002V2560-2653', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743636177666050, '000000', 'SN-FC-SCGD202608180002V2560-3019-0001', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, 'FC-SCGD202608180002V2560-3019', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743636181860354, '000000', 'SN-FC-SCGD202608180002V2560-3019-0002', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, 'FC-SCGD202608180002V2560-3019', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743636186054658, '000000', 'SN-FC-SCGD202608180002V2560-3019-0003', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, 'FC-SCGD202608180002V2560-3019', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089743636190248962, '000000', 'SN-FC-SCGD202608180002V2560-3019-0004', 2089743634013405186, 'SCGD202608180002V2560', 2089743635917619202, 'FC-SCGD202608180002V2560-3019', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-18', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255866875905, '000000', 'SN-FC-SCGD202608190001V9085-9166-0001', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255871070210, '000000', 'SN-FC-SCGD202608190001V9085-9166-0002', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255879458818, '000000', 'SN-FC-SCGD202608190001V9085-9166-0003', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255879458819, '000000', 'SN-FC-SCGD202608190001V9085-9166-0004', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255887847425, '000000', 'SN-FC-SCGD202608190001V9085-9166-0005', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745255896236033, '000000', 'SN-FC-SCGD202608190001V9085-9166-0006', 2089745255220953090, 'SCGD202608190001V9085', 2089745255535525890, 'FC-SCGD202608190001V9085-9166', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745257276162049, '000000', 'SN-FC-SCGD202608190001V9085-9513-0001', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, 'FC-SCGD202608190001V9085-9513', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745257280356353, '000000', 'SN-FC-SCGD202608190001V9085-9513-0002', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, 'FC-SCGD202608190001V9085-9513', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745257284550657, '000000', 'SN-FC-SCGD202608190001V9085-9513-0003', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, 'FC-SCGD202608190001V9085-9513', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745257288744962, '000000', 'SN-FC-SCGD202608190001V9085-9513-0004', 2089745255220953090, 'SCGD202608190001V9085', 2089745256990949377, 'FC-SCGD202608190001V9085-9513', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:04:30', 1, '2026-08-19 00:04:30', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700257579009, '000000', 'SN-FC-SCGD202608190001V5037-5122-0001', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700261773313, '000000', 'SN-FC-SCGD202608190001V5037-5122-0002', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700265967618, '000000', 'SN-FC-SCGD202608190001V5037-5122-0003', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700270161922, '000000', 'SN-FC-SCGD202608190001V5037-5122-0004', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700274356226, '000000', 'SN-FC-SCGD202608190001V5037-5122-0005', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745700278550530, '000000', 'SN-FC-SCGD202608190001V5037-5122-0006', 2089745699615850497, 'SCGD202608190001V5037', 2089745699947200514, 'FC-SCGD202608190001V5037-5122', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745701700419585, '000000', 'SN-FC-SCGD202608190001V5037-5475-0001', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, 'FC-SCGD202608190001V5037-5475', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745701713002498, '000000', 'SN-FC-SCGD202608190001V5037-5475-0002', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, 'FC-SCGD202608190001V5037-5475', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745701717196801, '000000', 'SN-FC-SCGD202608190001V5037-5475-0003', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, 'FC-SCGD202608190001V5037-5475', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2089745701717196802, '000000', 'SN-FC-SCGD202608190001V5037-5475-0004', 2089745699615850497, 'SCGD202608190001V5037', 2089745701427789826, 'FC-SCGD202608190001V5037-5475', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-19', NULL, 103, 1, '2026-08-19 00:06:16', 1, '2026-08-19 00:06:16', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201164983971841, '000000', 'SN-FC-SCGD202608230001-4957-0001', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201164983971842, '000000', 'SN-FC-SCGD202608230001-4957-0002', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201164983971843, '000000', 'SN-FC-SCGD202608230001-4957-0003', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886401, '000000', 'SN-FC-SCGD202608230001-4957-0004', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886402, '000000', 'SN-FC-SCGD202608230001-4957-0005', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886403, '000000', 'SN-FC-SCGD202608230001-4957-0006', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886404, '000000', 'SN-FC-SCGD202608230001-4957-0007', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886405, '000000', 'SN-FC-SCGD202608230001-4957-0008', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886406, '000000', 'SN-FC-SCGD202608230001-4957-0009', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2091201165046886407, '000000', 'SN-FC-SCGD202608230001-4957-0010', 2091201155861360641, 'SCGD202608230001', 2091201164656816129, 'FC-SCGD202608230001-4957', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', NULL, 'IN_PRODUCTION', NULL, NULL, 'BATCH', '2026-08-23', NULL, 103, 1, '2026-08-23 00:29:45', 1, '2026-08-23 00:29:45', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000101, '000000', 'SNQCTEST0001', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000102, '000000', 'SNQCTEST0002', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000103, '000000', 'SNQCTEST0003', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000104, '000000', 'SNQCTEST0004', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000105, '000000', 'SNQCTEST0005', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000106, '000000', 'SNQCFLASH0001', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000022, 'FC-QCTEST-F001', 1001, 'IF20251110001', '自行车', 10, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000107, '000000', 'SNQCFLASH0002', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000022, 'FC-QCTEST-F001', 1001, 'IF20251110001', '自行车', 10, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000108, '000000', 'SNQCFLASH0003', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000022, 'FC-QCTEST-F001', 1001, 'IF20251110001', '自行车', 10, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000201, '000000', 'SNQCCOMP0001', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000202, '000000', 'SNQCCOMP0002', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000203, '000000', 'SNQCCOMP0003', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000204, '000000', 'SNQCCOMP0004', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000205, '000000', 'SNQCCOMP0005', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);
INSERT INTO `bak_20260914_pro_sn` VALUES (2099000000000000206, '000000', 'SNQCCOMP0006', 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001', 1001, 'IF20251110001', '自行车', 30, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-08-22', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL, NULL);

-- ----------------------------
-- Table structure for bak_20260914_pro_step_check_rule
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_step_check_rule`;
CREATE TABLE `bak_20260914_pro_step_check_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `check_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '校验类型',
  `check_stage` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '校验阶段: ENTRY/EXIT',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(0停用 1启用)',
  `error_msg` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '校验失败提示',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_pro_step_guide
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_step_guide`;
CREATE TABLE `bak_20260914_pro_step_guide`  (
  `guide_id` bigint(20) NOT NULL COMMENT '指引ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID(ERP bd_process.process_id)',
  `guide_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指引类型: TEXT/SOP_LINK',
  `guide_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指引标题',
  `guide_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '文本内容(TEXT类型用)',
  `sop_id` bigint(20) NULL DEFAULT NULL COMMENT '关联SOP文档(SOP_LINK类型用)',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(0停用 1启用)',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_pro_work_order
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_work_order`;
CREATE TABLE `bak_20260914_pro_work_order`  (
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `erp_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联ERP工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'MES工单编号(继承或派生)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品名称(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位ID',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `plan_start_date` datetime(0) NULL DEFAULT NULL COMMENT '计划开工日期',
  `plan_end_date` datetime(0) NULL DEFAULT NULL COMMENT '计划完工日期',
  `actual_start_date` datetime(0) NULL DEFAULT NULL COMMENT '实际开工时间',
  `actual_end_date` datetime(0) NULL DEFAULT NULL COMMENT '实际完工时间',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称(冗余)',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联设备ID',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺路线ID(MES侧维护)',
  `wo_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CREATED' COMMENT '工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED',
  `priority` int(4) NULL DEFAULT 5 COMMENT '优先级(1最高,9最低)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ERP' COMMENT '工单来源(ERP/REWORK/MANUAL)',
  `release_package_id` bigint(20) NULL DEFAULT NULL COMMENT '发布包ID(mes_release_package, 工单按此包生成)',
  `release_package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布包编码(冗余)',
  `release_package_ver` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布包版本(冗余快照)',
  `release_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布包配置项快照(JSON数组, 供工位/报表消费)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_work_order
-- ----------------------------
INSERT INTO `bak_20260914_pro_work_order` VALUES (1, '000000', 10001, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 100.0000, 0.0000, 0.0000, 26, '件', '2026-06-10 08:00:00', '2026-07-05 17:00:00', '2026-07-27 23:14:00', NULL, 1, '注塑车间#1', 5, NULL, 1, 'IN_PROGRESS', 2, 'MES工单:自行车100辆装配', NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-07-27 23:13:59', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2068925615991463937, '000000', 2068920326621814786, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', 100.0000, 50.0000, 0.0000, 26, '件', '2026-06-22 00:00:00', '2026-06-29 00:00:00', '2026-06-24 14:44:22', '2026-06-24 15:24:57', 1, '注塑车间#1', 5, NULL, 1, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-24 16:07:12', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2074134800092008450, '000000', 2073979191344271361, 'SCGD202607060001', 8003, 'RACK-001', '简易金属工具架', 10.0000, 10.0000, 0.0000, 27, '套', '2026-07-03 00:00:00', '2026-07-31 00:00:00', '2026-07-06 23:25:49', '2026-07-06 23:28:12', 3, '装配车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 23:28:11', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2074394214269894658, '000000', 2074393703705657346, 'SCGD202607070001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 2.0000, 0.0000, 27, '套', '2026-07-07 15:22:54', '2026-07-08 00:00:00', '2026-07-07 15:29:55', '2026-07-07 16:15:23', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 16:15:23', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2074766761268252674, '000000', 2074766662588862465, 'SCGD202607080001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-08 00:00:00', '2026-07-09 00:00:00', '2026-07-08 16:08:19', '2026-07-08 16:08:52', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:08:52', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2074787137209667586, '000000', 2074786959987740674, 'SCGD202607080002', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-08 00:00:00', '2026-07-09 00:00:00', '2026-07-08 17:30:37', '2026-07-08 17:35:13', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:35:13', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2075397661844770817, '000000', 2075397566059450369, 'SCGD202607100001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-10 00:00:00', '2026-07-11 00:00:00', '2026-07-10 10:10:59', '2026-07-10 10:11:41', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 10:11:41', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2080270525816516609, '000000', 2080231171726180353, 'SCGD202607230001', 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 20.0000, 0.0000, 26, '件', '2026-07-23 00:00:00', '2026-07-24 00:00:00', '2026-07-23 22:32:34', '2026-07-23 22:33:00', 4, '包装车间#1', 99001, NULL, 99001, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 22:33:00', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2082127796938907650, '000000', NULL, 'RW20260728001', 1001, 'IF20251110001', '自行车', 5.0000, 0.0000, 0.0000, 26, '件', NULL, NULL, '2026-07-28 23:35:26', NULL, 1, '注塑车间#1', 5, NULL, 1, 'IN_PROGRESS', 5, 'NCR返工: E2E返工', NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0', 'REWORK', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2082128180742889473, '000000', NULL, 'RW20260728003', 1001, 'IF20251110001', '自行车', 5.0000, 5.0000, 0.0000, 26, '件', NULL, NULL, '2026-07-28 23:36:57', '2026-07-28 23:36:58', 1, '注塑车间#1', 5, NULL, 1, 'COMPLETED', 5, 'NCR返工: E2E返工', NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', 'REWORK', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089713975506821122, '000000', 2089704568974753794, 'SCGD202608180001', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 10.0000, 0.0000, 26, '件', '2026-08-18 00:00:00', '2026-08-20 00:00:00', '2026-08-18 22:01:00', '2026-08-18 23:29:10', NULL, NULL, 2089376872235282434, NULL, 2089698804193546241, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-08-18 22:00:11', -1, '2026-08-18 23:39:38', '0', 'ERP', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089742340792041473, '000000', NULL, 'SCGD202608180002', 2089376872126230500, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 0.0000, 0.0000, 26, '件', NULL, NULL, NULL, NULL, NULL, NULL, 2089376872235282400, NULL, 2089698804193546200, 'CANCELLED', 5, '刷写工单部分完工验证', NULL, 103, 1, '2026-08-18 23:52:54', 1, '2026-08-18 23:55:21', '0', 'MANUAL', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089743106437070849, '000000', NULL, 'SCGD202608180002V6776', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 10.0000, 0.0000, 26, '件', NULL, NULL, '2026-08-18 23:55:57', '2026-08-18 23:55:57', NULL, NULL, 2089376872235282434, NULL, 2089698804193546241, 'COMPLETED', 5, '刷写工单部分完工验证', NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', 'MANUAL', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089743634013405186, '000000', NULL, 'SCGD202608180002V2560', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 10.0000, 0.0000, 26, '件', NULL, NULL, '2026-08-18 23:58:03', '2026-08-18 23:58:03', NULL, NULL, 2089376872235282434, NULL, 2089698804193546241, 'COMPLETED', 5, '刷写工单部分完工验证', NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', 'MANUAL', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089745255220953090, '000000', NULL, 'SCGD202608190001V9085', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 10.0000, 0.0000, 26, '件', NULL, NULL, '2026-08-19 00:04:29', '2026-08-19 00:04:30', NULL, NULL, 2089376872235282434, NULL, 2089698804193546241, 'COMPLETED', 5, '刷写工单部分完工验证', NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', 'MANUAL', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2089745699615850497, '000000', NULL, 'SCGD202608190001V5037', 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 10.0000, 10.0000, 0.0000, 26, '件', NULL, NULL, '2026-08-19 00:06:15', '2026-08-19 00:06:16', NULL, NULL, 2089376872235282434, NULL, 2089698804193546241, 'COMPLETED', 5, '刷写工单部分完工验证', NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', 'MANUAL', NULL, NULL, NULL, NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2091201155861360641, '000000', 2091201154888282115, 'SCGD202608230001', 2091199708184752129, 'IF20260823001', '电动牙刷 TB-100', 300.0000, 0.0000, 0.0000, 26, '件', '2026-08-23 00:29:43', NULL, '2026-08-23 00:29:45', NULL, NULL, NULL, 2091200578037264385, NULL, 2091200577588473858, 'IN_PROGRESS', 5, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:45', '0', 'ERP', 2091201152438808578, 'RP20260823001', '1.0', NULL);
INSERT INTO `bak_20260914_pro_work_order` VALUES (2099000000000000001, '000000', NULL, 'WO-QCTEST-001', 1001, 'IF20251110001', '自行车', 10.0000, 0.0000, 0.0000, 26, '个', '2026-08-22 00:00:00', '2026-08-29 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'IN_PROGRESS', 5, '工位机QC测试工位专用工单', NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', 'MANUAL', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for bak_20260914_pro_work_order_bom_line
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_work_order_bom_line`;
CREATE TABLE `bak_20260914_pro_work_order_bom_line`  (
  `wo_bom_line_id` bigint(20) NOT NULL COMMENT '快照明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `mbom_line_id` bigint(20) NULL DEFAULT NULL COMMENT '源mes_mbom_line.mbom_line_id(可空,无MBOM时)',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP BOM ID',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP明细ID(溯源)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `total_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '含损耗用量 = qty × (1 + loss/100)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位编码(冗余)',
  `workstation_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位名称(冗余)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_work_order_bom_line
-- ----------------------------
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2068925616163430402, '000000', 2068925615991463937, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2068925616201179138, '000000', 2068925615991463937, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2068925616213762050, '000000', 2068925615991463937, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2068925616230539266, '000000', 2068925615991463937, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074134800150728709, '000000', 2074134800092008450, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074134800213643265, '000000', 2074134800092008450, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074134800213643266, '000000', 2074134800092008450, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074394214395723777, '000000', 2074394214269894658, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074394214429278209, '000000', 2074394214269894658, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074394214446055426, '000000', 2074394214269894658, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074766761406664705, '000000', 2074766761268252674, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074766761477967873, '000000', 2074766761268252674, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074766761494745089, '000000', 2074766761268252674, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074787137335496705, '000000', 2074787137209667586, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074787137377439745, '000000', 2074787137209667586, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2074787137398411265, '000000', 2074787137209667586, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2075397661995765761, '000000', 2075397661844770817, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2075397662067068930, '000000', 2075397661844770817, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2075397662088040450, '000000', 2075397661844770817, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082127797987483650, '000000', 2082127796938907650, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082127797995872258, '000000', 2082127796938907650, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082127798004260866, '000000', 2082127796938907650, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082127798016843777, '000000', 2082127796938907650, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082128181405589505, '000000', 2082128180742889473, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082128181422366722, '000000', 2082128180742889473, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082128181434949633, '000000', 2082128180742889473, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2082128181443338242, '000000', 2082128180742889473, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089704730375766020, '000000', 2089704730308657153, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, -1, -1, '2026-08-18 21:23:27', -1, '2026-08-18 21:23:27', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089710626950725634, '000000', 2089710626887811073, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, -1, -1, '2026-08-18 21:46:53', -1, '2026-08-18 21:46:53', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089713975506821124, '000000', 2089713975506821122, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, -1, -1, '2026-08-18 22:00:11', -1, '2026-08-18 22:00:11', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089743106684534786, '000000', 2089743106437070849, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089743634244091905, '000000', 2089743634013405186, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089745255413891073, '000000', 2089745255220953090, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2089745699812982785, '000000', 2089745699615850497, 2089701818929004545, 2089376872235282434, NULL, 2089376872126230529, 'IF20260817011', '智能网关PCBA GW-1000', 1.000000, 0.0000, 1.000000, 2089698804256460801, 1, 90002001, 'FLASH', '固件刷写', 3, 'GZZ0004', '工作站3', '刷写', 0, 1, NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155924275207, '000000', 2091201155861360641, 2091200578813210627, 2091200578037264385, 2091200578360225794, 2091200054030282754, 'IF20260823002', '主控板 PCBA-TB100', 1.000000, 0.0000, 1.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 1, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155987189762, '000000', 2091201155861360641, 2091200578813210628, 2091200578037264385, 2091200578427334658, 2091200054223220737, 'IF20260823003', '无刷马达 M06', 1.000000, 0.0000, 1.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 2, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155987189763, '000000', 2091201155861360641, 2091200578880319489, 2091200578037264385, 2091200578427334659, 2091200054416158722, 'IF20260823004', '锂电池 700mAh', 1.000000, 0.0000, 1.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 3, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155987189764, '000000', 2091201155861360641, 2091200578880319490, 2091200578037264385, 2091200578427334660, 2091200054672011266, 'IF20260823005', '手柄外壳 TB-SHELL', 1.000000, 0.0000, 1.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 4, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155987189765, '000000', 2091201155861360641, 2091200578880319491, 2091200578037264385, 2091200578427334661, 2091200055313739778, 'IF20260823008', '密封圈', 2.000000, 0.0000, 2.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 5, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201155987189766, '000000', 2091201155861360641, 2091200578880319492, 2091200578037264385, 2091200578427334662, 2091200055502483457, 'IF20260823009', '螺丝 M2×6', 4.000000, 0.0000, 4.000000, 2091200577651388417, 1, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 'TB装配工位A', '', 0, 6, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201156050104322, '000000', 2091201155861360641, 2091200578880319493, 2091200578037264385, 2091200578427334663, 2091200054864949250, 'IF20260823006', '刷头 BR-H', 2.000000, 0.0000, 2.000000, 2091200577651388422, 6, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 'TB包装工位A', '', 0, 7, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201156050104323, '000000', 2091201155861360641, 2091200578880319494, 2091200578037264385, 2091200578427334664, 2091200055120801794, 'IF20260823007', '充电底座 CD-TB', 1.000000, 0.0000, 1.000000, 2091200577651388422, 6, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 'TB包装工位A', '', 0, 8, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201156050104324, '000000', 2091201155861360641, 2091200578943234049, 2091200578037264385, 2091200578427334665, 2091200055695421442, 'IF20260823010', '彩盒 TB-BOX', 1.000000, 0.0000, 1.000000, 2091200577651388422, 6, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 'TB包装工位A', '', 0, 9, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2091201156050104325, '000000', 2091201155861360641, 2091200578943234050, 2091200578037264385, 2091200578427334666, 2091200055825444865, 'IF20260823011', '说明书', 1.000000, 0.0000, 1.000000, 2091200577651388422, 6, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 'TB包装工位A', '', 0, 10, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2099000000000000301, '000000', 2099000000000000001, NULL, NULL, 2099000000000000631, 7, 'BOLT_M12', '螺栓M12', 2.000000, 0.0000, 20.000000, NULL, 1, 4, 'QC', '质检', 16, 'GZZ0017', '质检站', NULL, 0, 1, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');
INSERT INTO `bak_20260914_pro_work_order_bom_line` VALUES (2099000000000000302, '000000', 2099000000000000001, NULL, NULL, NULL, 6, 'HOUSING', '箱体', 1.000000, 0.0000, 10.000000, NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 2, NULL, -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0');

-- ----------------------------
-- Table structure for bak_20260914_pro_work_order_step
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_pro_work_order_step`;
CREATE TABLE `bak_20260914_pro_work_order_step`  (
  `step_id` bigint(20) NOT NULL COMMENT '工序步骤ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '所属MES工单ID',
  `sequence_num` int(4) NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
  `step_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '工序类型: GENERAL/FLASH/TEST',
  `content_ref_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容引用类型(单类型快捷绑定: FIRMWARE/CONFIG/MAC_POOL...)',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件类=mes_firmware_id)',
  `step_flash_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '多类型绑定JSON: [{\"flashTypeCode\":\"firmware_file\",\"contentRefId\":123},{\"flashTypeCode\":\"mac\"}]',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP工序ID(bd_routing_step.routing_step_id)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `step_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  `firmware_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件来源: PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_pro_work_order_step
-- ----------------------------
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (1, '000000', 1, 10, 'FLASH', 'FIRMWARE', 2091000000000006101, '[{\"flashTypeCode\":\"firmware_file\",\"contentRefId\":2091000000000006101},{\"flashTypeCode\":\"mac\"}]', 1, 'CUT', '切割', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, '步1:钢板切割', 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-17 22:16:17', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2, '000000', 1, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 100.0000, 0.0000, 0.0000, 'PENDING', 1, NULL, NULL, '步2:车架焊接(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (3, '000000', 1, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 1, NULL, NULL, '步3:整车装配(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-08-16 17:53:11', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (4, '000000', 1, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, '步4:成品包装', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2068925616075350017, '000000', 2068925615991463937, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2068925616113098753, '000000', 2068925615991463937, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2068925616125681666, '000000', 2068925615991463937, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2068925616142458881, '000000', 2068925615991463937, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074134800092008451, '000000', 2074134800092008450, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074134800150728706, '000000', 2074134800092008450, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074134800150728707, '000000', 2074134800092008450, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074134800150728708, '000000', 2074134800092008450, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074394214299254786, '000000', 2074394214269894658, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074394214341197826, '000000', 2074394214269894658, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074394214353780737, '000000', 2074394214269894658, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074394214366363650, '000000', 2074394214269894658, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074766761289224194, '000000', 2074766761268252674, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074766761360527361, '000000', 2074766761268252674, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074766761373110274, '000000', 2074766761268252674, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074766761389887489, '000000', 2074766761268252674, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074787137239027713, '000000', 2074787137209667586, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074787137280970754, '000000', 2074787137209667586, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074787137297747969, '000000', 2074787137209667586, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2074787137310330881, '000000', 2074787137209667586, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2075397661861548033, '000000', 2075397661844770817, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2075397661953822721, '000000', 2075397661844770817, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2075397661970599938, '000000', 2075397661844770817, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2075397661983182849, '000000', 2075397661844770817, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2080270525816516611, '000000', 2080270525816516609, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 2, 'GZZ0003', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2080270525816516612, '000000', 2080270525816516609, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 6, 'GZZ0007', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2080270525816516613, '000000', 2080270525816516609, 30, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 9, 'GZZ0010', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082127797928763393, '000000', 2082127796938907650, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082127797941346305, '000000', 2082127796938907650, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082127797953929218, '000000', 2082127796938907650, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082127797962317825, '000000', 2082127796938907650, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082128181367840770, '000000', 2082128180742889473, 10, 'GENERAL', NULL, NULL, NULL, 1, 'CUT', '切割', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082128181376229377, '000000', 2082128180742889473, 20, 'GENERAL', NULL, NULL, NULL, 2, 'WELD', '焊接', 2, 'GZZ0003', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082128181380423681, '000000', 2082128180742889473, 30, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2082128181397200897, '000000', 2082128180742889473, 40, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 3, 'GZZ0004', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089704730375766019, '000000', 2089704730308657153, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-18 21:23:27', -1, '2026-08-18 21:23:27', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089710626887811074, '000000', 2089710626887811073, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-18 21:46:53', -1, '2026-08-18 21:46:53', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089713975506821123, '000000', 2089713975506821122, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-18 22:00:11', -1, '2026-08-18 22:00:11', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089743106671951873, '000000', 2089743106437070849, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-08-18 23:55:57', 1, '2026-08-18 23:55:57', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089743634231508993, '000000', 2089743634013405186, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-08-18 23:58:03', 1, '2026-08-18 23:58:03', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089745255401308162, '000000', 2089745255220953090, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-08-19 00:04:29', 1, '2026-08-19 00:04:29', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2089745699808788482, '000000', 2089745699615850497, 1, 'FLASH', 'FIRMWARE', 2089376872356917250, NULL, 90002001, 'FLASH', '固件刷写', 3, '', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-08-19 00:06:15', 1, '2026-08-19 00:06:15', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155861360642, '000000', 2091201155861360641, 1, 'GENERAL', NULL, NULL, NULL, 3, 'ASM', '装配', 2091200056404258818, 'GZZ0001', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155924275202, '000000', 2091201155861360641, 2, 'FLASH', 'FIRMWARE', 2091200577269706754, NULL, 90002001, 'FLASH', '固件刷写', 2091200575474544642, 'GZZ0018', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', 'PACKAGE');
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155924275203, '000000', 2091201155861360641, 3, 'TEST', NULL, NULL, NULL, 2091200576372125698, 'FUNC_TEST', '整机功能测试', 2091200575604568065, 'GZZ0019', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155924275204, '000000', 2091201155861360641, 4, 'TEST', NULL, NULL, NULL, 2091200576565063681, 'WP_TEST', '防水气密测试', 2091200575604568065, 'GZZ0019', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155924275205, '000000', 2091201155861360641, 5, 'GENERAL', NULL, NULL, NULL, 4, 'QC', '质检', 2091200575793311745, 'GZZ0020', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2091201155924275206, '000000', 2091201155861360641, 6, 'GENERAL', NULL, NULL, NULL, 5, 'PKG', '包装', 2091200575986249730, 'GZZ0021', 300.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-08-23 00:29:43', -1, '2026-08-23 00:29:43', '0', NULL);
INSERT INTO `bak_20260914_pro_work_order_step` VALUES (2099000000000000011, '000000', 2099000000000000001, 10, 'FLASH', 'FIRMWARE', 2091000000000006101, NULL, 90002001, 'FLASH', '固件烧录', NULL, NULL, 3.0000, 0.0000, 0.0000, 'PENDING', 1, NULL, NULL, '测试固件 FW20260817001', -1, -1, '2026-08-22 19:43:49', NULL, '2026-08-22 19:43:49', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_qc_inspect_detail
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_inspect_detail`;
CREATE TABLE `bak_20260914_qc_inspect_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '检验明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `check_id` bigint(20) NOT NULL COMMENT '检验单ID(qc_iqc.iqc_id/qc_ipqc.ipqc_id/qc_oqc.oqc_id)',
  `check_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验类别: IQC/IPQC/OQC',
  `template_id` bigint(20) NULL DEFAULT NULL COMMENT '来源模板ID(快照溯源)',
  `item_id` bigint(20) NULL DEFAULT NULL COMMENT '来源检验项目ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称(快照)',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型(快照): QUANTITATIVE/QUALITATIVE',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型(快照): NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL',
  `standard_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准快照描述(如10±0.05mm)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(快照)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度(快照): CRITICAL/MAJOR/MINOR',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '样本量',
  `measured_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测值(定量:数值/多值; 定性:OK/NG等)',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(快照, 判定用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下限(快照, 判定用)',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上限(快照, 判定用)',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(快照, 判定用)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(快照, 判定用)',
  `judge_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '判定结果: PASS/FAIL/PENDING',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_inspect_detail
-- ----------------------------
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100001, '000000', 2082124523750199298, 'IQC', 947001, 947011, 'THK-001', '板材厚度', 'QUANTITATIVE', 'LSL_USL', '大于等于2.900000且小于等于3.100000 mm', 'mm', 'CRITICAL', NULL, NULL, NULL, 2.900000, 3.100000, NULL, NULL, 'PENDING', 1, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100002, '000000', 2082124523750199298, 'IQC', 947001, 947012, 'TS-001', '抗拉强度', 'QUANTITATIVE', 'LSL_ONLY', '大于等于310.000000 MPa', 'MPa', 'MAJOR', NULL, NULL, NULL, 310.000000, NULL, NULL, NULL, 'PENDING', 2, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100003, '000000', 2082125048956751873, 'IQC', 947001, 947011, 'THK-001', '板材厚度', 'QUANTITATIVE', 'LSL_USL', '大于等于2.900000且小于等于3.100000 mm', 'mm', 'CRITICAL', NULL, NULL, NULL, 2.900000, 3.100000, NULL, NULL, 'PENDING', 1, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100004, '000000', 2082125048956751873, 'IQC', 947001, 947012, 'TS-001', '抗拉强度', 'QUANTITATIVE', 'LSL_ONLY', '大于等于310.000000 MPa', 'MPa', 'MAJOR', NULL, NULL, NULL, 310.000000, NULL, NULL, NULL, 'PENDING', 2, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100005, '000000', 2082127795194077186, 'IQC', 947001, 947011, 'THK-001', '板材厚度', 'QUANTITATIVE', 'LSL_USL', '大于等于2.900000且小于等于3.100000 mm', 'mm', 'CRITICAL', NULL, NULL, NULL, 2.900000, 3.100000, NULL, NULL, 'PENDING', 1, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_detail` VALUES (947100006, '000000', 2082127795194077186, 'IQC', 947001, 947012, 'TS-001', '抗拉强度', 'QUANTITATIVE', 'LSL_ONLY', '大于等于310.000000 MPa', 'MPa', 'MAJOR', NULL, NULL, NULL, 310.000000, NULL, NULL, NULL, 'PENDING', 2, NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_qc_inspect_item
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_inspect_item`;
CREATE TABLE `bak_20260914_qc_inspect_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '检验项目ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_id` bigint(20) NOT NULL COMMENT '关联qc_inspect_template.template_id',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型: QUANTITATIVE定量/QUALITATIVE定性',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计量单位',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型: NOMINAL名义值/LSL_ONLY/USL_ONLY/LSL_USL',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(NOMINAL用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下规格限LSL',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上规格限USL',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(NOMINAL用, 名义+tolUpper)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(NOMINAL用, 名义-tolLower)',
  `aql_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验水平(如II)',
  `sample_plan_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '抽样方案(如AQL1.0)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度: CRITICAL关键(一票否决)/MAJOR/MINOR',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '是否启用 1是0否',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_inspect_item
-- ----------------------------
INSERT INTO `bak_20260914_qc_inspect_item` VALUES (947011, '000000', 947001, 'THK-001', '板材厚度', 'QUANTITATIVE', 'mm', 'LSL_USL', NULL, 2.900000, 3.100000, NULL, NULL, 'II', 'AQL1.0', 'CRITICAL', 1, '1', '关键尺寸, 一票否决', 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');
INSERT INTO `bak_20260914_qc_inspect_item` VALUES (947012, '000000', 947001, 'TS-001', '抗拉强度', 'QUANTITATIVE', 'MPa', 'LSL_ONLY', NULL, 310.000000, NULL, NULL, NULL, 'II', 'AQL1.0', 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_qc_inspect_template
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_inspect_template`;
CREATE TABLE `bak_20260914_qc_inspect_template`  (
  `template_id` bigint(20) NOT NULL COMMENT '检验模板ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码(唯一)',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `template_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板类型: IQC/IPQC/OQC',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID(空=通用模板)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '版本号',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_inspect_template
-- ----------------------------
INSERT INTO `bak_20260914_qc_inspect_template` VALUES (947001, '000000', 'IQC-TPL-1001', '铝合金板材来料检验模板', 'IQC', 1001, 'MAT-1001', '铝合金板材', 1, 'ACTIVE', '演示: 2个定量项目(厚度LSL_USL/抗拉强度LSL_ONLY)', 103, 1, '2026-09-02 19:32:44', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_qc_ipqc
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_ipqc`;
CREATE TABLE `bak_20260914_qc_ipqc`  (
  `ipqc_id` bigint(20) NOT NULL COMMENT 'IPQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `ipqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IPQC编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `defect_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '不良数量',
  `defect_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '不良率(%)',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_ipqc
-- ----------------------------
INSERT INTO `bak_20260914_qc_ipqc` VALUES (1, '000000', 'IPQC-001', 1, 'WO-TEST-001', 1, 1, 1, 'CUT', '切割', 1001, 'IF20251110001', '自行车', 10.0000, 1.0000, 10.00, 'QUALIFIED', '[{\"code\": \"P001\", \"name\": \"尺寸偏差\", \"count\": 1, \"severity\": \"minor\"}]', 30005, '钱七', '2026-06-10 16:30:00', 'COMPLETED', '切割工序IPQC:10抽1不良(尺寸偏差)，合格', 100, 1, NULL, NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_qc_ipqc` VALUES (2081760017509511170, '000000', 'IPQC-20260727-001', 1, 'WO-TEST-001', 1, 3, 3, 'ASM', '装配', 1001, 'IF20251110001', '自行车', NULL, 0.0000, NULL, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-27 23:14:00', 1, '2026-07-27 23:14:00', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_qc_iqc
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_iqc`;
CREATE TABLE `bak_20260914_qc_iqc`  (
  `iqc_id` bigint(20) NOT NULL COMMENT 'IQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `iqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IQC编号',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP收货单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_iqc
-- ----------------------------
INSERT INTO `bak_20260914_qc_iqc` VALUES (1, '000000', 'IQC-001', 10001, 10, 'LUBRICANT', '润滑油', 2062098763051089924, '天津化工材料有限公司', 200.0000, 10.0000, 'QUALIFIED', '[{\"code\": \"Q001\", \"name\": \"粘度\", \"result\": \"合格\"}, {\"code\": \"Q002\", \"name\": \"闪点\", \"result\": \"合格\"}]', 30005, '钱七', '2026-06-10 15:00:00', 'COMPLETED', '润滑油来料检验合格', 100, 1, NULL, NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_qc_iqc` VALUES (2080291255916593154, '000000', 'IQC-20260723-001', 2080273188016762882, 99002, 'QC-RM-A', '质检测试原料A', 2062098763051089923, '深圳精密电机制造厂', 200.0000, 1.0000, 'QUALIFIED', '{}', 1, 'admin', '2026-07-23 22:26:06', 'COMPLETED', NULL, -1, -1, '2026-07-23 21:57:40', -1, '2026-07-23 21:57:40', '0', NULL);
INSERT INTO `bak_20260914_qc_iqc` VALUES (2082124523750199298, '000000', 'IQC-20260728-001', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:22:25', 1, '2026-07-28 23:22:25', '0', NULL);
INSERT INTO `bak_20260914_qc_iqc` VALUES (2082125048956751873, '000000', 'IQC-20260728-002', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:24:30', 1, '2026-07-28 23:24:30', '0', NULL);
INSERT INTO `bak_20260914_qc_iqc` VALUES (2082127795194077186, '000000', 'IQC-20260728-003', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', '0', NULL);
INSERT INTO `bak_20260914_qc_iqc` VALUES (2082128179698507778, '000000', 'IQC-20260728-004', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'UNQUALIFIED', '[{\"qty\": 3, \"code\": \"SCRATCH\", \"desc\": \"表面划痕\"}, {\"qty\": 1, \"code\": \"DENT\", \"desc\": \"凹痕\"}]', 1, 'admin', '2026-07-28 23:36:57', 'COMPLETED', 'E2E不合格', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_qc_oqc
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_qc_oqc`;
CREATE TABLE `bak_20260914_qc_oqc`  (
  `oqc_id` bigint(20) NOT NULL COMMENT 'OQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `oqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OQC编号',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP销售订单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_qc_oqc
-- ----------------------------
INSERT INTO `bak_20260914_qc_oqc` VALUES (1, '000000', 'OQC-001', 10001, 1001, 'IF20251110001', '自行车', 50.0000, 5.0000, 'PENDING', NULL, 30005, '钱七', NULL, 'PENDING', 'OQC待检:出货前抽查5辆', 100, 1, NULL, NULL, NULL, '0', NULL);
INSERT INTO `bak_20260914_qc_oqc` VALUES (2080312885934026753, '000000', 'OQC-20260723-001', 2080229404229697537, 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 1.0000, 'QUALIFIED', '{}', 1, 'admin', '2026-07-23 23:24:21', 'COMPLETED', NULL, -1, -1, '2026-07-23 23:23:37', -1, '2026-07-23 23:23:37', '0', NULL);

-- ----------------------------
-- Table structure for bak_20260914_sop_document
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_sop_document`;
CREATE TABLE `bak_20260914_sop_document`  (
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SOP编码',
  `sop_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SOP名称',
  `sop_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN',
  `current_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '当前发布版本号',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED',
  `file_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '当前发布文件OSS ID(私有桶存ID)',
  `file_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件类型: PDF/IMAGE/VIDEO/WORD',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `approval_by` bigint(20) NULL DEFAULT NULL COMMENT '审批人ID',
  `approval_time` datetime(0) NULL DEFAULT NULL COMMENT '审批时间',
  `approval_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审批意见',
  `archive_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '归档原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_sop_document
-- ----------------------------
INSERT INTO `bak_20260914_sop_document` VALUES (2081355255391621121, '000000', 'SOP-E2E-A', 'E2E Test SOP A', 'PRODUCTION', '1.1', 'ARCHIVED', 2081355254821195778, 'PDF', 'E2E test', 1, '2026-07-26 20:25:39', 'v1.1-approve', 'E2E-archive', NULL, 103, 1, '2026-07-26 20:25:37', 1, '2026-07-26 20:25:41', '1');
INSERT INTO `bak_20260914_sop_document` VALUES (2081357146414551042, '000000', 'SOP20260726001', '测试sop', 'PRODUCTION', '1.0', 'ARCHIVED', 2081357120791547906, 'WORD', 'cs', 1, '2026-07-26 20:33:57', NULL, NULL, '', 103, 1, '2026-07-26 20:33:08', 1, '2026-07-26 20:33:08', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387393067200514, '000000', 'SOP20260823001', 'TB100 整机装配作业指导书', 'PRODUCTION', '1.0', 'PUBLISHED', 2091387392878456834, 'WORD', '电动牙刷整机装配: PCBA/马达/电池/外壳组装与螺丝锁付', 1, '2026-08-23 12:49:45', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:45', 1, '2026-08-23 12:49:45', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387394098999298, '000000', 'SOP20260823002', 'TB100 主控固件烧录作业指导书', 'PRODUCTION', '1.0', 'PUBLISHED', 2091387394031890433, 'WORD', '主控固件烧录(刷写工序): 系统按发布包自动绑定固件版本, 烧录后校验', 1, '2026-08-23 12:49:46', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387394992386050, '000000', 'SOP20260823003', 'TB100 整机功能测试作业指导书', 'INSPECTION', '1.0', 'PUBLISHED', 2091387394862362626, 'WORD', '整机功能测试: 开机/模式切换/充电检测/指示灯与按键判定', 1, '2026-08-23 12:49:46', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387395827052546, '000000', 'SOP20260823004', 'TB100 防水气密测试作业指导书', 'INSPECTION', '1.0', 'PUBLISHED', 2091387395764137986, 'WORD', '防水气密测试(IPX7): 气密检测仪负压法判定整机密封性', 1, '2026-08-23 12:49:46', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387396733022210, '000000', 'SOP20260823005', 'TB100 成品检验作业指导书', 'INSPECTION', '1.0', 'PUBLISHED', 2091387396602998785, 'WORD', '成品最终检验(关键工序): 外观/功能抽验/标识/包装前确认', 1, '2026-08-23 12:49:46', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2091387397626408961, '000000', 'SOP20260823006', 'TB100 包装作业指导书', 'PRODUCTION', '1.0', 'PUBLISHED', 2091387397500579841, 'WORD', '彩盒包装与外箱: 内盒/说明书/配件齐套, 装箱与堆码', 1, '2026-08-23 12:49:46', '产线SOP发布审批通过', NULL, '电动牙刷全链路', 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2099000000000000501, '000000', 'SOP-QC-TEST-001', 'QC测试工位作业指导书(PDF)', 'PRODUCTION', 'V1.0', 'PUBLISHED', 2081355254821195778, 'PDF', '工位机SOP测试: PDF 在线预览', 1, '2026-08-22 19:43:49', NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, NULL, '0');
INSERT INTO `bak_20260914_sop_document` VALUES (2099000000000000502, '000000', 'SOP-QC-TEST-002', 'QC测试工位作业指导书(图片)', 'PRODUCTION', 'V1.0', 'PUBLISHED', 2083823493513863169, 'IMAGE', '工位机SOP测试: 图片展示', 1, '2026-08-22 19:43:49', NULL, NULL, NULL, -1, -1, '2026-08-22 19:43:49', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_sop_process_binding
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_sop_process_binding`;
CREATE TABLE `bak_20260914_sop_process_binding`  (
  `binding_id` bigint(20) NOT NULL COMMENT '绑定ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID(ERP bd_process.process_id, 工序主数据PK)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `process_category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING)',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指定版本号(空=当前发布版)',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否该工序默认SOP(1/0)',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_sop_process_binding
-- ----------------------------
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2081355265004965890, '000000', 1, 'E2E-PROC', 'E2E Process', 'PROCESSING', 2081355255391621121, NULL, '0', 1, NULL, 103, 1, '2026-07-26 20:25:40', 1, '2026-07-26 20:25:41', '1');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2081355265898352642, '000000', 1, 'E2E-PROC', 'E2E Process', 'PROCESSING', 2081355255391621121, NULL, '1', 2, NULL, 103, 1, '2026-07-26 20:25:40', 1, '2026-07-26 20:25:41', '1');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387393453076481, '000000', 3, 'ASM', '装配', NULL, 2091387393067200514, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:45', 1, '2026-08-23 12:49:45', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387394350657537, '000000', 90002001, 'FLASH', '固件刷写', NULL, 2091387394098999298, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387395248238593, '000000', 2091200576372125698, 'FUNC_TEST', '整机功能测试', NULL, 2091387394992386050, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387396145819650, '000000', 2091200576565063681, 'WP_TEST', '防水气密测试', NULL, 2091387395827052546, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387396984680449, '000000', 4, 'QC', '质检', NULL, 2091387396733022210, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2091387397882261505, '000000', 5, 'PKG', '包装', NULL, 2091387397626408961, NULL, '1', 1, NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2099000000000000511, '000000', 4, 'QC', '质检', NULL, 2099000000000000501, 'V1.0', '0', 1, NULL, -1, -1, '2026-08-22 19:43:49', NULL, NULL, '0');
INSERT INTO `bak_20260914_sop_process_binding` VALUES (2099000000000000512, '000000', 4, 'QC', '质检', NULL, 2099000000000000502, 'V1.0', '0', 2, NULL, -1, -1, '2026-08-22 19:43:49', NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_sop_version
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_sop_version`;
CREATE TABLE `bak_20260914_sop_version`  (
  `version_id` bigint(20) NOT NULL COMMENT '版本ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '版本号(如1.0/1.1)',
  `file_oss_id` bigint(20) NOT NULL COMMENT '该版本文件OSS ID',
  `file_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件类型: PDF/IMAGE/VIDEO/WORD',
  `change_log` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更说明',
  `published_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CURRENT/HISTORICAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_sop_version
-- ----------------------------
INSERT INTO `bak_20260914_sop_version` VALUES (2081355255400009730, '000000', 2081355255391621121, '1.0', 2081355254821195778, 'PDF', 'E2E test', '2026-07-26 20:25:38', 1, 'HISTORICAL', NULL, 103, 1, '2026-07-26 20:25:37', 1, '2026-07-26 20:25:37', '1');
INSERT INTO `bak_20260914_sop_version` VALUES (2081355261779546113, '000000', 2081355255391621121, '1.1', 2081355254821195778, 'PDF', 'v1.1 revision', '2026-07-26 20:25:39', 1, 'CURRENT', NULL, 103, 1, '2026-07-26 20:25:39', 1, '2026-07-26 20:25:39', '1');
INSERT INTO `bak_20260914_sop_version` VALUES (2081357146414551043, '000000', 2081357146414551042, '1.0', 2081357120791547906, 'WORD', 'cs', '2026-07-26 20:33:57', 1, 'CURRENT', NULL, 103, 1, '2026-07-26 20:33:08', 1, '2026-07-26 20:33:08', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387393067200515, '000000', 2091387393067200514, '1.0', 2091387392878456834, 'WORD', '电动牙刷整机装配: PCBA/马达/电池/外壳组装与螺丝锁付', '2026-08-23 12:49:45', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:45', 1, '2026-08-23 12:49:45', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387394098999299, '000000', 2091387394098999298, '1.0', 2091387394031890433, 'WORD', '主控固件烧录(刷写工序): 系统按发布包自动绑定固件版本, 烧录后校验', '2026-08-23 12:49:46', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387394992386051, '000000', 2091387394992386050, '1.0', 2091387394862362626, 'WORD', '整机功能测试: 开机/模式切换/充电检测/指示灯与按键判定', '2026-08-23 12:49:46', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387395889967106, '000000', 2091387395827052546, '1.0', 2091387395764137986, 'WORD', '防水气密测试(IPX7): 气密检测仪负压法判定整机密封性', '2026-08-23 12:49:46', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387396733022211, '000000', 2091387396733022210, '1.0', 2091387396602998785, 'WORD', '成品最终检验(关键工序): 外观/功能抽验/标识/包装前确认', '2026-08-23 12:49:46', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');
INSERT INTO `bak_20260914_sop_version` VALUES (2091387397626408962, '000000', 2091387397626408961, '1.0', 2091387397500579841, 'WORD', '彩盒包装与外箱: 内盒/说明书/配件齐套, 装箱与堆码', '2026-08-23 12:49:46', 1, 'CURRENT', NULL, 103, 1, '2026-08-23 12:49:46', 1, '2026-08-23 12:49:46', '0');

-- ----------------------------
-- Table structure for bak_20260914_sop_view_log
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_sop_view_log`;
CREATE TABLE `bak_20260914_sop_view_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '查阅记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查阅的版本号',
  `viewer_id` bigint(20) NOT NULL COMMENT '查阅人ID',
  `viewer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查阅人姓名(冗余)',
  `view_time` datetime(0) NOT NULL COMMENT '查阅时间',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `signature_id` bigint(20) NULL DEFAULT NULL COMMENT '电子签名记录ID(预留,P0-6接入)',
  `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_20260914_spc_control_chart
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_spc_control_chart`;
CREATE TABLE `bak_20260914_spc_control_chart`  (
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `chart_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '控制图名称',
  `chart_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
  `point_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采集点ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '关联物料ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `ucl` decimal(14, 4) NULL DEFAULT NULL COMMENT '上控制限(analyze计算回写)',
  `cl` decimal(14, 4) NULL DEFAULT NULL COMMENT '中心线',
  `lcl` decimal(14, 4) NULL DEFAULT NULL COMMENT '下控制限',
  `usl` decimal(14, 4) NULL DEFAULT NULL COMMENT '规格上限(USL, 用户维护)',
  `lsl` decimal(14, 4) NULL DEFAULT NULL COMMENT '规格下限(LSL, 用户维护)',
  `cp` decimal(10, 4) NULL DEFAULT NULL COMMENT '过程能力指数Cp(analyze回写)',
  `cpk` decimal(10, 4) NULL DEFAULT NULL COMMENT '过程能力指数Cpk(analyze回写)',
  `cpk_grade` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '能力等级: A+(>=1.67)/A(>=1.33)/B(>=1.0)/C(<1.0)',
  `sample_size` int(11) NULL DEFAULT 5 COMMENT '样本大小',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_spc_control_chart
-- ----------------------------
INSERT INTO `bak_20260914_spc_control_chart` VALUES (1, '000000', 'CNC主轴温度控制图', 'X_BAR_R', 1, 1, 1, 85.2750, 76.6200, 67.9650, NULL, NULL, NULL, NULL, NULL, 5, 'ACTIVE', '数控车床主轴温度SPC监控', 100, 1, NULL, 1, '2026-07-26 02:00:03', '0');
INSERT INTO `bak_20260914_spc_control_chart` VALUES (2, '000000', '装配关键尺寸控制图', 'X_BAR_R', 3, 1001, 3, 10.0500, 10.0000, 9.9500, 10.0500, 9.9500, NULL, NULL, NULL, 5, 'ACTIVE', '装配工序关键尺寸SPC监控(目标10mm)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `bak_20260914_spc_control_chart` VALUES (3, '000000', '焊接温度控制图', 'X_BAR_R', 4, 1, 2, 280.0000, 250.0000, 220.0000, NULL, NULL, NULL, NULL, NULL, 5, 'ACTIVE', '焊接温度SPC监控', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for bak_20260914_spc_data_point
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_spc_data_point`;
CREATE TABLE `bak_20260914_spc_data_point`  (
  `data_id` bigint(20) NOT NULL COMMENT '数据点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID',
  `sample_group` int(11) NOT NULL COMMENT '样本组号',
  `sample_index` int(11) NULL DEFAULT NULL COMMENT '组内序号',
  `value` decimal(14, 4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
  `is_ooc` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '是否超控制限(0/1)',
  `ooc_rule` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '违规规则(RULE1/2/3/4, 多规则逗号拼接)',
  `collect_time` datetime(0) NULL DEFAULT NULL COMMENT '采集时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_spc_data_point
-- ----------------------------
INSERT INTO `bak_20260914_spc_data_point` VALUES (2081077029524107266, '000000', 1, 1, NULL, 76.6200, '0', NULL, '2026-06-10 08:10:00', 103, 1, '2026-07-26 02:00:03', 1, '2026-07-26 02:00:03', '0');

-- ----------------------------
-- Table structure for bak_20260914_tg_maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_tg_maintenance_plan`;
CREATE TABLE `bak_20260914_tg_maintenance_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计划名称',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型: BY_COUNT/BY_HOURS/PERIODIC',
  `trigger_value` int(11) NULL DEFAULT NULL COMMENT '触发阈值(次数/小时/天数)',
  `last_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '上次保养时间',
  `next_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '下次保养时间(PERIODIC)',
  `next_maintain_count` int(11) NULL DEFAULT NULL COMMENT '下次保养次数阈值(BY_COUNT)',
  `next_maintain_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '下次保养时长阈值(BY_HOURS)',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(1/0)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_tg_maintenance_plan
-- ----------------------------
INSERT INTO `bak_20260914_tg_maintenance_plan` VALUES (2081272381422350338, '000000', 2081272377081245697, 'A-Plan-ByCount', 'BY_COUNT', 1000, NULL, NULL, 1000, NULL, '1', NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

-- ----------------------------
-- Table structure for bak_20260914_tg_maintenance_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_tg_maintenance_record`;
CREATE TABLE `bak_20260914_tg_maintenance_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '关联计划ID',
  `maintain_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养类型: PLANNED/REPAIR',
  `maintain_time` datetime(0) NOT NULL COMMENT '保养时间',
  `maintainer_id` bigint(20) NULL DEFAULT NULL COMMENT '保养人ID',
  `maintainer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养人姓名(冗余)',
  `used_count_before` int(11) NULL DEFAULT NULL COMMENT '保养前次数',
  `used_count_after` int(11) NULL DEFAULT NULL COMMENT '保养后次数(归零=0)',
  `used_hours_before` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养前时长',
  `used_hours_after` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养后时长(归零=0)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养内容',
  `cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养成本',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_tg_maintenance_record
-- ----------------------------
INSERT INTO `bak_20260914_tg_maintenance_record` VALUES (2081272385134309378, '000000', 2081272377081245697, NULL, 'PLANNED', '2026-07-26 14:56:19', 1, 'admin', 1100, 0, 2.50, 0.00, 'E2E routine maintain', 200.00, NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

-- ----------------------------
-- Table structure for bak_20260914_tg_tooling
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_tg_tooling`;
CREATE TABLE `bak_20260914_tg_tooling`  (
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工装编码',
  `tooling_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工装名称',
  `tooling_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: MOLD/FIXTURE/CUTTER/GAUGE/OTHER',
  `spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID',
  `workshop_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '车间名称(冗余)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IDLE' COMMENT '状态: IDLE/IN_USE/MAINTENANCE/SCRAPPED',
  `total_life_count` int(11) NULL DEFAULT NULL COMMENT '总寿命(次数)',
  `total_life_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '总寿命(小时)',
  `used_count` int(11) NULL DEFAULT 0 COMMENT '已使用次数',
  `used_hours` decimal(14, 2) NULL DEFAULT 0.00 COMMENT '已使用时长',
  `last_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '上次保养时间',
  `purchase_date` date NULL DEFAULT NULL COMMENT '采购日期',
  `purchase_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '采购成本',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_tg_tooling
-- ----------------------------
INSERT INTO `bak_20260914_tg_tooling` VALUES (2081272377081245697, '000000', 'TG-E2E-A', 'E2E Test Mold A', 'MOLD', NULL, 1, 'Test WS', 'SCRAPPED', 1000, 100.00, 0, 0.00, '2026-07-26 14:56:19', NULL, NULL, 'E2E-Test-Scrap', 103, 1, '2026-07-26 14:56:18', 1, '2026-07-26 14:56:18', '0');
INSERT INTO `bak_20260914_tg_tooling` VALUES (2081272388263260162, '000000', 'TG-E2E-B', 'E2E Test Gauge B', 'GAUGE', NULL, NULL, NULL, 'IDLE', 5000, NULL, 0, 0.00, NULL, NULL, NULL, NULL, 103, 1, '2026-07-26 14:56:20', 1, '2026-07-26 14:56:21', '1');

-- ----------------------------
-- Table structure for bak_20260914_tg_usage_record
-- ----------------------------
DROP TABLE IF EXISTS `bak_20260914_tg_usage_record`;
CREATE TABLE `bak_20260914_tg_usage_record`  (
  `usage_id` bigint(20) NOT NULL COMMENT '使用记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `workstation_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位名称(冗余)',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `checkout_time` datetime(0) NOT NULL COMMENT '领用时间',
  `return_time` datetime(0) NULL DEFAULT NULL COMMENT '归还时间',
  `usage_count` int(11) NULL DEFAULT 1 COMMENT '使用次数',
  `usage_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '使用时长',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'OUT' COMMENT '状态: OUT/RETURNED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bak_20260914_tg_usage_record
-- ----------------------------
INSERT INTO `bak_20260914_tg_usage_record` VALUES (2081272379664936961, '000000', 2081272377081245697, 100, 'WO-E2E-001', 1, 'WS1', 1, 'admin', '2026-07-26 14:56:18', '2026-07-26 14:56:18', 500, 2.50, 'RETURNED', NULL, 103, 1, '2026-07-26 14:56:18', 1, '2026-07-26 14:56:18', '0');
INSERT INTO `bak_20260914_tg_usage_record` VALUES (2081272382944882689, '000000', 2081272377081245697, NULL, NULL, NULL, NULL, 1, 'admin', '2026-07-26 14:56:19', '2026-07-26 14:56:19', 600, NULL, 'RETURNED', NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

-- ----------------------------
-- Table structure for dc_collection_point
-- ----------------------------
DROP TABLE IF EXISTS `dc_collection_point`;
CREATE TABLE `dc_collection_point`  (
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采集点编码',
  `point_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采集点名称',
  `point_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MANUAL/AUTO',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联设备ID',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT '关联 IoT 设备 ID（AUTO 类型必填）',
  `iot_property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IoT 属性编码（对应 iot_device_data.property_code）',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `frequency` int(11) NULL DEFAULT NULL COMMENT '采集频率(秒)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `last_archive_time` datetime(0) NULL DEFAULT NULL COMMENT '最后成功归档时间（数据补偿游标）',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`point_id`) USING BTREE,
  UNIQUE INDEX `uk_point_code`(`point_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES数据采集点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dc_data_record
-- ----------------------------
DROP TABLE IF EXISTS `dc_data_record`;
CREATE TABLE `dc_data_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采集点编码(冗余)',
  `property_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IoT 属性编码（source=IoT 时填充）',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序记录ID',
  `numeric_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文本值',
  `collect_time` datetime(0) NOT NULL COMMENT '采集时间',
  `collect_by` bigint(20) NULL DEFAULT NULL COMMENT '采集人(手动时)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE INDEX `uk_point_collect_time`(`point_id`, `collect_time`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_collect_time`(`collect_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES采集数据记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_equipment
-- ----------------------------
DROP TABLE IF EXISTS `eq_equipment`;
CREATE TABLE `eq_equipment`  (
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编码',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备名称',
  `equipment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备类型',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '型号',
  `manufacturer` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '制造商',
  `serial_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出厂编号',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `location` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '安装位置',
  `purchase_date` date NULL DEFAULT NULL COMMENT '购置日期',
  `warranty_date` date NULL DEFAULT NULL COMMENT '保修到期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
  `inspection_items` json NULL COMMENT '点检项目及标准(JSON)',
  `last_inspection` json NULL COMMENT '最近一次点检结果(JSON)',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT '关联IoT设备ID(预留,无外键)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`equipment_id`) USING BTREE,
  UNIQUE INDEX `uk_equipment_code`(`equipment_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_equipment
-- ----------------------------
INSERT INTO `eq_equipment` VALUES (1, '000000', 'EQ0001', '激光切割机', 'LASER', 'MODEL-LASER', '浙江智能装备制造有限公司', 'SN202609140001', 1, 'GZZ0001', '下料车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (2, '000000', 'EQ0002', '等离子切割机', 'PLASMA', 'MODEL-PLASMA', '浙江智能装备制造有限公司', 'SN202609140002', 2, 'GZZ0002', '下料车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (3, '000000', 'EQ0003', '剪板机', 'SHEAR', 'MODEL-SHEAR', '浙江智能装备制造有限公司', 'SN202609140003', 3, 'GZZ0003', '下料车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (4, '000000', 'EQ0004', '锯床', 'SAW', 'MODEL-SAW', '浙江智能装备制造有限公司', 'SN202609140004', 4, 'GZZ0004', '下料车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (5, '000000', 'EQ0005', '数控车床A', 'CNC', 'MODEL-CNC', '浙江智能装备制造有限公司', 'SN202609140005', 5, 'GZZ0005', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, 1, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (6, '000000', 'EQ0006', '数控车床B', 'CNC', 'MODEL-CNC', '浙江智能装备制造有限公司', 'SN202609140006', 6, 'GZZ0006', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, 2, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (7, '000000', 'EQ0007', '加工中心A', 'CNC', 'MODEL-CNC', '浙江智能装备制造有限公司', 'SN202609140007', 7, 'GZZ0007', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, 3, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (8, '000000', 'EQ0008', '铣床', 'MILL', 'MODEL-MILL', '浙江智能装备制造有限公司', 'SN202609140008', 8, 'GZZ0008', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, 4, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (9, '000000', 'EQ0009', '磨床', 'GRIND', 'MODEL-GRIND', '浙江智能装备制造有限公司', 'SN202609140009', 9, 'GZZ0009', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, 5, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (10, '000000', 'EQ0010', '钻床', 'DRILL', 'MODEL-DRILL', '浙江智能装备制造有限公司', 'SN202609140010', 10, 'GZZ0010', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (11, '000000', 'EQ0011', '滚齿机', 'GENERAL', 'MODEL-GEN', '浙江智能装备制造有限公司', 'SN202609140011', 11, 'GZZ0011', '机加车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (12, '000000', 'EQ0012', '冲床A', 'PUNCH', 'MODEL-PUNCH', '浙江智能装备制造有限公司', 'SN202609140012', 12, 'GZZ0012', '冲压车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (13, '000000', 'EQ0013', '冲床B', 'PUNCH', 'MODEL-PUNCH', '浙江智能装备制造有限公司', 'SN202609140013', 13, 'GZZ0013', '冲压车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (14, '000000', 'EQ0014', '折弯机', 'BEND', 'MODEL-BEND', '浙江智能装备制造有限公司', 'SN202609140014', 14, 'GZZ0014', '冲压车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (15, '000000', 'EQ0015', '油压机', 'PRESS', 'MODEL-PRESS', '浙江智能装备制造有限公司', 'SN202609140015', 15, 'GZZ0015', '冲压车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (16, '000000', 'EQ0016', '锻造机', 'GENERAL', 'MODEL-GEN', '浙江智能装备制造有限公司', 'SN202609140016', 16, 'GZZ0016', '冲压车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (17, '000000', 'EQ0017', '折弯工位', 'GENERAL', 'MODEL-GEN', '浙江智能装备制造有限公司', 'SN202609140017', 17, 'GZZ0017', '钣金车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (18, '000000', 'EQ0018', '卷圆工位', 'ROLL', 'MODEL-ROLL', '浙江智能装备制造有限公司', 'SN202609140018', 18, 'GZZ0018', '钣金车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (19, '000000', 'EQ0019', '冲孔工位', 'PUNCH', 'MODEL-PUNCH', '浙江智能装备制造有限公司', 'SN202609140019', 19, 'GZZ0019', '钣金车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (20, '000000', 'EQ0020', '氩弧焊工位', 'WELDING', 'MODEL-WELDING', '浙江智能装备制造有限公司', 'SN202609140020', 20, 'GZZ0020', '焊接车间', NULL, NULL, 'NORMAL', NULL, NULL, 6, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (21, '000000', 'EQ0021', '二保焊工位', 'WELDING', 'MODEL-WELDING', '浙江智能装备制造有限公司', 'SN202609140021', 21, 'GZZ0021', '焊接车间', NULL, NULL, 'NORMAL', NULL, NULL, 7, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (22, '000000', 'EQ0022', '点焊工位', 'WELDING', 'MODEL-WELDING', '浙江智能装备制造有限公司', 'SN202609140022', 22, 'GZZ0022', '焊接车间', NULL, NULL, 'NORMAL', NULL, NULL, 8, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (23, '000000', 'EQ0023', '打磨工位', 'GRIND', 'MODEL-GRIND', '浙江智能装备制造有限公司', 'SN202609140023', 23, 'GZZ0023', '焊接车间', NULL, NULL, 'NORMAL', NULL, NULL, 9, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (24, '000000', 'EQ0024', '注塑机A', 'INJECT', 'MODEL-INJECT', '浙江智能装备制造有限公司', 'SN202609140024', 24, 'GZZ0024', '注塑车间', NULL, NULL, 'NORMAL', NULL, NULL, 10, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (25, '000000', 'EQ0025', '注塑机B', 'INJECT', 'MODEL-INJECT', '浙江智能装备制造有限公司', 'SN202609140025', 25, 'GZZ0025', '注塑车间', NULL, NULL, 'NORMAL', NULL, NULL, 11, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (26, '000000', 'EQ0026', '注塑机C', 'INJECT', 'MODEL-INJECT', '浙江智能装备制造有限公司', 'SN202609140026', 26, 'GZZ0026', '注塑车间', NULL, NULL, 'NORMAL', NULL, NULL, 12, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (27, '000000', 'EQ0027', '去毛刺工位', 'DEBURR', 'MODEL-DEBURR', '浙江智能装备制造有限公司', 'SN202609140027', 27, 'GZZ0027', '注塑车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (28, '000000', 'EQ0028', '熔炼炉', 'FURNACE', 'MODEL-FURNACE', '浙江智能装备制造有限公司', 'SN202609140028', 28, 'GZZ0028', '铸造车间', NULL, NULL, 'NORMAL', NULL, NULL, 13, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (29, '000000', 'EQ0029', '压铸机', 'DIE_CAST', 'MODEL-DIE_CAST', '浙江智能装备制造有限公司', 'SN202609140029', 29, 'GZZ0029', '铸造车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (30, '000000', 'EQ0030', '清砂工位', 'CLEAN', 'MODEL-CLEAN', '浙江智能装备制造有限公司', 'SN202609140030', 30, 'GZZ0030', '铸造车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (31, '000000', 'EQ0031', '淬火炉', 'FURNACE', 'MODEL-FURNACE', '浙江智能装备制造有限公司', 'SN202609140031', 31, 'GZZ0031', '热处理车间', NULL, NULL, 'NORMAL', NULL, NULL, 14, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (32, '000000', 'EQ0032', '回火炉', 'FURNACE', 'MODEL-FURNACE', '浙江智能装备制造有限公司', 'SN202609140032', 32, 'GZZ0032', '热处理车间', NULL, NULL, 'NORMAL', NULL, NULL, 15, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (33, '000000', 'EQ0033', '渗碳炉', 'FURNACE', 'MODEL-FURNACE', '浙江智能装备制造有限公司', 'SN202609140033', 33, 'GZZ0033', '热处理车间', NULL, NULL, 'NORMAL', NULL, NULL, 16, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (34, '000000', 'EQ0034', '电镀线', 'PLATING', 'MODEL-PLATING', '浙江智能装备制造有限公司', 'SN202609140034', 34, 'GZZ0034', '表面处理车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (35, '000000', 'EQ0035', '阳极氧化线', 'ANODIZE', 'MODEL-ANODIZE', '浙江智能装备制造有限公司', 'SN202609140035', 35, 'GZZ0035', '表面处理车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (36, '000000', 'EQ0036', '喷塑线', 'COATING', 'MODEL-COATING', '浙江智能装备制造有限公司', 'SN202609140036', 36, 'GZZ0036', '表面处理车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (37, '000000', 'EQ0037', '喷漆房', 'COATING', 'MODEL-COATING', '浙江智能装备制造有限公司', 'SN202609140037', 37, 'GZZ0037', '表面处理车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (38, '000000', 'EQ0038', '丝印工位', 'GENERAL', 'MODEL-GEN', '浙江智能装备制造有限公司', 'SN202609140038', 38, 'GZZ0038', '表面处理车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (39, '000000', 'EQ0039', 'SMT贴片线', 'SMT', 'MODEL-SMT', '浙江智能装备制造有限公司', 'SN202609140039', 39, 'GZZ0039', '电子车间', NULL, NULL, 'NORMAL', NULL, NULL, 17, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (40, '000000', 'EQ0040', '插件线', 'INSERT', 'MODEL-INSERT', '浙江智能装备制造有限公司', 'SN202609140040', 40, 'GZZ0040', '电子车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (41, '000000', 'EQ0041', '波峰焊', 'WAVE', 'MODEL-WAVE', '浙江智能装备制造有限公司', 'SN202609140041', 41, 'GZZ0041', '电子车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (42, '000000', 'EQ0042', '手工焊工位', 'SOLDERING', 'MODEL-SOLDERING', '浙江智能装备制造有限公司', 'SN202609140042', 42, 'GZZ0042', '电子车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (43, '000000', 'EQ0043', '绕线机', 'WINDING', 'MODEL-WINDING', '浙江智能装备制造有限公司', 'SN202609140043', 43, 'GZZ0043', '电机车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (44, '000000', 'EQ0044', '浸漆工位', 'VARNISH', 'MODEL-VARNISH', '浙江智能装备制造有限公司', 'SN202609140044', 44, 'GZZ0044', '电机车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (45, '000000', 'EQ0045', '动平衡机', 'BALANCE', 'MODEL-BALANCE', '浙江智能装备制造有限公司', 'SN202609140045', 45, 'GZZ0045', '电机车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (46, '000000', 'EQ0046', '装配线A', 'ASSEMBLY', 'MODEL-ASSEMBLY', '浙江智能装备制造有限公司', 'SN202609140046', 46, 'GZZ0046', '装配车间', NULL, NULL, 'NORMAL', NULL, NULL, 18, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (47, '000000', 'EQ0047', '装配线B', 'ASSEMBLY', 'MODEL-ASSEMBLY', '浙江智能装备制造有限公司', 'SN202609140047', 47, 'GZZ0047', '装配车间', NULL, NULL, 'NORMAL', NULL, NULL, 19, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (48, '000000', 'EQ0048', '调试工位', 'DEBUG', 'MODEL-DEBUG', '浙江智能装备制造有限公司', 'SN202609140048', 48, 'GZZ0048', '装配车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (49, '000000', 'EQ0049', '功能测试工位', 'TEST', 'MODEL-TEST', '浙江智能装备制造有限公司', 'SN202609140049', 49, 'GZZ0049', '测试车间', NULL, NULL, 'NORMAL', NULL, NULL, 20, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (50, '000000', 'EQ0050', '老化房', 'AGING', 'MODEL-AGING', '浙江智能装备制造有限公司', 'SN202609140050', 50, 'GZZ0050', '测试车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (51, '000000', 'EQ0051', '气密测试台', 'TEST', 'MODEL-TEST', '浙江智能装备制造有限公司', 'SN202609140051', 51, 'GZZ0051', '测试车间', NULL, NULL, 'NORMAL', NULL, NULL, 21, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (52, '000000', 'EQ0052', '精度检验台', 'INSPECTION', 'MODEL-INSPECTION', '浙江智能装备制造有限公司', 'SN202609140052', 52, 'GZZ0052', '测试车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (53, '000000', 'EQ0053', '包装线A', 'PACKAGING', 'MODEL-PACKAGING', '浙江智能装备制造有限公司', 'SN202609140053', 53, 'GZZ0053', '包装车间', NULL, NULL, 'NORMAL', NULL, NULL, 22, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (54, '000000', 'EQ0054', '包装线B', 'PACKAGING', 'MODEL-PACKAGING', '浙江智能装备制造有限公司', 'SN202609140054', 54, 'GZZ0054', '包装车间', NULL, NULL, 'NORMAL', NULL, NULL, 23, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (55, '000000', 'EQ0055', '打包工位', 'PACKAGING', 'MODEL-PACKAGING', '浙江智能装备制造有限公司', 'SN202609140055', 55, 'GZZ0055', '包装车间', NULL, NULL, 'NORMAL', NULL, NULL, 24, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (56, '000000', 'EQ0056', '电火花机', 'EDM', 'MODEL-EDM', '浙江智能装备制造有限公司', 'SN202609140056', 56, 'GZZ0056', '模具车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (57, '000000', 'EQ0057', '线切割机', 'WEDM', 'MODEL-WEDM', '浙江智能装备制造有限公司', 'SN202609140057', 57, 'GZZ0057', '模具车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (58, '000000', 'EQ0058', '抛光工位', 'POLISH', 'MODEL-POLISH', '浙江智能装备制造有限公司', 'SN202609140058', 58, 'GZZ0058', '模具车间', NULL, NULL, 'NORMAL', NULL, NULL, NULL, '由工位台账自动生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for eq_health_record
-- ----------------------------
DROP TABLE IF EXISTS `eq_health_record`;
CREATE TABLE `eq_health_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT 'IoT设备ID',
  `score` int(11) NOT NULL COMMENT '健康评分(0-100)',
  `grade` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '等级: HIGH(>=80)/MEDIUM(>=60)/LOW(<60)',
  `anomaly_points` int(11) NULL DEFAULT 0 COMMENT '24h异常数据点数(z>3)',
  `repair_count_90d` int(11) NULL DEFAULT 0 COMMENT '90天内维修单数',
  `open_repair_count` int(11) NULL DEFAULT 0 COMMENT '未闭环维修单数',
  `detail_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '明细JSON[{propertyCode,propertyName,mean,std,anomalyPoints}]',
  `prev_score` int(11) NULL DEFAULT NULL COMMENT '前次评分(对比趋势)',
  `score_change` int(11) NULL DEFAULT NULL COMMENT '评分变化(本次-前次)',
  `record_date` date NOT NULL COMMENT '评分日期',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_ehr_equipment`(`equipment_id`) USING BTREE,
  INDEX `idx_ehr_date`(`record_date`) USING BTREE,
  INDEX `idx_ehr_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备健康评分记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_inspection_record
-- ----------------------------
DROP TABLE IF EXISTS `eq_inspection_record`;
CREATE TABLE `eq_inspection_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位编码(工位终端提交时留痕)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '点检人ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '点检人姓名',
  `operator_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '点检人工号(设备通道免登录留痕)',
  `inspect_time` datetime(0) NOT NULL COMMENT '点检时间',
  `results` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '逐项点检明细 JSON: [{item,standard,actual,pass}]',
  `overall_pass` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '总判定: Y合格/N不合格',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_inspect_time`(`inspect_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '设备点检记录(历史留痕, PC端与工位终端共用)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `eq_maintenance_plan`;
CREATE TABLE `eq_maintenance_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `maintenance_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
  `cycle_days` int(11) NULL DEFAULT NULL COMMENT '周期(天)',
  `next_date` date NULL DEFAULT NULL COMMENT '下次保养日期(自动计算)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '保养内容',
  `responsible_id` bigint(20) NULL DEFAULT NULL COMMENT '负责人ID',
  `responsible_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_next_date`(`next_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备保养计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_maintenance_record
-- ----------------------------
DROP TABLE IF EXISTS `eq_maintenance_record`;
CREATE TABLE `eq_maintenance_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '关联保养计划ID(可空)',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `maintenance_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '保养类型(冗余)',
  `executor_id` bigint(20) NULL DEFAULT NULL COMMENT '执行人ID',
  `executor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '执行人姓名',
  `execute_time` datetime(0) NULL DEFAULT NULL COMMENT '执行时间',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '保养内容/执行情况',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ABNORMAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_execute_time`(`execute_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备保养执行记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_oee_record
-- ----------------------------
DROP TABLE IF EXISTS `eq_oee_record`;
CREATE TABLE `eq_oee_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称(冗余)',
  `equipment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备类型(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(冗余, 车间级聚合用)',
  `stat_date` date NOT NULL COMMENT '统计日期(日粒度)',
  `planned_minutes` decimal(10, 2) NULL DEFAULT 480.00 COMMENT '计划生产时长(分钟, 首版默认480=8h单班)',
  `downtime_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '停机时长(分钟, 来自 eq_repair.downtime_hours 求和)',
  `maintenance_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '保养占用时长(分钟, 预留, 首版0)',
  `run_minutes` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '运行时长(分钟, = planned - downtime - maintenance)',
  `fault_count` int(11) NULL DEFAULT 0 COMMENT '故障次数(eq_repair 当日报修数)',
  `availability` decimal(6, 4) NULL DEFAULT 0.0000 COMMENT '可用率/稼动率(0~1), = run/planned',
  `performance_rate` decimal(6, 4) NULL DEFAULT 1.0000 COMMENT '表现率(首版占位1.0, 待工单报工数据补全)',
  `quality_rate` decimal(6, 4) NULL DEFAULT 1.0000 COMMENT '质量率(首版占位1.0, 待质量数据补全)',
  `oee` decimal(6, 4) NULL DEFAULT 0.0000 COMMENT 'OEE = availability * performance * quality',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_eq_date`(`tenant_id`, `equipment_id`, `stat_date`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_stat_date`(`stat_date`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备 OEE 聚合记录(日粒度)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_patrol_record
-- ----------------------------
DROP TABLE IF EXISTS `eq_patrol_record`;
CREATE TABLE `eq_patrol_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `task_id` bigint(20) NULL DEFAULT NULL COMMENT '关联任务ID(可空, 手动巡检无任务)',
  `route_id` bigint(20) NULL DEFAULT NULL COMMENT '关联路线ID(冗余)',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码(冗余)',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称(冗余)',
  `check_item` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检查项目',
  `actual_value` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实际值/读数',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ABNORMAL',
  `photo_oss_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '照片ossId',
  `executor_id` bigint(20) NULL DEFAULT NULL COMMENT '执行人ID',
  `executor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '执行人姓名',
  `execute_time` datetime(0) NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_execute_time`(`execute_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_patrol_route
-- ----------------------------
DROP TABLE IF EXISTS `eq_patrol_route`;
CREATE TABLE `eq_patrol_route`  (
  `route_id` bigint(20) NOT NULL COMMENT '路线ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `route_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路线描述',
  `equipment_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '设备ID列表(JSON数组)',
  `check_items` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '通用检查项(JSON数组)',
  `frequency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DAILY' COMMENT '频率 DAILY/WEEKLY/MONTHLY',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`route_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检路线' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_patrol_task
-- ----------------------------
DROP TABLE IF EXISTS `eq_patrol_task`;
CREATE TABLE `eq_patrol_task`  (
  `task_id` bigint(20) NOT NULL COMMENT '任务ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `route_id` bigint(20) NOT NULL COMMENT '关联路线ID',
  `route_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路线名称(冗余)',
  `plan_date` date NOT NULL COMMENT '计划巡检日期',
  `actual_date` datetime(0) NULL DEFAULT NULL COMMENT '实际完成时间',
  `assigned_to_id` bigint(20) NULL DEFAULT NULL COMMENT '指派人ID',
  `assigned_to_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '指派人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`task_id`) USING BTREE,
  INDEX `idx_route_id`(`route_id`) USING BTREE,
  INDEX `idx_plan_date`(`plan_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检任务' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_repair
-- ----------------------------
DROP TABLE IF EXISTS `eq_repair`;
CREATE TABLE `eq_repair`  (
  `repair_id` bigint(20) NOT NULL COMMENT '维修ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `repair_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修单号',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `fault_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '故障类型',
  `fault_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '故障描述',
  `reporter_id` bigint(20) NULL DEFAULT NULL COMMENT '报修人ID',
  `reporter_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报修人姓名',
  `repairer_id` bigint(20) NULL DEFAULT NULL COMMENT '维修人ID',
  `repairer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '维修人姓名',
  `repair_start` datetime(0) NULL DEFAULT NULL COMMENT '维修开始时间',
  `repair_end` datetime(0) NULL DEFAULT NULL COMMENT '维修结束时间',
  `downtime_hours` decimal(10, 2) NULL DEFAULT NULL COMMENT '停机时长(小时)',
  `repair_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '维修成本',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`repair_id`) USING BTREE,
  UNIQUE INDEX `uk_repair_no`(`repair_no`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备维修记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_repair_spare
-- ----------------------------
DROP TABLE IF EXISTS `eq_repair_spare`;
CREATE TABLE `eq_repair_spare`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `repair_id` bigint(20) NOT NULL COMMENT '维修单ID',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件名称(冗余)',
  `spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格(冗余)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '领用数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_repair_id`(`repair_id`) USING BTREE,
  INDEX `idx_spare_id`(`spare_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES维修领用备件明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_spare_part
-- ----------------------------
DROP TABLE IF EXISTS `eq_spare_part`;
CREATE TABLE `eq_spare_part`  (
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备件编码(唯一)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备件名称',
  `spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '型号',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类(字典 mes_spare_category)',
  `safety_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '安全库存',
  `current_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '当前库存(冗余, 出入库流水维护)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`spare_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_code`(`tenant_id`, `spare_code`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件主数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eq_spare_stock_log
-- ----------------------------
DROP TABLE IF EXISTS `eq_spare_stock_log`;
CREATE TABLE `eq_spare_stock_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '流水ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `spare_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件名称(冗余)',
  `transaction_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN/OUT/REPAIR_USE',
  `quantity` decimal(14, 4) NOT NULL COMMENT '变动数量(正数)',
  `before_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '变动前库存',
  `after_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '变动后库存',
  `ref_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联单据类型 MANUAL/REPAIR',
  `ref_id` bigint(20) NULL DEFAULT NULL COMMENT '关联单据ID',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `operate_time` datetime(0) NULL DEFAULT NULL COMMENT '操作时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_spare_id`(`spare_id`) USING BTREE,
  INDEX `idx_operate_time`(`operate_time`) USING BTREE,
  INDEX `idx_ref`(`ref_type`, `ref_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件库存流水' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lt_lot
-- ----------------------------
DROP TABLE IF EXISTS `lt_lot`;
CREATE TABLE `lt_lot`  (
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次编码',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `supplier_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称(冗余)',
  `supplier_lot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商批次号',
  `quantity` decimal(14, 4) NOT NULL COMMENT '批次数量',
  `consumed_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '已消耗数量',
  `produced_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '有效期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IN_STOCK' COMMENT '状态: IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED',
  `qc_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT 'QC状态: PENDING/PASS/FAIL',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT 'IQC检验单ID(关联qc_iqc.iqc_id,可空;关联后IQC完成自动回写qc_status)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`lot_id`) USING BTREE,
  UNIQUE INDEX `uk_lot_code`(`lot_code`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_qc_status`(`qc_status`) USING BTREE,
  INDEX `idx_expiry`(`expiry_date`) USING BTREE,
  INDEX `idx_iqc_id`(`iqc_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES批次主档' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lt_sn_binding
-- ----------------------------
DROP TABLE IF EXISTS `lt_sn_binding`;
CREATE TABLE `lt_sn_binding`  (
  `binding_id` bigint(20) NOT NULL COMMENT '绑定ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SN编码(冗余)',
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `bound_time` datetime(0) NOT NULL COMMENT '绑定时间',
  `bound_by` bigint(20) NULL DEFAULT NULL COMMENT '绑定人ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`binding_id`) USING BTREE,
  UNIQUE INDEX `uk_sn_material`(`sn_id`, `material_id`) USING BTREE,
  INDEX `idx_sn_id`(`sn_id`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SN批次绑定关系' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lt_wo_consumption
-- ----------------------------
DROP TABLE IF EXISTS `lt_wo_consumption`;
CREATE TABLE `lt_wo_consumption`  (
  `consumption_id` bigint(20) NOT NULL COMMENT '消耗记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `lot_id` bigint(20) NOT NULL COMMENT '批次ID',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `consumed_qty` decimal(14, 4) NOT NULL COMMENT '消耗数量',
  `consumed_time` datetime(0) NOT NULL COMMENT '消耗时间',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`consumption_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_consumed_time`(`consumed_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工单批次消耗记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_content_file
-- ----------------------------
DROP TABLE IF EXISTS `mes_content_file`;
CREATE TABLE `mes_content_file`  (
  `content_file_id` bigint(20) NOT NULL COMMENT '配置文件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `content_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置编码(自动编码 MES_CONFIG_CODE)',
  `content_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `content_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CONFIG_FILE' COMMENT '配置类型: CONFIG_FILE(可扩展校准/证书)',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `oss_id` bigint(20) NULL DEFAULT NULL COMMENT '配置文件OSS文件ID',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MD5',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `release_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '物料默认版本(0否/1是)',
  `published_by` bigint(20) NULL DEFAULT NULL,
  `publish_time` datetime(0) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`content_file_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_content_code`(`tenant_id`, `content_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写内容档案(配置文件等, 审批发布)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_firmware
-- ----------------------------
DROP TABLE IF EXISTS `mes_firmware`;
CREATE TABLE `mes_firmware`  (
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件编码(自动编码 MES_FW_CODE)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID(产品)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 1.2.3)',
  `build_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '构建号',
  `oss_id` bigint(20) NULL DEFAULT NULL COMMENT '固件文件OSS文件ID(sys_oss)',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件文件MD5',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '固件文件大小(字节)',
  `release_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否物料默认版本(0否/1是, 物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`firmware_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_firmware_code`(`tenant_id`, `firmware_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固件档案' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_firmware
-- ----------------------------
INSERT INTO `mes_firmware` VALUES (1, '000000', 'FW20260914001', '智能路由器 WR-1200主控固件', 14, 'IF20260914014', '智能路由器 WR-1200', 'V1.0.0', 'BUILD001', NULL, 'md50000000000000000000000000001', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (2, '000000', 'FW20260914002', '变频电风扇主控固件', 15, 'IF20260914015', '变频电风扇', 'V1.0.0', 'BUILD002', NULL, 'md50000000000000000000000000002', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (3, '000000', 'FW20260914003', '开关电源模组 SMPS-60主控固件', 16, 'IF20260914016', '开关电源模组 SMPS-60', 'V1.0.0', 'BUILD003', NULL, 'md50000000000000000000000000003', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (4, '000000', 'FW20260914004', '无刷直流电机 BLDC-45主控固件', 17, 'IF20260914017', '无刷直流电机 BLDC-45', 'V1.0.0', 'BUILD004', NULL, 'md50000000000000000000000000004', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (5, '000000', 'FW20260914005', '温度传感器模组 TS-100主控固件', 18, 'IF20260914018', '温度传感器模组 TS-100', 'V1.0.0', 'BUILD005', NULL, 'md50000000000000000000000000005', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (6, '000000', 'FW20260914006', 'LED 护眼台灯主控固件', 26, 'IF20260914026', 'LED 护眼台灯', 'V1.0.0', 'BUILD006', NULL, 'md50000000000000000000000000006', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (7, '000000', 'FW20260914007', '智能网关 GW-1000主控固件', 27, 'IF20260914027', '智能网关 GW-1000', 'V1.0.0', 'BUILD007', NULL, 'md50000000000000000000000000007', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (8, '000000', 'FW20260914008', '锂电池包 48V20Ah主控固件', 30, 'IF20260914030', '锂电池包 48V20Ah', 'V1.0.0', 'BUILD008', NULL, 'md50000000000000000000000000008', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (9, '000000', 'FW20260914009', '交流充电桩 AC-7kW主控固件', 31, 'IF20260914031', '交流充电桩 AC-7kW', 'V1.0.0', 'BUILD009', NULL, 'md50000000000000000000000000009', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (10, '000000', 'FW20260914010', '六轴机械臂 RB-6主控固件', 33, 'IF20260914033', '六轴机械臂 RB-6', 'V1.0.0', 'BUILD010', NULL, 'md50000000000000000000000000010', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (11, '000000', 'FW20260914011', '智能手环 BAND-3主控固件', 34, 'IF20260914034', '智能手环 BAND-3', 'V1.0.0', 'BUILD011', NULL, 'md50000000000000000000000000011', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (12, '000000', 'FW20260914012', '手持 POS 机 POS-8主控固件', 35, 'IF20260914035', '手持 POS 机 POS-8', 'V1.0.0', 'BUILD012', NULL, 'md50000000000000000000000000012', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (13, '000000', 'FW20260914013', '网络摄像机 IPC-300主控固件', 36, 'IF20260914036', '网络摄像机 IPC-300', 'V1.0.0', 'BUILD013', NULL, 'md50000000000000000000000000013', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_firmware` VALUES (14, '000000', 'FW20260914014', '无线蓝牙音箱 SPK-20主控固件', 37, 'IF20260914037', '无线蓝牙音箱 SPK-20', 'V1.0.0', 'BUILD014', NULL, 'md50000000000000000000000000014', 262144, '初始版本固件', '1', '1', NULL, 1, '2026-09-14 15:07:27', 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);

-- ----------------------------
-- Table structure for mes_flash_record
-- ----------------------------
DROP TABLE IF EXISTS `mes_flash_record`;
CREATE TABLE `mes_flash_record`  (
  `flash_record_id` bigint(20) NOT NULL COMMENT '刷写记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `flash_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷写记录号(自动编码 MES_FLASH_NO)',
  `flash_type_id` bigint(20) NULL DEFAULT NULL COMMENT '刷写类型ID(mes_flash_type)',
  `flash_type_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '刷写类型标识(快照: firmware_file/config_file/mac/sn)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(pro_flow_card_step)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `content_ref_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)',
  `content_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容版本(固件类=固件版本)',
  `content_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '实际写入内容快照JSON(如 {\"mac\":\"00:1A:2B:3C:4D:5E\"})',
  `content_checksum` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容校验值(泛化MD5/校验码)',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '刷写设备ID(eq_equipment)',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '刷写设备编码',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `flash_time` datetime(0) NULL DEFAULT NULL COMMENT '刷写时间',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果: PASS/FAIL',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `flash_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '刷写模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`flash_record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_flash_no`(`tenant_id`, `flash_no`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_sn`(`sn_id`) USING BTREE,
  INDEX `idx_wo`(`mes_wo_id`) USING BTREE,
  INDEX `idx_flash_type`(`tenant_id`, `flash_type_id`) USING BTREE,
  INDEX `idx_content_ref`(`content_ref_type`, `content_ref_id`) USING BTREE,
  INDEX `idx_sn_code`(`tenant_id`, `sn_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固件刷写记录(SN级)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_flash_type
-- ----------------------------
DROP TABLE IF EXISTS `mes_flash_type`;
CREATE TABLE `mes_flash_type`  (
  `flash_type_id` bigint(20) NOT NULL COMMENT '刷写类型ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `type_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型标识: firmware_file/config_file/mac/sn/external_*',
  `type_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容来源: FILE/POOL/EXTERNAL/INLINE',
  `source_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPI Provider bean名(如 firmwareFileFlashProvider)',
  `source_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源配置JSON(审批流标识/池ID/REST模板)',
  `verify_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MD5_MATCH' COMMENT '校验策略: MD5_MATCH/RESOURCE_BOUND/CUSTOM',
  `gate_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否参与完工门禁(0否/1是)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`flash_type_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_type_code`(`tenant_id`, `type_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写类型定义(配置化目录)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_label_template
-- ----------------------------
DROP TABLE IF EXISTS `mes_label_template`;
CREATE TABLE `mes_label_template`  (
  `template_id` bigint(20) NOT NULL COMMENT '模板ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `label_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'SN' COMMENT '标签类型: SN 标签(流转卡标签后续扩展)',
  `label_lang` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'TSPL' COMMENT '打印机语言: TSPL/ZPL',
  `width_mm` int(11) NULL DEFAULT 60 COMMENT '标签宽度(mm)',
  `height_mm` int(11) NULL DEFAULT 40 COMMENT '标签高度(mm)',
  `printer_host` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '网络打印机地址(TCP RAW 9100)',
  `printer_port` int(11) NULL DEFAULT 9100 COMMENT '网络打印机端口',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '字段映射布局 JSON: {items:[{type,field,x,y,size,height}]}',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'Y' COMMENT '是否启用(Y/N): SN 造码打印仅消费启用模板',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`template_id`) USING BTREE,
  UNIQUE INDEX `uk_template_code`(`template_code`) USING BTREE,
  INDEX `idx_label_type`(`label_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签打印模板' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_material_load
-- ----------------------------
DROP TABLE IF EXISTS `mes_material_load`;
CREATE TABLE `mes_material_load`  (
  `load_id` bigint(20) NOT NULL COMMENT '台账ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID(pro_work_order.mes_wo_id)',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工位编码',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作中心编码(MBOM快照冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID(wm_batch.batch_id)',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码(wm_batch.batch_code)',
  `feeder_slot` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '料站号(可空, 贴片机 feeder 位号等)',
  `is_alternative` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'N' COMMENT '是否替代料匹配: Y=bd_bom_alternative替代料, N=BOM主料',
  `operator_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上料人工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上料人姓名',
  `load_time` datetime(0) NOT NULL COMMENT '上料时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE=在机, REPLACED=已换料',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`load_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_station_code`(`station_code`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_load_time`(`load_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工位物料上料台账(上料防错 C19 写入, 换料置 REPLACED)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_mbom
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom`;
CREATE TABLE `mes_mbom`  (
  `mbom_id` bigint(20) NOT NULL COMMENT 'MBOM ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `bom_id` bigint(20) NOT NULL COMMENT '关联ERP bd_bom.bom_id',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '制造工艺路线ID(可继承自bd_bom.routing_id或MES侧指定)',
  `mbom_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MBOM版本号',
  `mbom_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/FROZEN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_id`) USING BTREE,
  UNIQUE INDEX `uk_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_routing_id`(`routing_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES制造BOM头' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_mbom
-- ----------------------------
INSERT INTO `mes_mbom` VALUES (1, '000000', 1, 1, '1.0', 'ACTIVE', '高强度螺栓组套 M12制造BOM（关联ERP BOM#4）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (2, '000000', 5, 2, '1.0', 'ACTIVE', '精密齿轮箱总成制造BOM（关联ERP BOM#9）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (3, '000000', 10, 3, '1.0', 'ACTIVE', '压缩弹簧组制造BOM（关联ERP BOM#11）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (4, '000000', 12, 4, '1.0', 'ACTIVE', '设备钣金机箱制造BOM（关联ERP BOM#14）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (5, '000000', 15, 5, '1.0', 'ACTIVE', '钢结构支架总成制造BOM（关联ERP BOM#17）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (6, '000000', 18, 6, '1.0', 'ACTIVE', '注塑模具制造BOM（关联ERP BOM#21）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (7, '000000', 22, 7, '1.0', 'ACTIVE', '棘轮扳手组套制造BOM（关联ERP BOM#25）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (8, '000000', 26, 8, '1.0', 'ACTIVE', '齿轮减速机 JZQ-350制造BOM（关联ERP BOM#30）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (9, '000000', 31, 9, '1.0', 'ACTIVE', '单级离心泵制造BOM（关联ERP BOM#34）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (10, '000000', 35, 10, '1.0', 'ACTIVE', '数控铣床 XK714制造BOM（关联ERP BOM#39）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (11, '000000', 40, 11, '1.0', 'ACTIVE', '皮带输送线制造BOM（关联ERP BOM#44）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (12, '000000', 45, 12, '1.0', 'ACTIVE', '螺杆式空压机制造BOM（关联ERP BOM#48）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (13, '000000', 49, 13, '1.0', 'ACTIVE', '双层 PCB 板制造BOM（关联ERP BOM#50）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (14, '000000', 51, 14, '1.0', 'ACTIVE', '智能路由器 WR-1200制造BOM（关联ERP BOM#54）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (15, '000000', 55, 15, '1.0', 'ACTIVE', '变频电风扇制造BOM（关联ERP BOM#59）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (16, '000000', 60, 16, '1.0', 'ACTIVE', '开关电源模组 SMPS-60制造BOM（关联ERP BOM#61）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (17, '000000', 62, 17, '1.0', 'ACTIVE', '无刷直流电机 BLDC-45制造BOM（关联ERP BOM#64）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (18, '000000', 65, 18, '1.0', 'ACTIVE', '温度传感器模组 TS-100制造BOM（关联ERP BOM#67）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (19, '000000', 68, 19, '1.0', 'ACTIVE', '数字万用表 DM-800制造BOM（关联ERP BOM#70）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (20, '000000', 71, 20, '1.0', 'ACTIVE', '汽车门锁总成制造BOM（关联ERP BOM#75）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (21, '000000', 76, 21, '1.0', 'ACTIVE', '城市通勤自行车制造BOM（关联ERP BOM#79）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (22, '000000', 80, 22, '1.0', 'ACTIVE', '四轴无人机机架制造BOM（关联ERP BOM#83）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (23, '000000', 84, 23, '1.0', 'ACTIVE', '不锈钢手术剪制造BOM（关联ERP BOM#87）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (24, '000000', 88, 24, '1.0', 'ACTIVE', '手动轮椅制造BOM（关联ERP BOM#91）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (25, '000000', 92, 25, '1.0', 'ACTIVE', '简易金属工具架 RACK-001制造BOM（关联ERP BOM#95）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (26, '000000', 96, 26, '1.0', 'ACTIVE', 'LED 护眼台灯制造BOM（关联ERP BOM#100）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (27, '000000', 101, 27, '1.0', 'ACTIVE', '智能网关 GW-1000制造BOM（关联ERP BOM#103）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (28, '000000', 104, 28, '1.0', 'ACTIVE', '精密塑料外壳 PC-200制造BOM（关联ERP BOM#106）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (29, '000000', 107, 29, '1.0', 'ACTIVE', '硅胶密封圈组制造BOM（关联ERP BOM#108）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (30, '000000', 109, 30, '1.0', 'ACTIVE', '锂电池包 48V20Ah制造BOM（关联ERP BOM#111）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (31, '000000', 112, 31, '1.0', 'ACTIVE', '交流充电桩 AC-7kW制造BOM（关联ERP BOM#115）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (32, '000000', 116, 32, '1.0', 'ACTIVE', '光伏组件 450W制造BOM（关联ERP BOM#117）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (33, '000000', 118, 33, '1.0', 'ACTIVE', '六轴机械臂 RB-6制造BOM（关联ERP BOM#122）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (34, '000000', 123, 34, '1.0', 'ACTIVE', '智能手环 BAND-3制造BOM（关联ERP BOM#126）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (35, '000000', 127, 35, '1.0', 'ACTIVE', '手持 POS 机 POS-8制造BOM（关联ERP BOM#131）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (36, '000000', 132, 36, '1.0', 'ACTIVE', '网络摄像机 IPC-300制造BOM（关联ERP BOM#134）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (37, '000000', 135, 37, '1.0', 'ACTIVE', '无线蓝牙音箱 SPK-20制造BOM（关联ERP BOM#137）', 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for mes_mbom_line
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom_line`;
CREATE TABLE `mes_mbom_line`  (
  `mbom_line_id` bigint(20) NOT NULL COMMENT 'MBOM明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mbom_id` bigint(20) NOT NULL COMMENT '关联mes_mbom.mbom_id',
  `bom_id` bigint(20) NOT NULL COMMENT '冗余ERP bom_id',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '关联ERP bd_bom_detail.detail_id(可空,MES附加消耗料时为空)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_routing_step.step_id(工序步骤)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_process.process_id(工序)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_workstation.workstation_id(工位)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_line_id`) USING BTREE,
  INDEX `idx_mbom_id`(`mbom_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_bom_detail_id`(`bom_detail_id`) USING BTREE,
  INDEX `idx_routing_step_id`(`routing_step_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES制造BOM明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_mbom_line
-- ----------------------------
INSERT INTO `mes_mbom_line` VALUES (1, '000000', 1, 4, NULL, 38, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (2, '000000', 1, 4, NULL, 39, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (3, '000000', 1, 4, NULL, 40, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (4, '000000', 1, 4, NULL, 140, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (5, '000000', 2, 9, NULL, 41, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (6, '000000', 2, 9, NULL, 42, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (7, '000000', 2, 9, NULL, 43, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (8, '000000', 2, 9, NULL, 44, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (9, '000000', 2, 9, NULL, 142, 2.500000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (10, '000000', 2, 9, NULL, 143, 4.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 6, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (11, '000000', 3, 11, NULL, 45, 10.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (12, '000000', 3, 11, NULL, 140, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (13, '000000', 4, 14, NULL, 46, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (14, '000000', 4, 14, NULL, 47, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (15, '000000', 4, 14, NULL, 146, 0.800000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (16, '000000', 5, 17, NULL, 48, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (17, '000000', 5, 17, NULL, 49, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (18, '000000', 5, 17, NULL, 149, 1.600000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (19, '000000', 6, 21, NULL, 50, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (20, '000000', 6, 21, NULL, 51, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (21, '000000', 6, 21, NULL, 52, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (22, '000000', 7, 25, NULL, 53, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (23, '000000', 7, 25, NULL, 54, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (24, '000000', 7, 25, NULL, 55, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (25, '000000', 7, 25, NULL, 154, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (26, '000000', 8, 30, NULL, 56, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (27, '000000', 8, 30, NULL, 57, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (28, '000000', 8, 30, NULL, 58, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (29, '000000', 8, 30, NULL, 59, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (30, '000000', 8, 30, NULL, 142, 6.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (31, '000000', 9, 34, NULL, 60, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (32, '000000', 9, 34, NULL, 61, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (33, '000000', 9, 34, NULL, 62, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (34, '000000', 9, 34, NULL, 158, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (35, '000000', 10, 39, NULL, 63, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (36, '000000', 10, 39, NULL, 64, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (37, '000000', 10, 39, NULL, 65, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (38, '000000', 10, 39, NULL, 66, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (39, '000000', 10, 39, NULL, 149, 6.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (40, '000000', 11, 44, NULL, 67, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (41, '000000', 11, 44, NULL, 68, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (42, '000000', 11, 44, NULL, 69, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (43, '000000', 11, 44, NULL, 70, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (44, '000000', 11, 44, NULL, 146, 2.400000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (45, '000000', 12, 48, NULL, 71, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (46, '000000', 12, 48, NULL, 72, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (47, '000000', 12, 48, NULL, 73, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (48, '000000', 12, 48, NULL, 142, 12.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (49, '000000', 13, 50, NULL, 74, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (50, '000000', 14, 54, NULL, 75, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (51, '000000', 14, 54, NULL, 76, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (52, '000000', 14, 54, NULL, 77, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (53, '000000', 14, 54, NULL, 171, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (54, '000000', 15, 59, NULL, 78, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (55, '000000', 15, 59, NULL, 79, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (56, '000000', 15, 59, NULL, 80, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (57, '000000', 15, 59, NULL, 81, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (58, '000000', 15, 59, NULL, 173, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (59, '000000', 16, 61, NULL, 82, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (60, '000000', 17, 64, NULL, 83, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (61, '000000', 17, 64, NULL, 84, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (62, '000000', 17, 64, NULL, 179, 0.160000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (63, '000000', 18, 67, NULL, 85, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (64, '000000', 18, 67, NULL, 86, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (65, '000000', 19, 70, NULL, 87, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (66, '000000', 19, 70, NULL, 88, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (67, '000000', 20, 75, NULL, 89, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (68, '000000', 20, 75, NULL, 90, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (69, '000000', 20, 75, NULL, 91, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (70, '000000', 20, 75, NULL, 92, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (71, '000000', 21, 79, NULL, 93, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (72, '000000', 21, 79, NULL, 94, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (73, '000000', 21, 79, NULL, 95, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (74, '000000', 21, 79, NULL, 146, 0.450000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (75, '000000', 22, 83, NULL, 96, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (76, '000000', 22, 83, NULL, 97, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (77, '000000', 22, 83, NULL, 98, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (78, '000000', 23, 87, NULL, 99, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (79, '000000', 23, 87, NULL, 100, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (80, '000000', 23, 87, NULL, 101, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (81, '000000', 23, 87, NULL, 198, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (82, '000000', 24, 91, NULL, 102, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (83, '000000', 24, 91, NULL, 103, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (84, '000000', 24, 91, NULL, 104, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (85, '000000', 24, 91, NULL, 146, 0.700000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (86, '000000', 25, 95, NULL, 105, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (87, '000000', 25, 95, NULL, 106, 4.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (88, '000000', 25, 95, NULL, 107, 8.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (89, '000000', 25, 95, NULL, 146, 0.420000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (90, '000000', 25, 95, NULL, 201, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (91, '000000', 26, 100, NULL, 108, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (92, '000000', 26, 100, NULL, 109, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (93, '000000', 26, 100, NULL, 110, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (94, '000000', 26, 100, NULL, 111, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (95, '000000', 27, 103, NULL, 112, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (96, '000000', 27, 103, NULL, 113, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (97, '000000', 27, 103, NULL, 114, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (98, '000000', 27, 103, NULL, 171, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (99, '000000', 28, 106, NULL, 115, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (100, '000000', 28, 106, NULL, 116, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (101, '000000', 29, 108, NULL, 117, 20.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (102, '000000', 29, 108, NULL, 207, 0.080000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (103, '000000', 30, 111, NULL, 118, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (104, '000000', 30, 111, NULL, 119, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (105, '000000', 31, 115, NULL, 120, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (106, '000000', 31, 115, NULL, 121, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (107, '000000', 31, 115, NULL, 122, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (108, '000000', 31, 115, NULL, 146, 0.900000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (109, '000000', 32, 117, NULL, 123, 12.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (110, '000000', 32, 117, NULL, 216, 2.400000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (111, '000000', 33, 122, NULL, 124, 3.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (112, '000000', 33, 122, NULL, 125, 3.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (113, '000000', 33, 122, NULL, 126, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (114, '000000', 33, 122, NULL, 127, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (115, '000000', 33, 122, NULL, 220, 2.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 5, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (116, '000000', 34, 126, NULL, 128, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (117, '000000', 34, 126, NULL, 129, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (118, '000000', 34, 126, NULL, 130, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (119, '000000', 34, 126, NULL, 223, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (120, '000000', 35, 131, NULL, 131, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (121, '000000', 35, 131, NULL, 132, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (122, '000000', 35, 131, NULL, 133, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 3, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (123, '000000', 35, 131, NULL, 134, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 4, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (124, '000000', 36, 134, NULL, 135, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (125, '000000', 36, 134, NULL, 136, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (126, '000000', 37, 137, NULL, 137, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 1, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (127, '000000', 37, 137, NULL, 138, 1.000000, 0.0000, NULL, NULL, NULL, NULL, 0, 2, NULL, 1, 103, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for mes_release_package
-- ----------------------------
DROP TABLE IF EXISTS `mes_release_package`;
CREATE TABLE `mes_release_package`  (
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布包编码(自动编码 MES_RP_CODE)',
  `client_id` bigint(20) NOT NULL COMMENT '客户ID(bd_client.client_id)',
  `client_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '成品物料ID(bd_material.material_id)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `customer_product_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品代号(客户对该产品的内部编号, 匹配键)',
  `customer_product_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 2.1)',
  `release_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `effective_date` datetime(0) NULL DEFAULT NULL COMMENT '生效日期(空=发布即生效)',
  `expiry_date` datetime(0) NULL DEFAULT NULL COMMENT '过期日期(空=永久有效)',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否客户+物料默认发布包(0否/1是, 客户+物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`package_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_package_code`(`tenant_id`, `package_code`) USING BTREE,
  INDEX `idx_client_material`(`client_id`, `material_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES客户定制化发布包主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_release_pkg_bom_item
-- ----------------------------
DROP TABLE IF EXISTS `mes_release_pkg_bom_item`;
CREATE TABLE `mes_release_pkg_bom_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `action_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型: REPLACE/ADD/REMOVE',
  `child_material_id` bigint(20) NULL DEFAULT NULL COMMENT '原物料ID(REPLACE/REMOVE 定位键, 按子件物料定位抗BOM版本变更)',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料名称(冗余)',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '标准BOM明细行ID(可选精确定位, 空则按原物料定位)',
  `target_material_id` bigint(20) NULL DEFAULT NULL COMMENT '目标物料ID(REPLACE/ADD 必填)',
  `target_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料编码(冗余)',
  `target_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NULL DEFAULT NULL COMMENT '单位用量(空=REPLACE沿用原行/ADD必填)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包BOM选配表(替换/新增/移除)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_release_pkg_config
-- ----------------------------
DROP TABLE IF EXISTS `mes_release_pkg_config`;
CREATE TABLE `mes_release_pkg_config`  (
  `config_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `config_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS',
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置值',
  `config_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STRING' COMMENT '值类型: STRING/NUMBER/BOOLEAN/JSON',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID(空=全局生效)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE,
  INDEX `idx_group`(`config_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包配置项表(测试参数/标签/包装/工艺参数)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_release_pkg_firmware
-- ----------------------------
DROP TABLE IF EXISTS `mes_release_pkg_firmware`;
CREATE TABLE `mes_release_pkg_firmware`  (
  `mapping_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺路线步骤ID(bd_routing_step.step_id, 精确定位)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(bd_process.process_id, 宽泛定位)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `pcba_material_id` bigint(20) NULL DEFAULT NULL COMMENT 'PCBA物料ID(固件物理载体, 追溯用, 可空)',
  `pcba_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料编码(冗余)',
  `pcba_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料名称(冗余)',
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID(mes_firmware.firmware_id, 仅RELEASED)',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件编码(冗余)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件名称(冗余)',
  `firmware_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件版本(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`mapping_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE,
  INDEX `idx_process`(`process_id`) USING BTREE,
  INDEX `idx_firmware`(`firmware_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包固件映射表(工序→固件)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_resource_item
-- ----------------------------
DROP TABLE IF EXISTS `mes_resource_item`;
CREATE TABLE `mes_resource_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '资源项ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_id` bigint(20) NOT NULL COMMENT '资源池ID',
  `resource_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源值(如MAC 00:1A:2B:3C:4D:5E)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/ALLOCATED/USED/SCRAPPED',
  `bound_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '已绑定SN ID(一SN绑一资源)',
  `bound_time` datetime(0) NULL DEFAULT NULL COMMENT '绑定时间',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_pool_value`(`tenant_id`, `pool_id`, `resource_value`) USING BTREE,
  INDEX `idx_pool_status`(`pool_id`, `status`) USING BTREE,
  INDEX `idx_bound_sn`(`bound_sn_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源池明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_resource_pool
-- ----------------------------
DROP TABLE IF EXISTS `mes_resource_pool`;
CREATE TABLE `mes_resource_pool`  (
  `pool_id` bigint(20) NOT NULL COMMENT '资源池ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池编码(自动编码 MES_POOL_CODE)',
  `pool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池名称',
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型: MAC/SN/CERT',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INTERNAL' COMMENT '来源: INTERNAL(内部导入)/EXTERNAL(外部REST)',
  `external_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外部REST配置JSON(baseUrl/路径/鉴权, 经SSRF校验)',
  `alloc_strategy` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIFO' COMMENT '分配策略: FIFO/随机',
  `total_count` int(11) NULL DEFAULT NULL COMMENT '总数量',
  `used_count` int(11) NOT NULL DEFAULT 0 COMMENT '已分配数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`pool_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_pool_code`(`tenant_id`, `pool_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_resource_type`(`resource_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写资源池(MAC/SN等)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_sn_bind_log
-- ----------------------------
DROP TABLE IF EXISTS `mes_sn_bind_log`;
CREATE TABLE `mes_sn_bind_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作: BIND/UNBIND/REBIND',
  `parent_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '父SN ID',
  `parent_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父SN编码',
  `component_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '组件SN ID',
  `component_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '组件SN编码',
  `new_component_sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '换板新组件SN编码(REBIND)',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作原因(解绑/换板必填)',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员工号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_parent`(`parent_sn_id`) USING BTREE,
  INDEX `idx_component`(`component_sn_id`) USING BTREE,
  INDEX `idx_parent_sn_code`(`tenant_id`, `parent_sn_code`) USING BTREE,
  INDEX `idx_component_sn_code`(`tenant_id`, `component_sn_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '关键件绑定操作日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_station_device
-- ----------------------------
DROP TABLE IF EXISTS `mes_station_device`;
CREATE TABLE `mes_station_device`  (
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `device_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编码(工控机)',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绑定工位编码',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '绑定工序编码',
  `app_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '终端版本',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前操作员工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前操作员姓名',
  `shutdown_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '主动下线标记: 1=应用正常退出(即时离线判定)',
  `last_heartbeat` datetime(0) NULL DEFAULT NULL COMMENT '最后心跳时间',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`device_id`) USING BTREE,
  UNIQUE INDEX `uk_device_code`(`device_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工位终端设备台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_station_report
-- ----------------------------
DROP TABLE IF EXISTS `mes_station_report`;
CREATE TABLE `mes_station_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报工记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `report_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报工类型: SINGLE(单件过站)/BATCH(批量计数)',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SN编码(SINGLE)',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工位编码',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员用户ID',
  `operator_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员工号',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '结果: PASS/FAIL(SINGLE)',
  `defect_codes` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '不良代码(逗号分隔)',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良数量',
  `client_ts` bigint(20) NOT NULL COMMENT '客户端时间戳(毫秒)',
  `client_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键: SINGLE|sn|station|ts 或 BATCH|station|ts',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`report_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_client_key`(`tenant_id`, `client_key`) USING BTREE,
  INDEX `idx_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_station_code`(`station_code`) USING BTREE,
  INDEX `idx_step_record`(`step_record_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工位终端报工记录(设备通道)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mes_test_item
-- ----------------------------
DROP TABLE IF EXISTS `mes_test_item`;
CREATE TABLE `mes_test_item`  (
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(如 RF-TXPWR)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID',
  `content_ref_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '内容源ID(0=该物料所有版本通用, 固件类=mes_firmware_id)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NUMERIC' COMMENT '数据类型: NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(JSON数组)',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否必测(0否/1是)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`test_item_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_material_item`(`tenant_id`, `material_id`, `content_ref_id`, `item_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试项标准主数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_test_item
-- ----------------------------
INSERT INTO `mes_test_item` VALUES (1, '000000', 'TI20260914001', '双层 PCB 板出厂测试项', 13, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 1, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (2, '000000', 'TI20260914002', '智能路由器 WR-1200出厂测试项', 14, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 2, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (3, '000000', 'TI20260914003', '变频电风扇出厂测试项', 15, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 3, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (4, '000000', 'TI20260914004', '开关电源模组 SMPS-60出厂测试项', 16, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 4, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (5, '000000', 'TI20260914005', '无刷直流电机 BLDC-45出厂测试项', 17, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 5, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (6, '000000', 'TI20260914006', '温度传感器模组 TS-100出厂测试项', 18, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 6, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (7, '000000', 'TI20260914007', '数字万用表 DM-800出厂测试项', 19, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 7, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (8, '000000', 'TI20260914008', 'LED 护眼台灯出厂测试项', 26, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 8, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (9, '000000', 'TI20260914009', '智能网关 GW-1000出厂测试项', 27, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 9, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (10, '000000', 'TI20260914010', '锂电池包 48V20Ah出厂测试项', 30, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 10, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (11, '000000', 'TI20260914011', '交流充电桩 AC-7kW出厂测试项', 31, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 11, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (12, '000000', 'TI20260914012', '光伏组件 450W出厂测试项', 32, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 12, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (13, '000000', 'TI20260914013', '六轴机械臂 RB-6出厂测试项', 33, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 13, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (14, '000000', 'TI20260914014', '智能手环 BAND-3出厂测试项', 34, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 14, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (15, '000000', 'TI20260914015', '手持 POS 机 POS-8出厂测试项', 35, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 15, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (16, '000000', 'TI20260914016', '网络摄像机 IPC-300出厂测试项', 36, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 16, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);
INSERT INTO `mes_test_item` VALUES (17, '000000', 'TI20260914017', '无线蓝牙音箱 SPK-20出厂测试项', 37, 0, 'NUMBER', NULL, NULL, NULL, NULL, NULL, '1', 17, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL);

-- ----------------------------
-- Table structure for mes_test_record
-- ----------------------------
DROP TABLE IF EXISTS `mes_test_record`;
CREATE TABLE `mes_test_record`  (
  `test_record_id` bigint(20) NOT NULL COMMENT '测试记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `test_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试单号(一次SN测试一条单号, 共享多行)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(测试工序)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '测试时内容源ID(固件类=mes_firmware_id)',
  `content_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试时内容版本',
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称(快照)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型(快照)',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `numeric_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '实测数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测文本',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限(快照)',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限(快照)',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值(快照)',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(快照)',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '结果: PASS/FAIL(空=未测)',
  `tester_id` bigint(20) NULL DEFAULT NULL COMMENT '测试人ID',
  `tester_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试人姓名',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '测试设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试设备编码',
  `test_time` datetime(0) NULL DEFAULT NULL COMMENT '测试时间',
  `test_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '测试模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`test_record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_test_no_item`(`tenant_id`, `test_no`, `test_item_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_sn`(`sn_id`) USING BTREE,
  INDEX `idx_wo`(`mes_wo_id`) USING BTREE,
  INDEX `idx_content_ref`(`content_ref_id`) USING BTREE,
  INDEX `idx_sn_code`(`tenant_id`, `sn_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试记录(SN级全检)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ncr_8d_report
-- ----------------------------
DROP TABLE IF EXISTS `ncr_8d_report`;
CREATE TABLE `ncr_8d_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报告ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_id` bigint(20) NOT NULL COMMENT '关联NCR ID',
  `d1_team` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D1团队',
  `d2_problem` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D2问题描述',
  `d3_interim` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D3临时措施',
  `d4_root_cause` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D4根因分析',
  `d5_permanent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D5永久措施',
  `d6_implement` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D6措施实施与验证',
  `d7_prevent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D7预防措施',
  `d8_recognize` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'D8团队认可',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_PROGRESS/COMPLETED',
  `completed_by` bigint(20) NULL DEFAULT NULL COMMENT '完成人',
  `completed_time` datetime(0) NULL DEFAULT NULL COMMENT '完成时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`report_id`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '8D报告' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ncr_defect
-- ----------------------------
DROP TABLE IF EXISTS `ncr_defect`;
CREATE TABLE `ncr_defect`  (
  `defect_id` bigint(20) NOT NULL COMMENT '缺陷明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_id` bigint(20) NOT NULL COMMENT 'NCR ID',
  `defect_code_id` bigint(20) NULL DEFAULT NULL COMMENT '缺陷代码ID(可空-自动建单时仅记defectCode字符串,REVIEW时关联正式代码库)',
  `defect_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷代码(冗余)',
  `defect_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷名称(冗余)',
  `defect_qty` decimal(14, 4) NOT NULL COMMENT '缺陷数量',
  `defect_cause` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '缺陷原因(MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT)',
  `root_cause_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '根因描述',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`defect_id`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE,
  INDEX `idx_defect_code_id`(`defect_code_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR缺陷明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ncr_defect_code
-- ----------------------------
DROP TABLE IF EXISTS `ncr_defect_code`;
CREATE TABLE `ncr_defect_code`  (
  `code_id` bigint(20) NOT NULL COMMENT '代码ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_id` bigint(20) NULL DEFAULT 0 COMMENT '父代码ID(层级,0=根)',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷代码',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷名称',
  `level` int(4) NULL DEFAULT 1 COMMENT '层级(1类别/2子类/3代码)',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '是否启用(1/0)',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`code_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '缺陷代码库' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ncr_defect_code
-- ----------------------------
INSERT INTO `ncr_defect_code` VALUES (1, '000000', 0, 'DC01', '尺寸超差', 1, '1', 1, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (2, '000000', 1, 'DC0101', '外径超上差', 2, '1', 101, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (3, '000000', 1, 'DC0102', '外径超下差', 2, '1', 102, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (4, '000000', 1, 'DC0103', '长度超差', 2, '1', 103, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (5, '000000', 1, 'DC0104', '同轴度超差', 2, '1', 104, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (6, '000000', 1, 'DC0105', '平面度超差', 2, '1', 105, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (7, '000000', 1, 'DC0106', '孔位偏移', 2, '1', 106, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (8, '000000', 0, 'DC02', '外观缺陷', 1, '1', 2, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (9, '000000', 8, 'DC0201', '划痕', 2, '1', 201, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (10, '000000', 8, 'DC0202', '碰伤', 2, '1', 202, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (11, '000000', 8, 'DC0203', '凹坑', 2, '1', 203, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (12, '000000', 8, 'DC0204', '毛刺未清', 2, '1', 204, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (13, '000000', 8, 'DC0205', '锈蚀', 2, '1', 205, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (14, '000000', 8, 'DC0206', '色差', 2, '1', 206, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (15, '000000', 8, 'DC0207', '流痕', 2, '1', 207, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (16, '000000', 8, 'DC0208', '缩水', 2, '1', 208, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (17, '000000', 0, 'DC03', '材料缺陷', 1, '1', 3, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (18, '000000', 17, 'DC0301', '材质不符', 2, '1', 301, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (19, '000000', 17, 'DC0302', '硬度不足', 2, '1', 302, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (20, '000000', 17, 'DC0303', '夹杂', 2, '1', 303, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (21, '000000', 17, 'DC0304', '气孔', 2, '1', 304, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (22, '000000', 17, 'DC0305', '裂纹', 2, '1', 305, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (23, '000000', 0, 'DC04', '焊接缺陷', 1, '1', 4, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (24, '000000', 23, 'DC0401', '未焊透', 2, '1', 401, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (25, '000000', 23, 'DC0402', '咬边', 2, '1', 402, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (26, '000000', 23, 'DC0403', '气孔', 2, '1', 403, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (27, '000000', 23, 'DC0404', '焊瘤', 2, '1', 404, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (28, '000000', 23, 'DC0405', '焊缝偏移', 2, '1', 405, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (29, '000000', 0, 'DC05', '电镀涂装缺陷', 1, '1', 5, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (30, '000000', 29, 'DC0501', '镀层起泡', 2, '1', 501, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (31, '000000', 29, 'DC0502', '镀层脱落', 2, '1', 502, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (32, '000000', 29, 'DC0503', '镀层厚度不足', 2, '1', 503, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (33, '000000', 29, 'DC0504', '喷涂流挂', 2, '1', 504, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (34, '000000', 29, 'DC0505', '喷涂漏底', 2, '1', 505, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (35, '000000', 29, 'DC0506', '附着力不足', 2, '1', 506, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (36, '000000', 0, 'DC06', '注塑缺陷', 1, '1', 6, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (37, '000000', 36, 'DC0601', '欠注', 2, '1', 601, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (38, '000000', 36, 'DC0602', '飞边', 2, '1', 602, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (39, '000000', 36, 'DC0603', '熔接痕', 2, '1', 603, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (40, '000000', 36, 'DC0604', '银纹', 2, '1', 604, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (41, '000000', 36, 'DC0605', '变形', 2, '1', 605, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (42, '000000', 0, 'DC07', '电子功能缺陷', 1, '1', 7, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (43, '000000', 42, 'DC0701', '不开机', 2, '1', 701, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (44, '000000', 42, 'DC0702', '功能失效', 2, '1', 702, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (45, '000000', 42, 'DC0703', '通信异常', 2, '1', 703, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (46, '000000', 42, 'DC0704', '参数漂移', 2, '1', 704, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (47, '000000', 42, 'DC0705', '短路', 2, '1', 705, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (48, '000000', 42, 'DC0706', '虚焊', 2, '1', 706, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (49, '000000', 42, 'DC0707', '固件版本错误', 2, '1', 707, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (50, '000000', 0, 'DC08', '装配缺陷', 1, '1', 8, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (51, '000000', 50, 'DC0801', '漏装', 2, '1', 801, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (52, '000000', 50, 'DC0802', '错装', 2, '1', 802, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (53, '000000', 50, 'DC0803', '装配不到位', 2, '1', 803, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (54, '000000', 50, 'DC0804', '扭矩不足', 2, '1', 804, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (55, '000000', 50, 'DC0805', '间隙超差', 2, '1', 805, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (56, '000000', 0, 'DC09', '包装标识缺陷', 1, '1', 9, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (57, '000000', 56, 'DC0901', '标签错误', 2, '1', 901, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (58, '000000', 56, 'DC0902', '包装破损', 2, '1', 902, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (59, '000000', 56, 'DC0903', '缺少附件', 2, '1', 903, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (60, '000000', 56, 'DC0904', '说明书缺失', 2, '1', 904, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `ncr_defect_code` VALUES (61, '000000', 56, 'DC0905', '条码无法识别', 2, '1', 905, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for ncr_order
-- ----------------------------
DROP TABLE IF EXISTS `ncr_order`;
CREATE TABLE `ncr_order`  (
  `ncr_id` bigint(20) NOT NULL COMMENT 'NCR ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NCR编号',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型(IQC/IPQC/OQC/PROCESS/CUSTOMER)',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID(iqc_id/ipqc_id/oqc_id/step_record_id)',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID(PROCESS来源)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '工序记录ID(PROCESS来源)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(IPQC/PROCESS来源)',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `lot_id` bigint(20) NULL DEFAULT NULL COMMENT '关联批次ID(隔离用)',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码(冗余)',
  `defect_qty` decimal(14, 4) NOT NULL COMMENT '不良数量',
  `disposition` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处置(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OPEN' COMMENT 'OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED',
  `review_by` bigint(20) NULL DEFAULT NULL COMMENT '评审人',
  `review_time` datetime(0) NULL DEFAULT NULL COMMENT '评审时间',
  `disposition_by` bigint(20) NULL DEFAULT NULL COMMENT '处置人',
  `disposition_time` datetime(0) NULL DEFAULT NULL COMMENT '处置时间',
  `disposition_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '处置签名ID(P0-6预留)',
  `created_time` datetime(0) NOT NULL COMMENT '创建时间',
  `closed_time` datetime(0) NULL DEFAULT NULL COMMENT '关闭时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`ncr_id`) USING BTREE,
  UNIQUE INDEX `uk_ncr_code`(`ncr_code`) USING BTREE,
  UNIQUE INDEX `uk_source`(`source_type`, `source_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR不合格品单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ncr_rework_order
-- ----------------------------
DROP TABLE IF EXISTS `ncr_rework_order`;
CREATE TABLE `ncr_rework_order`  (
  `rework_id` bigint(20) NOT NULL COMMENT '返工记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rework_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '返工工单编号',
  `ncr_id` bigint(20) NOT NULL COMMENT '关联NCR ID',
  `original_wo_id` bigint(20) NOT NULL COMMENT '原工单ID',
  `original_wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原工单编号(冗余)',
  `rework_wo_id` bigint(20) NOT NULL COMMENT '生成的返工工单ID(pro_work_order.mes_wo_id)',
  `rework_wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '返工工单编号(冗余)',
  `rework_qty` decimal(14, 4) NOT NULL COMMENT '返工数量',
  `rework_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '返工原因',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/COMPLETED',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`rework_id`) USING BTREE,
  UNIQUE INDEX `uk_rework_code`(`rework_code`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE,
  INDEX `idx_rework_wo_id`(`rework_wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR返工工单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_andon
-- ----------------------------
DROP TABLE IF EXISTS `pro_andon`;
CREATE TABLE `pro_andon`  (
  `andon_id` bigint(20) NOT NULL COMMENT '安灯ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `andon_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '安灯编号',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `station_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位编码(设备通道呼叫)',
  `call_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
  `call_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫原因',
  `andon_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
  `caller_id` bigint(20) NULL DEFAULT NULL COMMENT '呼叫人ID',
  `caller_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫人姓名',
  `responder_id` bigint(20) NULL DEFAULT NULL COMMENT '响应人ID',
  `responder_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '响应人姓名',
  `call_time` datetime(0) NULL DEFAULT NULL COMMENT '呼叫时间',
  `respond_time` datetime(0) NULL DEFAULT NULL COMMENT '响应时间',
  `resolve_time` datetime(0) NULL DEFAULT NULL COMMENT '解决时间',
  `duration_minutes` int(11) NULL DEFAULT NULL COMMENT '持续时长(分钟, 解决时计算)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`andon_id`) USING BTREE,
  UNIQUE INDEX `uk_andon_no`(`andon_no`) USING BTREE,
  INDEX `idx_andon_status`(`andon_status`) USING BTREE,
  INDEX `idx_call_type`(`call_type`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_station_code`(`station_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES安灯呼叫' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_dispatch
-- ----------------------------
DROP TABLE IF EXISTS `pro_dispatch`;
CREATE TABLE `pro_dispatch`  (
  `dispatch_id` bigint(20) NOT NULL COMMENT '派工单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `dispatch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '派工单编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `workcenter_id` bigint(20) NULL DEFAULT NULL,
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID(SchWorker.workerId)',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '派工数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `dispatch_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `plan_start_time` datetime(0) NULL DEFAULT NULL,
  `plan_end_time` datetime(0) NULL DEFAULT NULL,
  `actual_start_time` datetime(0) NULL DEFAULT NULL,
  `actual_end_time` datetime(0) NULL DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`dispatch_id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_no`(`dispatch_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_step_record_id`(`step_record_id`) USING BTREE,
  INDEX `idx_operator_id`(`operator_id`) USING BTREE,
  INDEX `idx_dispatch_status`(`dispatch_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES派工单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_flow_card
-- ----------------------------
DROP TABLE IF EXISTS `pro_flow_card`;
CREATE TABLE `pro_flow_card`  (
  `card_id` bigint(20) NOT NULL COMMENT '流转卡ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '流转卡编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `current_step_id` bigint(20) NULL DEFAULT NULL COMMENT '当前工序步骤记录ID',
  `current_step_seq` int(4) NULL DEFAULT NULL COMMENT '当前工序序号',
  `card_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CREATED' COMMENT '流转卡状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
  `barcode` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡条码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`card_id`) USING BTREE,
  UNIQUE INDEX `uk_card_no`(`card_no`) USING BTREE,
  UNIQUE INDEX `uk_barcode`(`barcode`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_status`(`card_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES流转卡表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_flow_card_step
-- ----------------------------
DROP TABLE IF EXISTS `pro_flow_card_step`;
CREATE TABLE `pro_flow_card_step`  (
  `step_record_id` bigint(20) NOT NULL COMMENT '工序记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `card_id` bigint(20) NOT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID(冗余便于查询)',
  `wo_step_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单工序步骤ID(pro_work_order_step.step_id)',
  `sequence_num` int(4) NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(来自ERP bd_routing_step)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
  `step_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `actual_start_time` datetime(0) NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime(0) NULL DEFAULT NULL COMMENT '实际结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `entry_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验结果(PASS/FAIL)',
  `entry_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验备注(失败原因)',
  `exit_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验结果(PASS/FAIL)',
  `exit_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验备注(失败原因)',
  `skip_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳站原因',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`step_record_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_step_status`(`step_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES流转卡工序记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_sn
-- ----------------------------
DROP TABLE IF EXISTS `pro_sn`;
CREATE TABLE `pro_sn`  (
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN序列号编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `current_step_seq` int(11) NULL DEFAULT NULL COMMENT '当前工序序号',
  `sn_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
  `parent_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
  `parent_sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父SN编码(冗余)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
  `production_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID(委外=subcontract_id)',
  `subcontract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '委外单号(冗余,SN直达追溯委外订单)',
  PRIMARY KEY (`sn_id`) USING BTREE,
  UNIQUE INDEX `uk_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_sn_status`(`sn_status`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_parent_sn_id`(`parent_sn_id`) USING BTREE,
  INDEX `idx_subcontract_no`(`tenant_id`, `subcontract_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SN序列号追溯' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_step_check_rule
-- ----------------------------
DROP TABLE IF EXISTS `pro_step_check_rule`;
CREATE TABLE `pro_step_check_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `check_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '校验类型',
  `check_stage` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '校验阶段: ENTRY/EXIT',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(0停用 1启用)',
  `error_msg` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '校验失败提示',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  INDEX `idx_process_id`(`process_id`) USING BTREE,
  INDEX `idx_check_stage`(`check_stage`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工序校验规则配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_step_guide
-- ----------------------------
DROP TABLE IF EXISTS `pro_step_guide`;
CREATE TABLE `pro_step_guide`  (
  `guide_id` bigint(20) NOT NULL COMMENT '指引ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID(ERP bd_process.process_id)',
  `guide_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '指引类型: TEXT/SOP_LINK',
  `guide_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指引标题',
  `guide_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '文本内容(TEXT类型用)',
  `sop_id` bigint(20) NULL DEFAULT NULL COMMENT '关联SOP文档(SOP_LINK类型用)',
  `sort_order` int(4) NULL DEFAULT 0 COMMENT '排序',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(0停用 1启用)',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`guide_id`) USING BTREE,
  INDEX `idx_process_id`(`process_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工序流程指引配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_work_order
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order`;
CREATE TABLE `pro_work_order`  (
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `erp_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联ERP工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'MES工单编号(继承或派生)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品名称(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位ID',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `plan_start_date` datetime(0) NULL DEFAULT NULL COMMENT '计划开工日期',
  `plan_end_date` datetime(0) NULL DEFAULT NULL COMMENT '计划完工日期',
  `actual_start_date` datetime(0) NULL DEFAULT NULL COMMENT '实际开工时间',
  `actual_end_date` datetime(0) NULL DEFAULT NULL COMMENT '实际完工时间',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称(冗余)',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联设备ID',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺路线ID(MES侧维护)',
  `wo_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CREATED' COMMENT '工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED',
  `priority` int(4) NULL DEFAULT 5 COMMENT '优先级(1最高,9最低)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ERP' COMMENT '工单来源(ERP/REWORK/MANUAL)',
  `release_package_id` bigint(20) NULL DEFAULT NULL COMMENT '发布包ID(mes_release_package, 工单按此包生成)',
  `release_package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布包编码(冗余)',
  `release_package_ver` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布包版本(冗余快照)',
  `release_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布包配置项快照(JSON数组, 供工位/报表消费)',
  PRIMARY KEY (`mes_wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_erp_wo_id`(`erp_wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_work_order_bom_line
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_bom_line`;
CREATE TABLE `pro_work_order_bom_line`  (
  `wo_bom_line_id` bigint(20) NOT NULL COMMENT '快照明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `mbom_line_id` bigint(20) NULL DEFAULT NULL COMMENT '源mes_mbom_line.mbom_line_id(可空,无MBOM时)',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP BOM ID',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP明细ID(溯源)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `total_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '含损耗用量 = qty × (1 + loss/100)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位编码(冗余)',
  `workstation_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工位名称(冗余)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`wo_bom_line_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单BOM明细快照' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pro_work_order_step
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_step`;
CREATE TABLE `pro_work_order_step`  (
  `step_id` bigint(20) NOT NULL COMMENT '工序步骤ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '所属MES工单ID',
  `sequence_num` int(4) NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
  `step_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '工序类型: GENERAL/FLASH/TEST',
  `content_ref_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容引用类型(单类型快捷绑定: FIRMWARE/CONFIG/MAC_POOL...)',
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件类=mes_firmware_id)',
  `step_flash_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '多类型绑定JSON: [{\"flashTypeCode\":\"firmware_file\",\"contentRefId\":123},{\"flashTypeCode\":\"mac\"}]',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP工序ID(bd_routing_step.routing_step_id)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `step_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  `firmware_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件来源: PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL',
  PRIMARY KEY (`step_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_step_status`(`step_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_step_type`(`tenant_id`, `step_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单工序步骤表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qc_inspect_detail
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_detail`;
CREATE TABLE `qc_inspect_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '检验明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `check_id` bigint(20) NOT NULL COMMENT '检验单ID(qc_iqc.iqc_id/qc_ipqc.ipqc_id/qc_oqc.oqc_id)',
  `check_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验类别: IQC/IPQC/OQC',
  `template_id` bigint(20) NULL DEFAULT NULL COMMENT '来源模板ID(快照溯源)',
  `item_id` bigint(20) NULL DEFAULT NULL COMMENT '来源检验项目ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称(快照)',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型(快照): QUANTITATIVE/QUALITATIVE',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型(快照): NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL',
  `standard_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准快照描述(如10±0.05mm)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(快照)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度(快照): CRITICAL/MAJOR/MINOR',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '样本量',
  `measured_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测值(定量:数值/多值; 定性:OK/NG等)',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(快照, 判定用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下限(快照, 判定用)',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上限(快照, 判定用)',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(快照, 判定用)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(快照, 判定用)',
  `judge_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '判定结果: PASS/FAIL/PENDING',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_check`(`check_type`, `check_id`) USING BTREE,
  INDEX `idx_check_id`(`check_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验单明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qc_inspect_item
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_item`;
CREATE TABLE `qc_inspect_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '检验项目ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_id` bigint(20) NOT NULL COMMENT '关联qc_inspect_template.template_id',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型: QUANTITATIVE定量/QUALITATIVE定性',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计量单位',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型: NOMINAL名义值/LSL_ONLY/USL_ONLY/LSL_USL',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(NOMINAL用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下规格限LSL',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上规格限USL',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(NOMINAL用, 名义+tolUpper)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(NOMINAL用, 名义-tolLower)',
  `aql_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验水平(如II)',
  `sample_plan_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '抽样方案(如AQL1.0)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度: CRITICAL关键(一票否决)/MAJOR/MINOR',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '是否启用 1是0否',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`item_id`) USING BTREE,
  INDEX `idx_template_id`(`template_id`) USING BTREE,
  INDEX `idx_item_code`(`item_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验项目' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qc_inspect_item
-- ----------------------------
INSERT INTO `qc_inspect_item` VALUES (1, '000000', 1, 'IT0001', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (2, '000000', 1, 'IT0002', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (3, '000000', 1, 'IT0003', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (4, '000000', 1, 'IT0004', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (5, '000000', 1, 'IT0005', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (6, '000000', 1, 'IT0006', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (7, '000000', 2, 'IT0007', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (8, '000000', 2, 'IT0008', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (9, '000000', 2, 'IT0009', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (10, '000000', 2, 'IT0010', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (11, '000000', 2, 'IT0011', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (12, '000000', 2, 'IT0012', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (13, '000000', 3, 'IT0013', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (14, '000000', 3, 'IT0014', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (15, '000000', 3, 'IT0015', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (16, '000000', 3, 'IT0016', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (17, '000000', 3, 'IT0017', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (18, '000000', 3, 'IT0018', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (19, '000000', 4, 'IT0019', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (20, '000000', 4, 'IT0020', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (21, '000000', 4, 'IT0021', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (22, '000000', 4, 'IT0022', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (23, '000000', 4, 'IT0023', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (24, '000000', 4, 'IT0024', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (25, '000000', 5, 'IT0025', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (26, '000000', 5, 'IT0026', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (27, '000000', 5, 'IT0027', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (28, '000000', 5, 'IT0028', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (29, '000000', 5, 'IT0029', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (30, '000000', 5, 'IT0030', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (31, '000000', 6, 'IT0031', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (32, '000000', 6, 'IT0032', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (33, '000000', 6, 'IT0033', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (34, '000000', 6, 'IT0034', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (35, '000000', 6, 'IT0035', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (36, '000000', 6, 'IT0036', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (37, '000000', 7, 'IT0037', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (38, '000000', 7, 'IT0038', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (39, '000000', 7, 'IT0039', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (40, '000000', 7, 'IT0040', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (41, '000000', 7, 'IT0041', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (42, '000000', 7, 'IT0042', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (43, '000000', 8, 'IT0043', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (44, '000000', 8, 'IT0044', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (45, '000000', 8, 'IT0045', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (46, '000000', 8, 'IT0046', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (47, '000000', 8, 'IT0047', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (48, '000000', 8, 'IT0048', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (49, '000000', 9, 'IT0049', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (50, '000000', 9, 'IT0050', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (51, '000000', 9, 'IT0051', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (52, '000000', 9, 'IT0052', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (53, '000000', 9, 'IT0053', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (54, '000000', 9, 'IT0054', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (55, '000000', 10, 'IT0055', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (56, '000000', 10, 'IT0056', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (57, '000000', 10, 'IT0057', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (58, '000000', 10, 'IT0058', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (59, '000000', 10, 'IT0059', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (60, '000000', 10, 'IT0060', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (61, '000000', 11, 'IT0061', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (62, '000000', 11, 'IT0062', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (63, '000000', 11, 'IT0063', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (64, '000000', 11, 'IT0064', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (65, '000000', 11, 'IT0065', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (66, '000000', 11, 'IT0066', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (67, '000000', 12, 'IT0067', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (68, '000000', 12, 'IT0068', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (69, '000000', 12, 'IT0069', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (70, '000000', 12, 'IT0070', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (71, '000000', 12, 'IT0071', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (72, '000000', 12, 'IT0072', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (73, '000000', 13, 'IT0073', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (74, '000000', 13, 'IT0074', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (75, '000000', 13, 'IT0075', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (76, '000000', 13, 'IT0076', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (77, '000000', 13, 'IT0077', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (78, '000000', 13, 'IT0078', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (79, '000000', 14, 'IT0079', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (80, '000000', 14, 'IT0080', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (81, '000000', 14, 'IT0081', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (82, '000000', 14, 'IT0082', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (83, '000000', 14, 'IT0083', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (84, '000000', 14, 'IT0084', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (85, '000000', 15, 'IT0085', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (86, '000000', 15, 'IT0086', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (87, '000000', 15, 'IT0087', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (88, '000000', 15, 'IT0088', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (89, '000000', 15, 'IT0089', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (90, '000000', 15, 'IT0090', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (91, '000000', 16, 'IT0091', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (92, '000000', 16, 'IT0092', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (93, '000000', 16, 'IT0093', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (94, '000000', 16, 'IT0094', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (95, '000000', 16, 'IT0095', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (96, '000000', 16, 'IT0096', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (97, '000000', 17, 'IT0097', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (98, '000000', 17, 'IT0098', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (99, '000000', 17, 'IT0099', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (100, '000000', 17, 'IT0100', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (101, '000000', 17, 'IT0101', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (102, '000000', 17, 'IT0102', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (103, '000000', 18, 'IT0103', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (104, '000000', 18, 'IT0104', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (105, '000000', 18, 'IT0105', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (106, '000000', 18, 'IT0106', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (107, '000000', 18, 'IT0107', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (108, '000000', 18, 'IT0108', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (109, '000000', 19, 'IT0109', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (110, '000000', 19, 'IT0110', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (111, '000000', 19, 'IT0111', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (112, '000000', 19, 'IT0112', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (113, '000000', 19, 'IT0113', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (114, '000000', 19, 'IT0114', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (115, '000000', 20, 'IT0115', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (116, '000000', 20, 'IT0116', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (117, '000000', 20, 'IT0117', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (118, '000000', 20, 'IT0118', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (119, '000000', 20, 'IT0119', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (120, '000000', 20, 'IT0120', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (121, '000000', 21, 'IT0121', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (122, '000000', 21, 'IT0122', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (123, '000000', 21, 'IT0123', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (124, '000000', 21, 'IT0124', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (125, '000000', 21, 'IT0125', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (126, '000000', 21, 'IT0126', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (127, '000000', 22, 'IT0127', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (128, '000000', 22, 'IT0128', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (129, '000000', 22, 'IT0129', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (130, '000000', 22, 'IT0130', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (131, '000000', 22, 'IT0131', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (132, '000000', 22, 'IT0132', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (133, '000000', 23, 'IT0133', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (134, '000000', 23, 'IT0134', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (135, '000000', 23, 'IT0135', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (136, '000000', 23, 'IT0136', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (137, '000000', 23, 'IT0137', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (138, '000000', 23, 'IT0138', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (139, '000000', 24, 'IT0139', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (140, '000000', 24, 'IT0140', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (141, '000000', 24, 'IT0141', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (142, '000000', 24, 'IT0142', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (143, '000000', 24, 'IT0143', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (144, '000000', 24, 'IT0144', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (145, '000000', 25, 'IT0145', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (146, '000000', 25, 'IT0146', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (147, '000000', 25, 'IT0147', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (148, '000000', 25, 'IT0148', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (149, '000000', 25, 'IT0149', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (150, '000000', 25, 'IT0150', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (151, '000000', 26, 'IT0151', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (152, '000000', 26, 'IT0152', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (153, '000000', 26, 'IT0153', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (154, '000000', 26, 'IT0154', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (155, '000000', 26, 'IT0155', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (156, '000000', 26, 'IT0156', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (157, '000000', 27, 'IT0157', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (158, '000000', 27, 'IT0158', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (159, '000000', 27, 'IT0159', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (160, '000000', 27, 'IT0160', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (161, '000000', 27, 'IT0161', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (162, '000000', 27, 'IT0162', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (163, '000000', 28, 'IT0163', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (164, '000000', 28, 'IT0164', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (165, '000000', 28, 'IT0165', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (166, '000000', 28, 'IT0166', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (167, '000000', 28, 'IT0167', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (168, '000000', 28, 'IT0168', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (169, '000000', 29, 'IT0169', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (170, '000000', 29, 'IT0170', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (171, '000000', 29, 'IT0171', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (172, '000000', 29, 'IT0172', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (173, '000000', 29, 'IT0173', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (174, '000000', 29, 'IT0174', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (175, '000000', 30, 'IT0175', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (176, '000000', 30, 'IT0176', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (177, '000000', 30, 'IT0177', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (178, '000000', 30, 'IT0178', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (179, '000000', 30, 'IT0179', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (180, '000000', 30, 'IT0180', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (181, '000000', 31, 'IT0181', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (182, '000000', 31, 'IT0182', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (183, '000000', 31, 'IT0183', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (184, '000000', 31, 'IT0184', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (185, '000000', 31, 'IT0185', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (186, '000000', 31, 'IT0186', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (187, '000000', 32, 'IT0187', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (188, '000000', 32, 'IT0188', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (189, '000000', 32, 'IT0189', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (190, '000000', 32, 'IT0190', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (191, '000000', 32, 'IT0191', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (192, '000000', 32, 'IT0192', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (193, '000000', 33, 'IT0193', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (194, '000000', 33, 'IT0194', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (195, '000000', 33, 'IT0195', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (196, '000000', 33, 'IT0196', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (197, '000000', 33, 'IT0197', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (198, '000000', 33, 'IT0198', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (199, '000000', 34, 'IT0199', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (200, '000000', 34, 'IT0200', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (201, '000000', 34, 'IT0201', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (202, '000000', 34, 'IT0202', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (203, '000000', 34, 'IT0203', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (204, '000000', 34, 'IT0204', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (205, '000000', 35, 'IT0205', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (206, '000000', 35, 'IT0206', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (207, '000000', 35, 'IT0207', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (208, '000000', 35, 'IT0208', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (209, '000000', 35, 'IT0209', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (210, '000000', 35, 'IT0210', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (211, '000000', 36, 'IT0211', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (212, '000000', 36, 'IT0212', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (213, '000000', 36, 'IT0213', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (214, '000000', 36, 'IT0214', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (215, '000000', 36, 'IT0215', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (216, '000000', 36, 'IT0216', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (217, '000000', 37, 'IT0217', '外观', 'VISUAL', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MINOR', 1, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (218, '000000', 37, 'IT0218', '尺寸', 'DIMENSION', 'mm', 'RANGE', NULL, -0.020000, 0.020000, NULL, NULL, NULL, NULL, 'MAJOR', 2, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (219, '000000', 37, 'IT0219', '硬度', 'NUMBER', 'HRC', 'RANGE', NULL, 55.000000, 62.000000, NULL, NULL, NULL, NULL, 'MINOR', 3, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (220, '000000', 37, 'IT0220', '功能', 'BOOLEAN', NULL, 'BOOLEAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'MAJOR', 4, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (221, '000000', 37, 'IT0221', '绝缘电阻', 'NUMBER', 'MΩ', 'BOOLEAN', NULL, 100.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 5, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_item` VALUES (222, '000000', 37, 'IT0222', '耐压', 'NUMBER', 'V', 'BOOLEAN', NULL, 1500.000000, NULL, NULL, NULL, NULL, NULL, 'MINOR', 6, '1', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for qc_inspect_template
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_template`;
CREATE TABLE `qc_inspect_template`  (
  `template_id` bigint(20) NOT NULL COMMENT '检验模板ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码(唯一)',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `template_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板类型: IQC/IPQC/OQC',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID(空=通用模板)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '版本号',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`template_id`) USING BTREE,
  UNIQUE INDEX `uk_template_code`(`template_code`) USING BTREE,
  INDEX `idx_template_type`(`template_type`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验模板' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qc_inspect_template
-- ----------------------------
INSERT INTO `qc_inspect_template` VALUES (1, '000000', 'QC20260914001', '高强度螺栓组套 M12成品检验规范', 'OQC', 1, 'IF20260914001', '高强度螺栓组套 M12', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (2, '000000', 'QC20260914002', '精密齿轮箱总成成品检验规范', 'OQC', 2, 'IF20260914002', '精密齿轮箱总成', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (3, '000000', 'QC20260914003', '压缩弹簧组成品检验规范', 'OQC', 3, 'IF20260914003', '压缩弹簧组', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (4, '000000', 'QC20260914004', '设备钣金机箱成品检验规范', 'OQC', 4, 'IF20260914004', '设备钣金机箱', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (5, '000000', 'QC20260914005', '钢结构支架总成成品检验规范', 'OQC', 5, 'IF20260914005', '钢结构支架总成', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (6, '000000', 'QC20260914006', '注塑模具成品检验规范', 'OQC', 6, 'IF20260914006', '注塑模具', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (7, '000000', 'QC20260914007', '棘轮扳手组套成品检验规范', 'OQC', 7, 'IF20260914007', '棘轮扳手组套', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (8, '000000', 'QC20260914008', '齿轮减速机 JZQ-350成品检验规范', 'OQC', 8, 'IF20260914008', '齿轮减速机 JZQ-350', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (9, '000000', 'QC20260914009', '单级离心泵成品检验规范', 'OQC', 9, 'IF20260914009', '单级离心泵', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (10, '000000', 'QC20260914010', '数控铣床 XK714成品检验规范', 'OQC', 10, 'IF20260914010', '数控铣床 XK714', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (11, '000000', 'QC20260914011', '皮带输送线成品检验规范', 'OQC', 11, 'IF20260914011', '皮带输送线', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (12, '000000', 'QC20260914012', '螺杆式空压机成品检验规范', 'OQC', 12, 'IF20260914012', '螺杆式空压机', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (13, '000000', 'QC20260914013', '双层 PCB 板成品检验规范', 'OQC', 13, 'IF20260914013', '双层 PCB 板', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (14, '000000', 'QC20260914014', '智能路由器 WR-1200成品检验规范', 'OQC', 14, 'IF20260914014', '智能路由器 WR-1200', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (15, '000000', 'QC20260914015', '变频电风扇成品检验规范', 'OQC', 15, 'IF20260914015', '变频电风扇', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (16, '000000', 'QC20260914016', '开关电源模组 SMPS-60成品检验规范', 'OQC', 16, 'IF20260914016', '开关电源模组 SMPS-60', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (17, '000000', 'QC20260914017', '无刷直流电机 BLDC-45成品检验规范', 'OQC', 17, 'IF20260914017', '无刷直流电机 BLDC-45', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (18, '000000', 'QC20260914018', '温度传感器模组 TS-100成品检验规范', 'OQC', 18, 'IF20260914018', '温度传感器模组 TS-100', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (19, '000000', 'QC20260914019', '数字万用表 DM-800成品检验规范', 'OQC', 19, 'IF20260914019', '数字万用表 DM-800', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (20, '000000', 'QC20260914020', '汽车门锁总成成品检验规范', 'OQC', 20, 'IF20260914020', '汽车门锁总成', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (21, '000000', 'QC20260914021', '城市通勤自行车成品检验规范', 'OQC', 21, 'IF20260914021', '城市通勤自行车', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (22, '000000', 'QC20260914022', '四轴无人机机架成品检验规范', 'OQC', 22, 'IF20260914022', '四轴无人机机架', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (23, '000000', 'QC20260914023', '不锈钢手术剪成品检验规范', 'OQC', 23, 'IF20260914023', '不锈钢手术剪', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (24, '000000', 'QC20260914024', '手动轮椅成品检验规范', 'OQC', 24, 'IF20260914024', '手动轮椅', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (25, '000000', 'QC20260914025', '简易金属工具架 RACK-001成品检验规范', 'OQC', 25, 'IF20260914025', '简易金属工具架 RACK-001', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (26, '000000', 'QC20260914026', 'LED 护眼台灯成品检验规范', 'OQC', 26, 'IF20260914026', 'LED 护眼台灯', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (27, '000000', 'QC20260914027', '智能网关 GW-1000成品检验规范', 'OQC', 27, 'IF20260914027', '智能网关 GW-1000', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (28, '000000', 'QC20260914028', '精密塑料外壳 PC-200成品检验规范', 'OQC', 28, 'IF20260914028', '精密塑料外壳 PC-200', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (29, '000000', 'QC20260914029', '硅胶密封圈组成品检验规范', 'OQC', 29, 'IF20260914029', '硅胶密封圈组', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (30, '000000', 'QC20260914030', '锂电池包 48V20Ah成品检验规范', 'OQC', 30, 'IF20260914030', '锂电池包 48V20Ah', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (31, '000000', 'QC20260914031', '交流充电桩 AC-7kW成品检验规范', 'OQC', 31, 'IF20260914031', '交流充电桩 AC-7kW', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (32, '000000', 'QC20260914032', '光伏组件 450W成品检验规范', 'OQC', 32, 'IF20260914032', '光伏组件 450W', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (33, '000000', 'QC20260914033', '六轴机械臂 RB-6成品检验规范', 'OQC', 33, 'IF20260914033', '六轴机械臂 RB-6', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (34, '000000', 'QC20260914034', '智能手环 BAND-3成品检验规范', 'OQC', 34, 'IF20260914034', '智能手环 BAND-3', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (35, '000000', 'QC20260914035', '手持 POS 机 POS-8成品检验规范', 'OQC', 35, 'IF20260914035', '手持 POS 机 POS-8', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (36, '000000', 'QC20260914036', '网络摄像机 IPC-300成品检验规范', 'OQC', 36, 'IF20260914036', '网络摄像机 IPC-300', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `qc_inspect_template` VALUES (37, '000000', 'QC20260914037', '无线蓝牙音箱 SPK-20成品检验规范', 'OQC', 37, 'IF20260914037', '无线蓝牙音箱 SPK-20', 1, '1', '按产品族通用检验要求生成', 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for qc_ipqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_ipqc`;
CREATE TABLE `qc_ipqc`  (
  `ipqc_id` bigint(20) NOT NULL COMMENT 'IPQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `ipqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IPQC编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `defect_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '不良数量',
  `defect_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '不良率(%)',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)',
  PRIMARY KEY (`ipqc_id`) USING BTREE,
  UNIQUE INDEX `uk_ipqc_no`(`ipqc_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES过程检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qc_iqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_iqc`;
CREATE TABLE `qc_iqc`  (
  `iqc_id` bigint(20) NOT NULL COMMENT 'IQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `iqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IQC编号',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP收货单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)',
  PRIMARY KEY (`iqc_id`) USING BTREE,
  UNIQUE INDEX `uk_iqc_no`(`iqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_receive_id`(`receive_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES来料检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qc_oqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_oqc`;
CREATE TABLE `qc_oqc`  (
  `oqc_id` bigint(20) NOT NULL COMMENT 'OQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `oqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OQC编号',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP销售订单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `photo_oss_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验照片附件ossId串(逗号分隔)',
  PRIMARY KEY (`oqc_id`) USING BTREE,
  UNIQUE INDEX `uk_oqc_no`(`oqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES出货检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sop_document
-- ----------------------------
DROP TABLE IF EXISTS `sop_document`;
CREATE TABLE `sop_document`  (
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SOP编码',
  `sop_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SOP名称',
  `sop_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN',
  `current_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '当前发布版本号',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED',
  `file_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '当前发布文件OSS ID(私有桶存ID)',
  `file_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件类型: PDF/IMAGE/VIDEO/WORD',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `approval_by` bigint(20) NULL DEFAULT NULL COMMENT '审批人ID',
  `approval_time` datetime(0) NULL DEFAULT NULL COMMENT '审批时间',
  `approval_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审批意见',
  `archive_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '归档原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`sop_id`) USING BTREE,
  UNIQUE INDEX `uk_sop_code`(`sop_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`sop_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SOP文档主档' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_document
-- ----------------------------
INSERT INTO `sop_document` VALUES (1, '000000', 'SOP20260914001', '激光切割作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '激光切割标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (2, '000000', 'SOP20260914002', '等离子切割作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '等离子切割标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (3, '000000', 'SOP20260914003', '剪板作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '剪板标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (4, '000000', 'SOP20260914004', '锯切作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '锯切标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (5, '000000', 'SOP20260914005', '冲压作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '冲压标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (6, '000000', 'SOP20260914006', '折弯作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '折弯标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (7, '000000', 'SOP20260914007', '卷圆作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '卷圆标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (8, '000000', 'SOP20260914008', '拉深作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '拉深标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (9, '000000', 'SOP20260914009', '锻造作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '锻造标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (10, '000000', 'SOP20260914010', '车削作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '车削标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (11, '000000', 'SOP20260914011', '铣削作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '铣削标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (12, '000000', 'SOP20260914012', '钻孔作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '钻孔标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (13, '000000', 'SOP20260914013', '磨削作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '磨削标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (14, '000000', 'SOP20260914014', '镗孔作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '镗孔标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (15, '000000', 'SOP20260914015', '滚齿作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '滚齿标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (16, '000000', 'SOP20260914016', '插齿作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '插齿标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (17, '000000', 'SOP20260914017', '线切割作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '线切割标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (18, '000000', 'SOP20260914018', '电火花作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '电火花标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (19, '000000', 'SOP20260914019', '铸造作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '铸造标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (20, '000000', 'SOP20260914020', '压铸作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '压铸标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (21, '000000', 'SOP20260914021', '注塑作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '注塑标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (22, '000000', 'SOP20260914022', '吹塑作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '吹塑标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (23, '000000', 'SOP20260914023', '氩弧焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '氩弧焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (24, '000000', 'SOP20260914024', '二保焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '二保焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (25, '000000', 'SOP20260914025', '点焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '点焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (26, '000000', 'SOP20260914026', '激光焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '激光焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (27, '000000', 'SOP20260914027', '淬火作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '淬火标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (28, '000000', 'SOP20260914028', '回火作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '回火标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (29, '000000', 'SOP20260914029', '渗碳作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '渗碳标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (30, '000000', 'SOP20260914030', '时效作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '时效标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (31, '000000', 'SOP20260914031', '电镀作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '电镀标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (32, '000000', 'SOP20260914032', '阳极氧化作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '阳极氧化标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (33, '000000', 'SOP20260914033', '喷塑作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '喷塑标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (34, '000000', 'SOP20260914034', '喷漆作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '喷漆标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (35, '000000', 'SOP20260914035', '丝印作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '丝印标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (36, '000000', 'SOP20260914036', '抛光作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '抛光标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (37, '000000', 'SOP20260914037', 'SMT贴片作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', 'SMT贴片标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (38, '000000', 'SOP20260914038', '插件作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '插件标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (39, '000000', 'SOP20260914039', '波峰焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '波峰焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (40, '000000', 'SOP20260914040', '手工焊作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '手工焊标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (41, '000000', 'SOP20260914041', '绕线作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '绕线标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (42, '000000', 'SOP20260914042', '浸漆作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '浸漆标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (43, '000000', 'SOP20260914043', '固件刷写作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '固件刷写标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (44, '000000', 'SOP20260914044', '装配作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '装配标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (45, '000000', 'SOP20260914045', '压装作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '压装标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (46, '000000', 'SOP20260914046', '点胶作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '点胶标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (47, '000000', 'SOP20260914047', '功能测试作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '功能测试标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (48, '000000', 'SOP20260914048', '老化作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '老化标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (49, '000000', 'SOP20260914049', '气密测试作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '气密测试标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (50, '000000', 'SOP20260914050', '精度检验作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '精度检验标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (51, '000000', 'SOP20260914051', '动平衡作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '动平衡标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (52, '000000', 'SOP20260914052', 'ICT测试作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', 'ICT测试标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (53, '000000', 'SOP20260914053', '防水测试作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '防水测试标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (54, '000000', 'SOP20260914054', '质检作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '质检标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (55, '000000', 'SOP20260914055', '包装作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '包装标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (56, '000000', 'SOP20260914056', '清洗作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '清洗标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_document` VALUES (57, '000000', 'SOP20260914057', '去毛刺作业指导书', 'PROCESS', 'V1.0', 'PUBLISHED', 0, 'PDF', '去毛刺标准作业流程与关键控制点', 1, '2026-09-14 15:07:27', '初始发布', NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for sop_process_binding
-- ----------------------------
DROP TABLE IF EXISTS `sop_process_binding`;
CREATE TABLE `sop_process_binding`  (
  `binding_id` bigint(20) NOT NULL COMMENT '绑定ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID(ERP bd_process.process_id, 工序主数据PK)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `process_category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING)',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指定版本号(空=当前发布版)',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否该工序默认SOP(1/0)',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`binding_id`) USING BTREE,
  INDEX `idx_process`(`process_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工序SOP绑定' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_process_binding
-- ----------------------------
INSERT INTO `sop_process_binding` VALUES (1, '000000', 1, 'LASER', '激光切割', '下料', 1, '1', '1', 1, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (2, '000000', 2, 'PLASMA', '等离子切割', '下料', 2, '1', '1', 2, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (3, '000000', 3, 'SHEAR', '剪板', '下料', 3, '1', '1', 3, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (4, '000000', 4, 'SAW', '锯切', '下料', 4, '1', '1', 4, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (5, '000000', 5, 'PUNCH', '冲压', '成形', 5, '1', '1', 5, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (6, '000000', 6, 'BEND', '折弯', '成形', 6, '1', '1', 6, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (7, '000000', 7, 'ROLL', '卷圆', '成形', 7, '1', '1', 7, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (8, '000000', 8, 'DRAW', '拉深', '成形', 8, '1', '1', 8, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (9, '000000', 9, 'FORGE', '锻造', '成形', 9, '1', '1', 9, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (10, '000000', 10, 'TURN', '车削', '机加', 10, '1', '1', 10, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (11, '000000', 11, 'MILL', '铣削', '机加', 11, '1', '1', 11, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (12, '000000', 12, 'DRILL', '钻孔', '机加', 12, '1', '1', 12, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (13, '000000', 13, 'GRIND', '磨削', '机加', 13, '1', '1', 13, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (14, '000000', 14, 'BORE', '镗孔', '机加', 14, '1', '1', 14, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (15, '000000', 15, 'HOB', '滚齿', '机加', 15, '1', '1', 15, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (16, '000000', 16, 'SHAPE', '插齿', '机加', 16, '1', '1', 16, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (17, '000000', 17, 'WEDM', '线切割', '机加', 17, '1', '1', 17, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (18, '000000', 18, 'EDM', '电火花', '机加', 18, '1', '1', 18, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (19, '000000', 19, 'CAST', '铸造', '铸造注塑', 19, '1', '1', 19, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (20, '000000', 20, 'DIE_CAST', '压铸', '铸造注塑', 20, '1', '1', 20, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (21, '000000', 21, 'MOLD', '注塑', '铸造注塑', 21, '1', '1', 21, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (22, '000000', 22, 'BLOW', '吹塑', '铸造注塑', 22, '1', '1', 22, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (23, '000000', 23, 'TIG', '氩弧焊', '焊接', 23, '1', '1', 23, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (24, '000000', 24, 'MIG', '二保焊', '焊接', 24, '1', '1', 24, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (25, '000000', 25, 'SPOT', '点焊', '焊接', 25, '1', '1', 25, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (26, '000000', 26, 'LASER_WELD', '激光焊', '焊接', 26, '1', '1', 26, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (27, '000000', 27, 'QUENCH', '淬火', '热处理', 27, '1', '1', 27, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (28, '000000', 28, 'TEMPER', '回火', '热处理', 28, '1', '1', 28, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (29, '000000', 29, 'CARBURIZE', '渗碳', '热处理', 29, '1', '1', 29, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (30, '000000', 30, 'AGE', '时效', '热处理', 30, '1', '1', 30, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (31, '000000', 31, 'PLATE', '电镀', '表面', 31, '1', '1', 31, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (32, '000000', 32, 'ANODIZE', '阳极氧化', '表面', 32, '1', '1', 32, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (33, '000000', 33, 'POWDER', '喷塑', '表面', 33, '1', '1', 33, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (34, '000000', 34, 'PAINT', '喷漆', '表面', 34, '1', '1', 34, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (35, '000000', 35, 'SCREEN', '丝印', '表面', 35, '1', '1', 35, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (36, '000000', 36, 'POLISH', '抛光', '表面', 36, '1', '1', 36, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (37, '000000', 37, 'SMT', 'SMT贴片', '电子', 37, '1', '1', 37, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (38, '000000', 38, 'INSERT', '插件', '电子', 38, '1', '1', 38, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (39, '000000', 39, 'WAVE', '波峰焊', '电子', 39, '1', '1', 39, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (40, '000000', 40, 'SOLDER_MANUAL', '手工焊', '电子', 40, '1', '1', 40, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (41, '000000', 41, 'WINDING', '绕线', '电子', 41, '1', '1', 41, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (42, '000000', 42, 'VARNISH', '浸漆', '电子', 42, '1', '1', 42, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (43, '000000', 43, 'FLASH', '固件刷写', '电子', 43, '1', '1', 43, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (44, '000000', 44, 'ASM', '装配', '装配', 44, '1', '1', 44, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (45, '000000', 45, 'PRESS_FIT', '压装', '装配', 45, '1', '1', 45, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (46, '000000', 46, 'DISPENSE', '点胶', '装配', 46, '1', '1', 46, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (47, '000000', 47, 'FUNC_TEST', '功能测试', '测试', 47, '1', '1', 47, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (48, '000000', 48, 'AGING', '老化', '测试', 48, '1', '1', 48, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (49, '000000', 49, 'WP_TEST', '气密测试', '测试', 49, '1', '1', 49, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (50, '000000', 50, 'PRECISION', '精度检验', '测试', 50, '1', '1', 50, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (51, '000000', 51, 'BALANCE', '动平衡', '测试', 51, '1', '1', 51, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (52, '000000', 52, 'ICT', 'ICT测试', '测试', 52, '1', '1', 52, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (53, '000000', 53, 'WATER_PROOF', '防水测试', '测试', 53, '1', '1', 53, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (54, '000000', 54, 'QC', '质检', '通用', 54, '1', '1', 54, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (55, '000000', 55, 'PKG', '包装', '通用', 55, '1', '1', 55, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (56, '000000', 56, 'CLEAN', '清洗', '通用', 56, '1', '1', 56, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_process_binding` VALUES (57, '000000', 57, 'DEBURR', '去毛刺', '通用', 57, '1', '1', 57, NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for sop_version
-- ----------------------------
DROP TABLE IF EXISTS `sop_version`;
CREATE TABLE `sop_version`  (
  `version_id` bigint(20) NOT NULL COMMENT '版本ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '版本号(如1.0/1.1)',
  `file_oss_id` bigint(20) NOT NULL COMMENT '该版本文件OSS ID',
  `file_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件类型: PDF/IMAGE/VIDEO/WORD',
  `change_log` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更说明',
  `published_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CURRENT/HISTORICAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`version_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'SOP版本历史' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_version
-- ----------------------------
INSERT INTO `sop_version` VALUES (1, '000000', 1, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (2, '000000', 2, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (3, '000000', 3, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (4, '000000', 4, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (5, '000000', 5, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (6, '000000', 6, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (7, '000000', 7, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (8, '000000', 8, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (9, '000000', 9, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (10, '000000', 10, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (11, '000000', 11, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (12, '000000', 12, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (13, '000000', 13, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (14, '000000', 14, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (15, '000000', 15, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (16, '000000', 16, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (17, '000000', 17, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (18, '000000', 18, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (19, '000000', 19, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (20, '000000', 20, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (21, '000000', 21, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (22, '000000', 22, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (23, '000000', 23, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (24, '000000', 24, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (25, '000000', 25, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (26, '000000', 26, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (27, '000000', 27, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (28, '000000', 28, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (29, '000000', 29, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (30, '000000', 30, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (31, '000000', 31, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (32, '000000', 32, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (33, '000000', 33, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (34, '000000', 34, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (35, '000000', 35, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (36, '000000', 36, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (37, '000000', 37, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (38, '000000', 38, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (39, '000000', 39, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (40, '000000', 40, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (41, '000000', 41, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (42, '000000', 42, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (43, '000000', 43, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (44, '000000', 44, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (45, '000000', 45, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (46, '000000', 46, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (47, '000000', 47, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (48, '000000', 48, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (49, '000000', 49, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (50, '000000', 50, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (51, '000000', 51, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (52, '000000', 52, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (53, '000000', 53, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (54, '000000', 54, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (55, '000000', 55, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (56, '000000', 56, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');
INSERT INTO `sop_version` VALUES (57, '000000', 57, 'V1.0', 0, 'PDF', '初始版本', '2026-09-14 15:07:27', 1, 'PUBLISHED', NULL, 103, 1, '2026-09-14 15:07:27', NULL, NULL, '0');

-- ----------------------------
-- Table structure for sop_view_log
-- ----------------------------
DROP TABLE IF EXISTS `sop_view_log`;
CREATE TABLE `sop_view_log`  (
  `log_id` bigint(20) NOT NULL COMMENT '查阅记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sop_id` bigint(20) NOT NULL COMMENT 'SOP ID',
  `version_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查阅的版本号',
  `viewer_id` bigint(20) NOT NULL COMMENT '查阅人ID',
  `viewer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查阅人姓名(冗余)',
  `view_time` datetime(0) NOT NULL COMMENT '查阅时间',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `signature_id` bigint(20) NULL DEFAULT NULL COMMENT '电子签名记录ID(预留,P0-6接入)',
  `ip_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE,
  INDEX `idx_viewer`(`viewer_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'SOP查阅记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for spc_control_chart
-- ----------------------------
DROP TABLE IF EXISTS `spc_control_chart`;
CREATE TABLE `spc_control_chart`  (
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `chart_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '控制图名称',
  `chart_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
  `point_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采集点ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '关联物料ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `ucl` decimal(14, 4) NULL DEFAULT NULL COMMENT '上控制限(analyze计算回写)',
  `cl` decimal(14, 4) NULL DEFAULT NULL COMMENT '中心线',
  `lcl` decimal(14, 4) NULL DEFAULT NULL COMMENT '下控制限',
  `usl` decimal(14, 4) NULL DEFAULT NULL COMMENT '规格上限(USL, 用户维护)',
  `lsl` decimal(14, 4) NULL DEFAULT NULL COMMENT '规格下限(LSL, 用户维护)',
  `cp` decimal(10, 4) NULL DEFAULT NULL COMMENT '过程能力指数Cp(analyze回写)',
  `cpk` decimal(10, 4) NULL DEFAULT NULL COMMENT '过程能力指数Cpk(analyze回写)',
  `cpk_grade` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '能力等级: A+(>=1.67)/A(>=1.33)/B(>=1.0)/C(<1.0)',
  `sample_size` int(11) NULL DEFAULT 5 COMMENT '样本大小',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`chart_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SPC控制图' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for spc_data_point
-- ----------------------------
DROP TABLE IF EXISTS `spc_data_point`;
CREATE TABLE `spc_data_point`  (
  `data_id` bigint(20) NOT NULL COMMENT '数据点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID',
  `sample_group` int(11) NOT NULL COMMENT '样本组号',
  `sample_index` int(11) NULL DEFAULT NULL COMMENT '组内序号',
  `value` decimal(14, 4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
  `is_ooc` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '是否超控制限(0/1)',
  `ooc_rule` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '违规规则(RULE1/2/3/4, 多规则逗号拼接)',
  `collect_time` datetime(0) NULL DEFAULT NULL COMMENT '采集时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_chart_id`(`chart_id`) USING BTREE,
  INDEX `idx_group`(`chart_id`, `sample_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SPC数据点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tg_maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `tg_maintenance_plan`;
CREATE TABLE `tg_maintenance_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计划名称',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发类型: BY_COUNT/BY_HOURS/PERIODIC',
  `trigger_value` int(11) NULL DEFAULT NULL COMMENT '触发阈值(次数/小时/天数)',
  `last_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '上次保养时间',
  `next_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '下次保养时间(PERIODIC)',
  `next_maintain_count` int(11) NULL DEFAULT NULL COMMENT '下次保养次数阈值(BY_COUNT)',
  `next_maintain_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '下次保养时长阈值(BY_HOURS)',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(1/0)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装保养计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tg_maintenance_record
-- ----------------------------
DROP TABLE IF EXISTS `tg_maintenance_record`;
CREATE TABLE `tg_maintenance_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '关联计划ID',
  `maintain_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养类型: PLANNED/REPAIR',
  `maintain_time` datetime(0) NOT NULL COMMENT '保养时间',
  `maintainer_id` bigint(20) NULL DEFAULT NULL COMMENT '保养人ID',
  `maintainer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养人姓名(冗余)',
  `used_count_before` int(11) NULL DEFAULT NULL COMMENT '保养前次数',
  `used_count_after` int(11) NULL DEFAULT NULL COMMENT '保养后次数(归零=0)',
  `used_hours_before` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养前时长',
  `used_hours_after` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养后时长(归零=0)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '保养内容',
  `cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '保养成本',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装保养记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tg_tooling
-- ----------------------------
DROP TABLE IF EXISTS `tg_tooling`;
CREATE TABLE `tg_tooling`  (
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工装编码',
  `tooling_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工装名称',
  `tooling_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: MOLD/FIXTURE/CUTTER/GAUGE/OTHER',
  `spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID',
  `workshop_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '车间名称(冗余)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IDLE' COMMENT '状态: IDLE/IN_USE/MAINTENANCE/SCRAPPED',
  `total_life_count` int(11) NULL DEFAULT NULL COMMENT '总寿命(次数)',
  `total_life_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '总寿命(小时)',
  `used_count` int(11) NULL DEFAULT 0 COMMENT '已使用次数',
  `used_hours` decimal(14, 2) NULL DEFAULT 0.00 COMMENT '已使用时长',
  `last_maintain_time` datetime(0) NULL DEFAULT NULL COMMENT '上次保养时间',
  `purchase_date` date NULL DEFAULT NULL COMMENT '采购日期',
  `purchase_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '采购成本',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`tooling_id`) USING BTREE,
  UNIQUE INDEX `uk_tooling_code`(`tooling_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`tooling_type`) USING BTREE,
  INDEX `idx_workshop`(`workshop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tg_usage_record
-- ----------------------------
DROP TABLE IF EXISTS `tg_usage_record`;
CREATE TABLE `tg_usage_record`  (
  `usage_id` bigint(20) NOT NULL COMMENT '使用记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `tooling_id` bigint(20) NOT NULL COMMENT '工装ID',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `workstation_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位名称(冗余)',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `checkout_time` datetime(0) NOT NULL COMMENT '领用时间',
  `return_time` datetime(0) NULL DEFAULT NULL COMMENT '归还时间',
  `usage_count` int(11) NULL DEFAULT 1 COMMENT '使用次数',
  `usage_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '使用时长',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'OUT' COMMENT '状态: OUT/RETURNED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`usage_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装使用领用记录' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
