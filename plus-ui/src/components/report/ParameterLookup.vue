<template>
  <el-select
    :model-value="modelValue"
    remote
    filterable
    clearable
    :remote-method="search"
    :loading="loading"
    :placeholder="'搜索' + label"
    style="width: 230px"
    @visible-change="opened"
    @update:model-value="(value) => emit('update:modelValue', value == null ? '' : String(value))"
    ><el-option v-if="modelValue && !options.some((o) => o.value === modelValue)" :label="modelValue" :value="modelValue" /><el-option
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    /><template #empty
      ><div class="px-4 py-2 text-gray-500">{{ loading ? '加载中…' : '没有匹配结果' }}</div></template
    ><template #footer><el-button v-if="total > options.length" link :loading="loading" @click="more">加载更多</el-button></template></el-select
  >
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import { reportLookup } from '@/api/report';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存当前 `modelValue` 值。 */
  modelValue: string;
  /** 保存固定版本。 */
  version: string;
  /** 保存当前 `parameter` 值。 */
  parameter: string;
  /** 保存展示标题。 */
  label: string;
}>();
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `'update:modelValue'` 值。 */
  'update:modelValue': [string];
}>();
/**
 * 保存当前页面或测试的options状态。
 */
const options = ref<
  {
    /** 保存当前 `value` 值。 */
    value: string;
    /** 保存展示标题。 */
    label: string;
  }[]
>([]);
/**
 * 保存当前页面或测试的total状态。
 */
const total = ref(0);
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的initialized状态。
 */
const initialized = ref(false);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/**
 * 保存当前页面或测试的keyword状态。
 */
let keyword = '';
/**
 * 保存当前页面或测试的page状态。
 */
let page = 1;
/** 处理当前报表的 `opened` 操作。 */
async function opened(visible: boolean) {
  if (visible && !initialized.value && !loading.value) await search('');
}
/** 处理当前报表的 `search` 操作。 */
async function search(value: string) {
  keyword = value;
  page = 1;
  options.value = [];
  total.value = 0;
  await load(false);
}
/** 处理当前报表的 `more` 操作。 */
async function more() {
  if (loading.value) return;
  page++;
  await load(true);
}
/** 处理当前报表的 `load` 操作。 */
async function load(append: boolean) {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const response = await reportLookup(props.version, props.parameter, keyword, page);
    if (!gate.current(ticket)) return;
    options.value = append ? [...options.value, ...response.data.options] : response.data.options;
    total.value = response.data.total;
    initialized.value = true;
  } catch {
    if (append && gate.current(ticket)) page--;
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
watch(
  () => [props.version, props.parameter],
  () => {
    gate.invalidate();
    options.value = [];
    total.value = 0;
    initialized.value = false;
    keyword = '';
    page = 1;
    loading.value = false;
  }
);
onUnmounted(() => gate.invalidate());
</script>
