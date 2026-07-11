# 销售订单"履行"按钮跨模块联动 — 实施计划

> 日期：2026-07-07
> 范围：ERP（so / wm / prd）+ 前端 orderheader
> 决策：**全套落地**（回写 + fulfill 重写 + 信用三态）；**严格门禁 + 可强制**

## 一、背景与现状缺口

`fulfill()` 当前仅 `CONFIRMED → FULFILLED` + 设 `fulfilledTime`，无任何联动（`SoSalesOrderHeaderServiceImpl.java:220-231`）。

根因：回写链路断裂，执行数据流不回销售订单行。

| 缺口 | 证据 |
|------|------|
| `so_sales_order_line.fulfilled_qty` / `delivered_qty` / `production_qty` 是死字段 | 全仓库无 `set` 调用；SQL 注释"已履行数量（已发货）" |
| 销售出库不回写 SO 行 | `SalesOutStrategy.java:156-166` 只递减 `reserved_qty`；`WmSalesIssueServiceImpl.completeSalesIssue()` 连预留都不碰 |
| MES 工单完工不回写 SO 行 | `PrdWorkOrderServiceImpl.complete()`（490-538）只更新工单 + 发倒冲消息 |
| 信用只有 `FROZEN`/`RELEASED` | `SoCreditUsage.java:41`；`CreditCheckServiceImpl` 无"已交付未付款"态 |

## 二、实施范围（7 类改动）

### 改动 1：新增回写公共入口 `ISoFulfillmentWritebackService`

**新文件** `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/service/ISoFulfillmentWritebackService.java`
**新文件** `.../so/service/impl/SoFulfillmentWritebackServiceImpl.java`

```java
public interface ISoFulfillmentWritebackService {
    /** 销售出库回写：delivered_qty += qty, fulfilled_qty += qty（原子 setSql） */
    void addShippedQty(Long soLineId, BigDecimal qty);
    /** 工单完工回写：production_qty += qty */
    void addProducedQty(Long soLineId, BigDecimal qty);
}
```

实现用 `LambdaUpdateWrapper.setSql("delivered_qty = delivered_qty + " + qty.toPlainString())` 原子累加，`@DS("erp")`、`@Transactional`。`soLineId` 为空或 `qty<=0` 直接 return。

### 改动 2（W1）：`SalesOutStrategy` 出库回写

**文件** `wm/strategy/impl/SalesOutStrategy.java`
- 构造函数追加注入 `ISoFulfillmentWritebackService fulfillmentWritebackService`
- 在第 166 行（`reserved_qty` 递减块）之后，对每个 `line` 追加：

```java
if (soLine != null && line.getQuantityIssue() != null) {
    fulfillmentWritebackService.addShippedQty(soLine.getLineId(), line.getQuantityIssue());
}
```

### 改动 3（W1）：`WmSalesIssueServiceImpl.completeSalesIssue` 出库回写

**文件** `wm/service/impl/WmSalesIssueServiceImpl.java`
- 注入 `ISoFulfillmentWritebackService`
- 在第 241 行（每行 FIFO 循环结束）后，行级追加：

```java
if (line.getSourceDocLineId() != null && issueQty.compareTo(BigDecimal.ZERO) > 0) {
    fulfillmentWritebackService.addShippedQty(line.getSourceDocLineId(), issueQty);
}
```

> `sourceDocLineId` 可能为空（非 SO 来源出库），必须判空。

### 改动 4（W2）：`PrdWorkOrderServiceImpl.complete` 完工回写

**文件** `prd/service/impl/PrdWorkOrderServiceImpl.java`（490-538 行 `complete` 方法）
- 注入 `ISoFulfillmentWritebackService` 与 `PrdProductionPlanMapper`
- 在第 520 行（工单更新成功后、发倒冲消息前）追加：

```java
// 回写销售订单行已投产数量：wo.planId → plan.orderLineId → SO 行
if (wo.getPlanId() != null && completeQty != null && completeQty.compareTo(BigDecimal.ZERO) > 0) {
    PrdProductionPlan plan = prdProductionPlanMapper.selectById(wo.getPlanId());
    if (plan != null && plan.getOrderLineId() != null) {
        fulfillmentWritebackService.addProducedQty(plan.getOrderLineId(), completeQty);
    }
}
```

> 覆盖 MQ 完工（`ErpWorkOrderCompletedConsumer` 调 complete）与手动完工两条路径。`PrdWorkOrder.planId`（`PrdWorkOrder.java:41`）、`PrdProductionPlan.orderLineId`（`:190`）已确认存在。

### 改动 5：信用三态化 `FROZEN` / `USED` / `RELEASED`

**文件** `so/domain/SoCreditUsage.java`
- 第 48 行后追加 `private Date usedTime;`（对应列 `used_time`）
- 注释更新为 `占用状态: FROZEN/USED/RELEASED`

**文件** `so/service/ICreditCheckService.java`
- 新增 `void convertToUsed(Long soId);`

**文件** `so/service/impl/CreditCheckServiceImpl.java`
- `release()`（89-91 行）：查询条件由 `eq(status,"FROZEN")` 改为 `in(status,"FROZEN","USED")` —— 已交付(USED)的信用在 close/cancel 时一并释放
- `getUsedAmount()`（115 行）：同样改为 `in(status,"FROZEN","USED")` —— 应收仍占额度
- 新增 `convertToUsed(soId)`：查 `status=FROZEN` 记录 → `setStatus("USED")` + `setUsedTime(now)` + updateById

**SQL** `script/sql/update/2026-07-07-so-credit-usage-used-time.sql`
```sql
ALTER TABLE `so_credit_usage` ADD COLUMN `used_time` datetime DEFAULT NULL COMMENT '转为已用(已交付)时间' AFTER `freeze_time`;
```

### 改动 6：`fulfill()` 重写（核心）

**文件** `so/service/ISoSalesOrderHeaderService.java`
- 新增 `void fulfill(Long soId, boolean force);`
- 保留 `void fulfill(Long soId);`（委托 `fulfill(soId, false)`，保持向后兼容）

**文件** `so/service/impl/SoSalesOrderHeaderServiceImpl.java`
- 注入 `ApplicationEventPublisher`（新增 import）
- 重写 `fulfill(Long soId, boolean force)`（替换 220-231 行）：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void fulfill(Long soId, boolean force) {
    SoSalesOrderHeader header = baseMapper.selectById(soId);
    if (header == null) throw new ServiceException("销售订单不存在");
    if (!STATUS_CONFIRMED.equals(header.getOrderStatus()))
        throw new ServiceException("只有已确认状态的订单才能履行");

    List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
        new LambdaQueryWrapper<SoSalesOrderLine>().eq(SoSalesOrderLine::getSoId, soId));
    if (lines.isEmpty()) throw new ServiceException("订单无明细，无法履行");

    // ① 严格门禁：所有行 delivered_qty >= ordered_qty
    BigDecimal tol = new BigDecimal("0.0001");
    List<String> shortLines = new ArrayList<>();
    for (SoSalesOrderLine l : lines) {
        BigDecimal need = l.getOrderedQty() == null ? BigDecimal.ZERO : l.getOrderedQty();
        BigDecimal done = l.getDeliveredQty() == null ? BigDecimal.ZERO : l.getDeliveredQty();
        if (need.subtract(done).compareTo(tol) > 0) {
            shortLines.add("行" + l.getLineNo() + "(" + l.getMaterialName()
                + "):需" + need + "已发" + done);
        }
    }
    if (!shortLines.isEmpty() && !force) {
        throw new ServiceException("存在未完全发货的行，无法履行：\n" + String.join("\n", shortLines)
            + "\n如需强制履行，请确认后重试。");
    }

    // ② 残余预留清理（兜底，正常出库已消耗至 0）
    stockReservationService.unreserve("SALES_ORDER", soId, null);

    // ③ 行状态固化 + fulfilled_qty 兜齐尾差
    List<Long> lineIds = new ArrayList<>();
    for (SoSalesOrderLine l : lines) {
        lineIds.add(l.getLineId());
        soSalesOrderLineMapper.update(null,
            Wrappers.<SoSalesOrderLine>lambdaUpdate()
                .eq(SoSalesOrderLine::getLineId, l.getLineId())
                .set(SoSalesOrderLine::getLineStatus, "FULFILLED")
                .set(SoSalesOrderLine::getFulfilledQty, l.getOrderedQty()));
    }

    // ④ 生产计划收尾：自制行关联计划 → CLOSED
    closeLinkedProductionPlans(lineIds);

    // ⑤ 信用转结 FROZEN → USED（已交付形成应收，仍占额度）
    creditCheckService.convertToUsed(soId);

    // ⑥ 头状态
    header.setOrderStatus(STATUS_FULFILLED);
    header.setFulfilledTime(new Date());
    baseMapper.updateById(header);

    // ⑦ 发布事件（为未来开票/通知预留）
    applicationEventPublisher.publishEvent(new SalesOrderFulfilledEvent(soId, header.getSoNo()));
    log.info("销售订单履行完成, soId={}, soNo={}, force={}", soId, header.getSoNo(), force);
}
```

- 新增私有 `closeLinkedProductionPlans(List<Long> lineIds)`：按 `orderLineId in (lineIds)` 查 `PrdProductionPlan`，逐条 `setStatus("CLOSED")` + updateById（非 CLOSED 终态的才更新）
- `cancel()`（259 行）追加守卫：`if (STATUS_FULFILLED.equals(header.getOrderStatus())) throw "已履行的订单不能取消，请改用关闭";`

**新文件** `so/event/SalesOrderFulfilledEvent.java`（Spring `ApplicationEvent`，仅 soId + soNo）

### 改动 7：Controller + 前端

**文件** `so/controller/SoSalesOrderHeaderController.java:217`
```java
@PutMapping("/fulfill/{soId}")
public R<Void> fulfill(@PathVariable Long soId,
                       @RequestParam(value = "force", defaultValue = "false") boolean force) {
    soSalesOrderHeaderService.fulfill(soId, force);
    return R.ok();
}
```

**文件** `plus-ui/src/api/erp/so/orderheader/index.ts:60`
```ts
export const fulfillOrder = (soId: number, force = false) =>
  request({ url: '/erp/so/orderheader/fulfill/' + soId, method: 'put', params: { force } });
```

**文件** `plus-ui/src/views/erp/so/orderheader/index.vue`（`handleFulfill` 381-393）
- 首次 `force=false` 调用
- 捕获失败：若 `err.msg` 含"未完全发货"，`$modal.confirm(err.msg + '\n是否强制履行？')` → 确认则 `fulfillOrder(soId, true)`
- 成功后刷新列表

**字典** `so_order_line_status` 补 `FULFILLED`（若缺失，前端行状态展示用）

## 三、事务与幂等

| 改动 | 事务 | 幂等保障 |
|------|------|---------|
| W1 SalesOutStrategy | `execute` @Transactional | `hasPosted` 已防重 |
| W1 completeSalesIssue | 方法 @Transactional | `txCount` 已防重 |
| W2 complete | @Transactional | 工单状态机已防重完 |
| fulfill | @Transactional | 状态守卫（仅 CONFIRMED 可履行） |
| 信用累加 | setSql 原子 | — |

回写均用 `setSql(... + qty)` 原子累加，避免读后写并发冲突。

## 四、风险与回滚

- **信用 `getUsedAmount` 语义变更**：现计入 `USED`，符合"应收占额度"。已确认订单信用从"冻结"变"已用"后仍占用，不影响新单校验逻辑。
- **cancel 守卫**：已履行不可取消，引导走 close（close 释放 FROZEN+USED）。
- **force 履行**：跳过发货校验但仍执行收尾（残余预留清理、行兜齐、计划关闭、信用转结）。仅用于尾差/客诉。
- **回滚**：全部为代码改动 + 一列增量 `used_time`，可 DROP COLUMN 回退。回写为纯增量行为（写入原本死字段），不影响既有功能。

## 五、实施顺序

1. SQL 增量 + 字典（地基）
2. 信用三态化（`SoCreditUsage` + `ICreditCheckService` + Impl）—— 独立可测
3. `ISoFulfillmentWritebackService` + Impl
4. W1（两处出库路径）+ W2（完工）回写
5. `fulfill()` 重写 + 事件 + cancel 守卫
6. Controller + 前端 force 流
7. 自测：
   - 路径 A（采购物料）：confirm → 销售出库 → 检查 delivered/fulfilled_qty → fulfill
   - 路径 B（自制物料）：confirm → 生产计划 → 工单完工 → 检查 production_qty → 销售出库 → fulfill
   - 强制履行：构造未发齐场景 → fulfill 弹短缺 → force=true 通过
   - 信用：fulfill 后查 so_credit_usage.status=USED；close 后=RELEASED

## 六、文件清单（15 改 + 3 新）

| # | 类型 | 文件 |
|---|------|------|
| 新 | 接口 | `so/service/ISoFulfillmentWritebackService.java` |
| 新 | 实现 | `so/service/impl/SoFulfillmentWritebackServiceImpl.java` |
| 新 | 事件 | `so/event/SalesOrderFulfilledEvent.java` |
| 新 | SQL | `script/sql/update/2026-07-07-so-credit-usage-used-time.sql` |
| 改 | 实体 | `so/domain/SoCreditUsage.java` |
| 改 | 接口 | `so/service/ICreditCheckService.java` |
| 改 | 实现 | `so/service/impl/CreditCheckServiceImpl.java` |
| 改 | 接口 | `so/service/ISoSalesOrderHeaderService.java` |
| 改 | 实现 | `so/service/impl/SoSalesOrderHeaderServiceImpl.java`（fulfill/cancel/新私有方法） |
| 改 | 策略 | `wm/strategy/impl/SalesOutStrategy.java` |
| 改 | 实现 | `wm/service/impl/WmSalesIssueServiceImpl.java` |
| 改 | 实现 | `prd/service/impl/PrdWorkOrderServiceImpl.java` |
| 改 | 控制器 | `so/controller/SoSalesOrderHeaderController.java` |
| 改 | API | `plus-ui/src/api/erp/so/orderheader/index.ts` |
| 改 | 页面 | `plus-ui/src/views/erp/so/orderheader/index.vue` |
| 改 | 字典 | `so_order_line_status` 补 FULFILLED（ry-vue.sql / 字典管理） |
