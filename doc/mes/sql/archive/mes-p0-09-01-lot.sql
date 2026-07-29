-- =============================================
-- NexusERP - MES模块 P0-9 批次(Lot)管理 DDL
-- 目标库: ry-mes
-- 说明: 批次主档 + 工单批次消耗 + SN批次绑定
-- 状态机: IN_STOCK -> IN_USE -> CONSUMED
--                  └-> QUARANTINED -> SCRAPPED
-- QC状态: PENDING -> PASS/FAIL (可由 IQC 完成回写,见 iqc_id)
-- FEFO: 按 expiry_date ASC, produced_date ASC, lot_id ASC 排序推荐
-- 约定对齐: tenant_id VARCHAR(20) DEFAULT '000000', del_flag CHAR(1), 状态列 VARCHAR
-- 创建时间: 2026-07-24 (2026-07-25 整合: 表名去冗余 lot 中段 + lt_lot 加 iqc_id)
-- 所属计划: doc/mes/plans/2026-08-03-mes-p0-9-lot-management-implementation-plan.md
--   整合记录: 原 mes-p0-09-04-rename-tables.sql 的改名(lt_sn_lot_binding->lt_sn_binding /
--             lt_wo_lot_consumption->lt_wo_consumption) 已并入本脚本直接建最终名;
--             原 mes-p0-09-01-lot.sql 建错名的问题在此根除。
--   部署说明: 若环境已跑过旧版 01+04(已改名),本脚本 CREATE TABLE IF NOT EXISTS 幂等,
--             但需补执行下方 lt_lot 加 iqc_id 的 ALTER(见末尾)。
-- =============================================
USE `ry-mes`;

-- lt_lot: MES批次主档
CREATE TABLE IF NOT EXISTS lt_lot (
    lot_id           BIGINT NOT NULL COMMENT '批次ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    lot_code         VARCHAR(64) NOT NULL COMMENT '批次编码',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    supplier_id      BIGINT COMMENT '供应商ID',
    supplier_name   VARCHAR(128) COMMENT '供应商名称(冗余)',
    supplier_lot    VARCHAR(64) COMMENT '供应商批次号',
    quantity         DECIMAL(14,4) NOT NULL COMMENT '批次数量',
    consumed_qty    DECIMAL(14,4) DEFAULT 0 COMMENT '已消耗数量',
    produced_date    DATE COMMENT '生产日期',
    expiry_date      DATE COMMENT '有效期',
    status           VARCHAR(16) DEFAULT 'IN_STOCK' COMMENT '状态: IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED',
    qc_status        VARCHAR(16) DEFAULT 'PENDING' COMMENT 'QC状态: PENDING/PASS/FAIL',
    iqc_id           BIGINT NULL COMMENT 'IQC检验单ID(关联qc_iqc.iqc_id,可空;关联后IQC完成自动回写qc_status)',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept     BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (lot_id),
    UNIQUE INDEX uk_lot_code (lot_code),
    INDEX idx_material (material_id),
    INDEX idx_status (status),
    INDEX idx_qc_status (qc_status),
    INDEX idx_iqc_id (iqc_id),
    INDEX idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES批次主档';

-- lt_wo_consumption: MES工单批次消耗记录
CREATE TABLE IF NOT EXISTS lt_wo_consumption (
    consumption_id   BIGINT NOT NULL COMMENT '消耗记录ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    wo_id            BIGINT NOT NULL COMMENT '工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    lot_id           BIGINT NOT NULL COMMENT '批次ID',
    lot_code        VARCHAR(64) COMMENT '批次编码(冗余)',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    consumed_qty     DECIMAL(14,4) NOT NULL COMMENT '消耗数量',
    consumed_time    DATETIME NOT NULL COMMENT '消耗时间',
    operator_id      BIGINT COMMENT '操作员ID',
    operator_name   VARCHAR(64) COMMENT '操作员姓名(冗余)',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept     BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (consumption_id),
    INDEX idx_wo_id (wo_id),
    INDEX idx_lot_id (lot_id),
    INDEX idx_material_id (material_id),
    INDEX idx_consumed_time (consumed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单批次消耗记录';

-- lt_sn_binding: MES SN批次绑定关系
CREATE TABLE IF NOT EXISTS lt_sn_binding (
    binding_id       BIGINT NOT NULL COMMENT '绑定ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_id            BIGINT NOT NULL COMMENT 'SN ID',
    sn_code         VARCHAR(64) COMMENT 'SN编码(冗余)',
    lot_id           BIGINT NOT NULL COMMENT '批次ID',
    lot_code        VARCHAR(64) COMMENT '批次编码(冗余)',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    bound_time       DATETIME NOT NULL COMMENT '绑定时间',
    bound_by         BIGINT COMMENT '绑定人ID',
    create_dept     BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (binding_id),
    INDEX idx_sn_id (sn_id),
    INDEX idx_lot_id (lot_id),
    INDEX idx_material_id (material_id),
    UNIQUE INDEX uk_sn_material (sn_id, material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN批次绑定关系';

-- =============================================
-- 增量: 为已部署环境补 lt_lot.iqc_id 列(新环境由上方 CREATE 已含,此 ALTER 幂等失败可忽略)
-- =============================================
-- ALTER TABLE `lt_lot`
--   ADD COLUMN `iqc_id` BIGINT NULL COMMENT 'IQC检验单ID(关联qc_iqc.iqc_id,可空)' AFTER `qc_status`,
--   ADD INDEX `idx_iqc_id` (`iqc_id`);

-- 验证(执行后人工核对):
-- SHOW TABLES FROM `ry-mes` LIKE 'lt_%';
-- 预期: lt_lot, lt_wo_consumption, lt_sn_binding
-- SHOW COLUMNS FROM lt_lot LIKE 'iqc_id';
-- 预期: 1 行, Type=BIGINT, Null=YES
