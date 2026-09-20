# MES 生产执行核心（ruoyi-mes/pro）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro`（工单/派工/报工/流转卡/SN/追溯/MBOM/安灯/首件自检/工序指引）+ 相邻写库方 `ruoyi-modules/ruoyi-station`（设备通道报工）+ 对应前端 `plus-ui/src/api/mes/pro`、`plus-ui/src/views/mes/pro` | 覆盖文件数: 31（实际读过/ grep 核对的 Java/Vue/TS/SQL 文件） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-PRO-01 | P0 | 状态机/数据错 | 流转卡「完工」可无校验手工触发，完工数按工序重算为 0；此后真实完工被 CAS 挡回，工单永久停留 PARTIAL_COMPLETED、ERP 永收不到完工消息 | `ProFlowCardServiceImpl.java:443`、`:490`、`ProFlowCardController.java:147`、`plus-ui/src/views/mes/pro/flow-card/detail.vue:498` |
| MES-PRO-02 | P0 | 并发 | M1 同型残留：`checkAndCompleteStep` 的卡级锁在外层事务提交前释放，"工序是否全完成"判定读不到并发事务未提交的完工 → 工序全 COMPLETED 但卡不完工、工单永不完工 | `ProDispatchServiceImpl.java:235`、`ProFlowCardServiceImpl.java:564`、`:583` |
| MES-PRO-03 | P1 | 功能缺失/事务 | 审批通过回调直接把工单置 RELEASED，绕过 `doReleaseAndSnapshot`；手动来源工单经审批下达后 0 工序、无 BOM 快照 → 流转卡无工序可报工 | `ProWorkOrderServiceImpl.java:1035`、`:349`、`:310` |
| MES-PRO-04 | P1 | 状态机 | `insertByBo/updateByBo` 直接落 BO 的 `woStatus`：可新建即 COMPLETED、可把 COMPLETED 改回 CREATED，绕过完工门禁与 ERP 通知 | `ProWorkOrderServiceImpl.java:223`、`:241`、`ProWorkOrderBo.java:79` |
| MES-PRO-05 | P1 | 事务 | `MesMbomServiceImpl` 同类直调 `doInsertByBo/doUpdateByBo`，`@Transactional` 失效（M2 同型残留）→ MBOM 头/行非同一事务，失败留孤儿头且重试被永久挡住 | `MesMbomServiceImpl.java:118`、`:152`、`:129` |
| MES-PRO-06 | P1 | 功能缺失 | 正向追溯 `forward(componentSnCode)` 返回组件 SN 自己所属工单，未沿 `parent_sn_id` 找成品 SN → 查不到消费该组件的成品工单 | `ProTraceabilityServiceImpl.java:98`、`:107`、`ry-mes.sql:1874` |
| MES-PRO-07 | P1 | 数量口径 | 末道工序跳站后 `completeCard` 取 SKIPPED 工序的 `good_qty`(NULL→0) 作为卡完工数 → 卡完工数=0、工单 +0，永久 PARTIAL_COMPLETED | `ProFlowCardServiceImpl.java:688`、`:469`、`:491` |
| MES-PRO-08 | P1 | 功能缺失 | 安灯仅 SSE 推在线用户，无在线用户即直接返回（只打日志），通知不落库、无离线提醒 → 呼叫丢失 | `ProAndonServiceImpl.java:282`、`:288`、`ry-mes.sql:1691` |
| MES-PRO-09 | P1 | 权限 | `mes:pro:trace:query` 无 `sys_menu` 种子 → 除超管外全员 403，组件追溯页不可用 | `ProTraceabilityController.java:30`、`:41`、`ry-vue.sql`（`mes:pro:*` 45 条种子中无 trace） |
| MES-PRO-10 | P1 | 租户 | `pro_work_order/pro_sn/pro_flow_card/pro_dispatch/pro_andon` 唯一索引均缺 `tenant_id`（`uk_wo_no(wo_no)` 等）→ 跨租户同工单号直接插入失败，ERP→MES 下达链路对该租户不可用 | `ry-mes.sql:1987`、`:1888`、`:1804`、`:1768`、`:1721` |
| MES-PRO-11 | P1 | 状态机 | 工单 `CANCELLED`/`CLOSED` 为死状态：全模块无写入点，无取消/关闭入口（唯一"写入"途径是 MES-PRO-04 的绕过） | `IProWorkOrderService.java:35`、`ProWorkOrderServiceImpl.java:257`、`ry-vue.sql:90121` |
| MES-PRO-12 | P2 | 幂等 | 安灯号/派工号用 `System.currentTimeMillis()%10000`，10 秒周期内可重号 → 唯一键冲突 500 | `ProAndonServiceImpl.java:272`、`ProDispatchServiceImpl.java:323` |
| MES-PRO-13 | P2 | 性能 | `pro_andon` 无 `step_record_id` 索引，而 EXIT 的 EXCEPTION_CLOSED 校验每次按该列查 | `ry-mes.sql:1720`、`ProEntryCheckServiceImpl.java:274` |
| MES-PRO-14 | P2 | 校验 | SN 批量生成的 `initialStatus` 未校验枚举值，任意字符串可落库 | `ProSnController.java:91`、`ProSnServiceImpl.java:124` |
| MES-PRO-15 | P2 | 契约 | 工单/流转卡/SN 的 `/export` 端点与菜单种子都在，前端既无 api 函数也无导出入口 | `ProSnController.java:48`、`plus-ui/src/api/mes/pro/sn.ts`、`views/mes/pro/sn/index.vue` |
| MES-PRO-16 | P2 | 功能缺失 | SN 无解绑/父子关系纠错端点，绑错只能报废重开 | `ProSnController.java:86-141` |
| MES-PRO-17 | P2 | 性能 | 按物料正向追溯无分页且逐工单 N+1 查询（每个工单再跑 3 条 SQL） | `ProTraceabilityServiceImpl.java:110`、`:122` |
| MES-PRO-18 | P2 | 数量口径 | 同一工序的报工数量被两条路径以不同口径写：派工路径 `setSql` 累加、出站路径 `set` 覆写 | `ProDispatchServiceImpl.java:277`、`ProFlowCardServiceImpl.java:284` |
| MES-PRO-19 | P2 | 幂等 | ERP 工单幂等只靠 `uk_wo_no`，`erp_wo_id` 仅普通索引；同一 `erp_wo_id` 以新 `wo_no` 重发会生成第二张 MES 工单 | `ProWorkOrderServiceImpl.java:468`、`ry-mes.sql:1988` |

## 1. P0 详述

### MES-PRO-01 流转卡可无校验手工完工，导致完工数量永久丢失、工单卡死 PARTIAL_COMPLETED
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java:443`、`:452`、`:460`、`:469`、`:490`、`:505`、`:510`；`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProFlowCardController.java:145`、`:147`；`plus-ui/src/api/mes/pro/flowcardApi.ts:106`；`plus-ui/src/views/mes/pro/flow-card/detail.vue:498`
- **现状**: `completeCard` 的唯一前置条件是状态 CAS，且**允许从 CREATED/RELEASED 直接完工**：
  ```java
  int rows = baseMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
      .set(ProFlowCard::getCardStatus, STATUS_COMPLETED)
      .eq(ProFlowCard::getCardId, cardId)
      .in(ProFlowCard::getCardStatus, STATUS_CREATED, STATUS_RELEASED, STATUS_IN_PROGRESS)); // :446
  if (rows == 0) { return; }
  ```
  随后完工数不是"本次产出"，而是从工序记录**重算**：`correctCompletedQty = 最后工序的 good_qty`（`:469-472`，工序未报工时 `good_qty` 为 NULL → 0），再以 `completed_qty = completed_qty + correctCompletedQty` 聚合到工单（`:491`）。该方法被 `PUT /mes/pro/flow-card/complete/{cardId}` 直接暴露（`ProFlowCardController.java:147`，权限 `mes:pro:flowcard:complete`，种子见 `ry-vue.sql:92424`），前端按钮 `handleCompleteFlowCard`（`detail.vue:498-503`）只弹一次确认，**不校验任何工序是否完成**。
- **触发路径**: 流转卡创建后（未开工/未报工）在流转卡详情页点「完工」→ 卡置 COMPLETED、`completed_qty=0`；工单被判 `totalCompleted+totalScrap(=0) < plannedQty` 而置 `PARTIAL_COMPLETED`（`:510-515`）。此后工人正常派工/报工，工序逐个完工，`checkAndCompleteStep`（`:587`）再调 `completeCard` → CAS 命中 0 行（卡已 COMPLETED）→ `return`（`:447-450`）→ **工单数量再也不聚合**，`completeWo` 永不触发（`ProWorkOrderServiceImpl.java:1157` 的 CAS 需要 RELEASED/IN_PROGRESS/PARTIAL_COMPLETED，而数量判定的入口已被吞掉），`ERP_WORKORDER_COMPLETED_QUEUE` 永不发送。
- **后果**: ①流转卡/工单完工数量与实际产出脱节且不可自愈（账面 0）；②ERP 收不到完工消息 → 完工入库、倒冲、销售订单进度全部不推进；③超报/漏报在对账时无法发现，因为卡与工单都显示"已完工"。属于库存/资金口径级数据错。
- **修复建议**: ①`completeCard` 去掉 CREATED/RELEASED 两个前置状态，只允许 `IN_PROGRESS → COMPLETED`（手工完工也必须先开工）；②在 `completeCard` 开头增加硬门禁：`stepMapper.selectCount(cardId 且 step_status NOT IN (COMPLETED,SKIPPED)) > 0` 时抛 `ServiceException`（与 `checkAndCompleteStep` 的判定同源）；③工单数量聚合改为"以卡为单位的一次性入账"：新增 `pro_flow_card.aggregated_flag`（CAS `0 → 1` 成功才 `completed_qty = completed_qty + ...`），杜绝 CAS 早退导致的漏聚合。需 DDL（新增列）；影响 `ProFlowCardController.complete`、前端 `handleCompleteFlowCard`；需补并发/重复完工单测。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/pro/service/impl/` 下存在 `ProWorkOrderServiceImplTest`，但**未见 `ProFlowCardServiceImplTest`**，卡完工聚合无单测覆盖。

### MES-PRO-02 M1 同型残留：卡级锁在事务提交前释放，"工序全完成"判定存在竞态窗口
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProDispatchServiceImpl.java:229`、`:235`、`:241`、`:250`、`:290`、`:294`；`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java:564`、`:567`、`:583`、`:587`
- **现状**: 基线报告 §2.3 M1 的修复只做到了**派工步级锁**：`completeDispatch`（无事务）先按 `stepRecordId` 取锁，再经 AOP 调用带事务的 `doCompleteDispatch`，锁在事务提交后才释放（`:235-245`），这部分是有效的。但 `doCompleteDispatch` 内部又调 `proFlowCardService.checkAndCompleteStep(cardId, stepRecordId)`（`:295`），而该方法的 `@Lock4j(keys={"#cardId"})`（`ProFlowCardServiceImpl.java:564`）**是它自己方法的环绕通知**：方法返回即释放锁，此时外层 `doCompleteDispatch` 的事务**尚未提交**。紧接着 `pendingCount` 判定（`:583-586`）与 `completeCard`（`:587`）都在该锁内执行：
  ```java
  Long pendingCount = stepMapper.selectCount(... .ne(stepStatus, COMPLETED).ne(stepStatus, SKIPPED)); // :583
  if (pendingCount == 0) { completeCard(cardId); }
  ```
- **触发路径**: 同一张流转卡的两道工序各有一张派工单，两名操作员几乎同时点「完工」：A 拿步锁 1、B 拿步锁 2（互不阻塞）→ A 先进 `checkAndCompleteStep` 拿卡锁，把自己的工序 CAS 成 COMPLETED，读 `pendingCount`（B 的工序尚未更新，且 B 的事务未提交/不可见）→ 1，不完工，释放卡锁（事务未提交）→ B 拿到卡锁，读 `pendingCount` 时**看不到 A 未提交的更新**（READ COMMITTED）→ 仍为 1，不完工 → 两事务各自提交。结果：所有工序 COMPLETED，流转卡仍 IN_PROGRESS。
- **后果**: 与 M1 原缺陷同型：卡不完工 → 工单完工数量不聚合（`ProFlowCardServiceImpl.java:490`）→ 工单永不 COMPLETED → ERP 无完工通知、完工入库/倒冲不触发；且没有任何补偿任务能自愈（工序已全部 COMPLETED，不会再有报工触发 `checkAndCompleteStep`）。**即 M1 只修了"步级"，"卡级聚合判定"仍是读-改-写**。
- **修复建议**: ①把卡锁提到与事务同生命周期：在 `completeDispatch` 入口同时取 `mes:dispatch:step:{stepRecordId}` 与 `mes:flowcard:{cardId}` 两级程序化锁，统一在 `finally` 中、**事务提交之后**释放；②或把卡完工判定改为条件更新 + 行数校验：`UPDATE pro_flow_card SET card_status='COMPLETED' WHERE card_id=? AND card_status='IN_PROGRESS' AND NOT EXISTS(SELECT 1 FROM pro_flow_card_step WHERE card_id=? AND step_status NOT IN ('COMPLETED','SKIPPED'))`，由数据库隔离级别消除可见性问题。影响 `ProDispatchServiceImpl`、`ProFlowCardServiceImpl`、`ruoyi-station/StationPassService.java:369`（同样调用 `completeCard`）；无需 DDL；**必须补并发单测**（两派工并发完工）。
- **测试现状**: 无并发单测；`ProDispatchServiceImpl` 无对应测试类。

## 2. P1 详述

### MES-PRO-03 审批下达路径不生成工序、不落 BOM 快照
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java:1032`、`:1035`、`:1046`、`:974`、`:976`、`:349`、`:310`、`:297`
- **现状**: 工序与 BOM 快照**只在 `doReleaseAndSnapshot` 里生成**（`:310-321`），而该方法只被 `release()`（直达下达，`:297`）经 AOP 调用。审批流通过时，`afterFlowHandle` 走的是另一条路：
  ```java
  } else if (ActStatus.pass.equals(actStatus)) {
      entity.setWoStatus(STATUS_RELEASED);   // :1036
      changed = true;
  }
  ...
  if (changed) { baseMapper.updateById(entity); }   // :1046 直接整行覆盖
  ```
  代码注释自己也承认这一点（`:974-976`："审批通过回调(afterFlowHandle)将直接置 RELEASED 不再走 doReleaseAndSnapshot"），其兜底手段是提交审批前调用 `assertFlashStepsBound`（`:976`）——但该方法在**工序数为 0 时直接 return**（`:353-355`），因此对"还没有任何工序"的手动来源工单完全不设防。
- **触发路径**: 新建 MES 工单（`source_type=MANUAL`，选工艺路线）→ 编辑页选审批流程 → 「提交审批」→ 审批通过 → 工单 RELEASED，但 `pro_work_order_step` / `pro_work_order_bom_line` 均为空 → 创建流转卡时 `generateCardStepsFromWorkOrder` 复制 0 条工序（`ProFlowCardServiceImpl.java:203`）→ 流转卡无工序，无法开工/报工/跳站。
- **后果**: 走审批流下达的手动工单（以及 REWORK 工单）在生产侧完全不可执行，且界面显示"已下达"，属功能不可用；ERP 来源工单因创建时已快照工序（`:573-582`）不受影响。
- **修复建议**: 在 `afterFlowHandle` 的 `ActStatus.pass` 分支改为经 AOP 调用 `doReleaseAndSnapshot(mesWoId, entity, routingSteps, mbom, releasePackage)`（桥接读取路由/MBOM 必须在事务外，参照 `release()` 的写法），或至少补齐"工序为空则生成工序 + 落 BOM 快照"的幂等步骤；同时把 `assertFlashStepsBound` 的兜底改为"无工序时拒绝下达"。影响 `ProWorkOrderServiceImpl`、审批流程 `WfMyBusiness` 回调；无 DDL；需补"审批通过后工序已生成"的测试。
- **测试现状**: `ProWorkOrderServiceImplTest`（`ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImplTest.java:346-423`）仅覆盖状态判定，未覆盖审批回调路径。

### MES-PRO-04 `insertByBo/updateByBo` 直写 woStatus，状态机可被绕过
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java:223`、`:225`、`:241`、`:243`；`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/bo/ProWorkOrderBo.java:79`；`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProWorkOrderController.java:70`、`:81`
- **现状**:
  ```java
  public int insertByBo(ProWorkOrderBo bo) {
      ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
      if (StringUtils.isBlank(entity.getWoStatus())) { entity.setWoStatus(STATUS_CREATED); }  // :225 非空则用前端传值
      int rows = baseMapper.insert(entity);
  }
  public int updateByBo(ProWorkOrderBo bo) {
      ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
      return baseMapper.updateById(entity);   // :243 整行按 BO 覆盖，含 wo_status
  }
  ```
  `ProWorkOrderBo` 暴露了 `woStatus`（`:79`），Controller 的 `add`（`:70`）/`edit`（`:81`）都是 `@RequestBody` 直传 BO。
- **触发路径**: ①`POST /mes/pro/work` 带 `woStatus:"COMPLETED"` → 工单创建即"已完工"，`completed_qty=0`，跳过下达/派工/报工/IPQC 门禁与 MQ 通知；②`PUT /mes/pro/work` 把已 COMPLETED 的工单改回 `CREATED`（或改成 `RELEASED`）→ 可重复走完工流程/重复下达；③配合 MES-PRO-11，用户只能用这个入口把工单"取消"。
- **后果**: 状态机形同虚设：完工门禁（IPQC `:1115-1153`）、数量守恒判定、ERP 完工通知都可被一次普通编辑绕过；且 `updateById` 是整行覆盖，前端表单未渲染的字段也可能被置空（`actualStartDate` 等）。
- **修复建议**: ①`ProWorkOrderBo` 删除 `woStatus`（或 `@JsonIgnore` / 在 `insertByBo` 强制 `setWoStatus(STATUS_CREATED)` 并忽略入参）；②`updateByBo` 只更新业务可编辑字段（计划量/日期/备注/优先级），状态一律由 `release/submit/completeWo/cancel` 专有方法经 CAS 变更；③若确需"取消/关闭"能力，按 MES-PRO-11 增加带 CAS 与权限的端点。影响 `ProWorkOrderController`、前端 `plus-ui/src/api/mes/pro/workorder.ts:23,32` 与工单表单；无 DDL；需补"编辑不改变状态"的单测。
- **测试现状**: `ProWorkOrderServiceImplTest:346-423` 只测删除/下达判定，未覆盖 BO 传 status。

### MES-PRO-05 `MesMbomServiceImpl` 的 `@Transactional` 因同类直调失效（M2 同型残留）
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/MesMbomServiceImpl.java:114`、`:118`、`:124`、`:135`、`:148`、`:152`、`:158`、`:172`；对照正确写法 `ProWorkOrderServiceImpl.java:297`、`:527`
- **现状**: 该类**没有 import `SpringUtils`**（import 清单见 `:3-29`），桥接校验后直接同类调用：
  ```java
  public int insertByBo(MesMbomBo bo) {
      validateLinesAgainstErp(bo.getRoutingId(), bo.getLines());   // 事务外
      return doInsertByBo(bo);                                      // :118 直调 → 代理被绕过
  }
  @Transactional(rollbackFor = Exception.class)
  public int doInsertByBo(MesMbomBo bo) { ... baseMapper.insert(entity); saveLines(...); }  // :124-138
  ```
  `updateByBo → doUpdateByBo` 同样（`:152`）。而 `ProWorkOrderServiceImpl` 在同类调用处显式使用了 `SpringUtils.getAopProxy(this)`（`:297`、`:527`），说明这是本模块既定的修复模式，MBOM 是漏网。
- **触发路径**: `POST /mes/pro/mbom` 保存 MBOM，明细行 `saveLines`（`:305`）任一行插入失败（物料/工序校验漏网、字段超长、并发删改）→ MBOM 主记录已自动提交（无事务），明细全丢。
- **后果**: 产生"有头无行"的孤儿 MBOM；再次保存时 `doInsertByBo` 的查重（`:127-131`）会命中自己并抛 "该ERP BOM已存在对应的MBOM，请在MBOM列表中编辑"，用户需人工清库才能恢复；`updateByBo` 更糟——先删明细（`:172`）再插，失败即**明细永久丢失**（原数据已删且不回滚）。
- **修复建议**: 与工单一致改为 `SpringUtils.getAopProxy(this).doInsertByBo(bo)` / `.doUpdateByBo(bo)`（或把桥接校验拆到 Controller/独立 `@DS` 服务后再让 `insertByBo` 自身标 `@Transactional`）。影响 `MesMbomController.add/edit`、前端 `plus-ui/src/api/mes/pro/mbom.ts:21,26`；无 DDL；需补"明细失败时主记录不落库"的单测。
- **测试现状**: `ruoyi-mes/src/test` 下无 `MesMbom*Test`。

### MES-PRO-06 正向追溯按组件 SN 查不到成品工单
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProTraceabilityServiceImpl.java:98`、`:102`、`:107`、`:123`、`:126`；`script/sql/ry-mes.sql:1874`
- **现状**: 表注释明确 `parent_sn_id` 的方向是"组件SN指向成品SN"（`ry-mes.sql:1874`），`backward` 也按此方向找子件（`:73-74` 用 `parent_sn_id = 成品SN`）。但 `forward(componentSnCode)` 拿到组件 SN 后直接把它自己的工单当结果：
  ```java
  ProSn componentSn = snMapper.selectOne(... .eq(ProSn::getSnCode, componentSnCode));   // :102
  woIds = List.of(componentSn.getMesWoId());                                            // :107 ← 组件自己的工单
  ```
  全程未使用 `componentSn.getParentSnId()`，因此永远走不到"消费该组件的成品工单"。
- **触发路径**: 组件追溯页输入某电容的 SN → 返回的是该电容自己的生产工单，而非装配了它的成品工单；随后 `:126-128` 又把该工单里 `parent_sn_id IS NULL` 的 SN 当作"父SN链"返回，语义进一步错位。
- **后果**: 质量事故（来料/半成品批次不良）无法正向定位受影响的成品与客户，追溯链在最关键的"向上"方向断裂；而"按物料号"路径（`:110-116`）能返回候选工单，二者结论不一致会误导质量判定。
- **修复建议**: `forward(componentSnCode)` 改为：组件 SN → `parentSnId` → 成品 SN（可沿链上行，复用 `ProSnServiceImpl.trace` 的限深循环 `MAX_PARENT_DEPTH`）→ 取成品 SN 的 `mesWoId` 作为 `woIds`；同时修正返回结构：`parentSnChain` 放上行链、`assembledComponentSns` 放下行件。影响 `ProTraceabilityController.forward`、前端追溯页；无 DDL；需补"组件→成品"的追溯单测。
- **测试现状**: 无 `ProTraceability*Test`。

### MES-PRO-07 末道工序跳站导致卡/工单完工数量为 0
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java:688`、`:711`、`:717`、`:460`、`:469`、`:491`
- **现状**: `skip()` 只把工序置 `SKIPPED`（不写任何数量），随后在"全部工序 COMPLETED/SKIPPED"时调用 `completeCard`（`:711-718`）。`completeCard` 取**序号最大的工序**的 `good_qty` 作为整卡完工数：
  ```java
  List<ProFlowCardStep> allSteps = ... .orderByDesc(ProFlowCardStep::getSequenceNum);   // :460-462
  if (i == 0) { correctCompletedQty = stepGood; }   // :469-472  SKIPPED 工序 good_qty=NULL→0
  ```
- **触发路径**: 多工序流转卡，最后一道工序（或全部工序）被跳站（`POST /mes/pro/flow-card/skip`，权限种子 `ry-vue.sql:92557`）→ 卡 COMPLETED 且 `completed_qty=0`、`scrap_qty=Σ工序报废` → 工单 `completed_qty += 0` → 数量判定 `totalCompleted+totalScrap < plannedQty`（`:499`）→ 工单置 PARTIAL_COMPLETED（`:510`），且此后不会再有任何卡完工事件来补足 → 工单永久无法完工、ERP 无完工通知。
- **后果**: 跳站场景下产量凭空消失（卡与工单均记 0），且工单死锁在 PARTIAL_COMPLETED；与 MES-PRO-01 同源（都是"完工数量取自工序、且缺聚合入账保护"）。
- **修复建议**: ①`completeCard` 计算完工数时跳过 SKIPPED 工序，取**最后一个 COMPLETED 工序**的 `good_qty`；②末道工序跳站时要求补录产出数量（在 `skip()` 增加"末道工序跳站需填 goodQty"校验）；③与 MES-PRO-01 的修复合并（卡完工聚合加一次性入账标记）。影响 `ProFlowCardController.skip`、前端 `flowcardApi.ts:140`；无 DDL（方案③需 DDL）；需补跳站单测。
- **测试现状**: 无 `ProFlowCardServiceImplTest`。

### MES-PRO-08 安灯无离线通知、通知不落库
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProAndonServiceImpl.java:282`、`:287`、`:288`、`:298`；`script/sql/ry-mes.sql:1691-1727`
- **现状**: `publishAndonSse` 是唯一的通知手段，且无在线用户时直接返回：
  ```java
  Set<Long> userIds = collectOnlineUsersByTenant(LoginHelper.getTenantId());
  if (CollUtil.isEmpty(userIds)) {
      log.debug("无同租户在线用户, 安灯推送仅落日志: andonId={}", vo.getAndonId());   // :288
      return;
  }
  ```
  `pro_andon` 表只有状态/时间/人字段（`ry-mes.sql:1691-1727`），**没有任何通知/接收人/已读字段，也没有通知明细表**；全类无 IM/站内信/短信兜底。
- **触发路径**: 夜班/换班期间或看板无人登录时，操作员呼叫安灯（`POST /mes/pro/andon/call`）或 IoT 设备告警经 `MesAndonServiceImpl.createAndonFromAlert`（`ruoyi-mes/.../MesAndonServiceImpl.java:42-50`）创建安灯 → 无在线用户 → 只写一条 debug 日志，呼叫永久无人知晓，直到有人主动打开看板列表。
- **后果**: 安灯的核心价值（即时触达）在"无人登录"这一最常见场景下失效；且事后无法追溯"当时通知了谁、谁未响应"，责任链缺失。
- **修复建议**: ①呼叫/响应/解决时除 SSE 外写一条站内通知（复用 `ruoyi-im` 的 `SysUserNotification`/`ImRealtimePush`）或经 MQ 投递到 IM/短信通道，保证离线可达；②`pro_andon` 增加 `notify_user_ids`/`notify_time`（或独立 `pro_andon_notify` 表）落库通知记录；③无在线用户时的日志级别从 debug 提升为 warn 并计数告警。影响 `ProAndonServiceImpl`、`MesAndonServiceImpl`（IoT 通道）、前端安灯看板；需要 DDL（新增列/表）；需补"无在线用户仍产生通知记录"的单测。
- **测试现状**: 无 `ProAndon*Test`。

### MES-PRO-09 `mes:pro:trace:query` 权限码无菜单种子
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProTraceabilityController.java:30`、`:41`；`script/sql/ry-vue.sql`（`mes:pro:*` 权限码种子共 45 条：`92357-92448`、`92557`，**无 `mes:pro:trace:query`**）
- **现状**: 组件追溯的两个端点都要求 `@SaCheckPermission("mes:pro:trace:query")`，但该权限码在 `sys_menu` 里没有任何 C/F 记录，无法通过角色分配获得。
- **触发路径**: 非超管用户（生产/质量主管）访问 `/mes/pro/trace/backward`、`/trace/forward` → 403；即使前端有页面/按钮，也无法授权。
- **后果**: 组件追溯功能对全部非超管角色不可用（本项目高发的"权限码无种子"坑），且超管以外无法自助开通。
- **修复建议**: 在 `ry-vue.sql` 的 MES 菜单块（紧随 `92557` 之后）补 `sys_menu` 记录：一个 `C` 型菜单（挂在 4001 下，component 如 `mes/pro/trace/index`）+ 一个 `F` 型按钮 `mes:pro:trace:query`；若前端暂无页面，至少补 F 型按钮种子以保证接口可授权。影响 `sys_menu` 种子与角色菜单勾选；需要 DDL（种子 INSERT，存量库需增量脚本）；无需代码改动。
- **测试现状**: 无权限码与 `@SaCheckPermission` 的一致性测试（建议加一条扫描式单测，覆盖本模块全部权限码）。

### MES-PRO-10 `pro_*` 唯一索引缺 `tenant_id`，跨租户同号即失败
- **证据**: `script/sql/ry-mes.sql:1987`（`UNIQUE INDEX uk_wo_no(wo_no)`）、`:1888`（`uk_sn_code(sn_code)`）、`:1804-1805`（`uk_card_no(card_no)`、`uk_barcode(barcode)`）、`:1768`（`uk_dispatch_no(dispatch_no)`）、`:1721`（`uk_andon_no(andon_no)`）；对照同文件多租户正确写法 `:496`（`uk_tenant_code(tenant_id, spare_code)`）、`:1125`、`:1269`
- **现状**: 同一份 `ry-mes.sql` 中，其它模块的唯一索引普遍带 `tenant_id` 前缀，但 `pro_*` 五张表的业务唯一键全部**不含 `tenant_id`**，而这些表都有 `tenant_id` 列并被多租户插件过滤（`ry-mes.sql:1951`、`:1863`、`:1693`）。其中 `wo_no` 直接继承 ERP 工单号（`ProWorkOrderServiceImpl.java:545`：`workOrder.setWoNo(message.getWoNo())`），`sn_code` 由 `SN-{cardNo}-{seq}` 派生（`ProSnServiceImpl.java:384-386`），`card_no` 由工单派生——都是**租户内唯一**而非全局唯一。
- **触发路径**: 两个租户各自在 ERP 建立编号相同的工单（如 `MO-20260920-001`）→ 租户 B 的 ERP 下达消息到达 MES，`createFromErpMessage` 幂等检查按 `erp_wo_id` 查不到（`:468-473`），插入时撞 `uk_wo_no` → 抛唯一键异常 → 消费者重试仍失败。SN/流转卡/派工/安灯同理（`SN-MO-xxx-0001` 在两租户下同码）。
- **后果**: 多租户部署下，第二个租户的 MES 工单下达链路整体不可用（持续失败且无法自愈）；SN 批量生成会在插入时中断（`ProSnServiceImpl.java:145-147` 循环插入，失败反复出现）。
- **修复建议**: 把这五个唯一索引改为带租户维度（`UNIQUE INDEX uk_tenant_wo_no(tenant_id, wo_no)` 等），并同步把 `erp_wo_id` 升为 `UNIQUE(tenant_id, erp_wo_id)`（同时解决 MES-PRO-19）。**需要 DDL + 存量库增量脚本**（先删旧唯一键再建新键，需先核对存量是否已有跨租户重号）；影响 ERP→MES 下达、SN 生成、流转卡/派工创建；需补多租户隔离单测。
- **测试现状**: 无多租户唯一性测试。

### MES-PRO-11 工单 `CANCELLED`/`CLOSED` 死状态，无取消/关闭入口
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProWorkOrderService.java:35`；`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java:257`（唯一引用处，仅作为删除前置判断读取）；`script/sql/ry-vue.sql:90121`（字典 `CLOSED`）、`:90122`（字典 `CANCELLED`）
- **现状**: 全模块对 `wo_status` 的写入点只有：`CREATED`（`:226`、`:558`、`:1041`）、`APPROVING`（`:1033`）、`RELEASED`（`:326`、`:1036`）、`IN_PROGRESS`（`ProFlowCardServiceImpl.java:256`）、`PARTIAL_COMPLETED`（`ProFlowCardServiceImpl.java:511`）、`COMPLETED`（`:1158`），**没有任何地方写 `CANCELLED` 或 `CLOSED`**（`STATUS_CANCELLED` 仅在 `deleteByIds` 的判断里被读取）。字典里两个状态（已关闭/已取消）在 UI 下拉和 `dict-tag` 里可见，但永远不会出现。
- **触发路径**: 生产计划取消 → 用户在工单列表找不到"取消/关闭"按钮（`plus-ui/src/views/mes/pro/workorder/` 中无对应操作），只能删除（仅 CREATED/CANCELLED 可删，`:253-261`）→ 已下达/已开工的工单既不能取消也不能关闭，只能长期挂在进行中，或用户走 MES-PRO-04 的编辑接口手工把状态改成 `CANCELLED` 来"假装取消"。
- **后果**: ①死状态 + 死字典值，UI 展示了不可达的状态；②工单列表长期堆积无效工单，`PARTIAL_COMPLETED`/`IN_PROGRESS` 统计与计划达成率失真；③唯一可用的"取消"手段是绕过状态机（MES-PRO-04），无审计日志、无 CAS 保护。
- **修复建议**: 在 `IProWorkOrderService` 增加 `cancel(Long mesWoId, String reason)` / `close(Long mesWoId)`：`cancel` 允许 `CREATED/RELEASED/APPROVING → CANCELLED`（CAS + 要求该工单下无在制流转卡，否则拒绝），`close` 允许 `COMPLETED/PARTIAL_COMPLETED → CLOSED`；补 `sys_menu` 按钮种子（`mes:pro:work:cancel`/`mes:pro:work:close`）与前端按钮。影响 `ProWorkOrderController`、`plus-ui/src/api/mes/pro/workorder.ts`、工单列表页；需要 DDL（菜单种子）；需补状态流转单测。
- **测试现状**: `ProWorkOrderServiceImplTest:346-423` 覆盖 `deleteByIds` 判定，未覆盖取消/关闭。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果 | 建议 |
|---|---|---|---|---|
| MES-PRO-12 | 安灯号/派工号用 `System.currentTimeMillis()%10000` 生成，10 秒周期内可重号 | `ProAndonServiceImpl.java:272-276`、`ProDispatchServiceImpl.java:323-326`、`ry-mes.sql:1721`、`:1768` | 同一 10 秒相位再次生成即撞唯一键 → 呼叫安灯/创建派工直接 500，用户看到数据库异常 | 改为「日期 + 数据库序列/Redis 自增」或 `IdUtil.getSnowflakeNextIdStr()` 后缀；无 DDL |
| MES-PRO-13 | `pro_andon` 缺 `step_record_id` 索引，而 EXIT 的 `EXCEPTION_CLOSED` 规则每次按该列查 | `ry-mes.sql:1720-1726`（仅 `andon_status`/`call_type`/`mes_wo_id`/`card_id`/`station_code` 索引）、`ProEntryCheckServiceImpl.java:274-276` | 安灯表随运行无限增长，出站校验全表扫描 → 工位出站变慢 | 增加 `INDEX idx_step_record_id(tenant_id, step_record_id)`；需要 DDL |
| MES-PRO-14 | SN 批量生成的 `initialStatus` 未校验枚举 | `ProSnController.java:91`、`ProSnServiceImpl.java:124`（`StringUtils.isNotBlank` 即直接用） | 前端可传任意值（如 `"SHIPPED"`/拼写错误）落库，`mes_sn_status` 字典外的状态无法用 `dict-tag` 展示，且可绕过状态机直接以已发货态造码 | 在 `generateSnForCard` 用白名单校验（`IN_PRODUCTION/COMPLETED`），非法值抛 `ServiceException`；无 DDL |
| MES-PRO-15 | 工单/流转卡/SN 的 `/export` 端点与菜单种子齐备，前端无 api 函数也无导出入口 | `ProSnController.java:48-51`、`ProFlowCardController.java:52-55`、`ProWorkOrderController.java:48-51`、`ry-vue.sql:92413`、`:92425`、`:92448`、`plus-ui/src/api/mes/pro/sn.ts`、`flowcardApi.ts`、`workorder.ts`（均无 export 函数）、`views/mes/pro/**`（仅 `step-guide/index.vue:266`、`step-check-rule/index.vue:230` 配了 `exportUrl`） | 三个导出按钮在菜单里配了权限却无 UI 入口，功能对用户不可达 | 在对应 api 文件补导出函数并在页面接 `exportUrl`（复用 `step-guide` 写法），或删除无用菜单种子；无 DDL |
| MES-PRO-16 | SN 无解绑/父子关系纠错端点 | `ProSnController.java:86-141`（仅 generate/add/complete/scrap/ship/查询）；`ProSnServiceImpl.java:217-266`（`createSn` 可设 `parentSnId`，之后无任何更新方法） | 组装关系录错（父SN选错、SN 错绑到别的卡）无法纠正，只能报废重开，报废会污染 `scrap_qty`/报废率统计 | 增加 `PUT /mes/pro/sn/{snId}/unbind`（清 `parent_sn_id`/`parent_sn_code`，CAS + 权限码）与改绑端点并记审计；无 DDL（需补菜单种子） |
| MES-PRO-17 | 按物料正向追溯无分页、逐工单 N+1 | `ProTraceabilityServiceImpl.java:110-116`（一次查出全部匹配工单）、`:122-123`（循环调 `backward`，每次 3 条 SQL）、`:126-128`（再一次按工单查 SN） | 通用物料（如螺丝）被上千张工单引用时，单次追溯放大到数千条 SQL，接口超时/拖垮 DB | 加分页参数，`backward` 改批量查询（一次 `IN` 取 BOM 行与组件 SN）；无 DDL |
| MES-PRO-18 | 同一工序数量被两条路径以不同口径写入：派工路径 `setSql` 累加、出站路径 `set` 覆写 | `ProDispatchServiceImpl.java:277-281`（`good_qty = good_qty + {0}`）、`ProFlowCardServiceImpl.java:282-290`（`set(goodQty)` 覆写） | 同一工序既走过派工报工又走出站报工时，后一次出站会整体覆盖派工累计值，工序/卡数量与实际不符（`completeCard` 又从工序重算，误差传导到工单完工数） | 统一为"事件累加 + 上限校验"，或禁止同一工序混用两条路径（在 `completeStep` 校验该工序无已完成派工，反之亦然）；无 DDL |
| MES-PRO-19 | ERP 工单幂等只靠 `uk_wo_no`，`erp_wo_id` 仅普通索引 | `ProWorkOrderServiceImpl.java:468-473`（先查后插 TOCTOU）、`ry-mes.sql:1987-1988`（`uk_wo_no` 唯一、`idx_erp_wo_id` 非唯一） | 同一 `erp_wo_id` 若以新 `wo_no` 重发（ERP 改单/重发新号）会生成第二张 MES 工单，同一 ERP 工单被生产两次 | 把 `erp_wo_id` 升为 `UNIQUE(tenant_id, erp_wo_id)`（与 MES-PRO-10 一并做）；需要 DDL |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 工单取消/关闭入口 | 无写入点，`CANCELLED` 仅被读取（`ProWorkOrderServiceImpl.java:257`） | 字典 `prd_wo_status` 已有 CANCELLED/CLOSED（`ry-vue.sql:90121-90122`）、常量 `IProWorkOrderService.java:35` | 0.5~1 人日（Service+CAS+菜单+前端按钮） |
| 2 | MBOM 明细级倒冲标志无消费方 | `backflush_flag` 在 MES 侧唯一写入点是快照 `ProWorkOrderServiceImpl.java:867`，全仓无读取方；ERP 倒冲只按工单级 `prd_work_order.backflush_flag`（`PrdBackflushServiceImpl.java:72`、`:187` 注释"明细 backflush_flag 不再作为过滤条件"） | 字段齐备：`MesMbomLineBo.java:40`、`ProWorkOrderBomLine.java:57`、表列 `ry-mes.sql:2021` | 1~2 人日（需先定义"工序级倒冲"语义，再改 `PrdBackflushServiceImpl` 取数口径） |
| 3 | 安灯超时升级 | `pro_andon` 无 level/timeout/escalate 字段（`ry-mes.sql:1691-1727`），无定时任务；`ProAndonServiceImpl` 无升级逻辑 | `call_time/respond_time/resolve_time/duration_minutes` 可作超时判定基础 | 1~2 人日（DDL + SnailJob 扫描 + 通知） |
| 4 | 安灯离线通知落库 | 仅 SSE，无在线用户即丢弃（`ProAndonServiceImpl.java:287-290`） | 可复用 `ruoyi-im` 的 `SysUserNotification`/`ImRealtimePush` | 1 人日 |
| 5 | SN 解绑/父子纠错 | 见 MES-PRO-16 | `parent_sn_id`/`parent_sn_code` 字段齐备 | 0.5 人日 |
| 6 | ENTRY/EXIT 校验存根 | `MATERIAL_KITTING`/`OPERATOR_SKILL` 启用时直接 PASS 并告警（`ProEntryCheckServiceImpl.java:121-125`）；`PARAMETER_OK` 同样（`:226-229`） | 规则表 `pro_step_check_rule`、规则引擎与 `dispatchEntry/dispatchExit` 骨架已就绪 | 各 1~2 人日（缺物料齐套算法/资质表/dc 参数达标算法） |
| 7 | 报工工时 → 考勤/绩效联动 | 报工只写 `actual_start_time/actual_end_time`（`ProFlowCardServiceImpl.java:237`、`:287`），`ruoyi-mes/pro` 无任何工时汇总方法；`ruoyi-erp/sch` 全模块无对 MES 报工的引用（grep `pro_flow_card`/`Mes` 无命中） | 工序起止时间、操作员、设备/工位均已在 `pro_flow_card_step`/`pro_dispatch` 落库 | 2~3 人日（工时聚合表/视图 + 与 `sch_attendance`/绩效口径对齐） |
| 8 | 追溯"后向父SN链"在 `backward` 中未实现 | 类注释声称"后向(沿 parent_sn_id 父SN链)"（`ProTraceabilityServiceImpl.java:40`），但 `backward` 只查子件，`parentSnChain` 仅在 `forward` 里被填（`:135`），且填的是"无父SN的成品SN" | `ProSnServiceImpl.trace`（`:361-374`）已有可复用的限深上行循环 | 0.5 人日 |

## 5. 已核查确认无问题（证明覆盖范围）

- **M2（release/createFromErpMessage 事务自调用）已修复且彻底**：两处均显式经 AOP 代理调用 `SpringUtils.getAopProxy(this)`（`ProWorkOrderServiceImpl.java:297`、`:527`），并带注释说明原因；全模块 grep `this.` 无命中，无同型残留（MBOM 的残留另见 MES-PRO-05）。
- **M1 的"步级锁"部分修复有效**：`completeDispatch` 的程序化锁覆盖了 `doCompleteDispatch` 的整个事务生命周期（`ProDispatchServiceImpl.java:235-245`），单工序内并发完工已安全；残留只在卡级判定（MES-PRO-02）。
- **报工数量守恒有强制校验（三条路径齐全）**：良品+不良+报废必须等于计划量 —— 派工 `ProDispatchServiceImpl.java:334-353`、出站 `ProFlowCardServiceImpl.java:322-343`、EXIT 规则 `ProEntryCheckServiceImpl.java:238-253`；并拒绝负数与合计为 0。
- **状态流转普遍使用 CAS 条件更新**：工单（`:325-331`、`:1157-1166`）、流转卡（`:443-446`、`:536-539`）、工序（`:234-243`、`:282-293`、`:571-579`、`:688-696`）、派工（`:186-193`、`:260-271`、`:308-314`）、SN（`:274-282`、`:290-299`、`:307-315`）、安灯（`:201-210`、`:231-240`、`:251-257`）均为 `UPDATE ... WHERE status = 期望值` + 影响行数校验，未发现读-改-写式状态变更。
- **`PARTIAL_COMPLETED` 不是死状态**：由 `ProFlowCardServiceImpl.java:511` 写入，可经 `completeWo` 的 CAS（`ProWorkOrderServiceImpl.java:1162`，含 `PARTIAL_COMPLETED`）转 COMPLETED；流转卡创建也允许该状态（`ProFlowCardServiceImpl.java:166`）。
- **设备通道报工未另起聚合路径**：`ruoyi-station/StationPassService.java:343-371` 工序收口后复用 `flowCardService.completeCard`（`:369`），自动开工复用 `startStep`（`:380`），与 MES 主路径一致。
- **完工链路（MES→ERP）成对闭合**：`completeWo` 在事务提交后经 `afterCommit` 发送（`ProWorkOrderServiceImpl.java:1198-1209`）→ `QueueConstants.ERP_WORKORDER_COMPLETED_QUEUE`（`QueueConstants.java:19`）→ 消费者存在（`ruoyi-erp/.../ErpWorkOrderCompletedConsumer.java:109`）；返工工单显式不发 ERP（`:1178-1196`，属设计）。
- **ERP→MES 下达链路成对**：`MES_WORKORDER_QUEUE`（`QueueConstants.java:17`）→ `WorkOrderReleaseConsumer.java:42` → `createFromErpMessage`；委外 SN 通道同样成对（`MES_SUBCONTRACT_SN_QUEUE` `:31` → `SubcontractSnGenerateConsumer.java:54`）。
- **工单下达/快照具备幂等保护**：工序与 MBOM 快照前先 `selectCount` 判空（`ProWorkOrderServiceImpl.java:311-321`），下达用 CAS（`:325-331`）；工单删除限定状态（`:253-261`）；MBOM 删除有"被工单快照引用"保护（`MesMbomServiceImpl.java:190-199`）。
- **超派/超建校验存在**：派工创建校验"已派合计+本次 ≤ 工序计划量"（`ProDispatchServiceImpl.java:128-136`）；流转卡创建校验"已建计划量之和 + 本次 ≤ 工单计划量"（`ProFlowCardServiceImpl.java:171-183`）。
- **SN 批量生成有并发保护**：流转卡批量 `@Lock4j(#cardId)`（`ProSnServiceImpl.java:106`）、委外批量 `@Lock4j(#subcontractNo)`（`:168`），序号取"已生成数+1"续号，委外编码为 `SN-{委外单号}-{4位序号}`（`:186`）。
- **FLASH 刷写门禁覆盖两条完工路径**：`completeStep`（`ProFlowCardServiceImpl.java:279`）与派工聚合的 `checkAndCompleteStep`（`:569`）均调用 `checkFlashStepGate`，含 SN 级 PASS 记录校验与 FORCE/WARN 模式（`:350-405`）。
- **追溯查询命中索引**：`backward` 按 `mes_wo_id`（`ry-mes.sql:2031`）、按 `parent_sn_id`（`:1893`）、按 `sn_code`（`:1888` 唯一）过滤；`forward` 按 `child_material_id`（`:2032`）过滤，均非全表扫描（性能问题只在 N+1，见 MES-PRO-17）。
- **字典齐备且与代码一致**：`prd_wo_status`（含 PARTIAL_COMPLETED `ry-vue.sql:90809`）、`mes_sn_status`（`:90681-90684`，与 `ProSn` 四个状态一致）、`mes_andon_status`（`:90696-90699`）、`mes_andon_call_type`（`:90700-90703`）、`mes_flow_card_status`（`:90727-90731`，含 CLOSED）均存在，字典类型记录齐全（`:91040`、`:91044`、`:91045`、`:91054`）。
- **权限码种子基本齐备**：`mes:pro:work:*`、`dispatch:*`、`flowcard:*`、`sn:*`、`andon:*`、`mbom:*`、`step-guide:*`、`step-check-rule:*` 在 `sys_menu` 均有 C/F 种子（`ry-vue.sql:92357-92448`、`92557`），仅 `trace:query` 缺失（MES-PRO-09）。
- **Controller 校验注解到位**：本模块 8 个 Controller 均标 `@Validated`（`MesMbomController.java:21`、`ProAndonController.java:25`、`ProDispatchController.java:26`、`ProFlowCardController.java:32`、`ProStepCheckRuleController.java:29`、`ProStepGuideController.java:29`、`ProSnController.java:28`、`ProWorkOrderController.java:28`），BO 上的 `@NotNull/@NotBlank/@DecimalMin` 生效。
- **安灯/IoT 跨模块契约正确**：`MesAndonServiceImpl.createAndonFromAlert` 用 `TenantHelper.dynamic(alert.getTenantId(), ...)` 注入租户上下文（`MesAndonServiceImpl.java:49-50`），避免了 IoT 异步线程 tenant_id 落 0 的已知坑。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `woStatus` | `ProWorkOrderBo.java:79` 可写；服务层直落库（`ProWorkOrderServiceImpl.java:225,243`） | 工单表单可提交 `woStatus` | **危险**：前端可绕过后端状态机写任意状态（MES-PRO-04），建议 BO 移除该字段 |
| `/mes/pro/work/export`、`/flow-card/export`、`/sn/export` | 端点存在（`ProWorkOrderController.java:48`、`ProFlowCardController.java:52`、`ProSnController.java:48`），菜单种子存在（`ry-vue.sql:92413`、`:92425`、`:92448`） | `plus-ui/src/api/mes/pro/{workorder,flowcardApi,sn}.ts` 无导出函数，页面无导出按钮（仅 `step-guide`/`step-check-rule` 配了 `exportUrl`） | 后端有端点、前端不可达（MES-PRO-15） |
| `mes:pro:dispatch:scan` | 无对应后端端点（`ProDispatchController.java` 仅 list/getInfo/by-step/by-operator/add/start/complete/cancel） | `views/mes/pro/dispatch/index.vue:40` 有「扫码执行」按钮并 `v-hasPermi="['mes:pro:dispatch:scan']"` | 权限码无后端落点（按钮走的是 `getFlowCardByBarcode` + 现有端点），权限配置形同虚设 |
| `initialStatus` | `ProSnController.java:91` 直接透传，服务层不校验（`ProSnServiceImpl.java:124`） | `views/mes/pro/sn/index.vue:113` 下拉 + `sn.ts:76` 传参 | 前端下拉限定值，但接口层无校验，非法值可落库（MES-PRO-14） |
| `sn_status`（返修/重工） | 代码仅 `IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED`（`ry-vue.sql:90681-90684`），无返修/重工状态 | SN 页面提供 完工/报废/发货 三个动作 | 返修/重工后的 SN 状态无处表达（返修需先报废再手工新建 SN），属功能缺失 |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：MES-PRO-01（`completeCard` 收紧前置状态 + 工序完成门禁 + 卡级一次性入账）、MES-PRO-02（卡级锁与事务同生命周期，或改条件更新式完工判定）。两者同属"卡完工聚合"链路，建议同一批改并共用并发单测。
- **批次 B（P1，事务与状态机）**：MES-PRO-05（MBOM AOP 代理调用，改动量 1 行级）、MES-PRO-04（BO 去 `woStatus`）、MES-PRO-11（补取消/关闭）、MES-PRO-03（审批通过走 `doReleaseAndSnapshot`）。
- **批次 C（P1，链路与权限/DDL）**：MES-PRO-06（正向追溯方向修正）、MES-PRO-07（跳站完工数）、MES-PRO-09（补 `trace:query` 菜单种子，纯 SQL）、MES-PRO-10 + MES-PRO-19（唯一索引补 `tenant_id`，需 DDL 与存量核对）、MES-PRO-08（安灯离线通知）。
- **批次 D（P2）**：MES-PRO-12~18（编号生成、索引、校验、导出入口、SN 解绑、追溯分页、报工口径统一）。

## 8. 未覆盖范围（本轮预算内未读）

- `ProWorkOrderServiceImpl` 中 `generateStepsFromRouting`/`snapshotBomLines`/`applyBomItems`/`matchBomLine`（`:597-960`）、`buildReleaseConfigSnapshot`/`resolveStepType`（`:786-827`）的内部逻辑未逐行审读（仅按调用点与行号核对），发布包 BOM 覆写（REPLACE/ADD/REMOVE）与固件映射匹配的边界条件未验证。
- `ProStepGuideServiceImpl`、`ProStepCheckRuleServiceImpl`、`ProWorkOrderBomLineServiceImpl`、`MesMbomLineServiceImpl` 的 CRUD 未逐行审读（配置类，风险低）。
- 前端页面布局/列宽/表单分组（规范 §2.3 的"布局"类）本轮只核对了可达性与字段契约，未做逐页 UI 评审。
- `MesMbomServiceImpl.verify`（`:227-305`）的校验规则细节与 `enrich*` 系列未审读。
- 安灯看板前端（`plus-ui/src/views/mes/pro/andon`）与 `ruoyi-mes/pro` 之外的 `flash`/`sop`/`lt`/`ncr` 联动细节（仅在追溯、门禁、NCR 回写处做了契约级核对）。
- 一处**待实测确认**（未计入发现）：`ProAndonServiceImpl.dashboardStats` 的活跃呼叫统计用 `.eq(status, CALLED).or().eq(status, RESPONDED)`（`:88-91`），若当前 MyBatis-Plus 版本的租户拦截器未对 `OrExpression` 加括号，则 SQL 为 `status='CALLED' OR status='RESPONDED' AND tenant_id=?`，会跨租户统计。本轮未能确认所用 MP 版本的行为（`.m2` 中仅见 3.3.1，与 pom 声明版本不一致），故不作为结论，建议修复批次 D 顺带改为 `.in(status, CALLED, RESPONDED)` 消除歧义。
