<template>
  <ReportPage title="批量单据" description="一次提交多份固定单据，逐项跟踪打印文件的生成结果。" icon="i-ep-printer" class="report-batch">
    <template #actions
      ><el-button :loading="loading" icon="Refresh" @click="load">刷新批次</el-button
      ><el-button @click="router.push('/report/exports')">导出中心</el-button></template
    >
    <el-card shadow="never" class="report-panel"
      ><div class="report-section-heading">
        <div>
          <h2>创建打印批次</h2>
          <p class="report-section-description">每份单据独立校验权限并生成 PDF，可在导出中心领取完成文件。</p>
        </div>
        <el-tag effect="plain">{{ documents.length }} / 50 份</el-tag>
      </div>
      <el-form label-position="top" :disabled="busy"
        ><div class="report-form-row">
          <el-form-item label="批次编码"><el-input v-model="code" placeholder="输入唯一批次编码" /></el-form-item
          ><el-form-item label="批次标题"><el-input v-model="title" placeholder="例如：本日出库单打印" /></el-form-item></div
      ></el-form>
      <div v-for="(item, index) in documents" :key="index" class="batch-document">
        <span class="batch-document__number">{{ index + 1 }}</span
        ><el-form label-position="top" :disabled="busy" class="batch-document__form"
          ><el-form-item label="固定单据版本"
            ><el-select v-model="item.reportVersionId" placeholder="选择已发布打印单据" filterable
              ><el-option
                v-for="report in reports"
                :key="report.id"
                :value="report.publishedVersion!"
                :label="report.name + ' / ' + report.publishedVersion" /></el-select></el-form-item
          ><el-form-item label="单据参数"
            ><el-input
              v-model="item.parametersText"
              type="textarea"
              :rows="2"
              placeholder='参数对象，例如 {"orderId":"完整字符串 ID"}' /></el-form-item></el-form
        ><el-button type="danger" link :disabled="busy || documents.length === 1" @click="documents.splice(index, 1)">移除</el-button>
      </div>
      <div class="report-form-row batch-submit">
        <el-button icon="Plus" :disabled="busy || documents.length >= 50" @click="documents.push({ reportVersionId: '', parametersText: '{}' })"
          >增加单据</el-button
        ><el-button v-hasPermi="['report:batch:manage']" type="primary" :loading="busy" @click="create">创建并入队</el-button>
      </div>
    </el-card>
    <el-card v-loading="loading" shadow="never" class="report-panel"
      ><div class="report-section-heading">
        <div>
          <h2>批次记录</h2>
          <p class="report-section-description">查看每份单据的结果，失败项可补充入队。</p>
        </div>
      </div>
      <el-alert v-if="loadFailed" title="批次目录载入失败，请刷新重试。" type="error" show-icon :closable="false" class="mb-4" /><el-table
        :data="batches"
        empty-text="暂无可见批次"
        ><el-table-column prop="title" label="批次名称" min-width="200" /><el-table-column prop="code" label="编码" min-width="180" /><el-table-column
          label="操作"
          min-width="270"
          ><template #default="scope"
            ><div class="report-row-actions">
              <el-button link type="primary" @click="open(scope.row.id)">查看逐项结果</el-button
              ><el-button v-hasPermi="['report:batch:manage']" link :disabled="busy" @click="start(scope.row.id)">补充入队</el-button
              ><el-button v-hasPermi="['report:acl:manage']" link :disabled="busy" @click="aclId = scope.row.id">资源授权</el-button>
            </div></template
          ></el-table-column
        ></el-table
      ></el-card
    >
    <el-card v-if="selected" shadow="never"
      ><div class="report-section-heading">
        <div>
          <h2>逐项结果</h2>
          <p class="report-section-description">当前批次：{{ selected }}</p>
        </div>
        <el-button @click="open(selected)">刷新结果</el-button>
      </div>
      <el-table :data="items" empty-text="当前批次暂无结果"
        ><el-table-column prop="number" label="序号" width="80" /><el-table-column
          prop="reportVersionId"
          label="固定版本"
          min-width="190" /><el-table-column label="状态" width="120"
          ><template #default="scope"><ReportStatus :status="scope.row.state" /></template></el-table-column
        ><el-table-column prop="error" label="结果码" min-width="180" /><el-table-column prop="jobId" label="导出任务" min-width="190" /></el-table
    ></el-card>
  </ReportPage>
  <AclDialog
    :open="!!aclId"
    resource-type="BATCH"
    :resource-id="aclId"
    @update:open="
      (value) => {
        if (!value) aclId = '';
      }
    "
  />
</template>
<script setup lang="ts">
/** 区分资源目录载入失败与当前账号暂无可见资源。 */
const loadFailed = ref(false);
import ReportPage from '@/components/report/ReportPage.vue';
import ReportStatus from '@/components/report/ReportStatus.vue';
import AclDialog from '@/components/report/AclDialog.vue';
/** 当前弹窗绑定的资源ID，关闭或切换时由弹窗请求围栏保护。 */
const aclId = ref('');
/** 批次设计仅接受明确版本与精确字符串参数。 */
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listReports } from '@/api/report';
import { listBatches, createBatch, startBatch, batchItems, type BatchItem } from '@/api/report/batch';
import type { ReportDefinition } from '@/types/report';
import type { ReportResource } from '@/api/report/query';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的router、reports、batches、items、documents、code、title、selected、loading、busy状态。
 */
const router = useRouter(),
  reports = ref<ReportDefinition[]>([]),
  batches = ref<ReportResource[]>([]),
  items = ref<BatchItem[]>([]),
  documents = ref([{ reportVersionId: '', parametersText: '{}' }]),
  code = ref(''),
  title = ref(''),
  selected = ref(''),
  loading = ref(false),
  busy = ref(false);
/**
 * 保存当前页面或测试的gate、detailGate、writeGate状态。
 */
const gate = new RequestGate(),
  detailGate = new RequestGate(),
  writeGate = new RequestGate();
/** 刷新单据定义及可访问批次。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  loadFailed.value = false;
  try {
    const [a, b] = await Promise.all([listReports(), listBatches()]);
    if (gate.current(ticket)) {
      reports.value = a.data.filter((report) => report.kind === 'DOCUMENT' && report.status === 'PUBLISHED');
      batches.value = b.data;
    }
  } catch {
    if (gate.current(ticket)) {
      loadFailed.value = true;
      batches.value = [];
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 参数对象中的ID和DECIMAL要求字符串，防止粘贴JSON数字精度丢失。 */
function fixedDocuments() {
  return documents.value.map((item) => {
    const value = JSON.parse(item.parametersText);
    if (
      !item.reportVersionId ||
      !value ||
      Array.isArray(value) ||
      typeof value !== 'object' ||
      Object.values(value).some((entry) => typeof entry !== 'string')
    )
      throw new Error('请选择固定版本，参数对象的值均应为字符串');
    return { reportVersionId: item.reportVersionId, parameters: value as Record<string, string> };
  });
}
/** 显式创建后把各单据独立送入持久导出中心。 */
async function create() {
  if (busy.value) return;
  let input;
  try {
    input = fixedDocuments();
  } catch (error) {
    ElMessage.warning((error as Error).message);
    return;
  }
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    const response = await createBatch(code.value, title.value, input);
    if (!writeGate.current(ticket)) return;
    await startBatch(response.data.id);
    if (writeGate.current(ticket)) {
      await load();
      await open(response.data.id);
      ElMessage.success('固定批次已逐项入队');
    }
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
/** 逐项结果受当前授权保护，迟到回包不切换批次。 */
async function open(id: string) {
  const ticket = detailGate.begin();
  selected.value = id;
  items.value = [];
  try {
    const response = await batchItems(id);
    if (detailGate.current(ticket)) items.value = response.data;
  } catch {}
}
/** 对尚未成功入队的项补充处理，成功项保持幂等。 */
async function start(id: string) {
  if (busy.value) return;
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    await startBatch(id);
    if (writeGate.current(ticket)) await open(id);
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  detailGate.invalidate();
  writeGate.invalidate();
});
</script>
<style scoped>
.report-form-row > .el-form-item {
  flex: 1 1 220px;
  min-width: 0;
}
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
  margin-top: 26px;
  border-radius: 50%;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-size: 12px;
  flex-shrink: 0;
}
.batch-document__form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr);
  gap: 16px;
  flex: 1;
  min-width: 0;
}
.batch-document__form .el-form-item {
  margin-bottom: 0;
}
.batch-document > .el-button {
  margin-top: 30px;
}
.batch-submit {
  justify-content: flex-end;
  margin-top: 20px;
}
@media (max-width: 768px) {
  .batch-document {
    gap: 10px;
    padding: 12px;
    flex-wrap: wrap;
  }
  .batch-document__form {
    grid-template-columns: minmax(0, 1fr);
  }
  .batch-document > .el-button {
    margin: 0 0 0 auto;
  }
}
</style>
