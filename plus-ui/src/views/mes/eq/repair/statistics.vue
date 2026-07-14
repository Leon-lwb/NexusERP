<template>
  <div class="p-2">
    <!-- 筛选 -->
    <el-card class="mb-2">
      <el-form label-width="80px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="统计区间">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
                @change="loadStats"
              />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="loadStats">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- KPI 卡片 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">总故障次数</div>
            <div class="stat-value primary">{{ stats.totalFault ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">MTTR(平均维修)</div>
            <div class="stat-value warning">{{ hours(stats.mttrHours) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">MTBF(故障间隔)</div>
            <div class="stat-value success">{{ hours(stats.mtbfHours) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">总停机时长</div>
            <div class="stat-value danger">{{ hours(stats.totalDowntimeHours) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">总维修成本</div>
            <div class="stat-value">¥{{ (stats.totalRepairCost ?? 0).toFixed(0) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span>故障趋势</span></template>
          <div ref="trendRef" style="height: 300px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span>故障类型 Pareto</span></template>
          <div ref="paretoRef" style="height: 300px" />
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="hover" class="mb-2">
      <template #header><span>设备故障 TOP10</span></template>
      <div ref="topRef" style="height: 280px" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesEqRepairStatistics">
import * as echarts from 'echarts';
import { getRepairStatistics } from '@/api/mes/eq/repair';
import type { EqRepairStatisticsVO } from '@/api/mes/eq/repair';

const fmt = (d: Date) => {
  const y = d.getFullYear(),
    m = String(d.getMonth() + 1).padStart(2, '0'),
    day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
};
const defaultRange = () => {
  const end = new Date(),
    start = new Date();
  start.setTime(start.getTime() - 29 * 24 * 3600 * 1000);
  return [fmt(start), fmt(end)];
};

const dateRange = ref<string[]>(defaultRange());
const stats = ref<Partial<EqRepairStatisticsVO>>({});
const trendRef = ref<HTMLElement>();
const paretoRef = ref<HTMLElement>();
const topRef = ref<HTMLElement>();
let trendChart: echarts.ECharts | null = null;
let paretoChart: echarts.ECharts | null = null;
let topChart: echarts.ECharts | null = null;

const hours = (v?: number) => (v == null ? '0h' : v.toFixed(1) + 'h');
const fmtDate = (s?: string) => (s ? String(s).substring(0, 10) : '-');

const loadStats = async () => {
  const [startDate, endDate] = dateRange.value || [];
  const res = await getRepairStatistics({ startDate, endDate });
  stats.value = res.data || {};
  renderTrend();
  renderPareto();
  renderTop();
};

const resetQuery = () => {
  dateRange.value = defaultRange();
  loadStats();
};

const renderTrend = () => {
  nextTick(() => {
    if (!trendRef.value) return;
    trendChart = trendChart || echarts.init(trendRef.value);
    const items = stats.value.trend || [];
    const dates = items.map((t) => fmtDate(t.statDate));
    const faults = items.map((t) => t.faultCount ?? 0);
    const downs = items.map((t) => Number(t.downtimeHours ?? 0));
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['故障次数', '停机时长(h)'], top: 0 },
      grid: { left: 50, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: dates },
      yAxis: [
        { type: 'value', name: '次数' },
        { type: 'value', name: '小时' }
      ],
      series: [
        { name: '故障次数', type: 'bar', data: faults, itemStyle: { color: '#409EFF' } },
        { name: '停机时长(h)', type: 'line', yAxisIndex: 1, data: downs, smooth: true, itemStyle: { color: '#F56C6C' } }
      ]
    });
  });
};

const renderPareto = () => {
  nextTick(() => {
    if (!paretoRef.value) return;
    paretoChart = paretoChart || echarts.init(paretoRef.value);
    const items = stats.value.pareto || [];
    const types = items.map((p) => p.faultType);
    const counts = items.map((p) => p.count ?? 0);
    const ratios = items.map((p) => Number(((p.ratio ?? 0) * 100).toFixed(1)));
    // 累计百分比
    let cumulative = 0;
    const cum = ratios.map((r) => {
      cumulative += r;
      return Math.min(cumulative, 100);
    });
    paretoChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['故障数', '累计占比(%)'], top: 0 },
      grid: { left: 50, right: 50, top: 40, bottom: 40 },
      xAxis: { type: 'category', data: types, axisLabel: { rotate: 30 } },
      yAxis: [
        { type: 'value', name: '次数' },
        { type: 'value', name: '%', max: 100 }
      ],
      series: [
        { name: '故障数', type: 'bar', data: counts, itemStyle: { color: '#409EFF' } },
        { name: '累计占比(%)', type: 'line', yAxisIndex: 1, data: cum, smooth: true, itemStyle: { color: '#E6A23C' } }
      ]
    });
  });
};

const renderTop = () => {
  nextTick(() => {
    if (!topRef.value) return;
    topChart = topChart || echarts.init(topRef.value);
    const items = stats.value.topEquipment || [];
    const names = items.map((t) => t.equipmentName || t.equipmentCode || String(t.equipmentId));
    const faults = items.map((t) => t.faultCount ?? 0);
    const downs = items.map((t) => Number(t.downtimeHours ?? 0));
    topChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['故障次数', '停机(h)'], top: 0 },
      grid: { left: 60, right: 30, top: 40, bottom: 40 },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 30 } },
      yAxis: [
        { type: 'value', name: '次数' },
        { type: 'value', name: '小时' }
      ],
      series: [
        { name: '故障次数', type: 'bar', data: faults, itemStyle: { color: '#409EFF' } },
        { name: '停机(h)', type: 'bar', yAxisIndex: 1, data: downs, itemStyle: { color: '#F56C6C' } }
      ]
    });
  });
};

const handleResize = () => {
  trendChart?.resize();
  paretoChart?.resize();
  topChart?.resize();
};

onMounted(() => {
  loadStats();
  window.addEventListener('resize', handleResize);
});
onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  trendChart?.dispose();
  paretoChart?.dispose();
  topChart?.dispose();
});
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
}
.stat-value.primary {
  color: var(--el-color-primary);
}
.stat-value.success {
  color: var(--el-color-success);
}
.stat-value.danger {
  color: var(--el-color-danger);
}
.stat-value.warning {
  color: var(--el-color-warning);
}
</style>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 0;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
</style>
