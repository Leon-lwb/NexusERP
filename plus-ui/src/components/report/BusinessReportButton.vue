<template>
  <template v-if="recordIds">
    <el-button v-if="canBatch" plain icon="Printer" aria-label="打开业务打印批次" :disabled="!canOpenBatch" @click="batchOpen = true"
      >{{ submission.locked.value ? '继续原批次' : '批量打印'
      }}{{ !submission.locked.value && recordIds.length ? `（${recordIds.length}）` : '' }}</el-button
    >
    <BusinessBatchDialog
      v-if="canBatch"
      v-model:open="batchOpen"
      :dataset-code="datasetCode"
      :parameter="parameter"
      :record-ids="recordIds"
      :documents="documents"
      :submission="submission"
    />
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
        <el-dropdown-item v-if="loadFailed" command="$retry">模板读取失败，点击重试</el-dropdown-item>
        <el-dropdown-item v-else-if="!reports.length" disabled>{{ loading ? '加载中' : '没有可用的已发布单据模板' }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <BusinessReportDrawer
    v-if="!recordIds"
    v-model:open="previewOpen"
    :report-id="selectedTemplate?.id"
    :version-id="selectedTemplate?.versionId"
    :parameters="previewParameters"
    :title="document?.id === exactRecordId ? document.code : '单据预览与打印'"
  />
</template>
<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import { businessReportTemplates, type BusinessReportTemplate } from '@/api/report';
import { checkPermi } from '@/utils/permission';
import { useUserStore } from '@/store/modules/user';
import { RequestGate } from '@/views/report/model';
import BusinessBatchDialog from './BusinessBatchDialog.vue';
import BusinessReportDrawer from './BusinessReportDrawer.vue';
import { useBatchSubmission } from './useBatchSubmission';
import { exactBusinessId, type BusinessDocumentRef } from './businessDocument';

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
  /** 与完整ID匹配的单张展示信息。 */
  document?: BusinessDocumentRef;
  /** 与勾选项匹配的展示信息，提交仍只使用ID。 */
  documents?: BusinessDocumentRef[];
}>();
const userStore = useUserStore();
const reports = ref<BusinessReportTemplate[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
const batchOpen = ref(false);
const previewOpen = ref(false);
const selectedTemplate = ref<BusinessReportTemplate>();
const submission = useBatchSubmission();
const gate = new RequestGate();
const canBatch = computed(
  () =>
    checkPermi(['report:runtime:preview']) &&
    checkPermi(['report:runtime:export']) &&
    checkPermi(['report:batch:manage']) &&
    checkPermi(['report:batch:list'])
);
const exactRecordId = computed(() => exactBusinessId(props.recordId));
const canOpenBatch = computed(
  () => canBatch.value && (submission.locked.value || !!submission.created.value || (!!props.recordIds?.length && props.recordIds.length <= 50))
);
const previewParameters = computed(() => ({ [props.parameter]: exactRecordId.value }));

/** 业务入口只展示可访问且已有固定版本的打印单据。 */
async function load() {
  if (!checkPermi(['report:runtime:preview'])) return;
  const ticket = gate.begin();
  loading.value = true;
  loadFailed.value = false;
  reports.value = [];
  try {
    const response = await businessReportTemplates(props.datasetCode);
    if (gate.current(ticket))
      reports.value = response.data.filter((report) => report.kind === 'DOCUMENT' && typeof report.versionId === 'string' && !!report.versionId);
  } catch {
    if (gate.current(ticket)) {
      reports.value = [];
      loadFailed.value = true;
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}

/** 只允许打开本次授权目录中的模板，自动预览标记只作用于当前业务参数。 */
function open(id: string) {
  if (id === '$retry') {
    void load();
    return;
  }
  const report = reports.value.find((item) => item.id === id);
  if (!report || !exactRecordId.value || !checkPermi(['report:runtime:preview'])) return;
  selectedTemplate.value = report;
  previewOpen.value = true;
}
watch(
  () => [props.datasetCode, props.parameter, props.recordId, userStore.userId, userStore.tenantId],
  () => {
    gate.invalidate();
    reports.value = [];
    loading.value = false;
    batchOpen.value = false;
    previewOpen.value = false;
    selectedTemplate.value = undefined;
  }
);
watch(
  () => checkPermi(['report:runtime:preview']),
  (allowed) => {
    if (!allowed) previewOpen.value = false;
  },
  { flush: 'sync' }
);
onUnmounted(() => gate.invalidate());
</script>
