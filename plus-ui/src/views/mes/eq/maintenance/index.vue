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
                <el-option label="日保" value="DAILY" />
                <el-option label="周保" value="WEEKLY" />
                <el-option label="月保" value="MONTHLY" />
                <el-option label="季保" value="QUARTERLY" />
                <el-option label="年保" value="YEARLY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
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
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="计划名称" prop="planName" min-width="120" show-overflow-tooltip />
        <!-- 技术债:保养类型暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="保养类型" prop="maintenanceType" align="center">
          <template #default="scope">
            <el-tag :type="typeTagType(scope.row.maintenanceType)">{{ typeLabel(scope.row.maintenanceType) }}</el-tag>
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
        <!-- 技术债:状态暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="保养内容" prop="content" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:eq:maintenance:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:maintenance:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑保养计划对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.planId ? '编辑保养计划' : '新增保养计划'" width="560px" append-to-body>
      <el-form :model="form" label-width="110px">
        <el-form-item label="计划名称" required>
          <el-input v-model="form.planName" placeholder="计划名称" clearable />
        </el-form-item>
        <el-form-item label="设备ID" required>
          <el-input v-model.number="form.equipmentId" placeholder="设备ID" clearable />
        </el-form-item>
        <el-form-item label="保养类型" required>
          <el-select v-model="form.maintenanceType" placeholder="请选择类型" style="width: 100%">
            <el-option label="日保" value="DAILY" />
            <el-option label="周保" value="WEEKLY" />
            <el-option label="月保" value="MONTHLY" />
            <el-option label="季保" value="QUARTERLY" />
            <el-option label="年保" value="YEARLY" />
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
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
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
  </div>
</template>

<script setup lang="ts" name="MesEqMaintenance">
import { listMaintenancePlan, getMaintenancePlan, addMaintenancePlan, updateMaintenancePlan, delMaintenancePlan } from '@/api/mes/eq/maintenance';
import type { EqMaintenancePlanVO, EqMaintenancePlanQuery, EqMaintenancePlanForm } from '@/api/mes/eq/maintenance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<EqMaintenancePlanVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<EqMaintenancePlanQuery>({
  pageNum: 1,
  pageSize: 10,
  planName: undefined,
  maintenanceType: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const form = ref<EqMaintenancePlanForm>({} as EqMaintenancePlanForm);

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
  if (!form.value.planName || !form.value.equipmentId || !form.value.maintenanceType) {
    proxy?.$modal.msgWarning('计划名称/设备ID/保养类型不能为空');
    return;
  }
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

const typeLabel = (t: string) => ({ DAILY: '日保', WEEKLY: '周保', MONTHLY: '月保', QUARTERLY: '季保', YEARLY: '年保' })[t] || t;
const typeTagType = (t: string) => ({ DAILY: 'info', WEEKLY: 'info', MONTHLY: 'primary', QUARTERLY: 'warning', YEARLY: 'success' })[t] || 'info';

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
