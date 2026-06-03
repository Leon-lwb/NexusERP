# NexusERP - MES模块综合文档

> **文档状态**: 📋 规划设计文档 — MES模块尚未开始实施，本文档为开发前的完整设计方案。
> 当前项目中 `ruoyi-mes` Maven模块尚未创建，`@DS("mes")` 数据源尚未配置，28张数据表DDL尚未执行。
> MES开发建议在ERP核心功能收尾（Week 11-12）后启动。

## 一、MES模块总体设计

### 1.1 MES定位

MES(Manufacturing Execution System)制造执行系统，位于ERP计划层与设备控制层之间，负责车间级生产执行管理。

### 1.2 MES模块结构（7个子模块）

```
ruoyi-mes/
└── com/nexus/mes/
    ├── md/     # 主数据(只读引用ERP)
    ├── pro/    # 生产执行(工单/工序/流转卡)
    ├── wm/     # 仓储扩展(线边仓/WIP)
    ├── qc/     # 质量管理(IQC/IPQC/OQC)
    ├── eq/     # 设备管理(台账/保养/维修)
    ├── dc/     # 数据采集(设备数据/传感器)
    └── spc/    # 统计过程控制
```

### 1.3 数据源配置

- 数据库: ry-mes (独立库) — 📋 待创建
- 注解: `@DS("mes")` — 📋 待配置
- 与ERP共享: wm_material_stock (通过`@DS("erp")`访问) — ✅ 已存在

## 二、MES数据表设计（28张表）

### 2.1 主数据模块(md) - 4张表

```sql
-- MES物料主数据(只读同步自ERP)
CREATE TABLE mes_md_material (
    material_id      BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    material_code    VARCHAR(64) NOT NULL,
    material_name    VARCHAR(128) NOT NULL,
    material_spec    VARCHAR(256),
    material_type    VARCHAR(32),
    base_uom_id      BIGINT,
    base_uom_name    VARCHAR(32),
    reference_price  DECIMAL(14,4),
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    sync_time        DATETIME COMMENT '最后同步时间',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES物料主数据';

-- MES BOM(只读同步自ERP)
CREATE TABLE mes_md_bom (
    bom_id           BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    bom_code         VARCHAR(64) NOT NULL,
    bom_name         VARCHAR(128) NOT NULL,
    material_id      BIGINT NOT NULL COMMENT '成品物料ID',
    bom_type         VARCHAR(16) DEFAULT 'STANDARD',
    bom_version      VARCHAR(32),
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    sync_time        DATETIME,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (bom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES BOM';

CREATE TABLE mes_md_bom_line (
    line_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    bom_id           BIGINT NOT NULL,
    material_id      BIGINT NOT NULL COMMENT '组件物料ID',
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    quantity         DECIMAL(14,4) NOT NULL,
    scrap_rate       DECIMAL(5,2) DEFAULT 0,
    sequence_num     INT,
    create_time      DATETIME,
    PRIMARY KEY (line_id),
    INDEX idx_bom (bom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES BOM行';

-- MES工艺路线(只读同步自ERP)
CREATE TABLE mes_md_routing (
    routing_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    routing_code     VARCHAR(64) NOT NULL,
    routing_name     VARCHAR(128) NOT NULL,
    routing_version  VARCHAR(32),
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    sync_time        DATETIME,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (routing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工艺路线';

CREATE TABLE mes_md_routing_step (
    step_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    routing_id       BIGINT NOT NULL,
    process_id       BIGINT NOT NULL COMMENT '工序ID',
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT NOT NULL,
    is_key_process   TINYINT(1) DEFAULT 0 COMMENT '是否关键工序',
    setup_time       DECIMAL(10,2) COMMENT '准备时间(分钟)',
    run_time         DECIMAL(10,2) COMMENT '运行时间(分钟/件)',
    queue_time       DECIMAL(10,2) COMMENT '排队时间(分钟)',
    move_time        DECIMAL(10,2) COMMENT '转移时间(分钟)',
    workcenter_id    BIGINT COMMENT '工作中心',
    workcenter_code  VARCHAR(64),
    create_time      DATETIME,
    PRIMARY KEY (step_id),
    INDEX idx_routing (routing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工艺路线步骤';
```

### 2.2 生产执行模块(pro) - 8张表

```sql
-- MES工单
CREATE TABLE mes_pro_workorder (
    wo_id            BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    wo_no            VARCHAR(64) NOT NULL,
    erp_wo_id        BIGINT COMMENT 'ERP工单ID',
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    material_spec    VARCHAR(256),
    quantity         DECIMAL(14,4) NOT NULL,
    completed_qty    DECIMAL(14,4) DEFAULT 0,
    scrapped_qty     DECIMAL(14,4) DEFAULT 0,
    routing_id       BIGINT,
    bom_id           BIGINT,
    status           VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    priority         INT DEFAULT 0,
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (wo_id),
    UNIQUE KEY uk_wo_no (wo_no, tenant_id),
    INDEX idx_status (status),
    INDEX idx_erp_wo (erp_wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单';

-- MES流转卡
CREATE TABLE mes_pro_flow_card (
    card_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    card_no          VARCHAR(64) NOT NULL,
    wo_id            BIGINT NOT NULL,
    wo_no            VARCHAR(64),
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    quantity         DECIMAL(14,4) NOT NULL,
    completed_qty    DECIMAL(14,4) DEFAULT 0,
    scrapped_qty     DECIMAL(14,4) DEFAULT 0,
    routing_id       BIGINT,
    current_step_id  BIGINT COMMENT '当前工序步骤ID',
    current_step_seq INT COMMENT '当前工序序号',
    status           VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    barcode          VARCHAR(128) COMMENT '流转卡条码',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (card_id),
    UNIQUE KEY uk_card_no (card_no, tenant_id),
    INDEX idx_wo (wo_id),
    INDEX idx_status (status),
    INDEX idx_barcode (barcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES流转卡';

-- MES流转卡工序记录
CREATE TABLE mes_pro_flow_card_step (
    step_record_id   BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    card_id          BIGINT NOT NULL,
    card_no          VARCHAR(64),
    wo_id            BIGINT NOT NULL,
    step_id          BIGINT NOT NULL COMMENT '工艺路线步骤ID',
    process_id       BIGINT NOT NULL,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT NOT NULL,
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    operator_id      BIGINT COMMENT '操作员',
    operator_name    VARCHAR(64),
    good_qty         DECIMAL(14,4) DEFAULT 0,
    defect_qty       DECIMAL(14,4) DEFAULT 0,
    scrap_qty        DECIMAL(14,4) DEFAULT 0,
    remark           VARCHAR(500),
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (step_record_id),
    INDEX idx_card (card_id),
    INDEX idx_wo (wo_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES流转卡工序记录';

-- MES派工单
CREATE TABLE mes_pro_dispatch (
    dispatch_id      BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    dispatch_no      VARCHAR(64) NOT NULL,
    wo_id            BIGINT NOT NULL,
    card_id          BIGINT,
    step_record_id   BIGINT,
    process_id       BIGINT NOT NULL,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    workstation_id   BIGINT COMMENT '工位',
    operator_id      BIGINT COMMENT '操作员',
    operator_name    VARCHAR(64),
    quantity         DECIMAL(14,4) NOT NULL,
    completed_qty    DECIMAL(14,4) DEFAULT 0,
    status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (dispatch_id),
    UNIQUE KEY uk_dispatch_no (dispatch_no, tenant_id),
    INDEX idx_wo (wo_id),
    INDEX idx_operator (operator_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES派工单';

-- MES安灯呼叫
CREATE TABLE mes_pro_andon (
    andon_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    andon_no         VARCHAR(64) NOT NULL,
    wo_id            BIGINT,
    card_id          BIGINT,
    step_record_id   BIGINT,
    call_type        VARCHAR(32) NOT NULL COMMENT 'QUALITY/MATERIAL/EQUIPMENT/OTHER',
    call_reason      VARCHAR(500),
    status           VARCHAR(16) DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
    caller_id        BIGINT COMMENT '呼叫人',
    caller_name      VARCHAR(64),
    responder_id     BIGINT COMMENT '响应人',
    responder_name   VARCHAR(64),
    call_time        DATETIME,
    respond_time     DATETIME,
    resolve_time     DATETIME,
    duration_minutes INT COMMENT '持续时长(分钟)',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (andon_id),
    UNIQUE KEY uk_andon_no (andon_no, tenant_id),
    INDEX idx_status (status),
    INDEX idx_call_type (call_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES安灯呼叫';

-- MES SN追溯
CREATE TABLE mes_pro_sn (
    sn_id            BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    sn_code          VARCHAR(128) NOT NULL,
    wo_id            BIGINT NOT NULL,
    card_id          BIGINT,
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    current_step_id  BIGINT,
    current_step_seq INT,
    status           VARCHAR(16) DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
    parent_sn_id     BIGINT COMMENT '父SN(组装追溯)',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (sn_id),
    UNIQUE KEY uk_sn_code (sn_code, tenant_id),
    INDEX idx_wo (wo_id),
    INDEX idx_card (card_id),
    INDEX idx_status (status),
    INDEX idx_parent (parent_sn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN序列号';

-- MES SN工序记录
CREATE TABLE mes_pro_sn_step (
    sn_step_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    sn_id            BIGINT NOT NULL,
    sn_code          VARCHAR(128),
    step_record_id   BIGINT NOT NULL,
    process_id       BIGINT NOT NULL,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT,
    status           VARCHAR(16) DEFAULT 'PENDING',
    operator_id      BIGINT,
    operator_name    VARCHAR(64),
    test_result      VARCHAR(16) COMMENT 'PASS/FAIL',
    test_data        JSON COMMENT '检测数据',
    start_time       DATETIME,
    end_time         DATETIME,
    create_time      DATETIME,
    PRIMARY KEY (sn_step_id),
    INDEX idx_sn (sn_id),
    INDEX idx_step (step_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN工序记录';

-- MES工单工序
CREATE TABLE mes_pro_wo_step (
    wo_step_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    wo_id            BIGINT NOT NULL,
    step_id          BIGINT NOT NULL,
    process_id       BIGINT NOT NULL,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT NOT NULL,
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    status           VARCHAR(16) DEFAULT 'PENDING',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    create_time      DATETIME,
    PRIMARY KEY (wo_step_id),
    INDEX idx_wo (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单工序';
```

### 2.3 仓储扩展模块(wm) - 3张表

```sql
-- MES线边仓
CREATE TABLE mes_wm_line_side_warehouse (
    warehouse_id     BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    warehouse_code   VARCHAR(64) NOT NULL,
    warehouse_name   VARCHAR(128) NOT NULL,
    workcenter_id    BIGINT COMMENT '所属工作中心',
    workcenter_code  VARCHAR(64),
    location_type    VARCHAR(16) DEFAULT 'LINE_SIDE' COMMENT 'LINE_SIDE/WIP',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES线边仓';

-- MES WIP库存
CREATE TABLE mes_wm_wip_stock (
    stock_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    warehouse_id     BIGINT,
    location_id      BIGINT,
    wo_id            BIGINT,
    card_id          BIGINT,
    step_id          BIGINT COMMENT '当前工序',
    quantity         DECIMAL(14,4) NOT NULL,
    status           VARCHAR(16) DEFAULT 'WIP' COMMENT 'WIP/COMPLETED/SCRAPPED',
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (stock_id),
    INDEX idx_material (material_id),
    INDEX idx_wo (wo_id),
    INDEX idx_card (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES WIP在制品库存';

-- MES库存事务
CREATE TABLE mes_wm_stock_transaction (
    tx_id            BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    tx_type          VARCHAR(32) NOT NULL COMMENT 'ISSUE/RECEIVE/TRANSFER',
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    quantity         DECIMAL(14,4) NOT NULL,
    from_warehouse_id BIGINT,
    to_warehouse_id  BIGINT,
    wo_id            BIGINT,
    card_id          BIGINT,
    step_id          BIGINT,
    operator_id      BIGINT,
    tx_time          DATETIME,
    create_time      DATETIME,
    PRIMARY KEY (tx_id),
    INDEX idx_type (tx_type),
    INDEX idx_wo (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES库存事务';
```

### 2.4 质量管理模块(qc) - 5张表

```sql
-- MES来料检验(IQC)
CREATE TABLE mes_qc_iqc (
    iqc_id           BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    iqc_no           VARCHAR(64) NOT NULL,
    receive_id       BIGINT COMMENT 'ERP收货单ID',
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    vendor_id        BIGINT,
    vendor_name      VARCHAR(128),
    quantity         DECIMAL(14,4) NOT NULL,
    sample_qty       DECIMAL(14,4),
    inspect_result   VARCHAR(16) COMMENT 'QUALIFIED/UNQUALIFIED/PENDING',
    inspector_id     BIGINT,
    inspector_name   VARCHAR(64),
    inspect_time     DATETIME,
    status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/INSPECTING/COMPLETED',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (iqc_id),
    UNIQUE KEY uk_iqc_no (iqc_no, tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES来料检验';

-- MES过程检验(IPQC)
CREATE TABLE mes_qc_ipqc (
    ipqc_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    ipqc_no          VARCHAR(64) NOT NULL,
    wo_id            BIGINT NOT NULL,
    card_id          BIGINT,
    step_record_id   BIGINT,
    process_id       BIGINT,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    material_id      BIGINT NOT NULL,
    sample_qty       DECIMAL(14,4),
    defect_qty       DECIMAL(14,4) DEFAULT 0,
    defect_rate      DECIMAL(5,2) COMMENT '不良率(%)',
    inspect_result   VARCHAR(16) COMMENT 'QUALIFIED/UNQUALIFIED/PENDING',
    inspector_id     BIGINT,
    inspector_name   VARCHAR(64),
    inspect_time     DATETIME,
    status           VARCHAR(16) DEFAULT 'PENDING',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (ipqc_id),
    UNIQUE KEY uk_ipqc_no (ipqc_no, tenant_id),
    INDEX idx_wo (wo_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES过程检验';

-- MES出货检验(OQC)
CREATE TABLE mes_qc_oqc (
    oqc_id           BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    oqc_no           VARCHAR(64) NOT NULL,
    so_id            BIGINT COMMENT 'ERP销售订单ID',
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    quantity         DECIMAL(14,4) NOT NULL,
    sample_qty       DECIMAL(14,4),
    inspect_result   VARCHAR(16) COMMENT 'QUALIFIED/UNQUALIFIED/PENDING',
    inspector_id     BIGINT,
    inspector_name   VARCHAR(64),
    inspect_time     DATETIME,
    status           VARCHAR(16) DEFAULT 'PENDING',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (oqc_id),
    UNIQUE KEY uk_oqc_no (oqc_no, tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES出货检验';

-- MES不良品记录
CREATE TABLE mes_qc_defect (
    defect_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    defect_no        VARCHAR(64) NOT NULL,
    source_type      VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/OQC',
    source_id        BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    defect_type      VARCHAR(32) COMMENT 'SCRATCH/CRACK/DIMENSION/OTHER',
    defect_desc      VARCHAR(500),
    quantity         DECIMAL(14,4) NOT NULL,
    disposition      VARCHAR(16) COMMENT 'SCRAP/REWORK/ACCEPT/RETURN',
    wo_id            BIGINT,
    card_id          BIGINT,
    process_id       BIGINT,
    operator_id      BIGINT,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (defect_id),
    INDEX idx_source (source_type, source_id),
    INDEX idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES不良品记录';

-- MES检验项目
CREATE TABLE mes_qc_inspect_item (
    item_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    item_name        VARCHAR(128) NOT NULL,
    item_code        VARCHAR(64) NOT NULL,
    inspect_type     VARCHAR(16) COMMENT 'IQC/IPQC/OQC',
    inspect_method   VARCHAR(32) COMMENT 'VISUAL/MEASUREMENT/TEST',
    standard_value   VARCHAR(128),
    upper_limit      DECIMAL(14,4),
    lower_limit      DECIMAL(14,4),
    unit             VARCHAR(16),
    is_required      TINYINT(1) DEFAULT 1,
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES检验项目';
```

### 2.5 设备管理模块(eq) - 4张表

```sql
-- MES设备台账
CREATE TABLE mes_eq_equipment (
    equipment_id     BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    equipment_code   VARCHAR(64) NOT NULL,
    equipment_name   VARCHAR(128) NOT NULL,
    equipment_type   VARCHAR(32),
    model            VARCHAR(128),
    manufacturer     VARCHAR(128),
    serial_no        VARCHAR(64),
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    location         VARCHAR(256),
    purchase_date    DATE,
    warranty_date    DATE,
    status           VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
    iot_device_id    BIGINT COMMENT '关联IoT设备ID',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uk_code (equipment_code, tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备台账';

-- MES设备保养计划
CREATE TABLE mes_eq_maintenance_plan (
    plan_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    plan_name        VARCHAR(128) NOT NULL,
    equipment_id     BIGINT NOT NULL,
    maintenance_type VARCHAR(16) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
    cycle_days       INT COMMENT '周期(天)',
    next_date        DATE COMMENT '下次保养日期',
    content          TEXT COMMENT '保养内容',
    responsible_id   BIGINT,
    responsible_name VARCHAR(64),
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (plan_id),
    INDEX idx_equipment (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备保养计划';

-- MES设备维修记录
CREATE TABLE mes_eq_repair (
    repair_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    repair_no        VARCHAR(64) NOT NULL,
    equipment_id     BIGINT NOT NULL,
    fault_type       VARCHAR(32),
    fault_desc       VARCHAR(500),
    reporter_id      BIGINT,
    reporter_name    VARCHAR(64),
    repairer_id      BIGINT,
    repairer_name    VARCHAR(64),
    repair_start     DATETIME,
    repair_end       DATETIME,
    downtime_hours   DECIMAL(10,2),
    repair_cost      DECIMAL(14,2),
    status           VARCHAR(16) DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (repair_id),
    UNIQUE KEY uk_repair_no (repair_no, tenant_id),
    INDEX idx_equipment (equipment_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备维修记录';

-- MES设备点检
CREATE TABLE mes_eq_inspection (
    inspection_id    BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    equipment_id     BIGINT NOT NULL,
    inspection_type  VARCHAR(16) COMMENT 'DAILY/SHIFT',
    inspector_id     BIGINT,
    inspector_name   VARCHAR(64),
    inspection_time  DATETIME,
    result           VARCHAR(16) COMMENT 'NORMAL/ABNORMAL',
    items            JSON COMMENT '点检项目及结果',
    remark           VARCHAR(500),
    create_by        BIGINT,
    create_time      DATETIME,
    PRIMARY KEY (inspection_id),
    INDEX idx_equipment (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备点检';
```

### 2.6 数据采集模块(dc) - 2张表

```sql
-- MES数据采集点
CREATE TABLE mes_dc_collection_point (
    point_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    point_code       VARCHAR(64) NOT NULL,
    point_name       VARCHAR(128) NOT NULL,
    point_type       VARCHAR(16) NOT NULL COMMENT 'MANUAL/AUTO',
    equipment_id     BIGINT,
    process_id       BIGINT,
    data_type        VARCHAR(16) COMMENT 'NUMERIC/TEXT/ENUM',
    unit             VARCHAR(16),
    frequency        INT COMMENT '采集频率(秒)',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (point_id),
    UNIQUE KEY uk_code (point_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES数据采集点';

-- MES采集数据记录
CREATE TABLE mes_dc_data_record (
    record_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    point_id         BIGINT NOT NULL,
    point_code       VARCHAR(64),
    wo_id            BIGINT,
    card_id          BIGINT,
    step_record_id   BIGINT,
    numeric_value    DECIMAL(14,4),
    text_value       VARCHAR(500),
    collect_time     DATETIME NOT NULL,
    collect_by       BIGINT COMMENT '采集人(手动时)',
    source           VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
    create_time      DATETIME,
    PRIMARY KEY (record_id),
    INDEX idx_point (point_id),
    INDEX idx_wo (wo_id),
    INDEX idx_time (collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES采集数据记录';
```

### 2.7 SPC模块(spc) - 2张表

```sql
-- MES SPC控制图
CREATE TABLE mes_spc_control_chart (
    chart_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    chart_name       VARCHAR(128) NOT NULL,
    chart_type       VARCHAR(16) NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
    point_id         BIGINT COMMENT '关联采集点',
    material_id      BIGINT,
    process_id       BIGINT,
    ucl              DECIMAL(14,4) COMMENT '上控制限',
    cl               DECIMAL(14,4) COMMENT '中心线',
    lcl              DECIMAL(14,4) COMMENT '下控制限',
    sample_size      INT DEFAULT 5 COMMENT '样本大小',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (chart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC控制图';

-- MES SPC数据点
CREATE TABLE mes_spc_data_point (
    data_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    chart_id         BIGINT NOT NULL,
    sample_group     INT NOT NULL COMMENT '样本组号',
    sample_index     INT COMMENT '组内序号',
    value            DECIMAL(14,4) NOT NULL,
    is_ooc           TINYINT(1) DEFAULT 0 COMMENT '是否超控制限',
    ooc_rule         VARCHAR(32) COMMENT '违规规则',
    collect_time     DATETIME,
    create_time      DATETIME,
    PRIMARY KEY (data_id),
    INDEX idx_chart (chart_id),
    INDEX idx_group (chart_id, sample_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC数据点';
```

## 三、流转卡引擎设计

### 3.1 流转卡状态机

```
CREATED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED
  │         │            │
  └───────→ CANCELLED ←──┘
```

状态说明：
- CREATED: 已创建，未下达
- RELEASED: 已下达，可开始生产
- IN_PROGRESS: 生产中
- COMPLETED: 已完工
- CLOSED: 已关闭
- CANCELLED: 已取消

### 3.2 流转卡服务接口

```java
public interface IMesFlowCardService {
    /** 创建流转卡(从MES工单) */
    MesFlowCardVo createFlowCard(Long woId, BigDecimal quantity);

    /** 下达流转卡 */
    void releaseCard(Long cardId);

    /** 开始工序 */
    void startStep(Long cardId, Long stepRecordId, Long operatorId);

    /** 完成工序 */
    void completeStep(Long cardId, Long stepRecordId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty);

    /** 完成流转卡 */
    void completeCard(Long cardId);

    /** 关闭流转卡 */
    void closeCard(Long cardId);

    /** 取消流转卡 */
    void cancelCard(Long cardId);

    /** 查询流转卡状态 */
    MesFlowCardVo getCardStatus(Long cardId);

    /** 按工单查询流转卡列表 */
    List<MesFlowCardVo> listByWoId(Long woId);
}
```

### 3.3 流转卡工序流转逻辑

1. 创建流转卡时，根据工艺路线自动生成所有工序步骤记录(mes_pro_flow_card_step)
2. 下达流转卡时，状态从CREATED→RELEASED
3. 开始第一道工序时，状态从RELEASED→IN_PROGRESS
4. 完成当前工序后，自动流转到下一道工序
5. 所有工序完成后，流转卡状态→COMPLETED
6. 工单下所有流转卡完成后，MES工单状态→COMPLETED

## 四、工序执行与SN追溯

### 4.1 工序执行流程

1. 派工单分配到操作员
2. 操作员扫码开始工序
3. 执行生产操作
4. 记录良品/不良品/报废数量
5. 完成工序，流转到下一道

### 4.2 SN追溯体系

- 每个产品分配唯一SN码
- SN与流转卡关联
- SN与工序记录关联
- SN支持父子关系(组装追溯)
- 前向追溯: SN→工序→物料→设备→操作员
- 后向追溯: 物料→SN→工序→产品

### 4.3 SN状态

- IN_PRODUCTION: 生产中
- COMPLETED: 已完成
- SCRAPPED: 已报废
- SHIPPED: 已发货

## 五、派工与安灯系统

### 5.1 派工逻辑

- 按工作中心分配工序
- 按操作员技能匹配
- 支持批量派工
- 支持重新派工

### 5.2 安灯系统

呼叫类型：
- QUALITY: 质量异常
- MATERIAL: 物料短缺
- EQUIPMENT: 设备故障
- OTHER: 其他异常

安灯流程：
1. 操作员呼叫(CALLED)
2. 响应人响应(RESPONDED)
3. 问题解决(RESOLVED)
4. 或取消(CANCELLED)

## 六、质量管理

### 6.1 三检制

- IQC(来料检验): 采购物料入库前检验
- IPQC(过程检验): 生产过程中抽检
- OQC(出货检验): 成品出货前检验

### 6.2 不良品处理

处置方式：
- SCRAP: 报废
- REWORK: 返工
- ACCEPT: 让步接收
- RETURN: 退货

### 6.3 SPC统计过程控制

控制图类型：
- X_BAR_R: 均值-极差图
- X_BAR_S: 均值-标准差图
- P: 不良率图
- C: 缺陷数图
- U: 单位缺陷数图

OOC(超控制限)规则：
- 规则1: 1点超出控制限
- 规则2: 连续9点在中心线同侧
- 规则3: 连续6点递增或递减
- 规则4: 连续14点上下交替

## 七、设备管理

### 7.1 设备台账

- 设备基本信息(编码/名称/型号/制造商)
- 设备状态(正常/保养中/故障/报废)
- 关联IoT设备(可选)

### 7.2 保养计划

- 保养类型: 日/周/月/季/年
- 保养周期(天)
- 下次保养日期自动计算
- 保养内容

### 7.3 维修管理

- 故障报修→维修→完成
- 记录停机时长和维修成本
- 维修历史追溯

### 7.4 设备点检

- 日检/班检
- 点检项目JSON格式
- 异常自动触发安灯

## 八、数据采集

### 8.1 采集方式

- MANUAL: 手动录入
- AUTO(IoT): 自动采集(通过IoT设备)

### 8.2 采集点管理

- 采集点编码/名称/类型
- 关联设备和工序
- 数据类型(数值/文本/枚举)
- 采集频率

### 8.3 数据记录

- 与工单/流转卡/工序关联
- 支持数值和文本两种值
- 记录采集时间和来源

## 九、MES实施计划

### 9.1 MES Phase 1 (Week 13-20): 基础框架 + 主数据 + 工单

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 13 | 创建ruoyi-mes模块+数据源配置+MQ消息 | 3 |
| Week 14-15 | MES主数据(md) CRUD + 同步机制 | 5 |
| Week 16-17 | 工艺路线+工序管理 | 5 |
| Week 18-19 | MES工单+工单工序 | 5 |
| Week 20 | 排程基础+前端页面 | 4 |

### 9.2 MES Phase 2 (Week 21-27): 流转卡 + 工序执行 + SN追溯

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 21-22 | 流转卡引擎(状态机+服务) | 6 |
| Week 23-24 | 工序执行+派工 | 5 |
| Week 25 | SN追溯体系 | 4 |
| Week 26 | 安灯系统 | 3 |
| Week 27 | Phase 2前端页面 | 5 |

### 9.3 MES Phase 3 (Week 28-34): 仓储扩展 + 质量 + 设备 + SPC

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 28-29 | 仓储扩展(线边仓/WIP) | 4 |
| Week 30-31 | 质量管理(IQC/IPQC/OQC) | 5 |
| Week 32 | 设备管理 | 4 |
| Week 33 | 数据采集+SPC | 4 |
| Week 34 | Phase 3前端页面+集成测试 | 4 |

### 9.4 工作量汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| Phase 1 | Week 13-20 | 22 |
| Phase 2 | Week 21-27 | 23 |
| Phase 3 | Week 28-34 | 21 |
| **合计** | Week 13-34 (22周) | **66** |

## 十、MES前端规划

### 10.1 目录结构

```
plus-ui/src/views/mes/
├── md/                    # 主数据
│   ├── material/          # 物料(只读)
│   ├── bom/               # BOM(只读)
│   └── routing/           # 工艺路线(只读)
├── pro/                   # 生产执行
│   ├── workorder/         # MES工单
│   ├── flow-card/         # 流转卡
│   ├── dispatch/          # 派工
│   ├── andon/             # 安灯
│   └── sn/                # SN追溯
├── wm/                    # 仓储扩展
│   ├── line-side/         # 线边仓
│   └── wip-stock/         # WIP库存
├── qc/                    # 质量管理
│   ├── iqc/               # 来料检验
│   ├── ipqc/              # 过程检验
│   ├── oqc/               # 出货检验
│   └── defect/            # 不良品
├── eq/                    # 设备管理
│   ├── equipment/         # 设备台账
│   ├── maintenance/       # 保养计划
│   ├── repair/            # 维修记录
│   └── inspection/        # 点检
├── dc/                    # 数据采集
│   ├── collection-point/  # 采集点
│   └── data-record/       # 数据记录
└── spc/                   # SPC
    ├── control-chart/     # 控制图
    └── data-point/        # 数据点
```

### 10.2 新增页面估算

- md: 3页面
- pro: 5页面
- wm: 2页面
- qc: 4页面
- eq: 4页面
- dc: 2页面
- spc: 2页面
- 合计: ~22页面

### 10.3 关键前端组件

- 流转卡看板: 实时展示流转卡状态
- 安灯看板: 实时展示安灯呼叫
- SPC控制图: ECharts绘制控制图
- SN追溯树: 树形展示追溯关系

## 十一、ERP与MES边界划分

### 11.1 九条划分原则

1. 主数据统一ERP: 物料/BOM/客户/供应商/工厂/仓库/库位/工序/路由在ERP维护，MES只读引用
2. 质量管理统一MES: IQC/IPQC/OQC/不良品/SPC在MES管理
3. 设备管理统一MES: 设备台账/保养/维修/点检/OEE在MES管理
4. 库存统一wm_material_stock: ERP和MES共用同一库存表，通过@DS("erp")访问
5. 工单创建在ERP，执行在MES: ERP创建工单→MES创建MES工单(含工序/流转卡)
6. 采购/销售只在ERP: 采购和销售业务不涉及MES
7. 数据采集只在MES: 设备数据采集/传感器数据在MES处理
8. 排班调度在ERP: 班次/班组/排班计划在ERP维护
9. 工艺路线在ERP维护，MES执行: ERP维护工艺路线→MES按工序执行

### 11.2 消除重复建设

通过边界划分可节省约17人天：
- 主数据MES只读: 节省5人天(CRUD开发)
- 库存共用: 节省4人天(库存表开发)
- 排班共用: 节省3人天
- 工艺路线MES只读: 节省3人天
- 审批流复用: 节省2人天

### 11.3 ERP→MES数据同步机制

- MQ消息: ERP工单下达→发送消息→MES创建MES工单
- 定时同步: 主数据每日增量同步
- 共享表: wm_material_stock通过@DS("erp")直接访问
