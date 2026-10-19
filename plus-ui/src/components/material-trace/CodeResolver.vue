<template>
  <div class="code-resolver">
    <el-input v-model="rawCode" :disabled="disabled || loading" placeholder="扫描正式 SN、外部别名或完整二维码" @keyup.enter="resolve(rawCode)">
      <template #append><el-button :loading="loading" :disabled="disabled" @click="resolve(rawCode)">解析</el-button></template>
    </el-input>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon class="mt-2" />
    <div v-if="resolution?.status === 'AMBIGUOUS'" class="mt-2">
      <p>存在多个候选，请核对正式身份和物料后明确选择。</p>
      <el-table :data="resolution.candidates || []" border size="small">
        <el-table-column prop="objectType" label="对象" width="90" />
        <el-table-column label="对象身份" min-width="180"
          ><template #default="{ row }">{{
            row.objectType === 'DOCUMENT' ? row.objectId : row.identityId || row.batchId || row.materialId
          }}</template></el-table-column
        >
        <el-table-column label="正式 SN / 单据编号" min-width="160"
          ><template #default="{ row }">{{
            row.objectType === 'DOCUMENT' ? row.documentCode : row.canonicalSnCode || row.snCode
          }}</template></el-table-column
        >
        <el-table-column prop="materialCode" label="物料" min-width="120" />
        <el-table-column prop="batchCode" label="批次" min-width="120" />
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button link type="primary" :disabled="disabled" @click="choose(row)">选择此项</el-button></template
          ></el-table-column
        >
      </el-table>
      <el-button class="mt-2" :disabled="disabled" @click="discard">放弃本次扫描</el-button>
    </div>
    <el-button v-else-if="error && !loading" link :disabled="disabled" @click="discard">放弃本次扫描</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { resolveMaterialCode } from '@/api/erp/wm/identity';
import type { CodeResolution, ResolvedMaterialObject, ResolveCodeRequest } from '@/api/erp/wm/identity/types';
import { rawCodeText, resolvedObject } from './model';

const props = withDefaults(defineProps<{ scene: string; context?: Omit<Partial<ResolveCodeRequest>, 'rawCode' | 'scene'>; disabled?: boolean }>(), {
  disabled: false
});
const emit = defineEmits<{
  resolved: [resolution: CodeResolution];
  state: [state: { pending: boolean; error: string }];
}>();
const rawCode = ref('');
const resolution = ref<CodeResolution>();
const loading = ref(false);
const error = ref('');
let requestVersion = 0;

/** 仅发布完整对象，绑定校验由业务行原子完成。 */
function publish(value: CodeResolution) {
  resolvedObject(value);
  error.value = '';
  resolution.value = value;
  emit('state', { pending: false, error: '' });
  emit('resolved', value);
}

/** 候选只在用户点击后接受，绝不默认第一条。 */
function choose(candidate: ResolvedMaterialObject) {
  try {
    publish({ ...candidate, status: 'RESOLVED' });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '候选证据不完整';
    emit('state', { pending: false, error: error.value });
  }
}

/** 新扫描使旧异步结果失效，OCR 原对象整体传给解析接口。 */
async function resolve(value: string | object) {
  if (props.disabled) return;
  const version = ++requestVersion;
  const context = JSON.stringify(props.context);
  rawCode.value = rawCodeText(value);
  resolution.value = undefined;
  error.value = '';
  loading.value = true;
  emit('state', { pending: true, error: '' });
  try {
    if (!rawCode.value) throw new Error('请扫描或输入完整编码');
    const response = await resolveMaterialCode({ ...props.context, rawCode: rawCode.value, scene: props.scene });
    if (version !== requestVersion || context !== JSON.stringify(props.context)) return;
    resolution.value = response.data;
    if (response.data.status === 'RESOLVED') publish(response.data);
    else {
      error.value = response.data.reason || (response.data.status === 'AMBIGUOUS' ? '解析有歧义，请明确选择候选' : '编码未识别，当前物料行保持原值');
      emit('state', { pending: false, error: error.value });
    }
  } catch (cause) {
    if (version !== requestVersion) return;
    error.value = cause instanceof Error ? cause.message : '解析失败，请重试';
    emit('state', { pending: false, error: error.value });
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

/** 主动放弃扫描后解除阻断，不修改已经绑定的实物。 */
function discard() {
  ++requestVersion;
  rawCode.value = '';
  resolution.value = undefined;
  error.value = '';
  loading.value = false;
  emit('state', { pending: false, error: '' });
}
watch(() => [props.context, props.scene], discard, { deep: true });
defineExpose({ resolve, discard });
</script>
