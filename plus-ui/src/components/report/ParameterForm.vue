<template>
  <el-form inline label-position="top" class="report-parameters" @submit.prevent>
    <div v-if="bindingsFailed" class="report-parameters__notice" role="status">
      <span>业务筛选配置读取失败，原参数已保留</span><el-button link type="primary" @click="loadBindings">重试筛选配置</el-button>
    </div>
    <el-form-item
      v-for="parameter in parameters"
      :key="parameter.name"
      :label="parameterLabel(parameter)"
      :required="parameter.required && !parameter.defaultValue"
    >
      <ParameterLookup
        v-if="datasetVersionId && bindings.some((b) => b.parameter === parameter.name)"
        :model-value="modelValue[parameter.name] || ''"
        :version="datasetVersionId"
        :parameter="parameter.name"
        :label="parameterLabel(parameter)"
        @update:model-value="set(parameter.name, $event)"
      />
      <el-date-picker
        v-else-if="parameter.type === 'DATE'"
        :model-value="modelValue[parameter.name] || ''"
        type="date"
        value-format="YYYY-MM-DD"
        clearable
        :placeholder="datePlaceholder(parameter)"
        @update:model-value="set(parameter.name, $event)"
      />
      <el-select
        v-else-if="parameter.type === 'BOOLEAN'"
        :model-value="modelValue[parameter.name] || ''"
        clearable
        placeholder="请选择"
        @update:model-value="set(parameter.name, $event)"
        ><el-option label="是" value="true" /><el-option label="否" value="false"
      /></el-select>
      <el-input
        v-else
        :model-value="modelValue[parameter.name] || ''"
        :placeholder="
          parameter.type === 'ID'
            ? bindingsLoading
              ? '正在读取业务筛选配置…'
              : '输入完整ID，按文本保存'
            : parameter.defaultValue
              ? '留空使用默认值'
              : parameter.label
        "
        :disabled="parameter.type === 'ID' && bindingsLoading"
        clearable
        @update:model-value="set(parameter.name, $event)"
      />
    </el-form-item>
  </el-form>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import ParameterLookup from './ParameterLookup.vue';
import { reportLookupBindings, type LookupBinding } from '@/api/report';
import { RequestGate } from '@/views/report/model';
import type { ReportParameter } from '@/types/report';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存按契约解析的输入参数。 */
  parameters: ReportParameter[];
  /** 保存当前 `modelValue` 值。 */
  modelValue: Record<string, string>;
  /** 保存当前 `datasetVersionId` 值。 */
  datasetVersionId?: string;
}>();
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `'update:modelValue'` 值。 */
  'update:modelValue': [value: Record<string, string>];
}>();
/**
 * 保存当前页面或测试的bindings状态。
 */
const bindings = ref<LookupBinding[]>([]);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/** 读取固定版本的候选绑定期间，避免把ID误认为普通名称输入。 */
const bindingsLoading = ref(false);
/** 绑定读取失败可重试，原参数仍保持完整文本。 */
const bindingsFailed = ref(false);
/** 根据可信默认标记解释日期范围，具体日期由服务端按运行时解析。 */
function datePlaceholder(parameter: ReportParameter): string {
  if (parameter.defaultValue === 'TODAY') return '留空使用今天';
  if (parameter.defaultValue === 'MONTHS_AGO_6') return '留空使用六个月前';
  return parameter.defaultValue ? '留空使用默认日期' : '选择日期';
}
/** 根据固定版本读取当前有业务权限的候选，不更改参数值。 */
async function loadBindings() {
  const ticket = gate.begin();
  bindings.value = [];
  bindingsLoading.value = false;
  bindingsFailed.value = false;
  const version = props.datasetVersionId;
  if (!version || !props.parameters.length) return;
  bindingsLoading.value = true;
  try {
    const response = await reportLookupBindings(version);
    if (gate.current(ticket)) bindings.value = response.data;
  } catch {
    if (gate.current(ticket)) bindingsFailed.value = true;
  } finally {
    if (gate.current(ticket)) bindingsLoading.value = false;
  }
}
/** 已注册参数优先展示业务对象名称，保留未注册自定义参数的原标签。 */
function parameterLabel(parameter: ReportParameter): string {
  return bindings.value.find((binding) => binding.parameter === parameter.name)?.label || parameter.label;
}
watch(() => [props.datasetVersionId, props.parameters.map((parameter) => parameter.name + ':' + parameter.type).join('|')], loadBindings, {
  immediate: true,
  flush: 'sync'
});
onUnmounted(() => gate.invalidate());
/** 处理当前报表的 `set` 操作。 */
function set(name: string, value: unknown) {
  if (props.parameters.find((parameter) => parameter.name === name)?.type === 'ID' && value != null && typeof value !== 'string') return;
  emit('update:modelValue', { ...props.modelValue, [name]: value == null ? '' : String(value) });
}
</script>

<style scoped>
.report-parameters__notice {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 8px;
}
.report-parameters {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 0 18px;
}
.report-parameters :deep(.el-form-item) {
  margin-right: 0;
  min-width: 0;
}
.report-parameters :deep(.el-form-item__label) {
  width: auto;
  max-width: 100%;
  font-size: 12px;
  font-weight: 500;
}
.report-parameters :deep(.el-select),
.report-parameters :deep(.el-date-editor),
.report-parameters :deep(.el-input) {
  width: 100%;
  min-width: 0;
}
@media (max-width: 540px) {
  .report-parameters {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
