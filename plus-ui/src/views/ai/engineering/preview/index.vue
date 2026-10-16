<template>
  <div v-loading="loading" class="app-container engineering-page engineering-preview">
    <nav class="engineering-topbar" aria-label="工程任务导航">
      <el-button :icon="ArrowLeft" link aria-label="返回任务列表" @click="router.push(ENGINEERING_TASK_LIST_PATH)">返回任务列表</el-button>
      <span>工程设计 / 任务详情</span>
    </nav>
    <el-alert v-if="!ENGINEERING_ENABLED" :title="ENGINEERING_DISABLED_NOTICE" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <section v-if="task" class="engineering-panel task-overview" aria-label="任务概览">
      <div class="engineering-heading">
        <div class="engineering-heading__main">
          <div class="engineering-heading__icon">
            <el-icon><component :is="isConnectionCheck ? Connection : Document" /></el-icon>
          </div>
          <div>
            <span class="engineering-eyebrow">{{ isConnectionCheck ? '设备诊断' : '工程任务' }}</span>
            <h1>{{ engineeringActionLabel(task.action) }}结果</h1>
            <p class="engineering-id">任务 {{ task.taskId }}</p>
          </div>
        </div>
        <div class="overview-actions">
          <el-tag :type="taskState?.type || 'info'" effect="light" round>{{ taskState?.label || task.state }}</el-tag>
          <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新状态</el-button>
        </div>
      </div>
      <dl class="engineering-meta-grid">
        <div>
          <dt>当前阶段</dt>
          <dd>
            {{ task.stage ? ENGINEERING_STAGES[task.stage] || task.stage : '等待设备确认'
            }}<span v-if="task.cancelRequested" class="engineering-muted"> · 取消确认中</span>
          </dd>
        </div>
        <div>
          <dt>执行设备</dt>
          <dd :class="{ 'engineering-id': !task.deviceName }">{{ task.deviceName || task.deviceId }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ engineeringTime(task.createdAt || task.createTime) }}</dd>
        </div>
        <div>
          <dt>最近更新</dt>
          <dd>{{ engineeringTime(task.updatedAt) }}</dd>
        </div>
      </dl>
      <details v-if="task.errorCode || task.errorMessage || task.traceId" class="task-record">
        <summary>查看任务记录</summary>
        <p v-if="task.errorCode" class="engineering-id">记录代码：{{ task.errorCode }}</p>
        <p v-if="task.errorMessage">{{ task.errorMessage }}</p>
        <p v-if="task.traceId" class="engineering-id">跟踪号：{{ task.traceId }}</p>
      </details>
    </section>
    <el-empty v-else-if="!loading && !error" description="尚无可读取的工程任务" />

    <ConnectionCheckResult v-if="task && isConnectionCheck" :task="task" />
    <template v-else-if="task">
      <section v-if="outcome" class="task-outcome" :class="`task-outcome--${outcome.type}`" aria-label="任务结果">
        <el-alert :title="outcome.title" :description="outcome.description" :type="outcome.type" :closable="false" show-icon />
        <el-button v-if="outcome.focus" :type="outcome.type === 'warning' ? 'warning' : 'primary'" plain @click="openOutcome">{{
          outcome.focus === 'drc' ? '查看 DRC 报告' : '查看产物文件'
        }}</el-button>
      </section>
      <div v-if="missing.length" class="missing-artifacts">
        <span>待补齐产物</span
        ><el-tag v-for="type in missing" :key="type" type="warning" size="small" effect="plain">{{ ARTIFACT_LABELS[type] || type }}</el-tag>
      </div>
      <section ref="workbench" class="engineering-panel artifact-workspace" aria-label="工程产物">
        <div class="engineering-section-heading workspace-heading">
          <div>
            <h2>工程产物</h2>
            <p>查看线路、物料清单、检查报告与三维模型</p>
          </div>
          <el-tag v-if="manifest" type="info" effect="plain" round>{{ manifest.artifacts.length }} 个已归档文件</el-tag>
        </div>
        <el-alert v-if="manifestError" :title="manifestError" type="error" :closable="false" show-icon class="mb-3" />
        <el-tabs v-model="activeTab" class="artifact-tabs">
          <el-tab-pane label="线路预览" name="line"><PcbLinePreview v-if="activeTab === 'line'" :artifacts="images" /></el-tab-pane>
          <el-tab-pane label="BOM" name="bom"><PcbBomTable v-if="activeTab === 'bom'" :artifact="bom" /></el-tab-pane>
          <el-tab-pane label="DRC 检查" name="drc"
            ><PcbDrcReport v-if="activeTab === 'drc'" :artifact="drc" :summary="manifest?.drc || task.drc"
          /></el-tab-pane>
          <el-tab-pane label="三维模型" name="model">
            <PcbObjViewer v-if="activeTab === 'model' && manifest && pageVisible" :manifest="manifest" />
            <el-empty v-else-if="activeTab === 'model' && !manifest" description="尚无可读取的模型清单" />
          </el-tab-pane>
          <el-tab-pane label="产物文件" name="files">
            <el-table :data="manifest?.artifacts || []" empty-text="尚无已确认产物">
              <el-table-column label="文件" min-width="260"
                ><template #default="{ row }"
                  ><div class="artifact-name">{{ row.fileName || row.relativePath }}</div>
                  <div class="engineering-id engineering-muted">{{ row.relativePath }}</div></template
                ></el-table-column
              >
              <el-table-column label="类型" min-width="130"
                ><template #default="{ row }">{{ ARTIFACT_LABELS[row.type] || row.type }}</template></el-table-column
              >
              <el-table-column label="大小" width="120"
                ><template #default="{ row }">{{ artifactSize(row.size) }}</template></el-table-column
              >
              <el-table-column v-if="canDownload" label="操作" width="100" fixed="right"
                ><template #default="{ row }"
                  ><el-button type="primary" link :loading="downloading === row.artifactId" @click="download(row)">下载</el-button></template
                ></el-table-column
              >
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <div v-if="downloads.length" class="artifact-downloads">
          <span>快捷下载</span>
          <div>
            <el-button
              v-for="artifact in quickDownloads"
              :key="artifact.artifactId"
              :loading="downloading === artifact.artifactId"
              @click="download(artifact)"
              >下载{{ ARTIFACT_LABELS[artifact.type] }}</el-button
            >
          </div>
        </div>
      </section>
      <p class="draft-footnote">工程草稿，未发布生产。请由工程人员结合实际设计与 DRC 报告进行核对。</p>
    </template>
  </div>
</template>

<script setup lang="ts" name="EngineeringPreview">
import { computed, defineAsyncComponent, onActivated, onDeactivated, onBeforeUnmount, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Connection, Document, Refresh } from '@element-plus/icons-vue';
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
  engineeringActionLabel,
  engineeringTaskOutcome,
  engineeringId,
  engineeringTime,
  downloadableArtifacts,
  shouldPoll
} from '../model';
import { useEngineeringPolling } from '../useEngineeringPolling';
import { ENGINEERING_TASK_LIST_PATH } from '../navigation';
import { ENGINEERING_ENABLED, ENGINEERING_DISABLED_NOTICE } from '../engineering-config';
import ConnectionCheckResult from '@/components/ai/engineering/ConnectionCheckResult.vue';
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
const workbench = ref<HTMLElement>();
const isConnectionCheck = computed(() => task.value?.action === 'CHECK_CONNECTION');
const taskState = computed(() => (task.value ? ENGINEERING_STATES[task.value.state] : undefined));
const outcome = computed(() => (task.value ? engineeringTaskOutcome(task.value, manifest.value) : undefined));

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
/** 结果提示只切换实际报告页签，不修改任务状态。 */
async function openOutcome() {
  activeTab.value = outcome.value?.focus || 'files';
  await nextTick();
  workbench.value?.scrollIntoView({ block: 'start' });
}
function artifactSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
/** 详情与清单都绑定当前 URL 中的任务，旧页面响应不能覆盖。 */
async function loadTask(signal: AbortSignal): Promise<boolean> {
  if (!ENGINEERING_ENABLED) return false;
  const taskId = engineeringId(route.params.taskId);
  const token = gate.begin(taskId);
  loading.value = !task.value;
  const taskResult = await getEngineeringTask(taskId, signal);
  if (signal.aborted || !gate.isCurrent(token)) return false;
  if (taskResult.data.taskId !== taskId) throw new Error('任务响应与当前任务不一致，请刷新核对');
  task.value = taskResult.data;
  error.value = '';
  if (task.value.action === 'CHECK_CONNECTION') {
    manifest.value = undefined;
    manifestError.value = '';
    loading.value = false;
    return shouldPoll(task.value.state);
  }
  const [manifestResult] = await Promise.allSettled([getEngineeringManifest(taskId, signal)]);
  if (signal.aborted || !gate.isCurrent(token)) return false;
  loading.value = false;
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

<style scoped lang="scss">
@use '../engineering-pages';
.task-overview {
  padding: 26px;
}
.overview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
}
.task-record {
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
  summary {
    width: fit-content;
    cursor: pointer;
  }
  p {
    margin: 9px 0 0;
    overflow-wrap: anywhere;
  }
}
.task-outcome {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 18px 6px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
  :deep(.el-alert) {
    flex: 1;
  }
}
.task-outcome--warning {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-8);
}
.task-outcome--success {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-8);
}
.task-outcome--error {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-8);
}
.missing-artifacts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.artifact-workspace {
  padding: 24px;
  scroll-margin-top: 100px;
}
.workspace-heading {
  margin-bottom: 18px;
}
.artifact-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}
.artifact-tabs :deep(.el-tabs__item) {
  height: 46px;
  font-weight: 500;
}
.artifact-name {
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.artifact-downloads {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  > span {
    flex-shrink: 0;
    padding-top: 8px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  > div {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .el-button {
    margin: 0;
  }
}
.draft-footnote {
  margin: -3px 2px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.8;
}
@media (max-width: 640px) {
  .task-overview,
  .artifact-workspace {
    padding: 18px;
  }
  .overview-actions {
    width: 100%;
    justify-content: space-between;
    padding: 0;
  }
  .task-outcome {
    flex-wrap: wrap;
    gap: 0;
    padding: 4px;
    .el-button {
      margin: 0 12px 10px;
    }
  }
  .artifact-downloads {
    flex-direction: column;
    gap: 10px;
  }
}
</style>
