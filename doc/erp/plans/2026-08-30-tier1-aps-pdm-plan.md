# 第一梯队开发计划：APS 智能排产 + 预测性维护 PdM

> 分支: `feat/aps-intelligent-scheduling` | 日期: 2026-08-30 | 状态: 实施中
> 背景: AI+工业软件赛道第一梯队开发——方向02（工艺优化与生产排程）补齐排程优化引擎；方向03（质量检测与设备运维）补齐数据驱动的设备健康评估。

---

## Part A. APS 智能排产（ruoyi-erp / prd 包，ry-erp 库）

### A1. 目标与边界

**目标**: 将 `prd_work_order`（CREATED/APPROVED/RELEASED 状态）按工艺路线（bd_routing + bd_routing_step）展开为工序级任务，在产能日历（prd_capacity_calendar）约束下用优先级规则（EDD/CR/PRIORITY）前向排产，产出排程方案 + 甘特图 + 拖期分析。

**边界（v1 明确不做）**:
- 不做约束求解器/遗传算法（启发式规则排产，预留 Strategy 扩展点）
- 不自动下发 MES 派工（ProDispatch 仍由 MES 侧人工/现有流程创建；后续可在发布时发 MQ）
- 不做小时级班次切分（天粒度产能；task 记录起止日 + 工时）

**设计决策**:
- 排程引擎为纯 Java 类 `PrdScheduleEngine`（无 Spring 依赖），输入输出全是 POJO → 可单测
- 资源键 = 工作站 workstationId（工序步骤指定）；步骤未指定工作站时退化为车间 workstation→workshop；委外工序（isOutsource=1）不占产能，只消耗等待+转移时间
- 产能来源: prd_capacity_calendar（objectType=WORKSTATION/WORKSHOP）按日 remainingHours 求和；无日历的日期回退默认 8h/日；sch_holiday 日期产能为 0

### A2. 数据模型（新增 2 表，ry-erp 库）

```sql
-- 排程方案
CREATE TABLE prd_schedule_plan (
  plan_id           BIGINT       NOT NULL COMMENT '排程方案ID',
  plan_no           VARCHAR(64)  NOT NULL COMMENT '方案编号 APS+yyyyMMdd+序号',
  plan_name         VARCHAR(128) COMMENT '方案名称',
  workshop_id       BIGINT COMMENT '车间ID(空=全部)',
  workshop_name     VARCHAR(64),
  horizon_days      INT          NOT NULL DEFAULT 14 COMMENT '排产跨度(天)',
  strategy          VARCHAR(16)  NOT NULL DEFAULT 'EDD' COMMENT 'EDD/CR/PRIORITY',
  start_date        DATE         NOT NULL COMMENT '排产起始日',
  wo_count          INT DEFAULT 0 COMMENT '参与工单数',
  task_count        INT DEFAULT 0 COMMENT '排产工序任务数',
  skipped_count     INT DEFAULT 0 COMMENT '跳过工单数(无工艺路线等)',
  total_hours       DECIMAL(12,2) DEFAULT 0 COMMENT '总负荷工时',
  delayed_count     INT DEFAULT 0 COMMENT '拖期工单数',
  makespan_date     DATE COMMENT '总完工日期',
  utilization_json  TEXT COMMENT '资源利用率快照 JSON [{resourceName,bookedHours,availableHours,rate}]',
  status            VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED/PUBLISHED',
  tenant_id         VARCHAR(20) DEFAULT '000000',
  create_dept       BIGINT, create_by BIGINT, create_time DATETIME,
  update_by BIGINT, update_time DATETIME, remark VARCHAR(500),
  del_flag          CHAR(1) DEFAULT '0',
  PRIMARY KEY (plan_id), KEY idx_tenant (tenant_id)
) COMMENT='APS排程方案';

-- 排程任务(工序级)
CREATE TABLE prd_schedule_task (
  task_id           BIGINT NOT NULL COMMENT '任务ID',
  plan_id           BIGINT NOT NULL COMMENT '方案ID',
  wo_id             BIGINT NOT NULL, wo_no VARCHAR(64) NOT NULL,
  material_code     VARCHAR(64), material_name VARCHAR(128),
  seq_num           INT COMMENT '工序序号',
  step_id           BIGINT, process_code VARCHAR(64), process_name VARCHAR(128),
  resource_type     VARCHAR(16) COMMENT 'WORKSTATION/WORKSHOP/OUTSOURCE',
  resource_id       BIGINT, resource_name VARCHAR(128),
  setup_hours       DECIMAL(10,2) DEFAULT 0,
  process_hours     DECIMAL(10,2) DEFAULT 0 COMMENT '加工工时=标准工时×剩余数量',
  start_time        DATETIME COMMENT '开始(起始日 00:00)',
  end_time          DATETIME COMMENT '结束(末日 23:59:59)',
  duration_days     INT DEFAULT 1 COMMENT '跨天数',
  delay_flag        TINYINT DEFAULT 0 COMMENT '工单拖期标记(末序任务)',
  tenant_id         VARCHAR(20) DEFAULT '000000',
  create_dept BIGINT, create_by BIGINT, create_time DATETIME,
  update_by BIGINT, update_time DATETIME,
  del_flag CHAR(1) DEFAULT '0',
  PRIMARY KEY (task_id), KEY idx_plan (plan_id), KEY idx_wo (wo_id)
) COMMENT='APS排程工序任务';
```

### A3. 引擎核心（纯 Java）

`com.nexus.erp.prd.schedule.PrdScheduleEngine`：

```
输入 ScheduleInput:
  List<WoInfo>          // woId, woNo, materialId/Code/Name, remainingQty, priority, dueDate(=planEndDate), workshopId/Name
  Function<materialId, List<StepInfo>>  // routingProvider: sequenceNum 升序; StepInfo{stepId,seq,processCode/Name,workstationId/Code/Name,standardHours,setupHours,isOutsource,waitTime,transferTime}
  Map<LocalDate, Map<Long, BigDecimal>> capacityByDate  // 日期 → (resourceId → 剩余小时)  (WORKSTATION与WORKSHOP合并视图)
  Set<LocalDate> holidays
  LocalDate startDate; int horizonDays; Strategy strategy  // EDD|CR|PRIORITY

算法:
1. WO 排序: EDD=dueDate 升序(次序 priority 升序); CR=剩余工期/剩余工时 升序; PRIORITY=priority 升序(次序 dueDate)
2. 逐 WO 逐序前向排: cursor = max(startDate, 上一序 endDay)
   内序工时 hours = setupHours + standardHours×remainingQty; 委外 hours=0、耗时=wait+transfer(天)
   产能分配: 自 cursor 起逐日取 capacity[date][resource]，不足则跨日拆分(写入剩余产能记账)
3. 末序 endDay > dueDate → 该 WO 全部任务 delayFlag=1
4. 汇总: utilization = booked/available (horizon 内被触及资源), makespan, delayedCount
```

**单元测试**（`PrdScheduleEngineTest`，5 例）: EDD 排序；同资源两工单不重叠；跨日拆分（16h 任务 8h/日占 2 天且逐日扣减产能）；拖期标记与天数；委外不占产能。

### A4. 交付物清单

| 层 | 文件 |
|---|---|
| SQL | `script/sql/update/2026-08-30-aps-schedule.sql`（2 表 + 菜单/权限 + 权威 schema 追加 ry-erp.sql） |
| 实体 | `prd/domain/PrdSchedulePlan.java`、`PrdScheduleTask.java` + bo/vo |
| Mapper | `prd/mapper/PrdSchedulePlanMapper.java`、`PrdScheduleTaskMapper.java` |
| 引擎 | `prd/schedule/PrdScheduleEngine.java`、`prd/schedule/ScheduleModel.java`(POJO+枚举) |
| 服务 | `prd/service/IPrdScheduleService.java` + `impl/PrdScheduleServiceImpl.java`（@DS("erp")：取工单/工艺路线/产能日历/假期 → 引擎 → 落库；发布=写回工单 planStartDate/planEndDate） |
| 控制器 | `prd/controller/PrdScheduleController.java`（`/erp/schedule`：page/list/{id}/run/publish/delete + gantt/{planId} 甘特聚合 VO） |
| 测试 | `PrdScheduleEngineTest`（纯算法 5 例） |
| 前端 | `plus-ui/src/api/erp/prd/schedule.ts`、`plus-ui/src/views/erp/prd/schedule/index.vue`（方案列表 + 运行对话框 + ECharts 甘特 + 利用率面板 + 拖期标红） |

---

## Part B. 预测性维护 PdM（ruoyi-iot 管道 + ruoyi-mes/eq，ry-iot / ry-mes 库）

### B1. 目标与边界

**目标**: (1) 实时侧——IoT 消息管道在阈值告警之外增加 **EWMA 统计异常检测**（无需预设阈值，自动学习设备正常波动区间）；(2) 日侧——定时任务对每台关联 IoT 的设备计算 **健康评分**，落历史并联动安灯/SSE。

**边界（v1）**: 不引入 Python/ML 依赖，纯 Java 统计方法（EWMA 控制图 + z-score），规则可解释；后续可替换为时序模型。

### B2. 实时异常检测（ruoyi-iot）

`com.nexus.iot.pipeline.IotAnomalyDetector`（与 IotAlertChecker 并列，由 IotStreamConsumer 逐条调用）:
- 状态: Redis `iot:ewma:{deviceId}:{propertyCode}` → {mean, s, count}（RedisUtils，TTL 7 天滚动）
- 每个数值点: EWMA 更新 mean/s（λ=0.3）；count≥20 且 z=|x−mean|/s > 3 → 构造 `IotAlert`(alertType=ANOMALY, level: z>4 HIGH 否则 MEDIUM) → 复用 `IotAlertNotifier`（限频+安灯+SSE 全套现成）
- 配置: `nexus.iot.pipeline.anomaly.enabled/lambda/z-warn/z-high/warmup`（`IotPipelineProperties` 增加嵌套类）

### B3. 设备健康评分（ruoyi-mes/eq）

- 新表 `eq_health_record`（ry-mes 库）: recordId, equipmentId/Code/Name, score, grade(HIGH≥80/MEDIUM≥60/LOW<60), anomalyScore, repairScore, detailJson(各属性 24h 均值/ stddev/超限次数), recordDate；tenant 审计列同 eq 系表
- `IEqHealthService` + impl（@DS("mes")）:
  - 遍历 status=正常 且 iotDeviceId 非空设备
  - `DynamicDataSourceContextHolder.push("iot")` 取该设备 24h 数值数据（复用 IotDeviceData 查询）→ 逐属性算 z-score>3 的异常点数
  - 维修扣分: 90 天内维修单数×5（封顶 30）+ 未闭环维修×10
  - score = clamp(100 − 异常点数×4 − 维修扣分, 0, 100)
  - 落 eq_health_record；LOW 且较昨日下降 → `IProAndonService.callAndon`(EQUIPMENT) + SSE `EQUIPMENT_HEALTH`
- `EquipmentHealthTask`（SnailJob `@JobExecutor(name="equipmentHealthTask")`，@DS("mes")，租户遍历仿 `MaintenanceDueTask`）

**单元测试**: `EwmaUtilTest`（EWMA 均值/方差收敛、z-score 触发阈值、warmup 期不告警）+ `EqHealthScoreTest`（扣分 clamp、等级划分）。

### B4. 交付物清单

| 层 | 文件 |
|---|---|
| SQL | `script/sql/update/2026-08-30-pdm-equipment-health.sql`（eq_health_record + 菜单 + ry-mes.sql 追加） |
| IoT | `pipeline/IotAnomalyDetector.java`、`pipeline/EwmaUtil.java`、IotPipelineProperties 扩展、IotStreamConsumer 挂钩 |
| MES | `eq/domain/EqHealthRecord.java` + bo/vo/mapper、`eq/service/IEqHealthService(+impl)`、`eq/task/EquipmentHealthTask.java`、`eq/controller/EqHealthController.java` |
| 测试 | `EwmaUtilTest`、`EqHealthScoreTest` |
| 前端 | `plus-ui/src/api/mes/eq/health.ts`、`plus-ui/src/views/mes/eq/health/index.vue`（设备健康列表 + 评分趋势折线 + 详情抽屉） |

---

## 实施顺序

1. A: DDL → 实体/Mapper → 引擎+测试 → Service/Controller → 前端 → 菜单 SQL
2. B: EWMA 工具+测试 → 检测器挂钩 → 健康服务+任务 → 控制器 → 前端 → 菜单 SQL
3. `mvn compile` 验证 + 分组提交（APS 一个 commit、PdM 一个、菜单/文档归入各自 commit）
