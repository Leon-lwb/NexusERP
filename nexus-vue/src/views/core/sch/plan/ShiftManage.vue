<template>
  <div class="shift-manage">
    <div class="header">
      <h3>班次管理</h3>
      <el-button type="primary" plain icon="Plus" @click="handleAddShift">新增班次</el-button>
    </div>
    
    <el-table :data="shiftList" style="margin-top: 10px;" :key="'shiftTable' + tableKey">
      <el-table-column label="班次编码" prop="shiftCode" />
      <el-table-column label="班次名称" prop="shiftName" />
      <el-table-column label="开始时间" prop="startTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" prop="endTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工时(小时)" prop="workHours" />
      <el-table-column label="是否跨天" prop="isCrossDay">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCrossDay"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEditShift(scope.row)">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDeleteShift(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
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
  </div>
</template>

<script setup name="ShiftManage">
import { listShift, getShift, delShift, addShift, updateShift } from "@/api/core/sch/shift"
import { parseTime } from '@/utils/ruoyi'

const { proxy } = getCurrentInstance()
const { sys_true_or_false, sch_shift_category } = proxy.useDict('sys_true_or_false', 'sch_shift_category')

// 定义props
const props = defineProps({
  planId: {
    type: Number,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

// 定义事件
const emit = defineEmits(['shift-updated'])

// 班次相关数据
const shiftList = ref([])
const tableKey = ref(0)
const shiftDialogVisible = ref(false)
const shiftDialogTitle = ref("")
const shiftForm = ref({})
const shiftFormRef = ref(null)

const shiftRules = ref({
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
})

// 初始化数据
onMounted(() => {
  loadShifts()
})

// 监听planId变化，更新班次列表
watch(() => props.planId, (newPlanId) => {
  if (newPlanId) {
    loadShifts()
  }
})

// 加载班次数据
function loadShifts() {
  if (props.planId) {
    listShift({ planId: props.planId }).then(response => {
      shiftList.value = response.rows
    })
  }
}

// 班次管理相关函数
function handleAddShift() {
  if (props.disabled) return
  resetShiftForm()
  shiftForm.value.planId = props.planId
  shiftDialogTitle.value = "添加班次"
  shiftDialogVisible.value = true
}

function handleEditShift(row) {
  if (props.disabled) return
  getShift(row.shiftId).then(response => {
    shiftForm.value = response.data
    shiftDialogTitle.value = "修改班次"
    shiftDialogVisible.value = true
  })
}

function handleDeleteShift(row) {
  if (props.disabled) return
  proxy.$modal.confirm('是否确认删除班次编号为"' + row.shiftId + '"的数据项？').then(function () {
    return delShift(row.shiftId)
  }).then(() => {
    loadShifts()
    proxy.$modal.msgSuccess("删除成功")
    emit('shift-updated')
  }).catch(() => { })
}

function submitShiftForm() {
  shiftFormRef.value.validate(valid => {
    if (valid) {
      if (shiftForm.value.shiftId != null) {
        updateShift(shiftForm.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          shiftDialogVisible.value = false
          loadShifts()
          emit('shift-updated')
        })
      } else {
        addShift(shiftForm.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          shiftDialogVisible.value = false
          loadShifts()
          emit('shift-updated')
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
    planId: props.planId,
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
  if (shiftFormRef.value) {
    shiftFormRef.value.resetFields()
  }
}
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.header h3 {
  margin: 0;
}
</style>