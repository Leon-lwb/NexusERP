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
                <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
                <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['wm:stock:query']">查看</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:stock:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-alert
          title="库存为真实业务单据过账形成的只读事实。数量、批次、SN及库存状态需回到原收货、领退料、调拨或盘点等来源单据，按业务流程更正后查看结果。"
          type="info"
          :closable="false"
          show-icon
          class="mb8"
        />

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
          <el-table-column label="序号" type="index" align="center" width="60" />
          <el-table-column label="产品物料编码" align="center" prop="materialCode" min-width="140" show-overflow-tooltip />
          <el-table-column label="产品物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
          <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
          <el-table-column label="单位名称" align="center" prop="baseUomName" min-width="80" />
          <el-table-column label="入库批次号" align="center" prop="batchCode" min-width="150" show-overflow-tooltip />
          <el-table-column label="仓库名称" align="center" prop="warehouseName" min-width="110" show-overflow-tooltip />
          <el-table-column label="库区名称" align="center" prop="areaName" min-width="100" show-overflow-tooltip />
          <el-table-column label="库位名称" align="center" prop="locationName" min-width="100" show-overflow-tooltip />
          <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="140" show-overflow-tooltip />
          <el-table-column label="在库数量" align="center" prop="stockQuantity" min-width="90" />
          <el-table-column label="生产工单编号" align="center" prop="woNo" min-width="140" show-overflow-tooltip />
          <el-table-column label="生产日期" align="center" prop="productionDate" width="110">
            <template #default="scope">
              <span>{{ parseTime(scope.row.productionDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库时间" align="center" prop="recptDate" width="110">
            <template #default="scope">
              <span>{{ parseTime(scope.row.recptDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存有效期" align="center" prop="expireDate" width="110">
            <template #default="scope">
              <span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冻结标志" align="center" prop="frozenFlag" width="90">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="90" fixed="right" :show-overflow-tooltip="false">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['wm:stock:query']">查看</el-button>
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

        <el-dialog title="查看库存事实" v-model="open" width="960px" append-to-body>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="field in detailFields" :key="field.key" :label="field.label">
              {{ form[field.key] ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="冻结标志"><dict-tag :options="sys_true_or_false" :value="form.frozenFlag" /></el-descriptions-item>
          </el-descriptions>
          <p>如有差异，请依据来源单据核对实际过账记录，并通过原业务流程更正。</p>
          <template #footer><el-button @click="cancel">关闭</el-button></template>
        </el-dialog>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Stock">
import { listStock, getStock, getCategoryStocks } from '@/api/erp/wm/stock';
import type { StockVO } from '@/api/erp/wm/stock/types';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false } = toRefs<any>(proxy.useDict('sys_true_or_false'));
const stockRef = ref<FormInstance>();
const { categoryTree, categoryTreeRef, categoryFilterText, getCategoryTree, filterCategoryNode, debouncedFilter } = useCategoryTree(proxy);
const activeCategoryId = ref<any>(null);
const isCategoryMode = ref(false);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null,
  batchCode: null,
  warehouseName: null,
  recptDate: null,
  expireDate: null,
  frozenFlag: null
});

/** 详情只展示服务端库存事实，不加载任何物料、仓库或供应商编辑选择器。 */
const detailFields: Array<{ key: keyof StockVO; label: string }> = [
  { key: 'stockId', label: '库存ID' },
  { key: 'materialCode', label: '产品物料编码' },
  { key: 'materialName', label: '产品物料名称' },
  { key: 'materialSpec', label: '规格型号' },
  { key: 'baseUomName', label: '单位名称' },
  { key: 'stockQuantity', label: '在库数量' },
  { key: 'batchCode', label: '入库批次号' },
  { key: 'warehouseName', label: '仓库名称' },
  { key: 'areaName', label: '库区名称' },
  { key: 'locationName', label: '库位名称' },
  { key: 'vendorName', label: '供应商名称' },
  { key: 'woNo', label: '生产工单编号' },
  { key: 'productionDate', label: '生产日期' },
  { key: 'recptDate', label: '入库时间' },
  { key: 'expireDate', label: '库存有效期' }
];
const {
  loading,
  list: stockList,
  total,
  open,
  single,
  showSearch,
  form,
  getList: crudGetList,
  handleQuery: crudHandleQuery,
  handleView,
  cancel,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listStock, get: getStock },
  idField: 'stockId',
  entityName: '库存事实',
  formRef: stockRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/stock/export',
  exportFileName: 'stock'
});

/** 查询库存清单列表（分类筛选模式下走分类接口，绕过分页） */
function getList() {
  if (isCategoryMode.value && activeCategoryId.value != null) {
    loading.value = true;
    // 后端 getCategoryStocks 会自动展开子分类，无需前端再调 getAllChildrenIds
    getCategoryStocks([activeCategoryId.value])
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
  } else {
    crudGetList();
  }
}

/** 树节点点击事件：进入分类筛选模式，载入该分类（含子分类）全部库存 */
function handleNodeClick(nodeData: any) {
  activeCategoryId.value = nodeData.categoryId;
  isCategoryMode.value = true;
  getList();
}

/** 清除分类筛选，回到分页列表 */
function clearCategoryFilter() {
  activeCategoryId.value = null;
  isCategoryMode.value = false;
  categoryTreeRef.value?.setCurrentKey(undefined);
  handleQuery();
}

/** 搜索按钮操作（退出分类筛选模式，回到分页列表） */
function handleQuery() {
  isCategoryMode.value = false;
  activeCategoryId.value = null;
  categoryTreeRef.value?.setCurrentKey(undefined);
  crudHandleQuery();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
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

/* 表头单行显示，避免列名换行 */
:deep(.el-table th.el-table__cell .cell) {
  white-space: nowrap;
}
</style>
