# NexusERP 系统架构完善执行计划

> 编制日期: 2026-05-27
> 基于: nexus-erp 代码库深度分析 + 三份规划文档综合
> 总工期: 48周 (约12个月)，按单人全职估算
> 目标状态: 完整的 ERP + MES + IoT 一体化平台

---

## 一、系统架构现状分析

### 1.1 当前架构评估

```
┌─────────────────────────────────────────────────────────────┐
│                    当前系统架构状态                            │
├─────────────────────────────────────────────────────────────┤
│  ✅ 优势                         │  ❌ 不足                  │
│  ├─ 框架成熟 (RuoYi-Vue-Plus)    │  ├─ ERP完成度仅47%       │
│  ├─ 多租户原生支持                │  ├─ 无MES制造执行系统     │
│  ├─ 多数据源隔离 (ry-vue/ry-erp) │  ├─ 无IoT设备物联网      │
│  ├─ Flowable工作流已集成          │  ├─ 质量管理模块缺失      │
│  ├─ Redis/分布式锁就绪           │  ├─ 设备管理模块缺失      │
│  └─ 移动端基础框架已有            │  └─ 代码质量待优化        │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 技术栈现状

| 层级 | 技术栈 | 版本 | 状态 |
|------|--------|------|------|
| **后端框架** | Spring Boot | 3.5.12 | ✅ 最新稳定 |
| **ORM框架** | MyBatis-Plus | 3.5.16 | ✅ 最新稳定 |
| **认证鉴权** | Sa-Token | 1.44.0 | ✅ 已配置 |
| **工作流** | Flowable | 7.0.0 | ✅ 已集成 |
| **缓存** | Redis/Redisson | 3.52.0 | ✅ 已配置 |
| **数据库** | MySQL + HikariCP | 8.x | ✅ 运行中 |
| **前端框架** | Vue 3 + Element Plus | 3.5 | ✅ 现代化 |
| **移动端** | uni-app | - | ⚠️ 基础框架 |
| **消息队列** | - | - | ❌ 未集成 |
| **时序数据库** | - | - | ❌ 未集成 |
| **协议适配** | - | - | ❌ 无 |

### 1.3 ERP模块完成度矩阵

| 模块 | 完成度 | 已有功能 | 关键缺口 | 优先级 |
|------|--------|---------|----------|--------|
| **bd** 基础数据 | 65% | 物料/BOM/客户/供应商/车间 | 工艺路线/ECN/替代BOM | P1 |
| **prd** 生产管理 | 45% | 工单/报工/物料需求 | 委外/返工/产能/倒冲 | P1 |
| **pu** 采购管理 | 30% | 采购申请/订单/退货 | 评估/收货差异/自动过账 | P1 |
| **so** 销售管理 | 25% | 订单/报价/退货 | 价格引擎/信用/发货 | P2 |
| **wm** 仓储管理 | 50% | 仓库/库存/出入库/条码 | 策略/估值/盘点/追溯 | P1 |
| **sch** 排产调度 | 50% | 排班/考勤/移动端 | 高级排产/甘特图 | P2 |
| **qc** 质量管理 | 0% | - | IQC/IPQC/OQC/缺陷 | P1 | **由MES统一实现，ERP不重复建设** |
| **eq** 设备管理 | 0% | - | 台账/点检/保养/维修 | P1 | **由MES统一实现，ERP不重复建设** |
| **fi** 财务管理 | 0% | - | AR/AP/成本核算 | P3 |

**综合评估**: 对标企业级ERP (SAP B1/金蝶K/3)，当前完成度约 **35-40%**

### 1.4 与目标架构差距分析

```
目标架构: ERP + MES + IoT 一体化平台
         ┌─────────────┐
         │   IoT 平台   │ ← 当前: 零基础
         │  (设备/协议)  │
         └──────┬───────┘
                │
         ┌──────┴───────┐
         │   MES 执行    │ ← 当前: 零基础
         │  (流转卡/质量) │
         └──────┬───────┘
                │
         ┌──────┴───────┐
         │   ERP 系统    │ ← 当前: 完成度40%
         │  (计划/采购)  │
         └──────────────┘

差距评估:
- ERP 完善: 需 12周 (~55人天，qc/eq由MES统一实现，节省~17人天)
- MES 建设: 需 22周 (~140人天，含qc/eq完整实现)
- IoT 建设: 需 16周 (~100人天)
- 总差距: 50周 + ~295人天
```

---

## 二、架构完善的总体目标和阶段性里程碑

### 2.1 总体目标

```
Phase 1: ERP 完工 (第1-12周)
  ├── 目标: 完成P0/P1全部剩余任务（qc/eq由MES统一实现，ERP不重复建设）
  ├── 交付: 完整的企业级ERP系统（采购/仓储/生产/销售/排班）
  └── 验收: 覆盖采购/仓储/生产全流程，MQ基建就绪

Phase 2: MES 建设 (第13-34周)
  ├── 目标: 新建 ruoyi-mes 模块，实现车间级生产执行
  ├── 交付: 流转卡引擎 + 质量管理(qc) + 设备管理(eq) + 数据采集
  └── 验收: ERP→MES 工单联动，工序级追溯

Phase 3: IoT 建设 (第33-48周)
  ├── 目标: 新建 ruoyi-iot 模块，实现设备物联网
  ├── 交付: 多协议接入 + 规则引擎 + 边缘计算
  └── 验收: 设备数据自动采集，与MES联动
```

### 2.2 阶段性里程碑

| 里程碑 | 时间点 | 交付物 | 验收标准 | 状态 |
|--------|--------|--------|----------|------|
| **M1: ERP P0清零** | Week 1 | 序列号规则/供应商校验完成 | 编译通过，功能可用 | ✅ 已达成 |
| **M2: 采购闭环** | Week 2 | 采购全流程完整 | 申请→订单→收货→评估 | ✅ 已达成 |
| **M3: 仓储策略层** | Week 3 | 上架/拣货/估值策略 | 策略可配置，库存准确 |
| **M4: 生产深度** | Week 5 | 委外/返工/倒冲/产能 | 生产全流程闭环 |
| **M5: MQ基建完成** | Week 8 | RabbitMQ/Kafka集成+跨库事务框架 | MQ可用，补偿机制就绪 |
| **M6: ERP代码重构完成** | Week 10 | Service拆分/AutoMapper/QueryWrapper优化 | 代码质量达标 |
| **M7: ERP全部交付** | Week 12 | 追溯/盘点审批流 | ERP功能100%完成 |
| **M8: MES基础上线** | Week 20 | 主数据/工单/工序/路线 | 工单可下达执行 |
| **M9: MES执行核心** | Week 27 | 流转卡/派工/报工/安灯 | 工序级追溯完整 |
| **M10: MES全部交付** | Week 34 | 质量/设备/数据采集/SPC | MES功能100%完成 |
| **M11: IoT多协议** | Week 44 | MQTT/OPC UA/Modbus/HTTP | 数据采集完整 |
| **M12: 平台全部交付** | Week 50 | 规则/告警/边缘/场景联动 | ERP+MES+IoT一体化 |

---

## 三、具体实施步骤

### 3.1 Phase 1: ERP 完工 (第1-12周)

#### 3.1.1 模块重构计划

| 周次 | 任务编号 | 任务内容 | 模块 | 人天 | 重构要点 | 状态 |
|------|---------|---------|------|------|----------|------|
| Week 1 | R-001 | 序列号规则表 | bd | 1 | 新增表+Domain/Service/Controller | ✅ 已完成 |
| Week 1 | R-006 | 供应商校验 | pu | 0.5 | 注入BdVendorMapper，实现校验逻辑 | ✅ 已完成 |
| Week 1 | Q-001 | bd/pu模块RuntimeException→ServiceException | bd/pu | 1 | 替换43处RuntimeException为ServiceException | ✅ 已完成 |
| Week 1 | Q-002 | SimpleDateFormat线程安全修复 | sch/wm | 0.5 | 3个文件5处改用DateTimeFormatter | ✅ 已完成 |
| Week 2 | R-007 | 请购来源扩展 | pu | 1 | 低库存建议/生产需求转入 | ✅ 已完成 |
| Week 2 | R-008 | 采购收货差异处理 | pu | 1 | 部分收货/超收/短收+状态回写 | ✅ 已完成 |
| Week 2 | R-009 | 收货自动过账 | pu | 0.5 | 联动WmStockOperationService | ✅ 已完成 |
| Week 2 | R-011 | 供应商评估实现 | pu | 1.5 | 评分算法+pu_vendor_evaluation表 | ✅ 已完成 |
| Week 2 | R-012 | 供应商绩效沉淀 | pu | 0.5 | 定期汇总评估数据 | ✅ 已完成 |
| Week 3 | R-013 | 上架策略 | wm | 1.5 | PutawayStrategyService | ✅ 已完成 |
| Week 3 | R-014 | 拣货策略 | wm | 1.5 | PickingStrategyService | ✅ 已完成 |
| Week 3 | R-015 | 库存估值 | wm | 2 | 移动加权平均成本计算 | ✅ 已完成 |
| Week 4 | R-016 | 安全库存预警 | wm | 1 | SnailJob定时任务 | ✅ 已完成 |
| Week 4 | R-017 | 库存台账校验 | wm | 1 | StockReconciliationService | ✅ 已完成 |
| Week 4 | R-018 | 委外订单 | prd | 1.5 | prd/subcontract子包 | ✅ 已完成 |
| Week 4 | R-019 | 委外发料与收货 | prd | 1 | WmStockOperationService联动 | ✅ 已完成 |
| Week 4 | R-020 | 返工订单 | prd | 1 | woType字段扩展 | ✅ 已完成 |
| Week 5 | R-021 | 生产消耗自动过账 | prd | 1.5 | 完工倒冲领料 | ✅ 已完成 |
| Week 5 | R-022 | 产能日历 | prd | 1.5 | prd_capacity_calendar表+Controller核心接口 | ✅ 已完成 |
| Week 5 | R-023 | 领料与完工入库联动 | prd | 1 | 状态变更同步库存 | ✅ 已完成 |
| Week 5 | F-021 | 倒冲领料前端 | 前端 | 1 | 工单/BOM倒冲标识+表单控件 | ✅ 已完成 |
| Week 5 | F-022 | 产能日历前端 | 前端 | 2.5 | API+类型定义+列表页+生成/占用/释放 | ✅ 已完成 |
| Week 5 | F-023 | 领料联动+完工入库前端 | 前端 | 3.5 | 领料单BOM自动填充+生产入库页面 | ✅ 已完成 |
| Week 4 | R-024 | 委外加工前端 | 前端 | 1 | 列表+表单页面 | ✅ 已完成 |
| Week 4 | R-025 | 返工前端 | 前端 | 1 | 页面开发 | ✅ 已完成 |
| Week 6 | R-026 | 采购收货联动入口 | 前端 | 0.5 | 进度展示 |
| Week 6 | R-027 | 供应商评估前端 | 前端 | 1 | 评估报表+趋势图 |
| Week 6 | R-028 | 产能日历前端 | 前端 | 1 | 产能日历可视化页面 | ✅ 已提前至Week5(F-022) |
| Week 7-8 | I-001 | MQ基础设施搭建 | infra | 3 | RabbitMQ/Kafka集成+Spring Cloud Stream |
| Week 7-8 | I-002 | 跨库事务补偿框架 | infra | 2 | 最终一致性+补偿任务调度+对账机制 |
| Week 7-8 | Q-003 | Service拆分重构 | quality | 2 | WmMaterialReceiveServiceImpl拆分为3个Service |
| Week 7-8 | Q-004 | @AutoMapper注解统一替换 | quality | 1.5 | 消除手动MapstructUtils调用 |
| Week 9-10 | Q-005 | LambdaQueryWrapper链式重构 | quality | 2 | 替换buildQueryWrapper重复代码 |
| Week 9-10 | Q-006 | Service基类统一+Jasypt加密 | quality | 2 | 统一继承ServiceImpl，数据库密码加密 |
| Week 9-10 | R-025 | 委外前端完善 | 前端 | 1 | 完善Week4委外前端 |
| Week 9-10 | R-026 | 销售管理增强 | so | 2 | 价格引擎基础版+信用检查联动 |
| Week 11-12 | R-041 | 序列号管理 | wm | 1.5 | wm_sn表，对接R-001 |
| Week 11-12 | R-042 | 批次管理增强 | wm | 1 | wm_batch独立表 |
| Week 11-12 | R-043 | 追溯查询服务 | wm | 2 | 前向+后向追溯 |
| Week 11-12 | R-044 | 追溯前端 | 前端 | 1 | 树图/表格展开 |
| Week 11-12 | R-055 | 盘点差异审批流 | wm | 1.5 | Flowable集成 |

#### 3.1.2 接口标准化计划

| 接口类型 | 标准规范 | 实施要求 |
|----------|---------|----------|
| **RESTful API** | GET/POST/PUT/DELETE + 资源路径 | 遵循现有Controller模板 |
| **权限注解** | @SaCheckPermission + 数据权限 | 所有接口必须配置 |
| **操作日志** | @Log注解 | 关键业务操作必须记录 |
| **参数校验** | @Validated + 自定义校验器 | 请求参数必须校验 |
| **返回格式** | R<T> 统一包装 | 所有接口统一返回 |
| **分页查询** | Page<T> + IPage | 列表接口必须支持分页 |
| **异常处理** | ServiceException + 错误码 | 禁止抛出RuntimeException |

#### 3.1.3 代码质量优化清单

| 优化项 | 当前问题 | 优化方案 | 优先级 | 状态 |
|--------|----------|----------|--------|------|
| **Service基类** | 未继承ServiceImpl | 统一继承ServiceImpl<Mapper, Entity> | 高 | ⏳ Week 9-10 Q-006 |
| **对象映射** | 手动调用MapstructUtils | 使用@AutoMapper注解自动映射 | 中 | ⏳ Week 7-8 Q-004 |
| **查询条件** | buildQueryWrapper重复 | 使用LambdaQueryWrapper链式调用 | 中 | ⏳ Week 9-10 Q-005 |
| **异常处理** | RuntimeException | 统一使用ServiceException | 高 | ✅ bd/pu模块已完成(43处) |
| **Service拆分** | WmMaterialReceiveServiceImpl 787行 | 拆分为CRUD/审批/库存转移三个Service | 高 | ⏳ Week 7-8 Q-003 |
| **对象拷贝** | 手动逐字段拷贝 | 使用BeanUtils或MapStruct | 中 | 待处理 |
| **日期处理** | SimpleDateFormat非线程安全 | 改用DateTimeFormatter | 中 | ✅ 全部完成(3文件5处) |
| **数据库密码** | 明文存储 | 使用Jasypt加密 | 中 | ⏳ Week 9-10 Q-006 |
| **单元测试** | 无测试代码 | 补充核心业务逻辑单测 | 中 | 待处理 |

#### 3.1.4 Week 1 执行记录

**执行日期**: 2026-05-27

**已完成任务汇总**:

| 任务编号 | 任务内容 | 涉及文件 | 变更数量 | 说明 |
|---------|---------|---------|---------|------|
| R-001 | 序列号规则表 | BdSerialNumberRule*.java (6文件) | 0 | 代码审查确认已完成，含完整CRUD+generateSerialNumber() |
| R-006 | 供应商校验 | PuPurchaseOrderServiceImpl.java | 0 | 代码审查确认已完成，validateVendor()含3项校验 |
| Q-001 | RuntimeException→ServiceException | bd模块2文件 + pu模块5文件 | 47处 | BdSerialNumberRuleServiceImpl(2), BdBomServiceImpl(2), PuPurchaseOrderServiceImpl(17), PuPurchaseRequisitionServiceImpl(11), PuPurchaseReceivingServiceImpl(3), PuPurchaseReturnServiceImpl(10), PuVendorEvaluationServiceImpl(1) |
| Q-002 | SimpleDateFormat修复 | BarcodeGenerator, AppSchAttendanceController, SchAttendanceServiceImpl | 3文件 | 全部改用DateTimeFormatter/LocalDate，消除线程安全风险 |

**代码变更统计**:
- 修改文件: **10个**
- RuntimeException→ServiceException: **47处**
- SimpleDateFormat→DateTimeFormatter: **3文件5处**
- 新增import: ServiceException(7文件) + DateTimeFormatter/LocalDate/ZoneId(3文件)

**Week 2 准备事项**:
- R-007 请购来源扩展: 需分析WmMaterialStock安全库存字段和PrdMaterialRequirement关联
- R-008 采购收货差异处理: 需设计差异率计算和PuPurchaseOrderLine状态回写逻辑
- R-009 收货自动过账: 需对接WmStockOperationService.postMaterialReceive()接口

#### 3.1.5 Week 2 执行记录

**执行日期**: 2026-05-27

**已完成任务汇总**:

| 任务编号 | 任务内容 | 涉及文件 | 变更类型 | 说明 |
|---------|---------|---------|---------|------|
| R-007 | 请购来源扩展 | PuPurchaseRequisitionServiceImpl, PuPurchaseRequisitionController, IPuPurchaseRequisitionService | 修改 | 新增generateFromLowStock()方法，扫描低库存物料自动生成采购申请 |
| R-008 | 采购收货差异处理 | WmMaterialReceiveServiceImpl, PuPurchaseOrderLine, PuPurchaseOrderServiceImpl, PuPurchaseOrderController | 修改 | 入库后回写receivedQuantity和receiveStatus，新增差异查询接口 |
| R-009 | 收货自动过账 | WmMaterialReceiveServiceImpl | 修改 | afterFlowHandle()审批通过后自动触发executeReceive() |
| R-011 | 供应商评估实现 | PuVendorEvaluation*.java (8文件), pu_vendor_evaluation.sql | 新增 | 全套CRUD+评估算法(交货及时率40%+质量合格率35%+价格竞争力25%) |
| R-012 | 供应商绩效沉淀 | VendorEvaluationTask.java, pom.xml | 新增 | SnailJob定时任务，每月批量计算所有活跃供应商评估 |

**代码变更统计**:
- 新增文件: **8个** (PuVendorEvaluation实体/Bo/Vo/Mapper/Service/Controller + SQL + Task)
- 修改文件: **6个** (WmMaterialReceiveServiceImpl, PuPurchaseOrderLine, PuPurchaseOrderServiceImpl/Controller, PuPurchaseRequisitionServiceImpl/Controller)
- 新增依赖: ruoyi-common-job (pom.xml)

**关键实现细节**:
- R-009: afterFlowHandle()中ActStatus.pass时自动调用executeReceive()，实现审批→入库一体化
- R-008: 入库后根据materialId匹配订单行，累加receivedQuantity，自动标记PARTIAL_RECEIVED/FULL_RECEIVED/OVER_RECEIVED
- R-007: 扫描BdMaterial.safetyStock与WmMaterialStock.stockQuantity对比，建议采购量=maxStock-currentStock
- R-011: 综合评分=交货及时率×40%+质量合格率×35%+价格竞争力×25%，等级A(≥90)/B(≥80)/C(≥60)/D(<60)

#### 3.1.6 Week 3 执行记录

**执行日期**: 2026-05-27

**已完成任务汇总**:

| 任务编号 | 任务内容 | 涉及文件 | 变更类型 | 说明 |
|---------|---------|---------|---------|------|
| R-013 | 上架策略 | WmPutawayRule*.java (9文件) + IPutawayStrategyService + PutawayStrategyServiceImpl | 新增 | 三种策略：NEAREST_EMPTY/CLASS_BASED/FIFO_LOCATION |
| R-014 | 拣货策略 | WmPickingRule*.java (11文件) + IPickingStrategyService + Fifo/FefoPickingStrategy + PickingStrategyFactory | 新增 | 两种策略：FIFO/FEFO，策略工厂模式 |
| R-015 | 库存估值 | WmMaterialStock.java, WmMaterialStockTx.java + IInventoryValuationService + MovingAverageValuationServiceImpl | 新增+修改 | 移动加权平均成本算法，新增unitCost/totalCost字段 |

**SQL DDL 变更**:
- 新增表: `wm_putaway_rule` (上架规则), `wm_picking_rule` (拣货规则)
- 字段扩展: `wm_material_stock` 新增 unit_cost/total_cost, `wm_material_stock_tx` 新增 unit_cost/tx_amount
- 字典: `wm_putaway_strategy_type`, `wm_picking_strategy_type`
- 菜单: 上架规则(2601), 拣货规则(2611), 库存估值(2621)

**前端变更**:
- 新增API: putawayRule/index.ts, pickingRule/index.ts, valuation/index.ts
- 新增页面: putawayRule/index.vue, pickingRule/index.vue, valuation/index.vue
- 路由: 新增3个表单路由

**代码变更统计**:
- 新增后端文件: **~20个**
- 修改后端文件: **2个** (WmMaterialStock, WmMaterialStockTx)
- 新增前端文件: **~9个** (API + 页面 + 类型定义)
- SQL脚本: **1个** (week3_warehouse_strategy.sql)

**关键实现细节**:
- R-013: PutawayStrategyServiceImpl 按优先级匹配规则，支持按仓库/物料类别配置不同策略
- R-014: PickingStrategyFactory 根据 WmPickingRule.strategyType 返回对应策略实现
- R-015: 移动加权平均公式 = (原库存金额 + 本次入库金额) / (原库存数量 + 本次入库数量)

#### 3.1.7 Week 4 执行记录

**执行日期**: 2026-05-28

**已完成任务汇总**:

| 任务编号 | 任务内容 | 涉及文件 | 变更类型 | 说明 |
|---------|---------|---------|---------|------|
| R-016 | 安全库存预警 | WmStockAlert*.java (8文件), StockAlertTask.java | 新增 | SnailJob定时任务，自动检查库存低于安全库存并生成预警 |
| R-017 | 库存台账校验 | WmStockReconciliation*.java (7文件), StockReconciliationServiceImpl | 新增 | 库存台账核对服务，支持实物盘点与系统库存差异比对 |
| R-018 | 委外订单 | PrdSubcontract*.java (9文件), PrdSubcontractController | 新增 | 完整委外订单CRUD，含状态流转(草稿→确认→执行→完成) |
| R-019 | 委外发料与收货 | SubcontractIssueStrategy.java, SubcontractReceiveStrategy.java | 新增 | 策略模式实现，联动WmStockOperationService处理委外出入库 |
| R-020 | 返工订单 | PrdReworkOrder*.java (9文件), ReworkIssueStrategy.java, ReworkReceiveStrategy.java | 新增 | 返工订单CRUD+出入库策略，支持工单返工流程 |
| R-024 | 委外加工前端 | subcontract/index.vue, subcontract/index.ts, subcontract/types.ts | 新增 | 委外订单列表+表单页面，支持增删改查 |
| R-025 | 返工订单前端 | rework/index.vue, rework/index.ts, rework/types.ts | 新增 | 返工订单列表+表单页面，支持增删改查 |

**SQL DDL 变更**:
- 新增表: `prd_subcontract` (委外订单), `prd_rework_order` (返工订单), `wm_stock_alert` (安全库存预警), `wm_stock_reconciliation` (库存台账校验)
- 字典: `prd_subcontract_status`, `prd_rework_order_status`, `wm_alert_level`
- 菜单: 委外订单(5401), 返工订单(5411), 安全库存预警(2631), 库存台账校验(2641)

**前端变更**:
- 新增API: subcontract/index.ts, rework/index.ts, stockAlert/index.ts, reconciliation/index.ts
- 新增页面: subcontract/index.vue, rework/index.vue, stockAlert/index.vue, reconciliation/index.vue
- 路由: 新增4个表单路由

**代码变更统计**:
- 新增后端文件: **~33个** (含实体/Bo/Vo/Mapper/Service/Controller/Strategy/Task)
- 新增前端文件: **~8个** (API 4个 + 页面 4个)
- SQL脚本: **1个** (week4_subcontract_rework_alert.sql)

**关键实现细节**:
- R-016: StockAlertTask 使用 @JobExecutor 注解，定时扫描 WmMaterialStock 与 BdMaterial.safetyStock 比对，低于安全库存时创建 WmStockAlert 记录
- R-017: StockReconciliationServiceImpl 核对库存台账，支持按仓库/物料维度进行差异分析
- R-018: PrdSubcontractServiceImpl 实现委外订单完整生命周期，包含供应商校验和物料检查
- R-019: SubcontractIssueStrategy/SubcontractReceiveStrategy 继承 StockOperationStrategy，通过工厂模式注册，实现委外发料(出库)和收货(入库)
- R-020: PrdReworkOrder 扩展 woType 字段支持返工类型，ReworkIssueStrategy/ReworkReceiveStrategy 处理返工出入库

**修复记录**:
- 修复 StockOperationTypeEnum 缺失枚举值: 新增 SUBCONTRACT_ISSUE/SUBCONTRACT_RECEIVE/REWORK_ISSUE/REWORK_RECEIVE
- 修复 @AutoMapper 注解导入路径: 7个文件从 org.dromara.common.core.annotation.AutoMapper 改为 io.github.linpeilie.annotations.AutoMapper
- 修复 WmStockAlertServiceImpl 单位名称获取: 通过 BdUnitOfMeasureMapper 查询单位名称而非直接获取

### 3.2 Phase 2: MES 建设 (第13-34周)

#### 3.2.1 模块结构规划

```
ruoyi-modules/ruoyi-mes/
  pom.xml
  src/main/java/com/nexus/mes/
    ├── config/                    # MES自动配置
    ├── common/                    # 公共类(枚举/常量/工具)
    ├── md/                        # 主数据 (扩展ERP bd)
    ├── pro/                       # 生产执行 (★核心模块)
    │   ├── controller/
    │   ├── domain/
    │   ├── mapper/
    │   └── service/impl/
    │       └── MesProCardServiceImpl.java  # 流转卡引擎 ★
    ├── wm/                        # 仓储扩展 (与ERP wm分层协作)
    ├── qc/                        # 质量控制 (MES完整实现，ERP不重复建设)
    ├── eq/                        # 设备管理 (MES完整实现，ERP不重复建设)
    ├── dc/                        # 数据采集 (nexus-erp独有)
    └── spc/                       # 统计过程控制 (nexus-erp独有)
```

#### 3.2.2 分阶段实施计划

**MES Phase 1: 基础骨架 + 主数据 (第13-20周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 13 | 创建ruoyi-mes模块、配置ry-mes数据源、初始化SQL、mes_md_*全部表+CRUD | 模块可编译，MD可用 | 5 |
| 14 | 工序定义(mes_pro_process)+工艺路线(mes_pro_route+route_process) | 工序/路线管理可用 | 4 |
| 15 | MES工单(mes_pro_workorder) CRUD，关联ERP工单 | MES工单基础可用 | 3 |
| 16 | 排班日历复用sch模块，配置MES工位排班 | 排班可用 | 2 |
| 17 | 前端: MD(BOM/SOP/SIP/工位资源)+工序/路线页面 | 主数据可视可用 | 5 |
| 18 | 前端: MES工单+排班日历页面 | 工单/排班可视 | 4 |
| 19 | 联调测试+Swagger文档 | — | 3 |
| 20 | 缓冲周 | — | 4 |

**MES Phase 2: 流转卡 + 执行核心 (第21-27周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 21 | 流转卡引擎: generateCards()、releaseCard()、状态机 | 流转卡生成可用 | 5 |
| 22 | 流转卡工序: startProcess()、completeProcess()、工序报工 | 工序执行可用 | 5 |
| 23 | SN级追溯: ProSnProcess+序列号绑定 | SN追溯可用 | 4 |
| 24 | 派工任务(mes_pro_task)+工序转移(mes_pro_trans_order) | 派工/转移可用 | 4 |
| 25 | 安灯系统: 触发/响应/解决/升级 | 安灯可用 | 4 |
| 26 | 前端: 流转卡管理/工序执行/派工看板/安灯看板 | 执行核心可视 | 5 |
| 27 | 集成测试+联调 | — | 3 |

**MES Phase 3: 仓库/质量/设备/数据采集 (第28-34周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 28 | MES仓库扩展: 领料/调拨/盘点/委外(三级单据模式) | 仓库扩展可用 | 5 |
| 29 | MES质量控制: IQC/IPQC/OQC/RQC(车间级完整版) | 质量完整闭环 | 5 |
| 30 | MES设备管理: 点检执行/保养记录/维修工单 | 设备管理完整 | 4 |
| 31 | 数据采集(DC)+SPC控制图 | DC/SPC可用 | 5 |
| 32 | 前端: qc/eq/dc/spc全部页面 | 前端完整 | 5 |
| 33 | 全模块集成测试: ERP↔MES跨库事务/MQ联动/数据一致性 | 集成问题修复 | 4 |
| 34 | 缓冲周: 性能优化+回归测试+文档补齐 | — | 4 |

#### 3.2.3 核心数据表设计

**生产执行核心表 (mes_pro_*)**

```sql
-- 工序定义
CREATE TABLE mes_pro_process (
    process_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    process_code    VARCHAR(64) NOT NULL,
    process_name    VARCHAR(128) NOT NULL,
    process_type    VARCHAR(32),
    standard_time   DECIMAL(10,2),
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
    material_id     BIGINT NOT NULL,
    status          VARCHAR(16) DEFAULT 'DRAFT',
    PRIMARY KEY (route_id)
);

-- 流转卡 (★MES最核心设计)
CREATE TABLE mes_pro_card (
    card_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    card_no         VARCHAR(64) NOT NULL,
    mes_wo_id       BIGINT NOT NULL,
    route_id        BIGINT NOT NULL,
    material_id     BIGINT NOT NULL,
    quantity        DECIMAL(14,4) NOT NULL,
    status          VARCHAR(16) DEFAULT 'CREATED',
    PRIMARY KEY (card_id),
    UNIQUE KEY uk_card_no (card_no)
);

-- 流转卡工序记录 (★最核心的MES数据表)
CREATE TABLE mes_pro_card_process (
    ccp_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    card_id         BIGINT NOT NULL,
    rp_id           BIGINT NOT NULL,
    seq_no          INT NOT NULL,
    process_code    VARCHAR(64),
    process_name    VARCHAR(128),
    workstation_id  BIGINT,
    equipment_id    BIGINT,
    operator_id     BIGINT,
    qty_processed   DECIMAL(14,4) DEFAULT 0,
    qty_good        DECIMAL(14,4) DEFAULT 0,
    qty_scrap       DECIMAL(14,4) DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'WAITING',
    PRIMARY KEY (ccp_id),
    INDEX idx_card (card_id)
);
```

**质量控制表 (mes_qc_*)**

```sql
-- 来料检验 IQC
CREATE TABLE mes_qc_iqc (
    iqc_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    iqc_no          VARCHAR(64) NOT NULL,
    receive_id      BIGINT,
    material_id     BIGINT NOT NULL,
    batch_id        BIGINT,
    result          VARCHAR(16),
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (iqc_id),
    UNIQUE KEY uk_iqc_no (iqc_no)
);

-- 过程检验 IPQC
CREATE TABLE mes_qc_ipqc (
    ipqc_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    ipqc_no         VARCHAR(64) NOT NULL,
    card_id         BIGINT,
    ccp_id          BIGINT NOT NULL,
    result          VARCHAR(16),
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (ipqc_id),
    UNIQUE KEY uk_ipqc_no (ipqc_no)
);
```

**设备管理表 (mes_eq_*)**

```sql
-- 设备台账
CREATE TABLE mes_equipment (
    equipment_id    BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    equipment_code  VARCHAR(64) NOT NULL,
    equipment_name  VARCHAR(128) NOT NULL,
    type_id         BIGINT NOT NULL,
    workstation_id  BIGINT,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uk_code (equipment_code, tenant_id)
);

-- 点检计划
CREATE TABLE mes_check_plan (
    plan_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    plan_name       VARCHAR(128) NOT NULL,
    equipment_id    BIGINT NOT NULL,
    check_frequency VARCHAR(16),
    next_check_date DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (plan_id)
);
```

### 3.3 Phase 3: IoT 建设 (第35-50周)

#### 3.3.1 模块结构规划

```
ruoyi-modules/ruoyi-iot/
  pom.xml
  src/main/java/org/dromara/iot/
    ├── config/                    # IoT自动配置+协议配置
    ├── common/                    # 枚举/常量/DTO
    ├── device/                    # 设备管理
    ├── protocol/                  # 协议适配
    │   ├── mqtt/                  # MQTT协议
    │   ├── opcua/                 # OPC UA协议
    │   ├── modbus/                # Modbus协议
    │   └── http/                  # HTTP协议
    ├── data/                      # 数据处理
    │   ├── pipeline/              # 数据管道
    │   ├── tsdb/                  # 时序数据库
    │   └── realtime/              # 实时数据
    ├── rule/                      # 规则引擎
    ├── alert/                     # 告警管理
    ├── scene/                     # 场景联动
    └── gateway/                   # 边缘网关
```

#### 3.3.2 分阶段实施计划

**IoT Phase 1: 基础设施 + 设备管理 (第35-39周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 35 | 创建ruoyi-iot模块、配置ry-iot数据源、初始化SQL、iot_device+iot_product核心表+CRUD | 模块可编译，设备管理可用 | 5 |
| 36 | MQTT Broker集成(Moquette)+设备认证+设备注册/密钥管理 | MQTT连接可用 | 5 |
| 37 | HTTP数据上报端点+设备在线状态管理(心跳检测) | HTTP上报可用 | 4 |
| 38 | 设备分组/标签+设备日志+物模型(产品→属性) | 设备管理完整 | 4 |
| 39 | 前端: 设备管理/分组/产品/物模型页面 | 前端可视可用 | 5 |

**IoT Phase 2: 协议 + 数据管道 (第40-44周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 40 | OPC UA客户端(Eclipse Milo): 连接/浏览/订阅/重连 | OPC UA可用 | 5 |
| 41 | Modbus驱动: TCP/RTU+轮询调度+寄存器映射 | Modbus可用 | 5 |
| 42 | 数据管道: 清洗→聚合→转发(DataPipelineService) | 数据管道可用 | 4 |
| 43 | 时序数据库集成(InfluxDB)+Redis实时缓存+WebSocket推送 | 实时数据可用 | 5 |
| 44 | 前端: 实时数据页面(ECharts)+协议配置页面 | 数据可视可用 | 4 |

**IoT Phase 3: 规则 + 告警 + 场景联动 + 边缘 (第45-50周)**

| 周次 | 任务内容 | 产出 | 人天 |
|------|---------|------|------|
| 45 | 规则引擎(Easy-Rules): 阈值/趋势/表达式规则 | 规则引擎可用 | 5 |
| 46 | 告警系统: 规则→触发→通知(系统/邮件/Webhook)+告警升级 | 告警完整闭环 | 5 |
| 47 | 边缘网关管理+边缘脚本引擎(Groovy)+数据缓冲 | 边缘计算可用 | 5 |
| 48 | 场景联动: 触发器→条件→动作+固件OTA | 场景联动+OTA可用 | 4 |
| 49 | IoT↔MQ↔MES全链路集成: 设备数据→MES联动(自动报工/质量触发) | 端到端集成 | 5 |
| 50 | 整体测试+IoT大屏看板+文档 | 全部上线 | 5 |

#### 3.3.3 核心数据表设计

```sql
-- 产品/物模型
CREATE TABLE iot_product (
    product_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    product_code    VARCHAR(64) NOT NULL,
    product_name    VARCHAR(128) NOT NULL,
    protocol_type   VARCHAR(16) NOT NULL,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_code (product_code, tenant_id)
);

-- 设备主表
CREATE TABLE iot_device (
    device_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    device_code     VARCHAR(64) NOT NULL,
    device_name     VARCHAR(128) NOT NULL,
    product_id      BIGINT NOT NULL,
    protocol_type   VARCHAR(16) NOT NULL,
    online_status   VARCHAR(16) DEFAULT 'OFFLINE',
    mes_workstation_id BIGINT,
    mes_equipment_id   BIGINT,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_code (device_code, tenant_id)
);

-- 告警规则
CREATE TABLE iot_alert_rule (
    alert_rule_id   BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    rule_name       VARCHAR(128) NOT NULL,
    device_id       BIGINT,
    property_code   VARCHAR(64) NOT NULL,
    operator        VARCHAR(16) NOT NULL,
    threshold       VARCHAR(128),
    alert_level     VARCHAR(16) DEFAULT 'WARNING',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (alert_rule_id)
);

-- 告警日志
CREATE TABLE iot_alert_log (
    alert_log_id    BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL,
    alert_rule_id   BIGINT NOT NULL,
    device_id       BIGINT NOT NULL,
    alert_level     VARCHAR(16) NOT NULL,
    alert_message   VARCHAR(512),
    trigger_time    DATETIME NOT NULL,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (alert_log_id),
    INDEX idx_device_time (device_id, trigger_time)
);
```

---

## 四、资源需求评估

### 4.1 人力资源需求

| 阶段 | 角色 | 人数 | 技能要求 |
|------|------|------|----------|
| **Phase 1** | Java后端开发 | 1人 | Spring Boot/MyBatis-Plus/Flowable |
| **Phase 1** | 前端开发 | 1人 | Vue 3/Element Plus/TypeScript |
| **Phase 2** | Java后端开发 | 1-2人 | Spring Boot/MES业务理解 |
| **Phase 2** | 前端开发 | 1人 | Vue 3/ECharts |
| **Phase 3** | Java后端开发 | 1-2人 | MQTT/OPC UA/Modbus协议 |
| **Phase 3** | 前端开发 | 1人 | Vue 3/WebSocket/实时数据 |
| **全周期** | 架构师/技术负责人 | 1人 | 全栈能力，架构设计 |
| **全周期** | 测试工程师 | 1人 | 功能测试/集成测试 |

**推荐配置**: 3-4人团队 (1架构+2后端+1前端)，全周期投入

### 4.2 时间资源需求

| 阶段 | 工期 | 工作量 | 人员配置 |
|------|------|--------|----------|
| **Phase 1: ERP完工** | 12周 | ~55人天 | 2人(后端+前端) |
| **Phase 2: MES建设** | 22周 | ~140人天 | 3人(2后端+1前端) |
| **Phase 3: IoT建设** | 16周 | ~100人天 | 3人(2后端+1前端) |
| **合计** | **50周** | **~295人天** | **3-4人** |

### 4.3 技术资源需求

| 资源类型 | 需求 | 说明 |
|----------|------|------|
| **开发环境** | JDK 17+ | Spring Boot 3.x要求 |
| **IDE** | IntelliJ IDEA | 推荐Ultimate版 |
| **数据库** | MySQL 8.0+ | 4个数据库(ry-vue/ry-erp/ry-mes/ry-iot) |
| **缓存** | Redis 7.x | 已有 |
| **工作流** | Flowable 7.0 | 已集成 |
| **消息队列** | RabbitMQ/Kafka | **Week 7-8集成，MES/IoT依赖** |
| **时序数据库** | InfluxDB 2.x/TDengine | Phase 3需要 |
| **协议栈** | Moquette/Eclipse Milo/modbus4j | Phase 3需要 |
| **版本控制** | Git | 已有 |
| **CI/CD** | Jenkins/GitHub Actions | 推荐配置 |
| **文档** | SpringDoc/OpenAPI 3 | 已集成 |
| **监控** | Spring Boot Admin | 已集成 |

### 4.4 硬件资源需求

| 环境 | 配置 | 用途 |
|------|------|------|
| **开发环境** | 8核16G内存 | 开发测试 |
| **测试环境** | 4核8G内存 | 功能测试 |
| **生产环境** | 8核32G内存+500G SSD | 正式部署 |
| **数据库服务器** | 8核32G内存+1T SSD | MySQL+InfluxDB |
| **MQ服务器** | 4核8G内存 | RabbitMQ/Kafka |
| **MQTT Broker** | 4核8G内存 | Moquette/EMQX |

---

## 五、风险评估及应对策略

### 5.1 技术风险

| 风险项 | 影响程度 | 发生概率 | 应对策略 |
|--------|----------|----------|----------|
| **ERP qc/eq与MES重复建设** | 高 | 高 | **已优化: ERP不建qc/eq，由MES统一实现，节省~17人天** |
| **ERP工期拖延影响MES启动** | 高 | 中 | ERP P3任务(财务/报表)可延后至MES并行开发 |
| **MES代码迁移工作量大** | 高 | 高 | 只参考设计模式，不直接迁移ktg-mes代码，使用nexus-erp分层规范重写 |
| **流转卡引擎性能** | 中 | 中 | 大批量SN时使用批量INSERT+异步生成，避免同步阻塞 |
| **OPC UA连接稳定性** | 中 | 中 | 实现指数退避重连+断点数据缓存+心跳检测 |
| **跨库事务一致性** | 高 | 高 | ERP/MES跨库采用最终一致性+补偿操作；IoT/MES通过MQ异步解耦 |
| **时序数据库选型** | 中 | 低 | 开发期使用InfluxDB 2.x(Docker)，生产环境可切换TDengine |
| **前端开发量大(~75页面)** | 中 | 中 | 使用nexus-erp现有代码生成器+列表页模板加速 |
| **MQTT Broker运维** | 中 | 中 | 开发期嵌入Moquette，生产环境建议独立部署EMQX |
| **多租户数据隔离** | 高 | 低 | 所有DDL包含tenant_id，MyBatis-Plus多租户插件自动注入 |
| **IoT安全** | 高 | 中 | 设备认证+ACL+数据加密+API限流 |

### 5.2 业务风险

| 风险项 | 影响程度 | 发生概率 | 应对策略 |
|--------|----------|----------|----------|
| **需求变更频繁** | 中 | 高 | 采用敏捷开发，每2周迭代一次，及时调整 |
| **用户接受度低** | 高 | 中 | 分阶段交付，及时收集反馈，快速迭代 |
| **数据迁移困难** | 中 | 中 | 提前规划数据迁移方案，编写迁移脚本 |
| **培训不足** | 中 | 中 | 编写详细操作手册，提供培训课程 |
| **业务流程不匹配** | 高 | 低 | 深入调研业务需求，提供可配置的工作流 |

### 5.3 项目管理风险

| 风险项 | 影响程度 | 发生概率 | 应对策略 |
|--------|----------|----------|----------|
| **人员流失** | 高 | 中 | 代码规范统一，文档完善，降低人员依赖 |
| **进度失控** | 高 | 中 | 每周进度评审，及时发现和解决问题 |
| **沟通不畅** | 中 | 中 | 建立定期沟通机制，使用项目管理工具 |
| **预算超支** | 中 | 低 | 严格控制范围，优先实现核心功能 |

### 5.4 风险应对预案

```
风险应对四象限:
                    发生概率高              发生概率低
影响程度高  │ 重点监控+预案           │ 关注+定期评估
           │ (ERP工期/跨库事务)      │ (多租户/数据迁移)
           ├─────────────────────────┼──────────────────
影响程度低  │ 接受+快速响应           │ 接受+监控
           │ (需求变更/沟通)         │ (技术选型)
```

---

## 六、质量保障措施

### 6.1 代码审查制度

| 审查类型 | 审查内容 | 审查频率 | 审查人 |
|----------|----------|----------|--------|
| **代码规范** | 命名/注释/格式/分层 | 每次提交 | 架构师 |
| **业务逻辑** | 状态机/校验规则/异常处理 | 每个功能点 | 架构师+后端 |
| **SQL审查** | 索引/性能/安全 | 每个DAO变更 | DBA/架构师 |
| **安全审查** | 权限/加密/注入 | 关键模块 | 安全工程师 |
| **前端审查** | 组件复用/类型/性能 | 每个页面 | 前端负责人 |

### 6.2 测试计划

| 测试类型 | 测试范围 | 测试工具 | 测试频率 |
|----------|----------|----------|----------|
| **单元测试** | Service层核心业务逻辑 | JUnit 5 + Mockito | 每次提交 |
| **集成测试** | 跨模块调用/数据库操作 | Spring Boot Test | 每个功能点 |
| **接口测试** | RESTful API | Postman/Swagger | 每个接口 |
| **功能测试** | 业务流程端到端 | 手动测试 | 每个迭代 |
| **性能测试** | 关键接口响应时间/并发 | JMeter | 关键模块上线前 |
| **安全测试** | 权限/注入/加密 | OWASP ZAP | 上线前 |
| **兼容性测试** | 浏览器/移动端 | BrowserStack | 上线前 |

### 6.3 测试用例设计

**ERP 核心业务测试用例**

| 模块 | 测试场景 | 预期结果 |
|------|----------|----------|
| **采购** | 采购申请→订单→收货→入库完整流程 | 库存增加，状态流转正确 |
| **采购** | 部分收货/超收/短收处理 | 差异处理正确，状态回写正确 |
| **仓储** | 上架策略自动推荐库位 | 推荐库位符合策略规则 |
| **仓储** | FIFO拣货策略 | 先入库批次先出库 |
| **仓储** | 移动加权平均成本计算 | 每次入库后成本计算正确 |
| **生产** | 工单→领料→生产→完工→入库 | 库存联动正确，状态流转正确 |
| **生产** | 委外发料→委外收货 | 库存联动正确 |
| **质量** | IQC检验→入库放行 | 检验结果影响入库 |
| **设备** | 点检计划→点检记录 | 点检执行记录完整 |

**MES 核心业务测试用例**

| 模块 | 测试场景 | 预期结果 |
|------|----------|----------|
| **流转卡** | 工单→生成流转卡→下达 | 流转卡生成正确，工序展开正确 |
| **流转卡** | 开始工序→报工→转移→完成 | 工序状态流转正确，产量累计正确 |
| **流转卡** | SN级追溯 | 完整加工路径可追溯 |
| **安灯** | 触发→响应→解决→升级 | 状态流转正确，超时升级正确 |
| **质量** | IPQC检验→缺陷记录 | 检验结果影响流转卡 |
| **设备** | 点检执行→保养记录 | 设备维护记录完整 |
| **数据采集** | 手动/自动数据采集 | 数据记录正确 |
| **SPC** | SPC控制图→超控告警 | 超控检测正确 |

### 6.4 性能监控指标

| 监控项 | 指标 | 阈值 | 监控工具 |
|--------|------|------|----------|
| **API响应时间** | P95延迟 | < 500ms | Spring Boot Admin |
| **数据库查询** | 慢查询 | > 1s | p6spy/MySQL慢查询日志 |
| **内存使用** | 堆内存 | < 80% | Spring Boot Admin |
| **CPU使用率** | 平均负载 | < 70% | 操作系统监控 |
| **并发用户** | 在线用户数 | 支持100+ | 应用日志 |
| **MQ消息积压** | 积压数量 | < 1000 | RabbitMQ Management |
| **设备连接数** | MQTT连接 | 支持1000+ | MQTT Broker监控 |
| **时序数据写入** | 写入延迟 | < 100ms | InfluxDB监控 |

### 6.5 持续集成/持续部署 (CI/CD)

```
代码提交 → 代码审查 → 单元测试 → 构建 → 集成测试 → 部署测试环境 → 功能测试 → 部署生产环境
   │           │          │        │         │            │            │            │
   ▼           ▼          ▼        ▼         ▼            ▼            ▼            ▼
  Git       SonarQube   JUnit   Maven    Docker       Jenkins     手动测试     Kubernetes
```

---

## 七、验收标准和交付物清单

### 7.1 Phase 1 验收标准 (ERP完工)

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| **功能完整性** | ERP全部P0/P1任务完成(qc/eq由MES统一实现) | 功能演示+测试用例通过 |
| **代码质量** | 无严重代码质量问题 | SonarQube扫描通过 |
| **接口规范** | RESTful规范，权限配置完整 | Swagger文档+接口测试 |
| **数据库** | DDL完整，索引合理 | 数据库审查 |
| **前端页面** | 对应功能页面完整 | 页面演示 |
| **单元测试** | 核心业务逻辑覆盖 | 测试报告 |
| **文档** | API文档+操作手册 | 文档审查 |

**交付物清单**

| 交付物 | 数量 | 说明 |
|--------|------|------|
| Java代码 | ~100个文件 | Controller/Service/Mapper/Domain |
| SQL脚本 | ~50个表 | DDL+初始化数据 |
| 前端代码 | ~15个页面 | Vue 3组件 |
| API文档 | 自动生成 | SpringDoc |
| 测试用例 | ~200个 | 单元测试+集成测试 |
| 操作手册 | 1份 | 用户手册 |

### 7.2 Phase 2 验收标准 (MES建设)

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| **模块独立性** | ruoyi-mes模块独立编译运行 | Maven构建+启动测试 |
| **数据源隔离** | ry-mes数据库独立 | 数据源配置验证 |
| **流转卡引擎** | 工单→流转卡→工序→报工完整流程 | 端到端测试 |
| **质量控制** | IQC/IPQC/OQC完整流程 | 测试用例通过 |
| **设备管理** | 台账/点检/保养/维修完整 | 功能演示 |
| **数据采集** | 手动/自动采集+SPC控制图 | 功能演示 |
| **ERP联动** | ERP工单→MES流转卡联动 | 集成测试 |
| **前端完整** | ~40个页面 | 页面演示 |
| **性能** | 关键接口响应<500ms | 性能测试 |

**交付物清单**

| 交付物 | 数量 | 说明 |
|--------|------|------|
| Java代码 | ~200个文件 | 8个子模块 |
| SQL脚本 | ~60个表 | mes_*表 |
| 前端代码 | ~40个页面 | 含看板/大屏 |
| API文档 | 自动生成 | SpringDoc |
| 测试用例 | ~400个 | 单元测试+集成测试 |
| 操作手册 | 1份 | MES用户手册 |

### 7.3 Phase 3 验收标准 (IoT建设)

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| **模块独立性** | ruoyi-iot模块独立编译运行 | Maven构建+启动测试 |
| **数据源隔离** | ry-iot数据库独立 | 数据源配置验证 |
| **多协议接入** | MQTT/OPC UA/Modbus/HTTP全部支持 | 协议测试 |
| **设备管理** | 设备注册/认证/在线状态管理 | 功能演示 |
| **数据管道** | 数据采集→清洗→聚合→存储完整 | 端到端测试 |
| **规则引擎** | 阈值/趋势/表达式规则触发 | 规则测试 |
| **告警系统** | 规则→触发→通知完整闭环 | 告警测试 |
| **场景联动** | 触发器→条件→动作联动 | 场景测试 |
| **MES联动** | 设备数据→MES自动报工/质量触发 | 集成测试 |
| **实时数据** | WebSocket推送延迟<1s | 性能测试 |
| **大屏看板** | 生产/库存/质量实时展示 | 页面演示 |

**交付物清单**

| 交付物 | 数量 | 说明 |
|--------|------|------|
| Java代码 | ~150个文件 | 8个子模块 |
| SQL脚本 | ~20个表 | iot_*表 |
| 前端代码 | ~20个页面 | 含实时数据/大屏 |
| API文档 | 自动生成 | SpringDoc |
| 测试用例 | ~300个 | 单元测试+集成测试 |
| 操作手册 | 1份 | IoT用户手册 |
| 部署文档 | 1份 | 含MQTT Broker/InfluxDB配置 |

### 7.4 总体验收标准 (平台交付)

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| **系统集成** | ERP+MES+IoT三系统无缝集成 | 端到端测试 |
| **数据一致性** | 跨系统数据同步准确 | 数据校验 |
| **性能指标** | 支持100+并发用户，1000+设备连接 | 压力测试 |
| **安全指标** | 无高危漏洞，权限控制完整 | 安全测试 |
| **可用性** | 系统可用率>99.5% | 运行监控 |
| **文档完整** | 设计文档+API文档+操作手册+部署文档 | 文档审查 |
| **培训完成** | 关键用户培训完成 | 培训记录 |

---

## 八、总体时间线与里程碑甘特图

```
Month 1-3   ████ Phase 1: ERP完工 (Week 1-12)
             ├── Week 1-2: P0清零+采购闭环
             ├── Week 3-5: 仓储策略+生产深度
             ├── Week 6: P1前端收尾
             ├── Week 7-8: MQ基建+跨库事务+代码重构
             ├── Week 9-10: 代码重构完成+销售增强
             └── Week 11-12: 追溯+盘点审批
             ★ 里程碑: ERP全部功能交付

Month 4-5   ████ MES Phase 1: 基础骨架 (Week 13-20)
             ├── Week 13-16: 主数据+工单+工序+路线
             └── Week 17-20: 前端+联调
             ★ 里程碑: MES基础功能上线

Month 6-7   ████ MES Phase 2: 执行核心 (Week 21-27)
             ├── Week 21-25: 流转卡+派工+报工+安灯
             └── Week 26-27: 前端+集成测试
             ★ 里程碑: MES执行核心上线

Month 8-9   ████ MES Phase 3: 扩展模块 (Week 28-34)
             ├── Week 28-30: 仓库+质量(qc)+设备(eq)
             ├── Week 31-32: 数据采集+SPC+前端
             └── Week 33-34: 全模块集成测试+缓冲
             ★ 里程碑: MES全部功能交付

Month 10-11 ████ IoT Phase 1/2: 基础+协议 (Week 35-44)
             ├── Week 35-39: 设备管理+MQTT+HTTP
             └── Week 40-44: OPC UA+Modbus+数据管道
             ★ 里程碑: IoT多协议数据采集完成

Month 12-13 ████ IoT Phase 3: 智能+集成 (Week 45-50)
             ├── Week 45-48: 规则+告警+边缘+场景
             └── Week 49-50: 全链路集成+测试+文档
             ★ 里程碑: ERP+MES+IoT一体化平台交付
```

---

## 九、ERP与MES边界划分

### 9.1 边界划分原则

| 原则 | 说明 |
|------|------|
| **主数据统一** | 物料/供应商/客户/计量单位/仓库库位 → ERP独有，MES只引用 |
| **工位分工** | ERP的`bd_workstation`管基础信息，MES的`mes_md_workstation`管资源绑定 |
| **BOM分工** | ERP的`bd_bom`管设计BOM，MES的`mes_md_product_bom`管工序级投料BOM |
| **库存统一** | `wm_material_stock`是唯一库存台账，MES工序产出回写到ERP |
| **质量合并** | IQC/IPQC/OQC/缺陷只用MES版（mes_qc_*），ERP不建qc模块 |
| **设备合并** | 设备台账/点检/保养/维修只用MES版（mes_eq_*），ERP不建eq模块 |
| **条码统一** | 条码管理统一用`wm_barcode`，MES不重复 |
| **生产分工** | ERP建工单，MES工单引用ERP工单并增加工序管控 |
| **MES独有** | 流转卡/派工/工序转移/安灯/SPC/数据采集 → 仅MES |

### 9.2 模块归属对照表

| 功能领域 | ERP (ry-erp) | MES (ry-mes) | 重叠程度 |
|---------|-------------|-------------|---------|
| **物料主数据** | `bd_material` | - | 无重叠 |
| **供应商/客户** | `bd_vendor`/`bd_client` | - | 无重叠 |
| **BOM** | `bd_bom` | `mes_md_product_bom` (工序级) | 30% |
| **工位** | `bd_workstation` | `mes_md_workstation` (资源绑定) | 40% |
| **SOP/SIP** | - | `mes_md_product_sop/sip` | 无重叠 |
| **生产工单** | `prd_work_order` | `mes_pro_workorder` (引用) | 清晰 |
| **工艺路线** | - | `mes_pro_route` | 无重叠 |
| **流转卡** | - | `mes_pro_card` ★ | 无重叠 |
| **派工/报工** | `prd_work_report` | `mes_pro_feedback` | 可合并 |
| **采购管理** | `pu_*` | - | 无重叠 |
| **销售管理** | `so_*` | - | 无重叠 |
| **库存台账** | `wm_material_stock` | - | 无重叠 |
| **批次管理** | `wm_batch` (档案) | `mes_wm_batch` (追溯) | 分层 |
| **序列号管理** | `wm_sn` (档案) | `mes_wm_sn` (追溯) | 分层 |
| **条码管理** | `wm_barcode` | 引用ERP | 统一 |
| **质量检验** | - | `mes_qc_*` | **统一用MES** |
| **设备管理** | - | `mes_eq_*` | **统一用MES** |
| **数据采集** | - | `mes_dc_*` | 无重叠 |
| **SPC** | - | `mes_spc_*` | 无重叠 |

### 9.3 消除的重复建设

| 原计划ERP任务 | 废弃理由 | 节省人天 |
|-------------|---------|---------|
| Week 7-8 ERP qc全部 (R-028~034) | 与MES qc完全重复 | ~9.5人天 |
| Week 9-10 ERP eq全部 (R-035~040) | 与MES eq完全重复 | ~7.5人天 |
| **合计节省** | | **~17人天** |

### 9.4 数据流向图

```
┌────────────────────────────────────────────────────────────────────┐
│                         ERP (ry-erp 数据库)                        │
│                                                                    │
│  采购订单 ──收货──→ IQC检验 ←── 触发                             │
│       │            │                    │                         │
│       │            ▼                    │                         │
│       │      ┌─────────────┐            │                         │
│       │      │  wm_stock   │◄───────────┘                         │
│       │      │ (库存台账)   │                                      │
│       │      └─────────────┘                                      │
│       │            ▲                                             │
│  工单 ──┬──领料──┤                                              │
│         │         │                                              │
│         │         ▼                                              │
│         │   ┌─────────────┐    MES (ry-mes 数据库)              │
│         └──►│ MES工单     │◄───── 工艺路线                    │
│             │ (引用ERP)   │                                     │
│             └──────┬──────┘                                     │
│                    │                                             │
│                    ▼                                             │
│             ┌─────────────┐                                      │
│             │  流转卡     │ ★ MES核心                          │
│             └──────┬──────┘                                     │
│                    │                                             │
│     ┌──────────────┼──────────────┐                              │
│     ▼              ▼              ▼                              │
│ 工序1 ──────► 工序2 ──────► 工序3                               │
│     │              │              │                               │
│     ▼              ▼              ▼                              │
│ 报工/IPQC       报工/IPQC       报工/OQC                         │
│     │              │              │                               │
│     └──────────────┴──────────────┘                               │
│                    │                                             │
│                    ▼                                             │
│             ┌─────────────┐                                      │
│             │ 完工入库    │ ─────────────────────────► ERP wm     │
│             └─────────────┘                                      │
└────────────────────────────────────────────────────────────────────┘
```

---

## 十、附录

### 10.1 术语表

| 术语 | 说明 |
|------|------|
| **ERP** | Enterprise Resource Planning，企业资源计划 |
| **MES** | Manufacturing Execution System，制造执行系统 |
| **IoT** | Internet of Things，物联网 |
| **流转卡** | MES核心概念，工单按批次拆分的生产执行载体 |
| **工序** | 生产过程中的一个加工步骤 |
| **IQC** | Incoming Quality Control，来料检验 |
| **IPQC** | In-Process Quality Control，过程检验 |
| **OQC** | Outgoing Quality Control，出厂检验 |
| **SPC** | Statistical Process Control，统计过程控制 |
| **OEE** | Overall Equipment Effectiveness，设备综合效率 |
| **安灯** | Andon，生产现场异常报警系统 |

### 10.2 参考文档索引

| 编号 | 文档 | 路径 |
|------|------|------|
| D1 | ERP剩余功能开发计划清单 | `script/ERP剩余功能开发计划清单.md` |
| D2 | ERP完成度分析与MES设计方案 | `script/ERP完成度分析与MES设计方案.md` |
| D3 | NexusERP完整扩展计划 | `script/NexusERP完整扩展计划_ERP_MES_IoT.md` |
| D4 | ry-erp.sql DDL脚本 | `script/sql/ry-erp.sql` |
| D5 | application-dev.yml配置 | `ruoyi-admin/src/main/resources/application-dev.yml` |

### 10.3 关键联系人

| 角色 | 职责 |
|------|------|
| 架构师 | 整体架构设计，技术决策，代码审查 |
| 后端开发 | Java代码开发，数据库设计，接口开发 |
| 前端开发 | Vue 3页面开发，组件开发，交互设计 |
| 测试工程师 | 测试用例设计，功能测试，性能测试 |
| 项目经理 | 进度管理，资源协调，风险管理 |

---

> **文档版本**: v1.1 (优化版)
> **编制日期**: 2026-05-27
> **优化日期**: 2026-05-29
> **编制依据**: nexus-erp代码库深度分析 + ERP/MES边界梳理 + 重复建设消除
> **总工期**: 50周 (约12.5个月)
> **总工作量**: ~295人天
> **推荐团队**: 3-4人 (1架构+2后端+1前端)
>
> **v1.1 关键变更**:
> - ERP qc/eq模块删除，由MES统一实现，消除重复建设，节省~17人天
> - 新增Week 7-8: MQ基础设施(RabbitMQ/Kafka) + 跨库事务补偿框架
> - 新增Week 7-10: 代码重构(Service拆分/AutoMapper/QueryWrapper优化)
> - MES工期从20周延长至22周，增加2周缓冲和集成测试
> - IoT整体顺延2周，全链路集成预留更充分时间
