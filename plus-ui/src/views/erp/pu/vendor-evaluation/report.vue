<template>
  <div class="app-container">
    <el-form :inline="true">
      <el-form-item label="供应商">
        <el-select v-model="selectedVendor" filterable remote :remote-method="searchVendor" placeholder="请选择供应商" clearable @change="loadReport" style="width: 250px;">
          <el-option v-for="v in vendorOptions" :key="v.vendorId" :label="v.vendorName" :value="v.vendorId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadReport">查询</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="评估雷达图">
          <div ref="radarChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="评估趋势">
          <div ref="trendChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card header="评估等级分布" style="margin-top: 20px;">
      <div ref="pieChartRef" style="height: 300px;"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="VendorEvaluationReport">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getVendorEvaluationTrend, getVendorEvaluationDistribution } from '@/api/erp/pu/vendor-evaluation';
import { listVendor } from '@/api/erp/bd/vendor';

const selectedVendor = ref<number>();
const vendorOptions = ref<any[]>([]);

const radarChartRef = ref<HTMLDivElement>();
const trendChartRef = ref<HTMLDivElement>();
const pieChartRef = ref<HTMLDivElement>();

let radarChart: echarts.ECharts | null = null;
let trendChart: echarts.ECharts | null = null;
let pieChart: echarts.ECharts | null = null;

const trendData = ref<any[]>([]);

const searchVendor = async (query: string) => {
  if (query) {
    const res = await listVendor({ vendorName: query, pageNum: 1, pageSize: 20 });
    vendorOptions.value = res.rows || [];
  } else {
    const res = await listVendor({ pageNum: 1, pageSize: 20 });
    vendorOptions.value = res.rows || [];
  }
};

const loadReport = async () => {
  await loadDistribution();
  if (selectedVendor.value) {
    await loadTrend(selectedVendor.value);
  } else {
    clearTrend();
    clearRadar();
  }
};

const loadTrend = async (vendorId: number) => {
  const res = await getVendorEvaluationTrend(vendorId);
  trendData.value = res.data || [];
  renderRadarChart();
  renderTrendChart();
};

const loadDistribution = async () => {
  const res = await getVendorEvaluationDistribution();
  renderPieChart(res.data || {});
};

const renderRadarChart = () => {
  if (!radarChartRef.value) return;
  if (!radarChart) {
    radarChart = echarts.init(radarChartRef.value);
  }
  const latest = trendData.value.length > 0 ? trendData.value[trendData.value.length - 1] : null;
  if (!latest) {
    radarChart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] });
    return;
  }
  radarChart.setOption({
    tooltip: {},
    radar: {
      indicator: [
        { name: '交货及时率', max: 100 },
        { name: '质量合格率', max: 100 },
        { name: '价格竞争力', max: 100 }
      ]
    },
    series: [{
      type: 'radar',
      data: [{
        value: [latest.deliveryRate || 0, latest.qualityRate || 0, latest.priceScore || 0],
        name: latest.evalPeriod || '',
        areaStyle: { opacity: 0.2 }
      }]
    }]
  });
};

const renderTrendChart = () => {
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }
  if (trendData.value.length === 0) {
    trendChart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] });
    return;
  }
  const periods = trendData.value.map((d: any) => d.evalPeriod);
  const scores = trendData.value.map((d: any) => d.totalScore);
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: periods },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{
      type: 'line',
      data: scores,
      smooth: true,
      areaStyle: { opacity: 0.1 },
      markLine: { data: [{ yAxis: 90, name: 'A级' }, { yAxis: 80, name: 'B级' }, { yAxis: 60, name: 'C级' }] }
    }]
  });
};

const renderPieChart = (data: Record<string, number>) => {
  if (!pieChartRef.value) return;
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value);
  }
  const pieData = Object.entries(data).map(([name, value]) => ({ name: name + '级', value }));
  const colorMap: Record<string, string> = { 'A级': '#67c23a', 'B级': '#409eff', 'C级': '#e6a23c', 'D级': '#f56c6c' };
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: pieData,
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } },
      itemStyle: {
        color: (params: any) => colorMap[params.name] || '#909399'
      }
    }]
  });
};

const clearTrend = () => {
  if (trendChart) {
    trendChart.setOption({ title: { text: '请选择供应商', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] });
  }
};

const clearRadar = () => {
  if (radarChart) {
    radarChart.setOption({ title: { text: '请选择供应商', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] });
  }
};

const handleResize = () => {
  radarChart?.resize();
  trendChart?.resize();
  pieChart?.resize();
};

onMounted(async () => {
  await searchVendor('');
  await loadDistribution();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  radarChart?.dispose();
  trendChart?.dispose();
  pieChart?.dispose();
});
</script>
