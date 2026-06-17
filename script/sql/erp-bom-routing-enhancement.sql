-- ============================================================
-- BOM 增强 + 工艺路线 数据库变更脚本
-- 执行顺序：先执行本脚本，再启动应用
-- ============================================================

-- ============================================================
-- 1. 新建工序定义表 bd_process
-- ============================================================
DROP TABLE IF EXISTS `bd_process`;
CREATE TABLE `bd_process` (
  `process_id`       BIGINT(20) NOT NULL COMMENT '工序ID',
  `tenant_id`        VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_code`     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `process_name`     VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `process_desc`     VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序描述',
  `process_category` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序类别: PROCESSING-加工, ASSEMBLY-装配, INSPECTION-检验, PACKAGING-包装',
  `workstation_id`   BIGINT(20) DEFAULT NULL COMMENT '默认工作中心ID',
  `workstation_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认工作中心编码',
  `workstation_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认工作中心名称',
  `standard_hours`   DECIMAL(10,4) DEFAULT NULL COMMENT '标准工时(小时/件)',
  `setup_hours`      DECIMAL(10,4) DEFAULT NULL COMMENT '准备工时(小时)',
  `labor_count`      INT(11) DEFAULT NULL COMMENT '标准人工数',
  `machine_count`    INT(11) DEFAULT NULL COMMENT '标准机台数',
  `is_key_process`   TINYINT(1) DEFAULT 0 COMMENT '是否关键工序: 0-否 1-是',
  `is_outsource`     TINYINT(1) DEFAULT 0 COMMENT '是否委外工序: 0-否 1-是',
  `quality_required` TINYINT(1) DEFAULT 0 COMMENT '是否需要质检: 0-否 1-是',
  `status`           TINYINT(4) DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark`           VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `attr1`            VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段1',
  `attr2`            VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段2',
  `attr3`            BIGINT(20) DEFAULT NULL COMMENT '预留字段3',
  `attr4`            BIGINT(20) DEFAULT NULL COMMENT '预留字段4',
  `create_dept`      BIGINT(20) DEFAULT NULL COMMENT '创建部门',
  `create_by`        BIGINT(20) DEFAULT NULL COMMENT '创建人',
  `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT(20) DEFAULT NULL COMMENT '更新人',
  `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`         CHAR(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`process_id`),
  UNIQUE KEY `uk_process_code` (`process_code`, `tenant_id`),
  INDEX `idx_workstation` (`workstation_id`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序定义表' ROW_FORMAT=DYNAMIC;

-- 示例工序数据
INSERT INTO `bd_process` VALUES (1, '000000', 'CUT', '切割', '板材切割工序', 'PROCESSING', NULL, NULL, NULL, 0.5000, 0.2500, 1, 1, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');
INSERT INTO `bd_process` VALUES (2, '000000', 'WELD', '焊接', '金属焊接工序', 'PROCESSING', NULL, NULL, NULL, 1.0000, 0.5000, 2, 1, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');
INSERT INTO `bd_process` VALUES (3, '000000', 'ASM', '装配', '产品装配工序', 'ASSEMBLY', NULL, NULL, NULL, 2.0000, 0.5000, 2, 0, 1, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');
INSERT INTO `bd_process` VALUES (4, '000000', 'QC', '质检', '成品质量检验', 'INSPECTION', NULL, NULL, NULL, 0.5000, 0.1000, 1, 0, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');
INSERT INTO `bd_process` VALUES (5, '000000', 'PKG', '包装', '成品包装工序', 'PACKAGING', NULL, NULL, NULL, 0.2500, 0.1000, 1, 0, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, NOW(), 1, NOW(), '0');

-- ============================================================
-- 2. 新建工艺路线主表 bd_routing
-- ============================================================
DROP TABLE IF EXISTS `bd_routing`;
CREATE TABLE `bd_routing` (
  `routing_id`       BIGINT(20) NOT NULL COMMENT '工艺路线ID',
  `tenant_id`        VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `routing_code`     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线编码',
  `routing_name`     VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线名称',
  `routing_desc`     VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线描述',
  `material_id`      BIGINT(20) DEFAULT NULL COMMENT '适用物料ID(可为空表示通用)',
  `material_code`    VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用物料编码',
  `material_name`    VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用物料名称',
  `routing_version`  VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1.0' COMMENT '版本号',
  `is_current`       TINYINT(1) DEFAULT 1 COMMENT '是否当前版本: 0-否 1-是',
  `total_steps`      INT(11) DEFAULT 0 COMMENT '总工序数',
  `total_hours`      DECIMAL(10,4) DEFAULT NULL COMMENT '总标准工时(小时)',
  `effective_date`   DATE DEFAULT NULL COMMENT '生效日期',
  `expiry_date`      DATE DEFAULT NULL COMMENT '失效日期',
  `status`           TINYINT(4) DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark`           VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `attr1`            VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段1',
  `attr2`            VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段2',
  `attr3`            BIGINT(20) DEFAULT NULL COMMENT '预留字段3',
  `attr4`            BIGINT(20) DEFAULT NULL COMMENT '预留字段4',
  `create_dept`      BIGINT(20) DEFAULT NULL COMMENT '创建部门',
  `create_by`        BIGINT(20) DEFAULT NULL COMMENT '创建人',
  `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT(20) DEFAULT NULL COMMENT '更新人',
  `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`         CHAR(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`routing_id`),
  UNIQUE KEY `uk_routing_code_version` (`routing_code`, `routing_version`, `tenant_id`),
  INDEX `idx_material` (`material_id`),
  INDEX `idx_current` (`is_current`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺路线主表' ROW_FORMAT=DYNAMIC;

-- ============================================================
-- 3. 新建工艺路线步骤表 bd_routing_step
-- ============================================================
DROP TABLE IF EXISTS `bd_routing_step`;
CREATE TABLE `bd_routing_step` (
  `step_id`          BIGINT(20) NOT NULL COMMENT '步骤ID',
  `tenant_id`        VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `routing_id`       BIGINT(20) NOT NULL COMMENT '工艺路线ID',
  `sequence_num`     INT(11) NOT NULL COMMENT '步骤序号',
  `process_id`       BIGINT(20) NOT NULL COMMENT '工序ID',
  `process_code`     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序编码',
  `process_name`     VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序名称',
  `step_name`        VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '步骤名称',
  `workstation_id`   BIGINT(20) DEFAULT NULL COMMENT '工作中心ID(覆盖工序默认)',
  `workstation_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作中心编码',
  `workstation_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作中心名称',
  `standard_hours`   DECIMAL(10,4) DEFAULT NULL COMMENT '标准工时(覆盖工序默认)',
  `setup_hours`      DECIMAL(10,4) DEFAULT NULL COMMENT '准备工时(覆盖工序默认)',
  `labor_count`      INT(11) DEFAULT NULL COMMENT '标准人工数(覆盖工序默认)',
  `machine_count`    INT(11) DEFAULT NULL COMMENT '标准机台数(覆盖工序默认)',
  `is_key_process`   TINYINT(1) DEFAULT 0 COMMENT '是否关键工序',
  `is_outsource`     TINYINT(1) DEFAULT 0 COMMENT '是否委外工序',
  `quality_required` TINYINT(1) DEFAULT 0 COMMENT '是否需要质检',
  `wait_time`        DECIMAL(10,4) DEFAULT 0 COMMENT '等待时间(小时)',
  `transfer_time`    DECIMAL(10,4) DEFAULT 0 COMMENT '转移时间(小时)',
  `description`      VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '步骤说明/作业指导',
  `remark`           VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`step_id`),
  INDEX `idx_routing` (`routing_id`),
  INDEX `idx_process` (`process_id`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺路线步骤表' ROW_FORMAT=DYNAMIC;

-- ============================================================
-- 4. 新建BOM替代料表 bd_bom_alternative
-- ============================================================
DROP TABLE IF EXISTS `bd_bom_alternative`;
CREATE TABLE `bd_bom_alternative` (
  `alternative_id`    BIGINT(20) NOT NULL COMMENT '替代料ID',
  `tenant_id`         VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `detail_id`         BIGINT(20) NOT NULL COMMENT 'BOM明细ID',
  `bom_id`            BIGINT(20) NOT NULL COMMENT 'BOM主表ID',
  `alt_material_id`   BIGINT(20) NOT NULL COMMENT '替代物料ID',
  `alt_material_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '替代物料编码',
  `alt_material_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '替代物料名称',
  `alt_quantity`      DECIMAL(18,6) NOT NULL COMMENT '替代用量',
  `priority`          INT(11) DEFAULT 1 COMMENT '优先级(数字越小优先级越高)',
  `substitution_type` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'FULL' COMMENT '替代类型: FULL-完全替代, PARTIAL-部分替代, INTERCHANGEABLE-互换',
  `effective_date`    DATE DEFAULT NULL COMMENT '生效日期',
  `expiry_date`       DATE DEFAULT NULL COMMENT '失效日期',
  `status`            TINYINT(4) DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark`            VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_dept`       BIGINT(20) DEFAULT NULL COMMENT '创建部门',
  `create_by`         BIGINT(20) DEFAULT NULL COMMENT '创建人',
  `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT(20) DEFAULT NULL COMMENT '更新人',
  `update_time`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`          CHAR(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`alternative_id`),
  INDEX `idx_detail_id` (`detail_id`),
  INDEX `idx_bom_id` (`bom_id`),
  INDEX `idx_alt_material` (`alt_material_id`),
  INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM替代料表' ROW_FORMAT=DYNAMIC;

-- ============================================================
-- 5. 变更现有BOM表 - 新增字段
-- ============================================================

-- bd_bom 新增 bom_type 字段
ALTER TABLE `bd_bom` ADD COLUMN `bom_type` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MBOM' COMMENT 'BOM类型: EBOM-工程BOM, MBOM-制造BOM, SBOM-服务BOM, CBOM-配置BOM' AFTER `source_type`;

-- bd_bom 新增 routing_id 字段
ALTER TABLE `bd_bom` ADD COLUMN `routing_id` BIGINT(20) DEFAULT NULL COMMENT '关联工艺路线ID' AFTER `bom_type`;

-- bd_bom_detail 新增 process_id 字段
ALTER TABLE `bd_bom_detail` ADD COLUMN `process_id` BIGINT(20) DEFAULT NULL COMMENT '工序ID(关联bd_process)' AFTER `workstation_id`;

-- bd_bom_detail 新增 routing_step_id 字段
ALTER TABLE `bd_bom_detail` ADD COLUMN `routing_step_id` BIGINT(20) DEFAULT NULL COMMENT '工艺路线步骤ID(关联bd_routing_step)' AFTER `process_id`;

-- ============================================================
-- 6. 预留字段 - 为后续 ECN/成本卷积/虚拟件准备
-- ============================================================

-- bd_bom 预留: 变更原因、变更通知号、是否虚拟件
-- ALTER TABLE `bd_bom` ADD COLUMN `change_reason` VARCHAR(500) COMMENT '变更原因';
-- ALTER TABLE `bd_bom` ADD COLUMN `ecn_no` VARCHAR(64) COMMENT '变更通知号';
-- ALTER TABLE `bd_bom` ADD COLUMN `is_phantom` TINYINT(1) DEFAULT 0 COMMENT '是否虚拟件: 0-否 1-是';
