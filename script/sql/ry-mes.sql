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

 Date: 21/06/2026 11:25:48
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`point_id`) USING BTREE,
  UNIQUE INDEX `uk_point_code`(`point_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES数据采集点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dc_collection_point
-- ----------------------------

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
  `collect_time` datetime NOT NULL COMMENT '采集时间',
  `collect_by` bigint(20) NULL DEFAULT NULL COMMENT '采集人(手动时)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_collect_time`(`collect_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES采集数据记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dc_data_record
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`equipment_id`) USING BTREE,
  UNIQUE INDEX `uk_equipment_code`(`equipment_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_workcenter_id`(`workcenter_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_equipment
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_next_date`(`next_date`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备保养计划' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_maintenance_plan
-- ----------------------------

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
  `repair_start` datetime NULL DEFAULT NULL COMMENT '维修开始时间',
  `repair_end` datetime NULL DEFAULT NULL COMMENT '维修结束时间',
  `downtime_hours` decimal(10, 2) NULL DEFAULT NULL COMMENT '停机时长(小时)',
  `repair_cost` decimal(14, 2) NULL DEFAULT NULL COMMENT '维修成本',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`repair_id`) USING BTREE,
  UNIQUE INDEX `uk_repair_no`(`repair_no`) USING BTREE,
  INDEX `idx_equipment_id`(`equipment_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES设备维修记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of eq_repair
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`mbom_id`) USING BTREE,
  UNIQUE INDEX `uk_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_routing_id`(`routing_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES制造BOM头' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mes_mbom
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
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
  `call_time` datetime NULL DEFAULT NULL COMMENT '呼叫时间',
  `respond_time` datetime NULL DEFAULT NULL COMMENT '响应时间',
  `resolve_time` datetime NULL DEFAULT NULL COMMENT '解决时间',
  `duration_minutes` int(11) NULL DEFAULT NULL COMMENT '持续时长(分钟, 解决时计算)',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
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
  `plan_start_time` datetime NULL DEFAULT NULL,
  `plan_end_time` datetime NULL DEFAULT NULL,
  `actual_start_time` datetime NULL DEFAULT NULL,
  `actual_end_time` datetime NULL DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
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
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
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
  PRIMARY KEY (`mes_wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_erp_wo_id`(`erp_wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES工单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pro_work_order
-- ----------------------------

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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`wo_bom_line_id`) USING BTREE,
  INDEX `idx_mes_wo_id`(`mes_wo_id`) USING BTREE,
  INDEX `idx_child_material_id`(`child_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES工单BOM明细快照' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pro_work_order_bom_line
-- ----------------------------

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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
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
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`chart_id`) USING BTREE,
  INDEX `idx_point_id`(`point_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SPC控制图' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of spc_control_chart
-- ----------------------------

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
  `collect_time` datetime NULL DEFAULT NULL COMMENT '采集时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`data_id`) USING BTREE,
  INDEX `idx_chart_id`(`chart_id`) USING BTREE,
  INDEX `idx_group`(`chart_id`, `sample_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MES SPC数据点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of spc_data_point
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
