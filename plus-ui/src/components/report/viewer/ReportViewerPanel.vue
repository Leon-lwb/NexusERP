<template>
  <section class="report-viewer-content">
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-card v-else-if="!published" shadow="never"
      ><el-empty :description="loadFailed ? loadError || '报表载入失败，请重新选择或重试' : '请选择已发布且有权访问的报表'"
        ><el-button v-if="loadFailed" @click="load">重新载入</el-button></el-empty
      ></el-card
    >
    <template v-else>
      <el-card shadow="never" class="report-panel viewer-parameters">
        <div class="report-section-heading">
          <div>
            <h2>查询条件</h2>
            <p class="report-section-description">
              {{ published.dsl.parameters.length ? '带 * 的参数为必填，留空的可选项使用报告默认值。' : '此报表无需参数，可直接运行。' }}
            </p>
          </div>
          <el-tag type="info" effect="plain">{{
            published.dsl.kind === 'DOCUMENT' ? '打印单据' : published.dsl.kind === 'PIVOT' ? '交叉报表' : '列表报表'
          }}</el-tag>
        </div>
        <ParameterForm
          v-if="published.dsl.parameters.length"
          :dataset-version-id="published.dsl.datasetVersionId"
          v-model="parameters"
          :parameters="published.effectiveParameters ?? published.dsl.parameters"
        />
        <div class="run-toolbar">
          <el-radio-group v-model="mode" :disabled="published.dsl.kind === 'DOCUMENT' || requiresSnapshot" aria-label="报表运行模式"
            ><el-radio-button value="LIVE">实时分页</el-radio-button><el-radio-button value="SNAPSHOT">固定快照</el-radio-button></el-radio-group
          >
          <el-button v-hasPermi="['report:runtime:preview']" type="primary" icon="VideoPlay" :loading="running" @click="execute">运行报表</el-button>
        </div>
        <el-alert
          v-if="requiresSnapshot"
          :title="
            published.dsl.kind === 'DOCUMENT'
              ? '打印单据使用固定快照，预览和导出共享同一份数据。'
              : '此报表包含聚合计算或分组汇总，运行时使用固定快照。'
          "
          type="info"
          show-icon
          :closable="false"
          class="mt-4"
        />
        <el-alert
          v-if="run?.mode === 'LIVE'"
          title="实时分页导出会重新获取全量数据，可能与当前结果不同；需保持预览与导出一致时，请使用固定快照运行。"
          type="info"
          show-icon
          :closable="false"
          class="mt-4"
        />
      </el-card>
      <el-card v-loading="running || pageLoading" shadow="never" class="viewer-results" aria-live="polite">
        <div class="report-section-heading">
          <div>
            <h2>运行结果</h2>
            <p class="report-section-description">{{ run ? '结果按本次运行的数据范围展示' : '运行后在此查看数据、汇总与打印版式' }}</p>
          </div>
          <div class="viewer-export-actions">
            <el-button
              v-if="run && published.dsl.kind === 'DOCUMENT'"
              v-hasPermi="['report:runtime:export']"
              type="primary"
              icon="Printer"
              :loading="printing"
              @click="printPdf"
              >打印PDF</el-button
            >
            <el-dropdown v-if="run" v-hasPermi="['report:runtime:export']" :disabled="exporting" @command="exportReport"
              ><el-button :loading="exporting" icon="Download"
                >创建导出<el-icon class="ml-2"><ArrowDown /></el-icon></el-button
              ><template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item v-for="format in availableFormats" :key="format" :command="format">{{
                    formatLabels[format]
                  }}</el-dropdown-item></el-dropdown-menu
                ></template
              ></el-dropdown
            >
          </div>
        </div>
        <el-alert v-if="runFailed" :title="runFailed" type="error" show-icon :closable="false" class="mb-4" />
        <div v-if="printJob || printError" class="mb-4" aria-live="polite">
          <span v-if="printJob"
            >PDF任务：{{ printJob.id }} · {{ printJob.state }} · {{ printJob.phase || '' }} {{ printJob.processedRows ?? 0
            }}<span v-if="printJob.totalRows != null"> / {{ printJob.totalRows }}</span></span
          >
          <el-alert v-if="printError" :title="printError" type="error" :closable="false" />
          <el-button v-if="printJob?.canRetry" v-hasPermi="['report:runtime:export']" link :disabled="printing" @click="retryPrint"
            >重试原PDF任务</el-button
          >
          <el-button
            v-if="printJob && printError && ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(printJob.state)"
            v-hasPermi="['report:runtime:export']"
            link
            @click="pollPrint"
            >刷新任务进度</el-button
          >
        </div>
        <div v-if="run && data" class="report-result-meta">
          <el-tag :type="run.mode === 'SNAPSHOT' ? 'success' : 'info'" effect="plain">{{ run.mode === 'SNAPSHOT' ? '固定快照' : '实时分页' }}</el-tag
          ><span>数据时间：{{ data.dataTime }}</span
          ><span>共 {{ data.total }} 条</span><span>运行版本：{{ run.version }}</span>
        </div>
        <div class="viewer-result-content">
          <PivotTable v-if="run && published.dsl.kind === 'PIVOT'" :run-id="run.id" /><ReportPaper v-else-if="plan && run" :plan="plan" /><template
            v-else-if="data && run"
            ><TypedTable :columns="data.columns" :rows="data.rows" :sortable="run.mode === 'LIVE'" @sort="sortChanged" />
            <div class="report-pagination">
              <el-pagination
                v-model:current-page="page"
                v-model:page-size="size"
                :page-sizes="[20, 50, 100, 200]"
                :total="data.total"
                layout="total, sizes, prev, pager, next"
                @current-change="loadPage"
                @size-change="sizeChanged"
              /></div></template
          ><el-empty v-else-if="!running && !runFailed" description="报表尚未运行，请先设置条件并点击运行报表" :image-size="100" />
        </div>
        <div v-if="data?.summaries.length" class="summary-bar">
          <div v-for="(summary, index) in data.summaries" :key="index">
            <span
              >{{ summary.label
              }}<small v-if="Object.keys(summary.group).length">（{{ summaryGroupText(summary.group, published.dsl.columns) }}）</small></span
            ><strong>{{ summary.value.value ?? '—' }}</strong>
          </div>
        </div>
      </el-card>
    </template>

    <ReportPdfPreview
      v-model:open="pdfOpen"
      :source-id="printJob?.id || ''"
      :load="loadPrintPdf"
      :filename="`nexus-report-${printJob?.id || ''}.pdf`"
      :can-download="ownsPrint"
    />
  </section>
</template>
<script setup lang="ts">
import { toRefs, type UnwrapNestedRefs } from 'vue';
import { ArrowDown } from '@element-plus/icons-vue';
import type { useReportRun } from './useReportRun';
import type { useReportPrint } from './useReportPrint';
import { jobError } from '@/views/report/model';
import { summaryGroupText } from '@/views/report/presentation';
import ParameterForm from '../ParameterForm.vue';
import TypedTable from '../TypedTable.vue';
import ReportPaper from '../ReportPaper.vue';
import PivotTable from '../PivotTable.vue';
import ReportPdfPreview from '../ReportPdfPreview.vue';
/** 内容组件只展示同一运行状态，可由完整页和业务抽屉复用。 */
type ViewerState = UnwrapNestedRefs<ReturnType<typeof useReportRun> & ReturnType<typeof useReportPrint>>;
const props = defineProps<{ state: ViewerState }>();
const {
  published,
  parameters,
  mode,
  run,
  data,
  plan,
  loading,
  loadFailed,
  loadError,
  runFailed,
  pageLoading,
  running,
  page,
  size,
  requiresSnapshot,
  load,
  execute,
  loadPage,
  sizeChanged,
  sortChanged,
  printJob,
  printing,
  exporting,
  printError,
  pdfOpen,
  ownsPrint,
  availableFormats,
  formatLabels,
  printPdf,
  pollPrint,
  retryPrint,
  exportReport,
  loadPrintPdf
} = toRefs(props.state);
</script>
<style scoped>
.run-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 8px;
}
.viewer-export-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.viewer-results {
  min-height: 300px;
}
.viewer-result-content {
  overflow: auto;
  min-width: 0;
}
.summary-bar {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 20px;
  background: var(--report-muted);
  border: 1px solid var(--report-line);
  border-radius: 8px;
  margin-top: 20px;
}
.summary-bar > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 120px;
}
.summary-bar span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.summary-bar strong {
  font-size: 20px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
</style>
