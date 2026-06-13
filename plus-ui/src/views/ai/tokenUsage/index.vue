<template>
  <div class="p-4">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始日期"
            end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="queryParams.modelId" placeholder="全部模型" clearable style="width: 180px">
            <el-option v-for="m in modelList" :key="m.id" :label="m.displayName || m.modelName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总 Token 数" :value="overview.totalTokens" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="输入 Token" :value="overview.totalInputTokens" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="输出 Token" :value="overview.totalOutputTokens" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总成本（元）" :value="overview.totalCost" :precision="4" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Token 用量趋势 -->
    <el-card shadow="never" class="mb-4">
      <template #header>
        <span>Token 用量趋势</span>
      </template>
      <div ref="tokenTrendChartRef" style="height: 320px"></div>
    </el-card>

    <!-- 成本趋势（独立图表，避免与 Token 数值差异过大） -->
    <el-card shadow="never" class="mb-4">
      <template #header>
        <span>成本趋势</span>
      </template>
      <div ref="costTrendChartRef" style="height: 260px"></div>
    </el-card>

    <!-- 排行表格 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>模型用量排行</span>
          </template>
          <el-table :data="modelRanking" stripe size="small">
            <el-table-column prop="modelName" label="模型" />
            <el-table-column prop="totalTokens" label="Token 总量" width="120">
              <template #default="{ row }">
                {{ Number(row.totalTokens).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column prop="totalCost" label="成本（元）" width="120">
              <template #default="{ row }">
                {{ Number(row.totalCost || 0).toFixed(4) }}
              </template>
            </el-table-column>
            <el-table-column prop="requestCount" label="请求次数" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>用户用量排行</span>
          </template>
          <el-table :data="userRanking" stripe size="small">
            <el-table-column prop="userName" label="用户" />
            <el-table-column prop="totalTokens" label="Token 总量" width="120">
              <template #default="{ row }">
                {{ Number(row.totalTokens).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column prop="totalCost" label="成本（元）" width="120">
              <template #default="{ row }">
                {{ Number(row.totalCost || 0).toFixed(4) }}
              </template>
            </el-table-column>
            <el-table-column prop="requestCount" label="请求次数" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用量明细 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>用量明细</span>
          <el-button type="warning" size="small" @click="handleBackfill">回填历史数据</el-button>
        </div>
      </template>
      <el-table :data="usageList" stripe>
        <el-table-column prop="usageDate" label="日期" width="120" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="modelName" label="模型" width="150" />
        <el-table-column prop="totalInputTokens" label="输入 Token" width="120">
          <template #default="{ row }">
            {{ Number(row.totalInputTokens || 0).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="totalOutputTokens" label="输出 Token" width="120">
          <template #default="{ row }">
            {{ Number(row.totalOutputTokens || 0).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="Token 总量" width="120">
          <template #default="{ row }">
            {{ Number(row.totalTokens || 0).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="requestCount" label="请求次数" width="100" />
        <el-table-column prop="totalCost" label="成本（元）" width="120">
          <template #default="{ row }">
            {{ Number(row.totalCost || 0).toFixed(4) }}
          </template>
        </el-table-column>
        <el-table-column prop="avgTokensPerRequest" label="平均/次" width="100">
          <template #default="{ row }">
            {{ Number(row.avgTokensPerRequest || 0).toFixed(1) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { getTokenUsageSummary, getTokenUsageTrend, getModelRanking, getUserRanking, backfillTokenUsage } from '@/api/ai/tokenUsage'
import { listModel } from '@/api/ai/model'
import * as echarts from 'echarts'

defineOptions({ name: 'AiTokenUsage' })

const dateRange = ref<string[]>([])
const queryParams = reactive<any>({ modelId: undefined })
const modelList = ref<any[]>([])
const usageList = ref<any[]>([])
const modelRanking = ref<any[]>([])
const userRanking = ref<any[]>([])
const trendData = ref<any[]>([])
const overview = reactive<any>({ totalTokens: 0, totalInputTokens: 0, totalOutputTokens: 0, totalCost: 0 })
const tokenTrendChartRef = ref<HTMLElement>()
const costTrendChartRef = ref<HTMLElement>()
let tokenTrendChart: echarts.ECharts | null = null
let costTrendChart: echarts.ECharts | null = null

/** 获取默认日期范围（最近30天） */
function getDefaultDateRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 30)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  return [fmt(start), fmt(end)]
}

/** 扩大时间范围到最近90天 */
function expandDateRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 90)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  dateRange.value = [fmt(start), fmt(end)]
  handleQuery()
}

/** 查询数据 */
async function handleQuery() {
  const [startDate, endDate] = dateRange.value?.length === 2 ? dateRange.value : getDefaultDateRange()
  const params = { startDate, endDate, modelId: queryParams.modelId || undefined }

  try {
    const [summaryRes, trendRes, modelRes, userRes] = await Promise.all([
      getTokenUsageSummary(params),
      getTokenUsageTrend(params),
      getModelRanking(params),
      getUserRanking(params)
    ])

    usageList.value = summaryRes.data || []
    trendData.value = trendRes.data || []
    modelRanking.value = modelRes.data || []
    // 过滤掉系统用户（userId <= 0）
    userRanking.value = (userRes.data || []).filter((u: any) => Number(u.userId) > 0)

    // 计算概览
    overview.totalTokens = usageList.value.reduce((s: number, v: any) => s + (v.totalTokens || 0), 0)
    overview.totalInputTokens = usageList.value.reduce((s: number, v: any) => s + (v.totalInputTokens || 0), 0)
    overview.totalOutputTokens = usageList.value.reduce((s: number, v: any) => s + (v.totalOutputTokens || 0), 0)
    overview.totalCost = usageList.value.reduce((s: number, v: any) => s + (Number(v.totalCost) || 0), 0)

    // 渲染趋势图（即使只有 1 天数据也显示）
    nextTick(() => {
      renderTokenTrendChart(trendData.value)
      renderCostTrendChart(trendData.value)
    })
  } catch (e) {
    console.error('查询 Token 用量失败', e)
  }
}

/** 重置查询 */
function resetQuery() {
  dateRange.value = getDefaultDateRange()
  queryParams.modelId = undefined
  handleQuery()
}

/** 渲染 Token 用量趋势图表（仅 Token 数据，单 Y 轴） */
function renderTokenTrendChart(data: any[]) {
  if (!tokenTrendChartRef.value) return
  if (!tokenTrendChart) {
    tokenTrendChart = echarts.init(tokenTrendChartRef.value)
  }
  const isSinglePoint = data.length <= 1
  tokenTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['Token 总量', '输入 Token', '输出 Token'],
      top: 0
    },
    grid: { left: 60, right: 30, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: data.map((d: any) => d.date),
      boundaryGap: isSinglePoint // 单点时留出间隙，避免贴边
    },
    yAxis: {
      type: 'value',
      name: 'Token',
      axisLabel: { formatter: (v: number) => v >= 1000 ? (v / 1000).toFixed(1) + 'k' : String(v) }
    },
    series: [
      {
        name: 'Token 总量',
        type: 'line',
        data: data.map((d: any) => d.totalTokens),
        smooth: !isSinglePoint,
        symbol: 'circle',
        symbolSize: isSinglePoint ? 12 : 6,
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.1 },
        label: isSinglePoint ? {
          show: true,
          position: 'top',
          formatter: (p: any) => Number(p.value).toLocaleString(),
          fontSize: 14,
          fontWeight: 'bold'
        } : undefined
      },
      {
        name: '输入 Token',
        type: 'line',
        data: data.map((d: any) => d.totalInputTokens),
        smooth: !isSinglePoint,
        symbol: 'circle',
        symbolSize: isSinglePoint ? 10 : 4,
        lineStyle: { width: 2, type: 'dashed' },
        label: isSinglePoint ? {
          show: true,
          position: 'bottom',
          formatter: (p: any) => '输入: ' + Number(p.value).toLocaleString()
        } : undefined
      },
      {
        name: '输出 Token',
        type: 'line',
        data: data.map((d: any) => d.totalOutputTokens),
        smooth: !isSinglePoint,
        symbol: 'circle',
        symbolSize: isSinglePoint ? 10 : 4,
        lineStyle: { width: 2, type: 'dashed' },
        label: isSinglePoint ? {
          show: true,
          position: 'bottom',
          formatter: (p: any) => '输出: ' + Number(p.value).toLocaleString()
        } : undefined
      }
    ]
  }, true) // true = not merge, replace
}

/** 渲染成本趋势图表（独立图表，单 Y 轴） */
function renderCostTrendChart(data: any[]) {
  if (!costTrendChartRef.value) return
  if (!costTrendChart) {
    costTrendChart = echarts.init(costTrendChartRef.value)
  }
  const isSinglePoint = data.length <= 1
  costTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成本（元）'], top: 0 },
    grid: { left: 60, right: 30, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: data.map((d: any) => d.date),
      boundaryGap: true
    },
    yAxis: {
      type: 'value',
      name: '成本（元）',
      axisLabel: { formatter: (v: number) => v.toFixed(4) }
    },
    series: [
      {
        name: '成本（元）',
        type: 'bar',
        data: data.map((d: any) => Number(d.totalCost) || 0),
        barWidth: isSinglePoint ? '30%' : '40%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        },
        label: isSinglePoint ? {
          show: true,
          position: 'top',
          formatter: (p: any) => Number(p.value).toFixed(4) + ' 元',
          fontSize: 14,
          fontWeight: 'bold',
          color: '#188df0'
        } : undefined
      }
    ]
  }, true)
}

/** 回填历史数据 */
async function handleBackfill() {
  const [startDate, endDate] = dateRange.value?.length === 2 ? dateRange.value : getDefaultDateRange()
  try {
    await backfillTokenUsage({ startDate, endDate })
    ElMessage.success('回填完成')
    handleQuery()
  } catch (e) {
    ElMessage.error('回填失败')
  }
}

/** 加载模型列表 */
async function loadModels() {
  try {
    const res = await listModel()
    modelList.value = res.rows || []
  } catch (e) {
    console.error('加载模型列表失败', e)
  }
}

/** 窗口大小变化时重绘图表 */
function handleResize() {
  tokenTrendChart?.resize()
  costTrendChart?.resize()
}

onMounted(() => {
  dateRange.value = getDefaultDateRange()
  loadModels()
  handleQuery()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.empty-chart {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}
</style>
