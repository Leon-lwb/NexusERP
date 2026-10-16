<template>
  <div class="app-container engineering-page engineering-task-list">
    <header class="engineering-heading">
      <div class="engineering-heading__main">
        <div class="engineering-heading__icon">
          <el-icon><FolderOpened /></el-icon>
        </div>
        <div>
          <span class="engineering-eyebrow">工程设计</span>
          <h1>工程任务</h1>
          <p>跟踪设备执行进度，查看连接诊断、PCB 草稿与设计产物。</p>
        </div>
      </div>
      <el-button v-if="ENGINEERING_ENABLED" :icon="Refresh" :loading="loading" @click="refresh">刷新列表</el-button>
    </header>
    <el-alert v-if="!ENGINEERING_ENABLED" :title="ENGINEERING_DISABLED_NOTICE" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section v-if="ENGINEERING_ENABLED" class="engineering-panel task-filter-panel" aria-label="筛选任务">
      <el-form :model="query" label-position="top" @submit.prevent="search">
        <div class="task-filter-grid">
          <el-form-item label="任务状态"
            ><el-select v-model="query.state" clearable placeholder="全部状态"
              ><el-option v-for="(item, key) in ENGINEERING_STATES" :key="key" :label="item.label" :value="key" /></el-select
          ></el-form-item>
          <el-form-item label="会话 ID"
            ><el-input v-model="sessionFilter" clearable placeholder="输入会话 ID 筛选" @keyup.enter="search"
          /></el-form-item>
          <div class="filter-actions">
            <el-button type="primary" :icon="Search" @click="search">搜索任务</el-button><el-button @click="reset">重置</el-button>
          </div>
        </div>
      </el-form>
    </section>
    <section v-if="ENGINEERING_ENABLED" class="engineering-panel task-table-panel" aria-label="工程任务列表">
      <div class="engineering-section-heading task-list-heading">
        <div>
          <h2>
            任务列表 <span class="task-total">{{ total }}</span>
          </h2>
          <p>连接检查与草稿生成分别保存，点击任务查看实际结果。</p>
        </div>
        <span class="list-page-note">当前页 {{ tasks.length }} 条</span>
      </div>
      <el-table v-loading="loading" :data="tasks" empty-text="暂无工程任务，可在 AI 对话中选择设备后发起">
        <el-table-column label="任务" min-width="280"
          ><template #default="{ row }"
            ><div class="task-identity">
              <span class="task-type-icon" :class="{ 'task-type-icon--connection': row.action === 'CHECK_CONNECTION' }"
                ><el-icon><component :is="row.action === 'CHECK_CONNECTION' ? Connection : Document" /></el-icon
              ></span>
              <div>
                <div class="task-action-name">{{ engineeringActionLabel(row.action) }}</div>
                <div class="engineering-id engineering-muted">{{ row.taskId }}</div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="状态 / 阶段" min-width="170"
          ><template #default="{ row }"
            ><el-tag :type="ENGINEERING_STATES[row.state]?.type || 'info'" effect="light" round>{{
              ENGINEERING_STATES[row.state]?.label || row.state
            }}</el-tag>
            <div class="task-stage">{{ row.cancelRequested ? '取消确认中' : ENGINEERING_STAGES[row.stage] || '等待设备确认' }}</div></template
          ></el-table-column
        >
        <el-table-column label="执行设备" min-width="170"
          ><template #default="{ row }"
            ><span :class="{ 'engineering-id': !row.deviceName }">{{ row.deviceName || row.deviceId }}</span></template
          ></el-table-column
        >
        <el-table-column label="创建时间" min-width="175"
          ><template #default="{ row }"
            ><span class="task-time">{{ engineeringTime(row.createdAt || row.createTime) }}</span></template
          ></el-table-column
        >
        <el-table-column label="产物情况" min-width="160"
          ><template #default="{ row }"
            ><span v-if="row.action === 'CHECK_CONNECTION'" class="engineering-muted">连接诊断</span
            ><span
              v-else-if="row.missingTypes?.length"
              class="missing-types"
              :title="row.missingTypes.map((type) => ARTIFACT_LABELS[type] || type).join('、')"
              >待补齐 {{ row.missingTypes.length }} 类产物</span
            ><span v-else class="engineering-muted">查看产物清单</span></template
          ></el-table-column
        >
        <el-table-column label="操作" min-width="230" fixed="right"
          ><template #default="{ row }"
            ><el-button v-hasPermi="['ai:engineering:query']" link type="primary" @click="router.push(`/ai/engineering/task/${row.taskId}`)">{{
              row.action === 'CHECK_CONNECTION' ? '查看检查结果' : '查看预览'
            }}</el-button
            ><el-button
              v-if="shouldPoll(row.state) && !row.cancelRequested"
              v-hasPermi="['ai:engineering:task']"
              link
              type="danger"
              @click="cancel(row)"
              >取消任务</el-button
            ><el-button v-if="!shouldPoll(row.state)" v-hasPermi="['ai:engineering:task']" link @click="retry(row)">新任务重试</el-button></template
          ></el-table-column
        >
      </el-table>
      <div class="task-pagination">
        <pagination v-show="total > 0" v-model:total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="refresh" />
      </div>
    </section>
    <el-dialog v-model="retryVisible" title="确认新的 PCB 草稿任务" width="640px" :close-on-click-modal="false">
      <el-alert title="原任务不会被重放。请核对实际结果后，为新草稿明确选择已审核模板及设计参数。" type="warning" :closable="false" class="mb-4" />
      <el-alert v-if="retryError" :title="retryError" type="error" :closable="false" class="mb-3" />
      <el-empty v-if="!retryLoading && !retryTemplates.length" description="当前没有已审核模板，暂不支持生成新草稿" />
      <el-form v-else :model="retryForm" label-width="110px" v-loading="retryLoading">
        <el-form-item label="已审核模板"
          ><el-select v-model="retryForm.templateKey" placeholder="请选择模板与版本" class="w-full"
            ><el-option
              v-for="template in retryTemplates"
              :key="templateKey(template)"
              :value="templateKey(template)"
              :label="`${template.title || template.templateName || template.templateId} · ${template.templateVersion}`" /></el-select
        ></el-form-item>
        <el-form-item label="板宽（mm）"><el-input v-model="retryForm.boardWidthMm" placeholder="十进制文本，例如 50.0" /></el-form-item>
        <el-form-item label="板高（mm）"><el-input v-model="retryForm.boardHeightMm" placeholder="十进制文本，例如 35.0" /></el-form-item>
        <el-form-item label="板层"
          ><el-select v-model="retryForm.layers" placeholder="明确选择层数"
            ><el-option v-for="layers in retryLayers" :key="layers" :value="layers" :label="`${layers} 层`" /></el-select
        ></el-form-item>
        <el-form-item v-if="selectedTemplate?.optionalModules?.length" label="可选模块"
          ><el-checkbox-group v-model="retryForm.optionalModules"
            ><el-checkbox v-for="module in selectedTemplate.optionalModules" :key="module" :label="module">{{
              module
            }}</el-checkbox></el-checkbox-group
          ></el-form-item
        >
      </el-form>
      <template #footer
        ><el-button :disabled="retryLoading" @click="retryVisible = false">返回核对</el-button
        ><el-button type="primary" :disabled="!selectedTemplate" :loading="retryLoading" @click="submitRetry">确认创建新任务</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EngineeringTask">
import { computed, getCurrentInstance, onBeforeUnmount, reactive, ref, watch, type ComponentInternalInstance } from 'vue';
import { useRouter } from 'vue-router';
import { Connection, Document, FolderOpened, Refresh, Search } from '@element-plus/icons-vue';
import {
  listEngineeringTasks,
  listEngineeringTemplates,
  getEngineeringManifest,
  type EngineeringTemplate,
  cancelEngineeringTask,
  createEngineeringTask,
  type EngineeringTask,
  type EngineeringTaskQuery
} from '@/api/ai/engineering';
import {
  ARTIFACT_LABELS,
  ENGINEERING_STATES,
  ENGINEERING_STAGES,
  engineeringActionLabel,
  engineeringError,
  engineeringId,
  engineeringTime,
  shouldPoll
} from '../model';
import { useEngineeringPolling } from '../useEngineeringPolling';
import { ENGINEERING_ENABLED, ENGINEERING_DISABLED_NOTICE } from '../engineering-config';
import { templateDraftDefaults, templateLayerOptions, validateDraftParameters } from '../template-parameters';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();
const tasks = ref<EngineeringTask[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const sessionFilter = ref('');
const query = reactive<EngineeringTaskQuery>({ pageNum: 1, pageSize: 10 });
const acting = new Set<string>();
const retryVisible = ref(false);
const retryLoading = ref(false);
const retryError = ref('');
const retryTemplates = ref<EngineeringTemplate[]>([]);
const retrySource = ref<EngineeringTask>();
let retryKey = '';
let retryController: AbortController | undefined;
const retryForm = reactive({
  templateKey: '',
  boardWidthMm: '',
  boardHeightMm: '',
  layers: undefined as number | undefined,
  optionalModules: [] as string[]
});
const templateKey = (template: EngineeringTemplate) => `${template.templateId}:${template.templateVersion}:${template.templateSha256}`;
const selectedTemplate = computed(() => retryTemplates.value.find((template) => templateKey(template) === retryForm.templateKey));
const retryLayers = computed(() => templateLayerOptions(selectedTemplate.value));
watch(selectedTemplate, (template) => Object.assign(retryForm, templateDraftDefaults(template)), { flush: 'sync' });
/** 读取当前过滤条件的实际分页状态，隐藏页面不继续请求。 */
async function loadTasks(signal: AbortSignal): Promise<boolean> {
  if (!ENGINEERING_ENABLED) return false;
  loading.value = tasks.value.length === 0;
  try {
    const result = await listEngineeringTasks({ ...query }, signal);
    if (!signal.aborted) {
      const currentRows = await Promise.all(
        (result.rows || []).map(async (task) => {
          if (Array.isArray(task.missingTypes)) return task;
          try {
            const manifest = await getEngineeringManifest(task.taskId, signal);
            return { ...task, missingTypes: manifest.data.missingTypes };
          } catch (cause) {
            if ([401, 403].includes(engineeringError(cause).status || 0)) throw cause;
            return task;
          }
        })
      );
      if (signal.aborted) return false;
      tasks.value = currentRows;
      total.value = result.total || 0;
      error.value = '';
    }
  } finally {
    if (!signal.aborted) loading.value = false;
  }
  return tasks.value.some((task) => shouldPoll(task.state));
}
const { refresh } = useEngineeringPolling(loadTasks, (cause) => {
  loading.value = false;
  error.value = engineeringError(cause).message;
});
/** 会话筛选保持原始字符串，非法输入不发送请求。 */
function search() {
  try {
    query.sessionId = sessionFilter.value ? engineeringId(sessionFilter.value) : undefined;
    query.pageNum = 1;
    void refresh();
  } catch (cause) {
    error.value = engineeringError(cause).message;
  }
}
function reset() {
  query.state = undefined;
  query.sessionId = undefined;
  sessionFilter.value = '';
  query.pageNum = 1;
  void refresh();
}
/** 取消请求成功仅表示已接收，最终状态仍从任务接口读取。 */
async function cancel(task: EngineeringTask) {
  if (acting.has(task.taskId)) return;
  try {
    await proxy?.$modal.confirm('确认请求取消此工程任务？运行中的设备会在步骤间确认停止。');
  } catch {
    return;
  }
  acting.add(task.taskId);
  try {
    await cancelEngineeringTask(task.taskId);
    await refresh();
  } catch (cause) {
    error.value = engineeringError(cause).message;
  } finally {
    acting.delete(task.taskId);
  }
}
/** 新生成任务必须再次确认模板与十进制参数，不假设安全视图包含原请求。 */
async function retry(task: EngineeringTask) {
  if (acting.has(task.taskId) || retryLoading.value) return;
  if (task.action === 'CREATE_PCB_DRAFT') {
    retrySource.value = task;
    retryKey = crypto.randomUUID();
    retryVisible.value = true;
    retryError.value = '';
    retryLoading.value = true;
    Object.assign(retryForm, { templateKey: '', ...templateDraftDefaults() });
    try {
      retryController?.abort();
      const controller = new AbortController();
      retryController = controller;
      const result = await listEngineeringTemplates(controller.signal);
      controller.signal.throwIfAborted();
      retryTemplates.value = result.data || [];
    } catch (cause) {
      retryError.value = engineeringError(cause).message;
    } finally {
      retryLoading.value = false;
    }
    return;
  }
  try {
    await proxy?.$modal.confirm('将创建一个新动作任务，请先核对原任务的实际结果，确认继续？');
  } catch {
    return;
  }
  acting.add(task.taskId);
  try {
    const result = await createEngineeringTask({
      deviceId: task.deviceId,
      action: task.action,
      idempotencyKey: crypto.randomUUID(),
      parentTaskId: task.taskId,
      sessionId: task.sessionId
    });
    await router.push(`/ai/engineering/task/${result.data.taskId}`);
  } catch (cause) {
    error.value = engineeringError(cause).message;
  } finally {
    acting.delete(task.taskId);
  }
}
/** 同一次确认失败后复用幂等键，只有新的重试意图才创建新键。 */
async function submitRetry() {
  const template = selectedTemplate.value;
  const task = retrySource.value;
  if (!task || retryLoading.value) return;
  if (!template) {
    retryError.value = '请选择已审核模板，并明确填写板宽、板高与层数；尺寸使用十进制字符串';
    return;
  }
  let parameters;
  try {
    parameters = validateDraftParameters(template, retryForm);
  } catch (cause) {
    retryError.value = engineeringError(cause).message;
    return;
  }
  retryLoading.value = true;
  retryError.value = '';
  try {
    const result = await createEngineeringTask({
      deviceId: task.deviceId,
      action: 'CREATE_PCB_DRAFT',
      idempotencyKey: retryKey,
      parentTaskId: task.taskId,
      sessionId: task.sessionId,
      templateId: template.templateId,
      templateVersion: template.templateVersion,
      templateSha256: template.templateSha256,
      parameters
    });
    retryVisible.value = false;
    await router.push(`/ai/engineering/task/${result.data.taskId}`);
  } catch (cause) {
    retryError.value = engineeringError(cause).message;
  } finally {
    retryLoading.value = false;
  }
}
onBeforeUnmount(() => retryController?.abort());
</script>

<style scoped lang="scss">
@use '../engineering-pages';
.engineering-task-list > .engineering-heading {
  align-items: center;
}
.task-filter-panel {
  padding: 20px 24px;
}
.task-filter-grid {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.3fr) auto;
  align-items: end;
  gap: 20px;
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
  :deep(.el-form-item__label) {
    margin-bottom: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .el-select {
    width: 100%;
  }
}
.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  .el-button {
    margin: 0;
  }
}
.task-table-panel {
  padding: 22px 24px 8px;
}
.task-list-heading {
  margin-bottom: 22px;
}
.task-total {
  display: inline-block;
  min-width: 24px;
  margin-left: 7px;
  padding: 2px 8px;
  border-radius: 7px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
  vertical-align: middle;
}
.list-page-note,
.task-stage {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.task-stage {
  margin-top: 7px;
}
.task-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}
.task-type-icon {
  display: inline-flex;
  width: 35px;
  height: 35px;
  flex: 0 0 35px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 18px;
}
.task-type-icon--connection {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.task-action-name {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 500;
}
.task-time {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.missing-types {
  color: var(--el-color-warning);
  font-size: 12px;
}
.task-pagination {
  min-height: 48px;
  padding-top: 8px;
  :deep(.pagination-container) {
    padding-right: 0;
    padding-left: 0;
  }
}
@media (max-width: 900px) {
  .task-filter-grid {
    grid-template-columns: 1fr 1fr;
  }
  .filter-actions {
    grid-column: 1 / -1;
  }
}
@media (max-width: 640px) {
  .task-filter-panel,
  .task-table-panel {
    padding: 18px;
  }
  .task-filter-grid {
    grid-template-columns: 1fr;
    gap: 15px;
  }
  .task-identity {
    gap: 8px;
  }
  .task-pagination {
    overflow-x: auto;
  }
}
</style>
