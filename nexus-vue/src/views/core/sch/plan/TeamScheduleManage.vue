<template>
  <div class="app-container">
    
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改班组排班对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="teamScheduleRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <!-- <el-form-item label="排班计划ID" prop="planId">
          <el-input v-model="form.planId" placeholder="排班计划ID" :disabled="true" />
        </el-form-item> -->
        
        <el-form-item label="班次" prop="shiftId">
          <el-select v-model="form.shiftId" placeholder="请选择班次" :disabled="true">
            <el-option v-for="shift in shiftList" :key="shift.shiftId" :label="shift.shiftName" :value="shift.shiftId" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="班组分配" prop="teamIds">
          <el-transfer
            v-model="selectedTeamIds"
            filterable
            :filter-method="filterMethod"
            filter-placeholder="请输入班组名称"
            :data="allTeamList"
            :titles="['待分配班组', '已分配班组']"
            :button-texts="['取消分配', '分配班组']"
            :disabled="isViewMode"
            style="text-align: left; display: inline-block"
          >
          </el-transfer>
        </el-form-item>
        
        <!-- <el-form-item label="是否节假日" prop="isHoliday">
          <el-radio-group v-model="form.isHoliday" :disabled="isViewMode">
            <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
              :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item> -->
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
        </el-form-item>
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

<script setup name="TeamScheduleManage">
import { listTeamSchedule, getTeamSchedule, delTeamSchedule, addTeamSchedule, updateTeamSchedule } from "@/api/core/sch/teamSchedule"
import { listTeam } from "@/api/core/sch/team"
import { listShift } from "@/api/core/sch/shift"
import { listPlanteam } from "@/api/core/sch/planteam" // 获取计划关联的班组

const { proxy } = getCurrentInstance()
const { sys_true_or_false } = proxy.useDict('sys_true_or_false')

const teamScheduleList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)

const teamList = ref([]) // 班组列表
const shiftList = ref([]) // 班次列表
const allTeamList = ref([]) // 所有班组列表（用于穿梭框）
const selectedTeamIds = ref([]) // 已选择的班组ID

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    planId: null,
    teamId: null,
    shiftId: null,
    scheduledDate: null,
    weekDay: null,
    isHoliday: null,
  },
  rules: {
    planId: [
      { required: true, message: "排班计划ID不能为空", trigger: "blur" }
    ],
    shiftId: [
      { required: true, message: "班次ID不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

// 初始化班组和班次列表
function initLists(planId) {
  // 获取计划关联的班组列表（用于穿梭框左侧）
  listPlanteam({ planId: planId }).then(response => {
    // 获取当前计划下所有已分配的班组，按班次分组
    listTeamSchedule({ planId: planId }).then(scheduleResponse => {
      // 获取当前班次已分配的班组ID
      const currentShiftTeamIds = scheduleResponse.rows
        .filter(item => item.shiftId === form.value.shiftId)
        .map(item => item.teamId)
      
      // 获取其他班次已分配的班组ID
      const otherShiftTeamIds = scheduleResponse.rows
        .filter(item => item.shiftId !== form.value.shiftId)
        .map(item => item.teamId)
      
      // 过滤掉其他班次已使用的班组，只保留当前计划关联的班组中未被其他班次使用的班组
      const availableTeams = response.rows.filter(team => 
        !otherShiftTeamIds.includes(team.teamId)
      )
      
      allTeamList.value = availableTeams.map(team => {
        return {
          key: team.teamId,
          label: team.teamName,
          planTeamRecordId: team.recordId // 保存关联记录ID
        }
      })
      
      // 设置当前班次已分配的班组到穿梭框右侧
      selectedTeamIds.value = currentShiftTeamIds
    })
  })
  
  // 获取班次列表
  listShift({ planId: planId }).then(response => {
    shiftList.value = response.rows
  })
}

/** 查询班组排班列表 */
function getList() {
  loading.value = true
  listTeamSchedule(queryParams.value).then(response => {
    teamScheduleList.value = response.rows
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
  form.value = {
    teamScheduleId: null,
    planId: null,
    teamId: null,
    shiftId: null,
    scheduledDate: null,
    weekDay: null,
    isHoliday: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  selectedTeamIds.value = []
  proxy.resetForm("teamScheduleRef")
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
  ids.value = selection.map(item => item.teamScheduleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加班组排班"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _teamScheduleId = row.teamScheduleId || ids.value
  getTeamSchedule(_teamScheduleId).then(response => {
    form.value = response.data
    // 获取当前排班计划下已分配给该班次的班组
    listTeamSchedule({
      planId: response.data.planId,
      shiftId: response.data.shiftId,
      scheduledDate: response.data.scheduledDate
    }).then(schResponse => {
      selectedTeamIds.value = schResponse.rows.map(item => item.teamId)
      open.value = true
      title.value = "修改班组排班"
    })
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["teamScheduleRef"].validate(valid => {
    if (valid) {
      // 保存所有选中的班组到当前班次
      // 先删除当前班次的所有班组分配（根据planId和shiftId）
      const deleteParams = {
        planId: form.value.planId,
        shiftId: form.value.shiftId
      }
      if (form.value.planId && form.value.shiftId) {
        // 获取当前班次的所有排班记录ID进行删除
        listTeamSchedule(deleteParams).then(response => {
          const recordsToDelete = response.rows;
          if (recordsToDelete.length > 0) {
            const idsToDelete = recordsToDelete.map(item => item.teamScheduleId);
            // 批量删除现有记录
            if (idsToDelete.length > 0) {
              delTeamSchedule(idsToDelete.join(',')).then(() => {
                // 删除完成后，创建新的分配记录
                const addPromises = []
                selectedTeamIds.value.forEach(teamId => {
                  const newRecord = {
                    planId: form.value.planId,
                    teamId: teamId,
                    shiftId: form.value.shiftId,
                    isHoliday: form.value.isHoliday,
                    remark: form.value.remark
                  }
                  addPromises.push(addTeamSchedule(newRecord))
                })
                
                Promise.all(addPromises).then(() => {
                  proxy.$modal.msgSuccess("保存成功")
                  open.value = false
                  getList()
                }).catch(error => {
                  proxy.$modal.msgError("保存失败: " + error.message)
                })
              }).catch(error => {
                proxy.$modal.msgError("删除失败: " + error.message)
              })
            } else {
              // 如果没有要删除的记录，直接创建新的分配记录
              const addPromises = []
              selectedTeamIds.value.forEach(teamId => {
                const newRecord = {
                  planId: form.value.planId,
                  teamId: teamId,
                  shiftId: form.value.shiftId,
                  isHoliday: form.value.isHoliday,
                  remark: form.value.remark
                }
                addPromises.push(addTeamSchedule(newRecord))
              })
              
              Promise.all(addPromises).then(() => {
                proxy.$modal.msgSuccess("保存成功")
                open.value = false
                getList()
              }).catch(error => {
                proxy.$modal.msgError("保存失败: " + error.message)
              })
            }
          } else {
            // 如果没有要删除的记录，直接创建新的分配记录
            const addPromises = []
            selectedTeamIds.value.forEach(teamId => {
              const newRecord = {
                planId: form.value.planId,
                teamId: teamId,
                shiftId: form.value.shiftId,
                isHoliday: form.value.isHoliday,
                remark: form.value.remark
              }
              addPromises.push(addTeamSchedule(newRecord))
            })
            
            Promise.all(addPromises).then(() => {
              proxy.$modal.msgSuccess("保存成功")
              open.value = false
              getList()
            }).catch(error => {
              proxy.$modal.msgError("保存失败: " + error.message)
            })
          }
        })
      } else {
        // 创建新的分配记录（如果没有现有记录需要删除）
        const addPromises = []
        selectedTeamIds.value.forEach(teamId => {
          const newRecord = {
            planId: form.value.planId,
            teamId: teamId,
            shiftId: form.value.shiftId,
            isHoliday: form.value.isHoliday,
            remark: form.value.remark
          }
          addPromises.push(addTeamSchedule(newRecord))
        })
        
        Promise.all(addPromises).then(() => {
          proxy.$modal.msgSuccess("保存成功")
          open.value = false
          getList()
        }).catch(error => {
          proxy.$modal.msgError("保存失败: " + error.message)
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _teamScheduleIds = row.teamScheduleId || ids.value
  proxy.$modal.confirm('是否确认删除班组排班编号为"' + _teamScheduleIds + '"的数据项？').then(function () {
    return delTeamSchedule(_teamScheduleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

// 打开排班对话框
function openScheduleDialog(planId, shiftId) {
  queryParams.value.planId = planId
  form.value.planId = planId
  form.value.shiftId = shiftId // 默认选择当前班次
  initLists(planId)
  getList()
  
  open.value = true
  title.value = "班组排班"
  isViewMode.value = false
}

// 关闭对话框
function closeScheduleDialog() {
  open.value = false
}

// 穿梭框过滤方法
function filterMethod(query, item) {
  return item.label.toLowerCase().includes(query.toLowerCase())
}

defineExpose({
  openScheduleDialog,
  closeScheduleDialog
})
</script>