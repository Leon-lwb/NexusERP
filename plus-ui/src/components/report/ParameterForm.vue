<template>
  <el-form inline label-position="top" class="report-parameters" @submit.prevent>
    <el-form-item
      v-for="parameter in parameters"
      :key="parameter.name"
      :label="parameter.label"
      :required="parameter.required && !parameter.defaultValue"
    >
      <ParameterLookup
        v-if="datasetVersionId && bindings.some((b) => b.parameter === parameter.name)"
        :model-value="modelValue[parameter.name] || ''"
        :version="datasetVersionId"
        :parameter="parameter.name"
        :label="parameter.label"
        @update:model-value="set(parameter.name, $event)"
      />
      <el-date-picker
        v-else-if="parameter.type === 'DATE'"
        :model-value="modelValue[parameter.name] || ''"
        type="date"
        value-format="YYYY-MM-DD"
        clearable
        :placeholder="parameter.defaultValue ? '留空使用默认日期' : '选择日期'"
        @update:model-value="set(parameter.name, $event)"
      />
      <el-select
        v-else-if="parameter.type === 'BOOLEAN'"
        :model-value="modelValue[parameter.name] || ''"
        clearable
        placeholder="请选择"
        style="width: 180px"
        @update:model-value="set(parameter.name, $event)"
        ><el-option label="是" value="true" /><el-option label="否" value="false"
      /></el-select>
      <el-input
        v-else
        :model-value="modelValue[parameter.name] || ''"
        :placeholder="parameter.defaultValue ? '留空使用默认值' : parameter.label"
        clearable
        style="width: 200px"
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
watch(
  () => props.datasetVersionId,
  async (version) => {
    const ticket = gate.begin();
    bindings.value = [];
    if (!version) return;
    try {
      const response = await reportLookupBindings(version);
      if (gate.current(ticket)) bindings.value = response.data;
    } catch {}
  },
  { immediate: true }
);
onUnmounted(() => gate.invalidate());
/** 处理当前报表的 `set` 操作。 */
function set(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value == null ? '' : String(value) });
}
</script>
