<template>
  <template v-if="recordIds">
    <el-button v-if="canBatch" plain icon="Printer" :disabled="recordIds.length === 0 || recordIds.length > 50" @click="batchOpen = true"
      >批量打印{{ recordIds.length ? `（${recordIds.length}）` : '' }}</el-button
    >
    <BusinessBatchDialog v-if="canBatch" v-model:open="batchOpen" :dataset-code="datasetCode" :parameter="parameter" :record-ids="recordIds" />
  </template>
  <el-dropdown
    v-else
    v-hasPermi="['report:runtime:preview']"
    :disabled="!exactRecordId"
    trigger="click"
    @visible-change="(value) => value && load()"
    @command="open"
  >
    <el-button link type="primary" :disabled="!exactRecordId">预览/打印</el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="report in reports" :key="report.id" :command="report.id">{{ report.name }}</el-dropdown-item>
        <el-dropdown-item v-if="!reports.length" disabled>{{ loading ? '加载中' : '没有可用的已发布单据模板' }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { businessReportTemplates, type BusinessReportTemplate } from '@/api/report';
import { checkPermi } from '@/utils/permission';
import { useUserStore } from '@/store/modules/user';
import { RequestGate } from '@/views/report/model';
import BusinessBatchDialog from './BusinessBatchDialog.vue';

/** 业务列表入口以字符串传递精确标识，多选入口固定参数和顺序。 */
const props = defineProps<{
  /** 受控业务数据集编码。 */
  datasetCode: string;
  /** 数据集声明的单据参数名。 */
  parameter: string;
  /** 行内单张标识，拒绝已经失真的数字。 */
  recordId?: string | number;
  /** 工具栏按当前勾选顺序传入的标识。 */
  recordIds?: (string | number)[];
}>();
const router = useRouter();
const userStore = useUserStore();
const reports = ref<BusinessReportTemplate[]>([]);
const loading = ref(false);
const batchOpen = ref(false);
const gate = new RequestGate();
const canBatch = computed(
  () =>
    checkPermi(['report:runtime:preview']) &&
    checkPermi(['report:runtime:export']) &&
    checkPermi(['report:batch:manage']) &&
    checkPermi(['report:batch:list'])
);
const exactRecordId = computed(() =>
  typeof props.recordId === 'string' ? props.recordId.trim() : Number.isSafeInteger(props.recordId) ? String(props.recordId) : ''
);

/** 业务入口只展示可访问且已有固定版本的打印单据。 */
async function load() {
  if (!checkPermi(['report:runtime:preview'])) return;
  const ticket = gate.begin();
  loading.value = true;
  reports.value = [];
  try {
    const response = await businessReportTemplates(props.datasetCode);
    if (gate.current(ticket))
      reports.value = response.data.filter((report) => report.kind === 'DOCUMENT' && typeof report.versionId === 'string' && !!report.versionId);
  } catch {
    if (gate.current(ticket)) reports.value = [];
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}

/** 只允许打开本次授权目录中的模板，自动预览标记只作用于当前业务参数。 */
function open(id: string) {
  const report = reports.value.find((item) => item.id === id);
  if (!report || !exactRecordId.value || !checkPermi(['report:runtime:preview'])) return;
  void router.push({ path: '/report/viewer', query: { id, versionId: report.versionId, auto: '1', [props.parameter]: exactRecordId.value } });
}
watch(
  () => [props.datasetCode, props.parameter, props.recordId, userStore.userId, userStore.tenantId],
  () => {
    gate.invalidate();
    reports.value = [];
    loading.value = false;
    batchOpen.value = false;
  }
);
onUnmounted(() => gate.invalidate());
</script>
