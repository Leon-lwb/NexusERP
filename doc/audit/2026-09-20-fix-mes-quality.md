# MES 质量域 P0 修复报告（2026-09-20，B4 批次）

> 对应审计: `doc/audit/2026-09-20-audit-mes-qc.md` 的 **MES-QC-01 / MES-QC-02 / MES-NCR-01 / MES-NCR-02 / MES-X-01** | 分支: `feature/erp-mes-iot-audit-fix`

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| MES-QC-01 | ①OQC 门禁改**白名单**：`fulfill()` 中仅 `oqc_status=QUALIFIED` 的行允许履行，`NULL`/`PENDING`/`UNQUALIFIED` 一律拦截（force 不可绕过），错误信息区分"未检验/回写缺失"②质检回写链路去掉 `@Async` fire-and-forget：IQC/OQC/IPQC 三条回写改为同步投递，失败在调用线程内可观测，可靠性由既有 `ReliableMessageProducer`（先落 `mq_message_record` 状态 SENDING）+ `MessageCompensationTask`/`ReconciliationTask` 补偿重投兜底 | `SoSalesOrderHeaderServiceImpl.java:518-534`、`MesMqSendService.java:19-33`、`:88`、`:128`、`:173`（去掉 `@Async`） | 否 | `SoSalesOrderHeaderServiceImplTest#fulfill_rejectsOqcNullStatusEvenWhenForced`、`#fulfill_rejectsOqcPendingEvenWhenForced`、`MesMqSendServiceTest#qcWritebackMethodsAreNotAsync` |
| MES-QC-02 | ①`QcIpqcServiceImpl.insertByBo`/`QcOqcServiceImpl.insertByBo` 建单即按模板生成检验明细（IPQC 传 `processId`，OQC 传检验批量），与 IQC 同构 ②`matchTemplate` 支持**物料/工序/客户**维度优先级（物料+工序 > 物料 > 工序 > 客户 > 通用），模板新增 `process_id`/`customer_id` 列 ③无匹配模板时行为明确：`log.warn` + 检验单保持 `PENDING`（待人工判定），**不静默判合格** ④IPQC/OQC 补 `inspectWithDetails`（自动判定）+ `selectDetails`（明细查询）服务与端点，复用既有权限码 `mes:qc:ipqc:inspect|query`、`mes:qc:oqc:inspect|query`（**无新权限种子**） | `QcInspectDetailServiceImpl.java:41-100`、`:134-186`、`IQcInspectDetailService.java`、`QcIpqcServiceImpl.java`、`QcOqcServiceImpl.java`、`IQcIpqcService.java`、`IQcOqcService.java`、`QcIpqcController.java`、`QcOqcController.java`、`QcInspectTemplate.java:26-29`、`QcInspectTemplateBo.java:39-42` | 是（`qc_inspect_template` 加 `process_id`/`customer_id` + 覆盖索引） | `QcIpqcServiceImplTest#autoCreateForKeyProcessCreatesWhenNotExists`（断言 `generateDetails("IPQC",…,processId)`）、`QcOqcServiceImplTest#insertByBoSetsInitialStatusAndResult`（断言 `generateDetails("OQC",…)`） |
| MES-NCR-01 | 四个 `autoCreateFrom*` 不再恒传 `lotId=null`：新增 `resolveLot()`，优先复用"批次↔IQC"关联，其次在"该物料下唯一一个待检且未关联 IQC 的批次"时建立关联（多候选不猜测，避免作用到错误批次）；IPQC 来源经 `qc_ipqc` 取物料定位批次；`lot_code` 随批次一并落库；**无法定位时 `log.warn("NCR未关联批次…")` 显式记录**，不伪造 null 走旁路 | `NcrOrderServiceImpl.java:200-250`、`:456-505`、`:660-720`（新增 `resolveLot`/`lotCodeOf`/`materialIdOfIpqc`） | 否 | `NcrOrderServiceImplTest#autoCreateFromIqc_linksRealLotFromIqcAssociation`、`#autoCreateFromIqc_withoutResolvableLot_keepsNullAndDoesNotFakeLot` |
| MES-NCR-02 | 报废/让步分支的批次动作失败由"`catch`+`log.warn` 后仍 CAS 到 `CLOSED`"改为**抛 `ServiceException` 回滚**（与返工分支一致）：处置结果与批次动作同事务一致，失败时 NCR 停在 `DISPOSITION` 可重试 | `NcrOrderServiceImpl.java:598-660`（`handleScrapDisposition`/`handleAcceptDisposition`） | 否 | `NcrOrderServiceImplTest#dispose_scrap_batchActionFailure_rollsBackWithoutClosing`、`#dispose_accept_batchReleaseFailure_rollsBackWithoutClosing` |
| MES-X-01 | ①ERP 侧补**解冻**能力：`unfreezeByBatch(materialId,batchCode,reason)`、`unfreezeByReceive(receiveId,materialId,reason)`，条件更新 `frozen_flag` 1→0（仅当前冻结行，幂等，带 reason 日志）②接到真实业务动作：IQC 回写结论为 `QUALIFIED` 时（复检合格 / 让步接收后重新判定合格 / 批次放行）由 `ErpIqcCompletedConsumer` 调 `unfreezeByReceive` 解冻 ③冻结旁路修复：`freezeStockByReceiveBatches` 无批次号时回退按**批次ID**定位冻结，两者都没有时记 warn（不再静默返回 0 当作无事）④上架门禁去掉 `noticeId != null` 前置：无到货通知时按**收货单头 `iqc_status`** 校验（`iqc_id` 已回写但结论未回写同样拦截），收货单头新增 `iqc_status` 列并由 IQC 回写消费端写入 | `IWmMaterialStockService.java`、`WmMaterialStockServiceImpl.java:196-300`、`ErpIqcCompletedConsumer.java:79-120`、`WmMaterialReceiveCrudServiceImpl.java:359-400`、`WmMaterialReceive.java:37-40` | 是（`wm_material_receive` 加 `iqc_status` + 存量回填） | `WmMaterialStockServiceImplTest#unfreezeByBatch_writesFrozenFlagBackToZero`（断言 `frozen_flag` 写回 `0`）、`#unfreezeByReceive_usesBatchCodesFromReceiveLines`、`#freezeStockByReceiveBatches_fallsBackToBatchIdWhenNoBatchCode` |

**门禁同型位置排查（MES-QC-01 要求"其他入口一并收口"）**：全仓 `grep getOqcStatus` 只有 3 处业务读点——`SoSalesOrderHeaderServiceImpl:521`（本次已改白名单）、`ErpOqcCompletedConsumer:81`（**写**回写，非门禁）、`SoSalesOrderLineServiceImpl:104`（**写**回写）。出库/发料侧读取的是批次 QC（`PdaIssueServiceImpl` 判 `UNQUALIFIED`）与 `frozen_flag`，不读 OQC 状态，故无第二个 OQC 门禁入口；但需注意 PDA 发料门禁同样是"仅阻断 UNQUALIFIED"口径（属 ERP/App 侧，非本批次所有权，建议主代理另派）。

## 2. 未修复 / 部分修复项（含理由与建议）

1. **MES-X-01 的"MES NCR 让步接收 → ERP 解冻"跨模块直连未打通**：MES 侧 `ACCEPT_AS_IS` 只解 MES `lt_lot` 隔离，ERP `frozen_flag` 的解冻需要新增 MES→ERP MQ 通道（`RoutingKeyConstants`/`QueueConstants` 属 `ruoyi-common-mq`，不在本批次文件所有权内）。本次已把 ERP 解冻能力做成服务 API 并接到"IQC 回写 QUALIFIED"这一真实入口（复检合格/让步后重新判定合格即解冻）；建议主代理在 `ruoyi-common-mq` 增加 `erp.stock.unfreeze` 通道 + ERP 消费者后，由 NCR 处置分支直接发消息。
2. **`QcInspectTemplateVo` 未补 `processId`/`customerId`**：模板查询接口暂不返回这两个新维度字段（前端模板表单亦未加），写入链路已通。属 `plus-ui` + VO 的后续工作。
3. **IPQC/OQC 的 `inspectWithDetails` 未做"缺项提交拒绝"**（审计 MES-QC-05）：该修复属 P1 批次，本次仅对齐 IQC 的现有行为，避免扩大 diff。
4. **`autoCreateFromExit`（工序出口）仍无法关联批次**：该入口没有物料/批次键，本次按任务要求改为显式 `log.warn("未关联批次")`；彻底解决需给 `lt_lot` 增加 `card_id`/`step_record_id` 维度（DDL + 批次创建链路改造）。
5. **`resolveLot` 的"唯一待检批次"自动关联是保守策略**：多候选时不做猜测（宁可 warn 也不把报废/放行作用到错误批次）。若业务要求按收货单精确定位，需要 ERP→MES 传递收货行批次号（`ReceiveApprovedMessage` 目前无 `batchCode` 字段，属 `ruoyi-common-mq`）。

## 3. 主脚本合并清单（目标文件 + 锚点 + 待插入行）

本次 DDL 全部写在 `script/sql/update/2026-09-20-mes-quality.sql`（幂等），**无需**修改 `ry-mes.sql`/`ry-erp.sql` 主脚本。若主代理决定并入主脚本，按下列锚点串行合并：

**目标文件 `script/sql/ry-mes.sql`（`qc_inspect_template` 建表段，锚点 = 已存在的整行）**
```sql
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称(冗余)',
```
插入行：
```sql
  `process_id` bigint DEFAULT NULL COMMENT '工序ID(IPQC维度,空=不限工序)',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID(客户专属模板维度,空=不限客户)',
```

**目标文件 `script/sql/ry-erp.sql`（`wm_material_receive` 建表段，锚点 = 已存在的整行）**
```sql
  `iqc_code` varchar(64) DEFAULT NULL COMMENT '质检单编号',
```
插入行：
```sql
  `iqc_status` varchar(20) DEFAULT NULL COMMENT '来料检验结论: QUALIFIED/UNQUALIFIED(无到货通知时上架门禁依据)',
```

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更 | 说明 |
|---|---|---|---|---|
| 2026-09-20 | ry-mes | `qc_inspect_template` | ADD `process_id` bigint NULL、`customer_id` bigint NULL；ADD INDEX `idx_qc_tpl_match(template_type,status,material_id,process_id,customer_id)` | 检验模板支持工序/客户维度匹配（MES-QC-02） |
| 2026-09-20 | ry-erp | `wm_material_receive` | ADD `iqc_status` varchar(20) NULL | 无到货通知时上架门禁依据 + 解冻联动（MES-X-01）；含存量按到货通知行回填 |

脚本：`script/sql/update/2026-09-20-mes-quality.sql`（幂等，可重复执行；无需清 Redis 缓存）。

## 5. 验证记录

命令（PowerShell，注意 `-D` 参数加引号）：
```
mvn -pl ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-erp -am test -DskipTests=false
    "-Dtest=QcIpqcServiceImplTest,QcOqcServiceImplTest,NcrOrderServiceImplTest,MesMqSendServiceTest,WmMaterialStockServiceImplTest,SoSalesOrderHeaderServiceImplTest"
    "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"
```
结果：见本节末尾"实际执行结果"。

新增/修改测试：
- 新增 `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/common/mq/MesMqSendServiceTest.java`（1 例）
- 新增 `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmMaterialStockServiceImplTest.java`（3 例）
- 追加 `NcrOrderServiceImplTest`（4 例：批次关联/无批次不伪造/报废回滚/让步回滚）
- 追加 `SoSalesOrderHeaderServiceImplTest`（2 例：OQC NULL 拦截、OQC PENDING 拦截）
- 追加 `QcIpqcServiceImplTest`（1 处断言：IPQC 建单生成明细）、`QcOqcServiceImplTest`（1 处断言：OQC 建单生成明细）

### 实际执行结果

**运行 1（mes + erp 六个测试类，`BUILD SUCCESS`）**
```
ruoyi-erp : SoSalesOrderHeaderServiceImplTest   Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
ruoyi-mes : MesMqSendServiceTest                Tests run:  1, Failures: 0, Errors: 0
ruoyi-mes : NcrOrderServiceImplTest             Tests run: 27, Failures: 0, Errors: 0, Skipped: 2（2 个为既有 @Disabled）
ruoyi-mes : QcIpqcServiceImplTest               Tests run: 12, Failures: 0, Errors: 0
ruoyi-mes : QcOqcServiceImplTest                Tests run: 10, Failures: 0, Errors: 0
ruoyi-mes 模块合计: Tests run: 50, Failures: 0, Errors: 0, Skipped: 2
```

**运行 2（erp 模块单独跑新增解冻测试，`BUILD SUCCESS`）**
```
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false "-Dtest=WmMaterialStockServiceImplTest" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"
ruoyi-erp : WmMaterialStockServiceImplTest      Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

**踩到的两个测试坑（供后续批次参考）**：
1. `ruoyi-erp` 继承根 pom 的 surefire `<groups>${profiles.active}</groups>`（本机为 `dev`），**新增 erp 测试类必须加 `@Tag("dev")`**，否则 `Tests run: 0` 静默不执行（`ruoyi-mes` 的 pom 已覆盖 groups，故 mes 侧无需 tag）。
2. `@InjectMocks` **不会注入 `ServiceImpl` 继承来的 `baseMapper`**（本项目 MyBatis-Plus 版本下该类没有可直接反射的 `baseMapper` 字段），`WmMaterialStockServiceImplTest` 改为按类型沿类层次查找字段并反射赋值；同类 ServiceImpl 单测（如 `WmBatchServiceImplTest`）可复用该写法。
3. `-am` 会把依赖模块一并纳入 reactor，未匹配到 `-Dtest` 的模块会直接 FAILURE：必须加 `-Dsurefire.failIfNoSpecifiedTests=false`。

## 6. 风险与回滚说明

| 改动 | 改错会怎样 | 回退方式 |
|---|---|---|
| OQC 白名单门禁 | 未检验/回写延迟的销售订单行无法履行（业务会被"卡住"而不是"放行"）——这是刻意的失败方向；若需放行必须走质量让步审批 | 把 `SoSalesOrderHeaderServiceImpl` 门禁条件改回 `"UNQUALIFIED".equals(...)` |
| 质检回写去 `@Async` | 回写失败会出现在调用线程（提交后异常向上抛/记录 ERROR）；RabbitMQ 长时间不可用时会在业务线程内产生等待 | 恢复三个方法上的 `@Async`（可靠性仍靠 `mq_message_record` 补偿） |
| IPQC/OQC 生成明细 | 已有 IPQC/OQC 检验单不会补生成明细（`generateDetails` 幂等，仅新建单生效）；模板未配置时明细为 0，检验单保持 PENDING | 去掉 `insertByBo` 中的 `generateDetails` 调用；新增列可保留（可空，不影响旧逻辑） |
| NCR 批次关联 | 自动关联仅在"唯一待检批次"时发生；若关联到错误批次，报废/放行会作用到该批次 | 回退 `resolveLot` 调用（传 null）+ 恢复 `handleScrap/Accept` 的 catch |
| NCR 批次动作抛异常 | 批次状态异常时 NCR 处置会失败并停在 `DISPOSITION`（需人工处理批次后重试），不再"单据关闭实物未动" | 恢复原 `catch { log.warn }` |
| ERP 解冻 | 误解冻会让冻结库存重新可发料：条件为 `frozen_flag=1 → 0` 且仅在 IQC 回写 `QUALIFIED` 时触发，风险窗口小；如需更严可要求二次确认/权限 | 删除 `ErpIqcCompletedConsumer` 第 5 步解冻调用（解冻 API 保留） |
| 上架门禁无到货通知分支 | 无到货通知但已有 IQC 的收货单在结论非 QUALIFIED 时无法上架；免检（无 IQC）不受影响 | 恢复 `else if` 分支前的判断（仅按到货通知行校验） |

## 7. 文件所有权说明（并发安全）

本批次按要求改了下列**所有权外但任务明确指定**的文件，请主代理在合并时确认无其他代理同时改动：
`ruoyi-modules/ruoyi-erp/.../so/service/impl/SoSalesOrderHeaderServiceImpl.java`（任务第 1 条明确指定）、
`ruoyi-modules/ruoyi-mes/.../common/mq/MesMqSendService.java`（任务第 1 条"回写链路去 @Async"明确指定）、
`ruoyi-modules/ruoyi-erp/.../wm/domain/WmMaterialReceive.java` 与 `.../mq/consumer/ErpIqcCompletedConsumer.java`（解冻/旁路所需的字段与消费端，属"解冻相关 service/consumer"范围内）。
未触碰 `wm/controller/**`、`script/sql/ry-*.sql`、`application*.yml`、`plus-ui/**`、`ruoyi-iot/**`。
