<template>
  <el-dialog
    v-model="visible"
    title="弃用未使用手工 SN"
    width="640px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!loading && !reading"
    :close-on-press-escape="!loading && !reading"
  >
    <el-alert
      title="仅允许弃用从未使用的手工登记身份。原号码与审计记录保留，不能删除、回收或重新登记；是否曾使用由服务端核验。"
      type="warning"
      :closable="false"
      class="mb-3"
    />
    <el-descriptions :column="1" border>
      <el-descriptions-item label="正式 SN">{{ original?.snCode || selectedCode }}</el-descriptions-item>
      <el-descriptions-item label="正式身份">{{ selectedId }}</el-descriptions-item>
      <el-descriptions-item label="原身份版本">{{ original?.expectedVersion || '等待服务端原版本' }}</el-descriptions-item>
    </el-descriptions>
    <el-form class="mt-3" label-width="90px" :disabled="reading || loading || !!submitted">
      <el-form-item label="弃用原因" required
        ><el-input v-model="reason" type="textarea" :rows="3" :maxlength="500" show-word-limit placeholder="请填写弃用原因（必填，最多 500 字）"
      /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <el-descriptions v-if="result" :column="1" border class="mt-3">
      <el-descriptions-item label="结果">已弃用，原号码永久保留</el-descriptions-item>
      <el-descriptions-item label="身份版本">{{ result.version }}</el-descriptions-item>
      <el-descriptions-item label="预留状态">{{ result.reservationStatus }}</el-descriptions-item>
      <el-descriptions-item label="审计操作">{{ result.operationId }}</el-descriptions-item>
      <el-descriptions-item label="原命令">{{ result.commandId }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button type="danger" :disabled="!original || reading" :loading="loading" @click="submit">{{
        result ? '重放原弃用命令' : '确认弃用'
      }}</el-button>
      <el-button :disabled="reading || loading" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { getSn } from '@/api/erp/wm/sn';
import { retireSerialIdentity } from '@/api/erp/wm/identity';
import type { WmSnVO } from '@/api/erp/wm/sn/types';
import type { SerialRetirement, SerialRetirementRequest } from '@/api/erp/wm/identity/types';
import { createCommandSession, identityText } from './model';

const emit = defineEmits<{ retired: [result: SerialRetirement] }>();
const visible = ref(false);
const reading = ref(false);
const loading = ref(false);
const selectedId = ref('');
const selectedCode = ref('');
const reason = ref('');
const error = ref('');
const original = ref<{ identityId: string; snCode: string; expectedVersion: string }>();
const result = ref<SerialRetirement>();
let generation = 0;
let commands = createCommandSession();
const submitted = ref<SerialRetirementRequest>();
const pendingCommands = new Map<
  string,
  {
    original: { identityId: string; snCode: string; expectedVersion: string };
    request: SerialRetirementRequest;
  }
>();

/** 新操作读取原详情；存在待核验命令时恢复原请求，不用已变更的详情重建。 */
async function open(row: Pick<WmSnVO, 'snId' | 'snCode'> & Partial<Pick<WmSnVO, 'identityId'>>) {
  const request = ++generation;
  visible.value = true;
  reading.value = true;
  loading.value = false;
  error.value = '';
  reason.value = '';
  original.value = undefined;
  result.value = undefined;
  submitted.value = undefined;
  commands = createCommandSession();
  selectedId.value = identityText(row.identityId ?? row.snId) || '';
  selectedCode.value = row.snCode;
  const pending = pendingCommands.get(selectedId.value);
  if (pending) {
    original.value = pending.original;
    submitted.value = pending.request;
    reason.value = pending.request.reason;
    reading.value = false;
    error.value = '保留上次尚未核验的弃用命令，请重试核对原结果。';
    return;
  }
  try {
    if (!selectedId.value) throw new Error('缺少正式身份，不能弃用');
    const response = await getSn(selectedId.value);
    if (request !== generation) return;
    const detail = response.data;
    if (identityText(detail.identityId ?? detail.snId) !== selectedId.value) throw new Error('服务端身份与所选SN不一致');
    if (detail.status !== 'REGISTERED' || detail.sourceDocType !== 'ERP_MANUAL' || detail.sourceDocId != null)
      throw new Error('仅允许弃用未使用的ERP手工登记身份');
    if (detail.stockId != null || detail.warehouseId != null || detail.locationId != null)
      throw new Error('身份已绑定库存维度，请通过原业务流程处理');
    if (typeof detail.identityVersion !== 'string' || !/^[1-9]\d*$/.test(detail.identityVersion))
      throw new Error('服务端未返回身份原版本 identityVersion，无法安全弃用');
    original.value = { identityId: selectedId.value, snCode: detail.snCode, expectedVersion: detail.identityVersion };
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '读取原身份失败';
  } finally {
    if (request === generation) reading.value = false;
  }
}

/** ACK丢失时保留原命令、原因和原版本，重试不重新取当前版本或重开登记。 */
async function submit() {
  if (!original.value || loading.value || reading.value) return;
  error.value = '';
  try {
    if (!submitted.value && (!reason.value.trim() || reason.value.length > 500)) throw new Error('弃用原因必填且不能超过500字');
    const payload = { expectedVersion: original.value.expectedVersion, reason: reason.value };
    const request = submitted.value || { ...payload, commandId: commands.forPayload({ identityId: original.value.identityId, ...payload }) };
    submitted.value = request;
    pendingCommands.set(original.value.identityId, { original: { ...original.value }, request });
    loading.value = true;
    const response = await retireSerialIdentity(original.value.identityId, request);
    if (
      response.data.identityId !== original.value.identityId ||
      response.data.commandId !== request.commandId ||
      response.data.status !== 'RETIRED' ||
      response.data.version !== (BigInt(original.value.expectedVersion) + 1n).toString()
    )
      throw new Error('弃用响应与原身份或结果不一致，请使用原命令核验');
    result.value = response.data;
    pendingCommands.delete(original.value.identityId);
    emit('retired', response.data);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '弃用失败，可使用原命令重试';
  } finally {
    loading.value = false;
  }
}
defineExpose({ open });
</script>
