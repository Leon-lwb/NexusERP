<template>
  <el-drawer v-model="visible" :title="'委外闭环详情 - ' + (info?.subcontractNo || '')" size="72%" destroy-on-close>
    <div v-loading="loading">
      <!-- 基本信息 -->
      <el-descriptions :column="4" border size="small" class="mb12">
        <el-descriptions-item label="委外单号">{{ info?.subcontractNo }}</el-descriptions-item>
        <el-descriptions-item label="产出物料">{{ info?.materialCode }} {{ info?.materialName }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ info?.vendorName || '未选定' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(info?.status)" size="small">{{ statusLabel(info?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ info?.plannedQty }}</el-descriptions-item>
        <el-descriptions-item label="实际数量">{{ info?.actualQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="加工单价">{{ info?.unitPrice || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标仓库">{{ info?.targetWarehouseName || '默认路由' }}</el-descriptions-item>
        <el-descriptions-item label="工艺要求" :span="4">
          <span style="white-space: pre-wrap">{{ info?.processRequirement || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- BOM 快照 -->
        <el-tab-pane label="BOM快照" name="bom">
          <div class="mb8">
            <el-button
              size="small"
              type="primary"
              icon="Refresh"
              :disabled="info?.status !== 'DRAFT'"
              @click="handleGenSnapshot"
              v-hasPermi="['prd:subcontract:edit']"
            >
              生成/重建快照
            </el-button>
            <span class="tip" v-if="!info?.bomId">订单未关联 BOM, 编辑订单选择 BOM 后可生成快照</span>
          </div>
          <el-table :data="bomLines" size="small" border max-height="380">
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="130" />
            <el-table-column prop="materialSpec" label="规格" min-width="110" show-overflow-tooltip />
            <el-table-column prop="quantityPer" label="单耗" width="80" align="right" />
            <el-table-column prop="lossRate" label="损耗%" width="80" align="right" />
            <el-table-column prop="demandQty" label="需求量" width="100" align="right" />
            <el-table-column prop="issuedQty" label="已发量" width="100" align="right" />
            <el-table-column label="供应方" width="100" align="center">
              <template #default="scope">
                <el-tag :type="supplyTagType(scope.row.supplyType)" size="small">{{ supplyLabel(scope.row.supplyType) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!bomLines.length" description="暂无BOM快照" :image-size="50" />
        </el-tab-pane>

        <!-- 工程文件 -->
        <el-tab-pane label="工程文件" name="docs">
          <div class="mb8">
            <el-button size="small" type="primary" icon="Plus" @click="openDocDialog()" v-hasPermi="['prd:subcontract:edit']">上传文件</el-button>
            <span class="tip">GERBER / 坐标 / BOM / 钢网 / 工艺要求随询价与发料资料包下发</span>
          </div>
          <el-table :data="docs" size="small" border max-height="380">
            <el-table-column prop="docType" label="类型" width="110" align="center">
              <template #default="scope">
                <dict-tag :options="erp_subcontract_doc_type" :value="scope.row.docType" />
              </template>
            </el-table-column>
            <el-table-column prop="docName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column prop="docVersion" label="版本" width="80" align="center" />
            <el-table-column prop="createTime" label="上传时间" width="150" />
            <el-table-column label="操作" width="120" align="center">
              <template #default="scope">
                <el-button link type="primary" icon="View" @click="previewDoc(scope.row)">查看</el-button>
                <el-button link type="danger" icon="Delete" @click="handleDelDoc(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!docs.length" description="暂无工程文件" :image-size="50" />
        </el-tab-pane>

        <!-- 报价 -->
        <el-tab-pane label="报价" name="quotes">
          <div class="mb8">
            <el-button size="small" type="primary" icon="Plus" @click="openQuoteDialog()" v-hasPermi="['prd:subcontract:edit']">录入报价</el-button>
          </div>
          <el-table :data="quotes" size="small" border max-height="380">
            <el-table-column prop="vendorName" label="供应商" min-width="150" />
            <el-table-column prop="unitPrice" label="加工单价" width="100" align="right" />
            <el-table-column prop="toolingFee" label="治具/钢网费" width="110" align="right" />
            <el-table-column prop="leadTimeDays" label="交期(天)" width="90" align="right" />
            <el-table-column prop="quoteTime" label="报价时间" width="150" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="quoteTagType(scope.row.quoteStatus)" size="small">{{ quoteLabel(scope.row.quoteStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="scope">
                <el-button
                  link
                  type="success"
                  icon="Check"
                  :disabled="scope.row.quoteStatus === 'SELECTED' || orderLocked"
                  @click="handleSelectQuote(scope.row)"
                  v-hasPermi="['prd:subcontract:edit']"
                  >选定</el-button
                >
                <el-button link type="danger" icon="Delete" @click="handleDelQuote(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!quotes.length" description="暂无报价记录" :image-size="50" />
        </el-tab-pane>

        <!-- 发料 -->
        <el-tab-pane label="发料" name="issues">
          <div class="mb8">
            <el-button size="small" type="primary" icon="MagicStick" @click="loadSuggest" v-hasPermi="['prd:subcontract:issue']">备料建议</el-button>
          </div>
          <!-- 备料建议 -->
          <el-table v-if="suggest.length" :data="suggest" size="small" border class="mb12" max-height="300">
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="120" />
            <el-table-column prop="remainingQty" label="待发量" width="90" align="right" />
            <el-table-column prop="allocatedQty" label="已分配" width="90" align="right" />
            <el-table-column label="分配明细(批次×数量)" min-width="260">
              <template #default="scope">
                <span v-if="!scope.row.allocations.length" class="tip">无可分配库存</span>
                <el-tag v-for="a in scope.row.allocations" :key="a.stockId" size="small" class="mr4">
                  {{ a.batchCode || '无批次' }} × {{ a.deductQty }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="缺料" width="90" align="right">
              <template #default="scope">
                <span :class="scope.row.shortage > 0 ? 'text-danger' : ''">{{ scope.row.shortage || 0 }}</span>
              </template>
            </el-table-column>
          </el-table>
          <!-- 发料单列表 -->
          <el-table :data="issues" size="small" border max-height="300">
            <el-table-column prop="issueNo" label="发料单号" min-width="180" />
            <el-table-column prop="totalQty" label="总数量" width="100" align="right" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'POSTED' ? 'success' : 'info'" size="small">
                  {{ scope.row.status === 'POSTED' ? '已过账' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="issuerName" label="发料人" width="100" />
            <el-table-column prop="issueDate" label="发料时间" width="150" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="scope">
                <el-button
                  link
                  type="success"
                  icon="Check"
                  v-if="scope.row.status === 'DRAFT'"
                  @click="handlePostIssue(scope.row)"
                  v-hasPermi="['prd:subcontract:issue']"
                  >过账</el-button
                >
                <el-button
                  link
                  type="danger"
                  icon="Delete"
                  v-if="scope.row.status === 'DRAFT'"
                  @click="handleDelIssue(scope.row)"
                  v-hasPermi="['prd:subcontract:issue']"
                />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!issues.length && !suggest.length" description="暂无发料单, 先点「备料建议」生成分配" :image-size="50" />
        </el-tab-pane>

        <!-- 收货 -->
        <el-tab-pane label="收货" name="receives">
          <div class="mb8">
            <el-button
              size="small"
              type="primary"
              icon="Box"
              :disabled="!canReceive"
              @click="openReceiveDialog"
              v-hasPermi="['prd:subcontract:receive']"
            >
              创建收货单
            </el-button>
          </div>
          <el-table :data="receives" size="small" border max-height="340">
            <el-table-column prop="receiveNo" label="收货单号" min-width="180" />
            <el-table-column prop="receivedQty" label="收货量" width="100" align="right" />
            <el-table-column prop="targetWarehouseName" label="入库仓库" width="140" />
            <el-table-column prop="vendorBatchCode" label="外协批次" width="120" show-overflow-tooltip />
            <el-table-column prop="snScope" label="SN范围" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'POSTED' ? 'success' : 'info'" size="small">
                  {{ scope.row.status === 'POSTED' ? '已过账' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="scope">
                <el-button
                  link
                  type="success"
                  icon="Check"
                  v-if="scope.row.status === 'DRAFT'"
                  @click="handlePostReceive(scope.row)"
                  v-hasPermi="['prd:subcontract:receive']"
                  >过账</el-button
                >
                <el-button
                  link
                  type="danger"
                  icon="Delete"
                  v-if="scope.row.status === 'DRAFT'"
                  @click="handleDelReceive(scope.row)"
                  v-hasPermi="['prd:subcontract:receive']"
                />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!receives.length" description="暂无收货单" :image-size="50" />
        </el-tab-pane>

        <!-- SN -->
        <el-tab-pane :label="'SN (' + sns.length + ')'" name="sns">
          <el-table :data="sns" size="small" border max-height="380">
            <el-table-column prop="snCode" label="SN编码" min-width="220" />
            <el-table-column prop="snStatus" label="状态" width="120" align="center" />
            <el-table-column prop="productionDate" label="生成日期" width="120" />
          </el-table>
          <el-empty v-if="!sns.length" description="发料完成后由 MES 自动生成 PCBA SN" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 报价对话框 -->
    <el-dialog v-model="quoteOpen" title="录入报价" width="520px" append-to-body>
      <el-form ref="quoteFormRef" :model="quoteForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-input v-model="quoteForm.vendorName" placeholder="供应商名称" />
        </el-form-item>
        <el-form-item label="加工单价" required>
          <el-input-number v-model="quoteForm.unitPrice" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="治具/钢网费">
          <el-input-number v-model="quoteForm.toolingFee" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交期(天)">
          <el-input-number v-model="quoteForm.leadTimeDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="报价有效期">
          <el-date-picker v-model="quoteForm.validUntil" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="quoteForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteOpen = false">取消</el-button>
        <el-button type="primary" @click="submitQuote">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工程文件对话框 -->
    <el-dialog v-model="docOpen" title="上传工程文件" width="520px" append-to-body>
      <el-form :model="docForm" label-width="100px">
        <el-form-item label="文件类型" required>
          <el-select v-model="docForm.docType" placeholder="请选择文件类型" style="width: 100%">
            <el-option v-for="d in erp_subcontract_doc_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件版本">
          <el-input v-model="docForm.docVersion" placeholder="如 V1.0" />
        </el-form-item>
        <el-form-item label="文件" required>
          <file-upload v-model="docForm.fileOssId" :limit="1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="docForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!docForm.fileOssId || !docForm.docType" @click="submitDoc">保存</el-button>
      </template>
    </el-dialog>

    <!-- 收货对话框 -->
    <el-dialog v-model="receiveOpen" title="创建收货单" width="560px" append-to-body>
      <el-form :model="receiveForm" label-width="110px">
        <el-form-item label="收货数量" required>
          <el-input-number v-model="receiveForm.receivedQty" :min="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="入库仓库">
          <span>{{ info?.targetWarehouseName || '默认半成品仓路由' }}(收货过账自动建批次)</span>
        </el-form-item>
        <el-form-item label="外协批次号">
          <el-input v-model="receiveForm.vendorBatchCode" placeholder="外协厂批次号(可选)" />
        </el-form-item>
        <el-form-item label="SN范围">
          <el-input v-model="receiveForm.snScope" placeholder="如 SN-SC...0001 ~ SN-SC...0100(可选)" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="receiveForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveOpen = false">取消</el-button>
        <el-button type="primary" @click="submitReceive">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts" name="SubcontractClosureDetail">
import { ref, computed, watch, getCurrentInstance, toRefs } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getBomLines,
  generateSnapshot,
  listQuotes,
  saveQuote,
  delQuotes,
  selectQuote,
  listDocs,
  saveDoc,
  delDocs,
  listIssues,
  suggestAllocations,
  createIssue,
  delIssue,
  postIssue,
  listReceives,
  createReceive,
  delReceive,
  postReceive,
  listSubcontractSns
} from '@/api/erp/prd/subcontract';

const proxy = getCurrentInstance()?.proxy as any;
const { erp_subcontract_doc_type } = toRefs<any>(proxy?.useDict('erp_subcontract_doc_type'));

const visible = ref(false);
const loading = ref(false);
const info = ref<any>(null);
const activeTab = ref('bom');
const props = defineProps<{ row: any }>();
const emit = defineEmits<{ (e: 'close'): void }>();
const bomLines = ref<any[]>([]);
const docs = ref<any[]>([]);
const quotes = ref<any[]>([]);
const suggest = ref<any[]>([]);
const issues = ref<any[]>([]);
const receives = ref<any[]>([]);
const sns = ref<any[]>([]);

const quoteOpen = ref(false);
const quoteForm = ref<any>({});
const docOpen = ref(false);
const docForm = ref<any>({});
const receiveOpen = ref(false);
const receiveForm = ref<any>({});

const orderLocked = computed(() => ['RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'].includes(info.value?.status));
const canReceive = computed(() => ['RELEASED', 'IN_PROGRESS'].includes(info.value?.status));

const statusLabelMap: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已批准',
  RELEASED: '已发料下达',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CLOSED: '已关闭'
};
const statusLabel = (s?: string) => statusLabelMap[s || ''] || s || '-';
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';
const statusTagType = (s?: string): TagType =>
  (({ DRAFT: 'info', SUBMITTED: 'warning', APPROVED: 'primary', RELEASED: 'primary', IN_PROGRESS: 'warning', COMPLETED: 'success', CLOSED: 'info' })[
    s || ''
  ] || 'info') as TagType;

const supplyLabelMap: Record<string, string> = { SELF_PROVIDED: '自备料', VENDOR_PROVIDED: '外协厂供', CUSTOMER_PROVIDED: '客供' };
const supplyLabel = (s?: string) => supplyLabelMap[s || ''] || s || '-';
const supplyTagType = (s?: string): TagType =>
  (({ SELF_PROVIDED: 'primary', VENDOR_PROVIDED: 'warning', CUSTOMER_PROVIDED: 'info' })[s || ''] || 'info') as TagType;

const quoteLabelMap: Record<string, string> = { PENDING: '待确认', SELECTED: '已选定', NOT_SELECTED: '未选中', EXPIRED: '已失效' };
const quoteLabel = (s?: string) => quoteLabelMap[s || ''] || s || '-';
const quoteTagType = (s?: string): TagType =>
  (({ PENDING: 'info', SELECTED: 'success', NOT_SELECTED: 'danger', EXPIRED: 'info' })[s || ''] || 'info') as TagType;

const open = async (row: any) => {
  info.value = row;
  visible.value = true;
  loading.value = true;
  activeTab.value = 'bom';
  try {
    await Promise.all([loadBomLines(), loadDocs(), loadQuotes(), loadIssues(), loadReceives(), loadSns()]);
  } finally {
    loading.value = false;
  }
};

// 外部 row 变化即打开并加载
watch(
  () => props.row,
  (row) => {
    if (row) open(row);
  },
  { immediate: true }
);
watch(visible, (v) => {
  if (!v) {
    suggest.value = [];
    emit('close');
  }
});
defineExpose({ open });

const loadBomLines = async () => {
  const res: any = await getBomLines(info.value.subcontractId);
  bomLines.value = res.data || [];
};

const handleGenSnapshot = async () => {
  await ElMessageBox.confirm('将删除现有快照并按关联 BOM 重建, 确认?', '提示', { type: 'warning' });
  const res: any = await generateSnapshot(info.value.subcontractId);
  ElMessage.success('快照生成 ' + res.data + ' 行');
  await loadBomLines();
};

const loadDocs = async () => {
  const [orderRes, matRes]: any[] = await Promise.all([
    listDocs({ subcontractId: info.value.subcontractId }),
    listDocs({ materialId: info.value.materialId, subcontractId: 0 })
  ]);
  docs.value = [...(orderRes.data || []), ...(matRes.data || [])];
};

const openDocDialog = () => {
  docForm.value = { materialId: info.value.materialId, subcontractId: info.value.subcontractId, docType: null, docVersion: null, fileOssId: null };
  docOpen.value = true;
};
const submitDoc = async () => {
  await saveDoc(docForm.value);
  ElMessage.success('工程文件已保存');
  docOpen.value = false;
  await loadDocs();
};
const previewDoc = (row: any) => {
  proxy?.$download?.oss?.(row.fileOssId);
};
const handleDelDoc = async (row: any) => {
  await ElMessageBox.confirm('确认删除该工程文件?', '提示', { type: 'warning' });
  await delDocs([row.docId]);
  await loadDocs();
};

const loadQuotes = async () => {
  const res: any = await listQuotes(info.value.subcontractId);
  quotes.value = res.data || [];
};
const openQuoteDialog = () => {
  quoteForm.value = {
    subcontractId: info.value.subcontractId,
    vendorName: null,
    unitPrice: null,
    toolingFee: 0,
    leadTimeDays: null,
    validUntil: null,
    quoteStatus: 'PENDING'
  };
  quoteOpen.value = true;
};
const submitQuote = async () => {
  await saveQuote(quoteForm.value);
  ElMessage.success('报价已保存');
  quoteOpen.value = false;
  await loadQuotes();
};
const handleSelectQuote = async (row: any) => {
  await ElMessageBox.confirm(`选定 ${row.vendorName} 并回写订单供应商/单价?`, '选定供应商', { type: 'warning' });
  await selectQuote(row.quoteId);
  ElMessage.success('已选定并回写订单');
  await loadQuotes();
};
const handleDelQuote = async (row: any) => {
  await ElMessageBox.confirm('确认删除该报价?', '提示', { type: 'warning' });
  await delQuotes([row.quoteId]);
  await loadQuotes();
};

const loadSuggest = async () => {
  const res: any = await suggestAllocations(info.value.subcontractId);
  suggest.value = res.data || [];
  if (activeTab.value !== 'issues') activeTab.value = 'issues';
  // 建议转发料单行: 有分配且无缺料的行自动成单
  const lines = suggest.value
    .filter((r: any) => r.remainingQty > 0 && r.shortage <= 0)
    .map((r: any) => ({
      bomLineId: r.bomLineId,
      materialId: r.materialId,
      materialCode: r.materialCode,
      materialName: r.materialName,
      materialSpec: r.materialSpec,
      baseUomName: r.baseUomName,
      issueQty: r.remainingQty,
      allocJson: JSON.stringify(
        r.allocations.map((a: any) => ({
          stockId: a.stockId,
          batchId: a.batchId,
          batchCode: a.batchCode,
          locationId: a.locationId,
          deductQty: a.deductQty
        }))
      )
    }));
  if (!lines.length) {
    ElMessage.warning('没有可发的自备料行(缺料或已发齐)');
    return;
  }
  await ElMessageBox.confirm(`将按备料建议创建发料单(${lines.length}行, 已手选批次), 确认?`, '创建发料单', { type: 'info' });
  await createIssue({ subcontractId: info.value.subcontractId, lines });
  ElMessage.success('发料单已创建, 请过账');
  suggest.value = [];
  await loadIssues();
};

const loadIssues = async () => {
  const res: any = await listIssues(info.value.subcontractId);
  issues.value = res.data || [];
};
const handlePostIssue = async (row: any) => {
  await ElMessageBox.confirm('过账将扣减库存并写流水, 全部自供行发齐后订单自动下达并生成 SN, 确认?', '发料过账', { type: 'warning' });
  await postIssue(row.issueId);
  ElMessage.success('过账成功');
  await loadIssues();
};
const handleDelIssue = async (row: any) => {
  await ElMessageBox.confirm('确认删除该发料单?', '提示', { type: 'warning' });
  await delIssue(row.issueId);
  await loadIssues();
};

const loadReceives = async () => {
  const res: any = await listReceives(info.value.subcontractId);
  receives.value = res.data || [];
};
const openReceiveDialog = () => {
  receiveForm.value = { subcontractId: info.value.subcontractId, receivedQty: null, vendorBatchCode: null, snScope: null };
  receiveOpen.value = true;
};
const submitReceive = async () => {
  await createReceive(receiveForm.value);
  ElMessage.success('收货单已创建, 请过账入库');
  receiveOpen.value = false;
  await loadReceives();
};
const handlePostReceive = async (row: any) => {
  await ElMessageBox.confirm('过账将自动建批次入半成品仓并累加订单实收量(达计划量自动完工), 确认?', '收货过账', { type: 'warning' });
  await postReceive(row.receiveId);
  ElMessage.success('过账成功');
  await loadReceives();
};
const handleDelReceive = async (row: any) => {
  await ElMessageBox.confirm('确认删除该收货单?', '提示', { type: 'warning' });
  await delReceive(row.receiveId);
  await loadReceives();
};

const loadSns = async () => {
  if (!info.value?.subcontractNo) return;
  try {
    const res: any = await listSubcontractSns(info.value.subcontractNo);
    sns.value = res.rows || res.data || [];
  } catch (e) {
    sns.value = [];
  }
};
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}
.mb12 {
  margin-bottom: 12px;
}
.mr4 {
  margin-right: 4px;
}
.tip {
  color: #94a3b8;
  font-size: 12px;
  margin-left: 8px;
}
.text-danger {
  color: #ef4444;
  font-weight: 600;
}
</style>
