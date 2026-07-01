<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="维修单号" prop="repairNo">
              <el-input v-model="queryParams.repairNo" placeholder="请输入维修单号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="已报修" value="REPORTED" />
                <el-option label="维修中" value="REPAIRING" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
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
          <el-button type="warning" plain icon="WarnTriangleFilled" @click="handleOpenAdd" v-hasPermi="['mes:eq:repair:add']">故障报修</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="维修单号" prop="repairNo" />
        <el-table-column label="故障类型" prop="faultType">
          <template #default="scope">{{ scope.row.faultType || '-' }}</template>
        </el-table-column>
        <el-table-column label="故障描述" prop="faultDesc" min-width="120" show-overflow-tooltip />
        <!-- 技术债:状态暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报修人" prop="reporterName">
          <template #default="scope">{{ scope.row.reporterName || '-' }}</template>
        </el-table-column>
        <el-table-column label="维修人" prop="repairerName">
          <template #default="scope">{{ scope.row.repairerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="停机时长" prop="downtimeHours" align="right">
          <template #default="scope">{{ scope.row.downtimeHours ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="维修成本" prop="repairCost" align="right">
          <template #default="scope">{{ scope.row.repairCost ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              link
              type="warning"
              icon="Tools"
              @click="handleStart(scope.row)"
              v-if="scope.row.status === 'REPORTED'"
              v-hasPermi="['mes:eq:repair:start']"
              >开工</el-button
            >
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleComplete(scope.row)"
              v-if="scope.row.status === 'REPAIRING'"
              v-hasPermi="['mes:eq:repair:complete']"
              >完成</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 故障报修对话框 -->
    <el-dialog v-model="addDialog.visible" title="故障报修" width="520px" append-to-body>
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="设备ID" required>
          <el-input v-model.number="addForm.equipmentId" placeholder="设备ID" clearable />
        </el-form-item>
        <el-form-item label="故障类型">
          <el-input v-model="addForm.faultType" placeholder="故障类型, 如: 机械/电气/气动" clearable />
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="addForm.faultDesc" type="textarea" :rows="3" placeholder="故障描述" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认报修</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqRepair">
import { listRepair, addRepair, startRepair, completeRepair } from '@/api/mes/eq/repair';
import type { EqRepairVO, EqRepairQuery, EqRepairForm } from '@/api/mes/eq/repair';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<EqRepairVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<EqRepairQuery>({
  pageNum: 1,
  pageSize: 10,
  repairNo: undefined,
  status: undefined
});

const addDialog = reactive({ visible: false });
const addForm = reactive<EqRepairForm>({
  equipmentId: undefined as unknown as number,
  faultType: '',
  faultDesc: '',
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listRepair(queryParams.value);
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

const handleOpenAdd = () => {
  Object.assign(addForm, {
    equipmentId: undefined as unknown as number,
    faultType: '',
    faultDesc: '',
    remark: ''
  });
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.equipmentId) {
    proxy?.$modal.msgWarning('请输入设备ID');
    return;
  }
  await addRepair(addForm);
  proxy?.$modal.msgSuccess('报修成功, 设备已置故障状态');
  addDialog.visible = false;
  getList();
};

const handleStart = async (row: EqRepairVO) => {
  await proxy?.$modal.confirm('确认开工维修单"' + row.repairNo + '"？');
  await startRepair(row.repairId);
  proxy?.$modal.msgSuccess('开工成功');
  getList();
};

const handleComplete = async (row: EqRepairVO) => {
  const { value: downtimeHours } = await proxy?.$modal.prompt('请输入停机时长(小时):', '维修完成 - ' + row.repairNo);
  if (!downtimeHours || isNaN(Number(downtimeHours)) || Number(downtimeHours) < 0) {
    proxy?.$modal.msgWarning('请输入有效的停机时长');
    return;
  }
  const { value: repairCost } = await proxy?.$modal.prompt('请输入维修成本(默认0):', '维修完成 - ' + row.repairNo);
  const { value: remark } = await proxy?.$modal.prompt('请输入备注(可选):', '维修完成 - ' + row.repairNo);
  await completeRepair(row.repairId, Number(downtimeHours), Number(repairCost || 0), remark || undefined);
  proxy?.$modal.msgSuccess('完成成功');
  getList();
};

const statusLabel = (s: string) => ({ REPORTED: '已报修', REPAIRING: '维修中', COMPLETED: '已完成' })[s] || s;
const statusTagType = (s: string) => ({ REPORTED: 'danger', REPAIRING: 'warning', COMPLETED: 'success' })[s] || 'info';

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
