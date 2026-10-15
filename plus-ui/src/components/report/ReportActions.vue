<template>
  <div class="report-row-actions">
    <el-button
      v-if="checkPermi(['report:runtime:preview'])"
      link
      type="primary"
      :disabled="row.status !== 'PUBLISHED'"
      @click="emit('action', 'run', row)"
      >运行</el-button
    >
    <el-button v-if="checkPermi(['report:definition:edit'])" link type="primary" @click="emit('action', 'design', row)">设计</el-button>
    <el-dropdown
      v-if="
        checkPermi(['report:definition:edit', 'report:definition:list', 'report:definition:publish', 'report:acl:manage', 'report:definition:remove'])
      "
      trigger="click"
      @command="(command) => emit('action', command, row)"
    >
      <el-button link aria-label="报表更多操作">更多<span class="i-ep-arrow-down ml-1" aria-hidden="true" /></el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-if="checkPermi(['report:definition:list'])" command="history">发布历史</el-dropdown-item>
          <el-dropdown-item
            v-if="checkPermi(['report:definition:edit']) && checkPermi(['report:definition:add'])"
            command="copy"
            :disabled="!!copying"
            >{{ copying === row.id ? '复制中…' : '复制为草稿' }}</el-dropdown-item
          >
          <el-dropdown-item v-if="checkPermi(['report:definition:publish'])" command="publish">发布草稿</el-dropdown-item>
          <el-dropdown-item v-if="row.status === 'PUBLISHED' && checkPermi(['report:definition:publish'])" command="offline"
            >下线报表</el-dropdown-item
          >
          <el-dropdown-item v-if="checkPermi(['report:acl:manage'])" command="acl">资源授权</el-dropdown-item>
          <el-dropdown-item v-if="checkPermi(['report:definition:remove'])" command="archive" divided
            ><span class="report-danger">归档报表</span></el-dropdown-item
          >
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>
<script setup lang="ts">
import { checkPermi } from '@/utils/permission';
import type { ReportDefinition } from '@/types/report';
/** 列表和卡片共用的报表操作，逐项沿用原有权限。 */
defineProps<{
  /** 当前资源及其完整 ID。 */
  row: ReportDefinition;
  /** 正在复制的资源 ID；复制结束前阻止重复提交。 */
  copying: string;
}>();
/** 将明确操作交由页面执行原有鉴权、修订和确认流程。 */
const emit = defineEmits<{
  /** 选择当前报表操作。 */
  action: [action: string, row: ReportDefinition];
}>();
</script>
<style scoped>
.report-danger {
  color: var(--el-color-danger);
}
</style>
