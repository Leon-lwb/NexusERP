<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="退货单编号" prop="returnNo">
        <el-input v-model="queryParams.returnNo" placeholder="请输入退货单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="销售订单号" prop="soNo">
        <el-input v-model="queryParams.soNo" placeholder="请输入销售订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户名称" prop="clientName">
        <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in so_return_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['so:return:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['so:return:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['so:return:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['so:return:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:return:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="returnList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="退货单编号" align="center" prop="returnNo" />
      <el-table-column label="销售订单号" align="center" prop="soNo" />
      <el-table-column label="客户名称" align="center" prop="clientName" />
      <el-table-column label="退货日期" align="center" prop="returnDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.returnDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="totalAmount" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_return_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-if="scope.row.status === 'DRAFT'" v-hasPermi="['so:return:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-if="scope.row.status === 'DRAFT'" v-hasPermi="['so:return:remove']">删除</el-button>
          <el-button link type="primary" icon="Finished" @click="handleSubmit(scope.row)" v-if="scope.row.status === 'DRAFT'" v-hasPermi="['so:return:edit']">提交审批</el-button>
          <el-button link type="info" icon="View" @click="handleViewFlow(scope.row)" v-if="scope.row.status === 'APPROVING' || scope.row.status === 'CONFIRMED'" v-hasPermi="['so:return:query']">流程</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Return">
import { listReturn, delReturn, submitReturnFlow, getReturnProcId } from "@/api/erp/so/return"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance() as any
const { so_return_status } = toRefs<any>(proxy.useDict('so_return_status'))

const returnList = ref([])
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
    returnNo: null,
    soNo: null,
    clientName: null,
    status: null,
  }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listReturn(queryParams.value).then((response: any) => {
    returnList.value = response.rows
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
  ids.value = selection.map((item: any) => item.returnId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  router.push({ path: '/erp/so/return/form', query: { action: 'add' } })
}

function handleUpdate(row?: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行修改"); return }
    const selected = returnList.value.find((item: any) => item.returnId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/so/return/form', query: { id: row.returnId, action: 'edit' } })
}

function handleView(row?: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行查看"); return }
    const selected = returnList.value.find((item: any) => item.returnId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/so/return/form', query: { id: row.returnId, action: 'view' } })
}

function handleDelete(row?: any) {
  const _ids = row ? row.returnId : ids.value
  proxy.$modal.confirm('是否确认删除退货单编号为"' + _ids + '"的数据项？').then(function () {
    return delReturn(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/so/return/export', { ...queryParams.value }, `return_${new Date().getTime()}.xlsx`)
}

function handleSubmit(row: any) {
  const id = row.returnId || ids.value[0]
  if (!id) { proxy.$modal.msgWarning("请选择一条退货单"); return }
  proxy.$modal.confirm('确认提交审批退货单【' + row.returnNo + '】吗？').then(() => {
    return submitReturnFlow(id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("提交审批成功")
  }).catch(() => {})
}

function handleViewFlow(row: any) {
  const id = row.returnId || ids.value[0]
  if (!id) { proxy.$modal.msgWarning("请选择一条退货单"); return }
  getReturnProcId(id).then((res: any) => {
    if (res.code === 200 && res.data) {
      router.push('/workflow/process/detail/' + res.data)
    } else {
      proxy.$modal.msgWarning('暂无流程信息')
    }
  }).catch(() => {
    proxy.$modal.msgWarning('获取流程信息失败')
  })
}

onActivated(() => {
  getList()
})
</script>
