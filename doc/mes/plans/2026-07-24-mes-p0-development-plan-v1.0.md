# NexusERP MES 系统 P0 级别功能开发计划书 v1.0

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **文档版本**: v1.0
> **编制日期**: 2026-07-24
> **编制依据**: [NexusERP_MES文档.md](file:///d:/workspace/nexus-erp/doc/mes/Nesu… 以下简称"主文档") + [mes-completion-linkage-analysis.md](file:///d:/workspace/nexus-erp/doc/system/mes-completion-linkage-analysis.md) + 主流 MES 对标报告（MESA-11、Siemens Opcenter 2604、鼎捷数智、用友 YonBIP）
> **状态**: 待评审 → 待用户批准 → 进入执行

---

## 目录

- [一、项目概述](#一项目概述)
- [二、P0 功能需求清单](#二p0-功能需求清单)
- [三、功能模块详细说明](#三功能模块详细说明)
- [四、技术实现方案](#四技术实现方案)
- [五、开发优先级与实施步骤](#五开发优先级与实施步骤)
- [六、资源分配](#六资源分配)
- [七、时间节点](#七时间节点)
- [八、质量标准](#八质量标准)
- [九、验收标准](#九验收标准)
- [十、风险评估与应对策略](#十风险评估与应对策略)
- [十一、测试计划](#十一测试计划)
- [十二、交付物清单](#十二交付物清单)
- [十三、附录](#十三附录)

---

## 一、项目概述

### 1.1 项目背景

[NexusERP MES 模块](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes) 经过 Week 17-29 共 13 周的迭代，已完成"通用 MES 基础框架"建设：

- **已落地**：[pro/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro)（工单/流转卡/派工/SN/安灯/MBOM）、[qc/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc)（三检）、[eq/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq)（设备 8 子模块）、[dc/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc)（数据采集）、[spc/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc)（SPC）、[common/](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common)（ERP/MQ 桥接）共 6 子模块、约 167 个 Java 文件、35 个前端页面、28 张数据库表。

- **完成度评估**：经 2026-07-24 主流 MES 系统对标分析（MESA-11 国际标准、Siemens Opcenter Execution Electronics 2604、鼎捷数智、华天、用友 YonBIP、智邦国际），综合完成度约 **45-50%**，距主流商用 MES 仍有约 50% 差距，共识别 **37 项功能差距**，其中 **P0 级（必做，影响可用性）9 项、P1 级 14 项、P2 级 11 项**。

### 1.2 项目目标

本项目聚焦 P0 级 9 项功能开发，目标：

| 维度 | 当前 | 目标 |
|------|------|------|
| MESA-11 功能覆盖 | 5/11 项 | **9/11 项**（覆盖核心生产管控+质量+追溯） |
| 综合完成度 | 45-50% | **70-75%**（达到主流商用 MES 基础水平） |
| 单元测试覆盖率 | <10% | **≥60%**（核心模块） |
| 数据采集自动化 | 0% | **IoT 自动采集打通**（覆盖 3 类设备数据） |
| 质量闭环 | 三检+SPC | **NCR+返工+电子签名** 完整闭环 |

### 1.3 项目范围

**包含**（9 项 P0 功能）：
1. P0-1 物料拉动配送 + 齐套管理
2. P0-2 工装/模治具管理（独立模块）
3. P0-3 任务进站/出站 + 流程指引
4. P0-4 NCR 不合格品 + 返工流程
5. P0-5 E-SOP 电子作业指导书
6. P0-6 电子签名 + DHR 批记录
7. P0-7 核心模块单元测试补齐
8. P0-8 IoT 自动采集打通
9. P0-9 批次（Lot）管理

**不包含**（P1/P2 共 25 项延后，详见对标报告）：
- APS 排产、WIP 独立库存表、过点管理、数字孪生、Cpk/RFT、业财一体化、ECN 变更、多工厂协同、AI Copilot、低代码平台、PDA 移动端、AI 视觉质检、能耗管理、行业包、MSA/FMEA/PPAP 等

### 1.4 项目周期

- **总工期**：12 周（含 1 周综合验收缓冲）
- **起止时间**：2026-08-03（周一）至 2026-10-25（周日）
- **阶段划分**：
  - Phase 4-A：基础设施 + 主数据类（P0-9、P0-2、P0-7）共 3.5 周
  - Phase 4-B：生产执行类（P0-3、P0-1）共 2.5 周
  - Phase 4-C：质量与合规类（P0-4、P0-5、P0-6）共 4 周
  - Phase 4-D：集成与验收（P0-8 + 综合验收）共 2 周

### 1.5 项目干系人

| 角色 | 职责 |
|------|------|
| 产品负责人（PO） | 需求评审、阶段性验收 |
| 项目经理（PM） | 进度跟踪、风险管理、资源协调 |
| 后端开发（BE） | 数据库设计、Service/Controller、单元测试 |
| 前端开发（FE） | 页面与组件、API 调用、E2E 测试 |
| 测试工程师（QA） | 集成测试、回归测试、性能测试 |
| AI Agent | 主力编码、文档生成、单元测试生成 |

---

## 二、P0 功能需求清单

### 2.1 需求汇总表

| 编号 | 功能名称 | 模块归属 | 估算人天 | 优先级 | 关联主线 | 状态 |
|------|---------|---------|---------|--------|---------|------|
| P0-1 | 物料拉动配送 + 齐套管理 | `wm`（新增子模块） | 7.5 | 高 | MESA-5 物料管理 | ❌ 不做（与 ERP wm/prd 重叠，见 §2.3） |
| P0-2 | 工装/模治具管理 | `eq` 扩展 + `tg`（新增子模块） | 5 | 高 | MESA-2 资源分配 | ✅ 完整交付（E2E 20/20） |
| P0-3 | 任务进站/出站 + 流程指引 | `pro` 扩展 | 5 | 高 | MESA-3 派工 + MESA-8 过程管理 | ✅ 完整交付（commit 03f0098a，进站出站跳站+规则引擎+SOP推送） |
| P0-4 | NCR 不合格品 + 返工流程 | `qc` 扩展 + `nr`（新增子模块） | 7.5 | 高 | MESA-7 质量管理 | ✅ 完整交付（E2E 41/41 + 单测 16/16） |
| P0-5 | E-SOP 电子作业指导书 | `sop`（新增子模块） | 7.5 | 高 | MESA-4 文档控制 | ✅ 完整交付（E2E 28/28，Flowable+OSS ossId+契约+Javadoc） |
| P0-6 | 电子签名 + DHR 批记录 | `common` 扩展（合规基础设施） | 7.5 | 高 | MESA-4 文档控制（合规） | ⏭️ 延后（电子签名后期抽 common 组件，业务自行调用） |
| P0-7 | 核心模块单元测试补齐 | `pro/qc/spc` 测试包 | 7.5 | 高 | 工程质量底线 | ⏭️ 不做（pro 模块测试缺口暂不补） |
| P0-8 | IoT 自动采集打通 | `dc` 扩展 + `common` 桥接 | 7.5 | 高 | MESA-5 数据采集 | ✅ 完整交付（E2E 10/10 + 单测 11/11） |
| P0-9 | 批次（Lot）管理 | `md` 扩展 + `pro` 扩展 | 5 | 高 | MESA-10 追溯与谱系 | ✅ 完整交付（编码+整改+DDL落库+并入ry-mes.sql+Javadoc） |
| **合计** | | | **60 人天** | | |

> **2026-07-29 P0 收尾决议**（6 完成 / 3 不做，P0 阶段闭合）：
> - **P0-1 物料拉动+齐套** ❌ 不做 — 与 ERP `wm`/`prd` 重叠：ERP 已有 `WmProductionIssue.buildLinesFromWorkOrder`（工单 BOM 带领料行）+ `selectAvailableStock`（ATP 可用库存=齐套校验）+ `PrdMaterialRequirement.calculateNetRequirements`（毛需求-库存-安全库存=短缺=缺料计算）+ `WmStockAlert.checkStockAlerts`（库存预警）+ `WmTransfer`/`PickingResolutionService`（调拨/拣货=配送）。F1-2~F1-6 均被覆盖，仅 F1-7 AGV 接口为新。按"不与 ERP 重复"约束不做。
> - **P0-7 pro 模块单测缺口** ⏭️ 不做 — ProWorkOrder/ProFlowCard/ProDispatch/ProSn/MesMbom/ProAndon/ProTraceability 暂不补。
> - **P0-6 电子签名+DHR** ⏭️ 延后 — 电子签名后期单独抽为 common 组件（`ruoyi-common` 下），业务模块自行调用；SOP `signature_id`、NCR `disposition_signature_id` 预留点待落地后回填。

### 2.2 优先级矩阵

```
        高价值
          │
   P0-7   │   P0-3   P0-4
   P0-1   │   P0-5   P0-6
   P0-8   │   P0-9   P0-2
          │
──────────┼────────── 高紧迫
          │
          │
        低价值
```

- **紧迫性判定依据**：①现有系统运行时风险（测试缺失=高）②数据贯通阻塞（IoT=高、批次=高）③下游模块依赖
- **价值判定依据**：①对标主流 MES 差距权重 ②用户感知度 ③合规风险

---

## 三、功能模块详细说明

### 3.1 P0-1 物料拉动配送 + 齐套管理

#### 3.1.1 需求描述

实现车间物料 JIT 配送体系，包含**看板拉动、齐套校验、配送工单、线边库预警**四大能力，对标鼎捷"多重物料拉动体系，降低生产线边库存积压 52%"。

#### 3.1.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F1-1 物料看板生成 | 基于工单 BOM + 工序节拍，自动生成配送看板（按工位/时段） | 必须 |
| F1-2 齐套校验 | 工单开工前调用 `checkKitting(woId)`，校验 ERP 库存是否满足 BOM 需求 | 必须 |
| F1-3 缺料预警 | 缺料时生成缺料清单，触发安灯黄色呼叫 + 站内消息 | 必须 |
| F1-4 配送工单 | 生成配送任务（从库位→线边库位），状态机：待配送/配送中/已送达/已确认 | 必须 |
| F1-5 线边库存预警 | 线边库位库存低于安全库存时自动预警 | 必须 |
| F1-6 配送路线配置 | 配置库位→工位的配送路线、配送频次 | 可选（v1.1） |
| F1-7 AGV 接口预留 | 预留 AGV 调度接口契约，不实现具体调度 | 必须（契约） |

#### 3.1.3 数据库设计

新增 4 张表（前缀 `wm_`）：

```sql
-- 物料看板(配送触发凭证)
CREATE TABLE wm_kanban_card (
    card_id          BIGINT NOT NULL COMMENT '看板ID',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    card_code        VARCHAR(64) NOT NULL COMMENT '看板编号',
    card_type        VARCHAR(16) NOT NULL COMMENT 'PRODUCTION/SUPPLY/WITHDRAWAL',
    wo_id            BIGINT COMMENT '关联工单ID',
    workstation_id   BIGINT COMMENT '目标工位ID',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    required_qty     DECIMAL(14,4) NOT NULL COMMENT '需求数量',
    supplied_qty     DECIMAL(14,4) DEFAULT 0 COMMENT '已配送数量',
    status           VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/IN_TRANSIT/DELIVERED/CONFIRMED/CLOSED',
    due_time         DATETIME COMMENT '需求时点',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (card_id), UNIQUE INDEX uk_card_code (card_code),
    INDEX idx_wo_id (wo_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料看板';

-- 工单齐套校验记录
CREATE TABLE wm_kitting_check (
    check_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    check_code       VARCHAR(64) NOT NULL,
    wo_id            BIGINT NOT NULL,
    check_status     VARCHAR(16) NOT NULL COMMENT 'FULL_KIT/PARTIAL/EMPTY',
    total_lines      INT COMMENT '总行数',
    kit_lines       INT COMMENT '齐套行数',
    shortage_lines  INT COMMENT '缺料行数',
    check_time      DATETIME NOT NULL,
    check_by        BIGINT,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (check_id), UNIQUE INDEX uk_check_code (check_code),
    INDEX idx_wo_id (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='齐套校验记录';

-- 齐套校验明细(每行物料)
CREATE TABLE wm_kitting_check_line (
    line_id          BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    check_id         BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    required_qty     DECIMAL(14,4) NOT NULL,
    onhand_qty       DECIMAL(14,4) COMMENT 'ERP库存',
    shortage_qty     DECIMAL(14,4) DEFAULT 0,
    is_kit           CHAR(1) DEFAULT '0' COMMENT '是否齐套(0/1)',
    PRIMARY KEY (line_id), INDEX idx_check_id (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='齐套校验明细';

-- 配送任务
CREATE TABLE wm_delivery_task (
    task_id          BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    task_code        VARCHAR(64) NOT NULL,
    kanban_card_id   BIGINT COMMENT '关联看板ID',
    from_location    VARCHAR(64) COMMENT '源库位编码',
    to_location      VARCHAR(64) NOT NULL COMMENT '目标线边库位',
    material_id      BIGINT NOT NULL,
    delivery_qty     DECIMAL(14,4) NOT NULL,
    status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/IN_TRANSIT/DELIVERED/CONFIRMED',
    assignee_id      BIGINT COMMENT '配送人',
    dispatch_time    DATETIME, deliver_time DATETIME, confirm_time DATETIME,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (task_id), UNIQUE INDEX uk_task_code (task_code),
    INDEX idx_card_id (kanban_card_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送任务';

-- 线边库位
CREATE TABLE wm_line_side_stock (
    stock_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    workstation_id   BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    onhand_qty       DECIMAL(14,4) DEFAULT 0,
    safety_qty       DECIMAL(14,4) DEFAULT 0,
    max_qty          DECIMAL(14,4) DEFAULT 0,
    last_in_time     DATETIME, last_out_time DATETIME,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (stock_id), UNIQUE INDEX uk_ws_mat (workstation_id, material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线边库位库存';
```

#### 3.1.4 关键接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/mes/wm/kanban/list` | GET | 看板列表 |
| `/mes/wm/kanban/generate` | POST | 按工单生成看板（触发式） |
| `/mes/wm/kitting/check/{woId}` | POST | 触发齐套校验，返回校验结果 |
| `/mes/wm/kitting/list` | GET | 校验记录列表 |
| `/mes/wm/delivery/list` | GET | 配送任务列表 |
| `/mes/wm/delivery/dispatch/{taskId}` | POST | 派发配送任务 |
| `/mes/wm/delivery/confirm/{taskId}` | POST | 确认送达 |
| `/mes/wm/line-stock/list` | GET | 线边库存列表 |
| `/mes/wm/line-stock/warning` | GET | 库存预警查询 |

### 3.2 P0-2 工装/模治具管理

#### 3.2.1 需求描述

独立工装/模具/夹具管理模块，覆盖台账、寿命监控、保养提醒、使用统计，对标智能工厂九大模块独立"工装管理"。

#### 3.2.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F2-1 工装台账 | 工装基本信息（编码/名称/类型/规格/状态/所属车间） | 必须 |
| F2-2 寿命计数 | 已使用次数/时长 + 总寿命阈值，自动预警 | 必须 |
| F2-3 保养计划 | 周期性保养计划（按次数/时长触发） | 必须 |
| F2-4 保养记录 | 保养执行记录，保养后归零计数 | 必须 |
| F2-5 使用记录 | 工装与工单/工序的绑定使用记录 | 必须 |
| F2-6 报废流程 | 寿命到期或损坏时申请报废 | 必须 |
| F2-7 领用/归还 | 工装领用归还登记 | 必须 |

#### 3.2.3 数据库设计

新增 4 张表（前缀 `tg_`，原 `tg_checkout_record` 已并入 `tg_usage_record`——领用-归还即一次使用会话，字段重叠。详见 [P0-2 实施方案](./2026-07-26-mes-p0-2-tooling-management-implementation-plan.md) §1.3）：

```sql
-- 工装台账
CREATE TABLE tg_tooling (
    tooling_id       BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_code     VARCHAR(64) NOT NULL,
    tooling_name     VARCHAR(128) NOT NULL,
    tooling_type     VARCHAR(16) NOT NULL COMMENT 'MOLD/FIXTURE/CUTTER/GAUGE/OTHER',
    spec             VARCHAR(128),
    workshop_id      BIGINT,
    status           VARCHAR(16) DEFAULT 'IDLE' COMMENT 'IDLE/IN_USE/MAINTENANCE/SCRAPPED',
    total_life_count INT COMMENT '总寿命(次数)',
    total_life_hours DECIMAL(14,2) COMMENT '总寿命(小时)',
    used_count       INT DEFAULT 0,
    used_hours       DECIMAL(14,2) DEFAULT 0,
    purchase_date    DATE, purchase_cost DECIMAL(14,2),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (tooling_id), UNIQUE INDEX uk_tooling_code (tooling_code),
    INDEX idx_status (status), INDEX idx_type (tooling_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装台账';

-- 工装保养计划
CREATE TABLE tg_maintenance_plan (
    plan_id          BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id       BIGINT NOT NULL,
    plan_name        VARCHAR(128) NOT NULL,
    trigger_type     VARCHAR(16) NOT NULL COMMENT 'BY_COUNT/BY_HOURS/PERIODIC',
    trigger_value    INT COMMENT '触发阈值(次数/小时/天数)',
    last_maintain_time DATETIME,
    next_maintain_time DATETIME,
    enabled          CHAR(1) DEFAULT '1',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (plan_id), INDEX idx_tooling_id (tooling_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装保养计划';

-- 工装保养记录
CREATE TABLE tg_maintenance_record (
    record_id        BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id       BIGINT NOT NULL,
    plan_id          BIGINT,
    maintain_type    VARCHAR(16) COMMENT 'PLANNED/REPAIR',
    maintain_time    DATETIME NOT NULL,
    maintainer_id    BIGINT,
    used_count_before INT, used_count_after INT,
    description     VARCHAR(500),
    cost            DECIMAL(14,2),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (record_id), INDEX idx_tooling_id (tooling_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装保养记录';

-- 工装使用/领用记录（合并原 tg_checkout_record：领用-归还即一次使用会话）
CREATE TABLE tg_usage_record (
    usage_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id       BIGINT NOT NULL,
    wo_id            BIGINT,
    wo_no            VARCHAR(64) COMMENT '工单编号(冗余)',
    workstation_id   BIGINT,
    workstation_name VARCHAR(128) COMMENT '工位名称(冗余)',
    operator_id      BIGINT,
    operator_name    VARCHAR(64) COMMENT '操作员姓名(冗余)',
    checkout_time    DATETIME NOT NULL COMMENT '领用时间',
    return_time      DATETIME COMMENT '归还时间',
    usage_count      INT DEFAULT 1,
    usage_hours      DECIMAL(14,2),
    status           VARCHAR(16) DEFAULT 'OUT' COMMENT 'OUT/RETURNED',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (usage_id), INDEX idx_tooling_id (tooling_id), INDEX idx_wo_id (wo_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装使用领用记录';
```

### 3.3 P0-3 任务进站/出站 + 流程指引

> **评审标注（2026-07-26，与 P0-5 联合评审）**：`pro_step_guide.guide_type` 原计划含 IMAGE/VIDEO + `media_url`，与 P0-5 `sop_document` 富媒体能力高重叠（影子 SOP 风险）。**建议收敛**：guide_type 限 **TEXT + SOP_LINK**（去掉 IMAGE/VIDEO），删除 `media_url` 字段（TEXT 用 guide_content，SOP_LINK 用 sop_id）。富媒体作业指导统一走 P0-5 `sop_document`（版本+审批+审计），pro_step_guide 用 SOP_LINK 引用。详见 [P0-5 实施方案 §1.6](./2026-07-26-mes-p0-5-esop-implementation-plan.md)。SOP_LINK 类型时 `sop_id` 须校验该 SOP 在 `sop_process_binding` 已绑定到同 process_id。
> **进站插入点**：`ProFlowCardServiceImpl.startStep()` 是 SOP 推送的天然触发点（F3-1 进站校验门禁），P0-5 `pushSopToWorkstation(stepRecordId)` 由此处调用。

#### 3.3.1 需求描述

扩展 [ProFlowCardServiceImpl](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java)，实现工序进站前置校验、出站放行规则、流程跳站防错，对标鼎捷"任务进站管理+任务出站管理+流程指引管理"。

#### 3.3.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F3-1 进站前置校验 | 校验物料齐套/人员资质/设备状态/SOP 版本/工装可用性 | 必须 |
| F3-2 进站放行规则 | 5 类校验任一不通过则阻止进站，记录未通过原因 | 必须 |
| F3-3 出站放行规则 | 校验必检项完成/数量达标/无未关闭异常/工艺参数达标 | 必须 |
| F3-4 跳站防错 | 仅允许有权限的工序跳站，记录跳站原因 | 必须 |
| F3-5 流程指引配置 | 工序级指引项配置（指引文本/图片/SOP 链接/视频） | 必须 |
| F3-6 校验规则配置 | 工序校验规则可配置（启用/禁用某项校验） | 必须 |

#### 3.3.3 数据库设计

扩展 `pro_flow_card_step` 表 + 新增 2 张配置表：

```sql
-- 扩展 pro_flow_card_step: 增加进站/出站相关字段
ALTER TABLE pro_flow_card_step
    ADD COLUMN entry_time DATETIME COMMENT '进站时间',
    ADD COLUMN exit_time DATETIME COMMENT '出站时间',
    ADD COLUMN entry_check_result VARCHAR(16) COMMENT 'PASS/FAIL',
    ADD COLUMN entry_check_remark VARCHAR(500),
    ADD COLUMN exit_check_result VARCHAR(16),
    ADD COLUMN exit_check_remark VARCHAR(500),
    ADD COLUMN skip_reason VARCHAR(500) COMMENT '跳站原因';

-- 工序流程指引配置
CREATE TABLE pro_step_guide (
    guide_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id       BIGINT NOT NULL COMMENT '工序ID',
    guide_type       VARCHAR(16) NOT NULL COMMENT 'TEXT/IMAGE/VIDEO/SOP_LINK',
    guide_title      VARCHAR(128),
    guide_content    TEXT COMMENT '文本内容',
    media_url        VARCHAR(500) COMMENT '图片/视频URL',
    sop_id           BIGINT COMMENT '关联SOP文档',
    sort_order       INT DEFAULT 0,
    enabled          CHAR(1) DEFAULT '1',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (guide_id), INDEX idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序流程指引配置';

-- 工序校验规则配置
CREATE TABLE pro_step_check_rule (
    rule_id          BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id       BIGINT NOT NULL,
    check_type       VARCHAR(32) NOT NULL COMMENT 'MATERIAL_KITTING/OPERATOR_SKILL/EQUIPMENT_STATUS/SOP_VERSION/TOOLING_AVAILABLE/INSPECTION_REQUIRED/QUANTITY_REACHED/PARAMETER_OK/EXCEPTION_CLOSED',
    check_stage      VARCHAR(16) NOT NULL COMMENT 'ENTRY/EXIT',
    enabled          CHAR(1) DEFAULT '1',
    error_msg        VARCHAR(200),
    sort_order       INT DEFAULT 0,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (rule_id), INDEX idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序校验规则配置';
```

#### 3.3.4 关键接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/mes/pro/flow-card/entry/{stepRecordId}` | POST | 进站校验（执行所有 ENTRY 规则，返回结果） |
| `/mes/pro/flow-card/exit/{stepRecordId}` | POST | 出站校验 |
| `/mes/pro/flow-card/skip` | POST | 跳站申请（带 reason） |
| `/mes/pro/step-guide/list` | GET | 工序指引查询 |
| `/mes/pro/step-guide` | POST/PUT/DELETE | 指引 CRUD |
| `/mes/pro/step-check-rule/list` | GET | 校验规则查询 |
| `/mes/pro/step-check-rule` | POST/PUT/DELETE | 规则 CRUD |

### 3.4 P0-4 NCR 不合格品 + 返工流程

#### 3.4.1 需求描述

对标西门子 Opcenter "缺陷跟踪和不合格管理"，实现独立 NCR 单据生命周期、不良品隔离、返工工单、8D 报告，闭环质量管理。

#### 3.4.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F4-1 NCR 单据创建 | 检验发现不良时自动/手动创建 NCR 单 | 必须 |
| F4-2 NCR 生命周期 | OPEN→REVIEW→DISPOSITION→IN_REWORK→CLOSED 状态机 | 必须 |
| F4-3 不良品隔离 | 不良品移入隔离库位，冻结可用库存 | 必须 |
| F4-4 返工工单 | 从 NCR 派生返工工单，关联原工单 | 必须 |
| F4-5 返工流程 | 返工工单走正常派工/流转卡流程 | 必须 |
| F4-6 8D 报告 | NCR 关联 8D 报告（D1-D8 八步） | 必须 |
| F4-7 缺陷代码库 | 维护分层缺陷代码（类别/子类/代码/描述） | 必须 |
| F4-8 不良原因分析 | 不良原因记录（人/机/料/法/环/测） | 必须 |

#### 3.4.3 数据库设计

新增 5 张表（前缀 `nr_`）：

```sql
-- NCR 不合格品单
CREATE TABLE nr_ncr_order (
    ncr_id           BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_code         VARCHAR(64) NOT NULL,
    source_type      VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/OQC/PROCESS/CUSTOMER',
    source_id        BIGINT COMMENT '来源单据ID(qc_iqc_id/ipqc_id等)',
    wo_id            BIGINT,
    material_id      BIGINT,
    defect_qty      DECIMAL(14,4) NOT NULL,
    disposition      VARCHAR(16) COMMENT 'REWORK/SCRAP/ACCEPT_AS_IS/RETURN',
    status           VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED',
    created_time     DATETIME NOT NULL,
    closed_time      DATETIME,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (ncr_id), UNIQUE INDEX uk_ncr_code (ncr_code),
    INDEX idx_source (source_type, source_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NCR不合格品单';

-- NCR 缺陷明细
CREATE TABLE nr_ncr_defect (
    defect_id        BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_id           BIGINT NOT NULL,
    defect_code_id   BIGINT NOT NULL,
    defect_qty       DECIMAL(14,4) NOT NULL,
    defect_cause     VARCHAR(32) COMMENT 'MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT',
    root_cause_desc  VARCHAR(500),
    PRIMARY KEY (defect_id), INDEX idx_ncr_id (ncr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NCR缺陷明细';

-- 缺陷代码库
CREATE TABLE nr_defect_code (
    code_id          BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    parent_id        BIGINT COMMENT '父代码ID(层级)',
    code             VARCHAR(32) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    level            INT DEFAULT 1 COMMENT '层级(1类别/2子类/3代码)',
    enabled          CHAR(1) DEFAULT '1',
    PRIMARY KEY (code_id), UNIQUE INDEX uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺陷代码库';

-- 返工工单(扩展 pro_work_order + 关联 NCR)
CREATE TABLE nr_rework_order (
    rework_id        BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    rework_code      VARCHAR(64) NOT NULL,
    ncr_id           BIGINT NOT NULL,
    original_wo_id   BIGINT NOT NULL,
    rework_wo_id     BIGINT NOT NULL COMMENT '生成的返工工单ID',
    rework_qty       DECIMAL(14,4) NOT NULL,
    rework_reason    VARCHAR(500),
    status           VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/COMPLETED',
    PRIMARY KEY (rework_id), UNIQUE INDEX uk_rework_code (rework_code),
    INDEX idx_ncr_id (ncr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返工工单';

-- 8D 报告
CREATE TABLE nr_8d_report (
    report_id        BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_id           BIGINT NOT NULL,
    d1_team          TEXT COMMENT 'D1团队',
    d2_problem       TEXT COMMENT 'D2问题描述',
    d3_interim       TEXT COMMENT 'D3临时措施',
    d4_root_cause    TEXT COMMENT 'D4根因',
    d5_permanent     TEXT COMMENT 'D5永久措施',
    d6_implement     TEXT COMMENT 'D6措施实施',
    d7_prevent       TEXT COMMENT 'D7预防措施',
    d8_recognize     TEXT COMMENT 'D8团队认可',
    status           VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_PROGRESS/COMPLETED',
    PRIMARY KEY (report_id), INDEX idx_ncr_id (ncr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='8D报告';
```

### 3.5 P0-5 E-SOP 电子作业指导书

> **实施状态**: ✅ 完整交付（2026-07-26）：编码 + SQL 落库 + 端到端联调 28/28 断言通过 + Javadoc 完善。详见 [P0-5 实施方案](./2026-07-26-mes-p0-5-esop-implementation-plan.md)。
> - 独立 `sop` 子包（非 dc 扩展），4 张 `sop_` 表
> - ⚠️ DDL 修正：`file_url VARCHAR(500)` -> `file_oss_id BIGINT`（OSS 私有桶存 ID，VO 用 `@Translation(OSS_ID_TO_URL)` 解析签名 URL，依据 memory `oss-private-bucket-store-ossid`）
> - 审批流：Flowable（`WfCallBackServiceI`，ruoyi-mes 已依赖 ruoyi-flowable）+ 管理员兜底 approve/reject（R11）
> - F5-5 电子签名：`sop_view_log.signature_id` 预留，强制校验待 P0-6 接入
> - F5-4 工位推送：查询 API + 查看器页 + SSE 契约（`SseMessageUtils`），触发时机由 P0-3 接入
> - F5-3 工序绑定：绑到 ERP `bd_process.process_id`（工序主数据，非 routing_step），冗余 `process_category`，复用 ERP `/erp/bd/process/list` 选择器；与 `bd_routing_step.description`（文本作业指导）并存

#### 3.5.1 需求描述

对标 MESA-4 文档控制，实现 SOP 文档库、按工序自动推送、版本管理、查阅记录，覆盖鼎捷"E-SOP 管理"功能。

#### 3.5.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F5-1 SOP 文档库 | SOP 主档（标题/类型/版本/状态/文件URL） | 必须 |
| F5-2 SOP 版本管理 | 多版本共存，发布/归档/废止 | 必须 |
| F5-3 工序绑定 | SOP 与工序多对多绑定，支持版本指定 | 必须 |
| F5-4 工位推送 | 工单开工后自动推送当前工序 SOP 到工位终端 | 必须 |
| F5-5 查阅记录 | 记录操作员查阅历史（含电子签名） | 必须 |
| F5-6 富媒体支持 | 支持 PDF/图片/视频/Word | 必须 |
| F5-7 审批流 | SOP 发布走 Flowable 审批 | 必须 |

#### 3.5.3 数据库设计

新增 3 张表（前缀 `sop_`）：

```sql
-- SOP 主档
CREATE TABLE sop_document (
    sop_id           BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_code         VARCHAR(64) NOT NULL,
    sop_name        VARCHAR(128) NOT NULL,
    sop_type        VARCHAR(16) COMMENT 'PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN',
    current_version VARCHAR(32) NOT NULL,
    status           VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED',
    file_url         VARCHAR(500),
    file_type        VARCHAR(16) COMMENT 'PDF/IMAGE/VIDEO/WORD',
    description      VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (sop_id), UNIQUE INDEX uk_sop_code (sop_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP文档主档';

-- SOP 版本历史
CREATE TABLE sop_version (
    version_id       BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_id           BIGINT NOT NULL,
    version_no       VARCHAR(32) NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    change_log       VARCHAR(500),
    published_time   DATETIME,
    published_by     BIGINT,
    status           VARCHAR(16) COMMENT 'CURRENT/HISTORICAL',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    PRIMARY KEY (version_id), INDEX idx_sop_id (sop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP版本历史';

-- 工序-SOP 绑定
CREATE TABLE sop_process_binding (
    binding_id       BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id       BIGINT NOT NULL,
    sop_id           BIGINT NOT NULL,
    version_no       VARCHAR(32) COMMENT '指定版本(空=最新)',
    is_default       CHAR(1) DEFAULT '0',
    sort_order       INT DEFAULT 0,
    PRIMARY KEY (binding_id), INDEX idx_process (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序SOP绑定';

-- SOP 查阅记录
CREATE TABLE sop_view_log (
    log_id           BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_id           BIGINT NOT NULL,
    version_no       VARCHAR(32),
    viewer_id        BIGINT NOT NULL,
    view_time        DATETIME NOT NULL,
    wo_id            BIGINT COMMENT '关联工单',
    process_id       BIGINT COMMENT '关联工序',
    signature_id    BIGINT COMMENT '关联电子签名记录',
    PRIMARY KEY (log_id), INDEX idx_sop_id (sop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP查阅记录';
```

### 3.6 P0-6 电子签名 + DHR 批记录

#### 3.6.1 需求描述

实现符合 21 CFR Part 11 的电子签名基础设施 + 制造批记录（DHR），覆盖西门子 Opcenter "Electronic Procedures: 支持电子签名"功能。

#### 3.6.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F6-1 电子签名基础设施 | 独立电子签名服务（密码校验+签名记录+审计追踪） | 必须 |
| F6-2 业务事件签名接入 | 关键业务事件（开工/完工/检验放行/NCR 处置/SOP 查阅）支持签名 | 必须 |
| F6-3 DHR 批记录模板 | 工单 DHR 模板配置（含哪些记录项） | 必须 |
| F6-4 DHR 自动归集 | 工单生命周期事件自动归集到 DHR | 必须 |
| F6-5 DHR 导出 | PDF 导出 DHR 完整记录（含电子签名水印） | 必须 |
| F6-6 审计追踪 | 所有签名/操作不可篡改审计日志 | 必须 |
| F6-7 签名二次确认 | 签名时输入密码 + 二次确认（意义声明） | 必须 |

#### 3.6.3 数据库设计

新增 3 张表（前缀 `es_` for 电子签名 + `dhr_`）：

```sql
-- 电子签名记录
CREATE TABLE es_signature (
    signature_id     BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    signer_id        BIGINT NOT NULL COMMENT '签名者用户ID',
    signer_name      VARCHAR(64),
    meaning          VARCHAR(200) NOT NULL COMMENT '签名意义声明',
    event_type       VARCHAR(32) NOT NULL COMMENT 'WORKORDER_START/COMPLETE/RELEASE/INSPECTION_PASS/NCR_DISPOSITION/SOP_VIEW',
    ref_type         VARCHAR(32) COMMENT '关联对象类型',
    ref_id           BIGINT COMMENT '关联对象ID',
    wo_id            BIGINT COMMENT '关联工单(冗余查询用)',
    signature_hash   VARCHAR(64) NOT NULL COMMENT '签名哈希(SHA-256)',
    signed_at        DATETIME NOT NULL,
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(256),
    PRIMARY KEY (signature_id), INDEX idx_signer (signer_id), INDEX idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子签名记录';

-- 审计追踪日志(不可篡改)
CREATE TABLE es_audit_trail (
    audit_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    user_id          BIGINT NOT NULL,
    user_name        VARCHAR(64),
    action           VARCHAR(64) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/SIGN/RELEASE',
    entity_type      VARCHAR(64) NOT NULL,
    entity_id        BIGINT,
    before_snapshot  TEXT COMMENT '变更前(JSON)',
    after_snapshot   TEXT COMMENT '变更后(JSON)',
    signature_id    BIGINT COMMENT '关联签名(若有)',
    action_time      DATETIME NOT NULL,
    ip_address       VARCHAR(64),
    PRIMARY KEY (audit_id), INDEX idx_entity (entity_type, entity_id), INDEX idx_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计追踪日志';

-- DHR 批记录主表(每工单一行)
CREATE TABLE dhr_record (
    dhr_id           BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    wo_id            BIGINT NOT NULL UNIQUE,
    dhr_code         VARCHAR(64) NOT NULL,
    status           VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/COMPLETED/REVIEWED/APPROVED/ARCHIVED',
    start_time       DATETIME, complete_time DATETIME,
    review_time      DATETIME, review_by BIGINT,
    approve_time     DATETIME, approve_by BIGINT,
    PRIMARY KEY (dhr_id), UNIQUE INDEX uk_dhr_code (dhr_code),
    INDEX idx_wo_id (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DHR批记录';

-- DHR 记录项(归集的事件/签名/检验/采集数据)
CREATE TABLE dhr_record_entry (
    entry_id         BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    dhr_id           BIGINT NOT NULL,
    entry_type       VARCHAR(32) NOT NULL COMMENT 'EVENT/SIGNATURE/INSPECTION/DATA_COLLECTION/NCR/SOP_VIEW',
    ref_id           BIGINT COMMENT '关联源记录ID',
    entry_time       DATETIME NOT NULL,
    entry_data       TEXT COMMENT '记录数据(JSON)',
    signature_id    BIGINT COMMENT '关联签名(若有)',
    sort_order       INT DEFAULT 0,
    PRIMARY KEY (entry_id), INDEX idx_dhr_id (dhr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DHR记录项';
```

### 3.7 P0-7 核心模块单元测试补齐

#### 3.7.1 需求描述

当前 [ruoyi-mes 测试目录](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes) 仅有 3 个测试类（仅 eq 模块），核心的 `pro/`、`qc/`、`spc/` 模块**零单元测试**，存在严重回归风险。本任务补齐测试覆盖至 60%+。

#### 3.7.2 测试范围与目标

| 模块 | 当前测试数 | 目标测试数 | 覆盖率目标 | 测试用例数 |
|------|----------|----------|----------|----------|
| pro/ | 0 | 8 | 70% | ≥40 |
| qc/ | 0 | 3 | 65% | ≥15 |
| spc/ | 0 | 2 | 80%（算法核心） | ≥12 |
| eq/ | 3 | 5 | 60% | +6 |
| common/ | 0 | 2 | 60% | ≥6 |
| lt/ | 0 | 2 | 65% | ≥8 |
| **合计** | 3 | 22 | **60%+** | **≥87** |

#### 3.7.3 测试用例清单（核心）

**pro 模块（重点）**：
- `ProWorkOrderServiceImplTest`：CRUD、状态机、审批联动、ERP MQ 回调
- `ProFlowCardServiceImplTest`：状态机 5 态流转、自动工序、扫码绑定、进站出站校验（P0-3 联动）
- `ProDispatchServiceImplTest`：派工分配、扫码开工/完工、Lock4j 并发
- `ProSnServiceImplTest`：SN 生成、父子关系、追溯树
- `MesMbomServiceImplTest`：MBOM 工单快照、解析
- `ProAndonServiceImplTest`：安灯触发、SSE 推送、IoT 联动
- `ProTraceabilityServiceImplTest`：前向/后向追溯
- `WorkOrderReleaseConsumerTest`：MQ 消费、幂等

**spc 模块（算法核心，必须 80%+）**：
- `SpcControlChartServiceImplTest`：5 种控制图控制限计算、4 条 OOC 规则、边界用例（n<2、n>10、数据不足）
- `SpcDataPointServiceImplTest`：分组、查询

**qc 模块**：
- `QcIqcServiceImplTest`、`QcIpqcServiceImplTest`、`QcOqcServiceImplTest`：CRUD + ERP MQ 回调

**lt 模块（P0-9 新增，状态机+FEFO+事件）**：
- `LtLotServiceImplTest`：状态机 5 态流转、FEFO 排序、consume 并发与超卖、applyIqcResult 钩子、checkLotUsable 契约
- `LtWoConsumptionServiceImplTest`：confirmPick 多批次部分消耗、事务回滚

#### 3.7.4 测试规范

- 框架：JUnit 5 + Mockito + Spring Boot Test
- 命名：`{ClassName}Test`，方法 `should_{行为}_when_{条件}`
- 数据：使用 `@BeforeEach` 准备 fixture，禁止依赖外部数据库
- 断言：使用 AssertJ（流式断言）
- 覆盖率工具：JaCoCo，目标 `line ≥ 60%, branch ≥ 50%`
- CI 集成：`mvn test -pl ruoyi-modules/ruoyi-mes` 必须全部通过

### 3.8 P0-8 IoT 自动采集打通

#### 3.8.1 需求描述

激活 [IotDataArchiveTask](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/task/IotDataArchiveTask.java)（当前为空壳），打通 IoT 设备数据自动写入 `dc_data_record`，实现 [NexusERP_MES文档.md](file:///d:/workspace/nexus-erp/doc/mes/NexusERP_MES文档.md) §19.4 中"⏳ 契约就绪未调用"的落地。

#### 3.8.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F8-1 设备软关联 | `iot_device_id` ↔ `mes_equipment_id` 双向绑定管理页面 | 必须 |
| F8-2 采集点-设备绑定 | `dc_collection_point` 与 IoT 设备点位绑定（point_type=AUTO） | 必须 |
| F8-3 数据归档任务 | 定时（每 30s）拉取 IoT 数据并写入 dc_data_record（source=IoT） | 必须 |
| F8-4 实时推送 | IoT 数据变化时通过 SSE 推送到设备监控页面 | 必须 |
| F8-5 设备状态联动 | IoT 设备状态变化时自动更新 `eq_equipment.status` | 必须 |
| F8-6 数据补偿 | 断线期间数据补偿拉取 | 必须 |
| F8-7 异常告警 | 采集异常时触发安灯 | 可选（v1.1） |

#### 3.8.3 技术实现

```java
// IotDataArchiveTask 重构核心逻辑
@Scheduled(cron = "0/30 * * * * ?")
public void archiveIotData() {
    // 1. 查询所有 point_type=AUTO 且绑定了 iot_device_id 的采集点
    List<DcCollectionPoint> autoPoints = collectionPointMapper.selectAutoPoints();
    // 2. 按 iot_device_id 分组,批量调用 IoT 服务
    Map<Long, List<DcCollectionPoint>> grouped = autoPoints.stream()
        .collect(Collectors.groupingBy(DcCollectionPoint::getIotDeviceId));
    // 3. 调用 ruoyi-iot 的 IotDeviceDataQueryService
    grouped.forEach((deviceId, points) -> {
        List<IotDataPoint> iotData = iotDataQueryService.queryLatest(deviceId, points);
        // 4. 转换为 dc_data_record 并批量插入(source=IoT)
        List<DcDataRecord> records = iotData.stream().map(this::convert).toList();
        dataRecordMapper.insertBatch(records);
    });
}
```

#### 3.8.4 数据库变更

```sql
-- 扩展 dc_collection_point
ALTER TABLE dc_collection_point
    ADD COLUMN iot_device_id BIGINT COMMENT '关联IoT设备ID',
    ADD COLUMN iot_point_code VARCHAR(64) COMMENT 'IoT点位编码',
    ADD INDEX idx_iot_device (iot_device_id);

-- 扩展 eq_equipment
ALTER TABLE eq_equipment
    ADD COLUMN iot_device_id BIGINT COMMENT '关联IoT设备ID(软关联)',
    ADD INDEX idx_iot_device (iot_device_id);
```

### 3.9 P0-9 批次（Lot）管理

> **实施状态**: ✅ 完整交付（2026-07-24 ~ 07-26）：编码 + 整改 + 前端打磨 + DDL/字典/菜单/autocode 落库 + 并入 `script/sql/ry-mes.sql` + Javadoc 完善。详见 [P0-9 实施方案](./2026-08-03-mes-p0-9-lot-management-implementation-plan.md) + [批次整改计划](./2026-07-25-mes-lot-remediation-plan.md)。
> - 编码：3 表 DDL + 21 Java + 3 API + 3 页面 + 菜单 SQL，`mvn compile` 通过（39 class）
> - 整改（07-25）：A1-A6 代码修复（编码顺序化+@Lock4j、数量/删除/放行守卫、SQL 4->2 整合）+ B1-B5 五链路（SN↔批次事件、工单 FEFO 领料、IQC->qc_status 钩子、NCR/进站契约）
> - 前端打磨（07-25~07-26，9 批）：`plus-ui/src/views/mes` 全 38 页选择器化（无裸 ID 输入）+ dict-tag（无硬编码状态 map）+ 17 字典种子 + 批次编码 autoCode 规则
> - DDL/字典/菜单/autocode 已落本地库（各库总库已备份）；3 表 DDL 已并入 `script/sql/ry-mes.sql` 规范库
> - Javadoc 完善（2026-07-26）：lt+sop 两包 51 文件方法级 Javadoc 补全（impl 镜像接口契约 + CAS/守卫/事务注记，controller @param/@return）
> - 运行时 E2E：lt 契约方法（checkLotUsable/quarantineByNcr）待 P0-3 进站校验联调时一并验证

#### 3.9.1 需求描述

补齐 [ProSn](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProSn.java) 之上的批次级追溯，对标 MESA-10 谱系管理，覆盖批次主档、批次状态、FEFO 出库。

#### 3.9.2 功能点清单

| 功能点 | 说明 | 优先级 |
|--------|------|--------|
| F9-1 批次主档 | 物料批次号、生产日期、有效期、供应商批次、状态 | 必须 |
| F9-2 工单-批次绑定 | 工单领料时绑定物料批次 | 必须 |
| F9-3 批次追溯链 | 批次→SN→工序→设备→人员→检验 全链路 | 必须 |
| F9-4 FEFO 出库 | 按先到期先出推荐批次 | 必须 |
| F9-5 批次隔离 | 质量异常时批次隔离，冻结 | 必须 |
| F9-6 批次状态机 | IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED | 必须 |

#### 3.9.3 数据库设计

新增 3 张表（前缀 `lt_`）：

```sql
-- 批次主档
CREATE TABLE lt_lot (
    lot_id           BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    lot_code         VARCHAR(64) NOT NULL,
    material_id      BIGINT NOT NULL,
    supplier_id      BIGINT,
    supplier_lot     VARCHAR(64) COMMENT '供应商批次号',
    quantity         DECIMAL(14,4) NOT NULL,
    produced_date    DATE,
    expiry_date      DATE,
    status           VARCHAR(16) DEFAULT 'IN_STOCK' COMMENT 'IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED',
    qc_status        VARCHAR(16) COMMENT 'PENDING/PASS/FAIL',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (lot_id), UNIQUE INDEX uk_lot_code (lot_code),
    INDEX idx_material (material_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次主档';

-- 工单-批次消耗记录
CREATE TABLE lt_wo_consumption (
    consumption_id   BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    wo_id            BIGINT NOT NULL,
    lot_id           BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    consumed_qty     DECIMAL(14,4) NOT NULL,
    consumed_time    DATETIME NOT NULL,
    operator_id      BIGINT,
    PRIMARY KEY (consumption_id), INDEX idx_wo_id (wo_id), INDEX idx_lot_id (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单批次消耗';

-- SN-批次绑定
CREATE TABLE lt_sn_binding (
    binding_id       BIGINT NOT NULL,
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_id            BIGINT NOT NULL COMMENT 'SN ID',
    lot_id           BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    bound_time       DATETIME NOT NULL,
    PRIMARY KEY (binding_id), INDEX idx_sn (sn_id), INDEX idx_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SN批次绑定';
```

---

## 四、技术实现方案

### 4.1 总体架构

延续 NexusERP 既有架构，新增 6 个子包（`wm/tg/nr/sop/lt` + `common` 扩展）：

```
ruoyi-mes/src/main/java/com/nexus/mes/
├── common/                      # 已存在,扩展(es 电子签名基础设施 + audit)
│   ├── es/                      # 新增:电子签名基础设施
│   │   ├── service/ESignatureService.java
│   │   ├── service/AuditTrailService.java
│   │   └── annotation@RequiresSignature  # 注解驱动签名
│   └── audit/                   # 新增:审计追踪
├── wm/                          # 新增:P0-1 物料拉动+齐套
├── tg/                          # 新增:P0-2 工装/模治具
├── nr/                          # 新增:P0-4 NCR+返工
├── sop/                         # 新增:P0-5 E-SOP
├── lt/                          # 新增:P0-9 批次管理
├── dc/                          # 扩展:P0-8 IoT 打通
├── pro/                         # 扩展:P0-3 进站出站 + P0-6 DHR 归集
└── ...
```

### 4.2 后端实现规范

**沿用既定模式**（参考 Week 17-29 实现）：

- **Entity**：`@Data @EqualsAndHashCode(callSuper=true) @TableName` extends `TenantEntity`，主键 `@TableId`，逻辑删除 `@TableLogic delFlag`
- **BO**：`@AutoMapper(target=..., reverseConvertGenerate=false)` extends `BaseEntity`，校验 `@NotBlank`/`@NotNull`，含查询字段 + `Long[] ids`
- **VO**：`@ExcelIgnoreUnannotated @AutoMapper(target=...)`
- **Mapper**：`extends BaseMapperPlus<Xxx, XxxVo>`
- **Service**：`@DS("mes") @Service`，使用 `MapstructUtils.convert`
- **Controller**：`@Validated @RequiredArgsConstructor @RestController`，`@SaCheckPermission`，`@Log`
- **编号生成**：`前缀-yyyyMMdd-3位序号`（uk 唯一索引兜底）
- **多租户**：`tenant_id VARCHAR(20) DEFAULT '000000'`
- **数据库**：`ry-mes` schema（独立 MES 库）

### 4.3 前端实现规范

- **API 层**：`plus-ui/src/api/mes/{子模块}/*.ts`，沿用 `request` 封装
- **页面**：`plus-ui/src/views/mes/{子模块}/index.vue`，使用 el-table + el-dialog（或 vxe-table）
- **权限**：`v-hasPermi="['mes:wm:kanban:list']"`
- **可视化**：ECharts（已有先例 [iot/device/realtime.vue](file:///d:/workspace/nexus-erp/plus-ui/src/views/iot/device/realtime.vue)）
- **菜单**：`sys_menu` 注入，前缀 4xxx，按 Phase 分段（详见 §5.2）

### 4.4 集成方案

#### 4.4.1 与 ERP 集成

| 集成点 | 方向 | 协议 | 备注 |
|--------|------|------|------|
| 齐套校验查询 ERP 库存 | MES→ERP | 跨库 Mapper（已存在 [ErpMaterialMapper](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mapper/ErpMaterialMapper.java)） | 直查 `wm_material_stock` |
| 工单批次绑定回传 ERP | MES→ERP | RabbitMQ | 新增 exchange |
| 返工工单同步 ERP | MES→ERP | RabbitMQ | 沿用 `WorkOrderReleaseConsumer` 模式 |

#### 4.4.2 与 IoT 集成（P0-8 核心）

```yaml
# application.yml 新增
mes:
  iot:
    archive-cron: "0/30 * * * * ?"
    batch-size: 200
    retry-times: 3
    enabled: true
```

调用 [ruoyi-iot](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-iot) 的 `IotDeviceDataQueryService.queryLatest(deviceId, pointCodes)`，通过 Maven 依赖引入。

#### 4.4.3 与 ruoyi-ai 集成（保留接口，不实现）

P0-6 DHR 的 PDF 导出可选用 ruoyi-ai 的文档能力（v1.1）。

### 4.5 关键技术决策

| 决策点 | 选项 | 决议 | 理由 |
|--------|------|------|------|
| 电子签名实现 | A. 注解驱动 B. 显式调用 | **A** | 业务无侵入，符合 21 CFR Part 11 |
| DHR 归集 | A. 同步插入 B. 异步 MQ | **B** | 不阻塞主业务，最终一致 |
| IoT 数据归档 | A. 定时拉 B. 推送订阅 | **A** | 简单可靠，30s 延迟可接受 |
| 批次状态机 | A. 状态字段 B. 状态机框架 | **A** | 沿用 ProFlowCard 既定模式 |
| NCR 与返工关系 | A. NCR 单独 B. NCR 嵌入 QC | **A** | 独立模块，对标西门子 |
| SOP 文件存储 | A. 本地 B. OSS（已有） | **B** | 沿用 ruoyi-oss |
| 测试覆盖率工具 | JaCoCo | **JaCoCo** | Spring Boot 标配 |

---

## 五、开发优先级与实施步骤

### 5.1 优先级排序（按依赖关系）

```
P0-7 单元测试 ─┐
P0-9 批次管理 ─┤
P0-2 工装管理 ─┤─── 无外部依赖,先做
              │
P0-3 进站出站 ─┤─── 依赖 P0-2(工装校验)、P0-9(批次校验)
              │
P0-1 物料拉动 ─┤─── 依赖 P0-3(齐套校验)、P0-9(批次FEFO)
              │
P0-8 IoT 打通 ─┤─── 独立,可并行
              │
P0-4 NCR+返工 ─┤─── 依赖 P0-3(出站校验触发NCR)
              │
P0-5 E-SOP ────┤─── 依赖 P0-3(工序指引)
              │
P0-6 电子签名 ─── 最后,被所有模块依赖
```

### 5.2 分阶段实施路线图

#### Phase 4-A：基础设施 + 主数据类（Week 28-30，3.5 周）

> **进度注（2026-07-26）**：P0-9 已于 2026-07-24 ~ 07-26 提前完工（编码+整改+前端打磨），早于计划 08-03。运行时验证待闭合。Phase 4-A 实际已启动，P0-2 计划已就绪（[实施方案](./2026-07-26-mes-p0-2-tooling-management-implementation-plan.md)）。

- **Week 28**（2026-08-03 ~ 08-09）：P0-7 单元测试补齐 + P0-9 批次管理（并行）- P0-9 ✅ 已完成，P0-7 待启动
- **Week 29**（2026-08-10 ~ 08-16）：P0-2 工装/模治具管理
- **Week 30 上半周**（2026-08-17 ~ 08-19）：P0-7 收尾 + 集成测试

#### Phase 4-B：生产执行类（Week 30-32，2.5 周）

- **Week 30 下半周**（2026-08-20 ~ 08-23）：P0-3 任务进站/出站 + 流程指引
- **Week 31**（2026-08-24 ~ 08-30）：P0-1 物料拉动配送 + 齐套管理
- **Week 32 上半周**（2026-08-31 ~ 09-06）：P0-8 IoT 自动采集打通（并行）

#### Phase 4-C：质量与合规类（Week 32-36，4 周）

- **Week 32 下半周**（2026-09-04 ~ 09-06）：P0-4 NCR + 返工流程启动
- **Week 33**（2026-09-07 ~ 09-13）：P0-4 完成
- **Week 34**（2026-09-14 ~ 09-20）：P0-5 E-SOP 电子作业指导书
- **Week 35**（2026-09-21 ~ 09-27）：P0-6 电子签名 + DHR 批记录
- **Week 36 上半周**（2026-09-28 ~ 10-04）：P0-6 完成

#### Phase 4-D：集成与验收（Week 36-37，2 周）

- **Week 36 下半周**：跨模块端到端集成测试
- **Week 37**（2026-10-05 ~ 10-11）：性能测试、用户验收测试、文档归档

### 5.3 各阶段详细任务（甘特图节选）

```
任务\周次        W28 W29 W30 W31 W32 W33 W34 W35 W36 W37
P0-7 测试补齐    ███ ███ ░░░
P0-9 批次管理    ███ ░░░
P0-2 �装管理         ███ ░░░
P0-3 进站出站             ███
P0-1 物料拉动                  ███
P0-8 IoT 打通                  ███ ░░░
P0-4 NCR+返工                      ███ ███
P0-5 E-SOP                              ███
P0-6 电子签名                                ███ ███
集成测试                                          ███
验收+文档                                              ███
```

---

## 六、资源分配

### 6.1 人力资源

| 角色 | 人数 | 投入比例 | 主要任务 |
|------|------|---------|---------|
| AI Agent（主力） | 1 | 100% | 编码、单元测试、文档 |
| 后端开发（人审+协同） | 1 | 30% | 代码评审、复杂业务设计 |
| 前端开发 | 1 | 50% | 前端页面、E2E 测试 |
| 测试工程师 | 1 | 25% | 集成测试、性能测试 |
| 项目经理 | 1 | 15% | 进度跟踪、风险协调 |

### 6.2 技术资源

| 资源 | 用途 | 来源 |
|------|------|------|
| MySQL 8.0 | ry-mes + ry-vue 库 | 已有 |
| Redis | 缓存、Lock4j | 已有 |
| RabbitMQ | 跨模块消息 | 已有 |
| MinIO/OSS | SOP 文件存储 | 已有 |
| JaCoCo | 覆盖率报告 | 新增依赖 |
| Flowable | SOP 审批流 | 已有 |
| Lock4j | 派工并发控制 | 已有 |

### 6.3 环境资源

| 环境 | 用途 | 规格 |
|------|------|------|
| 开发环境 | AI Agent + 开发人员 | 已有 |
| 测试环境 | 集成测试、E2E | 已有 |
| 性能测试环境 | P0 完成后压测 | 临时搭建（4C8G + MySQL 8C16G） |

---

## 七、时间节点

### 7.1 里程碑计划

| 里程碑 | 日期 | 交付物 | 验收方 |
|--------|------|--------|--------|
| M1: Phase 4-A 完成 | 2026-08-19 | 测试覆盖率 60%+、批次+工装模块 | PM + PO |
| M2: Phase 4-B 完成 | 2026-09-06 | 进站出站 + 物料拉动 + IoT 打通 | PM + PO |
| M3: Phase 4-C 完成 | 2026-10-04 | NCR + E-SOP + 电子签名 DHR | PM + PO |
| M4: 集成测试通过 | 2026-10-11 | 全链路 E2E 通过 | QA + PO |
| M5: 项目验收 | 2026-10-25 | 文档归档、性能基线 | PO + 用户 |

### 7.2 周度交付节奏

每周五 17:00 前提交：
- 周报（含进度、风险、下周计划）
- 周度 SQL 脚本（如 `mes-weekXX-YY-xxx.sql`）
- 周度菜单脚本（如 `mes-weekXX-ZZ-menu.sql`）
- 周度验证报告（参考 [mes-week21-verification-report.md](file:///d:/workspace/nexus-erp/doc/mes/plans/2026-06-19-mes-week21-verification-report.md) 格式）

### 7.3 菜单 ID 段分配

沿用既有约定（Week 17-29 + P0-9 已用至 4171），新增分配（2026-07-26 校正后）：

| Phase | 模块 | 菜单 ID 段 | 父菜单 | 备注 |
|-------|------|----------|--------|------|
| 4-A | P0-9 批次 | 4006 + 4152-4171 | 4006 主数据（新建） | ✅ 已落地（原分配 4166-4175，实际偏移） |
| 4-A | P0-2 工装 | 4205 + 4172-4195 | 4205 工装管理（新建 M 目录） | 原分配 4150-4165 被 P0-9 占用；4003 不存在，新建 4205 M 目录作父菜单 |
| 4-B | P0-3 进站出站 | 4200-4215 | 4001 生产执行 | 原分配 4176-4180，后移 |
| 4-B | P0-1 物料拉动 | 4216-4240 | 4006 物料管理（新建） | 原分配 4181-4195，后移 |
| 4-B | P0-8 IoT 打通 | - | 扩展现有 4004 数据采集 | 不新增菜单 |
| 4-C | P0-4 NCR | 4241-4260 | 4002 质量管理 | 原分配 4196-4210，后移 |
| 4-C | P0-5 E-SOP | 4206(M) + 4261-4275(C/F) | 4206 文档管理（新建 M 目录） | 原分配 4211-4220 后移至 4261-4280；M-dir 4206 接续 4205（工装）；file_url 改 file_oss_id（OSS 私有桶） |
| 4-C | P0-6 电子签名 | 4281-4300 | 系统管理下 | 原分配 4221-4230，后移 |

> ⚠️ **校正说明（2026-07-26）**：P0-9 实际占用 `4006 + 4152-4171`，原 §7.3 的 P0-2 段 `4150-4165` 冲突。全 MES 菜单 max 已用 = 4171。本表为校正后映射，每模块预留 20-25 ID 余量避免再溢出。详见 [P0-2 实施方案 §七](./2026-07-26-mes-p0-2-tooling-management-implementation-plan.md)。

---

## 八、质量标准

### 8.1 代码质量

| 指标 | 标准 | 验证方式 |
|------|------|---------|
| 编译 | `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过 | CI |
| Lint（前端） | `eslint --ext .vue,.ts` 0 error | CI |
| SonarQube | Blocker/Critical = 0，Major ≤ 5/模块 | CI |
| 代码规范 | 遵循 [阿里 Java 开发手册](https://github.com/alibaba/p3c) + NexusERP 既有约定 | 人工评审 |
| 重复代码 | <5% | SonarQube |
| 圈复杂度 | 单方法 ≤ 15 | SonarQube |

### 8.2 测试质量

| 指标 | 标准 | 验证方式 |
|------|------|---------|
| 单元测试覆盖率（行） | ≥60% | JaCoCo |
| 单元测试覆盖率（分支） | ≥50% | JaCoCo |
| SPC 算法模块覆盖率 | ≥80% | JaCoCo |
| 单元测试通过率 | 100% | `mvn test` |
| 集成测试用例数 | ≥30 | 测试报告 |
| E2E 用例数 | ≥15 | Playwright |
| 性能基线 | P95 API 响应 <500ms | JMeter |

### 8.3 文档质量

- 每周度计划文档（参考既有 [doc/mes/plans](file:///d:/workspace/nexus-erp/doc/mes/plans/archive) 风格）
- 接口文档：使用 Swagger 注解，自动生成
- 数据库文档：每张表必须有 COMMENT
- 用户手册：P0 完成后更新 [NexusERP_MES文档.md](file:///d:/workspace/nexus-erp/doc/mes/NexusERP_MES文档.md)

---

## 九、验收标准

### 9.1 功能验收

每个 P0 功能项必须满足以下验收点（用户故事场景）：

| 编号 | 验收场景 | 验收标准 |
|------|---------|---------|
| P0-1 | 工单开工前齐套校验 | 调用 `/kitting/check/{woId}` 返回齐套状态，缺料行展示在缺料清单 |
| P0-1 | 看板生成与配送 | 工单生成配送看板，配送任务全状态流转，线边库存更新 |
| P0-2 | 工装寿命到期预警 | 工装使用次数达阈值时状态变为"待保养"，触发预警 |
| P0-2 | 工装保养后归零 | 保养记录创建后，工装 used_count 归零 |
| P0-3 | 工序进站校验拦截 | 物料不齐套时进站失败，返回未通过原因 |
| P0-3 | 跳站防错 | 无权限跳站被拒绝 |
| P0-4 | IQC 不良生成 NCR | IQC 录入不良数时自动创建 NCR 单 |
| P0-4 | NCR 派生返工工单 | NCR 处置为 REWORK 时生成返工工单 |
| P0-5 | 工序 SOP 推送 | 工单进站后自动推送 SOP 到工位 |
| P0-5 | SOP 版本切换 | 旧版本归档，新版本生效 |
| P0-6 | 电子签名接入 | 关键事件必须签名才能完成，签名记录含哈希 |
| P0-6 | DHR 导出 | 工单完工后 DHR 可导出 PDF |
| P0-7 | 单元测试全绿 | `mvn test` 全部通过，覆盖率达标 |
| P0-8 | IoT 数据自动归档 | 设备数据 30s 内出现在 dc_data_record |
| P0-8 | 设备状态联动 | IoT 设备离线时 eq_equipment.status 更新 |
| P0-9 | 批次 FEFO 推荐 | 出库时按先到期先出推荐批次 |
| P0-9 | 批次隔离 | 批次隔离后无法出库 |

### 9.2 性能验收

| 接口类型 | P95 响应时间 | TPS |
|---------|------------|-----|
| 列表查询（分页） | <300ms | ≥50 |
| 详情查询 | <200ms | ≥100 |
| 写入操作 | <500ms | ≥20 |
| IoT 归档任务 | 单次 <5s | - |
| DHR PDF 导出 | <10s（10 万条记录内） | - |

### 9.3 安全验收

- 权限：所有新增接口配置 `@SaCheckPermission`
- 多租户：所有表含 `tenant_id`，租户隔离正确
- 审计：所有关键操作记录 `es_audit_trail`
- 电子签名：不可篡改（哈希校验）
- SQL 注入：使用 MyBatis Plus 参数化，禁止字符串拼接

---

## 十、风险评估与应对策略

| 编号 | 风险描述 | 等级 | 概率 | 应对策略 | 责任人 |
|------|---------|------|------|---------|--------|
| R1 | P0-7 测试补齐发现历史 Bug，引发回归 | 高 | 高 | 测试发现的 Bug 单独建 Issue，不阻塞 P0 主线，分批修复 | BE |
| R2 | P0-8 IoT 服务跨模块调用性能瓶颈 | 高 | 中 | 增加本地缓存（5min TTL），批量查询，降级开关 | BE |
| R3 | P0-6 电子签名性能影响主流程 | 中 | 中 | 异步签名（MQ），主流程不等待，签名失败告警 | BE |
| R4 | P0-1 物料拉动与 ERP 库存数据不一致 | 高 | 中 | 跨库查询走只读副本，对账任务每日跑 | BE |
| R5 | P0-4 NCR 状态机复杂，边界多 | 中 | 高 | 状态机用枚举+显式校验，单元测试覆盖所有转换 | BE |
| R6 | P0-5 SOP 大文件上传/下载性能 | 低 | 中 | 使用 MinIO 分片上传，前端分块加载 | FE |
| R7 | 菜单 ID 段冲突 | 低 | 低 | 严格按 §7.3 分配，提交前 SQL 校验 | BE |
| R8 | 多租户场景下隔离失效 | 高 | 低 | 集成测试覆盖多租户场景，自动校验 tenant_id | QA |
| R9 | 测试环境数据不足，无法验证 | 中 | 中 | 提供数据初始化脚本，覆盖典型业务流 | QA |
| R10 | AI Agent 编码质量参差 | 中 | 中 | 每个任务后人工评审，关键模块双评审 | BE |
| R11 | Flowable 工作流配置错误阻塞业务 | 中 | 低 | SOP 审批流先做兜底（管理员强制通过） | BE |
| R12 | 跨模块 MQ 消息丢失 | 高 | 低 | 启用 RabbitMQ 持久化 + 手动 ACK + 死信队列 | BE |

### 10.1 风险监控机制

- 每日站会（15min）同步风险
- 每周风险评审，更新风险登记册
- 红色风险（高概率+高影响）触发 PM 升级机制

---

## 十一、测试计划

### 11.1 单元测试（P0-7 任务）

详见 §3.7。覆盖范围：pro/qc/spc/eq/common 五个模块，目标覆盖率 60%+。

**测试金字塔**：
```
       /  E2E (15)  \           ← 11.3
      / 集成测试(30)  \          ← 11.2
     /  单元测试(80+)  \         ← 11.1
```

### 11.2 集成测试

| 模块间集成 | 测试场景 | 用例数 |
|----------|---------|--------|
| P0-1 ↔ ERP | 齐套校验跨库查询 | 3 |
| P0-1 ↔ P0-3 | 进站触发齐套校验 | 2 |
| P0-3 ↔ P0-2 | 进站校验工装可用性 | 2 |
| P0-3 ↔ P0-9 | 进站校验批次状态 | 2 |
| P0-4 ↔ P0-3 | 出站不良触发 NCR | 2 |
| P0-4 ↔ pro | NCR 派生返工工单 | 2 |
| P0-5 ↔ P0-3 | 工序指引推送 SOP | 2 |
| P0-6 ↔ 全模块 | 关键事件签名 | 5 |
| P0-8 ↔ dc | IoT 数据归档 | 3 |
| P0-8 ↔ eq | 设备状态联动 | 2 |
| P0-9 ↔ pro | 批次绑定与追溯 | 3 |
| **合计** | | **30** |

### 11.3 端到端测试（E2E）

使用 Playwright（沿用 [Nexus_App E2E 脚手架](file:///d:/workspace/nexus-erp) 模式），覆盖核心业务流：

| E2E 场景 | 步骤 | 用例数 |
|---------|------|--------|
| 完整工单流 | 工单下达→齐套校验→进站→作业（带 SOP）→出站→完工→DHR 导出 | 3 |
| 不合格品闭环 | 检验录入不良→NCR 生成→处置决策→返工工单→返工完工 | 2 |
| IoT 联动 | 设备数据采集→归档→设备状态变更→安灯 | 2 |
| 批次追溯 | 批次入库→工单消耗→SN 绑定→前向/后向追溯 | 2 |
| 工装生命周期 | 工装领用→使用计数→寿命预警→保养→归零 | 2 |
| 物料拉动 | 看板生成→配送→送达确认→线边库存更新 | 2 |
| 电子签名 | 关键事件签名→签名校验→DHR 包含签名 | 2 |
| **合计** | | **15** |

### 11.4 性能测试

**测试工具**：JMeter 5.5

**测试场景**：
1. **基准测试**：单接口循环 1000 次，统计 P50/P95/P99
2. **并发测试**：50 并发持续 10 分钟，观察 TPS 与错误率
3. **压力测试**：阶梯加压（10→50→100→200 并发），找拐点
4. **稳定性测试**：100 并发持续 1 小时，观察内存泄漏

**测试对象**（优先级排序）：
- `/mes/wm/kitting/check/{woId}`（齐套校验，跨库）
- `/mes/dc/data-record/list`（IoT 数据查询，高频）
- `/mes/spc/control-chart/analyze/{chartId}`（算法密集）
- `/mes/pro/flow-card/entry`（进站校验，业务高频）
- `/mes/dhr/export`（PDF 导出，CPU 密集）

**性能基线**：完成后写入 [doc/mes](file:///d:/workspace/nexus-erp/doc/mes) 作为后续优化对照。

### 11.5 用户验收测试（UAT）

- 邀请 2-3 名业务用户参与
- 测试用例：基于 §9.1 验收场景
- 通过率：100%（必须）
- 缺陷处理：Critical 必须修复，Major 评估后处理

---

## 十二、交付物清单

### 12.1 代码交付物

| 类型 | 数量 | 路径 |
|------|------|------|
| Java 源文件 | ~120 | [ruoyi-mes/.../com/nexus/mes/{wm,tg,nr,sop,lt,dc,pro,common/es,common/audit}](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes) |
| Java 测试文件 | ~20 | [ruoyi-mes/src/test/java/com/nexus/mes](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes) |
| Vue 页面 | ~30 | [plus-ui/src/views/mes/{wm,tg,nr,sop,lt}](file:///d:/workspace/nexus-erp/plus-ui/src/views/mes) |
| TypeScript API | ~25 | [plus-ui/src/api/mes/{wm,tg,nr,sop,lt}](file:///d:/workspace/nexus-erp/plus-ui/src/api/mes) |
| SQL DDL 脚本 | ~12 | [doc/mes/sql](file:///d:/workspace/nexus-erp/doc/mes/sql) |
| 菜单 SQL | ~8 | [doc/mes/sql](file:///d:/workspace/nexus-erp/doc/mes/sql) |

### 12.2 文档交付物

| 文档 | 路径 |
|------|------|
| 本计划书 v1.0 | [doc/mes/plans/2026-07-24-mes-p0-development-plan-v1.0.md](file:///d:/workspace/nexus-erp/doc/mes/plans/2026-07-24-mes-p0-development-plan-v1.0.md) |
| 周度计划（10 份） | [doc/mes/plans](file:///d:/workspace/nexus-erp/doc/mes/plans) |
| 周度验证报告（5 份） | [doc/mes/plans](file:///d:/workspace/nexus-erp/doc/mes/plans) |
| 阶段验收报告（4 份） | [doc/mes/plans](file:///d:/workspace/nexus-erp/doc/mes/plans) |
| 最终验收报告 | [doc/mes/plans/2026-10-25-mes-p0-final-acceptance-report.md](file:///d:/workspace/nexus-erp/doc/mes/plans/2026-10-25-mes-p0-final-acceptance-report.md) |
| 更新主文档 | [doc/mes/NexusERP_MES文档.md](file:///d:/workspace/nexus-erp/doc/mes/NexusERP_MES文档.md) |
| 性能基线报告 | [doc/mes/performance-baseline-2026-10.md](file:///d:/workspace/nexus-erp/doc/mes/performance-baseline-2026-10.md) |

### 12.3 数据库变更交付物

- 12 个 DDL 脚本（每个 P0 项 1-2 个）
- 1 个数据迁移脚本（既有 QC 不良数据迁移到 NCR）
- 8 个菜单 SQL（每个 Phase 2 个）

---

## 十三、附录

### 13.1 术语表

| 术语 | 全称 | 说明 |
|------|------|------|
| MESA | Manufacturing Enterprise Solutions Association | 国际制造执行系统协会 |
| ISA-95 | ANSI/ISA-95 | 企业与控制系统集成国际标准 |
| MES | Manufacturing Execution System | 制造执行系统 |
| DHR | Device History Record | 器械历史记录（批记录） |
| NCR | Non-Conformance Report | 不合格品报告 |
| 8D | Eight Disciplines | 八步问题解决法 |
| SOP | Standard Operating Procedure | 标准作业程序 |
| FEFO | First Expired First Out | 先到期先出 |
| JIT | Just-In-Time | 准时制生产 |
| OEE | Overall Equipment Effectiveness | 设备综合效率 |
| Cpk | Process Capability Index | 过程能力指数 |
| RFT | Right First Time | 一次合格率 |
| 21 CFR Part 11 | FDA 电子记录电子签名法规 | 美国食品药品管理局合规要求 |

### 13.2 参考文档

- [NexusERP_MES文档.md](file:///d:/workspace/nexus-erp/doc/mes/NexusERP_MES文档.md) — 主设计文档
- [mes-completion-linkage-analysis.md](file:///d:/workspace/nexus-erp/doc/system/mes-completion-linkage-analysis.md) — 完成度与联动分析
- [2026-07-19-im-p5-development-plan.md](file:///d:/workspace/nexus-erp/doc/mes/plans/2026-07-19-im-p5-development-plan.md) — P5 风格参考
- [mes-week27-implementation-plan.md](file:///d:/workspace/nexus-erp/doc/mes/plans/archive/2026-06-25-mes-week27-implementation-plan.md) — 周度计划模板
- [MESA 11 Functions](https://www.symestic.com/en-us/what-is/mesa-11)
- [Siemens Opcenter 2604 新功能](https://blogs.sw.siemens.com/opcenter/whats-new-in-opcenter-execution-electronics-2604/)
- [FDA 21 CFR Part 11 电子记录电子签名法规](https://www.fda.gov/regulatory-information/search-fda-guidance-documents)

### 13.3 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|------|------|--------|---------|
| v1.0 | 2026-07-24 | AI Agent | 初始版本,基于 P0 对标分析创建 |
| v1.1 | 2026-07-26 | AI Agent | 同步:P0-9 完工状态、P0-2 DDL 5->4 表合并、菜单 ID 段校正(4172+)、P0-7 范围补 lt、时间线 P0-9 提前 |
| v1.2 | 2026-07-26 | AI Agent | P0-5 E-SOP ✅ 完整交付(E2E 28/28)；P0-9 批次 ✅ 完整交付(DDL落库+并入ry-mes.sql+Javadoc)；lt/sop 两包 Javadoc 完善；doc/mes/sql 增量 SQL 清理(schema 已并入 ry-mes.sql + 总库备份) |

### 13.4 待评审开放问题

1. **P0-6 DHR PDF 导出**：使用 ruoyi-ai 还是独立组件？（建议独立，避免依赖）
2. **P0-8 IoT 数据归档频率**：30s 还是 10s？（建议 30s 起步，可配置）
3. **P0-4 NCR 与既有 qc.defectInfo JSON 字段关系**：迁移还是并存？（建议迁移，废弃 JSON 字段）
4. **P0-1 物料拉动是否引入 AGV**：v1.0 仅契约预留，v1.1 实施对接

---

**文档生成时间**: 2026-07-24
**编制者**: AI Agent
**审核状态**: 待评审 → 待用户批准 → 进入执行（Phase 4-A 起始 2026-08-03）
