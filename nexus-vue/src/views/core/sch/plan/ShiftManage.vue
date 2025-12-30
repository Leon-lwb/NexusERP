<template>
  <div class="shift-manage">
    <div class="header">
      <h3>班次管理</h3>
      <el-button type="primary" plain icon="Plus" @click="handleAddShift">新增班次</el-button>
    </div>
    
    <el-table :data="shiftList" style="margin-top: 10px;" :key="'shiftTable' + tableKey" :cell-style="getCellBackgroundStyle">
      <el-table-column label="班次编码" prop="shiftCode" />
      <el-table-column label="班次名称" prop="shiftName" />
      <el-table-column label="开始时间" prop="startTime">
        <template #default="scope">
          <span>{{ formatTimeValue(scope.row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" prop="endTime">
        <template #default="scope">
          <span>{{ formatTimeValue(scope.row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工时(小时)" prop="workHours" />
      <el-table-column label="是否跨天" prop="isCrossDay">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCrossDay"/>
        </template>
      </el-table-column>
      <el-table-column label="班次颜色" prop="color">
        <template #default="scope">
          <div class="color-tag" :style="{ backgroundColor: scope.row.color }">
            <span :style="{ color: getContrastColor(scope.row.color) }">{{ scope.row.color || '未设置' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleViewShift(scope.row)">查看</el-button>
          <el-button 
            link 
            type="primary" 
            icon="Edit" 
            @click="handleEditShift(scope.row)"
            v-if="!props.disabled">修改</el-button>
          <el-button 
            link 
            type="primary" 
            icon="Delete" 
            @click="handleDeleteShift(scope.row)"
            v-if="!props.disabled">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 班次添加/编辑对话框 -->
    <el-dialog :title="shiftDialogTitle" v-model="shiftDialogVisible" width="800px" append-to-body>
      <el-form ref="shiftFormRef" :model="shiftForm" :rules="shiftRules" label-width="120px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="shiftForm.shiftName" placeholder="请输入班次名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工时(小时)" prop="workHours">
              <el-input v-model="shiftForm.workHours" placeholder="请输入工时(小时)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker clearable
                              v-model="shiftForm.startTime"
                              value-format="HH:mm:ss"
                              placeholder="请选择开始时间"
                              style="width: 100%"
                              :disabled="isViewMode">
              </el-time-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker clearable
                              v-model="shiftForm.endTime"
                              value-format="HH:mm:ss"
                              placeholder="请选择结束时间"
                              style="width: 100%"
                              :disabled="isViewMode">
              </el-time-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="是否跨天" prop="isCrossDay">
              <el-radio-group v-model="shiftForm.isCrossDay" :disabled="isViewMode">
                <el-radio
                    v-for="dict in sys_true_or_false"
                    :key="dict.value"
                    :label="parseInt(dict.value)"
                    :disabled="isViewMode"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次类别" prop="shiftCategory">
              <el-select v-model="shiftForm.shiftCategory" placeholder="请选择班次类别" style="width: 100%" :disabled="isViewMode">
                <el-option
                    v-for="dict in sch_shift_category"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="休息开始时间" prop="restStartTime">
              <el-time-picker clearable
                              v-model="shiftForm.restStartTime"
                              value-format="HH:mm:ss"
                              placeholder="请选择休息开始时间"
                              style="width: 100%"
                              :disabled="isViewMode">
              </el-time-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="休息结束时间" prop="restEndTime">
              <el-time-picker clearable
                              v-model="shiftForm.restEndTime"
                              value-format="HH:mm:ss"
                              placeholder="请选择休息结束时间"
                              style="width: 100%"
                              :disabled="isViewMode">
              </el-time-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
              <el-input v-model="shiftForm.lateThreshold" placeholder="请输入迟到阈值(分钟)" :disabled="isViewMode"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
              <el-input v-model="shiftForm.earlyLeaveThreshold" placeholder="请输入早退阈值(分钟)" :disabled="isViewMode"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班次显示颜色" prop="color">
              <el-color-picker v-model="shiftForm.color" :disabled="isViewMode"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input v-model="shiftForm.sortOrder" placeholder="请输入排序" :disabled="isViewMode"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="shiftForm.remark" type="textarea" placeholder="请输入内容" :rows="3" :disabled="isViewMode"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitShiftForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancelShiftForm">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
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
const isViewMode = ref(false) // 新增：标识是否为查看模式

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
  isViewMode.value = false // 设置为非查看模式
  shiftForm.value.planId = props.planId
  shiftDialogTitle.value = "添加班次"
  shiftDialogVisible.value = true
}

function handleEditShift(row) {
  if (props.disabled) return
  isViewMode.value = false // 设置为非查看模式
  getShift(row.shiftId).then(response => {
    shiftForm.value = response.data
    shiftDialogTitle.value = "修改班次"
    shiftDialogVisible.value = true
  })
}

function handleViewShift(row) {
  isViewMode.value = true // 设置为查看模式
  getShift(row.shiftId).then(response => {
    shiftForm.value = response.data
    shiftDialogTitle.value = "查看班次"
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

// 获取表格行样式
function getRowStyle({ row }) {
  if (row.color) {
    const textColor = getContrastColor(row.color)
    return {
      backgroundColor: row.color,
      color: textColor
    }
  }
  return {}
}

// 获取文本对比色（根据背景色亮度决定文字颜色）
function getContrastColor(color) {
  if (!color) return '#333333'
  
  // 移除 # 符号
  const hex = color.replace('#', '')
  
  // 将十六进制颜色转换为RGB
  const r = parseInt(hex.substr(0, 2), 16)
  const g = parseInt(hex.substr(2, 2), 16)
  const b = parseInt(hex.substr(4, 2), 16)
  
  // 计算相对亮度
  const brightness = (r * 299 + g * 587 + b * 114) / 1000
  
  // 如果亮度值小于128，则返回白色，否则返回黑色
  return brightness > 128 ? '#000000' : '#FFFFFF'
}

// 设置表格行CSS类名
function tableRowClassName({ row, rowIndex }) {
  if (row.color) {
    return 'color-row-style'
  }
  return ''
}

// 设置单元格样式，为特定列设置背景色，但排除操作列
function getCellBackgroundStyle({ row, column, rowIndex, columnIndex }) {
  // 排除操作列（最后一列），不设置背景色
  // 假设有8列，操作列为第8列（索引为7）
  // 如果不是操作列且行有颜色设置，则应用背景色
  if (columnIndex < 7 && row.color) {
    const textColor = getContrastColor(row.color)
    return {
      backgroundColor: row.color,
      color: textColor
    }
  }
  return {}
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

// 格式化时间值，处理数据库中的time类型数据
function formatTimeValue(timeStr) {
  if (!timeStr) {
    return '-';
  }
  // 如果是完整日期时间格式，提取时间部分
  if (timeStr.includes('T')) {
    const date = new Date(timeStr);
    return date.toTimeString().substring(0, 8);
  }
  // 如果已经是时间格式（HH:mm:ss），直接返回
  if (typeof timeStr === 'string' && timeStr.length === 8 && timeStr.includes(':')) {
    return timeStr;
  }
  return timeStr || '-';
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

.color-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  min-width: 80px;
  text-align: center;
  font-size: 12px;
}

/* 确保操作列不应用背景色 */
:deep(.color-row-style .el-table__cell:last-child) {
  background-color: #fff !important;
}
</style>