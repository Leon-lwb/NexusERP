# MES 工装夹具（tg）与 eSOP（sop）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg`（29 文件）、`.../mes/sop`（28 文件），并联动核验 `pro`（进站门禁）、`plus-ui/src/api/mes/{tg,sop}`、`plus-ui/src/views/mes/{tg,sop}`、`script/sql/ry-mes.sql`、`script/sql/ry-vue.sql`（字典/菜单/种子）、`ruoyi-common-sign` | 覆盖文件数: 21（实际读过） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-TG-01 | P0 | 功能缺失 | 工装模块完全没有「校准」概念（无字段/无状态/无字典/无拦截），量具超期使用无任何系统约束 | `ry-mes.sql:2986`、`ITgToolingService.java:27` |
| MES-TG-02 | P0 | 功能缺失 | 保养/校准到期**不拦截**生产使用：可用性校验只看 `status`；且 `TOOLING_AVAILABLE` 门禁规则无种子数据（默认不启用） | `TgToolingServiceImpl.java:362`、`ProEntryCheckServiceImpl.java:168`、`ry-mes.sql:1899` |
| MES-TG-03 | P1 | 状态机 | 使用记录有独立 CRUD 端点，可凭空造 OUT 记录、可把 OUT 改成 RETURNED 而不累加寿命、可删未归还记录使工装永久卡死 IN_USE | `TgUsageRecordServiceImpl.java:51`、`:85`、`:92` |
| MES-TG-04 | P1 | 契约 | 保养记录可绕过状态机直接新增/修改/删除，与 `sys_menu` 声明「触发归零+状态机」不符 → 假保养记录 + 计划阈值不推进 | `TgMaintenanceRecordServiceImpl.java:24`、`ry-vue.sql:92531` |
| MES-TG-05 | P1 | 功能缺失 | 工装保养到期**无任何自动提醒**（全 MES 只有 dc/eq 有定时任务），仅靠人手动点页面按钮 | `MaintenanceDueTask.java:173`、`tooling/index.vue:596` |
| MES-SOP-01 | P1 | 权限 | 查阅登记端点用管理侧权限码 `mes:sop:viewlog:list`，工位终端 403 且前端静默吞掉 → 查阅记录与 `SOP_VIEW` 签名在真实工位场景全部不落库 | `SopViewLogController.java:56`、`viewer/index.vue:131`、`ry-vue.sql:92599` |
| MES-SOP-02 | P1 | 功能缺失 | SOP 是纯展示：进站只校验「绑定了已发布 SOP」，不校验操作人是否已阅/已签，无「操作前必须确认」门禁 | `ProEntryCheckServiceImpl.java:158`、`ProStepCheckRule.java:30` |
| MES-SOP-03 | P1 | 合规/数据错 | 查阅记录（含已生成的 `SOP_VIEW` 签名）可被 `DELETE` 静默删除，append-only 审计链与签名台账脱钩 | `SopViewLogController.java:75`、`SopViewLogServiceImpl.java:169` |
| MES-SOP-04 | P1 | 数据错/状态机 | `sop_version` 种子 54 行状态值为非法值 `PUBLISHED`（值域仅 PENDING/CURRENT/HISTORICAL）→ CURRENT 删除守卫失效、`doPublish` 找不到 PENDING | `ry-mes.sql:2783`、`ISopDocumentService.java:31`、`SopVersionServiceImpl.java:91` |
| MES-SOP-05 | P1 | 合规 | `SOP_VIEW` 逻辑签名的 `doc_hash` 恒为 null，签名不绑定 SOP 正文，替换 OSS 文件无法被验签发现 | `ESignatureServiceImpl.java:707`、`:786`、`SopViewLogServiceImpl.java:146` |
| MES-SOP-06 | P1 | 校验/契约 | 工序绑定接口不校验 SOP 状态，可把 DRAFT/ARCHIVED 的 SOP 绑定到工序（前端选择器过滤了、后端没有）→ 工位推送为空 | `SopProcessBindingServiceImpl.java:90`、`:161` |
| MES-TG-06 | P2 | 校验 | 保养计划可被直接改写 `next_maintain_*`，人为提前/推迟到期，绕过寿命双轨 | `TgMaintenancePlanServiceImpl.java:79` |
| MES-TG-07 | P2 | DDL | `tg_maintenance_plan` 无 (tooling_id, trigger_type) 唯一约束，同工装可建多条同类计划，到期列表重复 | `ry-mes.sql:2946` |
| MES-SOP-07 | P2 | 并发/DDL | `sop_version` 无 (sop_id, version_no) 唯一索引，版本号用 `selectCount` 先查后写（TOCTOU + 删版本后重号） | `ry-mes.sql:2775`、`SopDocumentServiceImpl.java:231` |
| MES-SOP-08 | P2 | 功能缺失 | 种子 57 条 SOP 的 `file_oss_id=0`，工位查看器无正文可显示；无离线缓存能力 | `viewer/index.vue:104` |
| MES-SOP-09 | P2 | 状态机 | 已归档（废止终态）SOP 仍允许编辑基础信息 | `SopDocumentServiceImpl.java:152` |

## 1. P0 详述

### MES-TG-01 工装「校准」能力整体缺失，超期校准无法拦截

- **证据**: `script/sql/ry-mes.sql:2986-3015`（`tg_tooling` 全部列，无任何 calibration 相关列）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/service/ITgToolingService.java:27-33`（状态常量仅 `IDLE`/`IN_USE`/`MAINTENANCE`/`SCRAPPED`）、`script/sql/ry-vue.sql:90732-90735`（`mes_tooling_status` 字典仅 4 值）
- **现状**: 工装台账字段只有 `total_life_count/total_life_hours/used_count/used_hours/last_maintain_time`；状态机注释（`TgToolingServiceImpl.java:44-47`）自述为「IDLE ⇄ IN_USE → MAINTENANCE → SCRAPPED」，不存在「校准中」状态；字典 `mes_tooling_type` 含 `GAUGE`（量具，`ry-vue.sql:90739`）却无校准周期/校准日期/下次校准日期任何承载字段。
- **触发路径**: 新建一条 `toolingType=GAUGE` 的工装 → 领用 → 归还 → 再次领用，全程无校准相关输入项与校验点。
- **后果**: 量具/检具的校准有效期在系统里**根本不存在**，超期使用既不告警也不拦截；ISO 9001 7.1.5.2 / IATF 16949 的测量设备溯源要求在 MES 侧无任何数据支撑，也无法在审计时出示校准证据。
- **修复建议**: ① DDL 新增 `calibration_cycle_days int`、`last_calibration_date date`、`next_calibration_date date`（存量行置 NULL 表示未纳入校准管理）；② `ITgToolingService` 增 `STATUS_CALIBRATING`，`mes_tooling_status` 字典补条目；③ 在 `checkToolingAvailable()` 内加 `next_calibration_date < today` → `usable=false, reason=校准已超期`；④ 领用 `checkout()` 前置同一校验。
- **影响面**: 需 DDL（`tg_tooling` + 字典 + 菜单按钮）；影响 `TgToolingController`（新增校准登记端点）、`TgToolingBo/Vo`、前端 `plus-ui/src/views/mes/tg/tooling/index.vue` 表单与列表、`ProEntryCheckServiceImpl` 复用同一校验；需补 `TgToolingServiceImplTest` 覆盖超期分支。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/pro/service/impl/ProEntryCheckServiceImplTest.java:158` 仅 mock 了 `checkToolingAvailable` 的返回，无校准用例（不存在该校验）。

### MES-TG-02 保养/校准到期不拦截生产使用，门禁规则默认未启用

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/service/impl/TgToolingServiceImpl.java:362-392`（`checkToolingAvailable` 只判 `SCRAPPED`/`MAINTENANCE`/`IN_USE`，其余一律 `usable=true`）、`:395-407`（`listMaintenanceDue` 是独立的只读方法，未被任何写路径/校验路径调用）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProEntryCheckServiceImpl.java:168-177`（进站校验直接调 `checkToolingAvailable`）、`script/sql/ry-mes.sql:1899-1902`（`pro_step_check_rule` 只有建表语句，**无任何 INSERT 种子**）
- **现状**: 工装到期判断逻辑 `isMaintenanceDue()`（`TgToolingServiceImpl.java:441-465`，含 BY_COUNT/BY_HOURS/PERIODIC 三种触发）实现完整，但只被 `listMaintenanceDue()` → `GET /mes/tg/tooling/due`（`TgToolingController.java:49-52`）这一条只读链路使用；进站门禁 `checkToolingAvailable` 完全不看它。
- **触发路径**: 建工装 + 建 `BY_COUNT/trigger_value=1000` 计划 → 反复领用归还至 `used_count=1500`（远超阈值）→ 带该 `toolingId` 调 `POST /mes/pro/flow-card/entry`：因 `status` 仍为 `IDLE`，校验返回 `usable=true`，正常进站生产。即使管理员把 `TOOLING_AVAILABLE` 规则配进 `pro_step_check_rule`，结果同样是放行。
- **后果**: 工装管理的核心价值（「超期未保养不得上机」）实际为零；超寿命/超保养周期工装持续生产，直接影响产品尺寸与模具寿命，且台账上看不出来（`used_count` 只是数字）。
- **修复建议**: ① `checkToolingAvailable()` 内调用 `isMaintenanceDue(vo, now)`，命中即 `usable=false, reason=保养已到期(阈值...)`；② `checkout()` 前置同一校验，禁止超期领用；③ 补 `pro_step_check_rule` 的 `TOOLING_AVAILABLE` 种子（`enabled='1'`，按工序配置），并在 `ProEntryCheckServiceImpl` 对「上下文无 toolingId」记录一条 WARN（现在 `:169-171` 静默跳过，等于门禁可被"不传参数"绕过）。
- **影响面**: 改 `TgToolingServiceImpl` 两个方法 + 新增 SQL 种子；影响 `ProEntryCheckServiceImpl`、前端进站扫码是否传 `toolingId`（`plus-ui/src/api/mes/pro/flowcardApi.ts:122`）、`mes/tg/tooling/index.vue`；需 DDL（仅种子 INSERT，无表结构变更）；需补测试。
- **测试现状**: `ProEntryCheckServiceImplTest.java:158` 只覆盖「usable=false → 产生 failure」，无「到期应判 false」的用例。

## 2. P1 详述

### MES-TG-03 使用记录 CRUD 绕过工装状态机

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/service/impl/TgUsageRecordServiceImpl.java:51-68`（`insertByBo` 直接插入，`status` 缺省 `OUT`，**不**改 `tg_tooling.status`）、`:85`（`updateByBo` 直接 `setStatus(bo.getStatus())`）、`:92-97`（`deleteByIds` 无任何守卫）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/controller/TgUsageRecordController.java:42-59`（`POST`/`PUT`/`DELETE` 三个写端点均在）、`TgToolingServiceImpl.java:215-222`（`doReturn` 依赖「存在 status=OUT 的记录」，找不到即抛「未找到领出记录,无法归还」）
- **触发路径（三种）**: ① `POST /mes/tg/usage-record`（`toolingId` 指向一台 `IDLE` 工装）→ 台账出现 OUT 记录但工装仍 `IDLE`，同一台工装可被重复领用；② `PUT /mes/tg/usage-record` 把 `status` 改成 `RETURNED` → 记录"已归还"但 `tg_tooling.used_count/used_hours` 未累加、状态仍 `IN_USE`；③ 领用后 `DELETE /mes/tg/usage-record/{usageId}` 删掉那条 OUT 记录 → 工装永远停在 `IN_USE`，`doReturn` 抛「未找到领出记录」而无法归还，只能靠改库恢复。
- **后果**: 台账寿命计数与实际使用脱钩（可少计使用次数 → 超寿命使用不被发现）；工装卡死 IN_USE 造成可用性丢失；追溯链可被随意编辑（与 `TgToolingServiceImpl.java:145-149` 的「孤儿守卫」意图相矛盾：守卫只保护工装不被删，却不保护使用记录本身）。
- **修复建议**: 把 `usage-record` 收敛为**只读**（保留 `list`/`get`，删除 `POST/PUT/DELETE` 端点与前端按钮，菜单 4193-4195 的 `mes:tg:usage:add/edit/remove` 停用）；若确需人工补录，改为新增受控端点 `POST /mes/tg/usage-record/manual` 并在同一事务内 CAS 推进工装状态 + 累加寿命。
- **影响面**: `TgUsageRecordController`、`ITgUsageRecordService`、前端 `plus-ui/src/api/mes/tg/usage-record.ts:55-61` 与 `plus-ui/src/views/mes/tg/usage-record/index.vue:208,264`（当前把 5 个 API 直接接进 CRUD 表格）；无 DDL；需补测试。
- **测试现状**: 无 tg 使用记录相关单测（`ruoyi-modules/ruoyi-mes/src/test` 下无 `TgUsage*`）。

### MES-TG-04 保养记录绕过归零与状态机

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/service/impl/TgMaintenanceRecordServiceImpl.java:24-25`（javadoc 自认「本接口仅做记录 CRUD，不触发工装归零/状态机」）、`:51-69`（`insertByBo` 直接采用客户端传入的 `usedCountBefore/After/usedHoursBefore/After/cost`）、`:73-89`、`:94-98`、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/controller/TgMaintenanceRecordController.java:42-59`、`script/sql/ry-vue.sql:92531`（菜单注释「新增保养记录(触发归零+状态机)」，与实现直接矛盾）
- **触发路径**: 保养记录页直接 `POST /mes/tg/maintenance-record`，传 `usedCountBefore=1500, usedCountAfter=0`，`tg_tooling.used_count` 仍为 1500、`status` 仍 `IDLE`、`last_maintain_time` 不更新、关联计划 `next_maintain_count` 不推进；反之也可 `PUT`/`DELETE` 篡改历史保养快照。
- **后果**: 出现「账面已保养、实物未保养」的假记录，且计划阈值不推进 → 到期判断失真（叠加 MES-TG-02 后完全失效）；保养成本（`cost`）可由客户端随意填报，成本统计不可信。
- **修复建议**: 删除 `POST/PUT/DELETE /mes/tg/maintenance-record` 写端点（归零只能走 `PUT /mes/tg/tooling/maintain/complete/{toolingId}`，见 `TgToolingServiceImpl.java:283-328`），保留 `list`/`get`；菜单 4188-4190 的 `add/edit/remove` 停用；若保留人工补录，`insertByBo` 必须由服务端回填 `usedCountBefore/After` 快照而非采信入参。
- **影响面**: `TgMaintenanceRecordController`、`ITgMaintenanceRecordService`、前端 `plus-ui/src/api/mes/tg/maintenance-record.ts:56-62` 与对应页面；无 DDL；需补测试。
- **测试现状**: 无。

### MES-TG-05 工装保养到期无自动提醒机制（无定时任务、无告警推送）

- **证据**: 全 `ruoyi-modules/ruoyi-mes` 内带调度注解的类只有 `dc/task/IotDataArchiveTask.java:41`、`eq/task/MaintenanceDueTask.java:63`、`eq/task/EquipmentStatusSyncTask.java:43`、`eq/task/EquipmentHealthTask.java:33`、`eq/task/EqOeeAggregateTask.java:27` —— **tg 包下无任何 Task 类**；`eq/task/MaintenanceDueTask.java:173-176` 只扫 `eq_maintenance_plan`（`EqMaintenancePlan::getStatus/NextDate`），不涉及 `tg_maintenance_plan`；`listMaintenanceDue()` 的调用方只有 `TgToolingController.java:52`（`GET /mes/tg/tooling/due`）与前端手动按钮 `plus-ui/src/views/mes/tg/tooling/index.vue:596-600`（`handleDue` 打开弹窗）；`listEnabledByToolingId()`（`TgMaintenancePlanServiceImpl.java:96-100`）全仓无任何调用方。
- **现状**: 设备侧（eq）已有 `@JobExecutor(name="maintenanceDueTask")` + 本地 `@Scheduled` 兜底（`MaintenanceDueTask.java:63-75`，按 B1 批次修复后生产可执行）并会推送通知；工装侧连任务骨架都没有。
- **触发路径**: 计划到期后无人打开工装台账页点「到期」按钮 → 永远不会有任何提醒；即使打开，也只是弹窗展示（无 SSE/站内信/待办）。
- **后果**: 保养计划形同台账装饰；到期工装继续上机（与 MES-TG-02 叠加），模具/夹具失保导致批量质量事故的窗口完全敞开。
- **修复建议**: 新增 `ruoyi-modules/ruoyi-mes/.../tg/task/TgMaintenanceDueTask.java`，照 `MaintenanceDueTask` 模式：`@JobExecutor(name="tgMaintenanceDueTask")` + 条件 `@Scheduled` 兜底（`snail-job.enabled` 开关让位）、遍历有效租户注入 `TenantHelper`（参考 `MaintenanceDueTask.java:142-150`）、对每个租户调 `listMaintenanceDue()` 并通过 `SseMessageUtils`/站内信推给车间主管。
- **影响面**: 新增 1 个 Task 类 + 可能的菜单/通知模板；无 DDL；需补测试（多租户遍历 + 到期判定）。
- **测试现状**: 无。

### MES-SOP-01 查阅登记端点的权限码与查看器菜单不匹配 → 工位侧查阅记录与签名全部不落库

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/controller/SopViewLogController.java:56-67`（`POST /mes/sop/view-log` 标 `@SaCheckPermission("mes:sop:viewlog:list")`）、`script/sql/ry-vue.sql:92597`（查阅记录管理菜单 4273 权限码 `mes:sop:viewlog:list`）与 `:92599`（SOP 查看器菜单 4275 权限码 `mes:sop:document:list`）、`plus-ui/src/views/mes/sop/viewer/index.vue:131-143`（`recordViewLog({...}).catch(() => { /* 查阅登记失败不阻断查看 */ })` 空 catch）
- **触发路径**: 给工位终端账号只授 `mes:sop:document:list`（菜单 4275 要求的那个）→ 打开查看器 → `POST /mes/sop/view-log` 返回 403 → 前端静默吞掉 → `sop_view_log` 无行、`es_signature` 无 `SOP_VIEW` 记录，页面看起来完全正常。
- **后果**: 电子签名 P1 的「SOP 已阅确认」在真实工位场景下**等于不存在**（只有同时拥有管理侧查阅权限的管理员操作才会产生记录）；`SopViewLogServiceImpl.java:80-85` 精心加的服务端准入与签名链投入在工位侧全部空转；后续任何「操作前已阅」追溯都无法出示。
- **修复建议**: 新增独立权限码 `mes:sop:viewlog:record`（`sys_menu` 新增 F 类按钮并绑到菜单 4275 的角色默认集），把 `SopViewLogController.java:56` 改为该码；或明确该端点「登录即可」（`@SaCheckLogin`）。同时把前端空 catch 改为 `console.warn` + 一次重试，避免此类静默失败复发。
- **影响面**: `SopViewLogController`、`ry-vue.sql`（新增菜单行 + 角色菜单关联，存量库需增量脚本）、`plus-ui` 查看器；无表结构变更；需补测试（无权限码时 403 的契约测试）。
- **测试现状**: 无。

### MES-SOP-02 SOP 无「操作前必须确认」门禁，查阅/签名纯展示

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProEntryCheckServiceImpl.java:158-163`（`checkSopVersion` 只判断 `sopBindingService.getSopForStep(stepRecordId)` 非空且 `status=PUBLISHED`，**不**查 `sop_view_log`、**不**校验签名）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProStepCheckRule.java:30`（校验类型枚举：`MATERIAL_KITTING/OPERATOR_SKILL/EQUIPMENT_STATUS/SOP_VERSION/TOOLING_AVAILABLE/LOT_AVAILABLE/INSPECTION_REQUIRED/QUANTITY_REACHED/PARAMETER_OK/EXCEPTION_CLOSED`，**无**「已阅/已确认」类）、`plus-ui/src/views/mes/sop/viewer/index.vue:133-142`（登记查阅不 await、失败不阻断）
- **触发路径**: 操作员扫码进站 → 只要工序绑了 PUBLISHED 的 SOP 即通过，完全不打开查看器也能开工；`sop_view_log` 里没有该操作员的任何记录，系统仍判定合格。
- **后果**: 「作业指导书已阅」在合规上不可证明——电子签名只记录了「某个用户打开过页面」，而生产放行与它无关；DHR/批记录所需的「操作前确认作业指导书版本」环节缺失（与 MES-SOP-05 的正文哈希缺失叠加，签名证据链无实质效力）。
- **修复建议**: ① `ProStepCheckRule` 新增 `SOP_ACKNOWLEDGED` 类型；② `ProEntryCheckServiceImpl.dispatchEntry` 增分支：按 `(operatorId, sopId, currentVersion)` 查 `sop_view_log` 且 `signature_id is not null`，缺失则 `addFailure("未确认作业指导书 Vx.y")`；③ 查看器增加显式「我已阅读并确认」按钮触发 `recordViewLog`，取代当前 onMounted 的隐式登记。
- **影响面**: `ProStepCheckRule`、`ProEntryCheckServiceImpl`、`SopViewLogMapper`（新增按 operator+version 的查询）、`plus-ui` 查看器交互、`pro_step_check_rule` 需补种子；无表结构变更；需补测试。
- **测试现状**: `ProEntryCheckServiceImplTest.java` 覆盖了 SOP_VERSION 分支，无「未阅应拦截」用例。

### MES-SOP-03 查阅记录（含已签名记录）可被静默删除

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/controller/SopViewLogController.java:75-80`（`DELETE /mes/sop/view-log/{ids}`，仅需 `mes:sop:viewlog:list`）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/impl/SopViewLogServiceImpl.java:169-179`（`deleteByIds` 直接物理删除，无守卫、不校验 `signature_id`、不联动 `es_signature`）、`SopViewLogServiceImpl.java:100`（每条记录都带 `signatureId`）
- **触发路径**: 任一持有 `mes:sop:viewlog:list` 的账号在查阅记录页勾选删除（前端 `plus-ui/src/api/mes/sop/view-log.ts:49` 已提供 `delViewLog`）→ `sop_view_log` 行消失，而 `es_signature` 中对应的 `SOP_VIEW` 记录仍在 → 签名台账指向一个不存在的业务记录。
- **后果**: append-only 电子记录被破坏（21 CFR Part 11 §11.10(e) 要求审计追踪不可被操作者修改/删除）；追溯时「谁在何时看过哪一版」直接消失，且签名链因 `prev_hash` 已固化而无法通过验链发现缺失。
- **修复建议**: 删除该端点（查阅记录页去掉删除按钮，菜单 4273 下不设删除按钮）；若必须支持纠错，改为 `del_flag` 软删除 + 在 `es_signature` 追加一条 `SOP_VIEW_VOID` 冲正签名，并在 `buildQueryWrapper`（`:184-192`）默认过滤 `del_flag`。
- **影响面**: `SopViewLogController`、`SopViewLogServiceImpl`、`plus-ui/src/api/mes/sop/view-log.ts:49` 与查阅记录页；软删除方案需 DDL（列已存在 `del_flag`，无需新增）；需补测试。
- **测试现状**: 无。

### MES-SOP-04 `sop_version` 种子状态值为非法值 `PUBLISHED`，使 CURRENT 守卫失效

- **证据**: `script/sql/ry-mes.sql:2767`（列注释「状态: PENDING/CURRENT/HISTORICAL」）与 `:2783-2836`（54 行种子全部 `'PUBLISHED'`）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/ISopDocumentService.java:31-35`（常量 `PENDING`/`CURRENT`/`HISTORICAL`）、`script/sql/ry-vue.sql:90761-90763`（字典 `sop_version_status` 仅 PENDING/CURRENT/HISTORICAL）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/impl/SopVersionServiceImpl.java:91-97`（删除守卫只拦 `VERSION_CURRENT`）、`SopDocumentServiceImpl.java:469-476`（`doPublish` 只找 `VERSION_PENDING`）
- **触发路径**: ① 对种子 SOP 点「删除版本」→ 守卫按 `status='CURRENT'` 查不到（实际值是 `PUBLISHED`），版本被删掉，`sop_document.file_oss_id/current_version` 变成悬空引用；② 对种子 SOP 走发布流程 → `doPublish` 找不到 PENDING 版本，抛「无待发布版本,无法发布」；③ 版本列表 `dict-tag` 显示原始码 `PUBLISHED`。
- **后果**: 已发布 SOP 的正文版本可被删除（追溯丢失）；发布流程对存量数据直接报错；字典与实际数据不一致，前端展示异常。
- **修复建议**: ① 主脚本 `ry-mes.sql:2783-2836` 种子值改为 `CURRENT`；② 提供存量库增量脚本 `UPDATE sop_version SET status='CURRENT' WHERE status='PUBLISHED'`（并在 `doc/system/数据库增量变更记录.md` 登记）；③ 在 `SopVersionServiceImpl` 删除守卫改为「非 PENDING 一律不可删」的保守策略，避免再次出现值域外状态绕过守卫。
- **影响面**: 需 DDL/数据订正脚本；影响 `SopVersionServiceImpl`、前端版本列表；需补测试（守卫对未知状态的行为）。
- **测试现状**: 无 `SopVersionServiceImpl` 单测。

### MES-SOP-05 `SOP_VIEW` 签名不绑定 SOP 正文哈希

- **证据**: `ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/ESignatureServiceImpl.java:706-709`（`appendSignature` → `appendLedger(..., DocLink.NONE)`）、`:786-789`（`DocLink.NONE` 五个字段全 null，含 `docHash`）、`:767-769`（链哈希把 `entity.getDocHash()` 编入，因此 null 不产生正文约束）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/impl/SopViewLogServiceImpl.java:146-147`（`signInNewTransaction(new SignCommand(SignBizType.SOP_VIEW, sopId, versionNo, meaning, null, LOGIN_ONLY, null))`，命令里没有文档 OSS/哈希）
- **触发路径**: 操作员查看并签署 SOP（`sop_view_log.signature_id` 已回填）→ 管理员在「文档管理 → 新增版本」上传**同名同版本号**的新 PDF 覆盖 OSS 对象（或直接改 OSS 对象内容）→ 验签仍通过（链哈希只覆盖 sopId/versionNo/meaning/时间），签名无法证明签署时看到的正文。
- **后果**: 电子签名对 SOP 正文不具备不可否认性——「签字时看到的是哪一份内容」不可证明；一旦发生质量争议（操作员称指导书内容不同），签名链无法作为证据。
- **修复建议**: 在 `SignCommand` 中带上 `docOssId`/`docHash`（SOP 版本发布时对 `sop_version.file_oss_id` 指向的文件算 SM3 并存列，如 `sop_version.file_hash`），`SopViewLogServiceImpl.signSopView` 传入该哈希，使 `appendLedger` 走非 `NONE` 的 `DocLink`；验签时重新计算 OSS 文件哈希比对。
- **影响面**: `ruoyi-common-sign`（`SignCommand` 增字段，向后兼容需默认 null）、`sop_version` 需 DDL 增 `file_hash`、`SopDocumentServiceImpl.doPublish` 需在发布时算哈希、`SopViewLogServiceImpl`；需补测试（哈希变更后验签失败）。
- **测试现状**: `ruoyi-common-sign/src/test/.../ESignatureVerifyTest.java:170` 已覆盖「bizNo 被改写 → 检出」，但无 docHash 用例。

### MES-SOP-06 工序绑定接口不校验 SOP 状态

- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/impl/SopProcessBindingServiceImpl.java:88-112`（`insertByBo` 只落 `processId/sopId/versionNo/isDefault/sortOrder`，**不查 SOP 状态**）、`:161-164`（`getSopForProcess` 对非 PUBLISHED 返回 `null`）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/controller/SopProcessBindingController.java:64-67`（`POST /mes/sop/binding`）
- **触发路径**: 直接调 API（或前端选择器未过滤的边界情况）把 DRAFT 或 ARCHIVED 的 `sopId` 绑到工序 → 绑定列表显示绑定存在（`fillSopInfo` 会带出 `sopStatus`，`:253-263`）→ 工位进站 `checkSopVersion` 因 `getSopForStep` 返回 null 而报「工序未绑定已发布的SOP文档」，排查时绑定页却"明明绑了"。
- **后果**: 工位侧作业指导书静默不可用（与 09-18 归档可见性 spec 想避免的「无声消失」同类问题，但入口从归档守卫换成了绑定入口）；排障成本高。
- **修复建议**: `insertByBo` 内查 `sopDocumentMapper.selectVoById(bo.getSopId())`，`status != PUBLISHED` 时抛 `ServiceException("仅已发布SOP可绑定工序,当前: X")`；同理在批量导入路径加同一校验。
- **影响面**: `SopProcessBindingServiceImpl`；前端 `plus-ui/src/api/mes/sop/binding.ts:54` 无需改（错误信息可直接展示）；无 DDL；需补测试。
- **测试现状**: 无 `SopProcessBindingServiceImpl` 单测。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果与建议 |
|---|---|---|---|
| MES-TG-06 | 保养计划可直接改写下次到期阈值 | `TgMaintenancePlanServiceImpl.java:79-81`（`updateByBo` 无条件采信 `nextMaintainCount/Hours/Time`） | 有权限者可把 `next_maintain_time` 改到明年，绕过寿命双轨；建议 `updateByBo` 忽略这三个字段，仅由 `updatePlanAfterMaintain`（`TgToolingServiceImpl.java:472-495`）维护 |
| MES-TG-07 | 保养计划无唯一约束 | `script/sql/ry-mes.sql:2946-2948`（仅 PK + `idx_tooling_id` + `idx_enabled`） | 同工装可建多条 `BY_COUNT` 计划，`listMaintenanceDue`（`TgToolingServiceImpl.java:441-465`）逐条判断 → 到期弹窗重复行；建议加 `UNIQUE(tooling_id, trigger_type, tenant_id)` |
| MES-SOP-07 | 版本号生成是 TOCTOU 且无唯一索引 | `script/sql/ry-mes.sql:2775-2777`（无 UNIQUE）、`SopDocumentServiceImpl.java:231-235`（`selectCount` 后拼 `"1."+count`） | 并发两次新增版本得到同一版本号；删过版本后会重号（如删掉 1.1 后再建仍是 1.1）→ `SOP_VIEW` 签名的 `bizNo` 歧义；建议加 `UNIQUE(sop_id, version_no, tenant_id)` 并改用 `max(version_no)+1` |
| MES-SOP-08 | 种子 SOP 无正文附件，无离线能力 | `plus-ui/src/views/mes/sop/viewer/index.vue:104-111`（注释明确「仓库种子数据里的 57 条 SOP 全部是 file_oss_id = 0」） | 工位打开查看器只有提示、无指导书；且正文经 `@Translation` 解析为 OSS URL 在线加载，断网即不可用。建议种子数据补附件，并评估工位终端本地缓存（service worker / 预下载当前工序 SOP） |
| MES-SOP-09 | 归档（废止终态）SOP 仍可编辑基础信息 | `SopDocumentServiceImpl.java:152-153`（`DRAFT` 或 `ARCHIVED` 均可编辑） | 废止文档的 `sopName/sopType/remark` 可被改，而它的 `SOP_VIEW` 签名指向该文档；建议归档后仅允许改 `archiveReason` |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 工装校准管理与超期拦截 | `ry-mes.sql:2986-3015` 无校准列；`ITgToolingService.java:27-33` 无 CALIBRATING | `tg_tooling` 台账主体、`isMaintenanceDue()`（`TgToolingServiceImpl.java:441`）可作为模板、`checkToolingAvailable()`（`:362`）已有门禁入口 | 中（DDL 3 列 + 字典 1 条 + 校验钩子 2 处 + 前端表单/列表 + 测试） |
| 2 | 工装到期提醒（自动告警） | tg 包无 Task；`MaintenanceDueTask.java:63-75` 仅 eq | `listMaintenanceDue()`（`:395`）、SSE 工具、`MaintenanceDueTask` 可整段复用（多租户遍历 + 兜底调度） | 小（1 个 Task 类 + 通知） |
| 3 | 工装可用性门禁规则种子 | `ry-mes.sql:1899-1902` 只有 `pro_step_check_rule` 建表，无 INSERT | `ProEntryCheckServiceImpl.checkToolingAvailable`（`:168`）、`TYPE_TOOLING_AVAILABLE`（`IProEntryCheckService.java:31` 同族常量） | 小（种子 INSERT + 进站时传 `toolingId` 的前端确认） |
| 4 | 工装维修单（REPAIR 与故障/维修成本关联） | `tg_maintenance_record.maintain_type` 有 `PLANNED/REPAIR`（`ry-mes.sql:2960`），但无维修申请/故障原因/停机时长承载 | `tg_maintenance_record`（含 `cost/description`）、`eq_repair` 可参考 | 中（新增表或扩列 + 前端） |
| 5 | SOP 操作前确认门禁（已阅校验） | `ProStepCheckRule.java:30` 无 SOP_ACK 类型；`ProEntryCheckServiceImpl.java:158-163` 只校验绑定 | `sop_view_log`（`ry-mes.sql:2845` 含 `signature_id`）、`es_signature` 链、`SopViewLogServiceImpl.recordView` | 中（枚举 + 校验分支 + 查看器确认按钮 + 规则种子） |
| 6 | SOP 签名绑定正文哈希 | `ESignatureServiceImpl.java:786-789` 逻辑签名 docHash 恒 null | `es_signature.doc_hash/src_doc_hash` 列已存在（DDL 无需改）、`es_sign_document` 文档签章链路可参考 | 小（`sop_version` 增 `file_hash` + 传参 + 验签比对） |
| 7 | **DHR / 批记录实体**（复核 09-06 报告 §1.1 第 6 项：**仍然不存在**） | 全仓 grep `dhr\|batch_record\|batchRecord\|批记录\|device_history` 仅命中 1 处无关文件（`ruoyi-flowable/.../ProcessAccessGuard.java:20`）；`ry-mes.sql` 无对应 CREATE TABLE | **可复用资产**：`pro_flow_card`/`pro_flow_card_step`（`ry-mes.sql:1782`/`:1818`，工单-流转卡-工序执行链）、`pro_sn`（`:1864`，SN 级追溯）、`lt_lot`（`:538`，批次）、`dc_data_record`（`:55`，采集数据）、`sop_view_log`（`:2845`，查阅+签名）、`es_signature`（签名链）、`pro_step_check_rule`（`:1902`，门禁证据） | 大（需新建 DHR 主表 + 与工单/SN/批次/签名关联的聚合服务 + 导出/审核视图） |

## 5. 已核查确认无问题（证明覆盖范围）

- **权限码种子齐备**：tg 侧 13 个（`ry-vue.sql:92515-92537`）、sop 侧 13 个（`:92585-92599`）权限码与控制器 `@SaCheckPermission` **逐一对应**（`TgToolingController.java:40-154`、`TgMaintenancePlanController.java:30-59`、`TgMaintenanceRecordController.java:30-59`、`TgUsageRecordController.java:30-59`、`SopDocumentController.java:51-226`、`SopVersionController.java:40-79`、`SopProcessBindingController.java:40-92`、`SopViewLogController.java:38-78`）。任务提示的 `mes:sop:document:list` 被 8 处引用，但菜单 4261/4275 均已种子，**不构成 403 缺口**（真正的问题是 MES-SOP-01 的权限码**错配**，而非缺种子）。
- **字典值齐备**：`mes_tooling_status`/`mes_tooling_type`（`ry-vue.sql:90732-90740`）、`tg_maintenance_trigger_type`/`tg_maintenance_type`/`tg_usage_status`（`:90786-90792` + 类型 `:91102-91104`）、`mes_sop_status`/`mes_sop_type`/`mes_sop_file_type`/`sop_version_status`（`:90748-90763`）全部有 `sys_dict_type` + `sys_dict_data`。工装状态/保养类型字典**存在**（此前担心的缺口不成立）。
- **自动编码规则已种子**：`MES_TOOLING_CODE`、`MES_SOP_CODE`（`ry-vue.sql:88059-88060`），`insertByBo` 留空编码走 `autoCodeUtil`（`TgToolingServiceImpl.java:84-86`、`SopDocumentServiceImpl.java:515-517`）不会失败；`uk_tooling_code` 唯一索引（`ry-mes.sql:3011`）兜底并发重码。
- **归档可见性 spec（2026-09-18）已落地**：默认排除已归档 `ne(!statusGiven, status, ARCHIVED)`（`SopDocumentServiceImpl.java:529-537`）、显式状态走 `eq`、归档绑定守卫（`:443-448`）、推送路径过滤 PUBLISHED（`SopProcessBindingServiceImpl.java:161-164`）—— 与 spec 行为矩阵一致，**复核后无回归**。
- **归档后不可被生产引用**：`getSopForProcess`/`getSopForStep` 对非 PUBLISHED 返回 null（`SopProcessBindingServiceImpl.java:161-164`、`:172-178`），进站 `checkSopVersion` 据此判失败（`ProEntryCheckServiceImpl.java:158-163`）。
- **借出/归还的状态机与并发**：`checkout` 用 `@Lock4j(keys="#toolingId")` + CAS `eq(status, IDLE)`（`TgToolingServiceImpl.java:167-201`），`doReturn` 同样加锁 + CAS `eq(status, IN_USE)`（`:204-245`），`startMaintain`/`completeMaintain`/`scrap` 均为条件更新（`:268-275`、`:296-305`、`:345-352`）；**无死状态、无可绕过的状态流转端点**（`insertByBo` 强制 `STATUS_IDLE`，`:99`；`updateByBo` 不允许改 `status`/`used_count`，`:119-122`）。
- **删除孤儿守卫**：工装删除校验使用/保养计划/保养记录（`TgToolingServiceImpl.java:145-160`）；SOP 删除校验 PUBLISHED + 绑定 + 查阅记录（`SopDocumentServiceImpl.java:174-190`）；版本删除校验 CURRENT（`SopVersionServiceImpl.java:91-97`，但对非法状态值失效——见 MES-SOP-04）。
- **`@Validated` 无漏标**：8 个控制器全部标注（`TgToolingController.java:29`、`TgMaintenancePlanController.java:22`、`TgMaintenanceRecordController.java:22`、`TgUsageRecordController.java:22`、`SopDocumentController.java:35`、`SopVersionController.java:25`、`SopProcessBindingController.java:25`、`SopViewLogController.java:23`）。
- **`signature_id` 不再恒为 null（复核 09-06 报告 §1.1 第 6 项：SOP_VIEW 部分已修复）**：`SopViewLogServiceImpl.java:100` 调 `signSopView` → `eSignatureService.signInNewTransaction`，`es_signature` 记录 `signerUserId/signerName/signerDeptId/signedAt/signIp/signUa/prevHash/chainHash/authMethod`（`ESignatureServiceImpl.java:744-771`），且用 `REQUIRES_NEW` 保证查阅登记不被签名故障回滚（`SopViewLogServiceImpl.java:127-133` 注释与 `:146` 实现一致）。残留缺口是正文哈希未绑定（MES-SOP-05）与记录可删（MES-SOP-03）。
- **进站工装门禁链路存在但需配置**：`ProEntryBo.toolingId`（`ProEntryBo.java:33`）→ `ProEntryCheckServiceImpl.checkToolingAvailable`（`:168-177`）→ `ITgToolingService.checkToolingAvailable`（`:105`）链路完整、有单测（`ProEntryCheckServiceImplTest.java:158`），缺的是规则种子与到期判断（MES-TG-02）。
- **SOP 版本/审批状态机写入点齐备**：`submitForApproval`（CAS DRAFT→IN_REVIEW，`SopDocumentServiceImpl.java:285-292`）、`afterFlowHandle`（回调驱动，`:302-320`）、`approve`/`reject`（`:375`/`:399`）、`doPublish`（PENDING→CURRENT、旧 CURRENT→HISTORICAL、主档同步 `current_version/file_oss_id/file_type`，`:467-501`）、`archive`（`:434-459`）—— 无「死流转」。
- **前端 API 层与后端端点一一对应**：`plus-ui/src/api/mes/tg/{tooling,maintenance-plan,maintenance-record,usage-record}.ts` 与 `.../sop/{document,version,binding,view-log}.ts` 覆盖了全部控制器端点（含 `listMaintenanceDue`/`checkToolingUsable`/`archiveDocument`/`pushSop`/`recordViewLog`），**无「后端有端点前端无函数」的不可达项**。

**未覆盖范围（本次未读，供后续补审）**：`TgToolingBo/Vo`、`TgUsageRecordBo`、`SopDocumentBo/Vo`、`SopViewLogBo/Vo` 等 DTO 的字段级逐项比对（仅通过 grep 与 SQL DDL 交叉核对，未逐文件读）；`plus-ui/src/views/mes/tg/*` 与 `mes/sop/*` 各页面的表格列顺序/宽度/表单分组等前端布局细则（仅读了 `tooling/index.vue:560-639`、`sop/viewer/index.vue:100-179`）；`sop_document` 与 `tg_*` 的 mapper XML（未发现 XML，均为 MyBatis-Plus 注解）；离线/PWA 缓存能力（仅从查看器注释推断，未查构建配置）。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `sop_version.status` | 代码/字典值域 `PENDING/CURRENT/HISTORICAL`（`ISopDocumentService.java:31-35`、`ry-vue.sql:90761-90763`） | 种子数据实际为 `PUBLISHED`（`ry-mes.sql:2783`），`dict-tag` 无对应项 | 展示为原始码；守卫失效（MES-SOP-04） |
| `tg_tooling` 校准字段 | 无任何字段 | 无 | 双向都缺（MES-TG-01） |
| `tg_maintenance_record.usedCountAfter` 等快照 | `TgMaintenanceRecordBo` 可由客户端传入并被直接落库（`TgMaintenanceRecordServiceImpl.java:59-62`） | 保养记录页提供新增/修改表单（`plus-ui/src/api/mes/tg/maintenance-record.ts:56-59`） | 服务端应回填快照而非采信入参（MES-TG-04） |
| `ProEntryBo.toolingId` | 可选上下文，为空则跳过校验（`ProEntryCheckServiceImpl.java:169-171`） | `entryFlowCard` 提交 `ProEntryBo`（`flowcardApi.ts:122`），是否传 `toolingId` 未在本次读取的页面中确认 | 门禁可被「不传参数」绕过（MES-TG-02） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：MES-TG-02（`checkToolingAvailable` 加到期判断 + `pro_step_check_rule` 种子 + 不传 `toolingId` 时告警）→ 单点改动即可让工装门禁真正生效；MES-TG-01（校准字段/状态/拦截，含 DDL 与字典，工作量较大但必须做，建议与 A 同批设计 DDL 一次到位）。
- **批次 B（P1，按主题分 3 个提交）**：① 写端点收敛——MES-TG-03 + MES-TG-04（删除 `usage-record`/`maintenance-record` 的 add/edit/remove，前端同步去掉按钮）；② 合规闭环——MES-SOP-01（新增 `viewlog:record` 权限码）+ MES-SOP-03（删除端点去掉）+ MES-SOP-05（签名绑定正文哈希，需 `sop_version.file_hash` DDL）；③ 状态与校验——MES-SOP-02（SOP_ACKNOWLEDGED 门禁）、MES-SOP-04（种子状态订正 + 存量 UPDATE）、MES-SOP-06（绑定校验 SOP 状态）、MES-TG-05（新增 `TgMaintenanceDueTask`，与 eq 侧同模式）。
- **批次 C（P2）**：MES-TG-06、MES-TG-07、MES-SOP-07（三条均建议合并为一个 DDL 批次：`tg_maintenance_plan` 唯一键、`sop_version` 唯一键、版本号生成改 `max+1`）、MES-SOP-08（种子附件与离线评估）、MES-SOP-09。
- **批次 D（功能建设，独立立项）**：DHR/批记录实体（复用 §4 第 7 项列出的 7 项资产）、工装维修单（§4 第 4 项）。
