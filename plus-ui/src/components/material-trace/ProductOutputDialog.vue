<template>
  <el-dialog
    v-model="visible"
    title="登记实际产品批次后完工"
    width="860px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
    <p>实际良品 {{ expected.goodQty }}；实际报废 {{ expected.scrapQty }}。每个产品批次按整批原数量登记，来源由服务端提供。</p>
    <p v-if="context?.outputs.length">已登记 {{ context.outputs.length }} 个实际产出；这里仅补本次缺少数量。</p>
    <el-table :data="lines" border>
      <el-table-column label="实际产品批次" min-width="300">
        <template #default="{ row }"
          ><el-select v-model="row.sourceKey" filterable placeholder="选择实际产出批次" :disabled="locked" style="width: 100%">
            <el-option
              v-for="source in available"
              :key="keyOf(source)"
              :value="keyOf(source)"
              :label="`${source.lotCode} · ${source.quantity} · ${source.status}/${source.qcStatus}`"
            /> </el-select
        ></template>
      </el-table-column>
      <el-table-column label="判定" width="150"
        ><template #default="{ row }"
          ><el-select v-model="row.result" :disabled="locked"
            ><el-option label="良品" value="GOOD" :disabled="!context?.isFinalStep" /><el-option label="报废" value="SCRAP" /></el-select></template
      ></el-table-column>
      <el-table-column label="整批数量" width="140"
        ><template #default="{ row }">{{ sourceOf(row.sourceKey)?.quantity || '未选择' }}</template></el-table-column
      >
      <el-table-column label="操作" width="80"
        ><template #default="{ $index }"
          ><el-button link type="danger" :disabled="locked" @click="lines.splice($index, 1)">移除</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-button class="mt-2" :disabled="locked || !context" @click="lines.push({ sourceKey: '', result: 'GOOD' })">增加实际批次</el-button>
    <p>可以选择多个实际成品批次，每个批次须与原生产分配对应。登记后继续原完工流程，并保留最终一次入库。</p>
    <template #footer
      ><el-button :disabled="busy" @click="cancel">关闭</el-button
      ><el-button type="primary" :loading="busy" :disabled="!context" @click="submit">{{
        attempt?.saved ? '产出已保存，重试原完工' : locked ? '重试原产出命令并完工' : '保存实际产出并完工'
      }}</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue';
import { getFlowCard } from '@/api/mes/pro/flowcardApi';
import { getMaterial } from '@/api/erp/bd/material';
import { recordProductOutputs } from '@/api/mes/pro/completion';
import type { ProductOutputContext, ProductLotSource } from '@/api/mes/pro/completion';
import {
  createCommandSession,
  createProductOutputAttempt,
  decimalCompare,
  decimalSum,
  identityText,
  materialPolicy,
  productOutputRequirement,
  remainingProductOutputs,
  validateProductOutputs
} from './model';
const visible = ref(false),
  busy = ref(false),
  error = ref(''),
  locked = ref(false);
const context = ref<ProductOutputContext>();
const expected = ref({ goodQty: '0', scrapQty: '0' });
const lines = ref<Array<{ sourceKey: string; result: 'GOOD' | 'SCRAP' }>>([]);
const attempt = shallowRef<ReturnType<typeof createProductOutputAttempt>>();
let finish: () => Promise<unknown>;
let attemptKey = '';
const pendingAttempts = new Map<
  string,
  {
    attempt: ReturnType<typeof createProductOutputAttempt>;
    context: ProductOutputContext;
    expected: { goodQty: string; scrapQty: string };
    lines: Array<{ sourceKey: string; result: 'GOOD' | 'SCRAP' }>;
  }
>();
let resolveResult: ((result: unknown) => void) | undefined, rejectResult: ((error: Error) => void) | undefined;
const keyOf = (source: ProductLotSource) => `${source.lotId}/${source.sourceAllocationId}`;
const available = computed(
  () => context.value?.sources.filter((source) => !context.value!.outputs.some((output) => output.lotId === source.lotId)) || []
);
const sourceOf = (key: string) => available.value.find((source) => keyOf(source) === key);
function cancel() {
  visible.value = false;
  if (rejectResult) {
    rejectResult(new Error('cancel'));
    resolveResult = undefined;
    rejectResult = undefined;
  }
}
/** 读取既有流转卡的真实产出上下文；缺读取能力时明确阻断，不能伪造技术来源。 */
async function run<T>(
  options: { cardId: string | number; stepRecordId: string | number; goodQty: string; scrapQty: string; incremental?: boolean; sourceKey?: string },
  complete: () => Promise<T>
): Promise<T> {
  if (visible.value) throw new Error('请先完成或关闭当前实际产出登记');
  decimalSum([options.goodQty, options.scrapQty]);
  const card = (await getFlowCard(identityText(options.cardId)!)).data;
  const step = card.steps?.find((row) => identityText(row.stepRecordId) === identityText(options.stepRecordId));
  if (!step) throw new Error('当前工序不属于原流转卡，请重新读取详情');
  const policy = step.productOutputContext?.policy || materialPolicy((await getMaterial(identityText(card.materialId)!)).data);
  if (!policy.batchManaged || policy.serialManaged) return complete();
  const current = step.productOutputContext;
  const executable = card.steps?.filter((row) => row.stepStatus !== 'SKIPPED') || [];
  const finalStep = identityText(executable.at(-1)?.stepRecordId) === identityText(step.stepRecordId);
  const required = productOutputRequirement(policy, current?.isFinalStep ?? finalStep, options.goodQty, options.scrapQty);
  if (!required.required) return complete();
  error.value = '';
  locked.value = false;
  attempt.value = undefined;
  lines.value = [];
  context.value = undefined;
  expected.value = required;
  finish = complete;
  visible.value = true;
  const pending = new Promise<T>((resolve, reject) => {
    resolveResult = resolve as (result: unknown) => void;
    rejectResult = reject;
  });
  attemptKey = JSON.stringify({ ...options, cardId: identityText(options.cardId), stepRecordId: identityText(options.stepRecordId) });
  const previous = pendingAttempts.get(attemptKey);
  if (previous) {
    attempt.value = previous.attempt;
    context.value = previous.context;
    expected.value = previous.expected;
    lines.value = previous.lines.map((line) => ({ ...line }));
    locked.value = true;
    return pending;
  }
  if (!current) {
    error.value = '服务端当前流转卡详情尚未提供实际产品批次及原生产分配来源，暂时无法登记；原完工未执行。';
    return pending;
  }
  if (
    identityText(current.cardId) !== identityText(card.cardId) ||
    identityText(current.stepRecordId) !== identityText(step.stepRecordId) ||
    identityText(current.materialId) !== identityText(card.materialId) ||
    identityText(current.mesWoId) !== identityText(card.mesWoId) ||
    current.hasMore
  ) {
    error.value = '实际产出上下文与当前工序不一致或来源被截断，请读取完整服务端详情；原完工未执行。';
    return pending;
  }
  try {
    expected.value = options.incremental ? required : remainingProductOutputs(current, required);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '实际产出与原报工不一致';
    return pending;
  }
  if (decimalCompare(decimalSum([expected.value.goodQty, expected.value.scrapQty]), '0') === 0) {
    visible.value = false;
    resolveResult = undefined;
    rejectResult = undefined;
    return complete();
  }
  context.value = current;
  lines.value = [{ sourceKey: '', result: decimalCompare(expected.value.goodQty, '0') > 0 ? 'GOOD' : 'SCRAP' }];
  return pending;
}
async function submit() {
  if (!context.value || busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (!attempt.value) {
      const actual = lines.value.map((row) => {
        const source = sourceOf(row.sourceKey);
        return {
          lotId: source?.lotId || '',
          sourceAllocationId: source?.sourceAllocationId || '',
          quantity: source?.quantity || '',
          result: row.result
        };
      });
      validateProductOutputs(actual, context.value, expected.value);
      const payload = { cardId: context.value.cardId, stepRecordId: context.value.stepRecordId, lines: actual };
      attempt.value = createProductOutputAttempt({ commandId: createCommandSession().forPayload(payload), ...payload }, recordProductOutputs);
      locked.value = true;
      pendingAttempts.set(attemptKey, {
        attempt: attempt.value,
        context: context.value,
        expected: { ...expected.value },
        lines: lines.value.map((line) => ({ ...line }))
      });
    }
    const result = await attempt.value.submit(finish);
    pendingAttempts.delete(attemptKey);
    const resolve = resolveResult;
    resolveResult = undefined;
    rejectResult = undefined;
    visible.value = false;
    resolve?.(result);
  } catch (cause) {
    error.value =
      (attempt.value?.saved ? '实际产出已保存，原完工尚未成功；重试只执行原完工。' : '') +
      (cause instanceof Error ? cause.message : '实际产出登记失败，请重试原命令');
  } finally {
    busy.value = false;
  }
}
defineExpose({ run });
</script>
