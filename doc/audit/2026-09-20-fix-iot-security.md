# IoT 安全与消息管道 P0 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-iot.md` 的 **IOT-MQTT-01 / IOT-MQTT-02 / IOT-MQTT-03 / IOT-MQTT-05 / IOT-PIPE-01 / IOT-PIPE-03 / IOT-PIPE-04 / IOT-DATA-01**（B3 批次） | 分支: `feature/erp-mes-iot-audit-fix`
> 未使用任何 git 命令；未编辑 `ry-vue.sql` / `ry-erp.sql` / `ry-mes.sql` / `application*.yml` / `plus-ui/**` / `doc/system/数据库增量变更记录.md`。

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **IOT-MQTT-01 / 02** | 认证身份口径统一为 **clientId**：clientId 为空 → 拒；`username` 非空且 ≠ clientId → 拒（防冒充/顶号）；被认证设备 = clientId。常量时间密钥比较保留 | `IotMqttAuthenticator.java:14-24,38-72` | 否 | `IotMqttAuthenticatorTest` 10 例，核心 `checkValid_usernameDiffersFromClientId_rejected`（修复前为 true）、`checkValid_usernameIsOtherDevice_rejected` |
| **IOT-MQTT-01** | 授权策略身份一律取 `client`（`user` 不再参与身份判定，仅保留签名） | `IotMqttAuthorizatorPolicy.java:44-46,80-82` | 否 | `IotMqttAuthorizatorPolicyTest#canWrite_identityComesFromClientIdNotUsername` |
| **IOT-MQTT-01** | `publishToStream`：topic 解析出的 deviceCode ≠ clientId → **直接丢弃 + 计数 + WARN**（含累计丢弃数，日志可读） | `IotMessagePipelineService.java:57-70`、计数器 `deviceMismatchDroppedCount` | 否 | `IotMessagePipelineServiceTest#publishToStream_deviceCodeMismatch_droppedAndCounted` |
| **IOT-MQTT-01** | 消费端：topic 租户 ≠ 设备记录租户 → **ACK + return 拒绝入库**（原为 warn 后继续写，跨租户注入） | `IotStreamConsumer.java:187-205`（原 `:309-314` 的 warn 分支已上移为拒绝分支） | 否 | `IotStreamConsumerTest#processBatch_tenantMismatch_ackAndNotPersisted` |
| **IOT-MQTT-03** | `canWrite`/`canRead` 增加 **topic 第 3 段必须等于自己 deviceCode**；订阅侧显式拒绝通配符设备段（`iot/{t}/#`、`iot/{t}/{他人}/#`、`iot/{t}/+/...`）；新增 `extractDeviceCode`/`isWildcardSegment` | `IotMqttAuthorizatorPolicy.java:56-70,100-125,138-166` | 否 | `IotMqttAuthorizatorPolicyTest` 14 例，核心 `canRead_tenantWildcard_denied`、`canWrite_otherDeviceTopicSameTenant_denied` |
| **IOT-PIPE-01** | 幂等去重键：新增 `source_msg_id`（**Redis Stream entry id**，最贴近 exactly-once）+ 唯一索引 `uk_source_msg_id`；消费端唯一键冲突视为「已处理成功」→ ACK 并**跳过** afterPersist（不重复触发缓存/告警） | `IotDeviceData.java:96-106`、`IotStreamConsumer.java:200-292`、`ry-iot.sql:100-109`、`script/sql/update/2026-09-20-iot-security-pipeline.sql` | **是**（DDL） | `IotStreamConsumerTest#processBatch_duplicateKey_treatedAsAlreadyProcessed` |
| **IOT-PIPE-03** | `data_type` 与实际值列一致：空 dataType **按值内容推断**（不再硬写 NUMERIC）；声明 NUMERIC 但解析失败 → 落 `text_value` 且 data_type **纠正为 TEXT**；支持 `STRING` 别名；**删除死代码分支**（`value instanceof Number` 在 Stream 链路上永不成立，改为字符串内容推断）；`applyValue` 回写生效类型 | `IotDataValueResolver.java:30-115`（重写）、`IotStreamConsumer.java:298-333` | 否 | `IotDataValueResolverTest`（追加 6 例）、`IotStreamConsumerTest#buildData_*` 6 例 |
| **IOT-PIPE-04** | `collect_time` 合理性校验：设备时间戳超出 `[now-7d, now+5min]` → 改用**服务端时间** + WARN（含 deviceCode/driftMs）+ 计数；阈值可配（`nexus.iot.pipeline.collect-time-*-tolerance-ms`） | `IotMessagePipelineService.java:73-74,105-155`、`IotPipelineProperties.java:54-68` | 否 | `IotMessagePipelineServiceTest#resolveCollectTime_*` 5 例（未来 2100 年、1970 年、边界、区间内、null） |
| **IOT-DATA-01** | `selectHistory` 增加 `limit` 参数并**下推 DB `LIMIT n`**；Controller 删除内存 `subList` 截断；MES 归档查询用 `ABSOLUTE_MAX_QUERY_LIMIT` 兜底并在截断时 WARN | `IIotDeviceDataService.java:16-70`、`IotDeviceDataServiceImpl.java:56-95`、`IotDeviceController.java:141-161`、`IotDeviceDataQueryServiceImpl.java:101-135` | 否 | `IotDeviceDataLimitTest` 3 例 |
| **IOT-MQTT-05**（顺带） | 新增 `PUT /iot/device/{deviceId}/resetSecret`（`@SaCheckPermission("iot:device:resetSecret")` + `@Log`）：服务端重新生成 UUID 密钥，条件更新（CAS，0 行即视为并发删除），新密钥经**专用 DTO** 一次性返回（不破坏 `IotDeviceVo.deviceSecret` 的 `@JsonIgnore`）；`updateByBo` 的「走专用接口」注释现已成立 | `IotDeviceController.java:186-205`、`IIotDeviceService.java:50-57`、`IotDeviceServiceImpl.java:112-151`、`IotDeviceSecretVo.java`（新建）、增量脚本 §3 | 否（需 `sys_menu` DML） | 见 §2（未覆盖，理由） |

**同型位置一并修**：`IotMqttTopicParser` 的 deviceCode 语义（提示 → 强制校验）在**生产端 + 消费端 + 授权端**三处全部落地，不再是「三处口径互不相同」。

## 2. 未修复 / 部分修复项（含理由与建议）

1. **IOT-MQTT-05 未加前端按钮 / 未加单测**：`plus-ui/**` 不在本批次所有权内 → 需前端代理在设备列表行加「重置密钥」按钮（二次确认 + 一次性展示 + 复制），调用 `PUT /iot/device/{id}/resetSecret`。Service 依赖 Mapper/DB，纯 Mock 断言价值低（权限码种子已随增量脚本给出，见 §3）。
2. **IOT-DATA-01 仅部分闭环**：MES 归档任务按分块窗口显式传上限，需要改 `ruoyi-common-iot` 的 `IotDeviceDataQueryService#getHistoryData` **契约**（不在本批次文件所有权内）。当前 IoT 侧用 `ABSOLUTE_MAX_QUERY_LIMIT = 50000` 兜底 + 达到上限时 WARN，已消除「全量载入内存」的 OOM 风险；**建议主代理让 common-iot/MES 代理补 `limit` 参数**。
3. **IOT-MQTT-03 未设共享/广播 topic 白名单**：全库检索确认当前**没有任何**共享/广播 topic 使用（模拟器与设备均按 `iot/{tenant}/{自己}/...` 发布/订阅），故未凭空引入白名单；如后续新增，需在 `IotMqttAuthorizatorPolicy` 显式放行并在本报告补充。
4. **未触碰的相邻项（属其他批次，避免重叠）**：IOT-MQTT-04（TLS，需 `application*.yml`）、IOT-PIPE-02（`event/post`、`property/set`）、IOT-PIPE-05（消费者名/pending 认领）、IOT-DEV-01（`status` 白名单）、IOT-TASK-01/02、IOT-ALERT-*、IOT-MES-01。其中 IOT-PIPE-05 的「建组用 NEWEST / 重投」风险已被 `uk_source_msg_id` 幂等兜住（重投安全）。
5. **存量数据未回填 `source_msg_id`**：保持 NULL 即可（MySQL 唯一索引允许多个 NULL），因此**无需清洗存量重复行**，这是选择 Stream entry id 而非业务复合键的主要理由之一（另一理由：设备同一毫秒内合法重复上报不会被误判为重复而丢数）。
6. **`script/sql/update/` 目录**：`AGENTS.md` 已声明该目录不再维护（改为直接改主脚本 + 登记变更记录），但本任务明确要求增量脚本，故两者并存：主脚本 `ry-iot.sql` 已同步修改，增量脚本供存量库升级。
7. **跨代理冲突（需主代理协调）**：`ruoyi-iot/src/test/java/com/nexus/iot/task/IotSchedulerFallbackTest.java`（**另一个代理** IOT-TASK-01/02 正在写，落在我的测试目录所有权内）当前**编译失败**，导致整个 ruoyi-iot 测试源集无法编译：
   - 第 39 行 javadoc 内 `{@code 0 */5 * * * ?}` —— `*/` 提前终止块注释 → 6 个语法错误（建议写 `0 *&#47;5 * * * ?`）；
   - 第 75/91/108 行 `mockStatic(SpringUtil.class)` 与 import 的 `SpringUtils` 不一致（修完上一处必然报 cannot find symbol）。
   我**未修改其内容**（避免与在飞写入互相覆盖），改为临时移出 → 跑我的测试 → 原样移回（已成功还原，`%TEMP%\IotSchedulerFallbackTest.java.bak` 保留）。请该代理自行修复后重跑，否则主代理最终的全模块测试也会红。

## 3. 主脚本合并清单

### 3.1 `script/sql/ry-vue.sql`（**独占文件，我未编辑**，请主代理串行合并）

- **锚点**（`ry-vue.sql:92680`，紧邻的已存在整行）：
  ```sql
  INSERT INTO `sys_menu` VALUES (6025, '设备导出', 6020, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:export', '#', 103, 1, '2026-06-17 17:35:58', NULL, NULL, '');
  ```
- **在其后插入 1 行**：
  ```sql
  INSERT INTO `sys_menu` VALUES (6026, '设备密钥重置', 6020, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:resetSecret', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '重置设备密钥(轮换 device_secret, 仅返回一次)');
  ```
- 说明：父菜单 6020（IoT 设备管理），排序 6 接在导出之后；超管（role_id=1）经 `SysPermissionServiceImpl` 返回 `*:*:*`，无需 `sys_role_menu`；其他角色在「角色管理」勾选即可。

### 3.2 `script/sql/ry-iot.sql`（本批次我独占，**已直接修改，无需合并**）

- `iot_device_data` 新增列 `source_msg_id`（`ry-iot.sql:100`）+ 唯一索引 `uk_source_msg_id`（`ry-iot.sql:104`）。

### 3.3 跨库唯一键 tenant 维度核查（主代理追加要求）

`Select-String -Path script\sql\ry-iot.sql -Pattern 'UNIQUE (KEY|INDEX)'` 结果（3 张表）：

| 表 | 唯一键 | 判定 |
|---|---|---|
| `iot_device` | `uk_code(device_code, tenant_id)` | ✅ 已含 `tenant_id`，正确（参考写法） |
| `iot_product` | `uk_code(product_code, tenant_id)` | ✅ 已含 `tenant_id`，正确 |
| `iot_device_data` | （原无）新增 `uk_source_msg_id(source_msg_id)` | ⚪ **豁免**：`source_msg_id` 是 Redis Stream entry id，单条 Stream 内**全局唯一的技术键**（非业务编码/业务键），`tenant_id` 是其函数，加维度冗余无收益 |

**结论：`ry-iot.sql` 不存在「唯一键缺 tenant 维度」的问题**，无需额外补键；备选业务键方案 `(device_id, property_code, collect_time)` 的 tenant 维度已被 `device_id`（全局唯一）覆盖，其前置重复数据检查 SQL 已作为注释写入增量脚本 §0.2。

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| # | 库 | 对象 | 变更 | 幂等/前置 | 需清 Redis |
|---|---|---|---|---|---|
| 1 | `ry-iot` | `iot_device_data` | `ADD COLUMN source_msg_id varchar(64) NULL COMMENT '来源消息ID(Redis Stream entry id,幂等去重键)' AFTER source` | information_schema 守卫；存量行 NULL，唯一索引允许多 NULL → **无需清洗数据** | 否 |
| 2 | `ry-iot` | `iot_device_data` | `ADD UNIQUE INDEX uk_source_msg_id(source_msg_id) USING BTREE` | information_schema 守卫；前置重复检查 SQL 见增量脚本 §0.1 | 否 |
| 3 | `ry-vue` | `sys_menu` | `INSERT` menu_id 6026 按钮权限 `iot:device:resetSecret`（parent 6020, order 6, type F） | `INSERT IGNORE`；主键 6026 若被占用需改 ID | 否 |

执行顺序：`USE ry-iot` → 加列 → 加唯一索引 → `ry-vue` 菜单种子。建议低峰/停机窗口执行 ALTER（大表加索引会锁表）。
脚本路径：`script/sql/update/2026-09-20-iot-security-pipeline.sql`（头部已写明用途/前提/影响库/缓存/幂等）。

## 5. 验证记录

**命令**（PowerShell，`-D` 带点参数已加引号）：
```powershell
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  "-Dtest=IotMqttAuthenticatorTest,IotMqttAuthorizatorPolicyTest,IotMessagePipelineServiceTest,IotStreamConsumerTest,IotDataValueResolverTest,IotDeviceDataLimitTest" `
  "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"
```
> 注意：本仓库 surefire 为 3.5.3，规范里给的 `-DfailIfNoTests=false` 是**旧属性名**，`-am` 构建上游模块时会因「No tests matching pattern」而 FAILURE；必须补 `-Dsurefire.failIfNoSpecifiedTests=false`。**主代理最终全量跑测试时请带上该参数。**

**结果**：`BUILD SUCCESS` / `Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`

| 测试类 | 用例数 | 结果 |
|---|---|---|
| `com.nexus.iot.mqtt.IotMqttAuthenticatorTest`（新建） | 10 | ✅ |
| `com.nexus.iot.mqtt.IotMqttAuthorizatorPolicyTest`（新建） | 14 | ✅ |
| `com.nexus.iot.pipeline.IotMessagePipelineServiceTest`（新建） | 9 | ✅ |
| `com.nexus.iot.pipeline.IotStreamConsumerTest`（新建） | 10 | ✅ |
| `com.nexus.iot.device.service.IotDeviceDataLimitTest`（新建） | 3 | ✅ |
| `com.nexus.iot.pipeline.payload.IotDataValueResolverTest`（**追加** 6 例） | 14（原 8） | ✅ |

**新增用例合计 52 个**（新建 46 + 追加 6），全部为「修复前会失败、修复后会通过」的行为断言，非「不抛异常」式弱断言。测试标签同时带 `@Tag("local")` 与 `@Tag("dev")`：本仓库 surefire `<groups>${profiles.active}</groups>`，默认 profile 为 `local`，若只标 `dev`（模块既有测试的写法）在默认命令下会被**静默跳过**。

**编译期校验**：`ruoyi-iot` 主源码在 `-am` 反应堆中编译通过（27 个模块）；接口签名变更 `selectHistory(deviceId,start,end,limit)` 的全部调用方（`IotDeviceController`、`IotDeviceDataQueryServiceImpl`）已同步，全库 grep 无遗漏调用点。

**未跑**：全模块全量测试（按规范留给主代理统一跑）。

## 6. 风险与回滚说明

| 变更 | 风险 | 回滚 |
|---|---|---|
| 强制 `clientId == deviceCode` | **兼容性风险最高**：若存量设备端用 username 传 deviceCode、而 clientId 是 MAC/序列号等其它值，升级后**无法连接**。上线前须核查设备端/`iot-simulator.html` 的 ClientID 配置（模拟器已按 `ClientID=deviceCode` 实现，风险主要在第三方固件） | 还原 `IotMqttAuthenticator.checkValid` 第 41 行附近的身份判定即可 |
| topic 设备段强校验 | 设备端若订阅 `iot/{tenant}/#` 或 `iot/{tenant}/{他人}/#` 会订阅失败（这正是修复目标）；需设备端改为订阅自己子树 `iot/{tenant}/{自己}/#` | 移除 `canRead`/`canWrite` 中的 `extractDeviceCode` 比对分支 |
| `uk_source_msg_id` 唯一键 | 多行 `insertBatch` 中若含重复行，整批失败 → 回退逐条插入（吞吐下降但**不丢数据**）；重复消息不再重复告警 | `DROP INDEX uk_source_msg_id ON iot_device_data;`（列可保留，消费端回退为「按异常重试」路径） |
| `data_type` 纠正 | 下游若按 `data_type='NUMERIC'` 过滤，历史行仍为旧值（不回填），新行为更正确；布尔/文本上报将不再被标成 NUMERIC | 还原 `IotDataValueResolver.applyValue` 与 `IotStreamConsumer.buildData` 的默认类型逻辑 |
| `collect_time` 纠正 | 设备时钟异常时落库时间变为服务端时间，与设备本地时间不一致（WARN 中记录了原始 deviceTimestamp 与 driftMs 便于取证）；阈值可通过 `nexus.iot.pipeline.collect-time-future-tolerance-ms` / `collect-time-past-tolerance-ms` 调整 | 还原 `resolveCollectTime` 调用为 `new Date(pl.getTimestamp())` |
| `LIMIT` 下推 | 行为等价（原先也是最多返回 limit 条，只是先在内存截断），仅内存占用下降；MES 归档单次最多 50000 行并在截断时 WARN | 还原 `selectHistory` 的 `.last("LIMIT ...")` 与 Controller 的 `subList` |
| 消费端租户不一致拒绝入库 | 若历史脏数据中 `topic_tenant_id` 与设备租户不符（例如设备迁移租户后 topic 未更新），这些消息将被 ACK 丢弃而非「以设备为准」入库 | 还原 `processBatch` 中的拒绝分支为原 warn 分支 |
| 新增权限码 `iot:device:resetSecret` | 未合并 `sys_menu` 种子时，非超管调用会 403（超管正常） | 移除该端点的 `@SaCheckPermission` 或改用 `iot:device:edit` |
