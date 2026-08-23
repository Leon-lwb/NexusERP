<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="IPQC编号" prop="ipqcNo">
              <el-input v-model="queryParams.ipqcNo" placeholder="请输入IPQC编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工单编号" prop="woNo">
              <el-input v-model="queryParams.woNo" placeholder="工单编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_qc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:qc:ipqc:add']">新增IPQC</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="IPQC编号" prop="ipqcNo" />
        <el-table-column label="工单编号" prop="woNo">
          <template #default="scope">{{ scope.row.woNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="工序" prop="processName" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.processName || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode">
          <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="抽样数量" prop="sampleQty" align="right" />
        <el-table-column label="不良数量" prop="defectQty" align="right" />
        <el-table-column label="不良率(%)" prop="defectRate" align="right">
          <template #default="scope">{{ scope.row.defectRate ?? '-' }}</template>
        </el-table-column>
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
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Check"
              @click="handleOpenInspect(scope.row)"
              v-if="scope.row.status !== 'COMPLETED'"
              v-hasPermi="['mes:qc:ipqc:inspect']"
              >检验</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增IPQC对话框 -->
    <el-dialog v-model="addDialog.visible" title="新增IPQC检验单" width="560px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="工单" required>
          <el-input v-model="addForm.woNo" placeholder="请选择工单" readonly>
            <template #append>
              <el-button icon="Search" @click="openWoSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="流转卡">
          <el-input v-model="addCardNo" placeholder="可选, 选择流转卡" readonly clearable @clear="addForm.cardId = undefined">
            <template #append>
              <el-button icon="Search" @click="openCardSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="工序编码">
          <el-input v-model="addForm.processCode" placeholder="工序编码(可选)" clearable />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="addForm.processName" placeholder="工序名称(可选)" clearable />
        </el-form-item>
        <el-form-item label="物料" required>
          <el-input v-model="addForm.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect" />
            </template>
          </el-input>
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
    <el-dialog v-model="inspectDialog.visible" :title="'IPQC检验 - ' + inspectForm.ipqcNo" width="520px" append-to-body>
      <el-form :model="inspectForm" label-width="100px">
        <el-form-item label="检验结果" required>
          <el-radio-group v-model="inspectForm.inspectResult">
            <el-radio v-for="dict in inspectResultOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="inspectForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良数量">
          <el-input-number v-model="inspectForm.defectQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良品信息">
          <el-input v-model="inspectForm.defectInfo" type="textarea" :rows="3" placeholder='不良品信息JSON, 如: [{"type":"尺寸超差","qty":1}]' />
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

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="950px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号">
          <el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWorkOrders" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryWorkOrders">搜索</el-button>
          <el-button icon="Refresh" @click="resetWoQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="400" @row-dblclick="selectWorkOrder">
        <el-table-column label="工单编号" prop="woNo" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="woStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWorkOrders"
      />
    </el-dialog>

    <!-- 流转卡选择对话框 -->
    <el-dialog v-model="cardDialog.visible" title="选择流转卡" width="900px" append-to-body>
      <el-form :model="cardDialog.query" :inline="true">
        <el-form-item label="流转卡号">
          <el-input v-model="cardDialog.query.cardNo" clearable @keyup.enter="queryFlowCards" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryFlowCards">搜索</el-button>
          <el-button icon="Refresh" @click="resetCardQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="cardDialog.list" height="400" @row-dblclick="selectFlowCard">
        <el-table-column label="流转卡号" prop="cardNo" min-width="140" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="cardStatus" width="100" align="center" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectFlowCard(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="cardDialog.total > 0"
        :total="cardDialog.total"
        v-model:page="cardDialog.query.pageNum"
        v-model:limit="cardDialog.query.pageSize"
        @pagination="queryFlowCards"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="MesQcIpqc">
import { listIpqc, addIpqc, inspectIpqc } from '@/api/mes/qc/ipqc';
import type { QcIpqcVO, QcIpqcQuery, QcIpqcForm } from '@/api/mes/qc/ipqc';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import { listFlowCard } from '@/api/mes/pro/flowcardApi';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_qc_status, mes_qc_result, prd_wo_status } = toRefs<any>(proxy?.useDict('mes_qc_status', 'mes_qc_result', 'prd_wo_status'));
// 检验结果可选项(排除 PENDING)
const inspectResultOptions = computed(() => (mes_qc_result.value as any[]).filter((d) => d.value !== 'PENDING'));

const dataList = ref<QcIpqcVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<QcIpqcQuery>({
  pageNum: 1,
  pageSize: 10,
  ipqcNo: undefined,
  woNo: undefined,
  status: undefined
});

const addDialog = reactive({ visible: false });
const addForm = reactive<QcIpqcForm>({
  mesWoId: undefined as unknown as number,
  woNo: '',
  cardId: undefined,
  processCode: '',
  processName: '',
  materialId: undefined as unknown as number,
  materialCode: '',
  materialName: '',
  sampleQty: undefined,
  remark: ''
});
// 选择器展示字段
const addCardNo = ref('');
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' as string }
});
const cardDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, cardNo: '' as string }
});

const inspectDialog = reactive({ visible: false });
const inspectForm = reactive<{
  ipqcId: number;
  ipqcNo: string;
  inspectResult: string;
  defectInfo?: string;
  sampleQty?: number;
  defectQty?: number;
  remark?: string;
}>({
  ipqcId: 0,
  ipqcNo: '',
  inspectResult: 'QUALIFIED',
  defectInfo: '',
  sampleQty: undefined,
  defectQty: 0,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listIpqc(queryParams.value);
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
    mesWoId: undefined as unknown as number,
    woNo: '',
    cardId: undefined,
    processCode: '',
    processName: '',
    materialId: undefined as unknown as number,
    materialCode: '',
    materialName: '',
    sampleQty: undefined,
    remark: ''
  });
  addCardNo.value = '';
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.mesWoId) {
    proxy?.$modal.msgWarning('请选择工单');
    return;
  }
  if (!addForm.materialId) {
    proxy?.$modal.msgWarning('请选择物料');
    return;
  }
  await addIpqc(addForm);
  proxy?.$modal.msgSuccess('新增成功');
  addDialog.visible = false;
  getList();
};

const handleOpenInspect = (row: QcIpqcVO) => {
  inspectForm.ipqcId = row.ipqcId;
  inspectForm.ipqcNo = row.ipqcNo;
  inspectForm.inspectResult = 'QUALIFIED';
  inspectForm.defectInfo = '';
  inspectForm.sampleQty = row.sampleQty;
  inspectForm.defectQty = 0;
  inspectForm.remark = '';
  inspectDialog.visible = true;
};

const submitInspect = async () => {
  await inspectIpqc(
    inspectForm.ipqcId,
    inspectForm.inspectResult,
    inspectForm.defectInfo,
    inspectForm.sampleQty,
    inspectForm.defectQty,
    inspectForm.remark
  );
  proxy?.$modal.msgSuccess('检验成功');
  inspectDialog.visible = false;
  getList();
};

// ============ 工单选择 ============
const openWoSelect = () => {
  woDialog.visible = true;
  queryWorkOrders();
};
const queryWorkOrders = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const resetWoQuery = () => {
  woDialog.query = { pageNum: 1, pageSize: 10, woNo: '' };
  queryWorkOrders();
};
const selectWorkOrder = (row: any) => {
  addForm.mesWoId = row.mesWoId;
  addForm.woNo = row.woNo;
  // 物料为空时顺带带出工单物料
  if (!addForm.materialId && row.materialId) {
    addForm.materialId = row.materialId;
    addForm.materialCode = row.materialCode;
    addForm.materialName = row.materialName;
  }
  woDialog.visible = false;
};

// ============ 流转卡选择 ============
const openCardSelect = () => {
  cardDialog.visible = true;
  queryFlowCards();
};
const queryFlowCards = () => {
  listFlowCard(cardDialog.query as any).then((res: any) => {
    cardDialog.list = res.rows || [];
    cardDialog.total = res.total || 0;
  });
};
const resetCardQuery = () => {
  cardDialog.query = { pageNum: 1, pageSize: 10, cardNo: '' };
  queryFlowCards();
};
const selectFlowCard = (row: any) => {
  addForm.cardId = row.cardId;
  addCardNo.value = row.cardNo;
  cardDialog.visible = false;
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
