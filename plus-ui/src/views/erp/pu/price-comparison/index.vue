<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料选择">
            <el-select
              v-model="selectedMaterialId"
              placeholder="请选择物料"
              filterable
              remote
              :remote-method="searchMaterial"
              :loading="materialLoading"
              style="width: 100%"
              @change="handleMaterialChange"
            >
              <el-option
                v-for="item in materialOptions"
                :key="item.materialId"
                :label="item.materialCode + ' - ' + item.materialName"
                :value="item.materialId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="queryPriceComparison" :disabled="!selectedMaterialId">查询</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            <el-button type="warning" icon="Download" @click="handleExport" :disabled="priceList.length === 0">导出</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 物料信息卡片 -->
    <el-card v-if="selectedMaterial" class="material-info-card" style="margin-bottom: 20px;">
      <template #header>
        <span class="card-title">物料信息</span>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="物料编码">{{ selectedMaterial.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ selectedMaterial.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ selectedMaterial.materialSpec }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ selectedMaterial.categoryName }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 价格对比表格 -->
    <el-card v-if="priceList.length > 0">
      <template #header>
        <div class="card-header">
          <span class="card-title">供应商价格对比</span>
          <el-tag type="info">共 {{ priceList.length }} 家供应商</el-tag>
        </div>
      </template>
      <el-table :data="priceList" border stripeshow-overflow-tooltip>
        <el-table-column label="排名" align="center" type="index" />
        <el-table-column label="供应商编码" prop="vendorCode" />
        <el-table-column label="供应商名称" prop="vendorName" min-width="180" show-overflow-tooltip />
        <el-table-column label="单价" prop="price" align="right">
          <template #default="scope">
            <span :class="{ 'price-lowest': scope.$index === 0 }">
              {{ formatPrice(scope.row.price) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="币种" prop="currency" align="center" />
        <el-table-column label="供货周期(天)" prop="leadTime" align="center" />
        <el-table-column label="最小订货量" prop="minOrderQty" align="right">
          <template #default="scope">
            {{ formatPrice(scope.row.minOrderQty) }}
          </template>
        </el-table-column>
        <!-- 技术债:默认供应商标志为硬编码 el-tag,应补字典后改 dict-tag -->
        <el-table-column label="默认供应商" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.isDefault === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" align="center" />
        <!-- 技术债:状态为硬编码 el-tag,应补字典后改 dict-tag -->
        <el-table-column label="状态" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
              {{ scope.row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 价格统计信息 -->
      <el-row :gutter="20" style="margin-top: 20px;">
        <el-col :span="6">
          <el-statistic title="最低价格" :value="lowestPrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最高价格" :value="highestPrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均价格" :value="averagePrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="价差比例" :value="priceDiffPercent" :precision="2" suffix="%" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 价格对比图表 -->
    <el-row :gutter="20" style="margin-top: 20px;" v-if="priceList.length > 0">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span class="card-title">供应商价格柱状图</span>
          </template>
          <div ref="barChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span class="card-title">价格趋势折线图</span>
          </template>
          <div ref="trendChartRef" style="height: 350px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空状态 -->
    <el-empty v-if="selectedMaterialId && priceList.length === 0 && !loading" description="该物料暂无供应商报价信息" />
  </div>
</template>

<script setup lang="ts" name="PriceComparison">
import { listMaterial } from '@/api/erp/bd/material';
import { listPriceComparison, getPriceTrend } from '@/api/erp/pu/price-comparison';
import * as echarts from 'echarts';

const { proxy } = getCurrentInstance();

// 物料选择相关
const selectedMaterialId = ref<number | undefined>(undefined);
const selectedMaterial = ref<any>(null);
const materialOptions = ref<any[]>([]);
const materialLoading = ref(false);

// 价格数据
const priceList = ref<any[]>([]);
const loading = ref(false);

// 图表引用
const barChartRef = ref<HTMLElement | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);
let barChart: echarts.ECharts | null = null;
let trendChart: echarts.ECharts | null = null;

/** 格式化价格 */
function formatPrice(price: number) {
  if (price === null || price === undefined) return '-';
  return price.toFixed(4);
}

/** 最低价格 */
const lowestPrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  return Math.min(...priceList.value.map((item) => item.price || 0));
});

/** 最高价格 */
const highestPrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  return Math.max(...priceList.value.map((item) => item.price || 0));
});

/** 平均价格 */
const averagePrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  const sum = priceList.value.reduce((acc, item) => acc + (item.price || 0), 0);
  return sum / priceList.value.length;
});

/** 价差比例 */
const priceDiffPercent = computed(() => {
  if (lowestPrice.value === 0) return 0;
  return ((highestPrice.value - lowestPrice.value) / lowestPrice.value) * 100;
});

/** 搜索物料 */
function searchMaterial(query: string) {
  if (query) {
    materialLoading.value = true;
    listMaterial({ materialName: query, pageNum: 1, pageSize: 20 }).then((response) => {
      materialOptions.value = response.rows || [];
      materialLoading.value = false;
    });
  } else {
    materialOptions.value = [];
  }
}

/** 物料选择变化 */
function handleMaterialChange(materialId: number) {
  if (materialId) {
    selectedMaterial.value = materialOptions.value.find((item) => item.materialId === materialId);
    queryPriceComparison();
  } else {
    selectedMaterial.value = null;
    priceList.value = [];
  }
}

/** 查询价格对比 */
function queryPriceComparison() {
  if (!selectedMaterialId.value) return;
  loading.value = true;
  listPriceComparison({ materialId: selectedMaterialId.value })
    .then((response: any) => {
      priceList.value = response.data || [];
      loading.value = false;
      // 数据加载后渲染图表
      nextTick(() => {
        renderBarChart();
        renderTrendChart();
      });
    })
    .catch(() => {
      loading.value = false;
    });
}

/** 渲染价格柱状图 */
function renderBarChart() {
  if (!barChartRef.value || priceList.value.length === 0) return;

  if (!barChart) {
    barChart = echarts.init(barChartRef.value);
  }

  const vendorNames = priceList.value.map((item) => item.vendorName);
  const prices = priceList.value.map((item) => item.price);
  const minPrice = Math.min(...prices);

  barChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0];
        return `${data.name}<br/>单价: ${data.value.toFixed(4)}`;
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: vendorNames,
      axisLabel: { rotate: vendorNames.length > 5 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: (val: number) => val.toFixed(2) }
    },
    series: [
      {
        type: 'bar',
        data: prices.map((price, index) => ({
          value: price,
          itemStyle: {
            color: price === minPrice ? '#67c23a' : '#409eff'
          }
        })),
        barMaxWidth: 50,
        label: {
          show: true,
          position: 'top',
          formatter: (params: any) => params.value.toFixed(2)
        }
      }
    ]
  });
}

/** 渲染价格趋势折线图 */
function renderTrendChart() {
  if (!trendChartRef.value) return;

  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }

  if (!selectedMaterialId.value) return;

  // 查询价格趋势数据
  getPriceTrend(selectedMaterialId.value, 12)
    .then((response: any) => {
      const trendData = response.data || [];

      if (trendData.length === 0) {
        trendChart.setOption({
          title: {
            text: '暂无趋势数据',
            left: 'center',
            top: 'center',
            textStyle: { color: '#999', fontSize: 14 }
          }
        });
        return;
      }

      // 按供应商分组
      const vendorMap = new Map<string, { months: string[]; prices: number[] }>();
      trendData.forEach((item: any) => {
        if (!vendorMap.has(item.vendorName)) {
          vendorMap.set(item.vendorName, { months: [], prices: [] });
        }
        vendorMap.get(item.vendorName)!.months.push(item.month);
        vendorMap.get(item.vendorName)!.prices.push(item.price);
      });

      // 获取所有月份（去重排序）
      const allMonths = [...new Set(trendData.map((item: any) => item.month))].sort();

      const series = Array.from(vendorMap.entries()).map(([vendorName, data]) => ({
        name: vendorName,
        type: 'line' as const,
        data: allMonths.map((month) => {
          const idx = data.months.indexOf(month);
          return idx >= 0 ? data.prices[idx] : null;
        }),
        smooth: true,
        connectNulls: true
      }));

      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0 },
        grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
        xAxis: { type: 'category', data: allMonths, boundaryGap: false },
        yAxis: {
          type: 'value',
          axisLabel: { formatter: (val: number) => val.toFixed(2) }
        },
        series
      });
    })
    .catch(() => {
      trendChart.setOption({
        title: {
          text: '暂无趋势数据',
          left: 'center',
          top: 'center',
          textStyle: { color: '#999', fontSize: 14 }
        }
      });
    });
}

/** 重置查询 */
function resetQuery() {
  selectedMaterialId.value = undefined;
  selectedMaterial.value = null;
  materialOptions.value = [];
  priceList.value = [];
  if (barChart) barChart.clear();
  if (trendChart) trendChart.clear();
}

/** 导出价格对比数据 */
function handleExport() {
  if (priceList.value.length === 0) {
    proxy.$modal.msgWarning('暂无数据可导出');
    return;
  }
  proxy.download(
    'erp/bd/materialVendor/export',
    {
      materialId: selectedMaterialId.value
    },
    `price_comparison_${new Date().getTime()}.xlsx`
  );
}

/** 窗口大小变化时自适应图表 */
function handleResize() {
  barChart?.resize();
  trendChart?.resize();
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  barChart?.dispose();
  trendChart?.dispose();
  barChart = null;
  trendChart = null;
});
</script>

<style scoped>
.material-info-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
}
.price-lowest {
  color: #67c23a;
  font-weight: 600;
}
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
