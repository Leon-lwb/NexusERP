<template>
  <div class="p-2">
    <el-card shadow="never" class="mb-2">
      <el-form :inline="true">
        <el-form-item label="月数">
          <el-input-number v-model="queryMonths" :min="1" :max="24" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="completionChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="efficiencyChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="PrdProductionReport" lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getWorkOrderCompletionRate, getProductionEfficiency } from '@/api/erp/prd/report';

const queryMonths = ref(6);
const completionChartRef = ref();
const efficiencyChartRef = ref();

const loadData = async () => {
  const [compRes, effRes] = await Promise.all([
    getWorkOrderCompletionRate({ months: queryMonths.value }),
    getProductionEfficiency({ months: queryMonths.value })
  ]);
  await nextTick();
  renderCompletionChart(compRes.data || []);
  renderEfficiencyChart(effRes.data || []);
};

const renderCompletionChart = (data: any[]) => {
  const chart = echarts.init(completionChartRef.value);
  chart.setOption({
    title: { text: '工单完工率' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['总工单', '已完工', '完工率(%)'] },
    xAxis: { type: 'category', data: data.map((d) => d.month) },
    yAxis: [
      { type: 'value', name: '工单数' },
      { type: 'value', name: '完工率(%)', max: 100 }
    ],
    series: [
      { name: '总工单', type: 'bar', data: data.map((d) => d.totalOrders) },
      { name: '已完工', type: 'bar', data: data.map((d) => d.completedOrders) },
      { name: '完工率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.completionRate) }
    ]
  });
  window.addEventListener('resize', () => chart.resize());
};

const renderEfficiencyChart = (data: any[]) => {
  const chart = echarts.init(efficiencyChartRef.value);
  chart.setOption({
    title: { text: '计划 vs 实际完工' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['计划数量', '实际数量', '效率(%)'] },
    xAxis: { type: 'category', data: data.map((d) => d.month) },
    yAxis: [
      { type: 'value', name: '数量' },
      { type: 'value', name: '效率(%)', max: 150 }
    ],
    series: [
      { name: '计划数量', type: 'bar', data: data.map((d) => d.plannedQty) },
      { name: '实际数量', type: 'bar', data: data.map((d) => d.completedQty) },
      { name: '效率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.efficiencyRate) }
    ]
  });
  window.addEventListener('resize', () => chart.resize());
};

onMounted(() => loadData());
</script>
