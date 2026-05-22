<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="出库单编号" prop="issueCode">
        <el-input v-model="queryParams.issueCode" placeholder="请输入出库单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in wm_misc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:miscissue:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:miscissue:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:miscissue:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:miscissue:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:miscissue:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="issueList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="出库单编号" align="center" prop="issueCode" />
      <el-table-column label="出库类型" align="center" prop="issueType" />
      <el-table-column label="仓库" align="center" prop="warehouseName" />
      <el-table-column label="出库日期" align="center" prop="issueDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.issueDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_misc_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:miscissue:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:miscissue:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MiscIssue">
import { listMiscIssue, delMiscIssue } from "@/api/erp/wm/miscissue"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance() as any
const { wm_misc_status } = toRefs<any>(proxy.useDict('wm_misc_status'))

const issueList = ref([])
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
    issueCode: null,
    status: null,
  }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listMiscIssue(queryParams.value).then((response: any) => {
    issueList.value = response.rows
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
  ids.value = selection.map((item: any) => item.issueId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  router.push({ path: '/erp/wm/miscissue/form', query: { action: 'add' } })
}

function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行修改"); return }
    const selected = issueList.value.find((item: any) => item.issueId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/wm/miscissue/form', query: { id: row.issueId, action: 'edit' } })
}

function handleView(row: any) {
  if (row && row.type && row instanceof Event) { row = null }
  if (!row) {
    if (ids.value.length !== 1) { proxy.$modal.msgWarning("请选择一条数据进行查看"); return }
    const selected = issueList.value.find((item: any) => item.issueId == ids.value[0])
    if (!selected) { proxy.$modal.msgWarning("未找到选中的数据"); return }
    row = selected
  }
  router.push({ path: '/erp/wm/miscissue/form', query: { id: row.issueId, action: 'view' } })
}

function handleDelete(row: any) {
  const _ids = row.issueId || ids.value
  proxy.$modal.confirm('是否确认删除出库单编号为"' + _ids + '"的数据项？').then(function () {
    return delMiscIssue(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/wm/miscissue/export', { ...queryParams.value }, `miscissue_${new Date().getTime()}.xlsx`)
}

getList()
</script>
