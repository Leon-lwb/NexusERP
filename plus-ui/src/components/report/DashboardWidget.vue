<template>
  <el-card class="dashboard-widget" shadow="never"
    ><template #header
      ><div class="flex justify-between">
        <strong>{{ view.title }}</strong
        ><el-tag v-if="view.error" type="danger">组件不可用</el-tag>
      </div></template
    ><el-alert v-if="view.error" :title="'读取失败：' + view.error" type="error" :closable="false" /><template v-else
      ><div v-if="view.type === 'METRIC'" class="metric-value">
        {{ metric.value?.value ?? '—'
        }}<small v-if="metric.rate">变化率：{{ metric.rate.value === null ? '基期为零或空，未定义' : metric.rate.value + '%' }}</small>
      </div>
      <div v-else-if="['BAR', 'LINE', 'PIE'].includes(view.type)" class="widget-chart">
        <el-empty v-if="!chartData?.points.length" description="当前范围暂无图表数据" :image-size="64" />
        <div v-else ref="chartElement" class="chart" />
      </div>
      <TypedTable v-else-if="view.type === 'TABLE'" :columns="table.columns || []" :rows="table.rows || []" />
      <div v-else-if="view.type === 'PIVOT'" class="pivot-inline">
        <table>
          <tbody>
            <tr v-for="(row, index) in pivot.rows || []" :key="index">
              <th>{{ row.keys.map(displayKey).join(' / ') }}</th>
              <td v-for="(cell, c) in row.cells" :key="c">{{ cell.values.map((value) => value.value ?? '—').join(' / ') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="widget-meta">
        {{ view.datasetCode }} · {{ formatReportDateTime(view.dataTime) }} ·
        {{ view.consistency === 'SNAPSHOT' ? '固定快照' : view.consistency === 'LIVE' ? '实时分页' : view.consistency
        }}<span v-if="view.type === 'TABLE'"> · 预览{{ table.visibleRows }}行 / 共{{ view.total }}行</span>
      </div>
      <div v-if="chartData" class="widget-meta">坐标比例：{{ chartData.scale }}，悬浮查看精确值</div></template
    ></el-card
  >
</template>
<script setup lang="ts">
/** 组件使用服务器精确结果；图表投影和HTML模板均由代码固定。 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import type { DashboardView } from '@/api/report/dashboard';
import type { TypedValue, PageData, PivotResult } from '@/types/report';
import { chartOption, type ChartData } from '@/views/report/dashboard/model';
import { formatDimension } from '@/views/report/model';
import { formatReportDateTime } from '@/views/report/time';
import TypedTable from './TypedTable.vue';
/** 分类显示字典名称，数字维度保留完整原值。 */
function displayKey(value: TypedValue) {
  return formatDimension(value);
}
/**
 * 保存当前页面或测试的props、emit、chartElement状态。
 */
const props = defineProps<{
    /**
     * 当前契约的view配置或状态
     */
    view: DashboardView;
  }>(),
  emit = defineEmits<{
    /**
     * 当前契约的select配置或状态
     */
    select: [TypedValue];
  }>(),
  chartElement = ref<HTMLElement>();
/**
 * 保存当前页面或测试的chart、observer、serial状态。
 */
/** 主题切换时重绘图表样式，不重新获取或计算业务值。 */
let themeObserver: MutationObserver | undefined;
/** 图表实例和尺寸观察器随组件销毁。 */
let chart: echarts.ECharts | undefined,
  observer: ResizeObserver | undefined,
  serial = 0;
/**
 * 保存当前页面或测试的metric、table、pivot、chartData状态。
 */
const metric = computed(
    () =>
      (props.view.data || {}) as {
        /**
         * 当前契约的value配置或状态
         */
        value?: TypedValue; /**
         * 当前契约的rate配置或状态
         */
        rate?: TypedValue;
      }
  ),
  table = computed(
    () =>
      (props.view.data || {}) as PageData & {
        /**
         * 当前契约的visibleRows配置或状态
         */
        visibleRows?: number;
      }
  ),
  pivot = computed(() => (props.view.data || {}) as PivotResult),
  chartData = computed(() => (['BAR', 'LINE', 'PIE'].includes(props.view.type) ? (props.view.data as ChartData | null) : null));
/** 重新创建已授权图表，旧实例和监听器不会遗留。 */
async function draw() {
  const ticket = ++serial;
  await nextTick();
  if (ticket !== serial) return;
  chart?.dispose();
  chart = undefined;
  observer?.disconnect();
  observer = undefined;
  if (!chartElement.value || !chartData.value || props.view.error) return;
  chart = echarts.init(chartElement.value, document.documentElement.classList.contains('dark') ? 'dark' : undefined);
  chart.setOption({ ...chartOption(props.view.type, chartData.value), backgroundColor: 'transparent' });
  chart.on('click', (event: unknown) => {
    const index = (
      event as {
        /**
         * 当前契约的dataIndex配置或状态
         */
        dataIndex: number;
      }
    ).dataIndex;
    const point = chartData.value?.points[index];
    if (point) emit('select', point.category);
  });
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(chartElement.value);
}
watch(() => props.view, draw, { deep: true });
onMounted(() => {
  void draw();
  if (typeof MutationObserver !== 'undefined' && document.documentElement) {
    themeObserver = new MutationObserver(() => void draw());
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
  }
});
onUnmounted(() => {
  serial++;
  observer?.disconnect();
  themeObserver?.disconnect();
  chart?.dispose();
});
</script>
<style scoped>
.dashboard-widget {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-top: 3px solid var(--el-color-primary-light-7);
}
.dashboard-widget :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
}
.dashboard-widget :deep(.el-card__header) {
  flex-shrink: 0;
}
.widget-chart {
  flex: 1;
  min-height: 120px;
}
.chart {
  height: 100%;
  min-height: 120px;
}
.metric-value {
  font-size: 30px;
  font-weight: 650;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.metric-value small {
  display: block;
  font-size: 14px;
  font-weight: 400;
  margin-top: 10px;
}
.widget-meta {
  flex-shrink: 0;
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}
.pivot-inline {
  overflow: auto;
}
.pivot-inline td,
.pivot-inline th {
  border: 1px solid var(--el-border-color);
  padding: 8px;
}
</style>
