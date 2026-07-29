# MES Phase 1 Week 21 开发实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施日期**: 2026-06-20
> **阶段**: MES Phase 1 收尾 — 前端功能补全 + 菜单权限清理 + Phase 1 端到端验证
> **状态**: 已审批

**Goal:** 补全 MES 工单/流转卡前端功能缺口（创建流转卡入口、关闭/完工流转卡按钮、工单详情 Tab 化、工单编辑弹窗），清理 Week 17 遗留的冗余/错配菜单权限，部署 Flowable BPMN 审批流，完成 Phase 1 端到端验证（ERP 审批 → MES 工单 → 审批 → 流转卡执行 → 完工 → MQ 回传 ERP）。

**Architecture:** Week 20 已提前完成原 Phase 2 Week 22 的流转卡引擎。本周按主计划（《NexusERP_MES文档》§10.1 / §15.1 Week 21）定位为 Phase 1 前端 + 冒烟验证收尾，不启动 Phase 2 派工（ProDispatch）。前端在现有 7 个 .vue 页面基础上增量改造，菜单 SQL 清理 Week 17 遗留的 4011-4017 冗余按钮并修正 4010 权限字符串错配。

**Tech Stack:** Vue 3.5 + Element Plus + TypeScript（前端）；T-SQL/MySQL（菜单清理 SQL）；Flowable 7.0（BPMN 部署，运行时 UI 操作）。

**前置条件：** Week 17-20 已完成 MES 模块骨架、`mes` 数据源、ERP 桥接、工单 CRUD + Flowable 回调 + MQ 双向联动、流转卡引擎（状态机 + 完工聚合）+ 流转卡前端骨架。

---

## 一、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | 流转卡前端功能补全（创建入口 + 关闭/完工按钮） | 高 | `workorder/detail.vue`、`flow-card/detail.vue` 修改 | 可从工单详情创建流转卡；流转卡详情可完工/关闭 |
| 2 | 工单详情 Tab 化重构（基本信息/工序步骤/审批流程） | 高 | `workorder/detail.vue` 重构 | 三个 Tab 切换正常，工序步骤为独立 Tab |
| 3 | 工单编辑弹窗 + 列表新增/修改按钮 | 中 | `workorder/form.vue` 新建 + `workorder/index.vue` 修改 | 可新增/编辑工单（手动创建场景） |
| 4 | 菜单权限清理 SQL | 高 | `mes-week21-15-menu.sql` | 4010 权限对齐 Controller；4011-4017 冗余按钮删除 |
| 5 | Flowable BPMN 审批流部署（运行时操作） | 高 | 部署 `mesWorkOrderApproval` 流程 | 工单详情审批流下拉可选中并提交 |
| 6 | Phase 1 端到端集成测试 + 报告 | 高 | `2026-06-19-mes-week21-verification-report.md` | 完整链路演示通过 |
| 7 | 编译验证 | 高 | `mvn compile` + `npm run dev` 通过 | 无编译/启动错误 |

---

## 二、详细任务分解

### 任务 1：流转卡前端功能补全

**Files:**
- Modify: `plus-ui/src/views/mes/pro/workorder/detail.vue` — 新增"创建流转卡"按钮
- Modify: `plus-ui/src/views/mes/pro/flow-card/detail.vue` — 新增"完工流转卡"/"关闭流转卡"按钮
- 复用已有 API: `addFlowCard`、`completeFlowCard`、`closeFlowCard`（`api/mes/pro/flowcardApi.ts` 已导出）

**1a. 工单详情页 — 创建流转卡入口**

在 `workorder/detail.vue` 头部区（与"返回"按钮同行）新增按钮，仅当 `woStatus === 'RELEASED' || woStatus === 'IN_PROGRESS'` 时显示，权限 `mes:pro:flowcard:add`：

```vue
<el-button type="success" icon="Stamp" @click="handleCreateFlowCard"
  v-if="workOrder.woStatus === 'RELEASED' || workOrder.woStatus === 'IN_PROGRESS'"
  v-hasPermi="['mes:pro:flowcard:add']">创建流转卡</el-button>
```

```ts
import { addFlowCard } from '@/api/mes/pro/flowcardApi';

const handleCreateFlowCard = async () => {
  const qty = await proxy?.$modal.prompt(
    `请输入本流转卡计划数量（工单计划数量 ${workOrder.value.plannedQty}）：`,
    '创建流转卡 - ' + workOrder.value.woNo
  );
  if (!qty || isNaN(Number(qty)) || Number(qty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  await addFlowCard(workOrder.value.mesWoId, Number(qty));
  proxy?.$modal.msgSuccess('流转卡创建成功');
  router.push(`/mes/pro/flow-card?mesWoId=${workOrder.value.mesWoId}`);
};
```

**1b. 流转卡详情页 — 完工/关闭按钮**

在 `flow-card/detail.vue` 头部（与"返回"同行）新增两个按钮：

```vue
<el-button style="float: right; margin-right: 8px" type="warning" plain icon="CircleCheck"
  @click="handleCompleteCard"
  v-if="flowCard.cardStatus === 'IN_PROGRESS'"
  v-hasPermi="['mes:pro:flowcard:complete']">完工流转卡</el-button>
<el-button style="float: right; margin-right: 8px" type="info" plain icon="Lock"
  @click="handleCloseCard"
  v-if="flowCard.cardStatus === 'COMPLETED'"
  v-hasPermi="['mes:pro:flowcard:edit']">关闭流转卡</el-button>
```

```ts
import { completeFlowCard, closeFlowCard } from '@/api/mes/pro/flowcardApi';

const handleCompleteCard = async () => {
  await proxy?.$modal.confirm('确认完成该流转卡？将聚合完工数量到工单。');
  await completeFlowCard(flowCard.value.cardId);
  proxy?.$modal.msgSuccess('流转卡完工成功');
  loadDetail();
};

const handleCloseCard = async () => {
  await proxy?.$modal.confirm('确认关闭该流转卡？关闭后不可再操作。');
  await closeFlowCard(flowCard.value.cardId);
  proxy?.$modal.msgSuccess('流转卡已关闭');
  loadDetail();
};
```

> **说明：** 后端 `completeStep` 在所有工序完成时已自动触发 `completeCard`，"完工流转卡"按钮用于异常补救/手动强制完工场景；"关闭流转卡"（COMPLETED→CLOSED）不会自动触发，必须人工关闭。

---

### 任务 2：工单详情 Tab 化重构

**Files:**
- Modify: `plus-ui/src/views/mes/pro/workorder/detail.vue`

将当前三段堆叠的 `el-card`（基本信息 `el-descriptions` / 审批操作 / 工序步骤）重构为 `el-tabs`，对齐主计划"工单详情页(含工序列表Tab)"要求。头部卡片（标题 + 返回 + 创建流转卡按钮）保留在 Tab 之外：

```vue
<el-card>
  <template #header>
    <div class="clearfix">
      <span>MES工单详情 - {{ workOrder.woNo }}</span>
      <div style="float: right">
        <el-button type="success" icon="Stamp" @click="handleCreateFlowCard"
          v-if="workOrder.woStatus === 'RELEASED' || workOrder.woStatus === 'IN_PROGRESS'"
          v-hasPermi="['mes:pro:flowcard:add']">创建流转卡</el-button>
        <el-button type="primary" plain @click="goBack">返回</el-button>
      </div>
    </div>
  </template>

  <el-tabs v-model="activeTab">
    <el-tab-pane label="基本信息" name="info">
      <el-descriptions :column="3" border>
        <!-- 原有 el-descriptions-item 全部迁入 -->
      </el-descriptions>
    </el-tab-pane>

    <el-tab-pane label="工序步骤" name="steps">
      <el-table :data="workOrder.steps" border>
        <!-- 原有工序步骤列迁入 -->
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="审批流程" name="approval">
      <!-- 原有审批操作 el-form 迁入 -->
    </el-tab-pane>
  </el-tabs>
</el-card>
```

```ts
const activeTab = ref('info');
```

> **约束：** 不改变任何业务逻辑与 API 调用，仅做布局重构。`loadDetail` / `loadFlowOptions` / `loadProcInsId` / `handleSubmitFlow` / `handleViewFlow` 保持不变。任务 1 的"创建流转卡"按钮并入此重构（位于 header 右侧）。

---

### 任务 3：工单编辑弹窗 + 列表新增/修改按钮

**Files:**
- Create: `plus-ui/src/views/mes/pro/workorder/form.vue`
- Modify: `plus-ui/src/views/mes/pro/workorder/index.vue`
- 复用已有 API: `addWorkOrder`、`updateWorkOrder`（`api/mes/pro/workorder.ts` 已导出）
- 复用已有类型: `ProWorkOrderForm`（`api/mes/pro/types.ts` 已定义）

**3a. form.vue — 新增/编辑弹窗**

`el-dialog` + `el-form`，字段：`woNo`（新增必填）、`materialId/materialCode`（复用 ERP 物料选择器 `@/api/erp/bd/material`）、`plannedQty`（必填>0）、`workshopId`、`priority`、`planStartDate`、`planEndDate`、`remark`。编辑模式回填，禁用 `woNo`/`materialId` 修改（工单由 ERP MQ 自动创建时物料已锁定）。

```vue
<el-dialog v-model="visible" :title="title" width="640px" append-to-body>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
    <el-form-item label="工单编号" prop="woNo">
      <el-input v-model="form.woNo" :disabled="form.mesWoId != null" placeholder="手动创建时填写" />
    </el-form-item>
    <el-form-item label="产品" prop="materialId">
      <!-- ERP 物料选择器, disabled when editing -->
    </el-form-item>
    <el-form-item label="计划数量" prop="plannedQty">
      <el-input-number v-model="form.plannedQty" :min="0.0001" :precision="4" />
    </el-form-item>
    <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" /></el-form-item>
    <el-form-item label="计划开工"><el-date-picker v-model="form.planStartDate" type="datetime" /></el-form-item>
    <el-form-item label="计划完工"><el-date-picker v-model="form.planEndDate" type="datetime" /></el-form-item>
    <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="close">取消</el-button>
    <el-button type="primary" @click="submit">确定</el-button>
  </template>
</el-dialog>
```

```ts
const submit = async () => {
  await formRef.value?.validate();
  if (form.value.mesWoId != null) {
    await updateWorkOrder(form.value);
  } else {
    await addWorkOrder(form.value);
  }
  proxy?.$modal.msgSuccess('保存成功');
  emit('refresh');
  close();
};
```

**3b. index.vue — 新增/修改按钮**

操作栏新增"新增"按钮（`mes:pro:work:add`）；表格操作列新增"修改"按钮（`mes:pro:work:edit`，仅 `CREATED` 状态可用）：

```vue
<el-button type="primary" plain icon="Plus" @click="handleAdd"
  v-hasPermi="['mes:pro:work:add']">新增</el-button>
```

```vue
<el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
  v-if="scope.row.woStatus === 'CREATED'" v-hasPermi="['mes:pro:work:edit']">修改</el-button>
```

```ts
import Form from './form.vue';
const formRef = ref();

const handleAdd = () => formRef.value?.open();
const handleUpdate = (row: ProWorkOrderVO) => formRef.value?.open(row);
```

> **说明：** MES 工单主路径是 ERP 审批后经 MQ 自动创建，手动新增为边缘场景；"修改"主要用于调整计划日期/优先级，仅限 `CREATED` 状态。

---

### 任务 4：菜单权限清理 SQL

**Files:**
- Create: `doc/mes/sql/archive/mes-week21-15-menu.sql`

**问题背景：** Week 17 创建的 4010（MES工单页面）权限字符串为 `mes:workorder:list`，但 `ProWorkOrderController` 实际使用 `mes:pro:work:list`（@SaCheckPermission）。若角色仅有 4010 的旧权限，菜单可见但列表 API 返回 403。Week 18 已补充 4018-4024（`mes:pro:work:*`）但未删除 4011-4017（`mes:workorder:*` 冗余按钮），两套并存。

```sql
-- =============================================
-- NexusERP - MES模块 Week 21 菜单权限清理SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 修正 Week17 遗留的权限字符串错配 + 删除冗余按钮
-- 创建时间: 2026-06-19
-- =============================================
USE `ry-vue`;

-- 1. 修正 MES工单页面(4010)权限字符串, 对齐 Controller mes:pro:work:list
UPDATE `sys_menu` SET `perms` = 'mes:pro:work:list'
WHERE `menu_id` = 4010 AND `perms` = 'mes:workorder:list';

-- 2. 删除 Week17 冗余的 mes:workorder:* 按钮权限(已被 Week18 的 4018~4023 替代)
DELETE FROM `sys_menu`
WHERE `menu_id` IN (4011, 4012, 4013, 4014, 4015, 4016, 4017);

-- 3. 同步清理角色-菜单关联表中 4011~4017 的残留分配
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (4011, 4012, 4013, 4014, 4015, 4016, 4017);

-- 4. 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4010 AND 4024 ORDER BY menu_id;
-- 预期: 4010(mes:pro:work:list) + 4018~4024(mes:pro:work:*) + 无 4011~4017
```

> **注意：** 执行前备份 `sys_menu` 与 `sys_role_menu`。执行后需在系统管理→角色中为相关角色重新勾选 MES 工单按钮权限（4018-4024），确保用户既有菜单可见权又有 API 调用权。

---

### 任务 5：Flowable BPMN 审批流部署（运行时操作）

**性质：** 运行时 UI 操作，非代码交付。Week 20 的 `mes-week20-13-flow-deploy.sql` 已在 `wf_custom_form` 注册 `proWorkOrderService`（id=4050，route `mes/pro/workorder/detail`）。本周在运行系统中完成 BPMN 部署，使审批流下拉可选。

**操作步骤（参照 `mes-week20-13-flow-deploy.sql` 注释）：**
1. 访问 `/workflow/model` 新建流程模型
2. 流程 key: `mesWorkOrderApproval`，分类: 自定义业务（ZDYYW）
3. StartEvent `formKey` 设为 `key_4050`（关联 `wf_custom_form` 记录）
4. 设计审批节点：车间主任审批 → 生产主管审批（UserTask）
5. 点击"部署"，系统自动回填 `wf_custom_form.deploy_id` 与 `flow_name`
6. 验证：`GET /workflow/process/getProcesss?serviceName=proWorkOrderService` 返回新流程
7. 前端验证：MES 工单详情页"审批流程"下拉应出现该流程

> **交付物：** 在任务 6 验证报告中记录部署截图与 `getProcesss` 返回结果。

---

### 任务 6：Phase 1 端到端集成测试 + 报告

**Files:**
- Create: `doc/mes/plans/2026-06-19-mes-week21-verification-report.md`

**测试场景（扩展至流转卡链路）：**

```
1. ERP 创建工单 → ERP 提交审批 → Flowable 审批通过(APPROVED)
2. ERP afterFlowHandle() 事务提交后发送 WorkOrderReleaseMessage → mes.workorder.queue
3. MES WorkOrderReleaseConsumer 消费 → 创建 MES 工单(CREATED) + 工序步骤
4. MES 工单详情 → 选择审批流 → 提交审批 → Flowable 审批 → 通过 → woStatus=RELEASED
5. MES 工单详情 → 创建流转卡(输入数量) → 流转卡 CREATED + 工序步骤 PENDING
6. 流转卡详情 → 下达(RELEASED) → 开工第一道工序(IN_PROGRESS, 工单同步 IN_PROGRESS)
7. 逐道工序完工(录入良品/不良/报废) → 所有工序完成 → 流转卡自动 COMPLETED
8. 流转卡完工聚合 → 工单 completed_qty 累加 → 所有流转卡完成 → 工单 COMPLETED
9. ProWorkOrderServiceImpl.completeWo() 事务提交后发送 MesWorkOrderCompletedMessage → erp.workorder.completed.queue
10. ERP 消费 → 更新 ERP 工单状态 + 完工入库
```

**验证检查点：**

| # | 检查点 | 验证方法 | 期望结果 |
|---|--------|----------|----------|
| 1 | MES 模块启动 | `mvn spring-boot:run` | 日志无 ERROR，4 数据源连接成功 |
| 2 | 菜单权限 | 登录后查看 MES 菜单 | 4010 可见，列表 API 不 403 |
| 3 | ERP→MES MQ 联动 | ERP 审批工单 | MES 自动创建工单+工序 |
| 4 | MES 工单审批 | 提交→审批→通过 | woStatus: CREATED→APPROVING→RELEASED |
| 5 | 流转卡创建 | 工单详情创建流转卡 | 流转卡 + 工序步骤生成 |
| 6 | 流转卡执行 | 下达→开工→完工 | 状态链正确，数量聚合 |
| 7 | 工单完工聚合 | 所有流转卡完成 | 工单 COMPLETED |
| 8 | MES→ERP MQ 联动 | RabbitMQ 管控台 | `erp.workorder.completed.queue` 收到消息 |
| 9 | 跨库主数据 | MES 物料/BOM/工艺只读页 | ERP 数据正确显示 |

**交付物：** 验证报告文档，含每步执行结果、截图、MQ 消息体、异常记录。

---

### 任务 7：编译验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `npm run dev` 启动无报错，MES 各页面可访问
- [ ] `npm run lint:eslint` 对新增/修改的 `plus-ui/src/views/mes/**` 文件无 error

---

## 三、Week 21 交付物汇总

| 交付物 | 类型 | 说明 |
|--------|------|------|
| `workorder/detail.vue` | Vue(修改) | Tab 化 + 创建流转卡按钮 |
| `flow-card/detail.vue` | Vue(修改) | 完工/关闭流转卡按钮 |
| `workorder/form.vue` | Vue(新建) | 新增/编辑弹窗 |
| `workorder/index.vue` | Vue(修改) | 新增/修改按钮 |
| `mes-week21-15-menu.sql` | SQL | 菜单权限清理 |
| `2026-06-19-mes-week21-verification-report.md` | 文档 | Phase 1 端到端验证报告 |

---

## 四、Phase 1 出口标准核对

| 出口标准（《NexusERP_MES文档》§16.1） | 状态 |
|----------|------|
| `ruoyi-mes` 模块存在且 Maven 注册 | Week 17 已完成 |
| `mes` 数据源配置并启动验证 | Week 17 已完成 |
| ERP 工单下达消息由 MES 正式消费（不走占位） | Week 17/19 已完成 |
| MES 工单与工序可查询 + 审批能力 | Week 19/20 已完成（本周补全前端 + BPMN 部署） |
| Phase 1 演示通过 | 本周任务 6 |

---

## 五、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| 菜单清理误删 | `sys_menu`/`sys_role_menu` DELETE 操作风险 | 执行前备份；仅删 4011-4017，保留 4010/4018-4024 |
| BPMN 部署依赖运行环境 | 审批流需在运行系统 UI 操作，非纯代码 | 任务 5 为运行时操作，列为验证前置条件 |
| 完工聚合并发 | 多流转卡同时完工时工单 `completed_qty` 竞争 | Week 20 已用 CAS UPDATE `setSql` 累加，本周验证场景覆盖 |
| `el-table` 未迁移 `vxe-table` | MES 7 个页面用 `el-table`，与项目标准 `vxe-table` 不一致 | 本周不做迁移（YAGNI，避免演示前引入回归）；列入 Phase 2 收尾后续 |
| 工单编辑弹窗边缘场景 | 手动新增工单无 ERP 工艺路线，工序步骤为空 | form.vue 仅支持基本字段；工序自动生成依赖 ERP MQ 路径，手动工单需后续补"选择工艺路线"功能（Phase 2） |

---

## 六、明确排除项（本周不做）

- Phase 2 派工（ProDispatch）：DDL/Entity/Service/Controller/前端
- 安灯（ProAndon）、SN 追溯（ProSn）
- `el-table` → `vxe-table` 迁移（7 个 MES 页面）
- Phase 3 全部模块（qc/eq/dc/spc）

---

**文档生成时间**: 2026-06-18
**执行者**: AI Agent (Subagent-Driven Development)
**审核状态**: 已审批
