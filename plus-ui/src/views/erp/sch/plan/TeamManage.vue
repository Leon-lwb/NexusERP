<template>
  <div class="team-manage">
    <div class="header">
      <h3>班组管理</h3>
      <el-button type="primary" plain icon="Plus" @click="handleAddTeam">添加班组</el-button>
    </div>

    <div class="team-member-container">
      <!-- 左侧班组列表 -->
      <div class="team-list">
        <el-table :data="teamList" style="width: 100%" :key="'teamTable' + tableKey" @row-click="handleRowClick" highlight-current-row height="400">
          <el-table-column label="序号" type="index" width="50" />
          <el-table-column label="班组名称" prop="teamName" />
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button link type="primary" icon="Delete" @click="handleRemoveTeam(scope.row)">删除班组</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 右侧成员列表 -->
      <div class="member-list">
        <h4 v-if="selectedTeam">班组成员 - {{ selectedTeam.teamName }}</h4>
        <el-table :data="memberList" style="width: 100%; margin-top: 10px" height="400">
          <el-table-column label="序号" type="index" width="50" />
          <el-table-column label="姓名" prop="workerName" />
          <el-table-column label="工号" prop="workerCode" />
          <el-table-column label="角色" prop="role">
            <template #default="scope">
              <dict-tag :options="sch_worker_role" :value="scope.row.role" />
            </template>
          </el-table-column>
          <el-table-column label="状态" prop="status">
            <template #default="scope">
              <dict-tag :options="sys_core_status" :value="scope.row.status" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 选择班组对话框 -->
    <el-dialog title="选择班组" v-model="selectTeamDialogVisible" width="800px" append-to-body>
      <el-form :model="queryParams" ref="queryForm" :inline="true">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="班组编码" prop="teamCode">
              <el-input v-model="queryParams.teamCode" placeholder="请输入班组编码" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="班组名称" prop="teamName">
              <el-input v-model="queryParams.teamName" placeholder="请输入班组名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="班组类别" prop="teamCategory">
              <el-select v-model="queryParams.teamCategory" placeholder="请选择班组类别" clearable>
                <el-option v-for="dict in sch_team_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所属车间" prop="workshopName">
              <el-input v-model="queryParams.workshopName" placeholder="请输入所属车间" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-table :data="availableTeams" @selection-change="handleSelectionChange" v-loading="loading" height="300">
        <el-table-column type="selection" width="55" />
        <el-table-column label="班组编码" prop="teamCode" />
        <el-table-column label="班组名称" prop="teamName" />
        <el-table-column label="班组类别" prop="teamCategory">
          <template #default="scope">
            <dict-tag :options="sch_team_category" :value="scope.row.teamCategory" />
          </template>
        </el-table-column>
        <el-table-column label="所属车间" prop="workshopName" />
        <el-table-column label="成员数量" prop="memberCount" />
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="loadAvailableTeams"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmSelectedTeams">确 定</el-button>
          <el-button @click="cancelSelectTeam">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="TeamManage">
import { listPlanteam, getPlanteam, delPlanteam, addPlanteam, updatePlanteam } from '@/api/erp/sch/planteam';
import { listTeam, getTeam } from '@/api/erp/sch/team';
import type { TeamMemberVO } from '@/api/erp/sch/team/types';
import type { FormInstance } from 'element-plus';

const { proxy } = getCurrentInstance();

const { sch_team_category, sch_worker_role } = toRefs(proxy.useDict('sch_team_category', 'sch_worker_role'));
const { sys_core_status } = toRefs(proxy.useDict('sys_core_status'));

const props = defineProps({
  planId: {
    type: Number,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['team-updated']);

const teamList = ref([]);
const tableKey = ref(0);
const selectTeamDialogVisible = ref(false);
const availableTeams = ref([]);
const selectedTeams = ref([]);
const loading = ref(false);
const total = ref(0);
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  teamCode: null,
  teamName: null,
  teamCategory: null,
  workshopName: null,
  status: 1
});

const memberList = ref<TeamMemberVO[]>([]);
const selectedTeam = ref(null);
const queryForm = ref<FormInstance>();

onMounted(() => {
  loadTeams();
});

watch(
  () => props.planId,
  (newPlanId) => {
    if (newPlanId) {
      loadTeams();
    }
  }
);

function loadTeams() {
  if (props.planId) {
    // 关联班组可能超过默认 pageSize=10, 指定足够大的分页参数避免列表被截断
    listPlanteam({ planId: props.planId, pageNum: 1, pageSize: 999 }).then((response) => {
      teamList.value = response.rows;
      if (teamList.value && teamList.value.length > 0) {
        selectedTeam.value = teamList.value[0];
        loadTeamMembers(selectedTeam.value.teamId);
      } else {
        selectedTeam.value = null;
        memberList.value = [];
      }
    });
  }
}

function loadTeamMembers(teamId) {
  getTeam(teamId).then((response) => {
    const teamData = response.data;
    if (teamData && teamData.schTeamMemberList) {
      memberList.value = teamData.schTeamMemberList.filter((member) => member.status === 1);
    } else {
      memberList.value = [];
    }
  });
}

function handleRowClick(row) {
  selectedTeam.value = row;
  loadTeamMembers(row.teamId);
}

function handleAddTeam() {
  if (props.disabled) return;
  selectTeamDialogVisible.value = true;
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    teamCode: null,
    teamName: null,
    teamCategory: null,
    workshopName: null,
    status: 1
  };
  loadAvailableTeams();
}

function loadAvailableTeams() {
  loading.value = true;
  listTeam(queryParams.value)
    .then((response) => {
      const associatedTeamIds = teamList.value.map((item) => item.teamId);
      availableTeams.value = response.rows.filter((team) => !associatedTeamIds.includes(team.teamId));
      total.value = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
}

function handleSelectionChange(selection) {
  selectedTeams.value = selection;
}

function confirmSelectedTeams() {
  if (selectedTeams.value.length === 0) {
    proxy.$modal.msgWarning('请至少选择一个班组');
    return;
  }

  const promises = selectedTeams.value.map((team) => {
    const submitData = {
      planId: props.planId,
      teamId: team.teamId,
      teamName: team.teamName
    };
    return addPlanteam(submitData);
  });

  Promise.all(promises)
    .then(() => {
      proxy.$modal.msgSuccess('关联成功');
      selectTeamDialogVisible.value = false;
      loadTeams();
      emit('team-updated');
    })
    .catch(() => {
      proxy.$modal.msgError('关联失败');
    });
}

function cancelSelectTeam() {
  selectTeamDialogVisible.value = false;
  selectedTeams.value = [];
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  loadAvailableTeams();
}

function resetQuery() {
  queryForm.value?.resetFields();
  queryParams.value.pageNum = 1;
  loadAvailableTeams();
}

function handleRemoveTeam(row) {
  if (props.disabled) return;
  proxy.$modal
    .confirm('是否确认删除班组编号为"' + row.teamId + '"的数据项？')
    .then(function () {
      return delPlanteam(row.recordId);
    })
    .then(() => {
      loadTeams();
      proxy.$modal.msgSuccess('删除成功');
      emit('team-updated');
    })
    .catch(() => {});
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

.team-member-container {
  display: flex;
  gap: 20px;
}

.team-list {
  flex: 1;
  min-width: 300px;
}

.member-list {
  flex: 1;
  min-width: 300px;
}

.member-list h4 {
  margin-bottom: 10px;
}
</style>
