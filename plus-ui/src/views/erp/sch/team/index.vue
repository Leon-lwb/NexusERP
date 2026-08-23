<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="班组编码" prop="teamCode">
            <el-input v-model="queryParams.teamCode" placeholder="请输入班组编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班组名称" prop="teamName">
            <el-input v-model="queryParams.teamName" placeholder="请输入班组名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班组类别" prop="teamCategory">
            <el-select v-model="queryParams.teamCategory" placeholder="请选择班组类别" clearable style="width: 100%">
              <el-option v-for="dict in sch_team_category" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="所属车间名称" prop="workshopName">
            <el-input v-model="queryParams.workshopName" placeholder="请输入所属车间名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:team:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:team:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:team:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:team:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:team:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="teamList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="班组编码" align="center" prop="teamCode" />
      <el-table-column label="班组名称" align="center" prop="teamName" min-width="120" show-overflow-tooltip />
      <el-table-column label="班组类别" align="center" prop="teamCategory">
        <template #default="scope">
          <dict-tag :options="sch_team_category" :value="scope.row.teamCategory" />
        </template>
      </el-table-column>
      <el-table-column label="所属车间名称" align="center" prop="workshopName" min-width="120" show-overflow-tooltip />
      <el-table-column label="班组长姓名" align="center" prop="leaderName" min-width="120" show-overflow-tooltip />
      <el-table-column label="成员数量" align="center" prop="memberCount" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:team:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:team:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="1000px" append-to-body>
      <el-form ref="teamRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="班组名称" prop="teamName">
              <el-input v-model="form.teamName" placeholder="请输入班组名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="班组类别" prop="teamCategory">
              <el-select v-model="form.teamCategory" placeholder="请选择班组类别" style="width: 100%">
                <el-option v-for="dict in sch_team_category" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所属车间名称" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请输入所属车间名称" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openWorkshopSelect" />
                </template>
              </el-input>
              <el-input v-model="form.workshopId" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="班组长" prop="leaderName">
              <el-input v-model="form.leaderName" placeholder="请输入班组长姓名" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openWorkerSelect" />
                </template>
              </el-input>
              <el-input v-model="form.leaderId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成员数量" prop="memberCount">
              <el-input v-model="form.memberCount" placeholder="请输入成员数量" @input="handleMemberCountInput" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
        </el-form-item>
        <el-divider content-position="center">班组成员信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="openWorkerSelectForTeamMember" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteSchTeamMember" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table
          :data="schTeamMemberList"
          :row-class-name="rowSchTeamMemberIndex"
          @selection-change="handleSchTeamMemberSelectionChange"
          ref="schTeamMember"
        >
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="姓名" prop="workerName" width="100" :disabled="true">
            <template #default="scope">
              <el-input v-model="scope.row.workerName" placeholder="请输入人员姓名" v-if="!isViewMode" :disabled="true" />
              <span v-else>{{ scope.row.workerName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工号" prop="workerCode" width="170">
            <template #default="scope">
              <el-input v-model="scope.row.workerCode" placeholder="请输入工号" v-if="!isViewMode" :disabled="true" />
              <span v-else>{{ scope.row.workerCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" prop="role" width="110">
            <template #default="scope">
              <el-select v-model="scope.row.role" placeholder="请选择角色" v-if="!isViewMode">
                <el-option v-for="dict in sch_worker_role" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
              <dict-tag v-else :options="sch_worker_role" :value="scope.row.role" />
            </template>
          </el-table-column>
          <el-table-column label="生效日期" prop="effectiveDate" width="180">
            <template #default="scope">
              <el-date-picker
                clearable
                v-model="scope.row.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生效日期"
                v-if="!isViewMode"
                style="width: 150px"
              />
              <span v-else>{{ scope.row.effectiveDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="失效日期" prop="expireDate" width="180">
            <template #default="scope">
              <el-date-picker
                clearable
                v-model="scope.row.expireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择失效日期"
                v-if="!isViewMode"
                style="width: 150px"
              />
              <span v-else>{{ scope.row.expireDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="100">
            <template #default="scope">
              <el-select v-model="scope.row.status" placeholder="请选择状态" v-if="!isViewMode" @change="handleStatusChange">
                <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
              <dict-tag v-else :options="sys_core_status" :value="scope.row.status" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择车间" v-model="workshopVisible" width="800px" append-to-body>
      <el-form :model="workshopQueryParams" ref="workshopQueryRef" :inline="true">
        <el-form-item label="车间编码" prop="workshopCode">
          <el-input v-model="workshopQueryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item label="车间名称" prop="workshopName">
          <el-input v-model="workshopQueryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="workshopQueryParams.status" placeholder="请选择状态" clearable>
            <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWorkshopList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWorkshopQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="workshopLoading" :data="workshopList" @row-dblclick="selectWorkshop" show-overflow-tooltip>
        <el-table-column label="车间编码" align="center" prop="workshopCode" />
        <el-table-column label="车间名称" align="center" prop="workshopName" />
        <el-table-column label="车间描述" align="center" prop="workshopDesc" />
        <el-table-column label="车间主任" align="center" prop="managerName" />
        <el-table-column label="产能" align="center" prop="capacity" />
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="sys_core_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkshop(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="workshopTotal > 0"
        :total="workshopTotal"
        v-model:page="workshopQueryParams.pageNum"
        v-model:limit="workshopQueryParams.pageSize"
        @pagination="getWorkshopList"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="workshopVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择班组成员" v-model="workerVisible" width="1100px" append-to-body>
      <el-form :model="workerQueryParams" ref="workerQueryRef" :inline="true">
        <el-form-item label="员工工号" prop="employeeId">
          <el-input v-model="workerQueryParams.employeeId" placeholder="请输入员工工号" clearable @keyup.enter="getWorkerList" />
        </el-form-item>
        <el-form-item label="姓名" prop="userName">
          <el-input v-model="workerQueryParams.userName" placeholder="请输入姓名" clearable @keyup.enter="getWorkerList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWorkerList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWorkerQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table
        v-loading="workerLoading"
        :data="workerList"
        @row-dblclick="selectWorker"
        @selection-change="handleWorkerSelectionChange"
        ref="workerSelectTable"
      >
        <el-table-column type="selection" width="50" align="center" v-if="currentSelectType === 'member'" />
        <el-table-column label="员工工号" align="center" prop="employeeId" />
        <el-table-column label="姓名" align="center" prop="userName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="性别" align="center" prop="sex">
          <template #default="scope">
            <dict-tag :options="sys_user_sex" :value="scope.row.sex" />
          </template>
        </el-table-column>
        <el-table-column label="工种" align="center" prop="workCategory">
          <template #default="scope">
            <dict-tag :options="sch_work_category" :value="scope.row.workCategory" />
          </template>
        </el-table-column>
        <el-table-column label="技能等级" align="center" prop="skillLevel">
          <template #default="scope">
            <dict-tag :options="sch_skill_level" :value="scope.row.skillLevel" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" v-if="currentSelectType === 'leader'">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorker(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="workerTotal > 0"
        :total="workerTotal"
        v-model:page="workerQueryParams.pageNum"
        v-model:limit="workerQueryParams.pageSize"
        @pagination="getWorkerList"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmSelectWorkers">确 定</el-button>
          <el-button @click="workerVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Team">
import { listTeam, getTeam, delTeam, addTeam, updateTeam } from '@/api/erp/sch/team';
import type { TeamVO, TeamForm, TeamQuery, TeamMemberVO } from '@/api/erp/sch/team/types';
import { listWorkshop } from '@/api/erp/bd/workshop';
import type { WorkshopQuery, WorkshopVO } from '@/api/erp/bd/workshop/types';
import { listWorker } from '@/api/erp/sch/worker';
import type { WorkerVO } from '@/api/erp/sch/worker/types';
import type { FormInstance } from 'element-plus';

const { proxy } = getCurrentInstance();
const { sys_core_status, sch_worker_role, sch_team_category, sch_work_status, sch_work_category, sys_user_sex, sch_skill_level } = toRefs(
  proxy.useDict('sys_core_status', 'sch_worker_role', 'sch_team_category', 'sch_work_status', 'sch_work_category', 'sys_user_sex', 'sch_skill_level')
);

const teamRef = ref<FormInstance>();
const teamList = ref<TeamVO[]>([]);
const schTeamMemberList = ref<TeamMemberVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const checkedSchTeamMember = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

const workshopList = ref<WorkshopVO[]>([]);
const workshopLoading = ref(false);
const workshopVisible = ref(false);
const workshopTotal = ref(0);
const workshopQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  workshopCode: undefined,
  workshopName: undefined,
  status: '1' as string | number
});

const workerList = ref<WorkerVO[]>([]);
const workerLoading = ref(false);
const workerVisible = ref(false);
const workerTotal = ref(0);
const workerQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  employeeId: undefined,
  userName: undefined,
  workStatus: 'active'
});

const currentSelectType = ref('member');
const selectedWorkers = ref<TeamMemberVO[]>([]);

const data = reactive<PageData<TeamForm, TeamQuery>>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    teamCode: null,
    teamName: null,
    teamCategory: null,
    workshopId: null,
    workshopName: null,
    memberCount: null,
    status: null
  },
  rules: {
    teamCode: [{ required: true, message: '班组编码不能为空', trigger: 'blur' }],
    teamName: [{ required: true, message: '班组名称不能为空', trigger: 'blur' }],
    memberCount: [{ pattern: /^\d+$/, message: '成员数量必须为正整数', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listTeam(queryParams.value).then((response) => {
    teamList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function handleMemberCountInput(value) {
  const newValue = value.replace(/[^\d]/g, '');
  if (newValue !== value) {
    form.value.memberCount = newValue;
  }
}

function handleStatusChange() {
  updateMemberCount();
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    teamId: null,
    teamCode: null,
    teamName: null,
    teamCategory: null,
    workshopId: null,
    workshopName: null,
    leaderId: null,
    leaderName: null,
    memberCount: null,
    status: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  schTeamMemberList.value = [];
  proxy.resetForm('teamRef');
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.teamId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加班组设置';
}

function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _teamId = row.teamId || ids.value;
  getTeam(_teamId).then((response) => {
    form.value = response.data;
    if (response.data.schTeamMemberList) {
      schTeamMemberList.value = response.data.schTeamMemberList.map((member) => ({
        ...member,
        status: String(member.status)
      }));
    } else {
      schTeamMemberList.value = [];
    }
    open.value = true;
    title.value = '修改班组设置';
  });
}

function handleView(row) {
  reset();
  isViewMode.value = true;
  const _teamId = row.teamId || ids.value;
  getTeam(_teamId).then((response) => {
    form.value = response.data;
    if (response.data.schTeamMemberList) {
      schTeamMemberList.value = response.data.schTeamMemberList.map((member) => ({
        ...member,
        status: String(member.status)
      }));
    } else {
      schTeamMemberList.value = [];
    }
    open.value = true;
    title.value = '查看班组设置';
  });
}

function submitForm() {
  teamRef.value?.validate((valid) => {
    if (valid) {
      form.value.schTeamMemberList = schTeamMemberList.value;
      if (form.value.teamId != null) {
        updateTeam(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addTeam(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row) {
  const _teamIds = row.teamId || ids.value;
  proxy.$modal
    .confirm('是否确认删除班组设置编号为"' + _teamIds + '"的数据项？')
    .then(function () {
      return delTeam(_teamIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function rowSchTeamMemberIndex({ row, rowIndex }: { row: TeamMemberVO; rowIndex: number }): string {
  row.index = rowIndex + 1;
  return '';
}

function handleAddSchTeamMember() {
  const obj: TeamMemberVO = {
    workerId: '',
    workerName: '',
    workerCode: '',
    role: '',
    effectiveDate: '',
    expireDate: '',
    status: '0'
  };
  schTeamMemberList.value.push(obj);
}

function handleDeleteSchTeamMember() {
  if (checkedSchTeamMember.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的班组成员数据');
  } else {
    const schTeamMembers = schTeamMemberList.value;
    const checkedSchTeamMembers = checkedSchTeamMember.value;
    schTeamMemberList.value = schTeamMembers.filter(function (item) {
      return checkedSchTeamMembers.indexOf(item.index) == -1;
    });
  }
}

function handleSchTeamMemberSelectionChange(selection) {
  checkedSchTeamMember.value = selection.map((item) => item.index);
}

function updateMemberCount() {
  const activeMembers = schTeamMemberList.value.filter((member) => member.status === '1' || member.status === 1);
  form.value.memberCount = activeMembers.length;
}

function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find((item) => item.value === value);
  return dict ? dict.label : value;
}

function handleExport() {
  proxy.download(
    'erp/sch/team/export',
    {
      ...queryParams.value
    },
    `team_${new Date().getTime()}.xlsx`
  );
}

function getWorkshopList() {
  workshopLoading.value = true;
  listWorkshop(workshopQueryParams as WorkshopQuery).then((response) => {
    workshopList.value = response.rows;
    workshopTotal.value = response.total;
    workshopLoading.value = false;
  });
}

function openWorkshopSelect() {
  workshopVisible.value = true;
  workshopQueryParams.status = '1';
  getWorkshopList();
}

function selectWorkshop(row) {
  form.value.workshopId = row.workshopId;
  form.value.workshopName = row.workshopName;
  workshopVisible.value = false;
}

function resetWorkshopQuery() {
  workshopQueryParams.workshopCode = undefined;
  workshopQueryParams.workshopName = undefined;
  workshopQueryParams.status = '1';
  getWorkshopList();
}

function getWorkerList() {
  workerLoading.value = true;
  listWorker(workerQueryParams).then((response) => {
    workerList.value = response.rows;
    workerTotal.value = response.total;
    workerLoading.value = false;
  });
}

function openWorkerSelect() {
  currentSelectType.value = 'leader';
  workerVisible.value = true;
  workerQueryParams.workStatus = 'active';
  getWorkerList();
}

function openWorkerSelectForTeamMember() {
  currentSelectType.value = 'member';
  workerVisible.value = true;
  workerQueryParams.workStatus = 'active';
  getWorkerList();
}

function selectWorker(row) {
  if (currentSelectType.value === 'leader') {
    form.value.leaderId = row.workerId;
    form.value.leaderName = row.userName;
    workerVisible.value = false;
    const exists = schTeamMemberList.value.some((member) => member.workerId === row.workerId);
    if (!exists) {
      const leaderMember = {
        workerId: row.workerId,
        workerName: row.userName,
        workerCode: row.employeeId,
        role: 'leader',
        effectiveDate: new Date().toISOString().split('T')[0],
        expireDate: null,
        status: '0'
      };
      schTeamMemberList.value.push(leaderMember);
    }
  } else {
    const exists = selectedWorkers.value.some((worker) => worker.workerId === row.workerId);
    if (!exists) {
      selectedWorkers.value.push({
        workerId: row.workerId,
        workerName: row.userName,
        workerCode: row.employeeId,
        role: 'member',
        effectiveDate: new Date().toISOString().split('T')[0],
        expireDate: null,
        status: '0'
      });
    }
  }
}

function handleWorkerSelectionChange(selection) {
  selectedWorkers.value = [];
  selection.forEach((row) => {
    const exists = selectedWorkers.value.some((worker) => worker.workerId === row.workerId);
    if (!exists) {
      selectedWorkers.value.push({
        workerId: row.workerId,
        workerName: row.userName,
        workerCode: row.employeeId,
        role: 'member',
        effectiveDate: new Date().toISOString().split('T')[0],
        expireDate: null,
        status: '0'
      });
    }
  });
}

function confirmSelectWorkers() {
  if (currentSelectType.value === 'leader' && selectedWorkers.value.length > 0) {
    const leader = selectedWorkers.value[0];
    form.value.leaderId = Number(leader.workerId);
    form.value.leaderName = leader.workerName;
    workerVisible.value = false;
    const exists = schTeamMemberList.value.some((member) => member.workerId === leader.workerId);
    if (!exists) {
      const leaderMember = {
        workerId: leader.workerId,
        workerName: leader.workerName,
        workerCode: leader.workerCode,
        role: 'leader',
        effectiveDate: new Date().toISOString().split('T')[0],
        expireDate: null,
        status: '0'
      };
      schTeamMemberList.value.push(leaderMember);
    }
  } else {
    if (selectedWorkers.value.length === 0) {
      proxy.$modal.msgError('请先选择工人');
      return;
    }
    const newMembers = [];
    selectedWorkers.value.forEach((worker) => {
      const exists = schTeamMemberList.value.some((member) => member.workerId === worker.workerId);
      if (!exists) {
        newMembers.push(worker);
      }
    });
    if (newMembers.length === 0) {
      proxy.$modal.msgWarning('所选工人已在成员列表中');
    } else {
      schTeamMemberList.value.push(...newMembers);
      proxy.$modal.msgSuccess(`成功添加了 ${newMembers.length} 名成员`);
    }
    selectedWorkers.value = [];
    workerVisible.value = false;
  }
}

function resetWorkerQuery() {
  workerQueryParams.employeeId = undefined;
  workerQueryParams.userName = undefined;
  workerQueryParams.workStatus = 'active';
  getWorkerList();
}

getList();
</script>
