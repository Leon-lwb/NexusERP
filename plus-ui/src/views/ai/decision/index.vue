<template>
  <div class="app-container">
    <el-form :inline="true" :model="filters" class="mb-4">
      <el-form-item label="运行状态"
        ><el-select v-model="filters.status" placeholder="全部" clearable style="width: 155px">
          <el-option label="分析中" value="RUNNING" /><el-option label="待审批" value="WAITING_APPROVAL" />
          <el-option label="已完成" value="SUCCEEDED" /><el-option label="已过期" value="STALE" /> </el-select
      ></el-form-item>
      <el-form-item label="风险等级"
        ><el-select v-model="filters.riskLevel" placeholder="全部" clearable style="width: 155px">
          <el-option label="严重" value="CRITICAL" /><el-option label="高" value="HIGH" /> <el-option label="中" value="MEDIUM" /><el-option
            label="低"
            value="LOW"
          /> </el-select
      ></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="12" class="mb-4">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="12" :md="6">
        <el-card shadow="never"><el-statistic :title="card.label" :value="card.count" /></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="12" class="mb-4">
      <el-col :xs="24" :lg="8"
        ><el-card shadow="never"
          ><template #header>风险分布（当前页）</template>
          <div ref="chartEl" v-show="rows.length > 0" style="height: 300px" />
          <el-empty v-if="rows.length === 0 && !loading" description="暂无决策运行数据" :image-size="96" /></el-card
      ></el-col>
      <el-col :xs="24" :lg="16"
        ><el-card shadow="never"
          ><template #header>高风险运行（当前页）</template>
          <el-table :data="highRisk" height="300px" v-loading="loading" @row-click="openDetail">
            <el-table-column label="业务编号" min-width="180"
              ><template #default="scope">
                <el-tooltip
                  :content="decisionBusinessObjectDisplay(scope.row).internalIdText"
                  :disabled="!decisionBusinessObjectDisplay(scope.row).internalIdText"
                >
                  <span>{{ decisionBusinessObjectDisplay(scope.row).label }}</span>
                </el-tooltip>
              </template></el-table-column
            >
            <el-table-column prop="bizType" label="业务类型" min-width="120" />
            <el-table-column label="等级" width="90"
              ><template #default="scope"
                ><el-tag :type="riskTagType(scope.row.riskLevel)">{{ decisionRiskLabel(scope.row.riskLevel) }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="状态" min-width="115"
              ><template #default="scope"
                ><el-tag
                  :type="
                    scope.row.status === 'FAILED'
                      ? 'danger'
                      : scope.row.status === 'WAITING_APPROVAL'
                        ? 'warning'
                        : scope.row.status === 'SUCCEEDED'
                          ? 'success'
                          : 'info'
                  "
                  size="small"
                  >{{ decisionRunStatusLabel(scope.row.status) }}</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column prop="recommendedScenarioCode" label="推荐方案" min-width="135" />
          </el-table> </el-card
      ></el-col>
    </el-row>
    <el-card shadow="never"
      ><template #header>决策运行记录</template>
      <el-table :data="rows" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="id" label="运行 ID" min-width="175" />
        <el-table-column label="业务对象" min-width="180"
          ><template #default="scope">
            <el-tooltip
              :content="decisionBusinessObjectDisplay(scope.row).internalIdText"
              :disabled="!decisionBusinessObjectDisplay(scope.row).internalIdText"
            >
              <span>{{ decisionBusinessObjectDisplay(scope.row).label }}</span>
            </el-tooltip>
          </template></el-table-column
        >
        <el-table-column prop="bizType" label="场景" min-width="135" />
        <el-table-column label="风险" min-width="100"
          ><template #default="scope"
            ><el-tag :type="riskTagType(scope.row.riskLevel)">{{ decisionRiskLabel(scope.row.riskLevel) }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="状态" min-width="115"
          ><template #default="scope"
            ><el-tag
              :type="
                scope.row.status === 'FAILED'
                  ? 'danger'
                  : scope.row.status === 'WAITING_APPROVAL'
                    ? 'warning'
                    : scope.row.status === 'SUCCEEDED'
                      ? 'success'
                      : 'info'
              "
              size="small"
              >{{ decisionRunStatusLabel(scope.row.status) }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column prop="recommendedScenarioCode" label="推荐方案" min-width="150" />
        <el-table-column label="操作" width="85" fixed="right"
          ><template #default="scope"><el-button link type="primary" @click.stop="openDetail(scope.row)">详情</el-button></template></el-table-column
        >
      </el-table>
      <pagination v-show="total > 0" v-model:page="filters.pageNum" v-model:limit="filters.pageSize" :total="total" @pagination="load" />
    </el-card>
  </div>
</template>
<script setup lang="ts" name="AiDecisionIndex">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { listDecisionRuns, type DecisionPageQuery, type DecisionRunSummary } from '@/api/ai/decision';
import { decisionRiskLabel, decisionRunStatusLabel, riskTagType } from './decisionViewModel';
import { decisionBusinessObjectDisplay } from './decisionDisplay';
import { decisionRiskSeriesData } from './riskChartData';
const router = useRouter();
const filters = reactive<DecisionPageQuery>({ pageNum: 1, pageSize: 20, status: '', riskLevel: '' });
const rows = ref<DecisionRunSummary[]>([]);
const total = ref(0);
const loading = ref(false);
const chartEl = ref<HTMLDivElement>();
let chart: echarts.ECharts | null = null;
const cards = computed(() => [
  { label: '本页总运行', count: rows.value.length },
  { label: '本页高风险', count: rows.value.filter((r) => ['HIGH', 'CRITICAL'].includes(r.riskLevel ?? '')).length },
  { label: '本页待审批', count: rows.value.filter((r) => r.status === 'WAITING_APPROVAL').length },
  { label: '本页已完成', count: rows.value.filter((r) => r.status === 'SUCCEEDED').length }
]);
const highRisk = computed(() => rows.value.filter((r) => r.riskLevel === 'HIGH' || r.riskLevel === 'CRITICAL'));
function updateChart() {
  if (!chartEl.value) return;
  if (rows.value.length === 0) {
    chart?.clear();
    return;
  }
  chart ||= echarts.init(chartEl.value);
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['36%', '67%'],
        data: decisionRiskSeriesData(rows.value)
      }
    ]
  });
}
async function load() {
  loading.value = true;
  try {
    const response = await listDecisionRuns(filters);
    rows.value = (response.rows ?? []) as DecisionRunSummary[];
    total.value = Number(response.total ?? 0);
    await nextTick();
    updateChart();
  } finally {
    loading.value = false;
  }
}
function search() {
  filters.pageNum = 1;
  void load();
}
function reset() {
  filters.pageNum = 1;
  filters.status = '';
  filters.riskLevel = '';
  void load();
}
function openDetail(row: DecisionRunSummary) {
  void router.push({ path: '/ai/decision/detail', query: { runId: String(row.id) } });
}
onMounted(() => {
  void load();
});
onBeforeUnmount(() => chart?.dispose());
</script>
