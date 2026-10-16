<template>
  <section class="connection-check-result" :class="[`connection-check-result--${result.type}`, { 'is-compact': compact }]" aria-label="连接检查结果">
    <div v-if="!compact" class="connection-result-heading">
      <span>连接诊断</span
      ><span class="connection-result-caption">{{
        ['SUCCEEDED', 'FAILED', 'CANCELED', 'EXPIRED', 'PARTIAL_SUCCESS'].includes(task.state) ? '已保存的检查结果' : '等待设备回传'
      }}</span>
    </div>
    <el-alert :title="result.title" :description="result.description" :type="result.type" :closable="false" show-icon />
    <dl v-if="task.connectionDiagnostic" class="connection-diagnostics">
      <div>
        <dt>CLI 版本</dt>
        <dd>{{ task.connectionDiagnostic.cliVersion || '未上报' }}</dd>
      </div>
      <div>
        <dt>编辑器版本</dt>
        <dd :class="{ 'diagnostic-unavailable': !task.connectionDiagnostic.editorVersion }">
          {{ task.connectionDiagnostic.editorVersion || '未上报' }}
        </dd>
      </div>
      <div>
        <dt>实际传输</dt>
        <dd>{{ task.connectionDiagnostic.transportMode || '未上报' }}</dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts" name="ConnectionCheckResult">
import { computed } from 'vue';
import type { EngineeringTask } from '@/api/ai/engineering';
import { connectionCheckPresentation } from '@/views/ai/engineering/model';
const props = withDefaults(defineProps<{ task: EngineeringTask; compact?: boolean }>(), { compact: false });
const result = computed(() => connectionCheckPresentation(props.task));
</script>

<style scoped lang="scss">
.connection-check-result {
  padding: 22px 24px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
}
.connection-result-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
}
.connection-result-caption {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}
.connection-check-result :deep(.el-alert) {
  padding: 16px;
  border-radius: 12px;
}
.connection-check-result :deep(.el-alert__title) {
  font-size: 15px;
  font-weight: 600;
}
.connection-check-result :deep(.el-alert__description) {
  margin-top: 6px;
  line-height: 1.7;
}
.connection-diagnostics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0 0;
  > div {
    min-width: 0;
    padding: 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 11px;
    background: var(--el-fill-color-blank);
  }
  dt {
    margin-bottom: 9px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  dd {
    margin: 0;
    overflow-wrap: anywhere;
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 14px;
    font-weight: 500;
    line-height: 1.6;
  }
  .diagnostic-unavailable {
    color: var(--el-text-color-placeholder);
    font-family: inherit;
    font-size: 13px;
    font-weight: 400;
  }
}
.is-compact {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  :deep(.el-alert) {
    padding: 10px 12px;
  }
  :deep(.el-alert__title) {
    font-size: 13px;
  }
  .connection-diagnostics {
    grid-template-columns: 1fr;
    gap: 7px;
    margin-top: 12px;
    > div {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
      padding: 0;
      border: 0;
      background: transparent;
    }
    dt {
      flex-shrink: 0;
      margin: 0;
    }
    dd {
      text-align: right;
      font-size: 12px;
    }
  }
}
@media (max-width: 640px) {
  .connection-check-result:not(.is-compact) {
    padding: 18px;
  }
  .connection-diagnostics {
    grid-template-columns: 1fr;
    gap: 10px;
    > div {
      padding: 13px 14px;
    }
  }
}
</style>
