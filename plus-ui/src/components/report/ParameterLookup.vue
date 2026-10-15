<template>
  <div class="report-lookup">
    <el-select
      :model-value="modelValue"
      remote
      filterable
      clearable
      :remote-method="queueSearch"
      :loading="loading || resolving"
      :placeholder="'搜索' + label + '编码、名称或ID'"
      :aria-label="label"
      class="report-lookup__select"
      @visible-change="opened"
      @update:model-value="select"
    >
      <el-option
        v-if="modelValue && !options.some((option) => option.value === modelValue)"
        :key="'selected/' + modelValue + '/' + (selected?.label || (resolving ? 'loading' : 'unavailable'))"
        :value="modelValue"
        :label="selected?.label || (resolving ? '正在读取' + label + '名称…' : '已传入' + label + '，名称暂不可用')"
        :disabled="!selected"
      />
      <el-option v-for="option in options" :key="'candidate/' + option.value" :label="option.label" :value="option.value" />
      <template #empty
        ><div class="report-lookup__empty">
          {{ loading ? '正在查询业务对象…' : failed ? '候选载入失败，请重试' : '没有匹配结果，可尝试编码、名称或完整ID' }}
        </div></template
      >
      <template #footer
        ><div class="report-lookup__footer">
          <span>按编码、名称或完整ID查找</span
          ><el-button v-if="initialized && moreAvailable" link type="primary" :loading="loading" @click="more">加载更多</el-button>
        </div></template
      >
    </el-select>
    <div v-if="failed" class="report-lookup__feedback" role="status">
      <span>候选载入失败</span><el-button link type="primary" :disabled="loading" @click="retry">重试</el-button>
    </div>
    <div v-if="selectionFailed" class="report-lookup__feedback" role="status">
      <span>名称读取失败，原参数已保留</span><el-button link type="primary" :disabled="resolving" @click="resolveSelected">重试名称</el-button>
    </div>
    <div v-else-if="selectionUnavailable" class="report-lookup__hint">当前值暂不可见或名称不可用，已保留原参数；可重新搜索选择。</div>
  </div>
</template>
<script setup lang="ts">
/** 业务选择器支持完整ID回显、分页与请求失效，不把ID转为浮点数。 */
import { ref, watch, onUnmounted } from 'vue';
import { reportLookup, reportLookupSelected, type LookupOption } from '@/api/report';
import { RequestGate } from '@/views/report/model';
/** 固定版本的业务参数绑定与当前完整值。 */
const props = defineProps<{
  /** 完整ID或业务编码。 */
  modelValue: string;
  /** 固定数据集版本。 */
  version: string;
  /** 代码登记的参数名。 */
  parameter: string;
  /** 已授权绑定的业务名称。 */
  label: string;
}>();
/** 仅在用户明确选择或清空时提交参数变更。 */
const emit = defineEmits<{
  /** 保留完整文本值。 */
  'update:modelValue': [value: string];
}>();
/** 已成功载入的搜索结果，不把固定已选项计入分页数量。 */
const options = ref<LookupOption[]>([]);
/** 当前搜索的可见候选总数。 */
const total = ref(0);
/** 已消费的原始候选条数，重复条目也占用服务端分页位置。 */
let consumed = 0;
/** 当前原始页仍有下一页，空页后立即停止追加。 */
const moreAvailable = ref(false);
/** 搜索或等待防抖时显示加载状态。 */
const loading = ref(false);
/** 当前搜索至少成功读取过一页。 */
const initialized = ref(false);
/** 候选失败时提供原条件重试。 */
const failed = ref(false);
/** 区分追加页和首页失败，不提前推进页码。 */
const failedAppend = ref(false);
/** 已选对象的名称只保留在当前组件上下文中。 */
const selected = ref<LookupOption>();
/** 正在按完整值读取名称。 */
const resolving = ref(false);
/** 名称回显网络失败不会清空原参数。 */
const selectionFailed = ref(false);
/** 空回显只提示当前值不可见或名称不可用。 */
const selectionUnavailable = ref(false);
/** 搜索响应与选中值回显分别使用围栏。 */
const gate = new RequestGate();
/** 选中值变化时丢弃旧名称回包。 */
const selectedGate = new RequestGate();
/** 当前已提交的搜索关键字。 */
let keyword = '';
/** 当前成功读取的页码。 */
let page = 1;
/** 防抖任务在参数切换和组件卸载时清理。 */
let timer: ReturnType<typeof setTimeout> | undefined;

/** 打开时只初始化一次；关闭时取消尚未开始的延迟检索。 */
async function opened(visible: boolean) {
  if (!visible && timer) {
    clearTimer();
    loading.value = false;
  }
  if (visible && !initialized.value && !loading.value) await search('');
}
/** 用户连续输入时先使旧检索失效，再等待短暂防抖。 */
function queueSearch(value: string) {
  clearTimer();
  gate.invalidate();
  options.value = [];
  total.value = 0;
  consumed = 0;
  moreAvailable.value = false;
  initialized.value = false;
  failed.value = false;
  loading.value = true;
  timer = setTimeout(() => {
    timer = undefined;
    void search(value);
  }, 250);
}
/** 立即执行明确搜索，测试和打开候选时无需等待防抖。 */
async function search(value: string) {
  clearTimer();
  keyword = value.trim().slice(0, 100);
  page = 1;
  options.value = [];
  total.value = 0;
  consumed = 0;
  moreAvailable.value = false;
  await load(false);
}
/** 追加下一页，成功前不改变已提交页码。 */
async function more() {
  if (loading.value || !initialized.value || !moreAvailable.value) return;
  await load(true);
}
/** 重试失败的同一关键字和同一目标页。 */
async function retry() {
  if (!loading.value) await load(failedAppend.value);
}
/** 读取有界候选，只接受当前围栏中的完整文本键。 */
async function load(append: boolean) {
  const ticket = gate.begin();
  const targetPage = append ? page + 1 : 1;
  loading.value = true;
  failed.value = false;
  try {
    const response = await reportLookup(props.version, props.parameter, keyword, targetPage);
    if (!gate.current(ticket)) return;
    const values = new Map<string, LookupOption>();
    for (const option of [...(append ? options.value : []), ...response.data.options]) {
      if (typeof option.value === 'string' && typeof option.label === 'string') values.set(option.value, option);
    }
    options.value = [...values.values()];
    total.value = response.data.total;
    consumed = (append ? consumed : 0) + response.data.options.length;
    moreAvailable.value = response.data.options.length > 0 && consumed < total.value;
    page = targetPage;
    initialized.value = true;
    const current = options.value.find((option) => option.value === props.modelValue);
    if (current) {
      selected.value = current;
      selectionUnavailable.value = false;
      selectionFailed.value = false;
    }
  } catch {
    if (gate.current(ticket)) {
      failed.value = true;
      failedAppend.value = append;
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 当前选择没有缓存名称时按完整值读取，始终保留原绑定值。 */
async function resolveSelected() {
  const ticket = selectedGate.begin();
  selected.value = undefined;
  selectionFailed.value = false;
  selectionUnavailable.value = false;
  resolving.value = false;
  const value = props.modelValue;
  if (!value) return;
  const current = options.value.find((option) => option.value === value);
  if (current) {
    selected.value = current;
    return;
  }
  resolving.value = true;
  try {
    const response = await reportLookupSelected(props.version, props.parameter, value);
    if (!selectedGate.current(ticket)) return;
    if (response.data && response.data.value === value && typeof response.data.label === 'string') selected.value = response.data;
    else selectionUnavailable.value = true;
  } catch {
    if (selectedGate.current(ticket)) selectionFailed.value = true;
  } finally {
    if (selectedGate.current(ticket)) resolving.value = false;
  }
}
/** 只接受原始文本；数字型值已经可能舍入，禁止伪装成完整ID。 */
function select(value: unknown) {
  if (value == null) emit('update:modelValue', '');
  else if (typeof value === 'string') emit('update:modelValue', value);
}
/** 清理尚未开始的延迟检索。 */
function clearTimer() {
  if (timer) clearTimeout(timer);
  timer = undefined;
}
watch(
  () => [props.version, props.parameter],
  () => {
    clearTimer();
    gate.invalidate();
    selectedGate.invalidate();
    options.value = [];
    total.value = 0;
    consumed = 0;
    moreAvailable.value = false;
    initialized.value = false;
    failed.value = false;
    keyword = '';
    page = 1;
    loading.value = false;
    selected.value = undefined;
  },
  { flush: 'sync' }
);
watch(() => [props.version, props.parameter, props.modelValue], resolveSelected, { immediate: true, flush: 'sync' });
onUnmounted(() => {
  clearTimer();
  gate.invalidate();
  selectedGate.invalidate();
});
</script>
<style scoped>
.report-lookup,
.report-lookup__select {
  width: 100%;
  min-width: 0;
}
.report-lookup__feedback {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
.report-lookup__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  margin-top: 5px;
}
.report-lookup__empty {
  padding: 14px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.report-lookup__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
</style>
