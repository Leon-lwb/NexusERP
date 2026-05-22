<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="调拨单编号" prop="transferNo">
        <el-input v-model="queryParams.transferNo" placeholder="请输入调拨单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in wm_transfer_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:transfer:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:transfer:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:transfer:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:transfer:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:transfer:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="transferList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="调拨单编号" align="center" prop="transferNo" />
      <el-table-column label="调出仓库" align="center" prop="fromWarehouseName" />
      <el-table-column label="调拨入库" align="center" prop="toWarehouseName" />
      <el-table-column label="调拨日期" align="center" prop="transferDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.transferDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_transfer_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:transfer:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:transfer:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Transfer">
import { listTransfer, delTransfer } from "@/api/erp/wm/transfer"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance() as any
const { wm_transfer_status } = toRefs<any>(proxy.useDict('wm_transfer_status'))

const transferList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    transferNo: null,
    status: null,
  }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listTransfer(queryParams.value).then((response: any) => {
    transferList.value = response.rows
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

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.transferId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  router.push({ path: '/erp/wm/transfer/form', query: { action: 'add' } })
}

function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行修改"); return }
    const selected = transferList.value.find((item: any) => item.transferId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/wm/transfer/form', query: { id: row.transferId, action: 'edit' } })
}

function handleView(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行查看"); return }
    const selected = transferList.value.find((item: any) => item.transferId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/wm/transfer/form', query: { id: row.transferId, action: 'view' } })
}

function handleDelete(row: any) {
  const _ids = row.transferId || ids.value
  proxy.$modal.confirm('是否确认删除调拨单编号为"' + _ids + '"的数据项？').then(function () {
    return delTransfer(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/wm/transfer/export', { ...queryParams.value }, `transfer_${new Date().getTime()}.xlsx`)
}

getList()
</script>
