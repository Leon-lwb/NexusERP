# BOM 优化 — ERP(成本 BOM) + MES(制造 BOM) 融合变更说明

> **文档编号**: NexusERP-CHG-2026-002
> **变更日期**: 2026-06-25
> **关联文档**: `doc/BOM管理增强-需求变更文档.md`（Phase 1-5 已实现）
> **变更负责人**: 王欢
> **基于审查**: Phase 1-5 代码审查 + 融合可行性评估

---

## 一、变更背景

BOM 管理增强需求变更文档（Phase 1-5）的功能已实现并编译通过，但经系统性审查发现三个核心问题，导致系统无法形成「ERP 维护、MES 使用」的融合闭环：

1. **MES 完全未消费 BOM**：`MesErpBridgeService.getBomLines` 是死代码；工单 `bomId` 存了但从不用；无 BOM 展开/快照/追溯。工艺路线在工单创建时做了快照，BOM 侧是空壳。
2. **职责错位**：制造执行字段（`bd_bom_detail.process_id`/`routing_step_id`/`workstation_id`）建在 ERP 侧，但制造工序/工位关联是 MES 的职责。与用户明确的职责划分冲突——**ERP BOM = 成本/物料（用量、损耗、成本滚算）**，**MES BOM = 制造（子件→工序→工位/作业内容）**。
3. **ERP 数据正确性隐患**：引用一致性无校验、VO 富集字段未填充（导致 MES 只读视图空白）、多「当前版本」会让 `selectOne` 报错、删除路线/工序会留孤立引用、`explodeBom` 不聚合重复叶子、无成本滚算、路由步骤无法持久化。

---

## 二、变更概述

| 阶段 | 内容 | 涉及模块 |
|------|------|----------|
| Phase 1 | ERP 侧清理 + 成本滚算 + 正确性加固 | ruoyi-erp |
| Phase 2 | MES 制造 BOM(MBOM) 子系统 | ruoyi-mes |
| Phase 3 | 工单下达 MBOM 快照 + 组件追溯 | ruoyi-mes |
| Phase 4 | 融合关键前端 | plus-ui |
| 审查修复 | 高/中优先级 bug 修复（Map key 大小写、null 安全等） | ruoyi-mes, ruoyi-erp |

---

## 三、架构决策

| 决策点 | 结论 |
|--------|------|
| 制造 BOM(MBOM) 归属 | MES 侧新建 `mes_mbom`/`mes_mbom_line`，ERP `bd_bom_detail` 退回纯物料+用量+成本 |
| `bd_bom_detail.operation_step` | 保留（软引用路线步骤序号），前端做联动下拉；制造关联权威在 MES MBOM |
| 成本滚算 | 本次实现完整版：物料成本（标准成本×累计用量）+ 工序成本（工时×费率） |
| `bd_routing_step` 费率 | 新增 `labor_rate`/`machine_rate` 字段，前端路线表单可录入 |
| 工单快照策略 | 与工艺路线一致：工单下达时快照 MBOM 到 `pro_work_order_bom_line`，隔离后续变更 |
| MBOM 编制模型 | 1 个 ERP BOM 对应 1 个 MBOM（`mes_mbom.bom_id` UNIQUE），专用 MES 页面编制 |
| 追溯粒度 | 物料级 + SN 装配级（基于 `pro_sn.parent_sn_id`）；批次/批号级需未来组件消费记录表，本次不做 |
| 前端范围 | 仅融合关键 UI（不做体验增强项：替代料内联、Where-Used 物料入口、路线可视化预览等） |

---

## 四、后端变更明细

### 4.1 Phase 1：ERP 侧

#### 4.1.1 `bd_bom_detail` 退回纯物料+用量+成本

**删除字段**：`workstation_id`、`process_id`、`routing_step_id`
**保留字段**：`operation_step`（Integer，软引用路线步骤序号）

| 文件 | 变更 |
|------|------|
| `BdBomDetail.java` | 删除 3 个字段，`operation_step` 加注释说明职责迁移 |
| `BdBomDetailBo.java` | 删除 4 个字段（含 `workstationName`） |
| `BdBomDetailVo.java` | 删除 6 个字段（含 `processCode`/`processName`/`workstationName`） |
| `BdBomServiceImpl.java` — `copyBom` | 删除复制 `workstationId`/`processId`/`routingStepId` 的 3 行 |
| `BdBomServiceImpl.java` — `compareBom` | 删除 `isProcessChanged`/`isRoutingStepChanged` 比较与变更详情 |
| `BdBomDetailServiceImpl.java` — `buildQueryWrapper` | 删除 `workstationId` 查询条件 |
| `PrdMaterialRequirementServiceImpl.java` | 删除 `setWorkstationId`/`setWorkstationName`（已无数据源） |
| `PrdProductionPlanServiceImpl.java` | 同上 |

#### 4.1.2 BOM 成本滚算

**新文件**：
- `BomCostRollupVo.java` — 成本卷积结果 VO（含 `MaterialCostLine`/`RoutingCostLine` 内部类）

**变更文件**：

| 文件 | 变更 |
|------|------|
| `IndentedBomLineVo.java` | 新增 `unitCost`、`lineCost` 字段 |
| `BdBomServiceImpl.java` — `buildIndentedLines` | 在物料查询处计算 `unitCost`（标准成本或参考价）和 `lineCost`（= unitCost × cumulativeQuantity） |
| `BdBomServiceImpl.java` — `costRollup` | **新增**：复用 `explodeBom` 聚合叶子求 `totalMaterialCost`；遍历路由步骤计算 `totalRoutingCost` |
| `BdBomServiceImpl.java` — `computeStepCost` | **新增**：`stepCost = standardHours × (laborRate × laborCount + machineRate × machineCount)` |
| `BdBomServiceImpl.java` — `materialCost` | **新增**：优先 `standardCost`，其次 `referencePrice`，均空返回 0 |
| `BdBomController.java` | 新增 `GET /costRollup/{bomId}`，权限 `bd:bom:query` |
| `IBdBomService.java` | 新增 `costRollup(Long bomId)` 接口方法 |

**成本公式**：
- 物料成本：`Σ(叶子物料标准成本 × 累计用量含损耗)`，已按 materialId 聚合
- 工序成本：`Σ(标准工时 × (人工费率 × 人数 + 机器费率 × 机台数))`
- `routingCostAvailable=false` 当无路由、或任一步骤费率缺失时

#### 4.1.3 VO 富集

| 文件 | 变更 |
|------|------|
| `BdBomServiceImpl.java` — `enrichBomVo` | **新增**：批量加载父项物料编码/名称、路线编码/名称到 `BdBomVo` |
| `BdBomDetailServiceImpl.java` — `enrichMaterialName` | 扩展：从 `BdMaterialVo` 补填 `childMaterialCode`/`childMaterialSpec`/`baseUomId`/`baseUomName` |

#### 4.1.4 多「当前版本」防护

| 文件 | 变更 |
|------|------|
| `BdBomServiceImpl.java` — `insertByBo`/`updateByBo` | 若 `isCurrent==1`，先调 `clearCurrentFlag` 置同父项其他 BOM 为非当前 |
| `BdBomServiceImpl.java` — `clearCurrentFlag` | **新增**：`UPDATE bd_bom SET is_current=0 WHERE parent_material_id=? AND is_current=1` |
| `ry-erp-bom-cleanup.sql` | `bd_bom.is_current` 默认值从 1 改为 0 |

#### 4.1.5 `explodeBom` 叶子聚合

`BdBomServiceImpl.java` — `explodeBom`：过滤 PART 节点后按 `materialId` 聚合，合并 `cumulativeQuantity` 和 `lineCost`。同一叶子在多分支出现时归为一行。

#### 4.1.6 级联删除保护

| 文件 | 变更 |
|------|------|
| `BdProcessServiceImpl.java` — `deleteByIds` | 删前查 `bd_routing_step.process_id` 引用，命中则拒绝 |
| `BdRoutingServiceImpl.java` — `deleteByIds` | 删前查 `bd_bom.routing_id` 引用，命中则拒绝 |

#### 4.1.7 工序费率字段 + 路由步骤持久化修复

| 文件 | 变更 |
|------|------|
| `BdRoutingStep.java` | 新增 `laborRate`/`machineRate` 字段 |
| `BdRoutingStepVo.java` | 同上 |
| `BdRoutingStepBo.java` | **新增文件**：路由步骤 BO |
| `BdRoutingBo.java` | 新增 `List<BdRoutingStepBo> steps` 字段 |
| `BdRoutingServiceImpl.java` — `insertByBo`/`updateByBo` | 改为调用 `saveSteps`（先删后插）+ `computeTotals`（自动算总步数/总工时） |
| `BdRoutingServiceImpl.java` — `saveSteps`/`computeTotals` | **新增** |

> **修复预存缺陷**：路由表单保存步骤时，原实现从未持久化步骤数据（`insertByBo`/`updateByBo` 只保存路由头）。本次将步骤列表嵌入 `BdRoutingBo.steps`，随路由一起保存。

---

### 4.2 Phase 2：MES MBOM 子系统

#### 4.2.1 数据库新增

**脚本**：`doc/mes/sql/archive/mes-week28-09-mbom.sql`（`USE ry-mes`）

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `mes_mbom` | 制造 BOM 头（1:1 关联 ERP bom_id） | `mbom_id`(PK), `bom_id`(UNIQUE), `routing_id`, `mbom_version`, `mbom_status`(DRAFT/ACTIVE/FROZEN) |
| `mes_mbom_line` | 制造 BOM 明细 | `mbom_line_id`(PK), `mbom_id`, `bom_detail_id`(可空), `child_material_id`, `quantity_per_parent`, `loss_rate`, `routing_step_id`, `process_id`, `workstation_id`, `operation_content`, `backflush_flag` |
| `pro_work_order_bom_line` | 工单 MBOM 快照 | `wo_bom_line_id`(PK), `mes_wo_id`, `mbom_line_id`, 冗余物料/工序/工位编码名称, `total_quantity`(含损耗用量) |

#### 4.2.2 新增代码

**域对象**（`com.nexus.mes.pro`）：

| 类型 | 文件 |
|------|------|
| 实体 | `MesMbom.java`, `MesMbomLine.java`, `ProWorkOrderBomLine.java` |
| BO | `MesMbomBo.java`(含嵌套 `List<MesMbomLineBo> lines`), `MesMbomLineBo.java` |
| VO | `MesMbomVo.java`(富集 bomVersion/parentMaterialCode/routingCode + lines), `MesMbomLineVo.java`(富集物料/工序/工位编码名称/routingStepSequence), `ProWorkOrderBomLineVo.java` |
| Mapper | `MesMbomMapper.java`, `MesMbomLineMapper.java`, `ProWorkOrderBomLineMapper.java` |

**Service**：

| 文件 | 关键方法 |
|------|----------|
| `IMesMbomService.java` | `selectPageList`, `selectById`, `selectByBomId`(1:1), `insertByBo`, `updateByBo`, `deleteByIds`, `activate`, `verify` |
| `MesMbomServiceImpl.java` | 保存时经 Bridge 校验 `routingStepId` 归属 MBOM 的 `routing_id`；删除时拒绝被在制工单快照引用；富集时查 ERP 物料/工位/路由步骤 |

**Controller**：

| 端点 | 权限 | 说明 |
|------|------|------|
| `GET /mes/pro/mbom/list` | `mes:pro:mbom:list` | 分页查询 |
| `GET /mes/pro/mbom/{mbomId}` | `mes:pro:mbom:query` | 详情（含 lines + 富集） |
| `GET /mes/pro/mbom/byBom/{bomId}` | `mes:pro:mbom:query` | 按 ERP BOM ID 查（1:1） |
| `POST /mes/pro/mbom` | `mes:pro:mbom:add` | 新增 |
| `PUT /mes/pro/mbom` | `mes:pro:mbom:edit` | 修改 |
| `DELETE /mes/pro/mbom/{ids}` | `mes:pro:mbom:remove` | 删除 |
| `PUT /mes/pro/mbom/activate/{mbomId}` | `mes:pro:mbom:edit` | 激活 |
| `GET /mes/pro/mbom/verify/{mbomId}` | `mes:pro:mbom:query` | 校验 |

#### 4.2.3 Bridge 扩展

| 方法 | 说明 |
|------|------|
| `getBomHeader(bomId)` | 查询 ERP BOM 头（含父项物料编码/名称/版本/路线） |
| `getBomDetails(bomId)` | 查询 ERP BOM 明细（纯物料+用量，替代已删除的 `getBomLines`） |
| `getMaterialFull(materialId)` | 查询 ERP 物料（含 `standard_cost`/`base_uom_name`） |
| `getRoutingHeader(routingId)` | 查询 ERP 路线头（含编码/名称/版本/物料） |
| `getWorkstationFull(workstationId)` | 查询 ERP 工位（编码/名称） |

**删除死代码**：`ErpBomLineMapper.java`、Bridge 接口/实现中的 `getBomLines`。

**新增只读 Mapper**：`ErpBomDetailMapper`、`ErpBomHeaderMapper`、`ErpRoutingHeaderMapper`。

---

### 4.3 Phase 3：工单快照 + 追溯

| 文件 | 变更 |
|------|------|
| `ProWorkOrderVo.java` | 新增 `bomLines`/`routingCode`/`routingName`/`bomVersion` 字段 |
| `ProWorkOrderServiceImpl.java` — `selectById` | 加载 `pro_work_order_bom_line` + 富集路线/BOM 信息 |
| `ProWorkOrderServiceImpl.java` — `createFromErpMessage` | 事务外读取 MBOM → 传入 `doCreateWorkOrderAndSteps` → 调用 `snapshotBomLines` |
| `ProWorkOrderServiceImpl.java` — `release` | 重构为 `doReleaseAndSnapshot`：事务外读 Bridge（路由+MBOM），事务内 CAS 更新 + 幂等快照 |
| `ProWorkOrderServiceImpl.java` — `snapshotBomLines` | **新增**：将 MBOM 行快照到 `pro_work_order_bom_line`，冗余 code/name，计算 `total_quantity` |

**追溯服务**：

| 文件 | 说明 |
|------|------|
| `IProTraceabilityService.java` | `backward(mesWoId, snCode)`：成品→组件清单+装配 SN；`forward(materialId, snCode)`：组件→消耗工单 |
| `ProTraceabilityServiceImpl.java` | 基于 `pro_work_order_bom_line` 快照 + `pro_sn.parent_sn_id` 链 |
| `ProTraceabilityController.java` | `GET /mes/pro/trace/backward`, `GET /mes/pro/trace/forward`，权限 `mes:pro:trace:query` |

---

### 4.4 菜单权限

**脚本**：`doc/mes/sql/archive/mes-week28-22-menu.sql`

| 菜单 ID | 名称 | 父菜单 | 权限 |
|---------|------|--------|------|
| 2950 | MBOM 管理 | 4001（生产执行） | `mes:pro:mbom:list` |
| 2951–2954 | MBOM 查询/新增/修改/删除 | 2950 | `mes:pro:mbom:query/add/edit/remove` |

> 组件追溯菜单（原计划 2980）已移除——现有 SN 追溯菜单（4080，week24）已作为追溯入口，后端追溯 API 端点（`/mes/pro/trace/backward`、`/mes/pro/trace/forward`，权限 `mes:pro:trace:query`）已就绪。

---

## 五、前端变更明细

### 5.1 ERP BOM 侧

| 文件 | 变更 |
|------|------|
| `api/erp/bd/bom/types.ts` | `BomDetailVO`/`BomDetailForm` 移除 `workstationId`/`processId`/`routingStepId` 等字段；`IndentedBomLineVo` 加 `unitCost`/`lineCost`；新增 `BomCostRollupVo`/`MaterialCostLine`/`RoutingCostLine` |
| `api/erp/bd/bom/index.ts` | 新增 `costRollup(bomId)` API |
| `views/erp/bd/bom/form.vue` | operationStep 列从 `el-input-number` 改为 `el-select`（联动路由步骤下拉）；新增「成本卷积」Tab（汇总卡片 + 物料明细表 + 工序明细表）；缩进表新增单位成本/行成本列 |

### 5.2 MES 侧

| 文件 | 变更 |
|------|------|
| `api/mes/pro/types.ts` | 新增 `ProWorkOrderBomLineVO`/`MesMbomVO`/`MesMbomLineVO`/`MesMbomForm`/`MesMbomLineForm`/`MesMbomQuery`；`ProWorkOrderVO` 加 `bomLines`/`routingCode`/`routingName`/`bomVersion` |
| `api/mes/pro/mbom.ts` | **新增**：MBOM CRUD + activate + verify API |
| `views/mes/pro/workorder/form.vue` | 新增 BOM + 工艺路线 `el-select`（并行加载选项） |
| `views/mes/pro/workorder/detail.vue` | raw `routingId` → 路线编码/名称；新增「物料明细(MBOM)」Tab |
| `views/mes/md/bom/index.vue` | 修复字段错配：`bomCode`→`bomVersion`，`materialCode`→`parentMaterialCode`，status 从 `=== '0'` 改为 `Number() === 1` |
| `views/mes/md/routing/index.vue` | 修复：`version`→`routingVersion`，status 修复 |
| `views/mes/pro/mbom/index.vue` | **新增**：MBOM 列表页 + 编辑对话框（ERP BOM 选择器、路由步骤/工位下拉、作业内容、倒冲开关） |

---

## 六、数据库脚本清单

| 脚本 | 库 | 说明 |
|------|----|------|
| `doc/erp/sql/archive/erp-bom-cleanup.sql` | ry-erp | DROP `bd_bom_detail.workstation_id`/`process_id`/`routing_step_id`；`bd_bom.is_current` 默认值改 0；`bd_routing_step` 加 `labor_rate`/`machine_rate` |
| `doc/mes/sql/archive/mes-week28-09-mbom.sql` | ry-mes | `mes_mbom`/`mes_mbom_line`/`pro_work_order_bom_line` 三表（幂等：`DROP TABLE IF EXISTS`） |
| `doc/mes/sql/archive/mes-week28-22-menu.sql` | ry-vue | MBOM 菜单（2950–2954，parent 4001）（幂等：`INSERT IGNORE`）；组件追溯菜单已移除（现有 SN 追溯菜单 4080 已覆盖入口） |

---

## 七、审查修复记录

在完成开发后进行了系统性代码审查，发现并修复了以下问题：

| 严重度 | 问题 | 修复 |
|--------|------|------|
| **高** | `MesMbomServiceImpl.enrichMbomVo`/`enrichLineVo` 使用 camelCase Map key（`"bomVersion"`），但 Bridge 返回 snake_case（`"bom_version"`） | 全部改为 snake_case key，与 `ProWorkOrderServiceImpl` 现有模式一致 |
| **高** | `MesMbomServiceImpl.validateAndSaveLines`/`verify` 使用 `m.get("stepId")`，但 Mapper 返回 `"step_id"` → NPE | 改为 `m.get("step_id")` |
| **中** | `costRollup`：无路由时 `routingCostAvailable=true` 且 `routingLines=null` | `routingAvailable` 初始化为 `false`，`routingLines` 始终初始化为空列表 |
| **中** | `MesMbomServiceImpl.enrichLineVo`：工序编码/名称、步骤序号富集未实现 | 新增 `enrichLinesWithRoutingSteps` 方法，在 `selectById` 中一次性加载路由步骤后匹配 |
| **中** | `MesMbomServiceImpl.updateByBo`：缺少 bomId 唯一校验（`insertByBo` 有但 `updateByBo` 没有） | 补充校验（排除自身） |
| **中** | 菜单 SQL `parent_id = 0` 导致 MBOM/追溯菜单成为顶级菜单 | 改为 `parent_id = 4001`（生产执行） |
| **中** | DDL `CREATE TABLE` 无 `IF NOT EXISTS`/`DROP TABLE IF EXISTS`，重复执行报错 | 加 `DROP TABLE IF EXISTS` |

**二轮审查修复：**

| 严重度 | 问题 | 修复 |
|--------|------|------|
| **高** | MBOM 编制页 `openMaterialSelectForLine` 完全不可用——`el-input readonly` + `$modal.confirm` 空壳，用户无法为行选择物料，`materialOptions` 加载后从未使用 | 替换为可用的 `el-select`（`filterable`，绑定 `childMaterialId`，选项来自 `materialOptions`），新增 `handleMaterialChangeForLine` 联动函数 |
| **中** | MBOM 编制页无行级校验——`rules` 仅校验 `bomId`，可提交 `childMaterialId: 0` 的空行 | `submitForm` 中逐行检查 `childMaterialId` 和 `quantityPerParent > 0` |
| **低** | `MesMbomServiceImpl.enrichLineVo()` 中 `stepCache` HashMap 声明但从未填充，步骤富集代码块（30+ 行）永远不执行，实际由 `enrichLinesWithRoutingSteps` 完成 | 清理死代码，`enrichLineVo` 仅保留物料/工位富集，步骤富集中文注释指向 `enrichLinesWithRoutingSteps` |

---

## 八、影响评估

### 8.1 向后兼容

- `bd_bom_detail` 删除 3 列是破坏性变更：旧版 `erp-bom-routing-enhancement.sql` 与此不兼容，新部署以 `ry-erp.sql` + `ry-erp-bom-cleanup.sql` 为准
- `bd_bom.is_current` 默认值从 1 改为 0：新创建的 BOM 不再自动成为当前版本（需手动激活），更安全但行为变化
- 删除 `ErpBomLineMapper`：如已有外部代码调用 `MesErpBridgeService.getBomLines`，需迁移至 `getBomDetails`

### 8.2 新增能力

- ERP BOM 完整成本滚算（物料 + 工序）
- MES 制造 BOM 编制（子件→工序→工位→作业内容）
- 工单下达 MBOM 快照（隔离后续变更）
- 组件正反向追溯（物料级 + SN 装配级）
- 路由步骤持久化修复 + 费率录入
- ERP/MES 只读视图修复

### 8.3 回滚方案

1. 数据库：DROP 新增 3 表（`mes_mbom`/`mes_mbom_line`/`pro_work_order_bom_line`）；ALTER ADD 回 `bd_bom_detail` 3 列；`bd_bom.is_current` 默认值改回 1；DELETE 菜单 2950–2954, 2980
2. 后端：回退 ruoyi-erp/ruoyi-mes 代码
3. 前端：回退 plus-ui 相关页面

---

## 九、后续开发项（已预留，未实现）

| 功能 | 预留方式 | 计划 |
|------|----------|------|
| ECN 工程变更管理 | DDL 注释字段 + 设计文档 | 后续 Phase |
| 虚拟件 / 模块件 | DDL 注释字段 + 设计文档 | 后续 Phase |
| 批次/批号级追溯 | 需组件消费记录表 | 后续 Phase |
| BOM 树内联替代料 | 前端体验增强 | 后续 Phase |
| 物料页 Where-Used 入口 | 前端体验增强 | 后续 Phase |
| 工艺路线可视化预览 | 前端体验增强 | 后续 Phase |
| N+1 查询优化（BOM 树/缩进递归） | 性能优化 | BOM 行数 >500 时考虑 |

---

**文档结束**
