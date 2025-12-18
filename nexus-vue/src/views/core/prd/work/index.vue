<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="工单编号" prop="woNo">
        <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划名称" prop="planName">
        <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="来源类型" prop="sourceType">
        <el-select v-model="queryParams.sourceType" placeholder="请选择来源类型" clearable style="width: 150px;">
          <el-option v-for="dict in prd_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="销售订单编号" prop="sourceNo">
        <el-input v-model="queryParams.sourceNo" placeholder="请输入销售订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入产品编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品名称" prop="materialName">
        <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划开始日期" prop="planStartDate">
        <el-date-picker clearable v-model="queryParams.planStartDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择计划开始日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="计划结束日期" prop="planEndDate">
        <el-date-picker clearable v-model="queryParams.planEndDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择计划结束日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="实际开始时间" prop="actualStartDate">
        <el-date-picker clearable v-model="queryParams.actualStartDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择实际开始时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="实际结束时间" prop="actualEndDate">
        <el-date-picker clearable v-model="queryParams.actualEndDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择实际结束时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="工单状态" prop="woStatus">
        <el-select v-model="queryParams.woStatus" placeholder="请选择工单状态" clearable style="width: 150px;">
          <el-option v-for="dict in prd_wo_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="车间名称" prop="workshopName">
        <el-input v-model="queryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工作站名称" prop="workstationName">
        <el-input v-model="queryParams.workstationName" placeholder="请输入工作站名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="生产线ID" prop="productionLineId">
        <el-input v-model="queryParams.productionLineId" placeholder="请输入生产线ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="负责人" prop="responsibleName">
        <el-input v-model="queryParams.responsibleName" placeholder="请输入负责人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="班组" prop="teamName">
        <el-input v-model="queryParams.teamName" placeholder="请输入班组" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="请选择优先级" clearable  style="width: 150px;">
          <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:work:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['prd:work:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['prd:work:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['prd:work:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['prd:work:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="工单ID" align="center" prop="woId" /> -->
      <el-table-column label="工单编号" align="center" prop="woNo" />
      <el-table-column label="计划名称" align="center" prop="planName" />
      <el-table-column label="来源类型" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="prd_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <!-- <el-table-column label="来源单据ID" align="center" prop="sourceId" /> -->
      <el-table-column label="销售订单编号" align="center" prop="sourceNo" />
      <!-- <el-table-column label="来源单据行ID" align="center" prop="sourceLineId" />
      <el-table-column label="产品物料ID" align="center" prop="materialId" /> -->
      <el-table-column label="产品编码" align="center" prop="materialCode" />
      <el-table-column label="产品名称" align="center" prop="materialName" />
      <el-table-column label="产品规格" align="center" prop="materialSpec" />
      <el-table-column label="计划数量" align="center" prop="plannedQty" />
      <el-table-column label="已完成数量" align="center" prop="completedQty" />
      <el-table-column label="报废数量" align="center" prop="scrapQty" />
      <!-- <el-table-column label="单位id" align="center" prop="unitId" /> -->
      <el-table-column label="单位名称" align="center" prop="unitName" />
      <el-table-column label="计划开始日期" align="center" prop="planStartDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planStartDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划结束日期" align="center" prop="planEndDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planEndDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实际开始时间" align="center" prop="actualStartDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.actualStartDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实际结束时间" align="center" prop="actualEndDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.actualEndDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工单状态" align="center" prop="woStatus">
        <template #default="scope">
          <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" />
        </template>
      </el-table-column>
      <!-- <el-table-column label="车间ID" align="center" prop="workshopId" /> -->
      <el-table-column label="车间名称" align="center" prop="workshopName" />
      <!-- <el-table-column label="工作站ID" align="center" prop="workstationId" /> -->
      <el-table-column label="工作站名称" align="center" prop="workstationName" />
      <!-- <el-table-column label="生产线ID" align="center" prop="productionLineId" />
      <el-table-column label="负责人ID" align="center" prop="responsibleId" /> -->
      <el-table-column label="负责人" align="center" prop="responsibleName" />
      <!-- <el-table-column label="班组ID" align="center" prop="teamId" /> -->
      <el-table-column label="班组" align="center" prop="teamName" />
      <el-table-column label="优先级" align="center" prop="priority">
        <template #default="scope">
          <dict-tag :options="so_order_priority" :value="scope.row.priority" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['prd:work:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['prd:work:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />
  </div>
</template>

<script setup name="Work">
import { listWork, delWork } from "@/api/core/prd/work"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance()
const { prd_source_type, so_order_priority, prd_wo_status } = proxy.useDict('prd_source_type', 'so_order_priority', 'prd_wo_status')

const workList = ref([])
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
    woNo: null,
    planName: null,
    sourceType: null,
    sourceId: null,
    sourceNo: null,
    sourceLineId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    plannedQty: null,
    completedQty: null,
    scrapQty: null,
    unitId: null,
    unitName: null,
    planStartDate: null,
    planEndDate: null,
    actualStartDate: null,
    actualEndDate: null,
    woStatus: null,
    workshopId: null,
    workshopName: null,
    workstationId: null,
    workstationName: null,
    productionLineId: null,
    responsibleId: null,
    responsibleName: null,
    teamId: null,
    teamName: null,
    priority: null,
  }
})

const { queryParams } = toRefs(data)

/** 查询生产工单列表 */
function getList() {
  loading.value = true
  listWork(queryParams.value).then(response => {
    workList.value = response.rows
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
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.woId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 修改按钮操作 */
function handleUpdate(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行修改");
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = workList.value.find(item => item.woId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const woId = row.woId;
  router.push({
    path: '/core/prd/work/form',
    query: { id: woId, action: 'edit' }
  });
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/core/prd/work/form',
    query: { action: 'add' }
  });
}

/** 查看按钮操作 */
function handleView(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行查看");
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = workList.value.find(item => item.woId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const woId = row.woId;
  router.push({
    path: '/core/prd/work/form',
    query: { id: woId, action: 'view' }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _woIds = row.woId || ids.value
  proxy.$modal.confirm('是否确认删除生产工单编号为"' + _woIds + '"的数据项？').then(function () {
    return delWork(_woIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('prd/work/export', {
    ...queryParams.value
  }, `work_${new Date().getTime()}.xlsx`)
}

getList()
</script>