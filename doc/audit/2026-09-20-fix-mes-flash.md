# MES 刷写/测试/标签域（B-flash 批次）修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-mes-flash.md` 的 MES-FLASH-01/02/03/09/10/11、MES-TEST-01/02/03、MES-LABEL-01 | 分支: `feature/erp-mes-iot-audit-fix`
> 修复规范: `doc/audit/2026-09-20-修复执行规范.md`

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **MES-FLASH-01** (P0) | 删除 `application-prod.yml` 中的字面量密钥，改为**不含默认值**的环境变量占位 `api-key: ${MES_API_KEY:}`；生产未注入 `MES_API_KEY` 时 `mes.api-key` 为空 → `FlashReportController.checkApiKey` 的 fail-closed 分支真正可达，`/mes/flash/report/**`（flash/auto、test/auto、station/pending）**全部拒绝** | `ruoyi-admin/src/main/resources/application-prod.yml:1-12` | 否 | `FlashReportControllerTest.blankApiKeyRejectsReportFailClosed`、`blankApiKeyRejectsTestReportFailClosed`、`mismatchedApiKeyRejectsReport` |
| **MES-FLASH-11** (P1) | `mes.flash-step-gate: true`（无任何代码读取）改为代码真实读取的 `mes.flash.gate.mode: FORCE`，并在注释中写明 FORCE/WARN/关闭三态与键名契约 | `application-prod.yml:8-12` | 否 | 不适用（纯配置契约，见 §5 说明） |
| **MES-FLASH-03** (P0) | **采用方案①（语义统一）**：池类 Provider `resolveContent` 返回的 `contentRefId` 改为「工序配置的 contentRefId」（`ctx.getContentRefId()`），工序未配置时退回本次分配的资源项ID；门禁侧 `.eq(contentRefId)` 补 **null 守卫**并加 `flashTypeCode` 收敛（未配置即按既有约定退化为"该 SN 该类型最新一条"，且不允许 firmware 的 PASS 冒充 mac/sn 绑定） | `MacPoolFlashProvider.java:42-52`、`SnPoolFlashProvider.java:42-51`、`ProFlowCardServiceImpl.java:382-397`、`script/sql/update/2026-09-20-mes-flash.sql` | 是（仅列注释，见 §4） | `MacPoolFlashProviderTest.resolveContent_storesStepConfiguredContentRefIdNotItemId`、`resolveContent_fallsBackToItemIdWhenStepHasNoContentRef`、`FlashRecordServiceImplTest.deviceChannelDoesNotTrustClientOperatorAndKeepsStepContentRefId` |
| **MES-FLASH-02** (P0) | 池类 `verify` 不再丢弃 `actual.contentPayload`：解析设备上报的实刷值（`mac`/`sn`）**落库为 `content_payload`**，与池分配值（期望值，保留在 `content_ref_id` 指向的资源项）比对，不一致 → `pass(false)`；上报内容缺字段 → fail-closed；旧客户端未上报 → 退回池分配值。另修 `FlashRecordServiceImpl` 落库处 `verify.payload` 为空时回退 `expected.payload` | `MacPoolFlashProvider.java:54-80`、`SnPoolFlashProvider.java:53-79`、新增 `PoolFlashPayload.java`、`FlashRecordServiceImpl.java:564` | 否 | `MacPoolFlashProviderTest.verify_recordsActualBurnedValue`、`verify_failsWhenActualDiffersFromAllocatedValue`、`verify_failsClosedWhenActualPayloadLacksMacField`、`verify_fallsBackToAllocatedValueWhenDeviceReportsNothing` |
| **MES-TEST-01** (P0) | AUTO 上报判定前校验**必测项全覆盖**：`standards.itemCode - 上报项编码` 非空即抛 `ServiceException("测试上报缺少必测项…")`，且在**任何落库之前**抛出（该 SN 不落行、不判 PASS） | `TestRecordServiceImpl.java:355-369` | 否 | `TestRecordServiceImplTest.autoReport_missingMandatoryItemIsRejectedAndNothingInserted`、`autoReport_allMandatoryItemsCoveredIsAccepted` |
| **MES-TEST-02** (P1) | ENUM 判定由 fail-open 改 **fail-closed**：`passEnum` 为空 → `FAIL`；`passEnum` 非法 JSON（解析异常）→ `FAIL`；两条路径均 `log.error` 留痕。选 `FAIL` 而非 NEED_REVIEW 的理由：本模块结果域只有 `PASS/FAIL`（`ITestRecordService.RESULT_PASS/RESULT_FAIL`），无 `NEED_REVIEW` 语义，新增枚举值会污染既有前端/门禁口径 | `TestRecordServiceImpl.java:465-486` | 否 | `autoReport_enumWithUnparsablePassEnumJudgesFail`、`autoReport_enumWithBlankPassEnumJudgesFail`、`autoReport_enumWithValidPassEnumStillPasses` |
| **MES-TEST-03** (P1) | 已判定测试行不可反复覆盖：① 读取后先校验 `result` 非空即拒绝（带测试单号/项编码的可操作提示）；② 写入改为 **CAS 条件更新**（`update … where id=? and (result='' or result is null)`），影响 0 行抛"已被判定(并发重复提交)"。重测须重新开单保留历史行 | `TestRecordServiceImpl.java:198-236` | 否 | `reportRows_rejectsAlreadyJudgedRow`、`reportRows_casConflictIsRejected`、`reportRows_judgesDraftRowViaConditionalUpdate` |
| **MES-LABEL-01** (P0) | ZPL 分支补字段值转义：新增 `escapeZpl`（剥离 `^`、`~` 指令前缀与 CR/LF/TAB/C0 控制字符）并应用于 text/barcode/qrcode 三类打印项；对照的 TSPL `escapeTspl` 一并先过 `sanitize`（原实现只处理 `\` 与 `"`，换行可把一条指令拆成两条） | `LabelRenderService.java:113-138`、`escapeTspl/escapeZpl/sanitize`（`:160-190`） | 否 | `LabelRenderServiceTest.zpl_stripsCaretInjectionInValues`、`zpl_stripsTildeResetInjectionInBarcodeAndQrcode`、`zpl_stripsNewlinesInValues`、`tspl_stripsNewlinesInValues` |
| **MES-FLASH-09** (P1) | 免登录设备通道**不信任**客户端传入的 `operatorId/operatorName`（测试侧 `testerId/testerName`）：`operator_id/tester_id` 一律留空，客户端工号/姓名仅以 `设备上报:xxx(未验证)` 形式落 `operator_name/tester_name`（明确标注来源，与登录态可信身份区分）；登录态路径不变 | `FlashRecordServiceImpl.applyOperator`（`:611-638`）、`TestRecordServiceImpl.reportTestAuto`（`:394-402`） | 否 | `FlashRecordServiceImplTest.deviceChannelDoesNotTrustClientOperatorAndKeepsStepContentRefId`、`TestRecordServiceImplTest.autoReport_doesNotTrustClientOperatorIdentity` |
| **MES-FLASH-10** (P1) | 设备上报不再硬编码租户 `000000`：新增 `MesReportTenantResolver`（`@DS("mes")`）按 `equipmentId/equipmentCode` 反查 `eq_equipment.tenant_id`、回退按 `snCode` 反查 `pro_sn.tenant_id`（查询走 `TenantHelper.ignore` 忽略租户条件，否则免登录线程永远查不到）；控制器改为 `TenantHelper.dynamic(resolvedTenant, …)`；解析不到或整批跨租户 → 抛异常拒绝（fail-closed，不回落任何硬编码租户） | 新增 `MesReportTenantResolver.java`、`FlashReportController.java:52-57,62-92` | 否 | `FlashReportControllerTest.validApiKeyResolvesDeviceTenantAndReports` |

### 方案选择理由（MES-FLASH-03）

审计给出两个方案，本批次选**方案①「Provider 落库时写工序配置的 contentRefId」**：

1. **门禁口径天然一致**：`ProFlowCardServiceImpl.checkFlashStepGate` 与 `FlashRecordServiceImpl.selectPendingTasks/selectStationPending` 三处都是"按工序绑定的 `contentRefId` 反查记录"，方案①让写入侧与三处读取侧同口径，不需要在 `pro` 包（非本批次所有权）做池/资源项解析；方案②要让门禁感知资源池表与分配关系，改动面与耦合都更大。
2. **追溯不丢**：实刷值落 `content_payload`（MES-FLASH-02），分配关系仍在 `mes_resource_item.bound_sn_id → resource_value`，二者可交叉核对。
3. **不需要新列**：避免了给 `mes_flash_record` 增列带来的发布期 DDL 与回滚风险（审计建议的 `resource_item_id` 新列列为后续可选项，见 §2）。
4. 资源项ID在"工序未配置 contentRefId"时仍会落库（仅留痕，不影响门禁按 `flashTypeCode` 匹配）。

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 说明与建议 |
|---|---|---|
| MES-FLASH-04（retryCount 客户端自报、无上限、PASS 后可被 FAIL 打回待刷） | 未修（不在本批次） | 建议后续：服务端按 `(snCode, flashTypeCode, contentRefId)` 统计条数写 `retry_count`，并对已 PASS 记录再报 FAIL 要求 `failReason` + 审计日志 |
| MES-FLASH-05（`status=DISABLED`/`gate_enabled`/`verify_strategy`/`source_provider` 死列） | 未修 | 建议后续在 `buildValidatedRecord` 的类型查询加 `.eq(MesFlashType::getStatus,"ENABLED")`，门禁按绑定项 `gate_enabled` 过滤 |
| MES-FLASH-06（池只分配不回收、`USED` 死状态、池选择无 ORDER BY） | 未修 | 本次仅把池类 `content_ref_id` 语义修正；**注意**：`MacPoolFlashProvider.resolvePoolId` 仍取"首个启用 MAC 池"，若工序配置的 `contentRefId` 指向另一个池，分配仍可能来自首个池（号码段错配），建议后续让 `resolveContent` 优先用 `ctx.contentRefId` 作为 poolId 并补 `ORDER BY` |
| MES-FLASH-07/08（EXTERNAL 池未实现、外部 REST 无重试/SSRF 可被 302 绕过） | 未修 | 见审计建议；`ExternalRestFlashProvider` 需 `followRedirect(false)` + 逐跳校验 + 重试 + 结构校验 |
| MES-TEST-04/05、MES-LABEL-02/03、MES-STATION-01、§3 全部 P2 | 未修 | 不在本批次范围 |
| 存量池类刷写记录（`content_ref_id` = 资源项ID） | **需运维/主代理决策** | 修复后门禁按工序配置值反查，**历史池类记录仍匹配不到**（这些工单在修复前本就卡死）。排查：`SELECT r.flash_record_id,r.sn_code,r.content_ref_id,i.pool_id FROM mes_flash_record r JOIN mes_resource_item i ON i.item_id=r.content_ref_id WHERE r.content_ref_type IN ('MAC_POOL','SN_POOL');` 处理建议：重刷，或按 `pool_id` 做一次性订正（需先确认工序 `step_flash_config.contentRefId` 语义是 poolId 还是资源项ID，避免订正成错误值） |
| `mes_flash_record` 新增 `resource_item_id` 列 | 未做（可选） | 若后续需要"记录里直接可查分配到的资源项"，再补 DDL + 实体字段；当前经 `bound_sn_id` 可反查 |
| `mes_flash_record.content_ref_id` 列注释 | 已修（见 §3/§4） | 原注释"池=mes_resource_item_id"与新语义冲突，已提供增量脚本 |

## 3. 主脚本合并清单

> 目标文件 `script/sql/ry-mes.sql` 属主代理串行合并区，本批次未直接编辑；等价语句已写入 `script/sql/update/2026-09-20-mes-flash.sql`。

**目标文件**：`script/sql/ry-mes.sql`

**锚点（已存在的整行文本，第 738 行）**：
```sql
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)',
```

**要替换为的行**：
```sql
  `content_ref_id` bigint(20) NULL DEFAULT NULL COMMENT '内容源记录ID(固件=mes_firmware_id; 池类=工序配置的 contentRefId, 与完工门禁比对口径一致; 工序未配置时落本次分配的资源项ID)',
```

（该替换只改列注释，不改类型/索引/数据，可安全重复执行。）

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更 | 类型 | 幂等 | 脚本 |
|---|---|---|---|---|---|---|
| 2026-09-20 | `ry-mes` | `mes_flash_record` | `content_ref_id` 列注释修正为"固件=mes_firmware_id; 池类=工序配置的 contentRefId, 与完工门禁比对口径一致; 工序未配置时落本次分配的资源项ID"（配合 MES-FLASH-03 语义统一） | DDL（COMMENT，无数据/结构影响） | 是（`MODIFY COLUMN` 重跑结果一致） | `script/sql/update/2026-09-20-mes-flash.sql` |

- 影响库：`ry-mes`；无需清 Redis 缓存；执行前提：无；回滚：把 COMMENT 改回原文即可。
- 本批次**无新增列/索引/表**，因此不涉及唯一约束前置检查。

## 5. 验证记录

**测试命令（照抄修复规范 §3.4，未加 `-P`，即默认 profile）**：

```powershell
mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false "-Dtest=LabelRenderServiceTest,TestRecordServiceImplTest,MacPoolFlashProviderTest,FlashReportControllerTest,FlashRecordServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**结果（最终一次，2026-09-20 20:4x）**：

```
[INFO] Running com.nexus.mes.flash.controller.FlashReportControllerTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nexus.mes.flash.provider.MacPoolFlashProviderTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nexus.mes.flash.service.impl.FlashRecordServiceImplTest
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nexus.mes.flash.service.impl.TestRecordServiceImplTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nexus.mes.label.service.LabelRenderServiceTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **新增测试类 3 个**：`FlashReportControllerTest`(4)、`MacPoolFlashProviderTest`(6)、`FlashRecordServiceImplTest`(1)、`TestRecordServiceImplTest`(10)。
- **修改既有测试类 1 个**：`LabelRenderServiceTest` 追加 4 个用例（14 = 原 10 + 新 4）。
- **`@Tag` 说明（重要）**：根 pom `<groups>${profiles.active}</groups>`，而默认激活 profile 是 **dev**（`pom.xml:92-95` `activeByDefault`），**不是 local**。为兼顾"新测试类必须带 `@Tag("local")`"与实际执行，本批次新/改测试类均同时标注 `@Tag("local")` + `@Tag("dev")`（JUnit5 `@Tag` 可重复，surefire groups 命中任一即执行）。仅标 `local` 会在默认 profile 下被静默跳过——`LabelRenderServiceTest` 原无任何 `@Tag`，修复前该 10 个用例从未真正执行过，本次一并补标签使其纳入执行。
- **修复前会失败的核心断言**（可复现"修复前失败/修复后通过"）：
  - ZPL：`zpl_stripsCaretInjectionInValues`（原实现直接拼 `^FD`+value，会残留注入的 `^FO0,0`/`^A0N,60,60`，且 `^FS`/`^XZ` 计数 >1）。
  - 池类语义：`resolveContent_storesStepConfiguredContentRefIdNotItemId`（原实现返回 `item.getItemId()`，断言 `456` 必失败）。
  - 池类实刷值：`verify_recordsActualBurnedValue` / `verify_failsWhenActualDiffersFromAllocatedValue`（原实现无条件 `pass(true)` 且回填 `expected` 的 MAC）。
  - 必测项：`autoReport_missingMandatoryItemIsRejectedAndNothingInserted`（原实现只报 1 项即 `judged=1` 落 PASS）。
  - ENUM：`autoReport_enumWithUnparsablePassEnumJudgesFail` / `…BlankPassEnum…`（原实现两条路径都 `return RESULT_PASS`）。
  - 重复判定：`reportRows_rejectsAlreadyJudgedRow` / `reportRows_casConflictIsRejected`（原实现 `updateById` 直接覆盖）。
  - 冒名留痕：`deviceChannelDoesNotTrustClientOperator…` / `autoReport_doesNotTrustClientOperatorIdentity`（原实现把 BO 的 `operatorId/姓名` 直接落库）。
  - 租户：`validApiKeyResolvesDeviceTenantAndReports`（原实现硬编码 `TenantHelper.dynamic("000000", …)`，无法解析出 `000001`）。
- 命令末尾的 `[exit code: 1]` 来自 PowerShell `Select-String` 管道的退出码，非 Maven 失败；同一次运行输出中 `BUILD SUCCESS` 与 `Tests run: 35, Failures: 0, Errors: 0` 为准。

## 6. 风险与回滚说明

| 变更 | 改错会怎样 | 回滚 |
|---|---|---|
| `application-prod.yml` 密钥改 `${MES_API_KEY:}` | **生产未注入 `MES_API_KEY` 时 `/mes/flash/report/**` 全部拒绝**（产线设备上报 500/业务失败）。这是刻意 fail-closed：宁可上报失败，也不能让仓库内字面量密钥继续有效。发布前必须确认 `docker-compose-main.yml` 已注入 `MES_API_KEY`（该文件已含注入），且产线上位机请求头 `X-API-KEY` 使用同一值；建议在部署检查清单加"上报接口 401/失败即检查 MES_API_KEY" | 恢复为原字面量（不推荐，等于重新泄露可伪造 PASS 记录的凭证）；或临时把 `mes.api-key` 设为环境变量注入 |
| `mes.flash.gate.mode: FORCE` | 若误写成 `WARN`/其他值会关闭硬拦截（未刷写也能完工）。运维若要临时放行，改成 `WARN` 并接受告警；恢复硬拦截改回 `FORCE` | 改回原 `flash-step-gate: true`（无代码读取，等价于不生效） |
| 池类 `content_ref_id` 语义统一 | 修复后**新**记录门禁可匹配；**历史**池类记录（资源项ID）仍匹配不到（修复前本就卡死，不会更差）。若工序配置里填的其实是"资源项ID"而非池ID，则语义仍会错位——需按 §2 排查确认配置口径 | 回退 Provider 一行（`.contentRefId(item.getItemId())`）；无需数据回滚 |
| 池类 verify 比对实刷值 | 若上位机上报的 `contentPayload` 字段名不是 `mac`/`sn`（如 `MAC`/`value`），PASS 上报会被 fail-closed 拦成失败（上报 `failed` 明细里有明确原因）。发布前需与上位机确认载荷键名；旧客户端不报 `contentPayload` 不受影响 | 回退 Provider 的 `verify` 方法（恢复无条件 `pass(true)` + 回填期望值） |
| AUTO 必测项全覆盖 | 若某物料测试项配置了产线不测的项，该 SN 上报会整批失败（reason 明确列出缺失项）。需在测试项管理里把非必测项停用/删除，或按审计建议改为"落缺测行 + 告警" | 删除 `reportTestAuto` 中的 missing 校验块 |
| ENUM fail-closed | 存量 `pass_enum` 为空/脏的 ENUM 测试项，其判定会由 PASS 变 FAIL（这是修复目标）。建议先做数据体检：`SELECT test_item_id,item_code,pass_enum FROM mes_test_item WHERE data_type='ENUM' AND (pass_enum IS NULL OR pass_enum='');` | 把 `judge` 的两处 `RESULT_FAIL` 改回 `RESULT_PASS` |
| 已判定行 CAS 拒绝覆盖 | 前端"重填已判定行"会收到明确异常提示（如需重测请重新开单），原覆盖能力消失（这是修复目标）；若前端有依赖覆盖的流程需同步文案 | 删除前置校验并把 `update(…, wrapper)` 改回 `updateById` |
| ZPL 转义 | 字段值中的 `^`/`~` 会被剥离（如工单号含 `~` 会少字符），但不再可能注入指令；TSPL 的换行也会被剥离 | 回退 `escapeZpl`/`sanitize` 两处调用 |
| 设备通道操作人标注 | 记录里 `operator_name` 变为 `设备上报:E1001(未验证)`，按姓名精确匹配的历史报表/筛选会受影响（语义上更正确） | 回退 `applyOperator` 与 `reportTestAuto` 的赋值块 |
| 租户解析（FLASH-10） | 上报未带 `equipmentCode` 且 SN 不存在 → 直接拒绝（原因明确）；同一批次混租户 → 拒绝（需拆分重报）。比"静默写错租户"更安全 | 把控制器两处改回 `TenantHelper.dynamic("000000", …)` |
