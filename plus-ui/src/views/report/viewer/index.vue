<template>
  <ReportPage
    :title="published?.dsl.title || '报表运行'"
    description="设置查询条件，运行报表并查看或导出结果。"
    icon="i-ep-document"
    class="report-viewer"
  >
    <template #badge
      ><el-tag v-if="published" effect="plain" round>发布版本 v{{ published.number }}</el-tag></template
    >
    <template #actions
      ><el-button v-hasPermi="['report:runtime:export']" @click="router.push('/report/exports')">导出中心</el-button
      ><el-button icon="Back" @click="router.push('/report/center')">返回报表中心</el-button></template
    >
    <ReportViewerPanel :state="panel" />
  </ReportPage>
</template>
<script setup lang="ts">
import { computed, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ReportPage from '@/components/report/ReportPage.vue';
import ReportViewerPanel from '@/components/report/viewer/ReportViewerPanel.vue';
import { useReportRun } from '@/components/report/viewer/useReportRun';
import { useReportPrint } from '@/components/report/viewer/useReportPrint';
defineOptions({ name: 'ReportViewer' });
const route = useRoute();
const router = useRouter();
/** 路由仅适配上下文，取数、排版和打印使用共用状态逻辑。 */
const source = computed(() => ({
  reportId: typeof route.query.id === 'string' ? route.query.id : '',
  expectedVersionId: typeof route.query.versionId === 'string' ? route.query.versionId : undefined,
  parameters: route.query,
  autoPreview: route.query.auto === '1',
  runId: typeof route.query.runId === 'string' ? route.query.runId : undefined
}));
const viewer = useReportRun(source);
const printer = useReportPrint(viewer.run, viewer.data, viewer.active, viewer.accessDenied);
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
  invalidate,
  load,
  execute,
  loadPage,
  sizeChanged,
  sortChanged
} = viewer;
const {
  printJob,
  printing,
  exporting,
  printError,
  pdfOpen,
  ownsPrint,
  availableFormats,
  formatLabels,
  stopPrint,
  printPdf,
  pollPrint,
  retryPrint,
  exportReport,
  loadPrintPdf
} = printer;
const panel = reactive({ ...viewer, ...printer });
/** 保留完整页面的受控操作入口，便于既有交互回归。 */
defineExpose({
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
  invalidate,
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
  stopPrint,
  printPdf,
  pollPrint,
  retryPrint,
  exportReport,
  loadPrintPdf
});
</script>
