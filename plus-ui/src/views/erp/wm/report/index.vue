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
              <el-button type="primary" icon="Search" :loading="loading" @click="loadSummary">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="summaryChartRef" v-loading="loading" style="height: 400px"></div>
              <el-empty v-if="!loading && !summaryData.length" description="暂无库存汇总数据" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <!-- 原写法 `bordershow-overflow-tooltip` 缺空格，两个属性都没生效，一并修正 -->
              <el-table :data="summaryData" border show-overflow-tooltip>
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
              <el-button type="primary" icon="Search" :loading="loading" @click="loadAging">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="agingChartRef" v-loading="loading" style="height: 400px"></div>
              <el-empty v-if="!loading && !agingData.length" description="暂无库龄数据" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <el-table :data="agingData" border show-overflow-tooltip>
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
              <el-button type="primary" icon="Search" :loading="loading" @click="loadTurnover">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <div ref="turnoverChartRef" v-loading="loading" style="height: 400px"></div>
          <el-empty v-if="!loading && !turnoverData.length" description="暂无周转率数据" :image-size="60" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup name="WmInventoryReport" lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import * as echarts from 'echarts';
import { getInventorySummary, getInventoryAging, getInventoryTurnover } from '@/api/erp/wm/report';

const activeTab = ref('summary');
const loading = ref(false);
const summaryData = ref<any[]>([]);
const agingData = ref<any[]>([]);
const turnoverData = ref<any[]>([]);

const summaryQuery = reactive({ warehouseId: undefined as any });
const agingQuery = reactive({ warehouseId: undefined as any });
const turnoverQuery = reactive({ categoryCode: undefined as any, months: 6 });

const summaryChartRef = ref<HTMLElement>();
const agingChartRef = ref<HTMLElement>();
const turnoverChartRef = ref<HTMLElement>();

/**
 * FE-ERP-07：三个图表实例统一由组件持有、复用并回收。
 * <p>原实现每次查询都 `echarts.init`（同一 DOM 反复 init → 实例泄漏 + echarts 告警），
 * 每次都 `window.addEventListener('resize', ...)` 且从不 `removeEventListener`（卸载后仍持有 DOM 引用），
 * 组件卸载也不 dispose，且无 loading / 空态。</p>
 */
let summaryChart: echarts.ECharts | null = null;
let agingChart: echarts.ECharts | null = null;
let turnoverChart: echarts.ECharts | null = null;
let resizeTimer: ReturnType<typeof setTimeout> | null = null;

function ensureChart(el: HTMLElement | undefined, current: echarts.ECharts | null): echarts.ECharts | null {
  if (!el) return current;
  return current ?? echarts.getInstanceByDom(el) ?? echarts.init(el);
}

/** 三个图表共用一个防抖 resize，只注册一次 */
function handleResize() {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    summaryChart?.resize();
    agingChart?.resize();
    turnoverChart?.resize();
  }, 200);
}

async function loadSummary() {
  loading.value = true;
  try {
    const res = await getInventorySummary(summaryQuery);
    summaryData.value = res.data || [];
    await nextTick();
    renderSummaryChart();
  } catch {
    summaryData.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadAging() {
  loading.value = true;
  try {
    const res = await getInventoryAging(agingQuery);
    agingData.value = res.data || [];
    await nextTick();
    renderAgingChart();
  } catch {
    agingData.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadTurnover() {
  loading.value = true;
  try {
    const res = await getInventoryTurnover(turnoverQuery);
    turnoverData.value = res.data || [];
    await nextTick();
    renderTurnoverChart();
  } catch {
    turnoverData.value = [];
  } finally {
    loading.value = false;
  }
}

function renderSummaryChart() {
  summaryChart = ensureChart(summaryChartRef.value, summaryChart);
  if (!summaryChart) return;
  summaryChart.setOption(
    {
      title: { text: '库存金额分布（按分类）' },
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          data: summaryData.value.map((d: any) => ({ name: d.categoryName, value: d.totalAmount })),
          emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } }
        }
      ]
    },
    { notMerge: true }
  );
}

function renderAgingChart() {
  agingChart = ensureChart(agingChartRef.value, agingChart);
  if (!agingChart) return;
  agingChart.setOption(
    {
      title: { text: '库龄分布' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: agingData.value.map((d: any) => d.agingRange) },
      yAxis: { type: 'value', name: '金额' },
      series: [{ type: 'bar', data: agingData.value.map((d: any) => d.totalAmount), itemStyle: { color: '#409EFF' } }]
    },
    { notMerge: true }
  );
}

function renderTurnoverChart() {
  turnoverChart = ensureChart(turnoverChartRef.value, turnoverChart);
  if (!turnoverChart) return;
  const months = [...new Set(turnoverData.value.map((d: any) => d.month))].sort();
  const categories = [...new Set(turnoverData.value.map((d: any) => d.categoryName))];
  turnoverChart.setOption(
    {
      title: { text: '库存周转率趋势' },
      tooltip: { trigger: 'axis' },
      legend: { data: categories },
      xAxis: { type: 'category', data: months },
      yAxis: { type: 'value', name: '周转率' },
      series: categories.map((cat) => ({
        name: cat,
        type: 'line',
        data: months.map((m) => {
          const item = turnoverData.value.find((d: any) => d.month === m && d.categoryName === cat);
          return item ? item.turnoverRate : 0;
        })
      }))
    },
    { notMerge: true }
  );
}

/** 非激活 Tab 的容器宽度为 0，init 会得到 0 宽图表，因此切到该 Tab 时才补一次渲染 */
watch(activeTab, (tab) => {
  nextTick(() => {
    if (tab === 'summary') renderSummaryChart();
    if (tab === 'aging') renderAgingChart();
    if (tab === 'turnover') renderTurnoverChart();
  });
});

onMounted(() => {
  window.addEventListener('resize', handleResize);
  loadSummary();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
    resizeTimer = null;
  }
  summaryChart?.dispose();
  agingChart?.dispose();
  turnoverChart?.dispose();
  summaryChart = null;
  agingChart = null;
  turnoverChart = null;
});
</script>
