<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in tg_usage_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
          ><el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:tg:usage:add']">新增记录</el-button></el-col
        >
        <el-col :span="1.5"
          ><el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="() => handleDelete()" v-hasPermi="['mes:tg:usage:remove']"
            >删除</el-button
          ></el-col
        >
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="工装编码" prop="toolingCode" min-width="140">
          <template #default="scope">{{ scope.row.toolingCode || scope.row.toolingId }}</template>
        </el-table-column>
        <el-table-column label="工单编号" prop="woNo" width="140" />
        <el-table-column label="工位" prop="workstationName" width="120" />
        <el-table-column label="操作员" prop="operatorName" width="100" />
        <el-table-column label="领用时间" prop="checkoutTime" width="160" />
        <el-table-column label="归还时间" prop="returnTime" width="160" />
        <el-table-column label="使用次数" prop="usageCount" align="right" width="90" />
        <el-table-column label="使用时长" prop="usageHours" align="right" width="90" />
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope"><dict-tag :options="tg_usage_status" :value="scope.row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:tg:usage:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:tg:usage:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="usageFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="工装" prop="toolingId">
          <el-input v-model="form.toolingName" placeholder="请选择工装" readonly>
            <template #append><el-button icon="Search" @click="openToolingDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="工单">
          <el-input v-model="form.woNo" placeholder="可选,选择工单" readonly>
            <template #append><el-button icon="Search" @click="openWoDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="工位">
          <el-input v-model="form.workstationName" placeholder="可选,选择工位" readonly>
            <template #append><el-button icon="Search" @click="openWorkstationDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="操作员">
          <el-input v-model="form.operatorName" placeholder="请选择操作员" readonly>
            <template #append><el-button icon="Search" @click="openUserDialog" /></template>
          </el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="使用次数">
              <el-input-number v-model="form.usageCount" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用时长">
              <el-input-number v-model="form.usageHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="dict in tg_usage_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="toolingDialog.visible" title="选择工装" width="780px" append-to-body>
      <el-form :model="toolingDialog.query" :inline="true">
        <el-form-item label="编码"><el-input v-model="toolingDialog.query.toolingCode" clearable @keyup.enter="queryToolings" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="toolingDialog.query.toolingName" clearable @keyup.enter="queryToolings" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryToolings">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="toolingDialog.list" height="380" @row-dblclick="selectTooling">
        <el-table-column label="工装编码" prop="toolingCode" width="150" />
        <el-table-column label="工装名称" prop="toolingName" min-width="140" />
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_status" :value="scope.row.status" /></template>
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

    <el-dialog v-model="userDialog.visible" title="选择操作员" width="700px" append-to-body>
      <el-form :model="userDialog.query" :inline="true">
        <el-form-item label="账号"><el-input v-model="userDialog.query.userName" clearable @keyup.enter="queryUsers" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="userDialog.query.nickName" clearable @keyup.enter="queryUsers" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryUsers">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="userDialog.list" height="380" @row-dblclick="selectUser">
        <el-table-column label="账号" prop="userName" width="140" />
        <el-table-column label="昵称" prop="nickName" min-width="140" />
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

    <el-dialog v-model="woDialog.visible" title="选择工单" width="780px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号"><el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWos" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWos">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="380" @row-dblclick="selectWo">
        <el-table-column label="工单编号" prop="woNo" width="160" />
        <el-table-column label="状态" prop="woStatus" width="110">
          <template #default="scope"><dict-tag :options="prd_wo_status" :value="scope.row.woStatus" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWo(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWos"
      />
    </el-dialog>

    <el-dialog v-model="workstationDialog.visible" title="选择工位" width="780px" append-to-body>
      <el-form :model="workstationDialog.query" :inline="true">
        <el-form-item label="编码"
          ><el-input v-model="workstationDialog.query.workstationCode" clearable @keyup.enter="queryWorkstations"
        /></el-form-item>
        <el-form-item label="名称"
          ><el-input v-model="workstationDialog.query.workstationName" clearable @keyup.enter="queryWorkstations"
        /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWorkstations">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="workstationDialog.list" height="380" @row-dblclick="selectWorkstation">
        <el-table-column label="工位编码" prop="workstationCode" width="160" />
        <el-table-column label="工位名称" prop="workstationName" min-width="180" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWorkstation(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="workstationDialog.total > 0"
        :total="workstationDialog.total"
        v-model:page="workstationDialog.query.pageNum"
        v-model:limit="workstationDialog.query.pageSize"
        @pagination="queryWorkstations"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="TgUsageRecord">
import { listUsageRecord, getUsageRecord, addUsageRecord, updateUsageRecord, delUsageRecord } from '@/api/mes/tg/usage-record';
import type { TgUsageRecordVO, TgUsageRecordForm, TgUsageRecordQuery } from '@/api/mes/tg/usage-record';
import { listTooling } from '@/api/mes/tg/tooling';
import { listUser } from '@/api/system/user';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import { listWorkstation } from '@/api/erp/bd/workstation';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { tg_usage_status, mes_tooling_status, prd_wo_status } = toRefs<any>(proxy?.useDict('tg_usage_status', 'mes_tooling_status', 'prd_wo_status'));
const dataList = ref<TgUsageRecordVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<TgUsageRecordQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const usageFormRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<TgUsageRecordForm & { toolingName?: string }>({ toolingId: 0, status: 'OUT' } as any);
const toolingDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, toolingCode: '', toolingName: '' }
});
const userDialog = reactive({ visible: false, list: [] as any[], total: 0, query: { pageNum: 1, pageSize: 10, userName: '', nickName: '' } });
const woDialog = reactive({ visible: false, list: [] as any[], total: 0, query: { pageNum: 1, pageSize: 10, woNo: '' } });
const workstationDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, workstationCode: '', workstationName: '' }
});
const rules: ElFormRules = {
  toolingId: [{ required: true, message: '工装不能为空', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listUsageRecord(queryParams);
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
const handleSelectionChange = (selection: TgUsageRecordVO[]) => {
  ids.value = selection.map((i) => i.usageId);
};
const resetForm = () => {
  Object.assign(form, {
    usageId: undefined,
    toolingId: 0,
    toolingName: '',
    woId: undefined,
    woNo: '',
    workstationId: undefined,
    workstationName: '',
    operatorId: undefined,
    operatorName: '',
    usageCount: 1,
    usageHours: 0,
    status: 'OUT',
    remark: ''
  });
  usageFormRef.value?.resetFields();
};
const handleAdd = () => {
  resetForm();
  dialog.title = '新增使用记录';
  dialog.visible = true;
};
const handleEdit = async (row: TgUsageRecordVO) => {
  resetForm();
  const res = await getUsageRecord(row.usageId);
  Object.assign(form, res.data);
  dialog.title = '编辑使用记录';
  dialog.visible = true;
};
const submitForm = () => {
  usageFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    if (form.usageId) {
      await updateUsageRecord(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addUsageRecord(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    dialog.visible = false;
    getList();
  });
};
const handleDelete = async (row?: TgUsageRecordVO) => {
  const targetIds = row ? [row.usageId] : ids.value;
  await proxy!.$modal.confirm('确认删除选中使用记录?');
  await delUsageRecord(targetIds);
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
  form.operatorId = row.userId;
  form.operatorName = row.nickName;
  userDialog.visible = false;
};
const openWoDialog = () => {
  woDialog.visible = true;
  queryWos();
};
const queryWos = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const selectWo = (row: any) => {
  form.woId = row.mesWoId;
  form.woNo = row.woNo;
  woDialog.visible = false;
};
const openWorkstationDialog = () => {
  workstationDialog.visible = true;
  queryWorkstations();
};
const queryWorkstations = () => {
  listWorkstation(workstationDialog.query as any).then((res: any) => {
    workstationDialog.list = res.rows || [];
    workstationDialog.total = res.total || 0;
  });
};
const selectWorkstation = (row: any) => {
  form.workstationId = row.workstationId;
  form.workstationName = row.workstationName;
  workstationDialog.visible = false;
};
onMounted(() => getList());
</script>
