<template>
  <ReportPage title="批量单据" description="按确认顺序提交固定版本单据，逐项跟踪并领取合并PDF。" icon="i-ep-printer" class="report-batch">
    <template #actions>
      <el-button v-hasPermi="['report:batch:list']" :loading="loading" icon="Refresh" @click="load">刷新批次</el-button>
      <el-button v-hasPermi="['report:runtime:export']" @click="router.push('/report/exports')">导出中心</el-button>
    </template>
    <el-card v-if="canCreate" shadow="never" class="report-panel">
      <div class="report-section-heading">
        <div>
          <h2>创建打印批次</h2>
          <p class="report-section-description">模板版本、参数和提交顺序在首次提交时固定。</p>
        </div>
        <el-tag effect="plain">{{ documents.length }} / 50 份</el-tag>
      </div>
      <el-form label-position="top" :disabled="busy || locked">
        <el-form-item label="批次标题"><el-input v-model="title" placeholder="例如：本日出库单打印" maxlength="100" /></el-form-item>
      </el-form>
      <el-alert v-if="locked" :title="`固定批次编码：${pending?.code}。重试保持相同内容。`" type="info" :closable="false" class="mb-4" />
      <div v-for="(item, index) in documents" :key="item.key" class="batch-document">
        <span class="batch-document__number">{{ index + 1 }}</span>
        <div v-loading="item.loading" class="batch-document__form">
          <el-select
            v-model="item.reportVersionId"
            :disabled="busy || locked"
            placeholder="选择已发布打印单据"
            filterable
            @change="selectTemplate(item)"
          >
            <el-option
              v-for="report in reports"
              :key="report.id"
              :value="report.publishedVersion!"
              :label="`${report.name} / ${report.publishedVersion}`"
            />
          </el-select>
          <el-alert v-if="item.failed" :title="item.failed" type="error" :closable="false" />
          <el-button v-if="item.failed" link :disabled="busy || locked" @click="selectTemplate(item)">重新读取模板</el-button>
          <el-form :disabled="busy || locked">
            <ParameterForm
              v-if="item.template"
              v-model="item.parameters"
              :parameters="item.template.effectiveParameters ?? item.template.dsl.parameters"
              :dataset-version-id="item.template.dsl.datasetVersionId"
            />
          </el-form>
          <span v-if="item.template" class="report-section-description">固定版本：{{ item.template.versionId }}</span>
        </div>
        <div class="batch-document__actions">
          <el-button link :disabled="busy || locked || index === 0" @click="move(index, -1)">上移</el-button>
          <el-button link :disabled="busy || locked || index === documents.length - 1" @click="move(index, 1)">下移</el-button>
          <el-button link type="danger" :disabled="busy || locked || documents.length === 1" @click="remove(item)">移除</el-button>
        </div>
      </div>
      <div class="report-form-row batch-submit">
        <el-button icon="Plus" :disabled="busy || locked || documents.length >= 50" @click="addDocument">增加单据</el-button>
        <el-button v-if="locked" :disabled="busy" @click="newBatch">新建其他批次</el-button>
        <el-button type="primary" :loading="creating" :disabled="writeBusy" @click="create">{{
          created ? '重试入队' : locked ? '重试原批次' : '创建并入队'
        }}</el-button>
      </div>
    </el-card>
    <el-card v-if="canList" v-loading="loading" shadow="never" class="report-panel">
      <div class="report-section-heading">
        <div>
          <h2>批次记录</h2>
          <p class="report-section-description">可见资源仍由服务端逐次鉴权，文件由批次所有者领取。</p>
        </div>
      </div>
      <el-alert v-if="loadFailed" title="批次目录载入失败，请刷新重试。" type="error" :closable="false" class="mb-4" />
      <el-table :data="batches" empty-text="暂无可见批次">
        <el-table-column prop="title" label="批次名称" min-width="200" />
        <el-table-column prop="code" label="编码" min-width="220" />
        <el-table-column label="操作" min-width="220">
          <template #default="scope">
            <el-button link type="primary" @click="open(scope.row.id)">查看逐项结果</el-button>
            <el-button v-hasPermi="['report:acl:manage']" link @click="aclId = scope.row.id">资源授权</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="selected && canList" v-loading="detailLoading" shadow="never" class="report-panel">
      <div class="report-section-heading">
        <div>
          <h2>逐项结果</h2>
          <p class="report-section-description">当前批次：{{ selected }}；显示顺序即提交顺序。</p>
        </div>
        <div>
          <el-button @click="refreshItems(selected)">刷新结果</el-button>
          <el-button v-if="isOwner && canStart && items.some(needsStart)" :loading="writeBusy" @click="start(selected)">补充入队</el-button>
          <el-button v-if="canReadPdf && allSucceeded" type="primary" @click="pdfOpen = true">合并PDF预览/下载</el-button>
        </div>
      </div>
      <el-alert v-if="detailFailed" title="逐项结果读取失败，请刷新重试。" type="error" :closable="false" class="mb-4" />
      <el-table :data="items" empty-text="当前批次暂无结果">
        <el-table-column label="序号" width="70"
          ><template #default="scope">{{ scope.row.number + 1 }}</template></el-table-column
        >
        <el-table-column prop="reportVersionId" label="固定版本" min-width="180" />
        <el-table-column label="进度" min-width="180">
          <template #default="scope">
            <ReportStatus :status="scope.row.state" />
            <div v-if="scope.row.phase">
              {{ phaseLabels[scope.row.phase] || scope.row.phase }} {{ scope.row.processedRows ?? 0
              }}<span v-if="scope.row.totalRows != null"> / {{ scope.row.totalRows }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="错误/有效期" min-width="230">
          <template #default="scope">
            <div v-if="scope.row.error">{{ jobError(scope.row.error) }}（{{ scope.row.error }}）</div>
            <div v-if="scope.row.expires">{{ scope.row.expires }}</div>
            <span v-if="['FAILED', 'CANCELLED', 'EXPIRED'].includes(scope.row.state) && scope.row.jobId && !scope.row.canRetry"
              >原任务不可恢复，请新建批次</span
            >
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="scope">
            <el-button v-if="isOwner && canExport && scope.row.canRetry" link :disabled="busy" @click="retryItem(scope.row)"
              >重试原任务（{{ scope.row.retryRound }}）</el-button
            >
            <el-button v-if="isOwner && canStart && needsStart(scope.row)" link :disabled="busy" @click="retryItem(scope.row)">补充入队</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </ReportPage>
  <AclDialog v-model:open="aclOpen" resource-type="BATCH" :resource-id="aclId" />
  <ReportPdfPreview
    v-model:open="pdfOpen"
    :source-id="selected"
    :load="loadPdf"
    :filename="`nexus-batch-${selected}.pdf`"
    :can-download="canReadPdf"
  />
</template>
<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import { checkPermi } from '@/utils/permission';
import { listReports, publishedReport, retryExport, listExportJobs } from '@/api/report';
import { listBatches, startBatch, batchItems, batchPdf, type BatchItem, type BatchDocument } from '@/api/report/batch';
import type { ReportDefinition, PublishedReport, ExportJob } from '@/types/report';
import type { ReportResource } from '@/api/report/query';
import { RequestGate, parameterInput, requiredParameterMessage, jobError } from '@/views/report/model';
import { useBatchSubmission } from '@/components/report/useBatchSubmission';
import ReportPage from '@/components/report/ReportPage.vue';
import ReportStatus from '@/components/report/ReportStatus.vue';
import ParameterForm from '@/components/report/ParameterForm.vue';
import ReportPdfPreview from '@/components/report/ReportPdfPreview.vue';
import AclDialog from '@/components/report/AclDialog.vue';

defineOptions({ name: 'ReportBatch' });
/** 每行参数只绑定本行已经核对的固定模板版本。 */
interface DocumentInput {
  /** 本地稳定行标识，不随排序变化。 */
  key: string;
  /** 选中的固定版本。 */
  reportVersionId: string;
  /** 版本核对后的已发布DSL和参数契约。 */
  template?: PublishedReport;
  /** 精确字符串参数。 */
  parameters: Record<string, string>;
  /** 独立模板加载状态。 */
  loading?: boolean;
  /** 模板加载或版本变化说明。 */
  failed?: string;
}
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const reports = ref<ReportDefinition[]>([]);
const batches = ref<ReportResource[]>([]);
const items = ref<(BatchItem & Partial<Pick<ExportJob, 'phase' | 'processedRows' | 'totalRows'>>)[]>([]);
const documents = ref<DocumentInput[]>([{ key: '0', reportVersionId: '', parameters: {} }]);
let nextKey = 1;
let selectionSerial = 0;
const title = ref('业务单据批量打印');
const selected = ref('');
const loading = ref(false);
const loadFailed = ref(false);
const detailLoading = ref(false);
const detailFailed = ref(false);
const writeBusy = ref(false);
const pdfOpen = ref(false);
const aclId = ref('');
const aclOpen = computed({
  get: () => !!aclId.value,
  set: (open: boolean) => {
    if (!open) aclId.value = '';
  }
});
const canList = computed(() => checkPermi(['report:batch:list']));
const canManage = computed(() => checkPermi(['report:batch:manage']));
const canExport = computed(() => checkPermi(['report:runtime:export']));
const canStart = computed(() => canList.value && canManage.value && canExport.value && checkPermi(['report:runtime:preview']));
const canCreate = computed(() => canStart.value && checkPermi(['report:definition:list']));
const isOwner = computed(
  () => !!userStore.userId && String(batches.value.find((batch) => batch.id === selected.value)?.owner ?? '') === String(userStore.userId)
);
const canReadPdf = computed(() => canList.value && canExport.value && isOwner.value && checkPermi(['report:runtime:preview']));
const allSucceeded = computed(() => items.value.length > 0 && items.value.every((item) => item.state === 'SUCCEEDED' && !!item.jobId));
const submission = useBatchSubmission();
const { pending, created, busy: creating, locked } = submission;
const busy = computed(() => creating.value || writeBusy.value);
const gate = new RequestGate();
const detailGate = new RequestGate();
const writeGate = new RequestGate();
const templateGates = new Map<string, RequestGate>();
let active = true;
let timer: ReturnType<typeof setTimeout> | undefined;
const phaseLabels: Record<string, string> = {
  QUEUED: '排队中',
  PREPARING: '准备中',
  SNAPSHOT: '固定快照',
  LAYOUT: '固定排版',
  WRITING: '写入文件',
  COMPLETED: '完成'
};

/** 刷新目录不改动已经选择的版本和参数。 */
async function load() {
  if (!canList.value) return;
  const ticket = gate.begin();
  loading.value = true;
  loadFailed.value = false;
  try {
    const [definitions, resources] = await Promise.all([canCreate.value ? listReports() : Promise.resolve({ data: [] }), listBatches()]);
    if (!gate.current(ticket)) return;
    reports.value = definitions.data.filter((report) => report.kind === 'DOCUMENT' && report.status === 'PUBLISHED' && !!report.publishedVersion);
    batches.value = resources.data;
    if (selected.value && !batches.value.some((batch) => batch.id === selected.value)) {
      selected.value = '';
      items.value = [];
      pdfOpen.value = false;
      detailGate.invalidate();
      pause();
    }
  } catch {
    if (gate.current(ticket)) {
      loadFailed.value = true;
      batches.value = [];
      items.value = [];
      pdfOpen.value = false;
      detailGate.invalidate();
      pause();
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}

/** 固定版本不符时拒绝套用新DSL，逐行请求围栏避免切换覆盖。 */
async function selectTemplate(item: DocumentInput) {
  if (busy.value || locked.value) return;
  const rowGate = templateGates.get(item.key) ?? new RequestGate();
  templateGates.set(item.key, rowGate);
  const ticket = rowGate.begin();
  const versionId = item.reportVersionId;
  item.template = undefined;
  item.parameters = {};
  item.failed = '';
  item.loading = false;
  const report = reports.value.find((entry) => entry.publishedVersion === versionId);
  if (!report) return;
  item.loading = true;
  try {
    const response = await publishedReport(report.id);
    if (!rowGate.current(ticket) || item.reportVersionId !== versionId) return;
    if (response.data.versionId !== versionId || response.data.dsl.kind !== 'DOCUMENT') {
      item.failed = '模板发布版本已变化，请刷新目录并重新选择固定版本';
      return;
    }
    item.template = response.data;
  } catch {
    if (rowGate.current(ticket)) item.failed = '模板读取失败，请重新选择后重试';
  } finally {
    if (rowGate.current(ticket)) item.loading = false;
  }
}

/** 生成不可变提交参数，任何精度不明的值都不能进入批次。 */
function fixedDocuments(): BatchDocument[] {
  if (!documents.value.length || documents.value.length > 50) throw new Error('请选择1至50份单据');
  return documents.value.map((item) => {
    if (!item.template || item.template.versionId !== item.reportVersionId || item.template.dsl.kind !== 'DOCUMENT' || item.loading)
      throw new Error('请先选择并载入每份单据的固定发布版本');
    if (Object.values(item.parameters).some((value) => typeof value !== 'string')) throw new Error('单据参数必须为精确字符串');
    const invalid = requiredParameterMessage(item.template.effectiveParameters ?? item.template.dsl.parameters, item.parameters);
    if (invalid) throw new Error(invalid);
    return { reportVersionId: item.reportVersionId, parameters: parameterInput(item.parameters) };
  });
}

/** 增加独立单据，稳定行标识不会被排序复用。 */
function addDocument() {
  if (!busy.value && !locked.value && documents.value.length < 50)
    documents.value.push({ key: String(nextKey++), reportVersionId: '', parameters: {} });
}
/** 删除本行时同时撤销它的在途模板读取。 */
function remove(item: DocumentInput) {
  if (busy.value || locked.value || documents.value.length <= 1) return;
  templateGates.get(item.key)?.invalidate();
  templateGates.delete(item.key);
  documents.value = documents.value.filter((row) => row.key !== item.key);
}
/** 上移和下移始终同时移动固定参数与版本。 */
function move(index: number, offset: number) {
  if (busy.value || locked.value || index + offset < 0 || index + offset >= documents.value.length) return;
  const [item] = documents.value.splice(index, 1);
  documents.value.splice(index + offset, 0, item);
}
/** 明确新建后才解除上一份提交的锁定。 */
function newBatch() {
  if (!busy.value) submission.reset();
}

/** 首次校验表单后交给固定提交状态机，重试不读取已变化的表单。 */
async function create() {
  if (!canCreate.value || busy.value) return;
  const selectionAtStart = selectionSerial;
  let input: BatchDocument[] = [];
  try {
    if (!locked.value) input = fixedDocuments();
  } catch (error) {
    ElMessage.warning((error as Error).message);
    return;
  }
  const result = await submission.submit(title.value, input);
  if (!result || !active) return;
  await load();
  if (active && selectionSerial === selectionAtStart) await open(result.id);
}

/** 定时刷新只在当前批次仍有运行项时继续，避免并发轮询。 */
function pause() {
  if (timer) clearTimeout(timer);
  timer = undefined;
}
/** 刷新当前授权项，任务进度来自现有导出中心。 */
async function refreshItems(id: string, quiet = false) {
  if (!active || !canList.value || selected.value !== id || (quiet && detailLoading.value)) return;
  pause();
  const ticket = detailGate.begin();
  detailLoading.value = true;
  try {
    const [result, jobs] = await Promise.all([batchItems(id), canExport.value && isOwner.value ? listExportJobs() : Promise.resolve({ data: [] })]);
    if (!detailGate.current(ticket) || selected.value !== id) return;
    items.value = result.data.map((item) => {
      const job = jobs.data.find((entry) => entry.id === item.jobId);
      return { ...item, phase: job?.phase, processedRows: job?.processedRows, totalRows: job?.totalRows };
    });
    detailFailed.value = false;
    if (active && items.value.some((item) => ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(item.state)))
      timer = setTimeout(() => {
        void refreshItems(id, true);
      }, 2500);
  } catch {
    if (detailGate.current(ticket)) {
      items.value = [];
      detailFailed.value = true;
      pdfOpen.value = false;
    }
  } finally {
    if (detailGate.current(ticket)) detailLoading.value = false;
  }
}

/** 切换批次立即清除上一批文件和结果，晚响应不能改变选择。 */
async function open(id: string) {
  if (!canList.value) return;
  selectionSerial++;
  pause();
  detailGate.invalidate();
  pdfOpen.value = false;
  selected.value = id;
  items.value = [];
  detailFailed.value = false;
  await refreshItems(id);
}

/** 尚未拥有任务的失败项只能补入队，已撤权项不能用此路径恢复。 */
function needsStart(item: BatchItem): boolean {
  return !item.jobId && ['PENDING', 'FAILED'].includes(item.state) && !['SCOPE_DENIED', 'SCOPE_CHANGED'].includes(item.error ?? '');
}

/** 补入队复用既有批次，不创建另一套导出任务集合。 */
async function start(id: string) {
  const batch = batches.value.find((entry) => entry.id === id);
  if (busy.value || !canStart.value || !batch || String(batch.owner) !== String(userStore.userId)) return;
  const ticket = writeGate.begin();
  writeBusy.value = true;
  try {
    await startBatch(id);
    if (writeGate.current(ticket) && selected.value === id) await refreshItems(id);
  } catch {
  } finally {
    if (writeGate.current(ticket)) writeBusy.value = false;
  }
}

/** 重试必须使用显示的预期轮次，不能以补入队替代原任务恢复。 */
async function retryItem(item: BatchItem) {
  if (busy.value || !isOwner.value) return;
  if (needsStart(item)) {
    await start(selected.value);
    return;
  }
  if (
    !item.jobId ||
    !item.canRetry ||
    !canExport.value ||
    !Number.isInteger(item.retryRound) ||
    (item.expires && Date.parse(item.expires) <= Date.now())
  ) {
    ElMessage.warning('原任务不可恢复，请新建批次');
    return;
  }
  const ticket = writeGate.begin();
  const id = selected.value;
  const expectedRound = item.retryRound;
  writeBusy.value = true;
  try {
    await retryExport(item.jobId, expectedRound);
    if (writeGate.current(ticket) && selected.value === id) await refreshItems(id);
  } catch {
  } finally {
    if (writeGate.current(ticket)) writeBusy.value = false;
  }
}

/** 合并文件只由owner且全部成功时读取，后端再次检查每项授权与到期。 */
async function loadPdf(): Promise<Blob> {
  if (!canReadPdf.value || !allSucceeded.value) throw new Error('批次尚不可领取');
  return batchPdf(selected.value);
}

/** 停止所有本地后续动作，保留已冻结提交供明确重试。 */
function stop() {
  active = false;
  pause();
  gate.invalidate();
  detailGate.invalidate();
  writeGate.invalidate();
  for (const rowGate of templateGates.values()) rowGate.invalidate();
  submission.suspend();
  loading.value = false;
  detailLoading.value = false;
  writeBusy.value = false;
  pdfOpen.value = false;
}
watch(
  () => route.query.id,
  (id) => {
    if (typeof id === 'string' && active && canList.value) void open(id);
  }
);
watch(
  canReadPdf,
  (allowed) => {
    if (!allowed) pdfOpen.value = false;
  },
  { flush: 'sync' }
);
watch(
  () => [userStore.userId, userStore.tenantId],
  () => {
    const resume = active;
    stop();
    submission.reset();
    batches.value = [];
    reports.value = [];
    items.value = [];
    selected.value = '';
    documents.value = [{ key: String(nextKey++), reportVersionId: '', parameters: {} }];
    if (resume) {
      active = true;
      void load();
    }
  },
  { flush: 'sync' }
);
onMounted(async () => {
  await load();
  if (active && typeof route.query.id === 'string') await open(route.query.id);
});
onActivated(() => {
  if (!active) {
    active = true;
    void load();
    if (selected.value) void refreshItems(selected.value);
  }
});
onDeactivated(stop);
onUnmounted(stop);
</script>
<style scoped>
.batch-document {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--report-line);
  background: var(--report-muted);
  border-radius: 10px;
  margin-bottom: 12px;
}
.batch-document__number {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  flex-shrink: 0;
}
.batch-document__form {
  display: grid;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.batch-document__actions {
  display: flex;
  flex-wrap: wrap;
}
.batch-submit {
  justify-content: flex-end;
  margin-top: 20px;
}
@media (max-width: 768px) {
  .batch-document {
    flex-wrap: wrap;
    padding: 12px;
    gap: 10px;
  }
  .batch-document__actions {
    margin-left: auto;
  }
}
</style>
