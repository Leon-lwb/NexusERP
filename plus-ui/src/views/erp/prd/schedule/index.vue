<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="方案编号" prop="planNo">
            <el-input v-model="queryParams.planNo" placeholder="请输入方案编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="策略" prop="strategy">
            <el-select v-model="queryParams.strategy" placeholder="全部" clearable style="width: 100%">
              <el-option label="最早交期(EDD)" value="EDD" />
              <el-option label="临界比(CR)" value="CR" />
              <el-option label="优先级" value="PRIORITY" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option label="已生成" value="COMPLETED" />
              <el-option label="已发布" value="PUBLISHED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="MagicStick" @click="handleRun" v-hasPermi="['prd:schedule:run']">执行排产</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="View" :disabled="!selectedPlanId" @click="showGanttDrawer = true">甘特图</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Promotion" :disabled="!canPublish" @click="handlePublish" v-hasPermi="['prd:schedule:publish']"
          >发布</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:schedule:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange" @row-click="handleRowClick" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="方案编号" align="center" prop="planNo" width="150" />
      <el-table-column label="方案名称" align="center" prop="planName" min-width="140" />
      <el-table-column label="排产策略" align="center" prop="strategy" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.strategy === 'EDD' ? 'primary' : scope.row.strategy === 'CR' ? 'warning' : 'success'">
            {{ strategyLabel(scope.row.strategy) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="起始日" align="center" prop="startDate" width="100">
        <template #default="scope">{{ formatDate(scope.row.startDate) }}</template>
      </el-table-column>
      <el-table-column label="跨度(天)" align="center" prop="horizonDays" width="80" />
      <el-table-column label="工单数" align="center" prop="woCount" width="80" />
      <el-table-column label="任务数" align="center" prop="taskCount" width="80" />
      <el-table-column label="拖期数" align="center" prop="delayedCount" width="80">
        <template #default="scope">
          <span :style="{ color: scope.row.delayedCount > 0 ? '#f56c6c' : '', fontWeight: scope.row.delayedCount > 0 ? 'bold' : 'normal' }">
            {{ scope.row.delayedCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="跳过数" align="center" prop="skippedCount" width="80" />
      <el-table-column label="总工时(h)" align="center" prop="totalHours" width="90" />
      <el-table-column label="总完工日" align="center" prop="makespanDate" width="100">
        <template #default="scope">{{ formatDate(scope.row.makespanDate) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ scope.row.status === 'PUBLISHED' ? '已发布' : '已生成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click.stop="handleViewGantt(scope.row)">甘特图</el-button>
          <el-button
            link
            type="warning"
            v-if="scope.row.status === 'COMPLETED'"
            v-hasPermi="['prd:schedule:publish']"
            @click.stop="handlePublishRow(scope.row)"
            >发布</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 执行排产对话框 -->
    <el-dialog title="执行智能排产" v-model="showRunDialog" width="520px" append-to-body>
      <el-form ref="runFormRef" :model="runForm" :rules="runRules" label-width="100px">
        <el-form-item label="方案名称" prop="planName">
          <el-input v-model="runForm.planName" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="排产起始日" prop="startDate">
          <el-date-picker v-model="runForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择起始日" style="width: 100%" />
        </el-form-item>
        <el-form-item label="跨度(天)" prop="horizonDays">
          <el-input-number v-model="runForm.horizonDays" :min="1" :max="90" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序策略" prop="strategy">
          <el-radio-group v-model="runForm.strategy">
            <el-radio value="EDD">最早交期</el-radio>
            <el-radio value="CR">临界比</el-radio>
            <el-radio value="PRIORITY">优先级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            参与排产: CREATED/APPROVED/RELEASED/IN_PROGRESS 状态工单; 委外工序不占产能; 无产能日历日期默认 8h/日; 假期产能为 0。
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showRunDialog = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="submitRun">开始排产</el-button>
      </template>
    </el-dialog>

    <!-- 甘特图抽屉 -->
    <el-drawer v-model="showGanttDrawer" :title="ganttTitle" size="80%" destroy-on-close>
      <template #header>
        <div class="gantt-drawer-header">
          <span>{{ ganttTitle }}</span>
          <el-button type="primary" plain icon="MagicStick" size="small" @click="handleAiInterpret">AI 解读本方案</el-button>
        </div>
      </template>
      <div v-loading="ganttLoading" class="gantt-body">
        <template v-if="gantt">
          <el-row :gutter="12" class="gantt-summary">
            <el-col :span="4"
              ><div class="stat-card">
                <div class="stat-num">{{ gantt.woCount }}</div>
                <div class="stat-label">工单数</div>
              </div></el-col
            >
            <el-col :span="4"
              ><div class="stat-card">
                <div class="stat-num">{{ gantt.totalHours }}</div>
                <div class="stat-label">总工时(h)</div>
              </div></el-col
            >
            <el-col :span="4"
              ><div class="stat-card" :class="{ 'stat-danger': gantt.delayedCount > 0 }">
                <div class="stat-num">{{ gantt.delayedCount }}</div>
                <div class="stat-label">拖期工单</div>
              </div></el-col
            >
            <el-col :span="4"
              ><div class="stat-card">
                <div class="stat-num">{{ gantt.skippedCount }}</div>
                <div class="stat-label">跳过工单</div>
              </div></el-col
            >
            <el-col :span="4"
              ><div class="stat-card">
                <div class="stat-num">{{ strategyLabel(gantt.strategy) }}</div>
                <div class="stat-label">策略</div>
              </div></el-col
            >
            <el-col :span="4"
              ><div class="stat-card">
                <div class="stat-num">{{ gantt.horizonDays }}</div>
                <div class="stat-label">跨度(天)</div>
              </div></el-col
            >
          </el-row>

          <div ref="ganttChartRef" class="gantt-chart"></div>

          <el-divider content-position="left">资源利用率(瓶颈优先)</el-divider>
          <el-table :data="gantt.loads" size="small" max-height="260">
            <el-table-column label="资源" prop="resourceName" min-width="140" />
            <el-table-column label="资源键" prop="resourceKey" width="160" />
            <el-table-column label="已排工时(h)" prop="bookedHours" width="110" align="center" />
            <el-table-column label="可用工时(h)" prop="availableHours" width="110" align="center" />
            <el-table-column label="利用率" width="220" align="center">
              <template #default="scope">
                <el-progress
                  :percentage="Number(scope.row.rate) || 0"
                  :color="rateColor(Number(scope.row.rate) || 0)"
                  :stroke-width="14"
                  text-inside
                />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <!-- AI 助手抽屉(排产解读入口) -->
    <AiChatDrawer ref="aiChatRef" />
  </div>
</template>

<script setup name="PrdSchedule" lang="ts">
import { listSchedule, runSchedule, publishSchedule, delSchedule, getScheduleGantt } from '@/api/erp/prd/schedule';
import { SchedulePlanVO, SchedulePlanQuery, ScheduleRunForm, ScheduleGanttVO } from '@/api/erp/prd/schedule/types';
import * as echarts from 'echarts';
import dayjs from 'dayjs';
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const running = ref(false);
const planList = ref<SchedulePlanVO[]>([]);
const total = ref(0);
const ids = ref<Array<string>>([]);
const single = ref(true);
const multiple = ref(true);
const showSearch = ref(true);
const showRunDialog = ref(false);
const showGanttDrawer = ref(false);
const selectedPlanId = ref<string>('');
const selectedPlan = ref<SchedulePlanVO | null>(null);
const ganttLoading = ref(false);
const gantt = ref<ScheduleGanttVO | null>(null);
const ganttChartRef = ref<HTMLElement | null>(null);
let ganttChart: echarts.ECharts | null = null;
const aiChatRef = ref<InstanceType<typeof AiChatDrawer>>();

const initQuery: SchedulePlanQuery = { pageNum: 1, pageSize: 10 };
const queryParams = ref<SchedulePlanQuery>({ ...initQuery });
const runForm = ref<ScheduleRunForm>({ startDate: '', horizonDays: 14, strategy: 'EDD' });
const runFormRef = ref<ElFormInstance>();
const runRules = ref({
  startDate: [{ required: true, message: '排产起始日不能为空', trigger: 'change' }],
  horizonDays: [{ required: true, message: '跨度不能为空', trigger: 'blur' }]
});

const ganttTitle = computed(() => (selectedPlan.value ? `排程甘特图 - ${selectedPlan.value.planNo}` : '排程甘特图'));
const canPublish = computed(() => selectedPlan.value?.status === 'COMPLETED');

const strategyLabel = (code: string) => {
  const map: Record<string, string> = { EDD: '最早交期', CR: '临界比', PRIORITY: '优先级' };
  return map[code] || code;
};

const formatDate = (d?: string) => (d ? dayjs(d).format('YYYY-MM-DD') : '-');

const rateColor = (rate: number) => {
  if (rate >= 90) return '#f56c6c';
  if (rate >= 70) return '#e6a23c';
  return '#67c23a';
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listSchedule(queryParams.value);
    planList.value = res.rows;
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

const handleSelectionChange = (selection: SchedulePlanVO[]) => {
  ids.value = selection.map((item) => item.planId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const handleRowClick = (row: SchedulePlanVO) => {
  selectedPlanId.value = row.planId;
  selectedPlan.value = row;
};

const handleRun = () => {
  runForm.value = { startDate: dayjs().format('YYYY-MM-DD'), horizonDays: 14, strategy: 'EDD' };
  showRunDialog.value = true;
};

const submitRun = async () => {
  await runFormRef.value?.validate();
  running.value = true;
  try {
    await runSchedule(runForm.value);
    proxy?.$modal.msgSuccess('排产完成');
    showRunDialog.value = false;
    getList();
  } finally {
    running.value = false;
  }
};

const handlePublishRow = async (row: SchedulePlanVO) => {
  await proxy?.$modal.confirm(`确认发布方案 ${row.planNo}？末序完工日期将回写工单计划起止日期。`);
  await publishSchedule(row.planId);
  proxy?.$modal.msgSuccess('发布成功');
  getList();
};

const handlePublish = async () => {
  if (selectedPlan.value) {
    await handlePublishRow(selectedPlan.value);
  }
};

const handleDelete = async () => {
  await proxy?.$modal.confirm(`确认删除选中的 ${ids.value.length} 个排程方案？`);
  await delSchedule(ids.value);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

/** AI 解读: 唤起全局 AI 助手并预填分析指令(Agent 调排产工具作答) */
const handleAiInterpret = () => {
  if (!gantt.value) return;
  const prompt =
    `请基于智能排产工具(aps_schedule)分析最新排产方案 ${gantt.value.planNo}：` +
    `1) 哪些工单预计拖期、拖期多少天；2) 瓶颈资源是哪个、利用率多少；` +
    `3) 结合拖期工单交期与瓶颈资源，给出缓解建议（如调整优先级、加班、委外）。`;
  aiChatRef.value?.open(prompt);
};

const handleViewGantt = async (row: SchedulePlanVO) => {
  selectedPlanId.value = row.planId;
  selectedPlan.value = row;
  showGanttDrawer.value = true;
  ganttLoading.value = true;
  gantt.value = null;
  try {
    const res = await getScheduleGantt(row.planId);
    gantt.value = res.data;
    await nextTick();
    renderGantt();
  } finally {
    ganttLoading.value = false;
  }
};

const RESOURCE_COLORS: Record<string, string> = {
  WORKSTATION: '#409eff',
  WORKSHOP: '#67c23a',
  OUTSOURCE: '#e6a23c'
};

const renderGantt = () => {
  if (!ganttChartRef.value || !gantt.value) return;
  ganttChart = echarts.init(ganttChartRef.value);
  const rows = gantt.value.rows;
  const dayStart = dayjs(gantt.value.startDate).startOf('day');
  const categories = rows.map((r) => `${r.woNo}${r.materialName ? ' · ' + r.materialName : ''}${r.delay ? ' ⚠拖期' : ''}`);
  type BarData = { value: (number | string)[]; itemStyle: any; label?: any };
  const seriesData: Record<string, BarData[]> = {};
  rows.forEach((row, rowIndex) => {
    row.bars.forEach((bar) => {
      const startOffset = dayjs(bar.startTime).diff(dayStart, 'day');
      const days = Math.max(bar.durationDays || 1, 1);
      const key = bar.resourceType;
      if (!seriesData[key]) seriesData[key] = [];
      seriesData[key].push({
        value: [rowIndex, startOffset, startOffset + days, bar.processName || ''],
        itemStyle: { color: row.delay ? '#f56c6c' : RESOURCE_COLORS[key] || '#909399' }
      });
    });
  });
  const horizon = gantt.value.horizonDays || 14;
  ganttChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const bar = rows[params.value[0]]?.bars.find((b) => (b.seqNum ?? 0) >= 0) as any;
        const row = rows[params.value[0]];
        const startDay = dayStart.add(params.value[1], 'day').format('YYYY-MM-DD');
        const endDay = dayStart.add(params.value[2] - 1, 'day').format('YYYY-MM-DD');
        return `<b>${row?.woNo}</b><br/>工单: ${row?.materialName || '-'}<br/>起: ${startDay}<br/>止: ${endDay}`;
      }
    },
    grid: { left: 220, right: 40, top: 40, bottom: 40 },
    xAxis: {
      type: 'value',
      min: 0,
      max: horizon,
      interval: Math.max(1, Math.ceil(horizon / 14)),
      axisLabel: { formatter: (v: number) => `D${v}` },
      name: '天',
      splitLine: { show: true, lineStyle: { type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisLabel: { width: 200, overflow: 'truncate' }
    },
    series: Object.entries(seriesData).map(([type, data]) => ({
      name: type,
      type: 'custom',
      renderItem: (params: any, api: any) => {
        const rowIndex = api.value(0);
        const start = api.coord([api.value(1), rowIndex]);
        const end = api.coord([api.value(2), rowIndex]);
        const height = api.size([0, 1])[1] * 0.6;
        const rectShape = {
          x: start[0],
          y: start[1] - height / 2,
          width: Math.max(end[0] - start[0] - 1, 2),
          height: height
        };
        return { type: 'rect', shape: rectShape, style: api.style() };
      },
      encode: { x: [1, 2], y: 0 },
      data: data
    })),
    legend: { data: Object.keys(seriesData).map((k) => k), top: 5 }
  });
  ganttChart.resize();
};

onMounted(() => {
  getList();
});

onBeforeUnmount(() => {
  ganttChart?.dispose();
  ganttChart = null;
});

watch(showGanttDrawer, (v) => {
  if (!v) {
    ganttChart?.dispose();
    ganttChart = null;
  }
});
</script>

<style scoped lang="scss">
.gantt-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 32px;
}
.gantt-body {
  padding: 0 12px;
}
.gantt-summary {
  margin-bottom: 12px;
}
.stat-card {
  text-align: center;
  padding: 8px 4px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  .stat-num {
    font-size: 20px;
    font-weight: bold;
    color: var(--el-text-color-primary);
  }
  .stat-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }
  &.stat-danger .stat-num {
    color: var(--el-color-danger);
  }
}
.gantt-chart {
  width: 100%;
  height: 420px;
}
</style>
