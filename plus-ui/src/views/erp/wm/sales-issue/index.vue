<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="出库单编号" prop="issueCode">
        <el-input
          v-model="queryParams.issueCode"
          placeholder="请输入出库单编号"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="销售订单" prop="soNo">
        <el-input
          v-model="queryParams.soNo"
          placeholder="请输入销售订单编号"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户" prop="clientName">
        <el-input
          v-model="queryParams.clientName"
          placeholder="请输入客户名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="仓库" prop="warehouseName">
        <el-input
          v-model="queryParams.warehouseName"
          placeholder="请输入仓库名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="单据状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable style="width: 200px">
          <el-option
            v-for="dict in wm_sales_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="出库日期" prop="issueDate">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:salesIssue:add']">
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:salesIssue:edit']">
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:salesIssue:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:salesIssue:export']">
          导出
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="salesIssueList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="出库单编号" align="center" prop="issueCode" width="160" show-overflow-tooltip />
      <el-table-column label="出库单名称" align="center" prop="issueName" width="150" show-overflow-tooltip />
      <el-table-column label="销售订单编号" align="center" prop="soNo" width="140" />
      <el-table-column label="客户名称" align="center" prop="clientName" width="150" show-overflow-tooltip />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" width="120" />
      <el-table-column label="出库日期" align="center" prop="issueDate" width="100">
        <template #default="scope">
          <span>{{ parseTime(scope.row.issueDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="出库数量" align="center" prop="totalQuantity" width="100" />
      <el-table-column label="出库金额" align="center" prop="totalAmount" width="120">
        <template #default="scope">
          <span>{{ formatAmount(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="wm_sales_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:salesIssue:edit']"
          >
            修改
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link type="success"
            icon="Check"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['wm:salesIssue:confirm']"
          >
            确认
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link type="primary"
            icon="Finished"
            @click="handleSubmit(scope.row)"
            v-hasPermi="['wm:salesIssue:edit']"
          >
            提交审批
          </el-button>
          <el-button
            v-if="scope.row.status === 'APPROVING' || scope.row.status === 'APPROVED'"
            link type="info"
            icon="View"
            @click="handleViewFlow(scope.row)"
            v-hasPermi="['wm:salesIssue:query']"
          >
            流程
          </el-button>
          <el-button
            v-if="scope.row.status === 'CONFIRMED'"
            link type="success"
            icon="CircleCheck"
            @click="handleComplete(scope.row)"
            v-hasPermi="['wm:salesIssue:complete']"
          >
            完成
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['wm:salesIssue:remove']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup lang="ts" name="SalesIssue">
import { listSalesIssue, delSalesIssue, confirmSalesIssue, completeSalesIssue, submitSalesIssueFlow, getSalesIssueProcId } from "@/api/erp/wm/sales-issue"
import { useRouter } from 'vue-router'

const router = useRouter()
const { proxy } = getCurrentInstance() as any
const { wm_sales_status } = toRefs<any>(proxy.useDict('wm_sales_status'))

const salesIssueList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const dateRange = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    issueCode: null,
    issueName: null,
    soNo: null,
    clientName: null,
    warehouseName: null,
    status: null,
    issueDate: null
  }
})

const { queryParams } = toRefs(data)

/** 查询销售出库列表 */
function getList() {
  loading.value = true
  listSalesIssue(proxy.addDateRange(queryParams.value, dateRange.value)).then((response: any) => {
    salesIssueList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.issueId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { action: 'add' }
  })
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  const issueId = row.issueId || ids.value[0]
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { id: issueId, action: 'edit' }
  })
}

/** 查看按钮操作 */
function handleView(row: any) {
  const issueId = row.issueId
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { id: issueId, action: 'view' }
  })
}

/** 提交审批 */
function handleSubmit(row: any) {
  proxy.$modal.confirm('确认提交审批出库单【' + row.issueCode + '】吗？').then(() => {
    return submitSalesIssueFlow(row.issueId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("提交审批成功")
  }).catch(() => {})
}

/** 查看流程 */
function handleViewFlow(row: any) {
  getSalesIssueProcId(row.issueId).then((res: any) => {
    if (res.code === 200 && res.data) {
      router.push('/workflow/process/detail/' + res.data)
    } else {
      proxy.$modal.msgWarning('暂无流程信息')
    }
  }).catch(() => {
    proxy.$modal.msgWarning('获取流程信息失败')
  })
}

/** 确认按钮操作 */
function handleConfirm(row: any) {
  proxy.$modal.confirm('确认要将出库单【' + row.issueCode + '】状态变更为已确认吗？').then(function () {
    return confirmSalesIssue(row.issueId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("确认成功")
  }).catch(() => {})
}

/** 完成按钮操作 */
function handleComplete(row: any) {
  proxy.$modal.confirm('确认要完成出库单【' + row.issueCode + '】吗？完成后将扣减库存！').then(function () {
    return completeSalesIssue(row.issueId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("出库完成")
  }).catch(() => {})
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  const _issueIds = row.issueId || ids.value
  proxy.$modal.confirm('是否确认删除销售出库编号为"' + _issueIds + '"的数据项？').then(function () {
    return delSalesIssue(_issueIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('/erp/wm/sales-issue/export', {
    ...queryParams.value
  }, `sales-issue_${new Date().getTime()}.xlsx`)
}

/** 格式化金额 */
function formatAmount(amount: any) {
  if (!amount) return '0.00'
  return parseFloat(amount).toFixed(2)
}

onActivated(() => {
  getList()
})
</script>
