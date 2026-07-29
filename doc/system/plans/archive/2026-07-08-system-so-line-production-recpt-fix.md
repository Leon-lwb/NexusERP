# SO 销售订单行联动与生产入库单据修复方案

> 日期：2026-07-08
> 范围：ERP（so / wm / mq）
> 关联：基于 `doc/system/so-line-linkage-analysis.md` 的方案 A 实施

---

## 一、修复 1：SO 行状态机驱动（方案 A 阶段一）

### 1.1 问题

`so_sales_order_line.production_qty` 是只写字段，生产完工回写后无任何业务逻辑读取它驱动下游流程。从生产完工到销售出库之间存在断点，SO 行缺少中间状态。

### 1.2 方案

让 `production_qty` / `delivered_qty` 驱动 SO 行状态自动推进：

```
SO 行状态流转：
  null (默认)
    -> PRODUCED (已生产：production_qty >= ordered_qty)
    -> DELIVERED (已发货：delivered_qty >= ordered_qty)
    -> FULFILLED (已履行：fulfill() 执行后)
```

### 1.3 实现

**新文件：** `so/event/SoLineProducedEvent.java`
- SO 行生产完成事件，携带 soId / lineId / materialCode / producedQty
- 供下游订阅（如未来自动创建销售出库单）

**修改：** `so/service/impl/SoFulfillmentWritebackServiceImpl.java`

| 方法 | 改动 |
|------|------|
| `addProducedQty()` | 累加 production_qty 后，检查是否 `>= ordered_qty`，若是则条件更新 lineStatus=PRODUCED 并发布 SoLineProducedEvent |
| `addShippedQty()` | 累加 delivered_qty 后，检查是否 `>= ordered_qty`，若是则条件更新 lineStatus=DELIVERED |

**并发安全：**
- 数量累加用 `setSql("production_qty = production_qty + ?")` 原子操作，规避读后写冲突
- 状态推进用条件更新 `WHERE production_qty >= ordered_qty AND (line_status IS NULL OR line_status NOT IN (...))`，防御并发累加下的状态丢失
- 终态保护：已处于 DELIVERED/FULFILLED 的行不会被回退为 PRODUCED

**注入：** `ApplicationEventPublisher`（用于发布事件）

### 1.4 状态推进逻辑

```
addProducedQty(soLineId, qty):
  1. setSql: production_qty += qty              // 原子累加
  2. select line                                 // 读取当前值
  3. if production_qty >= ordered_qty
       AND lineStatus NOT IN (PRODUCED, DELIVERED, FULFILLED):
       UPDATE SET lineStatus=PRODUCED
         WHERE line_id=? AND production_qty >= ordered_qty
         AND (line_status IS NULL OR line_status NOT IN (...))
     if rows > 0: publish SoLineProducedEvent

addShippedQty(soLineId, qty):
  1. setSql: delivered_qty += qty, fulfilled_qty += qty
  2. select line
  3. if delivered_qty >= ordered_qty
       AND lineStatus NOT IN (DELIVERED, FULFILLED):
       UPDATE SET lineStatus=DELIVERED
```

---

## 二、修复 2：生产入库单据缺失漏洞

### 2.1 问题

MES 工单完工后，`ProductionBackflushConsumer` 直接调用 `stockOperationService.postProductionCompletion()` 更新库存，**没有生成 `wm_production_recpt`（生产入库单）单据**，导致：

- 流程单据缺失：库存有变动但无可追溯的入库单据
- 审计断链：无法从单据维度回查某次完工入库的仓库/库位/数量
- 与手动入库流程不一致：手动入库走 `WmProductionRecpt.execute()`，自动入库却绕过单据

### 2.2 方案

在完工自动入库链路中，先生成生产入库单据，再过账入库。一次调用内完成"单据生成 + 库存过账"。

### 2.3 实现

**修改：** `wm/service/IWmProductionRecptService.java`
- 新增 `createAndExecute(...)` 方法签名

**修改：** `wm/service/impl/WmProductionRecptServiceImpl.java`
- 实现 `createAndExecute()`：
  1. 构建 `WmProductionRecpt`（自动生成单号 SCRK+日期+流水，状态 DRAFT）
  2. 填充完整仓库/库位信息（code + name，不再只有 ID）
  3. 调用 `postProductionCompletion()` 过账入库
  4. 单据状态 -> EXECUTED
  5. 返回单据 ID

**修改：** `mq/consumer/ProductionBackflushConsumer.java`
- 移除 `IWmStockOperationService` 直接依赖
- 注入 `IWmProductionRecptService`
- `doProcess()` 中用 `productionRecptService.createAndExecute(...)` 替换直接 `postProductionCompletion()`

### 2.4 幂等保障（三层）

| 层级 | 机制 | 作用 |
|------|------|------|
| MQ 消息层 | `BaseMessageConsumer` Redis messageId 去重（24h TTL） | 同一消息不重复消费 |
| 库存过账层 | `ProductionCompletionStrategy.hasPosted(woId)` | 同一工单不重复入库 |
| 单据层 | 单号唯一 + 状态机（DRAFT->EXECUTED） | 单据可追溯 |

### 2.5 改动前后对比

**改动前：**
```
ProductionBackflushConsumer.doProcess()
  ├─ backflushService.backflushOnCompletion()   // 倒冲领料
  └─ stockOperationService.postProductionCompletion()  // 直接改库存，无单据
```

**改动后：**
```
ProductionBackflushConsumer.doProcess()
  ├─ backflushService.backflushOnCompletion()   // 倒冲领料
  └─ productionRecptService.createAndExecute()  // 生成入库单 + 过账
       ├─ INSERT wm_production_recpt (DRAFT)     // 单据留痕
       ├─ stockOperationService.postProductionCompletion()  // 库存过账
       └─ UPDATE wm_production_recpt SET status=EXECUTED    // 单据完结
```

---

## 三、文件改动清单

| # | 类型 | 文件 | 说明 |
|---|------|------|------|
| 1 | 新 | `so/event/SoLineProducedEvent.java` | SO 行生产完成事件 |
| 2 | 改 | `so/service/impl/SoFulfillmentWritebackServiceImpl.java` | 状态机驱动 + 事件发布 |
| 3 | 改 | `wm/service/IWmProductionRecptService.java` | 新增 createAndExecute 接口 |
| 4 | 改 | `wm/service/impl/WmProductionRecptServiceImpl.java` | 实现 createAndExecute |
| 5 | 改 | `mq/consumer/ProductionBackflushConsumer.java` | 改用单据驱动入库 |

---

## 四、验证要点

### 4.1 SO 行状态机
- 生产完工回写后，`production_qty >= ordered_qty` 时 `line_status` 自动变为 `PRODUCED`
- 销售出库回写后，`delivered_qty >= ordered_qty` 时 `line_status` 自动变为 `DELIVERED`
- `fulfill()` 执行后 `line_status` 变为 `FULFILLED`（终态）
- 并发完工不会丢失状态推进（条件更新防御）

### 4.2 生产入库单据
- 完工后 `wm_production_recpt` 表新增一条记录，状态 `EXECUTED`
- 单据含完整仓库/库位信息（code + name）
- `wm_material_stock_tx` 流水正常生成（PRODUCTION_IN）
- 库存数量正确增加
- 重复消费不会重复入库（hasPosted 幂等）

---

## 五、后续扩展（未实施）

1. **SoLineProducedEvent 监听器**：基于业务需求决定是否启用自动创建销售出库单（默认关闭，通过系统参数 `so.auto_delivery_on_produced` 控制）。监听者须用 `@TransactionalEventListener(phase = AFTER_COMMIT)`，避免事务回滚后事件已送达的不一致
2. **库存记录仓库/库位名称补全**：`ProductionCompletionStrategy.findOrCreateStock()` 当前只设 ID，可扩展为传入完整名称（需调整 `postProductionCompletion` 签名）
3. **partial completion 支持**：当前 `hasPosted` 限制每工单仅一次入库，如需支持分批完工入库需调整幂等策略

---

## 六、代码审查加固（2026-07-08）

对前述修复进行对抗性审查后发现并修复 4 个漏洞，详见 `doc/system/code-review-report.md`。

### 6.1 SO 行状态推进并发丢失【已修复】

`promoteToProduced` / `promoteToDelivered` 原含内存级 `productionQty < orderedQty` 提前返回。REPEATABLE READ 下 SELECT 读快照，并发累加时可能误判跳过状态推进。

**修复：** 移除内存级数量提前返回，始终执行条件 UPDATE，由 DB 级 `ge(production_qty, ordered_qty)` 权威判定（UPDATE 获取行锁，见最新已提交值）。

### 6.2 生产入库单重复创建【已修复】

`createAndExecute` 无单据级幂等，MQ 重投（Redis 24h TTL 后）产生重复单据。

**修复：** 创建前查同 woId 的 EXECUTED 入库单，存在则直接返回。形成"单据级 + 库存级"双重幂等。

### 6.3 物料需求单完工判定依赖脏数据【已修复】

原按 `actual_consumed_qty >= required_quantity` 判定，required_quantity 脏数据（实测 4.0 vs 实际 1.53）导致永不完工。

**修复：** 改为"计划完工驱动"——`plan.status == COMPLETED` 时完工其下所有需求单。时机正确：`complete()` 先于 backflush 执行，plan COMPLETED 已提交可见。注入 `PrdProductionPlanMapper` 查询计划状态。

### 6.4 倒冲明细累加读后写丢失【已修复】

`detailMapper.updateById(detail)` 读-改-写，并发倒冲丢更新。

**修复：** 改为原子 `setSql("actual_consumed_qty = actual_consumed_qty + ?, issued_quantity = issued_quantity + ?")`。

### 6.5 已记录的可接受限制

| 限制 | 影响 | 缓解 |
|------|------|------|
| 计划完工并发判定（REPEATABLE READ 快照） | 低，MQ 顺序消费不触发 | 下次完工修正 |
| 事件事务内发布 | 当前无监听者 | 未来用 `@TransactionalEventListener(AFTER_COMMIT)` |
| createAndExecute TOCTOU 窗口 | 极低，MQ 顺序消费 | hasPosted 保库存不重复 |
| 自动入库单 materialSpec 为空 | 低，有 code/name 标识 | 可选字段 |