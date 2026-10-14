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
import { ref, watch, onUnmounted } from 'vue';
import { reportLookup } from '@/api/report';
import { RequestGate } from '@/views/report/model';
const props = defineProps<{ modelValue: string; version: string; parameter: string; label: string }>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();
const options = ref<{ value: string; label: string }[]>([]);
const total = ref(0);
const loading = ref(false);
const initialized = ref(false);
const gate = new RequestGate();
let keyword = '';
let page = 1;
async function opened(visible: boolean) {
  if (visible && !initialized.value && !loading.value) await search('');
}
async function search(value: string) {
  keyword = value;
  page = 1;
  options.value = [];
  total.value = 0;
  await load(false);
}
async function more() {
  if (loading.value) return;
  page++;
  await load(true);
}
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
