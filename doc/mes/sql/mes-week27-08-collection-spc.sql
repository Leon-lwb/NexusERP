-- =============================================
-- NexusERP - MES模块 Week 27 数据采集+SPC DDL
-- 目标库: ry-mes
-- 说明: 数据采集点 + 采集数据记录 + SPC控制图 + SPC数据点
-- 采集点类型: MANUAL(手动)/AUTO(自动,预留)
-- 数据类型: NUMERIC/TEXT/ENUM
-- 控制图类型: X_BAR_R/X_BAR_S/P/C/U
-- OOC规则: RULE1(超限)/RULE2(连续9点同侧)/RULE3(连续6点递增递减)/RULE4(连续14点交替)
-- SPC消费dc_data_record(手动录入), 不直接消费IoT原始数据
-- 约定对齐: tenant_id VARCHAR(20) DEFAULT '000000', del_flag CHAR(1), 状态列 VARCHAR
-- 创建时间: 2026-06-25
-- =============================================
USE `ry-mes`;

-- dc_collection_point: MES数据采集点
CREATE TABLE dc_collection_point (
    point_id         BIGINT NOT NULL COMMENT '采集点ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    point_code       VARCHAR(64) NOT NULL COMMENT '采集点编码',
    point_name       VARCHAR(128) NOT NULL COMMENT '采集点名称',
    point_type       VARCHAR(16) NOT NULL COMMENT 'MANUAL/AUTO',
    equipment_id     BIGINT COMMENT '关联设备ID',
    process_id       BIGINT COMMENT '关联工序ID',
    data_type        VARCHAR(16) COMMENT 'NUMERIC/TEXT/ENUM',
    unit             VARCHAR(16) COMMENT '单位',
    frequency        INT COMMENT '采集频率(秒)',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (point_id),
    UNIQUE INDEX uk_point_code (point_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES数据采集点';

-- dc_data_record: MES采集数据记录(先存MySQL,超500万行后考虑TDengine)
CREATE TABLE dc_data_record (
    record_id        BIGINT NOT NULL COMMENT '记录ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    point_id         BIGINT NOT NULL COMMENT '采集点ID',
    point_code       VARCHAR(64) COMMENT '采集点编码(冗余)',
    mes_wo_id        BIGINT COMMENT '关联MES工单ID',
    card_id          BIGINT COMMENT '关联流转卡ID',
    step_record_id   BIGINT COMMENT '关联工序记录ID',
    numeric_value    DECIMAL(14,4) COMMENT '数值',
    text_value       VARCHAR(500) COMMENT '文本值',
    collect_time     DATETIME NOT NULL COMMENT '采集时间',
    collect_by       BIGINT COMMENT '采集人(手动时)',
    source           VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (record_id),
    INDEX idx_point_id (point_id),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_collect_time (collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES采集数据记录';

-- spc_control_chart: MES SPC控制图
CREATE TABLE spc_control_chart (
    chart_id         BIGINT NOT NULL COMMENT '控制图ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_name       VARCHAR(128) NOT NULL COMMENT '控制图名称',
    chart_type       VARCHAR(16) NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
    point_id         BIGINT COMMENT '关联采集点ID',
    material_id      BIGINT COMMENT '关联物料ID',
    process_id       BIGINT COMMENT '关联工序ID',
    ucl              DECIMAL(14,4) COMMENT '上控制限(analyze计算回写)',
    cl               DECIMAL(14,4) COMMENT '中心线',
    lcl              DECIMAL(14,4) COMMENT '下控制限',
    sample_size      INT DEFAULT 5 COMMENT '样本大小',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (chart_id),
    INDEX idx_point_id (point_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC控制图';

-- spc_data_point: MES SPC数据点
CREATE TABLE spc_data_point (
    data_id          BIGINT NOT NULL COMMENT '数据点ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_id         BIGINT NOT NULL COMMENT '控制图ID',
    sample_group     INT NOT NULL COMMENT '样本组号',
    sample_index     INT COMMENT '组内序号',
    value            DECIMAL(14,4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
    is_ooc           CHAR(1) DEFAULT '0' COMMENT '是否超控制限(0/1)',
    ooc_rule         VARCHAR(32) COMMENT '违规规则(RULE1/2/3/4, 多规则逗号拼接)',
    collect_time     DATETIME COMMENT '采集时间',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (data_id),
    INDEX idx_chart_id (chart_id),
    INDEX idx_group (chart_id, sample_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC数据点';

-- 验证(执行后人工核对):
-- SHOW TABLES FROM `ry-mes` LIKE 'dc_%';
-- SHOW TABLES FROM `ry-mes` LIKE 'spc_%';
-- 预期: dc_collection_point, dc_data_record, spc_control_chart, spc_data_point
