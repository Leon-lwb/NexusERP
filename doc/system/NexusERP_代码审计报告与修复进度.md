# ERP/MES/IOT 系统代码审计报告与修复进度

> 审计日期: 2026-06-24 | 审计范围: 销售(SO)、采购(PU)、仓库(WM)、质检(QC)、生产(PRD)、MES、IOT 全模块
> 本文档记录所有已发现的问题、修复状态和修复方案，供后续开发人员参考。

---

## 目录

- [问题汇总](#问题汇总)
- [阶段一：数据安全 CRITICAL（已完成 ✅）](#阶段一数据安全-critical已完成-)
- [阶段二：业务正确性 HIGH（待修复）](#阶段二业务正确性-high待修复)
- [阶段三：健壮性 MEDIUM（待修复）](#阶段三健壮性-medium待修复)
- [阶段四：改进 LOW（待修复）](#阶段四改进-low待修复)

---

## 问题汇总

| 严重级别 | 数量 | 已修复 | 说明 |
|---------|------|--------|------|
| 🔴 CRITICAL | 5 | 5 ✅ | 会导致数据错误或安全漏洞 |
| 🟠 HIGH | 11 | 0 | 会导致业务流程异常 |
| 🟡 MEDIUM | 20 | 0 | 逻辑缺陷或防御性不足 |
| 🟢 LOW | 6 | 0 | 改进建议 |

---

## 阶段一：数据安全 CRITICAL（已完成 ✅）

### C1. 库存操作并发竞争条件 ✅

**问题描述**：7 个库存策略使用 `stockMapper.updateById(stock)` 修改库存，无版本保护、无原子性。并发操作时静默覆盖库存数据，导致负库存或库存虚增。

**涉及文件**：
- `WmMaterialStockMapper.java` — 新增 `addStock` 原子方法
- `ProductionIssueStrategy.java` — 改用 `deductStock`
- `SalesOutStrategy.java` — 改用 `deductStock`
- `TransferStrategy.java` — 出库改用 `deductStock`，入库改用 `addStock`
- `MiscIssueStrategy.java` — 改用 `deductStock`
- `MiscRecptStrategy.java` — 改用 `addStock`
- `CountAdjustStrategy.java` — 盘盈用 `addStock`，盘亏用 `deductStock`
- `ProductionCompletionStrategy.java` — 改用 `addStock`
- `ReceiveInStrategy.java` — 已有库存改用 `addStock`
- `ReworkReceiveStrategy.java` — 改用 `addStock`
- `SubcontractReceiveStrategy.java` — 改用 `addStock`
- `WmStockOperationServiceImpl.java` — 4 个 post* 方法补 `@Transactional`

**修复方案**：
```java
// 扣减（原 updateById 替换为）
int affected = stockMapper.deductStock(stock.getStockId(), deductQty, stock.getVersion());
if (affected == 0) {
    throw new ServiceException("库存并发冲突，请重试");
}

// 增加（原 updateById 替换为）
int affected = stockMapper.addStock(stock.getStockId(), addQty, stock.getVersion());
if (affected == 0) {
    throw new ServiceException("库存并发冲突，请重试");
}
```

**Mapper 原子方法**：
```java
@Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{deductQty}, " +
        "version = version + 1, update_time = NOW() " +
        "WHERE stock_id = #{stockId} AND stock_quantity >= #{deductQty} AND version = #{version}")
int deductStock(...);

@Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity + #{addQty}, " +
        "version = version + 1, update_time = NOW() " +
        "WHERE stock_id = #{stockId} AND version = #{version}")
int addStock(...);
```

---

### C2. MQ 补偿机制完全失效 ✅

**问题描述**：`ReliableMessageProducer.recordMessage()` 保存状态为 `"SENT"`，而 `MessageCompensationTask` 查找 `"SENDING"`，永远不会匹配。可靠消息投递机制形同虚设。

**涉及文件**：
- `ReliableMessageProducer.java` — 先记录 `SENDING`，发送后更新 `SENT`

**修复方案**：
```java
public void send(...) {
    // 1. 先记录为 SENDING
    recordMessage(exchange, routingKey, message, "SENDING");
    // 2. 再发送 MQ
    rabbitTemplate.convertAndSend(...);
    // 3. 发送成功后更新为 SENT
    updateMessageStatus(message.getMessageId(), "SENT");
}
```

---

### C3. 销售出库绕过策略模式，不生成事务记录 ✅

**问题描述**：`WmSalesIssueServiceImpl.completeSalesIssue()` 直接操作库存但不写 `wm_material_stock_tx`，破坏库存对账和审计追溯。且不过滤冻结库存。

**涉及文件**：
- `WmSalesIssueServiceImpl.java` — 重写 `completeSalesIssue` 方法

**修复方案**：
1. 用 `deductStock` 替代 `updateById`
2. 每笔扣减后插入 `WmMaterialStockTx` 记录（txType=SALES_OUT）
3. 添加幂等检查（检查是否已有该单号的事务记录）
4. 库存查询添加 `frozenFlag = 0` 过滤

---

### C4. 生产入库策略无幂等检查 ✅

**问题描述**：`ProductionCompletionStrategy.execute()` 不调用 `hasPosted()`，MQ 重发会导致重复入库。

**涉及文件**：
- `ProductionCompletionStrategy.java` — execute 方法开头添加幂等守卫

**修复方案**：
```java
if (hasPosted(SOURCE_DOC_TYPE_WORK_ORDER, woId)) {
    log.warn("工单[{}]已入库，跳过重复处理", woId);
    return;
}
```

---

### C5. setSql 参数化 + MQ 消费者租户上下文 ✅

**问题描述**：
1. `setSql()` 使用字符串拼接，存在 SQL 注入风险
2. `ProductionBackflushConsumer` 和 `StockInConsumer` 缺少租户上下文，多租户环境可能访问错误数据
3. 消息生产端未携带 tenantId

**涉及文件**：
- `ProFlowCardServiceImpl.java` — setSql 改参数化 `{0}`
- `ProDispatchServiceImpl.java` — setSql 改参数化 `{0}`
- `ProductionBackflushConsumer.java` — 添加 `TenantHelper.dynamic`
- `StockInConsumer.java` — 添加 `TenantHelper.dynamic`
- `PrdWorkOrderServiceImpl.java` — 发送消息时设置 tenantId
- `ErpWorkOrderCompletedConsumer.java` — 发送消息时设置 tenantId

**修复方案**：
```java
// setSql 参数化
.setSql("completed_qty = {0}", goodQty)

// MQ 消费者租户上下文
String tenantId = "000000";
if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
    tenantId = String.format("%06d", message.getTenantId());
}
TenantHelper.dynamic(tenantId, () -> { ... });

// MQ 生产者携带 tenantId
completeMsg.setTenantId(Long.parseLong(TenantHelper.getTenantId()));
```

---

## 阶段二：业务正确性 HIGH（待修复）

### H1. 销售订单/采购订单状态转换无 CAS 保护

**问题描述**：所有状态转换使用 SELECT → Java 检查 → UPDATE 模式，并发请求可同时通过。例如两个 `cancel()` 同时执行都会成功。

**涉及文件**：
- `SoSalesOrderHeaderServiceImpl.java` — confirm/fulfill/close/cancel
- `PuPurchaseOrderServiceImpl.java` — submit/approve/close/cancel
- `SoReturnServiceImpl.java` — confirm/execute
- `PuPurchaseReturnServiceImpl.java` — confirm/execute

**修复方案**：
```java
// 改用条件 UPDATE
int rows = baseMapper.update(null, Wrappers.<SoSalesOrderHeader>lambdaUpdate()
    .set(SoSalesOrderHeader::getOrderStatus, STATUS_CANCELLED)
    .eq(SoSalesOrderHeader::getSoId, soId)
    .eq(SoSalesOrderHeader::getOrderStatus, STATUS_CONFIRMED));
if (rows == 0) {
    throw new RuntimeException("状态已变更，当前状态不允许取消");
}
```

---

### H2. MQ 消费者缺少租户上下文（已随 C5 修复部分）

**问题描述**：`ProductionBackflushConsumer` 和 `StockInConsumer` 已修复。需检查其他消费者是否也有此问题。

**待检查**：
- `ReworkStockConsumer.java`
- `SubcontractStockConsumer.java`
- 其他 MQ 消费者

---

### H3. 采购退货库存查询缺少仓库过滤

**问题描述**：`PuPurchaseReturnServiceImpl.postReturnOut()` 查库存时不过滤 `warehouseId`，同物料多仓库时会扣错仓库。

**涉及文件**：`PuPurchaseReturnServiceImpl.java:352-355`

**修复方案**：
```java
lqw.eq(WmMaterialStock::getWarehouseId, purchaseReturn.getWarehouseId());
```

---

### H4. 信用检查并发竞争条件

**问题描述**：`CreditCheckServiceImpl.checkAndFreeze()` 使用 read-then-check-then-insert 模式，两个并发订单确认可同时通过信用额度检查。

**涉及文件**：`CreditCheckServiceImpl.java:44-78`

**修复方案**：用 `SELECT ... FOR UPDATE` 锁定 `bd_client_credit` 行，或用 Redis 分布式锁。

---

### H5. ERP 工单 `complete()` 用计划数量而非实际数量

**问题描述**：直接在 ERP 点完工时，completionQty 默认取 plannedQty。1000 计划只做 800 也会记 1000。

**涉及文件**：`PrdWorkOrderServiceImpl.java:374`

**修复方案**：方法签名增加 `completionQty` 参数，由用户输入实际完工数量。

---

### H6. BOM 损耗率在 `importBomDetails()` 中未应用

**问题描述**：物料需求计算不含损耗率，导致需求偏少、生产时原料不足。

**涉及文件**：`PrdMaterialRequirementServiceImpl.java:308`

**修复方案**：
```java
BigDecimal base = detail.getQuantityPerParent().multiply(totalRequiredQuantity);
if (detail.getLossRate() != null && detail.getLossRate().compareTo(BigDecimal.ZERO) > 0) {
    BigDecimal factor = BigDecimal.ONE.add(
        detail.getLossRate().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
    reqDetail.setRequiredQuantity(base.multiply(factor));
} else {
    reqDetail.setRequiredQuantity(base);
}
```

---

### H7. 需求日期计算反向（算到过去）

**问题描述**：`resolveRequiredDate()` 用 `当前时间 - 提前期`，算出过去的日期。

**涉及文件**：`PrdMaterialRequirementServiceImpl.java:442-448`

**修复方案**：改 `- offsetMillis` 为 `+ offsetMillis`，或接收计划开始日期作为基准。

---

### H8. IQC 不合格不冻结库存

**问题描述**：IQC 判定不合格仅更新收货单状态，不冻结已入库物料。不合格品可被领料或发货。

**涉及文件**：`ErpIqcCompletedConsumer.java:75-101`

**修复方案**：不合格时执行 `SET frozen_flag = 1` 对应库存记录，触发退货流程。

---

### H9. 倒冲服务 TOCTOU — 库存检查含冻结，扣减不含冻结

**问题描述**：`PrdBackflushServiceImpl` 检查可用库存时包含 frozenFlag=1，但实际扣减排除。

**涉及文件**：`PrdBackflushServiceImpl.java:98-119`

**修复方案**：移除预检查，让 `ProductionIssueStrategy` 原子处理；或预检查时加 `frozenFlag=0`。

---

### H10. 报工重复审批导致数量重复累计

**问题描述**：报工审批→驳回→再审批时，数量会重复加到工单。

**涉及文件**：`PrdWorkReportServiceImpl.java:116-128`

**修复方案**：增加 `synced` 标志位，或检查是否已同步过。

---

### H11. 完工入库 workshopId 当 warehouseId 使用

**问题描述**：`ProductionBackflushConsumer` 中 `message.getWorkshopId()` 直接当 `warehouseId`。车间≠仓库。

**涉及文件**：`ProductionBackflushConsumer.java:55-57`

**修复方案**：在 MQ 消息中增加独立的 `warehouseId` 字段，或在消费者中做车间→仓库映射查询。

---

## 阶段三：健壮性 MEDIUM（待修复）

| # | 模块 | 问题 | 文件 | 修复方案 |
|---|------|------|------|---------|
| M1 | SO | 退货执行不检查已确认状态 | `SoReturnServiceImpl.execute()` | 校验 `STATUS_CONFIRMED` |
| M2 | PU | 采购退货执行不检查已确认状态 | `PuPurchaseReturnServiceImpl.execute()` | 校验 `STATUS_CONFIRMED` |
| M3 | SO | 销售订单修改无状态守卫 | `SoSalesOrderHeaderServiceImpl.updateByBo()` | 仅 DRAFT/REJECTED 可修改 |
| M4 | SO | 报价转订单缺少状态校验 | `SoQuotationServiceImpl.convertToSalesOrder()` | 校验 `STATUS_CONFIRMED` |
| M5 | PU | 收货时合格数量预设为到货数量 | `PuPurchaseReceivingServiceImpl:129` | 设为 null 或 0，IQC 完成后回写 |
| M6 | PU | 采购收货回写并发竞争 | `PuPurchaseReceiveWritebackServiceImpl:97-114` | 改用 SQL 原子累加 |
| M7 | PU | 提交方法重复(Flowable vs Legacy) | `PuPurchaseOrderServiceImpl` | 废弃旧 submit 或合并 |
| M8 | PRD | 返工库存消费者只打日志不更新状态 | `ReworkStockConsumer.java:45-57` | 参照 SubcontractStockConsumer 添加状态更新 |
| M9 | PRD | MQ 消息在事务内发送 | `PrdWorkOrderServiceImpl.complete():364-394` | 移到 `afterCommit` 回调 |
| M10 | PRD | 倒冲不幂等，重试会重复扣料 | `PrdBackflushServiceImpl` | 检查是否已有同工单的倒冲记录 |
| M11 | MES | 流转卡/派工编号碰撞风险 | `ProFlowCardServiceImpl`, `ProDispatchServiceImpl` | 用雪花 ID 作后缀替代时间戳 |
| M12 | MES | @Async + DataSource 线程安全 | `MesMqSendService:46-71` | 用 @DS 注解的独立 Service 代理 |
| M13 | MES | 审批路径跳过工序/BOM 快照 | `ProWorkOrderServiceImpl.afterFlowHandle()` | 审批通过后调用 doReleaseAndSnapshot |
| M14 | MES | completeStep 中间态可能不正确 | `ProFlowCardServiceImpl.completeStep()` | 移除 completeStep 中的 card 数量更新，仅在 completeCard 重算 |
| M15 | WM | 库存操作缺少分布式锁 | `WmStockOperationServiceImpl` | 关键方法加 @Lock4j |
| M16 | WM | 返工/委外收货任意选库存 | `ReworkReceiveStrategy`, `SubcontractReceiveStrategy` | 按 warehouseId 过滤 |
| M17 | WM | 生产入库 warehouse=null 歧义 | `ProductionCompletionStrategy:100-118` | 校验 warehouseId 非空 |
| M18 | QC | 质检编号碰撞风险 | `QcIqc/Ipqc/OqcServiceImpl` | 用 Redis INCR 或数据库序列 |
| M19 | QC | IPQC/OQC 结果不影响下游 | `QcIpqcServiceImpl`, `QcOqcServiceImpl` | 添加 MQ 通知到 ERP |
| M20 | PRD | 采购退货库存查询无仓库过滤 | `PuPurchaseReturnServiceImpl` | 已在 H3 描述 |

---

## 阶段四：改进 LOW（待修复）

| # | 模块 | 问题 | 文件 |
|---|------|------|------|
| L1 | MES | ERP 数据源不可用时无优雅降级 | `MesErpBridgeServiceImpl` |
| L2 | PRD | ERP 工单完成消费者不校验可完工状态 | `ErpWorkOrderCompletedConsumer` |
| L3 | IOT | 告警检查只支持上限不支持下限 | `IotAlertChecker` |
| L4 | PRD | 净需求计算不独立考虑损耗率 | `PrdMaterialRequirementServiceImpl`（级联于 H6） |
| L5 | WM | 倒冲创建的领料行缺少状态/数量字段 | `PrdBackflushServiceImpl` |
| L6 | PU | 到货通知无重复创建防护 | `PuPurchaseReceivingServiceImpl` |

---

## 附录：修复优先级建议

### 建议修复顺序

```
阶段二 HIGH（按业务影响排序）：
  H5 → H6 → H8 → H1 → H11 → H3 → H10 → H4 → H7 → H9 → H2

阶段三 MEDIUM（按模块分组）：
  M8 → M9 → M10（MQ 消费者完善）
  M1 → M2 → M3 → M4（状态校验补充）
  M5 → M6 → M7（采购收货修正）
  M11 → M13 → M14（MES 修正）
  M15 → M16 → M17（仓库操作健壮性）
  M18 → M19（质检流程完善）

阶段四 LOW：按需处理
```

### 已修改文件清单（阶段一）

| 文件路径 | 改动类型 |
|---------|---------|
| `ruoyi-modules/ruoyi-erp/.../wm/mapper/WmMaterialStockMapper.java` | 新增 addStock 方法 |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/ProductionIssueStrategy.java` | updateById → deductStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/SalesOutStrategy.java` | updateById → deductStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/TransferStrategy.java` | updateById → deductStock + addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/MiscIssueStrategy.java` | updateById → deductStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/MiscRecptStrategy.java` | updateById → addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/CountAdjustStrategy.java` | updateById → deductStock/addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/ProductionCompletionStrategy.java` | updateById → addStock + hasPosted 幂等 |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/ReceiveInStrategy.java` | updateById → addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/ReworkReceiveStrategy.java` | updateById → addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/SubcontractReceiveStrategy.java` | updateById → addStock |
| `ruoyi-modules/ruoyi-erp/.../wm/service/impl/WmStockOperationServiceImpl.java` | 4 个方法补 @Transactional |
| `ruoyi-modules/ruoyi-erp/.../wm/service/impl/WmSalesIssueServiceImpl.java` | completeSalesIssue 重写 |
| `ruoyi-common/ruoyi-common-mq/.../producer/ReliableMessageProducer.java` | MQ 补偿状态修复 |
| `ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProFlowCardServiceImpl.java` | setSql 参数化 |
| `ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProDispatchServiceImpl.java` | setSql 参数化 |
| `ruoyi-modules/ruoyi-erp/.../mq/consumer/ProductionBackflushConsumer.java` | 租户上下文 |
| `ruoyi-modules/ruoyi-erp/.../mq/consumer/StockInConsumer.java` | 租户上下文 |
| `ruoyi-modules/ruoyi-erp/.../prd/service/impl/PrdWorkOrderServiceImpl.java` | 消息携带 tenantId |
| `ruoyi-modules/ruoyi-erp/.../mq/consumer/ErpWorkOrderCompletedConsumer.java` | 消息携带 tenantId |
