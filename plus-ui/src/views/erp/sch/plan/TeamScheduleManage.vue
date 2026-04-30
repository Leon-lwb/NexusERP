<template>
  <div class="app-container">

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改班组排班对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="teamScheduleRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">

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
import { listTeamSchedule, getTeamSchedule, delTeamSchedule, addTeamSchedule, updateTeamSchedule } from "@/api/erp/sch/teamSchedule"
import { listTeam } from "@/api/erp/sch/team"
import { listShift } from "@/api/erp/sch/shift"
import { listPlanteam } from "@/api/erp/sch/planteam"

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

const teamList = ref([])
const shiftList = ref([])
const allTeamList = ref([])
const selectedTeamIds = ref([])

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

function initLists(planId) {
  listPlanteam({ planId: planId }).then(response => {
    listTeamSchedule({ planId: planId }).then(scheduleResponse => {
      const currentShiftTeamIds = scheduleResponse.rows
        .filter(item => item.shiftId === form.value.shiftId)
        .map(item => item.teamId)

      const otherShiftTeamIds = scheduleResponse.rows
        .filter(item => item.shiftId !== form.value.shiftId)
        .map(item => item.teamId)

      const availableTeams = response.rows.filter(team =>
        !otherShiftTeamIds.includes(team.teamId)
      )

      allTeamList.value = availableTeams.map(team => {
        return {
          key: team.teamId,
          label: team.teamName,
          planTeamRecordId: team.recordId
        }
      })

      selectedTeamIds.value = currentShiftTeamIds
    })
  })

  listShift({ planId: planId }).then(response => {
    shiftList.value = response.rows
  })
}

function getList() {
  loading.value = true
  listTeamSchedule(queryParams.value).then(response => {
    teamScheduleList.value = response.rows
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

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.teamScheduleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加班组排班"
}

function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _teamScheduleId = row.teamScheduleId || ids.value
  getTeamSchedule(_teamScheduleId).then(response => {
    form.value = response.data
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

function submitForm() {
  proxy.$refs["teamScheduleRef"].validate(valid => {
    if (valid) {
      const deleteParams = {
        planId: form.value.planId,
        shiftId: form.value.shiftId
      }
      if (form.value.planId && form.value.shiftId) {
        listTeamSchedule(deleteParams).then(response => {
          const recordsToDelete = response.rows;
          if (recordsToDelete.length > 0) {
            const idsToDelete = recordsToDelete.map(item => item.teamScheduleId);
            if (idsToDelete.length > 0) {
              delTeamSchedule(idsToDelete.join(',')).then(() => {
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

function handleDelete(row) {
  const _teamScheduleIds = row.teamScheduleId || ids.value
  proxy.$modal.confirm('是否确认删除班组排班编号为"' + _teamScheduleIds + '"的数据项？').then(function () {
    return delTeamSchedule(_teamScheduleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function openScheduleDialog(planId, shiftId) {
  queryParams.value.planId = planId
  form.value.planId = planId
  form.value.shiftId = shiftId
  initLists(planId)
  getList()

  open.value = true
  title.value = "班组排班"
  isViewMode.value = false
}

function closeScheduleDialog() {
  open.value = false
}

function filterMethod(query, item) {
  return item.label.toLowerCase().includes(query.toLowerCase())
}

defineExpose({
  openScheduleDialog,
  closeScheduleDialog
})
</script>
