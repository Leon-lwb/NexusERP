<template>
  <el-drawer
    :model-value="open"
    :title="title || '单据预览与打印'"
    size="min(1180px, 96vw)"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:open', $event)"
  >
    <template #header>
      <div class="business-print-heading">
        <strong>{{ title || '单据预览与打印' }}</strong>
        <el-button v-if="batchId || viewer.published.value" link type="primary" @click="fullPage">打开完整页</el-button>
      </div>
    </template>
    <div class="business-print-content report-page">
      <slot><ReportViewerPanel v-if="reportId" :state="panel" /></slot>
    </div>
    <template #footer
      ><slot name="footer"><el-button @click="emit('update:open', false)">关闭</el-button></slot></template
    >
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { useReportRun } from './viewer/useReportRun';
import { useReportPrint } from './viewer/useReportPrint';
import ReportViewerPanel from './viewer/ReportViewerPanel.vue';

/** 业务页提供预览上下文，关闭抽屉保留列表筛选及勾选。 */
const props = defineProps<{
  /** 受控开关。 */
  open: boolean;
  /** 用户可识别的标题。 */
  title?: string;
  /** 单张模板资源。 */
  reportId?: string;
  /** 已确认的发布版本。 */
  versionId?: string;
  /** 精确字符串业务参数。 */
  parameters?: Record<string, string>;
  /** 批次模式复用相同抽屉外框。 */
  batchId?: string;
}>();
const emit = defineEmits<{ 'update:open': [value: boolean] }>();
const router = useRouter();
const source = computed(() => ({
  reportId: props.reportId || '',
  expectedVersionId: props.versionId,
  parameters: props.parameters,
  autoPreview: true,
  active: props.open && !!props.reportId
}));
const viewer = useReportRun(source);
const printer = useReportPrint(viewer.run, viewer.data, viewer.active, viewer.accessDenied);
const panel = reactive({ ...viewer, ...printer });

/** 完整页重新鉴权并复用当前run，避免导航再次查询业务。 */
function fullPage() {
  if (props.batchId) void router.push({ path: '/report/batch', query: { id: props.batchId } });
  else if (viewer.published.value && props.reportId) {
    void router.push({
      path: '/report/viewer',
      query: {
        id: props.reportId,
        versionId: viewer.published.value.versionId,
        ...viewer.parameters.value,
        ...(viewer.run.value ? { runId: viewer.run.value.id } : { auto: '1' })
      }
    });
  } else return;
  emit('update:open', false);
}
</script>
<style lang="scss" src="./report-ui.scss"></style>
<style scoped>
.business-print-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  flex-wrap: wrap;
}
.business-print-heading strong {
  overflow-wrap: anywhere;
}
.business-print-content {
  padding: 0;
  min-width: 0;
  overflow-x: auto;
}
</style>
