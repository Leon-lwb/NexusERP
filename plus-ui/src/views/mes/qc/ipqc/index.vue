<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
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

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="IPQC编号" prop="ipqcNo" min-width="140" />
      <el-table-column label="工单编号" prop="woNo" min-width="140">
        <template #default="scope">{{ scope.row.woNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="工序" prop="processName" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.processName || '-' }}</template>
      </el-table-column>
      <el-table-column label="物料编码" prop="materialCode" min-width="140">
        <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="抽样数量" prop="sampleQty" align="right" min-width="90" />
      <el-table-column label="不良数量" prop="defectQty" align="right" min-width="90" />
      <el-table-column label="不良率(%)" prop="defectRate" align="right" min-width="90">
        <template #default="scope">{{ scope.row.defectRate ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_qc_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="检验结果" prop="inspectResult" align="center" min-width="90">
        <template #default="scope">
          <dict-tag :options="mes_qc_result" :value="scope.row.inspectResult" />
        </template>
      </el-table-column>
      <el-table-column label="检验员" prop="inspectorName" min-width="90">
        <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleOpenDetail(scope.row)" v-hasPermi="['mes:qc:ipqc:query']">详情</el-button>
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

    <!-- 新增IPQC对话框 -->
    <el-dialog v-model="addDialog.visible" title="新增IPQC检验单" width="500px" append-to-body>
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
    <el-dialog v-model="inspectDialog.visible" :title="'IPQC检验 - ' + inspectForm.ipqcNo" width="500px" append-to-body>
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

    <!-- 检验单详情对话框(只读) -->
    <el-dialog v-model="detailDialog.visible" :title="'IPQC详情 - ' + (detailData.ipqcNo || '')" width="860px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="IPQC编号">{{ detailData.ipqcNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="mes_qc_status" :value="detailData.status" />
        </el-descriptions-item>
        <el-descriptions-item label="检验结果">
          <dict-tag :options="mes_qc_result" :value="detailData.inspectResult" />
        </el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ detailData.woNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ detailData.processName || detailData.processCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ detailData.materialCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称" :span="2">{{ detailData.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="抽样数量">{{ detailData.sampleQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{ detailData.defectQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="不良率(%)">{{ detailData.defectRate ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验员">{{ detailData.inspectorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验时间">{{ detailData.inspectTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="不良品信息">{{ detailData.defectInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">检验明细</el-divider>
      <el-alert v-if="detailEntries.length === 0" title="无检验明细(该检验单未匹配启用的检验模板)" type="info" :closable="false" />
      <el-table v-else :data="detailEntries" border size="small">
        <el-table-column label="项目" min-width="140">
          <template #default="scope">{{ scope.row.itemCode }} {{ scope.row.itemName }}</template>
        </el-table-column>
        <el-table-column label="标准" prop="standardDesc" min-width="120" />
        <el-table-column label="样本量" prop="sampleQty" width="70" align="center" />
        <el-table-column label="关键度" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.criticality === 'CRITICAL'" type="danger" size="small">关键</el-tag>
            <el-tag v-else-if="scope.row.criticality === 'MINOR'" type="info" size="small">次要</el-tag>
            <el-tag v-else type="warning" size="small">重要</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实测值" prop="measuredValue" min-width="120" />
        <el-table-column label="判定" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.judgeResult === 'PASS'" type="success" size="small">PASS</el-tag>
            <el-tag v-else-if="scope.row.judgeResult === 'FAIL'" type="danger" size="small">FAIL</el-tag>
            <el-tag v-else type="info" size="small">待判</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="960px" append-to-body>
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
    <el-dialog v-model="cardDialog.visible" title="选择流转卡" width="960px" append-to-body>
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
import { listIpqc, addIpqc, inspectIpqc, getIpqc, getIpqcDetails } from '@/api/mes/qc/ipqc';
import type { QcIpqcVO, QcIpqcQuery, QcIpqcForm } from '@/api/mes/qc/ipqc';
import type { QcInspectDetailVO } from '@/api/mes/qc/iqc';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import { listFlowCard } from '@/api/mes/pro/flowcardApi';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_qc_status, mes_qc_result, prd_wo_status } = toRefs<any>(proxy?.useDict('mes_qc_status', 'mes_qc_result', 'prd_wo_status'));
// 检验结果可选项(排除 PENDING)
const inspectResultOptions = computed(() => (mes_qc_result.value as any[]).filter((d) => d.value !== 'PENDING'));

// 检验单的新增/检验为独立业务弹窗(非标准 CRUD 表单)，列表骨架走 useCrud，以下占位 API 不会被调用
const ipqcFormRef = ref<ElFormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<QcIpqcQuery>({
  pageNum: 1,
  pageSize: 10,
  ipqcNo: undefined,
  woNo: undefined,
  status: undefined
});

const { loading, list, total, showSearch, getList, handleQuery } = useCrud<QcIpqcVO, QcIpqcQuery>({
  apis: { list: listIpqc, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'ipqcId',
  entityName: 'IPQC检验单',
  formRef: ipqcFormRef,
  queryParams,
  initForm: () => ({})
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

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

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

// ============ 详情查看(只读) ============
const detailDialog = reactive({ visible: false });
const detailData = ref<QcIpqcVO>({} as QcIpqcVO);
const detailEntries = ref<QcInspectDetailVO[]>([]);

const handleOpenDetail = async (row: QcIpqcVO) => {
  detailData.value = row;
  detailEntries.value = [];
  detailDialog.visible = true;
  try {
    const [infoRes, detailRes] = await Promise.all([getIpqc(row.ipqcId), getIpqcDetails(row.ipqcId)]);
    if (infoRes.data) detailData.value = infoRes.data;
    detailEntries.value = detailRes.data || [];
  } catch {
    detailEntries.value = [];
  }
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

getList();
</script>
