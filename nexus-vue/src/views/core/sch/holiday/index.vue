<template>
  <div class="app-container">
    <el-card>
      <div class="calendar-header">
        <el-button type="primary" link @click="prevMonth">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span class="calendar-title">{{ currentYear }}年{{ currentMonth + 1 }}月</span>
        <el-button type="primary" link @click="nextMonth">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
        <el-button type="primary" @click="handleAdd" style="margin-left: 20px;">
          新增节假日
        </el-button>
      </div>
      
      <!-- 手动实现的日历 -->
      <div class="custom-calendar">
        <!-- 星期标题 -->
        <div class="calendar-weekdays">
          <div class="calendar-weekday" v-for="weekday in weekdays" :key="weekday">{{ weekday }}</div>
        </div>
        
        <!-- 日期网格 -->
        <div class="calendar-grid">
          <!-- 前一个月的日期 -->
          <div 
            class="calendar-day prev-month" 
            v-for="day in prevMonthDaysList" 
            :key="'prev-' + day"
          >
            <div class="day-number">{{ day }}</div>
          </div>
          
          <!-- 当前月的日期 -->
          <div 
            class="calendar-day current-month"
            :class="{ today: isToday(currentYear, currentMonth, day), weekend: isWeekend(currentYear, currentMonth, day) }"
            v-for="day in daysInMonthList" 
            :key="'current-' + day"
            @contextmenu.prevent="(event) => openContextMenu(getDateString(currentYear, currentMonth, day), event)"
          >
            <div class="day-number">{{ day }}</div>
            <div class="lunar-info">{{ getLunarDate(getDateString(currentYear, currentMonth, day)) }}</div>
            <div class="tags">
              <el-tag 
                v-if="getHolidayType(getDateString(currentYear, currentMonth, day)) === 'working'" 
                effect="dark" 
                type="warning"
                size="small"
              >
                班
              </el-tag>
              <el-tag 
                v-else-if="getHolidayType(getDateString(currentYear, currentMonth, day)) === 'holiday'" 
                effect="dark" 
                type="success"
                size="small"
              >
                休
              </el-tag>
              <el-tag 
                v-else
                effect="dark" 
                type="warning"
                size="small"
              >
                班
              </el-tag>
            </div>
            <div class="setting-button" @click.stop="openContextMenu(getDateString(currentYear, currentMonth, day), $event)">
              <el-button type="primary" size="small" circle>
                <el-icon><Setting /></el-icon>
              </el-button>
            </div>
          </div>
          
          <!-- 下一个月的日期 -->
          <div 
            class="calendar-day next-month" 
            v-for="day in nextMonthDaysList" 
            :key="'next-' + day"
          >
            <div class="day-number">{{ day }}</div>
          </div>
        </div>
      </div>
    </el-card>
    
    <!-- 添加或修改节假日对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px" append-to-body>
      <el-form ref="holidayFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="日期" prop="holidayDate">
              <el-date-picker
                v-model="form.holidayDate"
                type="date"
                value-format="YYYY-MM-DD"
                format="YYYY-MM-DD"
                placeholder="请选择日期"
                :disabled="isEdit"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="节假日名称" prop="holidayName">
              <el-input v-model="form.holidayName" placeholder="请输入节假日名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="类型" prop="holidayType">
              <el-select v-model="form.holidayType" placeholder="请选择类型" style="width: 100%">
                <el-option
                  v-for="dict in sch_holiday_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="是否调休" prop="isWorkingDay">
              <el-radio-group v-model="form.isWorkingDay">
                <el-radio
                  v-for="dict in sys_true_or_false"
                  :key="dict.value"
                  :label="parseInt(dict.value)">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button v-if="isEdit" type="danger" @click="handleDeleteCurrent">删 除</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Holiday">
import { ref, reactive, onMounted, computed } from 'vue'
import { listHoliday, getHoliday, delHoliday, addHoliday, updateHoliday } from "@/api/core/sch/holiday"
import calendar from '@/utils/calendar'

const { proxy } = getCurrentInstance()
const { sys_true_or_false, sch_holiday_type } = proxy.useDict('sys_true_or_false', 'sch_holiday_type')

// 日历相关
const currentDate = ref(new Date())
const currentYear = computed(() => currentDate.value.getFullYear())
const currentMonth = computed(() => currentDate.value.getMonth())

// 星期标题
const weekdays = ['日', '一', '二', '三', '四', '五', '六']

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

// 列表相关
const holidayList = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    year: currentYear.value
  },
  rules: {
    holidayDate: [
      { required: true, message: "日期不能为空", trigger: "blur" }
    ],
    holidayName: [
      { required: true, message: "节假日名称不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

// 获取前一个月的天数（用于填充日历开始的空位）
const prevMonthDaysList = computed(() => {
  const firstDayOfMonth = new Date(currentYear.value, currentMonth.value, 1)
  const dayOfWeek = firstDayOfMonth.getDay()
  
  if (dayOfWeek === 0) return [] // 如果第一天是周日，不需要显示前一个月的日期
  
  const prevMonth = currentMonth.value === 0 ? 11 : currentMonth.value - 1
  const prevYear = currentMonth.value === 0 ? currentYear.value - 1 : currentYear.value
  const daysInPrevMonth = new Date(prevYear, prevMonth + 1, 0).getDate()
  
  const days = []
  for (let i = daysInPrevMonth - dayOfWeek + 1; i <= daysInPrevMonth; i++) {
    days.push(i)
  }
  return days
})

// 获取当前月的天数
const daysInMonthList = computed(() => {
  return new Date(currentYear.value, currentMonth.value + 1, 0).getDate()
})

// 获取下一个月的天数（用于填充日历结尾的空位）
const nextMonthDaysList = computed(() => {
  const totalCells = 42 // 6行7列
  const usedCells = prevMonthDaysList.value.length + daysInMonthList.value
  const remainingCells = totalCells - usedCells
  
  const days = []
  for (let i = 1; i <= remainingCells; i++) {
    days.push(i)
  }
  return days
})

// 获取上个月
function prevMonth() {
  currentDate.value.setMonth(currentDate.value.getMonth() - 1)
  currentDate.value = new Date(currentDate.value) // 触发响应式更新
  getList()
}

// 获取下个月
function nextMonth() {
  currentDate.value.setMonth(currentDate.value.getMonth() + 1)
  currentDate.value = new Date(currentDate.value) // 触发响应式更新
  getList()
}

// 判断是否为周末
function isWeekend(year, month, day) {
  const date = new Date(year, month, day)
  const dayOfWeek = date.getDay()
  return dayOfWeek === 0 || dayOfWeek === 6
}

// 判断是否为今天
function isToday(year, month, day) {
  const today = new Date()
  return (
    today.getFullYear() === year &&
    today.getMonth() === month &&
    today.getDate() === day
  )
}

// 获取日期字符串
function getDateString(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

// 获取农历日期
function getLunarDate(date) {
  try {
    const solarDate = new Date(date)
    const solarYear = solarDate.getFullYear()
    const solarMonth = solarDate.getMonth() + 1
    const solarDay = solarDate.getDate()
    
    const lunarInfo = calendar.solar2lunar(solarYear, solarMonth, solarDay)
    
    if (lunarInfo && lunarInfo.IDayCn) {
      // 如果是特殊日子（初一），显示月份和日期
      if (lunarInfo.lDay === 1) {
        return lunarInfo.IMonthCn + lunarInfo.IDayCn
      }
      return lunarInfo.IDayCn
    }
    return ''
  } catch (error) {
    console.error('农历转换失败:', error)
    return ''
  }
}

// 判断是否为节日
function isFestivalDay(date) {
  try {
    const solarDate = new Date(date)
    const solarYear = solarDate.getFullYear()
    const solarMonth = solarDate.getMonth() + 1
    const solarDay = solarDate.getDate()
    
    const lunarInfo = calendar.solar2lunar(solarYear, solarMonth, solarDay)
    
    // 检查是否有公历节日、农历节日或节气
    if (lunarInfo) {
      return !!(lunarInfo.festival || lunarInfo.lunarFestival || lunarInfo.Term)
    }
    return false
  } catch (error) {
    console.error('节日判断失败:', error)
    return false
  }
}

// 获取节假日类型
function getHolidayType(date) {
  // 检查是否是自定义节假日
  const holiday = holidayList.value.find(item => item.holidayDate === date)
  if (holiday) {
    if (holiday.isWorkingDay === '1') {
      return 'working' // 调休工作日
    } else {
      return 'holiday' // 节假日
    }
  }
  return 'normal' // 普通日期
}

// 格式化显示日期（中文格式）
function formatDisplayDate(date) {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}年${(d.getMonth() + 1).toString().padStart(2, '0')}月${d.getDate().toString().padStart(2, '0')}日`
}

// 打开右键菜单（实际上直接打开对话框）
function openContextMenu(date, event) {
  // 阻止默认右键菜单
  event.preventDefault()
  event.stopPropagation()
  
  resetForm()
  form.value.holidayDate = date
  
  // 根据当前日期状态设置默认值
  const holiday = holidayList.value.find(item => item.holidayDate === date)
  if (holiday) {
    // 如果已存在设置，则进入编辑模式
    handleEdit(holiday)
  } else {
    // 否则进入新增模式
    const dateObj = new Date(date)
    form.value.holidayName = isWeekend(dateObj.getFullYear(), dateObj.getMonth(), dateObj.getDate()) ? '周末' : '自定义假日'
    form.value.holidayType = 'legal'
    form.value.isWorkingDay = '0' // 默认都是休息日，用户可根据需要改为调休
    
    dialogTitle.value = `添加节假日 - ${formatDisplayDate(date)}`
    isEdit.value = false
    dialogVisible.value = true
  }
}

// 重置表单
function resetForm() {
  form.value = {
    holidayId: null,
    holidayDate: null,
    holidayName: null,
    holidayType: null,
    isWorkingDay: '0',
    remark: null
  }
}

// 查询节假日列表
function getList() {
  queryParams.value.year = currentYear.value
  listHoliday(queryParams.value).then(response => {
    holidayList.value = response.rows
  })
}

// 新增按钮操作
function handleAdd() {
  resetForm()
  dialogTitle.value = "添加节假日"
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑按钮操作
function handleEdit(row) {
  resetForm()
  Object.assign(form.value, row)
  dialogTitle.value = `修改节假日 - ${formatDisplayDate(row.holidayDate)}`
  isEdit.value = true
  dialogVisible.value = true
}

// 删除当前节假日
function handleDeleteCurrent() {
  if (!form.value.holidayId) return;
  
  proxy.$modal.confirm('是否确认删除日期为"' + form.value.holidayDate + '"的节假日数据项？').then(function() {
    return delHoliday(form.value.holidayId);
  }).then(() => {
    proxy.$modal.msgSuccess("删除成功");
    dialogVisible.value = false;
    getList();
  }).catch(() => {});
}

// 提交表单
function submitForm() {
  proxy.$refs["holidayFormRef"].validate(valid => {
    if (valid) {
      // 添加年份字段，从 holidayDate 中截取
      const formData = {
        ...form.value,
        year: form.value.holidayDate ? new Date(form.value.holidayDate).getFullYear() : new Date().getFullYear()
      };
      
      if (isEdit.value) {
        updateHoliday(formData).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          dialogVisible.value = false
          getList()
        })
      } else {
        addHoliday(formData).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          dialogVisible.value = false
          getList()
        })
      }
    }
  })
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  min-height: 44px;
}

.calendar-title {
  font-size: 18px;
  font-weight: bold;
  flex: 1;
  text-align: center;
  margin: 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-calendar {
  padding: 10px;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-weight: bold;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.calendar-day {
  min-height: 100px;
  padding: 5px;
  border-right: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  position: relative;
}

.calendar-day.prev-month,
.calendar-day.next-month {
  background-color: #f5f7fa;
  color: #c0c4cc;
}

.calendar-day:nth-child(7n),
.calendar-day:nth-child(7n+1) {
  color: #f56c6c; /* 周末为红色 */
}

.day-number {
  font-size: 16px;
  font-weight: bold;
}

.lunar-info {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

.tags {
  position: absolute;
  top: 5px;
  right: 5px;
}

.setting-button {
  position: absolute;
  bottom: 5px;
  right: 5px;
}

.setting-button :deep(.el-button) {
  padding: 4px;
  width: 24px;
  height: 24px;
}
</style>