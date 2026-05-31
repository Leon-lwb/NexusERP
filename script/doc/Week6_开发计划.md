# Week 6 开发计划：P1前端收尾 + 业务增强

> **计划日期**: 2026-06-06 ~ 2026-06-12
> **计划人天**: 5人天
> **前置依赖**: Week 1-5 已完成，所有后端核心业务逻辑已就绪
> **本周定位**: Phase 1 最后一个前端收尾周，确保P1功能前后端完整闭环

---

## 一、现有代码基线分析

### 1.1 Week 1-5 后端完成情况汇总

| 周次 | 完成任务 | 后端状态 |
|------|---------|---------|
| Week 1 | R-001序列号规则/R-006供应商校验/Q-001异常处理/Q-002日期修复 | ✅ 全部完成 |
| Week 2 | R-007请购来源/R-008收货差异/R-009自动过账/R-011供应商评估/R-012绩效沉淀 | ✅ 全部完成 |
| Week 3 | R-013上架策略/R-014拣货策略/R-015库存估值 | ✅ 全部完成 |
| Week 4 | R-016安全库存预警/R-017台账校验/R-018委外订单/R-019委外发料收货/R-020返工订单 | ✅ 全部完成 |
| Week 5 | R-021倒冲领料/R-022产能日历/R-023领料联动+完工入库 | ✅ 全部完成 |

### 1.2 前端完成度分析

| 模块 | API组数 | View页面数 | 缺失项 | 完成度 |
|------|---------|-----------|--------|--------|
| **bd** 基础数据 | 9 | 10 | 无 | 100% |
| **pu** 采购 | 4 | 8 | vendor-evaluation缺types.ts | 90% |
| **wm** 仓储 | 20 | 31 | 无关键缺失 | 95% |
| **prd** 生产 | 8 | 10 | work-order-resource缺View; rework/subcontract表单需增强 | 75% |
| **sch** 排班 | 10 | 11 | 无关键缺失(子组件模式) | 90% |
| **so** 销售 | 4 | 7 | 无关键缺失(增强留Week 9-10) | 80% |

### 1.3 关键前端缺口清单

| 编号 | 缺口描述 | 影响模块 | 优先级 |
|------|---------|---------|--------|
| GAP-1 | 供应商评估API缺types.ts类型定义 | pu | 高 |
| GAP-2 | 供应商评估页面缺评估报表/趋势图可视化 | pu | 高 |
| GAP-3 | 采购收货联动按钮已有，但缺收货进度展示 | pu | 高 |
| GAP-4 | 委外订单表单使用手动输入ID，缺供应商/物料选择器 | prd | 高 |
| GAP-5 | 返工订单表单使用手动输入ID，缺工单/物料选择器 | prd | 高 |
| GAP-6 | 工单资源分配缺前端页面 | prd | 中 |
| GAP-7 | 产能日历缺可视化日历视图 | prd | 中 |
| GAP-8 | 采购订单页面缺订单行明细展示 | pu | 中 |

### 1.4 原计划Week 6任务完成状态

| 任务编号 | 任务内容 | 原计划人天 | 当前状态 | 说明 |
|---------|---------|-----------|---------|------|
| R-026 | 采购收货联动入口 | 0.5d | ⚠️ 部分完成 | 后端API+前端按钮已有，但缺进度展示 |
| R-027 | 供应商评估前端 | 1d | ⚠️ 部分完成 | CRUD页面已有，但缺types.ts+报表趋势图 |
| R-028 | 产能日历前端 | 1d | ✅ 已完成 | 已提前至Week5(F-022)完成 |

---

## 二、任务分解

### 任务 F-026: 采购收货联动前端增强

#### 2.1.1 业务背景

当前采购订单页面已有"收货联动"按钮和"差异报表"按钮，但存在以下不足：
1. 点击"收货联动"后仅弹出成功提示，无法查看生成的到货通知单/收货单详情
2. 缺少收货进度展示：无法直观看到订单各行的收货完成率
3. 采购订单页面缺少订单行明细展示

#### 2.1.2 实现步骤

**Step 1: 采购订单行明细展示** (0.3d)

修改 `views/erp/pu/order/index.vue`：
- 在订单列表中增加"展开行"，展示该订单的明细行信息
- 明细行字段：物料编码/物料名称/规格型号/订单数量/已收数量/收货状态
- 收货状态使用 `el-tag` 颜色标识：NOT_RECEIVED(灰)/PARTIAL_RECEIVED(橙)/FULL_RECEIVED(绿)/OVER_RECEIVED(红)

```vue
<el-table-column type="expand">
  <template #default="props">
    <el-table :data="props.row.lines" border size="small" style="margin: 8px 20px;">
      <el-table-column label="物料编码" prop="materialCode" width="120" />
      <el-table-column label="物料名称" prop="materialName" min-width="150" />
      <el-table-column label="规格型号" prop="materialSpec" width="120" />
      <el-table-column label="订单数量" prop="orderQuantity" width="100" />
      <el-table-column label="已收数量" prop="receivedQuantity" width="100" />
      <el-table-column label="收货状态" prop="receiveStatus" width="120">
        <template #default="scope">
          <el-tag :type="getReceiveStatusType(scope.row.receiveStatus)" size="small">
            {{ getReceiveStatusLabel(scope.row.receiveStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="收货进度" width="150">
        <template #default="scope">
          <el-progress
            :percentage="calcReceiveProgress(scope.row)"
            :color="getProgressColor(scope.row)"
            :stroke-width="10"
          />
        </template>
      </el-table-column>
    </el-table>
  </template>
</el-table-column>
```

**Step 2: 收货联动增强** (0.2d)

修改 `handleReceive()` 方法：
- 收货联动成功后，弹出结果对话框，展示生成的到货通知单ID
- 提供"查看到货通知"跳转链接

**Step 3: 后端接口补充** (0.2d)

在 `PuPurchaseOrderController` 中补充订单行查询接口：
```java
@GetMapping("/{orderId}/lines")
public R<List<PuPurchaseOrderLine>> listLines(@PathVariable Long orderId) {
    return R.ok(purchaseOrderLineService.selectByOrderId(orderId));
}
```

修改 `listPurchaseOrder` 接口，在返回的VO中包含lines字段（订单行明细）。

**Step 4: API与类型补充** (0.1d)

在 `api/erp/pu/order/types.ts` 中补充：
```typescript
export interface PurchaseOrderLineVO {
  lineId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  unitId: number;
  unitName: string;
  orderQuantity: number;
  receivedQuantity: number;
  receiveStatus: string;
  unitPrice: number;
  lineAmount: number;
}

export interface PurchaseOrderVO {
  orderId: number;
  orderNo: string;
  vendorId: number;
  vendorName: string;
  orderDate: string;
  expectedArrivalDate: string;
  status: string;
  remark: string;
  lines?: PurchaseOrderLineVO[];
}
```

#### 2.1.3 验收标准

| 验收项 | 标准 |
|--------|------|
| 订单行展开 | 点击展开行可查看订单明细，含物料/数量/收货状态 |
| 收货进度条 | 每行显示收货进度百分比，颜色随状态变化 |
| 收货联动 | 点击后弹出结果对话框，可跳转到货通知页面 |
| 差异报表 | 保持现有功能不变 |

---

### 任务 F-027: 供应商评估前端增强

#### 2.2.1 业务背景

当前供应商评估页面仅有基础CRUD列表，缺少：
1. types.ts 类型定义文件（API使用 `any` 类型）
2. 评估报表：按供应商维度展示雷达图（交货及时率/质量合格率/价格竞争力）
3. 趋势图：展示供应商评估分数随时间变化的趋势

#### 2.2.2 实现步骤

**Step 1: 补充 types.ts** (0.1d)

新建 `api/erp/pu/vendor-evaluation/types.ts`：
```typescript
export interface VendorEvaluationVO {
  evalId: number;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  evalPeriod: string;
  deliveryRate: number;
  qualityRate: number;
  priceCompetitiveness: number;
  totalScore: number;
  evalLevel: string;
  evalDate: string;
  remark: string;
}

export interface VendorEvaluationQuery {
  pageNum: number;
  pageSize: number;
  vendorName?: string;
  evalPeriod?: string;
  evalLevel?: string;
}

export interface VendorEvaluationForm {
  evalId?: number;
  vendorId?: number;
  evalPeriod?: string;
  remark?: string;
}
```

更新 `api/erp/pu/vendor-evaluation/index.ts`，使用类型化接口替换 `any`。

**Step 2: 评估报表页面** (0.4d)

新建 `views/erp/pu/vendor-evaluation/report.vue`：
- 供应商选择下拉框（远程搜索）
- 评估雷达图（ECharts）：三个维度（交货及时率/质量合格率/价格竞争力）
- 评估趋势折线图（ECharts）：按评估周期展示分数变化趋势
- 评估等级分布饼图

```vue
<template>
  <div class="app-container">
    <el-form :inline="true">
      <el-form-item label="供应商">
        <el-select v-model="selectedVendor" filterable remote :remote-method="searchVendor" placeholder="请选择供应商">
          <el-option v-for="v in vendorOptions" :key="v.vendorId" :label="v.vendorName" :value="v.vendorId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadReport">查询</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="评估雷达图">
          <div ref="radarChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="评估趋势">
          <div ref="trendChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card header="评估等级分布" style="margin-top: 20px;">
      <div ref="pieChartRef" style="height: 300px;"></div>
    </el-card>
  </div>
</template>
```

**Step 3: 后端趋势数据接口** (0.3d)

在 `PuVendorEvaluationController` 中新增：
```java
@GetMapping("/trend/{vendorId}")
public R<List<PuVendorEvaluationVo>> trend(@PathVariable Long vendorId) {
    return R.ok(vendorEvaluationService.selectTrendByVendorId(vendorId));
}

@GetMapping("/distribution")
public R<Map<String, Integer>> distribution() {
    return R.ok(vendorEvaluationService.selectLevelDistribution());
}
```

`IPuVendorEvaluationService` 新增：
```java
List<PuVendorEvaluationVo> selectTrendByVendorId(Long vendorId);
Map<String, Integer> selectLevelDistribution();
```

**Step 4: 路由与菜单** (0.1d)

在路由配置中新增供应商评估报表路由：
```typescript
{
  path: '/erp/pu/vendor-evaluation/report',
  component: () => import('@/views/erp/pu/vendor-evaluation/report.vue'),
  hidden: true,
  perms: ['pu:vendorEvaluation:query']
}
```

SQL菜单：
```sql
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('供应商评估报表', [供应商评估父菜单ID], 2, 'report', 'erp/pu/vendor-evaluation/report', 0, 0, 'C', '0', '0', 'pu:vendorEvaluation:query', 'chart', 'admin', NOW());
```

#### 2.2.3 验收标准

| 验收项 | 标准 |
|--------|------|
| types.ts | API接口全部类型化，无any类型 |
| 雷达图 | 选择供应商后展示三维评估雷达图 |
| 趋势图 | 展示供应商评估分数随时间变化趋势 |
| 等级分布 | 展示所有供应商评估等级(A/B/C/D)分布饼图 |
| 趋势接口 | GET /erp/pu/vendor-evaluation/trend/{vendorId} 返回按时间排序的评估列表 |

---

### 任务 F-029: 委外订单前端完善

#### 2.3.1 业务背景

当前委外订单页面(`views/erp/prd/subcontract/index.vue`)存在以下问题：
1. 供应商ID/编码/名称使用手动输入，应改为远程搜索选择器
2. 物料ID/编码/名称/规格使用手动输入，应改为远程搜索选择器
3. 缺少关联工单选择
4. 缺少委外发料/收货操作按钮（后端已实现SubcontractIssueStrategy/SubcontractReceiveStrategy）

#### 2.3.2 实现步骤

**Step 1: 供应商选择器** (0.15d)

将表单中的供应商ID/编码/名称三个字段替换为单个供应商选择器：
```vue
<el-form-item label="供应商" prop="vendorId">
  <el-select
    v-model="form.vendorId"
    filterable
    remote
    :remote-method="searchVendor"
    placeholder="请选择供应商"
    @change="onVendorChange"
    style="width: 100%"
  >
    <el-option
      v-for="v in vendorOptions"
      :key="v.vendorId"
      :label="v.vendorName"
      :value="v.vendorId"
    />
  </el-select>
</el-form-item>
```

选择供应商后自动填充 vendorCode/vendorName。

**Step 2: 物料选择器** (0.15d)

将表单中的物料ID/编码/名称/规格/单位五个字段替换为单个物料选择器：
```vue
<el-form-item label="物料" prop="materialId">
  <el-select
    v-model="form.materialId"
    filterable
    remote
    :remote-method="searchMaterial"
    placeholder="请选择物料"
    @change="onMaterialChange"
    style="width: 100%"
  >
    <el-option
      v-for="m in materialOptions"
      :key="m.materialId"
      :label="`${m.materialCode} - ${m.materialName}`"
      :value="m.materialId"
    />
  </el-select>
</el-form-item>
```

选择物料后自动填充 materialCode/materialName/materialSpec/baseUomName。

**Step 3: 关联工单选择** (0.1d)

新增关联工单选择器（从已有工单中选择）：
```vue
<el-form-item label="关联工单" prop="woId">
  <el-select
    v-model="form.woId"
    filterable
    remote
    :remote-method="searchWorkOrder"
    placeholder="请选择关联工单"
    @change="onWoChange"
    style="width: 100%"
  >
    <el-option
      v-for="wo in woOptions"
      :key="wo.woId"
      :label="`${wo.woNo} - ${wo.materialName}`"
      :value="wo.woId"
    />
  </el-select>
</el-form-item>
```

**Step 4: 委外发料/收货操作** (0.1d)

在操作列增加委外发料/收货按钮（状态为IN_PROGRESS时显示）：
```vue
<el-tooltip content="委外发料" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
  <el-button link type="warning" icon="Van" @click="handleIssue(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
</el-tooltip>
<el-tooltip content="委外收货" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
  <el-button link type="success" icon="Box" @click="handleReceive(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
</el-tooltip>
```

#### 2.3.3 验收标准

| 验收项 | 标准 |
|--------|------|
| 供应商选择 | 远程搜索，选择后自动填充编码/名称 |
| 物料选择 | 远程搜索，选择后自动填充编码/名称/规格/单位 |
| 关联工单 | 可选择已有工单，自动填充工单号 |
| 委外发料 | IN_PROGRESS状态可点击发料，触发库存扣减 |
| 委外收货 | IN_PROGRESS状态可点击收货，触发库存增加 |

---

### 任务 F-030: 返工订单前端完善

#### 2.4.1 业务背景

当前返工订单页面(`views/erp/prd/rework/index.vue`)存在以下问题：
1. 来源工单ID使用手动输入数字，应改为工单选择器
2. 缺少物料信息自动填充（选择来源工单后应自动带出物料信息）
3. 缺少返工出库/入库操作按钮（后端已实现ReworkIssueStrategy/ReworkReceiveStrategy）

#### 2.4.2 实现步骤

**Step 1: 来源工单选择器** (0.15d)

将"来源工单ID"输入框替换为工单选择器：
```vue
<el-form-item label="来源工单" prop="sourceWoId">
  <el-select
    v-model="form.sourceWoId"
    filterable
    remote
    :remote-method="searchWorkOrder"
    placeholder="请选择来源工单"
    @change="onSourceWoChange"
    style="width: 100%"
  >
    <el-option
      v-for="wo in woOptions"
      :key="wo.woId"
      :label="`${wo.woNo} - ${wo.materialName}`"
      :value="wo.woId"
    />
  </el-select>
</el-form-item>
```

**Step 2: 物料信息自动填充** (0.1d)

选择来源工单后，自动填充物料信息：
```typescript
const onSourceWoChange = (woId: number) => {
  const wo = woOptions.value.find(w => w.woId === woId);
  if (wo) {
    form.value.sourceWoNo = wo.woNo;
    form.value.materialId = wo.materialId;
    form.value.materialCode = wo.materialCode;
    form.value.materialName = wo.materialName;
    form.value.materialSpec = wo.materialSpec;
    form.value.baseUomName = wo.baseUomName;
  }
};
```

**Step 3: 返工出库/入库操作** (0.1d)

在操作列增加返工出库/入库按钮：
```vue
<el-tooltip content="返工出库" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
  <el-button link type="warning" icon="Van" @click="handleIssue(scope.row)" v-hasPermi="['prd:rework:edit']" />
</el-tooltip>
<el-tooltip content="返工入库" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
  <el-button link type="success" icon="Box" @click="handleReceive(scope.row)" v-hasPermi="['prd:rework:edit']" />
</el-tooltip>
```

**Step 4: 后端接口补充** (0.15d)

在 `PrdReworkOrderController` 中新增返工出库/入库接口：
```java
@PutMapping("/issue/{reworkId}")
public R<Void> issue(@PathVariable Long reworkId) {
    reworkOrderService.issueMaterial(reworkId);
    return R.ok();
}

@PutMapping("/receive/{reworkId}")
public R<Void> receive(@PathVariable Long reworkId) {
    reworkOrderService.receiveMaterial(reworkId);
    return R.ok();
}
```

前端API补充：
```typescript
export const issueReworkMaterial = (reworkId: number) =>
  request({ url: BASE + '/issue/' + reworkId, method: 'put' });

export const receiveReworkMaterial = (reworkId: number) =>
  request({ url: BASE + '/receive/' + reworkId, method: 'put' });
```

#### 2.4.3 验收标准

| 验收项 | 标准 |
|--------|------|
| 工单选择 | 远程搜索，选择后自动填充工单号+物料信息 |
| 物料自动填充 | 选择来源工单后，物料编码/名称/规格/单位自动带出 |
| 返工出库 | IN_PROGRESS状态可点击出库，触发库存扣减 |
| 返工入库 | IN_PROGRESS状态可点击入库，触发库存增加 |

---

### 任务 F-031: 工单资源分配前端

#### 2.5.1 业务背景

后端已有 `PrdWorkOrderResourceController`（`/erp/prd/workOrderResource`），提供按工单查询/保存资源分配的接口，但前端缺少对应页面。工单资源分配用于将人员、设备等资源绑定到工单上，是产能排程的基础。

#### 2.5.2 实现步骤

**Step 1: 新增前端页面** (0.5d)

新建 `views/erp/prd/work-order-resource/index.vue`：
- 按工单查询资源分配列表
- 资源分配表格：资源类型(人员/设备/工位)/资源编码/资源名称/分配数量/开始时间/结束时间
- 批量保存功能

```vue
<template>
  <div class="app-container">
    <el-form :inline="true">
      <el-form-item label="工单号">
        <el-select v-model="woId" filterable remote :remote-method="searchWo" placeholder="请选择工单" @change="loadResources">
          <el-option v-for="wo in woOptions" :key="wo.woId" :label="wo.woNo" :value="wo.woId" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAddRow">添加资源</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" @click="handleSave">保存</el-button>
      </el-col>
    </el-row>

    <el-table :data="resourceList" border>
      <el-table-column label="资源类型" prop="resourceType" width="120">
        <template #default="scope">
          <el-select v-model="scope.row.resourceType" placeholder="请选择">
            <el-option label="人员" value="WORKER" />
            <el-option label="设备" value="EQUIPMENT" />
            <el-option label="工位" value="WORKSTATION" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="资源编码" prop="resourceCode" width="150">
        <template #default="scope">
          <el-input v-model="scope.row.resourceCode" placeholder="请输入" />
        </template>
      </el-table-column>
      <el-table-column label="资源名称" prop="resourceName" min-width="150">
        <template #default="scope">
          <el-input v-model="scope.row.resourceName" placeholder="请输入" />
        </template>
      </el-table-column>
      <el-table-column label="分配数量" prop="quantity" width="120">
        <template #default="scope">
          <el-input-number v-model="scope.row.quantity" :min="1" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="开始时间" prop="startTime" width="180">
        <template #default="scope">
          <el-date-picker v-model="scope.row.startTime" type="datetime" size="small" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="结束时间" prop="endTime" width="180">
        <template #default="scope">
          <el-date-picker v-model="scope.row.endTime" type="datetime" size="small" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="removeRow(scope.$index)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
```

**Step 2: 路由与菜单** (0.1d)

```typescript
{
  path: '/erp/prd/work-order-resource',
  component: () => import('@/views/erp/prd/work-order-resource/index.vue'),
  hidden: true,
  perms: ['prd:workOrderResource:list']
}
```

SQL菜单：
```sql
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('工单资源分配', [生产管理父菜单ID], 8, 'work-order-resource', 'erp/prd/work-order-resource/index', 0, 0, 'C', '0', '0', 'prd:workOrderResource:list', 'peoples', 'admin', NOW());
```

#### 2.5.3 验收标准

| 验收项 | 标准 |
|--------|------|
| 工单选择 | 远程搜索工单，选择后加载资源分配列表 |
| 资源添加 | 可添加人员/设备/工位资源行 |
| 批量保存 | 点击保存批量提交资源分配 |
| 资源删除 | 可删除单个资源行 |

---

### 任务 F-032: 产能日历可视化增强

#### 2.6.1 业务背景

Week 5 已完成产能日历基础列表页(F-022)，但仅有表格展示，缺少直观的日历视图。产能日历可视化对于排产调度至关重要，需要按月/周展示产能占用情况。

#### 2.6.2 实现步骤

**Step 1: 日历视图组件** (0.3d)

在 `views/erp/prd/capacity-calendar/index.vue` 中增加日历视图Tab：

```vue
<el-tabs v-model="activeView">
  <el-tab-pane label="列表视图" name="list">
    <!-- 现有列表内容 -->
  </el-tab-pane>
  <el-tab-pane label="日历视图" name="calendar">
    <el-calendar v-model="calendarDate">
      <template #date-cell="{ data }">
        <div class="calendar-cell">
          <div class="calendar-day">{{ data.day.split('-')[2] }}</div>
          <div v-for="item in getCalendarItems(data.day)" :key="item.calendarId" class="calendar-item">
            <el-tag :type="getCapacityTagType(item)" size="small" style="width: 100%; margin: 2px 0;">
              {{ item.shiftName }}: {{ item.remainingHours }}h/{{ item.availableHours }}h
            </el-tag>
          </div>
        </div>
      </template>
    </el-calendar>
  </el-tab-pane>
</el-tabs>
```

颜色规则：
- 剩余产能 > 80%：绿色(success)
- 剩余产能 50%-80%：蓝色(primary)
- 剩余产能 20%-50%：橙色(warning)
- 剩余产能 < 20%：红色(danger)

**Step 2: 数据加载优化** (0.1d)

日历视图切换时，按月加载产能数据：
```typescript
const loadCalendarData = async () => {
  const year = calendarDate.value.getFullYear();
  const month = calendarDate.value.getMonth();
  const startDate = new Date(year, month, 1);
  const endDate = new Date(year, month + 1, 0);
  const res = await listCapacityCalendar({
    calendarDateStart: formatDate(startDate),
    calendarDateEnd: formatDate(endDate),
    objectType: queryParams.objectType,
    objectId: queryParams.objectId,
    pageNum: 1,
    pageSize: 999
  });
  calendarData.value = res.rows;
};
```

**Step 3: 样式优化** (0.1d)

```css
.calendar-cell {
  height: 100%;
  min-height: 80px;
}
.calendar-day {
  font-weight: bold;
  margin-bottom: 4px;
}
.calendar-item {
  font-size: 11px;
  line-height: 1.2;
}
```

#### 2.6.3 验收标准

| 验收项 | 标准 |
|--------|------|
| 日历视图 | 切换Tab可查看日历视图，按月展示产能 |
| 颜色标识 | 产能充足(绿)/正常(蓝)/紧张(橙)/已满(红) |
| 数据联动 | 切换月份/工作中心后自动刷新日历数据 |
| 列表视图 | 保持现有列表功能不变 |

---

## 三、Week 6 开发排期

| 日期 | 任务 | 负责人 | 产出 | 依赖 |
|------|------|--------|------|------|
| **Day 1 (周一)** | F-026 Step 1-2: 采购订单行明细+收货联动增强 | 前端 | order/index.vue修改 | - |
| **Day 1 (周一)** | F-026 Step 3-4: 后端接口+API类型补充 | 后端 | Controller+types.ts | - |
| **Day 2 (周二)** | F-027 Step 1: types.ts补充 | 前端 | vendor-evaluation/types.ts | - |
| **Day 2 (周二)** | F-027 Step 2: 评估报表页面(雷达图+趋势图) | 前端 | vendor-evaluation/report.vue | Step 1 |
| **Day 2 (周二)** | F-027 Step 3: 后端趋势数据接口 | 后端 | Controller+Service | - |
| **Day 3 (周三)** | F-029 Step 1-3: 委外订单表单增强(选择器) | 前端 | subcontract/index.vue修改 | - |
| **Day 3 (周三)** | F-029 Step 4: 委外发料/收货操作 | 前端+后端 | 操作按钮+API | Step 1-3 |
| **Day 4 (周四)** | F-030 Step 1-2: 返工订单表单增强(选择器) | 前端 | rework/index.vue修改 | - |
| **Day 4 (周四)** | F-030 Step 3-4: 返工出库/入库操作 | 前端+后端 | 操作按钮+API | Step 1-2 |
| **Day 4 (周四)** | F-031: 工单资源分配前端页面 | 前端 | work-order-resource/index.vue | - |
| **Day 5 (周五)** | F-032: 产能日历可视化增强 | 前端 | capacity-calendar/index.vue修改 | - |
| **Day 5 (周五)** | F-027 Step 4: 路由+菜单配置 | 前端+SQL | 路由+菜单SQL | Step 2-3 |
| **Day 5 (周五)** | 全量联调+修复 | 全栈 | Week 6全部功能验证通过 | 全部 |

---

## 四、SQL DDL 汇总

```sql
-- ============================================
-- Week 6 SQL 变更脚本: week6_frontend_enhancement.sql
-- ============================================

-- 1. 供应商评估报表菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('供应商评估报表', [供应商评估父菜单ID], 2, 'report', 'erp/pu/vendor-evaluation/report', 0, 0, 'C', '0', '0', 'pu:vendorEvaluation:query', 'chart', 'admin', NOW(), '供应商评估报表与趋势分析');

-- 2. 工单资源分配菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('工单资源分配', [生产管理父菜单ID], 8, 'work-order-resource', 'erp/prd/work-order-resource/index', 0, 0, 'C', '0', '0', 'prd:workOrderResource:list', 'peoples', 'admin', NOW(), '工单资源分配管理');

-- 工单资源分配按钮权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('工单资源查询', LAST_INSERT_ID(), 1, '', '', 0, 0, 'F', '0', '0', 'prd:workOrderResource:query', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('工单资源新增', LAST_INSERT_ID()-1, 2, '', '', 0, 0, 'F', '0', '0', 'prd:workOrderResource:add', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('工单资源修改', LAST_INSERT_ID()-2, 3, '', '', 0, 0, 'F', '0', '0', 'prd:workOrderResource:edit', '#', 'admin', NOW());
```

---

## 五、文件变更清单

### 5.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `api/erp/pu/vendor-evaluation/types.ts` | 供应商评估类型定义 |
| `views/erp/pu/vendor-evaluation/report.vue` | 供应商评估报表页面(雷达图+趋势图) |
| `views/erp/prd/work-order-resource/index.vue` | 工单资源分配页面 |

### 5.2 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `views/erp/pu/order/index.vue` | 增加订单行展开+收货进度条+收货联动增强 |
| `api/erp/pu/order/types.ts` | 补充PurchaseOrderLineVO类型 |
| `api/erp/pu/vendor-evaluation/index.ts` | 类型化替换any |
| `views/erp/prd/subcontract/index.vue` | 供应商/物料选择器+关联工单+委外发料收货按钮 |
| `views/erp/prd/rework/index.vue` | 来源工单选择器+物料自动填充+返工出库入库按钮 |
| `views/erp/prd/capacity-calendar/index.vue` | 增加日历视图Tab |
| `PuPurchaseOrderController.java` | 新增listLines接口 |
| `IPuPurchaseOrderService.java` | 新增selectOrderLines方法 |
| `PuPurchaseOrderServiceImpl.java` | 实现selectOrderLines |
| `PuVendorEvaluationController.java` | 新增trend/distribution接口 |
| `IPuVendorEvaluationService.java` | 新增selectTrendByVendorId/selectLevelDistribution方法 |
| `PuVendorEvaluationServiceImpl.java` | 实现趋势查询+等级分布 |
| `PrdReworkOrderController.java` | 新增issue/receive接口 |
| `IPrdReworkOrderService.java` | 新增issueMaterial/receiveMaterial方法 |
| `PrdReworkOrderServiceImpl.java` | 实现返工出库/入库 |

### 5.3 SQL脚本

| 文件路径 | 说明 |
|---------|------|
| `script/sql/week6_frontend_enhancement.sql` | 菜单+权限 |

---

## 六、联调测试用例

### 6.1 采购收货联动测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 打开采购订单列表，点击某订单展开行 | 显示订单行明细，含物料/数量/收货状态 |
| 2 | 查看收货进度条 | 部分到货显示橙色进度条，完全到货显示绿色 |
| 3 | 点击"收货联动" | 弹出成功对话框，显示到货通知单ID，可点击跳转 |
| 4 | 点击"差异报表" | 弹出差异报表，显示收货差异行 |

### 6.2 供应商评估报表测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 打开供应商评估报表页面 | 显示供应商选择框+空图表 |
| 2 | 选择供应商，点击查询 | 雷达图显示三维评估，趋势图显示分数变化 |
| 3 | 查看等级分布饼图 | 显示A/B/C/D等级分布 |
| 4 | 切换供应商 | 图表数据更新 |

### 6.3 委外订单表单测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 点击新增委外订单 | 弹出表单，含供应商/物料选择器 |
| 2 | 选择供应商 | 自动填充供应商编码/名称 |
| 3 | 选择物料 | 自动填充物料编码/名称/规格/单位 |
| 4 | 选择关联工单 | 自动填充工单号 |
| 5 | 保存委外订单 | 订单创建成功 |
| 6 | 下达后点击委外发料 | 触发库存扣减 |
| 7 | 点击委外收货 | 触发库存增加 |

### 6.4 返工订单表单测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 点击新增返工订单 | 弹出表单，含来源工单选择器 |
| 2 | 选择来源工单 | 自动填充工单号+物料信息 |
| 3 | 保存返工订单 | 订单创建成功 |
| 4 | 开始后点击返工出库 | 触发库存扣减 |
| 5 | 点击返工入库 | 触发库存增加 |

### 6.5 产能日历可视化测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 打开产能日历，切换到日历视图 | 显示当月日历 |
| 2 | 查看日历中的产能标记 | 颜色标识正确(绿/蓝/橙/红) |
| 3 | 切换月份 | 日历数据刷新 |
| 4 | 切换回列表视图 | 列表数据正常 |

---

## 七、Phase 1 进度总览

### 7.1 Week 1-6 完成情况

| 周次 | 定位 | 完成任务 | 状态 |
|------|------|---------|------|
| Week 1 | P0清零 | R-001/R-006/Q-001/Q-002 | ✅ 已完成 |
| Week 2 | 采购闭环 | R-007/R-008/R-009/R-011/R-012 | ✅ 已完成 |
| Week 3 | 仓储策略层 | R-013/R-014/R-015 | ✅ 已完成 |
| Week 4 | 生产深度(上) | R-016/R-017/R-018/R-019/R-020/R-024/R-025 | ✅ 已完成 |
| Week 5 | 生产深度(下) | R-021/R-022/R-023/F-021/F-022/F-023 | ✅ 已完成 |
| Week 6 | P1前端收尾 | F-026/F-027/F-029/F-030/F-031/F-032 | ⏳ 本周执行 |

### 7.2 Phase 1 剩余任务 (Week 7-12)

| 周次 | 任务编号 | 任务内容 | 模块 | 人天 |
|------|---------|---------|------|------|
| Week 7-8 | I-001 | MQ基础设施搭建(RabbitMQ/Kafka) | infra | 3 |
| Week 7-8 | I-002 | 跨库事务补偿框架 | infra | 2 |
| Week 7-8 | Q-003 | Service拆分重构(WmMaterialReceiveServiceImpl) | quality | 2 |
| Week 7-8 | Q-004 | @AutoMapper注解统一替换 | quality | 1.5 |
| Week 9-10 | Q-005 | LambdaQueryWrapper链式重构 | quality | 2 |
| Week 9-10 | Q-006 | Service基类统一+Jasypt加密 | quality | 2 |
| Week 9-10 | R-025 | 委外前端完善(已提前至Week6) | 前端 | 1 |
| Week 9-10 | R-026 | 销售管理增强(价格引擎+信用检查) | so | 2 |
| Week 11-12 | R-041 | 序列号管理 | wm | 1.5 |
| Week 11-12 | R-042 | 批次管理增强 | wm | 1 |
| Week 11-12 | R-043 | 追溯查询服务 | wm | 2 |
| Week 11-12 | R-044 | 追溯前端 | 前端 | 1 |
| Week 11-12 | R-055 | 盘点差异审批流 | wm | 1.5 |

---

> **审查要点提示**:
> 1. 供应商选择器/物料选择器的远程搜索性能（需考虑防抖+最小字符数）
> 2. ECharts图表的响应式适配（窗口大小变化时自动resize）
> 3. 委外发料/收货、返工出库/入库的事务一致性
> 4. 产能日历日历视图的数据加载策略（按月加载，避免全量查询）
> 5. 采购订单行展开的数据来源（嵌套查询 vs VO包含lines）
> 6. 后端新增接口的权限注解(@SaCheckPermission)和操作日志(@Log)配置
