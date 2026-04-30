<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="人员ID" prop="workerId">
        <el-input v-model="queryParams.workerId" placeholder="请输入人员ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="人员姓名" prop="workerName">
        <el-input v-model="queryParams.workerName" placeholder="请输入人员姓名" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="考勤日期" prop="recordDate">
        <el-date-picker clearable v-model="queryParams.recordDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择考勤日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="考勤结果" prop="attendanceStatus">
        <el-select v-model="queryParams.attendanceStatus" placeholder="请选择考勤结果" clearable>
          <el-option v-for="dict in sch_attendance_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据来源" prop="sourceType">
        <el-select v-model="queryParams.sourceType" placeholder="请选择数据来源" clearable>
          <el-option v-for="dict in sch_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:attendance:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:attendance:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:attendance:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:attendance:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:attendance:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="attendanceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="考勤记录ID" align="center" prop="attendanceId" />
      <el-table-column label="人员ID" align="center" prop="workerId" />
      <el-table-column label="人员姓名" align="center" prop="workerName" />
      <el-table-column label="考勤日期" align="center" prop="recordDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.recordDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上班打卡时间" align="center" prop="clockInTime" width="200">
        <template #default="scope">
          <span>{{ parseTime(scope.row.clockInTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="下班打卡时间" align="center" prop="clockOutTime" width="200">
        <template #default="scope">
          <span>{{ parseTime(scope.row.clockOutTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="迟到分钟数" align="center" prop="lateMinutes" />
      <el-table-column label="早退分钟数" align="center" prop="leaveEarlyMinutes" />
      <el-table-column label="考勤结果" align="center" prop="attendanceStatus">
        <template #default="scope">
          <dict-tag :options="sch_attendance_status" :value="scope.row.attendanceStatus" />
        </template>
      </el-table-column>
      <el-table-column label="数据来源" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="sch_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:attendance:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:attendance:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="attendanceRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="人员ID" prop="workerId">
              <el-input v-model="form.workerId" placeholder="请输入人员ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人员姓名" prop="workerName">
              <el-input v-model="form.workerName" placeholder="请输入人员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考勤日期" prop="recordDate">
              <el-date-picker clearable v-model="form.recordDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择考勤日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上班打卡时间" prop="clockInTime">
              <el-date-picker clearable v-model="form.clockInTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择上班打卡时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下班打卡时间" prop="clockOutTime">
              <el-date-picker clearable v-model="form.clockOutTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择下班打卡时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考勤结果" prop="attendanceStatus">
              <el-radio-group v-model="form.attendanceStatus">
                <el-radio v-for="dict in sch_attendance_status" :key="dict.value"
                  :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据来源" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择数据来源">
                <el-option v-for="dict in sch_source_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="SchAttendance">
import { listAttendance, getAttendance, delAttendance, addAttendance, updateAttendance } from "@/api/erp/sch/attendance"

const { proxy } = getCurrentInstance()
const { sch_attendance_status, sch_source_type } = proxy.useDict('sch_attendance_status', 'sch_source_type')

const attendanceList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    workerId: null,
    workerName: null,
    recordDate: null,
    attendanceStatus: null,
    sourceType: null,
  },
  rules: {
    workerId: [{ required: true, message: "人员ID不能为空", trigger: "blur" }],
    recordDate: [{ required: true, message: "考勤日期不能为空", trigger: "blur" }],
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listAttendance(queryParams.value).then(response => {
    attendanceList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    attendanceId: null,
    workerId: null,
    workerName: null,
    recordDate: null,
    clockInTime: null,
    clockOutTime: null,
    lateMinutes: null,
    leaveEarlyMinutes: null,
    overtimeHours: null,
    attendanceStatus: null,
    sourceType: null,
    remark: null,
  }
  proxy.resetForm("attendanceRef")
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
  ids.value = selection.map(item => item.attendanceId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加考勤记录"
}

function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _attendanceId = row.attendanceId || ids.value
  getAttendance(_attendanceId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改考勤记录"
  })
}

function handleView(row) {
  reset()
  isViewMode.value = true
  const _attendanceId = row.attendanceId || ids.value
  getAttendance(_attendanceId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看考勤记录"
  })
}

function submitForm() {
  proxy.$refs["attendanceRef"].validate(valid => {
    if (valid) {
      if (form.value.attendanceId != null) {
        updateAttendance(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAttendance(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const _attendanceIds = row.attendanceId || ids.value
  proxy.$modal.confirm('是否确认删除考勤记录编号为"' + _attendanceIds + '"的数据项？').then(function () {
    return delAttendance(_attendanceIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('erp/sch/attendance/export', {
    ...queryParams.value
  }, `attendance_${new Date().getTime()}.xlsx`)
}

getList()
</script>
