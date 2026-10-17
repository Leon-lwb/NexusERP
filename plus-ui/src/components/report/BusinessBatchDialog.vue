<template>
  <BusinessReportDrawer :open="open" title="业务单据批量打印" :batch-id="created?.id" @update:open="emit('update:open', $event)">
    <section v-if="!viewingResult">
      <el-alert title="按下列顺序生成固定版本单据，最多50份。提交后版本、参数和顺序保持固定。" type="info" :closable="false" class="mb-4" />
      <el-alert v-if="invalid" :title="invalid" type="error" :closable="false" class="mb-4" />
      <el-alert v-if="locked" :title="`已固定提交编码：${pending?.code}。重试沿用原始内容。`" type="info" :closable="false" class="mb-4" />
      <el-form label-position="top" :disabled="busy || locked">
        <el-form-item label="已发布单据模板">
          <el-select v-model="versionId" :loading="loading" placeholder="选择固定版本" class="w-full">
            <el-option v-for="report in reports" :key="report.versionId" :value="report.versionId" :label="`${report.name} / ${report.versionId}`" />
          </el-select>
          <el-button v-if="loadFailed" link @click="load">重新载入模板</el-button>
        </el-form-item>
        <el-form-item label="批次标题"><el-input v-model="title" maxlength="100" /></el-form-item>
      </el-form>
      <el-table :data="displayRecords" max-height="380" empty-text="请先勾选业务单据">
        <el-table-column type="index" label="提交顺序" width="100" />
        <el-table-column prop="code" label="单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="counterparty" label="客户 / 供应商" min-width="150" show-overflow-tooltip />
        <el-table-column prop="id" label="完整标识" min-width="190" show-overflow-tooltip />
        <el-table-column label="调整顺序" width="150">
          <template #default="scope">
            <el-button link :disabled="busy || locked || scope.$index === 0" @click="move(scope.$index, -1)">上移</el-button>
            <el-button link :disabled="busy || locked || scope.$index === records.length - 1" @click="move(scope.$index, 1)">下移</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <BatchResultPanel v-if="viewingResult" :state="resultState" :documents="displayRecords" @new-batch="newBatch" />
    <template #footer>
      <el-button @click="emit('update:open', false)">关闭</el-button>
      <el-button v-if="viewingResult" @click="viewingResult = false">返回确认</el-button>
      <el-button v-else-if="created" @click="showResult">查看原批次</el-button>
      <el-button v-if="locked" :disabled="busy" @click="newBatch">新建其他批次</el-button>
      <el-button v-if="canSubmit && !viewingResult" type="primary" :loading="busy" :disabled="!!invalid || (!locked && !versionId)" @click="submit">
        {{ created ? '重试入队' : locked ? '重试原批次' : '确认并入队' }}
      </el-button>
    </template>
  </BusinessReportDrawer>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { businessReportTemplates, type BusinessReportTemplate } from '@/api/report';
import { checkPermi } from '@/utils/permission';
import { useUserStore } from '@/store/modules/user';
import { RequestGate } from '@/views/report/model';
import { useBatchSubmission } from './useBatchSubmission';
import { captureBusinessDocuments, blockedBatchItem, type BusinessDocumentRef } from './businessDocument';
import { useBatchResult } from './batch/useBatchResult';
import type { ReportResource } from '@/api/report/query';
import BusinessReportDrawer from './BusinessReportDrawer.vue';
import BatchResultPanel from './batch/BatchResultPanel.vue';

/** 多选列表先确认模板与顺序，再交给既有批次执行器。 */
const props = defineProps<{
  /** 弹窗受控开关。 */
  open: boolean;
  /** 固定业务数据集编码。 */
  datasetCode: string;
  /** 单据参数契约名。 */
  parameter: string;
  /** 本次打开时固定的勾选顺序。 */
  recordIds: (string | number)[];
  /** 与勾选项匹配的展示字段。 */
  documents?: BusinessDocumentRef[];
  /** 工具栏持有的提交状态，关闭抽屉不丢失幂等意图。 */
  submission?: ReturnType<typeof useBatchSubmission>;
}>();
const emit = defineEmits<{
  /** 将关闭意图交给业务入口。 */
  'update:open': [value: boolean];
}>();
const userStore = useUserStore();
const reports = ref<BusinessReportTemplate[]>([]);
const versionId = ref('');
const records = ref<BusinessDocumentRef[]>([]);
const blockedNumbers = ref<number[]>([]);
const displayRecords = computed(() =>
  records.value.map((record, index) => (blockedNumbers.value.includes(index) ? { id: '', code: '不可访问单据' } : record))
);
const title = ref('业务单据批量打印');
const loading = ref(false);
const loadFailed = ref(false);
const invalid = ref('');
const gate = new RequestGate();
const fallbackSubmission = useBatchSubmission();
const submission = props.submission ?? fallbackSubmission;
const { busy, locked, pending, created } = submission;
const viewingResult = ref(false);
const directory = ref<ReportResource[]>([]);
const result = useBatchResult(
  directory,
  computed(() => props.open && viewingResult.value),
  busy
);
const resultState = reactive(result);
watch(
  result.items,
  (items) => {
    const blocked = items.filter(blockedBatchItem).map((item) => item.number);
    if (blocked.length) blockedNumbers.value = [...new Set([...blockedNumbers.value, ...blocked])];
  },
  { flush: 'sync' }
);
const canSubmit = computed(
  () =>
    checkPermi(['report:batch:manage']) &&
    checkPermi(['report:batch:list']) &&
    checkPermi(['report:runtime:preview']) &&
    checkPermi(['report:runtime:export'])
);

/** 打开时固定当前选择，精度已丢失的数字不能再转成业务ID。 */
function captureRecords() {
  blockedNumbers.value = [];
  records.value = captureBusinessDocuments(props.recordIds, props.documents);
  invalid.value =
    !records.value.length || records.value.length > 50
      ? '请选择1至50份单据后再创建批次'
      : records.value.some((row) => !row.id)
        ? '单据ID无效，请刷新列表后重新选择'
        : '';
}

/** 读取授权目录时隔离关闭、切换和迟到响应，已提交版本不再改变。 */
async function load() {
  const ticket = gate.begin();
  if (!props.open || locked.value || !canSubmit.value) return;
  loading.value = true;
  loadFailed.value = false;
  reports.value = [];
  versionId.value = '';
  try {
    const result = await businessReportTemplates(props.datasetCode);
    if (!gate.current(ticket)) return;
    reports.value = result.data.filter((report) => report.kind === 'DOCUMENT' && typeof report.versionId === 'string' && !!report.versionId);
    if (reports.value.length === 1) versionId.value = reports.value[0].versionId;
  } catch {
    if (gate.current(ticket)) loadFailed.value = true;
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}

/** 顺序只在提交前调整，数组中的精确字符串保持原样。 */
function move(index: number, offset: number) {
  if (locked.value || busy.value || index + offset < 0 || index + offset >= records.value.length) return;
  const [row] = records.value.splice(index, 1);
  records.value.splice(index + offset, 0, row);
}

/** 显式放弃继续重试原编码后，按当前勾选重新准备新批次。 */
function newBatch() {
  if (busy.value) return;
  viewingResult.value = false;
  result.stop();
  result.selected.value = '';
  directory.value = [];
  submission.reset();
  captureRecords();
  void load();
}

/** 成功批次留在当前列表内展示，关闭重开继续观察同一批次。 */
async function showResult() {
  if (!props.open || !created.value || !canSubmit.value) return;
  directory.value = [created.value];
  viewingResult.value = true;
  if (result.selected.value !== created.value.id || (!result.detailLoading.value && !result.items.value.length)) await result.open(created.value.id);
}

/** 使用目录中的固定版本创建批次；失败重试完全复用首次提交。 */
async function submit() {
  if (!props.open || !canSubmit.value || busy.value || invalid.value) return;
  if (!locked.value && !reports.value.some((report) => report.versionId === versionId.value)) {
    ElMessage.warning('请选择已发布的打印单据模板');
    return;
  }
  const result = await submission.submit(
    title.value,
    records.value.map((row) => ({ reportVersionId: versionId.value, parameters: { [props.parameter]: row.id } }))
  );
  if (!result || !props.open) return;
  ElMessage.success('批次已创建，可查看逐项进度并领取合并PDF');
  await showResult();
}

watch(
  () => [props.open, props.datasetCode, props.parameter],
  () => {
    gate.invalidate();
    submission.suspend();
    loading.value = false;
    if (!props.open) {
      return;
    }
    if (submission.stage.value === 'DONE' && created.value) {
      void showResult();
      return;
    }
    if (!locked.value) {
      captureRecords();
      void load();
    }
  },
  { immediate: true, flush: 'sync' }
);
onUnmounted(() => {
  gate.invalidate();
  submission.suspend();
});
watch(
  () => [userStore.userId, userStore.tenantId],
  () => {
    gate.invalidate();
    reports.value = [];
    records.value = [];
    blockedNumbers.value = [];
    versionId.value = '';
    loading.value = false;
    invalid.value = '登录身份已变化，请关闭弹窗并重新选择业务单据';
  },
  { flush: 'sync' }
);
</script>
