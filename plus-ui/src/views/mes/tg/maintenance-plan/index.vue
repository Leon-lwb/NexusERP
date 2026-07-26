<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="触发类型" prop="triggerType">
              <el-select v-model="queryParams.triggerType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in tg_maintenance_trigger_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用" prop="enabled">
              <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 100%">
                <el-option label="启用" value="1" />
                <el-option label="停用" value="0" />
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
          ><el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:tg:plan:add']">新增计划</el-button></el-col
        >
        <el-col :span="1.5"
          ><el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="() => handleDelete()" v-hasPermi="['mes:tg:plan:remove']"
            >删除</el-button
          ></el-col
        >
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="计划名称" prop="planName" min-width="150" />
        <el-table-column label="工装ID" prop="toolingId" width="100" />
        <el-table-column label="触发类型" prop="triggerType" align="center" width="100">
          <template #default="scope"><dict-tag :options="tg_maintenance_trigger_type" :value="scope.row.triggerType" /></template>
        </el-table-column>
        <el-table-column label="阈值" prop="triggerValue" align="right" width="80" />
        <el-table-column label="下次到期" min-width="150">
          <template #default="scope">
            <span v-if="scope.row.triggerType === 'BY_COUNT'">次数: {{ scope.row.nextMaintainCount }}</span>
            <span v-else-if="scope.row.triggerType === 'BY_HOURS'">时长: {{ scope.row.nextMaintainHours }}</span>
            <span v-else>{{ scope.row.nextMaintainTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上次保养" prop="lastMaintainTime" width="160" />
        <el-table-column label="启用" prop="enabled" align="center" width="80">
          <template #default="scope"
            ><el-tag :type="scope.row.enabled === '1' ? 'success' : 'info'" size="small">{{
              scope.row.enabled === '1' ? '启用' : '停用'
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:tg:plan:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:tg:plan:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="planFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="计划名称" prop="planName"><el-input v-model="form.planName" /></el-form-item>
        <el-form-item label="工装" prop="toolingId">
          <el-input v-model="form.toolingName" placeholder="请选择工装" readonly>
            <template #append><el-button icon="Search" @click="openToolingDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="触发类型" prop="triggerType">
          <el-select v-model="form.triggerType" placeholder="请选择" style="width: 100%">
            <el-option v-for="dict in tg_maintenance_trigger_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发阈值" prop="triggerValue">
          <el-input-number v-model="form.triggerValue" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-value="1" inactive-value="0" />
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
        <el-table-column label="类型" prop="toolingType" align="center" width="90">
          <template #default="scope"><dict-tag :options="mes_tooling_type" :value="scope.row.toolingType" /></template>
        </el-table-column>
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
  </div>
</template>

<script setup lang="ts" name="TgMaintenancePlan">
import {
  listMaintenancePlan,
  getMaintenancePlan,
  addMaintenancePlan,
  updateMaintenancePlan,
  delMaintenancePlan
} from '@/api/mes/tg/maintenance-plan';
import type { TgMaintenancePlanVO, TgMaintenancePlanForm, TgMaintenancePlanQuery } from '@/api/mes/tg/maintenance-plan';
import { listTooling } from '@/api/mes/tg/tooling';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { tg_maintenance_trigger_type, mes_tooling_type, mes_tooling_status } = toRefs<any>(
  proxy?.useDict('tg_maintenance_trigger_type', 'mes_tooling_type', 'mes_tooling_status')
);
const dataList = ref<TgMaintenancePlanVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<TgMaintenancePlanQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const planFormRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<TgMaintenancePlanForm & { toolingName?: string }>({
  planName: '',
  toolingId: 0,
  triggerType: '',
  triggerValue: 1,
  enabled: '1'
} as any);
const toolingDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, toolingCode: '', toolingName: '' }
});
const rules: ElFormRules = {
  planName: [{ required: true, message: '计划名称不能为空', trigger: 'blur' }],
  toolingId: [{ required: true, message: '工装不能为空', trigger: 'change' }],
  triggerType: [{ required: true, message: '触发类型不能为空', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMaintenancePlan(queryParams);
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
const handleSelectionChange = (selection: TgMaintenancePlanVO[]) => {
  ids.value = selection.map((i) => i.planId);
};
const resetForm = () => {
  Object.assign(form, { planId: undefined, planName: '', toolingId: 0, toolingName: '', triggerType: '', triggerValue: 1, enabled: '1', remark: '' });
  planFormRef.value?.resetFields();
};
const handleAdd = () => {
  resetForm();
  dialog.title = '新增保养计划';
  dialog.visible = true;
};
const handleEdit = async (row: TgMaintenancePlanVO) => {
  resetForm();
  const res = await getMaintenancePlan(row.planId);
  Object.assign(form, res.data);
  dialog.title = '编辑保养计划';
  dialog.visible = true;
};
const submitForm = () => {
  planFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    if (form.planId) {
      await updateMaintenancePlan(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addMaintenancePlan(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    dialog.visible = false;
    getList();
  });
};
const handleDelete = async (row?: TgMaintenancePlanVO) => {
  const targetIds = row ? [row.planId] : ids.value;
  await proxy!.$modal.confirm('确认删除选中保养计划?');
  await delMaintenancePlan(targetIds);
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
onMounted(() => getList());
</script>
