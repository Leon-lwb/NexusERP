<template>
  <div class="p-4">
    <el-card shadow="never"
      ><template #header
        ><div class="flex items-center justify-between">
          <div>
            <strong class="text-xl">导出中心</strong>
            <p class="text-gray-500 mb-0">下载个人导出任务。文件到期或业务权限变化后，需要重新运行报表。</p>
          </div>
          <el-button icon="Refresh" @click="load()">刷新</el-button>
        </div></template
      >
      <el-alert v-if="failed" title="导出任务载入失败，请刷新重试。" type="error" :closable="false" class="mb-4" />
      <el-table v-loading="loading" :data="jobs" border
        ><el-table-column prop="id" label="任务编号" min-width="190" /><el-table-column label="格式" width="125"
          ><template #default="scope">{{ formats[scope.row.format] }}</template></el-table-column
        ><el-table-column label="状态" width="135"
          ><template #default="scope"
            ><el-tag :type="scope.row.state === 'SUCCEEDED' ? 'success' : scope.row.state === 'FAILED' ? 'danger' : 'info'">{{
              states[scope.row.state]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="文件大小" width="115"
          ><template #default="scope">{{ scope.row.bytes == null ? '—' : (scope.row.bytes / 1024).toFixed(1) + ' KB' }}</template></el-table-column
        ><el-table-column prop="expires" label="到期时间" min-width="210" /><el-table-column label="结果说明" min-width="220"
          ><template #default="scope">{{ jobError(scope.row.error) }}</template></el-table-column
        ><el-table-column label="操作" width="140"
          ><template #default="scope"
            ><el-button
              v-if="scope.row.state === 'SUCCEEDED'"
              v-hasPermi="['report:runtime:export']"
              :disabled="!!downloading"
              link
              type="primary"
              :loading="downloading === scope.row.id"
              @click="download(scope.row)"
              >下载</el-button
            ><el-button
              v-if="['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(scope.row.state)"
              v-hasPermi="['report:runtime:export']"
              link
              type="danger"
              :disabled="!!cancelling || scope.row.state === 'CANCEL_REQUESTED'"
              @click="cancel(scope.row)"
              >取消</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </el-card>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue';
import { ElMessage } from 'element-plus';
import { saveAs } from 'file-saver';
import { listExportJobs, cancelExport, downloadExport } from '@/api/report';
import type { ExportJob } from '@/types/report';
import { jobError, RequestGate } from '@/views/report/model';
defineOptions({ name: 'ReportExports' });
const jobs = ref<ExportJob[]>([]);
const loading = ref(false);
const downloading = ref('');
const failed = ref(false);
const cancelling = ref('');
let active = false;
let requests = 0;
let timer: ReturnType<typeof setInterval> | undefined;
const gate = new RequestGate();
const downloadGate = new RequestGate();
const formats: Record<string, string> = { PDF: 'PDF', XLSX_DATA: '明细 XLSX', XLSX_LAYOUT: '样式 XLSX' };
const states: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  CANCEL_REQUESTED: '正在取消',
  CANCELLED: '已取消',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  EXPIRED: '已到期'
};
async function load(quiet = false) {
  if (quiet && requests > 0) return;
  const ticket = gate.begin();
  requests++;
  if (!quiet) loading.value = true;
  try {
    const result = await listExportJobs();
    if (gate.current(ticket)) {
      jobs.value = result.data;
      failed.value = false;
      if (active) schedule();
    }
  } catch {
    if (gate.current(ticket)) {
      failed.value = true;
      jobs.value = [];
      pause();
    }
  } finally {
    requests--;
    if (gate.current(ticket)) loading.value = false;
  }
}
function schedule() {
  if (timer) return;
  timer = setInterval(() => {
    if (jobs.value.some((j) => ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(j.state))) void load(true);
  }, 3000);
}
function start() {
  if (active) return;
  active = true;
  void load();
  schedule();
}
function pause() {
  if (timer) clearInterval(timer);
  timer = undefined;
}
function stop() {
  active = false;
  pause();
  gate.invalidate();
  downloadGate.invalidate();
  loading.value = false;
  downloading.value = '';
}
async function cancel(job: ExportJob) {
  if (cancelling.value) return;
  cancelling.value = job.id;
  try {
    await cancelExport(job.id);
    if (active) await load();
  } catch {
  } finally {
    cancelling.value = '';
  }
}
async function download(job: ExportJob) {
  if (downloading.value) return;
  const ticket = downloadGate.begin();
  downloading.value = job.id;
  try {
    const blob = await downloadExport(job.id);
    if (!downloadGate.current(ticket)) return;
    if (blob.type.includes('json')) {
      ElMessage.error('文件不可用，请重新运行报表');
      return;
    }
    saveAs(blob, 'nexus-report-' + job.id + (job.format === 'PDF' ? '.pdf' : '.xlsx'));
  } catch {
  } finally {
    if (downloadGate.current(ticket)) downloading.value = '';
  }
}
onMounted(start);
onActivated(start);
onUnmounted(stop);
onDeactivated(stop);
</script>
