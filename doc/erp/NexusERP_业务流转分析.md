# Nexus-ERP 全业务流转分析

> 生成日期：2026-06-03
> 覆盖范围：后端（ruoyi-erp）、管理后台（plus-ui）、移动端（Nexus_App）

---

## 一、系统架构总览

系统由 **3 层** 构成：

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| **后端** | Spring Boot + MyBatis-Plus + Flowable + RabbitMQ | 7大业务模块 + MQ异步消费者 |
| **管理后台** | Vue3 + Element Plus + TypeScript | 6大业务子模块，28+动态路由 |
| **移动端** | UniApp + Vue3 | 供应商/客户订单/考勤3个业务入口 |

---

## 二、7大业务模块及核心实体

| 模块代码 | 模块名称 | 核心实体 |
|---------|---------|---------|
| **BD** | 基础数据 | 物料、物料分类、BOM、客户、供应商、计量单位、车间、工位、序列号规则 |
| **SO** | 销售管理 | 报价单、销售订单、客户订单、销售退货、价格表、信用额度 |
| **PU** | 采购管理 | 采购申请、采购订单、采购退货、供应商评估 |
| **PRD** | 生产管理 | 生产计划、物料需求、生产工单、报工、返工单、委外单、产能日历、工单资源 |
| **WM** | 仓储管理 | 仓库/库区/库位、库存、入库/出库/调拨/盘点、批次/序列号/条码、追溯/估值/预警/对账 |
| **SCH** | 排班考勤 | 排班计划、班次、班组、员工排班、考勤、打卡规则、假日 |
| **MQ** | 消息队列 | 生产倒冲、采购回写、入库过账、返工库存、委外库存 |

---

## 三、核心业务流转方向

### 3.1 销售到收款（Order-to-Cash）

```
客户订单（CustomerOrder）
  ↓
报价单（Quotation） ──确认──→ 转销售订单（SalesOrder）
  ↓                            ↓
                      信用检查 + 定价引擎
                            ↓
                      提交Flowable审批
                            ↓
                      确认订单 ──→ 履行订单
                            ↓              ↓
                     销售出库（SalesIssue）  关闭订单
                       ↓  [审批流]
                    扣减库存
                       ↓
              ──→ 如需退货 ──→ 销售退货（Return）[审批流] ──→ 退货入库
```

**关键流转节点**：
- 报价单 → 销售订单（`convertToOrder`）
- 销售订单行关联生产工单（`linkedMoId`）和采购订单（`linkedPoId`）
- 信用检查（`ICreditCheckService`）+ 定价引擎（`IPricingEngineService`）

### 3.2 采购到付款（Procure-to-Pay）

```
低库存预警 ──→ 自动生成采购申请（Requisition）[审批流]
                    ↓
              审批通过 ──→ 转采购订单（PurchaseOrder）[审批流]
                                ↓
                          审批通过 ──→ 生成收货联动单据
                                          ↓
                                    到货通知（ArrivalNotice）[审批流]
                                          ↓
                                    审批通过 ──→ 自动创建物料入库单（Receive）[审批流]
                                                    ↓
                                              审批通过（待上架/APPROVED）
                                                    ↓
                                              上架操作：添加上架明细（分配库位）→ 确认上架
                                                    ↓
                                              待入库（SHELVED） ──→ 记录上架人(shelfBy) + 上架时间(shelfTime)
                                                    ↓
                                              点击入库（executeReceive）
                                              ├── 防重校验（hasPosted）
                                              ├── 虚拟库位扣减 + 目标库位增加
                                              ├── 更新入库单状态 → FINISHED
                                              ├── 更新到货通知状态 → FINISHED
                                              └── 生成条码
                                                    ↓
                                              事务提交后 ──→ WmMqSendService（@Async） ──→ 发送 PurchaseReceiveWritebackMessage
                                                                              ↓
                                                                    PurchaseReceiveWritebackConsumer
                                                                    ├── TenantHelper.dynamic() 设置租户上下文
                                                                    └── writeBackReceivedQty()
                                                                        ├── 按物料ID/编码匹配收货行与采购行
                                                                        ├── 累加已收货数量 + 更新行级收货状态
                                                                        └── 更新采购订单状态（PARTIALLY_RECEIVED / RECEIVED）
              ──→ 如需退货 ──→ 采购退货（PurchaseReturn）[审批流] ──→ 确认 ──→ 执行退货出库 ──→ 扣减库存
```

**物料入库单状态机**：
```
DRAFT(草稿) → APPROVING(审批中) → APPROVED(待上架) → SHELVED(待入库) → FINISHED(已完成)
                                     ↑                    │
                              REJECTED(已驳回) ←──────────┘ (可重新提交)
```

**关键流转节点**：
- 低库存自动生成（`generate-from-low-stock`）
- 采购申请 → 采购订单（`convert`）
- 采购订单收货 → 自动创建到货通知 → 到货通知审批通过自动创建物料入库单
- 物料入库单审批通过 → 待上架（不再发送StockInMessage）
- 确认上架（`confirmShelf`）：校验上架明细和库位，记录上架人和上架时间，状态变为待入库
- 执行入库（`executeReceive`）：虚拟库位转储 + 事务提交后异步回写采购订单
- MQ异步：仅采购回写（入库完成后通过 `WmMqSendService` 发送 `PurchaseReceiveWritebackMessage`）

### 3.3 生产制造（Manufacturing）

```
销售订单行 ──→ 创建生产计划（ProductionPlan）
                    ↓
              BOM树计算 ──→ 生成物料需求（MaterialRequirement）
                    ↓                          ↓
              生成工单（WorkOrder）        导入BOM明细
                    ↓
    ┌───────────────┼───────────────┐
    ↓               ↓               ↓
  下达工单      委外加工        返工单
    ↓          （Subcontract）    （Rework）
  开始生产         ↓               ↓
    ↓          下达→发料→开始   开始→发料→完成→收料
  生产领料         ↓               ↓
  （ProductionIssue） 委外收货     回写报废数量到来源工单
    ↓ [扣减库存]    ↓
  生产报工       MQ:委外库存
  （WorkReport）   消费者
    ↓
  完工 ──→ 生产入库（ProductionRecpt） ──→ 增加库存
    ↓
  关闭工单
    ↓
  [如启用倒冲] MQ: ProductionBackflushConsumer ──→ 倒冲领料 + 成品入库过账
```

**关键流转节点**：
- 生产计划 → BOM计算 → 物料需求 → 工单（完整MRP链路）
- 工单状态机：草稿 → 下达 → 开工 → 完工 → 关闭
- 倒冲模式：完工后通过MQ异步扣减原材料
- 委外/返工：独立的子流程，通过MQ与仓储联动

### 3.4 仓储管理（Warehouse Management）

```
三级结构: 仓库（Warehouse） → 库区（StorageArea） → 库位（StorageLocation）

入库通道:
  采购入库（ReceiveInStrategy）     ← 采购订单收货
  生产完工入库（ProductionCompletionStrategy） ← 工单完工
  委外收货（SubcontractReceiveStrategy）   ← 委外完成
  返工入库（ReworkReceiveStrategy）       ← 返工完成
  杂项入库（MiscRecptStrategy）          ← 其他入库

出库通道:
  销售出库（SalesOutStrategy）       ← 销售订单履行
  生产领料（ProductionIssueStrategy） ← 工单领料
  委外发料（SubcontractIssueStrategy） ← 委外发料
  返工出库（ReworkIssueStrategy）     ← 返工领料
  采购退货出库                       ← 采购退货执行
  杂项出库（MiscIssueStrategy）       ← 其他出库

库内操作:
  调拨（TransferStrategy） ──→ 仓间/库区间移动
  盘点调整（CountAdjustStrategy） ──→ 盘点→生成盘点行→过账调整

辅助功能:
  拣货策略: FIFO / FEFO
  上架策略: PutawayRule
  库存估值: 移动平均法
  库存追溯: 正向（原材料→成品） / 反向（成品→原材料）
  库存预警: 定时任务自动检查
  库存对账: 账面 vs 事务余额
  批次/序列号/条码: 全生命周期管理
```

### 3.5 排班考勤（Scheduling & Attendance）

```
排班计划（SchPlan） ──→ 关联班组（PlanTeam）
       ↓
  班次定义（Shift） ──→ 班组排班（TeamSchedule） ──→ 员工排班（EmployeeSchedule）
       ↓                                                    ↓
  打卡规则（PunchRule）                                  考勤记录（Attendance）
  [位置/WiFi/蓝牙限制]                               [迟到/早退/加班]
       ↓
  产能日历（CapacityCalendar） ──→ 占用产能（工单下达） / 释放产能（工单关闭）

移动端打卡流程:
  获取排班 → 获取位置 → 验证位置范围 → 上班打卡/下班打卡 → 计算迟到/加班
```

---

## 四、跨模块业务流转总图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BD 基础数据层                                  │
│  物料 ── BOM ── 客户 ── 供应商 ── 计量单位 ── 车间/工位 ── 序列号规则    │
└──────────┬──────────────┬──────────────┬──────────────┬──────────────┘
           │              │              │              │
     ┌─────↓─────┐  ┌────↓────┐  ┌─────↓─────┐  ┌────↓────┐
     │  SO 销售   │  │ PU 采购  │  │ PRD 生产   │  │ SCH 排班 │
     │            │  │          │  │            │  │         │
     │ 客户订单   │  │ 采购申请 │  │ 生产计划   │  │ 排班计划 │
     │ 报价单     │  │ 采购订单 │  │ 物料需求   │  │ 班次/班组│
     │ 销售订单   │  │ 采购退货 │  │ 生产工单   │  │ 员工排班 │
     │ 销售退货   │  │ 供应商评估│  │ 报工/返工  │  │ 考勤记录 │
     │ 价格表     │  │          │  │ 委外加工   │  │ 产能日历 │
     └─────┬──────┘  └────┬─────┘  └─────┬──────┘  └────┬────┘
           │              │              │              │
           └──────┬───────┴──────┬───────┴──────────────┘
                  ↓              ↓
           ┌──────────────────────────────┐     ┌─────────────┐
           │       WM 仓储管理             │←───→│ MQ 消息队列   │
           │                              │     │             │
           │ 库存（乐观锁） ── 12种库存策略   │     │ 生产倒冲     │
           │ 入库/出库/调拨/盘点           │     │ 采购回写     │
           │ 批次/序列号/条码/追溯         │     │ 入库过账     │
           │ 估值/预警/对账               │     │ 返工/委外库存 │
           └──────────────────────────────┘     └─────────────┘
```

---

## 五、审批流集成点

以下单据集成了 **Flowable 工作流**，统一接口模式：`POST /{id}/submit?selectFlowId=` + `GET /{id}/proc`

| 模块 | 可审批单据 |
|------|-----------|
| SO | 销售订单、销售退货 |
| PU | 采购申请、采购订单、采购退货 |
| PRD | 生产工单 |
| WM | 物料入库、销售出库、调拨单、盘点计划 |

---

## 六、MQ异步消息流转

| 队列 | 触发场景 | 消费逻辑 |
|------|---------|---------|
| `PRODUCTION_COMPLETE_QUEUE` | 生产工单完工 | 倒冲领料 + 成品入库过账 |
| `STOCK_IN_QUEUE` | （保留，当前采购入库不再使用） | 按sourceType分发，采购入库→发送回写消息 |
| `PURCHASE_RECEIVE_QUEUE` | 物料入库单执行入库完成后 | 回写采购订单已收货数量 + 更新采购订单状态 |
| `REWORK_STOCK_QUEUE` | 返工出库/入库 | 更新返工订单状态 |
| `SUBCONTRACT_STOCK_QUEUE` | 委外发料/收货 | 更新委外订单状态（ISSUE→IN_PROGRESS, RECEIVE→COMPLETED） |

**采购入库MQ流转说明**：
- 触发时机：用户点击"入库"按钮执行 `executeReceive` 成功后，事务提交时通过 `TransactionSynchronization.afterCommit()` 触发
- 发送方式：`WmMqSendService`（`@Async`），独立于 `@DS("erp")` 上下文，确保 `mq_message_record` 写入 `ry-vue` 库
- 消息携带：`receiveId` + `tenantIdStr`（字符串格式，避免 Long 转换丢失前导零）
- 消费者处理：`TenantHelper.dynamic()` 设置租户上下文 → 按物料ID/编码匹配 → 累加已收货数量 → 更新采购订单状态

---

## 七、前后端对应关系

| 业务 | 管理后台（plus-ui） | 移动端（Nexus_App） | API前缀 |
|------|-------------------|-------------------|---------|
| 供应商 | BD/Vendor 完整CRUD | 列表+新增+编辑 | `/erp/bd/vendor/` |
| 客户订单 | SO/CustomerOrder 完整CRUD | 列表+新增+编辑 | `/erp/so/customerOrder/` |
| 考勤打卡 | SCH/Attendance 管理端 | 打卡+排班+考勤日历 | `/app/sch/`（移动端专属） |
| 其他模块 | 全功能管理界面 | 暂未覆盖 | `/erp/` |

移动端使用 `/app/sch/` 前缀（专属接口），bd/so 模块与后端共用 `/erp/` 前缀接口。

---

## 八、后端模块详细清单

### 8.1 BD - 基础数据

| 实体类 | 数据库表 | 核心字段 |
|--------|---------|---------|
| `BdMaterial` | `bd_material` | materialCode, materialName, categoryId, uomIds, materialType, sourceType, isBatchManage, isSerialManage, safetyStock, minStock, maxStock, standardCost, referencePrice, shelfLife |
| `BdMaterialCategory` | `bd_material_category` | categoryId, parentId, levelNum, pathIds |
| `BdBom` | `bd_bom` | bomId, parentMaterialId, bomVersion, isCurrent, effectiveDate, expiryDate, sourceType |
| `BdBomDetail` | `bd_bom_detail` | childMaterialId, quantityPerParent, lossRate, operationStep, leadTime（工序/工位关联由 MES MBOM 管理） |
| `BdClient` | `bd_client` | clientCode, clientName, clientType, creditCode, address, contacts |
| `BdClientCredit` | `bd_client_credit` | 客户信用额度 |
| `BdVendor` | `bd_vendor` | vendorCode, vendorName, taxId, contactPerson, phone, bankAccount, industry, region, category |
| `BdUnitOfMeasure` | `bd_unit_of_measure` | uomCode, uomName, category, isBase, conversionRate |
| `BdWorkshop` | `bd_workshop` | workshopCode, workshopName, managerId, capacity |
| `BdWorkstation` | `bd_workstation` | workshopId, equipmentId, operatorCount, standardCapacity |
| `BdSerialNumberRule` | `bd_serial_number_rule` | ruleCode, prefix, dateFormat, seqLength, checkDigitRule |

### 8.2 SO - 销售管理

| 实体类 | 数据库表 | 核心字段 |
|--------|---------|---------|
| `SoQuotation` | `so_quotation` | quotationNo, clientId, salesRepId, quotationDate, validUntil, currency, totalAmount |
| `SoSalesOrderHeader` | `so_sales_order_header` | soNo, coId, clientId, orderDate, promiseDate, currency, totalAmount, creditStatus, orderStatus |
| `SoSalesOrderLine` | `so_sales_order_line` | materialId, orderedQty, deliveredQty, fulfilledQty, productionQty, purchaseQty, reservedQty, linkedMoId, linkedPoId |
| `SoCustomerOrder` | `so_customer_order` | coNo, clientId, orderDate, requestedDate, currency, totalAmount, priority, customerPoNo, sourceChannel |
| `SoReturn` | `so_return` | returnNo, soId, clientId, returnDate, returnReason, returnType, totalAmount |
| `SoPriceList` | `so_price_list` | listName, listType, clientId, effectiveDateFrom, effectiveDateTo |
| `SoPriceListLine` | `so_price_list_line` | materialId, unitPrice, minQty, maxQty, discountRate |
| `SoCreditUsage` | `so_credit_usage` | 信用额度使用记录 |

### 8.3 PU - 采购管理

| 实体类 | 数据库表 | 核心字段 |
|--------|---------|---------|
| `PuPurchaseRequisition` | `pu_purchase_requisition` | requisitionNo, applicantId, requisitionDate, requiredDate, sourceType, status |
| `PuPurchaseRequisitionLine` | `pu_purchase_requisition_line` | materialId, quantity, requiredDate |
| `PuPurchaseOrder` | `pu_purchase_order` | orderNo, vendorId, requisitionId, orderDate, expectedArrivalDate, status, currency, totalAmount |
| `PuPurchaseOrderLine` | `pu_purchase_order_line` | materialId, orderQuantity, receivedQuantity, unitPrice, taxRate, lineAmount |
| `PuPurchaseReturn` | `pu_purchase_return` | returnNo, orderId, vendorName, returnDate, returnReason, totalAmount |
| `PuVendorEvaluation` | `pu_vendor_evaluation` | vendorId, evalPeriod, deliveryRate, qualityRate, priceScore, totalScore, evalLevel |

### 8.4 PRD - 生产管理

| 实体类 | 数据库表 | 核心字段 |
|--------|---------|---------|
| `PrdProductionPlan` | `prd_production_plan` | planNo, planName, version, planType, planDate, startDate, endDate, status, priority, bomId, orderLineId |
| `PrdWorkOrder` | `prd_work_order` | woNo, planId, sourceType, sourceId, materialId, plannedQty, completedQty, scrapQty, woStatus, backflushFlag, issuedQty |
| `PrdMaterialRequirement` | `prd_material_requirement` | requirementNo, planId, bomId, totalMaterialTypes, totalRequiredQuantity |
| `PrdMaterialRequirementDetail` | `prd_material_requirement_detail` | materialId, requiredQuantity, availableQuantity, shortageQuantity |
| `PrdWorkReport` | `prd_work_report` | reportNo, woId, materialId, plannedQty, reportedQty, goodQty, scrapQty, laborHours, machineHours |
| `PrdReworkOrder` | `prd_rework_order` | reworkNo, sourceWoId, materialId, reworkQty, reworkReason, actualQty, scrapQty |
| `PrdSubcontract` | `prd_subcontract` | subcontractNo, vendorId, materialId, plannedQty, actualQty, unitPrice, totalAmount |
| `PrdCapacityCalendar` | `prd_capacity_calendar` | calendarDate, objectType, objectId, availableHours, plannedHours, actualHours, remainingHours |
| `PrdWorkOrderResource` | `prd_work_order_resource` | woId, resourceType, resourceId, planStartTime, planEndTime |

### 8.5 WM - 仓储管理

| 实体类 | 数据库表 | 核心字段 |
|--------|---------|---------|
| `WmWarehouse` | `wm_warehouse` | warehouseCode, warehouseName, location, managerId |
| `WmStorageLocation` | `wm_storage_location` | locationCode, locationName, areaId, maxLoa, 3D位置, occupancyRate |
| `WmStorageArea` | `wm_storage_area` | areaCode, areaName, warehouseId, 3D位置, layoutType |
| `WmMaterialStock` | `wm_material_stock` | materialId, batchId, warehouseId, areaId, locationId, stockQuantity, unitCost, totalCost, frozenFlag, version |
| `WmMaterialStockTx` | `wm_material_stock_tx` | 库存交易记录 |
| `WmMaterialReceive` | `wm_material_receive` | receiveCode, noticeId, purId, vendorId, warehouseId, receiveDate, status, shelfBy, shelfTime |
| `WmMaterialReceiveLine` | `wm_material_receive_line` | materialId, quantityReceived, batchId |
| `WmMaterialReceiveDetail` | `wm_material_receive_detail` | materialId, quantity, warehouseId, areaId, locationId |
| `WmArrivalNotice` | `wm_arrival_notice` | noticeCode, purOrderCode, vendorId, arrivalDate |
| `WmSalesIssue` | `wm_sales_issue` | issueCode, soId, clientId, warehouseId, issueDate, totalQuantity |
| `WmProductionIssue` | `wm_production_issue` | issueCode, woId, warehouseId, issueDate, issueType |
| `WmProductionRecpt` | `wm_production_recpt` | recptCode, woId, materialId, quantity, warehouseId |
| `WmTransfer` | `wm_transfer` | transferNo, fromWarehouseId, toWarehouseId, transferType, totalQuantity |
| `WmMiscRecpt` | `wm_misc_recpt` | recptCode, recptType, warehouseId, recptDate |
| `WmMiscIssue` | `wm_misc_issue` | issueCode, issueType, warehouseId, issueDate |
| `WmBatch` | `wm_batch` | batchCode, materialId, vendorId, warehouseId, productionDate, expiryDate, quantity |
| `WmSn` | `wm_sn` | snCode, materialId, batchId, warehouseId, stockId, status |
| `WmBarcode` | `wm_barcode` | barcodeCode, configId, bizType, bizId, materialId, batchId |
| `WmBarcodeConfig` | `wm_barcode_config` | configCode, prefix, datePattern, sequenceLength, withCheckDigit |
| `WmStockTakingPlan` | `wm_stock_taking_plan` | planCode, planName, warehouseId, plannedDate |
| `WmStockTakingLine` | `wm_stock_taking_line` | materialId, stockId, bookQuantity, actualQuantity, differenceQuantity |
| `WmStockReconciliation` | `wm_stock_reconciliation` | materialId, bookQty, txBalance, diffQty |
| `WmStockAlert` | `wm_stock_alert` | materialId, warehouseId, currentQty, safetyStock, alertType, alertLevel |
| `WmPickingRule` | `wm_picking_rule` | ruleCode, warehouseId, categoryId, strategyType |
| `WmPutawayRule` | `wm_putaway_rule` | ruleCode, warehouseId, categoryId, strategyType |

### 8.6 SCH - 排班考勤

| 实体类 | 说明 |
|--------|------|
| `SchPlan` | 排班计划 |
| `SchPlanTeam` | 排班计划-班组关联 |
| `SchShift` | 班次定义（startTime, endTime, isCrossDay, rest时段） |
| `SchTeam` | 班组 |
| `SchTeamMember` | 班组成员 |
| `SchTeamSchedule` | 班组排班 |
| `SchWorker` | 工人信息 |
| `SchEmployeeSchedule` | 员工排班 |
| `SchAttendance` | 考勤记录（clockInTime, clockOutTime, lateMinutes, overtimeHours） |
| `SchHoliday` | 假日日历 |
| `SchPunchRule` | 打卡规则（位置限制/WiFi/蓝牙/户外打卡/补卡） |

### 8.7 MQ - 消息队列

| 消息类 | 说明 |
|--------|------|
| `ProductionCompleteMessage` | 生产完工消息（woId, completionQty, materialId） |
| `PurchaseReceiveWritebackMessage` | 采购收货回写消息（receiveId, tenantIdStr） |
| `StockInMessage` | 入库消息（receiveId, sourceType, tenantId） |
| `ReworkStockMessage` | 返工库存消息（reworkId, operationType: ISSUE/RECEIVE） |
| `SubcontractStockMessage` | 委外库存消息（subcontractId, operationType: ISSUE/RECEIVE） |

| 消费者 | 队列 | 处理逻辑 |
|--------|------|---------|
| `ProductionBackflushConsumer` | `PRODUCTION_COMPLETE_QUEUE` | 生产完工→倒冲领料+成品入库过账 |
| `StockInConsumer` | `STOCK_IN_QUEUE` | 入库消息分发: PURCHASE→采购入库+发送回写消息 |
| `PurchaseReceiveWritebackConsumer` | `PURCHASE_RECEIVE_QUEUE` | 采购收货→设置租户上下文→按物料ID/编码匹配→回写采购订单已收货数量→更新采购订单状态 |
| `ReworkStockConsumer` | `REWORK_STOCK_QUEUE` | 返工库存→更新返工订单状态 |
| `SubcontractStockConsumer` | `SUBCONTRACT_STOCK_QUEUE` | 委外库存→更新委外订单状态 |

---

## 九、移动端业务分析

### 9.1 API接口

| 模块 | 函数名 | 接口URL | 说明 |
|------|--------|---------|------|
| **供应商** | `listVendor` | `GET /erp/bd/vendor/list` | 查询供应商列表 |
| | `getVendor` | `GET /erp/bd/vendor/{vendorId}` | 查询供应商详情 |
| | `addVendor` | `POST /erp/bd/vendor` | 新增供应商 |
| | `updateVendor` | `PUT /erp/bd/vendor` | 修改供应商 |
| | `delVendor` | `DELETE /erp/bd/vendor/{vendorId}` | 删除供应商 |
| **客户订单** | `listCustomerOrder` | `GET /erp/so/customerOrder/list` | 查询客户订单列表 |
| | `getCustomerOrder` | `GET /erp/so/customerOrder/{coId}` | 查询客户订单详情 |
| | `addCustomerOrder` | `POST /erp/so/customerOrder` | 新增客户订单 |
| | `updateCustomerOrder` | `PUT /erp/so/customerOrder` | 修改客户订单 |
| | `delCustomerOrder` | `DELETE /erp/so/customerOrder/{coId}` | 删除客户订单 |
| **考勤** | `getTodaySchedule` | `GET /app/sch/employee-schedule/today` | 获取今日排班 |
| | `getTodayAttendanceStatus` | `GET /app/sch/attendance/today-status` | 获取今日打卡状态 |
| | `clockIn` | `POST /app/sch/attendance/clock-in` | 上班打卡 |
| | `clockOut` | `POST /app/sch/attendance/clock-out` | 下班打卡 |
| | `getMonthlyAttendance` | `GET /app/sch/attendance/monthly/{month}` | 月度考勤统计 |
| | `getMySchedule` | `GET /app/sch/employee-schedule/my-schedule` | 我的排班列表 |
| | `validateLocation` | `POST /app/sch/attendance/validate-location` | 验证位置范围 |
| | `getPunchRules` | `GET /app/sch/punch-rule/list` | 获取打卡规则 |
| | `applySupplement` | `POST /app/sch/supplement-apply` | 提交补卡申请 |

### 9.2 移动端页面

| 页面 | 路由 | 功能 |
|------|------|------|
| 供应商列表 | `pages/bd/vendor/uni-index` | 搜索、筛选、分页、CRUD |
| 客户订单列表 | `pages/so/customerOrder/uni-index` | 搜索、筛选、分页、CRUD |
| 考勤主页 | `pages/sch/attendance/index` | 今日状态、快捷入口 |
| 打卡页 | `pages/sch/attendance/punch-card` | GPS定位、位置验证、上下班打卡 |
| 我的考勤 | `pages/sch/attendance/my-attendance` | 月度日历视图 |
| 我的排班 | `pages/sch/attendance/my-schedule` | 排班列表 |

---

## 十、技术特点

1. **多租户支持**: 所有实体继承 `TenantEntity`，支持SaaS多租户隔离
2. **审批流集成**: 采购/销售/生产/仓储单据均集成 Flowable 工作流
3. **异步解耦**: 通过 RabbitMQ 实现生产倒冲、采购回写等异步操作；采购入库回写通过 `WmMqSendService`（@Async）发送，事务提交后触发
4. **策略模式**: 仓储模块使用策略模式处理12种不同类型的库存操作
5. **乐观锁**: 库存表使用 `@Version` 防止并发冲突
6. **权限控制**: 通过 Sa-Token `@SaCheckPermission` 实现细粒度权限
7. **操作日志**: 全部写操作通过 `@Log` 注解记录
8. **BOM管理**: 支持多版本BOM、树形展开、缩进式展示、循环引用校验
9. **信用控制**: 销售模块集成信用检查和信用额度管理
10. **3D可视化**: 库区和库位支持3D位置/尺寸/旋转/颜色配置
