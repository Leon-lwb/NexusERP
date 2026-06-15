-- ============================================================
-- 采购合同管理模块 - 建表脚本 + 菜单数据
-- 菜单ID范围: 2840-2849 (已确认无冲突)
-- 父菜单: 2500 (采购管理)
-- 权限前缀: pu:contract:*
-- ============================================================

-- ----------------------------
-- 1. 采购合同头表
-- ----------------------------
DROP TABLE IF EXISTS pu_contract;
CREATE TABLE pu_contract (
    contract_id     bigint(20)    NOT NULL              COMMENT '合同ID',
    contract_no     varchar(50)   NOT NULL              COMMENT '合同编号',
    contract_name   varchar(200)  DEFAULT NULL          COMMENT '合同名称',
    vendor_id       bigint(20)    DEFAULT NULL          COMMENT '供应商ID',
    vendor_code     varchar(50)   DEFAULT NULL          COMMENT '供应商编码',
    vendor_name     varchar(200)  DEFAULT NULL          COMMENT '供应商名称',
    contract_type   varchar(30)   DEFAULT NULL          COMMENT '合同类型 FRAMEWORK-框架合同 SPOT-现货合同 SERVICE-服务合同',
    sign_date       datetime      DEFAULT NULL          COMMENT '签订日期',
    effective_date  datetime      DEFAULT NULL          COMMENT '生效日期',
    expiry_date     datetime      DEFAULT NULL          COMMENT '失效日期',
    total_amount    decimal(18,4) DEFAULT NULL          COMMENT '合同总金额',
    currency        varchar(10)   DEFAULT 'CNY'         COMMENT '币种',
    payment_terms   varchar(200)  DEFAULT NULL          COMMENT '付款条款',
    status          varchar(30)   DEFAULT 'DRAFT'       COMMENT '合同状态 DRAFT-草稿 APPROVING-审批中 APPROVED-已批准 EXECUTING-执行中 COMPLETED-已完成 TERMINATED-已终止 REJECTED-已驳回',
    select_flow_id  bigint(20)    DEFAULT NULL          COMMENT '审批流程ID',
    remark          varchar(500)  DEFAULT NULL          COMMENT '备注',
    attr1           varchar(200)  DEFAULT NULL          COMMENT '扩展字段1',
    attr2           varchar(200)  DEFAULT NULL          COMMENT '扩展字段2',
    attr3           bigint(20)    DEFAULT NULL          COMMENT '扩展字段3',
    attr4           bigint(20)    DEFAULT NULL          COMMENT '扩展字段4',
    tenant_id       varchar(20)   DEFAULT '000000'      COMMENT '租户编号',
    create_dept     bigint(20)    DEFAULT NULL          COMMENT '创建部门',
    create_by       bigint(20)    DEFAULT NULL          COMMENT '创建者',
    create_time     datetime      DEFAULT NULL          COMMENT '创建时间',
    update_by       bigint(20)    DEFAULT NULL          COMMENT '更新者',
    update_time     datetime      DEFAULT NULL          COMMENT '更新时间',
    del_flag        char(1)       DEFAULT '0'           COMMENT '删除标志(0代表存在 2代表删除)',
    PRIMARY KEY (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购合同头表';

-- ----------------------------
-- 2. 采购合同行表
-- ----------------------------
DROP TABLE IF EXISTS pu_contract_line;
CREATE TABLE pu_contract_line (
    line_id         bigint(20)    NOT NULL              COMMENT '行ID',
    contract_id     bigint(20)    NOT NULL              COMMENT '合同ID',
    material_id     bigint(20)    DEFAULT NULL          COMMENT '物料ID',
    material_code   varchar(50)   DEFAULT NULL          COMMENT '物料编码',
    material_name   varchar(200)  DEFAULT NULL          COMMENT '物料名称',
    material_spec   varchar(200)  DEFAULT NULL          COMMENT '物料规格',
    unit            varchar(30)   DEFAULT NULL          COMMENT '计量单位',
    unit_price      decimal(18,4) DEFAULT NULL          COMMENT '单价',
    quantity        decimal(18,4) DEFAULT NULL          COMMENT '数量',
    line_amount     decimal(18,4) DEFAULT NULL          COMMENT '行金额',
    delivery_date   datetime      DEFAULT NULL          COMMENT '交货日期',
    sort_order      int           DEFAULT 0             COMMENT '排序号',
    remark          varchar(500)  DEFAULT NULL          COMMENT '备注',
    attr1           varchar(200)  DEFAULT NULL          COMMENT '扩展字段1',
    attr2           varchar(200)  DEFAULT NULL          COMMENT '扩展字段2',
    attr3           bigint(20)    DEFAULT NULL          COMMENT '扩展字段3',
    attr4           bigint(20)    DEFAULT NULL          COMMENT '扩展字段4',
    tenant_id       varchar(20)   DEFAULT '000000'      COMMENT '租户编号',
    create_dept     bigint(20)    DEFAULT NULL          COMMENT '创建部门',
    create_by       bigint(20)    DEFAULT NULL          COMMENT '创建者',
    create_time     datetime      DEFAULT NULL          COMMENT '创建时间',
    update_by       bigint(20)    DEFAULT NULL          COMMENT '更新者',
    update_time     datetime      DEFAULT NULL          COMMENT '更新时间',
    del_flag        char(1)       DEFAULT '0'           COMMENT '删除标志(0代表存在 2代表删除)',
    PRIMARY KEY (line_id),
    KEY idx_contract_id (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购合同行表';

-- ----------------------------
-- 3. 菜单数据 (ID范围: 2840-2849)
-- ----------------------------
INSERT INTO sys_menu VALUES (2840, '采购合同', 2500, 10, 'contract', 'erp/pu/contract/index', '', 1, 0, 'C', '0', '0', 'pu:contract:list', 'documentation', 103, 1, sysdate(), NULL, NULL, '采购合同菜单');
INSERT INTO sys_menu VALUES (2841, '采购合同查询', 2840, 1, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:query', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES (2842, '采购合同新增', 2840, 2, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:add', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES (2843, '采购合同修改', 2840, 3, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:edit', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES (2844, '采购合同删除', 2840, 4, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:remove', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES (2845, '采购合同导出', 2840, 5, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:export', '#', 103, 1, sysdate(), NULL, NULL, '');

-- ----------------------------
-- 4. SoInvoice增加select_flow_id字段
-- ----------------------------
ALTER TABLE so_invoice ADD COLUMN select_flow_id bigint(20) DEFAULT NULL COMMENT '审批流程ID' AFTER status;
