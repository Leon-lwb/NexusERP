# MES SPC / DC / LT 审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/{spc,dc,lt}`（53 个 java 文件，深读 15 个 + 关联 SQL/前端/配置/定时任务机制） | 覆盖文件数: 约 28 | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-DC-01 | P0 | 功能缺失/跨模块链路 | IoT→`dc_data_record` 归档任务只用 SnailJob `@JobExecutor`，生产 `snail-job.enabled: false` 且无 prod 命名空间任务定义 → 生产环境永不执行，AUTO 采集点永远空 | `IotDataArchiveTask.java:41`、`application-prod.yml:26` |
| MES-DC-02 | P0 | 租户/数据错 | 归档任务线程无租户上下文且未像其他 MES 任务那样 `TenantHelper.dynamic` → 读全租户扫描、写 `tenant_id` 为 NULL/错租户 | `IotDataArchiveTask.java:87`、`TenantHelper.java:232` |
| MES-LT-01 | P0 | 并发/数据错 | `consume()` 用「Java 先算后写绝对值」扣减 `consumed_qty`，非 `setSql` 原子 + `>=` 守卫，正确性完全依赖未配置顺序的 `@Lock4j` → 并发领料可超发 | `LtLotServiceImpl.java:509` |
| MES-SPC-01 | P1 | 功能缺失/统计错 | 只算 Xbar 图控制限，**没有 R 图(D3/D4)/S 图(B3/B4) 控制限**，也没有 np 图/X-MR 图 → 组内变异失控不告警 | `SpcControlChartServiceImpl.java:388` |
| MES-SPC-02 | P1 | 统计错 | Cp/Cpk 仅双边规格可算、无 Pp/Ppk、无最小子组数校验 → 单边规格不出值；1 组样本也出 Cpk | `SpcCapabilityCalculator.java:91` |
| MES-SPC-03 | P1 | 幂等/性能 | `analyze` 是 GET 且每次全量逻辑删除重建数据点 → `spc_data_point` 垃圾行无限增长，`data_id` 每次变化 | `SpcControlChartController.java:107`、`SpcDataPoint.java:49` |
| MES-DC-03 | P1 | 校验/数据错 | 手工录入不校验 `data_type`/单位/量程/负值，NUMERIC 点写文本值会被 SPC 静默忽略 | `DcDataRecordServiceImpl.java:80` |
| MES-DC-04 | P1 | 性能 | SPC 分析经 `listByPointId` 无时间窗、无 limit 全量拉取采集点历史 | `DcDataRecordServiceImpl.java:104` |
| MES-LT-02 | P1 | 校验/数据错 | 领料不校验 `lot.materialId` 与传入 materialId 一致，也不校验超领 → 消耗记录挂错物料、可无限领料 | `LtWoConsumptionServiceImpl.java:95` |
| MES-LT-03 | P1 | 质量/合规 | 效期不拦截：FEFO 只排序不过滤过期，`consume`/`checkLotUsable` 均不校验 `expiry_date` → 过期料可投产 | `LtLotServiceImpl.java:456` |
| MES-LT-04 | P1 | 功能缺失/追溯 | 删除消耗记录不回补批次数量，且完全没有退料/红冲接口 → 追溯链断裂且无纠错路径 | `LtWoConsumptionServiceImpl.java:120` |
| MES-LT-05 | P1 | DDL/租户 | `uk_lot_code(lot_code)`、`uk_point_code(point_code)` 缺 `tenant_id` → 多租户下编码互相冲突 | `ry-mes.sql:561`、`ry-mes.sql:47` |
| MES-LT-06 | P1 | 状态机 | `release()` 放行只看 `status` 不看 `qc_status`，FAIL 批次放行后 `consume` 只校验 status → 不合格批次可绕过 QC 被领用 | `LtLotServiceImpl.java:313` |

## 1. P0 详述

### MES-DC-01 IoT 自动采集归档任务在生产环境永不执行（IoT→MES 链路断开）
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/task/IotDataArchiveTask.java:41`、`ruoyi-admin/src/main/resources/application-prod.yml:26`、`application-prod.yml:33`、`script/sql/ry-vue.sql:10058`、`application-dev.yml:28`
- **现状**: 任务唯一入口是 SnailJob 注解 `@JobExecutor(name = "iotDataArchiveTask")`（`IotDataArchiveTask.java:41`），类内**没有** `@Scheduled` 兜底（对 `mes/dc` 全包 grep `@Scheduled|@JobExecutor` 只命中该注解）。生产配置 `snail-job.enabled: false`（`application-prod.yml:26`，注释明写"生产环境禁用客户端"），命名空间为 `namespace: ${spring.profiles.active}` 即 `prod`（`application-prod.yml:33`）；而任务定义只存在于 `dev` 命名空间：`INSERT INTO sj_job VALUES (7, 'dev', 'biz_7', ... 'iotDataArchiveTask', 3, '0 */5 * * * ?', ...)`（`ry-vue.sql:10058`，全部 33 条 `sj_job` 种子均为 `'dev'`）。开发环境日志证明该任务原本能跑（`ry-vue.sql:10123` 的 `sj_job_executor` 与多处 `sj_job_log_message`）。
- **触发路径**: 生产环境启动 → SnailJob 客户端不装配 → `jobExecute` 永不回调；即使把 `enabled` 改回 true，prod 命名空间下也没有 `biz_7` 任务定义。
- **后果**: `dc_collection_point` 中 `point_type=AUTO` 的采集点永远没有数据（`source=IoT` 的记录 0 条），IoT 已采集的 `iot_device_data` 不会进入 MES；SPC 只能依赖手工录入，`IotDataArchiveTask` 的断线补拉（游标 `last_archive_time`）能力完全浪费。
- **修复建议**: 二选一：① 在 `script/sql/ry-vue.sql` 为 `prod` 命名空间补 `sj_job`（executor `iotDataArchiveTask`，cron `0 */5 * * * ?`）并把 `application-prod.yml:26` 改回 `true`；② 增加一个 `@Scheduled(fixedDelay = 300000)` 薄壳方法复用 `archiveAutoPoints`（与 `IotDataCleanupTask` 同风格），由配置开关控制二者互斥。
- **影响面**: `IotDataArchiveTask.java`、`application-prod.yml`、`ry-vue.sql`（种子需同步 `doc/system/数据库增量变更记录.md`）；存量生产库需补 `sj_job` 行；需补测试（prod profile 下任务可达性）。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/dc/task/IotDataArchiveTaskTest.java` 只覆盖游标推进/分块/截断（5 个 `@Test`），**不覆盖**任务注册与 profile 开关。

### MES-DC-02 归档任务缺少租户上下文，读/写租户语义均错
- **证据**: `IotDataArchiveTask.java:87`、`IotDataArchiveTask.java:187`、`ruoyi-common/ruoyi-common-tenant/src/main/java/com/nexus/common/tenant/helper/TenantHelper.java:232`、`ruoyi-common/ruoyi-common-tenant/src/main/java/com/nexus/common/tenant/handle/PlusTenantLineHandler.java:28`、`PlusTenantLineHandler.java:40`、对比 `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/MaintenanceDueTask.java:81`
- **现状**: `jobExecute` → `archiveAutoPoints` 直接 `collectionPointMapper.selectList(...)`（`IotDataArchiveTask.java:87-90`）并 `dataRecordMapper.insert(record)`（`IotDataArchiveTask.java:187-207`），全程没有 `TenantHelper.dynamic`。SnailJob 线程无登录态 → `TenantHelper.getTenantId()` 返回 null（`TenantHelper.java:232-241`：`getDynamic()` 空则取 `LoginHelper.getTenantId()`，任务线程亦为空）→ `PlusTenantLineHandler.getTenantId()` 返回 `NullValue`（`:28-36`），且 `ignoreTable` 在租户为空时**直接返回 true**（`:40-56`）。同模块其他任务全部显式遍历租户：`MaintenanceDueTask.java:81` `total += TenantHelper.dynamic(tenantId, this::scanAndNotify)`，`EquipmentHealthTask.java:54`、`EquipmentStatusSyncTask.java:72` 同型。
- **触发路径**: 任务被调度（dev 环境已在跑，见 `ry-vue.sql:10123`）→ 无租户上下文。
- **后果**: ① 读：租户过滤被整体关闭，一次扫描所有租户的 `dc_collection_point`（多租户下越权读，且 `last_archive_time` 游标被跨租户推进）；② 写：`dc_data_record.tenant_id` 为 `NOT NULL DEFAULT '000000'`（`ry-mes.sql:57`），MyBatis-Plus 多租户插件以 `NullValue` 注入租户列 → 插入显式 NULL 触发约束错误（归档整体失败），若被忽略则数据落到默认租户，其他租户在 MES 里查不到自己的采集数据。开发环境日志恒为"归档 0 条"（`ry-vue.sql:10185` 等）与该症状一致。
- **修复建议**: 照 `MaintenanceDueTask` 模式改造：先 `TenantHelper.ignore(() -> 查全部有效租户)`，再对每个租户 `TenantHelper.dynamic(tenantId, () -> archiveTenant(...))`，`selectList` 与 `insert` 都在 dynamic 块内执行；`archiveOne` 的游标推进同样要在对应租户上下文内。
- **影响面**: `IotDataArchiveTask.java` 全部数据访问路径；多租户部署（`tenant.enable: true`）下必现；需补测试（断言 insert 的 `tenant_id`）。
- **测试现状**: `IotDataArchiveTaskTest` 无任何 tenant 相关断言（grep `tenant|Tenant` 无命中）。

### MES-LT-01 批次消耗扣减非原子，依赖未配置顺序的分布式锁
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java:495`、`LtLotServiceImpl.java:509`、`LtLotServiceImpl.java:522`、`ruoyi-admin/src/main/resources/application.yml:277`
- **现状**:
  ```java
  @Lock4j(keys = {"#lotId"})                 // :495
  @Transactional(rollbackFor = Exception.class)
  public Boolean consume(Long lotId, BigDecimal consumedQty) {
      LtLot lot = baseMapper.selectById(lotId);              // :502 读
      BigDecimal newConsumed = lot.getConsumedQty().add(consumedQty);   // :509 Java 侧算
      if (remaining.compareTo(BigDecimal.ZERO) < 0) throw ...;         // :511 内存守卫
      baseMapper.update(null, ... .set(LtLot::getConsumedQty, newConsumed)  // :522 写绝对值
          .in(LtLot::getStatus, STATUS_IN_STOCK, STATUS_IN_USE));
  ```
  写入的是**绝对值** `newConsumed`，SQL 里没有任何 `consumed_qty` 维度的守卫（条件只有 `status IN`）；守卫 `remaining >= 0` 是在 Java 内存里基于**已读快照**判断的。项目 `lock4j` 只配了 `acquire-timeout`/`expire`（`application.yml:277-281`），没有配置锁切面与事务切面的先后顺序。
- **触发路径**: 同一批次被两条路径并发消耗——两个用户同时点"FEFO 领料确认"（`plus-ui/src/views/mes/lt/consumption/index.vue:154`），或 `insertByBo` 与 `confirmPick`（`LtWoConsumptionServiceImpl.java:89`、`:178`）同时命中同一 lot。若锁切面在事务切面内层，锁在方法返回时释放而事务尚未提交，第二个线程拿到锁后 `selectById` 仍读到旧 `consumed_qty`（未提交不可见）→ 双重通过守卫 → 批次超发。
- **后果**: `lt_lot.consumed_qty` 可超过 `quantity`，批次状态被判为 `CONSUMED`，而 `lt_wo_consumption` 里存在两条各自"合法"的消耗记录；账面与实物不符且事后无法从记录判断哪条是超发。
- **修复建议**: 把 `:522` 的更新改成原子条件更新：`setSql("consumed_qty = consumed_qty + " + qty)` + `.apply("consumed_qty + {0} <= quantity", qty)`，状态用 `CASE WHEN quantity - consumed_qty - qty = 0 THEN 'CONSUMED' ELSE 'IN_USE' END`，`rows==0` 时抛"剩余不足/状态已变更"。同时确认 `@Lock4j` 切面 order 小于事务切面（否则锁不覆盖提交）。
- **影响面**: `LtLotServiceImpl.consume`、`LtWoConsumptionServiceImpl.insertByBo/confirmPick`、PDA 领料（如接入）；无需 DDL；必须补并发单测。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/lt/service/impl/LtLotServiceImplTest.java`、`LtWoConsumptionServiceImplTest.java` 存在，均为单线程 mock 测试，未覆盖并发。

## 2. P1 详述

### MES-SPC-01 只有 Xbar 图控制限，缺 R/S 图控制限，缺 np 图与 X-MR 图
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/service/impl/SpcControlChartServiceImpl.java:388`、`SpcControlChartServiceImpl.java:391`、`SpcControlChartServiceImpl.java:402`、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/domain/SpcControlChart.java:41`、`script/sql/ry-mes.sql:2868`、`script/sql/ry-mes.sql:2908`
- **现状**: `calcControlLimit` 的 `X_BAR_R` 分支只算 `x̄̄ ± A2(n)·R̄`（`:391-401`），`X_BAR_S` 分支只算 `x̄̄ ± A3(n)·S̄`（`:402-411`）；全类**没有** `D3/D4`（R 图）与 `B3/B4`（S 图）系数（grep `D3|D4|B3|B4` 无命中，只定义了 `A2`/`A3`，`:61-63`）。表结构只存一套控制限 `ucl/cl/lcl`（`SpcControlChart.java:41-48`、`ry-mes.sql:2868-2896`），数据点表也只存一个 `value`（`ry-mes.sql:2908`）。`chartType` 注释枚举为 `X_BAR_R/X_BAR_S/P/C/U`（`SpcControlChart.java:29`），字典 `spc_chart_type` 同样只有这 5 项（`ry-vue.sql:90711-90715`）——**np 图、X-MR(单值-移动极差)图完全缺失**，而计量型单件/破坏性检测场景（`dc_collection_point` 单点采集、`sampleSize` 默认 5 无法满足）恰恰需要 X-MR。
- **触发路径**: 建一张 `X_BAR_R` 图并分析 → 只有 Xbar 图有 UCL/LCL；组内极差持续变大（设备磨损/夹具松动）时 Xbar 可能仍在限内。
- **后果**: SPC 最核心的"位置+离散度"双图监控退化为单图；离散度失控（过程变异增大）不会被 OOC 捕获，也不会触发安灯，直到产品超规格才发现。前端 `plus-ui/src/views/mes/spc/control-chart/index.vue:48-57` 也只展示单套 UCL/LCL，无 R/S 图。
- **修复建议**: `spc_control_chart` 增加 `r_ucl/r_cl/r_lcl`（或 `sigma_ucl/sigma_cl/sigma_lcl`）三列 + `spc_data_point` 增加 `sub_value`（组极差/组标准差）一列（需 DDL 与增量脚本）；`calcControlLimit` 按 `D3/D4`（R 图）与 `B3/B4`（S 图）补算，系数表同 `A2/A3` 放在 `:61-63`；`analyze` 对 `sub_value` 序列独立跑一遍 OOC；字典 `spc_chart_type` 增加 `X_MR`/`NP` 并实现 `x̄ ± E2·MR̄`（X 图）与 `MR̄ ± D3/D4·MR̄`。
- **影响面**: `SpcControlChartServiceImpl`、`SpcControlChart`/`SpcDataPoint` 实体与 VO/BO、前端列表与详情、`ry-mes.sql`、字典种子；需要 DDL；需要补公式单测。
- **测试现状**: `SpcControlChartServiceImplTest.java` 存在，但无 R/S 图控制限用例。

### MES-SPC-02 过程能力只支持双边规格、无 Pp/Ppk、无样本量下限
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/service/SpcCapabilityCalculator.java:91`、`SpcCapabilityCalculator.java:97`、`SpcCapabilityCalculator.java:100`、`SpcControlChartServiceImpl.java:204`、`SpcControlChartServiceImpl.java:288`
- **现状**: `calc()` 的准入条件是 `grandMean == null || sigma == null || usl == null || lsl == null ... return null`（`:91-94`），即**必须同时维护 USL 与 LSL** 才出 Cp/Cpk；只有 USL（如粗糙度/圆度/拉力上限）或只有 LSL（如强度下限）的单边规格一律返回 null，`analyze` 随后把 `cp/cpk/cpkGrade` 清空（`SpcControlChartServiceImpl.java:219-223`）。公式本身正确：`Cp=(USL−LSL)/6σ`、`Cpk=min(USL−μ, μ−LSL)/3σ`（`:97-100`），σ 分别用 `R̄/d2`（`:63`）与 `S̄/c4`（`:83`）估计。**没有 Pp/Ppk**（长期能力，需用整体样本标准差 s（n-1）而非组内 σ），表也没有 `pp/ppk` 列（`ry-mes.sql:2868-2896`）。样本量下限缺失：`analyze` 只要求分组后非空（`SpcControlChartServiceImpl.java:204-206`），`calcCapability` 只要求 USL/LSL 非空（`:288-290`）——**1 个子组（例如 sampleSize=5 只有 5 条记录）也会算出 Cpk 并标 A+/A 等级**。
- **触发路径**: 采集点只有 5 条记录 + 控制图 sampleSize=5 → 1 组 → R̄ 只有 1 个极差 → 出 Cpk=1.8/A+，被当成过程受控且能力充分。
- **后果**: 单边规格的关键特性（形位公差、强度）在系统里永远没有能力指数，质量报表缺项；小样本 Cpk 被误判为合格能力等级，可能被用于放行/客户 PPAP 提交，属于质量决策依据错误。
- **修复建议**: ① `calc` 支持单边：`usl==null` 时 `Cpk=(μ−LSL)/3σ`（Cpl），`lsl==null` 时 `Cpk=(USL−μ)/3σ`（Cpu），两侧都空才返回 null；② 增加 `calcByStdLongTerm`（用整体 s，n-1）输出 `pp/ppk`，表加 `pp/ppk` 列；③ 在 `calcCapability` 加最小子组数（建议 ≥25 组）与最小总样本数校验，不足时不回写 cpk 并在返回体带 `capabilityInsufficient` 标记，前端置灰而非展示等级。
- **影响面**: `SpcCapabilityCalculator`、`SpcControlChartServiceImpl.calcCapability`、`SpcControlChart`/`SpcControlChartVo`、前端 `control-chart/index.vue:57-70`；需要 DDL（pp/ppk 列）；需要补公式单测。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/spc/service/SpcCapabilityCalculatorTest.java` 存在（未覆盖单边规格与小样本）。

### MES-SPC-03 `analyze` 为 GET 且全量逻辑删除重建数据点
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/controller/SpcControlChartController.java:107`、`SpcControlChartServiceImpl.java:232`、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/domain/SpcDataPoint.java:49`、`script/sql/ry-mes.sql:2918`
- **现状**: 端点是 `@GetMapping("/analyze/{chartId}")`（`SpcControlChartController.java:107-110`），但方法内做写操作：回写控制限/能力（`SpcControlChartServiceImpl.java:226-229`）、`dataPointMapper.delete(...)` 后重新 `insert`（`:232-244`）、逐点 `update` 打 OOC 标记（`:251-254`）、命中 RULE1 时调用安灯（`:259-261`）。`SpcDataPoint.delFlag` 带 `@TableLogic`（`SpcDataPoint.java:49`），`spc_data_point` 表**没有** `(chart_id, sample_group)` 唯一键（`ry-mes.sql:2918-2920` 只有普通索引）。
- **触发路径**: 每次点"分析"（`plus-ui/src/views/mes/spc/control-chart/index.vue:386`）→ 删除 + 重建；浏览器预取、监控探活、或用户重复点击都等价于一次全量重算。
- **后果**: ① 逻辑删除导致每分析一次就永久累积 N 行 `del_flag='2'` 的僵尸数据（1 个采集点 10 万条记录 = 每次分析 2 万行垃圾），表体积无界增长且拖慢 `idx_chart_id` 查询；② `data_id` 每次重建都变（雪花新 ID），任何外部引用/审计留痕失效；③ OOC 与安灯副作用挂在 GET 上，可被非预期触发。
- **修复建议**: 改为 `@PostMapping("/analyze/{chartId}")`（前端 api 同步改 `plus-ui/src/api/mes/spc/control-chart.ts:94`）；数据点改为按 `sample_group` upsert 而不是 delete+insert（`spc_data_point` 加 `UNIQUE(tenant_id, chart_id, sample_group)`，需 DDL + 存量库去重脚本）；或对 `dataPointMapper.delete` 改物理删除以避免垃圾行。
- **影响面**: `SpcControlChartController`、`SpcControlChartServiceImpl.analyze`、`SpcDataPoint`、前端 api 与页面、`ry-mes.sql`；需要 DDL（唯一键）与存量清理；需要补测试（重复 analyze 不产生垃圾行）。
- **测试现状**: `SpcControlChartServiceImplTest.java` 无"重复分析"用例。

### MES-DC-03 手工录入不校验数据类型/单位/量程，脏数据直落库并被 SPC 静默忽略
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/service/impl/DcDataRecordServiceImpl.java:80`、`DcDataRecordServiceImpl.java:86`、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/domain/bo/DcDataRecordBo.java:30`、`script/sql/ry-mes.sql:34`、`script/sql/ry-mes.sql:64`
- **现状**: `insertByBo` 只校验采集点存在（`:76-79`），随后把 `numericValue`/`textValue` 原样写入（`:86-87`），不校验：① 与 `dc_collection_point.data_type`（`ry-mes.sql:34` NUMERIC/TEXT/ENUM）是否匹配；② 数值是否为负、是否超量程（表里根本没有 min/max 列，`ry-mes.sql:24-49`）；③ 单位（`unit` 列仅展示，无换算/校验）。BO 上只有 `pointId` 一个 `@NotNull`（`DcDataRecordBo.java:30`），`numericValue`/`collectTime` 均无注解。精度上 `numeric_value` 为 `decimal(14,4)`（`ry-mes.sql:64`），写入超过 4 位小数会被 MySQL 静默四舍五入。
- **触发路径**: 在 `plus-ui/src/views/mes/dc/data-record/index.vue:55` 的"数值"框填 `-999` 或留空只填文本，提交到一个 `data_type=NUMERIC` 的采集点。
- **后果**: ① 负值/超量程数据进入 SPC（`buildGroups` 直接参与均值/极差，`SpcControlChartServiceImpl.java:330-337`），控制限与 Cp/Cpk 被污染；② NUMERIC 点填了 `textValue` 时 `numericValue` 为空，`listByPointId` 的 `isNotNull(numericValue)`（`DcDataRecordServiceImpl.java:107`）会**静默丢弃**该记录——用户以为录进去了，SPC 里没有，且无任何提示。
- **修复建议**: `DcDataRecordBo` 加 `@NotNull collectTime`；`insertByBo` 按 `point.getDataType()` 分流校验：NUMERIC 要求 `numericValue != null` 且非负（或落在新增的 `min_value/max_value` 区间），TEXT 要求 `textValue` 非空，非法值抛 `ServiceException`；`dc_collection_point` 增加 `min_value/max_value/precision` 列（DDL + 增量脚本）；前端录入框按 `dataType` 动态显隐并提示。
- **影响面**: `DcDataRecordServiceImpl`、`DcDataRecordBo`、`DcCollectionPoint`、前端 data-record 页面；需要 DDL；需要补校验单测。
- **测试现状**: `ruoyi-modules/ruoyi-mes/src/test` 下无 `DcDataRecordServiceImplTest`。

### MES-DC-04 SPC 分析全量拉取采集点历史数据
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/service/impl/DcDataRecordServiceImpl.java:104`、`DcDataRecordServiceImpl.java:105`、`SpcControlChartServiceImpl.java:197`
- **现状**: `listByPointId` 是 `selectVoList(... eq(pointId) ... isNotNull(numericValue) ... orderByAsc(collectTime))`，**没有时间范围参数、没有 `last()`/limit**（`DcDataRecordServiceImpl.java:104-109`）；`analyze` 直接把它整表结果转成 `List<DcDataRecordVo>` 并全量在内存里分组（`SpcControlChartServiceImpl.java:197`、`:203`）。
- **触发路径**: 一个 AUTO 采集点按 5 分钟一条运行一年 ≈ 10.5 万条；若频率是秒级（`dc_collection_point.frequency` 允许配置，`ry-mes.sql:36`）则轻松上千万条。此时点"分析"。
- **后果**: 单次请求加载全量记录 + 每个 `DcDataRecordVo` 对象开销 → 堆内存暴涨/OOM 或接口超时（`analyze` 还是同步 GET，前端会一直转圈直至网关超时）；即使用户只关心最近 25 组数据也照样全表拉。
- **修复建议**: `listByPointId` 改为 `listByPointId(pointId, startTime, endTime, limit)`，`analyze` 默认取"最近 N 组 × sampleSize 条"（N 默认 25~50，可配置），或直接用 `ORDER BY collect_time DESC LIMIT k` 子查询再反转；控制图 BO 增加 `analysisWindowDays`/`maxGroups` 字段。
- **影响面**: `IDcDataRecordService.listByPointId`（含其他调用方，需全量 grep）、`SpcControlChartServiceImpl.analyze`、`SpcControlChartBo`；无 DDL；需补测试（大结果集分页）。
- **测试现状**: 无相关测试。

### MES-LT-02 领料不校验批次物料一致性，也不校验超领
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtWoConsumptionServiceImpl.java:95`、`LtWoConsumptionServiceImpl.java:176`、`LtWoConsumptionServiceImpl.java:178`、对比 `LtLotServiceImpl.java:427`
- **现状**: `insertByBo` 只取批次冗余编码（`:94-96`）就写 `record.setMaterialId(bo.getMaterialId())`（`:95`）——`materialId` 来自请求体，**从不与 `lot.getMaterialId()` 比对**；`confirmPick` 更是把 `pickBo.getMaterialId()` 覆盖到每一条明细上（`:176`）后循环 `insertByBo`（`:178`）。项目里已有正确实现：`LtLotServiceImpl.checkLotUsable` 会校验 `!materialId.equals(lot.getMaterialId()) → "批次物料与要求物料不符"`（`:427-430`），但**领料路径没有调用它**。同时没有任何"累计领料量 ≤ 需求/BOM 用量"的校验（`LtWoConsumptionBo` 只有 `@NotNull`，`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/domain/bo/LtWoConsumptionBo.java:39`）。
- **触发路径**: FEFO 领料弹窗里先选工单/物料，再手工从批次选择器里挑一个**其他物料**的批次（`plus-ui/src/views/mes/lt/consumption/index.vue:205-223` 的批次选择器不带物料过滤条件），或直接调 `POST /mes/lt/consumption` 传任意 `materialId`。
- **后果**: ① `lt_wo_consumption.material_id` 与 `lt_lot.material_id` 不一致 → 按物料追溯/成本归集时这批消耗被算到错误物料上（`trace()` 与 `selectByLotId` 输出自相矛盾）；② 无超领上限 → 同一工单可反复领料直到批次耗尽，工单用料计划形同虚设。
- **修复建议**: `insertByBo` 在 `lotService.consume` 之前调用 `checkLotUsable(bo.getMaterialId(), bo.getLotId())` 并校验 `usable=true`，或至少 `Objects.equals(lot.getMaterialId(), bo.getMaterialId())` 否则抛异常；`materialId` 从批次派生而不是信客户端；超领校验按 `MesErpBridgeService` 取 BOM 单耗 × 工单数量作为上限（无 BOM 时跳过并记 warn）。
- **影响面**: `LtWoConsumptionServiceImpl.insertByBo/confirmPick`、前端领料弹窗（批次选择需按 materialId 过滤）；无 DDL；需补测试。
- **测试现状**: `LtWoConsumptionServiceImplTest.java` 存在，未覆盖物料不一致场景。

### MES-LT-03 效期完全不拦截，过期批次可被推荐并投产
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java:456`、`LtLotServiceImpl.java:464`、`LtLotServiceImpl.java:498`、`LtLotServiceImpl.java:417`、`script/sql/ry-mes.sql:549`
- **现状**: `recommendFefo` 的过滤条件是 `material_id + status=IN_STOCK + qc_status=PASS`（`:460-463`），只把 `expiry_date` 用作**排序**（`:464 orderByAsc(LtLot::getExpiryDate)`），**没有 `expiry_date >= today` 条件**；`consume()` 只校验 `status` 与剩余量（`:506-514`），`checkLotUsable()` 同样只查 `status`/`qc_status`/剩余量（`:431-445`），三处都不看效期。全模块没有任何"过期批次自动标记/隔离"的定时任务（`mes/lt` 下无 `task` 包）。
- **触发路径**: 批次 `expiry_date` 已过期（前端列表会用黄色高亮提示 `plus-ui/src/views/mes/lt/lot/index.vue:63`，但仅提示），用户在 FEFO 推荐列表里选中它 → 确认领料 → 成功扣减并投产。
- **后果**: 过期物料（尤其胶水、锡膏、化工料）被投入生产，产出品存在批次性质量风险且不可追溯召回；FEFO 的"先到期先出"反而**优先推荐已过期批次**（排序把最早到期的排最前）。
- **修复建议**: `recommendFefo` 查询加 `.and(w -> w.isNull(LtLot::getExpiryDate).or().ge(LtLot::getExpiryDate, today))`；`checkLotUsable` 增加 `expiry_date < today → usable=false, reason="批次已过期"`；`consume()` 在扣减前同样拒绝过期批次（可加配置开关允许特采）；另加每日任务扫描 `expiry_date < today AND status='IN_STOCK'` 自动 `quarantine`。
- **影响面**: `LtLotServiceImpl.recommendFefo/checkLotUsable/consume`、`ILtLotService` 契约、前端 FEFO 弹窗；无 DDL；需补测试与任务种子。
- **测试现状**: `LtLotServiceImplTest.java` 未覆盖效期。

### MES-LT-04 删除消耗记录不回补数量，且无退料/红冲接口
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtWoConsumptionServiceImpl.java:109`、`LtWoConsumptionServiceImpl.java:120`、`LtWoConsumptionServiceImpl.java:121`、`plus-ui/src/views/mes/lt/consumption/index.vue:40`
- **现状**: `deleteByIds` 的注释与实现都自认"仅删除记录,不回滚已扣减数量"（`:109`、`:120`），只做 `baseMapper.deleteByIds(...)`（`:121`）。`ILtWoConsumptionService`/`LtWoConsumptionController` 中**不存在**退料（reverse/return）方法（grep `退料|reverse|return|红冲` 在 lt service/controller 无命中；前端 `consumption/index.vue` 只有"新增消耗记录/删除/FEFO 领料"三个按钮，`:34-41`）。`lt_wo_consumption` 表也没有冲销关联列（`ry-mes.sql:600-625`）。
- **触发路径**: 领料录错数量/选错批次 → 用户点"删除"（有 `mes:lt:consumption:remove` 权限，`ry-vue.sql:92508`）→ 记录消失，`lt_lot.consumed_qty` 保持已扣减。
- **后果**: ① 批次 `consumed_qty` 与消耗记录之和永久不一致（批次显示已消耗 100，但能查到的消耗记录只有 60）——**追溯链断裂且无法自证**，`trace(lotId)` 输出的消耗明细不完整；② 没有退料路径，车间实际退料只能靠"再建一张批次"绕过，账实分离。
- **修复建议**: ① 删除改为"红冲"：新增 `reverse_of`（关联被冲销记录）与允许负数量的冲销记录，同时用 MES-LT-01 的原子 SQL 把 `consumed_qty` 减回并恢复 `status`（`CONSUMED → IN_USE/IN_STOCK`）；② 若保留物理删除，则必须在同一事务内回补 `lt_lot.consumed_qty` 并重算状态，且删除前校验批次当前状态允许回退；③ 前端把"删除"改为"冲销"并要求填原因。
- **影响面**: `LtWoConsumptionServiceImpl`、`ILtWoConsumptionService`、`LtWoConsumptionController`、前端 consumption 页面、`ry-mes.sql`（新增列）；需要 DDL 与存量对账脚本；需补测试。
- **测试现状**: `LtWoConsumptionServiceImplTest.java` 未覆盖删除回补。

### MES-LT-05 批次/采集点编码唯一键缺 `tenant_id`，多租户下互相冲突
- **证据**: `script/sql/ry-mes.sql:561`、`script/sql/ry-mes.sql:47`、`script/sql/ry-mes.sql:590`、对比 `script/sql/ry-mes.sql:657`
- **现状**: `lt_lot` 的唯一键是 `UNIQUE INDEX uk_lot_code(lot_code)`（`ry-mes.sql:561`，无 `tenant_id`），`dc_collection_point` 是 `UNIQUE INDEX uk_point_code(point_code)`（`ry-mes.sql:47`，无 `tenant_id`）。同库同脚本里已有正确写法可对照：`mes_content_file` 用 `UNIQUE INDEX uk_tenant_content_code(tenant_id, content_code)`（`ry-mes.sql:657`）。业务代码侧也是全局查重：`LtLotServiceImpl.insertByBo` 用 `exists(lotCode)` 判重（`:124`），`DcCollectionPointServiceImpl.insertByBo` 用 `exists(pointCode)`（`:74`），都受多租户插件过滤——**查重在租户内，约束在租户间**，二者口径不一致。
- **触发路径**: 租户 A 建批次编码 `LOT-20260920-001`；租户 B 新建同编码批次（各自前端都提示"编码可用"，因为租户内查重通过）→ 插入时被 `uk_lot_code` 拒绝 → 抛 500 `DuplicateKeyException`，租户 B 无法创建该编码。`point_code` 同型（且 `point_code` 常按设备位号命名，跨租户撞码概率高）。
- **后果**: 多租户部署下，后建租户的批次/采集点创建随机失败，且错误信息是数据库唯一键冲突而非业务提示；运维只能改编码规避。
- **修复建议**: 重建唯一键为 `uk_tenant_lot_code(tenant_id, lot_code)`、`uk_tenant_point_code(tenant_id, point_code)`（需 DDL + 存量库先删旧键再加新键，登记 `doc/system/数据库增量变更记录.md`）；同时把 `LtSnBinding` 的 `uk_sn_material(sn_id, material_id)`（`:590`）一并核对口径。
- **影响面**: `ry-mes.sql`、存量生产库升级脚本、`LtLotServiceImpl.insertByBo`、`DcCollectionPointServiceImpl.insertByBo/updateByBo`；需要 DDL。
- **测试现状**: 无多租户唯一键测试。

### MES-LT-06 QC 不合格批次可经"放行"绕过 QC 被领用
- **证据**: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java:313`、`LtLotServiceImpl.java:354`、`LtLotServiceImpl.java:506`、`script/sql/ry-vue.sql:92501`
- **现状**: IQC 不合格时 `updateQcStatus(FAIL)` 会 `qc_status='FAIL'` 并 `quarantine()`（`:354-356`，`status → QUARANTINED`）；`applyIqcResult` 同理（`:391-398`）。但 `release()` 只把 `status` 从 `QUARANTINED` 恢复为 `IN_STOCK/IN_USE/CONSUMED`（`:304-316`），**完全不检查、也不修改 `qc_status`**（仍是 `FAIL`）。而 `consume()` 的准入条件只有 `status IN (IN_STOCK, IN_USE)`（`:506-508`），**不校验 `qc_status`**。`release` 是常规按钮（权限码 `mes:lt:lot:release`，`ry-vue.sql:92501`；前端对任何 `QUARANTINED` 行都显示"放行"，`plus-ui/src/views/mes/lt/lot/index.vue:102-111`）。
- **触发路径**: 批次 IQC 判不合格 → 自动隔离（`qc_status=FAIL, status=QUARANTINED`）→ 操作员点"放行"（例如误以为放行=解除误判）→ `status=IN_STOCK`，`qc_status` 仍为 `FAIL` → 该批次不会出现在 FEFO 推荐里（`recommendFefo` 过滤 `qc_status=PASS`，`:463`），但**手工领料/`POST /mes/lt/consumption` 直接传 lotId 可正常扣减**（`consume` 只看 status）。
- **后果**: 已判定不合格的物料可被投入生产且系统不报错；QC 门禁被一个常规按钮绕过，事后从 `lt_lot` 只能看到 `status=IN_STOCK`，质量追溯困难。
- **修复建议**: ① `consume()` 增加 `QC_STATUS_PASS.equals(lot.getQcStatus())` 校验（与 `checkLotUsable` 口径一致）；② `release()` 增加 `qc_status` 前置条件：仅 `PASS` 可放行，`FAIL` 必须走"特采/让步接收"专用接口（记录审批人+原因）才能放行；③ 前端放行按钮按 `qcStatus` 置灰。
- **影响面**: `LtLotServiceImpl.consume/release`、`ILtLotService`、前端 lot 页面按钮态、可能影响 PDA 领料；需要 DDL（特采字段，可选）；需补状态机测试。
- **测试现状**: `LtLotServiceImplTest.java` 未覆盖 `FAIL → release → consume` 路径。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果 / 建议 |
|---|---|---|---|
| MES-SPC-04 | P/U 图把 `sampleSize` 复用为"检验数/单位数"，不支持变样本量；且 `statValue` 先四舍五入到 4 位再乘 n 还原缺陷数，引入舍入误差 | `SpcControlChartServiceImpl.java:363`、`:370`、`:416`、`:421` | `p̄` 与真实检验数不符；变批量抽检场景 p 图不可用。建议增加"组检验数"字段，控制限按逐组 n_i 计算 |
| MES-SPC-05 | 计量型子组按"连续 sampleSize 条记录"硬切，跨停机/跨班次/跨工单混合 | `SpcControlChartServiceImpl.java:317`、`:322` | 组内变异被人为放大（跨班次差异混入组内），控制限与 Cp/Cpk 失真。建议按 `mes_wo_id`/`card_id`/班次分组 |
| MES-SPC-06 | `spc_data_point.sample_index` 恒为 null，组内原始明细无法回溯，也无异常值剔除 | `SpcControlChartServiceImpl.java:240`、`ry-mes.sql:2907` | 数据点只能看到组均值，无法复核原始值。建议落组内序号或保留 `dc_data_record` 关联列 |
| MES-DC-05 | `dc_data_record` 无保留/清理策略，无归档或分区 | `IotDataArchiveTask.java:153`（只插入）、`script/sql/ry-mes.sql:55`（对比 IoT 侧有 `IotDataCleanupTask`） | 表无界增长，拖慢 SPC 与列表查询。建议加保留天数配置 + 分批删除任务（`delete ... limit`） |
| MES-DC-06 | 按"采集点 + 时间范围"查询缺组合索引 | `script/sql/ry-mes.sql:77`、`:78`、`:80` | `idx_point_id` 与 `idx_collect_time` 是单列索引，时间范围过滤只能选其一。建议加 `idx_point_time(tenant_id, point_id, collect_time)` |
| MES-DC-07 | 手工录入重复采集时间直接抛 `DuplicateKeyException`（500），无前置友好校验 | `DcDataRecordServiceImpl.java:88`、`:92`、`script/sql/ry-mes.sql:77` | 用户看到数据库错误。建议插入前按 `(point_id, collect_time)` 查重并提示 |
| MES-DC-08 | 归档任务单段 120 分钟把设备**所有属性**拉回内存再 Java 侧过滤 | `IotDataArchiveTask.java:153`、`:154`、`:171` | 高频设备（秒级上报）单段可拉数十万行。建议 `getHistoryData` 增加 `propertyCode` 下推过滤与分页 |
| MES-DC-09 | `archiveAutoPoints` 上的 `@Transactional` 因自调用失效（`jobExecute` 内部 `this.` 调用） | `IotDataArchiveTask.java:64`、`:69`、`:85` | 事务注解形同虚设（逐点 try/catch 使影响有限，但注释声称的原子性不成立）。建议改为外部 bean 调用或去掉误导性注解 |
| MES-DC-10 | SPC 安灯限频键只按 `chartId`，且先占坑后调用；`callAndon` 抛异常时坑位仍保留 10 分钟 | `SpcControlChartServiceImpl.java:591`、`:592`、`:608` | 真实告警可能被上一次失败的调用吞掉。建议失败时删除限频键，或按 `chartId+rule` 维度限频 |
| MES-DC-11 | `property_code` 仅 IoT 归档时填充，手工录入不写 | `DcDataRecordServiceImpl.java:80`、`IotDataArchiveTask.java:190` | 一个采集点挂多个 IoT 属性时手工记录无法区分属性。建议手工录入允许/要求选择 `propertyCode` |
| MES-LT-07 | `lt_wo_consumption` 缺 `(wo_id, lot_id)` 幂等键与重复提交防抖 | `script/sql/ry-mes.sql:620`、`LtWoConsumptionServiceImpl.java:102` | 重复点击/网络重试会生成两条消耗记录（各自都扣了批次数量）。建议前端防重 + 服务端可选幂等键 |
| MES-LT-08 | `updateByBo` 允许改 `quantity`，仅校验不小于已消耗量 | `LtLotServiceImpl.java:168` | 把 `quantity` 调到刚好等于已消耗量会让批次"提前耗尽"，且无调整留痕。建议数量调整走独立接口并记录 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | R 图 / S 图控制限（D3/D4、B3/B4） | `SpcControlChartServiceImpl.java:388-411` 只算 Xbar 限 | 表 `spc_control_chart` 已有 ucl/cl/lcl（需扩展）、系数表位置 `:61-63` | 中（DDL + 计算 + 前端） |
| 2 | np 图 / X-MR（单值-移动极差）图 | `SpcControlChart.java:29`、`ry-vue.sql:90711-90715` 仅 5 种类型 | 字典 `spc_chart_type`、`chartType` 字段可直接扩展 | 中 |
| 3 | Pp/Ppk 长期能力 + 单边规格 Cpk | `SpcCapabilityCalculator.java:91` 双规格硬门槛、无 pp/ppk 列 | `SpcCapabilityCalculator` 已可复用 σ 估计 | 小（纯计算 + 2 列） |
| 4 | 异常值/离群点剔除 | `SpcControlChartServiceImpl.java:235-244` 直接全量入点 | 无字段；`spc_data_point` 可加 `excluded` 标记 | 中 |
| 5 | 退料 / 红冲（反向消耗） | `LtWoConsumptionServiceImpl.java:120` 明示不回滚；无 reverse 接口 | `lt_wo_consumption` 表结构可加 `reverse_of` | 中（DDL + 服务 + 前端） |
| 6 | 过期批次自动隔离 / 领用拦截 | `LtLotServiceImpl.java:456-471`、`:498-514` 均不校验效期 | `lt_lot.expiry_date` + `idx_expiry`（`ry-mes.sql:549`、`:565`）已就绪 | 小 |
| 7 | `dc_data_record` 保留策略与清理任务 | 无 `@Scheduled`/`@JobExecutor` 清理任务（`mes/dc` 全包仅归档任务） | 可照抄 `ruoyi-iot` 的 `IotDataCleanupTask` | 小 |
| 8 | 采集点量程/精度配置 | `ry-mes.sql:24-49` 无 min/max/precision 列 | `unit`、`data_type` 已有 | 小（DDL + 校验） |
| 9 | SPC 分析窗口/数据量上限 | `DcDataRecordServiceImpl.java:104` 无 limit | `SpcControlChartBo` 可加字段 | 小 |
| 10 | 采集点 → 控制图自动分析（IoT 数据到达即判 OOC） | `analyze` 仅手工触发（`SpcControlChartController.java:107`） | `IotDataArchiveTask` 归档后可直接触发 | 中 |

## 5. 已核查确认无问题（证明覆盖范围）

- **SPC 系数表取值正确**：`A2 = {1.880,1.023,0.729,0.577,0.483,0.419,0.373,0.337,0.308}`（n=2..10，`SpcControlChartServiceImpl.java:61`）与 GB/T 4091 一致；`A3`（`:63`）、`D2`（`SpcCapabilityCalculator.java:26`）、`C4`（`:31`）逐值核对均正确，越界（n<2 或 n>10）会抛异常（`SpcControlChartServiceImpl.java:563-568`、`SpcCapabilityCalculator.java:124-130`）。
- **Cpk 公式正确**：`min(USL−μ, μ−LSL)/3σ`，`μ` 取组均值总平均 X̿（`SpcCapabilityCalculator.java:98-100`、`SpcControlChartServiceImpl.java:291`）；组内 σ 用 `R̄/d2`（`:63`）与 `S̄/c4`（`:83`）；组标准差用 n−1（`SpcControlChartServiceImpl.java:348`）。
- **OOC 不止"点出界"**：实现了 4 条规则——RULE1 超限、RULE2 连续 9 点同侧、RULE3 连续 6 点单调、RULE4 连续 14 点交替（`SpcControlChartServiceImpl.java:462-492`），且用**真实下标**而非按值匹配定位窗口，避免重复测量值错位（`:474-476`）；窗口边界判断正确（`idx>=8/5/13`）。
- **权限码与 `sys_menu` 种子一一对应**：`mes:spc:chart:{list,query,add,edit,remove,analyze}`（`ry-vue.sql:92486-92491` vs `SpcControlChartController.java:41-105`）、`mes:spc:point:{list,query}`（`:92492-92493`）、`mes:dc:point:*`（`:92478-92482`）、`mes:dc:record:{list,query,add}`（`:92483-92485`）、`mes:lt:lot:*`（`:92496-92504`、`:92513-92514`）、`mes:lt:consumption:*`（`:92505-92508`）、`mes:lt:binding:*`（`:92509-92512`）全部命中，无"无种子 → 403"问题。
- **字典值齐备**：`spc_chart_type`、`dc_data_type`、`dc_collection_point_type`、`mes_data_source`、`mes_lot_status`、`mes_lot_qc_status` 均有 `sys_dict_type` + `sys_dict_data` 条目（`ry-vue.sql:90673-90680`、`:90704-90715`、`:91038-91049`），前端 `useDict` 绑定的名字与种子一致。
- **Controller 校验与权限注解齐全**：`DcCollectionPointController`/`DcDataRecordController` 标了 `@Validated`（各自 `:24`），spc/dc/lt 全部端点都有 `@SaCheckPermission`，无裸端点。
- **AUTO 采集点强制绑定 IoT 设备**：`validateAndNormalizeIotFields` 对 `AUTO` 要求 `iotDeviceId` 非空，非 AUTO 清空 IoT 字段（`DcCollectionPointServiceImpl.java:163-172`）。
- **归档写入幂等**：依赖 `uk_point_collect_time(point_id, collect_time)`（`ry-mes.sql:77`）并在逐条插入时捕获 `DuplicateKeyException` 跳过（`IotDataArchiveTask.java:206-215`），断线补拉不会重复入账。
- **归档游标语义正确**：`last_archive_time` 优先、空结果也推进、任一分块异常则不推进（`IotDataArchiveTask.java:119-140`），并有 24h 截断保护（`:125-129`）；测试覆盖 5 条游标路径。
- **批次状态机使用 CAS**：`quarantine`/`scrap`/`release`/`consume`/`updateQcStatus` 全部为条件更新 + `rows==0` 抛"状态已变更"（`LtLotServiceImpl.java:243-250`、`:273-280`、`:313-319`、`:522-529`、`:346-352`），状态维度无读-改-写覆盖（数量维度除外，见 MES-LT-01）。
- **IQC 回写幂等且失败自动隔离**：`applyIqcResult` 按 `iqc_id` 反查批次、无关联批次返回 false 不报错、FAIL 时仅在 `IN_STOCK/IN_USE` 时隔离并记 warn（`LtLotServiceImpl.java:368-400`）。
- **批次删除有追溯保护**：删除前校验状态仅 `IN_STOCK/SCRAPPED`，且存在消耗记录或 SN 绑定时拒绝（`LtLotServiceImpl.java:196-219`）。
- **SPC 安灯不会 NPE**：`triggerAndonForOoc` 以 `mesWoId/cardId/stepRecordId = null` 调用，`ProAndonServiceImpl.callAndon` 对 null 有分支保护（`ProAndonServiceImpl.java:142`、`:155`），失败仅告警不阻断分析（`SpcControlChartServiceImpl.java:608-611`）。
- **前端 API 层无缺失函数**：`analyzeControlChart`（`plus-ui/src/api/mes/spc/control-chart.ts:94` → 页面 `:386`）、`listDataPointByChart`（`data-point.ts:17`）、`confirmPick`/`recommendForWo`（`lt/consumption.ts:85-89` → 页面 `:461`）、`updateLotQcStatus`/`recommendFefo`/`traceLot`（`lt/lot.ts:91-99` → 页面 `:438`、`:467`、`:478`）均已接线，本模块不存在"后端有端点前端不可达"。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `sampleIndex` | 表有 `spc_data_point.sample_index`（`ry-mes.sql:2907`），服务恒写 null（`SpcControlChartServiceImpl.java:240`） | 前端 `SpcDataPointVO` 未使用 | 后端写入即空，属无效列（见 MES-SPC-06） |
| `propertyCode` | 表/实体有（`ry-mes.sql:60`），仅 IoT 归档填充（`IotDataArchiveTask.java:190`） | 手工录入表单无该字段（`plus-ui/src/views/mes/dc/data-record/index.vue:55-58`） | 手工记录无法按属性区分（见 MES-DC-11） |
| `minValue/maxValue`（量程） | 表/实体均无 | 表单也无 | 双向缺失，无法做超量程校验（见 MES-DC-03） |
| `pp/ppk` | 表/实体/VO 均无 | 列表只展示 `cpk/cpkGrade`（`control-chart/index.vue:57-70`） | 双向缺失（见 MES-SPC-02） |
| `expiryDate` | 表 `lt_lot.expiry_date` 有（`ry-mes.sql:549`） | 列表展示并高亮即将到期（`lt/lot/index.vue:61-63`）、表单可编辑（`:186`） | 展示齐备但**服务端不用它做拦截**（见 MES-LT-03），前端"预警"给用户虚假安全感 |
| 退料数量/`reverse_of` | 表/BO/接口均无 | 无按钮 | 双向缺失（见 MES-LT-04） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：
  - MES-DC-01：补 prod 命名空间 `sj_job` 种子 + 打开 `snail-job.enabled`，或加 `@Scheduled` 兜底（二选一，附 SQL 增量与变更记录）。
  - MES-DC-02：`IotDataArchiveTask` 改为遍历租户 + `TenantHelper.dynamic` 包裹全部读写（照 `MaintenanceDueTask.java:81`）。
  - MES-LT-01：`consume()` 改 `setSql` 原子扣减 + `consumed_qty + qty <= quantity` 守卫，并确认 `@Lock4j` 切面 order 在事务之外。
- **批次 B（P1，质量与追溯优先）**：MES-LT-06（QC 门禁绕过）、MES-LT-03（效期拦截）、MES-LT-02（物料一致性 + 超领）、MES-LT-05（唯一键补 tenant_id，DDL）。
- **批次 C（P1，SPC 统计正确性）**：MES-SPC-01（R/S 图控制限 + np/X-MR）、MES-SPC-02（单边规格 + Pp/Ppk + 最小样本量）、MES-SPC-03（analyze 改 POST + upsert 数据点）。
- **批次 D（P1/P2，DC 数据质量与性能）**：MES-DC-03、MES-DC-04、MES-DC-05~11、MES-LT-04（退料/红冲，涉及 DDL 与前端，可与 B 合并）、MES-LT-07/08、MES-SPC-04/05/06。

## 8. 未覆盖范围

- 未逐文件读 `spc`/`dc`/`lt` 的 BO/VO/Mapper/Domain 全部字段（按规范 §4.5 用 grep 核对，仅在发现疑似不一致时局部 read）。
- 未运行任何测试、未连接数据库验证实际执行计划；`dc_data_record` 的索引评估基于 DDL 静态分析。
- 未审计 PDA 侧领料入口（`mes/pda`）是否也走 `LtWoConsumptionService`，以及 `spc_data_point` 的租户列配置细节。
- 未核对 `doc/system/数据库增量变更记录.md` 中是否已登记上述 DDL 需求。
- 未审计前端 `dc/data-record`、`lt/lot` 页面的完整布局细节（仅核对字段接线与按钮权限）。
