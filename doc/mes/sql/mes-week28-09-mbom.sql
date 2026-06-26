-- =============================================
-- NexusERP - MES模块 Week 28 MBOM子系统 DDL
-- 目标库: ry-mes
-- 说明:
--   mes_mbom: MES制造BOM头(1:1关联ERP bd_bom.bom_id,承载ERP侧迁出的工序/工位关联)
--   mes_mbom_line: MBOM明细行(每个子件关联到工序/工位/作业内容)
--   pro_work_order_bom_line: 工单下达时的MBOM快照(隔离ERP后续编辑,与pro_work_order_step一致策略)
-- 职责划分: ERP BOM = 成本/物料(用量、损耗、成本滚算), MES MBOM = 制造(子件->工序->工位)
-- 约定对齐: tenant_id VARCHAR(20) DEFAULT '000000', del_flag CHAR(1), 状态列 VARCHAR, BIGINT PK
-- 创建时间: 2026-06-25
-- =============================================
USE `ry-mes`;

-- ----------------------------
-- mes_mbom: MES制造BOM头(1:1关联ERP bom_id)
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom`;
CREATE TABLE `mes_mbom` (
    `mbom_id`      BIGINT NOT NULL COMMENT 'MBOM ID(主键)',
    `tenant_id`    VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `bom_id`       BIGINT NOT NULL COMMENT '关联ERP bd_bom.bom_id',
    `routing_id`   BIGINT COMMENT '制造工艺路线ID(可继承自bd_bom.routing_id或MES侧指定)',
    `mbom_version` VARCHAR(32) COMMENT 'MBOM版本号',
    `mbom_status`  VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/FROZEN',
    `remark`       VARCHAR(500) COMMENT '备注',
    `create_dept`  BIGINT COMMENT '创建部门',
    `create_by`    BIGINT COMMENT '创建者',
    `create_time`  DATETIME COMMENT '创建时间',
    `update_by`    BIGINT COMMENT '更新者',
    `update_time`  DATETIME COMMENT '更新时间',
    `del_flag`     CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`mbom_id`),
    UNIQUE INDEX `uk_bom_id` (`bom_id`),
    INDEX `idx_routing_id` (`routing_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES制造BOM头';

-- ----------------------------
-- mes_mbom_line: MBOM明细行(承载从ERP bd_bom_detail迁移出的工序/工位/作业内容)
-- bom_detail_id可空: 允许MES增加非ERP BOM的消耗料(MES附加件/辅料)
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom_line`;
CREATE TABLE `mes_mbom_line` (
    `mbom_line_id`        BIGINT NOT NULL COMMENT 'MBOM明细ID(主键)',
    `tenant_id`           VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `mbom_id`             BIGINT NOT NULL COMMENT '关联mes_mbom.mbom_id',
    `bom_id`              BIGINT NOT NULL COMMENT '冗余ERP bom_id',
    `bom_detail_id`       BIGINT COMMENT '关联ERP bd_bom_detail.detail_id(可空,MES附加消耗料时为空)',
    `child_material_id`   BIGINT NOT NULL COMMENT '子项物料ID',
    `quantity_per_parent` DECIMAL(18, 6) NOT NULL COMMENT '单位父项用量',
    `loss_rate`           DECIMAL(8, 4) DEFAULT 0.0000 COMMENT '损耗率(%)',
    `routing_step_id`     BIGINT COMMENT 'ERP bd_routing_step.step_id(工序步骤)',
    `process_id`          BIGINT COMMENT 'ERP bd_process.process_id(工序)',
    `workstation_id`      BIGINT COMMENT 'ERP bd_workstation.workstation_id(工位)',
    `operation_content`   VARCHAR(500) COMMENT '作业内容',
    `backflush_flag`      TINYINT(1) DEFAULT 0 COMMENT '是否倒冲 0否1是',
    `sort_order`          INT DEFAULT 0 COMMENT '显示排序',
    `remark`              VARCHAR(500) COMMENT '备注',
    `create_dept`         BIGINT COMMENT '创建部门',
    `create_by`           BIGINT COMMENT '创建者',
    `create_time`         DATETIME COMMENT '创建时间',
    `update_by`           BIGINT COMMENT '更新者',
    `update_time`         DATETIME COMMENT '更新时间',
    `del_flag`            CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`mbom_line_id`),
    INDEX `idx_mbom_id` (`mbom_id`),
    INDEX `idx_bom_id` (`bom_id`),
    INDEX `idx_bom_detail_id` (`bom_detail_id`),
    INDEX `idx_routing_step_id` (`routing_step_id`),
    INDEX `idx_child_material_id` (`child_material_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES制造BOM明细';

-- ----------------------------
-- pro_work_order_bom_line: 工单下达时的MBOM快照(隔离ERP后续编辑)
-- 与pro_work_order_step一致策略: 快照后不再跟踪MBOM变更,保证追溯完整性
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_bom_line`;
CREATE TABLE `pro_work_order_bom_line` (
    `wo_bom_line_id`      BIGINT NOT NULL COMMENT '快照明细ID(主键)',
    `tenant_id`           VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `mes_wo_id`           BIGINT NOT NULL COMMENT '关联MES工单ID',
    `mbom_line_id`        BIGINT COMMENT '源mes_mbom_line.mbom_line_id(可空,无MBOM时)',
    `bom_id`              BIGINT COMMENT 'ERP BOM ID',
    `bom_detail_id`       BIGINT COMMENT 'ERP明细ID(溯源)',
    `child_material_id`   BIGINT NOT NULL COMMENT '子项物料ID',
    `child_material_code` VARCHAR(64) COMMENT '物料编码(冗余)',
    `child_material_name` VARCHAR(200) COMMENT '物料名称(冗余)',
    `quantity_per_parent` DECIMAL(18, 6) NOT NULL COMMENT '单位父项用量',
    `loss_rate`           DECIMAL(8, 4) DEFAULT 0.0000 COMMENT '损耗率(%)',
    `total_quantity`      DECIMAL(18, 6) COMMENT '含损耗用量 = qty × (1 + loss/100)',
    `routing_step_id`     BIGINT COMMENT '工序步骤ID',
    `sequence_num`        INT COMMENT '工序序号(冗余)',
    `process_id`          BIGINT COMMENT '工序ID',
    `process_code`        VARCHAR(64) COMMENT '工序编码(冗余)',
    `process_name`        VARCHAR(200) COMMENT '工序名称(冗余)',
    `workstation_id`      BIGINT COMMENT '工位ID',
    `workstation_code`    VARCHAR(64) COMMENT '工位编码(冗余)',
    `workstation_name`    VARCHAR(200) COMMENT '工位名称(冗余)',
    `operation_content`   VARCHAR(500) COMMENT '作业内容',
    `backflush_flag`      TINYINT(1) DEFAULT 0 COMMENT '是否倒冲 0否1是',
    `sort_order`          INT DEFAULT 0 COMMENT '显示排序',
    `remark`              VARCHAR(500) COMMENT '备注',
    `create_dept`         BIGINT COMMENT '创建部门',
    `create_by`           BIGINT COMMENT '创建者',
    `create_time`         DATETIME COMMENT '创建时间',
    `update_by`           BIGINT COMMENT '更新者',
    `update_time`         DATETIME COMMENT '更新时间',
    `del_flag`            CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`wo_bom_line_id`),
    INDEX `idx_mes_wo_id` (`mes_wo_id`),
    INDEX `idx_child_material_id` (`child_material_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单BOM明细快照';
