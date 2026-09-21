# MES PDA / 跨模块 MQ 契约修复报告（2026-09-20）

> 对应审计: doc/audit/2026-09-20-audit-mes-pda-rel.md 的 MES-MQ-01/02、MES-PDA-01/02/03/04/05、MES-REL-01（附带 MES-PDA-09、MES-BRG-01） | 分支: feature/erp-mes-iot-audit-fix
> 行号为首行近似值（改动后文件）

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| MES-MQ-01① | 生产端消息携带真实 `tenantId`：优先取工单行自身 `tenant_id`（权威值），回退线程租户上下文，均无法解析时记 error 并返回 null（交由消费端 fail-closed） | `PrdWorkOrderServiceImpl.java:737-767`（新增 `resolveMessageTenantId`） | 否 | `BaseMessageConsumerTenantTest#resolveTenantIdFormatsRealTenant` |
| MES-MQ-01② | 消费端**不再静默降级**：`resolveTenantId` 缺租户时先走新钩子 `resolveMissingTenantId`，仍无法解析则抛 `ServiceException` → `processMessage` 捕获后重投/进死信，**绝不落到 000000** | `BaseMessageConsumer.java:59-100` | 否 | `#resolveTenantIdThrowsWhenTenantIdIsNull`、`#resolveTenantIdThrowsWhenTenantIdIsZero`、`#resolveTenantIdUsesSubclassFallbackBeforeFailingClosed` |
| MES-MQ-01③ | 桥接查 ERP 主数据：入口断言租户上下文非空（blank 即抛可读 `ServiceException`）；系统租户 `000000` 下主数据为空时显式报错，杜绝“无单位、无工序工单”静默产生 | `MesErpBridgeServiceImpl.java`（全 12 个方法 + `assertTenantContext`/`failIfSystemTenantEmpty`） | 否 | 未单测（理由见 §2-6） |
| MES-MQ-01 同型 | `WmMaterialReceiveCrudServiceImpl` 改用收货单自身 `tenant_id`（回退线程上下文）；仍缺失时不投递并记 error（不再 `tenantId=0` 制造必然死信） | `WmMaterialReceiveCrudServiceImpl.java:694-712` | 否 | — |
| MES-PDA-01 | `bind()` 按 `snCode` 反查 `ProSn` 写入 `snId`（修复 SQL 1364）；SN 不存在即 `fail`；同批次重复绑定幂等返回成功、跨批次拒绝；并发由 `uk_sn_material` 兜底 `DuplicateKeyException` 幂等处理 | `PdaSnServiceImpl.java:53-110` | 否 | `PdaSnServiceImplTest`（5 用例，含断言 `getSnId()==9001`） |
| MES-PDA-05 | 去掉 `PdaReportServiceImpl` 外层 `@Transactional`（事务归 `IProDispatchService` 各方法），可预期失败改为**业务校验前置**，内层异常在事务外捕获并转可读 `MesActionResultVo.fail`；不再出现提交期 `UnexpectedRollbackException`(500) | `PdaReportServiceImpl.java:70-160` | 否 | `PdaReportServiceImplTest`（5 用例，含“不抛异常且消息可读”） |
| MES-PDA-04（部分） | PDA 侧报工数量前置校验：非负、合计 > 0、**超报上限**（合计 ≤ 计划数）返回可读原因；少报仍由派工一次性完工模型拒绝并回传可读原因 | `PdaReportServiceImpl.validateQuantities` | 否 | `#completeReturnsReadableFailureWhenOverReported` |
| MES-PDA-02 | 幂等键改为 `pda:idem:{tenantId}:{userId}:sha256(方法+URI｜幂等标识)`；幂等标识优先 `X-Idempotent-Key`，**缺失时以端点+请求参数指纹兜底**；同租户不同用户/不同端点不再互相吞掉 | `PdaIdempotentAspect.java:47-95` | 否 | 未单测（切面需 Servlet/Redis 上下文，见 §2-6） |
| MES-PDA-03 | PENDING/DONE 状态机：执行前置 PENDING 占位，**业务成功提交后**才置 DONE；命中 DONE 返回重放成功，命中 PENDING 返回可读失败（`R.fail`），不再提前返回成功 | `PdaIdempotentAspect.java:47-80` | 否 | — |
| MES-MQ-02 | 消费幂等原子化：`setObjectIfAbsent` 处理前占位（失败即视为重复消费/在途）、业务失败释放占位、成功置 DONE；另加 DB 唯一键兜底 | `BaseMessageConsumer.java:110-165`、`script/sql/update/2026-09-20-pda-mq.sql` | **是** | — |
| MES-REL-01 | `doPublish` 返回影响行数并只在 >0 时记成功日志；`approve` 在 0 行时抛 `ServiceException("发布包状态已变更, 发布失败")`（与 reject/archive/setDefault 一致）；Flowable 回调路径保留原语义（不抛，仅 warn） | `ReleasePackageServiceImpl.java:280-295`、`:452-475` | 否 | `ReleasePackageServiceImplTest#approveThrowsWhenPublishCasAffectsZeroRows`、`#approvePublishesWhenPublishCasAffectsOneRow` |
| MES-PDA-09（附带） | `selectBySn` 补 `boundTime` 倒序，同 SN 多行时结果确定 | `PdaSnServiceImpl.java:112-118` | 否 | 由 `PdaSnServiceImplTest` 覆盖路径 |
| MES-BRG-01（部分） | 桥接入口租户断言（同上），无租户上下文的异步/定时线程不再静默返回空 | `MesErpBridgeServiceImpl.java` | 否 | — |

## 2. 未修复 / 部分修复项（含理由与建议）

1. **MES-PDA-04 分批报工未实现（仅补了超报上限 + 可读提示）**：真正的分批报工需要 `pro_dispatch` 增加“已报数量”累计语义 + 原子守卫（`reported_qty + ? <= planned_qty`，累计到量再置 COMPLETED），强制点在 `ProDispatchServiceImpl.validateReportQuantities` / `doCompleteDispatch`，属 **`mes/pro` 所有权（B7 批次 `fix-mes-pro-lt`）**，本批次不得改。建议主代理派单给 B7：把 `total != plannedQty` 改为 `total <= 剩余可报量`，并在工序/流转卡累加 SQL 上加 `<= planned_qty` 守卫。
2. **消费端 fail-closed 的连带影响（重要，需主代理派单）**：base 改为 fail-closed 后，**生产端未 `setTenantId` 的消息会从“落到 000000”变为死信**。已核实缺少 tenantId 的生产端：
   - `ReworkReceiveStrategy.java:181`、`ReworkIssueStrategy.java:135`（`ReworkStockMessage` → `erp/mq/consumer/ReworkStockConsumer`）
   - `PrdSubcontractClosureServiceImpl.java:359`（`SubcontractSnGenerateMessage` → `mes/common/mq/consumer/SubcontractSnGenerateConsumer`）
   
   修法各一行：`msg.setTenantId(…)`（取单据自身 `tenant_id`）。**补齐前这两条链路不可用**（原本是把 ERP 库存数据写到系统租户，属同类 P0 缺陷，fail-closed 是刻意选择）。
3. **MES-MQ-03 未修**（`MesMqSendService` 的 5 个方法：`tenantId` 为 null 时静默不设）。现因消费端 fail-closed，传 null 会变成死信而非静默错租户；建议一并改为 fail-closed 抛错（文件属本批次 `mes/common` 所有权，为避免超范围本次未动）。
4. **反向回退（按 ERP 工单号反查租户）未实现**：需新增跨库 mapper + 租户绕过（`TenantHelper.ignore`），风险/收益不划算；已保留 `resolveMissingTenantId` 钩子（`BaseMessageConsumerTenantTest#resolveTenantIdUsesSubclassFallbackBeforeFailingClosed` 已验证钩子生效），后续可一行接入。**后果**：升级前已在队列/消息记录中的无租户工单消息将进死信而非落错租户（需人工核对 `mq_message_record` / 死信后重投）。
5. **存量脏数据未订正**：已落 `000000` 的 MES 工单（及工序/派工）需数据订正，增量脚本 §2 给出可人工执行的排查/订正模板（默认注释掉，需 DBA 核对后执行）。
6. **PDA-02/03 切面、桥接守卫未补单测**：切面依赖 Servlet 请求上下文 + 静态 `RedisUtils`；桥接守卫需静态桩 `TenantHelper` + mapper 桩，价值低且 `RedisUtils` 静态初始化在单测环境无法 instrument（实测 `mockStatic(RedisUtils)` 报 “could not be initialized”），故以人工评审 + 既有 15 处 ERP PDA 端点回归为主。
7. **P2 未修**：MES-PDA-06（三检照片校验）、MES-PDA-07（保养 `updateById` 陈旧快照，属 `pda` 所有权但本次未纳入范围）、MES-PDA-08（幂等审计落库）、MES-BRG-02（工作中心 `LIMIT 200`）、MES-REL-02/03（匹配确定性 + 版本唯一键，需 DDL 复核）。

## 3. 主脚本合并清单

**无。** 本批次未编辑任何 `script/sql/ry-*.sql`（`ry-mes.sql` 的 `lt_sn_binding` 已含 `sn_id NOT NULL` 与 `uk_sn_material`，无需改主脚本）；全部 DDL 落在本批次独占增量脚本 `script/sql/update/2026-09-20-pda-mq.sql`。

## 4. DDL 登记（供主代理写入 doc/system/数据库增量变更记录.md）

| 日期 | 库 | 变更内容 | 脚本 | 说明 |
|---|---|---|---|---|
| 2026-09-20 | ry-mes | `pro_work_order` 新增唯一索引 `uk_tenant_erp_wo(tenant_id, erp_wo_id)` | `script/sql/update/2026-09-20-pda-mq.sql` | MES-MQ-02 消费幂等兜底（原子占位之外的最终防线）。脚本幂等（先查 `information_schema.STATISTICS`）；**执行前须跑脚本内前置检查 SQL** 确认无存量重复（有重复会直接失败）；`erp_wo_id` 允许 NULL（手工工单不受影响）；**逻辑删除行同样参与唯一约束**，已删除的 MES 工单重新下达会因冲突进入重试/死信（fail-closed 预期，需先物理清理或复用原工单） |

## 5. 验证记录

命令（照抄修复规范 §3.4）：

```powershell
mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false "-Dtest=PdaSnServiceImplTest,PdaReportServiceImplTest,BaseMessageConsumerTenantTest,ReleasePackageServiceImplTest,WorkOrderReleaseConsumerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

结果：**BUILD SUCCESS**，`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`（pwsh 管道回传 exit code 1 系 stderr 合流所致，非构建失败）：

| 测试类 | 用例数 | 结果 |
|---|---|---|
| `com.nexus.mes.common.mq.consumer.BaseMessageConsumerTenantTest`（新增） | 4 | 全绿 |
| `com.nexus.mes.pda.service.impl.PdaSnServiceImplTest`（新增） | 5 | 全绿 |
| `com.nexus.mes.pda.service.impl.PdaReportServiceImplTest`（新增） | 5 | 全绿 |
| `com.nexus.mes.release.service.impl.ReleasePackageServiceImplTest`（追加 2 用例，原 11） | 13 | 全绿 |
| `com.nexus.mes.common.mq.consumer.WorkOrderReleaseConsumerTest`（既有，回归） | 6 | 全绿 |

- 新增测试类均带 `@Tag("local")` + `@Tag("dev")`；追加用例用**方法级** `@Tag`（避免改动既有类注解）。
- 关键断言（修复前必失败）：`snId` 落库值 9001（原为 null → SQL 1364）；缺/零 tenantId 抛 `ServiceException` 且不含 `tenantId=000000`；报工失败返回 `ok=false` + 可读中文（原为 500 `UnexpectedRollbackException`）；`doPublish` 0 行抛“状态已变更”。
- 未跑全模块全量测试（按规范由主代理统一跑）。
- 第一次运行暴露并已修正的问题：测试类跨包访问 `protected resolveTenantId`（改为探针子类提升可见性）；`mockStatic(RedisUtils)` 在单测环境不可用（删除 2 个 processMessage 级用例，改为直接断言租户解析 fail-closed）。

## 6. 风险与回滚说明

**改动文件（回滚即还原以下 9 个文件，DDL 可保留——旧代码不读该索引）**：
`PrdWorkOrderServiceImpl.java`、`WmMaterialReceiveCrudServiceImpl.java`、`BaseMessageConsumer.java`、`PdaIdempotentAspect.java`、`MesErpBridgeServiceImpl.java`、`PdaSnServiceImpl.java`、`PdaReportServiceImpl.java`、`ReleasePackageServiceImpl.java`、`script/sql/update/2026-09-20-pda-mq.sql`（+ 4 个测试文件）。

1. **消费端 fail-closed（最高风险）**：无租户消息进死信 → 见 §2-2 的 3 处生产端必须补齐；未补齐前 ReworkStock / SubcontractSn 两条链路不可用。死信可在 `mq_message_record` 与 RabbitMQ 死信队列观测，补齐生产端后可重投恢复。
2. **幂等键构成变化**：升级后旧 Redis 键失效，24h 窗口内的重复请求可能被当作首次执行一次（无数据损坏，仅一次重复执行；各写端点仍有业务 CAS/唯一键兜底）。
3. **切面 PENDING 分支改为 `R.fail`**：ERP/MES 共 15+ 处 PDA 端点，客户端需把“相同作业正在处理中”视为可重试失败（原实现返回成功且不执行）。这是修 MES-PDA-03 的必需契约变更，**需 App 侧同步**。
4. **桥接守卫**：在系统租户 000000 下、ERP 主数据确实缺失时会由“静默空”变为可读异常（含 `materialId/routingId` 与租户提示）；若生产确有 000000 租户业务且主数据不全，会看到新的报错而非静默无工序工单——这是刻意的 fail-fast。
5. **`approve` 现在会因 0 行抛错**：并发双审批时后到请求由“返回成功但未发布”变为明确失败（客户端可重试/刷新），语义与 reject/archive/setDefault 一致；Flowable 回调路径行为未变。
6. **`PdaReportServiceImpl` 去事务**：`start`/`complete` 本身无写库（仅委派），事务由 `IProDispatchService` 持有；若后续有人在本类新增写库逻辑，需自行加事务。

## 7. 生产端 tenantId 补全（派单追加，2026-09-20）

本节闭环 §2-2 的阻塞清单：3 处生产端已补全（另含 `BaseMessageConsumer` 的 `0`/`null` 语义修正）。

| # | 文件（`ReworkStockMessage` / `SubcontractSnGenerateMessage` 构造之后） | 补入内容 | 取值方案 |
|---|---|---|---|
| 1 | `ReworkReceiveStrategy.java`（`execute` 尾部，`msg.setSourceModule("wm")` 后） | `msg.setTenantId(Long.parseLong(tenantIdStr))` | `TenantHelper.getTenantId()`（请求/事务线程上下文，与 `PrdWorkOrderServiceImpl.resolveMessageTenantId` 的回退分支同口径） |
| 2 | `ReworkIssueStrategy.java`（同上） | 同上 | 同上 |
| 3 | `PrdSubcontractClosureServiceImpl.advanceToReleased`（`msg.setSourceModule("erp")` 后） | 同上 | 同上 |

**取值方案与理由**

1. **取值口径**：三处均在请求线程内、由库存/委外业务事务触发（无 `@Async`/定时线程），故直接取 `TenantHelper.getTenantId()`。`ReworkStock` 两处的 `execute(Object... args)` 参数契约中没有单据实体（只有 reworkId/物料/数量），故未走“单据自身 tenant_id 优先”分支（那是 `resolveMessageTenantId` 在能拿到实体时的口径），避免为取租户引入额外查询。
2. **默认租户 `0` 与 `"000000"` 语义修正（重要，同时改了 `BaseMessageConsumer`）**：`BaseMessage.tenantId` 是 `Long`，租户 `"000000"` 只能编码为 `0`；原 fail-closed 判断把 `0` 与 `null` 一起当作“缺失”，会导致**默认租户消息被误判进死信**。现改为：
   - `tid != null` → `String.format("%06d", tid)`（`0` → `"000000"`，显式 `setTenantId(0)` 即系统租户的合法编码）；
   - 只有 **`null`（生产端从未 `setTenantId`）** 才走 `resolveMissingTenantId` 回退 → 仍无法解析则抛 `ServiceException` 进重试/死信。MES-MQ-01 的原缺陷正是“从未 setTenantId”(null)，P0 语义不变。
   - **反向风险与对策**：若生产端把“非法/空白”租户解析成 `0` 并显式 set，会被当成系统租户而静默错租户。故本批次涉及的 5 处生产端（`PrdWorkOrderServiceImpl`、`WmMaterialReceiveCrudServiceImpl` + 本次 3 处）一律在 `parseLong` 前**显式判空/判非法并让链路失败**（抛 `ServiceException`，随事务回滚），不产生 `0`。
   - 测试同步：`BaseMessageConsumerTenantTest#resolveTenantIdThrowsWhenTenantIdIsZero` → 改为 `#resolveTenantIdMapsExplicitZeroToSystemTenant`（断言 `0 → "000000"`）；`#resolveTenantIdThrowsWhenTenantIdIsNull` 保留（null 显式失败）。
3. **三处均为“拿不到可靠租户即显式失败”**：抛 `ServiceException` 回滚本次返工入库/出库/委外下达事务，不投递必然死信的消息，也不留静默失败；代价是此类请求需重试（原为库存已动、消息不带租户）。
4. **改动范围**：仅这 3 处各 1 个 setter + 租户解析守卫（约 9 行）+ 1 行 `import com.nexus.common.tenant.helper.TenantHelper;`，无其它重构。

**验证**

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am -q compile -DskipTests
#  → ERP_COMPILE_EXIT=0

mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false "-Dtest=BaseMessageConsumerTenantTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.compiler.failOnError=false"
#  → Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS / EXIT=0
```

⚠ **并发阻塞（需主代理协调）**：直接运行 mes 测试时，`ruoyi-mes` 测试模块编译失败于
`ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/pro/service/impl/ProEntryCheckServiceImplTest.java:258`
（`找不到符号: 方法 verify(ISopViewLogService, VerificationMode)`，缺 `import static org.mockito.Mockito.verify;`）——
该文件**不属本批次**，是其它代理（pro/sop 批次）的在途编辑；在它修好前 `ruoyi-mes` 测试模块整体无法编译，**主代理跑全量测试会被它卡住**。上面第二次运行以 `-Dmaven.compiler.failOnError=false` 绕过该文件，取得本批次用例的真实结果（4/4 绿）。
