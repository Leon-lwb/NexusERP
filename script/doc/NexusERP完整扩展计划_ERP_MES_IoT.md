# NexusERP 完整扩展计划：ERP完善 → MES → IoT

> 规划日期: 2026-05-24
> 基于: nexus-erp (RuoYi-Vue-Plus 5.6.0) + ktg-mes 3.8.2 (参考系统)
> 总工期: 48周 (约12个月)，按单人全职估算
> 三阶段: ERP完工(12周) → MES模块(20周) → IoT模块(16周)

---

## 目录

- [一、概述与目标](#一概述与目标)
- [二、Phase 1: ERP完工 (第1-12周)](#二phase-1-erp完工-第1-12周)
- [三、Phase 2: MES模块 (第13-32周)](#三phase-2-mes模块-第13-32周)
- [四、Phase 3: IoT模块 (第33-48周)](#四phase-3-iot模块-第33-48周)
- [五、总体时间线与里程碑](#五总体时间线与里程碑)
- [六、技术架构决策](#六技术架构决策)
- [七、数据库规划](#七数据库规划)
- [八、前端规划](#八前端规划)
- [九、风险分析与缓解](#九风险分析与缓解)
- [十、参考资源索引](#十参考资源索引)

---

## 一、概述与目标

### 1.1 当前状态

| 维度 | 状态 |
|------|------|
| NexusERP 基础框架 | RuoYi-Vue-Plus 5.6.0 (Spring Boot 3.x / MyBatis-Plus 3.5.x / Sa-Token / Vue 3.5 + Element Plus + TypeScript) |
| ERP 完成度 | ~70%，剩余 41 项任务，~70 人天 |
| 已有 ERP 模块 | bd(11表), prd(6表), pu(6表), sch(11表), so(7表), wm(24表) |
| 工作流 | Flowable 7.0 已集成到采购/销售/工单/仓储 |
| 条码管理 | 已完成 (wm_barcode + BarcodeGenerator) |
| 多数据源 | master(ry-vue) + erp(ry-erp)，通过 Baomidou dynamic-datasource |
| 参考 MES 系统 | ktg-mes 3.8.2 (~171表, 8子模块, RuoYi-Vue 原始版) |
| IoT 基础设施 | 无 (零代码) |

### 1.2 三阶段目标

```
Phase 1: ERP 完工 (12周)
  ├── 完成 P0/P1/P2 全部剩余 41 项任务
  ├── 补齐采购/仓储/生产/质量/设备模块
  └── 交付: 完整的企业 ERP 系统

Phase 2: MES 模块 (20周)  
  ├── 新建 ruoyi-mes 模块 + ry-mes 数据库
  ├── 7 个子模块: md/pro/wm/qc/eq/dc/spc
  └── 交付: 车间级生产执行系统

Phase 3: IoT 模块 (16周)
  ├── 新建 ruoyi-iot 模块 + ry-iot 数据库
  ├── 多协议接入 + 规则引擎 + 边缘计算
  └── 交付: 设备物联网平台
```

### 1.3 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                      ruoyi-admin (启动器)                     │
├─────────────────────────────────────────────────────────────┤
│  ruoyi-system     │ 系统管理 (用户/角色/菜单/部门/字典)        │
│  ruoyi-flowable   │ 工作流引擎 (审批流程)                     │
├─────────────────────────────────────────────────────────────┤
│  ruoyi-erp        │ ERP 业务 (bd/prd/pu/so/wm/sch/qc/eq)     │
│    @DS("erp")     │ 数据库: ry-erp                           │
├─────────────────────────────────────────────────────────────┤
│  ruoyi-mes  [新]  │ MES 制造执行 (md/pro/wm/qc/eq/dc/spc)   │
│    @DS("mes")     │ 数据库: ry-mes                           │
├─────────────────────────────────────────────────────────────┤
│  ruoyi-iot  [新]  │ IoT 设备平台 (协议/规则/边缘/告警)        │
│    @DS("iot")     │ 数据库: ry-iot + TDengine (时序)         │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、Phase 1: ERP完工 (第1-12周)

### 2.1 策略

- 严格按照 `ERP剩余功能开发计划清单.md` 执行
- P2 的 qc/eq 模块按**轻量版**实现，MES 阶段再做车间级完整版
- 审批流已完成 (Flowable 已接入)，直接从计划中移除

### 2.2 按周任务分解

#### 第1周: P0清零 + 采购启动
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-001 | 序列号规则表 `bd_serial_number_rule` | bd | 1 |
| R-006 | 供应商校验 `validateVendor()` | pu | 0.5 |
| ★ | 里程碑: P0遗留清零 | | |

#### 第2周: 采购闭环
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-007 | 请购来源扩展 (低库存建议/生产需求转入) | pu | 1 |
| R-008 | 采购收货差异处理 (部分/超收/短收 + 状态回写) | pu | 1 |
| R-009 | 收货自动过账 (联动 WmStockOperationService) | pu | 0.5 |
| R-011 | 供应商评估实现 (评分算法 + pu_vendor_evaluation 表) | pu | 1.5 |
| R-012 | 供应商绩效沉淀 | pu | 0.5 |
| ★ | 里程碑: 采购模块完整闭环 | | |

#### 第3周: 仓储策略层
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-013 | 上架策略 (PutawayStrategyService) | wm | 1.5 |
| R-014 | 拣货策略 (PickingStrategyService, FIFO/批次/库位) | wm | 1.5 |
| R-015 | 库存估值 (移动加权平均成本) | wm | 2 |
| ★ | 里程碑: 仓储策略层完成 | | |

#### 第4周: 仓储深度 + 生产扩展
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-016 | 安全库存预警 (SnailJob 定时任务) | wm | 1 |
| R-017 | 库存台账校验 (StockReconciliationService) | wm | 1 |
| R-018 | 委外订单 (prd/subcontract 子包 + prd_subcontract_order 表) | prd | 1.5 |
| R-019 | 委外发料与收货 | prd | 1 |
| R-020 | 返工订单 (PrdWorkOrder 增加 woType 字段) | prd | 1 |
| ★ | 里程碑: P1 仓储深度 + 委外/返工上线 | | |

#### 第5周: 生产深度
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-021 | 生产消耗自动过账 (完工倒冲领料) | prd | 1.5 |
| R-022 | 产能日历 (prd_capacity_calendar 表 + 服务) | prd | 1.5 |
| R-023 | 领料与完工入库联动 | prd | 1 |
| R-024 | 委外加工前端 | 前端 | 1 |
| ★ | 里程碑: P1 生产深度完成 | | |

#### 第6周: P1前端 + P2启动
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-025 | 返工/产能日历前端 | 前端 | 1 |
| R-026 | 采购收货联动入口 | 前端 | 0.5 |
| R-027 | 供应商评估前端完善 | 前端 | 1 |
| R-028 | ERP qc 子包骨架 + 检验指标表 | qc | 1 |
| R-029 | ERP 检验模板 (qc_template 关联物料) | qc | 1.5 |
| ★ | 里程碑: P1 全部交付 + P2 启动 | | |

#### 第7-8周: ERP 质量管理 (轻量版)
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-030 | ERP 来料检验 IQC (关联收货单) | qc | 2 |
| R-031 | ERP 过程检验 IPQC (关联工单) | qc | 1.5 |
| R-032 | ERP 出厂检验 OQC (关联出库) | qc | 1.5 |
| R-033 | ERP 缺陷管理 (qc_defect + qc_defect_record) | qc | 1 |
| R-010 | IQC 检验联动 (收货→IQC 接口预留) | pu | 0.5 |
| R-034 | ERP 质量模块前端 | 前端 | 3.5 |
| ★ | 里程碑: ERP 质量管理完成 (MES 将在此基础上扩展车间级功能) | | |

#### 第9-10周: ERP 设备管理 (轻量版)
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-035 | eq 子包骨架 + 设备台账 (eq_equipment/eq_equipment_type) | eq | 1.5 |
| R-036 | 点检管理 (eq_check_plan/eq_check_record) | eq | 1.5 |
| R-037 | 保养管理 (eq_maintenance_plan/eq_maintenance_record) | eq | 1.5 |
| R-038 | 维修管理 (eq_repair_record) | eq | 1.5 |
| R-039 | OEE 计算 (基础版，手动输入数据) | eq | 1 |
| R-040 | ERP 设备模块前端 | 前端 | 2.5 |
| ★ | 里程碑: ERP 设备管理完成 (MES 将做车间级完整版) | | |

#### 第11-12周: 批次/SN追溯
| 编号 | 任务 | 模块 | 人天 |
|------|------|------|------|
| R-041 | 序列号管理 (wm_sn 表，对接 R-001 规则) | wm | 1.5 |
| R-042 | 批次管理增强 (wm_batch 独立表) | wm | 1 |
| R-043 | 追溯查询服务 (前向+后向追溯) | wm | 2 |
| R-044 | 追溯前端 | 前端 | 1 |
| R-055 | 盘点差异审批流 (Flowable) | wm | 1.5 |
| ★ | 里程碑: ERP 全部功能交付 | | |

### 2.3 ERP 完工交付物

| 模块 | 新增表 | 新增实体 | 关键能力 |
|------|--------|---------|---------|
| bd | 1 (bd_serial_number_rule) | 1 | 序列号规则引擎 |
| pu | 1 (pu_vendor_evaluation) | 1 | 供应商校验/评估/绩效 |
| wm | 3 (wm_sn/wm_batch/上架拣货策略) | 3 | SN/批次追溯/估值/预警 |
| prd | 2 (prd_subcontract_*/prd_capacity_calendar) | 4 | 委外/返工/倒冲/产能 |
| qc (轻量) | 8 | 8 | IQC/IPQC/OQC/缺陷 |
| eq (轻量) | 7 | 6 | 台账/点检/保养/维修/OEE |
| 前端 | — | — | 对应全部功能页面 |

---

## 三、Phase 2: MES模块 (第13-32周)

### 3.1 模块结构

```
ruoyi-modules/ruoyi-mes/
  pom.xml
  src/main/java/org/dromara/mes/
    ├── config/                    # MES 自动配置 + 序列号引擎
    │   ├── MesAutoConfiguration.java
    │   └── SerialNumberConfig.java
    ├── common/                    # MES 公共类
    │   ├── enums/                 # 状态枚举 (CardStatus/ProcessStatus等)
    │   ├── constant/              # MES 常量
    │   └── utils/                 # MES 工具类
    ├── md/                        # MES 主数据 (扩展 ERP bd)
    │   ├── controller/
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    ├── pro/                       # 生产执行 (★核心模块)
    │   ├── controller/
    │   │   ├── MesWorkorderController.java
    │   │   ├── MesRouteController.java
    │   │   ├── MesRouteProcessController.java
    │   │   ├── MesProCardController.java        # 流转卡 ★
    │   │   ├── MesProCardProcessController.java  # 流转卡工序 ★
    │   │   ├── MesProSnProcessController.java    # SN追溯
    │   │   ├── MesProTaskController.java         # 派工
    │   │   ├── MesProFeedbackController.java     # 报工
    │   │   ├── MesProTransOrderController.java   # 转移单
    │   │   ├── MesAndonConfigController.java     # 安灯配置
    │   │   └── MesAndonRecordController.java     # 安灯事件
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    │       └── MesProCardServiceImpl.java        # 流转卡引擎 ★
    ├── wm/                        # 仓储扩展 (扩展 ERP wm)
    │   ├── controller/            # 三级单据 (Header+Line+Detail)
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    ├── qc/                        # 质量控制 (扩展 ERP qc)
    │   ├── controller/
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    ├── eq/                        # 设备管理 (扩展 ERP eq)
    │   ├── controller/
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    ├── dc/                        # 数据采集 (nexus-erp 独有)
    │   ├── controller/
    │   │   ├── MesDcPointController.java
    │   │   ├── MesDcParameterController.java
    │   │   └── MesDcRecordController.java
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    └── spc/                       # 统计过程控制 (nexus-erp 独有)
        ├── controller/
        │   ├── MesSpcRuleController.java
        │   ├── MesSpcSampleController.java
        │   └── MesSpcSampleValueController.java
        ├── domain/ (+bo/vo)
        ├── mapper/
        └── service/impl/
```

### 3.2 核心数据表设计

#### 3.2.1 主数据 (mes_md_*) — 扩展已有 ERP 主数据

```sql
-- MES 级 BOM 扩展 (关联 erp bd_bom)
CREATE TABLE mes_md_product_bom (
    bom_id        BIGINT NOT NULL,
    tenant_id     BIGINT NOT NULL,
    erp_bom_id    BIGINT COMMENT '关联ERP BOM ID',
    material_id   BIGINT NOT NULL,
    process_id    BIGINT COMMENT '关联工序ID (按工序投料)',
    quantity      DECIMAL(14,4) NOT NULL,
    unit_id       BIGINT,
    PRIMARY KEY (bom_id)
);

-- SOP 作业指导书
CREATE TABLE mes_md_product_sop (
    sop_id        BIGINT NOT NULL,
    tenant_id     BIGINT NOT NULL,
    material_id   BIGINT NOT NULL,
    process_id    BIGINT COMMENT '关联工序',
    sop_name      VARCHAR(128) NOT NULL,
    sop_version   VARCHAR(16),
    file_url      VARCHAR(512) COMMENT 'MinIO文件URL',
    status        VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (sop_id)
);

-- SIP 检验指导书
CREATE TABLE mes_md_product_sip (
    sip_id        BIGINT NOT NULL,
    tenant_id     BIGINT NOT NULL,
    material_id   BIGINT NOT NULL,
    process_id    BIGINT COMMENT '关联工序',
    sip_name      VARCHAR(128) NOT NULL,
    sip_version   VARCHAR(16),
    file_url      VARCHAR(512),
    status        VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (sip_id)
);

-- 工位-设备绑定 (N:M)
CREATE TABLE mes_md_workstation_machine (
    wm_id          BIGINT NOT NULL,
    tenant_id      BIGINT NOT NULL,
    workstation_id BIGINT NOT NULL,
    equipment_id   BIGINT NOT NULL,
    quantity       INT DEFAULT 1,
    PRIMARY KEY (wm_id)
);

-- 工位-人员资质绑定 (N:M)
CREATE TABLE mes_md_workstation_worker (
    ww_id          BIGINT NOT NULL,
    tenant_id      BIGINT NOT NULL,
    workstation_id BIGINT NOT NULL,
    worker_id      BIGINT NOT NULL,
    skill_level    VARCHAR(16) COMMENT '技能等级',
    PRIMARY KEY (ww_id)
);

-- 工位-工具绑定 (N:M)
CREATE TABLE mes_md_workstation_tool (
    wt_id          BIGINT NOT NULL,
    tenant_id      BIGINT NOT NULL,
    workstation_id BIGINT NOT NULL,
    tool_id        BIGINT NOT NULL,
    quantity       INT DEFAULT 1,
    PRIMARY KEY (wt_id)
);
```

#### 3.2.2 生产执行 (mes_pro_*) — ★核心模块

```sql
-- 工序定义
CREATE TABLE mes_pro_process (
    process_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    process_code    VARCHAR(64) NOT NULL,
    process_name    VARCHAR(128) NOT NULL,
    process_type    VARCHAR(32) COMMENT '类型: 加工/检验/包装/外协',
    standard_time   DECIMAL(10,2) COMMENT '标准工时(分钟)',
    setup_time      DECIMAL(10,2) COMMENT '准备时间(分钟)',
    description     VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (process_id),
    UNIQUE KEY uk_code (process_code, tenant_id)
);

-- 工艺路线
CREATE TABLE mes_pro_route (
    route_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    route_code      VARCHAR(64) NOT NULL,
    route_name      VARCHAR(128) NOT NULL,
    route_version   VARCHAR(16) NOT NULL,
    material_id     BIGINT NOT NULL COMMENT '适用产品',
    status          VARCHAR(16) DEFAULT 'DRAFT',
    effective_date  DATE,
    expiry_date     DATE,
    is_current      CHAR(1) DEFAULT '1' COMMENT '是否当前版本',
    PRIMARY KEY (route_id)
);

-- 工艺路线-工序组成 (有序)
CREATE TABLE mes_pro_route_process (
    rp_id           BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    route_id        BIGINT NOT NULL,
    seq_no          INT NOT NULL COMMENT '工序顺序号',
    process_id      BIGINT NOT NULL,
    process_code    VARCHAR(64) NOT NULL,
    process_name    VARCHAR(128) NOT NULL,
    workstation_id  BIGINT COMMENT '默认工位',
    setup_time      DECIMAL(10,2) COMMENT '准备时间(分钟)',
    process_time    DECIMAL(10,2) COMMENT '单件加工时间(分钟)',
    is_backflush    CHAR(1) DEFAULT '0' COMMENT '是否倒冲投料',
    labor_count     INT DEFAULT 1 COMMENT '标准人力',
    PRIMARY KEY (rp_id),
    INDEX idx_route_seq (route_id, seq_no)
);

-- ========== 流转卡体系 (MES 最核心设计) ==========

-- MES 工单 (关联 ERP prd_work_order)
CREATE TABLE mes_pro_workorder (
    mes_wo_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    erp_wo_id       BIGINT NOT NULL COMMENT '关联ERP工单ID',
    route_id        BIGINT NOT NULL COMMENT '工艺路线ID',
    material_id     BIGINT NOT NULL,
    quantity        DECIMAL(14,4) NOT NULL COMMENT '计划生产数量',
    priority        VARCHAR(16) DEFAULT 'NORMAL',
    planned_start   DATETIME,
    planned_end     DATETIME,
    actual_start    DATETIME,
    actual_end      DATETIME,
    qty_completed   DECIMAL(14,4) DEFAULT 0,
    qty_scrapped    DECIMAL(14,4) DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'CREATED',
    PRIMARY KEY (mes_wo_id),
    INDEX idx_erp_wo (erp_wo_id)
);

-- 流转卡 (工单→分批流转卡)
CREATE TABLE mes_pro_card (
    card_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    card_no         VARCHAR(64) NOT NULL COMMENT '流转卡号(唯一)',
    mes_wo_id       BIGINT NOT NULL COMMENT 'MES工单ID',
    route_id        BIGINT NOT NULL COMMENT '工艺路线ID',
    material_id     BIGINT NOT NULL,
    quantity        DECIMAL(14,4) NOT NULL COMMENT '本卡生产数量',
    priority        VARCHAR(16) DEFAULT 'NORMAL',
    planned_start   DATETIME,
    planned_end     DATETIME,
    actual_start    DATETIME,
    actual_end      DATETIME,
    qty_completed   DECIMAL(14,4) DEFAULT 0,
    qty_scrapped    DECIMAL(14,4) DEFAULT 0,
    batch_id        BIGINT COMMENT '关联批次ID',
    status          VARCHAR(16) DEFAULT 'CREATED',
    PRIMARY KEY (card_id),
    UNIQUE KEY uk_card_no (card_no),
    INDEX idx_mes_wo (mes_wo_id),
    INDEX idx_status (status)
);

-- 流转卡工序记录 (★最核心的MES数据表)
CREATE TABLE mes_pro_card_process (
    ccp_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    card_id         BIGINT NOT NULL COMMENT '关联流转卡',
    rp_id           BIGINT NOT NULL COMMENT '路线工序ID',
    seq_no          INT NOT NULL COMMENT '工序顺序号',
    process_code    VARCHAR(64) COMMENT '工序编码(冗余)',
    process_name    VARCHAR(128) COMMENT '工序名称(冗余)',
    workstation_id  BIGINT COMMENT '实际工位',
    equipment_id    BIGINT COMMENT '实际设备',
    operator_id     BIGINT COMMENT '操作员',
    start_time      DATETIME COMMENT '开始时间',
    end_time        DATETIME COMMENT '结束时间',
    qty_processed   DECIMAL(14,4) DEFAULT 0 COMMENT '加工数量',
    qty_good        DECIMAL(14,4) DEFAULT 0 COMMENT '合格数量',
    qty_scrap       DECIMAL(14,4) DEFAULT 0 COMMENT '报废数量',
    labor_hours     DECIMAL(10,2) COMMENT '人工工时',
    machine_hours   DECIMAL(10,2) COMMENT '机器工时',
    status          VARCHAR(16) DEFAULT 'WAITING' COMMENT 'WAITING/READY/RUNNING/COMPLETED/SKIPPED',
    remark          VARCHAR(512),
    PRIMARY KEY (ccp_id),
    INDEX idx_card (card_id),
    INDEX idx_operator (operator_id),
    INDEX idx_status (status)
);

-- SN级工序追溯 (启用SN时)
CREATE TABLE mes_pro_sn_process (
    snp_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    serial_number   VARCHAR(128) NOT NULL COMMENT '序列号',
    ccp_id          BIGINT NOT NULL COMMENT '关联流转卡工序',
    rp_id           BIGINT NOT NULL COMMENT '路线工序ID',
    operator_id     BIGINT COMMENT '操作员',
    equipment_id    BIGINT COMMENT '设备',
    process_result  VARCHAR(16) COMMENT '工序结果(PASS/FAIL)',
    process_time    DATETIME COMMENT '加工时间',
    PRIMARY KEY (snp_id),
    INDEX idx_sn (serial_number),
    INDEX idx_ccp (ccp_id)
);

-- 派工任务
CREATE TABLE mes_pro_task (
    task_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    mes_wo_id       BIGINT NOT NULL COMMENT 'MES工单ID',
    card_id         BIGINT COMMENT '流转卡ID',
    rp_id           BIGINT COMMENT '路线工序ID',
    workstation_id  BIGINT NOT NULL,
    team_id         BIGINT COMMENT '班组ID',
    assignee_id     BIGINT COMMENT '指派人',
    planned_start   DATETIME,
    planned_end     DATETIME,
    priority        VARCHAR(16) DEFAULT 'NORMAL',
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (task_id)
);

-- 工序报工
CREATE TABLE mes_pro_feedback (
    feedback_id     BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    mes_wo_id       BIGINT NOT NULL,
    card_id         BIGINT,
    ccp_id          BIGINT NOT NULL COMMENT '关联流转卡工序',
    operator_id     BIGINT NOT NULL COMMENT '报工人',
    workstation_id  BIGINT,
    feedback_time   DATETIME NOT NULL,
    qty_good        DECIMAL(14,4) NOT NULL COMMENT '合格数',
    qty_scrap       DECIMAL(14,4) DEFAULT 0 COMMENT '报废数',
    scrap_reason    VARCHAR(256) COMMENT '报废原因',
    labor_hours     DECIMAL(10,2),
    machine_hours   DECIMAL(10,2),
    PRIMARY KEY (feedback_id),
    INDEX idx_ccp (ccp_id)
);

-- 工序转移单
CREATE TABLE mes_pro_trans_order (
    trans_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    trans_no        VARCHAR(64) NOT NULL,
    card_id         BIGINT NOT NULL,
    from_ccp_id     BIGINT COMMENT '来源工序',
    to_ccp_id       BIGINT COMMENT '目标工序',
    from_workstation_id BIGINT,
    to_workstation_id   BIGINT,
    quantity        DECIMAL(14,4) NOT NULL,
    from_operator_id BIGINT,
    to_operator_id  BIGINT,
    trans_time      DATETIME,
    status          VARCHAR(16) DEFAULT 'IN_TRANSIT',
    PRIMARY KEY (trans_id),
    UNIQUE KEY uk_trans_no (trans_no)
);

-- 安灯配置与事件
CREATE TABLE mes_pro_andon_config (
    config_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    andon_type      VARCHAR(64) NOT NULL COMMENT '类型编码',
    andon_name      VARCHAR(128) NOT NULL COMMENT '类型名称(缺料/设备故障/质量异常)',
    priority        VARCHAR(16) DEFAULT 'MEDIUM',
    escalation_timeout INT COMMENT '升级超时(分钟)',
    color_code      VARCHAR(16) COMMENT '颜色(red/orange/yellow/blue)',
    PRIMARY KEY (config_id)
);

CREATE TABLE mes_pro_andon_record (
    andon_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    config_id       BIGINT NOT NULL,
    workstation_id  BIGINT,
    equipment_id    BIGINT,
    card_id         BIGINT COMMENT '关联流转卡',
    ccp_id          BIGINT COMMENT '关联流转卡工序',
    trigger_user_id BIGINT NOT NULL,
    trigger_time    DATETIME NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACKNOWLEDGED/RESOLVED',
    ack_user_id     BIGINT COMMENT '响应人',
    ack_time        DATETIME,
    resolve_user_id BIGINT COMMENT '解决人',
    resolve_time    DATETIME,
    resolution      VARCHAR(512),
    PRIMARY KEY (andon_id),
    INDEX idx_status (status),
    INDEX idx_workstation (workstation_id)
);
```

#### 3.2.3 仓储扩展 (mes_wm_*) — Header-Line-Detail 三级模式

```sql
-- 领料申请
CREATE TABLE mes_wm_material_request (
    mr_id           BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    mr_no           VARCHAR(64) NOT NULL,
    card_id         BIGINT COMMENT '流转卡ID',
    ccp_id          BIGINT COMMENT '流转卡工序ID',
    workstation_id  BIGINT COMMENT '申请工位',
    requested_qty   DECIMAL(14,4),
    issued_qty      DECIMAL(14,4) DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'PENDING',
    request_by      BIGINT,
    PRIMARY KEY (mr_id),
    UNIQUE KEY uk_mr_no (mr_no)
);

-- 调拨单
CREATE TABLE mes_wm_transfer (
    transfer_id     BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    transfer_no     VARCHAR(64) NOT NULL,
    from_warehouse_id BIGINT NOT NULL,
    to_warehouse_id   BIGINT NOT NULL,
    transfer_type   VARCHAR(16) COMMENT 'WH_TO_WH/AREA_TO_AREA/LOC_TO_LOC',
    status          VARCHAR(16) DEFAULT 'CREATED',
    execute_time    DATETIME,
    operator_id     BIGINT,
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_transfer_no (transfer_no)
);

-- 盘点计划/盘点单/差异结果
CREATE TABLE mes_wm_stock_taking_plan (
    plan_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    plan_name       VARCHAR(128) NOT NULL,
    warehouse_id    BIGINT COMMENT '仓库ID(NULL=全仓)',
    plan_date       DATE,
    status          VARCHAR(16) DEFAULT 'CREATED',
    PRIMARY KEY (plan_id)
);

CREATE TABLE mes_wm_stock_taking (
    taking_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    taking_no       VARCHAR(64) NOT NULL,
    plan_id         BIGINT,
    warehouse_id    BIGINT NOT NULL,
    status          VARCHAR(16) DEFAULT 'IN_PROGRESS',
    taker_id        BIGINT,
    taking_date     DATE,
    PRIMARY KEY (taking_id),
    UNIQUE KEY uk_taking_no (taking_no)
);

CREATE TABLE mes_wm_stock_taking_result (
    result_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    taking_id       BIGINT NOT NULL,
    material_id     BIGINT NOT NULL,
    location_id     BIGINT,
    batch_id        BIGINT,
    book_qty        DECIMAL(14,4) COMMENT '账面数量',
    actual_qty      DECIMAL(14,4) COMMENT '实盘数量',
    diff_qty        DECIMAL(14,4) COMMENT '差异数量',
    diff_reason     VARCHAR(256),
    PRIMARY KEY (result_id)
);
```

#### 3.2.4 MES质量控制 (mes_qc_*) — 车间级增强版

```sql
-- 检验指标 (MES增强版，增加SPC相关字段)
CREATE TABLE mes_qc_index (
    index_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    index_code      VARCHAR(64) NOT NULL,
    index_name      VARCHAR(128) NOT NULL,
    index_type      VARCHAR(16) NOT NULL COMMENT '定量/定性',
    unit_id         BIGINT,
    standard_value  VARCHAR(64),
    usl             DECIMAL(14,6) COMMENT '规格上限',
    lsl             DECIMAL(14,6) COMMENT '规格下限',
    spc_chart_type  VARCHAR(16) COMMENT 'SPC图表类型(XBAR_R/XBAR_S/P/NP)',
    PRIMARY KEY (index_id),
    UNIQUE KEY uk_code (index_code, tenant_id)
);

-- 来料检验 (MES增强：关联流转卡)
CREATE TABLE mes_qc_iqc (
    iqc_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    iqc_no          VARCHAR(64) NOT NULL,
    receive_id      BIGINT COMMENT '关联ERP收货单',
    material_id     BIGINT NOT NULL,
    batch_id        BIGINT COMMENT '批次ID',
    quantity        DECIMAL(14,4),
    sample_qty      DECIMAL(14,4),
    defect_qty      DECIMAL(14,4) DEFAULT 0,
    result          VARCHAR(16) COMMENT 'PASS/FAIL/CONCESSION',
    inspector_id    BIGINT,
    inspection_time DATETIME,
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (iqc_id),
    UNIQUE KEY uk_iqc_no (iqc_no)
);

-- 过程检验 (MES增强：关联流转卡工序)
CREATE TABLE mes_qc_ipqc (
    ipqc_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    ipqc_no         VARCHAR(64) NOT NULL,
    card_id         BIGINT COMMENT '关联流转卡',
    ccp_id          BIGINT NOT NULL COMMENT '关联流转卡工序',
    result          VARCHAR(16),
    inspector_id    BIGINT,
    inspection_time DATETIME,
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (ipqc_id),
    UNIQUE KEY uk_ipqc_no (ipqc_no)
);
```

#### 3.2.5 数据采集与SPC (mes_dc_*/mes_spc_*)

```sql
-- 数据采集点
CREATE TABLE mes_dc_point (
    point_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    point_code      VARCHAR(64) NOT NULL,
    point_name      VARCHAR(128) NOT NULL,
    rp_id           BIGINT COMMENT '关联路线工序',
    equipment_id    BIGINT COMMENT '关联设备',
    data_type       VARCHAR(16) NOT NULL COMMENT 'NUMERIC/TEXT/BOOLEAN/ENUM',
    collection_method VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    unit_id         BIGINT,
    is_spc_point    CHAR(1) DEFAULT '0' COMMENT '是否SPC采集点',
    PRIMARY KEY (point_id)
);

-- SPC样本组
CREATE TABLE mes_spc_sample (
    sample_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    rule_id         BIGINT NOT NULL COMMENT 'SPC规则ID',
    sample_time     DATETIME NOT NULL,
    sample_size     INT,
    x_bar           DECIMAL(14,6) COMMENT '均值',
    r               DECIMAL(14,6) COMMENT '极差',
    sigma           DECIMAL(14,6) COMMENT '标准差',
    is_out_control  CHAR(1) DEFAULT '0',
    out_rule        VARCHAR(64) COMMENT '超控规则',
    PRIMARY KEY (sample_id),
    INDEX idx_rule_time (rule_id, sample_time)
);
```

### 3.3 流转卡引擎设计 (MES 核心)

#### 核心流程

```
ERP 工单 (prd_work_order)
    │
    ▼
MES 工单 (mes_pro_workorder)     ← 关联工艺路线(mes_pro_route)
    │
    ├── 路线工序展开: mes_pro_route_process[*] (有序工序列表)
    │
    ├── 生成流转卡: 按批次/数量拆分 → mes_pro_card[*]
    │     ├── 每张卡关联一个批次号
    │     └── 每道工序展开: mes_pro_card_process[*]
    │           ├── 记录: 工位/人员/设备/时间
    │           ├── 记录: 加工量/合格量/报废量
    │           └── (可选) SN级: mes_pro_sn_process[*]
    │
    ├── 工序进行中: 报工、质检、安灯
    │
    └── 末工序完成 → 流转卡关闭 → 完工入库
```

#### 状态机

```
流转卡状态:  CREATED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED
工序状态:    WAITING → READY → RUNNING → COMPLETED (→ SKIPPED)
安灯状态:    ACTIVE → ACKNOWLEDGED → RESOLVED (→ ESCALATED)
```

#### 关键 Service 方法

```java
// MesProCardServiceImpl (流转卡引擎)
public interface IMesProCardService {
    // 根据MES工单生成流转卡 (按工艺路线展开工序)
    List<MesProCard> generateCards(Long mesWoId, int splitCount);
    
    // 下达流转卡
    void releaseCard(Long cardId);
    
    // 开始/完成工序
    void startProcess(Long ccpId, Long operatorId, Long workstationId);
    void completeProcess(Long ccpId, BigDecimal qtyGood, BigDecimal qtyScrap, String remark);
    
    // 工序转移
    void transferProcess(Long fromCcpId, Long toCcpId, BigDecimal quantity, Long transOperatorId);
    
    // 关闭流转卡
    void closeCard(Long cardId);
    
    // 查询流转卡进度
    CardProgressVO getCardProgress(Long cardId);
}
```

### 3.4 MES 实施分阶段

#### MES Phase 1: 基础骨架 + 主数据 （第13-20周，8周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 13 | 创建 ruoyi-mes 模块、配置 ry-mes 数据源、初始化 SQL、mes_md_* 全部表 + CRUD | 模块可编译，MD 可用 |
| 14 | 工序定义 (mes_pro_process) + 工艺路线 (mes_pro_route + route_process)，含版本管理 | 工序/路线管理可用 |
| 15 | MES工单 (mes_pro_workorder) CRUD，关联 ERP 工单 | MES工单基础可用 |
| 16 | 排班日历 (mes_cal_*) 全部表 + CRUD | 排班可用 |
| 17 | 前端: MD (BOM/SOP/SIP/工位资源) + 工序/路线页面 | 主数据可视可用 |
| 18 | 前端: MES工单 + 排班日历页面 | 工单/排班可视 |
| 19 | 联调测试 + Swagger 文档 | — |
| 20 | 缓冲周 | — |
| ★ | 里程碑: MES 基础功能上线 | |

#### MES Phase 2: 流转卡 + 执行核心 （第21-27周，7周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 21 | 流转卡引擎: generateCards()、releaseCard()、状态机 | 流转卡生成可用 |
| 22 | 流转卡工序: startProcess()、completeProcess()、工序报工 | 工序执行可用 |
| 23 | SN 级追溯: ProSnProcess + 序列号绑定 | SN 追溯可用 |
| 24 | 派工任务 (mes_pro_task) + 工序转移 (mes_pro_trans_order) | 派工/转移可用 |
| 25 | 安灯系统: 触发/响应/解决/升级 | 安灯可用 |
| 26 | 前端: 流转卡管理/工序执行/派工看板/安灯看板 | 执行核心可视 |
| 27 | 集成测试 + 联调 | — |
| ★ | 里程碑: MES 执行核心上线 | |

#### MES Phase 3: 仓库/质量/设备/数据采集 （第28-32周，5周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 28 | MES 仓库扩展: 领料/调拨/盘点/委外 (三级单据模式) | 仓库扩展可用 |
| 29 | MES 质量控制: IQC/IPQC/OQC/RQC (车间级完整版) | 质量完整闭环 |
| 30 | MES 设备管理: 点检执行/保养记录/维修工单 | 设备管理完整 |
| 31 | 数据采集 (DC) + SPC 控制图 | DC/SPC 可用 |
| 32 | 前端全部完成 + 全模块集成测试 | — |
| ★ | 里程碑: MES 全部功能上线 | |

---

## 四、Phase 3: IoT模块 (第33-48周)

### 4.1 IoT 架构

```
┌─────────────────────────────────────────────────────────────┐
│                    物理设备层                                 │
│  PLC / 传感器 / 仪表 / 机器人 / AGV / CNC / 扫码枪           │
└────────┬──────────┬──────────┬──────────┬───────────────────┘
         │MQTT      │OPC UA    │Modbus    │HTTP
         ▼          ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────┐
│                   协议适配层 (ruoyi-iot)                      │
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │MQTT      │ │OPC UA    │ │Modbus    │ │HTTP      │       │
│  │Adapter   │ │Adapter   │ │Adapter   │ │Adapter   │       │
│  │(Moquette │ │(Eclipse  │ │(modbus4j)│ │(Spring   │       │
│  │/EMQX)    │ │ Milo)    │ │          │ │ MVC)     │       │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘       │
│       │            │            │            │              │
│       └────────────┴────────────┴────────────┘              │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────┐           │
│  │          消息总线 (RabbitMQ / Kafka)           │           │
│  │  交换: amq.topic  │  路由: iot.data.{type}    │           │
│  └───────────────────┬──────────────────────────┘           │
│                      │                                       │
│       ┌──────────────┼──────────────┐                       │
│       ▼              ▼              ▼                        │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐                   │
│  │规则引擎  │  │数据管道   │  │告警服务   │                   │
│  │Easy-Rules│  │清洗→聚合  │  │阈值/趋势  │                   │
│  └────┬────┘  └────┬─────┘  └────┬─────┘                   │
│       │            │            │                            │
│       ▼            ▼            ▼                            │
│  ┌──────────────────────────────────────────────┐           │
│  │              数据存储层                        │           │
│  │  MySQL (ry-iot)  +  TDengine/InfluxDB (时序)  │           │
│  │  Redis (实时缓存 + WebSocket 推送)            │           │
│  └──────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 IoT 模块包结构

```
ruoyi-modules/ruoyi-iot/
  pom.xml
  src/main/java/org/dromara/iot/
    ├── config/
    │   ├── IotAutoConfiguration.java
    │   ├── MqttBrokerConfig.java
    │   ├── OpcUaConfig.java
    │   └── ModbusConfig.java
    ├── common/
    │   ├── enums/           # ProtocolType/DeviceStatus/AlertLevel
    │   └── dto/             # 协议消息DTO
    ├── device/              # 设备管理
    │   ├── controller/
    │   │   ├── IotDeviceController.java
    │   │   ├── IotDeviceGroupController.java
    │   │   ├── IotDeviceTagController.java
    │   │   ├── IotProductController.java
    │   │   ├── IotGatewayController.java
    │   │   └── IotFirmwareController.java
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   └── service/impl/
    ├── protocol/            # 协议适配
    │   ├── mqtt/
    │   │   ├── MqttBrokerService.java
    │   │   ├── MqttMessageHandler.java
    │   │   └── MqttAuthService.java
    │   ├── opcua/
    │   │   ├── OpcUaClientManager.java
    │   │   ├── OpcUaSubscriptionService.java
    │   │   └── OpcUaNodeReader.java
    │   ├── modbus/
    │   │   ├── ModbusTcpMaster.java
    │   │   ├── ModbusRtuMaster.java
    │   │   └── ModbusPollingScheduler.java
    │   └── http/
    │       └── DeviceDataController.java  # POST /api/v1/device/{code}/datapoint
    ├── data/                # 数据处理
    │   ├── pipeline/
    │   │   ├── DataIngestionService.java    # 数据接入
    │   │   ├── DataCleaningService.java     # 数据清洗
    │   │   ├── DataAggregationService.java  # 数据聚合
    │   │   └── DataForwardService.java      # 数据转发(→MQ)
    │   ├── tsdb/
    │   │   ├── TsdbService.java             # 时序数据库接口
    │   │   └── TsdbInfluxImpl.java          # InfluxDB实现
    │   └── realtime/
    │       ├── RealtimeDataCache.java       # Redis实时缓存
    │       └── WebSocketPushService.java    # WebSocket推送
    ├── rule/                # 规则引擎
    │   ├── RuleEngineService.java
    │   ├── RuleDefinitionLoader.java
    │   └── rules/           # 规则定义
    │       ├── ThresholdRule.java
    │       ├── TrendRule.java
    │       └── MissingDataRule.java
    ├── alert/               # 告警管理
    │   ├── controller/
    │   │   ├── IotAlertRuleController.java
    │   │   └── IotAlertLogController.java
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   ├── service/impl/
    │   └── notify/          # 通知渠道
    │       ├── SystemNotifier.java
    │       ├── EmailNotifier.java
    │       └── WebhookNotifier.java
    ├── scene/               # 场景联动
    │   ├── controller/IotSceneController.java
    │   ├── domain/ (+bo/vo)
    │   ├── mapper/
    │   ├── service/impl/
    │   └── executor/SceneActionExecutor.java
    └── gateway/             # 边缘网关
        ├── controller/IotGatewayController.java
        ├── domain/ (+bo/vo)
        ├── mapper/
        ├── service/impl/
        └── edge/
            ├── EdgeScriptEngine.java       # 边缘脚本(Groovy)
            └── EdgeDataBuffer.java         # 边缘数据缓冲
```

### 4.3 核心数据库设计 (ry-iot, iot_ 前缀)

```sql
-- ========== 设备管理 ==========

-- 产品/物模型
CREATE TABLE iot_product (
    product_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    product_code    VARCHAR(64) NOT NULL,
    product_name    VARCHAR(128) NOT NULL,
    protocol_type   VARCHAR(16) NOT NULL COMMENT 'MQTT/OPCUA/MODBUS/HTTP',
    node_type       VARCHAR(16) DEFAULT 'DEVICE' COMMENT 'DEVICE/GATEWAY',
    description     VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    create_time     DATETIME,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_code (product_code, tenant_id)
);

-- 产品属性定义 (物模型)
CREATE TABLE iot_product_property (
    property_id     BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    property_code   VARCHAR(64) NOT NULL COMMENT '属性标识符',
    property_name   VARCHAR(128) NOT NULL COMMENT '属性名称',
    data_type       VARCHAR(16) NOT NULL COMMENT 'INT/FLOAT/STRING/BOOLEAN/ENUM',
    unit            VARCHAR(32),
    access_mode     VARCHAR(16) DEFAULT 'RW' COMMENT 'R/RW',
    description     VARCHAR(256),
    PRIMARY KEY (property_id),
    UNIQUE KEY uk_prod_code (product_id, property_code)
);

-- 设备主表
CREATE TABLE iot_device (
    device_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_code     VARCHAR(64) NOT NULL COMMENT '设备编码',
    device_name     VARCHAR(128) NOT NULL COMMENT '设备名称',
    product_id      BIGINT NOT NULL COMMENT '产品ID',
    group_id        BIGINT COMMENT '分组ID',
    protocol_type   VARCHAR(16) NOT NULL COMMENT '通信协议',
    protocol_config TEXT COMMENT '协议连接参数(JSON)',
    device_key      VARCHAR(128) COMMENT '设备密钥(MQTT认证)',
    device_secret   VARCHAR(128) COMMENT '设备密钥',
    online_status   VARCHAR(16) DEFAULT 'OFFLINE',
    last_online_time DATETIME,
    location        VARCHAR(256) COMMENT '安装位置',
    mes_workstation_id BIGINT COMMENT '关联MES工位(可选)',
    mes_equipment_id   BIGINT COMMENT '关联MES设备(可选)',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    create_time     DATETIME,
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_code (device_code, tenant_id),
    INDEX idx_product (product_id),
    INDEX idx_online (online_status)
);

-- 设备分组
CREATE TABLE iot_device_group (
    group_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    parent_id       BIGINT COMMENT '父分组ID',
    group_name      VARCHAR(128) NOT NULL,
    group_type      VARCHAR(32) COMMENT '按类型/位置/产线/客户',
    PRIMARY KEY (group_id)
);

-- 设备标签
CREATE TABLE iot_device_tag (
    tag_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    tag_key         VARCHAR(64) NOT NULL,
    tag_value       VARCHAR(256) NOT NULL,
    PRIMARY KEY (tag_id),
    INDEX idx_device (device_id)
);

-- ========== 数据存储 ==========

-- 设备原始数据
CREATE TABLE iot_device_data_raw (
    data_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    device_code     VARCHAR(64) NOT NULL,
    property_code   VARCHAR(64) NOT NULL COMMENT '属性标识符',
    data_value      TEXT COMMENT '原始值',
    data_type       VARCHAR(16),
    report_time     DATETIME NOT NULL COMMENT '设备上报时间',
    receive_time    DATETIME NOT NULL COMMENT '服务端接收时间',
    quality         VARCHAR(16) DEFAULT 'GOOD' COMMENT '数据质量',
    PRIMARY KEY (data_id),
    INDEX idx_device_time (device_id, report_time)
) COMMENT='设备原始数据(可定期归档)';

-- 处理后的数据
CREATE TABLE iot_device_data_processed (
    data_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    property_code   VARCHAR(64) NOT NULL,
    data_value      DECIMAL(14,6) COMMENT '清洗后的数值',
    unit            VARCHAR(32),
    quality         VARCHAR(16),
    report_time     DATETIME NOT NULL,
    process_time    DATETIME NOT NULL,
    PRIMARY KEY (data_id),
    INDEX idx_device_time (device_id, report_time)
) COMMENT='处理后数据(可转发至时序数据库)';

-- ========== 协议配置 ==========

CREATE TABLE iot_protocol_config (
    config_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    protocol_type   VARCHAR(16) NOT NULL COMMENT 'MQTT/OPCUA/MODBUS/HTTP',
    config_name     VARCHAR(128) NOT NULL,
    endpoint_url    VARCHAR(512) COMMENT '连接地址',
    auth_config     TEXT COMMENT '认证配置(JSON)',
    poll_interval   INT COMMENT '轮询间隔(ms)',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (config_id)
);

-- 协议数据映射 (原始字段 → 物模型属性)
CREATE TABLE iot_protocol_mapping (
    mapping_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    property_id     BIGINT NOT NULL,
    raw_address     VARCHAR(256) NOT NULL COMMENT '原始地址(MQTT主题/MODBUS寄存器/OPC UA NodeId)',
    data_transform  VARCHAR(64) COMMENT '数据转换表达式(倍率/偏移)',
    PRIMARY KEY (mapping_id)
);

-- ========== 规则引擎 ==========

CREATE TABLE iot_rule (
    rule_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    rule_code       VARCHAR(64) NOT NULL,
    rule_name       VARCHAR(128) NOT NULL,
    device_id       BIGINT COMMENT 'NULL=全局规则',
    property_code   VARCHAR(64) NOT NULL,
    rule_type       VARCHAR(16) NOT NULL COMMENT 'THRESHOLD/TREND/MISSING/EXPRESSION',
    rule_config     TEXT NOT NULL COMMENT '规则配置(JSON)',
    action_type     VARCHAR(16) NOT NULL COMMENT 'ALERT/FORWARD/COMMAND/WEBHOOK',
    action_config   TEXT COMMENT '动作配置(JSON)',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_code (rule_code, tenant_id)
);

-- ========== 告警 ==========

CREATE TABLE iot_alert_rule (
    alert_rule_id   BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    rule_name       VARCHAR(128) NOT NULL,
    device_id       BIGINT,
    property_code   VARCHAR(64) NOT NULL,
    operator        VARCHAR(16) NOT NULL COMMENT 'GT/GTE/LT/LTE/EQ/NEQ/BETWEEN',
    threshold       VARCHAR(128) COMMENT '阈值(支持多值)',
    alert_level     VARCHAR(16) DEFAULT 'WARNING' COMMENT 'INFO/WARNING/CRITICAL/EMERGENCY',
    cooldown_sec    INT DEFAULT 300 COMMENT '冷却时间(秒)',
    notify_channels VARCHAR(256) COMMENT '通知渠道(SYSTEM/EMAIL/SMS/WEBHOOK)',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (alert_rule_id)
);

CREATE TABLE iot_alert_log (
    alert_log_id    BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    alert_rule_id   BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    alert_level     VARCHAR(16) NOT NULL,
    alert_message   VARCHAR(512),
    current_value   VARCHAR(128),
    threshold_value VARCHAR(128),
    trigger_time    DATETIME NOT NULL,
    ack_time        DATETIME,
    ack_user_id     BIGINT,
    resolve_time    DATETIME,
    resolve_note    VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACKED/RESOLVED',
    PRIMARY KEY (alert_log_id),
    INDEX idx_device_time (device_id, trigger_time),
    INDEX idx_status (status)
);

-- ========== 边缘网关 ==========

CREATE TABLE iot_gateway (
    gateway_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    gateway_code    VARCHAR(64) NOT NULL,
    gateway_name    VARCHAR(128) NOT NULL,
    gateway_type    VARCHAR(32) COMMENT '硬件型号',
    firmware_version VARCHAR(32),
    online_status   VARCHAR(16) DEFAULT 'OFFLINE',
    last_heartbeat  DATETIME,
    edge_script_id  BIGINT COMMENT '当前运行脚本',
    location        VARCHAR(256),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (gateway_id),
    UNIQUE KEY uk_code (gateway_code, tenant_id)
);

CREATE TABLE iot_edge_script (
    script_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    script_name     VARCHAR(128) NOT NULL,
    script_content  TEXT NOT NULL COMMENT '脚本内容(Groovy/JavaScript)',
    script_version  VARCHAR(32),
    status          VARCHAR(16) DEFAULT 'DRAFT',
    PRIMARY KEY (script_id)
);

-- ========== 场景联动 ==========

CREATE TABLE iot_scene (
    scene_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    scene_code      VARCHAR(64) NOT NULL,
    scene_name      VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (scene_id)
);

CREATE TABLE iot_scene_trigger (
    trigger_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    scene_id        BIGINT NOT NULL,
    trigger_type    VARCHAR(16) NOT NULL COMMENT 'DEVICE_DATA/TIMER/ALERT/MANUAL',
    trigger_config  TEXT NOT NULL COMMENT '触发器配置(JSON)',
    PRIMARY KEY (trigger_id),
    INDEX idx_scene (scene_id)
);

CREATE TABLE iot_scene_action (
    action_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    scene_id        BIGINT NOT NULL,
    seq_no          INT NOT NULL COMMENT '执行顺序',
    action_type     VARCHAR(16) NOT NULL COMMENT 'SET_PROPERTY/CALL_SERVICE/SEND_ALERT/WEBHOOK',
    action_config   TEXT NOT NULL COMMENT '动作配置(JSON)',
    PRIMARY KEY (action_id),
    INDEX idx_scene_seq (scene_id, seq_no)
);
```

### 4.4 协议适配详细设计

#### 4.4.1 MQTT (优先级最高)

采用嵌入式 MQTT Broker (Moquette) + 可选外部 EMQX:

```
MQTT 主题结构:
  iot/{tenant_code}/{product_code}/{device_code}/property/post     — 设备属性上报
  iot/{tenant_code}/{product_code}/{device_code}/property/set      — 属性设置(下行)
  iot/{tenant_code}/{product_code}/{device_code}/event/post        — 事件上报
  iot/{tenant_code}/{product_code}/{device_code}/command/get        — 指令下发
  iot/{tenant_code}/{product_code}/{device_code}/ota/request        — OTA请求

认证方式:
  - DeviceKey + DeviceSecret HMAC-SHA256
  - 支持 ACL 权限控制 (设备只能访问自己的主题)
```

#### 4.4.2 OPC UA

基于 Eclipse Milo SDK:

```java
// OpcUaClientManager 管理所有 OPC UA 连接
public class OpcUaClientManager {
    // 连接池: deviceId → OpcUaClient
    private Map<Long, OpcUaClient> clientCache;
    
    // 订阅管理: 为每个设备创建 MonitoredItem 订阅
    void subscribe(IotDevice device, List<NodeMapping> mappings);
    
    // 自动重连: 检测断连 → 退避重试 → 恢复订阅
    void autoReconnect(IotDevice device);
}

// 节点映射配置存储在 iot_protocol_mapping 表中:
// - raw_address: "ns=2;s=Temperature"
// - property_code: "temperature"
// - data_transform: "x * 0.1"  (倍率转换)
```

#### 4.4.3 Modbus

```java
// ModbusPollingScheduler 轮询调度
public class ModbusPollingScheduler {
    // 按设备分组轮询，避免阻塞
    // Modbus TCP: 以太网直连
    // Modbus RTU: 通过串口服务器 → TCP 桥接
    
    // 寄存器映射:
    // - raw_address: "HOLDING_REGISTER:40001"
    // - property_code: "temperature"
    // - data_transform: "x / 10.0"
    void pollDevice(IotDevice device, List<ModbusMapping> mappings);
}
```

#### 4.4.4 HTTP

简单的 RESTful 数据上报:

```
POST /api/v1/device/{deviceCode}/datapoint
Headers:
  Authorization: Bearer {deviceToken}
Body (JSON):
{
  "properties": [
    {"code": "temperature", "value": 25.5, "time": "2026-05-24T10:00:00Z"},
    {"code": "humidity", "value": 60.2, "time": "2026-05-24T10:00:00Z"}
  ],
  "events": [
    {"code": "alarm", "value": "overheat", "time": "2026-05-24T10:00:00Z"}
  ]
}
```

### 4.5 数据流与 MES 集成

```
设备数据 → 协议适配层
  ├→ 原始数据写入 iot_device_data_raw (MySQL, 可归档)
  ├→ 规则引擎处理
  │     ├→ 数据清洗 (去噪/补全/转换)
  │     ├→ 数据聚合 (每分钟/小时/天)
  │     └→ 告警匹配 (阈值/趋势/缺失)
  ├→ 处理后数据写入 iot_device_data_processed (MySQL)
  ├→ 时序数据写入 TDengine/InfluxDB (长期存储)
  ├→ 实时数据写入 Redis (ZSET按时间排序)
  ├→ WebSocket 推送前端实时展示
  └→ 通过 RabbitMQ 发送到 MES 队列
        └→ MES 消费处理:
              ├→ 自动报工 (设备计数 → 工序产量)
              ├→ 质量触发 (设备参数异常 → IPQC)
              ├→ 设备状态更新 (运行/停机/故障)
              └→ 安灯触发 (设备故障 → 安灯事件)
```

### 4.6 IoT 实施分阶段

#### IoT Phase 1: 基础设施 + 设备管理 （第33-37周，5周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 33 | 创建 ruoyi-iot 模块、配置 ry-iot 数据源、初始化 SQL、iot_device + iot_product 核心表 + CRUD | 模块可编译，设备管理可用 |
| 34 | MQTT Broker 集成 (Moquette) + 设备认证 + 设备注册/密钥管理 | MQTT 连接可用 |
| 35 | HTTP 数据上报端点 + 设备在线状态管理 (心跳检测) | HTTP 上报可用 |
| 36 | 设备分组/标签 + 设备日志 + 物模型 (产品→属性) | 设备管理完整 |
| 37 | 前端: 设备管理/分组/产品/物模型页面 | 前端可视可用 |
| ★ | 里程碑: IoT 基础平台上线，设备可注册连接 | |

#### IoT Phase 2: 协议 + 数据管道 （第38-42周，5周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 38 | OPC UA 客户端 (Eclipse Milo): 连接/浏览/订阅/重连 | OPC UA 可用 |
| 39 | Modbus 驱动: TCP/RTU + 轮询调度 + 寄存器映射 | Modbus 可用 |
| 40 | 数据管道: 清洗→聚合→转发 (DataPipelineService) | 数据管道可用 |
| 41 | 时序数据库集成 (InfluxDB) + Redis 实时缓存 + WebSocket 推送 | 实时数据可用 |
| 42 | 前端: 实时数据页面 (ECharts) + 协议配置页面 | 数据可视可用 |
| ★ | 里程碑: 多协议数据采集完成 | |

#### IoT Phase 3: 规则 + 告警 + 场景联动 + 边缘 （第43-48周，6周）

| 周次 | 内容 | 产出 |
|------|------|------|
| 43 | 规则引擎 (Easy-Rules): 阈值/趋势/表达式规则 | 规则引擎可用 |
| 44 | 告警系统: 规则→触发→通知 (系统/邮件/Webhook) + 告警升级 | 告警完整闭环 |
| 45 | 边缘网关管理 + 边缘脚本引擎 (Groovy) + 数据缓冲 | 边缘计算可用 |
| 46 | 场景联动: 触发器→条件→动作 + 固件OTA | 场景联动+OTA可用 |
| 47 | IoT ↔ MQ ↔ MES 全链路集成: 设备数据→MES联动 (自动报工/质量触发) | 端到端集成 |
| 48 | 整体测试 + IoT大屏看板 + 文档 | 全部上线 |
| ★ | 里程碑: IoT 全部功能上线 | |

---

## 五、总体时间线与里程碑

```
Month 1   Week 1-4    ERP: P0清零 + 采购闭环 + 仓储策略
Month 2   Week 5-8    ERP: 委外/返工/倒冲/产能 + 质量管理(IQC/IPQC)
Month 3   Week 9-12   ERP: 设备管理 + 批次/SN追溯 + ERP交付
  ★★★★★ 里程碑: ERP 全部完成 ★★★★★

Month 4   Week 13-16  MES Phase 1: 模块骨架 + MD主数据 + 工序/路线
Month 5   Week 17-20  MES Phase 1: MES工单 + 排班 + 前端 + 联调
  ★★★★★ 里程碑: MES 基础功能上线 ★★★★★

Month 6   Week 21-24  MES Phase 2: 流转卡引擎 + 工序执行 + SN追溯
Month 7   Week 25-28  MES Phase 2: 派工/安灯/报工 + MES仓库扩展
  ★★★★★ 里程碑: MES 执行核心上线 ★★★★★

Month 8   Week 29-32  MES Phase 3: 质量/设备/数据采集/SPC + 全模块交付
  ★★★★★ 里程碑: MES 全部功能上线 ★★★★★

Month 9   Week 33-36  IoT Phase 1: 模块骨架 + 设备管理 + MQTT + HTTP
Month 10  Week 37-40  IoT Phase 1/2: 前端 + OPC UA + Modbus + 数据管道
  ★★★★★ 里程碑: IoT 多协议数据采集完成 ★★★★★

Month 11  Week 41-44  IoT Phase 2/3: 时序数据库 + 规则引擎 + 告警系统
Month 12  Week 45-48  IoT Phase 3: 边缘计算 + 场景联动 + 全链路集成
  ★★★★★ 里程碑: NexusERP (ERP + MES + IoT) 完整交付 ★★★★★
```

---

## 六、技术架构决策

### 6.1 关键设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| MES 数据库 | 独立 ry-mes | 与 ERP 解耦，支持独立部署和扩展 |
| IoT 数据库 | 独立 ry-iot + TDengine | 时序数据走时序库，业务数据走 MySQL |
| MES 工单 | 引用 ERP 工单 ID，不重复建表 | 避免数据冗余，保持 ERP 为单一数据源 |
| MES 主数据 | 引用 ERP 主数据 ID，扩展 MES 特有字段 | 物料/供应商/客户等基础数据不重复维护 |
| 审批流 | 复用已有 Flowable 集成 | 所有模块均已完成 Flowable 接入 |
| ERP qc/eq | 轻量版，MES 做车间级完整版 | 避免 ERP 过度复杂，MES 专注车间执行 |
| 前端 | Vue 3 + Element Plus + TypeScript 重写 | 不迁就 ktg-mes-ui 的 Vue 2 代码 |
| 流转卡工作流 | 状态机内嵌 (非 Flowable) | 流转卡工序流转是简单线性状态，无需工作流引擎 |
| ID 策略 | 全局 ASSIGN_ID (Snowflake) | 遵循 nexus-erp 规范 |
| 多租户 | 所有 MES/IoT 表增加 tenant_id | 遵循 nexus-erp 多租户特性 |

### 6.2 与 ktg-mes 的关键差异

| 维度 | ktg-mes | nexus-erp MES | 理由 |
|------|---------|---------------|------|
| 框架 | RuoYi-Vue (Java 8) | RuoYi-Vue-Plus (Java 17) | Plus 版架构更先进 |
| ORM | MyBatis + XML Mapper | MyBatis-Plus + Lambda | 减少 XML 维护 |
| 前端 | Vue 2 + Element UI + JS | Vue 3.5 + Element Plus + TS | 技术栈升级 |
| 排班 | 自成 cal 子模块 | 复用 erp/sch 模块 | 已有完整排班考勤 |
| 报表 | UReport2 | 复用 nexus-erp 方案 | UReport2 已停更 |
| API 加密 | 无 | AES+RSA 动态加密 | nexus-erp 默认开启 |
| 数据源 | Druid | HikariCP | 遵循 nexus-erp |
| 分布式锁 | 无 | Lock4j (Redisson) | 防止并发问题 |
| cal 模块 | 独立实现 | 复用已有 sch | 避免功能重复 |

---

## 七、数据库规划

### 7.1 数据库分布

| 数据库 | 用途 | 预计表数 | 数据源注解 |
|--------|------|---------|-----------|
| ry-vue | 系统管理 (用户/角色/菜单/部门/字典/租户) | ~50 (已有) | master (默认) |
| ry-erp | ERP 业务 | ~75 (已有~65 + 新增~10) | @DS("erp") |
| ry-mes | MES 业务 | ~60 | @DS("mes") [新] |
| ry-iot | IoT 业务 (设备/规则/告警) | ~20 | @DS("iot") [新] |
| TDengine/InfluxDB | IoT 时序数据 | 按设备动态 | — [新] |

### 7.2 数据源配置 (application-dev.yml)

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:  # 默认 - ry-vue
          type: ${spring.datasource.type}
          driverClassName: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://localhost:3306/ry-vue?useUnicode=true&...
          username: root
          password: root
        erp:     # ERP 业务
          url: jdbc:mysql://localhost:3306/ry-erp?...
        mes:     # MES 业务 [新]
          url: jdbc:mysql://localhost:3306/ry-mes?...
        iot:     # IoT 业务 [新]
          url: jdbc:mysql://localhost:3306/ry-iot?...
```

### 7.3 SQL 脚本组织

```
script/sql/
  ry-erp.sql              ERP DDL (已有)
  ry-erp-phase2.sql       新增表: qc_*/eq_*/bd_serial_number_rule 等
  ry-mes.sql              全部 MES DDL (mes_* 表)
  ry-iot.sql              全部 IoT DDL (iot_* 表)
  update/                 增量更新脚本
```

---

## 八、前端规划

### 8.1 目录结构 (plus-ui)

```
plus-ui/src/
  api/
    erp/                   ERP API (已有 + 扩展)
      qc/                  ERP 质量 API
      eq/                  ERP 设备 API
    mes/                   MES API [新]
      md/                  主数据 API
      pro/                 生产执行 API (含流转卡/派工/报工/安灯)
      wm/                  仓储扩展 API
      qc/                  质量控制 API (车间级)
      eq/                  设备管理 API (车间级)
      dc/                  数据采集 API
      spc/                 SPC API
    iot/                   IoT API [新]
      device/              设备管理 API
      product/             产品/物模型 API
      protocol/            协议配置 API
      rule/                规则引擎 API
      alert/               告警 API
      scene/               场景联动 API
      gateway/             边缘网关 API

  views/
    erp/qc/                ERP 质量页面 (~5个)
    erp/eq/                ERP 设备页面 (~5个)
    mes/                   MES 页面 [新] (~40个)
      md/                  BOM/SOP/SIP/工位资源
      pro/                 工序/路线/工单/流转卡/派工/报工/安灯
      wm/                  领料/调拨/盘点/委外
      qc/                  IQC/IPQC/OQC/RQC/缺陷
      eq/                  台账/点检/保养/维修
      dc/                  采集点/实时数据
      spc/                 SPC规则/控制图
    iot/                   IoT 页面 [新] (~20个)
      device/              设备/分组/标签
      product/             产品/物模型
      protocol/            协议配置/数据映射
      data/                实时数据/历史趋势
      rule/                规则配置
      alert/               告警规则/告警日志
      scene/               场景联动
      gateway/             边缘网关/脚本管理

  components/
    mes/                   MES 专用组件 [新]
      WorkstationSelect.vue      工位选择器
      RouteSelect.vue            工艺路线选择器
      CardSelect.vue             流转卡选择器
      ProcessSelect.vue          工序选择器
      ProCardProgress.vue        流转卡工序进度条
      GanttSchedule.vue          甘特排程 (dhtmlx-gantt/planby)
    iot/                   IoT 专用组件 [新]
      DeviceSelect.vue           设备选择器
      RealtimeMonitor.vue        实时数据监控
      AlertBadge.vue             告警角标
      DeviceStatusIcon.vue       设备状态图标
```

### 8.2 页面数量估算

| 模块 | 页面数 | 说明 |
|------|--------|------|
| ERP 新增 (qc+eq+追溯) | ~15 | 质量/设备/追溯 |
| MES | ~40 | MD(5) + PRO(12) + WM(8) + QC(6) + EQ(5) + DC(2) + SPC(2) |
| IoT | ~20 | Device(5) + Product(3) + Protocol(3) + Data(3) + Rule(2) + Alert(2) + Scene(1) + Gateway(1) |
| **合计新增** | **~75** | |

### 8.3 关键前端技术选型

| 需求 | 技术 |
|------|------|
| 表格 | vxe-table (与 nexus-erp 一致) |
| 图表 | ECharts 5.x |
| 甘特排程 | vis-timeline (开源) 或 dhtmlx-gantt (商业) |
| SPC 控制图 | ECharts 自定义 (Xbar-R/Xbar-S/P/NP 图) |
| 实时数据 | WebSocket + ECharts 动态刷新 |
| 大屏看板 | 独立页面 + 全屏 + CSS Grid + ECharts |
| 条码标签打印 | vue-plugin-hiprint |
| 3D 设备模型 | Three.js (IoT 设备可视化，可选) |

---

## 九、风险分析与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| **ERP 工期拖延影响 MES 启动** | 高 | 中 | ERP P3 任务（财务/报表）可延后至 MES 并行开发 |
| **ktg-mes 代码迁移工作量大** | 高 | 高 | 只参考设计模式，不直接迁移代码。使用 nexus-erp 分层规范重写 |
| **流转卡引擎性能** | 中 | 中 | 大批量 SN 时使用批量 INSERT + 异步生成，避免同步阻塞 |
| **OPC UA 连接稳定性** | 中 | 中 | 实现指数退避重连 + 断点数据缓存 + 心跳检测 |
| **跨库事务一致性** | 高 | 高 | ERP/MES 跨库采用最终一致性 + 补偿操作；IoT/MES 通过 MQ 异步解耦 |
| **时序数据库选型** | 中 | 低 | 开发期使用 InfluxDB 2.x (Docker)，生产环境可切换 TDengine |
| **前端开发量大 (~75 页面)** | 中 | 中 | 使用 nexus-erp 现有代码生成器 + 列表页模板加速 |
| **MQTT Broker 运维** | 中 | 中 | 开发期嵌入 Moquette，生产环境建议独立部署 EMQX |
| **多租户数据隔离** | 高 | 低 | 所有 DDL 包含 tenant_id，MyBatis-Plus 多租户插件自动注入 |
| **IoT 安全** | 高 | 中 | 设备认证 + ACL + 数据加密 + API 限流 |

---

## 十、参考资源索引

### 10.1 本地参考项目

| 编号 | 路径 | 用途 |
|------|------|------|
| N1 | `D:\workspace\nexus-erp\ruoyi-modules\ruoyi-erp\` | ERP 模块结构/代码模板 |
| N2 | `D:\workspace\nexus-erp\ruoyi-modules\ruoyi-erp\pom.xml` | 模块 POM 依赖模板 |
| N3 | `D:\workspace\nexus-erp\ruoyi-admin\src\main\resources\application-dev.yml` | 数据源配置模板 |
| N4 | `D:\workspace\nexus-erp\script\sql\ry-erp.sql` | DDL 规范 |
| N5 | `D:\workspace\nexus-erp\plus-ui\src\` | 前端代码模板 |
| K1 | `D:\ktg-mes\` | MES 后端参考 (业务逻辑/表结构) |
| K2 | `D:\ktg-mes-ui\` | MES 前端参考 (UI布局/交互流程) |
| K3 | `D:\ktg-mes\doc\设计文档\数据库设计\` | 数据库设计文档 (8 个 SQL 文件) |

### 10.2 关键设计文件

| 编号 | 文件 | 用途 |
|------|------|------|
| D1 | `script/ERP剩余功能开发计划清单.md` | ERP 剩余任务基线 |
| D2 | `script/ERP完成度分析与MES设计方案.md` | ERP 完成度 + MES 详细设计 |
| D3 | ktg-mes `mes-pro.sql` | 生产模块表结构 (工单/路线/流转卡) |
| D4 | ktg-mes `mes-wm.sql` | 仓储模块表结构 (三级单据模式) |
| D5 | ktg-mes `mes-qc.sql` | 质量模块表结构 (IQC/IPQC/OQC) |
| D6 | ktg-mes `mes-dv.sql` | 设备模块表结构 |
| D7 | ktg-mes `ProCardServiceImpl.java` | 流转卡核心逻辑 |
| D8 | ktg-mes `StorageCoreServiceImpl.java` | 库存核心引擎 |

---

> **文档版本**: v1.0
> **规划日期**: 2026-05-24
> **作者**: Claude Opus 4.7
> **总工期**: 48 周 (约 12 个月)
> **参考项目**: nexus-erp (RuoYi-Vue-Plus 5.6.0) + ktg-mes 3.8.2 (苦糖果MES)
