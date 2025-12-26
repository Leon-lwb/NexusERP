<template>
  <div class="team-manage">
    <div class="header">
      <h3>班组关联</h3>
      <el-button type="primary" plain icon="Plus" @click="handleAddTeam">关联班组</el-button>
    </div>
    
    <el-table :data="teamList" style="margin-top: 10px;" :key="'teamTable' + tableKey">
      <el-table-column label="班组ID" prop="teamId" />
      <el-table-column label="班组名称" prop="teamName" />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button link type="primary" icon="Delete" @click="handleRemoveTeam(scope.row)">取消关联</el-button>
        </template>
      </el-table-column>
    </el-table>
    
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

<script setup name="TeamManage">
import { listPlanteam, getPlanteam, delPlanteam, addPlanteam, updatePlanteam } from "@/api/core/sch/planteam"

const { proxy } = getCurrentInstance()

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
const emit = defineEmits(['team-updated'])

// 班组相关数据
const teamList = ref([])
const tableKey = ref(0)
const teamDialogVisible = ref(false)
const teamForm = ref({})

// 初始化数据
onMounted(() => {
  loadTeams()
})

// 监听planId变化，更新班组列表
watch(() => props.planId, (newPlanId) => {
  if (newPlanId) {
    loadTeams()
  }
})

// 加载班组数据
function loadTeams() {
  if (props.planId) {
    listPlanteam({ planId: props.planId }).then(response => {
      teamList.value = response.rows
    })
  }
}

// 班组关联相关函数
function handleAddTeam() {
  if (props.disabled) return
  resetTeamForm()
  teamForm.value.planId = props.planId
  teamDialogVisible.value = true
}

function handleRemoveTeam(row) {
  if (props.disabled) return
  proxy.$modal.confirm('是否确认取消关联班组编号为"' + row.teamId + '"的数据项？').then(function () {
    return delPlanteam(row.recordId)
  }).then(() => {
    loadTeams()
    proxy.$modal.msgSuccess("取消关联成功")
    emit('team-updated')
  }).catch(() => { })
}

function submitTeamForm() {
  addPlanteam(teamForm.value).then(response => {
    proxy.$modal.msgSuccess("关联成功")
    teamDialogVisible.value = false
    loadTeams()
    emit('team-updated')
  })
}

function cancelTeamForm() {
  teamDialogVisible.value = false
  resetTeamForm()
}

function resetTeamForm() {
  teamForm.value = {
    planId: props.planId,
    teamId: null,
    teamName: null,
    remark: null
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