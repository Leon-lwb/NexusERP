<template>
  <div class="p-2">
    <el-card shadow="never" class="mb-2">
      <el-form :inline="true">
        <el-form-item label="月数">
          <el-input-number v-model="queryMonths" :min="1" :max="24" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" :loading="loading" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="executionChartRef" v-loading="loading" style="height: 400px"></div>
          <el-empty v-if="!loading && !executionData.length" description="暂无采购执行数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="deliveryChartRef" v-loading="loading" style="height: 400px"></div>
          <el-empty v-if="!loading && !deliveryData.length" description="暂无供应商交货数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="PuPurchaseReport" lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getPurchaseExecution, getVendorDeliveryRate } from '@/api/erp/pu/report';

const queryMonths = ref(6);
const loading = ref(false);
const executionData = ref<any[]>([]);
const deliveryData = ref<any[]>([]);
const executionChartRef = ref<HTMLElement>();
const deliveryChartRef = ref<HTMLElement>();

/**
 * FE-ERP-07：图表实例与 resize 监听由组件持有并回收。
 * <p>原实现每次查询都 `echarts.init`（同一 DOM 反复 init，实例泄漏 + echarts 告警）并
 * `window.addEventListener('resize', () => chart.resize())` 且从不移除（查询 N 次就有 N 个匿名监听，
 * 卸载后仍持有已销毁 DOM 引用），且无 loading / 空态。此处按 2026-09-19 销售报表页已修好的写法重写。</p>
 */
let executionChart: echarts.ECharts | null = null;
let deliveryChart: echarts.ECharts | null = null;
let resizeTimer: ReturnType<typeof setTimeout> | null = null;

/** 复用同一 DOM 上的既有实例，避免重复 init */
function ensureChart(el: HTMLElement | undefined, current: echarts.ECharts | null): echarts.ECharts | null {
  if (!el) return current;
  return current ?? echarts.getInstanceByDom(el) ?? echarts.init(el);
}

/** 两个图表共用一个防抖 resize，只注册一次 */
function handleResize() {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    executionChart?.resize();
    deliveryChart?.resize();
  }, 200);
}

async function loadData() {
  loading.value = true;
  try {
    const [execRes, deliveryRes] = await Promise.all([
      getPurchaseExecution({ months: queryMonths.value }),
      getVendorDeliveryRate({ months: queryMonths.value })
    ]);
    executionData.value = execRes.data || [];
    deliveryData.value = deliveryRes.data || [];
    await nextTick();
    renderExecutionChart();
    renderDeliveryChart();
  } catch {
    executionData.value = [];
    deliveryData.value = [];
  } finally {
    loading.value = false;
  }
}

function renderExecutionChart() {
  executionChart = ensureChart(executionChartRef.value, executionChart);
  if (!executionChart) return;
  const data = executionData.value;
  executionChart.setOption(
    {
      title: { text: '采购执行趋势' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['订单数', '完成率(%)'] },
      xAxis: { type: 'category', data: data.map((d) => d.month) },
      yAxis: [
        { type: 'value', name: '订单数' },
        { type: 'value', name: '完成率(%)', max: 100 }
      ],
      series: [
        { name: '订单数', type: 'bar', data: data.map((d) => d.orderCount ?? 0) },
        { name: '完成率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.completionRate ?? 0) }
      ]
    },
    { notMerge: true }
  );
}

function renderDeliveryChart() {
  deliveryChart = ensureChart(deliveryChartRef.value, deliveryChart);
  if (!deliveryChart) return;
  const data = deliveryData.value;
  deliveryChart.setOption(
    {
      title: { text: '供应商交货准时率' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map((d) => d.vendorName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: '准时率(%)', max: 100 },
      series: [{ type: 'bar', data: data.map((d) => d.onTimeRate ?? 0), itemStyle: { color: '#67C23A' } }]
    },
    { notMerge: true }
  );
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
  loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
    resizeTimer = null;
  }
  executionChart?.dispose();
  deliveryChart?.dispose();
  executionChart = null;
  deliveryChart = null;
});
</script>
