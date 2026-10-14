<template>
  <el-dropdown
    v-hasPermi="['report:runtime:preview']"
    :disabled="!recordId"
    trigger="click"
    @visible-change="(value) => value && load()"
    @command="open"
    ><el-button link type="primary">报表预览/打印</el-button
    ><template #dropdown
      ><el-dropdown-menu
        ><el-dropdown-item v-for="report in reports" :key="report.id" :command="report.id">{{ report.name }}</el-dropdown-item
        ><el-dropdown-item v-if="!reports.length" disabled>{{ loading ? '加载中' : '没有可用的已发布模板' }}</el-dropdown-item></el-dropdown-menu
      ></template
    ></el-dropdown
  >
</template>
<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { businessReportTemplates } from '@/api/report';
import { RequestGate } from '@/views/report/model';
const props = defineProps<{ datasetCode: string; parameter: string; recordId: string | number | undefined }>();
const router = useRouter();
const reports = ref<{ id: string; name: string }[]>([]);
const loading = ref(false);
const gate = new RequestGate();
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const response = await businessReportTemplates(props.datasetCode);
    if (gate.current(ticket)) reports.value = response.data;
  } catch {
    if (gate.current(ticket)) reports.value = [];
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
function open(id: string) {
  if (props.recordId == null) return;
  router.push({ path: '/report/viewer', query: { id, [props.parameter]: String(props.recordId) } });
}
watch(
  () => props.datasetCode,
  () => {
    gate.invalidate();
    reports.value = [];
  }
);
onUnmounted(() => gate.invalidate());
</script>
