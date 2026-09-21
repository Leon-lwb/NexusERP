# IoT OTA 固件升级 MVP 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-iot.md` §4「功能缺失清单」**#1 OTA 固件升级**（并复用 **IOT-PIPE-02** 已交付的下行通道）
> 分支: `feature/erp-mes-iot-audit-fix` | 批次: **B10-c** | 工作目录: `D:\workspace\nexus-erp`
> 参照同模块已完成的 B10-b（`2026-09-20-feat-iot-device-ops.md`）的分层/命名/测试/权限种子写法；
> **复用**其 `IotMqttPublisher` + `iot_device_command` 下行通道与 `iot_device_group_rel` 分组关系，未重复实现。
> 未使用任何 git 命令；未编辑 `ry-vue.sql` / `ry-erp.sql` / `ry-mes.sql` / `application*.yml` /
> `doc/system/数据库增量变更记录.md` / `plus-ui/**` / `ruoyi-modules/ruoyi-{mes,erp,station}/**`。

---

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| §4#1 固件登记 | 新增 `iot_firmware`（租户、`firmware_name`、产品型号 `product_key`、硬件型号 `hardware_model`、`version`、`file_oss_id`/`file_name`/`file_size`、**`checksum_type`+`checksum`(MD5/SHA256)**、`status`(DRAFT/RELEASED/DISABLED)、`release_time`、`change_log`、审计字段 + `del_flag`）；**唯一键 `uk_version(tenant_id, product_key, version)`** | `firmware/domain/IotFirmware.java`、`ry-iot.sql:441-475`、`update/2026-09-20-iot-ota.sql §1` | 是（1 新表） | `IotFirmwareServiceTest` 13 例 |
| §4#1 状态机 | 固件发布状态机 `IotFirmwareStatus`：DRAFT→RELEASED/DISABLED、RELEASED→DISABLED、DISABLED→RELEASED；**禁止回退 DRAFT**；发布前置校验（必须有包与校验值）；**CAS** 条件更新（0 行抛"已被其他操作变更"）；`insertByBo` 服务端强制 DRAFT、`updateByBo` 不接受 status/releaseTime 写入；**已发布固件不可编辑/不可删除** | `firmware/domain/IotFirmwareStatus.java`、`firmware/service/impl/IotFirmwareServiceImpl.java` | 否 | `IotFirmwareServiceTest#insertByBo_forcesDraftAndNormalizes/#changeStatus_*`（8 例） |
| §4#1 升级任务 | 新增 `iot_ota_task`（固件、目标版本+下发时当前版本、目标设备、来源分组/批次号、关联 `command_id`、状态机、进度、重试次数、下发/开始/结束时间、失败原因、回执消息ID、审计字段） | `ota/domain/IotOtaTask.java`、`ry-iot.sql:477-511`、增量脚本 §2 | 是（1 新表） | `IotOtaTaskServiceTest` 30 例 |
| §4#1 任务状态机 | `IotOtaStatus`：PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/**CANCELLED**；终态不可再流转；`canReport`（允许 `UPGRADING→UPGRADING` 进度上报，其余同 `canTransition`）；`canRetry`(仅 FAILED/TIMEOUT)、`canCancel`(仅进行中) | `ota/domain/IotOtaStatus.java` | 否 | `IotOtaTaskServiceTest#report_illegalTransition_rejected/#report_unknownStatus_rejected/#retry_upgradingTask_rejected/#cancel_successTask_rejected` |
| §4#1 单设备下发 | `POST /iot/ota/create`：校验固件已发布 → 校验设备 ACTIVE（禁用/注销拒绝）→ 校验产品型号匹配 → 业务幂等（同设备+同固件不允许并存进行中任务）→ **先留痕 PENDING** → 复用 `IIotDeviceCommandService.sendServiceInvoke`（`event/set` + service=`otaUpgrade`）→ 回写 SENT/FAILED | `ota/service/impl/IotOtaTaskServiceImpl.java:doCreate/dispatch`、`ota/controller/IotOtaController.java` | 否 | `#createTask_persistsPendingThenDispatches/#createTask_disabledDevice_rejected/#createTask_firmwareNotReleased_rejected/#createTask_productMismatch_rejected/#createTask_duplicateActiveTask_rejected` |
| §4#1 批量下发 | `POST /iot/ota/batch`：**复用既有分组关系表**（`IIotDeviceGroupService.selectDeviceIds(groupId, includeSubGroup)`，含子树）→ 逐台建任务，**部分成功语义**（禁用/重复设备跳过并逐条回传原因，不整体回滚），共用 `batch_no` 批次号 | `ota/service/impl/IotOtaTaskServiceImpl.java:createBatchTask`、`ota/domain/vo/IotOtaBatchResultVo.java` | 否 | `#createBatchTask_byGroup_skipsDisabledDevice/#createBatchTask_emptyGroup_rejected` |
| §4#1 查询/重试/取消 | `GET /iot/ota/list`（设备/固件/状态/批次号）、`GET /iot/ota/{taskId}`、`PUT /iot/ota/{taskId}/retry`（CAS 置 SENT + `retry_count+1` + 重新下发一条**新指令**，留痕一一对应）、`PUT /iot/ota/{taskId}/cancel` | `ota/controller/IotOtaController.java`、`IotOtaTaskServiceImpl.retry/cancel` | 否 | `#retry_failedTask_dispatchesNewCommand/#retry_disabledDevice_rejected/#cancel_sentTask_cancelled` |
| §4#1 回执 | `POST /iot/ota/report`（`@SaCheckPermission("iot:ota:report")`，网关/运维代报）+ **MQTT 旁路**：设备经 `event/post` 上报 `OTA_PROGRESS`/`OTA_RESULT` → `IotEventStreamConsumer` 落库事件后旁路调用 `handleOtaEvent`（内部吞异常，不阻断事件消费）。回执走状态机 CAS + 进度单调不回退 + 成功时**回写 `iot_device.firmware_version`** | `ota/service/impl/IotOtaTaskServiceImpl.doReport/handleOtaEvent`、`event/IotEventStreamConsumer.java:60-66,196-206` | 否 | `#report_sentToUpgrading_updatesProgress/#report_progressRegression_keptMonotonic/#report_upgradingToSuccess_writesDeviceFirmwareVersion/#report_sentToFailed_writesReason/#report_casConflict_throws/#report_withoutTaskId_matchesLatestActiveTask/#handleOtaEvent_*`（5 例） |
| §4#1 版本一致性 | `GET /iot/ota/versionCheck?firmwareId=&onlyBehind=`：返回"设备当前版本 ≠ 目标版本"的在用设备清单，`behind` 用**分段数值比较**（`1.10.0 > 1.9.0`，空版本视为落后）；升级成功时回写 `iot_device.firmware_version`（**该列已存在，未加列**） | `ota/domain/vo/IotOtaVersionCheckVo.java`、`IotOtaTaskServiceImpl.checkVersion/isBehind` | **否**（复用既有列，见 §2.3） | `#checkVersion_returnsMismatchedDevices/#isBehind_numericSegments` |
| §4#1 超时兜底 | `IotOtaTimeoutTask`：`@JobExecutor(name="iotOtaTimeoutTask")` + **`@Scheduled(cron="0 */5 * * * ?")` 本地兜底 + `SchedulerFallbackUtils`（开关判断 + Lock4j 分布式锁）**，把 `SENT/UPGRADING` 且 `sent_time < now - nexus.iot.ota.timeout-minutes`(默认 60) 的任务 CAS 置 `TIMEOUT`；以 `TenantHelper.ignore` 跨租户扫描 | `task/IotOtaTimeoutTask.java`、`IotOtaTaskServiceImpl.markTimeout` | 否 | `IotOtaTimeoutTaskTest` 4 例 + `IotOtaTaskServiceTest#markTimeout_marksActiveTasksTimeout` |
| §4#1 权限/菜单 | 13 条权限码种子（固件 5 + 任务 6 + 版本核对 1 + 2 个 C 菜单），menu_id 从 **6137/6138/6139/6147/6148/6150-6155/6157/6158**（逐个校验空闲，见 §3.1）；所有新端点均带 `@SaCheckPermission` | 增量脚本 §3 | 是（ry-vue DML） | 无（种子脚本，校验 SQL 见 §5.3） |
| §4#1 定时任务定义 | `sj_job`（id=29, executor=`iotOtaTimeoutTask`, cron `0 */5 * * * ?`）+ `sj_job_executor`（id=45）种子 | 增量脚本 §4 | 是（ry-vue DML） | 无 |

### 1.1 设计取舍（按要求说明理由）

1. **回执端点采用"专用 HTTP 端点 + MQTT 事件旁路"双通道**（审计允许自行取舍）：
   - 设备**直连 MQTT** 时，OTA 进度/结果以 `event/post`（事件码 `OTA_PROGRESS`/`OTA_RESULT`）上报 ——
     设备身份由既有 MQTT 认证（`clientId == deviceCode`）保证，**无需为设备开放免登录 HTTP 端点**；
     事件落库后由 `IotEventStreamConsumer` 旁路推进任务状态机（`handleOtaEvent` 内部吞异常，不影响事件消费）。
   - 同时提供 `POST /iot/ota/report` 供**设备侧网关/运维代报**（该端点带 `@SaCheckPermission("iot:ota:report")`，
     不引入新的免认证面）。
   - 未复用 `property/set_reply` 语义承载 OTA 进度：`set_reply` 已被"下行指令回执"占用（`handleReply` 更新
     `iot_device_command`），OTA 需要**进度+结果多态与任务级状态机**，混用会让"指令是否下发成功"与
     "升级是否成功"两种语义纠缠，故选择独立语义通道。
2. **`CANCELLED` 是审计状态机之外的显式新增**：审计列出 6 态，但"取消"是明确要求的端点，必须有终态承载，
   否则只能落 `FAILED`，无法区分"设备升级失败"与"人工取消"。已在 `IotOtaStatus` 类注释与本报告说明。
3. **`iot_ota_task` 不建业务唯一键，改为服务层幂等**：同设备重试/取消后重新下发是合法操作，
   `(tenant_id, device_id, firmware_id)` 唯一键会挡住重试；故用"同设备+同固件**不允许并存进行中任务**"
   （`selectCount + in(ACTIVE)`）表达幂等，回执幂等由状态机 CAS 保证。
4. **`file_oss_id` 而非下载 URL**：固件包本体走既有 `ruoyi-common-oss`（本批次不新增上传端点），
   表内只登记 `file_oss_id/file_name/file_size/checksum`；下载地址由设备侧网关按 OSS 配置解析为预签名 URL
   （见 §7 设备侧协议）。
5. **`hardware_model` 仅登记、不参与匹配**：`iot_device` 无"硬件型号"列，且该表在 `ry-iot.sql` 中是
   **无列名位序 INSERT** 的存量表（加列会让 24 行存量 INSERT 全部失败），故不新增列；
   产品维度用 `product_key` ↔ `iot_product.product_code` 做强校验（非空时），硬件型号留给后续批次。
6. **重试重新下发"新指令"而非重发原指令**：每次尝试在 `iot_device_command` 中一一对应，留痕清晰、
   便于区分"第几次尝试的设备回执"；`retry_count` 累加作为汇总计数。

---

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| **前端页面与 API 层** | **未做**（本批次明确排除） | 见 §6「前端需求」；后端 15 个端点 + 13 个权限码种子已就绪。 |
| **固件包上传端点** | **未做（刻意）** | 复用 `ruoyi-common-oss` 通用上传端点拿 `ossId`，本模块只做登记。若前端需要"上传并登记"一键化，建议在 `plus-ui` 侧串联两步（上传 → `POST /iot/firmware`）。 |
| **差分/增量升级、灰度分批、强制升级、按设备型号精确匹配** | **未做** | MVP 只做"全量包 + 按设备/按分组下发"。差分包需设备侧支持 bsdiff 类协议；灰度可按 `batch_no` 分批下发（当前已具备批次号，可人工分批）实现，无需改表。 |
| **设备侧下载/刷写超时与断点续传** | **未做** | 平台侧只保证"指令已下发 + 状态机可追踪 + 超时兜底"，实际下载/刷写由设备固件实现（见 §7 契约）。 |
| **`iot_device.firmware_version` 的来源优先级** | **部分** | 升级成功时由回执回写（服务端权威）；设备自行上报的版本（`property/post` 中的版本属性）**未**接入自动回写 —— 需在管道层增加"版本属性 → 设备表"的映射，涉及改 `IotStreamConsumer`，建议后续批次评估（当前可在设备列表/运维详情人工维护）。 |
| **OTA 进度落 SSE 实时推送** | **未做** | 复用 `IotRealtimeSseManager` 可推送进度，但会改动实时推送管理器；前端可先轮询任务列表（5~10s）获取进度。 |
| **`iot_firmware` 删除后的任务可追溯性** | **部分（已兜底）** | 删除仅限 DRAFT/DISABLED（RELEASED 必须先停用）；`iot_ota_task.target_version` 冗余保存目标版本，故固件行删除后任务仍可追溯版本号，但**丢失包与校验值**（属可接受的 MVP 取舍）。 |
| **多实例下 `internalPublish` 的适用性** | **部分（继承 B10-b 限制）** | 内嵌 Broker 每实例独立，跨实例下发需改外部 Broker 客户端；OTA 复用该通道，故限制同源。 |
| **端点级集成测试（MockMvc/真实库）与权限码合并验证** | **未做** | 按"单测覆盖核心断言"交付；留给主代理最终全量回归。 |
| **`sys_dict` 字典**（固件状态/任务状态/校验算法） | **未做** | 属 `ry-vue.sql` 独占文件；当前用常量 + `@Pattern` 白名单校验，前端可先硬编码（见 §6.3）。 |

---

## 3. 主脚本合并清单

### 3.1 `script/sql/ry-vue.sql`（**独占文件，本代理未编辑**，请主代理串行合并）

- **锚点 A**（IoT 平台下最后一条已存在菜单整行，`ry-vue.sql` 中 `menu_id=6080`）：
  ```sql
  INSERT INTO `sys_menu` VALUES (6080, '数据转发', 6000, 9, 'forwardRule', 'iot/forwardRule/index', '', 1, 0, 'C', '0', '0', 'iot:forwardRule:list', 'shuffle', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT数据转发规则(HTTP Webhook)');
  ```
- **待插入的行**：`script/sql/update/2026-09-20-iot-ota.sql` **第 3 节**（13 条 `INSERT IGNORE`，可重复执行），
  内容为 menu_id `6137,6138,6139,6147,6148,6150,6151,6152,6153,6154,6155,6157,6158`。
  - 父菜单：`6157 固件管理`（path `firmware`，component `iot/firmware/index`，排序 10）、
    `6158 升级任务`（path `ota`，component `iot/ota/index`，排序 11），均挂在 **6000（IoT平台）**，
    接在既有 6020(设备)/6050(分组)/6060(事件)/6070(指令)/6080(转发) 之后。
  - 按钮权限：`iot:firmware:list/query/add/edit/remove/release`、`iot:ota:list/query/create/retry/cancel/report/versionCheck`。
- **menu_id 占用校验记录**（从 `script/sql/ry-vue.sql` 提取的 6000-6199 段全部已占用 ID）：
  `6000,6010-6015,6020-6026,6030-6034,6040-6045,6050-6059,6060-6063,6070-6073,6075,6080-6085,6090,6091,`
  `6100-6136,6140-6146,6161-6178,6181-6187,6190-6194`
  → 本批次使用的 `6137,6138,6139,6147,6148,6150-6155,6157,6158` **全部空闲**（`6149/6156/6159/6160` 亦空闲但未使用）。
- **前端组件路径约定**（合并菜单时需与前端实现一致）：`iot/firmware/index`、`iot/ota/index`。

### 3.2 `script/sql/ry-vue.sql` 的 `sj_job` / `sj_job_executor`（**同上，请主代理合并**）

- **锚点 B**（已存在的 IoT 心跳任务整行）：
  ```sql
  INSERT INTO `sj_job` VALUES (5, 'dev', 'biz_5', 'ruoyi_group', 'IoT设备心跳检测任务', '', 1, 1781715600000, 1, 1, 4, 1, 'iotDeviceHeartbeatTask', 3, '0 */5 * * * ?', 1, 60, 3, 1, 1, 51, 0, '', 1, '{}', '', '', 1, '2026-06-18 17:32:23', '2026-09-14 13:18:12');
  ```
- **待插入的行**：增量脚本 **第 4 节** 两行 ——
  `sj_job(id=29, biz_29, 'IoT OTA升级超时检测任务', executor_info='iotOtaTimeoutTask', cron='0 */5 * * * ?')` 与
  `sj_job_executor(id=45, executor_name='iotOtaTimeoutTask')`（均 `INSERT IGNORE`）。
  - 现有 `sj_job` id：1-28、100-103、200 → 用 **29**；现有 `sj_job_executor` id：1-44、200 → 用 **45**。
  - 说明：生产 `snail-job.enabled=false`，任务由 `@Scheduled` 本地兜底执行；此种子仅让
    `snail-job.enabled=true` 的环境也能调度（与 B1 批次口径一致）。

### 3.3 `script/sql/ry-iot.sql`（**本批次独占，已直接修改，无需主代理合并**）

- 文件末尾 `SET FOREIGN_KEY_CHECKS = 1;` 之前追加 **2 张新表**：`iot_firmware`、`iot_ota_task`。
- **未 ALTER 任何存量表**（`iot_device.firmware_version` 列已存在），故 `iot_device` 的 24 行无列名位序 INSERT 不受影响。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| # | 库 | 对象 | 变更 | 幂等/前置 | 需清 Redis |
|---|---|---|---|---|---|
| 1 | `ry-iot` | `iot_firmware` | 新建表（固件登记：产品型号/版本/OSS 文件/大小/校验算法与校验值/发布状态/发布时间/变更说明 + 审计 + `del_flag`；`uk_version(tenant_id, product_key, version)`、`idx_status(tenant_id, status)`） | `CREATE TABLE IF NOT EXISTS`；无前置数据；**唯一键前置检查**：`SELECT tenant_id, product_key, version, COUNT(*) FROM iot_firmware GROUP BY 1,2,3 HAVING COUNT(*)>1;`（新表必为空） | 否 |
| 2 | `ry-iot` | `iot_ota_task` | 新建表（升级任务：固件/目标版本/当前版本/设备/分组/批次号/指令ID/状态/进度/重试次数/下发·开始·结束时间/失败原因/回执消息ID + 审计；`idx_device`、`idx_status`、`idx_firmware`、`idx_batch`） | 同上；无唯一键（幂等在服务层，见 §1.1-3） | 否 |
| 3 | `ry-vue` | `sys_menu` | `INSERT` 13 条（固件管理 6157 + 5 按钮 6137/6138/6139/6147/6148；升级任务 6158 + 6 按钮 6150-6155） | `INSERT IGNORE`；主键被占用需改 ID（占用核查见 §3.1） | 否 |
| 4 | `ry-vue` | `sj_job` | `INSERT` 1 条（id=29，executor `iotOtaTimeoutTask`，cron `0 */5 * * * ?`） | `INSERT IGNORE`；id 被占用需改 ID | 否 |
| 5 | `ry-vue` | `sj_job_executor` | `INSERT` 1 条（id=45，`iotOtaTimeoutTask`） | `INSERT IGNORE`；id 被占用需改 ID | 否 |

- 增量脚本：`script/sql/update/2026-09-20-iot-ota.sql`（**可重复执行**；**无 ALTER、无存量数据清洗，不需要停机窗口**）。
- 主脚本 `script/sql/ry-iot.sql` 已同步（新环境按主脚本导入即可，无需执行增量脚本）。
- 建议清理 Redis：**不需要**（无历史键冲突，本批次未新增 Redis 键）。
- 字符集核对：2 张新表均显式声明 `CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci`、`ENGINE=InnoDB`、
  `ROW_FORMAT=Dynamic`、`tenant_id varchar(20) NOT NULL DEFAULT '000000'`，符合 `doc/system/specs/charset-encoding-standard.md` §4/§5。

---

## 5. 验证记录

### 5.1 命令（PowerShell，`-D` 带点参数已加引号）

```powershell
# 本批次新增测试（3 个类）
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  "-Dtest=IotFirmwareServiceTest,IotOtaTaskServiceTest,IotOtaTimeoutTaskTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"

# 本模块全量回归（19 个测试类，含 B3/B10-a/B10-b 既有测试）
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  "-Dtest=Iot*Test" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 5.2 结果

**全量回归：`BUILD SUCCESS` / `Tests run: 204, Failures: 0, Errors: 0, Skipped: 0`（19 个测试类全部执行）**

| 测试类 | 用例数 | 结果 |
|---|---|---|
| `com.nexus.iot.firmware.service.IotFirmwareServiceTest`（**新建**） | 13 | ✅ |
| `com.nexus.iot.ota.service.IotOtaTaskServiceTest`（**新建**） | 30 | ✅ |
| `com.nexus.iot.task.IotOtaTimeoutTaskTest`（**新建**） | 4 | ✅ |
| 既有 16 个类（MQTT 认证/授权、Topic 解析、消息管道、Stream 消费、告警规则/记录、分组标签、生命周期、事件、指令、转发、数据 LIMIT、调度兜底等） | 157 | ✅（本批次改动 `IotEventStreamConsumer` 后**未破坏**既有用例） |
| **合计** | **204** | **0 失败 / 0 错误 / 0 跳过** |

3 个新测试类均带 `@Tag("local")` + `@Tag("dev")`（本仓库 surefire `<groups>${profiles.active}</groups>`，
只标 `dev` 会在默认 `local` profile 下被静默跳过）。

**任务要求的 6 项必测覆盖情况**：
① 固件 CRUD 与发布状态机 → `IotFirmwareServiceTest`（13 例）；
② 按分组批量建任务 → `#createBatchTask_byGroup_skipsDisabledDevice`（含禁用跳过与批次号/分组号断言）；
③ 离线/禁用设备拒绝下发 → `#createTask_disabledDevice_rejected`、`#createTask_decommissionedDevice_rejected`、`#retry_disabledDevice_rejected`；
④ 下发失败落 FAILED → `#createTask_dispatchFailure_marksFailed`（断言失败原因含"未下发"）；
⑤ 回执状态机（含非法流转被拒）→ `#report_*` 9 例（含终态拒绝、未知状态拒绝、CAS 冲突、进度单调、成功回写设备版本）；
⑥ 超时任务被标记 → `#markTimeout_marksActiveTasksTimeout` + `IotOtaTimeoutTaskTest` 4 例。

### 5.3 SQL 校验（人工/脚本核对）

```sql
-- 表结构（ry-iot）
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, ROW_FORMAT FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ry-iot' AND TABLE_NAME IN ('iot_firmware','iot_ota_task');
-- 期望：2 行，均 InnoDB / utf8mb4_unicode_ci / Dynamic

-- 唯一键 / 索引
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='ry-iot' AND TABLE_NAME IN ('iot_firmware','iot_ota_task')
 GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE;
-- 期望：iot_firmware uk_version(tenant_id,product_key,version) NON_UNIQUE=0 + idx_status(tenant_id,status)
--       iot_ota_task idx_device(tenant_id,device_id,create_time) / idx_status(tenant_id,status,sent_time)
--                    / idx_firmware(tenant_id,firmware_id) / idx_batch(batch_no)

-- 权限码与任务种子
SELECT menu_id, menu_name, parent_id, perms FROM `ry-vue`.`sys_menu`
 WHERE menu_id IN (6137,6138,6139,6147,6148,6150,6151,6152,6153,6154,6155,6157,6158) ORDER BY menu_id;
-- 期望：13 行
SELECT id, executor_info, trigger_cron FROM `ry-vue`.`sj_job` WHERE id = 29;   -- iotOtaTimeoutTask / 0 */5 * * * ?
SELECT id, executor_name FROM `ry-vue`.`sj_job_executor` WHERE id = 45;        -- iotOtaTimeoutTask
```

### 5.4 过程中修正的问题（供后续代理避坑）

1. **`TenantHelper.ignore(Supplier)` 只有一个参数**：单测 `mockStatic` 时误按 `dynamic(tenantId, supplier)`
   写成 `getArgument(1)`，导致 `Index 1 out of bounds for length 1`（4 个用例全红）。正确为 `getArgument(0)`。
2. **`LambdaUpdateWrapper.setSql(...)` 不落 `getParamNameValuePairs()`**：断言"累加 retry_count"必须查
   `wrapper.getSqlSet()`（返回 `retry_count = retry_count + 1`），而非参数值集合。
3. **MyBatis-Plus `in(column, collection)` 的参数存放形态**：断言不要依赖单一形态；本次改为
   "整集合 / 逐元素 / SQL 片段"三选一（并把实际 params 与 sql 打进断言消息），避免升级 MP 版本即红。
4. **`canTransition(from, to)` 拒绝"同状态流转"** → 设备分批上报进度时 `UPGRADING → UPGRADING` 会被误拒。
   已新增 `IotOtaStatus.canReport` 显式放行该分支（**这是单测发现的真实缺陷，非仅测试问题**），
   `doReport` 已改用 `canReport`。

---

## 6. 前端需求（本批次不做，交前端代理）

### 6.1 新增 API（建议 `plus-ui/src/api/iot/`）

```ts
// firmware/index.ts
listFirmware(query)                    GET    /iot/firmware/list        // query: firmwareName/productKey/version/status + 分页
optionFirmware(query)                  GET    /iot/firmware/optionList  // 下拉（建议前端只传 status=RELEASED）
getFirmware(firmwareId)                GET    /iot/firmware/{firmwareId}
addFirmware(data)                      POST   /iot/firmware
updateFirmware(data)                   PUT    /iot/firmware
delFirmware(ids)                       DELETE /iot/firmware/{ids}
changeFirmwareStatus(firmwareId, status, remark)  PUT /iot/firmware/{firmwareId}/status?status=RELEASED|DISABLED

// ota/index.ts
listOtaTask(query)                     GET    /iot/ota/list            // query: deviceId/firmwareId/status/batchNo + 分页
getOtaTask(taskId)                     GET    /iot/ota/{taskId}
createOtaTask({deviceId, firmwareId, remark})        POST /iot/ota/create
createOtaTaskBatch({firmwareId, groupId, includeSubGroup, remark})  POST /iot/ota/batch
retryOtaTask(taskId)                   PUT    /iot/ota/{taskId}/retry
cancelOtaTask(taskId, reason)          PUT    /iot/ota/{taskId}/cancel?reason=...
reportOtaTask({deviceId|deviceCode, taskId, status, progress, version, failReason, msgId})  POST /iot/ota/report
checkOtaVersion(firmwareId, onlyBehind) GET   /iot/ota/versionCheck?firmwareId=&onlyBehind=
```

### 6.2 页面

| 页面 | 路由/组件（menu） | 要点 |
|---|---|---|
| 固件管理 | `iot/firmware/index`（6157） | 列表：名称、产品型号、版本、文件名、大小（格式化 KB/MB）、校验算法+校验值（截断+复制）、发布状态（DRAFT/RELEASED/DISABLED 标签色）、发布时间、变更说明。操作：新增/修改（**仅草稿可改**，已发布行禁用编辑按钮）、删除（已发布后端会报错，需提示"请先停用"）、**发布/停用**（二次确认；发布前必须有包与校验值）。**固件包上传**：先调 `ruoyi-common-oss` 上传端点拿 `ossId`，再填 `fileOssId/fileName/fileSize/checksumType/checksum`（前端可本地计算 MD5 后填入，避免手抄） |
| 升级任务 | `iot/ota/index`（6158） | 列表：设备、目标版本、当前版本、状态（PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED 标签色）、进度条（`progress`）、批次号、下发/结束时间、失败原因、重试次数、关联指令ID（可跳设备指令页）。筛选：设备、固件、状态、批次号。行操作：重试（仅 FAILED/TIMEOUT）、取消（仅进行中，二次确认 + 原因输入）、详情抽屉（含 `commandId` 跳转）。顶部按钮：**单设备下发**（选固件→选设备，只列 ACTIVE 设备）、**按分组批量下发**（选固件→选分组 + "包含子分组"开关 → 结果弹窗展示"成功 N / 跳过 M"与逐条跳过原因）。**版本核对**入口：选固件 → 弹窗/子页展示不一致设备（含"仅看落后"开关，`behind` 高亮） |
| 设备管理（既有页扩展，可选） | `iot/device/index`（6020） | 行操作可增加"下发升级"（跳转/弹窗选已发布固件）；`firmwareVersion` 列已存在，升级成功后会变化，建议显示最近刷新时间 |

### 6.3 类型与字典

- 所有 ID（`firmwareId`/`taskId`/`deviceId`/`groupId`/`commandId`）均为雪花 ID，后端已全局序列化为字符串，
  **前端一律按 `string` 处理**。
- 若需下拉字典，需在 `ry-vue.sql` 增加 `sys_dict_type`/`sys_dict_data`：
  `iot_firmware_status`（DRAFT/RELEASED/DISABLED）、`iot_firmware_checksum_type`（MD5/SHA256）、
  `iot_ota_status`（PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED）。
  **本批次未落字典**（属 `ry-vue.sql` 独占文件），前端可先硬编码常量。

---

## 7. 设备侧协议说明（OTA 契约）

### 7.1 下行：升级指令（平台 → 设备）

- **Topic**：`iot/{tenantId}/{deviceCode}/event/set`（由既有 `iot_device_command` 通道下发，service=`SERVICE_INVOKE`）
- **Payload**（JSON，`params` 为 OTA 专有字段）：

```json
{
  "commandId": "1812345678901234567",
  "msgId": "1812345678901234567",
  "type": "SERVICE_INVOKE",
  "service": "otaUpgrade",
  "params": {
    "otaTaskId": "1812345678901234999",
    "firmwareId": "1812345678901234000",
    "version": "2.0.0",
    "fileOssId": "oss-2001",
    "fileName": "fw-2.0.0.bin",
    "fileSize": 4096,
    "checksumType": "MD5",
    "checksum": "abc123..."
  },
  "ts": 1758326401000
}
```

- 设备侧要求：
  1. **ClientID 必须等于 `deviceCode`**（B3 批次已强制的既有约束，否则无法连接）；
  2. 用 `fileOssId` 向平台网关换取固件包下载地址（预签名 URL，平台不直接暴露 OSS 凭据）；
  3. 下载完成后**必须**按 `checksumType`/`checksum` 校验，校验失败直接回报 FAILED（不要刷写）；
  4. 升级期间保持 MQTT 在线（否则任务会因超时被置 TIMEOUT）。

### 7.2 上行 A：指令回执（设备 → 平台，复用既有通道）

- **Topic**：`iot/{tenantId}/{deviceCode}/property/set_reply`
- **Payload**：`{"commandId":"1812345678901234567","success":true,"message":"已收到升级指令","payload":"..."}`
- 语义：表示"设备已收到指令"（更新 `iot_device_command` 状态），**不代表升级成功**。

### 7.3 上行 B：升级进度 / 结果（设备 → 平台，OTA 主通道）

- **Topic**：`iot/{tenantId}/{deviceCode}/event/post`
- **进度事件**：

```json
{
  "eventCode": "OTA_PROGRESS",
  "eventName": "固件升级进度",
  "eventLevel": "INFO",
  "eventTime": 1758326410000,
  "payload": "{\"otaTaskId\":\"1812345678901234999\",\"progress\":40,\"version\":\"2.0.0\"}"
}
```

- **结果事件**：

```json
{
  "eventCode": "OTA_RESULT",
  "eventName": "固件升级结果",
  "eventLevel": "INFO",
  "eventTime": 1758326500000,
  "payload": "{\"otaTaskId\":\"1812345678901234999\",\"success\":true,\"progress\":100,\"version\":\"2.0.0\"}"
}
```

- 约定：
  1. 事件信封的 `payload` 是**字符串形式的 JSON**（与既有 `event/post` 一致），平台会二次解析；
  2. `otaTaskId` 取下行指令 `params.otaTaskId`（原样回传）；**缺失时平台回落"该设备最近一条进行中任务"**；
  3. `eventCode` 必须以 `OTA_` 开头，且含 `RESULT`/`FINISH`/`DONE` 视为结果事件，否则视为进度事件；
  4. `success` 支持 `true/false`、"true/false"、"1/0"、"success"、"ok"（大小写不敏感）；
  5. **进度单调**：平台不会把进度改小；`SUCCESS` 强制置 100；
  6. 终态（SUCCESS/FAILED/TIMEOUT/CANCELLED）后**再上报一律被拒**（状态机 CAS，重复回执幂等）；
  7. 升级成功后平台自动把 `version` 写入 `iot_device.firmware_version`（版本一致性核对的权威来源）。

### 7.4 上行 C：HTTP 回执（网关/运维代报）

```
POST /iot/ota/report          （需登录 + iot:ota:report 权限）
{ "deviceId": "1", "taskId": "100", "status": "UPGRADING|SUCCESS|FAILED",
  "progress": 40, "version": "2.0.0", "failReason": "...", "msgId": "可选幂等/取证键" }
```

### 7.5 超时语义

- 指令下发后 `nexus.iot.ota.timeout-minutes`（默认 **60**）分钟内未收到任何进度/结果回执 →
  任务被 `IotOtaTimeoutTask`（每 5 分钟）置 `TIMEOUT`，之后可由运维**重试**或**取消**。
- 建议设备侧在开始刷写前先发一条 `OTA_PROGRESS`（progress=0），以进入 `UPGRADING` 并重置业务预期。

---

## 8. 风险与回滚说明

| 风险 | 说明 | 回退方式 |
|---|---|---|
| 主脚本 `ry-iot.sql` 已改 | 新环境导入会多出 2 张表；**未 ALTER 存量表**，`iot_device` 位序 INSERT 不受影响 | `DROP TABLE iot_firmware, iot_ota_task;` + 删除主脚本末尾对应段落（改动集中在文件末尾，diff 干净） |
| 事件流新增 OTA 旁路调用 | `IotEventStreamConsumer` 多了一个 `IIotOtaTaskService` 构造参数，每个 `event/post` 事件都会多一次"事件码是否 OTA_*"的判断（字符串前缀比较，开销可忽略）；`handleOtaEvent` 内部吞异常，**不会**把非 OTA 事件或 OTA 处理失败变成毒消息 | 删除 `IotEventStreamConsumer` 中的字段与 2 行调用（无其他副作用） |
| `iot_device.firmware_version` 被回执回写 | 设备升级成功后版本列被服务端改写；若设备上报的 `version` 与目标不一致，平台仍按 `target_version` 回写（以平台指令为准） | 还原 `doReport` 中 `deviceMapper.update(...set(firmwareVersion))` 分支 |
| 新增权限码未合并 | 未合并 `sys_menu` 种子时，非超管调用新端点会 403（超管正常） | 合并 §3.1 的 13 行，或临时放宽 `@SaCheckPermission` |
| 批量下发"部分成功" | 被跳过设备不会重试（需人工再发起）；返回体 `messages` 逐条给出原因 | 前端提示"跳过 M 台"并提供"仅对失败设备重发"入口（当前需人工按设备再下发） |
| 超时兜底误判 | 大包固件（数十分钟）可能超过默认 60 分钟窗口被置 TIMEOUT（但**不会**中断设备升级，设备仍可能稍后回报 SUCCESS —— 此时回执会被状态机拒绝并记日志） | 调大 `nexus.iot.ota.timeout-minutes`；或先取消再重新下发 |
| `sj_job` 种子 id 冲突 | id=29/45 若已被其他批次占用，`INSERT IGNORE` 会静默跳过（任务在 `snail-job.enabled=true` 环境下不会被调度，但 `@Scheduled` 兜底仍生效） | 改用空闲 id 并更新 SnailJob 后台 |

**回滚步骤**：删除 `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/{firmware,ota}/`、
`.../task/IotOtaTimeoutTask.java`、`src/test/java/com/nexus/iot/{firmware,ota,task/IotOtaTimeoutTaskTest.java}/`；
还原 `event/IotEventStreamConsumer.java`（移除 OTA 旁路字段与调用）、`script/sql/ry-iot.sql`；
删除 `script/sql/update/2026-09-20-iot-ota.sql`；清理 §4 的 2 张表与 13 条 `sys_menu` + 2 条 `sj_job*` 种子。
