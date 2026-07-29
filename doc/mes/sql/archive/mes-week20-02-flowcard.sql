-- =============================================
-- NexusERP - MES模块 Week 20 流转卡DDL
-- 数据库: ry-mes
-- 说明: MES流转卡 + 流转卡工序记录
-- 创建时间: 2026-06-19
-- 主键策略: 雪花算法(bigint)
-- 约定对齐: 与 ry-mes-week17.sql 保持一致
--   - tenant_id varchar(20) DEFAULT '000000'
--   - 数量字段 decimal(18,4)
--   - del_flag char(1) DEFAULT '0'
--   - material_name varchar(200)
--   - 工单ID字段名 mes_wo_id
-- =============================================

USE `ry-mes`;

-- -----------------------------------------------------------
-- 1. MES流转卡表 (pro_flow_card)
-- -----------------------------------------------------------
-- 关联关系:
--   mes_wo_id -> pro_work_order.mes_wo_id
-- 状态机: CREATED -> RELEASED -> IN_PROGRESS -> COMPLETED -> CLOSED
DROP TABLE IF EXISTS `pro_flow_card`;
CREATE TABLE `pro_flow_card` (
    `card_id`          bigint(20)     NOT NULL COMMENT '流转卡ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `card_no`          varchar(64)    NOT NULL DEFAULT '' COMMENT '流转卡编号',
    `mes_wo_id`        bigint(20)     NOT NULL COMMENT '关联MES工单ID',
    `wo_no`            varchar(64)    NULL DEFAULT NULL COMMENT '工单编号(冗余)',
    `material_id`      bigint(20)     NULL DEFAULT NULL COMMENT '物料ID',
    `material_code`    varchar(64)    NULL DEFAULT NULL COMMENT '物料编码(冗余)',
    `material_name`    varchar(200)   NULL DEFAULT NULL COMMENT '物料名称(冗余)',
    `planned_qty`      decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '计划数量',
    `completed_qty`    decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '已完成数量',
    `scrap_qty`        decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '报废数量',
    `current_step_id`  bigint(20)     NULL DEFAULT NULL COMMENT '当前工序步骤记录ID',
    `current_step_seq` int(4)         NULL DEFAULT NULL COMMENT '当前工序序号',
    `card_status`      varchar(32)    NULL DEFAULT 'CREATED' COMMENT '流转卡状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    `barcode`          varchar(128)   NULL DEFAULT NULL COMMENT '流转卡条码',
    `remark`           varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `create_dept`      bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`         char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`card_id`) USING BTREE,
    UNIQUE INDEX `uk_card_no` (`card_no`) USING BTREE,
    INDEX `idx_mes_wo_id` (`mes_wo_id`) USING BTREE,
    INDEX `idx_card_status` (`card_status`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MES流转卡表' ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 2. MES流转卡工序记录表 (pro_flow_card_step)
-- -----------------------------------------------------------
-- 关联关系:
--   card_id     -> pro_flow_card.card_id
--   mes_wo_id   -> pro_work_order.mes_wo_id (冗余)
--   wo_step_id  -> pro_work_order_step.step_id (关联工单工序模板)
-- 状态: PENDING -> IN_PROGRESS -> COMPLETED/SKIPPED
DROP TABLE IF EXISTS `pro_flow_card_step`;
CREATE TABLE `pro_flow_card_step` (
    `step_record_id`   bigint(20)     NOT NULL COMMENT '工序记录ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `card_id`          bigint(20)     NOT NULL COMMENT '关联流转卡ID',
    `card_no`          varchar(64)    NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
    `mes_wo_id`        bigint(20)     NOT NULL COMMENT '关联MES工单ID(冗余便于查询)',
    `wo_step_id`       bigint(20)     NULL DEFAULT NULL COMMENT '关联工单工序步骤ID(pro_work_order_step.step_id)',
    `sequence_num`     int(4)         NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
    `process_id`       bigint(20)     NULL DEFAULT NULL COMMENT '工序ID(来自ERP bd_routing_step)',
    `process_code`     varchar(64)    NULL DEFAULT NULL COMMENT '工序编码(冗余)',
    `process_name`     varchar(200)   NULL DEFAULT NULL COMMENT '工序名称(冗余)',
    `workcenter_id`    bigint(20)     NULL DEFAULT NULL COMMENT '工作中心ID',
    `workcenter_code`  varchar(64)    NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
    `is_key_process`   tinyint(1)     NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
    `step_status`      varchar(32)    NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
    `operator_id`      bigint(20)     NULL DEFAULT NULL COMMENT '操作员ID',
    `operator_name`    varchar(64)    NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
    `planned_qty`      decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '计划数量',
    `good_qty`         decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '良品数量',
    `defect_qty`       decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '不良品数量',
    `scrap_qty`        decimal(18,4)  NULL DEFAULT 0.0000 COMMENT '报废数量',
    `actual_start_time` datetime      NULL DEFAULT NULL COMMENT '实际开始时间',
    `actual_end_time`  datetime       NULL DEFAULT NULL COMMENT '实际结束时间',
    `remark`           varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `create_dept`      bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`         char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`step_record_id`) USING BTREE,
    INDEX `idx_card_id` (`card_id`) USING BTREE,
    INDEX `idx_mes_wo_id` (`mes_wo_id`) USING BTREE,
    INDEX `idx_step_status` (`step_status`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MES流转卡工序记录表' ROW_FORMAT = DYNAMIC;
