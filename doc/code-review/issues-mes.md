# MES 模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-modules/ruoyi-mes/src/main/java（344 文件）
> 总计: 7 个问题（CRITICAL 0, HIGH 3, MEDIUM 4）

## HIGH

### 1. ProDispatchServiceImpl:258-265 — 最后两张派工单并发完工时，工序永远卡在 IN_PROGRESS
- **问题**: `completeDispatch` 中，派工单 CAS 置 COMPLETED 后执行 `selectCount`（状态 not in (COMPLETED, CANCELLED)），若 activeCount==0 则 `checkAndCompleteStep`。两个不同 dispatchId 的派工单（`@Lock4j(keys = {"#dispatchId"})`，锁键不同，可并发）对同一工序同时完工时，事务 A 快照看不到 B 已提交的 COMPLETED，B 也看不到 A 的——双方都数出 activeCount>0，**双方都跳过 checkAndCompleteStep**。工序、流转卡、工单的完工聚合链中断。
- **影响**: 同一工序多张派工单（多操作员并行生产）最后两单同时完工时，工序卡 IN_PROGRESS、流转卡永不 COMPLETED、工单永不完工、MQ 完工通知不发，需人工干预。
- **建议**: `completeDispatch` 加 `@Lock4j(keys = {"#stepRecordId"})`（与 createDispatch 一致），或将计数改为 SELECT ... FOR UPDATE。

### 2. ProWorkOrderServiceImpl:251-300, 309-359 — release / createFromErpMessage 同名类内部调用使 @Transactional 失效
- **问题**: `release()`（无 @Transactional）内部调用 `this.doReleaseAndSnapshot(...)`；`createFromErpMessage()`（无 @Transactional）内部调用 `this.doCreateWorkOrderAndSteps(...)`——两者都标了 `@Transactional(rollbackFor = Exception.class)`，但**同类内部调用不经过代理，事务注解不生效**，每条语句独立自动提交。
- **影响**: 
  - `release`: CAS 置 RELEASED 后快照中途失败 → 工单已 RELEASED 但工序/BOM 快照不完整，无回滚。
  - `createFromErpMessage`（MQ 路径）: 主记录插入成功、某条工序失败 → 消息 NACK 重试 → 幂等检查跳过 → **残缺工单永久存在**。
- **建议**: `@Transactional` 移到 `release()` / `createFromErpMessage()` 本身，或将快照逻辑拆到独立 @Service 注入。

### 3. IotDataArchiveTask:64-103 — 定时任务缺 TenantHelper.dynamic，多租户隔离失效
- **问题**: 该任务是本模块唯一没有租户遍历/注入的定时任务（对比 EqOeeAggregateTask/EquipmentStatusSyncTask/MaintenanceDueTask 均显式 TenantHelper.dynamic）。SnailJob 线程无 Sa-Token 上下文，tenant_id 为 null → 无租户过滤 → 扫描所有租户数据；插入的 `dc_data_record.tenant_id` 落 DDL 默认值。
- **影响**: 多租户部署下各租户 IoT 数据混入同一租户（000000），数据串租/错归属，SPC 分析读不到自己的数据。
- **建议**: 按 EquipmentStatusSyncTask 模式，先 `push("master")` 查 `selectAllTenantIds()`，再逐租户 `TenantHelper.dynamic(tenantId, ...)` 包裹归档逻辑。

## MEDIUM

### 4. SpcControlChartServiceImpl:435-442 — judgeOoc 按"值相等"定位数据点索引，重复值导致 OOC 规则窗口错位
- **问题**: 用 `getValue().compareTo(value) == 0` 找当前点在序列中的位置而非 dataId/下标。重复测量值时 `idx` 命中首个同值点，RULE2/3/4 滑动窗口在错误位置求值。
- **影响**: 连续 9 点同侧 / 连续 6 点趋势 / 连续 14 点交替判定误报或漏报。
- **建议**: 遍历时直接携带下标传入 judgeOoc，或按 dataId 定位。

### 5. ProFlowCardServiceImpl:252-290 / ProDispatchServiceImpl:222-269 — 报工数量无负值/上限校验
- **问题**: `completeStep(goodQty, defectQty, scrapQty)` 与 `completeDispatch` 对入参数量零校验，负数直接写入并累加进流转卡/工单，`completeWo` 还会通过 MQ 通知 ERP。唯一防线是可选配置的 QUANTITY_REACHED 规则。
- **影响**: 前端绕过校验即可把工单完工数量写成负数/超量，ERP 库存与完工数据失真。
- **建议**: 入口统一校验非负且 `goodQty+defectQty+scrapQty <= plannedQty`。

### 6. EqSparePartServiceImpl:175-222 — 备件出入库 quantity 未校验
- **问题**: `stockTransaction` 对 `bo.getQuantity()` 无校验：TXN_IN+负值 → 库存减少绕过守卫；TXN_OUT+负值 → `.ge(current_stock, qty)` 恒成立 → 库存反而增加；null → NPE。
- **影响**: 携带负数的请求可任意增减备件库存。
- **建议**: 入口校验 `quantity != null && quantity.signum() > 0`。

### 7. LtLotServiceImpl:112-140, 494-529 — 批次创建未校验 quantity
- **问题**: `insertByBo` 对 `bo.getQuantity()` 未做非空/非负校验，下游 `consume`/`release`/`checkLotUsable` 直接解引用：null → NPE；负 → 负剩余批次照常 IN_STOCK 可被 FEFO 推荐。
- **影响**: 漏填数量/填负数的脏数据进入流转，领料环节 NPE 或推荐负剩余批次。
- **建议**: `insertByBo` 校验 quantity 非空且 > 0。
