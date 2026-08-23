<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="保养类型" prop="maintenanceType">
              <el-select v-model="queryParams.maintenanceType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="结果" prop="result">
              <el-select v-model="queryParams.result" placeholder="请选择结果" clearable style="width: 100%">
                <el-option v-for="d in mes_maintenance_result" :key="d.value" :label="d.label" :value="d.value" />
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
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:maintenance:complete']">手动保养</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['mes:eq:maintenance:record:export']">导出</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="设备编码" prop="equipmentCode" width="130" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
        <el-table-column label="保养类型" prop="maintenanceType" align="center">
          <template #default="scope">
            <dict-tag v-if="scope.row.maintenanceType" :options="mes_maintenance_type" :value="scope.row.maintenanceType" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="执行人" prop="executorName">
          <template #default="scope">{{ scope.row.executorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行时间" prop="executeTime" width="160" />
        <el-table-column label="保养内容" prop="content" min-width="160" show-overflow-tooltip />
        <el-table-column label="结果" prop="result" align="center">
          <template #default="scope">
            <dict-tag :options="mes_maintenance_result" :value="scope.row.result" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="100" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:maintenance:record:remove']"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 手动保养对话框 -->
    <el-dialog v-model="addDialog.visible" title="手动保养记录" width="500px" append-to-body>
      <el-form :model="addForm" label-width="100px" ref="addFormRef" :rules="addRules">
        <el-form-item label="设备" prop="equipmentId">
          <equipment-select v-model="addForm.equipmentId" />
        </el-form-item>
        <el-form-item label="保养类型" prop="maintenanceType">
          <el-select v-model="addForm.maintenanceType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养内容">
          <el-input v-model="addForm.content" type="textarea" :rows="3" placeholder="保养执行情况" />
        </el-form-item>
        <el-form-item label="保养结果">
          <el-select v-model="addForm.result" style="width: 100%">
            <el-option v-for="d in mes_maintenance_result" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqMaintenanceRecord">
import { listMaintenanceRecord, addMaintenanceRecord, delMaintenanceRecord, exportMaintenanceRecord } from '@/api/mes/eq/maintenance-record';
import type { EqMaintenanceRecordVO, EqMaintenanceRecordQuery, EqMaintenanceRecordForm } from '@/api/mes/eq/maintenance-record';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_maintenance_type, mes_maintenance_result } = toRefs<any>(proxy?.useDict('mes_maintenance_type', 'mes_maintenance_result'));

const dataList = ref<EqMaintenanceRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<EqMaintenanceRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  maintenanceType: undefined,
  result: undefined
});

const addDialog = reactive({ visible: false });
const addFormRef = ref();
const addRules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择保养类型', trigger: 'change' }]
};
const addForm = reactive<EqMaintenanceRecordForm>({
  equipmentId: undefined as unknown as number,
  maintenanceType: '',
  content: '',
  result: 'NORMAL',
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMaintenanceRecord(queryParams.value);
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
  exportMaintenanceRecord(queryParams.value);
};

const handleOpenAdd = () => {
  Object.assign(addForm, {
    equipmentId: undefined as unknown as number,
    maintenanceType: '',
    content: '',
    result: 'NORMAL',
    remark: ''
  });
  addDialog.visible = true;
};

const submitAdd = async () => {
  await addFormRef.value?.validate();
  await addMaintenanceRecord(addForm);
  proxy?.$modal.msgSuccess('保养记录已添加');
  addDialog.visible = false;
  getList();
};

const handleDelete = async (row: EqMaintenanceRecordVO) => {
  await proxy?.$modal.confirm('确认删除该保养记录？');
  await delMaintenanceRecord([row.recordId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

onMounted(() => {
  getList();
});
</script>
