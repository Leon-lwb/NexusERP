<template>
  <el-card shadow="never" class="engineering-task-card mb-3">
    <template #header
      ><div class="flex flex-wrap items-center justify-between gap-2">
        <span>{{ engineeringActionLabel(task.action) }}任务</span><el-tag :type="state.type">{{ state.label }}</el-tag>
      </div></template
    >
    <p class="mb-2 break-all">任务 {{ task.taskId }}</p>
    <p>阶段：{{ task.stage ? ENGINEERING_STAGES[task.stage] : '等待设备确认' }}</p>
    <p v-if="task.cancelRequested">取消请求已提交，等待设备确认</p>
    <el-alert v-if="task.errorMessage || error" :title="error || task.errorMessage" type="error" :closable="false" class="my-2" />
    <p v-if="task.missingTypes?.length">缺失：{{ task.missingTypes.map((type) => ARTIFACT_LABELS[type] || type).join('、') }}</p>
    <ConnectionCheckResult v-if="task.action === 'CHECK_CONNECTION'" :task="task" compact class="mt-3" />
    <p v-else class="text-sm text-gray-500">工程草稿，未发布生产</p>
    <div class="mt-3 flex flex-wrap gap-2">
      <el-button v-if="canQuery" size="small" type="primary" plain @click="router.push(`/ai/engineering/task/${task.taskId}`)">{{
        task.action === 'CHECK_CONNECTION' ? '查看检查结果' : '查看预览'
      }}</el-button>
      <el-button v-if="canAct && shouldPoll(task.state) && !task.cancelRequested" size="small" type="danger" plain :loading="acting" @click="cancel"
        >取消任务</el-button
      >
      <el-button v-if="canAct && !shouldPoll(task.state) && task.action !== 'CHECK_CONNECTION'" size="small" :loading="acting" @click="openProject"
        >在配对设备打开工程</el-button
      >
    </div>
  </el-card>
</template>

<script setup lang="ts" name="EngineeringTaskCard">
import { computed, getCurrentInstance, ref, watch, type ComponentInternalInstance } from 'vue';
import { useRouter } from 'vue-router';
import { checkPermi } from '@/utils/permission';
import { cancelEngineeringTask, createEngineeringTask, type EngineeringTask } from '@/api/ai/engineering';
import {
  ARTIFACT_LABELS,
  ENGINEERING_STATES,
  ENGINEERING_STAGES,
  engineeringError,
  shouldPoll,
  engineeringActionLabel,
  engineeringCompletionNotice
} from '@/views/ai/engineering/model';
import ConnectionCheckResult from './ConnectionCheckResult.vue';
const props = defineProps<{ task: EngineeringTask }>();
const emit = defineEmits<{ changed: [] }>();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();
const acting = ref(false);
const error = ref('');
const state = computed(() => ENGINEERING_STATES[props.task.state] || { label: '状态未知', type: 'info' as const });
const canQuery = computed(() => checkPermi(['ai:engineering:query']));
const canAct = computed(() => checkPermi(['ai:engineering:task']));
watch(
  () => props.task,
  (current, previous) => {
    const notice = engineeringCompletionNotice(previous, current);
    if (!notice || !proxy?.$modal) return;
    if (notice.type === 'success') proxy.$modal.msgSuccess(notice.message);
    else if (notice.type === 'warning') proxy.$modal.msgWarning(notice.message);
    else proxy.$modal.msgError(notice.message);
  }
);

/** 取消后刷新服务端任务，卡片不自行推断终态。 */
async function cancel() {
  if (!canAct.value || acting.value) return;
  try {
    await proxy?.$modal.confirm('确认请求取消此工程任务？');
  } catch {
    return;
  }
  acting.value = true;
  error.value = '';
  try {
    await cancelEngineeringTask(props.task.taskId);
    emit('changed');
  } catch (cause) {
    error.value = engineeringError(cause).message;
  } finally {
    acting.value = false;
  }
}
/** 打开本地工程只创建受控动作，不向浏览器提供或执行磁盘路径。 */
async function openProject() {
  if (!canAct.value || acting.value) return;
  acting.value = true;
  error.value = '';
  try {
    await createEngineeringTask({
      deviceId: props.task.deviceId,
      action: 'OPEN_PROJECT',
      parentTaskId: props.task.taskId,
      sessionId: props.task.sessionId,
      idempotencyKey: crypto.randomUUID()
    });
    proxy?.$modal.msgSuccess('已提交打开工程任务，等待配对设备执行');
    emit('changed');
  } catch (cause) {
    error.value = engineeringError(cause).message;
  } finally {
    acting.value = false;
  }
}
</script>

<style scoped lang="scss">
.engineering-task-card {
  border-radius: 12px;
  :deep(.el-card__header) {
    padding: 13px 16px;
    border-bottom-color: var(--el-border-color-lighter);
    background: var(--el-fill-color-lighter);
    font-size: 13px;
    font-weight: 500;
  }
  :deep(.el-card__body) {
    padding: 15px 16px;
    font-size: 12px;
    line-height: 1.8;
  }
  :deep(.el-card__body > p:first-child) {
    color: var(--el-text-color-secondary);
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 11px;
  }
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
