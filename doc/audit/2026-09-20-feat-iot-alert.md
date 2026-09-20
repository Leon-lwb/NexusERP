# IoT 告警落库 + 确认/恢复状态机 + 规则 CRUD 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-iot.md` 的 **IOT-ALERT-01**、**IOT-ALERT-02**（§4 功能缺失清单第 9 项）| 分支: `feature/erp-mes-iot-audit-fix`
> 批次: B10-a（IoT 告警能力补齐）| 工作目录: `D:\workspace\nexus-erp`

---

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| IOT-ALERT-01 | 新增告警记录表 `iot_alert_record`：租户/规则/设备/属性/触发值/级别/状态机（ACTIVE→ACKNOWLEDGED→RESOLVED）/触发·确认·恢复时间与操作人/恢复值 + 审计字段；唯一键 `uk_active_alert(tenant_id, device_id, property_code, active_flag)` 保证**同一设备+属性仅一条未恢复告警**（未恢复 `active_flag=1`，恢复后置 NULL，MySQL 允许多个 NULL → 历史告警可多条）；查询索引 `idx_query(tenant_id, device_id, status, trigger_time)`、`idx_status_time(tenant_id, status, trigger_time)` | `script/sql/ry-iot.sql:188-233`、`script/sql/update/2026-09-20-iot-alert.sql:82-121` | 是（新表） | `IotAlertRecordServiceTest#trigger_noActiveRecord_insertsAndLogsSkippedNotify`、`#trigger_existingActive_mergesWithoutInsert`、`#trigger_duplicateKey_degradesToMerge` |
| IOT-ALERT-01 | 新增通知投递留痕表 `iot_alert_notify_log`：每次 SSE / MES 安灯投递的渠道、目标、结果（SUCCESS/FAILED/SKIPPED）、说明落库，回答"告警有没有发出去、发给了谁、为什么没发" | `script/sql/ry-iot.sql:238-256`、`.../alert/domain/IotAlertNotifyLog.java` | 是（新表） | `IotAlertRecordServiceTest#trigger_noActiveRecord_insertsAndLogsSkippedNotify`（断言 SKIPPED 留痕） |
| IOT-ALERT-01 | `IotAlertNotifier` 重写：**去掉"无在线用户即 return、告警到此为止"**，改为逐渠道返回结果（SSE / MES 安灯），无在线用户时返回 SKIPPED 并**由调用方落库留痕**；移除原"每 (deviceId, alertType) 60s 限频"（限频语义下沉到规则的 `debounce_sec`，避免两层限频误抑制安灯） | `.../pipeline/IotAlertNotifier.java`（全文件） | 否 | `IotAlertRecordServiceTest#trigger_noActiveRecord_insertsAndLogsSkippedNotify` |
| IOT-ALERT-01 | `IotAlertRecordService.trigger`：**先落库**（合并/新增）→ 去抖判定 → 通知 → 投递留痕；消费线程无登录上下文，全程 `TenantHelper.dynamic` 传播租户（避免 tenant_id 落 0 / 跨租户串读）；重复触发只更新 `trigger_value/last_trigger_time` 与描述级别，**保留首次触发时间与确认状态**；并发唯一键冲突退化为合并（幂等） | `.../alert/service/impl/IotAlertRecordServiceImpl.java:222-330` | 否 | 同上 + `#trigger_existingActive_mergesWithoutInsert`、`#trigger_duplicateKey_degradesToMerge` |
| IOT-ALERT-01 | 确认/恢复状态机 + CAS：`acknowledge` 仅 `ACTIVE→ACKNOWLEDGED`，`resolve` 仅 `ACTIVE\|ACKNOWLEDGED→RESOLVED`；状态非法流转抛 `ServiceException`（含当前状态），CAS 影响 0 行抛"状态已被其他操作变更"；恢复时写恢复值/时间并清 `active_flag`（允许同一异常再次触发新记录） | `.../alert/domain/IotAlertStatus.java`、`.../alert/service/impl/IotAlertRecordServiceImpl.java:126-185` | 否 | `IotAlertRecordServiceTest#acknowledge_active_casSuccess`、`#acknowledge_casConflict_throws`、`#acknowledge_resolvedState_rejected`、`#resolve_resolvedState_rejected`、`#resolve_acknowledged_casSuccessAndClearsActiveFlag` |
| IOT-ALERT-01 | 自动恢复：属性值回到正常范围时把未恢复告警置 `RESOLVED`（写恢复值 + 时间 + "系统自动恢复"备注），并清理持续时长计时键 | `.../alert/service/impl/IotAlertRecordServiceImpl.java:187-220` | 否 | `IotAlertRecordServiceTest#autoResolve_valueBackToNormal_resolves`、`#autoResolve_stillBreached_keepsActive` |
| IOT-ALERT-01 | 告警查询/确认/恢复/导出/投递留痕端点（`/iot/alert/**`），全部带 `@SaCheckPermission`：`iot:alert:list/query/ack/recover/export` | `.../alert/controller/IotAlertController.java` | 否 | 端点无集成测试（见 §2） |
| IOT-ALERT-02 | 新增规则表 `iot_alert_rule`：租户、产品范围/设备范围（0=不限）、属性编码、比较符（GT/GE/LT/LE/EQ/NE）、阈值、**持续时长**、**去抖窗口**、告警类型/级别、通知渠道、启用状态、审计字段；唯一键 `uk_rule_scope(tenant_id, product_id, device_id, property_code)`（范围用 0 而非 NULL，使"不限范围"也受唯一键约束） | `script/sql/ry-iot.sql:136-169`、`script/sql/update/2026-09-20-iot-alert.sql:37-62` | 是（新表） | `IotAlertRuleServiceTest`（3 例） |
| IOT-ALERT-02 | 规则 CRUD（分页/详情/新增/修改/删除/启停），BO 完整校验（`@Validated` + `@NotBlank/@NotNull/@Pattern/@Min/@Max/@Size`），唯一键冲突返回可读业务异常，启停用条件更新 | `.../alert/controller/IotAlertRuleController.java`、`.../alert/domain/bo/IotAlertRuleBo.java`、`.../alert/service/impl/IotAlertRuleServiceImpl.java` | 否 | `IotAlertRuleServiceTest` |
| IOT-ALERT-02 | `IotAlertChecker` 改为**从库读规则**：按 设备规则 > 产品规则 > 全局规则 优先级匹配（`selectEnabledRules` 已按具体度降序），支持比较符、持续时长（Redis 计时，首次越限开始计时，持续越限刷新窗口避免"计时键过期后重新计时"）、去抖（`debounce_sec`）；**YAML `alert-thresholds` 仅在库内无匹配规则时兜底**，且键匹配改为大小写不敏感（修正"首字母大写属性永不触发"） | `.../pipeline/IotAlertChecker.java`（全文件重写）、`.../pipeline/IotPipelineProperties.java:66-74` | 否 | `IotAlertCheckerTest`（6 例） |
| IOT-ALERT-02 | 触发时构造告警内容取自规则（类型/级别/比较符/阈值快照），规则未配 `alert_type` 时才回落到原有属性名子串推断 | `.../pipeline/IotAlertChecker.java:243-289` | 否 | `IotAlertCheckerTest#check_ruleBreached_triggersAndPersists` |
| IOT-ALERT-01 | EWMA 异常告警（`ANOMALY`）同样落库：`IotAnomalyDetector` 改调 `IIotAlertRecordService.trigger(..., SOURCE_ANOMALY)`，异常告警不再"仅落日志"；与阈值告警的互斥抑制（`suppressNotify`）保持不变 | `.../pipeline/IotAnomalyDetector.java` | 否 | 复用记录服务用例（ANOMALY 路径无独立用例） |
| IOT-ALERT-01/02 | `sys_menu` 权限码种子：告警记录 6030~6034、告警规则 6040~6045（ID 已逐个校验未被占用：`ry-vue.sql` 中 6000/6010-6015/6020-6026/6100-6201 已占用；6026 由 `2026-09-20-iot-security-pipeline.sql` 占用） | `script/sql/update/2026-09-20-iot-alert.sql:158-180` | 是（ry-vue DML） | 无（种子脚本，见 §5 校验 SQL） |

### 新增文件清单（21 个主代码文件）

```
ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/alert/
├── domain/     IotAlertRule.java  IotAlertRecord.java  IotAlertNotifyLog.java
│              IotAlertStatus.java  IotAlertCondition.java  IotAlertConstants.java
├── domain/bo/  IotAlertRuleBo.java  IotAlertRecordBo.java
├── domain/vo/  IotAlertRuleVo.java  IotAlertRecordVo.java  IotAlertNotifyLogVo.java
├── mapper/     IotAlertRuleMapper.java  IotAlertRecordMapper.java  IotAlertNotifyLogMapper.java
├── service/    IIotAlertRuleService.java  IIotAlertRecordService.java
├── service/impl/ IotAlertRuleServiceImpl.java  IotAlertRecordServiceImpl.java
└── controller/ IotAlertRuleController.java  IotAlertController.java
```

修改的既有文件（4 个）：`IotAlertChecker.java`（重写）、`IotAlertNotifier.java`（重写）、`IotAnomalyDetector.java`（改调用）、`IotPipelineProperties.java`（注释与语义说明）。
另新增 `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/pipeline/IotAlertStateStore.java`：告警链路 Redis 访问的唯一出口
（持续时长计时键 / 去抖窗口键 / 设备→产品缓存），既保证缓存故障时统一降级，也让单测无需静态打桩 `RedisUtils`（见 §5.1 备注）。
新增测试（3 个类，15 个用例）：`IotAlertCheckerTest`、`IotAlertRecordServiceTest`、`IotAlertRuleServiceTest`。
新增 SQL：`script/sql/update/2026-09-20-iot-alert.sql`；修改主脚本 `script/sql/ry-iot.sql`。

### 设计要点（供评审）

1. **幂等键选择 `active_flag`**：MySQL 唯一索引对 NULL 不去重，因此"未恢复=1、已恢复=NULL"可同时表达
   "同一设备+属性仅一条未恢复告警"与"历史告警任意多条"。比"唯一键含 status"（RESOLVED 会互相冲突）或
   "唯一键含 del_flag"（二次逻辑删除会冲突）都更自洽。规则表则用 0 代替 NULL 表达"不限范围"，让唯一键真正生效。
2. **阈值快照**：告警记录冗余 `compare_operator`/`threshold`，规则被改阈值或删除后仍能判定"是否已恢复正常"并自动 RESOLVED。
3. **触发/恢复共用同一判定函数** `IotAlertCondition.breached(...)`，避免"触发用 `>`、恢复用 `<`"的口径漂移。
4. **租户上下文**：消费线程无 Sa-Token 上下文，`TenantHelper.ignoreTable()` 在租户为空时返回 true（即**不过滤**），
   因此所有消费端读写都显式带 `tenant_id` 条件并包在 `TenantHelper.dynamic` 中。
5. **规则删除为物理删除**：规则是配置项，删除后已产生的告警记录因持有阈值快照而不受影响；这样唯一键无需包含 `del_flag`。
6. **Redis 访问收敛**：`IotAlertStateStore` 是告警链路唯一接触 Redis 的组件，所有方法自带 try/catch 降级
   （缓存不可用 → 计时/去抖视为未命中、设备产品缓存视为未命中，链路退化为"立即触发 + 不去抖"），
   避免缓存故障打断数据管道；同时使单测可直接 mock 该组件。

---

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| 前端页面（IoT 告警列表/规则管理） | **未做**（本批次明确排除） | 见 §7「前端需求」。后端端点与权限码已就绪，前端可直接对接。 |
| 告警端点/规则端点的集成测试（MockMvc / 真实 DB） | **未做** | 本批次按"单测覆盖核心断言"要求交付；权限注解、参数绑定、CAS SQL 的真实库行为建议由主代理在最终全量回归时验证。 |
| `duration_sec` 的精确性 | **部分** | 采用"首次越限写 Redis 计时键 + 每次越限刷新 TTL"的近似实现：判定发生在数据点到达时刻，因此实际告警时刻可能比 `duration_sec` 晚一个上报周期（对 1Hz 上报即 ≤1s 误差）。若需严格定时触发，应改为定时扫描 `iot_alert_record` 的 `ACTIVE` 记录。 |
| 多条件组合（如 `temp>80 AND vib>4.5`） | **未做** | 审计建议里提到"多条件组合"，本批次实现了单属性 + 比较符 + 持续时长 + 去抖；组合条件需要规则表达式引擎（可复用 `ruoyi-flow` 的 `FlowConditionEvaluator`），建议单独立项。 |
| 字典 `iot_alert_type` / `iot_alert_level` | **未做** | 审计建议补字典。当前类型/级别用 BO 上的 `@Pattern` 白名单校验，未落 `sys_dict`；前端若需下拉字典需另行补种子（属 ry-vue 独占文件，见 §3 合并清单）。 |
| 告警级别联动 MES 安灯的严重度 | **未做** | `IotAlert` DTO 未承载 `alertId`，安灯与 IoT 告警仍无关联字段（审计已指出"无法反查"）。建议后续在 `IMesAndonService.createAndonFromAlert` 契约中增加 `alertId` 回写。 |
| `IotAlertNotifier` 的在线用户全量扫描 | **未改** | 在线 Token 全量扫描在用户量大时较重（审计已注明），但改为订阅式推送超出本批次范围；去抖窗口已显著降低扫描频率。 |

---

## 3. 主脚本合并清单

`script/sql/ry-iot.sql` **已由本代理直接修改**（属本任务所有权，无需主代理合并）：
在文件末尾 `SET FOREIGN_KEY_CHECKS = 1;` 之前插入三张新表 DDL（`iot_alert_rule`、`iot_alert_record`、`iot_alert_notify_log`）
与 2 条兜底规则种子（`rule_id=7001/7002`，与 `application.yml` 的 `alert-temperature: 80` 等价）。

`script/sql/ry-vue.sql` 为**独占文件，本代理未编辑**。需主代理串行合并的内容（等价语句已在
`script/sql/update/2026-09-20-iot-alert.sql` 第 5 节给出，可重复执行）：

- **目标文件**：`script/sql/ry-vue.sql`
- **锚点**（附近已存在的整行文本，`ry-vue.sql:92726`）：
  ```sql
  INSERT INTO `sys_menu` VALUES (6026, '设备密钥重置', 6020, 6, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:resetSecret', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '重置设备密钥(轮换 device_secret, 仅返回一次)');
  ```
- **待插入的行**（紧跟锚点之后）：
  ```sql
  INSERT INTO `sys_menu` VALUES (6030, '告警记录', 6000, 3, 'alert', 'iot/alert/index', '', 1, 0, 'C', '0', '0', 'iot:alert:list', 'warning', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT告警记录(查询/确认/恢复)');
  INSERT INTO `sys_menu` VALUES (6031, '告警查询', 6030, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6032, '告警确认', 6030, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:ack', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ACTIVE -> ACKNOWLEDGED');
  INSERT INTO `sys_menu` VALUES (6033, '告警恢复', 6030, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:recover', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ACTIVE/ACKNOWLEDGED -> RESOLVED');
  INSERT INTO `sys_menu` VALUES (6034, '告警导出', 6030, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6040, '告警规则', 6000, 4, 'alertRule', 'iot/alertRule/index', '', 1, 0, 'C', '0', '0', 'iot:alertRule:list', 'set', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT告警规则(阈值/持续时长/去抖/级别)');
  INSERT INTO `sys_menu` VALUES (6041, '规则查询', 6040, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6042, '规则新增', 6040, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6043, '规则修改', 6040, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6044, '规则删除', 6040, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
  INSERT INTO `sys_menu` VALUES (6045, '规则启停', 6040, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:changeStatus', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '启用/停用规则(1/0)');
  ```
- **menu_id 占用校验记录**（从 `script/sql/ry-vue.sql` 提取的全部 6000-6999 段 ID）：
  `6000,6010,6011,6012,6013,6014,6015,6020,6021,6022,6023,6024,6025,6026,6100-6136,6140-6146,6161-6178,6181-6187,6190,6201`
  → `6030~6034`、`6040~6045` 均空闲（6026 由 IoT 安全增量脚本占用）。
- **前端页面组件路径**（合并菜单时的约定，需与前端实现一致）：
  `iot/alert/index`（告警记录）、`iot/alertRule/index`（告警规则）。若前端最终采用其他路径，请同步修改菜单行的 `component` 字段。

> 另需主代理注意：`application.yml` 的 `nexus.iot.pipeline.alert-thresholds` 保持不动（本代理未编辑该文件），
> 其语义已改为"库内无规则时的兜底默认"，代码注释已同步（`IotPipelineProperties`）。生产环境如需完全依赖库内规则，可保留该 YAML 作为冷启动兜底。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 变更 | 落库 |
|---|---|---|
| 09-20 | IoT 告警落库/状态机/规则（IOT-ALERT-01/02）：`iot_alert_rule`（规则：范围/比较符/阈值/持续时长/去抖/级别/启用）、`iot_alert_record`（告警记录：状态机 ACTIVE/ACKNOWLEDGED/RESOLVED + `active_flag` 幂等唯一键 + 触发/确认/恢复字段）、`iot_alert_notify_log`（投递留痕）3 表；`sys_menu` 告警与规则权限码 6030~6034、6040~6045 | ry-iot + ry-vue |

增量脚本：`script/sql/update/2026-09-20-iot-alert.sql`（幂等：`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`；可重复执行）。
主脚本 `script/sql/ry-iot.sql` 已同步（新环境按主脚本导入即可，无需执行增量脚本）。
**建议清理 Redis**：`DEL iot:alert:*`（旧版限频键 `iot:alert:rl:*` 已废弃；新键 `iot:alert:first:*` 为持续时长计时、`iot:alert:debounce:*` 为去抖窗口、`iot:alert:devprod:*` 为设备→产品缓存，均可自然过期）。

---

## 5. 验证记录

### 5.1 编译 + 单测

```powershell
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  -Dtest=IotAlertCheckerTest,IotAlertRecordServiceTest,IotAlertRuleServiceTest `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

结果：**BUILD SUCCESS，19/19 通过（Failures 0 / Errors 0 / Skipped 0）**

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.nexus.iot.alert.service.IotAlertRecordServiceTest
[INFO] Tests run:  3, Failures: 0, Errors: 0, Skipped: 0 -- in com.nexus.iot.alert.service.IotAlertRuleServiceTest
[INFO] Tests run:  6, Failures: 0, Errors: 0, Skipped: 0 -- in com.nexus.iot.pipeline.IotAlertCheckerTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> 过程中修正的两个测试期问题（供后续代理避坑）：
> ① `RedisUtils` 的静态初始化依赖 Spring 容器，**无法用 `mockStatic` 打桩**（报 `Cannot instrument class ... because it or one of its supertypes could not be initialized`）；
> 故把告警链路的 Redis 访问收敛为可注入组件 `IotAlertStateStore`（同时获得"缓存故障时降级为立即触发+不去抖"的一致性）。
> ② `LambdaUpdateWrapper/LambdaQueryWrapper` 在单测中需先 `TableInfoHelper.initTableInfo(...)` 注册列缓存，否则 `LambdaUtils.extract` 抛 `can not find lambda cache`。
> ③ MP 的 `BaseMapper.insert` 有 `insert(T)` / `insert(Collection<T>)` 两个重载，单测里必须写 `any(IotAlertRecord.class)` 而非裸 `any()`。

### 5.2 新增测试类与用例数

| 测试类 | 用例数 | 覆盖的必测项 |
|---|---|---|
| `IotAlertCheckerTest` | 6 | 规则命中触发落库；持续时长未满足不触发；持续满足触发；YAML 兜底（大小写不敏感）；未越限调用自动恢复；比较符语义 |
| `IotAlertRecordServiceTest` | 10 | 触发落库（无在线用户仍落库 + SKIPPED 留痕）；幂等合并（已有未恢复不新增）；并发唯一键冲突退化合并；确认 CAS 成功 / 0 行冲突抛异常 / RESOLVED 非法流转被拒；恢复 CAS + 清 active_flag / RESOLVED 非法流转被拒；自动恢复；仍越限不恢复 |
| `IotAlertRuleServiceTest` | 3 | 设备>产品>全局 优先级；范围不匹配过滤；空属性编码返回空 |
| **合计** | **19** | 审计要求的 5 项必测全部覆盖 |

### 5.3 测试分组

三个测试类均标注 `@Tag("local")` + `@Tag("dev")`；`ruoyi-iot/pom.xml` 已覆盖根 pom 的 `<groups>` 过滤（`iot.groups` 为空 = 全部执行），故 `local`/`dev` profile 下都会真实执行。

### 5.4 SQL 校验（人工/脚本核对）

```sql
-- 表结构（ry-iot）
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, ROW_FORMAT FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ry-iot'
   AND TABLE_NAME IN ('iot_alert_rule','iot_alert_record','iot_alert_notify_log');
-- 期望：InnoDB / utf8mb4_unicode_ci / Dynamic

-- 唯一键
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='ry-iot' AND TABLE_NAME IN ('iot_alert_rule','iot_alert_record') AND NON_UNIQUE=0
 GROUP BY TABLE_NAME, INDEX_NAME;
-- 期望：uk_rule_scope(tenant_id,product_id,device_id,property_code)
--       uk_active_alert(tenant_id,device_id,property_code,active_flag)

-- 权限码
SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu` WHERE perms LIKE 'iot:alert%' ORDER BY menu_id;
```

字符集核对：三张新表均显式声明 `CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci`、`ENGINE=InnoDB`、`ROW_FORMAT=Dynamic`，
`tenant_id varchar(20) NOT NULL DEFAULT '000000'`，符合 `doc/system/specs/charset-encoding-standard.md` §4/§5。

---

## 6. 风险与回滚说明

| 风险 | 说明 | 回退方式 |
|---|---|---|
| 主脚本 `ry-iot.sql` 已改 | 新环境导入会多出 3 张表与 2 条规则种子，不影响既有 3 张表 | `DROP TABLE iot_alert_rule, iot_alert_record, iot_alert_notify_log;` + 删除主脚本对应段落（本次改动集中在文件末尾，diff 干净） |
| 规则表非空即覆盖 YAML | 种子规则 7001/7002 存在时，`temperature`/`vibration` 走库内规则（阈值与 YAML 相同，行为等价）；若运维期望完全由 YAML 控制，需删除这两条种子 | `DELETE FROM iot_alert_rule WHERE rule_id IN (7001,7002);` |
| `IotAlertNotifier.notify` 返回值由 void 变为 `List<ChannelOutcome>` | 源码兼容（既有调用方忽略返回值即可编译）；已核对唯一调用点 `IotAnomalyDetector` 已改为走记录服务 | 回退 `IotAlertNotifier.java` 至改动前版本即可 |
| 去抖窗口内不再重复推送 | 规则 `debounce_sec` 默认 60s（与旧限频一致），窗口内只合并落库、不推送；若期望每次都推，把 `debounce_sec` 配 0 | 修改规则 `debounce_sec=0` |
| 告警写入量 | 每个越限数据点都会 merge 更新一次告警记录（`last_trigger_time`/`trigger_value`），高频设备会产生持续 UPDATE。已用"仅未恢复告警合并 + 去抖控制推送"限制规模；若某设备上报频率极高（>10Hz）建议提高 `duration_sec` | 提高 `duration_sec` / 在 `IotStreamConsumer` 侧降采样 |
| Redis 依赖 | 持续时长/去抖/设备→产品缓存均依赖 Redis；Redis 不可用时 `RedisUtils` 抛异常，已被 `check()` 的 try/catch 隔离（记录 warn），**不影响数据管道主流程**，但会退化为"立即触发 + 不去抖" | 无需回退；如需完全去 Redis 化可后续把计时状态落到告警记录字段 |
| 状态机 CAS | 确认/恢复全部走条件更新，0 行即抛异常；前端需处理该业务错误提示 | 无需回退 |

**回滚步骤**：`git checkout -- ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/pipeline/`（恢复 3 个改动文件）+
删除 `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/alert/`、`src/test/java/com/nexus/iot/alert/`、
`src/test/java/com/nexus/iot/pipeline/IotAlertCheckerTest.java` + 还原 `script/sql/ry-iot.sql` +
删除 `script/sql/update/2026-09-20-iot-alert.sql` + 清理 `sys_menu` 6030~6034/6040~6045 与三张新表。
（注意：`IotStreamConsumer` 未改动，回退后行为与改动前完全一致。）

---

## 7. 前端需求（本批次不做，交前端代理）

### 7.1 新增 API（`plus-ui/src/api/iot/alert/`）

```ts
// index.ts —— 告警记录
listAlert(query)            GET  /iot/alert/list
getAlert(alertId)           GET  /iot/alert/{alertId}
ackAlert({alertId, ackRemark})       POST /iot/alert/ack
recoverAlert({alertId, resolveValue, resolveRemark})  POST /iot/alert/recover
exportAlert(query)          POST /iot/alert/export
notifyLog(alertId)          GET  /iot/alert/notifyLog/{alertId}

// index.ts —— 告警规则
listAlertRule(query)        GET  /iot/alertRule/list
optionAlertRule(query)      GET  /iot/alertRule/optionList
getAlertRule(ruleId)        GET  /iot/alertRule/{ruleId}
addAlertRule(data)          POST /iot/alertRule
updateAlertRule(data)       PUT  /iot/alertRule
changeAlertRuleEnabled(ruleId, enabled)  PUT /iot/alertRule/changeEnabled?ruleId=&enabled=
delAlertRule(ids)           DELETE /iot/alertRule/{ids}
```

### 7.2 页面

| 页面 | 路由/组件 | 要点 |
|---|---|---|
| IoT 告警记录 | `iot/alert/index`（menu 6030） | 列表列：设备编码、属性、类型、级别、触发值、阈值、状态（ACTIVE/ACKNOWLEDGED/RESOLVED 标签色）、触发时间、最近触发时间、确认人/时间、恢复值/时间；筛选：设备、属性、状态、级别、来源、触发时间区间；行操作：确认（弹备注，`iot:alert:ack`）、恢复（弹恢复值+备注，`iot:alert:recover`）、投递留痕抽屉（`iot:alert:query`）、导出（`iot:alert:export`）；默认按 `status=ACTIVE` + 触发时间倒序 |
| IoT 告警规则 | `iot/alertRule/index`（menu 6040） | 列表列：规则名称、产品范围、设备范围、属性编码、比较符、阈值、持续时长、去抖窗口、类型、级别、通知渠道、启用开关；表单校验与 BO 一致（比较符/级别/类型/启用为下拉白名单，`durationSec`/`debounceSec` 0~86400）；操作：新增/修改/删除/启停 |
| 设备实时页（既有） | `iot/device/index` | 告警 SSE payload 已有 `type=IOT_ALERT`，建议在设备页对 ACTIVE 告警做角标/提示，并提供"查看告警"跳转到告警记录页（带 deviceId 过滤） |

### 7.3 类型定义

`plus-ui/src/api/iot/alert/types.ts`：`AlertRecordVO`、`AlertRecordQuery`、`AlertRuleVO`、`AlertRuleForm`、`AlertNotifyLogVO`。
**注意**：所有 ID（`alertId`/`ruleId`/`deviceId`/`productId`）为雪花 ID，后端已全局序列化为字符串，前端一律按 `string` 处理，勿用 `number`。

### 7.4 字典建议（可选）

若需要下拉字典，需在 `ry-vue.sql` 增加 `sys_dict_type`/`sys_dict_data`：`iot_alert_status`（ACTIVE/ACKNOWLEDGED/RESOLVED）、
`iot_alert_level`（HIGH/MEDIUM/LOW）、`iot_alert_type`（TEMPERATURE/VIBRATION/PRESSURE/OTHER）、`iot_alert_operator`（GT/GE/LT/LE/EQ/NE）。
本代理未落字典（属 ry-vue 独占文件），前端可先硬编码常量。

---

## 8. 未完成项（汇总）

1. 前端页面与 API 层（§7）—— 明确不在本批次。
2. 告警/规则端点的 MockMvc 或真实库集成测试（权限码、CAS SQL 实际行为）。
3. 多条件组合规则（需表达式引擎）。
4. `sys_dict` 字典种子（属 ry-vue 独占文件）。
5. MES 安灯与 IoT 告警的关联字段（需改 `IMesAndonService` 契约，跨模块）。
6. `IotAlertNotifier` 在线用户全量扫描改为订阅式推送。
7. `duration_sec` 精确到秒级定时触发（当前为数据点驱动，存在 ≤1 个上报周期的误差）。
