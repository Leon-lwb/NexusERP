USE `ry-mes`;

-- =============================================
-- NexusERP - MES模块 Week 24 SN追溯体系DDL
-- 目标库: ry-mes
-- 表: pro_sn (MES SN序列号追溯)
-- 状态机: IN_PRODUCTION -> COMPLETED -> SHIPPED
--                          └-> SCRAPPED (终态)
--          COMPLETED -> SCRAPPED
-- 组装追溯: parent_sn_id 关联父SN(组件SN -> 成品SN)
-- 来源: MANUAL(手动单建) / BATCH(批量生成)
-- 创建时间: 2026-06-22
-- =============================================

CREATE TABLE pro_sn (
    sn_id           BIGINT NOT NULL COMMENT 'SN ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_code         VARCHAR(128) NOT NULL COMMENT 'SN序列号编码',
    mes_wo_id       BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id         BIGINT COMMENT '关联流转卡ID',
    card_no         VARCHAR(64) COMMENT '流转卡编号(冗余)',
    material_id     BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    current_step_seq INT COMMENT '当前工序序号',
    sn_status       VARCHAR(32) DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
    parent_sn_id    BIGINT COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
    parent_sn_code  VARCHAR(128) COMMENT '父SN编码(冗余)',
    source          VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
    production_date DATE COMMENT '生产日期',
    remark          VARCHAR(500),
    create_dept     BIGINT,
    create_by       BIGINT,
    create_time     DATETIME,
    update_by       BIGINT,
    update_time     DATETIME,
    del_flag        CHAR(1) DEFAULT '0',
    PRIMARY KEY (sn_id),
    UNIQUE INDEX uk_sn_code (sn_code),
    INDEX idx_sn_status (sn_status),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_material_id (material_id),
    INDEX idx_parent_sn_id (parent_sn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN序列号追溯';

-- 验证(执行后人工核对):
-- DESCRIBE pro_sn;
-- 预期: 23列, 主键 sn_id, 唯一索引 uk_sn_code(sn_code)
