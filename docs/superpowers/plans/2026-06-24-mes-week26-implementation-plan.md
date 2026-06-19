# MES Phase 3 Week 26 开发实施计划 — 设备管理模块（台账 + 保养计划 + 维修记录，设备状态自动联动）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。

> **实施日期**: 2026-06-24
> **阶段**: MES Phase 3 — 设备管理（对应主文档 Week 30 任务）
> **状态**: 待 Claude 自审 → 待用户审查

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

Phase 3 质量管理（Week 25）已完成。按主计划《NexusERP_MES文档.md》§10.3 / §15.3，Phase 3 下一块为 Week 30 设备管理（3 人天）。实际进度领先主计划约 4 周，故 **Week 26 = 设备管理模块**。对应主文档 §2.3（eq_equipment/eq_maintenance_plan/eq_repair DDL）、§9（设备管理设计）、§14.4（/mes/eq/* 接口）、§15.3 Week 30 任务表、§16.3 验收标准。

### 1.2 本周要解决的问题

1. 车间设备无统一台账。需落地 `eq_equipment` 表 + 状态机（NORMAL/MAINTENANCE/FAULT/SCRAPPED）+ 点检项目（JSON `inspection_items`）+ 最近点检结果（JSON `last_inspection`）+ 关联工作中心（ERP `bd_workstation`）+ 预留 IoT 设备关联（`iot_device_id`）。
2. 设备保养缺乏计划与周期管理。需落地 `eq_maintenance_plan` 表 + 保养类型（日/周/月/季/年）+ 周期天数 + **下次保养日期自动计算**（创建/编辑时按周期推算）。
3. 设备故障缺乏报修→维修→完成闭环。需落地 `eq_repair` 表 + 状态机（REPORTED→REPAIRING→COMPLETED）+ 报修人/维修人/停机时长/维修成本记录。
4. 设备状态与维修状态脱节。需实现**设备状态自动联动**：报修时 `eq_equipment.status=FAULT`；维修完成时若该设备无其他活跃报修则恢复 `NORMAL`。

### 1.3 关键边界（已确认）

- **设备状态自动联动维修记录**：`eq_repair` 报修(REPORTED)自动置设备 FAULT；维修完成(COMPLETED)时校验该设备无其他活跃报修(REPORTED/REPAIRING)才恢复 NORMAL，避免误恢复。
- **IoT 仅预留字段**：`eq_equipment.iot_device_id` 按 §12.4「字段预留，无外键」存储，**不实现实时状态/最新数据查询**（IoT 软关联完整联动列为后续增强）。不引入 ruoyi-iot 依赖到 eq 模块。
- **工作中心 = ERP bd_workstation**：`eq_equipment.workcenter_id/workcenter_code` 存 ERP `bd_workstation.workstation_id/workstation_code`（沿用 `ProFlowCardStep.workcenterId/Code` 的既定来源，见 `ErpRoutingStepMapper`）。台账新增/编辑时 workcenter 为可选冗余字段，不强制跨库校验（与 QC 物料冗余字段一致的处理）。
- **MES 自包含设备台账**：§12 明确「设备管理统一 MES，仅 MES」，ERP 无设备主数据表（`bd_workstation.equipment_id` 为悬空槽），MES `eq_equipment` 为唯一设备主数据源，不与 ERP 双写。
- **不跨库复用 ERP 质检/设备表**：点检合入设备台账 JSON 字段（§2.3 注：原 4 表→3 表，点检合入），不建独立点检表。

### 1.4 预期结果

- 操作员可增删改查设备台账（含点检项目 JSON）、保养计划、维修记录；状态机正确流转。
- 保养计划创建/编辑时下次保养日期按周期自动计算。
- 维修报修自动置设备 FAULT，维修完成自动恢复 NORMAL（无其他活跃报修时）。
- `mvn compile`（mes）与前端 `eslint` 通过。

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| eq_equipment/eq_maintenance_plan/eq_repair DDL 规范在主文档 §2.3（未执行），原 `tenant_id BIGINT` 需改为 Week22/25 约定 `VARCHAR(20) DEFAULT '000000'` + `del_flag CHAR(1)` | 主文档 §2.3 + `ry-mes-week25.sql` |
| eq 包完全空白：无 Java、无 DDL、无前端、无子菜单、无计划文档 | `ruoyi-mes/.../com/nexus/mes/` 仅 common/pro/qc 三包；grep `EqEquipment` 仅命中文档 |
| 设备状态 NORMAL/MAINTENANCE/FAULT/SCRAPPED；保养计划 status ACTIVE；维修 status REPORTED/REPAIRING/COMPLETED | 主文档 §2.3 DDL + §9 |
| 保养类型 DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY + cycle_days + 下次保养日期自动计算 | 主文档 §9.2 + DDL |
| 点检合入台账 JSON：`inspection_items`（点检项目及标准）+ `last_inspection`（最近点检结果），无独立点检表 | 主文档 §2.3 注 + §9.1 |
| `eq_equipment.iot_device_id` 预留关联 IoT 设备，无外键；IoT `iot_device.mes_equipment_id` 双向软引用 | 主文档 §12.4 + IoT DDL `ry-iot-week33.sql:77` |
| 工作中心 = ERP `bd_workstation`：MES workcenterId/Code 源自 `bd_routing_step.workstation_id/workstation_code`，可经 `MesErpBridgeService.getWorkstation()` 读 | `ErpRoutingStepMapper.java` + `ProWorkOrderServiceImpl:253-254` + `IMesErpBridgeService.getWorkstation` |
| 菜单结构：4003 设备管理（Week17 已建父菜单，**零子菜单**）；Week25 用到 4101，**Week26 取 4110-4121**（留 4102-4109 空隙对齐 Week25 接续习惯，取整段） | `ry-vue-mes-menu-week17.sql:4003` + `ry-vue-mes-menu-week25.sql` |
| MES Entity/Service/Controller 模式：TenantEntity + @TableLogic delFlag + @DS("mes") @Service + @SaCheckPermission + BaseMapperPlus | Week25 `QcIqc` 一族 + `ProSn` 一族（已实现） |
| 维修编号/保养编号生成：Week25 已有 `前缀-yyyyMMdd-3位序号` 模式（uk 唯一索引兜底，不加 Lock4j） | `QcIqcServiceImpl.generateNo` |
| 既有 IoT→MES 安灯联动：`IMesAndonService.createAndonFromAlert` 创建 EQUIPMENT 类型安灯，但未关联具体 eq_equipment（本周不改动） | `MesAndonServiceImpl` + `IotAlertNotifier` |
| §14.4 接口：设备 /mes/eq/equipment(list/get/add/edit/del)、保养 /mes/eq/maintenance-plan(list/get/add/edit)、维修 /mes/eq/repair(list/get/add/start/complete) | 主文档 §14.4 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | eq DDL | 高 | `ry-mes-week26.sql` | 三表对齐 Week25 约定 |
| 2 | EqEquipment/EqMaintenancePlan/EqRepair Entity/BO/VO/Mapper | 高 | 12 个 Java 文件 | 字段对齐 DDL |
| 3 | 三个 Service + 实现（CRUD + 状态机 + 设备状态联动 + 下次保养日期计算） | 高 | 3 接口 + 3 实现 | 维修联动设备状态 + 保养日期自动算 |
| 4 | 三个 Controller | 高 | 3 REST 控制器 | list/get/add/edit/del + repair start/complete |
| 5 | 前端 3 API + 3 列表页 | 高 | 6 文件 | 列表+搜索+新增/编辑弹窗+维修状态操作 |
| 6 | 菜单权限 SQL | 中 | `ry-vue-mes-menu-week26.sql` | 4110-4121，父菜单 4003 |
| 7 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 |

---

## 四、详细任务分解

### 任务 1：eq DDL

**File:** Create `script/sql/ry-mes-week26.sql`

对齐主文档 §2.3 字段，按 Week22/25 约定调整（`tenant_id` BIGINT→VARCHAR(20)、加 `del_flag`、JSON 字段、状态列 VARCHAR）。三表：

```sql
USE `ry-mes`;

-- eq_equipment: MES设备台账
-- 状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED
-- 点检合入JSON: inspection_items(项目及标准) + last_inspection(最近结果)
CREATE TABLE eq_equipment (
    equipment_id     BIGINT NOT NULL COMMENT '设备ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    equipment_code   VARCHAR(64) NOT NULL COMMENT '设备编码',
    equipment_name   VARCHAR(128) NOT NULL COMMENT '设备名称',
    equipment_type   VARCHAR(32) COMMENT '设备类型',
    model            VARCHAR(128) COMMENT '型号',
    manufacturer     VARCHAR(128) COMMENT '制造商',
    serial_no        VARCHAR(64) COMMENT '出厂编号',
    workcenter_id    BIGINT COMMENT '工作中心ID(ERP bd_workstation.workstation_id)',
    workcenter_code  VARCHAR(64) COMMENT '工作中心编码(冗余)',
    location         VARCHAR(256) COMMENT '安装位置',
    purchase_date    DATE COMMENT '购置日期',
    warranty_date    DATE COMMENT '保修到期',
    status           VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/FAULT/SCRAPPED',
    inspection_items JSON COMMENT '点检项目及标准(JSON)',
    last_inspection  JSON COMMENT '最近一次点检结果(JSON)',
    iot_device_id    BIGINT COMMENT '关联IoT设备ID(预留,无外键)',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (equipment_id),
    UNIQUE INDEX uk_equipment_code (equipment_code),
    INDEX idx_status (status), INDEX idx_workcenter_id (workcenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备台账';

-- eq_maintenance_plan: MES设备保养计划
-- 保养类型: DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
-- 下次保养日期: next_date 按周期自动计算
CREATE TABLE eq_maintenance_plan (
    plan_id          BIGINT NOT NULL COMMENT '计划ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    plan_name        VARCHAR(128) NOT NULL COMMENT '计划名称',
    equipment_id     BIGINT NOT NULL COMMENT '设备ID',
    maintenance_type VARCHAR(16) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
    cycle_days       INT COMMENT '周期(天)',
    next_date        DATE COMMENT '下次保养日期(自动计算)',
    content          TEXT COMMENT '保养内容',
    responsible_id   BIGINT COMMENT '负责人ID',
    responsible_name VARCHAR(64) COMMENT '负责人姓名',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (plan_id),
    INDEX idx_equipment_id (equipment_id), INDEX idx_next_date (next_date), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备保养计划';

-- eq_repair: MES设备维修记录
-- 状态机: REPORTED -> REPAIRING -> COMPLETED
-- 联动: 报修复设备FAULT; 完成且无其他活跃报修复NORMAL
CREATE TABLE eq_repair (
    repair_id        BIGINT NOT NULL COMMENT '维修ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    repair_no        VARCHAR(64) NOT NULL COMMENT '维修单号',
    equipment_id     BIGINT NOT NULL COMMENT '设备ID',
    fault_type       VARCHAR(32) COMMENT '故障类型',
    fault_desc       VARCHAR(500) COMMENT '故障描述',
    reporter_id      BIGINT COMMENT '报修人ID',
    reporter_name    VARCHAR(64) COMMENT '报修人姓名',
    repairer_id      BIGINT COMMENT '维修人ID',
    repairer_name    VARCHAR(64) COMMENT '维修人姓名',
    repair_start     DATETIME COMMENT '维修开始时间',
    repair_end       DATETIME COMMENT '维修结束时间',
    downtime_hours   DECIMAL(10,2) COMMENT '停机时长(小时)',
    repair_cost      DECIMAL(14,2) COMMENT '维修成本',
    status           VARCHAR(16) DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (repair_id),
    UNIQUE INDEX uk_repair_no (repair_no),
    INDEX idx_equipment_id (equipment_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES设备维修记录';
```

> 与主文档差异：`tenant_id` BIGINT→VARCHAR(20)；加 `del_flag`/`remark`；JSON 字段保留。均对齐 Week25 既定约定。

### 任务 2：Entity/BO/VO/Mapper（12 文件）

**Package:** `com.nexus.mes.eq.*`（新建 eq 包，镜像 qc/pro 布局）

每张表 4 文件（domain + bo + vo + mapper），共 12 个：

- `eq/domain/EqEquipment.java` / `EqMaintenancePlan.java` / `EqRepair.java` — `@Data @EqualsAndHashCode(callSuper=true) @TableName("eq_xxx")` extends `TenantEntity`，`@TableId` 主键，`@TableLogic delFlag`。JSON 字段（`inspectionItems`/`lastInspection`/`content`）用 `String` 存（避免 TypeHandler，前端序列化）。
- `eq/domain/bo/EqEquipmentBo.java` 等 — `@AutoMapper(target=EqXxx.class, reverseConvertGenerate=false)` extends `BaseEntity`，校验 `@NotBlank equipmentCode/equipmentName`（设备）/`@NotNull equipmentId`（保养/维修），查询字段 + `Long[] ids` 批量删。
- `eq/domain/vo/EqEquipmentVo.java` 等 — `@ExcelIgnoreUnannotated @AutoMapper(target=EqXxx.class)`，Excel 导出注解。
- `eq/mapper/EqEquipmentMapper.java` 等 — `extends BaseMapperPlus<EqXxx, EqXxxVo>`。

### 任务 3：三个 Service + 实现

**Files:** `eq/service/IEqXxxService.java` + `eq/service/impl/EqXxxServiceImpl.java`（6 文件）

类头：`@Slf4j @RequiredArgsConstructor @DS("mes") @Service("eqXxxService")`。

**EqEquipmentServiceImpl**：`selectPageList`、`selectById`、`insertByBo`、`updateByBo`、`deleteByIds`。状态常量 `STATUS_NORMAL/MAINTENANCE/FAULT/SCRAPPED`。新增/更新时 `inspectionItems` JSON 直传。提供内部方法 `updateStatus(equipmentId, status)` 供维修 Service 联动调用（同包，直接注入 `IEqEquipmentService` 或 mapper）。

**EqMaintenancePlanServiceImpl**：`selectPageList`、`selectById`、`insertByBo`、`updateByBo`、`deleteByIds`。状态常量 `STATUS_ACTIVE/INACTIVE`，类型 `TYPE_DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY`。
- **下次保养日期自动计算**（核心）：`insertByBo`/`updateByBo` 时，若 `nextDate` 为空但 `cycleDays` 有值，则 `nextDate = today + cycleDays`。`maintenanceType` 与 `cycleDays` 映射（DAILY=1/WEEKLY=7/MONTHLY=30/QUARTERLY=90/YEARLY=365）作为默认 cycleDays（用户可覆盖）。
- 私有方法 `calcNextDate(cycleDays)`：`LocalDate.now().plusDays(cycleDays)`。

**EqRepairServiceImpl**：`selectPageList`、`selectById`、`insertByBo`（报修，生成编号 `RP-yyyyMMdd-seq`）、`startRepair`（REPORTED→REPAIRING，置维修人/repairStart）、`completeRepair`（REPAIRING→COMPLETED，置repairEnd/downtimeHours/repairCost + **联动设备状态**）。
- 状态常量 `STATUS_REPORTED/REPAIRING/COMPLETED`。
- **设备状态联动**（核心，注入 `EqEquipmentMapper`）：
  - `insertByBo`（报修）：CAS 置设备 `status=FAULT`（设备非 SCRAPPED 才联动）。
  - `completeRepair`：维修完成时，查询该设备是否还有其他活跃报修（`eq_repair` status in (REPORTED, REPAIRING) 且 repair_id != 当前）。若**无**其他活跃报修，则恢复设备 `status=NORMAL`。
- `startRepair`/`completeRepair` 用 CAS 状态转换（镜像 ProAndon 状态机 + Week25 检验状态机）。

**编号生成**（私有方法，镜像 Week25）：`RP-` + `yyyyMMdd` + `-` + 3位序号（基于当日已有数 +1，uk 唯一索引兜底，不加 Lock4j）。

### 任务 4：三个 Controller

**Files:** `eq/controller/EqEquipmentController.java` / `EqMaintenancePlanController.java` / `EqRepairController.java`

镜像 `QcIqcController` / `ProAndonController`：`@Validated @RequiredArgsConstructor @RestController @RequestMapping("/mes/eq/{equipment,maintenance-plan,repair}") extends BaseController`，`@SaCheckPermission`，`@Log`。

| 端点 | 权限 |
|------|------|
| GET /list | mes:eq:equipment:list / mes:eq:maintenance:list / mes:eq:repair:list |
| GET /{id} | mes:eq:*:query |
| POST / (新增) | mes:eq:*:add |
| PUT / (编辑, 设备/保养) | mes:eq:equipment:edit / mes:eq:maintenance:edit |
| DELETE /{ids} (仅设备) | mes:eq:equipment:remove |
| PUT /start/{id} (维修开工) | mes:eq:repair:start |
| PUT /complete/{id} (维修完成) | mes:eq:repair:complete |

> 保养接口路径用 `/mes/eq/maintenance-plan`（对齐 §14.4 文档），前端目录用 `mes/eq/maintenance`（对齐 §11）。维修用 start/complete（对齐 §14.4）。

### 任务 5：前端 3 API + 3 列表页

**Files (plus-ui/src/):**
- `api/mes/eq/equipment.ts` / `maintenance.ts` / `repair.ts` — 对齐 `qc/iqc.ts`：VO/Query/Form 接口 + CRUD + repair start/complete。
- `views/mes/eq/equipment/index.vue` / `maintenance/index.vue` / `repair/index.vue` — 镜像 `qc/iqc/index.vue` 结构：搜索栏 + el-table + 新增/编辑弹窗 + 状态 el-tag 颜色编码。
  - 设备台账：状态 NORMAL=success/MAINTENANCE=warning/FAULT=danger/SCRAPPED=info；JSON 点检字段用文本域（placeholder 示例 JSON）。
  - 保养计划：类型下拉（日/周/月/季/年）+ cycleDays + nextDate（只读，后端算）。
  - 维修记录：报修(新增)→开工(REPORTED)→完成(REPAIRING)按钮，完成时 prompt 停机时长/维修成本；状态颜色 REPORTED=danger/REPAIRING=warning/COMPLETED=success。

### 任务 6：菜单权限 SQL

**File:** Create `script/sql/ry-vue-mes-menu-week26.sql`

父菜单 **4003（设备管理，Week17 已建，零子菜单）**。菜单 ID 段 **4110-4121**（Week25 用到 4101，留 4102-4109 空隙取整段接续）。

```sql
USE `ry-vue`;
-- 设备台账 (parent=4003 设备管理)
INSERT INTO sys_menu VALUES
(4110,'设备台账',4003,1,'eq/equipment','mes/eq/equipment/index','',1,0,'C','0','0','mes:eq:equipment:list','computer',103,1,NOW(),NULL,NULL,'MES设备台账'),
(4111,'设备查询',4110,1,'','','',1,0,'F','0','0','mes:eq:equipment:query','#',103,1,NOW(),NULL,NULL,''),
(4112,'设备新增',4110,2,'','','',1,0,'F','0','0','mes:eq:equipment:add','#',103,1,NOW(),NULL,NULL,''),
(4113,'设备编辑',4110,3,'','','',1,0,'F','0','0','mes:eq:equipment:edit','#',103,1,NOW(),NULL,NULL,''),
(4114,'设备删除',4110,4,'','','',1,0,'F','0','0','mes:eq:equipment:remove','#',103,1,NOW(),NULL,NULL,'');
-- 保养计划
INSERT INTO sys_menu VALUES
(4115,'保养计划',4003,2,'eq/maintenance','mes/eq/maintenance/index','',1,0,'C','0','0','mes:eq:maintenance:list','tool',103,1,NOW(),NULL,NULL,'MES设备保养计划'),
(4116,'保养查询',4115,1,'','','',1,0,'F','0','0','mes:eq:maintenance:query','#',103,1,NOW(),NULL,NULL,''),
(4117,'保养新增',4115,2,'','','',1,0,'F','0','0','mes:eq:maintenance:add','#',103,1,NOW(),NULL,NULL,''),
(4118,'保养编辑',4115,3,'','','',1,0,'F','0','0','mes:eq:maintenance:edit','#',103,1,NOW(),NULL,NULL,'');
-- 维修记录
INSERT INTO sys_menu VALUES
(4119,'维修记录',4003,3,'eq/repair','mes/eq/repair/index','',1,0,'C','0','0','mes:eq:repair:list','wrench',103,1,NOW(),NULL,NULL,'MES设备维修记录'),
(4120,'维修查询',4119,1,'','','',1,0,'F','0','0','mes:eq:repair:query','#',103,1,NOW(),NULL,NULL,''),
(4121,'维修报修',4119,2,'','','',1,0,'F','0','0','mes:eq:repair:add','#',103,1,NOW(),NULL,NULL,''),
(4122,'维修开工',4119,3,'','','',1,0,'F','0','0','mes:eq:repair:start','#',103,1,NOW(),NULL,NULL,''),
(4123,'维修完成',4119,4,'','','',1,0,'F','0','0','mes:eq:repair:complete','#',103,1,NOW(),NULL,NULL,'');
```

> 实际用到 4110-4123（维修 5 个按钮比预期多 1 个，仍接续无冲突）。验证：`SELECT menu_id,menu_name,perms FROM sys_menu WHERE menu_id BETWEEN 4110 AND 4123 ORDER BY menu_id;` 执行后为角色勾选。

### 任务 7：编译 + lint 验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `eslint src/views/mes/eq src/api/mes/eq --ext .vue,.ts` 无 error

---

## 五、端到端链路

```
设备台账:
  新增设备(编码/名称/类型/工作中心/点检项目JSON) → status=NORMAL
  手动置保养中/报废 → status 流转(SCRAPPED 终态, 报修不联动 SCRAPPED 设备)

保养计划:
  创建计划(设备+类型+周期) → nextDate 自动算(today+cycleDays)
  编辑周期 → nextDate 重算

维修记录(设备状态自动联动):
  报修(新增 eq_repair, REPORTED) → 设备 status=FAULT
    → 开工(startRepair, REPORTED→REPAIRING, 置维修人/repairStart)
    → 完成(completeRepair, REPAIRING→COMPLETED, 置repairEnd/downtimeHours/repairCost)
        → 校验该设备无其他活跃报修(REPORTED/REPAIRING) → 是则设备 status=NORMAL
```

---

## 六、风险与注意事项

| 风险 | 应对 |
|------|------|
| 维修完成误恢复设备状态（多报修并发） | completeRepair 时查该设备其他活跃报修 count，仅 count=0 才恢复 NORMAL；CAS 更新设备状态 |
| 设备 SCRAPPED 仍被报修联动置 FAULT | 报修时校验设备非 SCRAPPED 才联动；SCRAPPED 为终态不回退 |
| 下次保养日期计算与用户覆盖冲突 | 用户显式传 nextDate 则尊重用户值；仅 nextDate 为空时按 cycleDays 自动算 |
| JSON 点检字段处理 | Entity 用 String 存，前端 JSON.stringify/parse，避免 TypeHandler 复杂度（同 Week25 defectInfo） |
| 编号并发冲突 | `uk_repair_no` 唯一索引兜底；不引入 Lock4j（报修低并发，同 Week25） |
| eq 与 pro/ qc 包共存编译 | 全新 eq 包，无现有引用改动；仅新增文件 |
| IoT 联动越界 | iot_device_id 仅存储字段，不引入 ruoyi-iot 依赖，不查 Redis 实时状态 |
| 保养接口路径 vs 前端目录差异 | 后端 `/mes/eq/maintenance-plan`，前端目录 `mes/eq/maintenance`（对齐 §14.4 + §11 文档） |

---

## 七、明确排除项（本周不做）

- IoT 设备实时状态/最新数据展示（iot_device_id 仅预留字段，后续增强）
- 保养自动到期提醒/定时任务（依赖 SnailJob，后续）
- 点检执行流程（点检项目仅 JSON 存储，执行记录为后续）
- MES 设备与 ERP bd_workstation.equipment_id 的跨库软关联打通（ERP 设备槽悬空，后续）
- IoT 告警→EQUIPMENT 安灯关联具体 eq_equipment（既有安灯链路不改，后续增强）
- SPC/数据采集（Phase 3 后续 Week）
- el-table → vxe-table 迁移

---

## 八、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `ry-mes-week26.sql` | SQL | `script/sql/ry-mes-week26.sql` |
| EqEquipment/EqMaintenancePlan/EqRepair Entity/BO/VO/Mapper | Java×12 | `ruoyi-mes/.../eq/{domain,domain/bo,domain/vo,mapper}/` |
| IEqXxxService + 实现 | Java×6 | `ruoyi-mes/.../eq/{service,service/impl}/` |
| EqEquipmentController/EqMaintenancePlanController/EqRepairController | Java×3 | `ruoyi-mes/.../eq/controller/` |
| `equipment.ts`/`maintenance.ts`/`repair.ts` | TS×3 | `plus-ui/src/api/mes/eq/` |
| `equipment/index.vue`/`maintenance/index.vue`/`repair/index.vue` | Vue×3 | `plus-ui/src/views/mes/eq/` |
| `ry-vue-mes-menu-week26.sql` | SQL | `script/sql/ry-vue-mes-menu-week26.sql` |

---

## 九、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes`/`ry-erp`/`ry-iot` 四库 + Redis 就绪，并已执行 Week17/25 DDL 与菜单 SQL。

1. **DDL 执行**：`ry-mes-week26.sql` 建三表；`ry-vue-mes-menu-week26.sql` 注入菜单并为角色勾选 4110-4123。
2. **设备台账**：新增设备（编码/名称/点检项目JSON）→ status=NORMAL；手动置 MAINTENANCE/SCRAPPED 流转。
3. **保养计划**：创建计划（设备+MONTHLY+cycleDays=30）→ nextDate 自动 = today+30；编辑 cycleDays=7 → nextDate 重算。
4. **维修联动**：设备 A 报修 → 设备 A status=FAULT + eq_repair REPORTED；开工 → REPAIRING；完成（停机2h/成本500）→ COMPLETED + 设备 A status=NORMAL（无其他活跃报修）。
5. **多报修防误恢复**：设备 A 报修两次（repair1/repair2）→ 设备 FAULT；repair1 完成 → 设备仍 FAULT（repair2 活跃）；repair2 完成 → 设备 NORMAL。
6. **SCRAPPED 不联动**：报废设备报修 → 设备仍 SCRAPPED（不置 FAULT）。
7. **静态**：`mvn compile`（mes）+ `eslint` 均通过。

---

**文档生成时间**: 2026-06-24
**执行者**: AI Agent
**审核状态**: 待 Claude 自审 → 待用户审查
