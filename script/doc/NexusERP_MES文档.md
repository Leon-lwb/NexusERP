# NexusERP - MES模块综合文档

> **文档状态**: 📋 规划设计文档（已修订） — MES模块尚未开始实施，本文档为开发前的完整设计方案。
> 当前项目中 `ruoyi-mes` Maven模块尚未创建，`@DS("mes")` 数据源尚未配置，~13张数据表DDL尚未执行。
> ERP侧集成准备状态：主数据API已就绪✅，MQ消息发送/队列常量/数据源配置待实现❌。

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

## 二、MES数据表设计（~13张表）

> **精简说明**: 原规划28张表精简为~13张。主要削减：
> - 主数据表4张 → 0张（跨库查询ERP）
> - 仓储扩展表3张 → 0张（复用ERP wm_material_stock）
> - 质量管理表5张 → 3张（不良品+检验项目合入检验表JSON字段）
> - 设备管理表4张 → 3张（点检合入设备台账JSON字段）

### 2.1 生产执行模块(pro) — 7张表

```sql
-- MES工单
CREATE TABLE mes_pro_workorder (
    wo_id            BIGINT NOT NULL COMMENT '主键',
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    wo_no            VARCHAR(64) NOT NULL COMMENT 'MES工单编号',
    erp_wo_id        BIGINT COMMENT '关联ERP工单ID',
    material_id      BIGINT NOT NULL COMMENT '物料ID(来自ERP)',
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
    material_spec    VARCHAR(256),
    quantity         DECIMAL(14,4) NOT NULL COMMENT '计划数量',
    completed_qty    DECIMAL(14,4) DEFAULT 0,
    scrapped_qty     DECIMAL(14,4) DEFAULT 0,
    routing_id       BIGINT COMMENT '工艺路线ID(来自ERP)',
    bom_id           BIGINT COMMENT 'BOM ID(来自ERP)',
    status           VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    priority         INT DEFAULT 0,
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    select_flow_id   BIGINT COMMENT '审批流ID(Flowable)',
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

-- MES工单工序
CREATE TABLE mes_pro_wo_step (
    wo_step_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    wo_id            BIGINT NOT NULL,
    process_id       BIGINT NOT NULL COMMENT '工序ID(来自ERP)',
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT NOT NULL COMMENT '工序序号',
    is_key_process   TINYINT(1) DEFAULT 0 COMMENT '是否关键工序',
    workcenter_id    BIGINT COMMENT '工作中心(来自ERP)',
    workcenter_code  VARCHAR(64),
    status           VARCHAR(16) DEFAULT 'PENDING',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (wo_step_id),
    INDEX idx_wo (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工单工序';

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
    process_id       BIGINT NOT NULL,
    process_code     VARCHAR(64),
    process_name     VARCHAR(128),
    sequence_num     INT NOT NULL,
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/SKIPPED',
    operator_id      BIGINT COMMENT '操作员',
    operator_name    VARCHAR(64),
    good_qty         DECIMAL(14,4) DEFAULT 0,
    defect_qty       DECIMAL(14,4) DEFAULT 0,
    scrap_qty        DECIMAL(14,4) DEFAULT 0,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
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

-- MES SN序列号追溯
CREATE TABLE mes_pro_sn (
    sn_id            BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    sn_code          VARCHAR(128) NOT NULL,
    wo_id            BIGINT NOT NULL,
    card_id          BIGINT,
    material_id      BIGINT NOT NULL,
    material_code    VARCHAR(64),
    material_name    VARCHAR(128),
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
```

### 2.2 质量管理模块(qc) — 3张表

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
    defect_info      JSON COMMENT '不良品信息(类型/数量/描述)',
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
    defect_info      JSON COMMENT '不良品详情',
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
    defect_info      JSON COMMENT '不良品信息',
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
```

### 2.3 设备管理模块(eq) — 3张表

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
    inspection_items JSON COMMENT '点检项目及标准(JSON)',
    last_inspection  JSON COMMENT '最近一次点检结果(JSON)',
    iot_device_id    BIGINT COMMENT '关联IoT设备ID(接口查询,无外键)',
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
```

### 2.4 数据采集 + SPC模块 — 4张表（Phase 3）

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

-- MES采集数据记录(先存MySQL,数据量超500万行后考虑迁移TDengine)
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

---

## 三、主数据访问策略（核心设计变更）

### 3.1 原方案 vs 修订方案

| 维度 | 原方案 | 修订方案 | 原因 |
|------|--------|---------|------|
| 物料 | mes_md_material 同步表 | 跨库查询ERP bd_material | 避免数据双写 |
| BOM | mes_md_bom + bom_line | 跨库查询ERP bd_bom | 减少同步复杂度 |
| 工艺路线 | mes_md_routing + routing_step | 跨库查询ERP | ERP已有完整数据 |
| WIP库存 | mes_wm_wip_stock | 复用ERP wm_material_stock | 避免库存双写 |
| 线边仓 | mes_wm_line_side_warehouse | 复用ERP仓库+location区分 | 减少表冗余 |

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
      → 创建 mes_pro_workorder
      → 根据ERP工艺路线自动生成 mes_pro_wo_step
```

**ERP侧改动（`PrdWorkOrderServiceImpl.afterFlowHandle()`）：**
- 在APPROVED分支增加MQ消息发送逻辑
- 消息类型：`WorkOrderReleaseMessage`，包含ERP工单ID、物料ID、数量、工艺路线ID、BOM ID等
- **当前状态：❌ 待实现**。当前`afterFlowHandle()`仅更新工单状态（APPROVING/APPROVED/CREATED），不发送MQ消息
- 注：`PrdWorkOrderServiceImpl`已注入`ReliableMessageProducer`，并在`complete()`方法中发送了`ProductionCompleteMessage`，说明MQ基础设施已可用，只需在`afterFlowHandle()`中添加发送逻辑

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

1. 创建流转卡时，根据工艺路线自动生成所有工序步骤记录(mes_pro_flow_card_step)
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

### 10.1 MES Phase 1 (Week 13-19): 基础框架 + 主数据 + 工单

| 周次 | 任务 | 人天 | 前置条件 |
|------|------|------|----------|
| Week 13 | 创建ruoyi-mes模块+数据源配置+基础框架+公共组件 | 3 | ERP侧完成MQ消息扩展+Exchange常量+mes数据源配置 |
| Week 14 | 主数据跨库查询(MesErpBridgeService)+前端只读页面 | 3 | bd模块Controller已就绪 ✅ |
| Week 15-16 | MES工单+工单工序+Flowable审批流集成+ERP→MES MQ联动 | 5 | 复用ERP已有Flowable模式(9个单据已集成) |
| Week 17-19 | Phase 1前端页面(工单列表/详情/工序Tab/主数据只读)+菜单权限 | 5 | - |

**Phase 1 小计: 16人天**

### 10.2 MES Phase 2 (Week 20-26): 流转卡 + 工序执行 + 安灯 + SN

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 20-21 | 流转卡引擎(状态机+服务+自动生成工序) | 5 |
| Week 22-23 | 派工+工序执行(扫码开工/完工/数量记录) | 4 |
| Week 24 | 安灯系统(CRUD+SSE实时推送看板) | 3 |
| Week 25-26 | SN追溯体系+Phase 2前端页面 | 4 |

**Phase 2 小计: 16人天**

### 10.3 MES Phase 3 (Week 27-32): 质量 + 设备 + 数据采集 + SPC

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 27-28 | 质量管理(IQC/IPQC/OQC+ERP联动) | 5 |
| Week 29-30 | 设备管理(台账+保养+维修) | 4 |
| Week 31-32 | 数据采集+SPC+Phase 3前端页面+集成测试 | 4 |

**Phase 3 小计: 13人天**

### 10.4 工作量汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| Phase 1 | Week 13-19 | 16 |
| Phase 2 | Week 20-26 | 16 |
| Phase 3 | Week 27-32 | 17 |
| **合计** | **Week 13-32 (20周)** | **~49** |

> **对比原计划**: 原66人天/22周 → 修订49人天/20周（削减26%）

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
| 7 | 数据采集只在MES | 设备数据采集/传感器数据在MES处理 |
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
| ERP→MES | 工单下达 | MQ (RabbitMQ) | PrdWorkOrderServiceImpl审批通过后发消息 | ❌ afterFlowHandle未发MQ，待实现 |
| ERP→MES | 主数据查询 | 跨库直查 | MES Service切@DS("erp")读物料/BOM/工艺 | ✅ bd模块Controller已完整 |
| MES→ERP | 工单完工 | MQ (RabbitMQ) | 通知ERP更新工单状态+完工入库 | ❌ MES模块未创建 |
| MES→ERP | IQC检验完成 | MQ (RabbitMQ) | 通知ERP更新收货单质检状态 | ❌ MES模块未创建 |

### 12.4 MES↔IoT集成点（预留）

| 方向 | 方式 | 说明 |
|------|------|------|
| MES查设备数据 | REST API / Redis缓存 | MES查询IoT设备最新数据 |
| IoT告警→MES安灯 | 接口抽象 | `IMesAndonService.createAndonFromAlert()` 接口 |
| 设备关联 | 字段预留 | `mes_eq_equipment.iot_device_id` + `iot_device.mes_equipment_id` |
