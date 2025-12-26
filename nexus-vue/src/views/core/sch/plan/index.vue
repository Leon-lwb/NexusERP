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
        <el-date-picker clearable v-model="queryParams.startDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择开始日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="结束日期" prop="endDate">
        <el-date-picker clearable v-model="queryParams.endDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择结束日期">
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
        <el-date-picker clearable v-model="queryParams.publishedTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择发布时间">
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
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['sch:plan:edit']">修改</el-button>
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

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="计划ID" align="center" prop="planId" />
      <el-table-column label="计划编码" align="center" prop="planCode" />
      <el-table-column label="计划名称" align="center" prop="planName" />
      <el-table-column label="开始日期" align="center" prop="startDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束日期" align="center" prop="endDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endDate, '{y}-{m}-{d}') }}</span>
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
          <span>{{ parseTime(scope.row.publishedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['sch:plan:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['sch:plan:remove']">删除</el-button>
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
              <el-date-picker clearable v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择开始日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker clearable v-model="form.endDate" type="date" value-format="YYYY-MM-DD" 
                placeholder="请选择结束日期" style="width: 100%">
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
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status" style="width: 100%">
                <el-radio v-for="dict in sch_plan_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布人" prop="publishedBy">
              <el-input v-model="form.publishedBy" placeholder="请输入发布人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="发布时间" prop="publishedTime">
              <el-date-picker clearable v-model="form.publishedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择发布时间" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <!-- 班次和班组标签页 -->
      <el-tabs v-model="activeTab" v-if="!isViewMode && form.planId" class="mt10">
        <el-tab-pane label="班次" name="shift">
          <el-table :data="shiftList" style="width: 100%; margin-top: 10px;">
            <el-table-column prop="shiftCode" label="班次编码" width="120" />
            <el-table-column prop="shiftName" label="班次名称" width="150" />
            <el-table-column prop="startTime" label="开始时间" width="150">
              <template #default="scope">
                <span>{{ parseTime(scope.row.startTime, '{y}-{m}-{d}') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="150">
              <template #default="scope">
                <span>{{ parseTime(scope.row.endTime, '{y}-{m}-{d}') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="workHours" label="工时(小时)" width="100" />
            <el-table-column prop="isCrossDay" label="是否跨天" width="100">
              <template #default="scope">
                <dict-tag :options="sys_true_or_false" :value="scope.row.isCrossDay"/>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button link type="primary" icon="Edit" @click="handleEditShift(scope.row)">修改</el-button>
                <el-button link type="primary" icon="Delete" @click="handleDeleteShift(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="shift-footer">
            <el-button type="primary" plain icon="Plus" @click="handleAddShift">新增班次</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="班组" name="team">
          <el-table :data="teamList" style="width: 100%; margin-top: 10px;">
            <el-table-column prop="teamId" label="班组ID" width="120" />
            <el-table-column prop="teamName" label="班组名称" width="150" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button link type="primary" icon="Delete" @click="handleRemoveTeam(scope.row)">取消关联</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="team-footer">
            <el-button type="primary" plain icon="Plus" @click="handleAddTeam">关联班组</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 班次添加/编辑对话框 -->
    <el-dialog :title="shiftDialogTitle" v-model="shiftDialogVisible" width="800px" append-to-body>
      <el-form ref="shiftFormRef" :model="shiftForm" :rules="shiftRules" label-width="120px">
        <el-form-item label="班次编码" prop="shiftCode">
          <el-input v-model="shiftForm.shiftCode" placeholder="请输入班次编码" />
        </el-form-item>
        <el-form-item label="班次名称" prop="shiftName">
          <el-input v-model="shiftForm.shiftName" placeholder="请输入班次名称" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker clearable
                          v-model="shiftForm.startTime"
                          type="date"
                          value-format="YYYY-MM-DD"
                          placeholder="请选择开始时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker clearable
                          v-model="shiftForm.endTime"
                          type="date"
                          value-format="YYYY-MM-DD"
                          placeholder="请选择结束时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="工时(小时)" prop="workHours">
          <el-input v-model="shiftForm.workHours" placeholder="请输入工时(小时)" />
        </el-form-item>
        <el-form-item label="是否跨天" prop="isCrossDay">
          <el-radio-group v-model="shiftForm.isCrossDay">
            <el-radio
                v-for="dict in sys_true_or_false"
                :key="dict.value"
                :label="parseInt(dict.value)"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="休息开始时间" prop="restStartTime">
          <el-date-picker clearable
                          v-model="shiftForm.restStartTime"
                          type="date"
                          value-format="YYYY-MM-DD"
                          placeholder="请选择休息开始时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="休息结束时间" prop="restEndTime">
          <el-date-picker clearable
                          v-model="shiftForm.restEndTime"
                          type="date"
                          value-format="YYYY-MM-DD"
                          placeholder="请选择休息结束时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
          <el-input v-model="shiftForm.lateThreshold" placeholder="请输入迟到阈值(分钟)" />
        </el-form-item>
        <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
          <el-input v-model="shiftForm.earlyLeaveThreshold" placeholder="请输入早退阈值(分钟)" />
        </el-form-item>
        <el-form-item label="班次类别" prop="shiftCategory">
          <el-select v-model="shiftForm.shiftCategory" placeholder="请选择班次类别">
            <el-option
                v-for="dict in sch_shift_category"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="班次显示颜色" prop="color">
          <el-input v-model="shiftForm.color" placeholder="请输入班次显示颜色" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input v-model="shiftForm.sortOrder" placeholder="请输入排序" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="shiftForm.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitShiftForm">确 定</el-button>
          <el-button @click="cancelShiftForm">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 班组关联对话框 -->
    <el-dialog title="关联班组" v-model="teamDialogVisible" width="600px" append-to-body>
      <el-form :model="teamForm" label-width="120px">
        <el-form-item label="班组ID">
          <el-input v-model="teamForm.teamId" placeholder="请输入班组ID" />
        </el-form-item>
        <el-form-item label="班组名称">
          <el-input v-model="teamForm.teamName" placeholder="请输入班组名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitTeamForm">确 定</el-button>
          <el-button @click="cancelTeamForm">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Plan">
import { listPlan, getPlan, delPlan, addPlan, updatePlan } from "@/api/core/sch/plan"
import { genCode } from "@/api/system/autocode/rule"
import { listShift, getShift, delShift, addShift, updateShift } from "@/api/core/sch/shift"
import { listPlanteam, getPlanteam, delPlanteam, addPlanteam, updatePlanteam } from "@/api/core/sch/planteam"

const { proxy } = getCurrentInstance()
const { sch_plan_period_type, sch_plan_status, sch_rotation_type, sys_true_or_false, sch_shift_category } = proxy.useDict('sch_plan_period_type', 'sch_plan_status', 'sch_rotation_type', 'sys_true_or_false', 'sch_shift_category')

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
const isViewMode = ref(false) // 新增：标识是否为查看模式
const activeTab = ref('shift') // 新增：标签页控制

// 新增：班次和班组相关数据
const shiftList = ref([])
const teamList = ref([])
const shiftDialogVisible = ref(false)
const teamDialogVisible = ref(false)
const shiftDialogTitle = ref("")
const shiftForm = ref({})
const teamForm = ref({})

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
  },
  // 班次表单验证规则
  shiftRules: {
    shiftCode: [
      { required: true, message: "班次编码不能为空", trigger: "blur" }
    ],
    shiftName: [
      { required: true, message: "班次名称不能为空", trigger: "blur" }
    ],
    planId: [
      { required: true, message: "排班计划ID不能为空", trigger: "blur" }
    ],
    startTime: [
      { required: true, message: "开始时间不能为空", trigger: "blur" }
    ],
    endTime: [
      { required: true, message: "结束时间不能为空", trigger: "blur" }
    ],
    workHours: [
      { required: true, message: "工时(小时)不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules, shiftRules } = toRefs(data)

/** 查询排班计划列表 */
function getList() {
  loading.value = true
  listPlan(queryParams.value).then(response => {
    planList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
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
  shiftList.value = []
  teamList.value = []
  proxy.resetForm("planRef")
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
  ids.value = selection.map(item => item.planId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加排班计划"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改排班计划"
    // 加载班次和班组数据
    loadShiftsAndTeams(_planId)
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看排班计划"
  })
}

/** 提交按钮 */
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

/** 删除按钮操作 */
function handleDelete(row) {
  const _planIds = row.planId || ids.value
  proxy.$modal.confirm('是否确认删除排班计划编号为"' + _planIds + '"的数据项？').then(function () {
    return delPlan(_planIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('sch/plan/export', {
    ...queryParams.value
  }, `plan_${new Date().getTime()}.xlsx`)
}

//自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('SCH_PLAN_CODE').then(response => {
      form.value.planCode = response;
    });
  } else {
    form.value.planCode = null;
  }
}

// 班次和班组更新事件处理
function onShiftUpdated() {
  loadShiftsAndTeams(form.value.planId)
}

function onTeamUpdated() {
  loadShiftsAndTeams(form.value.planId)
}

// 加载班次和班组数据
function loadShiftsAndTeams(planId) {
  // 加载班次数据
  listShift({ planId: planId }).then(response => {
    shiftList.value = response.rows
  })
  
  // 加载班组数据
  listPlanteam({ planId: planId }).then(response => {
    teamList.value = response.rows
  })
}

// 班次管理相关函数
function handleAddShift() {
  resetShiftForm()
  shiftForm.value.planId = form.value.planId
  shiftDialogTitle.value = "添加班次"
  shiftDialogVisible.value = true
}

function handleEditShift(row) {
  getShift(row.shiftId).then(response => {
    shiftForm.value = response.data
    shiftDialogTitle.value = "修改班次"
    shiftDialogVisible.value = true
  })
}

function handleDeleteShift(row) {
  proxy.$modal.confirm('是否确认删除班次编号为"' + row.shiftId + '"的数据项？').then(function () {
    return delShift(row.shiftId)
  }).then(() => {
    loadShiftsAndTeams(form.value.planId)
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function submitShiftForm() {
  proxy.$refs["shiftFormRef"].validate(valid => {
    if (valid) {
      if (shiftForm.value.shiftId != null) {
        updateShift(shiftForm.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          shiftDialogVisible.value = false
          loadShiftsAndTeams(form.value.planId)
        })
      } else {
        addShift(shiftForm.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          shiftDialogVisible.value = false
          loadShiftsAndTeams(form.value.planId)
        })
      }
    }
  })
}

function cancelShiftForm() {
  shiftDialogVisible.value = false
  resetShiftForm()
}

function resetShiftForm() {
  shiftForm.value = {
    shiftId: null,
    shiftCode: null,
    shiftName: null,
    planId: form.value.planId,
    startTime: null,
    endTime: null,
    workHours: null,
    isCrossDay: null,
    restStartTime: null,
    restEndTime: null,
    lateThreshold: null,
    earlyLeaveThreshold: null,
    shiftCategory: null,
    color: null,
    sortOrder: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("shiftFormRef")
}

// 班组关联相关函数
function handleAddTeam() {
  resetTeamForm()
  teamForm.value.planId = form.value.planId
  teamDialogVisible.value = true
}

function handleRemoveTeam(row) {
  proxy.$modal.confirm('是否确认取消关联班组编号为"' + row.teamId + '"的数据项？').then(function () {
    return delPlanteam(row.recordId)
  }).then(() => {
    loadShiftsAndTeams(form.value.planId)
    proxy.$modal.msgSuccess("取消关联成功")
  }).catch(() => { })
}

function submitTeamForm() {
  addPlanteam(teamForm.value).then(response => {
    proxy.$modal.msgSuccess("关联成功")
    teamDialogVisible.value = false
    loadShiftsAndTeams(form.value.planId)
  })
}

function cancelTeamForm() {
  teamDialogVisible.value = false
  resetTeamForm()
}

function resetTeamForm() {
  teamForm.value = {
    planId: form.value.planId,
    teamId: null,
    teamName: null,
    remark: null
  }
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