<template>
  <el-dialog v-model="visible" title="登记正式 SN" width="860px" append-to-body :close-on-click-modal="false">
    <el-alert title="发号只登记 REGISTERED；实际在库及发货状态由真实业务单据推进。号码不会回收。" type="info" :closable="false" class="mb-3" />
    <el-form :model="form" label-width="100px" :disabled="loading || !!reservation">
      <el-form-item label="物料" required>
        <el-input :model-value="materialLabel" readonly placeholder="请选择物料"
          ><template #append><el-button @click="materialDialog?.open()">选择</el-button></template></el-input
        >
      </el-form-item>
      <el-form-item label="数量" required><el-input-number v-model="form.quantity" :min="1" :max="1000" :precision="0" /></el-form-item>
      <el-form-item label="发号规则"><el-input v-model="form.ruleId" placeholder="可空，使用服务端规则" /></el-form-item>
      <el-form-item label="登记来源"><el-input model-value="ERP 手工登记" readonly /></el-form-item>
      <el-form-item label="来源单据">无；业务单据和 MES 发号应在对应业务流程完成。</el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template v-if="reservation">
      <el-descriptions :column="1" border class="mt-3">
        <el-descriptions-item label="登记结果">已登记，库存状态以真实单据为准</el-descriptions-item>
        <el-descriptions-item label="预留状态">{{ reservation.status }}</el-descriptions-item>
        <el-descriptions-item label="预留 ID">{{ reservation.reservationId }}</el-descriptions-item>
        <el-descriptions-item label="原命令">{{ reservation.commandId }}</el-descriptions-item>
        <el-descriptions-item label="请求摘要">{{ reservation.fingerprint }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="reservation.identities" class="mt-3" border>
        <el-table-column prop="identityId" label="正式身份" min-width="180" />
        <el-table-column prop="snCode" label="正式 SN" min-width="180" />
        <el-table-column prop="batchCode" label="批次" min-width="130" />
      </el-table>
    </template>
    <template #footer>
      <el-button :loading="loading" type="primary" @click="submit">{{ reservation ? '重放原登记命令' : '确认登记' }}</el-button>
      <el-button :disabled="loading" @click="visible = false">关闭</el-button>
    </template>
    <MaterialSelectDialog ref="materialDialog" @select="selectMaterial" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { reserveSerialIdentities } from '@/api/erp/wm/identity';
import type { SerialReservation, ManualSerialReservationRequest } from '@/api/erp/wm/identity/types';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { createCommandSession, identityText } from './model';

const emit = defineEmits<{ registered: [] }>();
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const materialDialog = ref<InstanceType<typeof MaterialSelectDialog>>();
const materialLabel = ref('');
const form = ref({ materialId: '', quantity: 1, ruleId: '' });
const reservation = ref<SerialReservation>();
let commands = createCommandSession();
let submitted: ManualSerialReservationRequest | undefined;

/** 每次有意登记开启新命令，失败或结果查看中的重试保留原请求。 */
function open(options?: { ruleId?: string }) {
  commands = createCommandSession();
  submitted = undefined;
  form.value = { materialId: '', quantity: 1, ruleId: options?.ruleId || '' };
  materialLabel.value = '';
  reservation.value = undefined;
  error.value = '';
  visible.value = true;
}

/** 物料使用选择器原身份，不允许仅编辑物料编码。 */
function selectMaterial(material: { materialId: string | number; materialCode: string; materialName: string }) {
  form.value.materialId = identityText(material.materialId)!;
  materialLabel.value = `${material.materialCode} ${material.materialName}`;
}

/** /reserve 成功后展示不可变身份列表；重放复用同一命令和参数。 */
async function submit() {
  if (loading.value) return;
  error.value = '';
  try {
    if (!form.value.materialId || !Number.isInteger(form.value.quantity) || form.value.quantity < 1) throw new Error('请选择物料并填写正整数数量');
    const payload = {
      materialId: form.value.materialId,
      quantity: form.value.quantity,
      ruleId: identityText(form.value.ruleId),
      sourceType: 'ERP_MANUAL' as const,
      sourceId: null
    };
    const request = reservation.value && submitted ? submitted : { ...payload, commandId: commands.forPayload(payload) };
    submitted = request;
    loading.value = true;
    const response = await reserveSerialIdentities(request);
    reservation.value = response.data;
    emit('registered');
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登记失败，可使用原命令重试';
  } finally {
    loading.value = false;
  }
}
defineExpose({ open });
</script>
