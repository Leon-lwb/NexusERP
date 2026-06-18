USE `ry-mes`;

-- =============================================
-- NexusERP - MES模块 Week 23 安灯系统DDL
-- 目标库: ry-mes
-- 表: pro_andon (MES安灯呼叫)
-- 状态机: CALLED -> RESPONDED -> RESOLVED / CANCELLED
-- 呼叫类型: QUALITY(质量异常) / MATERIAL(物料短缺) / EQUIPMENT(设备故障) / OTHER(其他)
-- 来源: MANUAL(手动呼叫) / IoT(设备告警联动)
-- 创建时间: 2026-06-21
-- =============================================

CREATE TABLE pro_andon (
    andon_id        BIGINT NOT NULL COMMENT '安灯ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    andon_no        VARCHAR(64) NOT NULL COMMENT '安灯编号',
    mes_wo_id       BIGINT COMMENT '关联MES工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id         BIGINT COMMENT '关联流转卡ID',
    card_no         VARCHAR(64) COMMENT '流转卡编号(冗余)',
    step_record_id  BIGINT COMMENT '关联流转卡工序记录ID',
    call_type       VARCHAR(32) NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
    call_reason     VARCHAR(500) COMMENT '呼叫原因',
    andon_status    VARCHAR(32) DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
    caller_id       BIGINT COMMENT '呼叫人ID',
    caller_name     VARCHAR(64) COMMENT '呼叫人姓名',
    responder_id    BIGINT COMMENT '响应人ID',
    responder_name  VARCHAR(64) COMMENT '响应人姓名',
    call_time       DATETIME COMMENT '呼叫时间',
    respond_time    DATETIME COMMENT '响应时间',
    resolve_time    DATETIME COMMENT '解决时间',
    duration_minutes INT COMMENT '持续时长(分钟, 解决时计算)',
    source          VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
    remark          VARCHAR(500),
    create_dept     BIGINT,
    create_by       BIGINT,
    create_time     DATETIME,
    update_by       BIGINT,
    update_time     DATETIME,
    del_flag        CHAR(1) DEFAULT '0',
    PRIMARY KEY (andon_id),
    UNIQUE INDEX uk_andon_no (andon_no),
    INDEX idx_andon_status (andon_status),
    INDEX idx_call_type (call_type),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES安灯呼叫';

-- 验证(执行后人工核对):
-- DESCRIBE pro_andon;
-- 预期: 24列, 主键 andon_id, 唯一索引 uk_andon_no(andon_no)
