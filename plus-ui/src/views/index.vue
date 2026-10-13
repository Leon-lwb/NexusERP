<template>
  <div class="app-container home">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="12" :lg="6" v-for="(item, index) in statCards" :key="index" class="stat-col">
        <el-card class="stat-card" shadow="hover" v-loading="loading">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: item.bgGradient }">
              <svg class="stat-svg-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <!-- 销售额 -->
                <g v-if="item.icon === 'sales'">
                  <path
                    d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </g>
                <!-- 客户 -->
                <g v-if="item.icon === 'customer'">
                  <path
                    d="M17 21v-2a4 4 0 00-4-4H7a4 4 0 00-4 4v2"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="1.8" />
                  <path
                    d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </g>
                <!-- 商品 -->
                <g v-if="item.icon === 'product'">
                  <path
                    d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4H6z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <path d="M3 6h18M16 10a4 4 0 01-8 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </g>
                <!-- 任务 -->
                <g v-if="item.icon === 'task'">
                  <path d="M9 11l3 3L22 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                  <path
                    d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </g>
                <!-- 预警 -->
                <g v-if="item.icon === 'alert'">
                  <path
                    d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <line x1="12" y1="9" x2="12" y2="13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  <line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </g>
              </svg>
            </div>
            <div class="stat-info">
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-value" :style="{ color: item.color }">{{ animatedValues[index] }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="16">
        <el-card shadow="hover" v-loading="chartLoading" class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <div class="header-icon sales-icon">
                  <svg
                    viewBox="0 0 24 24"
                    width="16"
                    height="16"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                  </svg>
                </div>
                <span>销售趋势</span>
              </div>
            </div>
          </template>
          <div ref="salesChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card shadow="hover" v-loading="chartLoading" class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <div class="header-icon product-icon">
                  <svg
                    viewBox="0 0 24 24"
                    width="16"
                    height="16"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4H6z" />
                    <path d="M3 6h18" />
                    <path d="M16 10a4 4 0 01-8 0" />
                  </svg>
                </div>
                <span>订单量趋势（近12个月）</span>
              </div>
            </div>
          </template>
          <div ref="productChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表格区域 -->
    <el-row :gutter="20" class="dashboard-table-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card shadow="hover" class="table-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <div class="header-icon order-icon">
                  <svg
                    viewBox="0 0 24 24"
                    width="16"
                    height="16"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                    <line x1="16" y1="13" x2="8" y2="13" />
                    <line x1="16" y1="17" x2="8" y2="17" />
                  </svg>
                </div>
                <span>最新订单</span>
              </div>
              <el-link type="primary" :underline="false" size="small">
                查看更多
                <svg
                  viewBox="0 0 24 24"
                  width="12"
                  height="12"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  style="margin-left: 2px; vertical-align: -1px"
                >
                  <polyline points="9 18 15 12 9 6" />
                </svg>
              </el-link>
            </div>
          </template>
          <el-table
            :data="latestOrders"
            style="width: 100%"
            max-height="320"
            v-loading="loading"
            :header-cell-style="{ background: '#f8fafc', fontWeight: 600, color: '#475569' }"
          >
            <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip>
              <template #default="scope">
                <span class="order-no">{{ scope.row.orderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="customer" label="客户" min-width="100" show-overflow-tooltip />
            <el-table-column prop="amount" label="金额" min-width="100" align="right">
              <template #default="scope">
                <span class="order-amount">¥{{ formatNumber(scope.row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="80" align="center">
              <template #default="scope">
                <span class="status-badge" :class="getOrderStatusClass(scope.row.orderStatus)">
                  <span class="status-dot"></span>
                  {{ getSoStatusLabel(scope.row.orderStatus) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="140">
              <template #default="scope">
                <span class="time-text">{{ scope.row.createTime }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!latestOrders.length" description="暂无订单数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card shadow="hover" class="table-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <div class="header-icon task-icon">
                  <svg
                    viewBox="0 0 24 24"
                    width="16"
                    height="16"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M9 11l3 3L22 4" />
                    <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
                  </svg>
                </div>
                <span>库存预警</span>
              </div>
            </div>
          </template>
          <div class="task-list">
            <div v-for="alert in stockAlerts" :key="alert.alertId" class="task-item">
              <div class="task-main">
                <div class="task-info">
                  <div class="task-name">{{ alert.materialCode }} {{ alert.materialName }}</div>
                  <div class="task-meta">
                    <span class="task-executor">
                      <svg
                        viewBox="0 0 24 24"
                        width="12"
                        height="12"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      >
                        <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                        <circle cx="12" cy="7" r="4" />
                      </svg>
                      {{ alert.warehouseName || '默认仓库' }}
                    </span>
                    <span class="task-status" :class="getAlertLevelClass(alert.alertLevel)">{{ getAlertLevelLabel(alert.alertLevel) }}</span>
                  </div>
                </div>
                <div class="task-percent">{{ alertWaterLevel(alert) }}%</div>
              </div>
              <div class="task-progress-bar">
                <div
                  class="task-progress-fill"
                  :class="getWaterLevelClass(alertWaterLevel(alert))"
                  :style="{ width: alertWaterLevel(alert) + '%' }"
                ></div>
              </div>
            </div>
          </div>
          <el-empty v-if="!stockAlerts.length" description="暂无库存预警" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Index">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getDashboardStats } from '@/api/erp/dashboard';
import { StockAlertItemVO } from '@/api/erp/dashboard/types';

// ==================== 类型定义 ====================
interface StatCard {
  title: string;
  value: number;
  prefix?: string;
  suffix?: string;
  icon: string;
  bgGradient: string;
  color: string;
  trend?: number;
}

interface OrderItem {
  orderNo: string;
  customer: string;
  amount: number;
  orderStatus: string;
  createTime: string;
}

// ==================== 状态 ====================
const loading = ref(false);
const chartLoading = ref(false);

// ==================== 数据 ====================
const statCards = ref<StatCard[]>([
  {
    title: '本月销售额',
    value: 0,
    prefix: '¥',
    icon: 'sales',
    bgGradient: 'linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%)',
    color: '#2563eb'
  },
  {
    title: '进行中订单',
    value: 0,
    suffix: ' 单',
    icon: 'product',
    bgGradient: 'linear-gradient(135deg, #ffedd5 0%, #fed7aa 100%)',
    color: '#ea580c'
  },
  {
    title: '客户总数',
    value: 0,
    suffix: ' 家',
    icon: 'customer',
    bgGradient: 'linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%)',
    color: '#16a34a'
  },
  {
    title: '库存预警',
    value: 0,
    suffix: ' 项',
    icon: 'alert',
    bgGradient: 'linear-gradient(135deg, #fce7f3 0%, #fbcfe8 100%)',
    color: '#db2777'
  }
]);

const animatedValues = ref<string[]>(statCards.value.map(() => '0'));

const latestOrders = ref<OrderItem[]>([]);

const stockAlerts = ref<StockAlertItemVO[]>([]);

// ==================== 图表 ====================
const salesChartRef = ref<HTMLDivElement | null>(null);
const productChartRef = ref<HTMLDivElement | null>(null);
let salesChart: echarts.ECharts | null = null;
let productChart: echarts.ECharts | null = null;

// ==================== 工具函数 ====================
function formatNumber(num: number): string {
  return num.toLocaleString('zh-CN');
}

// 销售订单状态枚举 → 中文标签/样式
const soStatusMap: Record<string, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'warning' },
  APPROVING: { label: '审批中', cls: 'info' },
  APPROVED: { label: '已审批', cls: 'primary' },
  CONFIRMED: { label: '已确认', cls: 'primary' },
  FULFILLED: { label: '已完成', cls: 'success' },
  CLOSED: { label: '已关闭', cls: 'info' },
  CANCELLED: { label: '已取消', cls: 'danger' }
};

function getSoStatusLabel(status: string) {
  return soStatusMap[status]?.label || status || '-';
}

function getOrderStatusClass(status: string) {
  return soStatusMap[status]?.cls || 'info';
}

// 库存预警级别 → 中文标签/样式
function getAlertLevelLabel(level: string) {
  const map: Record<string, string> = { CRITICAL: '紧急', WARNING: '预警', INFO: '关注' };
  return map[level] || level || '-';
}

function getAlertLevelClass(level: string) {
  const map: Record<string, string> = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info' };
  return map[level] || 'info';
}

// 库存水位: current/safety*100, 上限 100
function alertWaterLevel(alert: StockAlertItemVO): number {
  const safety = Number(alert.safetyStock ?? 0);
  if (safety <= 0) return 100;
  const level = (Number(alert.currentQty ?? 0) / safety) * 100;
  return Math.min(100, Math.max(0, Math.round(level)));
}

function getWaterLevelClass(level: number) {
  if (level <= 50) return 'danger';
  if (level <= 80) return 'warning';
  return 'success';
}

// 数字递增动画
function animateValue(index: number, target: number, duration: number = 1200) {
  const startTime = performance.now();
  const prefix = statCards.value[index].prefix || '';
  const suffix = statCards.value[index].suffix || '';

  function update(currentTime: number) {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    // easeOutQuart 缓动
    const easeProgress = 1 - Math.pow(1 - progress, 4);
    const current = Math.floor(easeProgress * target);
    animatedValues.value[index] = prefix + formatNumber(current) + suffix;

    if (progress < 1) {
      requestAnimationFrame(update);
    }
  }

  requestAnimationFrame(update);
}

function initNumberAnimation() {
  statCards.value.forEach((card, index) => {
    animateValue(index, card.value, 1200);
  });
}

// ==================== 图表配置 ====================
// 近12月销售趋势(后端已补零, 无订单月份为0)
const salesTrendData = ref<{ categories: string[]; data: number[] }>({ categories: [], data: [] });
// 近12月订单量趋势
const orderTrendData = ref<{ categories: string[]; data: number[] }>({ categories: [], data: [] });

function getSalesChartOption(): echarts.EChartsOption {
  const trendData = salesTrendData.value;
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: { color: '#1e293b' },
      extraCssText: 'box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); border-radius: 8px;',
      formatter: (params: any) => {
        const p = params[0];
        return `<div style="font-weight:600;margin-bottom:4px">${p.name}</div>
                <div style="display:flex;align-items:center;gap:6px">
                  <span style="width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
                  <span style="color:#64748b">销售额：</span>
                  <span style="font-weight:700">¥${formatNumber(p.value)}</span>
                </div>`;
      }
    },
    grid: {
      left: '2%',
      right: '3%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.categories,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b', fontSize: 12 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#64748b', fontSize: 12, formatter: (v: number) => (v >= 1000 ? v / 1000 + 'k' : String(v)) }
    },
    series: [
      {
        data: trendData.data,
        type: 'line',
        smooth: true,
        symbolSize: 10,
        symbol: 'circle',
        showSymbol: false,
        itemStyle: {
          color: '#3b82f6',
          borderWidth: 3,
          borderColor: '#fff'
        },
        lineStyle: {
          width: 3,
          shadowColor: 'rgba(59, 130, 246, 0.25)',
          shadowBlur: 12,
          shadowOffsetY: 6
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
            { offset: 0.6, color: 'rgba(59, 130, 246, 0.05)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0)' }
          ])
        },
        emphasis: {
          scale: true,
          itemStyle: {
            shadowBlur: 12,
            shadowColor: 'rgba(59, 130, 246, 0.4)'
          }
        }
      }
    ]
  };
}

function getProductChartOption(): echarts.EChartsOption {
  const colors = ['#3b82f6', '#0ea5e9', '#06b6d4', '#14b8a6', '#10b981', '#3b82f6', '#0ea5e9', '#06b6d4', '#14b8a6', '#10b981', '#3b82f6', '#0ea5e9'];
  const trendData = orderTrendData.value;
  const months = trendData.categories.length ? trendData.categories : Array.from({ length: 12 }, () => '-');
  const counts = trendData.data.length ? trendData.data : Array.from({ length: 12 }, () => 0);
  // 倒序渲染: 最新月份在最上方
  const yCategories = [...months].reverse();
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: { color: '#1e293b' },
      extraCssText: 'box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); border-radius: 8px;',
      formatter: (params: any) => {
        const p = params[0];
        return `<div style="display:flex;align-items:center;gap:6px">
                  <span style="width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
                  <span style="color:#64748b">${p.name}：</span>
                  <span style="font-weight:700">${p.value} 单</span>
                </div>`;
      }
    },
    grid: {
      left: '2%',
      right: '12%',
      bottom: '3%',
      top: '5%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#64748b', fontSize: 12 }
    },
    yAxis: {
      type: 'category',
      data: yCategories,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#475569', fontSize: 12, fontWeight: 500 }
    },
    series: [
      {
        data: [...counts].reverse().map((val, idx) => ({
          value: val,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: colors[idx % colors.length] + 'cc' },
              { offset: 1, color: colors[idx % colors.length] }
            ]),
            borderRadius: [0, 6, 6, 0]
          }
        })),
        type: 'bar',
        barWidth: '50%',
        label: {
          show: true,
          position: 'right',
          color: '#64748b',
          fontSize: 12,
          fontWeight: 600,
          formatter: '{c}'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.1)'
          }
        }
      }
    ]
  };
}

// ==================== 生命周期 ====================
function initCharts() {
  if (salesChartRef.value) {
    salesChart = echarts.init(salesChartRef.value);
    salesChart.setOption(getSalesChartOption());
  }

  if (productChartRef.value) {
    productChart = echarts.init(productChartRef.value);
    productChart.setOption(getProductChartOption());
  }
}

function handleResize() {
  salesChart?.resize();
  productChart?.resize();
}

// 从 /erp/dashboard/stats 拉取真实工作台数据
async function loadDashboardData() {
  loading.value = true;
  chartLoading.value = true;

  try {
    const res = await getDashboardStats();
    const stats = res.data;
    statCards.value[0].value = Number(stats.monthSalesAmount ?? 0);
    statCards.value[1].value = Number(stats.openSalesOrderCount ?? 0);
    statCards.value[2].value = Number(stats.clientCount ?? 0);
    statCards.value[3].value = Number(stats.stockAlertCount ?? 0);

    latestOrders.value = (stats.recentOrders || []).map((o) => ({
      orderNo: o.soNo,
      customer: o.clientName || '-',
      amount: Number(o.totalAmount ?? 0),
      orderStatus: o.orderStatus,
      createTime: o.createTime ? String(o.createTime).replace('T', ' ').slice(0, 16) : '-'
    }));

    stockAlerts.value = stats.stockAlerts || [];

    const trend = stats.salesTrend || [];
    salesTrendData.value = {
      categories: trend.map((p) => p.month),
      data: trend.map((p) => Number(p.amount ?? 0))
    };
    orderTrendData.value = {
      categories: trend.map((p) => p.month),
      data: trend.map((p) => Number(p.orderCount ?? 0))
    };
  } catch (error: any) {
    console.error('首页数据加载失败:', error);
  } finally {
    loading.value = false;
    chartLoading.value = false;

    nextTick(() => {
      initCharts();
      initNumberAnimation();
    });
  }
}

onMounted(() => {
  loadDashboardData();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  salesChart?.dispose();
  productChart?.dispose();
});
</script>

<style scoped>
.home {
  padding-bottom: 20px;
}

.stat-col {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 16px;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(226, 232, 240, 0.6);
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 20px 40px -8px rgba(0, 0, 0, 0.1) !important;
  border-color: rgba(203, 213, 225, 0.8);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 6px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 18px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.stat-svg-icon {
  width: 28px;
  height: 28px;
  color: inherit;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-title {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.1;
  font-family: 'Inter', 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  letter-spacing: -0.5px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  margin-top: 6px;
}

.stat-trend.up {
  color: #16a34a;
}

.stat-trend.down {
  color: #dc2626;
}

.trend-label {
  font-weight: 400;
  color: #94a3b8;
  margin-left: 2px;
}

.chart-row,
.dashboard-table-row {
  margin-top: 4px;
}

.chart-row .el-col,
.dashboard-table-row .el-col {
  margin-bottom: 20px;
}

.chart-card,
.table-card {
  border-radius: 16px !important;
  border: 1px solid rgba(226, 232, 240, 0.6) !important;
  transition: all 0.3s ease;
}

.chart-card:hover,
.table-card:hover {
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06) !important;
}

.chart-container {
  height: 320px;
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #1e293b;
  font-size: 15px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.header-icon.sales-icon {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}
.header-icon.product-icon {
  background: linear-gradient(135deg, #0ea5e9, #38bdf8);
}
.header-icon.order-icon {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}
.header-icon.task-icon {
  background: linear-gradient(135deg, #10b981, #34d399);
}

/* 订单表格 */
.order-no {
  font-family: 'Inter', monospace;
  font-weight: 600;
  color: #334155;
  font-size: 13px;
}

.order-amount {
  font-weight: 700;
  color: #ef4444;
  font-size: 14px;
}

.time-text {
  color: #94a3b8;
  font-size: 13px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-badge.success {
  background: #dcfce7;
  color: #16a34a;
}
.status-badge.success .status-dot {
  background: #22c55e;
}
.status-badge.primary {
  background: #dbeafe;
  color: #2563eb;
}
.status-badge.primary .status-dot {
  background: #3b82f6;
}
.status-badge.warning {
  background: #fef3c7;
  color: #d97706;
}
.status-badge.warning .status-dot {
  background: #f59e0b;
}
.status-badge.info {
  background: #f1f5f9;
  color: #64748b;
}
.status-badge.info .status-dot {
  background: #94a3b8;
}

.status-badge.danger {
  background: #fef2f2;
  color: #dc2626;
}

.status-badge.danger .status-dot {
  background: #dc2626;
}

/* 任务列表 */
.task-list {
  padding: 4px 0;
}

.task-item {
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}

.task-item:last-child {
  border-bottom: none;
}

.task-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-name {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-executor {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.task-status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
}

.task-status.success {
  background: #dcfce7;
  color: #16a34a;
}
.task-status.warning {
  background: #fef3c7;
  color: #d97706;
}
.task-status.info {
  background: #f1f5f9;
  color: #64748b;
}

.task-percent {
  font-size: 16px;
  font-weight: 800;
  color: #334155;
  font-family: 'Inter', sans-serif;
  margin-left: 12px;
}

.task-progress-bar {
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}

.task-progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 1s cubic-bezier(0.4, 0, 0.2, 1);
}

.task-progress-fill.success {
  background: linear-gradient(90deg, #22c55e, #4ade80);
}
.task-progress-fill.primary {
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
}
.task-progress-fill.warning {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}
.task-progress-fill.info {
  background: linear-gradient(90deg, #94a3b8, #cbd5e1);
}
.task-progress-fill.danger {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

:deep(.el-card) {
  border-radius: 12px;
  border: none;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #f1f5f9;
  padding: 14px 20px;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-row-hover-bg-color: #f8fafc;
}

:deep(.el-table th.el-table__cell) {
  font-weight: 600;
  font-size: 13px;
}

:deep(.el-table td.el-table__cell) {
  font-size: 13px;
  padding: 10px 0;
}

:deep(.el-empty) {
  padding: 20px 0;
}
</style>
