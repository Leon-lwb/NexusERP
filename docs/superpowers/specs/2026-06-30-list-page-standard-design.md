# 列表页搜索栏与表格字段排列标准

- **日期**:2026-06-30
- **范围**:Nexus-ERP `plus-ui` 前端所有业务列表页(`views/erp/*`、`views/mes/*`、`views/iot/*` 等)
- **示范页**:本标准首次落地于 `views/erp/so/orderheader/index.vue`,作为其余页面回灌的参照样板

## 一、背景与问题

`plus-ui` 脱胎于 RuoYi-Vue-Plus 代码生成器,`proxy.useDict` / `RightToolbar` / `Pagination` / `dict-tag` 等基础设施高度统一,但**列表页的「搜索栏 + 表格列」没有任何公共封装**——10 个抽样页面 10 套手写实现,在五个维度上发散:

1. **搜索栏 label-width 不统一**:100px(多数)/ 90px(customerorder)/ 68px(material,且同表单内单项又覆写为 100px)/ 完全省略(workorder)。下拉框内联宽度也各写各的:orderheader 用 120px、customerorder 用 200px、material 用 120px,其余页走默认。
2. **表格列宽两极分化**:orderheader/workorder 几乎每列都设 width("全设"派);customerorder/quotation/return/material/stock 几乎一列都不设("全不设"派);stock 有 23 列却几乎全无 width,必然横向溢出。日期列宽度也不统一(多数 180、orderheader 120、部分页干脆不设)。
3. **show-overflow-tooltip 基本缺失**:10 页里仅 orderheader(1 列)和 workorder(1 列)用了;而客户名称、物料名称、规格型号、供应商名称、备注这些长文本列恰恰分布在其余 8 页里,长内容会换行撑高或被截断。
4. **操作列不一致**:orderheader width=280、workorder width=240(无 fixed-width)、其余页操作列无 width;pu/order 和 pu/requisition 干脆没有操作列,行级动作上移工具栏。
5. **状态渲染不统一 + 原始字段暴露**:多数用 dict-tag,workorder 改用硬编码 el-option + 本地 el-tag+map;多个表格把主键/ID 列暴露给用户(customerorder 的「客户订单ID」、stock 的「生产工单ID」、workorder 的「ERP工单ID」),quotation/return/pu-order/pu-requisition/pu-return 还把「备注」列摆进列表。

## 二、搜索栏标准

### 2.1 结构骨架(网格分栏)

采用 `el-row` + `el-col :span="8"` 网格,每行 3 个搜索项,窄屏自动收缩:

```vue
<el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
  <el-row :gutter="16">
    <el-col :span="8">
      <el-form-item label="销售订单编号" prop="soNo">
        <el-input v-model="queryParams.soNo" placeholder="请输入销售订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
    </el-col>
    <!-- 其余 el-col :span="8" ... -->
    <el-col :span="8" class="search-actions">
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-col>
  </el-row>
</el-form>
```

**关键点**:
- **不再用 `:inline="true"`**。inline 模式下各项宽度不一时换行会错列,无法保证对齐。网格分栏强制每项占据等宽格子,从根本上解决对齐问题。
- `label-width="100px"` 统一(项目所有页均适用,无 4 字以上超长 label;若有,单独在该 `el-form-item` 上覆写,不破坏全局统一)。
- `el-row :gutter="16"` 给列间留白。
- 每个搜索项独占一个 `<el-col :span="8">`,**不允许多项挤在同一个 el-col 里**。

### 2.2 控件宽度规则

| 控件类型 | 宽度处理 |
|---------|---------|
| `el-input`(文本/编号) | `width: 100%`,跟随 el-col 格宽,不设内联 width |
| `el-select`(下拉) | **必须设 `style="width: 100%"`**,跟随格宽(解决"下拉框宽度不合适"问题) |
| `el-date-picker`(单日) | `style="width: 100%"`,跟随格宽 |
| `el-date-picker`(日期范围 daterange) | 占一个 el-col,`style="width: 100%"` |

**下拉框宽度是本次修复重点**:现状忽宽忽窄,统一为跟随格宽(= el-col 的 1/3 行宽),既不会过窄挤掉文字,也不会过宽浪费空间。`el-select` 在 Element Plus 里默认不撑满父容器,必须显式 `width: 100%`。

### 2.3 搜索/重置按钮摆放(占位+靠右)

按钮独占一个 `el-col :span="8"`,放在最后一格。当搜索项不是 3 的倍数时,按钮自然落到下一行的末格;无需动态计算 span,**始终保持 :span=8**:

```
3 项(1 行):                 4 项(2 行):
┌─A─┬─B─┬─C─┐              ┌─A─┬─B─┬─C─┐
│   │   │   │              │   │   │   │
└───┴───┴───┘              ├─D─┼搜索重置┤
                            │   │       │
                            └───┴───────┘
按钮始终 :span=8 占满末格, 内容右对齐(text-align: right)
```

按钮 el-col 加 `class="search-actions"`,内部 `el-form-item` 的按钮靠右对齐(通过 CSS `text-align: right` 或 flex 实现,见 2.5)。

### 2.4 搜索项取舍原则(硬性准则)

**项数由业务决定,不是机械定个数;6 个左右为推荐值,可根据业务增减。** 判断每个搜索项去留时,依次套用以下四条原则:

1. **高频检索优先(保留)**:业务单号(如 soNo/returnNo/orderNo/quotationNo)、业务名称(如 clientName/物料名称/vendorName)、核心状态(如订单状态/退货状态)——这三类是用户查列表时最高频的筛选维度,优先保留。

2. **低频/内部字段移出(移除)**:承诺交货日、要求交货日、信用拦截人、原始 ID(coId/projectId/woId/erpWoId)——这类要么几乎不用、要么是内部字段不适合给业务用户看,默认不放进搜索栏。

3. **状态取主舍次(只留一个)**:同一业务有多个状态时只保留主状态。如 orderheader 同时有「信用状态」+「订单状态」,只留「订单状态」作为主筛选,信用状态不进搜索栏(避免多个状态下拉堆砌干扰)。

4. **日期合并为范围(最多 2 个)**:日期类搜索项最多保留 2 个(通常是制单日期 + 需求/交货日期),超过的合并为「日期范围」一个选择器(`type="daterange"`),而不是平铺 3 个独立 date-picker。

**判断顺序**:先按原则 1 圈定"必须保留"项 → 按原则 2 剔除"应移除"项 → 原则 3 合并多余状态下拉 → 原则 4 合并多余日期选择器。剩余项数即为本页搜索项数;若仍明显偏多(>8),回到原则 2 再筛一轮低频项。

### 2.5 配套样式(scope 内或全局)

```css
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
```

> 注:`search-actions` 的右对齐也可在 `el-form-item` 上用 `style="text-align: right"` 临时实现,但建议走 class 统一管理。

## 三、表格字段排列标准

### 3.1 列宽策略:纯自适应

**所有列一律不设 `width`**,靠 Element 表格按内容自适应分配列宽(配合表格整体的 `min-width` 行为)。这是统一策略,消除"全设派"与"全不设派"的分化。

**唯一例外处理长文本**:对名称、规格、备注等可能很长的文本列,加 `min-width`(给一个最小宽下限,如 120) + `show-overflow-tooltip`(超出宽度时鼠标悬停显示全文,不换行撑高)。

```vue
<!-- 普通列:不设 width -->
<el-table-column label="销售订单编号" align="center" prop="soNo" />

<!-- 长文本列:min-width + show-overflow-tooltip -->
<el-table-column label="来源客户" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
```

> **关于序号列与操作列**:本标准采用"全删 width 纯自适应",即序号列、操作列也不设 width,与数据列一视同仁。操作列因按钮多会自然分到较宽,通常无需干预;若个别页操作按钮极多导致拥挤,可对该页操作列单独设 width,但这是页面级特例,不写入通用标准。

### 3.2 序号列与原始主键 ID 列

- **保留序号列**:`<el-table-column type="index" label="序号" align="center" />`。

  **列宽遵循 3.1 纯自适应:序号列同样不设 `width`**,与数据列一视同仁。序号内容固定为数字,自适应后表格会自然分配给其合适窄宽,无需固定干预。

- **删除原始主键 ID 列**:任何 `coId`/`projectId`/`woId`/`erpWoId` 等内部主键列一律从表格移除。用户在列表里看到业务编号(如 soNo/woNo/orderNo)即可,原始数字主键无业务含义且占宽,不暴露。

> 原表格若序号列误挂 `prop`(如 orderheader 现状的 `prop="soId"`),一并去掉——序号列由 `type="index"` 自动生成行号,不应绑业务字段。

### 3.3 备注列

- **保留备注列,但必须加 `show-overflow-tooltip` + `min-width`**。备注是长文本,直接展示会换行撑高行高;加 tooltip 后超出宽度截断并悬停显示全文,既保留信息可见性又不破坏行高。

```vue
<el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
```

### 3.4 状态字段渲染:统一字典 + dict-tag

- **搜索栏状态下拉**:统一用 `proxy.useDict` 取字典 + `el-select` 内 `el-option v-for="dict in xxx_status"`。**禁止硬编码 `<el-option label="新建" value="CREATED" />`**(workorder 现状即此问题)。
- **表格状态列**:统一用 `<dict-tag :options="xxx_status" :value="scope.row.status" />`。**禁止本地 `el-tag` + JS map(`statusTagType`/`statusLabel`)的写法**。

```vue
<!-- 搜索栏 -->
<el-select v-model="queryParams.orderStatus" placeholder="请选择订单状态" clearable style="width: 100%">
  <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
</el-select>

<!-- 表格列 -->
<el-table-column label="订单状态" align="center" prop="orderStatus">
  <template #default="scope">
    <dict-tag :options="so_order_status" :value="scope.row.orderStatus" />
  </template>
</el-table-column>
```

> 若某状态确无对应字典(应优先补字典),才允许页面内 el-tag,但需在代码注释说明原因,并视为技术债待补字典。

### 3.5 操作列:统一行内操作列

- **所有列表页必须有行内「操作」列**,行级动作(查看/修改/删除/状态流转如确认/履行/关闭/取消)统一放在行内操作列,**不再上移工具栏**(pu/order、pu/requisition 现状即此问题)。
- 操作列统一加 `class-name="small-padding fixed-width"`。
- 列宽走 3.1 纯自适应,不设 width(按钮多时自适应会给较宽);若按钮极多导致拥挤,页面级特例可设 width。
- 顶部工具栏仅保留「新增」「导出」这类**整表级**动作 + `RightToolbar`。状态流转类按钮不进工具栏。

```vue
<el-table-column label="操作" align="center" class-name="small-padding fixed-width">
  <template #default="scope">
    <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
    <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:orderheader:edit']">修改</el-button>
    <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:orderheader:remove']">删除</el-button>
    <!-- 状态流转按钮按 v-if 控制显隐 -->
  </template>
</el-table-column>
```

### 3.6 表格列排列顺序(通用约定)

从左到右建议顺序:

1. `type="selection"`(多选框,若有)
2. `type="index"` 序号
3. 业务编号(如 soNo/woNo/orderNo/quotationNo)
4. 业务名称/关联主体(如客户名称/物料名称/供应商名称)
5. 数量/金额类(如计划数量/总金额/含税总金额)
6. 日期类(如制单日期/交货日期)
7. 状态类(如订单状态)
8. 备注(若有)
9. 操作列(始终最后)

> 此顺序为推荐约定,各页可按业务调整,但**业务编号必在最前数据列、操作列必在最后**这两条是硬性。

## 四、容器与命名规范(消除结构性差异)

| 项 | 标准 | 现状问题 |
|----|------|---------|
| 外层容器 | `<div class="app-container">` | workorder 用 `p-2 + el-card`,统一为 app-container |
| 表单 ref | `queryRef` | workorder 用 `queryFormRef`,统一为 queryRef |
| 表单 label-width | `100px` | 68/90/100/省略 混用,统一 100px |
| 表格 v-loading 变量 | `loading` | 已统一 |
| 表格数据变量 | `xxxList`(如 orderheaderList) | 已统一 |
| 搜索栏是否 inline | **否,用 el-row/el-col 网格** | 现全为 inline,改为网格 |

左侧分类树布局(material/stock)的页面:搜索栏网格标准同样适用,只是整体放在右侧 el-col 内,网格在子容器宽度下响应式收缩。

## 五、示范应用:orderheader/index.vue

### 5.1 搜索项取舍(应用四原则)

**原 11 项** → **保留 4 项**:

| 原字段 | 取舍 | 依据 |
|--------|------|------|
| soNo 销售订单编号 | **保留** | 原则1 高频检索(业务单号) |
| clientName 来源客户 | **保留** | 原则1 高频检索(业务名称) |
| orderStatus 订单状态 | **保留** | 原则1 高频检索(核心状态) |
| orderDate 订单生成日期 | **保留** | 原则1 高频检索(制单日期) |
| coId 来源客户订单 | 移除 | 原则2 低频/内部 |
| salesRepName 销售代表 | 移除 | 原则2 低频 |
| promiseDate 承诺交货日 | 移除 | 原则2 低频 |
| requestedDate 要求交货日 | 移除 | 原则2 低频 |
| creditStatus 信用状态 | 移除 | 原则3 状态取主舍次(留订单状态) |
| creditBlockedBy 信用拦截人 | 移除 | 原则2 低频/内部 |
| projectId 项目ID | 移除 | 原则2 内部字段 |

最终 4 项:销售订单编号、来源客户、订单状态、订单生成日期。`queryParams` 中被移除的字段一并删除(避免无效参数传后端)。

### 5.2 表格列调整

| 原列 | 取舍 | 处理 |
|------|------|------|
| selection | 保留 | — |
| 序号(type=index) | 保留 | 去掉异常的 `prop="soId"`,不设 width |
| 销售订单编号 soNo | 保留 | 删 width=160 |
| 来源客户 clientName | 保留 | 加 min-width + show-overflow-tooltip |
| 销售代表 salesRepName | 保留 | 删 width=100 |
| 订单生成日期 orderDate | 保留 | 删 width=120,保留 parseTime |
| 承诺交货日 promiseDate | 保留 | 删 width=120,保留 parseTime |
| 含税总金额 totalAmount | 保留 | 删 width=120 |
| 货币 currency | 保留 | 删 width=80,dict-tag 不变 |
| 信用状态 creditStatus | 保留 | 删 width=100,dict-tag 不变 |
| 订单状态 orderStatus | 保留 | 删 width=100,dict-tag 不变 |
| 操作 | 保留 | 删 width=280,保留 fixed-width |

> 表格列本次**未删备注列**(orderheader 表格原本就没有备注列);5.1 搜索项移除不等于表格列移除,两者独立。

### 5.3 操作按钮

操作列内按钮保持原样(查看/修改/删除/确认/履行/关闭/取消,按 v-if 显隐),仅删 width=280 改为自适应。工具栏的状态流转按钮(确认/履行/关闭/取消)按现状保留在工具栏(orderheader 工具栏原本就有这些且绑定选中行逻辑,不动其业务逻辑,只动搜索栏与表格列)。

> 注:工具栏按钮属 orderheader 已有的选中行联动逻辑,本次改造**不改动工具栏业务逻辑**,只规范搜索栏与表格列。3.5"状态流转不进工具栏"是通用标准,orderheader 工具栏按钮作为历史保留,新页面按标准执行。

## 六、落地范围与后续

- **本次仅改 `orderheader/index.vue` 一页**,作为标准示范样板。
- 其余 9 个抽样页面(customerorder/quotation/so-return/pu-order/pu-requisition/pu-return/material/stock/workorder)及未抽样页面,后续按本标准逐页回灌,不在本次范围。
- 回灌时注意:material/stock 的左侧分类树布局、pu-order 的展开子表、workorder 的状态字典补齐,需单独处理,不照搬 orderheader。
- workorder 的硬编码状态需先确认/补建对应字典(如 `mes_work_order_status`),再按标准改 dict-tag。

## 七、验收标准

orderheader/index.vue 改造后需满足:

1. 搜索栏为 el-row/el-col 网格,label-width=100px,无 inline 属性。
2. 搜索项精简至 4 个,queryParams 中已删字段同步删除。
3. 订单状态下拉 `width: 100%`,不再 120px 内联。
4. 表格所有列 width 已删(序号列也删,纯自适应),来源客户列有 min-width + show-overflow-tooltip。
5. 序号列去掉 `prop="soId"`。
6. 操作列删 width=280,保留 fixed-width。
7. 工具栏与状态流转业务逻辑零改动,仅搜索栏/表格列变更。
8. `npm run lint:eslint` 通过。
