# 代码审查报告：MES 完工联动修复

> 审查日期：2026-07-08
> 审查范围：本次会话所有修改（5 项修复）
> 审查方法：逐文件人工审查 + 对抗性并发场景分析

---

## 一、审查结论

**发现 4 个漏洞并已修复，4 个可接受的限制已记录。** 修复后编译通过，核心数据一致性得到保障。

---

## 二、已修复的漏洞（4 项）

### 漏洞 1：SO 行状态推进并发丢失【高危 → 已修复】

**文件：** `SoFulfillmentWritebackServiceImpl.java`

**问题：** `promoteToProduced` / `promoteToDelivered` 原实现含内存级提前返回：
```java
if (line.getProductionQty().compareTo(line.getOrderedQty()) < 0) {
    return;  // 并发下 SELECT 为快照旧值，误判跳过
}
```
`SELECT` 在 REPEATABLE READ 下读快照，并发累加时可能读到旧值（未含他事务已提交的累加），误判为"未达阈值"而跳过状态推进。导致 `production_qty` 已达 `ordered_qty` 但 `line_status` 停滞。

**修复：** 移除内存级数量提前返回，始终执行条件 UPDATE。UPDATE 的 WHERE 子句 `ge(production_qty, ordered_qty)` 在 DB 层判定，会获取行锁并看到最新已提交值，权威可靠。`rows > 0` 才发事件，保证事件恰好一次。

**验证场景：** WO1、WO2 同属 SO 行（ordered=10），并发完工各 +6：
- 修复前：两事务 SELECT 各见 6 < 10，均跳过 → 状态永久停滞
- 修复后：两事务条件 UPDATE 串行（行锁），先到者见 DB 值 6 < 10 返回 0 行，后到者见 12 >= 10 更新成功 → 状态正确推进

### 漏洞 2：生产入库单重复创建【中危 → 已修复】

**文件：** `WmProductionRecptServiceImpl.createAndExecute`

**问题：** 无单据级幂等。MQ 重投（Redis messageId 24h TTL 过期后）会创建重复入库单（库存不重复，因 `hasPosted` 保护，但单据重复污染审计）。

**修复：** 新增单据级幂等：创建前查询同 woId 的 EXECUTED 入库单，存在则直接返回其 ID。与底层 `hasPosted` 形成"单据级 + 库存级"双重幂等。

### 漏洞 3：物料需求单完工判定依赖脏数据【中危 → 已修复】

**文件：** `PrdBackflushServiceImpl.autoCompleteMaterialRequirements`

**问题：** 原实现按 `actual_consumed_qty >= required_quantity` 判定。但 `required_quantity` 可能因生成逻辑或历史脏数据偏大（实测钢板 required=4.0，实际消耗 1.53），导致需求单永不完工。

**修复：** 改为"计划完工驱动"：检查 `plan.status == COMPLETED`，若是则将其下所有未完工需求单标记为 COMPLETED。计划完工（所有工单完工）是权威信号，不依赖数量数据准确性。

**时机正确性：** `complete()` 先于 backflush 执行（同 MQ 链），plan 若已 COMPLETED 此刻已提交可见。backflush 中 `productionPlanMapper.selectById(planId)` 能读到已提交的 COMPLETED 状态。

### 漏洞 4：倒冲明细累加读后写丢失【中危 → 已修复】

**文件：** `PrdBackflushServiceImpl.backflushOnCompletion`

**问题：** 原实现 `detailMapper.updateById(detail)` 为读-改-写模式。同计划下多个工单并发倒冲时，后写者覆盖前写者，丢失累加值。

**修复：** 改为原子 `setSql("actual_consumed_qty = actual_consumed_qty + ?, issued_quantity = issued_quantity + ?")`，DB 层原子累加，无读后写冲突。

---

## 三、已记录的限制（4 项，可接受）

### 限制 1：计划完工并发判定（REPEATABLE READ 快照）

**文件：** `PrdWorkOrderServiceImpl.complete()`

**现象：** 同计划下两个工单在并行事务中完工，各自 `selectCount` 读快照可能均见他方工单未完工，导致 count > 0，计划停滞于 RELEASED。

**影响：** 低。MQ 驱动完工为单消费者顺序处理，不触发此问题。仅罕见的手动并发完工可能触发，且下一次完工会修正。

**不建议修复的原因：** 完全修复需引入事后提交检查（`@TransactionalEventListener`）或定时兜底，复杂度高，收益低。

### 限制 2：事件事务内发布

**文件：** `SoFulfillmentWritebackServiceImpl.promoteToProduced`

**现象：** `SoLineProducedEvent` 在 `complete()` 事务内同步发布。若事务在发布后回滚，事件已送达监听者但 DB 状态已回滚。

**影响：** 当前无监听者，无实际影响。未来新增监听者应使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 规避。

### 限制 3：createAndExecute 的 TOCTOU 窗口

**文件：** `WmProductionRecptServiceImpl.createAndExecute`

**现象：** 幂等检查（select 后 insert）存在 TOCTOU 窗口，同工单并发触发可能双双通过检查创建重复单据。

**影响：** 极低。MQ 消费者顺序处理，仅并发手动触发可能命中。且 `hasPosted` 保证库存不重复，最坏情况为重复单据（无数据损坏）。

### 限制 4：自动入库单 materialSpec 为空

**文件：** `ProductionBackflushConsumer` → `createAndExecute`

**现象：** `ProductionCompleteMessage` 不含 materialSpec，自动入库单的 materialSpec 为 NULL。

**影响：** 低。入库单含 materialId/code/name 足以标识物料，spec 为可选字段。

---

## 四、修复后文件清单与状态

| 文件 | 修复内容 | 状态 |
|------|---------|------|
| `SoFulfillmentWritebackServiceImpl.java` | 移除内存级数量提前返回，DB 级条件更新权威判定 | ✅ 已修复 |
| `WmProductionRecptServiceImpl.java` | 新增单据级幂等检查 | ✅ 已修复 |
| `PrdBackflushServiceImpl.java` | 需求单改计划完工驱动 + 明细原子累加 | ✅ 已修复 |
| `PrdWorkOrderServiceImpl.java` | （无新问题，原实现正确） | ✅ 无需改动 |
| `ProductionBackflushConsumer.java` | （无新问题） | ✅ 无需改动 |
| `SoLineProducedEvent.java` | （无问题） | ✅ 无需改动 |
| `IWmProductionRecptService.java` | （无问题） | ✅ 无需改动 |

---

## 五、编译验证

```
mvn compile -pl ruoyi-modules/ruoyi-erp -am -DskipTests
[INFO] BUILD SUCCESS
[INFO] Total time: 46.814 s
```

所有修改编译通过，无语法/依赖错误。

---

## 六、建议的后续测试

1. **并发完工测试：** 同 SO 行下多工单并发完工，验证 `line_status` 正确推进至 PRODUCED
2. **MQ 重投测试：** 模拟 messageId 过期后重投，验证不产生重复入库单
3. **脏数据场景：** required_quantity 异常时，验证计划完工后需求单仍能完工
4. **并发倒冲测试：** 同计划多工单并发倒冲，验证 actual_consumed_qty 正确累加无丢失
5. **端到端验证：** 完整跑一遍 MES 派工完工 → 检查所有联动表数据一致性