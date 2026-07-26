# P0-2 工装/模治具管理模块详细实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **编制日期**: 2026-07-26
> **计划周期**: Week 29（2026-08-10 ~ 08-16）；P0-9 提前完工，可提前启动
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.2
> **状态**: ✅ 编码完成 + SQL 落库 + 端到端联调通过（20/20 断言），后端 BUILD SUCCESS + 前端 eslint 0 错误

---

## 实施结果摘要

| 任务 | 状态 | 交付物 |
|------|------|--------|
| 1. DDL（4 表） | ✅ | [mes-p0-02-01-tooling.sql](../sql/mes-p0-02-01-tooling.sql) - 四表对齐既定约定，CREATE TABLE IF NOT EXISTS 幂等 |
| 2. Entity/BO/VO/Mapper | ✅ | 17 个 Java 文件（含 TgCheckoutBo 动作 BO），字段对齐 DDL，VO 含 remainingCount/Hours |
| 3. Service + 实现 | ✅ | 4 接口 + 4 实现，含状态机(CAS)/寿命累加/保养归零/checkToolingAvailable 契约/孤儿守卫 |
| 4. Controller | ✅ | 4 REST 控制器，端点对齐 §四 |
| 5. 前端 API + 页面 | ✅ | 4 API + 4 页面，全选择器化(车间/工位/工单/操作员/工装) + dict-tag + autocode 预览 |
| 6. 菜单权限 SQL | ✅ | [mes-p0-02-02-menu.sql](../sql/mes-p0-02-02-menu.sql) - ID 段 4172-4195（24 条），父菜单 4003 |
| 7. 自动编码规则 | ✅ | [mes-p0-02-03-autocode-tooling.sql](../sql/mes-p0-02-03-autocode-tooling.sql) - MES_TOOLING_CODE（TG+日期+3位日循环） |
| 8. 字典种子 | ✅ | [mes-p0-02-04-tooling-dicts.sql](../sql/mes-p0-02-04-tooling-dicts.sql) - 5 字典（status/type/trigger/maintain_type/usage_status） |
| 9. 编译 + lint 验证 | ✅ | `mvn -pl ruoyi-modules/ruoyi-mes -am compile` BUILD SUCCESS；eslint 0 错误；vue-tsc 仅 2 处 `$modal.prompt` 3 参基线噪声（同 lot 页） |

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

对标"智能工厂九大模块"独立"工装管理"、鼎捷"工装/模具管理"，当前 MES `eq` 模块仅覆盖**设备**（机器）维度的台账/保养/维修/巡检/备件/OEE，**工装/模具/夹具/刀具/量具**这类**寿命型、可领用归还、按使用次数/时长保养**的资源完全没有管理。工装是 P0-3（进站出站校验"工装可用性"）的硬依赖，也是 P0-1 物料拉动中工装配套的 prerequisite。

### 1.2 本周要解决的问题

1. **工装台账缺失**：模具/夹具/刀具/量具无编码、无状态、无所属车间记录
2. **寿命监控缺失**：工装使用次数/时长无累计，到寿命无预警，存在质量隐患
3. **保养触发缺失**：工装保养无"按次数/按时长/按周期"三模触发机制
4. **领用归还缺失**：工装被谁领走、何时归还无记录，状态与实物脱节
5. **使用追溯缺失**：工装与工单/工位/操作员的绑定使用记录断裂，无法追溯
6. **报废流程缺失**：寿命到期或损坏无规范报废流转

### 1.3 关键边界（已确认）

- **独立 `tg` 模块**（非 eq 扩展）：4 张 `tg_` 表，镜像 eq 包布局。工装寿命/领用归还是工装特有，设备无此概念。
- **5 表 → 4 表合并**：原计划 §3.2.3 的 `tg_checkout_record`（领用归还）并入 `tg_usage_record`（使用记录）--一次领用-归还即一次使用会话，两表字段高度重叠（tooling_id/operator_id/checkout_time/return_time），合并消除冗余。F2-5 使用记录与 F2-7 领用归还由同一表的不同生命周期阶段承载（领用=建记录 status=OUT，归还=更新 status=RETURNED + 写入 usage_count/hours）。
- **工装编码生成**：`autoCodeUtil.genSerialCode("MES_TOOLING_CODE", null)`，规则 `TG + yyyyMMdd + 3位日循环流水号`。对齐 P0-9 `MES_LOT_CODE` 模式（废弃本地 count+1）。
- **寿命双轨**：`used_count`（次数）+ `used_hours`（时长），每次归还累加。
- **保养三模**：BY_COUNT（达次数）/BY_HOURS（达时长）/PERIODIC（按周期）。下次保养阈值/时间自动计算。
- **保养后归零**：保养记录创建后，`used_count`/`used_hours` 归零，`last_maintain_time` 更新。
- **状态机**：IDLE ⇄ IN_USE（领用/归还）；IDLE/IN_USE → MAINTENANCE（保养中）→ IDLE；→ SCRAPPED（报废终态）。
- **P0-3 契约方法**：`checkToolingAvailable(toolingId)` 返回 `{usable, reason, tooling}`，供 P0-3 进站校验调用（对齐 P0-9 B5 `checkLotUsable` 模式）。
- **与 eq 平行**：tg 保养计划/记录独立于 eq 设备保养；不与 `EqMaintenancePlan`/`MaintenanceDueTask` 共表。
- **跨库**：workstation/workshop 信息冗余存储（`workshop_name`/`workstation_name`），不跨库查 ERP（对齐 P0-9 冗余字段策略）。

### 1.4 预期结果

- 工装可创建/查询/领用/归还/保养/报废，状态机正确流转
- 使用次数/时长自动累加，达保养阈值时预警
- 保养后计数归零，记录可查
- 使用记录绑定工单/工位/操作员，追溯链完整
- 进站校验可调用契约方法判断工装可用性
- `mvn compile`（mes）与前端 `eslint` 通过，vue-tsc 仅基线噪声

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| 既定约定：tenant_id VARCHAR(20) DEFAULT '000000'、del_flag CHAR(1)、状态列 VARCHAR(16)、数量/时长 DECIMAL(14,4)/DECIMAL(14,2)、备注 VARCHAR(500)、日期 DATE/DATETIME | [mes-week26-07-equipment.sql](../sql/mes-week26-07-equipment.sql) + [mes-p0-09-01-lot.sql](../sql/mes-p0-09-01-lot.sql) |
| Entity 模式：`@Data @EqualsAndHashCode(callSuper=true) @TableName` extends `TenantEntity` + `@TableId` + `@TableLogic delFlag` | [EqEquipment.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/domain/EqEquipment.java) + [LtLot.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/domain/LtLot.java) |
| BO 模式：`@AutoMapper(target=..., reverseConvertGenerate=false)` extends `BaseEntity` + `@NotBlank/@NotNull` + `Long[] ids` | [LtLotBo.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/domain/bo/LtLotBo.java) |
| VO 模式：`@ExcelIgnoreUnannotated @AutoMapper(target=...)` implements Serializable + `@ExcelProperty` + `@Translation` 翻译用户名 | [LtSnBindingVo.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/domain/vo/LtSnBindingVo.java)（`@Translation(USER_ID_TO_NAME, mapper="boundBy")`） |
| Mapper 模式：`extends BaseMapperPlus<X, XVo>` | [LtLotMapper.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/mapper/LtLotMapper.java) |
| ServiceImpl 模式：`@Slf4j @RequiredArgsConstructor @DS("mes") @Service("xxxService")` + `@Lock4j` + `@Transactional` + `Wrappers.lambdaQuery` + 私有 `buildQueryWrapper` + CAS 状态机（`.eq(status, currentStatus)` + rows==0 抛异常） | [LtLotServiceImpl.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java) |
| 自动编码：`autoCodeUtil.genSerialCode("RULE_CODE", null)`（注入 `com.nexus.system.strategy.AutoCodeUtil`），规则存 `sys_auto_code_rule`/`sys_auto_code_part`（ry-vue 库） | [LtLotServiceImpl.java:505-507](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java) + [mes-p0-09-05-autocode-lot.sql](../sql/mes-p0-09-05-autocode-lot.sql) |
| Controller 模式：`@Validated @RequiredArgsConstructor @RestController @RequestMapping("/mes/xxx/yyy")` extends `BaseController` + `@SaCheckPermission` + `@Log` + `R<T>` + `TableDataInfo<T>` | [LtLotController.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/controller/LtLotController.java) |
| 菜单 SQL 模式：`INSERT INTO sys_menu VALUES (id, name, parent_id, sort, path, component, ...)` 父菜单 4xxx，子菜单 ID 接续 | [mes-week26-20-menu.sql](../sql/mes-week26-20-menu.sql) |
| 字典 SQL 模式：`sys_dict_type`/`sys_dict_data` 动态 MAX+1 主键 + `WHERE NOT EXISTS` 守护（两表主键均雪花，不得硬编码小 ID） | [mes-p0-09-06-lot-dict.sql](../sql/mes-p0-09-06-lot-dict.sql) |
| 前端选择器组件已存在：`WorkcenterSelect`、`MaterialSelectDialog`、`EquipmentSelect`、`UserSelect` | `plus-ui/src/components/` |
| 前端 list API：`listWorkOrder`(mes/pro/workorder)、ERP `bd/workshop`、`bd/workstation`、`bd/process` 均存在 | `plus-ui/src/api/mes/pro/workorder.ts` + `plus-ui/src/api/erp/bd/` |
| MES 前端约定：全选择器化（无裸 ID 输入）+ dict-tag（无硬编码状态 map），P0-9 第九批已全目录收尾 | memory `mes-lot-remediation-and-erp-batch-conflict` 第八/九批 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| 1 | DDL（4 表） | 高 | `mes-p0-02-01-tooling.sql` | 四表对齐既定约定，`CREATE TABLE IF NOT EXISTS` 幂等 | ⬜ |
| 2 | Entity/BO/VO/Mapper | 高 | 16 个 Java 文件 | 字段对齐 DDL，VO 含冗余名称 + `@Translation` | ⬜ |
| 3 | Service + 实现 | 高 | 4 接口 + 4 实现 | CRUD + 状态机 + 寿命累加 + 保养归零 + 契约方法 | ⬜ |
| 4 | Controller | 高 | 4 REST 控制器 | list/get/add/edit/del + 领用/归还/保养/报废/契约 | ⬜ |
| 5 | 前端 API + 页面 | 高 | 4 API + 4 页面 | 全选择器化 + dict-tag，无裸 ID 输入 | ⬜ |
| 6 | 菜单权限 SQL | 中 | `mes-p0-02-02-menu.sql` | ID 段 4172-4199，父菜单 4003 | ⬜ |
| 7 | 自动编码规则 | 中 | `mes-p0-02-03-autocode-tooling.sql` | `MES_TOOLING_CODE` 规则（TG+日期+3位日循环） | ⬜ |
| 8 | 字典种子 | 中 | `mes-p0-02-04-tooling-dicts.sql` | 5 字典（status/type/trigger/maintain_type/usage_status） | ⬜ |
| 9 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 | ⬜ |

---

## 四、详细任务分解

### 任务 1：DDL（4 表）

**File:** Create `doc/mes/sql/mes-p0-02-01-tooling.sql`

四表设计（见 §六 DDL 详情）：
- `tg_tooling`：工装台账（类型/规格/状态/寿命双轨/采购）
- `tg_maintenance_plan`：保养计划（三模触发 + 下次阈值/时间）
- `tg_maintenance_record`：保养记录（保养前后计数快照 + 归零）
- `tg_usage_record`：使用/领用记录（合并 checkout，含工单/工位/操作员/使用量）

**P0-9 教训对齐**：
- 直接用最终表名，`CREATE TABLE IF NOT EXISTS` 幂等，杜绝事后改名脚本（P0-9 A5 教训）
- 冗余 `workshop_name`/`workstation_name`/`operator_name`/`maintainer_name`/`wo_no`，避免跨库查 ERP 与列表 N+1（P0-9 冗余字段策略）
- `tg_usage_record` 含 `status(OUT/RETURNED)`，承载领用归还生命周期

### 任务 2：Entity/BO/VO/Mapper（16 文件）

**Packages:** `com.nexus.mes.tg.*`（新建包，镜像 eq/lt 布局）

每张表 4 文件（domain + bo + vo + mapper），共 16 个：
- `tg/domain/TgTooling.java` / `TgMaintenancePlan.java` / `TgMaintenanceRecord.java` / `TgUsageRecord.java`
- 对应 `domain/bo/`、`domain/vo/`、`mapper/`（各 4 文件）

**VO 关键增强**：
- `TgToolingVo`：加 `remainingCount`/`remainingHours`（=total-used，transient 计算字段，Service 填充）
- `TgUsageRecordVo`：`operatorName` 用 `@Translation(USER_ID_TO_NAME, mapper="operatorId")` 自动翻译（对齐 `LtSnBindingVo.boundByName`）
- `TgMaintenanceRecordVo`：`maintainerName` 同理 `@Translation`

### 任务 3：Service + 实现（8 文件）

#### 3.1 `TgToolingServiceImpl`（核心）

**状态机**：

```
                    checkout(operatorId)
          ┌──────────────────────────────────┐
          ▼                                   │
     ┌──────────┐  return(usageCount,hours)  │
     │   IDLE   │◀────────────────────────┐  │
     └────┬─────┘                         │  │
          │ startMaintain                 │  │
          ▼                               │  │
     ┌──────────────┐  completeMaintain   │  │
     │ MAINTENANCE  │──────────────┘      │  │  ← 实际归 IDLE
     └──────┬───────┘                     │  │
            │ scrap                       │  │
            ▼                              │  │
     ┌──────────────┐                     │  │
     │   SCRAPPED   │ (终态)              │  │
     └──────────────┘                     │  │
                                          │  │
     ┌──────────┐  checkout               │  │
     │   IDLE   │─────────────────────────┘  │
     └────┬─────┘                            │
          │ (IN_USE 不可直接 scrap，需先 return)
          ▼
     ┌──────────┐
     │  IN_USE  │── return ──▶ IDLE
     └──────────┘
```

**关键方法**：

```java
// 领用: IDLE -> IN_USE, 写 tg_usage_record(status=OUT)
@Lock4j(keys = {"#toolingId"})
@Transactional(rollbackFor = Exception.class)
public Boolean checkout(Long toolingId, Long operatorId, Long woId, Long workstationId);

// 归还: IN_USE -> IDLE, 更新 usage_record(RETURNED + usage_count/hours), 累加 tg_tooling.used_*, 检查保养到期
@Lock4j(keys = {"#toolingId"})
@Transactional(rollbackFor = Exception.class)
public Boolean doReturn(Long toolingId, Integer usageCount, BigDecimal usageHours);

// 开始保养: IDLE/IN_USE -> MAINTENANCE
@Transactional(rollbackFor = Exception.class)
public Boolean startMaintain(Long toolingId, String reason);

// 完成保养: MAINTENANCE -> IDLE, 归零 used_count/used_hours, 更新 last_maintain_time, 写 tg_maintenance_record
@Lock4j(keys = {"#toolingId"})
@Transactional(rollbackFor = Exception.class)
public Boolean completeMaintain(Long toolingId, Long planId, String maintainType,
                                 Integer usedCountBefore, BigDecimal usedHoursBefore,
                                 Long maintainerId, String description, BigDecimal cost);

// 报废: -> SCRAPPED (守卫: IN_USE 不可, 需先归还)
@Transactional(rollbackFor = Exception.class)
public Boolean scrap(Long toolingId, String reason);

// 删除孤儿守卫: 存在使用/保养记录的工装不可删 (对齐 P0-9 A3)
@Transactional(rollbackFor = Exception.class)
public Boolean deleteByIds(Long[] ids);

// 契约方法: 校验工装可用性 (供 P0-3 进站校验调用, 对齐 P0-9 B5 checkLotUsable)
public Map<String, Object> checkToolingAvailable(Long toolingId);

// 保养到期查询 (供预警页面)
public List<TgToolingVo> listMaintenanceDue();
```

**CAS 状态机**：所有状态转换用 `.eq(TgTooling::getStatus, currentStatus)` 条件更新，`rows==0` 抛"工装状态已变更"（对齐 `LtLotServiceImpl.quarantine`）。

**编码生成**：`private String generateToolingCode() { return autoCodeUtil.genSerialCode("MES_TOOLING_CODE", null); }`（对齐 `LtLotServiceImpl.generateLotCode`）。

#### 3.2 `TgMaintenancePlanServiceImpl`

- CRUD + `calcNextDue(TgTooling)`：按 trigger_type 算下次到期
  - BY_COUNT：`next_maintain_count = last_maintain_count + trigger_value`（或 `used_count + trigger_value` 归零后）
  - BY_HOURS：`next_maintain_hours = used_hours + trigger_value`（归零后）
  - PERIODIC：`next_maintain_time = last_maintain_time + trigger_value 天`
- `enabled` 字段：CHAR(1) '1'/'0'，前端 el-switch（不用 dict-tag，布尔标志非状态）

#### 3.3 `TgMaintenanceRecordServiceImpl`

- CRUD；`insertByBo` 时若关联 plan，触发 `TgToolingServiceImpl.completeMaintain`（归零 + 状态机）
- 保养前后计数快照：`used_count_before`/`used_count_after`(=0)/`used_hours_before`/`used_hours_after`(=0)

#### 3.4 `TgUsageRecordServiceImpl`

- CRUD + 查询（按 tooling_id / wo_id / operator_id）
- `confirmPick`-like 流程不单独建（领用即建 OUT 记录，归还即更新 RETURNED，均在 `TgToolingServiceImpl.checkout/doReturn` 内完成）

### 任务 4：Controller（4 个）

| 端点 | 权限 | 说明 |
|------|------|------|
| GET /mes/tg/tooling/list | mes:tg:tooling:list | 工装台账列表 |
| GET /mes/tg/tooling/{toolingId} | mes:tg:tooling:query | 工装详情 |
| POST /mes/tg/tooling | mes:tg:tooling:add | 新增（编码自动生成） |
| PUT /mes/tg/tooling | mes:tg:tooling:edit | 编辑（寿命/状态字段不可改，走专用接口） |
| DELETE /mes/tg/tooling/{ids} | mes:tg:tooling:remove | 删除（孤儿守卫） |
| PUT /mes/tg/tooling/checkout/{toolingId} | mes:tg:tooling:checkout | 领用 |
| PUT /mes/tg/tooling/return/{toolingId} | mes:tg:tooling:return | 归还（带 usageCount/hours） |
| PUT /mes/tg/tooling/maintain/start/{toolingId} | mes:tg:tooling:maintain | 开始保养 |
| PUT /mes/tg/tooling/maintain/complete/{toolingId} | mes:tg:tooling:maintain | 完成保养（归零） |
| PUT /mes/tg/tooling/scrap/{toolingId} | mes:tg:tooling:scrap | 报废 |
| GET /mes/tg/tooling/due | mes:tg:tooling:list | 保养到期预警查询 |
| GET /mes/tg/tooling/check/{toolingId} | mes:tg:tooling:query | 契约：校验可用性（P0-3 调用） |
| GET /mes/tg/maintenance-plan/list | mes:tg:plan:list | 保养计划列表 |
| POST/PUT/DELETE /mes/tg/maintenance-plan | mes:tg:plan:* | 计划 CRUD |
| GET /mes/tg/maintenance-record/list | mes:tg:record:list | 保养记录列表 |
| POST /mes/tg/maintenance-record | mes:tg:record:add | 新增保养记录（触发归零） |
| GET /mes/tg/usage-record/list | mes:tg:usage:list | 使用记录列表 |

### 任务 5：前端 4 API + 4 页面

- `api/mes/tg/tooling.ts` / `maintenance-plan.ts` / `maintenance-record.ts` / `usage-record.ts`
- `views/mes/tg/tooling/index.vue` - 台账列表 + 新增/编辑 + 领用/归还/保养/报废按钮 + 寿命进度条
- `views/mes/tg/maintenance-plan/index.vue` - 保养计划 CRUD
- `views/mes/tg/maintenance-record/index.vue` - 保养记录列表
- `views/mes/tg/usage-record/index.vue` - 使用/领用记录列表

**全选择器化**（P0-9 第八/九批约定，无裸 ID 输入）：
- 工装：自建 `listTooling` 选择对话框
- 工单：`listWorkOrder`（取 `mesWoId`，对齐 lt/consumption）
- 工位/车间：`WorkcenterSelect` 组件（或 ERP `bd/workshop`/`bd/workstation` API 内联对话框）
- 操作员/保养员：`UserSelect` 组件（或 `listUser`，对齐 lt/consumption）
- 工装编码：`el-switch` 启用自动生成 + `previewCode('MES_TOOLING_CODE')`（对齐 lt/lot）

**dict-tag**（无硬编码状态 map）：
- 状态列/查询/表单全用 `useDict('mes_tooling_status')` + `dict-tag`
- 类型用 `useDict('mes_tooling_type')`
- 保养触发类型 `useDict('tg_maintenance_trigger_type')`
- 保养类型 `useDict('tg_maintenance_type')`
- 使用状态 `useDict('tg_usage_status')`
- 删除一切 `statusLabel`/`statusTagType` 本地函数

### 任务 6：菜单 SQL

**File:** `doc/mes/sql/mes-p0-02-02-menu.sql`

父菜单 **新建 4205 工装管理 M 目录**（parent=4000 MES系统）。原计划误用 4003，本地 DB 核实 4003 不存在，设备实际挂在 4201/4202 下，故新建独立 M 目录对齐 equipment 模式。菜单 ID 段 **4205 + 4172-4195**（1 M + 4 C + 20 F = 25 条）。

> ⚠️ **ID 段冲突说明**：原计划 §7.3 给 P0-2 分配 `4150-4165`，但 P0-9 实际已用 `4152-4171`（见 `mes-p0-09-02-menu.sql`）。本方案改用 `4172-4199`，并提议下游 ID 段整体后移（见 §七 校正映射）。

### 任务 7：自动编码规则

**File:** `doc/mes/sql/mes-p0-02-03-autocode-tooling.sql`

镜像 `mes-p0-09-05-autocode-lot.sql` 结构：
- 规则 `MES_TOOLING_CODE`：`TG` + `yyyyMMdd` + 3位日循环流水号 → `TG20260810001`
- 三 part：FIXCHAR('TG') + NOWDATE(yyyyMMdd) + SERIALNO(3位, cycle_flag=Y, cycle_method=DAY)
- `rule_id`/`part_id` 动态 MAX+1 + `WHERE NOT EXISTS` 守护（两表混有雪花大 ID，不得硬编码）
- 目标库 ry-vue（`AutoCodeUtil` 为 `@DS("master")` = ry-vue）

### 任务 8：字典种子

**File:** `doc/mes/sql/mes-p0-02-04-tooling-dicts.sql`

镜像 `mes-p0-09-06-lot-dict.sql` 结构，5 个字典：

| 字典 | 值域 | list_class |
|------|------|-----------|
| `mes_tooling_status` | IDLE/IN_USE/MAINTENANCE/SCRAPPED | success/primary/warning/danger |
| `mes_tooling_type` | MOLD/FIXTURE/CUTTER/GAUGE/OTHER | - |
| `tg_maintenance_trigger_type` | BY_COUNT/BY_HOURS/PERIODIC | - |
| `tg_maintenance_type` | PLANNED/REPAIR | primary/warning |
| `tg_usage_status` | OUT/RETURNED | warning/success |

> 复用既有 `mes_enable_status`（ACTIVE/INACTIVE）用于保养计划启用状态？**不复用**--`enabled` 字段为 CHAR(1) '1'/'0'，前端 el-switch 直接绑 `active-value="1"`，与布尔标志语义一致；`mes_enable_status` 是 VARCHAR 字符串字典，极性不符。保持 `enabled CHAR(1)` + el-switch（同 eq 模块既有写法）。

---

## 五、端到端链路

```
工装台账:
  新建工装(TG-20260810-001/模具A/MOLD/车间X/总寿命10000次)
    -> 状态 IDLE, used_count=0

领用:
  操作员领用 -> checkout(toolingId, operatorId, woId, workstationId)
    -> 状态 IDLE -> IN_USE
    -> 写 tg_usage_record(status=OUT, checkout_time=now)

归还:
  归还(usageCount=500, usageHours=2.5) -> doReturn(toolingId, 500, 2.5)
    -> 状态 IN_USE -> IDLE
    -> 更新 usage_record(status=RETURNED, return_time=now, usage_count=500, usage_hours=2.5)
    -> 累加 tg_tooling.used_count(500) / used_hours(2.5)
    -> 检查保养到期: BY_COUNT 阈值 1000, used_count(500) < 1000 -> 不到期

保养到期预警:
  持续领用归还至 used_count=1000 -> listMaintenanceDue() 返回该工装
    -> 看板黄色预警 + 站内消息(可选, 后续)

开始保养:
  startMaintain(toolingId) -> 状态 IDLE -> MAINTENANCE

完成保养(归零):
  completeMaintain(toolingId, planId, PLANNED, 1000, 5.0, maintainerId, "常规保养", 200)
    -> 状态 MAINTENANCE -> IDLE
    -> 归零 tg_tooling.used_count=0, used_hours=0, last_maintain_time=now
    -> 写 tg_maintenance_record(used_count_before=1000, used_count_after=0, ...)

报废:
  寿命到期或损坏 -> scrap(toolingId, "模具裂纹")
    -> 状态 -> SCRAPPED (终态, 守卫: IN_USE 需先归还)

P0-3 进站校验(契约):
  进站时 -> checkToolingAvailable(toolingId)
    -> 返回 {usable: true/false, reason, tooling}
    -> 校验: 存在 / 状态=IDLE / 未到寿命 / 非保养中
```

---

## 六、DDL 详情

### 6.1 tg_tooling - 工装台账

```sql
USE `ry-mes`;

CREATE TABLE IF NOT EXISTS tg_tooling (
    tooling_id        BIGINT NOT NULL COMMENT '工装ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_code      VARCHAR(64) NOT NULL COMMENT '工装编码',
    tooling_name      VARCHAR(128) NOT NULL COMMENT '工装名称',
    tooling_type      VARCHAR(16) NOT NULL COMMENT 'MOLD/FIXTURE/CUTTER/GAUGE/OTHER',
    spec              VARCHAR(128) COMMENT '规格',
    workshop_id       BIGINT COMMENT '所属车间ID',
    workshop_name     VARCHAR(128) COMMENT '车间名称(冗余)',
    status            VARCHAR(16) DEFAULT 'IDLE' COMMENT 'IDLE/IN_USE/MAINTENANCE/SCRAPPED',
    total_life_count  INT COMMENT '总寿命(次数)',
    total_life_hours  DECIMAL(14,2) COMMENT '总寿命(小时)',
    used_count        INT DEFAULT 0 COMMENT '已使用次数',
    used_hours        DECIMAL(14,2) DEFAULT 0 COMMENT '已使用时长',
    last_maintain_time DATETIME COMMENT '上次保养时间',
    purchase_date     DATE COMMENT '采购日期',
    purchase_cost     DECIMAL(14,2) COMMENT '采购成本',
    remark            VARCHAR(500) COMMENT '备注',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (tooling_id),
    UNIQUE INDEX uk_tooling_code (tooling_code),
    INDEX idx_status (status),
    INDEX idx_type (tooling_type),
    INDEX idx_workshop (workshop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES工装台账';
```

### 6.2 tg_maintenance_plan - 工装保养计划

```sql
CREATE TABLE IF NOT EXISTS tg_maintenance_plan (
    plan_id           BIGINT NOT NULL COMMENT '计划ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id        BIGINT NOT NULL COMMENT '工装ID',
    plan_name         VARCHAR(128) NOT NULL COMMENT '计划名称',
    trigger_type      VARCHAR(16) NOT NULL COMMENT 'BY_COUNT/BY_HOURS/PERIODIC',
    trigger_value     INT COMMENT '触发阈值(次数/小时/天数)',
    last_maintain_time DATETIME COMMENT '上次保养时间',
    next_maintain_time DATETIME COMMENT '下次保养时间(PERIODIC)',
    next_maintain_count INT COMMENT '下次保养次数阈值(BY_COUNT)',
    next_maintain_hours DECIMAL(14,2) COMMENT '下次保养时长阈值(BY_HOURS)',
    enabled           CHAR(1) DEFAULT '1' COMMENT '是否启用(1/0)',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (plan_id),
    INDEX idx_tooling_id (tooling_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装保养计划';
```

### 6.3 tg_maintenance_record - 工装保养记录

```sql
CREATE TABLE IF NOT EXISTS tg_maintenance_record (
    record_id         BIGINT NOT NULL COMMENT '记录ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id        BIGINT NOT NULL COMMENT '工装ID',
    plan_id           BIGINT COMMENT '关联计划ID',
    maintain_type     VARCHAR(16) COMMENT 'PLANNED/REPAIR',
    maintain_time     DATETIME NOT NULL COMMENT '保养时间',
    maintainer_id     BIGINT COMMENT '保养人ID',
    maintainer_name   VARCHAR(64) COMMENT '保养人姓名(冗余)',
    used_count_before INT COMMENT '保养前次数',
    used_count_after  INT COMMENT '保养后次数(归零=0)',
    used_hours_before DECIMAL(14,2) COMMENT '保养前时长',
    used_hours_after  DECIMAL(14,2) COMMENT '保养后时长(归零=0)',
    description       VARCHAR(500) COMMENT '保养内容',
    cost              DECIMAL(14,2) COMMENT '保养成本',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (record_id),
    INDEX idx_tooling_id (tooling_id),
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装保养记录';
```

### 6.4 tg_usage_record - 工装使用/领用记录（合并 checkout）

```sql
CREATE TABLE IF NOT EXISTS tg_usage_record (
    usage_id          BIGINT NOT NULL COMMENT '使用记录ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    tooling_id        BIGINT NOT NULL COMMENT '工装ID',
    wo_id             BIGINT COMMENT '工单ID',
    wo_no             VARCHAR(64) COMMENT '工单编号(冗余)',
    workstation_id    BIGINT COMMENT '工位ID',
    workstation_name  VARCHAR(128) COMMENT '工位名称(冗余)',
    operator_id       BIGINT COMMENT '操作员ID',
    operator_name     VARCHAR(64) COMMENT '操作员姓名(冗余)',
    checkout_time     DATETIME NOT NULL COMMENT '领用时间',
    return_time       DATETIME COMMENT '归还时间',
    usage_count       INT DEFAULT 1 COMMENT '使用次数',
    usage_hours       DECIMAL(14,2) COMMENT '使用时长',
    status            VARCHAR(16) DEFAULT 'OUT' COMMENT 'OUT/RETURNED',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (usage_id),
    INDEX idx_tooling_id (tooling_id),
    INDEX idx_wo_id (wo_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装使用领用记录';
```

---

## 七、菜单 ID 段校正映射（覆盖计划 §7.3）

P0-9 实际占用 `4006`(主数据目录) + `4152-4171`，原 §7.3 的 P0-2 段 `4150-4165` 冲突。校正后：

| Phase | 模块 | 原分配 | 校正分配 | 父菜单 |
|-------|------|--------|----------|--------|
| 4-A | P0-9 批次 | 4166-4175 | **4006+4152-4171**（已落地） | 4006 主数据 |
| 4-A | **P0-2 工装** | 4150-4165 | **4205 + 4172-4195**（25 条） | 4205 工装管理（新建 M 目录） |
| 4-B | P0-3 进站出站 | 4176-4180 | **4200-4215** | 4001 生产执行 |
| 4-B | P0-1 物料拉动 | 4181-4195 | **4216-4240** | 4006 物料管理(新建) |
| 4-B | P0-8 IoT 打通 | - | 扩展现有 4004 | 4004 数据采集 |
| 4-C | P0-4 NCR | 4196-4210 | **4241-4260** | 4002 质量管理 |
| 4-C | P0-5 E-SOP | 4211-4220 | **4261-4280** | 文档管理(新建) |
| 4-C | P0-6 电子签名 | 4221-4230 | **4281-4300** | 系统管理下 |

> 校正原则：每模块预留 20-25 ID 余量（C 页 + F 按钮 + 未来动作按钮），避免再次溢出冲突。**建议同步更新主计划 §7.3。**

---

## 八、风险与注意事项

| 风险 | 应对 |
|------|------|
| 寿命累加并发（多工单同时归还同一工装） | `@Lock4j(keys = {"#toolingId"})` 锁定工装（对齐 `LtLotServiceImpl.consume`） |
| 保养归零与正在进行的领用冲突 | `completeMaintain` 守卫状态=MAINTENANCE（CAS），IN_USE 不可直接保养 |
| 报废时工装被领用中 | `scrap` 守卫状态≠IN_USE，需先归还；CAS 更新防并发 |
| 删除孤儿断裂追溯 | `deleteByIds` 查 `tg_usage_record`/`tg_maintenance_record`/`tg_maintenance_plan` 是否存在，存在则抛异常（对齐 P0-9 A3） |
| 保养到期检测性能 | `listMaintenanceDue` 走 `idx_status` + 内存比较阈值；不建 SnailJob（PERIODIC 类未来可复用 eq `MaintenanceDueTask` 模式，本周不做） |
| 工装与设备概念混淆 | tg 工装（寿命型/可领用）独立于 eq 设备（机器/固定资产）；UI 上同挂 4003 设备管理父菜单，但子目录分开 |
| 跨库查 workshop/workstation | 冗余 `workshop_name`/`workstation_name`，创建时由前端选择器带入选填 |
| `enabled` 字段 dict 化误用 | `enabled CHAR(1)` + el-switch，**不用** `mes_enable_status` 字典（极性不符）；状态字段才 dict-tag |
| 自动编码规则未入库致生成失败 | DDL 执行顺序：先 `mes-p0-02-03-autocode-tooling.sql`（ry-vue）再联调；`genSerialCode` 找不到规则会抛异常 |
| 状态机校验失败 | 所有转换 `.eq(status, currentStatus)` + rows==0 抛异常（对齐 P0-9） |

---

## 九、明确排除项（本周不做）

- **SnailJob 定时保养到期推送**：本周用查询式预警（`listMaintenanceDue` + UI 看板），PERIODIC 类定时推送复用 eq `MaintenanceDueTask` 模式列为后续增强
- **工装与 P0-3 进站校验深度集成**：仅提供契约方法 `checkToolingAvailable`，P0-3 落地后接入
- **工装条码/二维码生成与打印**：后续 PDA 端
- **工装与 P0-1 物料拉动的工装配套**：FEFO 物料拉动的工装配套待 P0-1 实施
- **工装维修（REPAIR）独立流程**：本周 `maintain_type=REPAIR` 仅作记录标记，不走独立维修工单（设备维修 eq_repair 已覆盖机器维修，工装维修简化为保养记录）
- **多车间调拨**：工装跨车间调拨流转，v1.1
- **工装 BOM/组件管理**：模具子零件管理，v1.1

---

## 十、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `mes-p0-02-01-tooling.sql` | SQL | `doc/mes/sql/mes-p0-02-01-tooling.sql` |
| `mes-p0-02-02-menu.sql` | SQL | `doc/mes/sql/mes-p0-02-02-menu.sql` |
| `mes-p0-02-03-autocode-tooling.sql` | SQL | `doc/mes/sql/mes-p0-02-03-autocode-tooling.sql` |
| `mes-p0-02-04-tooling-dicts.sql` | SQL | `doc/mes/sql/mes-p0-02-04-tooling-dicts.sql` |
| TgTooling/TgMaintenancePlan/TgMaintenanceRecord/TgUsageRecord Entity/BO/VO/Mapper | Java×16 | `ruoyi-mes/.../tg/{domain,domain/bo,domain/vo,mapper}/` |
| 4 Service + 4 实现 | Java×8 | `ruoyi-mes/.../tg/{service,service/impl}/` |
| 4 Controller | Java×4 | `ruoyi-mes/.../tg/controller/` |
| `tooling.ts`/`maintenance-plan.ts`/`maintenance-record.ts`/`usage-record.ts` | TS×4 | `plus-ui/src/api/mes/tg/` |
| `tooling/index.vue`/`maintenance-plan/index.vue`/`maintenance-record/index.vue`/`usage-record/index.vue` | Vue×4 | `plus-ui/src/views/mes/tg/` |

---

## 十一、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes` 两库 + Redis 就绪。

1. **DDL 执行**：ry-mes 建 4 表；ry-vue 执行 autocode 规则 + 5 字典 + 菜单 SQL（4172-4199）
2. **工装创建**：新增工装（TG-20260810-001/模具A/MOLD/车间X/总寿命1000），编码自动生成，状态 IDLE
3. **领用**：调用 `/checkout/{toolingId}`（operatorId/woId/workstationId），状态 IDLE→IN_USE，写 usage_record(OUT)
4. **归还**：调用 `/return/{toolingId}`（usageCount=500, usageHours=2.5），状态 IN_USE→IDLE，used_count=500，usage_record(RETURNED)
5. **保养到期**：持续领用归还至 used_count≥1000，`/due` 返回该工装
6. **保养**：`/maintain/start` IDLE→MAINTENANCE；`/maintain/complete` MAINTENANCE→IDLE，used_count 归零，写 maintenance_record(before=1000/after=0)
7. **报废**：`/scrap/{toolingId}`，状态→SCRAPPED；IN_USE 态报废被拒（先归还）
8. **删除守卫**：有使用记录的工删删除被拒
9. **契约**：`/check/{toolingId}` 返回 `{usable, reason, tooling}`，IDLE 可用 / IN_USE 不可用
10. **静态**：`mvn -pl ruoyi-modules/ruoyi-mes -am compile` + `eslint`（tg 4 文件）均通过；vue-tsc 仅基线噪声（grep 自查，参考 memory `vue-tsc-baseline-noisy`）

---

## 十二、与主计划的对齐项（建议同步更新）

执行本方案时，建议同步修订 [主计划 v1.0](./2026-07-24-mes-p0-development-plan-v1.0.md)：

1. **§3.2.3 DDL**：5 表 → 4 表（`tg_checkout_record` 并入 `tg_usage_record`），更新字段清单
2. **§7.3 菜单 ID 段**：按 §七 校正映射整体后移
3. **§3.9 P0-9 状态**：补记 07-25 整改（A1-A6 + B1-B5）+ 前端 9 批打磨（38 页选择器化 + 17 字典）
4. **§3.7 P0-7 范围**：补入 `lt` 模块测试（P0-9 已落地，FEFO/状态机/事件绑定/IQC 钩子需覆盖）
5. **§5.2 时间线**：P0-9 实际 07-24~07-26 完成（早于 08-03），Phase 4-A 已提前启动

---

**文档生成时间**: 2026-07-26
**编制者**: AI Agent
**审核状态**: 📝 待执行（P0-9 已提前完工，可在 Week 29 计划起始前启动）
