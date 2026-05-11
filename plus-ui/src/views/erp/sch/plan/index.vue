<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="计划编码" prop="planCode">
        <el-input v-model="queryParams.planCode" placeholder="请输入计划编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划名称" prop="planName">
        <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="开始日期" prop="startDate">
        <el-date-picker clearable v-model="queryParams.startDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择开始日期时间" :default-time="new Date(0, 0, 0, 0, 0, 0)">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束日期" prop="endDate">
        <el-date-picker clearable v-model="queryParams.endDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择结束日期时间" :default-time="new Date(0, 0, 0, 23, 59, 59)">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="周期类型" prop="periodType">
        <el-select v-model="queryParams.periodType" placeholder="请选择" clearable style="width: 120px;">
          <el-option v-for="dict in sch_plan_period_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px;">
          <el-option v-for="dict in sch_plan_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="发布人" prop="publishedBy">
        <el-input v-model="queryParams.publishedBy" placeholder="请输入发布人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="发布时间" prop="publishedTime">
        <el-date-picker clearable v-model="queryParams.publishedTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择发布时间" :default-time="new Date(0, 0, 0, 0, 0, 0)">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:plan:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single || selectedRow?.status === 'published'" @click="handleUpdate"
          v-hasPermi="['sch:plan:edit']" v-if="selectedRow?.status !== 'published'">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['sch:plan:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['sch:plan:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['sch:plan:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange" @row-click="handleRowClick">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="计划编码" align="center" prop="planCode" />
      <el-table-column label="计划名称" align="center" prop="planName" />
      <el-table-column label="开始日期" align="center" prop="startDate" width="180">
        <template #default="scope">
          <span>{{ scope.row.startDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束日期" align="center" prop="endDate" width="180">
        <template #default="scope">
          <span>{{ scope.row.endDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="周期类型" align="center" prop="periodType">
        <template #default="scope">
          <dict-tag :options="sch_plan_period_type" :value="scope.row.periodType" />
        </template>
      </el-table-column>
      <el-table-column label="轮班方式" align="center" prop="rotationType">
        <template #default="scope">
          <dict-tag :options="sch_rotation_type" :value="scope.row.rotationType" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sch_plan_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="发布人" align="center" prop="publishedBy" />
      <el-table-column label="发布时间" align="center" prop="publishedTime" width="180">
        <template #default="scope">
          <span>{{ scope.row.publishedTime || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['sch:plan:edit']" v-if="scope.row.status !== 'published'">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['sch:plan:remove']">删除</el-button>
          <el-button link type="primary" icon="Operation" @click="handleSchedule(scope.row)"
            v-if="scope.row.status === 'published'">排班</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改排班计划对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="计划编码" prop="planCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.planCode" placeholder="请输入计划编码" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange"
                  :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker clearable v-model="form.startDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择开始日期时间" style="width: 100%" :default-time="new Date(0, 0, 0, 0, 0, 0)">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker clearable v-model="form.endDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择结束日期时间" style="width: 100%" :default-time="new Date(0, 0, 0, 23, 59, 59)">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="周期类型" prop="periodType">
              <el-select v-model="form.periodType" placeholder="请选择周期类型" style="width: 100%">
                <el-option v-for="dict in sch_plan_period_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮班方式" prop="rotationType">
              <el-select v-model="form.rotationType" placeholder="请选择轮班方式" style="width: 100%">
                <el-option v-for="dict in sch_rotation_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="发布人" prop="publishedBy">
              <el-input v-model="form.publishedBy" placeholder="请输入发布人" :disabled="true" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布时间" prop="publishedTime">
              <el-date-picker clearable v-model="form.publishedTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择发布时间" style="width: 100%" :default-time="new Date(0, 0, 0, 0, 0, 0)" :disabled="true">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 班次和班组标签页 -->
      <el-tabs v-model="activeTab" v-if="form.planId" class="mt10">
        <el-tab-pane label="班次" name="shift">
          <ShiftManage :planId="form.planId" :disabled="isViewMode" @shift-updated="onShiftUpdated" />
        </el-tab-pane>
        <el-tab-pane label="班组" name="team">
          <TeamManage :planId="form.planId" :disabled="isViewMode" @team-updated="onTeamUpdated" />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button type="success" @click="handlePublish" v-if="!isViewMode && form.status === 'draft'">发 布</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Plan">
import { listPlan, getPlan, delPlan, addPlan, updatePlan } from "@/api/erp/sch/plan"
import { useUserStore } from '@/store/modules/user'
import ShiftManage from './ShiftManage.vue'
import TeamManage from './TeamManage.vue'

const { proxy } = getCurrentInstance()
const { sch_plan_period_type, sch_plan_status, sch_rotation_type } = toRefs(proxy.useDict('sch_plan_period_type', 'sch_plan_status', 'sch_rotation_type'))

const autoGenFlag = ref(false)
const planList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)
const activeTab = ref('shift')
const selectedRow = ref({})
const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    planCode: null,
    planName: null,
    startDate: null,
    endDate: null,
    periodType: null,
    rotationType: null,
    status: null,
    publishedBy: null,
    publishedTime: null,
  },
  rules: {
    planCode: [
      { required: true, message: "计划编码不能为空", trigger: "blur" }
    ],
    planName: [
      { required: true, message: "计划名称不能为空", trigger: "blur" }
    ],
    startDate: [
      { required: true, message: "开始日期不能为空", trigger: "blur" }
    ],
    endDate: [
      { required: true, message: "结束日期不能为空", trigger: "blur" }
    ],
    rotationType: [
      { required: true, message: "轮班方式不能为空", trigger: "change" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listPlan(queryParams.value).then(response => {
    planList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function handleSchedule(row) {
  reset()
  isViewMode.value = true
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "排班管理"
    activeTab.value = 'shift'
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  autoGenFlag.value = false
  form.value = {
    planId: null,
    planCode: null,
    planName: null,
    startDate: null,
    endDate: null,
    periodType: null,
    rotationType: null,
    status: null,
    publishedBy: null,
    publishedTime: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("planRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.planId)
  single.value = selection.length != 1
  multiple.value = !selection.length
  if(selection.length === 1) {
    selectedRow.value = selection[0]
  } else {
    selectedRow.value = {}
  }
}

function handleRowClick(row) {
  selectedRow.value = row
}

function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加排班计划"
}

function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改排班计划"
  })
}

function handleView(row) {
  reset()
  isViewMode.value = true
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看排班计划"
  })
}

function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {
      if (form.value.planId != null) {
        updatePlan(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPlan(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handlePublish() {
  const currentUser = useUserStore().name || 'admin';
  form.value.publishedBy = currentUser;
  form.value.publishedTime = formatDateTime(new Date());
  form.value.status = 'published';

  updatePlan(form.value).then(response => {
    proxy.$modal.msgSuccess("发布成功");
    open.value = false;
    getList();
  }).catch(error => {
    console.error("发布失败:", error);
    proxy.$modal.msgError("发布失败");
  });
}

function formatDateTime(date) {
  if (!(date instanceof Date)) {
    date = new Date(date);
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function handleDelete(row) {
  const _planIds = row.planId || ids.value
  proxy.$modal.confirm('是否确认删除排班计划编号为"' + _planIds + '"的数据项？').then(function () {
    return delPlan(_planIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('sch/plan/export', {
    ...queryParams.value
  }, `plan_${new Date().getTime()}.xlsx`)
}

function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    form.value.planCode = 'AUTO_' + new Date().getTime()
  } else {
    form.value.planCode = null
  }
}

function onShiftUpdated() {
}

function onTeamUpdated() {
}

getList()
</script>

<style scoped>
.mt10 {
  margin-top: 10px;
}

.shift-footer, .team-footer {
  margin-top: 10px;
  text-align: left;
}
</style>
