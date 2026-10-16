<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-alert v-if="report?.truncated" title="BOM 已截断，仅展示前 10,000 行；请下载原文核对完整内容" type="warning" :closable="false" class="mb-3" />
    <el-empty v-if="!artifact" description="BOM 尚未上传" />
    <el-table v-else :data="report?.rows || []" border empty-text="暂无可展示的 BOM 条目">
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :min-width="column.width"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ row[column.prop] ?? '—' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts" name="PcbBomTable">
import { onBeforeUnmount, ref, watch } from 'vue';
import { getEngineeringArtifactReport, type EngineeringArtifact } from '@/api/ai/engineering';
import { parseBomReport, type BomReport } from '@/views/ai/engineering/artifact-model';
import { engineeringError } from '@/views/ai/engineering/model';
const props = defineProps<{ artifact?: EngineeringArtifact }>();
const report = ref<BomReport>();
const error = ref('');
const loading = ref(false);
let controller: AbortController | undefined;
const columns = [
  { prop: 'designator', label: '位号', width: 120 },
  { prop: 'value', label: '值', width: 140 },
  { prop: 'footprint', label: '封装', width: 140 },
  { prop: 'manufacturerPartNumber', label: '厂商料号', width: 180 },
  { prop: 'supplierPartNumber', label: '供应商料号', width: 180 },
  { prop: 'quantity', label: '数量', width: 100 }
];
/** 报告切换时中止旧请求，不把缺值替换为虚构的物料匹配。 */
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
    const parsed = parseBomReport(response.data);
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
