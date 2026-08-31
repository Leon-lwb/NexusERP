# ERP bd/so/pu/prd/sch/mq 模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-erp 除 wm/fin 外（bd/so/pu/prd/sch/mq/po/config）
> 总计: 17 个问题（CRITICAL 0, HIGH 9, MEDIUM 8）

## HIGH

### 1. BdSerialNumberRuleServiceImpl.java:140-178 — 序列号生成并发重复
- **问题**: generateSerialNumber 用 synchronized + @Transactional 组合。事务在方法返回后才提交而锁在返回时释放——单机并发 T2 在 T1 提交前读旧 currentSeq；双机集群 synchronized 完全无效。
- **影响**: 并发/集群下重复 SN/条码/批次号，产品标识冲突。
- **建议**: 原子 SQL（UPDATE SET current_seq = current_seq + 1 后按新值格式化）或 Redis 分布式锁（Lock4j）。

### 2. BdSerialNumberRuleServiceImpl.java:180-197 — 序列号周期重置逻辑失效（用"今天"比"今天"）
- **问题**: currentDateSegment（dateFormat 格式化今天）与 prevDatePart = buildCycleDatePart(cycle)（也用今天）比较，两侧都是当前日期，永远无法感知跨周期。格式一致 → 永不重置；不一致（如 "260801" vs "20260801"）→ 每次调用重置为 0，当天每次生成都是序号 1，序列号必然重复。
- **影响**: 配置了周期重置（DAY/MONTH/YEAR）的规则生成重复编号。
- **建议**: 在规则表持久化上次生成日期，与当前日期段比较决定是否重置。

### 3. mq/consumer/ReworkStockConsumer.java:716-729 + SubcontractStockConsumer.java:878-901 — MQ 消费者缺 TenantHelper.dynamic，跨租户读写
- **问题**: 两消费者直接 selectById/updateById 未用 TenantHelper.dynamic。MQ 线程无登录态 → tenant 为 null → ignoreTable 返回 true（跳过租户过滤）。同目录其他消费者均正确包裹。
- **影响**: 消息按全局主键命中其他租户同 ID 行并更新状态（跨租户写入）；行不存在则静默跳过消息被 ACK，业务状态永不更新。
- **建议**: doProcess 内用 TenantHelper.dynamic(message.getTenantId(), ...) 包裹。

### 4. mq/consumer/StockInConsumer.java:797-807 — writeback 消息未携带 tenantIdStr，下游无租户上下文
- **问题**: 构造 PurchaseReceiveWritebackMessage 只 setTenantId(Long)，而 PurchaseReceiveWritebackConsumer.doProcess 用 getTenantIdStr()，null 时走"无租户上下文执行"分支 → ignoreTable=true 无租户条件。
- **影响**: 多租户下采购收货回写命中/写入其他租户 PO 行；单租户下回写也脱离租户隔离。
- **建议**: 发送时同步设置 tenantIdStr，去掉无租户降级执行分支。

### 5. PuPurchaseReceiveWritebackServiceImpl.java:71-105 — 收货回写不幂等，重复消费双加已收数量
- **问题**: writeBackReceivedQty 对每条收货行 received_quantity = received_quantity + qty，无防重。BaseMessageConsumer 在 doProcess 成功后才写幂等 key，崩溃窗口重投/重发 StockInMessage → 重复累加。
- **影响**: PO 行超收（OVER_RECEIVED）、订单头状态错乱、付款/三单匹配/供应商评估失真。
- **建议**: 回写前检查收货单回写状态（wm_material_receive 加 writeback_flag 或按 receiveId 幂等键）。

### 6. mq/consumer/ProductionBackflushConsumer.java:536-548 — 倒冲与成品入库分属两个事务，失败重试双倒冲
- **问题**: doProcess 依次调 backflushOnCompletion（独立事务提交）和 createAndExecute（另一独立事务）。成品入库失败 → NACK requeue → 重试时倒冲再次执行 → 物料二次扣减、再生成一张倒冲单。
- **影响**: 生产完工链路部分失败时材料库存重复扣减、倒冲单翻倍。
- **建议**: 以 (woId, completionQty)/messageId 做幂等，或两次调用放入同一事务。

### 7. pu/task/VendorEvaluationTask.java:37-47 + PuVendorEvaluationServiceImpl.java:167-181 — 定时任务无租户上下文
- **问题**: batchCalculateAll() 全程无 TenantHelper.dynamic。SELECT 在 tenant=null 时跳过租户过滤跨租户读；INSERT pu_vendor_evaluation 租户列 NULL → NOT NULL 约束每次失败被 try/catch 吞掉，任务显示成功但数据从未生成。
- **影响**: 多租户评估数据串租户；单租户评估结果也写不进去，功能实际不可用。
- **建议**: 遍历 sys_tenant 逐个 TenantHelper.dynamic 包裹。

### 8. so/service/impl/SoReturnServiceImpl.java:311-325 — 退货新建库存未 setVersion(0)
- **问题**: postReturnStock 库存不存在时 new WmMaterialStock 直接 insert，未 setVersion(0)。插入后 version=NULL，后续乐观锁更新 WHERE version=NULL 永不匹配 → "库存并发冲突"或静默失败。
- **影响**: 客户退货入库时物料该仓库首次建库存，此后该物料出入库/盘点全部失败（已知坑在退货路径的漏网）。
- **建议**: 新建分支补 stock.setVersion(0)。

### 9. pu/service/impl/PuPurchaseRequisitionServiceImpl.java:502-556 — 低库存自动补货申请去重失效
- **问题**: generateFromLowStock 构建 existingRequisitionMap（按 requisitionNo 去重）但**构建后从未被使用**，物料循环中无任何对既有申请的检查，每次执行都为同一批物料生成新申请单。
- **影响**: 定时任务每次运行重复生成补货申请 → 重复采购、库存积压。
- **建议**: 按 materialId 判断是否已有未完结低库存申请行，存在则跳过。

## MEDIUM

### 10. BdBomServiceImpl.java:114 — selectById 对不存在记录抛 NPE（500）
- **问题**: selectVoById null 时 List.of(null) 抛 NPE；BdMaterialServiceImpl 有保护此处缺失。
- **建议**: if (vo != null) enrichBomVo(List.of(vo))。

### 11. PrdWorkOrderServiceImpl.java:502-576 — 工单完工原子更新缺少状态条件，并发双完工
- **问题**: complete() 先读状态校验但 UPDATE WHERE 只有 wo_id 无状态条件；PrdReworkOrderServiceImpl.complete()(168-189) scrap_qty 读改写。
- **影响**: MQ 重投与手动完工并发/双击完工 → completed_qty/scrap_qty 累加两次、两条 ProductionCompleteMessage → 倒冲扣料与成品入库双执行。
- **建议**: UPDATE 加 eq(woStatus, IN_PROGRESS)（rows==0 抛异常）；返工回写 setSql 原子累加。

### 12. PrdProductionPlanServiceImpl.java:388-414 — 物料需求生成无幂等
- **问题**: generateMaterialRequirementsWithDetails 每次按 BOM 全量重建，无"已生成过"检查。
- **影响**: 同一计划生成多套物料需求，后续生成工单/采购重复。
- **建议**: 生成前按 planId 检查已有需求单，先删旧或拒绝。

### 13. SoReturnServiceImpl.java:297-360 — 退货执行并发/重复：读改写丢失更新
- **问题**: execute() 头状态守卫仅预读，UPDATE 无状态条件；postReturnStock 对已有库存读改写，版本冲突返回 0 被忽略流水照写。
- **影响**: 并发双击"执行退货入库"库存多加、流水与库存不一致。
- **建议**: 头状态条件 UPDATE，库存 setSql 原子累加。

### 14. PuPurchaseReturnServiceImpl.java:191-219,364-409 — 采购退货执行并发重复 + 扣库存无仓库维度
- **问题**: execute() 状态守卫仅预读；postReturnOut 查库存不带 warehouseId，读改写丢失更新。
- **影响**: 并发退货执行库存双扣、红字发票重复生成；多仓库时从错误仓库扣料。
- **建议**: 状态条件更新防重；库存按仓库过滤 setSql 原子扣减带 stock_quantity >= qty 守卫。

### 15. PuInvoiceServiceImpl.java:709-738 — 多发票覆盖同 PO 行的退货进项转出重复红冲
- **问题**: redFlushForReturnByPoLines 把同一 poLineReturnQty 传给每张覆盖该行的蓝字发票，内部只按本行已开票数量封顶（代码自认 TODO）。发票 A 100 + B 100、退货 150 → 各红冲 100 合计 200 > 150，invoiced_qty 冲成负数。
- **影响**: 多发票场景进项转出超冲，已开票数量为负、税额抵扣失真。
- **建议**: 按发票行逐行分配退货数量（先消先冲），累计不超退货总量。

### 16. CreditCheckServiceImpl.java:66-99 — 信用冻结非幂等，重复确认重复占额
- **问题**: checkAndFreeze 不检查该 soId 是否已有 FROZEN 记录；confirm 允许 DRAFT 或 APPROVED 状态，审批通过后再确认插第二条 FROZEN → 额度双占。
- **影响**: 客户可用信用额度重复占用，后续订单误判"额度不足"。
- **建议**: 插入前按 (soId, status=FROZEN) 去重或唯一约束。

### 17. SoSalesOrderHeaderServiceImpl.java:291-317,343-415 — 状态流转读改写非原子
- **问题**: confirm/fulfill 状态守卫都是预读（UPDATE 无状态条件）：并发双击 confirm → 两份生产计划、两次信用冻结；并发 fulfill → 双次 unreserve/双次事件。updateByBo(249-256) 无状态限制，已确认订单可编辑删行。
- **影响**: 并发确认/履行产生重复计划/事件；确认后订单仍可改行导致计划 orderLineId 悬空。
- **建议**: 状态流转条件 UPDATE（WHERE 带当前状态 rows==0 抛异常）；updateByBo 加状态校验。

### 18. SoInvoiceServiceImpl.java:149-246 + PuInvoiceServiceImpl.java:259-358 — 按单生成发票草稿并发/重复触发双开票
- **问题**: 计算可开票量是"读快照→插入→setSql 累加"check-then-insert 非原子，并发调用都读到旧 invoiced_qty → 两张相同金额草稿。
- **影响**: 重复开票草稿，需人工核对删除。
- **建议**: DB 条件更新门禁（invoiced_qty + ? <= delivered_qty rows==0 放弃）或 (soId, 状态) 唯一约束。

### 19. PuContractServiceImpl.java:315-376 — 合同生成采购订单允许重复生成
- **问题**: APPROVED 或 EXECUTING 均可生成，生成后置 EXECUTING（EXECUTING 再次允许）——同一合同反复生成多张采购订单。
- **影响**: 重复点击/调用生成多张相同内容的采购订单。
- **建议**: 生成后置不可再生成状态，或按 contractId 检查已有未作废订单。
