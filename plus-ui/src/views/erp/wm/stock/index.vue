<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 左侧物料分类树 -->
      <el-col :span="5" class="left-tree">
        <div class="tree-header">
          <!-- 增加分类树搜索框 -->
          <el-input
            v-model="categoryFilterText"
            placeholder="请输入分类名称"
            clearable
            prefix-icon="Search"
            class="category-search-input"
            aria-label="分类搜索"
            @input="debouncedFilter"
          />
        </div>
        <el-tree
          ref="categoryTreeRef"
          :data="categoryTree"
          node-key="categoryId"
          :props="{ children: 'children', label: 'categoryName' }"
          :expand-on-click-node="false"
          highlight-current
          :filter-node-method="filterCategoryNode"
          @node-click="handleNodeClick"
        >
          <template #default="{ node }">
            <span class="custom-tree-node">
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </el-col>

      <!-- 右侧库存列表 -->
      <el-col :span="19">
        <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="产品物料编码" prop="materialCode">
                <el-input v-model="queryParams.materialCode" placeholder="请输入产品物料编码" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="产品物料名称" prop="materialName">
                <el-input v-model="queryParams.materialName" placeholder="请输入产品物料名称" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="入库批次号" prop="batchCode">
                <el-input v-model="queryParams.batchCode" placeholder="请输入入库批次号" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="仓库名称" prop="warehouseName">
                <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="入库时间" prop="recptDate">
                <el-date-picker
                  clearable
                  v-model="queryParams.recptDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择入库时间"
                  style="width: 100%"
                >
                </el-date-picker>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="库存有效期" prop="expireDate">
                <el-date-picker
                  clearable
                  v-model="queryParams.expireDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择库存有效期"
                  style="width: 100%"
                >
                </el-date-picker>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="冻结标志" prop="frozenFlag">
                <el-select v-model="queryParams.frozenFlag" placeholder="请选择冻结标志" clearable style="width: 100%">
                  <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6" class="search-actions">
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:stock:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:stock:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:stock:remove']">删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:stock:query']">查看</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:stock:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <!-- 分类筛选模式提示条 -->
        <el-alert
          v-if="isCategoryMode"
          type="info"
          show-icon
          :closable="false"
          :title="`按分类筛选中，共 ${total} 条（分类模式不参与分页）`"
          style="margin-bottom: 8px"
        >
          <template #default>
            <el-button link type="primary" @click="clearCategoryFilter">清除分类筛选</el-button>
          </template>
        </el-alert>

        <el-table v-loading="loading" :data="stockList" @selection-change="handleSelectionChange" show-overflow-tooltip>
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="序号" type="index" align="center" />
          <el-table-column label="产品物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="产品物料名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
          <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
          <el-table-column label="单位名称" align="center" prop="baseUomName" />
          <el-table-column label="入库批次号" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="仓库编码" align="center" prop="warehouseCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="仓库名称" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
          <el-table-column label="库区编码" align="center" prop="areaCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="库区名称" align="center" prop="areaName" min-width="120" show-overflow-tooltip />
          <el-table-column label="库位编码" align="center" prop="locationCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="库位名称" align="center" prop="locationName" min-width="120" show-overflow-tooltip />
          <el-table-column label="供应商编号" align="center" prop="vendorCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
          <el-table-column label="供应商简称" align="center" prop="vendorNick" min-width="120" show-overflow-tooltip />
          <el-table-column label="在库数量" align="center" prop="stockQuantity" />
          <el-table-column label="生产工单编号" align="center" prop="woNo" min-width="120" show-overflow-tooltip />
          <el-table-column label="生产日期" align="center" prop="productionDate">
            <template #default="scope">
              <span>{{ parseTime(scope.row.productionDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库时间" align="center" prop="recptDate">
            <template #default="scope">
              <span>{{ parseTime(scope.row.recptDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存有效期" align="center" prop="expireDate">
            <template #default="scope">
              <span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冻结标志" align="center" prop="frozenFlag">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:stock:edit']">修改</el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:stock:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0 && !isCategoryMode"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />

        <!-- 添加或修改库存清单对话框 -->
        <el-dialog :title="title" v-model="open" width="960px" append-to-body>
          <el-form ref="stockRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
            <el-divider content-position="left">物料信息</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="产品物料" prop="materialId">
                  <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
                    <template #append>
                      <el-button icon="Search" :disabled="isViewMode" @click="openMaterialSelect" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料名称">
                  <el-input v-model="form.materialName" placeholder="选择物料后自动带出" disabled />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="规格型号">
                  <el-input v-model="form.materialSpec" placeholder="选择物料后自动带出" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="基本单位">
                  <el-input v-model="form.baseUomName" placeholder="选择物料后自动带出" disabled />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">库位信息</el-divider>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="仓库/库区/库位" prop="warehouseId">
                  <el-cascader
                    v-model="selectedWarehouse"
                    :options="normalizedWarehouseTree"
                    :props="cascaderProps"
                    placeholder="请选择仓库 / 库区 / 库位"
                    style="width: 100%"
                    clearable
                    @change="handleWarehouseChange"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">批次与供应商</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="入库批次号" prop="batchCode">
                  <el-input v-model="form.batchCode" placeholder="请输入入库批次号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="生产工单号" prop="woNo">
                  <el-input v-model="form.woNo" placeholder="请输入生产工单号" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="供应商" prop="vendorId">
                  <el-select
                    v-model="form.vendorId"
                    filterable
                    remote
                    reserve-option
                    :remote-method="searchVendor"
                    :loading="vendorLoading"
                    placeholder="请输入供应商名称搜索"
                    clearable
                    style="width: 100%"
                    @change="handleVendorChange"
                  >
                    <el-option v-for="v in vendorOptions" :key="v.vendorId" :label="v.vendorName" :value="v.vendorId" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="供应商简称" prop="vendorNick">
                  <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">数量与日期</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="在库数量" prop="stockQuantity">
                  <el-input-number v-model="form.stockQuantity" :min="0" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="冻结标志" prop="frozenFlag">
                  <el-radio-group v-model="form.frozenFlag">
                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :value="Number(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="生产日期" prop="productionDate">
                  <el-date-picker
                    clearable
                    v-model="form.productionDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="请选择"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="入库时间" prop="recptDate">
                  <el-date-picker clearable v-model="form.recptDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="库存有效期" prop="expireDate">
                  <el-date-picker
                    clearable
                    v-model="form.expireDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="请选择"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
              <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
            </div>
          </template>
        </el-dialog>

        <!-- 物料选择对话框 -->
        <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelected" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Stock">
import { listStock, getStock, delStock, addStock, updateStock, getCategoryStocks } from '@/api/erp/wm/stock';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import { getWareHouseTree } from '@/api/erp/wm/warehouse';
import { listVendor, getVendor } from '@/api/erp/bd/vendor';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false } = toRefs<any>(proxy.useDict('sys_true_or_false'));

const stockList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

// 分类树（共享 composable）
const { categoryTree, categoryTreeRef, categoryFilterText, getCategoryTree, filterCategoryNode, debouncedFilter } = useCategoryTree(proxy);

// 分类筛选模式：点击分类树后进入，绕过分页一次性载入该分类（含子分类）库存
const activeCategoryId = ref<any>(null);
const isCategoryMode = ref(false);

// 物料选择弹窗
const materialSelectRef = ref<any>(null);

// 仓库树 + 级联选择
const warehouseTree = ref<any[]>([]);
const selectedWarehouse = ref<any>(null);
const cascaderProps = {
  label: 'label',
  value: 'value',
  children: 'children',
  emitPath: false,
  expandTrigger: 'hover' as const,
  checkStrictly: true
};

// 供应商远程搜索
const vendorOptions = ref<any[]>([]);
const vendorLoading = ref(false);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null,
    batchCode: null,
    warehouseName: null,
    recptDate: null,
    expireDate: null,
    frozenFlag: null
  },
  rules: {
    materialId: [{ required: true, message: '产品物料不能为空', trigger: 'change' }],
    warehouseId: [{ required: true, message: '仓库/库区/库位不能为空', trigger: 'change' }],
    frozenFlag: [{ required: true, message: '冻结标志不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 标准化仓库树：统一 label/value 字段供 el-cascader 使用 */
const normalizedWarehouseTree = computed(() => {
  const convert = (nodes: any[]): any[] => {
    if (!nodes || !Array.isArray(nodes)) return [];
    return nodes.map((node: any) => {
      const newNode: any = { ...node };
      if (node.warehouseName !== undefined) {
        newNode.label = node.warehouseName;
        newNode.value = node.warehouseId;
      } else if (node.areaName !== undefined) {
        newNode.label = node.areaName;
        newNode.value = node.areaId;
      } else if (node.locationName !== undefined) {
        newNode.label = node.locationName;
        newNode.value = node.locationId;
      } else {
        newNode.label = node.name || node.label || node.warehouseCode || node.areaCode || node.locationCode || '未知节点';
        newNode.value = node.id || null;
      }
      if (node.children && node.children.length > 0) {
        newNode.children = convert(node.children);
      }
      return newNode;
    });
  };
  return convert(warehouseTree.value);
});

/** 查询库存清单列表 */
function getList() {
  loading.value = true;
  listStock(queryParams.value)
    .then((response: any) => {
      stockList.value = response.rows;
      total.value = response.total;
    })
    .catch(() => {
      proxy.$modal.msgError('查询库存列表失败');
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 树节点点击事件：进入分类筛选模式，载入该分类（含子分类）全部库存 */
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
        stockList.value = [];
        total.value = 0;
        proxy.$modal.msgError('获取库存数据失败');
      }
    })
    .catch(() => {
      stockList.value = [];
      total.value = 0;
      proxy.$modal.msgError('请求出现异常');
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 清除分类筛选，回到分页列表 */
function clearCategoryFilter() {
  activeCategoryId.value = null;
  isCategoryMode.value = false;
  categoryTreeRef.value?.setCurrentKey(undefined);
  handleQuery();
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    stockId: null,
    categoryId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    stockQuantity: null,
    woId: null,
    woNo: null,
    productionDate: null,
    recptDate: null,
    expireDate: null,
    frozenFlag: null
  };
  selectedWarehouse.value = null;
  vendorOptions.value = [];
  proxy.resetForm('stockRef');
}

/** 搜索按钮操作（退出分类模式） */
function handleQuery() {
  isCategoryMode.value = false;
  activeCategoryId.value = null;
  categoryTreeRef.value?.setCurrentKey(undefined);
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.stockId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 加载仓库树（弹窗打开时调用，已加载则跳过）。返回 Promise 以便编辑回显时等待树就绪 */
function loadWarehouseTree(): Promise<void> {
  if (warehouseTree.value.length > 0) return Promise.resolve();
  return getWareHouseTree()
    .then((res: any) => {
      warehouseTree.value = res.data || [];
    })
    .catch(() => {
      proxy.$modal.msgError('加载仓库数据失败');
    });
}

/** 在标准化仓库树中按 value 查找从根到命中节点的路径 */
function findWarehousePath(nodes: any[], value: any): any[] {
  for (const node of nodes || []) {
    if (node.value === value) return [node];
    if (node.children && node.children.length > 0) {
      const sub = findWarehousePath(node.children, value);
      if (sub.length) return [node, ...sub];
    }
  }
  return [];
}

/** 仓库级联选择变更：按选中节点层级回填仓库/库区/库位 */
function handleWarehouseChange(val: any) {
  // 先清空仓库/库区/库位相关字段
  form.value.warehouseId = null;
  form.value.warehouseCode = null;
  form.value.warehouseName = null;
  form.value.areaId = null;
  form.value.areaCode = null;
  form.value.areaName = null;
  form.value.locationId = null;
  form.value.locationCode = null;
  form.value.locationName = null;
  if (!val) return;
  const path = findWarehousePath(normalizedWarehouseTree.value, val);
  const [wh, area, loc] = path;
  if (wh) {
    form.value.warehouseId = wh.warehouseId;
    form.value.warehouseCode = wh.warehouseCode;
    form.value.warehouseName = wh.warehouseName;
  }
  if (area) {
    form.value.areaId = area.areaId;
    form.value.areaCode = area.areaCode;
    form.value.areaName = area.areaName;
  }
  if (loc) {
    form.value.locationId = loc.locationId;
    form.value.locationCode = loc.locationCode;
    form.value.locationName = loc.locationName;
  }
}

/** 打开物料选择对话框 */
function openMaterialSelect() {
  materialSelectRef.value?.open();
}

/** 物料选择回调：一次性回填物料相关字段 */
function handleMaterialSelected(row: any) {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  form.value.materialSpec = row.materialSpec;
  form.value.categoryId = row.categoryId;
  form.value.baseUomId = row.baseUomId;
  form.value.baseUomName = row.baseUomName;
}

/** 供应商远程搜索 */
function searchVendor(query: string) {
  if (!query) {
    vendorOptions.value = [];
    return;
  }
  vendorLoading.value = true;
  listVendor({ vendorName: query, pageNum: 1, pageSize: 20 })
    .then((r: any) => {
      vendorOptions.value = r.rows || [];
    })
    .catch(() => {
      vendorOptions.value = [];
    })
    .finally(() => {
      vendorLoading.value = false;
    });
}

/** 供应商选择变更：同步回填编码/名称 */
function handleVendorChange(vendorId: any) {
  const v = vendorOptions.value.find((x: any) => x.vendorId === vendorId);
  if (v) {
    form.value.vendorCode = v.vendorCode;
    form.value.vendorName = v.vendorName;
  } else {
    form.value.vendorCode = null;
    form.value.vendorName = null;
  }
}

/** 编辑/查看回显：按 vendorId 预载供应商选项 */
function loadVendorOption(vendorId: any) {
  if (!vendorId) return;
  getVendor(vendorId)
    .then((r: any) => {
      if (r.data) vendorOptions.value = [r.data];
    })
    .catch(() => {});
}

/** 编辑/查看回显：根据已有仓库/库区/库位还原级联选中值 */
function restoreWarehouseSelection() {
  const f = form.value;
  selectedWarehouse.value = f.locationId || f.areaId || f.warehouseId || null;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  loadWarehouseTree();
  open.value = true;
  title.value = '添加库存清单';
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  reset();
  isViewMode.value = false;
  const _stockId = row.stockId || ids.value;
  getStock(_stockId)
    .then((response: any) => {
      form.value = response.data;
      loadVendorOption(form.value.vendorId);
      // 等仓库树就绪后再还原级联选中值，确保回显正确
      return loadWarehouseTree();
    })
    .then(() => {
      restoreWarehouseSelection();
      open.value = true;
      title.value = '修改库存清单';
    })
    .catch(() => {
      proxy.$modal.msgError('获取库存详情失败');
    });
}

/** 查看按钮操作 */
function handleView(row: any) {
  reset();
  isViewMode.value = true;
  const _stockId = row.stockId || ids.value;
  getStock(_stockId)
    .then((response: any) => {
      form.value = response.data;
      loadVendorOption(form.value.vendorId);
      return loadWarehouseTree();
    })
    .then(() => {
      restoreWarehouseSelection();
      open.value = true;
      title.value = '查看库存清单';
    })
    .catch(() => {
      proxy.$modal.msgError('获取库存详情失败');
    });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['stockRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.stockId != null) {
        updateStock(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addStock(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  const _stockIds = row.stockId || ids.value;
  proxy.$modal
    .confirm('是否确认删除库存清单编号为"' + _stockIds + '"的数据项？')
    .then(function () {
      return delStock(_stockIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    '/erp/wm/stock/export',
    {
      ...queryParams.value
    },
    `stock_${new Date().getTime()}.xlsx`
  );
}

getList();
getCategoryTree();
</script>

<style scoped>
.left-tree {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  height: calc(100vh - 200px);
  overflow-y: auto;
}

.tree-header {
  margin-bottom: 15px;
  text-align: center;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.category-search-input {
  margin-bottom: 10px;
}
</style>
