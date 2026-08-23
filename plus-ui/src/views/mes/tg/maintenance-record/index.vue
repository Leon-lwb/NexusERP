<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="保养类型" prop="maintainType">
              <el-select v-model="queryParams.maintainType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in tg_maintenance_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5"
          ><el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:tg:record:add']">新增记录</el-button></el-col
        >
        <el-col :span="1.5"
          ><el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="() => handleDelete()" v-hasPermi="['mes:tg:record:remove']"
            >删除</el-button
          ></el-col
        >
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="保养时间" prop="maintainTime" width="160" />
        <el-table-column label="工装ID" prop="toolingId" width="100" />
        <el-table-column label="类型" prop="maintainType" align="center" width="90">
          <template #default="scope"><dict-tag :options="tg_maintenance_type" :value="scope.row.maintainType" /></template>
        </el-table-column>
        <el-table-column label="保养人" prop="maintainerName" width="100" />
        <el-table-column label="保养前(次)" prop="usedCountBefore" align="right" width="100" />
        <el-table-column label="保养后(次)" prop="usedCountAfter" align="right" width="100" />
        <el-table-column label="保养内容" prop="description" min-width="160" show-overflow-tooltip />
        <el-table-column label="成本" prop="cost" align="right" width="90" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:tg:record:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:tg:record:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="500px" append-to-body>
      <el-form ref="recordFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工装" prop="toolingId">
          <el-input v-model="form.toolingName" placeholder="请选择工装" readonly>
            <template #append><el-button icon="Search" @click="openToolingDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="保养类型">
          <el-select v-model="form.maintainType" placeholder="请选择" style="width: 100%">
            <el-option v-for="dict in tg_maintenance_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养时间">
          <el-date-picker v-model="form.maintainTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="保养人">
          <el-input v-model="form.maintainerName" placeholder="请选择保养人" readonly>
            <template #append><el-button icon="Search" @click="openUserDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="保养内容"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="保养成本">
          <el-input-number v-model="form.cost" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="toolingDialog.visible" title="选择工装" width="800px" append-to-body>
      <el-form :model="toolingDialog.query" :inline="true">
        <el-form-item label="编码"><el-input v-model="toolingDialog.query.toolingCode" clearable @keyup.enter="queryToolings" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="toolingDialog.query.toolingName" clearable @keyup.enter="queryToolings" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryToolings">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="toolingDialog.list" height="380" @row-dblclick="selectTooling">
        <el-table-column label="工装编码" prop="toolingCode" width="150" />
        <el-table-column label="工装名称" prop="toolingName" min-width="140" />
        <el-table-column label="类型" prop="toolingType" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_type" :value="scope.row.toolingType" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectTooling(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="toolingDialog.total > 0"
        :total="toolingDialog.total"
        v-model:page="toolingDialog.query.pageNum"
        v-model:limit="toolingDialog.query.pageSize"
        @pagination="queryToolings"
      />
    </el-dialog>

    <el-dialog v-model="userDialog.visible" title="选择保养人" width="640px" append-to-body>
      <el-form :model="userDialog.query" :inline="true">
        <el-form-item label="账号"><el-input v-model="userDialog.query.userName" clearable @keyup.enter="queryUsers" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="userDialog.query.nickName" clearable @keyup.enter="queryUsers" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryUsers">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="userDialog.list" height="380" @row-dblclick="selectUser">
        <el-table-column label="账号" prop="userName" width="140" />
        <el-table-column label="昵称" prop="nickName" min-width="140" />
        <el-table-column label="部门" prop="deptName" min-width="140" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectUser(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.query.pageNum"
        v-model:limit="userDialog.query.pageSize"
        @pagination="queryUsers"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="TgMaintenanceRecord">
import {
  listMaintenanceRecord,
  getMaintenanceRecord,
  addMaintenanceRecord,
  updateMaintenanceRecord,
  delMaintenanceRecord
} from '@/api/mes/tg/maintenance-record';
import type { TgMaintenanceRecordVO, TgMaintenanceRecordForm, TgMaintenanceRecordQuery } from '@/api/mes/tg/maintenance-record';
import { listTooling } from '@/api/mes/tg/tooling';
import { listUser } from '@/api/system/user';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { tg_maintenance_type, mes_tooling_type } = toRefs<any>(proxy?.useDict('tg_maintenance_type', 'mes_tooling_type'));
const dataList = ref<TgMaintenanceRecordVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<TgMaintenanceRecordQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const recordFormRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<TgMaintenanceRecordForm & { toolingName?: string }>({ toolingId: 0, maintainType: 'PLANNED' } as any);
const toolingDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, toolingCode: '', toolingName: '' }
});
const userDialog = reactive({ visible: false, list: [] as any[], total: 0, query: { pageNum: 1, pageSize: 10, userName: '', nickName: '' } });
const rules: ElFormRules = {
  toolingId: [{ required: true, message: '工装不能为空', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMaintenanceRecord(queryParams);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};
const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy!.resetForm('queryFormRef');
  handleQuery();
};
const handleSelectionChange = (selection: TgMaintenanceRecordVO[]) => {
  ids.value = selection.map((i) => i.recordId);
};
const resetForm = () => {
  Object.assign(form, {
    recordId: undefined,
    toolingId: 0,
    toolingName: '',
    planId: undefined,
    maintainType: 'PLANNED',
    maintainTime: '',
    maintainerId: undefined,
    maintainerName: '',
    description: '',
    cost: undefined,
    remark: ''
  });
  recordFormRef.value?.resetFields();
};
const handleAdd = () => {
  resetForm();
  dialog.title = '新增保养记录';
  dialog.visible = true;
};
const handleEdit = async (row: TgMaintenanceRecordVO) => {
  resetForm();
  const res = await getMaintenanceRecord(row.recordId);
  Object.assign(form, res.data);
  dialog.title = '编辑保养记录';
  dialog.visible = true;
};
const submitForm = () => {
  recordFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    if (form.recordId) {
      await updateMaintenanceRecord(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addMaintenanceRecord(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    dialog.visible = false;
    getList();
  });
};
const handleDelete = async (row?: TgMaintenanceRecordVO) => {
  const targetIds = row ? [row.recordId] : ids.value;
  await proxy!.$modal.confirm('确认删除选中保养记录?');
  await delMaintenanceRecord(targetIds);
  proxy!.$modal.msgSuccess('删除成功');
  getList();
};
const openToolingDialog = () => {
  toolingDialog.visible = true;
  queryToolings();
};
const queryToolings = () => {
  listTooling(toolingDialog.query as any).then((res: any) => {
    toolingDialog.list = res.rows || [];
    toolingDialog.total = res.total || 0;
  });
};
const selectTooling = (row: any) => {
  form.toolingId = row.toolingId;
  form.toolingName = row.toolingName;
  toolingDialog.visible = false;
};
const openUserDialog = () => {
  userDialog.visible = true;
  queryUsers();
};
const queryUsers = () => {
  listUser(userDialog.query as any).then((res: any) => {
    userDialog.list = res.rows || [];
    userDialog.total = res.total || 0;
  });
};
const selectUser = (row: any) => {
  form.maintainerId = row.userId;
  form.maintainerName = row.nickName;
  userDialog.visible = false;
};
onMounted(() => getList());
</script>
