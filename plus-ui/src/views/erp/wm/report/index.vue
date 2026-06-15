<template>
  <div class="p-2">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 库存汇总 -->
      <el-tab-pane label="库存汇总" name="summary">
        <el-card shadow="never" class="mb-2">
          <el-form :inline="true">
            <el-form-item label="仓库">
              <el-input v-model="summaryQuery.warehouseId" placeholder="仓库ID" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="loadSummary">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="summaryChartRef" style="height: 400px"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <el-table :data="summaryData" border>
                <el-table-column label="物料分类" prop="categoryName" />
                <el-table-column label="物料数" prop="materialCount" align="right" />
                <el-table-column label="总数量" prop="totalQty" align="right" />
                <el-table-column label="总金额" prop="totalAmount" align="right" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 库龄分析 -->
      <el-tab-pane label="库龄分析" name="aging">
        <el-card shadow="never" class="mb-2">
          <el-form :inline="true">
            <el-form-item label="仓库">
              <el-input v-model="agingQuery.warehouseId" placeholder="仓库ID" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="loadAging">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="agingChartRef" style="height: 400px"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <el-table :data="agingData" border>
                <el-table-column label="库龄区间" prop="agingRange" />
                <el-table-column label="SKU数" prop="skuCount" align="right" />
                <el-table-column label="总数量" prop="totalQty" align="right" />
                <el-table-column label="总金额" prop="totalAmount" align="right" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 库存周转率 -->
      <el-tab-pane label="库存周转率" name="turnover">
        <el-card shadow="never" class="mb-2">
          <el-form :inline="true">
            <el-form-item label="月数">
              <el-input-number v-model="turnoverQuery.months" :min="1" :max="24" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="loadTurnover">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <div ref="turnoverChartRef" style="height: 400px"></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup name="WmInventoryReport" lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getInventorySummary, getInventoryAging, getInventoryTurnover } from '@/api/erp/wm/report';

const activeTab = ref('summary');
const summaryData = ref<any[]>([]);
const agingData = ref<any[]>([]);
const turnoverData = ref<any[]>([]);

const summaryQuery = reactive({ warehouseId: undefined as any });
const agingQuery = reactive({ warehouseId: undefined as any });
const turnoverQuery = reactive({ categoryCode: undefined as any, months: 6 });

const summaryChartRef = ref();
const agingChartRef = ref();
const turnoverChartRef = ref();

const loadSummary = async () => {
  const res = await getInventorySummary(summaryQuery);
  summaryData.value = res.data || [];
  await nextTick();
  renderSummaryChart();
};

const loadAging = async () => {
  const res = await getInventoryAging(agingQuery);
  agingData.value = res.data || [];
  await nextTick();
  renderAgingChart();
};

const loadTurnover = async () => {
  const res = await getInventoryTurnover(turnoverQuery);
  turnoverData.value = res.data || [];
  await nextTick();
  renderTurnoverChart();
};

const renderSummaryChart = () => {
  const chart = echarts.init(summaryChartRef.value);
  chart.setOption({
    title: { text: '库存金额分布（按分类）' },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: summaryData.value.map((d: any) => ({ name: d.categoryName, value: d.totalAmount })),
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } }
    }]
  });
  window.addEventListener('resize', () => chart.resize());
};

const renderAgingChart = () => {
  const chart = echarts.init(agingChartRef.value);
  chart.setOption({
    title: { text: '库龄分布' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: agingData.value.map((d: any) => d.agingRange) },
    yAxis: { type: 'value', name: '金额' },
    series: [{ type: 'bar', data: agingData.value.map((d: any) => d.totalAmount), itemStyle: { color: '#409EFF' } }]
  });
  window.addEventListener('resize', () => chart.resize());
};

const renderTurnoverChart = () => {
  const months = [...new Set(turnoverData.value.map((d: any) => d.month))].sort();
  const categories = [...new Set(turnoverData.value.map((d: any) => d.categoryName))];
  const chart = echarts.init(turnoverChartRef.value);
  chart.setOption({
    title: { text: '库存周转率趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: categories },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', name: '周转率' },
    series: categories.map(cat => ({
      name: cat, type: 'line',
      data: months.map(m => {
        const item = turnoverData.value.find((d: any) => d.month === m && d.categoryName === cat);
        return item ? item.turnoverRate : 0;
      })
    }))
  });
  window.addEventListener('resize', () => chart.resize());
};

onMounted(() => {
  loadSummary();
});
</script>
