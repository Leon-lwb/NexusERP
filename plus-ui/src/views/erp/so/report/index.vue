<template>
  <div class="p-2">
    <el-card shadow="never" class="mb-2">
      <el-form :inline="true">
        <el-form-item label="统计周期">
          <el-input-number v-model="queryMonths" :min="1" :max="60" :precision="0" />
          <span class="ml-1 text-gray-500">个月</span>
        </el-form-item>
        <el-form-item label="排行条数">
          <el-input-number v-model="queryTopN" :min="1" :max="100" :precision="0" />
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
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="rankingChartRef" v-loading="loading" style="height: 400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="SoSalesReport" lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getSalesExecution, getCustomerRanking } from '@/api/erp/so/report';
import type { SalesExecutionRow, CustomerRankingRow } from '@/api/erp/so/report/types';

/** 与后端一致：months ∈ [1,60]（后端 SoSalesReportServiceImpl.MAX_MONTHS）、topN ∈ [1,100] */
const queryMonths = ref(6);
const queryTopN = ref(10);
const loading = ref(false);
const executionChartRef = ref<HTMLElement>();
const rankingChartRef = ref<HTMLElement>();

/**
 * 图表实例与 resize 监听都由组件持有并负责回收。
 * <p>原实现的问题：① 每次查询都 `echarts.init` 一次，同一 DOM 上会不断新建实例
 * （旧实例泄漏、echarts 会告警 "There is a chart instance already initialized"）；
 * ② 每次都 `window.addEventListener('resize', () => chart.resize())` 且从不移除 ——
 * 查询 N 次就有 N 个匿名监听，各自持有图表引用，既是内存泄漏也会让一次 resize 触发 N 次重绘；
 * ③ 组件卸载时不 dispose。</p>
 */
let executionChart: echarts.ECharts | null = null;
let rankingChart: echarts.ECharts | null = null;
let resizeTimer: ReturnType<typeof setTimeout> | null = null;

/** 复用已有实例，避免同一 DOM 重复 init */
function ensureChart(el: HTMLElement | undefined, current: echarts.ECharts | null): echarts.ECharts | null {
  if (!el) return current;
  return current ?? echarts.getInstanceByDom(el) ?? echarts.init(el);
}

/** 两个图表共用一个防抖 resize 处理，只注册一次 */
function handleResize() {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    executionChart?.resize();
    rankingChart?.resize();
  }, 200);
}

const loadData = async () => {
  loading.value = true;
  try {
    const [execRes, rankRes] = await Promise.all([
      getSalesExecution({ months: queryMonths.value }),
      getCustomerRanking({ months: queryMonths.value, topN: queryTopN.value })
    ]);
    await nextTick();
    renderExecutionChart(execRes.data || []);
    renderRankingChart(rankRes.data || []);
  } finally {
    loading.value = false;
  }
};

const renderExecutionChart = (data: SalesExecutionRow[]) => {
  executionChart = ensureChart(executionChartRef.value, executionChart);
  if (!executionChart) return;
  executionChart.setOption(
    {
      title: { text: '销售趋势与完工率' },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any[]) => {
          const month = params[0]?.axisValue ?? '';
          const row = data.find((d) => d.month === month);
          if (!row) return month;
          return [
            `${month}`,
            `订单数：${row.orderCount ?? 0}`,
            `销售额：${formatAmount(row.totalAmount)}`,
            `已完成：${row.completedCount ?? 0}`,
            `完工率：${row.completionRate ?? 0}%`
          ].join('<br/>');
        }
      },
      legend: { data: ['订单数', '销售额', '完工率'] },
      grid: { left: 60, right: 60, bottom: 40, top: 60 },
      xAxis: { type: 'category', data: data.map((d) => d.month) },
      yAxis: [
        { type: 'value', name: '订单数' },
        { type: 'value', name: '销售额' },
        // 完工率是百分数，独立轴并固定 0~100，避免与金额轴量纲混用
        { type: 'value', name: '完工率(%)', min: 0, max: 100 }
      ],
      series: [
        { name: '订单数', type: 'bar', data: data.map((d) => d.orderCount ?? 0) },
        { name: '销售额', type: 'line', yAxisIndex: 1, data: data.map((d) => d.totalAmount ?? 0) },
        {
          name: '完工率',
          type: 'line',
          yAxisIndex: 2,
          smooth: true,
          symbolSize: 6,
          itemStyle: { color: '#67C23A' },
          // 原实现完全没消费后端返回的 completionRate / completedCount，这里补上
          data: data.map((d) => d.completionRate ?? 0)
        }
      ]
    },
    // 每次查询都是新数据，替换而非合并，避免残留上一次的系列
    { notMerge: true }
  );
};

const renderRankingChart = (data: CustomerRankingRow[]) => {
  rankingChart = ensureChart(rankingChartRef.value, rankingChart);
  if (!rankingChart) return;
  rankingChart.setOption(
    {
      title: { text: `客户销售排名 TOP${queryTopN.value}` },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any[]) => {
          const name = params[0]?.axisValue ?? '';
          const row = data.find((d) => d.clientName === name);
          if (!row) return name;
          return [`${name}`, `订单数：${row.orderCount ?? 0}`, `销售额：${formatAmount(row.totalAmount)}`].join('<br/>');
        }
      },
      grid: { left: 60, right: 30, bottom: 90, top: 60 },
      xAxis: { type: 'category', data: data.map((d) => d.clientName ?? '未指定客户'), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: '销售额' },
      series: [{ type: 'bar', data: data.map((d) => d.totalAmount ?? 0), itemStyle: { color: '#E6A23C' } }]
    },
    { notMerge: true }
  );
};

/** 金额千分位展示（报表金额量级较大，便于阅读） */
function formatAmount(value: number | null | undefined) {
  const n = Number(value ?? 0);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
  loadData();
});

onBeforeUnmount(() => {
  // 先解绑再 dispose，避免卸载过程中的 resize 打到已销毁实例上
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
    resizeTimer = null;
  }
  executionChart?.dispose();
  rankingChart?.dispose();
  executionChart = null;
  rankingChart = null;
});
</script>
