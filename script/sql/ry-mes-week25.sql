-- =============================================
-- NexusERP - MES模块 Week 25 质量管理DDL
-- 目标库: ry-mes
-- 说明: IQC来料检验 + IPQC过程检验 + OQC出货检验
-- 状态机: PENDING -> INSPECTING -> COMPLETED
-- 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
-- 不良品信息: defect_info JSON字段(类型/数量/描述)
-- 约定对齐: tenant_id VARCHAR(20) DEFAULT '000000', del_flag CHAR(1), 状态列 VARCHAR
-- 创建时间: 2026-06-23
-- =============================================
USE `ry-mes`;

-- qc_iqc: MES来料检验
CREATE TABLE qc_iqc (
    iqc_id          BIGINT NOT NULL COMMENT 'IQC ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    iqc_no          VARCHAR(64) NOT NULL COMMENT 'IQC编号',
    receive_id      BIGINT COMMENT 'ERP收货单ID',
    material_id     BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    vendor_id       BIGINT COMMENT '供应商ID',
    vendor_name     VARCHAR(128) COMMENT '供应商名称(冗余)',
    quantity        DECIMAL(14,4) NOT NULL COMMENT '检验数量',
    sample_qty      DECIMAL(14,4) COMMENT '抽样数量',
    inspect_result  VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info     JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id    BIGINT COMMENT '检验员ID',
    inspector_name  VARCHAR(64) COMMENT '检验员姓名',
    inspect_time    DATETIME COMMENT '检验时间',
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark          VARCHAR(500) COMMENT '备注',
    create_dept     BIGINT COMMENT '创建部门',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME COMMENT '更新时间',
    del_flag        CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (iqc_id),
    UNIQUE INDEX uk_iqc_no (iqc_no),
    INDEX idx_status (status),
    INDEX idx_receive_id (receive_id),
    INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES来料检验';

-- qc_ipqc: MES过程检验
CREATE TABLE qc_ipqc (
    ipqc_id         BIGINT NOT NULL COMMENT 'IPQC ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    ipqc_no         VARCHAR(64) NOT NULL COMMENT 'IPQC编号',
    mes_wo_id       BIGINT NOT NULL COMMENT 'MES工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id         BIGINT COMMENT '流转卡ID',
    step_record_id  BIGINT COMMENT '流转卡工序记录ID',
    process_id      BIGINT COMMENT '工序ID',
    process_code    VARCHAR(64) COMMENT '工序编码',
    process_name    VARCHAR(128) COMMENT '工序名称',
    material_id     BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    sample_qty      DECIMAL(14,4) COMMENT '抽样数量',
    defect_qty      DECIMAL(14,4) DEFAULT 0 COMMENT '不良数量',
    defect_rate     DECIMAL(5,2) COMMENT '不良率(%)',
    inspect_result  VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info     JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id    BIGINT COMMENT '检验员ID',
    inspector_name  VARCHAR(64) COMMENT '检验员姓名',
    inspect_time    DATETIME COMMENT '检验时间',
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark          VARCHAR(500) COMMENT '备注',
    create_dept     BIGINT COMMENT '创建部门',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME COMMENT '更新时间',
    del_flag        CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (ipqc_id),
    UNIQUE INDEX uk_ipqc_no (ipqc_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_status (status),
    INDEX idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES过程检验';

-- qc_oqc: MES出货检验
CREATE TABLE qc_oqc (
    oqc_id          BIGINT NOT NULL COMMENT 'OQC ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    oqc_no          VARCHAR(64) NOT NULL COMMENT 'OQC编号',
    so_id           BIGINT COMMENT 'ERP销售订单ID',
    material_id     BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    quantity        DECIMAL(14,4) NOT NULL COMMENT '检验数量',
    sample_qty      DECIMAL(14,4) COMMENT '抽样数量',
    inspect_result  VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info     JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id    BIGINT COMMENT '检验员ID',
    inspector_name  VARCHAR(64) COMMENT '检验员姓名',
    inspect_time    DATETIME COMMENT '检验时间',
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark          VARCHAR(500) COMMENT '备注',
    create_dept     BIGINT COMMENT '创建部门',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME COMMENT '更新时间',
    del_flag        CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (oqc_id),
    UNIQUE INDEX uk_oqc_no (oqc_no),
    INDEX idx_status (status),
    INDEX idx_so_id (so_id),
    INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES出货检验';

-- 验证(执行后人工核对):
-- SHOW TABLES FROM `ry-mes` LIKE 'qc_%';
-- 预期: qc_iqc, qc_ipqc, qc_oqc
