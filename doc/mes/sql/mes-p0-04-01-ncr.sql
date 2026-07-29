-- =============================================
-- NexusERP - MES模块 P0-4 NCR 不合格品+返工 DDL
-- 目标库: ry-mes
-- 内容: 5 张 ncr_ 表 + pro_work_order 加 source_type 列
-- 幂等: CREATE TABLE IF NOT EXISTS + information_schema 守卫 ALTER
-- 规范库镜像: 同步并入 script/sql/ry-mes.sql
-- =============================================
USE `ry-mes`;

-- -----------------------------------------------------------
-- 0. 扩展 pro_work_order: 加 source_type(工单来源 ERP/REWORK/MANUAL)
--    幂等: 已存在则跳过
-- -----------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pro_work_order' AND COLUMN_NAME = 'source_type');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `pro_work_order` ADD COLUMN `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''ERP'' COMMENT ''工单来源(ERP/REWORK/MANUAL)''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------
-- 1. ncr_order - NCR 不合格品单
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ncr_order` (
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

-- -----------------------------------------------------------
-- 2. ncr_defect - NCR 缺陷明细
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ncr_defect` (
  `defect_id` bigint(20) NOT NULL COMMENT '缺陷明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `ncr_id` bigint(20) NOT NULL COMMENT 'NCR ID',
  `defect_code_id` bigint(20) NULL DEFAULT NULL COMMENT '缺陷代码ID(ncr_defect_code,可空-自动建单时仅记defectCode字符串,REVIEW时关联正式代码库)',
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

-- -----------------------------------------------------------
-- 3. ncr_defect_code - 缺陷代码库(分层)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ncr_defect_code` (
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

-- -----------------------------------------------------------
-- 4. ncr_rework_order - 返工工单
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ncr_rework_order` (
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

-- -----------------------------------------------------------
-- 5. ncr_8d_report - 8D 报告
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ncr_8d_report` (
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
