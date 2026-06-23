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

 Date: 23/06/2026 17:54:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for dc_collection_point
-- ----------------------------
DROP TABLE IF EXISTS `dc_collection_point`;
CREATE TABLE `dc_collection_point`  (
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '采集点编码',
  `point_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '采集点名称',
  `point_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MANUAL/AUTO',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '关联设备ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `frequency` int(11) NULL DEFAULT NULL COMMENT '采集频率(秒)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`point_id`) USING BTREE,
  UNIQUE INDEX `uk_point_code`(`point_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES数据采集点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dc_collection_point
-- ----------------------------
INSERT INTO `dc_collection_point` VALUES (1, '000000', 'CP-TEMP-001', 'CNC001主轴温度', 'AUTO', 1, 1, 'NUMERIC', '℃', 60, 'ACTIVE', '数控车床#1主轴温度自动采集，每60秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (2, '000000', 'CP-VIB-001', 'CNC001振动', 'AUTO', 1, 1, 'NUMERIC', 'mm/s', 30, 'ACTIVE', '数控车床#1振动值自动采集，每30秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (3, '000000', 'CP-DIM-001', '装配尺寸检验', 'MANUAL', 4, 3, 'NUMERIC', 'mm', NULL, 'ACTIVE', '装配工序关键尺寸人工采集', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (4, '000000', 'CP-WELD-TEMP', '焊接温度监控', 'AUTO', 3, 2, 'NUMERIC', '℃', 10, 'ACTIVE', '焊接温度自动采集，每10秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (5, '000000', 'CP-PKG-CNT', '包装计数', 'AUTO', 5, 5, 'NUMERIC', '件', 60, 'ACTIVE', '包装机产出计数，每60秒', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for dc_data_record
-- ----------------------------
DROP TABLE IF EXISTS `dc_data_record`;
CREATE TABLE `dc_data_record`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `point_id` bigint(20) NOT NULL COMMENT '采集点ID',
  `point_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采集点编码(冗余)',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序记录ID',
  `numeric_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文本值',
  `collect_time` datetime(0) NOT NULL COMMENT '采集时间',
  `collect_by` bigint(20) NULL DEFAULT NULL COMMENT '采集人(手动时)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_collect_time`(`collect_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES采集数据记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dc_data_record
-- ----------------------------
INSERT INTO `dc_data_record` VALUES (1, '000000', 1, 'CP-TEMP-001', 1, 1, 1, 72.5000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (2, '000000', 1, 'CP-TEMP-001', 1, 1, 1, 74.1000, NULL, '2026-06-10 08:20:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (3, '000000', 1, 'CP-TEMP-001', 1, 1, 1, 87.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC主轴温度-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (4, '000000', 1, 'CP-TEMP-001', 1, 1, 1, 73.2000, NULL, '2026-06-10 08:40:00', NULL, 'IoT', 'CNC主轴温度-恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (5, '000000', 1, 'CP-TEMP-001', 1, 1, 1, 75.8000, NULL, '2026-06-10 08:50:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (6, '000000', 2, 'CP-VIB-001', 1, 1, 1, 2.8000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (7, '000000', 2, 'CP-VIB-001', 1, 1, 1, 3.2000, NULL, '2026-06-10 08:25:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (8, '000000', 2, 'CP-VIB-001', 1, 1, 1, 5.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC振动值-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (9, '000000', 3, 'CP-DIM-001', 1, 1, 3, 10.0200, NULL, '2026-06-10 15:00:00', 30005, 'MANUAL', '装配关键尺寸检验', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (10, '000000', 4, 'CP-WELD-TEMP', 1, 1, 2, 245.0000, NULL, '2026-06-11 09:10:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (11, '000000', 4, 'CP-WELD-TEMP', 1, 1, 2, 252.0000, NULL, '2026-06-11 09:20:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (12, '000000', 4, 'CP-WELD-TEMP', 1, 1, 2, 258.0000, NULL, '2026-06-11 09:30:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for eq_equipment
-- ----------------------------
DROP TABLE IF EXISTS `eq_equipment`;
CREATE TABLE `eq_equipment`  (
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '设备编码',
  `equipment_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '设备名称',
  `equipment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备类型',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '型号',
  `manufacturer` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '制造商',
  `serial_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '出厂编号',
  `workcenter_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
  `location` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '安装位置',
  `purchase_date` date NULL DEFAULT NULL COMMENT '购置日期',
  `warranty_date` date NULL DEFAULT NULL COMMENT '保修到期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
  `inspection_items` json NULL COMMENT '点检项目及标准(JSON)',
  `last_inspection` json NULL COMMENT '最近一次点检结果(JSON)',
  `iot_device_id` bigint(20) NULL DEFAULT NULL COMMENT '关联IoT设备ID(预留,无外键)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`equipment_id`) USING BTREE,
  UNIQUE INDEX `uk_equipment_code`(`equipment_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_equipment
-- ----------------------------
INSERT INTO `eq_equipment` VALUES (1, '000000', 'CNC-001', '数控车床#1', 'CNC', 'CK6140', '沈阳机床厂', 'SY20250001', 1, 'GZZ0002', '注塑车间#1-A区', '2025-03-15', '2027-03-15', 'NORMAL', '[{\"name\": \"主轴温度\", \"unit\": \"℃\", \"threshold\": 80}, {\"name\": \"液压油位\", \"unit\": \"\", \"threshold\": \"正常\"}, {\"name\": \"刀具磨损\", \"unit\": \"mm\", \"threshold\": 0.5}]', '{\"主轴温度\": \"72℃\", \"刀具磨损\": \"0.2mm\", \"检查时间\": \"2026-06-20\", \"液压油位\": \"正常\"}', NULL, '主力数控车床', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (2, '000000', 'CNC-002', '数控车床#2', 'CNC', 'CK6150', '大连机床厂', 'DL20250002', 2, 'GZZ0003', '冲压车间#1-B区', '2025-06-01', '2027-06-01', 'NORMAL', '[{\"name\": \"主轴温度\", \"unit\": \"℃\", \"threshold\": 80}, {\"name\": \"冷却液浓度\", \"unit\": \"\", \"threshold\": \"5%\"}]', '{\"主轴温度\": \"68℃\", \"检查时间\": \"2026-06-20\", \"冷却液浓度\": \"6%\"}', NULL, '数控车床备用机', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (3, '000000', 'WELD-001', '自动焊接机#1', 'WELDING', 'YD-350FR', '松下焊接', 'XH20250003', 2, 'GZZ0003', '冲压车间#1-C区', '2025-01-10', '2026-07-10', 'NORMAL', '[{\"name\": \"焊接电流\", \"unit\": \"A\", \"threshold\": 300}, {\"name\": \"气体流量\", \"unit\": \"L/min\", \"threshold\": 20}]', '{\"检查时间\": \"2026-06-20\", \"气体流量\": \"18L/min\", \"焊接电流\": \"250A\"}', NULL, '自动焊接设备', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (4, '000000', 'ASM-001', '装配流水线#1', 'ASSEMBLY', 'APL-2000', '自制设备', 'AS20250004', 1, 'GZZ0002', '注塑车间#1-D区', '2024-09-01', '2026-09-01', 'NORMAL', '[{\"name\": \"传送带速度\", \"unit\": \"m/min\", \"threshold\": 10}, {\"name\": \"气压\", \"unit\": \"MPa\", \"threshold\": 0.6}]', '{\"气压\": \"0.65MPa\", \"检查时间\": \"2026-06-20\", \"传送带速度\": \"8m/min\"}', NULL, '装配主产线', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_equipment` VALUES (5, '000000', 'PKG-001', '包装机#1', 'PACKAGING', 'ZB-100', '浙江包装机械', 'PK20250005', 3, 'GZZ0004', '注塑车间#1-E区', '2025-05-01', '2027-05-01', 'NORMAL', '[{\"name\": \"封口温度\", \"unit\": \"℃\", \"threshold\": 200}, {\"name\": \"包装速度\", \"unit\": \"件/min\", \"threshold\": 30}]', '{\"包装速度\": \"25件/min\", \"封口温度\": \"180℃\", \"检查时间\": \"2026-06-20\"}', NULL, '成品包装设备', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for eq_maintenance_plan
-- ----------------------------
DROP TABLE IF EXISTS `eq_maintenance_plan`;
CREATE TABLE `eq_maintenance_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `plan_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计划名称',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `maintenance_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
  `cycle_days` int(11) NULL DEFAULT NULL COMMENT '周期(天)',
  `next_date` date NULL DEFAULT NULL COMMENT '下次保养日期(自动计算)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '保养内容',
  `responsible_id` bigint(20) NULL DEFAULT NULL COMMENT '负责人ID',
  `responsible_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_next_date`(`next_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备保养计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_maintenance_plan
-- ----------------------------
INSERT INTO `eq_maintenance_plan` VALUES (1, '000000', 'CNC001日常保养', 1, 'DAILY', 1, '2026-06-22', '1. 检查主轴润滑系统\n2. 清理铁屑\n3. 检查冷却液液位\n4. 检查导轨防护', 1, '张三', 'ACTIVE', '每日开工前执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (2, '000000', 'CNC001月度保养', 1, 'MONTHLY', 30, '2026-07-20', '1. 主轴精度校准\n2. 冷却液更换\n3. 液压油过滤\n4. 电气系统检查\n5. 丝杠间隙补偿', 1, '张三', 'ACTIVE', '每月20日执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (3, '000000', 'WELD001周度保养', 3, 'WEEKLY', 7, '2026-06-28', '1. 焊枪喷嘴清理\n2. 送丝机构检查\n3. 气体管路检查\n4. 接地线检查', 2, '李四', 'ACTIVE', '每周五执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (4, '000000', 'ASM001季度保养', 4, 'QUARTERLY', 90, '2026-09-20', '1. 传动链条张紧度调整\n2. 轴承润滑\n3. 传感器校准\n4. 安全门开关检测', 1, '张三', 'ACTIVE', '每季度执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (5, '000000', 'PKG001年度保养', 5, 'YEARLY', 365, '2027-06-20', '1. 全机大修\n2. 易损件更换\n3. 精度全面校准\n4. 电气系统全面检测', 1, '张三', 'ACTIVE', '年度大保养', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for eq_repair
-- ----------------------------
DROP TABLE IF EXISTS `eq_repair`;
CREATE TABLE `eq_repair`  (
  `repair_id` bigint(20) NOT NULL COMMENT '维修ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `repair_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '维修单号',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `fault_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '故障类型',
  `fault_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '故障描述',
  `reporter_id` bigint(20) NULL DEFAULT NULL COMMENT '报修人ID',
  `reporter_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报修人姓名',
  `repairer_id` bigint(20) NULL DEFAULT NULL COMMENT '维修人ID',
  `repairer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '维修人姓名',
  `repair_start` datetime(0) NULL DEFAULT NULL COMMENT '维修开始时间',
  `repair_end` datetime(0) NULL DEFAULT NULL COMMENT '维修结束时间',
  `downtime_hours` decimal(10, 2) NULL DEFAULT NULL COMMENT '停机时长(小时)',
  `repair_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '维修成本',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`repair_id`) USING BTREE,
  UNIQUE INDEX `uk_repair_no`(`repair_no`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备维修记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_repair
-- ----------------------------
INSERT INTO `eq_repair` VALUES (1, '000000', 'RP-001', 1, 'TEMP_OVERHEAT', '数控车床#1主轴温度持续超过85℃，导致自动停机保护，需检查冷却系统', 30003, '王五', 30001, '张三', '2026-06-15 10:30:00', '2026-06-15 14:00:00', 3.50, 800.00, 'COMPLETED', '主轴冷却系统管路堵塞，清理后恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_repair` VALUES (2, '000000', 'RP-002', 3, 'CURRENT_ABNORMAL', '自动焊接机#1焊接电流不稳定，波动范围±50A，焊接质量受影响', 30002, '李四', NULL, NULL, NULL, NULL, 0.00, 0.00, 'REPORTED', '焊接电流波动异常，待维修', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for mes_mbom
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom`;
CREATE TABLE `mes_mbom`  (
  `mbom_id` bigint(20) NOT NULL COMMENT 'MBOM ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `bom_id` bigint(20) NOT NULL COMMENT '关联ERP bd_bom.bom_id',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '制造工艺路线ID(可继承自bd_bom.routing_id或MES侧指定)',
  `mbom_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MBOM版本号',
  `mbom_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/FROZEN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_id`) USING BTREE,
  UNIQUE INDEX `uk_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_routing_id`(`routing_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES制造BOM头' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_mbom
-- ----------------------------
INSERT INTO `mes_mbom` VALUES (1, '000000', 5, 1, '1.0', 'ACTIVE', '自行车制造BOM，关联ERP BOM#5', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (2, '000000', 1, 2, '1.0', 'ACTIVE', '减速机JZQ-350制造BOM，关联ERP BOM#1', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for mes_mbom_line
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom_line`;
CREATE TABLE `mes_mbom_line`  (
  `mbom_line_id` bigint(20) NOT NULL COMMENT 'MBOM明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mbom_id` bigint(20) NOT NULL COMMENT '关联mes_mbom.mbom_id',
  `bom_id` bigint(20) NOT NULL COMMENT '冗余ERP bom_id',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '关联ERP bd_bom_detail.detail_id(可空,MES附加消耗料时为空)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_routing_step.step_id(工序步骤)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_process.process_id(工序)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP bd_workstation.workstation_id(工位)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_line_id`) USING BTREE,
  INDEX `idx_mbom_id`(`mbom_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_bom_detail_id`(`bom_detail_id`) USING BTREE,
  INDEX `idx_routing_step_id`(`routing_step_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES制造BOM明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_mbom_line
-- ----------------------------
INSERT INTO `mes_mbom_line` VALUES (1, '000000', 1, 5, NULL, 2001, 1.000000, 0.0200, 1, 1, 1, '车架切割前准备', 0, 1, '车架在切割工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (2, '000000', 1, 5, NULL, 2002, 2.000000, 0.0100, 3, 3, 1, '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (3, '000000', 1, 5, NULL, 2003, 1.000000, 0.0100, 3, 3, 1, '车把装配', 1, 3, '车把在装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (4, '000000', 1, 5, NULL, 10, 1.000000, 0.0000, 4, 5, 3, '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (5, '000000', 2, 1, NULL, 6, 1.000000, 0.0200, 5, 1, 1, '箱体毛坯切割', 0, 1, '箱体在切割工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (6, '000000', 2, 1, NULL, 7, 4.000000, 0.0000, 7, 3, 1, '螺栓装配', 0, 2, '螺栓在总装配工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (7, '000000', 2, 1, NULL, 5, 1.000000, 0.0100, 7, 3, 1, '齿轮组装配', 1, 3, '齿轮组在总装配工序投入(反冲)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (8, '000000', 2, 1, NULL, 8, 2.000000, 0.0050, 7, 3, 1, '轴承装配', 0, 4, '轴承在总装配工序投入', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom_line` VALUES (9, '000000', 2, 1, NULL, 10, 0.500000, 0.0000, 8, 4, 3, '润滑处理', 0, 5, '润滑油在质检前投入', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for pro_andon
-- ----------------------------
DROP TABLE IF EXISTS `pro_andon`;
CREATE TABLE `pro_andon`  (
  `andon_id` bigint(20) NOT NULL COMMENT '安灯ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `andon_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '安灯编号',
  `mes_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `call_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
  `call_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '呼叫原因',
  `andon_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
  `caller_id` bigint(20) NULL DEFAULT NULL COMMENT '呼叫人ID',
  `caller_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '呼叫人姓名',
  `responder_id` bigint(20) NULL DEFAULT NULL COMMENT '响应人ID',
  `responder_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '响应人姓名',
  `call_time` datetime(0) NULL DEFAULT NULL COMMENT '呼叫时间',
  `respond_time` datetime(0) NULL DEFAULT NULL COMMENT '响应时间',
  `resolve_time` datetime(0) NULL DEFAULT NULL COMMENT '解决时间',
  `duration_minutes` int(11) NULL DEFAULT NULL COMMENT '持续时长(分钟, 解决时计算)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  PRIMARY KEY (`andon_id`) USING BTREE,
  UNIQUE INDEX `uk_andon_no`(`andon_no`) USING BTREE,
  INDEX `idx_andon_status`(`andon_status`) USING BTREE,
  INDEX `idx_call_type`(`call_type`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES安灯呼叫' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_andon
-- ----------------------------
INSERT INTO `pro_andon` VALUES (1, '000000', 'ANDON-001', 1, 'WO-TEST-001', 1, 'FC-001', 2, 'MATERIAL', '焊接工序焊条库存不足，需要紧急补料', 'RESOLVED', 30002, '李四', 30001, '张三', '2026-06-11 09:00:00', '2026-06-11 09:05:00', '2026-06-11 09:20:00', 20, 'MANUAL', '焊条已从仓库调配到位', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_andon` VALUES (2, '000000', 'ANDON-002', 1, 'WO-TEST-001', 1, 'FC-001', 3, 'QUALITY', '装配发现车架焊接质量不达标，需质检介入', 'CALLED', 30003, '王五', NULL, NULL, '2026-06-12 14:00:00', NULL, NULL, NULL, 'MANUAL', '待质检人员响应', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_andon` VALUES (3, '000000', 'ANDON-003', 1, 'WO-TEST-001', 1, 'FC-001', 2, 'EQUIPMENT', '[IoT告警] CNC001温度探头报告主轴温度87.5℃ 超过阈值85℃', 'RESPONDED', NULL, 'IoT系统', 30001, '张三', '2026-06-15 10:15:00', '2026-06-15 10:16:00', NULL, NULL, 'IoT', 'IoT设备告警自动创建安灯', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_andon` VALUES (2067643792365850626, '000000', 'AND-20260619-9718', NULL, NULL, NULL, NULL, NULL, 'QUALITY', '', 'RESOLVED', 1, 'admin', 1, 'admin', '2026-06-19 00:21:10', '2026-06-19 00:21:16', '2026-06-19 00:21:23', 0, 'MANUAL', '测试', 103, 1, '2026-06-19 00:21:10', 1, '2026-06-19 00:21:10', '0');

-- ----------------------------
-- Table structure for pro_dispatch
-- ----------------------------
DROP TABLE IF EXISTS `pro_dispatch`;
CREATE TABLE `pro_dispatch`  (
  `dispatch_id` bigint(20) NOT NULL COMMENT '派工单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `dispatch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '派工单编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡工序记录ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `workcenter_id` bigint(20) NULL DEFAULT NULL,
  `workcenter_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID(SchWorker.workerId)',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '派工数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '良品数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `dispatch_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `plan_start_time` datetime(0) NULL DEFAULT NULL,
  `plan_end_time` datetime(0) NULL DEFAULT NULL,
  `actual_start_time` datetime(0) NULL DEFAULT NULL,
  `actual_end_time` datetime(0) NULL DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  PRIMARY KEY (`dispatch_id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_no`(`dispatch_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_step_record_id`(`step_record_id`) USING BTREE,
  INDEX `idx_operator_id`(`operator_id`) USING BTREE,
  INDEX `idx_dispatch_status`(`dispatch_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES派工单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_dispatch
-- ----------------------------
INSERT INTO `pro_dispatch` VALUES (1, '000000', 'DP-001', 1, 'WO-TEST-001', 1, 'FC-001', 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 30003, '王五', 50.0000, 50.0000, 49.0000, 1.0000, 0.0000, 'COMPLETED', '2026-06-10 08:00:00', NULL, '2026-06-10 08:05:00', '2026-06-10 16:30:00', '王五切割工序完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_dispatch` VALUES (2, '000000', 'DP-002', 1, 'WO-TEST-001', 1, 'FC-001', 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 30002, '李四', 50.0000, 0.0000, 0.0000, 0.0000, 0.0000, 'IN_PROGRESS', '2026-06-11 08:00:00', NULL, '2026-06-11 08:10:00', NULL, '李四焊接工序进行中', 100, 1, NULL, NULL, NULL, '0');

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
-- Records of pro_flow_card
-- ----------------------------
INSERT INTO `pro_flow_card` VALUES (1, '000000', 'FC-001', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, NULL, 1, 'RELEASED', 'BC-FC-001', '流转卡#1:首批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card` VALUES (2, '000000', 'FC-002', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, NULL, 1, 'CREATED', 'BC-FC-002', '流转卡#2:第二批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');

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
-- Records of pro_flow_card_step
-- ----------------------------
INSERT INTO `pro_flow_card_step` VALUES (1, '000000', 1, 'FC-001', 1, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 30003, '王五', 50.0000, 49.0000, 1.0000, 0.0000, NULL, NULL, '切割完成:49良+1不良', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (2, '000000', 1, 'FC-001', 1, 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1, 'IN_PROGRESS', 30002, '李四', 50.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, '焊接进行中(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (3, '000000', 1, 'FC-001', 1, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1, 'PENDING', NULL, NULL, 50.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, '待装配(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (4, '000000', 1, 'FC-001', 1, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 50.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, '待包装', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');

-- ----------------------------
-- Table structure for pro_sn
-- ----------------------------
DROP TABLE IF EXISTS `pro_sn`;
CREATE TABLE `pro_sn`  (
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SN序列号编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '关联流转卡ID',
  `card_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `current_step_seq` int(11) NULL DEFAULT NULL COMMENT '当前工序序号',
  `sn_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
  `parent_sn_id` bigint(20) NULL DEFAULT NULL COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
  `parent_sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '父SN编码(冗余)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
  `production_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  PRIMARY KEY (`sn_id`) USING BTREE,
  UNIQUE INDEX `uk_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_sn_status`(`sn_status`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_parent_sn_id`(`parent_sn_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SN序列号追溯' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_sn
-- ----------------------------
INSERT INTO `pro_sn` VALUES (1, '000000', 'SN202606200001', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 3, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#001-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_sn` VALUES (2, '000000', 'SN202606200002', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 3, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#002-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_sn` VALUES (3, '000000', 'SN202606200003', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 2, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#003-全部工序已完成', 100, 1, NULL, NULL, NULL, '0');

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
  PRIMARY KEY (`mes_wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_erp_wo_id`(`erp_wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_work_order
-- ----------------------------
INSERT INTO `pro_work_order` VALUES (1, '000000', 10001, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 100.0000, 0.0000, 0.0000, 26, '件', '2026-06-10 08:00:00', '2026-07-05 17:00:00', NULL, NULL, 1, '注塑车间#1', 5, 1, 'RELEASED', 2, 'MES工单:自行车100辆装配', NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_work_order` VALUES (2068925615991463937, '000000', 2068920326621814786, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', 100.0000, 0.0000, 0.0000, 26, '件', '2026-06-22 00:00:00', '2026-06-29 00:00:00', NULL, NULL, 1, '注塑车间#1', 5, 1, 'RELEASED', 5, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 14:51:19', '0');

-- ----------------------------
-- Table structure for pro_work_order_bom_line
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_bom_line`;
CREATE TABLE `pro_work_order_bom_line`  (
  `wo_bom_line_id` bigint(20) NOT NULL COMMENT '快照明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '关联MES工单ID',
  `mbom_line_id` bigint(20) NULL DEFAULT NULL COMMENT '源mes_mbom_line.mbom_line_id(可空,无MBOM时)',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP BOM ID',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP明细ID(溯源)',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `total_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '含损耗用量 = qty × (1 + loss/100)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤ID',
  `sequence_num` int(11) NULL DEFAULT NULL COMMENT '工序序号(冗余)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工位ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位编码(冗余)',
  `workstation_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工位名称(冗余)',
  `operation_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '作业内容',
  `backflush_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否倒冲 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`wo_bom_line_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工单BOM明细快照' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_work_order_bom_line
-- ----------------------------
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616163430402, '000000', 2068925615991463937, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616201179138, '000000', 2068925615991463937, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616213762050, '000000', 2068925615991463937, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616230539266, '000000', 2068925615991463937, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');

-- ----------------------------
-- Table structure for pro_work_order_step
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_step`;
CREATE TABLE `pro_work_order_step`  (
  `step_id` bigint(20) NOT NULL COMMENT '工序步骤ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '所属MES工单ID',
  `sequence_num` int(4) NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
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
  PRIMARY KEY (`step_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_step_status`(`step_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单工序步骤表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_work_order_step
-- ----------------------------
INSERT INTO `pro_work_order_step` VALUES (1, '000000', 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, '步1:钢板切割', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_work_order_step` VALUES (2, '000000', 1, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 100.0000, 0.0000, 0.0000, 'PENDING', 1, NULL, NULL, '步2:车架焊接(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_work_order_step` VALUES (3, '000000', 1, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 1, NULL, NULL, '步3:整车装配(关键工序)', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_work_order_step` VALUES (4, '000000', 1, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, '步4:成品包装', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_work_order_step` VALUES (2068925616075350017, '000000', 2068925615991463937, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_step` VALUES (2068925616113098753, '000000', 2068925615991463937, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_step` VALUES (2068925616125681666, '000000', 2068925615991463937, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_step` VALUES (2068925616142458881, '000000', 2068925615991463937, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 100.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');

-- ----------------------------
-- Table structure for qc_ipqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_ipqc`;
CREATE TABLE `qc_ipqc`  (
  `ipqc_id` bigint(20) NOT NULL COMMENT 'IPQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `ipqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IPQC编号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工单编号(冗余)',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序名称',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `defect_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '不良数量',
  `defect_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '不良率(%)',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`ipqc_id`) USING BTREE,
  UNIQUE INDEX `uk_ipqc_no`(`ipqc_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES过程检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qc_ipqc
-- ----------------------------
INSERT INTO `qc_ipqc` VALUES (1, '000000', 'IPQC-001', 1, 'WO-TEST-001', 1, 1, 1, 'CUT', '切割', 1001, 'IF20251110001', '自行车', 10.0000, 1.0000, 10.00, 'QUALIFIED', '[{\"code\": \"P001\", \"name\": \"尺寸偏差\", \"count\": 1, \"severity\": \"minor\"}]', 30005, '钱七', '2026-06-10 16:30:00', 'COMPLETED', '切割工序IPQC:10抽1不良(尺寸偏差)，合格', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for qc_iqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_iqc`;
CREATE TABLE `qc_iqc`  (
  `iqc_id` bigint(20) NOT NULL COMMENT 'IQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `iqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IQC编号',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP收货单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`iqc_id`) USING BTREE,
  UNIQUE INDEX `uk_iqc_no`(`iqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_receive_id`(`receive_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES来料检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qc_iqc
-- ----------------------------
INSERT INTO `qc_iqc` VALUES (1, '000000', 'IQC-001', 10001, 10, 'LUBRICANT', '润滑油', 2062098763051089924, '天津化工材料有限公司', 200.0000, 10.0000, 'QUALIFIED', '[{\"code\": \"Q001\", \"name\": \"粘度\", \"result\": \"合格\"}, {\"code\": \"Q002\", \"name\": \"闪点\", \"result\": \"合格\"}]', 30005, '钱七', '2026-06-10 15:00:00', 'COMPLETED', '润滑油来料检验合格', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for qc_oqc
-- ----------------------------
DROP TABLE IF EXISTS `qc_oqc`;
CREATE TABLE `qc_oqc`  (
  `oqc_id` bigint(20) NOT NULL COMMENT 'OQC ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `oqc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OQC编号',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP销售订单ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `quantity` decimal(14, 4) NOT NULL COMMENT '检验数量',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '抽样数量',
  `inspect_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
  `defect_info` json NULL COMMENT '不良品信息(类型/数量/描述)',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime(0) NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`oqc_id`) USING BTREE,
  UNIQUE INDEX `uk_oqc_no`(`oqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES出货检验' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qc_oqc
-- ----------------------------
INSERT INTO `qc_oqc` VALUES (1, '000000', 'OQC-001', 10001, 1001, 'IF20251110001', '自行车', 50.0000, 5.0000, 'PENDING', NULL, 30005, '钱七', NULL, 'PENDING', 'OQC待检:出货前抽查5辆', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for spc_control_chart
-- ----------------------------
DROP TABLE IF EXISTS `spc_control_chart`;
CREATE TABLE `spc_control_chart`  (
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `chart_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '控制图名称',
  `chart_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
  `point_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采集点ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '关联物料ID',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID',
  `ucl` decimal(14, 4) NULL DEFAULT NULL COMMENT '上控制限(analyze计算回写)',
  `cl` decimal(14, 4) NULL DEFAULT NULL COMMENT '中心线',
  `lcl` decimal(14, 4) NULL DEFAULT NULL COMMENT '下控制限',
  `sample_size` int(11) NULL DEFAULT 5 COMMENT '样本大小',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`chart_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SPC控制图' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of spc_control_chart
-- ----------------------------
INSERT INTO `spc_control_chart` VALUES (1, '000000', 'CNC主轴温度控制图', 'X_BAR_R', 1, 1, 1, 85.0000, 75.0000, 65.0000, 5, 'ACTIVE', '数控车床主轴温度SPC监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `spc_control_chart` VALUES (2, '000000', '装配关键尺寸控制图', 'X_BAR_R', 3, 1001, 3, 10.0500, 10.0000, 9.9500, 5, 'ACTIVE', '装配工序关键尺寸SPC监控(目标10mm)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `spc_control_chart` VALUES (3, '000000', '焊接温度控制图', 'X_BAR_R', 4, 1, 2, 280.0000, 250.0000, 220.0000, 5, 'ACTIVE', '焊接温度SPC监控', 100, 1, NULL, NULL, NULL, '0');

-- ----------------------------
-- Table structure for spc_data_point
-- ----------------------------
DROP TABLE IF EXISTS `spc_data_point`;
CREATE TABLE `spc_data_point`  (
  `data_id` bigint(20) NOT NULL COMMENT '数据点ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '000000',
  `chart_id` bigint(20) NOT NULL COMMENT '控制图ID',
  `sample_group` int(11) NOT NULL COMMENT '样本组号',
  `sample_index` int(11) NULL DEFAULT NULL COMMENT '组内序号',
  `value` decimal(14, 4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
  `is_ooc` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否超控制限(0/1)',
  `ooc_rule` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '违规规则(RULE1/2/3/4, 多规则逗号拼接)',
  `collect_time` datetime(0) NULL DEFAULT NULL COMMENT '采集时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_chart_id`(`chart_id`) USING BTREE,
  INDEX `idx_group`(`chart_id`, `sample_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SPC数据点' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
