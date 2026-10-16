<template>
  <div v-loading="loading" class="app-container">
    <el-alert v-if="!ENGINEERING_ENABLED" :title="ENGINEERING_DISABLED_NOTICE" type="info" :closable="false" class="mb-3" />
    <el-page-header content="PCB 工程任务详情" @back="router.push('/ai/engineering/task')" class="mb-4" />
    <el-alert
      title="工程草稿，未发布生产。任务完成仅说明草稿及产物归档结果，请由工程人员核对设计与 DRC。"
      type="warning"
      :closable="false"
      class="mb-4"
    />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-card v-if="task" shadow="never" class="mb-4">
      <template #header
        ><div class="flex flex-wrap items-center justify-between gap-3">
          <span>任务 {{ task.taskId }}</span
          ><el-button icon="Refresh" @click="refresh">刷新</el-button>
        </div></template
      >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务状态"
          ><el-tag :type="ENGINEERING_STATES[task.state]?.type || 'info'">{{ ENGINEERING_STATES[task.state]?.label || task.state }}</el-tag
          ><span v-if="task.cancelRequested"> 取消确认中</span></el-descriptions-item
        >
        <el-descriptions-item label="当前阶段">{{ task.stage ? ENGINEERING_STAGES[task.stage] : '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ task.deviceName || task.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ engineeringTime(task.createdAt || task.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="task.errorMessage || task.errorCode"
        :title="[task.errorCode, task.errorMessage, task.traceId && `跟踪号：${task.traceId}`].filter(Boolean).join('；')"
        type="error"
        :closable="false"
        class="mt-3"
      />
      <p v-if="missing.length" class="text-orange-600">缺失产物：{{ missing.map((type) => ARTIFACT_LABELS[type] || type).join('、') }}</p>
    </el-card>
    <el-empty v-else-if="!loading && !error" description="尚无可读取的工程任务" />
    <el-card v-if="task" shadow="never">
      <el-alert v-if="manifestError" :title="manifestError" type="error" :closable="false" class="mb-3" />
      <el-tabs v-model="activeTab">
        <el-tab-pane label="线路预览" name="line"><PcbLinePreview v-if="activeTab === 'line'" :artifacts="images" /></el-tab-pane>
        <el-tab-pane label="BOM" name="bom"><PcbBomTable v-if="activeTab === 'bom'" :artifact="bom" /></el-tab-pane>
        <el-tab-pane label="DRC 检查" name="drc"
          ><PcbDrcReport v-if="activeTab === 'drc'" :artifact="drc" :summary="manifest?.drc || task.drc"
        /></el-tab-pane>
        <el-tab-pane label="三维模型" name="model"
          ><PcbObjViewer v-if="activeTab === 'model' && manifest && pageVisible" :manifest="manifest" /><el-empty
            v-else-if="activeTab === 'model' && !manifest"
            description="尚无可读取的模型清单"
        /></el-tab-pane>
        <el-tab-pane label="产物文件" name="files">
          <el-table :data="manifest?.artifacts || []" border empty-text="尚无已确认产物">
            <el-table-column label="类型" min-width="140"
              ><template #default="{ row }">{{ ARTIFACT_LABELS[row.type] || row.type }}</template></el-table-column
            >
            <el-table-column label="文件名" min-width="240"
              ><template #default="{ row }">{{ row.fileName || row.relativePath }}</template></el-table-column
            >
            <el-table-column prop="size" label="字节数" width="130" />
            <el-table-column v-if="canDownload" label="操作" width="110"
              ><template #default="{ row }"
                ><el-button type="primary" link :loading="downloading === row.artifactId" @click="download(row)">下载</el-button></template
              ></el-table-column
            >
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <div v-if="downloads.length" class="mt-4 flex flex-wrap gap-2">
        <el-button
          v-for="artifact in quickDownloads"
          :key="artifact.artifactId"
          :loading="downloading === artifact.artifactId"
          @click="download(artifact)"
          >下载{{ ARTIFACT_LABELS[artifact.type] }}</el-button
        >
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="EngineeringPreview">
import { computed, defineAsyncComponent, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { checkPermi } from '@/utils/permission';
import {
  getEngineeringTask,
  getEngineeringManifest,
  getEngineeringArtifactContent,
  type EngineeringTask,
  type EngineeringManifest,
  type EngineeringArtifact
} from '@/api/ai/engineering';
import { artifactsOfType, readArtifact, saveArtifactBlob } from '../artifact-model';
import {
  ARTIFACT_LABELS,
  ENGINEERING_STATES,
  ENGINEERING_STAGES,
  EngineeringRequestGate,
  engineeringError,
  engineeringId,
  engineeringTime,
  downloadableArtifacts,
  shouldPoll
} from '../model';
import { useEngineeringPolling } from '../useEngineeringPolling';
import { ENGINEERING_ENABLED, ENGINEERING_DISABLED_NOTICE } from '../engineering-config';
import PcbLinePreview from '@/components/ai/engineering/PcbLinePreview.vue';
import PcbBomTable from '@/components/ai/engineering/PcbBomTable.vue';
import PcbDrcReport from '@/components/ai/engineering/PcbDrcReport.vue';
const PcbObjViewer = defineAsyncComponent(() => import('@/components/ai/engineering/PcbObjViewer.vue'));
const route = useRoute();
const router = useRouter();
const gate = new EngineeringRequestGate();
const task = ref<EngineeringTask>();
const manifest = ref<EngineeringManifest>();
const loading = ref(false);
const error = ref('');
const manifestError = ref('');
const activeTab = ref('line');
const downloading = ref('');
const pageVisible = ref(true);
let downloadController: AbortController | undefined;
const canDownload = computed(() => checkPermi(['ai:engineering:download']));
const images = computed(() => artifactsOfType(manifest.value, 'PCB_PREVIEW_2D'));
const bom = computed(() => artifactsOfType(manifest.value, 'PCB_BOM')[0]);
const drc = computed(() => artifactsOfType(manifest.value, 'PCB_DRC_REPORT')[0]);
const downloads = computed(() => downloadableArtifacts(manifest.value?.artifacts, canDownload.value));
const quickDownloads = computed(() =>
  downloads.value.filter((item) => ['PCB_SOURCE', 'PCB_NETLIST', 'PCB_MODEL_OBJ', 'PCB_BOM', 'PCB_DRC_REPORT'].includes(item.type))
);
const missing = computed(() => manifest.value?.missingTypes || task.value?.missingTypes || []);
/** 详情与清单都绑定当前 URL 中的任务，旧页面响应不能覆盖。 */
async function loadTask(signal: AbortSignal): Promise<boolean> {
  if (!ENGINEERING_ENABLED) return false;
  const taskId = engineeringId(route.params.taskId);
  const token = gate.begin(taskId);
  loading.value = !task.value;
  const [taskResult, manifestResult] = await Promise.allSettled([getEngineeringTask(taskId, signal), getEngineeringManifest(taskId, signal)]);
  if (signal.aborted || !gate.isCurrent(token)) return false;
  loading.value = false;
  if (taskResult.status === 'rejected') throw taskResult.reason;
  if (taskResult.value.data.taskId !== taskId) throw new Error('任务响应与当前任务不一致，请刷新核对');
  task.value = taskResult.value.data;
  error.value = '';
  if (manifestResult.status === 'fulfilled') {
    if (manifestResult.value.data.taskId !== taskId) throw new Error('产物清单与当前任务不一致');
    manifest.value = manifestResult.value.data;
    manifestError.value = '';
  } else {
    manifest.value = undefined;
    manifestError.value = engineeringError(manifestResult.reason).message;
    if ([401, 403, 404].includes(engineeringError(manifestResult.reason).status || 0)) throw manifestResult.reason;
  }
  return shouldPoll(task.value.state);
}
const { refresh, stop } = useEngineeringPolling(loadTask, (cause) => {
  loading.value = false;
  const parsed = engineeringError(cause);
  error.value = parsed.message + (parsed.traceId ? `；跟踪号：${parsed.traceId}` : '');
});
watch(
  () => route.params.taskId,
  () => {
    gate.invalidate();
    downloadController?.abort();
    downloading.value = '';
    task.value = undefined;
    manifest.value = undefined;
    error.value = '';
    manifestError.value = '';
    activeTab.value = 'line';
    void refresh();
  }
);
/** 下载权限在执行时再次检查，只保存授权接口的 Blob。 */
async function download(artifact: EngineeringArtifact) {
  if (!canDownload.value || downloading.value) return;
  const controller = new AbortController();
  downloadController = controller;
  downloading.value = artifact.artifactId;
  try {
    const blob = await readArtifact(artifact, getEngineeringArtifactContent, controller.signal);
    controller.signal.throwIfAborted();
    await saveArtifactBlob(blob, artifact);
  } catch (cause) {
    if (!controller.signal.aborted) error.value = engineeringError(cause).message;
  } finally {
    if (downloadController === controller) downloading.value = '';
  }
}
onActivated(() => {
  pageVisible.value = true;
});
onDeactivated(() => {
  pageVisible.value = false;
  gate.invalidate();
  downloadController?.abort();
});
onBeforeUnmount(() => {
  gate.invalidate();
  stop();
  downloadController?.abort();
});
</script>
