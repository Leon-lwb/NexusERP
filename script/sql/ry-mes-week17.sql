-- =============================================
-- NexusERP - MES模块 Week 17 初始化SQL
-- 数据库: ry-mes
-- 说明: MES Phase 1 最小可运行表结构（仅包含工单主表和工序步骤表）
-- 创建时间: 2026-06-16
-- 主键策略: 与ERP保持一致，使用雪花算法(bigint)，无自增
-- =============================================

-- -----------------------------------------------------------
-- 1. 建库
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `ry-mes`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ry-mes`;

-- -----------------------------------------------------------
-- 2. MES工单表 (pro_work_order)
-- -----------------------------------------------------------
-- 关联关系:
--   erp_wo_id -> erp.prd_work_order.wo_id (来源ERP工单)
--   material_id -> erp.bd_material.material_id (通过桥接查询)
--   workshop_id -> erp.bd_workshop.workshop_id (通过桥接查询)
-- 状态机: CREATED -> RELEASED -> IN_PROGRESS -> COMPLETED/CANCELLED
DROP TABLE IF EXISTS `pro_work_order`;
CREATE TABLE `pro_work_order` (
    `mes_wo_id`        bigint(20)     NOT NULL COMMENT 'MES工单ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `erp_wo_id`        bigint(20)     NULL DEFAULT NULL COMMENT '关联ERP工单ID',
    `wo_no`            varchar(64)    NOT NULL DEFAULT '' COMMENT 'MES工单编号(继承或派生)',
    `material_id`      bigint(20)     NULL DEFAULT NULL COMMENT '产品物料ID',
    `material_code`    varchar(64)    NULL DEFAULT NULL COMMENT '产品编码(冗余)',
    `material_name`    varchar(200)   NULL DEFAULT NULL COMMENT '产品名称(冗余)',
    `planned_qty`      decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '计划数量',
    `completed_qty`    decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '已完成数量',
    `scrap_qty`        decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '报废数量',
    `unit_id`          bigint(20)     NULL DEFAULT NULL COMMENT '单位ID',
    `unit_name`        varchar(32)    NULL DEFAULT NULL COMMENT '单位名称',
    `plan_start_date`  datetime       NULL DEFAULT NULL COMMENT '计划开工日期',
    `plan_end_date`    datetime       NULL DEFAULT NULL COMMENT '计划完工日期',
    `actual_start_date`datetime       NULL DEFAULT NULL COMMENT '实际开工时间',
    `actual_end_date`  datetime       NULL DEFAULT NULL COMMENT '实际完工时间',
    `workshop_id`      bigint(20)     NULL DEFAULT NULL COMMENT '车间ID',
    `workshop_name`    varchar(100)   NULL DEFAULT NULL COMMENT '车间名称(冗余)',
    `bom_id`           bigint(20)     NULL DEFAULT NULL COMMENT 'BOM ID',
    `routing_id`       bigint(20)     NULL DEFAULT NULL COMMENT '工艺路线ID(MES侧维护)',
    `wo_status`        varchar(32)    NULL DEFAULT 'CREATED' COMMENT '工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED',
    `priority`         int(4)         NULL DEFAULT 5 COMMENT '优先级(1最高,9最低)',
    `remark`           varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `select_flow_id`   bigint(20)     NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
    `create_dept`      bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`         char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`mes_wo_id`) USING BTREE,
    UNIQUE INDEX `uk_wo_no` (`wo_no`) USING BTREE,
    INDEX `idx_erp_wo_id` (`erp_wo_id`) USING BTREE,
    INDEX `idx_material_id` (`material_id`) USING BTREE,
    INDEX `idx_wo_status` (`wo_status`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MES工单表' ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 3. MES工单工序步骤表 (pro_work_order_step)
-- -----------------------------------------------------------
-- 关联关系:
--   mes_wo_id -> pro_work_order.mes_wo_id
--   process_id -> ERP工序ID(通过桥接查询bd_routing_step)
-- 状态: PENDING -> READY -> IN_PROGRESS -> COMPLETED/SKIPPED
DROP TABLE IF EXISTS `pro_work_order_step`;
CREATE TABLE `pro_work_order_step` (
    `step_id`          bigint(20)     NOT NULL COMMENT '工序步骤ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `mes_wo_id`        bigint(20)     NOT NULL COMMENT '所属MES工单ID',
    `sequence_num`     int(4)         NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
    `process_id`       bigint(20)     NULL DEFAULT NULL COMMENT 'ERP工序ID(bd_routing_step.routing_step_id)',
    `process_code`     varchar(64)    NULL DEFAULT NULL COMMENT '工序编码(冗余)',
    `process_name`     varchar(200)   NULL DEFAULT NULL COMMENT '工序名称(冗余)',
    `workcenter_id`    bigint(20)     NULL DEFAULT NULL COMMENT '工作中心ID',
    `workcenter_code`  varchar(64)    NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
    `planned_qty`      decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '计划数量',
    `completed_qty`    decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '完成数量',
    `scrap_qty`        decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '报废数量',
    `step_status`      varchar(32)    NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED',
    `is_key_process`   tinyint(1)     NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
    `start_time`       datetime       NULL DEFAULT NULL COMMENT '开始时间',
    `end_time`         datetime       NULL DEFAULT NULL COMMENT '结束时间',
    `remark`           varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `create_dept`      bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`         char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`step_id`) USING BTREE,
    INDEX `idx_mes_wo_id` (`mes_wo_id`) USING BTREE,
    INDEX `idx_step_status` (`step_status`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MES工单工序步骤表' ROW_FORMAT = DYNAMIC;
