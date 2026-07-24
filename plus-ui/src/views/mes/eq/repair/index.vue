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
            <el-form-item label="故障类型" prop="faultType">
              <el-select v-model="queryParams.faultType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="d in mes_fault_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="d in mes_repair_status" :key="d.value" :label="d.label" :value="d.value" />
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
          <el-button type="warning" plain icon="WarnTriangleFilled" @click="handleOpenAdd" v-hasPermi="['mes:eq:repair:add']">故障报修</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['mes:eq:repair:export']">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="primary" plain icon="TrendCharts" @click="goStatistics" v-hasPermi="['mes:eq:repair:statistics']">故障分析</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="维修单号" prop="repairNo" width="140" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.equipmentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="故障类型" prop="faultType" align="center">
          <template #default="scope">
            <dict-tag v-if="scope.row.faultType" :options="mes_fault_type" :value="scope.row.faultType" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="故障描述" prop="faultDesc" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <dict-tag :options="mes_repair_status" :value="scope.row.status" />
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
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" :show-overflow-tooltip="false">
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
              @click="handleOpenComplete(scope.row)"
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
      <el-form :model="addForm" label-width="90px" ref="addFormRef" :rules="addRules">
        <el-form-item label="设备" prop="equipmentId">
          <equipment-select v-model="addForm.equipmentId" />
        </el-form-item>
        <el-form-item label="故障类型" prop="faultType">
          <el-select v-model="addForm.faultType" placeholder="请选择故障类型" clearable style="width: 100%">
            <el-option v-for="d in mes_fault_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDesc">
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

    <!-- 维修完成对话框(替代原3连prompt, 含更换备件明细 P1-2 R1) -->
    <el-dialog v-model="completeDialog.visible" :title="`维修完成 - ${completeForm.repairNo}`" width="640px" append-to-body>
      <el-form :model="completeForm" label-width="120px">
        <el-form-item label="停机时长(小时)">
          <el-input-number v-model="completeForm.downtimeHours" :min="0" :precision="2" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="维修成本(元)">
          <el-input-number v-model="completeForm.repairCost" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="completeForm.remark" type="textarea" :rows="2" placeholder="维修说明/备注" />
        </el-form-item>
        <el-form-item label="更换备件">
          <el-table :data="completeForm.spares" border size="small" style="width: 100%">
            <el-table-column label="备件" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.spareId" filterable placeholder="选择备件" style="width: 100%">
                  <el-option v-for="s in spareOptions" :key="s.spareId" :label="s.spareCode + ' - ' + s.spareName" :value="s.spareId" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :precision="2" :step="1" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="scope">
                <el-button link type="danger" @click="completeForm.spares.splice(scope.$index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            size="small"
            icon="Plus"
            style="margin-top: 8px"
            @click="completeForm.spares.push({ spareId: undefined as unknown as number, quantity: 1 })"
            >添加备件</el-button
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqRepair">
import { listRepair, addRepair, startRepair, completeRepair, exportRepair } from '@/api/mes/eq/repair';
import type { EqRepairVO, EqRepairQuery, EqRepairForm } from '@/api/mes/eq/repair';
import { listSparePart } from '@/api/mes/eq/spare-part';
import type { EqSparePartVO } from '@/api/mes/eq/spare-part';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_repair_status, mes_fault_type } = toRefs<any>(proxy?.useDict('mes_repair_status', 'mes_fault_type'));
const router = useRouter();

const goStatistics = () => {
  router.push('/mes/eq-maintain/statistics');
};

const dataList = ref<EqRepairVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<EqRepairQuery>({
  pageNum: 1,
  pageSize: 10,
  repairNo: undefined,
  status: undefined,
  faultType: undefined
});

const addDialog = reactive({ visible: false });
const addFormRef = ref();
const addRules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  faultDesc: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
};
const addForm = reactive<EqRepairForm & { remark?: string }>({
  equipmentId: undefined as unknown as number,
  faultType: '',
  faultDesc: '',
  remark: ''
});

const completeDialog = reactive({ visible: false });
const completeForm = reactive<{
  repairId: number;
  repairNo: string;
  downtimeHours: number;
  repairCost: number;
  remark: string;
  spares: Array<{ spareId: number | undefined; quantity: number }>;
}>({
  repairId: 0,
  repairNo: '',
  downtimeHours: 0,
  repairCost: 0,
  remark: '',
  spares: []
});
/** 备件下拉选项(完成维修时选择更换备件, 懒加载) */
const spareOptions = ref<EqSparePartVO[]>([]);

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

const handleExport = () => {
  exportRepair(queryParams.value);
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
  await addFormRef.value?.validate();
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

const handleOpenComplete = async (row: EqRepairVO) => {
  completeForm.repairId = row.repairId;
  completeForm.repairNo = row.repairNo;
  completeForm.downtimeHours = 0;
  completeForm.repairCost = 0;
  completeForm.remark = '';
  completeForm.spares = [];
  // 懒加载备件选项(仅首次打开)
  if (spareOptions.value.length === 0) {
    try {
      const res = await listSparePart({ pageNum: 1, pageSize: 200 });
      spareOptions.value = res.rows || [];
    } catch {
      /* ignore */
    }
  }
  completeDialog.visible = true;
};

const submitComplete = async () => {
  const spares = (completeForm.spares || []).filter((s) => s.spareId != null).map((s) => ({ spareId: s.spareId as number, quantity: s.quantity }));
  await completeRepair(completeForm.repairId, {
    downtimeHours: completeForm.downtimeHours,
    repairCost: completeForm.repairCost,
    remark: completeForm.remark || undefined,
    spares: spares.length > 0 ? spares : undefined
  });
  proxy?.$modal.msgSuccess('完成成功');
  completeDialog.visible = false;
  getList();
};

onMounted(() => {
  getList();
});
</script>
