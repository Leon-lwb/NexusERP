# IoT OTA 前端页面实现报告（2026-09-20）

> 对应: doc/audit/2026-09-20-feat-iot-ota.md §6 | 分支: feature/erp-mes-iot-audit-fix
> 范围: 补齐 OTA 固件升级的**前端 API 层 + 页面 + 字典种子**（后端 15 端点 / 13 权限码 / 2 菜单已就绪）
> 未使用任何 git 命令；未执行任何 SQL；未运行 `npm run build`；未编辑独占文件
> （`script/sql/ry-vue.sql`、`ry-erp.sql`、`ry-mes.sql`、`ry-iot.sql`、`doc/system/数据库增量变更记录.md`、
> `application*.yml`、`ruoyi-modules/**`）；未新增 npm 依赖。

---

## 1. 实现清单

| 文件 | 要点 | 依赖端点与权限码 |
|---|---|---|
| `plus-ui/src/api/iot/firmware/types.ts`（新增） | `FirmwareQuery`/`FirmwareVO`/`FirmwareForm`；字段以后端 `IotFirmware`/`IotFirmwareBo` 为准；ID 一律 `string` | — |
| `plus-ui/src/api/iot/firmware/index.ts`（新增） | `listFirmware`/`optionFirmware`/`getFirmware`/`addFirmware`/`updateFirmware`/`delFirmware`/`changeFirmwareStatus` | `GET /iot/firmware/list`（list）、`GET /iot/firmware/optionList`（**list**）、`GET /iot/firmware/{id}`（query）、`POST /iot/firmware`（add）、`PUT /iot/firmware`（edit）、`DELETE /iot/firmware/{ids}`（remove）、`PUT /iot/firmware/{id}/status?status=&remark=`（release） |
| `plus-ui/src/api/iot/ota/types.ts`（新增） | `OtaTaskQuery`/`OtaTaskVO`/`OtaTaskForm`/`OtaBatchForm`/`OtaBatchResultVO`/`OtaReportForm`/`OtaVersionCheckVO`；字段以后端 `IotOtaTask` 与 4 个 BO/VO 为准 | — |
| `plus-ui/src/api/iot/ota/index.ts`（新增） | `listOtaTask`/`getOtaTask`/`createOtaTask`/`createOtaTaskBatch`/`retryOtaTask`/`cancelOtaTask`/`reportOtaTask`/`checkOtaVersion` | `GET /iot/ota/list`（list）、`GET /iot/ota/{id}`（query）、`POST /iot/ota/create`（create）、`POST /iot/ota/batch`（create）、`PUT /iot/ota/{id}/retry`（retry）、`PUT /iot/ota/{id}/cancel?reason=`（cancel）、`POST /iot/ota/report`（report）、`GET /iot/ota/versionCheck?firmwareId=&onlyBehind=`（versionCheck） |
| `plus-ui/src/views/iot/firmware/index.vue`（新增，组件路径 `iot/firmware/index` = 菜单 6157） | 列表列（名称/产品型号/版本/文件名/大小格式化 KB·MB·GB/校验算法+校验值截断+复制/发布状态标签/发布时间/变更说明）；筛选（名称/型号/版本/状态）；**仅草稿可改**（非草稿行编辑按钮禁用 + tooltip「已发布固件需先停用」）；**发布/停用**（二次确认；发布前本地校验包与校验值）；**删除**（后端拒绝已发布时原样提示并追加「请先『停用』后再删除」引导）；**固件包上传**（本地算 MD5/SHA256 → `POST /resource/oss/upload` 拿 `ossId` → 回填 `fileOssId/fileName/fileSize/checksum`） | `iot:firmware:list/query/add/edit/remove/release` + OSS 通用端点 `system:oss:upload` |
| `plus-ui/src/views/iot/ota/index.vue`（新增，组件路径 `iot/ota/index` = 菜单 6158） | 列表列（设备=编码(ID)/目标版本/当前版本/7 态标签/进度条/批次号/下发·结束时间/失败原因/重试次数/关联指令ID 可跳转）；筛选（设备/固件/状态/批次号）；行操作（**重试**仅 FAILED·TIMEOUT、**取消**仅进行中 + 二次确认 + 原因输入、**详情抽屉**含 commandId 跳转）；顶部 **单设备下发**（仅已发布固件 + 仅 ACTIVE 设备）、**按分组批量下发**（分组树 + 包含子分组开关 → 结果弹窗「成功 N / 跳过 M」+ 逐条跳过原因）、**版本核对**（固件 → 不一致设备清单 + 「仅看落后」开关，`behind` 整行高亮）；**进度轮询 8s**，`onUnmounted` 清理定时器，工具栏可关闭自动刷新 | `iot:ota:list/query/create/retry/cancel/versionCheck` |
| `plus-ui/src/views/iot/device/index.vue`（**增量修改 4 处**） | ①行操作新增「下发升级」（非 ACTIVE 设备禁用，`v-hasPermi="['iot:ota:create']"`，操作列宽 208→288）；②新增「下发固件升级」对话框（仅列已发布固件 → `POST /iot/ota/create`）；③新增 3 条 import；④新增 `otaSendDialog` 状态与 `openOtaSend/submitOtaSend`（约 60 行，未重写文件） | `iot:ota:create` |
| `script/sql/update/2026-09-20-iot-ota-fe.sql`（新增） | 3 条 `sys_dict_type` + 12 条 `sys_dict_data`，全限定名 `` `ry-vue`. ``、`INSERT IGNORE` 幂等、含校验与回滚 SQL | 仅 DML（无 DDL） |
| `doc/audit/2026-09-20-feat-iot-ota-fe.md`（本报告） | — | — |

### 1.1 §6.1 与后端代码的一致性核对（以代码为准）

逐个比对 `IotFirmwareController` / `IotOtaController` 后，**15 个端点的路径、HTTP 方法、查询参数名与 §6.1 完全一致**，无路径级偏差。差异仅在细节，已按代码实现：

1. `GET /iot/firmware/optionList` 的权限码是 **`iot:firmware:list`**（§6.1 未标注）→ API 注释已写明。
2. `changeFirmwareStatus` 的 `remark` 与 `status` 均为 **query 参数**（`@RequestParam`），不是请求体（§6.1 写法未区分）→ 按 query 实现。
3. 筛选语义：固件 `firmwareName`/`version` 为**模糊**匹配，`productKey`/`status` 为**等值**匹配；OTA `batchNo` 为**等值**匹配（`IotOtaTaskServiceImpl#selectPageList:107-110`）。页面输入框未宣称模糊语义（批次号需完整粘贴）。
4. §6.1 未给出批量下发返回体结构，已按 `IotOtaBatchResultVo`（`batchNo/total/created/skipped/tasks/messages`）实现结果弹窗。
5. 所有路径/查询参数在后端是 `Long`（雪花 ID），响应侧由全局 `BigNumberSerializer` 序列化为字符串 → 前端全链路按 `string` 传参与展示。
6. 固件列表用 `IotFirmwareBo` 作为查询对象（非逐个 `@RequestParam`），前端 `params` 字段名与 BO 字段一一对应，无多余参数。

---

## 2. 未实现 / 待确认项

| 项 | 状态 | 理由与建议 |
|---|---|---|
| **MD5 计算方式** | **已实现（用 `crypto-js`，未新增依赖）** | 需求点名的 `crypto.subtle.digest('MD5')` 在 WebCrypto 中不可用；`plus-ui/package.json` 已有 `crypto-js@4.2.0`（`src/utils/crypto.ts` 已在用），故用 `CryptoJS.algo.MD5/SHA256` **分块流式**计算（4MB/块，`file.slice().arrayBuffer()`），避免大包一次性展开成 `number[]` 造成内存峰值。仍保留「手动填写校验值」入口（校验值输入框可直接粘贴）。若日后允许新增依赖，可换成 `spark-md5`（支持 Worker，避免主线程阻塞）——当前上传期间无进度反馈，大包（数十 MB）计算会有短暂卡顿。 |
| **进度获取用轮询而非 SSE** | **按需求先用轮询** | 后端未做 OTA 进度 SSE 推送（见 §2「OTA 进度落 SSE 实时推送 = 未做」）。页面每 **8 秒**轮询 `GET /iot/ota/list`，`onUnmounted` 清理定时器，工具栏提供「自动刷新」开关。后续若后端接入 `IotRealtimeSseManager`，应替换为 SSE 并移除轮询。 |
| **回执上报（`POST /iot/ota/report`）无 UI 入口** | **仅交付 API 层** | §6.2 未要求页面入口；该端点面向设备网关/运维代报，设备直连 MQTT 时走 `event/post` 旁路。`reportOtaTask` 已可用（含完整类型），如需人工代报可后续在详情抽屉加「代报进度」表单。 |
| **指令页跳转不带过滤** | **部分** | 行内「指令ID」跳 `/iot/command?commandId=xxx`，但 `iot/command/index.vue` 未读取该参数，且后端 `GET /iot/command/list` 无 `commandId` 查询条件 → 目前仅定位到设备指令列表页。建议后续给指令页加 `commandId` 路由参数预填（或后端加该筛选）。 |
| **固件包上传额外权限** | **已确认，需运维关注** | 上传走通用端点 `POST /resource/oss/upload`，其权限码是 **`system:oss:upload`**（`SysOssController:77`），而非 `iot:firmware:*`。仅授 OTA 权限的操作员上传固件会 403；需同时授予 `system:oss:upload`。另需已配置 OSS 渠道（未配置时上传失败，页面提示「固件包上传失败，请重试」）。 |
| **设备下拉一次性加载** | **已知限制** | 沿用既有 `iot/command/index.vue` 做法：`listDevice({pageNum:1,pageSize:500})` 一次拉取，前端按 `status==='ACTIVE'` 过滤。设备超 500 台时下拉不全，建议后续改远程搜索（`remote-method`）。 |
| **版本核对「非落后」标签文案** | **近似** | 后端只返回「当前版本 ≠ 目标版本」的设备，`behind` 由分段数值比较得出。页面把 `behind=false` 显示为「高于/不一致」（无法区分"高于"与"无法解析比较"）。 |
| **OTA 列表设备名称** | **已知限制** | `iot_ota_task` 只冗余 `deviceCode`，无设备名称，列表显示「编码(ID)」。如需名称需后端 VO 联表。 |
| **`vue-tsc` 增量缓存** | **待确认** | 本次 `npx vue-tsc --noEmit` 退出码 0（详见 §5），但项目 tsconfig 配置了 `tsBuildInfoFile`；若需绝对洁净的全量检查，可先删除 `node_modules/.tmp/tsconfig.tsbuildinfo` 再跑一次。 |
| **未执行 SQL / 未跑构建** | **按硬约束** | 字典种子未执行到本地库（主代理统一执行）；`npm run build` 按要求未运行。页面已内置**本地兜底文案映射**，字典未同步时不会显示空白。 |

---

## 3. 主脚本合并清单（请主代理串行合并）

### 3.1 `script/sql/ry-vue.sql`（**独占文件，本代理未编辑**）

> 合并时请去掉增量脚本中的 `` `ry-vue`. `` 限定前缀，改为与文件内既有行一致的裸表名。

- **锚点 A（`sys_dict_type` 整行，现位于 iot 字典类型段末尾，约 line 90356）**：
  ```sql
  INSERT INTO `sys_dict_type` VALUES (600208, '000000', 'IoT转发投递状态', 'iot_forward_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT数据转发投递留痕状态: PENDING/SUCCESS/FAILED');
  ```
  **待插入 3 行**（紧随其后）：
  ```sql
  INSERT INTO `sys_dict_type` VALUES (600209, '000000', 'IoT固件发布状态', 'iot_firmware_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT固件发布状态: DRAFT(草稿)/RELEASED(已发布)/DISABLED(已停用)');
  INSERT INTO `sys_dict_type` VALUES (600210, '000000', 'IoT固件校验算法', 'iot_firmware_checksum_type', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT固件包校验算法: MD5/SHA256');
  INSERT INTO `sys_dict_type` VALUES (600211, '000000', 'IoT升级任务状态', 'iot_ota_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT OTA升级任务状态: PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED');
  ```

- **锚点 B（`sys_dict_data` 整行，现位于 iot 字典数据段末尾，约 line 90375）**：
  ```sql
  INSERT INTO `sys_dict_data` VALUES (600228, '000000', 3, '已注销', 'DECOMMISSIONED', 'iot_device_status', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备注销(终态,不可恢复,MQTT 认证被拒)');
  ```
  **待插入 12 行**（紧随其后，内容与 `script/sql/update/2026-09-20-iot-ota-fe.sql` §2 完全相同）：
  ```sql
  INSERT INTO `sys_dict_data` VALUES (600229, '000000', 1, '草稿', 'DRAFT', 'iot_firmware_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '新登记固件为草稿，可编辑/删除，不可下发');
  INSERT INTO `sys_dict_data` VALUES (600230, '000000', 2, '已发布', 'RELEASED', 'iot_firmware_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已发布固件可下发升级任务，版本号与固件包冻结');
  INSERT INTO `sys_dict_data` VALUES (600231, '000000', 3, '已停用', 'DISABLED', 'iot_firmware_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已停用固件不再允许新建升级任务，可重新发布');
  INSERT INTO `sys_dict_data` VALUES (600232, '000000', 1, 'MD5', 'MD5', 'iot_firmware_checksum_type', '', 'primary', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MD5 校验(小写十六进制)');
  INSERT INTO `sys_dict_data` VALUES (600233, '000000', 2, 'SHA256', 'SHA256', 'iot_firmware_checksum_type', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SHA256 校验(小写十六进制)');
  INSERT INTO `sys_dict_data` VALUES (600234, '000000', 1, '已登记', 'PENDING', 'iot_ota_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '任务已登记，指令尚未确认下发');
  INSERT INTO `sys_dict_data` VALUES (600235, '000000', 2, '已下发', 'SENT', 'iot_ota_status', '', 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '指令已提交 MQTT Broker，等待设备回执');
  INSERT INTO `sys_dict_data` VALUES (600236, '000000', 3, '升级中', 'UPGRADING', 'iot_ota_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备已开始下载/刷写');
  INSERT INTO `sys_dict_data` VALUES (600237, '000000', 4, '成功', 'SUCCESS', 'iot_ota_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备回执升级成功，平台回写设备固件版本');
  INSERT INTO `sys_dict_data` VALUES (600238, '000000', 5, '失败', 'FAILED', 'iot_ota_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '设备回执升级失败(可人工重试)');
  INSERT INTO `sys_dict_data` VALUES (600239, '000000', 6, '超时', 'TIMEOUT', 'iot_ota_status', '', 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '超时窗口内未收到任何回执，由超时兜底任务置位(可重试)');
  INSERT INTO `sys_dict_data` VALUES (600253, '000000', 7, '已取消', 'CANCELLED', 'iot_ota_status', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '人工取消(终态，与设备升级失败区分)');
  ```

- **无需合并菜单/权限种子**：`iot:firmware:*`（6157 + 6137/6138/6139/6147/6148）与 `iot:ota:*`（6158 + 6150-6155）13 行已在 `ry-vue.sql` line 92826-92838 存在（前序批次已合并）。

- **ID 占用核对**：`sys_dict_type` 600209/600210/600211 空闲（现有 iot 类型占 600200-600208）；`sys_dict_data` 600229-600239 与 600253 空闲（现有占至 600228；600252 已被 `spc_chart_type` 使用，故 `CANCELLED` 用 600253）。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| # | 库 | 对象 | 变更 | 幂等/前置 | 需清 Redis |
|---|---|---|---|---|---|
| 1 | `ry-vue` | `sys_dict_type` | `INSERT` 3 行（600209 `iot_firmware_status`、600210 `iot_firmware_checksum_type`、600211 `iot_ota_status`） | `INSERT IGNORE`；主键被占用需改 ID | 否 |
| 2 | `ry-vue` | `sys_dict_data` | `INSERT` 12 行（600229-600239、600253） | `INSERT IGNORE`；`(dict_type,dict_value)` 重复会被忽略 | 否 |

- **无 DDL**：未建表、未加列、未改索引；**无存量数据清洗**，不需要停机窗口。
- 增量脚本：`script/sql/update/2026-09-20-iot-ota-fe.sql`（可重复执行；全限定名，可在任意默认库执行）。
- 字典写入后需**清字典缓存**（`sys_dict` 走 Redis 缓存）：建议在「系统管理 → 字典管理」点一次刷新，或清 `sys_dict:*` 键后重登；**页面已内置本地兜底映射**，未刷新时仅展示文案回退，不会空白。
- 表结构无变更，`charset-encoding-standard.md` 无新增对象需要核对。

---

## 5. 验证记录

工作目录 `D:\workspace\nexus-erp\plus-ui`：

```powershell
# 1) ESLint（仅本批次改动文件）
npx eslint --fix src/api/iot/firmware/index.ts src/api/iot/firmware/types.ts `
  src/api/iot/ota/index.ts src/api/iot/ota/types.ts `
  src/views/iot/firmware/index.vue src/views/iot/ota/index.vue src/views/iot/device/index.vue
# 结果: 退出码 0，无 error / warning 输出（--fix 未产生额外改动）

# 2) 类型检查（全项目，仅跑一次）
npx vue-tsc --noEmit
# 结果: 退出码 0，无类型错误（vue-tsc 3.2.8；tsconfig include = src/**/*.ts + src/**/*.vue）
```

- **未运行** `npm run build`（硬约束 5）。
- **未执行** 任何 SQL（硬约束 4），字典种子仅落盘。
- 人工核对：4 处 `device/index.vue` 增量均已落盘（`grep otaSendDialog|openOtaSend|submitOtaSend|createOtaTask|optionFirmware|iot:ota:create` 命中 29 处）。
- 后端签名核对：逐个打开 `IotFirmwareController`（108 行）与 `IotOtaController`（130 行）比对 15 个端点的 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@RequestParam/@RequestBody`。

---

## 6. 风险与回滚说明

| 风险 | 说明 | 回退方式 |
|---|---|---|
| 字典未合并/未刷新 | 页面用 `useDict` 优先，字典为空时回落到本地 `FALLBACK_*` 映射（标签色一致），不空白 | 无需回退；合并 §3 种子并刷新字典缓存后自动切回字典 |
| 进度轮询叠加请求 | 每 8s 一次 `GET /iot/ota/list`；多标签页同时开会线性叠加。工具栏「自动刷新」可关闭，`onUnmounted` 已清理定时器（本仓库曾被审计点名定时器泄漏） | 关闭开关或删除 `startPolling()` 调用 |
| 大固件包计算校验值卡顿 | 主线程分块哈希，数十 MB 包会有短暂卡顿（上传按钮已 loading 锁） | 改 `spark-md5` + Worker（需新增依赖，本次未做） |
| 固件包上传 403 / 失败 | 需 `system:oss:upload` 权限与可用 OSS 渠道；失败仅提示，不写入半成品数据（先算哈希再上传，两者都成功才回填表单） | 授予权限 / 配置 OSS 渠道 |
| 发布/停用/删除被后端拒绝 | 一律原样透出后端 `msg`（500 分支 `Promise.reject(new Error(msg))`），删除额外追加「请先停用」引导 | 无需回退 |
| 菜单指向缺失组件 | 若回滚前端页面而保留 `sys_menu` 6157/6158，菜单会 404 | 回滚时一并删除 `sys_menu` 6157/6158 及其按钮行，或保留页面 |
| 前端回滚步骤 | — | 删除 `plus-ui/src/api/iot/{firmware,ota}/` 4 个文件、`plus-ui/src/views/iot/{firmware,ota}/index.vue`、`script/sql/update/2026-09-20-iot-ota-fe.sql`、本报告；撤销 `plus-ui/src/views/iot/device/index.vue` 的 4 处增量（操作列按钮与宽度、OTA 对话框、3 条 import、`otaSendDialog`+2 函数）；执行增量脚本 §4 回滚 DELETE（若已入库） |
