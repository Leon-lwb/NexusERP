USE `ry-mes`;

-- 1. pro_dispatch: MES派工单
-- 状态机: PENDING -> IN_PROGRESS -> COMPLETED / CANCELLED
CREATE TABLE pro_dispatch (
    dispatch_id      BIGINT NOT NULL COMMENT '派工单ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    dispatch_no      VARCHAR(64) NOT NULL COMMENT '派工单编号',
    mes_wo_id        BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no            VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id          BIGINT COMMENT '关联流转卡ID',
    card_no          VARCHAR(64) COMMENT '流转卡编号(冗余)',
    step_record_id   BIGINT COMMENT '关联流转卡工序记录ID',
    sequence_num     INT COMMENT '工序序号(冗余)',
    process_id       BIGINT NOT NULL COMMENT '工序ID',
    process_code     VARCHAR(64),
    process_name     VARCHAR(200),
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    operator_id      BIGINT COMMENT '操作员ID(SchWorker.workerId)',
    operator_name    VARCHAR(64) COMMENT '操作员姓名',
    planned_qty      DECIMAL(18,4) DEFAULT 0 COMMENT '派工数量',
    completed_qty    DECIMAL(18,4) DEFAULT 0 COMMENT '已完成数量',
    good_qty         DECIMAL(18,4) DEFAULT 0 COMMENT '良品数量',
    defect_qty       DECIMAL(18,4) DEFAULT 0 COMMENT '不良品数量',
    scrap_qty        DECIMAL(18,4) DEFAULT 0 COMMENT '报废数量',
    dispatch_status  VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    remark           VARCHAR(500),
    create_dept      BIGINT,
    create_by        BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    del_flag         CHAR(1) DEFAULT '0',
    PRIMARY KEY (dispatch_id),
    UNIQUE INDEX uk_dispatch_no (dispatch_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_step_record_id (step_record_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_dispatch_status (dispatch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES派工单';

-- 2. pro_flow_card barcode 唯一索引增强
-- Week 20 DDL 已有 barcode 列但无唯一约束，本周补充
-- 注意: 现有流转卡 barcode 为 NULL, MySQL NULL 不参与唯一性检查, 旧数据不受影响
ALTER TABLE pro_flow_card ADD UNIQUE INDEX uk_barcode (barcode);
