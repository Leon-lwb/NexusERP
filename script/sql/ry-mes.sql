/*
 Navicat Premium Data Transfer

 Source Server         : 本地5.7
 Source Server Type    : MySQL
 Source Server Version : 50714 (5.7.14)
 Source Host           : localhost:3306
 Source Schema         : ry-mes

 Target Server Type    : MySQL
 Target Server Version : 50714 (5.7.14)
 File Encoding         : 65001

 Date: 28/07/2026 23:57:40
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`point_id`) USING BTREE,
  UNIQUE INDEX `uk_point_code`(`point_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES数据采集点' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of dc_collection_point
-- ----------------------------
INSERT INTO `dc_collection_point` VALUES (1, '000000', 'CP-TEMP-001', 'CNC001主轴温度', 'AUTO', 1, NULL, NULL, 1, 'NUMERIC', '℃', 60, 'ACTIVE', '数控车床#1主轴温度自动采集，每60秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (2, '000000', 'CP-VIB-001', 'CNC001振动', 'AUTO', 1, NULL, NULL, 1, 'NUMERIC', 'mm/s', 30, 'ACTIVE', '数控车床#1振动值自动采集，每30秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (3, '000000', 'CP-DIM-001', '装配尺寸检验', 'MANUAL', 4, NULL, NULL, 3, 'NUMERIC', 'mm', NULL, 'ACTIVE', '装配工序关键尺寸人工采集', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (4, '000000', 'CP-WELD-TEMP', '焊接温度监控', 'AUTO', 3, NULL, NULL, 2, 'NUMERIC', '℃', 10, 'ACTIVE', '焊接温度自动采集，每10秒', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_collection_point` VALUES (5, '000000', 'CP-PKG-CNT', '包装计数', 'AUTO', 5, NULL, NULL, 5, 'NUMERIC', '件', 60, 'ACTIVE', '包装机产出计数，每60秒', 100, 1, NULL, NULL, NULL, '0');

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
  `collect_time` datetime NOT NULL COMMENT '采集时间',
  `collect_by` bigint(20) NULL DEFAULT NULL COMMENT '采集人(手动时)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE INDEX `uk_point_collect_time`(`point_id`, `collect_time`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_collect_time`(`collect_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES采集数据记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of dc_data_record
-- ----------------------------
INSERT INTO `dc_data_record` VALUES (1, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 72.5000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (2, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 74.1000, NULL, '2026-06-10 08:20:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (3, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 87.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC主轴温度-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (4, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 73.2000, NULL, '2026-06-10 08:40:00', NULL, 'IoT', 'CNC主轴温度-恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (5, '000000', 1, 'CP-TEMP-001', NULL, 1, 1, 1, 75.8000, NULL, '2026-06-10 08:50:00', NULL, 'IoT', 'CNC主轴温度', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (6, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 2.8000, NULL, '2026-06-10 08:10:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (7, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 3.2000, NULL, '2026-06-10 08:25:00', NULL, 'IoT', 'CNC振动值', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (8, '000000', 2, 'CP-VIB-001', NULL, 1, 1, 1, 5.5000, NULL, '2026-06-10 08:30:00', NULL, 'IoT', 'CNC振动值-超阈值!', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (9, '000000', 3, 'CP-DIM-001', NULL, 1, 1, 3, 10.0200, NULL, '2026-06-10 15:00:00', 30005, 'MANUAL', '装配关键尺寸检验', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (10, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 245.0000, NULL, '2026-06-11 09:10:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (11, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 252.0000, NULL, '2026-06-11 09:20:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `dc_data_record` VALUES (12, '000000', 4, 'CP-WELD-TEMP', NULL, 1, 1, 2, 258.0000, NULL, '2026-06-11 09:30:00', NULL, 'IoT', '焊接温度监控', 100, 1, NULL, NULL, NULL, '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`equipment_id`) USING BTREE,
  UNIQUE INDEX `uk_equipment_code`(`equipment_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备台账' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_equipment
-- ----------------------------
INSERT INTO `eq_equipment` VALUES (1, '000000', 'CNC-001', '数控车床#1', 'CNC', 'CK6140', '沈阳机床厂', 'SY20250001', 1, 'GZZ0002', '注塑车间#1-A区', '2025-03-15', '2027-03-15', 'NORMAL', '[{\"name\": \"主轴温度\", \"unit\": \"℃\", \"threshold\": 80}, {\"name\": \"液压油位\", \"unit\": \"\", \"threshold\": \"正常\"}, {\"name\": \"刀具磨损\", \"unit\": \"mm\", \"threshold\": 0.5}]', '{\"主轴温度\": \"72℃\", \"刀具磨损\": \"0.2mm\", \"检查时间\": \"2026-06-20\", \"液压油位\": \"正常\"}', 1, '主力数控车床', 100, 1, NULL, NULL, NULL, '0');
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_next_date`(`next_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备保养计划' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_maintenance_plan
-- ----------------------------
INSERT INTO `eq_maintenance_plan` VALUES (1, '000000', 'CNC001日常保养', 1, 'DAILY', 1, '2026-06-22', '1. 检查主轴润滑系统\n2. 清理铁屑\n3. 检查冷却液液位\n4. 检查导轨防护', 1, '张三', 'ACTIVE', '每日开工前执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (2, '000000', 'CNC001月度保养', 1, 'MONTHLY', 30, '2026-07-20', '1. 主轴精度校准\n2. 冷却液更换\n3. 液压油过滤\n4. 电气系统检查\n5. 丝杠间隙补偿', 1, '张三', 'ACTIVE', '每月20日执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (3, '000000', 'WELD001周度保养', 3, 'WEEKLY', 7, '2026-06-28', '1. 焊枪喷嘴清理\n2. 送丝机构检查\n3. 气体管路检查\n4. 接地线检查', 2, '李四', 'ACTIVE', '每周五执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (4, '000000', 'ASM001季度保养', 4, 'QUARTERLY', 90, '2026-09-20', '1. 传动链条张紧度调整\n2. 轴承润滑\n3. 传感器校准\n4. 安全门开关检测', 1, '张三', 'ACTIVE', '每季度执行', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_maintenance_plan` VALUES (5, '000000', 'PKG001年度保养', 5, 'YEARLY', 365, '2027-06-20', '1. 全机大修\n2. 易损件更换\n3. 精度全面校准\n4. 电气系统全面检测', 1, '张三', 'ACTIVE', '年度大保养', 100, 1, NULL, NULL, NULL, '0');

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
  `execute_time` datetime NULL DEFAULT NULL COMMENT '执行时间',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '保养内容/执行情况',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ABNORMAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_execute_time`(`execute_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备保养执行记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_maintenance_record
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_eq_date`(`tenant_id`, `equipment_id`, `stat_date`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_stat_date`(`stat_date`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备 OEE 聚合记录(日粒度)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_oee_record
-- ----------------------------

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
  `execute_time` datetime NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_execute_time`(`execute_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_patrol_record
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`route_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检路线' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_patrol_route
-- ----------------------------

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
  `actual_date` datetime NULL DEFAULT NULL COMMENT '实际完成时间',
  `assigned_to_id` bigint(20) NULL DEFAULT NULL COMMENT '指派人ID',
  `assigned_to_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '指派人姓名',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`task_id`) USING BTREE,
  INDEX `idx_route_id`(`route_id`) USING BTREE,
  INDEX `idx_plan_date`(`plan_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES巡检任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_patrol_task
-- ----------------------------

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
  `repair_start` datetime NULL DEFAULT NULL COMMENT '维修开始时间',
  `repair_end` datetime NULL DEFAULT NULL COMMENT '维修结束时间',
  `downtime_hours` decimal(10, 2) NULL DEFAULT NULL COMMENT '停机时长(小时)',
  `repair_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '维修成本',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`repair_id`) USING BTREE,
  UNIQUE INDEX `uk_repair_no`(`repair_no`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES设备维修记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_repair
-- ----------------------------
INSERT INTO `eq_repair` VALUES (1, '000000', 'RP-001', 1, 'TEMP_OVERHEAT', '数控车床#1主轴温度持续超过85℃，导致自动停机保护，需检查冷却系统', 30003, '王五', 30001, '张三', '2026-06-15 10:30:00', '2026-06-15 14:00:00', 3.50, 800.00, 'COMPLETED', '主轴冷却系统管路堵塞，清理后恢复正常', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `eq_repair` VALUES (2, '000000', 'RP-002', 3, 'CURRENT_ABNORMAL', '自动焊接机#1焊接电流不稳定，波动范围±50A，焊接质量受影响', 30002, '李四', NULL, NULL, NULL, NULL, 0.00, 0.00, 'REPORTED', '焊接电流波动异常，待维修', 100, 1, NULL, NULL, NULL, '0');

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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_repair_id`(`repair_id`) USING BTREE,
  INDEX `idx_spare_id`(`spare_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES维修领用备件明细' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_repair_spare
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`spare_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_code`(`tenant_id`, `spare_code`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件主数据' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_spare_part
-- ----------------------------

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
  `operate_time` datetime NULL DEFAULT NULL COMMENT '操作时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_spare_id`(`spare_id`) USING BTREE,
  INDEX `idx_operate_time`(`operate_time`) USING BTREE,
  INDEX `idx_ref`(`ref_type`, `ref_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件库存流水' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of eq_spare_stock_log
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
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
-- Records of lt_lot
-- ----------------------------
INSERT INTO `lt_lot` VALUES (2081043029287907330, '000000', 'LOT-20260725-001', 1, '', '', 1, '', '', 0.0000, 0.0000, NULL, NULL, 'IN_STOCK', 'PENDING', NULL, '', 103, 1, '2026-07-25 23:44:57', 1, '2026-07-25 23:44:57', '0');
INSERT INTO `lt_lot` VALUES (2081053196305989635, '000000', 'LOT20260726001', 99002, 'QC-RM-A', '质检测试原料A', 2062098763051089924, '天津化工材料有限公司', '', 2.0000, 2.0000, '2026-07-26', '2027-07-25', 'CONSUMED', 'PASS', NULL, '', 103, 1, '2026-07-26 00:25:21', 1, '2026-07-26 00:25:21', '0');

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
  `bound_time` datetime NOT NULL COMMENT '绑定时间',
  `bound_by` bigint(20) NULL DEFAULT NULL COMMENT '绑定人ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`binding_id`) USING BTREE,
  UNIQUE INDEX `uk_sn_material`(`sn_id`, `material_id`) USING BTREE,
  INDEX `idx_sn_id`(`sn_id`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SN批次绑定关系' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of lt_sn_binding
-- ----------------------------

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
  `consumed_time` datetime NOT NULL COMMENT '消耗时间',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`consumption_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_consumed_time`(`consumed_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工单批次消耗记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of lt_wo_consumption
-- ----------------------------
INSERT INTO `lt_wo_consumption` VALUES (2081053452590546946, '000000', 2080270525816516609, NULL, 2081053196305989635, 'LOT20260726001', 99002, 'QC-RM-A', 2.0000, '2026-07-26 00:26:22', 1, NULL, NULL, 103, 1, '2026-07-26 00:26:22', 1, '2026-07-26 00:26:22', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_id`) USING BTREE,
  UNIQUE INDEX `uk_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_routing_id`(`routing_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES制造BOM头' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of mes_mbom
-- ----------------------------
INSERT INTO `mes_mbom` VALUES (1, '000000', 5, 1, '1.0', 'ACTIVE', '自行车制造BOM，关联ERP BOM#5', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (2, '000000', 1, 2, '1.0', 'ACTIVE', '减速机JZQ-350制造BOM，关联ERP BOM#1', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `mes_mbom` VALUES (30, '000000', 20, 3, '1.0', 'ACTIVE', '工具架MBOM，关联工艺路线和BOM', 103, 1, '2026-06-30 10:13:11', 1, '2026-06-30 10:13:11', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_line_id`) USING BTREE,
  INDEX `idx_mbom_id`(`mbom_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_bom_detail_id`(`bom_detail_id`) USING BTREE,
  INDEX `idx_routing_step_id`(`routing_step_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES制造BOM明细' ROW_FORMAT = DYNAMIC;

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
INSERT INTO `mes_mbom_line` VALUES (100, '000000', 30, 20, 100, 8001, 2.000000, 0.0000, 11, 3, 1, '取侧板组件2件，装配至工具架', 0, 10, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');
INSERT INTO `mes_mbom_line` VALUES (101, '000000', 30, 20, 101, 8002, 1.000000, 0.0000, 11, 3, 1, '取连接件包1套，装配至工具架', 0, 20, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');
INSERT INTO `mes_mbom_line` VALUES (102, '000000', 30, 21, 102, 1, 1.500000, 2.0000, 9, 1, 1, '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, 103, 1, '2026-06-30 10:13:16', 1, '2026-06-30 10:13:16', '0');

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
  `completed_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`report_id`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '8D报告' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ncr_8d_report
-- ----------------------------
INSERT INTO `ncr_8d_report` VALUES (2082122141029011457, '000000', 2082122139888160769, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:12:57', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `ncr_8d_report` VALUES (2082122243898511362, '000000', 2082122242732494850, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:13:22', 103, 1, '2026-07-28 23:13:22', 1, '2026-07-28 23:13:22', '0');
INSERT INTO `ncr_8d_report` VALUES (2082122379726852098, '000000', 2082122378531475458, 'E2E测试团队', '自动化测试发现问题', '临时措施', '根因', '永久措施', '已实施', '预防', '认可', 'COMPLETED', 1, '2026-07-28 23:13:54', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `ncr_8d_report` VALUES (2082123172148318210, '000000', 2082122379370336257, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:17:03', 1, '2026-07-28 23:17:03', '0');
INSERT INTO `ncr_8d_report` VALUES (2082132871811866625, '000000', 2082122140596998146, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:35', 1, '2026-07-28 23:55:35', '0');
INSERT INTO `ncr_8d_report` VALUES (2082132918037291009, '000000', 2082128180327653377, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:46', 1, '2026-07-28 23:55:46', '0');
INSERT INTO `ncr_8d_report` VALUES (2082132972085092354, '000000', 2082128179820142593, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL, NULL, 103, 1, '2026-07-28 23:55:59', 1, '2026-07-28 23:55:59', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`defect_id`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE,
  INDEX `idx_defect_code_id`(`defect_code_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR缺陷明细' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ncr_defect
-- ----------------------------
INSERT INTO `ncr_defect` VALUES (2082128179824336898, '000000', 2082128179820142593, NULL, 'SCRATCH', NULL, 3.0000, NULL, '表面划痕', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `ncr_defect` VALUES (2082128179828531202, '000000', 2082128179820142593, NULL, 'DENT', NULL, 1.0000, NULL, '凹痕', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`code_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '缺陷代码库' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ncr_defect_code
-- ----------------------------
INSERT INTO `ncr_defect_code` VALUES (2082122139464536065, '000000', 0, 'SURF', '表面缺陷', 1, '1', 0, NULL, 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `ncr_defect_code` VALUES (2082122378246262786, '000000', 0, 'SURF9293', '表面缺陷', 1, '1', 0, NULL, 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');

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
  `review_time` datetime NULL DEFAULT NULL COMMENT '评审时间',
  `disposition_by` bigint(20) NULL DEFAULT NULL COMMENT '处置人',
  `disposition_time` datetime NULL DEFAULT NULL COMMENT '处置时间',
  `disposition_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '处置签名ID(P0-6预留)',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `closed_time` datetime NULL DEFAULT NULL COMMENT '关闭时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`ncr_id`) USING BTREE,
  UNIQUE INDEX `uk_ncr_code`(`ncr_code`) USING BTREE,
  UNIQUE INDEX `uk_source`(`source_type`, `source_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_lot_id`(`lot_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR不合格品单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ncr_order
-- ----------------------------
INSERT INTO `ncr_order` VALUES (2082122139888160769, '000000', 'NCR20260728001', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, NULL, 'REVIEW', 1, '2026-07-28 23:12:57', NULL, NULL, NULL, '2026-07-28 23:12:57', NULL, 'E2E自动化测试建单', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `ncr_order` VALUES (2082122140596998146, '000000', 'NCR20260728002', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:12:57', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:12:57', 1, '2026-07-28 23:12:57', '0');
INSERT INTO `ncr_order` VALUES (2082122242732494850, '000000', 'NCR20260728003', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, 'ACCEPT_AS_IS', 'CLOSED', 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', NULL, '2026-07-28 23:13:21', '2026-07-28 23:13:21', '让步接收', 103, 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', '0');
INSERT INTO `ncr_order` VALUES (2082122243621687298, '000000', 'NCR20260728004', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:13:21', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:13:21', 1, '2026-07-28 23:13:21', '0');
INSERT INTO `ncr_order` VALUES (2082122378531475458, '000000', 'NCR20260728005', 'CUSTOMER', NULL, NULL, 'WO-TEST-001', NULL, NULL, NULL, NULL, 99002, 'QC-RM-A', '测试物料', NULL, NULL, 10.0000, 'ACCEPT_AS_IS', 'CLOSED', 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', NULL, '2026-07-28 23:13:54', '2026-07-28 23:13:54', '让步接收', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `ncr_order` VALUES (2082122379370336257, '000000', 'NCR20260728006', 'CUSTOMER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 99002, NULL, NULL, NULL, NULL, 3.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:13:54', NULL, '删除守卫测试', 103, 1, '2026-07-28 23:13:54', 1, '2026-07-28 23:13:54', '0');
INSERT INTO `ncr_order` VALUES (2082124524291264514, '000000', 'NCR20260728008', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:22:25', NULL, '返工闭环测试', 103, 1, '2026-07-28 23:22:25', 1, '2026-07-28 23:22:25', '0');
INSERT INTO `ncr_order` VALUES (2082125049405542401, '000000', 'NCR20260728010', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, NULL, 'REVIEW', 1, '2026-07-28 23:24:31', NULL, NULL, NULL, '2026-07-28 23:24:30', NULL, '返工闭环测试', 103, 1, '2026-07-28 23:24:30', 1, '2026-07-28 23:24:30', '0');
INSERT INTO `ncr_order` VALUES (2082127796360093697, '000000', 'NCR20260728012', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, 'REWORK', 'IN_REWORK', 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', NULL, '2026-07-28 23:35:25', NULL, 'E2E返工', 103, 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `ncr_order` VALUES (2082128179820142593, '000000', 'NCR20260728013', 'IQC', 2082128179698507778, NULL, NULL, NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 0.0000, NULL, 'OPEN', NULL, NULL, NULL, NULL, NULL, '2026-07-28 23:36:57', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `ncr_order` VALUES (2082128180327653377, '000000', 'NCR20260728014', 'CUSTOMER', NULL, 1, 'WO-TEST-001', NULL, NULL, NULL, NULL, 1001, NULL, NULL, NULL, NULL, 5.0000, 'REWORK', 'CLOSED', 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', NULL, '2026-07-28 23:36:57', '2026-07-28 23:36:58', 'E2E返工', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`rework_id`) USING BTREE,
  UNIQUE INDEX `uk_rework_code`(`rework_code`) USING BTREE,
  INDEX `idx_ncr_id`(`ncr_id`) USING BTREE,
  INDEX `idx_rework_wo_id`(`rework_wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'NCR返工工单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ncr_rework_order
-- ----------------------------
INSERT INTO `ncr_rework_order` VALUES (2082127797010210818, '000000', 'RW20260728002', 2082127796360093697, 1, 'WO-TEST-001', 2082127796938907650, 'RW20260728001', 5.0000, 'E2E返工', 'OPEN', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `ncr_rework_order` VALUES (2082128180793221121, '000000', 'RW20260728004', 2082128180327653377, 1, 'WO-TEST-001', 2082128180742889473, 'RW20260728003', 5.0000, 'E2E返工', 'COMPLETED', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `call_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
  `call_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫原因',
  `andon_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
  `caller_id` bigint(20) NULL DEFAULT NULL COMMENT '呼叫人ID',
  `caller_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫人姓名',
  `responder_id` bigint(20) NULL DEFAULT NULL COMMENT '响应人ID',
  `responder_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '响应人姓名',
  `call_time` datetime NULL DEFAULT NULL COMMENT '呼叫时间',
  `respond_time` datetime NULL DEFAULT NULL COMMENT '响应时间',
  `resolve_time` datetime NULL DEFAULT NULL COMMENT '解决时间',
  `duration_minutes` int(11) NULL DEFAULT NULL COMMENT '持续时长(分钟, 解决时计算)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`andon_id`) USING BTREE,
  UNIQUE INDEX `uk_andon_no`(`andon_no`) USING BTREE,
  INDEX `idx_andon_status`(`andon_status`) USING BTREE,
  INDEX `idx_call_type`(`call_type`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES安灯呼叫' ROW_FORMAT = DYNAMIC;

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
  `plan_start_time` datetime NULL DEFAULT NULL,
  `plan_end_time` datetime NULL DEFAULT NULL,
  `actual_start_time` datetime NULL DEFAULT NULL,
  `actual_end_time` datetime NULL DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`dispatch_id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_no`(`dispatch_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_step_record_id`(`step_record_id`) USING BTREE,
  INDEX `idx_operator_id`(`operator_id`) USING BTREE,
  INDEX `idx_dispatch_status`(`dispatch_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES派工单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_dispatch
-- ----------------------------
INSERT INTO `pro_dispatch` VALUES (1, '000000', 'DP-001', 1, 'WO-TEST-001', 1, 'FC-001', 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 30003, '王五', 50.0000, 50.0000, 49.0000, 1.0000, 0.0000, 'COMPLETED', '2026-06-10 08:00:00', NULL, '2026-06-10 08:05:00', '2026-06-10 16:30:00', '王五切割工序完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_dispatch` VALUES (2, '000000', 'DP-002', 1, 'WO-TEST-001', 1, 'FC-001', 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 30002, '李四', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', '2026-06-11 08:00:00', NULL, '2026-06-11 08:10:00', '2026-06-24 11:36:52', '李四焊接工序进行中', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_dispatch` VALUES (2069672596578680834, '000000', 'DIS-FC-SCGD202606220001-5210-4337', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288629600258, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 50.0000, 49.0000, 49.0000, 1.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:00:04', '2026-06-24 15:00:47', NULL, 103, 1, '2026-06-24 14:42:54', 1, '2026-06-24 14:42:54', '0');
INSERT INTO `pro_dispatch` VALUES (2069672880461758465, '000000', 'DIS-FC-SCGD202606220001-5210-2021', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288688320513, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:29', '2026-06-24 15:24:57', NULL, 103, 1, '2026-06-24 14:44:02', 1, '2026-06-24 14:44:02', '0');
INSERT INTO `pro_dispatch` VALUES (2069672909561839618, '000000', 'DIS-FC-SCGD202606220001-5210-8959', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288700903425, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:32', '2026-06-24 15:24:51', NULL, 103, 1, '2026-06-24 14:44:09', 1, '2026-06-24 14:44:09', '0');
INSERT INTO `pro_dispatch` VALUES (2069672943393095681, '000000', 'DIS-FC-SCGD202606220001-5210-7026', 2068925615991463937, 'SCGD202606220001', 2069625288491188226, 'FC-SCGD202606220001-5210', 2069625288709292033, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 50.0000, 50.0000, 50.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-06-24 15:24:34', '2026-06-24 15:24:46', NULL, 103, 1, '2026-06-24 14:44:17', 1, '2026-06-24 14:44:17', '0');
INSERT INTO `pro_dispatch` VALUES (2074152759460098050, '000000', 'DIS-FC-SCGD202607060001-3908-8417', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031869677570, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:26:31', '2026-07-06 23:26:43', NULL, 103, 1, '2026-07-06 23:25:28', 1, '2026-07-06 23:25:28', '0');
INSERT INTO `pro_dispatch` VALUES (2074152779248824322, '000000', 'DIS-FC-SCGD202607060001-3908-3134', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031869677571, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:27:49', '2026-07-06 23:27:56', NULL, 103, 1, '2026-07-06 23:25:33', 1, '2026-07-06 23:25:33', '0');
INSERT INTO `pro_dispatch` VALUES (2074152801914843138, '000000', 'DIS-FC-SCGD202607060001-3908-8534', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031928397825, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:27:58', '2026-07-06 23:28:07', NULL, 103, 1, '2026-07-06 23:25:39', 1, '2026-07-06 23:25:39', '0');
INSERT INTO `pro_dispatch` VALUES (2074152826820620289, '000000', 'DIS-FC-SCGD202607060001-3908-4471', 2074134800092008450, 'SCGD202607060001', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074140031928397826, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 10.0000, 10.0000, 10.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-06 23:28:01', '2026-07-06 23:28:12', NULL, 103, 1, '2026-07-06 23:25:44', 1, '2026-07-06 23:25:44', '0');
INSERT INTO `pro_dispatch` VALUES (2074395341140967425, '000000', 'DIS-FC-SCGD202607070001-8776-4386', 2074394214269894658, 'SCGD202607070001', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394478821425153, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 0.0000, 0.0000, 0.0000, 0.0000, 'IN_PROGRESS', NULL, NULL, '2026-07-07 15:30:19', NULL, NULL, 103, 1, '2026-07-07 15:29:24', 1, '2026-07-07 15:29:24', '0');
INSERT INTO `pro_dispatch` VALUES (2074395357666525185, '000000', 'DIS-FC-SCGD202607070001-8776-8327', 2074394214269894658, 'SCGD202607070001', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394478884339714, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 0.0000, 0.0000, 0.0000, 0.0000, 'IN_PROGRESS', NULL, NULL, '2026-07-07 15:30:17', NULL, NULL, 103, 1, '2026-07-07 15:29:28', 1, '2026-07-07 15:29:28', '0');
INSERT INTO `pro_dispatch` VALUES (2074395407633268738, '000000', 'DIS-FC-SCGD202607070001-5213-0239', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757453234178, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:14', '2026-07-07 15:30:38', NULL, 103, 1, '2026-07-07 15:29:40', 1, '2026-07-07 15:29:40', '0');
INSERT INTO `pro_dispatch` VALUES (2074395419746418690, '000000', 'DIS-FC-SCGD202607070001-5213-3128', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757470011394, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:12', '2026-07-07 15:30:35', NULL, 103, 1, '2026-07-07 15:29:43', 1, '2026-07-07 15:29:43', '0');
INSERT INTO `pro_dispatch` VALUES (2074395431591133185, '000000', 'DIS-FC-SCGD202607070001-5213-5951', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757474205698, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 14, '王思远', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:10', '2026-07-07 15:30:31', NULL, 103, 1, '2026-07-07 15:29:46', 1, '2026-07-07 15:29:46', '0');
INSERT INTO `pro_dispatch` VALUES (2074395445751103489, '000000', 'DIS-FC-SCGD202607070001-5213-9326', 2074394214269894658, 'SCGD202607070001', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394757490982913, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 15, '刘薇薇', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-07 15:30:09', '2026-07-07 15:30:27', NULL, 103, 1, '2026-07-07 15:29:49', 1, '2026-07-07 15:29:49', '0');
INSERT INTO `pro_dispatch` VALUES (2074767446839824385, '000000', 'DIS-FC-SCGD202607080001-5802-1298', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130249564162, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 12, '张明', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:36', '2026-07-08 16:08:52', NULL, 103, 1, '2026-07-08 16:08:01', 1, '2026-07-08 16:08:01', '0');
INSERT INTO `pro_dispatch` VALUES (2074767462119673857, '000000', 'DIS-FC-SCGD202607080001-5802-4941', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130295701506, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:34', '2026-07-08 16:08:49', NULL, 103, 1, '2026-07-08 16:08:05', 1, '2026-07-08 16:08:05', '0');
INSERT INTO `pro_dispatch` VALUES (2074767483657424897, '000000', 'DIS-FC-SCGD202607080001-5802-0076', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130304090113, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 18, '周涛', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:33', '2026-07-08 16:08:46', NULL, 103, 1, '2026-07-08 16:08:10', 1, '2026-07-08 16:08:10', '0');
INSERT INTO `pro_dispatch` VALUES (2074767502611484674, '000000', 'DIS-FC-SCGD202607080001-5802-4595', 2074766761268252674, 'SCGD202607080001', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074767130320867330, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 19, '吴静', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 16:08:31', '2026-07-08 16:08:42', NULL, 103, 1, '2026-07-08 16:08:15', 1, '2026-07-08 16:08:15', '0');
INSERT INTO `pro_dispatch` VALUES (2074787569524969473, '000000', 'DIS-FC-SCGD202607080002-6809-8920', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476835045377, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 20, '黄俊杰', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:49', '2026-07-08 17:35:13', NULL, 103, 1, '2026-07-08 17:27:59', 1, '2026-07-08 17:27:59', '0');
INSERT INTO `pro_dispatch` VALUES (2074787589187866626, '000000', 'DIS-FC-SCGD202607080002-6809-3607', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476851822593, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 21, '林小雨', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:47', '2026-07-08 17:35:01', NULL, 103, 1, '2026-07-08 17:28:04', 1, '2026-07-08 17:28:04', '0');
INSERT INTO `pro_dispatch` VALUES (2074787608284532737, '000000', 'DIS-FC-SCGD202607080002-6809-8161', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476860211202, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:45', '2026-07-08 17:34:57', NULL, 103, 1, '2026-07-08 17:28:08', 1, '2026-07-08 17:28:08', '0');
INSERT INTO `pro_dispatch` VALUES (2074787626882076674, '000000', 'DIS-FC-SCGD202607080002-6809-2596', 2074787137209667586, 'SCGD202607080002', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787476868599810, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 14, '王思远', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-08 17:34:43', '2026-07-08 17:34:54', NULL, 103, 1, '2026-07-08 17:28:13', 1, '2026-07-08 17:28:13', '0');
INSERT INTO `pro_dispatch` VALUES (2075402304033329154, '000000', 'DIS-FC-SCGD202607100001-0754-3047', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210584236033, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 18, '周涛', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:15', '2026-07-10 10:11:41', NULL, 103, 1, '2026-07-10 10:10:43', 1, '2026-07-10 10:10:43', '0');
INSERT INTO `pro_dispatch` VALUES (2075402316997922817, '000000', 'DIS-FC-SCGD202607100001-0754-6139', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210655539202, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 13, '李芳华', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:14', '2026-07-10 10:11:32', NULL, 103, 1, '2026-07-10 10:10:46', 1, '2026-07-10 10:10:46', '0');
INSERT INTO `pro_dispatch` VALUES (2075402335050207233, '000000', 'DIS-FC-SCGD202607100001-0754-0444', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210659733506, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 15, '刘薇薇', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:13', '2026-07-10 10:11:27', NULL, 103, 1, '2026-07-10 10:10:50', 1, '2026-07-10 10:10:50', '0');
INSERT INTO `pro_dispatch` VALUES (2075402351756120065, '000000', 'DIS-FC-SCGD202607100001-0754-4426', 2075397661844770817, 'SCGD202607100001', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075402210672316417, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 17, '赵琳', 1.0000, 1.0000, 1.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-10 10:11:11', '2026-07-10 10:11:22', NULL, 103, 1, '2026-07-10 10:10:54', 1, '2026-07-10 10:10:54', '0');
INSERT INTO `pro_dispatch` VALUES (2080299991368347649, '000000', 'DIS-FC-SCGD202607230001-7515-2707', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927648481283, 10, 1, 'CUT', '切割', 2, 'GZZ0003', 12, '张明', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:46', '2026-07-23 22:33:00', NULL, 103, 1, '2026-07-23 22:32:23', 1, '2026-07-23 22:32:23', '0');
INSERT INTO `pro_dispatch` VALUES (2080300008791486466, '000000', 'DIS-FC-SCGD202607230001-7515-6861', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927703007233, 20, 2, 'WELD', '焊接', 6, 'GZZ0007', 13, '李芳华', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:45', '2026-07-23 22:32:57', NULL, 103, 1, '2026-07-23 22:32:27', 1, '2026-07-23 22:32:27', '0');
INSERT INTO `pro_dispatch` VALUES (2080300025757446146, '000000', 'DIS-FC-SCGD202607230001-7515-0908', 2080270525816516609, 'SCGD202607230001', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080299927703007234, 30, 5, 'PKG', '包装', 9, 'GZZ0010', 14, '王思远', 20.0000, 20.0000, 20.0000, 0.0000, 0.0000, 'COMPLETED', NULL, NULL, '2026-07-23 22:32:42', '2026-07-23 22:32:53', NULL, 103, 1, '2026-07-23 22:32:31', 1, '2026-07-23 22:32:31', '0');

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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`card_id`) USING BTREE,
  UNIQUE INDEX `uk_card_no`(`card_no`) USING BTREE,
  UNIQUE INDEX `uk_barcode`(`barcode`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_status`(`card_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES流转卡表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_flow_card
-- ----------------------------
INSERT INTO `pro_flow_card` VALUES (1, '000000', 'FC-001', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, 3, 1, 'COMPLETED', 'BC-FC-001', '流转卡#1:首批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-07-27 23:14:00', '0');
INSERT INTO `pro_flow_card` VALUES (2, '000000', 'FC-002', 1, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 50.0000, 0.0000, 0.0000, NULL, 1, 'CREATED', 'BC-FC-002', '流转卡#2:第二批50辆', 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card` VALUES (2069625288491188226, '000000', 'FC-SCGD202606220001-5210', 2068925615991463937, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', 50.0000, 50.0000, 0.0000, 2069625288629600258, NULL, 'CLOSED', 'FC2069625288491188226', NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 16:07:12', '0');
INSERT INTO `pro_flow_card` VALUES (2074140031802568706, '000000', 'FC-SCGD202607060001-3908', 2074134800092008450, 'SCGD202607060001', 8003, 'RACK-001', '简易金属工具架', 10.0000, 10.0000, 0.0000, 2074140031869677570, NULL, 'CLOSED', 'FC2074140031802568706', NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:47:30', '0');
INSERT INTO `pro_flow_card` VALUES (2074394757411291138, '000000', 'FC-SCGD202607070001-5213', 2074394214269894658, 'SCGD202607070001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074394757470011394, NULL, 'COMPLETED', 'FC2074394757411291138', NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 16:15:23', '0');
INSERT INTO `pro_flow_card` VALUES (2074767130190843905, '000000', 'FC-SCGD202607080001-5802', 2074766761268252674, 'SCGD202607080001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074767130249564162, NULL, 'COMPLETED', 'FC2074767130190843905', NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:51', '0');
INSERT INTO `pro_flow_card` VALUES (2074787476788908033, '000000', 'FC-SCGD202607080002-6809', 2074787137209667586, 'SCGD202607080002', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2074787476835045377, NULL, 'COMPLETED', 'FC2074787476788908033', NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:13', '0');
INSERT INTO `pro_flow_card` VALUES (2075402210525515778, '000000', 'FC-SCGD202607100001-0754', 2075397661844770817, 'SCGD202607100001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 2075402210584236033, NULL, 'COMPLETED', 'FC2075402210525515778', NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:40', '0');
INSERT INTO `pro_flow_card` VALUES (2080299927648481282, '000000', 'FC-SCGD202607230001-7515', 2080270525816516609, 'SCGD202607230001', 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 20.0000, 0.0000, 2080299927648481283, NULL, 'COMPLETED', 'FC2080299927648481282', NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:33:00', '0');
INSERT INTO `pro_flow_card` VALUES (2082127798264307713, '000000', 'FC-RW20260728001-5826', 2082127796938907650, 'RW20260728001', 1001, 'IF20251110001', '自行车', 5.0000, 0.0000, 0.0000, 2082127798297862146, NULL, 'IN_PROGRESS', 'FC2082127798264307713', NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `pro_flow_card` VALUES (2082128181665636354, '000000', 'FC-RW20260728003-7237', 2082128180742889473, 'RW20260728003', 1001, 'IF20251110001', '自行车', 5.0000, 5.0000, 0.0000, 2082128181686607873, NULL, 'COMPLETED', 'FC2082128181665636354', NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `actual_start_time` datetime NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime NULL DEFAULT NULL COMMENT '实际结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `entry_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验结果(PASS/FAIL)',
  `entry_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '进站校验备注(失败原因)',
  `exit_check_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验结果(PASS/FAIL)',
  `exit_check_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出站校验备注(失败原因)',
  `skip_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳站原因',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`step_record_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_step_status`(`step_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES流转卡工序记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_flow_card_step
-- ----------------------------
INSERT INTO `pro_flow_card_step` VALUES (1, '000000', 1, 'FC-001', 1, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 30003, '王五', 50.0000, 49.0000, 1.0000, 0.0000, NULL, NULL, '切割完成:49良+1不良', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-21 20:59:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (2, '000000', 1, 'FC-001', 1, 2, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1, 'COMPLETED', 30002, '李四', 50.0000, 50.0000, 0.0000, 0.0000, NULL, '2026-06-24 11:36:52', '焊接进行中(关键工序)', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-06-24 11:36:52', '0');
INSERT INTO `pro_flow_card_step` VALUES (3, '000000', 1, 'FC-001', 1, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1, 'IN_PROGRESS', 1, NULL, 50.0000, 50.0000, 0.0000, 0.0000, '2026-07-27 23:18:08', '2026-07-27 23:14:00', '待装配(关键工序)', 'PASS', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-07-27 23:18:08', '0');
INSERT INTO `pro_flow_card_step` VALUES (4, '000000', 1, 'FC-001', 1, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 50.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, '待包装', NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-07-27 23:18:08', '0');
INSERT INTO `pro_flow_card_step` VALUES (2069625288629600258, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616075350017, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 50.0000, 49.0000, 1.0000, 0.0000, '2026-06-24 14:44:22', '2026-06-24 15:00:47', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:00:46', '0');
INSERT INTO `pro_flow_card_step` VALUES (2069625288688320513, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616113098753, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:29', '2026-06-24 15:24:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:56', '0');
INSERT INTO `pro_flow_card_step` VALUES (2069625288700903425, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616125681666, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:32', '2026-06-24 15:24:51', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:50', '0');
INSERT INTO `pro_flow_card_step` VALUES (2069625288709292033, '000000', 2069625288491188226, 'FC-SCGD202606220001-5210', 2068925615991463937, 2068925616142458881, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 50.0000, 50.0000, 0.0000, 0.0000, '2026-06-24 15:24:34', '2026-06-24 15:24:46', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-24 11:34:55', 1, '2026-06-24 15:24:45', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074140031869677570, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800092008451, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:25:49', '2026-07-06 23:26:43', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:26:42', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074140031869677571, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728706, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:27:49', '2026-07-06 23:27:56', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:27:55', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074140031928397825, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728707, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:27:58', '2026-07-06 23:28:07', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:28:06', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074140031928397826, '000000', 2074140031802568706, 'FC-SCGD202607060001-3908', 2074134800092008450, 2074134800150728708, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 10.0000, 10.0000, 0.0000, 0.0000, '2026-07-06 23:28:01', '2026-07-06 23:28:12', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:34:54', 1, '2026-07-06 23:28:11', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394478821425153, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214299254786, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'IN_PROGRESS', 12, '张明', 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-07 15:30:19', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:30:19', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394478884339714, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214341197826, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'IN_PROGRESS', 13, '李芳华', 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-07 15:30:17', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:30:17', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394478896922625, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214353780737, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'IN_PROGRESS', 1, NULL, 1.0000, 0.0000, 0.0000, 0.0000, '2026-07-27 23:14:00', NULL, NULL, 'PASS', NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-27 23:14:00', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394478905311234, '000000', 2074394478758510594, 'FC-SCGD202607070001-8776', 2074394214269894658, 2074394214366363650, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 1.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:25:59', 1, '2026-07-07 15:25:59', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394757453234178, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214299254786, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 12, '张明', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:14', '2026-07-07 15:30:38', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:37', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394757470011394, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214341197826, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', NULL, '李芳华', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:29:55', '2026-07-07 15:30:35', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:34', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394757474205698, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214353780737, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 14, '王思远', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:10', '2026-07-07 15:30:31', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074394757490982913, '000000', 2074394757411291138, 'FC-SCGD202607070001-5213', 2074394214269894658, 2074394214366363650, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 15, '刘薇薇', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-07 15:30:09', '2026-07-07 15:30:27', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-07 15:27:05', 1, '2026-07-07 15:30:26', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074767130249564162, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761289224194, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '张明', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:19', '2026-07-08 16:08:52', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:51', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074767130295701506, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761360527361, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:34', '2026-07-08 16:08:49', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:48', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074767130304090113, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761373110274, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 18, '周涛', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:33', '2026-07-08 16:08:46', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:45', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074767130320867330, '000000', 2074767130190843905, 'FC-SCGD202607080001-5802', 2074766761268252674, 2074766761389887489, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 19, '吴静', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 16:08:31', '2026-07-08 16:08:42', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 16:06:46', 1, '2026-07-08 16:08:42', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074787476835045377, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137239027713, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '黄俊杰', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:30:37', '2026-07-08 17:35:13', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:13', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074787476851822593, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137280970754, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 21, '林小雨', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:47', '2026-07-08 17:35:01', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:35:01', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074787476860211202, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137297747969, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:45', '2026-07-08 17:34:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:34:57', '0');
INSERT INTO `pro_flow_card_step` VALUES (2074787476868599810, '000000', 2074787476788908033, 'FC-SCGD202607080002-6809', 2074787137209667586, 2074787137310330881, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 14, '王思远', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-08 17:34:43', '2026-07-08 17:34:54', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-08 17:27:37', 1, '2026-07-08 17:34:54', '0');
INSERT INTO `pro_flow_card_step` VALUES (2075402210584236033, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661861548033, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', NULL, '周涛', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:10:59', '2026-07-10 10:11:41', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:40', '0');
INSERT INTO `pro_flow_card_step` VALUES (2075402210655539202, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661953822721, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 13, '李芳华', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:14', '2026-07-10 10:11:32', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:31', '0');
INSERT INTO `pro_flow_card_step` VALUES (2075402210659733506, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661970599938, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 15, '刘薇薇', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:13', '2026-07-10 10:11:27', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:27', '0');
INSERT INTO `pro_flow_card_step` VALUES (2075402210672316417, '000000', 2075402210525515778, 'FC-SCGD202607100001-0754', 2075397661844770817, 2075397661983182849, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 17, '赵琳', 1.0000, 1.0000, 0.0000, 0.0000, '2026-07-10 10:11:11', '2026-07-10 10:11:22', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-10 10:10:21', 1, '2026-07-10 10:11:22', '0');
INSERT INTO `pro_flow_card_step` VALUES (2080299927648481283, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516611, 10, 1, 'CUT', '切割', 2, 'GZZ0003', 0, 'COMPLETED', NULL, '张明', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:34', '2026-07-23 22:33:00', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:33:00', '0');
INSERT INTO `pro_flow_card_step` VALUES (2080299927703007233, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516612, 20, 2, 'WELD', '焊接', 6, 'GZZ0007', 0, 'COMPLETED', 13, '李芳华', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:45', '2026-07-23 22:32:57', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:32:56', '0');
INSERT INTO `pro_flow_card_step` VALUES (2080299927703007234, '000000', 2080299927648481282, 'FC-SCGD202607230001-7515', 2080270525816516609, 2080270525816516613, 30, 5, 'PKG', '包装', 9, 'GZZ0010', 0, 'COMPLETED', 14, '王思远', 20.0000, 20.0000, 0.0000, 0.0000, '2026-07-23 22:32:42', '2026-07-23 22:32:53', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-23 22:32:08', 1, '2026-07-23 22:32:53', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082127798297862146, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797928763393, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'IN_PROGRESS', 1, NULL, 5.0000, 0.0000, 0.0000, 0.0000, '2026-07-28 23:35:26', NULL, NULL, 'PASS', NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082127798306250753, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797941346305, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082127798310445057, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797953929218, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082127798318833665, '000000', 2082127798264307713, 'FC-RW20260728001-5826', 2082127796938907650, 2082127797962317825, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'PENDING', NULL, NULL, 5.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082128181686607873, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181367840770, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:57', '2026-07-28 23:36:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082128181694996481, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181376229377, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:57', '2026-07-28 23:36:57', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082128181699190785, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181380423681, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:58', '2026-07-28 23:36:58', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_flow_card_step` VALUES (2082128181707579394, '000000', 2082128181665636354, 'FC-RW20260728003-7237', 2082128180742889473, 2082128181397200897, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 0, 'COMPLETED', 1, NULL, 5.0000, 5.0000, 0.0000, 0.0000, '2026-07-28 23:36:58', '2026-07-28 23:36:58', NULL, 'PASS', NULL, 'PASS', NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`sn_id`) USING BTREE,
  UNIQUE INDEX `uk_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_sn_status`(`sn_status`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_parent_sn_id`(`parent_sn_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SN序列号追溯' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_sn
-- ----------------------------
INSERT INTO `pro_sn` VALUES (1, '000000', 'SN202606200001', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 3, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#001-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_sn` VALUES (2, '000000', 'SN202606200002', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 3, 'IN_PRODUCTION', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#002-切割焊接已完成', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `pro_sn` VALUES (3, '000000', 'SN202606200003', 1, 'WO-TEST-001', 1, 'FC-001', 1001, 'IF20251110001', '自行车', 2, 'COMPLETED', NULL, NULL, 'MANUAL', '2026-06-20', '自行车SN#003-全部工序已完成', 100, 1, NULL, NULL, NULL, '0');

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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  INDEX `idx_process_id`(`process_id`) USING BTREE,
  INDEX `idx_check_stage`(`check_stage`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工序校验规则配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_step_check_rule
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`guide_id`) USING BTREE,
  INDEX `idx_process_id`(`process_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工序流程指引配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_step_guide
-- ----------------------------

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
  `plan_start_date` datetime NULL DEFAULT NULL COMMENT '计划开工日期',
  `plan_end_date` datetime NULL DEFAULT NULL COMMENT '计划完工日期',
  `actual_start_date` datetime NULL DEFAULT NULL COMMENT '实际开工时间',
  `actual_end_date` datetime NULL DEFAULT NULL COMMENT '实际完工时间',
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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ERP' COMMENT '工单来源(ERP/REWORK/MANUAL)',
  PRIMARY KEY (`mes_wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_erp_wo_id`(`erp_wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_work_order
-- ----------------------------
INSERT INTO `pro_work_order` VALUES (1, '000000', 10001, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', 100.0000, 0.0000, 0.0000, 26, '件', '2026-06-10 08:00:00', '2026-07-05 17:00:00', '2026-07-27 23:14:00', NULL, 1, '注塑车间#1', 5, NULL, 1, 'IN_PROGRESS', 2, 'MES工单:自行车100辆装配', NULL, 100, 1, '2026-06-21 20:59:31', NULL, '2026-07-27 23:13:59', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2068925615991463937, '000000', 2068920326621814786, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', 100.0000, 50.0000, 0.0000, 26, '件', '2026-06-22 00:00:00', '2026-06-29 00:00:00', '2026-06-24 14:44:22', '2026-06-24 15:24:57', 1, '注塑车间#1', 5, NULL, 1, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-06-22 13:14:40', -1, '2026-06-24 16:07:12', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2074134800092008450, '000000', 2073979191344271361, 'SCGD202607060001', 8003, 'RACK-001', '简易金属工具架', 10.0000, 10.0000, 0.0000, 27, '套', '2026-07-03 00:00:00', '2026-07-31 00:00:00', '2026-07-06 23:25:49', '2026-07-06 23:28:12', 3, '装配车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 23:28:11', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2074394214269894658, '000000', 2074393703705657346, 'SCGD202607070001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 2.0000, 0.0000, 27, '套', '2026-07-07 15:22:54', '2026-07-08 00:00:00', '2026-07-07 15:29:55', '2026-07-07 16:15:23', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 16:15:23', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2074766761268252674, '000000', 2074766662588862465, 'SCGD202607080001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-08 00:00:00', '2026-07-09 00:00:00', '2026-07-08 16:08:19', '2026-07-08 16:08:52', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:08:52', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2074787137209667586, '000000', 2074786959987740674, 'SCGD202607080002', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-08 00:00:00', '2026-07-09 00:00:00', '2026-07-08 17:30:37', '2026-07-08 17:35:13', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:35:13', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2075397661844770817, '000000', 2075397566059450369, 'SCGD202607100001', 8003, 'RACK-001', '简易金属工具架', 1.0000, 1.0000, 0.0000, 27, '套', '2026-07-10 00:00:00', '2026-07-11 00:00:00', '2026-07-10 10:10:59', '2026-07-10 10:11:41', 2, '冲压车间#1', 20, NULL, 3, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 10:11:41', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2080270525816516609, '000000', 2080231171726180353, 'SCGD202607230001', 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 20.0000, 0.0000, 26, '件', '2026-07-23 00:00:00', '2026-07-24 00:00:00', '2026-07-23 22:32:34', '2026-07-23 22:33:00', 4, '包装车间#1', 99001, NULL, 99001, 'COMPLETED', 5, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 22:33:00', '0', 'ERP');
INSERT INTO `pro_work_order` VALUES (2082127796938907650, '000000', NULL, 'RW20260728001', 1001, 'IF20251110001', '自行车', 5.0000, 0.0000, 0.0000, 26, '件', NULL, NULL, '2026-07-28 23:35:26', NULL, 1, '注塑车间#1', 5, NULL, 1, 'IN_PROGRESS', 5, 'NCR返工: E2E返工', NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:25', '0', 'REWORK');
INSERT INTO `pro_work_order` VALUES (2082128180742889473, '000000', NULL, 'RW20260728003', 1001, 'IF20251110001', '自行车', 5.0000, 5.0000, 0.0000, 26, '件', NULL, NULL, '2026-07-28 23:36:57', '2026-07-28 23:36:58', 1, '注塑车间#1', 5, NULL, 1, 'COMPLETED', 5, 'NCR返工: E2E返工', NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0', 'REWORK');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`wo_bom_line_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单BOM明细快照' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_work_order_bom_line
-- ----------------------------
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616163430402, '000000', 2068925615991463937, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616201179138, '000000', 2068925615991463937, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616213762050, '000000', 2068925615991463937, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2068925616230539266, '000000', 2068925615991463937, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', -1, -1, '2026-06-22 13:14:40', -1, '2026-06-22 13:14:40', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074134800150728709, '000000', 2074134800092008450, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074134800213643265, '000000', 2074134800092008450, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074134800213643266, '000000', 2074134800092008450, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074394214395723777, '000000', 2074394214269894658, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074394214429278209, '000000', 2074394214269894658, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074394214446055426, '000000', 2074394214269894658, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074766761406664705, '000000', 2074766761268252674, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074766761477967873, '000000', 2074766761268252674, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074766761494745089, '000000', 2074766761268252674, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074787137335496705, '000000', 2074787137209667586, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074787137377439745, '000000', 2074787137209667586, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2074787137398411265, '000000', 2074787137209667586, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2075397661995765761, '000000', 2075397661844770817, 100, 20, 100, 8001, 'RACK-SIDE', '侧板组件', 2.000000, 0.0000, 2.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取侧板组件2件，装配至工具架', 0, 10, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2075397662067068930, '000000', 2075397661844770817, 102, 20, 102, 1, 'IF20251016004', '钢板', 1.500000, 2.0000, 1.530000, 9, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '取钢板1.5kg，切割为侧板坯料', 0, 10, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2075397662088040450, '000000', 2075397661844770817, 101, 20, 101, 8002, 'RACK-FIT', '连接件包', 1.000000, 0.0000, 1.000000, 11, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '取连接件包1套，装配至工具架', 0, 20, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082127797987483650, '000000', 2082127796938907650, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082127797995872258, '000000', 2082127796938907650, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082127798004260866, '000000', 2082127796938907650, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082127798016843777, '000000', 2082127796938907650, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082128181405589505, '000000', 2082128180742889473, 1, 5, NULL, 2001, 'IF20251203001', '车架', 1.000000, 0.0200, 1.000200, 1, 10, 1, 'CUT', '切割', 1, 'GZZ0002', '工作站1', '车架切割前准备', 0, 1, '车架在切割工序投入', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082128181422366722, '000000', 2082128180742889473, 2, 5, NULL, 2002, 'IF20251203002', '车轮', 2.000000, 0.0100, 2.000200, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车轮装配', 1, 2, '车轮在装配工序投入(反冲)', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082128181434949633, '000000', 2082128180742889473, 3, 5, NULL, 2003, 'IF20251203003', '车把', 1.000000, 0.0100, 1.000100, 3, 30, 3, 'ASM', '装配', 1, 'GZZ0002', '工作站1', '车把装配', 1, 3, '车把在装配工序投入(反冲)', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_bom_line` VALUES (2082128181443338242, '000000', 2082128180742889473, 4, 5, NULL, 10, 'LUBRICANT', '润滑油', 1.000000, 0.0000, 1.000000, 4, 40, 5, 'PKG', '包装', 3, 'GZZ0004', '工作站3', '包装前润滑处理', 0, 4, '润滑油在包装工序投入', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`step_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_step_status`(`step_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单工序步骤表' ROW_FORMAT = DYNAMIC;

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
INSERT INTO `pro_work_order_step` VALUES (2074134800092008451, '000000', 2074134800092008450, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_step` VALUES (2074134800150728706, '000000', 2074134800092008450, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_step` VALUES (2074134800150728707, '000000', 2074134800092008450, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_step` VALUES (2074134800150728708, '000000', 2074134800092008450, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 10.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-06 22:14:07', -1, '2026-07-06 22:14:07', '0');
INSERT INTO `pro_work_order_step` VALUES (2074394214299254786, '000000', 2074394214269894658, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_step` VALUES (2074394214341197826, '000000', 2074394214269894658, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_step` VALUES (2074394214353780737, '000000', 2074394214269894658, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_step` VALUES (2074394214366363650, '000000', 2074394214269894658, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-07 15:24:56', -1, '2026-07-07 15:24:56', '0');
INSERT INTO `pro_work_order_step` VALUES (2074766761289224194, '000000', 2074766761268252674, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2074766761360527361, '000000', 2074766761268252674, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2074766761373110274, '000000', 2074766761268252674, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2074766761389887489, '000000', 2074766761268252674, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 16:05:18', -1, '2026-07-08 16:05:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2074787137239027713, '000000', 2074787137209667586, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2074787137280970754, '000000', 2074787137209667586, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2074787137297747969, '000000', 2074787137209667586, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2074787137310330881, '000000', 2074787137209667586, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-08 17:26:16', -1, '2026-07-08 17:26:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2075397661861548033, '000000', 2075397661844770817, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2075397661953822721, '000000', 2075397661844770817, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2075397661970599938, '000000', 2075397661844770817, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2075397661983182849, '000000', 2075397661844770817, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 1.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-10 09:52:16', -1, '2026-07-10 09:52:16', '0');
INSERT INTO `pro_work_order_step` VALUES (2080270525816516611, '000000', 2080270525816516609, 10, 1, 'CUT', '切割', 2, 'GZZ0003', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2080270525816516612, '000000', 2080270525816516609, 20, 2, 'WELD', '焊接', 6, 'GZZ0007', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2080270525816516613, '000000', 2080270525816516609, 30, 5, 'PKG', '包装', 9, 'GZZ0010', 20.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, -1, -1, '2026-07-23 20:35:18', -1, '2026-07-23 20:35:18', '0');
INSERT INTO `pro_work_order_step` VALUES (2082127797928763393, '000000', 2082127796938907650, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_step` VALUES (2082127797941346305, '000000', 2082127796938907650, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_step` VALUES (2082127797953929218, '000000', 2082127796938907650, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_step` VALUES (2082127797962317825, '000000', 2082127796938907650, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:35:26', 1, '2026-07-28 23:35:26', '0');
INSERT INTO `pro_work_order_step` VALUES (2082128181367840770, '000000', 2082128180742889473, 10, 1, 'CUT', '切割', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_step` VALUES (2082128181376229377, '000000', 2082128180742889473, 20, 2, 'WELD', '焊接', 2, 'GZZ0003', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_step` VALUES (2082128181380423681, '000000', 2082128180742889473, 30, 3, 'ASM', '装配', 1, 'GZZ0002', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');
INSERT INTO `pro_work_order_step` VALUES (2082128181397200897, '000000', 2082128180742889473, 40, 5, 'PKG', '包装', 3, 'GZZ0004', 5.0000, 0.0000, 0.0000, 'PENDING', 0, NULL, NULL, NULL, 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`ipqc_id`) USING BTREE,
  UNIQUE INDEX `uk_ipqc_no`(`ipqc_no`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_card_id`(`card_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES过程检验' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qc_ipqc
-- ----------------------------
INSERT INTO `qc_ipqc` VALUES (1, '000000', 'IPQC-001', 1, 'WO-TEST-001', 1, 1, 1, 'CUT', '切割', 1001, 'IF20251110001', '自行车', 10.0000, 1.0000, 10.00, 'QUALIFIED', '[{\"code\": \"P001\", \"name\": \"尺寸偏差\", \"count\": 1, \"severity\": \"minor\"}]', 30005, '钱七', '2026-06-10 16:30:00', 'COMPLETED', '切割工序IPQC:10抽1不良(尺寸偏差)，合格', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `qc_ipqc` VALUES (2081760017509511170, '000000', 'IPQC-20260727-001', 1, 'WO-TEST-001', 1, 3, 3, 'ASM', '装配', 1001, 'IF20251110001', '自行车', NULL, 0.0000, NULL, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-27 23:14:00', 1, '2026-07-27 23:14:00', '0');

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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`iqc_id`) USING BTREE,
  UNIQUE INDEX `uk_iqc_no`(`iqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_receive_id`(`receive_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES来料检验' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qc_iqc
-- ----------------------------
INSERT INTO `qc_iqc` VALUES (1, '000000', 'IQC-001', 10001, 10, 'LUBRICANT', '润滑油', 2062098763051089924, '天津化工材料有限公司', 200.0000, 10.0000, 'QUALIFIED', '[{\"code\": \"Q001\", \"name\": \"粘度\", \"result\": \"合格\"}, {\"code\": \"Q002\", \"name\": \"闪点\", \"result\": \"合格\"}]', 30005, '钱七', '2026-06-10 15:00:00', 'COMPLETED', '润滑油来料检验合格', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `qc_iqc` VALUES (2080291255916593154, '000000', 'IQC-20260723-001', 2080273188016762882, 99002, 'QC-RM-A', '质检测试原料A', 2062098763051089923, '深圳精密电机制造厂', 200.0000, 1.0000, 'QUALIFIED', '{}', 1, 'admin', '2026-07-23 22:26:06', 'COMPLETED', NULL, -1, -1, '2026-07-23 21:57:40', -1, '2026-07-23 21:57:40', '0');
INSERT INTO `qc_iqc` VALUES (2082124523750199298, '000000', 'IQC-20260728-001', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:22:25', 1, '2026-07-28 23:22:25', '0');
INSERT INTO `qc_iqc` VALUES (2082125048956751873, '000000', 'IQC-20260728-002', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:24:30', 1, '2026-07-28 23:24:30', '0');
INSERT INTO `qc_iqc` VALUES (2082127795194077186, '000000', 'IQC-20260728-003', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'PENDING', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-28 23:35:25', 1, '2026-07-28 23:35:25', '0');
INSERT INTO `qc_iqc` VALUES (2082128179698507778, '000000', 'IQC-20260728-004', 9001, 1001, 'M1001', NULL, NULL, NULL, 100.0000, 10.0000, 'UNQUALIFIED', '[{\"qty\": 3, \"code\": \"SCRATCH\", \"desc\": \"表面划痕\"}, {\"qty\": 1, \"code\": \"DENT\", \"desc\": \"凹痕\"}]', 1, 'admin', '2026-07-28 23:36:57', 'COMPLETED', 'E2E不合格', 103, 1, '2026-07-28 23:36:57', 1, '2026-07-28 23:36:57', '0');

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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`oqc_id`) USING BTREE,
  UNIQUE INDEX `uk_oqc_no`(`oqc_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES出货检验' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qc_oqc
-- ----------------------------
INSERT INTO `qc_oqc` VALUES (1, '000000', 'OQC-001', 10001, 1001, 'IF20251110001', '自行车', 50.0000, 5.0000, 'PENDING', NULL, 30005, '钱七', NULL, 'PENDING', 'OQC待检:出货前抽查5辆', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `qc_oqc` VALUES (2080312885934026753, '000000', 'OQC-20260723-001', 2080229404229697537, 99001, 'QC-FP-001', '质检联动测试成品', 20.0000, 1.0000, 'QUALIFIED', '{}', 1, 'admin', '2026-07-23 23:24:21', 'COMPLETED', NULL, -1, -1, '2026-07-23 23:23:37', -1, '2026-07-23 23:23:37', '0');

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
  `approval_time` datetime NULL DEFAULT NULL COMMENT '审批时间',
  `approval_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审批意见',
  `archive_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '归档原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`sop_id`) USING BTREE,
  UNIQUE INDEX `uk_sop_code`(`sop_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`sop_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SOP文档主档' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_document
-- ----------------------------
INSERT INTO `sop_document` VALUES (2081355255391621121, '000000', 'SOP-E2E-A', 'E2E Test SOP A', 'PRODUCTION', '1.1', 'ARCHIVED', 2081355254821195778, 'PDF', 'E2E test', 1, '2026-07-26 20:25:39', 'v1.1-approve', 'E2E-archive', NULL, 103, 1, '2026-07-26 20:25:37', 1, '2026-07-26 20:25:41', '1');
INSERT INTO `sop_document` VALUES (2081357146414551042, '000000', 'SOP20260726001', '测试sop', 'PRODUCTION', '1.0', 'ARCHIVED', 2081357120791547906, 'WORD', 'cs', 1, '2026-07-26 20:33:57', NULL, NULL, '', 103, 1, '2026-07-26 20:33:08', 1, '2026-07-26 20:33:08', '0');

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`binding_id`) USING BTREE,
  INDEX `idx_process`(`process_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工序SOP绑定' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_process_binding
-- ----------------------------
INSERT INTO `sop_process_binding` VALUES (2081355265004965890, '000000', 1, 'E2E-PROC', 'E2E Process', 'PROCESSING', 2081355255391621121, NULL, '0', 1, NULL, 103, 1, '2026-07-26 20:25:40', 1, '2026-07-26 20:25:41', '1');
INSERT INTO `sop_process_binding` VALUES (2081355265898352642, '000000', 1, 'E2E-PROC', 'E2E Process', 'PROCESSING', 2081355255391621121, NULL, '1', 2, NULL, 103, 1, '2026-07-26 20:25:40', 1, '2026-07-26 20:25:41', '1');

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
  `published_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CURRENT/HISTORICAL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`version_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'SOP版本历史' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_version
-- ----------------------------
INSERT INTO `sop_version` VALUES (2081355255400009730, '000000', 2081355255391621121, '1.0', 2081355254821195778, 'PDF', 'E2E test', '2026-07-26 20:25:38', 1, 'HISTORICAL', NULL, 103, 1, '2026-07-26 20:25:37', 1, '2026-07-26 20:25:37', '1');
INSERT INTO `sop_version` VALUES (2081355261779546113, '000000', 2081355255391621121, '1.1', 2081355254821195778, 'PDF', 'v1.1 revision', '2026-07-26 20:25:39', 1, 'CURRENT', NULL, 103, 1, '2026-07-26 20:25:39', 1, '2026-07-26 20:25:39', '1');
INSERT INTO `sop_version` VALUES (2081357146414551043, '000000', 2081357146414551042, '1.0', 2081357120791547906, 'WORD', 'cs', '2026-07-26 20:33:57', 1, 'CURRENT', NULL, 103, 1, '2026-07-26 20:33:08', 1, '2026-07-26 20:33:08', '0');

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
  `view_time` datetime NOT NULL COMMENT '查阅时间',
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_sop_id`(`sop_id`) USING BTREE,
  INDEX `idx_viewer`(`viewer_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'SOP查阅记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sop_view_log
-- ----------------------------

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
  `sample_size` int(11) NULL DEFAULT 5 COMMENT '样本大小',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`chart_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SPC控制图' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of spc_control_chart
-- ----------------------------
INSERT INTO `spc_control_chart` VALUES (1, '000000', 'CNC主轴温度控制图', 'X_BAR_R', 1, 1, 1, 85.2750, 76.6200, 67.9650, 5, 'ACTIVE', '数控车床主轴温度SPC监控', 100, 1, NULL, 1, '2026-07-26 02:00:03', '0');
INSERT INTO `spc_control_chart` VALUES (2, '000000', '装配关键尺寸控制图', 'X_BAR_R', 3, 1001, 3, 10.0500, 10.0000, 9.9500, 5, 'ACTIVE', '装配工序关键尺寸SPC监控(目标10mm)', 100, 1, NULL, NULL, NULL, '0');
INSERT INTO `spc_control_chart` VALUES (3, '000000', '焊接温度控制图', 'X_BAR_R', 4, 1, 2, 280.0000, 250.0000, 220.0000, 5, 'ACTIVE', '焊接温度SPC监控', 100, 1, NULL, NULL, NULL, '0');

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
  `collect_time` datetime NULL DEFAULT NULL COMMENT '采集时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_chart_id`(`chart_id`) USING BTREE,
  INDEX `idx_group`(`chart_id`, `sample_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES SPC数据点' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of spc_data_point
-- ----------------------------
INSERT INTO `spc_data_point` VALUES (2081077029524107266, '000000', 1, 1, NULL, 76.6200, '0', NULL, '2026-06-10 08:10:00', 103, 1, '2026-07-26 02:00:03', 1, '2026-07-26 02:00:03', '0');

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
  `last_maintain_time` datetime NULL DEFAULT NULL COMMENT '上次保养时间',
  `next_maintain_time` datetime NULL DEFAULT NULL COMMENT '下次保养时间(PERIODIC)',
  `next_maintain_count` int(11) NULL DEFAULT NULL COMMENT '下次保养次数阈值(BY_COUNT)',
  `next_maintain_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '下次保养时长阈值(BY_HOURS)',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否启用(1/0)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装保养计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tg_maintenance_plan
-- ----------------------------
INSERT INTO `tg_maintenance_plan` VALUES (2081272381422350338, '000000', 2081272377081245697, 'A-Plan-ByCount', 'BY_COUNT', 1000, NULL, NULL, 1000, NULL, '1', NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

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
  `maintain_time` datetime NOT NULL COMMENT '保养时间',
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装保养记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tg_maintenance_record
-- ----------------------------
INSERT INTO `tg_maintenance_record` VALUES (2081272385134309378, '000000', 2081272377081245697, NULL, 'PLANNED', '2026-07-26 14:56:19', 1, 'admin', 1100, 0, 2.50, 0.00, 'E2E routine maintain', 200.00, NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

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
  `last_maintain_time` datetime NULL DEFAULT NULL COMMENT '上次保养时间',
  `purchase_date` date NULL DEFAULT NULL COMMENT '采购日期',
  `purchase_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '采购成本',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`tooling_id`) USING BTREE,
  UNIQUE INDEX `uk_tooling_code`(`tooling_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_type`(`tooling_type`) USING BTREE,
  INDEX `idx_workshop`(`workshop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tg_tooling
-- ----------------------------
INSERT INTO `tg_tooling` VALUES (2081272377081245697, '000000', 'TG-E2E-A', 'E2E Test Mold A', 'MOLD', NULL, 1, 'Test WS', 'SCRAPPED', 1000, 100.00, 0, 0.00, '2026-07-26 14:56:19', NULL, NULL, 'E2E-Test-Scrap', 103, 1, '2026-07-26 14:56:18', 1, '2026-07-26 14:56:18', '0');
INSERT INTO `tg_tooling` VALUES (2081272388263260162, '000000', 'TG-E2E-B', 'E2E Test Gauge B', 'GAUGE', NULL, NULL, NULL, 'IDLE', 5000, NULL, 0, 0.00, NULL, NULL, NULL, NULL, 103, 1, '2026-07-26 14:56:20', 1, '2026-07-26 14:56:21', '1');

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
  `checkout_time` datetime NOT NULL COMMENT '领用时间',
  `return_time` datetime NULL DEFAULT NULL COMMENT '归还时间',
  `usage_count` int(11) NULL DEFAULT 1 COMMENT '使用次数',
  `usage_hours` decimal(14, 2) NULL DEFAULT NULL COMMENT '使用时长',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'OUT' COMMENT '状态: OUT/RETURNED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`usage_id`) USING BTREE,
  INDEX `idx_tooling_id`(`tooling_id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工装使用领用记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tg_usage_record
-- ----------------------------
INSERT INTO `tg_usage_record` VALUES (2081272379664936961, '000000', 2081272377081245697, 100, 'WO-E2E-001', 1, 'WS1', 1, 'admin', '2026-07-26 14:56:18', '2026-07-26 14:56:18', 500, 2.50, 'RETURNED', NULL, 103, 1, '2026-07-26 14:56:18', 1, '2026-07-26 14:56:18', '0');
INSERT INTO `tg_usage_record` VALUES (2081272382944882689, '000000', 2081272377081245697, NULL, NULL, NULL, NULL, 1, 'admin', '2026-07-26 14:56:19', '2026-07-26 14:56:19', 600, NULL, 'RETURNED', NULL, 103, 1, '2026-07-26 14:56:19', 1, '2026-07-26 14:56:19', '0');

SET FOREIGN_KEY_CHECKS = 1;
