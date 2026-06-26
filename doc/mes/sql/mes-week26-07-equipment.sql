-- =============================================
-- NexusERP - MES模块 Week 26 设备管理DDL
-- 目标库: ry-mes
-- 说明: 设备台账 + 保养计划 + 维修记录
-- 设备状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED
-- 保养类型: DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY (下次保养日期自动计算)
-- 维修状态机: REPORTED -> REPAIRING -> COMPLETED
-- 点检合入台账JSON字段(原4表->3表)
-- 约定对齐: tenant_id VARCHAR(20) DEFAULT '000000', del_flag CHAR(1), 状态列 VARCHAR
-- 设备状态联动: 报修置FAULT; 完成且无其他活跃报修复NORMAL
-- 创建时间: 2026-06-24
-- =============================================
USE `ry-mes`;

-- eq_equipment: MES设备台账
CREATE TABLE eq_equipment (
    equipment_id     BIGINT NOT NULL COMMENT '设备ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    equipment_code   VARCHAR(64) NOT NULL COMMENT '设备编码',
    equipment_name   VARCHAR(128) NOT NULL COMMENT '设备名称',
    equipment_type   VARCHAR(32) COMMENT '设备类型',
    model            VARCHAR(128) COMMENT '型号',
    manufacturer     VARCHAR(128) COMMENT '制造商',
    serial_no        VARCHAR(64) COMMENT '出厂编号',
    workcenter_id    BIGINT COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
    workcenter_code  VARCHAR(64) COMMENT '工作中心编码(冗余)',
    location         VARCHAR(256) COMMENT '安装位置',
    purchase_date    DATE COMMENT '购置日期',
    warranty_date    DATE COMMENT '保修到期',
    status           VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
    inspection_items JSON COMMENT '点检项目及标准(JSON)',
    last_inspection  JSON COMMENT '最近一次点检结果(JSON)',
    iot_device_id    BIGINT COMMENT '关联IoT设备ID(预留,无外键)',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (equipment_id),
    UNIQUE INDEX uk_equipment_code (equipment_code),
    INDEX idx_status (status),
    INDEX idx_workcenter_id (workcenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备台账';

-- eq_maintenance_plan: MES设备保养计划
CREATE TABLE eq_maintenance_plan (
    plan_id          BIGINT NOT NULL COMMENT '计划ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    plan_name        VARCHAR(128) NOT NULL COMMENT '计划名称',
    equipment_id     BIGINT NOT NULL COMMENT '设备ID',
    maintenance_type VARCHAR(16) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
    cycle_days       INT COMMENT '周期(天)',
    next_date        DATE COMMENT '下次保养日期(自动计算)',
    content          TEXT COMMENT '保养内容',
    responsible_id   BIGINT COMMENT '负责人ID',
    responsible_name VARCHAR(64) COMMENT '负责人姓名',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (plan_id),
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_next_date (next_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备保养计划';

-- eq_repair: MES设备维修记录
CREATE TABLE eq_repair (
    repair_id        BIGINT NOT NULL COMMENT '维修ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    repair_no        VARCHAR(64) NOT NULL COMMENT '维修单号',
    equipment_id     BIGINT NOT NULL COMMENT '设备ID',
    fault_type       VARCHAR(32) COMMENT '故障类型',
    fault_desc       VARCHAR(500) COMMENT '故障描述',
    reporter_id      BIGINT COMMENT '报修人ID',
    reporter_name    VARCHAR(64) COMMENT '报修人姓名',
    repairer_id      BIGINT COMMENT '维修人ID',
    repairer_name    VARCHAR(64) COMMENT '维修人姓名',
    repair_start     DATETIME COMMENT '维修开始时间',
    repair_end       DATETIME COMMENT '维修结束时间',
    downtime_hours   DECIMAL(10,2) COMMENT '停机时长(小时)',
    repair_cost      DECIMAL(14,2) COMMENT '维修成本',
    status           VARCHAR(16) DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept      BIGINT COMMENT '创建部门',
    create_by        BIGINT COMMENT '创建者',
    create_time      DATETIME COMMENT '创建时间',
    update_by        BIGINT COMMENT '更新者',
    update_time      DATETIME COMMENT '更新时间',
    del_flag         CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (repair_id),
    UNIQUE INDEX uk_repair_no (repair_no),
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备维修记录';

-- 验证(执行后人工核对):
-- SHOW TABLES FROM `ry-mes` LIKE 'eq_%';
-- 预期: eq_equipment, eq_maintenance_plan, eq_repair
