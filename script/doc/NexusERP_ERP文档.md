# NexusERP - ERP模块综合文档

---

## 一、项目概述与技术架构

### 1.1 项目定位

NexusERP 是一套面向制造企业的 ERP 系统，基于 RuoYi-Vue-Plus 5.6.0 框架二次开发，覆盖基础数据、采购、仓储、生产、销售、排班等核心业务模块，并与 MES、IoT 模块协同构成完整的制造信息化解决方案。

### 1.2 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.x |
| ORM | MyBatis-Plus | 3.5.x |
| 权限认证 | Sa-Token | - |
| 工作流引擎 | Flowable | 7.0 |
| 消息队列 | RabbitMQ + Spring AMQP | - |
| 配置加密 | Jasypt | 3.0.5 |
| 前端框架 | Vue | 3.5 |
| UI组件库 | Element Plus | - |
| 前端语言 | TypeScript | - |

### 1.3 多数据源架构

| 数据源 | 注解 | 用途 | 状态 |
|--------|------|------|------|
| ry-vue | `@DS("master")` / 默认 | 系统管理、权限、日志等基础功能 | ✅ 已启用 |
| ry-erp | `@DS("erp")` | ERP业务数据（物料/采购/仓储/生产/销售/排班） | ✅ 已启用 |
| ry-mes | `@DS("mes")` | MES制造执行数据 | 📋 规划中，尚未创建 |
| ry-iot | `@DS("iot")` | IoT设备采集数据 | 📋 规划中，尚未创建 |

> **注意**: 当前代码中仅使用 `@DS("master")` 和 `@DS("erp")` 两个数据源。`@DS("mes")` 和 `@DS("iot")` 为MES/IoT模块规划数据源，待模块创建后启用。

### 1.4 数据库规划

共规划5个数据库（当前已创建2个）：

1. **ry-vue** — 系统管理库（用户/角色/菜单/日志等） ✅ 已创建
2. **ry-erp** — ERP业务库（基础数据/采购/仓储/生产/销售/排班） ✅ 已创建
3. **ry-mes** — MES制造执行库（工序执行/质量/设备/数据采集） 📋 待创建
4. **ry-iot** — IoT设备库（设备接入/传感器数据） 📋 待创建
5. **TDengine / InfluxDB** — 时序数据库（设备实时数据/历史趋势） 📋 待创建

---

## 二、ERP模块完成度分析

### 2.1 模块完成度矩阵

| 模块 | 代码包 | 完成度 | 说明 |
|------|--------|--------|------|
| bd 基础数据 | com.nexus.erp.bd | 95% | 物料/BOM/客户/供应商/工厂/仓库/库位/工序/路由 |
| pu 采购 | com.nexus.erp.pu | 85% | 请购/订单/收货/供应商评估，缺价格对比/合同管理 |
| wm 仓储 | com.nexus.erp.wm | 90% | 收货/上架/拣货/出库/盘点/库存估值/安全库存，缺序列号/批次独立表 |
| prd 生产 | com.nexus.erp.prd | 80% | 工单/委外/返工/倒冲领料/产能日历，缺资源分配前端 |
| so 销售 | com.nexus.erp.so | 75% | 订单/报价/退货/信用检查/价格引擎，缺合同/开票 |
| sch 排班 | com.nexus.erp.sch | 90% | 班次/班组/排班计划 |

### 2.2 前端完成度

| 模块 | API组数 | View页面数 | 完成度 | 说明 |
|------|---------|-----------|--------|------|
| bd 基础数据 | 9 | 10 | 100% | 物料/BOM/客户/供应商等页面完整 |
| pu 采购 | 4 | 8 | 70% | 订单/退货页面已有，供应商评估/价格表前端待开发 |
| wm 仓储 | 20 | 31 | 90% | 核心页面完整，序列号/批次/追溯前端待开发 |
| prd 生产 | 8 | 10 | 60% | 工单/计划/报告/返工页面已有，委外/产能日历/资源分配前端待开发 |
| sch 排班 | 10 | 11 | 90% | 班次/班组/排班计划页面完整 |
| so 销售 | 4 | 7 | 60% | 报价/退货页面已有，价格表管理/订单增强前端待开发 |

> **注意**: Week 6-10计划的前端增强任务（F-026~F-032、R-025委外前端、R-026价格表前端）在当前 `plus-ui/src/views/erp/` 目录中尚未找到对应页面文件，这些前端页面可能尚未实现或在其他分支上。

---

## 三、ERP剩余功能开发计划

### 3.1 任务清单（按优先级P0-P3）

#### P0 已完成

| 编号 | 任务 | 状态 |
|------|------|------|
| R-001 | 序列号规则表 | ✅ |
| R-006 | 供应商校验 | ✅ |

#### P1 已完成/进行中

| 编号 | 任务 | 状态 |
|------|------|------|
| R-003~R-005 | 采购审批流 | ✅ |
| R-007 | 请购来源 | ✅ |
| R-008 | 收货差异 | ✅ |
| R-009 | 自动过账 | ✅ |
| R-011 | 供应商评估 | ✅ |
| R-012 | 绩效沉淀 | ✅ |
| R-013 | 上架策略 | ✅ |
| R-014 | 拣货策略 | ✅ |
| R-015 | 库存估值 | ✅ |
| R-016 | 安全库存预警 | ✅ |
| R-017 | 台账校验 | ✅ |
| R-018 | 委外订单 | ✅ |
| R-019 | 委外发料收货 | ✅ |
| R-020 | 返工订单 | ✅ |
| R-021 | 倒冲领料 | ✅ |
| R-022 | 产能日历 | ✅ |
| R-023 | 领料联动+完工入库 | ✅ |
| R-025 | 委外前端完善 | ✅ |
| R-026 | 销售管理增强(价格引擎+信用检查) | ✅ |

#### P1 待开发

| 任务 | 说明 |
|------|------|
| 采购业务增强 | 价格对比/合同管理 |
| 供应商评估增强 | 趋势分析/报表 |
| 仓储深度 | 序列号管理/批次管理增强/追溯查询 |

#### P2 待开发 — 质量管理（7个任务）

| 编号 | 任务 | 说明 |
|------|------|------|
| QM-001 | 来料检验(IQC) | 采购收货后触发来料检验流程 |
| QM-002 | 过程检验(IPQC) | 生产过程中工序检验 |
| QM-003 | 出货检验(OQC) | 出库前成品检验 |
| QM-004 | 不良品处理 | 不良品隔离/评审/处置 |
| QM-005 | 质量追溯 | 按批次/序列号追溯质量记录 |
| QM-006 | SPC统计过程控制 | 控制图/过程能力分析 |
| QM-007 | 质量报表 | 质量统计/趋势/对比报表 |

#### P2 待开发 — 设备管理（6个任务）

| 编号 | 任务 | 说明 |
|------|------|------|
| EQ-001 | 设备台账 | 设备基本信息/参数/状态管理 |
| EQ-002 | 设备保养计划 | 定期保养计划/保养记录 |
| EQ-003 | 设备维修管理 | 维修工单/维修记录/维修分析 |
| EQ-004 | 设备点检 | 点检标准/点检记录/异常处理 |
| EQ-005 | 设备备件 | 备件库存/领用/采购 |
| EQ-006 | 设备OEE | 设备综合效率计算/分析 |

#### P2 待开发

| 任务 | 说明 |
|------|------|
| 批次+序列号追溯 | 前向+后向全链路追溯 |

#### P3 待开发

| 任务 | 说明 |
|------|------|
| 财务基础 | 应收/应付/总账 |
| 报表与BI | 业务报表/数据分析/可视化 |
| Flowable工作流覆盖 | 9个业务模块审批流集成 |

### 3.2 实施路线图

| 阶段 | 时间范围 | 内容 | 状态 |
|------|---------|------|------|
| Week 1-12 | ERP核心功能开发与代码重构 | P0/P1任务+代码质量+MQ基础设施 | 已完成 |
| Week 11-12 | 追溯与盘点 | 序列号管理/批次管理增强/追溯查询/盘点差异审批流 | 计划中 |

**剩余工作量：约68.5人天**

---

## 四、业务流程分析与优化

### 4.1 七大核心业务流程泳道分析

#### 1. 采购流程

```
请购 → 审批 → 订单 → 收货 → 入库 → 付款
```

- **请购**：采购申请创建，支持从MRP/手工创建
- **审批**：Flowable审批流，支持多级审批
- **订单**：根据请购单生成采购订单，支持合并/拆分
- **收货**：到货通知→收货登记→差异处理
- **入库**：收货确认→上架→库存过账（MQ异步）
- **付款**：发票校验→付款申请→付款执行

#### 2. 生产流程

```
工单 → 领料 → 生产 → 完工 → 入库
```

- **工单**：ERP创建工单，含BOM/工艺路线信息
- **领料**：根据BOM自动生成领料单，支持超领/欠领
- **生产**：MES执行工序，ERP跟踪状态
- **完工**：工单完工→倒冲领料→成品入库（MQ异步）
- **入库**：成品入库过账，更新库存台账

#### 3. 仓储流程

```
收货 → 上架 → 存储 → 拣货 → 出库
```

- **收货**：到货登记→质检（IQC）→收货确认
- **上架**：PutawayStrategyServiceImpl策略推荐库位（支持NEAREST_EMPTY/CLASS_BASED/FIFO_LOCATION）
- **存储**：库存台账管理，支持批次/序列号
- **拣货**：PickingStrategyFactory策略推荐拣货库位（支持FIFO/FEFO）
- **出库**：拣货确认→出库过账

#### 4. 销售流程

```
报价 → 订单 → 确认 → 出库 → 发货 → 收款
```

- **报价**：价格引擎自动取价，支持折扣
- **订单**：报价转订单，信用检查
- **确认**：信用额度冻结，库存预留
- **出库**：拣货→出库过账
- **发货**：物流安排→发货确认
- **收款**：发票开具→收款核销

#### 5. 委外流程

```
委外单 → 发料 → 加工 → 收货 → 入库
```

- **委外单**：创建委外订单，关联BOM/工艺
- **发料**：SubcontractIssueStrategy策略发料
- **加工**：供应商加工
- **收货**：SubcontractReceiveStrategy策略收货
- **入库**：委外成品入库过账

#### 6. 返工流程

```
返工单 → 出库 → 返工 → 入库
```

- **返工单**：创建返工订单，关联原工单
- **出库**：ReworkIssueStrategy策略出库
- **返工**：执行返工作业
- **入库**：ReworkReceiveStrategy策略入库

#### 7. 审批流程

```
Flowable 7.0 工作流集成
```

- 统一审批引擎管理所有业务审批
- 支持多级审批/并行审批/代理审批
- 审批状态回调更新业务单据状态

### 4.2 流程瓶颈（27项）

#### 采购模块

| 编号 | 瓶颈 | 说明 |
|------|------|------|
| PB-01 | 审批链过长 | 采购审批层级过多，影响响应速度 |
| PB-02 | 收货差异处理 | 收货与订单数量差异处理流程不清晰 |
| PB-03 | 供应商评估滞后 | 评估周期过长，无法实时反映供应商表现 |
| PB-04 | 价格比对缺失 | 缺少历史价格对比功能，采购决策缺乏依据 |
| PB-05 | 合同管理缺失 | 采购合同无系统管理，依赖线下流程 |

#### 生产模块

| 编号 | 瓶颈 | 说明 |
|------|------|------|
| PB-06 | BOM变更通知 | BOM变更后未及时通知相关工单 |
| PB-07 | 工单排产冲突 | 多工单排产时资源冲突检测不足 |
| PB-08 | 领料与完工脱节 | 领料与完工之间缺少联动机制 |
| PB-09 | 倒冲领料异常 | 倒冲领料异常处理流程不完善 |
| PB-10 | 产能瓶颈识别 | 缺少产能瓶颈自动识别与预警 |

#### 仓储模块

| 编号 | 瓶颈 | 说明 |
|------|------|------|
| PB-11 | 上架效率 | 上架策略推荐不够智能，人工干预多 |
| PB-12 | 拣货路径 | 拣货路径未优化，拣货效率低 |
| PB-13 | 盘点差异 | 盘点差异处理缺少审批流 |
| PB-14 | 安全库存 | 安全库存预警响应不及时 |
| PB-15 | 批次追溯 | 批次追溯链路不完整 |

#### 销售模块

| 编号 | 瓶颈 | 说明 |
|------|------|------|
| PB-16 | 价格审批 | 价格变更审批流程不灵活 |
| PB-17 | 信用检查 | 信用检查与订单确认耦合度高 |
| PB-18 | 出库调度 | 销售出库与生产入库调度冲突 |
| PB-19 | 退货处理 | 退货流程缺少质量关联 |
| PB-20 | 开票缺失 | 缺少发票开具功能 |

#### 审批流模块

| 编号 | 瓶颈 | 说明 |
|------|------|------|
| PB-21 | 审批撤回 | 审批撤回后业务状态未回退 |
| PB-22 | 并行审批 | 并行审批结果合并处理不完善 |
| PB-23 | 超时处理 | 审批超时缺少自动升级机制 |
| PB-24 | 代理审批 | 代理审批权限管理不清晰 |
| PB-25 | 审批数据隔离 | 多租户审批数据隔离不彻底 |
| PB-26 | 审批通知 | 审批通知渠道单一，缺少多渠道推送 |
| PB-27 | 审批效率 | 审批效率统计缺失，无法量化分析 |

### 4.3 冗余项（7项）

| 编号 | 冗余描述 | 涉及模块 |
|------|---------|---------|
| RD-01 | 采购订单与请购单字段重复 | pu |
| RD-02 | 收货单与到货通知单数据冗余 | pu/wm |
| RD-03 | 库存流水与库存台账数据冗余 | wm |
| RD-04 | 工单与领料单物料信息重复 | prd/wm |
| RD-05 | 供应商信息在多表中冗余 | pu/bd |
| RD-06 | 物料信息在多表中冗余 | bd/pu/wm/prd/so |
| RD-07 | 审批记录在业务表与流程表中冗余 | 全模块 |

### 4.4 数据冗余（3项）

| 编号 | 冗余描述 | 风险等级 |
|------|---------|---------|
| DR-01 | 采购订单行与收货行物料信息不一致 | 高 |
| DR-02 | 库存流水与库存台账数量不一致 | 高 |
| DR-03 | 工单完工数量与入库数量不一致 | 中 |

### 4.5 关键风险（6项）

| 编号 | 风险描述 | 影响 | 修复方案 |
|------|---------|------|---------|
| RISK-01 | 库存并发安全 | 高并发场景下库存数量不一致 | 乐观锁+悲观锁+分布式锁三重保障 |
| RISK-02 | BOM循环引用 | 多级BOM存在循环依赖导致栈溢出 | BOM解析时增加循环检测算法 |
| RISK-03 | 出库无审计流水 | 出库操作缺少完整审计日志 | 增加出库操作审计日志记录 |
| RISK-04 | 事务注解缺失 | 部分Service方法缺少@Transactional | 补充事务注解，确保数据一致性 |
| RISK-05 | 审批撤回回调缺失 | 审批撤回后业务状态未回退 | 增加审批撤回回调处理 |
| RISK-06 | 条码生成器多实例重复 | 分布式环境下条码可能重复 | 使用雪花算法或Redis自增序列 |

### 4.6 四阶段优化计划

#### Phase A — 紧急修复（1-2周，约12人天）

| 任务 | 工时 | 说明 |
|------|------|------|
| RISK-01 库存并发安全 | 3人天 | 乐观锁+悲观锁+分布式锁 |
| RISK-02 BOM循环引用 | 2人天 | 循环检测算法 |
| RISK-03 出库审计流水 | 2人天 | 审计日志记录 |
| RISK-04 事务注解补充 | 2人天 | @Transactional补全 |
| RISK-05 审批撤回回调 | 2人天 | 回调处理机制 |
| RISK-06 条码生成器 | 1人天 | 雪花算法/Redis序列 |

**KPI目标：库存并发0事故，BOM校验100%覆盖**

#### Phase B — 结构优化（3-4周，约18人天）

| 任务 | 工时 | 说明 |
|------|------|------|
| RD-01~RD-07 冗余消除 | 8人天 | 数据模型去冗余 |
| DR-01~DR-03 数据一致性 | 5人天 | 数据校验与修复 |
| PB-01~PB-05 采购流程优化 | 3人天 | 审批链精简/价格比对 |
| PB-06~PB-10 生产流程优化 | 2人天 | BOM变更通知/排产冲突 |

**KPI目标：数据冗余降低50%，流程效率提升30%**

#### Phase C — 流程增强（5-8周，约20人天）

| 任务 | 工时 | 说明 |
|------|------|------|
| PB-21~PB-27 审批流增强 | 8人天 | 撤回/并行/超时/代理/通知 |
| PB-11~PB-15 仓储流程优化 | 5人天 | 上架/拣货/盘点/追溯 |
| PB-16~PB-20 销售流程优化 | 4人天 | 信用/出库/退货/开票 |
| 自动化规则引擎 | 3人天 | 可配置业务规则 |

**KPI目标：审批效率提升50%，自动化率60%**

#### Phase D — 智能化提升（9-12周，约11人天）

| 任务 | 工时 | 说明 |
|------|------|------|
| BI报表平台 | 4人天 | 业务报表/数据分析 |
| 智能预警 | 3人天 | 库存/产能/质量预警 |
| 数据可视化 | 2人天 | 仪表盘/趋势图 |
| 决策支持 | 2人天 | 采购建议/排产建议 |

**KPI目标：报表覆盖率80%，预警准确率90%**

**四阶段总计：约61人天**

### 4.7 KPI目标汇总

| 阶段 | KPI |
|------|-----|
| Phase A | 库存并发0事故，BOM校验100%覆盖 |
| Phase B | 数据冗余降低50%，流程效率提升30% |
| Phase C | 审批效率提升50%，自动化率60% |
| Phase D | 报表覆盖率80%，预警准确率90% |

---

## 五、代码质量与重构记录

### 5.1 Week 9-10 代码重构

#### Q-003 Service拆分（已完成）

**拆分前：**
- `WmMaterialReceiveServiceImpl` — 895行，职责过重

**拆分后：**
- `WmMaterialReceiveCrudServiceImpl` — 104行，纯CRUD操作
- `WmMaterialReceiveExecutorService` — 业务执行逻辑

**pu Mapper依赖消除：**
- 移除 `PuPurchaseOrderMapper` 和 `PuPurchaseOrderLineMapper` 直接注入
- 删除 `writeBackPurchaseOrderReceivedQty` 和 `updatePurchaseOrderStatus` 方法
- 采购回写逻辑由MQ消费者承担

#### Q-004 @AutoMapper注解统一（已验证兼容）

- `MapstructUtils.convert()` 内部已通过Converter使用自动生成Mapper，无需替换
- Bo/Vo侧 `@AutoMapper` 注解已100%覆盖（100个文件）

#### Q-005 LambdaQueryWrapper链式重构（已完成）

- 8个核心Service已改为 `Wrappers.lambdaQuery()` 链式调用
- 消除 `new LambdaQueryWrapper<>()` 反模式

涉及Service：
1. WmMaterialReceiveServiceImpl
2. WmMaterialReceiveLineServiceImpl
3. WmMaterialStockServiceImpl
4. WmArrivalNoticeServiceImpl
5. PuPurchaseOrderServiceImpl
6. PuPurchaseRequisitionServiceImpl
7. PrdWorkOrderServiceImpl
8. SoSalesOrderServiceImpl

#### Q-006 Service基类统一+Jasypt加密（已完成）

**Service基类统一：**
6个简单Service切换为ServiceImpl基类：
1. WmMaterialReceiveLineServiceImpl
2. WmMaterialReceiveDetailServiceImpl
3. WmMaterialStockServiceImpl
4. WmMaterialStockTxServiceImpl
5. WmArrivalNoticeServiceImpl
6. WmArrivalNoticeLineServiceImpl

**Jasypt加密：**
- Jasypt 3.0.5依赖已添加
- 算法：PBEWITHHMACSHA512ANDAES_256

### 5.2 Week 1-10 代码变更统计

| 周次 | 主要任务 | 变更规模 |
|------|---------|---------|
| Week 1 | R-001, R-006, Q-001(47处RuntimeException→ServiceException), Q-002(3文件5处SimpleDateFormat修复) | 质量修复 |
| Week 2 | R-007~R-012 采购闭环 | 8 new files |
| Week 3 | R-013~R-015 仓储策略层 | ~20 new backend + ~9 frontend files |
| Week 4 | R-016~R-020 + R-024/R-025 | ~33 new backend + ~8 frontend files |
| Week 5 | R-021~R-023 + F-021~F-023 | ~8 new backend + ~6 frontend files |
| Week 6 | F-026/F-027/F-029/F-030/F-031/F-032 前端收尾 | 前端完善 |
| Week 7-8 | I-001 MQ基础设施 + I-002 跨库事务补偿 | ~30 new files (ruoyi-common-mq module) |
| Week 9-10 | Q-003~Q-006 + R-025/R-026 | ~22 new + ~18 modified backend files |

---

## 六、MQ基础设施（ERP模块解耦）

### 6.1 现状问题

| 问题 | 说明 |
|------|------|
| 双向依赖循环 | wm↔prd, wm↔pu, 策略类↔prd |
| 事务粒度过大 | 审批通过→入库过账→采购回写(5-6层调用同一事务) |
| 缺乏最终一致性保障 | 跨库操作无补偿机制 |
| 风格不统一 | 各模块异步调用方式不一致 |

### 6.2 技术选型

| 方案 | 结论 | 理由 |
|------|------|------|
| RabbitMQ + Spring AMQP | ✅ 选定 | 轻量级，满足ERP场景，运维成本低 |
| Kafka | ❌ 不选 | 无流处理需求，运维成本高 |
| Spring Cloud Stream | ❌ 不选 | 增加抽象层但只有一个Broker，过度设计 |

### 6.3 MQ模块架构

`ruoyi-common-mq` 模块结构：

```
ruoyi-common-mq/
├── config/
│   ├── RabbitMqAutoConfiguration      # MQ自动配置
│   ├── RabbitMqProperties             # 配置属性
│   └── RabbitMqInitializer            # 基础设施初始化
├── constant/
│   ├── ExchangeConstants              # 交换机常量
│   ├── QueueConstants                 # 队列常量
│   └── RoutingKeyConstants            # 路由键常量
├── domain/
│   ├── BaseMessage                    # 消息基类
│   ├── MessageHeader                  # 消息头
│   └── MessageRecord                  # 消息记录
├── producer/
│   └── ReliableMessageProducer        # 可靠消息生产者
├── consumer/
│   ├── BaseMessageConsumer            # 消费者基类
│   └── DeadLetterMessageConsumer      # 死信消费者
├── retry/
│   └── MessageRetryService            # 消息重试服务
└── compensation/
    ├── MessageRecord                  # 补偿记录实体
    ├── MessageRecordMapper            # 补偿记录Mapper
    └── MessageCompensationTask        # 补偿定时任务
```

### 6.4 交换机与队列拓扑

#### erp.direct.exchange（Direct类型）

| 队列 | 说明 |
|------|------|
| stock.in.queue | 入库消息队列 |
| stock.out.queue | 出库消息队列 |
| purchase.receive.queue | 采购收货消息队列 |
| production.complete.queue | 生产完工消息队列 |
| vendor.evaluation.queue | 供应商评估消息队列 |

#### erp.topic.exchange（Topic类型）

| 队列 | 说明 |
|------|------|
| stock.alert.queue | 库存预警消息队列 |
| notification.queue | 通知消息队列 |

#### erp.delay.exchange（Direct + TTL）

| 队列 | 说明 |
|------|------|
| erp.delay.queue | 延迟消息队列 → DLX |

#### erp.dlx.exchange（死信交换机）

| 队列 | 说明 |
|------|------|
| erp.dlx.queue | 死信消息队列（人工处理/告警） |

### 6.5 核心消息定义

| 消息类型 | Exchange | RoutingKey | 说明 |
|---------|----------|------------|------|
| StockInMessage | erp.direct | stock.in.receive | 采购入库过账 |
| StockInMessage | erp.direct | stock.in.production | 完工入库过账 |
| StockInMessage | erp.direct | stock.in.rework | 返工入库过账 |
| StockOutMessage | erp.direct | stock.out.issue | 生产领料出库 |
| StockOutMessage | erp.direct | stock.out.rework | 返工出库 |
| PurchaseReceiveMessage | erp.direct | purchase.receive.writeback | 收货回写采购订单 |
| ProductionCompleteMessage | erp.direct | production.complete.backflush | 完工倒冲领料 |
| VendorEvaluationMessage | erp.direct | vendor.evaluation.calculate | 供应商评估计算 |
| StockAlertMessage | erp.topic | stock.alert.low | 低库存预警→采购建议 |
| OrderTimeoutMessage | erp.delay | order.timeout.check | 订单超时自动关闭 |

### 6.6 消息基类设计

```java
public abstract class BaseMessage implements Serializable {
    private String messageId;       // UUID，全局唯一
    private Long timestamp;         // 消息时间戳
    private String traceId;         // 链路追踪ID
    private Long tenantId;          // 租户ID
    private String sourceModule;    // 来源模块: pu/prd/wm/so/bd
    private Integer retryCount;     // 已重试次数
    private Integer maxRetry;       // 最大重试次数，默认3
}
```

### 6.7 7层MQ消息可靠性保障

| 层级 | 机制 | 说明 |
|------|------|------|
| 第1层 | Publisher Confirm | `channel.waitForConfirms()` + 本地记录消息状态 |
| 第2层 | 消息持久化 | Exchange durable=true, Queue durable=true, deliveryMode=2 |
| 第3层 | 消费者手动ACK | `@RabbitListener(ackMode=MANUAL)` |
| 第4层 | 幂等消费 | Redis `SETNX mq:consumed:{messageId}` (TTL=24h) |
| 第5层 | 死信队列 | 消费失败3次→DLX→人工处理/告警 |
| 第6层 | 补偿任务 | SnailJob定时扫描message_record表→重新投递超时未确认消息 |
| 第7层 | 对账任务 | 每天凌晨对比mq_message_record与业务表状态 |

### 6.8 业务场景异步化改造

#### 采购收货→库存过账

**改造前：**
```
afterFlowHandle() → executeReceive() → processStockTransfer() → writeBackPurchaseOrder()
(同一事务5-6层调用)
```

**改造后：**
```
afterFlowHandle() → 更新状态APPROVED
  → 发送StockInMessage
    → StockInConsumer处理入库
    → PurchaseReceiveWritebackConsumer处理采购回写
```

#### 工单完工→倒冲领料→成品入库

**改造前：**
```
complete() → 更新状态 → backflushService.backflushOnCompletion() → stockOperationService.postProductionCompletion()
(同一大事务)
```

**改造后：**
```
complete() → 更新状态COMPLETED
  → 发送ProductionCompleteMessage
    → ProductionBackflushConsumer处理倒冲
    → StockInConsumer处理入库
```

#### 委外/返工库存联动

**改造前：**
```
策略类直接注入prd模块Mapper
```

**改造后：**
```
策略类只处理库存操作 → 发送消息通知prd模块更新状态
```

### 6.9 跨库事务补偿框架

| 组件 | 说明 |
|------|------|
| 补偿记录表 | `mq_compensation_record`，记录补偿操作 |
| CompensationService | `registerCompensation` / `executeCompensation` / `markCompensationSuccess` |
| CompensationTask | SnailJob定时扫描PENDING记录，执行补偿 |
| ReconciliationTask | 每天凌晨对账，对比消息记录与业务表状态 |

### 6.10 YAML配置

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: nexus
    password: nexus
    virtual-host: /erp
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
```

---

## 七、Jasypt加密配置

### 7.1 基本信息

| 项目 | 值 |
|------|-----|
| 版本 | Jasypt 3.0.5 |
| 算法 | PBEWITHHMACSHA512ANDAES_256 |
| 密钥来源 | 环境变量 `JASYPT_PASSWORD`，回退值 `nexus-erp-dev` |

### 7.2 加密范围

以下3个密码需加密：

1. 主库root密码
2. ERP库root密码
3. RabbitMQ nexus密码

### 7.3 密文生成方式

**方式一：Maven插件**
```bash
mvn jasypt:encrypt -Djasypt.encryptor.password=xxx -Dinput=明文
```

**方式二：Java代码**
```java
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
encryptor.setPassword("密钥");
String encrypted = encryptor.encrypt("明文");
```

### 7.4 环境配置

| 环境 | 密钥来源 | 说明 |
|------|---------|------|
| dev | 默认密码 `nexus-erp-dev` | 开发环境使用默认值 |
| test | 环境变量 `JASYPT_PASSWORD` | 测试环境必须配置环境变量 |
| prod | 强制环境变量 `JASYPT_PASSWORD` | 生产环境强制使用环境变量 |

### 7.5 配置格式

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:nexus-erp-dev}
    algorithm: PBEWITHHMACSHA512ANDAES_256
```

密码格式：`ENC(密文)`

示例：
```yaml
spring:
  datasource:
    master:
      password: ENC(生成的密文)
    erp:
      password: ENC(生成的密文)
  rabbitmq:
    password: ENC(生成的密文)
```

---

## 八、Week 1-12 详细执行记录

### Week 1: P0清零

| 任务 | 内容 |
|------|------|
| R-001 | 序列号规则表设计（snm_rule / snm_rule_detail） |
| R-006 | 供应商编码唯一性校验 |
| Q-001 | 47处 `RuntimeException` → `ServiceException` 统一异常处理 |
| Q-002 | 3文件5处 `SimpleDateFormat` → `DateTimeFormatter` 线程安全修复 |

### Week 2: 采购闭环

| 任务 | 内容 |
|------|------|
| R-007 | 请购来源（采购申请→请购单） |
| R-008 | 收货差异处理 |
| R-009 | 自动过账（审批通过→自动入库） |
| R-011 | 供应商评估模型 |
| R-012 | 供应商绩效沉淀 |

**新增文件：8个**

### Week 3: 仓储策略层

| 任务 | 内容 |
|------|------|
| R-013 | 上架策略（PutawayStrategyServiceImpl） |
| R-014 | 拣货策略（FifoPickingStrategy / FefoPickingStrategy） |
| R-015 | 库存估值（移动加权平均/标准成本/先进先出） |

**新增文件：~20 backend + ~9 frontend**

### Week 4: 生产深度(上)

| 任务 | 内容 |
|------|------|
| R-016 | 安全库存预警 |
| R-017 | 台账校验 |
| R-018 | 委外订单 |
| R-019 | 委外发料收货（SubcontractIssueStrategy / SubcontractReceiveStrategy） |
| R-020 | 返工订单（ReworkIssueStrategy / ReworkReceiveStrategy） |
| R-024/R-025 | 库存策略模式完善 |

**新增文件：~33 backend + ~8 frontend**

### Week 5: 生产深度(下)

| 任务 | 内容 |
|------|------|
| R-021 | 倒冲领料（BackflushService，完工倒冲自动扣料） |
| R-022 | 产能日历（prd_capacity_calendar表，日历视图） |
| R-023 | 领料与完工入库联动（ProductionCompletionStrategy） |
| F-021/F-022/F-023 | 对应前端页面 |

**新增文件：~8 backend + ~6 frontend**

### Week 6: P1前端收尾

| 任务 | 内容 | 状态 |
|------|------|------|
| F-026 | 采购收货联动前端增强（订单行展开+收货进度条） | ⚠️ 后端就绪，前端页面待验证 |
| F-027 | 供应商评估前端增强（types.ts+雷达图+趋势图） | ⚠️ 后端就绪，前端页面待验证 |
| F-029 | 委外订单前端完善（供应商/物料选择器+关联工单+发料收货按钮） | ⚠️ 后端就绪，前端页面待验证 |
| F-030 | 返工订单前端完善（来源工单选择器+物料自动填充+出库入库按钮） | ⚠️ 后端就绪，前端页面待验证 |
| F-031 | 工单资源分配前端页面 | ⚠️ 后端就绪，前端页面待验证 |
| F-032 | 产能日历可视化增强（日历视图Tab） | ⚠️ 后端就绪，前端页面待验证 |

> **注意**: 上述前端任务的后端API/Service均已实现并验证，但 `plus-ui/src/views/erp/` 目录中对应的前端页面文件尚未确认存在（如 vendor-evaluation/report.vue、subcontract/index.vue、rework/index.vue、capacity-calendar/index.vue、work-order-resource/index.vue 等），需确认是否在其他分支或待后续开发。

### Week 7-8: MQ基础设施搭建

| 任务 | 内容 |
|------|------|
| I-001 | MQ基础设施搭建（RabbitMQ + Spring AMQP） |
| - | 创建ruoyi-common-mq公共模块 |
| - | MQ自动配置与基础设施声明 |
| - | 消息基类与可靠消息生产者 |
| - | 消费者基类与死信处理 |
| - | YAML配置与Docker Compose |
| I-002 | 跨库事务补偿框架 |
| - | 补偿记录表 + CompensationService |
| - | CompensationTask + ReconciliationTask |
| - | 业务场景异步化改造 |
| - | 采购收货→库存过账异步化 |
| - | 工单完工→倒冲领料→成品入库异步化 |
| - | 委外/返工库存联动异步化 |

**新增文件：~30个（ruoyi-common-mq模块）**

### Week 9-10: 代码重构与销售增强

| 任务 | 内容 | 状态 |
|------|------|------|
| Q-003 | WmMaterialReceiveServiceImpl拆分 + pu Mapper清理 | ✅ |
| Q-004 | @AutoMapper注解验证（已兼容，无需修改） | ✅ |
| Q-005 | LambdaQueryWrapper链式重构 | ✅ |
| Q-006 | Service基类统一 + Jasypt加密 | ✅ |
| R-026-Part1 | 销售价格引擎（so_price_list + so_price_list_line + PricingEngine） | ✅ |
| R-026-Part2 | 信用检查联动增强（so_credit_usage + CreditCheckService） | ✅ |
| R-025 | 委外前端完善（审批流+发料收货弹窗+导出+日志） | ✅ |

**变更规模：~22 new + ~18 modified backend files**

### Week 11-12: 追溯与盘点（计划中）

| 任务 | 内容 |
|------|------|
| R-041 | 序列号管理（wm_sn表） |
| R-042 | 批次管理增强（wm_batch独立表） |
| R-043 | 追溯查询服务（前向+后向） |
| R-044 | 追溯前端（树图/表格展开） |
| R-055 | 盘点差异审批流（Flowable集成） |

---

## 九、库存策略模式

### 9.1 策略模式架构

**核心接口与工厂：**
- `StockOperationStrategy` — 库存操作策略接口（21行）
- `BaseStockOperationStrategy` — 策略抽象基类（65行）
- `StockOperationStrategyFactory` — 策略工厂，Spring自动收集所有策略实现（49行）

**全部策略实现（共12个）：**

| 策略类 | 业务场景 | 说明 |
|--------|---------|------|
| ReceiveInStrategy | 采购入库 | 采购收货入库过账 |
| SalesOutStrategy | 销售出库 | 销售订单出库过账 |
| TransferStrategy | 库存调拨 | 库间调拨/库内移库 |
| CountAdjustStrategy | 盘点调整 | 盘点差异调整过账 |
| MiscIssueStrategy | 其他出库 | 非标准业务出库 |
| MiscRecptStrategy | 其他入库 | 非标准业务入库 |
| ProductionCompletionStrategy | 生产完工 | 完工入库+倒冲领料联动 |
| ProductionIssueStrategy | 生产领料 | 工单领料出库 |
| SubcontractIssueStrategy | 委外发料 | 委外订单发料出库 |
| SubcontractReceiveStrategy | 委外收货 | 委外成品收货入库 |
| ReworkIssueStrategy | 返工出库 | 返工物料出库 |
| ReworkReceiveStrategy | 返工入库 | 返工成品入库 |

**上架/拣货策略（独立实现，不走StockOperationStrategy）：**

| 策略类 | 业务场景 | 说明 |
|--------|---------|------|
| PutawayStrategyServiceImpl | 上架推荐 | 支持NEAREST_EMPTY/CLASS_BASED/FIFO_LOCATION三种策略 |
| FifoPickingStrategy | 先进先出拣货 | 按入库时间排序拣货 |
| FefoPickingStrategy | 先到期先拣货 | 按批次有效期排序拣货 |

### 9.2 库存并发控制

| 机制 | 说明 | 适用场景 |
|------|------|---------|
| 乐观锁 | `wm_material_stock.version` 字段，更新时版本号+1 | 低并发更新场景 |
| 悲观锁 | `SELECT ... FOR UPDATE` | 关键库存操作（过账/盘点） |
| Redis分布式锁 | 库存操作前获取锁，防止分布式环境并发 | 多实例部署场景 |

**三重保障协同：**
1. 日常操作使用乐观锁，性能最优
2. 关键过账操作使用悲观锁，确保强一致
3. 分布式环境下使用Redis锁，防止多实例并发

---

## 十、销售管理增强

### 10.1 价格引擎

#### 新增表

**so_price_list（价格表头）：**
- 价格表基本信息，包含价格类型、有效期、客户范围等

**so_price_list_line（价格表行）：**
- 价格表明细行，包含物料、价格、数量区间、折扣率等

#### 价格类型

| 类型 | 编码 | 说明 |
|------|------|------|
| 基价 | BASE | 标准基价，适用于所有客户 |
| 客户专属价 | CUSTOMER | 特定客户的专属价格 |
| 期间促销价 | PROMOTION | 指定时间段内的促销价格 |

#### 四级取价优先级

```
客户专属价(CUSTOMER) > 期间促销价(PROMOTION) > 标准基价(BASE) > 物料参考价(BdMaterial.referencePrice)
```

#### 数量区间匹配

- `min_qty`：最小数量
- `max_qty`：最大数量
- 根据订单数量匹配对应价格区间

#### 折扣率

- `discount_rate`：折扣率（%）
- 最终价格 = 匹配价格 × discount_rate / 100

### 10.2 信用检查

#### 新增表

**so_credit_usage（信用占用记录）：**
- 记录每个订单的信用占用金额

#### 信用管理机制

| 机制 | 说明 |
|------|------|
| 信用额度冻结 | 订单确认时冻结金额，写入so_credit_usage |
| 信用释放 | 订单关闭/取消时释放冻结金额 |
| 信用超限阻断 | 订单金额 + 已占用 > 信用额度时阻止确认 |
| 信用告警 | 使用率 > 80%时弹出警告 |

#### 信用检查流程

```
订单确认 → 计算订单金额 → 查询客户信用额度
  → 计算已占用金额 → 判断是否超限
    → 超限：阻断确认，提示超限
    → 未超限但>80%：弹出警告，用户确认后继续
    → 正常：冻结信用额度，确认订单
```

---

## 十一、ERP与MES边界划分

### 11.1 九条划分原则

| 序号 | 原则 | 说明 |
|------|------|------|
| 1 | 主数据统一ERP | 物料/BOM/客户/供应商/工厂/仓库/库位/工序/路由在ERP维护，MES只读引用 |
| 2 | 质量管理统一MES | IQC/IPQC/OQC/不良品/SPC在MES管理 |
| 3 | 设备管理统一MES | 设备台账/保养/维修/点检/OEE在MES管理 |
| 4 | 库存统一wm_material_stock | ERP和MES共用同一库存表，通过@DS("erp")访问 |
| 5 | 工单创建在ERP，执行在MES | ERP创建工单→MES创建MES工单(含工序/流转卡) |
| 6 | 采购/销售只在ERP | 采购和销售业务不涉及MES |
| 7 | 数据采集只在MES | 设备数据采集/传感器数据在MES处理 |
| 8 | 排班调度在ERP | 班次/班组/排班计划在ERP维护 |
| 9 | 工艺路线在ERP维护，MES执行 | ERP维护工艺路线→MES按工序执行 |

### 11.2 模块归属对照表

| 功能 | ERP模块 | MES模块 | 说明 |
|------|---------|---------|------|
| 物料主数据 | bd | md(只读) | ERP维护 |
| BOM | bd | md(只读) | ERP维护 |
| 工艺路线 | bd | pro(执行) | ERP维护，MES执行 |
| 工单 | prd(创建) | pro(执行) | ERP创建，MES执行 |
| 库存 | wm | wm(扩展) | 共用wm_material_stock |
| 采购 | pu | - | 仅ERP |
| 销售 | so | - | 仅ERP |
| 质量管理 | - | qc | 仅MES |
| 设备管理 | - | eq | 仅MES |
| 数据采集 | - | dc | 仅MES |
| SPC | - | spc | 仅MES |

---

## 十二、技术建议

### 12.1 统一审批引擎

所有审批流通过Flowable统一管理，避免各模块自行实现审批逻辑。

**要点：**
- 定义统一的审批流程模板
- 审批状态回调标准化
- 支持多级审批/并行审批/代理审批

### 12.2 库存策略可配置化

策略选择通过配置而非硬编码，支持运行时切换策略。

**要点：**
- 策略类型通过数据库配置
- 支持按仓库/物料/业务类型配置不同策略
- 策略参数可动态调整

### 12.3 事件驱动解耦

跨模块通信通过MQ而非直接Mapper注入。

**要点：**
- 禁止跨模块直接注入Mapper
- 跨模块操作通过MQ消息异步处理
- 保证最终一致性

### 12.4 异常处理规范化

统一ServiceException，消除RuntimeException。

**要点：**
- 业务异常统一抛出ServiceException
- 异常码规范化管理
- 异常信息国际化

### 12.5 库存并发控制

乐观锁+悲观锁+分布式锁三重保障。

**要点：**
- 乐观锁：wm_material_stock.version字段
- 悲观锁：SELECT ... FOR UPDATE（关键操作）
- 分布式锁：Redis锁（多实例场景）
