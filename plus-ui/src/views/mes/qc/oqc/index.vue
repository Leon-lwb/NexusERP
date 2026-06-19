<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="OQC编号" prop="oqcNo">
          <el-input v-model="queryParams.oqcNo" placeholder="请输入OQC编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="待检验" value="PENDING" />
            <el-option label="检验中" value="INSPECTING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验结果" prop="inspectResult">
          <el-select v-model="queryParams.inspectResult" placeholder="请选择结果" clearable style="width: 140px">
            <el-option label="合格" value="QUALIFIED" />
            <el-option label="不合格" value="UNQUALIFIED" />
            <el-option label="待定" value="PENDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售订单ID" prop="soId">
          <el-input v-model.number="queryParams.soId" placeholder="销售订单ID" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:qc:oqc:add']">新增OQC</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="OQC编号" prop="oqcNo" width="190" />
        <el-table-column label="销售订单ID" prop="soId" width="120" align="right">
          <template #default="scope">{{ scope.row.soId || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="140">
          <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料名称" prop="materialName" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验数量" prop="quantity" width="100" align="right" />
        <el-table-column label="状态" prop="status" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检验结果" prop="inspectResult" width="100" align="center">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.inspectResult)">{{ resultLabel(scope.row.inspectResult) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检验员" prop="inspectorName" width="100">
          <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验时间" prop="inspectTime" width="160">
          <template #default="scope">{{ scope.row.inspectTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
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
        <el-form-item label="销售订单ID">
          <el-input v-model.number="addForm.soId" placeholder="可选, ERP销售订单ID" clearable />
        </el-form-item>
        <el-form-item label="物料ID" required>
          <el-input v-model.number="addForm.materialId" placeholder="物料ID" clearable />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="addForm.materialCode" placeholder="物料编码(冗余)" clearable />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="addForm.materialName" placeholder="物料名称(冗余)" clearable />
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
            <el-radio value="QUALIFIED">合格</el-radio>
            <el-radio value="UNQUALIFIED">不合格</el-radio>
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
  </div>
</template>

<script setup lang="ts" name="MesQcOqc">
import { listOqc, addOqc, inspectOqc } from '@/api/mes/qc/oqc';
import type { QcOqcVO, QcOqcQuery, QcOqcForm } from '@/api/mes/qc/oqc';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<QcOqcVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<QcOqcQuery>({
  pageNum: 1,
  pageSize: 10,
  oqcNo: undefined,
  status: undefined,
  inspectResult: undefined,
  soId: undefined
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
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.materialId) {
    proxy?.$modal.msgWarning('请输入物料ID');
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

const statusLabel = (s: string) => ({ PENDING: '待检验', INSPECTING: '检验中', COMPLETED: '已完成' })[s] || s;
const statusTagType = (s: string) => ({ PENDING: 'info', INSPECTING: 'warning', COMPLETED: 'success' })[s] || 'info';
const resultLabel = (r: string) => ({ QUALIFIED: '合格', UNQUALIFIED: '不合格', PENDING: '待定' })[r] || r;
const resultTagType = (r: string) => ({ QUALIFIED: 'success', UNQUALIFIED: 'danger', PENDING: 'info' })[r] || 'info';

onMounted(() => {
  getList();
});
</script>
