<template>
  <div class="sn-selector">
    <SubmissionStateNotice :snapshot="row.submissionSnapshot" />
    <div class="mb-2">
      <el-tag v-if="row.policy">{{ policyCombination(row.policy) }}</el-tag>
      <el-tag v-if="row.policy" class="ml-2" type="info">{{ row.policy.stage }} / {{ row.policy.version || '旧版未声明' }}</el-tag>
      <span v-if="row.detailVersion" class="ml-2">详情版本：{{ row.detailVersion }}</span>
    </div>
    <el-alert v-if="policyError" :title="policyError" type="warning" :closable="false" />
    <template v-if="row.policy?.serialManaged || bindObject">
      <CodeResolver
        ref="resolverRef"
        :scene="scene"
        :context="context"
        :disabled="disabled || !row.materialId || policyLoading"
        @resolved="select"
        @state="setState"
      />
      <el-table :data="row.snItems || []" size="small" class="mt-2" border>
        <el-table-column prop="identityId" label="正式实物身份" min-width="180" />
        <el-table-column prop="snCode" label="正式 SN" min-width="150" />
        <el-table-column prop="batchCode" label="实物批次" min-width="130" />
        <el-table-column label="操作" width="80"
          ><template #default="{ $index }"
            ><el-button link type="danger" :disabled="disabled" @click="remove($index)">移除</el-button></template
          ></el-table-column
        >
      </el-table>
      <p>已选 {{ row.snItems?.length || 0 }} 件 / 单据数量 {{ row[quantityKey] ?? '未填写' }}。库存集合按来源行分配批次核对。</p>
    </template>
    <p v-else-if="row.policy?.batchManaged">按当前行批次入账：{{ row.batchCode || row.batchId || '请在业务行选择批次' }}。</p>
    <el-alert v-if="validation" :title="validation" type="warning" :closable="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getMaterial } from '@/api/erp/bd/material';
import type { CodeResolution } from '@/api/erp/wm/identity/types';
import { appendIdentity, bindResolvedObject, identityText, inventoryLineError, materialPolicy, policyCombination } from './model';
import type { TraceRow } from './model';
import CodeResolver from './CodeResolver.vue';
import SubmissionStateNotice from './SubmissionStateNotice.vue';

const props = withDefaults(
  defineProps<{ row: TraceRow; header: TraceRow; scene: string; quantityKey?: string; disabled?: boolean; bindObject?: boolean }>(),
  { quantityKey: 'quantity', disabled: false }
);
const emit = defineEmits<{ change: [row: TraceRow] }>();
const resolverRef = ref<InstanceType<typeof CodeResolver>>();
const policyLoading = ref(false);
const policyError = ref('');
let policyRequest = 0;
const context = computed(() => ({
  materialId: identityText(props.row.materialId),
  supplierId: identityText(props.header.supplierId ?? props.header.vendorId),
  customerId: identityText(props.header.customerId ?? props.header.clientId),
  sourceDocType: props.header.sourceDocType || props.scene,
  sourceDocId: identityText(
    props.header.receiveId ??
      props.header.transferId ??
      props.header.issueId ??
      props.header.returnId ??
      props.header.planId ??
      props.header.recptId ??
      props.header.sourceDocId
  ),
  lineId: identityText(props.row.lineId ?? props.row.detailId)
}));
const validation = computed(() => (props.row.materialId ? inventoryLineError(props.row, props.quantityKey) : ''));

/** 精确物料详情读取策略，旧请求返回不能污染新物料行。 */
watch(
  () => identityText(props.row.materialId),
  async (materialId, previousId) => {
    const request = ++policyRequest;
    policyError.value = '';
    if (previousId && previousId !== materialId) emit('change', { ...props.row, policy: undefined });
    if (!materialId || props.row.policy) return;
    policyLoading.value = true;
    try {
      const response = await getMaterial(materialId);
      if (request !== policyRequest) return;
      if (identityText(response.data.materialId) !== materialId) throw new Error('物料详情身份不一致');
      emit('change', { ...props.row, policy: materialPolicy(response.data), _traceError: '' });
    } catch (cause) {
      if (request !== policyRequest) return;
      policyError.value = cause instanceof Error ? cause.message : '追溯策略读取失败，请重新打开详情';
      emit('change', { ...props.row, _traceError: policyError.value });
    } finally {
      if (request === policyRequest) policyLoading.value = false;
    }
  },
  { immediate: true }
);

/** 歧义及处理中状态会阻断原单据保存或执行。 */
function setState(state: { pending: boolean; error: string }) {
  emit('change', { ...props.row, _traceResolving: state.pending, _traceError: state.error });
}

/** 每次扫描只加入一个明确的正式身份，不修改来源批次分配。 */
function select(resolution: CodeResolution) {
  try {
    const complete = { ...resolution, policy: resolution.policy || props.row.policy };
    const bound = props.bindObject ? bindResolvedObject(props.row, complete) : undefined;
    const items = resolution.objectType === 'SN' ? appendIdentity(props.row.snItems || [], complete, props.row) : props.row.snItems;
    if (!props.bindObject && resolution.objectType !== 'SN') throw new Error('请选择正式 SN 实物对象');
    emit('change', { ...(bound || props.row), snItems: items, _traceError: '' });
  } catch (cause) {
    emit('change', { ...props.row, _traceError: cause instanceof Error ? cause.message : '实物选择失败' });
  }
}

/** 删除仅改变当前待提交集合，不产生库存操作。 */
function remove(index: number) {
  emit('change', { ...props.row, snItems: (props.row.snItems || []).filter((_, position) => position !== index) });
}
/** OCR 与扫描使用同一个解析流程，识别对象整体送入服务端。 */
function resolve(value: string | object) {
  return resolverRef.value?.resolve(value);
}
defineExpose({ resolve });
</script>
