<template>
  <div class="p-2">
    <!-- 筛选区 -->
    <el-card class="mb-2">
      <el-form :model="queryParams" ref="queryRef" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="统计区间" prop="dateRange">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
                @change="handleQuery()"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="设备">
              <equipment-select v-model="queryParams.equipmentId" @change="handleQuery()" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="设备类型">
              <el-select v-model="queryParams.equipmentType" placeholder="全部" clearable style="width: 100%" @change="handleQuery()">
                <el-option v-for="d in mes_equipment_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
              <el-button type="warning" plain icon="RefreshRight" @click="handleOpenAggregate()" v-hasPermi="['mes:eq:oee:aggregate']"
                >手动聚合</el-button
              >
              <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:eq:oee:export']">导出</el-button>
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
            <div class="stat-label">平均稼动率</div>
            <div class="stat-value primary">{{ pct(overview.avgAvailability) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">平均 OEE</div>
            <div class="stat-value success">{{ pct(overview.avgOee) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">总停机时长</div>
            <div class="stat-value danger">{{ hours(overview.totalDowntimeMinutes) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">总故障次数</div>
            <div class="stat-value warning">{{ overview.totalFaultCount ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">覆盖设备</div>
            <div class="stat-value">{{ overview.equipmentCount ?? 0 }} 台</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 占位提示：表现率/质量率无真实数据源时后端返回 *Available=false，此时不得展示 100% -->
    <el-alert
      v-if="overview.performanceRateAvailable === false || overview.qualityRateAvailable === false"
      class="mb-2"
      type="warning"
      :closable="false"
      show-icon
      title="表现率 / 质量率当前为占位值"
    >
      <template #default>
        <span v-if="overview.performanceRateAvailable === false"
          >表现率暂无真实数据源（缺少节拍/标准工时口径），明细中显示为「占位」，未按 100% 参与误导性展示。
        </span>
        <span v-if="overview.qualityRateAvailable === false">质量率暂无真实数据源（区间内无质量数据），明细中显示为「占位」。</span>
      </template>
    </el-alert>

    <!-- 图表区 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>稼动率 / 停机时长趋势</span></template>
          <div ref="trendChartRef" style="height: 320px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>时长分布</span></template>
          <div ref="distChartRef" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="mb-2">
      <template #header><span>停机设备 TOP10</span></template>
      <div ref="topChartRef" style="height: 280px" />
    </el-card>

    <!-- 明细表 -->
    <el-card>
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="统计日期" prop="statDate" align="center" width="120">
          <template #default="scope">{{ fmtDate(scope.row.statDate) }}</template>
        </el-table-column>
        <el-table-column label="设备编码" prop="equipmentCode" width="120" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
        <el-table-column label="计划时长(分)" prop="plannedMinutes" align="right" width="110" />
        <el-table-column label="停机时长(分)" prop="downtimeMinutes" align="right" width="110" />
        <el-table-column label="运行时长(分)" prop="runMinutes" align="right" width="110" />
        <el-table-column label="故障次数" prop="faultCount" align="center" width="90" />
        <el-table-column label="稼动率" prop="availability" align="center" width="100">
          <template #default="scope">
            <el-tag :type="availTagType(scope.row.availability)">{{ pct(scope.row.availability) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="OEE" prop="oee" align="center" width="90">
          <template #default="scope">{{ pct(scope.row.oee) }}</template>
        </el-table-column>
        <!-- 表现率/质量率：后端无真实数据源时返回 null，必须显示「占位」而不是 100%（MES-EQ-03 / R3） -->
        <el-table-column label="表现率" prop="performanceRate" align="center">
          <template #default="scope">
            <el-tooltip v-if="scope.row.performanceRate == null" content="后端暂无表现率数据源（缺节拍/标准工时），当前为占位值" placement="top">
              <el-tag type="info" size="small">占位</el-tag>
            </el-tooltip>
            <span v-else>{{ pct(scope.row.performanceRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="质量率" prop="qualityRate" align="center">
          <template #default="scope">
            <el-tooltip v-if="scope.row.qualityRate == null" content="该记录无质量率数据源，当前为占位值" placement="top">
              <el-tag type="info" size="small">占位</el-tag>
            </el-tooltip>
            <span v-else>{{ pct(scope.row.qualityRate) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 手动聚合对话框 -->
    <el-dialog v-model="aggregateDialog.visible" title="手动聚合 OEE 数据" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="聚合日期">
          <el-date-picker v-model="aggregateDialog.date" type="date" value-format="YYYY-MM-DD" placeholder="留空聚合昨天" style="width: 100%" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="按 (租户+设备+日期) 幂等 upsert, 可重复执行; 停机时长取自 eq_repair.downtime_hours。" />
      </el-form>
      <template #footer>
        <el-button @click="aggregateDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="aggregateDialog.loading" @click="submitAggregate">确认聚合</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqOee">
import * as echarts from 'echarts';
import { getOeeOverview, getOeeTrend, getOeeDistribution, getOeeTopDowntime, listOeeRecord, exportOee, aggregateOee } from '@/api/mes/eq/oee';
import type { EqOeeQuery, EqOeeRecordVO, EqOeeOverviewVO, EqOeeTrendVO, EqOeeDistributionVO, EqOeeTopVO } from '@/api/mes/eq/oee';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_equipment_type } = toRefs<any>(proxy?.useDict('mes_equipment_type'));

const loading = ref(false);
const dataList = ref<EqOeeRecordVO[]>([]);
const total = ref(0);
const overview = ref<Partial<EqOeeOverviewVO>>({});
const trendData = ref<EqOeeTrendVO[]>([]);
const distribution = ref<Partial<EqOeeDistributionVO>>({});
const topData = ref<EqOeeTopVO[]>([]);

/** 默认最近 7 天 */
const defaultRange = () => {
  const end = new Date();
  const start = new Date();
  start.setTime(start.getTime() - 6 * 24 * 3600 * 1000);
  return [fmt(start), fmt(end)];
};
const fmt = (d: Date) => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
};

const dateRange = ref<string[]>(defaultRange());
const queryParams = ref<EqOeeQuery>({
  pageNum: 1,
  pageSize: 10,
  equipmentId: undefined,
  equipmentType: undefined,
  granularity: 'DAY'
});

// ==================== ECharts ====================
const trendChartRef = ref<HTMLElement>();
const distChartRef = ref<HTMLElement>();
const topChartRef = ref<HTMLElement>();
let trendChart: echarts.ECharts | null = null;
let distChart: echarts.ECharts | null = null;
let topChart: echarts.ECharts | null = null;

// ==================== 数据加载 ====================
const buildParams = (): EqOeeQuery => {
  const [startDate, endDate] = dateRange.value || [];
  return {
    ...queryParams.value,
    startDate,
    endDate,
    pageNum: queryParams.value.pageNum,
    pageSize: queryParams.value.pageSize
  };
};

const loadDashboard = async () => {
  const [startDate, endDate] = dateRange.value || [];
  const params = { ...queryParams.value, startDate, endDate } as EqOeeQuery;
  delete (params as any).pageNum;
  delete (params as any).pageSize;
  const [ov, tr, di, tp] = await Promise.all([getOeeOverview(params), getOeeTrend(params), getOeeDistribution(params), getOeeTopDowntime(params)]);
  overview.value = ov.data || {};
  trendData.value = tr.data || [];
  distribution.value = di.data || {};
  topData.value = tp.data || [];
  renderTrend();
  renderDist();
  renderTop();
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listOeeRecord(buildParams());
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const loadAll = async () => {
  try {
    await loadDashboard();
  } catch (e) {
    // 看板加载失败不阻塞明细
  }
  await getList();
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  loadAll();
};

const resetQuery = () => {
  dateRange.value = defaultRange();
  queryParams.value.equipmentId = undefined;
  queryParams.value.equipmentType = undefined;
  handleQuery();
};

// ==================== 图表渲染 ====================
const renderTrend = () => {
  nextTick(() => {
    if (!trendChartRef.value) return;
    trendChart = echarts.getInstanceByDom(trendChartRef.value) || echarts.init(trendChartRef.value);
    const dates = trendData.value.map((t) => fmtDate(t.statDate));
    const avail = trendData.value.map((t) => toPct(t.availability));
    const oee = trendData.value.map((t) => toPct(t.oee));
    const down = trendData.value.map((t) => Number(t.downtimeMinutes ?? 0));
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['稼动率', 'OEE', '停机时长(分)'], top: 0 },
      grid: { left: 50, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: dates },
      yAxis: [
        { type: 'value', name: '百分比(%)', min: 0, max: 100 },
        { type: 'value', name: '停机(分)', min: 0 }
      ],
      series: [
        { name: '稼动率', type: 'line', data: avail, smooth: true, itemStyle: { color: '#409EFF' } },
        { name: 'OEE', type: 'line', data: oee, smooth: true, itemStyle: { color: '#67C23A' } },
        { name: '停机时长(分)', type: 'bar', yAxisIndex: 1, data: down, itemStyle: { color: '#F56C6C' } }
      ]
    });
  });
};

const renderDist = () => {
  nextTick(() => {
    if (!distChartRef.value) return;
    distChart = echarts.getInstanceByDom(distChartRef.value) || echarts.init(distChartRef.value);
    const d = distribution.value;
    distChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 分 ({d}%)' },
      legend: { bottom: 0 },
      series: [
        {
          name: '时长分布',
          type: 'pie',
          radius: ['40%', '70%'],
          label: { formatter: '{b}\n{d}%' },
          data: [
            { name: '运行', value: round(d.runMinutes), itemStyle: { color: '#67C23A' } },
            { name: '停机', value: round(d.downtimeMinutes), itemStyle: { color: '#F56C6C' } },
            { name: '保养', value: round(d.maintenanceMinutes), itemStyle: { color: '#E6A23C' } }
          ]
        }
      ]
    });
  });
};

const renderTop = () => {
  nextTick(() => {
    if (!topChartRef.value) return;
    topChart = echarts.getInstanceByDom(topChartRef.value) || echarts.init(topChartRef.value);
    const names = topData.value.map((t) => t.equipmentName || t.equipmentCode || String(t.equipmentId));
    const mins = topData.value.map((t) => Number(t.downtimeMinutes ?? 0));
    topChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 30, top: 20, bottom: 40 },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: '停机(分)' },
      series: [{ name: '停机时长', type: 'bar', data: mins, itemStyle: { color: '#F56C6C' }, label: { show: true, position: 'top' } }]
    });
  });
};

const handleResize = () => {
  trendChart?.resize();
  distChart?.resize();
  topChart?.resize();
};

// ==================== 手动聚合 ====================
const aggregateDialog = reactive({ visible: false, date: '', loading: false });
const handleOpenAggregate = () => {
  aggregateDialog.date = '';
  aggregateDialog.visible = true;
};
const submitAggregate = async () => {
  aggregateDialog.loading = true;
  try {
    const res = await aggregateOee(aggregateDialog.date || undefined);
    proxy?.$modal.msgSuccess(`聚合完成, 处理 ${res.data ?? 0} 条记录`);
    aggregateDialog.visible = false;
    await loadAll();
  } finally {
    aggregateDialog.loading = false;
  }
};

const handleExport = () => {
  const [startDate, endDate] = dateRange.value || [];
  exportOee({ ...queryParams.value, startDate, endDate } as EqOeeQuery);
};

// ==================== 辅助 ====================
const pct = (v?: number) => (v == null ? '0%' : (v * 100).toFixed(1) + '%');
const toPct = (v?: number) => (v == null ? 0 : Number((v * 100).toFixed(1)));
const hours = (v?: number) => (v == null ? '0 h' : (v / 60).toFixed(1) + ' h');
const round = (v?: number) => (v == null ? 0 : Number(v.toFixed(1)));
const fmtDate = (s?: string) => (s ? String(s).substring(0, 10) : '-');
const availTagType = (v?: number) => {
  if (v == null) return 'info';
  if (v >= 0.85) return 'success';
  if (v >= 0.6) return 'warning';
  return 'danger';
};

onMounted(() => {
  loadAll();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  trendChart?.dispose();
  distChart?.dispose();
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
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
  flex-wrap: wrap;
}
</style>
