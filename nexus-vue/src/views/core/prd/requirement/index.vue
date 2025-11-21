<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="物料需求单编号" prop="requirementNo">
        <el-input v-model="queryParams.requirementNo" placeholder="请输入物料需求单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="生产计划ID" prop="planId">
        <el-input v-model="queryParams.planId" placeholder="请输入生产计划ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="生产计划编号" prop="planNo">
        <el-input v-model="queryParams.planNo" placeholder="请输入生产计划编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="BOM ID" prop="bomId">
        <el-input v-model="queryParams.bomId" placeholder="请输入BOM ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="需求日期" prop="requirementDate">
        <el-date-picker clearable v-model="queryParams.requirementDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择需求日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="物料种类总数" prop="totalMaterialTypes">
        <el-input v-model="queryParams.totalMaterialTypes" placeholder="请输入物料种类总数" clearable
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="需求总量" prop="totalRequiredQuantity">
        <el-input v-model="queryParams.totalRequiredQuantity" placeholder="请输入需求总量" clearable
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in prd_requirement_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划员ID" prop="plannerId">
        <el-input v-model="queryParams.plannerId" placeholder="请输入计划员ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划员" prop="plannerName">
        <el-input v-model="queryParams.plannerName" placeholder="请输入计划员" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="部门ID" prop="departmentId">
        <el-input v-model="queryParams.departmentId" placeholder="请输入部门ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="部门" prop="departmentName">
        <el-input v-model="queryParams.departmentName" placeholder="请输入部门" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['prd:requirement:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['prd:requirement:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['prd:requirement:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['prd:requirement:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['prd:requirement:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="requirementList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料需求单ID" align="center" prop="requirementId" />
      <el-table-column label="物料需求单编号" align="center" prop="requirementNo" />
      <el-table-column label="生产计划ID" align="center" prop="planId" />
      <el-table-column label="生产计划编号" align="center" prop="planNo" />
      <el-table-column label="BOM ID" align="center" prop="bomId" />
      <el-table-column label="需求日期" align="center" prop="requirementDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requirementDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="物料种类总数" align="center" prop="totalMaterialTypes" />
      <el-table-column label="需求总量" align="center" prop="totalRequiredQuantity" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="prd_requirement_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="计划员ID" align="center" prop="plannerId" />
      <el-table-column label="计划员" align="center" prop="plannerName" />
      <el-table-column label="部门ID" align="center" prop="departmentId" />
      <el-table-column label="部门" align="center" prop="departmentName" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['prd:requirement:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['prd:requirement:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />
  </div>
</template>

<script setup name="Requirement">
import { listRequirement, delRequirement } from "@/api/core/prd/requirement"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance()
const { sys_core_status, prd_requirement_status } = proxy.useDict('sys_core_status', 'prd_requirement_status')

const requirementList = ref([])
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
    requirementNo: null,
    planId: null,
    planNo: null,
    bomId: null,
    requirementDate: null,
    totalMaterialTypes: null,
    totalRequiredQuantity: null,
    status: null,
    plannerId: null,
    plannerName: null,
    departmentId: null,
    departmentName: null,
  }
})

const { queryParams } = toRefs(data)

/** 查询物料清单列表 */
function getList() {
  loading.value = true
  listRequirement(queryParams.value).then(response => {
    requirementList.value = response.rows
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
  ids.value = selection.map(item => item.requirementId)
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
    const selectedRow = requirementList.value.find(item => item.requirementId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const requirementId = row.requirementId;
  router.push({
    path: '/core/prd/requirement/form',
    query: { id: requirementId, action: 'edit' }
  });
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/core/prd/requirement/form',
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
    const selectedRow = requirementList.value.find(item => item.requirementId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const requirementId = row.requirementId;
  router.push({
    path: '/core/prd/requirement/form',
    query: { id: requirementId, action: 'view' }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _requirementIds = row.requirementId || ids.value
  proxy.$modal.confirm('是否确认删除物料清单编号为"' + _requirementIds + '"的数据项？').then(function () {
    return delRequirement(_requirementIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('prd/requirement/export', {
    ...queryParams.value
  }, `requirement_${new Date().getTime()}.xlsx`)
}

getList()
</script>