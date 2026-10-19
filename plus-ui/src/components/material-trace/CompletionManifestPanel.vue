<template>
  <el-card class="mt-2">
    <ComponentCorrectionDialog ref="correctionRef" @corrected="corrected" />
    <template #header><span>完工清单与取消屏障</span><el-button class="ml-2" :loading="loading" @click="refresh">刷新服务端状态</el-button></template>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
    <el-alert v-if="!state" title="当前工单详情未提供完工清单状态，无法核对原修订或取消确认；请补齐服务端读取能力。" type="info" :closable="false" />
    <p v-else-if="state.latest === null">服务端明确返回：该工单尚无完工清单。</p>
    <template v-else-if="manifest">
      <el-descriptions border :column="3"
        ><el-descriptions-item label="当前修订">{{ manifest.revision }}</el-descriptions-item
        ><el-descriptions-item label="状态"
          ><el-tag>{{ manifest.status }}</el-tag></el-descriptions-item
        ><el-descriptions-item label="良品/报废">{{ manifest.goodQty }} / {{ manifest.scrapQty }}</el-descriptions-item></el-descriptions
      >
      <p v-if="state?.cancellation">取消处理：{{ state.cancellation.status }} {{ state.cancellation.reason }}</p>
      <p v-if="manifest.status === 'CANCEL_REQUESTED'">已申请取消，等待ERP取消屏障确认；当前不能重新冻结。</p>
      <p v-if="manifest.status === 'CANCELLED'">
        服务端已确认取消本修订，可按审阅后的当前业务事实重新冻结。报工数量和最终一次入库仍由原业务流程处理。
      </p>
      <el-input
        v-model="reason"
        type="textarea"
        :rows="2"
        :maxlength="500"
        show-word-limit
        placeholder="填写取消或重新冻结原因"
        :disabled="busy || Boolean(locked)"
      />
      <div class="mt-2">
        <el-button v-hasPermi="['mes:pro:work:cancel']" type="warning" :disabled="!canCancel || busy" @click="submit('cancel')">{{
          locked === 'cancel' ? '重试原取消请求' : '申请取消当前修订'
        }}</el-button
        ><el-button v-hasPermi="['mes:pro:work:edit']" type="primary" :disabled="!canRefreeze || busy" @click="submit('refreeze')">{{
          locked === 'refreeze' ? '重试原重新冻结请求' : '重新冻结已取消修订'
        }}</el-button>
      </div>
      <span v-hasPermi="['mes:lt:consumption:add']"
        ><span v-hasPermi="['mes:lt:consumption:remove']"
          ><el-button v-hasPermi="['mes:pro:work:edit']" class="mt-2" :disabled="!canRefreeze || busy" @click="correctionRef?.open(manifest)"
            >修订单件组件消耗</el-button
          ></span
        ></span
      >
      <p v-if="notice">{{ notice }}</p>
    </template>
    <el-table v-if="state?.revisions?.length" :data="state.revisions" border class="mt-2"
      ><el-table-column prop="revision" label="历史修订" /><el-table-column prop="status" label="实际状态" /><el-table-column
        prop="goodQty"
        label="良品" /><el-table-column prop="scrapQty" label="报废"
    /></el-table>
    <el-alert
      v-if="state?.hasMore || state?.truncated"
      title="历史修订已截断；这里仅展示服务端返回范围，不能视为全部历史。"
      type="warning"
      :closable="false"
    />
  </el-card>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import ComponentCorrectionDialog from './ComponentCorrectionDialog.vue';
const correctionRef = ref<InstanceType<typeof ComponentCorrectionDialog>>();
import { getWorkOrder } from '@/api/mes/pro/workorder';
import { requestCompletionCancellation, refreezeCompletionManifest } from '@/api/mes/pro/completion';
import type { CompletionState } from '@/api/mes/pro/completion';
import { createCommandSession, identityText } from './model';
const props = defineProps<{ mesWoId: string | number; initial?: CompletionState }>();
const emit = defineEmits<{ changed: [] }>();
const state = ref<CompletionState>();
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  reason = ref(''),
  notice = ref('');
const locked = ref<'' | 'cancel' | 'refreeze'>('');
let original: { commandId: string; manifestId: string; woId: string; revision: string | number; reason: string } | undefined;
const manifest = computed(() => state.value?.latest);
const canCancel = computed(() =>
  Boolean(manifest.value && ['FROZEN', 'APPLIED', 'RECEIVED'].includes(manifest.value.status) && (!locked.value || locked.value === 'cancel'))
);
const canRefreeze = computed(() => Boolean(manifest.value?.status === 'CANCELLED' && (!locked.value || locked.value === 'refreeze')));
let epoch = 0;
async function corrected() {
  await refresh();
  emit('changed');
}
async function refresh() {
  const id = identityText(props.mesWoId);
  if (!id) return;
  const request = ++epoch;
  loading.value = true;
  try {
    const response = await getWorkOrder(id);
    if (request !== epoch) return;
    state.value = response.data.completionState;
    if (
      state.value &&
      (!Object.prototype.hasOwnProperty.call(state.value, 'latest') || (state.value.latest && identityText(state.value.latest.mesWoId) !== id))
    )
      throw new Error('完工清单状态缺少明确当前修订或工单不匹配');
    if (
      original &&
      manifest.value &&
      ((locked.value === 'cancel' && manifest.value.status === 'CANCELLED') ||
        (locked.value === 'refreeze' && String(manifest.value.revision) !== String(original.revision)))
    ) {
      locked.value = '';
      original = undefined;
      reason.value = '';
    }
  } catch (cause) {
    if (request === epoch) {
      state.value = undefined;
      error.value = cause instanceof Error ? cause.message : '读取完工清单失败';
    }
  } finally {
    if (request === epoch) loading.value = false;
  }
}
/** 取消只发请求；只有读到原修订CANCELLED才能开放refreeze。网络重试沿用原命令和原因。 */
async function submit(action: 'cancel' | 'refreeze') {
  if (busy.value || (action === 'cancel' && !canCancel.value) || (action === 'refreeze' && !canRefreeze.value)) return;
  busy.value = true;
  error.value = '';
  try {
    if (!original) {
      if (!reason.value.trim() || reason.value.length > 500) throw new Error('原因必填且不能超过500字');
      const current = manifest.value!;
      const data = { manifestId: current.manifestId, woId: identityText(props.mesWoId)!, revision: current.revision, reason: reason.value };
      original = { ...data, commandId: createCommandSession().forPayload({ action, ...data }) };
      locked.value = action;
    }
    const data = original;
    if (action === 'cancel') {
      await requestCompletionCancellation(data.manifestId, { commandId: data.commandId, revision: data.revision, reason: data.reason });
      notice.value = '取消申请已提交，等待跨模块取消确认；请刷新状态。';
    } else {
      await refreezeCompletionManifest(data.woId, { commandId: data.commandId, cancelledRevision: data.revision, reason: data.reason });
      notice.value = '重新冻结请求已提交，请以刷新后的当前修订为准。';
    }
    await refresh();
    emit('changed');
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '完工修订请求失败，请重试原命令';
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.mesWoId,
  () => {
    epoch++;
    state.value = props.initial;
    locked.value = '';
    original = undefined;
    reason.value = '';
    error.value = '';
    notice.value = '';
    void refresh();
  },
  { immediate: true }
);
</script>
