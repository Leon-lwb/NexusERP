-- M2 批次2: 生产退料三类(良品/不良品/报废) — PC 建单, PDA 扫码核销
-- 落账策略: 良品/不良品经杂项入库通道回库存(库位由建单指定, 不良品建议不良库区), 报废仅留痕不回库存
-- 执行说明: 建表部分在 erp 库执行; 末尾 sys_auto_code_rule 种子属系统库(ry-vue), 需单独在系统库执行。

CREATE TABLE IF NOT EXISTS wm_material_return (
    return_id      BIGINT        NOT NULL COMMENT '退料单ID',
    return_no      VARCHAR(64)   NOT NULL COMMENT '退料单号',
    warehouse_id   BIGINT        NULL COMMENT '仓库ID',
    warehouse_code VARCHAR(64)   NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(128)  NULL COMMENT '仓库名称',
    return_type    VARCHAR(16)   NOT NULL COMMENT '退料类型 GOOD良品/DEFECT不良品/SCRAP报废',
    source_doc     VARCHAR(64)   NULL COMMENT '来源单号(工单/领料单等)',
    status         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT待核销/COMPLETED已完成',
    return_date    DATETIME      NULL COMMENT '退料日期',
    remark         VARCHAR(255)  NULL COMMENT '备注',
    tenant_id      VARCHAR(20)   NULL COMMENT '租户编号',
    create_dept    BIGINT        NULL COMMENT '创建部门',
    create_by      BIGINT        NULL COMMENT '创建者',
    create_time    DATETIME      NULL COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新者',
    update_time    DATETIME      NULL COMMENT '更新时间',
    PRIMARY KEY (return_id)
) COMMENT = '生产退料单';

CREATE TABLE IF NOT EXISTS wm_material_return_line (
    line_id         BIGINT        NOT NULL COMMENT '行ID',
    return_id       BIGINT        NOT NULL COMMENT '退料单ID',
    material_id     BIGINT        NULL COMMENT '物料ID',
    material_code   VARCHAR(64)   NULL COMMENT '物料编码',
    material_name   VARCHAR(255)  NULL COMMENT '物料名称',
    material_spec   VARCHAR(255)  NULL COMMENT '规格型号',
    base_uom_id     VARCHAR(64)   NULL COMMENT '基本单位',
    base_uom_name   VARCHAR(64)   NULL COMMENT '基本单位名称',
    batch_id        BIGINT        NULL COMMENT '批次ID',
    batch_code      VARCHAR(64)   NULL COMMENT '批次号',
    location_id     BIGINT        NULL COMMENT '目标库位ID',
    location_code   VARCHAR(64)   NULL COMMENT '目标库位编码',
    location_name   VARCHAR(128)  NULL COMMENT '目标库位名称',
    quantity        DECIMAL(18,4) NULL COMMENT '计划退料数量',
    actual_qty      DECIMAL(18,4) NULL COMMENT '实退数量(PDA核销累计)',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       VARCHAR(20)   NULL COMMENT '租户编号',
    create_dept     BIGINT        NULL COMMENT '创建部门',
    create_by       BIGINT        NULL COMMENT '创建者',
    create_time     DATETIME      NULL COMMENT '创建时间',
    update_by       BIGINT        NULL COMMENT '更新者',
    update_time     DATETIME      NULL COMMENT '更新时间',
    PRIMARY KEY (line_id),
    KEY idx_return_id (return_id)
) COMMENT = '生产退料单行';

-- 退料单号自动编码规则 (genSerialCode("WM_RETURN_CODE"))
INSERT INTO `sys_auto_code_rule` VALUES (295, '000000', 'WM_RETURN_CODE', '退料单编号', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, NULL, 1, '2026-08-30 00:00:00', NULL, NULL, '0');
