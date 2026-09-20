# 定时任务生产环境永不执行 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-iot.md` 的 IOT-TASK-01 / IOT-TASK-02 / IOT-MES-01；
> `doc/audit/2026-09-20-audit-mes-eq.md` 的 MES-EQ-01 / MES-EQ-02 | 分支: `feature/erp-mes-iot-audit-fix`

**用户已确认方向（未偏离）**：加 `@Scheduled` 本地兜底（仅当 `snail-job.enabled=false` 时生效）
+ Lock4j 分布式锁防多实例重复；**不修改** `application-prod.yml` 的 `snail-job.enabled`；
**不给** `@JobExecutor` Bean 加 `@ConditionalOnProperty`（SnailJob 启用时仍需注册执行器）。

---

## 0. 根因（两层，缺一不可）

| 层 | 根因 | 证据 |
|---|---|---|
| 第一层 | 生产 `snail-job.enabled: false` → SnailJob 客户端不启动，`@JobExecutor` 声明的任务**没有任何调度器回调** | `ruoyi-admin/src/main/resources/application-prod.yml:24-26` |
| **第二层（本次新发现，决定性）** | 全仓唯一的 `@EnableScheduling` 挂在 `SnailJobConfig` 上，而该类标了 `@ConditionalOnProperty(prefix="snail-job", name="enabled", havingValue="true")` → **生产环境 `ScheduledAnnotationBeanPostProcessor` 根本没注册**，任何 `@Scheduled` 兜底都会静默失效（无日志、无异常） | `ruoyi-common/ruoyi-common-job/.../SnailJobConfig.java:21-24`；`IotConfiguration.java:22` 是唯一另一处（仅覆盖 IoT 包） |

> 第二层不修，只加 `@Scheduled` 的兜底是**零效果**的假修复。`SoContractExpiryTask` 原注释
> “用 `@Scheduled` 写的任务在生产不会执行”正是该现象的记录，本次已改正该注释并补齐机制。

---

## 1. 第一步交付：全仓 `@JobExecutor` / `@Scheduled` 盘点

全仓 `@JobExecutor` 共 **23 处**（含 6 个 `ruoyi-job` 演示执行器）；`@Scheduled` 共 **4 处**（IoT 实时推送、人脸补跑、AI 清理、IM 清理）。

| # | 模块 | 任务类 | executor 名 | 原有本地兜底 | 本次处理 | 采用的 cron 与依据 |
|---|---|---|---|---|---|---|
| 1 | ruoyi-iot | `IotDataCleanupTask` | iotDataCleanupTask | **无（生产必不执行）** | 补兜底 | `0 0 2 * * ?`（`sj_job` biz_4，与代码注释一致） |
| 2 | ruoyi-iot | `IotDeviceHeartbeatTask` | iotDeviceHeartbeatTask | **无（生产必不执行）** | 补兜底 | `0 */5 * * * ?`（IOT-TASK-02 修正值；与 `TIMEOUT_MINUTES=15` 对齐） |
| 3 | ruoyi-iot | `IotStreamTrimTask` | iotStreamTrimTask | **无（生产必不执行）** | 补兜底 | `0 0 * * * ?`（IOT-TASK-02 修正值） |
| 4 | ruoyi-mes | `IotDataArchiveTask` | iotDataArchiveTask | **无（生产必不执行）** | 补兜底 | `0 */5 * * * ?`（`sj_job` biz_7） |
| 5 | ruoyi-mes | `MaintenanceDueTask` | maintenanceDueTask | **无（生产必不执行）** | 补兜底 | `0 0 8 * * ?`（`sj_job` biz_8） |
| 6 | ruoyi-mes | `EquipmentStatusSyncTask` | equipmentStatusSyncTask | **无（生产必不执行）** | 补兜底 | `7 */1 * * * ?`（`sj_job` biz_9；类注释写 `7 * * * * ?`，语义等价，取种子值） |
| 7 | ruoyi-mes | `EquipmentHealthTask` | equipmentHealthTask | **无（生产必不执行）** | 补兜底 | `0 0 7 * * ?`（**种子缺行**，取 MES-EQ-02 建议值） |
| 8 | ruoyi-mes | `EqOeeAggregateTask` | oeeAggregateTask | **无（生产必不执行）** | 补兜底 | `0 0 1 * * ?`（**种子缺行**，取 MES-EQ-02 建议值） |
| 9 | ruoyi-erp | `StockAlertTask` | stockAlertTask | **无（生产必不执行）** | 补兜底 | `0 0 1 * * ?`（`sj_job` biz_3） |
| 10 | ruoyi-erp | `VendorEvaluationTask` | vendorEvaluationTask | **无（生产必不执行）** | 补兜底 | `0 0 1 * * ?`（`sj_job` biz_2） |
| 11 | ruoyi-erp | `SoContractExpiryTask` | soContractExpiryTask | **无（生产必不执行）** | 补兜底 + 改正误导注释 | `0 30 1 * * ?`（`sj_job` biz_200） |
| 12 | ruoyi-common-mq | `CompensationTask` | mqCompensationTask | **无（生产必不执行）** | 补兜底 | `0 */1 * * * ?`（`sj_job` id=102，`trigger_type=2` 固定 60s 等价） |
| 13 | ruoyi-common-mq | `MessageCompensationTask` | mqMessageCompensationTask | **无（生产必不执行）** | 补兜底 | `0 */1 * * * ?`（`sj_job` id=100，固定 60s 等价） |
| 14 | ruoyi-common-mq | `ReconciliationTask` | mqReconciliationTask | **无（生产必不执行）** | 补兜底 | `0 */1 * * * ?`（`sj_job` id=101，固定 60s 等价） |
| 15 | ruoyi-erp | `FaceVerifyBackfillTask` | —（本就 `@Scheduled`） | 有注解但**生产不生效** | 无需改代码 | `0 17 * * * ?`；本次开启 `@EnableScheduling` 后真正生效 |
| 16 | ruoyi-im | `ImMessageClearTask` | —（本就 `@Scheduled`） | 有注解但**生产不生效** | 无需改代码 | `0 0 * * * ?`；同上 |
| 17 | ruoyi-ai | `AiThreadPoolConfig#cleanupOutputChannelRegistry` | —（本就 `@Scheduled`） | 有注解但**生产不生效** | 无需改代码 | `fixedRate=600s`；同上 |
| 18 | ruoyi-iot | `IotRealtimeSseManager` | —（本就 `@Scheduled`） | 已生效 | 无需改动 | `IotConfiguration` 的无条件 `@EnableScheduling` 覆盖 |
| 19 | ruoyi-job | `WechatBillTask`/`AlipayBillTask`/`SummaryBillTask` | wechatBillTask 等 | 无 | **不加兜底**（见 §2.1） | 无权威 cron |
| 20 | ruoyi-job | `TestAnnoJobExecutor`/`TestBroadcastJob`/`TestMapJobAnnotation`/`TestMapReduceAnnotation1`/`TestStaticShardingJob` | test* | 无 | **不加兜底**（见 §2.2） | 演示执行器 |
| 21 | ruoyi-flow | `FlowScheduleJob` | aiFlowJob | 无 | **不加兜底**（见 §2.3） | 动态调度，需独立方案 |

**结论：全仓“无任何本地兜底 → 生产必不执行”的任务共 14 个，本次全部补齐**（#1~#14）；
另有 3 个既有 `@Scheduled` 任务（#15~#17）因第二层根因在生产静默失效，本次一并修复生效。

**附带发现（不在本次范围）**：`sj_job` 种子中 id 10~28 共 19 条上游商城任务
（`bargainStopChangeTask`/`couponOverdueTask`/`statisticsTask` 等）**在全仓没有任何 `@JobExecutor` 实现**，
会持续产生与 IOT-MES-01 同型的 `Executor configuration is incorrect` / `No executable client` 日志。
建议主代理另开任务决定“删行 or 补实现”。

---

## 2. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| IOT-TASK-01 | 3 个 IoT 任务补 `@Scheduled` 兜底（开关判断 + Lock4j） | `IotDataCleanupTask.java:26-46`、`IotDeviceHeartbeatTask.java:29-49`、`IotStreamTrimTask.java:29-49` | 否 | `IotSchedulerFallbackTest`（5 例） |
| IOT-TASK-02 | 修正 `sj_job` 心跳/流裁剪 cron；兜底 cron 同步取修正值 | 增量脚本 §3/§4；`IotDeviceHeartbeatTask.java:43`、`IotStreamTrimTask.java:43` | 否（DML） | `IotSchedulerFallbackTest#iotTasksExposeExpectedFallbackCron` |
| IOT-MES-01 | `IotDataArchiveTask` 补兜底（生产归档链路断开的执行机制根因） | `IotDataArchiveTask.java:44-64` | 否 | `EqSchedulerFallbackTest#mesTasksExposeExpectedFallbackCron` |
| MES-EQ-01 | 4 个 eq 任务补 `@Scheduled` 兜底 | `MaintenanceDueTask.java:56-76`、`EquipmentStatusSyncTask.java:46-66`、`EquipmentHealthTask.java:36-56`、`EqOeeAggregateTask.java:30-50` | 否 | `EqSchedulerFallbackTest`（4 例） |
| MES-EQ-02 | 补 `oeeAggregateTask`(`0 0 1 * * ?`) / `equipmentHealthTask`(`0 0 7 * * ?`) 的 `sj_job` 任务定义 | 增量脚本 §1/§2 | 否（DML） | `EqSchedulerFallbackTest#mesTasksExposeExpectedFallbackCron` |
| **第二层根因（本次新发现）** | 新增 `LocalSchedulingConfig`：`snail-job.enabled=false` 时注册 `@EnableScheduling`，否则 `@Scheduled` 兜底全部静默失效 | 新增 `LocalSchedulingConfig.java`；`AutoConfiguration.imports:1` | 否 | 由上述测试 + 编译验证 |
| 同型扩展（ERP/MQ） | `StockAlertTask`/`VendorEvaluationTask`/`SoContractExpiryTask`/3 个 MQ 补偿任务同型补齐 | 见 §1 表 #9~#11、#12~#14 | 否 | 编译 + 共用工具测试（`IotSchedulerFallbackTest`） |
| **阻塞项（本次新发现，已修）** | `ruoyi-iot` 未像 `ruoyi-mes`/`ruoyi-ai`/`ruoyi-im`/`ruoyi-flow`/`ruoyi-flowable` 那样覆盖根 pom 的 `<groups>${profiles.active}</groups>` surefire 过滤 → 本模块**无 `@Tag` 的单测从不执行**（既有 `IotMqttTopicParserTest`、`IotDataValueResolverTest` 同样是死测试），本次新增的 IoT 兜底测试也跑不起来 | `ruoyi-iot/pom.xml`（新增 `<properties>` + `<build>` surefire 覆盖） | 否 | `IotSchedulerFallbackTest` 5 例真正被执行（修复前 `Tests run: 0`） |
| 注释同步（规范 §2.9） | 改正 `SoContractExpiryTask` 中与行为不符的“为什么不用 `@Scheduled`”段落 | `SoContractExpiryTask.java:25-31` | 否 | — |

### 2.1 兜底实现约定（14 处统一形态）

```java
@Value("${snail-job.enabled:false}")          // 注入开关
private boolean snailJobEnabled;

@Scheduled(cron = "<与 sj_job 种子一致>")       // 薄壳入口
public void scheduledExecute() {
    SchedulerFallbackUtils.runIfFallbackEnabled(
        snailJobEnabled, "<executor 名>", <锁过期 ms>, () -> jobExecute(null));
}
```

- **执行体语义零改动**：只新增入口，`jobExecute(...)` 内部一行未动。
- **不加 `@ConditionalOnProperty`**：`@JobExecutor` Bean 在 SnailJob 启用时仍需注册为执行器。
- **共用工具**：`ruoyi-common-redis/.../utils/SchedulerFallbackUtils.java`
  （`localFallbackEnabled(boolean)` 纯函数 + `runIfFallbackEnabled(...)` 串起“开关 → Lock4j → 执行体”）。
- **锁的取法用 `LockTemplate` 而非 `@Lock4j` 注解**（唯一偏离字面要求处，理由充分）：
  ①注解由 AOP 织入，加锁发生在方法体之前，无法满足“先判开关、再取锁”的既定顺序；
  ②lock4j 默认 `LockFailureStrategy` 抢锁失败抛 `LockException`，对每分钟级任务（设备状态联动、MQ 补偿）
  在多实例下会造成 ERROR 日志噪声；`LockTemplate.lock` 抢锁失败返回 `null`，可静默跳过。
  二者同为 Lock4j（`com.baomidou.lock`，Redisson 实现），分布式语义一致。
- **锁 key**：`scheduler:fallback:<executor 名>`（与 `sj_job.executor_info` 同名，便于排障）；
  抢锁等待 1ms（≈非阻塞）；锁过期 60s~30min，按任务周期与耗时设置。
- **幂等说明**：`EquipmentStatusSyncTask`（CAS `WHERE status IN(NORMAL,OFFLINE)`）、
  `SoContractExpiryTask`（EFFECTIVE→EXPIRED 条件更新）、`MaintenanceDueTask`（每计划 24h 限频）、
  `IotDataArchiveTask`（`uk_point_collect_time` 去重）、MQ 两任务（`retry_count` CAS 认领）
  本身已幂等，但仍按用户要求统一加锁，形成双保险。

---

## 3. 未修复 / 部分修复项（含理由与建议）

1. **`WechatBillTask` / `AlipayBillTask` / `SummaryBillTask`（ruoyi-job）**：`jobExecute` 直接读
   `jobArgs.getWfContext()`（SnailJob DAG 工作流上下文），`@Scheduled` 传 `null` 会 NPE；
   且 `sj_job` 中无这三条任务定义，**没有权威 cron 可依据**。→ 不补，建议主代理确认是否属废弃演示代码。
2. **`TestAnnoJobExecutor` 等 5 个 `Test*` 执行器（ruoyi-job）**：演示/自测执行器，无生产语义。→ 不补。
3. **`FlowScheduleJob`（ruoyi-flow，executor=aiFlowJob）**：它是“按 `ai_flow` 表中每个已发布流程的
   cron 逐个动态调度”的分发器，不是固定周期任务；兜底需按流程表枚举并逐流程注册调度（或改由
   `SchedulingConfigurer` 动态注册），属独立设计。→ 本次不补，建议单开任务（并注意 `FlowEngine` 的幂等）。
4. **IOT-MES-01 的 ③④ 建议未做**：归档水位持久化 + 采集点页面“最近归档时间”字段。
   执行机制根因已修（本次），但“失败可自证/不静默”仍缺失。→ 建议单开任务。
5. **未修改 `application-prod.yml`**（用户决策）；**未给 `@JobExecutor` Bean 加禁用注解**（用户决策）。
6. **未新增 `TaskScheduler` Bean**：`ScheduledAnnotationBeanPostProcessor` 在容器存在多个 `TaskScheduler`
   且无 `@Primary`、也无名为 `taskScheduler` 的 Bean 时会抛 `NoUniqueBeanDefinitionException` **导致启动失败**，
   故兜底任务复用容器中唯一的 `iotTaskScheduler`（`IotConfiguration`，`poolSize=2`）。
   → **建议主代理把 `IotConfiguration.iotTaskScheduler` 的 `poolSize` 提到 4~8**（该文件不在本次所有权内）。

---

## 4. 主脚本合并清单（`script/sql/ry-vue.sql`，本代理未直接编辑）

### 4.1 新增两条 `sj_job` 任务定义（MES-EQ-02）

- **目标文件**：`script/sql/ry-vue.sql`（`Records of sj_job` 段）
- **锚点（附近已存在的整行，第 10054 行）**：

```
INSERT INTO `sj_job` VALUES (200, 'dev', 'biz_200', 'ruoyi_group', '销售合同到期扫描任务', '', 1, 1789405200000, 1, 1, 4, 1, 'soContractExpiryTask', 3, '0 30 1 * * ?', 1, 60, 3, 1, 1, 90, 0, '', 1, '{}', '', '', 0, '2026-09-19 20:00:00', '2026-09-19 20:00:00');
```

- **在该行之后插入**（与增量脚本 §1/§2 等价，仅去掉 `IGNORE`）：

```sql
INSERT INTO `sj_job`
(`namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`)
VALUES
('dev', 'biz_201', 'ruoyi_group', '设备OEE日聚合任务', NULL, 1, UNIX_TIMESTAMP(NOW()) * 1000,
 1, 1, 4, 1, 'oeeAggregateTask', 3, '0 0 1 * * ?',
 1, 300, 3, 1, 60, 116,
 0, '', 1, '{}',
 'MES-EQ-02 补种: 每日01:00聚合前一天设备OEE写入 eq_oee_record(租户+设备+stat_date 唯一, 幂等可重跑)', '', 0);

INSERT INTO `sj_job`
(`namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`)
VALUES
('dev', 'biz_202', 'ruoyi_group', '设备健康度日评任务', NULL, 1, UNIX_TIMESTAMP(NOW()) * 1000,
 1, 1, 4, 1, 'equipmentHealthTask', 3, '0 0 7 * * ?',
 1, 300, 3, 1, 60, 116,
 0, '', 1, '{}',
 'MES-EQ-02 补种: 每日07:00评估全部关联IoT设备健康度, LOW级触发EQUIPMENT安灯', '', 0);
```

### 4.2 修正两条已有 `sj_job` 行的 cron（IOT-TASK-02）

- **目标文件**：`script/sql/ry-vue.sql`
- **锚点（第 10056 行）**：

```
INSERT INTO `sj_job` VALUES (5, 'dev', 'biz_5', 'ruoyi_group', 'IoT设备心跳检测任务', '', 1, 1781715600000, 1, 1, 4, 1, 'iotDeviceHeartbeatTask', 3, '0 0 2 * * ?', 1, 60, 3, 1, 1, 51, 0, '', 1, '{}', '', '', 1, '2026-06-18 17:32:23', '2026-09-14 13:18:12');
```
  → 行内把 `'iotDeviceHeartbeatTask', 3, '0 0 2 * * ?'` 改为 `'iotDeviceHeartbeatTask', 3, '0 */5 * * * ?'`（不新增行）。

- **锚点（第 10057 行）**：

```
INSERT INTO `sj_job` VALUES (6, 'dev', 'biz_6', 'ruoyi_group', 'IoT Stream修剪任务', '', 1, 1781715600000, 1, 1, 4, 1, 'iotStreamTrimTask', 3, '0 0 2 * * ?', 1, 60, 3, 1, 1, 52, 0, '', 1, '{}', '', '', 1, '2026-06-18 17:32:23', '2026-09-14 13:18:12');
```
  → 行内把 `'iotStreamTrimTask', 3, '0 0 2 * * ?'` 改为 `'iotStreamTrimTask', 3, '0 0 * * * ?'`（不新增行）。

> `IoT设备数据清理任务`（biz_4）的 `0 0 2 * * ?` **保持不变**——与设计一致（每天 02:00）。

---

## 5. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

- **DDL：无**（未新增/修改表、列、索引、约束）。
- **DML（库 `ry-vue`，表 `sj_job`）**：
  1. 新增 2 行任务定义：`biz_201`(executor=`oeeAggregateTask`, cron `0 0 1 * * ?`)、
     `biz_202`(executor=`equipmentHealthTask`, cron `0 0 7 * * ?`)；`job_status=1`、`trigger_type=3`、
     `group_name='ruoyi_group'`、`namespace_id='dev'`。
  2. 修正 2 行 cron：`iotDeviceHeartbeatTask` `0 0 2 * * ?` → `0 */5 * * * ?`；
     `iotStreamTrimTask` `0 0 2 * * ?` → `0 0 * * * ?`。
- 增量脚本：`script/sql/update/2026-09-20-scheduler-fallback.sql`（幂等，可重复执行）。
- 执行后建议重启 `ruoyi-snailjob-server` 或控制台刷新任务列表（服务端有任务本地缓存）。

---

## 6. 验证记录

### 6.1 编译（成功）

```
mvn -pl ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-erp -am -q compile -DskipTests
→ exit code 0（无输出即无错误/警告）
```

### 6.2 单测

```
mvn -pl ruoyi-common/ruoyi-common-mq,ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-mes -am test \
    -DskipTests=false "-Dtest=IotSchedulerFallbackTest,EqSchedulerFallbackTest" \
    "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"
→ （结果见下方“实测结果”）
```

> 注 1：`-DfailIfNoTests=false` 对 surefire 3.5.3 无效，必须同时加
> `-Dsurefire.failIfNoSpecifiedTests=false`，否则在无匹配测试的依赖模块（如 `ruoyi-common-core`）
> 上会以 “No tests matching pattern” 直接 FAILURE。**该命令坑建议同步进修复执行规范 §3.4。**

**实测结果**：

```
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false -Dtest=IotSchedulerFallbackTest \
    -Dsurefire.failIfNoSpecifiedTests=false
→ Running com.nexus.iot.task.IotSchedulerFallbackTest
  Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.288 s
  BUILD SUCCESS

mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false -Dtest=EqSchedulerFallbackTest \
    -Dsurefire.failIfNoSpecifiedTests=false
→ Running com.nexus.mes.eq.task.EqSchedulerFallbackTest
  Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.362 s
  BUILD SUCCESS
```

**9 个用例全部通过（5 + 4）**，覆盖“修复前会失败、修复后会通过”的核心断言：
`snail-job.enabled=false` 时执行体被调用（`deleteBefore` / `aggregate` 各 1 次）且锁被释放；
`=true` 时执行体零调用、且完全不取锁；抢锁失败（`lock` 返回 `null`）时执行体零调用。

> 注 2（本次踩坑，建议写入规范 §3.3）：
> ①测试里 `mockStatic(SpringUtils.class)` 桩 `SpringUtils.getBean(...)` **不生效** —— `getBean` 是
> `SpringUtils extends cn.hutool.extra.spring.SpringUtil` **继承**来的静态方法，必须 `mockStatic(SpringUtil.class)`；
> ②Mockito 的 `any(Date.class)` **不匹配 `null`**，而 `EqOeeAggregateTask` 以 `date=null` 表示“聚合昨天”，
> 断言须用 `isNull()`；③Javadoc 里写 cron 字面量 `0 */5 * * * ?` 会因 `*/` **提前闭合注释**导致编译失败。

> 注 3（并发影响记录）：首次 `test` 运行时 `ruoyi-mes` 编译失败于
> `QcOqcController.java:102,117` / `QcOqcServiceImpl.java:204,243`（`IQcOqcService` 缺
> `selectDetails`/`inspectWithDetails`）——**属其他修复代理在同一模块的在途改动**，非本次改动；
> 重试一次后该模块已恢复正常（本次全部改动在此之前已通过一次完整 `compile`，见 §6.1）。

### 6.3 新增测试类与用例

| 测试类 | 模块 | 用例数 | 覆盖点 |
|---|---|---|---|
| `IotSchedulerFallbackTest` | `ruoyi-modules/ruoyi-iot/src/test/java/com/nexus/iot/task/` | 5 | 开关为 false 时执行体被调用且释放锁；为 true 时不执行且不取锁；抢锁失败静默跳过；3 个 IoT 任务 cron 与种子一致；`localFallbackEnabled` 取反语义 |
| `EqSchedulerFallbackTest` | `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/eq/task/` | 4 | 同上（以 `EqOeeAggregateTask` 为执行体样本，断言 `aggregate` 调用 0/1 次、锁释放 0/1 次）；5 个 MES 任务 cron 与种子一致（含 MES-EQ-02 两条补种值） |

---

## 7. 风险与回滚说明

### 7.1 风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| **R1 既有 `@Scheduled` 任务首次在生产开始执行** | `LocalSchedulingConfig` 开启 `@EnableScheduling` 后，`ImMessageClearTask`（每小时删 180 天前 IM 消息/通知）、`FaceVerifyBackfillTask`（每小时人脸补跑）、`AiThreadPoolConfig`（每 10 分钟清 OutputChannel）在生产**首次真正运行**。这是预期修复，但首次执行可能是批量删除/补跑 | 上线后观察首轮日志；`ImMessageClearTask` 已有 `BATCH_SIZE=1000` + `MAX_BATCH_LOOP=1000` 上限保护 |
| **R2 调度线程争用** | 全部兜底任务 + IoT 3s 实时推送共用唯一 `iotTaskScheduler`（`poolSize=2`）。`iotDataCleanupTask` 删 90 天数据可能耗时数分钟，期间会阻塞心跳/状态联动 | 建议把 `IotConfiguration.iotTaskScheduler.poolSize` 提到 4~8（**不能**新增第二个 `TaskScheduler` Bean：无 `@Primary`/非 `taskScheduler` 命名时会抛 `NoUniqueBeanDefinitionException` 导致启动失败） |
| **R3 与 SnailJob 双跑** | 若运维把 `snail-job.enabled` 改 `true` | `snailJobEnabled` 开关使兜底自动让位 + Lock4j 锁双保险，无需改代码 |
| **R4 锁过期早于任务结束** | 若单次执行超过 `lockExpireMs`（60s~30min），另一实例可能并发进入 | 各任务执行体本身幂等（时间窗删除 / 条件更新 / 唯一键去重 / `retry_count` CAS 认领），不会产生重复数据 |
| **R5 `bucket_index=116`** | 新增 `sj_job` 行沿用种子中最常见的分桶值（19 条上游任务同为 116），仅影响集群扫描负载分摊 | 若 SnailJob 服务端要求精确分桶，在控制台对该任务点一次“保存”，服务端会重算 |
| **R6 命名空间** | 新增行 `namespace_id='dev'`（与既有 `sj_job` 行一致）；若生产客户端用 `prod` 命名空间则任务不在同一命名空间 | 增量脚本 §7 已写明如何改 `prod`；第 3/4 节 UPDATE 未按命名空间过滤，两个命名空间都会修正 |

### 7.2 回滚

1. **仅想停用兜底、不动代码**：把 `snail-job.enabled` 置 `true`（需同时部署 SnailJob 服务端）——
   兜底自动让位；或从 `ruoyi-common-job/.../AutoConfiguration.imports` 移除
   `com.nexus.common.job.config.LocalSchedulingConfig` 一行 → 立即恢复“无 `@EnableScheduling`”原状。
2. **回滚代码**：删除 14 处 `scheduledExecute()` + `snailJobEnabled` 字段（各自独立、互不依赖），
   删除 `SchedulerFallbackUtils`、`LocalSchedulingConfig`、2 个测试类。
3. **回滚 SQL**：
   ```sql
   DELETE FROM `sj_job` WHERE `namespace_id` = 'dev' AND `biz_id` IN ('biz_201', 'biz_202');
   UPDATE `sj_job` SET `trigger_interval` = '0 0 2 * * ?'
    WHERE `executor_info` IN ('iotDeviceHeartbeatTask', 'iotStreamTrimTask');
   ```

### 7.3 改动规模

- 修改 16 个文件（14 个任务类 + 1 个 `AutoConfiguration.imports` + 1 个 `ruoyi-iot/pom.xml`），新增 6 个文件
  （工具类、配置类、增量 SQL、2 个测试类、本报告）；未改 `application-prod.yml`、
  未改 `script/sql/ry-vue.sql`、未改 `doc/system/数据库增量变更记录.md`（均按 §1.1 独占约束）。
