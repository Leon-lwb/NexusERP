<template>
  <div v-if="modelValue" class="flex items-center gap-3 my-3">
    <el-tag :type="isCurrent ? 'success' : 'warning'">契约 {{ version?.number || '…' }} {{ isCurrent ? '当前版本' : '固定历史版本' }}</el-tag>
    <el-button v-if="!isCurrent && head" :loading="upgrading" :disabled="busy" @click="upgrade">比较并升级契约</el-button>
    <span v-if="failed" class="text-red-600">契约读取失败，字段编辑暂不可用</span>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { datasetVersion, datasetVersionDiff } from '@/api/report';
import type { ReportDsl, DatasetView, DatasetVersionView } from '@/types/report';
import { RequestGate } from '@/views/report/model';
import { upgradeDatasetVersion } from '@/views/report/designer/editorCommands';
const props = defineProps<{ modelValue: ReportDsl; datasets: DatasetView[]; busy: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [ReportDsl]; contract: [DatasetVersionView | undefined] }>();
const version = ref<DatasetVersionView>();
const failed = ref(false);
const upgrading = ref(false);
const gate = new RequestGate();
const upgradeGate = new RequestGate();
const head = computed(() => props.datasets.find((d) => d.dataset.code === props.modelValue.datasetCode));
const isCurrent = computed(() => head.value?.dataset.publishedVersion === props.modelValue.datasetVersionId);
watch(
  () => props.modelValue.datasetVersionId,
  async (id) => {
    upgradeGate.invalidate();
    upgrading.value = false;
    const ticket = gate.begin();
    version.value = undefined;
    failed.value = false;
    emit('contract', undefined);
    try {
      const response = await datasetVersion(id);
      if (!gate.current(ticket) || props.modelValue.datasetVersionId !== id) return;
      version.value = response.data;
      emit('contract', response.data);
    } catch {
      if (gate.current(ticket)) failed.value = true;
    }
  },
  { immediate: true }
);
async function upgrade() {
  if (!head.value || props.busy || upgrading.value) return;
  const from = props.modelValue.datasetVersionId;
  const target = head.value.dataset.publishedVersion;
  const start = JSON.stringify(props.modelValue);
  const ticket = upgradeGate.begin();
  upgrading.value = true;
  try {
    const [diff, targetVersion] = await Promise.all([datasetVersionDiff(from, target), datasetVersion(target)]);
    if (!upgradeGate.current(ticket)) return;
    await ElMessageBox.confirm(diff.data.changes.map((c) => c.name + '：' + c.kind).join('；') || '契约兼容，可升级至当前版本', '确认升级数据契约', {
      type: diff.data.breaking ? 'warning' : 'info'
    });
    if (!upgradeGate.current(ticket)) return;
    if (JSON.stringify(props.modelValue) !== start) {
      ElMessage.warning('设计已变化，请重新比较契约');
      return;
    }
    emit('update:modelValue', upgradeDatasetVersion(props.modelValue, targetVersion.data));
    ElMessage.success('已更新草稿契约；请校验受影响字段后保存');
  } catch {
  } finally {
    if (upgradeGate.current(ticket)) upgrading.value = false;
  }
}
onUnmounted(() => {
  gate.invalidate();
  upgradeGate.invalidate();
});
</script>
