<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="保养类型" prop="maintenanceType">
              <el-select v-model="queryParams.maintenanceType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="d in mes_maintenance_status" :key="d.value" :label="d.label" :value="d.value" />
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
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:maintenance:add']">新增计划</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['mes:eq:maintenance:remove']"
            >批量删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['mes:eq:maintenance:export']">导出</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="计划名称" prop="planName" min-width="120" show-overflow-tooltip />
        <el-table-column label="保养类型" prop="maintenanceType" align="center">
          <template #default="scope">
            <dict-tag :options="mes_maintenance_type" :value="scope.row.maintenanceType" />
          </template>
        </el-table-column>
        <el-table-column label="周期(天)" prop="cycleDays" align="right" />
        <el-table-column label="下次保养日期" prop="nextDate" align="center">
          <template #default="scope">
            <el-tag v-if="isOverdue(scope.row.nextDate)" type="danger">{{ scope.row.nextDate || '-' }}</el-tag>
            <span v-else>{{ scope.row.nextDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" prop="responsibleName">
          <template #default="scope">{{ scope.row.responsibleName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <dict-tag :options="mes_maintenance_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:eq:maintenance:edit']">编辑</el-button>
            <el-button
              link
              type="success"
              icon="Check"
              @click="handleOpenComplete(scope.row)"
              v-if="scope.row.status === 'ACTIVE'"
              v-hasPermi="['mes:eq:maintenance:complete']"
              >执行保养</el-button
            >
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:maintenance:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑保养计划对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.planId ? '编辑保养计划' : '新增保养计划'" width="560px" append-to-body>
      <el-form :model="form" label-width="110px" ref="formRef" :rules="formRules">
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="form.planName" placeholder="计划名称" clearable />
        </el-form-item>
        <el-form-item label="设备" prop="equipmentId">
          <equipment-select v-model="form.equipmentId" />
        </el-form-item>
        <el-form-item label="保养类型" prop="maintenanceType">
          <el-select v-model="form.maintenanceType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期(天)">
          <el-input-number
            v-model="form.cycleDays"
            :min="1"
            :precision="0"
            controls-position="right"
            style="width: 100%"
            placeholder="留空按类型默认"
          />
        </el-form-item>
        <el-form-item label="下次保养日期">
          <el-date-picker v-model="form.nextDate" type="date" value-format="YYYY-MM-DD" placeholder="留空自动计算(today+周期)" style="width: 100%" />
        </el-form-item>
        <el-form-item label="负责人姓名">
          <el-input v-model="form.responsibleName" placeholder="负责人姓名" clearable />
        </el-form-item>
        <el-form-item label="保养内容">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="保养内容" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="状态" style="width: 100%">
            <el-option v-for="d in mes_maintenance_status" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>

    <!-- 执行保养对话框(P0-4 保养工单闭环) -->
    <el-dialog v-model="completeDialog.visible" :title="`执行保养 - ${completeForm.planName}`" width="520px" append-to-body>
      <el-form :model="completeForm" label-width="100px">
        <el-form-item label="保养内容">
          <el-input v-model="completeForm.content" type="textarea" :rows="4" placeholder="本次保养执行情况" />
        </el-form-item>
        <el-form-item label="保养结果">
          <el-select v-model="completeForm.result" style="width: 100%">
            <el-option v-for="d in mes_maintenance_result" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="completeForm.remark" type="textarea" :rows="2" placeholder="备注(异常时说明)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqMaintenance">
import {
  listMaintenancePlan,
  getMaintenancePlan,
  addMaintenancePlan,
  updateMaintenancePlan,
  delMaintenancePlan,
  completeMaintenancePlan,
  exportMaintenancePlan
} from '@/api/mes/eq/maintenance';
import type { EqMaintenancePlanVO, EqMaintenancePlanQuery, EqMaintenancePlanForm } from '@/api/mes/eq/maintenance';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_maintenance_type, mes_maintenance_result, mes_maintenance_status } = toRefs<any>(
  proxy?.useDict('mes_maintenance_type', 'mes_maintenance_result', 'mes_maintenance_status')
);

const dataList = ref<EqMaintenancePlanVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const selectedIds = ref<(string | number)[]>([]);

const queryParams = ref<EqMaintenancePlanQuery>({
  pageNum: 1,
  pageSize: 10,
  planName: undefined,
  maintenanceType: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const formRef = ref();
const formRules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择保养类型', trigger: 'change' }]
};
const form = ref<EqMaintenancePlanForm>({} as EqMaintenancePlanForm);

const completeDialog = reactive({ visible: false });
const completeForm = reactive<{ planId: number; planName: string; content: string; result: string; remark: string }>({
  planId: 0,
  planName: '',
  content: '',
  result: 'NORMAL',
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMaintenancePlan(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const handleExport = () => {
  exportMaintenancePlan(queryParams.value);
};

const resetForm = () => {
  form.value = {
    planId: undefined,
    planName: '',
    equipmentId: undefined as unknown as number,
    maintenanceType: 'MONTHLY',
    cycleDays: undefined,
    nextDate: undefined,
    content: '',
    responsibleId: undefined,
    responsibleName: '',
    status: 'ACTIVE',
    remark: ''
  };
};

const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};

const handleOpenEdit = async (row: EqMaintenancePlanVO) => {
  const res = await getMaintenancePlan(row.planId);
  form.value = { ...res.data };
  dialog.visible = true;
};

const submitForm = async () => {
  await formRef.value?.validate();
  if (form.value.planId) {
    await updateMaintenancePlan(form.value);
    proxy?.$modal.msgSuccess('编辑成功');
  } else {
    await addMaintenancePlan(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  getList();
};

const handleDelete = async (row: EqMaintenancePlanVO) => {
  await proxy?.$modal.confirm('确认删除保养计划"' + row.planName + '"？');
  await delMaintenancePlan([row.planId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleSelectionChange = (selection: EqMaintenancePlanVO[]) => {
  selectedIds.value = selection.map((item) => item.planId);
};

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return;
  await proxy?.$modal.confirm('确认删除选中的 ' + selectedIds.value.length + ' 条保养计划？');
  await delMaintenancePlan(selectedIds.value);
  proxy?.$modal.msgSuccess('删除成功');
  selectedIds.value = [];
  getList();
};

const handleOpenComplete = (row: EqMaintenancePlanVO) => {
  completeForm.planId = row.planId;
  completeForm.planName = row.planName;
  completeForm.content = row.content || '';
  completeForm.result = 'NORMAL';
  completeForm.remark = '';
  completeDialog.visible = true;
};

const submitComplete = async () => {
  await completeMaintenancePlan(completeForm.planId, {
    content: completeForm.content,
    result: completeForm.result,
    remark: completeForm.remark
  });
  proxy?.$modal.msgSuccess('保养完成, 已推进下次保养日期');
  completeDialog.visible = false;
  getList();
};

const isOverdue = (nextDate?: string) => {
  if (!nextDate) return false;
  return new Date(nextDate) < new Date(new Date().toDateString());
};

onMounted(() => {
  getList();
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
