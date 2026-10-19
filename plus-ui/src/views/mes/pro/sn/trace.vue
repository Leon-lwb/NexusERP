<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>SN追溯 - {{ sn.snCode }}</span>
          <div style="float: right">
            <el-button type="primary" plain @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="3" border v-loading="loading">
        <el-descriptions-item label="SN编码">{{ sn.snCode }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ sn.materialCode }} {{ sn.materialName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="mes_sn_status" :value="sn.snStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ sn.woNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流转卡编号">{{ sn.cardNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="父SN编码">{{ sn.parentSnCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sn.source === 'BATCH' ? '批量' : '手动' }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ sn.productionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ sn.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <TraceEvidence :result="traceEvidence" class="mt-3" />
    <el-row :gutter="12" class="mt-2">
      <!-- 前向追溯: SN → 工序链 → 子SN -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <span>前向追溯（SN → 工序 → 操作员 / 子SN）</span>
          </template>
          <el-tree :data="forwardTree" :props="treeProps" default-expand-all :expand-on-click-node="true">
            <template #default="{ node, data }">
              <span>
                <span v-if="data.nodeType === 'sn'">{{ node.label }} <dict-tag :options="mes_sn_status" :value="data.snStatus" size="small" /></span>
                <el-tag v-else-if="data.nodeType === 'step'" type="primary" size="small">{{ node.label }}</el-tag>
                <el-tag v-else-if="data.nodeType === 'childGroup'" type="warning" size="small">{{ node.label }}</el-tag>
                <span v-else>{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 后向追溯: 父SN链 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <span>后向追溯（父SN组装链）</span>
          </template>
          <el-empty v-if="parentChain.length === 0" description="当前响应未提供父 SN 关系" />
          <el-tree v-else :data="backwardTree" :props="treeProps" default-expand-all>
            <template #default="{ node, data }">
              <span>
                <span>{{ node.label }} <dict-tag :options="mes_sn_status" :value="data.snStatus" size="small" /></span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="MesSnTrace">
import TraceEvidence from '@/components/material-trace/TraceEvidence.vue';
import { traceSn } from '@/api/mes/pro/sn';
import type { ProSnVO, SnTraceResult, SnTraceStep } from '@/api/mes/pro/sn';

const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_sn_status } = toRefs<any>(proxy?.useDict('mes_sn_status'));

const traceEvidence = ref<Record<string, any>>({});
const loading = ref(true);
const sn = ref<ProSnVO>({} as ProSnVO);
const forwardSteps = ref<SnTraceStep[]>([]);
const children = ref<ProSnVO[]>([]);
const parentChain = ref<ProSnVO[]>([]);

const treeProps = { children: 'children', label: 'label' };

const loadTrace = async () => {
  loading.value = true;
  try {
    const snId = route.params.snId as string;
    const res = await traceSn(snId);
    const data = res.data as SnTraceResult;
    traceEvidence.value = data;
    sn.value = data.sn;
    forwardSteps.value = data.forwardSteps || [];
    children.value = data.children || [];
    parentChain.value = data.parentChain || [];
  } finally {
    loading.value = false;
  }
};

// 前向追溯树: 根SN -> [工序链节点组] + [子SN组]
const forwardTree = computed(() => {
  const root: any = {
    label: sn.value.snCode,
    nodeType: 'sn',
    snStatus: sn.value.snStatus,
    children: [] as any[]
  };
  // 工序链
  if (forwardSteps.value.length > 0) {
    root.children.push({
      label: '工序链',
      nodeType: 'stepGroup',
      children: forwardSteps.value.map((step) => ({
        label: `工序${step.sequenceNum}: ${step.processName || step.processCode} — ${step.operatorName || '未指派'} (良品${step.goodQty ?? 0})`,
        nodeType: 'step',
        snStatus: undefined
      }))
    });
  }
  // 子SN
  if (children.value.length > 0) {
    root.children.push({
      label: '子SN(' + children.value.length + ')',
      nodeType: 'childGroup',
      children: children.value.map((c) => ({
        label: c.snCode,
        nodeType: 'sn',
        snStatus: c.snStatus
      }))
    });
  }
  return [root];
});

// 后向追溯树: 根SN -> 父SN链(从最近父到顶层)
const backwardTree = computed(() => {
  const root: any = {
    label: sn.value.snCode,
    nodeType: 'sn',
    snStatus: sn.value.snStatus,
    children: [] as any[]
  };
  let current = root;
  for (const p of parentChain.value) {
    const node = {
      label: p.snCode,
      nodeType: 'sn',
      snStatus: p.snStatus,
      children: [] as any[]
    };
    current.children = [node];
    current = node;
  }
  return [root];
});

const goBack = () => {
  router.push('/mes/pro/sn');
};

onMounted(() => {
  loadTrace();
});
</script>
