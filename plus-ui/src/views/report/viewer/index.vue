<template>
  <div class="p-4">
    <el-card v-loading="loading" shadow="never"
      ><template #header
        ><div class="viewer-heading">
          <div>
            <strong class="text-xl">{{ published?.dsl.title || '报表预览' }}</strong
            ><el-tag v-if="published" class="ml-3" effect="plain">发布版本 v{{ published.number }}</el-tag>
          </div>
          <el-button @click="router.push('/report/center')">返回报表中心</el-button>
        </div></template
      >
      <el-empty v-if="!published && !loading" description="请选择已发布且有权访问的报表" />
      <template v-if="published"
        ><ParameterForm :dataset-version-id="published?.dsl.datasetVersionId" v-model="parameters" :parameters="published.dsl.parameters" />
        <div class="run-toolbar">
          <el-radio-group v-model="mode" :disabled="published.dsl.kind === 'DOCUMENT' || requiresSnapshot"
            ><el-radio-button value="LIVE">实时分页</el-radio-button><el-radio-button value="SNAPSHOT">固定快照</el-radio-button></el-radio-group
          ><el-button v-hasPermi="['report:runtime:preview']" type="primary" icon="Search" :loading="running" @click="execute">运行报表</el-button
          ><el-dropdown v-if="run" v-hasPermi="['report:runtime:export']" :disabled="exporting" @command="exportReport"
            ><el-button :loading="exporting"
              >创建导出<el-icon class="ml-2"><ArrowDown /></el-icon></el-button
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item v-for="format in availableFormats" :key="format" :command="format">{{
                  formatLabels[format]
                }}</el-dropdown-item></el-dropdown-menu
              ></template
            ></el-dropdown
          ><el-button v-hasPermi="['report:runtime:export']" link @click="router.push('/report/exports')">前往导出中心</el-button>
        </div>
        <el-alert
          v-if="requiresSnapshot"
          :title="
            published.dsl.kind === 'DOCUMENT'
              ? '打印单据使用固定快照，预览和导出共享同一份数据。'
              : '此报表包含聚合计算或分组汇总，运行时使用固定快照。'
          "
          type="info"
          :closable="false"
          class="mt-4"
        />
        <el-alert
          v-if="run?.mode === 'LIVE'"
          title="实时分页导出会重新获取全量数据，可能与当前结果不同；需保持预览与导出一致时，请使用固定快照运行。"
          type="info"
          :closable="false"
          class="mt-4"
        />
        <div v-if="run && data" class="data-meta">
          <el-tag :type="run.mode === 'SNAPSHOT' ? 'success' : 'info'">{{ run.mode === 'SNAPSHOT' ? '固定快照' : '实时分页' }}</el-tag
          ><span>数据时间：{{ data.dataTime }}</span
          ><span>共 {{ data.total }} 条</span><span>运行版本：{{ run.version }}</span>
        </div>
        <PivotTable v-if="run && published?.dsl.kind === 'PIVOT'" :run-id="run.id" />
        <ReportPaper v-else-if="plan && run" :plan="plan" />
        <template v-else-if="data && run"
          ><TypedTable :columns="data.columns" :rows="data.rows" :sortable="run.mode === 'LIVE'" @sort="sortChanged" /><el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            class="mt-4"
            :page-sizes="[20, 50, 100, 200]"
            :total="data.total"
            layout="total, sizes, prev, pager, next"
            @current-change="loadPage"
            @size-change="sizeChanged"
        /></template>
        <div v-if="data?.summaries.length" class="summary-bar">
          <span v-for="(summary, index) in data.summaries" :key="index"
            >{{ summary.label
            }}<small v-if="Object.keys(summary.group).length"
              >（{{
                Object.entries(summary.group)
                  .map(([k, v]) => k + '=' + v.value)
                  .join('，')
              }}）</small
            >：<strong>{{ summary.value.value ?? '—' }}</strong></span
          >
        </div>
      </template>
    </el-card>
  </div>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, computed, watch, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { publishedReport, createRun, runPage, runLayout, enqueueExport } from '@/api/report';
import type { PublishedReport, ReportRun, PageData, RenderPlan, ExportFormat } from '@/types/report';
import { parameterInput, exportFormats, requiresSnapshot as snapshotRequired, RequestGate } from '@/views/report/model';
import ParameterForm from '@/components/report/ParameterForm.vue';
import TypedTable from '@/components/report/TypedTable.vue';
import ReportPaper from '@/components/report/ReportPaper.vue';
import PivotTable from '@/components/report/PivotTable.vue';
defineOptions({ name: 'ReportViewer' });
/**
 * 保存当前页面或测试的route状态。
 */
const route = useRoute();
/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的published状态。
 */
const published = ref<PublishedReport>();
/**
 * 保存当前页面或测试的parameters状态。
 */
const parameters = ref<Record<string, string>>({});
/**
 * 保存当前页面或测试的mode状态。
 */
const mode = ref('LIVE');
/**
 * 保存当前页面或测试的run状态。
 */
const run = ref<ReportRun>();
/**
 * 保存当前页面或测试的data状态。
 */
const data = ref<PageData>();
/**
 * 保存当前页面或测试的plan状态。
 */
const plan = ref<RenderPlan>();
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的running状态。
 */
const running = ref(false);
/**
 * 保存当前页面或测试的exporting状态。
 */
const exporting = ref(false);
/**
 * 保存当前页面或测试的page状态。
 */
const page = ref(1);
/**
 * 保存当前页面或测试的size状态。
 */
const size = ref(50);
/**
 * 保存当前页面或测试的sort状态。
 */
let sort: string | undefined;
/**
 * 保存当前页面或测试的descending状态。
 */
let descending = false;
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/**
 * 保存当前页面或测试的pageGate状态。
 */
const pageGate = new RequestGate();
/**
 * 保存当前页面或测试的exportGate状态。
 */
const exportGate = new RequestGate();
/**
 * 保存当前页面或测试的keys状态。
 */
const keys = new Map<string, string>();
/**
 * 保存当前页面或测试的formatLabels状态。
 */
const formatLabels: Record<ExportFormat, string> = { PDF: 'PDF 打印文件', XLSX_DATA: '明细 Excel', XLSX_LAYOUT: '样式 Excel' };
/**
 * 保存当前页面或测试的requiresSnapshot状态。
 */
const requiresSnapshot = computed(() => !!published.value && snapshotRequired(published.value.dsl));
/**
 * 保存当前页面或测试的availableFormats状态。
 */
const availableFormats = computed(() => exportFormats(run.value?.mode || 'LIVE', data.value?.total || 0));
/** 处理当前报表的 `invalidate` 操作。 */
function invalidate() {
  gate.invalidate();
  pageGate.invalidate();
  exportGate.invalidate();
  exporting.value = false;
  run.value = undefined;
  data.value = undefined;
  plan.value = undefined;
  keys.clear();
  running.value = false;
}
/** 处理当前报表的 `load` 操作。 */
async function load() {
  invalidate();
  published.value = undefined;
  parameters.value = {};
  const id = String(route.query.id || '');
  loading.value = false;
  if (!id) return;
  const ticket = gate.begin();
  loading.value = true;
  try {
    const response = await publishedReport(id);
    if (gate.current(ticket)) {
      published.value = response.data;
      for (const parameter of response.data.dsl.parameters) {
        const value = route.query[parameter.name];
        if (typeof value === 'string' && value.length <= 200) parameters.value[parameter.name] = value;
      }
      mode.value = response.data.dsl.kind === 'DOCUMENT' || requiresSnapshot.value ? 'SNAPSHOT' : 'LIVE';
    }
  } catch {
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 以当前任务围栏执行取数和导出。 */
async function execute() {
  if (!published.value) return;
  invalidate();
  const ticket = gate.begin();
  running.value = true;
  page.value = 1;
  sort = undefined;
  try {
    const result = await createRun(String(route.query.id), mode.value, parameterInput(parameters.value));
    if (!gate.current(ticket)) return;
    run.value = result.data;
    const response = await runPage(result.data.id, 1, size.value);
    if (!gate.current(ticket)) return;
    data.value = response.data;
    if (published.value.dsl.kind === 'DOCUMENT') {
      const layout = await runLayout(result.data.id);
      if (gate.current(ticket)) plan.value = layout.data;
    }
  } catch {
    if (gate.current(ticket)) {
      run.value = undefined;
      data.value = undefined;
    }
  } finally {
    if (gate.current(ticket)) running.value = false;
  }
}
/** 处理当前报表的 `loadPage` 操作。 */
async function loadPage() {
  if (!run.value) return;
  const ticket = pageGate.begin();
  const id = run.value.id;
  try {
    const result = await runPage(id, page.value, size.value, sort, descending);
    if (pageGate.current(ticket) && run.value?.id === id) data.value = result.data;
  } catch {
    if (pageGate.current(ticket)) {
      data.value = undefined;
      run.value = undefined;
    }
  }
}
/** 处理当前报表的 `sizeChanged` 操作。 */
function sizeChanged() {
  page.value = 1;
  void loadPage();
}
/** 处理当前报表的 `sortChanged` 操作。 */
function sortChanged(field: string | undefined, desc: boolean) {
  sort = field;
  descending = desc;
  page.value = 1;
  void loadPage();
}
/** 处理当前报表的 `exportReport` 操作。 */
async function exportReport(format: ExportFormat) {
  if (!run.value || exporting.value || !availableFormats.value.includes(format)) return;
  const ticket = exportGate.begin();
  const origin = run.value.id;
  const signature = origin + ':' + format;
  const requestKey = keys.get(signature) || crypto.randomUUID();
  keys.set(signature, requestKey);
  exporting.value = true;
  try {
    const result = await enqueueExport(origin, format, requestKey);
    if (!exportGate.current(ticket)) return;
    if (['FAILED', 'CANCELLED', 'EXPIRED'].includes(result.data.state)) keys.delete(signature);
    ElMessage.success('导出任务已创建，可在导出中心查看');
  } catch {
  } finally {
    if (exportGate.current(ticket)) exporting.value = false;
  }
}
watch(() => route.query, load, { immediate: true, deep: true, flush: 'sync' });
watch(
  parameters,
  () => {
    if (!loading.value) invalidate();
  },
  { deep: true, flush: 'sync' }
);
watch(
  mode,
  () => {
    if (!loading.value) invalidate();
  },
  { flush: 'sync' }
);
onUnmounted(invalidate);
</script>
<style scoped>
.viewer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.run-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.data-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  margin: 18px 0;
  color: #64748b;
  font-size: 13px;
}
.summary-bar {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 18px;
  background: #f5f7fa;
  margin-top: 12px;
}
</style>
