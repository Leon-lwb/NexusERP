<template>
  <div class="flex flex-wrap items-center gap-2 mb-3" @keydown.stop>
    <el-select v-model="selected" multiple collapse-tags placeholder="多选明细列" style="width: 250px"
      ><el-option v-for="column in modelValue.columns" :key="column.field" :label="column.label" :value="column.field"
    /></el-select>
    <el-button :disabled="!selected.length || disabled" @click="style({ bold: true })">加粗</el-button
    ><el-button :disabled="!selected.length || disabled" @click="style({ bold: false })">常规</el-button>
    <el-button v-for="align in alignments" :key="align" :disabled="!selected.length || disabled" @click="style({ align })">{{
      align === 'LEFT' ? '左对齐' : align === 'CENTER' ? '居中' : '右对齐'
    }}</el-button>
    <el-color-picker v-model="color" @change="(value) => value && style({ color: value })" />
    <el-button :disabled="!selected.length" @click="copy">复制列</el-button
    ><el-button :disabled="!clipboard || disabled" @click="paste">粘贴列</el-button>
    <el-slider
      v-if="selected.length"
      v-model="width"
      :min="30"
      :max="500"
      :step="1"
      :disabled="disabled"
      style="width: 150px"
      @change="resize"
    /><span v-if="selected.length">列宽 {{ width }} pt</span>
  </div>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { ReportDsl, ReportStyle } from '@/types/report';
import { styleColumns, copyColumns, pasteColumns } from '@/views/report/designer/editorCommands';
/**
 * 保存当前页面或测试的alignments状态。
 */
const alignments = ['LEFT', 'CENTER', 'RIGHT'] as const;
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存当前 `modelValue` 值。 */
  modelValue: ReportDsl;
  /** 保存当前 `disabled` 值。 */
  disabled: boolean;
}>();
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `'update:modelValue'` 值。 */
  'update:modelValue': [ReportDsl];
}>();
/**
 * 保存当前页面或测试的selected状态。
 */
const selected = ref<string[]>([]);
/**
 * 保存当前页面或测试的selectedIndices状态。
 */
const selectedIndices = computed(() =>
  selected.value.map((field) => props.modelValue.columns.findIndex((column) => column.field === field)).filter((index) => index >= 0)
);
/**
 * 保存当前页面或测试的color状态。
 */
const color = ref('#1f2937');
/**
 * 保存当前页面或测试的width状态。
 */
const width = ref(100);
/**
 * 保存当前页面或测试的clipboard状态。
 */
const clipboard = ref('');
watch(
  () => props.modelValue.columns.length,
  () => (selected.value = [])
);
watch(
  () => props.modelValue.columns.map((column) => column.field).join('|'),
  () => {
    const fields = new Set(props.modelValue.columns.map((column) => column.field));
    selected.value = selected.value.filter((field) => fields.has(field));
  }
);
watch(
  selected,
  () => {
    const first = selectedIndices.value[0];
    if (first !== undefined) width.value = props.modelValue.columns[first].width;
  },
  { deep: true }
);
watch(
  () => props.modelValue.datasetVersionId,
  () => {
    selected.value = [];
    clipboard.value = '';
  }
);
/** 处理当前报表的 `style` 操作。 */
function style(value: Partial<ReportStyle>) {
  try {
    emit('update:modelValue', styleColumns(props.modelValue, selectedIndices.value, value));
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 复制已授权定义并清除业务标识默认值。 */
function copy() {
  try {
    clipboard.value = copyColumns(props.modelValue, selectedIndices.value);
    ElMessage.success('已复制所选列');
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 处理当前报表的 `paste` 操作。 */
function paste() {
  try {
    emit('update:modelValue', pasteColumns(props.modelValue, clipboard.value));
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 处理当前报表的 `resize` 操作。 */
function resize() {
  if (props.disabled) return;
  const value = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
  selectedIndices.value.forEach((i) => (value.columns[i].width = width.value));
  emit('update:modelValue', value);
}
</script>
