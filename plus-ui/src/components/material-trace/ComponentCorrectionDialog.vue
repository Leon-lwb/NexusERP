<template>
  <el-dialog
    v-model="visible"
    title="修订单件组件实际消耗"
    width="980px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="!busy"
  >
    <el-alert
      title="仅对ERP已确认取消的原修订追加冲销与新分配。此操作不改旧分录，也不直接变更库存；后续需按实际事实重新冻结。"
      type="warning"
      :closable="false"
      class="mb-3"
    />
    <p>原取消修订：{{ manifest?.revision }}；MES工单：{{ manifest?.mesWoId }}</p>
    <el-form :disabled="busy || Boolean(original)" label-width="110px">
      <el-form-item label="产品本地SN"
        ><el-select v-model="selectedSnId" placeholder="选择该工单实际产品SN" filterable @change="loadSn" style="width: 100%"
          ><el-option
            v-for="row in snRows"
            :key="String(row.snId)"
            :value="identityText(row.snId)"
            :label="`${row.localSnCode || row.snCode} · MES本地SN · ${row.snStatus}`" /></el-select
      ></el-form-item>
      <div class="mb-3">
        <el-button :disabled="snPage === 1 || loading || Boolean(original)" @click="loadSnPage(snPage - 1)">产品SN上一页</el-button
        ><el-button :disabled="!snMore || loading || Boolean(original)" @click="loadSnPage(snPage + 1)">产品SN下一页</el-button
        ><span class="ml-2">第 {{ snPage }} 页，共 {{ snTotal }} 条</span>
      </div>
      <p v-if="context">
        正式身份：{{ context.sn.identityId }}；原MES单件版本：{{ context.sn.identityVersion }}；MES本地原码：{{
          trace?.sn.localSnCode || trace?.sn.snCode
        }}
      </p>
      <el-form-item label="组件物料"
        ><el-select v-model="materialId" placeholder="选择实际消耗的组件" filterable @change="changeComponent" style="width: 100%"
          ><el-option v-for="row in materials" :key="row.id" :value="row.id" :label="row.label" /></el-select
      ></el-form-item>
      <el-alert v-if="policyNotice" :title="policyNotice" type="info" :closable="false" class="mb-2" />
      <el-table :data="reversals" border
        ><el-table-column label="原组件批次" min-width="190"
          ><template #default="{ row }">{{ lotLabel(row.lotId) }}</template></el-table-column
        ><el-table-column prop="remainingQuantity" label="扣除历史冲销后的净额" min-width="190" /><el-table-column
          label="本次冲销数量"
          min-width="180"
          ><template #default="{ row }"
            ><el-input v-model="row.quantity" :placeholder="`冲销 ${row.entryId}`" inputmode="decimal" /></template></el-table-column
      ></el-table>
      <p class="mt-3">追加真实来源分配（数量上限为工单实际消耗，其他单件已占额度由服务端原子校验）</p>
      <el-table :data="allocations" border>
        <el-table-column label="原工单消耗来源" min-width="260"
          ><template #default="{ row }"
            ><el-select v-model="row.consumptionId" placeholder="选择实际消耗批次" style="width: 100%"
              ><el-option
                v-for="source in componentSources"
                :key="String(source.consumptionId)"
                :value="identityText(source.consumptionId)"
                :label="`${source.lotCode || source.lotId} · 实际消耗 ${source.consumedQty}`" /></el-select></template
        ></el-table-column>
        <el-table-column label="实际完成工序" min-width="220"
          ><template #default="{ row }"
            ><el-select v-model="row.stepRecordId" placeholder="选择该产品已完成工序"
              ><el-option
                v-for="step in completedSteps"
                :key="String(step.stepRecordId)"
                :value="identityText(step.stepRecordId)"
                :label="step.processName || step.processCode || `工序 ${step.stepRecordId}`" /></el-select></template
        ></el-table-column>
        <el-table-column label="新分配数量" width="170"
          ><template #default="{ row }"><el-input v-model="row.quantity" placeholder="新分配精确数量" inputmode="decimal" /></template
        ></el-table-column>
        <el-table-column label="操作" width="75"
          ><template #default="{ $index }"
            ><el-button link type="danger" @click="allocations.splice($index, 1)">移除</el-button></template
          ></el-table-column
        >
      </el-table>
      <el-button class="mt-2" :disabled="!materialId" @click="allocations.push({ consumptionId: '', stepRecordId: '', quantity: '' })"
        >增加来源分配</el-button
      >
      <el-form-item label="修订原因" required class="mt-3"
        ><el-input v-model="reason" type="textarea" :rows="2" :maxlength="500" show-word-limit placeholder="填写实际消耗修订原因"
      /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mt-2" />
    <el-alert
      v-if="result"
      :title="`MES修订已保存，原单件版本推进为 ${result.version}；请刷新原清单并按真实事实重新冻结。`"
      type="success"
      :closable="false"
      class="mt-2"
    />
    <template #footer
      ><el-button :disabled="busy" @click="visible = false">关闭</el-button
      ><el-button type="primary" :disabled="!context || loading" :loading="busy" @click="submit">{{
        original ? '重试原修订命令' : '提交原修订'
      }}</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef, onBeforeUnmount } from 'vue';
import { listSn, traceSn } from '@/api/mes/pro/sn';
import type { ProSnVO, SnTraceResult } from '@/api/mes/pro/sn';
import { listConsumptionByWo } from '@/api/mes/lt/consumption';
import type { LtWoConsumptionVO } from '@/api/mes/lt/consumption';
import { getMaterial } from '@/api/erp/bd/material';
import { correctSnComponent } from '@/api/mes/pro/completion';
import type { CompletionManifestHeader, ComponentCorrectionRequest, ComponentCorrectionResult } from '@/api/mes/pro/completion';
import {
  buildComponentCorrection,
  createCommandSession,
  decimalCompare,
  identityText,
  integerQuantity,
  materialPolicy,
  netComponentEntries
} from './model';
import type { ComponentCorrectionContext } from './model';
const emit = defineEmits<{ corrected: [result: ComponentCorrectionResult] }>();
const visible = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  policyNotice = ref('');
const manifest = ref<CompletionManifestHeader>();
const snRows = ref<ProSnVO[]>([]),
  snPage = ref(1),
  snTotal = ref('0');
const selectedSnId = ref(''),
  materialId = ref(''),
  reason = ref('');
const sources = ref<LtWoConsumptionVO[]>([]),
  trace = ref<SnTraceResult>(),
  context = ref<ComponentCorrectionContext>();
const reversals = ref<Array<{ entryId: string; lotId: string; remainingQuantity: string; quantity: string }>>([]);
const allocations = ref<Array<{ consumptionId: string; stepRecordId: string; quantity: string }>>([]);
const original = shallowRef<ComponentCorrectionRequest>(),
  result = ref<ComponentCorrectionResult>();
let generation = 0,
  selection = 0,
  commands = createCommandSession();
const snMore = computed(() => (integerQuantity(snTotal.value) || 0n) > BigInt(snPage.value) * 20n);
const componentSources = computed(() => sources.value.filter((source) => identityText(source.materialId) === materialId.value));
const completedSteps = computed(() => trace.value?.forwardSteps?.filter((step) => step.stepStatus === 'COMPLETED') || []);
const materials = computed(() => {
  const values = new Map<string, string>();
  for (const source of sources.value) {
    const id = identityText(source.materialId);
    if (id) values.set(id, `${source.materialCode || id} ${source.materialName || ''}`);
  }
  if (context.value)
    for (const entry of netComponentEntries(context.value.entries))
      if (!values.has(entry.componentMaterialId)) values.set(entry.componentMaterialId, `原分录组件 ${entry.componentMaterialId}`);
  return [...values].map(([id, label]) => ({ id, label }));
});
const lotLabel = (id: string) => sources.value.find((source) => identityText(source.lotId) === id)?.lotCode || `原批次 ${id}`;
/** 原工单范围的SN分页，不按相同编码推定身份或自动选第一项。 */
async function loadSnPage(page: number) {
  if (!manifest.value) return;
  const request = generation;
  loading.value = true;
  error.value = '';
  try {
    const response = await listSn({ mesWoId: manifest.value.mesWoId, pageNum: page, pageSize: 20 });
    if (request !== generation) return;
    const data = response as unknown as { rows: ProSnVO[]; total: string | number };
    if (!Array.isArray(data.rows) || integerQuantity(data.total) === undefined) throw new Error('服务端未提供真实产品SN分页');
    snRows.value = data.rows;
    snTotal.value = String(data.total);
    snPage.value = page;
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '读取产品SN失败';
  } finally {
    if (request === generation) loading.value = false;
  }
}
async function open(value: CompletionManifestHeader) {
  if (original.value && !result.value) {
    visible.value = true;
    error.value = '保留上次尚未核验的原修订命令，请先重试核对原结果。';
    return;
  }
  generation++;
  selection++;
  visible.value = true;
  manifest.value = JSON.parse(JSON.stringify(value));
  error.value = '';
  reason.value = '';
  policyNotice.value = '';
  selectedSnId.value = '';
  materialId.value = '';
  trace.value = undefined;
  context.value = undefined;
  original.value = undefined;
  result.value = undefined;
  reversals.value = [];
  allocations.value = [];
  commands = createCommandSession();
  sources.value = [];
  snRows.value = [];
  if (value.status !== 'CANCELLED') {
    error.value = '当前修订尚未得到ERP取消确认';
    return;
  }
  const request = generation;
  loading.value = true;
  try {
    const [uses] = await Promise.all([listConsumptionByWo(value.mesWoId), loadSnPage(1)]);
    if (request !== generation) return;
    if (!Array.isArray(uses.data)) throw new Error('服务端未提供原工单实际消耗');
    sources.value = uses.data;
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '读取修订来源失败';
  } finally {
    if (request === generation) loading.value = false;
  }
}
async function loadSn() {
  if (!manifest.value || !selectedSnId.value || original.value) return;
  const request = ++selection;
  context.value = undefined;
  trace.value = undefined;
  materialId.value = '';
  reversals.value = [];
  allocations.value = [];
  error.value = '';
  loading.value = true;
  try {
    const data = (await traceSn(selectedSnId.value)).data;
    if (request !== selection) return;
    if (
      identityText(data.sn?.snId) !== selectedSnId.value ||
      identityText(data.sn?.mesWoId) !== manifest.value.mesWoId ||
      !data.sn?.identityId ||
      typeof data.sn.identityVersion !== 'string' ||
      !Array.isArray(data.componentConsumptions)
    )
      throw new Error('原产品身份、版本或不可变消耗来源不完整');
    const current = {
      manifest: manifest.value,
      sn: data.sn,
      entries: data.componentConsumptions,
      sources: sources.value,
      steps: data.forwardSteps || []
    };
    netComponentEntries(current.entries);
    context.value = current;
    trace.value = data;
  } catch (cause) {
    if (request === selection) error.value = cause instanceof Error ? cause.message : '读取产品原分录失败';
  } finally {
    if (request === selection) loading.value = false;
  }
}
async function changeComponent() {
  if (!context.value) return;
  const request = ++selection;
  reversals.value = netComponentEntries(context.value.entries)
    .filter((row) => row.componentMaterialId === materialId.value && decimalCompare(row.remainingQuantity, '0') > 0)
    .map((row) => ({ ...row, quantity: '' }));
  allocations.value = [];
  context.value.policy = undefined;
  policyNotice.value = '同产品SN内组件混批规则以工单冻结策略和服务端校验为准。';
  try {
    const policy = materialPolicy((await getMaterial(materialId.value)).data);
    if (request !== selection) return;
    const versions = new Set(
      context.value.entries
        .filter((row) => identityText(row.componentMaterialId ?? row.component_material_id) === materialId.value)
        .map((row) => identityText(row.policyVersion ?? row.policy_version))
        .filter(Boolean)
    );
    if (versions.size === 1 && versions.has(policy.version)) {
      context.value.policy = policy;
      policyNotice.value = `${policy.allowMixedLot ? '允许' : '禁止'}同产品SN内本组件跨lot；原分录策略版本 ${policy.version}`;
    }
  } catch {
    if (request === selection) policyNotice.value = '未取得可核对的组件策略版本，最终混批与来源额度由服务端冻结策略校验。';
  }
}
/** 首次尝试后锁定原版本、原分录、原因和命令；ACK丢失不拼接新来源重发。 */
async function submit() {
  if (!context.value || busy.value) return;
  error.value = '';
  busy.value = true;
  try {
    if (!original.value) {
      const selected = reversals.value.filter((row) => row.quantity.trim());
      const extra = allocations.value
        .filter((row) => row.quantity.trim() || row.consumptionId || row.stepRecordId)
        .map((row) => ({
          ...row,
          operationRef: `实际工序复核 ${completedSteps.value.find((step) => identityText(step.stepRecordId) === row.stepRecordId)?.processCode || row.stepRecordId}`
        }));
      const draft = {
        componentMaterialId: materialId.value,
        reason: reason.value,
        reversals: selected.map((row) => ({ originalEntryId: row.entryId, quantity: row.quantity })),
        allocations: extra
      };
      original.value = buildComponentCorrection(
        { commandId: commands.forPayload({ manifest: manifest.value, snId: selectedSnId.value, ...draft }), ...draft },
        context.value
      );
    }
    const response = (await correctSnComponent(original.value)).data;
    if (
      typeof response.version !== 'string' ||
      BigInt(response.version) !== BigInt(original.value.expectedVersion) + 1n ||
      !Array.isArray(response.reversalEntryIds) ||
      !Array.isArray(response.allocationEntryIds) ||
      response.reversalEntryIds.length !== original.value.reversals.length ||
      response.allocationEntryIds.length !== original.value.allocations.length ||
      [...response.reversalEntryIds, ...response.allocationEntryIds].some((id) => typeof id !== 'string' || !id)
    )
      throw new Error('修订响应缺少原单件结果证明，请重试原命令核验');
    result.value = response;
    emit('corrected', response);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '修订失败，请使用原命令重试';
  } finally {
    busy.value = false;
  }
}
onBeforeUnmount(() => {
  generation++;
  selection++;
});
defineExpose({ open });
</script>
