<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备名称" prop="equipmentName">
            <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="健康等级" prop="grade">
            <el-select v-model="queryParams.grade" placeholder="全部" clearable style="width: 100%">
              <el-option label="优 (≥80)" value="HIGH" />
              <el-option label="中 (60-79)" value="MEDIUM" />
              <el-option label="差 (<60)" value="LOW" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" :loading="evaluating" @click="handleEvaluate" v-hasPermi="['mes:eq:health:evaluate']"
          >重新评估</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="healthList" show-overflow-tooltip>
      <el-table-column label="设备编码" align="center" prop="equipmentCode" width="120" />
      <el-table-column label="设备名称" align="center" prop="equipmentName" min-width="140" />
      <el-table-column label="健康评分" align="center" width="180">
        <template #default="scope">
          <el-progress
            type="dashboard"
            :percentage="scope.row.score"
            :color="scoreColor(scope.row.score)"
            :width="70"
            :stroke-width="8"
          />
        </template>
      </el-table-column>
      <el-table-column label="等级" align="center" prop="grade" width="90">
        <template #default="scope">
          <el-tag :type="gradeTagType(scope.row.grade)">{{ gradeLabel(scope.row.grade) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评分变化" align="center" width="100">
        <template #default="scope">
          <span v-if="scope.row.scoreChange === null || scope.row.scoreChange === undefined">-</span>
          <span v-else-if="scope.row.scoreChange > 0" style="color: #67c23a">↑ {{ scope.row.scoreChange }}</span>
          <span v-else-if="scope.row.scoreChange < 0" style="color: #f56c6c">↓ {{ Math.abs(scope.row.scoreChange) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="24h异常点" align="center" prop="anomalyPoints" width="90">
        <template #default="scope">
          <span :style="{ color: scope.row.anomalyPoints > 0 ? '#e6a23c' : '', fontWeight: scope.row.anomalyPoints > 0 ? 'bold' : 'normal' }">
            {{ scope.row.anomalyPoints }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="90天维修" align="center" prop="repairCount90d" width="90" />
      <el-table-column label="未闭环维修" align="center" prop="openRepairCount" width="90">
        <template #default="scope">
          <span :style="{ color: scope.row.openRepairCount > 0 ? '#f56c6c' : '' }">{{ scope.row.openRepairCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="评分日期" align="center" prop="recordDate" width="100">
        <template #default="scope">{{ formatDate(scope.row.recordDate) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleTrend(scope.row)">趋势</el-button>
          <el-button link type="primary" @click="handleDetail(scope.row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 评分趋势抽屉 -->
    <el-drawer v-model="showTrendDrawer" :title="trendTitle" size="60%" destroy-on-close>
      <div v-loading="trendLoading" ref="trendChartRef" class="trend-chart"></div>
    </el-drawer>

    <!-- 属性明细对话框 -->
    <el-dialog title="24h 属性异常明细" v-model="showDetailDialog" width="640px" append-to-body>
      <el-table :data="detailRows" size="small" v-loading="detailLoading">
        <el-table-column label="属性编码" prop="propertyCode" min-width="120" />
        <el-table-column label="属性名称" prop="propertyName" min-width="120" />
        <el-table-column label="EWMA 均值" prop="mean" width="110" align="center" />
        <el-table-column label="标准差" prop="std" width="110" align="center" />
        <el-table-column label="异常点数" prop="anomalyPoints" width="100" align="center">
          <template #default="scope">
            <span :style="{ color: scope.row.anomalyPoints > 0 ? '#e6a23c' : '', fontWeight: scope.row.anomalyPoints > 0 ? 'bold' : 'normal' }">
              {{ scope.row.anomalyPoints }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="EqHealth" lang="ts">
import { listHealth, healthTrend, evaluateHealth, parseDetail } from '@/api/mes/eq/health';
import { EqHealthRecordVO, EqHealthDetail } from '@/api/mes/eq/health/types';
import * as echarts from 'echarts';
import dayjs from 'dayjs';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const evaluating = ref(false);
const healthList = ref<EqHealthRecordVO[]>([]);
const total = ref(0);
const showSearch = ref(true);
const showTrendDrawer = ref(false);
const showDetailDialog = ref(false);
const trendLoading = ref(false);
const detailLoading = ref(false);
const detailRows = ref<EqHealthDetail[]>([]);
const trendTitle = ref('评分趋势');
const trendChartRef = ref<HTMLElement | null>(null);
let trendChart: echarts.ECharts | null = null;

const initQuery = { pageNum: 1, pageSize: 10, equipmentName: undefined, grade: undefined };
const queryParams = ref({ ...initQuery });

const gradeLabel = (grade: string) => ({ HIGH: '优', MEDIUM: '中', LOW: '差' }[grade] || grade);
const gradeTagType = (grade: string): 'success' | 'warning' | 'danger' | 'info' =>
  (({ HIGH: 'success', MEDIUM: 'warning', LOW: 'danger' } as const)[grade as 'HIGH' | 'MEDIUM' | 'LOW'] ?? 'info');
const scoreColor = (score: number) => {
  if (score >= 80) return '#67c23a';
  if (score >= 60) return '#e6a23c';
  return '#f56c6c';
};
const formatDate = (d?: string) => (d ? dayjs(d).format('YYYY-MM-DD') : '-');

const getList = async () => {
  loading.value = true;
  try {
    const res = await listHealth(queryParams.value);
    healthList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { ...initQuery };
  handleQuery();
};

const handleEvaluate = async () => {
  await proxy?.$modal.confirm('确认重新评估全部关联 IoT 的设备健康？');
  evaluating.value = true;
  try {
    const res = await evaluateHealth();
    proxy?.$modal.msgSuccess(`评估完成, 处理 ${res.data} 台设备`);
    getList();
  } finally {
    evaluating.value = false;
  }
};

const handleDetail = (row: EqHealthRecordVO) => {
  detailRows.value = parseDetail(row.detailJson);
  detailLoading.value = false;
  showDetailDialog.value = true;
};

const handleTrend = async (row: EqHealthRecordVO) => {
  trendTitle.value = `健康趋势 - ${row.equipmentName}`;
  showTrendDrawer.value = true;
  trendLoading.value = true;
  try {
    const res = await healthTrend(row.equipmentId, 30);
    await nextTick();
    renderTrend(res.data || []);
  } finally {
    trendLoading.value = false;
  }
};

const renderTrend = (records: EqHealthRecordVO[]) => {
  if (!trendChartRef.value) return;
  trendChart = echarts.init(trendChartRef.value);
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: records.map((r) => dayjs(r.recordDate).format('MM-DD')),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', min: 0, max: 100, name: '评分' },
    visualMap: {
      show: false,
      pieces: [
        { gt: 79, color: '#67c23a' },
        { gt: 59, lte: 79, color: '#e6a23c' },
        { lte: 59, color: '#f56c6c' }
      ]
    },
    series: [
      {
        type: 'line',
        data: records.map((r) => r.score),
        markLine: {
          silent: true,
          data: [
            { yAxis: 80, lineStyle: { color: '#67c23a', type: 'dashed' }, label: { formatter: '优 80' } },
            { yAxis: 60, lineStyle: { color: '#e6a23c', type: 'dashed' }, label: { formatter: '中 60' } }
          ]
        }
      }
    ]
  });
  trendChart.resize();
};

onMounted(() => {
  getList();
});

onBeforeUnmount(() => {
  trendChart?.dispose();
  trendChart = null;
});

watch(showTrendDrawer, (v) => {
  if (!v) {
    trendChart?.dispose();
    trendChart = null;
  }
});
</script>

<style scoped lang="scss">
.trend-chart {
  width: 100%;
  height: 420px;
}
</style>
