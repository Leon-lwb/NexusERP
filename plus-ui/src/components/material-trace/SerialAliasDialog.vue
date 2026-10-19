<template>
  <el-dialog v-model="visible" title="登记外部别名" width="620px" append-to-body :close-on-click-modal="false">
    <el-descriptions :column="1" border
      ><el-descriptions-item label="正式身份">{{ identityId }}</el-descriptions-item
      ><el-descriptions-item label="正式 SN">{{ snCode }}</el-descriptions-item></el-descriptions
    >
    <el-form :model="form" label-width="100px" class="mt-3" :disabled="loading || completed">
      <el-form-item label="别名来源" required
        ><el-select v-model="form.sourceType"
          ><el-option v-for="type in sourceTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select
      ></el-form-item>
      <el-form-item label="来源主体 ID"><el-input v-model="form.sourceId" placeholder="供应商或客户身份字符串" /></el-form-item>
      <el-form-item label="外部编码" required><el-input v-model="form.code" placeholder="保留外部别名原文" /></el-form-item>
    </el-form>
    <el-alert
      v-if="error || completed"
      :title="error || '别名已登记；正式 SN 与库存状态保持由业务事实决定。'"
      :type="error ? 'warning' : 'success'"
      :closable="false"
    />
    <template #footer
      ><el-button type="primary" :loading="loading" @click="submit">{{ completed ? '重放原命令' : '登记别名' }}</el-button
      ><el-button :disabled="loading" @click="visible = false">关闭</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { registerSerialAlias } from '@/api/erp/wm/identity';
import type { SerialAliasRequest } from '@/api/erp/wm/identity/types';
import { createCommandSession, identityText } from './model';
const sourceTypes = [
  { value: 'SUPPLIER', label: '供应商' },
  { value: 'CUSTOMER', label: '客户' },
  { value: 'LEGACY_ERP', label: '历史 ERP' },
  { value: 'LEGACY_MES', label: '历史 MES' }
];
const visible = ref(false);
const loading = ref(false);
const completed = ref(false);
const error = ref('');
const identityId = ref('');
const snCode = ref('');
const form = ref<{ sourceType: SerialAliasRequest['sourceType']; sourceId: string; code: string }>({
  sourceType: 'SUPPLIER',
  sourceId: '',
  code: ''
});
let commands = createCommandSession();
let submitted: SerialAliasRequest | undefined;

/** 别名绑定正式 ERP 身份，不使用 MES 本地 snId。 */
function open(identity: { identityId?: string; snId?: string | number; snCode?: string }) {
  identityId.value = identityText(identity.identityId ?? identity.snId) || '';
  snCode.value = identity.snCode || '';
  commands = createCommandSession();
  submitted = undefined;
  form.value = { sourceType: 'SUPPLIER', sourceId: '', code: '' };
  completed.value = false;
  error.value = '';
  visible.value = true;
}

/** 原文别名携带来源主体；网络重试和显式重放复用原命令。 */
async function submit() {
  if (loading.value) return;
  try {
    error.value = '';
    if (!identityId.value || !form.value.code.trim()) throw new Error('正式身份和外部编码不能为空');
    if ((form.value.sourceType === 'SUPPLIER' || form.value.sourceType === 'CUSTOMER') && !form.value.sourceId)
      throw new Error('供应商或客户别名需要来源主体 ID');
    const payload = { ...form.value, sourceId: identityText(form.value.sourceId) };
    const request = completed.value && submitted ? submitted : { ...payload, commandId: commands.forPayload(payload) };
    submitted = request;
    loading.value = true;
    await registerSerialAlias(identityId.value, request);
    completed.value = true;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登记失败，可重试原命令';
  } finally {
    loading.value = false;
  }
}
defineExpose({ open });
</script>
