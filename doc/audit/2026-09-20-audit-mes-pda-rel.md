# MES PDA / release / common 审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/{pda,release,common}` + 幂等切面 `ruoyi-common/ruoyi-common-satoken/.../pda` + 前端 `plus-ui/src/api/mes` | 覆盖文件数: 31（实际读过全文/片段） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-MQ-01 | P0 | 租户 | 工单下达消息未带 `tenantId`，消费端退化为 `000000`：MES 工单落错租户，且桥接查 ERP 主数据全部为空（无单位/无工序） | `PrdWorkOrderServiceImpl.java:724`、`BaseMessageConsumer.java:54` |
| MES-PDA-01 | P0 | 功能缺失/数据错 | PDA SN 绑定必失败：`lt_sn_binding.sn_id` NOT NULL 但 `bind()` 未赋值（未按 snCode 反查 SN） | `PdaSnServiceImpl.java:62`、`ry-mes.sql:576` |
| MES-PDA-02 | P1 | 幂等 | 幂等键只由客户端请求头提供、不含用户/端点/请求体，且命中即静默返回成功 → 同租户内不同作业可被 24h 吞掉 | `PdaIdempotentAspect.java:24`、`:30` |
| MES-PDA-03 | P1 | 幂等/并发 | 并发（超时重发）时后到请求在首个请求提交前即返回成功，首个请求失败后不补偿 → 作业丢失且客户端不再重试 | `PdaIdempotentAspect.java:35`、`:43` |
| MES-PDA-04 | P1 | 业务校验 | PDA 报工要求「合格+不良+报废 == 派工计划数」严格相等 → 无法分批/按实际产出报工，差 1 即整单失败 | `ProDispatchServiceImpl.java:347`、`PdaReportServiceImpl.java:93` |
| MES-PDA-05 | P1 | 事务 | 报工/开工在 `@Transactional` 内 catch 内层事务异常并返回 `fail`，共享事务已 rollback-only → 提交期抛 `UnexpectedRollbackException`，客户端拿到 500 而非原因 | `PdaReportServiceImpl.java:104`、`ProDispatchServiceImpl.java:250` |
| MES-MQ-02 | P1 | 幂等/并发 | 消费幂等 `hasKey`→处理→`setCacheObject` 非原子，业务侧查重也是先查后写 → 双实例并发可创建两张 MES 工单 | `BaseMessageConsumer.java:71`、`ProWorkOrderServiceImpl.java:469` |
| MES-REL-01 | P1 | 状态机 | `approve()` 经 `doPublish()` 发布但 `doPublish` 不校验影响行数（无 CAS），并发双批/与归档竞态时返回成功而状态未推进 | `ReleasePackageServiceImpl.java:280`、`:447` |
| MES-PDA-06 | P2 | 校验 | 三检照片 `photoOssIds` 原样落库，无格式/归属校验 | `PdaQcServiceImpl.java:102` |
| MES-PDA-07 | P2 | 并发 | 保养执行 `planMapper.updateById(plan)` 全量覆盖陈旧快照，可丢并发更新 | `PdaEqMaintServiceImpl.java:187` |
| MES-PDA-08 | P2 | 幂等 | 幂等命中无任何持久审计（Redis 键 24h 后消失），被吞作业事后无法排查 | `PdaIdempotentAspect.java:26`、`:39` |
| MES-MQ-03 | P2 | 租户 | `MesMqSendService` 的 tenantId 由调用方以 String 传入，传 null 即静默退化 000000（非 fail-closed） | `MesMqSendService.java:61`、`:100` |
| MES-BRG-01 | P2 | 租户 | 桥接 Mapper 全为裸 `@Select` 且不带 `tenant_id`，完全依赖租户插件；在无租户上下文线程被调用即静默返回空 | `ErpMaterialMapper.java:19`、`ErpWorkstationMapper.java:18` |
| MES-BRG-02 | P2 | 性能 | 工作中心列表硬编码 `LIMIT 200`，超量时选择器静默截断 | `ErpWorkstationMapper.java:35` |
| MES-REL-02 | P2 | 契约 | `matchPackage` 以 `publishTime` 排序 + `LIMIT 1`，同秒发布两包时匹配结果不确定 | `ReleasePackageServiceImpl.java:389`、`:401` |
| MES-REL-03 | P2 | 幂等 | `assertVersionUnique` 先查后写（TOCTOU），并发新增同客户+物料+版本可双写（DDL 唯一键未逐行核对，需复核） | `ReleasePackageServiceImpl.java:109`、`:480` |
| MES-PDA-09 | P2 | 校验 | `selectBySn` 用 `limit 1` 无排序，一个 SN 存在多行时返回任意行 | `PdaSnServiceImpl.java:86` |

## 1. P0 详述

### MES-MQ-01 工单下达消息缺 `tenantId`，MES 侧租户退化为 000000（工单落错租户 + 主数据桥接全空）

- **证据**:
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java:724`（`sendWorkOrderReleaseMessage`，726-741 逐字段 set，**无 `message.setTenantId(...)`**）
  - `ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/consumer/BaseMessageConsumer.java:54`（`resolveTenantId`：tenantId 为 null 或 0 → 返回 `"000000"`）、`:79`（`TenantHelper.dynamic(tenantId, () -> doProcess(message))`）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveCrudServiceImpl.java:622` 注释自认同类缺陷：「显式设置 tenantId 避免消费端退化为 000000（参考 sendWorkOrderReleaseMessage 同类缺陷）」→ 该缺陷在 ERP 另一处已修，此处仍在
  - 桥接侧：`ruoyi-modules/ruoyi-mes/.../common/bridge/MesErpBridgeServiceImpl.java:48`（`DynamicDataSourceContextHolder.push("erp")` 后直接查）、`.../common/mapper/ErpMaterialMapper.java:19`（SQL 无 `tenant_id` 条件，靠租户插件注入）
  - 租户排除名单不含 `bd_*`：`ruoyi-admin/src/main/resources/application.yml:156`（excludes 列表 158-187 全为 mq_/sys_/ai_/flow_/jm_ 表）
- **现状**: ERP 工单审批通过 → `sendWorkOrderReleaseMessage` 发送 `WorkOrderReleaseMessage`（woId/materialId/workshopId/bomId/clientId…），唯独没有租户。MES `WorkOrderReleaseConsumer` → `BaseMessageConsumer.processMessage` → `resolveTenantId` 返回 `"000000"` → 整个 `doProcess` 跑在系统租户上下文里。
- **触发路径**: ERP 任一非系统租户（tenant_id ≠ 000000）提交工单审批并下达（`PrdWorkOrderServiceImpl.java:309` / `:687` 两处调用点）。
- **后果**:
  1. `ProWorkOrderServiceImpl.createFromErpMessage`（`ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProWorkOrderServiceImpl.java:465`）在 000000 租户下写 `pro_work_order` → MES 工单归属系统租户，真实租户在 MES 工单列表/派工/PDA 待办里看不到（数据错位；MES 侧一切以工单为入口，等于整体不可用）。
  2. 同一上下文里 `mesErpBridgeService.getMaterial/getWorkshop/getRoutingSteps` 查 `bd_material`/`bd_workshop`/`bd_routing_step` 时被租户插件注入 `tenant_id = '000000'` → 返回 null/空 → 工单 `unitId/unitName/workshopName/routingId` 全空、`routingSteps` 为空 → **不创建任何工序**（`ProWorkOrderServiceImpl.java:476`-`505`），日志只有一句「通过桥接服务查询默认工艺路线: routingId=null」，无告警。
- **修复建议**:
  1. `PrdWorkOrderServiceImpl.sendWorkOrderReleaseMessage` 内补 `message.setTenantId(TenantHelper.getTenantId())`（消息继承 `BaseMessage`，`resolveTenantId` 已按 6 位零填充解析）；`prd` 两处调用点共用此方法，改一处即可。
  2. 把 `BaseMessageConsumer.resolveTenantId` 的默认值 `"000000"` 改为 **fail-closed**：消息无 tenantId 时抛异常走重试/死信，而不是静默落到系统租户（否则同类缺陷还会复发）。
  3. 补集成测试：非 000000 租户下达 → 断言 `pro_work_order.tenant_id` 与工序行数。
- **影响面**: `PrdWorkOrderServiceImpl`（生产者）、`BaseMessageConsumer`（所有 MQ 消费者共用）、`ProWorkOrderServiceImpl.createFromErpMessage`、`MesErpBridgeServiceImpl`。**无需 DDL**；存量脏数据（已落 000000 的 MES 工单）需数据修复脚本。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImplTest.java`（覆盖发布包/工序匹配，未覆盖租户）；`.../common/mq/consumer/WorkOrderReleaseConsumerTest.java` 仅 verify 调用了 `createFromErpMessage`，**未断言租户**。

### MES-PDA-01 PDA SN 绑定接口必然失败（`sn_id` NOT NULL 未赋值）

- **证据**:
  - `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pda/service/impl/PdaSnServiceImpl.java:62`（`bind()` 构造 `LtSnBinding`，只 set `snCode/lotId/lotCode/materialId/boundTime/boundBy`，**无 `setSnId`**；全文件 107 行，无 ProSn 反查）
  - `script/sql/ry-mes.sql:576`（`` `sn_id` bigint(20) NOT NULL COMMENT 'SN ID' ``，无 DEFAULT）
  - `script/sql/ry-mes.sql:590`（`UNIQUE INDEX uk_sn_material(sn_id, material_id)` —— 唯一键正是建立在 `sn_id` 上）
  - 正常路径对照：`ruoyi-modules/ruoyi-mes/.../lt/service/impl/LtSnBindingServiceImpl.java:99`（`binding.setSnId(bo.getSnId())`）
  - 入参：`ruoyi-modules/ruoyi-mes/.../pda/domain/bo/PdaSnBindBo.java:10`（只有 `snCode`/`lotCode` 两个 `@NotBlank` 字段，**没有 snId**）
- **现状**: MyBatis-Plus 默认 `insertStrategy=NOT_NULL`，`snId` 为 null 时该列不出现在 INSERT 列表；MySQL 8 默认 `STRICT_TRANS_TABLES` 下执行 `INSERT INTO lt_sn_binding(...)` 报 `1364 Field 'sn_id' doesn't have a default value`。
- **触发路径**: PDA 追溯页扫码 → `POST /mes/pda/sn/bind`（`AppPdaSnController.java:45`，需 `mes:lt:binding:add`）→ 任何输入都失败。
- **后果**: PDA「SN 绑定批次」100% 不可用（500/SQL 异常）；且因 `sn_id` 恒为 null，`uk_sn_material` 对本接口完全失效——`selectBySn` 先查后插（`:53`-`69`）是典型 TOCTOU，一旦列修好、并发放开，同一 SN 可插入重复绑定行（追溯链重复/错误）。
- **修复建议**:
  1. `PdaSnServiceImpl.bind()` 内按 `snCode` 查 `ProSn` 取 `snId` 后 `binding.setSnId(...)`；SN 不存在时返回 `MesActionResultVo.fail("SN不存在")`（当前语义下"任意字符串都能绑定"本身也是错的）。
  2. 查重保留，并发兜底交给唯一键：`catch (DuplicateKeyException e) { return fail("SN 已绑定该物料") }`；若业务允许一 SN 绑多物料，需重新评估 `uk_sn_material` 维度（当前唯一键允许同 SN 绑不同物料，而 `selectBySn` 只按 snCode 查 `limit 1`，语义不一致）。
- **影响面**: `PdaSnServiceImpl`、`AppPdaSnController`、`LtSnBinding`/`lt_sn_binding`。**无需 DDL**（列已存在）。需补单测（`ruoyi-modules/ruoyi-mes/src/test` 下无 PDA 用例）。

## 2. P1 详述

### MES-PDA-02 幂等键构成过弱 + 命中即静默返回成功（作业可被吞掉）

- **证据**: `ruoyi-common/ruoyi-common-satoken/src/main/java/com/nexus/common/satoken/pda/PdaIdempotentAspect.java:24`（`KEY_PREFIX="pda:idem:"`、`HEADER_NAME="X-Idempotent-Key"`、`TTL=24h`）、`:30`-`33`（取不到 header 直接 `point.proceed()`，**无兜底**）、`:34`（`redisKey = prefix + LoginHelper.getTenantId() + ":" + key`——**不含 userId、不含端点、不含请求体**）、`:39`（命中即 `R.ok("重复提交，已处理", new PdaReplayResult())`）
- **现状**: 幂等唯一性完全由客户端 header 决定。仓库内无 PDA 客户端代码（`plus-ui` 无任何 PDA 页面/接口，见 §4-3），因此客户端 key 生成策略无法在本仓库证实。
- **触发路径 / 后果**:
  - 客户端若按「设备/班次/任务」而非「每次写请求」生成 key（离线队列重放的常见实现），则 24h 内该租户下**所有** PDA 写操作（报工、三检、SN 绑定、点检、保养）中除第一次外全部返回「重复提交，已处理」而**实际未执行** → 漏报工/漏检验，客户端收到 success 不会重试，静默丢数据。
  - 同租户不同工位两台 PDA 使用相同 key（如按秒级时间戳生成）→ 互相吞掉。
  - 客户端不发 header（弱网重放/第三方 PDA）→ 幂等完全失效 → 重复报工/重复检验（重复报工被派工状态 CAS 兜住，重复 SN 绑定/保养记录不会）。
- **修复建议**: key 改为 `pda:idem:{tenantId}:{userId}:{METHOD+URI}:{headerKey 或 bodyHash}`；header 缺失时用「用户+端点+请求体 SHA-256」兜底；命中时返回可区分的结果（`replayed=true` 且 `ok=false`/专用 code）而不是伪装成业务成功。
- **影响面**: 切面为 ERP/MES 全部 PDA 写端点共用（ERP `wm/controller/app/AppPda*` 6 个控制器 15 处 + MES 8 处）。需补切面单测（当前无）。

### MES-PDA-03 并发/超时重发窗口内后到请求提前返回成功

- **证据**: `PdaIdempotentAspect.java:35`（`setObjectIfAbsent` 成功=首次）、`:36`-`40`（未拿到即立刻返回 ok）、`:41`-`52`（首个请求失败/异常才 `deleteObject`）
- **触发路径**: PDA 弱网「请求超时 → 立即重发」，此时第一个请求仍在事务中未提交。第二个请求 `setObjectIfAbsent` 失败 → 立即返回 `ok(replayed=true)`；若第一个请求随后业务失败（如"派工状态已变更"）并删 key，第二个请求已经告诉客户端"已处理"。
- **后果**: 客户端认为作业已成功、不再重试，而实际未执行 → 漏报工/漏检验；服务端只有一条 info 级「重复请求已忽略」，无告警。
- **修复建议**: 重复请求在「首次请求仍在执行中」时不能返回成功。`setObjectIfAbsent(redisKey, "PENDING", TTL)` 占位，成功后置 `"DONE"`；命中 `PENDING` 返回 `replayed=true, ok=false, message="处理中，请稍后查询"`（或 409），命中 `DONE` 才返回成功。需 App 契约同步。

### MES-PDA-04 报工数量必须严格等于派工计划数（无法分批、差 1 即失败）

- **证据**: `ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProDispatchServiceImpl.java:347`（`if (plannedQty != null && total.compareTo(plannedQty) != 0) throw`）、`:334`（`validateReportQuantities`）、`:257`（调用点）、`:260`-`268`（CAS `IN_PROGRESS → COMPLETED` 一次置完成）、`:277`-`287`（工序/流转卡 `setSql` 增量累加，**无"累计不超过计划"守卫**）
- **触发路径**: PDA `POST /mes/pda/report/{dispatchId}/complete`（`AppPdaReportController.java:64`）提交 `goodQty=9, defectQty=0` 而计划 10 → `fail("报工数量合计必须等于…")`。
- **后果**: 现场按实际产出报工（少一件待补、或分两次报）直接失败，PDA 无法完工；派工单无"部分完工"状态（`STATUS_COMPLETED` 一次性）。反向看超报确实被拦住，但拦住方式是"必须相等"，一旦业务放开为 `<=` 会立刻出现超报——因为工序/流转卡累加无上界守卫。
- **修复建议**: 若分批报工是业务需求：`pro_dispatch` 增"已报数量"累计语义，改为原子守卫 `UPDATE ... SET reported_qty = reported_qty + ? WHERE dispatch_id=? AND reported_qty + ? <= planned_qty`，累计到量再置 `COMPLETED`；工序/流转卡累加加 `WHERE good_qty + ? <= planned_qty` 守卫。**需业务确认**（若"一单一次报完"是刻意设计，本条降为 P2，但 MES-PDA-05 仍需修）。

### MES-PDA-05 报工失败被 catch 在 `@Transactional` 内 → 提交期 `UnexpectedRollbackException`（500，丢失失败原因）

- **证据**:
  - 外层：`ruoyi-modules/ruoyi-mes/.../pda/service/impl/PdaReportServiceImpl.java:92`（`@Transactional(rollbackFor = Exception.class)`）+ `:104`-`112`（try/catch `RuntimeException` → `MesActionResultVo.fail(e.getMessage())`）
  - 内层：`ProDispatchServiceImpl.java:250`（`@Transactional` 的 `doCompleteDispatch`，默认 REQUIRED → 加入外层同一事务）；`:257`（数量校验抛 `RuntimeException`）、`:270`（CAS 0 行抛 `RuntimeException`）
  - 同类：`PdaReportServiceImpl.java:82`-`87`（`start`）
- **现状**: Spring 在内层 `@Transactional` 方法抛 RuntimeException 时把共享事务标记 rollback-only；外层捕获并正常 return 后，提交阶段抛 `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`。
- **触发路径**: PDA 报工数量不合法 / 状态已变更。
- **后果**: 客户端拿到 HTTP 500 + 与业务无关的英文异常，而非「报工数量合计必须等于…」；App 通常把 500 归为"网络/服务异常"并允许重试 → 重试风暴；幂等切面此时走 `catch (Throwable)` 删 key（`PdaIdempotentAspect.java:48`），重试能进业务，但用户看到的是不可读错误。
- **修复建议**: 不要在事务方法内 catch 内层事务异常后正常返回。二选一：① 去掉 `PdaReportServiceImpl` 的 `@Transactional`（本方法只做一次委派），把异常处理上移到 Controller/切面；② 保持事务但把前置只读校验移到事务外，或在 catch 中 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` 并抛自定义异常由全局异常处理器转可读消息。注意 `MesActionResultVo.fail`（业务失败非异常）与 `@Transactional` 的组合语义必须一致。

### MES-MQ-02 消费幂等非原子 + 业务侧先查后写 → 并发可创建两张 MES 工单

- **证据**:
  - `ruoyi-common/ruoyi-common-mq/.../consumer/BaseMessageConsumer.java:71`（`isConsumed` = `RedisUtils.hasKey`）、`:86`（`markConsumed` 在业务成功后才写）、`:112`-`125`（`markConsumed` 用 `setCacheObject`，非 `setObjectIfAbsent`）
  - 业务侧：`ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProWorkOrderServiceImpl.java:469`-`473`（`selectByErpWoId` 查重后 return，先查后写）
- **触发路径**: MES 多实例部署（或单实例并发 prefetch>1）同时收到同一 `messageId`；或 `basicNack(requeue=true)` 重投与首次消费在途重叠（`BaseMessageConsumer.java:99`-`101`）。
- **后果**: 两线程都通过 `isConsumed` → 都执行 `createFromErpMessage` → 都通过 `selectByErpWoId`（对方未提交）→ 插入两张 `pro_work_order`（同一 `erp_wo_id`）→ 后续报工/完工回写 ERP 双计。
- **修复建议**: ① `markConsumed` 改为「消费前 `setObjectIfAbsent` 占位，失败即视为重复消费」；② 给 `pro_work_order.erp_wo_id` 建唯一索引（**需 DDL**，注意含 `tenant_id` 维度与逻辑删除列，参考 `lt_sn_binding` 唯一键写法），插入冲突即视为已处理；③ 保留现有业务查重作为快速路径。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/common/mq/consumer/WorkOrderReleaseConsumerTest.java`（6 用例均为 Mockito 验证委派，无并发/幂等断言）。

### MES-REL-01 `approve()` → `doPublish()` 无 CAS/无影响行数校验，返回成功但状态可能未推进

- **证据**: `ruoyi-modules/ruoyi-mes/.../release/service/impl/ReleasePackageServiceImpl.java:280`-`291`（`approve` 读状态校验后直接 `doPublish`，`return true`）、`:447`-`457`（`doPublish` 的 `baseMapper.update(...)` **未接收返回值**，随后无条件 `log.info("发布包发布成功")`）
- **对照**: 同文件 `reject`（`:303`-`309`）、`archive`（`:329`-`335`）、`setDefault`（`:360`-`:365`）均校验 `rows == 0` 并抛「状态已变更」——只有发布这条路径漏了。
- **触发路径**: 两个审批人同时点「审批通过」；或审批通过与「归档/驳回」并发（都读到 `IN_REVIEW`）。
- **后果**: 后到请求返回 `true`/200 并打印「发布包发布成功」，但 UPDATE 因 `WHERE status='IN_REVIEW'` 命中 0 行，状态实际未变（可能已被归档）→ 客户端以为已发布；随后 ERP 工单下达时 `matchPackage` 只取 `RELEASED`（`:386`、`:398`）匹配不到 → 该客户订单按标准工艺路线/BOM 生产，**客户定制化（固件映射/BOM 选配）被静默忽略**（`ProWorkOrderServiceImpl.java:516`-`524` 无匹配时走传统方式且仅 info 日志）。
- **修复建议**: `doPublish` 接收 `rows`，`rows == 0` 时抛 `ServiceException("发布包状态已变更, 发布失败")`（与 reject/archive 一致）；`approve` 改为 CAS 后再写 `publishedBy/publishTime`。另外 `matchPackage` 返回 null 时在工单创建日志显式 WARN（便于发现"该用定制包却用了标准工艺"）。
- **影响面**: `ReleasePackageServiceImpl`、`ReleasePackageController`（`/mes/release/package/approve/{id}`）、Flowable 回调 `afterFlowHandle`（`:232`-`247` 走 `updateStatus` 无 CAS，属既有设计，注释已说明）；无需 DDL；建议补并发单测。

## 3. P2 详述

| 编号 | 证据 | 后果 | 修复建议 |
|---|---|---|---|
| MES-PDA-06 | `PdaQcServiceImpl.java:102`、`:162`、`:217`（`set(QcIqc::getPhotoOssIds, bo.getPhotoOssIds())`） | 检验照片串未校验（非 ossId、超长、指向他人附件），详情页可能展示失败附件或写入脏数据 | 校验为逗号分隔数字串并核对 `sys_oss` 归属（或改用专门附件关联表） |
| MES-PDA-07 | `PdaEqMaintServiceImpl.java:187`（`planMapper.updateById(plan)`，plan 为 `:156` 读出的快照） | 两台 PDA 并发执行同一保养计划时后写覆盖先写（下次保养日期丢失/回退） | 改为 `lambdaUpdate().set(nextDate).eq(planId).eq(nextDate, 旧值)` 的 CAS，或只更新必要列 |
| MES-PDA-08 | `PdaIdempotentAspect.java:26`（TTL 24h）、`:39`（命中仅 info 日志） | 幂等命中无持久记录，事后无法回答"这次报工为什么没生效" | 命中时落一条审计（含 userId/端点/key/时间）并提供按 key 查询 |
| MES-MQ-03 | `MesMqSendService.java:61`、`:100`（`if (tenantId != null) msg.setTenantId(...)`，否则不设） | 调用方传 null 时 ERP 消费端退化为 000000（与 MES-MQ-01 同型，方向相反） | 与 MES-MQ-01 一并改 fail-closed：tenantId 缺失直接抛错，不静默发送 |
| MES-BRG-01 | `ErpMaterialMapper.java:19`、`ErpBomDetailMapper.java:19`、`ErpBomHeaderMapper.java:15`、`ErpRoutingHeaderMapper.java:15`、`ErpRoutingStepMapper.java:20`、`ErpUnitMapper.java:15`、`ErpWorkshopMapper.java:17`、`ErpWorkstationMapper.java:18`（全部裸 `@Select`，无 `tenant_id`） | 隔离完全依赖租户插件与调用线程上下文；任何异步/定时/无登录线程调用桥接都会静默返回空（表现为"ERP 数据不存在"而非报错），排查困难 | 在 `MesErpBridgeServiceImpl` 入口断言租户上下文非空（`TenantHelper.getTenantId()` 为空即抛错），或 SQL 显式 `AND tenant_id = #{tenantId}` |
| MES-BRG-02 | `ErpWorkstationMapper.java:35`（`ORDER BY workstation_code LIMIT 200`） | 工作中心超过 200 个时选择器静默少数据 | 改为分页/关键字必填，或提高上限并返回 total |
| MES-REL-02 | `ReleasePackageServiceImpl.java:389`、`:401`（`orderByDesc(publishTime).last("LIMIT 1")`） | 同秒发布两个同客户+物料包时匹配结果不确定（生产可能用错版本） | 排序改为 `publishTime DESC, packageId DESC`，或加"同客户+物料仅一个默认包"约束 |
| MES-REL-03 | `ReleasePackageServiceImpl.java:109`-`114`、`:480`-`487` | 并发新增同 `client+material+version` 可双写两条发布包 → 匹配歧义（叠加 MES-REL-02） | 给 `mes_release_package` 加 `UNIQUE(tenant_id, client_id, material_id, version)`（**需 DDL**；本次未逐行核对现有索引段，修复前先复核） |
| MES-PDA-09 | `PdaSnServiceImpl.java:86`-`88`（`selectOne(...).last("limit 1")`，无 `orderBy`） | 同 SN 多行绑定（历史脏数据/一 SN 多物料）时查询结果任意，解绑可能删错行 | 补 `orderByDesc(boundTime)`；解绑按 `bindingId` 或明确物料维度 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 发布包「下发/回执」链路不存在 | `ReleasePackageController.java:39`-`174` 只有 list/get/add/edit/remove/submit/approve/reject/archive/default/match/list-by-client-material/flows/proc，**无下发端点**；`release/domain` 下无下发记录实体（仅 `ReleasePackage`/`ReleasePkgFirmware`/`ReleasePkgBomItem`/`ReleasePkgConfig`） | 发布包状态机（DRAFT/IN_REVIEW/RELEASED/ARCHIVED）、`isDefault`、有效期、工单侧快照（`ProWorkOrder.releasePackageId/Code/Ver/releaseConfig`，`ProWorkOrderServiceImpl.java:561`-`566`） | 中：需新表（下发批次+回执）并与 flash 版本回执打通；当前"下发"实质是"工单创建时被动匹配"（`ProWorkOrderServiceImpl.java:517`），无法回答"哪块板刷了哪个发布包版本" |
| 2 | MES 侧无 PDA「发料」接口 | `mes/pda` 下只有 报工/三检/SN/点检/保养 5 类；发料/收料/调拨/盘点/交接 全在 ERP `ruoyi-modules/ruoyi-erp/.../wm/controller/app/AppPda*`（本次范围外） | ERP 侧 6 个 PDA 控制器已具备 | 0（仅需确认是否刻意分区；若 MES 工单需领料入口，需新增或复用 ERP 接口） |
| 3 | PDA 客户端不在本仓库 | `plus-ui/src/api/mes/**` 无任何 PDA 接口；全仓无 PDA 前端页面（`glob **/*pda*/**` 仅命中后端类与 SQL 增量脚本） | 后端 8 个 MES PDA 控制器 + 13 个权限码（均已在 `sys_menu` 有种子） | —（说明：MES-PDA-02 的"客户端 key 生成策略"因此无法在本仓库证实） |

## 5. 已核查确认无问题（证明覆盖范围）

- **MES PDA 权限全覆盖**：8 个控制器共 19 个端点全部带 `@SaCheckPermission`（`AppPdaReportController.java:37/44/51/60`、`AppPdaIqcController.java:41/48/55`、`AppPdaIpqcController.java:41/48/55`、`AppPdaOqcController.java:41/48/55`、`AppPdaSnController.java:35/42/51`、`AppPdaEqController.java:38/45/52`、`AppPdaEqMaintController.java:41/48/55`、`AppPdaQcAqlController.java:29`），且 13 个权限码在 `script/sql/ry-vue.sql` 均有 `sys_menu` 种子（`mes:pro:dispatch:list` :92427、`start` :92431、`complete` :92432；`mes:qc:iqc:list` :92449、`inspect` :92452；`mes:qc:ipqc:list` :92453、`inspect` :92456；`mes:qc:oqc:list` :92457、`inspect` :92460；`mes:eq:equipment:list` :92461、`inspect` :92477；`mes:eq:maintenance:list` :92466、`complete` :92828；`mes:lt:binding:query` :92510、`add` :92511、`remove` :92512）→ **不存在"无种子 → 除超管外全员 403"问题**。
- **MES PDA 无免登录通道**：8 个控制器无 `@Anonymous`/`@SaIgnore`（注解 grep 无命中），不存在"登录即可访问"的越权写端点；历史上 `AppPdaBasedataController` 的越权问题在 MES 侧已不复现（该类已不存在）。
- **PDA 写端点幂等注解无遗漏**：MES 侧全部 8 个写端点均带 `@PdaIdempotent`（`AppPdaReportController.java:52/61`、`AppPdaSnController.java:43/52`、`AppPdaIqcController.java:56`、`AppPdaIpqcController.java:56`、`AppPdaOqcController.java:56`、`AppPdaEqController.java:53`、`AppPdaEqMaintController.java:56`）；只读端点（todo/detail/plan）未加，符合预期。
- **失败请求不会被占位**：切面在 `R.code != SUCCESS` 或 `PdaActionResult.ok == false` 时删幂等键（`PdaIdempotentAspect.java:43`-`47`），异常路径同样删（`:48`-`52`）；`MesActionResultVo` 实现 `PdaActionResult`（`MesActionResultVo.java:10`），`PdaReplayResult` 与之 JSON 同构（`PdaReplayResult.java:13`-`18`：ok/message/replayed）→ 重放响应体对 MES 各写端点是**结构兼容**的（代价见 MES-PDA-02/03）。
- **`mes.api-key` fail-closed 已落实**（免登录通道专项）：`ruoyi-modules/ruoyi-mes/.../flash/controller/FlashReportController.java:56`（`@Value("${mes.api-key:}")`，无代码内默认值）、`:106`-`110`（未配置或 header 不匹配一律拒绝）。
- **三检枚举与重复检验校验已落实**：`QcIqcServiceImpl.java:151`（非法 inspectResult 抛错）、`:159`（已检验完成不可重复）、`:175`（CAS 0 行抛「状态已变更」）；PDA 侧经 `iqcService.inspect(...)` 复用（`PdaQcServiceImpl.java:101/160/216`），未绕过业务层直改状态。
- **报工/派工并发保护到位**：`ProDispatchServiceImpl.java:235`（按 `stepRecordId` 加锁且覆盖整个事务，注释说明 M1 竞态已修）、`:260`-`268`（CAS `IN_PROGRESS→COMPLETED`）、`:277`-`287`（工序/流转卡 `setSql` 原子累加）、`:290`-`296`（锁内统计 activeCount 再自动完工）。
- **桥接数据源切换正确**：`MesErpBridgeServiceImpl.java:48`-`221` 全部方法 `push("erp")` + `finally poll()`（无泄漏/无错配），未用 `@DS`（注释 `:23`-`:24` 说明为避免与 MES 类级 `@DS("mes")` 冲突）→ 不存在"同类内部调用不触发 AOP 切换"的问题。
- **桥接无 N+1**：`getRoutingSteps`/`getBomDetails` 均单次批量查询（`ErpRoutingStepMapper.java:20`、`ErpBomDetailMapper.java:19`）；工单创建按需 4~5 次单查（`ProWorkOrderServiceImpl.java:479`-`505`），非循环内查询。
- **MQ 消费失败处理合理**：`BaseMessageConsumer.java:92`-`102`（Redis 计数器判重试上限，替代失效的 `x-death` 方案）、`:88`/`:98` 手动 ack/nack（死信），注释记录了原无限重投缺陷已修。
- **发布包状态机守卫完备（除 approve 外）**：`submitForApproval`（`:190` 状态校验 + `:213`-`219` CAS）、`reject`（`:300`+`:303`-`309`）、`archive`（`:327`+`:329`-`335`）、`setDefault`（`:353`+`:360`-`365`）、`deleteByIds`（`:161`-`166` 仅 DRAFT）、`updateByBo`（`:135` 仅 DRAFT/ARCHIVED）；Flowable 回调的 `@DS("mes")`+`REQUIRES_NEW`（`:232`-`233`、`:258`-`259`）与 `getProcInsId` 的 `@DS("master")`（`:434`）位置正确，跨库不落错库。
- **release 前端可达性完整**：`plus-ui/src/api/mes/release/package.ts:117`-`165` 覆盖 `ReleasePackageController` 全部 13 个端点（含 match/list-by-client-material/flows/proc），**未发现"后端有端点但前端无函数"**。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `ReleasePackageVO.status` | `ReleasePackage` 状态常量 + 字典 | `package.ts:67` 仅 `string` | api 层无法判断字典绑定，需在 `views/mes/release/package/index.vue` 核对（**本次未读前端页面**） |
| `ReleasePkgFirmwareVO.routingStepId` / `processId` | 二选一"定位键"（`ReleasePackageServiceImpl.java:502` 校验须指定其一） | `package.ts:7`-`8` 两者均可选，无前端必填约束 | 前端可能提交两者皆空 → 后端抛「须指定工艺路线步骤或工序」；建议前端做二选一校验（**未读页面**） |
| PDA 全量接口 | `AppPda*Controller` 19 端点 | `plus-ui/src/api/mes/**` **无任何对应函数** | 属移动端 App 契约（PDA 客户端不在本仓库），非缺陷；但意味着前端契约无法在仓库内校验 |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：
  1. `MES-MQ-01`：`PrdWorkOrderServiceImpl.sendWorkOrderReleaseMessage` 补 `message.setTenantId(...)`；`BaseMessageConsumer.resolveTenantId` 改 fail-closed（无租户即抛错走死信）。→ 另需数据修复脚本处理已落 000000 的 MES 工单。
  2. `MES-PDA-01`：`PdaSnServiceImpl.bind()` 按 `snCode` 反查 `ProSn.snId` 后落库，SN 不存在返回 fail；补 `DuplicateKeyException` 兜底。
- **批次 B（P1）**：
  3. `MES-PDA-05`（事务内 catch → 500）：优先，因为它让所有报工失败都变成不可读 500。
  4. `MES-PDA-02`/`MES-PDA-03`（幂等键构成 + 并发提前成功）：切面统一改（ERP/MES 共用，需回归 ERP PDA 15 处），需 App 契约同步。
  5. `MES-MQ-02`（消费幂等原子化 + `erp_wo_id` 唯一索引，需 DDL）。
  6. `MES-REL-01`（`doPublish` 校验影响行数）。
  7. `MES-PDA-04`（报工数量语义，**需业务确认**是否需要分批）。
- **批次 C（P2）**：P2 表 9 条按类型合并：桥接租户显式化（MES-BRG-01/02）→ 发布包匹配确定性+唯一键（MES-REL-02/03，需 DDL 复核）→ 其余零散项。
- **测试补充**：MES PDA 目前**零单测**（`ruoyi-modules/ruoyi-mes/src/test` 下无 pda 目录）；建议补 ①切面幂等（首次/重复/失败释放/并发）②SN 绑定（sn_id 落库 + 重复绑定）③`createFromErpMessage` 租户断言 ④`doPublish` 并发。

---

### 未覆盖范围（诚实声明）

- 未读：`AppPda*Controller` 全文（仅按注解 grep 核对权限/映射/入参校验，未逐行读方法体）；`PdaEqServiceImpl.submit` 与 `InspectionResultBo` 的点检枚举校验；`PdaQcServiceImpl` 的 todo/detail 分页与关键字 SQL 细节（`:57`-`:95`、`:113`-`:156`、`:173`-`:211`）；`release` 的 `saveSubTables`/`loadDetails`/`validateSubTables` 全文（`:474`-`:606`）；`common/mq/consumer` 下 `EquipmentStatusConsumer`、`IqcAutoCreateConsumer`、`OqcAutoCreateConsumer`、`SubcontractSnGenerateConsumer`（仅见其 javadoc 声明经 `@DS("mes")` Service 切库，未读实现）；`plus-ui/src/views/mes/release/**` 页面（§6 两条前端字段结论需页面级复核）。
- 未核对 DDL 索引段：`mes_release_package`（`ry-mes.sql:1097`-`1135` 未读）→ MES-REL-03 的"无唯一键"结论待复核。
- 未运行任何代码/测试（只读审计）；`script/sql/ry-vue.sql` 仅 grep 局部行，未整读。
