# 库存清单页 (plus-ui/src/views/erp/wm/stock/index.vue) 优化方案

> 日期: 2026-07-10 · 仅改前端,不动后端

## 一、问题诊断

当前页面是代码生成器原始产物,从未人工打磨(对照已打磨的 `wm/receive/form.vue`)。存在三类问题:

### A. 功能性 Bug
1. **`getList()` loading 泄漏**:`.then()` 内才置 `loading=false`,无 `.catch/.finally`。请求失败时表格永久转圈。
2. **分类点击破坏分页与搜索**:`handleNodeClick` 调 `getCategoryStocks` 一次性载入该分类(含子分类)全部库存,`total=stockList.length`。此后:
   - 点分页 → 触发 `getList()` → 走 `listStock(queryParams)`,**忽略分类**,表格静默切换到无关数据。
   - 搜索框的条件对分类载入的列表不生效。
3. **`frozenFlag` 类型不一致**:查询区 `el-select` 绑 `dict.value`(字符串 "0"/"1"),弹窗 `el-radio` 绑 `parseInt(dict.value)`(数字 0/1)。靠 Spring 类型转换侥幸能跑,但前后端语义混乱。

### B. 弹窗不可用(核心体验问题)
新增/修改弹窗 30+ 个纯文本输入框,含 `物料类型ID/产品物料ID/单位id/批次ID/仓库ID/库区ID/库位ID/供应商ID/生产工单ID` 等原始 ID,真实用户无法使用。

### C. 代码冗余
4. `queryParams` 塞了 20+ 字段,搜索表单只用 7 个。
5. `reset()` 含 `attr1-4/createBy/createTime/updateBy/updateTime` 等审计字段,不应出现在编辑表单。

### 补充事实(已核实)
- 后端 `WmMaterialStockController` 暴露 add/edit/delete,`insertByBo` 直接 `insert`(非 upsert、不写库存流水 `wm_material_stock_tx`)。即手动新增/修改/删除是**绕过库存账本的管理员后门**,保留但需可用。
- `sys_true_or_false` 字典值: `'0'`(否)/`'1'`(是),后端 `frozenFlag` 为 Integer。
- `MaterialVO` 含 `materialId/materialCode/materialName/materialSpec/categoryId/baseUomId/baseUomName` —— 选物料可一次性回填所有物料相关字段(含 categoryId、单位)。
- 仓位级联: 已有 `getWareHouseTree()` + `receive/form.vue` 的 `normalizedWarehouseTree`/`handleWarehouseChange`/`findPathNodes` 模式可直接复用。
- `MaterialSelectDialog` 组件已存在,`ref.open()` 打开、`@select="fn(row)"` 回填(见 `wm/transfer/form.vue`)。
- 供应商:`listVendor(query)` 可用,无专用选择弹窗,改用 `el-select` 远程搜索即可。`VendorVO` **无 vendorNick** 字段,供应商简称留空或手填。

---

## 二、改造方案

### 改动 1:列表页 Bug 修复

**(1) `getList()` 加错误兜底**
```ts
function getList() {
  loading.value = true;
  listStock(queryParams.value)
    .then((response: any) => {
      stockList.value = response.rows;
      total.value = response.total;
    })
    .catch(() => { proxy.$modal.msgError('查询库存列表失败'); })
    .finally(() => { loading.value = false; });
}
```

**(2) 分类模式独立,修复分页失效**

引入 `activeCategoryId` + `isCategoryMode`。点击分类进入"分类模式":隐藏分页组件、顶部显示"按分类筛选中"提示条 + 清除按钮;**搜索/重置自动退出分类模式**回到分页列表。保留"含子分类浏览"能力,不再出现分页静默切换。

> **实现时发现**:原 `handleNodeClick` 先调 `getAllChildrenIds` 再调 `getCategoryStocks`,但前者返回 `{categories, materials}` 对象,被原代码的 `Array.isArray(response.data)` 守卫跳过,**属死代码**;而 `getCategoryStocks` 后端本身就会展开子分类(`selectAllChildrenIds`)。故实现中**删除 `getAllChildrenIds` 调用与导入**,直接 `getCategoryStocks([categoryId])`。

```ts
const activeCategoryId = ref<any>(null);
const isCategoryMode = ref(false);

function handleNodeClick(nodeData: any) {
  activeCategoryId.value = nodeData.categoryId;
  isCategoryMode.value = true;
  loading.value = true;
  // 后端 getCategoryStocks 会自动展开子分类，无需前端再调 getAllChildrenIds
  getCategoryStocks([nodeData.categoryId])
    .then((res: any) => {
      if (res && res.code === 200) {
        stockList.value = res.data?.stocks || [];
        total.value = stockList.value.length;
      } else {
        stockList.value = []; total.value = 0;
        proxy.$modal.msgError('获取库存数据失败');
      }
    })
    .catch(() => { stockList.value = []; total.value = 0; proxy.$modal.msgError('请求出现异常'); })
    .finally(() => { loading.value = false; });
}

// 退出分类模式
function clearCategoryFilter() {
  activeCategoryId.value = null;
  isCategoryMode.value = false;
  categoryTreeRef.value?.setCurrentKey(undefined);
  handleQuery();
}

// 搜索/重置都先退出分类模式
function handleQuery() {
  isCategoryMode.value = false;
  activeCategoryId.value = null;
  categoryTreeRef.value?.setCurrentKey(undefined);
  queryParams.value.pageNum = 1;
  getList();
}
```
模板:分页组件加 `v-show="total > 0 && !isCategoryMode"`,列表上方加提示条:
```html
<el-alert v-if="isCategoryMode" type="info" show-icon :closable="false" :title="`按分类筛选中,共 ${total} 条`" style="margin-bottom: 8px;">
  <template #default><el-button link type="primary" @click="clearCategoryFilter">清除分类筛选</el-button></template>
</el-alert>
```

**(3) `frozenFlag` 统一为数字**
查询区 select: `:value="Number(dict.value)"`;弹窗 radio 保持 `:label="Number(dict.value)"`。前后端均为数字,语义一致。

### 改动 2:新增/修改弹窗完整重建

**布局**:960px 弹窗内用 `el-row :gutter="20"` + `el-col :span="8/12"` 多列,`el-divider` 分区:基本信息 / 库位信息 / 批次与供应商 / 数量与日期。`isViewMode` 时整体 `:disabled`。

**字段映射(原始 ID 全部隐藏,由选择器回填)**:

| 表单项 | 控件 | 回填字段 |
|---|---|---|
| 物料 | `MaterialSelectDialog`(`ref.open()`,`@select`) | materialId/materialCode/materialName/materialSpec/categoryId/baseUomId/baseUomName |
| 仓位 | `el-cascader`(:options=normalizedWarehouseTree, checkStrictly, emitPath=false, @change=handleWarehouseChange) | warehouseId/Code/Name、areaId/Code/Name、locationId/Code/Name |
| 供应商 | `el-select` filterable remote(:remote-method=searchVendor) | vendorId/vendorCode/vendorName(vendorNick 留空手填) |
| 批次号 | `el-input` | batchCode(batchId 不展示,留 null) |
| 生产工单号 | `el-input` | woNo(woId 不展示,留 null) |
| 在库数量 | `el-input-number`(:min=0) | stockQuantity |
| 生产/入库/有效期 | `el-date-picker` | productionDate/recptDate/expireDate |
| 冻结标志 | `el-radio-group`(Number) | frozenFlag |
| 规格型号 | `el-input type=textarea` | materialSpec(物料选择后自动带出,可改) |

**物料选择展示**:用带 append 搜索按钮的只读 `el-input` 显示 `materialCode`,点击打开 `MaterialSelectDialog`(复用 transfer/form.vue 模式)。

**仓位级联**:直接移植 `receive/form.vue` 的 `normalizedWarehouseTree`(computed 转换 warehouseName/areaName/locationName → label/value)、`cascaderProps`(checkStrictly+emitPath:false)、`handleWarehouseChange`(按选中节点层级回填仓/区/位 id+code+name)、`findPathNodes/findNodeById/findParentPath`。编辑回显:打开弹窗时根据已有 warehouseId/areaId/locationId 构造 cascader 选中值。

**供应商远程搜索**:
```ts
const vendorOptions = ref<any[]>([]);
const vendorLoading = ref(false);
function searchVendor(query: string) {
  if (!query) { vendorOptions.value = []; return; }
  vendorLoading.value = true;
  listVendor({ vendorCode: query, pageNum: 1, pageSize: 20 }).then((r: any) => {
    vendorOptions.value = r.rows || [];
  }).finally(() => { vendorLoading.value = false; });
}
function handleVendorChange(vendorId: any) {
  const v = vendorOptions.value.find((x) => x.vendorId === vendorId);
  if (v) { form.value.vendorCode = v.vendorCode; form.value.vendorName = v.vendorName; }
  else { form.value.vendorCode = null; form.value.vendorName = null; }
}
```
编辑回显:打开修改/查看弹窗时,若 `form.vendorId` 存在,用 `getVendor` 拉一条塞进 `vendorOptions` 以显示已选供应商。

### 改动 3:代码清理

- `queryParams` 裁剪为搜索表单实际使用的字段:`pageNum/pageSize/materialCode/materialName/batchCode/warehouseName/recptDate/expireDate/frozenFlag`(+ `categoryId` 保留以备后端 eq 查询)。删除 materialId/materialSpec/baseUomId/baseUomName/batchId/warehouseId/warehouseCode/areaId/areaCode/locationId/locationCode/vendorId/vendorCode/vendorNick/stockQuantity/woId/productionDate。
- `reset()` 去掉 `attr1-4/createBy/createTime/updateBy/updateTime`,保留业务字段;`categoryId/baseUomId` 等由物料选择器回填,初始化为 null。
- `resetQuery()` 同步退出分类模式(已在 `handleQuery` 内统一处理)。
- 同步更新 `src/api/erp/wm/stock/types.ts` 的 `StockQuery` 接口,移除已删字段(可选,保持类型与实际参数一致)。

---

## 三、文件改动清单

| 文件 | 改动 |
|---|---|
| `plus-ui/src/views/erp/wm/stock/index.vue` | 重写(template 弹窗 + script 逻辑 + 样式) |
| `plus-ui/src/api/erp/wm/stock/types.ts` | `StockQuery` 裁剪对齐(可选) |

**不改**:后端、API `index.ts`、`MaterialSelectDialog` 组件、路由、权限。

---

## 四、回归验证点

1. 列表查询/搜索/重置/分页正常,失败时不再卡转圈。
2. 点分类树 → 进入分类模式(隐藏分页、显示提示条)→ 点"清除分类筛选"或"搜索"/"重置" → 回到分页列表,无静默数据切换。
3. 新增:物料选择回填全部物料字段;仓位级联回填仓/区/位;供应商远程搜索回填;数量/日期/冻结标志提交成功。
4. 修改:打开弹窗 cascader 与供应商正确回显已选值;保存生效。
5. 查看:全表单只读。
6. 删除/导出不受影响。
7. `frozenFlag` 查询(数字)与表格 dict-tag 显示一致。