# NexusERP 功能开发进度

> 主体核对日期: 2026-09-06；最近增量注记: 2026-09-28（下文 299 项表格仍是 08-29 历史统计，尚未重新逐项计数）
> 状态图例: ✅ 已完成 | 🚧 进行中 | ⬜ 未开发 | 🗑️ 暂时搁置
> 最近修订: 2026-09-06 按 [09-06 全项目审查报告](./system/2026-09-06-全项目审查报告.md) 复核回填——RAG+KG 混合检索 ✅、MES pro 单元测试 ✅、知识图谱已知问题修复 ✅；登记 08-29 后增量交付（APS 排产/缺料风险/检验标准模板/预测性维护/生产退料与上料交接/盘点扩展/移库两段式/批次质检门禁/PDA 作业接口/工位终端设备通道/AI 模型字典化）；同日按报告路线完成 P0 资金安全 → P1 财务库存并发 → P2 MES/公共库 → P3 前端低危四个修复批次（明细见报告七/八/九/十节），P3 含首页工作台接真实 API（/erp/dashboard/stats）；同日完成缺失功能 #1 委外订单(PCBA)业务闭环 MVP（BOM快照/工程文件/报价/批次级发料/收货自动完工/SN MQ 生成，明细见 09-06 审查报告第十一节）
> 历史修订: 2026-08-29 按 08-16 历史审查摘要（原报告未在当前仓库/可访问 Git 历史中找到） 勘误（库存预留前端/IM 已读未读/工作流加签转办均已实现），并登记 MES 固件模块 (fw) 与流程编排模块 (ruoyi-flow) 两个新交付模块

---

## 2026-09-28 增量｜工业决策本地 plus 迁移（未验收生产）

09-28 后端闭环与数据结构整合提交 `7a540891`、PC 工作台提交 `4cb71fa5`；本机 3306 存量库增量已执行并验证，详见 [本地迁移与合并记录](./ai/merge/2026-09-28-plus本地数据库迁移与合并记录.md)。这是本地 plus 操作，不证明已重启用户 IDE 服务、完成生产部署、模型实测或竞赛提交；08-29 功能计数不因此修改。

---

## 2026-09-26 增量｜工业交付风险闭环决策（当日功能分支快照）

ERP/AI/Flow/PC 与独立 Nexus-App 的工业决策链、40 条确定性服务案例和 5 项安全 CONTROL 已实现；本轮补历史排程关联资源的真实日历工时验证，缺日历与矛盾数值失败关闭。根文档见 [作品草稿](ai/competition/2026-工业决策-作品介绍.md)、[竞赛核验清单](ai/competition/提交核验清单.md) 与 [Benchmark 指南](ai/benchmark/delivery-risk/README.md)。本节不改写上表历史计数。

**以下仅为 2026-09-26 当日快照，09-28 本地迁移见上节。**截至该日：本地 plus 库没有导入竞赛 SQL；独立 MySQL/Redis/RabbitMQ/Flow 端到端、真实 40 案例数值、PC/App 全量静态检查中的历史错误、PDF/视频/公开远端及正式回执仍待处理。**本地功能提交不等于发布或竞赛提交。**

---

## 一、总体概览

| 模块 | 功能点数 | ✅ 已完成 | 🚧 进行中 | ⬜ 未开发 | 🗑️ 搁置 |
|------|---------|----------|----------|----------|---------|
| **ERP 基础数据 (bd)** | 20 | 20 | 0 | 0 | 0 |
| **ERP 销售管理 (so)** | 14 | 13 | 1 | 0 | 0 |
| **ERP 采购管理 (pu)** | 15 | 14 | 0 | 1 | 0 |
| **ERP 生产管理 (prd)** | 18 | 17 | 1 | 0 | 0 |
| **ERP 排产调度 (sch)** | 15 | 13 | 0 | 2 | 0 |
| **ERP 仓储管理 (wm)** | 39 | 37 | 1 | 1 | 0 |
| **ERP 跨模块 (MQ/枚举)** | 14 | 13 | 0 | 1 | 0 |
| **ERP 财务核心 (fin)** | 12 | 8 | 0 | 0 | 4 |
| **MES 制造执行** | 51 | 51 | 0 | 0 | 0 |
| **MES 固件管理 (fw)** | 6 | 6 | 0 | 0 | 0 |
| **流程编排 (ruoyi-flow)** | 8 | 8 | 0 | 0 | 0 |
| **IoT 物联网** | 18 | 16 | 0 | 2 | 0 |
| **AI 智能助手** | 25 | 21 | 2 | 2 | 0 |
| **IM 即时通讯** | 9 | 9 | 0 | 0 | 0 |
| **Pay 支付** | 5 | 4 | 0 | 1 | 0 |
| **Workflow 工作流** | 27 | 26 | 0 | 1 | 0 |
| **Report 报表** | 3 | 2 | 0 | 1 | 0 |
| **合计** | **299** | **278** | **5** | **12** | **4** |

> 注: 上表为 08-29 时点计数，不代表 09-28 功能总量。09-06 登记的增量交付（APS 排产、缺料风险预测、检验标准模板、预测性维护、生产退料/上料交接、盘点扩展、移库两段式、批次质检门禁、PDA 作业接口、工位终端设备通道等）已在各模块表格补行，计数未重算。

---

## 二、跨模块业务流程图

### 2.1 销售到收款 (Order-to-Cash)

```mermaid
flowchart TD
    A[客户订单] --> B[报价单]
    B -->|确认| C[转销售订单]
    C --> D{信用检查 + 定价引擎}
    D -->|通过| E[提交 Flowable 审批]
    D -->|不通过| F[拒绝 / 信用超限]
    E -->|审批通过| G[确认订单]
    G --> H[履行订单]
    H --> I[销售出库 - 扣减库存]
    H --> J[关联生产工单 MO]
    H --> K[关联采购订单 PO]
    G -->|需要退货| L[销售退货]
    L -->|审批| M[退货入库 - 增加库存]

    style A fill:#e1f5fe
    style C fill:#e1f5fe
    style I fill:#f3e5f5
    style L fill:#fff3e0
```

### 2.2 采购到付款 (Procure-to-Pay)

```mermaid
flowchart TD
    A[低库存预警] -->|自动生成| B[采购申请]
    B -->|审批| C[转采购订单]
    C -->|审批通过| D[到货通知]
    D -->|审批通过| E[自动创建物料入库单]
    E -->|审批通过| F[待上架]
    F --> G[上架操作 - 分配库位]
    G --> H[待入库]
    H --> I[执行入库]
    I --> J[虚拟库位扣减 + 目标库位增加]
    I --> K[事务提交后异步 MQ 回写]
    K --> L[更新采购订单收货数量]
    L --> M{全部收齐?}
    M -->|是| N[采购订单状态=RECEIVED]
    M -->|否| O[采购订单状态=PARTIALLY_RECEIVED]
    C -->|需要退货| P[采购退货]
    P -->|审批| Q[执行退货出库 - 扣减库存]

    style A fill:#fff3e0
    style I fill:#f3e5f5
    style K fill:#fce4ec
```

### 2.3 生产制造 (Manufacturing)

```mermaid
flowchart TD
    A[销售订单行] --> B[创建生产计划]
    B --> C[BOM 树计算]
    C --> D[生成物料需求 MRP]
    D --> E[生成工单]
    E --> F{工单类型}
    F -->|自制| G[下达工单]
    F -->|委外| H[委外订单]
    F -->|返工| I[返工单]

    G --> J[开始生产]
    J --> K[生产领料 - 扣减库存]
    K --> L[生产报工]
    L --> M[完工]
    M --> N[生产入库 - 增加库存]
    M -->|倒冲模式| O[MQ: 倒冲领料 + 成品入库]

    H --> P[下达 → 发料 → 开始]
    P --> Q[委外收货]
    Q --> R[MQ: 委外库存更新]

    I --> S[开始 → 发料 → 完成 → 收料]
    S --> T[回写报废数量到来源工单]

    style B fill:#e1f5fe
    style M fill:#e8f5e9
    style O fill:#fce4ec
```

### 2.4 仓储管理全景

```mermaid
flowchart LR
    subgraph 入库通道
        I1[采购入库]
        I2[生产完工入库]
        I3[委外收货]
        I4[返工入库]
        I5[杂项入库]
    end

    subgraph 库存中心
        ST[(wm_material_stock<br/>乐观锁 version)]
    end

    subgraph 出库通道
        O1[销售出库]
        O2[生产领料]
        O3[委外发料]
        O4[返工出库]
        O5[采购退货]
        O6[杂项出库]
    end

    subgraph 库内操作
        T1[调拨]
        T2[盘点调整]
    end

    subgraph 辅助功能
        A1[FIFO/FEFO 拣货]
        A2[上架策略]
        A3[移动平均估值]
        A4[正向/反向追溯]
        A5[安全库存预警]
        A6[库存对账]
        A7[批次/序列号/条码]
    end

    I1 & I2 & I3 & I4 & I5 --> ST
    ST --> O1 & O2 & O3 & O4 & O5 & O6
    ST --> T1 & T2

    style ST fill:#e8f5e9
```

### 2.5 跨模块总览

```mermaid
flowchart TB
    subgraph BD[BD 基础数据层]
        B1[物料] --- B2[BOM]
        B1 --- B3[客户]
        B1 --- B4[供应商]
        B1 --- B5[计量单位]
        B1 --- B6[车间/工位]
        B1 --- B7[序列号规则]
        B1 --- B8[工艺路线]
    end

    subgraph SO[SO 销售]
        S1[客户订单]
        S2[报价单]
        S3[销售订单]
        S4[退货]
        S5[价格表]
    end

    subgraph PU[PU 采购]
        P1[采购申请]
        P2[采购订单]
        P3[采购退货]
        P4[供应商评估]
    end

    subgraph PRD[PRD 生产]
        R1[生产计划]
        R2[物料需求]
        R3[工单]
        R4[报工]
        R5[委外/返工]
    end

    subgraph SCH[SCH 排班]
        SC1[排班计划]
        SC2[班次/班组]
        SC3[考勤]
        SC4[产能日历]
    end

    subgraph WM[WM 仓储]
        W1[库存]
        W2[12种出入库策略]
        W3[批次/序列号/条码]
        W4[追溯/估值/预警]
    end

    subgraph MQ[MQ 消息队列]
        M1[生产倒冲]
        M2[采购回写]
        M3[入库过账]
        M4[返工/委外库存]
    end

    BD --> SO & PU & PRD & SCH
    SO & PU & PRD --> WM
    PRD -->|完工| MQ
    PU -->|收货| MQ
    MQ -->|异步| WM

    style BD fill:#e3f2fd
    style WM fill:#e8f5e9
    style MQ fill:#fce4ec
```

### 2.6 MES 制造执行流程

```mermaid
flowchart TD
    A[ERP 工单下达] -->|MQ| B[MES 工单创建]
    B --> C[工序派工]
    C --> D[流转卡]
    D --> E[扫码报工]
    E --> F{质检}
    F -->|IPQC| G[过程检验]
    F -->|OQC| H[出货检验]
    G -->|合格| I[继续生产]
    G -->|不合格| J[安灯告警 Andon]
    I --> K[完工]
    K -->|MQ| L[ERP 工单完工回写]

    M[ERP 采购收货] -->|MQ| N[IQC 来料检验]
    N -->|合格| O[APPROVED]
    N -->|不合格| P[REJECTED]

    Q[IoT 设备数据] -->|SnailJob| R[数据采集归档]
    R --> S[SPC 控制图]
    S -->|OOC 超限| J

    style B fill:#e1f5fe
    style J fill:#ffebee
    style L fill:#e8f5e9
```

### 2.7 IoT 数据流

```mermaid
flowchart LR
    A[设备] -->|MQTT| B[内置 MQTT Broker]
    B --> C[IotStreamConsumer]
    C --> D[IotMqttTopicParser]
    D --> E[IotPropertyPayload]
    E --> F[IotDataValueResolver]
    F --> G[(MySQL 存储)]
    F --> H[IotDeviceLatestCache<br/>Redis]
    F --> I[IotAlertChecker]
    I -->|异常| J[IotAlertNotifier<br/>SSE 推送]
    H --> K[IotRealtimeSseManager<br/>前端实时监控]

    style B fill:#e3f2fd
    style J fill:#ffebee
    style K fill:#e8f5e9
```

---

## 三、ERP 模块功能进度

### 3.1 基础数据 (bd)

#### 第一层：基础 CRUD

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 物料管理 CRUD + 导入导出 | BdMaterialController | bd/material/index.vue | ✅ |
| 物料分类 CRUD (树形) | BdMaterialCategoryController | bd/category/index.vue | ✅ |
| 计量单位 CRUD | BdUnitOfMeasureController | bd/measure/index.vue | ✅ |
| 客户信息 CRUD + 导入导出 | BdClientController | bd/client/index.vue | ✅ |
| 供应商信息 CRUD + 导入导出 | BdVendorController | bd/vendor/index.vue | ✅ |
| 车间信息 CRUD + 导入导出 | BdWorkshopController | bd/workshop/index.vue | ✅ |
| 工作中心 CRUD + 导入导出 | BdWorkstationController | bd/workstation/index.vue | ✅ |
| 工序定义 CRUD + 导入导出 | BdProcessController | bd/process/index.vue | ✅ |
| 序列号规则 CRUD (MOD10/MOD11) | BdSerialNumberRuleController | bd/serial-number-rule/index.vue | ✅ |

#### 第二层：关联与规则

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 物料-供应商关系管理 CRUD | BdMaterialVendorController | bd/material-vendor/index.vue | ✅ |
| 物料-供应商价格对比报表 | BdMaterialVendorReportController | — | ✅ |
| BOM 管理 CRUD + 树形展开 | BdBomController | bd/bom/index.vue | ✅ |
| BOM 明细 CRUD + 导入导出 | BdBomDetailController | bd/bom/form.vue | ✅ |
| BOM 替代料管理 | BdBomAlternativeController | bd/bom/alternative/ | ✅ |
| 工艺路线 CRUD + 工序步骤 | BdRoutingController | bd/routing/index.vue | ✅ |

#### 第三层：高级能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| BOM 虚拟件跳层 + 来源类型编码统一 | BdBomController (enhanced) | bd/bom/index.vue | ✅ |
| BOM 成本卷积 | BdBomController.costRollup | — | ✅ |
| BOM 比较 | BdBomController.compare | — | ✅ |
| BOM N+1 消除 (批量预加载) | BdBomMapper batch methods | — | ✅ |
| 物料-供应商 isDefault 唯一性约束 | BdMaterialVendorController | — | ✅ |

---

### 3.2 销售管理 (so)

#### 第一层：基础 CRUD

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 客户订单 CRUD + 导入导出 | SoCustomerOrderController | so/customerorder/index.vue | ✅ |
| 销售报价单 CRUD + 导入导出 | SoQuotationController | so/quotation/index.vue | ✅ |
| 销售价格表 CRUD + 导入导出 | SoPriceListController | so/price-list/index.vue | ✅ |
| 销售合同 CRUD + 导入导出 | SoContractController | so/contract/index.vue | ✅ |
| 销售发票 CRUD + 导入导出 | SoInvoiceController | so/invoice/index.vue | ✅ |

#### 第二层：业务流转

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 销售订单 CRUD + 状态流转 | SoSalesOrderHeaderController | so/orderheader/index.vue | ✅ |
| 报价确认 → 转销售订单 | SoQuotationController.convertToOrder | so/quotation/form.vue | ✅ |
| 销售退货 CRUD + 确认/执行 | SoReturnController | so/return/index.vue | ✅ |
| 信用检查 + 定价引擎 | ICreditCheckService / IPricingEngineService | — | ✅ |
| Flowable 审批流集成 | SoSalesOrderHeaderController.submit/proc | — | ✅ |
| 销售订单行联动 (MO/PO) | SoSalesOrderLine.linkedMoId/linkedPoId | — | ✅ |

#### 第三层：高级能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 销售执行报表 + 客户排名 | SoSalesReportController | so/report/index.vue | ✅ |
| SO 履行流程重构 (跨模块联动) | SoSalesOrderHeaderController.fulfill | — | ✅ |
| 销售订单行 → 生产完工 → 入库联动 | 跨模块 MQ | — | 🚧 |

---

### 3.3 采购管理 (pu)

#### 第一层：基础 CRUD

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 采购申请 CRUD + 状态流转 | PuPurchaseRequisitionController | pu/requisition/index.vue | ✅ |
| 采购订单 CRUD + 状态流转 | PuPurchaseOrderController | pu/order/index.vue | ✅ |
| 采购合同 CRUD + 导入导出 | PuContractController | pu/contract/index.vue | ✅ |
| 采购退货 CRUD + 确认/执行 | PuPurchaseReturnController | pu/return/index.vue | ✅ |
| 供应商评估 CRUD + 计算 | PuVendorEvaluationController | pu/vendor-evaluation/ | ✅ |
| 部门请购单 CRUD | PoRequisitionController | po/requisition/index.vue | ✅ |

#### 第二层：业务流转

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 采购申请 → 转采购订单 | PuPurchaseRequisitionController.convert | — | ✅ |
| 采购订单审批流 (Flowable) | PuPurchaseOrderController.submit/proc | — | ✅ |
| 采购订单状态枚举 (完整状态机) | PurchaseOrderStatusEnum | — | ✅ |
| 采购订单行收货差异处理 | PuPurchaseOrderLineController | — | ✅ |
| 采购合同 → 采购订单联动 | PuContractController | pu/contract/index.vue | ✅ |
| 低库存自动生成采购申请 | generate-from-low-stock | — | ✅ |

#### 第三层：高级能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 采购执行报表 + 供应商交付率 | PuPurchaseReportController | pu/report/index.vue | ✅ |
| 采购比价 | — | pu/price-comparison/index.vue | ✅ |
| 部门请购 → 采购申请联动 | PoRequisition → PuRequisition | — | ⬜ |

---

### 3.4 生产管理 (prd)

#### 第一层：基础 CRUD

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 生产计划 CRUD + 版本管理 | PrdProductionPlanController | prd/prdplan/index.vue | ✅ |
| 生产工单 CRUD + 审批 | PrdWorkOrderController | prd/prdwork/index.vue | ✅ |
| 生产报工 CRUD + 工时统计 | PrdWorkReportController | prd/report/index.vue | ✅ |
| 物料需求清单 CRUD | PrdMaterialRequirementController | prd/requirement/index.vue | ✅ |
| 返工订单 CRUD + 状态流转 | PrdReworkOrderController | prd/rework/index.vue | ✅ |
| 委外订单 CRUD + 状态流转 | PrdSubcontractController | prd/subcontract/index.vue | ✅ |

#### 第二层：业务流转

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 生产计划 → BOM 计算 → 物料需求 | PrdProductionPlanController | — | ✅ |
| 物料需求 → 生成工单 (MRP) | PrdProductionPlanController | — | ✅ |
| 工单状态枚举 (完整状态机) | WoStatusEnum | — | ✅ |
| 产能日历管理 + 占用/释放 | PrdCapacityCalendarController | prd/capacity-calendar/ | ✅ |
| 工单资源分配 (设备/工装/人力) | PrdWorkOrderResourceController | prd/work-order-resource/ | ✅ |
| 工单状态原子更新防并发 | complete() 使用消息数量 | — | ✅ |

#### 第三层：高级能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 生产效率报表 | PrdProductionReportController | prd/report/index.vue | ✅ |
| 完工入库单号自动生成 + 库位选择 | WmProductionRecptController | wm/productionrecpt/ | ✅ |
| 生产计划 → 工单 → 完工状态流转全链路 | 跨模块 | — | 🚧 |
| 生产领料可用库存实时展示 | WmProductionIssueController | — | ✅ |
| MES 工单完工回写 ERP | ErpWorkOrderCompletedConsumer | — | ✅ |
| 倒冲模式 (完工后 MQ 异步扣减) | ProductionBackflushConsumer | — | ✅ |
| APS 智能排产引擎 (工单工序排程/甘特图/拖期分析/方案发布回写工单计划日期) | PrdScheduleEngine + PrdScheduleServiceImpl (含 AI Agent 工具) | prd/schedule/ | ✅ (2026-09-02, 4cd0f168/f7448e0d) |
| 供应链缺料风险预测 (MRP 毛需求+库存+在途推演+风险看板) | PrdShortageSimulator + PrdShortageRiskServiceImpl | prd/shortage/ | ✅ (2026-09-02, 1e913252) |

---

### 3.5 排产调度 (sch)

#### 第一层：基础 CRUD

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 班次定义 CRUD | SchShiftController | sch/shift/index.vue | ✅ |
| 班组设置 CRUD | SchTeamController | sch/team/index.vue | ✅ |
| 生产工人档案 CRUD | SchWorkerController | sch/worker/index.vue | ✅ |
| 考勤记录 CRUD | SchAttendanceController | sch/attendance/index.vue | ✅ |
| 节假日设置 CRUD | SchHolidayController | sch/holiday/index.vue | ✅ |
| 打卡规则 CRUD | SchPunchRuleController | sch/punch-rule/index.vue | ✅ |

#### 第二层：业务流转

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 排班计划 CRUD | SchPlanController | sch/plan/index.vue | ✅ |
| 计划-班组关联 | SchPlanTeamController | sch/plan/ | ✅ |
| 班组排班模板 (周/月) | SchTeamScheduleController | sch/plan/ | ✅ |
| 员工排班明细 | SchEmployeeScheduleController | sch/employee-schedule/ | ✅ |
| 产能日历 → 工单占用/释放 | PrdCapacityCalendarController | — | ✅ |

#### 第三层：移动端

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| App 端打卡考勤 (签到/签退/统计) | AppSchAttendanceController | 移动端 | ✅ |
| App 端排班查询 | AppSchAttendanceController | 移动端 | ✅ |
| WiFi/蓝牙位置验证打卡 | 打卡规则配置 | — | ⬜ |
| 考勤异常自动告警 | — | — | ⬜ |

---

### 3.6 仓储管理 (wm)

#### 第一层：基础结构

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 仓库管理 CRUD + 树形 | WmWarehouseController | wm/warehouse/index.vue | ✅ |
| 库区管理 CRUD | WmStorageAreaController | wm/warehouse/ | ✅ |
| 库位管理 CRUD + 批量生成 | WmStorageLocationController | wm/warehouse/ | ✅ |
| 库存清单 CRUD + 按分类查看 | WmMaterialStockController | wm/stock/index.vue | ✅ |
| 库存事务记录查询 | WmMaterialStockTxController | — | ✅ |
| 3D 仓库可视化 (M1 基础渲染) | WmWarehouseController.3d | wm/warehouse/3d.vue | ✅ |
| 3D 仓库 M2 交互增强 + M3 货架排版 | — | wm/warehouse/3d.vue | ✅ |
| 3D 仓库 M4 视觉升级（InstancedMesh 货物/货架钢架/标签/地面标线/图例过滤/库区显隐/视角预设/阴影 + 写实厂房 + 104 库位示例数据） | — | wm/warehouse/3d.vue + wm/components/{instancedLocations,rackFrame,sceneLabels,floorMarkings,warehouseBuilding}.ts | ✅ |

#### 第二层：入库通道

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 到货通知 CRUD | WmArrivalNoticeController | wm/arrivalnotice/ | ✅ |
| 到货通知行 CRUD | WmArrivalNoticeLineController | — | ✅ |
| 物料入库单 CRUD + 审批 | WmMaterialReceiveController | wm/receive/ | ✅ |
| 入库单行/明细 CRUD | WmMaterialReceiveLineController | wm/receive/ | ✅ |
| 执行入库 (虚拟库位→目标库位) | WmMaterialReceiveController.executeReceive | — | ✅ |
| 上架操作 (分配库位 + 确认) | WmMaterialReceiveController.confirmShelf | — | ✅ |
| 完工入库 (生产入库) | WmProductionRecptController | wm/productionrecpt/ | ✅ |
| 杂项入库 + 执行 | WmMiscRecptController | wm/miscrecpt/ | ✅ |

#### 第三层：出库通道

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 销售出库 CRUD + 审批 | WmSalesIssueController | wm/sales-issue/ | ✅ |
| 生产领料 + 执行 | WmProductionIssueController | wm/productionissue/ | ✅ |
| 杂项出库 + 执行 | WmMiscIssueController | wm/miscissue/ | ✅ |
| 采购退货出库 | PuPurchaseReturnController | pu/return/ | ✅ |
| 发料缺货逃生通道 (缺货关闭 + 原因留痕) | WmProductionIssueService | — | ✅ (2026-08-30, 4d70664b) |
| 发料批次质检门禁 (IQC 完成回写批次 qc_status + 拣货拦截开关) | WmBatchServiceImpl + PdaIssueServiceImpl | — | ✅ (2026-08-31, 47311593) |
| 生产退料单 (良品/不良/报废三类, PDA + PC 建单/详情) | WmMaterialReturnController + AppPdaReturnController | wm/return/ | ✅ (2026-09-01, c249ba85/a018af12/e82b21ce) |
| 上料交接单 (PDA 确认/签名 + PC 建单/查看) | WmMaterialHandoverController + AppPdaHandoverController | wm/handover/ | ✅ (2026-09-01, 94ab9a93) |

#### 第四层：库内操作

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 库存调拨 CRUD + 审批 + 执行 | WmTransferController | wm/transfer/ | ✅ |
| 盘点管理 CRUD + 生成明细 + 过账 | WmStockTakingPlanController | wm/stocktaking/ | ✅ |
| 安全库存预警 CRUD | WmStockAlertController | wm/stock-alert/ | ✅ |
| 库存台账校验 | WmStockReconciliationController | wm/reconciliation/ | ✅ |
| 盘点扩展 (盲盘遮蔽账面 + 临时盘点建单 + 动碰盘裁剪) | WmStockTakingPlan 扩展 | wm/stocktaking/ | ✅ (2026-09-01, 40a858e6) |
| 移库两段式 (行级下架核销 + 目标库位上架过账) | WmTransferService | wm/transfer/ | ✅ (2026-08-31, e96f0429) |

#### 第五层：辅助功能

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 拣货策略 (FIFO/FEFO) | WmPickingRuleController | wm/pickingrule/ | ✅ |
| 上架策略 | WmPutawayRuleController | wm/putawayrule/ | ✅ |
| 库存估值 (移动平均法) | WmValuationController | wm/valuation/ | ✅ |
| 正向/反向追溯 | WmTraceController | wm/trace/ | ✅ |
| 批次管理 CRUD | WmBatchController | wm/batch/ | ✅ |
| 序列号管理 CRUD | WmSnController | wm/sn/ | ✅ |
| 条码管理 CRUD + 生成 | WmBarcodeController | wm/barcode/ | ✅ |
| 条码规则配置 | WmBarcodeConfigController | wm/barcode/config/ | ✅ |
| 库存汇总 + 库龄分析报表 | WmInventoryReportController | wm/report/index.vue | ✅ |

#### 第六层：库存预留 (Phase B)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 库存预留 DDL + Entity + Mapper | WmStockReservation | — | ✅ |
| 库存预留 Service + 事务类型 | WmStockReservationService | — | ✅ |
| SO 确认 + 工单下达 + 出库消费集成 | 跨模块联动 | — | ✅ |
| 库存预留前端页面 | — | wm/reservation/ | ✅ |
| 拣货策略库存预留优化 | 拣货+预留联动 | — | ✅ |

#### 第七层：PDA 手持作业接口 (/app/pda, M1~M3)

> 2026-08-29~09-05 (7e666b0f 起一批)。客户端 nexus-pda 为外部独立仓库 (见 [客户端生态规划](./system/2026-08-19-Nexus客户端生态规划.md))。

| 功能 | 后端 | 状态 |
|------|------|------|
| 发料 (拣货/完工) + 收货确认 | AppPdaIssueController / AppPdaReceiveController | ✅ |
| 退料 (良品/不良/报废 confirm/finish) | AppPdaReturnController | ✅ |
| 移库两段式 (takeoff/execute) | AppPdaTransferController | ✅ |
| 盲盘 (create/count/post) | AppPdaStocktakingController | ✅ |
| 上料交接 (confirm/sign) | AppPdaHandoverController | ✅ |
| 主数据增量同步 + 待办聚合 | AppPdaBasedataController / AppPdaTodoController | ✅ (basedata 已补 wm:basedata:sync 权限点+种子, 09-06 P2) |
| 幂等组件 PdaIdempotent (失败释放占位键) | common-satoken pda 包唯一实现 | ✅ (09-06 P2: 原 ERP/MES 双套已合并下沉) |
| 权限点强制 + 退料权限种子 | 989887e6 | ✅ |

#### 搁置/未开发

| 功能 | 说明 | 状态 |
|------|------|------|
| 按仓库/库区/库位过滤库存查询 | 已部分实现（按仓库过滤） | 🚧 |
| 多仓库间自动调拨建议 | 未开发 | ⬜ |

---

### 3.7 跨模块 (MQ / 枚举)

| 功能 | 实现 | 状态 |
|------|------|------|
| 生产完工倒冲消费者 | ProductionBackflushConsumer | ✅ |
| 采购收货回写消费者 | PurchaseReceiveWritebackConsumer | ✅ |
| 入库消息分发消费者 | StockInConsumer | ✅ |
| 返工库存消费者 | ReworkStockConsumer | ✅ |
| 委外库存消费者 | SubcontractStockConsumer | ✅ |
| 工单下达消费者 (MES 占位) | WorkOrderReleaseConsumer | ✅ |
| MES 工单完工 → ERP 回写 | ErpWorkOrderCompletedConsumer | ✅ |
| IQC 完成 → ERP 质检状态回写 | ErpIqcCompletedConsumer | ✅ |
| 库存操作类型枚举 (14 种) | StockOperationTypeEnum | ✅ |
| 工单状态枚举 (7 状态 + 转换矩阵) | WoStatusEnum | ✅ |
| 采购订单状态枚举 (9 状态) | PurchaseOrderStatusEnum | ✅ |
| 跨库事务补偿框架 (7 层可靠性) | WmMqSendService + mq_message_record | ✅ |
| MQ 重试计数机制 | 修复后 | ✅ |
| 消息死信队列处理 | DeadLetterAdminController (查询/详情/人工重投/删除) + monitor/deadletter 页面 + 菜单 | ✅ (2026-09-06 P2, 死信运维闭环) |

---

## 四、MES 模块功能进度

### 4.1 生产管理 (pro)

#### 第一层：基础能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| MES 工单 CRUD + 状态流转 | ProWorkOrderController | pro/workorder/ | ✅ |
| 工序派工 (分配工人/工位) | ProDispatchController | pro/dispatch/ | ✅ |
| 流转卡 CRUD + 进度跟踪 | ProFlowCardController | pro/flow-card/ | ✅ |
| 流转卡创建数量校验 | ProFlowCardController | — | ✅ |
| 扫码报工 (ScanExecute) | ProDispatchController | pro/dispatch/ScanExecute.vue | ✅ |

#### 第二层：追溯与告警

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| SN 序列号管理 + 生成/分配 | ProSnController | pro/sn/ | ✅ |
| 组件级正向/反向追溯 | ProTraceabilityController | pro/sn/trace.vue | ✅ |
| 安灯告警 CRUD + SSE 推送 | ProAndonController | pro/andon/ | ✅ |
| MBOM (制造 BOM) 管理 | MesMbomController | pro/mbom/ | ✅ |
| 客户定制化发布包 (方案D: 固件映射/BOM 选配/配置项 + 工单固件确认与 FLASH 下达门禁) | ReleasePackageController + FirmwareServiceImpl + UpdateStepFirmwareBo | mes/release/package/ | ✅ (2026-08-22, cd362f51) |
| SN 标签打印 v1 | — | — | ✅ (2026-08-25, c28c2628) |

#### 第三层：进站/出站校验 (P0-3)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 任务进站/出站/跳站 (5 类规则) | ProFlowCardServiceImpl.entry/exit/skip | — | ✅ (commit 03f0098a) |
| 工序流程指引配置 | ProStepGuideController | pro/step-guide/ | ✅ |
| 工序校验规则配置 | ProStepCheckRuleController | pro/step-check-rule/ | ✅ |

#### 第四层：主数据桥接

| 功能 | 说明 | 状态 |
|------|------|------|
| MES 主数据 - BOM 查看 | 复用 ERP BdMaterialBomController | ✅ |
| MES 主数据 - 物料查看 | 复用 ERP BdMaterialController | ✅ |
| MES 主数据 - 工艺路线查看 | 复用 ERP BdRoutingController | ✅ |
| MES-IoT 设备软链接 | MesErpBridgeService | ✅ |

#### 第五层：PDA 手持作业接口 (mes/pda)

| 功能 | 后端 | 状态 |
|------|------|------|
| 扫码报工 (按派工单开工/完工, 幂等) | AppPdaReportController | ✅ (aa9b37de) |
| SN 绑定/解绑/查询 (按批次绑定+物料带出) | AppPdaSnController | ✅ (db36d127) |
| 设备点检 / 保养维保 (到期待办/详情/执行+保养日期推进) | AppPdaEqController / AppPdaEqMaintController | ✅ (2c2627d1/42f1e26a) |
| 质量三检 PDA (IQC/IPQC/OQC 待检/详情/提交+拍照附件) | AppPdaIqc/Ipqc/OqcController | ✅ (d2964d7c) |
| AQL 抽样试算 (GB/T 2828.1 简化版) | AppPdaQcAqlController (AqlSampler 唯一实现) | ✅ (d63f0c64/cbcd2aab) |
| 统一追溯查询门面 (M3 批次4) | AppPdaTraceController (ruoyi-admin) | ✅ (39c7c7b7) |
| 待办聚合 /app/pda/todo/summary | AppPdaTodoController | ✅ (de39c650) |

---

### 4.2 数据采集 (dc)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 采集点配置 (AUTO/MANUAL) | DcCollectionPointController | dc/collection-point/ | ✅ |
| 数据记录查询 + 归档 | DcDataRecordController | dc/data-record/ | ✅ |
| IoT 自动归档 dc_data_record | IotDataArchiveTask (SnailJob) | — | ✅ |
| AUTO 采集点 IoT 数据同步 | SnailJob 定时任务 | — | ✅ |

---

### 4.3 设备管理 (eq)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 设备台账 CRUD + 状态跟踪 | EqEquipmentController | eq/equipment/ | ✅ |
| 保养计划 CRUD | EqMaintenancePlanController | eq/maintenance/ | ✅ |
| 维修记录 CRUD + 成本 | EqRepairController | eq/repair/ | ✅ |
| 设备点检执行 + 异常联动安灯 | EqEquipmentController.inspection | eq/inspection/ | ✅ |
| 保养到期 SnailJob + SSE + 安灯 | MaintenanceDueTask | — | ✅ |
| 设备点检前端页面 + API | — | eq/inspection/ | ✅ |
| 设备 OEE 综合效率分析 | — | — | ✅ |
| 设备备件管理 | — | — | ✅ |
| 预测性维护 (EWMA 实时异常检测 + 设备健康评分 + 定时评估) | EqHealthServiceImpl + EquipmentHealthTask + common-iot EwmaUtil | eq/health/ | ✅ (2026-09-03, 801aceba/f0b00e10) |

---

### 4.4 质量管理 (qc)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| IQC 来料检验 CRUD + 合格/不合格 | QcIqcController | qc/iqc/ | ✅ |
| IQC 自动创建 (ERP 收货审批触发) | IqcAutoCreateConsumer | — | ✅ |
| IPQC 过程检验 CRUD | QcIpqcController | qc/ipqc/ | ✅ |
| OQC 出货检验 CRUD | QcOqcController | qc/oqc/ | ✅ |
| 不合格品处理流程 (MRB/NCR) | — | — | ✅ |
| 质量追溯 (批次→检验记录) | — | — | ✅ |
| 检验标准模板 + 明细自动判定引擎 | QcInspectTemplate/QcInspectItem + QcJudgeEngine | qc/template/ | ✅ (2026-09-02, dad81d73) |
| AQL 抽样 (GB/T 2828.1, AqlSampler 唯一实现 + plan 锚点单测) | mes/qc AqlSampler | — | ✅ (2026-09-03, cbcd2aab) |

---

### 4.5 统计过程控制 (spc)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 控制图定义 (X-bar/R 等, UCL/LCL/CL) | SpcControlChartController | spc/control-chart/ | ✅ |
| 数据点管理 | SpcDataPointController | spc/data-point/ | ✅ |
| SPC OOC 超限自动触发安灯 | SnailJob + Andon | — | ✅ |
| 过程能力分析 (Cp/Cpk) | — | — | ✅ |
| 控制图趋势分析前端可视化 | — | — | ✅ |

---

### 4.6 批次管理 (lt) — P0-9 ✅

> 实施: 2026-07-24~07-26, 编码+整改+前端打磨+DDL 落库(并入 `script/sql/ry-mes.sql`)。

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 批次主档 + 状态机 (IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED) | LtLotController | lt/lot/ | ✅ |
| 工单-批次消耗 (FEFO 推荐) | LtWoConsumptionController | lt/consumption/ | ✅ |
| SN-批次绑定 + 追溯链 | LtSnBindingController | lt/binding/ | ✅ |
| 批次隔离/放行/守卫 + IQC 结果钩子 | LtLotServiceImpl | — | ✅ |

### 4.7 工装/模治具管理 (tg) — P0-2 ✅

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 工装台账 + 寿命计数 | TgToolingController | tg/tooling/ | ✅ |
| 保养计划/记录 (计数归零) | TgMaintenancePlan/RecordController | tg/maintenance-plan/ tg/maintenance-record/ | ✅ |
| 使用/领用归还记录 | TgUsageRecordController | tg/usage-record/ | ✅ |

### 4.8 E-SOP 电子作业指导书 (sop) — P0-5 ✅

> 实施: 2026-07-26, 独立 `sop` 子包, 4 张 `sop_` 表, Flowable 审批 + 管理员兜底。

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| SOP 文档库 + 版本管理 | SopDocumentController / SopVersionController | sop/document/ | ✅ |
| 工序-SOP 绑定 (多对多, 版本指定) | SopProcessBindingController | sop/binding/ | ✅ |
| 查阅记录 (含电子签名预留) | SopViewLogController | sop/view-log/ + sop/viewer/ | ✅ |
| 工位推送 (进站触发, SSE 契约) | ProFlowCardServiceImpl → pushSopToWorkstation | — | ✅ |

### 4.9 NCR 不合格品 + 返工 (ncr) — P0-4 ✅

> 实施: 2026-07-28, 独立 `ncr` 子包 (5 张 `nr_` 表), 与既有 `qc.defectInfo` 并存, 4 触发点 (IQC/IPQC/OQC/过程), 隔离不回写 ERP。

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| NCR 单据 + 生命周期 (OPEN→REVIEW→DISPOSITION→IN_REWORK→CLOSED) | NcrOrderController | ncr/order/ | ✅ |
| 缺陷明细 + 缺陷代码库 (分层) | NcrDefectController / NcrDefectCodeController | ncr/defect/ ncr/defect-code/ | ✅ |
| 返工工单派生 (关联原工单) | NcrReworkOrderController | ncr/rework/ | ✅ |
| 8D 报告 (D1-D8) | Ncr8dReportController | ncr/8d-report/ | ✅ |

---

### 4.10 工位终端设备通道 (ruoyi-station) — M1/M2/M3 全量交付 ✅

> 2026-08-19~09-01 (6e495e27/c3c27907/867b9a15/fcccba4f/70cbe181/66efd0af/e75c9e12 等)。工位终端本体 nexus-station 为外部独立仓库 (见 [客户端生态规划](./system/2026-08-19-Nexus客户端生态规划.md))。

| 功能 | 说明 | 状态 |
|------|------|------|
| 过站报工 / SN 绑定追溯 / 点检 / 安灯 / 上料防错 / SOP 下发 / 刷写预备 | 8 Controller + 8 Service, 契约 API 级验通 | ✅ |
| 设备台账页 (在线状态/工位绑定/版本运维/当前操作员/主动下线) | plus-ui views/mes/station/device | ✅ (1c265281/228849cd) |
| 工位终端↔工作中心设备联动视图 + 工位码第6段归一化 | M3 双待办 | ✅ (ad8516a0) |
| 上报密钥 fail-closed (X-API-KEY 空值拒绝) | 29586fd0 | ✅ (遗留: 代码内默认密钥需去除, 见 09-06 审查报告 N1) |
| 单元测试 68 例 (7 个测试类) | ruoyi-station src/test | ✅ (fcccba4f) |

---

## 五、IoT 模块功能进度

### 5.1 设备管理 (device)

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 产品 (设备模板) CRUD | IotProductController | product/ | ✅ |
| 设备实例注册/管理 | IotDeviceController | device/ | ✅ |
| 设备在线/离线状态 | IotDeviceStatusCache (Redis) | — | ✅ |
| 设备密钥自动生成 | IotDeviceController | — | ✅ |
| 设备数据查询 | IotDeviceController | — | ✅ |
| 实时数据 SSE 推送 | IotRealtimeSseManager | device/realtime.vue | ✅ |

### 5.2 消息管道 (pipeline)

| 功能 | 说明 | 状态 |
|------|------|------|
| MQTT 消息流消费 | IotStreamConsumer | ✅ |
| Topic 解析 | IotMqttTopicParser | ✅ |
| 属性提取 | IotPropertyPayload | ✅ |
| 数据类型转换 | IotDataValueResolver | ✅ |
| 实时告警检测 | IotAlertChecker | ✅ |
| 告警通知推送 | IotAlertNotifier | ✅ |
| 设备最新数据缓存 (Redis) | IotDeviceLatestCache | ✅ |
| EWMA 实时异常检测 (在线检测/健康离线重放共用 EwmaUtil 唯一实现) | IotAnomalyDetector + EwmaUtil (common-iot) | ✅ (2026-09-04, 801aceba/f0b00e10) |
| 阈值告警与 EWMA ANOMALY 互斥抑制 (防双告警双安灯) | IotAlertChecker | ✅ (2026-09-04, ef139ae9) |

### 5.3 定时任务

| 功能 | 说明 | 状态 |
|------|------|------|
| 设备心跳检测 (15min 超时→OFFLINE) | IotDeviceHeartbeatTask | ✅ |
| 历史数据清理 (90 天) | IotDataCleanupTask | ✅ |
| Redis Stream 裁剪 (100K 上限) | IotStreamTrimTask | ✅ |

### 5.4 未开发

| 功能 | 说明 | 状态 |
|------|------|------|
| 设备 OTA 固件升级 | 远程升级 | ⬜ |
| 设备分组/标签管理 | 批量管理 | ⬜ |

---

## 六、AI 模块功能进度

### 6.1 核心 AI 能力

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| AI 厂商管理 CRUD | AiProviderController | model/provider.vue | ✅ |
| 模型配置 CRUD + 动态构建缓存 | AiModelController | model/index.vue | ✅ |
| AI 对话 (SSE 流式响应) | AiChatController | assistant/index.vue | ✅ |
| 会话管理 + 历史记录 | AiChatController | — | ✅ |
| Prompt 模板管理 | AiPromptTemplateController | prompt/index.vue | ✅ |
| Token 用量统计 + 趋势/排名 | AiTokenUsageController | tokenUsage/ | ✅ |
| SQL Few-Shot 示例管理 | AiSqlExampleController | sqlExample/ | ✅ |
| A/B 测试 (异步执行 + 人工评分) | AiAbTestController | abTest/ | ✅ |
| 文生图 (通义万象 + OSS 持久化) | AiImageController | imageGen/ | ✅ |
| 模型能力校验/厂商协议路由/连接测试/向量索引安全重建 | AiModelCapabilityEnum + AiModelConnectionTestService + rebuildIndex 确认门 | model/ | ✅ (2026-09-01, d4df50bd/dcc49f95/a6408f62) |

### 6.2 知识库与检索

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 知识库/文档/分块 CRUD | AiKnowledgeController | knowledge/ | ✅ |
| 文档上传 + 重试 | AiKnowledgeController | — | ✅ |
| 检索测试 | AiKnowledgeController | — | ✅ |
| 知识图谱构建/查询/邻居/社区/全局 | AiKnowledgeGraphController | knowledge/graph.vue | ✅ |
| 知识图谱全屏功能 | — | knowledge/graph.vue | ✅ |

### 6.3 MCP 工具

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| MCP 工具 CRUD + 测试执行 | McpToolController | mcp/ | ✅ |
| MCP 市场 (远程工具列表 + 一键加载) | McpMarketController | mcp/market.vue | ✅ |

### 6.4 AI 高级能力

| 功能 | 说明 | 状态 |
|------|------|------|
| 推理模式 (Reasoning) | S2-B | ✅ |
| 多厂商路由 + Rerank | S2-B | ✅ |
| 联网搜索 | S2-C | ✅ |
| 异步线程租户上下文传播 | AiRequestContext | ✅ |
| 自部署 NexusAI 集成 | 自有模型 | ✅ |
| RAG + 知识图谱混合检索 (向量+全文 RRF 融合, enableHybrid/hybridAlpha 可配) | RetrieverService | ✅ (09-06 复核确认) |
| Agent 自主工具调用 (多轮) | LangChain4j Agentic | 🚧 |
| 跨系统 Agent 编排 (MES/IoT/ERP) | Phase 3 | ⬜ |
| 工作流 Agent (审批自动化) | Phase 4 | ⬜ |

---

## 七、IM 即时通讯功能进度

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 私聊消息发送/接收 | ImChatController | chat/index.vue | ✅ |
| 群聊消息发送/接收 | ImChatController | — | ✅ |
| 会话列表 | ImChatController | — | ✅ |
| 群组 CRUD + 成员管理 | ImChatController | — | ✅ |
| 消息撤回 | ImChatController | — | ✅ |
| 系统通知 CRUD + 标记已读 | SysUserNotificationController | 系统组件 | ✅ |
| SSE 实时消息推送 | ImRealtimePush | — | ✅ |
| App 端即时通讯 (好友/群组/消息) | AppImController | 移动端 | ✅ |
| 消息已读/未读状态 | ImPrivateChatService/ImGroupChatService markRead | 已读回执信封 | ✅ |

---

## 八、Pay 支付功能进度

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 统一支付入口 (支付宝/微信) | PayController | paytest/ | ✅ |
| 支付订单管理 CRUD | PayOrderController | order/ | ✅ |
| 发票管理 CRUD | PayInvoiceController | invoice/ | ✅ |
| 支付回调处理 → ERP 自动收款/核销 | PayController + PayReceivedConsumer | — | ✅ (回调发 PAY_RECEIVED_CALLBACK → F1 自动收款/核销) |
| 最小 pay 收款入口 (模拟渠道回调) | fin.pay PayMockController | erp/fin/pay/mock.vue | ✅ (F1 补充, 解决自动收款断链) |
| 对账功能 | — | — | ⬜ (F6 暂缓, 无银行流水数据源) |

---

## 八·一、财务核心模块进度 (fin)

> 实施记录: 2026-07-29 财务核心实施计划（08-30 清理，当前仓库无原件）;各阶段 impl spec 见 `doc/erp/specs/`。
> 后端: `ruoyi-erp/.../com/nexus/erp/fin/{gl,arap,cost,fa,pay,report}`;前端: `plus-ui/src/views/erp/fin/`。

| 阶段 | 后端 | 前端 | 状态 |
|------|------|------|------|
| F0 GL 总账 (科目/凭证/期间/汇率/试算) | fin.gl (gl_account/gl_voucher/gl_period/gl_currency_rate) | erp/fin/gl/ (account/voucher/period/currencyRate/report) | ✅ E2E 通过 |
| F1 AR/AP 核销 (发票过账/收付款/核销/账龄) | fin.arap (FinArApReceipt/FinArApPayment/FinArApSettlement) + 3 Poster (AR/AP/存货) | erp/fin/receipt/ + erp/fin/settlement/ + erp/fin/arap/report | ✅ E2E 通过 |
| F2 固定资产 (卡片/折旧/处置) | fin.fa | erp/fin/fa/ (asset/category/depreciation/disposal) | ✅ E2E 通过 |
| F3-a 存货成本 (计价接线/成本过账/暂估) | fin.cost + wm 计价接线 | erp/fin/cost/ | ✅ E2E 通过 |
| F5 报表与期末关账 (三大报表/月结/年结) | fin.report | erp/fin/report/ (balance/income/cashFlow/itemConfig) + erp/fin/closing/ (monthly/yearly) | ✅ E2E 通过 |
| 最小 pay 收款入口 (模拟渠道回调) | fin.pay PayMockController | erp/fin/pay/mock.vue | ✅ E2E 通过 |
| F4 预算 | — | — | 🗑️ 暂缓 (无需求) |
| F6 银行对账 | — | — | 🗑️ 暂缓 (无银行流水数据源) |
| F7 电票 | — | — | 🗑️ 暂缓 (无需求) |
| F3-b 产品成本 (标准/实际成本/差异) | — | — | 🗑️ 暂缓 (MES 未成熟) |

---

## 九、Workflow 工作流功能进度

### 9.1 流程设计

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 流程分类管理 | WfCategoryController | category/ | ✅ |
| 流程模型管理 | WfModelController | model/ | ✅ |
| BPMN 流程设计器 | — | package/ProcessDesigner.vue | ✅ |
| 流程部署管理 | WfDeployController | deploy/ | ✅ |
| 设计器表单管理 | WfNformController | nform/ | ✅ |
| 业务自定义表单 | WfCustomFormController | custom-form/ | ✅ |

### 9.2 流程执行

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 待办任务列表 | WfTaskController | work/todo.vue | ✅ |
| 已办任务列表 | — | work/finished.vue | ✅ |
| 我发起的流程 | — | work/own.vue | ✅ |
| 可认领任务 | — | work/claim.vue | ✅ |
| 抄送列表 | — | work/copy.vue | ✅ |
| 发起新流程 | — | work/start.vue | ✅ |
| 流程详情查看 | — | work/detail.vue | ✅ |
| 流程详情嵌入业务表单 | WfCustomFormController | — | ✅ |

### 9.3 ERP 审批流集成

| 功能 | 状态 |
|------|------|
| 销售订单审批 | ✅ |
| 销售退货审批 | ✅ |
| 采购申请审批 | ✅ |
| 采购订单审批 | ✅ |
| 采购退货审批 | ✅ |
| 生产工单审批 | ✅ |
| 物料入库审批 | ✅ |
| 销售出库审批 | ✅ |
| 调拨单审批 | ✅ |
| 盘点计划审批 | ✅ |
| 到货通知审批 | ✅ |
| 自定义审批规则引擎 | ⬜ |
| 加签/转办/委派/撤回/撤销/跳跃 | ✅ |
| 催办 | ⬜ |

---

## 十、Report 报表功能进度

| 功能 | 前端 | 状态 |
|------|------|------|
| 报表仪表盘 (JimuReport) | report/dashboard/ | ✅ |
| 报表设计器 (JimuReport) | report/design/ | ✅ |
| 自定义数据源报表 | — | ⬜ |

---

## 十一、搁置/未开发功能汇总

### 🗑️ 暂时搁置

| 模块 | 功能 | 原因/备注 |
|------|------|----------|
| AI | Phase 3-6 (MES/IoT/跨系统/工作流 Agent) | 待 MES/IoT 成熟后启动 |

### ⬜ 未开发 (按优先级排列)

| 优先级 | 模块 | 功能 | 预估工时 |
|--------|------|------|----------|
| P1 | AI | 中优先级 Bug 11 项 (Rerank fallback 与知识图谱并发锁已修, 余项待核实) | 2d |
| P2 | IoT | 设备 OTA 固件升级 | 3d |
| P2 | IoT | 设备分组/标签管理 | 1d |
| P2 | AI | Agent 多轮自主工具调用 | 3d |
| P2 | AI | 低优先级 Bug 12 项 | 2d |
| P2 | AI | 知识图谱 v1.5 升级 (gleaning/embedding 匹配/Neo4j/Leiden) | 5d |
| P2 | MQ | 消息死信人工处理 (查询/重投接口与页面; 后端 DeadLetterRecord 落库已有) | ~~1d~~ ✅ (2026-09-06 P2) |
| P3 | MES | 电子签名 + DHR 批记录 (P0-6 延后, 预留点: sop_view_log.signature_id / ncr.disposition_signature_id) | 7.5d |
| P3 | ERP | 部门请购→采购申请联动 | 1d |
| P3 | ERP | 多仓库间自动调拨建议 | 2d |
| P3 | ERP | WiFi/蓝牙位置验证打卡 | 2d |
| P3 | ERP | 考勤异常自动告警 | 1d |
| P3 | Pay | 对账功能 | 2d |
| P3 | Report | 自定义数据源报表 | 1d |
| P3 | Workflow | 自定义审批规则引擎 | 3d |
| P3 | Workflow | 催办 | 1d |

---

## 十二、相关文档索引

| 文档 | 路径 |
|------|------|
| 全项目审查原基线 (08-16) | 原件不可用，勿按旧引用查阅 |
| 全项目审查报告 (09-06 复核轮) | [system/2026-09-06-全项目审查报告.md](./system/2026-09-06-全项目审查报告.md) |
| 数据库增量变更记录 | [system/数据库增量变更记录.md](./system/数据库增量变更记录.md) |
| ERP 业务流转分析 | [erp/NexusERP_业务流转分析.md](./erp/NexusERP_业务流转分析.md) |
| ERP 编码生成规则 | [erp/NexusERP_编码生成规则设计方案.md](./erp/NexusERP_编码生成规则设计方案.md) |
| ERP 主文档 | [erp/NexusERP_ERP文档.md](./erp/NexusERP_ERP文档.md) |
| PCBA 委外方案 | [erp/pcba-subcontract-feasibility-and-plan.md](./erp/pcba-subcontract-feasibility-and-plan.md) |
| MES 主文档 | [mes/NexusERP_MES文档.md](./mes/NexusERP_MES文档.md) |
| MES 刷写全链路指南 | [mes/plans/2026-08-17-mes-flash-fullchain-guide.md](./mes/plans/2026-08-17-mes-flash-fullchain-guide.md) |
| 硬件生产流程差异分析 | [mes/hardware-production-flow-analysis.md](./mes/hardware-production-flow-analysis.md) |
| 前瞻开发计划 (PLANS.md) | [PLANS.md](./PLANS.md) |
| IoT 主文档 | [iot/NexusERP_IoT文档.md](./iot/NexusERP_IoT文档.md) |
| AI 开发计划 | [ai/plans/NexusERP_AI开发计划.md](./ai/plans/NexusERP_AI开发计划.md) |
| 客户端生态规划 | [system/2026-08-19-Nexus客户端生态规划.md](./system/2026-08-19-Nexus客户端生态规划.md) |
| 工作流回归测试清单 | [system/workflow-regression-checklist.md](./system/workflow-regression-checklist.md) |
