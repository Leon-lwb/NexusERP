# ERP 仓储（wm）库存内核 审计报告（2026-09-20）

> 审计人: 子代理 A（库存与批次内核半边） | 范围: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm` 下库存主表/批次/流水/预留/上架拣货/成本计价/条码SN/库位转储/盘点过账/定时任务 | 覆盖文件数: 读过 30 个（含 3 个 SQL 的定点 grep） | 分支: feature/erp-mes-iot-audit-fix
> 明确不含（由另一子代理负责）: `WmMaterialReceive*`、`WmSalesIssue*`、`WmArrivalNotice*`、`WmSubcontract*`、PDA/App 控制器、`wm/report`、前端页面布局

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| ERP-WM-01 | P0 | 功能不可用/事务 | 出库成本结转在扣减**之后**执行，凡单批次扣减量超过该批次扣减后余量即抛异常整单回滚（整批出库 100% 失败） | `MovingAverageValuationServiceImpl.java:121-129`、`SalesOutStrategy.java:158-165` |
| ERP-WM-02 | P0 | 并发/数据错 | 可用量守卫只用于「选行」，扣减上限却按 `stock_quantity` 计算 → 可扣穿其他单据的预留，预留承诺落空 | `ProductionIssueStrategy.java:93,104-108`、`MiscIssueStrategy.java:124,92-95`、`TransferStrategy.java:91,102-108` |
| ERP-WM-03 | P0 | 数据错/幂等 | 出库消耗预留不写流水，`unreserve` 按历史 RESERVE 流水全额释放 → 释放他人预留或抛异常阻断订单取消/关闭 | `WmStockReservationServiceImpl.java:94-123`、`SalesOutStrategy.java:155-159`、`SoSalesOrderHeaderServiceImpl.java:536-537` |
| ERP-WM-04 | P1 | 数据错 | 盘点差异按「生成盘点行时的账面快照」调整**当前**库存，期间有出入库即错调（应 set 到实际值） | `WmStockTakingPlanServiceImpl.java:256-261`、`CountAdjustStrategy.java:99-106` |
| ERP-WM-05 | P1 | 金额精度 | 盘点盘盈/盘亏只改数量、完全不动 `total_cost`/`unit_cost` → 移动平均成本与数量脱节，后续出库成本偏低 | `CountAdjustStrategy.java:50-53,99-123` |
| ERP-WM-06 | P1 | 功能缺失 | 效期零拦截：`expire_date` 只用于排序从不与当前时间比较；FEFO 反而把「最过期」批次排在最前；批次 EXPIRED 无自动任务 | `FefoPickingStrategy.java:44-45`、`SalesOutStrategy.java:200`、`WmBatchServiceImpl.java:256-275` |
| ERP-WM-07 | P1 | 数据错 | 调拨目标库存行查询排除冻结行 → 目标库位只有冻结行时会新建同维度库存行，造成同（物料/批次/库位）两行库存；随后收货直接抛错 | `TransferStrategy.java:125-154`、`ReceiveInStrategy.java:168-170` |
| ERP-WM-08 | P1 | 并发/幂等 | 库存行「查不到就 insert」无唯一约束保护，并发收货可建重复行，之后该物料所有收货永久失败 | `ReceiveInStrategy.java:131-133,164-171` |
| ERP-WM-09 | P1 | 并发 | IQC 冻结/解冻用条件更新但不递增 `version` → 乐观锁形同失效，已读旧版本的在途出库仍能扣减被冻结（不合格）库存 | `WmMaterialStockServiceImpl.java:231-240,255-259` |
| ERP-WM-10 | P1 | 口径不一致 | 「可用库存」接口与水位预警都按 `stock_quantity`（账面量）计算，忽略 `reserved_quantity` → 领料参考列虚高、预警漏报 | `WmProductionIssueServiceImpl.java:374-403`、`WmStockAlertServiceImpl.java:178-190` |
| ERP-WM-11 | P1 | 数据错 | 调拨入库流水 `before/after` 算错（既有行分支：before=pre−qty、after=pre）→ 台账前后断裂，与库存行对不上 | `TransferStrategy.java:156-163,178-184` |
| ERP-WM-12 | P1 | 功能缺失 | 水位预警只落库，无任何通知链路（无 SSE/站内信/邮件）；且 `warehouseId` 恒为 null、按物料全局汇总 → 单仓缺货不预警 | `WmStockAlertServiceImpl.java:150-190,231,265`、`StockAlertTask.java:57-75` |
| ERP-WM-13 | P2 | 校验 | 拣货/上架规则「优先级」与注释矛盾：通用规则 priority 更小时会抢占类别专属规则，物料被导向错误仓库 | `PickingResolutionServiceImpl.java:124-137`、`PutawayStrategyServiceImpl.java:124-138` |
| ERP-WM-14 | P2 | 数据漂移 | 批次 `received_qty/issued_qty` 仅手工端点可改、读-改-写且 `WmBatch` 无 `@Version` → 丢失更新且与库存行长期漂移 | `WmBatchServiceImpl.java:219-232`、`WmBatchController.java:126-129` |
| ERP-WM-15 | P2 | DDL/租户 | `wm_batch`/`wm_sn`/`wm_barcode`/`wm_warehouse` 唯一键缺 `tenant_id`，跨租户同编码直接 500 | `doc/audit/_raw-ddl-tenant-check.txt:25-33` |
| ERP-WM-16 | P2 | 健壮性 | 盘点无 `stockId` 时 `selectOne` 未带仓库过滤，命中多行抛 `TooManyResultsException`，或调错仓库的库存行 | `CountAdjustStrategy.java:84-97` |
| ERP-WM-17 | P2 | 状态机 | SN 解绑即置 `OUT`、绑定即置 `IN_STOCK`，`changeStatus` 无状态校验 → 已出库 SN 可被任意改回在库，追溯失真 | `WmSnServiceImpl.java:165-200,294` |
| ERP-WM-18 | P2 | 并发 | 上架策略完全忽略 `quantity`（无容量/冻结校验），两个并发的上架会推荐同一库位，无占用抢占 | `PutawayStrategyServiceImpl.java:55-74,87-112` |
| ERP-WM-19 | P2 | 健壮性 | 预警去重 `selectOne` 命中多条未处理记录时抛 `TooManyResultsException`，该物料当次检查被 catch 吞掉 | `WmStockAlertServiceImpl.java:242-247` |

## 1. P0 详述

### ERP-WM-01 出库成本结转在扣减之后执行，单批次扣减超过余量一半即整单回滚

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/MovingAverageValuationServiceImpl.java:121-129`、`.../wm/strategy/impl/SalesOutStrategy.java:158-165`、`.../wm/strategy/impl/ProductionIssueStrategy.java:156-161`、`.../wm/strategy/impl/MiscIssueStrategy.java:95-101`、`.../wm/strategy/impl/SubcontractIssueStrategy.java:161-165`
- **现状**: 所有出库路径都是「先 `deductStock` 再 `issueStockCost`」：

```java
// SalesOutStrategy.java:158-165（ProductionIssueStrategy:156-161、MiscIssueStrategy:95-101、SubcontractIssueStrategy:161-165 同构）
affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion()); // 先扣
...
BigDecimal outUnitCost = valuationService.issueStockCost(stock.getStockId(), deductQty); // 后结转
```

而 `issueStockCost` 重新从库里读该行，再用**扣减后**的数量做校验：

```java
// MovingAverageValuationServiceImpl.java:121-129
WmMaterialStock stock = baseMapper.selectById(stockId);   // 读到的是扣减后的数量
BigDecimal qty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
if (outQty.compareTo(qty) > 0) {
    throw new ServiceException("出库数量超过库存数量");
}
```

- **触发路径**: 任一策略里 `deductQty = stockQty.min(remaining)`（`SalesOutStrategy.java:143`、`ProductionIssueStrategy.java:107`、`MiscIssueStrategy.java:92`、`TransferStrategy.java:105`），扣减后该行余量 = `stockQty - deductQty`。只要 `deductQty > stockQty - deductQty`（即本次扣减超过该批次余量的一半，**整批扣完必然命中**）就抛 `ServiceException("出库数量超过库存数量")`。
  例：某批次在库 100，销售出库 60 → 扣减后余 40，`60 > 40` → 抛错；某批次在库 100、出库 100 → 扣减后余 0，`100 > 0` → 抛错。
- **后果**: 出库/领料/杂项出库/委外发料只要涉及「整批出库」或「超过半批出库」就**必然失败**，`@Transactional(rollbackFor = Exception.class)` 使整张单据过账回滚（`WmStockOperationServiceImpl.java:57-60`、`82-88`）。表现为用户侧「出库数量超过库存数量」而库存明明充足，是彻底的功能不可用；同时因为事务回滚，库存不会错，但业务无法推进。
- **修复建议**: 二选一（推荐①）：
  ① 把出库成本结转改成原子 SQL，去掉重读校验：
  `@Update("UPDATE wm_material_stock SET total_cost = GREATEST(total_cost - unit_cost * #{outQty}, 0), version = version + 1, update_time = NOW() WHERE stock_id = #{stockId}")`，
  `issueStockCost` 只返回调用方传入的 `unitCost`（或先 `selectById` 取 unitCost 再原子扣减），不再比较 `outQty` 与余量。
  ② 保留校验但改为「扣减前」调用 `issueStockCost`，并把调用点从 4 处策略统一挪到 `deductStock` 之前。
  两者都需**补测试**：新增 `src/test` 下覆盖「整批出库（deductQty == stockQty）」「超过半批出库」两个用例。
- **影响面**: `SalesOutStrategy`、`ProductionIssueStrategy`（含 `executeAllocated` 倒冲路径 `:127-142`）、`MiscIssueStrategy`、`SubcontractIssueStrategy`、`WmStockOperationServiceImpl`；前端 `erp/wm/sales-issue`、`erp/wm/productionissue`、`erp/wm/miscissue` 的「执行/过账」按钮。无需 DDL。
- **测试现状**: `ruoyi-modules/ruoyi-erp/src/test` 下未见覆盖库存出库成本结转的用例（本次范围内未发现相关测试类）。

### ERP-WM-02 可用量守卫只用于选行，扣减上限按账面量 → 扣穿其他单据的预留

- **证据**: `.../wm/strategy/impl/ProductionIssueStrategy.java:91-108`、`.../wm/strategy/impl/MiscIssueStrategy.java:123-125,89-95`、`.../wm/strategy/impl/TransferStrategy.java:90-108`、对照正确实现 `.../wm/strategy/impl/SalesOutStrategy.java:148-159`
- **现状**: 三个策略的选行条件都写了「排除已预留」：

```java
// ProductionIssueStrategy.java:92-93（MiscIssueStrategy:122-124、TransferStrategy:90-91 同构）
lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
lqw.apply("(stock_quantity - reserved_quantity) > 0");   // 选行时看可用量
...
BigDecimal stockQty = stock.getStockQuantity();          // :104  扣减上限却是账面量
BigDecimal deductQty = stockQty.min(remaining);          // :107
```

- **触发路径**: 批次在库 100、已被销售订单预留 80（`reserved_quantity=80`，可用 20）。生产领料/杂项出库/库位转储需求 100 → 该行通过选行过滤（可用 20 > 0）→ `deductQty = min(100, 100) = 100` → `deductStock` 的守卫只看 `stock_quantity >= 100`，扣减成功 → 库存 0、预留 80 不变，`stock_quantity - reserved_quantity = -80`。销售出库时该批次已无实物，`deductStockWithReserved` 因 `reserved_quantity >= consumeReservedQty` 或 `stock_quantity >= outQty` 不满足而失败（`WmMaterialStockMapper.java:98-108`）。
- **后果**: 生产领料/杂项出库/调拨可以合法地把销售已承诺的库存领走，销售订单出库失败或延迟；可用量出现负数，`wm/reservation/summary` 与水位预警全部失真。
- **补充**: `SalesOutStrategy.resolveSalesIssueStocks`（`:193-207`）**连可用量过滤都没有**，且当 `line.getSourceDocLineId()` 为空（手工销售出库）时 `reservedQty = 0`（`:128-132`），`batchConsumeReserved = 0`（`:150-151`）→ 走 `deductStock` 同样扣穿预留。
- **修复建议**: 三处统一改为按可用量计算扣减上限：
  `BigDecimal available = stock.getStockQuantity().subtract(stock.getReservedQuantity() == null ? ZERO : stock.getReservedQuantity()); BigDecimal deductQty = available.min(remaining);`
  更彻底的做法是给 `deductStock` 增加可选参数 `reservedGuard`（`AND (stock_quantity - reserved_quantity) >= #{deductQty}`），或在 `WmMaterialStockMapper` 新增 `deductStockOnlyAvailable(...)` 供非预留消费类出库使用；`SalesOutStrategy` 的选行也应加 `apply("(stock_quantity - reserved_quantity) > 0")`（其消费预留的分支已单独处理预留）。需补并发/预留交叉测试。
- **影响面**: 生产领料（含 PDA 领料）、杂项出库、库位转储、手工销售出库；`WmMaterialStockMapper`（如需新增方法）。无需 DDL。

### ERP-WM-03 预留消耗不记账，`unreserve` 按历史 RESERVE 流水全额释放

- **证据**: `.../wm/service/impl/WmStockReservationServiceImpl.java:92-127`、`.../wm/strategy/impl/SalesOutStrategy.java:148-159`、`.../wm/service/impl/WmSalesIssueServiceImpl.java:299-300,347`、`.../so/service/impl/SoSalesOrderHeaderServiceImpl.java:536-537`
- **现状**: 预留的创建会写 `RESERVE` 流水（`WmStockReservationServiceImpl.java:70`），但**出库消耗预留时不写任何流水**，只把 `reserved_quantity` 减掉：

```java
// SalesOutStrategy.java:155-159（WmSalesIssueServiceImpl.java:299-300 同构）
affected = stockMapper.deductStockWithReserved(stock.getStockId(), deductQty, batchConsumeReserved, stock.getVersion());
```

而 `unreserve` 是「按来源单据把历史 RESERVE 流水全额释放」：

```java
// WmStockReservationServiceImpl.java:94-99,115
.eq(WmMaterialStockTx::getTxType, "RESERVE") ... // 取该单据全部 RESERVE 流水
rows = stockMapper.releaseReserved(stock.getStockId(), tx.getTxQuantity(), stock.getVersion());
```

- **触发路径**: 销售订单 SO-1 预留 100（写 RESERVE 流水 100）→ 出库 100（`reserved_quantity` 减到 0，**RESERVE 流水仍在**）→ 订单关闭/取消时 `SoSalesOrderHeaderServiceImpl.java:536-537` 仍调用 `unreserve("SALES_ORDER", soId, null)`（该行注释自述「正常出库已把 reserved_qty 消耗至 0」）→ 若此刻**另一张订单 SO-2 的预留**把该库存行的 `reserved_quantity` 抬到 ≥100，则 `releaseReserved(100)` 成功，把 SO-2 的预留释放掉（`WmStockReservationServiceImpl.java:115` 只校验行级 `reserved_quantity >= releaseQty`，无来源归属）；若 `reserved_quantity` 不足 100，则抛 `ServiceException("释放预留失败(并发冲突或预留不足)")`（`:117-121`）→ **订单取消/关闭直接失败**。
- **补充**: 接口只有 `reserve/unreserve/checkAvailable` 三个方法（`IWmStockReservationService.java:23-37`），**没有任何预留过期/回收能力**，长期占用的预留只能靠人工按单据释放。
- **后果**: ①释放到别人的预留 → 可用量虚增、超卖；②释放失败 → 已出库订单无法取消/关闭；③无过期回收 → 僵尸预留永久占用可用量。
- **修复建议**:
  ① 出库消耗预留时同步写一条 `CONSUME_RESERVE` 流水（`txFlag` 复用 OUT，`sourceDocType/Id/LineId` 与被消耗的 RESERVE 一致），`unreserve` 改为按「SUM(RESERVE) − SUM(CONSUME_RESERVE) − SUM(UNRESERVE)」净额释放，净额 ≤ 0 时直接跳过；
  ② 给 `wm_material_stock_tx` 增加 `reserved_ref_id`（或直接用 `sourceDocLineId`）以便按来源精确释放；
  ③ 新增 `releaseExpiredReserved()`（按 `wm_material_stock_tx.tx_date` 或新增 `reserve_expire_date`）并挂 SnailJob + `@Scheduled` 兜底。
  需要 DDL（若采用 ②③），需补「出库后取消订单」「释放他人预留」两个用例。
- **影响面**: `WmStockReservationServiceImpl`、`SalesOutStrategy`、`WmSalesIssueServiceImpl`、`SoSalesOrderHeaderServiceImpl`（取消/关闭/出库兜底三处调用点 `:376,:537,:622,:647`）、`wm/reservation` 前端页。**测试现状**: 未发现预留闭环单测。

## 2. P1 详述

### ERP-WM-04 盘点差异按快照账面差异调整当前库存

- **证据**: `.../wm/service/impl/WmStockTakingPlanServiceImpl.java:256-261`、`.../wm/strategy/impl/CountAdjustStrategy.java:99-106`
- **现状**: 过账时 `diff = line.getActualQuantity() - line.getBookQuantity()`，其中 `bookQuantity` 是 `generateLines` 时写入盘点行的**历史快照**（`WmStockTakingPlanServiceImpl.java:172-214` 生成、`:257` 读取），随后策略把 `|diff|` 直接加减到**当前**库存行：

```java
// CountAdjustStrategy.java:99-106
BigDecimal beforeQty = stock.getStockQuantity();                 // 当前库存（已含期间出入库）
if (diffQuantity.compareTo(BigDecimal.ZERO) > 0) affected = stockMapper.addStock(stock.getStockId(), absDiff, stock.getVersion());
else affected = stockMapper.deductStock(stock.getStockId(), absDiff, stock.getVersion());
```

- **触发路径**: 生成盘点行时账面 100（`book=100`）→ 盘点期间发生销售出库 20（当前库存 80）→ 实盘 95，`diff = 95-100 = -5` → 策略对当前库存 80 再扣 5 → 结果 75，正确结果应为 95。
- **后果**: 盘点期间有业务发生时库存被错误调整，且差异方向可能完全相反；`bookQuantity` 越陈旧错得越多（盘点本身可能持续数天）。
- **修复建议**: `postAdjustment` 过账时用「当前库存」重算差异：先 `selectById(line.getStockId())` 取当前量 `cur`，再算 `diff = actual - cur`，并同步把 `line.bookQuantity` 更新为 `cur`；或改为在 `CountAdjustStrategy` 中直接用 `setSql("stock_quantity = #{actual}")` 的条件更新（`WHERE stock_id=? AND version=?`），彻底消除快照依赖。
- **影响面**: `WmStockTakingPlanServiceImpl.postAdjustment`/`afterFlowHandle`（`:427` 同一策略调用）、`CountAdjustStrategy`、前端 `erp/wm/stocktaking`。无需 DDL，需补「盘点期间发生出入库」用例。

### ERP-WM-05 盘点差异不过成本，移动平均成本与数量脱节

- **证据**: `.../wm/strategy/impl/CountAdjustStrategy.java:50-53,99-123`、对照 `.../wm/strategy/impl/ReceiveInStrategy.java:145-148`
- **现状**: `CountAdjustStrategy` 的构造器只注入 `WmMaterialStockTxMapper` 与 `WmMaterialStockMapper`（`:50-53`），过账全程只调 `addStock`/`deductStock`（`:103/:105`）并写流水，**从不调用 `IInventoryValuationService`**；而入库路径会调 `updateStockCost`（`ReceiveInStrategy.java:147`）。
- **触发路径**: 盘盈 10（`addStock`）→ `stock_quantity +10`、`total_cost` 不变 → `unit_cost` 与 `total_cost/stock_quantity` 不再相等；盘亏同理（`issueStockCost` 未调用，`total_cost` 不减少）。
- **后果**: 盘盈后单位成本被稀释、盘亏后成本虚高，后续出库按错误的 `unit_cost` 结转（`issueStockCost` 用 `stock.getUnitCost()`，`MovingAverageValuationServiceImpl.java:125-131`），库存总金额与财务账长期不符。
- **修复建议**: 盘盈按当前 `unit_cost`（或最近入库价）调增 `total_cost`；盘亏按 `unit_cost × |diff|` 调减 `total_cost`（复用 `issueStockCost`/新增 `adjustStockCost(stockId, qty, unitCost)`），并在 `CountAdjustStrategy` 注入 `IInventoryValuationService`。
- **影响面**: `CountAdjustStrategy`、`wm/valuation` 与 `fin/cost` 报表。无需 DDL。

### ERP-WM-06 效期零拦截：只排序不过滤，FEFO 优先拣最过期批次，EXPIRED 无自动任务

- **证据**: `.../wm/service/impl/FefoPickingStrategy.java:39-47`、`.../wm/strategy/impl/SalesOutStrategy.java:194-200`、`.../wm/strategy/impl/ProductionIssueStrategy.java:87-94`、`.../wm/strategy/impl/TransferStrategy.java:85-92`、`.../wm/service/impl/WmBatchServiceImpl.java:256-275`
- **现状**: 全仓对 `expire_date` 的使用只有三类：`orderByAsc(expireDate)`（排序）、`isNotNull`（FEFO 排除空值）、`eq(bo.getExpireDate())`（查询条件）。**没有任何一处与 `new Date()` 比较做拦截**（grep `ExpireDate` 全量 23 处命中已逐条核对，见证据行）。FEFO 策略自身：

```java
// FefoPickingStrategy.java:42-46
lqw.eq(WmMaterialStock::getFrozenFlag, 0);
lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
lqw.isNotNull(WmMaterialStock::getExpireDate);
lqw.orderByAsc(WmMaterialStock::getExpireDate);   // 最早到期排最前 —— 过期品优先级最高
```

批次侧只有手工端点 `POST /erp/wm/batch/checkExpiry`（`WmBatchController.java:154-155`）能触发 `checkExpiry()` 把 `ACTIVE + expiry_date < now` 置为 `EXPIRED`（`WmBatchServiceImpl.java:256-275`）；该类**没有** `@JobExecutor`/`@Scheduled`，全仓定时任务只有 `FaceVerifyBackfillTask`、`SoContractExpiryTask`、`StockAlertTask`、`VendorEvaluationTask` 四个（grep `@Scheduled|@JobExecutor` 结果）。
- **触发路径**: 效期 2026-01-01 的批次仍在库、状态 `ACTIVE` → 2026-09 领料/出库/调拨时被正常选中且**排在第一位**；批次状态也永远不会自动变成 `EXPIRED`（除非有人手工点按钮）。
- **后果**: 过期物料被优先发给客户/投入生产；`EXPIRED` 是「准死状态」（仅人工可达）；库存效期报表与实物不符。
- **修复建议**: ① 选行统一加 `apply("(expire_date IS NULL OR expire_date >= CURDATE())")`（或按物料 `shelf_life_warn_days` 提前拦截），并给 FEFO 增加「跳过过期行」；② 给 `WmBatchServiceImpl.checkExpiry()` 加 `@JobExecutor(name="wmBatchExpiryTask")` + `@Scheduled` 兜底（照抄 `StockAlertTask.java:26,42-46` 的写法，含 `SchedulerFallbackUtils` 防双调度）；③ 过期批次同步把对应 `wm_material_stock` 行置 `frozen_flag=1`（否则批次状态与库存可用性继续脱节）。
- **影响面**: 全部出库策略、`WmBatchServiceImpl`、`WmBatchController`、前端 `erp/wm/batch`（`index.vue:188` 已接手工按钮）。无需 DDL。

### ERP-WM-07 调拨目标行排除冻结行 → 同维度两行库存，随后收货永久失败

- **证据**: `.../wm/strategy/impl/TransferStrategy.java:120-154`、`.../wm/strategy/impl/ReceiveInStrategy.java:154-171`
- **现状**: 目标库存查找带 `frozen_flag = 0`（`TransferStrategy.java:125`），查不到就**新建**一行 `frozen_flag=0` 的库存（`:129-154`，含 `setVersion(0)`）：

```java
toLqw.eq(WmMaterialStock::getFrozenFlag, 0L);          // :125
List<WmMaterialStock> toStocks = stockMapper.selectList(toLqw);
if (toStocks == null || toStocks.isEmpty()) { ... stockMapper.insert(toStock); }  // :129-154
```

而收货侧一旦匹配到多行就直接抛错（`ReceiveInStrategy.java:164-171`）：

```java
if (stockList.size() > 1) {
    throw new RuntimeException("物料【"+detail.getMaterialName()+"】匹配到多条库存记录，请检查库存维度");
}
```

- **触发路径**: 目标库位该批次因 IQC 不合格被冻结（`frozen_flag=1`，`WmMaterialStockServiceImpl.java:231-240`）→ 调拨入库 → 新建一行 `frozen_flag=0` → 此后该（物料/批次/库位/供应商）维度存在 2 行库存 → 下一次收货直接 500「匹配到多条库存记录」。
- **后果**: 收货功能对该物料永久不可用（需人工清库）；冻结库存与在库库存被人为拆成两行，`frozen_flag` 语义与可用量统计全部失真。
- **修复建议**: 目标行查找去掉 `frozen_flag` 条件（冻结是状态不是维度），命中冻结行时显式抛「目标库位库存已冻结，请先解冻」；若必须新建，则应在插入前加唯一约束 + `INSERT ... ON DUPLICATE KEY` 兜底。配合 ERP-WM-08 一起处理。
- **影响面**: `TransferStrategy`、`ReceiveInStrategy`、`wm/transfer`、`wm/receive`。需 DDL（唯一键，见 ERP-WM-08）。

### ERP-WM-08 库存行「查不到就 insert」，无唯一约束保护 → 并发重复行

- **证据**: `.../wm/strategy/impl/ReceiveInStrategy.java:126-133,154-171`；`doc/audit/_raw-ddl-tenant-check.txt` 中**没有** `wm_material_stock` 条目（该文件扫描了 wm 下所有含唯一键的表，说明该表无任何 UK）
- **现状**: 收货按 `findStockForReceive` 先查（`:154-171`），返回 null 就 `stockMapper.insert`（`:131-133`）——典型 TOCTOU：两个并发的收货单（或 MQ 重投）对同一新维度都会查到 null 并各自插入。
- **触发路径**: 同一物料/批次/库位的两张收货单同时过账（MQ 消费者 `PurchaseReceiveWritebackConsumer`/`StockInConsumer` 并发消费）→ 两行库存；此后 `findStockForReceive` 命中 2 行 → 所有后续收货抛「匹配到多条库存记录」。
- **后果**: 同一维度库存被拆成多行，FEFO/预留/盘点按行分散，可用量汇总虽正确但冻结/预留会分叉；收货功能在人工干预前不可用。
- **修复建议**: `wm_material_stock` 增加唯一键 `uk_stock_dim(tenant_id, material_id, batch_id, location_id, vendor_id, del_flag)`（`batch_id`/`vendor_id` 为 NULL 时 MySQL 唯一键不去重，需用 `IFNULL(batch_id,0)` 生成列或应用层用分布式锁兜底），并把插入改为 `INSERT ... ON DUPLICATE KEY UPDATE stock_quantity = stock_quantity + VALUES(...)`；或对「维度 → 新建」加 `@Lock4j` 分布式锁。需要 DDL + 存量数据去重脚本，需补并发用例。
- **影响面**: `ReceiveInStrategy`、`TransferStrategy`、`ProductionCompletionStrategy`、`MiscRecptStrategy` 等所有「新建库存行」路径。**测试现状**: 无并发收货测试。

### ERP-WM-09 冻结/解冻不递增 version，乐观锁失效

- **证据**: `.../wm/service/impl/WmMaterialStockServiceImpl.java:231-240,255-259,293-302`、`.../wm/mapper/WmMaterialStockMapper.java:46-51`
- **现状**: 冻结/解冻用 `LambdaUpdateWrapper` 条件更新 `frozen_flag`，但**没有** `set(version, version+1)`：

```java
// WmMaterialStockServiceImpl.java:231-240
LambdaUpdateWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaUpdate()
    .set(WmMaterialStock::getFrozenFlag, 1L)
    .eq(WmMaterialStock::getMaterialId, materialId)
    .eq(WmMaterialStock::getFrozenFlag, 0L);
```

而出库扣减依赖 `version` 做并发闸门（`WmMaterialStockMapper.java:46-51`：`WHERE stock_id = ? AND stock_quantity >= ? AND version = ?`）。
- **触发路径**: 领料流程在 t0 读到库存行（version=5、`frozen_flag=0`）→ t1 IQC 判定不合格、`freezeStockByReceiveBatches` 把该行置 `frozen_flag=1`（version 仍为 5）→ t2 领料用 version=5 调 `deductStock` → **扣减成功**，不合格库存被领用。
- **后果**: IQC 冻结（历史 E9 修复点）在并发窗口内可被绕过，不合格物料流入生产/客户；解冻同理存在旧版本写入问题。
- **修复建议**: 冻结/解冻的 update wrapper 增加 `.setSql("version = version + 1")`；更进一步，`deductStock` 的 WHERE 增加 `AND frozen_flag = 0`（把冻结判定下沉到原子 SQL，彻底消除 TOCTOU）。无需 DDL，需补「冻结与出库并发」用例。
- **影响面**: `WmMaterialStockServiceImpl`、`WmMaterialStockMapper`、IQC 消费者 `ErpIqcCompletedConsumer`（冻结调用方）。

### ERP-WM-10 「可用库存」口径不统一：接口与预警都用账面量

- **证据**: `.../wm/service/impl/WmProductionIssueServiceImpl.java:373-383,389-404`、`.../wm/domain/WmMaterialStock.java:190-197`、`.../wm/service/impl/WmStockAlertServiceImpl.java:177-190`
- **现状**: 领域模型明确定义可用量 = 在库 − 已预留（`WmMaterialStock.java:193-197`），但对外接口与预警都用 `stock_quantity`：

```java
// WmProductionIssueServiceImpl.java:397-401（selectAvailableStock 亦同，:379-381）
lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
... result.merge(s.getMaterialId(), zeroIfNull(s.getStockQuantity()), BigDecimal::add);  // 账面量

// WmStockAlertServiceImpl.java:185-189
for (WmMaterialStock stock : stocks) { totalQty = totalQty.add(stock.getStockQuantity()); }  // 账面量
```

- **触发路径**: 某物料在库 100、销售已预留 100 → 领料列表「可用库存」列显示 100，操作员按 100 领料 → 实际领料时被 ERP-WM-02 或策略过滤挡住（可用 0）→ 反复失败/或被扣穿预留。
- **后果**: 用户按错误口径备料/领料；水位预警对「账面充足但已全部被预留」的物料不报警（漏报），导致实际缺料停线。
- **修复建议**: 统一为 `stock_quantity - reserved_quantity`（冻结行单独排除）：`sumAvailableStockByMaterials`/`selectAvailableStock` 返回 `getAvailableQuantity()`；`WmStockAlertServiceImpl.checkMaterialStock` 同样改用可用量，并在 `wm_stock_alert` 上区分「账面量/可用量」口径字段（或至少在 VO 注明）。需同步前端列名与提示文案（`erp/wm/productionissue`、`erp/wm/stock-alert`）。
- **影响面**: `WmProductionIssueServiceImpl`、`WmStockAlertServiceImpl`、`IWmStockReservationService.checkAvailable`（该处口径已正确，`:136-146`）。

### ERP-WM-11 调拨入库流水 before/after 算错

- **证据**: `.../wm/strategy/impl/TransferStrategy.java:155-163,178-184`
- **现状**: 既有目标行分支中，`toStock` 是 `addStock` **之前**读到的快照（`stockQuantity = pre`），但流水按 `pre − qty → pre` 生成：

```java
// TransferStrategy.java:156-163
toStock = toStocks.get(0);
int affected = stockMapper.addStock(toStock.getStockId(), line.getQuantity(), toStock.getVersion());
...
stockTxMapper.insert(buildTransferInTx(line, toStock, line.getQuantity(), now));
// :180-181
BigDecimal beforeQty = stock.getStockQuantity().subtract(txQuantity);   // = pre - qty  ← 错
BigDecimal afterQty = stock.getStockQuantity();                         // = pre        ← 错
```

正确值应为 `before = pre`、`after = pre + qty`（新建行分支 `:150` 因 `stockQuantity` 已设为 `qty`，算出的 0 → qty 恰好正确，掩盖了该缺陷）。
- **触发路径**: 同一库位第二次转储（目标行已存在，pre=50，转储 30）→ 流水记录 20 → 50，实际 50 → 80。
- **后果**: 库存台账（`wm_material_stock_tx`）与库存行对不上，任何「按流水重建库存」的对账/审计都会失败，`wm/reconciliation` 台账校验报错。
- **修复建议**: `buildTransferInTx` 改为显式传入 `beforeQty`/`afterQty`（调用点用 `pre` 与 `pre + qty`），或与 `ReceiveInStrategy.java:127-129,148` 保持一致：先 `addStock` 再 `selectById` 重读，用重读值算 `after`。
- **影响面**: `TransferStrategy`、`wm/transfer`、`wm/reconciliation`。无需 DDL，需补转储流水断言用例。

### ERP-WM-12 水位预警无通知链路，且仓库维度恒为空

- **证据**: `.../wm/service/impl/WmStockAlertServiceImpl.java:148-175,229-275`、`.../wm/task/StockAlertTask.java:57-75`
- **现状**: `checkStockAlerts()` 全程只做「查库存 → 写/更新 `wm_stock_alert` 行」（`WmStockAlertServiceImpl.java:253-273`），**没有任何** SSE/站内信/IM/邮件推送调用；`createOrUpdateAlert` 的 `warehouseId` 形参在唯一调用点传入 `null`（`:231`），落库时 `alert.setWarehouseId(warehouseId)` 即 null（`:265`），而统计 `totalQty` 是跨全部仓库汇总（`:178-190` 无 warehouse 过滤）。任务侧 `StockAlertTask.jobExecute` 只调 `checkStockAlerts()` 并写日志（`:57-75`）。
- **触发路径**: 某仓库 A 的物料 M 库存为 0（缺料），但仓库 B 有 1000 且安全库存按物料全局汇总判定 → 不生成预警；即使生成预警，也无人收到任何通知，只能靠人主动打开 `erp/wm/stock-alert` 页面看。
- **后果**: 预警形同「事后台账」：单仓缺料不报警，报警了也没人知道 → 缺料停线；「水位预警」这一能力实际上不可用。
- **修复建议**: ① `wm_stock_alert` 启用 `warehouse_id` 维度，`checkMaterialStock` 按 `warehouseId` 分组统计（`GROUP BY warehouse_id`）；② 预警生成后调用项目既有的通知能力（`SysUserNotification`/SSE，参考 `ruoyi-im` 的 `SysUserNotificationController` 与 `UserNotificationEventListener`）推送给仓库负责人；③ 阈值（`safety_stock`/`min_stock`/`max_stock`）当前取物料主数据全局值，若要按仓设置需补字段。
- **影响面**: `WmStockAlertServiceImpl`、`StockAlertTask`、`WmStockAlert`（DDL 已有 `warehouse_id` 列，见 VO `WmStockAlertVo`）、前端 `erp/wm/stock-alert`。可能需 DDL（若增加按仓阈值）。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果 / 修复 |
|---|---|---|---|
| ERP-WM-13 | 拣货/上架规则「先按 priority 排序再取第一个 category 为空或匹配者」，通用规则 priority 更小时抢占类别专属规则，与注释「优先匹配类别专属」相反 | `PickingResolutionServiceImpl.java:124-137`、`PutawayStrategyServiceImpl.java:124-138` | 物料被导向通用规则绑定的仓库/库位。修复：分两轮匹配（先 categoryId 精确匹配，再取 categoryId 为空的通用规则），不要用一条流 |
| ERP-WM-14 | 批次 `received_qty/issued_qty` 仅由手工端点 `PUT /erp/wm/batch/updateQuantity` 维护，读-改-写且 `WmBatch` 无 `version` 字段 | `WmBatchServiceImpl.java:219-232`、`WmBatchController.java:126-129`、`WmBatch.java`（字段清单无 version） | 并发丢失更新；收货/出库实际不更新这两个字段，与 `wm_material_stock` 长期漂移。修复：改为 `setSql("received_qty = received_qty + ?")` 原子更新，并在收货/出库策略内同步；或直接删除这两个冗余字段改用库存行聚合 |
| ERP-WM-15 | `wm_batch.uk_batch_code`、`wm_sn.uk_sn_code`、`wm_barcode.uk_barcode_code`、`wm_warehouse.uk_warehouse_code` 唯一键不含 `tenant_id` | `doc/audit/_raw-ddl-tenant-check.txt:25-33`；`WmBatchServiceImpl.java:99-101`（用户可手填 batchCode） | 租户 B 录入与租户 A 相同的批次号/条码 → 唯一键冲突 500（且报错信息暴露他租户数据存在）。修复：唯一键加 `tenant_id`（需 DDL + 存量校验） |
| ERP-WM-16 | 盘点行无 `stockId` 时按物料+批次+库位 `selectOne`，未带 `warehouseId` 过滤 | `CountAdjustStrategy.java:84-97` | 多仓库同批次同库位编码时命中多行抛 `TooManyResultsException`（盘点过账失败），或调整到错误仓库的库存行。修复：加仓库维度并改 `selectList` + 显式校验唯一 |
| ERP-WM-17 | SN `bindStock`/`unbindStock` 直接改状态（`IN_STOCK`/`OUT`），`changeStatus` 无任何状态校验 | `WmSnServiceImpl.java:163-200,294` | 已出库 SN 可被任意改回在库、可重复绑定/解绑，序列号追溯链失真。修复：定义状态机并做合法流转校验（CAS 条件更新） |
| ERP-WM-18 | 上架策略 `recommendLocation`/`resolvePutaway` 接收 `quantity` 但**从未使用**，候选库位查询不校验冻结与容量 | `PutawayStrategyServiceImpl.java:55-74,87-112` | 可推荐冻结库位或容量不足的库位；并发的两次上架推荐同一库位且无占用抢占。修复：候选集过滤 `frozen_flag=0` 并校验剩余容量，推荐后落 `wm_storage_location` 占用（条件更新/CAS） |
| ERP-WM-19 | 预警去重 `selectOne`（物料+类型+未处理）命中多条时抛 `TooManyResultsException` | `WmStockAlertServiceImpl.java:242-247` | 该物料的检查被 `checkStockAlerts` 的 `try/catch` 吞掉（`:167-171`）并只记 warn → 预警静默缺失。修复：改 `selectList` 取最新一条，或加唯一键 `(tenant_id, material_id, alert_type, handled_flag)` |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 预留过期/自动回收 | `IWmStockReservationService.java:23-37` 仅 `reserve/unreserve/checkAvailable`，全仓无 `releaseExpired` 调用与定时任务 | `wm_material_stock_tx`（RESERVE 流水含 `tx_date`）、`SnailJob`/`@Scheduled` 兜底模板（`StockAlertTask.java:42-46`） | 0.5~1 天（含新字段 `reserve_expire_date` 或按 tx_date + 配置天数回收） |
| 2 | 批次效期自动过期任务 | `WmBatchServiceImpl.java:256-275` 只有手工端点 `WmBatchController.java:154-155`；全仓无对应 `@JobExecutor`/`@Scheduled` | `WmBatch.status=EXPIRED`、`expiry_date`、任务模板 | 0.5 天 |
| 3 | 库存预警通知链路 | `WmStockAlertServiceImpl.java:150-275` 无任何推送调用；`StockAlertTask.java:57-75` 只落库 | `wm_stock_alert`、IM/站内信（`SysUserNotification`）、SSE | 1 天 |
| 4 | 预警的仓库维度 | `WmStockAlertServiceImpl.java:178-190` 跨仓汇总，`:231/:265` `warehouseId` 恒 null | `wm_stock_alert.warehouse_id` 列已存在（`WmStockAlertVo` 有该字段） | 0.5 天（+ 前端按仓筛选/展示） |
| 5 | 库存成本重算入口 | 本次范围内未见任何「按物料/仓库重算 `unit_cost`/`total_cost`」的服务或端点（`IInventoryValuationService` 仅 3 个方法，`MovingAverageValuationServiceImpl.java:38/73/114`） | `wm_material_stock.unit_cost/total_cost`、`wm_material_stock_tx` 全量流水 | 1~2 天（可用于修复 WM-05/WM-11 的历史数据） |

## 5. 已核查确认无问题（证明覆盖范围）

1. **原子扣减基础设施齐备**：`deductStock`/`addStock`/`reserveStock`/`releaseReserved`/`deductStockWithReserved` 全部为 `setSql` 式原子 UPDATE + `stock_quantity >= qty`/`reserved_quantity >= qty` 守卫 + `version = ?` 条件（`WmMaterialStockMapper.java:46-108`），并递增 `version`。
2. **新建库存行 `setVersion(0)` 已全覆盖、无残留**（复核历史 E5~E11）：`ReceiveInStrategy.java:201`、`TransferStrategy.java:153`、`MiscRecptStrategy.java:125`、`ProductionCompletionStrategy.java:226`、`SubcontractReceiveStrategy.java:273`、`ReworkReceiveStrategy.java:230`、`WmMaterialReceiveExecutorServiceImpl.java:491,519` —— grep `setVersion(0)` 在 wm 策略目录无遗漏（其余命中在 `fin`/`so`，非本次范围）。
3. **库存增减未发现「Java 先算后写主表」路径**：所有数量变更都经 `StockOperationStrategy`（`WmStockOperationServiceImpl.java:47-139` 委派），未发现 `updateById(stock)` 直接改 `stock_quantity`；唯一的读-改-写是成本字段 `total_cost/unit_cost`（`MovingAverageValuationServiceImpl.java:98-100,135-136`，MP `@Version` 生效前提是 `updateById` 带 version，需在修复 WM-01 时一并改为原子 SQL）。
4. **单据级幂等已做**：`BaseStockOperationStrategy.hasPosted`（`:26-31`）覆盖收货/销售出库/返工收发/委外收发/完工入库；调拨与盘点用状态 CAS 抢占（`WmTransferServiceImpl.java:173-179`、`WmStockTakingPlanServiceImpl.java:234-241`），重复提交会抛错而非重复入账。
5. **预留创建侧是原子且失败显式**：`reserveStock` 条件更新（`WmMaterialStockMapper.java:72-79`），预留不足时抛 `ServiceException` 而非静默部分预留（`WmStockReservationServiceImpl.java:74-78`）；释放失败重试 3 次后抛异常（`:106-121`）。问题只在「消耗不记账」（见 WM-03）。
6. **销售出库消费预留的主链路仍在**（历史 E8 复核通过）：`deductStockWithReserved` + SO 行 `reserved_qty` 原子扣减 + `ge(reservedQty, totalConsumeReserved)` 守卫（`SalesOutStrategy.java:148-184`、`WmSalesIssueServiceImpl.java:299-300,347`）。
7. **IQC 冻结定位与解冻幂等**：`freezeStockByReceiveBatches`/`unfreezeByBatch`/`unfreezeByReceive` 都是条件更新（`frozen_flag` 0↔1 才受影响），无批次号/批次ID 时显式 `log.warn` 且由单据级门禁兜底，不再静默跳过（`WmMaterialStockServiceImpl.java:224-243,249-263,268-306`）。缺陷只在 version 未递增（见 WM-09）。
8. **批次号/序列号生成跨实例安全**：Redis 原子自增 + 当日最大号兜底（`WmBatchServiceImpl.java:284-290`、`WmSnServiceImpl.java:243`、`DailyCodeSequenceProvider`），未发现并发重号。
9. **预警去重与自动关闭逻辑存在**：同物料同类型未处理预警走更新而非新增（`WmStockAlertServiceImpl.java:242-252`），不再适用的未处理预警自动关闭（`:211-227`）；任务按租户遍历并在 `TenantHelper.dynamic` 下执行（`StockAlertTask.java:57-68`）—— 租户上下文处理正确。
10. **权限种子齐备（无「非超管 403」）**：`wm:stock:list`（`script/sql/ry-vue.sql:92047`）、`wm:stock:edit`（`:92120`）、`wm:stockAlert:list/query/add/edit/remove/export`（`:92073-92078`）、`wm:reconciliation:*`（`:92079-92084`）、`wm:reservation:summary/query/list/add/edit`（`:92167-92172`）、`wm:barcode:*`/`wm:barcodeConfig:*`（`:92294-92306`）、`wm:sn:*`（`:92653-92658`）、`wm:batch:*`（`:92659-92664`）、`wm:trace:list/query`（`:92665-92667`）、`wm:stocktaking:*`（`:92055-92060`）、`wm:stocktx:list`（`:96745`）均已核对存在。
11. **前端 API 无「后端有端点前端无函数」缺口**：`plus-ui/src/api/erp/wm` 下 `reservation/`、`stock-alert/`、`batch/`、`sn/`、`trace/`、`reconciliation/`、`stocktaking/`、`transfer/`、`stock/` 的 `index.ts` 全部存在（glob 全量 54 个文件），且 `batch/index.ts:31-40` 已接 `updateQuantity/changeStatus/checkExpiry`。
12. **预留控制器校验到位**：`WmStockReservationController.java:42` 类级 `@Validated`，`:106`/`:126` 方法级 `@Validated @RequestBody`，权限码 `wm:reservation:*` 与种子一致（`:66,86,103,123,136`）。
13. **盘点行级幂等**：`POSTED`/`BALANCED` 行跳过（`WmStockTakingPlanServiceImpl.java:252-255`），计划级 CAS 抢占（`:233-241`），未发现重复过账。
14. **调拨单据防重**：非 `EXECUTED` 才能抢占 `EXECUTING`，影响 0 行抛错（`WmTransferServiceImpl.java:169-179`）。
15. **原生 `@Select` 租户隔离**：`WmMaterialStockMapper.aggregateStockByWarehouse`（`:28-36`）等原生 SQL 未手写 `tenant_id`，但项目多租户由 MyBatis-Plus `TenantLineInnerInterceptor` 统一重写 SQL（`wm_*` 不在排除名单），本次未发现确凿的越权读取证据，故不列为发现。

## 6. 前后端字段双向不一致清单

本次范围为后端库存内核，前端页面级比对由另一子代理负责。仅记录一条由后端口径引发的展示不一致：

| 字段 | 后端(BO/VO/表) | 前端(types/列表) | 结论 |
|---|---|---|---|
| 「可用库存」参考列 | `WmProductionIssueServiceImpl.sumAvailableStockByMaterials:390-403` 返回 `SUM(stock_quantity)`（账面量）；领域模型另有 `getAvailableQuantity()=在库−预留`（`WmMaterialStock.java:193-197`） | `plus-ui/src/api/erp/wm/productionissue/index.ts:52,61` 的 `getAvailableStock/getAvailableStockBatch` 直接展示该值 | 字段名与语义不符：展示的是账面量而非可用量（见 ERP-WM-10） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：
  - ERP-WM-01：`issueStockCost` 改原子 SQL / 调用点前移（`MovingAverageValuationServiceImpl` + 4 处策略）—— 影响最大，先修。
  - ERP-WM-02：4 处出库扣减上限改用可用量（`ProductionIssueStrategy`/`MiscIssueStrategy`/`TransferStrategy`/`SalesOutStrategy`）。
  - ERP-WM-03：出库消耗预留写 `CONSUME_RESERVE` 流水 + `unreserve` 按净额释放（含 `SalesOutStrategy`/`WmSalesIssueServiceImpl`/`WmStockReservationServiceImpl`）。
- **批次 B（P1）**：ERP-WM-09（冻结加 `version+1` 且 `deductStock` 加 `frozen_flag=0`）、ERP-WM-04/05（盘点口径与成本）、ERP-WM-07/08（库存行唯一键 + 冻结行不再新建，需 DDL 与去重脚本）、ERP-WM-06（效期拦截 + 过期任务）、ERP-WM-10/11/12（口径统一、流水修正、预警通知）。
- **批次 C（P2）**：ERP-WM-13~19（规则优先级、批次冗余字段、唯一键 tenant_id、盘点/预警 selectOne、SN 状态机、上架容量与并发）。
