# ERP 排产考勤（sch）/ 视觉（cv）/ 首页（dashboard）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/{sch,cv,dashboard}`（sch 93 + cv 5 + dashboard 4 文件）+ `script/sql/ry-erp.sql`（sch_ 表 DDL）+ `script/sql/ry-vue.sql`（菜单/权限种子，仅 grep）+ `plus-ui/src/api/{erp/sch,cv}` + APS 联动点 `erp/prd/service/impl/PrdScheduleServiceImpl.java` | 覆盖文件数: 22（逐行读过 18 个 Java/DDL，另 4 个按 grep 核对） | 分支: feature/erp-mes-iot-audit-fix

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| ERP-SCH-01 | P0 | 越权/数据错 | App 打卡端点无权限码，且 `getWorkerList()` 在用户未绑定工人档案时回退"全表第一条"，任意登录用户可把考勤写到别人名下 | `AppSchAttendanceController.java:669`、`:682`、`:140` |
| ERP-SCH-02 | P0 | 状态机/数据错 | 补卡审批通过后**无条件**把 `attendance_status` 置 `normal`，抹掉当天已有的迟到/早退 | `SchAppApplyServiceImpl.java:233` |
| ERP-CV-01 | P0 | 功能不可用 | `CvFaceBatchRegisterService` 缺 `@DS("erp")`，`sch_worker` 查询落到 master(ry-vue) 库 → `/cv/face/batch-register` 必然报 Table doesn't exist | `CvFaceBatchRegisterService.java:33`、`:56` |
| ERP-SCH-03 | P1 | 校验/功能缺失 | GPS 围栏只在"前端主动调用"的 `/validate-location` 里算，打卡主流程硬编码 `locationValid=true, distance=0`，绕过该端点即可异地打卡 | `SchAttendanceServiceImpl.java:221`、`:309`、`:355` |
| ERP-SCH-04 | P1 | 功能缺失 | `SchPunchRule.allowWifiSsids/allowBluetoothDevices` 仍为死字段（**复核后仍存在**），WiFi/蓝牙打卡形同虚设 | `SchPunchRule.java:101`、`:106` |
| ERP-SCH-05 | P1 | 时间口径/数据错 | 跨天班次的下班打卡按**次日**查排班，匹配不到当班记录 → 早退/加班判定失效，考勤被拆成两天 | `SchAttendanceServiceImpl.java:248`、`SchEmployeeScheduleServiceImpl.java:225` |
| ERP-SCH-06 | P1 | 校验/数据错 | 同一人同日多班次无冲突拦截（DDL 亦无唯一键），考勤判定取 `scheduleList.get(0)`，迟到/早退算在随机一条班次上 | `SchEmployeeScheduleServiceImpl.java:201`、`SchAttendanceServiceImpl.java:515`、`ry-erp.sql:4425` |
| ERP-SCH-07 | P1 | 幂等/性能 | 人脸降级补跑无尝试计数、无排序、每轮都刷新 `face_verify_time`：无照片的 NONE 记录永久占据每轮 200 条配额 | `FaceVerifyBackfillTask.java:76`、`SchAttendanceServiceImpl.java:689` |
| ERP-CV-02 | P1 | 租户隔离 | 人脸底库 subject 固定为 `EMP-工号`，**不含 tenantId**，且无本地底库表；不同租户同工号会互相覆盖/串库 | `SchAttendanceServiceImpl.java:697`、`CvFaceBatchRegisterService.java:70` |
| ERP-CV-03 | P1 | 权限 | `/cv/face/verify`、`/cv/face/recognize`、`CvDetectController` 三个端点无 `@SaCheckPermission`，登录即可对全底库做 1:N 识别并拿到工号 | `CvFaceController.java:74`、`:89`、`CvDetectController.java:42` |
| ERP-SCH-08 | P1 | 口径错 | 月度出勤率分母只排除周六周日，既不读 `sch_holiday` 也不读排班 `isWorkingDay` → 出勤率系统性偏低 | `AppSchAttendanceController.java:750`、`:248` |
| ERP-PRD-01 | P1 | 跨模块链路 | APS 排产只消费 `sch_holiday`，完全不读班次/班组/工人排班；排班也不消费 APS 结果 → 人力与产能双向断点 | `PrdScheduleServiceImpl.java:37`、`:77`、`:232`、`:373` |
| ERP-SCH-09 | P2 | 并发 | 打卡/审批均"先查后写"，并发重复打卡撞 `uk_worker_date` 抛 500，双审批人可同时通过 | `SchAttendanceServiceImpl.java:196`、`ry-erp.sql:4414`、`SchAppApplyServiceImpl.java:165` |
| ERP-SCH-10 | P2 | 功能缺失 | `sch_attendance.schedule_id` 有列但全包 0 处写入（死列），考勤无法回溯到具体排班 | `ry-erp.sql:4386`、`grep setScheduleId`（0 命中） |
| ERP-SCH-11 | P2 | DDL/校验 | `sch_supplement_apply.apply_no`、`sch_leave_apply.leave_no` 无唯一约束，单号按"前缀+秒级时间"生成，同秒并发重号无人拦 | `SchAppApplyServiceImpl.java:118`、`:307`、`ry-erp.sql:4684`、`:4496` |
| ERP-SCH-12 | P2 | 状态机 | 排班计划 `updateByBo` 只做小写归一化，无状态机/枚举校验，任意状态字符串可落库 | `SchPlanServiceImpl.java:115`、`:126` |
| ERP-SCH-13 | P2 | 数据一致性 | 删除排班计划级联删掉 `sch_employee_schedule`，已产生考勤的排班引用直接悬空，无"已有考勤"拦截 | `SchPlanServiceImpl.java:146` |
| ERP-DASH-01 | P2 | 性能/权限 | 首页一次加载 8 条 SQL（含 `bd_client`/`bd_material`/`wm_stock_alert` 3 条全表 count），无缓存；端点无权限码，任意登录用户可见全租户销售额 | `ErpDashboardServiceImpl.java:86`、`ErpDashboardController.java:31` |
| ERP-CV-04 | P2 | 健壮性 | 批量注册逐条 catch 后静默跳过，返回结果不含失败原因，前端无法重试/对账 | `CvFaceBatchRegisterService.java:77` |
| ERP-CV-05 | P2 | 字段缺失 | 前端打卡已传 `accuracy`，后端无该列、无该字段，定位精度信息永久丢弃 | `script/sql/update/2026-09-11-sch-attendance-punch-location.sql:2`、`ry-erp.sql:4407` |

## 1. P0 详述

### ERP-SCH-01 App 打卡端点无鉴权 + 工人身份回退"全表第一条"
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/sch/controller/app/AppSchAttendanceController.java:669`（getWorkerList）、`:682`（`workerMapper.selectList(Wrappers.lambdaQuery())`）、`:140`（`clockIn`，无 `@SaCheckPermission`）、`:246`（`clockOut`，无权限码）
- **现状**: `getWorkerList()` 先按 `sch_worker.user_id = 当前登录 userId` 查；查不到时**不报错**，直接返回本租户全部工人，调用方一律 `workerList.get(0)`。打卡/今日状态/今日排班/最近打卡/月度统计共 8 处端点（L76/122/142/191/227/355/423/644）都走这条回退。注释自认"原实现固定取全表第一条——任何登录用户的打卡/统计都落到同一工人"，但**回退分支仍保留了旧行为**。
- **触发路径**: 任一普通登录账号（尚未绑定 `sch_worker.user_id` 的新员工、或只有报表/销售角色的账号）打开 App → 点"上班打卡" → 记录写到该租户工人列表第一条的 `worker_id` 上。
- **后果**: 越权写他人考勤；`worker_name` 仍是受害者姓名，代打卡无法追溯；多人并发触发回退时同一工人当天记录被反复覆盖（`performClockIn` L204-214 是覆盖式更新）。
- **修复建议**: 删除 `getWorkerList()` 的全表回退分支，未绑定即抛 `ServiceException("当前用户未关联工人档案")`（`requireCurrentWorker()` L688-694 已是该语义，统一改调它）；写接口补 `@SaCheckPermission("sch:attendance:add")` 或统一走 App 专用鉴权。**无需 DDL**。影响 `AppSchAttendanceController` 全部端点 + App 打卡页。
- **测试现状**: 本轮未发现针对 `AppSchAttendanceController` 的单测。

### ERP-SCH-02 补卡回写覆盖已有考勤状态（抹掉迟到/早退）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/sch/service/impl/SchAppApplyServiceImpl.java:233`（`update.setAttendanceStatus("normal")`）、`:225-236`
- **现状**: `writeBackSupplement()` 在"记录已存在"分支只对**缺卡的一侧**补时间（L227-232 有 null 判断），但状态行是无条件执行的，同时把 `sourceType` 改为 `manual`、`remark` 覆盖为"补卡通过"。
- **触发路径**: 工人排班 9:00，实际 9:30 打卡（判 `late`，`late_minutes=30`），下午提交"补下班卡" → 审批通过 → 该条 `attendance_status` 变 `normal`，而 `late_minutes` 仍是 30。
- **后果**: 迟到记录被补卡抹平；`attendance_status` 与 `late_minutes` 自相矛盾，月度考勤/绩效口径失真且无法自洽对账。
- **修复建议**: 仅当本次确实补上了原本为 null 的打卡时间（`fixClockIn && attendance.getClockInTime() == null`，或对称的 clock_out）时才置 `normal`；已有打卡时间时保留原状态，只补缺卡侧 + 追加 remark。若业务确定"补卡即正常"，须同时清零 `late_minutes/leave_early_minutes`。**无需 DDL**。
- **测试现状**: 未见单测（建议补 `late + 补下班卡 → 仍为 late`）。

### ERP-CV-01 `CvFaceBatchRegisterService` 缺 `@DS("erp")` → 批量注册端点必然失败
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/cv/service/CvFaceBatchRegisterService.java:33`（`@Service` 上无 `@DS`）、`:56-60`（`workerMapper.selectOne(... SchWorker::getUserId ...)`）；对照同包 `CvOpsService.java:34`、`CvLabelParseService.java:35` 均标了 `@DS("erp")`；数据源主库 `ruoyi-admin/src/main/resources/application-prod.yml:50`（`primary: master`）、`application-dev.yml:55`（同）
- **现状**: 该类同时访问 master 库表（`sys_user`、`sys_oss`）与 erp 库表（`sch_worker`），却没有 `@DS`，全部走默认 master。`sch_worker` 在 `ry-erp` 库，查询直接抛 `Table 'ry-vue.sch_worker' doesn't exist`；`CvFaceController.batchRegister`（`:45`）只 catch `VisionException`，SQL 异常冒泡成 500。
- **触发路径**: 前端 `plus-ui/src/api/cv/face.ts:155`（`/cv/face/batch-register`）→ 管理端"批量注册"按钮 → 500，一条都注册不进去。
- **后果**: 批量注册功能完全不可用（`cv:face:register` 权限、前端入口、`CvFaceController` 端点都已具备，只差数据源注解），上线集中采集底库时无法使用。
- **修复建议**: 类级加 `@DS("erp")`，对 `sysUserMapper`/`sysOssService` 调用点用 `DynamicDataSourceContextHolder.push("master")`（与 `SchAttendanceServiceImpl.java:652` 的既有做法一致），或把工人查询下沉到 `SchWorkerService`（已标 `@DS("erp")`）。**无需 DDL**。
- **测试现状**: 未见单测。

## 2. P1 详述

### ERP-SCH-03 GPS 围栏不参与打卡判定（可绕过）
- **证据**: `SchAttendanceServiceImpl.java:221`、`:309`、`:355`（三处硬编码 `locationValid=true`、`distance=0`）；围栏实现 `:437-492`；唯一调用点 `AppSchAttendanceController.java:395`（`POST /app/sch/attendance/validate-location`）
- **现状**: `validatePunchLocation()` 会按 `sch_punch_rule.location_lat/location_lng/location_radius` 做 Haversine 距离校验，但**只有前端主动调用该端点时才执行**。真正落库的 `performClockIn/performClockOut` 既不调用它，也不读打卡规则。
- **触发路径**: 直接 `POST /app/sch/attendance/clock-in`（带任意经纬度或不带）即可在厂区外打卡，返回体仍声称 `locationValid: true`。
- **后果**: 09-11 引入的经纬度留痕（`ry-erp.sql:4407-4412`）只做事后查看，**不能阻止**异地打卡；返回的 `locationValid/distance` 是假数据，管理端若展示会误导。
- **修复建议**: 在 `performClockIn/performClockOut` 内调用 `validatePunchLocation()`，超半径时按规则拒绝或落库并置异常状态 + `locationValid=false`；返回体改为真实计算结果。**无需 DDL**。

### ERP-SCH-04 打卡规则 WiFi/蓝牙白名单仍为死字段（复核历史结论）
- **证据**: `SchPunchRule.java:101`、`:106`；DDL `script/sql/ry-erp.sql:4619-4620`；前端表单 `plus-ui/src/views/erp/sch/punch-rule/index.vue:293-294`
- **现状**: 全仓 grep `allowWifiSsids|allowBluetoothDevices` 仅命中实体/BO/VO/前端表单与 DDL 注释，**没有任何 Service 读取或校验**。
- **后果**: 管理员配置"仅厂区 WiFi 可打卡"后实际任何网络都能打卡，配置项给管理者"已启用网络校验"的错觉。**复核结论：09-06 报告 §2 第 8 项仍存在**。
- **修复建议**: 二选一——(a) 打卡时接收客户端上报的 ssid/设备名并按 JSON 白名单校验（客户端可伪造，仅弱校验）；(b) 明确不做则从 BO/VO、前端表单与 DDL 移除，避免误导（选 (b) **需要 DDL**，存量库需增量脚本）。

### ERP-SCH-05 跨天班次的下班打卡按次日匹配排班（口径错）
- **证据**: `SchAttendanceServiceImpl.java:248-254`（`performClockOut` 用 `LocalDate.now()` 作为 `record_date` 查当天记录）、`:270-273`、`:330-333`（排班查询按 `scheduledDate = today`）；生成端 `SchEmployeeScheduleServiceImpl.java:225-229`（`isCrossDay=1` 时 `endTime` 加 1 天，`scheduledDate` 仍是排班起始日）
- **现状**: 生成排班时跨天班次的结束时间被推到次日，但**排班行的 `scheduled_date` 仍留在起始日**；下班打卡用 `LocalDate.now()`（次日凌晨）去查排班，`scheduleList` 为空 → `determineAttendanceStatus` 直接返回 `normal`（L511-513），`leaveEarlyMinutes` 恒为 0。
- **触发路径**: 夜班 22:00–06:00，工人 02:00 下班打卡 → 查的是次日排班（不存在）→ 无早退判定；且当天 22:00 的上班打卡已建了"起始日"的考勤行，02:00 的下班打卡因 `record_date=次日` 查不到该行，**新建一条次日考勤行**（L316-351），一个班次被拆成两天两条记录。
- **后果**: 跨天班次的早退/加班/工时全部失真；月度出勤天数虚高（两天都算出勤）；夜班考勤无法与排班对账。
- **修复建议**: 打卡时先按"当前时间是否落在某条排班的 [startTime, endTime] 区间"反查排班（含前一日跨天班），用该排班的 `scheduled_date` 作为 `record_date`；`performClockOut` 的排班查询同时查 `scheduledDate = today` 与 `scheduledDate = today-1 且 isCrossDay`。**无需 DDL**。

### ERP-SCH-06 同一人同日多班次无冲突拦截
- **证据**: `SchEmployeeScheduleServiceImpl.java:201-236`（`(teamSchedule, member)` 双层循环无条件 `out.add`）、`:146-148`（只按 `planId` 删旧明细）；消费端 `SchAttendanceServiceImpl.java:515-517`（`scheduleList.get(0)`）；DDL `script/sql/ry-erp.sql:4425`（`sch_employee_schedule` 无唯一键）
- **现状**: `generate()` 按 `planId` 删旧行后批量插入，生成过程不检查该 worker 当天是否已有排班（跨计划、跨班组、同班组同日期两条模板都会叠加），DB 也没有唯一键兜底。
- **触发路径**: 工人同属 A、B 两班组，或同一班组同一日期配了早班+晚班两条 `sch_team_schedule` → 生成 2 条同日排班 → 打卡后 `determineAttendanceStatus` 取到哪条不确定（无 order by）。
- **后果**: 迟到/早退/加班判定随机化，考勤争议无法解释；`generate` 重复执行还会让考勤引用的旧排班行被删除。
- **修复建议**: 生成时按 `(workerId, scheduledDate)` 去重并返回冲突清单；`sch_employee_schedule` 增加 `UNIQUE(tenant_id, worker_id, scheduled_date)`（**需要 DDL**，存量库先清洗重复行）。若业务允许多班次，则判定逻辑改为取"与打卡时间最接近的一条"而非 `get(0)`。

### ERP-SCH-07 人脸降级补跑会"饿死"真实待补跑记录
- **证据**: `FaceVerifyBackfillTask.java:76-88`（`face_verify_time >= now-24h` + `LIMIT 200`，无 `ORDER BY`、无尝试计数）、`SchAttendanceServiceImpl.java:689`（`verifyTime = new Date()`）、`:710-718`（每轮无条件回写刷新时间）、`:691-692`（无照片只 log 后继续，状态仍 NONE）
- **现状**: 补跑把选中记录交给 `asyncFaceVerify` → `verifyAndWriteBack` → **无条件**把 `face_verify_time` 刷新为当前时间。无存证照片/取图失败的记录仍是 `NONE`，但时间被刷新 → 永远落在 24h 窗口内；查询无排序，MySQL 每次可能返回同一批。
- **触发路径**: vision 宕机期间大量打卡落 NONE，其中一批无照片 → 服务恢复后每小时补跑，这 200 条每轮被选中并跳过，新产生的真实待补跑记录排不进来。
- **后果**: 降级安全闭环实际不闭环——人脸校验长期停留 NONE，`FAIL 复查` 页（`plus-ui/src/views/cv/face-review.vue`）看不到数据，等于静默关闭校验。
- **修复建议**: 增加 `face_verify_attempts`/`last_attempt_time` 列（或复用 remark），查询加 `ORDER BY face_verify_time ASC` + 尝试次数上限；补跑场景下若状态仍为 NONE 则不刷新 `face_verify_time`；多轮循环直到选空。**需要 DDL**（建议加列）。

### ERP-CV-02 人脸底库 subject 不含租户 → 跨租户串库
- **证据**: `SchAttendanceServiceImpl.java:697`（`String subject = "EMP-" + worker.getEmployeeId()`）、`CvFaceBatchRegisterService.java:70`（同）、`CvFaceController.java:59`（`subject` 由前端直接传入）；`script/sql/ry-erp.sql` 中无任何 `cv_*` 本地底库表（grep `CREATE TABLE \`cv` 零命中）
- **现状**: 人脸底库完全托管在外部 vision 服务（单一全局底库），subject 命名规则是 `EMP-<employee_id>`，**不带 tenantId**；而 `sch_worker` 的唯一键是 `uk_tenant_employee_id(tenant_id, employee_id)`（`ry-erp.sql:4828`），即**同工号在不同租户合法共存**。
- **触发路径**: 租户 A 与租户 B 都存在工号 1001 → 后注册方覆盖前者的底库样本（`registerFace` 注释"重复注册=追加样本取均值"）；1:1 验证时 A 租户员工可能匹配到 B 租户样本；`CvFaceController.delete`/`re-register` 按 subject 删除也没有租户维度校验。
- **后果**: 跨租户人脸数据串库/覆盖，属于租户隔离与隐私缺陷；且删除操作可跨租户生效。
- **修复建议**: subject 改为含租户前缀（如 `T{tenantId}-EMP-{employeeId}`），并在 `CvFaceController` 的 register/delete/re-register/verify 统一由后端按当前租户拼装 subject（禁止前端直接传裸 subject）。**需要迁移已注册底库**（一次性重注册脚本）。

### ERP-CV-03 底库识别与 OCR 端点无权限码（登录即可用）
- **证据**: `CvFaceController.java:74`（`/cv/face/verify`）、`:89`（`/cv/face/recognize`）、`:164`（`/cv/face/health`）均无 `@SaCheckPermission`；`CvDetectController.java:42`、`:68`、`:84` 三个端点同样无权限码（该文件 grep `@SaCheckPermission` 零命中）；`cv:detect:*` 在 `sys_menu` 无种子（ry-vue.sql 计数 0）
- **现状**: 同控制器的 register/list/remove/re-register 都有 `cv:face:register|list|remove` 权限码，但**最敏感的 1:1/1:N 识别没有**。前端 `plus-ui/src/api/cv/face.ts:92`、`:105` 直接调这两个端点。
- **触发路径**: 任意登录账号（含最低权限角色）构造 `POST /cv/face/recognize` + 一张人脸照 → 返回 top-N 候选 `subject`（即 `EMP-工号`），可批量探测员工是否在册、并做"照片找人"。
- **后果**: 越权使用生物特征底库、泄露员工工号；OCR 端点亦可被任意账号刷取视觉服务额度。
- **修复建议**: `verify`/`recognize` 补 `@SaCheckPermission("cv:face:list")`（或新增 `cv:face:verify`/`cv:face:recognize` 并在 `ry-vue.sql` 补 `sys_menu` 按钮种子，**需要菜单 DDL/种子**）；`CvDetectController` 三个端点补 `cv:detect:*` 权限码 + 菜单种子。

### ERP-SCH-08 月度出勤率分母只排周末（忽略节假日与排班）
- **证据**: `AppSchAttendanceController.java:750-767`（`calculateWorkDays` 仅 `dayOfWeek != SATURDAY && != SUNDAY`）、`:248`（`int workDays = calculateWorkDays(startDate, endDate)`）、`:314-319`（`attendanceRate` 由该分母算出）
- **现状**: 应出勤天数只按自然周排周末，既不读 `sch_holiday`（法定节假日/调休，表存在且 APS 已在用 `PrdScheduleServiceImpl.java:373`），也不读 `sch_employee_schedule.isWorkingDay`（排班休息日）。
- **触发路径**: 10 月（含 7 天法定假期）员工按排班出勤 18 天 → 分母仍为 23 个工作日 → 出勤率显示约 78% 而非 100%。
- **后果**: 出勤率系统性偏低，App"我的数据"与月度统计失真；加班/缺勤判定随之失准。
- **修复建议**: `calculateWorkDays` 改为"该区间内 `sch_employee_schedule.isWorkingDay=1` 的天数"，或"自然工作日 − `sch_holiday` 中非工作日"。**无需 DDL**。

### ERP-PRD-01 APS 与排班/人力双向断点
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdScheduleServiceImpl.java:37-38`（仅 import `SchHoliday`/`SchHolidayMapper`）、`:77`（唯一 sch 依赖）、`:232`（`loadHolidays`）、`:373-382`（只查 `sch_holiday`）；`:351-367`（产能只来自 `PrdCapacityCalendar`）
- **现状**: APS 排产的输入 = 工单 + 工艺路线 + 工作站产能日历 + 假期，**完全不读** `sch_shift`/`sch_team`/`sch_team_member`/`sch_employee_schedule`；反向，`sch` 包（`SchPlanServiceImpl` 等）的 import 中没有任何 `prd`/APS 引用，排班不消费排产结果。
- **触发路径**: 某日班组排班为休息日（或全员请假日），APS 仍按工作站产能日历把工序排到该日 → 排产计划不可执行，需人工回改。
- **后果**: "排产→派工→考勤"链路在人力维度断裂：排产结果与实际可用人力不一致，产能承诺失真；排班也不受排产驱动，两边各自维护。
- **修复建议**: `runSchedule` 增加"可用人力"约束输入（按 `sch_employee_schedule.isWorkingDay=1` 且 `scheduleStatus != rest` 汇总到车间/工作站），或在 `PrdScheduleEngine` 里对休息日按可出勤人数折算产能；反向至少提供"排产计划发布 → 生成班组排班草稿"的联动入口。**无需 DDL**（若需缓存人力快照再评估）。

## 3. P2 详述

| 编号 | 标题 | 证据 | 后果 | 建议 |
|---|---|---|---|---|
| ERP-SCH-09 | 打卡/审批"先查后写" | `SchAttendanceServiceImpl.java:196-216`、`ry-erp.sql:4414`（`uk_worker_date`）、`SchAppApplyServiceImpl.java:161-174` | 并发连点打卡撞唯一键 → 500（而非"今日已打卡"）；两个审批人可同时通过同一补卡并各自回写 | 打卡改 `insert ... on duplicate key update` 或按 `workerId+date` 加锁；审批改条件更新 `WHERE apply_status='pending'`，影响 0 行抛异常。无 DDL |
| ERP-SCH-10 | `schedule_id` 死列 | `ry-erp.sql:4386`；`grep setScheduleId` 在 `erp/sch` 零命中 | 考勤记录无法回溯到具体排班行，多班次场景无法定位"这条考勤对应哪个班" | 打卡时写入命中的 `schedule_id`，或删除该列。无 DDL（若删列需 DDL） |
| ERP-SCH-11 | 申请单号无唯一约束 | `SchAppApplyServiceImpl.java:118`（`"BC"+dateTimeNow()`）、`:307`（`"QJ"+...`）；`ry-erp.sql:4684`、`:4496`（无 UNIQUE） | 同秒内两人提交生成相同单号，对账/追溯混淆 | 补 `UNIQUE(tenant_id, apply_no)` / `leave_no`，或改用 `AutoCodeUtil` 序号。需要 DDL |
| ERP-SCH-12 | 计划状态无校验 | `SchPlanServiceImpl.java:115-119`、`:126-133` | `updateByBo` 可把 `status` 写成任意字符串（仅小写化），前端字典标签失配、`generate()` 的 `published` 过滤失效 | 用枚举校验 + 状态机（draft→published→closed），非法值抛异常。无 DDL |
| ERP-SCH-13 | 删计划级联删排班明细 | `SchPlanServiceImpl.java:143-150` | 已产生考勤的排班行被删（考勤引用悬空），历史考勤无法复现当时班次 | 删除前检查 `sch_employee_schedule` 是否已产生考勤，有则拒绝或改为软删。无 DDL |
| ERP-DASH-01 | 首页 8 条 SQL + 无权限码 | `ErpDashboardServiceImpl.java:66`、`:76`、`:86-89`、`:94`、`:115`、`:140`；`ErpDashboardController.java:31`（无 `@SaCheckPermission`） | 每次首页加载 3 条全表 count（客户/物料/预警）+ 2 条聚合，大表下首页变慢；任意登录用户可见全租户本月销售额 | 给 count 加 `@Cacheable`（短 TTL）或改为定时快照；销售额聚合加数据权限/权限码。无 DDL |
| ERP-CV-04 | 批量注册静默跳过 | `CvFaceBatchRegisterService.java:77-79`（catch 后仅 log）、`:61-68`（跳过也不进结果） | 返回列表只含成功项，管理员不知道哪些人没注册进去，无法重试 | 结果对象增加 `failed[]`（subject+原因），或返回 `{success, failed}` 摘要。无 DDL |
| ERP-CV-05 | `accuracy` 字段被丢弃 | `script/sql/update/2026-09-11-sch-attendance-punch-location.sql:2`（"前端已传 latitude/longitude/address/accuracy，后端此前接收后直接丢弃"）；`ry-erp.sql:4407-4412`（无 accuracy 列） | 定位精度无法评估，无法区分"高精度室内打卡"与"基站级粗略定位"，弱化 GPS 证据链 | 增加 `clock_in_accuracy`/`clock_out_accuracy` 列并在打卡接口落库。需要 DDL |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | WiFi/蓝牙打卡校验 | `SchPunchRule.java:101`、`:106` 无任何读取方 | 字段 + BO/VO + 前端表单项齐备 | 0.5~1 人日（或反向清理字段） |
| 2 | 打卡位置围栏拦截 | `SchAttendanceServiceImpl.java:221` 硬编码 `locationValid=true` | `validatePunchLocation()` 完整实现 + `sch_punch_rule` 三字段 | 0.5 人日（主流程接线） |
| 3 | 考勤↔MES 工时/绩效联动 | `ruoyi-mes` 全模块 grep `SchAttendance\|sch_attendance\|lateMinutes\|overtimeHours` **零命中** | `sch_attendance.overtime_hours/late_minutes/attendance_status` 已落库 | 3~5 人日（需明确工时口径与绩效规则） |
| 4 | APS↔排班人力联动 | `PrdScheduleServiceImpl.java:37-38` 只依赖 `SchHoliday`；`sch` 包无 `prd` 引用 | APS 引擎、产能日历、排班/班组/班次全套表 | 3~5 人日 |
| 5 | 考勤异常自动告警（缺卡/旷工/连续迟到） | `sch_attendance.attendance_status` 含 `absent`，但全包无写入 `absent` 的代码（仅 `normal/late/early_leave/late_early_leave/leave`） | 状态枚举 + 考勤表 + SnailJob 调度框架 | 2~3 人日（需 DDL 仅当加告警表） |
| 6 | 生产调度（APS）状态机回写 MES 派工 | 见 §8 未覆盖说明；本轮未审计 `PrdScheduleServiceImpl` 的派工回写路径 | `PrdSchedulePlan/PrdScheduleTask` + MES 派工 | 需专项审计后再估 |

## 5. 已核查确认无问题（证明覆盖范围）

- **dashboard 数据源已正确标注**：`ErpDashboardServiceImpl.java:40` 有类级 `@DS("erp")`，注释亦说明不标会落到 master —— 历史"漏标 `@DS` 查错库"的 bug **已修复**（复核结论：已修复）。
- **dashboard NULL 三值逻辑已处理**：月度汇总与 12 个月趋势都对 `order_status` 用了 `ne(CANCELLED).or().isNull()`（`ErpDashboardServiceImpl.java:69-71`、`:144-145`），不会漏算 NULL 状态单据；已取消单据被排除。
- **人脸降级不阻断主流程**：`SchAttendanceServiceImpl.asyncFaceVerify`（`:643-680`）外层 try/catch 全兜底，`verifyAndWriteBack`（`:686-724`）对 `VisionException` 与一般异常都只置 NONE + 日志，不会把异常抛回打卡请求。
- **异步线程上下文已手动恢复**：`TenantHelper.dynamic(tenantId, ...)`（`:647`）+ 显式 `DynamicDataSourceContextHolder.push("master")`（存证）/`push("erp")`（回写）（`:652`、`:669`）——异步租户落 0 与跨库查表问题已处理。
- **multipart 上传字段名正确**：`CvFaceController.java:58`、`:75`、`:90`、`:146` 全部为 `@RequestParam("file")`，未被改名，**不存在 422 风险**。
- **前端无"后端有端点但 UI 不可达"**：`/erp/sch/attendance/export` 由 `plus-ui/src/views/erp/sch/attendance/index.vue:249`（`exportUrl`）调用；`/erp/sch/attendance/face-photo/{ossId}` 由 `plus-ui/src/views/cv/face-review.vue:214` 调用；`mark-pass/mark-proxy` 由 `plus-ui/src/api/erp/sch/attendance/index.ts:50`、`:59` 调用。
- **sch/cv 权限码均有 `sys_menu` 种子**：逐码计数（`sch:worker/team/shift/plan/planteam/teamSchedule/employeeSchedule/attendance/punchRule/holiday` 的 list/add/edit/remove/export/query、`sch:attendance:review`、`sch:appApply:approve`、`cv:face:list/register/remove`）各命中 1 条，见 `ry-vue.sql:92246-92252`、`:92680`、`:92793`、`:92795`、`:96801`、`:96804`、`:92789-92792`。**唯一无种子的是 `cv:detect:*`，但控制器也没用权限码（见 ERP-CV-03），故不构成 403。**
- **补卡/外勤/请假的规则校验已实现**：`SchAppApplyServiceImpl.java:87-103`（`allow_supplement`/`supplement_deadline`/不可晚于今天）、`:245-255`（`allow_outdoor_punch`/`outdoor_require_photo`）、`:298-304`（结束早于开始、单次≤365 天）、`:108-115`（同日重复申请拦截）。
- **排班计划编码与字典值已加固**：`SchPlanServiceImpl.java:94-102`（空码自动生成 + 唯一性预检）、`:126-133`（status/periodType 统一小写入库），避免撞 `uk_tenant_plan_code` 裸抛 500 与前端字典失配。
- **排班生成已按模板展开且处理跨天**：`SchEmployeeScheduleServiceImpl.java:185-239`（班组→班次绑定、休息日 `isWorkingDay=0`、跨天 `endTime` +1 天）、`:137-148`（只清本计划的旧明细，不再全表清空）——生成端本身正确，问题在消费端（ERP-SCH-05/06）。
- **`SchAttendanceController` 三处复查端点权限一致**：`sch:attendance:review`（`:134`、`:149`、`:163`），与 `sys_menu` 6187 种子匹配。
- **补卡/外勤照片与头像走 OSS 且失败不阻断**：`SchAppApplyServiceImpl.uploadPhoto`、`CvFaceBatchRegisterService.loadAvatar`（`:90-104`）读取失败返回 null 并记日志，不会抛异常中断批处理。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `allowWifiSsids` | `SchPunchRule.java:101`、`SchPunchRuleBo.java:100`、`SchPunchRuleVo.java:116`、DDL `ry-erp.sql:4619` | `plus-ui/src/api/erp/sch/punch-rule/types.ts:22`、`views/erp/sch/punch-rule/index.vue:293` | 前端可填、后端存库但**无任何校验逻辑**（ERP-SCH-04） |
| `allowBluetoothDevices` | `SchPunchRule.java:106`、`SchPunchRuleBo.java:105`、`SchPunchRuleVo.java:122`、DDL `ry-erp.sql:4620` | `types.ts:23`、`index.vue:294` | 同上 |
| `schedule_id` | 表列 `ry-erp.sql:4386`，实体 `SchAttendance` 有字段 | 前端列表未展示 | 后端**从不写入**（`setScheduleId` 全包 0 命中）→ 恒 NULL，死列（ERP-SCH-10） |
| `accuracy` | **后端无字段、DDL 无列** | App 打卡已上报（`2026-09-11-...punch-location.sql:2` 注释确认） | 前端传、后端丢弃 → 定位精度信息丢失（ERP-CV-05） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交，1~2 天）**
  - ERP-SCH-01：`getWorkerList()` 去掉全表回退 + 打卡写接口补权限码（1 个文件，无 DDL）。
  - ERP-SCH-02：补卡回写改为"仅补 null 侧才置 normal"（1 个方法，无 DDL）。
  - ERP-CV-01：`CvFaceBatchRegisterService` 补 `@DS("erp")` + 存证/头像走 master 显式切换（1 个文件，无 DDL）。
- **批次 B（P1，3~5 天）**
  - ERP-SCH-03（围栏接线）、ERP-SCH-04（字段清理或落地）、ERP-SCH-05（跨天班次匹配）、ERP-SCH-08（出勤率分母）、ERP-CV-03（识别端点补权限码 + 菜单种子）。
- **批次 C（P1，含 DDL，3~5 天）**
  - ERP-SCH-06（`sch_employee_schedule` 唯一键 + 生成去重）、ERP-SCH-07（补跑尝试计数/排序，加列）。
- **批次 D（P1/P2 专项，5 天+）**
  - ERP-CV-02（subject 加租户前缀 + 底库重注册迁移）、ERP-PRD-01（APS 人力约束）、ERP-SCH-09~13、ERP-DASH-01、ERP-CV-04/05。
- **跨批次通用**：以上除标注外均需补单测（打卡并发、跨天班次、补卡状态保持、批注册数据源）。

## 8. 未覆盖范围

- **未逐行读**：`SchTeamServiceImpl`、`SchShiftServiceImpl`、`SchWorkerServiceImpl`、`SchTeamScheduleServiceImpl`、`SchHolidayServiceImpl`、`SchPlanTeamServiceImpl`、`SchTeamMemberServiceImpl`、`SchPunchRuleServiceImpl`（仅确认 `@DS("erp")` 与权限码）；`CvOpsService`、`CvDetectController`（仅 grep 注解/映射）；`CvFaceController.java:166-178`；`ErpDashboardServiceImpl.java:151-205`（12 个月趋势补零逻辑）。
- **未读**：`AppSchAttendanceController.java:150-340`（clock-in/clock-out 请求体解析细节，仅读了 L141-150 确认服务端取 `new Date()`，**客户端无法篡改打卡时间**这一点成立）。
- **前端**：`plus-ui/src/views/erp/sch/**`、`views/cv/**` 未做系统性布局/字段审计（仅按需 grep 校验可达性与字段名）；本报告的"前端字段"结论仅限 api/types 层与点名文件。
- **APS**：`PrdScheduleEngine`、`PrdScheduleServiceImpl` 的派工/状态机/回写 MES 部分未审（仅核了它与 sch 的依赖面），调度单状态机与并发改单需另开专项。
- **`script/sql/ry-vue.sql`（39MB）**：仅按权限码字符串精确 grep + 局部确认行号，未通读。
- **未核实**：`FaceVerifyBackfillTask` 在生产的实际调度可用性（`@EnableScheduling` 由 `SnailJobConfig`（`@ConditionalOnProperty(snail-job.enabled=true)`，`application-prod.yml:26` 为 `false`）与 `IotConfiguration`（`ruoyi-iot` 为 `ruoyi-admin` 依赖，`ruoyi-admin/pom.xml:117`，且 `NexusApplication` 为 `@SpringBootApplication` 默认扫描 `com.nexus`）两处提供；本报告按"该任务会执行"评估其风险，未验证运行环境实际是否启用调度。
