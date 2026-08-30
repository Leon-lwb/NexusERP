-- M3 批次2: 上料交接单(仓库→线边库位) — PC 建单, PDA 行级核销 + 签收过账
-- 流程: PC/PDA 建 DRAFT 单(来源工单 + 源库位 → 线边库位) → 材料员 PDA 逐行扫码核销(累计实交)
--       → 产线操作工 PDA 扫单签收 → 服务端同事务过账(逐行 postMiscIssue 出源库位 + postMiscRecpt 入线边库位)
-- 目标库: erp 库 (wm_* 表所在库)

CREATE TABLE IF NOT EXISTS wm_material_handover (
    handover_id    BIGINT        NOT NULL COMMENT '交接单ID',
    handover_no    VARCHAR(64)   NOT NULL COMMENT '交接单号',
    wo_id          BIGINT        NULL COMMENT '来源工单ID(prd_work_order)',
    wo_no          VARCHAR(64)   NULL COMMENT '来源工单号',
    warehouse_id   BIGINT        NULL COMMENT '源仓库ID',
    warehouse_code VARCHAR(64)   NULL COMMENT '源仓库编码',
    warehouse_name VARCHAR(128)  NULL COMMENT '源仓名称',
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT 待签收 / SIGNED 已签收',
    handover_time  DATETIME      NULL COMMENT '交接时间(建单时间)',
    sign_by        BIGINT        NULL COMMENT '签收人ID',
    sign_name      VARCHAR(64)   NULL COMMENT '签收人',
    sign_time      DATETIME      NULL COMMENT '签收时间',
    remark         VARCHAR(500)  NULL COMMENT '备注',
    tenant_id      VARCHAR(20)   DEFAULT '000000' COMMENT '租户编号',
    create_dept    BIGINT        NULL COMMENT '创建部门',
    create_by      BIGINT        NULL COMMENT '创建者',
    create_time    DATETIME      NULL COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新者',
    update_time    DATETIME      NULL COMMENT '更新时间',
    del_flag       CHAR(1)       DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
    PRIMARY KEY (handover_id),
    UNIQUE KEY uk_handover_no (handover_no),
    KEY idx_handover_status (status),
    KEY idx_handover_wo_no (wo_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上料交接单';

CREATE TABLE IF NOT EXISTS wm_material_handover_line (
    line_id          BIGINT        NOT NULL COMMENT '行ID',
    handover_id      BIGINT        NOT NULL COMMENT '交接单ID',
    material_id      BIGINT        NOT NULL COMMENT '物料ID',
    material_code    VARCHAR(64)   NULL COMMENT '物料编码',
    material_name    VARCHAR(255)  NULL COMMENT '物料名称',
    material_spec    VARCHAR(255)  NULL COMMENT '规格型号',
    base_uom_id      VARCHAR(64)   NULL COMMENT '基本单位',
    base_uom_name    VARCHAR(64)   NULL COMMENT '基本单位名称',
    batch_id         BIGINT        NULL COMMENT '批次ID',
    batch_code       VARCHAR(64)   NULL COMMENT '批次号',
    from_location_id   BIGINT      NULL COMMENT '源库位ID(源仓库内)',
    from_location_code VARCHAR(64) NULL COMMENT '源库位编码',
    from_location_name VARCHAR(128) NULL COMMENT '源库位名称',
    to_location_id     BIGINT      NULL COMMENT '线边库位ID',
    to_location_code   VARCHAR(64) NULL COMMENT '线边库位编码',
    to_location_name   VARCHAR(128) NULL COMMENT '线边库位名称',
    quantity         DECIMAL(18,4) NULL COMMENT '计划交接数量',
    actual_qty       DECIMAL(18,4) NULL COMMENT '实交数量(PDA核销累计)',
    remark           VARCHAR(500)  NULL COMMENT '备注',
    tenant_id        VARCHAR(20)   DEFAULT '000000' COMMENT '租户编号',
    create_dept      BIGINT        NULL COMMENT '创建部门',
    create_by        BIGINT        NULL COMMENT '创建者',
    create_time      DATETIME      NULL COMMENT '创建时间',
    update_by        BIGINT        NULL COMMENT '更新者',
    update_time      DATETIME      NULL COMMENT '更新时间',
    del_flag         CHAR(1)       DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
    PRIMARY KEY (line_id),
    KEY idx_handover_line_handover (handover_id),
    KEY idx_handover_line_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上料交接单行';

-- 注意: 单号自动编码规则 WM_HANDOVER_CODE (rule_id=296) 属系统库表 sys_auto_code_rule,
--       种子见 2026-08-30-pda-material-handover-menu.sql (与本文件分库执行)。
