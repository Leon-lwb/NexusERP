# ERP 仓储「库存内核」P0 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-erp-wm-a.md` 的 ERP-WM-01 / 02 / 03 / 09 / 06 / 08 | 分支: `feature/erp-mes-iot-audit-fix`
> 执行规范: `doc/audit/2026-09-20-修复执行规范.md`（§1 文件所有权 / §3 surefire 与 `@Tag` / §4 交付物结构）
> 范围: B-wm 批次（库存内核 P0 三条 + WM-09 行级高价值 + WM-06/08 时间允许项）

---

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **ERP-WM-01** (P0) | **采用方案②**（原子 SQL）：`issueStockCost` 去掉「用扣减后余量校验 `outQty > qty`」的 Java 侧校验，改为 `WmMaterialStockMapper.issueStockCost` 原子相对扣减 `total_cost = GREATEST(IFNULL(total_cost,0) - IFNULL(unit_cost,0)*outQty, 0)`；数量充足性由 `deductStock*` 的 `stock_quantity >= deductQty` 守卫唯一负责。4 处策略调用点（SalesOut / ProductionIssue / MiscIssue / SubcontractIssue）**逐个复核**：均保持「先扣减后结转」，不再有整批出库误判。**选②理由**：方案①（前移调用）会让 `issueStockCost` 内的 `updateById` 先递增 `version`，使调用方手里的 `stock.getVersion()` 立即失效、`deductStock` 必然 0 行，需再引入重读版本的脆弱时序；且成本结转是相对扣减，本就不需要版本闸门（递增 version 反而会让并发中的收货 `updateById` 产生假冲突）。 | `wm/service/impl/MovingAverageValuationServiceImpl.java:103-139`、`wm/mapper/WmMaterialStockMapper.java:120-142` | 否 | `MovingAverageValuationServiceImplTest`（4） |
| **ERP-WM-02** (P0) | 扣减上限统一改为**可用量**：新增 `BaseStockOperationStrategy.availableOf(stock)`（在库−已预留）与原子入口 `WmMaterialStockMapper.deductStockAvailable`（守卫 `(stock_quantity - IFNULL(reserved_quantity,0)) >= deductQty AND frozen_flag = 0 AND version = ?`）。四处口径统一：`ProductionIssueStrategy`（含 `executeAllocated` 倒冲）、`MiscIssueStrategy`、`TransferStrategy`（来源侧）、`SubcontractIssueStrategy`（自动分配 + 显式分配明细）全部 `deductQty = available.min(remaining)` 并走 `deductStockAvailable`。`SalesOutStrategy` 改为 `maxDeduct = (stockQty - stockReserved) + min(stockReserved, remainingConsumeReserved)`：**有 SO 预留时仍允许消费本单自己的预留（E8 主链路不变）**，无 SO 行的手工销售出库 `remainingConsumeReserved = 0` → 严格不越过可用量。`WmSalesIssueServiceImpl` 同口径，并补上原先缺失的「缺口显式报错」（修复前会静默按部分量出库却把单据置 COMPLETED）。 | `wm/strategy/BaseStockOperationStrategy.java:14-56`、`wm/mapper/WmMaterialStockMapper.java:53-77`、`SalesOutStrategy.java:138-196`、`ProductionIssueStrategy.java:87-120,144-176`、`MiscIssueStrategy.java:84-118`、`TransferStrategy.java:84-118`、`SubcontractIssueStrategy.java:109-176`、`WmSalesIssueServiceImpl.java:249-360` | 否 | `WmIssueStrategyAvailableGuardTest`（3） |
| **ERP-WM-03** (P0) | 出库消耗预留时**同步写 `CONSUME_RESERVE` 流水**（`txFlag=0`，`sourceDocType='SALES_ORDER'`、`sourceDocId=soId`、`sourceDocLineId=SO 行ID`，与被消耗的 `RESERVE` 流水一致）：写入点 `SalesOutStrategy`（新增 `buildConsumeReservedTx`）与 `WmSalesIssueServiceImpl`。`WmStockReservationServiceImpl.unreserve` 改为**净额释放**：按库存行聚合 `SUM(RESERVE) - SUM(CONSUME_RESERVE) - SUM(UNRESERVE)`，净额 ≤ 0 直接跳过（幂等，可重复调用），净额 > 0 才 `releaseReserved`。**`SoSalesOrderHeaderServiceImpl` 未改动**：其 `:376`(delete)/`:537`(fulfill)/`:622`(close)/`:647`(cancel) 四处 `unreserve("SALES_ORDER", soId, null)` 在净额语义下已天然安全——已出库订单净额为 0 → no-op，不再释放他人预留、不再抛异常阻断取消/关闭。 | `wm/service/impl/WmStockReservationServiceImpl.java:31-40,82-152`、`SalesOutStrategy.java:169-176,235-252`、`WmSalesIssueServiceImpl.java:305-350` | 否 | `WmStockReservationServiceImplTest`（4）、`WmIssueStrategyAvailableGuardTest#salesIssue_consumingReservation_writesConsumeReserveLedger` |
| **ERP-WM-09** (P1，一行级) | ① `WmMaterialStockServiceImpl` 的**冻结/解冻 3 处条件更新**（`freezeStockByReceiveBatches`、`unfreezeByBatch`、`unfreezeByReceive`）统一增加 `.setSql("version = version + 1")`，使乐观锁真正失效在途旧版本；② 冻结判定下沉到原子 SQL：`deductStock` 与 `deductStockWithReserved` 的 WHERE 均加 `AND frozen_flag = 0`；③ 新增 `deductStockIgnoreFrozen` 供**盘点盘亏**使用（冻结是质量状态不是库存维度，盘点必须能调整冻结行），`CountAdjustStrategy` 已改用它。与 MES 侧刚新增的 `unfreezeByBatch`/`unfreezeByReceive` **保持一致**：未新增重复方法，只在既有实现上加 version 递增。 | `wm/service/impl/WmMaterialStockServiceImpl.java:231-241,255-264,293-301`、`wm/mapper/WmMaterialStockMapper.java:38-51,94-118,144-160`、`wm/strategy/impl/CountAdjustStrategy.java:101-110` | 否 | `WmMaterialStockServiceImplTest`（+2）、`WmMaterialStockMapperSqlTest`（5） |
| **ERP-WM-06** (P1) | ① 新增 `BaseStockOperationStrategy.NOT_EXPIRED_SQL = "(expire_date IS NULL OR expire_date >= CURDATE())"`，并在**全部出库/拣货选行**处应用：`FefoPickingStrategy`（原先只按 `expire_date` 升序 = 把最过期批次排最前）、`SalesOutStrategy`、`ProductionIssueStrategy`、`MiscIssueStrategy`、`TransferStrategy`（来源侧）、`SubcontractIssueStrategy`、`WmSalesIssueServiceImpl`。② 新增 `WmBatchExpiryTask`（`@JobExecutor("wmBatchExpiryTask")` + `@Scheduled(cron="0 30 1 * * ?")` 本地兜底 + `SchedulerFallbackUtils` 防双调度 + `TenantHelper.dynamic` 遍历租户），每日把 `ACTIVE + expiry_date < now` 的批次标记为 `EXPIRED`（复用既有幂等 `IWmBatchService.checkExpiry()`）。 | `BaseStockOperationStrategy.java:18-25`、`FefoPickingStrategy.java:44-48`、4 处策略 + `WmSalesIssueServiceImpl.java:254-257`、`wm/task/WmBatchExpiryTask.java`（新增） | 否 | 见 §2「部分修复」 |
| **ERP-WM-08** (P1) | 代码侧：`ReceiveInStrategy` 新建库存行改为 `try { insert } catch (DuplicateKeyException) { 重查既有行 → 改走 addStock 原子累加 }`，并重算流水 `before/after`，消除 TOCTOU 下「重复行 → 该物料所有收货永久失败」。DDL 侧：唯一键建议写入 `script/sql/update/2026-09-20-wm-core.sql`（含前置检查 SQL、函数索引 `IFNULL(...)` 归一化 NULL 维度、幂等 `information_schema` 守卫、回滚语句）。 | `wm/strategy/impl/ReceiveInStrategy.java:126-160`、`script/sql/update/2026-09-20-wm-core.sql`（新增） | **建议**（见 §4） | 见 §2「部分修复」 |

**改动文件汇总**：主代码 15 个（14 改 + 1 新增 `WmBatchExpiryTask.java`）、增量 SQL 1 个（新增）、测试 5 个（4 新增 + 1 追加）。**未触碰** `wm/controller/**`、`SoSalesOrderHeaderServiceImpl.java`、`script/sql/ry-*.sql`、`application*.yml`、`doc/system/数据库增量变更记录.md`、`plus-ui/**`、`ruoyi-mes/**`、`ruoyi-iot/**`。

---

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| ERP-WM-06 的「过期批次同步冻结对应 `wm_material_stock` 行」 | **未做** | 选行层已用 `NOT_EXPIRED_SQL` 拦截过期批次（真正的"零拦截"缺口已堵），再叠加自动冻结会与 MES 侧 IQC 冻结/解冻链路交叉（同一 `frozen_flag` 字段两个来源、无来源标记），可能造成"解冻后又被效期任务冻结"的抖动。建议单独立项，给冻结加来源字段后再做。 |
| ERP-WM-06 的 `sj_job` 任务定义 | **未做** | `sj_job` 种子在 `script/sql/ry-vue.sql`（§1.1 绝对独占，禁止本代理编辑）。`@Scheduled` 本地兜底已可独立工作（生产 `snail-job.enabled=false` 即生效）；如需 SnailJob 托管，见 §3 主脚本合并清单。 |
| ERP-WM-08 的存量数据去重 | **未做** | 重复行的合并口径（保留哪一行、`stock_quantity`/`reserved_quantity`/`total_cost` 如何归并、另一行软删还是物理删）属业务决策，不能由修复代理擅自决定。增量脚本已给出清点 SQL，**必须人工确认重复行数为 0 后**才执行 ALTER。 |
| ERP-WM-08 的 DDL 未并入 `ry-erp.sql` | **故意** | ① 该文件为独占文件；② 启用唯一键的前提是 **先修 ERP-WM-07**（`TransferStrategy` 目标行查找带 `frozen_flag=0`，冻结行会导致同维度新建第二行），否则调拨入库会直接报重复键。本批次未分配 WM-07，故唯一键只作为增量脚本中的**待执行建议**。 |
| ERP-WM-01 附带的 `updateStockCost` 读-改-写 | **未做** | 审计 §5 第 3 条指出 `updateStockCost`（入库计价）仍是 `selectById` + `updateById` 的绝对写，并发收货存在丢失更新。本次只修 P0 的"数量校验位置错误"，改动它会牵动收货计价口径与 `@Version` 语义，属扩大 diff，建议单独立项。 |
| WM-04 / 05 / 07 / 10 / 11 / 12 / 13~19 | **未做** | 不在本批次任务清单内（批次 B/C 其余项）。 |

### ⚠️ 跨模块影响（请主代理转达）

1. **`deductStock` 新增 `frozen_flag = 0` 守卫会改变非本批次所有权调用方的行为**：
   - `ruoyi-erp/.../pu/service/impl/PuPurchaseReturnServiceImpl.java:371`（采购退货出库）
   - `ruoyi-erp/.../wm/strategy/impl/ReworkIssueStrategy.java:115`（返工出库，wm 内，无需转达）
   - `ruoyi-erp/.../wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java:464`（虚拟库存扣减，虚拟行 `frozen_flag=0`，不受影响）
   语义上"冻结（IQC 不合格）品不得出库"正是 WM-09 的目标行为，但**采购退货若业务上需要退冻结品**，需 pu 负责方改用 `deductStockIgnoreFrozen` 或先解冻。请主代理把这一条转给 pu 批次负责人确认。
2. `SalesOutStrategy` / `WmSalesIssueServiceImpl` 新增的 `CONSUME_RESERVE` 流水使用 `txType='CONSUME_RESERVE'`、`txFlag=0`、`sourceDocType='SALES_ORDER'`。**库存台账报表/对账若按 `tx_type` 白名单过滤**，需把该类型加入展示白名单（未发现按类型硬编码分支，但 `wm/reconciliation` 由另一代理负责，提请复核）。

---

## 3. 主脚本合并清单（目标文件 + 锚点 + 待插入行）

> 本批次**没有必须合并进主脚本的语句**（唯一键建议保留在增量脚本，待前置条件满足后由主代理决定）。

**可选（非必需）** —— 目标文件 `script/sql/ry-vue.sql`（SnailJob 任务定义区）：
- 锚点：现有 `stockAlertTask` 的 `sj_job` 种子插入行（搜索整行文本 `stockAlertTask`）。
- 待插入：一行 `wmBatchExpiryTask` 的 `sj_job` 记录，`executor_name = 'wmBatchExpiryTask'`、`trigger_type = 2`（CRON）、`trigger_interval = '0 30 1 * * ?'`、`job_name` 形如「批次效期过期标记」、其余列（`group_name`/`job_desc`/`route_key`/`block_strategy`/`executor_timeout`/`retry_*`/`status`/`del_flag`/`create_*`）**按锚点行逐列对齐**。
- 说明：本代理**未读取**该区段（避免与主脚本合并冲突），列定义请以锚点行实测为准。`@Scheduled` 兜底已独立生效，此项不做也不影响功能。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更类型 | 变更内容 | 脚本 | 状态 |
|---|---|---|---|---|---|---|
| 2026-09-20 | `ry-erp` | `wm_material_stock` | 新增唯一键（**建议，未执行**） | `UNIQUE KEY uk_stock_dim (tenant_id, material_id, (IFNULL(batch_id,0)), (IFNULL(warehouse_id,0)), (IFNULL(location_id,0)), (IFNULL(vendor_id,0)), (IFNULL(sn_id,0)), del_flag)` —— 对应 ERP-WM-08，MySQL ≥ 8.0.13（函数索引键部分） | `script/sql/update/2026-09-20-wm-core.sql` | **待执行**：需 ① 前置检查确认无重复行并完成去重 ② 先修 ERP-WM-07 ③ 主代理确认后执行 |
| 2026-09-20 | — | — | — | 本批次**无**新增表、无新增/修改字段 | — | — |

> 其余 DDL（`wm_batch`/`wm_sn`/`wm_barcode`/`wm_warehouse` 唯一键补 `tenant_id`，ERP-WM-15）不在本批次范围。
> **无 Redis 缓存需清理**（本次改动不涉及字典/缓存）。

---

## 5. 验证记录

**命令**（照抄修复规范 §3，surefire 3.5.3 实测校正）：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false "-Dtest=MovingAverageValuationServiceImplTest,WmIssueStrategyAvailableGuardTest,WmStockReservationServiceImplTest,WmMaterialStockMapperSqlTest,WmMaterialStockServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**结果**：`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`，`ruoyi-erp` 主代码 962 个源文件编译通过。

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `com.nexus.erp.wm.mapper.WmMaterialStockMapperSqlTest` | 5 | WM-01（成本结转无数量前置校验）、WM-02（可用量守卫）、WM-09（`deductStock`/`deductStockWithReserved` 冻结守卫、`deductStockIgnoreFrozen` 例外） |
| `com.nexus.erp.wm.service.impl.MovingAverageValuationServiceImplTest` | 4 | **WM-01 回归**：整批出库（扣减后余 0）、超过半批出库（扣减后余 40 而出 60）均不再抛「出库数量超过库存数量」；缺行/非正数仍报错 |
| `com.nexus.erp.wm.service.impl.WmMaterialStockServiceImplTest`（追加 2 个用例，共 5） | 5 | **WM-09 回归**：冻结与解冻的 `sqlSet` 必须含 `version = version + 1` |
| `com.nexus.erp.wm.service.impl.WmStockReservationServiceImplTest` | 4 | **WM-03 回归**：全额消耗后释放 0（不触碰 `releaseReserved`）、部分消耗只释放剩余 40、重复调用幂等、多库存行按行净额 |
| `com.nexus.erp.wm.strategy.impl.WmIssueStrategyAvailableGuardTest` | 3 | **WM-02 回归**：生产领料两行可用量 20+50 恰好满足需求 70（第一行绝不扣 70 穿透 80 预留）、手工销售出库不穿透他人预留、**WM-03 写入侧**：消耗预留必写 `CONSUME_RESERVE` 且来源单据与被消耗 RESERVE 一致 |

> 全部新增测试类均带 `@Tag("dev")`（erp 模块继承根 pom `groups=dev`，否则 `Tests run: 0` 假通过）；`WmMaterialStockServiceImplTest` 的 `baseMapper` 按既有惯例用反射注入（`@InjectMocks` 不注入 `ServiceImpl` 继承的 `baseMapper`）；`@BeforeAll` 用 `TableInfoHelper.initTableInfo` 建真实列缓存。

---

## 6. 风险与回滚说明

| 改动 | 改错会怎样 | 回滚方式 |
|---|---|---|
| WM-01 去掉 `issueStockCost` 的数量校验 | 若将来有调用方**在没有先 `deductStock`** 的情况下单独调用它，将不再拦截超发（当前全仓仅 4 处出库策略调用，均在其后）。 | 恢复 `MovingAverageValuationServiceImpl.issueStockCost` 旧实现 + 删除 `WmMaterialStockMapper.issueStockCost` 方法。 |
| WM-02 可用量守卫 | 「账面够但被他人预留」的出库现在**会失败并提示可用库存不足**——这是修复目标，但对操作员是行为变化（原先静默扣穿）。若业务确实需要"生产抢占销售预留"，应实现显式抢占接口，不能靠扣减穿透。 | 把 4 处 `deductStockAvailable` 换回 `deductStock` 并恢复 `deductQty = stockQty.min(remaining)`。 |
| WM-09 `deductStock` 加冻结守卫 | **已冻结库存行的任何出库都会失败**（含采购退货 `PuPurchaseReturnServiceImpl`、返工出库）。需业务先解冻；盘点盘亏已用 `deductStockIgnoreFrozen` 保留例外。 | 去掉 `deductStock`/`deductStockWithReserved` 中的 `AND frozen_flag = 0`；`CountAdjustStrategy` 可保持现状（`deductStockIgnoreFrozen` 与旧 `deductStock` 语义等价）。 |
| WM-03 `unreserve` 净额化 | **对存量单据行为不变**（修复前已出库但未写 `CONSUME_RESERVE` 的历史数据净额仍为全额 → 与旧实现一致），所以历史订单取消仍可能释放他人预留；要彻底修正需一次性补写历史 `CONSUME_RESERVE` 流水（**未做**，见 §2）。新增流水类型若未被台账报表白名单收录会"看不见"。 | 恢复 `unreserve` 的「按 RESERVE 全额释放」实现；`CONSUME_RESERVE` 流水可保留（无害）。 |
| WM-06 效期过滤 | 使用 `CURDATE()`（**MySQL 方言**），若将来移植到其他数据库需改写为参数化时间。副作用：过期批次的出库由"成功"变为"库存不存在/可用不足"报错，操作员需先走报废/退货流程。 | 删除各处 `lqw.apply(NOT_EXPIRED_SQL)` 与 `FefoPickingStrategy` 中的 `apply`；`WmBatchExpiryTask` 可直接停用（删除类或把 cron 关掉）。 |
| WM-08 捕获 `DuplicateKeyException` | 唯一键未启用时该 `catch` 永不触发（无害）；启用后若重查仍为 null（极少见，说明维度不匹配）会原样抛出冲突异常。 | 恢复 `stockMapper.insert(stock)` 单语句。 |

**整体回滚**：所有改动集中在 `ruoyi-erp/.../wm/**`（14 改 + 1 新增）+ 1 个新增 SQL + 5 个测试文件；无强制 DDL 依赖（唯一键为待执行建议），按文件粒度回退即可，无数据迁移不可逆点。
