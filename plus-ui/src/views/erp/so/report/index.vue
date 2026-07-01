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
          <div ref="executionChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div ref="rankingChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="SoSalesReport" lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getSalesExecution, getCustomerRanking } from '@/api/erp/so/report';

const queryMonths = ref(6);
const executionChartRef = ref();
const rankingChartRef = ref();

const loadData = async () => {
  const [execRes, rankRes] = await Promise.all([
    getSalesExecution({ months: queryMonths.value }),
    getCustomerRanking({ months: queryMonths.value, topN: 10 })
  ]);
  await nextTick();
  renderExecutionChart(execRes.data || []);
  renderRankingChart(rankRes.data || []);
};

const renderExecutionChart = (data: any[]) => {
  const chart = echarts.init(executionChartRef.value);
  chart.setOption({
    title: { text: '销售趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '销售额'] },
    xAxis: { type: 'category', data: data.map((d) => d.month) },
    yAxis: [
      { type: 'value', name: '订单数' },
      { type: 'value', name: '销售额' }
    ],
    series: [
      { name: '订单数', type: 'bar', data: data.map((d) => d.orderCount) },
      { name: '销售额', type: 'line', yAxisIndex: 1, data: data.map((d) => d.totalAmount) }
    ]
  });
  window.addEventListener('resize', () => chart.resize());
};

const renderRankingChart = (data: any[]) => {
  const chart = echarts.init(rankingChartRef.value);
  chart.setOption({
    title: { text: '客户销售排名 TOP10' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d) => d.clientName), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '销售额' },
    series: [{ type: 'bar', data: data.map((d) => d.totalAmount), itemStyle: { color: '#E6A23C' } }]
  });
  window.addEventListener('resize', () => chart.resize());
};

onMounted(() => loadData());
</script>
