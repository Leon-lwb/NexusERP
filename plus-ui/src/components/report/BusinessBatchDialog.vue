<template>
  <el-dialog :model-value="open" title="确认批量打印" width="720px" append-to-body @update:model-value="emit('update:open', $event)">
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
    <el-table :data="records" max-height="380" empty-text="请先勾选业务单据">
      <el-table-column type="index" label="提交顺序" width="100" />
      <el-table-column prop="id" label="单据标识（完整字符串）" min-width="220" />
      <el-table-column label="调整顺序" width="150">
        <template #default="scope">
          <el-button link :disabled="busy || locked || scope.$index === 0" @click="move(scope.$index, -1)">上移</el-button>
          <el-button link :disabled="busy || locked || scope.$index === records.length - 1" @click="move(scope.$index, 1)">下移</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:open', false)">关闭</el-button>
      <el-button v-if="locked" :disabled="busy" @click="newBatch">新建其他批次</el-button>
      <el-button v-if="canSubmit" type="primary" :loading="busy" :disabled="!!invalid || (!locked && !versionId)" @click="submit">
        {{ created ? '重试入队' : locked ? '重试原批次' : '确认并入队' }}
      </el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { businessReportTemplates, type BusinessReportTemplate } from '@/api/report';
import { checkPermi } from '@/utils/permission';
import { useUserStore } from '@/store/modules/user';
import { RequestGate } from '@/views/report/model';
import { useBatchSubmission } from './useBatchSubmission';

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
}>();
const emit = defineEmits<{
  /** 将关闭意图交给业务入口。 */
  'update:open': [value: boolean];
}>();
const router = useRouter();
const userStore = useUserStore();
const reports = ref<BusinessReportTemplate[]>([]);
const versionId = ref('');
const records = ref<{ id: string }[]>([]);
const title = ref('业务单据批量打印');
const loading = ref(false);
const loadFailed = ref(false);
const invalid = ref('');
const gate = new RequestGate();
const submission = useBatchSubmission();
const { busy, locked, pending, created } = submission;
const canSubmit = computed(
  () =>
    checkPermi(['report:batch:manage']) &&
    checkPermi(['report:batch:list']) &&
    checkPermi(['report:runtime:preview']) &&
    checkPermi(['report:runtime:export'])
);

/** 打开时固定当前选择，精度已丢失的数字不能再转成业务ID。 */
function captureRecords() {
  records.value = props.recordIds.map((id) => ({ id: typeof id === 'string' ? id.trim() : Number.isSafeInteger(id) ? String(id) : '' }));
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
  submission.reset();
  captureRecords();
  void load();
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
  emit('update:open', false);
  void router.push({ path: '/report/batch', query: { id: result.id } });
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
    if (submission.stage.value === 'DONE') submission.reset();
    if (!locked.value) {
      captureRecords();
      void load();
    }
  },
  { immediate: true, flush: 'sync' }
);
onUnmounted(() => gate.invalidate());
watch(
  () => [userStore.userId, userStore.tenantId],
  () => {
    gate.invalidate();
    reports.value = [];
    records.value = [];
    versionId.value = '';
    loading.value = false;
    invalid.value = '登录身份已变化，请关闭弹窗并重新选择业务单据';
  },
  { flush: 'sync' }
);
</script>
