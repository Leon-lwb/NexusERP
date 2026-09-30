# APS 逐日与逐班次可信产能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新排程按服务端工时口径保存逐日任务负荷，发布时同事务保存逐班次占用凭据并占用产能；AI 只能从所属排程的有效凭据重算 KPI，旧排程不回填、不当作新版发布。

**Architecture:** ERP `prd_schedule_plan.capacity_calc_version=2` 将新生成结果与旧数据隔离；两张租户隔离表分别保存 `plan/task/day/resource/hours` 与 `plan/task/calendar/day/hours`，所有 ID 来自服务端。排程引擎保留可追溯的逐日分配；发布事务锁工单与产能日历、拒绝跨方案重复发布/错误口径、先校验逐日与班次、再写占用和工单计划日期；Decision 快照/指纹读取所属行并检验每一笔工时一致，缺失/篡改失败关闭。任何 DDL 不回填存量方案，不改历史失败运行。

**Tech Stack:** Java 21 / Maven / MyBatis-Plus / dynamic-datasource / MySQL 5.7 / Python只读SQL审计。仅使用本地 127.0.0.1:3306 的 ry-erp，用户自管IDE服务，不启停服务；全部新业务编号仍由现有服务生成。用户已授权此增量直接迁移本地库，但禁止导入含 DROP 的主脚本。

---

### Task 1: 范围、结构及迁移合同

**Files:** `script/sql/ry-erp.sql`, `doc/ai/full-flow/scripts/2026-09-29-aps-capacity-provenance.sql`, `doc/system/数据库增量变更记录.md`, `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/prd/ApsProvenanceSqlContractTest.java`.

- [ ] RED: JUnit 断言新排程列 `capacity_calc_version NOT NULL DEFAULT 0`，两张新表的租户唯一索引、正数工时/资源字段与一份纯增量 SQL 存在；运行 `mvn -o -pl ruoyi-modules/ruoyi-erp -am test -Dtest=ApsProvenanceSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false -Dprofiles.active=`，应先因结构不存在而失败。
- [ ] GREEN: 主 SQL 新安装定义与一次性增量一致：`prd_schedule_task_day_load` 存日粒度来源、`prd_schedule_capacity_booking` 存班次日历占用，新增 plan 版本默认0，已有行不填2；仅在上述目标表/列加结构，不写采购、库存、审批或旧排程数据。登记 MySQL5.7 迁移顺序、单次执行护栏、备份与未应用环境限制。
- [ ] 运行定向回归并双阶段规格/质量审查；本任务不执行增量。

### Task 2: 排程引擎及生成时逐日事实

**Files:** `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/schedule/{ScheduleModel,PrdScheduleEngine}.java`, `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/domain/{PrdSchedulePlan,PrdScheduleTaskDayLoad}.java`, `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/mapper/PrdScheduleTaskDayLoadMapper.java`, `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdScheduleServiceImpl.java`, `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/prd/{schedule/PrdScheduleEngineTest,service/PrdScheduleServiceImplTest}.java`.

- [ ] RED: 1工单跨三天、中间假期只产生两个正负荷日，逐日之和等于 processHours+setupHours；无来源的旧草案版本0；相同任务不得重复写相同日期。运行定向 Maven 确认断言失败而不是编译缺类失败。
- [ ] GREEN: 调度引擎在每次扣减日剩余量时累积任务逐日 BigDecimal 工时（只保存正数）；`scheduleAndPersist` 在同一 ERP 事务写已生成方案、任务及逐日行，方案版本2；带来源幂等键的重试只读取已有行而不重复创建。工时=加工+准备，禁止从总时长均匀摊分假期。
- [ ] 回归 ERP 排程引擎、输入装配和已有计划/AI 场景测试，规格+质量审查。

### Task 3: 发布时逐班次占用、并发与可删除护栏

**Files:** `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/domain/PrdScheduleCapacityBooking.java`, `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/mapper/PrdScheduleCapacityBookingMapper.java`, `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/{PrdScheduleServiceImpl,PrdCapacityCalendarServiceImpl}.java`, `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/prd/service/{PrdScheduleServiceImplTest,PrdCapacityCalendarServiceImplTest}.java`.

- [ ] RED: 旧方案默认0拒绝发布且日历不变；已发布工单被另一方案重复发布拒绝；跨日假期不占用；同日多班次按真实 sch_shift 起止时序选且空/重复/不存在班次失败；逐日原任务负荷不足或超出日历余额失败；已占用日历不允许管理编辑/删除；并发一次发布/失败原子回滚。
- [ ] GREEN: 发布进入 `@DS("erp")` 单事务，确认同租户版本2/状态COMPLETED并CAS，按工单ID排序行锁并查重、锁同租户日历，核对 task/day/resource/hour 逐项一致，按班次真实开始时间分配每个日负荷；插入带 tenant+plan+task+calendar 唯一键的占用并调用原日历服务更新 planned/remaining，成功回写WO。只有事务整体成功才将方案PUBLISHED；异常保持原先状态和占用。已发布方案不得直接删除，已占用日历CRUD拒绝影响占用的修改/删除；不写 actualHours。
- [ ] 确认定向 tests 非零、并发和失败路径用独立数据库事务/只读读回实证，不以 mock `@Transactional` 注解代替真实原子性；双阶段审查。

### Task 4: AI 按方案归属核验与关键新鲜度

**Files:** `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/ai/adapter/DeliveryContextAssembler.java`, `ruoyi-common/ruoyi-common-ai/src/main/java/com/nexus/common/ai/decision/util/SavedScheduleCapacity.java`, 对应 `DeliveryContextAssemblerTest.java`、`SavedScheduleCapacityTest.java`、`ErpDecisionApsSimulationTest.java`、`ruoyi-modules/ruoyi-ai` Decision 测试。

- [ ] RED: 较新未发布草案不能借已发布 A 的 plannedHours；同资源别的方案账面占用也不能给当前方案作证；少一笔/重复/异租户/日期资源错配账册、日历被删改、任务量变化、旧版本和历史旧方案均返回未知/受控失败；多天不要求假期无负荷日存在产能；hash 变化导致审批前 stale。
- [ ] GREEN: 自动选择只纳入同租户可追溯的 PUBLISHED+版本2，显式旧/草案也拒绝成功基线；快照及关键指纹纳入版本、逐日行、所属账册与资源日历事实，独立重算任务总工时=逐日和=占用和，按 calendarId 查 plannedHours >= 全账册占用、校验已发布状态和租户。缺行/篡改返回null并保持 Decision 分析 fail-closed，不造模型数值。
- [ ] 完整公共AI/ERP/AI/Flow相关模块回归、规格+质量审查。

### Task 5: 本地迁移/真实运行证据与收尾

**Files:** `doc/ai/full-flow/reports/2026-09-29-local-acceptance-progress.md` 及本地 ignored `.codex/full-flow-20260928/`；不更改用户已有 `application*.yml`。

- [ ] 迁移前只读核对 localhost3306/ry-erp schema、目标列表全缺、存量方案数量/旧计划ID与状态、已有任务/日历摘要；用户已备份仍记录备份确认与目标对象快照，不导入初始化 SQL。
- [ ] 通过所有 SQL 契约及数据库迁移人工审核后，以单一 MySQL 会话执行脚本、错误立即退出；独立新会话 information_schema 读回新列默认0与表/唯一索引，现有 plan/version/status、任务、产能行不变；记录已应用标记防止重放。
- [ ] 仅用户 IDE 编译/冷重启加载新 Java 后，GET `/auth/code`，重新登录，再通过正常ERP API生成服务端新编号排程→发布→读回逐日/班次凭据→重跑受保护 AI/Flow；不发布旧 `APS202609300001`，不手改 plannedHours 或 FAILED run。验证审批/执行与其他闭环尚需完整独立事实，不能以单工单重复凑40例。
- [ ] 完整 Maven 非零测试、Python脚本、前端97+单测、vue-tsc、带内存保护的正式构建及页面巡检；报告通过/未验证项。全任务完成前不提交/推送/合并 `plus`（用户和 AGENTS.md 覆盖技能默认提交步骤）。
