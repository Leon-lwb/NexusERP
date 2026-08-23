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
    <el-empty v-if="!traceResult && !loading" description="请输入查询条件进行追溯查询" />

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
import { traceForward, traceBackward } from '@/api/erp/wm/trace';
import { listMaterial } from '@/api/erp/bd/material';
import { WmTraceResultVO, WmTraceNodeVO } from '@/api/erp/wm/trace/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_trace_direction } = toRefs<any>(proxy?.useDict('wm_trace_direction'));

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

/** 执行追溯查询 */
const handleQuery = async () => {
  if (!queryParams.value.materialCode) {
    proxy?.$modal.msgWarning('请输入物料编码');
    return;
  }
  loading.value = true;
  try {
    // 后端追溯接口需要 materialId（Long），先按物料编码查询物料再追溯，不能把编码当 ID 传
    const materialRes: any = await listMaterial({ materialCode: queryParams.value.materialCode, pageNum: 1, pageSize: 1 } as any);
    const materialRow = materialRes?.rows?.[0] || materialRes?.data?.[0];
    if (!materialRow?.materialId) {
      proxy?.$modal.msgWarning('未找到该物料编码对应的物料');
      loading.value = false;
      return;
    }
    const params: any = {
      materialId: materialRow.materialId
    };
    if (queryParams.value.batchCode) {
      params.batchCode = queryParams.value.batchCode;
    }
    if (queryParams.value.snCode) {
      params.snCode = queryParams.value.snCode;
    }

    if (queryParams.value.direction === 'FORWARD') {
      const res = await traceForward(params);
      traceResult.value = res.data;
    } else {
      const res = await traceBackward(params);
      traceResult.value = res.data;
    }
  } catch (e) {
    console.error('追溯查询失败', e);
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
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
