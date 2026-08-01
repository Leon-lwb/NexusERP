# ERP wm 仓储模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-modules/ruoyi-erp/wm（262 文件）
> 总计: 15 个问题（CRITICAL 3, HIGH 9, MEDIUM 3）

## CRITICAL

### 1. ReworkReceiveStrategy.java:93 — 返工入库幂等键错配，重复入库无防护
- **问题**: 幂等检查查 `"PRD_REWORK_ORDER_RECEIVE"`，但插入流水用 `"PRD_REWORK_ORDER"`（141 行）——检查永远不命中。且 `ReworkIssueStrategy`（88 行）幂等检查 `"PRD_REWORK_ORDER"` 与返工入库流水类型相同——先入库后出库时出库被误判为"已过账"整单跳过。
- **影响**: 双击/MQ 重投/重试 → 返工入库量重复累加（双倍库存、成本翻倍）；返工领料在入库后被跳过不扣库存。
- **建议**: 出库/入库用不同 sourceDocType 常量（PRD_REWORK_ISSUE / PRD_REWORK_RECEIVE），幂等检查与插入用同一类型，加 (source_doc_type, source_doc_id) 唯一约束兜底。

### 2. WmStockTakingPlanServiceImpl.java:238 — 盘点差异过账双通道无幂等，库存被重复调整
- **问题**: `postAdjustment(planId)`（控制器）与审批回调 `afterFlowHandle` → `executeCountAdjust`（353-356 行）两条入口，都不检查行已 POSTED，`CountAdjustStrategy` 内部无 hasPosted 防护。
- **影响**: 审批通过后再点"过账"或两次调用 → 盘盈/盘亏金额双倍/多次计入。
- **建议**: 只留一条入口；过账前条件更新抢占 `SET status='POSTED' WHERE status<>'POSTED'`，跳过已过账行。

### 3. WmBatchServiceImpl.java:261 — 批次号用 JVM 静态计数器生成，集群/重启后编码重复
- **问题**: `static AtomicInteger dailySequence` + volatile lastDateStr，进程内唯一、跨实例不唯一、重启重置。`wm_batch.batch_code` 是全局唯一索引 uk_batch_code。
- **影响**: 双机集群两实例同日生成相同批次号 → DuplicateKeyException 建批失败；同日内重启复现；多租户互相冲突。
- **建议**: 改用数据库行锁 / SELECT FOR UPDATE / AutoCodeUtil 生成。

## HIGH

### 4. WmMaterialReceiveExecutorServiceImpl.java:94 — 入库/出库过账 check-then-act，单据状态无乐观锁
- **问题**: executeReceive / WmTransferServiceImpl.execute(184) / WmMiscIssueServiceImpl.execute(146) / WmMiscRecptServiceImpl.execute(148) / WmSalesIssueServiceImpl.completeSalesIssue(216) / WmProductionRecptServiceImpl.execute(144) 均为"读状态→过账→updateById 置终态"，无 @Version，无条件更新；hasPosted 检查并发不可见。
- **影响**: 并发双击 / 用户点击+MQ 消费者同时到达 → 库存重复加减、流水重复。
- **建议**: 状态流转改条件更新（影响行数 0 即并发失败回滚），或加 @Version。

### 5. WmMaterialReceiveExecutorServiceImpl.java:444 — 库存转储 read-modify-write 非原子，乐观锁冲突被静默吞掉
- **问题**: `processStockTransfer` 虚拟库位扣减与目标累加"读出→改→updateById"，冲突返回 0 行不抛异常；数量不足守卫基于过期快照；目标"不存在则新建"并发时插两条同维度行。
- **影响**: 并发收货/上架时虚拟/目标库位数量不一致、重复库存行，库存总账虚高。
- **建议**: 原子 SQL（SET stock_quantity = stock_quantity - ? WHERE ... AND version=?），0 行抛并发异常；目标新建用原子 upsert。

### 6. TransferStrategy.java:85 — 调拨源/目标库存查询不按仓库过滤
- **问题**: 源/目标查询只按 materialId/batchId/locationId，未带 warehouseId；fromLocationId 为空时 FIFO 从全公司仓库扣减；新建目标库存复制来源行维度（127-149 行）→ "来源仓库+目标库位"矛盾维度。
- **影响**: 跨仓调拨从错误仓库扣库存、目标库存维度错乱，汇总与拣货失真。
- **建议**: 按 fromWarehouseId/toWarehouseId 过滤；目标库存用调拨单头/目标库位主数据填充维度。

### 7. WmSalesIssueServiceImpl.java:276 — 销售出库（生效路径）不消费预留，预留永久挂账
- **问题**: `completeSalesIssue` 逐行 FIFO 扣减 stock_quantity 但不扣 reserved_quantity、不回写 SO 行 reserved_qty；与 SalesOutStrategy 行为不一致；postSalesIssue 无调用者（死代码）。
- **影响**: 出库后 reserved_quantity 保持原值 → 可用量为负、其他订单无法预留，预留永久锁死。
- **建议**: 统一走 SalesOutStrategy，或同步原子扣减 reserved_quantity 与 SO 行 reserved_qty。

### 8. WmStockReservationServiceImpl.java:99 — 释放预留遇版本冲突静默跳过
- **问题**: `unreserve()` releaseReserved 返回 0 行时不重试不抛异常；`reserve()` 冲突只 log.warn 返回部分预留。
- **影响**: 出库/并发先改了库存行 → 释放永远失败，reserved 卡死、可用量长期为 0；调用方拿到静默部分预留。
- **建议**: 冲突重读重试（有限次）；仍失败抛 ServiceException 回滚；预留不足显式报错。

### 9. WmSnServiceImpl.java:202 — SN 批量生成读最大值+无锁插入，并发重复
- **问题**: `batchGenerate` 查当天最大 sn_code 再自增批量插入，无锁；两个并发请求读到相同最大值，生成重叠编码；uk_sn_code 唯一索引。
- **影响**: 并发批量生成 → DuplicateKeyException，SN 生成失败。
- **建议**: SELECT FOR UPDATE 行锁或唯一约束+重试。

### 10. ProductionIssueStrategy.java:87 — 出库链路全部忽略预留数量，预留机制可被绕穿
- **问题**: ProductionIssueStrategy(87-96) / MiscIssueStrategy(117-129) / TransferStrategy(85-92) / ReworkIssueStrategy(94-98) / completeSalesIssue(250-259) 扣减查询只过滤 frozen_flag 与 stock_quantity > 0，不排除已预留量（仅 FifoPickingStrategy.allocateStocks 用 available）。
- **影响**: 已为销售订单预留的库存被生产领料/杂项/调拨/返工领料扣走 → reserved > stock、可用量负，预留承诺落空。
- **建议**: 非销售出库按 (stock - reserved) > 0 过滤且按可用量扣减，或同步原子扣减 reserved。

### 11. StockAlertTask.java:30 — SnailJob 定时任务无租户上下文
- **问题**: 直接调 checkStockAlerts()，无 TenantHelper.dynamic；任务线程无登录态，只查/写默认租户。
- **影响**: 多租户下仅 000000 租户物料生成预警，其他租户预警缺失。
- **建议**: 任务内按租户列表循环 TenantHelper.dynamic(tenantId, ...)。

### 12. ReworkReceiveStrategy.java:126 — 返工入库新建库存无仓库维度，INSERT 违反 NOT NULL
- **问题**: 新建分支 `stock.setWarehouseId(...)` 从未赋值；wm_material_stock.warehouse_id NOT NULL → MySQL 严格模式 INSERT 报 "cannot be null"；新行无库位/批次维度，stockList.get(0) 未排序任意选行。
- **影响**: 返工物料首次入库必失败；已存在库存时入库叠加到任意批次。
- **建议**: 返工入库补充仓库/库位/批次维度，新建时完整填充；多库存按 FIFO 匹配。

## MEDIUM

### 13. PickingResolutionServiceImpl.java:131 — 拣货/上架规则无通用规则时兜底选中其他类别专用规则
- **问题**: `findMatchingRuleGlobal` 类别过滤失败后 `orElse(rules.get(0))` 返回最高优先级规则，可能绑定其他物料类别仓库；PutawayStrategyServiceImpl 同问题。
- **影响**: 物料被导向错误仓库/库位。
- **建议**: 兜底只匹配 materialCategoryId == null 通用规则，无匹配返回 null 报错。

### 14. SalesOutStrategy.java:211 — 库存流水 txQuantity 误用整行出库数量
- **问题**: buildSalesOutStockTx 把整行 line.getQuantityIssue() 写入每条流水，一行跨多批次拆分时各流水都等于行总量。
- **影响**: 流水与库存变动对不上，对账必报差异。
- **建议**: 传本次实际扣减的 deductQty。

### 15. CountAdjustStrategy.java:99 — 盘点调整流水 afterQty 与库存实际变动不一致
- **问题**: afterQty = bookQuantity + diffQuantity（快照推算），盘点行生成后、过账前库存发生出入库则对不上（beforeQty 当前值、afterQty 快照值）。
- **影响**: 盘点过账流水前后数量断裂，对账伪差异。
- **建议**: afterQty 用原子更新后的实际值。

### 16. WmProductionRecptServiceImpl.java:211 — 完工入库幂等假设"一工单一次完工"，增量完工数量会丢
- **问题**: createAndExecute 按 woId+EXECUTED 去重，ProductionCompletionStrategy.hasPosted(woId) 按工单整体去重；MES 多次完工消息（分次/重投且数量不同）第二次直接跳过。
- **影响**: 增量完工/重复完工时成品入库量低于实际，库存短缺。
- **建议**: 幂等键改 (woId, materialId)，消息累计数量大于已入库则补差额。
