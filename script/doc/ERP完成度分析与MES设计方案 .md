# ERP模块完成度分析与MES模块设计方案

> 生成日期: 2026-05-22
> 基于: nexus-erp (RuoYi-Vue-Plus 5.6.0) + ktg-mes 3.8.2 (苦糖果MES) 参考

---

## 目录

- [Part 1: ERP 模块完成度分析](#part-1-erp-模块完成度分析)
  - [1.1 各模块评分](#11-各模块评分)
  - [1.2 全局缺失模块](#12-全局缺失模块)
  - [1.3 对比标杆企业ERP差距总结](#13-对比标杆企业erp差距总结)
- [Part 2: ktg-mes 参考项目分析](#part-2-ktg-mes-参考项目分析)
  - [2.1 项目概况](#21-项目概况)
  - [2.2 技术栈对比](#22-技术栈对比)
  - [2.3 ktg-mes 功能模块](#23-ktg-mes-功能模块)
  - [2.4 终端覆盖](#24-终端覆盖)
  - [2.5 关键设计亮点](#25-关键设计亮点)
- [Part 3: MES 模块设计方案](#part-3-mes-模块设计方案)
  - [3.1 复用策略](#31-复用策略)
  - [3.2 模块包结构](#32-模块包结构)
  - [3.3 核心数据表设计](#33-核心数据表设计)
  - [3.4 关键API端点设计](#34-关键api端点设计)
  - [3.5 与现有ERP模块的集成映射](#35-与现有erp模块的集成映射)
  - [3.6 与ktg-mes的关键差异点](#36-与ktg-mes的关键差异点)
  - [3.7 前端视图规划](#37-前端视图规划)
  - [3.8 实施分期与任务清单](#38-实施分期与任务清单)
- [Part 4: 技术实施指南](#part-4-技术实施指南)
  - [4.1 新建MES模块步骤](#41-新建mes模块步骤)
  - [4.2 代码模板参考](#42-代码模板参考)
  - [4.3 关键设计文件索引](#43-关键设计文件索引)
  - [4.4 验证方案](#44-验证方案)

---

## Part 1: ERP 模块完成度分析

### 1.1 各模块评分

对比基准: SAP Business One、Oracle NetSuite、金蝶 K/3 WISE、用友 U8+

| 子模块 | 表数 | 完成度 | 已实现功能 | 主要缺口 |
|--------|------|--------|-----------|----------|
| **bd** 基础数据 | 11 | **65%** | 物料主数据(多单位)、物料分类(树形)、BOM(版本管理+有效期)、客户/供应商、客户信用管理、供应商-物料关系、计量单位(多单位转换)、车间、工位 | 工艺路线、工程变更(ECN)、物料交叉引用(客户料号/供应商料号)、替代BOM/工程BOM、序列号主数据、SOP/SIP文档管理、ATP规则、制造/采购默认标识 |
| **prd** 生产管理 | 6 | **45%** | 生产计划、工单(完整生命周期:状态/日期/优先级/班组/BOM)、工单资源(设备/人力需求)、报工(完成数/报废数)、物料需求计算(BOM分解→物料需求单) | 工艺路线定义、产能计划、物料预留/领料集成、委外管理、生产成本核算(实际vs标准)、联副产品处理、返工订单、工程变更影响分析、生产版本管理 |
| **sch** 排产调度 | 11 | **50%** | 班次定义(时间段)、班组/成员管理、工人主数据、排班计划(含班组排班)、员工排班(个人)、考勤打卡(含移动端:上班/下班/外勤/补卡)、假期日历、打卡规则(位置校验) | 高级排产算法(甘特图)、有限产能排程、物料可用性日期检查、工序级调度(非仅班次级)、what-if模拟、派工清单生成、实时排程看板、工时成本核算 |
| **so** 销售管理 | 3 | **25%** | 客户订单、销售订单头/行(基础订单录入) | 报价管理/机会管理、价格引擎(价格表/折扣/促销)、信用检查集成(已有credit表但未联动)、发货排程、退货/贷项处理(RMA)、销售佣金、收入确认、客户合同、框架协议/分批交货 |
| **wm** 仓储管理 | 12 | **50%** | 仓库→库区→库位三级层级、库存(含批次/供应商/仓库/库区/库位)、库存交易日志(审计追踪)、到货通知(含行)、物料入库(三级:收货单→收货行→收货明细)、销售出库(含行)、库存操作原子化服务 | 盘点/循环盘点、库位补货逻辑、上架策略、拣货/包装/发货流程、RF/条码扫描集成、库存估值(FIFO/加权平均/标准成本)、调拨(仓库间/库位间)、越库配送、寄售/委外库存、负库存管控 |
| **综合** | **43** | **~47%** | — | 流程密集型模块(生产、销售、仓储)完成度偏低，核心ERP模块(采购、财务、质量)整模块缺失 |

### 1.2 全局缺失模块

以下为企业级ERP标配但当前nexus-erp完全缺失的模块:

| 缺失模块 | 核心功能 | 优先级 | 备注 |
|---------|---------|--------|------|
| **采购管理** | 采购申请→采购订单→采购收货→供应商评估 | 高 | 与wm入库直接关联 |
| **财务管理** | 总账、应收应付、固定资产、成本会计 | 中 | 可与现有系统对账集成 |
| **质量管理** | IQC/PQC/OQC、检验计划、不合格品处理 | 高 | 可纳入MES的qc子模块 |
| **设备管理** | 设备台账、维护计划、故障记录、OEE | 高 | 可纳入MES的eq子模块 |
| **HR管理** | 招聘、培训、薪酬、绩效 | 低 | 已有sch排班考勤基础 |
| **项目管理** | 项目计划、WBS、项目成本 | 低 | 按需扩展 |
| **CRM** | 线索、商机、报价、活动管理 | 中 | 已有bd客户基础 |
| **PLM** | 产品生命周期、工程变更、文档管理 | 低 | 可与bd物料联动 |
| **报表BI** | 高级分析、仪表板、大屏展示 | 中 | 可集成现有报表工具 |

### 1.3 对比标杆企业ERP差距总结

```
nexus-erp 当前状态: ★★☆☆☆ (2/5星)

优势:
  ✓ 底层架构先进 (RuoYi-Vue-Plus 5.6.0, 多租户, 分布式支持)
  ✓ 基础数据模型扎实 (物料/BOM/单位/车间)
  ✓ 排班考勤体系完整 (含移动端)
  ✓ 仓储基础入库出库流程可用

劣势:
  ✗ 无采购模块 → 缺少供应链上游
  ✗ 无财务模块 → 缺少企业运营核心
  ✗ 生产管理仅到工单级 → 缺少车间工序级执行管控
  ✗ 销售仅有订单录入 → 缺少完整的报价到回款流程
  ✗ 仓储缺少条码/批次/SN追溯 → 缺少精细化库存管理
  ✗ 无质量管理体系 → 缺少品质保障
  ✗ 无设备管理体系 → 缺少资产维护

对标企业级ERP (SAP B1 / 金蝶K/3)，当前完成度约 30-35%
(企业级ERP通常覆盖: 财务+采购+销售+库存+生产+质量+设备+HR+项目+BI)
```

---

## Part 2: ktg-mes 参考项目分析

### 2.1 项目概况

| 属性 | 值 |
|------|-----|
| 项目名称 | 苦糖果MES (Kutangguo MES) |
| 本地路径 | `E:\mes\` |
| 主要子项目 | `ktg-mes/` (后端), `ktg-mes-ui/` (PC前端), `mes-master/ktg-mes-mobile-master/` (手机端), `mes-master/ktg-mes-pad-master/` (平板端) |
| 基础框架 | RuoYi-Vue (原始版本) |
| 版本 | 3.8.2 |
| 开源地址 | https://gitee.com/kutangguo/ktg-mes |
| 定位 | 离散制造MES系统，已在多家工厂实际部署 |
| 后端模块 | 9个Maven模块 (admin/common/framework/system/mes/quartz/generator/print/apicaller) |
| MES子模块 | 8个 (md/pro/wm/qc/dv/cal/tm/report) |
| 数据库 | MySQL, 约120张MES业务表 |

### 2.2 技术栈对比

| 维度 | ktg-mes (参考) | nexus-erp (目标) | 适配要求 |
|------|---------------|-----------------|---------|
| **基础框架** | RuoYi-Vue (原始) | RuoYi-Vue-Plus 5.6.0 | Plus版为完全重写，架构差异大 |
| **Java版本** | 8 | 17+ | 需要升级语法(records/pattern matching可用) |
| **Spring Boot** | 2.5.12 | 3.x | Jakarta EE迁移(javax→jakarta) |
| **ORM** | MyBatis + PageHelper | MyBatis-Plus 3.5.x | XML Mapper→BaseMapper, 查询方式不同 |
| **ID策略** | 数据库自增 | ASSIGN_ID (雪花算法) | 所有表的ID字段类型和生成方式需改变 |
| **认证鉴权** | JWT (jjwt 0.9.1) + 自定义Filter | Sa-Token + JWT | 认证机制完全不同，需用@SaCheckPermission |
| **多租户** | ❌ 无 | ✅ TenantEntity + 多租户插件 | **所有MES表需增加tenant_id字段** |
| **连接池** | Druid 1.2.8 | HikariCP | 配置变更 |
| **缓存** | Redis (Lettuce) | Redis (Redisson) | Redisson功能更强，API不同 |
| **分布式锁** | ❌ 无 | ✅ Lock4j (Redisson) | 关键业务需加分布式锁 |
| **前端框架** | Vue 2.6 + Element UI | Vue 3.5 + Element Plus + TypeScript | 语法、API、类型系统全部不同 |
| **API文档** | Swagger 2 (SpringFox) | SpringDoc (javadoc) | 注解方式切换 |
| **API加密** | ❌ 无 | ✅ AES+RSA动态加密 | 移动端需处理加密 |

### 2.3 ktg-mes 功能模块

| 子模块 | 包路径 | 核心实体 | 功能成熟度 | 对nexus-erp的参考价值 |
|--------|--------|---------|-----------|---------------------|
| **md** 主数据 | `com.ktg.mes.md` | 14个实体 | ⭐⭐⭐⭐⭐ | 物料/产品/BOM/SOP/SIP/工位-设备-工具-人员绑定，可直接参考 |
| **pro** 生产管理 | `com.ktg.mes.pro` | 20个实体 | ⭐⭐⭐⭐⭐ | 工单/工艺路线/工序/流转卡/报工/转移/安灯，核心参考源 |
| **wm** 仓储管理 | `com.ktg.mes.wm` | ~60个实体 | ⭐⭐⭐⭐⭐ | 出入库/调拨/盘点/条码/批次/SN/委外/杂项收发，表设计极完善 |
| **qc** 质量管理 | `com.ktg.mes.qc` | 16个实体 | ⭐⭐⭐⭐ | IQC/IPQC/OQC/RQC/检验模板/缺陷代码，检验流程完整 |
| **dv** 设备管理 | `com.ktg.mes.dv` | 12个实体 | ⭐⭐⭐⭐ | 设备台账/类型/点检计划+记录/保养记录/维修记录 |
| **cal** 排班日历 | `com.ktg.mes.cal` | 8个实体 | ⭐⭐⭐ | 班组/班次/排班/假期，nexus-erp的sch已有此功能，可跳过 |
| **tm** 工具管理 | `com.ktg.mes.tm` | 2个实体 | ⭐⭐ | 工具类型/工具台账，基础功能 |
| **report** 报表 | `com.ktg.mes.report` | 2个实体 | ⭐⭐⭐ | UReport2集成/图表角色，可用nexus-erp现有方案替代 |

### 2.4 终端覆盖

| 终端 | 技术栈 | 用途 | nexus-erp是否需要 |
|------|--------|------|-----------------|
| **PC Web** | Vue 2 + Element UI + ECharts + dhtmlx-gantt | 管理后台: 主数据维护、报表分析、甘特排程 | ✅ 需要 (用Vue 3重写) |
| **Android手机** | uni-app + uView UI | 移动端: 工单查看、报工录入、质检、安灯响应 | ✅ 后期需要 |
| **平板/触控屏** | uni-app + uView UI | 工位终端: 流转卡操作、数据采集、安灯触发 | ✅ 后期需要 |

### 2.5 关键设计亮点

以下是ktg-mes中最值得借鉴的核心设计模式:

#### 流转卡 (ProCard) 模式 — MES最核心设计

```
prd_work_order (ERP工单)
    ↓ 下达
mes_pro_card (流转卡)
    ├── card_no: 流转卡号(唯一)
    ├── wo_id: 关联工单
    ├── route_id: 关联工艺路线
    ├── quantity: 生产数量
    └── status: 状态(新建→生产中→完成→关闭)
        ↓ 按工艺路线展开
mes_pro_card_process (流转卡工序)
    ├── card_id: 关联流转卡
    ├── process_id: 工序ID
    ├── seq_no: 工序顺序
    ├── workstation_id: 执行工位
    ├── operator_id: 操作员
    ├── equipment_id: 使用设备
    ├── start_time / end_time: 实际开始/结束时间
    ├── qty_processed / qty_good / qty_scrap: 处理量/良品/报废
    └── status: 工序状态
        ↓ 序列号级追溯(可选)
mes_pro_sn_process (序列号工序记录)
    ├── serial_number: 产品序列号
    ├── card_process_id: 关联流转卡工序
    └── process_id / operator_id / timestamp: 操作追溯
```

**设计价值**: 流转卡是MES中连接"计划"与"执行"的核心载体。一张工单可有多张流转卡(分批生产)，每张流转卡按工艺路线展开为多个工序。每个工序记录完整的5W1H(谁/何时/在哪/用什么设备/做了什么/结果如何)。

#### 三级库存单据模式

```
Header (头)         Line (行)             Detail (明细)
─────────           ─────────             ──────────
入库单              入库行1 →             明细1: 批次A, 库位L1, 数量50
  ├── 单号            ├── 物料A, 数量100    明细2: 批次B, 库位L2, 数量50
  ├── 供应商          └── 仓库/库区
  ├── 日期          入库行2 →
  └── 状态            ├── 物料B, 数量200
                      └── 仓库/库区
```

**设计价值**: 一行收货可拆分为多个明细(不同批次/库位/SN)，支持扫码逐件入库。ktg-mes中几乎所有库存单据(入库/出库/调拨/委外/杂项收发)都遵循此模式。

#### 工位资源绑定

```
md_workstation (工位)
    ├── md_workstation_machine  (工位→设备: 1:N)
    ├── md_workstation_worker   (工位→人员资质: 1:N)
    └── md_workstation_tool     (工位→工具/夹具: 1:N)
```

**设计价值**: 工位不只是位置，而是一个"能力单元"。调度时校验"该工位是否有对应设备和资质人员"。

---

## Part 3: MES 模块设计方案

### 3.1 复用策略

**总原则**: 业务模型借鉴 ktg-mes (成熟、经过实际验证)，技术实现遵循 nexus-erp 架构规范。

| 复用层级 | 来源 | 做法 |
|---------|------|------|
| **数据库表结构** | ktg-mes DDL | 参考表名/字段/索引设计 → 增加`tenant_id`字段 → ID改为雪花算法 → 适配MySQL 8字符集 |
| **业务逻辑** | ktg-mes ServiceImpl | 参考业务流程(状态机、校验规则) → 用MyBatis-Plus LambdaQueryWrapper重写 → 遵循nexus-erp分层 |
| **API端点设计** | ktg-mes Controller | 参考URL命名和参数结构 → 遵循nexus-erp REST风格 → 使用Sa-Token权限注解 |
| **PC前端页面** | ktg-mes-ui | 参考UI布局和交互流程 → Vue 3 + Element Plus + TypeScript完全重写 |
| **移动端/Pad端** | ktg-mes uni-app | 参考页面结构和业务流程 → 新建独立移动端项目(后期) |

### 3.2 模块包结构

```
ruoyi-modules/ruoyi-mes/
  pom.xml                                          -- Maven模块POM
  src/main/java/org/dromara/mes/
    ├── config/                                     -- MES自动配置
    │
    ├── md/                                         -- 主数据 (对标ktg-mes md)
    │   ├── controller/
    │   │   ├── MesItemController.java              -- 物料/产品扩展
    │   │   ├── MesProductBomController.java         -- MES级BOM
    │   │   ├── MesProductSopController.java         -- 作业指导书SOP
    │   │   ├── MesProductSipController.java         -- 检验指导书SIP
    │   │   ├── MesWorkstationController.java        -- 工位管理
    │   │   ├── MesWorkstationMachineController.java -- 工位-设备绑定
    │   │   ├── MesWorkstationToolController.java    -- 工位-工具绑定
    │   │   └── MesWorkstationWorkerController.java  -- 工位-人员资质
    │   ├── domain/
    │   │   ├── MesItem.java
    │   │   ├── MesProductBom.java
    │   │   ├── MesProductSop.java / MesProductSip.java
    │   │   ├── MesWorkstation.java
    │   │   └── MesWorkstationMachine/Tool/Worker.java
    │   ├── domain/bo/   (请求DTO)
    │   ├── domain/vo/   (响应DTO)
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │
    ├── pro/                                        -- 生产执行 (对标ktg-mes pro)
    │   ├── controller/
    │   │   ├── MesWorkOrderController.java          -- MES工单(扩展prd)
    │   │   ├── MesWorkOrderBomController.java       -- 工单实际BOM
    │   │   ├── MesRouteController.java              -- 工艺路线
    │   │   ├── MesRouteProcessController.java       -- 路线工序
    │   │   ├── MesRouteProductController.java       -- 产品路线
    │   │   ├── MesProCardController.java            -- 流转卡 ★核心
    │   │   ├── MesProCardProcessController.java     -- 流转卡工序 ★核心
    │   │   ├── MesProSnProcessController.java       -- SN追溯
    │   │   ├── MesProTaskController.java            -- 派工任务
    │   │   ├── MesProFeedbackController.java        -- 工序报工
    │   │   ├── MesProTransOrderController.java      -- 转移单
    │   │   ├── MesAndonConfigController.java        -- 安灯配置
    │   │   └── MesAndonRecordController.java        -- 安灯事件
    │   ├── domain/
    │   │   ├── MesWorkOrder.java / MesWorkOrderBom.java
    │   │   ├── MesRoute.java / MesRouteProcess.java / MesRouteProduct.java
    │   │   ├── MesProCard.java / MesProCardProcess.java
    │   │   ├── MesProSnProcess.java
    │   │   ├── MesProTask.java / MesProFeedback.java
    │   │   ├── MesProTransOrder.java / MesProTransConsume.java
    │   │   └── MesAndonConfig.java / MesAndonRecord.java
    │   ├── domain/bo/ / domain/vo/
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │       └── MesProCardServiceImpl.java           -- 流转卡核心逻辑
    │
    ├── wm/                                         -- 仓储扩展 (对标ktg-mes wm，补充nexus-erp wm)
    │   ├── controller/
    │   │   ├── MesWmBatchController.java            -- 批次管理
    │   │   ├── MesWmSnController.java               -- 序列号管理
    │   │   ├── MesWmBarcodeController.java          -- 条码管理
    │   │   ├── MesWmBarcodeConfigController.java    -- 条码规则
    │   │   ├── MesWmPackageController.java          -- 包装管理
    │   │   ├── MesWmTransferController.java         -- 调拨管理
    │   │   ├── MesWmStockTakingController.java      -- 盘点管理
    │   │   ├── MesWmMaterialRequestController.java  -- 领料申请
    │   │   ├── MesWmOutsourceIssueController.java   -- 委外发料
    │   │   ├── MesWmOutsourceRecptController.java   -- 委外收货
    │   │   ├── MesWmMiscIssueController.java        -- 杂项出库
    │   │   └── MesWmMiscRecptController.java        -- 杂项入库
    │   ├── domain/  (+bo/vo)
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │
    ├── qc/                                         -- 质量控制 (对标ktg-mes qc)
    │   ├── controller/
    │   │   ├── MesQcIndexController.java            -- 检验指标
    │   │   ├── MesQcTemplateController.java         -- 检验模板
    │   │   ├── MesQcTemplateIndexController.java    -- 模板指标
    │   │   ├── MesQcTemplateProductController.java  -- 产品模板
    │   │   ├── MesQcIqcController.java              -- 来料检验
    │   │   ├── MesQcIpqcController.java             -- 过程检验
    │   │   ├── MesQcOqcController.java              -- 出厂检验
    │   │   ├── MesQcRqcController.java              -- 退货检验
    │   │   ├── MesQcDefectController.java           -- 缺陷代码
    │   │   └── MesQcDefectRecordController.java     -- 缺陷记录
    │   ├── domain/  (+bo/vo)
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │
    ├── eq/                                         -- 设备管理 (对标ktg-mes dv)
    │   ├── controller/
    │   │   ├── MesEquipmentController.java          -- 设备台账
    │   │   ├── MesEquipmentTypeController.java      -- 设备分类
    │   │   ├── MesCheckPlanController.java          -- 点检计划
    │   │   ├── MesCheckRecordController.java        -- 点检记录
    │   │   ├── MesMaintenancePlanController.java    -- 保养计划
    │   │   ├── MesMaintenanceRecordController.java  -- 保养记录
    │   │   └── MesRepairRecordController.java       -- 维修记录
    │   ├── domain/  (+bo/vo)
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │
    ├── dc/                                         -- 数据采集 (nexus-erp独有设计)
    │   ├── controller/
    │   │   ├── MesDcPointController.java            -- 采集点定义
    │   │   ├── MesDcParameterController.java        -- 采集参数
    │   │   └── MesDcRecordController.java           -- 采集记录
    │   ├── domain/  (+bo/vo)
    │   ├── mapper/
    │   ├── service/
    │   └── service/impl/
    │
    └── spc/                                        -- 统计过程控制 (nexus-erp独有设计)
        ├── controller/
        │   ├── MesSpcRuleController.java            -- SPC规则
        │   ├── MesSpcSampleController.java          -- 样本组
        │   └── MesSpcSampleValueController.java     -- 样本值
        ├── domain/  (+bo/vo)
        ├── mapper/
        ├── service/
        └── service/impl/
```

### 3.3 核心数据表设计

#### 3.3.1 主数据 (md)

```sql
-- 工位 (对标 ktg-mes md_workstation)
CREATE TABLE mes_workstation (
    workstation_id      BIGINT NOT NULL COMMENT '工位ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    workstation_code    VARCHAR(64) NOT NULL COMMENT '工位编码',
    workstation_name    VARCHAR(128) NOT NULL COMMENT '工位名称',
    workshop_id         BIGINT COMMENT '所属车间ID(关联bd_workshop)',
    workstation_type    VARCHAR(32) COMMENT '工位类型(加工/装配/检验/包装)',
    status              VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态',
    description         VARCHAR(512) COMMENT '描述',
    create_time         DATETIME,
    update_time         DATETIME,
    del_flag            CHAR(1) DEFAULT '0',
    PRIMARY KEY (workstation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工位表';

-- 工位-设备绑定 (对标 ktg-mes md_workstation_machine)
CREATE TABLE mes_workstation_machine (
    wm_id               BIGINT NOT NULL COMMENT '关联ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    workstation_id      BIGINT NOT NULL COMMENT '工位ID',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    quantity            INT DEFAULT 1 COMMENT '数量',
    PRIMARY KEY (wm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工位设备绑定';

-- 工位-人员资质 (对标 ktg-mes md_workstation_worker)
CREATE TABLE mes_workstation_worker (
    ww_id               BIGINT NOT NULL COMMENT '关联ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    workstation_id      BIGINT NOT NULL COMMENT '工位ID',
    worker_id           BIGINT NOT NULL COMMENT '工人ID(关联sch_worker)',
    skill_level         VARCHAR(16) COMMENT '技能等级',
    PRIMARY KEY (ww_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工位人员资质';

-- 作业指导书 SOP
CREATE TABLE mes_product_sop (
    sop_id              BIGINT NOT NULL COMMENT 'SOP ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    process_id          BIGINT COMMENT '关联工序ID',
    sop_name            VARCHAR(128) NOT NULL COMMENT 'SOP名称',
    sop_version         VARCHAR(16) COMMENT '版本',
    file_url            VARCHAR(512) COMMENT '文件URL(MinIO)',
    content             TEXT COMMENT '内容描述',
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (sop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业指导书';

-- 检验指导书 SIP
CREATE TABLE mes_product_sip (
    sip_id              BIGINT NOT NULL COMMENT 'SIP ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    process_id          BIGINT COMMENT '关联工序ID',
    sip_name            VARCHAR(128) NOT NULL COMMENT 'SIP名称',
    sip_version         VARCHAR(16) COMMENT '版本',
    file_url            VARCHAR(512) COMMENT '文件URL',
    content             TEXT COMMENT '内容描述',
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (sip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验指导书';
```

#### 3.3.2 工艺路线 (pro - 对标 ktg-mes pro_route / pro_route_process)

```sql
-- 工艺路线主表
CREATE TABLE mes_route (
    route_id            BIGINT NOT NULL COMMENT '路线ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    route_code          VARCHAR(64) NOT NULL COMMENT '路线编码',
    route_name          VARCHAR(128) NOT NULL COMMENT '路线名称',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    route_version       VARCHAR(16) NOT NULL COMMENT '版本号',
    status              VARCHAR(16) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/ACTIVE/ARCHIVED)',
    effective_date      DATE COMMENT '生效日期',
    expiry_date         DATE COMMENT '失效日期',
    is_current          CHAR(1) DEFAULT '1' COMMENT '是否当前版本',
    total_process_time  DECIMAL(10,2) COMMENT '总加工时间(分钟)',
    total_setup_time    DECIMAL(10,2) COMMENT '总准备时间(分钟)',
    description         VARCHAR(512) COMMENT '描述',
    PRIMARY KEY (route_id),
    INDEX idx_material_version (material_id, route_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线';

-- 工艺路线工序
CREATE TABLE mes_route_process (
    rp_id               BIGINT NOT NULL COMMENT '路线工序ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    route_id            BIGINT NOT NULL COMMENT '路线ID',
    seq_no              INT NOT NULL COMMENT '工序顺序号',
    process_code        VARCHAR(64) NOT NULL COMMENT '工序编码',
    process_name        VARCHAR(128) NOT NULL COMMENT '工序名称',
    workstation_id      BIGINT COMMENT '默认工位ID',
    process_type        VARCHAR(32) COMMENT '工序类型(加工/检验/包装/外协)',
    setup_time          DECIMAL(10,2) COMMENT '准备时间(分钟)',
    process_time        DECIMAL(10,2) COMMENT '单件加工时间(分钟)',
    queue_time          DECIMAL(10,2) COMMENT '排队等待时间(分钟)',
    wait_time           DECIMAL(10,2) COMMENT '等待时间(分钟)',
    is_backflush        CHAR(1) DEFAULT '0' COMMENT '是否倒冲投料',
    labor_count         INT DEFAULT 1 COMMENT '标准人力需求',
    next_seq_no         INT COMMENT '下一工序(并行时为空)',
    description         VARCHAR(512) COMMENT '工序描述',
    PRIMARY KEY (rp_id),
    INDEX idx_route_seq (route_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线工序';

-- 产品-路线关联
CREATE TABLE mes_route_product (
    rp_id               BIGINT NOT NULL COMMENT '关联ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    route_id            BIGINT NOT NULL COMMENT '路线ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    is_default          CHAR(1) DEFAULT '0' COMMENT '是否默认路线',
    PRIMARY KEY (rp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品路线关联';
```

#### 3.3.3 流转卡 (pro - 对标 ktg-mes pro_card ★核心设计)

```sql
-- 流转卡 (工单 → 流转卡，可分批)
CREATE TABLE mes_pro_card (
    card_id             BIGINT NOT NULL COMMENT '流转卡ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    card_no             VARCHAR(64) NOT NULL COMMENT '流转卡号(唯一)',
    wo_id               BIGINT NOT NULL COMMENT '工单ID(关联prd_work_order)',
    route_id            BIGINT NOT NULL COMMENT '工艺路线ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    quantity            DECIMAL(14,4) NOT NULL COMMENT '生产数量',
    priority            VARCHAR(16) DEFAULT 'NORMAL' COMMENT '优先级',
    planned_start       DATETIME COMMENT '计划开始',
    planned_end         DATETIME COMMENT '计划结束',
    actual_start        DATETIME COMMENT '实际开始',
    actual_end          DATETIME COMMENT '实际结束',
    qty_completed       DECIMAL(14,4) DEFAULT 0 COMMENT '已完成数量',
    qty_scrapped        DECIMAL(14,4) DEFAULT 0 COMMENT '已报废数量',
    status              VARCHAR(16) DEFAULT 'CREATED' COMMENT '状态(CREATED/IN_PROGRESS/COMPLETED/CLOSED/HOLD)',
    batch_id            BIGINT COMMENT '关联批次ID',
    create_by           BIGINT COMMENT '创建人',
    create_time         DATETIME,
    update_time         DATETIME,
    del_flag            CHAR(1) DEFAULT '0',
    PRIMARY KEY (card_id),
    UNIQUE KEY uk_card_no (card_no),
    INDEX idx_wo (wo_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES流转卡';

-- 流转卡工序记录 ★最核心的MES数据表
CREATE TABLE mes_pro_card_process (
    ccp_id              BIGINT NOT NULL COMMENT '记录ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    card_id             BIGINT NOT NULL COMMENT '流转卡ID',
    rp_id               BIGINT NOT NULL COMMENT '路线工序ID',
    seq_no              INT NOT NULL COMMENT '工序顺序号',
    process_code        VARCHAR(64) COMMENT '工序编码(冗余)',
    process_name        VARCHAR(128) COMMENT '工序名称(冗余)',
    workstation_id      BIGINT COMMENT '实际工位ID',
    equipment_id        BIGINT COMMENT '实际设备ID',
    operator_id         BIGINT COMMENT '操作员工ID',
    start_time          DATETIME COMMENT '开始时间',
    end_time            DATETIME COMMENT '结束时间',
    qty_processed       DECIMAL(14,4) DEFAULT 0 COMMENT '加工数量',
    qty_good            DECIMAL(14,4) DEFAULT 0 COMMENT '合格数量',
    qty_scrap           DECIMAL(14,4) DEFAULT 0 COMMENT '报废数量',
    labor_hours         DECIMAL(10,2) COMMENT '人工工时',
    machine_hours       DECIMAL(10,2) COMMENT '机器工时',
    status              VARCHAR(16) DEFAULT 'WAITING' COMMENT '状态(WAITING/READY/RUNNING/COMPLETED/SKIPPED)',
    remark              VARCHAR(512) COMMENT '备注',
    PRIMARY KEY (ccp_id),
    INDEX idx_card (card_id),
    INDEX idx_wo_status (card_id, status),
    INDEX idx_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流转卡工序记录(MES核心表)';

-- 序列号工序追溯
CREATE TABLE mes_pro_sn_process (
    snp_id              BIGINT NOT NULL COMMENT '记录ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    serial_number       VARCHAR(128) NOT NULL COMMENT '序列号',
    ccp_id              BIGINT NOT NULL COMMENT '流转卡工序ID',
    rp_id               BIGINT NOT NULL COMMENT '路线工序ID',
    operator_id         BIGINT COMMENT '操作员ID',
    equipment_id        BIGINT COMMENT '设备ID',
    process_result      VARCHAR(16) COMMENT '工序结果(PASS/FAIL)',
    process_time        DATETIME COMMENT '加工时间',
    PRIMARY KEY (snp_id),
    INDEX idx_sn (serial_number),
    INDEX idx_ccp (ccp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号工序追溯';

-- 工序转移单
CREATE TABLE mes_pro_trans_order (
    trans_id            BIGINT NOT NULL COMMENT '转移单ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    trans_no            VARCHAR(64) NOT NULL COMMENT '转移单号',
    card_id             BIGINT NOT NULL COMMENT '流转卡ID',
    from_ccp_id         BIGINT COMMENT '来源工序记录ID',
    to_ccp_id           BIGINT COMMENT '目标工序记录ID',
    from_workstation_id BIGINT COMMENT '来源工位',
    to_workstation_id   BIGINT COMMENT '目标工位',
    quantity            DECIMAL(14,4) NOT NULL COMMENT '转移数量',
    from_operator_id    BIGINT COMMENT '交出人',
    to_operator_id      BIGINT COMMENT '接收人',
    trans_time          DATETIME COMMENT '转移时间',
    status              VARCHAR(16) DEFAULT 'IN_TRANSIT' COMMENT '状态',
    PRIMARY KEY (trans_id),
    UNIQUE KEY uk_trans_no (trans_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序转移单';

-- 派工任务
CREATE TABLE mes_pro_task (
    task_id             BIGINT NOT NULL COMMENT '任务ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    wo_id               BIGINT NOT NULL COMMENT '工单ID',
    rp_id               BIGINT COMMENT '路线工序ID',
    workstation_id      BIGINT NOT NULL COMMENT '工位ID',
    team_id             BIGINT COMMENT '班组ID',
    assignee_id         BIGINT COMMENT '指派人',
    planned_start       DATETIME COMMENT '计划开始',
    planned_end         DATETIME COMMENT '计划结束',
    priority            VARCHAR(16) DEFAULT 'NORMAL' COMMENT '优先级',
    status              VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态',
    PRIMARY KEY (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工任务';

-- 工序报工
CREATE TABLE mes_pro_feedback (
    feedback_id         BIGINT NOT NULL COMMENT '报工ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    wo_id               BIGINT NOT NULL COMMENT '工单ID',
    card_id             BIGINT COMMENT '流转卡ID',
    ccp_id              BIGINT NOT NULL COMMENT '流转卡工序ID',
    operator_id         BIGINT NOT NULL COMMENT '报工人',
    workstation_id      BIGINT COMMENT '工位',
    feedback_time       DATETIME NOT NULL COMMENT '报工时间',
    qty_good            DECIMAL(14,4) NOT NULL COMMENT '合格数',
    qty_scrap           DECIMAL(14,4) DEFAULT 0 COMMENT '报废数',
    scrap_reason        VARCHAR(256) COMMENT '报废原因',
    labor_hours         DECIMAL(10,2) COMMENT '工时',
    machine_hours       DECIMAL(10,2) COMMENT '机时',
    PRIMARY KEY (feedback_id),
    INDEX idx_wo (wo_id),
    INDEX idx_ccp (ccp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序报工';
```

#### 3.3.4 质量控制 (qc - 对标 ktg-mes qc_iqc/ipqc/oqc)

```sql
-- 检验指标
CREATE TABLE mes_qc_index (
    index_id            BIGINT NOT NULL COMMENT '指标ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    index_code          VARCHAR(64) NOT NULL COMMENT '指标编码',
    index_name          VARCHAR(128) NOT NULL COMMENT '指标名称',
    index_type          VARCHAR(16) NOT NULL COMMENT '指标类型(定量/定性)',
    uom_id              BIGINT COMMENT '计量单位ID(定量时)',
    standard_value      VARCHAR(64) COMMENT '标准值',
    PRIMARY KEY (index_id),
    UNIQUE KEY uk_index_code (index_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验指标';

-- 检验模板
CREATE TABLE mes_qc_template (
    template_id         BIGINT NOT NULL COMMENT '模板ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    template_name       VARCHAR(128) NOT NULL COMMENT '模板名称',
    qc_type             VARCHAR(16) NOT NULL COMMENT '检验类型(IQC/IPQC/OQC/RQC)',
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验模板';

-- 模板-指标关联
CREATE TABLE mes_qc_template_index (
    ti_id               BIGINT NOT NULL COMMENT '关联ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    template_id         BIGINT NOT NULL COMMENT '模板ID',
    index_id            BIGINT NOT NULL COMMENT '指标ID',
    spec_min            DECIMAL(14,4) COMMENT '规格下限',
    spec_max            DECIMAL(14,4) COMMENT '规格上限',
    spec_standard       DECIMAL(14,4) COMMENT '标准值',
    seq_no              INT COMMENT '检验顺序',
    is_required         CHAR(1) DEFAULT '1' COMMENT '是否必检',
    PRIMARY KEY (ti_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验模板指标';

-- 来料检验 IQC
CREATE TABLE mes_qc_iqc (
    iqc_id              BIGINT NOT NULL COMMENT 'IQC ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    iqc_no              VARCHAR(64) NOT NULL COMMENT '检验单号',
    receive_id          BIGINT COMMENT '收货单ID(关联wm_material_receive)',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    batch_id            BIGINT COMMENT '批次ID',
    quantity            DECIMAL(14,4) COMMENT '检验数量',
    sample_qty          DECIMAL(14,4) COMMENT '抽样数量',
    defect_qty          DECIMAL(14,4) DEFAULT 0 COMMENT '不合格数',
    result              VARCHAR(16) COMMENT '检验结果(PASS/FAIL/CONCESSION)',
    inspector_id        BIGINT COMMENT '检验人ID',
    inspection_time     DATETIME COMMENT '检验时间',
    status              VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态',
    remark              VARCHAR(512) COMMENT '备注',
    PRIMARY KEY (iqc_id),
    UNIQUE KEY uk_iqc_no (iqc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来料检验IQC';

-- 过程检验 IPQC
CREATE TABLE mes_qc_ipqc (
    ipqc_id             BIGINT NOT NULL COMMENT 'IPQC ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    ipqc_no             VARCHAR(64) NOT NULL COMMENT '检验单号',
    wo_id               BIGINT COMMENT '工单ID',
    card_id             BIGINT COMMENT '流转卡ID',
    ccp_id              BIGINT NOT NULL COMMENT '流转卡工序ID',
    result              VARCHAR(16) COMMENT '检验结果',
    inspector_id        BIGINT COMMENT '检验人ID',
    inspection_time     DATETIME COMMENT '检验时间',
    status              VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (ipqc_id),
    UNIQUE KEY uk_ipqc_no (ipqc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='过程检验IPQC';

-- 出厂检验 OQC
CREATE TABLE mes_qc_oqc (
    oqc_id              BIGINT NOT NULL COMMENT 'OQC ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    oqc_no              VARCHAR(64) NOT NULL COMMENT '检验单号',
    sales_issue_id      BIGINT COMMENT '出库单ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    batch_id            BIGINT COMMENT '批次ID',
    quantity            DECIMAL(14,4) COMMENT '检验数量',
    result              VARCHAR(16) COMMENT '检验结果',
    inspector_id        BIGINT COMMENT '检验人ID',
    inspection_time     DATETIME,
    status              VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (oqc_id),
    UNIQUE KEY uk_oqc_no (oqc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出厂检验OQC';

-- 缺陷代码
CREATE TABLE mes_qc_defect (
    defect_id           BIGINT NOT NULL COMMENT '缺陷ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    defect_code         VARCHAR(64) NOT NULL COMMENT '缺陷编码',
    defect_name         VARCHAR(128) NOT NULL COMMENT '缺陷名称',
    severity            VARCHAR(16) NOT NULL COMMENT '严重度(CRITICAL/MAJOR/MINOR)',
    defect_category     VARCHAR(32) COMMENT '缺陷类别(外观/尺寸/功能/包装/其他)',
    description         VARCHAR(512) COMMENT '描述',
    PRIMARY KEY (defect_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺陷代码';

-- 缺陷记录
CREATE TABLE mes_qc_defect_record (
    record_id           BIGINT NOT NULL COMMENT '记录ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    qc_type             VARCHAR(16) NOT NULL COMMENT '检验类型(IQC/IPQC/OQC/RQC)',
    qc_source_id        BIGINT NOT NULL COMMENT '检验单ID',
    defect_id           BIGINT NOT NULL COMMENT '缺陷ID',
    quantity            DECIMAL(14,4) NOT NULL COMMENT '缺陷数量',
    disposition         VARCHAR(16) COMMENT '处置(SCRAP/REWORK/USE_AS_IS/RETURN)',
    root_cause          VARCHAR(256) COMMENT '根因分析',
    corrective_action   VARCHAR(512) COMMENT '纠正措施',
    discoverer_id       BIGINT COMMENT '发现人',
    discover_time       DATETIME,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺陷记录';
```

#### 3.3.5 设备管理 (eq - 对标 ktg-mes dv)

```sql
-- 设备台账
CREATE TABLE mes_equipment (
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    equipment_code      VARCHAR(64) NOT NULL COMMENT '设备编码',
    equipment_name      VARCHAR(128) NOT NULL COMMENT '设备名称',
    type_id             BIGINT NOT NULL COMMENT '设备类型ID',
    workstation_id      BIGINT COMMENT '所属工位ID',
    model               VARCHAR(64) COMMENT '型号',
    manufacturer        VARCHAR(128) COMMENT '制造商',
    serial_no           VARCHAR(128) COMMENT '出厂编号',
    install_date        DATE COMMENT '安装日期',
    warranty_expire     DATE COMMENT '质保到期',
    status              VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/IDLE/MAINTENANCE/SCRAPPED)',
    running_status      VARCHAR(16) COMMENT '运行状态(RUNNING/STOPPED/BREAKDOWN)',
    image_url           VARCHAR(512) COMMENT '图片URL',
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uk_equipment_code (equipment_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';

-- 设备类型
CREATE TABLE mes_equipment_type (
    type_id             BIGINT NOT NULL COMMENT '类型ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    type_code           VARCHAR(64) NOT NULL COMMENT '类型编码',
    type_name           VARCHAR(128) NOT NULL COMMENT '类型名称',
    maintenance_cycle   INT COMMENT '保养周期(天)',
    calibration_cycle   INT COMMENT '校准周期(天)',
    PRIMARY KEY (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型';

-- 点检计划
CREATE TABLE mes_check_plan (
    plan_id             BIGINT NOT NULL COMMENT '计划ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    plan_name           VARCHAR(128) NOT NULL COMMENT '计划名称',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    check_frequency     VARCHAR(16) COMMENT '频次(DAILY/WEEKLY/MONTHLY/CUSTOM)',
    frequency_value     INT COMMENT '频次值',
    next_check_date     DATE COMMENT '下次点检日期',
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检计划';

-- 点检记录
CREATE TABLE mes_check_record (
    record_id           BIGINT NOT NULL COMMENT '记录ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    plan_id             BIGINT COMMENT '点检计划ID',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    check_date          DATE NOT NULL COMMENT '点检日期',
    check_result        VARCHAR(16) COMMENT '结果(PASS/FAIL)',
    operator_id         BIGINT COMMENT '点检人',
    remark              VARCHAR(512) COMMENT '备注',
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检记录';

-- 保养计划
CREATE TABLE mes_maintenance_plan (
    plan_id             BIGINT NOT NULL COMMENT '计划ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    plan_type           VARCHAR(16) COMMENT '类型(PREVENTIVE/PREDICTIVE/CALIBRATION)',
    cycle_type          VARCHAR(16) COMMENT '周期类型(TIME_BASED/USAGE_BASED)',
    cycle_value         INT COMMENT '周期值(天/小时)',
    last_execution_date DATE COMMENT '上次执行日期',
    next_execution_date DATE COMMENT '下次执行日期',
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养计划';

-- 保养记录
CREATE TABLE mes_maintenance_record (
    record_id           BIGINT NOT NULL COMMENT '记录ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    plan_id             BIGINT COMMENT '计划ID',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    execution_date      DATE NOT NULL COMMENT '执行日期',
    description         VARCHAR(512) COMMENT '保养内容',
    operator_id         BIGINT COMMENT '执行人',
    hours_spent         DECIMAL(10,2) COMMENT '耗时(小时)',
    cost                DECIMAL(14,2) COMMENT '费用',
    remark              VARCHAR(512) COMMENT '备注',
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养记录';

-- 维修记录
CREATE TABLE mes_repair_record (
    repair_id           BIGINT NOT NULL COMMENT '维修ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    equipment_id        BIGINT NOT NULL COMMENT '设备ID',
    breakdown_time      DATETIME NOT NULL COMMENT '故障时间',
    repair_time         DATETIME COMMENT '修复时间',
    downtime_hours      DECIMAL(10,2) COMMENT '停机时长(小时)',
    fault_description   VARCHAR(512) NOT NULL COMMENT '故障描述',
    fault_category      VARCHAR(32) COMMENT '故障类别(MECHANICAL/ELECTRICAL/SOFTWARE/WEAR/OTHER)',
    repair_description  VARCHAR(512) COMMENT '维修描述',
    repair_cost         DECIMAL(14,2) COMMENT '维修费用',
    repair_by           VARCHAR(64) COMMENT '维修方(INTERNAL/EXTERNAL)',
    repair_company      VARCHAR(128) COMMENT '外部维修单位',
    status              VARCHAR(16) DEFAULT 'REPORTED' COMMENT '状态(REPORTED/IN_PROGRESS/COMPLETED)',
    PRIMARY KEY (repair_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修记录';
```

#### 3.3.6 仓储扩展 (wm)

```sql
-- 批次管理
CREATE TABLE mes_wm_batch (
    batch_id            BIGINT NOT NULL COMMENT '批次ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    batch_code          VARCHAR(128) NOT NULL COMMENT '批次号(唯一)',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    production_date     DATE COMMENT '生产日期',
    expiry_date         DATE COMMENT '效期',
    supplier_batch      VARCHAR(128) COMMENT '供应商批次号',
    status              VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态',
    create_wo_id        BIGINT COMMENT '来源工单ID',
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_batch_code (batch_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次管理';

-- 序列号管理
CREATE TABLE mes_wm_sn (
    sn_id               BIGINT NOT NULL COMMENT 'SN ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    serial_number       VARCHAR(128) NOT NULL COMMENT '序列号(唯一)',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    batch_id            BIGINT COMMENT '关联批次ID',
    wo_id               BIGINT COMMENT '生产工单ID',
    status              VARCHAR(16) COMMENT '状态(IN_STOCK/IN_TRANSIT/IN_USE/SOLD/SCRAPPED)',
    current_location    VARCHAR(256) COMMENT '当前位置',
    PRIMARY KEY (sn_id),
    UNIQUE KEY uk_sn (serial_number, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号管理';

-- 库存调拨单
CREATE TABLE mes_wm_transfer (
    transfer_id         BIGINT NOT NULL COMMENT '调拨单ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    transfer_no         VARCHAR(64) NOT NULL COMMENT '调拨单号',
    from_warehouse_id   BIGINT NOT NULL COMMENT '来源仓库ID',
    to_warehouse_id     BIGINT NOT NULL COMMENT '目标仓库ID',
    transfer_type       VARCHAR(16) COMMENT '类型(WH_TO_WH/AREA_TO_AREA/LOC_TO_LOC)',
    status              VARCHAR(16) DEFAULT 'CREATED' COMMENT '状态',
    create_time         DATETIME,
    execute_time        DATETIME COMMENT '执行时间',
    operator_id         BIGINT COMMENT '执行人',
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_transfer_no (transfer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存调拨单';

-- 盘点计划
CREATE TABLE mes_wm_stock_taking_plan (
    plan_id             BIGINT NOT NULL COMMENT '计划ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    plan_name           VARCHAR(128) NOT NULL COMMENT '计划名称',
    warehouse_id        BIGINT COMMENT '仓库ID(NULL=全仓)',
    plan_date           DATE COMMENT '计划盘点日期',
    status              VARCHAR(16) DEFAULT 'CREATED' COMMENT '状态',
    PRIMARY KEY (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点计划';

-- 盘点单
CREATE TABLE mes_wm_stock_taking (
    taking_id           BIGINT NOT NULL COMMENT '盘点单ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    taking_no           VARCHAR(64) NOT NULL COMMENT '盘点单号',
    plan_id             BIGINT COMMENT '盘点计划ID',
    warehouse_id        BIGINT NOT NULL COMMENT '仓库ID',
    status              VARCHAR(16) DEFAULT 'IN_PROGRESS' COMMENT '状态',
    taker_id            BIGINT COMMENT '盘点人',
    taking_date         DATE COMMENT '盘点日期',
    PRIMARY KEY (taking_id),
    UNIQUE KEY uk_taking_no (taking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单';

-- 盘点结果(差异)
CREATE TABLE mes_wm_stock_taking_result (
    result_id           BIGINT NOT NULL COMMENT '结果ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    taking_id           BIGINT NOT NULL COMMENT '盘点单ID',
    material_id         BIGINT NOT NULL COMMENT '物料ID',
    location_id         BIGINT COMMENT '库位ID',
    batch_id            BIGINT COMMENT '批次ID',
    book_qty            DECIMAL(14,4) COMMENT '账面数量',
    actual_qty          DECIMAL(14,4) COMMENT '实盘数量',
    diff_qty            DECIMAL(14,4) COMMENT '差异数量',
    diff_reason         VARCHAR(256) COMMENT '差异原因',
    PRIMARY KEY (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点差异';

-- 领料申请
CREATE TABLE mes_wm_material_request (
    mr_id               BIGINT NOT NULL COMMENT '申请ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    mr_no               VARCHAR(64) NOT NULL COMMENT '申请单号',
    wo_id               BIGINT NOT NULL COMMENT '工单ID',
    card_id             BIGINT COMMENT '流转卡ID',
    ccp_id              BIGINT COMMENT '流转卡工序ID',
    workstation_id      BIGINT COMMENT '申请工位ID',
    requested_qty       DECIMAL(14,4) COMMENT '申请数量',
    issued_qty          DECIMAL(14,4) DEFAULT 0 COMMENT '已发数量',
    status              VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态',
    request_by          BIGINT COMMENT '申请人',
    PRIMARY KEY (mr_id),
    UNIQUE KEY uk_mr_no (mr_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领料申请';
```

#### 3.3.7 数据采集与SPC (dc/spc)

```sql
-- 数据采集点
CREATE TABLE mes_dc_point (
    point_id            BIGINT NOT NULL COMMENT '采集点ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    point_code          VARCHAR(64) NOT NULL COMMENT '采集点编码',
    point_name          VARCHAR(128) NOT NULL COMMENT '采集点名称',
    rp_id               BIGINT COMMENT '关联路线工序ID',
    equipment_id        BIGINT COMMENT '关联设备ID',
    data_type           VARCHAR(16) NOT NULL COMMENT '数据类型(NUMERIC/TEXT/BOOLEAN/ENUM)',
    collection_method   VARCHAR(16) DEFAULT 'MANUAL' COMMENT '采集方式(MANUAL/AUTO)',
    uom_id              BIGINT COMMENT '单位ID(定量时)',
    is_spc_point        CHAR(1) DEFAULT '0' COMMENT '是否SPC采集点',
    PRIMARY KEY (point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据采集点';

-- SPC规则
CREATE TABLE mes_spc_rule (
    rule_id             BIGINT NOT NULL COMMENT '规则ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    rule_code           VARCHAR(64) NOT NULL COMMENT '规则编码',
    rule_name           VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_type           VARCHAR(16) NOT NULL COMMENT '图表类型(XBAR_R/XBAR_S/P/NP/U/C)',
    point_id            BIGINT NOT NULL COMMENT '关联采集点ID',
    sample_size         INT NOT NULL COMMENT '每组样本数',
    sample_frequency    VARCHAR(16) COMMENT '抽样频次',
    ucl                 DECIMAL(14,6) COMMENT '上控制限(UCL)',
    lcl                 DECIMAL(14,6) COMMENT '下控制限(LCL)',
    cl                  DECIMAL(14,6) COMMENT '中心线(CL)',
    PRIMARY KEY (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPC规则';

-- SPC样本组
CREATE TABLE mes_spc_sample (
    sample_id           BIGINT NOT NULL COMMENT '样本ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    rule_id             BIGINT NOT NULL COMMENT 'SPC规则ID',
    sample_time         DATETIME NOT NULL COMMENT '采样时间',
    sample_size         INT COMMENT '实际样本数',
    x_bar               DECIMAL(14,6) COMMENT '均值(X-bar)',
    r                   DECIMAL(14,6) COMMENT '极差(R)',
    sigma               DECIMAL(14,6) COMMENT '标准差(s)',
    is_out_of_control   CHAR(1) DEFAULT '0' COMMENT '是否超控',
    out_of_control_rule VARCHAR(64) COMMENT '超控规则',
    PRIMARY KEY (sample_id),
    INDEX idx_rule_time (rule_id, sample_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPC样本组';
```

#### 3.3.8 安灯系统 (pro - 对标 ktg-mes pro_andon)

```sql
-- 安灯类型
CREATE TABLE mes_andon_type (
    type_id             BIGINT NOT NULL COMMENT '类型ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    type_code           VARCHAR(64) NOT NULL COMMENT '类型编码',
    type_name           VARCHAR(128) NOT NULL COMMENT '类型名称(缺料/设备故障/质量问题/其他)',
    priority            VARCHAR(16) DEFAULT 'MEDIUM' COMMENT '优先级(HIGH/MEDIUM/LOW)',
    escalation_timeout  INT COMMENT '升级超时(分钟)',
    color_code          VARCHAR(16) COMMENT '颜色(red/orange/yellow/blue)',
    PRIMARY KEY (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安灯事件类型';

-- 安灯事件
CREATE TABLE mes_andon_record (
    andon_id            BIGINT NOT NULL COMMENT '事件ID',
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    type_id             BIGINT NOT NULL COMMENT '事件类型ID',
    workstation_id      BIGINT COMMENT '工位ID',
    equipment_id        BIGINT COMMENT '设备ID',
    wo_id               BIGINT COMMENT '工单ID',
    card_id             BIGINT COMMENT '流转卡ID',
    ccp_id              BIGINT COMMENT '流转卡工序ID',
    trigger_user_id     BIGINT NOT NULL COMMENT '触发人',
    trigger_time        DATETIME NOT NULL COMMENT '触发时间',
    description         VARCHAR(512) COMMENT '事件描述',
    status              VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/ACKNOWLEDGED/RESOLVED/ESCALATED)',
    acknowledge_user_id BIGINT COMMENT '响应人',
    acknowledge_time    DATETIME COMMENT '响应时间',
    resolve_user_id     BIGINT COMMENT '解决人',
    resolve_time        DATETIME COMMENT '解决时间',
    resolution          VARCHAR(512) COMMENT '解决措施',
    PRIMARY KEY (andon_id),
    INDEX idx_status (status),
    INDEX idx_workstation (workstation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安灯事件记录';
```

### 3.4 关键API端点设计

#### 生产执行核心流程API

```
# === 流转卡 (核心业务流程) ===
GET    /mes/pro/card/list                 -- 流转卡列表(分页)
GET    /mes/pro/card/{cardId}             -- 流转卡详情(含工序进度)
POST   /mes/pro/card                      -- 创建流转卡(工单→流转卡)
POST   /mes/pro/card/{cardId}/release     -- 下达流转卡
POST   /mes/pro/card/{cardId}/hold        -- 暂停流转卡
POST   /mes/pro/card/{cardId}/close       -- 关闭流转卡

# === 流转卡工序 (车间操作核心) ===
POST   /mes/pro/card-process/{ccpId}/start    -- 开始工序
POST   /mes/pro/card-process/{ccpId}/complete -- 完成工序(上报产量/报废/工时)
GET    /mes/pro/card-process/{cardId}/progress -- 流转卡工序进度

# === 工序转移 ===
POST   /mes/pro/trans-order             -- 创建转移单(工序→工序)
POST   /mes/pro/trans-order/{id}/receive -- 确认接收

# === 派工 ===
POST   /mes/pro/task/assign             -- 派工(工单工序→工人)
GET    /mes/pro/task/my-tasks           -- 我的任务列表

# === 报工 ===
POST   /mes/pro/feedback                -- 工序报工

# === 工艺路线 ===
POST   /mes/pro/route/{routeId}/verify   -- 校验路线完整性
POST   /mes/pro/route/{routeId}/copy     -- 复制路线(生成新版本)

# === 安灯 ===
POST   /mes/pro/andon/trigger            -- 触发安灯事件
POST   /mes/pro/andon/{andonId}/acknowledge -- 响应安灯
POST   /mes/pro/andon/{andonId}/resolve  -- 解决安灯
GET    /mes/pro/andon/active             -- 活跃事件列表
GET    /mes/pro/andon/board/{workstationId} -- 工位安灯看板
```

#### 质量检验API

```
POST   /mes/qc/iqc/{iqcId}/execute       -- 执行来料检验
POST   /mes/qc/ipqc/{ipqcId}/execute     -- 执行过程检验
POST   /mes/qc/oqc/{oqcId}/execute       -- 执行出厂检验
POST   /mes/qc/defect/record             -- 记录缺陷
GET    /mes/qc/defect/pareto             -- 缺陷Pareto分析数据
```

#### 设备管理API

```
GET    /mes/eq/equipment/{id}/oee        -- 单设备OEE
GET    /mes/eq/oee/dashboard             -- OEE仪表板数据
POST   /mes/eq/downtime/record           -- 记录停机事件
GET    /mes/eq/repair/mtbf/{equipmentId} -- MTBF计算
```

#### 数据采集与SPC

```
POST   /mes/dc/record/batch              -- 批量采集数据
GET    /mes/spc/chart/{ruleId}           -- SPC控制图数据
POST   /mes/spc/sample/collect           -- 采集SPC样本
GET    /mes/spc/check/{ruleId}           -- 检查最新样本是否超控
```

#### 追溯API

```
GET    /mes/pro/trace/up/{serialNumber}    -- 正向追溯(组件→成品)
GET    /mes/pro/trace/down/{serialNumber}  -- 反向追溯(成品→组件/原料/批次/工序)
GET    /mes/pro/trace/batch/{batchCode}    -- 批次追溯
```

### 3.5 与现有ERP模块的集成映射

| MES模块 | 依赖的ERP表/模块 | 集成方式 |
|---------|-----------------|---------|
| **md** 主数据 | bd_material, bd_workshop, bd_workstation, bd_vendor, bd_client, bd_unit_of_measure, sch_worker | MES主数据扩展ERP主数据(MES特有字段: SOP/SIP/工位资源)，不重复建物料/供应商等基础表 |
| **pro** 生产 | prd_work_order, prd_production_plan, bd_workshop, bd_workstation, sch_team, sch_worker | MES工单引用ERP工单ID，增加工艺路线和工序级管理；流转卡从工单生成 |
| **wm** 仓储 | wm_warehouse, wm_storage_area, wm_storage_location, wm_material_stock, wm_material_receive, wm_sales_issue | MES仓储扩展批次/SN/条码/盘点/委外/领料，与ERP库存基础表通过ID关联 |
| **qc** 质量 | bd_material, wm_material_receive, wm_material_stock | 检验引用物料和收货单；检验结果影响库存状态 |
| **eq** 设备 | bd_workstation | 设备绑定到工位，独立维护保养维修 |
| **dc** 数据采集 | pro(工单/工序), eq(设备), bd_unit_of_measure | 采集点关联工序和设备；单位引用ERP计量单位 |
| **spc** 统计控制 | dc(采集点) | SPC规则关联采集点，样本来自采集记录 |

### 3.6 与ktg-mes的关键差异点

| 设计决策 | ktg-mes 做法 | nexus-erp MES 做法 | 变更理由 |
|---------|-------------|-------------------|---------|
| **ID策略** | 数据库自增INT | 雪花算法ASSIGN_ID (BIGINT) | 遵循nexus-erp规范，支持分布式部署 |
| **多租户** | 无租户概念 | 所有表加tenant_id，实体继承TenantEntity | nexus-erp核心特性，所有业务表必须具备 |
| **ORM实现** | MyBatis XML Mapper + 手写SQL | MyBatis-Plus BaseMapper + LambdaQueryWrapper | 遵循nexus-erp技术栈，减少XML维护 |
| **分页** | PageHelper | MyBatis-Plus Page + IPage | 框架自带，无需额外插件 |
| **认证鉴权** | JWT Filter + @PreAuthorize | Sa-Token + @SaCheckPermission | 遵循nexus-erp安全框架 |
| **API文档** | Swagger 2 @Api注解 | SpringDoc javadoc | 遵循nexus-erp规范，减少注解维护 |
| **排班管理** | cal子模块(自成体系) | 复用nexus-erp的sch子模块 | 避免功能重复，sch已包含班次/班组/工人/考勤 |
| **报表引擎** | UReport2 | 复用nexus-erp现有方案或集成新方案 | 遵循nexus-erp技术栈 |
| **分布式锁** | 无 | @Lock4j注解(Redisson) | 防止并发操作库存/工单状态 |
| **API加密** | 无 | AES+RSA动态加密 | nexus-erp默认开启，移动端需适配 |
| **数据库连接池** | Druid | HikariCP | 遵循nexus-erp配置 |
| **操作日志** | 自定义 | @Log注解(nexus-erp) | 统一审计追踪 |

### 3.7 前端视图规划

#### Phase 1 - 基础 (14个页面)

```
src/views/mes/
├── md/
│   ├── workstation/index.vue        -- 工位列表
│   ├── workstation/form.vue         -- 工位表单(含设备/工具/人员Tab绑定)
│   ├── sop/index.vue                -- SOP管理
│   └── sip/index.vue                -- SIP管理
│
├── pro/
│   ├── route/index.vue              -- 工艺路线列表
│   ├── route/form.vue               -- 路线编辑(左侧路线树+右侧工序详情)
│   ├── procard/index.vue            -- 流转卡列表(卡片视图+状态筛选)
│   ├── procard/detail.vue           -- 流转卡详情(工序进度条+各工序状态)
│   ├── procard/process.vue          -- 工序执行页(Yield Entry+数据采集)
│   ├── task/index.vue               -- 派工任务看板(Kanban视图)
│   └── feedback/index.vue           -- 报工列表
│
└── eq/
    ├── equipment/index.vue          -- 设备台账
    ├── checkplan/index.vue          -- 点检计划
    └── repair/index.vue             -- 维修记录
```

#### Phase 2 - 仓储质量 (12个页面)

```
src/views/mes/
├── wm/
│   ├── batch/index.vue              -- 批次管理
│   ├── sn/index.vue                 -- 序列号管理
│   ├── barcode/index.vue            -- 条码管理
│   ├── transfer/index.vue           -- 调拨单
│   ├── stocktaking/index.vue        -- 盘点管理
│   ├── materialrequest/index.vue    -- 领料申请
│   └── outsource/index.vue          -- 委外收发
│
└── qc/
    ├── iqc/index.vue                -- 来料检验
    ├── ipqc/index.vue               -- 过程检验
    ├── oqc/index.vue                -- 出厂检验
    ├── defect/index.vue             -- 缺陷管理
    └── template/index.vue           -- 检验模板
```

#### Phase 3 - 智能 (7个页面)

```
src/views/mes/
├── dc/
│   └── point/index.vue              -- 数据采集点管理
│
├── spc/
│   ├── rule/index.vue               -- SPC规则配置
│   └── chart/index.vue              -- SPC控制图(Xbar-R/p/u实时图)
│
├── pro/
│   ├── andon/config.vue             -- 安灯类型配置
│   ├── andon/board.vue              -- 安灯看板(全屏/电视墙模式)
│   └── andon/log.vue                -- 安灯事件日志
│
└── pro/
    └── trace/index.vue              -- 追溯查询(输入SN/批次→树图展开)
```

#### 技术规范

- **框架**: Vue 3.5 + Vite 7 + TypeScript + Pinia + Element Plus + UnoCSS
- **表格**: vxe-table (与nexus-erp现有ERP模块一致)
- **图表**: ECharts 5.x (SPC控制图、OEE仪表盘、缺陷Pareto)
- **甘特图**: 评估 dhtmlx-gantt (商业) vs @apache-arrow/gantt (开源) vs 自研
- **权限**: `v-hasPermi` 指令 (复用nexus-erp)
- **字典**: `proxy.useDict()` (复用nexus-erp)
- **大屏**: 安灯看板和OEE仪表板使用全屏+自动刷新(WebSocket/SSE)

### 3.8 实施分期与任务清单

#### Phase 1: 基础 (预计6-8周)

| 序号 | 任务 | 预估人天 | 产出 |
|------|------|---------|------|
| 1.1 | 创建 `ruoyi-modules/ruoyi-mes/` 模块骨架 (pom.xml, AutoConfiguration, 包结构) | 1d | 模块可编译 |
| 1.2 | `md` 主数据: 工位/工位资源/SOP/SIP (8个实体+8个Controller+8个Service) | 5d | md子模块完成 |
| 1.3 | `pro` 工艺路线: Route/RouteProcess/RouteProduct (3个实体+完整CRUD) | 3d | 路线管理可用 |
| 1.4 | `pro` 流转卡: ProCard/ProCardProcess (2个实体+核心生命周期逻辑) | 5d | 流转卡核心流程 |
| 1.5 | `pro` 工序转移: TransOrder+TransConsume (2个实体) | 2d | 转移确认 |
| 1.6 | `pro` 派工+报工: ProTask+ProFeedback (2个实体) | 3d | 派工报工闭环 |
| 1.7 | `eq` 设备管理: Equipment/Type/CheckPlan/CheckRecord/ MaintenancePlan/MaintenanceRecord/RepairRecord (7个实体) | 5d | 设备管理完整 |
| 1.8 | 编写 `script/sql/ry-mes-phase1.sql` DDL | 2d | 数据库建表脚本 |
| 1.9 | Phase 1 前端页面 (14个页面) | 10d | 基础模块可视可用 |
| 1.10 | 联调测试 + Swagger文档 | 2d | — |

#### Phase 2: 仓储+质量 (预计6-8周)

| 序号 | 任务 | 预估人天 | 产出 |
|------|------|---------|------|
| 2.1 | `wm` 批次/SN/条码 (3个实体+BarcodeConfig) | 3d | 追溯基础 |
| 2.2 | `wm` 调拨/盘点 (Transfer+StockTaking系列) | 5d | 调拨盘点流程 |
| 2.3 | `wm` 领料/委外/杂项 (MaterialRequest+Outsource+Misc) | 5d | 扩展库存操作 |
| 2.4 | `qc` 检验指标/模板 (Index+Template系列) | 3d | 检验主数据 |
| 2.5 | `qc` IQC/IPQC/OQC/RQC (8个实体+完整流程) | 5d | 四检闭环 |
| 2.6 | `qc` 缺陷管理 (Defect+DefectRecord) | 2d | 缺陷代码+记录 |
| 2.7 | `wm` 包装管理 (Package+PackageLine) | 2d | 包装单 |
| 2.8 | 编写 `script/sql/ry-mes-phase2.sql` DDL | 2d | — |
| 2.9 | Phase 2 前端页面 (12个页面) | 8d | 质量仓储模块完成 |
| 2.10 | 联调测试 | 2d | — |

#### Phase 3: 智能 (预计4-6周)

| 序号 | 任务 | 预估人天 | 产出 |
|------|------|---------|------|
| 3.1 | `dc` 数据采集 (DcPoint+DcParameter+DcRecord) | 3d | 采集框架 |
| 3.2 | `spc` SPC规则+样本+控制图数据计算 (3个实体+统计算法) | 5d | SPC可用 |
| 3.3 | `pro` 安灯配置+事件记录 (AndonType+AndonRecord) | 3d | 安灯系统 |
| 3.4 | `pro` SN追溯查询 (TraceService递归算法) | 3d | 正反向追溯 |
| 3.5 | `eq` OEE计算 (OeeRecord+Downtime) | 3d | OEE指标 |
| 3.6 | WebSocket/SSE实时推送 (安灯+工序状态) | 3d | 实时通知 |
| 3.7 | 编写 `script/sql/ry-mes-phase3.sql` DDL | 1d | — |
| 3.8 | Phase 3 前端页面 (7个页面+大屏) | 8d | 智能模块完成+大屏 |
| 3.9 | 联调测试 | 2d | — |

#### Phase 4: 终端 (按需)

| 序号 | 任务 | 预估人天 | 产出 |
|------|------|---------|------|
| 4.1 | 移动端App (uni-app) — 报工/质检/安灯响应 | 10d | Android+iOS |
| 4.2 | 触控屏Pad端 — 工位终端/流转卡/数据采集 | 10d | Pad App |
| 4.3 | MES移动端API (Controller的/mobile/*端点) | 5d | 移动端后台 |

---

## Part 4: 技术实施指南

### 4.1 新建MES模块步骤

#### Step 1: 创建模块目录和POM

```bash
mkdir -p ruoyi-modules/ruoyi-mes/src/main/java/org/dromara/mes
mkdir -p ruoyi-modules/ruoyi-mes/src/main/resources
```

参考 `ruoyi-modules/ruoyi-erp/pom.xml` 创建 `ruoyi-modules/ruoyi-mes/pom.xml`，引入依赖:
- `ruoyi-common-core` (TenantEntity, BaseEntity, 异常, 工具)
- `ruoyi-common-mybatis` (BaseMapperPlus, 多租户插件)
- `ruoyi-common-satoken` (Sa-Token)
- `ruoyi-common-redis` (Redisson)
- `ruoyi-common-tenant` (多租户)
- `ruoyi-common-log` (操作日志)
- `ruoyi-common-excel` (Excel导入导出)
- `ruoyi-common-security` (API加密)

在根 `pom.xml` 的 `<modules>` 中添加 `<module>ruoyi-mes</module>`

在 `ruoyi-admin/pom.xml` 中添加对 `ruoyi-mes` 的依赖

#### Step 2: 创建MES自动配置

```java
// org/dromara/mes/config/MesAutoConfiguration.java
@AutoConfiguration
// 不需要@ComponentScan, 使用spring.factories自动配置
public class MesAutoConfiguration {
    // MES模块的Bean配置
}
```

创建 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
org.dromara.mes.config.MesAutoConfiguration
```

#### Step 3: 按子模块创建分层代码

以 `md/Workstation` 为例:

```
md/
  controller/MesWorkstationController.java   -- @RestController @RequestMapping("/mes/md/workstation")
  domain/MesWorkstation.java                  -- extends TenantEntity, @TableName("mes_workstation")
  domain/bo/MesWorkstationBo.java            -- extends BaseEntity, 查询条件
  domain/vo/MesWorkstationVo.java            -- 响应视图
  mapper/MesWorkstationMapper.java            -- extends BaseMapperPlus<MesWorkstation, MesWorkstationVo>
  service/IMesWorkstationService.java         -- extends IService<MesWorkstation>
  service/impl/MesWorkstationServiceImpl.java -- extends ServiceImpl<Mapper, Entity> implements IService
```

#### Step 4: 创建SQL脚本

参考 `script/sql/ry-erp.sql` 的DDL规范，创建 `script/sql/ry-mes.sql`

#### Step 5: 创建前端代码

在 `plus-ui/src/` 下创建对应的API和Views

### 4.2 代码模板参考

#### 实体类模板

```java
package org.dromara.mes.md.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.domain.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_workstation")
public class MesWorkstation extends TenantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long workstationId;

    private String workstationCode;
    private String workstationName;
    private Long workshopId;
    private String workstationType;
    private String status;
    private String description;
}
```

#### Service实现模板

```java
package org.dromara.mes.md.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.mes.md.domain.MesWorkstation;
import org.dromara.mes.md.domain.bo.MesWorkstationBo;
import org.dromara.mes.md.domain.vo.MesWorkstationVo;
import org.dromara.mes.md.mapper.MesWorkstationMapper;
import org.dromara.mes.md.service.IMesWorkstationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MesWorkstationServiceImpl
        extends ServiceImpl<MesWorkstationMapper, MesWorkstation>
        implements IMesWorkstationService {

    @Override
    public Page<MesWorkstationVo> queryPageList(MesWorkstationBo bo) {
        LambdaQueryWrapper<MesWorkstation> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StringUtils.isNotBlank(bo.getWorkshopId()), MesWorkstation::getWorkshopId, bo.getWorkshopId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkstationName()), MesWorkstation::getWorkstationName, bo.getWorkstationName());
        lqw.orderByAsc(MesWorkstation::getWorkstationCode);
        return baseMapper.selectVoPage(bo.build(), lqw);
    }
}
```

#### Controller模板

```java
package org.dromara.mes.md.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.mes.md.domain.bo.MesWorkstationBo;
import org.dromara.mes.md.domain.vo.MesWorkstationVo;
import org.dromara.mes.md.service.IMesWorkstationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mes/md/workstation")
@RequiredArgsConstructor
public class MesWorkstationController extends BaseController {

    private final IMesWorkstationService workstationService;

    @GetMapping("/list")
    public R<Page<MesWorkstationVo>> list(MesWorkstationBo bo) {
        return R.ok(workstationService.queryPageList(bo));
    }

    @GetMapping("/{workstationId}")
    public R<MesWorkstationVo> getInfo(@PathVariable Long workstationId) {
        return R.ok(workstationService.getVoById(workstationId));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@Validated @RequestBody MesWorkstationBo bo) {
        workstationService.saveBo(bo);
        return R.ok();
    }

    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> edit(@Validated @RequestBody MesWorkstationBo bo) {
        workstationService.updateBo(bo);
        return R.ok();
    }

    @DeleteMapping("/{workstationIds}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> remove(@PathVariable Long[] workstationIds) {
        workstationService.deleteByIds(Arrays.asList(workstationIds));
        return R.ok();
    }
}
```

#### 前端API模板 (TypeScript)

```typescript
// plus-ui/src/api/mes/md/workstation/types.ts
export interface WorkstationQuery {
  workshopId?: string | number;
  workstationName?: string;
  params?: any;
  pageNum: number;
  pageSize: number;
}

export interface WorkstationVO {
  workstationId: string | number;
  workstationCode: string;
  workstationName: string;
  workshopId: string | number;
  workstationType: string;
  status: string;
  description: string;
  createTime: string;
  machines?: WorkstationMachineVO[];
  workers?: WorkstationWorkerVO[];
  tools?: WorkstationToolVO[];
}

export interface WorkstationForm {
  workstationId?: string | number;
  workstationCode: string;
  workstationName: string;
  workshopId: string | number;
  workstationType: string;
  status: string;
  description: string;
}
```

### 4.3 关键设计文件索引

| 编号 | 文件路径 | 用途 |
|------|---------|------|
| R1 | `E:\mes\ktg-mes\doc\设计文档\数据库设计\mes-pro.sql` | 生产模块表结构参考(工单/路线/流转卡) |
| R2 | `E:\mes\ktg-mes\doc\设计文档\数据库设计\mes-qc.sql` | 质量模块表结构参考(IQC/IPQC/OQC) |
| R3 | `E:\mes\ktg-mes\doc\设计文档\数据库设计\mes-dv.sql` | 设备模块表结构参考 |
| R4 | `E:\mes\ktg-mes\doc\设计文档\数据库设计\mes-wm.sql` | 仓储扩展表结构参考(批次/SN/条码/盘点) |
| R5 | `E:\mes\ktg-mes\doc\设计文档\数据库设计\mes-md.sql` | 主数据表结构参考(工位/SOP/SIP) |
| R6 | `E:\mes\ktg-mes\com.ktg.mes.pro.service.impl.ProCardServiceImpl` | 流转卡核心业务逻辑 |
| R7 | `E:\mes\ktg-mes\com.ktg.mes.pro.service.impl.ProRouteServiceImpl` | 路线管理业务逻辑 |
| R8 | `E:\mes\ktg-mes\com.ktg.mes.pro.service.impl.ProFeedbackServiceImpl` | 报工业务逻辑 |
| R9 | `E:\mes\ktg-mes\com.ktg.mes.qc.service.impl.QcIqcServiceImpl` | IQC检验流程 |
| R10 | `E:\mes\ktg-mes\com.ktg.mes.qc.service.impl.QcIpqcServiceImpl` | IPQC检验流程 |
| N1 | `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-erp\pom.xml` | 模块POM模板 |
| N2 | `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-erp\...\bd\domain\BdMaterial.java` | 实体类模板(含TenantEntity) |
| N3 | `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-erp\...\bd\mapper\BdMaterialMapper.java` | Mapper模板(BaseMapperPlus) |
| N4 | `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-erp\...\bd\service\impl\BdMaterialServiceImpl.java` | Service实现模板 |
| N5 | `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-erp\...\bd\controller\BdMaterialController.java` | Controller模板 |
| N6 | `E:\Nexus\nexus-erp\script\sql\ry-erp.sql` | DDL规范 |
| N7 | `E:\Nexus\nexus-erp\plus-ui\src\api\erp\bd\material\types.ts` | 前端API类型模板 |
| N8 | `E:\Nexus\nexus-erp\plus-ui\src\views\erp\bd\material\index.vue` | 前端列表页模板 |

### 4.4 验证方案

#### 4.4.1 编译验证

```bash
# 编译MES模块
cd E:/Nexus/nexus-erp
mvn clean package -pl ruoyi-modules/ruoyi-mes -am
```

#### 4.4.2 数据库验证

```bash
# 连接MySQL执行DDL
mysql -u root -p nexus_erp < script/sql/ry-mes.sql

# 验证表结构
mysql -u root -p -e "SHOW TABLES LIKE 'mes_%';" nexus_erp
# 预期: 列出所有mes_开头的表
```

#### 4.4.3 API验证

```
启动后端 → 访问 http://localhost:8080/swagger-ui/index.html
→ 搜索 "mes/" 前缀
→ 验证所有端点可展开并显示参数说明
```

#### 4.4.4 端到端流程验证

1. **主数据准备**: 创建物料 → 创建工位 → 绑定设备/人员
2. **工艺路线**: 创建路线 → 添加工序(3-4道) → 关联产品
3. **工单下达**: ERP中创建工单 → MES中生成流转卡 → 下达
4. **工序执行**: 手机/PC端开始工序1 → 上报产量 → 转移到工序2
5. **质量检验**: 工序间触发IPQC → 记录检验结果 → 处理缺陷
6. **完工入库**: 末工序完成 → 生成产品SN → 触发OQC → 入库
7. **追溯查询**: 输入SN → 查看完整加工路径(设备/人员/时间/参数)

---

> **文档版本**: v1.0
> **生成日期**: 2026-05-22
> **作者**: Claude Opus 4.7
> **参考项目**: nexus-erp (RuoYi-Vue-Plus 5.6.0) + ktg-mes 3.8.2 (苦糖果MES)
