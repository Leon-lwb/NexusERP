# MES 生产与批次消耗（pro/lt）修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-mes-pro.md` 的 MES-PRO-01/02/04/05/07/09/10 + `doc/audit/2026-09-20-audit-mes-spc-dc-lt.md` 的 MES-LT-01/02/03/06 | 分支: `feature/erp-mes-iot-audit-fix`

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件（方法/位置） | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| MES-PRO-01 | ①新增**手工完工**专用入口 `manualCompleteCard`（Controller `/complete/{cardId}` 改调它）：卡上存在未完成工序时**拒绝**并列出 `序号:工序名(状态)`；②聚合路径 `completeCard` 同样过门禁，未完成工序只记 warn 返回（不误完工）；③完工数量改为取**最后一个 COMPLETED 工序的 good_qty**（不再从"序号最大工序"重算，也不再可能为 0）；④新增 `pro_flow_card.aggregated_flag`，以 CAS `0→1` 抢"一次性入账"权，成功才向工单聚合 `completed_qty/scrap_qty`：重复完工不重复入账（幂等），"工序已全完成而卡曾完工未入账"时重入即补入账（自愈，消除永久卡死） | `ProFlowCardServiceImpl.java`（`completeCard`/`manualCompleteCard`/`doCompleteCard`）、`IProFlowCardService.java`、`ProFlowCardController.java:147`、`ProFlowCard.java`（+`aggregatedFlag`） | **是**（新增列 + 存量订正） | `ProFlowCardServiceImplTest#manualCompleteRejectsWhenStepsPending`、`#completeCardIsIdempotentWhenCardAlreadyBooked`、`#completeCardSelfHealsWhenCardCompletedButNeverBooked` |
| MES-PRO-02 | 卡级锁提升为**程序化锁覆盖整个事务**（照 M1 已修写法）：`completeCard`/`manualCompleteCard`/`checkAndCompleteStep`/`completeStep`/`exit`/`skip` 改为"无事务入口 → `LockTemplate.lock("mes:flowcard:{cardId}")` → `SpringUtils.getAopProxy(this).doXxx(...)` 带事务实现"，锁在事务提交后才释放；`completeDispatch` 入口改为**卡锁 → 步锁**两级加锁（全模块唯一加锁顺序）覆盖 `doCompleteDispatch` 整个事务；`doCompleteDispatch` 改调 `checkAndCompleteStepUnderLock`（不重复取锁），消除"工序全 COMPLETED 而卡不完工"竞态 | `ProFlowCardServiceImpl.java`（`completeStep`/`doCompleteStep`/`checkAndCompleteStep`/`checkAndCompleteStepUnderLock`/`exit`/`doExit`/`skip`/`doSkip`）、`IProFlowCardService.java`、`ProDispatchServiceImpl.java`（`completeDispatch`/`doCompleteDispatch`） | 否 | `ProFlowCardServiceImplTest#completeCardSelfHealsAggregationWhenAllStepsCompletedButCardNotCompleted`（+ 既有 `ProDispatchServiceImpl` 步级锁） |
| MES-PRO-07 | 跳站工序（SKIPPED）不参与卡完工数：升序遍历取**最后一个 COMPLETED 工序**的 `good_qty`，`scrap_qty` 仍为全部工序之和（与 01 合并修） | `ProFlowCardServiceImpl.java`（`doCompleteCard` 步骤3） | 否 | `ProFlowCardServiceImplTest#completedQtyIgnoresSkippedLastStep` |
| MES-PRO-04 | 状态服务端强制：`insertByBo` **忽略入参** `woStatus` 一律置 `CREATED`；`updateByBo` 置空 `woStatus`（MyBatis-Plus NOT_NULL 策略跳过该列），状态只能经 `release/submit/completeWo` 等 CAS 方法变更 | `ProWorkOrderServiceImpl.java`（`insertByBo`/`updateByBo`） | 否 | 未补（见 §2） |
| MES-PRO-05 | `insertByBo/updateByBo` 改经 `SpringUtils.getAopProxy(this).doInsertByBo/doUpdateByBo`，`@Transactional` 生效，杜绝"有头无行"孤儿 MBOM 与"先删后插失败即明细永久丢失" | `MesMbomServiceImpl.java`（+import SpringUtils） | 否 | 未补（见 §2） |
| MES-LT-01 | `consume()` 改**原子 SQL 扣减**：`consumed_qty = consumed_qty + {0}` + 守卫 `consumed_qty + {0} <= quantity`，影响 0 行抛异常；状态流转改为独立语句 `status = CASE WHEN consumed_qty >= quantity THEN 'CONSUMED' ELSE 'IN_USE' END`（消除 MySQL 单条 UPDATE 内 SET 求值顺序歧义）；`@Lock4j` 保留但注释明确"仅为串行化优化，正确性由 SQL 守卫保证，与锁/事务切面顺序无关" | `LtLotServiceImpl.java`（`consume`） | 否 | `LtLotServiceImplTest#consumeUsesAtomicSqlWithQuantityGuard`（断言 SQL 为自增 + 含守卫） |
| MES-LT-02 | `insertByBo` 领料前调用既有 `checkLotUsable(materialId, lotId)`（校验批次物料一致性/QC/效期/剩余量），不可用即拒绝；消耗记录 `materialId` 以批次为准（不信客户端）；新增**超领校验** `assertNotOverPick`：同工单同物料累计领料 + 本次 ≤ `pro_work_order_bom_line.total_quantity`，超限抛"超领"，无 BOM 快照则 warn 跳过 | `LtWoConsumptionServiceImpl.java`（`insertByBo`/`assertNotOverPick`） | 否 | `LtWoConsumptionServiceImplTest#insertByBoRejectsWhenLotCheckNotUsable`、`#insertByBoRejectsOverPickBeyondBomSnapshot` |
| MES-LT-03 | 效期拦截：`consume` 拒绝过期批次；`checkLotUsable` 返回 `usable=false, reason=批次已过期`；`recommendFefo` 查询加 `expiry_date IS NULL OR expiry_date >= 今天零点`（不再优先推荐最过期批次）；口径统一由私有 `isExpired/startOfToday` 保证 | `LtLotServiceImpl.java`（`consume`/`checkLotUsable`/`recommendFefo`/`isExpired`/`startOfToday`） | 否 | `LtLotServiceImplTest#consumeRejectsExpiredLot`、`#checkLotUsableRejectsExpiredLot`、`#recommendFefoQueryExcludesExpiredLots` |
| MES-LT-06 | QC 门禁：`consume` 增加 `qc_status = PASS` 前置校验（FAIL/PENDING 一律拒绝领用）并在原子 UPDATE 的 WHERE 中再带一次 `qc_status=PASS`；`release()` 拒绝 `qc_status=FAIL` 批次放行（提示走特采/让步接收审批） | `LtLotServiceImpl.java`（`consume`/`release`） | 否 | `LtLotServiceImplTest#consumeRejectsFailQcLot`、`#releaseRejectsFailQcLot` |
| MES-PRO-09 | **不重复补**：`mes:pro:trace:query` 权限种子已在 B2 批次补齐，本次未改 `ry-vue.sql` | — | 否 | — |
| MES-PRO-10 | **不重复补**：`pro_*` 唯一索引补 `tenant_id` 已在 DDL 批次完成（实测 `ry-mes.sql:1804` 现为 `uk_card_no(tenant_id, card_no)`），本次未改 | — | 否 | — |

## 2. 未修复 / 部分修复项（含理由与建议）

1. **MES-PRO-07 的"末道工序跳站需补录产出数量"未做**：本次只保证跳站工序不污染完工数（取最后一个 COMPLETED 工序）。若某卡全部工序被跳站（首道工序禁止跳站，实际不可达）则完工数仍为 0；建议后续在 `skip()` 增加"末道工序跳站必须填 `goodQty`"校验（属新增校验，超出本次 P0 范围）。
2. **MES-PRO-04 未删除 `ProWorkOrderBo.woStatus` 字段**：按审计建议的第二方案"服务端强制赋值 + 忽略入参"实现，避免与前端（`plus-ui/**`，非本代理所有权）联动改动；前端仍可提交该字段但服务端忽略。
3. **MES-PRO-04/05 未补单测**：两者为"忽略入参/改代理调用"的 1~3 行级改动，且 `MesMbomServiceImpl` 无既有测试类、`ProWorkOrderServiceImplTest` 的构造依赖较重（本次测试预算优先保障 P0 的四类断言）。建议主代理在最终全量跑测后按需补 `MesMbomServiceImplTest`。
4. **历史 PRO-01 卡死数据需人工处理**：存量 `COMPLETED 且 completed_qty=0` 的卡（曾被手工提前完工）不会被自动自愈，需按 §3 脚本中**注释的可选修复语句**执行后对该卡再点一次「完工」（此时工序已全完成，门禁通过，会按工序实际报工数补入账并重新判定工单完工）。
5. **`entry`/`startStep` 仍用 `@Lock4j`**：二者不参与"工序是否全完成"的聚合判定，未纳入程序化锁改造，保持最小 diff。
6. **超领校验依赖工单 BOM 快照**：`pro_work_order_bom_line` 无对应行（手工工单/非 BOM 物料）时记 warn 跳过，不阻断领料（与审计建议一致）。
7. **未跑全模块全量测试**：按修复规范 §3 由主代理统一执行。

## 3. 主脚本合并清单

**目标文件**：`script/sql/ry-mes.sql`（`CREATE TABLE pro_flow_card` 块，第 1780 行起）

**锚点**（第 1791 行整行文本，注意该文本在 ry-mes.sql 中出现 5 次，必须选 `pro_flow_card` 块内那一处）：
```sql
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
```

**要插入的行**（紧随该锚点之后）：
```sql
  `aggregated_flag` tinyint NOT NULL DEFAULT 0 COMMENT '完工数量是否已一次性入账到工单(0=未入账 1=已入账)',
```

> 合并后建议对存量库执行 §4 增量脚本中的"存量订正 UPDATE"。

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更 | 增量脚本 |
|---|---|---|---|---|
| 2026-09-20 | `ry-mes` | `pro_flow_card` | 新增列 `aggregated_flag tinyint NOT NULL DEFAULT 0`（完工数量一次性入账标记，MES-PRO-01 幂等/自愈） | `script/sql/update/2026-09-20-mes-pro-lt.sql` |

同脚本内含**存量数据订正**（必须执行）：`UPDATE pro_flow_card SET aggregated_flag = 1 WHERE aggregated_flag = 0 AND card_status IN ('COMPLETED','CLOSED');`
—— 防止代码上线后对历史已完工卡再次触发「完工」造成工单完工数量二次累加。另附**可选**修复语句（默认注释）用于 MES-PRO-01 历史卡死数据自愈。脚本幂等、可重复执行，无需清 Redis 缓存。

## 5. 验证记录

命令（按修复规范 §3 实测校正）：
```powershell
mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false "-Dtest=ProFlowCardServiceImplTest,LtLotServiceImplTest,LtWoConsumptionServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

结果（`BUILD SUCCESS`，exit=0）：

| 测试类 | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `ProFlowCardServiceImplTest`（**新增**，含 `@Tag("local")`+`@Tag("dev")`） | 6 | 0 | 0 | 0 |
| `LtLotServiceImplTest`（追加 8 例：原子守卫/效期×2/FEFO 过滤/FAIL×2 等） | 48 | 0 | 0 | 0 |
| `LtWoConsumptionServiceImplTest`（追加 2 例：物料不一致/超领） | 14 | 0 | 0 | 0 |
| **合计** | **68** | **0** | **0** | **0** |

核心断言（修复前会失败）：
- `manualCompleteRejectsWhenStepsPending`：未完成工序时 `manualCompleteCard` 抛 `ServiceException` 且**零 update**（修复前 CAS 命中 CREATED/RELEASED → 卡 COMPLETED、完工数 0）。
- `completeCardSelfHealsAggregationWhenAllStepsCompletedButCardNotCompleted`：工序全完成而卡未完工 → 卡完工数取 10（非 0）、工单 `completed_qty = completed_qty + 10`、触发 `completeWo`（ERP 完工消息）。
- `completeCardIsIdempotentWhenCardAlreadyBooked` / `completeCardSelfHealsWhenCardCompletedButNeverBooked`：已入账不二次聚合；未入账（自愈）补入账。
- `completedQtyIgnoresSkippedLastStep`：末道 SKIPPED 时完工数为 10 而非 0。
- `consumeUsesAtomicSqlWithQuantityGuard`：捕获 `LambdaUpdateWrapper`，断言 `SET` 为 `consumed_qty = consumed_qty + ...` 且 WHERE 段含 `consumed_qty + ... <= quantity` 守卫（修复前为写绝对值 `consumed_qty=?`，无守卫）。
- `consumeRejectsExpiredLot` / `consumeRejectsFailQcLot` / `releaseRejectsFailQcLot` / `checkLotUsableRejectsExpiredLot` / `recommendFefoQueryExcludesExpiredLots`：过期与 FAIL 批次不可领用、不可放行。

## 6. 风险与回滚说明

**风险**
1. **上线顺序**：必须先执行 `script/sql/update/2026-09-20-mes-pro-lt.sql`（新增 `aggregated_flag`）再部署代码，否则 MyBatis-Plus 查询 `pro_flow_card` 会因未知列报错。
2. **存量订正必须执行**：跳过该 UPDATE 时，任何对历史已完工卡的重复「完工」点击会把该卡完工数量**二次累加**到工单（旧逻辑只累加一次，新逻辑靠该标记去重）。
3. **锁行为变化**：`completeDispatch` 现同时取卡锁，卡锁获取超时（5s）时抛"该流转卡有完工处理进行中, 请稍后重试"，高并发完工场景重试失败率略升（换取的是聚合判定正确性）。
4. **`completeCard` 门禁收紧**：设备通道（`ruoyi-station/StationPassService.java:369`，非本代理所有权）在工序未全完成时调用 `completeCard` 现在**不再误完工**（仅 warn 返回），后续工序完工时由 MES 路径正常聚合；若该通道存在"依赖 completeCard 强行收口"的用法需回归验证。
5. **`consume` 新增 QC/效期前置校验**：`qc_status` 为空（PENDING）的历史批次将无法领用，属预期收紧；存量脏数据（`qc_status IS NULL`）需先补判定。
6. **`updateByBo` 不再写 `wo_status`**：前端若曾用编辑接口改状态（含"假装取消"）将失效（正是修复目标），需要取消/关闭能力时应按 MES-PRO-11 补专有端点。

**回滚**：还原以下文件即可（DDL 列可保留，旧代码不读该列；存量订正 UPDATE 无需回滚）：
- 主代码：`ProFlowCardServiceImpl.java`、`IProFlowCardService.java`、`ProFlowCardController.java`、`ProFlowCard.java`、`ProDispatchServiceImpl.java`、`ProWorkOrderServiceImpl.java`、`MesMbomServiceImpl.java`、`LtLotServiceImpl.java`、`LtWoConsumptionServiceImpl.java`
- 测试：`ProFlowCardServiceImplTest.java`（删除）、`LtLotServiceImplTest.java`、`LtWoConsumptionServiceImplTest.java`
- 脚本：`script/sql/update/2026-09-20-mes-pro-lt.sql`（保留但不再执行）
