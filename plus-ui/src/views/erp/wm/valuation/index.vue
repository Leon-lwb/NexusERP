<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="仓库" prop="warehouseId">
            <el-select v-model="queryParams.warehouseId" placeholder="请选择仓库" clearable style="width: 100%">
              <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
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
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:valuation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="valuationList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="140" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" />
      <el-table-column label="规格" align="center" prop="specification" min-width="120" />
      <el-table-column label="单位" align="center" prop="unitName" min-width="80" />
      <el-table-column label="库存数量" align="center" prop="quantity" min-width="90" />
      <el-table-column label="单位成本" align="center" prop="unitCost" min-width="100">
        <template #default="scope">
          <span style="color: #f56c6c; font-weight: bold">{{ formatMoney(scope.row.unitCost) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存总金额" align="center" prop="totalCost" min-width="120">
        <template #default="scope">
          <span style="color: #f56c6c; font-weight: bold">{{ formatMoney(scope.row.totalCost) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="110" />
      <el-table-column label="库位" align="center" prop="locationName" min-width="100" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Valuation">
import { listValuation } from '@/api/erp/wm/valuation';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

// 库存计价为只读报表页面（无详情/新增/修改/删除接口），占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as any });

const warehouseOptions = ref([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null,
  warehouseId: null
});

const {
  loading,
  list: valuationList,
  total,
  showSearch,
  getList,
  handleQuery,
  handleExport
} = useCrud({
  apis: { list: listValuation, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'valuationId',
  entityName: '库存计价',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/valuation/export',
  exportFileName: 'valuation'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 格式化金额 */
function formatMoney(value: number) {
  if (value === null || value === undefined) return '0.00';
  return value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function getWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((response: any) => {
    warehouseOptions.value = response.rows;
  });
}

onActivated(() => {
  getList();
  getWarehouseOptions();
});
</script>
