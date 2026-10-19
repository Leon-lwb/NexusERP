<template>
  <div class="app-container">
    <el-page-header content="物料追溯审计与修复记录" @back="router.back()" />
    <el-alert title="此页查询记录、生成只读审计报告及下载已审阅清单。修复执行和回退由独立受控流程处理。" type="info" :closable="false" class="my-3" />
    <el-form :model="query" :inline="true">
      <el-form-item label="物料 ID"><el-input v-model="query.materialId" clearable placeholder="原身份字符串（可空）" /></el-form-item>
      <el-form-item label="运行状态"><el-input v-model="query.status" clearable placeholder="可空" /></el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="loading" @click="search">查询记录</el-button
        ><el-button :loading="auditing" v-hasPermi="['wm:trace:audit']" @click="audit">只读审计</el-button></el-form-item
      >
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mb-3" />
    <el-table :data="runs" v-loading="loading" border>
      <el-table-column prop="runId" label="运行身份" min-width="180" />
      <el-table-column prop="status" label="状态" width="130" />
      <el-table-column prop="scope" label="审计范围" min-width="160" />
      <el-table-column label="时间" width="180"
        ><template #default="{ row }">{{ row.createdAt || row.createTime || '-' }}</template></el-table-column
      >
      <el-table-column prop="summary" label="服务端摘要" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="110"
        ><template #default="{ row }"><el-button link type="primary" @click="inspect(row.runId)">查看证据</el-button></template></el-table-column
      >
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <el-drawer v-model="detailVisible" title="审计证据与待审清单" size="75%">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="运行 ID">{{ detail.runId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="清单摘要">{{ detail.manifestDigest || '未提供' }}</el-descriptions-item>
        </el-descriptions>
        <el-button class="my-3" type="primary" :disabled="!downloadable" @click="downloadManifest">下载已审阅清单</el-button>
        <el-alert
          v-if="detail.truncated"
          title="服务端报告已截断，请使用离线联合快照工具完成全量核对。"
          type="warning"
          :closable="false"
          class="mb-3"
        />
        <TraceEvidence :result="detail" />
        <el-table :data="detail.items || []" border>
          <el-table-column prop="classification" label="证据分类" min-width="140" />
          <el-table-column prop="status" label="状态" min-width="100" />
          <el-table-column label="证据与前后记录" min-width="400"
            ><template #default="{ row }">
              <pre class="evidence-json">{{ JSON.stringify(row, null, 2) }}</pre>
            </template></el-table-column
          >
        </el-table>
        <el-collapse class="mt-3"
          ><el-collapse-item title="服务端完整运行记录">
            <pre class="evidence-json">{{ JSON.stringify(detail, null, 2) }}</pre>
          </el-collapse-item></el-collapse
        >
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="MaterialTraceRepair">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { saveAs } from 'file-saver';
import { auditMaterialTrace, getRepairRun, listRepairRuns } from '@/api/erp/wm/trace/repair';
import type { TraceRepairRun } from '@/api/erp/wm/trace/repair';
import { reviewedManifest } from '@/components/material-trace/model';
import TraceEvidence from '@/components/material-trace/TraceEvidence.vue';

const router = useRouter();
const query = ref({ pageNum: 1, pageSize: 10, materialId: '', status: '' });
const runs = ref<TraceRepairRun[]>([]);
const total = ref(0);
const loading = ref(false);
const auditing = ref(false);
const error = ref('');
const detailVisible = ref(false);
const detail = ref<TraceRepairRun>();
let requestId = 0;
const downloadable = computed(() => {
  try {
    return !!detail.value && !!reviewedManifest(detail.value);
  } catch {
    return false;
  }
});

/** 查询只是读取当前服务端运行记录，不推断任何生产迁移或验收状态。 */
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await listRepairRuns({
      ...query.value,
      materialId: query.value.materialId || undefined,
      status: query.value.status || undefined
    });
    if (request !== requestId) return;
    const page = (response.data ?? response) as { rows?: TraceRepairRun[]; total?: number };
    runs.value = page.rows || [];
    total.value = page.total || 0;
  } catch (cause) {
    if (request === requestId) error.value = cause instanceof Error ? cause.message : '审计记录读取失败';
  } finally {
    if (request === requestId) loading.value = false;
  }
}

/** 新检索回到第一页。 */
function search() {
  query.value.pageNum = 1;
  load();
}

/** 请求只读审计，服务端权限及环境检查仍然负责最终授权。 */
async function audit() {
  if (auditing.value) return;
  auditing.value = true;
  error.value = '';
  try {
    const response = await auditMaterialTrace({ materialId: query.value.materialId || undefined });
    await load();
    if (response.data?.runId) await inspect(response.data.runId);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '审计失败';
  } finally {
    auditing.value = false;
  }
}

/** 明确选择运行后加载证据详情，不自动选择首条运行。 */
async function inspect(runId: string) {
  error.value = '';
  detail.value = undefined;
  detailVisible.value = false;
  try {
    detail.value = (await getRepairRun(runId)).data;
    detailVisible.value = true;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '运行详情读取失败';
  }
}

/** 只导出服务端已审阅内容，不生成或执行实时 apply。 */
function downloadManifest() {
  if (!detail.value) return;
  try {
    const manifest = reviewedManifest(detail.value);
    saveAs(
      new Blob([JSON.stringify(manifest, null, 2)], { type: 'application/json;charset=utf-8' }),
      `material-trace-reviewed-${detail.value.runId}.json`
    );
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '清单尚未审阅';
  }
}
onMounted(load);
</script>

<style scoped>
.evidence-json {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 480px;
  overflow: auto;
}
</style>
