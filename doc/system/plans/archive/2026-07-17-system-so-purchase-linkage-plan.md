# SO 销售订单 → 采购联动开发计划（purchase_qty / linked_po_id 补全）

> 日期：2026-07-17
> 状态：📋 待开发（独立较大功能）
> 来源：`so_sales_order_line` 字段赋值优化（line_status 字典一致性优化）衍生的已知缺口
> 关联记忆：`so-line-purchase-fields-gap.md`
> 关联文档：`../so-line-linkage-analysis.md`（生产→出库联动）、`../mes-completion-linkage-analysis.md`

---

## 一、背景

在 2026-07-17 的 `so_sales_order_line` 字段赋值审查中确认：`purchase_qty`（已采购数量）、`linked_po_id`（关联采购订单ID）两个字段**在全模块从未被赋值**（`setPurchaseQty` / `setLinkedPoId` 全仓零匹配）。当前仅作为表结构占位，无任何业务链路写入或读取。

本次 line_status 优化已闭合状态字段缺口，但 `purchase_qty` / `linked_po_id` 的补全因缺乏销售→采购业务联动链路，被明确列为后续独立功能。本文档为其开发需求与设计方案，供下次开发直接上手。

## 二、现状与根因

### 2.1 字段从未赋值

| 字段 | DDL 注释 | 现状 |
|------|----------|------|
| `purchase_qty` | 已采购数量 | ✗ 从未赋值（`setPurchaseQty` 零匹配） |
| `linked_po_id` | 关联采购订单ID | ✗ 从未赋值（`setLinkedPoId` 零匹配） |

对比同表其他数量字段均有回写链路：`production_qty`（完工回写）、`delivered_qty`/`fulfilled_qty`（出库回写）、`invoiced_qty`（开票/红冲/作废回写）、`reserved_qty`（库存预留/释放回写）。

### 2.2 根因一：销售确认不处理外购物料

`SoSalesOrderHeaderServiceImpl.confirm()` 销售订单确认时，只对**自制物料**自动创建生产计划：

```java
// SoSalesOrderHeaderServiceImpl.createProductionPlansFromSalesOrder()
if (material != null && "PROCESS".equals(material.getSourceType())) {
    createProductionPlan(line, soNo, material);  // 仅自制物料建生产计划
}
```

`BdMaterial.sourceType` 取值（`bd_material` 表）：

| sourceType | 含义 | 销售确认应触发的联动 | 现状 |
|------------|------|----------------------|------|
| `PROCESS` | 加工/自制 | 建生产计划（PrdProductionPlan） | ✅ 已实现 |
| `PROCUREMENT` | 采购 | 建采购申请（PuPurchaseRequisition） | ✗ **缺失（本功能）** |
| `SUBCONTRACT` | 委外 | 建委外订单（PrdSubcontract） | ✗ 缺失（另议） |
| `PHANTOM` | 虚拟件 | 不入库，并入子件需求 | ✗ 缺失（另议） |
| `CFM` | 客户供料 | 不采购 | — 无需处理 |
| `TRANSFER` | 调拨 | 库存调拨 | ✗ 缺失（另议） |

本功能聚焦 `PROCUREMENT`（外购物料）的采购联动。

### 2.3 根因二：采购单据行无销售行关联字段

- `PuPurchaseOrderLine`（采购订单行）：无 `soLineId` / `orderLineId` / `salesOrderLineId` 等销售行关联字段（grep 零匹配）。
- `PuPurchaseRequisitionLine`（采购申请行）：已有 `sourceType` + `sourceId`（来源单据机制），**可复用**关联销售行（`sourceType='SALES_ORDER'`，`sourceId=soLineId`），或新增专用 `so_line_id` 字段以更明确。

采购与销售订单行之间当前无任何直接关联链路，导致采购收货后无法回写对应 SO 行的 `purchase_qty`。

### 2.4 可复用的现有机制

- 采购收货已有 MQ 回写机制：`QueueConstants.PURCHASE_RECEIVE_QUEUE`（`erp.purchase.receive.queue`）+ `PuPurchaseReceiveWritebackServiceImpl`（当前回写采购订单行 `receiveStatus`）。**可扩展**为同时回写 `so_sales_order_line.purchase_qty`。
- 采购申请→采购订单的转换流程已存在（pu 模块）。

## 三、目标

1. 销售订单确认时，对 `sourceType=PROCUREMENT` 的物料行自动创建采购申请，并建立采购申请行→销售订单行的关联。
2. 采购申请转采购订单后，回写 `so_sales_order_line.linked_po_id`（指向采购订单ID）。
3. 采购收货时，经 MQ 回写 `so_sales_order_line.purchase_qty`（累加已收货数量）。
4. （可选）line_status 联动：采购到货后推进 SO 行状态（当前状态机 `PENDING→SCHEDULED→PARTIALLY_FULFILLED→FULFILLED`，可考虑采购到货即 `SCHEDULED`，与自制物料完工后 `SCHEDULED` 语义对齐）。

## 四、设计方案

### 4.1 数据模型：建立采购行→销售行关联

**方案 A（推荐，复用现有字段）**：`PuPurchaseRequisitionLine.sourceType='SALES_ORDER'`，`sourceId=soLineId`。不新增字段，沿用采购申请既有的"来源单据"机制。采购申请转订单时，将关联透传到 `PuPurchaseOrderLine`（需在订单行上也补 `sourceType`/`sourceId` 或新增 `so_line_id`，因为当前订单行无此字段）。

**方案 B（更明确）**：在 `PuPurchaseRequisitionLine` 和 `PuPurchaseOrderLine` 各新增 `so_line_id`（bigint）字段，专指销售订单行ID。语义清晰，便于反查，但需改表结构。

> 决策点：A vs B。建议 B（专字段更清晰，避免与"MRP运算生成"等其它 sourceType 混淆）。

### 4.2 销售确认联动（confirm 扩展）

`SoSalesOrderHeaderServiceImpl.createProductionPlansFromSalesOrder()` 旁新增 `createPurchaseRequisitionsFromSalesOrder()`，在 `confirm()` 中并行调用：

```
for each SO line:
    material = bdMaterialMapper.selectById(line.materialId)
    if "PROCUREMENT".equals(material.sourceType):
        创建 PuPurchaseRequisition（头：来源 sourceType='SALES_ORDER', sourceId=soId）
        创建 PuPurchaseRequisitionLine（行：sourceId=soLineId, 物料/数量=SO行, 需求日期=SO行承诺交期）
        回写 so_sales_order_line.linked_po_id = requisition.requisitionId（暂指向申请；转订单后更新为订单ID）
        或新增 linked_requisition_id 字段区分申请/订单
```

> 决策点：`linked_po_id` 语义是"采购订单"还是泛指"采购单据"？若严格指订单，则在采购申请转订单时再回写 `linked_po_id`，申请阶段用新字段 `linked_pr_id`。

### 4.3 采购收货回写 purchase_qty

扩展 `PuPurchaseReceiveWritebackServiceImpl`（或新增 `SoPurchaseReceiveWritebackConsumer` 监听同一队列）：

```
采购收货完成 -> 发 PURCHASE_RECEIVE_QUEUE 消息（含采购订单行ID、收货数量）
  -> 消费者：采购订单行 -> so_line_id（按 4.1 关联） -> so_sales_order_line
  -> setSql("purchase_qty = purchase_qty + ?", receivedQty) 原子累加
  -> （可选）推进 line_status
```

复用 `ISoFulfillmentWritebackService` 的模式（原子累加 + 状态推进），可新增 `addPurchasedQty(soLineId, qty)` 方法，与 `addProducedQty` / `addShippedQty` 对称。

### 4.4 line_status 联动（可选）

当前 `promoteToScheduled` 仅由 `production_qty >= ordered_qty` 触发。采购场景下，外购物料无 `production_qty`，应改为 `production_qty + purchase_qty >= ordered_qty` 触发 `SCHEDULED`。需调整 `SoFulfillmentWritebackServiceImpl.promoteToScheduled()` 的阈值判定。

## 五、涉及改动点清单

| 层 | 文件/对象 | 改动 |
|----|-----------|------|
| DB | `pu_purchase_requisition_line` | （方案B）新增 `so_line_id` |
| DB | `pu_purchase_order_line` | （方案B）新增 `so_line_id` |
| DB | `so_sales_order_line` | （可选）新增 `linked_pr_id` 区分采购申请/订单 |
| 后端 | `SoSalesOrderHeaderServiceImpl.confirm()` | 调用新建 `createPurchaseRequisitionsFromSalesOrder()` |
| 后端 | `SoSalesOrderHeaderServiceImpl` | 新增 `createPurchaseRequisitionsFromSalesOrder()`（PROCUREMENT 物料建采购申请） |
| 后端 | `ISoFulfillmentWritebackService` | 新增 `addPurchasedQty(soLineId, qty)` |
| 后端 | `SoFulfillmentWritebackServiceImpl` | 实现 `addPurchasedQty` + 调整 `promoteToScheduled` 阈值（production_qty + purchase_qty） |
| 后端 | `PuPurchaseReceiveWritebackServiceImpl` 或新 Consumer | 采购收货回写 `purchase_qty` |
| 后端 | 采购申请转订单逻辑 | 转订单时透传 `so_line_id` 并回写 `linked_po_id` |
| 前端 | `so/orderheader/form.vue` | 子表 `purchase_qty` 列已展示（只读），`linked_po_id` 可改为展示采购单号（仿 linkedWoNo 回填） |
| SQL | 增量脚本 | 表结构变更 + 字典（若有） |

## 六、验证

1. 建 SO 含 PROCUREMENT 物料行 → 确认 → 应自动生成采购申请，`linked_po_id`（或 `linked_pr_id`）回写。
2. 采购申请转采购订单 → `linked_po_id` 更新为采购订单ID。
3. 采购收货 → `purchase_qty` 累加；收齐后 `line_status` 推进 `SCHEDULED`。
4. 后续销售出库 → `delivered_qty` 累加 → `FULFILLED`（与既有出库链路衔接）。
5. 编译：`mvn -pl ruoyi-modules/ruoyi-erp -am compile`；前端 `eslint`。

## 七、待决策问题

1. **关联字段方案**：复用 `sourceType/sourceId`（方案A）还是新增 `so_line_id`（方案B）？→ 建议 B。
2. **`linked_po_id` 语义**：严格指采购订单，还是泛指采购单据（含申请）？若严格，需新增 `linked_pr_id`。
3. **line_status 阈值**：`SCHEDULED` 改为 `production_qty + purchase_qty >= ordered_qty`，是否影响既有自制物料流程？（应兼容，因自制物料 purchase_qty=0）。
4. **委外（SUBCONTRACT）/调拨（TRANSFER）物料**：是否纳入本功能或另立？
5. **采购数量与订单数量单位一致性问题**：采购单位（`purchaseUomId`）可能与销售单位不同，回写 `purchase_qty` 时是否需单位换算？

## 八、关联

- 记忆：`so-line-purchase-fields-gap.md`（根因速查）
- 联动分析：`../so-line-linkage-analysis.md`（生产→出库联动，本功能为其采购侧对偶）
- MES 完工联动：`../mes-completion-linkage-analysis.md`
- 开票回写：`invoicing-ar-ap-redesign`（`invoiced_qty` 回写链路，模式可参考）
