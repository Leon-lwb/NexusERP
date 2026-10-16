<template>
  <div class="app-container">
    <el-alert v-if="!ENGINEERING_ENABLED" :title="ENGINEERING_DISABLED_NOTICE" type="info" :closable="false" class="mb-3" />
    <el-form v-if="ENGINEERING_ENABLED" :model="query" class="list-search" @submit.prevent="search">
      <el-row :gutter="16">
        <el-col :span="6"
          ><el-form-item label="任务状态"
            ><el-select v-model="query.state" clearable placeholder="全部状态"
              ><el-option v-for="(item, key) in ENGINEERING_STATES" :key="key" :label="item.label" :value="key" /></el-select></el-form-item
        ></el-col>
        <el-col :span="6"
          ><el-form-item label="会话 ID"><el-input v-model="sessionFilter" clearable placeholder="可选，按会话筛选" /></el-form-item
        ></el-col>
        <el-col :span="6" class="search-actions"
          ><el-button type="primary" icon="Search" @click="search">搜索</el-button><el-button icon="Refresh" @click="reset">重置</el-button></el-col
        >
      </el-row>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-card v-if="ENGINEERING_ENABLED" shadow="never">
      <template #header
        ><div class="flex items-center justify-between">
          <span>PCB 工程任务</span><el-button icon="Refresh" @click="refresh">刷新</el-button>
        </div></template
      >
      <el-table v-loading="loading" :data="tasks" border empty-text="暂无工程任务，可在 AI 对话中主动选择设备后发起">
        <el-table-column prop="taskId" label="任务 ID" min-width="200" />
        <el-table-column label="状态" min-width="140"
          ><template #default="{ row }"
            ><el-tag :type="ENGINEERING_STATES[row.state]?.type || 'info'">{{ ENGINEERING_STATES[row.state]?.label || row.state }}</el-tag
            ><span v-if="row.cancelRequested">（取消确认中）</span></template
          ></el-table-column
        >
        <el-table-column label="阶段" min-width="150"
          ><template #default="{ row }">{{ ENGINEERING_STAGES[row.stage] || '—' }}</template></el-table-column
        >
        <el-table-column label="设备" min-width="180"
          ><template #default="{ row }">{{ row.deviceName || row.deviceId }}</template></el-table-column
        >
        <el-table-column label="创建时间" min-width="190"
          ><template #default="{ row }">{{ engineeringTime(row.createdAt || row.createTime) }}</template></el-table-column
        >
        <el-table-column label="缺失产物" min-width="190"
          ><template #default="{ row }">{{
            row.missingTypes?.length ? row.missingTypes.map((type) => ARTIFACT_LABELS[type] || type).join('、') : '—'
          }}</template></el-table-column
        >
        <el-table-column label="操作" min-width="220" fixed="right"
          ><template #default="{ row }">
            <el-button v-hasPermi="['ai:engineering:query']" link type="primary" @click="router.push(`/ai/engineering/task/${row.taskId}`)"
              >详情</el-button
            >
            <el-button
              v-if="shouldPoll(row.state) && !row.cancelRequested"
              v-hasPermi="['ai:engineering:task']"
              link
              type="danger"
              @click="cancel(row)"
              >取消</el-button
            >
            <el-button v-if="!shouldPoll(row.state)" v-hasPermi="['ai:engineering:task']" link type="primary" @click="retry(row)"
              >新任务重试</el-button
            >
          </template></el-table-column
        >
      </el-table>
      <pagination v-show="total > 0" v-model:total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="refresh" />
    </el-card>
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
import { ARTIFACT_LABELS, ENGINEERING_STATES, ENGINEERING_STAGES, engineeringError, engineeringId, engineeringTime, shouldPoll } from '../model';
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
