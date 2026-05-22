<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="报价单编号" prop="quotationNo">
        <el-input v-model="queryParams.quotationNo" placeholder="请输入报价单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户名称" prop="clientName">
        <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in so_quotation_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['so:quotation:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['so:quotation:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['so:quotation:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['so:quotation:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:quotation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="quotationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="报价单编号" align="center" prop="quotationNo" />
      <el-table-column label="客户名称" align="center" prop="clientName" />
      <el-table-column label="销售员" align="center" prop="salesRepName" />
      <el-table-column label="报价日期" align="center" prop="quotationDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.quotationDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="totalAmount" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_quotation_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:quotation:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:quotation:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Quotation">
import { listQuotation, delQuotation } from "@/api/erp/so/quotation"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance() as any
const { so_quotation_status } = toRefs<any>(proxy.useDict('so_quotation_status'))

const quotationList = ref([])
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
    quotationNo: null,
    clientName: null,
    status: null,
  }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listQuotation(queryParams.value).then((response: any) => {
    quotationList.value = response.rows
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
  ids.value = selection.map((item: any) => item.quotationId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  router.push({ path: '/erp/so/quotation/form', query: { action: 'add' } })
}

function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行修改"); return }
    const selected = quotationList.value.find((item: any) => item.quotationId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/so/quotation/form', query: { id: row.quotationId, action: 'edit' } })
}

function handleView(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行查看"); return }
    const selected = quotationList.value.find((item: any) => item.quotationId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/so/quotation/form', query: { id: row.quotationId, action: 'view' } })
}

function handleDelete(row: any) {
  const _ids = row.quotationId || ids.value
  proxy.$modal.confirm('是否确认删除报价单编号为"' + _ids + '"的数据项？').then(function () {
    return delQuotation(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/so/quotation/export', { ...queryParams.value }, `quotation_${new Date().getTime()}.xlsx`)
}

getList()
</script>
