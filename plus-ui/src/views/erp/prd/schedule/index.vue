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
        <el-button type="success" plain icon="View" :disabled="!toolbarPlan" @click="handleOpenSelectedGantt">甘特图</el-button>
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
      <el-col v-if="toolbarPlan" :span="4" class="schedule-toolbar-target">
        <el-text truncated :title="toolbarPlan.planNo">当前工具栏方案：{{ toolbarPlan.planNo }}</el-text>
      </el-col>
      <el-col v-if="checkedPlans.length > 1" :span="4"><el-text type="warning">请仅勾选一条方案查看或发布</el-text></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table
      ref="planTableRef"
      v-loading="loading"
      :data="planList"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
      show-overflow-tooltip
    >
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
      <el-table-column label="产能口径" align="center" min-width="240">
        <template #default="scope">
          <el-tag :type="scope.row.capacityCalcVersion === 2 ? 'info' : 'warning'">{{ scheduleCapacityLabel(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click.stop="handleViewGantt(scope.row)">甘特图</el-button>
          <el-tooltip
            v-if="isPublishableSchedulePlan(scope.row)"
            content="生成时无产能日历按 8h/日估算不代表可发布；发布需对应资源同日期真实 ACTIVE 班次产能日历及足额剩余产能，最终由后端发布事务校验。"
            placement="top"
          >
            <el-button link type="warning" v-hasPermi="['prd:schedule:publish']" @click.stop="handlePublishRow(scope.row)">发布（需核验）</el-button>
          </el-tooltip>
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
            参与排产: CREATED/APPROVED/RELEASED/IN_PROGRESS 状态工单; 委外工序不占产能; 假期产能为 0。无产能日历日期在生成时暂按 8h/日估算。
          </template>
        </el-alert>
        <el-alert type="warning" :closable="false" show-icon class="mt-2">
          <template #title>生成方案不等于可发布</template>
          非委外逐日负荷必须有对应资源、日期和班次的 ACTIVE
          产能日历及足够剩余产能；无对应日历时即使生成成功也不能发布，实际以服务端发布校验为准。生成的排程版本 2
          不等于可发布；服务端发布成功后才能用于可信 AI 交付风险分析。
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
          <el-button
            type="primary"
            plain
            icon="MagicStick"
            size="small"
            :disabled="!ganttPlan || !gantt || !isDecisionReadySchedulePlan(ganttPlan)"
            @click="handleDeliveryDecision"
            v-hasPermi="['ai:decision:run']"
            >交付风险分析</el-button
          >
          <el-button link type="info" size="small" disabled>AI 文字解读（不支持按当前方案）</el-button>
        </div>
      </template>
      <div v-loading="ganttLoading" class="gantt-body">
        <template v-if="gantt">
          <el-alert
            v-if="isLegacySchedulePlan(ganttPlan)"
            type="warning"
            :closable="false"
            show-icon
            class="mb-3"
            title="历史口径，非新版可信产能：旧方案保留原状态和甘特数据，不可作为新版发布"
          />
          <el-tag v-else-if="ganttPlan?.capacityCalcVersion === 2" type="info" class="mb-3">排程版本 2</el-tag>
          <el-alert v-else type="warning" :closable="false" show-icon class="mb-3" title="版本未识别，非新版可信产能：不可作为新排程版本 2 发布" />
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
  </div>
</template>

<script setup name="PrdSchedule" lang="ts">
import { listSchedule, runSchedule, publishSchedule, delSchedule, getScheduleGantt } from '@/api/erp/prd/schedule';
import { SchedulePlanVO, SchedulePlanQuery, ScheduleRunForm, ScheduleGanttVO } from '@/api/erp/prd/schedule/types';
import {
  formatScheduleGanttTooltip,
  isLegacySchedulePlan,
  isPublishableSchedulePlan,
  isDecisionReadySchedulePlan,
  scheduleCapacityLabel
} from './schedulePolicy';
import * as echarts from 'echarts';
import dayjs from 'dayjs';
import { runDeliveryRisk } from '@/api/ai/decision';
import { useRoute, useRouter } from 'vue-router';
import { decisionScheduleResultQuery, decisionScheduleResultRows } from '@/views/ai/decision/decisionActionExecutionView';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();
const route = useRoute();

const loading = ref(false);
const running = ref(false);
const planList = ref<SchedulePlanVO[]>([]);
const planTableRef = ref<{ clearSelection: () => void } | null>(null);
let listRequestSeq = 0;
const total = ref(0);
const ids = ref<Array<string>>([]);
const single = ref(true);
const multiple = ref(true);
const showSearch = ref(true);
const showRunDialog = ref(false);
const showGanttDrawer = ref(false);
const selectedPlanId = ref<string>('');
const selectedPlan = ref<SchedulePlanVO | null>(null);
const checkedPlans = ref<SchedulePlanVO[]>([]);
let selectionRevision = 0;
const ganttLoading = ref(false);
const gantt = ref<ScheduleGanttVO | null>(null);
const ganttPlan = ref<SchedulePlanVO | null>(null);
let ganttRequestSeq = 0;
const ganttChartRef = ref<HTMLElement | null>(null);
let ganttChart: echarts.ECharts | null = null;

const initQuery: SchedulePlanQuery = { pageNum: 1, pageSize: 10 };
const queryParams = ref<SchedulePlanQuery>({ ...initQuery });
const runForm = ref<ScheduleRunForm>({ startDate: '', horizonDays: 14, strategy: 'EDD' });
const runFormRef = ref<ElFormInstance>();
const runRules = ref({
  startDate: [{ required: true, message: '排产起始日不能为空', trigger: 'change' }],
  horizonDays: [{ required: true, message: '跨度不能为空', trigger: 'blur' }]
});

const ganttTitle = computed(() => (ganttPlan.value ? `排程甘特图 - ${ganttPlan.value.planNo}` : '排程甘特图'));
const toolbarPlan = computed(() => {
  if (loading.value || checkedPlans.value.length > 1) return null;
  const planId = checkedPlans.value.length === 1 ? checkedPlans.value[0].planId : selectedPlanId.value;
  return planList.value.find((plan) => plan.planId === planId) ?? null;
});
const canPublish = computed(() => isPublishableSchedulePlan(toolbarPlan.value));

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

const clearListSelection = () => {
  selectedPlanId.value = '';
  selectedPlan.value = null;
  checkedPlans.value = [];
  ids.value = [];
  single.value = true;
  multiple.value = true;
};

const getList = async () => {
  const requestSeq = ++listRequestSeq;
  loading.value = true;
  clearListSelection();
  planTableRef.value?.clearSelection();
  if (showGanttDrawer.value) {
    showGanttDrawer.value = false;
    ganttRequestSeq++;
    clearGantt();
    ganttLoading.value = false;
  }
  try {
    const res = await listSchedule({ ...queryParams.value });
    if (requestSeq !== listRequestSeq) return;
    /** 决策结果深链只定位真实行；编号模糊匹配不能替代结果身份。 */
    const resultQuery = decisionScheduleResultQuery(route.query);
    const locatingResult = resultQuery && queryParams.value.planNo === resultQuery.code ? resultQuery : null;
    planList.value = decisionScheduleResultRows(res.rows, locatingResult);
    total.value = locatingResult ? planList.value.length : res.total;
    clearListSelection();
  } catch (error) {
    if (requestSeq === listRequestSeq) {
      planList.value = [];
      total.value = 0;
      clearListSelection();
    }
    throw error;
  } finally {
    if (requestSeq === listRequestSeq) loading.value = false;
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
  selectionRevision++;
  const selectedRows = new Set(selection);
  checkedPlans.value = planList.value.filter((plan) => selectedRows.has(plan));
  ids.value = checkedPlans.value.map((plan) => plan.planId);
  single.value = checkedPlans.value.length !== 1;
  multiple.value = !checkedPlans.value.length;
};

const handleRowClick = (row: SchedulePlanVO) => {
  selectedPlanId.value = row.planId;
  selectedPlan.value = row;
  if (showGanttDrawer.value) return handleViewGantt(row);
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
  if (!isPublishableSchedulePlan(row)) {
    proxy?.$modal.msgWarning('仅已生成的排程版本 2 可发布；历史口径或已发布方案不能作为新版发布');
    return;
  }
  await proxy?.$modal.confirm(`确认发布方案 ${row.planNo}？末序完工日期将回写工单计划起止日期。`);
  await publishSchedule(row.planId);
  row.status = 'PUBLISHED';
  if (selectedPlan.value?.planId === row.planId) selectedPlan.value.status = 'PUBLISHED';
  proxy?.$modal.msgSuccess('发布成功');
  getList();
};

const handlePublish = async () => {
  if (toolbarPlan.value) await handlePublishRow(toolbarPlan.value);
};

const handleDelete = async () => {
  if (loading.value || !ids.value.length) return;
  const confirmedIds = [...ids.value];
  const confirmedSelectionRevision = selectionRevision;
  const confirmedListRequestSeq = listRequestSeq;
  const confirmedQuery = JSON.stringify(queryParams.value);
  await proxy?.$modal.confirm(`确认删除选中的 ${confirmedIds.length} 个排程方案？`);
  if (
    selectionRevision !== confirmedSelectionRevision ||
    listRequestSeq !== confirmedListRequestSeq ||
    JSON.stringify(queryParams.value) !== confirmedQuery ||
    ids.value.length !== confirmedIds.length ||
    confirmedIds.some((id) => !ids.value.includes(id))
  ) {
    proxy?.$modal.msgWarning('勾选或筛选条件已变化，请重新确认删除');
    return;
  }
  await delSchedule(confirmedIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

/**
 * 先执行确定性分析，不将计划的雪花标识转换为 Number。
 */
const handleDeliveryDecision = async () => {
  if (!showGanttDrawer.value || !isDecisionReadySchedulePlan(ganttPlan.value) || !gantt.value) return;
  try {
    const res = await runDeliveryRisk({
      bizType: 'SCHEDULE_PLAN',
      bizId: String(ganttPlan.value.planId),
      horizonDays: gantt.value.horizonDays || 30,
      triggerType: 'MANUAL'
    });
    await router.push({ path: '/ai/decision/detail', query: { runId: String(res.data.runId) } });
  } catch {
    proxy?.$modal.msgError('交付风险分析未能启动，请检查权限或稍后重试');
  }
};

const clearGantt = () => {
  gantt.value = null;
  ganttPlan.value = null;
  ganttChart?.dispose();
  ganttChart = null;
};

const handleOpenSelectedGantt = () => {
  if (toolbarPlan.value) return handleViewGantt(toolbarPlan.value);
};

const handleViewGantt = async (row: SchedulePlanVO) => {
  const requestSeq = ++ganttRequestSeq;
  clearGantt();
  showGanttDrawer.value = true;
  ganttLoading.value = true;
  try {
    const res = await getScheduleGantt(row.planId);
    if (requestSeq !== ganttRequestSeq || !showGanttDrawer.value) return;
    if (String(res.data?.planId) !== String(row.planId)) {
      proxy?.$modal.msgError('甘特数据与所选方案不匹配，请重试');
      return;
    }
    ganttPlan.value = row;
    gantt.value = res.data;
    await nextTick();
    if (requestSeq === ganttRequestSeq && showGanttDrawer.value) renderGantt();
  } catch {
    if (requestSeq === ganttRequestSeq && showGanttDrawer.value) proxy?.$modal.msgError('甘特图加载失败，请重试');
  } finally {
    if (requestSeq === ganttRequestSeq) ganttLoading.value = false;
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
  type BarData = { value: (number | string)[]; itemStyle: any };
  const seriesData: Record<string, BarData[]> = {};
  rows.forEach((row, rowIndex) => {
    row.bars.forEach((bar, barIndex) => {
      const startOffset = dayjs(bar.startTime).diff(dayStart, 'day');
      const days = Math.max(bar.durationDays || 1, 1);
      const key = bar.resourceType;
      if (!seriesData[key]) seriesData[key] = [];
      seriesData[key].push({
        value: [rowIndex, startOffset, startOffset + days, bar.processName || '', barIndex],
        itemStyle: { color: row.delay ? '#f56c6c' : RESOURCE_COLORS[key] || '#909399' }
      });
    });
  });
  const horizon = gantt.value.horizonDays || 14;
  ganttChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const row = rows[params.value[0]];
        return formatScheduleGanttTooltip(row, row?.bars[params.value[4]]);
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

const applyDecisionResultQuery = () => {
  const resultQuery = decisionScheduleResultQuery(route.query);
  if (resultQuery) {
    /** 仅合法结果深链重置无关筛选，旧状态/策略不能排除真实结果；普通查询不改。 */
    queryParams.value = { ...initQuery, planNo: resultQuery.code };
  }
};

onMounted(() => {
  applyDecisionResultQuery();
  getList();
});
/** 同页跳转仍只触发只读列表查询，不自动发布或执行排程。 */
watch(
  () => [route.query.decisionResultPlanId, route.query.planNo],
  () => {
    applyDecisionResultQuery();
    getList();
  }
);

onBeforeUnmount(() => {
  listRequestSeq++;
  ganttRequestSeq++;
  clearGantt();
  ganttLoading.value = false;
});

watch(showGanttDrawer, (v) => {
  if (!v) {
    ganttRequestSeq++;
    clearGantt();
    ganttLoading.value = false;
  }
});
</script>

<style scoped lang="scss">
.schedule-toolbar-target {
  display: flex;
  align-items: center;
  min-width: 0;
  :deep(.el-text) {
    width: 100%;
  }
}
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
