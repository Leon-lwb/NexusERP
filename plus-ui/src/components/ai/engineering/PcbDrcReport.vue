<template>
  <div v-loading="loading">
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <el-tag :type="presentation.type">{{ presentation.label }}</el-tag>
      <span>错误：{{ result?.errorCount ?? '—' }}，警告：{{ result?.warningCount ?? '—' }}</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-alert v-if="result?.message" :title="result.message" type="info" :closable="false" class="mb-3" />
    <el-alert
      v-if="report && summary && report.status !== summary.status"
      title="报告内容与服务端清单状态不一致，请下载原文核对；当前以服务端记录为准"
      type="warning"
      :closable="false"
      class="mb-3"
    />
    <el-alert v-if="report?.truncated" title="DRC 报告已截断，仅展示前 10,000 条；请下载原文核对" type="warning" :closable="false" class="mb-3" />
    <el-empty v-if="!artifact" description="DRC 报告尚未上传；缺少报告不代表检查通过" />
    <el-table v-else :data="report?.violations || []" border empty-text="报告未列出违规条目，检查结论以上方实际状态为准">
      <el-table-column prop="severity" label="级别" width="120" />
      <el-table-column prop="rule" label="规则" min-width="140" />
      <el-table-column prop="designator" label="位号" width="120" />
      <el-table-column prop="message" label="实际检查说明" min-width="260" />
    </el-table>
  </div>
</template>

<script setup lang="ts" name="PcbDrcReport">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getEngineeringArtifactReport, type EngineeringArtifact, type DrcSummary } from '@/api/ai/engineering';
import { parseDrcReport, type DrcReport } from '@/views/ai/engineering/artifact-model';
import { drcPresentation, engineeringError } from '@/views/ai/engineering/model';
const props = defineProps<{ artifact?: EngineeringArtifact; summary?: DrcSummary }>();
const report = ref<DrcReport>();
const error = ref('');
const loading = ref(false);
let controller: AbortController | undefined;
const result = computed(() => props.summary || report.value);
const presentation = computed(() => drcPresentation(result.value?.status));
/** 只有明确的 PASS 才显示通过，不以空报告代替检查结果。 */
async function loadReport() {
  controller?.abort();
  const current = new AbortController();
  controller = current;
  report.value = undefined;
  error.value = '';
  loading.value = !!props.artifact;
  if (!props.artifact) return;
  try {
    const response = await getEngineeringArtifactReport(props.artifact.artifactId, current.signal);
    const parsed = parseDrcReport(response.data);
    if (!current.signal.aborted) report.value = parsed;
  } catch (cause) {
    if (!current.signal.aborted) error.value = engineeringError(cause).message;
  } finally {
    if (!current.signal.aborted) loading.value = false;
  }
}
watch(() => props.artifact?.artifactId, loadReport, { immediate: true });
onBeforeUnmount(() => controller?.abort());
</script>
