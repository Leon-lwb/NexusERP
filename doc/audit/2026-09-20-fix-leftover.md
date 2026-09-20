# 跨模块收尾批次（MQ 通道 + PDA 门禁 + so/fin P2）修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-crosscut.md` §2（MQ-01）、`doc/audit/2026-09-20-audit-erp-pu-prd.md`（ERP-PRD-05）、`doc/audit/2026-09-20-audit-mes-qc.md`（MES-QC-01 / MES-X-01 的 PDA 侧门禁）、`doc/audit/2026-09-20-audit-erp-bd.md`（ERP-BD-09）、`doc/audit/2026-09-20-audit-erp-so-fin.md`（ERP-SO-01/02/04/08、FIN-FA-02/03/04、FIN-GL-03、FIN-ARAP-03）、`doc/audit/2026-09-20-fix-fin.md` §2、`doc/audit/2026-09-20-fix-mes-quality.md` §1（PDA 门禁遗留）
> 分支: `feature/erp-mes-iot-audit-fix` | 规范: `doc/audit/2026-09-20-修复执行规范.md`

## 1. MQ 通道结论表（MQ-01 逐通道定性）

**方法**：对 5 个常量 + `SUBCONTRACT_STOCK_QUEUE` 做全仓 `grep`（常量名、队列名字面量、对应 `RoutingKeyConstants` 路由键、对应 Message 类），并反向扫描 `@RabbitListener(queues = "字面量")`。

| 常量 | 队列名 | 生产者 | 消费者 | 结论 | 依据 |
|---|---|---|---|---|---|
| `STOCK_OUT_QUEUE` | `erp.stock.out.queue` | **无** | **无** | **死通道（预留）**，业务链路未断 | 全仓仅 `QueueConstants:10`（声明）与 `RabbitMqInitializer:48`（declare）两处引用；路由键 `RoutingKeyConstants.STOCK_OUT_ISSUE`(:12)、`STOCK_OUT_REWORK`(:13) 零业务引用；出库过账由 wm 策略（`SalesOutStrategy` 等）同步完成，不依赖事件 |
| `VENDOR_EVALUATION_QUEUE` | `erp.vendor.evaluation.queue` | **无** | **无** | **死通道（预留）** | 仅 `QueueConstants:13` + `RabbitMqInitializer:51`；`RoutingKeyConstants.VENDOR_EVALUATION_CALCULATE`(:16) 零引用；供应商评估在 pu/bd 侧同步计算落库 |
| `STOCK_ALERT_QUEUE` | `erp.stock.alert.queue` | **无** | **无** | **死通道（预留）** | 仅 `QueueConstants:14` + `RabbitMqInitializer:69`；`RoutingKeyConstants.STOCK_ALERT_LOW`(:17) 零引用；库存预警功能本身尚未实现 |
| `DELAY_QUEUE` | `erp.delay.queue` | **无** | **无** | **死通道（预留）** | 仅 `RabbitMqInitializer:107-111` 声明（TTL 中转）；`RoutingKeyConstants.DELAY_ORDER_TIMEOUT`(:35) 仅作为该队列的 `x-dead-letter-routing-key`，全仓无任何代码向 `DELAY_EXCHANGE` 投递 |
| `EINVOICE_ISSUE_RESULT_QUEUE` | `erp.einvoice.issue.result.queue` | **无** | **无** | **死通道（预留）**；设计中的回调链路未落地 | 仅 `QueueConstants:41` + `RabbitMqInitializer:79`；设计文档 `doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md:660` 规划 `EInvoiceResultConsumer`(ERP)，**代码中不存在该类**；亦无生产者（电票平台回调入口未实现） |
| `SUBCONTRACT_STOCK_QUEUE`（附带） | `erp.subcontract.stock.queue` | **无** | `SubcontractStockConsumer` | **死通道（无生产者）**，消费者保留为兼容监听 | `SubcontractStockMessage` 全仓仅被该消费者自身引用，无任何 `send/convertAndSend`；`RoutingKeyConstants.SUBCONTRACT_STOCK`(:18) 零引用 |

**反向检查（漏检排查）**：`grep '@RabbitListener(queues\s*=\s*"'` 全仓命中 **0** 处 —— 18 个 `@RabbitListener` 全部以 `QueueConstants.*` 常量引用队列，不存在字面量绕过造成的配对漏检。

**处理动作（不删除常量，明确注释为预留/废弃）**：删除常量需要同步改 `RabbitMqInitializer` 且会让既有 broker 拓扑失去声明来源（运维侧 `rabbitmqctl` 巡检脚本可能依赖队列存在），风险高于收益；且这 5 个通道是设计文档中已规划但未实现的接入点。因此按规范「明确注释为预留」处理：

- `ruoyi-common/.../constant/QueueConstants.java`：为 5 个常量 + `SUBCONTRACT_STOCK_QUEUE` 补 `【预留·未启用】/【已废弃·无生产者】` 注释，逐条写明"无生产者/无消费者"与权威替代路径。
- `ruoyi-common/.../config/RabbitMqInitializer.java`：在 `declareQueuesAndBindings()` 头部补通道定性说明，明确这些队列**不承载业务链路**。

> 结论：**MQ-01 不构成 P1 业务链路断点**（无"发了没人收"），危害等级从 P1 降为 **P2（文档/清理问题）**，已按 P2 处理。真正需要"补消费者"的前提是先有生产者（电票回调、库存预警、出库事件驱动），属功能开发而非缺陷修复，已列入 §2 未修复项。

## 2. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| MQ-01 | 6 个死通道逐条定性并在 `QueueConstants` / `RabbitMqInitializer` 注释为「预留/废弃」，写明无生产者/消费者与权威替代路径 | `QueueConstants.java:10,13,14,15,32,41`、`RabbitMqInitializer.java:46-49` | 否 | 无（注释/文档性改动，见 §1 结论表） |
| ERP-PRD-05 | `SubcontractStockConsumer` 的 `RECEIVE→COMPLETED` 分支**废弃**：不再改状态，仅告警留痕，口径统一到达量判定 `accumulateActualQty`（累计实收 ≥ 计划量才完工） | `erp/mq/consumer/SubcontractStockConsumer.java:40-70` | 否 | 见 §5（未单独单测，理由见 §2-6） |
| MES-X-01 / MES-QC-01 | PDA 发料批次 QC 门禁由「仅阻断 `UNQUALIFIED`」改为**白名单**：仅 `qc_status=QUALIFIED` 放行，`UNQUALIFIED/PENDING/NULL/批次未登记` 一律拦截，并返回可读原因 | `erp/wm/service/impl/PdaIssueServiceImpl.java:49-52,146-153,178-206` | 否 | `PdaIssueServiceImplTest` 6 例 |
| ERP-BD-09 | `bd_client_credit` 查询不再用 `selectOne`（多行抛 `TooManyResultsException` → 销售订单确认 500），改为按 `credit_id` 倒序取最新一行 + 重复行告警 | `erp/so/service/impl/CreditCheckServiceImpl.java:45-57` | 否（唯一键已由 `2026-09-20-uk-tenant-dimension.sql` 提供） | `CreditCheckServiceImplTest` 4 例（含 2 例） |
| ERP-SO-08 | `getUsedAmount` 由「全量载入内存循环累加」改为数据库端 `COALESCE(SUM(amount),0)` 聚合（MP 包装器，多租户插件自动补 `tenant_id`） | `CreditCheckServiceImpl.java:150-170` | 否 | `CreditCheckServiceImplTest#getUsedAmountReadsDatabaseSum` / `#getUsedAmountReturnsZeroWhenNoRows` |
| ERP-SO-02 | `updateOqcStatus` 不再按 `(so_id, material_id)` 批量改写同物料多行：先确定唯一目标行（未取消行中 `line_id` 最小者）再按 `line_id` 精确更新，多行命中告警；新增 `updateOqcStatusByLineId` 行级原语 | `erp/so/service/impl/SoSalesOrderLineServiceImpl.java:99-140`、`ISoSalesOrderLineService.java:88-99`、`erp/mq/consumer/ErpOqcCompletedConsumer.java:20-27` | 否 | `SoSalesOrderLineServiceImplTest` 3 例 |
| ERP-SO-01 | 履行/关闭/取消释放库存侧预留后，同步清零 `so_sales_order_line.reserved_qty`（行字段不再单向失真） | `erp/so/service/impl/SoSalesOrderHeaderServiceImpl.java:537-540,624-627,649-652`、新增 `clearLineReservedQty`（`buildQueryWrapper` 之前） | 否 | 见 §5（未单独单测，理由见 §2-6） |
| ERP-SO-04 | 报价转单改为**状态 CAS**：`UPDATE so_quotation SET status='CONVERTED' WHERE quotation_id=? AND status='CONFIRMED'`，影响 0 行即抛异常并回滚（连同已插入的销售订单）→ 无需新增 `source_quotation_id` 唯一键即获得转单幂等 | `erp/so/service/impl/SoQuotationServiceImpl.java:255-266` | **否**（原方案需 DDL，已用 CAS 替代） | 见 §5（未单独单测，理由见 §2-6） |
| FIN-FA-02 | 资产处置凭证：只要存在处置价款（`price > 0`）就生成银行存款借方行，修复报废(SCRAP)带残值收入时借贷不平衡导致 `createAndPost` 抛异常 | `erp/fin/fa/service/impl/FaDisposalServiceImpl.java:109-117` | 否 | 见 §5（未单独单测，理由见 §2-6） |
| FIN-FA-03 | 处置凭证 `voucherSource` 由误标的 `DEPRECIATION` 改为新增常量 `SOURCE_FA_DISPOSAL`；`sourceId` 与 `fa_disposal.disposal_id` 一致，使「同来源+同 sourceId」幂等键稳定可复算 | `FaDisposalServiceImpl.java:127-135`、`erp/fin/gl/constant/GlConstants.java:27-29` | 否 | 见 §5 |
| FIN-FA-04 | 年数总和法 `remaining = Math.max(0, life - (monthsInService - 1))`，并在方法末尾对 `amount.signum() < 0` 归零 → 超使用年限资产不再产生负折旧（冲回） | `erp/fin/fa/service/impl/FaDepreciationServiceImpl.java:207-215,220-229` | 否 | `FaDepreciationOverLifeTest` 3 例 |
| FIN-GL-03 | 手工凭证入口 `insertByBo` 强制 `voucherSource=MANUAL`、`sourceId=null`、`sourceNo=null`（忽略入参），杜绝手工凭证顶替真实自动过账 / 污染按来源归集口径 | `erp/fin/gl/service/impl/GlVoucherServiceImpl.java:101-108` | 否 | 见 §5 |
| FIN-ARAP-03 | 精度收敛方案 + 增量脚本（`fin_receipt.amount/settled_amount`、`fin_settlement.settle_amount` 由 `decimal(18,4)` → `decimal(18,2)`，含舍入与前置检查） | `script/sql/update/2026-09-20-leftover.sql` §1-§2 | **是**（见 §3/§4） | 无（DDL/数据收敛，需 DBA 在维护窗口执行） |

### 2.1 关键设计说明

- **ERP-PRD-05 选择"废弃该分支"而非"改走 `accumulateActualQty`"**：`accumulateActualQty` 位于 `PrdSubcontractClosureServiceImpl`（本批次**明确禁止修改**的文件，另一代理在改），且该队列经全仓 grep **无生产者**（`SubcontractStockMessage` 仅被消费者自身引用）。在没有生产者、且权威方法不可调用的前提下，把消费者改成"转发调用"既无法验证也不安全；因此改为**废弃该状态推进分支**（`RECEIVE` 仅告警、不改状态），由发料/收货主路径的达量判定权威维护状态。这与审计建议"删除 `SubcontractStockConsumer` 里的 RECEIVE→COMPLETED 分支"完全一致。
- **ERP-SO-04 用 CAS 替代唯一键**：审计建议新增 `source_quotation_id` 列 + `UNIQUE(source_quotation_id, tenant_id)`。但转单方法已是 `@Transactional`，把"报价单状态推进"从 `updateById` 改为条件更新即可让并发重复转单中**失败的一方整体回滚**（含其插入的销售订单与明细），达到同等幂等效果，且**零 DDL、零实体改动**（新增列需同步 Bo/VO/MapStruct 映射，diff 面积大）。若后续需要"报价单↔订单"的可追溯关联，再单独补列。
- **PDA 门禁白名单的失败语义**：`batchQcBlockReason` 对"批次未登记"也拦截。原实现把"无批次记录/`qc_status=NULL`"一律视为"无质检要求"放行，正是 MES-X-01 指出的旁路（冻结/拦截按批次号定位取不到行时静默通过）。门禁开关 `wm.issue.batch.qcGate` **缺省关闭**，故本次收紧不影响未开启该开关的环境；开启后如遇"批次确实无需质检"的场景，应先落 `wm_batch` 记录并回写 `qc_status=QUALIFIED`，而不是靠门禁静默放行。

## 3. 未修复 / 部分修复项（含理由与建议）

| 编号 | 结论 | 理由 / 建议 |
|---|---|---|
| MQ-01「补消费者」部分 | 未做（本轮按 P2 定性处理） | 5 个通道**连生产者都没有**，补消费者没有意义。要真正启用需先做功能开发：①电票平台回调入口 + `EInvoiceResultConsumer`（设计见 `2026-07-15-invoicing-ar-ap-redesign.md:660`）；②库存预警计算与通知；③出库事件驱动的成本结转。建议作为独立需求排期，而非本轮缺陷修复。 |
| FIN-GL-04（重估幂等键溢出） | 未修复 | `periodId*100+2` 对雪花 `period_id` 溢出，需改用"同期间同来源是否已有凭证"查询（`FinClosingServiceImpl`）。属 `fin/report` 所有权但改动涉及幂等语义重设计，本轮优先做了 GL-03/FA-02/03/04；建议下轮单独做并补"同期间重复重估只生成一张凭证"的用例。 |
| ERP-SO-05（价目表无 `del_flag`） | 未修复（纯 DDL + 实体注解） | 需给 `so_price_list`/`so_price_list_line` 补 `del_flag` 列 + 实体 `@TableLogic` + 存量脚本，属独立 DDL 批次；本批次增量脚本未包含以避免与其他代理的 DDL 批次冲突。 |
| ERP-SO-06/07/09 | 未修复 | SO-06 死字段需产品决策是否启用；SO-07 权限码已在权限种子批次（`fix-perm-seeds`）范围；SO-09 委外/调拨收口涉及 `so` 与 `wm` 两侧校验，改动面大于本批次预算。 |
| FIN-FA-02/03、FIN-GL-03、ERP-SO-01/04、ERP-PRD-05 的**单元测试** | 未补 | 见 §5「未补单测的原因」。改动本身已逐行人工复核（行构建/常量替换/条件更新），建议由主代理在全量回归中覆盖。 |
| PDA 门禁的"批次未登记即拦截" | 已实现，但需业务确认 | 见 §2.1。若生产存在"扫到的批次码确实不需要质检"的合法场景，需要提供 `wm_batch` 落库路径或给门禁加"免检物料白名单"，否则开关开启后会误拦。 |
| 前端 | 未修改（无所有权） | ①`PdaActionResultVo.fail` 的文案已含"待检/未回写""无质检记录(批次未登记)"，PDA 端无需改代码即可展示；②报价转单新增的并发失败提示"报价单已被并发转换为销售订单，请刷新后重试"需前端按普通业务异常展示（现有全局错误提示已覆盖）。 |

## 4. 主脚本合并清单（目标文件 + 锚点 + 待插入行）

> 本批次**未编辑**任何 `script/sql/ry-*.sql`。以下为 FIN-ARAP-03 精度收敛需要在主脚本同步的**列类型替换**（由主代理串行合并）。

### 4.1 `script/sql/ry-erp.sql`（3 处列类型 `decimal(18, 4)` → `decimal(18, 2)`）

| # | 锚点（已存在的整行文本，行号为当前文件） | 替换为 |
|---|---|---|
| 1 | `  \`amount\` decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '收付款金额',`（第 2924 行，`fin_receipt` 表内） | `  \`amount\` decimal(18, 2) NOT NULL DEFAULT 0.00 COMMENT '收付款金额',` |
| 2 | `  \`settled_amount\` decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '已核销金额',`（第 2925 行，`fin_receipt` 表内） | `  \`settled_amount\` decimal(18, 2) NOT NULL DEFAULT 0.00 COMMENT '已核销金额',` |
| 3 | `  \`settle_amount\` decimal(18, 4) NOT NULL COMMENT '核销金额',`（第 2962 行，`fin_settlement` 表内） | `  \`settle_amount\` decimal(18, 2) NOT NULL COMMENT '核销金额',` |

> 锚点上下文：`CREATE TABLE \`fin_receipt\`` 在第 2915 行、`CREATE TABLE \`fin_settlement\`` 在第 2953 行。
> **注意**：主脚本改的是"全新环境建库口径"，存量环境必须执行 `script/sql/update/2026-09-20-leftover.sql`（含先 `ROUND` 再 `MODIFY`，否则严格模式下截断报错）。

### 4.2 无需合并的其他主脚本

- `script/sql/ry-vue.sql`：本批次**无** `sys_menu`/权限码新增（未新增任何端点；`updateOqcStatusByLineId` 是服务层原语，无新 Controller 端点）。
- `script/sql/ry-mes.sql`：无变更。

## 5. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更类型 | 变更内容 | 脚本 |
|---|---|---|---|---|---|
| 2026-09-20 | ry-erp | `fin_receipt` | ALTER COLUMN（+ 存量舍入） | `amount`、`settled_amount` 由 `decimal(18,4)` 收敛为 `decimal(18,2)`，与凭证/余额口径一致（FIN-ARAP-03）；执行前先 `ROUND(...,2)` | `script/sql/update/2026-09-20-leftover.sql` §1 |
| 2026-09-20 | ry-erp | `fin_settlement` | ALTER COLUMN（+ 存量舍入） | `settle_amount` 由 `decimal(18,4)` 收敛为 `decimal(18,2)`（FIN-ARAP-03） | `script/sql/update/2026-09-20-leftover.sql` §2 |

> 无新增表/列/索引。脚本幂等（`ALTER` 前查 `information_schema.COLUMNS.NUMERIC_SCALE`，已是 2 位则执行 `DO 0`）；**不可逆**（被舍入的 2 位以下尾差无法恢复），脚本 §0 提供前置检查、§3 提供回滚语句。
> ERP-BD-09 所需的 `bd_client_credit.uk_client_tenant(client_id, tenant_id)` 唯一键已由 `script/sql/update/2026-09-20-uk-tenant-dimension.sql` 提供，本批次**不重复登记**。

## 6. 验证记录（命令 + 结果，含新增测试类与用例数）

命令（规范 §3.4 实测校正，`-am` 上游模块必须带 `failIfNoSpecifiedTests=false`；**注意 pwsh 下 `-Dtest=A,B,C` 必须整体加引号**，否则逗号被解析为数组运算符导致 `ParserError: 缺少参数`）：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false "-Dtest=CreditCheckServiceImplTest,SoSalesOrderLineServiceImplTest,FaDepreciationOverLifeTest,PdaIssueServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

新增测试类（均带 `@Tag("dev")` + `@Tag("local")`，与根 pom `groups=${profiles.active}` 一致）：

| 测试类 | 用例数 | 覆盖的修复 | 修复前会失败的断言 |
|---|---|---|---|
| `so/CreditCheckServiceImplTest`（新） | 4 | ERP-BD-09、ERP-SO-08 | `verify(bdClientCreditMapper, never()).selectOne(any())`（修复前走 `selectOne`，且多行时抛 `TooManyResultsException` 导致不落库）；`verify(creditUsageMapper, never()).selectList(any())`（修复前全量载入） |
| `so/SoSalesOrderLineServiceImplTest`（新） | 3 | ERP-SO-02 | 断言 `WHERE` 段含 `line_id` 且**不含** `material_id`（修复前按 `material_id` 批量更新） |
| `fin/fa/FaDepreciationOverLifeTest`（新） | 3 | FIN-FA-04 | 超使用年限（life=5、monthsInService=6）计提额为 `0` 而非负数；1..80 月遍历断言 `signum() >= 0`（修复前为负） |
| `wm/service/impl/PdaIssueServiceImplTest`（新） | 6 | MES-X-01/MES-QC-01 门禁白名单 | `PENDING` / `qc_status=NULL` / 批次未登记 三种情况 `ok=false`（修复前均放行，断言会失败）；`QUALIFIED` 仍放行 + 门禁开关关闭时不查批次 |

结果：见 §6.1（由执行结果回填）。

### 6.1 执行结果

```
[INFO] Running com.nexus.erp.fin.fa.FaDepreciationOverLifeTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in FaDepreciationOverLifeTest
[INFO] Running com.nexus.erp.so.CreditCheckServiceImplTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in CreditCheckServiceImplTest
[INFO] Running com.nexus.erp.so.SoSalesOrderLineServiceImplTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in SoSalesOrderLineServiceImplTest
[INFO] Running com.nexus.erp.wm.service.impl.PdaIssueServiceImplTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in PdaIssueServiceImplTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**合计 16 例全绿**（4 个新增测试类），reactor 34 个模块（含 `ruoyi-common-mq`）编译通过 —— 即 `QueueConstants` / `RabbitMqInitializer` 的注释改动与 `GlConstants` 新常量、`ISoSalesOrderLineService` 新方法均已通过编译。
> pwsh 下 `Select-String` 管道会把 stderr 合流并使 exit code 报 1，属管道假象（`fix-mes-pda.md` §5 已记录同现象），以 `BUILD SUCCESS` 与 `Tests run` 计数为准。第一次执行失败是**命令引号问题**（`-Dtest=A,B,C` 未加引号 → `ParserError: 缺少参数`），已改正并记录在 §6 命令说明中。
> 未跑全模块全量测试（按规范由主代理统一跑）。

### 6.2 未补单测的原因（逐条）

1. **`SubcontractStockConsumer`（ERP-PRD-05）**：`BaseMessageConsumer.processMessage` 依赖 Redis 幂等占位与 AMQP `Channel`，`mockStatic(RedisUtils)` 在本仓库单测环境实测不可用（见 `fix-mes-pda.md` §2-6）；本批次改动的核心是"RECEIVE 分支不再改状态"，可读性高、无分支扩散，故以人工复核 + 既有 `SubcontractStockConsumer` 编译回归覆盖。
2. **`FaDisposalServiceImpl`（FIN-FA-02/03）**：`dispose()` 内调用 `IdGeneratorUtil.nextLongId()`，该类静态初始化即 `SpringUtils.getBean(IdentifierGenerator.class)`，单测必须 `mockStatic(SpringUtil.class)` + `mockStatic(SpringUtils.class)` 并桩 `IdentifierGenerator`（`fix-fin.md` §5.1 已记录该坑）。本次改动为"行构建条件放宽 + 常量替换 + sourceId 取值来源"，风险集中在凭证行集合，已逐行核对借贷平衡。
3. **`GlVoucherServiceImpl.insertByBo`（FIN-GL-03）**：既有 `GlVoucherServiceImplTest` 只在"负数分录"用例中调用 `insertByBo`，该用例在源字段赋值前即抛异常，故**不受本次强制 `MANUAL` 影响**（已 grep 确认无其他 `insertByBo` 调用点）。新增断言语义（入参 source 被忽略）价值有限，故未追加。
4. **`SoQuotationServiceImpl.convertToSalesOrder`（ERP-SO-04）**：需 mock `AutoCodeUtil`（静态）+ `insertBatch` 链路，既有 `SoQuotationServiceImplTest` 已覆盖转换主路径；CAS 分支的失败路径（`statusRows==0` 抛异常）建议在下一轮与"报价单状态机"用例一并补。
5. **`SoSalesOrderHeaderServiceImpl.clearLineReservedQty`（ERP-SO-01）**：该类有 15+ 个注入依赖，既有 `SoSalesOrderHeaderServiceImplTest`（1416 行）已建立完整 mock 装配；为控制本批次 diff 与执行风险未改动该测试类，建议下轮在其中追加"close/cancel/fulfill 后 `so_sales_order_line.reserved_qty` 被清零"的断言。

## 7. 风险与回滚说明

**改动文件（回滚即还原以下文件；`2026-09-20-leftover.sql` 未执行前无数据影响）**：

| 文件 | 风险 | 回滚 |
|---|---|---|
| `QueueConstants.java`、`RabbitMqInitializer.java` | 仅注释，无行为变化 | 还原注释 |
| `erp/mq/consumer/SubcontractStockConsumer.java` | `RECEIVE` 不再置 `COMPLETED`。因该队列无生产者，**生产环境零影响**；若未来有人启用生产者，部分收货将不再误置完工（预期行为） | 还原 `RECEIVE` 分支 |
| `erp/wm/service/impl/PdaIssueServiceImpl.java` | 门禁开关 `wm.issue.batch.qcGate` **缺省关闭**，关闭时行为不变；开启后 `PENDING/NULL/批次未登记` 会新增拦截（可能误拦免检批次，见 §3 最后一行） | 还原 `batchQcBlockReason` 为 `isBatchUnqualified` |
| `erp/so/service/impl/CreditCheckServiceImpl.java` | 信用额度改为"取 `credit_id` 最大的一行"；若存量重复行中真正生效的是较早一行，会出现额度口径变化（并伴随 WARN 日志） | 还原为 `selectOne`（但会恢复 500 风险）；建议改为执行 `2026-09-20-uk-tenant-dimension.sql` 清理重复行 |
| `erp/so/service/impl/SoSalesOrderLineServiceImpl.java` | OQC 回写由"多行"收窄为"首行"，若业务确实依赖同物料多行同时回写，会看到"部分行 OQC 状态未更新"（并伴随 WARN） | 还原 `updateOqcStatus` 为按 `material_id` 批量更新 |
| `erp/so/service/impl/SoSalesOrderHeaderServiceImpl.java` | `clearLineReservedQty` 会把行 `reserved_qty` 置 0；行字段本就只是展示（权威在库存侧），不影响可用量计算 | 删除 3 处 `clearLineReservedQty(soId)` 调用与私有方法 |
| `erp/so/service/impl/SoQuotationServiceImpl.java` | 并发转单的失败方现在会收到业务异常（原先可能生成两张订单） | 还原为 `updateById` |
| `erp/fin/fa/service/impl/FaDisposalServiceImpl.java`、`GlConstants.java` | 报废带残值现在会生成银行借方行（凭证平衡）；`voucherSource=FA_DISPOSAL` 是**新来源值**，若有外部报表按来源硬编码枚举需同步（`gl_voucher.voucher_source` 为 varchar，无需 DDL） | 还原两处赋值 |
| `erp/fin/fa/service/impl/FaDepreciationServiceImpl.java` | 超年限资产计提额由负数变 0（不再冲回），若历史期间已产生负折旧，需按 §3 建议单独订正 | 还原 `Math.max(0, ...)` 与负额归零 |
| `erp/fin/gl/service/impl/GlVoucherServiceImpl.java` | 手工入口不再接受自带 `source/sourceId`；若存在外部客户端依赖该行为，会收到"字段被忽略"而非报错（本次选择静默强制而非拒绝，避免破坏既有新增流程） | 还原为"仅当为 null 时填 MANUAL" |
| `script/sql/update/2026-09-20-leftover.sql` | 执行后 `fin_receipt`/`fin_settlement` 金额精度降为 2 位，**2 位以下尾差不可恢复** | 未执行即无影响；已执行按脚本 §3 恢复列精度（尾差仍需业务确认） |
