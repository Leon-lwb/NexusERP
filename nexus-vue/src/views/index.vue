<template>
  <div class="app-container home">
    <el-row :gutter="20">
      <!-- 统计数据卡片 -->
      <el-col :span="6" v-for="(item, index) in statCards" :key="index">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: item.color }">
              <i :class="item.icon"></i>
            </div>
            <div class="stat-info">
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 销售趋势图表 -->
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>销售趋势</span>
            </div>
          </template>
          <div ref="salesChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>

      <!-- 热门商品排行 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>热门商品排行</span>
            </div>
          </template>
          <div ref="productChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 最新订单 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最新订单</span>
            </div>
          </template>
          <el-table :data="latestOrders" style="width: 100%">
            <el-table-column prop="orderNo" label="订单号" width="180" />
            <el-table-column prop="customer" label="客户" width="120" />
            <el-table-column prop="amount" label="金额" width="120">
              <template #default="scope">
                <span>¥{{ scope.row.amount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getOrderStatusType(scope.row.status)">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 任务进度 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>任务进度</span>
            </div>
          </template>
          <el-table :data="tasks" style="width: 100%">
            <el-table-column prop="name" label="任务名称" />
            <el-table-column prop="executor" label="执行人" width="120" />
            <el-table-column prop="progress" label="进度" width="200">
              <template #default="scope">
                <el-progress :percentage="scope.row.progress" :status="scope.row.progress === 100 ? 'success' : ''" />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getTaskStatusType(scope.row.status)">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Index">
import * as echarts from 'echarts'

// 统计卡片数据
const statCards = ref([
  { title: '总销售额', value: '¥128,560', icon: 'el-icon-s-order', color: '#40c9c6' },
  { title: '客户数量', value: '1,248', icon: 'el-icon-user', color: '#36a3f7' },
  { title: '商品数量', value: '3,652', icon: 'el-icon-goods', color: '#f4516c' },
  { title: '待办任务', value: '18', icon: 'el-icon-s-flag', color: '#34bfa3' }
])

// 最新订单数据
const latestOrders = ref([
  { orderNo: 'NO20230801001', customer: '阿里巴巴', amount: 12800, status: '已完成', createTime: '2023-08-01 14:30' },
  { orderNo: 'NO20230801002', customer: '腾讯科技', amount: 8600, status: '已发货', createTime: '2023-08-01 11:20' },
  { orderNo: 'NO20230801003', customer: '百度公司', amount: 15200, status: '待发货', createTime: '2023-08-01 09:45' },
  { orderNo: 'NO20230801004', customer: '字节跳动', amount: 6800, status: '已付款', createTime: '2023-08-01 08:15' },
  { orderNo: 'NO20230731001', customer: '京东商城', amount: 21500, status: '已完成', createTime: '2023-07-31 17:30' }
])

// 任务数据
const tasks = ref([
  { name: '完成年度销售报告', executor: '张三', progress: 100, status: '已完成' },
  { name: '客户回访计划', executor: '李四', progress: 80, status: '进行中' },
  { name: '新产品上线准备', executor: '王五', progress: 60, status: '进行中' },
  { name: '供应商资质审核', executor: '赵六', progress: 30, status: '进行中' },
  { name: '系统安全检测', executor: '钱七', progress: 0, status: '未开始' }
])

// 图表引用
const salesChartRef = ref(null)
const productChartRef = ref(null)
let salesChart = null
let productChart = null

// 订单状态类型映射
function getOrderStatusType(status) {
  const statusMap = {
    '已完成': 'success',
    '已发货': 'primary',
    '待发货': 'warning',
    '已付款': 'info'
  }
  return statusMap[status] || 'info'
}

// 任务状态类型映射
function getTaskStatusType(status) {
  const statusMap = {
    '已完成': 'success',
    '进行中': 'warning',
    '未开始': 'info'
  }
  return statusMap[status] || 'info'
}

// 初始化图表
function initCharts() {
  // 销售趋势图表
  salesChart = echarts.init(salesChartRef.value)
  const salesOption = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月']
    },
    yAxis: {
      type: 'value'
    },
    series: [{
      data: [1200, 1900, 1500, 2500, 1800, 2200, 2800, 3500],
      type: 'line',
      smooth: true,
      itemStyle: {
        color: '#40c9c6'
      }
    }]
  }
  salesChart.setOption(salesOption)

  // 热门商品排行图表
  productChart = echarts.init(productChartRef.value)
  const productOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'value'
    },
    yAxis: {
      type: 'category',
      data: ['商品A', '商品B', '商品C', '商品D', '商品E']
    },
    series: [{
      data: [220, 180, 150, 120, 100],
      type: 'bar',
      itemStyle: {
        color: '#36a3f7'
      }
    }]
  }
  productChart.setOption(productOption)
}

// 页面加载完成后初始化图表
onMounted(() => {
  initCharts()
  
  // 监听窗口大小变化，自适应图表
  window.addEventListener('resize', () => {
    if (salesChart) {
      salesChart.resize()
    }
    if (productChart) {
      productChart.resize()
    }
  })
})

// 组件销毁前清理图表实例
onUnmounted(() => {
  if (salesChart) {
    salesChart.dispose()
  }
  if (productChart) {
    productChart.dispose()
  }
})
</script>

<style scoped>
.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
}

.stat-icon i {
  font-size: 24px;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #262626;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-card {
  border-radius: 8px;
}
</style>