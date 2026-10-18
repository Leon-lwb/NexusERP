<template>
  <div v-loading="loading" class="pivot-wrap">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="result"
      ><div class="mb-3">固定快照原始行：{{ result.sourceRows }}；点击单元格查看本次快照的原始记录。</div>
      <table class="pivot-table">
        <thead>
          <tr>
            <th>行维度</th>
            <th v-for="(axis, c) in result.columns" :key="c">{{ axis.map(display).join(' / ') || '指标' }}</th>
            <th>行合计</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, r) in result.rows" :key="r" :class="{ subtotal: row.subtotal }">
            <th>{{ row.keys.map(display).join(' / ') }}{{ row.subtotal ? ' / 小计' : '' }}</th>
            <td v-for="(cell, c) in row.cells" :key="c">
              <el-button link :disabled="busy" @click="open(cell.coordinate)">{{ cell.values.map(display).join(' / ') }}</el-button>
            </td>
            <td>{{ row.total.map(display).join(' / ') }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr v-for="(summary, i) in result.totals" :key="i" class="subtotal">
            <th>
              总计
              {{
                Object.entries(summary.partition)
                  .map(([key, value]) => key + '=' + display(value))
                  .join('，')
              }}
            </th>
            <td v-for="(cell, c) in summary.cells" :key="c">
              <el-button link :disabled="busy" @click="open(cell.coordinate)">{{ cell.values.map(display).join(' / ') }}</el-button>
            </td>
            <td>{{ summary.values.map(display).join(' / ') }}</td>
          </tr>
        </tfoot>
      </table>
    </template>
    <el-dialog v-model="dialog" title="固定快照原始记录" width="80%" destroy-on-close
      ><TypedTable :columns="columns" :rows="drillRows" /><el-pagination
        v-model:current-page="page"
        :page-size="50"
        :total="total"
        class="mt-3"
        @current-change="fetchDrill"
    /></el-dialog>
  </div>
</template>
<script setup lang="ts">
/** 展示服务端交叉结果，业务数值不经过JavaScript Number。 */
import { ref, computed, watch, onUnmounted } from 'vue';
import { runPivot, runDrill } from '@/api/report';
import type { PivotResult, TypedValue, TypedRow } from '@/types/report';
import { RequestGate, normalStyle, formatDimension } from '@/views/report/model';
import TypedTable from './TypedTable.vue';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /**
   * 当前契约的runId配置或状态
   */
  runId: string;
}>();
/**
 * 保存当前页面或测试的result、loading、busy、error、dialog、drillRows、total、page、coordinate状态。
 */
const result = ref<PivotResult>(),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  dialog = ref(false),
  drillRows = ref<TypedRow[]>([]),
  total = ref(0),
  page = ref(1),
  coordinate = ref('');
/**
 * 保存当前页面或测试的gate、drillGate状态。
 */
const gate = new RequestGate(),
  drillGate = new RequestGate();
/**
 * 保存当前页面或测试的columns状态。
 */
const columns = computed(() =>
  Object.keys(drillRows.value[0] || {}).map((field) => ({ field, label: field, width: 140, scale: 4, expression: null, style: normalStyle() }))
);
/** 精确值直接显示，空值有独立标记。 */
function display(value: TypedValue) {
  return formatDimension(value);
}
/** 运行变化后使旧交叉结果和钻取回包失效。 */
async function load() {
  const ticket = gate.begin();
  drillGate.invalidate();
  dialog.value = false;
  drillRows.value = [];
  result.value = undefined;
  loading.value = true;
  error.value = '';
  try {
    const response = await runPivot(props.runId);
    if (gate.current(ticket)) result.value = response.data;
  } catch {
    if (gate.current(ticket)) error.value = '交叉快照读取失败，请检查运行状态与当前权限';
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 只将服务端给出的坐标送回当前运行。 */
async function open(value: string) {
  coordinate.value = value;
  page.value = 1;
  dialog.value = true;
  await fetchDrill();
}
/** 钻取页与原始业务库解耦，迟到回包不能污染新运行。 */
async function fetchDrill() {
  const ticket = drillGate.begin();
  busy.value = true;
  drillRows.value = [];
  try {
    const response = await runDrill(props.runId, coordinate.value, (page.value - 1) * 50, 50);
    if (drillGate.current(ticket)) {
      drillRows.value = response.data.rows;
      total.value = response.data.total;
    }
  } catch {
    if (drillGate.current(ticket)) dialog.value = false;
  } finally {
    if (drillGate.current(ticket)) busy.value = false;
  }
}
watch(() => props.runId, load, { immediate: true });
watch(dialog, (value) => {
  if (!value) drillGate.invalidate();
});
onUnmounted(() => {
  gate.invalidate();
  drillGate.invalidate();
});
</script>
<style scoped>
.pivot-wrap {
  overflow: auto;
}
.pivot-table {
  border-collapse: collapse;
  width: 100%;
}
.pivot-table th,
.pivot-table td {
  border: 1px solid var(--el-border-color);
  padding: 10px;
  text-align: left;
}
.subtotal {
  font-weight: 600;
  background: var(--el-fill-color-light);
}
</style>
