<template>
  <el-dialog
    v-model="visible"
    title="核对实际完工明细并执行最终入库"
    width="950px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
  >
    <el-alert
      title="本入口执行原生产入库单。批次产品按MES已登记的实际GOOD产出与最终完工清单核验，不按BOM、计划量或组件允许混批开关代造产出；每张工单保留最终一次入库。"
      type="info"
      :closable="false"
      class="mb-3"
    />
    <el-descriptions v-if="detail" :column="2" border
      ><el-descriptions-item label="原生产入库单">{{ detail.recptCode }}</el-descriptions-item
      ><el-descriptions-item label="原工单">{{ detail.woNo }} / {{ detail.woId }}</el-descriptions-item
      ><el-descriptions-item label="物料">{{ detail.materialCode }} {{ detail.materialName }}</el-descriptions-item
      ><el-descriptions-item label="本次入库数量">{{ detail.quantity }}</el-descriptions-item
      ><el-descriptions-item label="仓库/库位"
        >{{ detail.warehouseName || detail.warehouseId }} / {{ detail.locationName || detail.locationId }}</el-descriptions-item
      ><el-descriptions-item label="最新单据状态">{{ liveStatus }}</el-descriptions-item></el-descriptions
    >
    <p v-if="detail?.policy?.batchManaged && !detail.policy.serialManaged">
      产品按批次管理：原MES实际产出先登记GOOD/SCRAP，最终入库只处理清单中可交付良品。不同成品批次不会被组件混批开关拒绝。
    </p>
    <MaterialTraceLines
      v-if="detail?.policy?.serialManaged"
      :rows="[detail]"
      :header="detail"
      scene="PRODUCTION_RECEIPT"
      quantity-key="quantity"
      :disabled="Boolean(original) || finished || loading"
    />
    <SubmissionStateNotice v-if="submission" :snapshot="submission" class="mt-3" />
    <el-alert
      v-if="finished && !submission"
      title="原入库请求已返回成功，跨模块确认状态未提供；请以原单据和后续凭据为准。"
      type="info"
      :closable="false"
      class="mt-3"
    />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mt-3" />
    <p v-if="original && !finished">当前保留原提交快照。网络响应丢失时，重试使用原命令、原版本及原实物集合核验，不重取当前库存替代原结果。</p>
    <template #footer
      ><el-button :disabled="busy" @click="visible = false">关闭</el-button
      ><el-button :disabled="busy || loading || !detail" @click="refresh">刷新原单状态</el-button
      ><el-button type="primary" :loading="busy" :disabled="loading || !detail || (!original && liveStatus !== 'DRAFT')" @click="submit">{{
        original ? '重放原入库命令' : '按原完工明细执行入库'
      }}</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, shallowRef, onBeforeUnmount } from 'vue';
import { getProductionRecpt, executeProductionRecpt } from '@/api/erp/wm/productionrecpt';
import { getMaterial } from '@/api/erp/bd/material';
import type { SubmissionMetadata } from '@/api/erp/wm/identity/types';
import type { TraceRow } from './model';
import { createCommandSession, decimalCompare, identityText, materialPolicy, postingBody } from './model';
import MaterialTraceLines from './MaterialTraceLines.vue';
import SubmissionStateNotice from './SubmissionStateNotice.vue';
const emit = defineEmits<{ executed: [response: unknown] }>();
const visible = ref(false),
  loading = ref(false),
  busy = ref(false),
  finished = ref(false),
  error = ref(''),
  liveStatus = ref('');
const detail = ref<TraceRow>(),
  original = shallowRef<TraceRow>(),
  submission = ref<SubmissionMetadata>();
let generation = 0;
/** 进入实际原单时重读详情；未取得服务端原版本不能从列表代造提交快照。 */
async function open(id: string | number) {
  const key = identityText(id);
  if (!key) throw new Error('原入库单身份缺失');
  if (original.value && !finished.value && identityText(original.value.recptId) === key) {
    visible.value = true;
    return;
  }
  const request = ++generation;
  visible.value = true;
  loading.value = true;
  finished.value = false;
  error.value = '';
  detail.value = undefined;
  original.value = undefined;
  submission.value = undefined;
  try {
    const response = await getProductionRecpt(key);
    if (request !== generation) return;
    if (identityText(response.data.recptId) !== key) throw new Error('原入库详情身份不一致');
    const value = response.data as TraceRow;
    if (!value.policy) value.policy = materialPolicy((await getMaterial(identityText(value.materialId)!)).data);
    if (request !== generation) return;
    detail.value = value;
    liveStatus.value = value.status;
    submission.value = value.submissionSnapshot;
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '读取原入库单失败';
  } finally {
    if (request === generation) loading.value = false;
  }
}
/** 刷新只更新可见状态与原组确认信息，不能覆盖已尝试提交的原请求。 */
async function refresh() {
  if (!detail.value) return;
  const request = generation;
  loading.value = true;
  error.value = '';
  try {
    const response = await getProductionRecpt(identityText(detail.value.recptId)!);
    if (request !== generation) return;
    if (identityText(response.data.recptId) !== identityText(detail.value.recptId)) throw new Error('入库状态响应身份不一致');
    liveStatus.value = response.data.status;
    const snapshot = (response.data as TraceRow).submissionSnapshot;
    if (snapshot) submission.value = snapshot;
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '读取原入库状态失败';
  } finally {
    if (request === generation) loading.value = false;
  }
}
/** 首次冻结客户端原载荷，后续即使单据或库存变化也只重放同一原命令。 */
async function submit() {
  if (!detail.value || busy.value || loading.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (!original.value) {
      if (liveStatus.value !== 'DRAFT') throw new Error('当前原单状态不允许新的入库提交');
      if (decimalCompare(String(detail.value.quantity), '0') <= 0) throw new Error('最终入库只处理正数良品数量，全报废不创建入库效果');
      postingBody(detail.value, [detail.value], 'quantity', 'preflight');
      const value = JSON.parse(JSON.stringify(detail.value)) as TraceRow;
      value._traceCommandId = createCommandSession().forPayload({ action: 'PRODUCTION_RECEIPT', header: value });
      original.value = value;
    }
    const response = await executeProductionRecpt(identityText(original.value.recptId)!, original.value);
    finished.value = true;
    if (response.data?.submissionState) submission.value = response.data;
    emit('executed', response);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '入库请求结果未确认，请重试原命令';
  } finally {
    busy.value = false;
  }
}
onBeforeUnmount(() => {
  generation++;
});
defineExpose({ open });
</script>
