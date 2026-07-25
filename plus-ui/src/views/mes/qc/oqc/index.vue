<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="OQC编号" prop="oqcNo">
              <el-input v-model="queryParams.oqcNo" placeholder="请输入OQC编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_qc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:qc:oqc:add']">新增OQC</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="OQC编号" prop="oqcNo" />
        <el-table-column label="物料编码" prop="materialCode">
          <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料名称" prop="materialName" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验数量" prop="quantity" align="right" />
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <dict-tag :options="mes_qc_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="检验结果" prop="inspectResult" align="center">
          <template #default="scope">
            <dict-tag :options="mes_qc_result" :value="scope.row.inspectResult" />
          </template>
        </el-table-column>
        <el-table-column label="检验员" prop="inspectorName">
          <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验时间" prop="inspectTime">
          <template #default="scope">{{ scope.row.inspectTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Check"
              @click="handleOpenInspect(scope.row)"
              v-if="scope.row.status !== 'COMPLETED'"
              v-hasPermi="['mes:qc:oqc:inspect']"
              >检验</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增OQC对话框 -->
    <el-dialog v-model="addDialog.visible" title="新增OQC检验单" width="560px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="销售订单">
          <el-input v-model="addSoNo" placeholder="可选, 选择ERP销售订单" readonly clearable @clear="addForm.soId = undefined">
            <template #append>
              <el-button icon="Search" @click="openSoSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料" required>
          <el-input v-model="addForm.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="检验数量">
          <el-input-number v-model="addForm.quantity" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="addForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- 检验对话框 -->
    <el-dialog v-model="inspectDialog.visible" :title="'OQC检验 - ' + inspectForm.oqcNo" width="520px" append-to-body>
      <el-form :model="inspectForm" label-width="100px">
        <el-form-item label="检验结果" required>
          <el-radio-group v-model="inspectForm.inspectResult">
            <el-radio v-for="dict in inspectResultOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="inspectForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良品信息">
          <el-input v-model="inspectForm.defectInfo" type="textarea" :rows="3" placeholder='不良品信息JSON, 如: [{"type":"外观不良","qty":1}]' />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitInspect">确认检验</el-button>
      </template>
    </el-dialog>

    <!-- 销售订单选择对话框 -->
    <el-dialog v-model="soDialog.visible" title="选择销售订单" width="900px" append-to-body>
      <el-form :model="soDialog.query" :inline="true">
        <el-form-item label="订单编号">
          <el-input v-model="soDialog.query.soNo" clearable @keyup.enter="queryOrders" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryOrders">搜索</el-button>
          <el-button icon="Refresh" @click="resetSoQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="soDialog.list" height="400" @row-dblclick="selectOrder">
        <el-table-column label="订单编号" prop="soNo" min-width="160" />
        <el-table-column label="客户参考号" prop="customerRefNo" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.customerRefNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="soDialog.total > 0"
        :total="soDialog.total"
        v-model:page="soDialog.query.pageNum"
        v-model:limit="soDialog.query.pageSize"
        @pagination="queryOrders"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="MesQcOqc">
import { listOqc, addOqc, inspectOqc } from '@/api/mes/qc/oqc';
import type { QcOqcVO, QcOqcQuery, QcOqcForm } from '@/api/mes/qc/oqc';
import { listOrderheader } from '@/api/erp/so/orderheader';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_qc_status, mes_qc_result } = toRefs<any>(proxy?.useDict('mes_qc_status', 'mes_qc_result'));
// 检验结果可选项(排除 PENDING, 检验动作只能判合格/不合格)
const inspectResultOptions = computed(() => (mes_qc_result.value as any[]).filter((d) => d.value !== 'PENDING'));

const dataList = ref<QcOqcVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<QcOqcQuery>({
  pageNum: 1,
  pageSize: 10,
  oqcNo: undefined,
  status: undefined
});

const addDialog = reactive({ visible: false });
const addForm = reactive<QcOqcForm>({
  soId: undefined,
  materialId: undefined as unknown as number,
  materialCode: '',
  materialName: '',
  quantity: undefined,
  sampleQty: undefined,
  remark: ''
});
// 选择器展示字段
const addSoNo = ref('');
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const soDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, soNo: '' as string }
});

const inspectDialog = reactive({ visible: false });
const inspectForm = reactive<{ oqcId: number; oqcNo: string; inspectResult: string; defectInfo?: string; sampleQty?: number; remark?: string }>({
  oqcId: 0,
  oqcNo: '',
  inspectResult: 'QUALIFIED',
  defectInfo: '',
  sampleQty: undefined,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listOqc(queryParams.value);
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
    soId: undefined,
    materialId: undefined as unknown as number,
    materialCode: '',
    materialName: '',
    quantity: undefined,
    sampleQty: undefined,
    remark: ''
  });
  addSoNo.value = '';
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.materialId) {
    proxy?.$modal.msgWarning('请选择物料');
    return;
  }
  await addOqc(addForm);
  proxy?.$modal.msgSuccess('新增成功');
  addDialog.visible = false;
  getList();
};

const handleOpenInspect = (row: QcOqcVO) => {
  inspectForm.oqcId = row.oqcId;
  inspectForm.oqcNo = row.oqcNo;
  inspectForm.inspectResult = 'QUALIFIED';
  inspectForm.defectInfo = '';
  inspectForm.sampleQty = row.sampleQty;
  inspectForm.remark = '';
  inspectDialog.visible = true;
};

const submitInspect = async () => {
  await inspectOqc(inspectForm.oqcId, inspectForm.inspectResult, inspectForm.defectInfo, inspectForm.sampleQty, inspectForm.remark);
  proxy?.$modal.msgSuccess('检验成功');
  inspectDialog.visible = false;
  getList();
};

// ============ 销售订单选择 ============
const openSoSelect = () => {
  soDialog.visible = true;
  queryOrders();
};
const queryOrders = () => {
  listOrderheader(soDialog.query as any).then((res: any) => {
    soDialog.list = res.rows || [];
    soDialog.total = res.total || 0;
  });
};
const resetSoQuery = () => {
  soDialog.query = { pageNum: 1, pageSize: 10, soNo: '' };
  queryOrders();
};
const selectOrder = (row: any) => {
  addForm.soId = row.soId;
  addSoNo.value = row.soNo;
  soDialog.visible = false;
};

// ============ 物料选择 ============
const openMaterialSelect = () => {
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  addForm.materialId = row.materialId;
  addForm.materialCode = row.materialCode;
  addForm.materialName = row.materialName;
};

onMounted(() => {
  getList();
});
</script>
