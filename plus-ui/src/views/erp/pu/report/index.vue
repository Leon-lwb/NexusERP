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
          <div ref="deliveryChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="PuPurchaseReport" lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getPurchaseExecution, getVendorDeliveryRate } from '@/api/erp/pu/report';

const queryMonths = ref(6);
const executionChartRef = ref();
const deliveryChartRef = ref();

const loadData = async () => {
  const [execRes, deliveryRes] = await Promise.all([
    getPurchaseExecution({ months: queryMonths.value }),
    getVendorDeliveryRate({ months: queryMonths.value })
  ]);
  await nextTick();
  renderExecutionChart(execRes.data || []);
  renderDeliveryChart(deliveryRes.data || []);
};

const renderExecutionChart = (data: any[]) => {
  const chart = echarts.init(executionChartRef.value);
  chart.setOption({
    title: { text: '采购执行趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '完成率(%)'] },
    xAxis: { type: 'category', data: data.map((d) => d.month) },
    yAxis: [
      { type: 'value', name: '订单数' },
      { type: 'value', name: '完成率(%)', max: 100 }
    ],
    series: [
      { name: '订单数', type: 'bar', data: data.map((d) => d.orderCount) },
      { name: '完成率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.completionRate) }
    ]
  });
  window.addEventListener('resize', () => chart.resize());
};

const renderDeliveryChart = (data: any[]) => {
  const chart = echarts.init(deliveryChartRef.value);
  chart.setOption({
    title: { text: '供应商交货准时率' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d) => d.vendorName), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '准时率(%)', max: 100 },
    series: [{ type: 'bar', data: data.map((d) => d.onTimeRate), itemStyle: { color: '#67C23A' } }]
  });
  window.addEventListener('resize', () => chart.resize());
};

onMounted(() => loadData());
</script>
