<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="员工工号" prop="employeeId">
            <el-input v-model="queryParams.employeeId" placeholder="请输入员工工号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="姓名" prop="userName">
            <el-input v-model="queryParams.userName" placeholder="请输入姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="手机号码" prop="phonenumber">
            <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工种" prop="workCategory">
            <el-select v-model="queryParams.workCategory" placeholder="请选择工种" clearable style="width: 100%">
              <el-option v-for="dict in sch_work_category" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="在岗状态" prop="workStatus">
            <el-select v-model="queryParams.workStatus" placeholder="请选择在岗状态" clearable style="width: 100%">
              <el-option v-for="dict in sch_work_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入职日期" prop="entryDate">
            <el-date-picker
              clearable
              v-model="queryParams.entryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择入职日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:worker:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:worker:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:worker:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:worker:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:worker:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workerList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="员工工号" align="center" prop="employeeId" />
      <el-table-column label="姓名" align="center" prop="userName" min-width="120" show-overflow-tooltip />
      <el-table-column label="所属车间" align="center" prop="workshopName" min-width="120" show-overflow-tooltip />
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
      <el-table-column label="在岗状态" align="center" prop="workStatus">
        <template #default="scope">
          <dict-tag :options="sch_work_status" :value="scope.row.workStatus" />
        </template>
      </el-table-column>
      <el-table-column label="入职日期" align="center" prop="entryDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.entryDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:worker:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:worker:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="workerRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="关联用户" prop="userId">
              <el-input v-model="form.userName" placeholder="选择系统用户建档" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openUserSelect" />
                  <el-button v-if="!isViewMode && form.userId" icon="RefreshLeft" @click="clearUserSelect" />
                </template>
              </el-input>
              <el-input v-model="form.userId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所属车间" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请选择所属车间" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openWorkshopSelect" />
                </template>
              </el-input>
              <el-input v-model="form.workshopId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="员工工号" prop="employeeId">
              <el-input v-model="form.employeeId" placeholder="请输入员工工号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="员工姓名" prop="userName">
              <el-input v-model="form.userName" placeholder="请输入员工姓名" :disabled="hasUser" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="手机号码" prop="phonenumber">
              <el-input v-model="form.phonenumber" placeholder="请输入手机号码" :disabled="hasUser" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="性别" prop="sex">
              <el-select v-model="form.sex" placeholder="请选择性别" style="width: 100%" :disabled="hasUser">
                <el-option v-for="dict in sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="工种" prop="workCategory">
              <el-select v-model="form.workCategory" placeholder="请选择工种" style="width: 100%">
                <el-option v-for="dict in sch_work_category" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="技能等级" prop="skillLevel">
              <el-select v-model="form.skillLevel" placeholder="请选择技能等级" style="width: 100%">
                <el-option v-for="dict in sch_skill_level" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="在岗状态" prop="workStatus">
              <el-select v-model="form.workStatus" placeholder="请选择在岗状态" style="width: 100%">
                <el-option v-for="dict in sch_work_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="入职日期" prop="entryDate">
              <el-date-picker
                clearable
                v-model="form.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择入职日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="转正日期" prop="regularizationDate">
              <el-date-picker
                clearable
                v-model="form.regularizationDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择转正日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
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

    <el-dialog title="选择系统用户" v-model="userVisible" width="800px" append-to-body>
      <el-form :model="userQueryParams" ref="userQueryRef" :inline="true">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userQueryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getUserList">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="userLoading" :data="userList" @row-dblclick="selectUser" show-overflow-tooltip>
        <el-table-column label="用户账号" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="性别" align="center" prop="sex">
          <template #default="scope">
            <dict-tag :options="sys_user_sex" :value="scope.row.sex" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userTotal > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userVisible = false">取 消</el-button>
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
  </div>
</template>

<script setup lang="ts" name="Worker">
import { listWorker, getWorker, delWorker, addWorker, updateWorker } from '@/api/erp/sch/worker';
import { listUser } from '@/api/system/user';
import { listWorkshop } from '@/api/erp/bd/workshop';

const { proxy } = getCurrentInstance();
const { sch_skill_level, sch_work_category, sch_work_status, sys_user_sex, sys_core_status } = toRefs(
  proxy.useDict('sch_skill_level', 'sch_work_category', 'sch_work_status', 'sys_user_sex', 'sys_core_status')
);

const workerList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

// 系统用户选择
const userList = ref([]);
const userLoading = ref(false);
const userVisible = ref(false);
const userTotal = ref(0);
const userQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  phonenumber: undefined
});

// 车间选择
const workshopList = ref([]);
const workshopLoading = ref(false);
const workshopVisible = ref(false);
const workshopTotal = ref(0);
const workshopQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  workshopCode: undefined,
  workshopName: undefined,
  status: '0'
});

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userId: null,
    userName: null,
    phonenumber: null,
    employeeId: null,
    workCategory: null,
    workStatus: null,
    workshopId: null,
    entryDate: null,
    regularizationDate: null
  },
  rules: {
    employeeId: [{ required: true, message: '员工工号不能为空', trigger: 'blur' }],
    userName: [{ required: true, message: '员工姓名不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

// 是否已关联系统用户：关联后身份字段只读带出，未关联（无账号工人）可手填
const hasUser = computed(() => !!form.value.userId);

function getList() {
  loading.value = true;
  listWorker(queryParams.value).then((response) => {
    workerList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    workerId: null,
    userId: null,
    workshopId: null,
    workshopName: null,
    employeeId: null,
    workCategory: null,
    skillLevel: null,
    workStatus: null,
    entryDate: null,
    regularizationDate: null,
    userName: null,
    phonenumber: null,
    sex: null,
    createBy: null,
    updateBy: null,
    remark: null,
    createTime: null,
    updateTime: null
  };
  proxy.resetForm('workerRef');
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
  ids.value = selection.map((item) => item.workerId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加生产工人档案';
}

function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _workerId = row.workerId || ids.value;
  getWorker(_workerId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改生产工人档案';
  });
}

function handleView(row) {
  reset();
  isViewMode.value = true;
  const _workerId = row.workerId || ids.value;
  getWorker(_workerId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看生产工人档案';
  });
}

function submitForm() {
  proxy.$refs['workerRef'].validate((valid) => {
    if (valid) {
      if (form.value.workerId != null) {
        updateWorker(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addWorker(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row) {
  const _workerIds = row.workerId || ids.value;
  proxy.$modal
    .confirm('是否确认删除生产工人档案编号为"' + _workerIds + '"的数据项？')
    .then(function () {
      return delWorker(_workerIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download(
    'sch/worker/export',
    {
      ...queryParams.value
    },
    `worker_${new Date().getTime()}.xlsx`
  );
}

// ============ 系统用户选择 ============
function getUserList() {
  userLoading.value = true;
  listUser(userQueryParams).then((response) => {
    userList.value = response.rows;
    userTotal.value = response.total;
    userLoading.value = false;
  });
}

function openUserSelect() {
  userVisible.value = true;
  getUserList();
}

function resetUserQuery() {
  userQueryParams.userName = undefined;
  userQueryParams.phonenumber = undefined;
  getUserList();
}

function selectUser(row) {
  form.value.userId = row.userId;
  // 优先用昵称（显示名）作为员工姓名，回退到账号
  form.value.userName = row.nickName || row.userName;
  form.value.phonenumber = row.phonenumber;
  form.value.sex = row.sex;
  userVisible.value = false;
}

function clearUserSelect() {
  form.value.userId = null;
}

// ============ 车间选择 ============
function getWorkshopList() {
  workshopLoading.value = true;
  listWorkshop(workshopQueryParams).then((response) => {
    workshopList.value = response.rows;
    workshopTotal.value = response.total;
    workshopLoading.value = false;
  });
}

function openWorkshopSelect() {
  workshopVisible.value = true;
  workshopQueryParams.status = '0';
  getWorkshopList();
}

function resetWorkshopQuery() {
  workshopQueryParams.workshopCode = undefined;
  workshopQueryParams.workshopName = undefined;
  workshopQueryParams.status = '0';
  getWorkshopList();
}

function selectWorkshop(row) {
  form.value.workshopId = row.workshopId;
  form.value.workshopName = row.workshopName;
  workshopVisible.value = false;
}

getList();
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
