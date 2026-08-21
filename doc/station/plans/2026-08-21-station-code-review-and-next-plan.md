# nexus-station × NexusERP 设备通道 — 双端代码审查与下一阶段开发计划

> **文档版本**: v1.0
> **审查日期**: 2026-08-21
> **审查范围**: 后端 commit `c3c27907`（C2-C12 + M1-a/b 全量交付, 2168 行新增）× nexus-station 客户端（M1/M2 全部契约调用方）
> **审查结论**: 🟡 契约覆盖完整、架构合理，但存在 **3 个跨端集成缺陷**（P0×1 / P1×2），必须修复后才能进入真机联调；另有 2 项次要改进建议。
> **前序文档**: [2026-08-21-station-device-channel-plan.md](./2026-08-21-station-device-channel-plan.md)

---

## 一、审查总览

### 1.1 契约对齐矩阵（12 项契约逐项核对）

| # | 契约 | URL | 请求字段 | 响应字段 | 结论 |
|---|------|-----|---------|---------|------|
| C1 | 扫工号上岗 | ✅ | ✅ | ✅（多返回 employeeId/workshopName, 客户端忽略, 向后兼容） | ✅ |
| C2 | 过站四重校验 | ✅ | ✅ `{snCode, stationCode}` | ✅ `{pass, snInfo, checks}` | ✅（但 QUALITY_HOLD 逻辑缺陷见 2.3） |
| C3 | 单件过站报工 | ✅ | ✅ 全字段对齐含 clientTs | ✅ | ✅ 幂等设计正确（预检+唯一索引双保险） |
| C4 | 批量计数报工 | ✅ | ✅ | ✅ | ✅（在制工序定位约束已在报错信息中说明） |
| C5 | 关键件绑定 | ✅ | ✅ | ✅ `{boundQty}` | ✅ 防环+留痕完整 |
| C6 | 解绑/换板 | ✅ | ✅ | ✅ | ✅ |
| C7 | 安灯呼叫 | ✅ | ✅ | ✅ `{andonNo}` | ✅ 四键双向映射正确 |
| C8 | 安灯状态 | ✅ | ✅ | ✅（CANCELLED 状态透传, 客户端类型已含） | ✅ |
| C9 | 不良代码 | ✅ | — | ✅ `{code, label}` | ✅ |
| C10 | 设备心跳 | ✅ | ✅ | ✅ | ✅（顺带维护工位↔工序绑定, 为 C2 提供解析依据, 设计好） |
| C11 | 综测上报 | ✅ | ✅ | — | ✅（既有端点, C12 字段已加且容忍） |
| M1-a | prepare 预取 | ✅ | ✅ | 🐛 **响应缺 R 包络**（见 2.1） | 🔴 阻断 |
| M1-b | 固件下载 | ✅ | — | 🐛 **firmwareUrl 为相对路径**（见 2.2） | 🔴 阻断 |

### 1.2 后端架构评价（好的方面）

- **幂等设计正确**：C3/C4 均为「应用层预检 + `uk_tenant_client_key` 唯一索引 + DuplicateKeyException 吞并返回成功」三层兜底，离线队列重放安全。这正是冒烟#6 的验收基础。
- **数量收口谨慎**：`completeStepIfQtyReached` 重查累计值、CAS(IN_PROGRESS→COMPLETED)、全卡完成才复用 `completeCard`，不与 PC 端 completeStep 语义冲突。
- **跨端一致性好**：工位码→工序码的解析规则（心跳绑定优先、`STN|车间|产线|工序|形态` 第 4 段兜底）与客户端 `config.ts` 注释一致。
- **`@DS` 使用正确**：StationPassService/StationSnService/StationAndonService 类级 `@DS("mes")`；固件下载 sys_oss 跨库用编程式 push/poll master（对齐 FirmwareServiceImpl 先例）。
- SQL 脚本可重复执行（IF NOT EXISTS + information_schema 判存）。

---

## 二、发现的缺陷（按严重度排序）

### 2.1 🔴 P0：prepare 响应缺少 R 包络，刷写作业链路被阻断

**位置**: `StationFlashPrepareController.prepare()`（`StationFlashPrepareController.java:84-95`）返回裸 `Map<String, Object>`。

**问题**: 客户端 `MesClient.request<T>`（`client.ts:211-224`）按 RuoYi 统一包络解析——`body.code !== 200` 即抛错。裸 Map 无 `code/data` 字段，客户端收到 HTTP 200 但解析失败抛 `MES 接口请求失败 (HTTP 200)`；且 `FlashJobRunner` 对 prepare 仅容忍 404（后端未上线时的降级路径），其他异常直接中止作业。

**后果**: prepare 端点上线后反而比 404 降级模式更糟——**FLASH 工位刷写闭环全链路失败**。这是本次交付最严重的回归。

**修复**（二选一，推荐 A）:
- A. 后端: `prepare` 返回 `R<Map<String,Object>>`，`return R.ok(data)`（对齐其余全部端点）。`data=null` 时返回 `R.ok(null)` 客户端 `?? {}` 已兼容。
- B. 客户端: request 增加「无包络但 2xx」裸 JSON 兜底。不推荐——破坏全项目统一契约。

### 2.2 🔴 P0：firmwareUrl 为相对路径，客户端 fetch 直接崩溃

**位置**: 后端 `doPrepare` 写死 `"/mes/flash/report/firmware/{id}/download"`（`StationFlashPrepareController.java:167`）；客户端 `FirmwareDownloader.download(url)` 直接 `fetch(url)`（`firmware.ts`，无 baseUrl 拼接），且 `new URL(url).pathname` 对相对路径也会异常。

**后果**: 固件类刷写内容下载必然失败（Node fetch 相对 URL 抛 `TypeError: Failed to parse URL`）。

**修复**（推荐客户端侧，职责更合理——URL 主机归属客户端环境）:
- 客户端 `FlashJobRunner` 下载前拼 `${baseUrl}${prepared.firmwareUrl}`（需把 baseUrl 传入 runner 或 downloader）。
- 或后端返回绝对 URL（需感知请求 Host，反向代理场景易出错，不推荐）。

### 2.3 🟠 P1：QUALITY_HOLD 把「存在历史 FAIL」误判为「最新综测 FAIL」

**位置**: `StationPassService.validate()` 校验4（`StationPassService.java:176-184`）：查询条件是 `result='FAIL'` 取最新一条，**只要历史上存在任何 FAIL 记录就永久扣押**——SN 复测 PASS 后仍被拦截，与契约「最新综测 FAIL」不符。

**修复**: 先取该 SN **最新一条测试记录（不限 result）**，再判断其 result 是否为 FAIL：
```java
TestRecord latest = testRecordMapper.selectOne(...eq(snCode).orderByDesc(testTime)...last("LIMIT 1"));
if (latest != null && "FAIL".equals(latest.getResult())) { holdOk = false; ... }
```

### 2.4 🟡 P2：次要改进（不阻塞联调）

| # | 位置 | 问题 | 建议 |
|---|------|------|------|
| P2-1 | `StationFlashPrepareController.isFirmwareType()` | 查了 `MesFlashType` 却只做 `"firmware_file".equals(typeCode)` 硬编码比较，查询结果未使用 | 改为按 `flashType.getSourceType()` 判断文件类（FIRMWARE/CONFIG_FILE），或删掉无用查询 |
| P2-2 | ruoyi-station 模块整体 | **零单元测试**（无 src/test）；而客户端 42 个用例全绿 | 至少补 StationPassService 幂等/四重校验、StationSnService 防环的单测（ruoyi-mes 已有 spring-boot-starter-test 先例） |
| P2-3 | `StationPassService.validate` DUPLICATE 校验 | 只查 mes_station_report（设备通道报工），PC 端 completeStep 报的过站不在此表，混用时防重不完整 | 联调阶段如出现「PC+工位机混报」场景，需合并查 pro_flow_card_step 已报数量；纯工位机场景可接受，文档已注明设备通道语义 |

---

## 三、下一阶段开发计划

### 阶段 0：联调阻断修复（0.5d，立即执行）

- [x] **F-1** prepare 响应补 R 包络（2.1，`StationFlashPrepareController.prepare` 返回 `R<Map>`）✅ 2026-08-21
- [x] **F-2** 客户端 firmwareUrl 拼接 baseUrl（2.2，`flash-job-runner.ts` 新增 `resolveUrl`（绝对 URL 直传/相对路径拼 baseUrl），`MesClient.baseUrl` 改 public readonly；新增 3 个单测）✅ 2026-08-21
- [x] **F-3** QUALITY_HOLD 改为「最新测试记录的 result」（2.3，去掉 `result='FAIL'` 查询条件，取最新一条再判定，复测 PASS 放行）✅ 2026-08-21
- [x] **F-4** P2-1 isFirmwareType 清理（删除无用 MesFlashType 查询与依赖注入）✅ 2026-08-21
- [x] 双端回归：后端 `mvn compile` BUILD SUCCESS + 客户端 `vitest run` 45 用例全绿（新增 3 例）+ `typecheck` 零错误 ✅ 2026-08-21

### 阶段 1：真机联调冒烟（1-2d，双端配合，对应 station 冒烟清单 10 项）

执行 `nexus-station/doc/plans/2026-08-20-M2-冒烟记录.md` 全部 10 项（后端契约已齐，首次可全量脱离降级模式）：

- [ ] 工位绑定 + 三形态切换
- [ ] 扫工号上岗（sch_worker 档案需有数据且关联 sys_user）
- [ ] 过站红绿屏四重校验（重点验证 QUALITY_HOLD 修复后的复测放行）
- [ ] 过站 FAIL 不良代码上报
- [ ] 批量计数 +1/+5/+10
- [ ] **断网过站 → 离线队列 → 恢复回放 → 后端幂等去重**（验收 `uk_tenant_client_key`：同一 clientKey 重放库中仅一条）
- [ ] 虚拟串口综测监听 → mes_test_record 落库
- [ ] 关键件绑定/解绑/换板（含防环负例：A绑B后B绑A应拒绝）
- [ ] 安灯四键呼叫（plus-ui 看板可见 SSE 推送）+ 刷写连续失败 3 次自动呼叫
- [ ] 心跳：`mes_station_device.last_heartbeat` 更新

前置数据准备：执行 `script/sql/update/2026-08-21-station-device-channel.sql`；造 sch_worker 在岗档案、在制流转卡+SN、RELEASED 固件各一条。

### 阶段 2：补测试与加固（1-2d，可与阶段 1 并行）✅ 2026-08-21 完成

- [x] 后端 ruoyi-station 单测：StationPassServiceTest 14 例（F-3/F-5/F-7 回归、四重校验矩阵、幂等重放、收口 CAS、工位码解析）+ StationSnServiceTest 7 例（防环/重复绑定/换板）——erp 提交 fcccba4f
- [x] 客户端：firmwareUrl 拼接回归用例（相对路径下载拼接）——station 提交 7c70865
- [x] 客户端加固：FirmwareDownloader 识别 HTTP 200 + JSON 错误体（冒烟报告遗留项）——station 提交 3617498
- [ ] 客户端既有优化项（择要，未排期）：API Key 存储改 safeStorage、离线队列加上限/TTL、electron-log 文件日志

### 阶段 3：M1 Task 8 自动更新 + 铺开准备（2-3d）✅ 2026-08-21 完成

- [x] electron-updater 接入：静默下载 + 夜间窗口安装(02:00-05:00) + 差量更新 + 失败静默 4h 重查——station 提交 ecd9d93，**M1 全部 8 任务闭环**
- [x] 心跳已上报 appVersion（C10 已含）→ 灰度发布的版本依据已就位
- [x] mes_station_device 在 plus-ui 加只读管理页：/mes/station-admin/device（登录鉴权，与设备通道白名单分离）+ 菜单「生产执行→工位终端」——erp 提交 1c265281

### 阶段 4：标签打印 + M3 立项（另行排期）

- 标签打印（M1 设计内未启动）✅ 2026-08-21 交付（SN 标签 v1，后端 TCP 9100 直发，客户端零改动）：
  - 触发修复（前置）：`ProSnServiceImpl.generateSnForCard/createSn` 改为**造码即发** `SnCreatedEvent`（原 lotId 非空才发导致事件几乎不触发）；`LtSnBindingEventListener` 补 lotId 空跳过守卫
  - 新增 `com.nexus.mes.label` 模块：`mes_label_template` 表（字段映射式模板 JSON + 打印机地址，增量脚本 `2026-08-21-mes-label-template.sql`）、`LabelRenderService`（TSPL/ZPL 纯字符串渲染，TSPL=GBK/TSS24.BF2 中文，ZPL=UTF-8/^CI28）、`LabelPrintService`（TCP RAW 9100 直发 + daemon 单线程池，失败仅告警）、`SnLabelPrintEventListener`（AFTER_COMMIT + fallbackExecution，打印失败不影响造码）
  - 管理端：`/mes/label/template` CRUD + `/{id}/preview` 指令预览 + `/{id}/test-print` 测试打印（权限 `mes:label:template:*`，菜单 4231「生产执行→标签模板」）；plus-ui 标签模板页（useCrud，打印项布局可视化编辑表格）
  - 单测：LabelRenderServiceTest 10 例 + SnLabelPrintEventListenerTest 6 例全绿
  - v1 边界：流转卡标签/USB·BLE 通道/hiprint 设计器/打印留痕表均不做
- M3 立项评审 ✅ 2026-08-21 盘点完成，按批次推进（**重要发现：电子 SOP 后端管理端已于 2026-07-26 完整交付**——sop_document/version/binding/view_log 四表 + Flowable 审批 + plus-ui 4 页面 + E2E 联调，设计文档"需新增 mes_sop_doc"的判断已过时，真正缺口是设备通道）：
  - **批次 2**（下一批）：追溯查询（按 SN 聚合 mes_flash_record/mes_test_record/mes_station_report/mes_sn_bind_log 全履历接口 + 设备通道 + 客户端页）/ 设备点检（设备通道端点 + eq_inspection_record 留痕表 + 当日任务提醒，复用 PC 端 submitInspection 的 FAULT+安灯联动）
  - **批次 3**：SOP 设备通道（免登录拉取 + OSS 流式代理仿固件下载 + 版本号轮询）+ 版本强控门禁（版本不符禁止开工）+ nexus-station SOP 全屏轮播页
  - **批次 4**：上料防错（mes_material_load 料站表 + BOM 比对含 bd_bom_alternative 替代料 + 客户端上料页）
- 待办数据项：ncr_defect_code 造 level=3 且 enabled='Y' 的三级不良代码 ✅ 2026-08-21 交付：`2026-08-21-ncr-defect-code-seed.sql`（6 一级 × 13 二级 × 48 三级，946xxx 固定 ID 段，INSERT IGNORE 可重复执行，软删存量 SURF/SURF9293 测试数据；注意 enabled 为 Y/N 口径非 DDL 注释的 1/0），C9 已脱离客户端静态兜底
- 待办验证项：Electron 页面级真机端到端冒烟（10 项清单，API 级已全通）；自动更新真机链路（latest.yml 发布目录搭建）；标签打印真机验证（测试打印按钮 + 打印机在线）
- 待办（M3）**工位终端 ↔ 工作中心设备联动视图** ✅ 2026-08-21 交付（评审结论：纯运维便利性增强，不加列不建新模型）：后端新增 `StationErpProcessMapper`（bd_process/bd_workstation 跨库只读，@DS("erp")）+ `StationDeviceLinkageService` + 两个管理端只读端点（正向 `GET /mes/station-admin/device/{deviceId}/equipments`、反向 `GET /mes/station-admin/device/by-workcenter/{workcenterId}`，均权限 `mes:station:device:list`）；plus-ui 工位终端台账页新增「工作中心设备」弹窗，eq_equipment 设备详情抽屉新增「工位终端」页签（checkPermi 守卫，展示工序集+工位机+在线状态）。关联链 `mes_station_device.process_code → bd_process 默认工作中心 → eq_equipment.workcenter_id`，零 schema 变更，与主流 MES 的 Work Center 模型同构（终端是工作中心属性，非独立资产）；单测 StationDeviceLinkageServiceTest 7 例（全链/无工作中心/工序码兜底解析/主数据已删/在线状态矩阵）
- 待办（M3）**工位机设备编码自动录入** ✅ 2026-08-21 交付：工位码扩展第 6 段设备号（`STN|车间|产线|工序|形态|设备号`，5 段码向后兼容）；客户端（nexus-station）设置页保存时自动回填 deviceCode（印刷码优先于手工值）并将 stationCode 归一化为前 5 段——设备号是设备属性而非工位标识，剥离后保证台账/安灯/报工记录的工位编码一致；后端 `StationPassService.normalizeStationCode` 防御性归一化（split limit=-1 对齐客户端语义），统一应用于心跳(C10)/过站校验(C2)/单件与批量报工(C3/C4)/安灯呼叫与查询(C7/C8)入口，防异常路径 6 段码落库分裂工位标识；单测 +3 例（6 段截断/5 段不变/非 STN 不动）

### 里程碑判定

**阶段 0+1 完成 = M2 正式闭环**（客户端三形态全功能脱离降级 + 真机验证 + 幂等验收）✅
**阶段 2+3 完成 = M1/M2 全部里程碑交付**（2026-08-21，含自动更新与管理端运维视图）✅
**阶段 4 标签打印 + 造数完成 = M1 设计内全部功能项闭环**（2026-08-21，SN 标签打印 v1 + C9 不良代码字典数据）；M3 剩余按批次 2-4 路线图推进
