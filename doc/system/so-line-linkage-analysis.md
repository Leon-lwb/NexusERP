# SO 销售订单行联动缺失分析与设计方案

> 日期：2026-07-08
> 前提：基于 MES 工单完工跨模块联动分析（`doc/system/mes-completion-linkage-analysis.md`）

---

## 一、背景

在 MES 工单完工联动分析中发现：生产完工后，`so_sales_order_line.production_qty` 已正确回写，但该字段是**只写字段**，没有任何业务逻辑读取它来驱动后续流程。从生产完工到销售出库之间存在断点。

## 二、现状

### 已实现的功能（来自 2026-07-07 计划）

| 改动 | 内容 | 状态 |
|------|------|------|
| 改动 1 | `ISoFulfillmentWritebackService` 回写公共入口 | ✅ |
| 改动 2 | `SalesOutStrategy` 出库回写 delivered_qty | ✅ |
| 改动 3 | `WmSalesIssueServiceImpl` 出库回写 delivered_qty | ✅ |
| 改动 4 | `PrdWorkOrderServiceImpl` 完工回写 production_qty | ✅ |
| 改动 5 | 信用三态化 FROZEN/USED/RELEASED | ✅ |
| 改动 6 | `fulfill()` 重写 + 严格门禁 + 可强制 | ✅ |
| 改动 7 | Controller + 前端 force 流 | ✅ |

### 仍然缺失的环节

```
SO 确认 → 生产计划 → 工单 → 完工 → production_qty++ (写入死字段)
  → 成品入库 (货物在仓库)
  → [断点] 没有任何机制触发销售出库
  → 需要人工手动创建 WmSalesIssue → 出库 → delivered_qty++
  → fulfill() 检查 delivered_qty >= ordered_qty → 履行
```

## 三、根因分析

### 3.1 `production_qty` 是只写字段

全仓搜索确认，`production_qty` 只在 `SoFulfillmentWritebackServiceImpl.addProducedQty()` 中被写入。没有任何 Mapper、Service、Controller 或 Consumer 读取它来做业务决策。

### 3.2 SO 行无中间状态

`so_sales_order_line.lineStatus` 只有两个值：
- `null`（默认，未履行）
- `"FULFILLED"`（已履行，由 `fulfill()` 方法设置）

没有 `"PRODUCED"`、`"READY_TO_DELIVER"`、`"PARTIALLY_DELIVERED"` 等中间状态来驱动下游流程。

### 3.3 `fulfill()` 门禁只看 `delivered_qty`

`SoSalesOrderHeaderServiceImpl.fulfill()` 第 258-272 行：
```java
// 严格门禁：所有行 delivered_qty >= ordered_qty
BigDecimal done = line.getDeliveredQty();  // 只检查 delivered_qty
if (need.subtract(done).compareTo(tolerance) > 0) {
    shortLines.add(...);
}
```

完全不考虑 `production_qty`。即使货物已生产入库，只要没有创建销售出库单，就无法履行。

### 3.4 `SalesOrderFulfilledEvent` 无订阅者

`SalesOrderFulfilledEvent` 已发布，但全仓搜索 `@EventListener` 和 `ApplicationListener` 返回零结果。

### 3.5 无"生产完工→自动出库"链路

`ProductionBackflushConsumer` 在做完倒冲+入库后就结束了，没有检查 SO 行是否需要自动创建销售出库单。

## 四、设计方案

### 方案 A：SO 行状态机驱动（推荐）

**核心思路：** 让 `production_qty` 驱动 SO 行状态变更，状态变更触发自动创建销售出库单。

**新增状态：**
```
SO 行状态流转：
  null (默认)
    → PRODUCED (已生产：production_qty >= ordered_qty)
    → PARTIALLY_DELIVERED (部分发货：0 < delivered_qty < ordered_qty)
    → DELIVERED (已发货：delivered_qty >= ordered_qty)
    → FULFILLED (已履行：fulfill() 执行后)
```

**实现步骤：**

**步骤 1：在 `addProducedQty()` 中增加行状态更新**

文件：`SoFulfillmentWritebackServiceImpl.java`

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void addProducedQty(Long soLineId, BigDecimal qty) {
    // ... 现有 production_qty 累加逻辑 ...
    
    // 检查是否已完全生产，更新行状态
    SoSalesOrderLine line = soSalesOrderLineMapper.selectById(soLineId);
    if (line != null && line.getOrderedQty() != null) {
        BigDecimal newProductionQty = line.getProductionQty() != null 
            ? line.getProductionQty().add(qty) : qty;
        if (newProductionQty.compareTo(line.getOrderedQty()) >= 0) {
            soSalesOrderLineMapper.update(null,
                Wrappers.<SoSalesOrderLine>lambdaUpdate()
                    .eq(SoSalesOrderLine::getLineId, soLineId)
                    .set(SoSalesOrderLine::getLineStatus, "PRODUCED"));
            // 发布事件供下游订阅
            applicationEventPublisher.publishEvent(
                new SoLineProducedEvent(this, line.getSoId(), soLineId, 
                    line.getSoNo(), newProductionQty));
        }
    }
}
```

**步骤 2：新增 `SoLineProducedEvent`**

文件：`so/event/SoLineProducedEvent.java`（新文件）

```java
public class SoLineProducedEvent extends ApplicationEvent {
    private final Long soId;
    private final Long lineId;
    private final String soNo;
    private final BigDecimal producedQty;
}
```

**步骤 3：新增 `SoLineProducedListener` 事件监听器（可选，按业务配置启用）**

文件：`so/listener/SoLineProducedListener.java`（新文件）

- 监听 `SoLineProducedEvent`
- 检查 SO 配置是否启用"自动出库"（可通过系统参数 `so.auto_delivery_on_produced` 控制）
- 若是，自动创建 `WmSalesIssue` 销售出库单（草稿状态）
- 若否，仅记录日志

**步骤 4：在 `addShippedQty()` 中增加行状态更新**

文件：`SoFulfillmentWritebackServiceImpl.java`

```java
// 更新 delivered_qty 后，检查是否已完全发货
line = soSalesOrderLineMapper.selectById(soLineId);
if (line != null && line.getOrderedQty() != null 
    && line.getDeliveredQty().compareTo(line.getOrderedQty()) >= 0) {
    soSalesOrderLineMapper.update(null,
        Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .set(SoSalesOrderLine::getLineStatus, "DELIVERED"));
}
```

### 方案 B：`fulfill()` 门禁扩展（简化版，不推荐）

**核心思路：** 在 `fulfill()` 门禁中增加对 `production_qty` 的检查。

**风险：** 允许货物还在仓库就"履行"订单，可能导致账实不符。

### 方案 C：`ProductionBackflushConsumer` 增强（即时方案）

**核心思路：** 在 `ProductionBackflushConsumer` 做完入库后，检查 SO 行并自动创建销售出库单。

**风险：** 与 MQ 消费者耦合，逻辑复杂，测试困难。

## 五、推荐实施路径

### 第一阶段（低风险，推荐立即实施）

方案 A 的步骤 1+2+4：
- 让 `production_qty` 驱动行状态变更
- 发布事件供未来扩展
- 不改动出库流程
- 影响范围：仅 `SoFulfillmentWritebackServiceImpl` + 1 个新事件类

### 第二阶段（可选，按需实施）

方案 A 的步骤 3：
- 基于业务需求决定是否启用自动创建销售出库单
- 默认关闭，通过系统参数 `so.auto_delivery_on_produced` 控制
- 需要新增 `WmSalesIssue` 创建逻辑

### 第三阶段（远期）

方案 C：
- 在成品入库后立即触发自动出库
- 需要完善出库单自动审批流程
- 需要端到端测试覆盖

## 六、文件改动清单

| 阶段 | 文件 | 类型 | 说明 |
|------|------|------|------|
| 一 | `so/service/impl/SoFulfillmentWritebackServiceImpl.java` | 改 | addProducedQty/addShippedQty 增加行状态更新 |
| 一 | `so/event/SoLineProducedEvent.java` | 新 | SO 行生产完成事件 |
| 二 | `so/listener/SoLineProducedListener.java` | 新 | 自动出库监听器（可选） |
| 二 | `wm/service/IWmSalesIssueService.java` | 改 | 新增自动创建销售出库单方法 |
| 二 | `sys_config` 表 | 数据 | 新增 `so.auto_delivery_on_produced` 参数 |