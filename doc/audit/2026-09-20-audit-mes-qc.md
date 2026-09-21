# MES 质量（qc / ncr）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc`、`.../mes/ncr`（40+36 文件）及其 ERP/MES 联动链路与前端可达性 | 覆盖文件数: 26（qc 包 13 + 联动 13；NCR 36 由子代理覆盖并二次复核 7 处） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-QC-01 | P0 | 跨模块链路/幂等 | 检验结果回写 ERP 是 `@Async` fire-and-forget + afterCommit，失败只打日志无重试/发件箱；计划明确"OQC 门禁仅阻断 UNQUALIFIED、PENDING 放行"，于是回写丢失与"未检验"一样被放行出库 | `MesMqSendService.java:88`、`SoSalesOrderHeaderServiceImpl.java:521`、`2026-07-23-mes-erp-qc-linkage-completion-plan.md:315` |
| MES-QC-02 | P0 | 功能缺失 | 检验标准模板/AQL 只对 IQC 生效：IPQC、OQC 从不生成检验明细、从不匹配模板，只能人工选合格/不合格 | `QcIqcServiceImpl.java:116` vs `QcIpqcServiceImpl.java:111`、`QcOqcServiceImpl.java:98` |
| MES-QC-03 | P1 | 功能缺失/契约 | 代码注释声称关键工序 IPQC 由"completeWo 门禁兜底"，但 `selectByMesWoId` 全仓无调用者、门禁不存在（计划把质量门禁列为"本次不做"）→ 关键工序可以没有 IPQC 就完工，且建单异常被 try/catch 吞掉 | `QcIpqcServiceImpl.java:128`、`ProFlowCardServiceImpl.java:748`、`2026-07-23-mes-erp-qc-linkage-completion-plan.md:5` |
| MES-QC-04 | P1 | 幂等 | IQC/IPQC/OQC 自动建单的幂等全是"先查后写"（TOCTOU），无唯一约束兜底 → MQ 重投/并发重复建单、重复检验、重复回写 | `IqcAutoCreateConsumer.java:54`、`QcIpqcServiceImpl.java:144` |
| MES-QC-05 | P1 | 校验/判定 | 明细判定允许部分提交：未录入实测值的项目保持 PENDING 被 `judgeOverall` 跳过，只提交 1 项即可整单判 QUALIFIED | `QcIqcServiceImpl.java:233`、`QcJudgeEngine.java:117` |
| MES-QC-06 | P1 | 判定 | NOMINAL 判定 `nominal-tolLower`/`nominal+tolUpper` 不校验公差符号（负偏差录入即公差带整体上移），且三个值任一为 null 抛 NPE（LSL_USL 分支反而 null 安全） | `QcJudgeEngine.java:91`、`QcTemplateServiceImpl.java:218` |
| MES-QC-07 | P1 | 功能缺失/DDL | 项目级 AQL/抽样方案（`aql_level`/`sample_plan_code`）是死字段：建议抽样量硬编码检验水平 II 且不传 AQL，Ac/Re 从不参与判定；AQL 表只服务 PDA 试算 | `QcInspectItem.java:41`、`QcInspectDetailServiceImpl.java:131` |
| MES-QC-08 | P1 | 幂等 | 检验单号按"当日 count+1"生成：单号不连续（有删除/跨天残留）即复用已存在单号撞唯一索引，并发同号无重试 | `QcIqcServiceImpl.java:271`、`QcIpqcServiceImpl.java:252`、`QcOqcServiceImpl.java:187` |
| MES-QC-09 | P1 | 状态机 | 模板 `status` 可由客户端在 add/edit 直接置 ACTIVE，绕过 `changeStatus` 的"必须有启用项目"校验 → 产生 ACTIVE 但无启用项目的模板，被 `matchTemplate` 选中后检验单无明细 | `QcTemplateServiceImpl.java:71`、`QcInspectTemplateBo.java:43` |
| MES-QC-10 | P1 | 并发/数据错 | `applyIqcResult` 用 `selectOne(iqc_id)` 定位批次，多批次关联同一 IQC 时抛 TooManyResultsException，被 `catch(Exception)` 吞掉 → 批次不置 FAIL、不隔离（IQC 已是终态） | `LtLotServiceImpl.java:380`、`QcIqcServiceImpl.java:196` |
| MES-QC-11 | P1 | 校验 | IQC/IPQC/OQC 的 `add` 端点 `@RequestBody` 漏 `@Validated`（同包模板 Controller 有）→ BO 上 `@NotNull materialId`/`@NotNull mesWoId` 不生效，可落库无物料/无工单的检验单 | `QcIqcController.java:71`、`QcTemplateController.java:56` |
| MES-NCR-01 | P0 | 状态机/功能缺失 | 四个 `autoCreateFrom*` 全部把 `lotId` 传 null，而 `handleScrapDisposition`/`handleAcceptDisposition` 都以 `lotId != null` 为前置 → 计划标记 ✅ 的"批次隔离/解隔离/报废"对系统产生的全部 NCR 都不执行，`QUARANTINED` 从未置位 | `NcrOrderServiceImpl.java:203`、`:600`、`:624`、`2026-07-28-mes-p0-4-ncr-rework-implementation-plan.md:130` |
| MES-NCR-02 | P0 | 事务/数据错 | 报废/让步的批次动作失败被 `catch{log.warn}` 吞掉后仍把 NCR CAS 到 `CLOSED`（返工分支反而 rethrow）→ 单据"已报废关闭"而实物未报废 | `NcrOrderServiceImpl.java:601-614`、`:622-640`、对比 `:588-591` |
| MES-X-01 | P0 | 跨模块链路/功能缺失 | ERP 侧库存冻结是**单向**的：全 `ruoyi-erp` 模块 `set(WmMaterialStock::getFrozenFlag` 仅 1 处且写 `1L`，无任何写 0 的 UPDATE、无 unfreeze 入口，而 NCR 让步接收只解 MES 侧 `lt_lot`（且 lotId 恒 null）→ 被 IQC 冻结的库存永久无法发料/出库；另有"无到货通知+收货行无批次号"的 IQC 不合格旁路 | `WmMaterialStockServiceImpl.java:219`、`NcrOrderServiceImpl.java:626`、`2026-07-28-...-plan.md:652` |
| MES-NCR-03 | P1 | 并发/状态机 | `updateByBo` 先 `selectById` 再 `updateById(整实体)`：并发处置已把状态推进后，陈旧实体的 `status`/`disposition`/`reviewBy` 被整行写回，可把 `DISPOSITION/CLOSED` 回滚成 `REVIEW` | `NcrOrderServiceImpl.java:139-146`、`:161` |
| MES-NCR-04 | P1 | 权限 | `mes:ncr:8d:query`、`mes:ncr:defect:list` 在 `sys_menu` 中不存在（只有 `8d:list/edit/complete`、`defect:add`）→ 除超管外 403，8D 面板与缺陷明细不可达 | `Ncr8dReportController.java:35`、`NcrDefectController.java:37`、`ry-vue.sql:92569`、`:92577` |
| MES-NCR-05 | P1 | 幂等 | `autoCreateFromExit` 把 `sourceId` 传 null（PROCESS 来源），`uk_source(source_type, source_id)` 对含 NULL 的键不去重，且查重逻辑以 `sourceId != null` 为前提 → 反复出站重复建 NCR、重复安灯 | `NcrOrderServiceImpl.java:235`、`ry-mes.sql:1653` |
| MES-NCR-06 | P1 | 权限/审计 | 无签名的 `PUT /dispose/{ncrId}` 与签署接口共用 `mes:ncr:order:dispose`，注释自认"签署尚未强制" → 报废/退货/让步可完全绕过二级认证与电子签名 | `NcrOrderController.java:117-124`、`:139-146`、`:131` |
| MES-X-02 | P1 | 功能缺失 | `ReworkStockConsumer.doProcess` 只 `log.info("状态更新")`，读出的返工单从未被更新 → 返工出/入库消息消费后 `PrdReworkOrder` 状态永不变，返工状态机不闭环 | `ReworkStockConsumer.java:45-57` |
| MES-QC-12 | P2 | 判定 | 定性判定只认硬编码白名单（含 `是`/`正常`/`NG-NO`），模板层没有枚举型标准；`是` 语义歧义可被当成合格 | `QcJudgeEngine.java:35` |
| MES-QC-13 | P2 | 校验 | 不良率 `defectQty/sampleQty*100` 无 `defectQty<=sampleQty`、非负校验 → 可 >100% 或为负并回传 ERP | `QcIpqcServiceImpl.java:240` |
| MES-QC-14 | P2 | 并发 | 模板启用/停用是读-改-写（无 CAS）；`generateDetails` 幂等同样是先查后写 | `QcTemplateServiceImpl.java:119`、`QcInspectDetailServiceImpl.java:45` |
| MES-QC-15 | P2 | 死枚举 | `QcJudgeEngine.RESULT_PARTIAL` 永不落库（`inspect` 只接受 QUALIFIED/UNQUALIFIED，PARTIAL 被折叠为 UNQUALIFIED），"部分不合格=让步/挑选"语义在数据上不可表达 | `QcJudgeEngine.java:32`、`QcIqcServiceImpl.java:253` |
| MES-QC-16 | P2 | 幂等 | IPQC 幂等键在 `processId` 为空时失效（`.eq(processId, null)` 生成 `process_id = NULL` 恒不匹配）→ 每次报工新建一张 IPQC | `QcIpqcServiceImpl.java:120`、`:144` |
| MES-QC-17 | P2 | 口径 | 同一检验单所有项目共用一个建议抽样量，提交时前端又可覆盖 `sample_qty` → 不良率分母不可信 | `QcInspectDetailServiceImpl.java:58`、`:112` |
| MES-QC-18 | P2 | 校验 | 实测值个数与抽样量不校验 → 抽 125 件只录 1 个实测值也判 PASS | `QcJudgeEngine.java:71`、`QcInspectDetailServiceImpl.java:111` |
| MES-NCR-07 | P2 | 审计追踪 | 签名走 `REQUIRES_NEW` 先落库再绑定，处置 CAS 失败时签名不随事务回滚（有签名无处置），且可反复重绑 | `NcrOrderServiceImpl.java:334`、`:419` |
| MES-NCR-08 | P2 | 幂等 | 8D 懒创建先查后插且 `ncr_8d_report` 无唯一约束 → 并发/重复点击生成两份 8D | `Ncr8dReportServiceImpl.java:44`、`ry-mes.sql:1494` |
| MES-NCR-09 | P2 | 校验 | 8D 不校验 NCR 存在/是否关闭即可创建与完成；`complete` 不校验 D1~D8；COMPLETED 后仍可编辑 | `Ncr8dReportServiceImpl.java:44`、`:99` |
| MES-NCR-10 | P2 | 校验 | 缺陷明细补录不校验 NCR 状态，已 CLOSED 的 NCR 仍可追加；`ncr_defect` 无唯一约束 | `NcrDefectServiceImpl.java:54`、`ry-mes.sql:1518` |
| MES-NCR-11 | P2 | 校验 | 不良代码三处缺陷：`enabled` 默认 `"Y"` 与 DDL `'1'/'0'` 不符；组树丢弃子级搜索结果；`level` 与父级不校验 | `NcrDefectCodeServiceImpl.java:100`、`:49`、`:87` |
| MES-NCR-12 | P2 | 死枚举/幂等 | `ncr_rework_order` 无 `ncr_id` 唯一约束、无查重；`NcrReworkOrder.status` 的 `IN_PROGRESS` 无写入点 | `NcrReworkOrderServiceImpl.java:106`、`ry-mes.sql:1682` |
| MES-X-03 | P2 | 租户/DDL | 3 个唯一键不含 `tenant_id`（模板编码/不良代码编码/NCR 来源）；MQ `tenantId` 用 `Long.parseLong` 失败只 warn → 回写落 `tenant_id=0` 查询不到 | `ry-mes.sql:2401`、`MesMqSendService.java:102` |

## 1. P0 详述

### MES-QC-01 检验结果回写 ERP 无重试，OQC 不合格可被静默放行出库

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mq/MesMqSendService.java:88`、`:128`、`:173`；`.../mes/qc/service/impl/QcOqcServiceImpl.java:167-179`；`.../ruoyi-erp/src/main/java/com/nexus/erp/so/service/impl/SoSalesOrderHeaderServiceImpl.java:518-529`；`.../ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ErpOqcCompletedConsumer.java:80-85`；`.../ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveCrudServiceImpl.java:706-719`
- **现状**: 三个检验单的 `inspect()` 都只在 `TransactionSynchronization.afterCommit()` 里调用 `mesMqSendService.sendXxxCompleted(...)`，而这三个发送方法带 `@Async`（`MesMqSendService.java:88`、`:128`、`:173`）：

  ```java
  @Async
  public void sendOqcCompleted(Long oqcId, ..., String tenantId) { ... messageProducer.send(...); }
  ```

  即：MQ 投递发生在事务提交之后的**另一个线程**，调用方拿不到结果；发送抛异常只会落到异步线程的默认异常处理（日志），检验单已经是 `COMPLETED` 终态，没有任何重试、发件箱或补偿任务。ERP 侧同样：`WmMaterialReceiveCrudServiceImpl.java:710-719` 在 afterCommit 里逐条 send，失败仅 `log.error`（catch 后不抛出、不重试）。ERP 消费端只有正向回写，没有反向对账扫描"已 COMPLETED 但 ERP 未回写"的检验单。
- **触发路径**: ①OQC 判定 `UNQUALIFIED` → `inspect()` 提交 → `afterCommit` 异步线程投递 MQ 时 RabbitMQ 不可达/网络抖动/序列化失败；②ERP 侧 `ErpOqcCompletedConsumer` 消费失败且重试耗尽。此时 `so_sales_order_line.oqc_status` 仍为 `NULL`。
- **后果**: `SoSalesOrderHeaderServiceImpl.java:518-529` 的"OQC 硬门禁（force 不可绕过）"判定条件是 `"UNQUALIFIED".equals(line.getOqcStatus())`，**`NULL` 不等于 UNQUALIFIED 即被放行**。需要说明的是，`doc/erp/plans/archive/2026-07-23-mes-erp-qc-linkage-completion-plan.md:315` 把这一点写成了设计选择："OQC 硬门禁误阻断正常发货 | 仅阻断 `UNQUALIFIED`，`PENDING` 放行"。该设计的前提是"OQC 单一定会在发货前建好并回写"——而本条的机制缺陷正好破坏了这个前提：**一旦回写丢失，`oqcStatus` 为 NULL 与"尚未检验"不可区分，二者都放行**。因此后果是：已判定不合格的销售订单行可以正常履行、出库、发货，且账面看不出异常（状态是"从未检验"而不是"不合格"）。IQC 侧同类失败会让到货通知行 `iqc_status` 永远 PENDING，`WmMaterialReceiveCrudServiceImpl.java:360-375` 的货架门禁永久卡住（无人工兜底入口）；IPQC 侧工单聚合质检状态永不更新。
- **修复建议**: ①把三个 `sendXxxCompleted` 的 `@Async` 去掉，改为在 afterCommit 内同步投递（RabbitMQ 客户端本身有 confirm），投递失败时写一张 `qc_writeback_outbox`（或复用现成 MQ 重试表）由 SnailJob 定时重投；②消费端保持幂等（按 `oqcId` 唯一）；③**门禁口径改为"白名单放行"**：`SoSalesOrderHeaderServiceImpl.java:521` 改为 `!"QUALIFIED".equals(line.getOqcStatus())`（`NULL`/`UNQUALIFIED` 都不放行），并给前端明确提示"该行尚未 OQC 合格"；若业务上确实要允许未检验发货，则必须在发货单上落一个"跳过质量门禁"的显式确认与操作人。第③条是唯一能立即止血的改动，且它把"设计上允许 PENDING 放行"变成了一个必须有人负责的动作，而不是静默通过。
- **影响面**: `MesMqSendService`、三个 `Qc*ServiceImpl.inspect()`、`ErpOqcCompletedConsumer`/`ErpIqcCompletedConsumer`/`ErpIpqcCompletedConsumer`、`SoSalesOrderHeaderServiceImpl.fulfill()`、`WmMaterialReceiveCrudServiceImpl.confirmShelf()`。第③条需要 DDL：无；需要补测试：是（`oqcStatus=NULL` 时必须被拦截）。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/qc/service/impl/QcOqcServiceImplTest.java:227` 只断言"无 soId 时不发送"，没有任何"发送失败/门禁 NULL"用例；`SoSalesOrderHeaderServiceImplTest.java:180-189` 的 `line(...)` 辅助方法显式传 `oqcStatus`，未覆盖 NULL。

### MES-QC-02 检验标准模板与 AQL 只对 IQC 生效，IPQC/OQC 无检验明细

- **证据**: `.../mes/qc/service/impl/QcIqcServiceImpl.java:116`（唯一调用点）、`.../mes/qc/service/impl/QcIpqcServiceImpl.java:111-113`、`.../mes/qc/service/impl/QcOqcServiceImpl.java:98-100`、`.../mes/qc/service/impl/QcInspectDetailServiceImpl.java:137-155`、`.../mes/qc/service/impl/QcTemplateServiceImpl.java:70-82`
- **现状**: `grep generateDetails` 全仓只有 `QcIqcServiceImpl.java:116` 一处业务调用（其余是接口/重载自身）。IPQC 的 `insertByBo`（`QcIpqcServiceImpl.java:93-114`）与 OQC 的 `insertByBo`（`QcOqcServiceImpl.java:86-101`）只写头表字段，既不调用 `generateDetails`，也没有任何明细入口；`matchTemplate(checkType, materialId)`（`QcInspectDetailServiceImpl.java:137`）虽然按 `templateType` 匹配，但 `checkType` 只被传过 `"IQC"`。同时 `QcInspectTemplateBo.templateType` 无枚举校验、无字典约束（`QcTemplateServiceImpl.java:70-82` 只校验 items），建 `IPQC`/`OQC` 类型的模板不会报错，只是永远不会被匹配到。
- **触发路径**: 建一条 `template_type=IPQC` 的模板 → 报工触发 `autoCreateForKeyProcess` → IPQC 单无明细 → `inspect` 只能由人点"合格/不合格"。
- **后果**: 过程检验与出货检验**完全没有检验项、标准上下限、AQL 抽样**，只剩一个人工二选一按钮；`QcJudgeEngine` 与 `AqlSampler` 对 IPQC/OQC 不可达。审计重点要求的"物料/工序/客户维度优先级"实际只实现了"物料专属 > 通用"两档（`QcInspectDetailServiceImpl.java:137-155`），工序/客户维度在 `QcInspectTemplate` 上根本没有字段。
- **修复建议**: ①`QcIpqcServiceImpl.insertByBo`/`QcOqcServiceImpl.insertByBo` 末尾补 `inspectDetailService.generateDetails("IPQC"|"OQC", id, materialId, qty)`（IPQC 需扩展 `matchTemplate` 支持 `processId` 维度）；②给 IPQC/OQC 补 `inspectWithDetails` 同构方法（当前只有 IQC 有，`QcIqcController.java:114`）；③`templateType` 加 `sys_dict` + 服务端白名单校验。需要 DDL：IPQC/OQC 若要存"检验批量"需确认 `qc_ipqc`/`qc_oqc` 是否有 `quantity` 列（IPQC 头表当前无 quantity 字段，见 `QcIpqcServiceImpl.java:94-110`）。需要补测试：是。
- **测试现状**: `QcIpqcServiceImplTest`/`QcOqcServiceImplTest` 均无明细生成用例。

### MES-QC-03（P1，降级说明见 §2 开头）关键工序 IPQC 的"完工门禁"不存在，建单失败被静默吞掉

- **证据**: `.../mes/qc/service/impl/QcIpqcServiceImpl.java:127-134`（`selectByMesWoId`）、`.../mes/pro/service/impl/ProFlowCardServiceImpl.java:752-778`、`.../mes/qc/service/impl/QcIpqcServiceImpl.java:141-150`
- **现状**: `QcIpqcServiceImpl.selectByMesWoId` 的注释写明"供 completeWo 门禁按关键工序校验"，但全仓 `grep selectByMesWoId` 只有接口声明与实现本身，**没有任何调用者**——工单完工没有按关键工序校验 IPQC。而建单入口 `ProFlowCardServiceImpl.triggerIpqcIfKeyProcess` 整体包在 `try { ... } catch (Exception e) { log.error("...由 completeWo 门禁兜底"); }` 里（`:775-777`），注释所依赖的兜底并不存在。
- **触发路径**: 关键工序报工 → `autoCreateForKeyProcess` 抛异常（如 `qc_ipqc` 唯一索引冲突、数据源切换失败、`QcIpqcBo.mesWoId` 为空触发校验） → 异常被吞 → 继续报工 → 工单直接完工，全程无 IPQC 记录，且日志之外无任何痕迹。
- **后果**: 关键工序的过程检验可以 100% 缺失而工单照样完工入库。需要区分两件事：**"不做门禁"是计划内的范围裁剪**（`doc/erp/plans/archive/2026-07-23-mes-erp-qc-linkage-completion-plan.md:5` 写明"质量门禁强制执行本次不做"，`:372` 再次列为不做项），**但代码注释把它描述成"已有兜底"**（`ProFlowCardServiceImpl.java:748`、`:776`、`QcIpqcServiceImpl.java:128`、`:139`），维护者会据此认为"即使建单失败也有门禁拦住"，从而不会去补门禁也不会去补建单失败的告警。这是注释/契约与实现不一致导致的**静默质量逃逸**，而非单纯的缺功能。
- **修复建议**: ①在 `ProWorkOrderServiceImpl`/流转卡完工入口调用 `iqcIpqcService.selectByMesWoId(mesWoId)`，对 `isKeyProcess=1` 的工序逐一校验"存在且 `inspect_result=QUALIFIED` 的 IPQC"，否则抛 `ServiceException`（需与现有 `completeWo` 的其它门禁同一事务点；若本轮仍不做门禁，则必须走第②条）；②无论是否做门禁，都要把 `triggerIpqcIfKeyProcess` 的 catch 从"只 log"改为写一条待办/告警（复用 Andon 或 `sys_user_notification`），并删除/更正三处"由 completeWo 门禁兜底"的注释，避免继续误导。需要 DDL：无。需要补测试：是。
- **测试现状**: `QcIpqcServiceImplTest.java:89-116` 覆盖了"已存在则跳过/不存在则创建"，但没有任何门禁测试（因为门禁代码不存在）。

## 2. P1 详述

> 降级说明：`MES-QC-03` 初判 P0，复核 `doc/erp/plans/archive/2026-07-23-mes-erp-qc-linkage-completion-plan.md:5`、`:372`（"质量门禁强制执行本次不做"）后降为 P1——缺门禁是计划内的范围裁剪，真正的问题是代码注释谎称有兜底。其详述保留在 §1 以便与 MES-QC-01/02 的门禁主题连读。

### MES-QC-04 自动建单幂等是"先查后写"，无唯一约束兜底

- **证据**: `.../mes/common/mq/consumer/IqcAutoCreateConsumer.java:53-70`、`.../mes/qc/service/impl/QcIqcServiceImpl.java:130-135`、`.../mes/qc/service/impl/QcIpqcServiceImpl.java:142-150`、`.../mes/common/mq/consumer/OqcAutoCreateConsumer.java:57`、`.../mes/qc/service/impl/QcOqcServiceImpl.java:107-112`
- **现状**: 三处都是 `if (service.existsByXxxAndYyy(...)) return; ... insertByBo(...)`，`existsBy*` 是 `selectCount`（`QcIqcServiceImpl.java:131-134`）。两个并发/重投消息可以同时读到 0 然后各自插入。`insertByBo` 内部不做任何查重。
- **触发路径**: RabbitMQ 未 ack 时重投 + 两条消息被两个并发消费者线程处理；或 ERP 审批接口重复触发 afterCommit（`WmMaterialReceiveCrudServiceImpl.java:706`）。
- **后果**: 同一收货单+物料出现 2 张 IQC 单，两张都能被检验并各自回写 ERP/批次/触发 NCR；库存冻结与放行出现互相覆盖，检验工作量翻倍，且审计上无法判断哪张是有效的。
- **修复建议**: ①`qc_iqc` 加唯一索引 `uk_iqc_receive_material(tenant_id, receive_id, material_id)`，`qc_oqc` 加 `uk_oqc_so_material(tenant_id, so_id, material_id)`，`qc_ipqc` 加 `uk_ipqc_wo_process(tenant_id, mes_wo_id, process_id)`（**需 DDL + 存量数据去重**）；②消费端捕获 `DuplicateKeyException` 视为幂等成功；③`insertByBo` 内部同样做一次捕获。注意 `qc_ipqc.process_id` 可为空（见 MES-QC-16）。
- **影响面**: `script/sql/ry-mes.sql`、三个 Consumer、`Qc*ServiceImpl.insertByBo`。需要 DDL：是。需要补测试：是。
- **测试现状**: 无并发/重投用例（`QcIqcServiceImplTest.java:129-142` 只测 `existsBy*` 的布尔分支）。

### MES-QC-05 明细判定允许部分提交，未录入项目被静默跳过

- **证据**: `.../mes/qc/service/impl/QcIqcServiceImpl.java:227-254`、`.../mes/qc/service/QcJudgeEngine.java:109-135`
- **现状**: `inspectWithDetails` 遍历**请求里的 entries**去匹配明细（`:233-245`），没有校验"entries 是否覆盖该检验单全部明细"。`judgeOverall` 显式跳过非 PASS/FAIL 的明细（`QcJudgeEngine.java:117-119`），只在**一条都没判定**时返回 null（`:128-130`）。
- **触发路径**: 检验单按模板生成 5 个明细 → 前端只提交 1 个明细（或旧版前端、直接调接口 `PUT /mes/qc/iqc/inspect-details/{id}` 只传一项）→ 该明细 PASS → `judgeOverall` 返回 QUALIFIED → 整单 COMPLETED 合格，其余 4 项永远 PENDING。
- **后果**: 漏检即可放行。来料检验最常见的"关键尺寸未测"场景被判合格，NCR 不触发、库存不冻结，事后只能靠人工翻明细才发现（而明细页显示 PENDING，界面不会告警）。
- **修复建议**: 在 `inspectWithDetails` 判定前加：`List<QcInspectDetail> pending = details.stream().filter(d -> !"PASS".equals(d.getJudgeResult()) && !"FAIL".equals(d.getJudgeResult())).filter(d -> entries 未覆盖).toList(); if (!pending.isEmpty()) throw new ServiceException("仍有 N 项未录入实测值：" + 项目名);`（按 `detailId` 差集判断）。同时前端提交按钮在存在未录入项时置灰。需要 DDL：无。需要补测试：是（"缺项提交必须拒绝"）。
- **测试现状**: `QcIqcServiceImplTest` 无部分提交用例。

### MES-QC-06 NOMINAL 判定：公差符号未校验 + 空值 NPE

- **证据**: `.../mes/qc/service/QcJudgeEngine.java:80-101`、`:145-153`、`.../mes/qc/service/impl/QcTemplateServiceImpl.java:206-225`、`.../mes/qc/service/impl/QcInspectDetailServiceImpl.java:173-176`
- **现状**: 公式与实现对比：

  | 语义 | 期望 | 代码实现 | 结论 |
  |---|---|---|---|
  | NOMINAL 下限 | `nominal − tolLower` | `nominal.subtract(detail.getTolLower())`（`:93`） | 仅当 `tolLower` 为**正的偏差量**时正确 |
  | NOMINAL 上限 | `nominal + tolUpper` | `nominal.add(detail.getTolUpper())`（`:94`） | 仅当 `tolUpper` 为正时正确 |
  | LSL_USL | `lsl ≤ v ≤ usl` | `ge(v,lsl) && le(v,usl)`（`:83`） | 正确（null 安全） |
  | LSL_ONLY / USL_ONLY | 单边 | `ge`/`le`（`:86`、`:89`） | 正确（null 安全） |

  模板校验只要求 `nominalValue`/`tolUpper`/`tolLower` **非空**（`QcTemplateServiceImpl.java:218-222`），不校验正负；`QcInspectItem` 的注释把 tolLower 定义为"下公差(名义-tolLower)"（`QcInspectItem.java:38`），但没有任何地方阻止用户按工程习惯录入 `-0.05`。
- **触发路径**: ①模板里 NOMINAL 项录入 `名义=10、上公差=0.05、下公差=-0.05`（工程图纸的常规写法）→ 判定区间变成 `[10.05, 10.05]`，实测 10.00 被判 FAIL（误判不合格，触发 NCR 与批次冻结）；实测 10.05 反而 PASS。②若明细快照的三个值任一为 null（历史数据、直接改库、模板项被"先删后插"后旧明细仍引用）→ `BigDecimal.subtract(null)` 抛 NPE，检验提交 500。
- **后果**: ①系统性误判：要么把合格品判不合格（停线、误开 NCR、误冻库存），要么把超差品判合格；②NPE 让检验单无法提交（业务中断），且异常信息对用户不可读。
- **修复建议**: ①`validateItems` 的 NOMINAL 分支补 `tolUpper.signum() >= 0 && tolLower.signum() >= 0`，否则报"上/下公差请填正值（下公差按名义值减）"；②前端 NOMINAL 行给 `tolLower` 加 `min=0` 与提示文案；③`inStandard` 的 NOMINAL 分支改为 null 安全（任一为 null 直接 `return false`，与 LSL_USL 一致，走 FAIL 而不是抛异常）。需要 DDL：无（存量负值数据需一次性 `abs()` 修正）。需要补测试：是。
- **测试现状**: `src/test` 下未找到 `QcJudgeEngine` 的单测（`AqlSamplerTest` 存在，判定引擎无对应用例）。

### MES-QC-07 项目级 AQL/抽样方案是死字段，Ac/Re 不参与判定

- **证据**: `.../mes/qc/domain/QcInspectItem.java:41-43`、`.../mes/qc/service/impl/QcTemplateServiceImpl.java:177-178`、`.../mes/qc/service/impl/QcInspectDetailServiceImpl.java:57-58`、`:123-132`、`.../mes/qc/service/impl/QcIqcServiceImpl.java:246-254`
- **现状**: `QcInspectItem` 有 `aqlLevel`、`samplePlanCode` 两个字段，模板版本复制时被原样拷贝（`QcTemplateServiceImpl.java:177-178`），但**全仓没有任何读取点**（`grep aqlLevel|samplePlanCode` 只命中实体/BO/VO/复制）。生成明细时的建议抽样量是硬编码：`suggestSampleQty` 直接调 `AqlSampler.letterSampleSize(lot, "II")`（`QcInspectDetailServiceImpl.java:131`），既不传 AQL 也不做箭头修正；最终判定只由 `QcJudgeEngine` 的逐项 PASS/FAIL 汇总（`QcIqcServiceImpl.java:243-254`），**Ac/Re 从未参与**。
- **公式与实现对比（AQL 数学本身）**: 逐张表核对结论是**代码正确、但没被使用**。
  - 表 1（样本量字码）：`LETTERS` 跳过 I/O（`AqlSampler.java:33`）、`SAMPLE_SIZES={2,3,5,8,13,20,32,50,80,125,200,315,500,800,1250,2000}`（`:36`）、批量区间下界 `{2,9,16,26,51,91,151,281,501,1201,3201,10001,35001,150001,500001}`（`:42`）与 GB/T 2828.1 表 1 一致；`LEVEL_ROWS`（`:45-61`）水平 II = A..Q 连续、水平 I 左移一档、水平 III 右移一档，抽查 2-8/II→A、9-15/II→B、51-90/II→E、1201-3200/II→K、500001+/II→Q 全部相符。
  - 表 2-A（正常检验一次抽样）：Ac/Re 九档 `{0/1,1/2,2/3,3/4,5/6,7/8,10/11,14/15,21/22}`（`:39`）与标准一致；0/1 起始字码下标 `AQL_START_IDX`（`:67`）与 `SUPPORTED_AQLS`（`:64`）配对后，用公开锚点验证：`K(125)@2.5 → SEQ[5]=7/8`、`M(315)@2.5 → SEQ[7]=14/15`、`K@4.0 → SEQ[6]=10/11`、`M@4.0 → SEQ[8]=21/22`、`H(50)@1.5 → 2/3`、`J(80)@1.0 → 2/3`、`N(500)@2.5 → 21/22`，全部相符。
  - 箭头解析：`letterIdx < startIdx` 视为向下箭头取 `SEQ[0]`（`:118-122`）；`letterIdx-startIdx ≥ 9` 视为向上箭头取 `SEQ[8]` 且样本量取 `SAMPLE_SIZES[startIdx+8]`（`:127-132`）——与"沿箭头取该列首个/最后一个显式方案"一致。
  - 整批检查：`if (n >= lotSize) n = lotSize`（`:133-135`），Ac/Re 不变，与标准一致；`judge()` 用 `defectQty <= Ac`（`:147`），因该表恒有 `Re = Ac+1`，等价于 `≥Re 拒收`。
  - 唯一限制：AQL 仅支持 14 档（`:64`），越界抛 `IllegalArgumentException`（`:193`），检验水平仅 I/II/III（`:174-185`）。
- **后果**: 用户在模板上配置的 AQL（例如 AQL 0.65）与抽样方案**完全不生效**，实际抽样量永远是"水平 II 字码样本量"，判定永远是"逐项全过才合格"，与 AQL 允收逻辑（例如 n=125、Ac=7 时允许 7 个不良）矛盾——批量来料按 AQL 本应接收却被判不合格，或反之按项目判定合格但抽样量不足。用户无法从界面看出配置被忽略。
- **修复建议**: ①`generateDetails` 读取项目级 `aqlLevel`（解析为 AQL 数值）+ `samplePlanCode`（检验水平），调用 `AqlSampler.plan(lotQty, level, aql)` 落 `sample_qty` 与 `accept_qty/reject_qty` 快照（需给 `qc_inspect_detail` 加 `accept_qty`/`reject_qty` 列，**需 DDL**）；②`QcJudgeEngine` 增加"按抽样方案判定"入口：定性/计数型项目用 `defectQty <= Ac` 判单，而不是逐项全过；③若短期内不实现，则把这两个字段从模板表单与实体移除，避免误导。需要补测试：是（用 `AqlSamplerTest` 的锚点扩到"明细判定"层）。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/qc/service/AqlSamplerTest.java` 覆盖了字码、样本量、K/M@2.5/4.0 锚点与整批截断，**AQL 表本身有测试**；但没有任何"检验单按 AQL 判定"的测试（因为该链路不存在）。

### MES-QC-08 检验单号"当日 count+1"生成，撞唯一索引且无重试

- **证据**: `.../mes/qc/service/impl/QcIqcServiceImpl.java:267-277`、`.../mes/qc/service/impl/QcIpqcServiceImpl.java:249-258`、`.../mes/qc/service/impl/QcOqcServiceImpl.java:184-193`
- **现状**: 三处同构：

  ```java
  Long count = baseMapper.selectCount(Wrappers.<QcIqc>lambdaQuery()
      .likeRight(QcIqc::getIqcNo, prefix + "-" + today));
  int seq = (count == null ? 0 : count.intValue()) + 1;
  return prefix + "-" + today + "-" + String.format("%03d", seq);
  ```

  注释自称"并发冲突由 uk_xxx_no 唯一索引兜底"，但没有任何捕获 `DuplicateKeyException` 后重试的逻辑。
- **触发路径**: ①当日已有 `IQC-20260920-001..003`，其中 `002` 被物理删除（或历史库按 `del_flag` 逻辑删除而查询过滤掉）→ 下一次 count=2 → 生成 `003`，与已存在单号冲突；②两个并发请求同时 count=0 → 都生成 `001`。
- **后果**: 建单直接抛 SQL 异常（500），用户看到不可读的数据库错误；MES 收货审批触发的自动建单失败会走 `IqcAutoCreateConsumer.java:73-77` 的重抛 → 消息反复重投又反复失败（毒消息）。若唯一索引实际不存在（需 DDL 复核），则静默产生重复单号，后续按单号检索/打印会命中两条记录。
- **修复建议**: ①改用序列/Redis 自增或"日期+雪花后缀"（项目已有 `IdUtil` 雪花），彻底避免 count；②若坚持可读序号，则捕获 `DuplicateKeyException` 重试 3 次；③确认 `script/sql/ry-mes.sql` 中 `qc_iqc/qc_ipqc/qc_oqc` 的 `*_no` 唯一索引确实存在（见 §6 交叉核验）。
- **影响面**: 三个 `generateNo()`。需要 DDL：仅当唯一索引缺失。需要补测试：是（并发生成 10 个单号必须全不同）。

### MES-QC-09 模板 `status` 可由客户端直接置 ACTIVE，绕过启用校验

- **证据**: `.../mes/qc/domain/bo/QcInspectTemplateBo.java:43`、`.../mes/qc/service/impl/QcTemplateServiceImpl.java:70-82`、`:86-101`、`:119-137`
- **现状**: `insertByBo`/`updateByBo` 用 `MapstructUtils.convert(bo, QcInspectTemplate.class)` 整对象映射，`status` 只在为 null 时默认 DRAFT（`:73-75`）。而"必须有启用项目"的校验只在 `changeStatus(active=true)` 里（`:124-132`）。`validateItems`（`:195-231`）对 `enabled="0"` 的项目直接 `continue`（`:202-204`），因此一个"项目全为停用"的模板可以通过校验。
- **触发路径**: 直接调 `POST /mes/qc/template`（权限 `mes:qc:template:add`）传 `{"status":"ACTIVE","items":[{"itemCode":"X","itemName":"X","enabled":"0"}]}`；或对已 ACTIVE 的模板 `PUT` 时把 items 全改为停用。
- **后果**: 数据库里存在 ACTIVE 但零启用项目的模板，`matchTemplate`（`QcInspectDetailServiceImpl.java:137-155`）会选中它并生成 **0 条明细** → 检验单看起来"有模板"，实际无明细、无法自动判定，只能人工判定（而界面不会提示"模板为空"）。同一物料+类型存在多条 ACTIVE 时按 `versionNo` 取最大（`:143`），配置错误难以发现。
- **修复建议**: ①`insertByBo`/`updateByBo` 强制 `entity.setStatus(STATUS_DRAFT)`（忽略入参），状态只能走 `changeStatus`；②`changeStatus(active=true)` 改为条件更新 `UPDATE qc_inspect_template SET status='ACTIVE' WHERE template_id=? AND status='DRAFT'`，影响 0 行抛异常；③`validateItems` 增加"至少一个 `enabled=1` 的项目"。需要 DDL：无。需要补测试：是。
- **测试现状**: `src/test` 下未找到 `QcTemplateServiceImpl` 的单测。

### MES-QC-10 多批次关联同一 IQC 时批次隔离失败（异常被吞）

- **证据**: `.../mes/lt/service/impl/LtLotServiceImpl.java:366-401`、`.../mes/qc/service/impl/QcIqcServiceImpl.java:194-200`、`.../ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java:342-359`
- **现状**: `applyIqcResult` 用 `baseMapper.selectOne(Wrappers.<LtLot>lambdaQuery().eq(LtLot::getIqcId, iqcId))`（`:380`）定位批次。MyBatis-Plus 的 `selectOne` 在返回多行时抛 `TooManyResultsException`。ERP 侧同一 IQC 明确对应**多批次**：`markQcStatusByReceive` 取该收货单+物料的**全部批次号**批量回写（`WmBatchServiceImpl.java:342-359`，日志打印 `batchCodes` 与影响行数）。调用方把整个回写包在 `try/catch(Exception)` 里只打 warn（`QcIqcServiceImpl.java:196-200`），并注明"失败不影响 IQC 结果"。
- **触发路径**: 一个收货单某物料分 2 个批次入库 → MES 侧产生/关联 2 个 lot 且都带同一 `iqc_id` → IQC 判 UNQUALIFIED → `applyIqcResult` 抛 `TooManyResultsException` → 被吞 → 两个 lot 的 `qc_status` 都停留在 PENDING，`quarantine()` 未执行。
- **后果**: 不合格批次既没有被置 FAIL 也没有被隔离。虽然 PENDING 会被发料门禁拦截（保守），但"不合格"这一事实没有落到批次上：隔离库存报表、追溯页面、解除隔离流程都看不到这批货，NCR 与库存台账对不上；同时 `LtLotServiceImpl.java:390-397` 的"终态批次仅置 FAIL 未隔离"分支同样只 warn。
- **修复建议**: ①`applyIqcResult` 改为 `selectList(...eq(iqcId))` 并遍历所有 lot（与 ERP 多批次口径对齐）；②`QcIqcServiceImpl.java:198` 的 catch 从"仅 warn"改为写待处理告警（或让异常回滚 IQC 提交），至少不能静默；③补一个定时对账：`qc_iqc.inspect_result='UNQUALIFIED'` 但关联 lot 的 `qc_status<>'FAIL'` 的记录报警。需要 DDL：无。需要补测试：是（多 lot 同 iqcId）。
- **测试现状**: `LtLotServiceImplTest.java:496-548` 覆盖了 `applyIqcResult` 的 null/无关联/PASS/FAIL/非法值，**没有多批次用例**（mock 的 `selectOne` 只会返回单值，天然掩盖该缺陷）。

### MES-QC-11 IQC/IPQC/OQC 的 `add` 端点漏 `@Validated`，BO 校验不生效

- **证据**: `.../mes/qc/controller/QcIqcController.java:71`、`.../mes/qc/controller/QcIpqcController.java:68`、`.../mes/qc/controller/QcOqcController.java:68`、`.../mes/qc/controller/QcTemplateController.java:56`、`.../mes/qc/domain/bo/QcIpqcBo.java:32`、`:54`
- **现状**: 同一个包里 `QcTemplateController` 用 `add(@Validated @RequestBody QcInspectTemplateBo bo)`（`:56`），而三个检验单 Controller 都是 `add(@RequestBody QcIqcBo bo)`（`QcIqcController.java:71`）。类级 `@Validated`（`QcIqcController.java:29`）只驱动**参数自身**的约束校验，不会级联到 `@RequestBody` 对象的字段。
- **触发路径**: `POST /mes/qc/iqc` 传 `{"materialId": null}` 或 `POST /mes/qc/ipqc` 传 `{"mesWoId": null}`。
- **后果**: `QcIqcBo.materialId`、`QcIpqcBo.mesWoId`/`materialId` 上的 `@NotNull` 形同虚设：可以落库一条无物料（走"通用模板"分支，`QcInspectDetailServiceImpl.java:149-154`）或无工单（`inspect` 时 `mesWoId==null` 直接跳过 ERP 回写，`QcIpqcServiceImpl.java:219-221`）的检验单——数据进去时没人拦，出来时静默不联动。
- **修复建议**: 三个端点的 `@RequestBody` 前补 `@Validated`（与同包模板 Controller 一致）。需要 DDL：无。需要补测试：是（缺 materialId/mesWoId 必须 400）。
- **测试现状**: 无 Controller 层校验测试。

### MES-NCR-01 NCR 处置不驱动批次/库存/工单：`lotId` 恒 null + 退货分支无动作

- **证据**: `.../mes/ncr/service/impl/NcrOrderServiceImpl.java:202-205`、`:210-216`、`:221-226`、`:231-237`、`:598-606`、`:622-630`、`:646-657`
- **现状**: 四个自动建单入口都把 `lotId` 位置传 `null`：

  ```java
  public NcrOrderVo autoCreateFromIqc(Long iqcId, Long receiveId, Long materialId, String defectInfo) {
      return createNcr(SOURCE_IQC, iqcId, null, materialId, null, defectInfo,
          null, null, null, null, null, null, null, null);   // ← 第 7 个参数 lotId = null
  }
  ```

  `autoCreateFromIpqc`（`:214`）、`autoCreateFromOqc`（`:224`）、`autoCreateFromExit`（`:235`）同样传 null。而处置分支的批次动作全部以 `lotId != null` 为前置：`handleScrapDisposition` 的 `if (order.getLotId() != null)`（`:600`）与 `handleAcceptDisposition` 的 `if (order.getLotId() != null)`（`:624`）。`handleReturnDisposition`（`:646-657`）**除了把状态改成 CLOSED 之外没有任何语句**，连库存动作的入口都没有。另外 `autoCreateFromIqc` 收到 `receiveId`、`autoCreateFromOqc` 收到 `soId`，两个参数都只用于入参、没有落库也没有传给处置分支（`ncr_order` 表无 receive_id/so_id 列，见 §6）。
- **触发路径**: IQC 判 UNQUALIFIED → `autoCreateFromIqc` 建 NCR（lot_id=NULL）→ 评审 → 处置选 `SCRAP` → `handleScrapDisposition` 因 lotId 为 null 直接跳过 `ltLotService.scrap()`，只把 NCR 置 CLOSED。
- **后果**: 对**所有自动创建的 NCR**（也就是系统实际产生的全部不合格品记录），报废不产生任何批次报废/库存扣减，让步接收不产生批次放行。`QUARANTINED` 状态从未被置位（MES 侧批次隔离只在 `LtLotServiceImpl.applyIqcResult` 的 FAIL 分支发生，`NcrOrderServiceImpl` 的 `quarantineLotIfNeeded` 因 lotId 为 null 直接返回）。设计计划把"创建时若关联 lot_id 则隔离、SCRAP 置 SCRAPPED、ACCEPT_AS_IS 解隔离"列为**已验收 ✅** 的交付物（`doc/mes/plans/archive/2026-07-28-mes-p0-4-ncr-rework-implementation-plan.md:130`、`:271`、`:279-280`），因此这是**计划已验收但实际不可达**的功能，而不是未规划的缺口。
  需要单独说明 `RETURN` 分支（`:646-657`）：它只改状态**是设计决策**（同计划 `:281`"RETURN -> 置 NCR status=CLOSED（退货走 ERP 采购退货流程，MES 仅记录）"、`:654`"NCR 与 ERP 采购退货联动：disposition=RETURN 仅在 MES 记录 CLOSED，ERP 采购退货走既有 `pu_return` 流程不联动"）。因此本条不把 RETURN 当缺陷，只作为"来料不良无法从 NCR 一键触发供应商索赔"的功能缺失记入 §4。
- **修复建议**: ①`autoCreateFromIqc` 由 `receiveId + materialId` 反查 `lt_lot`（或直接用 `LtLotServiceImpl.applyIqcResult` 已写入的 `lot.iqc_id`）解析出 lotId 后传入；`autoCreateFromIpqc/Oqc/Exit` 同理按流转卡/工单/销售订单定位批次；②`ncr_order` 增列 `receive_id`/`so_id`（**需 DDL**）以便后续退货分支与 ERP 联动；③补一条"处置分支的下游动作"验收用例，防止"✅ 已验收"与实际不可达再次脱节。需要补测试：是（每个处置分支断言下游动作被调用）。
- **测试现状**: `NcrOrderServiceImplTest.java:92-125` 只覆盖 `autoCreateFromIqc` 的 defectQty 默认值/解析失败/幂等，**没有任何处置分支的下游动作断言**。

### MES-NCR-02 报废/让步的批次动作失败被吞掉，NCR 仍然关闭

- **证据**: `.../mes/ncr/service/impl/NcrOrderServiceImpl.java:598-617`、`:622-641`、对比 `:586-593`
- **现状**: 三个分支的异常策略不一致：

  | 分支 | 批次/库存动作 | 失败处理 | 之后 |
  |---|---|---|---|
  | 返工 `handleReworkDisposition` | 创建返工单 | `catch { log.error; throw new RuntimeException("返工单创建失败") }`（`:588-591`） | 事务回滚，NCR 保持 DISPOSITION |
  | 报废 `handleScrapDisposition` | `ltLotService.scrap()` | `catch { log.warn("NCR批次报废失败(不阻断)") }`（`:603-605`） | 继续 CAS 到 `CLOSED`（`:607-614`） |
  | 让步 `handleAcceptDisposition` | `ltLotService.release()` | `catch { log.warn("NCR批次放行失败(不阻断)") }`（`:627-629`） | 继续 CAS 到 `CLOSED`（`:631-640`） |

- **触发路径**: 批次处于非 `IN_STOCK/IN_USE` 终态（`LtLotServiceImpl` 的 scrap/release 有状态守卫，会抛异常），或数据库瞬时故障 → 报废动作抛异常被吞 → NCR 状态照常推进为 CLOSED。
- **后果**: 单据显示"报废处置完成并关闭"，实物与库存未变；没有任何重试入口或待办（`log.warn` 之外无痕迹），只能靠人工翻日志发现。与返工分支的严格策略并存，说明这是遗漏而非设计。**这是 MES-NCR-01 的放大器：一旦修好 lotId，这个 catch 会让报废静默失败。**
- **修复建议**: 与返工分支对齐——`scrap`/`release` 失败直接抛 `ServiceException` 让事务回滚（NCR 停在 `DISPOSITION`，用户可重试）；若业务上确实允许"先关单后补处置"，则必须写一条补偿待办表并由 SnailJob 重试。需要补测试：是（scrap 抛异常时 NCR 必须停在 DISPOSITION）。

### MES-X-01 冻结库存无解除路径（让步接收后永久冻结）+ IQC 不合格旁路

- **证据（冻结不可解除）**: 全 `ruoyi-erp` 模块 grep `set\(WmMaterialStock::getFrozenFlag` **只有 1 处命中**：`ruoyi-erp/.../wm/service/impl/WmMaterialStockServiceImpl.java:219` `.set(WmMaterialStock::getFrozenFlag, 1L)`（冻结）；grep `unfreeze|unFreeze|解冻` **零命中**；其余 30 余处 `frozenFlag` 命中全是查询条件 `.eq(...getFrozenFlag, 0L)` 或**新建库存行**的初始化 `newStock.setFrozenFlag(0L)`（如 `ReceiveInStrategy.java:200`、`TransferStrategy.java:152`、`ReworkReceiveStrategy.java:229`、`MiscRecptStrategy.java:124`、`ProductionCompletionStrategy.java:225`、`SubcontractReceiveStrategy.java:272`、`SoReturnServiceImpl.java:431`、`WmMaterialReceiveExecutorServiceImpl.java:489`）。
- **证据（旁路）**: 冻结入口 `WmMaterialStockServiceImpl.java:212-222` 按**批次号**定位库存行，取不到批次即 `return 0`（同段 `:215-217`）；上架门禁只在 `receive.getNoticeId() != null` 时校验 IQC 状态（`WmMaterialReceiveCrudServiceImpl.java:360-390`，注释明示"无到货通知则跳过"）。
- **触发路径**: ①NCR 判"让步接收（ACCEPT_AS_IS）"→ `handleAcceptDisposition` 调 `ltLotService.release(lotId)`（`NcrOrderServiceImpl.java:626`）——即使 lotId 修好，`release` 只改 MES 侧 `lt_lot`，**ERP 侧 `wm_material_stock.frozen_flag` 没有任何解除入口**；②"无到货通知 + 收货行无 batch_code"的收货被判 IQC 不合格 → 冻结按批次号查不到行返回 0，上架门禁又因无到货通知跳过 → 该批库存既没冻结也没被拦。
- **后果**: ①**单向冻结 = 永久冻结**：被 IQC 判不合格而冻结的库存永久无法发料/出库/调拨/盘点/预留（全部过滤 `frozen_flag=0`）。这不是"NCR 没做联动"——计划明确"隔离只在 MES 批次层，不反写 ERP `frozenFlag`"（`doc/mes/plans/archive/2026-07-28-mes-p0-4-ncr-rework-implementation-plan.md:54-56`、`:652`），问题在于**另一个方向**：ERP 的 IQC 回写（E10）会主动冻结库存，却没有任何一条路径能解冻——MES 侧的 `lt_lot.release()` 与 ERP 的 `frozen_flag` 是两套互不通气的状态，于是"让步接收"在 MES 显示已解隔离、在 ERP 仍然冻结。字典 `mes_ncr_disposition` 里"让步接收/解隔离放行"的语义在库存层无法实现，只能改库。同理，**复检（re-inspect）也无入口**：`QcIqcServiceImpl.java:158-160` 对 `COMPLETED` 直接抛"不可重复检验"，没有复检单/复检回写，即使人工改判也没有解冻路径。
  ②**旁路**：`WmMaterialStockServiceImpl.java:212-222` 按批次号定位库存行，取不到批次即 `return 0`；上架门禁又只在 `receive.getNoticeId() != null` 时校验 IQC 状态（`WmMaterialReceiveCrudServiceImpl.java:360-390`，注释明示"无到货通知则跳过"）。"无到货通知 + 收货行无 batch_code"的收货被判 IQC 不合格时，既不冻结也无人拦，可被生产领料与销售出库正常放行——不满足"不合格批次拦截不能只靠单一入口"。
- **修复建议**: ①补冻结解除入口：`WmMaterialStockService` 增加 `unfreezeByBatch(materialId, batchCode, reason)` 与 `unfreezeByReceive(receiveId, materialId, reason)`，由 NCR 让步接收/退货处置经**新增的 MES→ERP MQ 通道**调用（NCR 模块当前不发任何 MQ），并加权限码 + `sys_menu` 种子 + 操作日志 + 解除原因（可审计）；②冻结改为**不依赖批次号**：收货行无 `batch_code` 时按 `receive_id + material_id` 冻结该收货产生的库存行；③上架门禁去掉 `noticeId != null` 前置条件，改为"需检物料且 `iqc_status` 非 QUALIFIED 一律拦截"；④补复检入口：新增复检单或允许 `inspect` 在 `COMPLETED` 且 `inspect_result=UNQUALIFIED` 时以复检方式重判，并联动解冻。需要 DDL：`ncr_order` 需保留 receive/so 关联（同 MES-NCR-01），解冻原因字段可选。需要补测试：是。
- **测试现状**: 无冻结解除相关测试（功能不存在）；`LtLotServiceImplTest.java:514-538` 只覆盖 MES 侧 `applyIqcResult` 的 PASS/FAIL 分支，未覆盖 ERP 侧冻结。

### MES-X-02 返工入库 MQ 消费端是空实现，返工状态机不闭环

- **证据**: `ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ReworkStockConsumer.java:44-57`
- **现状**: `doProcess` 读出返工单后只打日志：

  ```java
  if ("ISSUE".equals(message.getOperationType())) {
      log.info("返工订单[{}]出库完成，状态更新", rework.getReworkNo());
  } else if ("RECEIVE".equals(message.getOperationType())) {
      log.info("返工订单[{}]入库完成，状态更新", rework.getReworkNo());
  }
  ```

  两个分支都只 `log.info`，`rework` 对象除日志外未被使用，没有任何 `update` 调用（对比同目录 `ErpIqcCompletedConsumer`/`ErpOqcCompletedConsumer` 都有实际回写）。
- **触发路径**: 返工单出库（`ReworkIssueStrategy.java:135`）或返工入库（`ReworkReceiveStrategy.java:181`）发送 `REWORK_STOCK_QUEUE` 消息 → 消费者打日志后 ack。
- **后果**: `prd_rework_order` 的状态永远停在派工时的值，界面上返工单"已出库/已入库"无法体现，返工数量闭环（`rework_qty` 与实物）缺失；结合 MES-X-01，返工入库后的库存状态也无从核对。
- **修复建议**: 在 `ISSUE` 分支条件更新 `status='IN_PROGRESS'`（若枚举无此值则先补枚举/字典），`RECEIVE` 分支条件更新 `status='COMPLETED'` 并回写实际入库数量；用 `update ... where rework_id=? and status=?` 保证幂等。需要补测试：是。
- **测试现状**: 无该消费者的单测。

### MES-NCR-03 `updateByBo` 整实体回写，可把已完成的处置回滚

- **证据**: `.../mes/ncr/service/impl/NcrOrderServiceImpl.java:136-164`
- **现状**: `selectById` 取实体（`:139`）→ 校验 `status ∈ {OPEN, REVIEW}`（`:144-146`）→ 逐个 set 业务字段（`:148-160`）→ `baseMapper.updateById(order)`（`:161`）。`updateById` 按非 null 字段整行 SET，而 `order` 里带着第 139 行读到的 `status`、以及 `reviewBy/reviewTime/disposition/dispositionSignatureId/closedTime` 等既有值。校验与写入之间没有 CAS，也没有 `version` 乐观锁列（11 张 qc/ncr 表全部无 `version`，见 §6）。
- **触发路径**: 用户 A 打开 NCR 编辑页（读到 status=REVIEW）→ 用户 B 处置该单（CAS 推进到 DISPOSITION，甚至 CLOSED）→ 用户 A 点保存 → `updateById` 把 `status=REVIEW` 写回。
- **后果**: NCR 从 `DISPOSITION`/`CLOSED` 退回 `REVIEW`，处置签名与处置时间被清空或残留（取决于读到的值），单据可以**再次处置**：第二张返工单（`ncr_rework_order` 无 `ncr_id` 唯一约束）、二次报废/让步，数量与成本重复计入；`closedTime` 被抹掉后"已关闭"审计线索丢失。
- **修复建议**: ①`updateByBo` 改为 `update(null, lambdaUpdate().set(白名单字段...).eq(ncrId).in(status, OPEN, REVIEW))`，`rows==0` 抛"状态已变更"，绝不整实体回写；②`ncr_order` 加 `version` 列 + `@Version`（需 DDL + 存量初始化 0）。需要补测试：是（并发编辑 + 处置）。
- **测试现状**: 无并发编辑用例。

### MES-NCR-04 两个 NCR 权限码无 `sys_menu` 种子，非超管永久 403

- **证据**: `.../mes/ncr/controller/Ncr8dReportController.java:35`（`@SaCheckPermission("mes:ncr:8d:query")`）、`.../mes/ncr/controller/NcrDefectController.java:37`（`@SaCheckPermission("mes:ncr:defect:list")`）、`script/sql/ry-vue.sql:92569`、`:92577`、`:92583`、`:92584`
- **现状**: 对 `script/sql/ry-vue.sql` grep `mes:ncr:(8d|defect):` 的全部 4 条命中为：`mes:ncr:8d:list`（92569，菜单）、`mes:ncr:defect:add`（92577，按钮）、`mes:ncr:8d:edit`（92583）、`mes:ncr:8d:complete`（92584）。**`mes:ncr:8d:query` 与 `mes:ncr:defect:list` 在菜单种子中不存在**。
- **触发路径**: 给角色勾选全部 NCR 菜单 → 打开 8D 报告页（`GET /mes/ncr/8d/{ncrId}`）或 NCR 单页面的缺陷明细（`GET /mes/ncr/defect/list`）。
- **后果**: 除超管外一律 403，8D 报告面板打不开、缺陷明细加载失败（前端 `order/index.vue` 依赖该接口），且因为菜单看起来"已授权"，排查成本很高。这是本项目反复出现的坑。
- **修复建议**: 二选一——①把代码改为已存在的权限码（`8d:query` → `mes:ncr:8d:list`，`defect:list` → `mes:ncr:defect:add` 或 `mes:ncr:order:query`）；②在 `ry-vue.sql` 的 `sys_menu` 补两条 F 类型按钮（`mes:ncr:8d:query`、`mes:ncr:defect:list`）并**同步存量库**（`doc/system/数据库增量变更记录.md` 登记）。推荐①（语义上查询权限本就该复用列表权限）。需要 DDL：方案②需要。需要补测试：否。
- **测试现状**: 无权限码与菜单一致性的自动化校验（建议加一条启动期/CI 检查：扫描 `@SaCheckPermission` 值必须全部存在于 `sys_menu.perms`）。

### MES-NCR-05 PROCESS 来源 `sourceId` 传 null，幂等键对 NULL 不去重

- **证据**: `.../mes/ncr/service/impl/NcrOrderServiceImpl.java:231-237`、`script/sql/ry-mes.sql:1653`（`uk_source(source_type, source_id)`）、`.../mes/pro/service/impl/ProFlowCardServiceImpl.java:656-661`
- **现状**: `autoCreateFromExit` 明确把 `sourceId` 传 `null`（`:235`：`createNcr(SOURCE_PROCESS, null, mesWoId, ...)`）。`createNcr` 的查重以 `sourceId != null` 为前提；即使查重被绕过，`uk_source(source_type, source_id)` 在 MySQL 中**含 NULL 的索引项不参与唯一性判定**，也无法兜底。调用点（工序出口检验）没有前置查重。
- **触发路径**: 同一流转卡工序反复出站/重报（返工后再次出站）→ 每次建一张 NCR。
- **后果**: 同一工序不良产生多张 NCR，安灯告警重复、不良数与帕累托统计重复计数；后续 NCR 与返工单/报废动作叠加，不良品数量被放大。
- **修复建议**: `autoCreateFromExit` 用 `stepRecordId` 作为 `sourceId`（配合 `SOURCE_PROCESS` 即可唯一定位一次出站），并给 `uk_source` 加 `tenant_id` 维度（现有唯一键不含租户，跨租户同 source_id 会误冲突/漏冲突）。需要 DDL：是（存量 NULL source_id 数据需回填）。需要补测试：是。
- **测试现状**: `NcrOrderServiceImplTest` 无 PROCESS 来源用例。

### MES-NCR-06 电子签名可被完全绕过（同一权限码两个入口）

- **证据**: `.../mes/ncr/controller/NcrOrderController.java:117-124`、`:139-146`、`:126-133`
- **现状**: `PUT /mes/ncr/dispose/{ncrId}`（`:119`，仅 `disposition` + `remark`，无签名参数）与 `PUT /mes/ncr/dispose/{ncrId}/sign`（`:141`，走 `NcrDisposeSignBo` + 二级认证）**共用同一个权限码** `mes:ncr:order:dispose`（`:117`、`:139`）。接口注释自认："未签署的 dispose 接口保持可用(P1 决策: 签署尚未强制)"（`:131`）。
- **触发路径**: 持有 `mes:ncr:order:dispose` 的用户直接调 `PUT /mes/ncr/dispose/{ncrId}?disposition=SCRAP`。
- **后果**: 报废、退货、让步接收这些**不可逆的授权动作**可以在没有二级认证、没有电子签名、没有责任人凭证的情况下完成；`bindDispositionSignature` 与 `es_signature` 台账形同虚设，IATF 16949 / GMP 的"关键处置需电子签名"要求不满足，出事后无法追溯是谁批准的。文档把它标记为"P1 决策"，但审计上它是**权限缺口**而非待办。
- **修复建议**: ①把无签名接口下线（删除路由或加 `@Deprecated` 并返回 400）；若必须保留过渡期，则加配置开关 `nexus.mes.ncr.dispose-requires-signature=true`（默认 true）+ 单独的高权权限码 `mes:ncr:order:dispose-nosign`，并在 NCR 上记录"未签名处置"标记供审计；②`disposeWithSignature` 增加"处置方式不可逆"白名单校验。需要 DDL：方案①无（若加标记列则需）。需要补测试：是（无签名入口必须拒绝）。
- **测试现状**: 无签名相关测试。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果 | 建议 |
|---|---|---|---|---|
| MES-QC-12 | 定性判定只认硬编码白名单 `Set.of("OK","PASS","合格","良好","正常","NG-NO","是")`，模板层没有枚举型标准（`standardType` 仅 NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL），标准描述固定回退为 `"OK/NG"` | `QcJudgeEngine.java:35`、`:56-58`、`QcInspectDetailServiceImpl.java:160-162` | ①"是/否"型检验项只要录"是"就 PASS，哪怕业务语义是"有缺陷"；②"NG-NO"被判合格；③无法配置"允许值集合"，所有非白名单值一律 FAIL（如录"A"、"黑"）→ 误判不合格并触发 NCR | 定性标准改为可配置的允许值集合（模板加 `enum_values`，字典化）；白名单移除语义歧义项，至少去掉 `是`/`NG-NO`；`是/否` 类项目按模板显式声明哪个值是合格 |
| MES-QC-13 | 不良率 `defectQty/sampleQty*100` 只判分母为 0/空，不校验 `defectQty ≤ sampleQty`、不校验非负 | `QcIpqcServiceImpl.java:240-247`、`:184-189` | 可写入 200% 或负的不良率并随 MQ 回传 ERP 工单聚合质检状态（`MesMqSendService.java:174`），SPC/看板与工单质量报表出现不可能值 | 提交前校验 `0 ≤ defectQty ≤ sampleQty`（`sampleQty>0`），越界抛 `ServiceException`；`defectRate` 上限 100 |
| MES-QC-14 | 模板启用/停用是读-改-写（`selectById` → `setStatus` → `updateById`），无 CAS；`generateDetails` 的幂等同样是"先查后写" | `QcTemplateServiceImpl.java:119-137`、`QcInspectDetailServiceImpl.java:45-50` | 并发启停最后写入者胜（可能留下"以为已停用其实 ACTIVE"的模板，检验单继续按旧模板生成）；并发生成明细产生重复明细行 | 状态流转改条件更新（`WHERE status='DRAFT'`）；`qc_inspect_detail` 加唯一索引 `(tenant_id, check_type, check_id, item_id)` |
| MES-QC-15 | `RESULT_PARTIAL`（"仅 MAJOR/MINOR 失败=让步/挑选"）永不落库：`inspect()` 只接受 QUALIFIED/UNQUALIFIED，`inspectWithDetails` 把 PARTIAL 折叠为 UNQUALIFIED | `QcJudgeEngine.java:32`、`:134`、`QcIqcServiceImpl.java:151-153`、`:252-254` | 判定引擎区分了"整单拒收"与"部分不合格可让步"，但数据层无法表达，让步接收只能靠 NCR 自由文本备注；统计"因 CRITICAL 拒收"与"仅次要项不合格"无法区分 | 若保留 PARTIAL 语义，给 `qc_iqc.inspect_result` 增加 PARTIAL 并放开 `inspect()` 白名单（同时确认 ERP 消费端对 PARTIAL 的处理）；否则删除该枚举与相关注释，避免误读 |
| MES-QC-16 | IPQC 自动建单幂等键在 `processId` 为空时失效：`existsByWoAndProcess` 用 `.eq(QcIpqc::getProcessId, null)`（MyBatis-Plus 生成 `process_id = NULL`，恒不匹配） | `QcIpqcServiceImpl.java:120-125`、`:144-148`、`ProFlowCardServiceImpl.java:768` | 工序步骤 `processId` 为空的关键工序，每次报工都会新建一张 IPQC（重复单、重复检验、重复 NCR），且 MES-QC-04 的唯一索引也挡不住 NULL 组合 | `existsByWoAndProcess` 改用 `isNull` 分支；`autoCreateForKeyProcess` 在 `processId==null` 时按 `stepRecordId` 幂等；`qc_ipqc` 唯一索引改为对 `step_record_id` 建 |
| MES-QC-17 | `generateDetails` 生成明细时把**所有项目**写成同一个建议抽样量，且 `inspectWithDetails` 提交时又用前端传入的 `entry.sampleQty` 覆盖明细 | `QcInspectDetailServiceImpl.java:58`、`:76`、`:112` | 抽样量口径混乱：同一检验单不同项目（不同 AQL/不同检验水平）应有不同抽样量，现在统一；且前端可任意改抽样量，不良率分母不可信 | 抽样量按项目级 AQL 分别计算（与 MES-QC-07 一并修）；明细 `sample_qty` 只允许服务端写入 |
| MES-QC-18 | `qc_inspect_detail.measured_value` 为单值字符串字段，多值实测用 `,;空格` 分隔（`judgeQuantitative` 支持多值全过才 PASS），但**样本量与实测值个数不校验** | `QcJudgeEngine.java:63-78`、`QcInspectDetailServiceImpl.java:109-115` | 抽样 125 件只录 1 个实测值也判 PASS，"抽样检验"退化为"单件检验"，与 AQL 抽样量脱节（叠加 MES-QC-05 可只交 1 项即整单合格） | 校验实测值个数 ≥ `sample_qty`（或明确记录"实测项数"），否则判 PENDING 并要求说明 |
| MES-NCR-07 | 电子签名走 `REQUIRES_NEW` 先落库再绑定，处置 CAS 失败时签名不随事务回滚，形成"有签名无处置"孤儿；`bindDispositionSignature` 无状态校验、可反复重绑 | `NcrOrderServiceImpl.java:334-339`、`:364-382`、`:419-425`、`:290-292` | 同一 NCR 挂多条处置签名，台账与处置记录无法对账，审计时无法判定哪次签名有效 | 签署前做状态预检（仅 OPEN/REVIEW）；`bindDispositionSignature` 已有值则拒绝覆盖；给签名加作废标记 |
| MES-NCR-08 | 8D 报告懒创建是"先查后插"，`ncr_8d_report` 只有普通索引 `idx_ncr_id`、无唯一约束（`ry-mes.sql:1494-1495`） | `Ncr8dReportServiceImpl.java:44-58`、`ry-mes.sql:1494` | 并发/重复点击产生两份 8D，编辑与完成只作用于其中一份，交付内容不一致 | 加 `UNIQUE INDEX uk_tenant_ncr(tenant_id, ncr_id)`，插入捕获重复键后回查返回既有记录 |
| MES-NCR-09 | 8D 不校验 NCR 是否存在/是否已关闭即可创建与完成；`complete` 不校验 D1~D8 是否填写；`COMPLETED` 后仍可编辑 | `Ncr8dReportServiceImpl.java:44-58`、`:68-88`、`:99-115` | 可对任意 `ncrId` 造报告；D1~D8 全空的报告能置 COMPLETED，8D 流程形同虚设 | 创建前校验 NCR 存在且未删除；`complete` 前校验 D1~D8 关键字段非空；`COMPLETED` 拒绝 `updateByBo` |
| MES-NCR-10 | 缺陷明细补录（`NcrDefectServiceImpl`）不校验 NCR 存在性与状态，已 `CLOSED` 的 NCR 仍可追加明细 | `NcrDefectServiceImpl.java:54-68`、`NcrDefectController.java:49-52` | 关闭后篡改不良明细，不良数量/帕累托统计与已归档的 8D 报告不一致（`ncr_defect` 无 `(ncr_id, defect_code_id)` 唯一约束，可重复补录同一缺陷） | 补录前校验 NCR 存在且 `status != CLOSED`；加唯一索引 `(tenant_id, ncr_id, defect_code_id)` |
| MES-NCR-11 | 不良代码三级维护三处缺陷：①`enabled` 默认写 `"Y"` 而 DDL/种子是 `'1'/'0'`，列表按 `enabled` 精确匹配 → 新增后筛选查不到；②查询组树后只返回 `parentId=0` 根节点，按名称/编码搜子级结果被丢弃；③`insertByBo` 只校验父节点存在，不校验 `level = parent.level+1`，也不前置查重 | `NcrDefectCodeServiceImpl.java:100`、`:174`、`:49-65`、`:87-106`、`ry-mes.sql:1534`、`:1544`、`:1551` | 新增不良代码后等于不可用；搜"划痕"等二级代码返回空导致用户重复新建并撞唯一键报 500；可造出 `level=1` 挂在三级节点下，前端按 level 分组错乱 | 默认值改 `"1"` 并迁移历史 `'Y'`；带查询条件时返回扁平列表；以 `parent.level+1` 覆盖并校验 `level`，前置查重返回友好错误 |
| MES-NCR-12 | `ncr_rework_order` 无 `ncr_id` 唯一约束、`deriveReworkOrder` 无查重；`NcrReworkOrder.status` 的 `IN_PROGRESS` 无任何写入点 | `NcrReworkOrderServiceImpl.java:106-155`、`ry-mes.sql:1674`、`:1683`、`ProWorkOrderServiceImpl.java:1182-1186` | 与 MES-NCR-03 叠加时 NCR 二次处置会重复派工、成本重复计入；返工单恒为 OPEN/COMPLETED，"返工中"筛选永远为空（死状态） | 加 `UNIQUE INDEX uk_ncr(ncr_id)` 并前置查重返回既有单；补 `IN_PROGRESS` 写入点（见 MES-X-02）或从字典删除该值 |
| MES-X-03 | 租户/DDL：`qc_inspect_template.uk_template_code`、`ncr_defect_code.uk_code`、`ncr_order.uk_source(source_type, source_id)` 均**不含 `tenant_id`**（同脚本其它表用 `uk_tenant_*` 范式，如 `ry-mes.sql:496`、`:1269`）；且 `MesMqSendService.java:102/144/192` 用 `Long.parseLong(tenantId)`，非数字租户 ID 时只 warn 并留空，消费端多租户写库会落 0 | `ry-mes.sql:2401`、`:1544`、`:1653`、`MesMqSendService.java:100-106` | 多租户下：A 租户占用的模板编码/不良代码编码会让 B 租户无法创建；`uk_source` 跨租户误冲突；MQ 消息租户丢失导致回写数据落到 `tenant_id=0` 而查询不到（表现为"回写成功但页面看不到"） | 三个唯一键补 `tenant_id` 前缀（需 DDL + 存量核对）；`tenantId` 解析失败改为抛异常或改用字符串传递（`tenant_id` 本就是 `varchar(20)`） |



## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | IPQC/OQC 按模板生成检验明细（检验项/标准/上下限） | `QcIpqcServiceImpl.java:111-113`、`QcOqcServiceImpl.java:98-100`（无 `generateDetails` 调用） | `IQcInspectDetailService.generateDetails` 已通用化；`qc_inspect_template.template_type`、`qc_inspect_detail` 表齐备 | 小（各 1 行调用 + `matchTemplate` 支持 IPQC 工序维度）；1~2 人日 |
| 2 | IPQC/OQC 明细自动判定（`inspectWithDetails` 同构方法 + Controller 端点） | 仅 IQC 有：`QcIqcController.java:114`、`QcIqcServiceImpl.java:221-257` | `QcJudgeEngine` 纯函数可复用 | 小；1 人日 |
| 3 | 检验单按 AQL/Ac-Re 判定（而非逐项全过） | `QcInspectItem.java:41-43` 字段无人读；`QcIqcServiceImpl.java:246-254` 只汇总 PASS/FAIL | `AqlSampler` 全表正确且有单测；`AqlSamplerTest.java:20-88` | 中；需 DDL 给 `qc_inspect_detail` 加 `accept_qty`/`reject_qty`；2~3 人日 |
| 4 | 关键工序完工质检门禁 | `QcIpqcServiceImpl.java:127-134`（`selectByMesWoId` 无调用者） | 该方法已实现，只差调用点 | 小；0.5 人日（计划列为"本次不做"，需产品决策） |
| 5 | 复检（re-inspect）入口与解冻联动 | `QcIqcServiceImpl.java:158-160` 对 COMPLETED 直接拒绝 | 无任何复检单据/字段 | 中；1~3 人日（含 ERP 解冻联动） |
| 6 | ERP 冻结库存的解除入口 | 全 ERP 模块 `set(WmMaterialStock::getFrozenFlag` 仅 `WmMaterialStockServiceImpl.java:219`（写 1L），无写 0 的 UPDATE、无 unfreeze | `wm_material_stock.frozen_flag`、字典 `mes_ncr_disposition` 已有"解隔离放行"语义 | 小~中；需新增 MES→ERP MQ 通道；2 人日 |
| 7 | NCR 处置驱动批次（隔离/报废/解隔离） | `NcrOrderServiceImpl.java:203`（lotId 恒 null） | `ILtLotService.quarantineByNcr/scrap/release` **全部已实现**，只差传入 lotId | 小；1 人日（IQC 可按 `receiveId+materialId` 反查） |
| 8 | NCR→ERP 退货/退料联动 | `NcrOrderServiceImpl.java:646-657`（仅改状态） | ERP 已有 `pu_return` 采购退货流程 | 中；计划明确列为不做项，需产品决策；2~3 人日 |
| 9 | 返工单状态回写（出库/入库完成） | `ReworkStockConsumer.java:45-57`（空实现，只打日志） | `REWORK_STOCK_QUEUE`、`PrdReworkOrder`、生产端已发送 | 小；0.5 人日 |
| 10 | 客户维度 / 工序维度检验模板优先级 | `QcInspectTemplate` 仅有 `materialId/materialCode/materialName`（`QcTemplateServiceImpl.java:156-158`），无 `process_id`/`customer_id` 列 | 物料维度"专属 > 通用"已实现（`QcInspectDetailServiceImpl.java:137-155`） | 中；需 DDL；2~3 人日 |
| 11 | 8D 报告 D1~D8 完整性校验与 NCR 状态联动 | `Ncr8dReportServiceImpl.java:44-58`、`:99-115` | `ncr_8d_report` 列齐全、状态字典齐备 | 小；1 人日 |
| 12 | 不良代码 `level` 与父级一致性、编码前置查重 | `NcrDefectCodeServiceImpl.java:87-106` | `parent_id`/`level` 列齐备，删除守卫已有 | 小；0.5 人日 |

## 5. 已核查确认无问题（证明覆盖范围）

1. **AQL 抽样数学正确**（表 1 字码、表 2-A 的 Ac/Re 九档、0/1 起始字码、箭头解析、整批截断）——逐条与 GB/T 2828.1 公开锚点比对全部相符，公式与代码对照见 MES-QC-07；有单测 `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/qc/service/AqlSamplerTest.java:20-88`（含批量边界 8/9、15/16、50/51，`codeLetter` 13000/II→M，整批截断 `plan(8,"II",1.0)`）。**问题只是它没接入判定链路**。
2. **三检 `inspect()` 的状态流转是条件 UPDATE（CAS）而非读-改-写**：`QcIqcServiceImpl.java:163-176`、`QcIpqcServiceImpl.java:184-199`、`QcOqcServiceImpl.java:138-151`，均带 `.eq(id).in(status, PENDING, INSPECTING)` 且 `rows==0` 抛"状态已变更"，并发重复检验被正确挡住。
3. **NCR 单条状态流转同样是 CAS**：`NcrOrderServiceImpl.java:252-260`（review）、`:282-292`、`:317-324`；`review`/`reworkCompleted`/`close` 不会被并发双执行。
4. **NCR 处置有分布式锁**：`@Lock4j("#ncrId")`（`NcrOrderServiceImpl.java:273`、`:417`），处置与签署在同一 NCR 上串行。
5. **NCR 状态无法经 BO 直改**：`insertByBo` 强制 `STATUS_OPEN`（`NcrOrderServiceImpl.java:122`），`updateByBo` 不写 `status`/`disposition`（`:148-160` 白名单式 set），BO 传入的状态值被忽略（与 MES-NCR-03 的"陈旧实体回写"是两个不同问题）。
6. **检验明细的判定结果无法由客户端注入**：`IQcInspectDetailService.updateDetails` 全仓唯一业务调用点是 `QcIqcServiceImpl.java:246`（引擎判定后回写），没有任何 Controller 暴露该方法；`inspectWithDetails` 只接受 `measuredValue`，`judgeResult` 由 `QcJudgeEngine.judgeDetail` 服务端重算（`QcIqcServiceImpl.java:238-246`）。
7. **无匹配模板时的降级行为是有意设计且不阻断**：`QcInspectDetailServiceImpl.java:52-56` 记录 info 日志后返回 0，检验单保持人工判定；`inspectWithDetails` 在无明细时给出可读提示（`QcIqcServiceImpl.java:228-230`）。
8. **模板删除有引用守卫**：已生成明细的模板禁止删除并提示"可停用代替"（`QcTemplateServiceImpl.java:107-112`）。
9. **不良代码删除有孤儿守卫、无法成环**：删除前校验子节点与 `ncr_defect` 引用（`NcrDefectCodeServiceImpl.java:147-160`），`updateByBo` 不可改 `parentId`，因此不存在环。
10. **NCR 自动建单在 IQC/IPQC/OQC 三个来源上有幂等双保险**：`sourceId` 前置查重（`NcrOrderServiceImpl.java:461-470`）+ `uk_source(source_type, source_id)`（`ry-mes.sql:1653`）；仅 PROCESS 来源因传 null 失效（MES-NCR-05）。
11. **三个检验单的 NCR 创建与检验在同一事务**，NCR 建单失败会回滚检验（`QcIqcServiceImpl.java:181-184` 注释与实现一致），质量闭环不会丢 NCR。
12. **字典无缺失**：代码/前端实际使用的 9 个字典类型（`mes_qc_status`、`mes_qc_result`、`mes_ncr_status`、`mes_ncr_source_type`、`mes_ncr_disposition`、`mes_defect_cause`、`mes_rework_status`、`mes_8d_status`、`prd_wo_status`）全部有 `sys_dict_type`/`sys_dict_data` 种子（`ry-vue.sql:90690-90695`、`:90779-90804`、`:91042-91043`、`:91067-91072`、`:90117-90122`）。规范清单里提到的 `qc_result`/`disposition_type`/`criticality`/`judge_result`/`standard_type`/`aql`/`inspection_level` 等名字在 Java/SQL/前端**均未被使用**，不构成缺种子问题（`criticality`/`standard_type` 是自由字符串常量而非字典）。
13. **前端 API 层无缺失函数**：`plus-ui/src/api/mes/qc/{iqc,ipqc,oqc,template}.ts` 与 `plus-ui/src/api/mes/ncr/{order,defect,defect-code,rework,8d-report}.ts` 的函数 url 与后端 Controller 端点逐一对应（例：`QcIqcController.java:100`↔`iqc.ts:88`、`:114`↔`iqc.ts:94`、`QcTemplateController.java:85/96`↔`template.ts:113/118`、`NcrOrderController.java:141`↔`order.ts:104`）。
14. **无"登录即可访问"的端点**：9 个 qc/ncr Controller 的 36 个端点与 36 处 `@SaCheckPermission` 数量配平，无遗漏注解的端点（权限码本身的问题见 MES-NCR-04）。
15. **MQ 通道生产↔消费全部成对、消息体字段名一致**：IQC/OQC/IPQC 三条回写（`MesMqSendService.java:110/152/200` ↔ `ErpIqcCompletedConsumer.java:120`、`ErpOqcCompletedConsumer.java:96`、`ErpIpqcCompletedConsumer.java:104`）、IQC/OQC 自动建单（`WmMaterialReceiveCrudServiceImpl.java:714`↔`IqcAutoCreateConsumer.java:80`、`WmSalesIssueServiceImpl.java:531`↔`OqcAutoCreateConsumer.java:80`）、返工库存（`ReworkIssueStrategy.java:135`/`ReworkReceiveStrategy.java:181`↔`ReworkStockConsumer.java:67`）。问题在可靠性与消费端实现（MES-QC-01、MES-X-02），不在契约。
16. **发料/出库侧确实统一过滤冻结库存**：`ProductionIssueStrategy.java:92`、`WmProductionIssueServiceImpl.java:379/397/595`、`WmSubcontractIssueServiceImpl.java:124`、`ReworkIssueStrategy.java:98`、`MiscIssueStrategy.java:122`、`SalesOutStrategy.java:199`、`SubcontractIssueStrategy.java:112`、`FifoPickingStrategy.java:43`、`FefoPickingStrategy.java:42`、`CountAdjustStrategy.java:91`、`WmSalesIssueServiceImpl.java:254`、`WmStockReservationServiceImpl.java:48/141`、`PuPurchaseReturnServiceImpl.java:362`；PDA 发料另有批次级 QC 拦截（`PdaIssueServiceImpl.java:186` 判 `UNQUALIFIED`）。拦截入口足够多，缺的是"冻结能解、旁路能堵"（MES-X-01）。
17. **11 张 qc/ncr 表的租户与逻辑删除字段齐备**：`tenant_id varchar(20)` 与 `del_flag char(1)` 全部存在（`ry-mes.sql:1475/1493`、`1504/1517`、`1529/1542`、`1619/1650`、`1665/1680`、`2082/2109`、`2122/2145`、`2384/2399`、`2455/2482`、`2497/2519`、`2534/2554`）。
18. **实体与表列无错配**（抽查）：`QcInspectItem.aqlLevel:41↔aql_level:2134`、`samplePlanCode:43↔sample_plan_code:2135`、`enabled:49↔2138`；`QcInspectDetail` 全字段对齐 `2081-2103`；`NcrOrder.materialCode/materialName:60/63↔1630/1631`；`NcrReworkOrder.originalWoNo/reworkWoNo:37/43↔1669/1671`；`NcrOrder.dispositionSignatureId:93↔1641`。

## 6. 前后端字段双向不一致清单（含 SQL / 权限 / 菜单交叉核验）

| 字段 / 项 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `QcInspectItem.aqlLevel` / `samplePlanCode` | 实体 `:41`/`:43`、表 `aql_level`/`sample_plan_code`（`ry-mes.sql:2134-2135`）、模板复制 `QcTemplateServiceImpl.java:177-178` | 模板表单可录入（`template.ts` 走整对象提交） | **可填但后端不读**——配置项无任何效果（MES-QC-07），属最隐蔽的一类不一致：界面看起来支持 AQL，实际判定与抽样都不用 |
| `mes:ncr:defect:list` | `NcrDefectController.java:37` 校验该码 | `order/index.vue:361` 调 `listDefect` | `sys_menu.perms` **无该码**（`ry-vue.sql` 仅 `:92577 mes:ncr:defect:add`）→ 非超管 403（MES-NCR-04） |
| `mes:ncr:8d:query` | `Ncr8dReportController.java:35` 校验该码 | `8d-report/index.vue` 打开即调 | `sys_menu.perms` **无该码**（仅 `:92569 8d:list`、`:92583 8d:edit`、`:92584 8d:complete`）→ 8D 面板 403（MES-NCR-04） |
| `plus-ui/src/views/mes/ncr/defect/index.vue` | — | 页面存在 | `sys_menu` 无任何记录 → 无路由，页面不可达（功能已内嵌 `order/index.vue`）；建议删除页面或补菜单 |
| `qc_inspect_detail.accept_qty` / `reject_qty` | **不存在**（`AqlSampler.AqlPlan` 只算不落库，`:212-214`） | 无 | 判定所需的 Ac/Re 无处持久化 → MES-QC-07 的修复需要 DDL |
| `ncr_order.receive_id` / `so_id` | **不存在**（`autoCreateFromIqc` 收到 `receiveId` 却丢弃，`NcrOrderServiceImpl.java:202-204`） | 无 | 退货/索赔链路无法从 NCR 追溯来源单据 → 需 DDL（MES-NCR-01） |
| `version`（乐观锁列） | 11 张 qc/ncr 表**全部没有** | — | `@Version` 无法使用，MES-NCR-03 / MES-QC-14 只能靠条件 UPDATE |
| 唯一键的租户维度 | `qc_inspect_template.uk_template_code`（`ry-mes.sql:2401`）、`ncr_defect_code.uk_code`（`:1544`）、`ncr_order.uk_source`（`:1653`）均不含 `tenant_id` | — | 同脚本其它表用 `uk_tenant_*` 范式（`:496`、`:1269`）→ 多租户下跨租户编码冲突（MES-X-03） |
| 幂等唯一键 | `qc_iqc` 只有 `uk_iqc_no`（`:2522`），无 `(tenant_id, receive_id, material_id)`；`qc_ipqc` 无 `(mes_wo_id, process_id)`；`qc_oqc` 无 `(so_id, material_id)`；`qc_inspect_item`/`qc_inspect_detail`/`ncr_defect`/`ncr_8d_report` 无任何 UNIQUE | — | 自动建单与明细生成的幂等只能靠应用层先查后写 → MES-QC-04 / MES-QC-14 / MES-NCR-08 |
| 前端字典绑定 | 9 个 `mes_*`/`prd_wo_status` 类型 | `useDict` 使用一致 | 无缺失（见 §5.12） |
| 前端页面与端点 | 36 个端点 | API 层全部有对应函数 | 无"后端有端点前端无函数"（见 §5.13） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交，先止血）**：
  - `MES-QC-01`：三个 `sendXxxCompleted` 去 `@Async` 并加失败落表重投；**同时**把 `SoSalesOrderHeaderServiceImpl.java:521` 改为"非 QUALIFIED 即拦截"。这一条单独提交就能消除"不合格品出库"的资金/质量风险。
  - `MES-NCR-02`：`handleScrapDisposition`/`handleAcceptDisposition` 的 `catch` 改为 rethrow（对齐返工分支），让 NCR 停在 DISPOSITION 可重试。
  - `MES-NCR-01`：`autoCreateFrom*` 解析并传入 `lotId`（IQC 走 `receiveId+materialId` 反查 `lt_lot`）。
  - `MES-X-01`：补 ERP 冻结解除入口（MES→ERP 新 MQ 通道）+ 上架门禁去掉 `noticeId != null` 前置 + 冻结不依赖 `batch_code`。
- **批次 B（P1）**：`MES-QC-02`（IPQC/OQC 明细与自动判定）、`MES-QC-03`（门禁或告警二选一 + 改注释）、`MES-QC-04`（幂等唯一索引，含 DDL）、`MES-QC-05`（缺项提交拒绝）、`MES-QC-06`（公差符号校验 + NOMINAL null 安全）、`MES-QC-08`（单号生成改雪花）、`MES-QC-09`（模板状态只能走 changeStatus）、`MES-QC-10`（`selectList` 替代 `selectOne`）、`MES-QC-11`（补 `@Validated`）、`MES-NCR-03`（`updateByBo` 改白名单条件更新）、`MES-NCR-04`（权限码，1 行改动，建议最先做）、`MES-NCR-05`（PROCESS 传 `stepRecordId`）、`MES-NCR-06`（签名强制）、`MES-X-02`（返工状态回写）。
- **批次 C（P2 / DDL 合并批次）**：`MES-QC-07`（AQL 接入判定，需 `accept_qty`/`reject_qty` 列）、`MES-QC-12`~`MES-QC-15`、`MES-NCR-07`~`MES-NCR-12`、`MES-X-03`（唯一键补 `tenant_id` + `version` 列 + `ncr_order` 补 `receive_id`/`so_id`）。所有 DDL 一次性评审，避免多轮锁表。
- **批次 D（测试补强，随 A/B 一起提交）**：并发生成单号、重复 MQ 投递、多批次共享 IQC、缺项提交、`oqcStatus=NULL` 门禁、每个 NCR 处置分支的下游动作断言、AQL 接入后的判定回归（复用 `AqlSamplerTest` 锚点）。建议额外加一条 CI 校验：扫描 `@SaCheckPermission` 的值必须全部存在于 `sys_menu.perms`（可一次性防住 MES-NCR-04 这类问题）。

## 8. 未覆盖范围与基线复核

### 8.1 基线复核结论

| 基线 | 原文结论 | 复核结果 |
|---|---|---|
| `doc/system/2026-09-06-全项目审查报告.md` §8.2「三检测试已修」 | 三检已有单测 | **复核后仍成立**：`QcIqcServiceImplTest`、`QcIpqcServiceImplTest`、`QcOqcServiceImplTest`、`AqlSamplerTest` 均存在。但覆盖面偏窄：无并发建单/重复 MQ、无"缺项提交"、无多批次共享 IQC、无门禁、无处置下游动作断言（见各条"测试现状"）。 |
| 同上「E10 IQC 不合格冻结库存已实现」 | 冻结已实现 | **部分成立（复核后仍存在缺口）**：冻结写入点确实存在（`WmMaterialStockServiceImpl.java:219` `.set(getFrozenFlag, 1L)`），但**全仓没有任何解冻写入**，且冻结按 `batch_code` 定位、上架门禁仅在 `noticeId != null` 时生效 → 见 MES-X-01。 |
| `doc/erp/plans/archive/2026-07-23-mes-erp-qc-linkage-completion-plan.md` | OQC 硬门禁"仅阻断 UNQUALIFIED、PENDING 放行"（`:315`）；质量门禁强制"本次不做"（`:5`、`:372`） | **复核后仍存在**：门禁实现与计划一致，但"PENDING 放行"在回写丢失时等价于放行（MES-QC-01）；"不做门禁"与代码注释声称的"completeWo 门禁兜底"矛盾（MES-QC-03）。 |
| `doc/mes/plans/archive/2026-07-28-mes-p0-4-ncr-rework-implementation-plan.md` | 任务 3「NcrOrderService（核心状态机+触发+隔离）」标记 ✅，交付物含"隔离/解隔离 + 幂等"（`:130`），流程含"若 lotId != null → isolateLot"（`:271`）、"SCRAP → 批次 SCRAPPED"（`:279`）、"ACCEPT_AS_IS → 解隔离"（`:280`） | **原判定为"已完成"，复核后判定为"不可达"**：`autoCreateFrom*` 恒传 `lotId=null`，`quarantineLotIfNeeded` 直接返回、报废/解隔离分支全部跳过（MES-NCR-01）。计划明确列出的两项不做项（不回写 ERP `frozenFlag` `:652`、RETURN 不联动 ERP 采购退货 `:654`）本轮**不作为缺陷**，但二者与 ERP 的 E10 冻结叠加后产生了计划未预期的"冻结不可解"后果（MES-X-01）。 |

### 8.2 未覆盖范围（诚实声明）

- **未读**：`qc`/`ncr` 两个包的 `domain/vo`、`mapper` 接口、`NcrDefectBo`/`NcrDefectCodeBo`/`NcrReworkOrderBo` 的注解细节；`ESignatureService.signInNewTransaction` 的实现（只据调用方注释判断其事务语义，MES-NCR-07 的具体回滚行为未验证）；`BaseMessageConsumer` 的 ack/重试策略。
- **未覆盖**：`plus-ui` 页面内部逻辑与**布局/字段呈现**（规范 §2.3 的列宽/分组/校验/防重复提交等）本轮完全未审，只做了"端点↔API 函数↔菜单"的存在性核验；`mes/pda` 的 QC 相关页面（仅引用到 `AppPdaQcAqlController`/`PdaQcServiceImpl` 的存在）；`spc` 与 `qc` 的数据联动；ERP 侧 `PrdWorkOrder` 聚合质检状态的消费实现细节（只确认消费者存在与字段一致）。
- **未验证**：所有结论均基于**静态阅读**，未编译、未运行测试、未连库验证（例如"`uk_iqc_no` 唯一索引在存量库中是否存在"只核对了主脚本）。
- **预算执行**：本代理 `read` 调用 29 次（规范上限 30，每次均带 `limit`），未整读任何 SQL 主脚本（SQL/字典/菜单/前端核验走 `grep` + 局部 `read`）；NCR 包与横向核验由两个子代理并行完成，其中 NCR-01/02/03/05/06、X-01、X-02 与两条权限码结论已由本代理二次打开文件/grep 复核。

