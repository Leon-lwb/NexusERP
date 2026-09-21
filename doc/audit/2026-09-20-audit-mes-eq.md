# MES 设备管理（eq）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq`（设备档案/保养计划/保养记录/点检/维修/维修备件/备件库存/健康度 EWMA/OEE/4 个定时任务）+ 前端 `plus-ui/src/api/mes/eq`、`plus-ui/src/views/mes/eq` + `script/sql/ry-mes.sql`(eq 段)、`ry-vue.sql`(sj_job/菜单/字典)、`ry-iot.sql`(关联字段) | 覆盖文件数: 21 个 Java 源文件精读 + 10 个文件定向 grep + 4 段 SQL | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| MES-EQ-01 | P0 | 功能缺失 | 生产环境 `snail-job.enabled: false`，eq 全部 4 个任务（保养提醒/状态同步/健康评估/OEE 聚合）在生产**一个都不会执行** | `application-prod.yml:24-26`、`MaintenanceDueTask.java:53`、`EquipmentStatusSyncTask.java:43`、`EquipmentHealthTask.java:33`、`EqOeeAggregateTask.java:27` |
| MES-EQ-02 | P0 | 功能缺失 | `oeeAggregateTask` / `equipmentHealthTask` 在 `sj_job` **无任何任务记录**（只注册了执行器），即使打开 SnailJob 也不会被调度 | `ry-vue.sql:10050-10083`、`ry-vue.sql:10125-10126`、`ry-vue.sql:10147` |
| MES-EQ-03 | P1 | 口径/功能缺失 | OEE 的表现率/质量率硬编码 1.0、计划时长固定 480min/天、保养时长恒 0 → OEE 只等于"1−手工停机/480"，与工单报工/SPC 良率完全脱钩 | `EqOeeServiceImpl.java:286,290,310-311,329-330`、`IEqOeeService.java:31`、`ry-mes.sql:309-317` |
| MES-EQ-04 | P1 | 跨模块链路 | 备件库存是**第二套独立库存**（`eq_spare_part.current_stock`），与 ERP `wm_*` 库存零关联：无同步、无采购建议、无出库单 | `EqSparePartServiceImpl.java:194-203,258-261`；全 eq 包 grep `WmStock\|IErp\|Purchase` 命中 0 |
| MES-EQ-05 | P1 | 状态机 | 设备进入 `MAINTENANCE` 后无任何自动恢复路径（唯一写入点，无清除点，且状态同步任务不碰它）→ 永久卡"保养中" | `EqMaintenancePlanServiceImpl.java:230-232`、`EquipmentStatusSyncTask.java:52-54`、`IEqEquipmentService.java:26` |
| MES-EQ-06 | P1 | 状态机 | 维修单无作废/撤销入口，误报修后设备永远存在"活跃报修" → 永久 FAULT 无法自动恢复；健康度还引用了不存在的 `CANCELLED` | `EqRepairController.java:48-139`、`EqEquipmentMapper.java:32-33`、`EqHealthServiceImpl.java:186`、`ry-vue.sql:90627-90629` |
| MES-EQ-07 | P1 | 幂等/并发 | `completePlan` 是读-改-写：并发/重复提交会写两条保养记录并把 `next_date` 推进两次 | `EqMaintenancePlanServiceImpl.java:189-196,210,222-226` |
| MES-EQ-08 | P1 | 幂等/数据错 | 维修领用备件明细可被**事后删除并回滚库存**（`batchAdd` 有 REPAIRING 守卫，`deleteByIds` 没有） | `EqRepairSpareServiceImpl.java:84-86` vs `124-147` |
| MES-EQ-09 | P1 | 功能缺失 | 网页端点检只覆盖 `eq_equipment.last_inspection` JSON（无历史），不写 `eq_inspection_record`；只有 PDA/工位入口写历史表 | `EqEquipmentServiceImpl.java:496-512`、`ry-mes.sql:216`、`PdaEqServiceImpl.java:101` |
| MES-EQ-10 | P1 | 校验 | 点检记录可任意删除（含已完成任务下的记录），"点检不可篡改"不成立 | `EqPatrolServiceImpl.java:372-375`、`EqPatrolController.java:264-266` |
| MES-EQ-11 | P1 | 校验/状态机 | `batchSubmitRecords` 只校验 `bos.get(0).getTaskId()`，后续条目可携带已结束任务的 taskId 绕过守卫 | `EqPatrolServiceImpl.java:388-397` |
| MES-EQ-12 | P1 | 功能缺失 | 保养到期无超期升级（固定 24h 限频 + 一条安灯）；SSE 推送是死代码（在线用户恒为空集） | `MaintenanceDueTask.java:122-149,152-162,185-187` |

## 1. P0 详述

### MES-EQ-01 生产环境 SnailJob 客户端被禁用，eq 全部定时任务停摆
- **证据**: `ruoyi-admin/src/main/resources/application-prod.yml:24-26`（`# snail-job 配置（生产环境禁用客户端…）` / `snail-job:` / `enabled: false`）、`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/MaintenanceDueTask.java:53`、`EquipmentStatusSyncTask.java:43`、`EquipmentHealthTask.java:33`、`EqOeeAggregateTask.java:27`
- **现状**: 复核 09-19 交接报告 Q9 —— **代码写法已修**（4 个任务全部是 `@JobExecutor`，eq 包内 `@Scheduled` 命中 0，符合规范）；但生产 profile 里 SnailJob 客户端整体 `enabled: false`，`@JobExecutor` 是 SnailJob 的执行器注解，客户端不启动则**没有任何调度器会回调 `jobExecute`**。
- **触发路径**: 以 `prod` profile 部署 → 不启动 SnailJob 客户端 → 保养到期提醒、IoT 设备状态联动、健康度日评、OEE 日聚合全部静默不执行（无报错、无日志、无告警）。
- **后果**: ①保养超期无人提醒；②设备掉线/恢复状态永不回写，看板显示的状态长期失真；③健康度无数据、LOW 安灯永不产生（预测性维护等于没有）；④OEE 看板永远空白（除非人工点"手动聚合"）。属于"功能完全不可用"级。
- **修复建议**: 二选一 —— (a) 生产启用 SnailJob 客户端（`application-prod.yml:26` 改 `true` 并配好 `snail-job.server` 地址/命名空间，同时确认 `ruoyi-snailjob-server` 已部署）；(b) 若生产确实不部署 SnailJob，则给这 4 个任务补 `@Scheduled` 兜底（Bean 上加 `@ConditionalOnProperty(name="snail-job.enabled", havingValue="false")`，避免双调度重复执行），或改由 `ruoyi-job` 之外的本地调度承载。
- **影响面**: `application-prod.yml`、4 个 task 类；无需 DDL；建议补测试（断言两种 profile 下至少有一种调度器生效）。
- **测试现状**: 无单测覆盖 task 调度装配（`ruoyi-modules/ruoyi-mes/src/test` 下无 eq task 测试）。

### MES-EQ-02 `oeeAggregateTask` / `equipmentHealthTask` 未在 `sj_job` 注册，永不触发
- **证据**: `script/sql/ry-vue.sql:10050-10083`（`sj_job` 全部 INSERT：与 eq 相关的只有 `biz_8 maintenanceDueTask`、`biz_9 equipmentStatusSyncTask` 两条）、`ry-vue.sql:10125-10126`（`sj_job_executor` 有 `oeeAggregateTask`/`equipmentStatusSyncTask`）、`ry-vue.sql:10147`（`sj_job_executor` 有 `equipmentHealthTask`）、`EqOeeAggregateTask.java:27`、`EquipmentHealthTask.java:33`
- **现状**: `sj_job_executor` 只表示"客户端上报了执行器能力"，`sj_job` 才是任务定义（`ry-vue.sql:10009-10018` 定义 `job_status tinyint COMMENT '任务状态 0、关闭、1、开启'`）。`oeeAggregateTask` 与 `equipmentHealthTask` **只在 executor 表出现，在 job 表没有行**，所以调度中心没有可触发的任务。
- **触发路径**: 即使按 MES-EQ-01 打开 SnailJob → 控制台任务列表里找不到这两个任务 → OEE 只有人工 `POST /mes/eq/oee/aggregate`（`EqOeeController.java:129`）才有数据；健康度只有人工 `POST /mes/eq/health/evaluate`（`EqHealthController.java:80`）才有数据。
- **后果**: 设备健康度/预测性维护与 OEE 看板在无人手动操作时永远是空数据；文档中承诺的"每天 07:00 健康日评 / 每天 01:00 OEE 聚合"从未生效。历史文档亦承认此缺口：`doc/erp/plans/2026-09-02-tier2-competition-plan.md:142`（"未注册则只有手动评估"）。
- **修复建议**: 在 `script/sql/ry-vue.sql` 的 `sj_job` 段补两条 INSERT，`executor_info` 分别为 `oeeAggregateTask`（cron `0 0 1 * * ?`）与 `equipmentHealthTask`（cron `0 0 7 * * ?`），`job_status=1`、`group_name='ruoyi_group'`、`trigger_type=3`，并在 `doc/system/数据库增量变更记录.md` 登记存量库升级 SQL。
- **影响面**: `script/sql/ry-vue.sql`、存量库需增量脚本；前端无需改动；建议补一条"任务注册完整性"校验（比对 `@JobExecutor` 与 `sj_job.executor_info`）。
- **测试现状**: 无。

## 2. P1 详述

### MES-EQ-03 OEE 三项只有可用率是真的，其余为占位常量
- **证据**: `EqOeeServiceImpl.java:286`（`BigDecimal maintenance = BigDecimal.ZERO;`）、`:290`（`oee = availability.multiply(DEFAULT_PERF).multiply(DEFAULT_PERF)`）、`:310-311`、`:329-330`（写入 `performanceRate/qualityRate = DEFAULT_PERF`）、`IEqOeeService.java:31`（`DEFAULT_PLANNED_MINUTES = 480.00`）、`ry-mes.sql:309-317`（列注释自述"首版默认480=8h单班"、"表现率(首版占位1.0, 待工单报工数据补全)"、"质量率(首版占位1.0)"）
- **现状**: `planned_minutes` 对所有设备恒为 480（不读班次/日历/设备可用日历），`downtime_minutes` 只来自人工在"维修完成"时填的 `eq_repair.downtime_hours`（`EqOeeServiceImpl.java:267-278`），`maintenance_minutes` 恒 0，表现率/质量率恒 1.0。因此 `OEE ≡ 1 − 人工停机/480`。
- **触发路径**: 打开 OEE 看板 → 性能率、质量率列恒为 100%；分布图"保养占比"恒为 0；换班/两班制车间（实际 960min）可用率被系统性高估或低估。
- **后果**: OEE 数字无业务含义、不能用于对标与改善决策；与工单报工（`pro_*`）、SPC/质检良率数据完全无联动，属"占位计算"。
- **修复建议**: ①`planned_minutes` 改为按 `sch_shift`/班次日历与设备 `workcenter_id` 计算（至少提供 `eq_equipment.planned_minutes_per_day` 可配置列）；②`performance_rate` 取当日工单报工的实际产量/理论产量（需接 MES `pro` 报工数据）；③`quality_rate` 取当日良品数/总产出（接质检或 SPC 数据）；④`maintenance_minutes` 由保养记录 `execute_time` 与保养计划工时汇总。需 DDL（新增计划时长列或新表）；影响 OEE 看板 4 个接口与 `mes/eq/oee/index.vue`；建议补单测覆盖分母为 0 与两班制。
- **测试现状**: 无 eq OEE 单测。

### MES-EQ-04 备件库存与 ERP 库存是两套互不相关的账
- **证据**: `EqSparePartServiceImpl.java:194-203`（出入库直接改 `eq_spare_part.current_stock`）、`:258-261`（`selectLowStock` 只查 `current_stock < safety_stock`）、`script/sql/ry-mes.sql:477-497`（`eq_spare_part` 含自有 `current_stock`/`safety_stock`/`uk_tenant_code`）；在 `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq` 全包 grep `WmStock|wm_inventory|wm_stock|IErp|Purchase|purchase` → **命中 0**（仅 `purchaseDate` 设备采购日期字段）
- **现状**: 备件台账在 MES 侧自建库存并自建流水（`eq_spare_stock_log`），与 ERP 仓储（`wm_*`）、采购（`pu_*`）之间没有任何消息或服务调用；`lowStock` 只返回列表，不生成采购申请。
- **触发路径**: 备件入库在 MES 备件页录入（`/mes/eq/spare-part/stock`），而 ERP 采购收货走 `wm` 收货入库 → 同一批实物在两个系统各记一次；维修领用只减 MES 库存，ERP 库存不减。
- **后果**: 两套库存长期对不上，盘点必出差异；维修备件成本无法进 ERP 成本核算；安全库存预警不会转化为采购动作（预警了也没人补货）。
- **修复建议**: 明确单一账本 —— 建议备件主数据映射到 ERP 物料（新增 `eq_spare_part.material_id`），入库/领用改为发 MQ 到 ERP 仓储（复用 `QueueConstants` 现有通道模式）或直接调用 `wm` 出入库服务；至少为 `lowStock` 增加"生成采购申请"动作。需 DDL + 跨模块改造；影响 `EqSparePartServiceImpl`、备件页与 ERP 仓储。
- **测试现状**: 无跨模块测试。

### MES-EQ-05 设备可永久卡在 `MAINTENANCE`
- **证据**: `EqMaintenancePlanServiceImpl.java:230-232`（保养结果为 `ABNORMAL` 时 `updateStatus(..., STATUS_MAINTENANCE)`）、`IEqEquipmentService.java:26`（常量）、`EquipmentStatusSyncTask.java:52-54`（`SYNCABLE_STATUS` 仅 `NORMAL/OFFLINE`，不碰 MAINTENANCE）、全 eq 包 grep `STATUS_MAINTENANCE` → 只有 `EqEquipmentServiceImpl.java:126`（统计）与上述写入点，**无任何清除点**
- **现状**: 保养异常置 `MAINTENANCE` 后，没有任何流程（保养复查、点检合格、维修完工之外的路径）把它改回 `NORMAL`；`resetToNormalIfNoActiveRepair` 只在"该设备有维修单且刚完工"时才顺带清掉（`EqEquipmentMapper.java:29-34`）。
- **触发路径**: 保养计划完成时结果选"异常" → 设备状态 = 保养中 → 之后无人手工编辑设备档案 → 该设备永远显示"保养中"，且 IoT 状态同步任务永远不会覆盖它（不在 SYNCABLE_STATUS 内）。
- **后果**: 设备台账状态失真、OEE/状态汇总长期偏斜；依赖设备状态的下游（工单派工、安灯、看板）拿到错误的"保养中"。
- **修复建议**: ①保养异常应进入可闭环的流程（如生成一条待处理的保养异常任务/维修单），处理完成后置 `NORMAL`；②至少在 `submitInspection` 合格或保养记录补录合格时提供恢复路径；③`EqEquipmentController` 增加显式"结束保养"端点并用 CAS 限定 `status='MAINTENANCE'`。无需 DDL（可复用现有状态），影响 `EqMaintenancePlanServiceImpl`、`EqEquipmentServiceImpl` 与设备页。
- **测试现状**: 无。

### MES-EQ-06 维修单无作废/撤销，误报修导致设备永久 FAULT
- **证据**: `EqRepairController.java:48-139`（全部端点：list/query/add/start/complete/export/statistics/summary，**无 cancel/close**）、`EqEquipmentMapper.java:32-33`（"无活跃报修"判定为 `status IN ('REPORTED','REPAIRING')`）、`EqHealthServiceImpl.java:186`（`notIn(status, "COMPLETED", "CANCELLED")` —— 引用了从不存在的状态）、`ry-vue.sql:90627-90629`（`mes_repair_status` 字典只有 REPORTED/REPAIRING/COMPLETED）
- **现状**: 状态机只有 REPORTED→REPAIRING→COMPLETED 三态，没有"接单""验收""作废"；`insertByBo` 一落库就把设备置 FAULT（`EqRepairServiceImpl.java:130`）。
- **触发路径**: 误报修（或重复报修）后无法关闭该单 → 该设备永远存在 REPORTED/REPAIRING 单 → 即使别的维修单完工，`resetToNormalIfNoActiveRepair` 的 `NOT EXISTS` 不成立 → 设备状态永远回不到 NORMAL。
- **后果**: 设备状态长期错误、维修统计（`statusSummary` 的 active 计数）永远挂着僵尸单；健康度评分把僵尸单当"未闭环维修"扣分（`EqHealthServiceImpl.java:184-190`）。
- **修复建议**: ①`eq_repair.status` 增加 `CANCELLED`（字典同步补 `mes_repair_status` 值）并提供 `PUT /mes/eq/repair/cancel/{repairId}`，用 CAS 限定 `status IN ('REPORTED','REPAIRING')`；②作废后调用 `equipmentService.resetToNormalIfNoActiveRepair(equipmentId)`；③如确需验收环节，补 `ACCEPTED` 状态与验收端点。需 DDL（字典值即可，列宽 varchar(16) 够）；影响维修页、健康度、设备状态联动。
- **测试现状**: 无。

### MES-EQ-07 `completePlan` 读-改-写，重复完成会产生双份记录并多推进一次周期
- **证据**: `EqMaintenancePlanServiceImpl.java:189-196`（`selectById` 后判 `ACTIVE`）、`:210`（`maintenanceRecordMapper.insert(record)`）、`:222-226`（`update ... .eq(planId)` —— 更新条件**不含状态**）
- **现状**: 状态校验与写库分离，且推进 `next_date` 的 UPDATE 没有 `status = 'ACTIVE'` 条件、也没有检查影响行数。
- **触发路径**: 保养完成按钮双击 / 前端重试 / 两个终端同时点"完成" → 两个线程都通过 `ACTIVE` 校验 → 插入 2 条 `eq_maintenance_record`，`next_date` 被推进两次（等于跳过了一个保养周期）。
- **后果**: 保养执行记录重复（保养次数统计翻倍）、保养周期被拉长一倍（漏保养风险）。
- **修复建议**: 改为 CAS：先 `UPDATE eq_maintenance_plan SET next_date=?, status=? WHERE plan_id=? AND status='ACTIVE'`，`rows==0` 时抛 `ServiceException("计划状态已变更")`，再写保养记录；或把状态校验合并进同一条条件更新。无需 DDL；影响保养计划页；建议补并发单测。
- **测试现状**: 无。

### MES-EQ-08 维修备件领用明细可事后删除并回滚库存
- **证据**: `EqRepairSpareServiceImpl.java:84-86`（`batchAdd` 要求维修单必须 `REPAIRING`）、`:124-147`（`deleteByIds` 直接查明细 → 逐条 `TXN_IN` 回滚库存 → 删除，**全程不校验维修单状态，也不校验是否为本人/是否已完工**）
- **现状**: 同一份数据，写入有守卫、删除无守卫，形成旁路。
- **触发路径**: 维修单完工（COMPLETED）后，调用 `DELETE /mes/eq/repair-spare/{ids}`（权限码 `mes:eq:repair:complete`，见 `EqRepairSpareController.java:69-71`）→ 备件库存加回、领用明细消失。
- **后果**: 已完工维修的备件消耗与成本记录可被抹除，库存账实不符，且流水里只有一笔 `REPAIR_CANCEL` 入库、看不出是事后回滚；维修成本统计（`statistics`）与备件消耗对不上。
- **修复建议**: `deleteByIds` 增加守卫：查出每条明细对应的维修单，`status != 'REPAIRING'` 时抛异常（或改为"退库单"业务，不允许物理删除，用 `del_flag` 软删并留痕）。无需 DDL（软删已有 `del_flag` 模式）；影响维修页备件行操作。
- **测试现状**: 无。

### MES-EQ-09 网页端点检不落历史表，点检历史只存在于 PDA/工位入口
- **证据**: `EqEquipmentServiceImpl.java:496-512`（把 `results/overallPass` 序列化成 JSON 后 `set(EqEquipment::getLastInspection, ...)`，**只有单列覆盖式更新**）、`script/sql/ry-mes.sql:216-236`（`eq_inspection_record` 历史表存在且已建索引）、`ruoyi-modules/ruoyi-mes/.../pda/service/impl/PdaEqServiceImpl.java:101`（PDA 补写历史）、`ruoyi-modules/ruoyi-station/.../StationInspectionService.java:157`（工位补写历史）
- **现状**: `eq` 包自己的点检入口只写 `eq_equipment.last_inspection`，每次点检覆盖上一次；`EqInspectionRecordMapper` 虽在 eq 包，但只被 `pda`/`station` 模块使用。
- **触发路径**: 在 MES 网页"设备点检"页（`plus-ui/src/views/mes/eq/inspection/index.vue`）提交点检 → 只有 `last_inspection` 被覆盖 → 查不到任何点检历史；同一台设备从 PDA 点检却在 `eq_inspection_record` 有记录。
- **后果**: 点检追溯（质量体系/客户审核必需）在网页入口完全缺失；同一业务两套口径，报表与审计对不上。
- **修复建议**: `submitInspection` 内同步 `inspectionRecordMapper.insert(...)`（字段与 `PdaEqServiceImpl.java:101` 对齐），保留 `last_inspection` 作为"最近一次"快照。无需 DDL；影响设备点检页与设备详情。
- **测试现状**: `ruoyi-modules/ruoyi-station/src/test/.../StationInspectionServiceTest.java` 覆盖了工位入口，**eq 入口无测试**。

### MES-EQ-10 点检记录可任意删除，不可篡改要求不成立
- **证据**: `EqPatrolServiceImpl.java:372-375`（`deleteRecordByIds` 直接 `recordMapper.deleteByIds`，无任务状态/时间窗校验）、`EqPatrolController.java:264-266`（`DELETE /mes/eq/patrol/record/{ids}`，权限 `mes:eq:patrol:record:remove`）
- **现状**: 巡检/点检记录是质量留痕数据，但允许物理删除且无删除留痕（无操作日志表、无 `del_flag` 语义说明）。
- **触发路径**: 有 `record:remove` 权限的用户在"巡检记录"页删除任意记录（包括已完成任务下的异常记录）。
- **后果**: 点检异常记录可被消除，质量追溯链断裂；与"点检记录不可篡改"的业务要求直接冲突。
- **修复建议**: 改为软删（`del_flag`）+ 记录删除人/原因，或仅允许 `PENDING/IN_PROGRESS` 任务下的记录删除；同时把 `record:remove` 权限从常规角色收回。无需 DDL（`eq_patrol_record` 已有 `del_flag` 惯例，需确认列存在）；影响巡检记录页。
- **测试现状**: 无。

### MES-EQ-11 批量提交巡检记录只校验第一条的 taskId，守卫可绕过
- **证据**: `EqPatrolServiceImpl.java:388-397`（`Long taskId = bos.get(0).getTaskId();` 只校验首条，循环内 `buildRecord(bo)` 直接用各自的 `bo.getTaskId()`）、`:349-359`（单条入口的守卫是完整的）
- **现状**: 批量接口的"任务已结束不允许提交"守卫只作用于 `bos[0]`。
- **触发路径**: 构造 `[{taskId: 未结束任务}, {taskId: 已完成任务, ...}]` 调用 `POST /mes/eq/patrol/record/batch` → 第 2 条成功写入已结束任务。
- **后果**: 已完成/已取消的巡检任务仍能被追加记录，任务完成时间与记录时间矛盾，统计失真。
- **修复建议**: 循环内对每条 `bo.getTaskId()` 单独校验（可按 taskId 去重后批量查一次任务表），或先收集所有 taskId 再统一校验。无需 DDL；影响巡检任务执行页。
- **测试现状**: 无（09-06 基线修复的是单条 `insertRecordByBo` 守卫，批量分支未覆盖）。

### MES-EQ-12 保养超期无升级机制；SSE 通知为死代码
- **证据**: `MaintenanceDueTask.java:122-149`（`notifyOne`：Redis 24h 限频 → 查设备 → 组装 reason → 安灯，**无分级、无超期天数、无升级**）、`:152-162`（`publishMaintenanceSse` 中 `if (userIds.isEmpty()) return;`）、`:185-187`（`collectOnlineUsers()` 恒 `return Collections.emptySet()`）
- **现状**: 到期提醒只有"每 24h 一次 + 一条 EQUIPMENT 安灯"；任务 javadoc 自述"SnailJob 线程无 Sa-Token token, 此处返回空集"，即 SSE 通道实际不可用。
- **触发路径**: 计划超期 1 天、7 天、30 天，通知内容与频率完全一致（24h 一条安灯），SSE 分支永不执行。
- **后果**: 长期超期保养无法升级为更高级别告警（无差别提醒 → 被忽略）；前端若依赖 `MAINTENANCE_DUE` SSE 消息做角标/弹窗，则永远收不到。
- **修复建议**: ①在 `reason`/安灯级别中带超期天数，按阈值（如 >7 天升 P1、>30 天升级到班组长）分级；②SSE 改为按租户从 Redis 会话索引取在线用户（参考 `ProAndonServiceImpl.collectOnlineUsersByTenant` 的 `StpUtil.searchTokenValue` 用法）或直接删除该分支与注释，避免误导。无需 DDL；影响保养计划页/安灯看板。
- **测试现状**: 无。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果/建议 |
|---|---|---|---|
| MES-EQ-13 | 维修完成 BO 后端无校验：`downtimeHours`/`repairCost` 可为负、可 null | `EqRepairCompleteBo.java:17-30`（纯 `@Data`，无任何约束注解）、`EqOeeServiceImpl.java:287-290`（`run = planned - downtime`，只 `max(ZERO)` 兜底下限，**无上限钳制**）、前端有 `:min="0"`（`plus-ui/src/views/mes/eq/repair/index.vue:129`） | 直接调 API 传负停机 → `availability`/`oee` > 1（>100%）；传 null 则 OEE 少算停机。建议在 BO 加 `@DecimalMin("0")` 或服务端 `signum()<0` 抛异常，并对 `availability` 做 `min(1)` 钳制 |
| MES-EQ-14 | 备件流水 `before_qty/after_qty` 是 Java 先算的，并发下与实际库存不符 | `EqSparePartServiceImpl.java:184`（先读 `before`）、`:193/199`（Java 计算 `after`）、`:194-203`（SQL 才原子增减）、`:215-216`（写入流水） | 库存本身正确（`setSql`+`ge` 守卫），但流水账可能失真，对账时无法解释差异。建议改用 `UPDATE ... SET current_stock = current_stock ± n` 后回读实际值写流水，或流水只记增量 |
| MES-EQ-15 | 健康度同日重评是 select-then-insert/update，且 `eq_health_record` 无 (tenant,equipment,date) 唯一键 | `EqHealthServiceImpl.java:218-227`、`script/sql/ry-mes.sql:207-209`（只有 `idx_ehr_equipment/idx_ehr_date/idx_ehr_tenant`，无 UNIQUE） | 手动评估与定时任务并发 → 同设备同日多条健康记录，趋势图重复点。建议补 `UNIQUE INDEX uk_tenant_eq_date(tenant_id, equipment_id, DATE(record_date))`（或冗余 `stat_date` 列）并改为 `ON DUPLICATE KEY`/先 CAS |
| MES-EQ-16 | 健康度 LOW 安灯无去重/限频 | `EqHealthServiceImpl.java:229-239`（每台设备每次评估都可能 `callAndon`，无 Redis 限频，与保养任务的 24h 限频形成反差，`MaintenanceDueTask.java:122-128`） | 评分持续下滑时每天（或每次手动评估）重复触发同一条安灯。建议复用 `RedisUtils.setObjectIfAbsent` 做 24h 限频 |
| MES-EQ-17 | EWMA 只检测上偏异常（未取绝对值） | `EqHealthServiceImpl.java:262-265`（`zScore(...).compareTo(zThreshold) > 0`）、`ruoyi-common/ruoyi-common-iot/.../EwmaUtil.java:111`（`zScore`） | 温度/振动/转速**向下**突变（掉速、掉压、停机前兆）不告警，异常点数少算。建议用 `abs(zScore) > 阈值` 并同步在线检测口径 |
| MES-EQ-18 | `eq_equipment.uk_equipment_code`、`eq_repair.uk_repair_no` 缺 `tenant_id` 维度 | `script/sql/ry-mes.sql:113`、`:442` | 多租户下不同租户不能使用相同设备编码/维修单号（第二个租户新增即唯一键冲突）。建议改为 `uk_tenant_code(tenant_id, equipment_code)`、`uk_tenant_repair_no(tenant_id, repair_no)`（需 DDL + 存量校验） |
| MES-EQ-19 | `eq_equipment.iot_device_id` 无唯一索引 | `script/sql/ry-mes.sql:87-115`（仅 `uk_equipment_code`、`idx_status`、`idx_workcenter_id`）、`EqEquipmentServiceImpl.java:465-476`（`updateIotDeviceLink` 无查重） | 多台 MES 设备可绑定同一 IoT 设备 → 状态同步任务会把多台设备状态一起改写（`EquipmentStatusSyncTask.java:141-146`）。建议加唯一索引并在绑定时校验占用 |
| MES-EQ-20 | 设备↔IoT 关联字段双向冗余且互不同步 | `EqEquipmentServiceImpl.java:465-476`（只写 `eq_equipment.iot_device_id`）、`ruoyi-modules/ruoyi-iot/.../IotDeviceServiceImpl.java:214`（只写 `iot_device.mes_equipment_id`）、`script/sql/ry-iot.sql:38`；`ruoyi-iot` 全模块 grep `EqEquipment\|eq_equipment` 命中 0 | 在 IoT 设备页绑定与在 MES 设备页绑定结果不一致，两侧显示不同对象。建议任一侧写入时双向同步（或明确以一侧为准，另一侧改为视图/查询） |
| MES-EQ-21 | 维修完工恢复 NORMAL 时不区分来源状态 | `EqEquipmentMapper.java:29-33`（仅 `status <> 'SCRAPPED'`）、`EqEquipmentServiceImpl.java:317`（仅跳过 `NORMAL/SCRAPPED`） | 因保养异常处于 `MAINTENANCE` 的设备，只要有一张维修单完工就会被置回 `NORMAL`，保养异常标记被静默清除。建议 UPDATE 限定 `status = 'FAULT'`（或仅清 FAULT） |
| MES-EQ-22 | 报修时无条件把设备置 FAULT，覆盖 MAINTENANCE | `EqRepairServiceImpl.java:130`、`EqEquipmentServiceImpl.java:273-276`（只保护 SCRAPPED 终态） | 保养中的设备被报修后"保养中"状态丢失。建议 `updateStatus` 对 `MAINTENANCE` 增加白名单策略或报修时保留保养标记 |
| MES-EQ-23 | OEE 停机按 `repair_end` 整天归集，跨天维修不切分 | `EqOeeServiceImpl.java:267-271`（`between(repairEnd, dayRange[0], dayRange[1])`） | 22:00→次日 06:00 的 8 小时停机全部计入结束当天，前一天可用率虚高、当天虚低。建议按小时与日边界切分 |
| MES-EQ-24 | 停机时长两套口径互不校核（人工填 vs 时间戳） | `EqRepairServiceImpl.java:302-314`（MTTR 用 `repair_start→repair_end`）、`EqOeeServiceImpl.java:268-278`（OEE 用人工 `downtime_hours`） | 同一张维修单可得出两个停机数。建议 OEE 用 `repair_end-repair_start` 计算并保留人工值作为修正项（或做差异告警） |
| MES-EQ-25 | 权限码与后端校验不一致 | `script/sql/ry-vue.sql:92860`（按钮 `mes:eq:repair:spare`）、`EqRepairSpareController.java:56-71`（实际校验 `mes:eq:repair:complete`）；`ry-vue.sql:92476`（菜单 `mes:eq:inspection:list`）在 `eq` 包内无任何 `@SaCheckPermission` 使用 | 授予"维修领用备件"但不授予"维修完成"的角色看不到/用不了该功能；仅授"设备点检"菜单的角色进入页面后调 `/mes/eq/equipment/list` 会 403。建议统一为实际使用的权限码 |
| MES-EQ-26 | 后端有端点但前端 api 层无函数 | `EqPatrolController.java:70`（`GET /mes/eq/patrol/route/listAll`）、`plus-ui/src/api/mes/eq/patrol.ts:90-114`（无 `listAll` 函数） | 路线下拉数据源无法在 UI 使用。建议补 api 函数或删除死端点 |
| MES-EQ-27 | 健康等级硬编码在页面（无字典） | `plus-ui/src/views/mes/eq/health/index.vue:13-15,133-135`（HIGH/MEDIUM/LOW 中英文与颜色写死在页面）；`ry-vue.sql` 中不存在 `mes_health_grade` 字典类型 | 后端若调整分级阈值/取值，前端不同步。建议建 `mes_health_grade` 字典并改 `dict-tag` |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 维修"接单/验收"环节（报修→接单→维修中→完工→验收） | `EqRepairController.java:48-139` 无 accept/verify 端点；`EqRepairServiceImpl.java:144-188` 仅两段流转 | `eq_repair.status`、`mes_repair_status` 字典（3 值） | 中：加 2 个状态 + 2 个 CAS 端点 + 字典/菜单种子 |
| 2 | 维修单作废/撤销 | 同 MES-EQ-06 | 状态列 `varchar(16)` 够用 | 小：1 个端点 + 1 个字典值 |
| 3 | 点检项模板与设备类型匹配（必检项） | `EqEquipmentServiceImpl.java:496-512` 直接存客户端 `results`；`ry-mes.sql:216-236` 无点检项/模板表 | `eq_inspection_record`、`InspectionResultBo`（含 item/standard/actual/pass） | 大：需新表（点检模板/项）+ 按 `equipment_type` 匹配 + 必检项校验 |
| 4 | OEE 表现率/质量率真实数据源 | MES-EQ-03 | `eq_oee_record.performance_rate/quality_rate` 列已存在 | 中：接 `pro` 报工与质检/SPC 聚合 |
| 5 | 计划生产时长按班次日历取值 | `IEqOeeService.java:31`（常量 480） | `eq_oee_record.planned_minutes` | 中：接 `sch` 班次/日历或加设备级配置列 |
| 6 | 备件库存与 ERP 库存联动 / 采购建议 | MES-EQ-04 | `eq_spare_part.safety_stock`、`selectLowStock` | 大：物料映射 + MQ/服务调用 + 采购申请 |
| 7 | 备件正式退库单（当前只有"删除领用明细"旁路） | `EqRepairSpareServiceImpl.java:124-147` | `eq_spare_stock_log`（已有 `IN/OUT/REPAIR_USE`，可加 `RETURN`） | 小：加流水类型 + 退库端点 |
| 8 | 保养超期分级升级 | MES-EQ-12 | `eq_maintenance_plan.next_date`、安灯服务 | 小：阈值分级 + 安灯级别 |
| 9 | 设备健康度对未绑定 IoT 设备的评估 | `EqHealthServiceImpl.java:129-131`（仅 `iotDeviceId` 非空） | `eq_health_record` 表 | 小：无 IoT 时用维修历史/点检结果评分 |
| 10 | 保养计划变更对"已生成待办/提醒"的影响 | `EqMaintenancePlanServiceImpl.java:146-152`（编辑即重算 `next_date`，无历史、无变更留痕） | `eq_maintenance_record` | 中：计划变更审计 + 提醒状态重置 |

## 5. 已核查确认无问题（证明覆盖范围）

- 备件出入库防超卖：`setSql("current_stock = current_stock - {0}")` + `.ge(currentStock, qty)` 原子守卫，失败抛异常（`EqSparePartServiceImpl.java:200-207`）。
- 维修状态流转用 CAS（`WHERE status = 期望前态` + `rows==0` 抛异常）：`startRepair`（`EqRepairServiceImpl.java:144-153`）、`completeRepair`（`:178-188`）。
- 设备状态更新是 CAS + `SCRAPPED` 终态保护 + `afterCommit` 才发 MQ（避免回滚幻影事件）：`EqEquipmentServiceImpl.java:273-293`、`:353-366`。
- 维修完成恢复 NORMAL 用单条 `UPDATE ... NOT EXISTS(活跃报修)`，消除 TOCTOU（**复核 M3 修复：SQL 正确**，`IN ('REPORTED','REPAIRING')` 作用在子查询 `eq_repair` 上）：`EqEquipmentMapper.java:29-34`。
- 设备状态同步任务：CAS 只覆盖 `NORMAL/OFFLINE`、IoT `UNKNOWN/null` 跳过不臆断、遍历全部租户（`TenantHelper.dynamic`）：`EquipmentStatusSyncTask.java:100-147`、`:85-93`。
- 保养到期 24h 限频用 Redis `setObjectIfAbsent`（key 含 planId，TTL 24h）：`MaintenanceDueTask.java:122-128`。
- 维修单号走 Redisson 原子发号器（按日 key），非 `count+1`：`EqRepairServiceImpl.java:222-226`。
- `eq_oee_record` 有 `uk_tenant_eq_date(tenant_id, equipment_id, stat_date)`（`ry-mes.sql:326`），单线程重跑聚合幂等；`eq_repair` 有 `uk_repair_no`（`:442`）。
- 全部 9 个 eq Controller 均带 `@Validated` 且每个端点都有 `@SaCheckPermission`（`EqEquipmentController.java:35-215`、`EqRepairController.java:33-139`、`EqPatrolController.java:28-278`、`EqSparePartController.java:38-170`、`EqOeeController.java:38-129`、`EqHealthController.java:31-80`、`EqMaintenancePlanController.java:30-121`、`EqMaintenanceRecordController.java:28-108`、`EqRepairSpareController.java:30-71`）。
- 权限码在 `sys_menu` 有完整种子（58 条 `mes:eq:*`，`ry-vue.sql:92382-92862`）—— 无"无种子 → 403"问题（仅 MES-EQ-25 的个别码与实际校验不一致）。
- 前端所用 12 个字典类型全部存在于 `sys_dict_type`/`sys_dict_data`：`mes_equipment_status`（含 `OFFLINE`，`ry-vue.sql:90808`）、`mes_equipment_type`、`mes_maintenance_type`、`mes_maintenance_result`、`mes_maintenance_status`、`mes_repair_status`、`mes_fault_type`、`mes_spare_category`、`mes_spare_txn_type`、`mes_patrol_status`、`mes_patrol_result`、`mes_patrol_route_status`。
- 前端 api 路径与后端 `@RequestMapping` 一致（保养计划确为 `/mes/eq/maintenance-plan`，非 404）：`EqMaintenancePlanController.java:33` vs `plus-ui/src/api/mes/eq/maintenance.ts:44-63`。
- **复核 09-06 基线**：`EqPatrolServiceImpl.insertRecordByBo` 守卫**仍存在**（`EqPatrolServiceImpl.java:349-359`）；`EqRepairSpareServiceImpl.batchAdd` 三重校验（数量 → 维修单存在 → 维修单 REPAIRING）**仍存在**（`:77-90`）—— 原判定"已修"复核成立。
- `InspectionResultBo.results/overallPass` 有 `@NotNull`（`InspectionResultBo.java:36-41`），`submitInspection` 内 `.stream()` 无 NPE 风险。
- 保养计划默认周期映射（DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY）与 `mes_maintenance_type` 字典取值一致：`EqMaintenancePlanServiceImpl.java:252-264` vs `ry-vue.sql:90622-90626`。
- 维修统计 `statistics` 已把过滤下推到 SQL（`ge/le create_time`），非全表捞后 Java 过滤：`EqRepairServiceImpl.java:285-288`。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `iotDeviceId` ↔ `mesEquipmentId` | `eq_equipment.iot_device_id`（`ry-mes.sql:87-115`）/ `iot_device.mes_equipment_id`（`ry-iot.sql:38`） | `equipment.ts:85-94`（两个方向各有独立函数） | 双向冗余、无同步（MES-EQ-20） |
| `grade` | `EqHealthRecord.grade`（HIGH/MEDIUM/LOW 由 `EqHealthServiceImpl.java:192` 硬编码） | `health/index.vue:13-15,133-135` 硬编码中英文/颜色 | 无字典，改后端不同步（MES-EQ-27） |
| `downtimeHours` / `repairCost` | `EqRepairCompleteBo.java:20-23`（无校验注解） | `repair/index.vue:129,132`（`el-input-number :min="0"`） | 前端有下限、后端无（MES-EQ-13） |
| `/mes/eq/patrol/route/listAll` | `EqPatrolController.java:70` | `plus-ui/src/api/mes/eq/patrol.ts` 无对应函数 | 端点 UI 不可达（MES-EQ-26） |
| `mes:eq:repair:spare` / `mes:eq:inspection:list` | 后端分别用 `mes:eq:repair:complete` / 无端点使用 | `sys_menu` 已种这两个码（`ry-vue.sql:92860,92476`） | 授权码与校验码不一致（MES-EQ-25） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：MES-EQ-01（决定生产是否启用 SnailJob 客户端，或给 4 个任务加本地调度兜底）、MES-EQ-02（补 `sj_job` 两条种子 + 存量库增量脚本）。二者是"有没有数据"的前提。
- **批次 B（P1 状态机/幂等，改一个文件为主）**：MES-EQ-05（MAINTENANCE 闭环）、MES-EQ-06（维修作废 + CANCELLED）、MES-EQ-07（`completePlan` CAS）、MES-EQ-08（备件明细删除守卫）、MES-EQ-10/11（点检不可篡改 + 批量守卫）。
- **批次 C（P1 口径/链路，需跨模块）**：MES-EQ-03（OEE 真实口径）、MES-EQ-04（备件与 ERP 库存统一账本）、MES-EQ-09（点检历史落表）、MES-EQ-12（超期升级 + SSE）。
- **批次 D（P2，随批次 B/C 顺手修）**：MES-EQ-13~27，其中 MES-EQ-13/14/15/18/19 涉及校验与 DDL，建议与批次 B 同批提交。

## 8. 未覆盖范围

- 未逐一精读 `domain/bo`、`domain/vo`、`mapper` 接口的每个字段（按规范 §4.5-4 用 grep 核对，仅在疑似不一致时打开），因此**实体与表列的逐列一致性**只做了抽样核对（`eq_repair`、`eq_oee_record`、`eq_spare_part`、`eq_equipment` 索引段）。
- 未审计前端 14 个 eq 页面的布局/列宽/交互细节（本轮聚焦后端逻辑与契约；仅核对了 api 函数覆盖、字典使用、维修完成表单的 `:min`）。
- 未审计 `EqPatrolServiceImpl` 的路线/任务 CRUD 完整逻辑（只精读了记录提交/批量/删除三处）；未读 `EqMaintenanceRecordServiceImpl`、`EqOeeServiceImpl` 的 overview/trend/distribution/topDowntime 四个查询方法的完整实现（只精读 aggregate 段）。
- 未验证 `IotDeviceDataQueryService.getOnlineStatus` / `getHistoryData` 的 Redis/数据源实现细节（属 `ruoyi-common-iot`，非本轮范围），因此"健康度与 IoT 实时数据对接是否真的存在"只确认了调用链存在且参数/返回契约合理。
- 未检查 `ruoyi-modules/ruoyi-mes/src/test` 下是否有 eq 单测（除 station 模块的 `StationInspectionServiceTest` 外，各条"测试现状"按"未发现"记录）。
