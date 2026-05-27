<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="物料名称" prop="materialName">
        <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="warehouseId">
        <el-select v-model="queryParams.warehouseId" placeholder="请选择仓库" clearable>
          <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:valuation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="valuationList">
      <el-table-column label="序号" type="index" width="50" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" />
      <el-table-column label="规格" align="center" prop="specification" />
      <el-table-column label="单位" align="center" prop="unitName" />
      <el-table-column label="库存数量" align="center" prop="quantity" />
      <el-table-column label="单位成本" align="center" prop="unitCost">
        <template #default="scope">
          <span style="color: #f56c6c; font-weight: bold;">{{ formatMoney(scope.row.unitCost) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存总金额" align="center" prop="totalCost">
        <template #default="scope">
          <span style="color: #f56c6c; font-weight: bold;">{{ formatMoney(scope.row.totalCost) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="仓库" align="center" prop="warehouseName" />
      <el-table-column label="库位" align="center" prop="locationName" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Valuation">
import { listValuation } from "@/api/erp/wm/valuation"
import { listWarehouse } from "@/api/erp/wm/warehouse"

const { proxy } = getCurrentInstance() as any

const valuationList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const warehouseOptions = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null,
    warehouseId: null
  }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listValuation(queryParams.value).then((response: any) => {
    valuationList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleExport() {
  proxy.download('/erp/wm/valuation/export', {
    ...queryParams.value
  }, `valuation_${new Date().getTime()}.xlsx`)
}

function formatMoney(value: number) {
  if (value === null || value === undefined) return '0.00'
  return value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function getWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((response: any) => {
    warehouseOptions.value = response.rows
  })
}

getList()
getWarehouseOptions()
</script>
