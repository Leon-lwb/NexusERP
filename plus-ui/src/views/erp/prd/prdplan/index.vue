<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="生产计划编号" prop="planNo">
            <el-input v-model="queryParams.planNo" placeholder="请输入生产计划编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="计划名称" prop="planName">
            <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="计划日期" prop="planDate">
            <el-date-picker
              clearable
              v-model="queryParams.planDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择计划日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_plan_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:plan:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['prd:plan:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['prd:plan:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['prd:plan:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:plan:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="生产计划编号" align="center" prop="planNo" width="140" show-overflow-tooltip />
      <el-table-column label="计划名称" align="center" prop="planName" width="180" show-overflow-tooltip />
      <el-table-column label="版本号" align="center" prop="version" width="70" />
      <el-table-column label="是否当前版本" align="center" prop="isCurrent" width="110">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
        </template>
      </el-table-column>
      <el-table-column label="参考单据号" align="center" prop="referenceNo" width="150" show-overflow-tooltip />
      <el-table-column label="计划类型" align="center" prop="planType" width="100">
        <template #default="scope">
          <dict-tag :options="prd_plan_type" :value="scope.row.planType" />
        </template>
      </el-table-column>
      <el-table-column label="计划日期" align="center" prop="planDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划开始日期" align="center" prop="startDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划结束日期" align="center" prop="endDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <dict-tag :options="prd_plan_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="80">
        <template #default="scope">
          <dict-tag :options="so_order_priority" :value="scope.row.priority" />
        </template>
      </el-table-column>
      <el-table-column label="计划员" align="center" prop="plannerName" width="90" />
      <el-table-column label="生产部门" align="center" prop="departmentName" width="140" show-overflow-tooltip />
      <el-table-column label="计划总数量" align="center" prop="totalQuantity" width="110" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:plan:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:plan:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PrdPlan">
import { listPlan, delPlan } from '@/api/erp/prd/plan';
import { useRouter } from 'vue-router';
import { onActivated } from 'vue';

const router = useRouter();
const { proxy } = getCurrentInstance();
const { prd_plan_type, prd_plan_status, so_order_priority, sys_true_or_false } = toRefs<any>(
  proxy.useDict('prd_plan_type', 'prd_plan_status', 'so_order_priority', 'sys_true_or_false')
);

const planList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    planNo: null,
    planName: null,
    planDate: null,
    status: null,
    totalQuantity: null,
    confirmedTime: null,
    releasedTime: null,
    closedTime: null
  }
});

const { queryParams } = toRefs(data);

/** 查询生产计划列表 */
function getList() {
  loading.value = true;
  listPlan(queryParams.value).then((response) => {
    planList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.planId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 修改按钮操作 */
function handleUpdate(row) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selectedRow = planList.value.find((item) => item.planId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }
  const planId = row.planId;
  router.push({
    path: '/erp/prd/prdplan/form',
    query: { id: planId, action: 'edit' }
  });
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/erp/prd/prdplan/form',
    query: { action: 'add' }
  });
}

/** 查看按钮操作 */
function handleView(row) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selectedRow = planList.value.find((item) => item.planId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }
  const planId = row.planId;
  router.push({
    path: '/erp/prd/prdplan/form',
    query: { id: planId, action: 'view' }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _planIds = row.planId || ids.value;
  proxy.$modal
    .confirm('是否确认删除生产计划编号为"' + _planIds + '"的数据项？')
    .then(function () {
      return delPlan(_planIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    'erp/prd/plan/export',
    {
      ...queryParams.value
    },
    `plan_${new Date().getTime()}.xlsx`
  );
}

onActivated(() => {
  getList();
});
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
