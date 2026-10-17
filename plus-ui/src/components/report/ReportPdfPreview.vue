<template>
  <el-dialog
    :model-value="open"
    title="PDF预览与打印"
    width="90%"
    top="4vh"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:open', $event)"
  >
    <div v-loading="loading" class="report-pdf-preview">
      <el-alert v-if="failed" :title="failed" type="error" show-icon :closable="false" />
      <iframe v-if="url" :key="url" ref="frame" :src="url" title="固定快照PDF预览" />
      <el-empty v-else-if="!loading && !failed" description="暂无可预览的PDF" />
    </div>
    <template #footer>
      <span class="report-pdf-preview__hint">在PDF查看器中点击打印，或使用系统打印快捷键。</span>
      <el-button v-if="failed" @click="loadPdf">重新读取文件</el-button>
      <el-button v-if="canDownload" v-hasPermi="['report:runtime:export']" :disabled="!url" @click="download">下载PDF</el-button>
      <el-button v-if="canDownload" v-hasPermi="['report:runtime:export']" type="primary" :disabled="!url" @click="print">系统打印</el-button>
      <el-button @click="emit('update:open', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, shallowRef, watch, onUnmounted, onDeactivated } from 'vue';
import { saveAs } from 'file-saver';
import { checkPermi } from '@/utils/permission';
import { RequestGate } from '@/views/report/model';
import { readReportFailure } from '@/api/report/errors';

/** 受控Blob预览不使用外部文件地址，切换和关闭均立即回收。 */
const props = defineProps<{
  /** 弹窗受控开关。 */
  open: boolean;
  /** 当前批次或任务标识，作为异步文件请求围栏。 */
  sourceId: string;
  /** 经父级权限检查的固定文件读取操作。 */
  load: () => Promise<Blob>;
  /** 下载文件名。 */
  filename: string;
  /** 父级核对owner后的领取资格，后端仍会再次鉴权。 */
  canDownload: boolean;
}>();
const emit = defineEmits<{
  /** 通知父级同步关闭状态。 */
  'update:open': [value: boolean];
}>();
const url = ref('');
const loading = ref(false);
const failed = ref('');
const blob = shallowRef<Blob>();
const frame = ref<HTMLIFrameElement>();
const gate = new RequestGate();

/** 立即撤销文件访问地址，并使尚未完成的下载失效。 */
function release() {
  gate.invalidate();
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = '';
  blob.value = undefined;
  loading.value = false;
}

/** 文件必须具有PDF签名，JSON错误信封永远不能送进查看器。 */
async function loadPdf() {
  release();
  failed.value = '';
  if (!props.open || !props.sourceId || !props.canDownload || !checkPermi(['report:runtime:export'])) return;
  const ticket = gate.begin();
  loading.value = true;
  try {
    const result = await props.load();
    if (!gate.current(ticket)) return;
    const header = await result.slice(0, 1024).text();
    if (!gate.current(ticket)) return;
    if (result.type.toLowerCase().includes('json') || !header.startsWith('%PDF-')) {
      const failure = await readReportFailure({ response: { data: result } });
      if (gate.current(ticket)) failed.value = failure.code ? failure.message : 'PDF文件不可用，权限可能已变化或文件已过期，请刷新结果后重试';
      return;
    }
    blob.value = result.type === 'application/pdf' ? result : new Blob([result], { type: 'application/pdf' });
    url.value = URL.createObjectURL(blob.value);
  } catch (error) {
    const failure = await readReportFailure(error);
    if (gate.current(ticket)) failed.value = failure.code ? failure.message : 'PDF读取失败，请核对当前权限和有效期后重试';
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}

/** 复用已验证的同一份文件，不再次读取业务或生成任务。 */
function download() {
  if (props.open && props.canDownload && blob.value && url.value && checkPermi(['report:runtime:export'])) saveAs(blob.value, props.filename);
}

/** 调用浏览器原生PDF的打印功能，兼容以查看器菜单打印。 */
function print() {
  if (!props.open || !props.canDownload || !url.value || !checkPermi(['report:runtime:export'])) return;
  try {
    frame.value?.contentWindow?.focus();
    frame.value?.contentWindow?.print();
  } catch {
    frame.value?.focus();
  }
}

watch(() => [props.open, props.sourceId, props.canDownload, checkPermi(['report:runtime:export'])], loadPdf, { immediate: true, flush: 'sync' });
onUnmounted(release);
onDeactivated(release);
</script>
<style scoped>
.report-pdf-preview {
  min-height: 65vh;
}
.report-pdf-preview iframe {
  width: 100%;
  height: 72vh;
  border: 0;
}
.report-pdf-preview__hint {
  margin-right: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
