<template>
  <div class="app-container home">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col
        :xs="24"
        :sm="12"
        :md="12"
        :lg="6"
        v-for="(item, index) in statCards"
        :key="index"
        class="stat-col"
      >
        <el-card class="stat-card" shadow="hover" v-loading="loading">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: item.bgColor }">
              <i :class="item.icon" :style="{ fontSize: '28px', color: item.color }"></i>
            </div>
            <div class="stat-info">
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-value">{{ animatedValues[index] }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="16">
        <el-card shadow="hover" v-loading="chartLoading">
          <template #header>
            <div class="card-header">
              <span>销售趋势</span>
              <el-tag type="success" effect="plain">本年度</el-tag>
            </div>
          </template>
          <div ref="salesChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card shadow="hover" v-loading="chartLoading">
          <template #header>
            <div class="card-header">
              <span>热门商品排行</span>
            </div>
          </template>
          <div ref="productChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表格区域 -->
    <el-row :gutter="20" class="table-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最新订单</span>
              <el-button text type="primary" size="small">查看更多</el-button>
            </div>
          </template>
          <el-table
            :data="latestOrders"
            style="width: 100%"
            max-height="320"
            v-loading="loading"
          >
            <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="customer" label="客户" min-width="100" show-overflow-tooltip />
            <el-table-column prop="amount" label="金额" min-width="100" align="right">
              <template #default="scope">
                <span class="order-amount">¥{{ formatNumber(scope.row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="80" align="center">
              <template #default="scope">
                <el-tag :type="getOrderStatusType(scope.row.status)" size="small" effect="light">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="140" />
          </el-table>
          <el-empty v-if="!latestOrders.length" description="暂无订单数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>任务进度</span>
              <el-button text type="primary" size="small">查看全部</el-button>
            </div>
          </template>
          <el-table
            :data="tasks"
            style="width: 100%"
            max-height="320"
            v-loading="loading"
          >
            <el-table-column prop="name" label="任务名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="executor" label="执行人" min-width="80" align="center" />
            <el-table-column prop="progress" label="进度" min-width="140">
              <template #default="scope">
                <el-progress
                  :percentage="scope.row.progress"
                  :status="scope.row.progress === 100 ? 'success' : undefined"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="80" align="center">
              <template #default="scope">
                <el-tag :type="getTaskStatusType(scope.row.status)" size="small" effect="light">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!tasks.length" description="暂无任务数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Index">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

// ==================== 类型定义 ====================
interface StatCard {
  title: string
  value: number
  prefix?: string
  suffix?: string
  icon: string
  bgColor: string
  color: string
}

interface OrderItem {
  orderNo: string
  customer: string
  amount: number
  status: string
  createTime: string
}

interface TaskItem {
  name: string
  executor: string
  progress: number
  status: string
}

// ==================== 状态 ====================
const loading = ref(false)
const chartLoading = ref(false)

// ==================== 数据 ====================
const statCards = ref<StatCard[]>([
  { title: '总销售额', value: 128560, prefix: '¥', icon: 'el-icon-s-order', bgColor: '#e3f2fd', color: '#1565c0' },
  { title: '客户数量', value: 1248, suffix: ' 家', icon: 'el-icon-user', bgColor: '#e8f5e9', color: '#2e7d32' },
  { title: '商品数量', value: 3652, suffix: ' 件', icon: 'el-icon-goods', bgColor: '#fff3e0', color: '#ef6c00' },
  { title: '待办任务', value: 18, suffix: ' 个', icon: 'el-icon-s-flag', bgColor: '#fce4ec', color: '#ad1457' }
])

const animatedValues = ref<string[]>(statCards.value.map(() => '0'))

const latestOrders = ref<OrderItem[]>([
  { orderNo: 'NO20230801001', customer: '阿里巴巴', amount: 12800, status: '已完成', createTime: '2023-08-01 14:30' },
  { orderNo: 'NO20230801002', customer: '腾讯科技', amount: 8600, status: '已发货', createTime: '2023-08-01 11:20' },
  { orderNo: 'NO20230801003', customer: '百度公司', amount: 15200, status: '待发货', createTime: '2023-08-01 09:45' },
  { orderNo: 'NO20230801004', customer: '字节跳动', amount: 6800, status: '已付款', createTime: '2023-08-01 08:15' },
  { orderNo: 'NO20230731001', customer: '京东商城', amount: 21500, status: '已完成', createTime: '2023-07-31 17:30' }
])

const tasks = ref<TaskItem[]>([
  { name: '完成年度销售报告', executor: '张三', progress: 100, status: '已完成' },
  { name: '客户回访计划', executor: '李四', progress: 80, status: '进行中' },
  { name: '新产品上线准备', executor: '王五', progress: 60, status: '进行中' },
  { name: '供应商资质审核', executor: '赵六', progress: 30, status: '进行中' },
  { name: '系统安全检测', executor: '钱七', progress: 0, status: '未开始' }
])

// ==================== 图表 ====================
const salesChartRef = ref<HTMLDivElement | null>(null)
const productChartRef = ref<HTMLDivElement | null>(null)
let salesChart: echarts.ECharts | null = null
let productChart: echarts.ECharts | null = null

// ==================== 工具函数 ====================
function formatNumber(num: number): string {
  return num.toLocaleString('zh-CN')
}

const orderStatusMap: Record<string, any> = {
  '已完成': 'success',
  '已发货': 'primary',
  '待发货': 'warning',
  '已付款': 'info'
}

const taskStatusMap: Record<string, any> = {
  '已完成': 'success',
  '进行中': 'warning',
  '未开始': 'info'
}

function getOrderStatusType(status: string) {
  return orderStatusMap[status] || 'info'
}

function getTaskStatusType(status: string) {
  return taskStatusMap[status] || 'info'
}

// 数字递增动画
function animateValue(index: number, target: number, duration: number = 1200) {
  const startTime = performance.now()
  const prefix = statCards.value[index].prefix || ''
  const suffix = statCards.value[index].suffix || ''

  function update(currentTime: number) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    // easeOutQuart 缓动
    const easeProgress = 1 - Math.pow(1 - progress, 4)
    const current = Math.floor(easeProgress * target)
    animatedValues.value[index] = prefix + formatNumber(current) + suffix

    if (progress < 1) {
      requestAnimationFrame(update)
    }
  }

  requestAnimationFrame(update)
}

function initNumberAnimation() {
  statCards.value.forEach((card, index) => {
    animateValue(index, card.value, 1200)
  })
}

// ==================== 图表配置 ====================
function getSalesChartOption(): echarts.EChartsOption {
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e4e7ed',
      borderWidth: 1,
      textStyle: { color: '#303133' },
      extraCssText: 'box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);'
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
      data: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月'],
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#ebeef5' } },
      axisLabel: { color: '#606266' }
    },
    series: [{
      data: [1200, 1900, 1500, 2500, 1800, 2200, 2800, 3500],
      type: 'line',
      smooth: true,
      symbolSize: 8,
      itemStyle: { color: '#667eea' },
      lineStyle: {
        width: 3,
        shadowColor: 'rgba(102, 126, 234, 0.3)',
        shadowBlur: 10
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(102, 126, 234, 0.3)' },
          { offset: 1, color: 'rgba(102, 126, 234, 0.02)' }
        ])
      }
    }]
  }
}

function getProductChartOption(): echarts.EChartsOption {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e4e7ed',
      borderWidth: 1,
      textStyle: { color: '#303133' },
      extraCssText: 'box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);'
    },
    grid: {
      left: '2%',
      right: '8%',
      bottom: '3%',
      top: '5%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#ebeef5' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'category',
      data: ['商品E', '商品D', '商品C', '商品B', '商品A'],
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' }
    },
    series: [{
      data: [100, 120, 150, 180, 220],
      type: 'bar',
      barWidth: '55%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#4facfe' },
          { offset: 1, color: '#00f2fe' }
        ]),
        borderRadius: [0, 4, 4, 0]
      }
    }]
  }
}

// ==================== 生命周期 ====================
function initCharts() {
  if (salesChartRef.value) {
    salesChart = echarts.init(salesChartRef.value)
    salesChart.setOption(getSalesChartOption())
  }

  if (productChartRef.value) {
    productChart = echarts.init(productChartRef.value)
    productChart.setOption(getProductChartOption())
  }
}

function handleResize() {
  salesChart?.resize()
  productChart?.resize()
}

// 模拟数据加载（预留 API 接入结构）
async function loadDashboardData() {
  loading.value = true
  chartLoading.value = true

  try {
    // TODO: 替换为实际 API 调用
    // const [statsRes, ordersRes, tasksRes] = await Promise.all([
    //   getDashboardStats(),
    //   getLatestOrders(),
    //   getTaskList()
    // ])

    // 模拟网络延迟
    await new Promise(resolve => setTimeout(resolve, 500))

    // 数据已在响应式变量中初始化，实际项目中替换为接口返回值
  } catch (error) {
    console.error('首页数据加载失败:', error)
  } finally {
    loading.value = false
    chartLoading.value = false

    nextTick(() => {
      initCharts()
      initNumberAnimation()
    })
  }
}

onMounted(() => {
  loadDashboardData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  salesChart?.dispose()
  productChart?.dispose()
})
</script>

<style scoped>
.home {
  padding-bottom: 20px;
}

.stat-col {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08) !important;
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 4px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-title {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1f2d3d;
  line-height: 1;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.chart-row,
.table-row {
  margin-top: 4px;
}

.chart-row .el-col,
.table-row .el-col {
  margin-bottom: 20px;
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
  color: #303133;
  font-size: 15px;
}

.order-amount {
  font-weight: 600;
  color: #f56c6c;
}

:deep(.el-card) {
  border-radius: 12px;
  border: none;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #f0f2f5;
  padding: 14px 20px;
}

:deep(.el-progress-bar__outer) {
  border-radius: 4px;
  background-color: #f0f2f5;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8f9fa;
  --el-table-header-text-color: #606266;
}

:deep(.el-table th.el-table__cell) {
  font-weight: 600;
}

:deep(.el-empty) {
  padding: 20px 0;
}
</style>
