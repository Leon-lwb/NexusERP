# NexusERP - MES模块综合文档

> **文档状态**: ✅ 实施完成文档 — 本文档为MES模块的完整设计方案与可执行开发记录。
> MES Phase 1-3 全部交付（实际 Week 17-27，领先原计划约 5 周），详见 §十九。
> ERP侧集成准备状态：主数据API已就绪✅，MQ消息发送+队列常量已完成✅，MES→ERP MQ双向贯通✅（Week 25 ErpIqcCompletedConsumer + ErpWorkOrderCompletedConsumer）。
> MES↔IoT集成状态：IMesAndonService 安灯联动✅（Week 23），设备关联字段已建✅，IoT自动采集归档⏳待打通。
> 参考实施计划：[MES Phase 1~3 + IoT 阶段一实施计划](./plans/2026-06-16-mes-iot-phase-plan.md)
> 后续升级方案：[NexusERP 三模块共同升级方案](NexusERP_三模块共同升级方案.md)
>
> **命名规范**: MES表命名与ERP保持一致，采用一级前缀 `{模块缩写}_{业务实体名}`（如 `pro_work_order`、`qc_iqc`），
> 不使用 `mes_` 二级前缀，因为MES在独立库 `ry-mes` 中，库名已标识来源。
>
> **数据采集归属**: MES负责手工/业务采集（质检记录、工序数据录入等），IoT负责自动设备采集（MQTT/传感器数据）。
> SPC消费MES归档后的采集结果。MES的 `dc_collection_point` 表中 `point_type` 字段区分 `MANUAL`（MES采集）和 `AUTO`（IoT采集）。

## 一、MES模块总体设计

### 1.1 MES定位

MES(Manufacturing Execution System)制造执行系统，位于ERP计划层与设备控制层之间，负责车间级生产执行管理。

### 1.2 MES模块结构（7个子模块）

```
ruoyi-mes/
└── com/nexus/mes/
    ├── pro/      # 生产执行(工单/流转卡/派工/安灯/SN)
    ├── wm/       # 仓储扩展(复用ERP库存表,仅业务逻辑)
    ├── qc/       # 质量管理(IQC/IPQC/OQC)
    ├── eq/       # 设备管理(台账/保养/维修)
    ├── dc/       # 数据采集(设备数据/传感器)
    ├── spc/      # 统计过程控制
    └── common/   # 公共(状态枚举/工具类/ERP桥接)
```

> **重要变更**: 原规划中的 `md/`(主数据)子模块已删除。MES不建主数据表，物料/BOM/工艺路线/工序均通过跨库查询ERP数据获取，避免数据双写和同步复杂度。

### 1.3 数据源配置

- 数据库: ry-mes (独立库) — 📋 待创建
- 注解: `@DS("mes")` — 📋 待配置
- 与ERP共享: wm_material_stock (通过`@DS("erp")`访问) — ✅ 已存在
- 跨库查询: MES Service通过 `DynamicDataSourceContextHolder` 临时切换到 `erp` 数据源读取主数据

### 1.4 数据流架构

```
ERP工单审批通过 → MQ消息(RabbitMQ erp.direct) → MES消费创建MES工单+工序
  → 流转卡下达 → 派工 → 工序执行 → 完工 → MQ通知ERP完工入库
  → IoT设备数据(可选) → 数据采集点记录
  → 安灯呼叫 → SSE实时推送前端看板
```

### 1.5 技术栈复用

| 组件 | 来源 | 用途 | 状态 |
|------|------|------|------|
| ruoyi-common-mq | 已有(21个Java文件) | ERP↔MES消息通信 | ✅ 可用 |
| ruoyi-flowable | 已有(74个Java文件) | MES工单等关键单据审批流 | ✅ 可用，ERP已集成9个业务单据 |
| ruoyi-common-flowable | 已有(20个Java文件) | Flowable公共工具/监听器 | ✅ 可用 |
| ruoyi-common-sse | 已有 | 安灯看板实时推送 | ✅ 可用 |
| ruoyi-common-job (SnailJob) | 已有 | 定时任务(数据清理/对账) | ✅ 可用 |
| ruoyi-common-redis (Redisson) | 已有 | 分布式锁/缓存 | ✅ 可用 |

---

## 二、MES数据表设计（~17张表）

> ⚠️ 本节 DDL 已与 `sql/` 下实际脚本对齐，后续变更以 SQL 脚本为准。

> **精简说明**: 原规划28张表精简为~17张。主要削减：
> - 主数据表4张 → 0张（跨库查询ERP）
> - 仓储扩展表3张 → 0张（复用ERP wm_material_stock）
> - 质量管理表5张 → 3张（不良品+检验项目合入检验表JSON字段）
> - 设备管理表4张 → 3张（点检合入设备台账JSON字段）
>
> **命名规范**: 与ERP一致，一级前缀 `{模块}_{实体}`，如 `pro_work_order`、`qc_iqc`、`eq_equipment`。

### 2.1 生产执行模块(pro) — 7张表

```sql
-- MES工单
CREATE TABLE pro_work_order (
    mes_wo_id BIGINT NOT NULL COMMENT 'MES工单ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    erp_wo_id BIGINT NULL DEFAULT NULL COMMENT '关联ERP工单ID',
    wo_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MES工单编号(继承或派生)',
    material_id BIGINT NULL DEFAULT NULL COMMENT '产品物料ID',
    material_code VARCHAR(64) NULL DEFAULT NULL COMMENT '产品编码(冗余)',
    material_name VARCHAR(200) NULL DEFAULT NULL COMMENT '产品名称(冗余)',
    planned_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '计划数量',
    completed_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
    scrap_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '报废数量',
    unit_id BIGINT NULL DEFAULT NULL COMMENT '单位ID',
    unit_name VARCHAR(32) NULL DEFAULT NULL COMMENT '单位名称',
    plan_start_date DATETIME NULL DEFAULT NULL COMMENT '计划开工日期',
    plan_end_date DATETIME NULL DEFAULT NULL COMMENT '计划完工日期',
    actual_start_date DATETIME NULL DEFAULT NULL COMMENT '实际开工时间',
    actual_end_date DATETIME NULL DEFAULT NULL COMMENT '实际完工时间',
    workshop_id BIGINT NULL DEFAULT NULL COMMENT '车间ID',
    workshop_name VARCHAR(100) NULL DEFAULT NULL COMMENT '车间名称(冗余)',
    bom_id BIGINT NULL DEFAULT NULL COMMENT 'BOM ID',
    routing_id BIGINT NULL DEFAULT NULL COMMENT '工艺路线ID(MES侧维护)',
    wo_status VARCHAR(32) NULL DEFAULT 'CREATED' COMMENT '工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED',
    priority INT DEFAULT 5 COMMENT '优先级(1最高,9最低)',
    remark VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
    select_flow_id BIGINT NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
    create_dept BIGINT NULL DEFAULT NULL COMMENT '创建部门',
    create_by BIGINT NULL DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (mes_wo_id),
    UNIQUE INDEX uk_wo_no (wo_no),
    INDEX idx_erp_wo_id (erp_wo_id),
    INDEX idx_material_id (material_id),
    INDEX idx_wo_status (wo_status),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单表';

-- MES工单工序
CREATE TABLE pro_work_order_step (
    step_id BIGINT NOT NULL COMMENT '工序步骤ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    mes_wo_id BIGINT NOT NULL COMMENT '所属MES工单ID',
    sequence_num INT NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
    process_id BIGINT NULL DEFAULT NULL COMMENT 'ERP工序ID(bd_routing_step.routing_step_id)',
    process_code VARCHAR(64) NULL DEFAULT NULL COMMENT '工序编码(冗余)',
    process_name VARCHAR(200) NULL DEFAULT NULL COMMENT '工序名称(冗余)',
    workcenter_id BIGINT NULL DEFAULT NULL COMMENT '工作中心ID',
    workcenter_code VARCHAR(64) NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
    planned_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '计划数量',
    completed_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '完成数量',
    scrap_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '报废数量',
    step_status VARCHAR(32) NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED',
    is_key_process TINYINT(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
    start_time DATETIME NULL DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME NULL DEFAULT NULL COMMENT '结束时间',
    remark VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
    create_dept BIGINT NULL DEFAULT NULL COMMENT '创建部门',
    create_by BIGINT NULL DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (step_id),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_step_status (step_status),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单工序步骤表';

-- MES流转卡
CREATE TABLE pro_flow_card (
    card_id BIGINT NOT NULL COMMENT '流转卡ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    card_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '流转卡编号',
    mes_wo_id BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no VARCHAR(64) NULL DEFAULT NULL COMMENT '工单编号(冗余)',
    material_id BIGINT NULL DEFAULT NULL COMMENT '物料ID',
    material_code VARCHAR(64) NULL DEFAULT NULL COMMENT '物料编码(冗余)',
    material_name VARCHAR(200) NULL DEFAULT NULL COMMENT '物料名称(冗余)',
    planned_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '计划数量',
    completed_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
    scrap_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '报废数量',
    current_step_id BIGINT NULL DEFAULT NULL COMMENT '当前工序步骤记录ID',
    current_step_seq INT NULL DEFAULT NULL COMMENT '当前工序序号',
    card_status VARCHAR(32) NULL DEFAULT 'CREATED' COMMENT '流转卡状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    barcode VARCHAR(128) NULL DEFAULT NULL COMMENT '流转卡条码',
    remark VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
    create_dept BIGINT NULL DEFAULT NULL COMMENT '创建部门',
    create_by BIGINT NULL DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (card_id),
    UNIQUE INDEX uk_card_no (card_no),
    UNIQUE INDEX uk_barcode (barcode),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_status (card_status),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES流转卡表';

-- MES流转卡工序记录
CREATE TABLE pro_flow_card_step (
    step_record_id BIGINT NOT NULL COMMENT '工序记录ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    card_id BIGINT NOT NULL COMMENT '关联流转卡ID',
    card_no VARCHAR(64) NULL DEFAULT NULL COMMENT '流转卡编号(冗余)',
    mes_wo_id BIGINT NOT NULL COMMENT '关联MES工单ID(冗余便于查询)',
    wo_step_id BIGINT NULL DEFAULT NULL COMMENT '关联工单工序步骤ID(pro_work_order_step.step_id)',
    sequence_num INT NOT NULL DEFAULT 0 COMMENT '工序序号(从1开始)',
    process_id BIGINT NULL DEFAULT NULL COMMENT '工序ID(来自ERP bd_routing_step)',
    process_code VARCHAR(64) NULL DEFAULT NULL COMMENT '工序编码(冗余)',
    process_name VARCHAR(200) NULL DEFAULT NULL COMMENT '工序名称(冗余)',
    workcenter_id BIGINT NULL DEFAULT NULL COMMENT '工作中心ID',
    workcenter_code VARCHAR(64) NULL DEFAULT NULL COMMENT '工作中心编码(冗余)',
    is_key_process TINYINT(1) NULL DEFAULT 0 COMMENT '是否关键工序(0否 1是)',
    step_status VARCHAR(32) NULL DEFAULT 'PENDING' COMMENT '工序状态: PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
    operator_id BIGINT NULL DEFAULT NULL COMMENT '操作员ID',
    operator_name VARCHAR(64) NULL DEFAULT NULL COMMENT '操作员姓名(冗余)',
    planned_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '计划数量',
    good_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '良品数量',
    defect_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
    scrap_qty DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '报废数量',
    actual_start_time DATETIME NULL DEFAULT NULL COMMENT '实际开始时间',
    actual_end_time DATETIME NULL DEFAULT NULL COMMENT '实际结束时间',
    remark VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
    create_dept BIGINT NULL DEFAULT NULL COMMENT '创建部门',
    create_by BIGINT NULL DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (step_record_id),
    INDEX idx_card_id (card_id),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_step_status (step_status),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES流转卡工序记录表';

-- MES派工单
CREATE TABLE pro_dispatch (
    dispatch_id BIGINT NOT NULL COMMENT '派工单ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    dispatch_no VARCHAR(64) NOT NULL COMMENT '派工单编号',
    mes_wo_id BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id BIGINT COMMENT '关联流转卡ID',
    card_no VARCHAR(64) COMMENT '流转卡编号(冗余)',
    step_record_id BIGINT COMMENT '关联流转卡工序记录ID',
    sequence_num INT COMMENT '工序序号(冗余)',
    process_id BIGINT NOT NULL COMMENT '工序ID',
    process_code VARCHAR(64),
    process_name VARCHAR(200),
    workcenter_id BIGINT,
    workcenter_code VARCHAR(64),
    operator_id BIGINT COMMENT '操作员ID(SchWorker.workerId)',
    operator_name VARCHAR(64) COMMENT '操作员姓名',
    planned_qty DECIMAL(18,4) DEFAULT 0 COMMENT '派工数量',
    completed_qty DECIMAL(18,4) DEFAULT 0 COMMENT '已完成数量',
    good_qty DECIMAL(18,4) DEFAULT 0 COMMENT '良品数量',
    defect_qty DECIMAL(18,4) DEFAULT 0 COMMENT '不良品数量',
    scrap_qty DECIMAL(18,4) DEFAULT 0 COMMENT '报废数量',
    dispatch_status VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
    plan_start_time DATETIME,
    plan_end_time DATETIME,
    actual_start_time DATETIME,
    actual_end_time DATETIME,
    remark VARCHAR(500),
    create_dept BIGINT,
    create_by BIGINT,
    create_time DATETIME,
    update_by BIGINT,
    update_time DATETIME,
    del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (dispatch_id),
    UNIQUE INDEX uk_dispatch_no (dispatch_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_step_record_id (step_record_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_dispatch_status (dispatch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES派工单';

-- MES安灯呼叫
CREATE TABLE pro_andon (
    andon_id BIGINT NOT NULL COMMENT '安灯ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    andon_no VARCHAR(64) NOT NULL COMMENT '安灯编号',
    mes_wo_id BIGINT COMMENT '关联MES工单ID',
    wo_no VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id BIGINT COMMENT '关联流转卡ID',
    card_no VARCHAR(64) COMMENT '流转卡编号(冗余)',
    step_record_id BIGINT COMMENT '关联流转卡工序记录ID',
    call_type VARCHAR(32) NOT NULL COMMENT '呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER',
    call_reason VARCHAR(500) COMMENT '呼叫原因',
    andon_status VARCHAR(32) DEFAULT 'CALLED' COMMENT 'CALLED/RESPONDED/RESOLVED/CANCELLED',
    caller_id BIGINT COMMENT '呼叫人ID',
    caller_name VARCHAR(64) COMMENT '呼叫人姓名',
    responder_id BIGINT COMMENT '响应人ID',
    responder_name VARCHAR(64) COMMENT '响应人姓名',
    call_time DATETIME COMMENT '呼叫时间',
    respond_time DATETIME COMMENT '响应时间',
    resolve_time DATETIME COMMENT '解决时间',
    duration_minutes INT COMMENT '持续时长(分钟, 解决时计算)',
    source VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT(来源)',
    remark VARCHAR(500),
    create_dept BIGINT,
    create_by BIGINT,
    create_time DATETIME,
    update_by BIGINT,
    update_time DATETIME,
    del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (andon_id),
    UNIQUE INDEX uk_andon_no (andon_no),
    INDEX idx_andon_status (andon_status),
    INDEX idx_call_type (call_type),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES安灯呼叫';

-- MES SN序列号追溯
CREATE TABLE pro_sn (
    sn_id BIGINT NOT NULL COMMENT 'SN ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_code VARCHAR(128) NOT NULL COMMENT 'SN序列号编码',
    mes_wo_id BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id BIGINT COMMENT '关联流转卡ID',
    card_no VARCHAR(64) COMMENT '流转卡编号(冗余)',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    material_code VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name VARCHAR(128) COMMENT '物料名称(冗余)',
    current_step_seq INT COMMENT '当前工序序号',
    sn_status VARCHAR(32) DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
    parent_sn_id BIGINT COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
    parent_sn_code VARCHAR(128) COMMENT '父SN编码(冗余)',
    source VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
    production_date DATE COMMENT '生产日期',
    remark VARCHAR(500),
    create_dept BIGINT,
    create_by BIGINT,
    create_time DATETIME,
    update_by BIGINT,
    update_time DATETIME,
    del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (sn_id),
    UNIQUE INDEX uk_sn_code (sn_code),
    INDEX idx_sn_status (sn_status),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_material_id (material_id),
    INDEX idx_parent_sn_id (parent_sn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN序列号追溯';
```

### 2.2 质量管理模块(qc) — 3张表

```sql
-- MES来料检验(IQC)
CREATE TABLE qc_iqc (
    iqc_id BIGINT NOT NULL COMMENT 'IQC ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    iqc_no VARCHAR(64) NOT NULL COMMENT 'IQC编号',
    receive_id BIGINT COMMENT 'ERP收货单ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    material_code VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name VARCHAR(128) COMMENT '物料名称(冗余)',
    vendor_id BIGINT COMMENT '供应商ID',
    vendor_name VARCHAR(128) COMMENT '供应商名称(冗余)',
    quantity DECIMAL(14,4) NOT NULL COMMENT '检验数量',
    sample_qty DECIMAL(14,4) COMMENT '抽样数量',
    inspect_result VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id BIGINT COMMENT '检验员ID',
    inspector_name VARCHAR(64) COMMENT '检验员姓名',
    inspect_time DATETIME COMMENT '检验时间',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (iqc_id),
    UNIQUE INDEX uk_iqc_no (iqc_no),
    INDEX idx_status (status),
    INDEX idx_receive_id (receive_id),
    INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES来料检验';

-- MES过程检验(IPQC)
CREATE TABLE qc_ipqc (
    ipqc_id BIGINT NOT NULL COMMENT 'IPQC ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    ipqc_no VARCHAR(64) NOT NULL COMMENT 'IPQC编号',
    mes_wo_id BIGINT NOT NULL COMMENT 'MES工单ID',
    wo_no VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id BIGINT COMMENT '流转卡ID',
    step_record_id BIGINT COMMENT '流转卡工序记录ID',
    process_id BIGINT COMMENT '工序ID',
    process_code VARCHAR(64) COMMENT '工序编码',
    process_name VARCHAR(128) COMMENT '工序名称',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    material_code VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name VARCHAR(128) COMMENT '物料名称(冗余)',
    sample_qty DECIMAL(14,4) COMMENT '抽样数量',
    defect_qty DECIMAL(14,4) DEFAULT 0 COMMENT '不良数量',
    defect_rate DECIMAL(5,2) COMMENT '不良率(%)',
    inspect_result VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id BIGINT COMMENT '检验员ID',
    inspector_name VARCHAR(64) COMMENT '检验员姓名',
    inspect_time DATETIME COMMENT '检验时间',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (ipqc_id),
    UNIQUE INDEX uk_ipqc_no (ipqc_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_status (status),
    INDEX idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES过程检验';

-- MES出货检验(OQC)
CREATE TABLE qc_oqc (
    oqc_id BIGINT NOT NULL COMMENT 'OQC ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    oqc_no VARCHAR(64) NOT NULL COMMENT 'OQC编号',
    so_id BIGINT COMMENT 'ERP销售订单ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    material_code VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name VARCHAR(128) COMMENT '物料名称(冗余)',
    quantity DECIMAL(14,4) NOT NULL COMMENT '检验数量',
    sample_qty DECIMAL(14,4) COMMENT '抽样数量',
    inspect_result VARCHAR(16) DEFAULT 'PENDING' COMMENT '检验结果: QUALIFIED/UNQUALIFIED/PENDING',
    defect_info JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id BIGINT COMMENT '检验员ID',
    inspector_name VARCHAR(64) COMMENT '检验员姓名',
    inspect_time DATETIME COMMENT '检验时间',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态: PENDING/INSPECTING/COMPLETED',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (oqc_id),
    UNIQUE INDEX uk_oqc_no (oqc_no),
    INDEX idx_status (status),
    INDEX idx_so_id (so_id),
    INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES出货检验';
```

### 2.3 设备管理模块(eq) — 3张表

```sql
-- MES设备台账
CREATE TABLE eq_equipment (
    equipment_id BIGINT NOT NULL COMMENT '设备ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码',
    equipment_name VARCHAR(128) NOT NULL COMMENT '设备名称',
    equipment_type VARCHAR(32) COMMENT '设备类型',
    model VARCHAR(128) COMMENT '型号',
    manufacturer VARCHAR(128) COMMENT '制造商',
    serial_no VARCHAR(64) COMMENT '出厂编号',
    workcenter_id BIGINT COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
    workcenter_code VARCHAR(64) COMMENT '工作中心编码(冗余)',
    location VARCHAR(256) COMMENT '安装位置',
    purchase_date DATE COMMENT '购置日期',
    warranty_date DATE COMMENT '保修到期',
    status VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
    inspection_items JSON COMMENT '点检项目及标准(JSON)',
    last_inspection JSON COMMENT '最近一次点检结果(JSON)',
    iot_device_id BIGINT COMMENT '关联IoT设备ID(预留,无外键)',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (equipment_id),
    UNIQUE INDEX uk_equipment_code (equipment_code),
    INDEX idx_status (status),
    INDEX idx_workcenter_id (workcenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备台账';

-- MES设备保养计划
CREATE TABLE eq_maintenance_plan (
    plan_id BIGINT NOT NULL COMMENT '计划ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    plan_name VARCHAR(128) NOT NULL COMMENT '计划名称',
    equipment_id BIGINT NOT NULL COMMENT '设备ID',
    maintenance_type VARCHAR(16) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
    cycle_days INT COMMENT '周期(天)',
    next_date DATE COMMENT '下次保养日期(自动计算)',
    content TEXT COMMENT '保养内容',
    responsible_id BIGINT COMMENT '负责人ID',
    responsible_name VARCHAR(64) COMMENT '负责人姓名',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (plan_id),
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_next_date (next_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备保养计划';

-- MES设备维修记录
CREATE TABLE eq_repair (
    repair_id BIGINT NOT NULL COMMENT '维修ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    repair_no VARCHAR(64) NOT NULL COMMENT '维修单号',
    equipment_id BIGINT NOT NULL COMMENT '设备ID',
    fault_type VARCHAR(32) COMMENT '故障类型',
    fault_desc VARCHAR(500) COMMENT '故障描述',
    reporter_id BIGINT COMMENT '报修人ID',
    reporter_name VARCHAR(64) COMMENT '报修人姓名',
    repairer_id BIGINT COMMENT '维修人ID',
    repairer_name VARCHAR(64) COMMENT '维修人姓名',
    repair_start DATETIME COMMENT '维修开始时间',
    repair_end DATETIME COMMENT '维修结束时间',
    downtime_hours DECIMAL(10,2) COMMENT '停机时长(小时)',
    repair_cost DECIMAL(14,2) COMMENT '维修成本',
    status VARCHAR(16) DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (repair_id),
    UNIQUE INDEX uk_repair_no (repair_no),
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备维修记录';
```

### 2.4 数据采集 + SPC模块 — 4张表（Phase 3）

```sql
-- MES数据采集点
CREATE TABLE dc_collection_point (
    point_id BIGINT NOT NULL COMMENT '采集点ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    point_code VARCHAR(64) NOT NULL COMMENT '采集点编码',
    point_name VARCHAR(128) NOT NULL COMMENT '采集点名称',
    point_type VARCHAR(16) NOT NULL COMMENT 'MANUAL/AUTO',
    equipment_id BIGINT COMMENT '关联设备ID',
    process_id BIGINT COMMENT '关联工序ID',
    data_type VARCHAR(16) COMMENT 'NUMERIC/TEXT/ENUM',
    unit VARCHAR(16) COMMENT '单位',
    frequency INT COMMENT '采集频率(秒)',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (point_id),
    UNIQUE INDEX uk_point_code (point_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES数据采集点';

-- MES采集数据记录(先存MySQL,数据量超500万行后考虑迁移TDengine)
CREATE TABLE dc_data_record (
    record_id BIGINT NOT NULL COMMENT '记录ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    point_id BIGINT NOT NULL COMMENT '采集点ID',
    point_code VARCHAR(64) COMMENT '采集点编码(冗余)',
    mes_wo_id BIGINT COMMENT '关联MES工单ID',
    card_id BIGINT COMMENT '关联流转卡ID',
    step_record_id BIGINT COMMENT '关联工序记录ID',
    numeric_value DECIMAL(14,4) COMMENT '数值',
    text_value VARCHAR(500) COMMENT '文本值',
    collect_time DATETIME NOT NULL COMMENT '采集时间',
    collect_by BIGINT COMMENT '采集人(手动时)',
    source VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (record_id),
    INDEX idx_point_id (point_id),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_collect_time (collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES采集数据记录';

-- MES SPC控制图
CREATE TABLE spc_control_chart (
    chart_id BIGINT NOT NULL COMMENT '控制图ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_name VARCHAR(128) NOT NULL COMMENT '控制图名称',
    chart_type VARCHAR(16) NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
    point_id BIGINT COMMENT '关联采集点ID',
    material_id BIGINT COMMENT '关联物料ID',
    process_id BIGINT COMMENT '关联工序ID',
    ucl DECIMAL(14,4) COMMENT '上控制限(analyze计算回写)',
    cl DECIMAL(14,4) COMMENT '中心线',
    lcl DECIMAL(14,4) COMMENT '下控制限',
    sample_size INT DEFAULT 5 COMMENT '样本大小',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark VARCHAR(500) COMMENT '备注',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (chart_id),
    INDEX idx_point_id (point_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC控制图';

-- MES SPC数据点
CREATE TABLE spc_data_point (
    data_id BIGINT NOT NULL COMMENT '数据点ID(主键)',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_id BIGINT NOT NULL COMMENT '控制图ID',
    sample_group INT NOT NULL COMMENT '样本组号',
    sample_index INT COMMENT '组内序号',
    value DECIMAL(14,4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
    is_ooc CHAR(1) DEFAULT '0' COMMENT '是否超控制限(0/1)',
    ooc_rule VARCHAR(32) COMMENT '违规规则(RULE1/2/3/4, 多规则逗号拼接)',
    collect_time DATETIME COMMENT '采集时间',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME COMMENT '更新时间',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (data_id),
    INDEX idx_chart_id (chart_id),
    INDEX idx_group (chart_id, sample_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC数据点';
```

---

## 三、主数据访问策略（核心设计变更）

### 3.1 原方案 vs 修订方案

| 维度 | 原方案 | 修订方案 | 原因 |
|------|--------|---------|------|
| 物料 | md_material 同步表 | 跨库查询ERP bd_material | 避免数据双写 |
| BOM | md_bom + bom_line | 跨库查询ERP bd_bom | 减少同步复杂度 |
| 工艺路线 | md_routing + routing_step | 跨库查询ERP | ERP已有完整数据 |
| WIP库存 | wm_wip_stock | 复用ERP wm_material_stock | 避免库存双写 |
| 线边仓 | wm_line_side_warehouse | 复用ERP仓库+location区分 | 减少表冗余 |

### 3.2 跨库查询实现

```java
/**
 * MES-ERP桥接服务，封装跨库查询逻辑
 * MES Service通过DynamicDataSourceContextHolder临时切换到erp数据源读取
 */
public interface IMesErpBridgeService {
    /** 查询物料信息(从ERP) */
    Map<String, Object> getMaterial(Long materialId);
    /** 查询BOM明细(从ERP) */
    List<Map<String, Object>> getBomLines(Long bomId);
    /** 查询工艺路线步骤(从ERP) */
    List<Map<String, Object>> getRoutingSteps(Long routingId);
}
```

### 3.3 前端主数据访问

MES前端页面直接调用ERP已有API：

| 数据 | ERP API | MES前端使用场景 |
|------|---------|---------------|
| 物料 | `/erp/bd/material/list` | 工单物料选择、检验物料显示 |
| BOM | `/erp/bd/bom/list` | 工单BOM关联 |
| 工艺路线 | ERP工艺路线API | 工单工序自动生成 |
| 工序 | ERP工序API | 工序名称/编码显示 |

---

## 四、ERP→MES工单下达与审批流

### 4.1 ERP→MES工单下达流程

```
ERP PrdWorkOrderServiceImpl.afterFlowHandle() 审批通过(APPROVED)
  → ReliableMessageProducer 发送消息到 RabbitMQ erp.direct exchange
    → MesWorkOrderSyncConsumer(@DS("mes")) 消费
      → 创建 pro_work_order
      → 根据ERP工艺路线自动生成 pro_work_order_step
```

**ERP侧改动（`PrdWorkOrderServiceImpl.afterFlowHandle()`）：**
- 在APPROVED分支增加MQ消息发送逻辑
- 消息类型：`WorkOrderReleaseMessage`，包含ERP工单ID、物料ID、数量、工艺路线ID、BOM ID等
- **当前状态：✅ 已实现(Week 16)**。`afterFlowHandle()`审批通过后发送`WorkOrderReleaseMessage`到`mes.workorder.queue`

### 4.2 MES工单审批流（复用Flowable）

参照ERP的 `WmMaterialReceiveServiceImpl` 模式（已9个业务单据集成此模式）：

| 层次 | 改动内容 |
|------|---------|
| 实体层 | `selectFlowId Long` 字段（已在DDL中包含） |
| 接口层 | Service继承 `WfCallBackServiceI`，声明 `submit()` |
| 实现层 | `submit()` 调用 `processService.startProcessByDataId()`；`afterFlowHandle()` 同步业务状态 |
| 控制器层 | `POST /{id}/submit?selectFlowId=` + `GET /{id}/proc` |
| 前端层 | 审批流下拉选择器 + "提交审批"按钮 + "查看流程"按钮 |
| 数据库 | `wf_custom_form` 插入记录绑定 `businessService` + `routeName` + `deployId` |

**状态联动：**
- APPROVED → 工单状态 RELEASED（已下达，可创建流转卡）
- REJECTED → 退回 CREATED

### 4.3 MES完工→ERP联动

```
MES流转卡所有工序完成 → 流转卡状态COMPLETED
  → 更新MES工单 completed_qty
  → 若MES工单下所有流转卡均完成 → MES工单状态COMPLETED
    → MQ消息通知ERP → ERP工单状态更新 + 完工入库
```

---

## 五、流转卡引擎设计

### 5.1 流转卡状态机

```
CREATED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED
  │         │            │
  └───────→ CANCELLED ←──┘
```

状态说明：
- CREATED: 已创建，未下达
- RELEASED: 已下达，可开始生产
- IN_PROGRESS: 生产中（第一道工序开始时自动转入）
- COMPLETED: 已完工（所有工序完成）
- CLOSED: 已关闭
- CANCELLED: 已取消

### 5.2 流转卡服务接口

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

### 5.3 流转卡工序流转逻辑

1. 创建流转卡时，根据工艺路线自动生成所有工序步骤记录(pro_flow_card_step)
2. 下达流转卡时，状态从CREATED→RELEASED
3. 开始第一道工序时，状态从RELEASED→IN_PROGRESS
4. 完成当前工序后，自动流转到下一道工序
5. 所有工序完成后，流转卡状态→COMPLETED
6. 工单下所有流转卡完成后，MES工单状态→COMPLETED

---

## 六、安灯系统

### 6.1 呼叫类型

- QUALITY: 质量异常
- MATERIAL: 物料短缺
- EQUIPMENT: 设备故障
- OTHER: 其他异常

### 6.2 安灯流程

1. 操作员呼叫(CALLED)
2. 响应人响应(RESPONDED)
3. 问题解决(RESOLVED)
4. 或取消(CANCELLED)

### 6.3 实时推送

安灯呼叫/响应/解决 → 通过 `SseMessageUtils.publishMessage()` 推送到前端安灯看板。

前端使用 `EventSource` 接收SSE事件，实时更新看板状态。

---

## 七、SN追溯体系

### 7.1 SN追溯设计

- 每个产品分配唯一SN码
- SN与流转卡关联
- SN支持父子关系(组装追溯)
- 前向追溯: SN→工序→物料→设备→操作员
- 后向追溯: 物料→SN→工序→产品

### 7.2 SN状态

- IN_PRODUCTION: 生产中
- COMPLETED: 已完成
- SCRAPPED: 已报废
- SHIPPED: 已发货

---

## 八、质量管理

### 8.1 三检制

- IQC(来料检验): 采购物料入库前检验
- IPQC(过程检验): 生产过程中抽检
- OQC(出货检验): 成品出货前检验

### 8.2 不良品处理

不良品信息直接记录在检验表的 `defect_info` JSON字段中，包含：
- 不良类型(SCRATCH/CRACK/DIMENSION/OTHER)
- 不良数量
- 处置方式(SCRAP/REWORK/ACCEPT/RETURN)
- 不良描述

### 8.3 IQC与ERP联动

```
IQC检验完成(COMPLETED) → MQ消息通知ERP → ERP更新收货单质检状态
  → QUALIFIED: 允许入库
  → UNQUALIFIED: 触发不良品处理流程
```

### 8.4 SPC统计过程控制

控制图类型：
- X_BAR_R: 均值-极差图
- X_BAR_S: 均值-标准差图
- P: 不良率图
- C: 缺陷数图
- U: 单位缺陷数图

OOC(超控制限)规则（硬编码）：
- 规则1: 1点超出控制限
- 规则2: 连续9点在中心线同侧
- 规则3: 连续6点递增或递减
- 规则4: 连续14点上下交替

---

## 九、设备管理

### 9.1 设备台账

- 设备基本信息(编码/名称/型号/制造商)
- 设备状态(正常/保养中/故障/报废)
- 点检项目及标准存在 `inspection_items` JSON字段中
- 最近一次点检结果存在 `last_inspection` JSON字段中
- `iot_device_id` 预留关联IoT设备，不建外键

### 9.2 保养计划

- 保养类型: 日/周/月/季/年
- 保养周期(天)
- 下次保养日期自动计算

### 9.3 维修管理

- 故障报修→维修→完成
- 记录停机时长和维修成本

---

## 十、MES实施计划

> **⚠️ 实际交付进度**: Phase 1-3 全部完成（实际 Week 17-27），领先原计划约 5 周。详见 §十九。
> 下表保留原计划列作对照，实际列标注各模块实际完成周次。

### 10.1 MES Phase 1 (计划 Week 17-21 / 实际 Week 17-21): 基础框架 + 主数据 + 工单

| 周次 | 任务 | 人天 | 实际周次 | 实际状态 |
|------|------|------|---------|---------|
| Week 17 | 创建ruoyi-mes模块+数据源配置+基础框架+公共组件 | 3 | Week 17 | ✅ 已完成 |
| Week 18 | 主数据跨库查询(MesErpBridgeService)+前端只读页面 | 3 | Week 18 | ✅ 已完成 |
| Week 19-20 | MES工单+工单工序+Flowable审批流集成+ERP→MES MQ联动 | 5 | Week 19-20 | ✅ 已完成 |
| Week 21 | Phase 1前端页面(工单列表/详情/工序Tab/主数据只读)+菜单权限 | 3 | Week 21 | ✅ 已完成 |

**Phase 1 小计: 14人天 — ✅ 已全部交付**

### 10.2 MES Phase 2 (计划 Week 22-27 / 实际 Week 20-24): 流转卡 + 工序执行 + 安灯 + SN

| 周次 | 任务 | 人天 | 实际周次 | 实际状态 |
|------|------|------|---------|---------|
| Week 22-23 | 流转卡引擎(状态机+服务+自动生成工序) | 5 | Week 20 | ✅ 已完成（领先2周） |
| Week 24-25 | 派工+工序执行(扫码开工/完工/数量记录) | 4 | Week 22 | ✅ 已完成（领先2周） |
| Week 26 | 安灯系统(CRUD+SSE实时推送看板+IoT联动) | 3 | Week 23 | ✅ 已完成 |
| Week 27 | SN追溯体系+Phase 2前端页面 | 3 | Week 24 | ✅ 已完成 |

**Phase 2 小计: 15人天 — ✅ 已全部交付**

### 10.3 MES Phase 3 (计划 Week 28-32 / 实际 Week 25-27): 质量 + 设备 + 数据采集 + SPC

| 周次 | 任务 | 人天 | 实际周次 | 实际状态 |
|------|------|------|---------|---------|
| Week 28-29 | 质量管理(IQC/IPQC/OQC+ERP联动) | 5 | Week 25 | ✅ 已完成（领先3周） |
| Week 30 | 设备管理(台账+保养+维修) | 3 | Week 26 | ✅ 已完成 |
| Week 31-32 | 数据采集+SPC+Phase 3前端页面+集成测试 | 4 | Week 27 | ✅ 已完成 |

**Phase 3 小计: 12人天 — ✅ 已全部交付**

### 10.4 工作量汇总

| 阶段 | 计划周次 | 实际周次 | 人天 | 状态 |
|------|---------|---------|------|------|
| Phase 1 | Week 17-21 | Week 17-21 | 14 | ✅ |
| Phase 2 | Week 22-27 | Week 20-24 | 15 | ✅ |
| Phase 3 | Week 28-32 | Week 25-27 | 12 | ✅ |
| **合计** | **Week 17-32 (16周)** | **Week 17-27 (11周)** | **~41** | **✅ 全部交付** |

> **交付效率**: 原计划 41 人天 / 16 周 → 实际 41 人天 / 11 周（提前 5 周完成，节约 31% 周期）
> **归档计划文档**: `doc/mes/plans/2026-06-{16~25}-mes-week{17~27}*.md`

---

## 十一、MES前端规划

### 11.1 目录结构

```
plus-ui/src/views/mes/
├── md/                        # 主数据(只读,调ERP API)
│   ├── material/readonly.vue  # 物料只读查看
│   ├── bom/readonly.vue       # BOM只读查看
│   └── routing/readonly.vue   # 工艺路线只读查看
├── pro/                       # 生产执行
│   ├── workorder/             # MES工单
│   │   ├── index.vue          # 工单列表(含审批状态筛选)
│   │   └── form.vue           # 工单详情(含工序列表Tab)
│   ├── flow-card/             # 流转卡
│   │   ├── index.vue          # 流转卡列表
│   │   └── form.vue           # 流转卡详情(含工序进度)
│   ├── dispatch/index.vue     # 派工管理
│   ├── andon/index.vue        # 安灯看板(SSE实时刷新)
│   └── sn/                    # SN追溯
│       ├── index.vue          # SN查询
│       └── trace.vue          # 追溯树
├── qc/                        # 质量管理
│   ├── iqc/index.vue          # 来料检验
│   ├── ipqc/index.vue         # 过程检验
│   └── oqc/index.vue          # 出货检验
├── eq/                        # 设备管理
│   ├── equipment/index.vue    # 设备台账
│   ├── maintenance/index.vue  # 保养计划
│   └── repair/index.vue       # 维修记录
├── dc/                        # 数据采集
│   ├── collection-point/index.vue  # 采集点
│   └── data-record/index.vue       # 数据记录
└── spc/                       # SPC
    └── control-chart/index.vue     # 控制图(ECharts)
```

### 11.2 页面估算

| 模块 | 页面数 |
|------|--------|
| md(只读) | 3 |
| pro | 7 |
| qc | 3 |
| eq | 3 |
| dc | 2 |
| spc | 1 |
| **合计** | **~19页面** |

### 11.3 关键前端组件

- 流转卡看板: 实时展示流转卡状态和工序进度
- 安灯看板: SSE实时刷新安灯呼叫状态，颜色编码区分呼叫类型
- SPC控制图: ECharts绘制X-Bar/R控制图，自动标注OOC点
- SN追溯树: 树形展示SN→工序→物料的追溯关系

---

## 十二、ERP与MES边界划分

### 12.1 九条划分原则

| 序号 | 原则 | 说明 |
|------|------|------|
| 1 | 主数据统一ERP | 物料/BOM/客户/供应商/工厂/仓库/库位/工序/路由在ERP维护，MES跨库只读 |
| 2 | 质量管理统一MES | IQC/IPQC/OQC在MES管理 |
| 3 | 设备管理统一MES | 设备台账/保养/维修在MES管理 |
| 4 | 库存统一wm_material_stock | ERP和MES共用同一库存表，通过@DS("erp")访问 |
| 5 | 工单创建在ERP，执行在MES | ERP创建工单→MQ→MES创建MES工单(含工序/流转卡) |
| 6 | 采购/销售只在ERP | 采购和销售业务不涉及MES |
| 7 | 数据采集分层 | MES负责手工/业务采集（质检、工序录入），IoT负责自动设备采集（MQTT/传感器），SPC消费MES归档结果 |
| 8 | 排班调度在ERP | 班次/班组/排班计划在ERP维护 |
| 9 | 工艺路线在ERP维护，MES执行 | ERP维护工艺路线→MES按工序执行 |

### 12.2 模块归属对照表

| 功能 | ERP模块 | MES模块 | 说明 |
|------|---------|---------|------|
| 物料主数据 | bd | 跨库只读 | ERP维护，MES通过@DS("erp")查询 |
| BOM | bd | 跨库只读 | ERP维护 |
| 工艺路线 | bd | 跨库只读 | ERP维护，MES执行 |
| 工单 | prd(创建) | pro(执行) | ERP创建→MQ→MES执行 |
| 库存 | wm | 共用wm_material_stock | 通过@DS("erp")访问 |
| 采购 | pu | - | 仅ERP |
| 销售 | so | - | 仅ERP |
| 质量管理 | - | qc | 仅MES |
| 设备管理 | - | eq | 仅MES |
| 数据采集 | - | dc | 仅MES |
| SPC | - | spc | 仅MES |

### 12.3 ERP↔MES集成点

| 方向 | 触发点 | 方式 | 说明 | 状态 |
|------|--------|------|------|------|
| ERP→MES | 工单下达 | MQ (RabbitMQ) | PrdWorkOrderServiceImpl审批通过后发消息 | ✅ Week 16已实现 |
| ERP→MES | 主数据查询 | 跨库直查 | MES Service切@DS("erp")读物料/BOM/工艺 | ✅ bd模块Controller已完整 |
| MES→ERP | 工单完工 | MQ (RabbitMQ) | 通知ERP更新工单状态+完工入库 | ✅ Week 25已实现(ErpWorkOrderCompletedConsumer) |
| MES→ERP | IQC检验完成 | MQ (RabbitMQ) | 通知ERP更新收货单质检状态 | ✅ Week 25已实现(ErpIqcCompletedConsumer) |

> **Week 25 修复**: MES→ERP MQ producer `sendWorkOrderCompleted` 原发 `DIRECT_EXCHANGE`（与队列绑定 `TOPIC_EXCHANGE` 不匹配，消息无法路由），已修正为 `TOPIC_EXCHANGE`。
> DTO `IqcCompletedMessage`/`MesWorkOrderCompletedMessage` 已从 `ruoyi-mes` 迁移至 `ruoyi-common-mq`，供 ERP 消费端引用。

### 12.4 MES↔IoT集成点

| 方向 | 方式 | 说明 | 状态 |
|------|------|------|------|
| MES查设备数据 | REST API / Redis缓存 | MES查询IoT设备最新数据（IotDeviceDataQueryService） | ⏳ 契约就绪(common-iot)，MES侧待调用 |
| IoT告警→MES安灯 | 接口抽象 | `IMesAndonService.createAndonFromAlert()` 接口 | ✅ Week 23已上线(MesAndonServiceImpl @Primary + IotAlertNotifier) |
| 设备关联 | 字段预留 | `eq_equipment.iot_device_id` + `iot_device.mes_equipment_id` | ⏳ 双向字段已建(Week 26 eq + IoT阶段一)，双向查询未打通 |

> MES与IoT之间不建外键，通过接口抽象(`IMesAndonService`/`IotDeviceDataQueryService`)解耦。两个模块可独立开发和部署。

---

## 十三、开发前置条件与检查清单

> **关键原则**: 以下每一项都是 MES Phase 1 开始编码前必须完成的前置条件。

### 13.1 基础设施检查清单

| 检查项 | 当前状态 | 说明 |
|--------|----------|------|
| `ruoyi-mes` Maven模块已创建 | ✅ Week 17完成 | 模块可编译 |
| `ruoyi-modules/pom.xml` 注册 mes 模块 | ✅ Week 17完成 | `<module>ruoyi-mes</module>` |
| 根 `pom.xml` 注册 mes 模块 | ✅ Week 17完成 | `<module>ruoyi-mes</module>` |
| `application-dev.yml` 配置 mes 数据源 | ✅ Week 17完成 | `spring.datasource.dynamic.datasource.mes` |
| `@DS("mes")` 注解可用 | ✅ Week 17完成 | DynamicDataSource 切换正常 |
| `ry-mes` 数据库已创建 | ✅ Week 17完成 | `CREATE DATABASE ry-mes` |
| ~17张DDL已执行 | ✅ Week 17-27分批执行 | pro×4+flow×2+dispatch/andon/sn+qc×3+eq×3+dc×2+spc×2=20张 |
| ERP MQ占位消费者已处理 | ✅ Week 16完成 | `@ConditionalOnProperty` 配置开关禁用占位 |
| 工单下达消息发送时序已修正 | ✅ Week 19完成 | `TransactionSynchronization.afterCommit()` |

### 13.2 ERP侧依赖确认

| 依赖项 | 来源 | 状态 | 说明 |
|--------|------|------|------|
| 物料API | `/erp/bd/material/list` | ✅ 已存在 | MES跨库只读 |
| BOM API | `/erp/bd/bom/list` | ✅ 已存在 | MES跨库只读 |
| 工艺路线API | ERP工艺路线Controller | ✅ 已存在 | MES跨库只读 |
| 工序API | ERP工序Controller | ✅ 已存在 | MES跨库只读 |
| 工作中心API | ERP工作中心Controller | ✅ 已存在 | MES跨库只读 |
| 工单下达MQ消息 | `PrdWorkOrderServiceImpl.afterFlowHandle()` | ✅ 已实现 | 发送端已完成 |
| MQ队列常量 | `MesMqConstants` | ✅ 已存在 | `WORK_ORDER_RELEASE_QUEUE` 等 |
| Flowable审批流 | `ruoyi-flowable` (74个Java文件) | ✅ 已存在 | MES复用ERP已有模式 |
| SSE基础设施 | `ruoyi-common-sse` | ✅ 已存在 | 安灯看板使用 |
| SnailJob | `ruoyi-common-job` | ✅ 已存在 | 定时任务使用 |
| Redisson | `ruoyi-common-redis` | ✅ 已存在 | 分布式锁/缓存使用 |

---

## 十四、接口与消息契约清单

### 14.1 ERP→MES 消息契约

| 消息 | Exchange | RoutingKey | Queue | 消息体 | 生产者 | 消费者 |
|------|----------|------------|-------|--------|--------|--------|
| 工单下达 | `erp.direct` | `mes.workorder.release` | `mes.workorder.queue` | `WorkOrderReleaseMessage` | `ERP PrdWorkOrderServiceImpl` | `MES MesWorkOrderSyncConsumer` |

**WorkOrderReleaseMessage 字段：**
```java
public class WorkOrderReleaseMessage implements Serializable {
    private Long erpWoId;        // ERP工单ID
    private String woNo;         // 工单编号
    private Long materialId;     // 物料ID
    private String materialCode; // 物料编码
    private String materialName; // 物料名称
    private String materialSpec; // 规格型号
    private BigDecimal quantity; // 计划数量
    private Long routingId;      // 工艺路线ID
    private Long bomId;          // BOM ID
    private Long tenantId;       // 租户ID
    private Long operatorId;     // 操作人ID
}
```

### 14.2 MES→ERP 消息契约

| 消息 | Exchange | RoutingKey | Queue | 消息体 | 生产者 | 消费者 |
|------|----------|------------|-------|--------|--------|--------|
| 工单完工 | `mes.direct` | `erp.workorder.completed` | `erp.workorder.completed.queue` | `WorkOrderCompletedMessage` | `MES ProWorkOrderServiceImpl` | `ERP ErpWorkOrderCompletedConsumer` |
| IQC检验完成 | `mes.direct` | `erp.iqc.completed` | `erp.iqc.completed.queue` | `IqcCompletedMessage` | `MES QcIqcServiceImpl` | `ERP ErpIqcCompletedConsumer` |

**WorkOrderCompletedMessage 字段：**
```java
public class WorkOrderCompletedMessage implements Serializable {
    private Long mesWoId;        // MES工单ID
    private Long erpWoId;        // ERP工单ID
    private String woNo;         // 工单编号
    private BigDecimal completedQty; // 完工数量
    private BigDecimal scrappedQty;  // 报废数量
    private Long tenantId;
}
```

**IqcCompletedMessage 字段：**
```java
public class IqcCompletedMessage implements Serializable {
    private Long iqcId;          // IQC检验ID
    private Long receiveId;      // ERP收货单ID
    private String inspectResult;// QUALIFIED/UNQUALIFIED
    private BigDecimal quantity; // 检验数量
    private Long tenantId;
}
```

### 14.3 MES→ERP REST接口（跨库桥接）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 查询物料 | GET | `/erp/bd/material/{id}` | MES前端直接调用ERP API |
| 查询BOM明细 | GET | `/erp/bd/bom-line/list?bomId=` | MES前端直接调用ERP API |
| 查询工艺路线 | GET | `/erp/bd/routing/{id}` | MES前端直接调用ERP API |
| 查询工序 | GET | `/erp/bd/process/list` | MES前端直接调用ERP API |
| 查询工作中心 | GET | `/erp/bd/workcenter/list` | MES前端直接调用ERP API |

### 14.4 MES内部REST接口清单

| 模块 | 接口前缀 | 方法示例 | 说明 |
|------|----------|----------|------|
| MES工单 | `/mes/pro/workorder` | list/get/add/edit/del/submit/approve | 含Flowable审批 |
| 流转卡 | `/mes/pro/flow-card` | list/get/create/release/startStep/completeStep/completeCard | 状态机驱动 |
| 派工 | `/mes/pro/dispatch` | list/get/add/edit/del/start/complete | - |
| 安灯 | `/mes/pro/andon` | list/get/call/respond/resolve | SSE实时推送 |
| SN | `/mes/pro/sn` | list/get/add/trace | 追溯树查询 |
| IQC | `/mes/qc/iqc` | list/get/add/inspect/submit | 含ERP回传 |
| IPQC | `/mes/qc/ipqc` | list/get/add/inspect | - |
| OQC | `/mes/qc/oqc` | list/get/add/inspect | - |
| 设备 | `/mes/eq/equipment` | list/get/add/edit/del | 含点检 |
| 保养 | `/mes/eq/maintenance-plan` | list/get/add/edit | - |
| 维修 | `/mes/eq/repair` | list/get/add/start/complete | - |
| 采集点 | `/mes/dc/collection-point` | list/get/add/edit | - |
| 采集记录 | `/mes/dc/data-record` | list/get/add | - |
| SPC控制图 | `/mes/spc/control-chart` | list/get/add/edit/analyze | ECharts数据 |

### 14.5 MES↔IoT接口契约

| 方向 | 接口 | 方法 | 说明 | 状态 |
|------|------|------|------|------|
| MES→IoT | 查询设备最新数据 | GET `/iot/device/{deviceId}/latest` | 从Redis缓存读取 | ⏳ 契约就绪，MES侧未调用 |
| MES→IoT | 查询设备在线状态 | GET `/iot/device/{deviceId}/status` | 从Redis缓存读取 | ⏳ 契约就绪，MES侧未调用 |
| MES→IoT | 查询设备历史数据 | GET `/iot/device/{deviceId}/data?start=&end=` | MySQL查询 | ⏳ 契约就绪，MES侧未调用 |
| IoT→MES | 告警触发安灯 | `IMesAndonService.createAndonFromAlert(alert)` | 接口抽象，IoT调用MES | ✅ Week 23已上线 |

---

## 十五、详细实施任务表

### 15.1 MES Phase 1 详细任务（Week 17-21）

#### Week 17：模块骨架 + 数据源（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 创建 `ruoyi-mes` Maven模块 | pom.xml + 目录结构 | 模块可编译，无报错 |
| 注册到 `ruoyi-modules/pom.xml` 和根 `pom.xml` | `<module>ruoyi-mes</module>` | `mvn compile` 通过 |
| 配置 `application-dev.yml` mes 数据源 | `spring.datasource.dynamic.datasource.mes` | 启动无报错 |
| 创建 `ry-mes` 数据库 | `CREATE DATABASE ry-mes` | 可连接 |
| 执行 Phase 1 DDL | pro_work_order + pro_work_order_step | 表结构正确 |
| 创建公共包结构 | `com/nexus/mes/{pro,wm,qc,eq,dc,spc,common}` | 目录存在 |
| 创建 `MesErpBridgeService` 接口 + 实现 | `IMesErpBridgeService.java` | 跨库查询可返回数据 |
| 暂时禁用 ERP 侧 `WorkOrderReleaseConsumer` | 配置开关或注释 | 不会误消费消息 |

#### Week 18：主数据桥接 + 只读页面（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现 `MesErpBridgeService` 物料查询 | `getMaterial(Long materialId)` | 返回ERP物料数据 |
| 实现 `MesErpBridgeService` BOM查询 | `getBomLines(Long bomId)` | 返回ERP BOM明细 |
| 实现 `MesErpBridgeService` 工艺路线查询 | `getRoutingSteps(Long routingId)` | 返回ERP工艺步骤 |
| 创建 MES 工单 Entity/Mapper/Service/Controller 骨架 | 完整CRUD骨架 | 可增删改查（空数据） |
| 前端：物料只读页面 | `mes/md/material/readonly.vue` | 调ERP API显示物料 |
| 前端：BOM只读页面 | `mes/md/bom/readonly.vue` | 调ERP API显示BOM |
| 前端：工艺路线只读页面 | `mes/md/routing/readonly.vue` | 调ERP API显示工艺路线 |

#### Week 19-20：MES工单 + Flowable审批 + MQ联动（5人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现 `MesWorkOrderSyncConsumer` | 消费 `mes.workorder.queue` | ERP工单下达后自动创建MES工单 |
| 实现工单工序自动生成 | 根据工艺路线创建 `pro_work_order_step` | 工单有完整工序列表 |
| 修正工单下达消息发送时序 | 事务提交后发送 | 消息发送时ERP工单状态已更新 |
| 集成 Flowable 审批流 | `submit()` + `afterFlowHandle()` | MES工单可提交审批 |
| 审批通过后工单状态→RELEASED | 状态联动 | 审批通过自动变更状态 |
| 前端：审批流选择器 + 提交按钮 | 工单详情页审批区 | 可选审批流、提交、查看流程 |
| 执行 Phase 2 DDL（流转卡相关） | pro_flow_card + pro_flow_card_step | 表结构正确 |

#### Week 21：Phase 1 前端 + 菜单权限（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 前端：MES工单列表页 | `mes/pro/workorder/index.vue` | 含审批状态筛选、分页 |
| 前端：MES工单详情页 | `mes/pro/workorder/form.vue` | 含工序列表Tab |
| 配置菜单与权限 | sys_menu 数据 | MES菜单可见、权限控制生效 |
| Phase 1 集成测试 | 测试报告 | ERP下达→MES工单→审批→RELEASED 全链路 |

---

### 15.2 MES Phase 2 详细任务（Week 22-27）

#### Week 22-23：流转卡引擎（5人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现流转卡状态机 | `IMesFlowCardService` + 实现类 | 状态流转正确 |
| 创建流转卡（从MES工单） | `createFlowCard(woId, quantity)` | 自动生成工序步骤记录 |
| 下达流转卡 | `releaseCard(cardId)` | CREATED→RELEASED |
| 开始/完成工序 | `startStep()` / `completeStep()` | 流转卡自动流转到下一道工序 |
| 完成/关闭/取消流转卡 | `completeCard()` / `closeCard()` / `cancelCard()` | 状态正确联动 |
| 工单完工聚合 | 所有流转卡完成→工单COMPLETED | 数量汇总正确 |
| 前端：流转卡列表 | `mes/pro/flow-card/index.vue` | 含状态筛选 |
| 前端：流转卡详情 | `mes/pro/flow-card/form.vue` | 含工序进度展示 |

#### Week 24-25：派工 + 工序执行（4人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现派工CRUD | `ProDispatchServiceImpl` | 可创建/编辑/查询派工单 |
| 实现派工状态流转 | PENDING→IN_PROGRESS→COMPLETED | 状态联动正确 |
| 实现扫码开工/完工 | 扫码接口 + 数量记录 | 支持条码扫描操作 |
| 良品/不良/报废数量记录 | `completeStep()` 参数校验 | 数量逻辑正确 |
| 前端：派工管理页 | `mes/pro/dispatch/index.vue` | 含操作员/工作中心筛选 |
| 执行 Phase 2 剩余 DDL | pro_dispatch + pro_andon + pro_sn | 表结构正确 |

#### Week 26：安灯系统（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现安灯CRUD | `ProAndonServiceImpl` | 可呼叫/响应/解决 |
| 实现SSE实时推送 | `SseMessageUtils.publishMessage()` | 安灯状态变更实时推送前端 |
| 实现 `IMesAndonService` 接口 | 接口定义 + 基础实现 | IoT可通过接口触发安灯 |
| 前端：安灯看板 | `mes/pro/andon/index.vue` | SSE实时刷新，颜色编码区分类型 |

#### Week 27：SN追溯 + Phase 2 收尾（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现SN CRUD | `ProSnServiceImpl` | 可创建/查询SN |
| 实现SN父子关系 | `parent_sn_id` 关联 | 组装追溯正确 |
| 实现SN状态流转 | IN_PRODUCTION→COMPLETED→SCRAPPED→SHIPPED | 状态联动正确 |
| 实现追溯树查询 | 前向/后向追溯API | 返回完整追溯链 |
| 前端：SN查询页 | `mes/pro/sn/index.vue` | 含状态筛选 |
| 前端：追溯树 | `mes/pro/sn/trace.vue` | 树形展示追溯关系 |
| Phase 2 集成测试 | 测试报告 | 工单→流转卡→派工→工序→安灯→SN 全链路 |

---

### 15.3 MES Phase 3 详细任务（计划 Week 28-32 / 实际 Week 25-27）

> Phase 3 提前 5 周完成，各模块任务表保留原计划作对照，实际交付物详见 `doc/mes/plans/` 归档。

#### Week 28-29：质量管理 → 实际 Week 25 ✅（5人天）

| 任务 | 交付物 | 实际交付物 |
|------|--------|----------|
| 实现IQC CRUD + 检验 | `QcIqcServiceImpl` | ✅ `com.nexus.mes.qc.service.impl.QcIqcServiceImpl` |
| 实现IQC→ERP MQ回传 | `IqcCompletedMessage` 生产者 | ✅ `MesMqSendService.sendIqcCompleted` + DTO迁移至 common-mq |
| ERP侧实现IQC消费端 | `ErpIqcCompletedConsumer` | ✅ `com.nexus.erp.mq.consumer.ErpIqcCompletedConsumer` |
| 实现IPQC CRUD + 检验 | `QcIpqcServiceImpl` | ✅ 含不良率自动计算(defectQty/sampleQty*100) |
| 实现OQC CRUD + 检验 | `QcOqcServiceImpl` | ✅ |
| 实现MES→ERP完工MQ回传 | `WorkOrderCompletedMessage` 生产者 | ✅ 已在Phase2 ProWorkOrderServiceImpl.completeWo afterCommit触发 |
| ERP侧实现完工消费端 | `ErpWorkOrderCompletedConsumer` | ✅ `com.nexus.erp.mq.consumer.ErpWorkOrderCompletedConsumer` + 修复exchange bug(DIRECT→TOPIC) |
| 前端：IQC/IPQC/OQC页面 | 3个列表+检验弹窗 | ✅ `plus-ui/src/views/mes/qc/{iqc,ipqc,oqc}/index.vue` |
| 执行 Phase 3 DDL | qc_iqc + qc_ipqc + qc_oqc | ✅ `doc/mes/sql/mes-week25-06-quality.sql` |

#### Week 30：设备管理 → 实际 Week 26 ✅（3人天）

| 任务 | 交付物 | 实际交付物 |
|------|--------|----------|
| 实现设备台账CRUD | `EqEquipmentServiceImpl` | ✅ 含设备状态联动维修(FAULT/NORMAL) |
| 实现保养计划CRUD | `EqMaintenancePlanServiceImpl` | ✅ 含下次保养日期自动计算(today+cycleDays) |
| 实现维修记录CRUD | `EqRepairServiceImpl` | ✅ REPORTED→REPAIRING→COMPLETED 状态机 |
| 前端：设备台账页 | `mes/eq/equipment/index.vue` | ✅ |
| 前端：保养计划页 | `mes/eq/maintenance/index.vue` | ✅ 过期标红 |
| 前端：维修记录页 | `mes/eq/repair/index.vue` | ✅ 开工/完成操作按钮 |
| 执行 Phase 3 DDL | eq_equipment + eq_maintenance_plan + eq_repair | ✅ `doc/mes/sql/mes-week26-07-equipment.sql` |

#### Week 31-32：数据采集 + SPC + 集成测试 → 实际 Week 27 ✅（4人天）

| 任务 | 交付物 | 实际交付物 |
|------|--------|----------|
| 实现采集点CRUD | `DcCollectionPointServiceImpl` | ✅ point_type MANUAL/AUTO 预留 |
| 实现采集记录CRUD | `DcDataRecordServiceImpl` | ✅ source MANUAL/IoT 预留，仅手动录入 |
| 实现SPC控制图 | `SpcControlChartServiceImpl` | ✅ 5种图表(X_BAR_R/X_BAR_S/P/C/U) + A2/A3系数表 |
| 实现OOC规则判定 | 硬编码4条规则 | ✅ RULE1(超限)/RULE2(9点同侧)/RULE3(6点递增递减)/RULE4(14点交替) |
| 前端：采集点页面 | `mes/dc/collection-point/index.vue` | ✅ |
| 前端：采集记录页面 | `mes/dc/data-record/index.vue` | ✅ |
| 前端：SPC控制图 | `mes/spc/control-chart/index.vue` | ✅ ECharts UCL/CL/LCL参考线+OOC红点 |
| 执行 Phase 3 DDL | dc/spc 四表 | ✅ `doc/mes/sql/mes-week27-08-collection-spc.sql` |
| Phase 3 集成测试 | 测试报告 | ⏳ 待人工运行时验证 |
| MES全链路集成测试 | 测试报告 | ⏳ 待人工运行时验证 |

---

## 十六、阶段验收标准

### 16.1 Phase 1 验收标准

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| 模块启动 | `ruoyi-mes` 模块可独立启动，无报错 | 启动日志无ERROR |
| 数据源切换 | `@DS("mes")` 和 `@DS("erp")` 可正常切换 | 跨库查询返回正确数据 |
| ERP工单下达联动 | ERP审批工单→MQ→MES自动创建工单+工序 | 端到端测试 |
| MES工单审批 | MES工单可提交Flowable审批→通过→RELEASED | 审批流程测试 |
| 主数据只读 | MES页面可查看ERP物料/BOM/工艺路线 | 页面展示正确 |
| 菜单权限 | MES菜单可见，权限控制生效 | 不同角色权限测试 |

### 16.2 Phase 2 验收标准

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| 流转卡全生命周期 | 创建→下达→开工→完工→关闭 流转正确 | 状态机测试 |
| 工序自动流转 | 完成当前工序→自动流转到下一道工序 | 流转卡工序测试 |
| 派工执行 | 可派工→开工→完工→数量记录 | 派工流程测试 |
| 安灯实时推送 | 安灯呼叫→SSE实时推送到前端看板 | SSE连接测试 |
| SN追溯 | 创建SN→父子关系→前向/后向追溯树 | 追溯查询测试 |
| 工单完工聚合 | 所有流转卡完成→工单COMPLETED | 聚合逻辑测试 |

### 16.3 Phase 3 验收标准

| 验收项 | 验收标准 | 验证方法 | 状态 |
|--------|----------|----------|------|
| IQC检验+回传 | IQC完成→MQ→ERP更新收货单状态 | 端到端测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| IPQC/OQC检验 | 过程检验/出货检验可录入并查询 | 检验流程测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| 设备台账 | 设备可增删改查，含点检项目 | CRUD测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| 保养计划 | 保养计划可创建，下次保养日期自动计算 | 计划逻辑测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| 维修记录 | 故障报修→维修→完成，记录停机时长 | 维修流程测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| SPC控制图 | 控制图可创建，OOC点自动标注 | ECharts展示测试 | ✅ 代码就绪，⏳ 运行时待验证 |
| MES全链路 | ERP→MES工单→流转卡→派工→质检→完工→ERP | 全链路集成测试 | ✅ 代码就绪，⏳ 运行时待验证 |

> 代码交付全部完成（Week 17-27），运行时集成测试待人工执行四库+Redis+RabbitMQ 联调验证。

---

## 十七、联调与测试策略

### 17.1 测试层次

| 层次 | 说明 | 工具 |
|------|------|------|
| 单元测试 | Service层逻辑测试 | JUnit + Mockito |
| 接口测试 | REST API测试 | Postman / Swagger |
| 集成测试 | 跨模块联动测试 | 手动 + 自动化脚本 |
| MQ消息测试 | 消息发送/消费正确性 | RabbitMQ Management Console |
| Flowable测试 | 审批流正确性 | Flowable Actuator |
| SSE测试 | 实时推送正确性 | 浏览器EventSource |

### 17.2 联调顺序

```
Phase 1 联调:
  ERP工单审批 → MQ消息 → MES工单创建 → MES工单审批 → RELEASED
  MES页面 → ERP主数据API → 物料/BOM/工艺路线显示

Phase 2 联调:
  MES工单 → 流转卡创建 → 派工 → 工序执行 → 安灯 → SN
  流转卡完工 → MES工单完工聚合

Phase 3 联调:
  IQC检验 → MQ → ERP收货单状态更新
  MES工单完工 → MQ → ERP工单状态+完工入库
  设备台账 → 保养/维修
  采集点 → 采集数据 → SPC控制图
```

### 17.3 测试数据准备

| 数据 | 来源 | 说明 |
|------|------|------|
| 物料 | ERP bd_material | 至少5条测试物料 |
| BOM | ERP bd_bom + bd_bom_line | 至少2条BOM |
| 工艺路线 | ERP bd_routing + bd_routing_step | 至少2条工艺路线（含3+步骤） |
| 工单 | ERP prd_work_order | 至少3条测试工单（不同状态） |
| 审批流 | Flowable wf_deploy_info | 至少1个MES工单审批流 |

---

## 十八、ERP→MES占位消费处理方案

### 18.1 问题说明

当前 `WorkOrderReleaseConsumer`（位于 `ruoyi-erp` 模块内）是占位实现，仅打印日志。如果 `ruoyi-mes` 创建后直接部署，已发送到 `mes.workorder.queue` 的消息会被占位消费者"吃掉"，导致 MES 无法接收到这些工单下达消息。

### 18.2 处理方案

**推荐方案：配置开关禁用占位消费者**

```yaml
# application-dev.yml
mes:
  consumer:
    enabled: false  # 禁用ERP侧占位消费者，由MES侧消费者接管
```

```java
@Component
@ConditionalOnProperty(name = "mes.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class WorkOrderReleaseConsumer extends BaseMessageConsumer {
    // 当 mes.consumer.enabled=false 时不注册此消费者
}
```

**备选方案：直接删除占位消费者**

在 `ruoyi-mes` 模块创建并部署后，直接删除 `ruoyi-erp` 中的 `WorkOrderReleaseConsumer`，由 MES 侧的 `MesWorkOrderSyncConsumer` 接管。

### 18.3 工单下达消息发送时序修正

**当前问题**: `afterFlowHandle()` 中先发送MQ消息，再更新工单状态为APPROVED。如果消息发送成功但后续状态更新失败，MES会收到一条ERP侧未成功审批的工单下达消息。

**修正方案**: 改为事务提交后发送消息。

```java
// 方案1: 使用 TransactionSynchronizationManager
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        reliableMessageProducer.sendMessage(MesMqConstants.WORK_ORDER_RELEASE_EXCHANGE,
            MesMqConstants.WORK_ORDER_RELEASE_ROUTING_KEY, message);
    }
});

// 方案2: 使用本地消息表(outbox pattern)
// 将消息写入本地消息表，由定时任务扫描发送
```

---

## 十九、实施进度更新（截至 2026-06-25）

> **结论**: MES Phase 1-3 全部交付（Week 17-27，领先原计划约 5 周），代码交付 100% 完成，运行时集成测试待人工。

### 19.1 实际交付周次表

| 原计划周次 | 实际周次 | 模块 | 交付物 | 验证 |
|-----------|---------|------|--------|------|
| Week 17 | Week 17 | 模块骨架+数据源 | ruoyi-mes pom+配置+MesErpBridgeService | mvn compile ✅ |
| Week 18 | Week 18 | 主数据桥接 | ErpMaterialMapper/BomLine/RoutingStep + 前端只读 | eslint ✅ |
| Week 19-20 | Week 19-20 | MES工单+MQ+Flowable | ProWorkOrder + WorkOrderReleaseConsumer + 审批流 | mvn compile ✅ |
| Week 21 | Week 21 | Phase 1 前端收尾 | workorder/index+form + 菜单4001-4032 | eslint ✅ |
| Week 22-23 | **Week 20** | 流转卡引擎 | ProFlowCard + 状态机+自动生成工序 | mvn compile ✅ |
| Week 24-25 | **Week 22** | 派工+工序执行 | ProDispatch + 状态机+扫码+Lock4j | mvn compile ✅ |
| Week 26 | **Week 23** | 安灯系统 | ProAndon + SSE + MesAndonServiceImpl(IoT联动) | mvn compile ✅ |
| Week 27 | **Week 24** | SN追溯 | ProSn + 追溯树 + 组装父子关系 | mvn compile ✅ |
| Week 28-29 | **Week 25** | 质量管理+MQ贯通 | QcIqc/Ipqc/Oqc + DTO迁移 + ErpIqcCompletedConsumer + ErpWorkOrderCompletedConsumer + exchange bug修复 | mvn compile ✅ |
| Week 30 | **Week 26** | 设备管理 | EqEquipment/MaintenancePlan/Repair + 状态联动 | mvn compile ✅ |
| Week 31-32 | **Week 27** | 数据采集+SPC | DcCollectionPoint/DataRecord + SpcControlChart/DataPoint + 5种控制图 + 4条OOC规则 + ECharts | mvn compile ✅ |
| Week 28（后续扩展） | Week 28 | MBOM 子系统扩展 | mes_mbom/mes_mbom_line/pro_work_order_bom_line 三表 + 菜单 2950-2954 | SQL 就绪 ✅ |

### 19.2 关键架构决策汇总

| 决策 | 周次 | 选择 | 原因 |
|------|------|------|------|
| MES 不建主数据表 | Week 17 | 跨库查询 ERP（`@DS("erp")`） | 避免双写 |
| 事务提交后发 MQ | Week 19 | `TransactionSynchronization.afterCommit()` | 避免消息先于状态持久化 |
| SN 生成操作员触发 | Week 24 | 不自动耦合 completeCard | 避免 FlowCard→Sn 循环依赖 |
| 安灯 SSE 推送 | Week 23 | `SseMessageUtils.publishMessage` + `collectOnlineUsersByTenant` | 镜像 IotAlertNotifier 模式 |
| IQC 不合格仅标记 | Week 25 | ERP 收货单置 REJECTED，不实现不良品入库 | 避免 Week 25 范围膨胀 |
| 设备状态联动维修 | Week 26 | 报修复 FAULT，完成恢复 NORMAL（活跃报修为 0 时） | 周末信息一致 |
| 仅手动采集 | Week 27 | dc_data_record 仅 MANUAL，AUTO 预留 | IoT 归档为后续增强 |
| 全五种控制图 | Week 27 | X_BAR_R/S/P/C/U + A2/A3 系数表 | 完整 SPC 能力 |

### 19.3 代码交付统计

| 类型 | Phase 1 | Phase 2 | Phase 3 | 合计 |
|------|---------|---------|---------|------|
| SQL DDL 表 | 4（pro_work_order等） | 6（pro_flow_card/step/dispatch/andon/sn） | 10（qc×3+eq×3+dc×2+spc×2） | 20 表 |
| SQL 菜单 | Week17-21 | Week22-24 | Week25-27 | 7 个菜单 SQL |
| Java 后端 | ~40 文件 | ~80 文件 | ~100 文件 | ~220 文件 |
| Vue/TS 前端 | ~15 文件 | ~30 文件 | ~40 文件 | ~85 文件 |
| 归档计划 | 5 个 | 5 个 | 3 个 | 13 个 |

### 19.4 集成点打通状态

| 集成点 | 状态 | 完成周次 |
|--------|------|---------|
| ERP→MES 工单下达 MQ | ✅ 已通 | Week 16 |
| MES→ERP 工单完工 MQ | ✅ 已通（Week 25 修复 exchange bug） | Week 25 |
| MES→ERP IQC 检验完成 MQ | ✅ 已通 | Week 25 |
| IoT→MES 安灯联动（IMesAndonService） | ✅ 已通 | Week 23 |
| MES→IoT 设备数据查询 | ⏳ 契约就绪，MES 侧未调用 | - |
| 设备软关联（iot_device_id ↔ mes_equipment_id） | ⏳ 字段已建，双向查询未打通 | - |
| IoT 自动采集归档到 dc_data_record | ⏳ 常量预留，无写入逻辑 | - |

### 19.5 归档计划文档清单

| 文件 | 内容 |
|------|------|
| `doc/mes/plans/2026-06-16-mes-iot-phase-plan.md` | Phase 1~3 + IoT 总计划 |
| `doc/mes/plans/2026-06-16-mes-week17-implementation-report.md` | Week17 模块骨架 |
| `doc/mes/plans/2026-06-17-mes-week18-implementation-plan.md` | Week18 主数据桥接 |
| `doc/mes/plans/2026-06-17-mes-week19-implementation-plan.md` | Week19 MES工单+MQ |
| `doc/mes/plans/2026-06-18-mes-week20-implementation-plan.md` | Week20 流转卡引擎 |
| `doc/mes/plans/2026-06-19-mes-week21-implementation-plan.md` | Week21 Phase1前端收尾 |
| `doc/mes/plans/2026-06-19-mes-week21-verification-report.md` | Week21 验证报告 |
| `doc/mes/plans/2026-06-20-mes-week22-implementation-plan.md` | Week22 派工引擎 |
| `doc/mes/plans/2026-06-21-mes-week23-implementation-plan.md` | Week23 安灯系统 |
| `doc/mes/plans/2026-06-22-mes-week24-implementation-plan.md` | Week24 SN追溯 |
| `doc/mes/plans/2026-06-23-mes-week25-implementation-plan.md` | Week25 质量管理+MQ贯通 |
| `doc/mes/plans/2026-06-24-mes-week26-implementation-plan.md` | Week26 设备管理 |
| `doc/mes/plans/2026-06-25-mes-week27-implementation-plan.md` | Week27 数据采集+SPC |

---

## 二十、Week 28 MBOM 子系统扩展

> **定位**: Phase 1-3 全部交付（Week 17-27）后的后续扩展。引入 MBOM（制造 BOM）子系统，目的是修复 BOM 管理增强审查中发现的核心问题——MES 完全未消费 BOM、职责错位、ERP 数据正确性隐患。详见变更说明文档 `NexusERP-CHG-2026-002`（`doc/erp/BOM优化-ERP成本融合MES制造BOM-变更说明文档.md`）。
>
> **交付状态**: SQL 已就绪（`mes-week28-09-mbom.sql` + `mes-week28-22-menu.sql`），后端/前端代码交付以变更说明文档为准。

### 20.1 背景与定位

Phase 1-5 BOM 管理增强需求实现后，经系统性审查发现三个核心问题，导致系统无法形成「ERP 维护、MES 使用」的融合闭环：

1. **MES 完全未消费 BOM**：`MesErpBridgeService.getBomLines` 是死代码；工单 `bomId` 存了但从不用；无 BOM 展开/快照/追溯。工艺路线在工单创建时做了快照，BOM 侧是空壳。
2. **职责错位**：制造执行字段（`bd_bom_detail.process_id`/`routing_step_id`/`workstation_id`）建在 ERP 侧，但制造工序/工位关联是 MES 的职责。
3. **ERP 数据正确性隐患**：引用一致性无校验、VO 富集字段未填充、多「当前版本」会让 `selectOne` 报错、`explodeBom` 不聚合重复叶子、无成本滚算。

Week 28 MBOM 子系统即针对上述问题 1、2 落地 MES 侧改造，与 ERP 侧清理（Phase 1）协同形成 ERP/MES BOM 融合闭环。

### 20.2 与 ERP BOM 的职责划分

| 维度 | ERP 成本 BOM | MES 制造 BOM（MBOM） |
|------|-------------|----------------------|
| 职责 | 成本/物料（用量、损耗、成本滚算） | 制造（子件→工序→工位→作业内容） |
| 表 | `bd_bom` / `bd_bom_detail` | `mes_mbom` / `mes_mbom_line` |
| 维护方 | ERP 侧 | MES 侧专用编制页面 |
| 关系 | 1 个 ERP BOM : 1 个 MBOM（`mes_mbom.bom_id` UNIQUE） | - |
| ERP 侧退回 | 删除 `bd_bom_detail.workstation_id`/`process_id`/`routing_step_id`，保留 `operation_step`（软引用路线步骤序号） | - |

> **核心决策**: 制造关联（工序/工位/作业内容）的权威在 MES MBOM；ERP `bd_bom_detail` 退回纯物料+用量+成本。工单下达时快照 MBOM 到 `pro_work_order_bom_line`，与 `pro_work_order_step` 一致策略，隔离后续变更。

### 20.3 数据表设计

新增 3 张表，目标库 `ry-mes`。约定对齐：`tenant_id VARCHAR(20) DEFAULT '000000'`、`del_flag CHAR(1)`、状态列 VARCHAR、BIGINT 主键。脚本幂等（`DROP TABLE IF EXISTS`）。

**脚本**: `doc/mes/sql/mes-week28-09-mbom.sql`

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `mes_mbom` | 制造 BOM 头（1:1 关联 ERP `bd_bom.bom_id`） | `mbom_id`(PK), `bom_id`(UNIQUE), `routing_id`, `mbom_version`, `mbom_status`(DRAFT/ACTIVE/FROZEN) |
| `mes_mbom_line` | 制造 BOM 明细 | `mbom_line_id`(PK), `mbom_id`, `bom_detail_id`(可空), `child_material_id`, `quantity_per_parent`, `loss_rate`, `routing_step_id`, `process_id`, `workstation_id`, `operation_content`, `backflush_flag` |
| `pro_work_order_bom_line` | 工单下达时的 MBOM 快照 | `wo_bom_line_id`(PK), `mes_wo_id`, `mbom_line_id`, 冗余物料/工序/工位编码名称, `total_quantity`(含损耗用量) |

> `mes_mbom_line.bom_detail_id` 可空：允许 MES 增加非 ERP BOM 的消耗料（MES 附加件/辅料）。
> `pro_work_order_bom_line` 快照后不再跟踪 MBOM 变更，保证追溯完整性，与 `pro_work_order_step` 一致策略。

```sql
-- ----------------------------
-- mes_mbom: MES制造BOM头(1:1关联ERP bom_id)
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom`;
CREATE TABLE `mes_mbom` (
    `mbom_id`      BIGINT NOT NULL COMMENT 'MBOM ID(主键)',
    `tenant_id`    VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `bom_id`       BIGINT NOT NULL COMMENT '关联ERP bd_bom.bom_id',
    `routing_id`   BIGINT COMMENT '制造工艺路线ID(可继承自bd_bom.routing_id或MES侧指定)',
    `mbom_version` VARCHAR(32) COMMENT 'MBOM版本号',
    `mbom_status`  VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/FROZEN',
    `remark`       VARCHAR(500) COMMENT '备注',
    `create_dept`  BIGINT COMMENT '创建部门',
    `create_by`    BIGINT COMMENT '创建者',
    `create_time`  DATETIME COMMENT '创建时间',
    `update_by`    BIGINT COMMENT '更新者',
    `update_time`  DATETIME COMMENT '更新时间',
    `del_flag`     CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`mbom_id`),
    UNIQUE INDEX `uk_bom_id` (`bom_id`),
    INDEX `idx_routing_id` (`routing_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES制造BOM头';

-- ----------------------------
-- mes_mbom_line: MBOM明细行(承载从ERP bd_bom_detail迁移出的工序/工位/作业内容)
-- bom_detail_id可空: 允许MES增加非ERP BOM的消耗料(MES附加件/辅料)
-- ----------------------------
DROP TABLE IF EXISTS `mes_mbom_line`;
CREATE TABLE `mes_mbom_line` (
    `mbom_line_id`        BIGINT NOT NULL COMMENT 'MBOM明细ID(主键)',
    `tenant_id`           VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `mbom_id`             BIGINT NOT NULL COMMENT '关联mes_mbom.mbom_id',
    `bom_id`              BIGINT NOT NULL COMMENT '冗余ERP bom_id',
    `bom_detail_id`       BIGINT COMMENT '关联ERP bd_bom_detail.detail_id(可空,MES附加消耗料时为空)',
    `child_material_id`   BIGINT NOT NULL COMMENT '子项物料ID',
    `quantity_per_parent` DECIMAL(18, 6) NOT NULL COMMENT '单位父项用量',
    `loss_rate`           DECIMAL(8, 4) DEFAULT 0.0000 COMMENT '损耗率(%)',
    `routing_step_id`     BIGINT COMMENT 'ERP bd_routing_step.step_id(工序步骤)',
    `process_id`          BIGINT COMMENT 'ERP bd_process.process_id(工序)',
    `workstation_id`      BIGINT COMMENT 'ERP bd_workstation.workstation_id(工位)',
    `operation_content`   VARCHAR(500) COMMENT '作业内容',
    `backflush_flag`      TINYINT(1) DEFAULT 0 COMMENT '是否倒冲 0否1是',
    `sort_order`          INT DEFAULT 0 COMMENT '显示排序',
    `remark`              VARCHAR(500) COMMENT '备注',
    `create_dept`         BIGINT COMMENT '创建部门',
    `create_by`         BIGINT COMMENT '创建者',
    `create_time`       DATETIME COMMENT '创建时间',
    `update_by`         BIGINT COMMENT '更新者',
    `update_time`       DATETIME COMMENT '更新时间',
    `del_flag`            CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`mbom_line_id`),
    INDEX `idx_mbom_id` (`mbom_id`),
    INDEX `idx_bom_id` (`bom_id`),
    INDEX `idx_bom_detail_id` (`bom_detail_id`),
    INDEX `idx_routing_step_id` (`routing_step_id`),
    INDEX `idx_child_material_id` (`child_material_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES制造BOM明细';

-- ----------------------------
-- pro_work_order_bom_line: 工单下达时的MBOM快照(隔离ERP后续编辑)
-- 与pro_work_order_step一致策略: 快照后不再跟踪MBOM变更,保证追溯完整性
-- ----------------------------
DROP TABLE IF EXISTS `pro_work_order_bom_line`;
CREATE TABLE `pro_work_order_bom_line` (
    `wo_bom_line_id`      BIGINT NOT NULL COMMENT '快照明细ID(主键)',
    `tenant_id`           VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `mes_wo_id`           BIGINT NOT NULL COMMENT '关联MES工单ID',
    `mbom_line_id`        BIGINT COMMENT '源mes_mbom_line.mbom_line_id(可空,无MBOM时)',
    `bom_id`              BIGINT COMMENT 'ERP BOM ID',
    `bom_detail_id`       BIGINT COMMENT 'ERP明细ID(溯源)',
    `child_material_id`   BIGINT NOT NULL COMMENT '子项物料ID',
    `child_material_code` VARCHAR(64) COMMENT '物料编码(冗余)',
    `child_material_name` VARCHAR(200) COMMENT '物料名称(冗余)',
    `quantity_per_parent` DECIMAL(18, 6) NOT NULL COMMENT '单位父项用量',
    `loss_rate`           DECIMAL(8, 4) DEFAULT 0.0000 COMMENT '损耗率(%)',
    `total_quantity`      DECIMAL(18, 6) COMMENT '含损耗用量 = qty × (1 + loss/100)',
    `routing_step_id`     BIGINT COMMENT '工序步骤ID',
    `sequence_num`        INT COMMENT '工序序号(冗余)',
    `process_id`          BIGINT COMMENT '工序ID',
    `process_code`        VARCHAR(64) COMMENT '工序编码(冗余)',
    `process_name`        VARCHAR(200) COMMENT '工序名称(冗余)',
    `workstation_id`      BIGINT COMMENT '工位ID',
    `workstation_code`    VARCHAR(64) COMMENT '工位编码(冗余)',
    `workstation_name`    VARCHAR(200) COMMENT '工位名称(冗余)',
    `operation_content`   VARCHAR(500) COMMENT '作业内容',
    `backflush_flag`      TINYINT(1) DEFAULT 0 COMMENT '是否倒冲 0否1是',
    `sort_order`          INT DEFAULT 0 COMMENT '显示排序',
    `remark`              VARCHAR(500) COMMENT '备注',
    `create_dept`         BIGINT COMMENT '创建部门',
    `create_by`         BIGINT COMMENT '创建者',
    `create_time`       DATETIME COMMENT '创建时间',
    `update_by`         BIGINT COMMENT '更新者',
    `update_time`       DATETIME COMMENT '更新时间',
    `del_flag`            CHAR(1) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`wo_bom_line_id`),
    INDEX `idx_mes_wo_id` (`mes_wo_id`),
    INDEX `idx_child_material_id` (`child_material_id`),
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单BOM明细快照';
```

### 20.4 菜单权限

**脚本**: `doc/mes/sql/mes-week28-22-menu.sql`（目标库 `ry-vue`，幂等：先 `DELETE` 旧错误记录再 `INSERT IGNORE`）

父菜单：`4001`（生产执行），菜单 ID 段 2950。

| 菜单 ID | 名称 | 父菜单 | 类型 | 权限标识 | 组件 | 图标 |
|---------|------|--------|------|----------|------|------|
| 2950 | MBOM 管理 | 4001 | C（菜单） | `mes:pro:mbom:list` | `mes/pro/mbom/index` | tree |
| 2951 | MBOM 查询 | 2950 | F（按钮） | `mes:pro:mbom:query` | - | # |
| 2952 | MBOM 新增 | 2950 | F（按钮） | `mes:pro:mbom:add` | - | # |
| 2953 | MBOM 修改 | 2950 | F（按钮） | `mes:pro:mbom:edit` | - | # |
| 2954 | MBOM 删除 | 2950 | F（按钮） | `mes:pro:mbom:remove` | - | # |

> 组件追溯菜单（原计划 2980）已移除——现有 SN 追溯菜单（4080，Week 24）已作为追溯入口，后端追溯 API 端点（`/mes/pro/trace/backward`、`/mes/pro/trace/forward`，权限 `mes:pro:trace:query`）已就绪。

### 20.5 交付状态

| 交付项 | 状态 | 说明 |
|--------|------|------|
| DDL 脚本 `mes-week28-09-mbom.sql` | ✅ 已就绪 | 三表（`mes_mbom`/`mes_mbom_line`/`pro_work_order_bom_line`），幂等 |
| 菜单脚本 `mes-week28-22-menu.sql` | ✅ 已就绪 | 2950-2954，父菜单 4001，幂等 |
| 后端代码（MBOM CRUD + 工单快照 + 追溯） | 见变更说明文档 | 详见 `doc/erp/BOM优化-ERP成本融合MES制造BOM-变更说明文档.md` §4.2-§4.3 |
| 前端代码（MBOM 编制页 + 工单 BOM Tab） | 见变更说明文档 | 详见同上 §5.2 |

> **与主文档其他章节关系**: 本节为 Phase 1-3 之外的独立扩展，不在 §10「MES 实施计划」原计划周次内。`mes_mbom`/`mes_mbom_line`/`pro_work_order_bom_line` 三表未计入 §2「~17 张表」统计，单独列出。

