<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6"
            ><el-form-item label="状态" prop="status"
              ><el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%"
                ><el-option v-for="d in mes_patrol_status" :key="d.value" :label="d.label" :value="d.value" /></el-select></el-form-item
          ></el-col>
          <el-col :span="6" class="search-actions"
            ><el-form-item
              ><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button
              ><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item
            ></el-col
          >
        </el-row>
      </el-form>
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5"
          ><el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:patrol:task:add']">新增任务</el-button></el-col
        >
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="任务ID" prop="taskId" width="120" />
        <el-table-column label="路线" prop="routeName" min-width="120" />
        <el-table-column label="计划日期" prop="planDate" align="center" width="120" />
        <el-table-column label="指派人" prop="assignedToName" width="100" />
        <el-table-column label="状态" prop="status" align="center" width="100">
          <template #default="s"><dict-tag :options="mes_patrol_status" :value="s.row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" :show-overflow-tooltip="false">
          <template #default="s">
            <el-button
              link
              type="success"
              icon="Check"
              @click="handleExecute(s.row)"
              v-if="s.row.status === 'PENDING' || s.row.status === 'IN_PROGRESS'"
              v-hasPermi="['mes:eq:patrol:task:execute']"
              >执行</el-button
            >
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(s.row)" v-hasPermi="['mes:eq:patrol:task:add']">编辑</el-button>
            <el-button
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(s.row)"
              v-if="s.row.status !== 'COMPLETED'"
              v-hasPermi="['mes:eq:patrol:task:remove']"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
    <!-- 新增/编辑 -->
    <el-dialog v-model="dialog.visible" :title="form.taskId ? '编辑任务' : '新增任务'" width="520px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="路线" prop="routeId"
          ><el-select v-model="form.routeId" filterable placeholder="选择路线" style="width: 100%"
            ><el-option v-for="r in routeOptions" :key="r.routeId" :label="r.routeName" :value="r.routeId" /></el-select
        ></el-form-item>
        <el-form-item label="计划日期" prop="planDate"
          ><el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%"
        /></el-form-item>
        <el-form-item label="指派人"><el-input v-model="form.assignedToName" placeholder="指派人姓名" clearable /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" @click="submitForm">确认</el-button></template
      >
    </el-dialog>
    <!-- 执行巡检 -->
    <el-dialog v-model="execDialog.visible" :title="`执行巡检 - ${execDialog.routeName}`" width="700px" append-to-body>
      <el-form label-width="90px">
        <el-table :data="execRecords" border size="small">
          <el-table-column label="设备" min-width="180"
            ><template #default="s"><equipment-select v-model="s.row.equipmentId" /></template
          ></el-table-column>
          <el-table-column label="检查项" width="150"
            ><template #default="s"><el-input v-model="s.row.checkItem" size="small" /></template
          ></el-table-column>
          <el-table-column label="实际值" width="120"
            ><template #default="s"><el-input v-model="s.row.actualValue" size="small" /></template
          ></el-table-column>
          <el-table-column label="结果" width="100"
            ><template #default="s"
              ><el-select v-model="s.row.result" size="small"
                ><el-option label="正常" value="NORMAL" /><el-option label="异常" value="ABNORMAL" /></el-select></template
          ></el-table-column>
          <el-table-column label="备注" width="150"
            ><template #default="s"><el-input v-model="s.row.remark" size="small" /></template
          ></el-table-column>
          <el-table-column label="" width="60"
            ><template #default="s"><el-button link type="danger" icon="Delete" size="small" @click="execRecords.splice(s.$index, 1)" /></template
          ></el-table-column>
        </el-table>
        <el-button
          size="small"
          icon="Plus"
          style="margin-top: 8px"
          @click="execRecords.push({ equipmentId: undefined, checkItem: '', actualValue: '', result: 'NORMAL', remark: '' })"
          >添加记录</el-button
        >
      </el-form>
      <template #footer>
        <el-button @click="execDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="execSubmitting" :disabled="execSubmitting" @click="submitExecute(false)">保存记录</el-button>
        <el-button type="success" :loading="execSubmitting" :disabled="execSubmitting" @click="submitExecute(true)">保存并完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolTask">
import {
  listPatrolTask,
  getPatrolTask,
  addPatrolTask,
  updatePatrolTask,
  delPatrolTask,
  completePatrolTask,
  batchSubmitPatrolRecord,
  listPatrolRoute
} from '@/api/mes/eq/patrol';
import type { EqPatrolTaskVO, EqPatrolTaskQuery, EqPatrolTaskForm, EqPatrolRouteVO, EqPatrolRecordForm } from '@/api/mes/eq/patrol';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_patrol_status } = toRefs<any>(proxy?.useDict('mes_patrol_status'));
const dataList = ref<EqPatrolTaskVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const queryParams = ref<EqPatrolTaskQuery>({ pageNum: 1, pageSize: 10, status: undefined });
const routeOptions = ref<EqPatrolRouteVO[]>([]);
const dialog = reactive({ visible: false });
const formRef = ref();
const formRules = {
  routeId: [{ required: true, message: '请选择路线', trigger: 'change' }],
  planDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
};
const form = ref<EqPatrolTaskForm>({} as EqPatrolTaskForm);
const execDialog = reactive({ visible: false, taskId: 0, routeId: 0, routeName: '' });
const execRecords = ref<Array<{ equipmentId?: number; checkItem: string; actualValue: string; result: string; remark: string }>>([]);
const execSubmitting = ref(false);
const getList = async () => {
  loading.value = true;
  try {
    const r = await listPatrolTask(queryParams.value);
    dataList.value = r.rows;
    total.value = r.total;
  } finally {
    loading.value = false;
  }
};
const loadRoutes = async () => {
  const r = await listPatrolRoute({ status: 'ACTIVE' } as any);
  routeOptions.value = r.rows || [];
};
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};
const resetForm = () => {
  form.value = { routeId: undefined as unknown as number, planDate: '', assignedToName: '', remark: '' };
};
const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};
const handleOpenEdit = async (row: EqPatrolTaskVO) => {
  const r = await getPatrolTask(row.taskId);
  form.value = { ...r.data };
  dialog.visible = true;
};
const submitForm = async () => {
  await formRef.value?.validate();
  form.value.taskId ? await updatePatrolTask(form.value) : await addPatrolTask(form.value);
  proxy?.$modal.msgSuccess(form.value.taskId ? '编辑成功' : '新增成功');
  dialog.visible = false;
  getList();
};
const handleDelete = async (row: EqPatrolTaskVO) => {
  await proxy?.$modal.confirm('确认删除任务？');
  await delPatrolTask([row.taskId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};
const handleExecute = (row: EqPatrolTaskVO) => {
  execDialog.taskId = row.taskId;
  execDialog.routeId = row.routeId;
  execDialog.routeName = row.routeName || '';
  execRecords.value = [{ equipmentId: undefined as unknown as number, checkItem: '', actualValue: '', result: 'NORMAL', remark: '' }];
  execDialog.visible = true;
};
const submitExecute = async (complete: boolean) => {
  const valid = execRecords.value.filter((r) => r.equipmentId);
  if (valid.length === 0) {
    proxy?.$modal.msgWarning('请至少填写一条带设备的记录');
    return;
  }
  // 部分行填了设备但未填巡检项: 提示但不阻断(允许只记录设备到访)
  const noCheckItem = valid.filter((r) => !r.checkItem || !r.checkItem.trim());
  if (noCheckItem.length > 0) {
    proxy?.$modal.msgWarning('存在未填写巡检项的记录, 请确认');
  }
  const bos: EqPatrolRecordForm[] = valid.map((r) => ({ ...r, taskId: execDialog.taskId, routeId: execDialog.routeId }) as any);
  execSubmitting.value = true;
  try {
    await batchSubmitPatrolRecord(bos);
    if (complete) {
      await completePatrolTask(execDialog.taskId);
    }
    proxy?.$modal.msgSuccess(complete ? '巡检完成' : '记录已保存');
    execDialog.visible = false;
    getList();
  } catch {
    // 记录已保存但完成失败时, 保留弹窗让用户重试完成(避免重复提交导致重复记录)
    proxy?.$modal.msgError(complete ? '记录已保存, 但完成任务失败, 请重试完成或检查任务状态' : '保存记录失败');
  } finally {
    execSubmitting.value = false;
  }
};
onMounted(() => {
  getList();
  loadRoutes();
});
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
