# MES + IoT 前端专项审计报告（2026-09-20）

> 审计人: 子代理（前端专项 MES+IoT） | 范围: `plus-ui/src/views/mes/**`（68 vue）、`plus-ui/src/views/iot/**`（3 vue）、`plus-ui/src/views/station/**`（**不存在**）、`plus-ui/src/api/{mes,iot,station}/**`；后端对照 `ruoyi-modules/ruoyi-{mes,iot,station}/**` Controller/BO/VO（仅核契约） | 分支: `feature/erp-mes-iot-audit-fix`
> 口径: `doc/audit/2026-09-20-审计执行规范.md`（§2.3 / §4.5 / §4.6 / §6）。**只读审计，未运行构建**。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| FE-IOT-01 | **P0** | 功能缺失/契约 | `sys_menu` 已播种「告警记录」「告警规则」两个菜单，但 `plus-ui/src/views/iot/` 下**没有任何对应 vue 文件、也没有 api 层** → 菜单可点、页面白屏，9 个告警权限码全部悬空 | `ry-vue.sql:92733`、`ry-vue.sql:92738`；`plus-ui/src/views/iot/` 仅 3 个 vue |
| FE-IOT-02 | P1 | 功能不可达/字段 | 设备密钥重置：菜单按钮种子 + 后端端点齐备，**前端既无 api 函数也无按钮**；同时 `deviceSecret` 前端类型有、后端 `@JsonIgnore` 永不返回 | `ry-vue.sql:92732`、`IotDeviceController.java:192-194`、`api/iot/device/index.ts:1-50`、`IotDeviceVo.java:80-81`、`api/iot/device/types.ts:18` |
| FE-MES-01 | P1 | 字段/契约 | OEE 看板把「性能率/质量率缺失」的 OEE 当完整 OEE 展示：后端新增的 `performanceRateAvailable`/`qualityRateAvailable` 前端类型未接、页面无任何提示，被乘 1.0 的 OEE 直出 | `EqOeeServiceImpl.java:340-347,121-122`、`EqOeeOverviewVo.java:47,50`、`api/mes/eq/oee.ts:19-20,36-44`、`views/mes/eq/oee/index.vue:114-132` |
| FE-MES-02 | P1 | 字段/布局 | 工单列表缺 5 个后端已有且前端类型已声明的关键列（已完工/报废/计划完工/单位/优先级/来源），计划数量无单位，计划开工日期未格式化 | `ProWorkOrderVo.java:56-62,68-69,77,113,122-123`、`api/mes/pro/types.ts:9-10,14,22`、`views/mes/pro/workorder/index.vue:46-58` |
| FE-MES-03 | P1 | 功能缺失 | 后端 **33 个** MES/工位终端端点在 plus-ui **零调用方**（备件耗用/烧录报表/批次追溯/工位终端全部业务/SPC 数据点详情）→ UI 完全不可达 | `_raw-reachability2.txt:280-353`；全 `plus-ui/src` grep `repair-spare|flash/report|pro/trace|station/pass` = 0 命中 |
| FE-MES-04 | P1 | 健壮性/数据错 | 维修完工对话框的备件下拉懒加载失败被 `catch { /* ignore */ }` 静默吞掉 → 备件列表空白，用户以为"无备件可选"，完工时不登记备件耗用且无任何报错 | `views/mes/eq/repair/index.vue:267-275` |
| FE-MES-05 | P2 | 前端布局 | 多列宽表 10/11 列未设 `width`/`min-width`，靠 Element Plus 均分 → 长文本列被压、时间列被 tooltip 截断 | `views/mes/pro/andon/index.vue:76-106`、`views/mes/qc/iqc/index.vue:33-60`、`views/mes/pro/workorder/index.vue:46-58` |
| FE-MES-06 | P2 | 前端布局 | 工位终端台账页没有搜索表单，`right-toolbar` 的搜索开关 `v-model:showSearch` 无对应 `v-show` 区域 → 点开关无任何反应，设备多时无法筛选 | `views/mes/station/device/index.vue:31-38`、`api/mes/station/device.ts:13-14` |
| FE-IOT-03 | P2 | 前端字段 | IoT 实时页把 `connectionStatus` 硬编码成 `'ONLINE' ? '在线'`、`protocolType` 直出原始码，而同模块列表页两处都用 `dict-tag` → 后端改字典不同步 | `views/iot/device/realtime.vue:8-9,14` vs `views/iot/device/index.vue:65,71` |
| FE-MES-07 | P2 | 健壮性 | 空 `catch` 静默吞错在本域大面积存在（≥17 处），已核实 1 处为真静默、1 处有降级兜底，其余需逐个判读 | 见 §3 FE-MES-07 |

**计数：P0 = 1，P1 = 5，P2 = 4。**

## 1. P0 详述

### FE-IOT-01 告警菜单已播种但前端页面与 api 层完全不存在 → 菜单白屏、9 个权限码悬空

- **证据**:
  - 菜单种子：`script/sql/ry-vue.sql:92733`（菜单 6030 `告警记录`，`component='iot/alert/index'`，`perms='iot:alert:list'`）、`script/sql/ry-vue.sql:92738`（菜单 6040 `告警规则`，`component='iot/alertRule/index'`，`perms='iot:alertRule:list'`）
  - 按钮种子：`ry-vue.sql:92734-92737`（`iot:alert:query/ack/recover/export`）、`ry-vue.sql:92739-92743`（`iot:alertRule:query/add/edit/remove/changeStatus`）
  - 后端端点齐备：`IotAlertController.java:40,48-98`（`/iot/alert` list/export/query/notifyLog/ack/recover）、`IotAlertRuleController.java:38,46-105`（`/iot/alertRule` list/optionList/query/add/edit/changeEnabled/remove）
  - 前端：`plus-ui/src/views/iot/` 实际只有 `device/index.vue`、`device/realtime.vue`、`product/index.vue`（glob 全量枚举）；全 `plus-ui/src` grep `alertRule|iot/alert|notifyLog` **0 命中**
- **现状**: 后端告警域（规则配置 + 告警记录 + 确认/恢复 + 通知日志）功能完整且已授权，但前端一页未建。
- **触发路径**: 用有 `iot:alert:list` 授权的角色登录 → 侧边栏「IoT → 告警记录」/「告警规则」→ 点击。
- **后果**: 路由 `component` 指向不存在的模块，动态 `import()` 失败 → **空白页 + 控制台报错**，业务人员看到"点了没反应/白屏"却无法自助判断。整个 IoT 告警闭环（阈值配置、去抖、级别、确认、恢复）在 UI 上完全不可用；9 个已播种权限码成为死码。
- **修复建议**:
  1. 补 `plus-ui/src/api/iot/alert.ts`（list/export/get/notifyLog/ack/recover）与 `plus-ui/src/api/iot/alertRule.ts`（list/optionList/get/add/update/changeEnabled/del），字段以 `IotAlertVo`/`IotAlertRuleVo` 为准；
  2. 补 `plus-ui/src/views/iot/alert/index.vue`（列：告警设备、规则、级别 `dict-tag`、触发值、状态 `dict-tag`、触发/确认/恢复时间；操作：确认 `iot:alert:ack`、恢复 `iot:alert:recover`、通知日志）与 `views/iot/alertRule/index.vue`（阈值/持续时长/去抖/级别/启停开关）；
  3. 若本轮不做页面，则必须**同时下线菜单 6030/6040**（否则是稳定的白屏入口）。
- **影响面**: 新增 2 个 vue + 2 个 api 文件；无需 DDL（菜单/权限已播种）；`iot:alert*` 字典类型需核对 `sys_dict_type` 是否存在。建议补一个"菜单 component 文件存在性"校验测试。
- **测试现状**: `ruoyi-modules/ruoyi-iot/src/test` 无告警前端相关测试。

## 2. P1 详述

### FE-IOT-02 设备密钥重置功能前端不可达 + `deviceSecret` 前后端字段不一致

- **证据**:
  - 菜单按钮种子已存在：`script/sql/ry-vue.sql:92732`（菜单 6026 `设备密钥重置`，`perms='iot:device:resetSecret'`，备注"重置设备密钥(轮换 device_secret, 仅返回一次)"）
  - 后端端点：`IotDeviceController.java:192-194`（`@SaCheckPermission("iot:device:resetSecret")` + `@PutMapping("/{deviceId}/resetSecret")`）
  - 前端 api：`plus-ui/src/api/iot/device/index.ts:1-50` 只有 `listDevice/getDevice/addDevice/updateDevice/delDevice/listIotDeviceForBind`，**无 resetSecret**
  - 前端页面：`views/iot/device/index.vue:87-95` 操作列只有 实时/查看/修改/关联MES/删除，**无密钥重置按钮**（全 `plus-ui/src` grep `resetSecret` 0 命中）
  - 字段不一致：`IotDeviceVo.java:80-81` 对 `deviceSecret` 标 `@JsonIgnore`（"API 响应中隐藏，防止泄露认证凭证"），但 `plus-ui/src/api/iot/device/types.ts:18` 仍声明 `deviceSecret: string`
- **触发路径**: 设备密钥泄露需轮换时，管理员在 IoT 设备页找不到任何入口，只能手工调 `PUT /iot/device/{id}/resetSecret`。
- **后果**: ①安全运维动作不可达（密钥无法通过界面轮换）；②前端 `DeviceVO.deviceSecret` 永远是 `undefined`，任何依赖该字段的展示/逻辑都是死代码，且误导后续开发者以为后端会返回密钥。
- **修复建议**: 在 `api/iot/device/index.ts` 增 `resetDeviceSecret(deviceId)`；在 `views/iot/device/index.vue:87-95` 操作列加「重置密钥」按钮（`v-hasPermi="['iot:device:resetSecret']"`），成功后用 `ElMessageBox` 一次性展示新密钥并提示"仅显示一次"；从 `types.ts` 删除 `deviceSecret`（或改注释说明永不返回）。
- **影响面**: 1 个 api 文件 + 1 个 vue；无 DDL；权限种子已就绪。
- **测试现状**: 无。

### FE-MES-01 OEE 看板把「性能率/质量率缺失」的 OEE 当完整 OEE 展示

- **证据**:
  - 后端计算：`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqOeeServiceImpl.java:340`（`BigDecimal performanceRate = null;`）、`:346-347`（`.multiply(performanceRate == null ? BigDecimal.ONE : performanceRate)`、`.multiply(qualityRate == null ? BigDecimal.ONE : qualityRate)`）→ **P 因子恒为 1，OEE 退化为 稼动率 × 质量率**
  - 后端已给标记：`EqOeeServiceImpl.java:121-122`（`vo.setPerformanceRateAvailable(false)`、`vo.setQualityRateAvailable(...)`）；`domain/vo/EqOeeOverviewVo.java:47,50` 定义 `performanceRateAvailable`/`qualityRateAvailable`
  - 前端类型未接：`plus-ui/src/api/mes/eq/oee.ts:36-44` 的 `EqOeeOverviewVO` 只有 avgAvailability/avgOee/totalDowntimeMinutes/totalFaultCount/equipmentCount/totalPlannedMinutes/totalRunMinutes，**没有两个 Available 字段**
  - 前端页面：`views/mes/eq/oee/index.vue:114-132` 明细表列只有 统计日期/设备编码/设备名称/计划时长/停机时长/运行时长/故障次数/稼动率/OEE，**无性能率、无质量率列**；`:130` `{{ pct(scope.row.oee) }}` 直出
  - `performanceRate`/`qualityRate` 虽在 `api/mes/eq/oee.ts:19-20` 声明，但全 `plus-ui/src` grep 仅命中该 2 行 → **声明后全站零使用**
- **触发路径**: 打开「MES → 设备管理 → OEE看板」→ 看 KPI 卡「平均 OEE」与明细表 OEE 列。
- **后果**: 页面显示的 "OEE 85%" 实际是 `稼动率 × 质量率`（P 因子被静默置 1），**OEE 被系统性高估**；设备主管据此判断设备效率、排产与考核都会偏乐观，且页面上没有任何"数据不完整"的提示。后端已专门新增 `*Available` 标记正是为此，前端没接 → 标记形同不存在。
- **修复建议**:
  1. `api/mes/eq/oee.ts:36-44` 的 `EqOeeOverviewVO` 补 `performanceRateAvailable?: boolean; qualityRateAvailable?: boolean;`
  2. `views/mes/eq/oee/index.vue` KPI 卡（:57-64）与明细表 OEE 列（:129-131）在 `available === false` 时显示 `—` 并加 `el-tooltip`「性能率数据缺失，OEE 未含性能因子」；
  3. 明细表补「性能率」「质量率」两列（`null` → `—`），让用户能区分"真 100%"与"无数据"。
- **影响面**: 1 个 api 类型 + 1 个 vue；无 DDL；不涉及后端改动（后端已就绪）。
- **测试现状**: 无前端测试。

### FE-MES-02 工单列表缺关键列 + 数量无单位 + 日期未格式化

- **证据**:
  - 后端 VO 已有：`ProWorkOrderVo.java:56-58`（`completedQty` 已完成数量、`scrapQty` 报废数量）、`:68-69`（`unitName` 单位）、`:77`（`planEndDate` 计划完工）、`:113`（`priority` 优先级）、`:122-123`（`sourceType` 工单来源，注释 ERP/REWORK/MANUAL）
  - 前端类型已声明：`api/mes/pro/types.ts:9-10`（completedQty/scrapQty）、`:14`（planEndDate）、`:22`（priority）
  - 列表实际只有 8 列：`views/mes/pro/workorder/index.vue:46-58`（woNo/materialCode/materialName/plannedQty/workshopName/planStartDate/woStatus + 操作）
  - 数量列无单位且未格式化：`workorder/index.vue:51`（`<el-table-column label="计划数量" prop="plannedQty" align="right" />`）——无 `unitName`、无千分位
  - 日期列未格式化且无宽度：`workorder/index.vue:53`（`<el-table-column label="计划开工" prop="planStartDate" />`）——对比同域 `views/mes/eq/oee/index.vue:115-117` 用 `fmtDate(scope.row.statDate)` 且带 `width="120"`
- **触发路径**: 打开「MES → 生产管理 → 工单」列表。
- **后果**: ①**看不到完工进度**（已完成/报废数量不展示），生产调度必须逐单点进详情才能判断哪些工单接近完工；②`计划数量` 没有单位列，`1000` 是件/箱/千克不明（后端 `unitName` 就在同一 VO 里）；③看不到计划完工日期，无法在列表层判断交期风险；④`planStartDate` 输出后端 `Date` 原始序列化串且无宽度，列窄时折行/截断，与 OEE 页格式不一致。
- **修复建议**: `workorder/index.vue` 列表按业务顺序补列：`unitName`（"单位"，width 70）、`completedQty`（"已完工"，align right + 千分位）、`scrapQty`（"报废"，align right）、`planEndDate`（"计划完工"，width 120 + `fmtDate`）、`sourceType`（"来源"，`dict-tag`）、`priority`（"优先级"）；`planStartDate` 补 `width` + `fmtDate`；建议同时加"完工进度"列（`completedQty/plannedQty` 百分比）。
- **影响面**: 1 个 vue（列表）；无需 DDL；`sourceType` 需确认 `sys_dict_type` 是否有对应字典（若无则用固定映射）。
- **测试现状**: 无。

### FE-MES-03 后端 33 个 MES/工位终端端点在 plus-ui 零调用方 → 功能不可达

- **证据**（`doc/audit/_raw-reachability2.txt:280-353` 候选清单，**已用 grep 抽样验证真伪**；验证方法：在 `plus-ui/src` 全量搜索该端点的路径特征串，0 命中即判定无调用方）

| 后端控制器 | 端点数 | 关键端点 | plus-ui 验证结果 |
|---|---|---|---|
| `EqRepairSpareController` | 4 | `/mes/eq/repair-spare`、`/repair/{repairId}`、`/batch`、`/{ids}` | grep `repair-spare` → **0 命中**（无 api 文件、无菜单种子） |
| `FlashReportController` | 4 | `/mes/flash/report/flash/auto`、`/test/auto`、`/station/pending` | grep `flash/report` → **0 命中**（无 api 文件、无菜单种子） |
| `ProTraceabilityController` | 3 | `/mes/pro/trace`、`/backward`、`/forward` | grep `pro/trace` → **0 命中**；前端实际走 `api/mes/pro/sn.ts:73` `/mes/pro/sn/trace/{snId}` |
| `StationDeviceController`（工位终端） | 14 | `/mes/station/pass/validate`、`/pass/report`、`/sn/bind`、`/defect-codes`、`/andon/call`、`/trace/{snCode}`、`/inspection/tasks` | grep `station/pass\|station/sn\|station/andon\|station/inspection` → **0 命中** |
| `StationSopController` / `StationMaterialController` / `StationFlashPrepareController` / `StationHeartbeatController` | 4+3+3+2 | `/mes/station/sop/current`、`/material/load`、`/flash/report/station/prepare`、`/heartbeat` | grep `station/sop\|station/material\|station/heartbeat` → **0 命中** |
| `SpcDataPointController` | 1 | `GET /mes/spc/data-point/{dataId}` | `api/mes/spc/data-point.ts` 仅 `:14` list、`:18` by-chart，**无详情函数** |

- **已排除项（不算前端缺口）**: `_raw-reachability2.txt:291-321` 的 `/app/pda/mes/**`（26 个端点）是移动端 PDA 通道，不由 plus-ui 调用，属另一前端载体，本报告不列缺口。
- **触发路径**: 需要"备件耗用明细/烧录自动报表/批层级与反向追溯/工位终端上岗与过站/SPC 单点详情"时，界面上没有任何入口。
- **后果**: ①`mes:pro:trace:query` 权限码已播种（`ry-vue.sql:96844`，备注"ProTraceabilityController 追溯查询"）但授予后无处可用；②备件耗用记录（`EqRepairSpare`）与烧录报表只能靠直连数据库查看，**业务侧无法核对维修耗料与烧录产出**；③工位终端整套业务（过站校验、SN 绑定、安灯呼叫、巡检、SOP 当前版本、物料装载、心跳）在 plus-ui 无任何页面——`views/station/` 目录**不存在**（glob `**/station/**/*.vue` 仅命中 `views/mes/station/device/index.vue` 一个台账页）。
- **修复建议**: 逐条定性后再补：①`repair-spare` 应作为「设备维修」详情页的子表（`views/mes/eq/repair/`）而非独立菜单；②`flash/report/*` 应挂到「烧录记录」页的"自动报表"标签页；③`ProTraceabilityController` 与现有 `sn/trace` 二选一（建议保留后端 `/mes/pro/trace` 并把 `sn/trace.vue` 改为调用它，避免两套追溯实现分叉）；④工位终端 26 个端点若确实由独立终端程序调用，须在 `_raw-reachability2.txt` 的豁免清单里书面登记，否则后续审计会反复报同一缺口。
- **影响面**: 需新增/改造 3~4 个 vue 与对应 api；`EqRepairSpare` 前端无 api 文件；无 DDL。
- **测试现状**: 无。

### FE-MES-04 维修完工备件下拉静默失败 → 备件耗用不登记且无报错

- **证据**: `plus-ui/src/views/mes/eq/repair/index.vue:267-275`
  ```
  267:  // 懒加载备件选项(仅首次打开)
  268:  if (spareOptions.value.length === 0) {
  269:    try {
  270:      const res = await listSparePart({ pageNum: 1, pageSize: 200 });
  271:      spareOptions.value = res.rows || [];
  272:    } catch {
  273:      /* ignore */
  274:    }
  275:  }
  ```
  随后 `:276` 直接 `completeDialog.visible = true`，`:279-286` 的 `submitComplete` 用 `completeForm.spares` 提交。
- **触发路径**: 网络抖动 / `mes:eq:spare:list` 权限缺失 / 备件接口 500 → 打开「维修完工」对话框 → 备件选择框为空 → 用户直接点确定。
- **后果**: 维修实际消耗的备件**不写入 `EqRepairSpare`、不扣减备件库存**（`submitComplete` 只在 `spares.length > 0` 时传值，见 `:285`），而界面不报任何错。结果是**备件库存账实不符、维修成本核算漏料**，且事后无法从日志发现（异常被吞）。这是与 `doc/audit/2026-09-20-audit-mes-tg-sop.md` MES-SOP-01（工位侧查阅登记被静默吞掉）**完全同型**的问题。
- **修复建议**: `:272-274` 改为 `catch (e) { proxy?.$modal.msgError('备件列表加载失败，请重试后再提交完工'); return; }`（即加载失败不打开对话框），或至少 `msgWarning` + 在对话框内显示"备件列表加载失败"的 `el-alert` 并禁用提交；同时给 `completeDialog` 加"是否消耗备件"的显式勾选，避免用户默认无备件。
- **影响面**: 1 个 vue；无 DDL；建议为 `completeRepair` 增加"备件耗用必填"的联调用例。
- **测试现状**: 无。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果与建议 |
|---|---|---|---|
| FE-MES-05 | 宽表列宽缺失 | `views/mes/pro/andon/index.vue:76-106`（11 列中仅 `callReason` 有 `min-width`、操作列有 `width`）、`views/mes/qc/iqc/index.vue:33-60`（9 列中仅 2 列有 `min-width`）、`views/mes/pro/workorder/index.vue:46-58`（8 列中仅 2 列有宽度） | Element Plus 对无宽度列按内容均分：短列（状态/来源）被拉宽，长列（物料名称/供应商/呼叫原因）被压缩，`show-overflow-tooltip` 把"检验时间/呼叫时间"截成省略号必须悬停才可读；1366px 下更明显。建议给编码/状态/时间类列补 `width`，名称/原因类列补 `min-width` |
| FE-MES-06 | 工位终端台账无搜索区，`right-toolbar` 开关空转 | `views/mes/station/device/index.vue:31-36`（操作栏只有"刷新"按钮 + `<right-toolbar v-model:showSearch="showSearch" @queryTable="getList()">`），`:38` 直接是 `el-table`，**全页无 `el-form`/筛选控件**；`showSearch` 无任何 `v-show` 绑定目标 | 用户点右上角搜索图标无任何反应；终端设备上百台时无法按工位/工序/在线状态筛选，只能翻页。建议补 `el-form`（设备编码、工位、在线状态）并让 `v-show="showSearch"` 绑定它 |
| FE-MES-06b | `shutdown` 字段前端未展示 | `api/mes/station/device.ts:13-14`（`/** 应用正常退出标记(服务端即时判离线) */ shutdown?: boolean;`）；`views/mes/station/device/index.vue:38-77` 列清单中无该列 | 台账把"应用正常退出"和"心跳超时（掉线/断电）"都显示为「离线」，运维无法区分计划关机和异常断连，排障方向错误。建议在"在线状态"列用不同 `el-tag` 文案区分（正常退出=info「已退出」，心跳超时=danger「离线」） |
| FE-IOT-03 | 字典码硬编码 / 展示口径不一致 | `views/iot/device/realtime.vue:8-9`（`connectionStatus === 'ONLINE' ? '在线' : '离线'` 硬编码，且只认 ONLINE，其它值一律显示"离线"）、`:14`（`deviceInfo.protocolType` 直出原始码 `MQTT`）；对照 `views/iot/device/index.vue:65`（`<dict-tag :options="iot_protocol_type" ...>`）、`:71`（`iot_connection_status`） | ①后端字典改文案/新增状态（如 `UNBOUND`）时实时页不同步；②协议类型显示 `MQTT` 而非字典标签，同模块两页展示不一致。建议实时页同样引入 `useDict('iot_protocol_type','iot_connection_status')` 并用 `dict-tag` |
| FE-MES-07 | 空 `catch` 静默吞错大面积存在 | 已核实：`views/mes/eq/repair/index.vue:272-273`（真静默，见 FE-MES-04）、`views/mes/eq/equipment/index.vue:506-510`（有降级兜底，**不算问题**）。同型待逐个判读的 `} catch {` 位置：`views/mes/eq/equipment/index.vue:474,529,559,573,610,614,638,653,665`、`views/mes/label/template/index.vue:340,369`、`views/mes/ncr/order/index.vue:601`、`views/mes/flash/testrecord/index.vue:496`、`views/mes/flash/testitem/index.vue:331`、`views/mes/dc/collection-point/index.vue:227`、`views/mes/ncr/defect-code/index.vue:208`、`views/mes/ncr/8d-report/index.vue:106`、`views/mes/pro/andon/index.vue:324`、`views/mes/pro/flow-card/detail.vue:358,530,581`、`views/mes/eq/inspection/index.vue:135,163`、`views/mes/eq/patrol-task/index.vue:241`、`views/mes/station/device/index.vue:171`、`views/mes/qc/iqc/index.vue:343`（后者有"回退人工判定"降级，可接受） | 建议统一加 `proxy?.$modal.msgError(...)` 或至少 `console.error`，避免"界面看起来成功、数据没落库"。本轮未逐个判读（预算所限），已列入 §5 未覆盖范围 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | IoT 告警记录页 | `ry-vue.sql:92733` 菜单 6030 指向 `iot/alert/index`，文件不存在 | 后端 `IotAlertController` 5 端点 + `iot:alert:*` 5 权限种子 + 表 `iot_alert` | 1 vue + 1 api，约 0.5 人日 |
| 2 | IoT 告警规则页 | `ry-vue.sql:92738` 菜单 6040 指向 `iot/alertRule/index`，文件不存在 | 后端 `IotAlertRuleController` 6 端点 + `iot:alertRule:*` 5 权限种子 | 1 vue + 1 api，约 0.5 人日 |
| 3 | 设备密钥重置入口 | `ry-vue.sql:92732` 权限种子 + `IotDeviceController.java:194` 端点齐备，前端 0 引用 | 后端已实现（含"仅返回一次"语义） | api 1 函数 + 按钮，约 0.2 人日 |
| 4 | 工单列表完工进度/单位/交期 | `ProWorkOrderVo.java:56-78,113` 字段齐备，`types.ts` 已声明 | VO 字段、字典 `prd_wo_status` | 改 1 vue，约 0.2 人日 |
| 5 | OEE 性能率/质量率展示与"数据缺失"提示 | `EqOeeOverviewVo.java:47,50` 标记已就绪；`api/mes/eq/oee.ts:19-20` 类型已声明未用 | 后端 `EqOeeServiceImpl:121-122` 已算出 Available | 改 1 api 类型 + 1 vue，约 0.3 人日 |
| 6 | 维修备件耗用明细 UI | `EqRepairSpareController` 4 端点 + 表 `eq_repair_spare`，前端无 api 无页面 | 表、BO、VO、Service 齐备 | 作为 `eq/repair` 详情子表，约 0.5 人日 |
| 7 | 烧录自动报表（flash/test auto） | `FlashReportController` 4 端点，前端无 api 无页面、无菜单种子 | 后端已实现 | 1 标签页，约 0.5 人日 |
| 8 | 批层级/反向追溯 | `ProTraceabilityController` 3 端点，前端 0 引用 | 后端已实现；`mes:pro:trace:query` 已播种 | 复用 `sn/trace.vue` 改造，约 0.5 人日 |
| 9 | 工位终端业务界面（过站/SN/安灯/巡检/SOP/物料/心跳） | 26 端点，`views/station/` 目录不存在 | 后端 `ruoyi-station` 5 个 Controller 齐备 | 需确认是否由独立终端程序承载；若需要 Web 端则 ≥5 人日 |
| 10 | SPC 数据点详情 | `SpcDataPointController` `GET /{dataId}`，`api/mes/spc/data-point.ts` 仅 list/by-chart | 后端已实现 | 1 api 函数 + 详情弹窗，约 0.2 人日 |

## 5. 已核查确认无问题（证明覆盖范围）

- **图表/监听器释放**：抽查的 6 个图表页均有 `onUnmounted` 清理，无内存泄漏 —— `views/mes/eq/oee/index.vue:374-378`（`removeEventListener` + 3 个 `dispose`）、`views/mes/eq/health/index.vue:237,243`、`views/mes/spc/control-chart/index.vue:521-523`、`views/mes/eq/repair/statistics.vue:232-236`、`views/mes/pro/andon/index.vue:447-448`、`views/mes/eq/monitor/index.vue:220-221`（`clearInterval`）。
- **工位终端台账权限码一致（非 403 缺口）**：`StationDeviceAdminController.java:54,84,103,113` 四个端点统一用 `@SaCheckPermission("mes:station:device:list")`，与菜单 4230 种子 `ry-vue.sql:92609` 的 `perms` **逐字一致**；页面 `views/mes/station/device/index.vue:120` 调用的 `/mes/station-admin/device/{list,stats,{id}/equipments,by-workcenter}` 与该控制器 4 个映射一一对应。
- **OEE 权限码齐备**：`ry-vue.sql:92899-92902` 播种 `mes:eq:oee:{list,query,export,aggregate}`，与页面 `views/mes/eq/oee/index.vue:37`（`mes:eq:oee:aggregate`）、`:40`（`mes:eq:oee:export`）的 `v-hasPermi` 一致。
- **IoT 设备/产品 CRUD 权限码一致**：`IotDeviceController.java:61-112`（list/export/query/add/edit/remove）与 `views/iot/device/index.vue:40-52` 的 `v-hasPermi` 逐条对应；`iot:device:resetSecret` 的种子也已存在（问题只在**前端未实现入口**，非种子缺失）。
- **IoT 菜单 component 路径**：菜单 6020 `iot/device/index`（`ry-vue.sql:92726`）→ `views/iot/device/index.vue` **存在**，路径正确（与 FE-IOT-01 的两个悬空菜单形成对照，说明该问题是局部的、可精确修复）。
- **IoT 实时页 SSE 与图表**：`views/iot/device/realtime.vue:76-79` 声明了 `eventSource`/`resizeHandler` 句柄，`:114-119` 注册 resize 监听（释放逻辑在 120 行之后，未读，见 §5 未覆盖）。
- **`eq/equipment/index.vue:506-510`**：IoT 批量状态查询失败时有明确降级（`iotStatus = 'UNKNOWN'`），**不属于静默吞错**。
- **MES 页面字典使用总体规范**：抽查的 7 个页面（oee/andon/iqc/workorder/equipment/station/realtime）中，状态与类型列基本都走 `dict-tag`（如 `andon/index.vue:86,94,103`、`iqc/index.vue:47,52`、`monitor/index.vue:58,71,99,104`），硬编码仅见于 IoT 实时页（FE-IOT-03）。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `performanceRateAvailable` | `EqOeeOverviewVo.java:47`（后端已赋值 `EqOeeServiceImpl.java:121`） | `api/mes/eq/oee.ts:36-44` 的 `EqOeeOverviewVO` **缺** | ①后端有/前端没接 → 无法提示 OEE 不完整（FE-MES-01） |
| `qualityRateAvailable` | `EqOeeOverviewVo.java:50`（`EqOeeServiceImpl.java:122`） | 同上 **缺** | ①同上 |
| `performanceRate` | `EqOeeRecordVo.java:78`、`EqOeeRecord.java:78` | `api/mes/eq/oee.ts:19` 声明，但全 `plus-ui/src` **零使用**；`oee/index.vue:114-132` 无列 | ①后端有/列表未展示 → 用户无法判断 P 因子缺失 |
| `qualityRate` | `EqOeeRecordVo.java:81`、`EqOeeRecord.java:81` | `api/mes/eq/oee.ts:20` 声明，零使用 | ①同上 |
| `deviceSecret` | `IotDeviceVo.java:80-81` 标 `@JsonIgnore`（**永不返回**） | `api/iot/device/types.ts:18` 声明 `deviceSecret: string` | ②前端有/后端没有 → 永远 `undefined`，误导后续开发（FE-IOT-02） |
| `completedQty` | `ProWorkOrderVo.java:56-58` | `api/mes/pro/types.ts:9` 有；`views/mes/pro/workorder/index.vue:46-58` **无列** | ①后端有/列表没接（FE-MES-02） |
| `scrapQty` | `ProWorkOrderVo.java:60-62` | `types.ts:10` 有；列表**无列** | ①同上 |
| `unitName` | `ProWorkOrderVo.java:67-69` | `types.ts:12` 有；列表**无列** → `plannedQty` 无单位 | ①+⑤单位口径不明（FE-MES-02） |
| `planEndDate` | `ProWorkOrderVo.java:75-77` | `types.ts:14` 有；列表**无列** | ①后端有/列表没接（FE-MES-02） |
| `priority` | `ProWorkOrderVo.java:112-113` | `types.ts:22` 有；列表**无列** | ①同上 |
| `sourceType` | `ProWorkOrderVo.java:121-123`（ERP/REWORK/MANUAL） | `types.ts` 的 `ProWorkOrderVO` **无该字段**（仅 `types.ts:41-47` 有发布包相关） | ①后端有/前端类型都没接 → 无法区分工单来源 |
| `shutdown` | 服务端计算列（`api/mes/station/device.ts:13-14` 注释"应用正常退出标记"） | `views/mes/station/device/index.vue:38-77` **无列** | ①前端类型有/页面没展示（FE-MES-06b） |
| `connectionStatus` | `IotDeviceVo.java:64-68`（ONLINE/OFFLINE） | `views/iot/device/realtime.vue:8-9` **硬编码** `'ONLINE' ? '在线' : '离线'`；列表页 `index.vue:71` 用 `dict-tag` | ④字典码硬编码 + 同模块两页口径不一致（FE-IOT-03） |
| `protocolType` | `IotDeviceVo.java:58-62`（MQTT/OPC_UA/MODBUS/HTTP） | `realtime.vue:14` 直出原始码；`index.vue:65` 用 `dict-tag` | ④展示口径不一致（FE-IOT-03） |
| `planStartDate` | `ProWorkOrderVo.java:71-73`（`Date`） | `workorder/index.vue:53` 无 `fmtDate`、无 `width`；对照 `oee/index.vue:115-117` 用 `fmtDate` | ⑥时间格式不一致 + 折行（FE-MES-02） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交，先做）**：FE-IOT-01 —— 补 `views/iot/alert/index.vue` + `views/iot/alertRule/index.vue` + 2 个 api 文件；**若本轮不做页面，则必须先删掉菜单 6030/6040 的种子**（白屏入口不可留）。验收：遍历 `sys_menu.component` 做文件存在性校验，差集为空。
- **批次 B（P1，前端契约对齐，可与 A 并行）**：FE-MES-01（OEE 可用性标记 + P/Q 列）、FE-MES-02（工单列表补 6 列 + 日期格式化 + 单位）、FE-IOT-02（密钥重置入口 + 删 `deviceSecret` 类型）。三者都是"后端已就绪、只改前端"，风险低、收益直接。
- **批次 C（P1，链路补齐，需先定性）**：FE-MES-03（33 个不可达端点逐条定性后补 UI 或书面豁免，其中工位终端 26 个端点需先确认是否由独立终端程序承载）、FE-MES-04（备件下拉静默失败改显式报错）。
- **批次 D（P2，体验/健壮性）**：FE-MES-05（列宽）、FE-MES-06/06b（工位台账搜索区 + `shutdown` 列）、FE-IOT-03（实时页改 `dict-tag`）、FE-MES-07（空 `catch` 逐个判读后补错误提示）。

## 8. 未覆盖范围（自述）

- **未逐个核对** 68 个 MES vue 的完整表单校验规则与后端 BO 校验注解的对应关系；本轮深读了 7 个页面（`mes/eq/oee`、`mes/pro/workorder`、`mes/pro/andon`、`mes/qc/iqc`、`mes/eq/monitor`、`mes/station/device`、`mes/pro/sn/trace`）与 4 个 IoT/station api 文件，其余页面只做了 grep 级扫描（字典硬编码、空 catch、图表释放、api URL）。
- **未核对** `sys_role_menu`：FE-IOT-01 的菜单 6030/6040 是否已授权给具体角色未验证（若未授权给任何角色，则白屏只对超管可见，严重度仍是 P0 但影响面收窄）。
- **未验证** `views/iot/device/realtime.vue` 第 120 行之后的 SSE `eventSource.close()` 与图表 `dispose()` 释放逻辑（只读到 120 行）。
- **未逐个判读** FE-MES-07 列出的 17 处 `} catch {` 是否为空体（仅核实了 `eq/repair:272` 为真静默、`eq/equipment:506` 有降级）。
- **未运行构建**（规范禁止 `npm run build`），菜单 `component` 路径只做了"文件是否存在"的静态比对，未做路由实际加载验证。
- **`/app/pda/mes/**`（26 端点）** 判定为移动端通道、不计入前端缺口，未在 plus-ui 之外寻找调用方。
- **未核对** `mes:pro:trace:query` 在 `ProTraceabilityController.java` 中的实际 `@SaCheckPermission` 值（依据 `ry-vue.sql:96844` 的种子备注推断，未读该控制器）。
