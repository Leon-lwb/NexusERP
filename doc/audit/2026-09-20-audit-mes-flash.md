# MES 刷写（flash）与标签（label）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/flash`（58 文件）、`.../mes/label`（10 文件）、`ruoyi-modules/ruoyi-station`（绑定/追溯关联部分） | 覆盖文件数: 27（实际读过全文/局部的 Java + SQL） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-FLASH-01 | P0 | 权限 | 生产配置硬编码 `mes.api-key`，免登录上报接口唯一防线泄露，可伪造任意 SN 的 PASS 刷写记录绕过完工门禁 | `application-prod.yml:3` |
| MES-FLASH-02 | P0 | 数据错 | MAC 池刷写只落"应刷 MAC"，设备上报的实际写入值被丢弃，追溯数据失真 | `MacPoolFlashProvider.java:54`、`FlashRecordServiceImpl.java:564` |
| MES-FLASH-03 | P0 | 状态机 | 池类刷写记录的 `content_ref_id` 存的是资源项ID，与工序绑定/门禁比对的 contentRefId 语义错位 → 完工门禁永不通过（工单卡死） | `MacPoolFlashProvider.java:48`、`FlashRecordServiceImpl.java:561`、`ProFlowCardServiceImpl.java:388` |
| MES-TEST-01 | P0 | 校验 | AUTO 测试上报无"必测项全覆盖"校验，设备只报 1 项即通过，缺失项不产生记录 | `TestRecordServiceImpl.java:364` |
| MES-LABEL-01 | P0 | 校验 | ZPL 分支字段值零转义，SN/物料名可注入 `^`/`~` 标签指令（截断、改版式、打印任意内容） | `LabelRenderService.java:122` |
| MES-FLASH-04 | P1 | 幂等 | 刷写重试次数由客户端传入、服务端不累加、无上限，同 SN 同内容可无限重复插入 | `FlashRecordServiceImpl.java:568` |
| MES-FLASH-05 | P1 | 功能缺失 | 刷写类型 `status=DISABLED` 不生效；`gate_enabled`/`verify_strategy`/`source_provider` 三列无任何代码读取 | `FlashRecordServiceImpl.java:523` |
| MES-FLASH-06 | P1 | 并发 | 池资源只分配不回收，FAIL 也永久占用；`USED` 为死状态；池选择无 `ORDER BY` 取首个 | `MesResourcePoolServiceImpl.java:175`、`MacPoolFlashProvider.java:63` |
| MES-FLASH-07 | P1 | 功能缺失 | 资源池 `source=EXTERNAL`/`external_config` 无任何实现，池耗尽即硬失败无降级 | `ry-mes.sql:1255`、`MesResourcePoolServiceImpl.java:69` |
| MES-FLASH-08 | P1 | 校验 | 外部 REST provider 无重试/无降级，SSRF 校验可被 302 重定向绕过，响应原样入库 | `ExternalRestFlashProvider.java:96` |
| MES-FLASH-09 | P1 | 权限 | 免登录设备通道可伪造 `operatorId/operatorName`，操作留痕可冒名 | `FlashRecordServiceImpl.java:615` |
| MES-FLASH-10 | P1 | 租户 | 设备上报强制落租户 `000000`，非 000000 租户数据被写错库 | `FlashReportController.java:68` |
| MES-FLASH-11 | P1 | 契约 | `mes.flash-step-gate` 配置项无代码读取，真实开关是 `mes.flash.gate.mode`，运维"关不掉"门禁 | `application-prod.yml:5`、`ProFlowCardServiceImpl.java:85` |
| MES-TEST-02 | P1 | 校验 | ENUM 判定 fail-open：未配合格值集合 → PASS；JSON 解析失败 → PASS | `TestRecordServiceImpl.java:469` |
| MES-TEST-03 | P1 | 幂等 | 已判定测试行可反复覆盖改写（FAIL→PASS），无不可变性、无留痕 | `TestRecordServiceImpl.java:198` |
| MES-TEST-04 | P1 | 跨模块链路 | 测试 FAIL 不触发 NCR/返工/SN 状态变更，不良品处置链路断开 | `TestRecordServiceImpl.java:408` |
| MES-TEST-05 | P1 | 契约 | 后端 `POST /mes/flash/pool/allocate` 存在但前端 api 层无函数，手工分配资源 UI 不可达 | `MesResourcePoolController.java:69`、`plus-ui/src/api/mes/flash/pool.ts:38` |
| MES-LABEL-02 | P1 | 幂等 | 打印结果不落库、无重打计数；造码事件对每个启用 SN 模板各打一张，多模板重复打印 | `SnLabelPrintEventListener.java:45`、`LabelPrintService.java:63` |
| MES-LABEL-03 | P1 | 功能缺失 | 模板非法 JSON 的异常在造码监听器内被吞成 warn，标签静默不打印，产线无感知 | `LabelRenderService.java:143`、`SnLabelPrintEventListener.java:64` |
| MES-STATION-01 | P1 | 并发 | 关键件绑定 CAS 更新未校验影响行数，并发重复绑定会重复写 BIND 日志并虚增 boundQty | `StationSnService.java:85` |

## 1. P0 详述

### MES-FLASH-01 生产环境硬编码设备上报密钥，免登录写接口可被伪造

- **证据**: `ruoyi-admin/src/main/resources/application-prod.yml:1-5`（第 3 行 `api-key: riLPP9GDxYL3Bop7NHPQEGS7IB3wP69hYyx0zv1F` 字面量）、`FlashReportController.java:56`（`@Value("${mes.api-key:}")`）、`FlashReportController.java:105-110`（空则 fail-closed）、`ruoyi-admin/src/main/resources/application.yml:142`（`/mes/flash/report/**` 在 `security.excludes` 免登录白名单）、`docker-compose-main.yml:147-148`
- **现状**: 09-06 报告 §2.1 N1 称"`mes.api-key` 默认值已去除（fail-closed）"——**复核后：注解默认值确已去除（`${mes.api-key:}`），但 `application-prod.yml` 仍写死一个强随机字面量**，且 `application-dev.yml:3` 为 `nexus-mes-api-2026`（可猜）。生产 profile 下 `apiKey` 永不为空，`checkApiKey` 的 fail-closed 分支（第 106 行）永不触发。
- **触发路径**: 读取仓库/配置文件得到密钥 → `POST /mes/flash/report/flash/auto`，请求头 `X-API-KEY: <该值>`，body `[{snCode:"任意在制SN", contentRefId:<已发布固件ID>, result:"PASS"}]` → 该 SN 立即产生一条 PASS 刷写记录。同法可伪造 `POST /mes/flash/report/test/auto`。
- **后果**: 未真正刷写的 SN 可通过 `ProFlowCardServiceImpl.checkFlashStepGate`（`ProFlowCardServiceImpl.java:350-404`，只校验"存在最新 PASS 记录"）完成过站与工单完工，固件版本与测试结论全部失真；且无需登录、无 IP 限制、无频次限制。
- **修复建议**: ① 删除 `application-prod.yml:3` 字面量，改为 `api-key: ${MES_API_KEY:}`（`docker-compose-main.yml:148` 已注入 `MES_API_KEY`，第 147 行注释亦写明"不设则上报全部拒绝"）；② dev 值改为不可猜随机值或同样走环境变量；③ 上报接口增加设备级凭证（每台设备独立 key + 设备编码绑定），key 比较改为恒定时间；④ 对 `report/**` 限流。
- **影响面**: `application-prod.yml`、`application-dev.yml`；调用方产线上位机需同步换 key；无需 DDL；需补"密钥为空时拒写"单测。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test` 下无 flash 上报相关测试，无覆盖。

### MES-FLASH-02 MAC 池刷写记录丢失实际写入值（追溯数据失真）

- **证据**: `MacPoolFlashProvider.java:43-51`（`resolveContent` 生成 `{"mac":"<池中分配值>"}`）、`MacPoolFlashProvider.java:54-61`（`verify` 直接返回 `expected.getContentPayload()`，**未读 `actual.getContentPayload()`**）、`FlashRecordServiceImpl.java:564`（`record.setContentPayload(verify.getContentPayload())`）、`FlashRecordBo.java:80-81`（BO 明确声明 `contentPayload` 是"实际写入内容JSON"）
- **现状**: 设备上报的 `contentPayload`（设备实际烧进去的 MAC）在池类路径下被完全忽略，落库的是"分配到的 MAC"；固件类有 MD5 比对（`FirmwareFileFlashProvider.java:72-75`），池类 `verify` 无条件 `pass(true)`（第 56-60 行）。
- **触发路径**: 上位机写入 MAC `AA:BB:CC:DD:EE:FF` 但上报 `contentPayload={"mac":"11:22:33:44:55:66"}`（或漏报、写错）→ `result=PASS` 照常落库，`mes_flash_record.content_payload` 记录的是 `11:22:33:44:55:66`。
- **后果**: 追溯数据不可信——产线记录与实际烧录值可以不一致且无法发现；出现"同一 MAC 出现在两台设备"时记录层面看不出任何异常（记录里永远是池分配的、天然唯一的值），MAC 唯一性只剩 `mes_resource_item` 分配表兜底，设备侧实际值无任何校验。
- **修复建议**: `MacPoolFlashProvider.verify`/`SnPoolFlashProvider.verify` 改为：`actual.getContentPayload()` 非空时解析其 JSON 取出 `mac`/`sn` 与 `expected` 比对，不一致则 `pass(false)`，并把 `contentPayload` 设为**实际值**（与固件类 MD5 语义一致）；`FlashRecordServiceImpl:564` 改为 `verify.getContentPayload() != null ? verify.getContentPayload() : expected.getContentPayload()`。另建议 `mes_flash_record` 增加 `actual_payload` 列区分应刷/实刷（需 DDL）。
- **影响面**: `MacPoolFlashProvider`、`SnPoolFlashProvider`、`FlashRecordServiceImpl`；前端刷写记录详情展示口径随之变化；需补池类 verify 单测。
- **测试现状**: 无覆盖。

### MES-FLASH-03 池类刷写 `content_ref_id` 语义错位，完工门禁永不通过

- **证据**: `MacPoolFlashProvider.java:45-49`（`contentRefId(item.getItemId())` — **资源项ID**）、`FlashRecordServiceImpl.java:560-561`（`record.setContentRefId(expected.getContentRefId())` — 落库为资源项ID）、`FlashRecordServiceImpl.java:489-491`（工序绑定校验用 BO 传入的 `contentRefId`，即工序配置值）、`ProFlowCardServiceImpl.java:386-391`（门禁按 `eq(FlashRecord::getContentRefId, b.contentRefId())` 查最新记录）
- **现状**: 池类刷写时，BO 携带的 `contentRefId` 是工序 `step_flash_config` 中配置的值（池ID 或为空），而落库的 `content_ref_id` 被 Provider 改写成**本次分配到的资源项ID**（每 SN 不同、配置期不存在）。门禁按配置值反查记录必然查不到：
  - 配置里填池ID → 记录里是资源项ID → 匹配不到；
  - 配置里不填（null）→ `ProFlowCardServiceImpl.java:388` 的 `.eq(...)` 无条件拼 `content_ref_id = NULL`（同仓库 `FlashRecordServiceImpl.java:280` 用的是 `.eq(refId != null, ...)` 守卫写法，此处缺守卫）→ 同样匹配不到。
- **触发路径**: 工单某工序绑定 `mac` 池刷写 → 产线刷写 PASS → 该工序报工/完工 → `checkFlashStepGate` 报"刷写门禁未通过: ...(mac: 未刷写)"，即使刷写记录确实存在且为 PASS。
- **后果**: 绑定了 MAC/SN 池刷写的工单**无法完成工序与工单完工**（FORCE 模式硬拦截）；运维只能把 `mes.flash.gate.mode` 改成 WARN/关闭绕过，等于把固件刷写门禁一并关掉。属"池类刷写上线即卡线"级别缺陷。
- **修复建议**: 统一 `content_ref_id` 语义为"工序绑定的内容源ID"（池类存 poolId，资源项ID 另存新列 `resource_item_id`）；或门禁改为按 `(snCode, flashTypeCode)` + 绑定顺序匹配，不比对 contentRefId。同时给 `ProFlowCardServiceImpl.java:388` 的 `.eq` 补 null 守卫。需 DDL（若新增列）；需补"池类刷写 + 门禁通过"集成测试。
- **影响面**: `MacPoolFlashProvider`、`SnPoolFlashProvider`、`FlashRecordServiceImpl`、`ProFlowCardServiceImpl`；需 DDL；需补测试。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test` 下未见池类刷写或门禁覆盖。

### MES-TEST-01 AUTO 测试上报无必测项覆盖校验

- **证据**: `TestRecordServiceImpl.java:349-369`（按上报 items 逐项匹配 `standardMap`，匹配不到仅 `unmatched.add` 后 `continue`）、`TestRecordServiceImpl.java:364-415`（只为上报项建行判定）、`TestRecordServiceImpl.java:416-418`（仅 `judged == 0` 才报错）、`ITestItemService.selectByMaterial` 已提供该物料全部测试项标准
- **现状**: 服务端拿到该物料完整测试项清单（`standards`），却只对**设备上报的项**判定，不校验覆盖度；未上报项既不建行也不报错。人工路径 `beginTest`（第 137-140 行）反而是全项建行的。
- **触发路径**: 综测仪只上报 1 项（如 `{"itemCode":"VOLT","value":"5.0"}`），其余项不报 → 返回 `judged=1`，落库 1 行 PASS，测试单"通过"，无缺失告警。
- **后果**: 未测项在数据上不存在，事后无法区分"没测"与"测了通过"；测试覆盖率失真，可被产线用"少报项"规避测试。
- **修复建议**: `reportTestAuto` 判定前先算 `missing = standards.itemCode - dedupItems.keySet()`，非空抛 `ServiceException("缺少必测项: ...")`（或按配置降级为告警并落一条 `result=''` 缺测行）；同时补测试单级整体结论（见 §4）。
- **影响面**: `TestRecordServiceImpl`、上位机上报契约；无 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-LABEL-01 ZPL 字段值未转义 → 标签指令注入

- **证据**: `LabelRenderService.java:118-123`（`"^FD" + value + "^FS"`，text 分支无任何转义）、`LabelRenderService.java:124-129`（barcode `^BCN` 直拼）、`LabelRenderService.java:130-132`（qrcode `^FDQA,` + value 直拼）、对照 `LabelRenderService.java:160-163`（TSPL 有 `escapeTspl`）
- **现状**: TSPL 分支做了字面量转义，ZPL 分支完全没做。字段值来源 `SnLabelPrintEventListener.buildFields`（第 71-79 行）：`snCode/materialCode/materialName/woNo/cardNo` —— 物料名称、工单号等可由业务人员在主数据/工单中自由填写。
- **触发路径**: 把某物料名称改为 `X^FS^FO0,0^A0N,60,60^FDSCRAP^FS^XZ`（或在工单号注入 `~JR`、`^LL0`、`^PW0`）→ 造码时渲染进 ZPL → 打印机执行注入指令：覆盖版式、提前 `^XZ` 截断、打印任意内容，甚至 `~JR` 复位。
- **后果**: 标签内容被篡改（可打印出与 SN 不符的料号/条码），或打印机被复位/配置被改；标签是产线防错与售后追溯的第一道凭证，被注入即防错失效。
- **修复建议**: `renderZpl` 中对所有注入 `^FD` 的值做转义/白名单（至少剥离 `^`、`~`、`\r`、`\n`）；`escapeTspl` 也应剥离 CR/LF（当前只处理 `\` 和 `"`，换行会把一条指令拆成两条）。抽 `sanitize(String)` 供两分支共用，并补"含 `^`/`~` 字段值"单测。
- **影响面**: `LabelRenderService`；所有 ZPL 模板；无 DDL；需补单测。
- **测试现状**: 无覆盖（该服务注释自称"纯函数可单测"，但 `src/test` 下未见对应测试）。

## 2. P1 详述

### MES-FLASH-04 刷写重试次数由客户端传入且无上限

- **证据**: `FlashRecordServiceImpl.java:568`（`record.setRetryCount(bo.getRetryCount() == null ? 0 : bo.getRetryCount())`）、`FlashRecordServiceImpl.java:513-529`（无"该 SN 该内容已 PASS 后禁止再报"校验）、`FlashRecordServiceImpl.java:384-392`（待刷判定取最新一条，`pendingFlag = !"PASS".equals(latestResult)`）
- **现状**: `retry_count` 完全是客户端自报值，服务端既不累加也不校验上限；同一 `(snCode, contentRefId)` 可无限次插入记录，且 PASS 之后再报 FAIL 会把该 SN 重新标为"待刷"（取最新一条判定）。
- **触发路径**: 设备/上位机循环上报，或对已 PASS 的 SN 再报一条 FAIL（`result=FAIL` 时 `verify` 不拦截，第 548-550 行只在 PASS 时校验）→ 该 SN 重新变为待刷、门禁失效。
- **后果**: ① 刷写记录表被同一 SN 反复写入膨胀；② 刷写次数/良率统计无意义；③ 有权限者可用一条 FAIL 记录把已完成门禁的 SN 打回待刷状态。
- **修复建议**: 服务端按 `(snCode, flashTypeCode, contentRefId)` 统计历史条数写入 `retry_count`（不信客户端），超阈值（如 3 次）按配置拒绝或强制转 NCR；对已存在 PASS 的 `(sn, content)` 再报 FAIL 时要求 `failReason` 并写审计日志。
- **影响面**: `FlashRecordServiceImpl`、上位机契约（`retryCount` 可废弃）；无 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-FLASH-05 刷写类型禁用状态不生效 + 三个 DDL 列是死列

- **证据**: `FlashRecordServiceImpl.java:523-527`（`mesFlashTypeMapper.selectOne(eq(typeCode))` — **未过滤 `status`**）、`script/sql/ry-mes.sql:778-782`（`source_provider`/`verify_strategy`/`gate_enabled`/`status`）、`MesFlashType.java:35,41,44`、全仓 grep `gateEnabled|verifyStrategy|sourceProvider|getVerifyStrategy|getSourceProvider` 仅命中实体本身
- **现状**: `status`（ENABLED/DISABLED）、`gate_enabled`、`verify_strategy`、`source_provider` 四列运行期无人读取：类型注册按 Bean 的 `getTypeCode()`（`FlashTypeRegistry.java:31-42`），校验策略按 Provider 硬编码，门禁按工序绑定（不看 `gate_enabled`）。
- **触发路径**: 管理员把某刷写类型置 `DISABLED`（或 `gate_enabled` 置 0 期望"不参与完工门禁"）→ 刷写照常可用、门禁照常拦截。
- **后果**: 停用/门禁开关是假的，管理员以为已停用的类型仍可写库并通过门禁；属"可绕过的业务漏洞"。
- **修复建议**: ① `buildValidatedRecord` 查类型时加 `.eq(MesFlashType::getStatus, "ENABLED")`；② 门禁按绑定项 `flashTypeId` 过滤 `gate_enabled='1'`；③ 或删除这三列（需 DDL + 前端表单同步）。
- **影响面**: `FlashRecordServiceImpl`、`ProFlowCardServiceImpl`、前端刷写类型配置页；视方案决定是否 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-FLASH-06 池资源只分配不回收；`USED` 死状态；池选择无排序

- **证据**: `MesResourcePoolServiceImpl.java:155-191`（CAS 置 ALLOCATED + `bound_sn_id`、`used_count + 1`；**无任何释放方法**）、`MesResourcePoolServiceImpl.java:162`（幂等查询 `in(STATUS_ALLOCATED, "USED")` — 无代码把 status 置 `USED`）、`MacPoolFlashProvider.java:63-72`（`resolvePoolId` 用 `.last("LIMIT 1")` 且无 `ORDER BY`）、`IMesResourcePoolService`（无 release/回收接口）
- **现状**: 资源一旦分配即永久占用；刷写 FAIL、SN 报废、换板、返工均不释放；`SCRAPPED`/`USED` 无写入点；多池并存时取哪个池由 MySQL 返回顺序决定。
- **触发路径**: ① 某 SN 刷 MAC 失败（`result=FAIL`）→ MAC 已 ALLOCATED 并 bound 到该 SN，该 SN 报废后此 MAC 再也无法给别的设备用；② 建了两个 MAC 池（不同产品/供应商）→ 刷写可能从错误池取号。
- **后果**: 池被 FAIL/报废 SN 逐步吃掉，`used_count`/`total_count` 失真（可用量虚低），产线出现"池里明明有号却报无可用资源"；跨产品取号导致号段/供应商归属错乱。
- **修复建议**: 新增 `release(poolId, snId, reason)`（CAS `ALLOCATED→AVAILABLE` 并清 `bound_sn_id`、`used_count - 1`），在 FAIL 落库后、SN 报废/解绑时调用；`resolvePoolId` 改为按绑定指定的 poolId 取池，退化为 `ORDER BY create_time ASC`；明确 `USED` 语义或从查询移除。
- **影响面**: `MesResourcePoolServiceImpl`、`MacPoolFlashProvider`/`SnPoolFlashProvider`、`FlashRecordServiceImpl`（FAIL 分支）；无 DDL；需补并发分配 + 释放单测。
- **测试现状**: 无覆盖。

### MES-FLASH-07 资源池外部来源（EXTERNAL）完全未实现

- **证据**: `script/sql/ry-mes.sql:1255-1256`（`source` 注释"INTERNAL(内部导入)/EXTERNAL(外部REST)"、`external_config` 注释"外部REST配置JSON…经SSRF校验"）、`MesResourcePoolServiceImpl.java:69-70`（`source`/`external_config` 仅从 BO 存取，无消费）、全包无 `external_config` 业务使用
- **现状**: 池的 EXTERNAL 分支只有存储没有实现：不拉取、不校验 SSRF、不缓存；池耗尽时 `MesResourcePoolServiceImpl.java:172-174` 直接抛"资源池无可用资源"。
- **触发路径**: 建 `source=EXTERNAL` 的 MAC 池并配 `external_config`（期望从上游 MAC 管理系统取号）→ 池内无 AVAILABLE 项 → 刷写失败且原因指向"池无可用资源"，与配置预期不符。
- **后果**: 文档/DDL 声称支持的外部池是空壳；依赖外部号源时无法上线，且池耗尽无降级路径（见 MES-FLASH-08）。
- **修复建议**: 要么实现 EXTERNAL 拉取（复用 `ExternalRestFlashProvider.checkSsrf` + 超时/重试 + 结果落 `mes_resource_item` 后走同一 CAS 分配），要么在 `insertByBo` 拒绝 `source=EXTERNAL`（fail-fast 而非静默存下）。
- **影响面**: `MesResourcePoolServiceImpl`、`ExternalRestFlashProvider`、前端资源池表单；无需 DDL；需补集成测试。
- **测试现状**: 无覆盖。

### MES-FLASH-08 外部 REST provider 无重试/无降级、SSRF 可被重定向绕过

- **证据**: `ExternalRestFlashProvider.java:71-80`（`HttpUtil.createGet(url).timeout(5000)` + `req.execute().body()`，单次调用、无重试、无降级、响应原文直接进 `contentPayload`）、`ExternalRestFlashProvider.java:96-105`（`checkSsrf` 只解析 URL 字面主机名）、`ExternalRestFlashProvider.java:84-91`（`verify` 无条件 `pass(true)`）
- **现状**: ① 上游超时/5xx 时 Hutool 抛 `IORuntimeException`（非 `ServiceException`）→ 冒泡成 500，异常文本可能含上游地址；② `checkSsrf` 用 `InetAddress.getByName` 校验后实际连接会**重新解析 DNS 且 Hutool 默认跟随重定向**，攻击者用公网域名 + 302 指向 `http://127.0.0.1:8080/...` 即可绕过；③ 拿到什么存什么，不与设备实际写入值比对（同 MES-FLASH-02）。
- **触发路径**: `mes_flash_type.source_config` 配 `{"url":"http://attacker.example/x"}`，该地址 302 到内网 → 服务端发起内网请求（SSRF）；或上游抖动 → 刷写整体失败并返回 500。
- **后果**: SSRF 可达内网/云元数据（配合 `source_config` 写权限）；上游不可用即产线刷写中断且无降级；`content_payload` 可能存下 HTML 错误页而非真实内容。
- **修复建议**: ① `followRedirect(false)` 或逐跳重新做 SSRF 校验；② 校验解析后的 IP 并**用该 IP 直连**（避免 DNS rebinding）；③ 加 2~3 次带退避重试 + 熔断，失败抛 `ServiceException`；④ 响应做 JSON 结构与长度校验后入库。
- **影响面**: `ExternalRestFlashProvider`；无 DDL；需补 SSRF/重定向单测。
- **测试现状**: 无覆盖。

### MES-FLASH-09 免登录通道可伪造操作人留痕

- **证据**: `FlashRecordServiceImpl.java:615-628`（`applyOperator`：未登录时直接采用 BO 的 `operatorId`/`operatorName`/`operatorCode`）、`FlashReportController.java:64-71`（上报入口无登录态，仅共享 API-Key）、`TestRecordServiceImpl.java:395-402`（测试上报同型：`testerId`/`testerName` 取自 BO）
- **现状**: 设备通道下操作人完全由请求体决定，服务端不校验工号是否存在、是否属于该工位/租户。
- **触发路径**: 用 MES-FLASH-01 泄露的共享 key 上报，`operatorCode` 填他人工号 → 记录里 `operator_name` 变成该工号/姓名。
- **后果**: 刷写/测试记录的"谁做的"不可信，异常追责无法定位到人；配合 MES-FLASH-01 可完全伪造一条"某人刷写 PASS"的记录。
- **修复建议**: 设备通道改为按设备凭证（设备编码 + 独立密钥）识别来源，操作人通过工号反查校验（不存在则拒绝或落 `operator_code` 并标记"未验证"），不要直接把 BO 姓名写进 `operator_name`。
- **影响面**: `FlashRecordServiceImpl`、`TestRecordServiceImpl`、上位机契约；无 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-FLASH-10 设备上报强制落租户 000000

- **证据**: `FlashReportController.java:68-69`（`TenantHelper.dynamic("000000", () -> mesFlashRecordService.reportFlashBatch(list))`）、`FlashReportController.java:82-83`（测试上报同型）、对照 `MaintenanceDueTask.java:77`（同仓库定时任务做法是"遍历所有有效租户逐个注入上下文"，未硬编码）
- **现状**: 上报接口把租户硬编码为 `000000`，不解析请求上下文/设备归属租户。
- **触发路径**: 租户 A（非 000000）的产线设备调用上报 → 记录写入 `tenant_id='000000'`。
- **后果**: 跨租户数据污染：租户 A 的刷写/测试记录出现在 000000 下；门禁查询按当前租户过滤时查不到这些记录 → 该 SN 永远"未刷写"、工单卡死。
- **修复建议**: 按设备编码反查所属租户（`mes_station_device`/`eq_equipment` 带 `tenant_id`）后 `TenantHelper.dynamic(deviceTenantId, ...)`；或要求上报头带租户标识并在密钥校验时一并验证。
- **影响面**: `FlashReportController`；无 DDL；需补多租户单测。
- **测试现状**: 无覆盖。

### MES-FLASH-11 `mes.flash-step-gate` 配置项是空开关

- **证据**: `application-prod.yml:4-5`（`# 刷写工序门禁: ... (关掉置 false)` + `flash-step-gate: true`）、全仓 grep `flash-step-gate` 仅命中该 yml（无 Java 读取）、`ProFlowCardServiceImpl.java:84-86`（真实开关 `@Value("${mes.flash.gate.mode:FORCE}")`）、`ProFlowCardServiceImpl.java:350-354`（`FORCE`/`WARN` 之外视为关闭）
- **现状**: 生产配置里写的开关名与代码读取的键名不一致，`mes.flash-step-gate` 无任何消费者。
- **触发路径**: 按 `application-prod.yml:4` 注释把 `flash-step-gate` 置 `false`（期望关闭门禁）→ 重启后门禁仍为 FORCE，产线继续被拦截；反之运维以为自己已关掉，排查方向完全错误。
- **后果**: 配置与代码契约断裂，故障时按注释操作无效，排障时间被拉长；属"文档/配置声称已做但链路未闭合"。
- **修复建议**: 删除 `flash-step-gate` 并在同处改为 `mes.flash.gate.mode: FORCE`（注释同步），或在代码里同时支持该键。
- **影响面**: `application-prod.yml`（配置，审计期不可改）、`ProFlowCardServiceImpl`；无 DDL。
- **测试现状**: 不适用。

### MES-TEST-02 ENUM 判定 fail-open

- **证据**: `TestRecordServiceImpl.java:465-483`（`passEnum` 为空 → 第 469-471 行 `return RESULT_PASS`；`JSONUtil.parseArray` 抛异常 → 第 479-482 行 `return RESULT_PASS`）
- **现状**: 两条失败路径都返回 PASS。配置侧 `TestItemServiceImpl.java:170` 对 ENUM 强制要求配 `passEnum`，故运行时为空只可能来自历史数据/直接改库/导入；解析失败同理。
- **触发路径**: 历史 ENUM 测试项（或有人手工清空 `pass_enum`）→ 设备上报任意文本值（如 `"NG"`）→ 判定 PASS。
- **后果**: 不合格品被判合格入库，且数据上完全看不出（记录里 `pass_enum` 为空但 `result=PASS`）。
- **修复建议**: 两条路径都改为 `RESULT_FAIL`（fail-closed），解析失败打 error 日志 + 写 `remark`；对存量空 `pass_enum` 的 ENUM 测试项做一次数据体检。
- **影响面**: `TestRecordServiceImpl`、存量 `mes_test_item` 数据；无 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-TEST-03 已判定测试行可被反复覆盖改写

- **证据**: `TestRecordServiceImpl.java:194-221`（`reportTestRows`：`selectById` → 直接 `setNumericValue/setTextValue/setResult/setTestTime` → `updateById`，**无 `result` 非空校验、无状态守卫、无留痕**）、对照 `TestRecordServiceImpl.java:232-242`（删除路径反而有"已判定禁止删除"守卫）
- **现状**: 同一 `testRecordId` 可被无限次重报，实测值、判定结果、测试时间全部被覆盖，历史值不保留。
- **触发路径**: 某 SN 某项实测超上限判 FAIL → 重新提交合规值 → 该行变 PASS，`test_time` 被更新，原 FAIL 无任何痕迹。
- **后果**: 测试数据可篡改且不可审计；质量数据失去证据效力；与"已判定禁止删除"的追溯意图自相矛盾。
- **修复建议**: `reportTestRows` 增加 `if (StringUtils.isNotBlank(record.getResult())) throw new ServiceException("该行已判定, 如需重测请重新开单")`；重测走"新开 testNo"保留历史行，或新增 `mes_test_record_log` 记录旧值/新值/操作人。
- **影响面**: `TestRecordServiceImpl`、前端测试填报页（需提示"重测请重新开单"）；②方案需 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-TEST-04 测试 FAIL 不触发 NCR/返工/SN 状态变更

- **证据**: `TestRecordServiceImpl.java:405-414`（判定后仅 `updateById` 写回该行）、`TestRecordServiceImpl.java:283-310`（AUTO 批量仅收集 success/failed）、`mes/flash` 与 `mes/label` 全包 grep `Ncr|rework|返工|不良` **零命中**（命中全在 `mes/ncr`、`mes/qc`、`mes/pro`、`mes/spc`）、对照 `INcrOrderService.java:192-201`（已具备"工序出站不良自动建 NCR"能力）
- **现状**: 测试判定结果与质量闭环完全脱节：测试 FAIL 只写 `mes_test_record.result='FAIL'`，不建 NCR、不派生返工工单、不改 `pro_sn.sn_status`、不触发安灯/MQ。
- **触发路径**: SN 某项测试 FAIL → 记录落 FAIL → 该 SN 仍可继续过站/完工（没有任何门禁读取 `mes_test_record`，`checkFlashStepGate` 只读 `mes_flash_record`）。
- **后果**: 不良品继续流入下道工序与出货；NCR/返工/8D 链路对测试环节完全缺失（现有 `autoCreateFrom*` 只覆盖 IQC/IPQC/OQC/工序出站），质量数据与处置数据无法对齐。
- **修复建议**: 判定出 FAIL 时调用 `INcrOrderService.autoCreateFromExit(...)` 或新增 `autoCreateFromTest(snId, testNo, itemCode, ...)`（注意事务边界与幂等：同一 testNo 多项 FAIL 只建一单）；给工序/工单完工增加"该 SN 无未处置 FAIL 测试行"的门禁。
- **影响面**: `TestRecordServiceImpl`、`ncr` 模块、`ProFlowCardServiceImpl` 门禁；无 DDL（复用 NCR 表）；需补集成测试。
- **测试现状**: 无覆盖。

### MES-TEST-05 资源池"手工分配"端点前端不可达

- **证据**: `MesResourcePoolController.java:69-70`（`@PostMapping("/allocate")` + `@SaCheckPermission("mes:flash:pool:edit")`）、`plus-ui/src/api/mes/flash/pool.ts:38-55`（仅 list/get/add/update/delete/import 六个函数，**无 allocate**）
- **现状**: 后端提供了手工把资源项分配给某 SN 的接口，前端 api 层无对应函数，页面无从调用（规范 §2.1"后端有端点但前端无函数 → UI 不可达"）。
- **触发路径**: 产线需人工补号（池自动分配取错号 / 需指定某 MAC）时，界面无任何入口。
- **后果**: 手工分配/纠错能力不可用，只能改库；也意味着 MES-FLASH-06（无回收/无重分配）在 UI 侧同样无补救手段。
- **修复建议**: `pool.ts` 补 `allocatePoolResource(data)`，资源池详情页提供"手工分配/改绑"按钮（权限沿用 `mes:flash:pool:edit`，种子已存在）。
- **影响面**: `plus-ui/src/api/mes/flash/pool.ts` + 资源池页面；无 DDL。
- **测试现状**: 不适用。

### MES-LABEL-02 打印结果不落库、无重打计数、多模板重复打印

- **证据**: `SnLabelPrintEventListener.java:45-63`（取**全部**启用中的 SN 模板逐个 `submit`）、`LabelPrintService.java:47-56`（异步提交，异常仅 `log.warn`）、`LabelPrintService.java:63-82`（`send` 只 `log.info`/`log.warn`，**无任何持久化**）、`plus-ui/src/api/mes/label/template.ts:57`（仅"测试打印"）
- **现状**: 没有标签打印记录表/字段：打印成功、失败、次数、重打、操作人全部只进日志。造码事件对每个 `enabled='Y'` 的 SN 模板各打一张（N 个模板 = N 张不同版式），且 `SnCreatedEvent` 重投/重试会再打一轮（无幂等键）。
- **触发路径**: ① 同时启用"内箱标签 + 外箱标签"两个 SN 模板 → 每个 SN 造码即打两张；② 事件重投或用户重复点"生成SN" → 重复打印，无记录可查；③ 打印机离线 → 只留一条 warn 日志。
- **后果**: 标签耗材与产出不符无法对账；重打/漏打纠纷无数据支撑；多模板场景下"一个 SN 打了几张"无人知晓。
- **修复建议**: 新增 `mes_label_print_log`（tenant_id/sn_code/template_id/print_time/result/retry_count/operator/printer_host）落库；造码打印加幂等键（`sn_id + template_id` 唯一），重打走显式"补打"入口并累加计数；打印机离线升级为可查询失败记录 + 看板告警。
- **影响面**: `SnLabelPrintEventListener`、`LabelPrintService`、新表 + 前端打印记录页；需 DDL；需补单测。
- **测试现状**: 无覆盖。

### MES-LABEL-03 模板非法 JSON 导致标签静默不打印

- **证据**: `LabelRenderService.java:143-149`（`parseContent` 捕获异常后抛 `ServiceException("模板内容不是合法的 JSON")`）、`SnLabelPrintEventListener.java:58-67`（渲染在 `try` 内，任何异常被第 64-67 行 `catch (Exception e) { log.warn(...) }` 吞掉，不外抛、不重试、不落库）、`SnLabelPrintEventListener.java:42`（`AFTER_COMMIT` 监听器，异常本就不会回滚造码）
- **现状**: 造码成功后，若模板 JSON 非法（或字段绑定缺失 `field`、打印项类型非法 `LabelRenderService.java:98/133`），监听器只写一条 warn，SN 已生成但标签没打。
- **触发路径**: 管理员保存了非法 JSON 模板（`enabled='Y'`）→ 后续所有造码都不打标签，产线只看到"没出标签"，日志里一条 warn（即审计任务提到的那条 `模板数据不是合法的 JSON` 告警的成因）。
- **后果**: 批量漏打标签且无人被通知；SN 造码本身成功，业务侧无异常信号，漏打到下游工序/出货才发现。
- **修复建议**: ① 模板保存时即校验 JSON 合法性 + 试渲染（`MesLabelTemplateServiceImpl` 的 insert/update 调 `renderService.parseContent` 并校验字段非空），非法拒绝保存；② 监听器把渲染失败写入打印记录表（配合 MES-LABEL-02）并推送告警，而非只 warn。
- **影响面**: `MesLabelTemplateServiceImpl`、`SnLabelPrintEventListener`、标签模板表单；无 DDL（若不建打印记录表）；需补单测。
- **测试现状**: 无覆盖。

### MES-STATION-01 关键件绑定 CAS 未校验影响行数

- **证据**: `StationSnService.java:85-91`（`proSnMapper.update(...).eq(snId).isNull(parentSnId)` 之后**不读取返回行数**，直接 `insertLog("BIND", ...)` + `bound++`）、`StationSnService.java:75-78`（先查 `parentSnId != null` 再更新——先查后写 TOCTOU）、`script/sql/ry-mes.sql:1295-1299`（`mes_sn_bind_log` 无任何唯一约束）
- **现状**: 并发两个请求把同一组件绑定到不同父 SN 时：两者都在第 75 行读到 `parentSnId == null`，随后 CAS 只有一个能更新成功，但**两个请求都会写 BIND 日志并把 boundQty 加 1**。
- **触发路径**: 两个工位/两条产线同时对同一组件 SN 绑定（或用户双击提交）→ 返回 `boundQty=2`，`mes_sn_bind_log` 出现两条互相矛盾的 BIND 记录，而 `pro_sn.parent_sn_id` 只有一个值。
- **后果**: 绑定日志与真实父子关系不一致（正反向一致性被破坏），追溯查询（`StationTraceService.java:223-228` 同时按 parent/component 查日志）会展示错误的绑定历史；`boundQty` 虚高。
- **修复建议**: `rows == 0` 时抛 `ServiceException("组件 SN 已被并发绑定, 请刷新重试")` 且**不写日志**；`insertLog` 前必须已确认 `rows == 1`。可另加 `(tenant_id, parent_sn_id, component_sn_id, action, create_time)` 索引辅助对账。
- **影响面**: `StationSnService`；无 DDL（索引可选）；已有 `StationSnServiceTest`（`ruoyi-modules/ruoyi-station/src/test/java/com/nexus/station/service/StationSnServiceTest.java:85-165`）可扩展并发用例。
- **测试现状**: 有单测但未覆盖 rows==0 分支。

## 3. P2 详述

| 编号 | 类型 | 结论 | 证据 |
|---|---|---|---|
| MES-FLASH-12 | 事务 | `reportFlashBatch`/`reportTestAutoBatch` 在**单个外层事务**内逐条 insert 并本地 catch 异常：若某条抛 `DataAccessException`，外层事务可能已被标记 rollback-only，最终 commit 抛 `UnexpectedRollbackException`，但返回值已报 `success=N` → 前端显示"成功 N 条"而实际一条未落库 | `FlashRecordServiceImpl.java:212-238`、`TestRecordServiceImpl.java:283-310` |
| MES-TEST-06 | 统计口径 | `summary` 的 `judged` 用 `isNotNull(result)`，但待测行 `result=''`（非 NULL，见 `beginTest` 第 166 行注释）→ `judged` 恒等于 `total`，未测行被当作"已判定未通过"，通过率被系统性拉低 | `TestRecordServiceImpl.java:257-277`、`TestRecordServiceImpl.java:166` |
| MES-TEST-07 | 校验 | `beginTest` 允许用**未发布（DRAFT）**固件开测试单并回填版本（注释明示"测试允许候选固件，不强制 RELEASED"），测试记录可能关联未评审版本 | `TestRecordServiceImpl.java:118-126` |
| MES-TEST-08 | 契约 | 测试记录与刷写记录无关联字段（`mes_test_record` 只有 `content_ref_id/content_version` 快照，无 `flash_record_id`），无法证明"测的就是这台设备实际刷的那一版" | `script/sql/ry-mes.sql:1433-1434` |
| MES-FLASH-13 | DDL | `mes_flash_record` 无 `(tenant_id, sn_id, flash_type_code, content_ref_id)` 唯一/组合索引；`idx_content_ref` 缺 `tenant_id` 前缀（门禁按 contentRefId 高频反查）；`flash_type_code` 无索引 | `script/sql/ry-mes.sql:759-765` |
| MES-FLASH-14 | 性能 | `selectPendingTasks` 对每个 (SN × 绑定项) 单独 `selectOne` 取最新记录（N+1），工单 SN 数上百时单次请求数百次查询 | `FlashRecordServiceImpl.java:384-390` |
| MES-FLASH-15 | 契约 | `MesFlashType.source_provider`（注释"SPI Provider bean名"）与实际路由方式（按 Bean `getTypeCode()`）不一致，配错该列不影响行为也不报错 | `script/sql/ry-mes.sql:778`、`FlashTypeRegistry.java:31-42` |
| MES-LABEL-04 | 健壮性 | 打印线程池为无界队列单线程，打印机长期离线时任务持续堆积（内存增长），无队列上限/丢弃策略 | `LabelPrintService.java:32-36`、`LabelPrintService.java:47-56` |
| MES-LABEL-05 | 健壮性 | 打印 TCP 只写不读回执即视为成功（`send` 返回 true），打印机实际报错（缺纸/开盖）无法感知 | `LabelPrintService.java:68-81` |
| MES-STATION-02 | 校验 | `bind` 的组件集合用 `LinkedHashSet` 去重，第 79-81 行只拦"组件==父SN"，未拦"父 SN 也是本次组件之一"的组合环（`checkCycle` 逐组件校验，跨组件成环未覆盖） | `StationSnService.java:57-83` |
| MES-FLASH-16 | 契约 | `MesContentFile`（配置档案）无文件上传端点，`fileMd5/version` 只能手工填，配置文件的"内容校验值"失去防错意义（对照固件有 `/upload`） | `MesContentFileController.java:29-61`（无 `@PostMapping("/upload")`）、`FirmwareController.java:89-91` |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 测试 FAIL → NCR/返工联动 | `mes/flash` 全包无 `Ncr` 引用（grep 零命中） | `INcrOrderService.autoCreateFromExit/autoCreateFromIpqc`、`mes_ncr_order`/`mes_ncr_rework_order` | 小（1 方法 + 调用点 + 幂等键），0.5~1 人日 |
| 2 | 池资源释放/回收/换号 | `IMesResourcePoolService` 无 release；`USED` 无写入点 | `mes_resource_item.status`(AVAILABLE/ALLOCATED/USED/SCRAPPED)、`bound_sn_id`、`bound_time` | 小~中（含并发用例），1~2 人日 |
| 3 | 资源池 EXTERNAL 拉取 | `MesResourcePoolServiceImpl.java:69` 仅存 `external_config` | `mes_resource_pool.source/external_config`、`ExternalRestFlashProvider.checkSsrf` | 中（拉取 + SSRF + 重试 + 落库），2~3 人日 |
| 4 | 标签打印记录/重打计数/补打 | 打印结果仅日志；无 print 表 | `mes_label_template`、`LabelPrintService`、`SnCreatedEvent` | 中（新表 + 幂等 + 前端页），2~3 人日 |
| 5 | 测试单级整体结论 + 完工门禁读测试结果 | 无单头表/字段；`checkFlashStepGate` 只读 `mes_flash_record` | `mes_test_record.test_no`（已按单号聚合多行） | 中（聚合判定 + 门禁扩展），2 人日 |
| 6 | 手工分配/改绑资源 UI | 后端 `/allocate` 已存在 | `MesResourcePoolController.java:69`、`mes:flash:pool:edit` 权限种子（`ry-vue.sql:92615`） | 小（前端 1 函数 + 按钮），0.5 人日 |
| 7 | 配置档案文件上传 | `MesContentFileController` 无 upload | `MesContentFile`（version/fileMd5）、固件 `/upload` 可复用 | 小，0.5~1 人日 |
| 8 | 设备级独立凭证（替代共享 API-Key） | 单一 `mes.api-key` 共享密钥 | `mes_station_device`（device_code/station_code）、`eq_equipment` | 中（新表/新列 + 校验），2 人日 |

## 5. 已核查确认无问题（证明覆盖范围）

- **API-Key fail-closed 逻辑本体**：`FlashReportController.java:56` 为 `@Value("${mes.api-key:}")`（无代码内默认值），第 106-110 行空即拒绝、不匹配即拒绝 → **09-06 报告 §2.1 N1 复核：注解默认值确已去除（原判定已修复）**；但 `application-prod.yml:3` 仍有字面量，见 MES-FLASH-01。
- **资源分配 CAS 原子性**：`MesResourcePoolServiceImpl.java:167-189` 为"查 AVAILABLE → 条件更新 `eq(itemId).eq(status=AVAILABLE)` → 判 rows"的 CAS + 3 次重试，**并发不会把同一资源项发给两个 SN**；同 SN 幂等（第 158-166 行先查 `bound_sn_id`）。
- **固件刷写防错三层**：`FirmwareFileFlashProvider.java:49-55`（RELEASED + 物料匹配）、`:72-75`（MD5 不匹配判 FAIL）、`FlashRecordServiceImpl.java:451-496`（SN 当前工序须为刷写工序且绑定匹配）。
- **刷写门禁本体**：`ProFlowCardServiceImpl.java:350-404` 实现 FORCE/WARN/关闭三态；刷写工序无 SN 时 FORCE 拦截（防派工绕过）；多类型绑定逐 (绑定项 × SN) 校验最新 PASS；报工与派工聚合完工两条路径都调用（`:279`、`:569`）。
- **测试项配置校验**：`TestItemServiceImpl.java:149-182`（类型合法、NUMERIC 须有上下限、下限≤上限、目标值在区间内、ENUM 须配合格值、同物料同固件同编码唯一）。
- **测试记录追溯守卫**：`TestRecordServiceImpl.java:232-242` 已判定行禁止删除；`TestItemServiceImpl.java:122` 被测试记录引用的测试项禁止删除。
- **唯一约束核查（DDL）**：`mes_resource_item` `uk_tenant_pool_value`（`ry-mes.sql:1240`）、`mes_resource_pool` `uk_tenant_pool_code`（`:1269`）、`mes_flash_record` `uk_tenant_flash_no`（`:759`）、`mes_test_record` `uk_tenant_test_no_item`（`:1461`）均存在且带 `tenant_id` 前缀；`mes_sn_bind_log` **无唯一约束**（影响见 MES-STATION-01）。
- **工位绑定防错**：`StationSnService.java:75-89` 已拦截重复绑定、组件==父SN、父链防环（`checkCycle`），更新用 `.isNull(parentSnId)` CAS。
- **标签 TSPL 分支**：`LabelRenderService.java:161-163` 对 `\` 与 `"` 转义；`:66-69` 按语言选 GBK/UTF-8（中文点阵字体与 `^CI28` 匹配）；`:98/:133` 未知类型抛异常而非静默。
- **打印旁路不阻断主流程**：`SnLabelPrintEventListener.java:42`（AFTER_COMMIT）、`:64-67`（异常不外抛）、`LabelPrintService.java:51-54`（异步线程池异常吞掉）——设计意图正确（缺的是失败可见性，见 MES-LABEL-02/03）。
- **定时任务机制**：`flash`/`label` 包内**无** `@Scheduled`（grep 确认），不存在"生产 `snail-job.enabled:false` 导致 `@Scheduled` 不执行"的问题；刷写/标签链路全部由请求或事务事件驱动。
- **权限码种子**：`mes:flash:firmware:*`（`ry-vue.sql:92601-92611`）、`mes:flash:pool:*`（`:92612-92617`）、`mes:flash:content:*`（`:92618-92623`）、`mes:flash:flash:*`（`:92863-92868`）、`mes:flash:testitem:*`/`mes:flash:testrecord:*`（`:92869-92877`）、`mes:label:template:*`（`:92559-92564`）**均已在 `sys_menu` 落种子**，与各 Controller 的 `@SaCheckPermission` 逐条比对一致，无"除超管外 403"缺口。
- **前端 api 层覆盖**：`plus-ui/src/api/mes/flash/{firmware,flash,pool,content,testItem,testRecord}.ts` 与 `mes/label/template.ts` 已覆盖全部后端端点，**唯一缺口是 `/mes/flash/pool/allocate`**（见 MES-TEST-05）。
- **CvLabelParseService 与 MES 标签模板无契约耦合**：`CvLabelParseService` 位于 `ruoyi-erp/cv`（`CvLabelParseService.java:36`），仅由 `CvDetectController.java:45-55` 在 `enableLabelParse=true` 时调用，输出"料号/批次/SN"解析结果；与 MES 标签模板（字段绑定 `snCode/materialCode/...`、服务端渲染 TSPL/ZPL）是两条独立链路，未发现字段或契约不一致。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `POST /mes/flash/pool/allocate` | `MesResourcePoolController.java:69`（`@PostMapping("/allocate")`） | `plus-ui/src/api/mes/flash/pool.ts` 无函数 | 后端有、前端无 → UI 不可达（MES-TEST-05） |
| `FlashRecordBo.contentPayload` | `FlashRecordBo.java:81`（注释"实际写入内容JSON"）、`FlashRecord.java:74`、`FlashRecordVo.java:78` | 前端 `.vue` 表单未核（本报告未读） | 后端语义为"实际值"但服务端写的是"应刷值"（MES-FLASH-02） |
| `mes_flash_type.gate_enabled` / `verify_strategy` / `source_provider` | `script/sql/ry-mes.sql:778-781`、`MesFlashType.java:35/41/44` | 未核 | 无任何读取方 → 死列（MES-FLASH-05） |
| `mes_resource_pool.source` / `external_config` | `script/sql/ry-mes.sql:1255-1256` | 未核 | `EXTERNAL` 分支无实现（MES-FLASH-07） |
| `mes.flash-step-gate` | `application-prod.yml:5`（配置项） | 不适用 | 无代码读取，真实键为 `mes.flash.gate.mode`（MES-FLASH-11） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交，建议立即）**：MES-FLASH-01（删 `application-prod.yml:3` 字面量、改环境变量注入）、MES-LABEL-01（ZPL `sanitize`）、MES-FLASH-02（池类 verify 比对实刷值）、MES-TEST-01（必测项覆盖校验）。前两条是"外部可伪造/可注入"，后两条是"数据失真"，互不依赖。
- **批次 B（P1 状态机与链路，需联调）**：MES-FLASH-03（统一 `content_ref_id` 语义 + 门禁 null 守卫，**决定池类刷写能否上线**，建议与 A 批同期评估）、MES-FLASH-04（retryCount 服务端化 + 上限）、MES-FLASH-05（类型状态生效）、MES-FLASH-06（池释放）、MES-TEST-02/03（判定 fail-closed + 已判定行不可改写）。
- **批次 C（P1 链路补齐，含 DDL）**：MES-TEST-04（测试 FAIL → NCR，需与 `ncr` 约定幂等键）、MES-LABEL-02（新增 `mes_label_print_log`，需 DDL）、MES-LABEL-03（模板保存即校验 JSON）、MES-FLASH-07/08（外部池与外部 REST 的取舍与加固）、MES-FLASH-09/10（设备身份与租户归属）、MES-STATION-01（rows 校验）、MES-FLASH-11（配置项改名，纯配置）、MES-TEST-05（前端补 allocate）。
- **批次 D（P2，技术债）**：§3 表格全部条目（事务边界、`judged` 口径、索引、N+1、打印回执、配置档案上传等）。
- **建议补测试清单**：`MacPoolFlashProvider.verify`（实刷值不一致）、`LabelRenderService`（ZPL 含 `^`/`~` 渲染）、`TestRecordServiceImpl.reportTestAuto`（缺项 / ENUM 空 passEnum）、`MesResourcePoolServiceImpl.allocate`（并发 + 释放）、`FlashRecordServiceImpl.reportFlashBatch`（部分失败时事务与返回值一致性）、`StationSnService.bind`（rows==0 分支）。

## 8. 未覆盖范围（自觉收尾声明）

- 未读 `flash`/`label` 的前端 `.vue` 页面（`plus-ui/src/views/mes/flash/**`、`views/mes/label/**`），故 §6 中前端表单/列表字段仅核到 api 层；"表格列缺失/宽度/表单分组"类前端布局问题不在本报告范围。
- 未读 `FirmwareServiceImpl`（539 行）、`MesContentFileServiceImpl`、`TestItemServiceImpl` 全文、`SnPoolFlashProvider` 全文、`MesLabelTemplateServiceImpl` 全文（后三者仅按 grep 命中行核对）。
- 未读 `ConfigFileFlashProvider`（按 `FirmwareFileFlashProvider` 同型推断，未取证，故未列发现）。
- 未核对 `ruoyi-station` 其余模块（过站报工 `mes_station_report`、工位终端免登录通道的越权面），仅覆盖 `StationSnService`/`StationTraceService` 与 `mes_sn_bind_log` 相关的绑定/追溯链路。
- 未核 `CvLabelParseService` 的解析规则细节与其上游 Vision 服务契约。
