<template>
  <div class="app-container">
    <!-- 查询表单 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="批次编码" prop="batchCode">
        <el-input v-model="queryParams.batchCode" placeholder="请输入批次编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="序列号" prop="snCode">
        <el-input v-model="queryParams.snCode" placeholder="请输入序列号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="追溯方向" prop="direction">
        <el-select v-model="queryParams.direction" placeholder="请选择追溯方向">
          <el-option v-for="dict in wm_trace_direction" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery" v-hasPermi="['wm:trace:query']">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button :type="viewMode === 'tree' ? 'primary' : ''" plain @click="viewMode = 'tree'">树形视图</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button :type="viewMode === 'table' ? 'primary' : ''" plain @click="viewMode = 'table'">列表视图</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="handleQuery" />
    </el-row>

    <el-button class="mb-3" @click="router.push('/erp/wm/trace/repair')" v-hasPermi="['wm:trace:query']">审计与修复记录</el-button>
    <CodeResolver ref="codeResolver" scene="TRACE" @resolved="queryResolved" @state="handleResolveState" />
    <el-alert v-if="traceError" :title="traceError" type="warning" :closable="false" class="mt-3" />
    <el-descriptions v-if="selectedObject?.objectType === 'DOCUMENT'" :column="3" border class="mt-3">
      <el-descriptions-item label="单据身份">{{ selectedObject.objectId }}</el-descriptions-item>
      <el-descriptions-item label="单据类型">{{ selectedObject.documentType }}</el-descriptions-item>
      <el-descriptions-item label="单据编号">{{ selectedObject.documentCode }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="selectedObject && selectedObject.objectType !== 'DOCUMENT'" class="my-3">
      <el-button v-if="selectedObject.materialId" :loading="loading" @click="legacyQuery">查询已有仓储汇总</el-button>
      <el-button v-if="selectedObject.batchId && selectedObject.objectType !== 'BATCH'" @click="recallBatchId = selectedObject.batchId"
        >召回所属组件批次</el-button
      >
    </div>
    <IdentityTracePanel v-if="identityResult" :result="identityResult" />
    <el-button v-if="identityResult" class="my-2" @click="componentUsageId = identityResult.identityId">查询该组件身份去向及可能影响批次</el-button>
    <BatchRecallPanel v-if="componentUsageId" :component-identity-id="componentUsageId" @identity="openIdentity" />
    <BatchRecallPanel v-if="recallBatchId" :batch-id="recallBatchId" @identity="openIdentity" />
    <el-alert
      v-if="traceResult"
      title="以下为已有仓储查询结果，跨库范围和历史证据完整性以服务端声明为准。"
      type="info"
      :closable="false"
      class="mt-3"
    />
    <TraceEvidence v-if="traceResult" :result="traceResult" class="mt-3" />

    <!-- 追溯结果信息 -->
    <el-alert
      v-if="traceResult"
      :title="`追溯方向：${directionLabel} | 追踪关键字：${traceResult.traceKey} | 节点数量：${traceResult.nodes?.length || 0}`"
      type="info"
      :closable="false"
      class="mb8"
      show-icon
    />

    <!-- 树形视图 -->
    <div v-if="viewMode === 'tree' && traceResult && traceResult.tree">
      <el-tree :data="treeData" :props="treeProps" node-key="nodeId" default-expand-all :expand-on-click-node="false" @node-click="handleNodeClick">
        <template #default="{ data }">
          <span class="custom-tree-node">
            <span class="node-label">
              <el-tag size="small" type="info" class="mr4">{{ data.materialCode }}</el-tag>
              <span class="mr4">{{ data.materialName }}</span>
              <el-tag v-if="data.batchCode" size="small" type="warning" class="mr4">批次:{{ data.batchCode }}</el-tag>
              <el-tag v-if="data.snCode" size="small" type="success" class="mr4">SN:{{ data.snCode }}</el-tag>
              <span class="mr4 text-muted">×{{ data.quantity }}</span>
              <el-tag v-if="data.docType" size="small">{{ docTypeLabel(data.docType) }}</el-tag>
              <span v-if="data.docNo" class="mr4 text-muted">{{ data.docNo }}</span>
            </span>
          </span>
        </template>
      </el-tree>
    </div>

    <!-- 列表视图 -->
    <el-table v-if="viewMode === 'table' && traceResult" :data="traceResult.nodes" bordershow-overflow-tooltip>
      <el-table-column label="物料编码" align="center" prop="materialCode" width="120" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="批次编码" align="center" prop="batchCode" width="130" show-overflow-tooltip />
      <el-table-column label="序列号" align="center" prop="snCode" width="130" show-overflow-tooltip />
      <el-table-column label="数量" align="center" prop="quantity" width="100" />
      <el-table-column label="单据类型" align="center" prop="docType" width="120">
        <template #default="scope">
          <el-tag v-if="scope.row.docType" size="small">{{ docTypeLabel(scope.row.docType) }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="单据编号" align="center" prop="docNo" width="150" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" width="120" show-overflow-tooltip />
      <el-table-column label="事务时间" align="center" prop="transactionTime" width="180" />
      <el-table-column label="操作" align="center" width="80">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleNodeClick(scope.row)" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <el-empty v-if="!traceResult && !identityResult && !recallBatchId && !selectedObject && !loading" description="请输入查询条件进行追溯查询" />

    <!-- 节点详情弹窗 -->
    <el-dialog title="追溯节点详情" v-model="detailVisible" width="640px">
      <el-descriptions :column="2" border v-if="detailNode">
        <el-descriptions-item label="物料编码">{{ detailNode.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ detailNode.materialName }}</el-descriptions-item>
        <el-descriptions-item label="批次编码">{{ detailNode.batchCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ detailNode.snCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ detailNode.quantity }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detailNode.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ detailNode.docType ? docTypeLabel(detailNode.docType) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="单据编号">{{ detailNode.docNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单据ID">{{ detailNode.docId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事务时间">{{ detailNode.transactionTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="节点ID" :span="2">{{ detailNode.nodeId }}</el-descriptions-item>
        <el-descriptions-item label="父节点ID" :span="2">{{ detailNode.parentNodeId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WmTrace" lang="ts">
import { traceForward, traceBackward, traceIdentity } from '@/api/erp/wm/trace';
import CodeResolver from '@/components/material-trace/CodeResolver.vue';
import TraceEvidence from '@/components/material-trace/TraceEvidence.vue';
import { resolvedObject } from '@/components/material-trace/model';
import IdentityTracePanel from '@/components/material-trace/IdentityTracePanel.vue';
import BatchRecallPanel from '@/components/material-trace/BatchRecallPanel.vue';
import type { IdentityTraceResult } from '@/api/erp/wm/trace/types';
import type { ResolvedMaterialObject, CodeResolution } from '@/api/erp/wm/identity/types';
import { WmTraceResultVO, WmTraceNodeVO } from '@/api/erp/wm/trace/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_trace_direction } = toRefs<any>(proxy?.useDict('wm_trace_direction'));

const router = useRouter();
const codeResolver = ref<InstanceType<typeof CodeResolver>>();
const resolverState = ref({ pending: false, error: '' });
const selectedObject = ref<ResolvedMaterialObject>();
const identityResult = ref<IdentityTraceResult>();
const recallBatchId = ref('');
const componentUsageId = ref('');
const traceError = ref('');
let traceRequest = 0;
const loading = ref(false);
const showSearch = ref(true);
const viewMode = ref<'tree' | 'table'>('tree');
const traceResult = ref<WmTraceResultVO | null>(null);
const detailVisible = ref(false);
const detailNode = ref<WmTraceNodeVO | null>(null);

const queryRef = ref<ElFormInstance>();

const queryParams = ref({
  materialCode: '',
  batchCode: '',
  snCode: '',
  direction: 'FORWARD'
});

const treeProps = {
  children: 'children',
  label: 'materialName'
};

const treeData = computed(() => {
  if (!traceResult.value?.tree) return [];
  return [traceResult.value.tree];
});

const directionLabel = computed(() => {
  if (!traceResult.value) return '';
  return traceResult.value.direction === 'FORWARD' ? '正向追溯' : '反向追溯';
});

const docTypeMap: Record<string, string> = {
  'RECEIVE_IN': '采购入库',
  'SALES_OUT': '销售出库',
  'TRANSFER': '调拨',
  'COUNT_ADJUST': '盘点调整',
  'MISC_ISSUE': '杂项出库',
  'MISC_RECPT': '杂项入库',
  'PRODUCTION_ISSUE': '生产领料',
  'PRODUCTION_IN': '完工入库',
  'SUBCONTRACT_ISSUE': '委外发料',
  'SUBCONTRACT_RECEIVE': '委外收货',
  'REWORK_ISSUE': '返工出库',
  'REWORK_RECEIVE': '返工入库'
};

function docTypeLabel(docType: string): string {
  return docTypeMap[docType] || docType;
}

/** 料号、批次和 SN 作为同一个对象解析，歧义不自动查询。 */
const handleQuery = async () => {
  traceResult.value = null;
  await codeResolver.value?.resolve({
    materialCode: queryParams.value.materialCode,
    batchCode: queryParams.value.batchCode,
    snCode: queryParams.value.snCode
  });
};

/** 新扫描立即使旧查询失效；错误或歧义不会沿用上一对象。 */
function handleResolveState(state: { pending: boolean; error: string }) {
  resolverState.value = state;
  if (state.pending) clearResults();
}

/** 清理查询状态，不修改已扫描对象的身份字段。 */
function clearResults() {
  ++traceRequest;
  identityResult.value = undefined;
  recallBatchId.value = '';
  componentUsageId.value = '';
  selectedObject.value = undefined;
  traceResult.value = null;
  traceError.value = '';
  loading.value = false;
}

/** SN 走统一身份追溯，批次走组件召回，单据使用独立字段展示。 */
async function queryResolved(resolution: CodeResolution) {
  if (resolverState.value.pending || resolverState.value.error) return;
  try {
    const object = resolvedObject(resolution);
    selectedObject.value = object;
    if (object.objectType === 'DOCUMENT') return;
    if (object.objectType === 'SN' && object.identityId) await openIdentity(object.identityId);
    else if (object.objectType === 'BATCH' && object.batchId) recallBatchId.value = object.batchId;
    else await legacyQuery();
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : '追溯查询失败';
  }
}

/** 首次不指定模块，由服务端返回当前身份获准查看的 ERP/MES 部分。 */
async function openIdentity(identityId: string) {
  const request = ++traceRequest;
  loading.value = true;
  traceError.value = '';
  identityResult.value = undefined;
  try {
    const response = await traceIdentity(identityId);
    if (request !== traceRequest) return;
    if (response.data.identityId !== identityId) throw new Error('统一追溯返回了不同正式身份');
    identityResult.value = response.data;
  } catch (cause) {
    if (request === traceRequest) traceError.value = cause instanceof Error ? cause.message : '统一身份追溯不可用，可明确选择已有仓储汇总';
  } finally {
    if (request === traceRequest) loading.value = false;
  }
}

/** 已有查询作为显式汇总入口，不在统一接口错误时静默降低证据范围。 */
async function legacyQuery() {
  const object = selectedObject.value;
  if (!object || object.objectType === 'DOCUMENT' || !object.materialId) return;
  const request = ++traceRequest;
  loading.value = true;
  traceError.value = '';
  try {
    const params = {
      materialId: object.materialId,
      batchCode: object.batchCode,
      snCode: object.canonicalSnCode || object.snCode,
      identityId: object.identityId
    };
    const response = queryParams.value.direction === 'FORWARD' ? await traceForward(params) : await traceBackward(params);
    if (request === traceRequest) traceResult.value = response.data;
  } catch (cause) {
    if (request === traceRequest) traceError.value = cause instanceof Error ? cause.message : '已有仓储查询失败';
  } finally {
    if (request === traceRequest) loading.value = false;
  }
}

const resetQuery = () => {
  clearResults();
  codeResolver.value?.discard();
  queryRef.value?.resetFields();
  queryParams.value.direction = 'FORWARD';
  traceResult.value = null;
};

const handleNodeClick = (node: WmTraceNodeVO) => {
  detailNode.value = node;
  detailVisible.value = true;
};
</script>

<style scoped>
.custom-tree-node {
  display: flex;
  align-items: center;
  font-size: 14px;
  padding: 4px 0;
}
.node-label {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
.mr4 {
  margin-right: 4px;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
