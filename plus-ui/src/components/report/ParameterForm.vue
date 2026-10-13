<template>
  <el-form inline label-position="top" class="report-parameters" @submit.prevent>
    <el-form-item
      v-for="parameter in parameters"
      :key="parameter.name"
      :label="parameter.label"
      :required="parameter.required && !parameter.defaultValue"
    >
      <el-date-picker
        v-if="parameter.type === 'DATE'"
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
import type { ReportParameter } from '@/types/report';
const props = defineProps<{ parameters: ReportParameter[]; modelValue: Record<string, string> }>();
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, string>] }>();
function set(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value == null ? '' : String(value) });
}
</script>
